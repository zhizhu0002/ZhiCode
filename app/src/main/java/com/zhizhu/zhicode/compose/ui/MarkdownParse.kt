package com.zhizhu.zhicode.compose.ui

/**
 * Markdown 的**纯 Kotlin 解析层**。
 *
 * ## 为什么和渲染层分开
 *
 * 这个文件刻意**不引用任何 Compose 类型**（只用 Kotlin 标准库），因此可以脱离
 * Android / Compose，直接用 `kotlinc` 编译并跑测试来验证解析正确性 —— 标记解析的
 * 边界情况很多（未闭合围栏、嵌套列表、`*` 的歧义、表格分隔行…），光靠肉眼看界面
 * 很容易漏。渲染层见同目录的 `Markdown.kt`。
 *
 * ## 支持的语法
 *
 * 块级：
 * | 语法 | 说明 |
 * | --- | --- |
 * | `# 标题` ~ `###### 标题` | ATX 标题 |
 * | `标题` + `===` / `---` | Setext 标题（1 / 2 级） |
 * | ` ``` ` 或 `~~~` 围栏 | 代码块，info string 记为语言 |
 * | `- ` `* ` `+ ` | 无序列表（按缩进嵌套） |
 * | `1. ` / `1) ` | 有序列表（保留原编号） |
 * | `- [ ]` / `- [x]` | 任务项 |
 * | `> ` | 引用块（内部**递归解析**，块中可再有块） |
 * | `---` `***` `___` | 分隔线 |
 * | `\| a \| b \|` + `\|---\|---\|` | 表格 |
 *
 * 行内：转义、`` `行内代码` ``、`[文字](链接)`、`![替代](链接)`、`<自动链接>`、
 * 裸 `https://…`、`**粗体**`（或 `__`）、`*斜体*`（或 `_`）、`***粗斜体***`、
 * `~~删除线~~`。
 *
 * ## 有意不支持的
 *
 * 内联 HTML、脚注、定义列表、引用式链接（`[x][1]`）—— 模型回复里几乎不出现，
 * 而解析规则会显著增加复杂度。遇到时按普通文本原样显示，**不会丢字符**。
 */

// ------------------------------------------------------------------ 数据模型

/** 块级元素。 */
internal sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock

    data class Paragraph(val text: String) : MdBlock

    /**
     * 列表项。
     *
     * [depth] 是嵌套层级（0 起）；[task] 非空表示任务项，值即勾选状态
     * （`- [ ]` → false，`- [x]` → true）；[marker] 是无序列表的圆点或有序列表的原编号。
     */
    data class Bullet(
        val marker: String,
        val text: String,
        val depth: Int = 0,
        val task: Boolean? = null,
    ) : MdBlock

    /** 引用块。[blocks] 是剥掉 `>` 之后**递归解析**的结果。 */
    data class Quote(val blocks: List<MdBlock>) : MdBlock

    /** 分隔线。 */
    data object Rule : MdBlock

    /** 代码块。[lang] 来自围栏后的 info string，可能为空。 */
    data class Code(val lang: String, val body: String) : MdBlock

    /** 表格。[rows] 长度可能不齐，渲染时补齐。 */
    data class Table(val header: List<String>, val rows: List<List<String>>) : MdBlock
}

/** 行内片段样式。 */
internal enum class MdStyle { PLAIN, BOLD, ITALIC, BOLD_ITALIC, CODE, STRIKE, LINK }

/**
 * 一段行内文本。
 *
 * [href] 仅在 [style] == [MdStyle.LINK] 时非空。自动链接与裸 URL 的
 * [text] 与 [href] 相同。
 */
internal data class MdSpan(
    val text: String,
    val style: MdStyle = MdStyle.PLAIN,
    val href: String? = null,
)

// ------------------------------------------------------------------ 常量

/** 强调嵌套的递归上限：防御 `**a **b **c …` 这类构造。 */
private const val MAX_INLINE_DEPTH = 8

/** 引用块递归上限，防止病态输入把栈打爆。 */
private const val MAX_BLOCK_DEPTH = 4

/** 行首缩进超过这个层级就不再加深，避免深缩进把内容挤出屏幕。 */
private const val MAX_LIST_DEPTH = 5

/** 这些字符前面的反斜杠是转义，输出时要去掉反斜杠。 */
private const val ESCAPABLE = "\\`*_{}[]()#+-.!>~|"

// ------------------------------------------------------------------ 块级解析

