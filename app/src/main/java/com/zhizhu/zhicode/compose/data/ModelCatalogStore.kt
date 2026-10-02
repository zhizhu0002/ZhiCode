package com.zhizhu.zhicode.compose.data

import android.content.Context
import com.zhizhu.zhicode.compose.model.ModelOption
import com.zhizhu.zhicode.compose.model.QuotaRow
import com.termux.app.zhicode.api.ModelCatalogClient
import com.termux.app.zhicode.api.ZcodeProvider
import com.termux.app.zhicode.api.zcode.ZcodeWire
import com.termux.app.zhicode.storage.ApiSettingsStore

/**
 * 模型目录的读取适配层。
 *
 * 引擎侧的 [ModelCatalogClient] 直接吃 `SessionConfig`（里面带密钥），界面侧只应该拿到
 * 「有哪些模型可选」。所以这里负责：拿当前配置 → 请求目录 → 翻译成 [ModelOption]。
 *
 * 密钥不出这个类：`ModelCatalogClient.fetch` 只把它用在请求头上，返回值里没有密钥。
 *
 * ## 失败是常态，不是异常
 *
 * 这条链路会失败得很正常——没配密钥、协议不公开目录（Anthropic）、网络不通、服务端 4xx。
 * 原版的做法是**在面板里显示原因并允许手动输入模型名**，而不是弹错误框。
 * 所以这里返回 `Result`，由界面决定怎么呈现；[friendlyError] 负责把原始异常
 * 压成一句人能读的话（原版 `friendlyCatalogError` 的等价物）。
 */
internal object ModelCatalogStore {

    private const val MAX_ERROR_CHARS = 120

    /**
     * 一次读取的结果：可选模型 + 套餐额度。
     *
     * 做成一个对象而不是两次调用，是因为 ZCode 那边**额度与"套餐可用哪些模型"出自
     * 同一个响应**：额度接口既给出余额，也给出 `capabilities` 里的 `model:` 项。
     * 分两次调用会多一次往返，还可能拿到互相不一致的两份数据。
     *
     * 但**模型列表本身不来自那次请求**：它是内置的固定表（见下面 ZCode 分支）。
     * 其他协议没有额度，[quota] 与 [quotaError] 都是空的。
     */
    data class Catalog(
        val models: List<ModelOption> = emptyList(),
        val quota: List<QuotaRow> = emptyList(),
        /** 显示在标题下的一句补充，如「（仅套餐可用模型）」。 */
        val note: String = "",
        /**
         * 额度读取失败的原因（空表示没失败）。
         *
         * **它不是 [Result] 的失败**：额度失败时模型列表照样可用，原因只是显示在
         * 额度卡片里。把额度失败当成整次读取失败，后果就是真机上出现过的那个画面 ——
         * 一个模型都选不到，只剩一行 400。
         */
        val quotaError: String = "",
    )

    /**
     * 一行套餐额度。
     *
     * 直接用界面模型里的 `QuotaRow` 而不是在这里另定义一个：同一件事两个类型，
     * 迟早要在中间加一次转换，而转换漏字段是不报错的（界面上只是少一行数字）。
     */

    /** 用当前生效的 API 配置拉一次模型目录（含 ZCode 的额度）。 */
    fun fetch(context: Context): Result<Catalog> = runCatching {
        val config = ApiSettingsStore(context).load()

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

    /**
     * 把异常压成界面提示。
     *
     * 三种情况分开处理：无异常信息（多半是被取消）→ 给手动输入的兜底提示；
     * 信息过长 → 截断，否则一个 HTML 错误页会把弹窗撑爆；
     * 正常 → 附上"可手动输入或管理 API"，因为失败时用户最需要知道还有别的路。
     */
    fun friendlyError(error: Throwable?): String {
        val message = error?.message.orEmpty().trim()
        if (message.isEmpty()) return "无法获取模型，可手动输入"
        return clip(message) + "；可手动输入或管理 API"
    }

    /**
     * 额度失败的一句话（放在额度卡片里，**不阻断模型列表**）。
     *
     * 措辞刻意与 [friendlyError] 不同：那个说"可手动输入模型名"，而这里模型名是齐的，
     * 用户真正该做的是刷新/查配置。混用会让用户在模型列表里找一个并不缺的东西。
     */
    private fun quotaFailureText(error: Throwable?): String {
        val message = error?.message.orEmpty().trim()
        return "额度读取失败：" + clip(message.ifEmpty { "原因未知（多半是被取消）" })
    }

    /** 截断：网关回一整页 HTML 时，原文会把卡片撑爆。 */
    private fun clip(message: String): String =
        if (message.length > MAX_ERROR_CHARS) message.take(MAX_ERROR_CHARS) + "…" else message
}
