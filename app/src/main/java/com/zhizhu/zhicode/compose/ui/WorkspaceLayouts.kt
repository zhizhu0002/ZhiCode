package com.zhizhu.zhicode.compose.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.data.TabBarPosition
import com.zhizhu.zhicode.compose.model.WorkspaceTab
import com.zhizhu.zhicode.compose.model.WorkspaceUiState
import com.zhizhu.zhicode.compose.theme.LocalThemeSettings
import com.zhizhu.zhicode.compose.state.WorkspaceViewModel
import com.zhizhu.zhicode.compose.ui.pages.LocalZhiPageSet
import com.zhizhu.zhicode.compose.ui.pages.miuix.ZhiAnchoredMenuHost
import top.yukonga.miuix.kmp.utils.PagerNavigationSpringSpec
import top.yukonga.miuix.kmp.utils.springAnimateToPage
import kotlinx.coroutines.launch

internal val SidebarWidth = 264.dp
internal val DividerWidth = 1.dp

/** 宽屏：侧栏常驻 + 对话主栏 + 工作区副栏。 */
@Composable
internal fun WideWorkspace(
    state: WorkspaceUiState,
    viewModel: WorkspaceViewModel,
    isDark: Boolean,
    glass: Glass,
    glassMain: Glass,
    sidebarContent: @Composable (Modifier) -> Unit = { sidebarModifier ->
        ZhiSidebarHost(state = state, viewModel = viewModel, modifier = sidebarModifier)
    },
) {
    Row(modifier = Modifier.fillMaxSize()) {
        sidebarContent(Modifier.width(SidebarWidth))
        ZhiVerticalDivider()
        Column(modifier = Modifier.weight(56f)) {
            ZhiTopBar(
                state = TopBarState.from(state),
                wide = true,
                // WideWorkspace 本身处于 glassMain.capture 的子树里；让这个
                glass = NoGlass,
                onOpenSidebar = viewModel::openSidebar,
                onSettings = viewModel::openSettings,
                tabs = null,
            )
            LocalZhiPageSet.current.ChatContent(state, viewModel, true, Modifier.weight(1f), glass)
        }
        ZhiVerticalDivider()
        Column(modifier = Modifier.weight(44f)) {
            val secondary = WorkspaceTab.entries.filter { it != WorkspaceTab.CHAT }
            val paneTabs = remember { secondary }
            val panePager = rememberPagerState(pageCount = { paneTabs.size })
            val paneScope = rememberCoroutineScope()
            WorkspaceTabs(
                tabs = paneTabs,
                selected = state.tab.takeIf { it in paneTabs } ?: paneTabs.first(),
                onSelect = { tab ->
                    // 宽屏副栏也写入同一个业务真源；否则 TabRow 高亮、Pager 和
                    // 下次 state 更新之间会出现短暂的双状态。
                    viewModel.selectTab(tab)
                    paneScope.launch { panePager.springAnimateToPage(paneTabs.indexOf(tab)) }
                },
            )
            WorkspacePager(
                tabs = paneTabs,
                pagerState = panePager,
                selectedTab = state.tab.takeIf { it in paneTabs } ?: paneTabs.first(),
                modifier = Modifier.weight(1f),
                compact = false,
                state = state,
                viewModel = viewModel,
                isDark = isDark,
                glass = glass,
            )
        }
    }
}

/** 窄屏：底部 Tab 切换；内容使用 Miuix 官方示例同款 HorizontalPager。 */
@Composable
internal fun CompactWorkspace(
    state: WorkspaceUiState,
    viewModel: WorkspaceViewModel,
    isDark: Boolean,
    glass: Glass,
    /** Pager 状态由**调用方**持有。 */
    pagerState: PagerState,
    onPageSelected: (WorkspaceTab) -> Unit = {},
) {
    val tabs = remember { WorkspaceTab.entries.toList() }
    WorkspacePager(
        tabs = tabs,
        pagerState = pagerState,
        selectedTab = state.tab,
        modifier = Modifier.fillMaxSize(),
        compact = true,
        state = state,
        viewModel = viewModel,
        isDark = isDark,
        glass = glass,
        onPageSelected = onPageSelected,
    )
}

