package com.zhizhu.zhicode.compose.ui.pages.m3e

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.zhizhu.zhicode.compose.data.TabBarPosition
import com.zhizhu.zhicode.compose.model.WorkspaceTab
import com.zhizhu.zhicode.compose.model.WorkspaceUiState
import com.zhizhu.zhicode.compose.state.WorkspaceViewModel
import com.zhizhu.zhicode.compose.theme.LocalThemeSettings
import com.zhizhu.zhicode.compose.ui.CompactWorkspace
import com.zhizhu.zhicode.compose.ui.FloatingNavBarGap
import com.zhizhu.zhicode.compose.ui.FloatingNavBarHeight
import com.zhizhu.zhicode.compose.ui.FloatingNavContentGap
import com.zhizhu.zhicode.compose.ui.Glass
import com.zhizhu.zhicode.compose.ui.LocalBottomBarInset
import com.zhizhu.zhicode.compose.ui.LocalCompactTopInsetOverride
import com.zhizhu.zhicode.compose.ui.LocalFloatingNavInset
import com.zhizhu.zhicode.compose.ui.LocalImeMotion
import com.zhizhu.zhicode.compose.ui.LocalNavBarProgress
import com.zhizhu.zhicode.compose.ui.LocalNavBarScrollTick
import com.zhizhu.zhicode.compose.ui.LocalNavBarTarget
import com.zhizhu.zhicode.compose.ui.LocalNavBarTravelPx
import com.zhizhu.zhicode.compose.ui.NoGlass
import com.zhizhu.zhicode.compose.ui.PageFeedback
import com.zhizhu.zhicode.compose.ui.WideWorkspace
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.ZhiMotion
import com.zhizhu.zhicode.compose.ui.ZhiOverlayHost
import com.zhizhu.zhicode.compose.ui.ZhiSidebarHost
import com.zhizhu.zhicode.compose.ui.brandSignatureMarquee
import com.zhizhu.zhicode.compose.ui.iconForTab
import com.zhizhu.zhicode.compose.ui.pages.miuix.FileSelectionToolbar
import com.zhizhu.zhicode.compose.ui.pages.miuix.navBarHideOnVerticalDrag
import com.zhizhu.zhicode.compose.ui.pages.miuix.overlays.ZhiDebugHud

