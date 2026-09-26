package com.zhizhu.zhicode.compose

import android.app.Application
import android.content.Context
import com.zhizhu.zhicode.sandbox.ZhiSandbox
import com.zhizhu.zhicode.sandbox.SandboxAgentBridge
import com.zhizhu.zhicode.sandbox.SandboxConsole
import com.zhizhu.zhicode.sandbox.SandboxProcess
import com.zhizhu.zhicode.sandbox.SandboxStage
import com.zhizhu.zhicode.sandbox.SandboxTermuxBridge
import com.termux.shared.termux.TermuxConstants

/**
 * 应用入口。
 *
 * 这里做且只做四件必须在所有其他类之前完成的事：
 *
 * 1. **配置 Termux 路径**。`TermuxConstants` 是全部路径的单一来源（35 处引用），
 *    必须在任何引擎/终端类被加载前用本应用自己的包名与私有目录初始化。
 *    晚一步就会有人读到兜底的 `com.iqge` 路径。
 * 2. **安装崩溃日志**。放在 `attachBaseContext` 里，早于 ContentProvider 创建，
 *    否则启动期崩溃抓不到。
 * 3. **IQ 沙箱（BlackBox）只挂在沙箱自己的进程里**（`:zhisandbox` / `:black` / `:p0..:p49`）。
 *    主进程刻意不 attach：沙箱要 hook ART、替换一堆系统服务，任何一处失败
 *    在启动期都会把整个应用带走；隔离在 guest 进程里，崩了只影响那一个 guest，
 *    编辑器与 Agent 执行链不受影响。判定见 [SandboxProcess]。
 * 4. **主进程启动沙箱的 Termux 桥**，让 guest 侧的沙箱工具能复用同一条内置 Termux 用户空间。
 */
class ZhiCodeApplication : Application() {

    /** 本进程是否属于沙箱（guest / 服务 / 控制进程）。 */
    private var sandboxProcess = false

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)

        // 必须是第一个动作：后面所有路径都依赖它
        TermuxConstants.configure(base)

        SandboxConsole.init(base)
        // 阶段日志按 pid 分文件，必须尽早初始化：引擎 attach/create 的每一步都靠它留痕，
        // 否则「卡在哪一步」只能靠猜（旧实现的单文件覆盖写就是这么丢掉线索的）。
        SandboxStage.init(base)
        SandboxStage.mark("app:attach-begin")
        installCrashLogger()

        sandboxProcess = SandboxProcess.ownsEngine(base)
        SandboxConsole.event(
            "Application attach: " + SandboxProcess.name(base) + " sandbox=" + sandboxProcess
        )
        if (sandboxProcess) {
            // 顺序不能换：attach 必须早于 create，且都发生在任何 ContentProvider 之后被
            // 首次 IPC 触发之前（SandboxRpcService 自己在 onCreate 里也会补一次排队初始化）。
            ZhiSandbox.attach(base)
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (sandboxProcess) {
            SandboxAgentBridge.register(this)
            ZhiSandbox.create()
        } else if (SandboxProcess.isMain(this)) {
            SandboxTermuxBridge.start(this)
        }
    }

    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            SandboxConsole.event(
                "未捕获异常 [" + SandboxProcess.name(this) + "/" + thread.name + "]: " +
                    SandboxConsole.stackTrace(error)
            )
            previous?.uncaughtException(thread, error)
        }
    }
}