/** 识别 ``` 或 ~~~ 围栏，返回字符与长度；不是围栏时为 null。 */
private fun fenceInfo(trimmed: String): Pair<Char, Int>? {
    val ch = trimmed.firstOrNull() ?: return null
    if (ch != '`' && ch != '~') return null
    val n = trimmed.takeWhile { it == ch }.length
    return if (n >= 3) ch to n else null
}

/** 分隔线：整行只有 `-` / `*` / `_`，至少 3 个，允许中间有空格。 */
private fun isRule(trimmed: String): Boolean {
    if (trimmed.isEmpty()) return false
    val ch = trimmed[0]
    if (ch != '-' && ch != '*' && ch != '_') return false
    var count = 0
    for (c in trimmed) {
        when {
            c == ch -> count++
            c == ' ' || c == '\t' -> Unit
            else -> return false
        }
    }
    return count >= 3
}

/** Setext 标题的下划线行：整行只有 `=` 或只有 `-`。 */
private fun setextLevel(trimmed: String): Int? = when {
    trimmed.isNotEmpty() && trimmed.all { it == '=' } -> 1
    trimmed.isNotEmpty() && trimmed.all { it == '-' } -> 2
    else -> null
}

/** 表格分隔行：`|---|:--:|---|` 这类。 */
private fun isTableSeparator(trimmed: String): Boolean {
    if (!trimmed.contains('-') || !trimmed.contains('|')) return false
    return trimmed.all { it == '|' || it == '-' || it == ':' || it == ' ' || it == '\t' }
}

/** 按 `|` 拆单元格，并去掉首尾因行首/行尾竖线产生的空串。 */
private fun splitRow(line: String): List<String> {
    var s = line.trim()
    if (s.startsWith("|")) s = s.substring(1)
    if (s.endsWith("|") && !s.endsWith("\\|")) s = s.dropLast(1)
    return s.split('|').map { it.trim() }
}

/** 有序列表标记：`1. ` / `12) `，返回标记本身（含点/括号与尾随空格）。 */
private fun orderedListMarker(line: String): String? {
    val digits = line.takeWhile { it.isDigit() }
    // 3 位以上基本是正文里的数字（如年份），不当列表
    if (digits.isEmpty() || digits.length > 3) return null
    val rest = line.drop(digits.length)
    return when {
        rest.startsWith(". ") -> digits + ". "
        rest.startsWith(") ") -> digits + ") "
        else -> null
    }
}

/** 无序列表标记：返回"标记之后的正文起点"；不是无序项时为 null。 */
private fun unorderedBodyStart(line: String): Int? {
    if (line.length < 2) return null
    val ch = line[0]
    if (ch != '-' && ch != '*' && ch != '+') return null
    if (line[1] != ' ') return null
    // `- - -` 是分隔线，不是列表项
    if (isRule(line)) return null
    return 2
}

/** 统计行首缩进（制表符按 4 空格计）。 */
private fun indentOf(line: String): Int {
    var n = 0
    for (c in line) {
        when (c) {
            ' ' -> n++
            '\t' -> n += 4
            else -> return n
        }
    }
    return n
}

/**
 * 解析整篇 Markdown。
 *
 * 单遍扫描 + 少量前视（表格与 Setext 需要看相邻行）。递归只发生在引用块。
 */
