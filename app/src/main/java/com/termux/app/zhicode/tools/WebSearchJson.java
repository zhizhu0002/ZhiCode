package com.termux.app.zhicode.tools;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 密钥制 / 自建搜索服务的 JSON 响应解析（Tavily / Exa / Brave / SearXNG）。
 *
 * <p>不放 [WebSearchTool] 里只有一个理由：那个类为了 DuckDuckGo 的实体解码
 * import 了 {@code android.text.Html}，整个类因此进不了 JVM 单测；而这条
 * 路径是纯 {@code org.json} 解析，各家的字段名又是线上契约（改一个字母
 * 就是「搜到 0 条」），值得用真实实现测。单独成类之后 JVM 单测可以直接
 * 喂样例响应。
 *
 * <p>四家的解析纪律一致：字段缺失给「(无标题)」/ 空串占位而不是抛异常 ——
 * 单条结果坏了不该废掉整次搜索；解析出的条数交给上游的 domain 过滤与
 * clamp，这里不做截断。
 */
final class WebSearchJson {

    private WebSearchJson() {}

    /** Tavily：{@code results[].{title,url,content}}。 */
    static List<WebSearchTool.Hit> parseTavily(JSONObject response) {
        return collect(response, "results", "title", "url", "content");
    }

    /** Exa：{@code results[].{title,url,text}}。 */
    static List<WebSearchTool.Hit> parseExa(JSONObject response) {
        return collect(response, "results", "title", "url", "text");
    }

    /** Brave：结果嵌在 {@code web} 底下：{@code web.results[].{title,url,description}}。 */
    static List<WebSearchTool.Hit> parseBrave(JSONObject response) {
        JSONObject web = response.optJSONObject("web");
        return collect(web == null ? new JSONObject() : web,
                "results", "title", "url", "description");
    }

    /** SearXNG：{@code results[].{title,url,content}}。 */
    static List<WebSearchTool.Hit> parseSearxng(JSONObject response) {
        return collect(response, "results", "title", "url", "content");
    }

    /**
     * Firecrawl：{@code data[].{title, url, description}}（结果数组叫 {@code data}）。
     */
    static List<WebSearchTool.Hit> parseFirecrawl(JSONObject response) {
        return collect(response, "data", "title", "url", "description");
    }

    /** 博查：结果嵌在 {@code data.webPages.value[]}，摘要在 {@code snippet}。 */
    static List<WebSearchTool.Hit> parseBocha(JSONObject response) {
        JSONObject data = response.optJSONObject("data");
        JSONObject pages = data == null ? null : data.optJSONObject("webPages");
        return collect(pages == null ? new JSONObject() : pages,
                "value", "name", "url", "snippet");
    }

    /** 秘塔：{@code webpages[].{title, link, snippet}}（链接字段叫 {@code link}）。 */
    static List<WebSearchTool.Hit> parseMetaso(JSONObject response) {
        return collect(response, "webpages", "title", "link", "snippet");
    }

    /**
     * 智谱：结果在 {@code search_result[]}，但它往往是**一层一层的 message**：
     * {@code {search_result: [...]}} 与 {@code {choices:[{message:{tool_calls:[{search_result:[…]}]}}]}}
     * 两种形状都出现过。两种都试，取到为止。
     */
    static List<WebSearchTool.Hit> parseZhipu(JSONObject response, int wanted) {
        List<WebSearchTool.Hit> direct = collect(response, "search_result", "title", "link", "content");
        if (!direct.isEmpty()) return direct;

        JSONArray choices = response.optJSONArray("choices");
        for (int i = 0; choices != null && i < choices.length(); i++) {
            JSONObject message = choices.optJSONObject(i) == null
                    ? null : choices.optJSONObject(i).optJSONObject("message");
            JSONArray calls = message == null ? null : message.optJSONArray("tool_calls");
            for (int c = 0; calls != null && c < calls.length(); c++) {
                JSONObject fn = calls.optJSONObject(c) == null
                        ? null : calls.optJSONObject(c).optJSONObject("function");
                JSONObject args = fn == null ? null : fn.optJSONObject("arguments");
                JSONObject source = args == null ? calls.optJSONObject(c) : args;
                List<WebSearchTool.Hit> hits =
                        collect(source, "search_result", "title", "link", "content");
                if (!hits.isEmpty()) return hits;
            }
        }
        return new ArrayList<>();
    }

    /**
     * Perplexity：返回的是一段**回答**加一份 {@code citations} URL 列表。
     *
     * <p>它没有「标题 + 摘要」这种结构（回答是整体的一段），所以这里把回答正文
     * 当作每一条的摘要、把 citations 当作可核对的来源。少了这一步，
     * 用户拿到的引用链接就无处安放，模型也没法说清答案出自哪里。
     */
    static List<WebSearchTool.Hit> parsePerplexity(JSONObject response, int wanted) {
        String answer = "";
        JSONArray choices = response.optJSONArray("choices");
        if (choices != null && choices.length() > 0) {
            JSONObject message = choices.optJSONObject(0) == null
                    ? null : choices.optJSONObject(0).optJSONObject("message");
            if (message != null) answer = message.optString("content", "").trim();
        }
        List<WebSearchTool.Hit> hits = new ArrayList<>();
        JSONArray citations = response.optJSONArray("citations");
        for (int i = 0; citations != null && i < citations.length() && hits.size() < wanted; i++) {
            String url = citations.optString(i, "").trim();
            if (url.isEmpty()) continue;
            // 第一条挂上回答正文（那是这段回答的主要出处），其余只给 URL。
            hits.add(new WebSearchTool.Hit(
                    hostLabel(url),
                    url,
                    hits.isEmpty() ? answer : ""));
        }
        if (hits.isEmpty() && !answer.isEmpty()) {
            // 一条 citation 都没有时也不能丢内容：至少让模型看到这段回答。
            hits.add(new WebSearchTool.Hit("Perplexity 回答", "", answer));
        }
        return hits;
    }

