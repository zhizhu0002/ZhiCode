package com.termux.app.zhicode.model;

import java.util.UUID;

/**
 * 计划流程的一个不可变快照：现在处于哪个阶段、计划正文是什么、还能不能改。
 *
 * <h3>为什么整份替换而不是逐字段改</h3>
 * 计划会被引擎（推进流程）与界面（显示当前计划、提交反馈）同时读写。
 * 做成可变对象就要为「读到一半被别人改了」这类问题引入锁与约定的可见性；
 * 不可变快照让交换变成一个引用的赋值，界面看到的一定是某个完整版本。
 * 每次推进都产生**新的快照且 revision + 1**，界面据此判断自己画的是不是旧版本。
 *
 * <h3>阶段与允许的操作</h3>
 * <pre>
 *   IDLE ──(开始计划)──▶ PLANNING ──(提交)──▶ AWAITING_APPROVAL ──(批准)──▶ EXECUTING ──(结束)──▶ IDLE
 *                          ▲                        │
 *                          └────(继续讨论/反馈)───────┘
 *
 *   任何非 IDLE 阶段都可以 ──(取消)──▶ CANCELLED（已取消则幂等）
 * </pre>
 * 不允许的转换一律抛 {@link IllegalStateException}，而不是「尽力而为」地接受：
 * 一个在 {@code IDLE} 状态被写进去的计划正文，界面上显示不出来、
 * 用户也不知道自己的输入去哪了。
 *
 * <h3>时间戳是单调的</h3>
 * 新快照的 {@link #updatedAt} 取「当前时间」与「上一版 + 1」的较大者。
 * 系统时钟可能被回拨（对时、时区/夏令时），而界面是按时间戳判断先后顺序的，
 * 回拨会让新版本看起来比旧版本还旧。
 */
public final class PlanWorkflowState {

    /** 计划流程的阶段。 */
    public enum Status {
        IDLE,
        PLANNING,
        AWAITING_APPROVAL,
        EXECUTING,
        CANCELLED
    }

    /** 给不 import {@link Status} 的调用方准备的别名。 */
    public static final Status IDLE = Status.IDLE;
    public static final Status PLANNING = Status.PLANNING;
    public static final Status AWAITING_APPROVAL = Status.AWAITING_APPROVAL;
    public static final Status EXECUTING = Status.EXECUTING;
    public static final Status CANCELLED = Status.CANCELLED;

    /** 权限模式的初值。空值一律归到它，免得出现「空字符串权限模式」这种第三态。 */
    private static final String DEFAULT_PERMISSION_MODE = "default";

    private static final String ERROR_NOT_EDITABLE = "这个状态下不能编辑计划: ";
    private static final String ERROR_NO_WORKFLOW = "当前没有计划流程，无法执行这个操作";
    private static final String ERROR_WRONG_STATE = "计划状态不对，期望 ";
    private static final String ERROR_ALREADY_IDLE = "没有进行中的计划流程可供取消";

    public final Status status;
    /** 流程标识。空串表示「还没有流程」，此时所有推进操作都应当被拒绝。 */
    public final String workflowId;
    /** 版本号，每次推进 +1。界面用它判断自己手上的快照是不是旧的。 */
    public final long revision;
    /** 进入计划模式**之前**用户自己的权限模式，结束时还原用。 */
    public final String previousPermissionMode;
    /** 批准时生效的权限模式；未批准前为空串。 */
    public final String approvedPermissionMode;
    public final String planFile;
    public final String planText;
    /** 用户「继续讨论」时给出的反馈，用于下一轮计划。 */
    public final String feedback;
    public final long updatedAt;

    private PlanWorkflowState(Status status, String workflowId, long revision,
                              String previousPermissionMode, String approvedPermissionMode,
                              String planFile, String planText, String feedback, long updatedAt) {
        this.status = status == null ? Status.IDLE : status;
        this.workflowId = text(workflowId);
        this.revision = Math.max(0L, revision);
        this.previousPermissionMode = permissionMode(previousPermissionMode);
        this.approvedPermissionMode = text(approvedPermissionMode);
        this.planFile = text(planFile);
        this.planText = text(planText);
        this.feedback = text(feedback);
        this.updatedAt = Math.max(0L, updatedAt);
    }

    /** 没有流程时的空快照。 */
    public static PlanWorkflowState idle() {
        return new PlanWorkflowState(Status.IDLE, "", 0L, DEFAULT_PERMISSION_MODE, "", "", "", "",
            System.currentTimeMillis());
    }

    /** 开始一个新流程，流程标识自动生成。 */
    public static PlanWorkflowState planning(String previousPermissionMode) {
        return planning(UUID.randomUUID().toString(), previousPermissionMode, "");
    }

    /**
     * 开始一个新流程，流程标识由调用方指定。
     *
     * <p>标识为空时退回自动生成而不是拒绝：调用方常常是从一个「可能已经有 id」
     * 的位置传进来的，拒绝会让它必须在两处判空。
     */
    public static PlanWorkflowState planning(String workflowId, String previousPermissionMode,
                                             String planFile) {
        String id = text(workflowId);
        if (id.isEmpty()) id = UUID.randomUUID().toString();
        return new PlanWorkflowState(Status.PLANNING, id, 1L, previousPermissionMode, "",
            planFile, "", "", System.currentTimeMillis());
    }

