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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.snap
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.model.WorkspaceTab
import com.zhizhu.zhicode.compose.model.WorkspaceUiState
import com.zhizhu.zhicode.compose.state.WorkspaceViewModel
import com.zhizhu.zhicode.compose.ui.panes.FilesPane
import com.zhizhu.zhicode.compose.ui.panes.TerminalPane
import com.zhizhu.zhicode.compose.ui.debug.ZhiFrameTrace
import top.yukonga.miuix.kmp.utils.springAnimateToPage

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
                state = TopBarState.from(state),
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
                    // 测量点必须在 selectTab **之前**：我们要的是"点击 → 第一帧"，
                    // 放在后面就变成"状态已改 → 第一帧"，那个数测不出滞后（见 FrameTrace）。
                    onSelect = { tab ->
                        ZhiFrameTrace.begin("tab:${tab.name}")
                        viewModel.selectTab(tab)
                    },
                )
            }
            // ---- 副栏换面板：与窄屏用**同一套**机制 ----
            //
            // 原来是 `rememberSaveableStateHolder` + `PaneHost` 内部的 `when (tab)`：
            // 切一次页签就销毁离场面板、重建入场面板 —— 终端要把原生 `AndroidView`
            // 重新挂上去、文件面板要重建整棵列表。这与窄屏当初那组实测数据里
            // `avg 27~53ms / max 458.5ms` 是同一个病（单帧重建），只是在宽屏上
            // 没被单独量过。
            //
            // 现在照窄屏已验证的做法：`HorizontalPager` 保留相邻页的组合，
            // 转场交给官方那条弹簧 —— 由 `springAnimateToPage` 驱动
            // （`PagerNavigationSpringSpec`），与窄屏同一句、同一条曲线。
            //
            // 两个必须显式给出的参数：
            //  · `userScrollEnabled = false`：副栏的页签是**顶栏那一行**，
            //    面板本身不该能被手指横滑（滑动与"列表横向手势"会打架）。
            //    ⚠️ 窄屏本轮**也关掉了**，但理由与手势无关，而是**唯一真源**：
            //    手指横滑能改页码却改不了 `state.tab`，两者一对不上就会出现
            //    「标签栏高亮对话、内容却是终端」（窄屏那段注释里有实测症状）。
            //  · `beyondViewportPageCount = secondary.size`：三页都预先组合并摆放，
            //    切换那一刻不再付首次测量的钱（窄屏那段注释里有实测依据）。
            //
            // （原先这里还有第三个参数 `flingBehavior(snapAnimationSpec = …)`：它只服务
            //   "手指拖拽之后的回弹吸附"，横滑关掉之后就是死配置，已随窄屏一起去掉。）
            //
            // `rememberSaveableStateHolder` 仍然保留，但**挪进每一页内部**：
            // 它原本解决的是"面板被销毁后还能记住滚动位置"，现在面板不销毁了，
            // 它改为兜住"页面被回收（内存压力下 Pager 会丢远处页）后滚动位置不丢"。
            // 直接删掉会因为这份能力变少而被 SidebarMetricsTest 一类守卫问起。
            val secondaryTabs = remember { secondary }
            val secondaryPagerState = remember(secondaryTabs) {
                PagerState(currentPage = secondaryTabs.indexOf(state.tab).coerceAtLeast(0)) {
                    secondaryTabs.size
                }
            }
            // 与窄屏同款：状态是唯一真源，页码用**动画**跟随它。
            // 不用 `requestScrollToPage` —— 它是瞬时的（"下一次重测量时直接到位"），
            // 点了会硬切；窄屏那段注释里有完整的来源与取舍。
            LaunchedEffect(state.tab) {
                val target = secondaryTabs.indexOf(state.tab)
                if (target >= 0) secondaryPagerState.springAnimateToPage(target)
            }
            val secondaryPaneStateHolder = rememberSaveableStateHolder()
            HorizontalPager(
                state = secondaryPagerState,
                modifier = Modifier.weight(1f),
                // 宽屏本来就关掉了横滑（见上方），所以 flingBehavior 是死配置 ——
                // 与窄屏同款，随横滑一起去掉。
                userScrollEnabled = false,
                beyondViewportPageCount = secondaryTabs.size,
                pageContent = { page ->
                    val tab = secondaryTabs[page]
                    secondaryPaneStateHolder.SaveableStateProvider(tab.name) {
                        PaneHost(
                            state = state,
                            viewModel = viewModel,
                            isDark = isDark,
                            glass = glass,
                            tabOverride = tab,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                },
            )
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
    // ## 为什么换成 `HorizontalPager`（R6）
    //
    // 原来是 `AnimatedContent` + `slideIn/OutHorizontally` + `fadeIn/Out`，四件套叠起来的
    // 结果是**每一帧都在超额**。改前的实测（debug 沙箱，40 帧窗口）：
    //
    // ```
    // tab:TERMINAL tapToFirstFrame=14.4ms span=2117.4ms avg=52.9ms max=458.5ms janks=8
    // tab:CHAT     tapToFirstFrame=10.2ms span=1108.6ms avg=27.7ms  max=200.0ms janks=7
    // ```
    //
    // 关键读数有两个，各自指向不同的病因：
    //  · `tapToFirstFrame` 只有 10~14ms（**不到一帧**）→ 起手并不慢，
    //    所以「导航栈要等协程派发」那个猜测不是主因；
    //  · `avg` 27~53ms、`max` 200~458ms → **每一帧都超额，其中一帧超了 27 倍**。
    //    那几帧在干什么：`AnimatedContent` 切换时**销毁离场面板、重建入场面板** ——
    //    终端要把原生 `AndroidView` 重新挂上去、文件面板要重建整棵列表。
    //
    // 换成官方示例（`example/.../AppContent.kt`）的做法后这两件事一起解决：
    //  · `HorizontalPager` **保留**相邻页面的组合（默认就保留一页），
    //    切过去时终端/文件面板早就建好了，不再有那 200~458ms 的重建帧；
    //  · 转场交给 `PagerNavigationSpringSpec`（官方那一条 `spring(stiffness=322.2,
    //    dampingRatio≈0.9, visibilityThreshold=0.5)`），比自己拼四条 spec 更少更一致。
    //
    // 顺带删掉 `SaveableStateHolder`：以前是为了"面板被销毁后还能记住滚动位置"，
    // 现在面板根本不销毁，留着就是多余的间接层。
    val tabs = WorkspaceTab.entries
    // ⚠️ 用 `remember` + 直接构造，**不用** `rememberPagerState`。
    //
    // `rememberPagerState` 内部是 `rememberSaveable`：进程/Activity 重建后它会把
    // **上次保存的页码**恢复回来，而 `state.tab` 是另一条恢复路径（VM / 磁盘设置）。
    // 两者一旦不一致（实测过：导航条高亮「对话」、Pager 却停在第 2 页显示文件面板），
    // 页面内容就和导航条对不上 —— 这是双真源必然的后果。
    //
    // 现在只留 `state.tab` 一个真源：页码在每次组合开始时**从它推导**，
    // 之后由下面的 effect 跟随它的变化。代价是重建后不保留"滑到一半"的位置，
    // 而那个位置本来也不该越过 `state.tab` 说话。
    val pagerState = remember(tabs) {
        PagerState(currentPage = tabs.indexOf(state.tab).coerceAtLeast(0)) { tabs.size }
    }
    // 状态（ViewModel）是唯一来源：点击页签改 state.tab，再由这里把 pager 跟过去。
    //
    // ---- 为什么是「动画跟随」，而不是当初那个 `requestScrollToPage` ----
    //
    // 这里一度用的是 `pagerState.requestScrollToPage(target)`，为的是绕开一条死等：
    // `scrollToPage` 是 **suspend** 的，内部 `awaitScrollDependencies()` 要等 Pager 的
    // layout 依赖就绪，应用空闲时不产帧就干等（逐段打时间戳实测过 263.7ms，其中它占 212ms），
    // 而 `requestScrollToPage` 非挂起、由 Pager 在本次组合之后的布局阶段自己完成。
    //
    // ⚠️ 但那个 API 的语义是**瞬时**的，这一点当时判断错了。AOSP `PagerState.kt` 的文档：
    //
    //   | Requests the [page] to be at the snapped position **during the next remeasure** …
    //   | Any scroll in progress will be cancelled.
    //
    // 实现就是 `snapToItem(page, offsetFraction, forceRemeasure = false)` —— 直接到位。
    // 而 `snapAnimationSpec` 只管**手指拖拽之后**的回弹吸附，从不管程序化请求，
    // 所以那条弹簧对点击是死代码。观感就是用户报的「TAB 栏切换是没有动画的」。
    //
    // 现在走 Miuix 官方那条（`PagerGestureUtils.kt` 的注释就写着 "**Animates** to [target]…"，
    // 官方示例 `TabRowSection.kt:63` 用的也正是它）：`springAnimateToPage`。
    //
    // 首帧与进程重建都**不会误播**：`PagerState(currentPage = tabs.indexOf(state.tab))`
    // 一上来就在目标页，动画距离为 0，等于不播。
    //
    // 代价照实说：`LaunchedEffect` 那一跳约一帧（上面那张表里量到 39ms）。
    // 这次要的就是动画，动画本身 200~300ms，那一帧可以忽略。
    ZhiFrameTrace.stamp("compose-enter")
    LaunchedEffect(state.tab) {
        val target = tabs.indexOf(state.tab)
        if (target >= 0) {
            ZhiFrameTrace.stamp("animate-to-$target")
            pagerState.springAnimateToPage(target)
        }
    }
    // 诊断：量「手指抬起 → 页面真正切完」用了多久。
    //
    // 这是用户能直接感受到的那个数（他说的"点击之后过了大约 0.1~0.2s 才切换页面，
    // 此时切换是流畅的"就是它），而帧统计给不出来 —— 帧统计只说每帧多长。
    // ⚠️ 起点必须是 ACTION_UP：onClick 在抬起后才触发，拿 ACTION_DOWN 当起点
    // 测到的只是"按住时长"，此前据此得出过完全错误的结论。
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .collect { settled ->
                ZhiFrameTrace.stamp("settle-$settled")
                ZhiFrameTrace.note("pager/settled page=$settled")
                ZhiFrameTrace.markPageSettled()
            }
    }
    // 诊断：`currentPage` 变化（内容真正开始移动）与 `settledPage`（Pager 记账完成）
    // 是**两个不同的时刻**。用户看得见的是前者；后者只是内部标记。
    // 分开量才能判断剩下的几十毫秒到底是不是用户能感知的。
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .collect { page -> ZhiFrameTrace.stamp("current-$page") }
    }
    // 动效走**官方那一条弹簧**（`PagerNavigationSpringSpec`；官方示例 `TabRowSection.kt:63`）。
    //
    // 这里一度改成 `snap()` 瞬时落定，因为实测「抬起 → 切完」要 263.7ms。但那 263.7ms
    // 里弹簧只占 116ms，真正的病根是另外两处：面板在**重建**（单帧 458.5ms）与请求是
    // **挂起**的（`scrollToPage` 要等 layout 依赖，212ms 死等）。两处都已修掉，帧率追平
    // 官方（release 实测 8.3ms / janks 0）—— 动画这时才是加分项，而不是一段空白等待。
    // 这也正是用户要的（他另外那个 App 的页签就是有切换动画的）。
    //
    // ⚠️⚠️ 但"弹簧"这个词一度让人以为点击**早就在动**了。**不是。** 当年为了绕开那 212ms
    // 死等，请求换成了非挂起的 `requestScrollToPage`，而它的语义是瞬时的
    // （"下一次重测量时直接到位"）—— 于是这条弹簧对点击成了**死代码**，用户看到的是硬切。
    // 现在动效来自上面那句 `springAnimateToPage`，用的仍是这条 spec。
    //
    // ⚠️ 它**不经过** `flingBehavior`：那条 `PagerDefaults.flingBehavior(…)` 只服务
    // "手指拖拽之后的回弹吸附"，既然横滑已关掉就是死配置，已删。反过来说 ——
    // 要恢复横滑，必须把它**一起**加回来，否则松手不会吸附。
    //
    // ⚠️ 若哪天帧数据退化（avg 明显高于官方 8.3~15.4ms，或 max 回到数百 ms），
    // 第一件该做的就是把动效换回瞬时：动画只有在**跑得动**的时候才是加分项。

    Column(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            // 不接受横滑切页 —— 这一条与上面「唯一真源」是**同一个决定**。
            //
            // 实测过的症状：在对话页左右滑，能滑动，但**切不过去**。原因是组合里那段同步
            // 每次组合都跑，手指把 pager 推到第 1 页而 `state.tab` 仍是「对话」，
            // 于是下一帧就把它拽回去。
            //
            // 更要紧的是：换成「按 state.tab 播动画」之后，横滑会**真的**停在第 1 页 ——
            // 那时标签栏高亮「对话」、内容却是「终端」，双真源当场对不上。
            // 页码只能由 `state.tab` 驱动，所以手势就删掉（用户也是这么要求的）。
            //
            // 顺带的好处：面板很重（终端里有原生 `AndroidView`），拖拽过程中来回测量本来就贵。
            userScrollEnabled = false,
            // 相邻页留在组合里：这就是"不再重建面板"的关键。
            //
            // 取值 = 页数（而不是 1）：实测切到终端页时，`currentPage`/`settledPage`
            // 要比请求晚 **116~122ms** 才更新，而且同期帧统计里正好有一条 `max=116.7ms`
            // —— 那不是"缺帧"，是**主线程被占住**：目标面板要首次测量布局
            // （终端面板里是原生 `AndroidView` + Termux 会话），把这一下堵在点击路径上了。
            // 只有 1 时相邻页只是被组合、未必被摆放，所以切换那一刻才付这笔钱。
            // 取页数让三页都在切换前就完成组合与摆放，把成本挪到启动阶段。
            beyondViewportPageCount = tabs.size,
            pageContent = { page ->
                // ⚠️ 这里**绝对不要**放 `ZhiFrameTrace.note` 之类的埋点：
                // `sink` 会往对话流写消息 → 改状态 → 重组 → 再记 → 无限重组。
                // 实测过一次，日志里 `pager/compose` 刷屏，等于自己造了一个永久卡顿源。
                when (tabs[page]) {
                    WorkspaceTab.CHAT -> ChatArea(
                        state = state,
                        viewModel = viewModel,
                        wide = false,
                        modifier = Modifier.fillMaxSize(),
                        glass = glass,
                    )
                    // ⚠️ 这两个面板（终端 / 文件）必须**自己顶开顶栏高度**。
                    //
                    // 规则：Scaffold 的 content lambda **有意丢掉了 `padding.top`**
                    // （为了让顶栏 blur 有内容可采样），所以内容实际从 y=0 铺满 ——
                    // 任何不自己顶开的面板，头部都会被顶栏盖住：面板标题、向上按钮、
                    // 面包屑连同第一行一起消失。用户截图里"上面有一行被裁掉"就是这个。
                    //
                    // 对话面板**不**在这里补：它的留白由 ChatList 的 topInset 负责，
                    // 因为它需要能滚到顶栏下面被模糊。
                    else -> PaneHost(
                        state = state,
                        viewModel = viewModel,
                        isDark = isDark,
                        glass = glass,
                        tabOverride = tabs[page],
                        modifier = Modifier.fillMaxSize().padding(top = TopBarInsetWithTabs),
                    )
                }
            },
        )
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

