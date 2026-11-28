package com.zhizhu.zhicode.compose.ui.pages.miuix

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.zhizhu.zhicode.compose.data.TabBarPosition
import com.zhizhu.zhicode.compose.ui.WorkspaceTabs
import com.zhizhu.zhicode.compose.ui.LiquidWorkspaceBar
import com.zhizhu.zhicode.compose.ui.ZhiMotion
import top.yukonga.miuix.kmp.basic.Surface
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import com.zhizhu.zhicode.compose.theme.ZhiColors
import com.zhizhu.zhicode.compose.ui.effects.pressSink
import com.zhizhu.zhicode.compose.ui.effects.PageBlurBar
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow

import com.zhizhu.zhicode.compose.model.ChoiceIntent
import com.zhizhu.zhicode.compose.model.WorkspaceTab
import com.zhizhu.zhicode.compose.model.WorkspaceUiState
import com.zhizhu.zhicode.compose.state.WorkspaceViewModel
import com.zhizhu.zhicode.compose.ui.CompactWorkspace
import com.zhizhu.zhicode.compose.ui.FloatingNavBarGap
import com.zhizhu.zhicode.compose.ui.FloatingNavBarHeight
import com.zhizhu.zhicode.compose.ui.FloatingNavContentGap
import com.zhizhu.zhicode.compose.ui.Glass
import com.zhizhu.zhicode.compose.ui.LocalNavBarProgress
import com.zhizhu.zhicode.compose.ui.LocalNavBarTarget
import com.zhizhu.zhicode.compose.ui.LocalNavBarScrollTick
import com.zhizhu.zhicode.compose.ui.LocalNavBarTravelPx
import com.zhizhu.zhicode.compose.ui.LocalBottomBarInset
import com.zhizhu.zhicode.compose.ui.LocalFloatingNavInset
import com.zhizhu.zhicode.compose.ui.PageFeedback
import com.zhizhu.zhicode.compose.ui.TopBarInsetCompact
import com.zhizhu.zhicode.compose.ui.TopBarState
import com.zhizhu.zhicode.compose.ui.WideWorkspace
import com.zhizhu.zhicode.compose.ui.ZhiOverlayHost
import com.zhizhu.zhicode.compose.ui.ZhiSideDrawer
import com.zhizhu.zhicode.compose.ui.ZhiSidebarHost
import com.zhizhu.zhicode.compose.ui.ZhiTopBar
import com.zhizhu.zhicode.compose.ui.debug.ZhiFrameTrace
import com.zhizhu.zhicode.compose.ui.pages.miuix.overlays.ZhiDebugHud
import top.yukonga.miuix.kmp.basic.Scaffold

