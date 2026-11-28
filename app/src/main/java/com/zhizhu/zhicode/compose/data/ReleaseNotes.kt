package com.zhizhu.zhicode.compose.data

import com.zhizhu.zhicode.compose.model.ChangeLogItem
import com.zhizhu.zhicode.compose.model.ChangeLogSection

object ReleaseNotes {

    /** 没有 `#` 小节标题时，正文归到这个小节。 */
    private const val DEFAULT_SECTION = "更新内容"

    /** 一次最多解析多少小节/条目 —— 防止一段超长 body 在界面上铺出几千行。 */
    private const val MAX_SECTIONS = 24
    private const val MAX_ITEMS_PER_SECTION = 60

    /** 单个字段的字符上限。GitHub 的 release 正文本身可以很长，但显示不需要。 */
    private const val MAX_FIELD_CHARS = 600

    /** 分流。 */
    fun parse(body: String?): List<ChangeLogSection> {
        val text = body?.replace("\r\n", "\n")?.replace('\r', '\n').orEmpty()
        if (text.isBlank()) return emptyList()

        val sections = ArrayList<ChangeLogSection>()
        var currentTitle = DEFAULT_SECTION
        val currentItems = ArrayList<ChangeLogItem>()

        fun flushSection() {
            if (currentItems.isEmpty()) return
            if (sections.size >= MAX_SECTIONS) return
            sections.add(ChangeLogSection(title = currentTitle, items = currentItems.toList()))
            currentItems.clear()
        }

        for (rawLine in text.split('\n')) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            if (isRule(line)) continue

            val heading = headingText(line)
            if (heading != null) {
                flushSection()
                currentTitle = heading
                continue
            }

            if (currentItems.size >= MAX_ITEMS_PER_SECTION) continue
            val bullet = bulletText(line)
            if (bullet != null) {
                val (title, detail) = splitTitleAndDetail(bullet)
                currentItems.add(ChangeLogItem(title = title, detail = detail))
                continue
            }

            // 过深的 `####` 标题：去掉落井号后当正文。留着会让界面上出现一串 `####`。
            val bodyLine = stripDeepHeading(line)

            // 普通正文行：并进上一条；上一条还不存在时自己就是一条（标题为空）。
            if (currentItems.isEmpty()) {
                currentItems.add(ChangeLogItem(title = "", detail = clip(bodyLine)))
            } else {
                val last = currentItems.removeAt(currentItems.lastIndex)
                val merged = if (last.detail.isEmpty()) bodyLine else last.detail + "\n" + bodyLine
                currentItems.add(last.copy(detail = clip(merged)))
            }
        }
        flushSection()
        return sections
    }

    /** `---` / `***` / `___`：Markdown 的水平分割线，界面上由 Divider 呈现，不是内容。 */
    private fun isRule(line: String): Boolean {
        if (line.length < 3) return false
        val c = line.first()
        if (c != '-' && c != '*' && c != '_') return false
        return line.all { it == c }
    }

    private fun headingText(line: String): String? {
        if (!line.startsWith("#")) return null
        val hashes = line.takeWhile { it == '#' }.length
        if (hashes > 3) return null
        val rest = line.drop(hashes).trim()
        if (rest.isEmpty()) return null
        return clip(rest)
    }

    private fun stripDeepHeading(line: String): String {
        if (!line.startsWith("####")) return line
        return line.trimStart('#').trim()
    }

    /** 取 `-` / `*` / `+` 列表项的正文。缩进的子列表也认（先 trim 过）。 */
    private fun bulletText(line: String): String? {        val marker = line.firstOrNull() ?: return null
        if (marker != '-' && marker != '*' && marker != '+') return null
        // `-` 后面必须跟空白，否则 `-beta` 这种正文会被误当成列表项。
        if (line.length < 2 || !line[1].isWhitespace()) return null
        val rest = line.drop(1).trim()
        if (rest.isEmpty()) return null
        return clip(rest)
    }

    /** 把 `**标题**：正文` / `标题：正文` 拆成两段。 */
    private fun splitTitleAndDetail(text: String): Pair<String, String> {
        val stripped = text.removePrefix("- ").trim()
        // 先去首尾的粗体标记，命中 `**标题**：正文` 这种最常见的写法。
        val boldEnd = stripped.indexOf("**", startIndex = 2)
        if (stripped.startsWith("**") && boldEnd > 2) {
            val title = stripped.substring(2, boldEnd).trim().trimEnd(':', '：').trim()
            val rest = stripped.substring(boldEnd + 2).trim().trimStart(':', '：').trim()
            if (title.isNotEmpty()) return clip(title) to clip(rest)
        }
        for (sep in listOf("：", ":")) {
            val index = stripped.indexOf(sep)
            if (index in 1..TITLE_MAX_CHARS) {
                val title = stripped.substring(0, index).trim()
                val rest = stripped.substring(index + 1).trim()
                if (title.isNotEmpty() && rest.isNotEmpty()) return clip(title) to clip(rest)
            }
        }
        return "" to clip(stripped)
    }

    private fun clip(text: String): String =
        if (text.length <= MAX_FIELD_CHARS) text else text.take(MAX_FIELD_CHARS) + "…"

    /** 拆分标题时允许的标题最大长度（字符）。 */
    private const val TITLE_MAX_CHARS = 40
}
