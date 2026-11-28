package com.zhizhu.zhicode.compose.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.zhizhu.zhicode.compose.model.ChatImage
import com.zhizhu.zhicode.compose.model.AgentTask
import com.zhizhu.zhicode.compose.model.EffortLevel
import com.zhizhu.zhicode.compose.model.McpServerStatus
import com.zhizhu.zhicode.compose.model.McpToolInfo
import com.zhizhu.zhicode.compose.model.PermissionMode
import com.zhizhu.zhicode.compose.model.TaskState
import com.termux.app.zhicode.core.ZhiCodeEngine
import org.json.JSONArray
import com.termux.app.zhicode.core.PermissionGate
import com.termux.app.zhicode.core.PermissionModePolicy
import com.termux.app.zhicode.core.PlanApprovalGate
import com.termux.app.zhicode.core.QuestionGate
import com.termux.app.zhicode.core.RiskClassifier
import com.termux.app.zhicode.tools.WebFetchTool
import com.termux.app.zhicode.tools.WebSearchTool
import com.termux.app.zhicode.model.PlanWorkflowState
import com.termux.app.zhicode.model.SessionConfig
import com.termux.app.zhicode.model.ToolCall
import com.termux.app.zhicode.model.ToolExecutionResult
import com.zhizhu.zhicode.compose.model.PlanApproval
import com.zhizhu.zhicode.compose.model.readChatImageBlocks
import com.termux.app.zhicode.storage.ApiSettingsStore
import com.termux.app.zhicode.tasks.TaskStore
import com.zhizhu.zhicode.pi.PiRpcBackend
import org.json.JSONObject
import java.util.LinkedHashMap
import java.util.concurrent.Executors

/** 界面侧覆盖项。 */
internal data class EngineOverrides(
    val permissionMode: String? = null,
    val effort: String? = null,
    val model: String? = null,
    val contextWindow: Int? = null,
    val projectDirectory: String? = null,
    val visionEnabled: Boolean? = null,
    val customSystemPrompt: String? = null,
    /** 提示词槽位：空串 = 用默认（见 model/PromptSlots.kt）。*/
    val initPrompt: String? = null,
    val summaryPrompt: String? = null,
    val compactPromptHead: String? = null,
    /** 角色卡内容。引擎把它作为 &lt;role_card&gt; 块注入系统提示词（见 SystemPromptBuilder）。 */
    val roleCard: String? = null,
    val autoCompact: Boolean? = null,
    val autoCompactPercent: Int? = null,
    val webSearchEnabled: Boolean? = null,
    val webSearchProvider: String? = null,
    val webSearchMaxResults: Int? = null,
    val webTimeoutSec: Int? = null,
    /** 密钥制搜索服务（Tavily/Exa/Brave）的 Key；只在选中的服务需要时非空。 */
    val webSearchApiKey: String? = null,
    /** SearXNG 实例地址；只有 searxng 服务用到。 */
    val webSearchBaseUrl: String? = null,
    /** 当前生效搜索服务的每服务选项（JSON，如 depth/topic/language）。 */
    val webSearchServiceConfig: String? = null,
    val rootExecutionEnabled: Boolean? = null,
    /** Shizuku 工具开关。与 Root 独立：它的前提是「用过 adb 启动过 Shizuku」，不是「设备 root」。 */
    val shizukuExecutionEnabled: Boolean? = null,
    val forcedKeepAliveEnabled: Boolean? = null,
    val maxConcurrentSubagents: Int? = null,
)

/** 一次计划审批请求在界面侧需要的全部内容。 */
data class EnginePlanApproval(
    val requestId: String,
    val title: String,
    val body: String,
    val revision: Int,
    val path: String,
    val permissionNote: String,
)

/** 引擎事件。 */
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
        previews: List<ChatImage>,
    )
    fun onEngineToolBatchCompleted(toolIds: List<String>)
    fun onEnginePermissionRequest(requestId: String, tool: String, summary: String, highRisk: Boolean)
    fun onHostPermissionClosed(requestId: String)
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

