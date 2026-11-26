package com.zhizhu.zhicode.pi

import android.content.Context
import com.termux.app.zhicode.api.zcode.ZcodeDeviceMid
import com.termux.app.zhicode.api.zcode.ZcodeWire
import com.termux.app.zhicode.core.PermissionModePolicy
import com.termux.app.zhicode.model.ApiProfile
import com.termux.app.zhicode.model.SessionConfig
import com.termux.app.zhicode.storage.ApiSettingsStore
import com.termux.app.zhicode.storage.McpConfigStore
import com.zhizhu.zhicode.ContainerPaths
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * 将 ZhiCode 多供应商配置与会话参数同步到 Debian 容器内的 Pi 原生配置文件。
 */
internal object PiConfigSync {

    const val PROVIDER_ID = "zhicode"

    data class PiProviderSnapshot(
        val profileId: String,
        val key: String,
        val name: String,
        val api: PiApiFormat,
        val baseUrl: String,
        val apiKey: String,
        val extraHeaders: String,
        val compatJson: String,
        val models: List<PiModelConfig>,
        val envVarName: String,
    )

    internal data class SyncedConfig(
        val providerId: String,
        val modelId: String,
        val thinkingLevel: String,
        val appendSystemGuestPath: String,
        val sessionDirGuestPath: String,
        val compactionEnabled: Boolean,
        val compactionReserveTokens: Int,
        val modelsJsonHash: String,
        val activeModelConfig: PiModelConfig?,
        val env: Map<String, String>,
    )

    fun syncToContainer(context: Context, config: SessionConfig): SyncedConfig {
        PiBridgeFiles.ensureInstalled(context)

        val rootfs = ContainerPaths.rootfsDir()
        val piAgentDir = File(rootfs, "root/.pi/agent")
        val etcDir = File(rootfs, "etc/zhicode")
        piAgentDir.mkdirs()
        etcDir.mkdirs()
        cleanInaccessibleFiles(piAgentDir, "auth.json", "models-store.json")

        val (activeKey, snapshots) = collectProviderSnapshots(context, config)
        val hasZcode = snapshots.any { it.api == PiApiFormat.ZCODE }
        val deviceMid = if (hasZcode || "zcode".equals(config.protocol, ignoreCase = true)) {
            runCatching { ZcodeDeviceMid.get() }.getOrDefault("")
        } else {
            ""
        }
        val modelsJson = buildMultiProviderModelsJson(snapshots, deviceMid)
        val modelsText = modelsJson.toString(2)
        writeAtomic(File(piAgentDir, "models.json"), modelsText)

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

        val activeSnapshot = snapshots.firstOrNull { it.key == activeKey } ?: snapshots.firstOrNull()
        val activeModelId = config.model.trim().ifEmpty {
            activeSnapshot?.models?.firstOrNull()?.id.orEmpty()
        }
        val activeModelCfg = activeSnapshot?.models?.firstOrNull { it.id == activeModelId }
            ?: activeSnapshot?.models?.firstOrNull()

        return SyncedConfig(
            providerId = activeKey,
            modelId = activeModelId,
            thinkingLevel = mapEffortToThinkingLevel(config.effort),
            appendSystemGuestPath = "/etc/zhicode/append-system.md",
            sessionDirGuestPath = "/root/.pi/agent/sessions",
            compactionEnabled = config.autoCompact,
            compactionReserveTokens = compactionReserveTokens,
            modelsJsonHash = modelsText.hashCode().toString(),
            activeModelConfig = activeModelCfg,
            env = buildProcessEnv(config, effectiveMode, snapshots),
        )
    }

