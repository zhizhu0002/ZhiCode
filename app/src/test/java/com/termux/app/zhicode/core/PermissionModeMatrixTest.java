package com.termux.app.zhicode.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.zhicode.model.SessionConfig;
import com.termux.app.zhicode.model.ToolCall;
import com.termux.app.zhicode.model.ToolExecutionResult;
import com.termux.app.zhicode.tools.ZhiTool;

import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

/**
 * 权限模式 × 工具类别的**行为**矩阵。
 *
 * <p>守的是一条真实事故：「每次询问」之外的档位里，用户把权限模式设成
 * **「不询问」之后几乎所有命令都执行失败**。真因是 {@code PermissionGate}
 * 对该模式直接 {@code return false}（拒绝执行），即「不问也不做」，
 * 而用户与 UI 文案理解的都是「不问就做」。
 *
 * <p>这类错误编译通过、守卫也看不出来（它不改变任何签名），只有跑一遍
 * 才能发现 —— 所以这里用真实的 {@link PermissionGate} 跑模式矩阵。
 *
 * <p>{@code PermissionGate} 不碰 {@code android.*}，可以直接在 JVM 上跑。
 */
public class PermissionModeMatrixTest {

    /**
     * 单条用例的硬上限。
     *
     * <p>必须有：`PermissionGate.ask()` 等不到用户回答时会等 **30 分钟**
     * （`TIMEOUT_MINUTES`）才返回「不同意」。测试里一旦有哪条路径没被正确应答，
     * 表现就是**整批测试挂住**而不是失败 —— 那比失败还难查。
     * 有了它，挂住会变成一条指名道姓的失败。
     */
    @Rule
    public Timeout globalTimeout = Timeout.seconds(10);

    private static final class FakeTool implements ZhiTool {
        private final String name;
        private final PermissionKind kind;
        private final boolean needsApproval;

        FakeTool(String name, PermissionKind kind, boolean needsApproval) {
            this.name = name;
            this.kind = kind;
            this.needsApproval = needsApproval;
        }

        @Override public String name() { return name; }
        @Override public String description() { return name; }
        @Override public JSONObject inputSchema() { return new JSONObject(); }
        @Override public PermissionKind permissionKind() { return kind; }
        @Override public boolean requiresApproval(JSONObject input) { return needsApproval; }
        @Override public ToolExecutionResult execute(SessionConfig config, JSONObject input) {
            return ToolExecutionResult.ok("ok");
        }
    }

    private static final ZhiTool READ =
            new FakeTool("Read", ZhiTool.PermissionKind.READ, false);
    private static final ZhiTool WRITE =
            new FakeTool("Write", ZhiTool.PermissionKind.WRITE, false);
    private static final ZhiTool SHELL =
            new FakeTool("Bash", ZhiTool.PermissionKind.SHELL, false);
    /** 逐次审批类：MCP 就是这种（分类是 NETWORK，但这次必须问）。 */
    private static final ZhiTool NETWORK_GATED =
            new FakeTool("mcp_call", ZhiTool.PermissionKind.NETWORK, true);

    /** 期望「放行」：require 返回 true 且**没有**弹出请求。 */
    private static void allows(String mode, ZhiTool tool) throws Exception {
        int[] asked = {0};
        boolean result = new PermissionGate().require(mode, tool, new ToolCall("t1", tool.name(), new JSONObject()), r -> asked[0]++);
        assertTrue(mode + " + " + tool.name() + " 应当放行", result);
        assertTrue(mode + " + " + tool.name() + " 放行时不该弹窗（实际弹了 " + asked[0] + " 次）",
                asked[0] == 0);
    }

    /** 期望「拒绝」：require 返回 false 且没有弹窗（不问也不做）。 */
    private static void denies(String mode, ZhiTool tool) throws Exception {
        int[] asked = {0};
        boolean result = new PermissionGate().require(mode, tool, new ToolCall("t1", tool.name(), new JSONObject()), r -> asked[0]++);
        assertFalse(mode + " + " + tool.name() + " 应当拒绝", result);
        assertTrue(mode + " + " + tool.name() + " 拒绝时不该弹窗", asked[0] == 0);
    }

    /** 期望「弹窗等用户回答」。这里只验证它走到询问（不回答，靠超时前的取消收尾）。 */
    private static void asks(String mode, ZhiTool tool) throws Exception {
        int[] asked = {0};
        PermissionGate gate = new PermissionGate();
        Thread worker = new Thread(() -> {
            try {
                gate.require(mode, tool, new ToolCall("t1", tool.name(), new JSONObject()), r -> {
                    asked[0]++;
                    gate.respond(r.requestId, true);
                });
            } catch (InterruptedException ignored) {
            }
        });
        worker.start();
        worker.join(5000);
        assertTrue(mode + " + " + tool.name() + " 应当弹窗询问", asked[0] > 0);
    }

    // ---------------------------------------------------------------- 默认档

    @Test
    public void defaultMode_onlyAsksForNonReadOnly() throws Exception {
        allows(PermissionModePolicy.DEFAULT, READ);
        asks(PermissionModePolicy.DEFAULT, WRITE);
        asks(PermissionModePolicy.DEFAULT, SHELL);
    }

    @Test
    public void acceptEdits_autoAllowsWritesButStillAsksForShell() throws Exception {
        allows(PermissionModePolicy.ACCEPT_EDITS, WRITE);
        asks(PermissionModePolicy.ACCEPT_EDITS, SHELL);
    }

    @Test
    public void autoMode_autoAllowsWritesButStillAsksForShell() throws Exception {
        // 「接受编辑」不等于「同意跑任意命令」—— auto 与 acceptEdits 在这点上一致，
        // 只自动放行改文件，shell 仍走确认。（写这条时我原本以为 auto 连 shell 也放行，
        // 测试直接挂到超时才发现 —— 这正是需要矩阵测试的理由。）
        allows(PermissionModePolicy.AUTO, WRITE);
        asks(PermissionModePolicy.AUTO, SHELL);
    }

    // ------------------------------------------------ 「不询问」= 不问就做

    @Test
    public void dontAsk_executesInsteadOfDenying() throws Exception {
        // 这条就是事故本身：修之前 SHELL/WRITE 全部 return false。
        allows(PermissionModePolicy.DONT_ASK, WRITE);
        allows(PermissionModePolicy.DONT_ASK, SHELL);
        allows(PermissionModePolicy.DONT_ASK, READ);
    }

    @Test
    public void dontAsk_stillAsksForCallsThatRequireApproval() throws Exception {
        // UI 文案是「不再弹出确认，高风险操作仍会提示」—— 两者必须一致。
        asks(PermissionModePolicy.DONT_ASK, NETWORK_GATED);
    }

    @Test
    public void bypass_suppressesEvenApprovalRequiredCalls() throws Exception {
        // bypass 是「别问我」，压在逐次审批之上。
        allows(PermissionModePolicy.BYPASS, NETWORK_GATED);
        allows(PermissionModePolicy.BYPASS, SHELL);
    }

    // -------------------------------------------------------- 规划 = 只读

    @Test
    public void planMode_deniesAnythingThatNeedsConfirmation() throws Exception {
        allows(PermissionModePolicy.PLAN, READ);
        denies(PermissionModePolicy.PLAN, WRITE);
        denies(PermissionModePolicy.PLAN, SHELL);
        denies(PermissionModePolicy.PLAN, NETWORK_GATED);
    }

    @Test
    public void unknownModeFallsBackToDefaultSemantics() throws Exception {
        allows("完全没听过的模式", READ);
    }
}
