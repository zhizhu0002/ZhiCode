package com.zhizhu.zhicode.pi

import com.termux.app.zhicode.api.zcode.ZcodeWire
import java.util.Locale

/** 供应商预设模板（自定义与 CC Switch 全部内置模板统一映射到 [PiModelConfig] 结构）。 */
data class PiProviderPreset(
    val id: String,
    val name: String,
    val providerKey: String,
    val api: PiApiFormat,
    val baseUrl: String,
    val extraHeaders: String = "",
    val compatJson: String = "",
    val models: List<PiModelConfig> = emptyList(),
    val isCustom: Boolean = false,
    val category: String = "custom",
    val icon: String = "",
    val iconColor: String = "",
    val websiteUrl: String = "",
)

/** 借鉴 CC Switch 的 Pi 模型目录、thinkingLevelMap 档案与主流供应商预设。 */
object PiCatalogAndPresets {

    private val MAP_DEEPSEEK_V4 = mapOf(
        PiThinkingLevel.MINIMAL to null,
        PiThinkingLevel.LOW to null,
        PiThinkingLevel.MEDIUM to null,
        PiThinkingLevel.HIGH to "high",
        PiThinkingLevel.MAX to "max",
    )

    private val MAP_OFF_UNSUPPORTED = mapOf(
        PiThinkingLevel.OFF to null,
    )

    private val MAP_OFF_UNSUPPORTED_XHIGH_MAX = mapOf(
        PiThinkingLevel.OFF to null,
        PiThinkingLevel.XHIGH to "xhigh",
        PiThinkingLevel.MAX to "max",
    )

    private val MAP_KIMI_3 = mapOf(
        PiThinkingLevel.OFF to null,
        PiThinkingLevel.MINIMAL to null,
        PiThinkingLevel.LOW to "low",
        PiThinkingLevel.MEDIUM to null,
        PiThinkingLevel.HIGH to "high",
        PiThinkingLevel.XHIGH to null,
        PiThinkingLevel.MAX to "max",
    )

    private val MAP_LOW_MED_HIGH_ONLY = mapOf(
        PiThinkingLevel.OFF to null,
        PiThinkingLevel.MINIMAL to null,
        PiThinkingLevel.LOW to "low",
        PiThinkingLevel.MEDIUM to "medium",
        PiThinkingLevel.HIGH to "high",
        PiThinkingLevel.XHIGH to null,
        PiThinkingLevel.MAX to null,
    )

    private val MAP_GPT5_RESPONSES = mapOf(
        PiThinkingLevel.OFF to "none",
        PiThinkingLevel.MINIMAL to null,
        PiThinkingLevel.LOW to "low",
        PiThinkingLevel.MEDIUM to "medium",
        PiThinkingLevel.HIGH to "high",
        PiThinkingLevel.XHIGH to "xhigh",
        PiThinkingLevel.MAX to "max",
    )

    private val MAP_GEMINI_LOW_HIGH = mapOf(
        PiThinkingLevel.OFF to null,
        PiThinkingLevel.MINIMAL to null,
        PiThinkingLevel.LOW to "LOW",
        PiThinkingLevel.MEDIUM to null,
        PiThinkingLevel.HIGH to "HIGH",
    )

    private val MAP_MINIMAX_M31 = mapOf(
        PiThinkingLevel.OFF to null,
        PiThinkingLevel.MINIMAL to null,
        PiThinkingLevel.LOW to "low",
        PiThinkingLevel.MEDIUM to "medium",
        PiThinkingLevel.HIGH to "high",
        PiThinkingLevel.XHIGH to "xhigh",
        PiThinkingLevel.MAX to "max",
    )

