package com.iqge.sandbox;

import android.content.Context;

import org.json.JSONObject;

/**
 * guest 进程原生调试（`proc_*`）的占位实现。
 *
 * <p><b>这是 Phase 7（Bcore 移植）之前的桩。</b>原版这一个类提供
 * {@code proc_info / proc_maps / proc_modules / proc_threads / proc_memory_read /
 * proc_memory_write / proc_load_library / proc_thread_dump / proc_gc / proc_frida_*}
 * 等动作，它跑在沙箱 guest 进程里，通过 Frida 通道访问目标进程内存。
 *
 * <p>本桩不假装成功：返回结构化的错误对象，让上层（`IQDebugTool` /
 * `SandboxAgentBridge`）把「沙箱尚未移植」如实转达给 Agent，而不是给出空结果。
 */
public final class SandboxProcessDebug {

    /** 错误码，便于上层判断这是"未移植"而不是"目标进程不存在"。 */
    public static final String NOT_PORTED = "sandbox_not_ported";

    /**
     * 与原版保持同样的签名，便于 Phase 7 直接替换实现而不动调用点。
     *
     * @return 永远是一个带 {@link #NOT_PORTED} 的错误对象
     */
    public static JSONObject dispatch(Context context, String action, JSONObject payload) throws Exception {
        JSONObject out = new JSONObject();
        out.put("ok", false);
        out.put("error", NOT_PORTED);
        out.put("action", action == null ? "" : action);
        out.put("message", "IQ 沙箱尚未移植到本工程（Phase 7）。原生进程调试不可用。");
        return out;
    }

    private SandboxProcessDebug() {}
}
