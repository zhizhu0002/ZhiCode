package com.zhizhu.zhicode.compose.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import android.app.Application
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
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

    // 玻璃对象分两层，因为捕获节点不能包含自己：
    //  · glassMain —— 捕获「整个工作区」；用于顶栏 / 侧栏抽屉 / 弹窗。
    //  · glassChat —— 捕获「对话列表」；用于底部悬浮的任务卡与输入器。
    //    这两层必须分开：输入器就在对话区里面，若共用整屏捕获，它会把自己
    //    录进背景里，模糊时出现自我叠影。
    val glassMain = rememberGlass()
    val glass = rememberGlass()

    // UI 重构（S1/S2）：顶栏迁入官方 `topBar` 槽位（与 Miuix example 的
    // BlurredBar + TopAppBar 同一结构）。此前顶栏是 Scaffold 内容里手排的
    // 悬浮 Box 层叠 —— 这让「+」下拉菜单等 Overlay 弹层的锚点坐标
    // （调用层的 positionInWindow）与根 MiuixPopupHost 的窗口原点不一致，
    // 菜单整体飞到锚点上方。迁入槽位后触发器与 PopupHost 同处 Scaffold
    // 根坐标系，弹出位置贴回锚点；手排 z 序与 TopBarInset 手工留白一并删除。
    // 抽屉必须画在 Scaffold **之外**：Miuix Scaffold 的绘制顺序是
    // bodyContent → topBar → popup，放在内容里的抽屉永远被顶栏压住。
    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            if (wide) {
                // 宽屏：侧栏常驻，顶栏只覆盖右侧内容区 —— 由 WorkspaceLayouts
                // 里的 WideWorkspace 自己排侧栏 + 分隔线 + 顶栏，这里不重复挂。
            } else {
                ZhiTopBar(
                    state = state,
                    wide = false,
                    glass = glassMain,
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
                    tabs = WorkspaceTab.entries,
                    onSelectTab = viewModel::selectTab,
                )
            }
        },
    ) { padding ->
        // 顶栏 blur 需要内容从它**底下滚过**才有东西可采样：
        // 去掉 padding.top，让内容 Box 从 y=0 铺满；各面板自己用
        // TopBarTotalInset 在内容头部留白（见 WorkspaceLayouts）。
        Box(
            modifier = Modifier.fillMaxSize().padding(
                bottom = padding.calculateBottomPadding(),
            ),
        ) {
            // 整块工作区先录进 glassMain 的背景（抽屉/弹窗的模糊来源）
            Box(modifier = Modifier.fillMaxSize().then(glassMain.capture(Modifier))) {
                if (wide) {
                    // 宽屏的顶栏在右侧内容区内部（与常驻侧栏并列），见 WideWorkspace
                    WideWorkspace(
                        state = state,
                        viewModel = viewModel,
                        isDark = isDark,
                        glass = glass,
                        glassMain = glassMain,
                    )
                } else {
                    CompactWorkspace(state = state, viewModel = viewModel, isDark = isDark, glass = glass)
                }
            }


            // ---- Overlay 系列 ----
            // 弹窗挂载与模态模糊背景在 OverlayHost.kt。
            // 必须仍处于 Scaffold 内容里才能找到 popupHost。
            ZhiOverlayHost(state = state, viewModel = viewModel, glassMain = glassMain)
        }
    }

    // ---- 侧边栏抽屉（窄屏，画在 Scaffold 之上）----
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
    }
}
