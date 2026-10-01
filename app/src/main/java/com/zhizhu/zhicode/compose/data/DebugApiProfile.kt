package com.zhizhu.zhicode.compose.data

import android.content.Context
import com.zhizhu.zhicode.compose.BuildConfig
import com.termux.app.zhicode.model.ApiProfile as EngineProfile
import com.termux.app.zhicode.storage.ApiSettingsStore

/**
 * 「调试 · 本地模拟」这条 API 配置的**建/选/还原**。
 *
 * ## 为什么要真的写一条配置记录
 *
 * 调试模式需要的不是一个开关，而是**一条能被正常选中的 API 配置**：引擎读的是
 * `ApiSettingsStore` 里当前生效的那条记录（协议、模型、密钥），
 * ViewModel 的 `configure()` 每轮都从那里读。把脚本化传输做成一条普通记录，
 * 于是：
 *
 * - 它走的是**完全真实的**配置链路（选择、生效、会话里记下 profile_id…），
 *   调试出来的行为与真配置一致；
 * - 用户可以在「API 配置记录」里看到它、切换走、再切回来 —— 不需要为调试
 *   发明第二套"当前模型是什么"的来源；
 * - 关掉调试模式时能**切回原来那条**，不会把用户的正常配置弄丢。
 *
 * ## 幂等
 *
 * 反复开关调试模式只会重用同一条记录（id 固定），不会越攒越多。
 * 它的名字带「调试」前缀，用户一眼能认出这不是自己的配置。
 */
internal object DebugApiProfile {

    /** 固定 id：重复开关时按 id 覆盖，不新增。 */
    const val ID = "zhicode-debug-scripted"

    /** 协议名必须与引擎 `ApiProtocol.DEBUG_SCRIPTED` 的线上名一致。 */
    private const val PROTOCOL = "debug-scripted"

    /**
     * 占位地址。**刻意不是 http(s)**：
     *
     * - 脚本化传输不发请求，任何真实地址都是误导；
     * - 而且 `http://…` 会让「允许明文 HTTP」那类校验与提示出现在一条根本不出网的
     *   配置上，看起来像配置错了。
     *
     * 用 `debug://` 这个自定义 scheme，一眼能看出它不是真地址。
     */
    private const val BASE_URL = "debug://scripted"

    private const val MODEL = "debug-scripted-1"

    const val NAME = "调试 · 本地模拟（无网络）"

    /**
     * 确保这条配置存在并切到它。
     *
     * @return 切换是否成功。失败时调用方应给出提示 —— 静默失败会让人以为
     *         "调试模式打开了但发的消息还是走真模型"。
     */
    fun activate(context: Context): Result<Unit> = runCatching {
        require(BuildConfig.DEBUG) { "调试 API 只在 debug 构建可用" }
        val store = ApiSettingsStore(context)
        val profile = EngineProfile().also {
            it.id = ID
            it.name = NAME
            it.protocol = PROTOCOL
            it.baseUrl = BASE_URL
            it.defaultModel = MODEL
        }
        // 空密钥即可：脚本化传输不读密钥。用 replaceKey = true 覆盖成空串，
        // 免得之前误填过什么留在这条记录里。
        store.saveProfile(profile, "", true)
        store.selectProfile(ID)
    }

    /**
     * 切回指定配置（关掉调试模式时用）。
     *
     * @return 是否切回去了。目标不存在（用户删过）返回失败，让调用方决定怎么提示。
     */
    fun restore(context: Context, profileId: String): Result<Unit> = runCatching {
        require(profileId.isNotBlank()) { "没有可还原的配置" }
        val store = ApiSettingsStore(context)
        val exists = runCatching { store.getProfiles() }.getOrDefault(emptyList())
            .any { it != null && profileId == it.id }
        require(exists) { "原来的配置已被删除" }
        store.selectProfile(profileId)
    }

    /** 当前生效的配置是不是这条调试配置。 */
    fun isActive(context: Context): Boolean = runCatching {
        ApiSettingsStore(context).getActiveProfile()?.id == ID
    }.getOrDefault(false)
}
