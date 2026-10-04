package com.zhizhu.zhicode.compose.model

/**
 * 对话流里的**定位**逻辑（纯逻辑，无 Android 依赖）。
 *
 * 目前只有一件事：「从某条消息往前找最近的用户提问」。
 *
 * ## 为什么必须是一个真函数，而不是界面里的一段循环
 *
 * 「重试上一问」原本取的是整条对话**最后一条**用户消息。于是用户翻到很早以前的一轮、
 * 对它点「重试」时，重发的却是最新那个问题 —— 点 A 发了 B，界面上看不出任何异常，
 * 而这是纯逻辑错误，靠肉眼看界面几乎发现不了。
 *
 * 抽成函数后可以被逐条断言（见 `ChatNavigationTest`）。为了让这个文件保持"零依赖"
 * （能进 `test-jvm-fast.sh` 的快回路），这里用最小输入 [Entry] 而不是 `ChatItem`：
 * 后者拖着一整套界面状态类，为一次定位把它们全编一遍不划算。
 *
 * 参考实现：IQ Code `MainActivity` 的 `for (int i = index - 1; i >= 0; i--)`
 * （在 `showMessageActions` 里找被点那条之前的用户消息）。
 */
object ChatNavigation {

    /**
     * 定位所需的最小信息。
     *
     * @param id      消息 id（与界面上那条同一个）
     * @param isUser  是不是用户发言（工具组、助手回复、错误、提示都不算）
     * @param body    正文。**空正文不算可重试的提问** —— 只有图片的消息没有文字可发
     */
    data class Entry(val id: String, val isUser: Boolean, val body: String)

    /**
     * 找 [anchorId] **之前**最近的一条用户提问的正文。
     *
     * @param anchorId 被点击的那条消息。传 null（或找不到）时退回"最后一条用户提问" ——
     *                 菜单正常情况下一定带着来源，这只是兜底，不该静默返回 null
     *                 让用户以为"没有可重试的问题"。
     * @return 找到的正文；没有则 null（调用方据此给出明确提示，而不是假装成功）
     */
    fun previousUserPrompt(entries: List<Entry>, anchorId: String?): String? {
        val anchor = anchorId?.let { id -> entries.indexOfFirst { it.id == id } } ?: -1
        val from = if (anchor >= 0) anchor - 1 else entries.lastIndex
        if (from < 0) return null
        for (i in from downTo 0) {
            val entry = entries[i]
            if (entry.isUser && entry.body.isNotBlank()) return entry.body
        }
        return null
    }
}
