package com.termux.app.zhicode.core;

import com.termux.app.zhicode.model.PlanWorkflowState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

/**
 * 计划批准的中转站：工作线程交出一份计划，等用户点头。
 *
 * <h3>没有超时，这是刻意的</h3>
 * {@link QuestionGate} 超时后给空答案（模型可以继续），但这个门超时后
 * **必须**是不批准：批准意味着「接下来按这份计划动手改文件」，
 * 而「没人应答」绝不等于「同意」。所以这里等多久都等，
 * 唯一的出路是用户明确选择，或者会话被取消。
 *
 * <h3>只批准一次</h3>
 * {@link ApprovalRequest#complete} 用「已经完成」来判断重复提交：
 * 界面与通知栏都可能触发同一个请求，而重复批准一次改动会让引擎
 * 对同一份计划执行两遍。
 *
 * <h3>{@link #pendingRequests()} 为什么存在</h3>
 * Android 会销毁并重建 Activity（旋转屏幕、分屏）。重建后界面手里没有
 * 之前那个请求对象了，得能按 id 重新拿到它们 —— 否则用户会看到一个
 * 「等一个不知道是什么的批准」的界面。
 */
public final class PlanApprovalGate {

    /** 用户的选择。{@link #isApproved()} 只有 {@link #APPROVE} 是 true。 */
    public enum Decision {
        APPROVE(true),
        KEEP_PLANNING(false),
        CANCEL(false);

        private final boolean approved;

        Decision(boolean approved) { this.approved = approved; }

        public boolean isApproved() { return approved; }
    }

    /** 一次回答。{@link #feedback} 是「继续讨论」时用户写的意见。 */
    public static final class ApprovalResponse {
        public final Decision decision;
        public final String feedback;

        ApprovalResponse(Decision decision, String feedback) {
            // 决策缺失按 CANCEL 处理：见类注释「没人应答绝不等于同意」。
            this.decision = decision == null ? Decision.CANCEL : decision;
            this.feedback = feedback == null ? "" : feedback;
        }

        public boolean isApproved() { return decision.isApproved(); }
    }

    /**
     * 一次待批准的请求。
     *
     * <p>{@link #plan} 是**快照**（构造时已 copy）：用户看到的必须是
     * 「提交时的那一版计划」，而不是引擎随后又改过的版本。
     */
    public static final class ApprovalRequest {
        public final String requestId;
        public final PlanWorkflowState plan;

        private final CountDownLatch answered = new CountDownLatch(1);
        private ApprovalResponse response;

        ApprovalRequest(PlanWorkflowState plan) {
            requestId = UUID.randomUUID().toString();
            this.plan = plan == null ? PlanWorkflowState.idle() : plan.copy();
        }

        /** 当前回答；尚未回答时为 {@code null}。合成方法，读的是可能正被写的字段。 */
        public synchronized ApprovalResponse getResponse() {
            return response;
        }

        /**
         * 落定这次请求。
         *
         * @return 是否由**本次调用**落定。已经落定过则返回 false，
         *         调用方据此知道「这是我第一次给出决定，还是重复提交」。
         */
        private synchronized boolean complete(ApprovalResponse result) {
            if (response != null) return false;
            response = result == null ? cancelled() : result;
            answered.countDown();
            return true;
        }
    }

    private final Map<String, ApprovalRequest> pending = new ConcurrentHashMap<>();

    /**
     * 登记一个批准请求并阻塞，直到 {@link #respond} 或 {@link #cancelAll} 让它落定。
     *
     * <p>中断与运行时异常都要先把请求落定成 CANCEL 再往外抛：不这样做的话
     * 界面会一直显示一个永远不会被回答的批准卡片。
     */
    public ApprovalResponse request(PlanWorkflowState plan, Consumer<ApprovalRequest> notify)
            throws InterruptedException {
        ApprovalRequest request = new ApprovalRequest(plan);
        pending.put(request.requestId, request);
        try {
            // 先通知再等待：反过来就永远是死等（见 QuestionGate 里的同一处理）。
            if (notify != null) notify.accept(request);
            request.answered.await();
            ApprovalResponse response = request.getResponse();
            return response == null ? cancelled() : response;
        } catch (InterruptedException interrupted) {
            request.complete(cancelled());
            throw interrupted;
        } catch (RuntimeException failed) {
            request.complete(cancelled());
            throw failed;
        } finally {
            pending.remove(request.requestId, request);
        }
    }

    /** 与 {@link #request} 同一个门，只是名字更贴合调用方的读法。 */
    public ApprovalResponse awaitApproval(PlanWorkflowState plan, Consumer<ApprovalRequest> notify)
            throws InterruptedException {
        return request(plan, notify);
    }

    /** 用户什么都没写地回答（比如点「批准」）。 */
    public boolean respond(String requestId, Decision decision) {
        return respond(requestId, decision, "");
    }

    /**
     * 用户给出回答。
     *
     * @return 是否成功地**首次**落定了这个请求
     */
    public boolean respond(String requestId, Decision decision, String feedback) {
        if (requestId == null || decision == null) return false;
        ApprovalRequest request = pending.get(requestId);
        if (request == null || !request.complete(new ApprovalResponse(decision, feedback))) return false;
        pending.remove(requestId, request);
        return true;
    }

    /**
     * 让所有等待中的请求落定为「取消」。
     *
     * <p>遍历中同时移除：ConcurrentHashMap 的弱一致性迭代允许这么做，
     * 而遍历完再清空会让「遍历期间新加入的请求」被无声丢掉。
     */
    public void cancelAll() {
        for (ApprovalRequest request : pending.values()) {
            request.complete(cancelled());
            pending.remove(request.requestId, request);
        }
    }

    /** 未落定请求的快照，供 Activity 重建后恢复界面。 */
    public List<ApprovalRequest> pendingRequests() {
        return new ArrayList<>(pending.values());
    }

    /** 「不批准」的唯一构造处，免得几处各写一遍 Decision.CANCEL。 */
    private static ApprovalResponse cancelled() {
        return new ApprovalResponse(Decision.CANCEL, "");
    }
}
