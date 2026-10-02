package com.termux.app.zhicode.api.zcode

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ZcodeWire] 的纯逻辑测试。
 *
 * 这一层没有 I/O、不碰 Android，但它是**最容易错又最难查**的部分：请求体形状写错、
 * 请求头被覆盖、模型列表结构变了 —— 在真机上只表现为「回复不完整」或「列表是空的」，
 * 而这两句话都指不回具体哪一行。所以每条断言都对着一种具体的坏法。
 */
class ZcodeWireTest {

    // ------------------------------------------------------------ 端点

    @Test
    fun endpointTrimsTrailingSlashesButKeepsThePath() {
        assertEquals(
            "https://gw.example.com/api/v1/plan/anthropic/v1/messages",
            ZcodeWire.messagesEndpoint("https://gw.example.com/api/v1/plan/"),
        )
        assertEquals(
            "https://gw.example.com/api/v1/plan/anthropic/v1/messages",
            ZcodeWire.messagesEndpoint("  https://gw.example.com/api/v1/plan  "),
        )
    }

    @Test
    fun endpointNeverInventsAVersionSegment() {
        // 叶子整段来自常量，不由我们拼装；这里钉的是"基址原样保留、只接上叶子"。
        // 替网关补 /v1 只会拼出 404，而现象是"请求发不出去"，看不出路径被我们改过。
        assertEquals(
            "https://gw.example.com/anthropic/v1/messages",
            ZcodeWire.messagesEndpoint("https://gw.example.com"),
        )
        assertEquals(
            "https://gw.example.com/anthropic/v1/messages",
            ZcodeWire.messagesEndpoint("https://gw.example.com/"),
        )
    }

    // ------------------------------------------------------------ 请求头

    @Test
    fun defaultHeadersCarryTheOfficialSourceHeaderSet() {
        val headers = ZcodeWire.headers(
            baseUrl = "https://gw.example.com/api/v1/plan",
            apiKey = "code-123",
            extraHeaders = null,
            deviceMid = "dev-mid-1",
        )
        assertEquals("Bearer code-123", headers["authorization"])
        assertEquals("application/json", headers["content-type"])
        assertEquals("text/event-stream", headers["accept"])
        assertEquals(ZcodeWire.ANTHROPIC_VERSION, headers["anthropic-version"])

        // 缺 X-Device-Mid 会被服务端判 parameter error（官方 stdioDeviceMid.ts 的注释），
        // 所以它必须默认带上。
        assertEquals("dev-mid-1", headers["x-device-mid"])
        // UA 与 X-ZCode-App-Version 必须说同一个版本：两处不一致是自相矛盾。
        assertEquals("ZCode/${ZcodeWire.DEFAULT_APP_VERSION}", headers["user-agent"])
        assertEquals(ZcodeWire.DEFAULT_APP_VERSION, headers["x-zcode-app-version"])
        // HTTP-Referer 由 baseUrl 推 origin，不写死域名。
        assertEquals("https://gw.example.com", headers["http-referer"])
        assertEquals(ZcodeWire.DEFAULT_PLATFORM, headers["x-platform"])
        assertEquals(ZcodeWire.DEFAULT_OS_CATEGORY, headers["x-os-category"])
        assertEquals(ZcodeWire.DEFAULT_RELEASE_CHANNEL, headers["x-release-channel"])
        assertEquals(ZcodeWire.DEFAULT_SOURCE_TITLE, headers["x-title"])
        assertTrue("语言/时区要真发出去：${headers["x-client-language"]}", !headers["x-client-language"].isNullOrBlank())
        assertTrue(!headers["x-client-timezone"].isNullOrBlank())

        // 请求级归因头**只在会话请求**上带，这里没传就不该出现。
        assertFalse(headers.containsKey("x-request-id"))
        assertFalse(headers.containsKey("x-zcode-session-type"))
    }