    val presets: List<PiProviderPreset> by lazy {
        val core = listOf(
            PiProviderPreset(
                id = "custom",
                name = "自定义供应商",
                providerKey = "custom",
                api = PiApiFormat.OPENAI_COMPLETIONS,
                baseUrl = "",
                isCustom = true,
                category = "custom",
            ),
            PiProviderPreset(
                id = "openai",
                name = "OpenAI",
                providerKey = "openai",
                api = PiApiFormat.OPENAI_RESPONSES,
                baseUrl = "https://api.openai.com/v1",
                category = "official",
                icon = "openai",
                websiteUrl = "https://platform.openai.com",
                models = listOf(
                    PiModelConfig("gpt-5.4", "GPT-5.4", reasoning = true, supportsImage = true, contextWindow = 1_000_000, maxTokens = 128_000, thinkingLevelMap = MAP_GPT5_RESPONSES),
                    PiModelConfig("gpt-5.4-mini", "GPT-5.4 Mini", reasoning = true, supportsImage = true, contextWindow = 400_000, maxTokens = 128_000, thinkingLevelMap = MAP_GPT5_RESPONSES),
                    PiModelConfig("o3", "o3", reasoning = true, supportsImage = true, contextWindow = 200_000, maxTokens = 100_000, thinkingLevelMap = MAP_LOW_MED_HIGH_ONLY),
                    PiModelConfig("gpt-4.1", "GPT-4.1", reasoning = false, supportsImage = true, contextWindow = 1_047_576, maxTokens = 32_768),
                ),
            ),
            PiProviderPreset(
                id = "anthropic",
                name = "Anthropic",
                providerKey = "anthropic",
                api = PiApiFormat.ANTHROPIC_MESSAGES,
                baseUrl = "https://api.anthropic.com",
                category = "official",
                icon = "anthropic",
                websiteUrl = "https://console.anthropic.com",
                models = listOf(
                    PiModelConfig("claude-sonnet-4-6", "Claude Sonnet 4.6", reasoning = true, supportsImage = true, contextWindow = 1_000_000, maxTokens = 64_000, thinkingLevelMap = MAP_OFF_UNSUPPORTED_XHIGH_MAX),
                    PiModelConfig("claude-opus-4-6", "Claude Opus 4.6", reasoning = true, supportsImage = true, contextWindow = 1_000_000, maxTokens = 128_000, thinkingLevelMap = MAP_OFF_UNSUPPORTED_XHIGH_MAX),
                    PiModelConfig("claude-haiku-4-5-20251001", "Claude Haiku 4.5", reasoning = true, supportsImage = true, contextWindow = 200_000, maxTokens = 64_000),
                ),
            ),
            PiProviderPreset(
                id = "google",
                name = "Google Gemini",
                providerKey = "google",
                api = PiApiFormat.GOOGLE_GENERATIVE_AI,
                baseUrl = "https://generativelanguage.googleapis.com/v1beta",
                category = "official",
                icon = "gemini",
                websiteUrl = "https://ai.google.dev",
                models = listOf(
                    PiModelConfig("gemini-2.5-pro", "Gemini 2.5 Pro", reasoning = true, supportsImage = true, contextWindow = 1_048_576, maxTokens = 65_536, thinkingLevelMap = MAP_GEMINI_LOW_HIGH),
                    PiModelConfig("gemini-2.5-flash", "Gemini 2.5 Flash", reasoning = true, supportsImage = true, contextWindow = 1_048_576, maxTokens = 65_536),
                ),
            ),
            PiProviderPreset(
                id = "zcode",
                name = "ZCode",
                providerKey = "zcode",
                api = PiApiFormat.ZCODE,
                baseUrl = "https://zcode.z.ai/api/v1/zcode-plan",
                extraHeaders = """{"X-ZCode-App-Version":"${ZcodeWire.DEFAULT_APP_VERSION}"}""",
                category = "cn_official",
                icon = "zhipu",
                websiteUrl = "https://z.ai",
                models = ZcodeWire.MODEL_NAMES.map { (id, label) ->
                    PiModelConfig(id = id, name = label, reasoning = true, supportsImage = true, contextWindow = 200_000, maxTokens = 32_768)
                },
            ),
        )
        val seenIds = core.mapTo(HashSet()) { it.id }
        buildList {
            addAll(core)
            for (ccs in PiCcsPresetCatalog.presets) {
                if (seenIds.add(ccs.id)) add(ccs)
            }
        }
    }

    fun presetById(id: String): PiProviderPreset? = presets.firstOrNull { it.id == id }

