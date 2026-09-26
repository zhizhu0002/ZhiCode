package com.termux.app.zhicode.api;

import java.io.IOException;

/**
 * 一次流式请求的失败，带一个机器可读的失败码。
 *
 * <p>继承 {@link IOException} 而不是自定义基类：它总是在 IO 过程中抛出，
 * 上层「读流失败」的 {@code catch} 分支本来就该把它一起接住，
 * 换基类会让那些分支被动地漏掉它。
 *
 * <p>提成顶层类型的原因：它是**调用方要捕获的**东西（{@code ZhiCodeEngine}
 * 按 {@link #code} 决定能不能重试），不是 {@code ModelProvider} 自己的声明。
 *
 * <h3>{@link #code} 的取值</h3>
 * {@code request_timeout} / {@code unexpected_eof} / {@code connection_reset} /
 * {@code stream_read_error} / {@code network_error}，以及各协议自己发现的错误
 * （例如 Responses 把服务端 {@code error} 事件里的 {@code type} 原样带上来）。
 * 上层按前缀比较，所以这些字符串是契约。
 */
public class StreamFailure extends IOException {

    private static final long serialVersionUID = 1L;

    /** 机器可读的失败码；永不为 {@code null}（缺失时为 {@code ""}）。 */
    public final String code;

    public StreamFailure(String code, String message) {
        super(message);
        this.code = normalize(code);
    }

    public StreamFailure(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = normalize(code);
    }

    /** 缺失的码统一成空串，免得每个调用点都要判一次 null。 */
    private static String normalize(String code) {
        return code == null ? "" : code;
    }
}
