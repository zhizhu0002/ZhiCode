package com.iqge.sandbox;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import org.json.JSONObject;

/** Synchronous IPC from IQ Code/Agent into the isolated :iqsandbox host process. */
public final class SandboxHostClient {
    /**
     * Derived from the real applicationId rather than hardcoded.
     *
     * Provider authorities are device-global: if the original com.iqge build is installed
     * alongside this one, a hardcoded "com.iqge.sandbox.control" on both sides makes the
     * second install fail with INSTALL_FAILED_CONFLICTING_PROVIDER. The manifest declares
     * the matching "${applicationId}.sandbox.control".
     */
    public static String authority(Context context) {
        return context.getPackageName() + ".sandbox.control";
    }

    private SandboxHostClient() {}

    public static JSONObject call(Context context, String action, JSONObject payload) {
        try {
            Bundle in = new Bundle();
            in.putString("payload", payload == null ? "{}" : payload.toString());
            Uri uri = Uri.parse("content://" + authority(context));
            Bundle out = context.getContentResolver().call(uri, "control", action, in);
            if (out == null) return error("IQSandbox 进程没有返回结果");
            String raw = out.getString("result", "");
            if (raw.isEmpty()) return error("IQSandbox 返回空结果");
            return new JSONObject(raw);
        } catch (Throwable e) {
            return error("IQSandbox 独立进程不可用: " + e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()));
        }
    }

    private static JSONObject error(String message) {
        JSONObject o = new JSONObject();
        try { o.put("ok", false).put("error", message); } catch (Throwable ignored) {}
        return o;
    }
}
