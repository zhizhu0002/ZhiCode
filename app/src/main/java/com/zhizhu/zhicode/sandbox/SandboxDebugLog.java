package com.zhizhu.zhicode.sandbox;

import android.content.Context;
import android.os.Build;
import android.os.Process;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Shared cross-process event log for the sandbox host and virtual app processes. */
public final class SandboxDebugLog {
    private static final String TAG = "IQSandbox";
    private static final int MAX_TEXT = 420_000;
    /** logcat 最多取的行数。见 [readLogcat]：不限行数会把这台设备的整个缓冲区读出来。 */
    private static final int LOGCAT_MAX_LINES = 400;
    /** 等 logcat dump 完的上限。超时就用手上已有的部分，不让界面无限等。 */
    private static final long LOGCAT_TIMEOUT_MS = 4_000L;
    /** UI/main-thread work below this duration is normal and stays out of the log. */
    private static final long STALL_THRESHOLD_MS = 120L;
    private static final Object LOCK = new Object();
    private static volatile File eventFile;
    private SandboxDebugLog() {}

    /**
     * Records main-thread work that ran long enough to drop frames. Callers time their own
     * operation and pass the elapsed milliseconds, so silent frames cost nothing.
     */
    public static void stall(String label, long elapsedMs) {
        if (elapsedMs < STALL_THRESHOLD_MS) return;
        event("卡顿 " + label + " " + elapsedMs + "ms");
    }

    public static void init(Context context) {
        File dir = new File(context.getFilesDir(), "sandbox/debug");
        if (!dir.exists()) dir.mkdirs();
        eventFile = new File(dir, "events.log");
    }

    public static void event(String message) {
        String line = timestamp() + " pid=" + Process.myPid() + "  " + message;
        Log.i(TAG, message);
        File target = eventFile;
        if (target == null) return;
        synchronized (LOCK) {
            try (FileOutputStream out = new FileOutputStream(target, true)) {
                out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
            } catch (Throwable ignored) {}
        }
    }

    public static String snapshot(Context context) {
        return snapshot(context, MAX_TEXT, true);
    }

    /**
     * 限量快照。
     *
     * <h3>为什么要能限量</h3>
     * 全量快照的上限是 42 万字符，而把它整段交给 Compose 去测量排版时，
     * 一帧要处理几十万个字形 —— 这正是"点开诊断很卡"的来源之一。
     * 诊断真正要看的是**最近发生了什么**，所以按尾部截断。
     *
     * @param includeLogcat 要不要连 logcat 一起读。**界面路径应当传 false**：
     *   读 logcat 要起一个子进程并等它 dump 完，而它恰恰是最慢、信息密度最低的一段。
     *   事件流与 startup-stage 才是"引擎走到哪一步"的答案。
     */
    public static String snapshot(Context context, int maxChars) {
        return snapshot(context, maxChars, true);
    }

    public static String snapshot(Context context, int maxChars, boolean includeLogcat) {
        int limit = Math.max(4_000, Math.min(MAX_TEXT, maxChars));
        // logcat 分到 1/4 预算，其余留给事件流与 startup-stage（它们更有价值）。
        int logcatBudget = includeLogcat ? limit / 4 : 0;
        StringBuilder text = new StringBuilder();
        text.append("IQ Sandbox 调试快照\n")
            .append("Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
            .append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
            .append("Host: ").append(context.getPackageName()).append(" uid=").append(Process.myUid()).append('\n')
            .append("Time: ").append(timestamp()).append("\n\n")
            .append(readStartupStage(context))
            .append("\n===== IQ SANDBOX EVENTS =====\n")
            .append(readEventTail(limit - logcatBudget))
            .append("\n===== APP-UID LOGCAT =====\n")
            .append(readLogcat(logcatBudget));
        return tail(text.toString(), limit);
    }

    /**
     * BlackBox 自己写的启动阶段标记（`filesDir/sandbox/startup-stage.txt`）。
     *
     * <h3>为什么这一行比我们的日志更有用</h3>
     * 它是**对方**写的，记录的是 `doAttachBaseContext` / `doCreate` 内部走到了哪一步
     * （例如 `attach:controller-hookless`、`attach:native-essential-ok`）。
     *
     * 我们这边只能看到「引擎初始化超时」这个结果，而这一行能直接指出
     * 是 attach 根本没跑、还是 attach 跑了但 create 卡住。
     * 判断这两种情况的修法完全不同，没有这一行就只能靠猜。
     *
     * <p>注意这是**单行覆盖写**（不是追加）：它永远只反映最近一次 attach/create。
     */
    private static String readStartupStage(Context context) {
        if (context == null) return "===== SANDBOX STARTUP STAGE =====\n（没有 Context）\n";
        File file = new File(new File(context.getFilesDir(), "sandbox"), "startup-stage.txt");
        if (!file.isFile()) {
            return "===== SANDBOX STARTUP STAGE =====\n"
                + "（没有 startup-stage.txt：说明 BlackBoxCore.doAttachBaseContext 从未执行到 —— "
                + "沙盒进程的 Application.attachBaseContext 没有调用 ZhiSandboxEngine.attach）\n";
        }
        try {
            try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
                byte[] data = new byte[(int) Math.min(input.length(), 4096)];
                input.readFully(data);
                return "===== SANDBOX STARTUP STAGE =====\n"
                    + new String(data, StandardCharsets.UTF_8).strip() + "\n";
            }
        } catch (Throwable e) {
            return "===== SANDBOX STARTUP STAGE =====\n（读取失败：" + e + "）\n";
        }
    }

