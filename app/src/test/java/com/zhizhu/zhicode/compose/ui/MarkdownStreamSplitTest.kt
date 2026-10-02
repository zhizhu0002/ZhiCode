package com.zhizhu.zhicode.compose.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 流式 Markdown 的切点：[settledPrefixLength]。
 *
 * <p>这个函数是「流式每 32ms 重解析整篇」这条 O(n²) 路径上唯一的正确性风险点：
 * 它算错的表现不是崩溃，而是**视觉上**的 —— 代码块被从中间切开、某个段落少了半截、
 * 或者标题样式在流式期间闪一下。所以它被抽成纯函数钉在这里。
 *
 * <p>下面每条用例都手工推演过切点，注释里写的是**为什么是这个位置**，而不是"应该是"。
 */
class MarkdownStreamSplitTest {

    /** 切点必须落在某个空行之后，且前缀一定以换行结束。 */
    private fun assertCutIsAfterBlankLine(source: String, expected: Int) {
        val cut = settledPrefixLength(source)
        assertEquals("切点不对：source=${source.replace("\n", "\\n")}", expected, cut)
        if (cut > 0) {
            assertEquals("前缀必须以换行结束", '\n', source[cut - 1])
        }
    }

    @Test
    fun `空串没有可切的位置`() {
        assertEquals(0, settledPrefixLength(""))
    }

    @Test
    fun `没有空行时不切`() {
        // 单段回复是常见情况：整段解析比分两半更划算，也不会有「尾部退化成行内」的差异。
        assertEquals(0, settledPrefixLength("一句话还没写完"))
    }

    @Test
    fun `切在唯一空行之后`() {
        // 前(0)言(1)\n(2)\n(3)后(4)言(5) → 空行在 3，切点 = 4
        val source = "前言\n\n后言"
        assertCutIsAfterBlankLine(source, 4)
        assertEquals("前言\n\n", source.substring(0, settledPrefixLength(source)))
        assertEquals("后言", source.substring(settledPrefixLength(source)))
    }

    @Test
    fun `多个空行取最后一个`() {
        // a(0)\n(1)\n(2)b(3)\n(4)\n(5)c(6) → 最后一个空行在 5，切点 = 6
        val source = "a\n\nb\n\nc"
        assertCutIsAfterBlankLine(source, 6)
        assertEquals("a\n\nb\n\n", source.substring(0, 6))
    }

    @Test
    fun `围栏代码块内部的空行不算块边界`() {
        // 若把代码块内的空行当边界，前缀就停在 ``` 后面，代码块会被劈成两半：
        // 前缀里是未闭合围栏（解析器会输出一个"未闭合也算数"的代码块），
        // 尾部又接着下半截 —— 于是同一个代码块在界面上出现两次。
        val source = "前言\n\n```\ncode\n\ncode\n```\n\n尾部"
        assertCutIsAfterBlankLine(source, 24)
        assertEquals("前言\n\n```\ncode\n\ncode\n```\n\n", source.substring(0, 24))
        assertEquals("尾部", source.substring(24))
    }

    @Test
    fun `未闭合围栏之后的空行也不算`() {
        // 流式输出的高频中间态：模型正在写代码块，闭合围栏还没出来。
        // 此时它的空行同样不能当边界，否则前缀会切进代码块内部。
        val source = "前言\n\n```\ncode\n\n仍在代码块里"
        assertCutIsAfterBlankLine(source, 4)
        assertEquals("前言\n\n", source.substring(0, 4))
        assertEquals("```\ncode\n\n仍在代码块里", source.substring(4))
    }

    @Test
    fun `短于开启围栏的围栏行不闭合代码块`() {
        // 开启是 ````（4 个），收尾只写了 ```（3 个）—— 与 parseMarkdown 的判据一致
        // （要求 f2.second >= len），所以这里不算闭合，其后的空行仍不可切。
        val source = "前言\n\n````\ncode\n```\n\n还在块里"
        // 可切的位置只有「前言」后面那个空行
        assertEquals("前言\n\n", source.substring(0, settledPrefixLength(source)))
    }

    @Test
    fun `CRLF 的空行也算块边界`() {
        // a(0)\r(1)\n(2)\r(3)\n(4)b(5) → 空行是 3..4，切点 = 5
        val source = "a\r\n\r\nb"
        assertCutIsAfterBlankLine(source, 5)
        assertEquals("a\r\n\r\n", source.substring(0, 5))
        assertEquals("b", source.substring(5))
    }

