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
}
