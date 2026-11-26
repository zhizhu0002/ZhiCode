package com.zhizhu.zhicode.pi

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Pi 原生传输协议与兼容协议枚举。 */
enum class PiApiFormat(
    val wire: String,
    val label: String,
    val piApi: String = wire,
    val debugOnly: Boolean = false,
) {
    OPENAI_COMPLETIONS("openai-completions", "OpenAI Chat Completions"),
    OPENAI_RESPONSES("openai-responses", "OpenAI Responses"),
    ANTHROPIC_MESSAGES("anthropic-messages", "Anthropic Messages"),
    GOOGLE_GENERATIVE_AI("google-generative-ai", "Google Generative AI"),
    BEDROCK_CONVERSE_STREAM("bedrock-converse-stream", "Amazon Bedrock"),
    MISTRAL_CONVERSATIONS("mistral-conversations", "Mistral"),
    OPENAI_CODEX_RESPONSES("openai-codex-responses", "Codex Responses"),
    ZCODE("zcode", "ZCode", piApi = "anthropic-messages"),
    DEBUG_SCRIPTED("debug-scripted", "调试 · 本地模拟（无网络）", debugOnly = true);

    companion object {
        fun selectable(includeDebug: Boolean): List<PiApiFormat> =
            entries.filter { includeDebug || !it.debugOnly }

        /** 兼容旧版 ZhiCode 协议名与 Pi 原生协议名。 */
        fun fromWire(raw: String?): PiApiFormat = when (raw?.trim()?.lowercase(Locale.US)) {
            "openai-completions", "openai-chat", "openai-compatible" -> OPENAI_COMPLETIONS
            "openai-responses" -> OPENAI_RESPONSES
            "anthropic-messages", "anthropic" -> ANTHROPIC_MESSAGES
            "google-generative-ai", "gemini" -> GOOGLE_GENERATIVE_AI
            "bedrock-converse-stream", "bedrock" -> BEDROCK_CONVERSE_STREAM
            "mistral-conversations", "mistral" -> MISTRAL_CONVERSATIONS
            "openai-codex-responses", "codex-responses" -> OPENAI_CODEX_RESPONSES
            "zcode" -> ZCODE
            "debug-scripted" -> DEBUG_SCRIPTED
            else -> OPENAI_RESPONSES
        }
    }
}

/** Pi 原生全量推理强度档位（按从弱到强顺序排列）。 */
enum class PiThinkingLevel(val id: String) {
    OFF("off"),
    MINIMAL("minimal"),
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    XHIGH("xhigh"),
    MAX("max");

    companion object {
        fun fromId(raw: String?): PiThinkingLevel = when (raw?.trim()?.lowercase(Locale.US)) {
            "off", "none" -> OFF
            "minimal" -> MINIMAL
            "low" -> LOW
            "medium" -> MEDIUM
            "xhigh" -> XHIGH
            "max" -> MAX
            else -> HIGH
        }
    }
}

/**
 * 单个模型的能力与 thinkingLevelMap 配置。
 *
 * `thinkingLevelMap` 遵循 Pi 语义：
 * - 键缺失：使用 Pi 默认行为（`xhigh`/`max` 默认隐藏，其余默认开启）
 * - 值为 `null`：显式禁用该档位
 * - 值为非空字符串：开启该档位并发送指定映射值
 */
