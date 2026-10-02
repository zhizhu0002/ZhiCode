package com.zhizhu.zhicode.sandbox;

import android.content.Context;
import android.os.Build;
import android.os.Process;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 跨进程运行期事件日志 —— 宿主与各 guest 共用一条时间线。
 *
 * <h3>与 {@link SandboxStage} 的分工</h3>
 * 两者都在记录「发生了什么」，但回答的是不同问题，所以不能合并：
 * <ul>
 *   <li>{@link SandboxStage} 记<b>启动阶段</b>，按 pid 分文件 → 回答「卡在哪一步」。
 *       它必须分文件，因为多进程同时写一个文件会互相冲掉。</li>
 *   <li>{@code SandboxConsole} 记<b>运行期事件</b>，单一追加文件 → 回答「发生过什么」。
 *       它必须<b>合</b>成一个文件，因为排错时看到的是「A 进程做完 X，B 进程才开始 Y」
 *       这种跨进程顺序；分成多份就看不出来了。</li>
 * </ul>
 * 追加写天然抗并发覆盖（每次只往后加），加上一个进程内锁避免交错，
 * 因此这里不需要按 pid 拆分。{@link #snapshot(Context)} 会把三者
 * （阶段 + 事件 + logcat）一起导出，作为诊断面板的内容。
 *
 * <h3>两条硬约束</h3>
 * <ol>
 *   <li><b>写日志绝不能影响调用方。</b>调用点是错误处理路径与事件回调，
 *       日志写失败若往上抛，会把一个可恢复的小问题变成功能失败。</li>
 *   <li><b>读出来的东西必须有界。</b>事件文件会一直长，logcat 动辄几 MB，
 *       而结果要塞进模型上下文。所有导出路径都截尾并限量。</li>
 * </ol>
 */
public final class SandboxConsole {

    private static final String TAG = "ZhiSandbox";

    /** 事件文件所在目录，相对宿主 files。 */
    private static final String DEBUG_DIR = "sandbox/debug";
    private static final String EVENT_FILE = "events.log";

    /**
     * 快照的最终字符上限。
     *
     * <p>这是在「够用来定位问题」与「不要把上下文占满」之间取的折中值。
     *
     * <p><b>⚠️ 这个上限是「阶段 + 事件」优先的，不是「谁在后面留谁」。</b>
     * 见 {@link #snapshot} 的说明：这里以前对整份快照做 {@code tail()}（截头留尾），
     * 而 logcat 排在最后且自带一份同样大的上限 —— 于是一份完整 logcat 就能把
     * 进程阶段与事件时间线整个挤掉，用户点开「诊断」看到的全是 libc 的
     * {@code Access denied finding property} 刷屏，真正的诊断信息一条都看不到。
     */
    private static final int MAX_TEXT = 420_000;

    /** 事件文件尾部读取上限。取快照总上限的一半，给阶段与 logcat 留出空间。 */
    private static final int MAX_EVENT_TAIL = MAX_TEXT / 2;

    /**
     * logcat 那一段的字符上限。
     *
     * <p>刻意远小于 {@link #MAX_TEXT}：logcat 是三者里最吵、最不值钱的一份
     * （前面把阶段与事件放好了，logcat 只是佐证）。它只吃「剩下的预算」，
     * 因此不可能再把前两段挤掉。
     */
    private static final int MAX_LOGCAT_CHARS = 120_000;

    /** 写文件与读文件的进程内互斥。跨进程一致性由「追加写 + 只读尾部」保证。 */
    private static final Object LOCK = new Object();

    /** 事件文件位置；{@link #init} 之前为 null，此时 {@link #event} 静默丢弃。 */
    private static volatile File eventFile;

    private SandboxConsole() {}

    /**
     * 指定事件文件位置。
     *
     * <p>刻意不做「首次初始化锁定」：控制器进程与各 guest 都会调它，
     * 而它们的 {@code filesDir} 可能不同（guest 可能被重定向到虚拟数据目录）。
     * 每次都用调用方自己的 Context 重算是正确的——各进程写各自的那一份，
     * 而诊断面板读的是宿主自己那份。
     */
    public static void init(Context context) {
        if (context == null) return;
        File dir = new File(context.getFilesDir(), DEBUG_DIR);
        if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) {
            // 目录建不出来就保持未初始化：写日志失败不该比这更严重。
            return;
        }
        eventFile = new File(dir, EVENT_FILE);
    }

    /**
     * 记一条运行期事件。
     *
     * <p>同时走 logcat 与文件：前者让 `adb logcat` 能直接看到，
     * 后者让 App 内的诊断面板不必申请 logcat 权限也能读到。
     */
    public static void event(String message) {
        String text = message == null ? "" : message;
        Log.i(TAG, text);

        File target = eventFile;
        if (target == null) return;
        String line = stamp() + " pid=" + Process.myPid() + "  " + text + "\n";
        synchronized (LOCK) {
            try (FileOutputStream out = new FileOutputStream(target, true)) {
                out.write(line.getBytes(StandardCharsets.UTF_8));
            } catch (Throwable ignored) {
                // 见类注释第 1 条：写日志失败不得影响调用方。
            }
        }
    }

    /**
     * 完整诊断快照：环境、各进程阶段、事件时间线、logcat tail。
     *
     * <p><b>⚠️ 分段限额，不做整体截断。</b>
     *
     * <p>以前这里是「拼完全部内容再做一次 {@code tail(text, MAX_TEXT)}」——
     * 保留末尾、砍掉开头。而 logcat 排在最后、又自带一份和总上限一样大的额度，
     * 于是一份完整 logcat 足以把前面「进程阶段」与「事件时间线」整段挤掉。
     * 用户点「诊断」看到的第一屏就是 libc 的 property 警告刷屏，
     * 反馈是「诊断也读不出来什么」—— 信息其实抓到了，是被后拼的那段挤走的。
     *
     * <p>现在改成：先把阶段与事件放好（这两段是排错的主证据，**永不截断**），
     * logcat 只吃剩下的预算（见 {@link #MAX_LOGCAT_CHARS}）。
     * 顺序不变，所以「第一屏就是主证据」这件事也顺带成立了。
     */
    public static String snapshot(Context context) {
        StringBuilder text = new StringBuilder(MAX_TEXT + 1024);
        text.append("ZhiCode 沙箱 调试快照\n")
                .append("Android ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
                .append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
                .append("Host: ").append(context == null ? "?" : context.getPackageName())
                .append(" uid=").append(Process.myUid()).append('\n')
                .append("Time: ").append(stamp()).append("\n\n")
                .append("===== 进程阶段（按 pid） =====\n")
                .append(SandboxStage.dump(context))
                .append("\n===== 事件时间线 =====\n")
                .append(eventTail());

        // logcat 最后，且额度是「总上限减掉已用」，再夹在 MAX_LOGCAT_CHARS 之内。
        int remaining = MAX_TEXT - text.length();
        int budget = Math.min(MAX_LOGCAT_CHARS, Math.max(8_000, remaining));
        text.append("\n===== APP-UID LOGCAT =====\n").append(logcatTail(budget));
        return text.toString();
    }

    /** 清空事件文件并留一条标记，这样快照里能看出「清过」而不是「一直没日志」。 */
    public static void clear() {
        synchronized (LOCK) {
            File file = eventFile;
            if (file != null && file.exists()) file.delete();
        }
        event("已清空沙箱日志");
    }

    /**
     * 把异常展开成文本。
     *
     * <p>存在的意义是让调用方不必各自 import StringWriter/PrintWriter 三件套；
     * 这类样板在错误处理路径上出现很多次。
     */
    public static String stackTrace(Throwable error) {
        if (error == null) return "";
        java.io.StringWriter buffer = new java.io.StringWriter();
        error.printStackTrace(new java.io.PrintWriter(buffer));
        return buffer.toString();
    }

    /**
     * 只读事件文件的尾部。
     *
     * <p>不整个读进来：文件会一直增长，而快照只需要最近一段。
     * 用 RandomAccessFile 直接 seek 到「末尾往前 MAX_EVENT_TAIL」处，
     * 代价与文件总长无关。
     *
     * <p>seek 落点几乎必然在某一行中间，所以把第一段残行丢掉——
     * 否则快照里会出现一条从半个时间戳开始、看起来像乱码的记录。
     */
    private static String eventTail() {
        File target = eventFile;
        if (target == null || !target.isFile()) return "(暂无事件)\n";
        synchronized (LOCK) {
            try (java.io.RandomAccessFile input = new java.io.RandomAccessFile(target, "r")) {
                long length = input.length();
                long start = Math.max(0, length - MAX_EVENT_TAIL);
                input.seek(start);
                byte[] data = new byte[(int) (length - start)];
                input.readFully(data);
                String value = new String(data, StandardCharsets.UTF_8);
                if (start == 0) return value;
                int firstBreak = value.indexOf('\n');
                return firstBreak < 0 ? value : value.substring(firstBreak + 1);
            } catch (Throwable error) {
                return "读取事件失败: " + error + "\n";
            }
        }
    }

    /**
     * 抓一份 logcat，最多 [budget] 个字符。
     *
     * <p>{@code -d} 表示 dump 后退出（不阻塞），{@code -v threadtime} 带上线程与时间戳。
     * 一边读一边削掉超出上限的头部：{@code logcat -d} 的输出可能很大，
     * 先全读进内存再截断等于把上限设在内存上而不是字符数上。
     *
     * <p>{@code *:V libc:S} 是 logcat 的**过滤表达式**（过滤器是包含式的，
     * 所以要显式写「全部 tag 都放行、只把 libc 静音」）。这里的 {@code *} 不会被 shell 展开 ——
     * ProcessBuilder 不经过 shell。
     * 之所以专门静音 libc：它在应用启动阶段会为每个 property 打一行
     * {@code Access denied finding property "persist.vendor.…"}，一次启动几百行，
     * 把这一段的预算全吃光。那既不是本应用的问题，也没有诊断价值
     * （用户点「诊断」看到的第一屏就是它，反馈是「读不出来什么」）。
     */
    private static String logcatTail(int budget) {
        StringBuilder out = new StringBuilder();
        // 注意用全限定名：本文件 import 了 android.os.Process（为了 Process.myPid()），
        // 裸写 Process 会指到它，而它没有 waitFor/destroy。
        java.lang.Process process = null;
        try {
            process = new ProcessBuilder("logcat", "-d", "-v", "threadtime", "*:V", "libc:S")
                    .redirectErrorStream(true)
                    .start();
            try (InputStream stream = process.getInputStream()) {
                byte[] chunk = new byte[8192];
                int read;
                while ((read = stream.read(chunk)) != -1) {
                    out.append(new String(chunk, 0, read, StandardCharsets.UTF_8));
                    if (out.length() > budget) trimHead(out, budget);
                }
            }
        } catch (Throwable error) {
            out.append("读取 logcat 失败: ").append(error).append('\n');
        } finally {
            waitQuietly(process);
        }
        return out.length() == 0 ? "(暂无 logcat)\n" : out.toString();
    }

    /** 等子进程退出，但不等它卡住自己——logcat 偶发不退出时不能拖住快照。 */
    private static void waitQuietly(java.lang.Process process) {
        if (process == null) return;
        try {
            if (!process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) process.destroy();
        } catch (Throwable ignored) {
            // 中断或进程已消失；快照已经有内容，不需要因此失败。
        }
    }

    private static void trimHead(StringBuilder buffer, int keep) {
        buffer.delete(0, buffer.length() - keep);
    }

    /** 毫秒级时间戳。跨进程排序靠它，所以精度比格式好看更重要。 */
    private static String stamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
    }
}
