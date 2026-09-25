package com.iqge.iqcode.compose.data

import android.content.Context
import com.iqge.iqcode.compose.model.ApiConfigState
import com.iqge.iqcode.compose.model.ApiProfile
import com.iqge.iqcode.compose.model.ApiProfileDraft
import com.iqge.iqcode.compose.model.ApiProtocol
import com.termux.app.iqcode.model.ApiProfile as EngineProfile
import com.termux.app.iqcode.storage.ApiSettingsStore

/**
 * API 配置的读写适配层。
 *
 * 存在的意义：引擎侧的 [ApiSettingsStore] 用的是 Java 模型（`protocol` 是字符串、
 * 模型字段叫 `defaultModel`），界面侧用的是 Kotlin 模型（`ApiProtocol` 枚举、
 * 字段叫 `model`）。两边都不该为对方改形状，所以在中间做一次翻译。
 *
 * 密钥不经过这里返回：界面永远拿不到已保存的密钥明文（[ApiProfile.apiKey] 恒为空串）。
 * 只有 [save] 会把用户新输入的密钥写进 AndroidKeyStore 加密存储。
 * 编辑已有记录时密钥框留空 = **沿用原密钥**（`replaceKey=false`），
 * 而不是把密钥清空 —— 后者会让人"改个模型名就把密钥弄丢了"。
 */
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
        val target = EngineProfile().also {
            // 草稿的 id 为 null 表示新增：交给引擎生成（ApiProfile 的无参构造会给 id）。
            if (!draft.id.isNullOrBlank()) it.id = draft.id
            it.name = draft.name.trim()
            it.protocol = draft.protocol.toEngineProtocol()
            it.baseUrl = draft.baseUrl.trim()
            it.defaultModel = draft.model.trim()
        }
        val replaceKey = !draft.isEditing || draft.apiKey.isNotBlank()
        val saved = store.saveProfile(target, draft.apiKey, replaceKey)
        saved.id
    }

    fun select(context: Context, profileId: String): Result<Unit> = runCatching {
        ApiSettingsStore(context).selectProfile(profileId)
    }

    fun delete(context: Context, profileId: String): Result<Unit> = runCatching {
        ApiSettingsStore(context).deleteProfile(profileId)
    }

    /** 官方 API 记录是给新用户直接可用的入口，不允许改名/改地址/删除。 */
    fun isOfficial(profileId: String): Boolean = ApiSettingsStore.OFFICIAL_PROFILE_ID == profileId

    /**
     * 当前生效的配置摘要，用于设置页显示与「为什么调不通」的排查。
     *
     * `hasKey` 是这里唯一需要知道的密钥信息：只说"配没配"，不暴露内容。
     */
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

    private fun EngineProfile.toUi(): ApiProfile = ApiProfile(
        id = id,
        name = name,
        protocol = protocol.toUiProtocol(),
        baseUrl = baseUrl,
        // 密钥**故意不回填**：界面上不该出现已保存的密钥。
        apiKey = "",
        model = defaultModel,
    )

    /**
     * 协议字符串 ↔ 枚举。
     *
     * 引擎里还存在 `codex-responses` / `openai-compatible` 这类历史别名
     * （见 `ModelProviders.forConfig`），映射不回显式选项时归到最接近的一项，
     * 避免把用户的旧配置显示成"未知"。
     */
    private fun String.toUiProtocol(): ApiProtocol = when (lowercase()) {
        "anthropic" -> ApiProtocol.ANTHROPIC
        "openai-chat", "openai-compatible" -> ApiProtocol.OPENAI_CHAT
        else -> ApiProtocol.OPENAI_RESPONSES
    }

    private fun ApiProtocol.toEngineProtocol(): String = when (this) {
        ApiProtocol.OPENAI_RESPONSES -> "openai-responses"
        ApiProtocol.OPENAI_CHAT -> "openai-chat"
        ApiProtocol.ANTHROPIC -> "anthropic"
    }
}