/**
 * Material 3 Expressive 主工作区：
 * - `CenterAlignedTopAppBar` + Tonal 圆钮 + 用量胶囊；
 * - 顶部标签模式：连接型 `SingleChoiceSegmentedButtonRow`；
 * - 底部悬浮模式：MD3 Expressive `HorizontalFloatingToolbar` + `ToggleButton`；
 * - 底部固定模式：MD3 `NavigationBar`；
 * - 窄屏侧栏：`ModalNavigationDrawer` + `ModalDrawerSheet`。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun M3eWorkspacePage(
    state: WorkspaceUiState,
    viewModel: WorkspaceViewModel,
    isDark: Boolean,
    wide: Boolean,
    glass: Glass,
    glassMain: Glass,
) {
    val scheme = MaterialTheme.colorScheme
    val themeSettings = LocalThemeSettings.current
    val compactTabs = remember { WorkspaceTab.entries.toList() }
    val compactPagerState = rememberPagerState(pageCount = { compactTabs.size })
    var highlightedTab by remember { mutableStateOf(state.tab) }
    LaunchedEffect(state.tab) { highlightedTab = state.tab }

    val showBottomNavigation = !wide && themeSettings.tabBarPositionValue == TabBarPosition.BOTTOM
    val fileSelectionActive = !wide && (state.fileSelectionMode || state.fileSelection.isNotEmpty())
    val imeOpen = LocalImeMotion.current.imeVisible
    val imeNavAlpha by animateFloatAsState(
        targetValue = if (imeOpen) 0f else 1f,
        animationSpec = tween(
            durationMillis = if (imeOpen) ZhiMotion.EXIT_MILLIS else ZhiMotion.FADE_IN_MILLIS,
            easing = FastOutSlowInEasing,
        ),
        label = "m3eImeNavAlpha",
    )
    val navBarBottomInsetDp = WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
        .asPaddingValues().calculateBottomPadding()
    val floatingAvoidance = FloatingNavBarHeight + FloatingNavBarGap + navBarBottomInsetDp

    val navBarHideTarget = remember { mutableFloatStateOf(0f) }
    val navBarCollapsed = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        snapshotFlow { navBarHideTarget.floatValue }.collect { progress ->
            if (!navBarCollapsed.value && progress >= 0.995f) navBarCollapsed.value = true
            else if (navBarCollapsed.value && progress <= 0.85f) navBarCollapsed.value = false
        }
    }
    val navBarScrollTick = remember { IntArray(1) }
    val navBarHideAnim = remember { Animatable(0f) }
    val navBarHideProgressState = navBarHideAnim.asState()
    LaunchedEffect(Unit) {
        snapshotFlow { navBarHideTarget.floatValue }.collect { target ->
            navBarHideAnim.animateTo(
                targetValue = target,
                animationSpec = tween(durationMillis = 120, easing = LinearOutSlowInEasing),
            )
        }
    }
    LaunchedEffect(showBottomNavigation, themeSettings.tabBarFloating, fileSelectionActive) {
        if (!showBottomNavigation || !themeSettings.tabBarFloating || fileSelectionActive) {
            navBarHideTarget.floatValue = 0f
            navBarHideAnim.snapTo(0f)
            navBarCollapsed.value = false
        }
    }

    val floatingNavInset = if (
        showBottomNavigation && themeSettings.tabBarFloating && !navBarCollapsed.value
    ) {
        floatingAvoidance + FloatingNavContentGap
    } else {
        0.dp
    }

    val drawerScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(
        initialValue = if (state.sidebarOpen) DrawerValue.Open else DrawerValue.Closed,
    )
    LaunchedEffect(state.sidebarOpen, wide) {
        if (!wide) {
            if (state.sidebarOpen) drawerState.open() else drawerState.close()
        }
    }
    LaunchedEffect(drawerState, wide) {
        if (wide) return@LaunchedEffect
        snapshotFlow { drawerState.currentValue }
            .drop(1)
            .collect { value ->
                if (value == DrawerValue.Closed && viewModel.state.value.sidebarOpen) {
                    viewModel.closeSidebar()
                } else if (value == DrawerValue.Open && !viewModel.state.value.sidebarOpen) {
                    viewModel.openSidebar()
                }
            }
    }

    M3eMiuixPopupBridge {
        val workspaceScaffold: @Composable () -> Unit = {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = scheme.surface,
                contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
                topBar = {
                    if (!wide) {
                        Surface(color = scheme.surfaceContainerLow, tonalElevation = 2.dp) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                CenterAlignedTopAppBar(
                                    title = {
                                        Text(
                                            text = themeSettings.resolvedTopBrand,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier.brandSignatureMarquee(),
                                        )
                                    },
                                    navigationIcon = {
                                        FilledTonalIconButton(
                                            onClick = viewModel::openSidebar,
                                            modifier = Modifier.padding(start = 8.dp),
                                        ) {
                                            Icon(painter = ZhiIcons.menu, contentDescription = "打开侧边栏")
                                        }
                                    },
                                    actions = {
                                        FilledTonalIconButton(
                                            onClick = viewModel::openSettings,
                                            modifier = Modifier.padding(end = 8.dp),
                                        ) {
                                            Icon(painter = ZhiIcons.settings, contentDescription = "设置")
                                        }
                                    },
                                    colors = TopAppBarDefaults.topAppBarColors(
                                        containerColor = Color.Transparent,
                                    ),
                                )
                                if (!showBottomNavigation) {
                                    val selectedIdx = compactTabs.indexOf(highlightedTab).coerceAtLeast(0)
                                    SingleChoiceSegmentedButtonRow(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp)
                                            .padding(bottom = 10.dp),
                                    ) {
                                        compactTabs.forEachIndexed { idx, tab ->
                                            SegmentedButton(
                                                shape = SegmentedButtonDefaults.itemShape(idx, compactTabs.size),
                                                selected = idx == selectedIdx,
                                                onClick = { viewModel.selectTab(tab) },
                                                icon = {
                                                    Icon(
                                                        painter = iconForTab(tab),
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp),
                                                    )
                                                },
                                                label = { Text(tab.label, maxLines = 1) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                bottomBar = {
                    if (showBottomNavigation && !themeSettings.tabBarFloating) {
                        if (fileSelectionActive) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(scheme.surfaceContainer)
                                    .padding(bottom = navBarBottomInsetDp),
                            ) {
                                FileSelectionToolbar(
                                    visible = true,
                                    entries = state.fileEntries,
                                    selection = state.fileSelection,
                                    glass = glassMain,
                                    onAttach = viewModel::attachSelectedEntries,
                                    onRename = viewModel::renameSelectedEntry,
                                    onDelete = viewModel::deleteSelectedEntries,
                                    onCopy = viewModel::copySelectedEntries,
                                    onMove = viewModel::moveSelectedEntries,
                                    clipboardCount = state.fileClipboard.size,
                                    clipboardMove = state.fileClipboardMove,
                                    onPaste = viewModel::pasteFilesIntoCurrentDirectory,
                                    onSelectAll = viewModel::toggleSelectAllFiles,
                                    onDismiss = viewModel::clearFileSelection,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        } else {
                            NavigationBar(containerColor = scheme.surfaceContainer) {
                                compactTabs.forEach { tab ->
                                    NavigationBarItem(
                                        selected = state.tab == tab,
                                        onClick = { viewModel.selectTab(tab) },
                                        icon = {
                                            Icon(
                                                painter = iconForTab(tab),
                                                contentDescription = tab.label,
                                                modifier = Modifier.size(22.dp),
                                            )
                                        },
                                        label = { Text(tab.label) },
                                    )
                                }
                            }
                        }
                    }
                },
            ) { padding ->
                val travelPx = with(LocalDensity.current) {
                    (FloatingNavBarHeight + FloatingNavBarGap + navBarBottomInsetDp + 16.dp).toPx()
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            bottom = if (showBottomNavigation && themeSettings.tabBarFloating) {
                                0.dp
                            } else {
                                padding.calculateBottomPadding()
                            },
                        ),
                ) {
                    CompositionLocalProvider(
                        LocalCompactTopInsetOverride provides padding.calculateTopPadding(),
                        LocalFloatingNavInset provides floatingNavInset,
                        LocalBottomBarInset provides if (showBottomNavigation && themeSettings.tabBarFloating) {
                            0.dp
                        } else {
                            padding.calculateBottomPadding()
                        },
                        LocalNavBarProgress provides navBarHideProgressState,
                        LocalNavBarTarget provides navBarHideTarget,
                        LocalNavBarScrollTick provides navBarScrollTick,
                        LocalNavBarTravelPx provides travelPx,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .navBarHideOnVerticalDrag(
                                    progress = navBarHideTarget,
                                    scrollTick = navBarScrollTick,
                                    exitPx = travelPx,
                                ),
                        ) {
                            if (wide) {
                                WideWorkspace(
                                    state = state,
                                    viewModel = viewModel,
                                    isDark = isDark,
                                    glass = glass,
                                    glassMain = glassMain,
                                )
                            } else {
                                CompactWorkspace(
                                    state = state,
                                    viewModel = viewModel,
                                    isDark = isDark,
                                    glass = glass,
                                    pagerState = compactPagerState,
                                    onPageSelected = { highlightedTab = it },
                                )
                            }
                        }
                    }

                    if (!wide && state.tab != WorkspaceTab.CHAT) {
                        PageFeedback(
                            message = state.message,
                            isError = state.messageIsError,
                            onDismiss = viewModel::clearMessage,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 8.dp)
                                .offset {
                                    val visibleAvoidance = floatingNavInset.toPx() *
                                        (1f - navBarHideProgressState.value)
                                    IntOffset(0, -visibleAvoidance.toInt())
                                },
                        )
                    }

                    if (showBottomNavigation && themeSettings.tabBarFloating && imeNavAlpha > 0.005f) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(bottom = navBarBottomInsetDp + 10.dp)
                                .graphicsLayer {
                                    translationY = travelPx * navBarHideProgressState.value
                                    alpha = imeNavAlpha
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (fileSelectionActive) {
                                FileSelectionToolbar(
                                    visible = true,
                                    entries = state.fileEntries,
                                    selection = state.fileSelection,
                                    glass = glassMain,
                                    onAttach = viewModel::attachSelectedEntries,
                                    onRename = viewModel::renameSelectedEntry,
                                    onDelete = viewModel::deleteSelectedEntries,
                                    onCopy = viewModel::copySelectedEntries,
                                    onMove = viewModel::moveSelectedEntries,
                                    clipboardCount = state.fileClipboard.size,
                                    clipboardMove = state.fileClipboardMove,
                                    onPaste = viewModel::pasteFilesIntoCurrentDirectory,
                                    onSelectAll = viewModel::toggleSelectAllFiles,
                                    onDismiss = viewModel::clearFileSelection,
                                )
                            } else {
                                HorizontalFloatingToolbar(
                                    expanded = true,
                                    colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
                                ) {
                                    compactTabs.forEach { tab ->
                                        val selected = state.tab == tab
                                        Surface(
                                            onClick = { if (!imeOpen) viewModel.selectTab(tab) },
                                            shape = CircleShape,
                                            color = if (selected) scheme.surface else Color.Transparent,
                                            contentColor = if (selected) scheme.primary else scheme.onPrimaryContainer,
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Icon(
                                                    painter = iconForTab(tab),
                                                    contentDescription = tab.label,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    text = tab.label,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    maxLines = 1,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    ZhiOverlayHost(state = state, viewModel = viewModel, glassMain = glassMain)

                    if (com.zhizhu.zhicode.compose.BuildConfig.DEBUG && state.debugOverlayEnabled) {
                        ZhiDebugHud(
                            state = state,
                            viewModel = viewModel,
                            glass = NoGlass,
                            topInset = padding.calculateTopPadding(),
                        )
                    }
                }
            }
        }

        if (wide) {
            workspaceScaffold()
        } else {
            val drawerWidth = (LocalConfiguration.current.screenWidthDp.dp * 0.78f).coerceAtMost(320.dp)
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        modifier = Modifier.width(drawerWidth),
                        drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
                        drawerContainerColor = scheme.surfaceContainerLow,
                    ) {
                        ZhiSidebarHost(
                            state = state,
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                },
                content = {
                    BackHandler(enabled = state.sidebarOpen || drawerState.isOpen) {
                        viewModel.closeSidebar()
                        drawerScope.launch { drawerState.close() }
                    }
                    workspaceScaffold()
                },
            )
        }
    }
}
