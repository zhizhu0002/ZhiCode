package com.zhizhu.zhicode.compose

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.zhizhu.zhicode.compose.model.ThemeMode
import com.zhizhu.zhicode.compose.state.WorkspaceViewModel
import com.zhizhu.zhicode.compose.state.WorkspaceViewModelFactory
import com.zhizhu.zhicode.compose.ui.ZhiCodeApp
import com.termux.app.zhicode.tools.AndroidIntentBridge

/**
 * 主界面宿主。
 *
 * ViewModel 在 **Activity 这一层**创建并显式传给 [ZhiCodeApp]（而不是在 Composable 里
 * 用默认参数创建）。原因：`onResume` 需要访问它——从系统「安装未知应用」权限页返回后，
 * 要继续那个被挂起的 APK 安装流程，并把结果写回对话流。
 *
 * 两者拿到的是**同一个实例**：`viewModel(factory = …)` 与 `by viewModels { … }`
 * 都以本 Activity 作为 `ViewModelStoreOwner`，只是入口不同。
 *
 * ## 状态栏（用户：「把手机状态栏显示出来，每次打开软件上面黑乎乎的很难受」）
 *
 * 症状的来源是**清单里的主题**：`Theme.Material.NoActionBar.Fullscreen` 会把状态栏
 * 整个藏掉，屏幕最上方就留一条黑边。这里改成显示状态栏 + edge-to-edge：
 * 状态栏透明、由 Compose 侧的 `statusBarsPadding` 让出高度，
 * 图标明暗跟随应用主题（浅色主题下白图标在白底上看不见）。
 */
class MainActivity : ComponentActivity() {

    private val viewModel: WorkspaceViewModel by viewModels {
        WorkspaceViewModelFactory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // edge-to-edge 必须在 super.onCreate **之前**调用：它设置的是窗口 decor 的
        // 布局策略，晚于内容视图创建就只在下一帧生效（会看到一次状态栏闪动）。
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        applyStatusBarAppearance()
        setContent { ZhiCodeApp(viewModel) }
    }

    override fun onResume() {
        super.onResume()
        // 主题可能在别处被改过（设置页 / 顶栏的循环按钮），回到前台时对齐一次。
        applyStatusBarAppearance()
        // APK 安装权限被拒绝时，AndroidIntentBridge 会把安装 Intent 暂存起来并跳去系统设置页。
        // 用户授权后回到本页面，必须在这里把流程接上——没有这一步，
        // 「授予权限」之后安装就永远停在半路，用户只能再让 Agent 重做一遍。
        // 返回 null 表示当前没有待续的安装（绝大多数情况），此时什么都不做。
        val pending = runCatching { AndroidIntentBridge.resumePendingApkInstall(this) }
            .getOrElse { error ->
                viewModel.reportExternalEvent("继续安装失败", error.message ?: "未知原因")
                return
            } ?: return
        viewModel.reportExternalEvent(
            if (pending.isError) "继续安装失败" else "安装 APK",
            pending.content,
        )
    }

    /**
     * 状态栏：透明底色 + 图标明暗跟随应用主题。
     *
     * 「跟随应用主题」而不是「跟随系统」是有意的：本应用允许在设置里独立选浅色/深色
     * （`ThemeMode`），选浅色而系统是深色时，状态栏图标必须是**深色**的，
     * 否则白图标压在白背板上完全看不见。所以这里读的是应用自己的主题状态。
     */
    private fun applyStatusBarAppearance() {
        val dark = when (viewModel.state.value.themeMode) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.SYSTEM ->
                (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        // 背板由 Compose 画（ZhiColors.backdrop()），窗口这一层保持透明，
        // 状态栏区域才不会有一条与背板不同的色带。
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
            // 只改图标明暗，**不**隐藏状态栏：用户明确要求「把状态栏显示出来」。
            // 这一行同时也把上一版本（Fullscreen 主题留下的）隐藏状态复位。
            show(WindowInsetsControllerCompat.BEHAVIOR_DEFAULT)
        }
    }
}
