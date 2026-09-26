package com.zhizhu.zhicode.sandbox;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.SandboxContract;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.app.configuration.AppLifecycleCallback;
import top.niunaijun.blackbox.app.configuration.ClientConfiguration;
import top.niunaijun.blackbox.entity.am.RunningAppProcessInfo;
import top.niunaijun.blackbox.entity.pm.InstallResult;
import top.niunaijun.blackbox.fake.frameworks.BActivityManager;

/**
 * 蜘蛛沙箱引擎门面。
 *
 * <p>对外<b>只暴露这一个入口</b>：宿主层其它类不允许直接触碰 {@code BlackBoxCore}。
 * 这样引擎的调用面收敛成一份可审计的清单，引擎版本变动时只需要改这一处。
 *
 * <p><b>只允许在沙箱进程里使用。</b>调用方必须先经
 * {@link SandboxProcess#ownsEngine(Context)} 判定；主进程加载本类视为错误。
 *
 * <p>生命周期：{@code attach()} 在 {@code attachBaseContext} 里调，{@code create()} 在
 * {@code onCreate} 里调。控制器进程与 Guest 进程都会各走一遍。
 * 任一步骤结束都会在 {@link SandboxStage} 留痕，因此
 * {@link #status()} 能报出「卡在哪一步」而不是只给一句超时。
 */
public final class ZhiSandbox {

    /** 沙箱内使用的用户 id，与引擎的 {@code SandboxContract.USER_ID} 同源。 */
    public static final int USER_ID = SandboxContract.USER_ID;

    private static final AtomicBoolean ATTACHED = new AtomicBoolean();
    private static final AtomicBoolean CREATE_STARTED = new AtomicBoolean();
    private static final AtomicBoolean CREATED = new AtomicBoolean();
    private static final CountDownLatch READY_LATCH = new CountDownLatch(1);
    private static final Object LIFECYCLE_LOCK = new Object();

    private static volatile Throwable initError;
    private static volatile Context appContext;
    private static volatile WeakReference<Activity> resumedActivity = new WeakReference<>(null);
    private static volatile String resumedPackage = "";

    private ZhiSandbox() {}

    // ------------------------------------------------------------------ 生命周期

    /** 在 {@code Application.attachBaseContext} 里调用，必须早于 {@link #create()}。 */
    public static void attach(Context context) {
        if (!ATTACHED.compareAndSet(false, true)) return;
        SandboxStage.mark("engine:attach-begin");
        try {
            Context app = context.getApplicationContext() == null ? context : context.getApplicationContext();
            appContext = app;
            BlackBoxCore.get().doAttachBaseContext(context, new ClientConfiguration() {
                @Override public String getHostPackageName() { return app.getPackageName(); }
                @Override public boolean isHideRoot() { return SandboxPrefs.isRootHidden(app); }
                @Override public boolean isEnableDaemonService() { return false; }
                @Override public boolean isEnableLauncherActivity() { return false; }
                @Override public boolean isUseVpnNetwork() { return false; }
                @Override public boolean requestInstallPackage(File file, int userId) { return false; }
                @Override public String getLogSenderChatId() { return null; }
            });
            SandboxStage.mark("engine:attach-ok");
        } catch (Throwable error) {
            initError = error;
            SandboxStage.mark("engine:attach-error:" + error.getClass().getSimpleName());
            SandboxConsole.event("引擎 attach 失败: " + error);
        }
    }

