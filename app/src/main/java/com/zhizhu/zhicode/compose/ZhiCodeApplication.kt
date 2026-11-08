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

/**
 * 应用入口。
 *
 * 这里做且只做四件必须在所有其他类之前完成的事：
 *
 * 1. **配置 Termux 路径**。`TermuxConstants` 是全部路径的单一来源，必须在任何
 *    引擎/终端类被加载前用本应用自己的包名与私有目录初始化。晚一步就会有人
 *    读到兜底的默认路径。
 * 2. **ZCode 设备标识就位**。见 [ZcodeDeviceMid] 的说明。
 * 3. **安装崩溃日志**。放在 `attachBaseContext` 里，早于 `ContentProvider` 创建，
 *    否则启动期崩溃抓不到。
 * 4. **沙盒进程的引擎初始化**，见下。
 *
 * ## 沙盒的进程边界（这条性质要保住）
 *
 * BlackBox 虚拟运行时**只在沙盒相关进程里加载** —— `:iqsandbox` / `:black` /
 * `:p0..:p49`，判定全部收在 [SandboxProcessRole.shouldAttachBlackBox]。
 * **主进程一个 BlackBox 的类都不碰**，所以主界面启动速度与稳定性不受 hook
 * 运行时影响。任何"顺手在主进程也初始化一下"的改动都会毁掉这条性质，
 * 而症状很隐蔽（启动慢几十毫秒、偶发 crash），不会有人联想到沙盒。
 */
class ZhiCodeApplication : Application() {

    /**
     * 本进程是不是沙盒进程。
     *
     * <p>在 [attachBaseContext] 里算一次、存起来 —— 后面 `onCreate` 要用它，
     * 而那边再去读 `/proc/self/cmdline` 就多一次 IO，且两次读到的可能不一致
     * （理论上一进程名固定，但没必要冒这个险）。
     */
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

        /*
         * ⚠️ `attach()` 必须在这里，**不能挪到 onCreate**。
         *
         * BlackBox 的入口就叫 `doAttachBaseContext` —— 它的语义是
         * 「在 base context 就绪时接管」，而 `Application.attachBaseContext`
         * 正是那个时刻。放到 `onCreate` 里会晚一步，症状不是崩溃而是
         * **引擎永远停在"等待 Application.onCreate"**，
         * 于是管理页那边 `awaitReady(12000)` 超时、报
         * 「引擎初始化超时（12000ms）」。
         *
         * 这也是原版 IQ-Code-Android 的接线位置（IQCodeApplication.attachBaseContext）。
         *
         * ## 为什么这里**不做**"嵌套时就地承载"
         *
         * 试过：本应用自己跑在别的虚拟运行时里时，让主进程就地 attach 引擎。
         * 实测**直接打不开应用** —— 外层 BlackBox 已经在这个进程里装过一层
         * native hook（`libblackbox.so` 的 ART/JNI/文件系统 hook），
         * 再叠一层会让进程在启动期就死掉，连 Java 异常都来不及抛。
         *
         * 结论：**引擎永远只在子进程里**。嵌套场景下的 IPC 问题另想办法
         * （见 SandboxControlMailbox 的说明），不靠改进程拓扑来解。
         */
        sandboxProcess = SandboxProcessRole.shouldAttachBlackBox(base)
        if (sandboxProcess) {
            ZhiSandboxEngine.attach(base)
        }
    }

    /**
     * 沙盒进程的引擎创建。
     *
     * ## 为什么 attach 在上面、create 在这里
     *
     * `attach` 只是把 `ClientConfiguration`（宿主包名、是否隐藏 Root…）交给
     * `BlackBoxCore`，代价很低，所以要在 `attachBaseContext` 做；
     * 真正的 `doCreate()` 会起服务、扫包、可能触发 hook —— 那必须等
     * `Application.onCreate`（原版在这里踩过：provider 比 onCreate 更早，
     * 在 provider 里 create 会让 Android 15 上的运行时初始化过早）。
     *
     * ## 只有沙盒进程会走到这里
     *
     * [SandboxProcessRole.shouldAttachBlackBox] 只认 `:iqsandbox` / `:black` /
     * `:p0..:p49`。**主进程一个 BlackBox 类都不加载** —— 这是这套设计最重要的性质。
     */
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

    /**
     * 未捕获异常落盘。
     *
     * ## 为什么不用 logcat
     *
     * 原先这里走的是 `SandboxConsole.event()` —— 它把事件写进
     * `filesDir/sandbox/debug/events.log`，外加 `logcat -d` 采样。
     * 那套设施是为沙盒的多进程调试写的（按 pid 分文件、能回答"卡在哪一步"），
     * 沙盒删掉之后它没有存在的理由。
     *
     * 现在直接写 `filesDir/crash.log`：单一追加文件、纯 Java IO、没有线程池也没有
     * 子进程。启动期崩溃的上下文里，**越少的依赖越可能真的写进去** ——
     * 在 `UncaughtExceptionHandler` 里起线程、等活、调 `logcat`，
     * 都可能在"进程马上要被收走"的那一刻写不完。
     */
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
