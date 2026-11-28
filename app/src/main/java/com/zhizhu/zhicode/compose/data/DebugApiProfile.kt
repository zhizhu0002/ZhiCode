package com.zhizhu.zhicode.compose.data

import android.content.Context
import com.zhizhu.zhicode.compose.BuildConfig
import com.termux.app.zhicode.model.ApiProfile as EngineProfile
import com.termux.app.zhicode.storage.ApiSettingsStore

internal object DebugApiProfile {

    /** 固定 id：重复开关时按 id 覆盖，不新增。 */
    const val ID = "zhicode-debug-scripted"

    /** 协议名必须与引擎 `ApiProtocol.DEBUG_SCRIPTED` 的线上名一致。 */
    private const val PROTOCOL = "debug-scripted"

    /** 占位地址。 */
    private const val BASE_URL = "debug://scripted"

    private const val MODEL = "debug-scripted-1"

    const val NAME = "调试 · 本地模拟（无网络）"

    /** 确保这条配置存在并切到它。 */
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

    /** 切回指定配置（关掉调试模式时用）。 */
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
