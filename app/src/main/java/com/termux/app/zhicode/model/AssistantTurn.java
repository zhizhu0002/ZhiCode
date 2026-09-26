package com.termux.app.zhicode.model;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次模型请求的最终结果：正文块、工具调用、停止原因、用量。
 *
 * <p>字段故意是公开可变的基本容器：它在本进程内从解码器交给引擎，不跨越进程边界，
 * 也不被持久化（持久化的是引擎随后拼出来的会话消息），
 * 所以不需要 getter/setter 那层壳。
 *
 * <p>两个容器是 {@code final} 的**可变**容器 —— 解码器流式填充它们，
 * 再到最后才被读。填充期只发生在一个线程里，填充完成后才交给别人读；
 * 不要在回调过程中把 {@link #toolCalls} 交出去边改边读。
 */
public final class AssistantTurn {

    /** 原样的协议内容块（Anthropic 的 text/thinking/tool_use 等）。直接进会话历史。 */
    public final JSONArray content = new JSONArray();

    /** 从内容块里抽出来的工具调用，按出现顺序。 */
    public final List<ToolCall> toolCalls = new ArrayList<>();

    /** 协议给的停止原因（{@code end_turn} / {@code tool_use} / {@code max_tokens}），可能未设置。 */
    public String stopReason;

    /** 输入 token（Anthropic 口径已含缓存命中与写入，见各 provider）。 */
    public long inputTokens;

    public long outputTokens;
}
