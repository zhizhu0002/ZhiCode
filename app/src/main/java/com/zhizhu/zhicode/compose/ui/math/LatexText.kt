package com.zhizhu.zhicode.compose.ui.math

/**
 * 一段正文切出来的片段：普通文本，或一段数学公式。
 *
 * 之所以做成纯数据 + 纯函数，是因为**切分规则**本身是最容易出错、也最值得写单测的部分
 * （定界符、转义、代码跨度里的 `$`、流式时未闭合的那一半），而它不该依赖 Compose。
 */
sealed interface LatexSegment {
    data class Plain(val text: String) : LatexSegment

    /**
     * @param latex 定界符**内部**的内容（不含 `$` / `\(` / `\[`）。
     * @param display 是否为独立公式（`$$…$$` / `\[…\]`）。独立公式在界面上居中、略大。
     */
    data class Formula(val latex: String, val display: Boolean) : LatexSegment
}

/**
 * 把正文切成 [LatexSegment] 序列。
 *
 * ## 支持的定界符
 *
 * | 写法 | 含义 | 来源习惯 |
 * |---|---|---|
 * | `$…$` | 行内公式 | 通用 Markdown / TeX |
 * | `$$…$$` | 独立公式 | 通用 Markdown / TeX |
 * | `\(…\)` | 行内公式 | 原生 LaTeX |
 * | `\[…\]` | 独立公式 | 原生 LaTeX |
 *
 * 四种都要认：模型输出里这四种都常见，只认 `$` 会让相当一部分回答变成一堆反斜杠。
 *
 * ## 三条硬规则
 *
 * 1. **代码跨度里的 `$` 不算公式**。`` `awk '{print $1}'` `` 里的 `$1` 是字段引用，
 *    把它当公式会渲染成乱码；而且这类内容在工具输出里极其常见。
 * 2. **`\$` 是转义的字面美元号**。否则「价格 \$5 到 \$10」会被当成一段公式。
 * 3. **流式时未闭合的定界符原样保留**。模型正在写 `$x^2` 的那一刻，不能把半截吃掉；
 *    等它写出收尾的 `$` 才切换成公式。这条与解析层"保证不丢字符"的约定一致。
 *
 * ## 已知的取舍
 *
 * 跨公式的强调不生效：`**粗 $x$ 体**` 里的 `**` 会被切成三段的边界吃掉，
 * 表现为 `**粗 ` 与 ` 体**` 各自按字面显示。这是刻意的——把行内强调解析搬到
 * 切分之前，就要在公式两侧重建 span，复杂度远大于收益（这种写法在回答里几乎不出现）。
 */
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

/**
 * 从 [from] 开始找一个**可作为行内公式收尾**的 `$`。
 *
 * 跳过被转义的 `\$` 与代码跨度里的反引号段；遇到换行就放弃 —— 行内公式不跨行，
 * 跨行的 `$` 极可能是两处独立的字面美元号（如价格列表）。
 */
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

/**
 * 整段是否**恰好**是一个独立公式。是则返回公式内容，否则返回 null。
 *
 * 独立公式在 Markdown 里通常自成一个段落（前后空行），所以"整段只有一个公式"是最常见
 * 的显示形态。这种情形要居中、且不与正文行高混排；而夹在句子中间的 `$$…$$`
 * （少见但合法）走行内路径即可。
 */
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
