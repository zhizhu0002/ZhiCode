package com.termux.app.zhicode.mcp;

import com.termux.app.zhicode.json.JsonItems;
import com.termux.app.zhicode.model.ToolExecutionResult;
import com.termux.app.zhicode.storage.McpConfigStore;
import com.zhizhu.zhicode.compose.BuildConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MCP 的两套传输：stdio 子进程，以及 Streamable HTTP。
 *
 * <h3>每次调用都是一次完整的会话</h3>
 * stdio 与 HTTP 都是「连上 → initialize → 通知已初始化 → 发请求 → 断开」。
 * 不做连接复用是**有意的**：MCP 服务器是有状态的进程，长期挂着一个空闲连接
 * 意味着多一个要管的生命周期（谁负责在组件重建后清理它？），
 * 而这里的调用是低频的（一次工具调用一次）。断开时用
 * {@code destroy → destroyForcibly} 两步，因为有些实现不响应优雅终止。
 *
 * <h3>为什么 HTTP 侧要把 {@code _session_id} 藏起来又删掉</h3>
 * Streamable HTTP 的会话 id 在**响应头**里，而我们要把它带到后续请求上。
 * 内部用一个私有键在两次请求之间传递它，交回给调用方之前删掉 ——
 * 否则它会混进工具结果，看起来像服务器返回的一个字段。
 *
 * <h3>stdio 的分帧</h3>
 * 按 {@code Content-Length} 分帧（LSP 风格），不是按行。
 * 按行读会在正文里出现换行的那一刻错位，而错位后的表现是「JSON 解析失败」，
 * 看不出是分帧错了。
 */
public final class McpRuntime {

    private static final long TIMEOUT_MS = 30_000L;
    private static final String PROTOCOL_VERSION = "2024-11-05";
    /** 单条消息上限。超过它基本可以断定对端不是 MCP 服务器。 */
    private static final int MAX_MESSAGE_BYTES = 16 * 1024 * 1024;
    private static final int READ_BUFFER_BYTES = 8192;
    /** 轮询间隔：{@link BufferedInputStream#available()} 不阻塞，所以要自己让出 CPU。 */
    private static final long POLL_INTERVAL_MS = 5L;

    private static final String KEY_SESSION_ID = "_session_id";
    private static final String HEADER_SESSION_ID = "Mcp-Session-Id";
    private static final String METHOD_INITIALIZE = "initialize";
    private static final String METHOD_INITIALIZED = "notifications/initialized";
    private static final String METHOD_LIST_TOOLS = "tools/list";
    private static final String METHOD_CALL_TOOL = "tools/call";

    /** 自增的 JSON-RPC 请求 id。 */
    private final AtomicLong nextId = new AtomicLong(1L);
    private final McpConfigStore store = new McpConfigStore();

    // ------------------------------------------------------------------ 对外

    /**
     * 列出已启用的服务器，并各自查一次工具清单。
     *
     * <p>单个服务器出错**不**影响其它服务器：那一行会带上 {@code ok:false} 与错误原因。
     * 让整次列举失败会让一个配错的服务器挡住所有可用的服务器。
     */
    public JSONArray listServers() {
        JSONArray rows = new JSONArray();
        for (McpConfigStore.Server server : store.load()) {
            if (!server.enabled) continue;
            JSONObject row = new JSONObject();
            try {
                row.put("server", server.name).put("type", server.type).put("scope", server.scope);
                JSONArray tools = toolsOf(server);
                row.put("ok", true).put("tools", tools);
            } catch (Exception failure) {
                try {
                    row.put("ok", false).put("error", messageOf(failure));
                } catch (Exception ignored) {
                    // JSONObject.put 在这里不会失败；真失败也只能给出这一行。
                }
            }
            rows.put(row);
        }
        return rows;
    }

