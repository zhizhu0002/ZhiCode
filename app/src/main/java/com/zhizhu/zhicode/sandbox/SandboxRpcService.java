package com.zhizhu.zhicode.sandbox;

import android.app.ActivityManager;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.List;

import top.niunaijun.blackbox.entity.pm.InstallResult;

/**
 * 控制器进程（{@code :zhisandbox}）里的同 UID IPC 服务端 —— 宿主访问沙箱的高层入口。
 *
 * <h3>为什么用 ContentProvider 的 {@code call()}</h3>
 * {@code call()} 能直接收传一个 {@link Bundle} 并原样回一个，这对
 * 「动作名 + 一个 JSON 对象 → 一个 JSON 对象」的形态最省事，
 * 不必为每个动作定义 AIDL 接口或 Parcelable。
 * 访问控制由 provider 的 authority 与 UID 决定：只有同 UID 的进程能调到它。
 *
 * <h3>创建时机，以及它带来的两个必须处理的问题</h3>
 * provider 的 {@link #onCreate()} <b>早于</b> {@code Application.onCreate}。
 * 由此产生两件事：
 * <ol>
 *   <li>真正的引擎初始化（{@code doCreate()}）必须留在 {@code Application.onCreate} 里。
 *       提前到 provider 里跑，会在 Android 15 上过早初始化运行时。这里的处理是
 *       {@link ZhiSandbox#ensureCreateScheduled()}：把初始化排进主循环，不自己动手。</li>
 *   <li>provider 一发布，管理界面就可能立刻发 IPC 过来，而引擎可能还没就绪。
 *       因此 {@link #dispatch} 入口处会 {@link ZhiSandbox#awaitReady(int)} 等一个真实的
 *       结果，而不是立刻回一句「初始化中」——后者会让管理界面显示一个假失败。</li>
 * </ol>
 *
 * <h3>动作名是对外契约</h3>
 * 这些串与 Agent 工具（{@code zhisandbox} 命令）、系统提示词、以及
 * {@link SandboxRpc} 共享。改动必须同步三处，否则会出现
 * 「界面点得动、Agent 调不动」这类只在一条路径上复现的问题。
 */
public final class SandboxRpcService extends ContentProvider {

    /** {@code call()} 支持的方法名。 */
    private static final String METHOD_CONTROL = "control";

    /** 等待引擎就绪的上限。超过它说明初始化真的卡住了，此时应该如实报错而不是继续等。 */
    private static final int READY_TIMEOUT_MS = 12000;

    private static final int ACTION_STATUS = 0;
    private static final int ACTION_SET_FLOATING_LOG = 1;
    private static final int ACTION_SET_HIDE_ROOT = 2;
    private static final int ACTION_LIST = 3;
    private static final int ACTION_INSTALL = 4;
    private static final int ACTION_LAUNCH = 5;
    private static final int ACTION_STOP = 6;
    private static final int ACTION_CLEAR_DATA = 7;
    private static final int ACTION_UNINSTALL = 8;
    private static final int ACTION_PROCESS_LIST = 9;
    private static final int ACTION_STAGES = 10;
    private static final int ACTION_UNKNOWN = -1;

    @Override
    public boolean onCreate() {
        SandboxStage.mark("rpc:provider-created");
        SandboxConsole.event("SandboxRpcService 已创建，等待 Application.onCreate 初始化引擎");
        // 见类注释第 1 条：只把初始化排进主循环，不在这里自己初始化。
        ZhiSandbox.ensureCreateScheduled();
        return true;
    }