    @Test
    fun `只有缩进空白的行也算空行`() {
        // a(0)\n(1) 空(2,3,4)\n(5)b(6) → 去掉行首空白后为空，切点 = 6
        val source = "a\n   \nb"
        assertCutIsAfterBlankLine(source, 6)
        assertEquals("a\n   \n", source.substring(0, 6))
    }

    @Test
    fun `末尾的空行之后没有内容时不切`() {
        // 切了尾部就是空串：白白多一次 substring，而且前缀与整篇等价。
        assertEquals(0, settledPrefixLength("a\n\n"))
        // 尾部只剩刚敲下的空格同样是「没有内容」：尾部渲染出来是个空 Text。
        assertEquals(0, settledPrefixLength("a\n\n   "))
    }

    @Test
    fun `尾部有内容时前面的空行仍然可切`() {
        // "a\n\nb\n\n   " → 最后那个空行之后只有空白，不能切；
        // 但**前面**那个空行之后有真正的 b，切成 "a\n\n" + "b\n\n   " 是对的。
        val source = "a\n\nb\n\n   "
        assertEquals(3, settledPrefixLength(source))
        assertEquals("a\n\n", source.substring(0, 3))
    }

    @Test
    fun `切点随流式追加单调不减且前缀稳定`() {
        // 这是整条优化的**前提**：`remember(settled)` 之所以能长期命中，
        // 就是因为前缀只在跨块时换一次、之后一直不变。
        val full = "第一段内容\n\n第二段内容\n\n第三段内容\n\n```\ncode\n\ncode\n```\n\n末尾"
        var lastCut = -1
        var lastSettled = ""
        for (len in 1..full.length) {
            val cut = settledPrefixLength(full.substring(0, len))
            assertTrue("切点不应回退：len=$len", cut >= lastCut)
            if (cut > 0) {
                val settled = full.substring(0, cut)
                assertTrue(
                    "前缀必须是上一次前缀的延长（否则 remember 会整段失效）",
                    settled.startsWith(lastSettled),
                )
                lastSettled = settled
            }
            lastCut = cut
        }
        // 走完全篇后，尾部应当正好是「末尾」那一段
        assertEquals("末尾", full.substring(lastCut))
    }

    /**
     * 前缀是否以**空行**收尾。
     *
     * 切点唯一有意义的性质就是这个：块级解析在空行处分块，所以只有这样切，
     * 「单独解析前缀」才会与「解析整篇时那一段」得到同样的结果。
     */
    private fun endsAtBlankLine(prefix: String): Boolean {
        if (prefix.isEmpty()) return false
        var end = prefix.length
        if (prefix[end - 1] != '\n') return false
        end--
        if (end > 0 && prefix[end - 1] == '\r') end--
        var s = prefix.lastIndexOf('\n', end - 1) + 1
        while (s < end && (prefix[s] == ' ' || prefix[s] == '\t')) s++
        return s == end
    }

    @Test
    fun `切点必须落在空行之后——不丢字符`() {
        // 解析层那份"不丢字符"的契约（见 Markdown.kt 顶部注释）在切分这一步同样要成立，
        // 否则流式期间会看到文字凭空少一截。
        //
        // ⚠️ 只断言 `substring(0,c)+substring(c)==source` 是**没有牙的**：任何 c 都成立。
        // 所以真正要断言的是「切点两侧的划分是块级的」，即前缀以空行收尾。
        val samples = listOf(
            "",
            "a",
            "a\n\nb",
            "a\n\n\n\nb\n\n",
            "```\n未闭合",
            "前言\n\n```\nkotlin\nval x = 1\n\nval y = 2\n```\n\n结束",
            "| a | b |\n|---|---|\n| 1 | 2 |\n\n后文",
            "> 引用\n>\n> 续行\n\n后文",
            "第一段\n\n## 标题\n\n- 项目一\n- 项目二\n\n结尾",
        )
        for (source in samples) {
            val cut = settledPrefixLength(source)
            assertTrue("切点越界：$source", cut in 0..source.length)
            assertEquals(
                "拼接必须等于原文：${source.replace("\n", "\\n")}",
                source,
                source.substring(0, cut) + source.substring(cut),
            )
            if (cut > 0) {
                assertTrue(
                    "切点之前必须是空行，否则会把某个块劈开：${source.replace("\n", "\\n")}",
                    endsAtBlankLine(source.substring(0, cut)),
                )
            }
        }
    }
}