@Composable
internal fun WorkspacePage(
    state: WorkspaceUiState,
    viewModel: WorkspaceViewModel,
    isDark: Boolean,
    wide: Boolean,
    glass: Glass,
    glassMain: Glass,
) {
    val configuration = LocalConfiguration.current
    val themeSettings = com.zhizhu.zhicode.compose.theme.LocalThemeSettings.current
    // 窄屏 Pager 状态提到这里：顶栏（标签高亮）与内容（页面滑动）必须共用同一个
    // `currentPage`，否则高亮只能等动画结束才更新。
    val compactTabs = remember { WorkspaceTab.entries.toList() }
    val compactPagerState = androidx.compose.foundation.pager.rememberPagerState(
        pageCount = { compactTabs.size },
    )
    val showBottomNavigation = !wide && themeSettings.tabBarPositionValue == TabBarPosition.BOTTOM
    val fileSelectionActive = !wide &&
        (state.fileSelectionMode || state.fileSelection.isNotEmpty())
    val floatingNavigationBlur = showBottomNavigation && themeSettings.tabBarFloating &&
        themeSettings.blurEnabled && themeSettings.floatingNavBlur && glassMain.supported
    // 底栏的模糊源必须**只**来自 `glassMain` 的层级捕获，而底栏本身位于
    val pageNavigationBackdrop = glassMain.backdrop
    val floatingNavigationBackdrop = glassMain.backdrop.takeIf { floatingNavigationBlur }
    val navigationBarBottomInsetDp = WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
        .asPaddingValues().calculateBottomPadding()
    val floatingNavigationAvoidance = FloatingNavBarHeight + FloatingNavBarGap +
        navigationBarBottomInsetDp
    val imeOpen = com.zhizhu.zhicode.compose.ui.LocalImeMotion.current.imeVisible
    val imeNavAlpha by animateFloatAsState(
        targetValue = if (imeOpen) 0f else 1f,
        animationSpec = tween(
            durationMillis = if (imeOpen) ZhiMotion.EXIT_MILLIS else ZhiMotion.FADE_IN_MILLIS,
            easing = FastOutSlowInEasing,
        ),
        label = "miuixImeNavAlpha",
    )
    /* 胶囊隐藏进度的**目标值**（`0` = 完全可见，`1` = 完全滑出）。 */
    val navBarHideTarget = remember { mutableFloatStateOf(0f) }
    val navBarCollapsed = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        snapshotFlow { navBarHideTarget.floatValue }.collect { progress ->
            if (!navBarCollapsed.value && progress >= 0.995f) {
                navBarCollapsed.value = true
            } else if (navBarCollapsed.value && progress <= 0.85f) {
                navBarCollapsed.value = false
            }
        }
    }
    val floatingNavInset = if (
        showBottomNavigation && themeSettings.tabBarFloating && !navBarCollapsed.value
    ) {
        floatingNavigationAvoidance + FloatingNavContentGap
    } else {
        0.dp
    }
    val navBarScrollTick = remember { IntArray(1) }
    val navBarProgressAnim = remember { Animatable(0f) }
    LaunchedEffect(navBarProgressAnim) {
        snapshotFlow { navBarHideTarget.floatValue }.collect { target ->
            navBarProgressAnim.animateTo(
                targetValue = target,
                animationSpec = tween(durationMillis = 120, easing = LinearOutSlowInEasing),
            )
        }
    }
    val navBarHideProgressState = navBarProgressAnim.asState()

    val workspaceBottomAvoidance = floatingNavInset
    val anchoredActionMenu = state.choicePicker?.isActionMenu == true
    // Edge-to-edge makes the transparent system navigation area part of the app window.
    Box(modifier = Modifier.fillMaxSize().background(ZhiColors.backdrop())) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            // This is the KSU mounting point: the bar is a Scaffold child, not a
            if (showBottomNavigation && !themeSettings.tabBarFloating) {
                Box(
                    // 用来核对"导航栏实际位置与内容让位是否一致"。
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    /* 固定底栏 ↔ 选择工具栏的**形变**过渡（与悬浮胶囊那一份同构）。 */
                    val barMorph = updateTransition(
                        targetState = fileSelectionActive,
                        label = "bottomBarFileToolbarMorph",
                    )
                    val navAlpha by barMorph.animateFloat(
                        transitionSpec = { tween(ZhiMotion.EXIT_MILLIS, easing = FastOutSlowInEasing) },
                        label = "bottomNavAlpha",
                    ) { selecting -> if (selecting) 0f else 1f }
                    val navSink by barMorph.animateFloat(
                        transitionSpec = { tween(ZhiMotion.EXIT_MILLIS, easing = FastOutSlowInEasing) },
                        label = "bottomNavSink",
                    ) { selecting -> if (selecting) 1f else 0f }
                    val toolAlpha by barMorph.animateFloat(
                        transitionSpec = { tween(ZhiMotion.FADE_IN_MILLIS, easing = FastOutSlowInEasing) },
                        label = "bottomToolAlpha",
                    ) { selecting -> if (selecting) 1f else 0f }
                    val toolRise by barMorph.animateFloat(
                        transitionSpec = { tween(ZhiMotion.FADE_IN_MILLIS, easing = FastOutSlowInEasing) },
                        label = "bottomToolRise",
                    ) { selecting -> if (selecting) 0f else 1f }

                    if (navAlpha > 0f) {
                        PageBlurBar(backdrop = pageNavigationBackdrop) {
                            NavigationBar(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .graphicsLayer {
                                        alpha = navAlpha
                                        val shrink = 1f - 0.04f * navSink
                                        scaleX = shrink
                                        scaleY = shrink
                                        translationY = 10.dp.toPx() * navSink
                                    },
                                // KSU 的做法：有可采样的背板时底栏底色必须是**透明**的
                                color = if (pageNavigationBackdrop != null) {
                                    Color.Transparent
                                } else {
                                    MiuixTheme.colorScheme.surface
                                },
                            ) {
                                WorkspaceTab.entries.forEach { tab ->
                                    val icon = when (tab) {
                                        WorkspaceTab.CHAT -> com.zhizhu.zhicode.compose.ui.ZhiMaterialIcons.ChatBubble
                                        WorkspaceTab.TERMINAL -> com.zhizhu.zhicode.compose.ui.ZhiMaterialIcons.Terminal
                                        WorkspaceTab.FILES -> com.zhizhu.zhicode.compose.ui.ZhiMaterialIcons.Folder
                                    }
                                    NavigationBarItem(
                                        modifier = Modifier.weight(1f),
                                        icon = icon,
                                        label = tab.label,
                                        selected = state.tab == tab,
                                        onClick = {
                                            ZhiFrameTrace.begin("tab:${tab.name}")
                                            viewModel.selectTab(tab)
                                        },
                                    )
                                }
                            }
                        }
                    }
                    if (toolAlpha > 0f) {
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
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .graphicsLayer {
                                    alpha = toolAlpha
                                    val grow = 0.96f + 0.04f * (1f - toolRise)
                                    scaleX = grow
                                    scaleY = grow
                                    translationY = 10.dp.toPx() * toolRise
                                },
                        )
                    }
                }
            }
        },
        // Miuix Scaffold defaults to `colorScheme.surface`. That opaque surface used to
        containerColor = ZhiColors.backdrop(),
        floatingToolbar = {
            // 文件选择工具栏**不在这里**挂：它和悬浮胶囊要共用屏幕底部同一个槽位
        },
        topBar = {
            if (wide) {
                // 宽屏：侧栏常驻，顶栏只覆盖右侧内容区 —— 由 WorkspaceLayouts
                // 里的 WideWorkspace 自己排侧栏 + 分隔线 + 顶栏，这里不重复挂。
            } else {
                ZhiTopBar(
                    state = TopBarState.from(state),
                    wide = false,
                    glass = glassMain,
                    onOpenSidebar = viewModel::openSidebar,
                    onSettings = {
                        ZhiFrameTrace.begin("settings:open")
                        viewModel.openSettings()
                    },
                    // 底栏模式下顶栏**不再画**标签行：同一组标签不能同时出现在两处，
                    // 那会让人分不清哪个是当前的。`null` 就是不渲染这个槽。
                    tabs = if (themeSettings.tabBarPositionValue == TabBarPosition.BOTTOM) {
                        null
                    } else {
                        WorkspaceTab.entries
                    },
                    selectedTab = state.tab,
                    // 让标签高亮跟随 Pager 的弹簧动画（与模型选择面板同一套逻辑）。
                    // 业务状态仍然只在 Pager 停稳后写回，两条路各管一件事。
                    pagerPage = compactPagerState.currentPage,
                    onSelectTab = { tab ->
                        ZhiFrameTrace.begin("tab:${tab.name}")
                        // ViewModel 是唯一业务真源；Pager 由 state.tab 反向同步。
                        viewModel.selectTab(tab)
                    },
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(glassMain.capture(Modifier))
                .padding(
                    bottom = if (showBottomNavigation) {
                        if (themeSettings.tabBarFloating) {
                            // 悬浮胶囊：**不让位**。内容铺到屏幕最底边（含导航栏那一段），
                            // 胶囊浮在它上面。让位交给各页的滚动内边距（LocalFloatingNavInset）。
                            0.dp
                        } else padding.calculateBottomPadding()
                    } else {
                        padding.calculateBottomPadding()
                    },
                ),
        ) {
            CompositionLocalProvider(
                LocalFloatingNavInset provides floatingNavInset,
                /* 固定底栏的高度（悬浮模式下是 0，因为那时内容本来就不让位）。 */
                LocalBottomBarInset provides if (showBottomNavigation && themeSettings.tabBarFloating) {
                    0.dp
                } else {
                    padding.calculateBottomPadding()
                },
                LocalNavBarProgress provides navBarHideProgressState,
                LocalNavBarTarget provides navBarHideTarget,
                LocalNavBarScrollTick provides navBarScrollTick,
                LocalNavBarTravelPx provides with(LocalDensity.current) {
                    (FloatingNavBarHeight + FloatingNavBarGap +
                        navigationBarBottomInsetDp + NavBarShadowRoom).toPx()
                },
            ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // 三屏共用的隐藏手势。
                    .navBarHideOnVerticalDrag(
                        progress = navBarHideTarget,
                        scrollTick = navBarScrollTick,
                        // 移出量：与 `graphicsLayer` 里的算式**必须一致**，
                        // 否则"拖到底"和"藏到底"会差一截。
                        exitPx = with(LocalDensity.current) {
                            (FloatingNavBarHeight + FloatingNavBarGap +
                                navigationBarBottomInsetDp + NavBarShadowRoom).toPx()
                        },
                    ),
            ) {
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
                    Box {
                        CompactWorkspace(
                            state = state,
                            viewModel = viewModel,
                            isDark = isDark,
                            glass = glass,
                            pagerState = compactPagerState,
                        )
                    }
                }
            }
            }

            // ---- 非对话面板的反馈条 
            if (!wide && state.tab != WorkspaceTab.CHAT) {
                PageFeedback(
                    message = state.message,
                    isError = state.messageIsError,
                    onDismiss = viewModel::clearMessage,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        // 8dp 的固定底距走 padding（组合期常量，不随滚动变）；
                        .padding(bottom = 8.dp)
                        .offset {
                            val visibleAvoidance = workspaceBottomAvoidance.toPx() *
                                (1f - navBarHideProgressState.value)
                            IntOffset(0, -visibleAvoidance.toInt())
                        },
                )
            }

            if (!state.sidebarOpen) {
                ZhiOverlayHost(state = state, viewModel = viewModel, glassMain = glassMain)
            }



            // ---- 全局调试浮层（debug 构建的 UI 调试页里打开）
            if (com.zhizhu.zhicode.compose.BuildConfig.DEBUG && state.debugOverlayEnabled) {
                ZhiDebugHud(
                    state = state,
                    viewModel = viewModel,
                    // 调试 HUD 在 glassMain.capture 子树内部，禁止它反向采样父背板。
                    glass = com.zhizhu.zhicode.compose.ui.NoGlass,
                    // 让出顶栏（含 Tab 行）的高度，否则药丸会被模糊顶栏压住。
                    topInset = TopBarInsetCompact,
                )
            }
        }
    }

    val floatingNavSlotAvailable = !wide &&
        themeSettings.tabBarPositionValue == TabBarPosition.BOTTOM &&
        themeSettings.tabBarFloating
    val showFloatingNav = floatingNavSlotAvailable && !fileSelectionActive
    val fileToolbarBottomPadding = navigationBarBottomInsetDp + FloatingNavBarGap
    // 选择工具栏与底栏形态**无关**：顶栏模式、固定底栏模式下它照样要出来。
    // 只有"窄屏 + 没开模态浮层"是硬条件（宽屏用侧栏与行内动作，不需要它）。
    if (floatingNavSlotAvailable || !showBottomNavigation) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            val morph = updateTransition(
                targetState = fileSelectionActive,
                label = "navFileToolbarMorph",
            )
            val slotHasNav = floatingNavSlotAvailable
            val exitOffsetPx = with(LocalDensity.current) {
                (FloatingNavBarHeight + FloatingNavBarGap +
                    navigationBarBottomInsetDp + NavBarShadowRoom).toPx()
            }
            val navAlpha by morph.animateFloat(
                transitionSpec = { tween(ZhiMotion.EXIT_MILLIS, easing = FastOutSlowInEasing) },
                label = "navAlpha",
            ) { selecting ->
                // 槽位不可用（顶栏模式）→ 胶囊永远不该出现；可用时按选择态淡出。
                if (slotHasNav && !selecting) 1f else 0f
            }
            val navSink by morph.animateFloat(
                transitionSpec = { tween(ZhiMotion.EXIT_MILLIS, easing = FastOutSlowInEasing) },
                label = "navSink",
            ) { selecting -> if (selecting) 1f else 0f }
            val toolbarAlpha by morph.animateFloat(
                transitionSpec = { tween(ZhiMotion.FADE_IN_MILLIS, easing = FastOutSlowInEasing) },
                label = "toolbarAlpha",
            ) { selecting -> if (selecting) 1f else 0f }
            val toolbarRise by morph.animateFloat(
                transitionSpec = { tween(ZhiMotion.FADE_IN_MILLIS, easing = FastOutSlowInEasing) },
                label = "toolbarRise",
            ) { selecting -> if (selecting) 0f else 1f }

            if (navAlpha > 0f && imeNavAlpha > 0.005f) {
                LiquidWorkspaceBar(
                    selected = state.tab,
                    onSelect = { tab ->
                        if (imeOpen) return@LiquidWorkspaceBar
                        ZhiFrameTrace.begin("tab:${tab.name}")
                        viewModel.selectTab(tab)
                    },
                    backdrop = floatingNavigationBackdrop,
                    blurEnabled = floatingNavigationBackdrop != null,
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = navAlpha * imeNavAlpha
                            // 形变时胶囊略微下沉 + 缩小，像被"压"进工具栏那一层
                            val shrink = 1f - 0.04f * navSink
                            scaleX = shrink
                            scaleY = shrink
                            translationY = with(density) {
                                (12.dp * navSink).toPx()
                            } + exitOffsetPx * navBarHideProgressState.value
                        },
                )
            }
            if (toolbarAlpha > 0f && imeNavAlpha > 0.005f) {
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
                    modifier = Modifier
                        // 与胶囊**同一个**底距：形变的前提就是两者落点一致，
                        // 否则中途会看到一块在上升、一块在原地。
                        .padding(bottom = fileToolbarBottomPadding)
                        .graphicsLayer {
                            alpha = toolbarAlpha * imeNavAlpha
                            val grow = 0.96f + 0.04f * (1f - toolbarRise)
                            scaleX = grow
                            scaleY = grow
                            translationY = with(density) { (10.dp * toolbarRise).toPx() }
                        },
                )
            }
        }

    }
    if (!wide) {
        ZhiSideDrawer(
            open = state.sidebarOpen,
            onClose = viewModel::closeSidebar,
            // 宽度上限 300dp：抽屉是"导航"，不是工作区 —— 铺到 320dp/82% 时
            width = (configuration.screenWidthDp * 0.76f).dp.coerceAtMost(300.dp),
            overlayHost = {
                ZhiOverlayHost(state = state, viewModel = viewModel, glassMain = glassMain)
            },
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

/** 胶囊隐藏时额外多移出的距离，用来把**阴影**也一起送出屏幕。 */
private val NavBarShadowRoom = 24.dp

internal fun Modifier.navBarHideOnVerticalDrag(
    progress: androidx.compose.runtime.MutableFloatState,
    exitPx: Float,
    scrollTick: IntArray,
): Modifier = this.pointerInput(exitPx) {
    awaitPointerEventScope {
        val safety = exitPx.coerceAtLeast(1f)
        var startProgress = 0f
        var anchorY = Float.NaN
        // 按下时的滚动计数。按住期间若它变了，说明列表在滚、这一下归列表管。
        var tickAtPress = 0
        while (true) {
            val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
            val change = event.changes.firstOrNull() ?: continue

            if (change.pressed) {
                val y = change.position.y
                if (anchorY.isNaN()) {
                    anchorY = y
                    startProgress = progress.floatValue
                    tickAtPress = scrollTick[0]
                } else {
                    val listMoved = scrollTick[0] != tickAtPress
                    if (listMoved) {
                        anchorY = y
                        startProgress = progress.floatValue
                    } else {
                        // 手指向下 → 隐藏（进度增大），每一像素都反映到进度上。
                        val delta = (y - anchorY) / safety
                        progress.floatValue = (startProgress + delta).coerceIn(0f, 1f)
                    }
                }
            } else if (!anchorY.isNaN()) {
                // 抬手：保留当前进度，不回弹、不补齐。
                anchorY = Float.NaN
            }
        }
    }
}
