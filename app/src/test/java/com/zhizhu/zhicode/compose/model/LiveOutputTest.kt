package com.zhizhu.zhicode.compose.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 工具实时输出的累积与裁剪。
 *
 * 这一层以前是 ViewModel 里几行内联代码，于是有两个只有真机上才看得出来的毛病：
 *   1. 每个 stderr chunk 都插一行 `[stderr]`，连续报错的命令把输出刷成一片标记；
 *   2. 换行没有归一化，PTY 的 `\r\n` 在界面上留下看不见的回车。
 * 另外"运行中那一行该显示多少行输出"完全没有规则 —— 它是纯字符串运算，
 * 所以放在纯逻辑层逐条钉住，而不是靠肉眼看界面。
 */
class LiveOutputTest {

    private fun feed(vararg chunks: Pair<String, Boolean>, keep: Int = LiveOutput.DEFAULT_KEEP) =
        chunks.fold(LiveOutput.Buffer()) { acc, (text, stderr) ->
            LiveOutput.append(acc, text, stderr, keep)
        }

    // ---------------------------------------------------------------- 累积

    @Test
    fun crlfAndLoneCrAreNormalisedToLf() {
        val buffer = feed("a\r\nb\rc" to false)
        assertEquals("a\nb\nc", buffer.text)
        // 计数按**归一化之后**算：原版也是先 clean 再累加长度。
        assertEquals(5, buffer.stdoutChars)
    }

    @Test
    fun stdoutAndStderrAreCountedSeparately() {
        val buffer = feed("hello" to false, "boom" to true)
        assertEquals(5, buffer.stdoutChars)
        assertEquals(4, buffer.stderrChars)
    }

    /**
     * 标记**只在流切换时插一次**。
     *
     * 这是那个"连续 stderr 会刷屏"的回归点：三块 stderr 只该有一行标记，
     * 而不是三行（原版 `if (liveOutput.length() > 0 && liveLastWasStderr != stderr)`）。
     */
    @Test
    fun theStreamMarkerIsInsertedOnlyWhenTheStreamSwitches() {
        val buffer = feed("one" to false, "e1" to true, "e2" to true, "e3" to true, "two" to false)
        assertEquals("one\n[stderr]\ne1e2e3\n[stdout]\ntwo", buffer.text)
        assertEquals(1, countOf(buffer.text, "[stderr]"))
        assertEquals(1, countOf(buffer.text, "[stdout]"))
    }

    @Test
    fun aStderrFirstChunkGetsAMarkerAndAStdoutFirstChunkDoesNot() {
        assertEquals("[stderr]\noops", feed("oops" to true).text)
        // 第一块就是 stdout 时不插标记：没有"切换"这回事，插了只是噪音。
        assertEquals("fine", feed("fine" to false).text)
    }

    @Test
    fun theMarkerStartsOnItsOwnLineEvenWithoutATrailingNewline() {
        // 上一块没有换行结尾时要先补一个，否则标记会接在正文屁股后面。
        assertEquals("abc\n[stderr]\nx", feed("abc" to false, "x" to true).text)
    }

    @Test
    fun emptyChunksAreIgnored() {
        val buffer = feed("a" to false, "" to true)
        assertEquals("a", buffer.text)
        // 空块不改任何东西：不插标记、不计数、不翻转 lastWasStderr。
        assertEquals(false, buffer.lastWasStderr)
        assertEquals(0, buffer.stderrChars)
    }

    @Test
    fun theBufferKeepsOnlyTheTail() {
        val buffer = feed("0123456789" to false, keep = 4)
        assertEquals("6789", buffer.text)
        // 计数是**累计**的，不跟着被丢掉的部分缩水 —— 它代表"一共产出了多少"。
        assertEquals(10, buffer.stdoutChars)
    }

    // ---------------------------------------------------------------- 裁剪

    @Test
    fun aShortOutputIsReturnedUnchanged() {
        assertEquals("ok", LiveOutput.tail("ok", maxChars = 100))
        assertFalse(LiveOutput.tail("ok", maxChars = 100).contains(LiveOutput.OMITTED_TAIL))
    }

    @Test
    fun aTrimmedTailSaysThatEarlierOutputWasOmitted() {
        val text = "a".repeat(5000)
        val out = LiveOutput.tail(text, maxChars = 100)
        assertTrue(out.startsWith(LiveOutput.OMITTED_TAIL + "\n"))
        // 100 字符的请求会被下限抬到 1000：少于一行宽度的尾巴没有信息量。
        assertEquals(LiveOutput.OMITTED_TAIL.length + 1 + 1000, out.length)
    }

    @Test
    fun previewDropsTrailingBlankLinesAndSaysSoWhenItCutEarlierLines() {
        val text = "one\ntwo\nthree\nfour\n"
        // 末尾那个空行不算行（否则以换行结尾的输出会白占一行）。
        // 行确实被裁了（4 行只要 2 行）→ 带上"上面还有更多行"的提示（原版行为）。
        assertEquals(
            LiveOutput.OMITTED_LIVE + "\nthree\nfour",
            LiveOutput.preview(text, maxChars = 1000, maxLines = 2),
        )
    }

    @Test
    fun previewWithoutCuttingLinesCarriesNoNotice() {
        assertEquals("one\ntwo", LiveOutput.preview("one\ntwo\n", maxChars = 1000, maxLines = 7))
    }

    @Test
    fun previewUsesItsOwnOmissionNoticeWhenItCutWholeLines() {
        val out = LiveOutput.preview("1\n2\n3\n4\n5", maxChars = 1000, maxLines = 2)
        assertTrue(out.startsWith(LiveOutput.OMITTED_LIVE))
        // 两条提示文案**不同**：用户要能分清"这段输出本身被截断过"与"上面还有更多行"。
        assertFalse(LiveOutput.OMITTED_LIVE == LiveOutput.OMITTED_TAIL)
    }

    @Test
    fun previewAsksForAtLeastOneLine() {
        // maxLines 传 0（或负数）时不能返回空 —— 那会让"运行中"那一块整片消失。
        assertEquals("only", LiveOutput.preview("only", maxChars = 1000, maxLines = 0))
    }

    @Test
    fun previewOfAnEmptyBufferIsEmptySoTheUiCanShowItsPlaceholder() {
        // 空 → 空：界面据此显示"等待程序输出…"，而不是一个空白的面板。
        assertEquals("", LiveOutput.preview("", maxChars = 7000, maxLines = 7))
    }

    // ---------------------------------------------------------------- 体量

    @Test
    fun volumeUsesDecimalUnitsLikeTheReference() {
        assertEquals("标准输出 0 B · 错误输出 0 B", LiveOutput.volume(0, 0))
        assertEquals("标准输出 999 B · 错误输出 1.0 KB", LiveOutput.volume(999, 1000))
        assertEquals("标准输出 1.5 MB · 错误输出 0 B", LiveOutput.volume(1_500_000, 0))
    }

    private fun countOf(text: String, needle: String): Int {
        var count = 0
        var from = 0
        while (true) {
            val at = text.indexOf(needle, from)
            if (at < 0) return count
            count++
            from = at + needle.length
        }
    }
}
