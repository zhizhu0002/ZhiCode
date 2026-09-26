package com.termux.app.zhicode.api;

/**
 * 流式回复的增量回调。
 *
 * <p>实现方在界面/引擎侧，调用方是各协议的解码器。三个「增量」回调都必须能在
 * 一次请求里被调用任意多次、且可以交错 —— 服务端把一条回答拆成多少片、
 * 正文/思考/工具入参怎么穿插，都是协议自己的事，解码器只负责转发。
 *
 * <p>提成顶层类型的原因：它是**调用方要实现的**接口，与
 * {@link ModelProvider} 那条「发一次请求」的方法不是一回事。
 *
 * <p>回调在请求线程上同步执行，实现里不要做重活：解码器正停在这一行等它返回。
 */
public interface StreamListener {

    /** 正文增量。拼起来才是完整回答。 */
    void onTextDelta(String text);

    /** 思考过程增量（思维链，有些协议叫 reasoning）。界面通常折叠显示或直接丢弃。 */
    void onThinkingDelta(String thinking);

    /**
     * 工具入参增量。
     *
     * <p>{@code partialJson} 是**不完整的** JSON 片段：拼完之前不能解析。
     * 同一次工具调用会带着同一个 {@code toolUseId} 反复进来。
     *
     * <p>{@code toolUseId} 与 {@code toolName} **可能是 null**：有些协议只在
     * 「这次工具调用开始」的那个事件里给 id/name，后面的增量事件里没有它们，
     * 而解码器必须把增量原样转发（它自己并不知道调用方是否需要这两个字段）。
     * 需要它们的话请一起缓存「开始」事件里的值，不要假设每次都不为 null。
     */
    void onToolInputDelta(String toolUseId, String toolName, String partialJson);

    /**
     * 用量更新。
     *
     * <p>报的是这次请求**到目前为止的累计值**，不是本次增量：同一个字段会被更新后
     * 重发，所以调用方应当按「覆盖」处理，累加会算成好几倍。
     *
     * <p>可能是 0：非流式响应里缺字段时就是 0，而不是「没报过」。
     */
    void onUsage(long inputTokens, long outputTokens);
}
