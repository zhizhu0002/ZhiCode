package com.zhizhu.zhicode.compose.ui.pages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.zhizhu.zhicode.compose.data.TabBarPosition
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import com.zhizhu.zhicode.compose.ui.WorkspaceTabs
import top.yukonga.miuix.kmp.basic.Surface
import com.zhizhu.zhicode.compose.ui.WorkspaceTabRowHeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import com.zhizhu.zhicode.compose.theme.ZhiColors
import com.zhizhu.zhicode.compose.ui.effects.pressSink
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration

import com.zhizhu.zhicode.compose.model.WorkspaceTab
import com.zhizhu.zhicode.compose.model.WorkspaceUiState
import com.zhizhu.zhicode.compose.state.WorkspaceViewModel
import com.zhizhu.zhicode.compose.ui.CompactWorkspace
import com.zhizhu.zhicode.compose.ui.Glass
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
    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        floatingToolbar = {
            com.zhizhu.zhicode.compose.ui.pages.FileSelectionToolbar(
                visible = !wide && (state.fileSelectionMode || state.fileSelection.isNotEmpty()),
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
                        viewModel.onComposerChange("/usage")
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
            modifier = Modifier.fillMaxSize().padding(
                bottom = padding.calculateBottomPadding() +
                    // 底栏模式下内容要给底部标签栏让位，否则最后一行会被它压住。
                    // 数值与上面浮条的实际占位逐步对应：
                    //   标签行 45dp + 浮条内 padding 上下各 4dp + 浮条外 6dp×2。
                    // 贴边形态没有那两圈 padding，所以只让 45dp。
                    if (!wide && themeSettings.tabBarPositionValue == TabBarPosition.BOTTOM) {
                        WorkspaceTabRowHeight + if (themeSettings.tabBarFloating) 20.dp else 0.dp
                    } else {
                        0.dp
                    },
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
                    CompactWorkspace(
                        state = state,
                        viewModel = viewModel,
                        isDark = isDark,
                        glass = glass,
                        pagerState = compactPagerState,
                        )
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
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp),
                )
            }

            // ---- 底部标签栏（窄屏 + 用户选了「底栏」）----
            //
            // 形态**照文件页的选中工具栏**（`FileSelectionToolbar`）：一条独立的
            // 悬浮胶囊，玻璃底 + 1dp 高光描边 + 阴影，左右 18dp 内缩。
            //
            // ⚠️ 上一版这里是"把标签栏搬到底部再叠一层实心色块"：
            //   · 用 `background(surfaceContainer)` 直接填 —— 于是它是一块**不透明**
            //     的方块，与周围毛玻璃的输入器完全不是一族；
            //   · 没有任何位移，位置正好压在输入器上 —— 两者重叠，看起来像
            //     "输入器上面多了一排按钮"。
            // 现在改成真正的浮条：`ActiveSurface` 负责玻璃与描边，
            // `AnimatedVisibility` 负责滑入，位置由它在内容之上单独占一层。
            if (!wide && themeSettings.tabBarPositionValue == TabBarPosition.BOTTOM) {
                AnimatedVisibility(
                    visible = true,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    val floating = themeSettings.tabBarFloating
                    val shape = RoundedCornerShape(if (floating) 28.dp else 0.dp)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = if (floating) 18.dp else 0.dp,
                                vertical = if (floating) 6.dp else 0.dp,
                            )
                            .then(
                                if (floating) {
                                    glassMain.blur(Modifier, shape, radius = 20f)
                                } else {
                                    Modifier
                                }
                            )
                            .then(
                                if (floating) {
                                    Modifier.border(
                                        width = 1.dp,
                                        color = if (ZhiColors.isDark()) {
                                            Color.White.copy(alpha = 0.10f)
                                        } else {
                                            Color.White.copy(alpha = 0.62f)
                                        },
                                        shape = shape,
                                    )
                                } else {
                                    Modifier
                                }
                            ),
                        shape = shape,
                        color = if (floating) {
                            glassMain.surfaceColor(
                                if (ZhiColors.isDark()) {
                                    MiuixTheme.colorScheme.surfaceContainer
                                } else {
                                    ZhiColors.panelSurface()
                                },
                            )
                        } else {
                            MiuixTheme.colorScheme.surfaceContainer
                        },
                        contentColor = MiuixTheme.colorScheme.onSurface,
                        shadowElevation = if (floating) 12.dp else 0.dp,
                    ) {
                        WorkspaceTabs(
                            tabs = WorkspaceTab.entries,
                            selected = state.tab,
                            pagerPage = compactPagerState.currentPage,
                            onSelect = { tab ->
                                ZhiFrameTrace.begin("tab:${tab.name}")
                                viewModel.selectTab(tab)
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            // ---- Overlay 系列 ----
            // 弹窗挂载与模态模糊背景在 OverlayHost.kt。
            // 必须仍处于 Scaffold 内容里才能找到 popupHost。
            ZhiOverlayHost(state = state, viewModel = viewModel, glassMain = glassMain)

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
                    glass = glassMain,
                    // 让出顶栏（含 Tab 行）的高度，否则药丸会被模糊顶栏压住。
                    topInset = TopBarInsetCompact,
                )
            }
        }
    }

    // ---- 侧边栏抽屉（窄屏，画在 Scaffold 之上）----
    if (!wide) {
        ZhiSideDrawer(
            open = state.sidebarOpen,
            onClose = viewModel::closeSidebar,
            // 宽度上限 300dp：抽屉是"导航"，不是工作区 —— 铺到 320dp/82% 时
            // 它一打开就把后面的对话流整个盖住，用户反馈「侧栏有点太宽泛了」。
            // 比例 0.76 兜住小屏：360dp 的机器上是 274dp，不至于挤掉 24dp 图标。
            width = (configuration.screenWidthDp * 0.76f).dp.coerceAtMost(300.dp),
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
