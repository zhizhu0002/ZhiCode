package com.zhizhu.zhicode.compose

import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.activity.viewModels
import com.zhizhu.zhicode.compose.state.WorkspaceViewModel
import com.zhizhu.zhicode.compose.state.WorkspaceViewModelFactory
import com.zhizhu.zhicode.compose.theme.ZhiThemeMode
import com.zhizhu.zhicode.compose.ui.ImeMotionState
import com.zhizhu.zhicode.compose.ui.LocalImeMotion
import com.zhizhu.zhicode.compose.ui.ZhiCodeApp
import com.zhizhu.zhicode.compose.ui.debug.ZhiFrameTrace
import com.zhizhu.zhicode.sandbox.SandboxThemeBridge
import com.termux.app.zhicode.tools.AndroidIntentBridge

class MainActivity : ComponentActivity() {

    private val imeMotion = ImeMotionState()

    private val viewModel: WorkspaceViewModel by viewModels {
        WorkspaceViewModelFactory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // edge-to-edge 必须在 super.onCreate **之前**调用：它设置的是窗口 decor 的
        // 布局策略，晚于内容视图创建就只在下一帧生效（会看到一次状态栏闪动）。
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) viewModel.resetTabForLaunch()
        applyStatusBarAppearance()
        // DecorView 先拿到启动焦点，避免 Compose 首个可编辑节点被系统当成默认焦点，
        // 从而在用户尚未点击发送框时主动拉起输入法。
        window.decorView.isFocusableInTouchMode = true
        window.decorView.requestFocus()
        window.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN,
        )
        setContent {
            androidx.compose.runtime.CompositionLocalProvider(LocalImeMotion provides imeMotion) {
                ZhiCodeApp(viewModel)
            }
        }
        window.decorView.post { installImeMotionListener() }
    }

    /** 在 DecorView 上接住 IME 动画，避免 ChatArea 自己重复观察窗口 inset。 */
    private fun installImeMotionListener() {
        val root = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            imeMotion.apply(insets)
            insets
        }
        ViewCompat.setWindowInsetsAnimationCallback(
            root,
            object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                override fun onPrepare(animation: WindowInsetsAnimationCompat) {
                    imeMotion.onPrepare(animation)
                }

                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: MutableList<WindowInsetsAnimationCompat>,
                ): WindowInsetsCompat = imeMotion.onProgress(insets)

                override fun onEnd(animation: WindowInsetsAnimationCompat) {
                    imeMotion.onEnd(animation)
                }
            },
        )
        ViewCompat.requestApplyInsets(root)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> ZhiFrameTrace.markTouchDown()
            // 抬起时刻才是"用户完成点击"的那一瞬间：onClick 在抬起后才触发，
            // 所以「抬起 → 页面切完」才是用户说的"点完要等一会儿"。见 markTouchUp。
            MotionEvent.ACTION_UP -> {
                ZhiFrameTrace.markTouchUp()
                ZhiFrameTrace.stamp("up")
            }
            else -> Unit
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onResume() {
        super.onResume()
        // 主题可能在别处被改过（设置页 / 顶栏的循环按钮），回到前台时对齐一次。
        applyStatusBarAppearance()
        /* 保活恢复。 */
        viewModel.resumeKeepAliveIfEnabled()
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

    /** 退到后台时把去抖窗口里那次设置改动兑现。 */
    override fun onStop() {
        super.onStop()
        viewModel.flushSettings()
    }

    private fun applyStatusBarAppearance() {
        val dark = ZhiThemeMode.resolve(
            viewModel.state.value.themeMode,
            ZhiThemeMode.systemDark(this),
        )
        ZhiThemeMode.appliedTo(this, dark)
        /* 顺手把深浅告诉沙箱。 */
        runCatching { SandboxThemeBridge.publishThemeDark(this, dark) }
    }
}