    /** 调用某个服务器上的一个工具。 */
    public ToolExecutionResult call(String serverName, String toolName, JSONObject arguments) {
        McpConfigStore.Server server = store.find(serverName);
        if (server == null || !server.enabled) {
            return ToolExecutionResult.error("MCP server unavailable: " + serverName);
        }
        if (toolName == null || toolName.trim().isEmpty()) {
            return ToolExecutionResult.error("MCP tool name is empty");
        }
        try {
            JSONObject params = new JSONObject()
                .put("name", toolName)
                .put("arguments", arguments == null ? new JSONObject() : arguments);
            JSONObject result = session(server, METHOD_CALL_TOOL, params);
            JSONArray content = result == null ? null : result.optJSONArray("content");
            // MCP 把「工具执行失败」放在 isError 里而不是 JSON-RPC 错误里：
            // 前者是可预期的业务失败，后者是协议层失败。两者都要映射成工具失败，
            // 但只有后者会被上面的 catch 接住。
            if (result != null && result.optBoolean("isError", false)) {
                return ToolExecutionResult.error(flattenContent(content));
            }
            return ToolExecutionResult.ok(flattenContent(content));
        } catch (Exception failure) {
            return ToolExecutionResult.error("MCP call failed: " + messageOf(failure));
        }
    }

    private JSONArray toolsOf(McpConfigStore.Server server) throws Exception {
        JSONObject result = session(server, METHOD_LIST_TOOLS, new JSONObject());
        JSONArray tools = result == null ? null : result.optJSONArray("tools");
        return tools == null ? new JSONArray() : tools;
    }

    // ------------------------------------------------------------------ 传输

    private JSONObject session(McpConfigStore.Server server, String method, JSONObject params)
            throws Exception {
        String type = server.type;
        if ("stdio".equalsIgnoreCase(type)) return overStdio(server, method, params);
        if ("http".equalsIgnoreCase(type) || "sse".equalsIgnoreCase(type)) {
            return overHttp(server, method, params);
        }
        throw new IOException("unsupported MCP transport: " + type);
    }

    /**
     * 起子进程走 stdio。
     *
     * <p>环境变量在子进程自己的环境上改（{@code builder.environment()}），
     * 不动本进程的 —— 后者会让 MCP 服务器的影响扩散到整个应用。
     */
    private JSONObject overStdio(McpConfigStore.Server server, String method, JSONObject params)
            throws Exception {
        if (server.command == null || server.command.trim().isEmpty()) {
            throw new IOException("MCP stdio command is empty");
        }
        ProcessBuilder builder = new ProcessBuilder(commandLine(server));
        Map<String, String> environment = builder.environment();
        applyPairs(environment, server.env);
        builder.redirectError(ProcessBuilder.Redirect.PIPE);

        Process process = builder.start();
        try {
            BufferedInputStream in = new BufferedInputStream(process.getInputStream());
            BufferedOutputStream out = new BufferedOutputStream(process.getOutputStream());
            write(out, METHOD_INITIALIZE, initializeParams(), true);
            read(in);
            // 通知没有 id，也不会有响应 —— 这是协议规定的，不是我们偷懒。
            write(out, METHOD_INITIALIZED, new JSONObject(), false);
            write(out, method, params, true);
            return read(in);
        } finally {
            process.destroy();
            // 有些实现在收到 SIGTERM 之后仍然挂着。
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    /**
     * 走 Streamable HTTP。
     *
     * <p>三步：initialize 拿到会话 id（可能在响应头里）→ 发已初始化通知 →
     * 发真正的请求。会话 id 用私有键在请求之间传递，见类注释。
     */
    private JSONObject overHttp(McpConfigStore.Server server, String method, JSONObject params)
            throws Exception {
        if (server.url == null || server.url.trim().isEmpty()) {
            throw new IOException("MCP HTTP URL is empty");
        }
        JSONObject init = post(server, requestEnvelope(METHOD_INITIALIZE, initializeParams()), null);
        String sessionId = init == null ? null : init.optString(KEY_SESSION_ID, null);
        post(server, requestEnvelope(METHOD_INITIALIZED, new JSONObject()), sessionId);
        JSONObject result = post(server, requestEnvelope(method, params), sessionId);
        if (result != null) result.remove(KEY_SESSION_ID);
        return result;
    }

    private JSONObject post(McpConfigStore.Server server, JSONObject body, String sessionId)
            throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(server.url).openConnection();
        connection.setConnectTimeout((int) TIMEOUT_MS);
        connection.setReadTimeout((int) TIMEOUT_MS);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json, text/event-stream");
        if (sessionId != null && !sessionId.isEmpty()) {
            connection.setRequestProperty(HEADER_SESSION_ID, sessionId);
        }
        for (String name : namesOf(server.headers)) {
            connection.setRequestProperty(name, server.headers.optString(name, ""));
        }

        byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
        try (BufferedOutputStream out = new BufferedOutputStream(connection.getOutputStream())) {
            out.write(payload);
            out.flush();
        }

        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) {
            throw new IOException("MCP HTTP " + status + ": " + readErrorBody(connection));
        }
        // 通知不应答，所以这里必须提前返回一个「没有响应」的表示，
        // 否则会阻塞在读一个永远不来的响应体上。
        if (METHOD_INITIALIZED.equals(body.optString("method"))) return null;

        JSONObject result = parseEnvelope(readAll(connection.getInputStream()));
        String returnedSession = connection.getHeaderField(HEADER_SESSION_ID);
        if (returnedSession != null && result != null) result.put(KEY_SESSION_ID, returnedSession);
        return result;
    }

