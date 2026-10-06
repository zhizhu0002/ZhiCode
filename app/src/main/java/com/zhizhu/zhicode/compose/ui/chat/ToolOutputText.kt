package com.zhizhu.zhicode.compose.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.theme.ZhiColors
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 工具输出的有界、懒渲染文本视图。
 *
 * 工具卡本身位于对话流的 LazyColumn item 内，因此不能把 300,000 字符直接交给一个
 * Text，也不能把全部行拆成几万个 Compose 节点。这里把原文只建立一次行偏移索引，
 * 再用第二层有界 LazyColumn 按小窗口取行；视口外的行不会组合或布局。
 */

/** 默认预览最多显示多少行；原文仍保留在 ToolActivity 中。 */
internal const val MaxRenderedLines = 300

/** 单个工具预览最多读取的字符数，防止一行超长文本撑爆测量。 */
private const val MaxRenderedChars = 48_000

/** 每个懒列表 item 的行数；只会组合视口附近的几个 chunk。 */
private const val LinesPerChunk = 24

/** 内层输出窗口的高度上限，避免展开工具卡独占整个对话流。 */
private val OutputWindowMaxHeight = 360.dp

@Composable
fun DiffLines(diff: String) {
    OutputWindow(text = diff, diffMode = true)
}

@Composable
fun OutputLines(text: String) {
    OutputWindow(text = text, diffMode = false)
}

@Composable
private fun OutputWindow(text: String, diffMode: Boolean) {
    if (text.isEmpty()) return

    var showAll by remember(text) { mutableStateOf(false) }
    val index = remember(text) { TextLayoutIndex(text) }
    val window = remember(index, showAll) { index.window(showAll) }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = OutputWindowMaxHeight),
        userScrollEnabled = window.lineCount > LinesPerChunk,
    ) {
        val chunkCount = (window.lineCount + LinesPerChunk - 1) / LinesPerChunk
        items(
            count = chunkCount,
            key = { chunk -> "output-chunk-$chunk" },
        ) { chunk ->
            val firstLine = chunk * LinesPerChunk
            val lastLine = minOf(firstLine + LinesPerChunk, window.lineCount)
            Column(modifier = Modifier.fillMaxWidth()) {
                for (lineIndex in firstLine until lastLine) {
                    val line = index.line(
                        lineIndex = lineIndex,
                        clippedLine = window.clippedLine,
                        clippedEnd = window.clippedEnd,
                    )
                    if (diffMode) {
                        DiffLine(line)
                    } else {
                        Text(
                            text = line.ifEmpty { " " },
                            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                        )
                    }
                }
            }
        }
    }

    if (window.hiddenLines > 0) {
        MoreLinesRow(hidden = window.hiddenLines, onClick = { showAll = true })
    }
}

@Composable
private fun DiffLine(line: String) {
    val scheme = MiuixTheme.colorScheme
    val (foreground, background) = when {
        line.startsWith("+++") || line.startsWith("---") ->
            scheme.onSurfaceVariantSummary to Color.Transparent
        line.startsWith("+") -> ZhiColors.green() to ZhiColors.greenContainer()
        line.startsWith("-") -> ZhiColors.red() to ZhiColors.redContainer()
        line.startsWith("@@") -> scheme.primary to Color.Transparent
        line.startsWith("diff ") || line.startsWith("index ") || line.startsWith("new file") ->
            scheme.onSurfaceVariantSummary to Color.Transparent
        else -> scheme.onSurface to Color.Transparent
    }
    Text(
        text = line.ifEmpty { " " },
        color = foreground,
        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
        fontFamily = FontFamily.Monospace,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = 8.dp),
    )
}

@Composable
private fun MoreLinesRow(hidden: Int, onClick: () -> Unit) {
    val scheme = MiuixTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
        contentColor = scheme.onSurfaceVariantSummary,
    ) {
        Text(
            text = "还有 $hidden 行 · 点按显示全部",
            color = scheme.onSurfaceVariantSummary,
            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
        )
    }
}

/**
 * 原文的轻量行索引。只保存每行起点，不预先 split 字符串；展示时只为可见行创建
 * substring。对 300,000 字符的单行输出也只产生一个受 MaxRenderedChars 约束的 Text。
 */
private class TextLayoutIndex(private val source: String) {
    private val starts: IntArray = buildList {
        add(0)
        source.forEachIndexed { index, char ->
            if (char == '\n' && index + 1 <= source.length) add(index + 1)
        }
    }.toIntArray()

    val lineCount: Int get() = starts.size

    fun line(lineIndex: Int, clippedLine: Int, clippedEnd: Int): String {
        val start = starts[lineIndex]
        val naturalEnd = if (lineIndex + 1 < starts.size) starts[lineIndex + 1] - 1 else source.length
        val end = if (lineIndex == clippedLine) clippedEnd.coerceIn(start, naturalEnd) else naturalEnd
        return source.substring(start, end)
    }

    fun window(showAll: Boolean): RenderedWindow {
        if (showAll) {
            return RenderedWindow(
                lineCount = lineCount,
                clippedLine = -1,
                clippedEnd = -1,
                hiddenLines = 0,
            )
        }

        val lineLimit = minOf(lineCount, MaxRenderedLines)
        var chars = 0
        var rendered = 0
        var clippedLine = -1
        var clippedEnd = -1
        while (rendered < lineLimit) {
            val start = starts[rendered]
            val naturalEnd = if (rendered + 1 < starts.size) starts[rendered + 1] - 1 else source.length
            val length = naturalEnd - start
            if (chars + length > MaxRenderedChars) {
                clippedLine = rendered
                clippedEnd = start + (MaxRenderedChars - chars).coerceAtLeast(1).coerceAtMost(length)
                rendered++
                break
            }
            chars += length
            if (rendered + 1 < starts.size) chars++
            rendered++
        }

        val hasMoreLines = rendered < lineCount
        val hasClippedText = clippedLine >= 0
        return RenderedWindow(
            lineCount = rendered,
            clippedLine = clippedLine,
            clippedEnd = clippedEnd,
            hiddenLines = (lineCount - rendered) + if (hasClippedText) 1 else 0,
        ).takeIf { it.lineCount > 0 } ?: RenderedWindow(1, -1, -1, if (hasMoreLines) 1 else 0)
    }
}

private data class RenderedWindow(
    val lineCount: Int,
    val clippedLine: Int,
    val clippedEnd: Int,
    val hiddenLines: Int,
)

/**
 * 兼容旧的纯函数调用点：只保留前 [max] 行，不在 Compose 中创建行数组。
 * `ToolOutputText` 的 UI 路径使用上面的索引窗口；这个函数保留给已有逻辑复用。
 */
internal class LimitedLines(val text: String, val hidden: Int)

internal fun limitLines(text: String, max: Int): LimitedLines {
    if (text.isEmpty()) return LimitedLines(text, 0)
    if (max <= 0) return LimitedLines("", 1 + countNewlines(text, 0))
    var lines = 1
    var cut = -1
    for (i in text.indices) {
        if (text[i] == '\n') {
            lines++
            if (lines > max) {
                cut = i
                break
            }
        }
    }
    if (cut < 0) return LimitedLines(text, 0)
    return LimitedLines(text.substring(0, cut), lines + countNewlines(text, cut + 1) - max)
}

private fun countNewlines(text: String, from: Int): Int {
    var count = 0
    for (i in from until text.length) {
        if (text[i] == '\n') count++
    }
    return count
}
