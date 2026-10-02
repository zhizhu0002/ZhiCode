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