    @Test
    fun sessionOnlyAttributionHeadersAreAddedWhenAsked() {
        val headers = ZcodeWire.headers(
            baseUrl = "https://gw.example.com",
            apiKey = "k",
            extraHeaders = null,
            deviceMid = "d",
            requestId = "req-1",
            traceId = "trace-1",
            sessionType = ZcodeWire.SESSION_TYPE_MAIN,
            agent = ZcodeWire.DEFAULT_AGENT,
        )
        assertEquals("req-1", headers["x-request-id"])
        assertEquals("trace-1", headers["x-zcode-trace-id"])
        assertEquals("main", headers["x-zcode-session-type"])
        // 官方模型请求路径上有它、额度路径上没有（model-config.ts vs zcode-source-headers.ts）。
        // 缺它正是真机上会话被拒 405/3012 的原因，所以它必须能被显式带上。
        assertEquals("glm", headers["x-zcode-agent"])
    }

    @Test
    fun theAgentHeaderIsNotSentUnlessAskedSoTheQuotaPathStaysUnchanged() {
        // 额度请求刚被证明可用（补上 X-Device-Mid 之后），所以不去动它：
        // X-ZCode-Agent 只给模型请求。
        val headers = ZcodeWire.headers("https://gw.example.com", "k", null, "d")
        assertFalse(headers.containsKey("x-zcode-agent"))
    }

    @Test
    fun aBlankDeviceMidIsOmittedRatherThanSentEmpty() {
        // 发空串同样会被判参数错误，但报错一样看不懂 —— 不如不发，让日志里能看出是"没有"。
        val headers = ZcodeWire.headers("https://gw.example.com", "k", null, "   ")
        assertFalse(headers.containsKey("x-device-mid"))
    }

    @Test
    fun anUnparsableBaseUrlOmitsTheRefererInsteadOfSendingHalfOfIt() {
        // 半截 origin 换来的是同样看不懂的 4xx；不发这个头至少不会误报来源。
        val headers = ZcodeWire.headers("not a url", "k", null, "d")
        assertFalse(headers.containsKey("http-referer"))
        assertNull(ZcodeWire.originOf("not a url"))
    }

    @Test
    fun userHeadersOverrideDefaultsSoImpersonationIsPossible() {
        // 覆盖是唯一能让"网关要求特定 UA"生效的方式；不覆盖的话这个字段就没用了。
        val headers = ZcodeWire.headers(
            baseUrl = "https://gw.example.com",
            apiKey = "k",
            extraHeaders = """{"User-Agent":"ZCode/3.14.0","X-Platform":"darwin-arm64"}""",
            deviceMid = "d",
        )
        assertEquals("ZCode/3.14.0", headers["User-Agent"])
        // 覆盖要**替换**而不是并存：同名的另一种大小写必须被去掉，否则会发两条头出去。
        assertFalse("不许同时留下默认的那条 X-Platform", headers.containsKey("x-platform"))
        assertEquals("darwin-arm64", headers["X-Platform"])
        assertEquals("Bearer k", headers["authorization"])
        // 只改 UA 不会连带改版本头（它们是两件事，谁改谁生效）。
        assertEquals(ZcodeWire.DEFAULT_APP_VERSION, headers["x-zcode-app-version"])
    }

    @Test
    fun theUserAgentFollowsTheVersionHeaderWhenTheUserOverridesIt() {
        // UA 与版本头说两个版本是自相矛盾的，反而更容易被风控挑出来。
        // 所以版本由用户给定时，默认 UA 必须跟着走。
        val headers = ZcodeWire.headers("https://gw.example.com", "k", """{"X-ZCode-App-Version":"9.9.9"}""", "d")
        assertEquals("ZCode/9.9.9", headers["user-agent"])
        assertEquals("9.9.9", headers["X-ZCode-App-Version"])
    }

    @Test
    fun malformedJsonIsReportedInsteadOfSilentlyIgnored() {
        val parsed = ZcodeWire.parseExtraHeaders("{这不是 JSON}")
        assertNotNull("写错的 JSON 必须报出来，不能当成「没配」", parsed.error)
        assertTrue(parsed.values.isEmpty())
    }

