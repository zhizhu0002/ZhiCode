package com.zhizhu.zhicode.background;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import com.termux.app.zhicode.storage.ApiSettingsStore;
import com.zhizhu.zhicode.compose.HostRefs;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 用户明确开关的前台保活服务。
 *
 * <h3>为什么需要它</h3>
 * Android 会在后台限制应用的 CPU 与网络。一次会话可能跑几十分钟，被系统挂起就等于
 * 「网络请求莫名其妙断了」。这里用前台服务（带常驻通知）+ 部分唤醒锁把进程保住。
 *
 * <h3>开工之前先说清楚在做什么</h3>
 * 常驻通知是这套机制的代价，也是它必须「用户明确打开」的原因 —— 它会一直显示在状态栏，
 * 并且能一键停止。通知里那句「正在后台保活」不是装饰：用户看到一个常驻通知，
 * 必须有办法知道它是谁加的、以及怎么关掉。
 *
 * <h3>停止时要把开关归位</h3>
 * 从通知里点「停止保活」等于用户关掉了这个功能，所以必须同时把设置里的开关写回 false。
 * 这一步走 {@link ApiSettingsStore} 而不是自己拼 prefs 文件名与键名 ——
 * 这里出过一个真实的缺陷：原先服务自己写的是<b>旧版</b> prefs 文件（迁移用的只读来源），
 * 于是「从通知停掉保活」并没有把开关关掉，下次启动界面还显示开启。
 *
 * <h3>root 加强是可选的</h3>
 * 只有启动时带了 {@code root=true} 才去调 {@link RootKeepAliveController}。
 * 没带的时候反而要主动撤回（{@code revoke}）：上一次可能是带 root 启动的，
 * 而这次用户关掉了那个开关。
 */
public final class KeepAliveService extends Service {

    /** 启动（或重新设定 root 期望值）。必须带成对的 action，否则系统重投递无法区分意图。 */
    public static final String ACTION_START = "com.zhizhu.zhicode.action.KEEP_ALIVE_START";
    /** 停止保活，并把设置里的开关归位。 */
    public static final String ACTION_STOP = "com.zhizhu.zhicode.action.KEEP_ALIVE_STOP";

    private static final String EXTRA_ROOT = "root";
    private static final String CHANNEL_ID = "zhicode_keep_alive";
    private static final String CHANNEL_NAME = "蜘蛛后台保活";
    private static final int NOTIFICATION_ID = 19001;
    private static final String WAKELOCK_TAG = "ZhiCode:KeepAlive";

    private static final String TITLE = "蜘蛛正在后台保活";
    private static final String STARTED_TEXT = "强制后台保活已开启";
    private static final String STOP_ACTION_LABEL = "停止保活";

    /** 通知与唤醒锁都要在 API 26 前后走两套构造方式。 */
    private static final int API_CHANNELS = 26;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile PowerManager.WakeLock wakeLock;

    /**
     * 启动保活。
     *
     * <p>API 26 之后必须用 {@code startForegroundService}：普通 {@code startService}
     * 在后台启动时会被系统直接拒绝，表现是「点了开关但没有任何反应」。
     */
    public static void start(Context context, boolean rootEnabled) {
        Intent intent = new Intent(context, KeepAliveService.class)
                .setAction(ACTION_START)
                .putExtra(EXTRA_ROOT, rootEnabled);
        if (Build.VERSION.SDK_INT >= API_CHANNELS) context.startForegroundService(intent);
        else context.startService(intent);
    }

    /**
     * 停止保活。
     *
     * <p>刻意用 {@code startService} 而不是 {@code startForegroundService}：
     * 这次调用的目的正是「不再需要前台状态」，把它当成前台服务启动会在某些系统上
     * 触发「5 秒内必须显示通知」的检查而抛异常。
     */
    public static void stop(Context context) {
        context.startService(new Intent(context, KeepAliveService.class).setAction(ACTION_STOP));
    }

