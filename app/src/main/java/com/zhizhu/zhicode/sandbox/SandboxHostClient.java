package com.zhizhu.zhicode.sandbox;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

import com.zhizhu.zhicode.compose.BuildConfig;

import org.json.JSONObject;

/**
 * 主进程 → `:iqsandbox` 进程的同步 IPC。
 *
 * <h3>为什么是 ContentProvider 而不是自定义 AIDL</h3>
 * 引擎的每个动作都是「发一个动作名 + 一段 JSON，收一段 JSON」。为这种形状写一套
 * AIDL 只是把同一份协议声明两遍，还得维护 parcelable。`ContentResolver.call`
 * 拿到的是 `Bundle`，塞字符串即可 —— 而 `SandboxControlProvider` 那边
 * 本来就已经用 JSON 做参数校验了。
 *
 * <h3>authority 为什么读 BuildConfig</h3>
 * 清单里写的是 `android:authorities="${applicationId}.sandbox.control"`。
 * 这里**必须**用同一个来源拼出来。曾经把它写成字面量，换包名时两边就会静默错开，
 * 症状是 `call()` 抛 `IllegalArgumentException: Unknown authority` ——
 * 而它被 catch 成一句"独立进程不可用"，看起来像引擎没起来。
 */
public final class SandboxHostClient {

    /** 与清单里 `${applicationId}.sandbox.control` 对应，**唯一来源是 applicationId**。 */
    public static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".sandbox.control";

    /**
     * 信箱通道的超时。
     *
     * <p>比 provider 那条长：这条路上 `:iqsandbox` 可能**还没启动**，
     * 广播对它来说是被丢弃的，要靠重发把它拉起来，而引擎
     * `create()` 本身在低端机上也确实要跑几秒。
     * 给 30s 是与 {@code SandboxControlProvider.dispatch} 里
     * `awaitReady(30000)` 对齐的结果 —— 短于它只会让"引擎正在初始化"被误报成失败。
     */
    private static final long CALL_TIMEOUT_MS = 30000L;

    private SandboxHostClient() {}

    /**
     * 主进程 → 引擎的一次同步调用。
     *
     * ## 两条通道，先 provider 后信箱
     *
     * | 情形 | provider | 信箱（文件 + 广播） |
     * |---|---|---|
     * | 正常真机运行 | ✅ | ✅（备用） |
     * | **ZhiCode 跑在 IQ 沙箱里** | ❌ `Unknown authority` | ✅ |
     *
     * 在沙箱里跑本应用时，guest 的 `ContentResolver` 被 BlackBox 虚拟化，
     * 宿主 manifest 的 provider 在 guest 内解析不到 —— 这不是权限问题，
     * 也不是 authority 拼错（宿主合并清单里那条是正确的）。
     * 详见 {@link SandboxControlMailbox} 的说明。
     *
     * ## 为什么要 catch 到 Throwable 再降级
     *
     * `ContentResolver.call` 在 authority 不存在时抛的是
     * `IllegalArgumentException`，而不是返回 null。只判 null 是不够的。
     * 但**不能**把所有异常都当成"该降级"：引擎自己抛出的业务错误
     * （`{ok:false, error:"APK 不存在"}`）是**正常结果**，走的是返回值而不是异常，
     * 所以这里 catch 到的基本都是通道层面的失败。
     */
    /**
     * 嵌套环境（本应用自己跑在别的虚拟运行时里）的固定文案。
     *
     * <p>**一处定义**：界面拿 `isUnsupportedHere()` 做判定，不要匹配这段字符串。
     * 两处各写一份是这类"文案驱动分支"最常见的坏味道 —— 改一处忘一处，
     * 卡片就会静默退回错误态。
     */
    public static final String NESTED_MESSAGE =
            "当前环境不支持沙箱引擎：ZhiCode 自身正运行在另一个虚拟运行时里，"
                    + "内层引擎拿不到独立进程。请在真机上运行 ZhiCode；"
                    + "要让 Agent 装包到真机，用 AndroidIntent install_apk。";

    /** 供界面直接判定，避免字符串匹配。 */
    public static boolean isUnsupportedHere() {
        return ZhiSandboxEngine.isNestedRuntime();
    }

    public static JSONObject call(Context context, String action, JSONObject payload) {
        /*
         * 嵌套环境：**立刻**给结论，一个 IPC 都不发。
         *
         * 判据来自启动期写入的 `ZhiSandboxEngine.isNestedRuntime()`，不在这里现场探测 ——
         * 现场探测要么重复读 dataDir，要么得发一次请求再等超时。
         *
         * 为什么这条路根本走不通（实测，不是推测）：
         * 外层 BlackBox 会把本应用的 `:iqsandbox` 也当成 guest 拉起来
         * （日志：`创建虚拟进程: com.zhizhu.code / com.zhizhu.code:iqsandbox`
         * 跑在 `com.iqge:p2` 里）。那个"引擎进程"的 filesDir、包解析、
         * `/proc/self/cmdline` 全部被外层虚拟化，于是：
         *   · provider —— guest 内解析不到宿主 authority；
         *   · 信箱   —— 结果文件落在主进程读不到的路径（实测 `sandbox/ipc/`
         *               从未被创建）。
         *
         * 所以这里直接返回，不让调用方白等 30 秒。
         */
        if (ZhiSandboxEngine.isNestedRuntime()) {
            return error(NESTED_MESSAGE);
        }

        JSONObject viaProvider = callViaProvider(context, action, payload);
        if (viaProvider != null) return viaProvider;
        SandboxDebugLog.event("provider 通道不可用，改用控制信箱: " + action);
        return SandboxControlMailbox.call(context, action, payload, CALL_TIMEOUT_MS);
    }

    private static JSONObject error(String message) {
        JSONObject o = new JSONObject();
        try {
            o.put("ok", false).put("error", message);
        } catch (Throwable ignored) {
        }
        return o;
    }

    /** @return 成功时返回引擎结果；通道不可用时返回 null（调用方降级）。 */
    private static JSONObject callViaProvider(Context context, String action, JSONObject payload) {
        try {
            Bundle in = new Bundle();
            in.putString("payload", payload == null ? "{}" : payload.toString());
            Bundle out = context.getContentResolver()
                    .call(Uri.parse("content://" + AUTHORITY), "control", action, in);
            if (out == null) return null;
            String raw = out.getString("result", "");
            if (raw.isEmpty()) return null;
            return new JSONObject(raw);
        } catch (IllegalArgumentException unknownAuthority) {
            // 沙箱内运行时的**正常**失败路径：guest 解析不到宿主 provider。
            return null;
        } catch (Throwable e) {
            SandboxDebugLog.event("provider 通道异常，改用控制信箱: " + e);
            return null;
        }
    }
}
