package com.zhizhu.zhicode.compose

import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.zhizhu.zhicode.compose.state.WorkspaceViewModel
import com.zhizhu.zhicode.compose.state.WorkspaceViewModelFactory
import com.zhizhu.zhicode.compose.theme.ZhiThemeMode
import com.zhizhu.zhicode.compose.ui.ZhiCodeApp
import com.zhizhu.zhicode.compose.ui.debug.ZhiFrameTrace
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

    /**
     * 记录**手指落下**的时刻，用于测真正的「点一下要等多久」。
     *
     * ## 为什么非得在这一层记
     *
     * `ZhiFrameTrace.begin()` 是在各个 `onClick` 里调的，它测的是
     * 「onClick 开始 → 第一帧」。但用户感知的延迟里最难受的一段**发生在 onClick 之前**：
     * 主线程若正在跑一个 458ms 的长帧，这一下触摸就只能排队 —— 系统要等那帧画完
     * 才会把 `ACTION_DOWN/UP` 派发进来。这段排队时间在 onClick 里根本看不见，
     * 于是会出现"我的数据在变好、用户手感依旧很差"。
     *
     * 在这里取 `System.nanoTime()` 就把它接上了：之后 `begin()` 用这个时刻做起点，
     * 报出来的就是**手指落下 → 第一帧**，也就是用户真正经历的那段。
     *
     * 只在 `ACTION_DOWN` 更新：一次点击的"起点"应该是按下去那一刻。
     */
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
     * 退到后台时把去抖窗口里那次设置改动兑现。
     *
     * 用户「点一下开关 / 打完字就直接划掉应用」是很常见的动作，而去抖窗口
     * （[com.zhizhu.zhicode.compose.state.WorkspaceViewModel] 的
     * `SettingsPersistDebounceMs`）还开着时那一次改动就没了。
     * `onStop` 是"应用不再可见"的最早可靠时机，在这里补一次同步落盘。
     */
    override fun onStop() {
        super.onStop()
        viewModel.flushSettings()
    }

    /**
     * 状态栏：透明底色 + 图标明暗跟随应用主题。
     *
     * 判定与落地都在 [ZhiThemeMode]（那里有为什么「跟随应用主题」而不是「跟随系统」
     * 的推导，以及为什么不能各 Activity 各写一份）。这里只负责「什么时候调用」。
     */
    private fun applyStatusBarAppearance() {
        ZhiThemeMode.appliedTo(
            this,
            ZhiThemeMode.resolve(
                viewModel.state.value.themeMode,
                ZhiThemeMode.systemDark(this),
            ),
        )
    }
}
