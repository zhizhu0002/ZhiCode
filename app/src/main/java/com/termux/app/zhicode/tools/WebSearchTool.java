package com.termux.app.zhicode.tools;

import android.text.Html;

import com.termux.app.zhicode.model.SessionConfig;
import com.termux.app.zhicode.model.ToolExecutionResult;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 联网搜索。走两个公开后端，不需要任何搜索 API 密钥。
 *
 * <h3>为什么要两个后端</h3>
 * 这一层没有任何凭据、也没有服务等级约定：两个后端都是「能拿到就用」的公开入口，
 * 各自会时不时改版或限流。所以策略是**先试一个、空了再试另一个**，
 * 并且把各自的失败原因收集起来 —— 两个都空时把它们一起报出来，
 * 而不是只说一句「没搜到结果」。后者会让人以为是关键词的问题。
 *
 * <h3>解析为什么用正则而不是 HTML 解析器</h3>
 * 引入一个 HTML 解析库只为了从结果页里抠出三类字段，代价是几百 KB 的 APK 体积与
 * 一个额外的升级面。公开结果页的结构相对稳定，正则够用；
 * 它坏掉的表现是「搜到 0 条」，而上面那套双后端的兜底正是为这种情况准备的。
 *
 * <h3>域名过滤放在最后</h3>
 * 过滤在拿到结果之后做，而不是作为查询参数下推给后端：
 * 后端各自支持不同的过滤语法，而下推会让「同一个 query 在两个后端得到不同结果」。
 * 在这里过滤则两条路径的行为完全一致。
 */
public final class WebSearchTool implements ZhiTool {

    /** 单次响应的读取上限。结果页通常只有几十 KB，两兆已经是异常充裕。 */
    private static final int MAX_RESPONSE_BYTES = 2_000_000;
    private static final int MIN_RESULTS = 1;
    /** 上限原为 10；应用户要求放宽到 50 —— 密钥制服务（Tavily/Exa…）按 count 计费返回，扛得住。 */
    private static final int MAX_RESULTS = 50;
    /** 每个后端最多扫出检索目标的两倍，留出被域名过滤掉之后的余量。 */
    private static final int SCAN_FACTOR = 2;

    private static final String USER_AGENT =
        "Mozilla/5.0 (Android; ZhiCode) AppleWebKit/537.36 Chrome/126 Mobile Safari/537.36";
    private static final String ACCEPT_LANGUAGE = "zh-CN,zh;q=0.9,en;q=0.7";

    private static final String DUCK_HTML_ENDPOINT = "https://html.duckduckgo.com/html/";
    private static final String BING_RSS_ENDPOINT = "https://www.bing.com/search?format=rss&q=";
    /** DuckDuckGo 的结果链接类名。 */
    private static final String RESULT_LINK_CLASS = "result__a";
    /** DuckDuckGo 跳转链接里承载真实地址的参数名。 */
    private static final String DUCK_REDIRECT_PARAM = "uddg";