internal fun parseMarkdown(source: String, depth: Int = 0): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val paragraph = StringBuilder()

    // 正在累积的列表项
    var bulletMarker: String? = null
    var bulletDepth = 0
    var bulletTask: Boolean? = null
    val bulletText = StringBuilder()

    val lines = source.replace("\r\n", "\n").replace('\r', '\n').split('\n')
    var i = 0

    fun flushParagraph() {
        if (paragraph.isNotBlank()) blocks += MdBlock.Paragraph(paragraph.toString().trim())
        paragraph.clear()
    }

    fun flushBullet() {
        val marker = bulletMarker
        if (marker != null) {
            blocks += MdBlock.Bullet(
                marker = marker,
                text = bulletText.toString().trim(),
                depth = bulletDepth,
                task = bulletTask,
            )
        }
        bulletMarker = null
        bulletTask = null
        bulletText.clear()
    }

    fun startBullet(marker: String, body: String, indent: Int, task: Boolean?) {
        flushParagraph()
        flushBullet()
        bulletMarker = marker
        bulletDepth = (indent / 2).coerceIn(0, MAX_LIST_DEPTH)
        bulletTask = task
        bulletText.append(body)
    }

    while (i < lines.size) {
        val raw = lines[i]
        val line = raw.trimEnd()
        val trimmed = line.trimStart()
        val indent = indentOf(line)

        // ---- 围栏代码块：内部一律按字面处理，先找闭合围栏 ----
        val fence = fenceInfo(trimmed)
        if (fence != null) {
            flushParagraph()
            flushBullet()
            val (ch, len) = fence
            val lang = trimmed.drop(len).trim()
            val body = StringBuilder()
            i++
            while (i < lines.size) {
                val t = lines[i].trimEnd()
                val f2 = fenceInfo(t.trimStart())
                // 闭合围栏：同字符且长度不短于开启围栏
                if (f2 != null && f2.first == ch && f2.second >= len) {
                    i++
                    break
                }
                body.appendLine(t)
                i++
            }
            // 未闭合（流式输出中间态）也要产出代码块，否则内容会整段消失
            blocks += MdBlock.Code(lang, body.toString().trimEnd('\n'))
            continue
        }

        // ---- 表格：需要前视一行分隔行 ----
        if (trimmed.contains('|') && indent < 4 &&
            i + 1 < lines.size && isTableSeparator(lines[i + 1].trimStart()) &&
            trimmed.count { it == '|' } >= 2
        ) {
            flushParagraph()
            flushBullet()
            val header = splitRow(trimmed)
            i += 2
            val rows = mutableListOf<List<String>>()
            while (i < lines.size) {
                val t = lines[i].trimEnd()
                if (t.isBlank() || !t.trimStart().contains('|')) break
                rows += splitRow(t)
                i++
            }
            blocks += MdBlock.Table(header, rows)
            continue
        }

        when {
            // ---- 空行 ----
            line.isBlank() -> {
                flushParagraph()
                flushBullet()
                i++
            }

            // ---- Setext 标题必须在 isRule 之前判断 ----
            //
            // `段落\n-----` 在 CommonMark 里是 2 级标题，而不是"段落 + 分隔线"。
            // 这两条规则用的是同一批字符（整行 `-` 或 `=`），所以顺序错了就会
            // 把标题解析成分隔线 —— 已由 kotlinc 测试用例「Setext」抓到。
            // 只有"上面确实攒了一个段落"时才算 Setext，否则 `---` 仍是分隔线。
            paragraph.isNotEmpty() && setextLevel(trimmed) != null -> {
                blocks += MdBlock.Heading(setextLevel(trimmed)!!, paragraph.toString().trim())
                paragraph.clear()
                flushBullet()
                i++
            }

            // ---- 分隔线 ----
            isRule(trimmed) -> {
                flushParagraph()
                flushBullet()
                blocks += MdBlock.Rule
                i++
            }

            // ---- ATX 标题 ----
            trimmed.startsWith("#") -> {
                val level = trimmed.takeWhile { it == '#' }.length
                val rest = trimmed.drop(level)
                // `#foo` 不是标题，`# foo` 才是
                if (level <= 6 && (rest.isEmpty() || rest[0] == ' ')) {
                    flushParagraph()
                    flushBullet()
                    // 结尾的闭合 #（`## 标题 ##`）要去掉
                    blocks += MdBlock.Heading(level, rest.trim().trimEnd('#').trimEnd())
                    i++
                } else {
                    if (paragraph.isNotEmpty()) paragraph.append('\n')
                    paragraph.append(trimmed)
                    i++
                }
            }

            // ---- 引用块：收集连续的 > 行后递归解析 ----
            trimmed.startsWith(">") && depth < MAX_BLOCK_DEPTH -> {
                flushParagraph()
                flushBullet()
                val inner = StringBuilder()
                while (i < lines.size) {
                    val tt = lines[i].trimEnd().trimStart()
                    if (!tt.startsWith(">")) break
                    // 只剥掉一层 `>`，并去掉紧随其后的一个空格
                    inner.appendLine(tt.removePrefix(">").removePrefix(" "))
                    i++
                }
                val innerBlocks = parseMarkdown(inner.toString().trimEnd('\n'), depth + 1)
                if (innerBlocks.isNotEmpty()) blocks += MdBlock.Quote(innerBlocks)
            }

            // ---- 无序列表项 ----
            unorderedBodyStart(trimmed) != null -> {
                var body = trimmed.drop(unorderedBodyStart(trimmed)!!)
                // 任务项 `- [ ]` / `- [x]`
                var task: Boolean? = null
                val tl = body.lowercase()
                if (tl.startsWith("[ ]")) {
                    task = false
                    body = body.drop(3).trimStart()
                } else if (tl.startsWith("[x]")) {
                    task = true
                    body = body.drop(3).trimStart()
                }
                // 标记按层级选：一级实心圆点，更深用空心
                val marker = if ((indent / 2) == 0) "•" else "◦"
                startBullet(marker, body, indent, task)
                i++
            }

            // ---- 有序列表项 ----
            orderedListMarker(trimmed) != null -> {
                val marker = orderedListMarker(trimmed)!!
                startBullet(marker, trimmed.drop(marker.length).trimStart(), indent, null)
                i++
            }

            // ---- 列表项的续行（懒续行）----
            bulletMarker != null -> {
                if (bulletText.isNotEmpty()) bulletText.append('\n')
                bulletText.append(trimmed)
                i++
            }

            // ---- 普通段落 ----
            else -> {
                // 续行**保留换行**，而不是按 CommonMark 合并成一个空格。
                //
                // 这是个有意的偏离，理由是内容来源：
                //  · `appendInfo` 生成的提示卡（如「会话状态」）用 `\n` 表达"一行一项"，
                //    合并成空格会让整块糊成一段 —— 这是实测发现的回归。
                //  · 模型的回复也大量用单换行分行（步骤、并列说明），保留更贴近它的本意。
                // 段落内部的换行由 Text 直接渲染成换行，列表/标题等块级语法不受影响。
                if (paragraph.isNotEmpty()) paragraph.append('\n')
                paragraph.append(trimmed)
                i++
            }
        }
    }

    flushParagraph()
    flushBullet()
    return blocks
}

