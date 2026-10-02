package com.zhizhu.zhicode.compose.ui.panes

import com.zhizhu.zhicode.compose.theme.ZhiTextScale
import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhizhu.zhicode.TermuxTerminalPane
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.model.TerminalLine
import com.zhizhu.zhicode.compose.model.TerminalTone
import com.zhizhu.zhicode.compose.theme.ZhiColors
import com.zhizhu.zhicode.compose.ui.ZhiHorizontalDivider
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 终端面板。
 *
 * 两种形态，由 [runtimeReady] 决定：
 * - **环境就绪**：挂真实的 Termux PTY（`AndroidView` 承载 [TermuxTerminalPane]），
 *   里面是真正的 bash，可以跑命令；
 * - **环境未就绪**：显示只读滚动缓冲 + 一句"为什么不能输入"，
 *   而不是给一个点了没反应的输入框。
 *
 * 为什么用 `AndroidView` 而不是纯 Compose 重写终端：
 * `TerminalView`（约 2000 行）依赖 `Canvas` 逐字符绘制、CSI 序列解析、
 * 文本选择与缩放手势，全部重写成 Compose 的收益很低、风险很高。
 * 终端的正确性来自 `TerminalEmulator`/`TerminalBuffer`（已完整移植），
 * 渲染层复用现成 View 是更稳的选择。
 */
@Composable
fun TerminalPane(
    lines: List<TerminalLine>,
    projectName: String,
    runtimeReady: Boolean,
    workingDirectory: String,
    /** 终端实例的持有者（跨 Tab 保活，见 [WorkspaceViewModel.terminalPane]）。 */
    terminalHolder: TerminalHolder,
    modifier: Modifier = Modifier,
    /** 清屏动作。默认 `null` = 不显示按钮。 */
    onClear: (() -> Unit)? = null,
) {
    if (runtimeReady) {
        RealTerminalPane(
            projectName = projectName,
            workingDirectory = workingDirectory,
            terminalHolder = terminalHolder,
            modifier = modifier,
        )
        return
    }
    TerminalPlaceholder(
        lines = lines,
        projectName = projectName,
        modifier = modifier,
        onClear = onClear,
    )
}

/**
 * 终端实例的取用口。
 *
 * 做成接口而不是直接把 `WorkspaceViewModel` 传进来：这个包（`ui.panes`）里其它面板
 * 都不认识 ViewModel，终端也不该是例外 —— 它需要的只是“给我一个能跨 Tab 活着的
 * `TermuxTerminalPane`”。由 `AppScaffold` 在接线处注入。
 */
fun interface TerminalHolder {
    fun obtain(context: Context): TermuxTerminalPane
}

/**
 * 真实 PTY。
 *
 * ⚠️ **这里不能持有会话的生命周期**。
 *
 * 原来写的是 `remember { TermuxTerminalPane(...) }` + `onDispose { pane.closeAll() }`，
 * 而 `PaneHost` 用 `when(tab)` 切面板：切到别的 Tab，这段 Composable 直接离开组合，
 * `onDispose` 触发，**`closeAll()` 把每个会话（bash 进程 + PTY fd）都 finish 掉**。
 * 所以“切走再切回来”看到的永远是全新的空终端。
 *
 * 现在实例由 [TerminalHolder] 持有（实际挂在 ViewModel 上），离开组合时只把它
 * **从视图树上摘下来**，会话原封不动：
 * - 子进程不依赖 View 挂在哪里，摘下来之后 bash 照常跑，回声也照常进缓冲；
 * - 重新切回来时 [AndroidView] 的 factory 返回同一个实例，终端内容还是原来那些。
 *
 * 唯一需要防御的是“同一个 View 不能有两个父节点”：切回来时新的 AndroidView 可能
 * 先建立、旧的还没来得及 dispose，所以 attach 前再摘一次（[detachFromParent]）。
 *
 * <h3>外壳为什么是 Compose 了</h3>
 * 工具栏、抽屉、扩展键、两个对话框原来全在 `TermuxTerminalPane` 里用 Java 拼 View，
 * 与它正上方那行 Compose 标题栏割裂，而且整块和原版逐行相同（95.4%）。
 * 现在壳在 [TerminalToolbar] / [TerminalDrawer] / [TerminalExtraKeys] /
 * [TerminalQuickActionsDialog] 里，宿主只剩下 PTY 与上游 `TerminalView`
 * （由中间那个 [AndroidView] 承载）。**渲染层仍然是上游 View**：
 * `TerminalView` 靠 Canvas 逐字符绘制 + CSI 解析 + 选区手势，重写收益低风险高。
 */
