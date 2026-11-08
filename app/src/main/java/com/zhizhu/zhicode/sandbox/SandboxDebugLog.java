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
        StringBuilder text = new StringBuilder();
        text.append("IQ Sandbox 调试快照\n")
            .append("Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
            .append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
            .append("Host: ").append(context.getPackageName()).append(" uid=").append(Process.myUid()).append('\n')
            .append("Time: ").append(timestamp()).append("\n\n")
            .append(readStartupStage(context))
            .append("\n===== IQ SANDBOX EVENTS =====\n")
            .append(readEventTail()).append("\n===== APP-UID LOGCAT =====\n").append(readLogcat());
        return tail(text.toString(), MAX_TEXT);
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
        File target=eventFile; if(target==null||!target.exists()) return "(暂无事件)\n";
        synchronized (LOCK) {
            try (RandomAccessFile input=new RandomAccessFile(target,"r")) {
                long start=Math.max(0,input.length()-MAX_TEXT/2L); input.seek(start);
                byte[] data=new byte[(int)(input.length()-start)]; input.readFully(data);
                String v=new String(data,StandardCharsets.UTF_8); int first=start==0?0:v.indexOf('\n')+1;
                return v.substring(Math.max(0,first));
            } catch(Throwable e){ return "读取事件失败: "+e+"\n"; }
        }
    }

    private static String readLogcat() {
        StringBuilder out=new StringBuilder();
        try {
            java.lang.Process p=new ProcessBuilder("logcat","-d","-v","threadtime").redirectErrorStream(true).start();
            try(BufferedReader r=new BufferedReader(new InputStreamReader(p.getInputStream(),StandardCharsets.UTF_8))){
                String line; while((line=r.readLine())!=null){ out.append(line).append('\n'); if(out.length()>MAX_TEXT)out.delete(0,out.length()-MAX_TEXT); }
            } p.waitFor();
        } catch(Throwable e){out.append("读取 logcat 失败: ").append(e).append('\n');}
        return out.length()==0?"(暂无 logcat)\n":out.toString();
    }

    private static String timestamp(){return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS",Locale.US).format(new Date());}
    private static String tail(String v,int max){return v.length()<=max?v:v.substring(v.length()-max);}
}
