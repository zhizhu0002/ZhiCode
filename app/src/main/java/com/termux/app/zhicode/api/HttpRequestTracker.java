package com.termux.app.zhicode.api;

import java.io.EOFException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 跟踪「每个提供方工作线程上正在进行的那个 HTTP 请求」，并给静默等待设上限。
 *
 * <h3>为什么需要它</h3>
 * 这套传输是阻塞式 {@link HttpURLConnection}，一次流式对话可能持续几分钟。
 * 有两个必须能处理的情况：
 * <ol>
 *   <li><b>用户中途取消。</b>线程被 interrupt 只是设了个标志；阻塞在读 socket 上的线程
 *       不会因此醒来。必须主动 {@code disconnect()}。</li>
 *   <li><b>服务端不响应也不关闭连接。</b>没有超时的话线程会永久挂住，
 *       表现为「应用卡住、没有任何报错」。</li>
 * </ol>
 *
 * <h3>为什么按线程索引</h3>
 * 每个工作线程同一时刻只应有一个在途请求。用线程作键就能实现
 * 「取消我自己的那个请求」，而不需要一个全局的请求注册表 ——
 * 后者在并发会话下容易误取消别人的请求。
 *
 * <p>键是 {@link Thread} 本身，它在 {@code ConcurrentHashMap} 里按身份比较；
 * 线程结束后条目仍会留在表里，因此每次 {@link Scope#close()} 都必须移除自己那一条。
 */
final class HttpRequestTracker {

    /** 建连超时。连不上应当在 30 秒内失败，而不是拖到用户以为卡死。 */
    static final int CONNECT_TIMEOUT_MS = 30_000;

    /**
     * 读空闲超时。
     *
     * <p>这是「两次收到数据之间」的上限，不是整个响应的上限 ——
     * 长流式回答里每个 token 都会刷新它，所以设成 5 分钟不会误杀正常的长回答，
     * 但能兜住「连接还在、服务端不再说话」的情况。
     */
    static final int READ_IDLE_TIMEOUT_MS = 300_000;

    /** 已回服务端得到的失败码之一。 */
    private static final String FAILURE_TIMEOUT = "request_timeout";
    private static final String FAILURE_EOF = "unexpected_eof";
    private static final String FAILURE_RESET = "connection_reset";
    private static final String FAILURE_STREAM = "stream_read_error";
    private static final String FAILURE_UNKNOWN = "network_error";

    private final ConcurrentHashMap<Thread, Scope> active = new ConcurrentHashMap<>();

    /**
     * 为当前线程登记一次请求。
     *
     * <p>同一线程上若已有在途请求，先取消旧的：说明上一次没走完，
     * 而两个请求共用一个连接状态只会互相干扰。
     *
     * @throws InterruptedException 线程已经被 interrupt，或登记后立刻被取消 ——
     *         此时不返回一个「可用」的 Scope，让调用方尽早退出，
     *         而不是先发一次注定要被丢弃的请求
     */
    Scope begin(HttpURLConnection connection) throws InterruptedException {
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_IDLE_TIMEOUT_MS);

        Thread worker = Thread.currentThread();
        Scope scope = new Scope(worker, connection);
        Scope previous = active.put(worker, scope);
        if (previous != null) previous.cancel();

        // 登记与被取消之间存在竞态：cancel(worker) 可能在 put 之后、这里检查之前跑完。
        // 所以取消后要再确认一次，两种情况都按「本次请求作废」处理。
        if (worker.isInterrupted() || scope.isClosed()) {
            active.remove(worker, scope);
            scope.cancel();
            throw new InterruptedException("ZhiCode request interrupted");
        }
        return scope;
    }

    /** 取消指定线程上的在途请求。{@code worker} 为 null 时什么也不做。 */
    void cancel(Thread worker) {
        if (worker == null) return;
        Scope scope = active.remove(worker);
        if (scope != null) scope.cancel();
    }

    /** 当前在途请求数，供诊断使用。 */
    int activeCount() {
        return active.size();
    }

    /**
     * 一次请求的生命周期句柄。
     *
     * <p>{@link AutoCloseable} 是为了让调用方用 try-with-resources 表达
     * 「这段代码结束时请求就结束了」，避免忘记从表里摘掉自己。
     */
    final class Scope implements AutoCloseable {

        private final Thread worker;
        private final HttpURLConnection connection;
        private final AtomicBoolean closed = new AtomicBoolean();

        /**
         * 是否已经开始收到响应体。
         *
         * <p>它决定「同一种 IOException 该报成什么」：连接建立阶段读超时是
         * {@code request_timeout}，而响应中途读失败是 {@code stream_read_error} ——
         * 前者用户该重试，后者说明回答被打断、可能已经收到部分内容。两者对上层
         * 的处理方式不同，所以必须区分。
         */
        private volatile boolean responseStarted;

        private Scope(Thread worker, HttpURLConnection connection) {
            this.worker = worker;
            this.connection = connection;
        }

        /** 响应头已收到、开始读正文时调用。 */
        void markResponseStarted() {
            responseStarted = true;
        }

        /**
         * 把一次 IO 失败归类成机器可读的码；返回空串表示「这是一次未分类的失败」。
         *
         * <p>顺序有意义：已经收到响应就不该再报「超时」，即便异常类型恰好是超时 ——
         * 那种情况下对用户有用的信息是「回答被截断了」，而不是「请求超时」。
         */
        String failureCode(IOException error) {
            if (responseStarted) return FAILURE_STREAM;
            if (error instanceof SocketTimeoutException) return FAILURE_TIMEOUT;
            if (error instanceof EOFException) return FAILURE_EOF;
            String message = error.getMessage() == null ? "" : error.getMessage().toLowerCase(Locale.US);
            if (error instanceof SocketException
                    && (message.contains("reset") || message.contains("closed") || message.contains("broken pipe"))) {
                return FAILURE_RESET;
            }
            return "";
        }

        /** 给人看的失败说明。未分类的失败回落到 {@code network_error}。 */
        String failureMessage(IOException error) {
            String code = failureCode(error);
            String message = error.getMessage();
            return (code.isEmpty() ? FAILURE_UNKNOWN : code) + ": "
                    + (message == null || message.isEmpty() ? "network I/O failed" : message);
        }

        boolean isClosed() {
            return closed.get();
        }

        /**
         * 断开连接。CAS 保证只真正断一次 ——
         * 重复 disconnect 本身无害，但重复进入说明有并发路径，值得挡住。
         */
        private void cancel() {
            if (closed.compareAndSet(false, true)) connection.disconnect();
        }

        @Override
        public void close() {
            active.remove(worker, this);
            cancel();
        }
    }
}
