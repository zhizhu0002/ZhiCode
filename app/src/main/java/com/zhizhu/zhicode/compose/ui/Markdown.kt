package com.zhizhu.zhicode.compose.ui

import com.zhizhu.zhicode.compose.theme.ZhiColors
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import com.zhizhu.zhicode.compose.ui.pages.LocalZhiPageSet
import com.zhizhu.zhicode.compose.ui.ZhiHorizontalDivider
import com.zhizhu.zhicode.compose.data.Clipboard
import com.zhizhu.zhicode.compose.theme.LocalThemeSettings
import com.zhizhu.zhicode.compose.ui.math.LatexRenderer

import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import com.zhizhu.zhicode.compose.ui.effects.ZhiSquircle
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import java.util.LinkedHashMap

@Composable
fun ZhiMarkdown(
    source: String,
    modifier: Modifier = Modifier,
    bodyFontSize: TextUnit = 12.sp,
    streaming: Boolean = false,
    /** 正文字体族。 */
    bodyFontFamily: FontFamily? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val inlineStyles = rememberInlineStyles(bodyFontSize)
    val split = remember(source, streaming) { splitForStreaming(source, streaming) }
    // 流式尾部会不断变化，不能写进进程级缓存；定稿正文则跨 LazyColumn 回收
    val blocks = remember(split.settled, streaming) {
        if (streaming) parseMarkdown(split.settled) else MarkdownBlockCache.getOrParse(split.settled)
    }

    val fadeRate = remember { TailFadeRate() }
    val fadeRamp = remember(source) { fadeRate.widthFor(source.length, System.nanoTime()) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        blocks.forEachIndexed { index, block ->
            MdBlockView(
                block = block,
                fontSize = bodyFontSize,
                bodyFontFamily = bodyFontFamily,
                fadeTail = streaming && split.tail.isEmpty() && index == blocks.lastIndex,
                fadeRamp = fadeRamp,
            )
        }
        // 在写尾部按行内渲染（不是纯文本）：模型正在写的那一段里的 `代码`、**粗体**
        // 照常生效，只有块级语法（标题/列表/围栏）要等它跨过块边界。
        if (split.tail.isNotEmpty()) {
            Text(
                text = rememberInlineFading(
                    text = split.tail,
                    styles = inlineStyles,
                    base = scheme.onSurface,
                    fade = true,
                    ramp = fadeRamp,
                ),
                color = scheme.onSurface,
                fontSize = bodyFontSize,
                lineHeight = bodyFontSize * 1.5f,
                fontFamily = bodyFontFamily,
            )
        }
    }
}

/** 流式渲染的切分结果。 */
private class MdStreamSplit(val settled: String, val tail: String)

/** 定稿 Markdown 的跨组合缓存。 */
private object MarkdownBlockCache {
    /** 缓存多少条**定稿正文**的块解析结果。 */
    private const val MaxEntries = 48
    private const val MaxSourceChars = 512 * 1024
    private const val MaxCacheableSourceChars = 128 * 1024

    private val entries = LinkedHashMap<String, List<MdBlock>>(MaxEntries, 0.75f, true)
    private var sourceChars = 0

    fun getOrParse(source: String): List<MdBlock> {
        if (source.length > MaxCacheableSourceChars) return parseMarkdown(source)

        synchronized(this) {
            entries[source]?.let { return it }
        }

        val parsed = parseMarkdown(source)
        synchronized(this) {
            // 另一个组合可能在解析期间先填入了同一正文，优先复用那份。
            entries[source]?.let { return it }
            entries[source] = parsed
            sourceChars += source.length
            trim()
        }
        return parsed
    }

    private fun trim() {
        val iterator = entries.entries.iterator()
        while (entries.size > MaxEntries || sourceChars > MaxSourceChars) {
            if (!iterator.hasNext()) break
            sourceChars -= iterator.next().key.length
            iterator.remove()
        }
    }
}

/** 短于这个长度就不切。 */
private const val MinSettledChars = 512

private fun splitForStreaming(source: String, streaming: Boolean): MdStreamSplit {
    if (!streaming) return MdStreamSplit(source, "")
    val cut = settledPrefixLength(source)
    if (cut < MinSettledChars) return MdStreamSplit(source, "")
    return MdStreamSplit(source.substring(0, cut), source.substring(cut))
}

