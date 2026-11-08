package com.zhizhu.zhicode.compose.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Release `body` 的分流解析。
 *
 * ## 为什么值得断言
 *
 * 这一段的输出直接决定更新页"横线下面"的样子。分流错了（比如把 `-` 列表项
 * 当成正文、或者把长句里的冒号当标题分隔符）不会报错，只会让那一屏看起来很怪，
 * 而线上没有 Release 时又完全看不到 —— 只能在这里挡。
 */
class ReleaseNotesTest {

    @Test
    fun headingsBecomeSections() {
        val sections = ReleaseNotes.parse(
            """
            # Beta 版说明
            本版功能和体验可能与正式版存在差异。

            ## 基础体验
            降负载、控内存。
            """.trimIndent(),
        )
        assertEquals(listOf("Beta 版说明", "基础体验"), sections.map { it.title })
        assertEquals("本版功能和体验可能与正式版存在差异。", sections[0].items.single().detail)
        assertEquals("降负载、控内存。", sections[1].items.single().detail)
    }

    @Test
    fun bulletsBecomeItems() {
        val sections = ReleaseNotes.parse(
            """
            ## 新增
            - 会话可多选导出
            * 支持 LaTeX 公式
            + 标签栏可移到底部
            """.trimIndent(),
        )
        val items = sections.single().items
        assertEquals(3, items.size)
        assertEquals("会话可多选导出", items[0].detail)
        assertEquals("支持 LaTeX 公式", items[1].detail)
        assertEquals("标签栏可移到底部", items[2].detail)
    }

    @Test
    fun boldLeadBecomesItemTitle() {
        val items = ReleaseNotes.parse("- **新增主题预设色**：深浅之外的调色板。").single().items
        assertEquals(1, items.size)
        assertEquals("新增主题预设色", items[0].title)
        assertEquals("深浅之外的调色板。", items[0].detail)
    }

    @Test
    fun shortColonPrefixBecomesItemTitle() {
        val items = ReleaseNotes.parse("- 沙箱：可直接运行未安装的 APK。").single().items
        assertEquals("沙箱", items[0].title)
        assertEquals("可直接运行未安装的 APK。", items[0].detail)
    }

    @Test
    fun longSentenceIsNotSplitAtItsColon() {
        // 长句里的冒号是标点，不是"标题到此结束"。拆错了会把一句话劈成两行。
        val line = "- 这一条说明很长很长很长很长很长很长很长很长很长很长很长" +
            "很长很长很长很长很长很长很长很长很长：它不该被拆开"
        val items = ReleaseNotes.parse(line).single().items
        assertEquals(1, items.size)
        assertTrue("不该被拆出标题，实际是「${items[0].title}」", items[0].title.isEmpty())
        assertTrue(items[0].detail.startsWith("这一条说明很长"))
    }

    @Test
    fun continuationLinesMergeIntoTheSameItem() {
        val items = ReleaseNotes.parse(
            """
            - 第一段
              折行的后半句
            """.trimIndent(),
        ).single().items
        assertEquals(1, items.size)
        assertEquals("第一段\n折行的后半句", items[0].detail)
    }

    @Test
    fun textWithoutHeadingGoesToTheDefaultSection() {
        val sections = ReleaseNotes.parse("就是一句话，没有标题。")
        assertEquals(1, sections.size)
        assertEquals("更新内容", sections[0].title)
        assertEquals("就是一句话，没有标题。", sections[0].items.single().detail)
    }

    @Test
    fun emptyBodyProducesNoSectionsAtAll() {
        // 空 body 与"一个空小节"是两件事：页面据此显示"没有写说明"，
        // 而不是画一个没有内容的粗体标题。
        assertTrue(ReleaseNotes.parse(null).isEmpty())
        assertTrue(ReleaseNotes.parse("").isEmpty())
        assertTrue(ReleaseNotes.parse("   \n\n  \t ").isEmpty())
    }

    @Test
    fun horizontalRulesAreNotContent() {
        val sections = ReleaseNotes.parse(
            """
            ## 修复
            ---
            - 修了一处崩溃
            """.trimIndent(),
        )
        val items = sections.single().items
        assertEquals(1, items.size)
        assertEquals("修了一处崩溃", items[0].detail)
    }

    @Test
    fun deepHeadingsStayAsContent() {
        // `####` 在手机上已经是正文的视觉重量，不该当小节标题。
        val sections = ReleaseNotes.parse(
            """
            ## 小节
            #### 这不该开一个新小节
            """.trimIndent(),
        )
        // 只有 `##` 那一个开了小节；`####` 那行进成了正文。
        assertEquals(listOf("小节"), sections.map { it.title })
        assertEquals(1, sections[0].items.size)
        assertTrue(
            "`####` 那行应原样留在正文里，实际「${sections[0].items[0].detail}」",
            sections[0].items[0].detail.contains("这不该开一个新小节"),
        )
    }

    @Test
    fun crashSafetyOnPathologicalInput() {
        // 超长 body 不该让界面铺出几千行。
        val huge = buildString {
            repeat(5000) { append("- 第").append(it).append("条\n") }
        }
        val sections = ReleaseNotes.parse(huge)
        val total = sections.sumOf { it.items.size }
        assertTrue("条目数必须有上限，实际 $total", total <= 60)
    }
}
