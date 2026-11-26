package com.zhizhu.zhicode.pi

import android.content.Context
import com.termux.app.zhicode.api.zcode.ZcodeDeviceMid
import com.termux.app.zhicode.api.zcode.ZcodeWire
import com.termux.app.zhicode.core.PermissionModePolicy
import com.termux.app.zhicode.model.SessionConfig
import com.termux.app.zhicode.storage.McpConfigStore
import com.zhizhu.zhicode.ContainerPaths
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * 将 ZhiCode 会话配置同步到 Debian 容器内的 Pi 配置与运行时文件。
 */
internal object PiConfigSync {

    const val PROVIDER_ID = "zhicode"

    fun syncToContainer(context: Context, config: SessionConfig): SyncedConfig {
        PiBridgeFiles.ensureInstalled(context)

        val rootfs = ContainerPaths.rootfsDir()
        val piAgentDir = File(rootfs, "root/.pi/agent")
        val etcDir = File(rootfs, "etc/zhicode")
        piAgentDir.mkdirs()
        etcDir.mkdirs()
        cleanInaccessibleFiles(piAgentDir, "auth.json", "models-store.json")

        val deviceMid = if ("zcode".equals(config.protocol, ignoreCase = true)) ZcodeDeviceMid.get() else ""
        val modelsJson = buildModelsJson(config, deviceMid)
        writeAtomic(File(piAgentDir, "models.json"), modelsJson.toString(2))

        val mcpServers = runCatching { McpConfigStore().load() }.getOrDefault(emptyList())
        val mcpJson = buildMcpJson(mcpServers)
        writeAtomic(File(piAgentDir, "mcp.json"), mcpJson.toString(2))

        val settingsFile = File(piAgentDir, "settings.json")
        val existingSettings = runCatching {
            if (settingsFile.isFile) JSONObject(settingsFile.readText(StandardCharsets.UTF_8)) else null
        }.getOrNull()
        val compactionReserveTokens = computeCompactionReserveTokens(config)
        val settingsJson = buildSettingsJson(config, existingSettings, compactionReserveTokens)
        writeAtomic(settingsFile, settingsJson.toString(2))

        val effectiveMode = PermissionModePolicy.effective(config.permissionMode, config.planWorkflowState)
        val runtimeState = JSONObject()
            .put("permissionMode", effectiveMode)
            .put("rootEnabled", config.rootExecutionEnabled)
            .put("shizukuEnabled", config.shizukuExecutionEnabled)
            .put("webSearchEnabled", config.webSearchEnabled)
        writeAtomic(File(etcDir, "runtime-state.json"), runtimeState.toString(2))

        val projectDir = File(config.projectDirectory)
        val appendSystem = buildAppendSystemPrompt(config, projectDir)
        writeAtomic(File(etcDir, "append-system.md"), appendSystem)

        return SyncedConfig(
            providerId = PROVIDER_ID,
            modelId = config.model.trim(),
            thinkingLevel = mapEffortToThinkingLevel(config.effort),
            appendSystemGuestPath = "/etc/zhicode/append-system.md",
            sessionDirGuestPath = "/root/.pi/agent/sessions",
            compactionEnabled = config.autoCompact,
            compactionReserveTokens = compactionReserveTokens,
            env = buildProcessEnv(config, effectiveMode),
        )
    }

    internal data class SyncedConfig(
        val providerId: String,
        val modelId: String,
        val thinkingLevel: String,
        val appendSystemGuestPath: String,
        val sessionDirGuestPath: String,
        val compactionEnabled: Boolean,
        val compactionReserveTokens: Int,
        val env: Map<String, String>,
    )

    internal fun computeCompactionReserveTokens(config: SessionConfig): Int {
        val window = maxOf(4096, config.contextWindowTokens)
        val ratio = config.autoCompactRatio.coerceIn(0.50, 1.0)
        val threshold = kotlin.math.floor(window * ratio).toInt().coerceAtLeast(1024)
        return (window - threshold).coerceAtLeast(0)
    }

    internal fun buildSettingsJson(
        config: SessionConfig,
        existing: JSONObject? = null,
        reserveTokens: Int = computeCompactionReserveTokens(config),
    ): JSONObject {
        val root = if (existing != null) JSONObject(existing.toString()) else JSONObject()
        val compaction = root.optJSONObject("compaction")?.let { JSONObject(it.toString()) } ?: JSONObject()
        compaction.put("enabled", config.autoCompact)
        compaction.put("reserveTokens", reserveTokens)
        root.put("compaction", compaction)
        return root
    }