@Composable
private fun RealTerminalPane(
    projectName: String,
    workingDirectory: String,
    terminalHolder: TerminalHolder,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // remember 只是避免每次重组都调一次 obtain；真正的实例缓存由 holder 负责。
    val pane = remember(terminalHolder) { terminalHolder.obtain(context) }
    val state by rememberTerminalState(pane)

    // 项目目录变化时同步给终端（下一次新建会话用它当工作目录）。
    LaunchedEffect(workingDirectory) {
        pane.setNextSessionWorkingDirectory(workingDirectory)
    }

    DisposableEffect(pane) {
        pane.onRuntimeReady()
        // ⚠️ 只摘视图，**不关会话**。真正释放是 ViewModel.onCleared() 与重装环境之前。
        onDispose { detachFromParent(pane) }
    }

    var drawerOpen by remember { mutableStateOf(false) }
    var quickActionsOpen by remember { mutableStateOf(false) }
    // -1 = 没在改名。存下标而不是布尔值：长按哪一行就要改哪一行。
    var renamingIndex by remember { mutableStateOf(-1) }

    // 配置变化（旋转、换主题）后让终端重新对齐一次尺寸。
    //
    // MainActivity 声明了 configChanges，所以旋转**不会**重建 Activity，组合也不会重来；
    // 而终端能不能画出来，取决于上游 TerminalView 内部那个 mEmulator 有没有被设上 ——
    // 它只在 updateSize() 里被设，而 updateSize() 在尺寸为 0 时静默返回，
    // onDraw() 在 mEmulator == null 时只画一块纯黑。真机上确实这样表现过：
    // 旋转后终端一片空白，切到别的标签再切回来才恢复。
    //
    // ⚠️ 这里**不能**无条件 key(configuration) 去重建 AndroidView 节点。
    // 试过，不行：宿主是同一个 View 实例，同一帧内从旧节点迁到新节点时，
    // 旧节点的释放会把刚挂上的宿主再摘一次 → 宿主不在视图树上 → 没有测量/布局 →
    // 尺寸恒为 0 → 谁都救不回来（表现仍然是「旋转后空白、切标签才恢复」）。
    // 所以顺序反过来：先让宿主自己在原地重挂渲染（refreshTerminal），
    // **只有当它报告自己真的不在视图树上时才重建节点**（那是它唯一无法自救的情况）。
    val configuration = LocalConfiguration.current
    var viewEpoch by remember { mutableStateOf(0) }
    LaunchedEffect(configuration) { pane.refreshTerminal() }
    LaunchedEffect(state.hostInTree) { if (!state.hostInTree) viewEpoch++ }

    val selectedIndex = state.sessions.indexOfFirst { it.selected }

    Surface(modifier = modifier.fillMaxSize(), color = ZhiColors.panelSurface()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 只留一条头。这里原来还画的是 `PaneHeader("终端", projectName)`，紧接着
            // 下面 RealTerminalPane 又画一条自己的工具栏（☰ / 会话名 / ⌨ / ⋮）——
            // 于是终端比文件、变更两个面板多一行标题。现在三块面板同构：
            // 标题 + 副标题（当前会话名，没会话时退回项目名）+ 行尾动作。
            PaneHeader(
                title = "终端",
                subtitle = state.title.ifBlank { projectName },
                actions = {
                    TerminalHeaderActions(
                        onMenu = { drawerOpen = true },
                        onKeyboard = { pane.toggleKeyboard() },
                        onMore = { quickActionsOpen = true },
                    )
                },
            )
            // 抽屉要盖住"终端 + 扩展键"这两段（和原实现一样，
            // 它当时是往面板的 FrameLayout 里加一层全高视图），所以这两段同处一个 Box。
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        val failure = state.failureDetail
                        val notice = state.runtimeNotice
                        when {
                            failure != null -> TerminalFailure(failure, onRetry = { pane.retryTerminal() })
                            notice != null -> TerminalRuntimeNotice(notice)
                            else -> key(viewEpoch) {
                                AndroidView(
                                    factory = {
                                        // 可能还挂在上一轮的容器上（旧 AndroidView 尚未 dispose），先摘干净
                                        detachFromParent(pane)
                                        pane
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                    TerminalExtraKeys(state) { action ->
                        // DRAWER 的目标是 Compose 自己这个抽屉，宿主碰不到它，
                        // 所以在这一层拦下来 —— 属性文件里写 `DRAWER` 仍然有效。
                        if (action.trim().uppercase() == "DRAWER") {
                            drawerOpen = true
                        } else {
                            pane.sendExtraKey(action)
                        }
                    }
                }
                TerminalDrawer(
                    open = drawerOpen,
                    state = state,
                    onDismiss = { drawerOpen = false },
                    onSelect = { index ->
                        pane.selectSession(index)
                        drawerOpen = false
                    },
                    onNewSession = {
                        pane.newSession()
                        drawerOpen = false
                    },
                    onCloseSession = { index -> pane.closeSession(index) },
                    onRenameSession = { index -> renamingIndex = index },
                    onToggleKeyboard = {
                        pane.toggleKeyboard()
                        drawerOpen = false
                    },
                    onReloadProperties = {
                        pane.reloadProperties()
                        drawerOpen = false
                    },
                )
            }
        }
    }

    TerminalQuickActionsDialog(
        show = quickActionsOpen,
        onDismiss = { quickActionsOpen = false },
        onAction = { index ->
            // 下标顺序与 [QuickActions] 一一对应，两边都不能重排。
            when (index) {
                0 -> pane.pasteFromClipboard()
                1 -> toast(context, pane.copySelection())
                2 -> pane.resetTerminal()
                3 -> pane.newSession()
                4 -> if (selectedIndex >= 0) renamingIndex = selectedIndex
                5 -> pane.closeSession(selectedIndex)
                6 -> pane.killShell()
                7 -> pane.changeFont(-1)
                8 -> pane.changeFont(1)
                9 -> pane.reloadProperties()
                10 -> toast(context, pane.toggleWakeLock())
            }
        },
    )

    TerminalRenameDialog(
        show = renamingIndex >= 0,
        initial = state.sessions.getOrNull(renamingIndex)?.name ?: "",
        onDismiss = { renamingIndex = -1 },
        onConfirm = { name ->
            pane.renameSession(renamingIndex, name)
            renamingIndex = -1
        },
    )
}

/** 把一个 View 从它当前的父容器上摘下来（没父容器就什么都不做）。 */
private fun detachFromParent(view: View) {
    (view.parent as? ViewGroup)?.removeView(view)
}

/** 环境未就绪时的只读占位。文案明确说明**为什么**不能输入。 */
@Composable
private fun TerminalPlaceholder(
    lines: List<TerminalLine>,
    projectName: String,
    modifier: Modifier = Modifier,
    onClear: (() -> Unit)? = null,
) {
    val scheme = MiuixTheme.colorScheme
    val listState = rememberLazyListState()

    // ⚠️ 用 `requestScrollToItem`（非挂起，只登记目标下标、在下次测量里生效），
    // **不要**换回 `animateScrollToItem`。
    //
    // 它俩的差别在"内容连续增长"这个场景下是决定性的：`animateScrollToItem` 是挂起的，
    // 而它的 key 是 `lines.size` —— 终端每来一行就变一次，于是 effect 被不停地取消并重启，
    // 滚动常常在真正滚到底之前就被取消掉，只能等某个空档才追上，看起来就是"滚一下停一下"。
    // 日志输出时本来也不该有动画（每一步都起动画会互相打断）。
    // 完整推导见 `ui/chat/ChatList.kt` 里那段同源注释（对话流踩过同一个坑）。
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.requestScrollToItem(lines.size - 1)
    }

    Surface(modifier = modifier.fillMaxSize(), color = ZhiColors.panelSurface()) {
        Column(modifier = Modifier.fillMaxSize()) {
            PaneHeader(
                title = "终端",
                actionIcon = ZhiIcons.clear,
                actionDescription = "清屏",
                onAction = onClear,
                subtitle = projectName,
            )
            Surface(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                color = ZhiColors.cardSurface(),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(start = 10.dp, top = 8.dp, bottom = 8.dp),
                    ) {
                        items(lines.size) { index ->
                            val line = lines[index]
                            Text(
                                text = line.text.ifEmpty { " " },
                                color = toneColor(line.tone, scheme.onSurface),
                                fontSize = ZhiTextScale.Caption,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                    VerticalScrollBar(
                        adapter = rememberScrollBarAdapter(listState),
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    )
                }
            }
            ZhiHorizontalDivider()
            Text(
                text = if (lines.any { it.text.contains("未就绪") || it.text.contains("尚未接入") }) {
                    "初始化内置 Termux 环境后，这里会变成可输入的真实终端。"
                } else {
                    "内置 Termux 环境未就绪，无法启动终端。"
                },
                color = scheme.onSurfaceVariantSummary,
                fontSize = ZhiTextScale.Footnote,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun toneColor(tone: TerminalTone, fallback: Color): Color {
    val scheme = MiuixTheme.colorScheme
    return when (tone) {
        TerminalTone.NORMAL -> fallback
        TerminalTone.DIM -> scheme.onSurfaceVariantSummary
        TerminalTone.PROMPT -> scheme.primary
        TerminalTone.ERROR -> ZhiColors.red()
        TerminalTone.SUCCESS -> ZhiColors.green()
    }
}
