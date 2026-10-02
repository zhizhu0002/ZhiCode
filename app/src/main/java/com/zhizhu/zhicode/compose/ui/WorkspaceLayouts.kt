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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.model.WorkspaceTab
import com.zhizhu.zhicode.compose.model.WorkspaceUiState
import com.zhizhu.zhicode.compose.state.WorkspaceViewModel
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
            // 副栏的面板同样会因为 `PaneHost` 里的 when(tab) 被销毁，滚动位置照丢，
            // 所以这里也包一层。理由与 CompactWorkspace 里那段注释相同。
            val secondaryPaneStateHolder = rememberSaveableStateHolder()
            secondaryPaneStateHolder.SaveableStateProvider(state.tab.name) {
                PaneHost(state = state, viewModel = viewModel, isDark = isDark, glass = glass, modifier = Modifier.weight(1f))
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
        //
        // ## 为什么要 SaveableStateHolder（切 tab 不再回到顶部）
        //
        // 下面的 `AnimatedContent` 在切换时会**销毁**离场的那个面板 —— 这是它的正常工作方式。
        // 而三个面板的滚动位置都是 `rememberLazyListState()`（内部就是 `rememberSaveable`），
        // 组合被销毁，位置就跟着没了，于是每次切回来都停在最上面。
        // 用户的原话：「每次切换对话那一栏的 TAB，对话每次都会回到最上层」。
        //
        // `SaveableStateHolder` 正是为这个场景存在的：面板离开组合时，它把子树里所有
        // `rememberSaveable` 的值存下来，回来时按 key 还回去。所以**不用改任何面板**，
        // 在这外面加一层就够了（对话、终端、文件三个一起受益）。
        //
        // ⚠️ holder 必须在 `AnimatedContent` **外面**取：放在里面就跟着一起被销毁了，
        // 等于没做。
        val paneStateHolder = rememberSaveableStateHolder()

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
            // key 用 tab 名：每个 tab 各自记一份滚动位置。
            paneStateHolder.SaveableStateProvider(tab.name) {
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
                // ⚠️ 这三个面板（变更 / 终端 / 文件）必须**自己顶开顶栏高度**。
                //
                // 规则：Scaffold 的 content lambda **有意丢掉了 `padding.top`**
                // （为了让顶栏 blur 有内容可采样），所以内容实际从 y=0 铺满 ——
                // 任何不自己顶开的面板，头部都会被顶栏盖住：面板标题、向上按钮、
                // 面包屑连同第一行一起消失。用户截图里"上面有一行被裁掉"就是这个。
                //
                // 对话面板**不**在这里补：它的留白由 ChatList 的 topInset 负责，
                // 因为它需要能滚到顶栏下面被模糊。
                PaneHost(
                    state = state,
                    viewModel = viewModel,
                    isDark = isDark,
                    glass = glass,
                    tabOverride = tab,
                    modifier = Modifier.fillMaxSize().padding(top = TopBarInsetWithTabs),
                )
            }
            }
        }
    }
}

/** 工作区 Tab 行高度 = 官方 `TabRowDefaults.TabRowWithContourHeight`（45dp）。 */
internal val WorkspaceTabRowHeight = 45.dp

/**
 * 工作区 Tab 行。转发到 [ZhiSegmentedTabs]（Miuix `TabRowWithContour`，即带轮廓变体）。
 *
 * `matchWidth = true`：4 项等分填满整行（S3 重构：等分上限改为官方
 * `TabRowDefaults.TabRowWithContourMaxWidth` 的三倍即 252dp——4 项等分在
 * 450dpi 手机上约 100dp/项，252dp 足够表达"等分"语义又远小于原来的 1000dp）。
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

/** 归顺到顶栏的 Tab 行上下留白（`ZhiTopBar` 里 `padding(vertical = 6.dp)` 的两倍）。 */
internal val TopBarTabRowPadding = 12.dp

/**
 * 悬浮头部的占位高度：宽屏只有标题行，窄屏还要加上归顺进来的 Tab 行。
 *
 * 这三个数字必须和 `ZhiTopBar` 里的实际尺寸严格对齐，否则面板的首行
 * （尤其是标题栏右侧的动作图标）会被悬浮头部压掉一半。
 */
// SmallTopAppBar 官方 CollapsedHeight = 52dp；窄屏 Tab 行在 bottomContent 槽位。
// S1 之后顶栏占布局高度、内容不再重叠 —— 但顶栏 blur 需要内容从底下滚过，
// 所以 Scaffold 内容层不再吃 padding.top，各面板用这两个常量自己留出可见区起点。
internal val TopBarInset = 52.dp
internal val TopBarInsetWithTabs =
    52.dp + WorkspaceTabRowHeight + TopBarTabRowPadding

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