    internal fun collectProviderSnapshots(
        context: Context,
        activeConfig: SessionConfig,
    ): Pair<String, List<PiProviderSnapshot>> {
        val store = runCatching { ApiSettingsStore(context) }.getOrNull()
        val profiles = runCatching { store?.getProfiles().orEmpty() }.getOrDefault(emptyList())
        if (profiles.isEmpty()) {
            val fallback = snapshotFromSessionConfig(activeConfig)
            return fallback.key to listOf(fallback)
        }

        val usedKeys = HashSet<String>()
        var activeKey = ""
        val snapshots = profiles.mapIndexed { index, profile ->
            val uniqueKey = allocateUniqueKey(
                raw = profile.providerKey.ifBlank { profile.name },
                fallback = "provider-${index + 1}",
                used = usedKeys,
            )
            val isActive = profile.id == activeConfig.profileId ||
                (activeConfig.profileId.isBlank() && index == 0)
            if (isActive) activeKey = uniqueKey

            val format = PiApiFormat.fromWire(if (isActive && activeConfig.protocol.isNotBlank()) activeConfig.protocol else profile.protocol)
            val baseUrl = if (isActive && activeConfig.baseUrl.isNotBlank()) activeConfig.baseUrl else profile.baseUrl
            val extraHeaders = if (isActive) activeConfig.extraHeaders else profile.extraHeaders
            val key = if (isActive && !activeConfig.apiKey.isNullOrBlank()) {
                activeConfig.apiKey.trim()
            } else {
                runCatching { store?.apiKeyForProfile(profile).orEmpty() }.getOrDefault("")
            }
            val models = resolveModelsForProfile(profile, activeConfig.takeIf { isActive }, format)
            PiProviderSnapshot(
                profileId = profile.id,
                key = uniqueKey,
                name = profile.name,
                api = format,
                baseUrl = baseUrl,
                apiKey = key,
                extraHeaders = extraHeaders,
                compatJson = profile.compatJson.orEmpty(),
                models = models,
                envVarName = "ZHICODE_KEY_$index",
            )
        }
        val resolvedActiveKey = activeKey.ifEmpty { snapshots.first().key }
        return resolvedActiveKey to snapshots
    }

    private fun resolveModelsForProfile(
        profile: ApiProfile,
        activeConfig: SessionConfig?,
        format: PiApiFormat,
    ): List<PiModelConfig> {
        val decoded = PiModelConfig.decodeList(profile.modelsJson).toMutableList()
        val primaryId = (activeConfig?.model ?: profile.defaultModel).orEmpty().trim()
        if (decoded.isEmpty() && primaryId.isNotEmpty()) {
            val inferred = PiCatalogAndPresets.inferModelConfig(
                id = primaryId,
                displayName = primaryId,
                api = format,
                fallbackContextWindow = activeConfig?.contextWindowTokens ?: PiModelConfig.DEFAULT_CONTEXT_WINDOW,
            )
            decoded.add(
                inferred.copy(
                    supportsImage = activeConfig?.visionEnabled ?: inferred.supportsImage,
                    contextWindow = activeConfig?.contextWindowTokens?.takeIf { it >= PiModelConfig.MIN_CONTEXT_WINDOW }
                        ?: inferred.contextWindow,
                    maxTokens = activeConfig?.maxTokens?.takeIf { it >= PiModelConfig.MIN_MAX_TOKENS }
                        ?: inferred.maxTokens,
                ),
            )
        } else if (primaryId.isNotEmpty() && decoded.none { it.id == primaryId }) {
            decoded.add(0, PiCatalogAndPresets.inferModelConfig(primaryId, primaryId, format))
        }
        if (decoded.isEmpty()) {
            decoded.add(PiModelConfig(id = "default", name = "default"))
        }
        return decoded
    }

    private fun snapshotFromSessionConfig(config: SessionConfig): PiProviderSnapshot {
        val format = PiApiFormat.fromWire(config.protocol)
        val modelId = config.model.trim().ifEmpty { "default" }
        val inferred = PiCatalogAndPresets.inferModelConfig(
            id = modelId,
            displayName = modelId,
            api = format,
            fallbackContextWindow = config.contextWindowTokens,
        ).copy(
            supportsImage = config.visionEnabled,
            contextWindow = maxOf(PiModelConfig.MIN_CONTEXT_WINDOW, config.contextWindowTokens),
            maxTokens = maxOf(PiModelConfig.MIN_MAX_TOKENS, config.maxTokens),
        )
        return PiProviderSnapshot(
            profileId = config.profileId.orEmpty(),
            key = PROVIDER_ID,
            name = PROVIDER_ID,
            api = format,
            baseUrl = config.baseUrl.orEmpty(),
            apiKey = config.apiKey.orEmpty().trim(),
            extraHeaders = config.extraHeaders.orEmpty(),
            compatJson = "",
            models = listOf(inferred),
            envVarName = "ZHICODE_API_KEY",
        )
    }

    private fun allocateUniqueKey(raw: String, fallback: String, used: MutableSet<String>): String {
        val base = sanitizePiProviderKey(raw, fallback)
        if (used.add(base)) return base
        var idx = 2
        while (true) {
            val candidate = "$base-$idx"
            if (used.add(candidate)) return candidate
            idx++
        }
    }

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

