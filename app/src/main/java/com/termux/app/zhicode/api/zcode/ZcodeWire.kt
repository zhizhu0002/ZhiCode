package com.termux.app.zhicode.api.zcode

import com.termux.app.zhicode.api.ModelDescriptor
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

object ZcodeWire {

    /** 线上名。写进设置与会话文件，是持久化契约的一部分。 */
    const val WIRE_NAME = "zcode"

    /** 与 Anthropic 相同的版本头取值 —— 这个网关说的是 Anthropic 协议。 */
    const val ANTHROPIC_VERSION = "2023-06-01"

    /** `User-Agent` 的默认值。 */
    const val DEFAULT_APP_VERSION = "3.14.3"

    /** 官方在非 macOS/Windows 上给的取值（`normalizeOsCategory`）。 */
    const val DEFAULT_OS_CATEGORY = "linux"

    /** 官方在 Linux x64 上的 `X-Platform`（`${platform}-${arch}`）。 */
    const val DEFAULT_PLATFORM = "linux-x64"

    /** 官方 `ZCODE_SOURCE_HEADERS` 里的 `X-Title` 形态：`Z Code@<source>`。 */
    const val DEFAULT_SOURCE_TITLE = "Z Code@cli"

    /** 官方 `ZCODE_ENV` 默认就是 production。 */
    const val DEFAULT_RELEASE_CHANNEL = "production"

    /** 会话请求的 `x-zcode-session-type`：主对话是 `main`（官方 `ModelRequestSessionType`）。 */
    const val SESSION_TYPE_MAIN = "main"

    const val DEFAULT_AGENT = "glm"

    /** 被风控挡回时服务端给的业务码。 */
    const val CODE_BLOCKED = 3012

    fun describeHttpFailure(status: Int, body: String?): String? {
        val json = runCatching { JSONObject(body.orEmpty()) }.getOrNull() ?: return null
        if (json.optInt("code", 0) != CODE_BLOCKED) return null
        val logId = json.optString("logid", "").trim()
        return buildString {
            append("账号被网关风控拦截（HTTP ").append(status)
            append(" / code ").append(CODE_BLOCKED).append(" unusual activity）。")
            append("这不是本应用的问题：官方客户端同样会中招（额度接口照常可用），")
            append("请求头与模型名也已逐项对齐官方源码。")
            append("需要联系网关提供方解除该账号的标记；也可以在 API 配置里换一个网关地址再试。")
            if (logId.isNotEmpty()) {
                // logid 是报障时唯一有用的东西：没有它，对方查不到这一次请求。
                append("\n报障时提供 logid：").append(logId)
            }
        }
    }

    /** 会话端点。 */
    private const val MESSAGES_PATH = "/anthropic/v1/messages"

    private const val BALANCE_PATH = "/billing/balance?app_version={v}"

    // ------------------------------------------------------------ 端点

    /** 拼接网关端点。 */
    fun endpoint(baseUrl: String?, path: String): String {
        val base = (baseUrl ?: "").trim().let { value ->
            var trimmed = value
            while (trimmed.endsWith("/")) trimmed = trimmed.dropLast(1)
            trimmed
        }
        val leaf = if (path.startsWith("/")) path else "/$path"
        return base + leaf
    }

    fun messagesEndpoint(baseUrl: String?): String = endpoint(baseUrl, MESSAGES_PATH)

    /** 额度端点，并把 `{v}` 替换掉。 */
    fun balanceEndpoint(baseUrl: String?, extraHeaders: Map<String, String>): String {
        val version = headerValue(extraHeaders, "X-ZCode-App-Version")
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_APP_VERSION
        val path = BALANCE_PATH.replace("{v}", encode(version))
        // 兜底断言：占位符拼错时这里立刻炸，而不是发一个带 {v} 的地址出去。
        check(!path.contains("{")) { "额度端点仍有未替换的占位符：$path" }
        return endpoint(baseUrl, path)
    }

    /** 请求头名不区分大小写：用户手写 `x-zcode-app-version` 也该认。 */
    private fun headerValue(headers: Map<String, String>, name: String): String? =
        headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    /** URL 查询串里的值要转义（版本号里可能有 `+`，平台里有 `/`）。 */
    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")

    // ------------------------------------------------------------ 请求头