/** 递归渲染一个块。引用块内部会有嵌套的块，所以这里必须能自我调用。 */
@Composable
private fun MdBlockView(
    block: MdBlock,
    fontSize: TextUnit,
    bodyFontFamily: FontFamily? = null,
    fadeTail: Boolean = false,
    fadeRamp: Int = TailFadeMinChars,
) {
    val scheme = MaterialTheme.colorScheme
    val inlineStyles = rememberInlineStyles(fontSize)

    when (block) {
        is MdBlock.Heading -> Text(
            text = rememberInline(block.text, inlineStyles),
            color = scheme.onSurface,
            fontSize = when (block.level) {
                1 -> (fontSize.value + 4).sp
                2 -> (fontSize.value + 2.5).sp
                3 -> (fontSize.value + 1.5).sp
                else -> (fontSize.value + 0.5).sp
            },
            fontWeight = FontWeight.Bold,
            lineHeight = (fontSize.value + 6).sp,
            fontFamily = bodyFontFamily,
            modifier = Modifier.padding(top = 2.dp),
        )

        is MdBlock.Paragraph -> {
            val latex = LocalThemeSettings.current.latexEnabled
            // 独立公式：整段就是一个公式时居中单独画。
            // 不这么做的话，`$$…$$` 会被当作行内公式塞进正文流，居中对齐完全失效。
            val alone = remember(block.text, latex) {
                if (latex) com.zhizhu.zhicode.compose.ui.math.aloneDisplayFormula(block.text) else null
            }
            if (alone != null) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    com.zhizhu.zhicode.compose.ui.math.LatexFormula(
                        latex = alone,
                        fontSize = (fontSize.value + 1.5f).sp,
                        modifier = Modifier.padding(vertical = 2.dp),
                        color = scheme.onSurface,
                    )
                }
            } else {
                // 行内公式：把 `$…$` 切成图片，用 Compose 的 InlineTextContent 内嵌。
                // 只有开着 LaTeX 时才切，关闭时走原来的纯文本路径（零开销）。
                val inlineLatex = remember(block.text, latex) {
                    if (latex) com.zhizhu.zhicode.compose.ui.math.splitLatex(block.text) else null
                }
                val hasFormula = inlineLatex?.any { it is com.zhizhu.zhicode.compose.ui.math.LatexSegment.Formula } == true
                if (!hasFormula) {
                    Text(
                        // 段落是在写正文最常见（也是唯一常见）的形状，所以淡入只在这里落点。
                        text = rememberInlineFading(
                            text = block.text,
                            styles = inlineStyles,
                            base = scheme.onSurface,
                            fade = fadeTail,
                            ramp = fadeRamp,
                        ),
                        color = scheme.onSurface,
                        fontSize = fontSize,
                        lineHeight = fontSize * 1.5f,
                        // 正文才跟随用户选的字体；代码块永远是等宽（见 `CodeBlock`）。
                        fontFamily = bodyFontFamily,
                    )
                } else {
                    InlineLatexText(
                        segments = inlineLatex!!,
                        styles = inlineStyles,
                        fontSize = fontSize,
                        color = scheme.onSurface,
                        bodyFontFamily = bodyFontFamily,
                    )
                }
            }
        }

        is MdBlock.Bullet -> BulletRow(block, fontSize, inlineStyles)

        is MdBlock.Quote -> {
            // 竖条颜色在这里取好：drawBehind 的 lambda 不是 @Composable，里面不能读主题。
            val barColor = scheme.primary.copy(alpha = 0.55f)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        val w = 3.dp.toPx()
                        drawRoundRect(
                            color = barColor,
                            size = Size(w, size.height),
                            cornerRadius = CornerRadius(w / 2f),
                        )
                    }
                    .padding(start = 11.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                block.blocks.forEach { inner -> MdBlockView(inner, fontSize, bodyFontFamily) }
            }
        }

        // `---` 水平分割线。
        MdBlock.Rule -> ZhiHorizontalDivider(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
        )

        is MdBlock.Code -> CodeBlock(block, fontSize)

        is MdBlock.Table -> MdTableView(block, fontSize, inlineStyles)
    }
}