    @Test
    fun nonStringValuesAreAcceptedAndNullsSkipped() {
        // 手写 JSON 时很容易漏引号：{"X-Ver": 1} 应当能用，而不是整段拒绝。
        val parsed = ZcodeWire.parseExtraHeaders("""{"X-Ver":1,"X-Flag":true,"X-Null":null}""")
        assertNull(parsed.error)
        assertEquals("1", parsed.values["X-Ver"])
        assertEquals("true", parsed.values["X-Flag"])
        assertFalse("null 值的头没有意义，不该发一个字符串 \"null\" 出去",
            parsed.values.containsKey("X-Null"))
    }

    @Test
    fun blankHeadersMeanNoExtraHeaders() {
        assertNull(ZcodeWire.parseExtraHeaders(null).error)
        assertTrue(ZcodeWire.parseExtraHeaders("").values.isEmpty())
        assertTrue(ZcodeWire.parseExtraHeaders("   ").values.isEmpty())
    }

    // ------------------------------------------------------------ 请求体

    @Test
    fun bodyUsesCacheBlocksForSystemPrompt() {
        // 传字符串会让 Anthropic 的缓存语义整体失效：不报错，只是变慢、变贵。
        val system = ZcodeWire.buildSystem("你是助手")
        assertEquals(1, system.length())
        assertEquals("text", system.getJSONObject(0).getString("type"))
        assertEquals("你是助手", system.getJSONObject(0).getString("text"))
        assertEquals("ephemeral", system.getJSONObject(0).getJSONObject("cache_control").getString("type"))
    }

    @Test
    fun emptySystemPromptGivesAnEmptyArrayNotAnEmptyBlock() {
        assertEquals(0, ZcodeWire.buildSystem("").length())
        assertEquals(0, ZcodeWire.buildSystem(null).length())
        assertEquals(0, ZcodeWire.buildSystem("   ").length())
    }

    @Test
    fun toolsLoseTheirCacheControl() {
        val tools = JSONArray()
            .put(JSONObject().put("name", "bash").put("cache_control", JSONObject().put("type", "ephemeral")))
            .put(JSONObject().put("name", "read"))
        val stripped = ZcodeWire.strippedTools(tools)!!
        assertEquals(2, stripped.length())
        assertFalse("工具上的 cache_control 必须剥掉", stripped.getJSONObject(0).has("cache_control"))
        assertEquals("bash", stripped.getJSONObject(0).getString("name"))
        // 剥掉的是副本，原数组不能被改（调用方的历史还在用它）。
        assertTrue(tools.getJSONObject(0).has("cache_control"))
    }

    @Test
    fun noToolsMeansNoToolsField() {
        assertNull(ZcodeWire.strippedTools(null))
        assertNull(ZcodeWire.strippedTools(JSONArray()))
    }

    @Test
    fun metadataUserIdIsAJsonString() {
        // 写错类型时服务端通常只是忽略它 —— 不报错，所以只能靠断言钉住。
        val raw = ZcodeWire.metadataUserId("")
        assertTrue("必须是 JSON **字符串**而不是对象", raw.trim().startsWith("{"))
        val parsed = JSONObject(raw)
        assertEquals("", parsed.getString("device_id"))
    }

    @Test
    fun bodyHasTheFieldsThisGatewayRequires() {
        val body = ZcodeWire.buildRequestBody(
            model = "  glm-4.6  ",
            maxTokens = 8192,
            systemPrompt = "s",
            messages = JSONArray().put(JSONObject().put("role", "user")),
            tools = null,
            deviceId = "",
        )
        assertEquals("模型名要去掉首尾空格", "glm-4.6", body.getString("model"))
        assertEquals(8192, body.getInt("max_tokens"))
        assertTrue("必须开流式，否则解不出增量", body.getBoolean("stream"))
        assertEquals(1, body.getJSONArray("messages").length())
        assertTrue(body.has("metadata"))
        assertFalse("没有工具就不该出现 tools 字段", body.has("tools"))
    }

