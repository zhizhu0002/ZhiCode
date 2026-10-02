package com.zhizhu.zhicode.compose.model

/**
 * 对话流里**单个工具**的操作菜单（纯逻辑，无 Android 依赖）。
 *
 * ## 为什么要有这一层
 *
 * 一个工具能做什么，取决于它**当前是什么状态**：命令类可以复制命令，有输出的才能复制输出，
 * 写文件类才有 Diff，没跑完的才有"实时输出"、跑完了才有"展开/折叠"。
 * 这些判断原本散在界面与 ViewModel 里，于是出现了两个真问题：
 *
 * 1. **单个工具的 `⋯` 打开的是整组的菜单**：`ToolGroupCard` 把同一个回调传给每一行，
 *     回调里拿到的只有"哪一组"，不知道"哪一行"。
 * 2. **菜单里混进了状态文字**：有一项是 `已全部完成` / `执行中` —— 那不是动作，
 *     点下去什么都不会发生，而用户会以为是坏了。
 *
 * 把决策抽成一个纯函数之后：菜单内容**只由这一处决定**（表驱动、可单测），
 * 界面只负责把 `toolId` 传下来，谁也再拼不出一份不一样的菜单。
 *
 * ## 与参考实现（IQ Code `showToolActions`）的关系
 *
 * 分支结构照它来：判据是"是不是命令 / 有没有输出 / 有没有 diff / 跑完没 / 展开没"，
 * 按组合给出 2~3 项。这里把判据做成显式的 [ToolActionFlags]，而不是在函数里
 * 到处读 `ChatItem` 的字段 —— 那样这个函数就没法在秒级回路里跑了。
 */
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

    /**
     * 构造菜单所需的全部判据。
     *
     * @param isCommand   命令类工具（Bash / Root / Shizuku）—— 只有它们才有"命令"可复制
     * @param hasCommand  真的取到了命令行（为空时不显示"复制命令"，那是空操作）
     * @param hasOutput   已经有输出（含运行中的实时输出）
     * @param hasDiff     输出是文件 diff（写文件类工具才会有）
     * @param completed   工具是否已结束。**运行中不给"展开输出"**：那时输出还在长，
     *                    展开后每来一块就要重排一次；而且运行中的输出面板本来就是自动跟的
     * @param expanded    当前是否已展开
     */
    data class ToolActionFlags(
        val isCommand: Boolean,
        val hasCommand: Boolean,
        val hasOutput: Boolean,
        val hasDiff: Boolean,
        val completed: Boolean,
        val expanded: Boolean,
    )

    /**
     * 按状态给出该显示的菜单项（可能为空 —— 那就不要弹菜单）。
     *
     * 分支顺序照参考实现：先处理"命令 + 有输出"这一档（它们的选项最具体），
     * 再退到只有命令、再退到 diff、再退到只有输出，最后是"什么都没有 → 复制参数"。
     *
     * 每个分支都给了**两三项**，而不是一项：只给一项等于让用户为一个动作点两次
     * （打开菜单 → 点那一项）。至少要有"复制 + 展开/折叠"的组合才值得弹。
     */
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

    /**
     * 展开/折叠类选项不进"动作"分派 —— 它们改的是界面状态，不是复制。
     *
     * 单独判一次而不是让调用方去比对字符串：文案是常量，比较集中在一处就不会漏。
     */
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

    /**
     * 运行中要显示的耗时。
     *
     * ## 为什么不能只用引擎推的那个值
     *
     * `elapsedMs` 是**跟着输出块**推过来的（引擎按 chunk 回调进度）。一个跑 30 秒
     * 都不吐字的命令，那个值就停在最后一次进度的位置上 —— 界面上看起来像卡死了。
     * 参考实现用界面侧的定时刷新续走，我们这边缺的是**时间基准**：
     * 光有"已经过了多久"没法自己往前推，所以 [ToolActivity.startedAtMs] 记下起点。
     *
     * 取两者较大值：
     * - 还没有基准（`startedAtMs <= 0`，工具刚开始、或从历史会话恢复）→ 只信引擎值；
     * - 有基准 → 用「现在 - 起点」，但**不倒退**（引擎值更大时用它）：
     *   定时刷新与进度回调的频率不一样，取小值会让秒数来回跳。
     *
     * 工具已结束时**不在这里处理** —— 调用方不该对已结束的工具调用它，
     * 否则计时会一直涨；这里只在两个值都为 0 时返回 0。
     */
    fun displayElapsedMs(elapsedFromEngine: Long, startedAtMs: Long, nowMs: Long): Long {
        val engine = if (elapsedFromEngine > 0) elapsedFromEngine else 0L
        if (startedAtMs <= 0L || nowMs <= startedAtMs) return engine
        val wall = nowMs - startedAtMs
        return if (wall > engine) wall else engine
    }
}
