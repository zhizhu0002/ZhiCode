package com.iqge.iqcode.compose.data

import com.iqge.iqcode.compose.engine.ToolText
import com.iqge.iqcode.compose.model.AgentTask
import com.iqge.iqcode.compose.model.ChatItem
import com.iqge.iqcode.compose.model.ChatKind
import com.iqge.iqcode.compose.model.SessionSummary
import com.iqge.iqcode.compose.model.TaskState
import com.iqge.iqcode.compose.model.ToolActivity
import com.iqge.iqcode.compose.model.ToolKind
import com.termux.app.iqcode.storage.SessionStore
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 把磁盘上的真实会话读成界面模型。
 *
 * **只读**：写操作（新建/恢复/删除/改备注）都在引擎与 ViewModel 里，
 * 因为它们要同时维护引擎内部的对话历史，不只是改一个文件。
 *
 * 数据来源是会话 JSONL 的原始行（[SessionStore.readRows]），
 * 不是 [SessionStore.loadMessages]：后者是给模型看的、经过压缩的 provider 上下文，
 * 拿它渲染界面会**丢掉被压缩掉的历史**，用户会以为消息丢了。
 */
internal object SessionReader {

    /** 侧栏列表。按最近活动倒序。 */
    fun list(projectDirectory: String): List<SessionSummary> {
        val raw = runCatching { SessionStore.listSessions(projectDirectory) }.getOrDefault(emptyList())
        // 先按**原始时间戳**排序再映射成界面模型：
        // 界面模型里只有"3 分钟前"这种相对文案，按它排序是错的。
        return raw.sortedByDescending { it.activityModifiedAt }.map { it.toUi() }
    }

