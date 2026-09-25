package com.iqge.iqcode.compose.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.iqge.iqcode.compose.model.AgentTask
import com.iqge.iqcode.compose.model.EffortLevel
import com.iqge.iqcode.compose.model.PermissionMode
import com.iqge.iqcode.compose.model.TaskState
import com.termux.app.iqcode.core.IQCodeEngine
import com.termux.app.iqcode.core.PermissionGate
import com.termux.app.iqcode.core.PermissionModePolicy
import com.termux.app.iqcode.core.PlanApprovalGate
import com.termux.app.iqcode.core.QuestionGate
import com.termux.app.iqcode.model.PlanWorkflowState
import com.termux.app.iqcode.model.SessionConfig
import com.termux.app.iqcode.model.ToolCall
import com.termux.app.iqcode.model.ToolExecutionResult
import com.iqge.iqcode.compose.model.PlanApproval
import com.termux.app.iqcode.storage.ApiSettingsStore
import com.termux.app.iqcode.tasks.TaskStore
import com.termux.app.iqcode.tools.IQTool
import org.json.JSONObject
import java.io.File

/**
 * 界面侧覆盖项。
 *
 * 语义：`null` = **不覆盖**，沿用 [ApiSettingsStore] 里读出来的值。
 * 这样"设置页还没接线"的字段不会被这里悄悄写成默认值，
 * 造成"设置里明明开着、实际却关着"这种最难查的不一致。
 */
internal data class EngineOverrides(
    val permissionMode: String? = null,
    val effort: String? = null,
    val model: String? = null,
    val contextWindow: Int? = null,
    val projectDirectory: String? = null,
    val visionEnabled: Boolean? = null,
    val customSystemPrompt: String? = null,
    val autoCompact: Boolean? = null,
    val autoCompactPercent: Int? = null,
    val webSearchEnabled: Boolean? = null,
    val webSearchProvider: String? = null,
    val webSearchMaxResults: Int? = null,
    val webTimeoutSec: Int? = null,
    val rootExecutionEnabled: Boolean? = null,
    val sandboxAgentFullAccess: Boolean? = null,
    val forcedKeepAliveEnabled: Boolean? = null,
)

/**
 * 一次计划审批请求在界面侧需要的全部内容。
 *
 * 公开可见性是被迫的：[EngineEvents] 是 public 接口，它的参数类型不能是 internal。
 */
data class EnginePlanApproval(
    val requestId: String,
    val title: String,
    val body: String,
    val revision: Int,
    val path: String,
    val permissionNote: String,
)

/**
 * 引擎事件。**保证在主线程回调**，实现方可以直接改界面状态。
 *
 * 之所以不让 ViewModel 直接实现 `IQCodeEngine.Listener`：
 * 引擎回调来自后台线程、参数是引擎内部的 Java 类型（`ToolCall` / `ToolExecutionResult` …），
 * 直接暴露会让状态机里到处散落 `post { }` 与字段解引用，也没法单独测。
 *
 * 公开可见性是被迫的：[WorkspaceViewModel] 是 public 类，它的父类型不能是 internal。
 */
interface EngineEvents {
    fun onEngineSessionStarted(model: String)
    fun onEngineText(delta: String)
    fun onEngineThinking(delta: String)
    fun onEngineToolBatchStarted(toolIds: List<String>)
    fun onEngineToolUse(id: String, name: String, input: JSONObject?)
    fun onEngineToolProgress(id: String, chunk: String, stderr: Boolean, elapsedMs: Long)
    fun onEngineToolResult(
        id: String,
        name: String,
        isError: Boolean,
        exitCode: Int,
        content: String,
        diff: String,
        addedLines: Int,
        deletedLines: Int,
        command: String,
    )
    fun onEngineToolBatchCompleted(toolIds: List<String>)
    fun onEnginePermissionRequest(requestId: String, tool: String, summary: String, highRisk: Boolean)
    fun onEngineQuestionRequest(requestId: String, questionsJson: String)
    fun onEnginePlanApprovalRequest(request: EnginePlanApproval)
    fun onEngineTasksChanged(tasks: List<AgentTask>)
    fun onEngineProjectDirectoryChanged(directory: String)
    fun onEngineInterruptedBySteering()
    fun onEngineResponseRetry()
    fun onEngineQueuedPromptApplied()
    fun onEngineUsage(inputTokens: Long, outputTokens: Long)
    fun onEngineStatus(status: String)
    fun onEngineTurnComplete(stopReason: String)
    fun onEngineError(message: String, error: Throwable?)
}

