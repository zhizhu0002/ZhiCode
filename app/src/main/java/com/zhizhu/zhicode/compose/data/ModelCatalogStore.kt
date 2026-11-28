package com.zhizhu.zhicode.compose.data

import android.content.Context
import com.zhizhu.zhicode.compose.model.ModelOption
import com.zhizhu.zhicode.compose.model.QuotaRow
import com.termux.app.zhicode.api.ModelCatalogClient
import com.termux.app.zhicode.api.ZcodeProvider
import com.termux.app.zhicode.api.zcode.ZcodeWire
import com.termux.app.zhicode.storage.ApiSettingsStore

internal object ModelCatalogStore {

    private const val MAX_ERROR_CHARS = 120

    /** 一次读取的结果：可选模型 + 套餐额度。 */
    data class Catalog(
        val models: List<ModelOption> = emptyList(),
        val quota: List<QuotaRow> = emptyList(),
        /** 显示在标题下的一句补充，如「（仅套餐可用模型）」。 */
        val note: String = "",
        /** 额度读取失败的原因（空表示没失败）。 */
        val quotaError: String = "",
    )

    /** 一行套餐额度。 */

    /** 用当前生效的 API 配置拉一次模型目录（含 ZCode 的额度）。 */
    fun fetch(context: Context): Result<Catalog> =
        runCatching { ApiSettingsStore(context).load() }.mapCatching { fetchConfig(it).getOrThrow() }

    /** 按表单草稿直连拉取模型目录（无需先保存供应商）。 */
    fun fetchForDraft(
        context: Context,
        profileId: String?,
        protocolWire: String,
        baseUrl: String,
        rawApiKey: String,
        extraHeaders: String,
    ): Result<Catalog> = runCatching {
        val store = ApiSettingsStore(context)
        val config = store.load()
        config.protocol = protocolWire
        config.baseUrl = baseUrl.trim()
        config.extraHeaders = extraHeaders.trim()
        if (rawApiKey.isNotBlank()) {
            config.apiKey = rawApiKey.trim()
        } else if (!profileId.isNullOrBlank()) {
            val saved = store.getProfiles().firstOrNull { it.id == profileId }
            if (saved != null) config.apiKey = store.apiKeyForProfile(saved)
        }
        return fetchConfig(config)
    }

    private fun fetchConfig(config: com.termux.app.zhicode.model.SessionConfig): Result<Catalog> = runCatching {

        // 与协议层比对**线上名**而不是 ApiProtocol 枚举：那个枚举是 api 包的包内类型
        // （compose 层看不到它，也不该看到），而线上名本来就是公开契约。
        if (config.protocol == ZcodeWire.WIRE_NAME) {
            // 模型列表**先摆好**，再单独去读额度。
            //
            // 这两件事是分开的，理由不是"顺手"：内置模型表是**本地**的（11 项固定），
            // 额度是一次**网络**请求。把它们绑在同一个结果上，额度一失败整张列表就没了 ——
            // 真机上就是这样：额度接口回 400，面板里一个模型都选不到，而其实模型名
            // 我们本来就写着。参考实现也是分开的（列表来自内置表，额度另有卡片、
            // 失败只改那一行字）。
            val all = ZcodeWire.MODEL_NAMES.map { (id, display) ->
                ModelOption(id = id, displayName = display)
            }

            val balance = runCatching { ZcodeProvider().fetchBalance(config) }
            val payload = balance.getOrNull()
                ?: return@runCatching Catalog(
                    models = all,
                    quotaError = quotaFailureText(balance.exceptionOrNull()),
                )

            val entitled = ZcodeWire.entitledModelIds(payload)
            return@runCatching Catalog(
                models = ZcodeWire.filterEntitled(entitled).map { (id, display) ->
                    ModelOption(id = id, displayName = display)
                },
                quota = payload.rows.map { row ->
                    QuotaRow(
                        name = row.showName.ifBlank { "套餐" },
                        remaining = ZcodeWire.formatUnits(row.remainingUnits),
                        total = ZcodeWire.formatUnits(row.totalUnits),
                        fraction = ZcodeWire.quotaFraction(row),
                        resetLabel = ZcodeWire.formatCountdown(row.expiresAtSec, payload.serverTimeSec),
                    )
                },
                // 拿到套餐列表才敢说"仅套餐可用"；退回落表时那句话不成立。
                note = if (entitled.isNotEmpty()) "（仅套餐可用模型）" else "",
            )
        }

        Catalog(
            models = ModelCatalogClient()
                .fetch(config, ModelCatalogClient.CancellationSignal { Thread.currentThread().isInterrupted })
                .map { ModelOption(id = it.id, displayName = it.displayName) },
        )
    }

    /** 把异常压成界面提示。 */
    fun friendlyError(error: Throwable?): String {
        val message = error?.message.orEmpty().trim()
        if (message.isEmpty()) return "无法获取模型，可手动输入"
        return clip(message) + "；可手动输入或管理 API"
    }

    /** 额度失败的一句话（放在额度卡片里，**不阻断模型列表**）。 */
    private fun quotaFailureText(error: Throwable?): String {
        val message = error?.message.orEmpty().trim()
        return "额度读取失败：" + clip(message.ifEmpty { "原因未知（多半是被取消）" })
    }

    /** 截断：网关回一整页 HTML 时，原文会把卡片撑爆。 */
    private fun clip(message: String): String =
        if (message.length > MAX_ERROR_CHARS) message.take(MAX_ERROR_CHARS) + "…" else message
}
