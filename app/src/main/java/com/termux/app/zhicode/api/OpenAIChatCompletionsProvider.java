package com.termux.app.zhicode.api;

import com.termux.app.zhicode.api.compat.ReasoningMapper;
import com.termux.app.zhicode.json.JsonItems;
import com.termux.app.zhicode.model.AssistantTurn;
import com.termux.app.zhicode.model.SessionConfig;
import com.termux.app.zhicode.model.ToolCall;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * OpenAI Chat Completions 协议的流式传输（也覆盖各家的兼容网关）。
 *
 * <h3>它的历史形状与其它两家不同</h3>
 * 引擎内部按 Anthropic 的形状维护对话历史（内容块数组）。这个协议用的是另一套：
 * {@code {role, content}} 里 {@code content} 多半是纯字符串，
 * 工具调用是 {@code tool_calls[]}，而工具结果必须是一条<b>独立的</b>
 * {@code {role:"tool", tool_call_id}} 消息。所以这里有一层真正的翻译，
 * 见 {@link HistoryMapper}。翻译错了不会报错，只会让模型看不到上下文 ——
 * 表现为「它好像忘了我刚说的」，很难联想到是消息映射的问题。
 *
 * <h3>流式与非流式共用同一套字段读取</h3>
 * 两种情况下的字段名是一样的（{@code content}、{@code tool_calls[].function.name}…），
 * 差别只有三处，都用 {@code incremental} 这个开关显式表达：
 * <ol>
 *   <li><b>增量拼接</b>：流式的 arguments 是分片，必须追加；非流式是完整值，直接取。</li>
 *   <li><b>usage 缺字段时的处理</b>：流式保留旧值（后面的事件更完整）；
 *       非流式的 usage 是终值，缺字段就是 0。</li>
 *   <li><b>finish_reason</b>：流式可能只在最后一个分片里给，之前不能动；
 *       非流式一定有，缺失按 {@code stop} 处理。</li>
 * </ol>
 * 上游把这两条路径各写了一遍（{@code processChunk} 与 {@code parseNonStreaming}），
 * 于是「改了一处忘了另一处」是默认结果。合并后这三处差异成了显式参数。
 *
 * <h3>工具调用的 id 与 name 只在第一个分片里</h3>
 * 后续分片只带 {@code index} 与 arguments 片段。所以累加器必须按 index 复用，
 * 且只在非空时才覆盖 id/name —— 否则第二个分片会把已经拿到的好名字抹成空。
 */
public final class OpenAIChatCompletionsProvider implements ModelProvider {

    private static final String USER_AGENT = "ZhiCodeAndroid-JavaNative/0.18";
    private static final String DATA_FIELD = "data:";
    private static final String DONE_MARKER = "[DONE]";
    private static final int MAX_ERROR_CHARS = 12_000;

    /** 工具名缺失时的占位。与 Responses 不同，这个协议下会**保留**这条调用。 */
    private static final String UNKNOWN_TOOL = "unknown_tool";

    private final HttpRequestTracker requests = new HttpRequestTracker();

    @Override
    public void cancelRequest(Thread worker) {
        requests.cancel(worker);
    }

    // ------------------------------------------------------------------ 请求

