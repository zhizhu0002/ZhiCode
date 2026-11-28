package com.zhizhu.zhicode.compose.ui.pages.miuix

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhizhu.zhicode.TermuxTerminalPane
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.ZhiIconButton
import com.zhizhu.zhicode.compose.ui.ZhiIndeterminateBar
import com.zhizhu.zhicode.compose.ui.ZhiUsageBar
import com.zhizhu.zhicode.compose.ui.chat.rememberAutoFollow
import com.zhizhu.zhicode.compose.model.TerminalLine
import com.zhizhu.zhicode.compose.model.TerminalTone
import com.zhizhu.zhicode.compose.theme.ZhiColors
import com.zhizhu.zhicode.compose.ui.ZhiHorizontalDivider
import com.zhizhu.zhicode.compose.ui.ZhiMotion
import com.zhizhu.zhicode.compose.ui.panes.PaneHeader
import com.zhizhu.zhicode.compose.ui.panes.TerminalDrawer
import com.zhizhu.zhicode.compose.ui.panes.TerminalExtraKeys
import com.zhizhu.zhicode.compose.ui.panes.TerminalFailure
import com.zhizhu.zhicode.compose.ui.panes.TerminalHeaderActions
import com.zhizhu.zhicode.compose.ui.panes.TerminalQuickActionsDialog
import com.zhizhu.zhicode.compose.ui.panes.TerminalRenameDialog
import com.zhizhu.zhicode.compose.ui.panes.TerminalRuntimeNotice
import com.zhizhu.zhicode.compose.ui.panes.rememberTerminalState
import com.zhizhu.zhicode.compose.ui.panes.toast
import com.zhizhu.zhicode.compose.ui.settings.rememberLastNonNull

@Composable
fun TerminalPage(
    lines: List<TerminalLine>,
    projectName: String,
    runtimeReady: Boolean,
    workingDirectory: String,
    /** 终端实例的持有者（跨 Tab 保活，见 [WorkspaceViewModel.terminalPane]）。 */
    terminalHolder: TerminalHolder,
    modifier: Modifier = Modifier,
    /** 清屏动作。默认 `null` = 不显示按钮。 */
    onClear: (() -> Unit)? = null,
    /** 正在安装内置运行环境（与 `state.runtimeInstalling` 同源）。 */
    installing: Boolean = false,
    /** 安装进度 0~100；0 表示不显示确定进度。 */
    progress: Int = 0,
    /** 安装步骤文案或最后一次失败原因。 */
    message: String = "",
    /** 初始化运行环境。`null` 表示当前入口不可用（例如已有任务在跑）。 */
    onInstall: (() -> Unit)? = null,
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
        installing = installing,
        progress = progress,
        message = message,
        onInstall = onInstall,
    )
}

