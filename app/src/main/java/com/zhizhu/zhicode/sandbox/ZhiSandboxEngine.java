package com.zhizhu.zhicode.sandbox;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.app.ActivityManager;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.app.configuration.AppLifecycleCallback;
import top.niunaijun.blackbox.app.configuration.ClientConfiguration;
import top.niunaijun.blackbox.entity.pm.InstallResult;
import top.niunaijun.blackbox.entity.am.RunningAppProcessInfo;
import top.niunaijun.blackbox.fake.frameworks.BActivityManager;

/** One real BlackBox virtual Android runtime embedded directly in the ZhiCode host app. */
public final class ZhiSandboxEngine {
    public static final int USER_ID=0;
    private static final AtomicBoolean ATTACHED=new AtomicBoolean();
    private static final AtomicBoolean CREATE_STARTED=new AtomicBoolean();
    private static final AtomicBoolean CREATED=new AtomicBoolean();
    private static final CountDownLatch READY_LATCH=new CountDownLatch(1);
    private static final Object LIFECYCLE_LOCK=new Object();
    private static volatile Throwable initError;
    private static volatile Context appContext;
    private static volatile WeakReference<Activity> resumedActivity=new WeakReference<>(null);
    private static volatile String resumedPackage="";

    /**
     * 本进程（主进程）是否跑在**另一个**虚拟运行时里。
     *
     * <p>由 `Application.attachBaseContext` 在**启动期**写入一次，之后只读。
     * 界面读它就可以立刻判定"引擎在这个环境里用不了"，而不必发一次 IPC
     * 等 30 秒超时 —— 那 30 秒是白等的，因为这条路在结构上就不通：
     * 外层沙箱会把本应用的 `:iqsandbox` 也当成 guest 拉起来
     * （实测 `创建虚拟进程: com.zhizhu.code / com.zhizhu.code:iqsandbox`，
     * 跑在 `com.iqge:p2` 里），于是那个"引擎进程"的 filesDir、包解析、
     * 甚至 `/proc/self/cmdline` 全部被外层虚拟化。
     */
    private static volatile boolean nestedRuntime;

    /** 启动期由 Application 调一次。 */
    public static void noteEnvironment(Context context){
        nestedRuntime = SandboxProcessRole.isInsideVirtualRuntime(context);
        if (nestedRuntime) SandboxDebugLog.event("检测到嵌套虚拟运行时：本应用自身运行在其它沙箱内，内层引擎不可用");
    }

    /** 是否处在"引擎结构性不可用"的嵌套环境。界面应当据此直接给结论。 */
    public static boolean isNestedRuntime(){return nestedRuntime;}

    private ZhiSandboxEngine(){}

    public static void attach(Context context){
        if(!ATTACHED.compareAndSet(false,true))return;
        try{
            Context app=context.getApplicationContext()==null?context:context.getApplicationContext();
            appContext=app;
            BlackBoxCore.get().doAttachBaseContext(context,new ClientConfiguration(){
                @Override public String getHostPackageName(){return app.getPackageName();}
                @Override public boolean isHideRoot(){return SandboxSettingsStore.isRootHidden(app);}
                @Override public boolean isEnableDaemonService(){return false;}
                @Override public boolean isEnableLauncherActivity(){return false;}
                @Override public boolean isUseVpnNetwork(){return false;}
                @Override public boolean requestInstallPackage(File file,int userId){return false;}
                @Override public String getLogSenderChatId(){return null;}
            });
        }catch(Throwable e){initError=e;SandboxDebugLog.event("引擎 attach 失败: "+e);}
    }

    public static void create(){
        if(!CREATE_STARTED.compareAndSet(false,true))return;
        if(initError!=null){READY_LATCH.countDown();return;}
        try{
            SandboxDebugLog.event("IQ Sandbox create 开始: "+SandboxProcessRole.processName(appContext));
            BlackBoxCore.get().addAppLifecycleCallback(new AppLifecycleCallback(){
                @Override public void beforeMainLaunchApk(String pkg,int uid){SandboxDebugLog.event("准备启动虚拟应用: "+pkg+" user="+uid);}
                @Override public void beforeCreateApplication(String pkg,String process,Context c,int uid){SandboxDebugLog.event("创建虚拟进程: "+pkg+" / "+process);}
                @Override public void beforeApplicationOnCreate(String pkg,String process,Application a,int uid){
                    Context host=appContext==null?a:appContext;
                    if(FridaRuntimeManager.isAutoAttachEnabled(host,pkg)){
                        try{SandboxFridaBridge.load(host);SandboxDebugLog.event("Frida auto-attach 已在 Application.onCreate 前加载: "+pkg+" / "+process);}
                        catch(Throwable e){SandboxDebugLog.event("Frida auto-attach 失败: "+pkg+" / "+process+" / "+e);}
                    }
                }
                @Override public void afterApplicationOnCreate(String pkg,String process,Application a,int uid){SandboxDebugLog.event("虚拟 Application 已启动: "+pkg+" / "+process);}
                @Override public void onActivityResumed(Activity activity){
                    String pkg=virtualPackage();
                    if(pkg.isEmpty())pkg=activity.getPackageName();
                    if(pkg.isEmpty())pkg="guest";
                    resumedActivity=new WeakReference<>(activity); resumedPackage=pkg;
                    SandboxDebugLog.event("Activity resumed: "+pkg+" / "+activity.getClass().getName());
                    if (SandboxSettingsStore.isFloatingLogEnabled(appContext)) SandboxFloatingController.attach(activity,pkg);
                    else SandboxFloatingController.detach(activity);
                }
                @Override public void onActivityPaused(Activity activity){
                    Activity current=resumedActivity.get(); if(current==activity){resumedActivity=new WeakReference<>(null);resumedPackage="";}
                }
            });
            BlackBoxCore.get().doCreate();
            CREATED.set(true);
            SandboxDebugLog.event("IQ Sandbox 引擎已初始化；网络使用宿主机直连，VPN 网络模式已禁用");
        }catch(Throwable e){
            initError=e;
            SandboxDebugLog.event("引擎 create 失败: "+SandboxDebugLog.stackTrace(e));
        }finally{
            READY_LATCH.countDown();
        }
    }