    @Override
    public AssistantTurn createMessage(SessionConfig config, String systemPrompt, JSONArray messages,
                                       JSONArray tools, StreamListener listener) throws Exception {
        // 注意：这个协议**不**强制要求密钥非空。很多自建网关不需要鉴权，
        // 而 Anthropic / Responses 两家会在这里直接报错。
        String baseUrl = ApiUrlPolicy.requireBaseUrl(config);
        HttpURLConnection conn = openConnection(baseUrl, config);
        HttpRequestTracker.Scope request = requests.begin(conn);

        StreamDecoder decoder = new StreamDecoder(listener);
        try {
            conn.setRequestProperty("x-request-id", request.requestId());
            writeRequestBody(conn, buildRequestBody(config, systemPrompt, messages, tools));

            int status = conn.getResponseCode();
            if (request.exceedsTotalTimeout()) {
                throw new java.net.SocketTimeoutException("request total timeout");
            }
            request.markResponseStarted();
            if (status < 200 || status >= 300) {
                throw new IllegalStateException(
                        "OpenAI 兼容 API HTTP " + status + ": "
                                + truncate(ProviderTransport.readError(conn, MAX_ERROR_CHARS)));
            }

            if (isStreaming(conn.getContentType())) {
                readSse(conn.getInputStream(), decoder, request);
                if (!decoder.terminalEventSeen) {
                    // 这个措辞是契约：引擎按 "stream_read_error" 子串判定可重试。
                    throw new IllegalStateException("stream_read_error: model stream ended before [DONE]");
                }
            } else {
                decoder.applyFullResponse(new JSONObject(ProviderTransport.readBody(conn, MAX_ERROR_CHARS)));
            }
        } catch (IOException failure) {
            if (Thread.currentThread().isInterrupted()) throw failure;
            throw new StreamFailure(request.failureCode(failure), request.failureMessage(failure), failure);
        } finally {
            request.close();
        }

        return decoder.toTurn();
    }

    /**
     * 这份响应是不是 SSE。
     *
     * <p>判断偏「是」：{@code accept} 头同时声明了两种类型，所以服务端有权回任一者。
     * 缺 content-type、不是 json、或明说是 event-stream，都按流式处理 ——
     * 把一段 SSE 当 JSON 解析会立刻炸，而把 JSON 当 SSE 解析只会读到空内容，
     * 后者更难查。
     */
    private static boolean isStreaming(String contentType) {
        if (contentType == null) return true;
        String lower = contentType.toLowerCase(Locale.ROOT);
        return !lower.contains("json") || lower.contains("event-stream");
    }

