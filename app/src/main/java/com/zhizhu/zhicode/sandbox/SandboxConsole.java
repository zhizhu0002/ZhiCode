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

/**
 * 跨进程事件日志 —— 宿主与各 Guest 进程共用一条时间线。
 *
 * <p>与 {@link SandboxStage} 的分工：
 * <ul>
 *   <li>{@code SandboxStage} 记录<b>启动阶段</b>，按 pid 分文件，用于回答「卡在哪一步」；</li>
 *   <li>{@code SandboxConsole} 记录<b>运行期事件</b>，单一追加文件，用于回答「发生过什么」。</li>
 * </ul>
 * {@link #snapshot(Context)} 会把两者连同 logcat tail 一起导出，作为诊断面板的内容。
 */
public final class SandboxConsole {

    private static final String TAG = "ZhiSandbox";
    private static final int MAX_TEXT = 420_000;
    private static final Object LOCK = new Object();

    private static volatile File eventFile;

    private SandboxConsole() {}

    public static void init(Context context) {
        File dir = new File(context.getFilesDir(), "sandbox/debug");
        if (!dir.exists()) dir.mkdirs();
        eventFile = new File(dir, "events.log");
    }

    public static void event(String message) {
        String line = stamp() + " pid=" + Process.myPid() + "  " + message;
        Log.i(TAG, message);
        File target = eventFile;
        if (target == null) return;
        synchronized (LOCK) {
            try (FileOutputStream out = new FileOutputStream(target, true)) {
                out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
            } catch (Throwable ignored) {
                // 日志写入失败不得影响调用方
            }
        }
    }

    /** 完整诊断快照：环境、各进程阶段、事件时间线、logcat tail。 */
    public static String snapshot(Context context) {
        StringBuilder text = new StringBuilder();
        text.append("蜘蛛沙箱 调试快照\n")
                .append("Android ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
                .append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
                .append("Host: ").append(context.getPackageName())
                .append(" uid=").append(Process.myUid()).append('\n')
                .append("Time: ").append(stamp()).append("\n\n")
                .append("===== 进程阶段（按 pid） =====\n")
                .append(SandboxStage.dump(context))
                .append("\n===== 事件时间线 =====\n")
                .append(readEventTail())
                .append("\n===== APP-UID LOGCAT =====\n")
                .append(readLogcat());
        return tail(text.toString(), MAX_TEXT);
    }

    public static void clear() {
        synchronized (LOCK) {
            File file = eventFile;
            if (file != null && file.exists()) file.delete();
        }
        event("沙箱日志已清空");
    }

    public static String stackTrace(Throwable error) {
        java.io.StringWriter buffer = new java.io.StringWriter();
        error.printStackTrace(new java.io.PrintWriter(buffer));
        return buffer.toString();
    }

    private static String readEventTail() {
        File target = eventFile;
        if (target == null || !target.exists()) return "(暂无事件)\n";
        synchronized (LOCK) {
            try (RandomAccessFile input = new RandomAccessFile(target, "r")) {
                long start = Math.max(0, input.length() - MAX_TEXT / 2L);
                input.seek(start);
                byte[] data = new byte[(int) (input.length() - start)];
                input.readFully(data);
                String value = new String(data, StandardCharsets.UTF_8);
                int firstBreak = start == 0 ? 0 : value.indexOf('\n') + 1;
                return value.substring(Math.max(0, firstBreak));
            } catch (Throwable error) {
                return "读取事件失败: " + error + "\n";
            }
        }
    }

    private static String readLogcat() {
        StringBuilder out = new StringBuilder();
        try {
            java.lang.Process process = new ProcessBuilder("logcat", "-d", "-v", "threadtime")
                    .redirectErrorStream(true).start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    out.append(line).append('\n');
                    if (out.length() > MAX_TEXT) out.delete(0, out.length() - MAX_TEXT);
                }
            }
            process.waitFor();
        } catch (Throwable error) {
            out.append("读取 logcat 失败: ").append(error).append('\n');
        }
        return out.length() == 0 ? "(暂无 logcat)\n" : out.toString();
    }

    private static String stamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
    }

    private static String tail(String value, int max) {
        return value.length() <= max ? value : value.substring(value.length() - max);
    }
}