    /**
     * ContentProvider 在 Application.onCreate 之前就发布了，主进程的第一次 IPC
     * 可能比 create() 早几毫秒。所以这里在 controller 的主 looper 上排一次 create，
     * 让 Binder 调用方 await 到真实结果，而不是收到一个假的"后端不可用"。
     *
     * <h3>⚠️ 必须先 attach，否则这次 create 是白跑的</h3>
     * `create()` 里第一件事就是 `BlackBoxCore.get()`。如果那个时刻
     * `attach()` 还没执行（`Application.attachBaseContext` 尚未跑到），
     * `BlackBoxCore.get()` 拿到的是一个未配置的实例，`doCreate()` 会静默失败 ——
     * 而 `CREATE_STARTED` 已被置真，**后面真正的 attach 之后再也不会重试**，
     * 于是表现成管理页那边 `awaitReady(12000)` 超时：
     * 「引擎初始化超时（12000ms）」。
     *
     * 这条竞态在真实设备上很容易命中：provider 的 `onCreate` 早于
     * `Application.onCreate`，而 `attachBaseContext` 与它们之间还夹着
     * ContentProvider 的实例化顺序。
     */
    public static void ensureCreateScheduled(){
        if(CREATED.get()||CREATE_STARTED.get()||initError!=null)return;
        // attach 还没发生 → 现在排 create 只会白跑一次并锁死 CREATE_STARTED。
        // 交给 attach 完成后的 create() 负责（那条路径一定会走）。
        if(!ATTACHED.get()){
            SandboxDebugLog.event("ensureCreateScheduled: 尚未 attach，交给 attach 后的 create()");
            return;
        }
        try{
            Handler h=new Handler(Looper.getMainLooper());
            h.post(ZhiSandboxEngine::create);
        }catch(Throwable e){
            SandboxDebugLog.event("调度沙箱 create 失败: "+e);
            create();
        }
    }

    public static void awaitReady(long timeoutMs){
        if(isReady())return;
        if(initError!=null)throw new IllegalStateException(status(),initError);
        ensureCreateScheduled();
        try{
            /*
             * 分两段等，而不是一次 await 到底。
             *
             * `READY_LATCH` 只在 create() 结束时倒数。如果连 attach 都还没发生，
             * 一次 await 到 12 秒只会给出一句"超时"，把
             * 「谁还没起来」这个关键信息吞掉 —— 而那决定了用户该去修什么
             * （attach 没跑 = 接线问题；attach 跑了 create 失败 = 运行时问题）。
             *
             * 所以先等 attach，再等 create，两段的超时文案不一样。
             */
            long deadline = SystemClock.uptimeMillis() + Math.max(250L, timeoutMs);
            while(!ATTACHED.get() && initError == null && SystemClock.uptimeMillis() < deadline){
                ensureCreateScheduled(); // attach 完成后这里会真正排上 create
                Thread.sleep(25L);
            }
            if(!ATTACHED.get() && initError == null){
                throw new IllegalStateException(
                        "引擎尚未 attach（沙盒进程的 Application.attachBaseContext 没有执行到引擎初始化）");
            }
            long remaining = Math.max(250L, deadline - SystemClock.uptimeMillis());
            if(!READY_LATCH.await(remaining, TimeUnit.MILLISECONDS))
                throw new IllegalStateException("引擎初始化超时（"+timeoutMs+"ms）；"+status());
        }catch(InterruptedException e){
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待引擎初始化被中断",e);
        }
        if(!isReady())throw new IllegalStateException(status(),initError);
    }

