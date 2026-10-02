package com.termux.app.zhicode.api

import com.termux.app.zhicode.api.zcode.ZcodeWire
import com.termux.app.zhicode.model.AssistantTurn
import com.termux.app.zhicode.model.SessionConfig
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * ZCode 协议的传输实现。
 *
 * ## 为什么它这么薄
 *
 * 那个网关说的是 **Anthropic Messages**：同一个路径形状、同一套事件流
 * （`message_start` / `content_block_delta` / `message_stop`）、同一份 `tools` 语义。
 * 所以这里**不重写**任何解码逻辑 —— 直接复用
 * [AnthropicMessagesProvider.StreamDecoder] 与 [AnthropicMessagesProvider.readEvent]，
 * 它们已经处理完了这个协议最容易错的部分：SSE 分帧（`data:` 可跨行）、
 * 内容块按 index 累加、终止标记判定、以及缺终止标记时报什么错。
 *
 * 本类只做两件那两家没有的事：
 *  1. 端点与请求头**全部来自用户配置**（网关地址、授权码、额外身份头）——
 *     理由见 [ZcodeWire] 的类注释；
 *  2. 报文由 [ZcodeWire.buildRequestBody] 构造（system 是缓存块数组、
 *     工具上的 `cache_control` 剥掉、`metadata.user_id` 是 JSON 字符串）。
 *
 * 放在 `api` 这个包里而不是 `api.zcode`，是为了能用上面那两个**包内可见**的成员：
 * 与其把它们放宽成 `public`（等于给所有人开一个本不该公开的口子），
 * 不如让本类待在同一个包里，一行既有代码都不用改。
 */
class ZcodeProvider : ModelProvider {

    private val requests = HttpRequestTracker()

    override fun cancelRequest(worker: Thread?) {
        requests.cancel(worker)
    }

    override fun createMessage(
        config: SessionConfig,
        systemPrompt: String?,
        messages: JSONArray?,
        tools: JSONArray?,
        listener: StreamListener?,
    ): AssistantTurn {
        val baseUrl = ApiUrlPolicy.requireBaseUrl(config)
        val apiKey = config.apiKey?.trim().orEmpty()
        if (apiKey.isEmpty()) throw IllegalStateException("请先填写 ZCode 授权码")

        // 额外头先解析：写错了要在**发请求之前**报出来，而不是等一个看不懂的 4xx。
        val extra = ZcodeWire.parseExtraHeaders(config.extraHeaders)
        extra.error?.let { throw IllegalStateException(it) }

        val model = ZcodeWire.normalizeModel(config.model)
        if (model.isEmpty()) {
            throw IllegalStateException("请先选择 ZCode 模型（GLM 系列，可用「拉取模型列表」获取）")
        }

        val endpoint = ZcodeWire.messagesEndpoint(baseUrl)
        val conn = open(endpoint, ZcodeWire.headers(apiKey, config.extraHeaders))
        val request = requests.begin(conn)
        val decoder = AnthropicMessagesProvider.StreamDecoder(listener)

        try {
            val body = ZcodeWire.buildRequestBody(
                model = model,
                maxTokens = config.maxTokens,
                systemPrompt = systemPrompt,
                messages = messages,
                tools = tools,
                deviceId = deviceId(),
            )
            write(conn, body.toString())

            val status = conn.responseCode
            request.markResponseStarted()
            if (status < 200 || status >= 300) {
                // 401/403 在这里最可能是"授权码不对"或"身份头没填对"，所以把响应体带上 ——
                // 那个网关会在正文里说清是哪一个。
                throw IllegalStateException(
                    "ZCode HTTP $status: " + truncate(readAll(conn.errorStream)),
                )
            }

            BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8)).use { reader ->
                while (AnthropicMessagesProvider.readEvent(reader, decoder)) {
                    // 循环体为空是有意的：一次调用消费一个事件，见 readEvent 的说明。
                }
            }
        } catch (failure: java.io.IOException) {
            if (Thread.currentThread().isInterrupted) throw failure
            throw StreamFailure(request.failureCode(failure), request.failureMessage(failure), failure)
        } finally {
            request.close()
        }

        if (!decoder.terminalEventSeen) {
            // 与 Anthropic 分支同一句措辞：引擎按 "stream_read_error" 子串判定可重试。
            throw IllegalStateException("stream_read_error: model stream ended before message_stop")
        }
        return decoder.toTurn()
    }

    // ---------------------------------------------------------------- 传输细节

    private fun open(endpoint: String, headers: Map<String, String>): HttpURLConnection {
        val conn = URL(endpoint).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.useCaches = false
        for ((name, value) in headers) conn.setRequestProperty(name, value)
        return conn
    }

    private fun write(conn: HttpURLConnection, body: String) {
        OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { it.write(body) }
    }

    /**
     * `metadata.user_id` 里的设备标识。
     *
     * **刻意留空**。参考实现会塞一个设备标识进去，但我们这边没有这个字段，
     * 而"没字段就现编一个"是不对的：那等于替用户向对方提交一份**伪造的设备指纹**，
     * 而且它会随实现变化，反而更难排查。
     *
     * 留空是否会被网关拒绝，目前**没有证据**（参考实现里这个字段是纯信息性的：
     * `account_uuid` 与 `session_id` 它自己也都传空）。所以先传空 ——
     * 如果实测报错说明它必需，再按报错加一个**本应用范围**的安装标识（不是硬件标识）。
     */
    private fun deviceId(): String = ""

    private fun readAll(stream: InputStream?): String {
        if (stream == null) return ""
        return stream.use { input ->
            val buffer = ByteArray(8192)
            val out = StringBuilder()
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                out.append(String(buffer, 0, read, StandardCharsets.UTF_8))
            }
            out.toString()
        }
    }

    /** 出错时把响应正文带上，但要截断 —— 有的网关会回一整页 HTML。 */
    private fun truncate(value: String): String =
        if (value.length <= MAX_ERROR_CHARS) value else value.take(MAX_ERROR_CHARS) + "…"

    private companion object {
        const val MAX_ERROR_CHARS = 12_000
    }
}
