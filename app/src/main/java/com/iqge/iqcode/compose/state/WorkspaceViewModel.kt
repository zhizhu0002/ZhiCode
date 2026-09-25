package com.iqge.iqcode.compose.state

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.iqge.iqcode.compose.data.FileBrowser
import com.iqge.iqcode.compose.data.GitChanges
import com.iqge.iqcode.compose.data.MockWorkspaceRepository
import com.iqge.iqcode.compose.data.SessionReader
import com.iqge.iqcode.compose.data.WorkspaceRepository
import com.iqge.iqcode.compose.engine.EngineEvents
import com.iqge.iqcode.compose.engine.EngineOverrides
import com.iqge.iqcode.compose.engine.EnginePlanApproval
import com.iqge.iqcode.compose.engine.IqEngineController
import com.iqge.iqcode.compose.engine.ToolText
import com.iqge.iqcode.compose.engine.toEngineEffort
import com.iqge.iqcode.compose.engine.toEngineMode
import com.iqge.iqcode.compose.engine.toUiPlanApproval
import com.iqge.iqcode.compose.runtime.EnvDoctor
import com.iqge.RuntimeInstaller
import com.iqge.iqcode.compose.model.AgentTask
import com.iqge.iqcode.compose.model.Attachment
import com.iqge.iqcode.compose.model.ChatItem
import com.iqge.iqcode.compose.model.ChatKind
import com.iqge.iqcode.compose.model.ChoiceIntent
import com.iqge.iqcode.compose.model.ChoiceOption
import com.iqge.iqcode.compose.model.ChoicePickerState
import com.iqge.iqcode.compose.model.DiffState
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
import com.iqge.iqcode.compose.model.WebSearchProvider
import com.termux.app.iqcode.core.PlanApprovalGate
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 主界面状态机。
 *
 * 数据来自 [WorkspaceRepository]；第一期用 Mock 实现模拟流式回复与工具执行，
 * 后续换成真实引擎实现即可，UI 层不用改。
 *
 * 继承 [AndroidViewModel] 而不是 `ViewModel`，是为了拿到 `Application`：
 * 内置 Termux 环境的安装与自检都需要 Context。
 * 这样做还有一个实际好处 —— `viewModel()` 的默认工厂本来就支持 `AndroidViewModel`，
 * 所以 `IqCodeApp()` 里那行默认参数不用改、也不用自己写 Factory。
 */