/**
 * 真实 IQ Code 引擎的接线层。
 *
 * 职责边界（刻意收窄，避免和 ViewModel 抢状态）：
 * - **引擎生命周期**：按需创建、配置、关闭；
 * - **线程搬运**：把后台回调全部投递到主线程；
 * - **事件节流**：流式文本按 32ms 合并成块（原版 `queueStreamingDelta` 的做法）；
 * - **类型翻译**：引擎的 Java 类型 → 界面用的轻量类型。
 *
 * 它**不**持有界面状态、不碰 `ChatItem`，那些都是 ViewModel 的事。
 */
internal class IqEngineController(
    context: Context,
    private val events: EngineEvents,
) : IQCodeEngine.Listener {

    private val appContext: Context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    /** 待合并的流式文本。引擎线程写、主线程读，必须加锁。 */
    private val deltaLock = Any()
    private val pendingText = StringBuilder()
    private var flushPosted = false

    /** 回合代际。取消/重置后自增，用来静默丢弃在途的旧回调。 */
    @Volatile
    private var generation = 0L

    private var engine: IQCodeEngine? = null
    private var apiStore: ApiSettingsStore? = null

    /** 最近一次实际生效的配置，供界面查询（只读）。 */
    @Volatile
    var sessionConfig: SessionConfig? = null
        private set

    private val flushDelta = Runnable { flushPendingText() }

    // ------------------------------------------------------------------
    // 生命周期
    // ------------------------------------------------------------------

    /** 拿到引擎，需要时创建并配置。 */
    fun engine(): IQCodeEngine {
        engine?.let { return it }
        val created = IQCodeEngine(appContext, this)
        engine = created
        return created
    }

    /**
     * 用「设置里读出来的配置」叠加界面侧的覆盖项，然后下发到引擎。
     *
     * 每次发消息前都调用一次：用户在设置里改了权限模式/模型后，
     * 不需要重启应用就能在下一轮生效。
     */
    fun configure(overrides: EngineOverrides): SessionConfig {
        val store = apiStore ?: ApiSettingsStore(appContext).also { apiStore = it }
        val base = runCatching { store.load() }.getOrElse { SessionConfig() }
        applyOverrides(base, overrides)
        sessionConfig = base
        engine().configure(base)
        return base
    }

    private fun applyOverrides(config: SessionConfig, o: EngineOverrides) {
        o.permissionMode?.let { config.permissionMode = PermissionModePolicy.normalize(it) }
        o.effort?.let { if (it.isNotBlank()) config.effort = it }
        o.model?.let { if (it.isNotBlank()) config.model = it }
        o.contextWindow?.let { if (it > 0) config.contextWindowTokens = it }
        o.projectDirectory?.let { if (it.isNotBlank()) config.projectDirectory = it }
        o.visionEnabled?.let { config.visionEnabled = it }
        o.customSystemPrompt?.let { config.customSystemPrompt = it }
        o.autoCompact?.let { config.autoCompact = it }
        // 界面用的是百分比整数，引擎用的是倍率；在这里换算，别把两种口径泄进引擎。
        o.autoCompactPercent?.let { config.autoCompactRatio = (it.coerceIn(50, 100)) / 100.0 }
        o.webSearchEnabled?.let { config.webSearchEnabled = it }
        o.webSearchProvider?.let { if (it.isNotBlank()) config.webSearchProvider = it }
        o.webSearchMaxResults?.let { if (it > 0) config.webSearchMaxResults = it }
        o.webTimeoutSec?.let { if (it > 0) config.webTimeoutMs = it * 1000 }
        o.rootExecutionEnabled?.let { config.rootExecutionEnabled = it }
        o.sandboxAgentFullAccess?.let { config.sandboxAgentFullAccess = it }
        o.forcedKeepAliveEnabled?.let { config.forcedKeepAliveEnabled = it }
    }

    fun isBusy(): Boolean = engine?.isBusy ?: false

    /** 当前回合已经开始（用于区分「该排队」还是「该直接发」）。 */
    fun hasEngine(): Boolean = engine != null

    fun sendPrompt(prompt: String) {
        engine().sendPrompt(prompt)
    }

    /** 排队一条预输入。返回 false 表示引擎当时并不忙（调用方应改走 [sendPrompt]）。 */
    fun steer(prompt: String, messageId: String): Boolean =
        engine?.steerPrompt(prompt, null, messageId) ?: false

    /**
     * 停止当前回合。
     *
     * 自增代际：引擎的 `cancel()` 之后仍可能有一两个在途回调，
     * 不丢弃的话会在新一轮对话里插入上一轮的尾巴。
     */
    fun cancel() {
        generation++
        synchronized(deltaLock) {
            pendingText.setLength(0)
            flushPosted = false
        }
        main.removeCallbacks(flushDelta)
        runCatching { engine?.cancel() }
    }

    fun resumeConversation(file: File) {
        generation++
        synchronized(deltaLock) {
            pendingText.setLength(0)
            flushPosted = false
        }
        main.removeCallbacks(flushDelta)
        engine().resumeConversation(file)
    }

    fun resetConversation() {
        generation++
        synchronized(deltaLock) {
            pendingText.setLength(0)
            flushPosted = false
        }
        main.removeCallbacks(flushDelta)
        runCatching { engine?.resetConversation() }
    }

    fun shutdown() {
        generation++
        main.removeCallbacks(flushDelta)
        runCatching { engine?.shutdown() }
    }

    // ------------------------------------------------------------------
    // 界面回执
    // ------------------------------------------------------------------

    fun respondPermission(requestId: String, allow: Boolean): Boolean =
        runCatching { engine?.respondPermission(requestId, allow) ?: false }.getOrDefault(false)

    fun respondQuestion(requestId: String, answers: JSONObject): Boolean =
        runCatching { engine?.respondQuestion(requestId, answers) ?: false }.getOrDefault(false)

    fun respondPlanApproval(requestId: String, decision: PlanApprovalGate.Decision, feedback: String): Boolean =
        runCatching { engine?.respondPlanApproval(requestId, decision, feedback) ?: false }.getOrDefault(false)

    fun planWorkflowState(): PlanWorkflowState = engine?.planWorkflowState ?: PlanWorkflowState.idle()

    /** `/plan`：进入只读计划模式。 */
    fun enterPlanModeFromUi(): ToolExecutionResult = engine().enterPlanModeFromUi()

    /** `/plan off`：退出计划模式并附带驳回理由。 */
    fun cancelPlanModeFromUi(feedback: String): ToolExecutionResult = engine().cancelPlanModeFromUi(feedback)

    // ------------------------------------------------------------------
    // 上下文用量
    // ------------------------------------------------------------------

    fun estimateContextTokens(): Int = runCatching { engine?.estimateContextTokens() ?: 0 }.getOrDefault(0)

    /** 当前会话的 JSONL 文件。引擎尚未配置或还没写过内容时为 null。 */
    fun sessionFile(): File? = runCatching { engine?.sessionFile }.getOrNull()

    fun hasMeasuredContextUsage(): Boolean =
        runCatching { engine?.hasMeasuredContextUsage() ?: false }.getOrDefault(false)

    fun contextWindowTokens(): Int = sessionConfig?.contextWindowTokens ?: 0

    fun contextPercent(): Int = runCatching { engine?.contextPercent() ?: 0 }.getOrDefault(0)

    // ------------------------------------------------------------------
    // 流式文本合并（原版 queueStreamingDelta：32ms 合并成块）
    // ------------------------------------------------------------------

    private fun queueText(delta: String) {
        synchronized(deltaLock) {
            if (generation != currentGeneration()) return
            pendingText.append(delta)
            if (flushPosted) return
            flushPosted = true
        }
        main.postDelayed(flushDelta, DELTA_MERGE_MS)
    }

    /** 主线程：把合并好的块交给界面。 */
    private fun flushPendingText() {
        val chunk: String
        val gen: Long
        synchronized(deltaLock) {
            chunk = pendingText.toString()
            pendingText.setLength(0)
            flushPosted = false
            gen = generation
        }
        if (chunk.isEmpty()) return
        if (gen != generation) return
        events.onEngineText(chunk)
    }

    /**
     * 立即把缓冲里的文本交给界面。
     *
     * 工具/结束/错误事件之前**必须**调用，否则工具卡会插到还没显示的文本前面（顺序错乱）。
     * 只能在主线程调用。
     */
    private fun flushTextNow() {
        main.removeCallbacks(flushDelta)
        flushPendingText()
    }

    private fun currentGeneration(): Long = generation

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else main.post(action)
    }

    /** 投递到主线程并校验代际；代际不符则静默丢弃（同原版 `postRuntimeUi`）。 */
    private fun onMainForGeneration(gen: Long, action: () -> Unit) {
        main.post {
            if (gen != generation) return@post
            action()
        }
    }

    // ------------------------------------------------------------------
    // IQCodeEngine.Listener —— 21 个回调
    // ------------------------------------------------------------------

    override fun onSessionStarted(config: SessionConfig) {
        val gen = generation
        onMainForGeneration(gen) {
            events.onEngineSessionStarted(config.model ?: "")
        }
    }

    override fun onTextDelta(text: String) {
        if (text.isNullOrEmpty()) return
        queueText(text)
    }

    override fun onThinkingDelta(thinking: String) {
        if (thinking.isNullOrEmpty()) return
        val gen = generation
        onMainForGeneration(gen) { events.onEngineThinking(thinking) }
    }

    override fun onToolBatchStarted(batch: IQCodeEngine.ToolBatch?) {
        val ids = batch?.toolCalls?.filterNotNull()?.map { it.id ?: "" } ?: return
        if (ids.isEmpty()) return
        val gen = generation
        onMainForGeneration(gen) { events.onEngineToolBatchStarted(ids) }
    }

    override fun onToolBatchCompleted(batch: IQCodeEngine.ToolBatch?) {
        val ids = batch?.toolCalls?.filterNotNull()?.map { it.id ?: "" } ?: return
        if (ids.isEmpty()) return
        val gen = generation
        onMainForGeneration(gen) { events.onEngineToolBatchCompleted(ids) }
    }

    override fun onToolUse(call: ToolCall?) {
        if (call == null) return
        val gen = generation
        onMainForGeneration(gen) {
            // 引擎保证工具卡插在已有文本之后，所以这里先落盘再发工具事件。
            flushTextNow()
            events.onEngineToolUse(call.id ?: "", call.name ?: "工具", call.input)
        }
    }

    override fun onToolProgress(call: ToolCall?, chunk: String?, stderr: Boolean, elapsedMs: Long) {
        if (call?.id == null || chunk.isNullOrEmpty()) return
        val gen = generation
        onMainForGeneration(gen) { events.onEngineToolProgress(call.id, chunk, stderr, elapsedMs) }
    }

    override fun onToolResult(call: ToolCall?, result: ToolExecutionResult?) {
        if (call == null || result == null) return
        val gen = generation
        onMainForGeneration(gen) {
            flushTextNow()
            events.onEngineToolResult(
                id = call.id ?: "",
                name = call.name ?: "工具",
                isError = result.isError,
                exitCode = result.exitCode,
                content = result.content ?: "",
                diff = result.diff ?: "",
                addedLines = result.addedLines,
                deletedLines = result.deletedLines,
                command = call.input?.optString("command", "") ?: "",
            )
        }
    }

    override fun onPermissionRequest(request: PermissionGate.PermissionRequest?) {
        if (request == null) return
        val gen = generation
        val kind = request.kind
        val highRisk = kind == IQTool.PermissionKind.SYSTEM || kind == IQTool.PermissionKind.SHELL
        val call = request.call
        val name = call?.name ?: "工具"
        val summary = ToolText.summary(name, call?.input) ?: ""
        onMainForGeneration(gen) {
            flushTextNow()
            events.onEnginePermissionRequest(request.requestId, name, summary, highRisk)
        }
    }

    override fun onQuestionRequest(request: QuestionGate.QuestionRequest?) {
        if (request == null) return
        val gen = generation
        val json = request.questions?.toString() ?: "[]"
        onMainForGeneration(gen) {
            flushTextNow()
            events.onEngineQuestionRequest(request.requestId, json)
        }
    }

    override fun onPlanStateChanged(state: PlanWorkflowState?) {
        // 计划状态本身由引擎持有，界面按需查询；这里不需要推事件。
    }

    override fun onPlanApprovalRequest(request: PlanApprovalGate.ApprovalRequest?) {
        if (request == null) return
        val plan = request.plan
        val payload = EnginePlanApproval(
            requestId = request.requestId,
            title = "实现计划 · revision ${plan.revision}",
            body = plan.planText ?: "",
            revision = plan.revision.toInt(),
            path = plan.planFile ?: "",
            permissionNote = "批准只确认计划；后续操作仍按你的权限模式",
        )
        val gen = generation
        onMainForGeneration(gen) {
            flushTextNow()
            events.onEnginePlanApprovalRequest(payload)
        }
    }

    override fun onTasksChanged(snapshot: TaskStore.Snapshot?) {
        if (snapshot == null) return
        val tasks = snapshot.tasks.mapNotNull { toAgentTask(it) }
        val gen = generation
        onMainForGeneration(gen) { events.onEngineTasksChanged(tasks) }
    }

    override fun onProjectDirectoryChanged(projectDirectory: String?) {
        if (projectDirectory.isNullOrBlank()) return
        val gen = generation
        onMainForGeneration(gen) { events.onEngineProjectDirectoryChanged(projectDirectory) }
    }

    override fun onResponseInterruptedBySteering() {
        val gen = generation
        onMainForGeneration(gen) {
            flushTextNow()
            events.onEngineInterruptedBySteering()
        }
    }

    override fun onResponseRetry() {
        val gen = generation
        onMainForGeneration(gen) {
            // 重试会重发整段回复：缓冲里属于上一次失败尝试的残片必须丢掉，
            // 否则界面上会留下"半句话 + 重新开始的完整回复"。
            synchronized(deltaLock) { pendingText.setLength(0) }
            main.removeCallbacks(flushDelta)
            events.onEngineResponseRetry()
        }
    }

    override fun onQueuedPromptApplied() {
        val gen = generation
        onMainForGeneration(gen) {
            flushTextNow()
            events.onEngineQueuedPromptApplied()
        }
    }

    override fun onUsage(inputTokens: Long, outputTokens: Long) {
        val gen = generation
        onMainForGeneration(gen) { events.onEngineUsage(inputTokens, outputTokens) }
    }

    override fun onStatus(status: String?) {
        val gen = generation
        onMainForGeneration(gen) { events.onEngineStatus(status ?: "") }
    }

    override fun onTurnComplete(stopReason: String?) {
        val gen = generation
        onMainForGeneration(gen) {
            flushTextNow()
            events.onEngineTurnComplete(stopReason ?: "end_turn")
        }
    }

    override fun onError(message: String?, error: Throwable?) {
        val gen = generation
        onMainForGeneration(gen) {
            flushTextNow()
            events.onEngineError(message ?: error?.message ?: "未知错误", error)
        }
    }

    // ------------------------------------------------------------------

    /** TaskStore 的任务 JSON → 界面条目。键名见 [TaskStore] 的写入处。 */
    private fun toAgentTask(json: JSONObject): AgentTask? {
        val subject = json.optString("subject", "")
        if (subject.isBlank()) return null
        val detail = json.optString("description", "")
        val activeForm = json.optString("activeForm", "")
        val state = when (json.optString("status", "pending")) {
            "completed" -> TaskState.DONE
            "in_progress" -> TaskState.RUNNING
            else -> TaskState.PENDING
        }
        return AgentTask(
            title = subject,
            detail = if (state == TaskState.RUNNING && activeForm.isNotBlank()) activeForm else detail,
            state = state,
        )
    }

    private companion object {
        /** 流式文本合并窗口。原版 `queueStreamingDelta` 用的是 32ms。 */
        const val DELTA_MERGE_MS = 32L
    }
}

