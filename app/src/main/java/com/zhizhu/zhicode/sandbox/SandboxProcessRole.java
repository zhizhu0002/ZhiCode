package com.zhizhu.zhicode.sandbox;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;

/** Keeps BlackBox completely out of IQ Code's main process. */
public final class SandboxProcessRole {
    private SandboxProcessRole() {}

    public static String processName(Context context) {
        try {
            File f = new File("/proc/self/cmdline");
            try (FileInputStream in = new FileInputStream(f); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                int b;
                while ((b = in.read()) != -1 && b != 0) out.write(b);
                String s = out.toString("UTF-8").trim();
                if (!s.isEmpty()) return s;
            }
        } catch (Throwable ignored) {}
        return context == null ? "" : context.getPackageName();
    }

    public static boolean isMainProcess(Context context) {
        if (context == null) return false;
        return context.getPackageName().equals(processName(context));
    }

    /**
     * 本应用是否**跑在别人的虚拟运行时里**（数据目录被虚拟化）。
     *
     * <p>判定方式与 `EnvDoctor` 一致：BlackBox 会把 guest 的 dataDir
     * 重映射到 `.../blackbox/data/user/0/<pkg>`，那条路径里带 `blackbox`。
     * 这是唯一可靠的判据 —— `/proc/self/cmdline` 里看不出来
     * （进程名仍是 `com.zhizhu.code`）。
     *
     * <p>它**只用于诊断与界面文案**，不参与任何控制流决策：
     * 见 {@link #shouldHostEngineInPlace} 上那段"已否决"的说明。
     */
    public static boolean isInsideVirtualRuntime(Context context) {
        if (context == null) return false;
        try {
            String dataDir = context.getApplicationInfo().dataDir;
            return dataDir != null && dataDir.contains("blackbox");
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * （已否决，保留备查）曾想用「嵌套时就地在当前进程承载引擎」绕开 IPC 问题。
     *
     * <p><b>实测直接打不开应用。</b>外层 BlackBox 已经在这个进程里装过一层
     * native hook（`libblackbox.so` 的 ART/JNI/文件系统 hook），
     * 再叠一层会让进程在启动期就死掉，连 Java 异常都来不及抛 ——
     * 表现是点图标毫无反应，没有任何崩溃日志可看。
     *
     * <p>所以**引擎永远只在子进程里**。这个方法刻意保留但**不得**接回控制流：
     * 它存在的意义是让下一个人先读到这里，而不是把那次崩溃重新踩一遍。
     *
     * @deprecated 不要使用。见方法说明。
     */
    @Deprecated
    public static boolean shouldHostEngineInPlace(Context context) {
        return isMainProcess(context) && isInsideVirtualRuntime(context);
    }

    /**
     * 本进程是否应该加载 BlackBox 引擎。
     *
     * <h3>只有子进程承载引擎</h3>
     *
     * | 进程 | 跑引擎 |
     * |---|---|
     * | `:iqsandbox`（控制器） | ✅ |
     * | `:black` / `:pN`（guest 代理） | ✅ |
     * | 主进程 | ❌ **永远不** |
     *
     * <p>主进程保持"一个 BlackBox 类都不加载"是这套设计最重要的性质：
     * 主界面的启动速度与稳定性不受 hook 运行时影响，而且这条边界一旦破掉，
     * 症状极其隐蔽（启动慢几十毫秒、偶发 crash），不会有人联想到沙盒。
     */
    public static boolean shouldAttachBlackBox(Context context) {
        if (context == null) return false;
        String pkg = context.getPackageName();
        String proc = processName(context);
        if (proc.equals(pkg + ":iqsandbox") || proc.equals(pkg + ":black")) return true;
        String prefix = pkg + ":p";
        if (!proc.startsWith(prefix)) return false;
        try {
            int index = Integer.parseInt(proc.substring(prefix.length()));
            return index >= 0 && index < 50;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
