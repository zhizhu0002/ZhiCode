package com.zhizhu.zhicode.sandbox;

import android.app.ActivityManager;
import android.content.ContentProvider;
import android.content.ContentValues;
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
 * 控制器进程（{@code :zhisandbox}）里的同 UID IPC 服务端 —— 宿主访问沙箱的**唯一**高层入口。
 *
 * <p>动作名是对外契约，与 Agent 工具（{@code zhisandbox} 命令）和系统提示词共享，
 * 改动会连带改提示词，因此保持不变。
 */
public final class SandboxRpcService extends ContentProvider {

    @Override
    public boolean onCreate() {
        // ContentProvider 的创建早于 Application.onCreate。引擎的 doCreate() 必须留在
        // Application.onCreate 里执行（在这里跑过，会在 Android 15 上过早初始化运行时）。
        SandboxStage.mark("rpc:provider-created");
        SandboxConsole.event("SandboxRpcService 已创建，等待 Application.onCreate 初始化引擎");
        // provider 发布早于 Application.onCreate，这里补排一次主循环初始化，
        // 免得管理界面立刻发来的 IPC 抢在 Application.onCreate 前面。
        ZhiSandbox.ensureCreateScheduled();
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Bundle bundle = new Bundle();
        JSONObject out;
        try {
            if (!"control".equals(method)) throw new IllegalArgumentException("未知方法: " + method);
            JSONObject in = new JSONObject(extras == null ? "{}" : extras.getString("payload", "{}"));
            out = dispatch(arg == null ? "" : arg, in);
        } catch (Throwable error) {
            out = new JSONObject();
            try {
                out.put("ok", false)
                        .put("error", error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage()))
                        // 把该进程最后一条阶段一并带回去：调用方拿到的错误里就能看出「卡在哪一步」，
                        // 不必再去翻日志文件。
                        .put("stage", SandboxStage.lastMark())
                        .put("status", ZhiSandbox.status());
            } catch (Throwable ignored) {
                // 组装错误对象失败时返回空对象
            }
            SandboxConsole.event("SandboxRpcService 失败: " + error);
        }
        bundle.putString("result", out.toString());
        return bundle;
    }

    private JSONObject dispatch(String action, JSONObject in) throws Exception {
        // provider 可能在 Application.onCreate 之前收到 Binder 调用。
        // 这里等真实结果，而不是直接返回「引擎初始化中」。
        ZhiSandbox.awaitReady(12000);
        JSONObject out = new JSONObject()
                .put("ok", true)
                .put("process", SandboxProcess.name(getContext()))
                .put("stage", SandboxStage.lastMark());
        switch (action) {
            case "status":
                return out.put("status", ZhiSandbox.status())
                        .put("hide_root", ZhiSandbox.isRootHidden())
                        .put("show_floating_log", SandboxPrefs.isFloatingLogEnabled(getContext()));
            case "set_show_floating_log": {
                if (!in.has("show_floating_log")) throw new IllegalArgumentException("需要 show_floating_log");
                boolean enabled = in.getBoolean("show_floating_log");
                return out.put("show_floating_log", ZhiSandbox.setFloatingLogEnabled(enabled));
            }
            case "set_hide_root": {
                if (!in.has("hide_root")) throw new IllegalArgumentException("需要 hide_root");
                boolean hidden = in.getBoolean("hide_root");
                boolean changed = ZhiSandbox.setRootHidden(hidden);
                return out.put("hide_root", ZhiSandbox.isRootHidden()).put("changed", changed);
            }
            case "list": {
                JSONArray apps = new JSONArray();
                for (ApplicationInfo info : ZhiSandbox.installedApplications()) {
                    apps.put(new JSONObject().put("package", info.packageName));
                }
                return out.put("apps", apps);
            }
            case "install": {
                File apk = new File(in.optString("path", ""));
                if (!apk.isFile()) throw new IllegalArgumentException("APK 不存在: " + apk);
                InstallResult result = ZhiSandbox.install(apk);
                if (result == null) throw new IllegalStateException("沙箱安装没有返回结果");
                return out.put("success", result.success)
                        .put("package", result.packageName == null ? "" : result.packageName)
                        .put("message", result.msg == null ? "" : result.msg);
            }
            case "launch": {
                String pkg = requirePackage(in);
                boolean launched = ZhiSandbox.launch(pkg);
                return out.put("success", launched).put("package", pkg);
            }
            case "stop": {
                String pkg = requirePackage(in);
                ZhiSandbox.stop(pkg);
                return out.put("package", pkg);
            }
            case "clear_data": {
                String pkg = requirePackage(in);
                ZhiSandbox.clearData(pkg);
                return out.put("package", pkg);
            }
            case "uninstall": {
                String pkg = requirePackage(in);
                ZhiSandbox.uninstall(pkg);
                return out.put("package", pkg);
            }
            case "process_list": {
                String filter = in.optString("package", "").trim();
                JSONArray rows = new JSONArray();
                if (!filter.isEmpty()) {
                    appendProcesses(rows, filter);
                } else {
                    for (ApplicationInfo info : ZhiSandbox.installedApplications()) {
                        appendProcesses(rows, info.packageName);
                    }
                }
                return out.put("processes", rows);
            }
            case "stages": {
                // 诊断用：一次拿到全部进程的启动阶段
                return out.put("stages", SandboxStage.dump(getContext()));
            }
            default:
                throw new IllegalArgumentException("未知沙箱动作: " + action);
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

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
}
