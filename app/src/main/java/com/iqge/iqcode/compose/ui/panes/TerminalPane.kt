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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * 第一期只还原外观：只读滚动缓冲 + 输入行占位，未接入真实 Termux PTY。
 */
@Composable
fun TerminalPane(
    lines: List<TerminalLine>,
    projectName: String,
    modifier: Modifier = Modifier,
    /** 清屏动作。默认 `null` = 不显示按钮（终端还没接 PTY，清屏无处生效）。 */
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
                subtitle = "$projectName · Mock",
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
                    // Miuix 滚动条
                    VerticalScrollBar(
                        adapter = rememberScrollBarAdapter(listState),
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    )
                }
            }
            IqHorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "$ ",
                    color = scheme.primary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
                // 命令输入位走 Miuix TextField 的禁用态 + useLabelAsPlaceholder：
                // 占位文字、焦点、键盘行为都由组件负责，将来接入 PTY 只需把
                // enabled 打开，不用再重画一个假的输入框。
                //
                // 三处尺寸必须显式覆盖，否则文字会被裁成一团乱码：
                // Miuix `TextFieldDefaults` 写死 `LabelFontSizeNormal = 17sp`、
                // `InsideMargin = 16dp×16dp`、`CornerRadius = 16dp`（字节码实测）。
                // 17sp 的标签 + 上下各 16dp 内边距 = 约 54dp，而终端输入行只有 42dp。
                // 标签的字号没有公开参数可调，所以只能把 insideMargin 压到 4dp 让位，
                // 并把圆角收进本工程的 IqRadius。
                TextField(
                    value = "",
                    onValueChange = {},
                    modifier = Modifier.weight(1f),
                    enabled = false,
                    singleLine = true,
                    label = "输入命令（未接入 PTY）",
                    useLabelAsPlaceholder = true,
                    textStyle = MiuixTheme.textStyles.body2,
                    insideMargin = DpSize(10.dp, 4.dp),
                    cornerRadius = IqRadius.inner,
                )
            }
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
