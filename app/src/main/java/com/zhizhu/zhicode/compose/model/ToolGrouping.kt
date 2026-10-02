package com.zhizhu.zhicode.compose.model

import java.util.Locale

/**
 * 工具批次的分组规则（纯逻辑，无 Android 依赖）。
 *
 * ## 它解决的是什么
 *
 * 一个批次里的工具**不该长相一致**。读文件、搜模式这类调用往往一次来七八个，
 * 每条都画一整行会把对话流淹掉；而 `Bash` / `Write` / `Edit` 是"这个回合真正干了什么"，
 * 必须独立可见。
 *
 * 规则逐字来自参考实现（IQ Code `ToolActivityGrouper` + `collapsedActivityLabel`）：
 *
 * 1. **只有那七类才是候选**：`Read` `ReadMany` `Grep` `Glob` `LS` `Tree` `Stat`。
 *    `Bash`（含 `Root`）、`Write`、`Edit`、`MultiEdit`、`Agent` 等一律**不成组** ——
 *    它们就是截图里那种"扁平一行"。
 * 2. **候选要连续**：中间夹了一个非候选（比如一条 `Bash`）就断开，两边的候选各算各的。
 *    这条很重要 —— 否则"读了 3 个文件、跑了一条命令、又读 2 个文件"会被折成一组，
 *    而那条命令到底在两次读取之前还是之后就看不清了。
 * 3. **连续候选不足 2 条就不成组**：单个 `Read` 仍然单独一行。一个"组"里只有一条
 *    是纯粹的视觉噪音 —— 用户还要多点一次才能看到那唯一的内容。
 *
 * ## 为什么必须在纯逻辑层
 *
 * 上面三条没有任何一条会编译失败：候选集少写一个名字、`>= 2` 写成 `> 2`、
 * 断开条件写错，表现都只是"折叠得不太对"。只有逐条断言才钉得住。
 */
object ToolGrouping {

    /** 可以折进组的工具名（**大小写不敏感**，与参考实现的 `equals` 语义一致）。 */
    private val CANDIDATES = setOf("read", "readmany", "grep", "glob", "ls", "tree", "stat")

    /**
     * 分组所需的最小信息。
     *
     * @param toolId       工具 id（[Segment.Group] 的 key 取首成员的这个值）
     * @param name         引擎给的原始工具名（`Read` / `ReadMany` / …），判断候选用它
     * @param hint         这一条在副行里要显示的路径/模式（[ToolText.activityHint] 的产物）
     * @param readRequests `ReadMany` 读了几个文件（单个 `Read` 算 1，其余算 0）
     * @param completed    是否已结束
     * @param failed       是否失败
     */
    data class Entry(
        val toolId: String,
        val name: String,
        val hint: String = "",
        val readRequests: Int = 0,
        val completed: Boolean = false,
        val failed: Boolean = false,
    )

    /**
     * 批次里的一段。
     *
     * [Group.key] 取**首成员的 toolId**：工具是边跑边追加的，首成员一旦确定就不会变，
     * 所以展开态可以直接用它当键，不需要另外分配 id（也就不用管 id 的回收）。
     */
    sealed interface Segment {
        data class Single(val entry: Entry) : Segment
        data class Group(val key: String, val members: List<Entry>) : Segment
    }

    /**
     * 把一个批次的工具切成若干段，**顺序保持不变**。
     *
     * 空 id 的条目直接降级成单条：没有 id 就没法做展开态的键，硬塞进组里会让
     * 整组跟着一个无名条目一起展开。
     */
    fun group(entries: List<Entry>): List<Segment> {
        if (entries.isEmpty()) return emptyList()
        val out = ArrayList<Segment>(entries.size)
        var run = ArrayList<Entry>()

        fun flush() {
            when {
                run.isEmpty() -> Unit
                // 不足 2 条就拆回单条 —— 见类注释第 3 条。
                run.size < 2 -> run.forEach { out += Segment.Single(it) }
                else -> out += Segment.Group(key = run.first().toolId, members = run.toList())
            }
            run = ArrayList()
        }

        for (entry in entries) {
            val usable = entry.toolId.isNotEmpty() && isCandidate(entry.name)
            if (!usable) {
                flush()
                out += Segment.Single(entry)
                continue
            }
            run.add(entry)
        }
        flush()
        return out
    }

