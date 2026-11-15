package com.zhizhu.zhicode.compose.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 对话流怎么切成"助手回合"。
 *
 * 判据来自反编译版 IQ Code：**用户发言断开容器，其余全部落进同一个回合容器**。
 * 切错了不会编译失败，只会让间距/包裹关系不对，所以逐条钉住。
 */
class TurnLayoutTest {

    private fun user(id: String) = TurnLayout.Entry(id, isUser = true)
    private fun other(id: String) = TurnLayout.Entry(id, isUser = false)

    @Test
    fun aUserMessageBreaksTheTurn() {
        val blocks = TurnLayout.blocks(listOf(other("a1"), user("u1"), other("a2")))
        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is TurnLayout.Block.Turn)
        assertTrue(blocks[1] is TurnLayout.Block.Standalone)
        assertTrue(blocks[2] is TurnLayout.Block.Turn)
    }

    @Test
    fun assistantToolsAndErrorsShareOneTurn() {
        val blocks = TurnLayout.blocks(listOf(other("a1"), other("g1"), other("e1"), other("i1")))
        assertEquals(1, blocks.size)
        val turn = blocks[0] as TurnLayout.Block.Turn
        assertEquals(listOf("a1", "g1", "e1", "i1"), turn.ids)
    }

    @Test
    fun theTurnKeyIsItsFirstMemberSoAppendingDoesNotRebuildIt() {
        // 工具是边跑边追加的：key 必须保持稳定，否则整块会被重建（展开态、滚动位置都丢）。
        val one = TurnLayout.blocks(listOf(other("a1"))) as List<TurnLayout.Block.Turn>
        val two = TurnLayout.blocks(listOf(other("a1"), other("g1"))) as List<TurnLayout.Block.Turn>
        assertEquals("a1", one[0].key)
        assertEquals(one[0].key, two[0].key)
    }

    @Test
    fun consecutiveUserMessagesAreEachTheirOwnBlock() {
        val blocks = TurnLayout.blocks(listOf(user("u1"), user("u2")))
        assertEquals(2, blocks.size)
        assertEquals("u1", blocks[0].key)
        assertEquals("u2", blocks[1].key)
    }

    @Test
    fun aTurnNeverContainsAUserMessage() {
        // 这一条是"容器只包助手侧"的正面表述。
        val blocks = TurnLayout.blocks(listOf(user("u1"), other("a1"), user("u2"), other("a2")))
        for (block in blocks) {
            if (block is TurnLayout.Block.Turn) {
                assertTrue("回合里不该出现用户消息：" + block.ids, block.ids.none { it.startsWith("u") })
            }
        }
    }

    @Test
    fun aLeadingTurnWithoutAUserMessageIsStillItsOwnBlock() {
        // 恢复历史时可能从助手正文开头（前面的用户消息被裁掉了）。
        val blocks = TurnLayout.blocks(listOf(other("a1"), other("a2")))
        assertEquals(1, blocks.size)
        assertEquals("a1", blocks[0].key)
    }

    @Test
    fun userOnlyTranscriptProducesStandalonesOnly() {
        val blocks = TurnLayout.blocks(listOf(user("u1"), user("u2")))
        assertTrue(blocks.all { it is TurnLayout.Block.Standalone })
    }

    @Test
    fun anEmptyTranscriptProducesNoBlocks() {
        assertTrue(TurnLayout.blocks(emptyList()).isEmpty())
    }

    @Test
    fun everyEntryLandsInExactlyOneBlockInOrder() {
        val entries = listOf(user("u1"), other("a1"), other("g1"), user("u2"), other("a2"))
        val ids = TurnLayout.blocks(entries).flatMap { block ->
            when (block) {
                is TurnLayout.Block.Standalone -> listOf(block.id)
                is TurnLayout.Block.Turn -> block.ids
            }
        }
        // 顺序不变、且一条不漏一条不重 —— "某条消息凭空消失"这类问题会在这里被抓住。
        assertEquals(entries.map { it.id }, ids)
    }

    // ---------------------------------------------------------- 前插时的 key 稳定性

    @Test
    fun prependChangesTheTurnKeyWhichIsExactlyWhyPinningIsNeeded() {
        // 这条**故意**把问题钉出来：往前补一页之后，第一轮的首成员变成了更早的
        // a0，于是 key 从 a1 变成 a0。对 LazyColumn 来说那是**另一条 item**，
        // 滚动锚点作废 —— 用户看到的就是「每次上滑加载都会置顶」。
        val before = TurnLayout.blocks(listOf(other("a1"), user("u1"), other("a2")))
        val after = TurnLayout.blocks(listOf(other("a0"), other("a1"), user("u1"), other("a2")))
        assertNotEquals(before[0].key, after[0].key)
        assertEquals("a1", before[0].key)
        assertEquals("a0", after[0].key)
    }

    @Test
    fun pinForPrependKeepsTheFirstTurnKeyStable() {
        // 修法：把上一帧的 key 钉回去，让那一块对列表而言仍是同一块。
        val previous = TurnLayout.blocks(listOf(other("a1"), user("u1"), other("a2")))
        val next = TurnLayout.blocks(listOf(other("a0"), other("a1"), user("u1"), other("a2")))
        val pinned = TurnLayout.pinForPrepend(previous, next)
        val pinnedBlocks = TurnLayout.blocks(
            listOf(other("a0"), other("a1"), user("u1"), other("a2")),
            pinned,
        )
        // 首块仍是原来那个 key —— 前插不再让锚点失效。
        assertEquals("a1", pinnedBlocks[0].key)
        // 但 id 仍是真实的首成员：菜单/跳转按 id 算，不该被锚定需要污染。
        assertEquals("a0", (pinnedBlocks[0] as TurnLayout.Block.Turn).id)
    }

    @Test
    fun pinForPrependDoesNotPinBlocksThatShareNoMembers() {
        // 完全没有交集 = 这一块换人了，钉过去反而会把旧 key 安到无关内容上。
        val previous = TurnLayout.blocks(listOf(other("a1")))
        val next = TurnLayout.blocks(listOf(other("z9")))
        assertTrue(TurnLayout.pinForPrepend(previous, next).isEmpty())
    }

    @Test
    fun pinForPrependLeavesStandaloneBlocksAlone() {
        // 用户发言自成一块、id 就是消息 id，前插不会让它变，所以不该被钉。
        val previous = TurnLayout.blocks(listOf(user("u1")))
        val next = TurnLayout.blocks(listOf(user("u1")))
        assertTrue(TurnLayout.pinForPrepend(previous, next).isEmpty())
    }

    @Test
    fun pinForPrependOnEmptyInputsIsEmpty() {
        // 首帧 / 切换会话后的空表：不能崩，也不能钉出莫名其妙的东西。
        assertTrue(TurnLayout.pinForPrepend(emptyList(), TurnLayout.blocks(listOf(other("a1")))).isEmpty())
        assertTrue(TurnLayout.pinForPrepend(TurnLayout.blocks(listOf(other("a1"))), emptyList()).isEmpty())
    }
}