    internal fun buildProcessEnv(config: SessionConfig, effectiveMode: String): Map<String, String> = mapOf(
        "ZHICODE_API_KEY" to (config.apiKey ?: "").trim(),
        "ZHICODE_PERMISSION_MODE" to effectiveMode,
        "ZHICODE_ROOT_ENABLED" to if (config.rootExecutionEnabled) "1" else "0",
        "ZHICODE_SHIZUKU_ENABLED" to if (config.shizukuExecutionEnabled) "1" else "0",
        "ZHICODE_WEB_SEARCH_ENABLED" to if (config.webSearchEnabled) "1" else "0",
    )

    internal fun buildModelsJson(config: SessionConfig, deviceMid: String = ""): JSONObject {
        val protocol = config.protocol.trim().lowercase()
        val piApi = mapProtocolToPiApi(protocol)
        val resolvedBaseUrl = resolveProviderBaseUrl(protocol, config.baseUrl)

        val headers = JSONObject()
        if (protocol == "zcode") {
            val zHeaders = ZcodeWire.headers(
                baseUrl = config.baseUrl,
                apiKey = "",
                extraHeaders = config.extraHeaders,
                deviceMid = deviceMid.ifBlank { null },
            )
            val skip = setOf("content-type", "accept", "authorization", "anthropic-version")
            for ((k, v) in zHeaders) {
                if (k.lowercase() !in skip && v.isNotBlank()) headers.put(k, v)
            }
        } else {
            val parsed = parseExtraHeaders(config.extraHeaders)
            for ((k, v) in parsed) {
                headers.put(k, v)
            }
        }

        val compat = JSONObject()
        if (protocol == "codex-responses") {
            compat.put("supportsMaxOutputTokens", false)
        }

        val modelId = config.model.trim().ifEmpty { "default" }
        val inputTypes = JSONArray().put("text").apply {
            if (config.visionEnabled) put("image")
        }
        val cost = JSONObject()
            .put("input", 0)
            .put("output", 0)
            .put("cacheRead", 0)
            .put("cacheWrite", 0)

        val modelEntry = JSONObject()
            .put("id", modelId)
            .put("name", modelId)
            .put("api", piApi)
            .put("reasoning", true)
            .put("input", inputTypes)
            .put("cost", cost)
            .put("contextWindow", maxOf(4096, config.contextWindowTokens))
            .put("maxTokens", maxOf(1024, config.maxTokens))
        if (compat.length() > 0) modelEntry.put("compat", compat)

        val provider = JSONObject()
            .put("baseUrl", resolvedBaseUrl)
            .put("apiKey", "\$ZHICODE_API_KEY")
            .put("api", piApi)
            .put("authHeader", protocol == "zcode")
            .put("models", JSONArray().put(modelEntry))
        if (headers.length() > 0) provider.put("headers", headers)
        if (compat.length() > 0) provider.put("compat", compat)

        return JSONObject().put("providers", JSONObject().put(PROVIDER_ID, provider))
    }

    internal fun buildMcpJson(servers: List<McpConfigStore.Server>): JSONObject {
        val mcpServers = JSONObject()
        for (server in servers) {
            val name = server.name.trim()
            if (name.isEmpty()) continue
            val entry = JSONObject()
            if (!server.enabled) entry.put("enabled", false)
            entry.put("exposure", "direct")
            val type = server.type.trim().lowercase()
            if (type == "http" || type == "sse" || type == "streamable-http") {
                val url = server.url.trim()
                if (url.isEmpty()) continue
                entry.put("url", url)
                if (server.headers != null && server.headers.length() > 0) {
                    entry.put("headers", server.headers)
                }
            } else {
                val cmd = server.command.trim()
                if (cmd.isEmpty()) continue
                entry.put("command", cmd)
                if (server.args.isNotEmpty()) {
                    entry.put("args", JSONArray(server.args))
                }
                if (server.env != null && server.env.length() > 0) {
                    entry.put("env", server.env)
                }
            }
            mcpServers.put(name, entry)
        }
        return JSONObject().put("mcpServers", mcpServers)
    }