// ----------------------------------------------------------------------
// 界面枚举 → 引擎字符串
// ----------------------------------------------------------------------

internal fun PermissionMode.toEngineMode(): String = when (this) {
    PermissionMode.ASK -> PermissionModePolicy.DEFAULT
    PermissionMode.ACCEPT_EDITS -> PermissionModePolicy.ACCEPT_EDITS
    PermissionMode.PLAN -> PermissionModePolicy.PLAN
    PermissionMode.AUTO -> PermissionModePolicy.AUTO
    PermissionMode.DONT_ASK -> PermissionModePolicy.DONT_ASK
    PermissionMode.BYPASS -> PermissionModePolicy.BYPASS
}

internal fun EffortLevel.toEngineEffort(): String = when (this) {
    EffortLevel.LOW -> "low"
    EffortLevel.MEDIUM -> "medium"
    EffortLevel.HIGH -> "high"
    EffortLevel.MAX -> "max"
    EffortLevel.AUTO -> "auto"
}

/** 引擎权限模式 → 界面枚举（引擎回传的可能是 `default`）。 */
internal fun engineModeToUi(mode: String?): PermissionMode = when (PermissionModePolicy.normalize(mode)) {
    PermissionModePolicy.ACCEPT_EDITS -> PermissionMode.ACCEPT_EDITS
    PermissionModePolicy.PLAN -> PermissionMode.PLAN
    PermissionModePolicy.AUTO -> PermissionMode.AUTO
    PermissionModePolicy.DONT_ASK -> PermissionMode.DONT_ASK
    PermissionModePolicy.BYPASS -> PermissionMode.BYPASS
    else -> PermissionMode.ASK
}

/** 界面用的审批模型（供 `PlanApprovalOverlay` 直接渲染）。 */
internal fun EnginePlanApproval.toUiPlanApproval(): PlanApproval = PlanApproval(
    id = requestId,
    title = title,
    body = body,
    revision = revision,
    path = path,
    permissionNote = permissionNote,
)