/** 工作区内容页：与 Miuix 官方示例的布局板块相同，TabRow 只负责选择 */
@Composable
private fun WorkspacePager(
    tabs: List<WorkspaceTab>,
    pagerState: PagerState,
    selectedTab: WorkspaceTab,
    modifier: Modifier,
    compact: Boolean,
    state: WorkspaceUiState,
    viewModel: WorkspaceViewModel,
    isDark: Boolean,
    glass: Glass,
    onPageSelected: (WorkspaceTab) -> Unit = {},
) {
    val currentSelectedTab by rememberUpdatedState(selectedTab)
    val latestOnPageSelected by rememberUpdatedState(onPageSelected)
    LaunchedEffect(pagerState, tabs) {
        snapshotFlow { pagerState.currentPage to pagerState.isScrollInProgress }
            .collect { (page, scrolling) ->
                tabs.getOrNull(page)?.let { tab ->
                    // currentPage 只在页真正变化时变化；isScrollInProgress 仅负责
                    // 把 settle 边界传给业务层，不把逐帧拖动状态向上扩散。
                    latestOnPageSelected(tab)
                    if (!scrolling && tab != currentSelectedTab) viewModel.selectTab(tab)
                }
            }
    }
    var pagerInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(pagerState, selectedTab) {
        val target = tabs.indexOf(selectedTab).coerceIn(0, tabs.lastIndex)
        if (!pagerInitialized) {
            if (pagerState.currentPage != target) pagerState.scrollToPage(target)
            pagerInitialized = true
        } else if (pagerState.currentPage != target) {
            // 顶部 Tab 点击先更新 ViewModel；外部状态变化必须反向驱动 Pager，
            // 否则选中态可能变化而页面仍停在旧页。
            pagerState.springAnimateToPage(target)
        }
    }
    val flingBehavior = PagerDefaults.flingBehavior(
        state = pagerState,
        snapAnimationSpec = PagerNavigationSpringSpec,
    )
    HorizontalPager(
        state = pagerState,
        modifier = modifier,
        userScrollEnabled = false,
        // 工作区只有少量页面；不要让 Pager 预组合远离当前页的重型对话、终端和文件树。
        beyondViewportPageCount = 0,
        flingBehavior = flingBehavior,
        key = { tabs[it] },
        pageContent = { page ->
            val pageSet = LocalZhiPageSet.current
            val tab = tabs[page]
            val pageModifier = if (compact && tab != WorkspaceTab.CHAT) {
                Modifier.fillMaxSize().padding(top = TopBarInsetCompact)
            } else {
                Modifier.fillMaxSize()
            }
            when (tab) {
                WorkspaceTab.CHAT -> pageSet.ChatContent(state, viewModel, !compact, pageModifier, glass)
                WorkspaceTab.FILES -> pageSet.FilesContent(state, viewModel, pageModifier)
                WorkspaceTab.TERMINAL -> pageSet.TerminalContent(state, viewModel, pageModifier)
            }
        },
    )
}

/** 工作区 Tab 行高度 = 官方 `TabRowDefaults.TabRowWithContourHeight`（45dp）。 */
internal val WorkspaceTabRowHeight = 45.dp

@Composable
internal fun WorkspaceTabs(
    tabs: List<WorkspaceTab>,
    selected: WorkspaceTab,
    onSelect: (WorkspaceTab) -> Unit,
    modifier: Modifier = Modifier,
    pagerPage: Int? = null,
) {
    val fromBusiness = tabs.indexOf(selected).coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
    val selectedIndex = (pagerPage ?: fromBusiness).coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
    val labels = remember(tabs) { tabs.map { it.label } }
    ZhiSegmentedTabs(
        tabs = labels,
        selectedIndex = selectedIndex,
        // 点击时**不**按 `selected` 去重：高亮此时由 Pager 驱动
        onSelect = { index -> tabs.getOrNull(index)?.let(onSelect) },
        modifier = modifier.fillMaxWidth().height(WorkspaceTabRowHeight),
        matchWidth = true,
    )
}