// ------------------------------------------------------------------ 行内解析

/** 强调能否在此处开启：标记之后不能是空白。 */
private fun canOpen(text: String, at: Int, delim: String): Boolean {
    val after = at + delim.length
    return after < text.length && !text[after].isWhitespace()
}

/** 强调能否在此处闭合：标记之前不能是空白。 */
private fun canClose(text: String, at: Int): Boolean =
    at > 0 && !text[at - 1].isWhitespace()

/** 合并外层与内层样式（例如粗体里再套斜体 → 粗斜体）。 */
private fun mergeStyle(outer: MdStyle, inner: MdStyle): MdStyle = when {
    inner == MdStyle.PLAIN -> outer
    inner == MdStyle.CODE || inner == MdStyle.LINK -> inner
    outer == MdStyle.STRIKE -> MdStyle.STRIKE
    (outer == MdStyle.BOLD && inner == MdStyle.ITALIC) ||
        (outer == MdStyle.ITALIC && inner == MdStyle.BOLD) -> MdStyle.BOLD_ITALIC
    else -> outer
}

private class Emphasis(
    val contentStart: Int,
    val contentEnd: Int,
    val next: Int,
    val style: MdStyle,
)

/**
 * 在 [i] 处尝试匹配强调标记。
 *
 * 顺序很关键：`***` 必须排在 `**` 前、`**` 排在 `*` 前，否则 `**粗体**` 会被
 * 当成"一个 `*` + 内容 + 一个 `*`"。`_` 系列额外要求词边界，避免 `snake_case` 变斜体。
 */
private fun tryEmphasis(text: String, i: Int): Emphasis? {
    val candidates = listOf(
        "***" to MdStyle.BOLD_ITALIC,
        "___" to MdStyle.BOLD_ITALIC,
        "**" to MdStyle.BOLD,
        "__" to MdStyle.BOLD,
        "*" to MdStyle.ITALIC,
        "_" to MdStyle.ITALIC,
    )
    for ((delim, style) in candidates) {
        if (!text.startsWith(delim, i)) continue
        if (delim[0] == '_') {
            // 开启侧词边界
            val beforeOk = i == 0 || !text[i - 1].isLetterOrDigit()
            if (!beforeOk) continue
        }
        if (!canOpen(text, i, delim)) continue
        val close = text.indexOf(delim, i + delim.length)
        if (close <= i) continue
        // 中间不能是空内容
        if (close == i + delim.length) continue
        if (!canClose(text, close)) continue
        if (delim[0] == '_') {
            // 闭合侧词边界
            val afterClose = close + delim.length
            val afterOk = afterClose >= text.length || !text[afterClose].isLetterOrDigit()
            if (!afterOk) continue
        }
        return Emphasis(i + delim.length, close, close + delim.length, style)
    }
    return null
}

/**
 * 解析一段行内文本为片段序列。
 *
 * 规则（有意简化，非严格 CommonMark）：
 *  · 行内代码优先级最高，其内部不再解析；
 *  · 强调必须"开标记后非空白、闭标记前非空白"，避免把 `2 * 3 * 4` 当强调；
 *  · 未闭合的标记按普通字符原样输出，**不丢字符**。
 */
