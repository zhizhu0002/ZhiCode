package com.zhizhu.zhicode.sandbox;

import android.content.Context;
import android.os.Process;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

/**
 * 每进程一条的启动阶段日志。
 *
 * <p><b>为什么需要它。</b>旧实现只有一个 {@code sandbox/startup-stage.txt}，被所有进程
 * 共享覆盖写。沙箱启动时控制器、服务进程、Guest 代理会并发写这个文件，
 * <b>后写的会把先写的冲掉</b>：曾经因此出现过「控制器卡住但日志里只剩另一个进程的阶段」，
 * 完全无法定位。本类改为<b>按 pid 分文件、追加写</b>，任何进程的阶段都不会被别的进程抹掉。
 *
 * <p>读法：{@link #tail(Context)} 看当前进程，{@link #dump(Context)} 看全部进程。
 */
public final class SandboxStage {

    private static final String DIR = "sandbox/stages";
    private static final int MAX_BYTES = 64 * 1024;
    private static volatile File stageFile;

    private SandboxStage() {}

    /** 在 {@code Application.attachBaseContext} 里尽早调用，之后 {@link #mark} 才有落点。 */
    public static void init(Context context) {
        if (context == null) return;
        try {
            File dir = new File(context.getFilesDir(), DIR);
            if (!dir.exists() && !dir.mkdirs() && !dir.isDirectory()) return;
            stageFile = new File(dir, Process.myPid() + ".log");
        } catch (Throwable ignored) {
            stageFile = null;
        }
    }

    /** 追加一条阶段记录。任何异常都不得影响启动流程。 */
    public static void mark(String stage) {
        File target = stageFile;
        if (target == null || stage == null) return;
        String line = stamp() + " " + stage;
        try {
            if (target.length() > MAX_BYTES) {
                // 超限就截断重来，避免无限增长
                try (FileOutputStream out = new FileOutputStream(target, false)) {
                    out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
                }
                return;
            }
            try (FileOutputStream out = new FileOutputStream(target, true)) {
                out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
            }
        } catch (Throwable ignored) {
            // 阶段日志是诊断手段，绝不允许它把启动流程带崩
        }
    }

    /** 当前进程最后一条阶段，用于把「卡在哪一步」直接写进错误信息。 */
    public static String lastMark() {
        String text = readTail(stageFile, 8 * 1024);
        if (text.isEmpty()) return "(无阶段记录)";
        String[] lines = text.split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (!line.isEmpty()) return line;
        }
        return "(无阶段记录)";
    }

    /** 当前进程的阶段日志。 */
    public static String tail(Context context) {
        String text = readTail(stageFile, MAX_BYTES);
        return text.isEmpty() ? "(当前进程暂无阶段记录)\n" : text;
    }

    /** 全部进程的阶段日志，按修改时间从新到旧。 */
    public static String dump(Context context) {
        if (context == null) return "(无 Context)\n";
        File dir = new File(context.getFilesDir(), DIR);
        File[] files = dir.listFiles();
        if (files == null || files.length == 0) return "(暂无任何进程阶段记录)\n";
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        StringBuilder out = new StringBuilder();
        for (File file : files) {
            out.append("===== pid ").append(file.getName().replace(".log", "")).append(" =====\n");
            out.append(readTail(file, 8 * 1024));
        }
        return out.toString();
    }

    private static String readTail(File file, int maxBytes) {
        if (file == null || !file.isFile()) return "";
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            long length = input.length();
            long start = Math.max(0L, length - maxBytes);
            input.seek(start);
            byte[] data = new byte[(int) (length - start)];
            input.readFully(data);
            String text = new String(data, StandardCharsets.UTF_8);
            if (start > 0) {
                int firstBreak = text.indexOf('\n');
                if (firstBreak >= 0) text = text.substring(firstBreak + 1);
            }
            return text;
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String stamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
    }
}
