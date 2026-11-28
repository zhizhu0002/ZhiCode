package com.zhizhu.zhicode.compose.data

import com.zhizhu.zhicode.compose.engine.ToolText
import com.zhizhu.zhicode.compose.model.AgentTask
import com.zhizhu.zhicode.compose.model.ChatItem
import com.zhizhu.zhicode.compose.model.ChatKind
import com.zhizhu.zhicode.compose.model.ImageOrigin
import com.zhizhu.zhicode.compose.model.SessionSummary
import com.zhizhu.zhicode.compose.model.TaskState
import com.zhizhu.zhicode.compose.model.ToolActivity
import com.zhizhu.zhicode.compose.model.ToolKind
import com.zhizhu.zhicode.compose.model.readChatImageBlocks
import com.termux.app.zhicode.storage.SessionStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 把库里的真实会话读成界面模型。 */
internal object SessionReader {

    /** 一次读进内存的消息条数（首屏与每次「加载更早」都用它）。 */
    const val PAGE_MESSAGES = 100

    /** 一页内容。 */
    class Page(
        val items: List<ChatItem>,
        /** 任务清单只在打开会话时取一次（它取的是全库最后一条快照）。 */
        val tasks: List<AgentTask>,
        /** 本页最早一行的 seq；<= 0 表示没有更早的内容。翻上一页时把它当上界。 */
        val oldestSeq: Long,
        val hasMore: Boolean,
    )

    /** 打开一条会话：只读**最近**一页 + 完整任务清单。 */
    fun openPage(sessionId: String): Page {
        val window = runCatching { SessionStore.readRowsWindow(sessionId, -1L, PAGE_MESSAGES) }
            .getOrElse { failure ->
                logIfOom(failure)
                return Page(emptyList(), emptyList(), -1L, false)
            }
        return Page(
            items = transcriptOf(sessionId, toRows(window.rows)),
            tasks = tasks(sessionId),
            oldestSeq = window.oldestSeq,
            hasMore = window.hasMore,
        )
    }

    fun olderPage(sessionId: String, beforeSeq: Long): Page? {
        if (beforeSeq <= 0L) return null
        val window = runCatching { SessionStore.readRowsWindow(sessionId, beforeSeq, PAGE_MESSAGES) }
            .getOrElse { failure ->
                logIfOom(failure)
                return null
            }
        if (window.rows.length() == 0) return null
        return Page(
            items = transcriptOf(sessionId, toRows(window.rows)),
            tasks = emptyList(),
            oldestSeq = window.oldestSeq,
            hasMore = window.hasMore,
        )
    }

    /** 把两页接起来。 */
    fun merge(older: List<ChatItem>, newer: List<ChatItem>): List<ChatItem> = older + newer

    /** 侧栏单项目列表。按最近活动倒序。 */
    fun list(projectDirectory: String): List<SessionSummary> {
        val raw = runCatching { SessionStore.listSessions(projectDirectory) }.getOrDefault(emptyList())
        return raw.sortedByDescending { it.activityModifiedAt }.map { it.toUi() }
    }

    /** 侧栏全量工作区列表（跨全部项目）。按最近活动倒序。 */
    fun listAll(): List<SessionSummary> {
        val raw = runCatching { SessionStore.listSessions() }.getOrDefault(emptyList())
        return raw.sortedByDescending { it.activityModifiedAt }.map { it.toUi() }
    }

    /** JSON 数组 → 行列表：读不出来就返回空，绝不让界面崩。 */
    private fun toRows(array: JSONArray): List<JSONObject> =
        (0 until array.length()).mapNotNull { array.optJSONObject(it) }

    /** 把被吞掉的 `OutOfMemoryError` 记下来。 */
    private fun logIfOom(failure: Throwable) {
        if (failure !is OutOfMemoryError) return
        runCatching { android.util.Log.e("ZhiCodeSession", "读取会话时内存不足", failure) }
    }

    /** 任务清单。 */
    private fun tasks(sessionId: String): List<AgentTask> {
        val rows = toRows(
            runCatching { SessionStore.readTaskSnapshotRows(sessionId) }.getOrElse { failure ->
                logIfOom(failure)
                return emptyList()
            },
        )
        return tasksOf(rows)
    }

