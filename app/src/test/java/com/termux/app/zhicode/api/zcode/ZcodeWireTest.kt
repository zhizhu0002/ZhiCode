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
            "https://gw.example.com/api/v1/plan/messages",
            ZcodeWire.messagesEndpoint("https://gw.example.com/api/v1/plan/"),
        )
        assertEquals(
            "https://gw.example.com/api/v1/plan/messages",
            ZcodeWire.messagesEndpoint("  https://gw.example.com/api/v1/plan  "),
        )
    }

    @Test
    fun endpointNeverInventsAVersionSegment() {
        // 替网关补 /v1 只会拼出 404，而现象是"请求发不出去"，看不出路径被我们改过。
        assertEquals(
            "https://gw.example.com/messages",
            ZcodeWire.messagesEndpoint("https://gw.example.com"),
        )
        assertEquals(
            "https://gw.example.com/messages",
            ZcodeWire.messagesEndpoint("https://gw.example.com/"),
        )
    }

    @Test
    fun balanceEndpointIsSeparateFromMessages() {
        assertEquals(
            "https://gw.example.com/api/billing/balance",
            ZcodeWire.balanceEndpoint("https://gw.example.com/api"),
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
        assertEquals("12.5", ZcodeWire.parseBalance(JSONObject().put("balance", "12.5")))
        assertEquals("7", ZcodeWire.parseBalance(JSONObject().put("data", JSONObject().put("quota", 7))))
        assertNull("找不到就返回 null，界面据此不显示这一行", ZcodeWire.parseBalance(JSONObject()))
        assertNull(ZcodeWire.parseBalance(null))
    }

    @Test
    fun glmDetectionIsOnlyUsedForHints() {
        assertTrue(ZcodeWire.looksLikeGlm("GLM-4.6"))
        assertTrue(ZcodeWire.looksLikeGlm(" glm-4.5-air "))
        assertFalse(ZcodeWire.looksLikeGlm("gpt-4o"))
        assertFalse(ZcodeWire.looksLikeGlm(null))
    }
}
