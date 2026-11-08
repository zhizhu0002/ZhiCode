package com.zhizhu.zhicode.compose.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 版本号比较。
 *
 * ## 为什么这条值得一堆断言
 *
 * 比错的后果**不是崩溃，是静默**：`"0.9" > "0.30"` 的字符串比较会让
 * 「有新版本」永远不出现，用户在界面上看不到任何异常，只是永远不知道有新版。
 * 这类错误只能靠断言挡。
 */
class VersionCompareTest {

    // ------------------------------------------------------------ 基本大小

    @Test
    fun numericSegmentsCompareByValueNotByText() {
        // 这一条就是"为什么不能用字符串比"的现场：文本上 "0.9" > "0.30"，
        // 版本语义上 0.30 > 0.9。
        assertTrue(VersionCompare.isNewer("0.30", "0.9"))
        assertFalse(VersionCompare.isNewer("0.9", "0.30"))
        assertTrue(VersionCompare.isNewer("0.10", "0.9"))
        assertTrue(VersionCompare.isNewer("1.0.0", "0.99.99"))
        assertTrue(VersionCompare.isNewer("2.0", "1.999.999"))
    }

    @Test
    fun segmentCountDoesNotMatterWhenValuesAreEqual() {
        // 0.2 与 0.2.0 是同一个版本：缺的段按 0 补。
        // 不比这一条的话，`v0.2.0` 的 tag 会被当成比 `0.2` 新，于是提示一个假更新。
        assertFalse(VersionCompare.isNewer("0.2.0", "0.2"))
        assertFalse(VersionCompare.isNewer("0.2", "0.2.0"))
        assertFalse(VersionCompare.isNewer("1.2.3.0.0", "1.2.3"))
        assertEquals(0, VersionCompare.compare(parse("0.2"), parse("0.2.0")))
    }

    // ------------------------------------------------------------ 前缀与后缀

    @Test
    fun leadingVIsIgnored() {
        // GitHub tag 常见 `v0.3.0`，而 BuildConfig.VERSION_NAME 是 `0.2`（没有 v）。
        assertTrue(VersionCompare.isNewer("v0.3.0", "0.2"))
        assertFalse(VersionCompare.isNewer("v0.2", "0.2"))
        assertTrue(VersionCompare.isNewer("V1.0", "0.9"))
    }

    @Test
    fun prereleaseIsOlderThanTheSameRelease() {
        // semver：0.3.0-beta < 0.3.0。所以从 0.3.0-beta 升到 0.3.0 是有新版。
        assertTrue(VersionCompare.isNewer("0.3.0", "0.3.0-beta"))
        assertFalse(VersionCompare.isNewer("0.3.0-beta", "0.3.0"))
        // 但 0.3.0-beta 仍然比 0.2 新。
        assertTrue(VersionCompare.isNewer("0.3.0-beta", "0.2"))
        assertTrue(VersionCompare.isNewer("0.3.0-rc.1", "0.3.0-alpha.2"))
        // 段数少的预发布更小：alpha < alpha.1
        assertTrue(VersionCompare.isNewer("1.0.0-alpha.1", "1.0.0-alpha"))
    }

    @Test
    fun buildMetadataIsIgnored() {
        // semver 规定 `+build` 不参与比较。
        assertFalse(VersionCompare.isNewer("0.2+build7", "0.2"))
        assertFalse(VersionCompare.isNewer("0.2", "0.2+99"))
    }

    // ------------------------------------------------------------ 非法输入

    @Test
    fun unparseableInputNeverClaimsANewerVersion() {
        // 关键约定：解析不了时**不提示更新**，而不是拿 0 去比出"永远有新版本"。
        val junk = listOf("", "   ", "v", "abc", "1..2", "1.2x", "1.2.3.4.5.6.7", "- 1.0", "1.-2", ".1")
        for (bad in junk) {
            assertNull("「$bad」不该被解析成版本", VersionCompare.parse(bad))
            assertFalse("「$bad」作为新版本时应返回 false", VersionCompare.isNewer(bad, "0.2"))
            assertFalse("「$bad」作为当前版本时应返回 false", VersionCompare.isNewer("9.9", bad))
        }
        assertNull(VersionCompare.parse(null))
    }

    @Test
    fun whitespaceAroundAVersionIsTolerated() {
        // API 返回的字符串偶尔带空白；那只该被 trim 掉，不该判成非法。
        assertTrue(VersionCompare.isNewer(" 0.3 ", "0.2"))
        assertTrue(VersionCompare.isNewer("\tv0.3\n", "0.2"))
    }

    // ------------------------------------------------------------ 真实场景

    @Test
    fun realTagsFromThisProject() {
        // 当前 versionName 是 0.2；上游 IQ Code 的 0.30.4 **不可比**（那是别人的版本号），
        // 但"用本仓库的 tag 比本仓库的版本"这件事本身要正确。
        assertTrue(VersionCompare.isNewer("0.3", "0.2"))
        assertTrue(VersionCompare.isNewer("1.0", "0.2"))
        assertFalse(VersionCompare.isNewer("0.2", "0.2"))
        assertFalse(VersionCompare.isNewer("0.1.9", "0.2"))
    }

    private fun parse(text: String): VersionCompare.Parsed =
        requireNotNull(VersionCompare.parse(text)) { "测试数据应能解析：$text" }
}
