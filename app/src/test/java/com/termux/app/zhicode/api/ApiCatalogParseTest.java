package com.termux.app.zhicode.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.termux.app.zhicode.model.SessionConfig;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

/**
 * 模型目录的**纯解析**与协议名枚举的行为测试。
 *
 * <h3>为什么这些用例重要</h3>
 * 目录响应来自任意的自建/第三方网关，形状并不统一：缺字段、类型不对、
 * 塞进一行说明、id 里带控制字符、同一模型出现两次、列表长到几千项。
 * 这些都不会让编译失败，只会让设置界面出现空白项、重复项，
 * 或者把一条非法 id 发给服务端换来 400。
 *
 * <p>不需要网络：{@link ModelCatalogClient#parse(String)} 是纯函数，
 * 那几个错误分支（缺密钥、协议没有目录接口）在任何网络动作之前就返回了。
 */
public class ApiCatalogParseTest {

    // ============================================================ 解析规则

    @Test
    public void keepsFirstOccurrenceOfDuplicatedId() throws Exception {
        // 同一模型在列表里出现两次是常见的（网关把别名也列出来）。
        // 留下第一次出现的那条：服务端把常用的排在前面。
        List<ModelDescriptor> models = parse(
                items(new JSONObject().put("id", "gpt-x").put("display_name", "第一次"),
                        new JSONObject().put("id", "gpt-x").put("display_name", "第二次")));
        assertEquals(1, models.size());
        assertEquals("第一次", models.get(0).displayName);
    }

    @Test
    public void dropsIdsThatCouldNotBeSentAsARequest() throws Exception {
        JSONObject blank = new JSONObject().put("id", "   ");
        JSONObject control = new JSONObject().put("id", "bad\u0001id");
        JSONObject tooLong = new JSONObject().put("id", repeat('a', 161));
        JSONObject exactlyAtLimit = new JSONObject().put("id", repeat('b', 160));
        JSONObject noId = new JSONObject();

        List<ModelDescriptor> models = parse(items(blank, control, tooLong, exactlyAtLimit, noId));
        // 只剩边界内那一条：空/含控制字符/超长都发不出去，留在下拉框里是害人。
        assertEquals(1, models.size());
        assertEquals(repeat('b', 160), models.get(0).id);
    }

    @Test
    public void fallsBackFromDisplayNameToNameToId() throws Exception {
        List<ModelDescriptor> models = parse(items(
                new JSONObject().put("id", "a").put("display_name", "有名").put("name", "别名"),
                new JSONObject().put("id", "b").put("name", "只有别名"),
                new JSONObject().put("id", "c")));
        assertEquals("有名", models.get(0).displayName);
        assertEquals("只有别名", models.get(1).displayName);
        assertEquals("c", models.get(2).displayName);
    }

    @Test
    public void skipsNonObjectEntriesInsteadOfFailing() throws Exception {
        // 有些网关会在 data 里塞一行说明字符串或 null。整批失败太脆弱。
        JSONArray data = new JSONArray()
                .put("下面开始是模型")
                .put(JSONObject.NULL)
                .put(new JSONObject().put("id", "real"));
        assertEquals(1, parse(data).size());
    }

    @Test
    public void sortsByIdBecauseServerOrderIsNotStable() throws Exception {
        List<ModelDescriptor> models = parse(items(
                new JSONObject().put("id", "b"),
                new JSONObject().put("id", "zz"),
                new JSONObject().put("id", "a")));
        assertEquals("a", models.get(0).id);
        assertEquals("b", models.get(1).id);
        assertEquals("zz", models.get(2).id);
    }

    @Test
    public void stopsAtTheModelCap() throws Exception {
        JSONArray data = new JSONArray();
        for (int i = 0; i < 300; i++) data.put(new JSONObject().put("id", "model-" + i));
        assertEquals(250, parse(data).size());
    }

    @Test
    public void rejectsPayloadWithoutDataArray() {
        // 顶层没有 data 说明这不是目录响应（地址填错了 / 拿到的是别的接口）。
        // 静默返回空列表会让界面显示「没有模型」，真正的原因就被藏起来了。
        for (String body : new String[]{"{}", "{\"data\":{}}", "{\"models\":[]}", "not json"}) {
            try {
                ModelCatalogClient.parse(body);
                fail("必须拒绝的响应: " + body);
            } catch (Exception expected) {
                assertTrue("失败要说明是格式问题: " + expected.getMessage(),
                        expected.getMessage().contains("格式") || expected instanceof org.json.JSONException);
            }
        }
    }

    // ============================================================ 值对象

    @Test
    public void descriptorNeverExposesAnEmptyDisplayName() {
        // 界面会直接把 displayName 画出来；为空会显示成一行空白，
        // 用户看不出那是哪个模型。回落到 id 至少能看出标识。
        assertEquals("id-1", new ModelDescriptor("id-1", null).displayName);
        assertEquals("id-1", new ModelDescriptor("id-1", "   ").displayName);
        assertEquals("Sona", new ModelDescriptor("id-1", "  Sona  ").displayName);
    }