/**
 * 工作区标签 → 图标。顶栏（TopBar.kt）与宽屏按键组共用，所以是 internal。
 *
 * `@Composable`：图标改成 `Painter` 之后要经 `painterResource` 解析，
 * 而那一步只能发生在组合里（它读 `LocalContext` 并按 id 缓存）。见 [ZhiIcons]。
 */
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

/**
 * 顶栏**自己消费掉的窗口 inset**（就是状态栏那一条）。
 *
 * ## 为什么这个偏移量必须来自窗口，不能写成常量
 *
 * `MainActivity` 调了 `enableEdgeToEdge()`，内容从屏幕 y=0 起排（Miuix Scaffold 的
 * `bodyContentPlaceable.place(0, 0)`）；而 `SmallTopAppBar` 内部是
 * **无条件** `.windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))`
 * ——注意 `defaultWindowInsetsPadding = false` 只关掉**横向**的挖孔与导航栏，
 * 纵向那一条关不掉（见 Miuix `TopAppBar.kt` 的 `SmallTopAppBarLayout`）。
 *
 * 于是顶栏真实高度 = 状态栏高度 + 52 + Tab 行。这里原先是
 * `52.dp + WorkspaceTabRowHeight + TopBarTabRowPadding` —— **漏了状态栏**，
 * 所有"自己顶开顶栏"的面板都因此短了一截：滚到最顶上时，第一行内容
 * （对话的首条消息、终端的首行、文件面板的标题）正好被顶栏盖住，
 * 表现就是用户说的「划到最顶上，顶部栏把这些东西全部遮盖了」。
 *
 * 各家状态栏高度并不相同（挖孔屏更高），所以只能实测。
 */
