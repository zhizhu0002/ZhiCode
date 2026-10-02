package com.zhizhu.zhicode.compose.ui

import com.zhizhu.zhicode.compose.theme.ZhiTextScale
import com.zhizhu.zhicode.compose.theme.ZhiColors
import com.zhizhu.zhicode.compose.theme.ZhiRadius

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Markdown 渲染层。语法解析在 `MarkdownParse.kt`（纯 Kotlin、可独立测试），
 * 本文件只负责把那份 AST 画成 Miuix 组件。
 *
 * ## 为什么不用三方库
 *
 * 这个工程跑在受限的 Termux 环境里，多引一个依赖就多一份版本冲突与离线解析风险；
 * 而 Markdown 的常用子集并不大，且必须复用 Miuix 配色。自写反而更可控。
 *
 * ## 与解析层的约定
 *
 * 解析层**保证不丢字符**：未闭合的围栏、未闭合的强调都会原样输出。
 * 这条对流式输出很关键 —— 模型正在写 `**粗` 的那一刻，不能把半截标记吃掉。
 *
 * ## 链接
 *
 * 用 Compose 1.7+ 的 [LinkAnnotation.Url]，是**真正可点击**的链接（不是只换个颜色）。
 * 本工程 Compose UI 1.12.0，满足要求。
 */
@Composable
fun ZhiMarkdown(
    source: String,
    modifier: Modifier = Modifier,
    bodyFontSize: TextUnit = 12.sp,
    /**
     * 这段文本是不是**正在流式写入**。
     *
     * 传 true 时才启用「已完结前缀 + 在写尾部」的分段渲染（见 [settledPrefixLength]）；
     * 默认 false 走整段解析 —— 已定稿的消息不该有任何理由拆开解析。
     *
     * ⚠️ 调用方必须在流式结束时把它翻回 false：尾部是按**行内**渲染的（不做块级解析），
     * 只有整段重解析一次，最后那一段的标题/列表/代码块才会拿到正确的块级样式。
     */
    streaming: Boolean = false,
) {
    val scheme = MiuixTheme.colorScheme
    val inlineStyles = rememberInlineStyles(bodyFontSize)
    val split = remember(source, streaming) { splitForStreaming(source, streaming) }
    val blocks = remember(split.settled) { parseMarkdown(split.settled) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        blocks.forEach { block -> MdBlockView(block, bodyFontSize) }
        // 在写尾部按行内渲染（不是纯文本）：模型正在写的那一段里的 `代码`、**粗体**
        // 照常生效，只有块级语法（标题/列表/围栏）要等它跨过块边界。
        if (split.tail.isNotEmpty()) {
            Text(
                text = rememberInline(split.tail, inlineStyles),
                color = scheme.onSurface,
                fontSize = bodyFontSize,
                lineHeight = bodyFontSize * 1.5f,
            )
        }
    }
}

/**
 * 流式渲染的切分结果。
 *
 * 用 `remember(source, streaming)` 一次性算出，**不要**在组合里各算各的：
 * `settled` 只在跨块时变（几十次），`tail` 每个 delta 都变（短），
 * 于是 `parseMarkdown` 的 key 也只在跨块时失效 —— 这正是这次优化的全部意义。
 */
private class MdStreamSplit(val settled: String, val tail: String)

/**
 * 短于这个长度就不切。
 *
 * 两个理由：一是短文本整段解析本来就比「切一次 + 建两个对象」更划算；
 * 二是不切就没有「尾部按行内渲染」这回事，短消息在流式期间与最终形态**完全一致**，
 * 不会出现"先看着像纯文本、定稿后突然多出标题样式"的跳变。
 */
private const val MinSettledChars = 512

private fun splitForStreaming(source: String, streaming: Boolean): MdStreamSplit {
    if (!streaming) return MdStreamSplit(source, "")
    val cut = settledPrefixLength(source)
    if (cut < MinSettledChars) return MdStreamSplit(source, "")
    return MdStreamSplit(source.substring(0, cut), source.substring(cut))
}

/** 递归渲染一个块。引用块内部会有嵌套的块，所以这里必须能自我调用。 */
@Composable
private fun MdBlockView(block: MdBlock, fontSize: TextUnit) {
    val scheme = MiuixTheme.colorScheme
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
            modifier = Modifier.padding(top = 2.dp),
        )

        is MdBlock.Paragraph -> Text(
            text = rememberInline(block.text, inlineStyles),
            color = scheme.onSurface,
            fontSize = fontSize,
            lineHeight = fontSize * 1.5f,
        )

        is MdBlock.Bullet -> BulletRow(block, fontSize, inlineStyles)

        is MdBlock.Quote -> {
            // 竖条颜色在这里取好：drawBehind 的 lambda 不是 @Composable，里面不能读主题。
            val barColor = scheme.primary.copy(alpha = 0.55f)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // 竖条用 drawBehind 按**实测高度**画，而不是 `height(IntrinsicSize.Min)`
                    // + `fillMaxHeight`：后者要求 Compose 做固有测量，在滚动容器（LazyColumn）
                    // 里属于容易踩坑的构造，而 drawBehind 拿到的是已经量好的 size，零风险。
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
                block.blocks.forEach { inner -> MdBlockView(inner, fontSize) }
            }
        }

        MdBlock.Rule -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .height(1.dp)
                .background(scheme.dividerLine),
        )

        is MdBlock.Code -> CodeBlock(block, fontSize)

        is MdBlock.Table -> MdTableView(block, fontSize, inlineStyles)
    }
}

