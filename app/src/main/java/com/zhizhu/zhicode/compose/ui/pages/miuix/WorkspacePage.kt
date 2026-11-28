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
    // Scaffold 的 bottomBar 槽 —— 它是内容节点的**兄弟**，不是子节点。
    // 这条「内容录制 / 底栏采样」的单向关系就是 KSU 的 BlurredBar 结构；
    // 反向（底栏被自己录进去）会让 HWUI 得到自引用 RenderNode 并栈溢出。
    val pageNavigationBackdrop = glassMain.backdrop
    val floatingNavigationBackdrop = glassMain.backdrop.takeIf { floatingNavigationBlur }
    // 悬浮胶囊在底部**实际遮住**的高度：胶囊 + 与系统导航栏之间的缝 + 导航栏 inset。
    //
    // ⚠️ 这只是"被遮住多少"，不是"内容要让出多少布局高度"。
    // 胶囊是浮层，内容必须一路铺到屏幕底边（KSU 那种卡片从胶囊下穿过的观感）；
    // 各页拿这个值当**滚动内边距**用，于是"最后一行能滑到胶囊上方"和
    // "底部不再空出一条背景色带"同时成立。以前这里给内容加布局底部 padding，
    // 结果就是胶囊底下留出一整条纯背景色 —— 那正是用户反复说的"黑色区域"。
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
    // 列表滚动计数器：手势靠它判断"这一下归列表管"，避免同一段位移被算两次。
    // ⚠️ 用 `IntArray(1)` 而不是 `mutableStateOf(0)`：这个计数器由滚动**逐像素**自增，
    // 用 State 会每次都走一遍快照写入/失效分发；而它只在手势回调里被读（不进组合），
    // 普通容器就够，省掉这部分开销。
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
    // Paint the page backdrop at the root so that the floating bar's safe-area gap is
    // themed background, never the window's default black surface.
    Box(modifier = Modifier.fillMaxSize().background(ZhiColors.backdrop())) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            // This is the KSU mounting point: the bar is a Scaffold child, not a
            // z-indexed child of the captured workspace. Scaffold measures it once and
            // passes that exact height to the content lambda.
            if (showBottomNavigation && !themeSettings.tabBarFloating) {
                Box(
                    // 用来核对"导航栏实际位置与内容让位是否一致"。它每次布局变化都执行，
                    // 而悬浮/底栏在动画期间逐帧位移 → 日志里出现 893 次 `nav-probe`。
                    // 结论早已确认，探针删除；详见 ChatList.kt 同类注释与实测数据。
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
                                // KSU 的做法：有可采样的背板时底栏底色必须是**透明**的，
                                // 否则不透明的 surface 会把刚做好的模糊整个盖住
                                // （表现就是"固定底栏切换后模糊没了/背板变透明"）。
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
                                // ⚠️ **不要**在这里再写 `padding(horizontal = floatingHorizontalInset, vertical = 6.dp)`：
                                // `FileSelectionToolbar` 内部已经有那一层。叠两次会让卡片比
                                // 悬浮胶囊窄一圈、也矮一截，同时把内容挤出 `bottomBar` 槽位
                                // —— 表现就是底部被裁（用户截图里工具栏下缘被切掉）。
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
        // show through the transparent outer area of the floating bar as a full-width
        // black strip. The page backdrop must be the Scaffold's own container, not only
        // a parent behind it.
        containerColor = ZhiColors.backdrop(),
        floatingToolbar = {
            // 文件选择工具栏**不在这里**挂：它和悬浮胶囊要共用屏幕底部同一个槽位，
            // 而 Scaffold 的 floatingToolbar 槽位落在胶囊下面 —— 两样同挂就会叠在一起
            // （用户截图里工具栏被胶囊压住一半）。两者现在都在根层的同一个 Box 里过渡。
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
        // KSU's Scaffold passes the measured bottomBar height to content. This is the
        // app bar reservation (capsule + its single navigation inset), not a second
        // system-bar padding. Without it the bar is merely painted over every pane.
        // 顶栏 blur 需要内容从它**底下滚过**才有东西可采样：
        // 去掉 padding.top，让内容 Box 从 y=0 铺满，各面板自己在头部留白。
        //
        // ⚠️ 丢掉 top 是**有代价的**，改动这里之前必须知道：
        // 任何从 y=0 开始画、又不自己顶开的面板，头部都会被顶栏盖住。
        // 所以每个面板必须二选一 ——
        //   · 对话面板：用 ChatList 的 topInset（**滚动内边距**，它要能滚过顶栏）；
        //   · 其余面板：用 `TopBarInsetCompact` 的 **padding** 顶开
        //     （见 WorkspaceLayouts 里 PaneHost 那个分支）。
        // **新加面板时最容易漏的就是这一步**（文件面板就这么被盖过一次，
        // 当时这里有一句指向并不存在的常量的注释，照它做就漏了）。
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
            // 各页从这里取「胶囊遮住多高」，当作**滚动内边距**。别改回布局 padding：
            // 布局让位会在胶囊底下留出一条纯背景色带（用户报的就是这个）。
            //
            // ⚠️ 这里提供的是**稳定**的 [floatingNavInset]，不是动画值。
            //
            // 曾经写成 `provides animatedWorkspaceBottomAvoidance`（想让列表留白
            // 也随动画收窄），结果是**回路**：
            //   进度变 → 留白变 → LazyColumn contentPadding 变 → 重新测量
            //   → firstVisibleItemScrollOffset 变 → reportNavBarScroll 又写进度 → …
            // 表现就是用户报的「偶尔下滑或上滑，组件都会弹一下」，到底部越界时最重
            // （那时候 `overScrollVertical` 的阻尼也在动，两个扰动叠加）。
            //
            // 现在分工明确：留白稳定 → 不参与回路；悬浮层**只在绘制阶段位移**
            // （见 `LocalNavBarProgress` 的消费方），所以"组件跟着滑下去"的观感仍然存在。
            // ⚠️ 同一段位移不能既从这里出去、又在页内各自 `graphicsLayer` 一次，
            // 那会变成两倍 —— 位移只由页内那一处负责。
            //
            // ⚠️⚠️ `LocalNavBarProgress` / `LocalNavBarTarget` / `LocalNavBarTravelPx`
            // 必须**和留白一起**提供在这一层：各页（对话 / 终端 / 文件）是这里的内容子树，
            // 只在别处提供的话它们读到的是 `compositionLocalOf` 的**默认实例**，
            // 写进去没有任何人读（这个坑已经踩过一次：`LocalNavBarScrollHidden`
            // 曾经只提供给悬浮栏槽位，导致「下滑隐藏」在页面上从未生效过）。
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
                    // 三屏共用的隐藏手势。挂在内容区**外层**，所以对话 / 终端 /
                    // 文件都生效，且**不依赖列表能不能滚** —— 会话很短、终端没输出、
                    // 目录里只有几个文件时，`LazyListState` 的 offset 根本不会变。
                    //
                    // 主驱动是列表滚动（见各页的 `reportNavBarScroll`），
                    // 手势只用来补"这一屏根本不能滚"的情况。
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

            // ---- 非对话面板的反馈条 ----
            // 对话页自己那条在输入器上方（MessageBar）；文件 / 终端页没有它，于是
            // 「删除失败」「已复制 N 项」这类消息在屏幕上根本看不到。这里补在底部
            // Tab 栏之上。宽屏不挂：那时对话面板常驻，MessageBar 已经在显示了。
            if (!wide && state.tab != WorkspaceTab.CHAT) {
                PageFeedback(
                    message = state.message,
                    isError = state.messageIsError,
                    onDismiss = viewModel::clearMessage,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        // 8dp 的固定底距走 padding（组合期常量，不随滚动变）；
                        // 随进度变化的那一段走 `offset { }` —— 布局阶段求值，
                        // 滚动时不触发重组（见上面 `workspaceBottomAvoidance` 的说明）。
                        // progress=0（胶囊可见）时上移让开胶囊，progress=1（胶囊收起）时回落到底边距。
                        .padding(bottom = 8.dp)
                        .offset {
                            val visibleAvoidance = workspaceBottomAvoidance.toPx() *
                                (1f - navBarHideProgressState.value)
                            IntOffset(0, -visibleAvoidance.toInt())
                        },
                )
            }

            // ---- Overlay 系列 ----
            // 弹窗挂载与模态模糊背景在 OverlayHost.kt。
            //
            // ---- 浮层宿主（主 Scaffold 这一份）----
            //
            // 只在**抽屉关闭**时组合：抽屉打开时浮层由抽屉内部那一份负责
            // （见函数末尾 `ZhiSideDrawer(overlayHost = …)`）。
            // 两处同时存在会让同一个对话框被两个宿主各画一次。
            //
            // 为什么抽屉里那份能盖住抽屉：`ChoicePickerOverlay` 传了
            // `renderInRootScaffold = false`，走的是**离调用点最近**的
            // `LocalPopupStates` —— 也就是抽屉自己那个 Scaffold 的宿主，
            // 它的 popupHost 在 `ScaffoldLayout` 里最后 place。
            if (!state.sidebarOpen) {
                ZhiOverlayHost(state = state, viewModel = viewModel, glassMain = glassMain)
            }



            // ---- 全局调试浮层（debug 构建的 UI 调试页里打开）----
            // 画在最后 = 压在所有东西之上，但只在自己那块矩形里响应手势，
            // 其余事件原样透传给下面的工作区。
            //
            // `BuildConfig.DEBUG` 是硬门控：唯一能把它打开的地方是 debug 独有
            // 的「UI 调试」页，这里再挡一道 —— 万一将来有人把开关挪到别处，
            // 也不会有发布包里冒出调试浮层这种事。
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

    // ---- 悬浮底栏槽位（窄屏；独立于 Scaffold，避免产生全宽黑色占位层）----
    //
    // 这个槽位里可能出现**两块**东西：悬浮导航胶囊，和文件选择工具栏。
    // 它们不能同时出现 —— 两者的宽度、位置、圆角语言都一样，叠在一起就是一团。
    // 所以按"选择模式"切换，并让它们走同一组形变过渡：
    // 底栏淡出下沉、工具栏淡入落定，看起来像是同一块东西换了个内容。
    //
    // ⚠️ `fileSelectionActive` 声明在 Scaffold **之前**：
    // 固定底栏那份形变在 `bottomBar` 槽位里也要读它。
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
            // 它一打开就把后面的对话流整个盖住，用户反馈「侧栏有点太宽泛了」。
            // 比例 0.76 兜住小屏：360dp 的机器上是 274dp，不至于挤掉 24dp 图标。
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
                    // ⚠️ **列表优先**：本轮按住期间列表已经滚过了，就完全不碰进度。
                    // 否则同一段手指位移会被列表和手势各加一次，
                    // 胶囊走两倍距离 —— 用户看到的就是"抬起再滑会弹一下"。
                    //
                    // 让路时仍要更新锚点：等列表停下来，手势从那一刻的位置继续，
                    // 不会把让路期间攒下的位移一次性补上。
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
