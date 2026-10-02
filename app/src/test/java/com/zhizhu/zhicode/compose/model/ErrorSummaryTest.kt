package com.zhizhu.zhicode.compose.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * "哪一行才是有用的错误信息"。
 *
 * 这个判据以前在界面层是"第一条非空行"，于是折叠摘要经常是
 * `退出码 100 · Reading package lists...` —— 真正的错因在下一行。
 * 现在它是纯逻辑，可以逐条钉住。
 */
class ErrorSummaryTest {

    @Test
    fun theKeywordLineWinsOverTheFirstLine() {
        // apt 的典型长相：第一行只是进展，错因在第二行。
        val output = "Reading package lists...\nE: Unable to locate package foo\n"
        assertEquals("E: Unable to locate package foo", ErrorSummary.firstUsefulLine(output))
    }

    @Test
    fun allTheReferenceKeywordsAreRecognised() {
        val cases = listOf(
            "error: unknown flag",
            "Build failed",
            "open: permission denied",
            "unable to resolve host",
            "E: broken packages",
            "file not found",
        )
        for (line in cases) {
            assertEquals(line, ErrorSummary.firstUsefulLine("noise line\n$line\nmore noise"))
        }
    }

    @Test
    fun theComparisonIsCaseInsensitive() {
        assertEquals("Permission Denied", ErrorSummary.firstUsefulLine("x\nPermission Denied"))
    }

    @Test
    fun withoutAnyKeywordTheFirstUsableLineIsUsedAsFallback() {
        val output = "step 1\nstep 2\nstep 3"
        assertEquals("step 1", ErrorSummary.firstUsefulLine(output))
    }

    @Test
    fun theStreamMarkerIsNotContentAndNeverBecomesTheSummary() {
        // `[stderr]` 是我们插的流标记（见 LiveOutput），当摘要显示毫无信息量。
        val output = "[stderr]\nsomething happened"
        assertEquals("something happened", ErrorSummary.firstUsefulLine(output))
    }

    @Test
    fun keywordOnlyInsideTheMarkerLineDoesNotCount() {
        // 标记行里有 "stderr"，而关键词表里没有它 —— 这里确认判据没有被标记行污染。
        assertEquals("plain text", ErrorSummary.firstUsefulLine("[stderr]\nplain text"))
    }

    @Test
    fun blankLinesAreSkipped() {
        assertEquals("real content", ErrorSummary.firstUsefulLine("\n\n   \nreal content\n\n"))
    }

    @Test
    fun emptyInputGivesAnEmptyStringSoTheCallerCanOmitIt() {
        assertEquals("", ErrorSummary.firstUsefulLine(""))
        assertEquals("", ErrorSummary.firstUsefulLine("\n   \n"))
    }

    @Test
    fun surroundingWhitespaceOfTheChosenLineIsTrimmed() {
        assertEquals("hash mismatch", ErrorSummary.firstUsefulLine("   hash mismatch   \n"))
    }
}
