package com.zhizhu.zhicode.compose.model

object ToolActions {

    // 文案即契约：ViewModel 按这些字符串分派动作，所以它们是常量而不是散落的字面量。
    const val COPY_COMMAND = "复制命令"
    const val COPY_OUTPUT = "复制输出"
    const val COPY_LIVE_OUTPUT = "复制实时输出"
    const val COPY_DIFF = "复制 Diff"
    const val COPY_INPUT = "复制参数"
    const val EXPAND_OUTPUT = "展开输出"
    const val COLLAPSE_OUTPUT = "折叠输出"
    const val EXPAND_DIFF = "展开修改"
    const val COLLAPSE_DIFF = "折叠修改"

    /** 构造菜单所需的全部判据。 */
    data class ToolActionFlags(
        val isCommand: Boolean,
        val hasCommand: Boolean,
        val hasOutput: Boolean,
        val hasDiff: Boolean,
        val completed: Boolean,
        val expanded: Boolean,
    )

    fun flags(
        kind: ToolKind,
        summary: String,
        output: String,
        isFileDiff: Boolean,
        completed: Boolean,
        expanded: Boolean,
    ): ToolActionFlags {
        val isCommand = kind == ToolKind.COMMAND
        return ToolActionFlags(
            isCommand = isCommand,
            hasCommand = isCommand && summary.isNotBlank(),
            hasOutput = output.isNotBlank(),
            // diff 是输出里的一段：没有输出就谈不上 diff，这个合取放在这里
            // （调用方只回答"这段输出是不是 diff"）。
            hasDiff = output.isNotBlank() && isFileDiff,
            completed = completed,
            expanded = expanded,
        )
    }

    /** 按状态给出该显示的菜单项（可能为空 —— 那就不要弹菜单）。 */
    fun options(flags: ToolActionFlags): List<String> {
        val toggle = when {
            !flags.completed -> null
            flags.hasDiff -> if (flags.expanded) COLLAPSE_DIFF else EXPAND_DIFF
            flags.hasOutput -> if (flags.expanded) COLLAPSE_OUTPUT else EXPAND_OUTPUT
            else -> null
        }

        if (flags.isCommand && flags.hasCommand) {
            if (!flags.hasOutput) return listOf(COPY_COMMAND)
            // 运行中：还没有最终输出，"复制输出"会复制到半截内容，所以给的是"实时输出"
            // （那正是用户此刻看得见的东西）。
            return if (!flags.completed) {
                listOf(COPY_COMMAND, COPY_LIVE_OUTPUT)
            } else {
                listOfNotNull(COPY_COMMAND, COPY_OUTPUT, toggle)
            }
        }

        if (flags.hasOutput) {
            return if (flags.hasDiff) {
                listOfNotNull(COPY_DIFF, COPY_OUTPUT, toggle)
            } else {
                listOfNotNull(COPY_OUTPUT, toggle)
            }
        }

        // 没有命令也没有输出：退到"复制参数"，这是唯一还有意义的东西。
        return listOf(COPY_INPUT)
    }

    /** 展开/折叠类选项不进"动作"分派 —— 它们改的是界面状态，不是复制。 */
    fun isToggle(label: String): Boolean = label == EXPAND_OUTPUT || label == COLLAPSE_OUTPUT ||
        label == EXPAND_DIFF || label == COLLAPSE_DIFF

    /** 该选项要求的目标展开状态（`isToggle` 为真时才有意义）。 */
    fun toggledTo(label: String): Boolean = label == EXPAND_OUTPUT || label == EXPAND_DIFF

    /** 复制类选项要复制哪一段内容。 */
    enum class CopySource { COMMAND, OUTPUT, LIVE_OUTPUT, DIFF, INPUT }

    /** 把选项映射成"复制什么"；不是复制类则返回 null。 */
    fun copySource(label: String): CopySource? = when (label) {
        COPY_COMMAND -> CopySource.COMMAND
        COPY_OUTPUT -> CopySource.OUTPUT
        COPY_LIVE_OUTPUT -> CopySource.LIVE_OUTPUT
        COPY_DIFF -> CopySource.DIFF
        COPY_INPUT -> CopySource.INPUT
        else -> null
    }

    fun displayElapsedMs(elapsedFromEngine: Long, startedAtMs: Long, nowMs: Long): Long {
        val engine = if (elapsedFromEngine > 0) elapsedFromEngine else 0L
        if (startedAtMs <= 0L || nowMs <= startedAtMs) return engine
        val wall = nowMs - startedAtMs
        return if (wall > engine) wall else engine
    }
}
