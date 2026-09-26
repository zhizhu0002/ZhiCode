package com.termux.app.zhicode.api;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;

/**
 * {@link RequestFailure} 的归类规则。
 *
 * <h3>为什么这几行值得单独测</h3>
 * 它决定上层「重试还是放弃」：报成 {@code request_timeout} 的消息会被重试，
 * 报成 {@code stream_read_error} 的说明回答已经被截断、不能简单重试。
 * 判断顺序写反（先看异常类型、后看有没有开始收响应）不会让编译失败，
 * 只会让「回答读了一半断掉」被当成「连接超时」，于是重试一次、
 * 用户看到重复的半截回答 —— 这类问题在真机上极难复现。
 *
 * <p>这些用例不需要网络：构造异常是纯内存操作，这正是把它从
 * {@code HttpRequestTracker.Scope} 里提出来换到的好处。
 */
public class RequestFailureTest {

    // ============================================================ 顺序是契约

    @Test
    public void startedResponseBeatsExceptionType() {
        // 已经开始收响应之后，无论异常是什么，语义都是「回答被截断」。
        // 哪怕异常类型恰好是超时 —— 那时对用户有用的是「截断了」，不是「超时」。
        assertEquals(RequestFailure.STREAM, RequestFailure.code(true, new SocketTimeoutException("Read timed out")));
        assertEquals(RequestFailure.STREAM, RequestFailure.code(true, new EOFException("eof")));
        assertEquals(RequestFailure.STREAM, RequestFailure.code(true, new SocketException("Connection reset")));
        assertEquals(RequestFailure.STREAM, RequestFailure.code(true, new IOException("anything")));
        assertEquals(RequestFailure.STREAM, RequestFailure.code(true, null));
    }

    // ============================================================ 按异常类型

    @Test
    public void classifiesTimeoutAndPrematureEof() {
        assertEquals(RequestFailure.TIMEOUT, RequestFailure.code(false, new SocketTimeoutException("Read timed out")));
        assertEquals(RequestFailure.EOF, RequestFailure.code(false, new EOFException("Premature EOF")));
    }

    @Test
    public void recognisesResetByMessageBecauseJvmsDisagreeOnTheType() {
        // 各家实现给出的类型与文案都不一样，只能按文案认。
        for (String message : new String[]{
                "Connection reset", "Connection Reset By Peer", "socket closed", "Broken pipe"}) {
            assertEquals("文案: " + message,
                    RequestFailure.RESET, RequestFailure.code(false, new SocketException(message)));
        }
    }

    @Test
    public void leavesUnrecognisedFailuresUnclassified() {
        // 返回空串是「没能归类」，交给调用方决定怎么呈现；
        // 这里刻意不兜底成 network_error —— 那是给用户看的文案层的兜底，
        // 混进码里会让上层以为「确实判断出是网络错误」。
        assertEquals("", RequestFailure.code(false, new IOException("Software caused connection abort")));
        assertEquals("", RequestFailure.code(false, new SocketException((String) null)));
        assertEquals("", RequestFailure.code(false, null));
    }

    // ============================================================ 给人看的文案

    @Test
    public void describePrefixesTheCodeAndFallsBackForMissingDetail() {
        assertEquals("request_timeout: Read timed out",
                RequestFailure.describe(false, new SocketTimeoutException("Read timed out")));
        // 未归类 → 文案层的兜底码 network_error。
        assertEquals("network_error: boom", RequestFailure.describe(false, new IOException("boom")));
        // 异常自己没有文案时给一句人类能读懂的，而不是 "network_error: null"。
        assertEquals("network_error: network I/O failed", RequestFailure.describe(false, new IOException()));
        assertEquals("network_error: network I/O failed", RequestFailure.describe(false, new IOException("")));
    }
}
