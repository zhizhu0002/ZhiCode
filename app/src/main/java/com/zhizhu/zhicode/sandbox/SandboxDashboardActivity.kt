package com.zhizhu.zhicode.sandbox

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.zhizhu.zhicode.compose.data.ThemeSettings
import com.zhizhu.zhicode.compose.data.ThemeStore
import com.zhizhu.zhicode.compose.theme.ZhiAppTheme
import com.zhizhu.zhicode.compose.ui.pages.SandboxBoardPage

/**
 * 「沙箱」的宿主 Activity。
 *
 * <h3>为什么是独立 Activity，不是主界面里的一页</h3>
 * 和 `editor/EditorActivity` 同一个理由，只是更硬：沙盒管理页要**启动 guest Activity**，
 * 而 guest 的窗口属于宿主进程之外的 BlackBox 运行时。把它放主界面的 `NavDisplay`
 * 里，会变成「返回栈里既有虚拟应用又有沙盒管理页」这种没人能讲清的层级；
 * 独立 Activity 之后，返回键永远只有一个主人。
 *
 * <h3>这个类刻意很薄</h3>
 * 它只做「窗口设置 → 读主题 → 装页面」。**所有状态与动作在
 * [SandboxBoardPage] 与 `SandboxBoardViewModel` 里** —— 这样真机上的布局问题
 * 不会连带把窗口配置一起改坏。
 *
 * <p>它跑在**主进程**里。BlackBox 本体在 `:iqsandbox` 进程，两边通过
 * [SandboxHostClient]（ContentProvider）与 [SandboxAgentBridge]（广播）通信。
 * 这是刻意的隔离：hook 运行时不能进主进程。
 */
class SandboxDashboardActivity : ComponentActivity() {

    companion object {
        /**
         * 从悬浮条 / 侧栏打开沙盒页。
         *
         * 带 `REORDER_TO_FRONT | SINGLE_TOP`：这一屏在返回栈里最多一份。
         * 悬浮条每次点击都新建一个沙盒页的话，用户按十次返回才能回到主界面。
         */
        fun intent(context: Context): Intent =
            Intent(context, SandboxDashboardActivity::class.java)
                .addFlags(
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // edge-to-edge 必须早于 super.onCreate：它设的是窗口 decor 的布局策略。
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val themeSettings = runCatching { ThemeStore.read(this) }
            .getOrDefault(ThemeSettings())
        setContent {
            ZhiAppTheme(theme = themeSettings) {
                SandboxBoardPage(onExit = { finish() })
            }
        }
    }
}
