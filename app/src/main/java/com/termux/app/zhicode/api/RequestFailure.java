package com.termux.app.zhicode.api;

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Locale;

/**
 * 把一次网络 IO 失败翻译成「机器可读的码 + 给人看的一句话」。
 *
 * <h3>为什么单独拿出来</h3>
 * 这段判断是**纯函数**：只看两个输入 —— 有没有开始收响应、异常本身。
 * 但它是整条传输链路里唯一需要「按顺序判断」的地方，留在
 * {@code HttpRequestTracker.Scope} 里就得先造一个 HTTP 连接才能测到，
 * 于是实际上没人测。提出来之后可以用几行构造出来的异常把每种归类钉住。
 *
 * <h3>顺序是契约</h3>
 * <ol>
 *   <li><b>已开始收响应 → 一律 {@code stream_read_error}。</b>
 *       哪怕异常类型恰好是超时：此时对用户有用的信息是「回答被截断了」，
 *       而不是「请求超时」—— 两者的处理方式不同（前者已收到部分内容，
 *       不能简单重试；后者应当重试）。</li>
 *   <li>否则才看异常类型：超时 → {@code request_timeout}，
 *       流被提前结束 → {@code unexpected_eof}。</li>
 *   <li>再看 socket 的文案（各家 JVM 与底层实现用词不一，只能按文案认）。</li>
 *   <li>都不匹配返回空串，表示「这是一次未分类的失败」，由调用方决定怎么呈现。</li>
 * </ol>
 */
final class RequestFailure {

    /** 连接建立阶段超时。用户该重试。 */
    static final String TIMEOUT = "request_timeout";
    /** 响应在正常结束前断了，且不是 socket 层面的重置。 */
    static final String EOF = "unexpected_eof";
    /** socket 被对端或本机重置/关闭。 */
    static final String RESET = "connection_reset";
    /** 已经开始收响应之后读失败：回答被截断。 */
    static final String STREAM = "stream_read_error";
    /** 未分类失败的兜底码，只用于给用户看的文案里。 */
    static final String UNKNOWN = "network_error";

    /**
     * 判定 {@code connection_reset} 时要在异常文案里找的词。
     *
     * <p>不区分大小写地比较，且用 {@link Locale#US}：某些语言环境下
     * {@code "I".toLowerCase()} 不是 {@code "i"}，会让一条正常的失败漏判。
     */
    private static final String[] RESET_MARKERS = {"reset", "closed", "broken pipe"};

    /** 异常自己没有文案时给用户看的兜底说明。 */
    private static final String NO_DETAIL = "network I/O failed";

    private RequestFailure() {}

    /**
     * 归类一次 IO 失败。
     *
     * @param responseStarted 是否已经开始读响应体
     * @return 失败码；空串表示未能归类
     */
    static String code(boolean responseStarted, IOException error) {
        if (responseStarted) return STREAM;
        if (error == null) return "";
        if (error instanceof SocketTimeoutException) return TIMEOUT;
        if (error instanceof EOFException) return EOF;
        if (error instanceof SocketException && looksLikeReset(error.getMessage())) return RESET;
        return "";
    }

    /** 给人看的失败说明，形如 {@code "request_timeout: Read timed out"}。 */
    static String describe(boolean responseStarted, IOException error) {
        String classified = code(responseStarted, error);
        String detail = error == null ? null : error.getMessage();
        String prefix = classified.isEmpty() ? UNKNOWN : classified;
        return prefix + ": " + (detail == null || detail.isEmpty() ? NO_DETAIL : detail);
    }

    /** 异常文案里是否出现了「连接被重置/关闭」的迹象。 */
    private static boolean looksLikeReset(String message) {
        if (message == null) return false;
        String lower = message.toLowerCase(Locale.US);
        for (String marker : RESET_MARKERS) {
            if (lower.contains(marker)) return true;
        }
        return false;
    }
}