    /** 从持久化数据还原一个快照，**不改动** revision 与时间戳。 */
    public static PlanWorkflowState restore(Status status, String workflowId, long revision,
                                            String previousPermissionMode,
                                            String approvedPermissionMode, String planFile,
                                            String planText, String feedback, long updatedAt) {
        return new PlanWorkflowState(status, workflowId, revision, previousPermissionMode,
            approvedPermissionMode, planFile, planText, feedback, updatedAt);
    }

    public PlanWorkflowState copy() {
        return new PlanWorkflowState(status, workflowId, revision, previousPermissionMode,
            approvedPermissionMode, planFile, planText, feedback, updatedAt);
    }

    /**
     * 发布一份新的计划正文，并清掉落在前一版上的反馈。
     *
     * <p>计划阶段与待批准阶段都可以改：用户在「待批准」界面里直接编辑计划是常见操作，
     * 要求他先退回计划阶段等于把一次编辑变成两次点击。
     */
    public PlanWorkflowState withPlan(String planFile, String planText) {
        requireWorkflow();
        if (status != Status.PLANNING && status != Status.AWAITING_APPROVAL) {
            throw new IllegalStateException(ERROR_NOT_EDITABLE + status);
        }
        return advance(Status.PLANNING, "", planFile, planText, "");
    }

    public PlanWorkflowState withPlanText(String planText) {
        return withPlan(planFile, planText);
    }

    public PlanWorkflowState withPlanFile(String planFile) {
        return withPlan(planFile, planText);
    }

    /** 提交计划，等用户批准。 */
    public PlanWorkflowState awaitingApproval() {
        requireStatus(Status.PLANNING);
        return advance(Status.AWAITING_APPROVAL, "", planFile, planText, feedback);
    }

    /** 用户批准：进入执行，**不改动**用户自己的权限基线。 */
    public PlanWorkflowState executing() {
        requireStatus(Status.AWAITING_APPROVAL);
        return advance(Status.EXECUTING, "", planFile, planText, "");
    }

    /** 用户选择「继续讨论」：带着反馈退回计划阶段。 */
    public PlanWorkflowState keepPlanning(String feedback) {
        requireStatus(Status.AWAITING_APPROVAL);
        return advance(Status.PLANNING, "", planFile, planText, feedback);
    }

    /** 执行结束，回到空闲，并把权限还原成批准时定的那一档。 */
    public PlanWorkflowState finished() {
        requireStatus(Status.EXECUTING);
        return advance(Status.IDLE, approvedPermissionMode, planFile, planText, "");
    }

    /** 取消流程。已经取消时返回自身（幂等），空快照则拒绝。 */
    public PlanWorkflowState cancelled(String feedback) {
        requireWorkflow();
        if (status == Status.CANCELLED) return this;
        if (status == Status.IDLE) throw new IllegalStateException(ERROR_ALREADY_IDLE);
        return advance(Status.CANCELLED, "", planFile, planText, feedback);
    }

    public boolean isIdle() { return status == Status.IDLE; }
    public boolean isPlanning() { return status == Status.PLANNING; }
    public boolean isAwaitingApproval() { return status == Status.AWAITING_APPROVAL; }
    public boolean isExecuting() { return status == Status.EXECUTING; }
    public boolean isCancelled() { return status == Status.CANCELLED; }

    /** 是否有流程正在进行（三种中间态之一）。 */
    public boolean isActive() {
        return status == Status.PLANNING || status == Status.AWAITING_APPROVAL
            || status == Status.EXECUTING;
    }

    /**
     * 产出版本号 +1 的新快照。
     *
     * <p>时间戳必须严格递增：界面按它排序，系统时钟回拨时若直接取当前时间，
     * 新快照看起来会比它替换掉的那一版更旧。
     */
    private PlanWorkflowState advance(Status nextStatus, String approvedMode, String nextPlanFile,
                                      String nextPlanText, String nextFeedback) {
        long now = Math.max(System.currentTimeMillis(), updatedAt + 1L);
        return new PlanWorkflowState(nextStatus, workflowId, revision + 1L,
            previousPermissionMode, approvedMode, nextPlanFile, nextPlanText, nextFeedback, now);
    }

    private void requireWorkflow() {
        if (workflowId.isEmpty()) throw new IllegalStateException(ERROR_NO_WORKFLOW);
    }

    private void requireStatus(Status expected) {
        requireWorkflow();
        if (status != expected) {
            throw new IllegalStateException(ERROR_WRONG_STATE + expected + "，实际是 " + status);
        }
    }

    /** 空权限模式归到默认档，避免出现「空串权限模式」这种没人处理过的第三态。 */
    private static String permissionMode(String mode) {
        String normalized = text(mode);
        return normalized.isEmpty() ? DEFAULT_PERMISSION_MODE : normalized;
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
