package com.zhizhu.zhicode.compose.ui.math

/** 一段正文切出来的片段：普通文本，或一段数学公式。 */
sealed interface LatexSegment {
    data class Plain(val text: String) : LatexSegment

    /**
     * @param latex 定界符**内部**的内容（不含 `$` / `\(` / `\[`）。
     * @param display 是否为独立公式（`$$…$$` / `\[…\]`）。独立公式在界面上居中、略大。
     */
    data class Formula(val latex: String, val display: Boolean) : LatexSegment
}

fun splitLatex(source: String): List<LatexSegment> {
    if (source.isEmpty()) return emptyList()
    // 快速退出：绝大多数段落里没有任何定界符，不必走扫描。
    if (!source.contains('$') && !source.contains("\\(") && !source.contains("\\[")) {
        return listOf(LatexSegment.Plain(source))
    }

    val out = ArrayList<LatexSegment>()
    val plain = StringBuilder()
    var i = 0

    fun flushPlain() {
        if (plain.isNotEmpty()) {
            out.add(LatexSegment.Plain(plain.toString()))
            plain.setLength(0)
        }
    }

    while (i < source.length) {
        val c = source[i]

        // ---- 代码跨度：整段原样跳过，内部的 `$` 不参与公式识别 ----
        if (c == '`') {
            val close = source.indexOf('`', startIndex = i + 1)
            val end = if (close >= 0) close + 1 else source.length
            plain.append(source, i, end)
            i = end
            continue
        }

        // ---- 转义的美元号 ----
        if (c == '\\' && i + 1 < source.length && source[i + 1] == '$') {
            plain.append('$')
            i += 2
            continue
        }

        // ---- \[ … \] ：独立公式 ----
        if (c == '\\' && i + 1 < source.length && source[i + 1] == '[') {
            val close = source.indexOf("\\]", startIndex = i + 2)
            if (close >= 0) {
                flushPlain()
                out.add(LatexSegment.Formula(source.substring(i + 2, close).trim(), display = true))
                i = close + 2
                continue
            }
            // 未闭合：流式时保留原文，等收尾符来了再切。
            plain.append(source, i, source.length)
            i = source.length
            continue
        }

        // ---- \( … \) ：行内公式 ----
        if (c == '\\' && i + 1 < source.length && source[i + 1] == '(') {
            val close = source.indexOf("\\)", startIndex = i + 2)
            if (close >= 0) {
                flushPlain()
                out.add(LatexSegment.Formula(source.substring(i + 2, close).trim(), display = false))
                i = close + 2
                continue
            }
            plain.append(source, i, source.length)
            i = source.length
            continue
        }

        // ---- $$ … $$ ：独立公式（必须先于单个 `$` 判断） ----
        if (c == '$' && i + 1 < source.length && source[i + 1] == '$') {
            val close = source.indexOf("$$", startIndex = i + 2)
            if (close >= 0) {
                flushPlain()
                out.add(LatexSegment.Formula(source.substring(i + 2, close).trim(), display = true))
                i = close + 2
                continue
            }
            // 未闭合的 `$$`：按字面保留。注意这里**不能**退而按单个 `$` 处理 ——
            // 那会把 `$$\frac{1}{2}$$` 中正在输入的前半段误认成行内公式。
            plain.append("$$")
            i += 2
            continue
        }

        // ---- $ … $ ：行内公式 ----
        if (c == '$') {
            val close = findInlineDollarClose(source, i + 1)
            if (close >= 0) {
                val body = source.substring(i + 1, close)
                // 单字符或空白内容不当作公式：`$ ` 与 `$x$` 的区别在于有没有内容，
                // 而"价格 $5" 这类写法在中文回答里很常见，误判的代价是整段被吃掉。
                if (body.isNotBlank()) {
                    flushPlain()
                    out.add(LatexSegment.Formula(body.trim(), display = false))
                    i = close + 1
                    continue
                }
            }
            // 未闭合或内容为空：原样保留那个 `$`。
            plain.append('$')
            i += 1
            continue
        }

        plain.append(c)
        i += 1
    }

    flushPlain()

    return out
}

/** 从 [from] 开始找一个**可作为行内公式收尾**的 `$`。 */
private fun findInlineDollarClose(source: String, from: Int): Int {
    var i = from
    while (i < source.length) {
        when {
            source[i] == '\n' -> return -1
            source[i] == '\\' && i + 1 < source.length && source[i + 1] == '$' -> i += 2
            source[i] == '`' -> {
                val close = source.indexOf('`', startIndex = i + 1)
                if (close < 0) return -1
                i = close + 1
            }
            source[i] == '$' -> return i
            else -> i += 1
        }
    }
    return -1
}

/** 整段是否**恰好**是一个独立公式。 */
fun aloneDisplayFormula(text: String): String? {
    val trimmed = text.trim()
    val body = when {
        trimmed.startsWith("$$") && trimmed.endsWith("$$") && trimmed.length > 4 ->
            trimmed.substring(2, trimmed.length - 2)
        trimmed.startsWith("\\[") && trimmed.endsWith("\\]") && trimmed.length > 4 ->
            trimmed.substring(2, trimmed.length - 2)
        else -> return null
    }
    // 中间还有第二个定界符说明这不是"一个公式"，而是两段公式排在一行 —— 交给行内路径。
    if (body.contains("$$") || body.contains("\\[")) return null
    return body.trim().ifBlank { null }
}
