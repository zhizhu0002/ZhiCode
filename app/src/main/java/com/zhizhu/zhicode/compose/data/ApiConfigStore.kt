package com.zhizhu.zhicode.compose.data

import android.content.Context
import com.zhizhu.zhicode.compose.model.ApiConfigState
import com.zhizhu.zhicode.compose.model.ApiProfile
import com.zhizhu.zhicode.compose.model.ApiProfileDraft
import com.zhizhu.zhicode.compose.model.ApiProtocol
import com.zhizhu.zhicode.pi.PiCatalogAndPresets
import com.zhizhu.zhicode.pi.PiModelConfig
import com.zhizhu.zhicode.pi.sanitizePiProviderKey
import com.termux.app.zhicode.model.ApiProfile as EngineProfile
import com.termux.app.zhicode.storage.ApiSettingsStore

/** API 配置的读写适配层。 */
internal object ApiConfigStore {

    fun read(context: Context): ApiConfigState {
        val store = ApiSettingsStore(context)
        val profiles = runCatching { store.getProfiles() }.getOrDefault(emptyList())
        val activeId = runCatching { store.getActiveProfileId() }.getOrDefault("")
        return ApiConfigState(
            profiles = profiles.map { it.toUi() },
            activeId = if (activeId.isBlank()) profiles.firstOrNull()?.id.orEmpty() else activeId,
        )
    }

    /** 保存（新增或编辑）。[ApiProfileDraft.isEditing] 决定是否替换密钥。 */
    fun save(context: Context, draft: ApiProfileDraft): Result<String> = runCatching {
        val store = ApiSettingsStore(context)
        val normalizedModels = normalizeModelsForDraft(draft)
        val defaultModel = draft.model.trim().ifEmpty { normalizedModels.firstOrNull()?.id.orEmpty() }
        val target = EngineProfile().also {
            if (!draft.id.isNullOrBlank()) it.id = draft.id
            it.name = draft.name.trim()
            it.protocol = draft.protocol.toEngineProtocol()
            it.baseUrl = draft.baseUrl.trim()
            it.defaultModel = defaultModel
            it.extraHeaders = draft.extraHeaders.trim()
            it.providerKey = sanitizePiProviderKey(
                draft.providerKey.ifBlank { draft.name },
                fallback = "provider",
            )
            it.compatJson = draft.compatJson.trim()
            it.modelsJson = PiModelConfig.encodeList(normalizedModels)
        }
        val replaceKey = !draft.isEditing || draft.apiKey.isNotBlank()
        val saved = store.saveProfile(target, draft.apiKey, replaceKey)
        saved.id
    }

    private fun normalizeModelsForDraft(draft: ApiProfileDraft): List<PiModelConfig> {
        val list = draft.models.toMutableList()
        val defaultId = draft.model.trim()
        if (defaultId.isNotEmpty() && list.none { it.id == defaultId }) {
            list.add(0, PiCatalogAndPresets.inferModelConfig(defaultId, defaultId, draft.protocol.toPiFormat()))
        }
        return list.distinctBy { it.id }
    }

    /** 只改当前生效配置的默认模型（若列表尚无此模型则自动按目录元数据补入）。 */
    fun setDefaultModel(context: Context, model: String): Result<String> = runCatching {
        require(model.isNotBlank()) { "模型名不能为空" }
        val trimmed = model.trim()
        val store = ApiSettingsStore(context)
        val profile = store.getActiveProfile() ?: error("没有可用的 API 配置")
        val existingModels = PiModelConfig.decodeList(profile.modelsJson).toMutableList()
        if (existingModels.none { it.id == trimmed }) {
            existingModels.add(
                0,
                PiCatalogAndPresets.inferModelConfig(
                    id = trimmed,
                    displayName = trimmed,
                    api = profile.protocol.toUiProtocol().toPiFormat(),
                ),
            )
        }
        profile.defaultModel = trimmed
        profile.modelsJson = PiModelConfig.encodeList(existingModels)
        store.saveProfile(profile, "", false)
        store.selectProfile(profile.id)
        profile.id
    }

