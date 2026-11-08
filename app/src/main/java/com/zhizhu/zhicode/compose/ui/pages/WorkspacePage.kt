package com.zhizhu.zhicode.compose.ui.pages

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
import com.zhizhu.zhicode.compose.ui.pages.overlays.ZhiDebugHud
import top.yukonga.miuix.kmp.basic.Scaffold

/**
 * 工作区整页（导航栈的 root）。
 *
 * 这一块原先直接内联在 `AppScaffold.kt` 的 `entry<AppKey.Workspace>` 里，与主题、
 * 导航栈、Activity Result 启动器挤在同一个函数中。拆出来之后：
 *
 * - `AppScaffold` 只负责「主题 + 栈 + 每个页面的 entry」，不再关心工作区内部结构；
 * - 本页只画工作区（顶栏 / 面板 / 浮层 / 抽屉），状态与回调继续来自
 *   [WorkspaceViewModel] 与 [WorkspaceUiState]，不新增任何业务真源。
 *
 * 内容逐字搬移，行为与拆分前一致 —— 包括下面这些**不能顺手改**的约束。
 *
 * ## 顶栏在 `topBar` 槽位
 *
 * 顶栏迁入 Miuix [Scaffold] 的官方槽位后，「+」下拉菜单等 Overlay 弹层的锚点坐标
 * 与根 MiuixPopupHost 的窗口原点才一致；此前它是内容里手排的悬浮 Box，
 * 菜单整体会飞到锚点上方。
 *
 * ## 内容丢掉 `padding.top` 是刻意的
 *
 * 顶栏 blur 需要内容从它**底下**滚过才有东西可采样，所以内容 Box 从 y=0 铺满、
 * 各面板自己在头部留白。代价是：任何从 y=0 开始画、又不自己顶开的面板，头部都会
 * 被顶栏盖住。所以每个面板必须二选一 ——
 *  · 对话面板：用 ChatList 的 topInset（**滚动内边距**，它要能滚过顶栏）；
 *  · 其余面板：用 `TopBarInsetCompact` 的 **padding** 顶开（见 `WorkspaceLayouts` 的 PaneHost）。
 * **新加面板时最容易漏的就是这一步。**
 *
 * ## 抽屉画在 Scaffold 之外
 *
 * Miuix [Scaffold] 的绘制顺序是 bodyContent → topBar → popup，放在内容里的抽屉
 * 永远被顶栏压住。
 *
 * @param glass 捕获「对话列表」的玻璃层，给底部悬浮的任务卡与输入器用。
 * @param glassMain 捕获「整个工作区」的玻璃层，给顶栏 / 抽屉 / 弹窗用。
 *   两者必须分开：输入器就在对话区里面，共用整屏捕获会出现自我叠影。
 */
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
    /*
     * 键盘是否可见（含动画过程）。
     *
     * ⚠️ 取 `imeVisible` 而不是 `settledLiftPx > 0`：后者要等键盘动画**结束**才翻转，
     * 于是动画期间让位量还是旧值，扩展键栏 / 输入器与键盘之间会裂开一条缝（用户截图）。
     * `imeVisible` 在动画开始那一刻就翻真，且每次开合只变两次，可以安全在组合期读。
     */
    val imeOpen = com.zhizhu.zhicode.compose.ui.LocalImeMotion.current.imeVisible
    /*
     * 供各页当作滚动留白的值；非悬浮模式**或键盘可见时为 0**。
     *
     * ⚠️ 键盘弹起时必须归 0：悬浮胶囊在最底部，键盘弹出后它要么被键盘整个盖住、
     * 要么被抬出可视区 —— 两种情况都不需要再给内容留出它的高度。若仍然留着，
     * 键盘上方就会多出一整块空白（用户截图里终端扩展键栏与键盘之间、
     * 输入器与键盘之间那条大缝）。
     */
    val floatingNavInset = if (showBottomNavigation && themeSettings.tabBarFloating && !imeOpen) {
        floatingNavigationAvoidance + FloatingNavContentGap
    } else {
        0.dp
    }
    /*
     * 胶囊隐藏进度。
     *
     * 分两层，缺一不可：
     *  · [navBarHideTarget] 是**目标**，由滚动与手势直接写（可能一次跳几像素，
     *    甚至因为列表重置而跳一大段 —— 那部分已在 `reportNavBarScroll` 里挡掉）；
     *  · [navBarHideProgress] 是**显示值**，用一条短 tween 追目标。
     *
     * 为什么要追一次：用户要求「滑动幅度就算很小也要有动画」。
     * 直接用目标值的话，一像素的滚动就是一像素的位移，看起来是硬跳、没有过渡；
     * 追一条 120ms 的曲线之后，极小的滚动也会走出一段平滑位移。
     * 时长刻意取很短 —— 它是"补间"而不是"动画"，太长就跟不上手指了。
     */
    val navBarHideTarget = remember { mutableFloatStateOf(0f) }
    // 列表滚动计数器：手势靠它判断"这一下归列表管"，避免同一段位移被算两次。
    // ⚠️ 用 `IntArray(1)` 而不是 `mutableStateOf(0)`：这个计数器由滚动**逐像素**自增，
    // 用 State 会每次都走一遍快照写入/失效分发；而它只在手势回调里被读（不进组合），
    // 普通容器就够，省掉这部分开销。
    val navBarScrollTick = remember { IntArray(1) }
    /*
     * 隐藏进度的**平滑层**。
     *
     * ⚠️⚠️ 这里曾经是 `animateFloatAsState(targetValue = navBarHideTarget.floatValue)`。
     * 那是**上滑卡顿的主因**：`animateFloatAsState` 的 `targetValue` 在**组合期**读，
     * 而 target 由滚动逐像素写入 —— 于是"滚动每一像素 → 整个 WorkspacePage 重组一次"。
     * 一屏里它下面是对话列表、顶栏、悬浮层三大块，全部跟着白重组。
     *
     * 现在改成 `Animatable` + 协程里追目标：
     *  · target 变化**不进组合**（只在 flow 里被读）；
     *  · 动画值通过 [Animatable.asState] 暴露出去，消费方**在绘制阶段**读
     *    （`graphicsLayer { }` / `offset { }`）；
     *  · 结果是：滚动期间 WorkspacePage 一次都不重组，只有绘制属性在变。
     *
     * 时长 120ms 取短的用意是"补间"而不是"动画"：太长会跟不上手指。
     */
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
    /*
     * 非对话面板的反馈条要跟着胶囊一起下沉。
     *
     * ⚠️ 这里**不在组合期算 Dp**。以前写的是
     * `workspaceBottomAvoidance * (1f - navBarHideProgress)`，那会在组合期读进度，
     * 每像素滚动都把这一层（连同页面）重组一次。改成在 `offset { }` 里读
     * （布局阶段，且只失效布局、不重组），见下面 `PageFeedback` 处。
     */
    val anchoredActionMenu = state.choicePicker?.isActionMenu == true
    /*
     * 底栏 / 悬浮胶囊**不会**因为弹窗打开而消失或让位。
     *
     * 这里曾经有一个 `modalOverlayOpen` 变量，含义是「当前有模态浮层」，
     * 效果是把 NavigationBar 整块移除、把内容底距归零、把悬浮胶囊摘掉。
     *
     * 问题是那会造成**整块跳动**：一打开任何 dialog 或 bottom sheet，
     * 屏幕下方那排东西就凭空消失，内容同时往下窜；关掉又跳回来。
     * 用户报的「打开所有 dialog 都会隐藏 NavigationBar，bottomsheet 也会」
     * 就是这条。而模态的本意只是"压暗并拦住输入"，不需要真的移除任何东西 ——
     * Miuix 的窗口本身自带压暗层，底栏被压暗即可。
     *
     * 所以现在：底栏照常渲染、内容照常让位、悬浮胶囊照常存在，
     * 由浮层自己去盖住它们。
     */
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
                PageBlurBar(backdrop = pageNavigationBackdrop) {
                        NavigationBar(
                            modifier = Modifier.align(Alignment.BottomCenter),
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
                    onContextClick = {
                        viewModel.requestComposerText("/usage")
                        viewModel.send()
                    },
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
                        padding.calculateBottomPadding() +
                            WindowInsets.systemBars.union(WindowInsets.displayCutout)
                                .only(WindowInsetsSides.Bottom)
                                .asPaddingValues()
                                .calculateBottomPadding()
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
                /*
                 * 固定底栏的高度（悬浮模式下是 0，因为那时内容本来就不让位）。
                 *
                 * 给对话页用：键盘弹起时输入器要往上抬，但**不能**抬到屏幕最底
                 * —— 它得停在 `max(键盘顶边, 底栏上沿)`。而底栏上沿 = 屏幕底 −
                 * 这个值，所以对话页必须知道它。
                 *
                 * 之前对话页拿不到这个值，只能"键盘可见就把底栏让位归零"，
                 * 于是键盘**收起动画**期间（此时抬起量已归零、但还没判定为收起）
                 * 输入器直接落到屏幕最底、压在 NavigationBar 上（用户截图）。
                 */
                LocalBottomBarInset provides padding.calculateBottomPadding(),
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
                        .padding(bottom = 8.dp)
                        .offset {
                            val hidden = workspaceBottomAvoidance.toPx() *
                                navBarHideProgressState.value
                            IntOffset(0, hidden.toInt())
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
    // 所以按"选择模式"切换，并让它们走同一组滑入/滑出动画：
    // 胶囊向下退、工具栏从下方升起来，看起来像是同一块东西换了个内容。
    val fileSelectionActive = !wide &&
        (state.fileSelectionMode || state.fileSelection.isNotEmpty())
    val floatingNavSlotAvailable = !wide &&
        themeSettings.tabBarPositionValue == TabBarPosition.BOTTOM &&
        themeSettings.tabBarFloating
    val showFloatingNav = floatingNavSlotAvailable && !fileSelectionActive
    /*
     * 文件选择工具栏的底距 —— 必须按底栏形态分开算。
     *
     * ⚠️ 这里曾经写死 `FloatingNavBarGap + navigationBarBottomInsetDp`，
     * 那是**悬浮胶囊**的底距。固定底栏模式下 NavigationBar 自己占了 64dp，
     * 工具栏按同一个底距定位就正好和它**重合**，表现为「工具栏盖在 NavigationBar 上层」。
     *
     * 正确行为是叠在底栏之上：
     *  · 悬浮胶囊模式 = 胶囊高度 + 间隙 + 系统栏（工具栏取代胶囊，同位置）；
     *  · 固定底栏模式 = 底栏高度 + 系统栏 + 一点缝（工具栏浮在底栏上沿）。
     *  · 顶栏模式（底栏不显示）= 只让系统栏。
     */
    val fileToolbarBottomPadding = when {
        !showBottomNavigation -> navigationBarBottomInsetDp + FloatingNavBarGap
        themeSettings.tabBarFloating -> FloatingNavBarHeight + FloatingNavBarGap + navigationBarBottomInsetDp
        // NavigationBarDefaults.ItemHeight = 64dp（Miuix 实测值），
        // 再加 8dp 呼吸缝，工具栏就不会贴着底栏图标。
        else -> 64.dp + navigationBarBottomInsetDp + FloatingNavBarGap
    }
    // 选择工具栏与底栏形态**无关**：顶栏模式、固定底栏模式下它照样要出来。
    // 只有"窄屏 + 没开模态浮层"是硬条件（宽屏用侧栏与行内动作，不需要它）。
    if (floatingNavSlotAvailable || fileSelectionActive) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            // 这里曾有 `density` / `reservedPx` / `navInsetPx` 三个局部量，
            // 只服务于上面那个已删除的 `nav-probe` 探针。一并删掉，
            // 免得留下"看起来有用、其实没人读"的值。
            AnimatedVisibility(
                visible = showFloatingNav,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                LiquidWorkspaceBar(
                    selected = state.tab,
                    onSelect = { tab ->
                        ZhiFrameTrace.begin("tab:${tab.name}")
                        viewModel.selectTab(tab)
                    },
                    backdrop = floatingNavigationBackdrop,
                    blurEnabled = floatingNavigationBackdrop != null,
                    modifier = Modifier
                        // 下滑隐藏：胶囊整体滑出屏幕底部（`graphicsLayer` 平移而不是
                        // 改变布局高度 —— 改高度会让下方的 `Shapes`/阴影每帧重新测量，
                        // 滚动时直接掉帧）。
                        //
                        // ⚠️⚠️ 两处曾经写错，都会让"隐藏"看起来像坏了：
                        //
                        // 1. **没有动画**。原来是 `translationY = if (hidden) exit else 0f`，
                        //    硬跳变 —— 胶囊是"啪"地消失/出现。现在走
                        //    `animateFloatAsState`，时长与曲线取 [ZhiMotion] 里
                        //    "退出"那条（与底栏收起、面板退出同一族）。
                        //
                        // 2. **移出量少了阴影**。原来只算 `高度 + 间隙 + 系统栏`，
                        //    正好把 Body 送到屏幕外，但 `LiquidWorkspaceBar` 上挂着
                        //    `dropShadow(radius = 10.dp)` —— 阴影是从边缘向外扩散的，
                        //    本体移出去之后它还剩一截露在屏幕里，用户看到的就是
                        //    "没藏好，还是漏出来一点"。这里补 [NavBarShadowRoom]。
                        .graphicsLayer {
                            val exit = (FloatingNavBarHeight + FloatingNavBarGap +
                                navigationBarBottomInsetDp + NavBarShadowRoom).toPx()
                            // 只在**绘制阶段**读进度：滚动期间这一层不重组。
                            translationY = exit * navBarHideProgressState.value
                        },
                )
            }
            AnimatedVisibility(
                visible = fileSelectionActive,
                // ⚠️ 过渡动画必须**明显大于**胶囊那条：工具栏是"整块面板浮上来"，
                // 用 8dp 的滑入看不出发生过什么。这里用「上滑 + 淡入 + 缩放」三合一，
                // 时长取 [ZhiMotion] 的进入族，退出略快（退出不该拖沓）。
                enter = slideInVertically(
                    animationSpec = tween(ZhiMotion.FADE_IN_MILLIS, easing = FastOutSlowInEasing),
                    initialOffsetY = { it },
                ) + fadeIn(ZhiMotion.fadeInSpec) + scaleIn(
                    animationSpec = tween(ZhiMotion.FADE_IN_MILLIS, easing = FastOutSlowInEasing),
                    initialScale = 0.92f,
                ),
                exit = slideOutVertically(
                    animationSpec = tween(ZhiMotion.EXIT_MILLIS, easing = FastOutSlowInEasing),
                    targetOffsetY = { it },
                ) + fadeOut(ZhiMotion.fadeOutSpec) + scaleOut(
                    animationSpec = tween(ZhiMotion.EXIT_MILLIS, easing = FastOutSlowInEasing),
                    targetScale = 0.94f,
                ),
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
                    modifier = Modifier.padding(bottom = fileToolbarBottomPadding),
                )
            }
        }

    }
    /*
     * ---- 侧栏抽屉（画在主 Scaffold **之后**）----
     *
     * 必须排在这里，不能放进主 Scaffold 的 content：`ScaffoldLayout` 把
     * topBar / bottomBar 作为独立槽位在 body **之后** place，content 里的东西
     * 永远被它们压住 —— 表现为抽屉第一行「新会话」被顶栏吃掉。
     *
     * ⚠️ 关闭时 `ZhiSideDrawer` 内部用零尺寸 Modifier，不参与命中测试，
     * 所以不会挡住顶栏的汉堡按钮（这个坑踩过：抽屉关闭仍 `fillMaxSize`
     * 会吃掉全屏点击，「侧栏按钮点了没反应」）。
     *
     * ⚠️ 也不要改成独立 `Dialog` 窗口：窗口 attach 有延迟，进出动画会在
     * 黑屏之后才跑，表现为「侧栏打开/关闭时背景黑化有延迟」。
     *
     * ## 对话框怎么盖住抽屉
     *
     * 抽屉内部自带一个 `Scaffold`（渲染长按菜单用），`ZhiOverlayHost` 通过
     * `overlayHost` 槽位挂进**那个** Scaffold；同时 `ChoicePickerOverlay` 用
     * `renderInRootScaffold = false`，走最近的 `LocalPopupStates`。
     * 于是对话框与长按菜单同源，都画在抽屉之上。
     */
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