    // ------------------------------------------------------------ 响应解析

    @Test
    fun modelListAcceptsTheShapesTheseGatewaysActuallyReturn() {
        val objects = ZcodeWire.parseModelList(
            JSONObject().put(
                "data",
                JSONArray()
                    .put(JSONObject().put("id", "glm-4.6").put("display_name", "GLM-4.6"))
                    .put(JSONObject().put("name", "glm-4.5")),
            ),
        )
        assertEquals(listOf("glm-4.6", "glm-4.5"), objects.map { it.id })
        assertEquals("GLM-4.6", objects[0].displayName)
        // 没有 display_name 时显示名回落到 id，而不是一行空白。
        assertEquals("glm-4.5", objects[1].displayName)

        val strings = ZcodeWire.parseModelList(
            JSONObject().put("models", JSONArray().put("glm-4.6").put("glm-4.5")),
        )
        assertEquals(2, strings.size)
    }

    @Test
    fun unparseableModelListIsEmptyNotAnException() {
        // 对方调整结构不该让"选择模型"整页不可用：空列表 → 界面回落到手填模型名。
        assertEquals(0, ZcodeWire.parseModelList(null).size)
        assertEquals(0, ZcodeWire.parseModelList(JSONObject()).size)
        assertEquals(0, ZcodeWire.parseModelList(JSONObject().put("data", JSONArray())).size)
    }

    @Test
    fun balanceIsFoundDirectlyOrNested() {
        // 旧的"猜字段名"版本已删除：真实形状解出来之后，留着它就是同一件事两份实现，
        // 而且只有一份会被调用。这条用例改查真实形状。
        val payload = ZcodeWire.parseBalancePayload(
            JSONObject()
                .put("code", 0)
                .put(
                    "data",
                    JSONObject()
                        .put("server_time", 1000)
                        .put(
                            "balances",
                            JSONArray().put(
                                JSONObject()
                                    .put("show_name", "GLM-5.3")
                                    .put("total_units", 3_000_000)
                                    .put("remaining_units", 2_400_000)
                                    .put("expires_at", 1000 + 7200)
                                    .put("capabilities", JSONArray().put("model:glm-5.3").put("其他")),
                            ),
                        ),
                ),
        )
        assertEquals(1, payload.rows.size)
        assertEquals("GLM-5.3", payload.rows[0].showName)
        assertEquals(listOf("glm-5.3"), payload.rows[0].modelIds)
    }

    @Test
    fun glmDetectionIsOnlyUsedForHints() {
        assertTrue(ZcodeWire.looksLikeGlm("GLM-4.6"))
        assertTrue(ZcodeWire.looksLikeGlm(" glm-4.5-air "))
        assertFalse(ZcodeWire.looksLikeGlm("gpt-4o"))
        assertFalse(ZcodeWire.looksLikeGlm(null))
    }

    // ------------------------------------------------------------ 额度

    private fun balanceResponse(
        code: Int = 0,
        message: String = "",
        serverTime: Long = 1_000,
        balances: JSONArray? = JSONArray(),
    ): JSONObject = JSONObject()
        .put("code", code)
        .put("message", message)
        .put("data", JSONObject().put("server_time", serverTime).put("balances", balances))

    private fun balanceItem(
        name: String = "GLM-5.3",
        total: Long = 3_000_000,
        remaining: Long = 3_000_000,
        expiresAt: Long = 0,
        capabilities: JSONArray = JSONArray().put("model:glm-5.3"),
    ): JSONObject = JSONObject()
        .put("show_name", name)
        .put("total_units", total)
        .put("remaining_units", remaining)
        .put("expires_at", expiresAt)
        .put("capabilities", capabilities)

