package com.termux.app.zhicode.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.termux.app.zhicode.api.compat.ReasoningMapper;
import com.termux.app.zhicode.model.SessionConfig;

import org.junit.Test;

/**
 * api/ 层里那些「纯函数」的**行为**测试。
 *
 * <h3>为什么这一批和 app/tests/ 下的不一样</h3>
 * app/tests/ 下那 22 个是文本级断言：读源码字符串，防的是「重写时漏掉一个分支」。
 * 它们证明不了运行时行为 —— 而档位映射写错一档、路径去重判断反了、
 * 快照去重算法变了，都不会让编译失败，只会让请求被拒或参数丢失。
 *
 * <p>这一批直接在 JVM 上跑真实实现。可行性来自一个事实：**整个 api/ 包没有任何
 * {@code android.*} import**（只有 java.* 与 org.json），所以不需要 Robolectric 或
 * 模拟器。用的是 org.json 的真实实现，不是 android.jar 里那些抛异常的桩。
 *
 * <h3>覆盖范围</h3>
 * {@link ReasoningMapper} 的全部映射分支、两个端点/校验类的边界条件、
 * {@code snapshotDelta} 的去重语义、{@link ModelProviders} 的路由。
 * 三个 provider 的 SSE 解析需要真实 HTTP 往返，放在另一批（{@code ApiSseBehaviourTest}）。
 */
public class ApiPureLogicTest {

    // ============================================================ ReasoningMapper

    @Test
    public void normalizeRequestedEffort_onlyTrimsAndLowercases() {
        // 它**只**做 trim + 小写，不做档位归一。所以 "XXHigh" 出来后仍是 "xxhigh" ——
        // 把 xxhigh 别名成 xhigh 是后续 wire 函数的责任（见下面的 Responses 用例）。
        // 这两步分开是有意的：中间结果要能被调试时看见。
        assertEquals("high", ReasoningMapper.normalizeRequestedEffort("  HIGH "));
        assertEquals("xxhigh", ReasoningMapper.normalizeRequestedEffort("XXHigh"));
        assertEquals("max", ReasoningMapper.normalizeRequestedEffort("Max"));
        assertNull(ReasoningMapper.normalizeRequestedEffort(null));
        assertNull(ReasoningMapper.normalizeRequestedEffort(""));
        // 纯空白归一成 null 而不是空串：调用方靠 null 判断“没指定档位”。
        assertNull(ReasoningMapper.normalizeRequestedEffort("   "));
    }

    @Test
    public void numericBudget_mapsToBuckets() {
        // 分界值本身要落在下界那一档：<=2048 才是 minimal。
        assertEquals("minimal", ReasoningMapper.openAIChatEffort("1"));
        assertEquals("minimal", ReasoningMapper.openAIChatEffort("2048"));
        assertEquals("low", ReasoningMapper.openAIChatEffort("2049"));
        assertEquals("low", ReasoningMapper.openAIChatEffort("4096"));
        assertEquals("medium", ReasoningMapper.openAIChatEffort("4097"));
        assertEquals("medium", ReasoningMapper.openAIChatEffort("8192"));
        assertEquals("high", ReasoningMapper.openAIChatEffort("8193"));
        assertEquals("high", ReasoningMapper.openAIChatEffort("16384"));
        assertEquals("xhigh", ReasoningMapper.openAIChatEffort("16385"));
    }

    @Test
    public void numericEffort_rejectsNonNumeric() {
        // 负数、带符号、带空格、含字母都不是「纯数字」，应当走档位名分支而不是数字分支。
        assertNull(ReasoningMapper.openAIChatEffort("-1"));
        assertNull(ReasoningMapper.openAIChatEffort("+100"));
        assertNull(ReasoningMapper.openAIChatEffort("1e3"));
        assertNull(ReasoningMapper.openAIChatEffort("12a"));
    }

