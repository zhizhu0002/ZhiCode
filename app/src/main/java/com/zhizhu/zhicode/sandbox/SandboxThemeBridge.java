package com.zhizhu.zhicode.sandbox;

import android.content.Context;

/**
 * 宿主 → 沙箱设置的单向桥。
 *
 * <p>存在的唯一理由是**可见性**：[@link SandboxSettingsStore} 是包级私有
 * （它读写的是沙箱自己的内部设置文件，不该被别的模块随手改），
 * 而主进程需要在每次 `onResume` 把自己的深浅色写进去，供 guest 侧的悬浮条读取。
 *
 * <p>为什么不能由 guest 直接读宿主的主题设置：guest 进程的
 * `getSharedPreferences` 被 BlackBox 虚拟化，读到的永远是默认值 ——
 * 症状就是"用户选浅色、悬浮条还是深色"。
 *
 * <p>只暴露这一个写入方法，不把整个 store 打开：这个类**不该**变成
 * "绕过封装改沙箱设置"的后门。
 */
public final class SandboxThemeBridge {

    private SandboxThemeBridge() {}

    /**
     * 记录宿主当前的深浅。
     *
     * <p>只在主进程调用。值没变时 [@link SandboxSettingsStore} 不会写盘，
     * 所以挂在 `onResume` 上是安全的。
     */
    public static void publishThemeDark(Context context, boolean dark) {
        if (context == null) return;
        SandboxSettingsStore.publishThemeDark(context.getApplicationContext(), dark);
    }
}