    @Test
    fun parsesBalancesAndKeepsOnlyModelCapabilities() {
        val payload = ZcodeWire.parseBalancePayload(
            balanceResponse(
                balances = JSONArray().put(
                    balanceItem(capabilities = JSONArray().put("model:glm-5.3").put("vision").put("model:glm-5.3-flash")),
                ),
            ),
        )
        assertEquals(1, payload.rows.size)
        // 只认 "model:" 前缀 —— 别的能力项是"这个套餐还有什么"，不是模型 id。
        assertEquals(listOf("glm-5.3", "glm-5.3-flash"), payload.rows[0].modelIds)
    }

    @Test
    fun nonZeroCodeIsReportedWithItsMessage() {
        // 返回空列表会把「你的码不对」显示成「你没有套餐」——两句话的排查方向完全不同。
        val error = runCatching {
            ZcodeWire.parseBalancePayload(balanceResponse(code = 40003, message = "登录态失效"))
        }.exceptionOrNull()
        assertNotNull(error)
        assertTrue(error!!.message!!.contains("登录态失效"))
        assertTrue(error.message!!.contains("40003"))
    }

    @Test
    fun missingDataIsReportedRatherThanSilentlyEmpty() {
        val error = runCatching {
            ZcodeWire.parseBalancePayload(JSONObject().put("code", 0))
        }.exceptionOrNull()
        assertNotNull("缺 data 必须报出来，不能当成「没有套餐」", error)
    }

    @Test
    fun countdownCoversAllFourBands() {
        // 四档的边界是整数秒，改一个数字就会让某一档说错话。
        assertEquals("永久有效", ZcodeWire.formatCountdown(0, 1000))
        assertEquals("已过期", ZcodeWire.formatCountdown(1000, 1000))
        assertEquals("已过期", ZcodeWire.formatCountdown(900, 1000))
        assertEquals("剩 59 分钟重置", ZcodeWire.formatCountdown(1000 + 3599, 1000))
        assertEquals("剩 1:00 重置", ZcodeWire.formatCountdown(1000 + 3600, 1000))
        assertEquals("剩 23:59 重置", ZcodeWire.formatCountdown(1000 + 86399, 1000))
        assertEquals("剩 1 天重置", ZcodeWire.formatCountdown(1000 + 86400, 1000))
    }

    @Test
    fun countdownPadsMinutesToTwoDigits() {
        // "剩 1:5 重置" 读起来像 1 小时 5 分，但不是这个格式该有的样子。
        assertEquals("剩 1:05 重置", ZcodeWire.formatCountdown(1000 + 3900, 1000))
    }

    @Test
    fun unitsUseWanAndYiLikeTheReference() {
        assertEquals("9999", ZcodeWire.formatUnits(9999))
        assertEquals("1.0万", ZcodeWire.formatUnits(10_000))
        assertEquals("300.0万", ZcodeWire.formatUnits(3_000_000))
        assertEquals("1.50亿", ZcodeWire.formatUnits(150_000_000))
    }

    @Test
    fun quotaFractionSurvivesZeroTotal() {
        val zero = ZcodeWire.BalanceRow("x", 0, 0, 0, emptyList())
        assertEquals(0f, ZcodeWire.quotaFraction(zero))
        val half = ZcodeWire.BalanceRow("x", 100, 50, 0, emptyList())
        assertEquals(0.5f, ZcodeWire.quotaFraction(half))
        // 剩余大于总量（服务端加了额度）时不能给出 >1 的比例：进度条会画到框外。
        val over = ZcodeWire.BalanceRow("x", 100, 150, 0, emptyList())
        assertEquals(1f, ZcodeWire.quotaFraction(over))
    }

    @Test
    fun entitledIdsAreDeduplicatedInOrder() {
        val payload = ZcodeWire.BalancePayload(
            1000,
            listOf(
                ZcodeWire.BalanceRow("a", 1, 1, 0, listOf("glm-5.3", "glm-5.3-flash")),
                ZcodeWire.BalanceRow("b", 1, 1, 0, listOf("glm-5.3")),
            ),
        )
        assertEquals(listOf("glm-5.3", "glm-5.3-flash"), ZcodeWire.entitledModelIds(payload))
        assertTrue(ZcodeWire.entitledModelIds(null).isEmpty())
    }