@Composable
private fun BulletRow(block: MdBlock.Bullet, fontSize: TextUnit, styles: InlineStyles) {
    val scheme = MaterialTheme.colorScheme
    // 每层缩进 12dp；太深时收敛，避免把正文挤成一条窄柱
    val indent = (block.depth * 12).coerceAtMost(48).dp

    Row(
        modifier = Modifier.fillMaxWidth().padding(start = indent),
        verticalAlignment = Alignment.Top,
    ) {
        if (block.task != null) {
            // 任务项用勾选框一样的方块，而不是圆点：语义不同（一个是"待办"，一个是"列举"）
            Icon(
                painter = if (block.task) ZhiIcons.done else ZhiIcons.pending,
                contentDescription = if (block.task) "已完成" else "未完成",
                tint = if (block.task) ZhiColors.green() else scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp).size(fontSize.value.dp + 2.dp),
            )
        } else {
            Text(
                text = block.marker,
                color = scheme.onSurfaceVariant,
                fontSize = fontSize,
                // 有序列表的编号用等宽，位数不同也对得齐
                fontFamily = if (block.marker == "•" || block.marker == "◦") {
                    FontFamily.Default
                } else {
                    FontFamily.Monospace
                },
                modifier = Modifier.widthIn(min = 14.dp),
            )
        }
        Text(
            text = rememberInline(block.text, styles),
            color = scheme.onSurface,
            fontSize = fontSize,
            lineHeight = fontSize * 1.5f,
            modifier = Modifier.padding(start = if (block.task != null) 6.dp else 6.dp),
        )
    }
}

@Composable
private fun CodeBlock(block: MdBlock.Code, fontSize: TextUnit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val settings = LocalThemeSettings.current
    // 复制反馈的**本地**状态：点击后把标签换成「已复制」，约 520ms 后复原
    var copied by remember(block.body) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (!copied) return@LaunchedEffect
        delay(CopyFeedbackMs)
        copied = false
    }

    // 折叠状态是**本地**的，且按内容重置：同一个代码块重新渲染时若内容变了
    // （流式追加），应回到默认折叠状态而不是继承上一份的展开。
    var expanded by remember(block.body) { mutableStateOf(false) }
    val lines = remember(block.body) { block.body.lines() }
    val collapsible = settings.codeCollapse && lines.size > CodeCollapseLines
    val collapsed = collapsible && !expanded
    val shownLines = if (collapsed) lines.take(CodeCollapseLines) else lines

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(LocalZhiPageSet.current.innerCorner),
        color = scheme.surfaceContainerHighest,
        contentColor = scheme.onSurface,
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            // 语言标记与「复制」同处一行：语言靠左（便于一眼看出是哪种代码）
            if (block.lang.isNotBlank() || block.body.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = block.lang.ifBlank { "text" },
                        color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f),
                    )
                    ZhiSmallPill(
                        label = if (copied) "已复制" else "复制",
                        highlighted = copied,
                        onClick = {
                            val ok = Clipboard.copy(context, "ZhiCode 代码块", block.body)
                            copied = ok
                        },
                    )
                }
            }

            val bodyText = remember(shownLines, settings.codeLineNumbers, scheme.onSurfaceVariant) {
                if (settings.codeLineNumbers) {
                    // 行号宽度按**总行数**的位数算（不是当前显示的行数）：
                    // 折叠时若按显示行数算，展开那一刻整体会横向跳一下。
                    val digits = lines.size.toString().length
                    buildAnnotatedString {
                        shownLines.forEachIndexed { index, line ->
                            if (index > 0) append('\n')
                            val number = (index + 1).toString().padStart(digits)
                            withStyle(
                                SpanStyle(color = scheme.onSurfaceVariant.copy(alpha = 0.65f)),
                            ) {
                                append(number)
                            }
                            append("  ")
                            append(line)
                        }
                    }
                } else {
                    AnnotatedString(shownLines.joinToString("\n"))
                }
            }

            val codeText: @Composable () -> Unit = {
                Text(
                    text = bodyText,
                    color = scheme.onSurface,
                    fontSize = (fontSize.value - 1).coerceAtLeast(9f).sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = (fontSize.value + 4).sp,
                    // 软换行开关：关掉时必须配合外层横向滚动，否则长行会被直接截断。
                    softWrap = settings.codeWrap,
                )
            }

            if (settings.codeWrap) {
                codeText()
            } else {
                // 不换行时给一个横向滚动容器。`rememberScrollState` 按 block 记忆，
                // 顺序滚动位置在重组之间保留（流式追加时不会跳回最左）。
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    codeText()
                }
            }

            if (collapsible) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (collapsed) "已折叠 ${lines.size - CodeCollapseLines} 行" else "",
                        color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f),
                    )
                    ZhiSmallPill(
                        label = if (collapsed) "展开全部（${lines.size} 行）" else "收起",
                        onClick = { expanded = !expanded },
                    )
                }
            }
        }
    }
}

