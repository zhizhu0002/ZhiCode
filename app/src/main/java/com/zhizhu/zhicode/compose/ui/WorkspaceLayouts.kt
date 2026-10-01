package com.zhizhu.zhicode.compose.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.model.WorkspaceTab
import com.zhizhu.zhicode.compose.model.WorkspaceUiState
import com.zhizhu.zhicode.compose.state.WorkspaceViewModel
import com.zhizhu.zhicode.compose.ui.panes.FilesPane
import com.zhizhu.zhicode.compose.ui.panes.TerminalPane
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarDisplayMode
import top.yukonga.miuix.kmp.basic.NavigationBarItem

/** （以下内容从 `AppScaffold.kt` 原地拆出，注释逐字未改。） */

/** 宽屏：侧栏常驻 + 对话主栏 + 工作区副栏。宽屏顶栏在右侧内容区顶部（S1 重构）。 */
@Composable
internal fun WideWorkspace(
    state: WorkspaceUiState,
    viewModel: WorkspaceViewModel,
    isDark: Boolean,
    glass: Glass,
    glassMain: Glass,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        // 侧栏保持**满高**：宽屏顶栏现在位于右侧内容区内部（Column 顶部），
        // 不再悬浮盖住侧栏，所以侧栏不需要让位。
        ZhiSidebarHost(
            state = state,
            viewModel = viewModel,
            modifier = Modifier.width(SidebarWidth),
        )
        ZhiVerticalDivider()
        Column(modifier = Modifier.weight(56f)) {
            // 宽屏顶栏（含玻璃模糊）：排在内容 Column 顶部，占布局高度而非悬浮。
            // 触发器从此处于 Scaffold 根坐标系，「+」菜单等 Overlay 弹层锚点正确。
            ZhiTopBar(
                state = state,
                wide = true,
                glass = glassMain,
                onOpenSidebar = viewModel::openSidebar,
                onContextClick = {
                    viewModel.onComposerChange("/usage")
                    viewModel.send()
                },
                onSettings = viewModel::openSettings,
            )
            // 输入器现在由 ChatArea 以悬浮层形式托管
            ChatArea(state = state, viewModel = viewModel, wide = true, modifier = Modifier.weight(1f), glass = glass)
        }
        ZhiVerticalDivider()
        Column(modifier = Modifier.weight(44f)) {
            val secondary = WorkspaceTab.entries.filter { it != WorkspaceTab.CHAT }
            // 顶栏不再悬浮覆盖副栏，按键组不再需要躲开头部
            Column {
                // ⚠️ Tab 行在**下边**（与窄屏的底部导航栏一致）。
                // 宽屏这里仍用标签条而不是 `NavigationBar`：`NavigationBar` 是"贴屏幕底部"
                // 的组件，放进中栏既不是屏幕底部、又会被 Scaffold 的 bottomBar 横跨整屏
                // 压到满高的常驻侧栏下面。所以保持标签条，只把它挪到底部。
                PaneHost(
                    state = state,
                    viewModel = viewModel,
                    isDark = isDark,
                    glass = glass,
                    modifier = Modifier.weight(1f),
                )
                WorkspaceTabs(
                    tabs = secondary,
                    selected = state.tab,
                    onSelect = viewModel::selectTab,
                )
            }
        }
    }
}

/** 宽屏侧栏宽度。 */
internal val SidebarWidth = 258.dp

/** Miuix 纵向分隔线的宽度；顶栏偏移要把它算进去。 */
internal val DividerWidth = 1.dp

