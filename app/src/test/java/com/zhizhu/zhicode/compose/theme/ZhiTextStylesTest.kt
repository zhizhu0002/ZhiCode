package com.zhizhu.zhicode.compose.theme

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 字阶的守卫：主题样式槽位的接线，以及 Miuix 的两处设计意图没被覆盖掉。
 *
 * 为什么值得留一条：字阶**错了不会编译失败**。body2 接错到正文档、两档调成相等，
 * 界面只会层级不清，code review 最容易滑过去。跑真 JVM，不需要 Robolectric。
 */
class ZhiTextStylesTest {

    private val s = zhiTextStyles()

    /** 期望的接线表。顺序即层级，所以「单调」不用另写一条用例。
     *
     * Miuix **原生字阶**（`miuix-ui/theme/TextStyles.kt` 的 `defaultTextStyles()`）。
     * 这个工程曾经把它整体压过一档（title1 24 / main 14），结果是"官方的组件 +
     * 自己的字"，且四档标题被挤进 9sp 区间里。现在回到规范值 ——
     * 要调观感请去调排版，不要动这张表。
     */
    private val expected = listOf(
        "title1" to 32f, "title2" to 24f, "title3" to 20f, "title4" to 18f,
        "main" to 17f, "paragraph" to 17f, "button" to 17f, "headline1" to 17f,
        "body1" to 16f, "headline2" to 16f,
        "body2" to 14f, "subtitle" to 14f,
        "footnote1" to 13f, "footnote2" to 11f,
    )

    private fun size(name: String): Float = when (name) {
        "title1" -> s.title1.fontSize
        "title2" -> s.title2.fontSize
        "title3" -> s.title3.fontSize
        "title4" -> s.title4.fontSize
        "main" -> s.main.fontSize
        "paragraph" -> s.paragraph.fontSize
        "button" -> s.button.fontSize
        "headline1" -> s.headline1.fontSize
        "body1" -> s.body1.fontSize
        "headline2" -> s.headline2.fontSize
        "body2" -> s.body2.fontSize
        "subtitle" -> s.subtitle.fontSize
        "footnote1" -> s.footnote1.fontSize
        "footnote2" -> s.footnote2.fontSize
        else -> error("未知 token: $name")
    }.let { unit: TextUnit ->
        assertEquals("$name 的字号不能是 Unspecified", true, unit != TextUnit.Unspecified)
        unit.value
    }

    @Test
    fun `每个 Miuix 样式槽位落到预期字号`() {
        for ((name, want) in expected) {
            assertEquals(name, want, size(name), 0.001f)
        }
    }

    /** Miuix 的层级关系不能被这次覆盖拉平或倒过来。 */
    @Test
    fun `标题 大于 正文 大于 脚注`() {
        val sizes = expected.map { size(it.first) }
        for (i in 1 until 4) {
            assertEquals("前四档必须严格递减", true, sizes[i] < sizes[i - 1])
        }
        assertEquals(true, sizes.first() > sizes[4])   // title1 > main
        assertEquals(true, sizes[4] > sizes.last())   // main > footnote2
    }

    /** 只换字号，不动 Miuix 的其它设计意图。 */
    @Test
    fun `段落行高与副标题粗体都保留`() {
        assertEquals("paragraph 的行高必须是相对单位，否则不随字号缩放", 1.2f.em.type, s.paragraph.lineHeight.type)
        assertEquals(FontWeight.Bold, s.subtitle.fontWeight)
    }

    /** Miuix 官方样式槽位的层级由上面的字号断言覆盖。 */

}
