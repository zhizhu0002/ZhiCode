package com.zhizhu.zhicode.compose.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 工具操作菜单的决策表与耗时取值。
 *
 * 这两件事以前散在界面与 ViewModel 里，于是出了两个真问题：
 *   1. 单个工具的 `⋯` 打开的是整组的菜单（回调里拿不到行号）；
 *   2. 菜单里混进了 `已全部完成` / `执行中` 这种**状态文字**，点了什么都不发生。
 *
 * 抽成纯函数之后它们可以被逐条钉住 —— 界面只负责把 toolId 传下来。
 */
class ToolActionsTest {

    private fun flags(
        isCommand: Boolean = false,
        hasCommand: Boolean = false,
        hasOutput: Boolean = false,
        hasDiff: Boolean = false,
        completed: Boolean = false,
        expanded: Boolean = false,
    ) = ToolActions.ToolActionFlags(
        isCommand = isCommand,
        hasCommand = hasCommand,
        hasOutput = hasOutput,
        hasDiff = hasDiff,
        completed = completed,
        expanded = expanded,
    )

    // ------------------------------------------------------------ 决策表

    @Test
    fun aCommandWithNoOutputYetOnlyOffersCopyingTheCommand() {
        assertEquals(
            listOf(ToolActions.COPY_COMMAND),
            ToolActions.options(flags(isCommand = true, hasCommand = true)),
        )
    }

    @Test
    fun aRunningCommandOffersLiveOutputNotTheFinalOutput() {
        // 运行中还没有最终输出，"复制输出"会复制到半截内容；此刻用户看得见的是实时输出。
        val options = ToolActions.options(
            flags(isCommand = true, hasCommand = true, hasOutput = true, completed = false),
        )
        assertEquals(listOf(ToolActions.COPY_COMMAND, ToolActions.COPY_LIVE_OUTPUT), options)
        assertFalse("运行中不许出现「复制输出」", options.contains(ToolActions.COPY_OUTPUT))
        assertFalse("运行中不给展开：输出还在长，展开会反复重排", options.contains(ToolActions.EXPAND_OUTPUT))
    }

    @Test
    fun aFinishedCommandOffersCommandOutputAndAToggle() {
        val collapsed = ToolActions.options(
            flags(isCommand = true, hasCommand = true, hasOutput = true, completed = true),
        )
        assertEquals(
            listOf(ToolActions.COPY_COMMAND, ToolActions.COPY_OUTPUT, ToolActions.EXPAND_OUTPUT),
            collapsed,
        )
        val expanded = ToolActions.options(
            flags(
                isCommand = true, hasCommand = true, hasOutput = true,
                completed = true, expanded = true,
            ),
        )
        assertEquals(ToolActions.COLLAPSE_OUTPUT, expanded.last())
    }

    @Test
    fun aCommandWithoutACapturedCommandLineDoesNotOfferCopyingIt() {
        // 命令行是空的（工具名对但入参里没有 command）时，"复制命令"是空操作。
        val options = ToolActions.options(flags(isCommand = true, hasCommand = false))
        assertFalse(options.contains(ToolActions.COPY_COMMAND))
        assertEquals(listOf(ToolActions.COPY_INPUT), options)
    }

    @Test
    fun aDiffResultOffersCopyingTheDiffAndSaysModificationNotOutput() {
        val options = ToolActions.options(
            flags(hasOutput = true, hasDiff = true, completed = true),
        )
        assertEquals(
            listOf(ToolActions.COPY_DIFF, ToolActions.COPY_OUTPUT, ToolActions.EXPAND_DIFF),
            options,
        )
        // 写文件类的展开文案是「展开修改」而不是「展开输出」—— 说的是同一次点击，
        // 但前者才符合用户此刻在看的东西。
        assertFalse(options.contains(ToolActions.EXPAND_OUTPUT))
    }

    @Test
    fun plainOutputOffersCopyAndToggleOnly() {
        assertEquals(
            listOf(ToolActions.COPY_OUTPUT, ToolActions.EXPAND_OUTPUT),
            ToolActions.options(flags(hasOutput = true, completed = true)),
        )
    }

    @Test
    fun aToolWithNothingYetFallsBackToCopyingItsParameters() {
        // 没有命令、没有输出：唯一还有意义的是"复制参数"。
        assertEquals(listOf(ToolActions.COPY_INPUT), ToolActions.options(flags()))
    }