@Composable
private fun topBarWindowInset(): Dp =
    with(LocalDensity.current) {
        WindowInsets.systemBars.only(WindowInsetsSides.Top).getTop(this).toDp()
    }

internal val TopBarInsetWithTabs: Dp
    @Composable get() = 52.dp + WorkspaceTabRowHeight + TopBarTabRowPadding + topBarWindowInset()

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
            root = state.fileRoot,
            onSwitchRoot = viewModel::switchFileRoot,
            draft = state.fileDraft,
            onStartEdit = viewModel::startEditingFile,
            onDraftChange = viewModel::updateFileDraft,
            onSave = viewModel::saveFile,
            onCancelEdit = viewModel::cancelEditingFile,
            nameForm = state.fileNameForm,
            // 只有一个「新建」入口：建文件还是建文件夹由弹窗里按下的那个按钮决定
            // （见 WorkspaceViewModel.submitFileNameForm 的 KDoc）。
            onNewEntry = viewModel::newFileForm,
            onRename = viewModel::renameForm,
            onNameDraftChange = viewModel::updateFileNameDraft,
            onSubmitName = viewModel::submitFileNameForm,
            onCancelName = viewModel::cancelFileNameForm,
            deletePrompt = state.fileDeletePrompt,
            onConfirmDelete = viewModel::confirmDelete,
            onCancelDelete = viewModel::cancelDelete,
            sharedStorageGranted = state.sharedStorageGranted,
            onGrantSharedStorage = viewModel::openSharedStorageSettings,
            // 长按多选。
            selection = state.fileSelection,
            onLongPressEntry = viewModel::longPressFileEntry,
            onToggleEntry = viewModel::toggleFileSelection,
            onToggleSelectAll = viewModel::toggleSelectAllFiles,
            onClearSelection = viewModel::clearFileSelection,
            onRenameSelected = viewModel::renameSelectedEntry,
            onDeleteSelected = viewModel::deleteSelectedEntries,
            onAttachSelected = viewModel::attachSelectedEntries,
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
        state = SidebarState.from(state),
        onNewSession = viewModel::newSession,
        onOpenSession = viewModel::openSession,
        onSessionActions = viewModel::showSessionActions,
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