    /** 一条会话的所有行；读不出来就返回空，绝不让界面崩。 */
    private fun rows(file: File): List<JSONObject> {
        val array = runCatching { SessionStore.readRows(file) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
    }

    /**
     * 从 JSONL 重建对话流。
     *
     * 规则来自引擎的写入约定（`IQCodeEngine.appendMessage`）：
     * - `type == "message"` 且 `role == "user"` 且带 `tool_result` 块
     *   → **不是**用户发言，而是工具执行结果，按 `tool_use_id` 挂回对应的工具卡；
     * - 其余 `role == "user"` 且有非空文本 → 用户气泡；
     * - `role == "assistant"` → 依次取 `thinking`（思考）/`text`（正文）/`tool_use`（工具卡）。
     *
     * ⚠️ 最容易写错的一处：工具结果行也是 `role == "user"`。
     * 如果按角色一刀切，历史里会冒出一堆"用户"消息，内容是原始工具输出。
     * 这里用三重判据（有 tool_result 块 / 有非空文本 / origin 不是 internal）来区分。
     */
    fun transcript(file: File): List<ChatItem> {
        val items = mutableListOf<ChatItem>()
        var seq = 0
        fun id(prefix: String) = "$prefix-${++seq}"

        /** 工具 id → 它所在分组卡的下标。用于把结果/进度写回正确的卡。 */
        val toolGroupIndex = mutableMapOf<String, Int>()
        /** tool_use_id → 增删行数（来自 tool_diff 事件）。 */
        val toolDiffs = mutableMapOf<String, Triple<String, Int, Int>>()

        for (row in rows(file)) {
            when (row.optString("type", "")) {
                "message" -> {
                    val role = row.optString("role", "")
                    val content = row.optJSONArray("content") ?: continue
                    if (role == "assistant") {
                        val body = StringBuilder()
                        val thinking = StringBuilder()
                        val toolUses = mutableListOf<JSONObject>()
                        for (i in 0 until content.length()) {
                            val block = content.optJSONObject(i) ?: continue
                            when (block.optString("type", "")) {
                                "text" -> body.append(block.optString("text", ""))
                                "thinking" -> thinking.append(block.optString("thinking", ""))
                                "tool_use" -> toolUses.add(block)
                            }
                        }
                        // 一条 assistant 行可能同时有正文和工具调用：先出正文气泡，再出工具卡，
                        // 与实时回合的渲染顺序一致。
                        if (body.isNotBlank() || thinking.isNotBlank()) {
                            items.add(
                                ChatItem(
                                    id = id("a"),
                                    kind = ChatKind.ASSISTANT,
                                    title = "IQ",
                                    body = body.toString(),
                                    thinking = thinking.toString(),
                                ),
                            )
                        }
                        if (toolUses.isNotEmpty()) {
                            val groupIndex = items.size
                            items.add(ChatItem(id = id("g"), kind = ChatKind.TOOL_GROUP))
                            for (block in toolUses) {
                                val toolId = block.optString("id", "")
                                val name = block.optString("name", "")
                                val input = block.optJSONObject("input")
                                val (added, deleted) = ToolText.delta(name, input)
                                val activity = ToolActivity(
                                    id = toolId,
                                    toolName = name,
                                    displayName = ToolText.userFacingName(name),
                                    summary = ToolText.summary(name, input) ?: "",
                                    additions = added,
                                    deletions = deleted,
                                    kind = kindOf(name),
                                )
                                if (toolId.isNotEmpty()) toolGroupIndex[toolId] = groupIndex
                                val group = items[groupIndex]
                                val tools = group.tools + activity
                                items[groupIndex] = group.copy(tools = tools, groupLabel = groupLabel(tools))
                            }
                        }
                    } else {
                        // user 行：要么是人类发言，要么是工具结果，二者必须分开。
                        val text = StringBuilder()
                        var hasToolResult = false
                        val results = mutableListOf<Pair<String, JSONObject>>()
                        for (i in 0 until content.length()) {
                            val block = content.optJSONObject(i) ?: continue
                            when (block.optString("type", "")) {
                                "tool_result" -> {
                                    hasToolResult = true
                                    results.add(block.optString("tool_use_id", "") to block)
                                }
                                "text" -> text.append(block.optString("text", ""))
                            }
                        }
                        for ((toolId, block) in results) {
                            val index = toolGroupIndex[toolId] ?: continue
                            val group = items[index]
                            val diff = toolDiffs[toolId]
                            items[index] = group.copy(
                                tools = group.tools.map { tool ->
                                    if (tool.id != toolId) tool
                                    else tool.copy(
                                        completed = true,
                                        failed = block.optBoolean("is_error", false),
                                        output = block.optString("content", ""),
                                        additions = diff?.second ?: tool.additions,
                                        deletions = diff?.third ?: tool.deletions,
                                    )
                                },
                            )
                        }
                        // 工具结果行**不算发言**。只有"有非空文本且来源不是内部"才是用户说的。
                        val origin = row.optString("origin", "")
                        if (!hasToolResult && text.isNotBlank() && origin != "internal") {
                            items.add(
                                ChatItem(
                                    id = id("u"),
                                    kind = ChatKind.USER,
                                    title = "你",
                                    body = text.toString(),
                                ),
                            )
                        }
                    }
                }
                "tool_diff" -> {
                    // 增删行数单独落在事件里，工具结果本身不带。先收集，等结果行到达时用。
                    val payload = row.optJSONObject("payload") ?: continue
                    val toolId = payload.optString("tool_use_id", "")
                    if (toolId.isNotEmpty()) {
                        toolDiffs[toolId] = Triple(
                            payload.optString("tool_name", ""),
                            payload.optInt("additions", 0),
                            payload.optInt("deletions", 0),
                        )
                    }
                }
            }
        }
        // 整组工具都已完成的，标上完成态（用于分组标题的「已运行 N 个工具」与折叠行为）。
        return items.map { item ->
            if (item.kind != ChatKind.TOOL_GROUP) item
            else item.copy(groupCompleted = item.tools.isNotEmpty() && item.tools.all { it.completed })
        }
    }

    /** 任务清单：取最后一条 `task_snapshot` 事件（引擎在每次任务变更时都会追加）。 */
    fun tasks(file: File): List<AgentTask> {
        var latest: List<AgentTask> = emptyList()
        for (row in rows(file)) {
            if (row.optString("type", "") != "task_snapshot") continue
            val payload = row.optJSONObject("payload") ?: continue
            val array = payload.optJSONArray("tasks") ?: continue
            val tasks = mutableListOf<AgentTask>()
            for (i in 0 until array.length()) {
                val json = array.optJSONObject(i) ?: continue
                toAgentTask(json)?.let { tasks.add(it) }
            }
            latest = tasks
        }
        return latest
    }

    fun delete(file: File): Boolean = runCatching { SessionStore.deleteSession(file) }.getOrDefault(false)

    /** 备注 / 标题覆盖。空字符串表示清除该项。 */
    fun updateMetadata(file: File, note: String, titleOverride: String): Boolean =
        runCatching { SessionStore.updateSessionMetadata(file, note, titleOverride); true }.getOrDefault(false)

    // ------------------------------------------------------------------

    private fun SessionStore.SessionSummary.toUi(): SessionSummary {
        // 用绝对路径当 id：唯一、稳定，而且能从 id 直接拿回文件，不需要再查一次列表。
        return SessionSummary(
            id = file.absolutePath,
            title = if (title.isBlank()) "新会话" else title,
            project = File(project.ifBlank { "/" }).name,
            messageCount = messageCount,
            updatedAtLabel = relativeTime(activityModifiedAt),
            note = note,
        )
    }

    private fun toAgentTask(json: JSONObject): AgentTask? {
        val subject = json.optString("subject", "")
        if (subject.isBlank()) return null
        val activeForm = json.optString("activeForm", "")
        val state = when (json.optString("status", "pending")) {
            "completed" -> TaskState.DONE
            "in_progress" -> TaskState.RUNNING
            else -> TaskState.PENDING
        }
        return AgentTask(
            title = subject,
            detail = if (state == TaskState.RUNNING && activeForm.isNotBlank()) activeForm
            else json.optString("description", ""),
            state = state,
        )
    }

    private fun kindOf(toolName: String): ToolKind = when (toolName) {
        "Grep", "Glob", "Search" -> ToolKind.SEARCH
        "Read", "ReadMany", "LS", "List", "Tree", "Stat" -> ToolKind.READ
        "Edit", "MultiEdit", "Write", "Move", "Delete", "Mkdir", "Copy" -> ToolKind.EDIT
        "Bash", "Root", "BashTool" -> ToolKind.COMMAND
        else -> ToolKind.OTHER
    }

    private fun groupLabel(tools: List<ToolActivity>): String {
        val parts = mutableListOf<String>()
        val searches = tools.count { it.kind == ToolKind.SEARCH }
        val reads = tools.count { it.kind == ToolKind.READ }
        val edits = tools.count { it.kind == ToolKind.EDIT }
        val commands = tools.count { it.kind == ToolKind.COMMAND }
        if (searches > 0) parts += "搜索 $searches 个模式"
        if (reads > 0) parts += "读取 $reads 个文件"
        if (edits > 0) parts += "修改 $edits 处代码"
        if (commands > 0) parts += "执行 $commands 条命令"
        if (parts.isEmpty()) parts += "调用 ${tools.size} 个工具"
        return parts.joinToString("、")
    }

    /**
     * 相对时间。刚写下的会话显示"刚刚"，避免出现"0 分钟前"这种别扭文案。
     */
    private fun relativeTime(timestamp: Long): String {
        if (timestamp <= 0L) return ""
        val now = System.currentTimeMillis()
        val delta = now - timestamp
        if (delta < 60_000L) return "刚刚"
        if (delta < 3_600_000L) return "${delta / 60_000L} 分钟前"
        val today = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = timestamp }
        if (today.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
            today.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
        ) {
            return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        }
        if (today.get(Calendar.YEAR) == then.get(Calendar.YEAR)) {
            return SimpleDateFormat("M月d日", Locale.getDefault()).format(Date(timestamp))
        }
        return SimpleDateFormat("yyyy年M月d日", Locale.getDefault()).format(Date(timestamp))
    }
}
