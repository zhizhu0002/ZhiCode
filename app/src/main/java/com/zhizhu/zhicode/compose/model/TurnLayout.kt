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
         * ## key 取**首成员**的 id，以及为什么这在"追加"下是对的
         *
         * 内容只会往这一轮末尾追加（流式输出、工具陆续返回），首成员不会变，
         * 于是 `LazyColumn` 不会因为追加而把整块重建。
         *
         * ## ⚠️ 但它在"前插"下会变，而那就是上滑置顶的根因
         *
         * 往上加载更早的历史时，新补进来的消息会落在**列表最前面**。
         * 如果补进来的第一条是助手/工具类消息，它会并入第一个 `Turn` 并
         * 成为新的首成员 —— 于是这一块的 `key` 从旧 id 变成新 id。
         *
         * 对 `LazyColumn` 来说，`key` 变 = 这是一块**全新的 item**，
         * 之前记住的锚点全部作废，滚动位置被夹到某项上。
         * 用户看到的就是「每次上滑加载都会置顶」。
         *
         * 修法见 [blocks] 的 `stableIdFirst` 参数：前插发生时，把这一块的
         * key **钉在原来那个 id 上**，让它对 `LazyColumn` 仍是同一块。
         * 这样"追加稳定"与"前插稳定"两个要求都能满足。
         */
        data class Turn(
            val id: String,
            val ids: List<String>,
            /**
             * 稳定 key。默认等于 [id]（首成员），前插场景下由调用方钉成旧值。
             *
             * 单独一个字段而不是复用 [id]：`id` 也要参与"这一轮是谁"的语义
             * （菜单、跳转都按它算），不该被滚动锚定的需要污染。
             */
            val stableKey: String = id,
        ) : Block {
            override val key: String get() = stableKey
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
     *
     * @param pinnedKeys 上一帧各块的 key（下标 → key）。**只为前插服务**：
     *   往前补了一页历史之后，第一个 `Turn` 的首成员会变，`key` 跟着变，
     *   于是 `LazyColumn` 认为那是一条全新的 item、滚动锚点作废 ——
     *   表现就是「每次上滑加载都会置顶」。
     *   把上一帧**还认得出来**的 key 钉回去，那一块对列表而言仍是同一块。
     *
     *   判定"还认得出"：旧块的 `ids` 里至少有一个 id 出现在新块里。
     *   只看首成员不够 —— 首成员正是变掉的那一个。
     */
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

    /**
     * 为新一份块表算出"该把哪些 key 钉住"。
     *
     * 逐块对齐：新旧两边**同一位置**的两块，如果某一方是 `Turn` 且
     * 成员有交集，就把旧的 key 钉到新的那一块上。
     *
     * ## 为什么按位置对齐已经够了
     *
     * 前插只影响**最前面那一块**（新内容从头部进来）；尾部淘汰只影响**最后面**。
     * 中间各块的位置与身份都不变，按位置对齐不会错配。
     *
     * 位置对齐在"中间插入了整块"时会错位，但那种情况在这个界面上不存在：
     * 历史只会从头部补、只往尾部删。
     */
    fun pinForPrepend(previous: List<Block>, next: List<Block>): Map<Int, String> {
        if (previous.isEmpty() || next.isEmpty()) return emptyMap()
        val out = HashMap<Int, String>()
        val shared = minOf(previous.size, next.size)
        for (i in 0 until shared) {
            val old = previous[i]
            val new = next[i]
            if (old is Block.Turn && new is Block.Turn) {
                // 成员有交集才钉：完全没交集说明这一块换人了，钉过去反而错。
                if (old.ids.any { it in new.ids }) out[i] = old.key
            }
        }
        return out
    }
}