    fun headers(
        baseUrl: String?,
        apiKey: String?,
        extraHeaders: String?,
        deviceMid: String?,
        accept: String = "text/event-stream",
        requestId: String? = null,
        traceId: String? = null,
        sessionType: String? = null,
        agent: String? = null,
    ): Map<String, String> {
        val user = parseExtraHeaders(extraHeaders).values
        // 版本优先取用户值：UA 与 X-ZCode-App-Version 必须说同一个版本，
        // 否则两处自相矛盾（见 DEFAULT_APP_VERSION 的说明）。
        val version = headerValue(user, "X-ZCode-App-Version")
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_APP_VERSION

        val out = linkedMapOf(
            "content-type" to "application/json",
            "accept" to accept,
            "authorization" to "Bearer " + (apiKey ?: "").trim(),
            "anthropic-version" to ANTHROPIC_VERSION,
            "user-agent" to "ZCode/$version",
            "x-zcode-app-version" to version,
            "x-title" to DEFAULT_SOURCE_TITLE,
            "x-release-channel" to DEFAULT_RELEASE_CHANNEL,
            "x-platform" to DEFAULT_PLATFORM,
            "x-os-category" to DEFAULT_OS_CATEGORY,
            "x-os-version" to System.getProperty("os.version").orEmpty(),
            "x-client-language" to clientLanguage(),
            "x-client-timezone" to java.util.TimeZone.getDefault().id,
        )
        originOf(baseUrl)?.let { out["http-referer"] = it }
        deviceMid?.takeIf { it.isNotBlank() }?.let { out["x-device-mid"] = it }
        // 请求级归因头只在会话请求上带（官方额度请求不带它们）。
        requestId?.let { out["x-request-id"] = it }
        traceId?.let { out["x-zcode-trace-id"] = it }
        sessionType?.let { out["x-zcode-session-type"] = it }
        agent?.let { out["x-zcode-agent"] = it }

        // 用户值最后铺：同名（不分大小写）的默认项要让位。
        for ((name, value) in user) {
            out.keys.firstOrNull { it.equals(name, ignoreCase = true) }?.let { out.remove(it) }
            out[name] = value
        }
        return out
    }

    /** 从用户填的 baseUrl 取 origin（`scheme://host[:port]`）。 */
    fun originOf(baseUrl: String?): String? = runCatching {
        val uri = java.net.URI((baseUrl ?: "").trim())
        val scheme = uri.scheme ?: return null
        val host = uri.host ?: return null
        val port = if (uri.port > 0) ":${uri.port}" else ""
        "$scheme://$host$port"
    }.getOrNull()

    /** 客户端语言，与官方一样取运行时的区域设置（取不到给 `unknown`，官方同此）。 */
    private fun clientLanguage(): String =
        runCatching { Locale.getDefault().toLanguageTag() }
            .getOrNull()?.takeIf { it.isNotBlank() } ?: "unknown"

    /** 会话请求的请求级头各一个随机 UUID（官方来自 `randomUUID()`）。 */
    fun newRequestId(): String = java.util.UUID.randomUUID().toString()

    /** 解析结果：解析失败时 [error] 非空，[values] 为空（调用方据此报出来）。 */
    data class ExtraHeaders(val values: Map<String, String>, val error: String?)

    /** 解析用户填的"额外请求头"。 */
    fun parseExtraHeaders(raw: String?): ExtraHeaders {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return ExtraHeaders(emptyMap(), null)
        val json = try {
            JSONObject(text)
        } catch (e: Exception) {
            return ExtraHeaders(emptyMap(), "额外请求头不是合法的 JSON 对象：${e.message}")
        }
        val values = LinkedHashMap<String, String>()
        for (key in json.keys()) {
            val value = json.opt(key)
            // null 值的头没有意义，跳过而不是写一个字符串 "null" 发出去。
            if (value == null || value == JSONObject.NULL) continue
            values[key] = value.toString()
        }
        return ExtraHeaders(values, null)
    }

    // ------------------------------------------------------------ 请求体

    /** 构造一次会话请求的报文。 */
    fun buildRequestBody(
        model: String?,
        maxTokens: Int,
        systemPrompt: String?,
        messages: JSONArray?,
        tools: JSONArray?,
        deviceId: String?,
        /** 今天的日期（`yyyy-MM-dd`）；由调用方给，便于单测钉住固定值。 */
        today: String,
    ): JSONObject {
        val body = JSONObject()
        val trimmedModel = (model ?: "").trim()
        body.put("model", trimmedModel)
        body.put("max_tokens", maxTokens)
        body.put("stream", true)
        // system 是「harness 前言 3 块 + 本应用提示词」——见 [ZcodeHarness] 的说明：
        // 只发我们自己的提示词会被网关的风控判成"不是它自己的客户端"（405/3012）。
        body.put("system", ZcodeHarness.systemBlocks(systemPrompt, trimmedModel))
        // messages 最前面插一条合成的 user 消息（上下文 + 日期），与官方一致。
        val outgoing = JSONArray()
        outgoing.put(contextMessage(today))
        val incoming = messages ?: JSONArray()
        for (i in 0 until incoming.length()) {
            val item = incoming.optJSONObject(i) ?: continue
            outgoing.put(item)
        }
        // 最后一条消息也打缓存标记（官方如此）。
        ZcodeHarness.markLastMessageEphemeral(outgoing)
        body.put("messages", outgoing)
        strippedTools(tools)?.let { body.put("tools", it) }
        body.put("metadata", JSONObject().put("user_id", metadataUserId(deviceId)))
        return body
    }

