package com.zhizhu.zhicode.sandbox;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;

import top.niunaijun.blackbox.SandboxContract;

/**
 * 进程角色判定 —— 决定哪些进程允许挂载 BlackBox 引擎。
 *
 * <p><b>主进程必须保持干净。</b>BlackBox 会 hook ART、替换 BootClassLoader 的 IO、
 * 并接管两百来个系统 Binder 服务；其中任何一处失败在启动期都会把整个应用带走。
 * 因此引擎只挂在沙箱自己的进程里，编辑器与 Agent 执行链不受影响。
 *
 * <p><b>本类刻意不引用 {@code BlackBoxCore}。</b>它在每个进程的
 * {@code Application.attachBaseContext} 里都会被调用，包括主进程；
 * 若读取 {@code BlackBoxCore} 的字段就会触发该类的静态初始化，把 BlackBox 拉进主进程。
 * 进程名常量走 {@link SandboxContract}（只有编译期常量，javac 内联，零类加载）。
 *
 * <p>见 docs/sandbox-host.md。
 */
public final class SandboxProcess {

    private SandboxProcess() {}

    /** 从 {@code /proc/self/cmdline} 读取当前进程名；失败时退回包名。 */
    public static String name(Context context) {
        try (FileInputStream in = new FileInputStream(new File("/proc/self/cmdline"));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            int b;
            while ((b = in.read()) != -1 && b != 0) out.write(b);
            String value = out.toString("UTF-8").trim();
            if (!value.isEmpty()) return value;
        } catch (Throwable ignored) {
            // 读不到就退回包名：宁可判成主进程（不挂引擎），也不能误判成沙箱进程。
        }
        return context == null ? "" : context.getPackageName();
    }

    /** 是否主进程（编辑器 / Agent 所在进程）。 */
    public static boolean isMain(Context context) {
        return context != null && context.getPackageName().equals(name(context));
    }

    /** 是否沙箱控制器进程。该进程 hookless，只编排 Binder/包服务。 */
    public static boolean isController(Context context) {
        return context != null
                && name(context).equals(context.getPackageName() + SandboxContract.CONTROLLER_PROCESS_SUFFIX);
    }

    /** 是否引擎服务进程（承载 BlackBox 运行时与 native hook）。 */
    public static boolean isServer(Context context) {
        return context != null
                && name(context).equals(context.getPackageName() + SandboxContract.SERVER_PROCESS_SUFFIX);
    }

    /**
     * 本进程是否应该挂载引擎：控制器、服务进程，以及 {@code :p0..:p49} 这组 Guest 代理进程。
     *
     * <p>进程池上界取自 {@link SandboxContract#GUEST_PROCESS_COUNT}，
     * 与引擎 {@code ProxyManifest} 里声明的代理组件数量必须一致。
     */
    public static boolean ownsEngine(Context context) {
        if (context == null) return false;
        if (isController(context) || isServer(context)) return true;
        String prefix = context.getPackageName() + SandboxContract.GUEST_PROCESS_PREFIX;
        String process = name(context);
        if (!process.startsWith(prefix)) return false;
        try {
            int index = Integer.parseInt(process.substring(prefix.length()));
            return index >= 0 && index < SandboxContract.GUEST_PROCESS_COUNT;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
