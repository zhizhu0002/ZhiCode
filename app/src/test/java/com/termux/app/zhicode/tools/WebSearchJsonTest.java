package com.termux.app.zhicode.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

/**
 * [WebSearchJson] 的真实解析测试：四家服务的字段名是线上契约
 * （{@code content} / {@code text} / {@code description} 各不相同，
 * Brave 还多嵌一层 {@code web}），改一个字母的表现就是「搜到 0 条」，
 * 编译期毫无察觉。这里喂真实形状的样例响应，跑的是 org.json 真实现。
 */
public class WebSearchJsonTest {

    @Test
    public void tavily_parsesTitleUrlContent() throws Exception {
        List<WebSearchTool.Hit> hits = WebSearchJson.parseTavily(new JSONObject(
                "{\"query\":\"k\",\"results\":["
                        + "{\"title\":\"T1\",\"url\":\"https://a.example/1\",\"content\":\"C1\",\"score\":0.9},"
                        + "{\"title\":\"T2\",\"url\":\"https://a.example/2\",\"content\":\"C2\"}]}"));
        assertEquals(2, hits.size());
        assertEquals("T1", hits.get(0).title);
        assertEquals("https://a.example/1", hits.get(0).url);
        assertEquals("C1", hits.get(0).snippet);
    }

    @Test
    public void exa_readsTextInsteadOfContent() throws Exception {
        // Exa 的摘要在 text 字段：拿 Tavily 的 content 字段名来读会得到空摘要。
        List<WebSearchTool.Hit> hits = WebSearchJson.parseExa(new JSONObject(
                "{\"results\":[{\"title\":\"E1\",\"url\":\"https://e.example/1\","
                        + "\"text\":\"正文片段\"}]}"));
        assertEquals(1, hits.size());
        assertEquals("正文片段", hits.get(0).snippet);
    }

    @Test
    public void brave_readsResultsUnderWebObject() throws Exception {
        // Brave 的结果嵌在 web 底下，且摘要字段叫 description。
        List<WebSearchTool.Hit> hits = WebSearchJson.parseBrave(new JSONObject(
                "{\"web\":{\"results\":[{\"title\":\"B1\",\"url\":\"https://b.example/1\","
                        + "\"description\":\"描述\"}]}}"));
        assertEquals(1, hits.size());
        assertEquals("B1", hits.get(0).title);
        assertEquals("描述", hits.get(0).snippet);
    }

    @Test
    public void searxng_parsesFlatResults() throws Exception {
        List<WebSearchTool.Hit> hits = WebSearchJson.parseSearxng(new JSONObject(
                "{\"results\":[{\"title\":\"S1\",\"url\":\"https://s.example/1\","
                        + "\"content\":\"摘要\",\"engine\":\"bing\"}]}"));
        assertEquals(1, hits.size());
        assertEquals("S1", hits.get(0).title);
    }

    @Test
    public void brokenEntriesAreSkippedNotFatal() throws Exception {
        // 单条结果坏了不该废掉整次搜索；没有 URL 的条目对下游毫无用处，直接丢。
        List<WebSearchTool.Hit> hits = WebSearchJson.parseTavily(new JSONObject(
                "{\"results\":["
                        + "{\"title\":\"没有URL\",\"content\":\"x\"},"
                        + "\"不是对象\","
                        + "{\"title\":\"好的\",\"url\":\"https://ok.example/\",\"content\":\"y\"}]}"));
        assertEquals(1, hits.size());
        assertEquals("好的", hits.get(0).title);
        assertTrue(hits.get(0).url.startsWith("https://ok.example"));
    }

    @Test
    public void emptyOrMissingArrayYieldsNoHits() throws Exception {
        assertTrue(WebSearchJson.parseTavily(new JSONObject("{\"results\":[]}")).isEmpty());
        assertTrue(WebSearchJson.parseBrave(new JSONObject("{}")).isEmpty());
        assertTrue(WebSearchJson.parseSearxng(new JSONObject("{\"results\":null}")).isEmpty());
    }

    @Test
    public void missingTitleGetsPlaceholderNotException() throws Exception {
        List<WebSearchTool.Hit> hits = WebSearchJson.parseTavily(new JSONObject(
                "{\"results\":[{\"url\":\"https://x.example/\"}]}"));
        assertEquals(1, hits.size());
        assertEquals("(无标题)", hits.get(0).title);
        assertEquals("", hits.get(0).snippet);
    }

    // -------------------------------------------- RikkaHub 形态新增的服务

    @Test
    public void bocha_readsNestedWebPagesValue() throws Exception {
        // 博查的结果藏在 data.webPages.value[]，且标题字段叫 name —— 与其它家都不同。
        List<WebSearchTool.Hit> hits = WebSearchJson.parseBocha(new JSONObject(
                "{\"data\":{\"webPages\":{\"value\":[{\"name\":\"博查标题\","
                        + "\"url\":\"https://b.example/1\",\"snippet\":\"摘要\"}]}}}"));
        assertEquals(1, hits.size());
        assertEquals("博查标题", hits.get(0).title);
        assertEquals("摘要", hits.get(0).snippet);
    }