    @Test
    fun entitledFilterFallsBackToTheFullList() {
        // 三种情况：没有套餐信息、套餐里的模型不在我们的静态表里、正常交集。
        assertEquals(ZcodeWire.MODEL_NAMES, ZcodeWire.filterEntitled(null))
        assertEquals(ZcodeWire.MODEL_NAMES, ZcodeWire.filterEntitled(emptyList()))
        assertEquals(
            "套餐里的模型全不在静态表里时必须退回全部 —— 空列表比多几项更糟",
            ZcodeWire.MODEL_NAMES,
            ZcodeWire.filterEntitled(listOf("some-future-model")),
        )
        assertEquals(
            listOf("GLM 5.3", "GLM 5.3 Flash"),
            ZcodeWire.filterEntitled(listOf("glm-5.3", "glm-5.3-flash")).values.toList(),
        )
        // 匹配必须**不区分大小写**：服务端 capabilities 里回的是哪种大小写不由我们决定
        // （官方的规范化函数存在本身就说明它见过小写）。用 == 比对时一次大小写差异
        // 就会静默筛空 → 回落到整张表 → 用户选的仍是一个服务端可能不认的写法。
        assertEquals(
            "小写、大写、混合大小写都要认",
            listOf("GLM 5.3", "GLM 5.3 Flash"),
            ZcodeWire.filterEntitled(listOf("GLM-5.3", "glm-5.3-FLASH")).values.toList(),
        )
    }

    @Test
    fun modelIdsAreTheOfficialCanonicalUppercaseForm() {
        // 官方规范 id 是大写（official-glm-model-id.ts 的 OFFICIAL_GLM_MODEL_IDS），
        // 内置模型名单也用大写形式。模型名是**发给服务端的**，大小写由服务端定义。
        assertTrue("GLM-5.3 必须在大写形式下存在：${ZcodeWire.MODEL_NAMES.keys}",
            ZcodeWire.MODEL_NAMES.containsKey("GLM-5.3"))
        assertTrue(ZcodeWire.MODEL_NAMES.containsKey("GLM-5.3-Flash"))
        assertFalse("不许留小写的旧键（那是另一个应用的表）",
            ZcodeWire.MODEL_NAMES.containsKey("glm-5.3"))
    }

    @Test
    fun normalizeModelFoldsToTheCanonicalFormButKeepsUnknownNames() {
        // 用户手打 glm-5.3 / GLM-5.3 / Glm-5.3 都要发成同一个官方形式。
        assertEquals("GLM-5.3", ZcodeWire.normalizeModel("glm-5.3"))
        assertEquals("GLM-5.3", ZcodeWire.normalizeModel("GLM-5.3"))
        assertEquals("GLM-5.3", ZcodeWire.normalizeModel("  Glm-5.3  "))
        assertEquals("GLM-5.3-Flash", ZcodeWire.normalizeModel("glm-5.3-flash"))
        // 对不上的**原样发**：对方上新模型时我们这张表落后，用户的写法就是唯一能用的写法。
        // 硬套一个"最接近"的名字只会把请求发到一个不存在的模型上。
        assertEquals("glm-9.9-preview", ZcodeWire.normalizeModel("glm-9.9-preview"))
        assertEquals("", ZcodeWire.normalizeModel(null))
        assertEquals("", ZcodeWire.normalizeModel("   "))
    }

