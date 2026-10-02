package com.termux.app.zhicode.api.zcode

import com.termux.app.zhicode.api.ModelDescriptor
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * ZCode 协议的**纯逻辑**层（刻意不 import 任何 `android.*`）。
 *
 * ## 与参考实现（反编译 IQ Code 的 `ZcodePlanProvider` + `ZcodeVault`）的关系
 *
 * 报文形状与它一致：请求是 **Anthropic Messages** 的形态（`model` / `max_tokens` /
 * `stream` / `system` 缓存块 / `messages` / `tools` / `metadata.user_id`），
 * 响应走 Anthropic 的事件流（`message_start` / `content_block_delta` / `message_stop`），
 * 模型是 GLM 系列，另有额度查询。
 *
 * ### 一处刻意的不照做
 *
 * 参考实现把**网关地址与一整套身份标识**（`X-ZCode-Agent`、`HTTP-Referer`、`X-Title`、
 * `X-Platform: linux-x64`、`X-Os-Category: linux`…）用 XOR 混淆藏在 `ZcodeVault` 里，
 * 界面上写「无需填写，直连 ZCode 网关」。那些值**不是我们的**：它们是那个产品的
 * 官方客户端身份（`X-Platform` / `X-Os-Version` 明确自称官方 Linux CLI），
 * 混进本应用等于用别人的凭据去消费别人的服务；而且对方一旦撤销那个标识，
 * 所有用户会同时失效，我们连原因都看不到。
 *
 * 所以这里的做法是：
 *
 * - **网关地址**就是配置里已有的 `baseUrl`（用户自己填）；
 * - **授权码**就是 `apiKey`；
 * - **身份头**由用户在 `extraHeaders`（JSON 对象）里自己给，我们不预置任何一条。
 *
 * 这样机制完全一样、能力不减少，但「连到哪、以谁的身份」是用户自己决定的，
 * 也才排得动障。
 *
 * ## 为什么这一层要单独存在
 *
 * 请求体与响应解析是**最容易错又最难查**的部分：字段名写错、缓存块位置不对、
 * 模型列表结构变了…… 在真机上只表现为「回复不完整」或「列表是空的」。
 * 这一层没有 I/O、不碰 Android，所以能进 `test-jvm-fast.sh` 的秒级回路。
 */
object ZcodeWire {

    /** 线上名。写进设置与会话文件，是持久化契约的一部分。 */
    const val WIRE_NAME = "zcode"

    /** 与 Anthropic 相同的版本头取值 —— 这个网关说的是 Anthropic 协议。 */
    const val ANTHROPIC_VERSION = "2023-06-01"

    /**
     * 我们自己的 UA。
     *
     * 刻意**不**自称别人的官方客户端：改这一行的后果是"对服务端声称是另一个客户端"，
     * 而那正是我们要避免的事。用户若确实需要冒充某个身份，用 [extraHeaders] 覆盖。
     */
    const val USER_AGENT = "ZhiCodeAndroid-JavaNative/0.15"

    /** 会话与额度端点。相对路径，拼在用户给的网关基址上。 */
    private const val MESSAGES_PATH = "/messages"
    private const val BALANCE_PATH = "/billing/balance"

    // ------------------------------------------------------------ 端点

    /**
     * 拼接网关端点。
     *
     * 只做两件必要的事：去掉基址末尾多余的斜杠、必要时补一个前导斜杠。
     * **不猜版本段**：这个网关的路径结构由它自己决定，替它补 `/v1` 只会拼出 404，
     * 而失败现象是"请求发不出去"，看不出是路径被我们改了。
     */
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

    fun balanceEndpoint(baseUrl: String?): String = endpoint(baseUrl, BALANCE_PATH)

    // ------------------------------------------------------------ 请求头

    /**
     * 组装请求头：我们的最小默认集，再让用户给的覆盖/追加。
     *
     * 默认集只有四类**功能上必需**的东西（内容类型、接受类型、鉴权、协议版本）；
     * 身份类的头一条都不预置，见类注释。
     *
     * 用户值**覆盖**同名默认项是有意的：他要伪装成别的客户端（或那个网关要求
     * 特定的 `User-Agent`）时，覆盖是唯一能生效的方式。
     *
     * @param apiKey       授权码；空白表示未配置，由 provider 拦下并给出明确提示
     * @param extraHeaders 用户填的 JSON 对象（可为空/非法 —— 非法时**忽略并如实返回错误**，
     *                     而不是抛出去：一条配置写错不该让整个请求无法发出去，
     *                     但也不能静默当成"没有"）
     */
    fun headers(apiKey: String?, extraHeaders: String?): Map<String, String> {
        val out = linkedMapOf(
            "content-type" to "application/json",
            "accept" to "text/event-stream",
            "authorization" to "Bearer " + (apiKey ?: "").trim(),
            "anthropic-version" to ANTHROPIC_VERSION,
            "user-agent" to USER_AGENT,
        )
        out.putAll(parseExtraHeaders(extraHeaders).values)
        return out
    }