    public static void clear() {
        synchronized (LOCK) { File f=eventFile; if(f!=null&&f.exists()) f.delete(); }
        event("沙箱日志已清空");
    }

    public static String stackTrace(Throwable error) {
        java.io.StringWriter b=new java.io.StringWriter(); error.printStackTrace(new java.io.PrintWriter(b)); return b.toString();
    }

    private static String readEventTail() {
        return readEventTail(MAX_TEXT);
    }

    /** 事件流的尾部。`limit` 是**总预算**，这里只花一半，另一半留给 logcat。 */
    private static String readEventTail(int limit) {
        int budget = Math.max(2_048, limit / 2);
        File target=eventFile; if(target==null||!target.exists()) return "(暂无事件)\n";
        synchronized (LOCK) {
            try (RandomAccessFile input=new RandomAccessFile(target,"r")) {
                long start=Math.max(0,input.length()-budget); input.seek(start);
                byte[] data=new byte[(int)(input.length()-start)]; input.readFully(data);
                String v=new String(data,StandardCharsets.UTF_8); int first=start==0?0:v.indexOf('\n')+1;
                return v.substring(Math.max(0,first));
            } catch(Throwable e){ return "读取事件失败: "+e+"\n"; }
        }
    }

    /**
     * 读 logcat 的尾部。
     *
     * <h3>为什么必须 `-t` 限行数</h3>
     *
     * 原先只有 `-d`（dump）—— 那会把这台设备**整个** logcat 环形缓冲读出来，
     * 量级是几 MB、上万行。而我们对几分钟前的系统日志没有任何兴趣。
     * 加上 `-t N` 之后只取最近 N 行，读取量降两个数量级。
     * **这是"点开诊断很卡 / 悬浮窗日志很卡"最直接的原因。**
     *
     * <h3>为什么用环形缓冲而不是 `StringBuilder.delete`</h3>
     *
     * 原实现读满上限后每来一行就 `out.delete(0, 超出量)` —— 在一个 42 万字符的
     * `StringBuilder` 上反复做**头部删除**，每行都要搬一遍剩下的全部字符，
     * 总代价 O(n²)。日志越长它越慢，而"日志长"恰恰是用户最需要打开诊断的时候。
     *
     * 换成 `ArrayDeque` 逐行入队、从头部丢：总代价 O(n)。
     *
     * @param limit 字符预算。<= 0 表示**跳过** logcat（界面路径用得上：
     *   事件流与 startup-stage 才是诊断要看的，logcat 只是补充）。
     */
    private static String readLogcat(int limit) {
        if (limit <= 0) return "(已跳过 logcat)\n";
        java.util.ArrayDeque<String> ring = new java.util.ArrayDeque<>();
        int chars = 0;
        // 必须写全限定名：本文件上面 import 了 android.os.Process，
        // 简写的 `Process` 指的是它，不是 java.lang.Process。
        java.lang.Process process = null;
        try {
            process = new ProcessBuilder(
                    "logcat", "-d",
                    "-t", String.valueOf(LOGCAT_MAX_LINES),
                    "-v", "threadtime")
                    .redirectErrorStream(true)
                    .start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    ring.addLast(line);
                    chars += line.length() + 1;
                    while (chars > limit && !ring.isEmpty()) {
                        chars -= ring.removeFirst().length() + 1;
                    }
                }
            }
            if (!process.waitFor(LOGCAT_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                ring.addFirst("(logcat 读取超时 " + LOGCAT_TIMEOUT_MS + "ms，仅显示已读到的部分)");
            }
        } catch (Throwable e) {
            return "读取 logcat 失败: " + e + "\n";
        } finally {
            // 进程已经退出时 destroy 是无害的；没退出（超时）时它是必需的 ——
            // 否则会留一个 logcat 子进程一直挂在应用身上。
            if (process != null) process.destroy();
        }
        if (ring.isEmpty()) return "(暂无 logcat)\n";
        StringBuilder out = new StringBuilder(chars + 64);
        for (String line : ring) out.append(line).append('\n');
        return out.toString();
    }

    private static String timestamp(){return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS",Locale.US).format(new Date());}
    private static String tail(String v,int max){return v.length()<=max?v:v.substring(v.length()-max);}
}
