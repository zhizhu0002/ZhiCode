package com.zhizhu.zhicode.compose.model

object TurnLayout {

    /** 分块所需的最小输入。 */
    data class Entry(val id: String, val isUser: Boolean)

    /** 对话流里的一块。 */
    sealed interface Block {
        /** 这块自己的 key（`LazyColumn` 的 item key 用它）。 */
        val key: String

        /** 不与他人成块：目前只有用户发言。 */
        data class Standalone(val id: String) : Block {
            override val key: String get() = id
        }

        data class Turn(
            val id: String,
            val ids: List<String>,
            /** 稳定 key。 */
            val stableKey: String = id,
        ) : Block {
            override val key: String get() = stableKey
        }
    }

    /** 把对话流切成块。 */
    fun blocks(entries: List<Entry>, pinnedKeys: Map<Int, String> = emptyMap()): List<Block> {
        if (entries.isEmpty()) return emptyList()
        val out = ArrayList<Block>(entries.size)
        var turnIds = ArrayList<String>()

        fun sealTurn() {
            if (turnIds.isEmpty()) return
            val index = out.size
            out += Block.Turn(
                id = turnIds.first(),
                ids = turnIds.toList(),
                stableKey = pinnedKeys[index] ?: turnIds.first(),
            )
            turnIds = ArrayList()
        }

        for (entry in entries) {
            if (entry.isUser) {
                sealTurn()
                out += Block.Standalone(entry.id)
            } else {
                turnIds.add(entry.id)
            }
        }
        sealTurn()
        return out
    }

    fun pinForPrepend(previous: List<Block>, next: List<Block>): Map<Int, String> {
        if (previous.isEmpty() || next.isEmpty()) return emptyMap()
        val out = HashMap<Int, String>()
        val shared = minOf(previous.size, next.size)
        for (back in 0 until shared) {
            val newIndex = next.size - 1 - back
            val old = previous[previous.size - 1 - back]
            val new = next[newIndex]
            if (old is Block.Turn && new is Block.Turn) {
                if (old.ids.any { it in new.ids }) out[newIndex] = old.key
            }
        }
        return out
    }

    /** 这一块里有没有这条消息。 */
    fun containsMessage(block: Block, id: String): Boolean = when (block) {
        is Block.Standalone -> block.id == id
        is Block.Turn -> block.ids.contains(id)
    }
}
