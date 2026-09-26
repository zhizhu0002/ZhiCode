package com.termux.app.zhicode.api;

import com.termux.app.zhicode.model.SessionConfig;

/**
 * 按协议挑一个 Java 原生传输实现。
 *
 * <p>这里<b>不启动任何子进程</b>：三种协议都由本进程内的 Java 代码直接完成报文构造与
 * 流式解析。早期版本是靠外部 CLI 跑的，那会让传输受制于外部程序是否安装、
 * 也为注入提供了入口。
 *
 * <p>「协议名 → 实现」的对应关系写成一个 {@code switch} 表达式而不是一串
 * {@code equals} 判断：{@link ApiProtocol} 是枚举，枚举的 {@code switch} 要求覆盖
 * 每个取值，所以**将来加了协议却忘了在这里分派**会直接编译不过。
 * 用字符串判断时漏掉一个分支不会报错，只会表现为「报文发错了格式」。
 *
 * <p>每次调用都新建实例，**不做缓存**：取消请求靠的是 provider 实例上的
 * {@link HttpRequestTracker}，缓存实例会让子代理与主循环互相取消对方的在途请求。
 */
public final class ModelProviders {

    private static final String UNSUPPORTED = "这个协议还没有 Java 原生实现: ";

    private ModelProviders() {}

    /**
     * @throws IllegalArgumentException 协议名不在已知集合内。
     *         这里刻意抛错而不是回落到某个默认协议 —— 静默回落会把报文发成
     *         另一种格式，服务端返回的解析错误与真正的原因（协议名写错）对不上。
     */
    public static ModelProvider forConfig(SessionConfig config) {
        String wire = config == null ? null : config.protocol;
        // 只有「配置里根本没有协议这个字段」才回落到 Anthropic。
        // 空串不算「没有」：那是配置里写着一个空值，属于不认识的值，应当报错。
        if (wire == null) return new AnthropicMessagesProvider();
        ApiProtocol protocol = ApiProtocol.fromWire(wire);
        if (protocol == null) throw new IllegalArgumentException(UNSUPPORTED + wire);
        return switch (protocol) {
            case ANTHROPIC -> new AnthropicMessagesProvider();
            case OPENAI_CHAT -> new OpenAIChatCompletionsProvider();
            case OPENAI_RESPONSES -> new OpenAIResponsesProvider();
        };
    }
}
