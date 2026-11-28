package com.zhizhu.zhicode.compose.data

import java.util.Locale

object VersionCompare {

    /** 一个解析成功的版本。`segments` 已去掉 `v` 前缀与预发布/构建后缀。 */
    data class Parsed(
        val segments: List<Int>,
        val prerelease: String?,
        val raw: String,
    ) {
        /** 补零到 [n] 段，用于两两比较。 */
        fun padded(n: Int): List<Int> = List(n) { segments.getOrElse(it) { 0 } }
    }

    /** 解析一个版本号。 */
    fun parse(text: String?): Parsed? {
        val trimmed = text?.trim().orEmpty()
        if (trimmed.isEmpty()) return null

        // 去 `v` / `V` 前缀（GitHub tag 常见写法）。
        val body = if (trimmed.first() == 'v' || trimmed.first() == 'V') trimmed.drop(1) else trimmed
        if (body.isEmpty()) return null

        // 构建元数据（`+...`）按 semver 规定不参与比较，先切掉。
        val withoutBuild = body.substringBefore('+')

        // 预发布（`-...`）。`0.3.0-rc1` 的预发布是 `rc1`。
        val dashIndex = withoutBuild.indexOf('-')
        val numericPart = if (dashIndex >= 0) withoutBuild.substring(0, dashIndex) else withoutBuild
        val prerelease = if (dashIndex >= 0) withoutBuild.substring(dashIndex + 1).ifEmpty { "" } else null

        if (numericPart.isEmpty()) return null
        val parts = numericPart.split('.')
        if (parts.size > MAX_SEGMENTS) return null

        val segments = ArrayList<Int>(parts.size)
        for (part in parts) {
            // 空段（`1..2`）与含非数字的段（`1.2x`）都算非法：
            // 不去猜 `1.2x` 想表达什么，猜错就是"永远提示有新版本"或"永远不提示"。
            if (part.isEmpty() || part.any { !it.isDigit() }) return null
            val value = part.toIntOrNull() ?: return null
            segments.add(value)
        }
        if (segments.isEmpty()) return null

        return Parsed(segments = segments, prerelease = prerelease, raw = trimmed)
    }

    /** [a] 是否比 [b] 新（严格大于）。 */
    fun isNewer(a: String?, b: String?): Boolean {
        val left = parse(a) ?: return false
        val right = parse(b) ?: return false
        return compare(left, right) > 0
    }

    /** 标准三路比较。调用方保证两者都已解析成功。 */
    fun compare(left: Parsed, right: Parsed): Int {
        val width = maxOf(left.segments.size, right.segments.size)
        val l = left.padded(width)
        val r = right.padded(width)
        for (i in 0 until width) {
            val cmp = l[i].compareTo(r[i])
            if (cmp != 0) return cmp
        }
        // 数字部分相等时比预发布：没有预发布的一方**更大**（`0.3.0 > 0.3.0-rc1`）。
        return when {
            left.prerelease == null && right.prerelease == null -> 0
            left.prerelease == null -> 1
            right.prerelease == null -> -1
            else -> comparePrerelease(left.prerelease, right.prerelease)
        }
    }

    /** 预发布标识符比较（semver 第 11 条的精简版）。 */
    private fun comparePrerelease(left: String, right: String): Int {
        if (left == right) return 0
        val l = left.split('.')
        val r = right.split('.')
        val width = minOf(l.size, r.size)
        for (i in 0 until width) {
            val cmp = compareIdentifier(l[i], r[i])
            if (cmp != 0) return cmp
        }
        return l.size.compareTo(r.size)
    }

    private fun compareIdentifier(left: String, right: String): Int {
        val ln = left.toIntOrNull()
        val rn = right.toIntOrNull()
        return when {
            ln != null && rn != null -> ln.compareTo(rn)
            // 数字段 < 字母段（semver：数字标识符总是低于非数字标识符）。
            ln != null -> -1
            rn != null -> 1
            else -> left.lowercase(Locale.US).compareTo(right.lowercase(Locale.US))
        }
    }

    private const val MAX_SEGMENTS = 6
}