/** 工作区标签 → 图标。 */
@Composable
internal fun iconForTab(tab: WorkspaceTab): Painter = when (tab) {
    WorkspaceTab.CHAT -> ZhiIcons.chat
    WorkspaceTab.TERMINAL -> ZhiIcons.terminal
    WorkspaceTab.FILES -> ZhiIcons.files
}

/** 顶栏标题行高度（窄屏用 48dp，见 `ZhiTopBar` 里的 height）。 */
internal val TopBarTitleRowHeight = 48.dp

/** 归顺到顶栏的 Tab 行上下留白（`ZhiTopBar` 里 `padding(vertical = 6.dp)` 的两倍）。 */
internal val TopBarTabRowPadding = 12.dp

/** 悬浮头部的占位高度：宽屏只有标题行，窄屏还要加上归顺进来的 Tab 行。 */
// SmallTopAppBar 官方 CollapsedHeight = 52dp；窄屏 Tab 行在 bottomContent 槽位。
internal val TopBarInset = 56.dp

@Composable
private fun topBarWindowInset(): Dp =
    with(LocalDensity.current) {
        WindowInsets.systemBars.only(WindowInsetsSides.Top).getTop(this).toDp()
    }

internal val LocalCompactTopInsetOverride = androidx.compose.runtime.compositionLocalOf<Dp?> { null }

internal val TopBarInsetCompact: Dp
    @Composable get() {
        LocalCompactTopInsetOverride.current?.let { return it }
        // Bottom-tab mode removes the tab row from ZhiTopBar. Do not keep reserving
        val hasTopTabs = LocalThemeSettings.current.tabBarPositionValue == TabBarPosition.TOP
        return TopBarInset +
            (if (hasTopTabs) WorkspaceTabRowHeight + 1.dp else 0.dp) +
            topBarWindowInset()
    }

@Composable
internal fun ZhiSidebarHost(
    state: WorkspaceUiState,
    viewModel: WorkspaceViewModel,
    modifier: Modifier = Modifier,
) {
    ZhiSidebar(
        state = SidebarState.from(state),
        onNewSession = viewModel::newSession,
        onOpenSession = viewModel::openSession,
        onSessionActions = viewModel::showSessionActions,
        // 长按会话的动作菜单：由侧栏里那一条会话自己渲染（从手指位置长出来）
        anchoredMenu = { anchorId, fingerOffset ->
            // 侧栏抽屉内部自有一个 Scaffold（见 `ZhiSideDrawer`），所以用**局部**宿主，
            // 菜单才落在抽屉这一层。传 true 会注册进最外层 Scaffold，被抽屉盖住。
            ZhiAnchoredMenuHost(state, viewModel, anchorId, fingerOffset, renderInRootScaffold = false)
        },
        onSandbox = { viewModel.openSandbox() },
        onRuntime = viewModel::openEnvironment,
        onSettings = viewModel::openSettings,
        onUpdateProjectPath = viewModel::updateProjectDirectory,
        onAddWorkspace = { path, label -> viewModel.addWorkspace(path, label, switchToIt = true) },
        onRenameWorkspace = viewModel::renameWorkspace,
        onRemoveWorkspace = viewModel::removeWorkspace,
        onToggleWorkspaceExpanded = viewModel::toggleWorkspaceExpanded,
        onSetAllWorkspacesExpanded = viewModel::setAllWorkspacesExpanded,
        onSetSidebarOrderBy = viewModel::setSidebarOrderBy,
        onNewSessionInWorkspace = viewModel::newSessionInWorkspace,
        modifier = modifier,
    )
}
