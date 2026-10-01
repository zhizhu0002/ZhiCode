package com.zhizhu.zhicode.compose.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import android.app.Application
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhizhu.zhicode.compose.theme.ZhiColors
import com.zhizhu.zhicode.compose.theme.LocalZhiDark
import com.zhizhu.zhicode.compose.theme.zhiTextStyles
import com.zhizhu.zhicode.compose.model.ThemeMode
import com.zhizhu.zhicode.compose.model.WorkspaceTab
import com.zhizhu.zhicode.compose.model.WorkspaceUiState
import com.zhizhu.zhicode.compose.state.WorkspaceViewModel
import com.zhizhu.zhicode.compose.state.WorkspaceViewModelFactory
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * 取（或创建）主 ViewModel。
 *
 * 不能用默认的 `viewModel()`：反射工厂需要精确的 `(Application)` 构造器，而
 * `WorkspaceViewModel` 的构造器带默认参数，Kotlin 不会生成那个重载，会直接抛
 * `NoSuchMethodException` 并让界面白屏。详见 [WorkspaceViewModelFactory]。
 *
 * 工厂里再注入 `WorkspaceRepository`。注意它现在只剩「终端占位横幅」一项：
 * 对话流、工具执行、会话文件都已由真实引擎负责，不再是模拟实现。
 */
@Composable
private fun rememberWorkspaceViewModel(): WorkspaceViewModel {
    val application = LocalContext.current.applicationContext as Application
    val factory = remember(application) { WorkspaceViewModelFactory(application) }
    return viewModel(factory = factory)
}

/** 应用入口：主题 + 主界面。 */
@Composable
fun ZhiCodeApp(viewModel: WorkspaceViewModel = rememberWorkspaceViewModel()) {
    val state by viewModel.state.collectAsState()

    val systemDark = isSystemInDarkTheme()
    val isDark = when (state.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemDark
    }
    val colors = if (isDark) darkColorScheme() else lightColorScheme()

    // LocalZhiDark 必须**先**提供，之后才能求任何 ZhiColors 层级色：
    // 这些色函数靠 LocalZhiDark 判断深浅，读早了会拿到默认值 false（浅色），
    // 于是深色模式下背板被设成浅灰、弹窗整片发白。
    CompositionLocalProvider(LocalZhiDark provides isDark) {
        // 主背板 = 灰（深色 #242424 / 浅色 #EDEDED），侧栏与面板则用纯黑/纯白。
        // 两者是互换过的层级：背板退后，板块站出来。
        // 之所以要覆盖主题的 background：Miuix 浅色方案里 background / surface /
        // surfaceContainer 都是 #FFFFFF，纯白铺满后板块完全分不出来。
        val appColors = colors.copy(background = ZhiColors.backdrop())
        // textStyles 必须在这里给：Miuix 组件读的是主题样式（TextField→main、
        // Button→button、BasicComponent→headline1/body2、SmallTitle→subtitle），
        // 逐个传 fontSize 够不到它们。字阶与依据见 theme/ZhiTextStyles.kt。
        MiuixTheme(colors = appColors, textStyles = zhiTextStyles()) {
            ZhiCodeScreen(state = state, viewModel = viewModel, isDark = isDark)
        }
    }
}