/**
 * 胶囊隐藏时额外多移出的距离，用来把**阴影**也一起送出屏幕。
 *
 * `LiquidWorkspaceBar` 上挂的是 `dropShadow(radius = 10.dp)` —— 阴影是从胶囊边缘
 * **向外扩散**的，所以胶囊本体移出屏幕之后，它还剩下最多 10dp 的模糊边露在屏幕里。
 * 只算 `高度 + 间隙 + 系统栏` 会正好把本体送到边缘、把阴影留在里面，看起来就是
 * "没藏好，还是漏出来一点"。
 *
 * 取 24dp 而不是 10dp：`dropShadow` 的 `radius` 与最终模糊半径不是一比一
 * （Skia 会按 sigma 展开约 2~3 倍），留一倍余量才不会在窄屏或高缩放下又露出头发丝。
 */
private val NavBarShadowRoom = 24.dp

/**
 * 手势驱动的"隐藏 / 唤出"底部悬浮栏。
 *
 * ## 为什么不能只靠列表滚动
 *
 * 原来的唯一驱动是 `reportNavBarScroll`，它观察 `LazyListState` 的
 * `firstVisibleItemIndex / ScrollOffset`。这在"内容多到能滚"时很准，但三种很常见的
 * 情况里它**永远不会触发**：
 *
 *  · 会话很短，一屏放得下 → offset 恒为 0；
 *  · 终端还没输出、目录里只有几个文件 → 同上传动为 0；
 *  · 内容顶部被 overscroll 阻尼吃掉时，手指在动而 offset 不动。
 *
 * 用户的话是"即使再不可滑动的地方也要加上这个"。
 *
 * ## 为什么在 `Initial` 段观察而不消费
 *
 * `PointerEventPass.Initial` 是**分发的最早一段**，这里只读 `change.position`，
 * **从不调用 `consume()`**。所以：
 *  · 列表照常滚动、pager 照常横滑、长按菜单照常触发；
 *  · 我们只是"旁听"手指的竖直方向。
 *
 * 只旁听不消费是这里唯一可行的做法 —— 若在父层用 `detectVerticalDragGestures`
 * 去抢手势，对话流就再也滚不动了。
 *
 * ## 方向
 *
 * **手指向下移动 → 隐藏；手指向上移动 → 唤出。**
 *
 * 这是按用户的手感要求定的（"把出入逻辑反过来"）。注意它**与列表回滚的方向相反**：
 * 列表滚动那条驱动已停用，所以两者不会打架。
 *
 * ## 抬手不回弹
 *
 * 用户要求「抬手时保留当前动画进度」：手指抬起来，胶囊就停在那个位置，
 * 既不补齐到端点、也不退回原位。下一次按下从当前位置继续累计
 * （`startProgress` 记的就是抬手时的值）——所以整体读起来是
 * "用手把这块东西推走/拽回来"，而不是"划一下触发开关"。
 */
private fun Modifier.navBarHideOnVerticalDrag(
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