    @Test
    public void openAIChatEffort_keepsLowTiersAndCollapsesOnlyHighEnd() {
        // Chat 侧刻意把 max/ultra 折叠成 xhigh —— 这个协议没有比 xhigh 更高的表达。
        assertEquals("xhigh", ReasoningMapper.openAIChatEffort("max"));
        assertEquals("xhigh", ReasoningMapper.openAIChatEffort("ultra"));
        assertEquals("xhigh", ReasoningMapper.openAIChatEffort("xxhigh"));
        assertEquals("xhigh", ReasoningMapper.openAIChatEffort("xhigh"));
        // 低档区**原样保留** —— 包括 minimal。只有 Anthropic 把 minimal 上抬到 low，
        // 因为那个协议没有 minimal 这一档；Chat 有这个档，所以不该动。
        assertEquals("minimal", ReasoningMapper.openAIChatEffort("minimal"));
        assertEquals("low", ReasoningMapper.openAIChatEffort("low"));
        assertEquals("medium", ReasoningMapper.openAIChatEffort("medium"));
        assertEquals("high", ReasoningMapper.openAIChatEffort("high"));
        // 认不出的值不猜：返回 null 让调用方完全不发这个字段。
        assertNull(ReasoningMapper.openAIChatEffort("auto"));
        assertNull(ReasoningMapper.openAIChatEffort("none"));
        assertNull(ReasoningMapper.openAIChatEffort("adaptive"));
        assertNull(ReasoningMapper.openAIChatEffort(null));
    }

    @Test
    public void openAIResponsesWireEffort_neverDowngradesMaxOrUltra() {
        // 这是那条「刻意的不对称」：Responses 协议精确单发，只有 xxhigh 是本地别名。
        // 一旦有人「顺手统一成 Chat 的写法」，max/ultra 会被静默降级成 xhigh。
        assertEquals("max", ReasoningMapper.openAIResponsesWireEffort("max"));
        assertEquals("ultra", ReasoningMapper.openAIResponsesWireEffort("ultra"));
        assertEquals("xhigh", ReasoningMapper.openAIResponsesWireEffort("xxhigh"));
        assertEquals("xhigh", ReasoningMapper.openAIResponsesWireEffort("xhigh"));
        assertEquals("low", ReasoningMapper.openAIResponsesWireEffort("low"));
        assertNull(ReasoningMapper.openAIResponsesWireEffort(null));
    }

    @Test
    public void openAIResponsesConfiguredEffort_keepsUnknownValuesVerbatim() {
        // 与 Chat 不同：这里刻意不把认不出的值丢掉，原样透传（网关可能支持自定义档位）。
        assertEquals("some-custom-tier", ReasoningMapper.openAIResponsesConfiguredEffort("some-custom-tier"));
        // "none" 是关闭推理的显式信号，必须原样保留：调用方靠它判断「不要发 reasoning 字段」。
        assertEquals("none", ReasoningMapper.openAIResponsesConfiguredEffort("none"));
        // 数字 0 也要保留（"0" 被引擎当作显式关闭）。
        assertEquals("none", ReasoningMapper.openAIResponsesConfiguredEffort("0"));
        assertEquals("minimal", ReasoningMapper.openAIResponsesConfiguredEffort("1"));
        // auto/adaptive 表示「不指定」，返回 null。
        assertNull(ReasoningMapper.openAIResponsesConfiguredEffort("auto"));
        assertNull(ReasoningMapper.openAIResponsesConfiguredEffort("adaptive"));
        assertNull(ReasoningMapper.openAIResponsesConfiguredEffort(null));
    }

    @Test
    public void anthropicEffort_hasItsOwnBucketNames() {
        // Anthropic 没有 minimal 这一档，所以最小预算与 minimal 都要落到 low。
        assertEquals("low", ReasoningMapper.anthropicEffort("1"));
        assertEquals("low", ReasoningMapper.anthropicEffort("minimal"));
        assertEquals("low", ReasoningMapper.anthropicEffort("low"));
        assertEquals("medium", ReasoningMapper.anthropicEffort("medium"));
        assertEquals("high", ReasoningMapper.anthropicEffort("high"));
        assertEquals("xhigh", ReasoningMapper.anthropicEffort("xhigh"));
        assertEquals("xhigh", ReasoningMapper.anthropicEffort("xxhigh"));
        // Anthropic 的最高档叫 max。
        assertEquals("max", ReasoningMapper.anthropicEffort("max"));
        assertEquals("max", ReasoningMapper.anthropicEffort("ultra"));
        assertNull(ReasoningMapper.anthropicEffort("auto"));
    }

