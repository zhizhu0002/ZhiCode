package com.zhizhu.zhicode.compose.data

import java.util.Locale

/**
 * 版本号比较（更新检查用）。
 *
 * ## 为什么是 `object` + public（不是 `internal`）
 *
 * 它的调用方只有 `WorkspaceViewModel`，看起来该是 `internal`。但
 * `test-jvm-fast.sh` 把**主源码与测试源码分成两次 kotlinc 调用**编译
 * （两个模块），`internal` 在那里不跨模块可见 —— 于是 `VersionCompareTest`
 * 编不过。这与同目录的 `FileFormat` / `LiveOutput` 等纯逻辑对象一致：
 * 它们都是 public，理由相同。
 *
 * ## 为什么不能用字符串比较
 *
 * 字符串比较里 `"0.9" > "0.30"`（因为 `'9' > '3'`），而版本号语义正好相反。
 * 这个错误不会崩、不会报错，只会让"有新版本"提示永远不出现 —— 最难发现的那种。
 *
 * ## 支持的形态
 *
 * - 语义化版本 `1.2.3`，段数不必相等（`0.2` 与 `0.2.0` **相等**，缺的段按 0 补）
 * - 可选 `v` 前缀（GitHub tag 常见 `v0.3.0`）
 * - 可选的预发布后缀 `-beta` / `-rc1` / `-alpha.2`：**预发布小于同号正式版**
 *   （`0.3.0-beta < 0.3.0`），这是 semver 的规定，也是用户期望
 * - 可选的构建元数据 `+build7`：**忽略**（semver 规定它不参与比较）
 *
 * ## 解析不了就如实说解析不了
 *
 * [parse] 对非法输入返回 `null`（而不是当 0）。调用方据此**不显示**"有新版本"，
 * 而不是拿 0 去比出"永远有新版本"。宁可少一个提示，也不要一个假的提示。
 */
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

    /**
     * 解析一个版本号。**解析不了返回 `null`**。
     *
     * 数字段上限 6 个：超过这个数量的版本号（`1.2.3.4.5.6.7`）不是我们要处理的东西，
     * 而无限接受等于给了一个"输入任意长字符串"的入口。
     */
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

    /**
     * [a] 是否比 [b] 新（严格大于）。
     *
     * **任一侧解析不了就返回 `false`** —— 见类头注释：解析不了时不提示更新，
     * 而不是猜一个结果。
     */
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

    /**
     * 预发布标识符比较（semver 第 11 条的精简版）。
     *
     * 逐段比：纯数字段按数值比，其它按字典序；数字段**小于**字母段；
     * 段数少的**小于**段数多的（`alpha < alpha.1`）。
     * 大小写不敏感 —— `Beta` 与 `beta` 是同一个东西。
     */
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