    @Override public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        acquireWakeLock();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) return handleStop();
        return handleStart(intent);
    }

    /**
     * 处理「停止」。
     *
     * <p>五件事的顺序不能换：
     * <ol>
     *   <li>先把设置里的开关写回 false —— 这是用户看得见的结果，必须最先做；
     *   <li>撤掉前台通知（{@code stopForeground(true)} 连通知一起清掉）；
     *   <li>放掉唤醒锁，否则进程活着但 CPU 一直被占；
     *   <li>在后台撤掉 root 加强（要执行 shell，不能卡住主线程）；
     *   <li>{@code stopSelf()} 让服务真正结束。</li>
     * </ol>
     */
    private int handleStop() {
        ApiSettingsStore.setForcedKeepAliveEnabled(this, false);
        stopForeground(true);
        releaseWakeLock();
        worker.execute(() -> {
            RootKeepAliveController.revoke(this);
            stopSelf();
        });
        // 不粘性：用户明确停掉的东西不该被系统重新拉起来。
        return START_NOT_STICKY;
    }

    /**
     * 处理「启动」。
     *
     * <p>先 {@code startForeground} 再做别的：后台服务启动后有几秒时间必须显示通知，
     * 先去跑 shell（可能要好几秒）会把这几秒用光。root 加强因此放进工作线程，
     * 结果回来之后原地更新同一条通知。
     */
    private int handleStart(Intent intent) {
        startForeground(NOTIFICATION_ID, buildNotification(STARTED_TEXT));
        boolean rootRequested = intent != null && intent.getBooleanExtra(EXTRA_ROOT, false);
        worker.execute(() -> {
            if (!rootRequested) {
                // 上次可能是带 root 启动的，而这次用户关了那个开关。
                RootKeepAliveController.revoke(this);
                return;
            }
            updateNotification(RootKeepAliveController.apply(this));
        });
        // 粘性：保活被系统回收后应当自己恢复 —— 这正是用户要的效果。
        return START_STICKY;
    }

    @Override public void onDestroy() {
        releaseWakeLock();
        worker.shutdownNow();
        super.onDestroy();
    }

    /** 本服务不接受绑定。 */
    @Override public IBinder onBind(Intent intent) {
        return null;
    }

    /** 刻意留空：默认行为（不清掉自己）就是保活想要的；写出来是为了说明这不是遗漏。 */
    @Override public void onTaskRemoved(Intent rootIntent) { }

    // ------------------------------------------------------------ 通知

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < API_CHANNELS) return;
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) return;
        NotificationChannel channel =
                new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW);
        manager.createNotificationChannel(channel);
    }

    private void updateNotification(String text) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification(text));
    }

    /**
     * 构造常驻通知。
     *
     * <p>{@code setOngoing(true)} 让用户不能滑掉它 —— 前台服务的通知被滑掉，
     * 系统会认为服务不再有前台状态并可能杀掉进程，而「保活」正是要避免这件事。
     * 所以关闭的唯一入口是通知上的那个按钮。
     */
    private Notification buildNotification(String text) {
        Intent openApp = new Intent(this, HostRefs.mainActivity());
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT);

        Intent stopIntent = new Intent(this, KeepAliveService.class).setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 1, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder builder = Build.VERSION.SDK_INT >= API_CHANNELS
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle(TITLE)
                .setContentText(text)
                .setOngoing(true)
                .setContentIntent(contentIntent)
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_menu_close_clear_cancel, STOP_ACTION_LABEL, stopPendingIntent)
                        .build())
                .build();
    }

    // ------------------------------------------------------------ 唤醒锁

    /** 拿部分唤醒锁保住 CPU。熄屏之后 CPU 会睡，正在读的网络流随之卡住。 */
    private void acquireWakeLock() {
        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        if (power == null) return;
        PowerManager.WakeLock lock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG);
        // 不引用计数：这个服务自己就是唯一持有者，计数只会带来「放不掉」的风险。
        lock.setReferenceCounted(false);
        lock.acquire();
        wakeLock = lock;
    }

    /**
     * 放掉唤醒锁。
     *
     * <p>用 {@code volatile} + 局部变量：{@code onDestroy} 可能与 {@code handleStop}
     * 同时走到这里，两次 release 同一个已释放的锁会抛异常。
     */
    private void releaseWakeLock() {
        PowerManager.WakeLock lock = wakeLock;
        wakeLock = null;
        if (lock == null) return;
        try {
            if (lock.isHeld()) lock.release();
        } catch (Throwable ignored) {
            // 已经释放（或被系统收回）时忽略。
        }
    }
}