/** 终端实例的取用口。 */
fun interface TerminalHolder {
    fun obtain(context: Context): TermuxTerminalPane
}

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

    val isDark = ZhiColors.isDark()
    LaunchedEffect(isDark) { pane.setDarkTheme(isDark) }

    DisposableEffect(pane) {
        pane.onRuntimeReady()
        // ⚠️ 只摘视图，**不关会话**。真正释放是 ViewModel.onCleared() 与重装环境之前。
        onDispose { detachFromParent(pane) }
    }

    var drawerOpen by remember { mutableStateOf(false) }
    var quickActionsOpen by remember { mutableStateOf(false) }
    // -1 = 没在改名。存下标而不是布尔值：长按哪一行就要改哪一行。
    var renamingIndex by remember { mutableStateOf(-1) }

    val configuration = LocalConfiguration.current
    var viewEpoch by remember { mutableStateOf(0) }
    LaunchedEffect(configuration) { pane.refreshTerminal() }
    LaunchedEffect(state.hostInTree) { if (!state.hostInTree) viewEpoch++ }

    val selectedIndex = state.sessions.indexOfFirst { it.selected }

    val imeMotion = com.zhizhu.zhicode.compose.ui.LocalImeMotion.current
    val bottomBarPx = with(LocalDensity.current) {
        com.zhizhu.zhicode.compose.ui.LocalBottomBarInset.current.roundToPx()
    }
    Surface(
        modifier = modifier
            .fillMaxSize()
            .layout { measurable, constraints ->
                val imePadPx = (imeMotion.imeBottomPx - bottomBarPx).coerceAtLeast(0)
                val maxH = (constraints.maxHeight - imePadPx).coerceAtLeast(0)
                val minH = minOf(constraints.minHeight, maxH)
                val placeable = measurable.measure(constraints.copy(minHeight = minH, maxHeight = maxH))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(0, 0)
                }
            },
        color = ZhiColors.panelSurface(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 只留一条头。
            PaneHeader(
                title = "终端",
                subtitle = state.title.ifBlank { projectName },
                leadingAction = {
                    ZhiIconButton(
                        icon = ZhiIcons.menu,
                        description = "会话列表",
                        onClick = { drawerOpen = true },
                        tint = MaterialTheme.colorScheme.onSurface,
                        compact = 30.dp,
                        iconSize = 16.dp,
                    )
                },
                actions = {
                    TerminalHeaderActions(
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
                        val failure = rememberLastNonNull(state.failureDetail)
                        val notice = rememberLastNonNull(state.runtimeNotice)
                        AnimatedContent(
                            targetState = when {
                                state.failureDetail != null -> TerminalStage.FAILURE
                                state.runtimeNotice != null -> TerminalStage.NOTICE
                                else -> TerminalStage.REAL
                            },
                            transitionSpec = {
                                fadeIn(ZhiMotion.fadeInSpec) togetherWith fadeOut(ZhiMotion.fadeOutSpec)
                            },
                            label = "terminalStage",
                        ) { stage ->
                            when (stage) {
                                TerminalStage.FAILURE -> TerminalFailure(
                                    failure.orEmpty(),
                                    onRetry = { pane.retryTerminal() },
                                )

                                TerminalStage.NOTICE -> TerminalRuntimeNotice(notice.orEmpty())

                                TerminalStage.REAL -> key(viewEpoch) {
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

/** 真实终端区域此刻画的是三种东西中的哪一种。 */
private enum class TerminalStage { REAL, FAILURE, NOTICE }

/** 环境未就绪时的只读占位。文案明确说明**为什么**不能输入。 */
@Composable
private fun TerminalPlaceholder(
    lines: List<TerminalLine>,
    projectName: String,
    modifier: Modifier = Modifier,
    onClear: (() -> Unit)? = null,
    installing: Boolean = false,
    progress: Int = 0,
    message: String = "",
    onInstall: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val listState = rememberLazyListState()

    val autoFollow by rememberAutoFollow(listState)
    LaunchedEffect(lines.size, autoFollow) {
        if (autoFollow && lines.isNotEmpty()) listState.requestScrollToItem(lines.size - 1)
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
                        // 同 FilesPane 的正文列表：**不加** `key = { it }` —— 行号就是下标
                        items(
                            count = lines.size,
                            key = { it },
                            contentType = { "terminal-line" },
                        ) { index ->
                            val line = lines[index]
                            Text(
                                text = line.text.ifEmpty { " " },
                                color = toneColor(line.tone, scheme.onSurface),
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
            ZhiHorizontalDivider()
            Text(
                text = if (lines.any { it.text.contains("未就绪") || it.text.contains("尚未接入") }) {
                    "初始化内置 Debian 环境后，这里会直接进入可输入的真实 Debian 终端。"
                } else {
                    "内置 Debian 环境未就绪，无法启动终端。"
                },
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp),
            )
            // 「环境未就绪」以前只有一句话，没有任何可点的东西 —— 用户得自己
            TerminalSetupAction(
                installing = installing,
                progress = progress,
                message = message,
                onInstall = onInstall,
                modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 12.dp),
            )
        }
    }
}

/** 占位态的「初始化运行环境」入口。 */
@Composable
private fun TerminalSetupAction(
    installing: Boolean,
    progress: Int,
    message: String,
    onInstall: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (onInstall == null) return
    val scheme = MaterialTheme.colorScheme
    Column(modifier = modifier) {
        if (installing) {
            if (progress in 1..99) {
                ZhiUsageBar(
                    fraction = progress / 100f,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                ZhiIndeterminateBar(modifier = Modifier.fillMaxWidth())
            }
            Text(
                text = message.ifBlank { "正在解压并配置内置 Debian + PRoot 环境…" },
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        } else {
            Button(
                onClick = onInstall,
                modifier = Modifier.fillMaxWidth(),
                
            ) {
                Text(text = "初始化运行环境", color = scheme.onPrimary)
            }
            if (message.isNotBlank()) {
                Text(
                    text = message,
                    color = scheme.error,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun toneColor(tone: TerminalTone, fallback: Color): Color {
    val scheme = MaterialTheme.colorScheme
    return when (tone) {
        TerminalTone.NORMAL -> fallback
        TerminalTone.DIM -> scheme.onSurfaceVariant
        TerminalTone.PROMPT -> scheme.primary
        TerminalTone.ERROR -> ZhiColors.red()
        TerminalTone.SUCCESS -> ZhiColors.green()
    }
}
