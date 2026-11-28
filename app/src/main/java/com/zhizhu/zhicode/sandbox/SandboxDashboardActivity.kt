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
import com.zhizhu.zhicode.compose.ui.pages.miuix.SandboxBoardPage

class SandboxDashboardActivity : ComponentActivity() {

    companion object {
        /** 从悬浮条 / 侧栏打开沙盒页。 */
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