class WorkspaceViewModel(
    application: Application,
    private val repo: WorkspaceRepository = MockWorkspaceRepository(),
) : AndroidViewModel(application), EngineEvents {

    /** 内置 Termux 环境安装器（第一次使用前会解压 32MB 的 bootstrap）。 */
    private val installer = RuntimeInstaller(application)

    /** 真实 IQ Code 引擎。懒创建，第一次发消息时才初始化。 */
    private val engine = IqEngineController(application, this)

    // ------------------------------------------------------------------
    // 运行期状态（属于"这一轮正在发生什么"，不是界面状态，因此不进 StateFlow）
    // ------------------------------------------------------------------

    /** 当前正在流式输出的助手气泡 id。为 null 表示还没有任何 delta。 */
    private var streamingAssistantId: String? = null

    /** 当前回合的工具分组 id。一个工具批次 = 一张分组卡。 */
    private var currentGroupId: String? = null

    /** 引擎给的工具 id → 它落在哪个分组卡里。用于把结果/进度写回正确的位置。 */
    private data class LiveTool(val groupId: String, val name: String, val command: String)

    private val liveTools = mutableMapOf<String, LiveTool>()

    /**
     * 工具实时输出的合并缓冲。
     *
     * `onToolProgress` 的粒度是"每次进程写出一点"，Bash 跑一条 `apt install` 能到几千次。
     * 每次都改 StateFlow 会把界面拖垮，所以按 [PROGRESS_FLUSH_MS] 合并后再落盘。
     */
    private val progressBuffers = mutableMapOf<String, StringBuilder>()
    private val progressLastFlush = mutableMapOf<String, Long>()

    /** 正在等待用户回执的权限请求 id。 */
    private var pendingPermissionId: String? = null

    /**
     * 正在进行的「提问」流程。
     *
     * 引擎的 `AskUserQuestion` 可以一次带多个问题，界面是**分步**展示的，
     * 所以必须把请求 id、问题列表、当前步骤、已收集的答案都留着 ——
     * 只存"当前这一步"的话，走到第二步就丢了前面的答案。
     */
    private class PendingQuestions(
        val requestId: String,
        val questions: JSONArray,
        var index: Int = 0,
        val answers: JSONObject = JSONObject(),
    )

    private var pendingQuestions: PendingQuestions? = null


    /**
     * 会话操作的目标。
     *
     * `showSessionActions(session)` 只负责弹选择器，真正的动作在选择器回调里执行；
     * 那时只能拿到选项文案，拿不到是哪条会话。所以必须把目标记住 ——
     * 否则会出现"想删 A 结果删了当前会话"这类错删。
     */
    private var pendingSessionAction: SessionSummary? = null


    private val _state = MutableStateFlow(
        WorkspaceUiState(
            projectName = repo.projectName(),
            projectPath = repo.projectPath(),
            // 会话列表 / 对话历史 / 任务清单都来自磁盘，这里先给空值，
            // 由 [initSessionState] 在 IO 线程读好后填上。
            // 不在构造器里同步读盘：会话 JSONL 可能有几千行，会拖慢启动。
            sessions = emptyList(),
            activeSessionId = "",
            transcript = emptyList(),
            tasks = emptyList(),
            contextTokens = 0,
            contextWindow = 200_000,
            deviceStatus = clockLabel(),
            // 变更面板首屏为空；git 是要真实执行命令的，等用户点「刷新」再读。
            diff = DiffState(),
            terminalLines = repo.terminalBanner(repo.projectPath()),
            // 文件面板走真实文件系统。只列**一层**（不递归）：内置 Termux 环境装好后
            // home 下可能有几千个文件，递归会拖慢启动。
            filePath = FileBrowser.projectRoot(),
            fileEntries = FileBrowser.children(FileBrowser.projectRoot()),
            busySessionIds = emptySet(),
            composerBusy = false,
            workingStatus = null,
            sidebarOpen = false,
            // 真实探测，不再是写死的 true
            runtimeReady = installer.isInstalled(),
        )
    )

    val state: StateFlow<WorkspaceUiState> = _state.asStateFlow()

    init {
        initSessionState()
    }

    /**
     * 首次载入：列出磁盘上的真实会话，有历史就把最近一条打开。
     *
     * 「自动打开最近一条」是刻意保留的行为：否则用户每次启动都看到空对话流，
     * 会以为历史丢了（历史其实在侧栏里，但要自己去点）。
     */
    private fun initSessionState() {
        viewModelScope.launch(Dispatchers.IO) {
            val sessions = SessionReader.list(_state.value.projectPath)
            _state.update { it.copy(sessions = sessions) }
            sessions.firstOrNull()?.let { openSession(it) }
        }
    }

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
        if (engine.isBusy()) {
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
            // 真正的排队交给引擎：它会在「当前工具执行完 / 当前模型回复结束」这个
            // 协议安全点把预输入并入对话，并回调 onQueuedPromptApplied。
            // 界面这边不再自己维护"跑完一轮再发下一条"的递归链（那会与引擎重复排队）。
            val steered = engine.steer(text, userItem.id)
            if (!steered) {
                // 极端竞态：这里判定为忙，但真正入队时回合已经结束。
                // 不静默丢弃 —— 把文字放回输入框，用户能直接重发。
                _state.update { s ->
                    s.copy(
                        pendingInputs = s.pendingInputs.filterNot { q -> q.chatItemId == userItem.id },
                        composerText = text,
                        message = "上一回合刚好结束，消息未能入队，已放回输入框",
                    )
                }
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
        startTurn(text)
    }

    /**
     * 把一条提示词交给真实引擎。
     *
     * 会话配置**每一轮都重新下发**：用户在设置里改了权限模式/推理强度/上下文窗口后，
     * 不需要重启应用就能在下一轮生效。
     */
    private fun startTurn(prompt: String) {
        val configured = runCatching { engine.configure(engineOverrides()) }
        if (configured.isFailure) {
            // 配置阶段就失败（例如 SharedPreferences 损坏）时不要静默：
            // 否则界面会一直停在"正在思考…"，用户只能干等。
            finishTurnWithError("引擎配置失败：" + (configured.exceptionOrNull()?.message ?: "未知原因"))
            return
        }
        runCatching { engine.sendPrompt(prompt) }.onFailure { error ->
            finishTurnWithError(ToolText.friendlyError(error.message, error))
        }
    }

    /**
     * 把当前界面状态翻译成引擎覆盖项。
     *
     * `model` **故意留空**：界面上的 `modelLabel` 目前还是演示标签，真正生效的模型
     * 来自 API 配置记录（`ApiSettingsStore`）。在这里覆盖会用一个不存在的模型名把请求打挂；
     * 等设置页接线（Phase 5）之后再把模型选择器接上。
     */
    private fun engineOverrides(): EngineOverrides {
        val s = _state.value
        return EngineOverrides(
            permissionMode = s.permissionMode.toEngineMode(),
            effort = s.effort.toEngineEffort(),
            contextWindow = s.contextWindow,
            projectDirectory = s.projectPath,
            customSystemPrompt = s.settings.customSystemPrompt,
            visionEnabled = s.settings.visionEnabled,
            autoCompact = s.settings.autoCompact,
            autoCompactPercent = s.settings.autoCompactPercent,
            webSearchEnabled = s.settings.webSearchEnabled,
            webSearchProvider = when (s.settings.webSearchProvider) {
                WebSearchProvider.AUTO -> "auto"
                WebSearchProvider.DUCKDUCKGO -> "duckduckgo"
                WebSearchProvider.BING -> "bing"
            },
            webSearchMaxResults = s.settings.webSearchMaxResults,
            webTimeoutSec = s.settings.webSearchTimeoutSec,
            rootExecutionEnabled = s.settings.rootExecutionEnabled,
            sandboxAgentFullAccess = s.settings.sandboxAgentFullAccess,
            forcedKeepAliveEnabled = s.settings.forcedKeepAliveEnabled,
        )
    }

    fun stop() {
        // 引擎的 cancel() 会一并取消权限/提问/计划三个门控，
        // 所以这里不需要再单独回执一次权限，只要把界面状态收干净。
        engine.cancel()
        pendingPermissionId = null
        // 在途的流式残片与工具进度缓冲全部丢弃：它们属于已经作废的这一轮。
        progressBuffers.clear()
        progressLastFlush.clear()
        liveTools.clear()
        currentGroupId = null
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

    // ==================================================================
    // 引擎事件逐条落地
    //
    // 线程约定：这些方法**全部在主线程被调用**（IqEngineController 负责搬运），
    // 所以可以直接改 StateFlow、不需要再加 post/Handler。
    // 顺序约定：凡是"文本之后紧跟工具/结束"的地方，控制器都已先 flush 过文本缓冲，
    // 否则工具卡会插到还没显示的正文前面。
    // ==================================================================

    override fun onEngineSessionStarted(model: String) {
        streamingAssistantId = null
        currentGroupId = null
        liveTools.clear()
        progressBuffers.clear()
        progressLastFlush.clear()
        _state.update {
            it.copy(
                composerBusy = true,
                workingStatus = "正在思考…",
                busySessionIds = it.busySessionIds + it.activeSessionId,
            )
        }
    }

    override fun onEngineText(delta: String) {
        val existing = streamingAssistantId
        if (existing == null) {
            // 首个 delta 才创建气泡。原版是回合开始时就插一个空气泡、
            // 结束时再丢弃空的；Compose 里那样会先闪一个空白块，观感更差，
            // 所以改成延迟创建 —— 语义等价（空回复同样不会留下气泡）。
            val id = nextId("a")
            streamingAssistantId = id
            _state.update { s ->
                s.copy(
                    transcript = s.transcript + ChatItem(
                        id = id,
                        kind = ChatKind.ASSISTANT,
                        title = "IQ",
                        body = delta,
                        streaming = true,
                        processSteps = listOf("开始分析请求"),
                    ),
                    workingStatus = "正在回复…",
                )
            }
            return
        }
        _state.update { s ->
            s.copy(
                transcript = s.transcript.map {
                    if (it.id == existing) it.copy(body = it.body + delta) else it
                },
                workingStatus = "正在回复…",
            )
        }
    }

    override fun onEngineThinking(delta: String) {
        val id = streamingAssistantId ?: return
        _state.update { s ->
            s.copy(
                transcript = s.transcript.map {
                    if (it.id != id) it
                    // 思考预览按 1200 字符上限保留尾部（原版 liveThinking 的做法），
                    // 否则长思考会把内存和渲染都拖住。
                    else {
                        val merged = foldWhitespace(it.thinking + delta)
                        it.copy(thinking = if (merged.length > THINKING_LIMIT) merged.takeLast(THINKING_LIMIT) else merged)
                    }
                },
            )
        }
    }

    override fun onEngineToolBatchStarted(toolIds: List<String>) {
        // 一个批次 = 一张新的工具分组卡。批次边界由引擎给，界面不猜。
        val groupId = nextId("g")
        currentGroupId = groupId
        _state.update { s ->
            s.copy(
                transcript = s.transcript + ChatItem(
                    id = groupId,
                    kind = ChatKind.TOOL_GROUP,
                    groupLabel = "",
                    tools = emptyList(),
                ),
            )
        }
    }

    override fun onEngineToolUse(id: String, name: String, input: JSONObject?) {
        // 工作流类工具（任务清单/计划模式）不产生工具卡，只更新进度与步骤，
        // 与原版一致：它们的效果体现在任务面板和计划窗口上。
        if (ToolText.isWorkflowTool(name)) {
            _state.update { it.copy(workingStatus = "正在执行 $name…") }
            appendProcessStep("调用工具：$name")
            return
        }

        val groupId = currentGroupId ?: nextId("g").also { currentGroupId = it }
        val summary = ToolText.summary(name, input) ?: ""
        val command = input?.optString("command", "") ?: ""
        val (added, deleted) = ToolText.delta(name, input)
        val activity = ToolActivity(
            id = id,
            toolName = name,
            displayName = ToolText.userFacingName(name),
            summary = summary,
            additions = added,
            deletions = deleted,
            kind = kindOf(name),
        )
        liveTools[id] = LiveTool(groupId, name, command)

        _state.update { s ->
            val text = if (s.transcript.any { it.id == groupId }) s.transcript
            else s.transcript + ChatItem(id = groupId, kind = ChatKind.TOOL_GROUP)
            s.copy(
                transcript = text.map { item ->
                    if (item.id != groupId) item
                    else {
                        val tools = item.tools + activity
                        item.copy(tools = tools, groupLabel = groupLabel(tools))
                    }
                },
                workingStatus = "正在执行 $name…",
            )
        }
        appendProcessStep("调用工具：$name")
    }

    override fun onEngineToolProgress(id: String, chunk: String, stderr: Boolean, elapsedMs: Long) {
        if (liveTools[id] == null) return
        val buffer = progressBuffers.getOrPut(id) { StringBuilder() }
        if (stderr) buffer.append("[stderr]\n")
        buffer.append(chunk)
        // 单条工具的实时输出上限，超出丢头部（原版 liveOutput 是 40000）。
        if (buffer.length > LIVE_OUTPUT_LIMIT) buffer.delete(0, buffer.length - LIVE_OUTPUT_LIMIT)

        val now = System.currentTimeMillis()
        val last = progressLastFlush[id] ?: 0L
        if (now - last < PROGRESS_FLUSH_MS) return
        progressLastFlush[id] = now
        val text = buffer.toString()
        _state.update { s ->
            s.copy(
                transcript = s.transcript.map { item ->
                    if (item.kind != ChatKind.TOOL_GROUP) item
                    else item.copy(
                        tools = item.tools.map {
                            if (it.id == id) it.copy(output = text, elapsedMs = maxOf(it.elapsedMs, elapsedMs)) else it
                        },
                    )
                },
            )
        }
    }

    override fun onEngineToolResult(
        id: String,
        name: String,
        isError: Boolean,
        exitCode: Int,
        content: String,
        diff: String,
        addedLines: Int,
        deletedLines: Int,
        command: String,
    ) {
        val live = liveTools.remove(id)
        progressBuffers.remove(id)
        progressLastFlush.remove(id)

        _state.update { s ->
            s.copy(
                transcript = s.transcript.map { item ->
                    if (item.kind != ChatKind.TOOL_GROUP) item
                    else item.copy(
                        tools = item.tools.map { tool ->
                            if (tool.id != id) tool
                            else tool.copy(
                                completed = true,
                                failed = isError,
                                exitCode = exitCode,
                                output = content,
                                elapsedMs = maxOf(tool.elapsedMs, 0L),
                                additions = if (addedLines > 0) addedLines else tool.additions,
                                deletions = if (deletedLines > 0) deletedLines else tool.deletions,
                                // 包管理器命令失败时默认展开：报错信息通常很长，
                                // 折叠着用户只能看到"退出码 1"，等于没说。
                                expanded = tool.expanded ||
                                    (isError && ToolText.isPackageManagerTool(command)),
                            )
                        },
                    )
                },
                workingStatus = "正在继续处理…",
            )
        }
        appendProcessStep("工具完成：$name")

        // 编辑类工具可能改动工作区，顺手刷新变更面板。
        if (live != null && kindOf(name) == ToolKind.EDIT) refreshDiff()
    }

    override fun onEngineToolBatchCompleted(toolIds: List<String>) {
        if (toolIds.isEmpty()) return
        _state.update { s ->
            s.copy(
                transcript = s.transcript.map { item ->
                    if (item.kind != ChatKind.TOOL_GROUP) item
                    else {
                        val touched = item.tools.any { it.id in toolIds }
                        if (!touched) item
                        else item.copy(
                            groupCompleted = true,
                            // 批结束时仍没拿到结果的成员，原版会标记成失败并补一句话。
                            // 保留这个行为：静默留一个"永远转圈"的卡更难排查。
                            tools = item.tools.map { tool ->
                                if (tool.id in toolIds && !tool.completed) {
                                    tool.copy(
                                        completed = true,
                                        failed = true,
                                        output = tool.output.ifEmpty { "工具在返回结果前结束。" },
                                    )
                                } else tool
                            },
                        )
                    }
                },
            )
        }
        currentGroupId = null
    }

    override fun onEnginePermissionRequest(requestId: String, tool: String, summary: String, highRisk: Boolean) {
        pendingPermissionId = requestId
        _state.update {
            it.copy(
                permissionRequest = PermissionRequest(
                    id = requestId,
                    tool = tool,
                    subtitle = subtitleFor(tool),
                    detail = summary,
                    riskLevel = if (highRisk) RiskLevel.HIGH else RiskLevel.NORMAL,
                ),
            )
        }
    }

    override fun onEngineQuestionRequest(requestId: String, questionsJson: String) {
        val questions = runCatching { JSONArray(questionsJson) }.getOrDefault(JSONArray())
        if (questions.length() == 0) {
            // 空提问没有可问的内容，直接回执让引擎继续，别把用户晾在一个空窗口前。
            engine.respondQuestion(requestId, JSONObject())
            return
        }
        pendingQuestions = PendingQuestions(requestId = requestId, questions = questions)
        showQuestionStep()
    }

    /**
     * 展示提问流程的第 [step] 步。
     *
     * 引擎的 `AskUserQuestion` 有两种形态：单问题（直接提交）与多问题（分步，
     * 每步一个窗口，按钮是「下一步」，最后一步才是「提交」）。
     * 答案按**问题原文**为键累积（与引擎的约定一致）。
     */
    private fun showQuestionStep() {
        val flow = pendingQuestions ?: return
        val question = flow.questions.optJSONObject(flow.index)
        if (question == null) {
            // 该步不是合法对象：跳过它而不是整个流程失败。
            advanceQuestion(JSONObject())
            return
        }
        val options = question.optJSONArray("options")
        val choices = buildList {
            if (options != null) {
                for (i in 0 until options.length()) {
                    val option = options.optJSONObject(i) ?: continue
                    add(ChoiceOption(label = option.optString("label", ""), detail = option.optString("description", "")))
                }
            }
        }
        val isLast = flow.index >= flow.questions.length() - 1
        _state.update {
            it.copy(
                choicePicker = ChoicePickerState(
                    title = question.optString("header", "问题"),
                    intent = ChoiceIntent.QUESTION,
                    prompt = question.optString("question", "IQ 应该怎么做？"),
                    options = choices,
                    allowFreeForm = true,
                    freeFormHint = "其他回答…",
                    multiSelect = question.optBoolean("multiSelect", false),
                    // 多问题时分步：中间步骤是「下一步」，最后一步才是「提交」。
                    submitLabel = if (isLast) "提交" else "下一步",
                ),
            )
        }
    }

    /** 记录这一步的答案并前进；已是最后一步则回执引擎。 */
    private fun advanceQuestion(answer: Any?) {
        val flow = pendingQuestions ?: return
        val question = flow.questions.optJSONObject(flow.index)
        val key = question?.optString("question", "") ?: ""
        if (key.isNotEmpty() && answer != null) {
            runCatching { flow.answers.put(key, answer) }
        }
        val next = flow.index + 1
        if (next >= flow.questions.length()) {
            pendingQuestions = null
            _state.update { it.copy(choicePicker = null) }
            engine.respondQuestion(flow.requestId, flow.answers)
            return
        }
        flow.index = next
        showQuestionStep()
    }

    /** 取消整个提问流程：回执空答案，引擎会按「未选择」继续。 */
    private fun cancelQuestions() {
        val flow = pendingQuestions ?: return
        pendingQuestions = null
        _state.update { it.copy(choicePicker = null) }
        engine.respondQuestion(flow.requestId, JSONObject())
        appendInfo("已跳过提问", "IQ 的提问被跳过，它会按「未选择」继续。")
    }

    override fun onEnginePlanApprovalRequest(request: EnginePlanApproval) {
        _state.update { it.copy(planApproval = request.toUiPlanApproval()) }
    }

    override fun onEngineTasksChanged(tasks: List<AgentTask>) {
        _state.update { it.copy(tasks = tasks) }
    }

    override fun onEngineProjectDirectoryChanged(directory: String) {
        _state.update {
            it.copy(
                projectPath = directory,
                projectName = directory.trimEnd('/').substringAfterLast('/').ifEmpty { directory },
                filePath = directory,
            )
        }
    }

    override fun onEngineQueuedPromptApplied() {
        // 引擎已经把队首预输入并入了对话。界面这边做的是：
        // 出队一条（FIFO），并把还在流的上一段回复收尾。
        finalizeStreaming(keepIfEmpty = false)
        _state.update { s ->
            val rest = s.pendingInputs.drop(1)
            s.copy(
                pendingInputs = rest,
                workingStatus = "预输入已加载，正在继续…",
                message = if (rest.isEmpty()) "预输入已加载，正在继续" else "预输入已加载，还剩 ${rest.size} 条",
            )
        }
    }

    override fun onEngineResponseRetry() {
        // 重试会重发整段回复：把上一次失败尝试留下的半截气泡丢掉，
        // 否则界面上会出现"半句话 + 完整回复"。
        discardStreamingAssistant()
        _state.update { it.copy(workingStatus = "连接中断，正在重试模型请求…", composerBusy = true) }
    }

    override fun onEngineInterruptedBySteering() {
        discardStreamingAssistant()
        _state.update {
            it.copy(
                workingStatus = "旧响应已打断，正在按最新纠正重新规划…",
                composerBusy = true,
            )
        }
    }

    override fun onEngineUsage(inputTokens: Long, outputTokens: Long) {
        val used = maxOf(0L, inputTokens) + maxOf(0L, outputTokens)
        _state.update { s ->
            // 有效窗口下限 16000：配置里若是 0/异常值，也不能让上下文条除出 Inf。
            val window = maxOf(16_000, engine.contextWindowTokens().takeIf { it > 0 } ?: s.contextWindow)
            val shown = if (used > 0) used.toInt() else engine.estimateContextTokens()
            s.copy(contextTokens = shown, contextWindow = window)
        }
    }

    override fun onEngineStatus(status: String) {
        val text = ToolText.displayStatus(status, "thinking", true)
        _state.update { it.copy(workingStatus = text) }
        appendProcessStep(text)
    }

    override fun onEngineTurnComplete(stopReason: String) {
        finalizeStreaming(keepIfEmpty = false)
        _state.update { s ->
            s.copy(
                composerBusy = false,
                workingStatus = null,
                busySessionIds = s.busySessionIds - s.activeSessionId,
                contextTokens = engine.estimateContextTokens().takeIf { it > 0 } ?: s.contextTokens,
                sessions = s.sessions.map { session ->
                    if (session.id == s.activeSessionId) session.copy(busy = false, updatedAtLabel = "刚刚") else session
                },
            )
        }
        // 第一轮结束后会话文件才真正落盘（引擎在 sendPrompt 里创建 SessionStore），
        // 所以这里必须重读列表，否则新会话在侧栏里不存在。
        syncActiveSessionFromDisk()
    }

    /**
     * 回合结束后把当前会话对齐到磁盘状态。
     *
     * 做两件事：
     * 1. 重读会话列表 —— 让刚创建的会话出现、让标题/消息数/时间刷新；
     * 2. 如果当前还没有 activeSessionId（刚 /new 过），把引擎刚写出的文件认领为当前会话。
     */
    private fun syncActiveSessionFromDisk() {
        viewModelScope.launch(Dispatchers.IO) {
            val sessions = SessionReader.list(_state.value.projectPath)
            val sessionFile = engine.sessionFile()
            _state.update { s ->
                val activeId = when {
                    s.activeSessionId.isNotEmpty() -> s.activeSessionId
                    sessionFile != null -> sessionFile.absolutePath
                    else -> ""
                }
                s.copy(sessions = sessions, activeSessionId = activeId)
            }
        }
    }

    override fun onEngineError(message: String, error: Throwable?) {
        finishTurnWithError(ToolText.friendlyError(message, error))
    }

    // ---------- 引擎事件用到的内部工具 ----------

    /** 一轮以错误收尾：收尾气泡、落一条错误卡、把忙碌态清干净。 */
    private fun finishTurnWithError(text: String) {
        finalizeStreaming(keepIfEmpty = false)
        _state.update { s ->
            s.copy(
                transcript = s.transcript + ChatItem(
                    id = nextId("e"),
                    kind = ChatKind.ERROR,
                    title = "错误",
                    body = text,
                ),
                composerBusy = false,
                workingStatus = null,
                permissionRequest = null,
                busySessionIds = s.busySessionIds - s.activeSessionId,
            )
        }
        pendingPermissionId = null
    }

    /** 结束流式状态。[keepIfEmpty] 为 false 时，空白气泡会被丢弃（原版 discardEmptyStreamingMessage）。 */
    private fun finalizeStreaming(keepIfEmpty: Boolean) {
        val id = streamingAssistantId ?: return
        streamingAssistantId = null
        _state.update { s ->
            s.copy(
                transcript = s.transcript.mapNotNull { item ->
                    if (item.id != id) item
                    else if (!keepIfEmpty && item.body.isBlank() && item.thinking.isBlank()) null
                    else item.copy(streaming = false)
                },
            )
        }
    }

    /** 整条丢弃当前流式气泡（重试 / 被纠偏打断时用）。 */
    private fun discardStreamingAssistant() {
        val id = streamingAssistantId ?: return
        streamingAssistantId = null
        _state.update { s -> s.copy(transcript = s.transcript.filterNot { it.id == id }) }
    }

    /** 给当前助手气泡记一条过程步骤（最多 12 条，相邻重复去重，与原版一致）。 */
    private fun appendProcessStep(step: String) {
        val id = streamingAssistantId ?: return
        _state.update { s ->
            s.copy(
                transcript = s.transcript.map { item ->
                    if (item.id != id) item
                    else {
                        val merged = if (item.processSteps.lastOrNull() == step) item.processSteps
                        else (item.processSteps + step).takeLast(PROCESS_STEP_LIMIT)
                        item.copy(processSteps = merged)
                    }
                },
            )
        }
    }

    /** 思考文本的空白折叠：连续空白合成一个空格（原版 appendCollapsedWhitespace）。 */
    private fun foldWhitespace(text: String): String {
        val out = StringBuilder(text.length)
        var lastSpace = false
        for (ch in text) {
            val isSpace = ch == ' ' || ch == '\n' || ch == '\t' || ch == '\r'
            if (isSpace) {
                if (!lastSpace) out.append(' ')
                lastSpace = true
            } else {
                out.append(ch)
                lastSpace = false
            }
        }
        return out.toString()
    }


    private fun queuedStatus(count: Int): String =
        if (count <= 1) "已预输入，等待当前回复完成…" else "已预输入 $count 条，等待当前回复完成…"

    /**
     * 回执一次权限请求。
     *
     * 回执对象是**引擎**而不是界面上的临时对象：引擎的工具线程正阻塞在
     * `PermissionGate.require()` 的闩锁上，只有真正 respond 过去它才会继续。
     * 若一直不回执，引擎会等满 30 分钟。
     */
    fun resolvePermission(allow: Boolean, alwaysAllow: Boolean = false) {
        val requestId = pendingPermissionId
        pendingPermissionId = null
        engine.respondPermission(requestId ?: "", allow)
        _state.update {
            it.copy(
                permissionRequest = null,
                permissionMode = if (alwaysAllow) PermissionMode.ACCEPT_EDITS else it.permissionMode,
                message = if (alwaysAllow) {
                    if (allow) "已允许本次调用，并切换为自动编辑" else "已拒绝本次调用，并切换为自动编辑"
                } else {
                    if (allow) "已允许本次调用" else "已拒绝本次调用"
                },
            )
        }
        // 切到自动编辑后要立刻下发，否则下一条工具调用还会弹窗。
        if (alwaysAllow) runCatching { engine.configure(engineOverrides()) }
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
            runCatching { engine.cancelPlanModeFromUi("") }
            _state.update { it.copy(planApproval = null, message = "已退出计划模式") }
            return
        }
        if (engine.isBusy()) {
            _state.update { it.copy(message = "当前回合还在进行，等它结束后再进入计划模式") }
            return
        }
        val result = runCatching { engine.enterPlanModeFromUi() }
        val entered = result.getOrNull()?.isError == false
        if (!entered) {
            // 计划模式进不去时必须说出来。默默什么都不做的话，用户会以为
            // 自己已经在只读模式里了 —— 而实际上下一条消息会照常执行。
            finishTurnWithError("进入计划模式失败：" + (result.exceptionOrNull()?.message ?: "引擎拒绝了该请求"))
            return
        }
        _state.update { it.copy(permissionMode = PermissionMode.PLAN, message = "已进入计划模式（只读）") }
        // 带了目标就当成一条普通提示词发出去：计划模式下引擎只会产出计划，不会执行。
        // 「问目标」这一步由引擎自己用「选择」窗口发起（等提问门控接线后生效）。
        if (arg.isNotEmpty()) {
            _state.update { it.copy(composerText = arg) }
            send()
        }
    }

    /**
     * 批准 / 退回计划。
     *
     * 回执必须发给引擎：`onPlanApprovalRequest` 对应的工具线程正阻塞在审批闩锁上，
     * 只有 `respondPlanApproval` 才会让它继续。原版这个闩锁**故意没有超时**——
     * 无人值守的请求永远不能变成"批准执行"。
     */
    fun resolvePlan(approved: Boolean) {
        val plan = _state.value.planApproval ?: return
        engine.respondPlanApproval(
            plan.id,
            if (approved) PlanApprovalGate.Decision.APPROVE else PlanApprovalGate.Decision.KEEP_PLANNING,
            "",
        )
        appendInfo(
            if (approved) "计划已批准" else "计划已退回",
            if (approved) "开始执行：${plan.title}" else "等待修改后的计划。",
        )
        _state.update { it.copy(planApproval = null) }
    }


    /** 「继续规划」并带上用户填写的反馈。 */
    fun resolvePlanWithFeedback(feedback: String) {
        val plan = _state.value.planApproval ?: return
        val trimmed = feedback.trim()
        // 空反馈等价于"退回"：把 KEEP_PLANNING 与空 feedback 一起发过去，
        // 引擎会重新进入规划并等待下一版计划。
        engine.respondPlanApproval(plan.id, PlanApprovalGate.Decision.KEEP_PLANNING, trimmed)
        appendInfo(
            if (trimmed.isEmpty()) "计划已退回" else "已提交反馈",
            if (trimmed.isEmpty()) "等待修改后的计划。" else trimmed,
        )
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
        // 记住操作对象：动作是在**选择器回调**里执行的，那时只能拿到选项文案，
        // 拿不到是哪条会话 —— 不记住就会出现"删掉了当前会话"这种错删。
        pendingSessionAction = session
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

    /** 打开备注编辑：没有选项，只靠自由输入提交。 */
    private fun editSessionNote(session: SessionSummary) {
        _state.update {
            it.copy(
                choicePicker = ChoicePickerState(
                    title = "会话备注",
                    intent = ChoiceIntent.SESSION_NOTE,
                    prompt = "给这条会话写一句备注，方便以后在侧栏里认出来。",
                    options = emptyList(),
                    allowFreeForm = true,
                    freeFormHint = if (session.note.isEmpty()) "例如：修好了相册崩溃" else session.note,
                ),
            )
        }
    }

    /** 写入备注到磁盘。空字符串表示清除。 */
    private fun saveSessionNote(session: SessionSummary, note: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(session.id)
            // titleOverride 传空串 = 不覆盖标题，只改备注。
            val ok = SessionReader.updateMetadata(file, note, "")
            val sessions = SessionReader.list(_state.value.projectPath)
            _state.update {
                it.copy(
                    sessions = sessions,
                    message = if (ok) {
                        if (note.isEmpty()) "已清除备注" else "备注已保存"
                    } else {
                        "备注保存失败"
                    },
                )
            }
        }
    }

    fun dismissChoicePicker() {
        // 提问流程的「取消」必须把空答案回执给引擎，否则引擎会一直等
        // （`QuestionGate.ask` 的闩锁要等 30 分钟才超时）。
        if (_state.value.choicePicker?.intent == ChoiceIntent.QUESTION) {
            cancelQuestions()
            return
        }
        _state.update { it.copy(choicePicker = null) }
    }

    /**
     * 提交选择器里选中的项。
     *
     * [indices] 是选中的下标列表：单选选择器只会有 0 或 1 个，
     * 多选的提问流程会有多个。统一用列表是为了让两条路径共用一套 UI 回调。
     */
    fun onSubmitSelection(indices: List<Int>) {
        val picker = _state.value.choicePicker ?: return
        // 提问流程走自己的分步状态机，答案要按问题原文累积。
        if (picker.intent == ChoiceIntent.QUESTION) {
            val labels = indices.mapNotNull { picker.options.getOrNull(it)?.label }
            val answer: Any? = when {
                labels.isEmpty() -> null
                picker.multiSelect -> JSONArray(labels)
                else -> labels.first()
            }
            advanceQuestion(answer)
            return
        }
        // 单选选择器语义不变：等价于点第一项。
        onChoiceSelected(indices.firstOrNull() ?: -1)
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
                val target = pendingSessionAction
                pendingSessionAction = null
                _state.update { it.copy(choicePicker = null) }
                if (target == null) {
                    // 拿不到操作对象就什么都不做。宁可无反应，也不能猜一条会话删掉。
                    _state.update { it.copy(message = "找不到目标会话，操作已取消") }
                } else when (option.label) {
                    "删除会话" -> deleteSession(target.id)
                    "恢复会话" -> openSession(target)
                    "编辑备注" -> editSessionNote(target)
                    "清除备注" -> saveSessionNote(target, "")
                    else -> _state.update { it.copy(message = "${option.label}（未接线）") }
                }
            }
            ChoiceIntent.SESSION_NOTE -> {
                // 这个分支正常不会走到（备注靠自由输入提交），留作兜底。
                _state.update { it.copy(choicePicker = null) }
            }
            ChoiceIntent.QUESTION -> {
                // 提问流程正常走 onSubmitSelection / dismissChoicePicker；
                // 单选回调真落到这里时按「跳过本步」处理，不要静默什么都不做。
                advanceQuestion(null)
            }
            ChoiceIntent.GENERIC, ChoiceIntent.ATTACH -> _state.update {
                it.copy(choicePicker = null, message = "${option.label}（Mock）")
            }
            // 计划模式下的「目标澄清」：用户选定的目标本身就是一条提示词。
            // 计划正文由引擎产出（不再由界面拼），所以这里只是把它发出去。
            ChoiceIntent.PLAN_GOAL -> {
                val goal = if (option.detail.isNotEmpty()) "${option.label} —— ${option.detail}" else option.label
                _state.update { it.copy(choicePicker = null, composerText = goal) }
                send()
            }
        }
    }

    /**
     * 选择窗口里的「其他回答…」提交：不走选项，直接把这段自由文本当作输入。
     * 在计划模式下它等价于"自定义目标"，会作为提示词发给引擎；
     * 在会话备注中它是备注正文。
     */
    fun onSubmitFreeForm(text: String) {
        val trimmed = text.trim()
        val picker = _state.value.choicePicker
        if (picker == null) return
        // 备注可以提交空字符串（= 清除），所以不能和"空输入就关闭"混在一起。
        if (picker.intent == ChoiceIntent.SESSION_NOTE) {
            val target = pendingSessionAction
            pendingSessionAction = null
            _state.update { it.copy(choicePicker = null) }
            if (target == null) {
                _state.update { it.copy(message = "找不到目标会话，备注未保存") }
            } else {
                saveSessionNote(target, trimmed)
            }
            return
        }
        if (trimmed.isEmpty()) {
            dismissChoicePicker()
            return
        }
        if (picker.intent == ChoiceIntent.PLAN_GOAL) {
            _state.update { it.copy(choicePicker = null, composerText = trimmed) }
            send()
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

    /**
     * 新建会话。
     *
     * 这里只重置引擎的对话上下文（新 workflowId），**不**立即建文件：
     * 引擎的 `SessionStore` 是在第一轮发消息时才创建的，提前建会留下
     * 一堆"0 条消息"的空会话文件，侧栏会很快被垃圾填满。
     * 所以界面上先插入一个占位条目，等第一轮结束后 [refreshSessions] 会用真实文件替换它。
     */
    fun newSession() {
        engine.resetConversation()
        discardStreamingAssistant()
        currentGroupId = null
        liveTools.clear()
        progressBuffers.clear()
        progressLastFlush.clear()
        pendingPermissionId = null
        _state.update {
            it.copy(
                // 占位条目的 id 用空串而不是伪造 id：它不是真实文件，
                // 若被当成路径去打开会失败，空串能让"打不开"这件事显式化。
                activeSessionId = "",
                transcript = emptyList(),
                tasks = emptyList(),
                contextTokens = 0,
                composerBusy = false,
                workingStatus = null,
                tab = WorkspaceTab.CHAT,
                sidebarOpen = false,
                message = "已开始新会话，第一条消息会创建会话文件",
            )
        }
    }

    /**
     * 打开一条历史会话。
     *
     * 两件事都要做，缺一不可：
     * 1. `engine.resumeConversation(file)` —— 让**引擎**把历史装回 provider 上下文，
     *    否则界面上看得到历史、模型却不知道，追问会答非所问；
     * 2. 从同一个 JSONL 重建界面 transcript。
     *
     * 文件读取放 IO 线程：一个长会话的 JSONL 可能有几千行，在主线程读会卡住界面。
     */
    fun openSession(session: SessionSummary) {
        val file = File(session.id)
        if (!file.isFile) {
            _state.update { it.copy(message = "会话文件不存在：${session.id}") }
            refreshSessions()
            return
        }
        // 切会话必须先把引擎这一轮停掉：否则旧会话的回调还会继续往新的
        // transcript 里写（引擎的 cancel 会连带取消三个门控）。
        engine.cancel()
        discardStreamingAssistant()
        currentGroupId = null
        liveTools.clear()
        progressBuffers.clear()
        progressLastFlush.clear()
        pendingPermissionId = null

        _state.update { it.copy(sidebarOpen = false, tab = WorkspaceTab.CHAT, workingStatus = "正在载入会话…") }
        viewModelScope.launch(Dispatchers.IO) {
            val transcript = SessionReader.transcript(file)
            val tasks = SessionReader.tasks(file)
            // 引擎必须先配置好才有 config 可用，否则 resumeConversation 不会恢复项目/工作流。
            val resumed = runCatching {
                engine.configure(engineOverrides())
                engine.resumeConversation(file)
            }
            _state.update {
                it.copy(
                    activeSessionId = session.id,
                    transcript = transcript,
                    tasks = tasks,
                    contextTokens = engine.estimateContextTokens(),
                    contextWindow = engine.contextWindowTokens().takeIf { w -> w > 0 } ?: it.contextWindow,
                    composerBusy = false,
                    workingStatus = null,
                    message = resumed.exceptionOrNull()?.let { error ->
                        "会话历史已显示，但引擎恢复失败：" + (error.message ?: "未知原因")
                    },
                )
            }
        }
    }

    /**
     * 删除会话。
     *
     * 删除的是**磁盘上的文件**，不是列表里的一项 —— 只从列表移除的话，
     * 下次启动它又会回来，用户会以为删除没生效。
     */
    fun deleteSession(id: String) {
        if (id.isEmpty()) {
            // 尚未落盘的占位会话：直接清掉当前视图即可。
            _state.update { it.copy(activeSessionId = "", transcript = emptyList(), tasks = emptyList(), message = "会话已清空") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val removed = SessionReader.delete(File(id))
            val wasActive = _state.value.activeSessionId == id
            if (wasActive) {
                engine.resetConversation()
                discardStreamingAssistant()
            }
            val sessions = SessionReader.list(_state.value.projectPath)
            val next = sessions.firstOrNull()
            _state.update {
                it.copy(
                    sessions = sessions,
                    activeSessionId = if (wasActive) "" else it.activeSessionId,
                    transcript = if (wasActive) emptyList() else it.transcript,
                    tasks = if (wasActive) emptyList() else it.tasks,
                    message = if (removed) "会话已删除" else "删除失败：文件可能已被移除",
                )
            }
            // 删除后仍留历史时，顺手把最近一条打开，避免出现"删完一片空白"。
            if (wasActive && next != null) openSession(next)
        }
    }

    /** 从磁盘重新读取会话列表（新建/发消息/删除之后调用）。 */
    fun refreshSessions() {
        viewModelScope.launch(Dispatchers.IO) {
            val sessions = SessionReader.list(_state.value.projectPath)
            _state.update { state ->
                // 保留"尚未落盘的新会话"这一状态：此刻 activeSessionId 为空且
                // 列表里还没有对应文件，不能被列表刷新覆盖掉。
                state.copy(sessions = sessions)
            }
        }
    }



    fun openSidebar() {
        // 每次拉开侧栏都重读一次文件列表：新会话是在**第一轮消息之后**才落盘的，
        // 不刷新的话"刚聊完的会话"在侧栏里看不到。
        refreshSessions()
        _state.update { it.copy(sidebarOpen = true) }
    }
    fun closeSidebar() = _state.update { it.copy(sidebarOpen = false) }

    // ---------- 工作区 ----------

    fun selectTab(tab: WorkspaceTab) {
        _state.update { it.copy(tab = tab) }
        // 进入「变更」页时才去读 git。切换 Tab 是明确的用户动作，
        // 每次切过去读一次是合理的；若挂在回合结束自动读，大仓库会明显拖慢对话。
        if (tab == WorkspaceTab.CHANGES) refreshDiff()
    }

    /**
     * 重新拉一次变更列表（变更面板的「刷新」）。
     *
     * 对应原版 `refreshChanges()`（重跑 `git status --short` + `git diff`）。
     * 这里数据来自 [repo]，所以"刷新"等价于重新读一次。
     */
    /**
     * 刷新 git 变更。
     *
     * 三条 git 命令（rev-parse / status / diff）+ 未跟踪文件的补丁，必须放 IO 线程，
     * 而且这是**唯一**会去读 git 的入口 —— 不在回合结束时自动跑：
     * 一个大仓库的 `git diff` 可能几秒，挂在每个回合结尾会让对话明显变卡。
     */
    fun refreshDiff() {
        val path = _state.value.projectPath
        _state.update { it.copy(diff = it.diff.copy(loading = true)) }
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { GitChanges.read(getApplication(), path) }
                .getOrElse { error ->
                    DiffState(note = "读取 git 变更失败：" + (error.message ?: "未知原因"))
                }
            _state.update {
                it.copy(
                    diff = result,
                    message = when {
                        result.note.isNotEmpty() -> result.note
                        result.files.isEmpty() -> "工作区没有未提交的变更"
                        else -> "已刷新变更列表（${result.files.size} 个文件）"
                    },
                )
            }
        }
    }

    fun cycleThemeMode() = _state.update {
        val next = when (it.themeMode) {
            ThemeMode.SYSTEM -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.SYSTEM
        }
        it.copy(themeMode = next, message = "主题：${next.label}")
    }

    // ---------- 内置 Termux 运行环境 ----------

    /**
     * 生成自检报告。
     *
     * 刻意**绝不抛异常**：这是排障入口，它自己崩掉或什么都不显示，就等于把唯一的诊断手段也弄没了。
     * 探测失败时把失败原因当作报告正文显示出来，至少还能看到是哪一项炸的。
     */
    private fun buildEnvironmentReport(): String = runCatching {
        EnvDoctor.report(getApplication())
    }.getOrElse { error ->
        "# IQ Code Compose · 环境自检\n\n" +
            "自检本身失败了：\n" +
            "${error.javaClass.name}: ${error.message}\n\n" +
            "堆栈：\n" +
            error.stackTraceToString() +
            "\n— 报告结束，可整段复制"
    }

    /** 打开「环境自检」：现场跑一遍探测并生成可复制的报告。 */
    fun openEnvironment() {
        val reportText = buildEnvironmentReport()
        _state.update {
            it.copy(
                environmentOpen = true,
                environmentReport = reportText,
                runtimeReady = runCatching { installer.isInstalled() }.getOrDefault(false),
            )
        }
    }

    fun closeEnvironment() = _state.update { it.copy(environmentOpen = false) }

    /** 重新生成自检报告（安装前后各跑一次，便于对比）。 */
    fun refreshEnvironmentReport() {
        val reportText = buildEnvironmentReport()
        _state.update {
            it.copy(
                environmentReport = reportText,
                runtimeReady = runCatching { installer.isInstalled() }.getOrDefault(false),
            )
        }
    }

    /**
     * 安装（或重新初始化）内置 Termux 环境。
     *
     * 解压 32MB、落 3473 个文件、建 1177 个符号链接，必须放到 IO 线程，
     * 并且把进度实时回灌给 UI —— 整个过程在真机上要十几秒，不给反馈会被当成卡死。
     */
    fun installRuntime() {
        if (_state.value.runtimeInstalling) return
        _state.update {
            it.copy(runtimeInstalling = true, runtimeProgress = 0, runtimeMessage = "正在准备…")
        }
        viewModelScope.launch(Dispatchers.IO) {
            val ok = runCatching {
                installer.install { message, percent ->
                    _state.update { it.copy(runtimeMessage = message, runtimeProgress = percent) }
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        runtimeInstalling = false,
                        runtimeMessage = "初始化失败：${error.message ?: error.javaClass.simpleName}",
                    )
                }
            }.isSuccess

            if (ok) {
                val reportText = buildEnvironmentReport()
                _state.update {
                    it.copy(
                        runtimeInstalling = false,
                        runtimeProgress = 100,
                        runtimeMessage = "内置 Termux 环境已就绪",
                        runtimeReady = installer.isInstalled(),
                        environmentReport = reportText,
                    )
                }
            }
        }
    }

    /** 幂等修复：清半成品目录、补齐 apt/dpkg 兼容层。适用于 apt 升级过或上次中断。 */
    fun repairRuntime() {
        if (_state.value.runtimeInstalling) return
        _state.update { it.copy(runtimeInstalling = true, runtimeMessage = "正在修复…") }
        viewModelScope.launch(Dispatchers.IO) {
            val ok = runCatching { installer.repairIfInstalled() }.isSuccess
            val reportText = buildEnvironmentReport()
            _state.update {
                it.copy(
                    runtimeInstalling = false,
                    runtimeMessage = if (ok) "已修复" else "修复失败",
                    runtimeReady = installer.isInstalled(),
                    environmentReport = reportText,
                )
            }
        }
    }

    /** 复制自检报告到剪贴板，便于把真机现状整段发出来定位问题。 */
    fun copyEnvironmentReport(): Boolean {
        val app = getApplication<Application>()
        val text = _state.value.environmentReport.ifBlank { buildEnvironmentReport() }
        val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return false
        clipboard.setPrimaryClip(ClipData.newPlainText("IQ Code 环境自检", text))
        _state.update { it.copy(message = "环境自检报告已复制") }
        return true
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

    /** 刷新文件面板。Agent 改动文件后用它重新列目录。 */
    fun refreshFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            val entries = FileBrowser.children(_state.value.filePath)
            _state.update { it.copy(fileEntries = entries, message = "已刷新文件列表") }
        }
    }

    fun navigateTo(path: String) {
        // 列目录要读盘，放 IO 线程：大目录（例如 node_modules）在主线程列会卡住界面。
        viewModelScope.launch(Dispatchers.IO) {
            val entries = FileBrowser.children(path)
            _state.update { it.copy(filePath = path, fileEntries = entries, openFile = null) }
        }
    }

    fun navigateUp() {
        val current = _state.value.filePath
        val root = rootPath()
        if (current == root) return
        val parent = current.substringBeforeLast('/', "")
        if (parent.isEmpty() || parent.length < root.length) {
            // 已经到根（或再往上会越过根）：回到根，而不是继续往外走。
            navigateTo(root)
            return
        }
        navigateTo(parent)
    }

    fun openFile(entry: FileEntry) {
        if (entry.directory) {
            openDirectory(entry)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val file = FileBrowser.read(entry.path)
            _state.update { it.copy(openFile = file, message = "已打开 ${entry.name}（只读）") }
        }
    }

    /** 文件面板的根路径（内置 Termux home 不存在时回退到应用私有目录）。 */
    private fun rootPath(): String = FileBrowser.projectRoot()

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

        /** 思考预览保留的尾部字符数（原版 `liveThinking` 上限 1200）。 */
        private const val THINKING_LIMIT = 1200

        /** 单条工具实时输出的上限（原版 `liveOutput` 上限 40000）。 */
        private const val LIVE_OUTPUT_LIMIT = 40_000

        /** 工具实时输出落盘间隔：每条进程输出都改一次 StateFlow 会把界面拖垮。 */
        private const val PROGRESS_FLUSH_MS = 200L

        /** 助手气泡上保留的过程步骤条数上限（原版 12）。 */
        private const val PROCESS_STEP_LIMIT = 12
    }
}
