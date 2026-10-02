package com.zhizhu.zhicode.compose.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「哪些工具该折起来、哪些该单独一行」。
 *
 * 这条规则没有任何一处会编译失败：候选集少一个名字、`>= 2` 写成 `> 2`、
 * 断开条件写错，表现都只是"折叠得不太对"，只能靠逐条断言钉住。
 * 判据来自参考实现 `ToolActivityGrouper` / `collapsedActivityLabel`。
 */
class ToolGroupingTest {

    private fun entry(
        id: String,
        name: String,
        hint: String = "",
        readRequests: Int = 0,
        completed: Boolean = false,
        failed: Boolean = false,
    ) = ToolGrouping.Entry(id, name, hint, readRequests, completed, failed)

    private fun group(vararg entries: ToolGrouping.Entry): ToolGrouping.Segment.Group =
        ToolGrouping.Segment.Group("k", entries.toList())

    // ------------------------------------------------------------ 成组与不成组

    @Test
    fun aSingleCommandStaysAFreeStandingRow() {
        // 这就是截图里的那一条：IQ Code 不会给单独一条 Bash 套卡片。
        val segments = ToolGrouping.group(listOf(entry("t1", "Bash", hint = "ls")))
        assertEquals(1, segments.size)
        assertTrue(segments[0] is ToolGrouping.Segment.Single)
    }

    @Test
    fun aLoneReadIsNotWorthAGroup() {
        // 组里只有一条 = 用户还得多点一次才能看到唯一的内容，纯噪音。
        val segments = ToolGrouping.group(listOf(entry("t1", "Read", hint = "a.kt")))
        assertTrue(segments[0] is ToolGrouping.Segment.Single)
    }

    @Test
    fun twoAdjacentReadsCollapse() {
        val segments = ToolGrouping.group(
            listOf(entry("t1", "Read", hint = "a.kt"), entry("t2", "Read", hint = "b.kt")),
        )
        assertEquals(1, segments.size)
        val group = segments[0] as ToolGrouping.Segment.Group
        assertEquals(listOf("t1", "t2"), group.members.map { it.toolId })
        // key 取首成员：工具是边跑边追加的，首成员一旦确定就不会变。
        assertEquals("t1", group.key)
    }

    @Test
    fun aCommandInBetweenSplitsTheRuns() {
        // "读了 3 个、跑一条、又读 2 个" 折成一组就看不清那条命令在前还是在后了。
        val segments = ToolGrouping.group(
            listOf(
                entry("r1", "Read"), entry("r2", "Read"),
                entry("b1", "Bash"),
                entry("r3", "Read"), entry("r4", "Grep"),
            ),
        )
        assertEquals(3, segments.size)
        assertTrue(segments[0] is ToolGrouping.Segment.Group)
        assertTrue(segments[1] is ToolGrouping.Segment.Single)
        assertTrue(segments[2] is ToolGrouping.Segment.Group)
    }

    @Test
    fun writeEditAndAgentAreNeverCandidates() {
        for (name in listOf("Bash", "Root", "Write", "Edit", "MultiEdit", "Move", "Delete", "Mkdir", "Agent", "Task")) {
            assertFalse(name + " 不该成组", ToolGrouping.isCandidate(name))
        }
    }

    @Test
    fun theCandidateSetIsExactlyTheSevenReferenceNames() {
        for (name in listOf("Read", "ReadMany", "Grep", "Glob", "LS", "Tree", "Stat")) {
            assertTrue(name + " 应当是候选", ToolGrouping.isCandidate(name))
        }
        // 大小写不敏感（引擎给的名字大小写不保证）。
        assertTrue(ToolGrouping.isCandidate("read"))
        // `List` 是 LS 的别名，但参考实现的候选集里**没有**它 —— 照抄，不擅自扩。
        assertFalse(ToolGrouping.isCandidate("List"))
    }

    @Test
    fun anEntryWithoutAnIdFallsBackToASingleRow() {
        // 没有 id 就没法做展开态的键。
        val segments = ToolGrouping.group(
            listOf(entry("", "Read"), entry("t2", "Read"), entry("t3", "Read")),
        )
        assertTrue(segments[0] is ToolGrouping.Segment.Single)
        assertTrue(segments[1] is ToolGrouping.Segment.Group)
    }

    @Test
    fun anEmptyBatchProducesNothing() {
        assertEquals(emptyList<ToolGrouping.Segment>(), ToolGrouping.group(emptyList()))
    }

    @Test
    fun orderIsPreserved() {
        val segments = ToolGrouping.group(
            listOf(
                entry("b1", "Bash"), entry("r1", "Read"), entry("r2", "Read"), entry("w1", "Write"),
            ),
        )
        assertEquals(
            listOf("b1", "r1", "w1"),
            segments.map {
                when (it) {
                    is ToolGrouping.Segment.Single -> it.entry.toolId
                    is ToolGrouping.Segment.Group -> it.key
                }
            },
        )
    }

