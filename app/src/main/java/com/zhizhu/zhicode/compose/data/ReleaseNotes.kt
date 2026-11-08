package com.zhizhu.zhicode.compose.data

import com.zhizhu.zhicode.compose.model.ChangeLogItem
import com.zhizhu.zhicode.compose.model.ChangeLogSection

/**
 * 把 Release `body` 的 Markdown 分流成 [ChangeLogSection]。
 *
 * ## 为什么只有"分流"，没有"渲染"
 *
 * 更新页要显示的是**版本说明**，不是一篇要按 Markdown 排版渲染的文档。
 * 引一个 Markdown 库意味着几十 KB 的代码 + 一套自己的样式表，而这里只需要
 * 三种东西：**小节标题**、**条目标题**、**正文**。所以按行看一眼前缀就够了：
 *
 * | 行 | 归类 |
 * |---|---|
 * | `# X` / `## X` / `### X` | 新开一个小节，标题为 `X` |
 * | `- X` / `* X` / `+ X` | 一个条目，`X` 里若有 `**粗体**` 就当条目标题 |
 * | 其它非空行 | 上一条的正文；还没有条目时归到"概览"小节 |
 *
 * 输出结构与本地自带的 [com.zhizhu.zhicode.compose.model.CHANGE_LOG] 完全一致，
 * 于是页面用**同一个渲染器**画两种来源 —— 用户看不出这段是远端来的还是本地写死的。
 *
 * ## 为什么是 `object` + public（不是 `internal`）
 *
 * 它的调用方只有 `UpdatePage`，看起来该是 `internal`。但 `test-jvm-fast.sh`
 * 把**主源码与测试源码分成两次 kotlinc 调用**编译（两个模块），`internal`
 * 在那里不跨模块可见 —— 于是 `ReleaseNotesTest` 编不过。同目录的
 * `VersionCompare` 与既有 `FileFormat` / `LiveOutput` 同理，都是 public。
 *
 * ## 为什么不解析行内样式
 *
 * `**粗体**` / `` `代码` `` / `[链接](url)` 一律**原样保留在文本里**，
 * 由页面统一按纯文本显示。理由：本地那份说明是纯文本，如果远端那份做了富文本，
 * 同一页里两种来源就会长相不一致 —— 而那正是我们要避免的。
 * 唯一的例外是条目前缀：`- **标题**：正文` 这种最常见的写法会被拆成
 * [ChangeLogItem.title] + [ChangeLogItem.detail]，否则每条都会以一堆星号开头。
 */
object ReleaseNotes {

    /** 没有 `#` 小节标题时，正文归到这个小节。 */
    private const val DEFAULT_SECTION = "更新内容"

    /** 一次最多解析多少小节/条目 —— 防止一段超长 body 在界面上铺出几千行。 */
    private const val MAX_SECTIONS = 24
    private const val MAX_ITEMS_PER_SECTION = 60

    /** 单个字段的字符上限。GitHub 的 release 正文本身可以很长，但显示不需要。 */
    private const val MAX_FIELD_CHARS = 600

    /**
     * 分流。空输入或全是空行时返回**空列表**（不是"一个空小节"）——
     * 页面据此显示"这个版本的 Release 没有写说明"，比一个空标题诚实。
     */
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

    /**
     * 取 `#` 标题的正文（去掉井号）。
     *
     * 只认 1–3 级：更深的层级（`####`）在手机上已经是正文的视觉重量，
     * 当成小节标题会把结构拉得比实际更碎。
     */
    private fun headingText(line: String): String? {
        if (!line.startsWith("#")) return null
        val hashes = line.takeWhile { it == '#' }.length
        if (hashes > 3) return null
        val rest = line.drop(hashes).trim()
        if (rest.isEmpty()) return null
        return clip(rest)
    }

    /**
     * 过深的 `####` 标题：去掉落井号后当正文。
     *
     * 留着井号会让界面上出现一串 `####` —— 那是标记语言泄漏到界面里，
     * 而这一行的内容本身是有价值的（它就是一条说明）。
     */
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

    /**
     * 把 `**标题**：正文` / `标题：正文` 拆成两段。
     *
     * 只在**分隔符之前**足够短（≤ 40 字）时才拆：长句里的冒号是标点，
     * 不是"这条的标题到此结束"。拆错了会把一句话劈成两行，比不拆更难看。
     */
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