    /** 这个工具名能不能折进组。 */
    fun isCandidate(name: String): Boolean = name.lowercase(Locale.US) in CANDIDATES

    /**
     * 组表头的文案，逐字对齐参考实现 `collapsedActivityLabel`：
     *
     * ```
     * 正在搜索 2 个模式、读取 3 个文件
     * 已读取 3 个文件 · 1 项失败
     * 已查看 2 个位置 · 1 项未完成
     * ```
     *
     * 计数口径也照抄：`Grep`/`Glob` 算"搜索一个模式"；`Read` 算读 1 个文件；
     * `ReadMany` 按它实际带了几条路径算；`LS`/`Tree`/`Stat` 算"查看一个位置"。
     *
     * @param batchDone 整批是否已结束（决定开头是「已」还是「正在」）
     */
    fun label(group: Segment.Group, batchDone: Boolean): String {
        var searches = 0
        var reads = 0
        var locations = 0
        for (m in group.members) {
            when (m.name.lowercase(Locale.US)) {
                "grep", "glob" -> searches++
                "read" -> reads++
                "readmany" -> reads += maxOf(1, m.readRequests)
                else -> locations++
            }
        }
        val parts = ArrayList<String>(3)
        if (searches > 0) parts += "搜索 $searches 个模式"
        if (reads > 0) parts += "读取 $reads 个文件"
        if (locations > 0) parts += "查看 $locations 个位置"

        val builder = StringBuilder(if (batchDone) "已" else "正在")
        if (parts.isEmpty()) {
            // 只有七个候选名能进组，而它们各自都会落进上面三个计数之一，
            // 所以这一支正常走不到 —— 留着是为了"绝不显示一个光秃秃的『已』"。
            builder.append(group.members.size).append(" 项工具")
        } else {
            builder.append(parts.joinToString("、"))
        }

        val failed = group.members.count { it.failed }
        if (failed > 0) {
            builder.append(" · ").append(failed).append(" 项失败")
        } else if (batchDone) {
            val missing = group.members.count { !it.completed }
            if (missing > 0) builder.append(" · ").append(missing).append(" 项未完成")
        }
        return builder.toString()
    }

    /**
     * 组卡片的副行：`⎿ <最新一条 hint> · 点按展开/收起`。
     *
     * 取**最后一条非空 hint**（不是第一条）：它代表这一组最近在读/搜什么，
     * 而用户对"最后那一条"最有印象。一条 hint 都没有时退回"N 项工具" ——
     * 副行空着会让这张卡片看起来坏了一半。
     */
    fun subtitle(group: Segment.Group, expanded: Boolean, batchDone: Boolean): String {
        val hint = group.members.lastOrNull { it.hint.isNotBlank() }?.hint.orEmpty()
        val what = hint.ifEmpty { "${group.members.size} 项工具" }
        // 整批跑完就不再提示"点按展开"——那时收起不是用户的动作目标，而是回顾。
        if (batchDone) return "⎿  $what"
        return "⎿  $what · 点按" + if (expanded) "收起" else "展开"
    }

    /**
     * 整批是否已结束（决定组标题是「已…」还是「正在…」，也决定还要不要提示"点按展开"）。
     *
     * 按**这一组自己的成员**算，而不是整批：一批里可能既有一组读文件、又有几条命令，
     * 用整批的状态会让"已经读完的那一组"一直显示「正在读取」。
     */
    fun isDone(group: Segment.Group): Boolean = group.members.all { it.completed }

    /** 组里有没有失败项（表头字形要变红）。 */
    fun hasFailure(group: Segment.Group): Boolean = group.members.any { it.failed }
}