    public static boolean isReady(){return ATTACHED.get()&&CREATED.get()&&initError==null;}
    public static String status(){
        Throwable e=initError;
        if(e!=null)return "引擎异常: "+e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage());
        if(CREATED.get())return "引擎就绪 · 用户 "+USER_ID;
        return CREATE_STARTED.get()?"引擎正在初始化":"引擎等待 Application.onCreate";
    }
    public static InstallResult install(File apk){ensureReady();return BlackBoxCore.get().installPackageAsUser(apk,USER_ID);}
    public static List<ApplicationInfo> installedApplications(){ensureReady();List<ApplicationInfo> r=BlackBoxCore.get().getInstalledApplications(0,USER_ID);return r==null? Collections.emptyList():r;}
    public static List<ActivityManager.RunningAppProcessInfo> runningProcesses(String pkg){
        ensureReady();
        if(pkg==null||pkg.trim().isEmpty())return Collections.emptyList();
        try{
            RunningAppProcessInfo info=BActivityManager.get().getRunningAppProcesses(pkg.trim(),USER_ID);
            return info==null||info.mAppProcessInfoList==null?Collections.emptyList():new java.util.ArrayList<>(info.mAppProcessInfoList);
        }catch(Throwable e){throw new IllegalStateException("读取沙箱进程失败: "+e.getMessage(),e);}
    }
    public static boolean isRootHidden(){Context c=appContext;return c==null||SandboxSettingsStore.isRootHidden(c);}
    public static boolean isFloatingLogEnabled(){Context c=appContext;return c!=null&&SandboxSettingsStore.isFloatingLogEnabled(c);}
    public static boolean setFloatingLogEnabled(boolean enabled)throws Exception{
        ensureReady();
        Context c=appContext;
        if(c==null)throw new IllegalStateException("沙箱 Context 尚未就绪");
        SandboxSettingsStore.setFloatingLogEnabled(c,enabled);
        Activity a=resumedActivity.get();
        if(a!=null){if(enabled)SandboxFloatingController.attach(a,resumedPackage);else SandboxFloatingController.detach(a);}
        SandboxDebugLog.event("日志悬浮窗已"+(enabled?"开启":"关闭"));
        return SandboxSettingsStore.isFloatingLogEnabled(c);
    }
    public static boolean setRootHidden(boolean hidden)throws Exception{
        ensureReady();
        Context c=appContext;
        if(c==null)throw new IllegalStateException("沙箱 Context 尚未就绪");
        synchronized(LIFECYCLE_LOCK){
            boolean current=SandboxSettingsStore.isRootHidden(c);
            if(current==hidden)return false;
            List<ApplicationInfo> apps=installedApplications();
            SandboxGuardService.stop(c);
            for(ApplicationInfo ai:apps)BlackBoxCore.get().stopPackage(ai.packageName,USER_ID);
            long deadline=SystemClock.uptimeMillis()+1500L;
            boolean stopped=allGuestsStopped(apps);
            while(!stopped&&SystemClock.uptimeMillis()<deadline){SystemClock.sleep(50L);stopped=allGuestsStopped(apps);}
            if(!stopped)throw new IllegalStateException("仍有 Guest 进程在运行，Root 隐藏设置未变更");
            SandboxSettingsStore.setRootHidden(c,hidden);
            SandboxDebugLog.event("Root 隐藏已"+(hidden?"开启":"关闭")+"；已停止全部 Guest");
            return true;
        }
    }
    private static boolean allGuestsStopped(List<ApplicationInfo> apps){
        for(ApplicationInfo ai:apps)if(!runningProcesses(ai.packageName).isEmpty())return false;
        return true;
    }
    public static boolean launch(String pkg){
        synchronized(LIFECYCLE_LOCK){
            ensureReady();Context c=appContext;if(c!=null)SandboxGuardService.start(c);boolean ok=BlackBoxCore.get().launchApk(pkg,USER_ID);if(!ok&&c!=null)SandboxGuardService.stop(c);return ok;
        }
    }
    public static void stop(String pkg){ensureReady();BlackBoxCore.get().stopPackage(pkg,USER_ID);Context c=appContext;if(c!=null)SandboxGuardService.stop(c);}
    public static void clearData(String pkg){ensureReady();BlackBoxCore.get().clearPackage(pkg,USER_ID);}
    public static void uninstall(String pkg){ensureReady();BlackBoxCore.get().uninstallPackageAsUser(pkg,USER_ID);Context c=appContext;if(c!=null)SandboxGuardService.stop(c);}
    public static boolean hasAllFilesAccess(){ensureReady();return BlackBoxCore.get().hasAllFilesAccess();}
    public static void requestAllFilesAccess(Activity a){ensureReady();BlackBoxCore.get().requestAllFilesAccess(a);}
    public static Activity resumedActivity(){return resumedActivity.get();}
    public static String resumedPackage(){return resumedPackage;}
    static Context hostContext(){return appContext;}

    private static String virtualPackage(){
        try{String p=BActivityThread.getAppPackageName();return p==null?"":p;}catch(Throwable ignored){return "";}
    }
    private static void ensureReady(){if(!isReady())throw new IllegalStateException(status());}
}