data class PiModelConfig(
    val id: String,
    val name: String = id,
    val reasoning: Boolean = true,
    val supportsImage: Boolean = true,
    val contextWindow: Int = DEFAULT_CONTEXT_WINDOW,
    val maxTokens: Int = DEFAULT_MAX_TOKENS,
    val thinkingLevelMap: Map<PiThinkingLevel, String?> = emptyMap(),
    val compatJson: String = "",
) {
    fun inputList(): List<String> = if (supportsImage) listOf("text", "image") else listOf("text")

    fun toStorageJson(): JSONObject = JSONObject().apply {
        put("id", id.trim())
        put("name", name.trim().ifEmpty { id.trim() })
        put("reasoning", reasoning)
        put("input", JSONArray(inputList()))
        put("contextWindow", maxOf(MIN_CONTEXT_WINDOW, contextWindow))
        put("maxTokens", maxOf(MIN_MAX_TOKENS, maxTokens))
        if (thinkingLevelMap.isNotEmpty()) {
            val mapObj = JSONObject()
            for ((level, mapped) in thinkingLevelMap) {
                if (mapped == null) mapObj.put(level.id, JSONObject.NULL)
                else mapObj.put(level.id, mapped)
            }
            put("thinkingLevelMap", mapObj)
        }
        val compat = parseJsonObjectOrNull(compatJson)
        if (compat != null && compat.length() > 0) {
            put("compat", compat)
        }
    }

    companion object {
        const val DEFAULT_CONTEXT_WINDOW = 128_000
        const val DEFAULT_MAX_TOKENS = 32_768
        const val MIN_CONTEXT_WINDOW = 4_096
        const val MIN_MAX_TOKENS = 1_024

        fun fromStorageJson(obj: JSONObject): PiModelConfig? {
            val id = obj.optString("id", "").trim()
            if (id.isEmpty()) return null
            val name = obj.optString("name", id).trim().ifEmpty { id }
            val reasoning = obj.optBoolean("reasoning", true)
            val inputArr = obj.optJSONArray("input")
            val supportsImage = if (inputArr == null) {
                true
            } else {
                (0 until inputArr.length()).any { inputArr.optString(it) == "image" }
            }
            val contextWindow = maxOf(MIN_CONTEXT_WINDOW, obj.optInt("contextWindow", DEFAULT_CONTEXT_WINDOW))
            val maxTokens = maxOf(MIN_MAX_TOKENS, obj.optInt("maxTokens", DEFAULT_MAX_TOKENS))
            val mapObj = obj.optJSONObject("thinkingLevelMap")
            val levelMap = linkedMapOf<PiThinkingLevel, String?>()
            if (mapObj != null) {
                for (level in PiThinkingLevel.entries) {
                    if (!mapObj.has(level.id)) continue
                    levelMap[level] = if (mapObj.isNull(level.id)) null else mapObj.optString(level.id, level.id)
                }
            }
            val compat = obj.optJSONObject("compat")?.toString().orEmpty()
            return PiModelConfig(
                id = id,
                name = name,
                reasoning = reasoning,
                supportsImage = supportsImage,
                contextWindow = contextWindow,
                maxTokens = maxTokens,
                thinkingLevelMap = levelMap,
                compatJson = compat,
            )
        }

        fun encodeList(models: List<PiModelConfig>): String {
            val arr = JSONArray()
            for (m in models) arr.put(m.toStorageJson())
            return arr.toString()
        }

        fun decodeList(raw: String?): List<PiModelConfig> {
            val text = raw?.trim().orEmpty()
            if (text.isEmpty()) return emptyList()
            val arr = runCatching { JSONArray(text) }.getOrNull() ?: return emptyList()
            val seen = HashSet<String>()
            return buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val parsed = fromStorageJson(obj) ?: continue
                    if (seen.add(parsed.id)) add(parsed)
                }
            }
        }
    }
}

/** 根据 Pi 原生 `getSupportedThinkingLevels` 算法计算模型支持的档位列表。 */
fun resolveSupportedThinkingLevels(model: PiModelConfig?): List<PiThinkingLevel> {
    if (model == null) {
        return listOf(
            PiThinkingLevel.OFF,
            PiThinkingLevel.MINIMAL,
            PiThinkingLevel.LOW,
            PiThinkingLevel.MEDIUM,
            PiThinkingLevel.HIGH,
        )
    }
    if (!model.reasoning) return listOf(PiThinkingLevel.OFF)
    val map = model.thinkingLevelMap
    return PiThinkingLevel.entries.filter { level ->
        if (map.containsKey(level)) {
            map[level] != null
        } else {
            level != PiThinkingLevel.XHIGH && level != PiThinkingLevel.MAX
        }
    }
}

/** 当切换模型后原档位不再被支持时，按 Pi `clampThinkingLevel` 夹紧到最近支持档位。 */
fun clampThinkingLevel(model: PiModelConfig?, requested: PiThinkingLevel): PiThinkingLevel {
    val available = resolveSupportedThinkingLevels(model)
    if (requested in available) return requested
    val all = PiThinkingLevel.entries
    val idx = all.indexOf(requested).coerceAtLeast(0)
    for (i in idx until all.size) {
        if (all[i] in available) return all[i]
    }
    for (i in idx downTo 0) {
        if (all[i] in available) return all[i]
    }
    return available.firstOrNull() ?: PiThinkingLevel.OFF
}

/** 规范化 `models.json` 中的供应商 key（仅保留小写字母、数字和连字符）。 */
fun sanitizePiProviderKey(raw: String?, fallback: String = "provider"): String {
    val cleaned = raw.orEmpty()
        .trim()
        .lowercase(Locale.US)
        .replace(Regex("[^a-z0-9-]+"), "-")
        .trim('-')
    return cleaned.ifEmpty { fallback }
}

internal fun parseJsonObjectOrNull(raw: String?): JSONObject? {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return null
    return runCatching { JSONObject(text) }.getOrNull()
}
