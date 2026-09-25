package com.iqge.iqcode.compose

import android.app.Application
import android.content.Context
import com.iqge.sandbox.SandboxDebugLog
import com.termux.shared.termux.TermuxConstants

/**
 * 应用入口。
 *
 * 这里做且只做两件必须在所有其他类之前完成的事：
 *
 * 1. **配置 Termux 路径**。`TermuxConstants` 是全部路径的单一来源（35 处引用），
 *    必须在任何引擎/终端类被加载前用本应用自己的包名与私有目录初始化。
 *    晚一步就会有人读到兜底的 `com.iqge` 路径。
 * 2. **安装崩溃日志**。放在 `attachBaseContext` 里，早于 ContentProvider 创建，
 *    否则启动期崩溃抓不到。
 *
 * 注意：IQ 沙箱（BlackBox 宿主）**不在本期范围**。原版 `IQCodeApplication` 会在
 * `:iqsandbox` / `:black` / `:pN` 进程里 attach BlackBox，本工程等 Phase 7 再补。
 * 这里刻意不引用那些类，避免在没有 Bcore 的情况下启动即 `NoClassDefFoundError`。
 */
class IqCodeApplication : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)

        // 必须是第一个动作：后面所有路径都依赖它
        TermuxConstants.configure(base)

        SandboxDebugLog.init(base)
        installCrashLogger()
    }

    override fun onCreate() {
        super.onCreate()
        // Phase 7（Bcore 沙箱宿主）将在此接入 SandboxAgentBridge / SandboxTermuxBridge。
    }

    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            SandboxDebugLog.event(
                "未捕获异常 [" + packageName + "/" + thread.name + "]: " +
                    SandboxDebugLog.stackTrace(error)
            )
            previous?.uncaughtException(thread, error)
        }
    }
}