    /** 按供应商 Key 或名称匹配内置预设的图标标识。 */
    fun resolveIconKey(providerKey: String, name: String): String {
        val cleanKey = providerKey.trim().lowercase(Locale.US)
        val cleanName = name.trim().lowercase(Locale.US)
        if (cleanKey.isNotEmpty()) {
            presets.firstOrNull {
                !it.isCustom && (it.providerKey.equals(cleanKey, ignoreCase = true) || it.id.equals(cleanKey, ignoreCase = true))
            }?.icon?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        if (cleanName.isNotEmpty()) {
            presets.firstOrNull { !it.isCustom && it.name.equals(cleanName, ignoreCase = true) }
                ?.icon?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return ""
    }

    /** 按模型 ID 与协议推断并补全已知元数据（只补空缺或通用默认能力，方便拉取后一键导入）。 */
    fun inferModelConfig(
        id: String,
        displayName: String = id,
        api: PiApiFormat = PiApiFormat.OPENAI_COMPLETIONS,
        fallbackContextWindow: Int = PiModelConfig.DEFAULT_CONTEXT_WINDOW,
    ): PiModelConfig {
        val lower = id.trim().lowercase(Locale.US)
        val cleanName = displayName.trim().ifEmpty { id.trim() }

        // 先尝试从预设模型里精确匹配同名元数据
        val catalogHit = presets.asSequence()
            .flatMap { it.models.asSequence() }
            .firstOrNull { it.id.equals(id.trim(), ignoreCase = true) }
        if (catalogHit != null) {
            return catalogHit.copy(id = id.trim(), name = cleanName)
        }

        return when {
            lower.contains("deepseek-v4") || lower.contains("deepseek-r1") || lower.contains("reasoner") -> PiModelConfig(
                id = id.trim(),
                name = cleanName,
                reasoning = true,
                supportsImage = lower.contains("vision") || lower.contains("flash"),
                contextWindow = if (lower.contains("v4")) 1_000_000 else 128_000,
                maxTokens = if (lower.contains("v4")) 131_072 else 65_536,
                thinkingLevelMap = if (lower.contains("v4")) MAP_DEEPSEEK_V4 else emptyMap(),
            )
            lower.contains("deepseek-chat") || lower.contains("deepseek-v3") -> PiModelConfig(
                id = id.trim(),
                name = cleanName,
                reasoning = lower.contains("v3.2"),
                supportsImage = false,
                contextWindow = 128_000,
                maxTokens = 32_768,
            )
            lower.contains("claude-opus") || lower.contains("claude-sonnet") || lower.contains("claude-fable") -> PiModelConfig(
                id = id.trim(),
                name = cleanName,
                reasoning = true,
                supportsImage = true,
                contextWindow = if (lower.contains("4-6") || lower.contains("4.6") || lower.contains("4-7") || lower.contains("4.7") || lower.contains("5")) 1_000_000 else 200_000,
                maxTokens = 64_000,
                thinkingLevelMap = if (api == PiApiFormat.ANTHROPIC_MESSAGES) MAP_OFF_UNSUPPORTED_XHIGH_MAX else emptyMap(),
            )
            lower.contains("claude-haiku") -> PiModelConfig(
                id = id.trim(),
                name = cleanName,
                reasoning = lower.contains("4"),
                supportsImage = true,
                contextWindow = 200_000,
                maxTokens = 64_000,
            )
            lower.startsWith("gpt-5") || lower.contains("/gpt-5") || lower.startsWith("gpt-6") -> PiModelConfig(
                id = id.trim(),
                name = cleanName,
                reasoning = true,
                supportsImage = true,
                contextWindow = if (lower.contains("mini")) 400_000 else 1_000_000,
                maxTokens = 128_000,
                thinkingLevelMap = if (api == PiApiFormat.OPENAI_RESPONSES || api == PiApiFormat.OPENAI_CODEX_RESPONSES) MAP_GPT5_RESPONSES else MAP_LOW_MED_HIGH_ONLY,
            )
            lower.startsWith("o1") || lower.startsWith("o3") || lower.startsWith("o4") -> PiModelConfig(
                id = id.trim(),
                name = cleanName,
                reasoning = true,
                supportsImage = true,
                contextWindow = 200_000,
                maxTokens = 100_000,
                thinkingLevelMap = MAP_LOW_MED_HIGH_ONLY,
            )
            lower.startsWith("gpt-4") -> PiModelConfig(
                id = id.trim(),
                name = cleanName,
                reasoning = false,
                supportsImage = true,
                contextWindow = if (lower.contains("4.1")) 1_047_576 else 128_000,
                maxTokens = 32_768,
            )
            lower.contains("gemini") -> PiModelConfig(
                id = id.trim(),
                name = cleanName,
                reasoning = lower.contains("2.5") || lower.contains("3.") || lower.contains("thinking"),
                supportsImage = true,
                contextWindow = 1_048_576,
                maxTokens = 65_536,
                thinkingLevelMap = if (lower.contains("pro") && (lower.contains("3.") || lower.contains("2.5"))) MAP_GEMINI_LOW_HIGH else emptyMap(),
            )
            lower.contains("kimi-k3") || lower.contains("kimi-k2.5") -> PiModelConfig(
                id = id.trim(),
                name = cleanName,
                reasoning = true,
                supportsImage = true,
                contextWindow = 262_144,
                maxTokens = 32_768,
                thinkingLevelMap = if (lower.contains("k3")) MAP_KIMI_3 else emptyMap(),
            )
            lower.contains("glm-") -> PiModelConfig(
                id = id.trim(),
                name = cleanName,
                reasoning = lower.contains("glm-5") || lower.contains("4.5") || lower.contains("z1"),
                supportsImage = lower.contains("v"),
                contextWindow = 200_000,
                maxTokens = 32_768,
            )
            else -> PiModelConfig(
                id = id.trim(),
                name = cleanName,
                reasoning = true,
                supportsImage = true,
                contextWindow = maxOf(PiModelConfig.MIN_CONTEXT_WINDOW, fallbackContextWindow),
                maxTokens = PiModelConfig.DEFAULT_MAX_TOKENS,
            )
        }
    }
}
