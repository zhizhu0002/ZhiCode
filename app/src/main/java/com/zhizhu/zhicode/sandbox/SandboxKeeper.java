package com.zhizhu.zhicode.sandbox;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import com.zhizhu.zhicode.compose.MainActivity;

/**
 * Sandbox 保活服务。
 *
 * <p><b>为什么需要它。</b>guest 的虚拟 Activity 会盖住蜘蛛界面，此时主进程处于 paused。
 * 没有前台服务，系统会在内存压力下回收主进程，Agent 执行链与工具 worker 会随之中断 ——
 * 而 guest 还在前台跑，用户看到的只是"Agent 不动了"。本服务用一条常驻通知把主进程
 * 钉在前台，保证 guest 运行期间 Agent 不被回收。
 *
 * <p><b>什么时候起停。</b>只在确实有 guest 要跑时才起（由 {@link ZhiSandbox#launch} 在启动
 * guest 前调用），guest 启动失败或停止时立刻关掉（{@link ZhiSandbox#stop} 等），
 * 不在空闲时白占一条通知。
 *
 * <p>通知里的两个动作：点通知体回到蜘蛛界面；点"结束"按钮停掉本服务。
 */
public final class SandboxKeeper extends Service {

    private static final String CHANNEL_ID = "zhisandbox_keeper";
    private static final int NOTIFICATION_ID = 19221;
    private static final String ACTION_STOP = "com.zhizhu.zhicode.sandbox.KEEPER_STOP";

    /** 起前台保活。失败只记录，不影响 guest 启动流程。 */
    public static void start(Context context) {
        Intent intent = new Intent(context, SandboxKeeper.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Throwable error) {
            SandboxConsole.event("沙箱保活服务启动失败: " + error);
        }
    }

    /** 停前台保活。幂等，任何异常都吞掉。 */
    public static void stop(Context context) {
        try {
            context.stopService(new Intent(context, SandboxKeeper.class));
        } catch (Throwable ignored) {
            // 服务本就没起或已停，无需处理
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "ZhiCode 沙箱保活", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("沙箱应用运行期间保持 ZhiCode Agent 执行链存活");
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;

        Intent open = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openIntent = PendingIntent.getActivity(this, 0, open, flags);

        Intent stop = new Intent(this, SandboxKeeper.class).setAction(ACTION_STOP);
        PendingIntent stopIntent = PendingIntent.getService(this, 1, stop, flags);

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder
                .setSmallIcon(android.R.drawable.stat_notify_more)
                .setContentTitle("ZhiCode 沙箱正在运行")
                .setContentText("Agent 保持在线 · 点击返回 ZhiCode")
                .setOngoing(true)
                .setContentIntent(openIntent)
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_menu_close_clear_cancel, "结束沙箱保活", stopIntent).build())
                .build();
    }
}