    internal fun buildProcessEnv(
        config: SessionConfig,
        effectiveMode: String,
        snapshots: List<PiProviderSnapshot> = emptyList(),
    ): Map<String, String> {
        val env = linkedMapOf(
            "ZHICODE_API_KEY" to (config.apiKey ?: "").trim(),
            "ZHICODE_PERMISSION_MODE" to effectiveMode,
            "ZHICODE_ROOT_ENABLED" to if (config.rootExecutionEnabled) "1" else "0",
            "ZHICODE_SHIZUKU_ENABLED" to if (config.shizukuExecutionEnabled) "1" else "0",
            "ZHICODE_WEB_SEARCH_ENABLED" to if (config.webSearchEnabled) "1" else "0",
        )
        for (snapshot in snapshots) {
            if (snapshot.envVarName.isNotBlank() && snapshot.apiKey.isNotBlank()) {
                env[snapshot.envVarName] = snapshot.apiKey
            }
        }
        return env
    }

    /** 单供应商快捷构造（保留兼容既有单测）。 */
    internal fun buildModelsJson(config: SessionConfig, deviceMid: String = ""): JSONObject =
        buildMultiProviderModelsJson(listOf(snapshotFromSessionConfig(config)), deviceMid)

    /** 构建 Pi 原生多供应商 `models.json`。 */
    internal fun buildMultiProviderModelsJson(
        snapshots: List<PiProviderSnapshot>,
        deviceMid: String = "",
    ): JSONObject {
        val providers = JSONObject()
        for (snapshot in snapshots) {
            val resolvedBaseUrl = resolveProviderBaseUrl(snapshot.api.wire, snapshot.baseUrl)
            val headers = JSONObject()
            if (snapshot.api == PiApiFormat.ZCODE) {
                val zHeaders = ZcodeWire.headers(
                    baseUrl = snapshot.baseUrl,
                    apiKey = "",
                    extraHeaders = snapshot.extraHeaders,
                    deviceMid = deviceMid.ifBlank { null },
                )
                val skip = setOf("content-type", "accept", "authorization", "anthropic-version")
                for ((k, v) in zHeaders) {
                    if (k.lowercase() !in skip && v.isNotBlank()) headers.put(k, v)
                }
            } else {
                for ((k, v) in parseExtraHeaders(snapshot.extraHeaders)) {
                    headers.put(k, v)
                }
            }

            val compat = parseJsonObjectOrNull(snapshot.compatJson)?.let { JSONObject(it.toString()) } ?: JSONObject()
            if (snapshot.api == PiApiFormat.OPENAI_CODEX_RESPONSES) {
                compat.put("supportsMaxOutputTokens", false)
            }

            val cost = JSONObject()
                .put("input", 0)
                .put("output", 0)
                .put("cacheRead", 0)
                .put("cacheWrite", 0)

            val modelsArr = JSONArray()
            for (model in snapshot.models) {
                val entry = model.toStorageJson()
                entry.put("api", snapshot.api.piApi)
                entry.put("cost", JSONObject(cost.toString()))
                if (compat.length() > 0 && !entry.has("compat")) {
                    // compat 也可放 provider 级，不覆盖模型自带 compat
                }
                modelsArr.put(entry)
            }

            val provider = JSONObject()
                .put("name", snapshot.name)
                .put("baseUrl", resolvedBaseUrl)
                .put("apiKey", "\$${snapshot.envVarName}")
                .put("api", snapshot.api.piApi)
                .put("authHeader", snapshot.api == PiApiFormat.ZCODE)
                .put("models", modelsArr)
            if (headers.length() > 0) provider.put("headers", headers)
            if (compat.length() > 0) provider.put("compat", compat)
            providers.put(snapshot.key, provider)
        }
        return JSONObject().put("providers", providers)
    }

    internal fun buildMcpJson(servers: List<McpConfigStore.Server>): JSONObject {
        val mcpServers = JSONObject()
        for (server in servers) {
            val name = server.name.trim()
            if (name.isEmpty()) continue
            val entry = JSONObject()
            if (!server.enabled) entry.put("enabled", false)
            entry.put("exposure", "direct")
            if ("http".equals(server.type, ignoreCase = true) || !server.url.isNullOrBlank()) {
                entry.put("url", server.url ?: "")
                server.headers?.let { if (it.length() > 0) entry.put("headers", it) }
            } else {
                entry.put("command", server.command ?: "")
                val args = JSONArray()
                for (arg in server.args) args.put(arg)
                if (args.length() > 0) entry.put("args", args)
                server.env?.let { if (it.length() > 0) entry.put("env", it) }
            }
            mcpServers.put(name, entry)
        }
        return JSONObject().put("mcpServers", mcpServers)
    }

