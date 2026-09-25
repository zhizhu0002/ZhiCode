package com.iqge.iqcode.compose.ui.panes

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iqge.RuntimeInstaller
import com.iqge.TermuxTerminalPane
import com.iqge.iqcode.compose.theme.IqRadius
import com.iqge.iqcode.compose.ui.IqIcons
import com.iqge.iqcode.compose.model.TerminalLine
import com.iqge.iqcode.compose.model.TerminalTone
import com.iqge.iqcode.compose.theme.IqColors
import com.iqge.iqcode.compose.ui.IqHorizontalDivider
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
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
    modifier: Modifier = Modifier,
    /** 清屏动作。默认 `null` = 不显示按钮。 */
    onClear: (() -> Unit)? = null,
) {
    if (runtimeReady) {
        RealTerminalPane(
            projectName = projectName,
            workingDirectory = workingDirectory,
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
 * 真实 PTY。
 *
 * ⚠️ 这个视图持有的是**真实子进程**（bash）与 PTY 文件描述符，
 * 必须随 Composable 离开组合而关闭，否则每切一次 Tab 就漏一个 bash 进程。
 * `onRelease` 里调 `closeAll()` 正是为此。
 */
@Composable
private fun RealTerminalPane(
    projectName: String,
    workingDirectory: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val pane = remember {
        TermuxTerminalPane(context, RuntimeInstaller(context.applicationContext))
    }

    // 项目目录变化时同步给终端（下一次新建会话用它当工作目录）。
    LaunchedEffect(workingDirectory) {
        pane.setNextSessionWorkingDirectory(workingDirectory)
    }

    DisposableEffect(Unit) {
        pane.onRuntimeReady()
        onDispose { pane.closeAll() }
    }

    Surface(modifier = modifier.fillMaxSize(), color = IqColors.panelSurface()) {
        Column(modifier = Modifier.fillMaxSize()) {
            PaneHeader(
                title = "终端",
                subtitle = projectName,
            )
            AndroidView(
                factory = { pane },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
    }
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

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1)
    }

    Surface(modifier = modifier.fillMaxSize(), color = IqColors.panelSurface()) {
        Column(modifier = Modifier.fillMaxSize()) {
            PaneHeader(
                title = "终端",
                actionIcon = IqIcons.clear,
                actionDescription = "清屏",
                onAction = onClear,
                subtitle = projectName,
            )
            Surface(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                color = IqColors.cardSurface(),
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
                                fontSize = 11.sp,
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
            IqHorizontalDivider()
            Text(
                text = if (lines.any { it.text.contains("未就绪") || it.text.contains("尚未接入") }) {
                    "初始化内置 Termux 环境后，这里会变成可输入的真实终端。"
                } else {
                    "内置 Termux 环境未就绪，无法启动终端。"
                },
                color = scheme.onSurfaceVariantSummary,
                fontSize = 10.5.sp,
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
        TerminalTone.ERROR -> IqColors.red()
        TerminalTone.SUCCESS -> IqColors.green()
    }
}