    @Test
    fun blockedErrorBecomesAnActionableMessageInsteadOfRawJson() {
        // 3012 看起来像"请求写错了"（HTTP 405 + 一段 JSON），实际是账号级风控，
        // 且官方客户端同样中招（zai-org/feedback#716，官方 3.14.4 上一个字也失败）。
        // 这里要保证它不再以原始 JSON 的形式出现 —— 那会让人往错的方向查一整轮。
        val raw = """{"code":3012,"msg":"request has been blocked due to unusual activity.","logid":"202610021744498f418cccebcde8da33d9"}"""
        val message = ZcodeWire.describeHttpFailure(405, raw)
        assertNotNull("3012 必须被识别出来", message)
        assertTrue("要说清不是本应用的问题：$message", message!!.contains("不是本应用的问题"))
        assertTrue("要说清官方客户端同样会中招：$message", message.contains("官方客户端"))
        // 要给出可行动作。注意**不能**在这里断言具体域名：纯逻辑层不许认识主机名
        // （ZcodeProtocolTest 有一条守卫盯着），所以那句话说的是"网关提供方"。
        assertTrue("要给出可行动作：$message", message.contains("联系") && message.contains("解除"))
        assertTrue("要给一条用户自己就能试的退路：$message", message.contains("换一个网关地址"))
        // logid 是向官方报障时唯一有用的东西，必须带出来。
        assertTrue("必须带上 logid：$message", message.contains("202610021744498f418cccebcde8da33d9"))
        assertFalse("不要把原始 JSON 整段丢给用户", message.contains("\"code\":3012"))
    }

    @Test
    fun otherFailuresKeepTheirOriginalTextSoNothingIsSwallowed() {
        // 只翻译认识的那一个码：别的错误原样留着，否则会把真正有用的服务端说明吞掉。
        assertNull("401 不该被当成风控",
            ZcodeWire.describeHttpFailure(401, """{"code":3001,"msg":"parameter error"}"""))
        assertNull("3012 之外一律不认",
            ZcodeWire.describeHttpFailure(400, """{"code":1210,"msg":"invalid request"}"""))
        // 不是 JSON（比如网关回了一页 HTML）时也要 null，由调用方回落。
        assertNull(ZcodeWire.describeHttpFailure(502, "<html>bad gateway</html>"))
        assertNull(ZcodeWire.describeHttpFailure(405, null))
        // 没有 logid 也要给出指引，不能因为缺字段就整段失效。
        val noLogId = ZcodeWire.describeHttpFailure(405, """{"code":3012,"msg":"blocked"}""")
        assertNotNull(noLogId)
        assertFalse("没有 logid 时不该硬编一段空的", noLogId!!.contains("logid："))
    }

    @Test
    fun balanceEndpointCarriesOnlyTheVersionParameter() {
        // 没填版本时用内置默认（官方那边也是编译期常量），**不再**报错不发请求：
        // 代码有默认值却让用户去找一个数字，只会多一次失败。
        val fallback = ZcodeWire.balanceEndpoint("https://gw.example.com", emptyMap())
        assertTrue("默认版本要真的用上：$fallback", fallback.contains("app_version=${ZcodeWire.DEFAULT_APP_VERSION}"))

        val url = ZcodeWire.balanceEndpoint(
            "https://gw.example.com",
            mapOf("x-zcode-app-version" to "3.14.3"),
        )
        assertTrue("请求头名要大小写不敏感：$url", url.contains("app_version=3.14.3"))
        assertFalse("占位符必须被替换掉", url.contains("{v}"))

        // 官方端点**没有** platform 参数（zcodeEndpoint.ts + zaiStartPlanBilling.ts）。
        // 多带一个参数时服务端只回 "parameter error"，不会说是哪个参数多余 ——
        // 所以这条断言是防止它被"顺手加回来"。
        for (candidate in listOf(fallback, url)) {
            assertFalse("额度端点不许带 platform：$candidate", candidate.contains("platform"))
        }
    }

    @Test
    fun messagesPathIsTheAnthropicOneNotBareMessages() {
        // 那个网关把会话端点挂在 /anthropic/v1/messages 下。少一段就是 404，
        // 而报错只会说"请求失败"，看不出路径不对。
        assertEquals(
            "https://gw.example.com/api/v1/plan/anthropic/v1/messages",
            ZcodeWire.messagesEndpoint("https://gw.example.com/api/v1/plan"),
        )
    }
}