    // ------------------------------------------------------------ 协议报文

    /**
     * initialize 的参数。
     *
     * <p>客户端版本取自构建配置，而不是写字面量：写死一个版本号会在下次改版本时
     * 变成一句假话，而 MCP 服务器常常把它显示在日志与「已连接客户端」列表里。
     */
    private static JSONObject initializeParams() throws Exception {
        return new JSONObject()
            .put("protocolVersion", PROTOCOL_VERSION)
            .put("capabilities", new JSONObject())
            .put("clientInfo", new JSONObject()
                .put("name", "ZhiCode")
                .put("version", BuildConfig.VERSION_NAME));
    }

    private JSONObject requestEnvelope(String method, JSONObject params) throws Exception {
        return new JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", nextId.getAndIncrement())
            .put("method", method)
            .put("params", params);
    }

    /** stdio 侧写一条消息。通知（{@code withId=false}）不带 id，见协议。 */
    private void write(BufferedOutputStream out, String method, JSONObject params, boolean withId)
            throws IOException {
        try {
            JSONObject envelope = new JSONObject()
                .put("jsonrpc", "2.0").put("method", method).put("params", params);
            if (withId) envelope.put("id", nextId.getAndIncrement());
            byte[] payload = envelope.toString().getBytes(StandardCharsets.UTF_8);
            out.write(("Content-Length: " + payload.length + "\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII));
            out.write(payload);
            out.flush();
        } catch (Exception failure) {
            throw new IOException(failure);
        }
    }

    /**
     * 读一条 {@code Content-Length} 分帧的消息。
     *
     * <p>返回值统一成「result 对象」：协议信封是
     * {@code {"id":…,"result":{…}}}，而调用方要的是里面那个 {@code result}。
     * 遇到 {@code error} 键则抛错 —— 协议层错误不能当成空结果吞掉，
     * 那会让「服务器拒绝了这次调用」变成「服务器返回了空」。
     */
    private JSONObject read(BufferedInputStream in) throws Exception {
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        int length = -1;
        String line;
        do {
            line = readLine(in, deadline);
            if (line == null) throw new IOException("MCP closed stdout");
            if (line.toLowerCase().startsWith("content-length:")) {
                length = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
            }
        } while (!line.isEmpty());

        if (length < 0 || length > MAX_MESSAGE_BYTES) {
            throw new IOException("invalid MCP content length");
        }
        JSONObject envelope = new JSONObject(
            new String(readFully(in, length, deadline), StandardCharsets.UTF_8));
        return unwrap(envelope);
    }

    /**
     * 拆信封：有 {@code error} 就抛，有 {@code result} 就取它，否则返回信封本身。
     *
     * <p>「否则返回信封本身」是为了兼容不回 result 的实现 —— 对它们来说报文里
     * 的内容就是结果。这不是猜测：MCP 规范里有若干通知类响应就是如此。
     */
    private static JSONObject unwrap(JSONObject envelope) throws IOException {
        if (envelope.has("error")) {
            JSONObject error = envelope.optJSONObject("error");
            throw new IOException(error == null ? "MCP error" : error.optString("message", "MCP error"));
        }
        JSONObject result = envelope.optJSONObject("result");
        return result == null ? envelope : result;
    }

    /**
     * 解析 HTTP 响应：可能是裸 JSON，也可能是 SSE。
     *
     * <p>SSE 分支把多个 {@code data:} 行拼起来（协议允许分帧），
     * 并丢掉结束标记 —— 留着它会让后面的 JSON 解析失败。
     */
    private static JSONObject parseEnvelope(String raw) throws Exception {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) return null;
        if (text.startsWith("data:")) text = joinEventData(text);
        return unwrap(new JSONObject(text));
    }

    private static String joinEventData(String payload) {
        StringBuilder joined = new StringBuilder();
        for (String line : payload.split("\\r?\\n")) {
            if (!line.startsWith("data:")) continue;
            String value = line.substring(5).trim();
            if ("[DONE]".equals(value)) continue;
            if (joined.length() > 0) joined.append('\n');
            joined.append(value);
        }
        return joined.toString();
    }

    // ---------------------------------------------------------------- 小工具

    private static List<String> commandLine(McpConfigStore.Server server) {
        List<String> command = new ArrayList<>();
        command.add(server.command);
        command.addAll(server.args);
        return command;
    }

    /** 把 JSON 对象的键值对写进环境或请求头；空键跳过。 */
    private static void applyPairs(Map<String, String> target, JSONObject pairs) {
        for (String key : namesOf(pairs)) target.put(key, pairs.optString(key, ""));
    }

    private static List<String> namesOf(JSONObject object) {
        List<String> names = new ArrayList<>();
        if (object == null) return names;
        JSONArray keys = object.names();
        if (keys == null) return names;
        for (int i = 0; i < keys.length(); i++) {
            String key = keys.optString(i, "");
            if (!key.isEmpty()) names.add(key);
        }
        return names;
    }

    /** 把 MCP 的内容块数组压成一段文本，块之间用换行分隔。 */
    private static String flattenContent(JSONArray content) {
        if (content == null) return "";
        StringBuilder out = new StringBuilder();
        for (JSONObject item : JsonItems.of(content)) {
            String text = item.optString("text", "");
            if (text.isEmpty()) continue;
            if (out.length() > 0) out.append('\n');
            out.append(text);
        }
        return out.toString();
    }

    private static String readAll(InputStream in) throws IOException {
        if (in == null) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[READ_BUFFER_BYTES];
        int read;
        while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        return out.toString(StandardCharsets.UTF_8.name());
    }

    private static String readErrorBody(HttpURLConnection connection) {
        try {
            return readAll(connection.getErrorStream());
        } catch (Exception unreadable) {
            return "";
        }
    }

    /**
     * 读一行（以 {@code \n} 结尾）。
     *
     * <p>{@link BufferedInputStream#available()} 返回 0 不代表流结束，
     * 所以要轮询等待而不是直接返回 —— 直接返回会把「还没到」当成「没有数据」，
     * 于是分帧头读一半就失败。轮询期间响应中断。
     */
    private static String readLine(BufferedInputStream in, long deadline) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        while (System.currentTimeMillis() < deadline) {
            if (in.available() == 0) {
                try {
                    Thread.sleep(POLL_INTERVAL_MS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted", interrupted);
                }
                continue;
            }
            int next = in.read();
            if (next < 0) return null;
            if (next == '\n') {
                return buffer.toString(StandardCharsets.US_ASCII.name()).replace("\r", "");
            }
            buffer.write(next);
        }
        throw new IOException("MCP response timeout");
    }

    /** 读指定字节数。读不满就是超时 —— 报文不完整时继续解析只会得到错位的 JSON。 */
    private static byte[] readFully(BufferedInputStream in, int length, long deadline) throws IOException {
        byte[] payload = new byte[length];
        int offset = 0;
        while (offset < length && System.currentTimeMillis() < deadline) {
            int read = in.read(payload, offset, length - offset);
            if (read < 0) break;
            offset += read;
        }
        if (offset != length) throw new IOException("MCP response timeout");
        return payload;
    }

    private static String messageOf(Exception failure) {
        return failure.getMessage() == null ? failure.toString() : failure.getMessage();
    }
}