    /** 那条合成的 user 消息（`<system-reminder>` 里放上下文与日期）。 */
    fun contextMessage(today: String): JSONObject {
        val text = ZcodeHarness.contextMessageText(today)
        return JSONObject()
            .put("role", "user")
            .put(
                "content",
                JSONArray().put(JSONObject().put("type", "text").put("text", text)),
            )
    }

    /** 今天的日期戳（`yyyy-MM-dd`）。不用 `java.time`：minSdk 24 需要脱糖。 */
    fun todayStamp(now: java.util.Date = java.util.Date()): String =
        java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now)

    /** 系统提示 → 缓存块数组。空提示给空数组（而不是一个空文本块）。 */
    fun buildSystem(systemPrompt: String?): JSONArray {
        val blocks = JSONArray()
        val text = systemPrompt?.trim().orEmpty()
        if (text.isEmpty()) return blocks
        blocks.put(
            JSONObject()
                .put("type", "text")
                .put("text", text)
                .put("cache_control", JSONObject().put("type", "ephemeral")),
        )
        return blocks
    }

    /** 去掉每个工具上的 `cache_control`；没有工具时返回 null（不往报文里塞空数组）。 */
    fun strippedTools(tools: JSONArray?): JSONArray? {
        if (tools == null || tools.length() == 0) return null
        val out = JSONArray()
        for (i in 0 until tools.length()) {
            val item = tools.optJSONObject(i) ?: continue
            val copy = JSONObject(item.toString())
            copy.remove("cache_control")
            out.put(copy)
        }
        return if (out.length() == 0) null else out
    }

    /** `metadata.user_id` 的取值：一个 JSON 字符串。 */
    fun metadataUserId(deviceId: String?): String {
        val json = JSONObject()
        json.put("device_id", (deviceId ?: "").trim())
        json.put("account_uuid", "")
        json.put("session_id", "")
        return json.toString()
    }

    // ------------------------------------------------------------ 响应解析

    /** 解析模型列表。 */
    fun parseModelList(json: JSONObject?): List<ModelDescriptor> {
        if (json == null) return emptyList()
        val array = json.optJSONArray("data")
            ?: json.optJSONArray("models")
            ?: json.optJSONArray("items")
            ?: return emptyList()
        val out = ArrayList<ModelDescriptor>()
        for (i in 0 until array.length()) {
            when (val item = array.opt(i)) {
                is String -> if (item.isNotBlank()) out.add(ModelDescriptor(item.trim(), item.trim()))
                is JSONObject -> {
                    val id = firstNonBlank(
                        item.optString("id"),
                        item.optString("model"),
                        item.optString("name"),
                    ) ?: continue
                    val display = firstNonBlank(
                        item.optString("display_name"),
                        item.optString("displayName"),
                        item.optString("name"),
                        id,
                    )!!
                    out.add(ModelDescriptor(id, display))
                }
                else -> continue
            }
        }
        return out
    }

    /** 归一化模型名：网关对大小写敏感，但用户手填时常带空格。 */
    /** 把用户给的模型名折成**官方规范形式**。 */
    fun normalizeModel(model: String?): String {
        val trimmed = (model ?: "").trim()
        if (trimmed.isEmpty()) return ""
        return MODEL_NAMES.keys.firstOrNull { it.equals(trimmed, ignoreCase = true) } ?: trimmed
    }

    // ------------------------------------------------------------ 套餐模型目录

    val MODEL_NAMES: Map<String, String> = linkedMapOf(
        "GLM-4.5-Air" to "GLM 4.5 Air",
        "GLM-4.6" to "GLM 4.6",
        "GLM-4.6V" to "GLM 4.6V",
        "GLM-4.7" to "GLM 4.7",
        "GLM-5" to "GLM 5",
        "GLM-5-Turbo" to "GLM 5 Turbo",
        "GLM-5V-Turbo" to "GLM 5V Turbo",
        "GLM-5.1" to "GLM 5.1",
        "GLM-5.2" to "GLM 5.2",
        "GLM-5.3" to "GLM 5.3",
        "GLM-5.3-Flash" to "GLM 5.3 Flash",
    )

    /** 用"套餐可用模型"筛一遍 [MODEL_NAMES]。 */
    fun filterEntitled(entitled: Collection<String>?): Map<String, String> {
        if (entitled.isNullOrEmpty()) return MODEL_NAMES
        val wanted = entitled.map { it.trim().lowercase(Locale.US) }.toSet()
        val keep = MODEL_NAMES.filterKeys { it.lowercase(Locale.US) in wanted }
        return keep.ifEmpty { MODEL_NAMES }
    }

    // ------------------------------------------------------------ 额度

    /** 一档套餐额度。字段名对应网关返回的 `balances[]`。 */
    data class BalanceRow(
        val showName: String,
        val totalUnits: Long,
        val remainingUnits: Long,
        val expiresAtSec: Long,
        val modelIds: List<String>,
    )

    /** 额度响应里我们用到的那部分。 */
    data class BalancePayload(
        val serverTimeSec: Long,
        val rows: List<BalanceRow>,
    )

    /** 解析额度响应。 */
    fun parseBalancePayload(json: JSONObject?): BalancePayload {
        if (json == null) throw IllegalStateException("额度接口没有返回内容")
        val code = json.optInt("code", 0)
        if (code != 0) {
            val message = json.optString("message").trim()
            throw IllegalStateException(
                if (message.isEmpty()) "额度接口返回 code $code" else "$message（code $code）",
            )
        }
        val data = json.optJSONObject("data") ?: throw IllegalStateException("额度响应缺少 data")
        val serverTime = data.optLong("server_time", System.currentTimeMillis() / 1000)
        val array = data.optJSONArray("balances")
        val rows = ArrayList<BalanceRow>()
        if (array != null) {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val capabilities = item.optJSONArray("capabilities")
                val modelIds = ArrayList<String>()
                if (capabilities != null) {
                    for (j in 0 until capabilities.length()) {
                        val raw = capabilities.optString(j, "")
                        // 参考实现只认 "model:" 前缀的能力项。
                        if (raw.startsWith("model:")) modelIds += raw.substring(6)
                    }
                }
                rows += BalanceRow(
                    showName = item.optString("show_name").trim(),
                    totalUnits = item.optLong("total_units", 0),
                    remainingUnits = item.optLong("remaining_units", 0),
                    expiresAtSec = item.optLong("expires_at", 0),
                    modelIds = modelIds,
                )
            }
        }
        return BalancePayload(serverTime, rows)
    }

    /** 套餐可用的模型 id（去重，保持出现顺序）。 */
    fun entitledModelIds(payload: BalancePayload?): List<String> {
        val seen = LinkedHashSet<String>()
        payload?.rows?.forEach { seen.addAll(it.modelIds) }
        return seen.toList()
    }

    /** 重置倒计时文案。 */
    fun formatCountdown(expiresAtSec: Long, serverTimeSec: Long): String {
        if (expiresAtSec <= 0) return "永久有效"
        val left = expiresAtSec - serverTimeSec
        return when {
            left <= 0 -> "已过期"
            left < 3600 -> "剩 ${left / 60} 分钟重置"
            left < 86400 -> "剩 ${left / 3600}:${String.format(Locale.US, "%02d", (left % 3600) / 60)} 重置"
            else -> "剩 ${left / 86400} 天重置"
        }
    }

    /** 额度数字的显示写法（与参考实现的 `fmtTokens` 一致） */
    fun formatUnits(units: Long): String = when {
        units >= 100_000_000L -> String.format(Locale.US, "%.2f亿", units / 100_000_000.0)
        units >= 10_000L -> String.format(Locale.US, "%.1f万", units / 10_000.0)
        else -> units.toString()
    }

    /** 额度条的比例（0..1）。总量为 0 时给 0，而不是除零。 */
    fun quotaFraction(row: BalanceRow): Float {
        if (row.totalUnits <= 0L) return 0f
        return (row.remainingUnits.toDouble() / row.totalUnits.toDouble())
            .coerceIn(0.0, 1.0)
            .toFloat()
    }

    /** 判断一个模型名看起来是不是这个网关的（GLM 系列），用于给出更准的提示。 */
    fun looksLikeGlm(model: String?): Boolean {
        val name = normalizeModel(model).lowercase(Locale.US)
        return name.startsWith("glm") || name.contains("glm-")
    }

    private fun firstNonBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }?.trim()
}