/** 折叠时保留的行数。取 12：一屏能放下，又足够看清一段函数或一份配置的开头。 */
private const val CodeCollapseLines = 12

/** 「已复制」保留多久（参考实现 `MarkdownRenderer` 用的是 520ms）。 */
private const val CopyFeedbackMs = 520L

@Composable
private fun InlineLatexText(
    segments: List<com.zhizhu.zhicode.compose.ui.math.LatexSegment>,
    styles: InlineStyles,
    fontSize: TextUnit,
    color: Color,
    bodyFontFamily: FontFamily?,
) {
    val scheme = MaterialTheme.colorScheme
    val density = androidx.compose.ui.platform.LocalDensity.current
    val context = androidx.compose.ui.platform.LocalContext.current

    // 先算出每个公式的像素尺寸，再据此建 InlineTextContent。
    // 尺寸必须在组合期拿到：InlineTextContent 的 placeholder 需要确定宽高。
    val formulas = remember(segments, fontSize, color) {
        segments.mapNotNull { segment ->
            if (segment is com.zhizhu.zhicode.compose.ui.math.LatexSegment.Formula) segment else null
        }
    }
    val sizes = remember(formulas, fontSize, color) {
        val px = with(density) { fontSize.toPx() }
        formulas.map { LatexRenderer.measure(context, it.latex, px, color.toArgb()) }
    }

    val annotated = remember(segments, styles, sizes, color) {
        buildAnnotatedString {
            var formulaIndex = 0
            segments.forEach { segment ->
                when (segment) {
                    is com.zhizhu.zhicode.compose.ui.math.LatexSegment.Plain ->
                        append(inline(segment.text, styles))
                    is com.zhizhu.zhicode.compose.ui.math.LatexSegment.Formula -> {
                        val size = sizes.getOrNull(formulaIndex)
                        if (size == null) {
                            // 渲染失败：把定界符与源码一起原样显示，用户能看出是哪个宏的问题。
                            append(if (segment.display) "$$${segment.latex}$$" else "\$${segment.latex}\$")
                        } else {
                            appendFormula(id = FormulaIdPrefix + formulaIndex, alternate = segment.latex)
                        }
                        formulaIndex += 1
                    }
                }
            }
        }
    }

    val inlineContent = HashMap<String, InlineTextContent>()
    formulas.forEachIndexed { index, formula ->
        val size = sizes.getOrNull(index) ?: return@forEachIndexed
        // ⚠️ `Placeholder` 的宽高是 **TextUnit**，不是 `Dp`。
        // 用 sp 表达是正确的：公式要与正文一起随字号缩放，用绝对 dp 会在改字号后错位。
        val widthSp = with(density) { size.first.toDp() }.value.sp
        val heightSp = with(density) { size.second.toDp() }.value.sp
        inlineContent[FormulaIdPrefix + index] = InlineTextContent(
            placeholder = Placeholder(
                width = widthSp,
                height = heightSp,
                placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
            ),
        ) { _ ->
            com.zhizhu.zhicode.compose.ui.math.LatexFormula(
                latex = formula.latex,
                fontSize = fontSize,
                color = color,
            )
        }
    }

    Text(
        text = annotated,
        color = color,
        fontSize = fontSize,
        lineHeight = fontSize * 1.5f,
        fontFamily = bodyFontFamily,
        inlineContent = inlineContent,
    )
}

