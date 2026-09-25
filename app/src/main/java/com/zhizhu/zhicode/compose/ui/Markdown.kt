package com.zhizhu.zhicode.compose.ui
import com.zhizhu.zhicode.compose.theme.ZhiRadius

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 轻量 Markdown 渲染器。
 *
 * 为什么不用三方库（如 multiplatform-markdown-renderer）：
 * 这个工程跑在受限的 Termux 环境里，多引一个依赖就多一份版本冲突与离线解析风险；
 * 而实际需要的语法子集很小，且必须复用 Miuix 配色，自写反而更可控。
 *
 * 支持的语法（覆盖「提交计划」「选择」窗口的实际内容）：
 *
 * | 语法 | 渲染 |
 * | --- | --- |
 * | `## 标题` / `### 标题` | 加粗、略大字号 |
 * | `**粗体**` | 加粗 |
 * | `` `行内代码` `` | 等宽 + 底色 |
 * | `- 项目` / `* 项目` | 圆点列表 |
 * | `1. 项目` | 有序列表（保留原编号） |
 * | ` ```代码块``` ` | 独立等宽卡片 |
 * | 空行 | 段落间距 |
 */
@Composable
fun ZhiMarkdown(
    source: String,
    modifier: Modifier = Modifier,
    bodyFontSize: androidx.compose.ui.unit.TextUnit = 12.sp,
) {
    val scheme = MiuixTheme.colorScheme
    val blocks = remember(source) { parseMarkdown(source) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> Text(
                    text = inline(block.text, scheme.primary, scheme.surfaceContainerHighest),
                    color = scheme.onSurface,
                    fontSize = if (block.level <= 2) 13.5.sp else 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 2.dp),
                )

                is MdBlock.Bullet -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.Top,
                ) {
                    Text(
                        text = block.marker,
                        color = scheme.onSurfaceVariantSummary,
                        fontSize = bodyFontSize,
                        fontFamily = if (block.marker == "•") FontFamily.Default else FontFamily.Monospace,
                        modifier = Modifier.widthIn(min = 14.dp),
                    )
                    Text(
                        text = inline(block.text, scheme.primary, scheme.surfaceContainerHighest),
                        color = scheme.onSurface,
                        fontSize = bodyFontSize,
                        lineHeight = bodyFontSize * 1.5f,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }

                is MdBlock.Paragraph -> Text(
                    text = inline(block.text, scheme.primary, scheme.surfaceContainerHighest),
                    color = scheme.onSurface,
                    fontSize = bodyFontSize,
                    lineHeight = bodyFontSize * 1.5f,
                )

                is MdBlock.Code -> Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = ZhiRadius.inner,
                    insideMargin = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    colors = CardDefaults.defaultColors(
                        color = scheme.surfaceContainerHighest,
                        contentColor = scheme.onSurface,
                    ),
                ) {
                    Text(
                        text = block.body,
                        color = scheme.onSurface,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp,
                    )
                }
            }
        }
    }
}

// ---------- 块级解析 ----------

private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock

    /** [marker] 是行首符号：无序列表为 `•`，有序列表为 `1.` 这样的原编号。 */
    data class Bullet(val marker: String, val text: String) : MdBlock

    data class Paragraph(val text: String) : MdBlock
    data class Code(val lang: String, val body: String) : MdBlock
}

/**
 * 识别 `1. ` / `12) ` 这类有序列表标记，返回标记本身（含点/括号）。
 * 不是有序列表时返回 null。
 */
private fun orderedListMarker(line: String): String? {
    val digits = line.takeWhile { it.isDigit() }
    if (digits.isEmpty() || digits.length > 3) return null
    val rest = line.drop(digits.length)
    if (rest.startsWith(". ") || rest.startsWith(") ")) return digits + rest.first() + " "
    return null
}

private fun parseMarkdown(source: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val paragraph = StringBuilder()
    val code = StringBuilder()
    var inCode = false
    var codeLang = ""

    fun flushParagraph() {
        if (paragraph.isNotBlank()) blocks += MdBlock.Paragraph(paragraph.toString().trim())
        paragraph.clear()
    }

    source.split('\n').forEach { raw ->
        val line = raw.trimEnd()
        val trimmed = line.trimStart()
        val isFence = trimmed.startsWith("```")

        when {
            isFence && !inCode -> {
                flushParagraph()
                inCode = true
                codeLang = trimmed.removePrefix("```").trim()
                code.clear()
            }

            isFence && inCode -> {
                blocks += MdBlock.Code(codeLang, code.toString().trimEnd())
                inCode = false
                codeLang = ""
                code.clear()
            }

            inCode -> code.appendLine(line)

            trimmed.startsWith("#") -> {
                flushParagraph()
                val level = trimmed.takeWhile { it == '#' }.length.coerceIn(1, 6)
                blocks += MdBlock.Heading(level, trimmed.drop(level).trim())
            }

            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                flushParagraph()
                blocks += MdBlock.Bullet("•", trimmed.drop(2).trim())
            }

            // 有序列表：保留原编号，避免 "1. … 2. …" 被并成一段
            orderedListMarker(trimmed) != null -> {
                flushParagraph()
                val marker = orderedListMarker(trimmed)!!
                blocks += MdBlock.Bullet(marker, trimmed.drop(marker.length).trim())
            }

            line.isBlank() -> flushParagraph()

            else -> {
                // 续行并入同一段，避免把换行硬渲染成多段
                if (paragraph.isNotEmpty()) paragraph.append(' ')
                paragraph.append(trimmed)
            }
        }
    }

    if (inCode && code.isNotEmpty()) blocks += MdBlock.Code(codeLang, code.toString().trimEnd())
    flushParagraph()
    return blocks
}

// ---------- 行内解析 ----------

/** 处理 `**粗体**` 与 `` `行内代码` ``。未闭合的标记按普通字符输出。 */
private fun inline(text: String, codeColor: Color, codeBackground: Color): AnnotatedString =
    buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end > i) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append(text[i]); i++
                    }
                }

                text[i] == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i) {
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                color = codeColor,
                                background = codeBackground,
                            ),
                        ) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(text[i]); i++
                    }
                }

                else -> {
                    append(text[i]); i++
                }
            }
        }
    }
