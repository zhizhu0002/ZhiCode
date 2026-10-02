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
     * 做成一个对象而不是两次调用，是因为 ZCode 那边**两者出自同一个响应**：
     * 额度接口既给出余额，也给出"套餐可用哪些模型"（`capabilities` 里的
     * `model:` 项）。分两次调用会多一次往返，还可能拿到互相不一致的两份数据。
     * 其他协议没有额度，[quota] 就是空的。
     */
    data class Catalog(
        val models: List<ModelOption> = emptyList(),
        val quota: List<QuotaRow> = emptyList(),
        /** 显示在标题下的一句补充，如「（仅套餐可用模型）」。 */
        val note: String = "",
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
            // 只发一次请求，两个结果都从它来。
            val payload = ZcodeProvider().fetchBalance(config)
            val entitled = ZcodeWire.entitledModelIds(payload)
            val names = ZcodeWire.filterEntitled(entitled)
            return@runCatching Catalog(
                models = names.map { (id, display) -> ModelOption(id = id, displayName = display) },
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
        val clipped = if (message.length > MAX_ERROR_CHARS) message.take(MAX_ERROR_CHARS) + "…" else message
        return "$clipped；可手动输入或管理 API"
    }
}