/** 行内公式占位符的 id 前缀。带前缀是为了不与未来其它内嵌内容撞号。 */
private const val FormulaIdPrefix = "latex:"

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendFormula(
    id: String,
    alternate: String,
) {
    addStringAnnotation(
        tag = FormulaInlineContentTag,
        annotation = alternate.ifBlank { id },
        start = length,
        end = length,
    )
    append('\uFFFC')
}

/** ⚠️ 字面量必须与 foundation 内部常量逐字一致，不要"顺手改个更好看的名字"。 */
private const val FormulaInlineContentTag = "androidx.compose.foundation.text.inlineContent"

/** 表格。 */
@Composable
private fun MdTableView(table: MdBlock.Table, fontSize: TextUnit, styles: InlineStyles) {
    val scheme = MaterialTheme.colorScheme
    val cols = maxOf(table.header.size, table.rows.maxOfOrNull { it.size } ?: 0)
    if (cols == 0) return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .background(scheme.surfaceContainerHighest, ZhiSquircle(LocalZhiPageSet.current.innerCorner))
            .padding(horizontal = 2.dp),
    ) {
        Column(Modifier.widthIn(min = (cols * 116).dp)) {
            TableRow(
                cells = table.header,
                cols = cols,
                fontSize = fontSize,
                styles = styles,
                header = true,
            )
            ZhiHorizontalDivider(modifier = Modifier.fillMaxWidth())
            table.rows.forEachIndexed { index, row ->
                TableRow(
                    cells = row,
                    cols = cols,
                    fontSize = fontSize,
                    styles = styles,
                    header = false,
                )
                if (index != table.rows.lastIndex) {
                    // 行间线用更淡的一档：表头线是结构分隔，行间线只是视觉引导
                    ZhiHorizontalDivider(
                        modifier = Modifier.fillMaxWidth(),
                        color = scheme.outlineVariant.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}

@Composable
private fun TableRow(
    cells: List<String>,
    cols: Int,
    fontSize: TextUnit,
    styles: InlineStyles,
    header: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        for (c in 0 until cols) {
            Text(
                text = rememberInline(cells.getOrElse(c) { "" }, styles),
                color = if (header) scheme.primary else scheme.onSurface,
                fontSize = if (header) fontSize else (fontSize.value - 0.5).sp,
                fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
                lineHeight = fontSize * 1.35f,
                modifier = Modifier.width(116.dp).padding(end = if (c == cols - 1) 0.dp else 6.dp),
            )
        }
    }
}

// ------------------------------------------------------------------ 行内

/** 解析行内时要用的几种颜色，集中传入避免每层都取一遍主题。 */
private class InlineStyles(
    val codeColor: Color,
    val codeBackground: Color,
    val linkColor: Color,
)

@Composable
private fun rememberInlineStyles(fontSize: TextUnit): InlineStyles {
    val scheme = MaterialTheme.colorScheme
    return remember(fontSize, scheme) {
        InlineStyles(
            codeColor = scheme.primary,
            codeBackground = scheme.surfaceContainerHighest,
            linkColor = scheme.primary,
        )
    }
}

/** [inline] 的 `remember` 包装。 */
@Composable
private fun rememberInline(text: String, styles: InlineStyles): AnnotatedString =
    remember(text, styles) { inline(text, styles) }


/** 一个字从最浅走到全实的目标时长。想更明显就调大它（斜坡会变宽）。 */
private const val TailFadeMillis = 420f

/** 斜坡宽度的下限。 */
private const val TailFadeMinChars = 12

private const val TailFadeMaxChars = 120

private const val TailFadeMaxGapSeconds = 0.5f

/** 斜坡最浅处的透明度。 */
private const val TailFadeFloor = 0.30f

private class TailFadeRate {
    private var lastLength = 0
    private var lastAtNanos = 0L
    private var charsPerSecond = 0f
    private var width = TailFadeMinChars

    fun widthFor(length: Int, nowNanos: Long): Int {
        if (length == lastLength) return width

        val prevLength = lastLength
        val prevAt = lastAtNanos
        lastLength = length
        lastAtNanos = nowNanos

        // 首帧，或者文本**变短**了（跨过块边界、开了新的一段）：
        // 沿用上一次的宽度，不重估 —— 换段不该让淡入忽长忽短。
        if (prevAt == 0L || length < prevLength) return width

        val seconds = (nowNanos - prevAt) / 1_000_000_000.0
        if (seconds <= 0.0) return width
        // 间隔太长 = 那段时间里模型在思考，不是在出字：这个样本说的不是流式节奏，
        // 拿它算会把速率估得很低、让斜坡塌下去（见 TailFadeMaxGapSeconds 的说明）。
        if (seconds > TailFadeMaxGapSeconds) return width

        val rate = ((length - prevLength) / seconds).toFloat()
        // EMA 平滑：单次突发的 chunk（比如一次来 40 个字）不该让斜坡瞬间变宽。
        charsPerSecond =
            if (charsPerSecond <= 0f) rate else charsPerSecond * 0.6f + rate * 0.4f
        width = (charsPerSecond * TailFadeMillis / 1000f).toInt()
            .coerceIn(TailFadeMinChars, TailFadeMaxChars)
        return width
    }
}

/** [inline] + 可选的前沿淡入。 */
@Composable
private fun rememberInlineFading(
    text: String,
    styles: InlineStyles,
    base: Color,
    fade: Boolean,
    ramp: Int,
): AnnotatedString = remember(text, styles, base, fade, ramp) {
    val annotated = inline(text, styles)
    if (fade) fadeTailOf(annotated, base, ramp) else annotated
}

private fun fadeTailOf(source: AnnotatedString, base: Color, ramp: Int): AnnotatedString {
    val length = source.text.length
    if (length == 0) return source
    // 只挑"自带颜色或底色"的 span：其余的（粗体/斜体）只是字形变化，没有颜色可盖。
    val colored = source.spanStyles.filter {
        it.item.color != null || it.item.background != null
    }
    val builder = AnnotatedString.Builder(source)
    var touched = false
    for (index in (length - ramp).coerceAtLeast(0) until length) {
        if (colored.any { index >= it.start && index < it.end }) continue
        builder.addStyle(
            SpanStyle(color = base.copy(alpha = fadeAlpha(index, length, ramp))),
            index,
            index + 1,
        )
        touched = true
    }
    // 整段都被语法色占住时不返回新对象：省一次 AnnotatedString 拷贝。
    return if (touched) builder.toAnnotatedString() else source
}

private fun fadeAlpha(index: Int, length: Int, ramp: Int): Float {
    if (ramp <= 1) return 1f
    val fromEnd = length - index // 末尾那个字是 1
    if (fromEnd >= ramp) return 1f
    val t = (fromEnd - 1).toFloat() / (ramp - 1)
    return (TailFadeFloor + (1f - TailFadeFloor) * t).coerceAtMost(1f)
}

/** [MdSpan] 列表 → [AnnotatedString]。 */
private fun inline(text: String, styles: InlineStyles): AnnotatedString {
    val spans = parseInline(text)
    return buildAnnotatedString {
        spans.forEach { span ->
            val style = when (span.style) {
                MdStyle.PLAIN -> null
                MdStyle.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                MdStyle.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                MdStyle.BOLD_ITALIC -> SpanStyle(
                    fontWeight = FontWeight.Bold,
                    fontStyle = FontStyle.Italic,
                )
                MdStyle.CODE -> SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    color = styles.codeColor,
                    background = styles.codeBackground,
                )
                MdStyle.STRIKE -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                MdStyle.LINK -> SpanStyle(
                    color = styles.linkColor,
                    textDecoration = TextDecoration.Underline,
                )
            }

            val href = span.href
            if (span.style == MdStyle.LINK && href != null) {
                withLink(
                    LinkAnnotation.Url(
                        url = href,
                        styles = TextLinkStyles(style = style ?: SpanStyle()),
                    ),
                ) {
                    append(span.text)
                }
            } else if (style != null) {
                withStyle(style) { append(span.text) }
            } else {
                append(span.text)
            }
        }
    }
}
