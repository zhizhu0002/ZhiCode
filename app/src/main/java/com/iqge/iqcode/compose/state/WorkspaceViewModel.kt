package com.iqge.iqcode.compose.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iqge.iqcode.compose.data.MockWorkspaceRepository
import com.iqge.iqcode.compose.data.WorkspaceRepository
import com.iqge.iqcode.compose.model.Attachment
import com.iqge.iqcode.compose.model.ChatItem
import com.iqge.iqcode.compose.model.ChatKind
import com.iqge.iqcode.compose.model.ChoiceIntent
import com.iqge.iqcode.compose.model.ChoiceOption
import com.iqge.iqcode.compose.model.ChoicePickerState
import com.iqge.iqcode.compose.model.EffortLevel
import com.iqge.iqcode.compose.model.FileEntry
import com.iqge.iqcode.compose.model.OpenFile
import com.iqge.iqcode.compose.model.PermissionMode
import com.iqge.iqcode.compose.model.PermissionRequest
import com.iqge.iqcode.compose.model.QueuedPrompt
import com.iqge.iqcode.compose.model.PlanApproval
import com.iqge.iqcode.compose.model.RiskLevel
import com.iqge.iqcode.compose.model.SLASH_COMMANDS
import com.iqge.iqcode.compose.model.SessionSummary
import com.iqge.iqcode.compose.model.SettingsCategory
import com.iqge.iqcode.compose.model.SettingsDraft
import com.iqge.iqcode.compose.model.SlashCommand
import com.iqge.iqcode.compose.model.ThemeMode
import com.iqge.iqcode.compose.model.ToolActivity
import com.iqge.iqcode.compose.model.ToolKind
import com.iqge.iqcode.compose.model.WorkspaceTab
import com.iqge.iqcode.compose.model.WorkspaceUiState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 主界面状态机。
 *
 * 数据来自 [WorkspaceRepository]；第一期用 Mock 实现模拟流式回复与工具执行，
 * 后续换成真实引擎实现即可，UI 层不用改。
 */
