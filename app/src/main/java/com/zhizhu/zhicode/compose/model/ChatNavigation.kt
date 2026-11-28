package com.zhizhu.zhicode.compose.model

object ChatNavigation {

    /** 定位所需的最小信息。 */
    data class Entry(val id: String, val isUser: Boolean, val body: String)

    /** 找 [anchorId] **之前**最近的一条用户提问的正文。 */
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
