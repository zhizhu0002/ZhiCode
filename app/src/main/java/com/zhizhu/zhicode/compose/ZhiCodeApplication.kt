package com.zhizhu.zhicode.compose

import android.app.Application
import android.content.Context
import android.util.Log
import com.termux.app.zhicode.api.zcode.ZcodeDeviceMid
import com.termux.shared.termux.TermuxConstants
import com.zhizhu.zhicode.sandbox.SandboxAgentBridge
import com.zhizhu.zhicode.sandbox.SandboxControlMailbox
import com.zhizhu.zhicode.sandbox.SandboxDebugLog
import com.zhizhu.zhicode.sandbox.SandboxProcessRole
import com.zhizhu.zhicode.sandbox.ZhiSandboxEngine
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ZhiCodeApplication : Application() {

    /** 本进程是不是沙盒进程。 */
    private var sandboxProcess = false

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)

        // 必须是第一个动作：后面所有路径都依赖它
        TermuxConstants.configure(base)

        // ZCode 的设备标识要在任何请求发出之前就位。晚一步的话 provider 会回退到
        // 进程内的临时值 —— 那个值每次启动都不同，服务端认不出是同一台设备，
        // 额度与套餐模型就会时有时无。
        ZcodeDeviceMid.install(base)

        // 沙盒调试日志要在 ContentProvider 创建之前就位：
        // `SandboxControlProvider.onCreate` 会立刻写一条事件，
        // 那个时刻晚于 attachBaseContext、早于 Application.onCreate。
        SandboxDebugLog.init(base)

        // 启动期判一次「本应用是不是跑在别的沙箱里」。
        // 界面据此可以直接给出结论，而不是发一次 IPC 白等 30 秒超时 ——
        // 嵌套环境下引擎结构性不可用，见 ZhiSandboxEngine.isNestedRuntime。
        ZhiSandboxEngine.noteEnvironment(base)

        installCrashLogger()

        sandboxProcess = SandboxProcessRole.shouldAttachBlackBox(base)
        if (sandboxProcess) {
            ZhiSandboxEngine.attach(base)
        }
    }

    override fun onCreate() {
        super.onCreate()

        if (!sandboxProcess) return

        // 先注册广播桥再 create：guest Activity 一旦 resume 就要能接控制指令，
        // 而 create 里可能立刻拉起应用。
        SandboxAgentBridge.register(this)
        // 引擎控制信箱。与 provider 是**同一个 dispatch** 的两个入口 ——
        // 沙箱内运行本应用时 guest 找不到宿主 provider，只能走这条。
        SandboxControlMailbox.register(this)
        ZhiSandboxEngine.create()
    }

    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val trace = StringWriter().also { writer ->
                    PrintWriter(writer).use { error.printStackTrace(it) }
                }.toString()
                val stamp = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
                val line = "$stamp [${thread.name}]\n$trace\n\n"
                Log.e(TAG, "未捕获异常 [${thread.name}]", error)
                File(filesDir, CRASH_LOG).appendText(line)
            }
            // 交回上一个处理器：它是系统的，负责真正把进程结束掉并弹系统对话框。
            previous?.uncaughtException(thread, error)
        }
    }

    private companion object {
        const val TAG = "ZhiCode"
        const val CRASH_LOG = "crash.log"
    }
}
