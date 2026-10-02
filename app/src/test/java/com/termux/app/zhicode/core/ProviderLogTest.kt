package com.termux.app.zhicode.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ProviderLog] 的纯逻辑测试。
 *
 * 这些用例存在的理由很具体：日志的轮转与截断**写错了不会报任何错** ——
 * 要么文件无限长，要么把最关键的那一段截掉，要么按字节切把汉字劈成乱码。
 * 而它偏偏是"真机上看不到失败原因"时唯一的证据来源，所以它自己得先可信。
 */
class ProviderLogTest {

    @Test
    fun nullAndEmptyArgumentsAreDistinguishable() {
        // 这两种在"新建失败"里含义完全不同：没传名字 vs 传了空名字。
        // 日志如果都渲染成空，最该分辨的那件事就分辨不出来了。
        assertEquals("null", ProviderLog.arg(null))
        assertEquals("\"\"", ProviderLog.arg(""))
    }

    @Test
    fun argumentsAreEscapedSoOneEntryStaysOneLine() {
        assertEquals("\"a\\nb\"", ProviderLog.arg("a\nb"))
        assertEquals("\"he said \\\"hi\\\"\"", ProviderLog.arg("he said \"hi\""))
    }

    @Test
    fun longArgumentsAreTruncated() {
        val kept = ProviderLog.arg("x".repeat(1000))
        assertTrue("截断后仍然太长：${kept.length}", kept.length < 200)
        assertTrue(kept.endsWith("…\""))
    }

    @Test
    fun formatHasTimeMethodArgsAndOutcome() {
        val line = ProviderLog.format(0L, "createDocument", listOf("\"home\"", "\"dir\""), "ok")
        assertTrue(
            line,
            line.matches(
                Regex(
                    "^\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d\\.\\d\\d\\d " +
                        "createDocument\\(\"home\", \"dir\"\\) -> ok$",
                ),
            ),
        )
    }

    @Test
    fun trimsOnlyAboveTheLimit() {
        assertFalse(ProviderLog.shouldTrim(ProviderLog.MAX_BYTES))
        assertTrue(ProviderLog.shouldTrim(ProviderLog.MAX_BYTES + 1))
    }

    @Test
    fun trimTailKeepsTheEndAndStartsAtALineBoundary() {
        // 行 1..9 各 5 字节（行=3 + 数字=1 + 换行），行 10..50 各 6 字节 —— 共 291 字节。
        // 保留 40 字节时，起点落在第 44 行中间，所以必须先跳到第 45 行的行首。
        val text = (1..50).joinToString("") { "行$it\n" }
        val kept = ProviderLog.trimTail(text, 40)
        assertTrue(kept.length < text.length)
        assertTrue("必须从完整的一行开始：$kept", kept.startsWith("行45\n"))
        assertTrue("尾部必须保住：$kept", kept.endsWith("行50\n"))
        for (line in kept.split("\n")) {
            if (line.isNotEmpty()) {
                assertTrue("出现了半行：$line", line.matches(Regex("^行\\d{2}$")))
            }
        }
    }

    @Test
    fun trimTailNeverSplitsACharacter() {
        // 每行 28 字节（9 个汉字 27 字节 + 换行）。按字节切一定会落在字符中间 ——
        // 而日志里最要紧的恰恰可能是中文报错。
        val text = "错误信息：无法创建\n".repeat(50)
        val kept = ProviderLog.trimTail(text, 37)
        assertFalse("按字节切把汉字劈开了：$kept", kept.contains('\uFFFD'))
        for (line in kept.split("\n")) {
            if (line.isNotEmpty()) assertEquals("错误信息：无法创建", line)
        }
    }

    @Test
    fun trimTailGivesUpWhenThereIsNoLineBoundary() {
        // 找不到行首就宁可不留：半行日志比没有日志更误导。
        assertEquals("", ProviderLog.trimTail("x".repeat(1000), 10))
    }

    @Test
    fun tailReturnsTheLastNonBlankLines() {
        val text = (1..100).joinToString("\n") { "行$it" } + "\n\n"
        assertEquals(listOf("行98", "行99", "行100"), ProviderLog.tail(text, 3))
    }
}
