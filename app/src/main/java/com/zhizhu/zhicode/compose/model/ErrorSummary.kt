package com.zhizhu.zhicode.compose.model

import java.util.Locale

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
