package com.zhizhu.zhicode.compose.model

import java.util.Locale

object ToolGrouping {

    /** 可以折进组的工具名（**大小写不敏感**，与参考实现的 `equals` 语义一致）。 */
    private val CANDIDATES = setOf("read", "readmany", "grep", "glob", "ls", "tree", "stat")

    /** 分组所需的最小信息。 */
    data class Entry(
        val toolId: String,
        val name: String,
        val hint: String = "",
        val readRequests: Int = 0,
        val completed: Boolean = false,
        val failed: Boolean = false,
    )

    /** 批次里的一段。 */
    sealed interface Segment {
        data class Single(val entry: Entry) : Segment
        data class Group(val key: String, val members: List<Entry>) : Segment
    }

    /** 把一个批次的工具切成若干段，**顺序保持不变**。 */
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

    /** 组表头的文案，逐字对齐参考实现 `collapsedActivityLabel` */
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

    /** 组卡片的副行：`⎿ <最新一条 hint> · 点按展开/收起`。 */
    fun subtitle(group: Segment.Group, expanded: Boolean, batchDone: Boolean): String {
        val hint = group.members.lastOrNull { it.hint.isNotBlank() }?.hint.orEmpty()
        val what = hint.ifEmpty { "${group.members.size} 项工具" }
        // 整批跑完就不再提示"点按展开"——那时收起不是用户的动作目标，而是回顾。
        if (batchDone) return "⎿  $what"
        return "⎿  $what · 点按" + if (expanded) "收起" else "展开"
    }

    /** 整批是否已结束（决定组标题是「已…」还是「正在…」，也决定还要不要提示"点按展开"）。 */
    fun isDone(group: Segment.Group): Boolean = group.members.all { it.completed }

    /** 组里有没有失败项（表头字形要变红）。 */
    fun hasFailure(group: Segment.Group): Boolean = group.members.any { it.failed }
}
