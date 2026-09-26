package com.termux.app.zhicode.api;

import com.termux.app.zhicode.model.SessionConfig;

/**
 * 按协议挑一个 Java 原生传输实现。
 *
 * <p>这里<b>不启动任何子进程</b>：三种协议都由本进程内的 Java 代码直接完成报文构造与
 * 流式解析。早期版本是靠外部 CLI 跑的，那会让传输受制于外部程序是否安装、
 * 也为注入提供了入口。
 *
 * <p>协议名是持久化配置的一部分（存在用户设置里），所以这些字面量不能随意改；
 * 见 {@code ApiEndpointResolver} 里同一组常量。
 */
public final class ModelProviders {

    private ModelProviders() {}

    /**
     * @throws IllegalArgumentException 协议名不在已知集合内。
     *         这里刻意抛错而不是回落到某个默认协议 —— 静默回落会把报文发成
     *         另一种格式，服务端返回的解析错误与真正的原因（协议名写错）对不上。
     */
    public static ModelProvider forConfig(SessionConfig config) {
        String protocol = config == null || config.protocol == null
                ? ApiEndpointResolver.PROTOCOL_ANTHROPIC
                : config.protocol;
        // codex-responses 与 openai-responses 是同一套报文格式的两个入口名：
        // 后者是标准 OpenAI 路径，前者是同类服务的另一种叫法。
        if (ApiEndpointResolver.PROTOCOL_OPENAI_RESPONSES.equals(protocol) || "codex-responses".equals(protocol)) {
            return new OpenAIResponsesProvider();
        }
        // openai-compatible 面向自建/第三方网关，报文与 openai-chat 一致。
        if ("openai-compatible".equals(protocol) || ApiEndpointResolver.PROTOCOL_OPENAI_CHAT.equals(protocol)) {
            return new OpenAIChatCompletionsProvider();
        }
        if (ApiEndpointResolver.PROTOCOL_ANTHROPIC.equals(protocol)) {
            return new AnthropicMessagesProvider();
        }
        throw new IllegalArgumentException("这个协议还没有 Java 原生实现: " + protocol);
    }
}