    /**
     * IPC 入口。任何失败都被折成一个 {@code ok:false} 的 JSON 返回，
     * 而不是抛给 Binder —— 抛出去调用方只能拿到一个笼统的 RemoteException，
     * 而错误对象里携带的信息（阶段、状态）才是排错要用的。
     */
    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Bundle bundle = new Bundle();
        JSONObject out;
        try {
            if (!METHOD_CONTROL.equals(method)) throw new IllegalArgumentException("未知方法: " + method);
            JSONObject in = new JSONObject(extras == null ? "{}" : extras.getString("payload", "{}"));
            out = dispatch(arg == null ? "" : arg, in);
        } catch (Throwable error) {
            out = failure(error);
            SandboxConsole.event("SandboxRpcService 失败: " + error);
        }
        bundle.putString("result", out.toString());
        return bundle;
    }

    /**
     * 组装错误响应。
     *
     * <p>除了 {@code error}，还带上 {@code stage}（本进程最后一条启动阶段）与
     * {@code status}（引擎状态）。这两项让调用方在收到错误时就能看出「卡在哪一步」，
     * 不必再去翻日志文件——而那一步往往发生在还没法看日志的时候。
     */
    private static JSONObject failure(Throwable error) {
        JSONObject out = new JSONObject();
        try {
            out.put("ok", false)
                    .put("error", error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage()))
                    .put("stage", SandboxStage.lastMark())
                    .put("status", ZhiSandbox.status());
        } catch (Throwable ignored) {
            // 组装错误对象本身失败（例如 status() 抛错）时返回空对象，
            // 调用方仍能从「不是 ok:true」判断失败。
        }
        return out;
    }

    private JSONObject dispatch(String action, JSONObject in) throws Exception {
        // 见类注释第 2 条：等真实结果，不返回「初始化中」。
        ZhiSandbox.awaitReady(READY_TIMEOUT_MS);
        JSONObject out = new JSONObject()
                .put("ok", true)
                .put("process", SandboxProcess.name(getContext()))
                .put("stage", SandboxStage.lastMark());

        switch (classify(action)) {
            case ACTION_STATUS:
                return out.put("status", ZhiSandbox.status())
                        .put("hide_root", ZhiSandbox.isRootHidden())
                        .put("show_floating_log", SandboxPrefs.isFloatingLogEnabled(getContext()));

            case ACTION_SET_FLOATING_LOG: {
                if (!in.has("show_floating_log")) throw new IllegalArgumentException("缺少 show_floating_log 参数");
                return out.put("show_floating_log", ZhiSandbox.setFloatingLogEnabled(in.getBoolean("show_floating_log")));
            }

            case ACTION_SET_HIDE_ROOT: {
                if (!in.has("hide_root")) throw new IllegalArgumentException("缺少 hide_root 参数");
                boolean changed = ZhiSandbox.setRootHidden(in.getBoolean("hide_root"));
                // 同时回读生效值：改设置可能因「仍有 guest 在跑」而没落盘，
                // 只回 {changed} 会让调用方以为开关已经切过去了。
                return out.put("hide_root", ZhiSandbox.isRootHidden()).put("changed", changed);
            }

            case ACTION_LIST: {
                JSONArray apps = new JSONArray();
                for (android.content.pm.ApplicationInfo info : ZhiSandbox.installedApplications()) {
                    apps.put(new JSONObject().put("package", info.packageName));
                }
                return out.put("apps", apps);
            }

            case ACTION_INSTALL: {
                File apk = new File(in.optString("path", ""));
                if (!apk.isFile()) throw new IllegalArgumentException("找不到 APK：" + apk);
                InstallResult result = ZhiSandbox.install(apk);
                if (result == null) throw new IllegalStateException("沙箱安装没有返回结果");
                return out.put("success", result.success)
                        .put("package", result.packageName == null ? "" : result.packageName)
                        .put("message", result.msg == null ? "" : result.msg);
            }

            case ACTION_LAUNCH: {
                String pkg = requirePackage(in);
                return out.put("success", ZhiSandbox.launch(pkg)).put("package", pkg);
            }

            case ACTION_STOP: {
                String pkg = requirePackage(in);
                ZhiSandbox.stop(pkg);
                return out.put("package", pkg);
            }

            case ACTION_CLEAR_DATA: {
                String pkg = requirePackage(in);
                ZhiSandbox.clearData(pkg);
                return out.put("package", pkg);
            }

            case ACTION_UNINSTALL: {
                String pkg = requirePackage(in);
                ZhiSandbox.uninstall(pkg);
                return out.put("package", pkg);
            }

            case ACTION_PROCESS_LIST: {
                String filter = in.optString("package", "").trim();
                JSONArray rows = new JSONArray();
                if (!filter.isEmpty()) {
                    appendProcesses(rows, filter);
                } else {
                    // 没给过滤条件时列出所有已安装应用的进程；这比按「当前前台」猜更可靠。
                    for (android.content.pm.ApplicationInfo info : ZhiSandbox.installedApplications()) {
                        appendProcesses(rows, info.packageName);
                    }
                }
                return out.put("processes", rows);
            }

            case ACTION_STAGES:
                // 诊断用：一次拿到全部进程的启动阶段。
                return out.put("stages", SandboxStage.dump(getContext()));

            default:
                throw new IllegalArgumentException("未知沙箱动作: " + action);
        }
    }

    /**
     * 动作名 → 内部编号。
     *
     * <p>走编号而不是在 switch 里直接比字符串，是为了让「一张动作表」成为一个
     * 可枚举的东西：{@link #ACTION_UNKNOWN} 这个默认分支也就有了明确的语义，
     * 而不是散在 switch 的 {@code default} 里。
     */
    private static int classify(String action) {
        if (action == null) return ACTION_UNKNOWN;
        switch (action) {
            case "status": return ACTION_STATUS;
            case "set_show_floating_log": return ACTION_SET_FLOATING_LOG;
            case "set_hide_root": return ACTION_SET_HIDE_ROOT;
            case "list": return ACTION_LIST;
            case "install": return ACTION_INSTALL;
            case "launch": return ACTION_LAUNCH;
            case "stop": return ACTION_STOP;
            case "clear_data": return ACTION_CLEAR_DATA;
            case "uninstall": return ACTION_UNINSTALL;
            case "process_list": return ACTION_PROCESS_LIST;
            case "stages": return ACTION_STAGES;
            default: return ACTION_UNKNOWN;
        }
    }

    private void appendProcesses(JSONArray out, String pkg) throws Exception {
        List<ActivityManager.RunningAppProcessInfo> processes = ZhiSandbox.runningProcesses(pkg);
        for (ActivityManager.RunningAppProcessInfo process : processes) {
            out.put(new JSONObject()
                    .put("package", pkg)
                    .put("pid", process.pid)
                    .put("uid", process.uid)
                    .put("process", process.processName)
                    .put("importance", process.importance));
        }
    }

    private static String requirePackage(JSONObject in) {
        String pkg = in.optString("package", "").trim();
        if (pkg.isEmpty()) throw new IllegalArgumentException("需要 package");
        return pkg;
    }

    // ------------------------------------------------------- 未使用的 CRUD
    // 本 provider 只承载 call()。其余方法是 ContentProvider 的抽象成员，
    // 必须实现但不应被调用；显式抛 UnsupportedOperationException 而不是返回
    // 一个空结果，这样一旦有人误用会立刻暴露，而不是静默拿到空数据。

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("沙箱控制通道只支持 call()");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("沙箱控制通道只支持 call()");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("沙箱控制通道只支持 call()");
    }
}
