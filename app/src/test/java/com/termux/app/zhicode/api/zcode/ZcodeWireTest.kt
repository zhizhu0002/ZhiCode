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
    fun defaultHeadersCarryOnlyWhatIsFunctionallyNeeded() {
        val headers = ZcodeWire.headers("code-123", null)
        assertEquals("Bearer code-123", headers["authorization"])
        assertEquals("application/json", headers["content-type"])
        assertEquals("text/event-stream", headers["accept"])
        assertEquals(ZcodeWire.ANTHROPIC_VERSION, headers["anthropic-version"])
        // 身份类的头一条都不预置 —— 那是用户的事，见 ZcodeWire 的类注释。
        assertFalse(headers.containsKey("originator"))
        assertFalse(headers.containsKey("x-zcode-agent"))
    }

    @Test
    fun userHeadersOverrideDefaultsSoImpersonationIsPossible() {
        // 覆盖是唯一能让"网关要求特定 UA"生效的方式；不覆盖的话这个字段就没用了。
        val headers = ZcodeWire.headers("k", """{"User-Agent":"ZCode/3.14.0"}""")
        assertEquals("ZCode/3.14.0", headers["User-Agent"])
        assertEquals("Bearer k", headers["authorization"])
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
    }

    @Test
    fun balanceEndpointNeedsVersionAndPlatform() {
        // 缺取值时**不发请求**：原样带 {v} 发出去只会 404，而 404 看不出是占位符没替换。
        val error = runCatching { ZcodeWire.balanceEndpoint("https://gw", emptyMap()) }.exceptionOrNull()
        assertNotNull(error)
        assertTrue("要说清缺哪一项", error!!.message!!.contains("X-ZCode-App-Version"))

        val url = ZcodeWire.balanceEndpoint(
            "https://gw.example.com",
            mapOf("X-ZCode-App-Version" to "3.14.0", "x-platform" to "linux-x64"),
        )
        assertTrue("请求头名要大小写不敏感：$url", url.contains("app_version=3.14.0"))
        assertTrue(url.contains("platform=linux-x64"))
        assertFalse("占位符必须被替换掉", url.contains("{v}") || url.contains("{p}"))
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
