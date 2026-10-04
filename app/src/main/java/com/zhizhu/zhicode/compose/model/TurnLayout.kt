package com.zhizhu.zhicode.compose.model

/**
 * 对话流的分块：**一轮助手回合 = 一块**（纯逻辑，无 Android 依赖）。
 *
 * ## 为什么要分块
 *
 * 反编译版 IQ Code（`~/.iqcode/src/sources/com/iqge/MainActivity.java`）里，
 * `addChatView` 的分支是：
 *
 * ```java
 * if (chatItem.type == 1 /* USER */) {
 *     currentConversationHost = null;        // 断开上一轮
 *     destination = chatMessages;            // 自己回到列表根层
 * } else {
 *     destination = currentConversationHost != null ? currentConversationHost : beginConversation();
 * }
 * ```
 *
 * 也就是说：**用户发言永远在容器外，而助手正文 / 工具 / 错误 / 提示全都落进
 * 同一个"本轮"容器**（`beginConversation()` 建的是一个 padding 为 0、
 * 没有任何背景的透明 `vbox`，只用 `margins(0,10,0,14)` 与上下拉开距离）。
 *
 * 这一层把这个分法从视图代码里抽出来，于是它可以被逐条断言 —— "哪一条属于哪一轮"
 * 没有任何一处会编译失败，切错了表现只是"间距不对"，肉眼很难发现。
 *
 * ## 为什么是 sealed 而不是"给每条打一个 turnId"
 *
 * 界面要按块渲染（一块 = 一个 `LazyColumn` item = 一个容器），所以它需要的是
 * **分区结果**而不是"每条归属于谁"。做成 `Standalone` / `Turn` 两种形态之后，
 * "容器只包住 Turn"这件事在类型上就是显然的。
 */
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

        /**
         * 一轮助手回合：助手正文 + 它的工具 + 期间出现的错误/提示。
         *
         * [key] 取**首成员**的 id：内容只会往这一轮末尾追加，首成员不会变，
         * 于是 `LazyColumn` 不会因为追加工具而把整块重建。
         */
        data class Turn(val id: String, val ids: List<String>) : Block {
            override val key: String get() = id
        }
    }

    /**
     * 把对话流切成块。
     *
     * 规则（照抄反编译版）：
     * - `USER` → 自己一块（`Standalone`），**同时断开当前回合**；
     * - 其余（ASSISTANT / TOOL_GROUP / ERROR / INFO）→ 并入当前回合，没有就现开一个。
     *
     * 空输入返回空列表。
     */
    fun blocks(entries: List<Entry>): List<Block> {
        if (entries.isEmpty()) return emptyList()
        val out = ArrayList<Block>(entries.size)
        var turnIds = ArrayList<String>()

        fun sealTurn() {
            if (turnIds.isEmpty()) return
            out += Block.Turn(id = turnIds.first(), ids = turnIds.toList())
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
}