    /** LinkUp：{@code results[].{name, url, content}}。 */
    static List<WebSearchTool.Hit> parseLinkup(JSONObject response) {
        return collect(response, "results", "name", "url", "content");
    }

    /**
     * Jina Reader 返回的是 **Markdown 纯文本**，没有 JSON 结构。
     *
     * <p>所以把它整段作为一条结果的摘要（它的形态本来就是"一篇文章"而不是
     * 结果列表），标题取首行。不做正则抠链接：那会把正文里的引用链接误当成结果。
     */
    static List<WebSearchTool.Hit> parsePlainText(String text) {
        List<WebSearchTool.Hit> hits = new ArrayList<>();
        String body = text == null ? "" : text.trim();
        if (body.isEmpty()) return hits;
        int newline = body.indexOf('\n');
        String title = (newline > 0 ? body.substring(0, newline) : body).trim();
        if (title.length() > 120) title = title.substring(0, 120);
        // URL 留空会让上游 collect/filter 丢掉这一条，所以这里给一个占位说明；
        // 真正的取用处是「读正文」，而正文已经在 snippet 里了。
        hits.add(new WebSearchTool.Hit(title, "https://s.jina.ai/", body));
        return hits;
    }

    /**
     * 自定义 HTTP 检索：按用户给的点号路径取数组，再按字段名取三个值。
     *
     * <p>[itemsPath] 为空视为根数组（工具侧已经把顶层数组包进 {@code items}，
     * 所以这里把空路径当作 {@code items}）。
     */
    static List<WebSearchTool.Hit> parseCustom(JSONObject response, String itemsPath,
                                               String titlePath, String urlPath, String textPath) {
        JSONArray items = arrayAt(response, itemsPath);
        List<WebSearchTool.Hit> hits = new ArrayList<>();
        for (int i = 0; items != null && i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            String url = firstNonBlank(item, urlPath, "url", "link", "href");
            if (url.isEmpty()) continue;
            String title = firstNonBlank(item, titlePath, "title", "name");
            // 摘要字段没指定时按常见名字依次尝试 —— 自建 API 的字段名很不统一。
            String text = firstNonBlank(item, textPath, "text", "content", "snippet", "description");
            hits.add(new WebSearchTool.Hit(title.isEmpty() ? "(无标题)" : title, url, text));
        }
        return hits;
    }

    /** 按点号路径取一个 JSON 数组；路径为空时回落到 {@code items}。 */
    private static JSONArray arrayAt(JSONObject root, String path) {
        String key = path == null || path.trim().isEmpty() ? "items" : path.trim();
        JSONObject node = root;
        String[] segments = key.split("\\.");
        for (int i = 0; i < segments.length - 1; i++) {
            node = node.optJSONObject(segments[i]);
            if (node == null) return null;
        }
        return node.optJSONArray(segments[segments.length - 1]);
    }

    /** 按给定字段名取值，为空就试后面的候选名字。 */
    private static String firstNonBlank(JSONObject item, String primary, String... fallbacks) {
        if (primary != null && !primary.trim().isEmpty()) {
            String value = item.optString(primary.trim(), "").trim();
            if (!value.isEmpty()) return value;
        }
        for (String name : fallbacks) {
            String value = item.optString(name, "").trim();
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    /** 从 URL 取一个可读的标题（Perplexity 只给 URL，列表里总得有个名字）。 */
    private static String hostLabel(String url) {
        try {
            String host = new java.net.URL(url).getHost();
            return host == null || host.isEmpty() ? url : host;
        } catch (Exception ignored) {
            return url;
        }
    }

    private static List<WebSearchTool.Hit> collect(JSONObject container, String arrayKey,
                                                   String titleKey, String urlKey, String textKey) {
        List<WebSearchTool.Hit> hits = new ArrayList<>();
        JSONArray results = container == null ? null : container.optJSONArray(arrayKey);
        for (int i = 0; results != null && i < results.length(); i++) {
            JSONObject item = results.optJSONObject(i);
            if (item == null) continue;
            String url = item.optString(urlKey, "").trim();
            // 没有 URL 的条目对下游（WebFetch 指引）毫无用处，直接丢。
            if (url.isEmpty()) continue;
            hits.add(new WebSearchTool.Hit(
                    item.optString(titleKey, "").trim().isEmpty()
                            ? "(无标题)" : item.optString(titleKey, "").trim(),
                    url, item.optString(textKey, "").trim()));
        }
        return hits;
    }
}
