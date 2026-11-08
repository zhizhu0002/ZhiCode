package com.zhizhu.zhicode.sandbox;

import android.app.ActivityManager;
import android.content.ComponentCallbacks;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.List;

import top.niunaijun.blackbox.entity.pm.InstallResult;

/**
 * Private same-UID RPC provider hosted in :iqsandbox. This is the only high-level
 * BlackBox control surface used by IQ Code's main process.
 */
public final class SandboxControlProvider extends ContentProvider {
    @Override public boolean onCreate() {
        // Providers are created before Application.onCreate(). BlackBox doCreate() must
        // stay in IQCodeApplication.onCreate(), matching the original IQSandbox order.
        // Running it here used to initialize the runtime too early on Android 15.
        SandboxDebugLog.event("SandboxControlProvider 已创建，等待 Application.onCreate 初始化引擎");
        // Provider publication happens before Application.onCreate. Queue a main-loop
        // initialization now so an immediate IPC from the dashboard cannot win the race.
        ZhiSandboxEngine.ensureCreateScheduled();
        return true;
    }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        Bundle b = new Bundle();
        b.putString("result", handle(getContext(), method, arg, extras).toString());
        return b;
    }

    /**
     * 一个控制动作的完整处理流程（校验 method → 解析 payload → dispatch → 兜错误）。
     *
     * <p>抽成静态方法是为了让**第二条通道**复用同一份逻辑：
     * 见 {@link SandboxMailboxReceiver} 与 {@link SandboxHostClient} 的说明 ——
     * 在沙箱里跑 ZhiCode 自己时，guest 的 ContentResolver 被 BlackBox 虚拟化，
     * 宿主 manifest 里的这个 provider 在 guest 内**根本解析不到**
     * （症状：`Unknown authority com.zhizhu.code.sandbox.control`）。
     * 两条通道必须共用同一套 dispatch，否则两边的行为会悄悄分叉。
     */
    static JSONObject handle(Context context, String method, String arg, Bundle extras) {
        JSONObject out;
        try {
            if (!"control".equals(method)) throw new IllegalArgumentException("unknown method: " + method);
            JSONObject in = new JSONObject(extras == null ? "{}" : extras.getString("payload", "{}"));
            out = dispatch(context, arg == null ? "" : arg, in);
        } catch (Throwable e) {
            out = new JSONObject();
            try { out.put("ok", false).put("error", e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage())); } catch (Throwable ignored) {}
            SandboxDebugLog.event("SandboxControl 失败: " + e);
        }
        return out;
    }

    private JSONObject dispatch(String action, JSONObject in) throws Exception {
        return dispatch(getContext(), action, in);
    }

    private static JSONObject dispatch(Context context, String action, JSONObject in) throws Exception {
        // The provider can receive Binder calls before Application.onCreate(). Wait for
        // the actual BlackBox controller result instead of returning “引擎初始化中”.
        ZhiSandboxEngine.awaitReady(30000);
        JSONObject o = new JSONObject().put("ok", true).put("process", SandboxProcessRole.processName(context));
        switch (action) {
            case "status":
                return o.put("status", ZhiSandboxEngine.status()).put("hide_root", ZhiSandboxEngine.isRootHidden())
                        .put("show_floating_log", SandboxSettingsStore.isFloatingLogEnabled(context));
            case "set_show_floating_log": {
                if (!in.has("show_floating_log")) throw new IllegalArgumentException("需要 show_floating_log");
                boolean enabled = in.getBoolean("show_floating_log");
                return o.put("show_floating_log", ZhiSandboxEngine.setFloatingLogEnabled(enabled));
            }
            case "set_hide_root": {
                if (!in.has("hide_root")) throw new IllegalArgumentException("需要 hide_root");
                boolean hidden = in.getBoolean("hide_root");
                boolean changed = ZhiSandboxEngine.setRootHidden(hidden);
                return o.put("hide_root", ZhiSandboxEngine.isRootHidden()).put("changed", changed);
            }
            case "list": {
                JSONArray a = new JSONArray();
                for (ApplicationInfo ai : ZhiSandboxEngine.installedApplications()) a.put(new JSONObject().put("package", ai.packageName));
                return o.put("apps", a);
            }
            case "install": {
                File apk = new File(in.optString("path", ""));
                if (!apk.isFile()) throw new IllegalArgumentException("APK 不存在: " + apk);
                /*
                 * 安装前的取证日志。
                 *
                 * BlackBox 的失败文案只有一句 `parser apk error`，它**不区分**
                 * 「文件读不到」和「APK 真的解析不了」—— 而这两者的修法完全不同。
                 * 所以在这里把引擎**自己看到的**事实记下来：
                 *   · 它收到的绝对路径（可能与调用方写的不是同一个）
                 *   · 文件能不能读、多大、前 4 字节（是不是 ZIP 的 `PK\x03\x04`）
                 *   · 这个进程的 filesDir / cacheDir（BlackBox 可能重映射过）
                 * 打开沙箱页的「诊断」就能看到这几行。
                 */
                String canonical = apk.getCanonicalPath();
                String head = "";
                try (java.io.FileInputStream fin = new java.io.FileInputStream(apk)) {
                    byte[] magic = new byte[4];
                    int n = fin.read(magic);
                    if (n > 0) {
                        StringBuilder b = new StringBuilder();
                        for (int i = 0; i < n; i++) b.append(String.format("%02x", magic[i]));
                        head = b.toString();
                    }
                } catch (Throwable ignored) {}
                SandboxDebugLog.event("install 取证: path=" + canonical
                        + " isFile=" + apk.isFile()
                        + " length=" + apk.length()
                        + " magic=" + head
                        + " readable=" + apk.canRead()
                        + " engineFiles=" + context.getFilesDir()
                        + " engineCache=" + context.getCacheDir());
                InstallResult r = ZhiSandboxEngine.install(apk);
                if (r == null) throw new IllegalStateException("沙箱安装没有返回结果");
                SandboxDebugLog.event("install 结果: success=" + r.success
                        + " pkg=" + r.packageName + " msg=" + r.msg);
                return o.put("success", r.success).put("package", r.packageName == null ? "" : r.packageName).put("message", r.msg == null ? "" : r.msg);
            }
            case "launch": {
                String pkg = requirePackage(in);
                boolean ok = ZhiSandboxEngine.launch(pkg);
                return o.put("success", ok).put("package", pkg);
            }
            case "stop": { String pkg=requirePackage(in); ZhiSandboxEngine.stop(pkg); return o.put("package",pkg); }
            case "clear_data": { String pkg=requirePackage(in); ZhiSandboxEngine.clearData(pkg); return o.put("package",pkg); }
            case "uninstall": { String pkg=requirePackage(in); ZhiSandboxEngine.uninstall(pkg); return o.put("package",pkg); }
            case "process_list": {
                String filter = in.optString("package", "").trim();
                JSONArray rows = new JSONArray();
                if (!filter.isEmpty()) appendProcesses(rows, filter);
                else for (ApplicationInfo ai : ZhiSandboxEngine.installedApplications()) appendProcesses(rows, ai.packageName);
                return o.put("processes", rows);
            }
            default: throw new IllegalArgumentException("未知 IQSandbox RPC action: " + action);
        }
    }

    private static void appendProcesses(JSONArray out, String pkg) throws Exception {
        List<ActivityManager.RunningAppProcessInfo> list = ZhiSandboxEngine.runningProcesses(pkg);
        for (ActivityManager.RunningAppProcessInfo p : list) {
            out.put(new JSONObject().put("package",pkg).put("pid",p.pid).put("uid",p.uid).put("process",p.processName).put("importance",p.importance));
        }
    }

    private static String requirePackage(JSONObject in) {
        String p = in.optString("package", "").trim();
        if (p.isEmpty()) throw new IllegalArgumentException("需要 package");
        return p;
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
}