    /** 在 {@code Application.onCreate} 里调用。 */
    public static void create() {
        if (!CREATE_STARTED.compareAndSet(false, true)) return;
        if (initError != null) {
            READY_LATCH.countDown();
            return;
        }
        SandboxStage.mark("engine:create-begin");
        try {
            SandboxConsole.event("沙箱引擎 create 开始: " + SandboxProcess.name(appContext));
            BlackBoxCore.get().addAppLifecycleCallback(new AppLifecycleCallback() {
                @Override public void beforeMainLaunchApk(String pkg, int uid) {
                    SandboxConsole.event("准备启动虚拟应用: " + pkg + " user=" + uid);
                }

                @Override public void beforeCreateApplication(String pkg, String process, Context c, int uid) {
                    SandboxConsole.event("创建虚拟进程: " + pkg + " / " + process);
                }

                @Override public void beforeApplicationOnCreate(String pkg, String process, Application a, int uid) {
                    Context host = appContext == null ? a : appContext;
                    if (FridaEnv.isAutoAttachEnabled(host, pkg)) {
                        try {
                            SandboxFrida.load(host);
                            SandboxConsole.event("Frida auto-attach 已在 Application.onCreate 前加载: " + pkg + " / " + process);
                        } catch (Throwable error) {
                            SandboxConsole.event("Frida auto-attach 失败: " + pkg + " / " + process + " / " + error);
                        }
                    }
                }

                @Override public void afterApplicationOnCreate(String pkg, String process, Application a, int uid) {
                    SandboxConsole.event("虚拟 Application 已启动: " + pkg + " / " + process);
                }

                @Override public void onActivityResumed(Activity activity) {
                    String pkg = virtualPackage();
                    if (pkg.isEmpty()) pkg = activity.getPackageName();
                    if (pkg.isEmpty()) pkg = "guest";
                    resumedActivity = new WeakReference<>(activity);
                    resumedPackage = pkg;
                    SandboxConsole.event("Activity resumed: " + pkg + " / " + activity.getClass().getName());
                    if (SandboxPrefs.isFloatingLogEnabled(appContext)) {
                        SandboxOverlay.attach(activity, pkg);
                    } else {
                        SandboxOverlay.detach(activity);
                    }
                }

                @Override public void onActivityPaused(Activity activity) {
                    Activity current = resumedActivity.get();
                    if (current == activity) {
                        resumedActivity = new WeakReference<>(null);
                        resumedPackage = "";
                    }
                }
            });
            BlackBoxCore.get().doCreate();
            CREATED.set(true);
            SandboxStage.mark("engine:create-ok");
            SandboxConsole.event("沙箱引擎已初始化；网络使用宿主机直连，VPN 网络模式已禁用");
        } catch (Throwable error) {
            initError = error;
            SandboxStage.mark("engine:create-error:" + error.getClass().getSimpleName());
            SandboxConsole.event("引擎 create 失败: " + SandboxConsole.stackTrace(error));
        } finally {
            READY_LATCH.countDown();
        }
    }

    /**
     * ContentProvider 在 {@code Application.onCreate} 之前就发布，因此调用方可能
     * 比 {@link #create()} 早几毫秒到达。这里把创建排到控制器主线程队列上，
     * 让 Binder 调用方等真实结果，而不是谎报后端不可用。
     */
    public static void ensureCreateScheduled() {
        if (CREATED.get() || CREATE_STARTED.get() || initError != null) return;
        SandboxStage.mark("engine:create-scheduled");
        try {
            new Handler(Looper.getMainLooper()).post(ZhiSandbox::create);
        } catch (Throwable error) {
            SandboxConsole.event("调度引擎 create 失败: " + error);
            create();
        }
    }

    /**
     * 等待引擎就绪。
     *
     * <p>超时/失败时抛出的是<b>可读原因</b>：包含状态串与该进程最后一条阶段，
     * 便于直接看出「卡在哪一步」。旧实现只给一句「引擎初始化超时」，
     * 拿到日志也无从下手。
     */
    public static void awaitReady(long timeoutMs) {
        if (isReady()) return;
        if (initError != null) throw new IllegalStateException(status(), initError);
        ensureCreateScheduled();
        long budget = Math.max(250L, timeoutMs);
        long deadline = SystemClock.uptimeMillis() + budget;
        try {
            while (true) {
                long remaining = deadline - SystemClock.uptimeMillis();
                if (remaining <= 0) break;
                if (READY_LATCH.await(Math.min(200L, remaining), TimeUnit.MILLISECONDS)) break;
                if (initError != null) throw new IllegalStateException(status(), initError);
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待引擎初始化被中断", error);
        }
        if (!isReady()) {
            throw new IllegalStateException(
                    "引擎初始化超时（" + budget + "ms）· " + status()
                            + " · 进程 " + SandboxProcess.name(appContext));
        }
    }

    public static boolean isReady() {
        return ATTACHED.get() && CREATED.get() && initError == null;
    }

    /** 状态串。未就绪时带上最后一条阶段记录，避免只有一句笼统的「初始化中」。 */
    public static String status() {
        Throwable error = initError;
        if (error != null) {
            return "引擎异常: " + error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage());
        }
        if (CREATED.get()) return "引擎就绪 · 用户 " + USER_ID;
        if (CREATE_STARTED.get()) return "引擎正在初始化 · 最后阶段 " + SandboxStage.lastMark();
        return "引擎等待 Application.onCreate · 最后阶段 " + SandboxStage.lastMark();
    }

    // ------------------------------------------------------------------ 应用管理

    public static InstallResult install(File apk) {
        ensureReady();
        return BlackBoxCore.get().installPackageAsUser(apk, USER_ID);
    }

    public static List<ApplicationInfo> installedApplications() {
        ensureReady();
        List<ApplicationInfo> result = BlackBoxCore.get().getInstalledApplications(0, USER_ID);
        return result == null ? Collections.emptyList() : result;
    }

