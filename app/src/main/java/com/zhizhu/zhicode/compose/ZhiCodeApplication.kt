package com.zhizhu.zhicode.compose

import android.app.Application
import android.content.Context
import android.util.Log
import com.termux.app.zhicode.api.zcode.ZcodeDeviceMid
import com.termux.shared.termux.TermuxConstants
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 应用入口。
 *
 * 这里做且只做三件必须在所有其他类之前完成的事：
 *
 * 1. **配置 Termux 路径**。`TermuxConstants` 是全部路径的单一来源，必须在任何
 *    引擎/终端类被加载前用本应用自己的包名与私有目录初始化。晚一步就会有人
 *    读到兜底的默认路径。
 * 2. **ZCode 设备标识就位**。见 [ZcodeDeviceMid] 的说明。
 * 3. **安装崩溃日志**。放在 `attachBaseContext` 里，早于 `ContentProvider` 创建，
 *    否则启动期崩溃抓不到。
 *
 * ## 这里曾经还有沙盒初始化
 *
 * 原先这个类的绝大部分代码是 BlackBox 沙箱的进程编排：判断本进程是不是
 * `:zhisandbox` / `:black` / `:p0..:p49`，是就 `ZhiSandbox.attach/create`，
 * 主进程还要起一条 Termux 桥（`SandboxShell.start`）给 guest 侧转发工具调用。
 * 沙箱功能已整体移除，这些逻辑连同 `sandbox` 包一起删掉了。
 *
 * 删掉之后有一个**真实的收益**值得记下来：主进程不再加载任何 hook 运行时。
 * 原先 `SandboxProcess.ownsEngine()` 在主进程返回 false 只是"不 attach"，
 * 但相关类仍在 classpath 上、静态初始化仍可能被触发；现在这条路径彻底不存在了。
 */
class ZhiCodeApplication : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)

        // 必须是第一个动作：后面所有路径都依赖它
        TermuxConstants.configure(base)

        // ZCode 的设备标识要在任何请求发出之前就位。晚一步的话 provider 会回退到
        // 进程内的临时值 —— 那个值每次启动都不同，服务端认不出是同一台设备，
        // 额度与套餐模型就会时有时无。
        ZcodeDeviceMid.install(base)

        installCrashLogger()
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