    private static HttpURLConnection openConnection(String baseUrl, SessionConfig config) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(chatEndpoint(baseUrl)).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setUseCaches(false);
        conn.setRequestProperty("content-type", "application/json");
        conn.setRequestProperty("accept", "text/event-stream, application/json");
        if (config.apiKey != null && !config.apiKey.trim().isEmpty()) {
            conn.setRequestProperty("authorization", "Bearer " + config.apiKey.trim());
        }
        conn.setRequestProperty("user-agent", USER_AGENT);
        return conn;
    }

    /**
     * 在用户给的 Base URL 上拼出 Chat 端点。
     *
     * <p>{@code /v1} 与 {@code /chat/completions} 都做去重：用户填的地址习惯不统一，
     * 而拼重的结果是 404，表现却是「模型不回复」，很难联想到是地址多了两段。
     *
     * <p>去重规则收编在 {@link ApiEndpointResolver#sessionEndpoint} ——
     * responses 与 messages 起初漏掉了同样的处理，实测就是 404；
     * 三个会话端点必须共用同一条规则。
     */
    private static String chatEndpoint(String baseUrl) {
        return ApiEndpointResolver.sessionEndpoint(baseUrl, "/chat/completions", true);
    }

    private static void writeRequestBody(HttpURLConnection conn, JSONObject body) throws IOException {
        ProviderTransport.writeJson(conn, body.toString());
    }

    private static JSONObject buildRequestBody(SessionConfig config, String systemPrompt,
                                               JSONArray messages, JSONArray tools) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", config.model);
        body.put("messages", HistoryMapper.mapMessages(systemPrompt, messages));
        body.put("stream", true);

        JSONArray mappedTools = HistoryMapper.mapTools(tools);
        if (mappedTools.length() > 0) {
            body.put("tools", mappedTools);
            // 只在真的带了工具时才发这两个字段：没有工具却声明 tool_choice，
            // 有些网关会直接报「tool_choice 需要 tools」。
            body.put("tool_choice", "auto");
            body.put("parallel_tool_calls", true);
        }

        // 这个协议顶格是 xhigh，所以 max/ultra 在映射层就折叠过来了。
        String effort = ReasoningMapper.openAIChatEffort(config.effort);
        if (effort != null) body.put("reasoning_effort", effort);

        // maxTokens 为 0 表示「不指定」，此时不发这个字段让服务端用自己的默认值；
        // 发一个 0 会被判成「只要 0 个 token」。
        if (config.maxTokens > 0) body.put("max_tokens", config.maxTokens);
        return body;
    }

    // ------------------------------------------------------------ SSE 分帧

    private static void readSse(InputStream in, StreamDecoder decoder, HttpRequestTracker.Scope request) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("ZhiCode request interrupted");
                }
                if (request.exceedsTotalTimeout()) {
                    throw new java.net.SocketTimeoutException("request total timeout");
                }
                // 这个协议只有一个 data: 字段，没有 event: —— 事件类型在 payload 里。
                if (!line.startsWith(DATA_FIELD)) continue;
                String payload = line.substring(DATA_FIELD.length()).trim();
                if (payload.isEmpty()) continue;
                if (DONE_MARKER.equals(payload)) {
                    decoder.terminalEventSeen = true;
                    return;
                }
                decoder.applyChunk(new JSONObject(payload));
            }
        }
    }

    // ------------------------------------------------------------ 解码器

    /**
     * 把 Chat 的响应还原成一条回复。
     *
     * <p>无 I/O：状态是「累积的正文/思考 + 按 index 索引的工具调用」。
     * 提供两个入口 —— {@link #applyChunk} 给流式分片，
     * {@link #applyFullResponse} 给非流式整包 —— 二者共用同一个字段读取实现。
     */
    static final class StreamDecoder {

        private final StreamListener listener;
        private final AssistantTurn turn = new AssistantTurn();
        private final StringBuilder text = new StringBuilder();
        private final StringBuilder thinking = new StringBuilder();
        /** 按 index 索引：工具调用的参数是分片到达的，必须找到同一个累加器。 */
        private final Map<Integer, ToolCallAccumulator> tools = new LinkedHashMap<>();

        boolean terminalEventSeen;

        StreamDecoder(StreamListener listener) {
            this.listener = listener;
        }

        /** 处理一个流式分片。 */
        void applyChunk(JSONObject chunk) throws Exception {
            applyUsage(chunk.optJSONObject("usage"), true);

            // 有些网关把错误当成一个普通分片发过来，而不是用 HTTP 状态码。
            JSONObject error = chunk.optJSONObject("error");
            if (error != null) throw new IllegalStateException(error.optString("message", chunk.toString()));

            JSONObject choice = firstChoice(chunk);
            if (choice == null) return;
            String finish = choice.optString("finish_reason", "");
            // 空值与字面量 "null" 都表示「还没结束」：部分网关会把 null 序列化成字符串。
            if (!finish.isEmpty() && !"null".equals(finish)) turn.stopReason = mapFinishReason(finish);
            applyContent(choice.optJSONObject("delta"), true);
        }

        /** 处理一个非流式整包响应。 */
        void applyFullResponse(JSONObject response) throws Exception {
            applyUsage(response.optJSONObject("usage"), false);

            JSONObject choice = firstChoice(response);
            if (choice == null) return;
            // 非流式一定有 finish_reason，缺失时按正常结束处理（而不是保持未知）。
            turn.stopReason = mapFinishReason(choice.optString("finish_reason", "stop"));
            applyContent(choice.optJSONObject("message"), false);
        }

        /**
         * @param incremental 分片模式：arguments 是增量、usage 缺字段保留旧值；
         *                    否则是完整值、缺字段即 0
         */
        private void applyUsage(JSONObject usage, boolean incremental) {
            if (usage == null) return;
            turn.inputTokens = usage.optLong("prompt_tokens", incremental ? turn.inputTokens : 0L);
            turn.outputTokens = usage.optLong("completion_tokens", incremental ? turn.outputTokens : 0L);
            if (listener != null) listener.onUsage(turn.inputTokens, turn.outputTokens);
        }

        private void applyContent(JSONObject source, boolean incremental) throws Exception {
            if (source == null) return;

            String chunk = contentText(source.opt("content"));
            if (!chunk.isEmpty()) {
                text.append(chunk);
                if (listener != null) listener.onTextDelta(chunk);
            }

            // 三个字段名是同一件事的不同叫法：不同网关各自实现时选了不同的名字，
            // 所以按优先级第一个非空的即答案，而不是把它们拼起来。
            String reasoning = firstNonEmpty(
                    stringValue(source.opt("reasoning_content")),
                    stringValue(source.opt("reasoning")),
                    stringValue(source.opt("thinking")));
            if (!reasoning.isEmpty()) {
                thinking.append(reasoning);
                if (listener != null) listener.onThinkingDelta(reasoning);
            }

            JSONArray calls = source.optJSONArray("tool_calls");
            if (calls == null) return;
            for (int i = 0; i < calls.length(); i++) {
                JSONObject call = calls.optJSONObject(i);
                if (call == null) continue;
                // 分片里带 index；非流式的数组元素没有 index，用数组下标兜底。
                int index = call.has("index") ? call.optInt("index", i) : i;
                ToolCallAccumulator accumulator = tools.get(index);
                if (accumulator == null) {
                    accumulator = new ToolCallAccumulator();
                    tools.put(index, accumulator);
                }
                accumulator.apply(call, incremental, listener);
            }
        }

        private AssistantTurn toTurn() throws JSONException {
            // 思考在前、正文在后：与协议里模型「先推理再回答」的顺序一致。
            if (thinking.length() > 0) {
                turn.content.put(new JSONObject().put("type", "thinking").put("thinking", thinking.toString()));
            }
            if (text.length() > 0) {
                turn.content.put(new JSONObject().put("type", "text").put("text", text.toString()));
            }
            for (ToolCallAccumulator accumulator : tools.values()) {
                ToolCallAccumulator.Resolved resolved = accumulator.resolve();
                turn.content.put(new JSONObject()
                        .put("type", "tool_use")
                        .put("id", resolved.id)
                        .put("name", resolved.name)
                        .put("input", resolved.input));
                turn.toolCalls.add(new ToolCall(resolved.id, resolved.name, resolved.input));
            }
            if (!turn.toolCalls.isEmpty() && (turn.stopReason == null || "end_turn".equals(turn.stopReason))) {
                // 有工具调用却报 end_turn 会让引擎直接结束回合、工具永远不执行，
                // 所以这里必须纠正过来。两种路径都要纠正，故放在收尾统一处理。
                turn.stopReason = "tool_use";
            }
            if (turn.stopReason == null || turn.stopReason.isEmpty()) {
                turn.stopReason = turn.toolCalls.isEmpty() ? "end_turn" : "tool_use";
            }
            return turn;
        }

        private static JSONObject firstChoice(JSONObject envelope) {
            JSONArray choices = envelope.optJSONArray("choices");
            if (choices == null || choices.length() == 0) return null;
            return choices.optJSONObject(0);
        }
    }

    /** 一个工具调用的累积器：id/name 取到一次就不再改，参数按需追加。 */
    static final class ToolCallAccumulator {

        private String id;
        private String name;
        private final StringBuilder arguments = new StringBuilder();

        void apply(JSONObject call, boolean incremental, StreamListener listener) {
            // 只在非空时覆盖：分片里只有第一个带 id/name，后面的空值不能把已有值抹掉。
            String incomingId = call.optString("id", "");
            if (!incomingId.isEmpty()) id = incomingId;

            JSONObject function = call.optJSONObject("function");
            if (function == null) return;
            String incomingName = function.optString("name", "");
            if (!incomingName.isEmpty()) name = incomingName;

            String fragment = function.optString("arguments", "");
            // 完整值模式下即使是空串也要写入（它会变成 {}），分片模式下空片段没有意义。
            if (fragment.isEmpty() && incremental) return;
            arguments.append(fragment);
            if (incremental && listener != null) listener.onToolInputDelta(id, name, fragment);
        }

        /** 一条工具调用的最终形态；缺失的 id/name 在这里兜底。 */
        Resolved resolve() {
            return new Resolved(
                    id == null || id.trim().isEmpty() ? "call_" + UUID.randomUUID() : id,
                    name == null || name.trim().isEmpty() ? UNKNOWN_TOOL : name,
                    parseToolArguments(arguments.toString()));
        }

        static final class Resolved {
            final String id;
            final String name;
            final JSONObject input;

            Resolved(String id, String name, JSONObject input) {
                this.id = id;
                this.name = name;
                this.input = input;
            }
        }
    }

    // ------------------------------------------------------------ 历史翻译

    /**
     * 把引擎的 Anthropic 形状历史翻译成 Chat Completions 的 messages。
     *
     * <p>四件事必须做对，做错了都不报错，只是模型看不到上下文：
     * <ol>
     *   <li><b>system 是 messages 的第一条</b>，不是顶层字段；</li>
     *   <li><b>assistant 的多个 text 块要合成一个字符串</b>，而 tool_use 变成
     *       {@code tool_calls[]}（arguments 是 JSON <b>字符串</b>，不是对象）；</li>
     *   <li><b>tool_result 必须单独成一条 {@code role:"tool"} 消息</b>，
     *       不能混在 user 里 —— 混了会被判成「工具调用没有对应结果」而拒绝请求；</li>
     *   <li><b>thinking 块要丢掉</b>：它是传输层的产物，塞进正文会让模型
     *       把自己上一轮的内部推理当成说过的话。</li>
     * </ol>
     *
     * <p>另外 {@code flushUserParts} 有个刻意的优化：只有一个 text 片段时降级成
     * 纯字符串 {@code content}。这是为了兼容那些不认内容数组的老网关 ——
     * 数组形式在新网关上正确，但在老网关上会被当成「不支持多模态」而拒绝。
     */
    static final class HistoryMapper {

        private HistoryMapper() {}

        static JSONArray mapMessages(String systemPrompt, JSONArray messages) throws Exception {
            JSONArray out = new JSONArray();
            if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
                out.put(new JSONObject().put("role", "system").put("content", systemPrompt));
            }
            if (messages == null) return out;

            for (JSONObject message : JsonItems.of(messages)) {
                JSONArray blocks = message.optJSONArray("content");
                if (blocks == null) continue;
                if ("assistant".equals(message.optString("role", "user"))) {
                    appendAssistant(out, blocks);
                } else {
                    appendUserAndToolResults(out, blocks);
                }
            }
            return out;
        }

        static JSONArray mapTools(JSONArray tools) throws Exception {
            JSONArray out = new JSONArray();
            if (tools == null) return out;
            for (JSONObject tool : JsonItems.of(tools)) {
                String name = tool.optString("name", "").trim();
                // 无名工具直接跳过：发出去会被服务端拒绝整份请求，
                // 而拒绝信息不会指出是哪个工具的问题。
                if (name.isEmpty()) continue;
                JSONObject schema = tool.optJSONObject("input_schema");
                JSONObject function = new JSONObject()
                        .put("name", name)
                        .put("description", tool.optString("description", ""))
                        .put("parameters", schema == null ? new JSONObject().put("type", "object") : schema);
                out.put(new JSONObject().put("type", "function").put("function", function));
            }
            return out;
        }

        private static void appendAssistant(JSONArray out, JSONArray blocks) throws Exception {
            StringBuilder text = new StringBuilder();
            JSONArray toolCalls = new JSONArray();
            for (JSONObject block : JsonItems.of(blocks)) {
                String type = block.optString("type", "");
                if ("text".equals(type)) {
                    String value = block.optString("text", "");
                    if (!value.isEmpty()) text.append(value);
                } else if ("tool_use".equals(type)) {
                    JSONObject input = block.optJSONObject("input");
                    JSONObject function = new JSONObject()
                            .put("name", block.optString("name", UNKNOWN_TOOL))
                            // arguments 必须是字符串形式的 JSON。传对象在某些网关能过，
                            // 在另一些网关会被判成协议错误 —— 字符串是两边都接受的形状。
                            .put("arguments", input == null ? "{}" : input.toString());
                    String id = block.optString("id", "");
                    toolCalls.put(new JSONObject()
                            .put("id", id.trim().isEmpty() ? "call_" + UUID.randomUUID() : id)
                            .put("type", "function")
                            .put("function", function));
                }
                // thinking 块在这里被有意丢弃，见类注释第 4 条。
            }
            if (text.length() == 0 && toolCalls.length() == 0) return;
            JSONObject message = new JSONObject().put("role", "assistant");
            // 只有工具调用、没有正文时 content 必须是显式 null 而不是缺字段：
            // 有些网关要求带 tool_calls 的消息必须存在 content 键。
            message.put("content", text.length() > 0 ? text.toString() : JSONObject.NULL);
            if (toolCalls.length() > 0) message.put("tool_calls", toolCalls);
            out.put(message);
        }

        private static void appendUserAndToolResults(JSONArray out, JSONArray blocks) throws Exception {
            JSONArray pendingUserParts = new JSONArray();
            for (JSONObject block : JsonItems.of(blocks)) {
                String type = block.optString("type", "");
                if ("tool_result".equals(type)) {
                    // 工具结果之前累积的 user 片段必须先落盘，否则顺序会被打乱：
                    // 它们本该在工具结果之前发给模型。
                    flushUserParts(out, pendingUserParts);
                    pendingUserParts = new JSONArray();
                    out.put(new JSONObject()
                            .put("role", "tool")
                            .put("tool_call_id", block.optString("tool_use_id", ""))
                            .put("content", toolResultText(block.opt("content"))));
                } else if ("text".equals(type)) {
                    String value = block.optString("text", "");
                    if (!value.isEmpty()) {
                        pendingUserParts.put(new JSONObject().put("type", "text").put("text", value));
                    }
                } else if ("image".equals(type)) {
                    JSONObject source = block.optJSONObject("source");
                    if (source == null || !"base64".equals(source.optString("type"))) continue;
                    String data = source.optString("data", "");
                    // 空 data 不产出片段：一个空的 data URI 会被服务端判成非法图片。
                    if (data.isEmpty()) continue;
                    String mediaType = source.optString("media_type", "image/jpeg");
                    pendingUserParts.put(new JSONObject()
                            .put("type", "image_url")
                            .put("image_url", new JSONObject()
                                    .put("url", "data:" + mediaType + ";base64," + data)));
                }
            }
            flushUserParts(out, pendingUserParts);
        }

        private static void flushUserParts(JSONArray out, JSONArray parts) throws Exception {
            if (parts == null || parts.length() == 0) return;
            if (parts.length() == 1) {
                JSONObject only = parts.optJSONObject(0);
                if (only != null && "text".equals(only.optString("type"))) {
                    // 单个纯文本片段降级成字符串，兼容不认内容数组的老网关。
                    out.put(new JSONObject().put("role", "user").put("content", only.optString("text", "")));
                    return;
                }
            }
            out.put(new JSONObject().put("role", "user").put("content", parts));
        }

        /**
         * 把工具结果的内容压成纯文本。
         *
         * <p>这个协议的工具结果只能是字符串（不像 Anthropic 可以带图片块）。
         * 数组里的 text 片段取 {@code text} 字段，其它类型的块**原样序列化后带上**。
         *
         * <p>关于后半句：直接把 JSON 文本塞进去看起来很难看，但它是**无损**的 ——
         * 换成「[图片已省略]」这样的占位符会让模型完全不知道那里有什么，
         * 而带上原始结构至少还能看出「这里有一段非文本内容」以及它的形状。
         * 这是一个刻意保留的既有行为，不是遗漏的重构。
         */
        private static String toolResultText(Object content) {
            if (content == null || content == JSONObject.NULL) return "";
            if (content instanceof String) return (String) content;
            if (content instanceof JSONArray) {
                JSONArray array = (JSONArray) content;
                StringBuilder out = new StringBuilder();
                for (int i = 0; i < array.length(); i++) {
                    Object item = array.opt(i);
                    if (item == null || item == JSONObject.NULL) continue;
                    String piece = item instanceof JSONObject
                            ? textOf((JSONObject) item)
                            : String.valueOf(item);
                    if (out.length() > 0) out.append('\n');
                    out.append(piece);
                }
                return out.toString();
            }
            return String.valueOf(content);
        }

        private static String textOf(JSONObject block) {
            if ("text".equals(block.optString("type"))) return block.optString("text", "");
            // 非文本块：连同结构一起带上，保住「这里有什么」这条信息。
            return block.toString();
        }
    }

    // ------------------------------------------------------------ 小工具

    /**
     * 从 content 里取文本。它可能是字符串，也可能是内容块数组。
     *
     * <p>数组形式出现在两种情况下：模型开了多模态输出，或者网关自己把
     * 纯文本包成了数组。两种都要能读。
     */
    private static String contentText(Object content) {
        if (content == null || content == JSONObject.NULL) return "";
        if (content instanceof String) return (String) content;
        if (content instanceof JSONArray) {
            JSONArray array = (JSONArray) content;
            StringBuilder out = new StringBuilder();
            for (JSONObject part : JsonItems.of(array)) {
                if ("text".equals(part.optString("type"))) out.append(part.optString("text", ""));
            }
            return out.toString();
        }
        return String.valueOf(content);
    }

    private static String stringValue(Object value) {
        return value instanceof String ? (String) value : "";
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.isEmpty()) return value;
        }
        return "";
    }

    /**
     * 把服务端的 finish_reason 映射成引擎认识的 stopReason。
     *
     * <p>三个特殊值必须映射：{@code tool_calls} 决定引擎去执行工具、
     * {@code length} 决定它是否续跑被截断的回答、{@code stop} 是正常结束。
     * 认不出的原样透传 —— 引擎对未知值按「正常结束」处理，
     * 而编造一个映射会让未来新增的值被误判。
     */
    private static String mapFinishReason(String reason) {
        if (reason == null) return null;
        if ("tool_calls".equals(reason) || "function_call".equals(reason)) return "tool_use";
        if ("length".equals(reason)) return "max_tokens";
        if ("stop".equals(reason)) return "end_turn";
        return reason;
    }

    /**
     * 解工具入参。
     *
     * <p>解不开时把原文放进 {@code _raw_invalid_json}，而不是抛异常：
     * Agent 能在工具结果里看到坏掉的原文，从而判断是模型发坏了还是自己拼错了。
     */
    private static JSONObject parseToolArguments(String raw) {
        if (raw == null || raw.trim().isEmpty()) return new JSONObject();
        try {
            return new JSONObject(raw);
        } catch (JSONException malformed) {
            try {
                return new JSONObject().put("_raw_invalid_json", raw);
            } catch (JSONException impossible) {
                return new JSONObject();
            }
        }
    }

    private static String truncate(String value) {
        if (value == null) return "";
        return value.length() <= MAX_ERROR_CHARS
                ? value
                : value.substring(0, MAX_ERROR_CHARS) + "\n…truncated…";
    }
}