    @Test
    fun theMenuNeverContainsAStatusWordPretendingToBeAnAction() {
        // 这就是那个 bug：第二项曾经是「已全部完成」/「执行中」，点了什么都不发生。
        val banned = listOf("已全部完成", "执行中", "运行中", "等待授权")
        val allStates = ArrayList<List<String>>()
        for (isCommand in listOf(false, true)) {
            for (hasCommand in listOf(false, true)) {
                for (hasOutput in listOf(false, true)) {
                    for (hasDiff in listOf(false, true)) {
                        for (completed in listOf(false, true)) {
                            for (expanded in listOf(false, true)) {
                                allStates.add(
                                    ToolActions.options(
                                        flags(isCommand, hasCommand, hasOutput, hasDiff, completed, expanded),
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
        assertEquals("应当枚举到 64 种组合", 64, allStates.size)
        for (state in allStates) {
            for (label in state) {
                assertFalse(
                    "菜单里不许出现状态文字冒充动作：$label（整份菜单 $state）",
                    banned.contains(label),
                )
                // 每一项都必须是已知的动作，否则点击会落到"不支持的操作"。
                assertTrue(
                    "未登记的动作：$label",
                    ToolActions.copySource(label) != null || ToolActions.isToggle(label),
                )
            }
        }
    }

    @Test
    fun everyMenuHasAtLeastOneItem() {
        // 空菜单会让界面弹出"没有可用操作"，而这里的每一档都至少能复制点什么。
        assertTrue(ToolActions.options(flags()).isNotEmpty())
        assertTrue(ToolActions.options(flags(isCommand = true, hasCommand = true)).isNotEmpty())
    }

    // ------------------------------------------------------------ 选项分类

    @Test
    fun toggleLabelsAreRecognisedAndCarryTheirTargetState() {
        assertTrue(ToolActions.isToggle(ToolActions.EXPAND_OUTPUT))
        assertTrue(ToolActions.isToggle(ToolActions.COLLAPSE_OUTPUT))
        assertTrue(ToolActions.isToggle(ToolActions.EXPAND_DIFF))
        assertTrue(ToolActions.isToggle(ToolActions.COLLAPSE_DIFF))
        // "展开"类指向 true，"折叠"类指向 false —— 目标是**状态**而不是取反，
        // 取反时若状态在期间变了（刚跑完自动展开），结果会与文案相反。
        assertTrue(ToolActions.toggledTo(ToolActions.EXPAND_OUTPUT))
        assertTrue(ToolActions.toggledTo(ToolActions.EXPAND_DIFF))
        assertFalse(ToolActions.toggledTo(ToolActions.COLLAPSE_OUTPUT))
        assertFalse(ToolActions.toggledTo(ToolActions.COLLAPSE_DIFF))
        // 复制类不是 toggle。
        assertFalse(ToolActions.isToggle(ToolActions.COPY_OUTPUT))
        assertNull(ToolActions.copySource(ToolActions.EXPAND_OUTPUT))
    }

    @Test
    fun copySourcesMapOneToOneWithTheCopyLabels() {
        assertEquals(ToolActions.CopySource.COMMAND, ToolActions.copySource(ToolActions.COPY_COMMAND))
        assertEquals(ToolActions.CopySource.OUTPUT, ToolActions.copySource(ToolActions.COPY_OUTPUT))
        assertEquals(ToolActions.CopySource.LIVE_OUTPUT, ToolActions.copySource(ToolActions.COPY_LIVE_OUTPUT))
        assertEquals(ToolActions.CopySource.DIFF, ToolActions.copySource(ToolActions.COPY_DIFF))
        assertEquals(ToolActions.CopySource.INPUT, ToolActions.copySource(ToolActions.COPY_INPUT))
        assertNull("不认识的就是不认识，不许回落到某个默认动作", ToolActions.copySource("随便什么"))
    }

    // ------------------------------------------------------------ 耗时取值

    @Test
    fun withoutAStartPointOnlyTheEngineValueIsUsed() {
        // 工具刚开始（还没有基准）或从历史会话恢复时，只能信引擎推的值。
        assertEquals(1200L, ToolActions.displayElapsedMs(1200L, 0L, 999_999L))
        assertEquals(0L, ToolActions.displayElapsedMs(0L, 0L, 999_999L))
        // 起点"在未来"（时钟回拨）时不产生负数。
        assertEquals(500L, ToolActions.displayElapsedMs(500L, 10_000L, 9_000L))
    }

    @Test
    fun theWallClockKeepsTheLabelMovingWhileTheEngineStaysSilent() {
        // 这就是"跑了 30 秒不吐字的命令，标签冻住"那个问题的修复点：
        // 引擎值停在 2000，而界面侧靠起点自己走到了 9000。
        assertEquals(9000L, ToolActions.displayElapsedMs(2000L, 1_000L, 10_000L))
    }

    @Test
    fun theLargerValueWinsSoTheSecondsNeverJumpBackwards() {
        // 定时刷新与进度回调的频率不同：取小值会让秒数来回跳。
        assertEquals("引擎值更大时用它（进度比时钟快）", 12_000L, ToolActions.displayElapsedMs(12_000L, 1_000L, 10_000L))
        assertEquals("时钟更大时用它", 9_000L, ToolActions.displayElapsedMs(2_000L, 1_000L, 10_000L))
        // 负的引擎值（不该出现，但别让它把显示压成负数）按 0 处理。
        assertEquals(9_000L, ToolActions.displayElapsedMs(-5L, 1_000L, 10_000L))
    }
}
