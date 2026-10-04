package com.zhizhu.zhicode.compose.model

import java.util.Locale

/**
 * 从一段工具输出里挑出"最有用的一行"当错误摘要（纯逻辑，无 Android 依赖）。
 *
 * ## 为什么不能就用第一行
 *
 * 命令失败时输出往往长相是这样：
 *
 * ```
 * Reading package lists...
 * E: Unable to locate package foo
 * ```
 *
 * 第一行只是进展信息，真正的错因在第二行。以前（以及本文件存在之前）取的是
 * "第一条非空行"，于是折叠态摘要恒为 `退出码 100 · Reading package lists...` ——
 * 用户根本看不出错在哪，只能每次都点开。
 *
 * ## 规则（逐字对齐参考实现 `MainActivity.firstUsefulErrorLine`）
 *
 * 逐行扫：
 * 1. 跳过空白行，也跳过我们自己插的 `[stderr]` 标记行（那是流标记，不是内容）；
 * 2. 记住**第一行**可用内容当兜底；
 * 3. 只要某一行命中关键词就立刻返回它；一行都没命中时返回兜底那一行。
 *
 * 关键词用的是 `contains`（`"e:"` 除外，它按**行首**匹配）：
 * - `error` / `failed` —— 通用；
 * - `permission denied` / `unable to` —— 权限与依赖问题；
 * - `e:`（行首）—— apt/dpkg 的错误前缀；
 * - `not found` —— 路径、命令、包名不存在。
 *
 * ## 为什么按下标扫而不是 `split('\n')`
 *
 * 输出上限是 40 000 字符（`WorkspaceViewModel.LIVE_OUTPUT_LIMIT`），而这个函数
 * 在工具行每次重组时都可能被调用（滚动、状态变化、展开/收起）。
 * `split` 会先把整段切成几百个 String，再开始逐行判断 —— 而命中往往发生在
 * 前几行。按下标逐行扫 + 提前 return，命中就停，代价与实际行数成正比。
 */
object ErrorSummary {

    /** 流标记行：它不是内容，不该出现在摘要里。 */
    private const val STDERR_MARKER = "[stderr]"

    /** 命中即返回的关键词（小写比较）。 */
    private val KEYWORDS = listOf("error", "failed", "permission denied", "unable to", "not found")

    /** 行首前缀式的关键词（`e:` 是 apt/dpkg 的错误行前缀）。 */
    private val PREFIX_KEYWORDS = listOf("e:")

    fun firstUsefulLine(text: String): String {
        if (text.isEmpty()) return ""
        var fallback = ""
        var start = 0
        val n = text.length
        while (start <= n) {
            var end = text.indexOf('\n', start)
            if (end < 0) end = n
            var lo = start
            var hi = end
            while (lo < hi && text[lo].isWhitespace()) lo++
            while (hi > lo && text[hi - 1].isWhitespace()) hi--
            if (lo < hi) {
                val line = text.substring(lo, hi)
                if (line != STDERR_MARKER) {
                    if (fallback.isEmpty()) fallback = line
                    if (looksLikeTheCause(line)) return line
                }
            }
            if (end >= n) break
            start = end + 1
        }
        return fallback
    }

    /** 这一行像不像"真正的原因"（见类注释里的关键词表）。 */
    private fun looksLikeTheCause(line: String): Boolean {
        val lower = line.lowercase(Locale.US)
        if (KEYWORDS.any { lower.contains(it) }) return true
        return PREFIX_KEYWORDS.any { lower.startsWith(it) }
    }
}