    fun select(context: Context, profileId: String): Result<Unit> = runCatching {
        ApiSettingsStore(context).selectProfile(profileId)
    }

    fun delete(context: Context, profileId: String): Result<Unit> = runCatching {
        ApiSettingsStore(context).deleteProfile(profileId)
    }

    data class Active(val profileId: String, val name: String, val model: String, val baseUrl: String, val protocol: String, val hasKey: Boolean)

    fun active(context: Context): Active? = runCatching {
        val store = ApiSettingsStore(context)
        val profile = store.getActiveProfile() ?: return@runCatching null
        val config = store.load()
        Active(
            profileId = profile.id,
            name = profile.name,
            model = profile.defaultModel.ifBlank { "未设置" },
            baseUrl = profile.baseUrl,
            protocol = profile.protocol,
            hasKey = config.apiKey.isNotBlank(),
        )
    }.getOrNull()

    // ------------------------------------------------------------------

    fun activeProfile(context: Context): ApiProfile? = runCatching {
        val store = ApiSettingsStore(context)
        store.getActiveProfile()?.toUi()
    }.getOrNull()

    private fun EngineProfile.toUi(): ApiProfile {
        val uiProto = protocol.toUiProtocol()
        val decodedModels = PiModelConfig.decodeList(modelsJson)
        val effectiveModels = if (decodedModels.isNotEmpty() || defaultModel.isBlank()) {
            decodedModels
        } else {
            listOf(PiCatalogAndPresets.inferModelConfig(defaultModel, defaultModel, uiProto.toPiFormat()))
        }
        return ApiProfile(
            id = id,
            name = name,
            protocol = uiProto,
            baseUrl = baseUrl,
            // 密钥**故意不回填**：界面上不该出现已保存的密钥。
            apiKey = "",
            model = defaultModel.ifBlank { effectiveModels.firstOrNull()?.id.orEmpty() },
            extraHeaders = extraHeaders,
            providerKey = providerKey,
            compatJson = compatJson,
            models = effectiveModels,
        )
    }

    private fun String.toUiProtocol(): ApiProtocol = when (lowercase()) {
        "anthropic", "anthropic-messages" -> ApiProtocol.ANTHROPIC
        "openai-chat", "openai-compatible", "openai-completions" -> ApiProtocol.OPENAI_CHAT
        "google-generative-ai", "gemini" -> ApiProtocol.GOOGLE_GENERATIVE_AI
        "bedrock-converse-stream", "bedrock" -> ApiProtocol.BEDROCK_CONVERSE_STREAM
        "mistral-conversations", "mistral" -> ApiProtocol.MISTRAL_CONVERSATIONS
        "codex-responses", "openai-codex-responses" -> ApiProtocol.CODEX_RESPONSES
        "zcode" -> ApiProtocol.ZCODE
        "debug-scripted" -> ApiProtocol.DEBUG_SCRIPTED
        else -> ApiProtocol.OPENAI_RESPONSES
    }

    private fun ApiProtocol.toEngineProtocol(): String = when (this) {
        ApiProtocol.OPENAI_RESPONSES -> "openai-responses"
        ApiProtocol.OPENAI_CHAT -> "openai-chat"
        ApiProtocol.ANTHROPIC -> "anthropic"
        ApiProtocol.GOOGLE_GENERATIVE_AI -> "google-generative-ai"
        ApiProtocol.BEDROCK_CONVERSE_STREAM -> "bedrock-converse-stream"
        ApiProtocol.MISTRAL_CONVERSATIONS -> "mistral-conversations"
        ApiProtocol.CODEX_RESPONSES -> "codex-responses"
        ApiProtocol.ZCODE -> "zcode"
        ApiProtocol.DEBUG_SCRIPTED -> "debug-scripted"
    }
}
