package com.zhizhu.zhicode.compose.data

import com.zhizhu.zhicode.compose.engine.ToolText
import com.zhizhu.zhicode.compose.model.AgentTask
import com.zhizhu.zhicode.compose.model.ChatImage
import com.zhizhu.zhicode.compose.model.ChatItem
import com.zhizhu.zhicode.compose.model.ChatKind
import com.zhizhu.zhicode.compose.model.SessionSummary
import com.zhizhu.zhicode.compose.model.TaskState
import com.zhizhu.zhicode.compose.model.ToolActivity
import com.zhizhu.zhicode.compose.model.ToolKind
import com.termux.app.zhicode.storage.SessionStore
import org.json.JSONArray
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
     * 规则来自引擎的写入约定（`ZhiCodeEngine.appendMessage`）：
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
        /*
         * 恢复出来的条目 id 必须与 WorkspaceViewModel.nextId() **命名空间不重叠**。
         *
         * 为什么不能直接用 "u-1"：ViewModel 的 idCounter 是进程内从 0 开始的，
         * 而启动时会自动恢复最近一条会话。于是就会出现
         *   恢复：SessionReader 生成 u-1, u-2, u-3
         *   发送：ViewModel.nextId("u") 因为 idCounter 还是 0，又生成一个 u-1
         * 同一份对话流里出现两个 u-1。Compose 的 LazyColumn 以 id 作 key，
         * 重复 key 会直接抛 IllegalArgumentException 把整个界面崩掉，
         * 而且只在两条都进入可视区时才触发——表现为"发一条消息就闪退"，很难查。
         *
         * 实测确认过这条路径：恢复后发送得到 id=[u-1, u-2, u-3, u-1, e-2]，重复 {u-1=2}。
         *
         * 加 "session-" 前缀后，本函数产出的 id 一定形如 session-u-1，
         * 而 ViewModel 只会产出 (a|att|e|g|i|skill|u|web)-<数字>，两者不可能相等。
         */
        fun id(prefix: String) = "session-$prefix-${++seq}"

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
                                    title = "智蛛",
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
                                    // 会话记录里存着原始入参，命令类工具的命令行就在里面。
                                    // 不读出来的话，恢复历史后展开一条 Bash 只能看到
                                    // `truncateCommand` 截过的摘要（前两行 + `…`）。
                                    command = input?.optString("command", "") ?: "",
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
                        // 图片：引擎把用户发的图作为 image 块写在同一条 user 消息里
                        // （见 ZhiCodeEngine.buildUserContent），这里读回来挂到气泡上。
                        val images = readImages(content)
                        // 工具结果行**不算发言**。只有"有内容且来源不是内部"才是用户说的。
                        //
                        // ⚠️ 判据必须包含 images：只发图、不写字的消息没有 text 块，
                        // 早先按 `text.isNotBlank()` 判断会把这条消息**整个丢掉** ——
                        // 用户回头翻历史，会发现自己发的那张图连带那条消息都不存在。
                        val origin = row.optString("origin", "")
                        if (!hasToolResult && (text.isNotBlank() || images.isNotEmpty()) && origin != "internal") {
                            items.add(
                                ChatItem(
                                    id = id("u"),
                                    kind = ChatKind.USER,
                                    title = "你",
                                    body = text.toString(),
                                    images = images,
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
        //
        // ⚠️ 同时给**没有结果的**工具收口：会话记录里可能留着只有调用、没有结果的事件
        // （进程被杀、写入中断、旧版本记录格式）。它们恢复出来会是 `completed = false`，
        // 而界面把"未完成"一律画成转圈 + 「运行中…」—— 于是一条几天前的记录里会
        // **永远**有一个转圈的工具行，看着像卡住了。
        //
        // 参考实现（IQ Code `MainActivity`）同样在恢复时把它们标成失败，并补一句
        // 「会话记录未包含该工具的结果。」。照做：这里不是"猜一个结果"，
        // 而是如实说明"记录里没有"。
        return items.map { item ->
            if (item.kind != ChatKind.TOOL_GROUP) {
                item
            } else {
                val closed = item.tools.map { tool ->
                    if (tool.completed) {
                        tool
                    } else {
                        tool.copy(
                            completed = true,
                            failed = true,
                            output = tool.output.ifBlank { UNFINISHED_TOOL_NOTE },
                        )
                    }
                }
                item.copy(
                    tools = closed,
                    groupCompleted = closed.isNotEmpty() && closed.all { it.completed },
                )
            }
        }
    }

    /** 恢复历史时给"记录里没有结果的工具"补的说明（参考实现逐字）。 */
    private const val UNFINISHED_TOOL_NOTE = "会话记录未包含该工具的结果。"

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

    /**
     * 从一条消息的 content 数组里取出图片块。
     *
     * 结构由引擎写入（`ZhiCodeEngine.buildUserContent` + `buildImageBlocks`）：
     * `{"type":"image","source":{"type":"base64","media_type":…,"data":…},"name":…}`。
     *
     * 做成**接收 JSONArray 的顶层函数**而不是内联在 `transcript()` 里，是为了能用
     * 真实的 org.json 直接单测 —— 这段逻辑的失败模式（少读一张图、把非 base64 的
     * 当图）在界面上都只表现为"图没出来"，光看界面分不清是哪一种。
     *
     * 任何一块读不出来就**跳过那一块**，不影响同一条消息的其它图：一条坏数据
     * 不该让整张对话历史里的图都消失。
     */
    internal fun readImages(content: JSONArray): List<ChatImage> {
        val images = mutableListOf<ChatImage>()
        for (i in 0 until content.length()) {
            val block = content.optJSONObject(i) ?: continue
            if (block.optString("type", "") != "image") continue
            val source = block.optJSONObject("source") ?: continue
            // 只认 base64：引擎目前只写这一种；将来若支持 url，那要联网加载，
            // 与"离线也要能显示历史图片"这个前提冲突，得有单独的决定。
            if (source.optString("type", "") != "base64") continue
            val data = source.optString("data", "")
            if (data.isEmpty()) continue
            images.add(
                ChatImage(
                    data = data,
                    mimeType = source.optString("media_type", "image/png"),
                    name = block.optString("name", "").ifBlank { "图片" },
                ),
            )
        }
        return images
    }

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