@Composable
private fun ZhiCodeScreen(
    state: WorkspaceUiState,
    viewModel: WorkspaceViewModel,
    isDark: Boolean,
) {
    val configuration = LocalConfiguration.current
    val wide = configuration.screenWidthDp >= 600

    // 提示条已移除：Snackbar 会遮挡底部输入器，且本工程的 message 基本都是
    // 一次性操作反馈（「已复制到剪贴板」「配置已保存」这类），直接静默即可。
    // 若将来需要反馈，改在 Composer 上方做一条内联提示，不要用悬浮 Snackbar。

    // 玻璃对象分两层，因为捕获节点不能包含自己：
    //  · glassMain —— 捕获「整个工作区」；用于顶栏 / 侧栏抽屉 / 弹窗。
    //  · glassChat —— 捕获「对话列表」；用于底部悬浮的任务卡与输入器。
    //    这两层必须分开：输入器就在对话区里面，若共用整屏捕获，它会把自己
    //    录进背景里，模糊时出现自我叠影。
    val glassMain = rememberGlass()
    val glass = rememberGlass()

    Scaffold(
        // 顶栏不再交给 Scaffold 的 topBar 槽位，而是作为**悬浮层**画在内容之上
        // （见下方 Box）。这样它才能像玻璃一样压住滚动内容做背景虚化；
        // 放进 topBar 槽位只会把它挤到内容外面，没有东西可模糊。
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            // 整块工作区先录进 glassMain 的背景（顶栏/抽屉/弹窗的模糊来源）
            Box(modifier = Modifier.fillMaxSize().then(glassMain.capture(Modifier))) {
                if (wide) {
                    WideWorkspace(state = state, viewModel = viewModel, isDark = isDark, glass = glass)
                } else {
                    CompactWorkspace(state = state, viewModel = viewModel, isDark = isDark, glass = glass)
                }
            }

            // ---- 绘制顺序说明 ----
            // Compose 没有负 z-index：**后写的 Composable 画在上面**。
            // 整个屏幕自下而上是：
            //   1. 工作区内容        —— 被 glassMain 捕获，作为模糊的底
            //   2. 悬浮顶栏          —— 盖住内容；窄屏时又要被侧栏抽屉盖住
            //   3. 侧栏抽屉          —— 模态，含遮罩，必须盖住顶栏
            //   4. 弹窗模糊层 + 弹窗 —— 最上层
            // 宽屏的常驻侧栏与顶栏并列不重叠，靠顶栏的 start 偏移避让。

            // ---- 悬浮顶栏（玻璃效果）----
            // 画在内容之上，才能把滚动到它下面的对话模糊掉；
            // 又必须排在弹窗的模糊背景之前，否则弹窗打开时它不会跟着糊掉。
            ZhiTopBar(
                state = state,
                wide = wide,
                glass = glassMain,
                // 宽屏时从侧栏右边开始，否则整宽的悬浮顶栏会盖住侧栏顶部。
                //
                // 注意**不能**写成 `align(TopCenter) + padding(start = X)`：
                // 那样 padding 会先给元素加宽 X 再整体居中，实际只右移 X/2。
                // 用 TopStart + fillMaxWidth + padding(start = X) 才是从 X 铺到右边缘。
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(start = if (wide) SidebarWidth + DividerWidth else 0.dp),
                onOpenSidebar = viewModel::openSidebar,
                onContextClick = {
                    viewModel.onComposerChange("/usage")
                    viewModel.send()
                },
                onCycleTheme = viewModel::cycleThemeMode,
                onFloatingBall = {
                    viewModel.onComposerChange("/canvas")
                    viewModel.send()
                },
                onSettings = viewModel::openSettings,
                // 窄屏：按键组归顺到头部（原先它被悬浮顶栏遮住）
                tabs = if (wide) null else WorkspaceTab.entries,
                onSelectTab = viewModel::selectTab,
            )

            // ---- 侧边栏抽屉（窄屏，替代原来的底部弹层）----
            // 排在顶栏之后 = 画在顶栏之上。抽屉是模态的：它的遮罩本来就应该连顶栏
            // 一起压暗，面板内容也不必再为顶栏留白（不必再 padding）。
            if (!wide) {
                ZhiSideDrawer(
                    open = state.sidebarOpen,
                    onClose = viewModel::closeSidebar,
                    width = (configuration.screenWidthDp * 0.82f).dp.coerceAtMost(320.dp),
                    glass = glassMain,
                ) {
                    ZhiSidebarHost(
                        state = state,
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxHeight(),
                    )
                }
            }

            // ---- Overlay 系列 ----
            // 弹窗挂载与模态模糊背景在 OverlayHost.kt（从本文件原地拆出）。
            // 它必须仍处于 Scaffold 内容里才能找到 popupHost；绘制顺序上排在
            // 侧栏抽屉之后 = 画在最上层。
            ZhiOverlayHost(state = state, viewModel = viewModel, glassMain = glassMain)
        }
    }
}
