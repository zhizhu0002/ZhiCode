package com.zhizhu.zhicode.compose.ui.panes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 超长输出的行数上限：[limitLines]。
 *
 * <p>它是「展开的工具输出 / diff 最多渲染 300 行」这条规则的唯一执行者，
 * 而它算错的表现**不是崩溃**：是少渲染了几行、或者「还有 N 行」的 N 报错。
 * 那看起来更像工具本身没输出那么多，不像渲染 bug，所以最难被发现 ——
 * 因此整条规则被抽成纯函数钉在这里。
 *
 * <p>**行数的定义**（整套用例的口径）：`split('\n')` 的分段数，也就是
 * 「换行数 + 1」，**保留尾部空串**。这里的 `renderedLines` 直接调 `split` ——
 * 它必须与渲染侧 `DiffLines` 里那句 `shown.split('\n').forEach { Text(...) }` 一致
 * （顺带钉住一个容易记反的事实：Kotlin 的 `split` **不**像 Java 的 `String.split`
 * 那样丢弃尾部空串）。
 *
 * <p>**截断是吃掉分隔符的**：`cut` 落在第 max 行末尾的那个 `\n` 上，
 * 所以断言不能只看「显示了几行」，还要看显示出来的**最后一行文本** ——
 * 半行会把 diff 的 ± 前缀切掉，比少显示几行糟糕得多。
 */
class LimitLinesTest {

    /** 渲染侧会画出来的行数（与 `DiffLines` 里的 `split('\n')` 同一口径）。 */
    private fun renderedLines(text: String): Int = text.split('\n').size

    @Test
    fun `没到上限就原样返回`() {
        val text = "a\nb\nc"
        val r = limitLines(text, 300)
        assertEquals(text, r.text)
        assertEquals(0, r.hidden)
    }

    @Test
    fun `恰好等于上限也原样返回`() {
        // 边界：max=3 时 3 行必须一行都不藏。差一位的话最常见的输出就会平白多出提示。
        val text = "l1\nl2\nl3"
        val r = limitLines(text, 3)
        assertEquals(text, r.text)
        assertEquals(0, r.hidden)
    }

    @Test
    fun `多一行就藏起来一行`() {
        val r = limitLines("l1\nl2\nl3\nl4", 3)
        assertEquals("l1\nl2\nl3", r.text)
        assertEquals("显示的必须是完整的最后一行", "l3", r.text.substringAfterLast('\n'))
        assertEquals(1, r.hidden)
    }

    /**
     * 工具输出几乎总以换行收尾，而按 `split` 的口径那个尾巴**也是一行**（空行）。
     * 所以 3 行正文 + 收尾换行 = 4 行，max=3 时要藏 1 行。
     *
     * <p>这条用例存在的意义是**把口径钉死**：另一种同样说得通的定义（"结尾换行不算行"）
     * 会算出 0，两种都能自圆其说，但只有一种与 `DiffLines` 画出来的行数对得上 ——
     * 不对上就会变成"提示说还有 1 行，点开却什么都没多出来"。
     */
    @Test
    fun `收尾的换行按空行计数`() {
        assertEquals(4, renderedLines("l1\nl2\nl3\n"))
        val r = limitLines("l1\nl2\nl3\n", 3)
        assertEquals("l1\nl2\nl3", r.text)
        assertEquals(1, r.hidden)
    }

    @Test
    fun `隐藏行数等于总数减上限`() {
        val lines = (1..40).joinToString("\n") { "line$it" } + "\n"
        assertEquals(41, renderedLines(lines))
        val r = limitLines(lines, 12)
        assertEquals(41 - 12, r.hidden)
        assertEquals(12, renderedLines(r.text))
        assertEquals("line12", r.text.substringAfterLast('\n'))
    }

    @Test
    fun `单行无换行不截断`() {
        for (max in listOf(1, 2, 300)) {
            val r = limitLines("只有一行而且很长", max)
            assertEquals("max=$max", "只有一行而且很长", r.text)
            assertEquals("max=$max", 0, r.hidden)
        }
    }

    @Test
    fun `空串原样返回`() {
        val r = limitLines("", 300)
        assertEquals("", r.text)
        assertEquals(0, r.hidden)
    }

    @Test
    fun `全是换行的输出按行数截断`() {
        // 退化输入：渲染出来是 4 行空白。要求是不崩、且口径仍然一致。
        val r = limitLines("\n\n\n", 1)
        assertEquals("", r.text)
        assertEquals(3, r.hidden)
        assertEquals(4, renderedLines("\n\n\n"))
        assertEquals(4, renderedLines(r.text) + r.hidden)
    }

    @Test
    fun `上限为零时什么都不显示但总数要报对`() {
        // `max <= 0` 定义为「一行都不留」。
        val r = limitLines("a\nb\nc\nd\n", 0)
        assertEquals("", r.text)
        assertEquals(5, r.hidden)
    }

    @Test
    fun `上限为负与为零等价`() {
        assertEquals(limitLines("a\nb\nc", 0).hidden, limitLines("a\nb\nc", -5).hidden)
        assertEquals("", limitLines("a\nb\nc", -5).text)
    }

    @Test
    fun `截断处一定是整行边界`() {
        val text = (1..50).joinToString("\n") { "line$it" }
        for (max in 1..49) {
            val r = limitLines(text, max)
            assertEquals("max=$max", "line$max", r.text.substringAfterLast('\n'))
            assertEquals("max=$max", max, renderedLines(r.text))
            assertEquals("max=$max", 50 - max, r.hidden)
        }
    }

    @Test
    fun `显示行数加隐藏行数恒等于总行数`() {
        // 守恒：不管从哪一条上限切，画出来的 + 藏起来的都必须正好是原文的行数。
        val text = (1..37).joinToString("\n") { "line$it" } + "\n"
        val total = renderedLines(text)
        assertEquals(38, total)
        for (max in 1..37) {
            val r = limitLines(text, max)
            assertTrue(
                "max=$max: ${renderedLines(r.text)} + ${r.hidden} != $total",
                renderedLines(r.text) + r.hidden == total,
            )
        }
    }
}