/** 真实 ZhiCode 引擎的接线层。 */
internal class ZhiEngineController(
    context: Context,
    private val events: EngineEvents,
) : ZhiCodeEngine.Listener {

    private val appContext: Context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    /** 待合并的流式文本。引擎线程写、主线程读，必须加锁。 */
    private val deltaLock = Any()
    private val pendingText = StringBuilder()
    private var flushPosted = false
    private var pendingTextGeneration = 0L
    private val thinkingLock = Any()
    private val pendingThinking = StringBuilder()
    private var thinkingFlushPosted = false
    private var thinkingGeneration = 0L
    private val toolProgressLock = Any()
    private val pendingToolProgress = LinkedHashMap<String, ToolProgressBuffer>()
    private var toolProgressFlushPosted = false

    /** 回合代际。取消/重置后自增，用来静默丢弃在途的旧回调。 */
    @Volatile
    private var generation = 0L

    private var engine: ZhiCodeEngine? = null
    private var piBackend: PiRpcBackend? = null
    private var apiStore: ApiSettingsStore? = null

    private fun usePiBackend(config: SessionConfig? = sessionConfig): Boolean =
        !"debug-scripted".equals(config?.protocol, ignoreCase = true)

    private val piSink = object : PiRpcBackend.Sink {
        override fun onSessionStarted(model: String) {
            val gen = generation
            onMainForGeneration(gen) { events.onEngineSessionStarted(model) }
        }

        override fun onTextDelta(delta: String) {
            if (delta.isNotEmpty()) queueText(delta)
        }

        override fun onThinkingDelta(delta: String) {
            this@ZhiEngineController.onThinkingDelta(delta)
        }

        override fun onToolBatchStarted(toolIds: List<String>) {
            if (toolIds.isEmpty()) return
            val gen = generation
            onMainForGeneration(gen) { events.onEngineToolBatchStarted(toolIds) }
        }

        override fun onToolUse(call: ToolCall) {
            this@ZhiEngineController.onToolUse(call)
        }

        override fun onToolProgress(toolId: String, chunk: String, isError: Boolean, elapsedMs: Long) {
            if (chunk.isEmpty()) return
            synchronized(toolProgressLock) {
                val existing = pendingToolProgress[toolId]
                if (existing == null || existing.stderr != isError) {
                    pendingToolProgress[toolId] = ToolProgressBuffer(toolId, isError, StringBuilder(chunk), elapsedMs)
                } else {
                    existing.chunk.append(chunk)
                    existing.elapsedMs = maxOf(existing.elapsedMs, elapsedMs)
                }
                if (toolProgressFlushPosted) return
                toolProgressFlushPosted = true
            }
            main.postDelayed(flushToolProgress, TOOL_PROGRESS_MERGE_MS)
        }

        override fun onToolResult(
            id: String,
            name: String,
            isError: Boolean,
            exitCode: Int,
            content: String,
            diff: String,
            addedLines: Int,
            deletedLines: Int,
            command: String,
            previews: List<ChatImage>,
        ) {
            val gen = generation
            onMainForGeneration(gen) {
                flushTextNow()
                events.onEngineToolResult(
                    id = id,
                    name = name,
                    isError = isError,
                    exitCode = exitCode,
                    content = content,
                    diff = diff,
                    addedLines = addedLines,
                    deletedLines = deletedLines,
                    command = command,
                    previews = previews,
                )
            }
        }

        override fun onToolBatchCompleted(toolIds: List<String>) {
            if (toolIds.isEmpty()) return
            val gen = generation
            onMainForGeneration(gen) { events.onEngineToolBatchCompleted(toolIds) }
        }

        override fun onPermissionRequest(requestId: String, toolName: String, summary: String, highRisk: Boolean) {
            val gen = generation
            onMainForGeneration(gen) {
                if (requestId.startsWith("pi-host-") && piBackend?.isHostPermissionPending(requestId) != true) {
                    return@onMainForGeneration
                }
                flushTextNow()
                events.onEnginePermissionRequest(requestId, toolName, summary, highRisk)
            }
        }

        override fun onHostPermissionClosed(requestId: String) {
            onMain { events.onHostPermissionClosed(requestId) }
        }

        override fun onResponseRetry() {
            this@ZhiEngineController.onResponseRetry()
        }

        override fun onQueuedPromptApplied() {
            this@ZhiEngineController.onQueuedPromptApplied()
        }

        override fun onUsage(inputTokens: Long, outputTokens: Long) {
            this@ZhiEngineController.onUsage(inputTokens, outputTokens)
        }

        override fun onStatus(status: String) {
            this@ZhiEngineController.onStatus(status)
        }

        override fun onTurnComplete(stopReason: String) {
            this@ZhiEngineController.onTurnComplete(stopReason)
        }

        override fun onError(message: String, error: Throwable?) {
            this@ZhiEngineController.onError(message, error)
        }
    }

    private fun pi(): PiRpcBackend {
        piBackend?.let { return it }
        return PiRpcBackend(appContext, piSink).also { piBackend = it }
    }

    /** 最近一次实际生效的配置，供界面查询（只读）。 */
    @Volatile
    var sessionConfig: SessionConfig? = null
        private set
    private var lastConfiguredFingerprint: String? = null

    private val flushDelta = Runnable { flushPendingText() }
    private val flushThinking = Runnable { flushPendingThinking() }
    private val flushToolProgress = Runnable { flushPendingToolProgress() }

    private data class ToolProgressBuffer(
        val id: String,
        val stderr: Boolean,
        val chunk: StringBuilder,
        var elapsedMs: Long,
    )

    /** 引擎**生命周期**调用的串行后台线程。 */
    private val engineOps = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "zhi-engine-ops").apply { isDaemon = true }
    }


    /** 拿到引擎，需要时创建并配置。 */
    fun engine(): ZhiCodeEngine {
        engine?.let { return it }
        val created = ZhiCodeEngine(appContext, this)
        engine = created
        return created
    }

    /** 用「设置里读出来的配置」叠加界面侧的覆盖项，然后下发到引擎。 */
    fun configure(overrides: EngineOverrides): SessionConfig {
        val store = apiStore ?: ApiSettingsStore(appContext).also { apiStore = it }
        val base = runCatching { store.load() }.getOrElse { SessionConfig() }
        applyEngineOverrides(base, overrides)
        val fingerprint = configurationFingerprint(base)
        val changed = fingerprint != lastConfiguredFingerprint
        sessionConfig = base
        if (usePiBackend(base)) {
            pi().configure(base)
        } else {
            piBackend?.cancelHostTools()
            engine().configure(base)
        }
        if (changed) lastConfiguredFingerprint = fingerprint
        if (changed) runCatching { store.save(base) }
        return base
    }

    private fun configurationFingerprint(config: SessionConfig): String = listOf(
        config.profileId, config.profileRevision.toString(), config.credentialRevision.toString(),
        config.protocol, config.baseUrl, config.extraHeaders, config.model, config.effort,
        config.permissionMode, config.projectDirectory, config.customSystemPrompt,
        config.initPrompt, config.summaryPrompt, config.compactPromptHead,
        config.roleCard, config.toolMode, config.visionEnabled.toString(),
        config.autoCompact.toString(), config.autoCompactRatio.toString(), config.contextWindowTokens.toString(),
        config.webSearchEnabled.toString(), config.webSearchProvider,
        config.webSearchMaxResults.toString(), config.webTimeoutMs.toString(),
        config.rootExecutionEnabled.toString(),
        config.shizukuExecutionEnabled.toString(),
        config.forcedKeepAliveEnabled.toString(), config.maxConcurrentSubagents.toString(), config.workflowId,
    ).joinToString("\u0000")

    fun isBusy(): Boolean = if (usePiBackend()) piBackend?.isBusy() ?: false else engine?.isBusy ?: false

    /** 当前回合已经开始（用于区分「该排队」还是「该直接发」）。 */
    fun hasEngine(): Boolean = if (usePiBackend()) piBackend != null else engine != null

    /** 发送一轮请求。 */
    fun sendPrompt(prompt: String, extraContent: JSONArray? = null) {
        // 新回合开一代：让排队的旧取消（cancel() 的代际守卫）自行动失效，
        // 否则"停止 → 立刻再发"时，排在后台队列里的那次取消会杀掉这条新回合。
        generation++
        if (usePiBackend()) {
            pi().sendPrompt(prompt, extraContent)
            return
        }
        if (extraContent == null || extraContent.length() == 0) engine().sendPrompt(prompt)
        else engine().sendPrompt(prompt, extraContent)
    }

    /** 排队一条预输入。返回 false 表示引擎当时并不忙（调用方应改走 [sendPrompt]）。 */
    fun steer(prompt: String, messageId: String, extraContent: JSONArray? = null): Boolean =
        if (usePiBackend()) piBackend?.steer(prompt, extraContent, messageId) ?: false
        else engine?.steerPrompt(prompt, extraContent ?: JSONArray(), messageId) ?: false

    /** 停止当前回合。 */
    fun cancel() {
        piBackend?.cancelHostTools()
        generation++
        synchronized(deltaLock) {
            pendingText.setLength(0)
            flushPosted = false
            pendingTextGeneration = generation
        }
        main.removeCallbacks(flushDelta)
        main.removeCallbacks(flushThinking)
        main.removeCallbacks(flushToolProgress)
        synchronized(thinkingLock) {
            pendingThinking.setLength(0)
            thinkingFlushPosted = false
            thinkingGeneration = generation
        }
        synchronized(toolProgressLock) {
            pendingToolProgress.clear()
            toolProgressFlushPosted = false
        }
        val genAtSubmit = generation
        engineOps.execute {
            if (generation != genAtSubmit) return@execute
            runCatching { piBackend?.cancel() }
            runCatching { engine?.cancel() }
        }
    }

    fun resumeConversation(sessionId: String): Result<Unit> {
        require(sessionId.isNotBlank()) { "会话标识为空" }
        piBackend?.cancelHostTools()
        generation++
        synchronized(deltaLock) {
            pendingText.setLength(0)
            flushPosted = false
            pendingTextGeneration = generation
        }
        main.removeCallbacks(flushDelta)
        main.removeCallbacks(flushThinking)
        main.removeCallbacks(flushToolProgress)
        synchronized(thinkingLock) {
            pendingThinking.setLength(0)
            thinkingFlushPosted = false
            thinkingGeneration = generation
        }
        synchronized(toolProgressLock) {
            pendingToolProgress.clear()
            toolProgressFlushPosted = false
        }
        val genAtSubmit = generation
        val future = java.util.concurrent.CompletableFuture<Result<Unit>>()
        engineOps.execute {
            if (generation != genAtSubmit) {
                future.complete(Result.success(Unit))
                return@execute
            }
            val result = runCatching {
                if (usePiBackend()) pi().resumeConversation(sessionId) else engine().resumeConversation(sessionId)
            }.onFailure { error ->
                onMainForGeneration(genAtSubmit) {
                    events.onEngineError("恢复会话失败：${error.message ?: error}", error)
                }
            }
            future.complete(result)
        }
        return future.get()
    }

    fun resetConversation() {
        piBackend?.cancelHostTools()
        generation++
        synchronized(deltaLock) {
            pendingText.setLength(0)
            flushPosted = false
            pendingTextGeneration = generation
        }
        main.removeCallbacks(flushDelta)
        main.removeCallbacks(flushThinking)
        main.removeCallbacks(flushToolProgress)
        synchronized(thinkingLock) {
            pendingThinking.setLength(0)
            thinkingFlushPosted = false
            thinkingGeneration = generation
        }
        synchronized(toolProgressLock) {
            pendingToolProgress.clear()
            toolProgressFlushPosted = false
        }
        // 与 cancel() 同样的代际守卫：排队的重置在执行时若已来了新回合，就让它过去
        // （引擎自己的 resetConversation() 开头也会再 cancel 一次，语义不会丢）。
        val genAtSubmit = generation
        engineOps.execute {
            if (generation != genAtSubmit) return@execute
            runCatching { piBackend?.resetConversation() }
            runCatching { engine?.resetConversation() }
        }
    }

    fun shutdown() {
        generation++
        main.removeCallbacks(flushDelta)
        main.removeCallbacks(flushThinking)
        main.removeCallbacks(flushToolProgress)
        runCatching { piBackend?.shutdown() }
        runCatching { engine?.shutdown() }
        engineOps.shutdownNow()
    }


    fun respondPermission(requestId: String, allow: Boolean): Boolean =
        if (usePiBackend()) runCatching { piBackend?.respondPermission(requestId, allow) ?: false }.getOrDefault(false)
        else runCatching { engine?.respondPermission(requestId, allow) ?: false }.getOrDefault(false)

    fun respondQuestion(requestId: String, answers: JSONObject): Boolean =
        runCatching { engine?.respondQuestion(requestId, answers) ?: false }.getOrDefault(false)

    fun respondPlanApproval(requestId: String, decision: PlanApprovalGate.Decision, feedback: String): Boolean =
        runCatching { engine?.respondPlanApproval(requestId, decision, feedback) ?: false }.getOrDefault(false)

    fun planWorkflowState(): PlanWorkflowState =
        if (usePiBackend()) piBackend?.planWorkflowState() ?: PlanWorkflowState.idle()
        else engine?.planWorkflowState ?: PlanWorkflowState.idle()

    /** `/plan`：进入只读计划模式。 */
    fun enterPlanModeFromUi(): ToolExecutionResult =
        if (usePiBackend()) pi().enterPlanModeFromUi() else engine().enterPlanModeFromUi()

    /** `/plan off`：退出计划模式并附带驳回理由。 */
    fun cancelPlanModeFromUi(feedback: String): ToolExecutionResult =
        if (usePiBackend()) pi().cancelPlanModeFromUi(feedback) else engine().cancelPlanModeFromUi(feedback)


    fun estimateContextTokens(): Int = runCatching {
        if (usePiBackend()) piBackend?.estimateContextTokens() ?: 0 else engine?.estimateContextTokens() ?: 0
    }.getOrDefault(0)

    fun estimateSessionContextTokens(sessionId: String): Int = runCatching {
        if (usePiBackend()) pi().estimateContextTokensForSession(sessionId)
        else engine?.estimateContextTokens() ?: 0
    }.getOrDefault(0)

    /** 当前会话的标识（`sessions.db` 里的 id）。引擎尚未配置或还没写过内容时为 null。 */
    fun sessionId(): String? = runCatching {
        if (usePiBackend()) piBackend?.sessionId() else engine?.sessionId
    }.getOrNull()

    /** 内存里这份引擎历史的规模 —— **只给诊断日志用**。 */
    fun memoryFootprint(): Pair<Int, Long> = runCatching {
        if (usePiBackend()) return@runCatching 0 to -1L
        val current = engine ?: return@runCatching 0 to -1L
        current.messageCount() to current.historyChars()
    }.getOrDefault(0 to -1L)

    fun hasMeasuredContextUsage(): Boolean =
        runCatching {
            if (usePiBackend()) piBackend?.hasMeasuredContextUsage() ?: false
            else engine?.hasMeasuredContextUsage() ?: false
        }.getOrDefault(false)

    fun contextWindowTokens(): Int = sessionConfig?.contextWindowTokens ?: 0

    /** 引擎当前实际使用的模型名。 */
    fun configuredModel(): String = sessionConfig?.model.orEmpty().ifEmpty { "（引擎未配置）" }

    /** 引擎当前的系统提示词。 */
    fun configuredSystemPrompt(): String = sessionConfig?.customSystemPrompt.orEmpty()

    /** 引擎侧当前是否允许联网搜索。 */
    fun configuredWebSearchEnabled(): Boolean = sessionConfig?.webSearchEnabled == true

    /** 引擎当前的动态角色卡内容。 */
    fun configuredRoleCard(): String = sessionConfig?.roleCard.orEmpty()

    /** 手动触发一次上下文压缩（`/compact`）。 */
    fun compactContext(instructions: String): Result<String> = runCatching {
        check(!isBusy()) { "任务正在运行，完成后再压缩上下文" }
        if (usePiBackend()) {
            pi().compactContext(instructions.trim())
        } else {
            val engine = engine ?: error("引擎尚未初始化")
            engine.compactContext(instructions.trim())
        }
    }

    fun mcpStatus(): Result<List<McpServerStatus>> = runCatching {
        if (usePiBackend()) {
            parseMcpStatus(pi().mcpServers())
        } else {
            val engine = engine ?: error("引擎尚未初始化")
            parseMcpStatus(engine.mcpServers())
        }
    }

    private fun parseMcpStatus(rows: JSONArray): List<McpServerStatus> = buildList {
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val name = row.optString("server", "")
            if (name.isEmpty()) continue
            val ok = row.optBoolean("ok", false)
            add(
                McpServerStatus(
                    name = name,
                    connected = ok,
                    error = if (ok) "" else row.optString("error", "未知原因"),
                    tools = McpToolInfo.parse(row.optJSONArray("tools")),
                ),
            )
        }
    }

    /** 手动联网搜索（`/web 关键词`）。 */
    fun manualWebSearch(query: String): Result<String> = runCatching {
        val config = currentConfig()
        check(config.webSearchEnabled) { "联网搜索已关闭，可输入 /web on 开启" }
        val result = WebSearchTool().execute(config, JSONObject().put("query", query.trim()))
        if (result.isError) throw IllegalStateException(result.content)
        result.content
    }

    /** 手动读取网页（`/web fetch <url>`）。 */
    fun manualWebFetch(url: String): Result<String> = runCatching {
        val config = currentConfig()
        check(config.webSearchEnabled) { "联网搜索已关闭，可输入 /web on 开启" }
        val result = WebFetchTool().execute(config, JSONObject().put("url", url.trim()))
        if (result.isError) throw IllegalStateException(result.content)
        result.content
    }

    /** 取当前生效的会话配置。 */
    private fun currentConfig(): SessionConfig =
        sessionConfig ?: ApiSettingsStore(appContext).load()

    fun contextPercent(): Int = runCatching {
        if (usePiBackend()) piBackend?.contextPercent() ?: 0 else engine?.contextPercent() ?: 0
    }.getOrDefault(0)


    private fun queueText(delta: String) {
        if (delta.isEmpty()) return
        synchronized(deltaLock) {
            val gen = currentGeneration()
            if (pendingTextGeneration != gen) {
                pendingText.setLength(0)
                pendingTextGeneration = gen
            }
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
            gen = pendingTextGeneration
        }
        if (chunk.isEmpty() || gen != generation) return
        events.onEngineText(chunk)
    }

    /** 立即把缓冲里的文本交给界面。 */
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
        synchronized(thinkingLock) {
            if (thinkingGeneration != generation) {
                pendingThinking.setLength(0)
                thinkingGeneration = generation
            }
            pendingThinking.append(thinking)
            if (thinkingFlushPosted) return
            thinkingFlushPosted = true
        }
        main.postDelayed(flushThinking, DELTA_MERGE_MS)
    }

    override fun onToolBatchStarted(batch: ZhiCodeEngine.ToolBatch?) {
        val ids = batch?.toolCalls?.filterNotNull()?.map { it.id ?: "" } ?: return
        if (ids.isEmpty()) return
        val gen = generation
        onMainForGeneration(gen) { events.onEngineToolBatchStarted(ids) }
    }

    override fun onToolBatchCompleted(batch: ZhiCodeEngine.ToolBatch?) {
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
        val id = call?.id ?: return
        if (chunk.isNullOrEmpty()) return
        synchronized(toolProgressLock) {
            val existing = pendingToolProgress[id]
            if (existing == null || existing.stderr != stderr) {
                pendingToolProgress[id] = ToolProgressBuffer(id, stderr, StringBuilder(chunk), elapsedMs)
            } else {
                existing.chunk.append(chunk)
                existing.elapsedMs = maxOf(existing.elapsedMs, elapsedMs)
            }
            if (toolProgressFlushPosted) return
            toolProgressFlushPosted = true
        }
        main.postDelayed(flushToolProgress, TOOL_PROGRESS_MERGE_MS)
    }

    private fun flushPendingThinking() {
        val text: String
        val gen: Long
        synchronized(thinkingLock) {
            text = pendingThinking.toString()
            pendingThinking.setLength(0)
            thinkingFlushPosted = false
            gen = thinkingGeneration
        }
        if (text.isNotEmpty() && gen == generation) events.onEngineThinking(text)
    }

    private fun flushPendingToolProgress() {
        val rows: List<ToolProgressBuffer>
        synchronized(toolProgressLock) {
            rows = pendingToolProgress.values.toList()
            pendingToolProgress.clear()
            toolProgressFlushPosted = false
        }
        if (rows.isEmpty()) return
        rows.forEach { row -> events.onEngineToolProgress(row.id, row.chunk.toString(), row.stderr, row.elapsedMs) }
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
                // 解析与解码是**分开**的：这里只把 base64 字符串搬进 UI 模型
                previews = result.additionalContent()?.let { readChatImageBlocks(it) } ?: emptyList(),
            )
        }
    }

    override fun onPermissionRequest(request: PermissionGate.PermissionRequest?) {
        if (request == null) return
        val gen = generation
        val call = request.call
        val name = call?.name ?: "工具"
        // 「高风险」按**命令内容**判，不按工具种类判。
        val highRisk = runCatching { RiskClassifier.isHighRisk(name, call?.input) }
            .getOrDefault(false)
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
        const val TOOL_PROGRESS_MERGE_MS = 48L
    }
}


