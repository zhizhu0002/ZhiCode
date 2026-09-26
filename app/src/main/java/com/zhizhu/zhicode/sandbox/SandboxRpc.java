package com.zhizhu.zhicode.sandbox;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

import org.json.JSONObject;

/**
 * 宿主侧的同 UID IPC 客户端，用来访问沙箱控制器进程（{@code :zhisandbox}）里的
 * {@link SandboxRpcService}。
 *
 * <p>authority 由<b>运行时真实包名</b>派生，不写死。provider authority 是设备全局的：
 * 若把包名写死，本应用与另一个同源构建（例如原始 IQ Code）同时安装时，
 * 第二份会直接报 {@code INSTALL_FAILED_CONFLICTING_PROVIDER}。
 * 清单里声明的 {@code ${applicationId}.sandbox.control} 与本方法必须一致。
 */
public final class SandboxRpc {

    private SandboxRpc() {}

    public static String authority(Context context) {
        return context.getPackageName() + ".sandbox.control";
    }

    /** 同步调用控制器。任何异常都转成 {@code {"ok":false,"error":...}}，不向调用方抛。 */
    public static JSONObject call(Context context, String action, JSONObject payload) {
        try {
            Bundle request = new Bundle();
            request.putString("payload", payload == null ? "{}" : payload.toString());
            Uri uri = Uri.parse("content://" + authority(context));
            Bundle response = context.getContentResolver().call(uri, "control", action, request);
            if (response == null) return error("沙箱控制器进程没有返回结果");
            String raw = response.getString("result", "");
            if (raw.isEmpty()) return error("沙箱控制器返回空结果");
            return new JSONObject(raw);
        } catch (Throwable error) {
            return error("沙箱控制器进程不可用: " + error.getClass().getSimpleName()
                    + ": " + String.valueOf(error.getMessage()));
        }
    }

    private static JSONObject error(String message) {
        JSONObject out = new JSONObject();
        try {
            out.put("ok", false).put("error", message);
        } catch (Throwable ignored) {
            // 构造错误对象失败时只能返回空对象，调用方按 ok 缺失处理
        }
        return out;
    }
}
