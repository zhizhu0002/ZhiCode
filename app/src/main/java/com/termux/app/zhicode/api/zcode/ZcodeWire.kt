package com.termux.app.zhicode.api.zcode

import com.termux.app.zhicode.api.ModelDescriptor
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * ZCode 协议的**纯逻辑**层（刻意不 import 任何 `android.*`）。
 *
 * ## 依据是官方开源源码，不是反编译
 *
 * 报文形状：请求是 **Anthropic Messages** 的形态（`model` / `max_tokens` / `stream` /
 * `system` 缓存块 / `messages` / `tools` / `metadata.user_id`），响应走 Anthropic 的
 * 事件流（`message_start` / `content_block_delta` / `message_stop`），另有额度查询。
 *
 * 具体的取值与请求头清单来自 **`zai-org/ZCode`（Apache-2.0，ZCode 官方客户端本体）**：
 * `packages/shared/src/zcode-source-headers.ts`（来源头全集）、
 * `apps/zcode-cli/packages/bootstrap/src/runtime-platform-headers.ts`（平台头取法）、
 * `packages/services/src/model-provider/zaiStartPlanBilling.ts` 与
 * `packages/shared/src/zcodeEndpoint.ts`（端点构造）、
 * `apps/zcode-cli/packages/adapters/src/model/runner-attribution.ts`（请求级归因头）。
 *
 * ### 为什么以前不是这么做的（这段历史要留着，否则会被改回去）
 *
 * 最早这里是照 IQ Code（另一个应用）的反编译结果写的。当时有两个判断，现在都变了：
 *
 * 1. 「那些身份标识是**别人的**，内置等于冒充另一个产品」—— 那个客户端后来以
 *    **Apache-2.0 开源**了，这些头是它公开的协议契约，不是泄露出来的私密凭据。
 * 2. 「网关地址与版本不该写死」—— 这条**仍然成立**，而且现在更要紧：地址依旧只能
 *    来自用户填的 `baseUrl`（`HTTP-Referer` 也从它推 origin），我们一个域名都不内置。
 *
 * 而真正逼着改的是真机上的两个错：额度恒回 `400 / 3001 parameter error`、
 * 会话恒回 `405 / 3012 request has been blocked due to unusual activity`。
 * 官方源码把第一个错的原因写得很直白（见 [ZcodeDeviceMid]）：**缺 `X-Device-Mid`**。
 * 也就是说此前"只发功能上必需的头"这个取舍，代价是这个协议根本用不了。
 *
 * ### 现在的规则
 *
 * - **地址**：`baseUrl`（用户填）。不内置任何主机名。
 * - **授权码**：`apiKey`（用户填）。
 * - **请求头**：内置官方那一套**默认值**，并且**每一条都能被用户的 `extraHeaders` 覆盖**。
 *   默认值里有意义的那些（版本号、来源标识）本来就是那个客户端的公开取值；
 *   用户要连别的兼容网关时，覆盖是唯一能生效的方式。
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
     * `User-Agent` 的默认值。
     *
     * **必须与 [DEFAULT_APP_VERSION] 说同一个版本**：官方客户端是
     * `ZCode/${appVersion}`（`zcode-source-headers.ts`），UA 与
     * `X-ZCode-App-Version` 说两个版本是自相矛盾的，反而更容易被风控挑出来。
     */
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

    /**
     * `X-ZCode-Agent` 的取值。
     *
     * ## 为什么它是**只给会话请求**的
     *
     * 官方有两条不同的请求头路径，取值不一样：
     *
     * - `apps/zcode-cli/packages/bootstrap/src/model-config.ts`（**模型/会话**请求）
     *   里有 `"X-ZCode-Agent": "glm"`；
     * - `packages/shared/src/zcode-source-headers.ts`（**额度**等业务请求）里没有它。
     *
     * 真机现象正好印证了这个分叉：补上 `X-Device-Mid` 之后**额度通了**（它走第二条路），
     * 而**会话仍然被拒** `405 / 3012 unusual activity`（它走第一条路，缺的正是这个头）。
     *
     * 所以这里做成参数而不是默认项：额度请求刚被证明可用，不去动它。
     */
    const val DEFAULT_AGENT = "glm"

    /** 被风控挡回时服务端给的业务码。 */
    const val CODE_BLOCKED = 3012

    /**
     * 把网关的业务错误翻成一句**人能读、能行动**的话；不认识时返回 null。
     *
     * ## 为什么专门处理 3012
     *
     * 它看起来像"请求发错了"（HTTP **405 Method Not Allowed**，而请求体是
     * `{"code":3012,"msg":"request has been blocked due to unusual activity."}`），
     * 所以我们（和任何看到它的人）第一反应都是去查请求哪一项不对 —— 我为此把
     * 请求头、模型名、端点逐项跟官方源码对齐过一轮，全部对上了，错误依旧。
     *
     * 真相是它**不是协议错误，是账号级风控标记**，而且与客户端实现无关：
     * 官方反馈仓库 `zai-org/feedback` issue #716 里，报告者在 **官方客户端 3.12.3 /
     * 3.14.4** 上用**一个字**的输入同样被拒，`headersApplied=true`，额度接口正常，
     * 触发点是"短时间并发调用"之后被持续标记。也就是说：**头都发对了照样 3012**。
     *
     * 所以这一层的价值不在"修"，而在**不要让人往错的方向查**：把"这不是你的请求写错了、
     * 也不是本应用的 bug、官方客户端同样会中招、要联系 z.ai 解除"直接说出来，
     * 并把 `logid` 一并给出（那是向官方报障时唯一有用的东西）。
     *
     * @param status HTTP 状态码（3012 走的是 405；不同网关可能不同，所以不硬性要求）
     * @param body   响应正文；不是 JSON 时返回 null，由调用方回落到原文
     */
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

    /**
     * 会话端点。**注意不是 `/messages`** —— 那个网关把它挂在 `/anthropic/v1/messages` 下。
     *
     * 取值来自参考实现（反编译的 `ZcodeVault.messagesPath()`）。写错它的现象是 404，
     * 而错误信息只会说"请求失败"，看不出是路径少了一段。
     */
    private const val MESSAGES_PATH = "/anthropic/v1/messages"

    /**
     * 额度端点。
     *
     * **只有 `app_version` 一个查询参数，没有 `platform`。**
     *
     * 这一条踩过坑，所以写清楚：官方源码里额度地址是
     * `${origin}/api/v1/zcode-plan/billing/balance`（`zcodeEndpoint.ts`），
     * 请求时只 `url.searchParams.set("app_version", ZCODE_VERSION)`
     * （`zaiStartPlanBilling.ts`）。我最早照另一个应用的反编译结果多带了一个
     * `platform=`，真机上那条请求就被拒成 `parameter error` —— 服务端不会说是哪个
     * 参数多余，只会说参数错误。**多余的参数和缺失的参数在这一层是同一个报错。**
     *
     * `{v}` 仍是占位符：取值从用户填的 `X-ZCode-App-Version` 头里读，见 [balanceEndpoint]。
     */
    private const val BALANCE_PATH = "/billing/balance?app_version={v}"

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
     * 额度端点，并把 `{v}` 替换掉。
     *
     * `{v}` 优先取用户填的 `X-ZCode-App-Version`，没填就用 [DEFAULT_APP_VERSION]
     * （官方那边同样是一个编译期常量 `ZCODE_VERSION`，不需要用户填）。
     *
     * 上一版在这里**硬性要求**用户填那个头，缺了就报错不发请求 —— 那是自找的麻烦：
     * 代码本来就有默认值，让用户去"找一个数字"只会多一次失败。
     *
     * 留下的硬性检查只有一条：**替换必须真的发生**。原样带着 `{v}` 发出去只会 404，
     * 而 404 的报错看不出是"占位符没替换"。
     */
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

    /**
     * 组装请求头：官方的来源头默认集，再让用户给的覆盖/追加。
     *
     * 默认集逐条对应官方 `buildZCodeSourceHeadersFromContext()`（`zcode-source-headers.ts`）：
     * `User-Agent` / `HTTP-Referer` / `X-Title` / `X-ZCode-App-Version` / `X-Platform` /
     * `X-Release-Channel` / `X-Client-Language` / `X-Client-Timezone` / `X-Os-Category` /
     * `X-Os-Version` / `X-Device-Mid`，外加功能性的 `content-type` / `accept` /
     * `authorization` / `anthropic-version`。
     *
     * ## 为什么要补齐（而不是只发"功能上必需"的那几个）
     *
     * 只发最小集时，真机上额度恒回 `400 / 3001 parameter error`、会话恒回
     * `405 / 3012 ... unusual activity`。官方源码说明了原因：这个网关按来源头判定
     * 请求来源，缺 `X-Device-Mid` 时额度接口**直接判参数错误**。
     *
     * ## `HTTP-Referer` 从 baseUrl 推
     *
     * 不写死域名：官方也只在"等于默认 origin"时才改写它（`nodeApiClient.ts`）。
     * 我们从用户填的 `baseUrl` 取 origin —— 用户把地址指到别处时，这个头跟着走，
     * 「请求发到哪」和「对端看到我们来自哪」始终一致。取不到合法 origin 时**不发这个头**
     * （发一个错的来源比不发更容易被拒）。
     *
     * ## 覆盖顺序
     *
     * 用户值**最后**铺上去，所以能覆盖任意一条默认项（包括 `User-Agent` 和
     * `X-Device-Mid`）。这是有意的：要连别的兼容网关时，覆盖是唯一能生效的方式。
     *
     * @param baseUrl      用户填的网关地址，用来推 `HTTP-Referer`
     * @param apiKey       授权码；空白表示未配置，由 provider 拦下并给出明确提示
     * @param extraHeaders 用户填的 JSON 对象（可为空/非法 —— 非法时**忽略并如实返回错误**，
     *                     而不是抛出去：一条配置写错不该让整个请求无法发出去，
     *                     但也不能静默当成"没有"）
     * @param accept       `accept` 头的取值。会话要事件流，额度接口要 JSON
     * @param deviceMid    设备标识（见 [ZcodeDeviceMid]）。为 null 时**不发这个头**，
     *                     而不是发一个空串：空串同样会被判参数错误，但报错一样看不懂
     * @param requestId    请求级 `x-request-id`；仅会话请求用
     * @param traceId      请求级 `x-zcode-trace-id`；仅会话请求用
     * @param sessionType  请求级 `x-zcode-session-type`；仅会话请求用
     * @param agent        `X-ZCode-Agent`；仅会话请求用（见 [DEFAULT_AGENT]）
     */
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

    /**
     * 从用户填的 baseUrl 取 origin（`scheme://host[:port]`）。
     *
     * 解析不出来就返回 null —— 调用方据此**不发** `HTTP-Referer`。拼一个半截的
     * origin 出去只会换来一个同样看不懂的 4xx。
     */
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
     * `device_id` 传的是 [ZcodeDeviceMid] 那个**本应用自己的安装标识**（官方也是同一个
     * deviceMid：`anthropic-request-metadata.ts` 用 `ensureCliDeviceMid()` 填这一格）。
     * `account_uuid` 与 `session_id` 官方同样传空。
     *
     * 这里仍然**不生成任何硬件指纹**：`device_id` 的值来自一个随机 UUID，
     * 与 IMEI / ANDROID_ID / 机型 / 系统版本无关，卸载重装即变。
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
    /**
     * 把用户给的模型名折成**官方规范形式**。
     *
     * 只做一件事：如果去掉大小写后能对上 [MODEL_NAMES] 里的某一项，就用那一项
     * （官方 `normalizeOfficialGlmModelId` 的等价物）。所以用户在输入框里手打
     * `glm-5.3`、`GLM-5.3`、`Glm-5.3` 都会发成 `GLM-5.3`。
     *
     * **对不上的原样发出去**：对方上新模型时我们这张表落后，那时用户的写法就是唯一
     * 能用的写法 —— 硬套一个"最接近"的名字只会把请求发到一个不存在的模型上。
     */
    fun normalizeModel(model: String?): String {
        val trimmed = (model ?: "").trim()
        if (trimmed.isEmpty()) return ""
        return MODEL_NAMES.keys.firstOrNull { it.equals(trimmed, ignoreCase = true) } ?: trimmed
    }

    // ------------------------------------------------------------ 套餐模型目录

    /**
     * 这个服务的套餐模型（id → 显示名）。
     *
     * ## 为什么 id 是**大写**的
     *
     * 官方把 GLM 的规范 id 定义成大写（`packages/shared/src/official-glm-model-id.ts`
     * 的 `OFFICIAL_GLM_MODEL_IDS`：`GLM-5.3`、`GLM-5.3-Flash`、`GLM-5V-Turbo`…），
     * 并提供了一个规范化函数把任意大小写折算过去（`glm-5.3` → `GLM-5.3`）。
     * 内置模型名单（`zcode-builtin.json` 的 `builtinModelIds`）用的也是大写形式。
     *
     * 之前这里是全小写 —— 那是照另一个应用的反编译表抄的，而那个表很可能是它自己
     * 从来没跑通的原因之一（它同样缺 `X-Device-Mid` 与 `X-ZCode-Agent`）。
     * **模型名是发给服务端的**，大小写由服务端定义，不能由我们决定。
     *
     * ## 它仍然是静态快照
     *
     * 对方上新模型时这份表不会自己变。所以界面上必须允许手填模型名，
     * 而这个表只用于"给出好看的显示名"与"筛选套餐可用项"，不参与任何校验。
     */
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

    /**
     * 用"套餐可用模型"筛一遍 [MODEL_NAMES]。
     *
     * 匹配**不区分大小写**：服务端在 `capabilities` 里回的是哪种大小写不由我们决定
     * （官方的规范化函数存在本身就说明它见过小写），而这张表的 key 是官方规范形式。
     * 用 `==` 比对的话，一次大小写差异就会让筛选**静默筛空** → 回落到整张表 →
     * 用户选的仍是一个服务端可能不认的写法。
     *
     * 三种情况分开处理，与参考实现一致：
     *  - 套餐列表为空（额度没读到）→ **返回全部**，让用户至少还能选、还能手填；
     *  - 筛完为空（套餐里的模型不在我们的静态表里，说明对方上新了）→ 也返回全部，
     *    否则用户会看到一个空列表，比"多出几个用不了的"更糟；
     *  - 正常 → 只留交集。
     */
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