/** 窄屏：底部 Tab 切换，一次只显示一个面板。 */
@Composable
internal fun CompactWorkspace(
    state: WorkspaceUiState,
    viewModel: WorkspaceViewModel,
    isDark: Boolean,
    glass: Glass,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 面板容器**不**留顶栏高度：blur 顶栏要内容从它底下滚过才有东西可采样。
        // 各面板自己处理首行可见位置 —— 对话列表用 topInset（contentPadding，
        // 滚动时消息穿过 blur 区），其余面板直接 padding 顶开（无需滚过顶栏）。
        // 面板切换动画：淡入淡出 + 轻微横向位移
        AnimatedContent(
            targetState = state.tab,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                val offset = if (forward) 1 else -1
                (slideInHorizontally(ZhiMotion.enterSpec) {
                    offset * it / 8
                } + fadeIn(ZhiMotion.fadeInSpec)) togetherWith
                    (slideOutHorizontally(ZhiMotion.exitSpec) { -offset * it / 8 } +
                        fadeOut(ZhiMotion.fadeOutSpec))
            },
            modifier = Modifier.weight(1f),
            label = "workspacePane",
        ) { tab ->
            if (tab == WorkspaceTab.CHAT) {
                // 对话面板的留白由 ChatList 的 topInset 负责：它需要能滚到悬浮头部
                // 下面被模糊。所以这里不额外顶开，避免双重留白。
                ChatArea(
                    state = state,
                    viewModel = viewModel,
                    wide = false,
                    modifier = Modifier.fillMaxSize(),
                    glass = glass,
                )
            } else {
                // 变更 / 终端 / 文件三个面板：顶栏已在 Scaffold topBar 槽位占布局
                // 高度（S1 重构），不再悬浮覆盖，面板不需要手工让位。
                PaneHost(
                    state = state,
                    viewModel = viewModel,
                    isDark = isDark,
                    glass = glass,
                    tabOverride = tab,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** 工作区 Tab 行高度 = 官方 `TabRowDefaults.TabRowWithContourHeight`（45dp）。 */
internal val WorkspaceTabRowHeight = 45.dp

/**
 * 窄屏底部的工作区导航栏。
 *
 * ## 为什么是 Miuix `NavigationBar` 而不是把顶部那条 Tab 行搬下来
 *
 * 这两个组件在 Miuix 里是**分开设计的**：`TabRow` / `TabRowWithContour` 是"内容分类"用的
 * 横向标签条（通常放内容上方），`NavigationBar` 才是"固定在屏幕底部"的导航
 * （官方文档原话：used to create navigation menus fixed at the bottom of applications，
 * 支持 2~5 项、自带窗口 inset 处理）。把分类标签条硬挪到底部，位置对了但语义与
 * 默认尺寸都不是为底部准备的，那才是"不原生"。
 *
 * ## 为什么用默认配色
 *
 * 0.9.4 的 `NavigationBarDefaults` **没有** `navigationBarItemColors()`（更新版本的文档里有，
 * 这个版本还没有）。所以这里不传颜色，直接用官方默认 —— 也正好符合"保持 Miuix 原生"。
 *
 * [NavigationBarDisplayMode.IconAndText]：底栏有图标才能一眼认出（对话/终端/文件三个图标
 * 在 `iconForTab` 里已有）。官方还支持 `IconOnly` 与 `IconWithSelectedLabel`，
 * 想换成那两种只改这一个参数。
 */
@Composable
internal fun WorkspaceNavigationBar(
    selected: WorkspaceTab,
    onSelect: (WorkspaceTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier,
        mode = NavigationBarDisplayMode.IconAndText,
    ) {
        WorkspaceTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = iconForTab(tab),
                label = tab.label,
            )
        }
    }
}

/**
 * 工作区 Tab 行。转发到 [ZhiSegmentedTabs]（Miuix `TabRowWithContour`，即带轮廓变体）。
 *
 * `matchWidth = true`：4 项等分填满整行（S3 重构：等分上限改为官方
 * `TabRowDefaults.TabRowWithContourMaxWidth` 的三倍即 252dp——4 项等分在
 * 450dpi 手机上约 100dp/项，252dp 足够表达"等分"语义又远小于原来的 1000dp）。
 *
 * ⚠️ 现在只给**宽屏**的右栏用（窄屏改用底部 [WorkspaceNavigationBar]）。不要顺手删掉
 * 它以为没人用 —— 宽屏是"侧栏常驻 + 双栏"范式，底栏该由整个屏幕共用一个，
 * 而那会横跨到常驻侧栏下面，所以宽屏保持用这条标签条。
 */
@Composable
internal fun WorkspaceTabs(
    tabs: List<WorkspaceTab>,
    selected: WorkspaceTab,
    onSelect: (WorkspaceTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth().height(WorkspaceTabRowHeight),
        contentAlignment = Alignment.Center,
    ) {
        ZhiSegmentedTabs(
            tabs = tabs.map { it.label },
            selectedIndex = tabs.indexOf(selected).coerceAtLeast(0),
            onSelect = { index -> tabs.getOrNull(index)?.let(onSelect) },
            modifier = Modifier.fillMaxWidth(),
            matchWidth = true,
        )
    }
}

/** 工作区标签 → 图标。顶栏（TopBar.kt）与宽屏按键组共用，所以是 internal。 */
internal fun iconForTab(tab: WorkspaceTab) = when (tab) {
    WorkspaceTab.CHAT -> ZhiIcons.chat
    WorkspaceTab.TERMINAL -> ZhiIcons.terminal
    WorkspaceTab.FILES -> ZhiIcons.files
}

/** 顶栏标题行高度（窄屏用 48dp，见 `ZhiTopBar` 里的 height）。 */
internal val TopBarTitleRowHeight = 48.dp

/**
 * 顶栏的占位高度。
 *
 * 这三个数字必须和 `ZhiTopBar` 里的实际尺寸严格对齐，否则面板的首行
 * （尤其是标题栏右侧的动作图标）会被悬浮头部压掉一半。
 */
// SmallTopAppBar 官方 CollapsedHeight = 52dp。
// S1 之后顶栏占布局高度、内容不再重叠 —— 但顶栏 blur 需要内容从底下滚过，
// 所以 Scaffold 内容层不再吃 padding.top，各面板用这个常量自己留出可见区起点。
//
// ⚠️ 原先还有第二个常量 `TopBarInsetWithTabs = 52 + 45 + 12dp`（窄屏把 Tab 行归顺进顶栏时）。
// Tab 行挪到底部之后它没有正确取值了，已删除 —— **不要**为了让某处多留一点空白而把它加回来，
// 那会让所有面板顶部多出 57dp。
internal val TopBarInset = 52.dp

@Composable
private fun PaneHost(
    state: WorkspaceUiState,
    viewModel: WorkspaceViewModel,
    isDark: Boolean,
    glass: Glass,
    modifier: Modifier = Modifier,
    tabOverride: WorkspaceTab? = null,
) {
    when (val tab = tabOverride ?: state.tab) {
        WorkspaceTab.TERMINAL -> TerminalPane(
            lines = state.terminalLines,
            projectName = state.projectName,
            // 内置 Termux 环境是否就绪决定这个是"真终端"还是"只读占位"。
            // runtimeReady 是真实探测（见 EnvDoctor / RuntimeInstaller），不是常量。
            runtimeReady = state.runtimeReady,
            workingDirectory = state.projectPath,
            // 终端实例由 ViewModel 持有，**不能**在这里 remember：
            // PaneHost 用 when(tab) 切面板，切走会让这段 Composable 离开组合，
            // 实例若建在这里就会连会话一起被销毁（见 TerminalHolder 的注释）。
            terminalHolder = { ctx -> viewModel.terminalPane(ctx) },
            modifier = modifier,
        )
        WorkspaceTab.FILES -> FilesPane(
            filePath = state.filePath,
            entries = state.fileEntries,
            openFile = state.openFile,
            emptyNote = state.fileNote,
            onOpen = viewModel::openFile,
            onUp = viewModel::navigateUp,
            onNavigate = viewModel::navigateTo,
            onCloseFile = viewModel::closeFile,
            modifier = modifier,
        )
        WorkspaceTab.CHAT -> ChatArea(state = state, viewModel = viewModel, wide = false, modifier = modifier, glass = glass)
    }
}

@Composable
internal fun ZhiSidebarHost(
    state: WorkspaceUiState,
    viewModel: WorkspaceViewModel,
    modifier: Modifier = Modifier,
) {
    ZhiSidebar(
        state = state,
        onNewSession = viewModel::newSession,
        onProjectPath = { viewModel.onComposerChange("/status"); viewModel.send() },
        onOpenSession = viewModel::openSession,
        onSessionActions = viewModel::showSessionActions,
        onDeleteSession = { viewModel.deleteSession(it.id) },
        // 长按会话的动作菜单：由侧栏里那一条会话自己渲染（从手指位置长出来）
        anchoredMenu = { anchorId, fingerOffset ->
            ZhiAnchoredMenuHost(state, viewModel, anchorId, fingerOffset)
        },
        onSandbox = { viewModel.openSandbox() },
        // 「运行环境」行现在是真实探测结果 + 真实的安装/自检窗口，
        // 不再是把 /doctor 当普通消息发出去（那样只会得到一句 Mock 回复）。
        onRuntime = viewModel::openEnvironment,
        onSettings = viewModel::openSettings,
        modifier = modifier,
    )
}