@Composable
private fun BulletRow(block: MdBlock.Bullet, fontSize: TextUnit, styles: InlineStyles) {
    val scheme = MiuixTheme.colorScheme
    // 每层缩进 12dp；太深时收敛，避免把正文挤成一条窄柱
    val indent = (block.depth * 12).coerceAtMost(48).dp

    Row(
        modifier = Modifier.fillMaxWidth().padding(start = indent),
        verticalAlignment = Alignment.Top,
    ) {
        if (block.task != null) {
            // 任务项用勾选框一样的方块，而不是圆点：语义不同（一个是"待办"，一个是"列举"）
            Icon(
                imageVector = if (block.task) ZhiIcons.done else ZhiIcons.pending,
                contentDescription = if (block.task) "已完成" else "未完成",
                tint = if (block.task) ZhiColors.green() else scheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 2.dp).size(fontSize.value.dp + 2.dp),
            )
        } else {
            Text(
                text = block.marker,
                color = scheme.onSurfaceVariantSummary,
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
    val scheme = MiuixTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = ZhiRadius.inner,
        insideMargin = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        colors = CardDefaults.defaultColors(
            color = scheme.surfaceContainerHighest,
            contentColor = scheme.onSurface,
        ),
    ) {
        Column {
            // 有语言标记时在最上方用小字标出，便于一眼看出是哪种代码
            if (block.lang.isNotBlank()) {
                Text(
                    text = block.lang,
                    color = scheme.onSurfaceVariantSummary,
                    fontSize = ZhiTextScale.Micro,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Text(
                text = block.body,
                color = scheme.onSurface,
                fontSize = (fontSize.value - 1).coerceAtLeast(9f).sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = (fontSize.value + 4).sp,
            )
        }
    }
}

/**
 * 表格。
 *
 * 列宽用 `weight` **等分**而不是按内容自适应：Compose 没有跨行测量，
 * 要做到真正的内容自适应需要自己两趟测量，代价远大于收益；等分在
 * 模型输出的小表格上观感已经足够，且不会因为某一列内容特别长而挤爆布局。
 */
@Composable
private fun MdTableView(table: MdBlock.Table, fontSize: TextUnit, styles: InlineStyles) {
    val scheme = MiuixTheme.colorScheme
    val cols = maxOf(table.header.size, table.rows.maxOfOrNull { it.size } ?: 0)
    if (cols == 0) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = ZhiRadius.inner,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(
            color = scheme.surfaceContainerHighest,
            contentColor = scheme.onSurface,
        ),
    ) {
        Column {
            TableRow(
                cells = table.header,
                cols = cols,
                fontSize = fontSize,
                styles = styles,
                header = true,
            )
            Box(
                modifier = Modifier.fillMaxWidth().height(1.dp).background(scheme.dividerLine),
            )
            table.rows.forEachIndexed { index, row ->
                TableRow(
                    cells = row,
                    cols = cols,
                    fontSize = fontSize,
                    styles = styles,
                    header = false,
                )
                if (index != table.rows.lastIndex) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(1.dp)
                            .background(scheme.dividerLine.copy(alpha = 0.5f)),
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
    val scheme = MiuixTheme.colorScheme
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
                modifier = Modifier.weight(1f).padding(end = if (c == cols - 1) 0.dp else 6.dp),
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
    val scheme = MiuixTheme.colorScheme
    return remember(fontSize, scheme) {
        InlineStyles(
            codeColor = scheme.primary,
            codeBackground = scheme.surfaceContainerHighest,
            linkColor = scheme.primary,
        )
    }
}

/**
 * [inline] 的 `remember` 包装。
 *
 * 每一步都值得：`inline` 会跑一遍 [parseInline]（逐字符扫描 + 大量 substring），
 * 再 `buildAnnotatedString` 建一份带 span 副本的对象。而 `MdBlockView` 的调用点原本是
 * 直接 `inline(block.text, styles)` —— 只要**任何**一次重组（父级状态变化、滚动时
 * 复用、主题切换）就会把每个段落的行内解析重跑一遍。
 *
 * key 用 `(文本, 样式)`：`InlineStyles` 没有 `equals`，靠 [rememberInlineStyles] 返回的
 * 同一个实例保证恒等；它在主题/字号不变时不会换新，所以不会破坏缓存。
 */
@Composable
private fun rememberInline(text: String, styles: InlineStyles): AnnotatedString =
    remember(text, styles) { inline(text, styles) }

/**
 * [MdSpan] 列表 → [AnnotatedString]。
 *
 * 链接走 [withLink] + [LinkAnnotation.Url]，由 Compose 自己处理点击与
 * 按下高亮，不需要手动 `clickable` 或 `LocalUriHandler`。
 */
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
