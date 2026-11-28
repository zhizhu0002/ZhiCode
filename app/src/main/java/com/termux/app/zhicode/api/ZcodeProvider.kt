package com.termux.app.zhicode.api

import com.termux.app.zhicode.api.zcode.ZcodeDeviceMid
import com.termux.app.zhicode.api.zcode.ZcodeWire
import com.termux.app.zhicode.model.AssistantTurn
import com.termux.app.zhicode.model.SessionConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

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
        // 会话请求带三个**请求级**归因头（官方 `createModelRequestAttributionHeaders`）
        val conn = open(
            endpoint,
            ZcodeWire.headers(
                baseUrl = baseUrl,
                apiKey = apiKey,
                extraHeaders = config.extraHeaders,
                deviceMid = deviceId(),
                requestId = ZcodeWire.newRequestId(),
                traceId = ZcodeWire.newRequestId(),
                sessionType = ZcodeWire.SESSION_TYPE_MAIN,
                // 官方模型请求路径上有这个头（`model-config.ts`），而额度路径上没有。
                // 缺它正是真机上会话被拒 405/3012 的原因。
                agent = ZcodeWire.DEFAULT_AGENT,
            ),
        )
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
                today = ZcodeWire.todayStamp(),
            )
            write(conn, body.toString())

            val status = conn.responseCode
            request.markResponseStarted()
            if (status < 200 || status >= 300) {
                // 401/403 在这里最可能是"授权码不对"或"身份头没填对"，所以把响应体带上 
                val text = readAll(conn.errorStream)
                ZcodeWire.describeHttpFailure(status, text)?.let { throw IllegalStateException(it) }
                throw IllegalStateException("ZCode HTTP $status: " + truncate(text))
            }

            BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8)).use { reader ->
                while (AnthropicMessagesProvider.readEvent(reader, decoder, request)) {
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

    // ---------------------------------------------------------------- 额度

    fun fetchBalance(config: SessionConfig): ZcodeWire.BalancePayload {
        val baseUrl = ApiUrlPolicy.requireBaseUrl(config)
        val apiKey = config.apiKey?.trim().orEmpty()
        if (apiKey.isEmpty()) throw IllegalStateException("请先填写 ZCode 授权码")

        val extra = ZcodeWire.parseExtraHeaders(config.extraHeaders)
        extra.error?.let { throw IllegalStateException(it) }

        // 额度要的是 JSON（不是 SSE）—— 由 headers 的 accept 参数决定，
        // 而不是在返回的 Map 上回写（那个 Map 不保证可写）。
        val headers = ZcodeWire.headers(
            baseUrl = baseUrl,
            apiKey = apiKey,
            extraHeaders = config.extraHeaders,
            deviceMid = deviceId(),
            accept = "application/json",
        )
        val endpoint = ZcodeWire.balanceEndpoint(baseUrl, headers)

        val conn = URL(endpoint).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.useCaches = false
        conn.instanceFollowRedirects = false
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS
        for ((name, value) in headers) conn.setRequestProperty(name, value)

        val request = requests.begin(conn)
        try {
            val status = conn.responseCode
            request.markResponseStarted()
            val text = readAll(if (status in 200..299) conn.inputStream else conn.errorStream)
            if (status < 200 || status >= 300) {
                ZcodeWire.describeHttpFailure(status, text)?.let { throw IllegalStateException(it) }
                throw IllegalStateException("ZCode HTTP $status: " + truncate(text))
            }
            return ZcodeWire.parseBalancePayload(JSONObject(text))
        } catch (failure: java.io.IOException) {
            if (Thread.currentThread().isInterrupted) throw failure
            throw StreamFailure(request.failureCode(failure), request.failureMessage(failure), failure)
        } finally {
            request.close()
        }
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

    private fun deviceId(): String = ZcodeDeviceMid.get()

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
        const val CONNECT_TIMEOUT_MS = 8_000
        const val READ_TIMEOUT_MS = 12_000
    }
}
