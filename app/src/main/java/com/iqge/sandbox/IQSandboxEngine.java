package com.iqge.sandbox;

import android.app.Activity;
import android.content.Context;

/**
 * IQ 沙箱宿主引擎的占位实现。
 *
 * <p><b>这是 Phase 7（Bcore 移植）之前的桩。</b>原版这一个类是 BlackBox 的门面
 * （`BlackBoxCore` 的包装，负责 create / install / launch / stop / clearData / uninstall、
 * 已安装应用列表、运行中进程、root 隐藏、悬浮日志开关等）。它依赖整个
 * `top.niunaijun.blackbox` 及其 native `libblackbox.so`，不在本期范围内。
 *
 * <p>这里只保留被引擎侧引用到的 3 个成员，并一律返回 {@code null}：
 * {@code SandboxAgentBridge} 在拿到 null 时会干净地 no-op（见该类的
 * `if (a == null || pkg == null) return;`），因此不会崩溃，也不会假装成功。
 * 其余能力（安装/启动 guest、进程列表）在 Phase 7 之前由
 * 「IQ 沙箱」入口提示「待移植」。
 */
public final class IQSandboxEngine {

    /** 当前处于前台的 guest Activity。Phase 7 之前恒为 null。 */
    public static Activity resumedActivity() {
        return null;
    }

    /** 当前处于前台的 guest 包名。Phase 7 之前恒为 null。 */
    public static String resumedPackage() {
        return null;
    }

    /**
     * 宿主进程的 Context（用于定位 `files/sandbox/...` 下的截图与结果目录）。
     * Phase 7 之前返回 null，调用方会退回使用自身 Context。
     */
    public static Context hostContext() {
        return null;
    }

    private IQSandboxEngine() {}
}
