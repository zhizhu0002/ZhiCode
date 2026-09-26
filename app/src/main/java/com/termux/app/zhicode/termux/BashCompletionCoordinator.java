package com.termux.app.zhicode.termux;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 协调「命令真的跑完了吗」这件事的两个独立信号。
 *
 * <h3>为什么不能只看 {@link Process#waitFor()}</h3>
 * 命令是包在一个 wrapper 里的：wrapper 先写 pid，跑真正的命令，然后
 * 把退出码写进一个控制文件并 {@code exit}。于是有两个信号会先后到达：
 * <ol>
 *   <li><b>控制文件</b>—— wrapper 明确报告了退出码。它比进程退出**更早、更准**：
 *       进程退出只是「这个进程没了」，而控制文件说明「命令自己认为它结束了」。</li>
 *   <li><b>进程退出</b>—— 用于兜底：wrapper 可能被信号杀掉，那样永远不会有控制文件。
 *       没有这个信号的话我们会一直等到超时。</li>
 * </ol>
 * 两者都可能先到，且**都可能不来**（超时），所以状态必须由这个对象统一裁定。
 *
 * <h3>先到者为准</h3>
 * 退出码一旦定下就不再改（{@link #publishControl} 与
 * {@link #publishProcessExit} 都检查过）。顺序很重要：控制文件后到时，
 * 它带来的那个码是**更可信**的，但此时进程可能已经以别的码退出了 ——
 * 后者往往是被信号杀的（比如 wrapper 报告 0 之后清理阶段被杀）。
 * 因此这里的选择是「先到者为准」，而不是「控制文件总是优先」。
 */
public final class BashCompletionCoordinator {

    /** 退出码是从哪来的。{@link #CONTROL} 说明拿到了 wrapper 的完整报告。 */
    public enum Source { NONE, CONTROL, PROCESS }

    /** 退出码的合法范围（shell 约定）。 */
    private static final int MIN_EXIT_CODE = 0;
    private static final int MAX_EXIT_CODE = 255;

    /** 退出码已定。 */
    private final CountDownLatch resolved = new CountDownLatch(1);
    /** 进程本身已退出（与控制文件无关）。 */
    private final CountDownLatch processExit = new CountDownLatch(1);

    private boolean hasExitCode;
    private boolean processExited;
    private int exitCode;
    private Source source = Source.NONE;

    /**
     * 解析控制文件里的一行。
     *
     * <p>格式是 {@code <token>:<退出码>}，token 是一次执行一个的随机串 ——
     * 它的作用不是保密，而是**证明这行是我们自己那个 wrapper 写的**：
     * 控制文件在 {@code $PREFIX/tmp} 下，而那个目录里的文件可能被上一次
     * 未清理的执行留下。没有 token 校验的话，读到上次残留的退出码会让
     * 这次的命令被误判成已完成。
     *
     * <p>分隔符的位置也必须等于 token 的长度：直接 {@code split(":")} 在
     * token 本身含冒号（不会，但不必依赖）或退出码部分含冒号时会出错。
     *
     * @return 退出码；这一行不属于本次执行、或不是合法的退出码时返回 {@code null}
     */
    public static Integer parseControlLine(String line, String token) {
        if (line == null || token == null || token.isEmpty()) return null;
        int separator = line.indexOf(':');
        if (separator != token.length() || separator <= 0) return null;
        if (!token.equals(line.substring(0, separator))) return null;
        String rawCode = line.substring(separator + 1);
        if (rawCode.isEmpty()) return null;
        try {
            int code = Integer.parseInt(rawCode);
            return isValidExitCode(code) ? Integer.valueOf(code) : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * 报告 wrapper 给出的退出码。
     *
     * @return 是否由**本次调用**定下了退出码
     */
    public synchronized boolean publishControl(int code) {
        if (!isValidExitCode(code) || hasExitCode) return false;
        hasExitCode = true;
        exitCode = code;
        source = Source.CONTROL;
        resolved.countDown();
        return true;
    }

    /**
     * 报告进程已退出。
     *
     * <p>{@link #processExited} 与「是否定下退出码」是两件事：
     * 进程退出一定发生（哪怕控制文件已经先给了码），所以那个闩总是要放行。
     */
    public synchronized boolean publishProcessExit(int code) {
        processExited = true;
        processExit.countDown();
        if (hasExitCode) return false;
        hasExitCode = true;
        exitCode = code;
        source = Source.PROCESS;
        resolved.countDown();
        return true;
    }

    /** 等待退出码定下（或超时）。 */
    public boolean await(long timeoutMs) throws InterruptedException {
        return resolved.await(Math.max(0L, timeoutMs), TimeUnit.MILLISECONDS);
    }

    /** 等待进程真的退出（或超时）。 */
    public boolean awaitProcessExit(long timeoutMs) throws InterruptedException {
        return processExit.await(Math.max(0L, timeoutMs), TimeUnit.MILLISECONDS);
    }

    public synchronized boolean hasExitCode() { return hasExitCode; }

    public synchronized int exitCode() { return exitCode; }

    public synchronized Source source() { return source; }

    public synchronized boolean processExited() { return processExited; }

    private static boolean isValidExitCode(int code) {
        return code >= MIN_EXIT_CODE && code <= MAX_EXIT_CODE;
    }
}