    private static final Pattern ANCHOR =
        Pattern.compile("<a\\b([^>]*)>(.*?)</a>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern CLASS_ATTR =
        Pattern.compile("\\bclass\\s*=\\s*[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern HREF_ATTR =
        Pattern.compile("\\bhref\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern SNIPPET = Pattern.compile(
        "<(?:a|div|span)[^>]*class=[\"'][^\"']*result__snippet[^\"']*[\"'][^>]*>(.*?)</(?:a|div|span)>",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern RSS_ITEM =
        Pattern.compile("<item>(.*?)</item>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern RSS_TITLE =
        Pattern.compile("<title>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern RSS_LINK =
        Pattern.compile("<link>(.*?)</link>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern RSS_DESC =
        Pattern.compile("<description>(.*?)</description>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** 一条搜索结果。 */
    /** 解析结果条目。字段对同包的 [WebSearchJson] 与 JVM 单测可见。 */
    static final class Hit {
        final String title;
        final String url;
        final String snippet;

        Hit(String title, String url, String snippet) {
            this.title = title;
            this.url = url;
            this.snippet = snippet;
        }
    }

    @Override public String name() { return "WebSearch"; }

    @Override public String description() {
        return "Search the web; returns titles, URLs, and snippets.";
    }

    @Override public JSONObject inputSchema() {
        try {
            JSONObject properties = new JSONObject()
                .put("query", ToolSchemas.string("Search query"))
                .put("max_results", ToolSchemas.integer("Maximum number of results (1-50)", 1))
                .put("allowed_domains", ToolSchemas.stringArray(
                    "Optional domains to restrict results to, such as example.com"))
                .put("blocked_domains", ToolSchemas.stringArray(
                    "Optional domains to exclude from results"));
            return ToolSchemas.object(properties, "query");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override public PermissionKind permissionKind() { return PermissionKind.NETWORK; }

    @Override public ToolExecutionResult execute(SessionConfig config, JSONObject input) throws Exception {
        if (!config.webSearchEnabled) {
            return ToolExecutionResult.error("联网搜索已在设置中关闭。可输入 /web on 开启。 ");
        }
        String query = input.optString("query", "").trim();
        if (query.isEmpty()) return ToolExecutionResult.error("缺少 query 参数");

        int wanted = clamp(input.optInt("max_results", config.webSearchMaxResults));
        String provider = providerName(config);
        List<Hit> raw = new ArrayList<>();
        List<String> failures = new ArrayList<>();

        // 密钥制 / 自建服务优先：选了它们就要说到做到，缺配置时**明确报错**
        // （提示去设置里配），而不是静默回落免费后端 —— 用户配了 Tavily 却
        // 拿到 DuckDuckGo 的结果，比一次失败难查得多。
        if (isKeyedService(provider) || isProvider(provider, "searxng", "custom_http")) {
            try {
                raw = keyedSearch(provider, query, wanted, config);
            } catch (Exception failure) {
                return ToolExecutionResult.error(providerLabel(provider) + "：" + safeMessage(failure));
            }
        }

        if (isProvider(provider, "auto", "duckduckgo")) {
            try {
                raw = duckDuckGo(query, wanted, config.webTimeoutMs);
            } catch (Exception failure) {
                failures.add("DuckDuckGo: " + safeMessage(failure));
            }
        }
        // 只有「还没有结果」才试第二个后端：后端是备选关系，不是合并关系。
        if (raw.isEmpty() && isProvider(provider, "auto", "bing")) {
            try {
                raw = bingRss(query, wanted, config.webTimeoutMs);
            } catch (Exception failure) {
                failures.add("Bing: " + safeMessage(failure));
            }
        }

        List<Hit> hits = filter(raw, input.optJSONArray("allowed_domains"),
            input.optJSONArray("blocked_domains"), wanted);
        if (hits.isEmpty()) {
            return ToolExecutionResult.error(failures.isEmpty()
                ? "没有搜索到结果。"
                : "搜索后端失败：" + join(failures, "；"));
        }
        return ToolExecutionResult.ok(render(query, hits));
    }

    // ------------------------------------------------------------------ 渲染

    private static String render(String query, List<Hit> hits) {
        StringBuilder out = new StringBuilder();
        out.append("联网搜索：").append(query).append('\n');
        out.append("结果数：").append(hits.size()).append("\n\n");
        for (int i = 0; i < hits.size(); i++) {
            Hit hit = hits.get(i);
            out.append(i + 1).append(". ").append(hit.title).append('\n');
            out.append("   URL: ").append(hit.url).append('\n');
            if (!hit.snippet.isEmpty()) out.append("   摘要: ").append(hit.snippet).append('\n');
            out.append('\n');
        }
        // 明确指路下一步：搜索只给摘要，模型常常需要正文才能真正回答问题，
        // 而它默认会想再搜一次。
        out.append("要读网页正文，请对该 URL 调用 WebFetch。");
        return out.toString();
    }

    // ------------------------------------------------- 后端〇：密钥制 / 自建服务

    /**
     * RikkaHub 式的密钥制 / 自建后端（Tavily / Exa / Brave / SearXNG）。
     *
     * <p>与免费后端的关系是**互斥**而不是备选：用户点名了某个服务，
     * 失败就原样报失败（错误里带去哪里配置的指引），绝不静默回落 ——
     * 「配了 Tavily 却拿到 DuckDuckGo 的结果」这种事比一次报错难查得多。
     */
    private static List<Hit> keyedSearch(String provider, String query, int wanted,
                                         SessionConfig config) throws Exception {
        switch (provider) {
            case "tavily":
                return tavily(query, wanted, requireKey(config), config);
            case "exa":
                return exa(query, wanted, requireKey(config), config);
            case "brave":
                return brave(query, wanted, requireKey(config), config);
            case "perplexity":
                return perplexity(query, wanted, requireKey(config), config);
            case "linkup":
                return linkup(query, wanted, requireKey(config), config);
            case "jina":
                return jina(query, wanted, requireKey(config), config);
            case "firecrawl":
                return firecrawl(query, wanted, requireKey(config), config);
            case "bocha":
                return bocha(query, wanted, requireKey(config), config);
            case "metaso":
                return metaso(query, wanted, requireKey(config), config);
            case "zhipu":
                return zhipu(query, wanted, requireKey(config), config);
            case "searxng":
                return searxng(query, wanted, requireSearxngUrl(config), config.webTimeoutMs);
            case "custom_http":
                return customHttp(query, wanted, config);
            default:
                throw new IllegalStateException("未知搜索服务：" + provider);
        }
    }

    /**
     * 这家服务要不要密钥。
     *
     * <p>免费后端（auto/duckduckgo/bing）不需要；其余都要 —— 少一家漏掉的表现是
     * 「配了 Key 却当成免费后端用」，所以做成**白名单式**判断而不是逐个列举。
     */
    private static boolean isKeyedService(String provider) {
        return !isProvider(provider, "auto", "duckduckgo", "bing", "searxng");
    }

    private static String requireKey(SessionConfig config) {
        String key = config.webSearchApiKey == null ? "" : config.webSearchApiKey.trim();
        if (key.isEmpty()) {
            throw new IllegalStateException("未配置 API Key，请到 设置 → 联网搜索 → 搜索服务 里填写");
        }
        return key;
    }

    /** 每服务选项：读 config 里的 JSON，缺字段就给默认。 */
    private static String option(SessionConfig config, String name, String fallback) {
        if (config.webSearchServiceConfig == null || config.webSearchServiceConfig.trim().isEmpty()) {
            return fallback;
        }
        try {
            String value = new JSONObject(config.webSearchServiceConfig).optString(name, "").trim();
            return value.isEmpty() ? fallback : value;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean optionBool(SessionConfig config, String name, boolean fallback) {
        String value = option(config, name, "");
        if (value.isEmpty()) return fallback;
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    private static String requireSearxngUrl(SessionConfig config) {
        String base = config.webSearchBaseUrl == null ? "" : config.webSearchBaseUrl.trim();
        if (base.isEmpty()) {
            throw new IllegalStateException(
                    "未配置 SearXNG 实例地址，请到 设置 → 联网搜索 → 搜索服务 里填写");
        }
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base;
    }

    /** 报错文案里的服务名（错误会直接回到模型面前，用正式名而不是 wire 名）。 */
    private static String providerLabel(String provider) {
        switch (provider) {
            case "tavily": return "Tavily";
            case "exa": return "Exa";
            case "brave": return "Brave";
            case "perplexity": return "Perplexity";
            case "linkup": return "LinkUp";
            case "jina": return "Jina";
            case "firecrawl": return "Firecrawl";
            case "bocha": return "博查";
            case "metaso": return "秘塔";
            case "zhipu": return "智谱";
            case "searxng": return "SearXNG";
            case "custom_http": return "自定义 HTTP";
            default: return provider;
        }
    }

    /**
     * Tavily：{@code POST https://api.tavily.com/search}，Bearer 鉴权。
     * 支持 {@code depth}（basic/advanced）与 {@code topic}（general/news/finance）。
     * 响应解析见 [WebSearchJson.parseTavily]。
     */
    private static List<Hit> tavily(String query, int wanted, String key, SessionConfig config)
            throws Exception {
        HttpURLConnection connection = postJson("https://api.tavily.com/search",
                config.webTimeoutMs, "authorization", "Bearer " + key);
        writeBody(connection, new JSONObject()
                .put("query", query)
                .put("max_results", wanted)
                .put("search_depth", option(config, "depth", "basic"))
                .put("topic", option(config, "topic", "general")));
        return WebSearchJson.parseTavily(readJson(connection));
    }

    /**
     * Exa：{@code POST https://api.exa.ai/search}，{@code x-api-key} 鉴权。
     * 要正文要显式给 {@code contents.text}，不给的话响应里只有标题与 URL。
     */
    private static List<Hit> exa(String query, int wanted, String key, SessionConfig config)
            throws Exception {
        HttpURLConnection connection = postJson("https://api.exa.ai/search",
                config.webTimeoutMs, "x-api-key", key);
        writeBody(connection, new JSONObject()
                .put("query", query)
                .put("numResults", wanted)
                .put("contents", new JSONObject().put(
                        "text", new JSONObject().put("maxCharacters", 1000))));
        return WebSearchJson.parseExa(readJson(connection));
    }

    /**
     * Brave：{@code GET https://api.search.brave.com/res/v1/web/search}，
     * {@code x-subscription-token} 鉴权。结果嵌在 {@code web} 底下。
     */
    private static List<Hit> brave(String query, int wanted, String key, SessionConfig config)
            throws Exception {
        String url = "https://api.search.brave.com/res/v1/web/search?q="
                + URLEncoder.encode(query, "UTF-8") + "&count=" + wanted;
        return WebSearchJson.parseBrave(readJson(getJson(url, config.webTimeoutMs,
                "x-subscription-token", key)));
    }

    /**
     * Perplexity：{@code POST /chat/completions}，回答形如 OpenAI Chat。
     *
     * <p>它返回的是**一段带引用的回答**而不是结果列表，所以「摘要」取回答正文本身，
     * URL 取 {@code citations} 里的条目 —— 那些才是可点开核对的来源。
     */
    private static List<Hit> perplexity(String query, int wanted, String key, SessionConfig config)
            throws Exception {
        HttpURLConnection connection = postJson("https://api.perplexity.ai/chat/completions",
                config.webTimeoutMs, "authorization", "Bearer " + key);
        writeBody(connection, new JSONObject()
                .put("model", option(config, "model", "sonar"))
                .put("messages", new JSONArray().put(new JSONObject()
                        .put("role", "user")
                        .put("content", query))));
        return WebSearchJson.parsePerplexity(readJson(connection), wanted);
    }

    /** LinkUp：{@code POST https://api.linkup.so/v1/search}，可配 {@code depth}。 */
    private static List<Hit> linkup(String query, int wanted, String key, SessionConfig config)
            throws Exception {
        HttpURLConnection connection = postJson("https://api.linkup.so/v1/search",
                config.webTimeoutMs, "authorization", "Bearer " + key);
        writeBody(connection, new JSONObject()
                .put("q", query)
                .put("depth", option(config, "depth", "standard"))
                .put("outputType", "searchResults"));
        return WebSearchJson.parseLinkup(readJson(connection));
    }

    /**
     * Jina Reader 的检索接口：{@code GET https://s.jina.ai/?q=}，返回 Markdown 文本。
     *
     * <p>它是**纯文本**返回（不是 JSON），所以这里不解析结构，而是把它整段当作
     * 一条「摘要」交给模型 —— 与其它服务的形态不同，这是它本身的接口形态决定的。
     */
    private static List<Hit> jina(String query, int wanted, String key, SessionConfig config)
            throws Exception {
        String url = "https://s.jina.ai/?q=" + URLEncoder.encode(query, "UTF-8");
        HttpURLConnection connection = getJson(url, config.webTimeoutMs,
                "authorization", "Bearer " + key);
        return WebSearchJson.parsePlainText(readLimited(connection, MAX_RESPONSE_BYTES));
    }

    /** Firecrawl：{@code POST https://api.firecrawl.dev/v1/search}。 */
    private static List<Hit> firecrawl(String query, int wanted, String key, SessionConfig config)
            throws Exception {
        HttpURLConnection connection = postJson("https://api.firecrawl.dev/v1/search",
                config.webTimeoutMs, "authorization", "Bearer " + key);
        writeBody(connection, new JSONObject()
                .put("query", query)
                .put("limit", wanted));
        return WebSearchJson.parseFirecrawl(readJson(connection));
    }

    /** 博查：{@code POST https://api.bochaai.com/v1/web-search}，可选 AI 摘要。 */
    private static List<Hit> bocha(String query, int wanted, String key, SessionConfig config)
            throws Exception {
        HttpURLConnection connection = postJson("https://api.bochaai.com/v1/web-search",
                config.webTimeoutMs, "authorization", "Bearer " + key);
        writeBody(connection, new JSONObject()
                .put("query", query)
                .put("count", wanted)
                .put("summary", optionBool(config, "summary", false)));
        return WebSearchJson.parseBocha(readJson(connection));
    }

    /** 秘塔：{@code POST https://metaso.cn/api/v1/search}。 */
    private static List<Hit> metaso(String query, int wanted, String key, SessionConfig config)
            throws Exception {
        HttpURLConnection connection = postJson("https://metaso.cn/api/v1/search",
                config.webTimeoutMs, "authorization", "Bearer " + key);
        writeBody(connection, new JSONObject()
                .put("q", query)
                .put("scope", "webpage")
                .put("includeSummary", false)
                .put("size", String.valueOf(wanted)));
        return WebSearchJson.parseMetaso(readJson(connection));
    }

    /** 智谱：{@code POST https://open.bigmodel.cn/api/paas/v4/tools}，{@code web_search} 工具。 */
    private static List<Hit> zhipu(String query, int wanted, String key, SessionConfig config)
            throws Exception {
        HttpURLConnection connection = postJson("https://open.bigmodel.cn/api/paas/v4/tools",
                config.webTimeoutMs, "authorization", "Bearer " + key);
        writeBody(connection, new JSONObject()
                .put("tool", "web-search-pro")
                .put("messages", new JSONArray().put(new JSONObject()
                        .put("role", "user")
                        .put("content", query))));
        return WebSearchJson.parseZhipu(readJson(connection), wanted);
    }

    /**
     * SearXNG：{@code GET {实例}/search?q=&format=json}。实例必须开启 JSON 输出
     * （{@code search.formats} 里要有 json），否则拿到的是 HTML 页、解析为 0 条 ——
     * 报错里要提示这一层，否则用户只会看到「搜不到」。
     */
    private static List<Hit> searxng(String query, int wanted, String base, int timeout)
            throws Exception {
        String url = base + "/search?q=" + URLEncoder.encode(query, "UTF-8") + "&format=json";
        List<Hit> hits = WebSearchJson.parseSearxng(readJson(getJson(url, timeout, null, null)));
        if (hits.isEmpty()) {
            throw new IllegalStateException(
                    "实例返回了 0 条结果 —— 多数是实例没开 JSON 输出（settings.yml 里启用 formats: [json]）");
        }
        return hits;
    }

    /**
     * 自定义 HTTP 检索（对应 RikkaHub 的「Custom JS」，见 SearchServiceType 的说明）。
     *
     * <p>不做代码执行，只做模板替换 + JSON 路径取值：
     * {@code %s} 是查询词、{@code %d} 是条数；结果数组与三个字段名都由用户指定。
     * 这样既覆盖了自建/内部检索 API，又不需要引入 JS 引擎。
     */
    private static List<Hit> customHttp(String query, int wanted, SessionConfig config)
            throws Exception {
        String template = option(config, "urlTemplate", "");
        if (template.isEmpty()) {
            throw new IllegalStateException(
                    "未配置检索地址模板，请到 设置 → 联网搜索 → 搜索服务 里填写");
        }
        String url = String.format(java.util.Locale.US, template,
                URLEncoder.encode(query, "UTF-8"), wanted);
        HttpURLConnection connection = getJson(url, config.webTimeoutMs, null, null);
        String headers = option(config, "headers", "");
        if (!headers.isEmpty()) {
            JSONObject obj = new JSONObject(headers);
            for (java.util.Iterator<String> it = obj.keys(); it.hasNext(); ) {
                String name = it.next();
                connection.setRequestProperty(name, obj.optString(name, ""));
            }
        }
        return WebSearchJson.parseCustom(
                readJsonAllowArray(connection),
                option(config, "itemsPath", ""),
                option(config, "titlePath", "title"),
                option(config, "urlPath", "url"),
                option(config, "textPath", ""));
    }

    /**
     * GET + JSON 头（可选鉴权头）。
     *
     * <p>{@code authHeader} 为 null 时不设鉴权 —— SearXNG 与自定义 HTTP 都可能
     * 不需要它，硬塞一个空 {@code Authorization} 反而会被某些实例拒绝。
     */
    private static HttpURLConnection getJson(String url, int timeout,
                                            String authHeader, String authValue)
            throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(timeout);
        connection.setReadTimeout(timeout);
        connection.setRequestProperty("accept", "application/json");
        if (authHeader != null && authValue != null) {
            connection.setRequestProperty(authHeader, authValue);
        }
        return connection;
    }

    /** POST JSON 请求的公共前缀（超时 / 头）；鉴权头各家不同，由调用方传。 */
    private static HttpURLConnection postJson(String url, int timeout,
                                              String authHeader, String authValue)
            throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(timeout);
        connection.setReadTimeout(timeout);
        connection.setDoOutput(true);
        connection.setUseCaches(false);
        connection.setRequestProperty("content-type", "application/json");
        connection.setRequestProperty("accept", "application/json");
        connection.setRequestProperty(authHeader, authValue);
        return connection;
    }

    private static void writeBody(HttpURLConnection connection, JSONObject body) throws Exception {
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream out = connection.getOutputStream()) {
            out.write(bytes);
        }
    }

    /** 读整个响应体并解析成 JSON；非 2xx 直接带状态码报错（错误体对模型没有价值）。 */
    private static JSONObject readJson(HttpURLConnection connection) throws Exception {
        return new JSONObject(readJsonText(connection));
    }

    /**
     * 读 JSON，允许顶层是**数组**（自定义 HTTP 常见）。
     *
     * <p>包成 {@code {"items": [...]}} 再交给解析器：调用方（[WebSearchJson.parseCustom]）
     * 只需要处理一种顶层形状，少一个分支就少一处能写错的地方。
     */
    private static JSONObject readJsonAllowArray(HttpURLConnection connection) throws Exception {
        String text = readJsonText(connection);
        String trimmed = text.trim();
        if (trimmed.startsWith("[")) {
            return new JSONObject().put("items", new JSONArray(trimmed));
        }
        return new JSONObject(trimmed);
    }

    private static String readJsonText(HttpURLConnection connection) throws Exception {
        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) {
            throw new IllegalStateException("HTTP " + status);
        }
        return readLimited(connection, MAX_RESPONSE_BYTES);
    }

    // ------------------------------------------------------- 后端一：DuckDuckGo

    /**
     * DuckDuckGo 的 HTML 端点。
     *
     * <p>用 POST 而不是 GET：这个端点是表单提交，GET 查询串在长关键词上会被截断。
     *
     * <p>摘要与标题是**分开**解析的（一个来自 {@code result__snippet}、
     * 一个来自 {@code result__a} 锚点），然后按下标配对。这个配对是这里最脆的
     * 一点：两边的元素数量不一定相等。数量不足时给空摘要而不是错位对齐 ——
     * 错位会让模型看到「标题 A 配摘要 B」，那比没有摘要更糟。
     */
    private static List<Hit> duckDuckGo(String query, int wanted, int timeout) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(DUCK_HTML_ENDPOINT).openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(timeout);
        connection.setReadTimeout(timeout);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml");
        connection.setRequestProperty("Accept-Language", ACCEPT_LANGUAGE);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");

        byte[] body = ("q=" + URLEncoder.encode(query, "UTF-8")).getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(body.length);
        try (OutputStream out = connection.getOutputStream()) {
            out.write(body);
        }

        int status = connection.getResponseCode();
        if (status < 200 || status >= 400) throw new Exception("HTTP " + status);
        String html = readLimited(connection, MAX_RESPONSE_BYTES);

        List<String> snippets = new ArrayList<>();
        Matcher snippetMatcher = SNIPPET.matcher(html);
        while (snippetMatcher.find()) snippets.add(cleanHtml(snippetMatcher.group(1)));

        List<Hit> hits = new ArrayList<>();
        int scanLimit = Math.max(wanted * SCAN_FACTOR, wanted);
        int snippetIndex = 0;
        Matcher anchor = ANCHOR.matcher(html);
        while (anchor.find() && hits.size() < scanLimit) {
            String attributes = anchor.group(1);
            Matcher classAttr = CLASS_ATTR.matcher(attributes);
            if (!classAttr.find() || !classAttr.group(1).contains(RESULT_LINK_CLASS)) continue;
            Matcher hrefAttr = HREF_ATTR.matcher(attributes);
            if (!hrefAttr.find()) continue;

            String url = decodeDuckRedirect(decodeHtmlEntities(hrefAttr.group(1)));
            String title = cleanHtml(anchor.group(2));
            if (title.isEmpty() || url.isEmpty() || !isHttpUrl(url)) continue;
            String snippet = snippetIndex < snippets.size() ? snippets.get(snippetIndex++) : "";
            hits.add(new Hit(title, url, snippet));
        }
        return hits;
    }

    /**
     * 还原 DuckDuckGo 的跳转链接。
     *
     * <p>结果页里的 href 指向 {@code /l/?uddg=<编码后的真实地址>}，
     * 直接把跳转地址给用户是没用的（点开还是 DuckDuckGo 的中转页）。
     * 不是跳转链接时原样返回。
     */
    private static String decodeDuckRedirect(String href) {
        String value = href;
        try {
            if (value.startsWith("//")) value = "https:" + value;
            URL url = new URL(value);
            boolean isRedirect = url.getHost().contains("duckduckgo.com")
                && url.getPath().startsWith("/l/");
            if (!isRedirect) return value;
            String queryString = url.getQuery();
            if (queryString == null) return value;
            for (String pair : queryString.split("&")) {
                int equals = pair.indexOf('=');
                if (equals <= 0) continue;
                if (DUCK_REDIRECT_PARAM.equals(pair.substring(0, equals))) {
                    return URLDecoder.decode(pair.substring(equals + 1), "UTF-8");
                }
            }
        } catch (Exception ignored) {
            // 不是合法 URL 时原样返回，交给后面的 isHttpUrl 判掉。
        }
        return value;
    }

    // -------------------------------------------------------------- 后端二：Bing

    /** Bing 的 RSS 端点：结构稳定，但结果条数与摘要质量都不如上面那个。 */
    private static List<Hit> bingRss(String query, int wanted, int timeout) throws Exception {
        URL url = new URL(BING_RSS_ENDPOINT + URLEncoder.encode(query, "UTF-8"));
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(timeout);
        connection.setReadTimeout(timeout);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", "application/rss+xml,application/xml,text/xml,*/*");

        int status = connection.getResponseCode();
        if (status < 200 || status >= 400) throw new Exception("HTTP " + status);
        String xml = readLimited(connection, MAX_RESPONSE_BYTES);

        List<Hit> hits = new ArrayList<>();
        int scanLimit = Math.max(wanted * SCAN_FACTOR, wanted);
        Matcher item = RSS_ITEM.matcher(xml);
        while (item.find() && hits.size() < scanLimit) {
            String block = item.group(1);
            String title = cleanHtml(firstMatch(RSS_TITLE, block));
            // link 在 RSS 里常常是转义过的（&amp; 之类），所以先还原实体再 trim。
            String link = decodeHtmlEntities(firstMatch(RSS_LINK, block)).trim();
            String description = cleanHtml(firstMatch(RSS_DESC, block));
            if (title.isEmpty() || !isHttpUrl(link)) continue;
            hits.add(new Hit(title, link, description));
        }
        return hits;
    }

    // ------------------------------------------------------------------ 过滤

    private static List<Hit> filter(List<Hit> raw, JSONArray allowed, JSONArray blocked, int wanted) {
        List<Hit> kept = new ArrayList<>();
        for (Hit hit : raw) {
            if (!matchesAnyDomain(hit.url, allowed, true)) continue;
            if (matchesAnyDomain(hit.url, blocked, false)) continue;
            kept.add(hit);
            if (kept.size() >= wanted) break;
        }
        return kept;
    }

    /**
     * 域名是否落在名单里。
     *
     * <p>匹配两种形式：完全相等，或者以 {@code .名单项} 结尾。
     * 只做 {@code endsWith(项)} 是不对的 —— 那会让 {@code notexample.com}
     * 命中名单里的 {@code example.com}。子域名（{@code api.example.com}）
     * 则应当被 {@code example.com} 命中，这正是加点号再比较的用意。
     */
    private static boolean matchesAnyDomain(String url, JSONArray domains, boolean emptyMeans) {
        if (domains == null || domains.length() == 0) return emptyMeans;
        String host = hostOf(url);
        for (int i = 0; i < domains.length(); i++) {
            String domain = domains.optString(i, "").trim().toLowerCase(Locale.US);
            if (domain.isEmpty()) continue;
            if (host.equals(domain) || host.endsWith("." + domain)) return true;
        }
        return false;
    }

    private static String hostOf(String url) {
        try {
            return new URL(url).getHost().toLowerCase(Locale.US);
        } catch (Exception invalid) {
            return "";
        }
    }

    // -------------------------------------------------------------- 小工具

    /**
     * 读取响应体，超过上限即停。
     *
     * <p>{@code read(buf)} 的一次读可能跨过上限，所以要按剩余额度截断再写 ——
     * 否则返回值会略微超过上限，而这个「略微」在极端情况下是几 MB。
     */
    private static String readLimited(HttpURLConnection connection, int maxBytes) throws Exception {
        try (BufferedInputStream in = new BufferedInputStream(connection.getInputStream());
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = in.read(buffer)) > 0) {
                int take = Math.min(read, maxBytes - total);
                if (take > 0) out.write(buffer, 0, take);
                total += take;
                if (total >= maxBytes) break;
            }
            return out.toString("UTF-8");
        }
    }

    /**
     * HTML → 纯文本。
     *
     * <p>{@code \u00a0}（不换行空格）要显式换成普通空格：HTML 里它被大量用于排版，
     * 而它在终端里不可见，会让「看起来正常的摘要」里混进奇怪的空白。
     */
    private static String cleanHtml(String html) {
        if (html == null || html.isEmpty()) return "";
        String text = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString();
        return text.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    /**
     * 还原少量常见实体。
     *
     * <p>只处理这三种，是因为它们出现在属性值（href）里；属性值不会经过
     * {@link android.text.Html}，所以必须在这里手工还原。
     * 用 {@code Html.fromHtml} 处理 href 反而会把 {@code &} 之后的 URL 参数吃掉。
     */
    private static String decodeHtmlEntities(String value) {
        return value == null ? "" : value
            .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'");
    }

    private static String firstMatch(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static boolean isHttpUrl(String url) {
        String lower = url.toLowerCase(Locale.US);
        return lower.startsWith("https://") || lower.startsWith("http://");
    }

    private static int clamp(int requested) {
        return Math.max(MIN_RESULTS, Math.min(MAX_RESULTS, requested));
    }

    private static String providerName(SessionConfig config) {
        return config.webSearchProvider == null
            ? "auto"
            : config.webSearchProvider.trim().toLowerCase(Locale.US);
    }

    private static boolean isProvider(String actual, String... accepted) {
        return Arrays.asList(accepted).contains(actual);
    }

    private static String join(List<String> values, String separator) {
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (out.length() > 0) out.append(separator);
            out.append(value);
        }
        return out.toString();
    }

    /** 异常没有文案时给出类名，免得报错变成一行 {@code null}。 */
    private static String safeMessage(Throwable failure) {
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }
}