internal fun parseInline(text: String, depth: Int = 0): List<MdSpan> {
    if (depth > MAX_INLINE_DEPTH) return listOf(MdSpan(text))

    val out = mutableListOf<MdSpan>()
    val plain = StringBuilder()

    fun flush() {
        if (plain.isNotEmpty()) {
            out += MdSpan(plain.toString())
            plain.clear()
        }
    }

    fun emit(body: String, style: MdStyle, href: String? = null) {
        flush()
        // 递归解析内部，这样 `**粗体里有 `代码`**` 也对
        val inner = if (style == MdStyle.CODE || style == MdStyle.LINK) {
            listOf(MdSpan(body, style, href))
        } else {
            parseInline(body, depth + 1).map { it.copy(style = mergeStyle(style, it.style)) }
        }
        out += inner
    }

    var i = 0
    while (i < text.length) {
        val c = text[i]

        // ---- 转义 ----
        if (c == '\\' && i + 1 < text.length && ESCAPABLE.indexOf(text[i + 1]) >= 0) {
            plain.append(text[i + 1])
            i += 2
            continue
        }

        // ---- 行内代码 ----
        if (c == '`') {
            val ticks = text.substring(i).takeWhile { it == '`' }.length
            val delim = "`".repeat(ticks)
            val close = text.indexOf(delim, i + ticks)
            if (close > i) {
                // 代码内部去掉首尾各一个空格（CommonMark 的规则）
                var body = text.substring(i + ticks, close)
                if (body.length >= 2 && body.startsWith(" ") && body.endsWith(" ")) {
                    body = body.substring(1, body.length - 1)
                }
                emit(body, MdStyle.CODE)
                i = close + ticks
                continue
            }
            // 未闭合：原样输出
            plain.append(delim)
            i += ticks
            continue
        }

        // ---- 图片 / 链接 ----
        if (c == '[' || (c == '!' && i + 1 < text.length && text[i + 1] == '[')) {
            val isImage = c == '!'
            val open = if (isImage) i + 1 else i
            val closeBracket = text.indexOf(']', open + 1)
            if (closeBracket > open && closeBracket + 1 < text.length && text[closeBracket + 1] == '(') {
                val closeParen = text.indexOf(')', closeBracket + 2)
                if (closeParen > closeBracket) {
                    val label = text.substring(open + 1, closeBracket)
                    val dest = text.substring(closeBracket + 2, closeParen).trim()
                    // 图片只用替代文字表示，**不加载远程图片**：
                    // 既慢又涉及网络权限，而模型回复里图片链接几乎不出现
                    emit(label.ifEmpty { dest }, MdStyle.LINK, dest.ifEmpty { null })
                    i = closeParen + 1
                    continue
                }
            }
        }

        // ---- 自动链接 <https://…> ----
        if (c == '<') {
            val close = text.indexOf('>', i + 1)
            if (close > i) {
                val inner = text.substring(i + 1, close)
                if (inner.startsWith("http://") || inner.startsWith("https://")) {
                    emit(inner, MdStyle.LINK, inner)
                    i = close + 1
                    continue
                }
            }
        }

        // ---- 裸 URL ----
        if (text.startsWith("http://", i) || text.startsWith("https://", i)) {
            // 只在词边界起判：前面是字母数字时不当作 URL
            val prevOk = i == 0 || !text[i - 1].isLetterOrDigit()
            if (prevOk) {
                var end = i
                while (end < text.length && !text[end].isWhitespace() && text[end] !in "<>\"") end++
                // 结尾的句读与右括号不属于 URL
                while (end > i && text[end - 1] in ".,;:!?)\u3002\uff0c\uff09") end--
                if (end > i + 8) {
                    val url = text.substring(i, end)
                    emit(url, MdStyle.LINK, url)
                    i = end
                    continue
                }
            }
        }

        // ---- 删除线 ~~ ----
        if (text.startsWith("~~", i) && canOpen(text, i, "~~")) {
            val close = text.indexOf("~~", i + 2)
            if (close > i + 2 && canClose(text, close)) {
                emit(text.substring(i + 2, close), MdStyle.STRIKE)
                i = close + 2
                continue
            }
        }

        // ---- 强调：先试更长的分隔符，否则 ** 会被 * 抢先匹配 ----
        val emph = tryEmphasis(text, i)
        if (emph != null) {
            emit(text.substring(emph.contentStart, emph.contentEnd), emph.style)
            i = emph.next
            continue
        }

        plain.append(c)
        i++
    }

    flush()
    return out.ifEmpty { listOf(MdSpan("")) }
}
