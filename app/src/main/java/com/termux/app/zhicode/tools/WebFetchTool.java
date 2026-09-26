package com.termux.app.zhicode.tools;

import android.text.Html;

import com.termux.app.zhicode.model.SessionConfig;
import com.termux.app.zhicode.model.ToolExecutionResult;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把一个公开网页取回来，抽成可读文本。
 *
 * <h3>三条安全约束，顺序都不无谓</h3>
 * <ol>
 *   <li><b>只允许 HTTPS。</b>{@code http} 会把内容与请求头暴露在明文里，
 *       而这里的 URL 由模型给出 —— 一条明文链路意味着中间人可以让模型
 *       读到任意内容，而它随后会据此回答用户。</li>
 *   <li><b>拒绝本机与私网地址。</b>模型可以编出 {@code https://192.168.1.1/}
 *       或者 {@code https://localhost:8080/}，那等于给了一个「探测用户局域网」
 *       的工具。判定在**解析出 IP 之后**做 —— 只查域名字符串的话，
 *       一个指向 {@code 127.0.0.1} 的域名就能绕过去。</li>
 *   <li><b>自己跟随重定向，最多 {@value #MAX_REDIRECTS} 次。</b>不用
 *       {@code HttpURLConnection} 的自动跟随，是因为**每一跳都要重新校验**：
 *       自动跟随不检查目的地，一次 302 就能把请求引到内网地址上。</li>
 * </ol>
 *
 * <h3>为什么要按 content-type 选字符集</h3>
 * 中文网页大量使用 GBK。一律按 UTF-8 解码会让整页变成乱码，
 * 而模型看到乱码时的表现是「这个页面没内容」，不会联想到编码问题。
 */
public final class WebFetchTool implements ZhiTool {

    private static final int MIN_CHARS = 1000;
    private static final int MAX_CHARS = 100000;
    private static final int MAX_REDIRECTS = 6;
    /** 原始响应的绝对上限，与请求的 max_chars 无关：先拦住离谱的响应体。 */
    private static final int MAX_BODY_BYTES = 2_000_000;
    private static final int MIN_BODY_BYTES = 200_000;
    /** 每个字符最多可能占几个字节（UTF-8 下中文是 3，留一倍余量）。 */
    private static final int BYTES_PER_CHAR = 6;
    private static final int READ_BUFFER_BYTES = 8192;

    private static final String TRUNCATED_MARKER = "\n…[内容已截断]";
    private static final String USER_AGENT =
        "Mozilla/5.0 (Android; ZhiCode) AppleWebKit/537.36 Chrome/126 Mobile Safari/537.36";
    private static final String ACCEPT =
        "text/html,text/plain,application/json,application/xml,text/xml;q=0.9,*/*;q=0.5";
    private static final String ACCEPT_LANGUAGE = "zh-CN,zh;q=0.9,en;q=0.7";

    private static final Pattern TITLE =
        Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    /** 脚本、样式、SVG 的内容不是正文，但会污染抽取结果。 */
    private static final Pattern NON_CONTENT =
        Pattern.compile("<(script|style|noscript|svg)[^>]*>.*?</\\1>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    /** 块级标签换成换行，否则整页会挤成一行。 */
    private static final Pattern BLOCK_BOUNDARY = Pattern.compile(
        "</?(?:p|div|section|article|main|header|footer|nav|li|ul|ol|h[1-6]|pre|blockquote|br|tr|table)[^>]*>",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern CHARSET_PARAM =
        Pattern.compile("charset=([^; ]+)", Pattern.CASE_INSENSITIVE);

    @Override public String name() { return "WebFetch"; }

    @Override public String description() { return "Fetch readable text from a public HTTPS URL."; }

    @Override public JSONObject inputSchema() {
        try {
            return ToolSchemas.object(new JSONObject()
                .put("url", ToolSchemas.string("Public HTTPS URL to fetch"))
                .put("max_chars", ToolSchemas.integer(
                    "Maximum extracted characters (1000-100000)", 1000)), "url");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override public PermissionKind permissionKind() { return PermissionKind.NETWORK; }

    @Override public ToolExecutionResult execute(SessionConfig config, JSONObject input) throws Exception {
        if (!config.webSearchEnabled) {
            return ToolExecutionResult.error("联网功能已在设置中关闭。可输入 /web on 开启。");
        }
        String requested = input.optString("url", "").trim();
        if (requested.isEmpty()) return ToolExecutionResult.error("url 不能为空");

        int maxChars = Math.max(MIN_CHARS,
            Math.min(MAX_CHARS, input.optInt("max_chars", config.webFetchMaxChars)));
        URL url = validate(requested);

        for (int hop = 0; hop < MAX_REDIRECTS; hop++) {
            HttpURLConnection connection = open(url, config);
            int status = connection.getResponseCode();

            if (isRedirect(status)) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null || location.trim().isEmpty()) {
                    return ToolExecutionResult.error("网页重定向缺少 Location");
                }
                // 每一跳都重新校验，见类注释第 3 点。
                url = validate(new URL(url, location).toString());
                continue;
            }
            if (status < 200 || status >= 400) {
                return ToolExecutionResult.error("网页请求失败：HTTP " + status + " · " + url);
            }
            String contentType = contentTypeOf(connection);
            if (!isReadable(contentType)) {
                return ToolExecutionResult.error("不支持的网页内容类型：" + contentType);
            }
            return ToolExecutionResult.ok(render(url, readBody(connection, contentType, maxChars), maxChars));
        }
        return ToolExecutionResult.error("网页重定向次数超出上限");
    }

    // ------------------------------------------------------------------ 网络

    private static HttpURLConnection open(URL url, SessionConfig config) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(config.webTimeoutMs);
        connection.setReadTimeout(config.webTimeoutMs);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", ACCEPT);
        connection.setRequestProperty("Accept-Language", ACCEPT_LANGUAGE);
        return connection;
    }

    private static boolean isRedirect(int status) {
        return status >= 300 && status < 400;
    }

    private static String contentTypeOf(HttpURLConnection connection) {
        String contentType = connection.getContentType();
        return contentType == null ? "" : contentType.toLowerCase(Locale.US);
    }

    /** 文本类与结构化文本才值得抽取；二进制下载（图片、压缩包）直接拒绝。 */
    private static boolean isReadable(String contentType) {
        return contentType.isEmpty()
            || contentType.contains("text/")
            || contentType.contains("json")
            || contentType.contains("xml");
    }

    /**
     * 读响应体并按内容类型决定怎么抽正文。
     *
     * <p>上限同时受 max_chars 约束（每字符最多 {@value #BYTES_PER_CHAR} 字节），
     * 因为要的只是前 max_chars 个字符 —— 把一个 50 MB 的 JSON 全读进来
     * 再截断，白花内存。
     */
    private static String readBody(HttpURLConnection connection, String contentType, int maxChars)
            throws Exception {
        int wanted = Math.min(MAX_BODY_BYTES, Math.max(MIN_BODY_BYTES, maxChars * BYTES_PER_CHAR));
        byte[] bytes = readLimited(connection, wanted);
        String body = new String(bytes, charsetOf(contentType));
        if (contentType.contains("html") || body.toLowerCase(Locale.US).contains("<html")) {
            return extractText(body);
        }
        return body;
    }

    /** HTML → 纯文本。先去掉不相关内容、再把块级边界换成换行，最后交给系统解析。 */
    private static String extractText(String body) {
        String withoutNonContent = NON_CONTENT.matcher(body).replaceAll(" ");
        String withBreaks = BLOCK_BOUNDARY.matcher(withoutNonContent).replaceAll("\n");
        return Html.fromHtml(withBreaks, Html.FROM_HTML_MODE_LEGACY).toString();
    }

    private static byte[] readLimited(HttpURLConnection connection, int maxBytes) throws Exception {
        try (BufferedInputStream in = new BufferedInputStream(connection.getInputStream());
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[READ_BUFFER_BYTES];
            int total = 0;
            int read;
            while ((read = in.read(buffer)) > 0) {
                int take = Math.min(read, maxBytes - total);
                if (take > 0) out.write(buffer, 0, take);
                total += take;
                if (total >= maxBytes) break;
            }
            return out.toByteArray();
        }
    }

    /** 从 content-type 里取字符集；取不到或认不出时回落 UTF-8。 */
    private static Charset charsetOf(String contentType) {
        try {
            Matcher matcher = CHARSET_PARAM.matcher(contentType);
            if (matcher.find()) {
                return Charset.forName(matcher.group(1).replace("\"", ""));
            }
        } catch (Exception unsupported) {
            // 认不出的字符集名回落 UTF-8 —— 乱码总好过抛异常。
        }
        return StandardCharsets.UTF_8;
    }

    // ------------------------------------------------------------ 地址校验

    /**
     * 校验并解析地址。见类注释的三条约束。
     *
     * @throws IllegalArgumentException 地址不合规。它会作为工具错误呈现给模型，
     *         所以每组文案都要说清**是什么**不合规
     */
    private static URL validate(String raw) throws Exception {
        URI uri = new URI(raw);
        String scheme = uri.getScheme();
        if (scheme == null || !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("WebFetch 仅允许 HTTPS URL");
        }
        String host = uri.getHost();
        if (host == null || host.trim().isEmpty()) {
            throw new IllegalArgumentException("URL 缺少有效主机名");
        }
        String lower = host.toLowerCase(Locale.US);
        if ("localhost".equals(lower) || lower.endsWith(".localhost")) {
            throw new IllegalArgumentException("不允许访问 localhost");
        }
        // 域名先解析成 IP 再判断：只查字符串的话，一个指向 127.0.0.1 的域名能绕过去。
        for (InetAddress address : InetAddress.getAllByName(host)) {
            if (isPrivate(address)) {
                throw new IllegalArgumentException("不允许访问私有/本地网络地址");
            }
        }
        return uri.toURL();
    }

    /**
     * 是否是本机/私网/保留地址。
     *
     * <p>IPv4 部分手查网段而不是只靠 {@code isSiteLocalAddress}：
     * 运营商级 NAT（{@code 100.64/10}）与链路本地（{@code 169.254/16}）
     * 都不被标准方法覆盖，而它们同样是「不该由模型去访问」的地址。
     */
    private static boolean isPrivate(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
            || address.isLinkLocalAddress() || address.isSiteLocalAddress()
            || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address && bytes.length == 4) {
            int first = bytes[0] & 255;
            int second = bytes[1] & 255;
            if (first == 100 && second >= 64 && second <= 127) return true;
            if (first == 169 && second == 254) return true;
            if (first == 0 || first >= 224) return true;
        }
        if (address instanceof Inet6Address && bytes.length == 16) {
            int first = bytes[0] & 255;
            if ((first & 0xfe) == 0xfc) return true;
            if (first == 0xfe && (bytes[1] & 0xc0) == 0x80) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- 渲染

    private static String render(URL url, String rawBody, int maxChars) {
        String title = "";
        Matcher matcher = TITLE.matcher(rawBody);
        if (matcher.find()) title = flatten(matcher.group(1));

        String text = normalize(rawBody);
        if (text.length() > maxChars) text = text.substring(0, maxChars) + TRUNCATED_MARKER;

        StringBuilder out = new StringBuilder();
        out.append("URL: ").append(url).append('\n');
        if (!title.isEmpty()) out.append("标题: ").append(title).append('\n');
        out.append('\n').append(text);
        return out.toString();
    }

    /** HTML 片段 → 一行纯文本（标题用）。 */
    private static String flatten(String html) {
        return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString()
            .replaceAll("\\s+", " ").trim();
    }

    /**
     * 压掉多余空白。
     *
     * <p>四步的顺序有讲究：先把不换行空格换成普通空格（否则后面按空格压的时候
     * 它会被当成有内容的字符留下来），再去掉 {@code \r}（不同换行风格混用时
     * {@code \r} 会留在行尾），然后压行内空格与行首缩进，最后把三个以上连续换行
     * 压成两个 —— 正文正常只有段落分隔，多余的空行都来自被删掉的标签。
     */
    private static String normalize(String text) {
        String value = text.replace('\u00a0', ' ').replace("\r", "");
        value = value.replaceAll("[ \\t]+", " ");
        value = value.replaceAll("\\n[ \\t]+", "\n");
        value = value.replaceAll("\\n{3,}", "\n\n");
        return value.trim();
    }
}
