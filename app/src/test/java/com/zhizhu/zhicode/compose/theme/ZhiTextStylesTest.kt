package com.zhizhu.zhicode.compose.theme

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 字阶的守卫：token → 档位 的接线，以及 Miuix 的两处设计意图没被覆盖掉。
 *
 * 为什么值得留一条：字阶**错了不会编译失败**。body2 接错到正文档、两档调成相等，
 * 界面只会层级不清，code review 最容易滑过去。跑真 JVM，不需要 Robolectric。
 */
class ZhiTextStylesTest {

    private val s = zhiTextStyles()

    /** 期望的接线表。顺序即层级，所以「单调」不用另写一条用例。
     *
     * S4b 重构：取值回归 Miuix 官方默认字阶
     * （title1..4 = 32/24/20/18，main/paragraph/button/headline1 = 17，
     *  body1/headline2 = 16，body2/subtitle = 14，footnote1/2 = 13/11）。
     * token → 档位的接线断言不变。
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
    fun `每个 token 落到预期档位`() {
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

    /** 档位表自己的单调性：类初始化时就会抛，这里显式钉一次。 */
    private val scaleOrder = listOf(
        ZhiTextScale.Micro, ZhiTextScale.Footnote, ZhiTextScale.Caption, ZhiTextScale.BodySmall,
        ZhiTextScale.Body, ZhiTextScale.Subheading, ZhiTextScale.Heading,
        ZhiTextScale.TitleSmall, ZhiTextScale.Title,
    )

    @Test
    fun `档位严格递增`() {
        for (i in 1 until scaleOrder.size) {
            assertEquals(
                "第 $i 档必须大于前一档",
                true,
                scaleOrder[i].value > scaleOrder[i - 1].value,
            )
        }
    }
}