    // ------------------------------------------------------------ 表头文案

    @Test
    fun theLabelMatchesTheReferenceWording() {
        assertEquals(
            "正在搜索 2 个模式、读取 1 个文件",
            ToolGrouping.label(group(entry("1", "Grep"), entry("2", "Glob"), entry("3", "Read")), batchDone = false),
        )
        assertEquals(
            "已读取 3 个文件",
            ToolGrouping.label(
                group(entry("1", "Read", completed = true), entry("2", "Read", completed = true), entry("3", "Read", completed = true)),
                batchDone = true,
            ),
        )
    }

    @Test
    fun readManyCountsItsPathsNotItself() {
        // ReadMany 带 3 条路径 = 读了 3 个文件，而不是 1 个。
        val label = ToolGrouping.label(
            group(entry("1", "ReadMany", readRequests = 3), entry("2", "Read")),
            batchDone = false,
        )
        assertEquals("正在读取 4 个文件", label)
    }

    @Test
    fun readManyWithNoPathsStillCountsAsOne() {
        // `maxOf(1, readRequests)`：没带路径的 ReadMany 至少算读了 1 个，
        // 而不是 0（否则一个全是 ReadMany 的组会显示"读取 0 个文件"）。
        assertEquals(
            "正在读取 2 个文件",
            ToolGrouping.label(group(entry("1", "ReadMany", readRequests = 0), entry("2", "Read")), batchDone = false),
        )
        assertEquals(
            "正在读取 2 个文件",
            ToolGrouping.label(group(entry("1", "ReadMany", readRequests = 0), entry("2", "ReadMany", readRequests = 0)), batchDone = false),
        )
    }

    @Test
    fun lsTreeAndStatCountAsLocations() {
        assertEquals(
            "正在查看 3 个位置",
            ToolGrouping.label(group(entry("1", "LS"), entry("2", "Tree"), entry("3", "Stat")), batchDone = false),
        )
    }

    @Test
    fun failuresWinOverIncompletenessInTheSuffix() {
        val members = group(
            entry("1", "Read", completed = true, failed = true),
            entry("2", "Read", completed = false),
        )
        assertEquals("已读取 2 个文件 · 1 项失败", ToolGrouping.label(members, batchDone = true))
    }

    @Test
    fun anUnfinishedMemberIsReportedOnceTheBatchIsDone() {
        val members = group(entry("1", "Read", completed = true), entry("2", "Read", completed = false))
        assertEquals("已读取 2 个文件 · 1 项未完成", ToolGrouping.label(members, batchDone = true))
        // 还在跑的时候不需要"未完成"这句 —— 那时这是常态，不是异常。
        assertEquals("正在读取 2 个文件", ToolGrouping.label(members, batchDone = false))
    }

    // ------------------------------------------------------------ 副行

    @Test
    fun theSubtitleShowsTheLatestHintAndTheTapHint() {
        val members = group(entry("1", "Read", hint = "a.kt"), entry("2", "Read", hint = "b.kt"))
        assertEquals("⎿  b.kt · 点按展开", ToolGrouping.subtitle(members, expanded = false, batchDone = false))
        assertEquals("⎿  b.kt · 点按收起", ToolGrouping.subtitle(members, expanded = true, batchDone = false))
    }

    @Test
    fun theSubtitleSkipsBlankHintsAndFallsBackToACount() {
        assertEquals(
            "⎿  a.kt · 点按展开",
            ToolGrouping.subtitle(group(entry("1", "Read", hint = "a.kt"), entry("2", "Read", hint = "  ")), false, false),
        )
        assertEquals(
            "⎿  2 项工具 · 点按展开",
            ToolGrouping.subtitle(group(entry("1", "LS"), entry("2", "Stat")), expanded = false, batchDone = false),
        )
    }

    @Test
    fun aFinishedGroupStopsAskingToTap() {
        val members = group(entry("1", "Read", hint = "a.kt", completed = true), entry("2", "Read", completed = true))
        assertEquals("⎿  a.kt", ToolGrouping.subtitle(members, expanded = false, batchDone = true))
    }

    // ------------------------------------------------------------ 整组状态

    @Test
    fun doneAndFailureAreComputedPerGroupNotPerBatch() {
        val mixed = group(entry("1", "Read", completed = true), entry("2", "Read", completed = false, failed = true))
        assertFalse(ToolGrouping.isDone(mixed))
        assertTrue(ToolGrouping.hasFailure(mixed))

        val fine = group(entry("1", "Read", completed = true), entry("2", "Read", completed = true))
        assertTrue(ToolGrouping.isDone(fine))
        assertFalse(ToolGrouping.hasFailure(fine))
    }
}