    // ============================================================ 协议名

    @Test
    public void resolvesEveryKnownWireNameAndAlias() {
        assertEquals(ApiProtocol.ANTHROPIC, ApiProtocol.fromWire("anthropic"));
        assertEquals(ApiProtocol.OPENAI_CHAT, ApiProtocol.fromWire("openai-chat"));
        assertEquals(ApiProtocol.OPENAI_RESPONSES, ApiProtocol.fromWire("openai-responses"));
        // codex-responses **不是**别名：它有自己的端点、UA、四个关联头与请求体字段，
        // 而且模型目录要单独排除。曾经按别名处理，后果是 UI 里选不到 Codex、
        // 引擎里那条已实现的路径永远点不亮（见 ApiProtocol 的类注释）。
        assertEquals(ApiProtocol.CODEX_RESPONSES, ApiProtocol.fromWire("codex-responses"));
        // openai-compatible 仍是真别名：报文与 openai-chat 完全一致。
        assertEquals(ApiProtocol.OPENAI_CHAT, ApiProtocol.fromWire("openai-compatible"));
    }

    @Test
    public void wireNamesAreFrozenBecauseTheyAreStoredInSettings() {
        // 这些字符串写进了设置与会话文件，改动等于让旧配置变成未知协议。
        assertEquals("anthropic", ApiProtocol.ANTHROPIC.wireName());
        assertEquals("openai-chat", ApiProtocol.OPENAI_CHAT.wireName());
        assertEquals("openai-responses", ApiProtocol.OPENAI_RESPONSES.wireName());
        assertEquals("codex-responses", ApiProtocol.CODEX_RESPONSES.wireName());
    }

    @Test
    public void matchingIsExactSoTyposSurfaceInsteadOfGuessing() {
        // 不 trim、不忽略大小写：静默接受 "Anthropic" 会让人以为两种写法都行，
        // 而真正的后果是「有些机器上能用、有些不能」。
        assertNull(ApiProtocol.fromWire("Anthropic"));
        assertNull(ApiProtocol.fromWire(" anthropic"));
        assertNull(ApiProtocol.fromWire(""));
        assertNull(ApiProtocol.fromWire(null));
        assertNull(ApiProtocol.fromWire("gemini"));
    }

    @Test
    public void emptyProtocolIsRejectedWhileAbsentProtocolDefaultsToAnthropic() {
        // 只在「配置里根本没有协议字段」时回落；空串是一个写坏的值，要报错。
        SessionConfig empty = new SessionConfig();
        empty.protocol = "";
        try {
            ModelProviders.forConfig(empty);
            fail("空协议名必须报错，而不是回落到默认协议");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("没有 Java 原生实现"));
        }
    }

    // ============================================================ 网络之前的检查

    @Test
    public void fetchRejectsMissingKeyAndMissingCatalogBeforeTouchingTheNetwork() {
        // 这两条都在开连接之前返回，所以这个用例不需要网络也不会有超时。
        SessionConfig noKey = config("openai-responses", "https://example.com");
        noKey.apiKey = "   ";
        assertFailsWith("密钥", noKey);

        // 认识的协议名之外一律「没有目录接口」，让界面回落到手填模型名。
        assertFailsWith("模型目录", config("gemini", "https://example.com"));
    }

    @Test
    public void fetchReportsMissingBaseUrlBeforeUnknownProtocol() {
        // 顺序是刻意的：地址没配是**配置缺失**，用户要去做的是去填地址；
        // 若反过来报「该协议没有目录接口」，用户会去改协议，问题却依然在。
        assertFailsWith("Base URL", config("gemini", "  "));
    }

    // ============================================================ 工具

    /**
     * 按真实响应的形状走一遍解析：外层的 {@code data} 数组也是被测契约的一部分
     * （缺它必须报错），所以这里不把数组直接递给解析函数。
     */
    private static List<ModelDescriptor> parse(JSONArray data) throws Exception {
        return ModelCatalogClient.parse(new JSONObject().put("data", data).toString());
    }

    private static JSONArray items(JSONObject... entries) {
        JSONArray data = new JSONArray();
        for (JSONObject entry : entries) data.put(entry);
        return data;
    }

    private static void assertFailsWith(String expectedText, SessionConfig config) {
        try {
            new ModelCatalogClient().fetch(config, null);
            fail("必须失败: " + expectedText);
        } catch (IllegalStateException expected) {
            assertTrue("失败信息要包含「" + expectedText + "」，实际: " + expected.getMessage(),
                    expected.getMessage().contains(expectedText));
        } catch (Exception unexpected) {
            fail("应当抛 IllegalStateException，实际是 " + unexpected);
        }
    }

    private static SessionConfig config(String protocol, String baseUrl) {
        SessionConfig config = new SessionConfig();
        config.protocol = protocol;
        config.baseUrl = baseUrl;
        config.apiKey = "test-key";
        return config;
    }

    private static String repeat(char value, int count) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < count; i++) out.append(value);
        return out.toString();
    }
}