    public static List<ActivityManager.RunningAppProcessInfo> runningProcesses(String pkg) {
        ensureReady();
        if (pkg == null || pkg.trim().isEmpty()) return Collections.emptyList();
        try {
            RunningAppProcessInfo info = BActivityManager.get().getRunningAppProcesses(pkg.trim(), USER_ID);
            if (info == null || info.mAppProcessInfoList == null) return Collections.emptyList();
            return new ArrayList<>(info.mAppProcessInfoList);
        } catch (Throwable error) {
            throw new IllegalStateException("读取沙箱进程失败: " + error.getMessage(), error);
        }
    }

    public static boolean launch(String pkg) {
        synchronized (LIFECYCLE_LOCK) {
            ensureReady();
            Context context = appContext;
            if (context != null) SandboxKeeper.start(context);
            boolean launched = BlackBoxCore.get().launchApk(pkg, USER_ID);
            if (!launched && context != null) SandboxKeeper.stop(context);
            return launched;
        }
    }

    public static void stop(String pkg) {
        ensureReady();
        BlackBoxCore.get().stopPackage(pkg, USER_ID);
        Context context = appContext;
        if (context != null) SandboxKeeper.stop(context);
    }

    public static void clearData(String pkg) {
        ensureReady();
        BlackBoxCore.get().clearPackage(pkg, USER_ID);
    }

    public static void uninstall(String pkg) {
        ensureReady();
        BlackBoxCore.get().uninstallPackageAsUser(pkg, USER_ID);
        Context context = appContext;
        if (context != null) SandboxKeeper.stop(context);
    }

    // ------------------------------------------------------------------ 设置

    public static boolean isRootHidden() {
        Context context = appContext;
        return context == null || SandboxPrefs.isRootHidden(context);
    }

    public static boolean isFloatingLogEnabled() {
        Context context = appContext;
        return context != null && SandboxPrefs.isFloatingLogEnabled(context);
    }

    public static boolean setFloatingLogEnabled(boolean enabled) throws Exception {
        ensureReady();
        Context context = appContext;
        if (context == null) throw new IllegalStateException("沙箱 Context 尚未就绪");
        SandboxPrefs.setFloatingLogEnabled(context, enabled);
        Activity activity = resumedActivity.get();
        if (activity != null) {
            if (enabled) SandboxOverlay.attach(activity, resumedPackage);
            else SandboxOverlay.detach(activity);
        }
        SandboxConsole.event("日志悬浮窗已" + (enabled ? "开启" : "关闭"));
        return SandboxPrefs.isFloatingLogEnabled(context);
    }

    /**
     * 切换 Root 隐藏。
     *
     * <p>必须先停掉全部 Guest：Root 隐藏会改变引擎暴露给 Guest 的设备视图，
     * 已经跑起来的 Guest 不会重新读取，继续运行会得到不一致的状态。
     */
    public static boolean setRootHidden(boolean hidden) throws Exception {
        ensureReady();
        Context context = appContext;
        if (context == null) throw new IllegalStateException("沙箱 Context 尚未就绪");
        synchronized (LIFECYCLE_LOCK) {
            if (SandboxPrefs.isRootHidden(context) == hidden) return false;
            List<ApplicationInfo> apps = installedApplications();
            SandboxKeeper.stop(context);
            for (ApplicationInfo info : apps) BlackBoxCore.get().stopPackage(info.packageName, USER_ID);
            long deadline = SystemClock.uptimeMillis() + 1500L;
            boolean stopped = allGuestsStopped(apps);
            while (!stopped && SystemClock.uptimeMillis() < deadline) {
                SystemClock.sleep(50L);
                stopped = allGuestsStopped(apps);
            }
            if (!stopped) throw new IllegalStateException("仍有 Guest 进程在运行，Root 隐藏设置未变更");
            SandboxPrefs.setRootHidden(context, hidden);
            SandboxConsole.event("Root 隐藏已" + (hidden ? "开启" : "关闭") + "；已停止全部 Guest");
            return true;
        }
    }

    private static boolean allGuestsStopped(List<ApplicationInfo> apps) {
        for (ApplicationInfo info : apps) {
            if (!runningProcesses(info.packageName).isEmpty()) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ 存储权限

    public static boolean hasAllFilesAccess() {
        ensureReady();
        return BlackBoxCore.get().hasAllFilesAccess();
    }

    public static void requestAllFilesAccess(Activity activity) {
        ensureReady();
        BlackBoxCore.get().requestAllFilesAccess(activity);
    }

    // ------------------------------------------------------------------ 当前 Guest

    public static Activity resumedActivity() {
        return resumedActivity.get();
    }

    public static String resumedPackage() {
        return resumedPackage;
    }

    /** 宿主（本应用）Context，供 Guest 侧解析宿主私有目录。 */
    static Context hostContext() {
        return appContext;
    }

    private static String virtualPackage() {
        try {
            String pkg = BActivityThread.getAppPackageName();
            return pkg == null ? "" : pkg;
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static void ensureReady() {
        if (!isReady()) throw new IllegalStateException(status());
    }
}
