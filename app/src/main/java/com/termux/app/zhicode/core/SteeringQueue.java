package com.termux.app.zhicode.core;

import org.json.JSONArray;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 会话进行中收到的「预输入」。
 *
 * <h3>为什么需要它</h3>
 * 一次请求可能要跑很久（几十个工具调用、几分钟）。用户在这期间打下的字不该被丢掉，
 * 但也不能立刻插进历史 —— 模型请求正在飞、或者某个工具正跑在中间，这时改历史会得到
 * 一份「工具调用与结果对不上」的上下文，提供方直接报错。
 *
 * <p>所以预输入先排队，等到<b>协议安全的边界</b>再一次性写进历史。边界有四处：
 * 发第一条模型请求之前、每次模型请求之前、模型回复完成之后、工具批次结束之后。
 * 每一处调用 {@link #drain()} 时都会把当时排队的全部取出。
 *
 * <h3>打断标记</h3>
 * 两个 {@code AtomicBoolean} 表示「这次预输入应该打断正在跑的东西」。它们与队列放在一起，
 * 而不是散在引擎里，是因为语义上属于同一件事：预输入的处理方式。
 *
 * <p>要留意的是：<b>目前没有任何地方把它们置为 true</b>。取消走的是
 * {@code ZhiCodeEngine.cancel()} 里的线程中断与 {@code provider.cancelRequest}，
 * 而预输入只排队、不打断。保留这两个标记是为了让「预输入可以打断」这条路径仍然有落点 ——
 * 真接上去的时候不需要重新设计接口。这一点写在这里，是因为读到 {@code consume*} 的
 * 调用点时会以为它们在起作用。
 */
final class SteeringQueue {

    /** 一条等待应用的用户输入。 */
    static final class Pending {
        final String prompt;
        final JSONArray extraContent;
        /** 用户界面上那条消息的 id，用于之后编辑历史时精确定位。 */
        final String messageId;
        final long queuedAt;

        Pending(String prompt, JSONArray extraContent, String messageId) {
            this.prompt = prompt == null ? "" : prompt;
            this.extraContent = extraContent == null ? new JSONArray() : copy(extraContent);
            this.messageId = messageId == null || messageId.trim().isEmpty()
                    ? UUID.randomUUID().toString()
                    : messageId;
            this.queuedAt = System.currentTimeMillis();
        }

        /** 深拷贝：调用方传进来的是「本次请求的快照」，之后还会继续改它。 */
        private static JSONArray copy(JSONArray source) {
            try {
                return new JSONArray(source.toString());
            } catch (Exception unreadable) {
                return new JSONArray();
            }
        }
    }

    private final ArrayDeque<Pending> queue = new ArrayDeque<>();
    private final AtomicBoolean interruptTools = new AtomicBoolean(false);
    private final AtomicBoolean interruptModel = new AtomicBoolean(false);

    void enqueue(Pending pending) {
        synchronized (queue) {
            queue.addLast(pending);
        }
    }

    int size() {
        synchronized (queue) {
            return queue.size();
        }
    }

    boolean isEmpty() {
        synchronized (queue) {
            return queue.isEmpty();
        }
    }

    /** 取出当前排队的全部输入，队列随之清空。 */
    List<Pending> drain() {
        List<Pending> drained = new ArrayList<>();
        synchronized (queue) {
            while (!queue.isEmpty()) drained.add(queue.removeFirst());
        }
        return drained;
    }

    /** 丢弃排队的输入与打断标记。用于取消、重置、恢复会话。 */
    void clear() {
        synchronized (queue) {
            queue.clear();
        }
        clearInterrupts();
    }

    void clearInterrupts() {
        interruptTools.set(false);
        interruptModel.set(false);
    }

    void requestToolInterrupt() {
        interruptTools.set(true);
    }

    void requestModelInterrupt() {
        interruptModel.set(true);
    }

    /** 取走「打断工具」标记。取走而不是只读，是因为调用方要顺手清掉线程的中断位。 */
    boolean takeToolInterrupt() {
        return interruptTools.getAndSet(false);
    }

    /** 同 {@link #takeToolInterrupt()}，用于模型请求。 */
    boolean takeModelInterrupt() {
        return interruptModel.getAndSet(false);
    }

    /** 只读地看「是否要求打断工具」，不消费标记。 */
    boolean toolInterruptRequested() {
        return interruptTools.get();
    }
}