    @Test
    public void metaso_readsLinkInsteadOfUrl() throws Exception {
        // 秘塔用 link 而不是 url：拿 url 读会得到 0 条。
        List<WebSearchTool.Hit> hits = WebSearchJson.parseMetaso(new JSONObject(
                "{\"webpages\":[{\"title\":\"秘塔\",\"link\":\"https://m.example/1\","
                        + "\"snippet\":\"摘要\"}]}"));
        assertEquals(1, hits.size());
        assertEquals("https://m.example/1", hits.get(0).url);
    }

    @Test
    public void firecrawl_readsDataArray() throws Exception {
        List<WebSearchTool.Hit> hits = WebSearchJson.parseFirecrawl(new JSONObject(
                "{\"success\":true,\"data\":[{\"title\":\"F\",\"url\":\"https://f.example/\","
                        + "\"description\":\"说明\"}]}"));
        assertEquals(1, hits.size());
        assertEquals("说明", hits.get(0).snippet);
    }

    @Test
    public void zhipu_readsBothKnownShapes() throws Exception {
        // 直接挂 search_result 的形状。
        List<WebSearchTool.Hit> flat = WebSearchJson.parseZhipu(new JSONObject(
                "{\"search_result\":[{\"title\":\"Z\",\"link\":\"https://z.example/\","
                        + "\"content\":\"正文\"}]}"), 5);
        assertEquals(1, flat.size());

        // 嵌在 choices[].message.tool_calls[].function.arguments 里的形状。
        List<WebSearchTool.Hit> nested = WebSearchJson.parseZhipu(new JSONObject(
                "{\"choices\":[{\"message\":{\"tool_calls\":[{\"function\":{\"arguments\":"
                        + "{\"search_result\":[{\"title\":\"Z2\",\"link\":\"https://z2.example/\"}]}}}]}}]}"), 5);
        assertEquals(1, nested.size());
        assertEquals("Z2", nested.get(0).title);
    }

    @Test
    public void perplexity_usesCitationsAsSources() throws Exception {
        // Perplexity 返回一段回答 + citations 列表：回答当摘要、citations 当来源。
        List<WebSearchTool.Hit> hits = WebSearchJson.parsePerplexity(new JSONObject(
                "{\"choices\":[{\"message\":{\"content\":\"这是回答\"}}],"
                        + "\"citations\":[\"https://a.example/1\",\"https://b.example/2\"]}"), 5);
        assertEquals(2, hits.size());
        assertEquals("https://a.example/1", hits.get(0).url);
        assertEquals("这是回答", hits.get(0).snippet);
    }

    @Test
    public void perplexity_withoutCitationsKeepsTheAnswer() throws Exception {
        // 一条 citation 都没有时也不能把内容丢掉。
        List<WebSearchTool.Hit> hits = WebSearchJson.parsePerplexity(new JSONObject(
                "{\"choices\":[{\"message\":{\"content\":\"只有回答\"}}]}"), 5);
        assertEquals(1, hits.size());
        assertEquals("只有回答", hits.get(0).snippet);
    }

    @Test
    public void jina_treatsMarkdownAsOneResult() throws Exception {
        // Jina 返回 Markdown 而不是 JSON：整段作为一条结果的摘要。
        List<WebSearchTool.Hit> hits = WebSearchJson.parsePlainText("# 标题\n\n正文内容");
        assertEquals(1, hits.size());
        assertEquals("# 标题", hits.get(0).title);
        assertTrue(hits.get(0).snippet.contains("正文内容"));
        assertTrue(WebSearchJson.parsePlainText("   ").isEmpty());
    }

    @Test
    public void customHttp_readsByConfiguredPaths() throws Exception {
        // 自定义 HTTP：数组路径与三个字段名都由用户指定。
        JSONObject response = new JSONObject(
                "{\"payload\":{\"hits\":[{\"headline\":\"H\",\"link\":\"https://c.example/\","
                        + "\"body\":\"B\"}]}}");
        List<WebSearchTool.Hit> hits = WebSearchJson.parseCustom(
                response, "payload.hits", "headline", "link", "body");
        assertEquals(1, hits.size());
        assertEquals("H", hits.get(0).title);
        assertEquals("https://c.example/", hits.get(0).url);
        assertEquals("B", hits.get(0).snippet);
    }

    @Test
    public void customHttp_fallsBackToCommonFieldNames() throws Exception {
        // 字段名没指定时按常见名字依次尝试 —— 自建 API 的命名很不统一。
        List<WebSearchTool.Hit> hits = WebSearchJson.parseCustom(
                new JSONObject("{\"items\":[{\"name\":\"N\",\"href\":\"https://d.example/\","
                        + "\"description\":\"D\"}]}"),
                "", "", "", "");
        assertEquals(1, hits.size());
        assertEquals("N", hits.get(0).title);
        assertEquals("D", hits.get(0).snippet);
    }

    @Test
    public void customHttp_arrayInsideArrayPathMissingYieldsNothing() throws Exception {
        // 路径写错时给空结果而不是抛异常 —— 报错由上游统一处理成「没有搜到结果」。
        assertTrue(WebSearchJson.parseCustom(
                new JSONObject("{\"items\":[]}"), "a.b.c", "title", "url", "text").isEmpty());
    }
}
