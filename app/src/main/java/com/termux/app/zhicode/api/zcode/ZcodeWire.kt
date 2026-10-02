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

    /**
     * 会话端点。**注意不是 `/messages`** —— 那个网关把它挂在 `/anthropic/v1/messages` 下。
     *
     * 取值来自参考实现（反编译的 `ZcodeVault.messagesPath()`）。写错它的现象是 404，
     * 而错误信息只会说"请求失败"，看不出是路径少了一段。
     */
    private const val MESSAGES_PATH = "/anthropic/v1/messages"

    /**
     * 额度端点。`{v}`（客户端版本）与 `{p}`（平台）两个占位符必须被替换。
     *
     * 取值同样来自参考实现的 `billingBalancePath()`。注意它是**查询串**而不是路径段 ——
     * 少一个 `?` 就会 404。
     */
    private const val BALANCE_PATH = "/billing/balance?app_version={v}&platform={p}"

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

    /**
     * 额度端点，并把两个占位符替换掉。
     *
     * `{v}`/`{p}` 的取值**从用户自己填的额外请求头里读** —— 不新增字段、也不内置：
     * 那两个值本来就属于那个客户端，用户已经在配置里给了一份，再抄一份只会多一处
     * 会不一致的地方。
     *
     * ## `{p}` 取的是 `X-Os-Category`，**不是** `X-Platform`
     *
     * 这两个在参考实现里是**不同的值**，而这一点在真机上就是把额度打回
     * `HTTP 400 {"code":3001,"msg":"parameter error"}` 的原因：
     *
     * - 查询串的 `platform` 取 `ZcodeVault.platform()`，解出来是 `linux`；
     * - 请求头的 `X-Platform` 是**硬编码**的 `linux-x64`（同一个类里另外写的字面量）。
     *
     * 一开始这里是按"名字对上就行"从 `X-Platform` 头取的，于是发出去的是
     * `platform=linux-x64` —— 服务端那两个参数里有一个它认不出，直接判参数错误。
     * 这类错**不会**说明是哪个参数不对，只能靠跟已知可用的那份实现逐字节对齐。
     *
     * 换到 `X-Os-Category` 是因为它恰好等于那个 `platform()` 的取值（`linux`），
     * 于是既不用凭空内置一个值，也不用新增一个配置字段。
     *
     * 缺任一取值时**不发请求**，直接说明缺什么。原样带着 `{v}` 发出去只会得到 404，
     * 而 404 的报错看不出是"占位符没替换"。
     */
    fun balanceEndpoint(baseUrl: String?, extraHeaders: Map<String, String>): String {
        val version = headerValue(extraHeaders, "X-ZCode-App-Version")
        val platform = headerValue(extraHeaders, "X-Os-Category")
        val missing = ArrayList<String>()
        if (version.isNullOrBlank()) missing += "X-ZCode-App-Version"
        if (platform.isNullOrBlank()) missing += "X-Os-Category"
        if (missing.isNotEmpty()) {
            throw IllegalStateException(
                "读取额度需要额外请求头里的 ${missing.joinToString("、")}（点「填入 ZCode 默认值」可一次填好）",
            )
        }
        val path = BALANCE_PATH
            .replace("{v}", encode(version!!))
            .replace("{p}", encode(platform!!))
        return endpoint(baseUrl, path)
    }

    /** 请求头名不区分大小写：用户手写 `x-zcode-app-version` 也该认。 */
    private fun headerValue(headers: Map<String, String>, name: String): String? =
        headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    /** URL 查询串里的值要转义（版本号里可能有 `+`，平台里有 `/`）。 */
    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")

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
     * @param accept       `accept` 头的取值。会话要事件流，额度接口要 JSON ——
     *                     用参数而不是让调用方改返回值，是为了让"返回的 Map 是否可写"
     *                     不成为一个隐含约定（上一版就是在这里踩到了不可写）
     */
    fun headers(
        apiKey: String?,
        extraHeaders: String?,
        accept: String = "text/event-stream",
    ): Map<String, String> {
        val out = linkedMapOf(
            "content-type" to "application/json",
            "accept" to accept,
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

    /** 归一化模型名：网关对大小写敏感，但用户手填时常带空格。 */
    fun normalizeModel(model: String?): String = (model ?: "").trim()

    // ------------------------------------------------------------ 套餐模型目录

    /**
     * 这个服务的套餐模型（id → 显示名）。
     *
     * 取值来自参考实现的 `ZcodeVault.modelPairs()`（11 个）。它**不是**凭据也不是端点，
     * 而是"这个服务提供哪些模型"这件事本身 —— 所以放在这里而不是界面里：
     * 下面 [filterEntitled] 要用它做筛选，两边分开就得在界面层再抄一份。
     *
     * ⚠️ 它是**静态快照**：对方上新模型时这份表不会自己变。所以界面上必须允许手填模型名，
     * 而这个表只用于"给出好看的显示名"与"筛选套餐可用项"，不参与任何校验。
     */
    val MODEL_NAMES: Map<String, String> = linkedMapOf(
        "glm-4.5-air" to "GLM 4.5 Air",
        "glm-4.6" to "GLM 4.6",
        "glm-4.6v" to "GLM 4.6V",
        "glm-4.7" to "GLM 4.7",
        "glm-5" to "GLM 5",
        "glm-5-turbo" to "GLM 5 Turbo",
        "glm-5v-turbo" to "GLM 5V Turbo",
        "glm-5.1" to "GLM 5.1",
        "glm-5.2" to "GLM 5.2",
        "glm-5.3" to "GLM 5.3",
        "glm-5.3-flash" to "GLM 5.3 Flash",
    )

    /**
     * 用"套餐可用模型"筛一遍 [MODEL_NAMES]。
     *
     * 三种情况分开处理，与参考实现一致：
     *  - 套餐列表为空（额度没读到）→ **返回全部**，让用户至少还能选、还能手填；
     *  - 筛完为空（套餐里的模型不在我们的静态表里，说明对方上新了）→ 也返回全部，
     *    否则用户会看到一个空列表，比"多出几个用不了的"更糟；
     *  - 正常 → 只留交集。
     */
    fun filterEntitled(entitled: Collection<String>?): Map<String, String> {
        if (entitled.isNullOrEmpty()) return MODEL_NAMES
        val keep = MODEL_NAMES.filterKeys { entitled.contains(it) }
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

    /**
     * 解析额度响应。
     *
     * 形状（来自参考实现的 `fetchBalanceData` + 它的渲染代码）：
     * ```
     * {"code":0,"message":"…","data":{
     *    "server_time":<秒>,
     *    "balances":[{"show_name":…,"total_units":N,"remaining_units":N,
     *                 "expires_at":<秒>,"capabilities":["model:glm-5.3",…]}]}}
     * ```
     *
     * `code != 0` 时**抛错并带上 message**：那是业务层失败（授权码无效之类），
     * 返回空列表会让界面显示"暂无套餐余额"——把"你的码不对"说成"你没有套餐"。
     */
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

    /**
     * 重置倒计时文案。
     *
     * 与参考实现同一套分档（它自己的渲染代码就是这么写的），四档的边界都落在
     * 整数秒上：`<=0` 已过期、`<1 小时` 说分钟、`<1 天` 说 `H:MM`、再往上说天。
     * `expiresAt` 非正表示**永久有效**（而不是"已过期"）——这两者的区别很重要。
     */
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

    /**
     * 额度数字的显示写法（与参考实现的 `fmtTokens` 一致）：
     * `>= 1 亿` 用「亿」、`>= 1 万` 用「万」、再小就原样。
     *
     * 用 `Locale.US` 固定小数点：跟随系统区域的话，某些区域会把 `.` 写成 `,`，
     * 而这里拼的是"300.0万"这种给人看的短标签，不是本地化数字。
     */
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