    internal fun buildAppendSystemPrompt(config: SessionConfig, projectDir: File?): String = buildString {
        appendLine("You are running inside ZhiCode on Android (Debian arm64 PRoot container).")
        appendLine("- Normal shell commands run in Debian userland (`bash`) sharing `/root` and the visible Terminal pane.")
        appendLine(
            if (config.rootExecutionEnabled) {
                "- Agent Root is ENABLED: `Root` executes Android host uid 0 commands via su."
            } else {
                "- Agent Root is DISABLED: do not attempt or claim host root access."
            },
        )
        appendLine(
            if (config.shizukuExecutionEnabled) {
                "- Shizuku is ENABLED: `Shizuku` executes Android host shell (uid 2000) commands via Shizuku."
            } else {
                "- Shizuku is DISABLED."
            },
        )
        appendLine("- Available Android host tools: `AndroidIntent`, `Sandbox`, `Debug`, `ui_canvas`" +
            if (config.webSearchEnabled) ", `WebSearch`, `WebFetch`." else ".")
        if (config.customSystemPrompt.isNotBlank()) {
            appendLine()
            appendLine("## User Instructions")
            appendLine(config.customSystemPrompt.trim())
        }
        if (config.roleCard.isNotBlank()) {
            appendLine()
            appendLine("<role_card>")
            appendLine(config.roleCard.trim())
            appendLine("</role_card>")
        }
        if (projectDir != null && projectDir.isDirectory) {
            val zhiDoc = File(projectDir, "ZhiCode.md")
            if (zhiDoc.isFile && zhiDoc.length() in 1..65536) {
                appendLine()
                appendLine("## Project ZhiCode.md")
                appendLine(zhiDoc.readText(StandardCharsets.UTF_8).trim())
            }
        }
    }.trim() + "\n"

    internal fun mapProtocolToPiApi(protocol: String): String = when (protocol.trim().lowercase()) {
        "anthropic", "zcode" -> "anthropic-messages"
        "openai-chat", "openai-compatible" -> "openai-completions"
        "codex-responses" -> "openai-codex-responses"
        else -> "openai-responses"
    }

    internal fun mapEffortToThinkingLevel(effort: String?): String = when (effort?.trim()?.lowercase()) {
        "off", "none" -> "off"
        "minimal" -> "minimal"
        "low" -> "low"
        "medium" -> "medium"
        "max" -> "max"
        "xhigh" -> "xhigh"
        else -> "high"
    }

    internal fun resolveProviderBaseUrl(protocol: String, rawBaseUrl: String?): String {
        val stripped = stripTrailingSlashes(rawBaseUrl.orEmpty())
        if (stripped.isEmpty()) return ""
        val proto = protocol.trim().lowercase()
        return when (proto) {
            "anthropic" -> {
                var base = stripped
                if (base.endsWith("/messages", ignoreCase = true)) {
                    base = stripTrailingSlashes(base.dropLast("/messages".length))
                }
                if (base.endsWith("/v1", ignoreCase = true)) {
                    base = stripTrailingSlashes(base.dropLast("/v1".length))
                }
                base
            }
            "zcode" -> {
                var base = stripped
                if (base.endsWith("/anthropic/v1/messages", ignoreCase = true)) {
                    base = stripTrailingSlashes(base.dropLast("/v1/messages".length))
                } else if (!base.endsWith("/anthropic", ignoreCase = true)) {
                    base = "$base/anthropic"
                }
                base
            }
            "codex-responses" -> stripped
            "openai-chat", "openai-compatible" -> {
                var base = stripped
                if (base.endsWith("/chat/completions", ignoreCase = true)) {
                    base = stripTrailingSlashes(base.dropLast("/chat/completions".length))
                }
                if (!base.endsWith("/v1", ignoreCase = true)) "$base/v1" else base
            }
            else -> {
                var base = stripped
                if (base.endsWith("/responses", ignoreCase = true)) {
                    base = stripTrailingSlashes(base.dropLast("/responses".length))
                }
                if (!base.endsWith("/v1", ignoreCase = true)) "$base/v1" else base
            }
        }
    }

    private fun parseExtraHeaders(raw: String?): Map<String, String> {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return emptyMap()
        return runCatching {
            val obj = JSONObject(trimmed)
            val out = linkedMapOf<String, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = obj.opt(key)?.toString()?.trim().orEmpty()
                if (key.isNotBlank() && value.isNotEmpty()) out[key] = value
            }
            out
        }.getOrDefault(emptyMap())
    }

    private fun stripTrailingSlashes(s: String): String {
        var out = s.trim()
        while (out.endsWith("/")) out = out.dropLast(1)
        return out
    }

    internal fun cleanInaccessibleFiles(dir: File, vararg names: String) {
        for (name in names) {
            val file = File(dir, name)
            if (file.exists() && (!file.canRead() || !file.canWrite())) {
                file.delete()
            }
        }
    }

    private fun writeAtomic(target: File, content: String) {
        target.parentFile?.mkdirs()
        if (target.exists() && !target.canWrite()) {
            target.delete()
        }
        val tmp = File(target.parentFile, ".${target.name}.tmp")
        tmp.writeText(content, StandardCharsets.UTF_8)
        if (!tmp.renameTo(target)) {
            target.writeText(content, StandardCharsets.UTF_8)
            tmp.delete()
        }
    }
}