    /** 解析结果：解析失败时 [error] 非空，[values] 为空（调用方据此报出来）。 */
    data class ExtraHeaders(val values: Map<String, String>, val error: String?)

    /**
     * 解析用户填的"额外请求头"。
     *
     * 允许 `{"X-Title": "..."}` 这种 JSON 对象。值允许是数字/布尔（转成字符串）——
     * 配置文件里手写 JSON 时很容易漏引号，直接拒绝会让人摸不着头脑。
     */
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

    /**
     * 构造一次会话请求的报文。
     *
     * 三处形状上的细节，各对应一种"不报错但错"：
     *
     * - `system` 是**块数组**而不是字符串：该网关按 Anthropic 的缓存语义读
     *   `cache_control`，传字符串会让缓存整体失效（不报错，只是变慢、变贵）。
     * - `tools` 里的 `cache_control` 要**剥掉**：目前一个工具一条（量很小），
     *   带上缓存块没有收益，反而让最后一块的位置随工具数量变化。
     * - `metadata.user_id` 是 JSON **字符串**（不是对象）：参考实现也是这么发的，
     *   而写错类型通常只表现为服务端忽略它。
     */
    fun buildRequestBody(
        model: String?,
        maxTokens: Int,
        systemPrompt: String?,
        messages: JSONArray?,
        tools: JSONArray?,
        deviceId: String?,
    ): JSONObject {
        val body = JSONObject()
        body.put("model", (model ?: "").trim())
        body.put("max_tokens", maxTokens)
        body.put("stream", true)
        body.put("system", buildSystem(systemPrompt))
        body.put("messages", messages ?: JSONArray())
        strippedTools(tools)?.let { body.put("tools", it) }
        body.put("metadata", JSONObject().put("user_id", metadataUserId(deviceId)))
        return body
    }

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

    /**
     * `metadata.user_id` 的取值：一个 JSON 字符串。
     *
     * 里面的 `device_id` 由用户配置提供（可以为空）。这里**不生成任何设备标识** ——
     * 生成一个假的"设备指纹"发出去，等于替用户向对方提供了一份伪造的身份信息。
     */
    fun metadataUserId(deviceId: String?): String {
        val json = JSONObject()
        json.put("device_id", (deviceId ?: "").trim())
        json.put("account_uuid", "")
        json.put("session_id", "")
        return json.toString()
    }

    // ------------------------------------------------------------ 响应解析

    /**
     * 解析模型列表。
     *
     * 容错形状很多（`data` / `models` 数组，元素里 `id` / `name` / `display_name`），
     * 因为这类网关的返回格式常变。**认不出来返回空列表**，由界面回落到手填模型名 ——
     * 而不是抛异常：一个结构调整不该让"选择模型"整页不可用。
     */
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

    /**
     * 从额度接口的返回里抠出一句人话。
     *
     * 这个接口的字段名不稳定，所以按一组候选找；一个都找不到时返回 null
     * （界面据此不显示额度行，而不是显示一行空白或 `null`）。
     */
    fun parseBalance(json: JSONObject?): String? {
        if (json == null) return null
        val candidates = listOf("balance", "remaining", "quota", "credits", "amount")
        for (key in candidates) {
            if (!json.has(key)) continue
            val value = json.opt(key)
            if (value == null || value == JSONObject.NULL) continue
            val text = value.toString().trim()
            if (text.isNotEmpty()) return text
        }
        // 常见形态：数据在一层 data/result 里。
        for (wrapper in listOf("data", "result")) {
            json.optJSONObject(wrapper)?.let { inner ->
                parseBalance(inner)?.let { return it }
            }
        }
        return null
    }

    /** 归一化模型名：网关对大小写敏感，但用户手填时常带空格。 */
    fun normalizeModel(model: String?): String = (model ?: "").trim()

    /** 判断一个模型名看起来是不是这个网关的（GLM 系列），用于给出更准的提示。 */
    fun looksLikeGlm(model: String?): Boolean {
        val name = normalizeModel(model).lowercase(Locale.US)
        return name.startsWith("glm") || name.contains("glm-")
    }

    private fun firstNonBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }?.trim()
}
