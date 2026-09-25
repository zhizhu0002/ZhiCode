package com.zhizhu.zhicode.compose.data

import android.content.Context
import com.zhizhu.zhicode.compose.model.ModelOption
import com.termux.app.zhicode.api.ModelCatalogClient
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

    /** 用当前生效的 API 配置拉一次模型目录。 */
    fun fetch(context: Context): Result<List<ModelOption>> = runCatching {
        val config = ApiSettingsStore(context).load()
        ModelCatalogClient()
            .fetch(config, ModelCatalogClient.CancellationSignal { Thread.currentThread().isInterrupted })
            .map { ModelOption(id = it.id, displayName = it.displayName) }
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
