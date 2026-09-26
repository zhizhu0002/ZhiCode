package com.termux.app.zhicode.core;

import com.termux.app.zhicode.model.PlanWorkflowState;

import java.util.Arrays;
import java.util.List;

/**
 * 权限模式：用户拥有的基线，加上几条运行时规则。
 *
 * <h3>关键性质：运行时规则只能收紧，不能放宽</h3>
 * 用户在设置里选的模式是**基线**。引擎在运行中会临时改变有效模式
 * （进入计划模式、子代理要求自己的模式），但任何一条规则都不允许
 * 造出比基线更宽的权限。这不是礼貌问题：模式决定哪些工具会向用户确认，
 * 放宽了就等于绕过了用户的授权决定。
 *
 * <h3>计划模式是叠加的，不是切换</h3>
 * 进入计划流程时有效模式变成 {@code plan}，但**用户自己的基线不变**
 * （它记在 {@link PlanWorkflowState#previousPermissionMode}）。计划结束、
 * 或者用户点了「继续讨论」之后，基线还在原处 —— 否则「先进计划看看再回来」
 * 会顺手把用户设的 {@code acceptEdits} 弄丢。
 */
public final class PermissionModePolicy {

    public static final String DEFAULT = "default";
    public static final String ACCEPT_EDITS = "acceptEdits";
    public static final String AUTO = "auto";
    public static final String PLAN = "plan";
    public static final String DONT_ASK = "dontAsk";
    public static final String BYPASS = "bypassPermissions";

    /** 全部已知模式。{@link #normalize} 只认这一份名单。 */
    private static final List<String> KNOWN = Arrays.asList(
        DEFAULT, ACCEPT_EDITS, AUTO, PLAN, DONT_ASK, BYPASS);

    /**
     * 子代理可以要求哪些模式。
     *
     * <p>读法：「当父级有效模式是 X 时，子代理能把模式设成这里的某一个」。
     * 每条规则都能从上面那条「只能收紧」的原则推出来：
     * <ul>
     *   <li>{@code plan} / {@code dontAsk} 本身就是最紧的两档，子代理只能维持原样。</li>
     *   <li>{@code bypassPermissions} 是最宽的一档 —— 用户已经把全部决定交出来了，
     *       子代理要求什么都不算放宽。</li>
     *   <li>{@code default} 下，子代理可以要求更紧的 {@code plan} 或 {@code dontAsk}
     *       （研究类子代理常这么做），但不能要求更宽的。</li>
     *   <li>{@code acceptEdits} / {@code auto} 之下，除 {@code bypassPermissions}
     *       之外的都算「不大于父级」，因为这两档本身已经允许改文件了。</li>
     * </ul>
     */
    private static List<String> requestableUnder(String parent) {
        switch (parent) {
            case PLAN:
            case DONT_ASK:
                return Arrays.asList(parent);
            case BYPASS:
                return KNOWN;
            case DEFAULT:
                return Arrays.asList(PLAN, DONT_ASK);
            case ACCEPT_EDITS:
            case AUTO:
                return Arrays.asList(DEFAULT, ACCEPT_EDITS, AUTO, PLAN, DONT_ASK);
            default:
                // normalize 已保证只会是上面六个值之一；这里只是让 switch 完整。
                return Arrays.asList(parent);
        }
    }

    private PermissionModePolicy() { }

    /**
     * 把任意输入归一到已知模式。
     *
     * <p>不认识的值一律归到 {@link #DEFAULT} 而不是报错：模式可能来自
     * 老版本的设置文件、或用户手写的配置，为它中断整个会话代价太大，
     * 而 {@code default} 是最保守的选择（该确认的都会确认）。
     */
    public static String normalize(String mode) {
        return KNOWN.contains(mode) ? mode : DEFAULT;
    }

    /**
     * 叠加计划流程之后的有效模式。
     *
     * <p>只在**计划中**与**待批准**两个阶段生效：一旦进入执行阶段，
     * 用户已经批准了计划，此时继续用 {@code plan} 会挡住执行本身。
     * 已取消的计划也不再叠加（用户明确终止了它）。
     */
    public static String effective(String baseMode, PlanWorkflowState workflowState) {
        if (workflowState != null && (workflowState.isPlanning() || workflowState.isAwaitingApproval())) {
            return PLAN;
        }
        return normalize(baseMode);
    }

    /**
     * 把子代理要求的模式夹到父级允许的范围内。
     *
     * @return 实际生效的模式。永远不宽于 {@code parentEffectiveMode}
     */
    public static String capSubagent(String parentEffectiveMode, String requestedMode) {
        String parent = normalize(parentEffectiveMode);
        String requested = normalize(requestedMode);
        if (parent.equals(requested)) return parent;
        return requestableUnder(parent).contains(requested) ? requested : parent;
    }
}
