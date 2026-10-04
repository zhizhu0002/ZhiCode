package com.zhizhu.zhicode.compose.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 「重试上一问」的定位。
 *
 * 这条逻辑以前是"取整条对话最后一条用户消息"，于是对**旧**回复点重试会重发最新那个
 * 问题 —— 纯逻辑错误，肉眼看界面基本发现不了。这里逐条钉住。
 */
class ChatNavigationTest {

    private fun user(id: String, body: String) = ChatNavigation.Entry(id, isUser = true, body = body)
    private fun assistant(id: String) = ChatNavigation.Entry(id, isUser = false, body = "回复 $id")

    private val transcript = listOf(
        user("u1", "第一个问题"),
        assistant("a1"),
        user("u2", "第二个问题"),
        assistant("a2"),
    )

    @Test
    fun thePromptBeforeTheAnchorIsTheOneThatGetsReplayed() {
        // 对第二条助手回复点重试 → 应该是"第二个问题"，而不是最新的那条。
        assertEquals("第二个问题", ChatNavigation.previousUserPrompt(transcript, "a2"))
    }

    @Test
    fun anOlderAnchorGoesBackToItsOwnPrompt() {
        // 这正是那个 bug 的实测形态：翻回第一轮点重试，必须重发第一个问题。
        assertEquals("第一个问题", ChatNavigation.previousUserPrompt(transcript, "a1"))
    }

    @Test
    fun nonUserEntriesBetweenAreSkipped() {
        val withTools = listOf(
            user("u1", "问题"),
            assistant("a1"),
            ChatNavigation.Entry("g1", isUser = false, body = ""), // 工具组没有正文
            assistant("a2"),
        )
        assertEquals("问题", ChatNavigation.previousUserPrompt(withTools, "a2"))
    }

    @Test
    fun anEmptyBodyIsNotAReplayablePrompt() {
        // 只发了图片、没写字的用户消息：没有文字可重发，要继续往前找。
        val entries = listOf(user("u1", "真正的问题"), user("u2", "   "), assistant("a1"))
        assertEquals("真正的问题", ChatNavigation.previousUserPrompt(entries, "a1"))
    }

    @Test
    fun clickingTheFirstMessageFindsNothingInsteadOfGuessing() {
        assertNull(ChatNavigation.previousUserPrompt(transcript, "u1"))
    }

    @Test
    fun anUnknownAnchorFallsBackToTheLastUserPrompt() {
        // 找不到来源（不该发生）时退回"最后一条用户提问"，而不是静默返回 null。
        assertEquals("第二个问题", ChatNavigation.previousUserPrompt(transcript, "nope"))
    }

    @Test
    fun aNullAnchorFallsBackToTheLastUserPrompt() {
        assertEquals("第二个问题", ChatNavigation.previousUserPrompt(transcript, null))
    }

    @Test
    fun anEmptyTranscriptFindsNothing() {
        assertNull(ChatNavigation.previousUserPrompt(emptyList(), "a1"))
        assertNull(ChatNavigation.previousUserPrompt(emptyList(), null))
    }

    @Test
    fun aTranscriptWithoutAnyUserMessageFindsNothing() {
        val onlyAssistant = listOf(assistant("a1"), assistant("a2"))
        assertNull(ChatNavigation.previousUserPrompt(onlyAssistant, "a2"))
    }
}