internal fun PermissionMode.toEngineMode(): String = when (this) {
    PermissionMode.ASK -> PermissionModePolicy.DEFAULT
    PermissionMode.PLAN -> PermissionModePolicy.PLAN
    PermissionMode.BYPASS -> PermissionModePolicy.BYPASS
}

internal fun EffortLevel.toEngineEffort(): String = wire

internal fun engineEffortToUi(effort: String?): EffortLevel {
    val key = effort?.trim()?.lowercase().orEmpty()
    if (key == "none") return EffortLevel.OFF
    return EffortLevel.entries.firstOrNull { it.wire == key } ?: EffortLevel.HIGH
}

/** 引擎权限模式 → 界面枚举（历史 acceptEdits/auto/dontAsk 统一安全归回 ASK）。 */
internal fun engineModeToUi(mode: String?): PermissionMode = when (PermissionModePolicy.normalize(mode)) {
    PermissionModePolicy.PLAN -> PermissionMode.PLAN
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

internal fun applyEngineOverrides(config: SessionConfig, o: EngineOverrides) {
    o.permissionMode?.let { config.permissionMode = PermissionModePolicy.normalize(it) }
    o.effort?.let { if (it.isNotBlank()) config.effort = it }
    o.model?.let { if (it.isNotBlank()) config.model = it }
    o.contextWindow?.let { if (it > 0) config.contextWindowTokens = it }
    o.projectDirectory?.let { if (it.isNotBlank()) config.projectDirectory = it }
    o.visionEnabled?.let { config.visionEnabled = it }
    o.customSystemPrompt?.let { config.customSystemPrompt = it }
    // 空串也要写：用户点「恢复默认」之后，引擎侧不该继续用旧的自定义文案。
    o.initPrompt?.let { config.initPrompt = it }
    o.summaryPrompt?.let { config.summaryPrompt = it }
    o.compactPromptHead?.let { config.compactPromptHead = it }
    o.autoCompact?.let { config.autoCompact = it }
    // 界面用的是百分比整数，引擎用的是倍率；在这里换算，别把两种口径泄进引擎。
    o.autoCompactPercent?.let { config.autoCompactRatio = (it.coerceIn(50, 100)) / 100.0 }
    o.webSearchEnabled?.let { config.webSearchEnabled = it }
    o.webSearchProvider?.let { if (it.isNotBlank()) config.webSearchProvider = it }
    o.webSearchMaxResults?.let { if (it > 0) config.webSearchMaxResults = it }
    o.webTimeoutSec?.let { if (it > 0) config.webTimeoutMs = it * 1000 }
    o.webSearchApiKey?.let { if (it.isNotBlank()) config.webSearchApiKey = it }
    o.webSearchBaseUrl?.let { config.webSearchBaseUrl = it }
    // 空串也要写：用户在设置里清掉地址/选项之后，引擎侧不该继续用旧值。
    o.webSearchServiceConfig?.let { config.webSearchServiceConfig = it }
    o.rootExecutionEnabled?.let { config.rootExecutionEnabled = it }
    o.shizukuExecutionEnabled?.let { config.shizukuExecutionEnabled = it }
    o.forcedKeepAliveEnabled?.let { config.forcedKeepAliveEnabled = it }
    o.maxConcurrentSubagents?.let { config.maxConcurrentSubagents = it.coerceIn(1, 8) }
    o.roleCard?.let { config.roleCard = it }
}

internal fun readEngineSettings(store: ApiSettingsStore): EngineOverrides? =
    runCatching { store.load() }.getOrNull()?.let { config ->
        EngineOverrides(
            permissionMode = PermissionModePolicy.normalize(config.permissionMode),
            effort = config.effort,
            contextWindow = config.contextWindowTokens,
            projectDirectory = config.projectDirectory,
            customSystemPrompt = config.customSystemPrompt,
            initPrompt = config.initPrompt,
            summaryPrompt = config.summaryPrompt,
            compactPromptHead = config.compactPromptHead,
            visionEnabled = config.visionEnabled,
            autoCompact = config.autoCompact,
            autoCompactPercent = kotlin.math.round(config.autoCompactRatio * 100.0).toInt().coerceIn(50, 100),
            webSearchEnabled = config.webSearchEnabled,
            webSearchProvider = config.webSearchProvider,
            webSearchMaxResults = config.webSearchMaxResults,
            webTimeoutSec = config.webTimeoutMs / 1000,
            webSearchApiKey = config.webSearchApiKey,
            webSearchBaseUrl = config.webSearchBaseUrl,
            webSearchServiceConfig = config.webSearchServiceConfig,
            rootExecutionEnabled = config.rootExecutionEnabled,
            shizukuExecutionEnabled = config.shizukuExecutionEnabled,
            forcedKeepAliveEnabled = config.forcedKeepAliveEnabled,
            maxConcurrentSubagents = config.maxConcurrentSubagents.coerceIn(1, 8),
        )
    }