class WorkspaceViewModel(
    private val repo: WorkspaceRepository = MockWorkspaceRepository(),
) : ViewModel() {

    private val initialSessions = repo.sessions()
    private val _state = MutableStateFlow(
        WorkspaceUiState(
            projectName = repo.projectName(),
            projectPath = repo.projectPath(),
            sessions = initialSessions,
            activeSessionId = initialSessions.firstOrNull()?.id ?: "",
            transcript = repo.transcript(initialSessions.firstOrNull()?.id ?: ""),
            contextTokens = 41_200,
            contextWindow = 200_000,
            deviceStatus = clockLabel(),
            diff = repo.changes(),
            terminalLines = repo.terminalBanner(repo.projectPath()),
            filePath = repo.projectPath(),
            fileEntries = repo.rootFiles(),
            busySessionIds = initialSessions.filter { it.busy }.map { it.id }.toSet(),
            composerBusy = false,
            workingStatus = null,
            tasks = repo.tasks(),
            sidebarOpen = false,
        )
    )

    val state: StateFlow<WorkspaceUiState> = _state.asStateFlow()

    private var turnJob: Job? = null
    private var permissionGate: CompletableDeferred<Boolean>? = null
    private var idCounter = 0L

    private fun nextId(prefix: String): String = "$prefix-${++idCounter}"

    // ---------- 输入器 ----------

    // ---------- 任务清单 ----------

    /** 点悬浮任务卡：打开任务详情窗口，查看**全部**任务（卡片只显示当前窗口）。 */
    fun openTaskList() = _state.update { it.copy(taskListOpen = true) }

    fun closeTaskList() = _state.update { it.copy(taskListOpen = false) }

    fun onComposerChange(text: String) {        val query = if (text.startsWith("/") && !text.contains(' ') && !text.contains('\n')) text else null
        _state.update {
            it.copy(
                composerText = text,
                slashQuery = query,
                slashMatches = if (query == null) emptyList()
                else SLASH_COMMANDS.filter { c -> c.name.startsWith(query.lowercase(Locale.US)) },
            )
        }
    }

    fun pickSlashCommand(command: SlashCommand) {
        _state.update {
            it.copy(composerText = command.name + " ", slashQuery = null, slashMatches = emptyList())
        }
    }

    fun dismissSlashPalette() {
        _state.update { it.copy(slashQuery = null, slashMatches = emptyList()) }
    }

    fun addMockAttachment() {
        val index = _state.value.attachments.size + 1
        val isImage = index % 2 == 0
        _state.update {
            it.copy(
                attachments = it.attachments + Attachment(
                    id = nextId("att"),
                    label = if (isImage) "screenshot-$index.png" else "notes-$index.txt",
                    detail = if (isImage) "PNG · 截图" else "TEXT · 只读内容",
                    isImage = isImage,
                ),
                message = "已附加${if (isImage) "图片" else "文本"}（Mock）",
            )
        }
    }

    fun removeAttachment(id: String) {
        _state.update { s -> s.copy(attachments = s.attachments.filterNot { it.id == id }) }
    }

    // ---------- 发送 / 停止 ----------

    fun send() {
        val text = _state.value.composerText.trim()
        if (text.isEmpty()) return
        if (text.startsWith("/")) {
            _state.update { it.copy(composerText = "", slashQuery = null, slashMatches = emptyList()) }
            runSlashCommand(text)
            return
        }
        if (turnJob?.isActive == true) {
            // 预输入，复刻原版 sendPrompt() 的 steering 分支（MainActivity.java:1425-1450）：
            //  1) 用户气泡**立刻**进对话流（原版同样 addChatView(userItem)），这就是可见的确认；
            //  2) 清空输入框与附件；
            //  3) 工作状态文案改成「已预输入，等待当前回复完成…」（原版 showWorkingIndicator）；
            //  4) 文案按当时在执行什么分三种（原版的 wasTool / wasModel / 其它）。
            // ⚠️ 追加到**队尾**（不是覆盖）：连发多条都要保留。
            val userItem = ChatItem(
                id = nextId("u"),
                kind = ChatKind.USER,
                title = "你",
                body = text,
            )
            _state.update { s ->
                val toolsRunning = s.transcript.lastOrNull()
                    ?.tools?.any { !it.completed } == true
                val queuedNote = when {
                    s.permissionRequest != null || toolsRunning -> "已预输入：当前工具完成后加载"
                    else -> "已预输入：当前回复完成后加载"
                }
                val queue = s.pendingInputs + QueuedPrompt(userItem.id, text)
                s.copy(
                    composerText = "",
                    slashQuery = null,
                    slashMatches = emptyList(),
                    attachments = emptyList(),
                    transcript = s.transcript + userItem,
                    pendingInputs = queue,
                    workingStatus = queuedStatus(queue.size),
                    message = queuedNote,
                )
            }
            return
        }
        val userItem = ChatItem(
            id = nextId("u"),
            kind = ChatKind.USER,
            title = "你",
            body = text,
        )
        _state.update {
            it.copy(
                composerText = "",
                slashQuery = null,
                slashMatches = emptyList(),
                attachments = emptyList(),
                transcript = it.transcript + userItem,
                contextTokens = it.contextTokens + 1_100,
                composerBusy = true,
                workingStatus = "正在思考…",
                busySessionIds = it.busySessionIds + it.activeSessionId,
            )
        }
        turnJob = viewModelScope.launch { runMockTurn(text) }
    }

    fun stop() {
        turnJob?.cancel()
        turnJob = null
        permissionGate?.complete(false)
        permissionGate = null
        _state.update {
            // 停止时把还在排队的预输入**还原回输入框**（拼成多行逐条显示），
            // 而不是直接丢弃 —— 否则连发多条再按停止就会漏消息。
            val restored = it.pendingInputs.joinToString("\n") { q -> q.text }
            it.copy(
                composerBusy = false,
                workingStatus = null,
                permissionRequest = null,
                busySessionIds = it.busySessionIds - it.activeSessionId,
                composerText = if (restored.isNotEmpty()) restored else it.composerText,
                pendingInputs = emptyList(),
                message = if (it.pendingInputs.isNotEmpty()) {
                    "已停止当前任务，${it.pendingInputs.size} 条预输入已放回输入框"
                } else {
                    "已停止当前任务"
                },
            )
        }
    }

    private suspend fun runMockTurn(prompt: String) {
        delay(320)
        val reply = repo.assistantReply(prompt)
        val assistantId = nextId("a")
        val builder = StringBuilder()
        var inserted = false
        for (chunk in reply) {
            delay(90)
            builder.append(chunk)
            val body = builder.toString()
            _state.update { s ->
                val item = ChatItem(
                    id = assistantId,
                    kind = ChatKind.ASSISTANT,
                    title = "IQ",
                    body = body,
                    streaming = true,
                    processSteps = listOf("开始分析请求", "正在生成回复"),
                )
                s.copy(
                    transcript = if (inserted) s.transcript.map { if (it.id == assistantId) item else it }
                    else s.transcript + item,
                    workingStatus = "正在回复…",
                    contextTokens = s.contextTokens + 45,
                )
            }
            inserted = true
        }

        val tools = repo.toolSequence(prompt)
        val groupId = nextId("g")
        val activities = tools.mapIndexed { index, run ->
            ToolActivity(
                id = nextId("t$index"),
                toolName = run.toolName,
                displayName = run.displayName,
                summary = run.summary,
                output = run.output,
                additions = run.additions,
                deletions = run.deletions,
                kind = kindOf(run.toolName),
            )
        }
        _state.update { s ->
            val finalized = s.transcript.map {
                if (it.id == assistantId) it.copy(
                    streaming = false,
                    thinking = "先确认工具链与组件签名，再逐块重写界面。",
                    processSteps = listOf("开始分析请求", "完成回复"),
                ) else it
            }
            s.copy(
                transcript = finalized + ChatItem(
                    id = groupId,
                    kind = ChatKind.TOOL_GROUP,
                    groupLabel = groupLabel(activities),
                    tools = activities,
                ),
            )
        }

        for ((index, activity) in activities.withIndex()) {
            delay(150)
            if (kindOf(activity.toolName) == ToolKind.EDIT && _state.value.permissionMode == PermissionMode.ASK) {
                val allowed = requestPermission(activity)
                if (!allowed) {
                    markTool(activity.copy(failed = true, completed = true, output = "用户拒绝了这次调用。"))
                    continue
                }
            }
            _state.update { s ->
                s.copy(
                    workingStatus = "正在执行 ${activity.displayName}…",
                    transcript = s.transcript.map { item ->
                        if (item.id != groupId) item
                        else item.copy(tools = item.tools.map { if (it.id == activity.id) it.copy(elapsedMs = tools[index].elapsedMs) else it })
                    },
                )
            }
            delay(600)
            val finished = activity.copy(
                completed = index < activities.lastIndex,
                elapsedMs = tools[index].elapsedMs,
                failed = tools[index].exitCode != 0,
            )
            _state.update { s ->
                s.copy(
                    transcript = s.transcript.map { item ->
                        if (item.id != groupId) item
                        else item.copy(
                            tools = item.tools.map { if (it.id == activity.id) finished else it },
                            groupCompleted = index == activities.lastIndex,
                        )
                    },
                )
            }
        }

        delay(240)
        // 出队一条（FIFO），其余继续排队 —— 连发多条时逐条执行，不会漏
        val next = _state.value.pendingInputs.firstOrNull()
        _state.update {
            val rest = it.pendingInputs.drop(1)
            it.copy(
                composerBusy = false,
                // 复刻原版 handleQueuedPromptApplied()：预输入被加载时状态改为
                // 「预输入已加载，正在继续…」（MainActivity.java:4427）
                workingStatus = if (next != null) "预输入已加载，正在继续…" else null,
                contextTokens = it.contextTokens + 3_600,
                busySessionIds = it.busySessionIds - it.activeSessionId,
                pendingInputs = rest,
                message = if (next != null) {
                    if (rest.isEmpty()) "预输入已加载，正在继续" else "预输入已加载，还剩 ${rest.size} 条"
                } else {
                    "本轮任务完成"
                },
                sessions = it.sessions.map { s ->
                    if (s.id == it.activeSessionId) s.copy(
                        messageCount = s.messageCount + 2,
                        busy = false,
                        updatedAtLabel = "刚刚",
                    ) else s
                },
            )
        }

        // 复刻原版：预输入在当前回合结束后接着跑。
        // 用户气泡在排队那一刻就已插入，所以这里只跑 assistant 回合，不再补用户消息。
        // 递归链：每条执行完都会再取队首，直到队列空。
        if (next != null) {
            // ⚠️ 必须同时把 composerBusy 置回 true：续跑的回合同样"在忙"，
            // 否则输入器会回到空闲态（暂停键消失、发送键可点）。
            _state.update { s -> s.copy(composerBusy = true) }
            turnJob = viewModelScope.launch { runMockTurn(next.text) }
        }
    }

    /** 排队状态文案；多条时把条数显示出来，避免"发了没反应"的错觉。 */
    private fun queuedStatus(count: Int): String =
        if (count <= 1) "已预输入，等待当前回复完成…" else "已预输入 $count 条，等待当前回复完成…"

    private suspend fun requestPermission(activity: ToolActivity): Boolean {
        val gate = CompletableDeferred<Boolean>()
        permissionGate = gate
        _state.update {
            it.copy(
                permissionRequest = PermissionRequest(
                    id = activity.id,
                    tool = activity.toolName,
                    subtitle = subtitleFor(activity.toolName),
                    detail = activity.summary,
                    riskLevel = if (activity.toolName == "Bash") RiskLevel.HIGH else RiskLevel.NORMAL,
                )
            )
        }
        val result = gate.await()
        permissionGate = null
        _state.update { it.copy(permissionRequest = null) }
        return result
    }

    fun resolvePermission(allow: Boolean, alwaysAllow: Boolean = false) {
        if (alwaysAllow) {
            _state.update { it.copy(permissionMode = PermissionMode.ACCEPT_EDITS, message = "已切换为自动编辑") }
        }
        permissionGate?.complete(allow)
    }

    private fun markTool(activity: ToolActivity) {
        _state.update { s ->
            s.copy(
                transcript = s.transcript.map { item ->
                    if (item.kind != ChatKind.TOOL_GROUP) item
                    else item.copy(tools = item.tools.map { if (it.id == activity.id) activity else it })
                }
            )
        }
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

    private fun subtitleFor(tool: String): String = when (tool) {
        "Root" -> "高风险：IQ Code 请求以 Android uid 0 执行系统命令"
        "Bash" -> "IQ Code 请求在本地 Termux 环境中执行以下命令"
        "AndroidIntent" -> "IQ Code 请求通过 Android 应用进程打开手机 App、网页或系统页面"
        "Write", "Edit", "MultiEdit" -> "IQ Code 请求修改项目中的文件"
        "Delete" -> "IQ Code 请求删除项目中的文件或目录"
        else -> "IQ Code 请求调用工具 $tool"
    }

    // ---------- 斜杠命令 ----------

    private fun runSlashCommand(raw: String) {
        val command = raw.substringBefore(' ').lowercase(Locale.US)
        val arg = raw.substringAfter(' ', "").trim()
        when (command) {
            "/clear", "/new" -> newSession()
            "/terminal" -> selectTab(WorkspaceTab.TERMINAL)
            "/changes", "/diff" -> selectTab(WorkspaceTab.CHANGES)
            "/files" -> selectTab(WorkspaceTab.FILES)
            "/plan" -> enterPlanMode(arg)
            "/permissions" -> showPermissionPicker()
            "/effort" -> showEffortPicker()
            "/model" -> showModelPicker()
            "/cancel" -> stop()
            "/usage", "/stats", "/status" -> appendInfo(
                "会话状态",
                buildString {
                    append("模型：").append(_state.value.modelLabel).append('\n')
                    append("项目：").append(_state.value.projectName).append('\n')
                    append("上下文：").append(formatTokens(_state.value.contextTokens))
                    append(" / ").append(formatTokens(_state.value.contextWindow)).append('\n')
                    append("权限：").append(_state.value.permissionMode.label).append('\n')
                    append("推理强度：").append(_state.value.effort.label)
                },
            )
            "/help" -> appendInfo(
                "全部指令",
                SLASH_COMMANDS.joinToString("\n") { "` ${it.name} ` — ${it.hint}" },
            )
            "/compact" -> appendInfo("上下文压缩", "已请求模型整理上下文（Mock，不会真正压缩）。")
            "/config" -> openSettings()
            else -> appendInfo("指令", "已执行 `$command`（Mock），$arg".trim())
        }
    }

    /**
     * `/plan` 进入计划模式。
     *
     * 参考原版流程：先弹「计划目标」窗口问清楚要做哪件事，再产出计划。
     * 直接把计划推给用户（旧行为）会跳过"目标澄清"这一步。
     */
    private fun enterPlanMode(arg: String) {
        if (arg == "off" || arg == "exit") {
            _state.update { it.copy(planApproval = null, message = "已退出计划模式") }
            return
        }
        if (arg.isNotEmpty()) {
            // 已经带了目标，直接产出计划
            producePlan(goal = arg)
            return
        }
        _state.update {
            it.copy(
                choicePicker = ChoicePickerState(
                    title = "计划目标",
                    intent = ChoiceIntent.PLAN_GOAL,
                    prompt = "计划窗口已打开（只读模式）。你想让我为哪个目标制定实施计划？",
                    allowFreeForm = true,
                    freeFormHint = "其他回答…",
                    options = listOf(
                        ChoiceOption(
                            label = "IQ-Code-Compose 主界面收尾",
                            detail = "围绕 projects/IQ-Code-Compose 继续开发：侧栏布局、WindowDialog、" +
                                "Markdown 渲染与发布收尾。",
                        ),
                        ChoiceOption(
                            label = "Miuix 组件适配 / 移植",
                            detail = "围绕 Miuix 相关工程继续适配 Card / Surface / WindowDialog 等组件。",
                        ),
                        ChoiceOption(
                            label = "ControlLayoutConverter 相关工作",
                            detail = "围绕 projects/ControlLayoutConverter（或 ControlConverter-v0.3）" +
                                "继续开发、修复或发版。",
                        ),
                        ChoiceOption(
                            label = "你先说清楚要做什么",
                            detail = "我描述具体需求（例如新功能、修 bug、性能优化、打包发版），" +
                                "你再据此写计划。",
                        ),
                    ),
                )
            )
        }
    }

    /** 真正产出计划（Markdown 正文由 `ui/Markdown.kt` 渲染）。 */
    private fun producePlan(goal: String) {
        val revision = 3
        _state.update {
            it.copy(
                choicePicker = null,
                planApproval = PlanApproval(
                    id = nextId("plan"),
                    title = "实现计划",
                    revision = revision,
                    path = "/data/user/0/com.iqge/files/home/.iq/projects/" +
                        "-data-user-0-com-iqge-files-home-d869025f44cb/plans/" +
                        "43db878b-665c-4003-b41f-b8a8dab74de2.md",
                    body = buildString {
                        append("## 说明\n\n")
                        append("目标：").append(goal).append("\n\n")
                        append("你没有指定更细的约束，所以我按当前活跃项目的真实状态给出一个可执行计划。")
                        append("以下都是我在只读探查中实际观察到的：\n\n")
                        append("- 活跃工程：`projects/IQ-Code-Compose`（Compose + Miuix 重写的主界面）\n")
                        append("- 工具链：Gradle **9.3.1** / AGP **9.1.1** / Kotlin **2.4.0**，")
                        append("Miuix `0.9.4`\n")
                        append("- 产物：`app/build/outputs/apk/debug/IQCodeCompose-debug.apk`，")
                        append("`versionCode 1` / `versionName 0.1`\n")
                        append("- 手写绘制站点已从 **78** 降到 **9**，其余交给 Miuix `Card` / `Surface`\n\n")
                        append("## 待办\n\n")
                        append("1. 侧栏：去掉搜索、会话区占满、工作区移到底部\n")
                        append("2. 「选择」与「提交计划」窗口改用 `WindowDialog`\n")
                        append("3. Markdown 渲染：标题 / 粗体 / 行内代码 / 列表 / 代码块\n\n")
                        append("```\n")
                        append("./gradlew :app:assembleDebug\n")
                        append("BUILD SUCCESSFUL in 43s\n")
                        append("```\n\n")
                        append("计划模式无法执行 Bash，因此尚未做任何文件改动、没有跑构建。")
                    },
                    permissionNote = "批准只确认计划；后续操作仍按你的权限模式：${it.permissionMode.label}",
                ),
            )
        }
    }

    fun resolvePlan(approved: Boolean) {
        val plan = _state.value.planApproval ?: return
        appendInfo(
            if (approved) "计划已批准" else "计划已退回",
            if (approved) "开始执行：${plan.title}" else "等待修改后的计划。",
        )
        _state.update { it.copy(planApproval = null) }
    }

    /** 「继续规划」并带上用户填写的反馈。 */
    fun resolvePlanWithFeedback(feedback: String) {
        if (feedback.isBlank()) {
            resolvePlan(approved = false)
            return
        }
        appendInfo("已提交反馈", feedback.trim())
        _state.update { it.copy(planApproval = null) }
    }

    private fun appendInfo(title: String, body: String) {
        _state.update {
            it.copy(transcript = it.transcript + ChatItem(id = nextId("i"), kind = ChatKind.INFO, title = title, body = body))
        }
    }

    // ---------- 选择器 ----------

    fun showPermissionPicker() {
        val values = PermissionMode.entries
        _state.update {
            it.copy(
                choicePicker = ChoicePickerState(
                    title = "权限模式",
                    intent = ChoiceIntent.PERMISSION_MODE,
                    options = values.map { mode ->
                        ChoiceOption(
                            label = mode.label,
                            detail = mode.detail,
                            checked = mode == it.permissionMode,
                        )
                    },
                )
            )
        }
    }

    fun showEffortPicker() {
        val values = EffortLevel.entries
        _state.update {
            it.copy(
                choicePicker = ChoicePickerState(
                    title = "推理强度",
                    intent = ChoiceIntent.EFFORT,
                    options = values.map { level -> ChoiceOption(level.label, checked = level == it.effort) },
                )
            )
        }
    }

    fun showModelPicker() {
        val models = listOf(
            "IQ-Code-2.0-preview" to "默认 · 200k 上下文",
            "IQ-Code-2.0-fast" to "低延迟 · 128k 上下文",
            "IQ-Code-2.0-max" to "高推理 · 1m 上下文",
        )
        _state.update {
            it.copy(
                choicePicker = ChoicePickerState(
                    title = "选择模型",
                    intent = ChoiceIntent.MODEL,
                    options = models.map { (id, detail) -> ChoiceOption(id, detail, id == it.modelLabel) },
                )
            )
        }
    }

    fun showMessageActions(item: ChatItem) {
        val options = when (item.kind) {
            ChatKind.USER -> listOf("复制", "再次发送", "编辑后发送")
            ChatKind.ASSISTANT, ChatKind.ERROR -> listOf("复制", "重试上一问")
            else -> listOf("复制", if (item.groupCompleted) "已全部完成" else "执行中")
        }
        _state.update {
            it.copy(
                choicePicker = ChoicePickerState(
                    title = "消息操作",
                    intent = ChoiceIntent.MESSAGE_ACTION,
                    options = options.map { ChoiceOption(it) },
                )
            )
        }
    }

    fun showSessionActions(session: SessionSummary) {
        val options = if (session.note.isEmpty()) listOf("编辑备注", "恢复会话", "删除会话")
        else listOf("编辑备注", "清除备注", "恢复会话", "删除会话")
        _state.update {
            it.copy(
                choicePicker = ChoicePickerState(
                    title = session.title,
                    intent = ChoiceIntent.SESSION_ACTION,
                    options = options.map { ChoiceOption(it) },
                )
            )
        }
    }

    fun dismissChoicePicker() {
        _state.update { it.copy(choicePicker = null) }
    }

    fun onChoiceSelected(index: Int) {
        val picker = _state.value.choicePicker ?: return
        val option = picker.options.getOrNull(index) ?: return
        when (picker.intent) {
            ChoiceIntent.PERMISSION_MODE -> {
                val mode = PermissionMode.entries.getOrNull(index)
                if (mode != null) _state.update {
                    it.copy(choicePicker = null, permissionMode = mode, message = "权限模式：${mode.label}")
                } else _state.update { it.copy(choicePicker = null) }
            }
            ChoiceIntent.EFFORT -> {
                val level = EffortLevel.entries.getOrNull(index)
                if (level != null) _state.update {
                    it.copy(choicePicker = null, effort = level, message = "推理强度：${level.label}")
                } else _state.update { it.copy(choicePicker = null) }
            }
            ChoiceIntent.MODEL -> _state.update {
                it.copy(choicePicker = null, modelLabel = option.label, message = "已切换模型：${option.label}")
            }
            ChoiceIntent.MESSAGE_ACTION -> _state.update {
                it.copy(choicePicker = null, message = "${option.label}（Mock）")
            }
            ChoiceIntent.SESSION_ACTION -> {
                _state.update { it.copy(choicePicker = null) }
                when (option.label) {
                    "删除会话" -> deleteSession(_state.value.activeSessionId)
                    "恢复会话" -> _state.update { it.copy(sidebarOpen = false, message = "已恢复会话（Mock）") }
                    else -> _state.update { it.copy(message = "${option.label}（Mock）") }
                }
            }
            ChoiceIntent.GENERIC, ChoiceIntent.ATTACH -> _state.update {
                it.copy(choicePicker = null, message = "${option.label}（Mock）")
            }
            // 目标选定后才产出计划；加上 detail 让计划正文里能看到完整目标
            ChoiceIntent.PLAN_GOAL -> producePlan(
                goal = if (option.detail.isNotEmpty()) "${option.label} —— ${option.detail}" else option.label,
            )
        }
    }

    /**
     * 选择窗口里的「其他回答…」提交：不走选项，直接把这段自由文本当作输入。
     * 在计划模式下它等价于"自定义目标"，会直接产出计划。
     */
    fun onSubmitFreeForm(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            dismissChoicePicker()
            return
        }
        val picker = _state.value.choicePicker
        if (picker?.intent == ChoiceIntent.PLAN_GOAL) {
            producePlan(goal = trimmed)
            return
        }
        _state.update {
            it.copy(
                choicePicker = null,
                composerText = trimmed,
                slashQuery = null,
                slashMatches = emptyList(),
                message = null,
            )
        }
    }

    // ---------- 会话 ----------

    fun newSession() {
        val id = nextId("s")
        val summary = SessionSummary(
            id = id,
            title = "新会话",
            project = _state.value.projectName,
            messageCount = 0,
            updatedAtLabel = "刚刚",
        )
        _state.update {
            it.copy(
                sessions = listOf(summary) + it.sessions,
                activeSessionId = id,
                transcript = emptyList(),
                contextTokens = 0,
                composerBusy = false,
                workingStatus = null,
                tab = WorkspaceTab.CHAT,
                sidebarOpen = false,
                message = "已创建新会话",
            )
        }
    }

    fun openSession(session: SessionSummary) {
        turnJob?.cancel()
        turnJob = null
        _state.update {
            it.copy(
                activeSessionId = session.id,
                transcript = repo.transcript(session.id),
                sidebarOpen = false,
                tab = WorkspaceTab.CHAT,
                composerBusy = session.busy,
                workingStatus = if (session.busy) "正在执行工具…" else null,
                contextTokens = 28_400,
            )
        }
    }

    fun deleteSession(id: String) {
        val remaining = _state.value.sessions.filterNot { it.id == id }
        _state.update {
            it.copy(
                sessions = remaining,
                activeSessionId = if (it.activeSessionId == id) (remaining.firstOrNull()?.id ?: "") else it.activeSessionId,
                transcript = if (it.activeSessionId == id) repo.transcript(remaining.firstOrNull()?.id ?: "") else it.transcript,
                message = "会话已删除",
            )
        }
    }

    fun openSidebar() = _state.update { it.copy(sidebarOpen = true) }
    fun closeSidebar() = _state.update { it.copy(sidebarOpen = false) }

    // ---------- 工作区 ----------

    fun selectTab(tab: WorkspaceTab) = _state.update { it.copy(tab = tab) }

    /**
     * 重新拉一次变更列表（变更面板的「刷新」）。
     *
     * 对应原版 `refreshChanges()`（重跑 `git status --short` + `git diff`）。
     * 这里数据来自 [repo]，所以"刷新"等价于重新读一次。
     */
    fun refreshDiff() {
        _state.update { it.copy(diff = repo.changes(), message = "已刷新变更列表") }
    }

    fun cycleThemeMode() = _state.update {
        val next = when (it.themeMode) {
            ThemeMode.SYSTEM -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.SYSTEM
        }
        it.copy(themeMode = next, message = "主题：${next.label}")
    }

    // ---------- 设置 ----------

    /**
     * 打开设置弹窗：从当前 state 快照一份 draft。
     *
     * 之后的编辑全部落在 draft 上，「取消」丢弃 draft 即可，不需要回滚任何东西。
     */
    fun openSettings() = _state.update {
        it.copy(settingsDraft = SettingsDraft.from(it))
    }

    fun closeSettings() = _state.update { it.copy(settingsDraft = null) }

    /** 弹窗内任意一项改动都走这里，保证 draft 只有一份写入路径。 */
    fun updateSettingsDraft(transform: (SettingsDraft) -> SettingsDraft) = _state.update {
        val draft = it.settingsDraft ?: return@update it
        it.copy(settingsDraft = transform(draft))
    }

    /** 设置弹窗直接回传整份 draft 时的入口（等价于 [updateSettingsDraft] 忽略旧值）。 */
    fun setSettingsDraft(draft: SettingsDraft) = _state.update { it.copy(settingsDraft = draft) }

    /** 「保存」：唯一把 draft 写回 state 的入口，并关闭弹窗。 */
    fun saveSettings() = _state.update {
        val draft = it.settingsDraft ?: return@update it
        draft.applyTo(it).copy(message = "设置已保存")
    }

    /**
     * 分类页里的「入口类」条目（API 配置记录 / MCP / UI 画布 / 其他设置）：
     * 先关掉设置弹窗，再把对应目标落成一次指令或信息卡。
     */
    fun navigateFromSettings(target: String) {
        _state.update { it.copy(settingsDraft = null) }
        when (target) {
            // 这些还没有独立面板，先以信息卡说明现状，避免死链
            "apiProfiles" -> appendInfo(
                "API 配置记录",
                "当前使用 **${_state.value.profileName}** · ${_state.value.modelLabel}。\n" +
                    "API 地址、协议与密钥的独立保存需要真实引擎，本期尚未接入。",
            )
            "mcp" -> appendInfo("MCP 服务器", "MCP 服务器配置将在接入真实引擎后提供。")
            "canvas" -> {
                onComposerChange("/canvas")
                send()
            }
            "other" -> appendInfo(
                "其他设置",
                "自定义头部提示词已并入设置页「上下文与项目」分类，可直接在弹窗内编辑。",
            )
            else -> Unit
        }
    }

    // ---------- 对话流交互 ----------

    fun toggleToolExpanded(id: String) {
        _state.update { s ->
            s.copy(
                transcript = s.transcript.map { item ->
                    if (item.kind != ChatKind.TOOL_GROUP) item
                    else item.copy(
                        tools = item.tools.map { if (it.id == id) it.copy(expanded = !it.expanded) else it },
                    )
                },
            )
        }
    }

    /**
     * 批量设置工具组里各成员的展开状态。
     *
     * [collapseIds] 是**要收起的成员 id 集合**：集合内的成员收起，其余展开。
     * 因此"全部展开"传空集，"全部收起"传全部成员 id。
     */
    fun toggleGroupExpanded(id: String, collapseIds: Set<String>) {
        _state.update { s ->
            s.copy(
                transcript = s.transcript.map { item ->
                    if (item.id != id || item.kind != ChatKind.TOOL_GROUP) item
                    else item.copy(tools = item.tools.map { it.copy(expanded = it.id !in collapseIds) })
                },
            )
        }
    }

    fun toggleThinking(id: String) {
        _state.update { s ->
            s.copy(
                transcript = s.transcript.map { if (it.id == id) it.copy(thinkingExpanded = !it.thinkingExpanded) else it },
            )
        }
    }

    // ---------- 文件 ----------

    fun openDirectory(entry: FileEntry) {
        navigateTo(entry.path)
    }

    /** 面包屑点击等场景：直接跳到任意层级目录。 */
    fun navigateTo(path: String) {
        _state.update { it.copy(filePath = path, fileEntries = repo.childrenOf(path), openFile = null) }
    }

    fun navigateUp() {
        val current = _state.value.filePath
        if (current == repo.projectPath()) return
        val parent = current.substringBeforeLast('/', "")
        if (parent.isEmpty()) return
        navigateTo(parent)
    }

    fun openFile(entry: FileEntry) {
        if (entry.directory) {
            openDirectory(entry)
            return
        }
        val file: OpenFile = repo.readFile(entry.path)
        _state.update { it.copy(openFile = file, message = "已打开 ${entry.name}（只读）") }
    }

    fun closeFile() = _state.update { it.copy(openFile = null) }

    // ---------- 操作反馈 ----------
    //
    // 原先这里由 Snackbar 消费 message，但浮层会遮挡底部输入器，已移除。
    // state.message 继续记录最后一次操作结果（"已恢复会话（Mock）"等），
    // 留作将来接内联提示的缓冲；不接也不会渲染出任何东西。

    companion object {
        fun clockLabel(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

        fun formatTokens(value: Int): String = when {
            value >= 1_000_000 -> trimZero(value / 1_000_000f) + "m"
            value >= 1_000 -> trimZero(value / 1_000f) + "k"
            else -> "$value"
        }

        /** 200.0k → 200k，省出顶栏 chip 的横向空间。 */
        private fun trimZero(value: Float): String =
            if (value >= 100f || value % 1f == 0f) String.format(Locale.US, "%.0f", value)
            else String.format(Locale.US, "%.1f", value)
    }
}
