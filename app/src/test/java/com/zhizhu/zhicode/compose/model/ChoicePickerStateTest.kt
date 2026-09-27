package com.zhizhu.zhicode.compose.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 长按动作菜单的**渲染判定**守卫。
 *
 * 长按一条消息 / 一条会话时，界面有两条互斥的渲染路径：
 *
 * - [ChoicePickerState.isActionMenu] 为真 → 由被长按的那一项自己渲染
 *   **贴住它的下拉菜单**（`ui/Common.kt` 的 `ZhiAnchoredActionMenu`）；
 * - 为假 → 原来的居中对话框（`ui/dialogs` 的 `ChoicePickerOverlay`）。
 *
 * `AppScaffold` 正是按这一个布尔值决定「对话框让位 / 背景要不要模糊」，
 * 所以它判断错的表现是**功能性**的、而且不会编译失败：
 *
 * - 该为真却为假 → 退回居中对话框（观感退化，但不致命）；
 * - 该为假却为真 → **两边都不画**：菜单认领不到锚点、对话框又被让位了，
 *   于是长按之后屏幕上什么都不出现。这一条最难排查。
 *
 * 这就是必须把 [ChoicePickerState.anchorId] 也纳入判定的原因 ——
 * 光看 intent 是不够的。
 */
class ChoicePickerStateTest {

    private fun picker(intent: ChoiceIntent, anchorId: String?) = ChoicePickerState(
        title = "标题",
        options = listOf(ChoiceOption("复制")),
        intent = intent,
        anchorId = anchorId,
    )

    @Test
    fun `消息与会话的动作菜单在带锚点时按菜单渲染`() {
        assertTrue(
            "长按消息应走贴住该项的下拉菜单",
            picker(ChoiceIntent.MESSAGE_ACTION, "msg-1").isActionMenu,
        )
        assertTrue(
            "长按会话应走贴住该条的下拉菜单",
            picker(ChoiceIntent.SESSION_ACTION, "sess-1").isActionMenu,
        )
    }

    @Test
    fun `缺少锚点时必须退回居中对话框而不是什么都不画`() {
        // 这是唯一的「两边都不画」入口：intent 是动作菜单，但没有锚点，
        // 没有任何一项会认领它。所以它必须判为 false，让对话框接手。
        assertFalse(
            "没有 anchorId 时不能按菜单渲染（否则菜单与对话框都不会出现）",
            picker(ChoiceIntent.MESSAGE_ACTION, null).isActionMenu,
        )
        assertFalse(
            picker(ChoiceIntent.SESSION_ACTION, null).isActionMenu,
        )
    }

    @Test
    fun `其余选择器即使带锚点也仍然走居中对话框`() {
        val others = listOf(
            ChoiceIntent.GENERIC,
            ChoiceIntent.QUESTION,
            ChoiceIntent.PERMISSION_MODE,
            ChoiceIntent.EFFORT,
            ChoiceIntent.PLAN_GOAL,
            ChoiceIntent.SESSION_NOTE,
        )
        for (intent in others) {
            assertFalse(
                "$intent 应当走居中对话框（斜杠命令、分步提问、备注编辑都靠它）",
                picker(intent, "some-id").isActionMenu,
            )
        }
    }

    @Test
    fun `默认构造的锚点为空`() {
        val plain = ChoicePickerState(title = "t", options = emptyList())
        assertEquals(null, plain.anchorId)
        assertFalse(plain.isActionMenu)
    }
}
