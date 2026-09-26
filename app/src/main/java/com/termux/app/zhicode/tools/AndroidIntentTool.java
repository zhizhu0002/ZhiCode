package com.termux.app.zhicode.tools;

import android.content.Context;

import com.termux.app.zhicode.model.SessionConfig;
import com.termux.app.zhicode.model.ToolExecutionResult;

import org.json.JSONObject;

/**
 * 让模型打开别的 App、网址或系统页面。
 *
 * <h3>它为什么不能走 shell</h3>
 * 用 {@code am start} 起意图的进程是 Termux 的 shell，Android 会把调用方身份认成
 * 那个 shell 所属的应用，于是「谁能启动这个组件」的判定、以及
 * {@code FileProvider} 的授权都算在错的 UID 上。这个工具从本应用进程发起意图，
 * Android 看到的调用方才是正确的。
 *
 * <h3>只是转交</h3>
 * 真正的意图构造与启动都在 {@link AndroidIntentBridge} 里 —— 那样它也能被
 * 界面直接调用（用户点一个链接），而界面不该为了开个网址去构造一次工具调用。
 */
final class AndroidIntentTool implements ZhiTool {

    private final AndroidIntentBridge bridge;

    public AndroidIntentTool(Context context) {
        this(new AndroidIntentBridge(context));
    }

    /** 供测试直接注入桥。 */
    AndroidIntentTool(AndroidIntentBridge bridge) {
        this.bridge = bridge;
    }

    @Override public String name() { return "AndroidIntent"; }

    @Override public String description() {
        return "Open Android intents/apps/URLs or install an APK; use instead of shell am start.";
    }

    @Override public JSONObject inputSchema() {
        try {
            JSONObject properties = new JSONObject();
            properties.put("operation", ToolSchemas.string("intent (default), launch_app, or install_apk"));
            properties.put("action", ToolSchemas.string("Android intent action, default android.intent.action.VIEW"));
            properties.put("uri", ToolSchemas.string(
                "Optional data URI such as https://www.google.com, tel:, geo:, package:, or content:"));
            properties.put("path", ToolSchemas.string(
                "Absolute local path for install_apk, normally under /storage/emulated/0 or ZhiCode HOME."));
            properties.put("package", ToolSchemas.string("Optional target package for launch_app or explicit intents"));
            properties.put("app_name", ToolSchemas.string(
                "Optional visible app name for launch_app when package is unknown"));
            properties.put("component", ToolSchemas.string("Optional flattened component package/.Activity"));
            properties.put("mime_type", ToolSchemas.string("Optional MIME type"));
            properties.put("categories", ToolSchemas.stringArray("Optional Android intent categories"));
            properties.put("extras", ToolSchemas.freeObject("Optional primitive Android intent extras"));
            properties.put("flags", ToolSchemas.integer("Optional Android Intent flags bitmask", 0));
            return ToolSchemas.object(properties);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 它会启动应用之外的组件，所以是最宽的一档权限。 */
    @Override public PermissionKind permissionKind() { return PermissionKind.SYSTEM; }

    @Override public ToolExecutionResult execute(SessionConfig config, JSONObject input) {
        return bridge.execute(input);
    }
}