    private fun transcriptOf(sessionId: String, rows: List<JSONObject>): List<ChatItem> {
        val items = mutableListOf<ChatItem>()
        fun id(prefix: String, rowSeq: Long) = "session-$prefix-$rowSeq"

        /** 工具 id → 它所在分组卡的下标。用于把结果/进度写回正确的卡。 */
        val toolGroupIndex = mutableMapOf<String, Int>()
        /** tool_use_id → 这次改动的增删行数与 diff 正文（来自 `tool_diff` 事件）。 */
        val toolDiffs = mutableMapOf<String, ToolDiffInfo>()

        for (row in rows) {
            val rowSeq = row.optLong("seq", -1L)
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
                                    id = id("a", rowSeq),
                                    kind = ChatKind.ASSISTANT,
                                    title = "ZhiCode",
                                    body = body.toString(),
                                    // 思考内容与实时流同一个上限：它**默认是折叠的**，
                                    // 截断不改变屏幕上看得见的内容，但能挡住"一条回复
                                    // 带几十万字的思考"长期占住堆。见 THINKING_PREVIEW_CHARS。
                                    thinking = previewOf(thinking.toString(), THINKING_PREVIEW_CHARS),
                                ),
                            )
                        }
                        if (toolUses.isNotEmpty()) {
                            val groupIndex = items.size
                            items.add(ChatItem(id = id("g", rowSeq), kind = ChatKind.TOOL_GROUP))
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
                                    // 折叠组副行/表头要用的两个字段。历史记录里
                                    // 原始入参还在（存在 chunk 的 input 里），所以
                                    // 从历史恢复出来的组也能算出正确的"读取 N 个文件"。
                                    hint = ToolText.activityHint(name, input),
                                    readRequests = ToolText.readRequestCount(name, input),
                                )
                                if (toolId.isNotEmpty()) toolGroupIndex[toolId] = groupIndex
                                val group = items[groupIndex]
                                val tools = group.tools + activity
                                items[groupIndex] = group.copy(tools = tools)
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
                            // 只留一份有界预览：完整输出可能要几 MB，而一次会话里
                            // 可能有几百条工具 —— 全塞进 transcript 等于把整个会话的
                            // 工具输出常驻内存。展开某一条时再由界面按 id 取全文
                            //（SessionStore.readToolResult）。
                            val fullOutput = block.optString("content", "")
                            items[index] = group.copy(
                                tools = group.tools.map { tool ->
                                    if (tool.id != toolId) tool
                                    else tool.copy(
                                        completed = true,
                                        failed = block.optBoolean("is_error", false),
                                        output = previewOf(fullOutput),
                                        outputTruncated = fullOutput.length > OUTPUT_PREVIEW_CHARS,
                                        additions = diff?.additions ?: tool.additions,
                                        deletions = diff?.deletions ?: tool.deletions,
                                        // diff 正文与增删行数同源（同一个 `tool_diff` 事件）。
                                        // 只取行数不取正文，等于恢复历史后那块 diff 预览井是空的。
                                        // 它同样可以是几 MB 的统一 diff（一次大重构），
                                        // 所以与 output 用同一套有界预览规则。
                                        diff = previewOf(diff?.text ?: tool.diff),
                                    )
                                },
                            )
                        }
                        // 图片：引擎把用户发的图作为 image 块写在同一条 user 消息里
                        // （见 ZhiCodeEngine.buildUserContent），这里读回来挂到气泡上。
                        //
                        // ⚠️ 只带**位置**，不带字节。窗口化读历史时存储层本来就没解码图片块
                        // （见 `SessionStore.loadBlocks`），而就算带了字节也不该在这里常驻：
                        // 一条长会话的图片 base64 是几十 MB 量级，屏幕上一次只有几张可见。
                        // 界面画到哪一张就取哪一张（`SessionReader.imageData`）。
                        // 这里再兜一道 `data = ""`，让"字节不常驻"这件事不依赖存储层的实现细节。
                        val images = readChatImageBlocks(content) { i ->
                            if (rowSeq < 0L) null else ImageOrigin(sessionId, rowSeq, i)
                        }.map { if (it.origin == null) it else it.copy(data = "") }
                        // 工具结果行**不算发言**。只有"有内容且来源不是内部"才是用户说的。
                        //
                        // ⚠️ 判据必须包含 images：只发图、不写字的消息没有 text 块，
                        // 早先按 `text.isNotBlank()` 判断会把这条消息**整个丢掉** ——
                        // 用户回头翻历史，会发现自己发的那张图连带那条消息都不存在。
                        val origin = row.optString("origin", "")
                        if (!hasToolResult && (text.isNotBlank() || images.isNotEmpty()) && origin != "internal") {
                            items.add(
                                ChatItem(
                                    id = id("u", rowSeq),
                                    kind = ChatKind.USER,
                                    title = "你",
                                    body = text.toString(),
                                    images = images,
                                ),
                            )
                        }
                    }
                }
                "context_compaction" -> {
                    val payload = row.optJSONObject("payload")
                    val kept = payload?.optInt("kept_messages", -1) ?: -1
                    val automatic = payload?.optBoolean("automatic", false) == true
                    items.add(
                        ChatItem(
                            id = id("c", rowSeq),
                            kind = ChatKind.INFO,
                            title = "上下文压缩",
                            body = buildString {
                                append(if (automatic) "自动压缩了较早的上下文。" else "手动压缩了较早的上下文。")
                                if (kept >= 0) append("保留最近 ").append(kept).append(" 条。")
                                append("此前的详细对话已被摘要替代。")
                            },
                        ),
                    )
                }
                "tool_diff" -> {
                    // 增删行数单独落在事件里，工具结果本身不带。先收集，等结果行到达时用。
                    val payload = row.optJSONObject("payload") ?: continue
                    val toolId = payload.optString("tool_use_id", "")
                    if (toolId.isNotEmpty()) {
                        toolDiffs[toolId] = ToolDiffInfo(
                            additions = payload.optInt("additions", 0),
                            deletions = payload.optInt("deletions", 0),
                            text = payload.optString("diff", ""),
                        )
                    }
                }
                "turn_elapsed" -> {
                    val elapsed = row.optJSONObject("payload")?.optLong("elapsed_ms", -1L) ?: -1L
                    if (elapsed > 0L) {
                        for (i in items.lastIndex downTo 0) {
                            val item = items[i]
                            if (item.kind == ChatKind.USER) break
                            if (item.kind == ChatKind.ASSISTANT) {
                                items[i] = item.copy(elapsedMs = elapsed)
                                break
                            }
                        }
                    }
                }
            }
        }
        return closeUnfinished(items)
    }

    /** 给**没有结果的**工具收口，并标出整组是否都已完成。 */
    private fun closeUnfinished(items: List<ChatItem>): List<ChatItem> = items.map { item ->
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

    /** 恢复历史时给"记录里没有结果的工具"补的说明（参考实现逐字）。 */
    private const val UNFINISHED_TOOL_NOTE = "会话记录未包含该工具的结果。"

    /** 工具输出在 transcript 里保留的字符数上限。 */
    private const val OUTPUT_PREVIEW_CHARS = 8 * 1024

    /** 被截断时接在末尾的提示。用省略号而不是硬切，让人知道后面还有内容。 */
    private const val OUTPUT_PREVIEW_MARKER = "\n…（输出过长，展开查看全文）"

    /** 历史里思考内容的字符上限。 */
    private const val THINKING_PREVIEW_CHARS = 1200

    /** 取输出的有界预览；没超限时原样返回，不做任何拷贝。 */
    private fun previewOf(output: String): String =
        if (output.length <= OUTPUT_PREVIEW_CHARS) output
        else output.substring(0, OUTPUT_PREVIEW_CHARS) + OUTPUT_PREVIEW_MARKER

    /** 按给定上限取预览（思考内容用它，上限与输出不同）。 */
    private fun previewOf(text: String, limit: Int): String =
        if (text.length <= limit) text else text.takeLast(limit)

    /** 任务清单：取最后一条 `task_snapshot` 事件（引擎在每次任务变更时都会追加）。 */
    private fun tasksOf(rows: List<JSONObject>): List<AgentTask> {
        var latest: List<AgentTask> = emptyList()
        for (row in rows) {
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

    fun delete(sessionId: String): Boolean =
        runCatching { SessionStore.deleteSession(sessionId) }.getOrDefault(false)

    /** 删除一条历史消息（界面上那一条）。 */
    fun deleteMessage(sessionId: String, chatItemId: String): Result<Unit> = runCatching {
        val removed = SessionStore.deleteMessageById(sessionId, chatItemId)
        if (removed <= 0) throw IllegalStateException("这条消息在库里已经不存在了")
    }

    /** 备注 / 标题覆盖。空字符串表示清除该项。 */
    fun updateMetadata(sessionId: String, note: String, titleOverride: String): Boolean =
        runCatching { SessionStore.updateSessionMetadata(sessionId, note, titleOverride); true }
            .getOrDefault(false)

    // ------------------------------------------------------------------
    // 存储维护与归档
    //
    // 下面四个都是**重活**（读完整条会话 / 遍历文件系统 / 导入），调用方必须放在
    // IO 线程上。它们只做转发与容错，真正的实现在 storage 层。
    // ------------------------------------------------------------------

    /** 存储维护：回收孤儿大内容 + 把自由页还给文件系统。返回人可读的摘要。 */
    fun maintain(): String = runCatching { SessionStore.maintain() }
        .getOrElse { failure -> logIfOom(failure); "维护失败：" + (failure.message ?: "未知原因") }

    /** 存储占用报告，给人看的多行文本。 */
    fun storageReport(): String {
        val stats = runCatching { SessionStore.describeStorage() }.getOrElse { failure ->
            logIfOom(failure)
            return "存储信息读不出来：" + (failure.message ?: "未知原因")
        }
        fun size(key: String): String {
            val bytes = stats.optLong(key, 0L)
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024L * 1024L -> (bytes / 1024L).toString() + " KB"
                else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
            }
        }
        return buildString {
            append("会话 ").append(stats.optLong("sessions")).append(" 条")
            append(" · 事件 ").append(stats.optLong("events")).append(" 行")
            append(" · 内容块 ").append(stats.optLong("blocks")).append(" 个")
            append(" · 快照 ").append(stats.optLong("snapshots")).append(" 个")
            append('\n')
            append("数据库 ").append(size("database_bytes"))
            append(" · 大内容 ").append(size("blobs_bytes"))
            append("（").append(stats.optLong("blobs")).append(" 个文件")
            append('/').append(stats.optLong("blob_refs")).append(" 处引用）")
            append('\n')
            append("旧格式已导入 ").append(stats.optLong("migrated")).append(" 条")
            append(" · 失败 ").append(stats.optLong("migration_failures")).append(" 条")
            append('\n')
            append("数据库检查：").append(stats.optString("integrity_check", "未知"))
            append(" · 计数偏差 ").append(stats.optLong("session_count_mismatches"))
            append(" · 序号偏差 ").append(stats.optLong("sequence_mismatches"))
            append(" · 缺失大内容 ").append(stats.optLong("blob_missing_files"))
        }
    }

    /** 导出结果。[ok] 为真时 [path] 是写出来的文件。 */
    class ExportResult(val ok: Boolean, val path: String, val message: String)

    /** 把一个会话导出成自包含的 `jsonl.gz`。 */
    fun exportSession(sessionId: String, directory: File): ExportResult {
        if (sessionId.isEmpty()) return ExportResult(false, "", "没有打开的会话")
        return runCatching {
            val target = File(directory, SessionStore.suggestedArchiveName(sessionId))
            SessionStore.exportSession(sessionId, target)
            ExportResult(true, target.absolutePath, "已导出：" + target.absolutePath)
        }.getOrElse { failure ->
            logIfOom(failure)
            ExportResult(false, "", "导出失败：" + (failure.message ?: "未知原因"))
        }
    }

    /** 把一个归档导入成一条新会话。 */
    fun importSession(file: File): Pair<String?, String> = runCatching {
        val id = SessionStore.importSession(file)
        id to ("已导入会话：" + id.take(8))
    }.getOrElse { failure ->
        logIfOom(failure)
        null to ("导入失败：" + (failure.message ?: "未知原因"))
    }

    /** 取一张历史图片的字节（base64）。 */
    fun imageData(origin: ImageOrigin): String =
        runCatching {
            SessionStore.readImageData(origin.sessionId, origin.messageSeq, origin.ordinal)
        }.getOrElse { failure ->
            logIfOom(failure)
            ""
        }

    // 读图片块的那个函数**不在这里** —— 它被搬到 `model/ChatImageBlocks.kt` 了。
    //
    // 原因：同一个块结构有两个互不相关的来源（会话文件里的用户消息、以及工具结果里的
    // `additionalContent`），而后者要由 `engine` 包调用，`engine` 的既有依赖方向是
    // **只依赖 `model`**、从不 import `data`。留在 `data` 里会让引擎反向依赖数据层。
    // 现在两边共用 `readChatImageBlocks`，也共用它的单测。

    // ------------------------------------------------------------------

    private fun SessionStore.SessionSummary.toUi(): SessionSummary {
        val cleanProject = project.trimEnd('/').ifBlank { "/" }
        return SessionSummary(
            id = id,
            title = if (title.isBlank()) "新会话" else title,
            project = File(cleanProject).name,
            projectPath = cleanProject,
            messageCount = messageCount,
            updatedAtLabel = relativeTime(activityModifiedAt),
            updatedAtMs = activityModifiedAt,
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
        "Bash", "Root", "Shizuku", "BashTool" -> ToolKind.COMMAND
        else -> ToolKind.OTHER
    }

    /** 紧凑相对时间（参考 DeepSeek Harness 侧栏：`刚刚` / `5分钟` / `11小时` / `3天` / `M月d日`）。 */
    internal fun relativeTime(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        if (timestamp <= 0L) return ""
        val delta = (now - timestamp).coerceAtLeast(0L)
        if (delta < 60_000L) return "刚刚"
        if (delta < 3_600_000L) return "${delta / 60_000L}分钟"
        if (delta < 86_400_000L) return "${delta / 3_600_000L}小时"
        val days = delta / 86_400_000L
        if (days < 30L) return "${days}天"
        val today = Calendar.getInstance().apply { timeInMillis = now }
        val then = Calendar.getInstance().apply { timeInMillis = timestamp }
        if (today.get(Calendar.YEAR) == then.get(Calendar.YEAR)) {
            return SimpleDateFormat("M月d日", Locale.getDefault()).format(Date(timestamp))
        }
        return SimpleDateFormat("yyyy年M月d日", Locale.getDefault()).format(Date(timestamp))
    }
}

private class ToolDiffInfo(val additions: Int, val deletions: Int, val text: String)