    // ============================================================ ApiEndpointResolver

    @Test
    public void modelCatalogEndpoint_neverDoublesVersionSegment() {
        // 用户填 Base URL 的习惯不统一，"host" 与 "host/v1" 两种都常见。
        // 拼成 /v1/v1/models 会得到 404，而表现是「模型列表拉不到」，很难联想到原因。
        assertEquals("https://example.com/v1/models",
                ApiEndpointResolver.modelCatalogEndpoint(config("openai-chat", "https://example.com")));
        assertEquals("https://example.com/v1/models",
                ApiEndpointResolver.modelCatalogEndpoint(config("openai-chat", "https://example.com/v1")));
        assertEquals("https://example.com/v1/models",
                ApiEndpointResolver.modelCatalogEndpoint(config("openai-chat", "https://example.com/v1/")));
        // 用户写的大写 V1 被**原样保留**：判定时小写化，但拼接时用原串。
        // 这是有意的 —— 我们的职责是拼端点，不是改写用户填的 URL。
        assertEquals("https://example.com/V1/models",
                ApiEndpointResolver.modelCatalogEndpoint(config("openai-chat", "https://example.com/V1/")));
    }

    @Test
    public void modelCatalogEndpoint_allThreeProtocolsAgree() {
        for (String protocol : new String[]{"anthropic", "openai-chat", "openai-responses"}) {
            assertEquals("协议 " + protocol + " 的目录端点",
                    "https://example.com/v1/models",
                    ApiEndpointResolver.modelCatalogEndpoint(config(protocol, "https://example.com")));
        }
    }

    @Test
    public void modelCatalogEndpoint_returnsBlankForUnknownProtocol() {
        // 返回空串而不是猜一个地址：调用方据此跳过这次请求，界面回落到手填模型名。
        assertEquals("", ApiEndpointResolver.modelCatalogEndpoint(config("gemini", "https://example.com")));
    }

    @Test
    public void stripTrailingSlash_removesAllTrailingSlashes() {
        assertEquals("https://a", ApiEndpointResolver.stripTrailingSlash("https://a///"));
        assertEquals("https://a", ApiEndpointResolver.stripTrailingSlash("https://a"));
        assertEquals("", ApiEndpointResolver.stripTrailingSlash(""));
        assertEquals("", ApiEndpointResolver.stripTrailingSlash(null));
    }

    // ============================================================ ApiUrlPolicy

    @Test
    public void requireBaseUrl_rejectsEmptyWithoutInventingADefault() {
        // 这是「不内置任何厂商地址」这条设计的技术保证：没有地址就明确报错，
        // 绝不能补一个默认域名 —— 那会让流量去到一个用户没指定的地方。
        for (String blank : new String[]{"", "   ", null}) {
            try {
                ApiUrlPolicy.requireBaseUrl(config("openai-chat", blank));
                fail("空白 Base URL 必须被拒绝，而不是回落到某个默认地址");
            } catch (IllegalStateException expected) {
                assertTrue("错误文案要能指导用户", expected.getMessage().contains("Base URL"));
            }
        }
    }

    @Test
    public void requireBaseUrl_requiresExplicitScheme() {
        for (String bad : new String[]{"example.com", "ftp://example.com", "//example.com", "example.com/v1"}) {
            try {
                ApiUrlPolicy.requireBaseUrl(config("openai-chat", bad));
                fail("缺少协议头的地址必须被拒绝: " + bad);
            } catch (IllegalStateException expected) {
                assertTrue(expected.getMessage().contains("https://"));
            }
        }
    }

    @Test
    public void requireBaseUrl_acceptsBothSchemesCaseInsensitively() {
        // 某些语言环境下 "HTTPS" 的默认小写不是 "https"，所以判定必须显式用 Locale.US。
        assertEquals("http://example.com", ApiUrlPolicy.requireBaseUrl(config("openai-chat", "http://example.com")));
        assertEquals("HTTPS://example.com", ApiUrlPolicy.requireBaseUrl(config("openai-chat", "HTTPS://example.com")));
    }

    // ============================================================ snapshotDelta

