package com.zhizhu.zhicode.pi

import android.content.Context
import com.termux.app.zhicode.core.PermissionModePolicy
import com.termux.app.zhicode.mcp.McpRuntime
import com.termux.app.zhicode.model.PlanWorkflowState
import com.termux.app.zhicode.model.SessionConfig
import com.termux.app.zhicode.model.ToolCall
import com.termux.app.zhicode.model.ToolExecutionResult
import com.termux.app.zhicode.storage.SessionStore
import com.zhizhu.zhicode.ContainerPaths
import com.zhizhu.zhicode.ProotCommand
import com.zhizhu.zhicode.bridge.AndroidHostBridge
import com.zhizhu.zhicode.compose.model.ChatImage
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * 通过 PRoot 启动与驱动 Pi coding agent (`pi --mode rpc`)，并把流式事件、工具卡片与权限确认适配给界面。
 */
internal class PiRpcBackend(
    context: Context,
    private val sink: Sink,
) {
    interface Sink {
        fun onSessionStarted(model: String)
        fun onTextDelta(delta: String)
        fun onThinkingDelta(delta: String)
        fun onToolBatchStarted(toolIds: List<String>)
        fun onToolUse(call: ToolCall)
        fun onToolProgress(toolId: String, chunk: String, isError: Boolean, elapsedMs: Long)
        fun onToolResult(
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
        fun onToolBatchCompleted(toolIds: List<String>)
        fun onPermissionRequest(requestId: String, toolName: String, summary: String, highRisk: Boolean)
        fun onHostPermissionClosed(requestId: String)
        fun onResponseRetry()
        fun onQueuedPromptApplied()
        fun onUsage(inputTokens: Long, outputTokens: Long)
        fun onStatus(status: String)
        fun onTurnComplete(stopReason: String)
        fun onError(message: String, error: Throwable?)
    }

    private val appContext = context.applicationContext
    private val hostBridge by lazy { AndroidHostBridge(appContext) }
    private val hostToolCalls = PiHostToolCalls()
    private var hostTurn = 0L
    private var cancelledHostProcess: Process? = null
    private val cmdSeq = AtomicLong(0L)
    private val busyFlag = AtomicBoolean(false)
    private val shutdownFlag = AtomicBoolean(false)

    private val ioExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "pi-rpc-io").apply { isDaemon = true }
    }
    private val hostToolExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "pi-host-tool").apply { isDaemon = true }
    }

    private val pendingRpcResponses = ConcurrentHashMap<String, CompletableFuture<JSONObject>>()
    private val activeTools = ConcurrentHashMap<String, ToolCall>()
    private val activeToolStartMs = ConcurrentHashMap<String, Long>()
    private val stderrTail = ArrayDeque<String>(STDERR_TAIL_LIMIT)

    private val processLock = Any()
    private val stdinLock = Any()
    private val pendingAssistantLock = Any()

    @Volatile
    private var process: Process? = null

    @Volatile
    private var stdinStream: OutputStream? = null

    @Volatile
    private var currentConfig: SessionConfig = SessionConfig()

    @Volatile
    private var activeProcessEnvKey: String = ""

    @Volatile
    private var activeProviderKey: String = PiConfigSync.PROVIDER_ID

    @Volatile
    private var activeModelId: String = ""

    @Volatile
    private var appliedProviderKey: String = ""

    @Volatile
    private var appliedModelId: String = ""

    @Volatile
    private var appliedThinkingLevel: String = ""

    @Volatile
    private var needsRestartForEnvChange: Boolean = false

    @Volatile
    private var sessionStore: SessionStore? = null

    @Volatile
    private var piSessionFile: String = ""

    @Volatile
    private var lastMeasuredContextTokens: Long = -1L

    @Volatile
    private var sawTurnError: Boolean = false

    @Volatile
    private var turnStartedAtElapsedMs: Long = 0L

    @Volatile
    private var lastAssistantDurationMs: Long = -1L

    private val pendingAssistantText = StringBuilder()
    private val pendingAssistantThinking = StringBuilder()
    private val pendingAssistantToolUses = mutableListOf<JSONObject>()

    fun isBusy(): Boolean = busyFlag.get()

    fun sessionId(): String? = sessionStore?.sessionId

    fun hasMeasuredContextUsage(): Boolean = lastMeasuredContextTokens > 0L

    fun estimateContextTokens(): Int = if (lastMeasuredContextTokens > 0L) lastMeasuredContextTokens.toInt() else 0

    fun estimateContextTokensForSession(sessionId: String): Int {
        if (sessionId.isBlank()) return 0
        if (sessionStore?.sessionId == sessionId && lastMeasuredContextTokens > 0L) {
            return lastMeasuredContextTokens.toInt()
        }
        val targetFile = findPiSessionFile(sessionId)
        if (targetFile.isEmpty()) return 0
        val hostFile = File(ContainerPaths.guestToHost(targetFile).absolutePath)
        return estimateContextTokensFromSessionFile(hostFile)
    }

    fun contextPercent(): Int {
        val window = maxOf(1, currentConfig.contextWindowTokens)
        val used = estimateContextTokens()
        return if (used <= 0) 0 else ((used * 100L) / window).toInt().coerceIn(0, 100)
    }

    fun planWorkflowState(): PlanWorkflowState = currentConfig.planWorkflowState

    fun configure(config: SessionConfig) {
        val prev = currentConfig
        val next = config.copy()
        next.permissionMode = PermissionModePolicy.normalize(next.permissionMode)
        val prevPlan = prev.planWorkflowState
        if (prevPlan != null && (prevPlan.isPlanning || prevPlan.isAwaitingApproval)) {
            next.planWorkflowState = prevPlan.copy()
        }
        currentConfig = next
        val synced = PiConfigSync.syncToContainer(appContext, next)
        val envKey = processRestartKey(next, synced)
        val alive = process?.isAlive == true
        val providerOrModelChanged = synced.providerId != activeProviderKey || synced.modelId != activeModelId
        activeProviderKey = synced.providerId
        activeModelId = synced.modelId
        if (alive && envKey != activeProcessEnvKey) {
            if (!busyFlag.get()) {
                needsRestartForEnvChange = false
                ioExecutor.execute { stopProcessQuietly() }
            } else {
                needsRestartForEnvChange = true
            }
            return
        }
        if (alive) {
            ioExecutor.execute {
                if (process?.isAlive == true && !busyFlag.get() && (needsRestartForEnvChange || activeProcessEnvKey != envKey)) {
                    stopProcessQuietly()
                    return@execute
                }
                if (synced.modelId.isNotEmpty() && (appliedProviderKey != synced.providerId || appliedModelId != synced.modelId || providerOrModelChanged || prev.model != next.model)) {
                    val ok = runCatching {
                        applyModelAndThinkingLocked(synced.providerId, synced.modelId, synced.thinkingLevel, throwOnFailure = false)
                    }.getOrDefault(false)
                    if (!ok && !busyFlag.get()) {
                        stopProcessQuietly()
                        return@execute
                    }
                } else if (appliedThinkingLevel != synced.thinkingLevel || prev.effort != next.effort) {
                    runCatching {
                        val thinkResp = sendCommandBlocking(
                            JSONObject()
                                .put("type", "set_thinking_level")
                                .put("level", synced.thinkingLevel),
                            CMD_TIMEOUT_MS,
                        )
                        if (thinkResp.optBoolean("success", false)) {
                            appliedThinkingLevel = synced.thinkingLevel
                        }
                    }
                }
                if (prev.autoCompact != next.autoCompact) {
                    runCatching {
                        sendCommandAsync(
                            JSONObject()
                                .put("type", "set_auto_compaction")
                                .put("enabled", next.autoCompact),
                        )
                    }
                }
            }
        }
    }

    fun sendPrompt(prompt: String, extraContent: JSONArray?) {
        val config = currentConfig
        val images = extractPromptImages(extraContent, config.visionEnabled)
        if (prompt.isBlank() && images.isEmpty()) {
            sink.onError("请输入消息或附加图片", null)
            return
        }
        if (config.model.isBlank()) {
            sink.onError("请先在设置中配置模型名称", null)
            return
        }
        if (config.baseUrl.isBlank()) {
            sink.onError("请先在设置中配置 API 地址", null)
            return
        }

        sawTurnError = false
        lastAssistantDurationMs = -1L
        turnStartedAtElapsedMs = android.os.SystemClock.elapsedRealtime()
        clearPendingAssistant()
        val turn = synchronized(hostToolCalls) { ++hostTurn }
        busyFlag.set(true)
        sink.onSessionStarted(config.model)

        ioExecutor.execute {
            try {
                ensureSessionStore(config)
                recordUserMessage(prompt, images)
                val synced = PiConfigSync.syncToContainer(appContext, config)
                val cancelledProcess = synchronized(hostToolCalls) { cancelledHostProcess }
                if (process?.isAlive == true && (process === cancelledProcess || needsRestartForEnvChange ||
                        activeProcessEnvKey != processRestartKey(config, synced))
                ) {
                    stopProcessQuietly()
                }
                ensureProcessStarted()
                if (appliedProviderKey != synced.providerId || appliedModelId != synced.modelId || appliedThinkingLevel != synced.thinkingLevel) {
                    val ok = runCatching {
                        applyModelAndThinkingLocked(synced.providerId, synced.modelId, synced.thinkingLevel, throwOnFailure = false)
                    }.getOrDefault(false)
                    if (!ok) {
                        stopProcessQuietly()
                        ensureProcessStarted()
                        applyModelAndThinkingLocked(synced.providerId, synced.modelId, synced.thinkingLevel, throwOnFailure = true)
                    }
                }

                val cmd = JSONObject()
                    .put("type", "prompt")
                    .put("message", prompt)
                if (images.isNotEmpty()) {
                    val arr = JSONArray()
                    for (img in images) {
                        arr.put(JSONObject().put("type", "image").put("data", img.data).put("mimeType", img.mimeType))
                    }
                    cmd.put("images", arr)
                }

                synchronized(hostToolCalls) {
                    if (hostTurn != turn) return@execute
                    hostToolCalls.beginTurn()
                }
                val resp = sendCommandBlocking(cmd, CMD_TIMEOUT_MS)
                if (!resp.optBoolean("success", false)) {
                    hostToolCalls.endTurn()
                    busyFlag.set(false)
                    sink.onError(resp.optString("error", "Pi 未接受请求"), null)
                    return@execute
                }
                val disposition = resp.optJSONObject("data")?.optString("disposition", "") ?: ""
                if (disposition == "handled") {
                    hostToolCalls.endTurn()
                    busyFlag.set(false)
                    sink.onTurnComplete("end_turn")
                }
            } catch (t: Throwable) {
                hostToolCalls.endTurn()
                busyFlag.set(false)
                sink.onError("启动或调用 Pi 失败：${t.message ?: t.javaClass.simpleName}", t)
            }
        }
    }

    fun steer(prompt: String, extraContent: JSONArray?, @Suppress("UNUSED_PARAMETER") messageId: String): Boolean {
        if (!busyFlag.get() || process?.isAlive != true) return false
        val images = extractPromptImages(extraContent, currentConfig.visionEnabled)
        ioExecutor.execute {
            recordUserMessage(prompt, images)
            val cmd = JSONObject()
                .put("type", "steer")
                .put("message", prompt)
            if (images.isNotEmpty()) {
                val arr = JSONArray()
                for (img in images) {
                    arr.put(JSONObject().put("type", "image").put("data", img.data).put("mimeType", img.mimeType))
                }
                cmd.put("images", arr)
            }
            runCatching {
                val resp = sendCommandBlocking(cmd, CMD_TIMEOUT_MS)
                if (resp.optBoolean("success", false)) {
                    sink.onQueuedPromptApplied()
                }
            }
        }
        return true
    }

    fun cancel() {
        cancelHostTools()
        busyFlag.set(false)
        turnStartedAtElapsedMs = 0L
        lastAssistantDurationMs = -1L
        activeToolStartMs.clear()
        if (process?.isAlive != true) return
        ioExecutor.execute {
            if (process?.isAlive != true) return@execute
            runCatching {
                sendCommandAsync(JSONObject().put("type", "clear_queue"))
                sendCommandAsync(JSONObject().put("type", "abort"))
            }
        }
    }

    fun resetConversation() {
        cancelHostTools()
        busyFlag.set(false)
        sessionStore?.flush()
        sessionStore = null
        piSessionFile = ""
        lastMeasuredContextTokens = -1L
        activeTools.clear()
        clearPendingAssistant()
        if (process?.isAlive == true) {
            ioExecutor.execute {
                if (process?.isAlive == true) {
                    runCatching { sendCommandBlocking(JSONObject().put("type", "clear_queue"), CMD_TIMEOUT_MS) }
                    runCatching { sendCommandBlocking(JSONObject().put("type", "abort"), CMD_TIMEOUT_MS) }
                    runCatching { sendCommandBlocking(JSONObject().put("type", "new_session"), CMD_TIMEOUT_MS) }
                }
            }
        }
    }

    fun resumeConversation(sessionId: String) {
        cancel()
        val store = SessionStore.resume(sessionId)
        sessionStore = store
        val targetFile = findPiSessionFile(sessionId)
        piSessionFile = targetFile
        val hostFile = if (targetFile.isNotEmpty()) File(ContainerPaths.guestToHost(targetFile).absolutePath) else null
        val fileEstimated = if (hostFile != null && hostFile.isFile) estimateContextTokensFromSessionFile(hostFile) else 0
        lastMeasuredContextTokens = if (fileEstimated > 0) fileEstimated.toLong() else -1L
        val synced = PiConfigSync.syncToContainer(appContext, currentConfig)
        if (process?.isAlive == true && (needsRestartForEnvChange || activeProcessEnvKey != processRestartKey(currentConfig, synced))) {
            stopProcessQuietly()
        }
        if (hostFile != null && hostFile.isFile) {
            ensureProcessStarted(recordInitialSessionFile = false)
            val resp = sendCommandBlocking(
                JSONObject().put("type", "switch_session").put("sessionPath", targetFile),
                CMD_TIMEOUT_MS,
            )
            if (!resp.optBoolean("success", false)) {
                throw IllegalStateException(resp.optString("error", "恢复 Pi 会话失败"))
            }
            piSessionFile = targetFile
            applyModelAndThinkingLocked(synced.providerId, synced.modelId, synced.thinkingLevel, throwOnFailure = true)
            val statsResp = runCatching {
                sendCommandBlocking(JSONObject().put("type", "get_session_stats"), CMD_TIMEOUT_MS)
            }.getOrNull()
            val contextUsage = statsResp?.optJSONObject("data")?.optJSONObject("contextUsage")
            if (contextUsage != null && !contextUsage.isNull("tokens")) {
                val statsTokens = contextUsage.optLong("tokens", -1L)
                if (statsTokens > 0L) {
                    lastMeasuredContextTokens = statsTokens
                }
            }
        } else if (process?.isAlive == true) {
            runCatching { sendCommandBlocking(JSONObject().put("type", "new_session"), CMD_TIMEOUT_MS) }
            runCatching { applyModelAndThinkingLocked(synced.providerId, synced.modelId, synced.thinkingLevel, throwOnFailure = false) }
        }
    }

    private fun applyModelAndThinkingLocked(
        providerId: String,
        modelId: String,
        thinkingLevel: String,
        throwOnFailure: Boolean = true,
    ): Boolean {
        if (modelId.isNotEmpty()) {
            val modelResp = sendCommandBlocking(
                JSONObject()
                    .put("type", "set_model")
                    .put("provider", providerId)
                    .put("modelId", modelId),
                CMD_TIMEOUT_MS,
            )
            if (!modelResp.optBoolean("success", false)) {
                val err = modelResp.optString("error", "未知错误")
                if (throwOnFailure) {
                    throw IllegalStateException("切换模型失败（$providerId/$modelId）：$err")
                }
                return false
            }
            appliedProviderKey = providerId
            appliedModelId = modelId
        }
        if (thinkingLevel.isNotEmpty()) {
            val thinkResp = sendCommandBlocking(
                JSONObject()
                    .put("type", "set_thinking_level")
                    .put("level", thinkingLevel),
                CMD_TIMEOUT_MS,
            )
            if (thinkResp.optBoolean("success", false)) {
                appliedThinkingLevel = thinkingLevel
            }
        }
        return true
    }

    fun respondPermission(requestId: String, allow: Boolean): Boolean {
        if (requestId.startsWith("pi-host-")) return hostToolCalls.respond(requestId, allow)
        if (requestId.isBlank() || process?.isAlive != true) return false
        ioExecutor.execute {
            runCatching {
                sendRawJson(
                    JSONObject()
                        .put("type", "extension_ui_response")
                        .put("id", requestId)
                        .put("confirmed", allow),
                )
            }
        }
        return true
    }

    fun isHostPermissionPending(requestId: String): Boolean = hostToolCalls.isAwaitingApproval(requestId)

    fun cancelHostTools() {
        synchronized(hostToolCalls) {
            ++hostTurn
            if (hostToolCalls.endTurn()) cancelledHostProcess = process
        }
    }

    fun compactContext(instructions: String): String {
        check(!isBusy()) { "任务正在运行，完成后再压缩上下文" }
        ensureProcessStarted()
        val cmd = JSONObject().put("type", "compact")
        if (instructions.isNotBlank()) cmd.put("customInstructions", instructions)
        val resp = sendCommandBlocking(cmd, COMPACT_TIMEOUT_MS)
        if (!resp.optBoolean("success", false)) {
            throw IllegalStateException(resp.optString("error", "上下文压缩失败"))
        }
        val data = resp.optJSONObject("data") ?: JSONObject()
        val summary = data.optString("summary", "已完成上下文压缩")
        val after = data.optInt("estimatedTokensAfter", -1)
        val before = data.optInt("tokensBefore", -1)
        if (after > 0) lastMeasuredContextTokens = after.toLong()
        sessionStore?.appendCompaction(summary, 0, 0, before, after, "manual", currentConfig.model)
        sessionStore?.flush()
        return summary
    }

    fun mcpServers(): JSONArray = McpRuntime(appContext).listServers()

    fun enterPlanModeFromUi(): ToolExecutionResult {
        val next = currentConfig.copy()
        val state = next.planWorkflowState ?: PlanWorkflowState.idle()
        if (!state.isPlanning && !state.isAwaitingApproval) {
            next.permissionModeBeforePlan = PermissionModePolicy.normalize(next.permissionMode)
            next.planWorkflowState = PlanWorkflowState.planning(next.permissionModeBeforePlan)
            configure(next)
        }
        return ToolExecutionResult.ok("已进入只读计划模式")
    }

    fun cancelPlanModeFromUi(feedback: String): ToolExecutionResult {
        val next = currentConfig.copy()
        val state = next.planWorkflowState
        if (state != null) {
            next.permissionMode = PermissionModePolicy.normalize(
                state.previousPermissionMode ?: next.permissionModeBeforePlan,
            )
            if (state.isActive) {
                next.planWorkflowState = state.cancelled(feedback)
            }
            configure(next)
        }
        val reason = feedback.trim()
        return ToolExecutionResult.ok(if (reason.isEmpty()) "已退出计划模式" else "已退出计划模式：$reason")
    }

    fun shutdown() {
        shutdownFlag.set(true)
        cancelHostTools()
        stopProcessQuietly()
        runCatching { sessionStore?.flush() }
        ioExecutor.shutdownNow()
        hostToolExecutor.shutdownNow()
    }

    // ------------------------------------------------------------------ 进程管理与协议解析

    private fun ensureProcessStarted(recordInitialSessionFile: Boolean = true) {
        synchronized(processLock) {
            val current = process
            if (current != null && current.isAlive) return

            val config = currentConfig
            val synced = PiConfigSync.syncToContainer(appContext, config)

            val proot = ProotCommand.prootBinary(appContext)
            val loader = ProotCommand.loaderBinary(appContext)
            val rootfs = ContainerPaths.rootfsDir()
            if (!(proot.isFile && loader.isFile && File(rootfs, "bin/bash").exists())) {
                throw IllegalStateException("Ubuntu 24.04 运行时尚未初始化，请先完成云端下载与安装")
            }

            val guestCwd = ContainerPaths.hostToGuest(config.projectDirectory)
            val command = listOf(
                "pi",
                "--mode", "rpc",
                "--session-dir", synced.sessionDirGuestPath,
                "-e", PiBridgeFiles.bridgeExtensionGuestPath(),
                "--provider", synced.providerId,
                "--model", synced.modelId.ifEmpty { "default" },
                "--thinking", synced.thinkingLevel,
                "--append-system-prompt", synced.appendSystemGuestPath,
            )
            val args = ProotCommand.buildArgs(appContext, guestCwd, command, synced.env, false)
            val pb = ProcessBuilder(args)
            pb.directory(rootfs)
            ProotCommand.applyHostEnv(appContext, pb.environment())

            synchronized(stderrTail) { stderrTail.clear() }
            val started = pb.start()
            process = started
            stdinStream = started.outputStream
            activeProcessEnvKey = processRestartKey(config, synced)
            needsRestartForEnvChange = false
            activeProviderKey = synced.providerId
            activeModelId = synced.modelId
            appliedProviderKey = synced.providerId
            appliedModelId = synced.modelId
            appliedThinkingLevel = synced.thinkingLevel

            startStdoutThread(started)
            startStderrThread(started)

            val stateResp = sendCommandBlocking(JSONObject().put("type", "get_state"), STARTUP_TIMEOUT_MS)
            if (!stateResp.optBoolean("success", false)) {
                stopProcessQuietly()
                throw IllegalStateException(stateResp.optString("error", "Pi 初始化失败"))
            }
            val sessionFile = stateResp.optJSONObject("data")?.optString("sessionFile", "") ?: ""
            if (recordInitialSessionFile) {
                val existingFile = resolveExistingSessionFileForRestart()
                if (existingFile.isNotEmpty()) {
                    val switchResp = sendCommandBlocking(
                        JSONObject().put("type", "switch_session").put("sessionPath", existingFile),
                        CMD_TIMEOUT_MS,
                    )
                    if (!switchResp.optBoolean("success", false)) {
                        stopProcessQuietly()
                        throw IllegalStateException(switchResp.optString("error", "恢复 Pi 会话上下文失败"))
                    }
                    piSessionFile = existingFile
                    // switch_session 会恢复历史文件中的 model_change，必须在恢复后重新强制切到当前选中模型。
                    applyModelAndThinkingLocked(synced.providerId, synced.modelId, synced.thinkingLevel, throwOnFailure = true)
                    val hostFile = File(ContainerPaths.guestToHost(existingFile).absolutePath)
                    val estimated = estimateContextTokensFromSessionFile(hostFile)
                    if (estimated > 0 && lastMeasuredContextTokens <= 0L) {
                        lastMeasuredContextTokens = estimated.toLong()
                    }
                } else if (sessionFile.isNotEmpty()) {
                    recordPiSessionFile(sessionFile, sessionStore)
                }
            }
        }
    }

    private fun stopProcessQuietly() {
        synchronized(processLock) {
            val running = process
            process = null
            clearHostProcess(running)
            val out = stdinStream
            stdinStream = null
            activeProcessEnvKey = ""
            appliedProviderKey = ""
            appliedModelId = ""
            appliedThinkingLevel = ""
            needsRestartForEnvChange = false
            runCatching { out?.close() }
            if (running != null && running.isAlive) {
                running.destroy()
                if (!running.waitFor(1500, TimeUnit.MILLISECONDS)) {
                    running.destroyForcibly()
                }
            }
            val err = IllegalStateException("Pi 进程已关闭")
            for (future in pendingRpcResponses.values) {
                future.completeExceptionally(err)
            }
            pendingRpcResponses.clear()
        }
    }

    private fun startStdoutThread(proc: Process) {
        Thread({
            try {
                readStrictJsonLines(proc.inputStream) { line ->
                    if (process === proc) handleProtocolLine(line, proc)
                }
            } catch (_: Throwable) {
                // 进程退出或流关闭
            } finally {
                onProcessExited(proc)
            }
        }, "pi-rpc-stdout").apply {
            isDaemon = true
            start()
        }
    }

    private fun startStderrThread(proc: Process) {
        Thread({
            try {
                readStrictJsonLines(proc.errorStream) { line ->
                    synchronized(stderrTail) {
                        if (stderrTail.size >= STDERR_TAIL_LIMIT) stderrTail.removeFirst()
                        stderrTail.addLast(line)
                    }
                }
            } catch (_: Throwable) {
                // ignore
            }
        }, "pi-rpc-stderr").apply {
            isDaemon = true
            start()
        }
    }

    private fun onProcessExited(exited: Process) {
        synchronized(processLock) {
            if (process !== exited) return
            process = null
            clearHostProcess(exited)
            val out = stdinStream
            stdinStream = null
            activeProcessEnvKey = ""
            appliedProviderKey = ""
            appliedModelId = ""
            appliedThinkingLevel = ""
            needsRestartForEnvChange = false
            runCatching { out?.close() }
        }
        val wasBusy = busyFlag.getAndSet(false)
        val tail = synchronized(stderrTail) { stderrTail.joinToString("\n") }
        val err = IllegalStateException(
            if (tail.isNotBlank()) "Pi 进程已退出：\n$tail" else "Pi 进程意外退出",
        )
        for (future in pendingRpcResponses.values) {
            future.completeExceptionally(err)
        }
        pendingRpcResponses.clear()
        if (wasBusy && !shutdownFlag.get()) {
            sink.onError(err.message ?: "Pi 进程意外退出", err)
        }
    }

    private fun clearHostProcess(exited: Process?) {
        synchronized(hostToolCalls) {
            hostToolCalls.endTurn()
            if (cancelledHostProcess === exited) cancelledHostProcess = null
        }
    }

    private fun sendCommandAsync(command: JSONObject): CompletableFuture<JSONObject> {
        val id = "rpc-${cmdSeq.incrementAndGet()}"
        val payload = JSONObject(command.toString()).put("id", id)
        val future = CompletableFuture<JSONObject>()
        pendingRpcResponses[id] = future
        try {
            sendRawJson(payload)
        } catch (t: Throwable) {
            pendingRpcResponses.remove(id)
            future.completeExceptionally(t)
        }
        return future
    }

    private fun sendCommandBlocking(command: JSONObject, timeoutMs: Long): JSONObject {
        val future = sendCommandAsync(command)
        return future.get(timeoutMs, TimeUnit.MILLISECONDS)
    }

    private fun sendRawJson(obj: JSONObject) {
        val line = (obj.toString() + "\n").toByteArray(StandardCharsets.UTF_8)
        synchronized(stdinLock) {
            val out = stdinStream ?: throw IllegalStateException("Pi stdin 未就绪")
            out.write(line)
            out.flush()
        }
    }

    private fun handleProtocolLine(line: String, source: Process) {
        if (line.isBlank()) return
        val event = runCatching { JSONObject(line) }.getOrNull() ?: return
        val type = event.optString("type", "")
        if (type == "response") {
            val id = event.optString("id", "")
            if (id.isNotEmpty()) {
                pendingRpcResponses.remove(id)?.complete(event)
            }
            return
        }

        when (type) {
            "message_update" -> handleMessageUpdate(event)
            "message_end" -> handleMessageEnd(event)
            "tool_execution_start" -> handleToolStart(event)
            "tool_execution_update" -> handleToolUpdate(event)
            "tool_execution_end" -> handleToolEnd(event)
            "extension_ui_request" -> handleExtensionUiRequest(event, source)
            "auto_retry_start" -> sink.onResponseRetry()
            "compaction_start" -> sink.onStatus("正在压缩上下文…")
            "compaction_end" -> {
                sink.onStatus("")
                val result = event.optJSONObject("result")
                val after = result?.optInt("estimatedTokensAfter", -1) ?: -1
                if (after > 0) lastMeasuredContextTokens = after.toLong()
            }
            "agent_settled" -> handleAgentSettled(event)
        }
    }

    private fun handleMessageUpdate(event: JSONObject) {
        val usage = event.optJSONObject("usage")
        if (usage != null) {
            val totalInput = usage.optLong("input", 0L) +
                usage.optLong("cacheRead", 0L) +
                usage.optLong("cacheWrite", 0L)
            val output = usage.optLong("output", 0L)
            val totalTokens = resolveContextUsageTokens(usage, totalInput, output)
            if (totalTokens > 0L) {
                lastMeasuredContextTokens = maxOf(lastMeasuredContextTokens, totalTokens)
                sink.onUsage(totalInput, output)
            }
        }
        val nested = event.optJSONObject("assistantMessageEvent") ?: return
        when (nested.optString("type", "")) {
            "text_delta" -> {
                val delta = nested.optString("delta", "")
                if (delta.isNotEmpty()) sink.onTextDelta(delta)
            }
            "thinking_delta" -> {
                val delta = nested.optString("delta", "")
                if (delta.isNotEmpty()) sink.onThinkingDelta(delta)
            }
        }
    }

    private fun handleMessageEnd(event: JSONObject) {
        val message = event.optJSONObject("message") ?: return
        if (message.optString("role", "") != "assistant") return

        val usage = message.optJSONObject("usage")
        if (usage != null) {
            val totalInput = usage.optLong("input", 0L) +
                usage.optLong("cacheRead", 0L) +
                usage.optLong("cacheWrite", 0L)
            val output = usage.optLong("output", 0L)
            val totalTokens = resolveContextUsageTokens(usage, totalInput, output)
            if (totalTokens > 0L) {
                lastMeasuredContextTokens = totalTokens
                sink.onUsage(totalInput, output)
            }
        }

        val duration = message.optLong("durationMs", -1L)
        if (duration > 0L) lastAssistantDurationMs = duration

        val content = message.optJSONArray("content")
        if (content != null) {
            synchronized(pendingAssistantLock) {
                for (i in 0 until content.length()) {
                    val block = content.optJSONObject(i) ?: continue
                    when (block.optString("type", "")) {
                        "text" -> pendingAssistantText.append(block.optString("text", ""))
                        "thinking" -> pendingAssistantThinking.append(block.optString("thinking", ""))
                        "toolCall" -> {
                            val toolId = block.optString("id", "")
                            val toolName = block.optString("name", "")
                            val args = block.optJSONObject("arguments") ?: JSONObject()
                            pendingAssistantToolUses.add(
                                JSONObject()
                                    .put("type", "tool_use")
                                    .put("id", toolId)
                                    .put("name", toolName)
                                    .put("input", args),
                            )
                        }
                    }
                }
            }
            flushPendingAssistantToStore()
        }

        val stopReason = message.optString("stopReason", "")
        if (stopReason == "error") {
            sawTurnError = true
            val errMsg = message.optString("errorMessage", "").ifBlank { "模型请求失败" }
            sink.onError(errMsg, null)
        }
    }

    private fun handleToolStart(event: JSONObject) {
        val id = event.optString("toolCallId", "")
        val rawName = event.optString("toolName", "tool")
        val args = event.optJSONObject("args") ?: JSONObject()
        val call = ToolCall(id, rawName, args)
        activeTools[id] = call
        if (rawName in AndroidHostBridge.allowedTools) hostToolCalls.register(call)
        if (id.isNotEmpty()) activeToolStartMs[id] = android.os.SystemClock.elapsedRealtime()
        sink.onToolBatchStarted(listOf(id))
        sink.onToolUse(call)
    }

    private fun handleToolUpdate(event: JSONObject) {
        val id = event.optString("toolCallId", "")
        val partial = event.optJSONObject("partialResult") ?: return
        val text = extractContentText(partial.optJSONArray("content"))
        if (text.isNotEmpty()) {
            val elapsedMs = activeToolStartMs[id]?.let { maxOf(0L, android.os.SystemClock.elapsedRealtime() - it) } ?: 0L
            sink.onToolProgress(id, text, false, elapsedMs)
        }
    }

    private fun handleToolEnd(event: JSONObject) {
        val id = event.optString("toolCallId", "")
        val rawName = event.optString("toolName", "tool")
        val call = activeTools.remove(id) ?: ToolCall(id, rawName, JSONObject())
        if (rawName in AndroidHostBridge.allowedTools) hostToolCalls.finish(id)
        activeToolStartMs.remove(id)
        val isError = event.optBoolean("isError", false)
        val resultObj = event.optJSONObject("result") ?: JSONObject()
        val contentArr = resultObj.optJSONArray("content")
        val text = extractContentText(contentArr)
        val previews = extractContentImages(contentArr)
        val details = resultObj.optJSONObject("details")
        val exitCode = details?.optInt("exitCode", if (isError) 1 else 0) ?: if (isError) 1 else 0
        val diff = details?.optString("diff", "").orEmpty()
        val (added, deleted) = countDiffLines(diff)
        val command = call.input?.optString("command", "").orEmpty()

        sink.onToolResult(id, call.name ?: rawName, isError, exitCode, text, diff, added, deleted, command, previews)
        sink.onToolBatchCompleted(listOf(id))

        recordToolResult(call, isError, text, diff, added, deleted)
    }

    private fun handleExtensionUiRequest(event: JSONObject, source: Process) {
        val reqId = event.optString("id", "")
        val method = event.optString("method", "")
        if (method == "input" && event.optString("title") == "zhicode_host_tool" && reqId.isNotEmpty()) {
            handleHostToolRequest(source, reqId, event.optString("placeholder"))
        } else if (method == "confirm" && reqId.isNotEmpty()) {
            val title = event.optString("title", "")
            val message = event.optString("message", "")
            val (toolName, highRisk) = parseExtensionConfirmTitle(title)
            sink.onPermissionRequest(reqId, toolName, message, highRisk)
        } else if ((method == "select" || method == "input" || method == "editor") && reqId.isNotEmpty()) {
            ioExecutor.execute {
                runCatching {
                    sendRawJson(JSONObject().put("type", "extension_ui_response").put("id", reqId).put("cancelled", true))
                }
            }
        }
    }

    private fun handleHostToolRequest(source: Process, rpcId: String, payload: String) {
        val args = if (payload.length <= 4 * 1024 * 1024) runCatching { JSONObject(payload) }.getOrNull() else null
        val tool = args?.optString("tool").orEmpty()
        val input = args?.optJSONObject("input")
        val request = if (input != null && tool in AndroidHostBridge.allowedTools && process === source) {
            hostToolCalls.claim(args.optString("toolCallId"), tool)
        } else null
        if (request == null || input == null) {
            ioExecutor.execute {
                sendHostToolResult(source, rpcId, AndroidHostBridge.error("无效或已处理的宿主工具调用"))
            }
            return
        }
        request.submit(hostToolExecutor) {
            val result = try {
                hostBridge.invoke(
                    tool, input, { currentConfig },
                    confirm = { summary, highRisk ->
                        try {
                            request.awaitApproval { id -> sink.onPermissionRequest(id, tool, summary, highRisk) }
                        } finally {
                            sink.onHostPermissionClosed(request.permissionId)
                        }
                    },
                    beginExecution = { process === source && request.beginExecution() },
                )
            } catch (_: InterruptedException) {
                return@submit
            } catch (e: Exception) {
                AndroidHostBridge.error(e.message ?: e.javaClass.simpleName)
            }
            if (request.isActive()) sendHostToolResult(source, rpcId, result)
        }
    }

    private fun sendHostToolResult(source: Process, rpcId: String, result: JSONObject) {
        synchronized(processLock) {
            if (process !== source) return
            runCatching {
                sendRawJson(JSONObject().put("type", "extension_ui_response").put("id", rpcId)
                    .put("value", result.toString()))
            }
        }
    }

    private fun handleAgentSettled(event: JSONObject) {
        hostToolCalls.endTurn()
        busyFlag.set(false)
        val elapsed = when {
            turnStartedAtElapsedMs > 0L -> maxOf(1L, android.os.SystemClock.elapsedRealtime() - turnStartedAtElapsedMs)
            lastAssistantDurationMs > 0L -> lastAssistantDurationMs
            else -> -1L
        }
        turnStartedAtElapsedMs = 0L
        lastAssistantDurationMs = -1L
        flushPendingAssistantToStore()
        if (elapsed > 0L) {
            sessionStore?.appendEvent("turn_elapsed", JSONObject().put("elapsed_ms", elapsed))
        }
        val settledStore = sessionStore
        settledStore?.flush()
        runCatching {
            sendCommandAsync(JSONObject().put("type", "get_state")).thenAccept { resp ->
                val sf = resp.optJSONObject("data")?.optString("sessionFile", "") ?: ""
                if (sf.isNotEmpty()) recordPiSessionFile(sf, settledStore)
            }
        }
        val aborted = event.optBoolean("aborted", false)
        if (!sawTurnError) {
            sink.onTurnComplete(if (aborted) "aborted" else "end_turn")
        }
    }

    // ------------------------------------------------------------------ 会话存储桥接

    private fun ensureSessionStore(config: SessionConfig): SessionStore {
        sessionStore?.let { return it }
        val created = SessionStore(config.projectDirectory)
        created.appendProfileBinding(config)
        sessionStore = created
        return created
    }

    private fun recordUserMessage(prompt: String, images: List<ChatImage>) {
        val store = sessionStore ?: return
        val content = JSONArray()
        if (prompt.isNotEmpty()) {
            content.put(JSONObject().put("type", "text").put("text", prompt))
        }
        for (img in images) {
            content.put(
                JSONObject()
                    .put("type", "image")
                    .put("source", JSONObject().put("type", "base64").put("media_type", img.mimeType).put("data", img.data)),
            )
        }
        store.appendMessage("user", content, "", "", "human")
    }

    private fun flushPendingAssistantToStore() {
        val store = sessionStore ?: return
        val content: JSONArray = synchronized(pendingAssistantLock) {
            if (pendingAssistantText.isEmpty() && pendingAssistantThinking.isEmpty() && pendingAssistantToolUses.isEmpty()) {
                return
            }
            JSONArray().apply {
                if (pendingAssistantThinking.isNotEmpty()) {
                    put(JSONObject().put("type", "thinking").put("thinking", pendingAssistantThinking.toString()))
                }
                if (pendingAssistantText.isNotEmpty()) {
                    put(JSONObject().put("type", "text").put("text", pendingAssistantText.toString()))
                }
                for (use in pendingAssistantToolUses) {
                    put(use)
                }
                pendingAssistantText.setLength(0)
                pendingAssistantThinking.setLength(0)
                pendingAssistantToolUses.clear()
            }
        }
        store.appendMessage("assistant", content)
    }

    private fun clearPendingAssistant() {
        synchronized(pendingAssistantLock) {
            pendingAssistantText.setLength(0)
            pendingAssistantThinking.setLength(0)
            pendingAssistantToolUses.clear()
        }
    }

    private fun recordToolResult(
        call: ToolCall,
        isError: Boolean,
        content: String,
        diff: String,
        addedLines: Int,
        deletedLines: Int,
    ) {
        val store = sessionStore ?: return
        val block = JSONObject()
            .put("type", "tool_result")
            .put("tool_use_id", call.id ?: "")
            .put("content", content)
            .put("is_error", isError)
        store.appendMessage("user", JSONArray().put(block))
        if (diff.isNotEmpty()) {
            store.appendEvent(
                "tool_diff",
                JSONObject()
                    .put("tool_use_id", call.id ?: "")
                    .put("additions", addedLines)
                    .put("deletions", deletedLines)
                    .put("diff", diff),
            )
        }
    }

    private fun resolveExistingSessionFileForRestart(): String {
        val sid = sessionStore?.sessionId.orEmpty()
        val candidates = ArrayList<String>(2)
        if (sid.isNotEmpty()) {
            val stored = findPiSessionFile(sid)
            if (stored.isNotEmpty()) candidates.add(stored)
        }
        if (piSessionFile.isNotBlank() && piSessionFile !in candidates) {
            candidates.add(piSessionFile)
        }
        return selectLatestUsableSessionFile(candidates) { guestPath ->
            File(ContainerPaths.guestToHost(guestPath).absolutePath)
        }
    }

    private fun recordPiSessionFile(sessionFile: String, targetStore: SessionStore? = sessionStore) {
        if (sessionFile.isBlank() || sessionFile == piSessionFile) return
        val store = targetStore ?: return
        if (store !== sessionStore) return
        piSessionFile = sessionFile
        store.appendEvent("pi_session", JSONObject().put("session_file", sessionFile))
        store.flush()
    }

    private fun findPiSessionFile(sessionId: String): String {
        val rows = runCatching { SessionStore.readRows(sessionId) }.getOrNull() ?: return ""
        val candidates = ArrayList<String>()
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            if (row.optString("type", "") == "pi_session") {
                val sf = row.optJSONObject("payload")?.optString("session_file", "") ?: ""
                if (sf.isNotEmpty()) candidates.add(sf)
            }
        }
        return selectLatestUsableSessionFile(candidates) { guestPath ->
            File(ContainerPaths.guestToHost(guestPath).absolutePath)
        }
    }

    private fun processRestartKey(config: SessionConfig, synced: PiConfigSync.SyncedConfig): String {
        val multiKeys = synced.env.entries
            .filter { it.key.startsWith("ZHICODE_KEY_") }
            .sortedBy { it.key }
        val envPairs = if (multiKeys.isNotEmpty()) {
            multiKeys.joinToString("\u0001") { "${it.key}=${it.value}" }
        } else {
            "ZHICODE_API_KEY=${synced.env["ZHICODE_API_KEY"].orEmpty()}"
        }
        return listOf(
            config.projectDirectory,
            config.customSystemPrompt,
            config.roleCard,
            config.rootExecutionEnabled.toString(),
            config.shizukuExecutionEnabled.toString(),
            config.webSearchEnabled.toString(),
            synced.compactionEnabled.toString(),
            synced.compactionReserveTokens.toString(),
            synced.modelsJsonHash,
            envPairs,
        ).joinToString("\u0000")
    }

    companion object {
        private const val STARTUP_TIMEOUT_MS = 30_000L
        private const val CMD_TIMEOUT_MS = 15_000L
        private const val COMPACT_TIMEOUT_MS = 120_000L
        private const val STDERR_TAIL_LIMIT = 40
        private const val ESTIMATED_IMAGE_CHARS = 4800

        internal fun resolveContextUsageTokens(usage: JSONObject, totalInput: Long, output: Long): Long {
            val total = usage.optLong("totalTokens", 0L)
            return if (total > 0L) total else (totalInput + output)
        }

        /** 从候选 Pi 会话路径中优先选取最新且磁盘存在有效对话的文件，跳过未落盘的空白临时会话。 */
        internal fun selectLatestUsableSessionFile(
            candidates: List<String>,
            hostResolver: (String) -> File,
        ): String {
            var existingFallback = ""
            for (i in candidates.lastIndex downTo 0) {
                val path = candidates[i]
                val file = hostResolver(path)
                if (!file.isFile || file.length() <= 0L) continue
                if (existingFallback.isEmpty()) existingFallback = path
                if (estimateContextTokensFromSessionFile(file) > 0) return path
            }
            return existingFallback
        }

        /** 从 Pi 会话 `.jsonl` 解析当前分支最新有效上下文 token 占用，无须启动子进程。 */
        internal fun estimateContextTokensFromSessionFile(file: File): Int {
            if (!file.isFile) return 0
            val entries = ArrayList<JSONObject>()
            val byId = HashMap<String, JSONObject>()
            runCatching {
                file.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                    lines.forEach { raw ->
                        val line = raw.trim()
                        if (line.isEmpty()) return@forEach
                        val obj = runCatching { JSONObject(line) }.getOrNull() ?: return@forEach
                        entries.add(obj)
                        val id = obj.optString("id", "")
                        if (id.isNotEmpty()) byId[id] = obj
                    }
                }
            }
            if (entries.isEmpty()) return 0

            val branch = extractActiveBranch(entries, byId)
            var lastCompactionSummaryTokens = 0L
            var hasCompactionAfterUsage = false
            var lastUsageTokens = -1L
            var trailingTokens = 0L
            var fallbackTokens = 0L

            for (entry in branch) {
                when (entry.optString("type", "")) {
                    "compaction" -> {
                        lastUsageTokens = -1L
                        trailingTokens = 0L
                        hasCompactionAfterUsage = true
                        val summary = entry.optString("summary", "")
                        lastCompactionSummaryTokens = roughCharsToTokens(summary.length.toLong())
                        fallbackTokens = lastCompactionSummaryTokens
                    }
                    "message" -> {
                        val msg = entry.optJSONObject("message") ?: continue
                        val role = msg.optString("role", "")
                        val msgTokens = estimateJsonMessageTokens(msg)
                        if (role == "assistant") {
                            val stopReason = msg.optString("stopReason", "")
                            val usage = msg.optJSONObject("usage")
                            if (stopReason != "aborted" && stopReason != "error" && usage != null) {
                                val totalInput = usage.optLong("input", 0L) +
                                    usage.optLong("cacheRead", 0L) +
                                    usage.optLong("cacheWrite", 0L)
                                val output = usage.optLong("output", 0L)
                                val total = resolveContextUsageTokens(usage, totalInput, output)
                                if (total > 0L) {
                                    lastUsageTokens = total
                                    trailingTokens = 0L
                                    hasCompactionAfterUsage = false
                                    continue
                                }
                            }
                        }
                        if (role != "system") {
                            trailingTokens += msgTokens
                            fallbackTokens += msgTokens
                        } else if (lastUsageTokens <= 0L && !hasCompactionAfterUsage) {
                            fallbackTokens += msgTokens
                        }
                    }
                }
            }

            val total = if (lastUsageTokens > 0L) lastUsageTokens + trailingTokens else fallbackTokens
            return total.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        }

        private fun extractActiveBranch(
            entries: List<JSONObject>,
            byId: Map<String, JSONObject>,
        ): List<JSONObject> {
            val last = entries.lastOrNull() ?: return emptyList()
            val leafId = last.optString("id", "")
            if (leafId.isEmpty() || !last.has("parentId")) return entries
            val reversed = ArrayList<JSONObject>()
            val visited = HashSet<String>()
            var current: JSONObject? = last
            while (current != null) {
                val id = current.optString("id", "")
                if (id.isNotEmpty() && !visited.add(id)) break
                reversed.add(current)
                val parentId = if (current.isNull("parentId")) "" else current.optString("parentId", "")
                current = if (parentId.isEmpty()) null else byId[parentId]
            }
            reversed.reverse()
            return reversed
        }

        private fun estimateJsonMessageTokens(message: JSONObject): Long {
            var chars = 0L
            when (message.optString("role", "")) {
                "assistant" -> {
                    val content = message.optJSONArray("content")
                    if (content != null) {
                        for (i in 0 until content.length()) {
                            val block = content.optJSONObject(i) ?: continue
                            when (block.optString("type", "")) {
                                "text" -> chars += block.optString("text", "").length
                                "thinking" -> chars += block.optString("thinking", "").length
                                "toolCall" -> {
                                    chars += block.optString("name", "").length
                                    chars += block.optJSONObject("arguments")?.toString()?.length ?: 0
                                }
                            }
                        }
                    }
                }
                "user", "toolResult", "custom", "bashExecution" -> {
                    chars += estimateAnyContentChars(message.opt("content"))
                    chars += message.optString("command", "").length
                    chars += message.optString("output", "").length
                }
                "system" -> {
                    chars += estimateAnyContentChars(message.opt("content"))
                    val sections = message.optJSONObject("sections")
                    if (sections != null) {
                        val keys = sections.keys()
                        while (keys.hasNext()) {
                            chars += sections.optString(keys.next(), "").length
                        }
                    }
                }
            }
            return roughCharsToTokens(chars)
        }

        private fun estimateAnyContentChars(content: Any?): Long = when (content) {
            is String -> content.length.toLong()
            is JSONArray -> estimateArrayChars(content)
            else -> 0L
        }

        private fun estimateArrayChars(content: JSONArray): Long {
            var chars = 0L
            for (i in 0 until content.length()) {
                val block = content.optJSONObject(i) ?: continue
                when (block.optString("type", "")) {
                    "text" -> chars += block.optString("text", "").length
                    "image" -> chars += ESTIMATED_IMAGE_CHARS
                }
            }
            return chars
        }

        private fun roughCharsToTokens(chars: Long): Long = if (chars <= 0L) 0L else (chars + 3L) / 4L

        /**
         * 严格按 0x0A (`\n`) 拆行读取 JSONL，绝不用 `BufferedReader.readLine()`（会错误切割字符串内的 `U+2028`/`U+2029`）。
         */
        internal fun readStrictJsonLines(input: InputStream, onLine: (String) -> Unit) {
            val lineBuf = ByteArrayOutputStream(1024)
            val chunk = ByteArray(8192)
            while (true) {
                val n = input.read(chunk)
                if (n < 0) break
                for (i in 0 until n) {
                    val b = chunk[i].toInt() and 0xFF
                    if (b == 0x0A) {
                        val bytes = lineBuf.toByteArray()
                        val len = if (bytes.isNotEmpty() && bytes[bytes.size - 1] == 0x0D.toByte()) {
                            bytes.size - 1
                        } else {
                            bytes.size
                        }
                        if (len > 0) {
                            onLine(String(bytes, 0, len, StandardCharsets.UTF_8))
                        }
                        lineBuf.reset()
                    } else {
                        lineBuf.write(b)
                    }
                }
            }
            if (lineBuf.size() > 0) {
                val bytes = lineBuf.toByteArray()
                val len = if (bytes.isNotEmpty() && bytes[bytes.size - 1] == 0x0D.toByte()) {
                    bytes.size - 1
                } else {
                    bytes.size
                }
                if (len > 0) {
                    onLine(String(bytes, 0, len, StandardCharsets.UTF_8))
                }
            }
        }

        internal fun parseExtensionConfirmTitle(title: String): Pair<String, Boolean> {
            if (!title.startsWith("zhi-perm:")) return (title.ifBlank { "Tool" }) to false
            val parts = title.split(':')
            val name = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: "Tool"
            val highRisk = parts.getOrNull(2) == "1"
            return name to highRisk
        }

        internal fun extractContentText(content: JSONArray?): String {
            if (content == null || content.length() == 0) return ""
            return buildString {
                for (i in 0 until content.length()) {
                    val item = content.optJSONObject(i) ?: continue
                    if (item.optString("type", "") == "text") {
                        val text = item.optString("text", "")
                        if (text.isNotEmpty()) {
                            if (isNotEmpty()) append('\n')
                            append(text)
                        }
                    }
                }
            }
        }

        internal fun extractContentImages(content: JSONArray?): List<ChatImage> {
            if (content == null || content.length() == 0) return emptyList()
            val out = mutableListOf<ChatImage>()
            for (i in 0 until content.length()) {
                val item = content.optJSONObject(i) ?: continue
                if (item.optString("type", "") != "image") continue
                val data = item.optString("data", "")
                val mime = item.optString("mimeType", "image/png")
                if (data.isNotEmpty()) out.add(ChatImage(data = data, mimeType = mime, name = "image.png"))
            }
            return out
        }

        internal fun extractPromptImages(extraContent: JSONArray?, visionEnabled: Boolean): List<ChatImage> {
            if (!visionEnabled || extraContent == null || extraContent.length() == 0) return emptyList()
            val out = mutableListOf<ChatImage>()
            for (i in 0 until extraContent.length()) {
                val block = extraContent.optJSONObject(i) ?: continue
                if (block.optString("type", "") != "image") continue
                val src = block.optJSONObject("source") ?: continue
                val data = src.optString("data", "")
                val mime = src.optString("media_type", "image/png")
                val name = block.optString("name", "image.png")
                if (data.isNotEmpty()) out.add(ChatImage(data = data, mimeType = mime, name = name))
            }
            return out
        }

        internal fun countDiffLines(diff: String): Pair<Int, Int> {
            if (diff.isEmpty()) return 0 to 0
            var added = 0
            var deleted = 0
            for (line in diff.split('\n')) {
                if (line.startsWith("+") && !line.startsWith("+++")) added++
                else if (line.startsWith("-") && !line.startsWith("---")) deleted++
            }
            return added to deleted
        }
    }
}