    internal fun buildAppendSystemPrompt(config: SessionConfig, projectDir: File): String = buildString {
        appendLine("You are running inside ZhiCode on Android (Debian arm64 PRoot container).")
        appendLine("- Normal shell commands run in Debian userland (`bash`) sharing `/root` and the visible Terminal pane.")
        appendLine("- Agent Root is ${if (config.rootExecutionEnabled) "ENABLED" else "DISABLED"}: ${if (config.rootExecutionEnabled) "privileged host actions go through `zhi-root` when explicitly needed." else "do not attempt or claim host root access."}")
        appendLine("- Shizuku is ${if (config.shizukuExecutionEnabled) "ENABLED" else "DISABLED"}.")
        appendLine("- Available Android host tools: `AndroidIntent`, `Sandbox`, `Debug`, `ui_canvas`, `WebSearch`, `WebFetch`")
        if (!config.customSystemPrompt.isNullOrBlank()) {
            appendLine()
            appendLine("<custom_instructions>")
            appendLine(config.customSystemPrompt.trim())
            appendLine("</custom_instructions>")
        }
        if (!config.roleCard.isNullOrBlank()) {
            appendLine()
            appendLine("<role_card>")
            appendLine(config.roleCard.trim())
            appendLine("</role_card>")
        }
        val zhiDoc = File(projectDir, "ZhiCode.md")
        if (zhiDoc.isFile && zhiDoc.canRead()) {
            val docText = runCatching { zhiDoc.readText(StandardCharsets.UTF_8).trim() }.getOrDefault("")
            if (docText.isNotEmpty()) {
                appendLine()
                appendLine("<project_instructions path=\"${zhiDoc.name}\">")
                appendLine(docText)
                appendLine("</project_instructions>")
            }
        }
    }.trim() + "\n"

    internal fun mapProtocolToPiApi(protocol: String): String =
        PiApiFormat.fromWire(protocol).piApi

    internal fun mapEffortToThinkingLevel(effort: String?): String =
        PiThinkingLevel.fromId(effort).id

    internal fun resolveProviderBaseUrl(protocol: String, rawBaseUrl: String?): String {
        val stripped = stripTrailingSlashes(rawBaseUrl.orEmpty())
        if (stripped.isEmpty()) return ""
        return when (PiApiFormat.fromWire(protocol)) {
            PiApiFormat.ANTHROPIC_MESSAGES -> {
                var base = stripped
                if (base.endsWith("/messages", ignoreCase = true)) {
                    base = stripTrailingSlashes(base.dropLast("/messages".length))
                }
                if (base.endsWith("/v1", ignoreCase = true)) {
                    base = stripTrailingSlashes(base.dropLast("/v1".length))
                }
                base
            }
            PiApiFormat.ZCODE -> {
                var base = stripped
                if (base.endsWith("/anthropic/v1/messages", ignoreCase = true)) {
                    base = stripTrailingSlashes(base.dropLast("/v1/messages".length))
                } else if (!base.endsWith("/anthropic", ignoreCase = true)) {
                    base = "$base/anthropic"
                }
                base
            }
            PiApiFormat.OPENAI_CODEX_RESPONSES,
            PiApiFormat.GOOGLE_GENERATIVE_AI,
            PiApiFormat.BEDROCK_CONVERSE_STREAM,
            PiApiFormat.MISTRAL_CONVERSATIONS -> stripped
            PiApiFormat.OPENAI_COMPLETIONS -> {
                var base = stripped
                if (base.endsWith("/chat/completions", ignoreCase = true)) {
                    base = stripTrailingSlashes(base.dropLast("/chat/completions".length))
                }
                if (!hasVersionSuffix(base)) "$base/v1" else base
            }
            PiApiFormat.OPENAI_RESPONSES,
            PiApiFormat.DEBUG_SCRIPTED -> {
                var base = stripped
                if (base.endsWith("/responses", ignoreCase = true)) {
                    base = stripTrailingSlashes(base.dropLast("/responses".length))
                }
                if (!hasVersionSuffix(base)) "$base/v1" else base
            }
        }
    }

    /** 判断 URL 末尾是否已包含 `/v1`、`/v4` 等版本段（如智谱 `/api/paas/v4`）。 */
    private fun hasVersionSuffix(url: String): Boolean {
        val tail = url.substringAfterLast('/', "").lowercase()
        return tail.length >= 2 && tail[0] == 'v' && tail[1].isDigit()
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
