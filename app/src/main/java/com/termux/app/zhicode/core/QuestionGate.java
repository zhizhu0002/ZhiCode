package com.termux.app.zhicode.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 向用户提问并等回答的中转站。
 *
 * <h3>为什么 agent 工作线程要阻塞在这里</h3>
 * 「向用户确认」在 agent 循环里是同步语义：模型要拿到答案才能决定下一步。
 * 界面的回答来自另一个线程（用户点按钮），所以这里用
 * {@link CountDownLatch} 做一次跨线程的交接：工作线程在 {@code await} 上等，
 * 界面线程 countDown 后它才继续。
 *
 * <h3>为什么有超时，而 PlanApprovalGate 没有</h3>
 * 两者的默认行为不同，这是刻意的：
 * 这里的超时结果是**空答案**（模型据此按「用户没回答」继续，可以再问）；
 * 批准计划的超时结果是**不批准**（不能因为没人应答就把执行放出去）。
 * 所以批准那条路径干脆不设超时 —— 无人值守的请求永远无法批准执行。
 *
 * <h3>并发</h3>
 * 用 {@link ConcurrentHashMap} 而不是同步整块：可能有多轮提问同时在等
 * （主循环一个 + 每个子代理一个），整块同步会让一个慢问题拖住所有问题。
 */
public final class QuestionGate {

    /** 提问后等待的最长时间。超时按「用户没回答」处理。 */
    private static final long TIMEOUT_MINUTES = 30;

    /**
     * 一次提问。
     *
     * <p>字段对界面可见（它要拿 {@link #questions} 去渲染），
     * 所以是 public 的 final 字段而不是 getter。
     */
    public static final class QuestionRequest {
        public final String requestId = UUID.randomUUID().toString();
        /** 问题列表。构造时归一化成非 null 的空数组，界面不必判空。 */
        public final JSONArray questions;

        private final CountDownLatch answered = new CountDownLatch(1);
        /** 用户给的答案；为 null 表示「还没答」或「超时/被取消」。 */
        private volatile JSONObject answers;

        QuestionRequest(JSONArray questions) {
            this.questions = questions == null ? new JSONArray() : questions;
        }
    }

    private final Map<String, QuestionRequest> pending = new ConcurrentHashMap<>();

    /**
     * 提问并等待回答。
     *
     * @param notify 在**阻塞之前**被同步调用，用来把问题交给界面。
     *               先通知再等待的顺序不能反 —— 反过来就变成先等后通知，永远是死等。
     * @return 用户的答案；超时、被取消、或用户给了空答案时返回空对象
     */
    public JSONObject ask(JSONArray questions, Consumer<QuestionRequest> notify)
            throws InterruptedException {
        QuestionRequest request = new QuestionRequest(questions);
        pending.put(request.requestId, request);
        try {
            if (notify != null) notify.accept(request);
            boolean answered = request.answered.await(TIMEOUT_MINUTES, TimeUnit.MINUTES);
            JSONObject answers = request.answers;
            // answered 与 answers 都要看：await 返回 true 但答案仍为 null 是可能的
            // （被 cancelAll 唤醒），那时要给出「没有答案」而不是 null。
            return answered && answers != null ? answers : new JSONObject();
        } finally {
            // 无论怎么退出都要摘掉自己，否则待答表会一直涨。
            pending.remove(request.requestId);
        }
    }

    /**
     * 界面提交答案。
     *
     * @return 是否有这样一个待答的问题。同一个问题答两次时第二次返回 false ——
     *         避免界面重复点击时把答案覆盖掉（用户第二次点的可能是误触）。
     */
    public boolean respond(String requestId, JSONObject answers) {
        QuestionRequest request = pending.get(requestId);
        if (request == null) return false;
        request.answers = answers == null ? new JSONObject() : answers;
        request.answered.countDown();
        return true;
    }

    /** 唤醒所有等待者（会话中断、界面销毁时调用）。它们会拿到空答案。 */
    public void cancelAll() {
        for (QuestionRequest request : pending.values()) request.answered.countDown();
        pending.clear();
    }
}
