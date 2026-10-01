package com.zhizhu.zhicode.compose.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.model.WorkspaceTab
import com.zhizhu.zhicode.compose.model.WorkspaceUiState
import com.zhizhu.zhicode.compose.state.WorkspaceViewModel
import com.zhizhu.zhicode.compose.ui.panes.ChangesPane
import com.zhizhu.zhicode.compose.ui.panes.FilesPane
import com.zhizhu.zhicode.compose.ui.panes.TerminalPane

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
                onCycleTheme = viewModel::cycleThemeMode,
                onFloatingBall = {
                    viewModel.onComposerChange("/canvas")
                    viewModel.send()
                },
                onSettings = viewModel::openSettings,
                tabs = null,
            )
            // 输入器现在由 ChatArea 以悬浮层形式托管
            ChatArea(state = state, viewModel = viewModel, wide = true, modifier = Modifier.weight(1f), glass = glass)
        }
        ZhiVerticalDivider()
        Column(modifier = Modifier.weight(44f)) {
            val secondary = WorkspaceTab.entries.filter { it != WorkspaceTab.CHAT }
            // 顶栏不再悬浮覆盖副栏，按键组不再需要躲开头部
            Column {
                WorkspaceTabs(
                    tabs = secondary,
                    selected = state.tab,
                    onSelect = viewModel::selectTab,
                )
            }
            PaneHost(state = state, viewModel = viewModel, isDark = isDark, glass = glass, modifier = Modifier.weight(1f))
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
        // 面板切换动画：淡入淡出 + 轻微横向位移
        AnimatedContent(
            targetState = state.tab,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                val offset = if (forward) 1 else -1
                (slideInHorizontally(tween(ZhiMotion.MEDIUM, easing = EaseOutCubic)) {
                    offset * it / 8
                } + fadeIn(tween(ZhiMotion.MEDIUM))) togetherWith
                    (slideOutHorizontally(tween(ZhiMotion.FAST)) { -offset * it / 8 } +
                        fadeOut(tween(ZhiMotion.FAST)))
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

/** 工作区 Tab 行的总高度。必须 ≥ `TabRowWithContour` 的默认高（45dp），否则轮廓会被裁。 */
internal val WorkspaceTabRowHeight = 45.dp

/**
 * 工作区 Tab 行。转发到 [ZhiSegmentedTabs]（Miuix `TabRowWithContour`，即带轮廓变体）。
 *
 * `matchWidth = true`：4 项等分填满整行，不再受默认 84dp 上限影响。
 *
 * 这里以前是手写的 `ZhiButtonGroup`（等分按键 + 自绘选中胶囊），理由是"TabRow 的轮廓
 * 会向外绘制并盖住相邻内容"。实测确实会溢出，但正确做法不是退回手写，而是
 * **给足高度并裁切外层**：胶囊有地方画，溢出的部分也被裁掉，不会压到下面面板的首行。
 *
 * 代价：Miuix `TabRow` 只接受文本标签，原先每项左侧的图标不再显示。
 */
@Composable
internal fun WorkspaceTabs(
    tabs: List<WorkspaceTab>,
    selected: WorkspaceTab,
    onSelect: (WorkspaceTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth().height(WorkspaceTabRowHeight).clipToBounds(),
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
    WorkspaceTab.CHANGES -> ZhiIcons.changes
    WorkspaceTab.TERMINAL -> ZhiIcons.terminal
    WorkspaceTab.FILES -> ZhiIcons.files
}

/** 顶栏标题行高度（窄屏用 48dp，见 `ZhiTopBar` 里的 height）。 */
internal val TopBarTitleRowHeight = 48.dp

/** 归顺到顶栏的 Tab 行上下留白（`ZhiTopBar` 里 `padding(vertical = 6.dp)` 的两倍）。 */
internal val TopBarTabRowPadding = 12.dp

/**
 * 悬浮头部的占位高度：宽屏只有标题行，窄屏还要加上归顺进来的 Tab 行。
 *
 * 这三个数字必须和 `ZhiTopBar` 里的实际尺寸严格对齐，否则面板的首行
 * （尤其是标题栏右侧的动作图标）会被悬浮头部压掉一半。
 */
internal val TopBarInset = TopBarTitleRowHeight
internal val TopBarInsetWithTabs =
    TopBarTitleRowHeight + WorkspaceTabRowHeight + TopBarTabRowPadding

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
        WorkspaceTab.CHANGES -> ChangesPane(
            diff = state.diff,
            isDark = isDark,
            onRefresh = viewModel::refreshDiff,
            modifier = modifier,
        )
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
        onProjectHistory = { viewModel.onComposerChange("/resume"); viewModel.send() },
        onProjectPath = { viewModel.onComposerChange("/status"); viewModel.send() },
        onOpenSession = viewModel::openSession,
        onSessionActions = viewModel::showSessionActions,
        onDeleteSession = { viewModel.deleteSession(it.id) },
        // 长按会话的动作菜单：由侧栏里那一条会话自己渲染（从手指位置长出来）
        anchoredMenu = { anchorId, fingerOffset ->
            ZhiAnchoredMenuHost(state, viewModel, anchorId, fingerOffset)
        },
        onSkills = viewModel::openSkills,
        onRoleCard = viewModel::openRoleCards,
        onSandbox = { viewModel.openSandbox() },
        // 「运行环境」行现在是真实探测结果 + 真实的安装/自检窗口，
        // 不再是把 /doctor 当普通消息发出去（那样只会得到一句 Mock 回复）。
        onRuntime = viewModel::openEnvironment,
        onSettings = viewModel::openSettings,
        modifier = modifier,
    )
}