    @Test
    public void snapshotDelta_returnsDifferenceWhenSnapshotExtendsEmitted() {
        // 服务端在 "…done" 事件里会把已发的文本整段重发一遍，直接追加就会重复。
        assertEquals(" world", OpenAIResponsesProvider.snapshotDelta("hello world", "hello"));
        assertEquals("", OpenAIResponsesProvider.snapshotDelta("hello", "hello"));
    }

    @Test
    public void snapshotDelta_returnsBlankWhenSnapshotIsAlreadyContained() {
        // 已发内容以快照结尾 —— 说明这次快照没有新内容，必须返回空串。
        assertEquals("", OpenAIResponsesProvider.snapshotDelta("world", "hello world"));
    }

    @Test
    public void snapshotDelta_findsLargestOverlapSuffix() {
        // 前后缀关系先判，都命中时行为是确定的：
        // 快照以已发内容开头 → 返回真正的剩余部分。
        assertEquals("bar", OpenAIResponsesProvider.snapshotDelta("foobar", "foo"));
        assertEquals("def", OpenAIResponsesProvider.snapshotDelta("abcdef", "abc"));
        // 完全不相关时整段返回（宁可重复也不能丢内容）。
        assertEquals("xyz", OpenAIResponsesProvider.snapshotDelta("xyz", "abc"));
        // 两者都不是前后缀关系、但有重叠时，才走「最大重叠后缀」这条分支：
        // emitted 的尾部 "cd" 就是 snapshot 的头部，所以只补 "ef"。
        assertEquals("ef", OpenAIResponsesProvider.snapshotDelta("cdef", "abcd"));
        // 空快照返回空串，不报错（服务端偶尔发空字段）。
        assertEquals("", OpenAIResponsesProvider.snapshotDelta("", "abc"));
        assertEquals("", OpenAIResponsesProvider.snapshotDelta(null, "abc"));
    }

    // ============================================================ ModelProviders 路由

    @Test
    public void forConfig_routesEveryAlias() {
        assertTrue(ModelProviders.forConfig(config("anthropic", "https://a"))
                instanceof AnthropicMessagesProvider);
        assertTrue(ModelProviders.forConfig(config("openai-chat", "https://a"))
                instanceof OpenAIChatCompletionsProvider);
        assertTrue(ModelProviders.forConfig(config("openai-compatible", "https://a"))
                instanceof OpenAIChatCompletionsProvider);
        assertTrue(ModelProviders.forConfig(config("openai-responses", "https://a"))
                instanceof OpenAIResponsesProvider);
        assertTrue(ModelProviders.forConfig(config("codex-responses", "https://a"))
                instanceof OpenAIResponsesProvider);
    }

    @Test
    public void forConfig_defaultsToAnthropicOnlyWhenProtocolIsAbsent() {
        SessionConfig noProtocol = new SessionConfig();
        noProtocol.protocol = null;
        assertTrue(ModelProviders.forConfig(noProtocol) instanceof AnthropicMessagesProvider);
        assertTrue(ModelProviders.forConfig(null) instanceof AnthropicMessagesProvider);
    }

    @Test
    public void forConfig_rejectsUnknownProtocolInsteadOfGuessing() {
        // 回落默认协议会让用户以为「配好了但模型答得不对劲」，比直接报错难查得多。
        try {
            ModelProviders.forConfig(config("gemini", "https://a"));
            fail("未知协议必须报错");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("gemini"));
        }
    }

    @Test
    public void forConfig_doesNotCacheInstances() {
        // 每次新建实例是有意的：HttpRequestTracker 挂在 provider 实例上，
        // 取消必须拿到同一个对象。如果实现成缓存，子代理与主循环会互相取消对方的请求。
        assertFalse(ModelProviders.forConfig(config("anthropic", "https://a"))
                == ModelProviders.forConfig(config("anthropic", "https://a")));
    }

    // ============================================================ 小工具

    private static SessionConfig config(String protocol, String baseUrl) {
        SessionConfig config = new SessionConfig();
        config.protocol = protocol;
        config.baseUrl = baseUrl;
        config.apiKey = "test-key";
        config.model = "test-model";
        assertNotNull(config);
        return config;
    }
}
