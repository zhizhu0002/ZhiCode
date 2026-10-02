package com.termux.app.zhicode.core;

import com.termux.app.zhicode.model.ToolCall;
import com.termux.app.zhicode.tools.ZhiTool;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 向用户确认一次工具调用的中转站。
 *
 * <h3>什么情况下根本不该问</h3>
 * 这个类最重要的部分是 {@link #require} 开头那几行「直接放行」与「直接拒绝」。
 * 它们把「确认」限制在真正需要用户判断的那一小块上：
 * <ul>
 *   <li><b>只读与联网不问。</b>读文件、grep、搜网页是 agent 工作的常态。
 *       每次都问会让用户把确认框当噪音，于是真正危险的那次也被顺手点掉。</li>
 *   <li><b>{@code bypassPermissions} 一律放行；{@code plan} 与 {@code dontAsk}
 *       一律拒绝。</b>这两档是用户明确表达过的意图：前者「都别问我」，
 *       后者「都别做」。注意是**拒绝**而不是询问 —— 询问等于给了用户一个
 *       他自己已经放弃的机会。</li>
 *   <li><b>改文件在 {@code acceptEdits}/{@code auto} 下放行。</b>
 *       这两档的名字就说明了意图（接受编辑），而它们**不**放行 shell 与系统类工具：
 *       「接受编辑」不等于「同意跑任意命令」。</li>
 * </ul>
 * 剩下的（{@code default} 下的写、所有 shell 与系统调用）才走确认流程。
 *
 * <h3>没人应答 = 不同意</h3>
 * 超时的结果是 {@code false}。与 {@link PlanApprovalGate} 同理：
 * 没有任何自动化路径能让一次未被回答的确认变成许可。
 */
public final class PermissionGate {

    /** 等待用户回答的最长时间。超时按「不同意」处理。 */
    private static final long TIMEOUT_MINUTES = 30;

    /** 一次待确认的请求。字段对界面可见（它要渲染这次调用），所以是 public final。 */
    public static final class PermissionRequest {
        public final String requestId;
        public final ToolCall call;
        public final ZhiTool.PermissionKind kind;
        /** 当时的有效权限模式，用于在界面上说明「为什么现在要问」。 */
        public final String mode;

        private final CountDownLatch answered = new CountDownLatch(1);
        /** {@code null} = 还没回答；{@code FALSE} = 拒绝。 */
        private volatile Boolean allowed;

        PermissionRequest(ToolCall call, ZhiTool.PermissionKind kind, String mode) {
            this.requestId = UUID.randomUUID().toString();
            this.call = call;
            this.kind = kind;
            this.mode = mode;
        }
    }

    /** 界面侧的通知回调。 */
    public interface Listener {
        void onPermissionRequested(PermissionRequest request);
    }

    private final Map<String, PermissionRequest> pending = new ConcurrentHashMap<>();

    /**
     * 判断一次调用是否需要用户确认。
     *
     * @return 是否可以执行。返回 true 的路径不产生任何界面交互
     */
    public boolean require(String effectiveMode, ZhiTool tool, ToolCall call, Listener listener)
            throws InterruptedException {
        String mode = PermissionModePolicy.normalize(effectiveMode);
        ZhiTool.PermissionKind kind = tool.permissionKind();

        // 「这个工具这次必须问」要**最先**判，且必须在 isAlwaysAllowed 之前。
        //
        // ⚠️ 顺序是这段代码的全部要点：MCP 属于 NETWORK，而 NETWORK 在
        // isAlwaysAllowed 里是**永远放行**的。把这个判断放到它后面，逐工具审批
        // 就是个摆设 —— 开关点得动、存得下、界面也对，只是从不拦截任何东西，
        // 而且不会有任何报错。
        //
        // bypass 仍然优先：那一档是用户明确说过"别问我"，逐工具审批不该推翻它。
        // PLAN / DONT_ASK 也照旧拒绝（它们的语义是"不执行需要确认的东西"，
        // 不是"那就直接执行"）。
        if (tool.requiresApproval(call.input) && !PermissionModePolicy.BYPASS.equals(mode)) {
            if (PermissionModePolicy.PLAN.equals(mode) || PermissionModePolicy.DONT_ASK.equals(mode)) {
                return false;
            }
            return ask(call, kind, mode, listener);
        }

        // 见类注释：三段「不用问」的判断，顺序无关但都要在询问之前。
        if (isAlwaysAllowed(kind)) return true;
        if (PermissionModePolicy.BYPASS.equals(mode)) return true;
        if (PermissionModePolicy.PLAN.equals(mode) || PermissionModePolicy.DONT_ASK.equals(mode)) {
            return false;
        }
        if (writesAreAccepted(mode, kind)) return true;

        return ask(call, kind, mode, listener);
    }

    /**
     * 只读类工具永远直接放行。
     *
     * <p>{@link ZhiTool.PermissionKind#NETWORK} 也在其中：联网是搜索与读取网页，
     * 不改用户任何东西。
     */
    private static boolean isAlwaysAllowed(ZhiTool.PermissionKind kind) {
        return kind == ZhiTool.PermissionKind.INTERNAL
            || kind == ZhiTool.PermissionKind.READ
            || kind == ZhiTool.PermissionKind.NETWORK;
    }

    /** 见类注释：只对「改文件」，且只在这两档模式下。 */
    private static boolean writesAreAccepted(String mode, ZhiTool.PermissionKind kind) {
        if (kind != ZhiTool.PermissionKind.WRITE) return false;
        return PermissionModePolicy.ACCEPT_EDITS.equals(mode)
            || PermissionModePolicy.AUTO.equals(mode);
    }

    /**
     * 登记请求、通知界面、等回答。
     *
     * <p>{@code finally} 里摘掉自己：无论正常返回、超时还是被中断，
     * 待确认表都不该留下一条永远没人回答的记录 —— 那会让界面一直显示着那张卡片。
     */
    private boolean ask(ToolCall call, ZhiTool.PermissionKind kind, String mode, Listener listener)
            throws InterruptedException {
        PermissionRequest request = new PermissionRequest(call, kind, mode);
        pending.put(request.requestId, request);
        try {
            if (listener != null) listener.onPermissionRequested(request);
            boolean answered = request.answered.await(TIMEOUT_MINUTES, TimeUnit.MINUTES);
            return answered && Boolean.TRUE.equals(request.allowed);
        } finally {
            pending.remove(request.requestId);
        }
    }

    /**
     * 界面提交决定。
     *
     * @return 是否有这样一个待确认的请求。重复提交时返回 false ——
     *         界面的重复点击不该覆盖掉用户第一次的决定
     */
    public boolean respond(String requestId, boolean allow) {
        PermissionRequest request = pending.get(requestId);
        if (request == null) return false;
        request.allowed = allow;
        request.answered.countDown();
        return true;
    }

    /**
     * 把所有待确认请求判为拒绝并清空（会话中断、界面销毁时调用）。
     *
     * <p>判成**拒绝**而不是放行：中断与销毁都不是用户的许可。
     */
    public void cancelAll() {
        for (PermissionRequest request : pending.values()) {
            request.allowed = false;
            request.answered.countDown();
        }
        pending.clear();
    }
}
