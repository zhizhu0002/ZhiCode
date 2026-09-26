package com.termux.app.zhicode.api;

import com.termux.app.zhicode.model.SessionConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 拉取模型目录（{@code GET /v1/models}），供设置界面列出可选模型。
 *
 * <p>这个类只做一件事：把服务端的模型列表变成一串 {@link ModelDescriptor}。
 * 它不碰设置、不写盘，密钥只出现在请求头上、不会进入返回值。
 *
 * <h3>四条必须守住的约束</h3>
 * <ol>
 *   <li><b>不跟随重定向。</b>明文的重定向会把 {@code Authorization} 头送到另一个主机上。
 *       遇到 3xx 直接报错，而不是跟过去。</li>
 *   <li><b>响应体有上限。</b>这是个展示用的接口，没有理由接受几百 KB 以上的响应；
 *       无上限的读取等于把「服务端返回巨物」变成应用的 OOM。</li>
 *   <li><b>超时短。</b>8 秒建连、12 秒读。模型目录拉不到不影响手填模型名，
 *       不该像对话请求那样等几分钟。</li>
 *   <li><b>每条字段都过一遍消毒。</b>id/name 会直接进界面与设置，
 *       控制字符和超长字符串必须在进 UI 之前就被丢掉。</li>
 * </ol>
 *
 * <p>文件分两半：上半是网络（{@link #fetch}），下半是纯解析
 * （{@link #parse(String)}，不需要网络，可以单独测 —— 这一半才是真正容易出错的地方）。
 */
public final class ModelCatalogClient {

    /** 取消信号。界面销毁时用它中断一次正在进行的目录请求。 */
    public interface CancellationSignal { boolean isCancelled(); }

    private static final int CONNECT_TIMEOUT_MS = 8_000;
    private static final int READ_TIMEOUT_MS = 12_000;

    /** 响应体字符上限。超过即判定为异常响应，而不是继续读。 */
    private static final int MAX_BODY_CHARS = 512 * 1024;

    /** 模型条数上限。下拉列表再长也没有意义，且要防止服务端返回上千项。 */
    private static final int MAX_MODELS = 250;

    /** 单个 id/名称的字符上限。 */
    private static final int MAX_FIELD_CHARS = 160;

    private static final int READ_BUFFER_CHARS = 4096;

    private static final String METHOD_GET = "GET";
    private static final String HEADER_ACCEPT = "Accept";
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String ACCEPT_JSON = "application/json";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String JSON_ARRAY_FIELD = "data";

    private static final String ERROR_NO_KEY = "当前 API 配置没有可用密钥";
    private static final String ERROR_NO_CATALOG = "当前 API 协议未公开标准模型目录，请手动填写模型名";
    private static final String ERROR_REDIRECT = "模型目录请求被重定向，已拒绝";
    private static final String ERROR_BODY = "模型目录返回格式无效";
    private static final String ERROR_TOO_LARGE = "模型目录响应过大";
    private static final String ERROR_CANCELLED = "Cancelled";

    // ==================================================================== 网络

    /**
     * 拉一次目录。
     *
     * @throws IllegalStateException 配置不完整、服务端返回非 2xx、响应格式不对或过大
     * @throws InterruptedException  被取消（线程中断或 {@code cancellation} 报告已取消）
     */
    public List<ModelDescriptor> fetch(SessionConfig config, CancellationSignal cancellation) throws Exception {
        requireKey(config);
        String endpoint = ApiEndpointResolver.modelCatalogEndpoint(config);
        if (endpoint.isEmpty()) throw new IllegalStateException(ERROR_NO_CATALOG);
        checkCancelled(cancellation);

        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        try {
            prepare(connection, config);
            String body = readBounded(connection, cancellation);
            return parse(body);
        } finally {
            connection.disconnect();
        }
    }

    /** 配置里必须有密钥：目录接口需要鉴权，没有密钥的请求只会拿到 401。 */
    private static void requireKey(SessionConfig config) {
        if (config == null || config.apiKey == null || config.apiKey.trim().isEmpty()) {
            throw new IllegalStateException(ERROR_NO_KEY);
        }
    }

    /**
     * 建连参数。重定向**关掉**：跟过去就等于把密钥交给重定向目标。
     */
    private static void prepare(HttpURLConnection connection, SessionConfig config) throws Exception {
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestMethod(METHOD_GET);
        connection.setRequestProperty(HEADER_ACCEPT, ACCEPT_JSON);
        applyAuth(connection, config);
    }

    /**
     * 鉴权头按协议选。
     *
     * <p>Anthropic 用 {@code x-api-key} + 版本头，其余（OpenAI 系）用 Bearer。
     * 这里读 {@link ApiProtocol} 而不是内联协议名：内联的话，
     * 协议改名时这里会被漏掉，表现为「目录接口 401，而对话正常」。
     */
    private static void applyAuth(HttpURLConnection connection, SessionConfig config) {
        if (ApiProtocol.fromWire(config.protocol) != ApiProtocol.ANTHROPIC) {
            connection.setRequestProperty(HEADER_AUTHORIZATION, BEARER_PREFIX + config.apiKey);
            return;
        }
        connection.setRequestProperty("x-api-key", config.apiKey);
        connection.setRequestProperty("anthropic-version", AnthropicMessagesProvider.API_VERSION);
    }

    /**
     * 读响应体，带长度上限与取消检查。
     *
     * <p>先判状态码再读正文：错误响应体可能是另一个凭据供应商的 HTML，
     * 拿它去解析 JSON 只会得到一个与真正原因无关的解析错误。
     */
    private static String readBounded(HttpURLConnection connection, CancellationSignal cancellation) throws Exception {
        int status = connection.getResponseCode();
        if (status >= 300 && status < 400) throw new IllegalStateException(ERROR_REDIRECT);
        if (status < 200 || status >= 300) {
            throw new IllegalStateException("模型目录请求失败（HTTP " + status + "）");
        }
        StringBuilder body = new StringBuilder();
        try (InputStream stream = connection.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            char[] buffer = new char[READ_BUFFER_CHARS];
            int count;
            while ((count = reader.read(buffer)) >= 0) {
                checkCancelled(cancellation);
                if (body.length() + count > MAX_BODY_CHARS) throw new IllegalStateException(ERROR_TOO_LARGE);
                body.append(buffer, 0, count);
            }
        }
        checkCancelled(cancellation);
        return body.toString();
    }

    private static void checkCancelled(CancellationSignal cancellation) throws InterruptedException {
        boolean bySignal = cancellation != null && cancellation.isCancelled();
        if (Thread.currentThread().isInterrupted() || bySignal) {
            throw new InterruptedException(ERROR_CANCELLED);
        }
    }

    // ==================================================================== 解析

    /**
     * 把 {@code {"data":[{"id":…,"display_name":…}, …]}} 变成模型列表。
     *
     * <p>纯函数，不碰网络，所以这里的行为可以逐条钉住：
     * <ul>
     *   <li>顶层没有 {@code data} 数组 → 报错（说明这不是一个目录响应，
     *       静默返回空列表会让界面显示「没有模型」，而真正原因是地址填错了）；</li>
     *   <li>数组里的非对象项跳过，不报错（有些网关会往列表里塞一行说明）；</li>
     *   <li>id 去空白后为空、含控制字符、或超过 {@value #MAX_FIELD_CHARS} 字符 → 整项丢弃
     *       （这种 id 发出去也只会得到 400，留在下拉框里是害人）；</li>
     *   <li>重复 id 只留第一次出现的那条；</li>
     *   <li>显示名取 {@code display_name}，没有则取 {@code name}，都没有则回落 id；</li>
     *   <li>按 id 升序 —— 服务端给的顺序不稳定，界面每次刷新都换顺序会让人以为列表变了。</li>
     * </ul>
     * 达到 {@value #MAX_MODELS} 条后停止扫描：剩下的项连消毒都不做，
     * 免得为一个不会显示的条目付出解析代价。
     */
    static List<ModelDescriptor> parse(String body) throws Exception {
        JSONArray data = new JSONObject(body).optJSONArray(JSON_ARRAY_FIELD);
        if (data == null) throw new IllegalStateException(ERROR_BODY);

        Map<String, ModelDescriptor> byId = new LinkedHashMap<>();
        for (int i = 0; i < data.length() && byId.size() < MAX_MODELS; i++) {
            JSONObject item = data.optJSONObject(i);
            if (item == null) continue;
            String id = sanitizeId(item.optString("id", ""));
            if (id.isEmpty() || byId.containsKey(id)) continue;
            String name = sanitizeName(item.optString("display_name", item.optString("name", id)), id);
            byId.put(id, new ModelDescriptor(id, name));
        }

        List<ModelDescriptor> models = new ArrayList<>(byId.values());
        Collections.sort(models, (left, right) -> left.id.compareTo(right.id));
        return models;
    }

    /** 合法的模型 id；不合法返回空串（调用方据此丢弃整项）。 */
    private static String sanitizeId(String raw) {
        String id = raw == null ? "" : raw.trim();
        if (id.isEmpty() || id.length() > MAX_FIELD_CHARS) return "";
        for (int i = 0; i < id.length(); i++) {
            if (Character.isISOControl(id.charAt(i))) return "";
        }
        return id;
    }

    /**
     * 合法的显示名。
     *
     * <p>与 id 不同，超长在这里**截断**而不是丢弃：显示名只是给人看的，
     * 截断后的前缀仍然有用，而丢掉的后果是用户看到一个空名字。
     * 控制字符不在这里过滤 —— 界面渲染时会按纯文本处理。
     */
    private static String sanitizeName(String raw, String fallbackId) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) return fallbackId;
        return name.length() > MAX_FIELD_CHARS ? name.substring(0, MAX_FIELD_CHARS) : name;
    }
}
