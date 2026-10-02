package com.termux.app.zhicode.api;

import com.termux.app.zhicode.api.compat.ReasoningMapper;
import com.termux.app.zhicode.model.AssistantTurn;
import com.termux.app.zhicode.model.SessionConfig;
import com.termux.app.zhicode.model.ToolCall;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

/**
 * Anthropic Messages 协议的流式传输（纯 Java，不依赖任何外部运行时）。
 *
 * <h3>分成两层</h3>
 * <ul>
 *   <li><b>传输层</b>（本类）：拼请求体、发请求、读出 SSE 的 {@code event:}/{@code data:}
 *       成对内容，把每个事件交给解码器。</li>
 *   <li><b>解码层</b>（{@link StreamDecoder}）：把事件流还原成
 *       {@link AssistantTurn}。它不碰网络，因此逻辑可以逐条单独推理。</li>
 * </ul>
 * 拆开的实际好处不是「好看」：上游把两者写在一个循环里，于是「哪一行代码算协议语义、
 * 哪一行算网络处理」分不清，改一个超时都要先读懂协议状态机。
 *
 * <h3>内容块按 index 建累加器</h3>
 * 一条回复由若干内容块组成（正文、思考、工具调用），各自用 {@code index} 标识，
 * 而它们的增量事件是<b>交错</b>到达的。所以必须按 index 分别累积，
 * 最后再按 index 顺序输出 —— 直接按到达顺序拼会把正文与工具调用搅在一起。
 *
 * <h3>三件不能想当然的事</h3>
 * <ol>
 *   <li><b>input token 要三个字段相加。</b> Anthropic 把新 token、
 *       写缓存、读缓存分开报，三者都占用上下文窗口。只取 {@code input_tokens}
 *       会得到偏小的用量，进而让自动压缩判断得太晚。</li>
 *   <li><b>{@code content_block_start} 里可能已经带了内容。</b> 正文/思考/签名都可能
 *       在起始事件里就有初值，不能假设它们一定从空开始。</li>
 *   <li><b>没收到 {@code message_stop} 就是错误。</b> 连接被中断时读到的是一段
 *       「看起来完整」的回复，静默接受会让用户拿到被截断的答案而不自知。
 *       这里抛出带 {@code stream_read_error} 的消息，引擎据此重试一次。</li>
 * </ol>
 */
public final class AnthropicMessagesProvider implements ModelProvider {

    /**
     * {@code anthropic-version} 的取值。
     *
     * <p>包内可见而不是 private：{@link ModelCatalogClient} 拉模型目录时也要带同一个版本头。
     * 这个字符串是线上契约（服务端按它选报文格式），两处各写一份迟早会不一致。
     */
    static final String API_VERSION = "2023-06-01";

    /** 端点叶子；{@code /v1} 版本段由 sessionEndpoint 负责（用户 base 常带 /v1）。 */
    private static final String MESSAGES_PATH = "/messages";
    private static final String USER_AGENT = "ZhiCodeAndroid-JavaNative/0.15";

    /** 错误体回显上限。上游偶尔把整个 HTML 错误页塞进来，全量拼进消息会淹掉真正的信息。 */
    private static final int MAX_ERROR_CHARS = 12_000;

    private static final String EVENT_FIELD = "event:";
    private static final String DATA_FIELD = "data:";

    private final HttpRequestTracker requests = new HttpRequestTracker();

    @Override
    public void cancelRequest(Thread worker) {
        requests.cancel(worker);
    }

    @Override
    public AssistantTurn createMessage(SessionConfig config, String systemPrompt, JSONArray messages,
                                       JSONArray tools, StreamListener listener) throws Exception {
        String baseUrl = ApiUrlPolicy.requireBaseUrl(config);
        if (config.apiKey == null || config.apiKey.trim().isEmpty()) {
            throw new IllegalStateException("Anthropic API key is not configured");
        }

        HttpURLConnection conn = openConnection(baseUrl, config);
        HttpRequestTracker.Scope request = requests.begin(conn);
        StreamDecoder decoder = new StreamDecoder(listener);

        try {
            writeRequestBody(conn, buildRequestBody(config, systemPrompt, messages, tools));

            int status = conn.getResponseCode();
            request.markResponseStarted();
            if (status < 200 || status >= 300) {
                throw new IllegalStateException(
                        "ZhiCode API HTTP " + status + ": " + truncate(readAll(conn.getErrorStream())));
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                while (readEvent(reader, decoder)) {
                    // 循环体为空是有意的：readEvent 每调用一次消费一个 data 事件，
                    // 返回 false 表示流结束或解码器要求停止。逻辑都在解码器里。
                }
            }
        } catch (IOException failure) {
            if (Thread.currentThread().isInterrupted()) throw failure;
            throw new StreamFailure(request.failureCode(failure), request.failureMessage(failure), failure);
        } finally {
            request.close();
        }

        if (!decoder.terminalEventSeen) {
            // 这个措辞是契约：引擎按 "stream_read_error" 子串判定可重试。
            throw new IllegalStateException("stream_read_error: model stream ended before message_stop");
        }
        return decoder.toTurn();
    }

    // ------------------------------------------------------------------ 请求

    private static JSONObject buildRequestBody(SessionConfig config, String systemPrompt,
                                               JSONArray messages, JSONArray tools) throws JSONException {
        JSONObject body = new JSONObject();
        body.put("model", config.model);
        // max_tokens 是 Anthropic 的必填项，没有「不限制」这个选项，所以无条件带上。
        body.put("max_tokens", config.maxTokens);
        body.put("stream", true);
        body.put("system", systemPrompt);
        // messages 与 tools 已经是本协议的形状（引擎内部就按 Anthropic 形状维护历史），
        // 所以这里原样透传，不做任何重映射 —— 做重映射只会引入一处能改错的地方。
        body.put("messages", messages);
        body.put("tools", tools);

        String effort = ReasoningMapper.anthropicEffort(config.effort);
        if (effort != null) {
            body.put("output_config", new JSONObject().put("effort", effort));
        }
        return body;
    }

    private static HttpURLConnection openConnection(String baseUrl, SessionConfig config) throws IOException {
        // /v1 去重见 sessionEndpoint：base 里带了 /v1 就不能再拼一份（否则 404）。
        String endpoint = ApiEndpointResolver.sessionEndpoint(baseUrl, MESSAGES_PATH, true);
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setUseCaches(false);
        conn.setRequestProperty("content-type", "application/json");
        conn.setRequestProperty("accept", "text/event-stream");
        // Anthropic 用 x-api-key，不是 Authorization: Bearer —— 发错头会得到 401，
        // 而错误信息里不会说明是头的问题。
        conn.setRequestProperty("x-api-key", config.apiKey);
        conn.setRequestProperty("anthropic-version", API_VERSION);
        conn.setRequestProperty("user-agent", USER_AGENT);
        return conn;
    }

    private static void writeRequestBody(HttpURLConnection conn, JSONObject body) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(conn.getOutputStream(), StandardCharsets.UTF_8))) {
            writer.write(body.toString());
        }
    }

    // ------------------------------------------------------------ SSE 分帧

    /**
     * 读一个 {@code data:} 事件并交给解码器。
     *
     * @return 是否应继续读；{@code false} 表示流已结束或解码器要求停止
     */
    private static boolean readEvent(BufferedReader reader, StreamDecoder decoder) throws Exception {
        String eventName = null;
        String line;
        while ((line = reader.readLine()) != null) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("ZhiCode request interrupted");
            }
            if (line.startsWith(EVENT_FIELD)) {
                // 事件名只在缺少 type 字段时用作回落，所以记住即可，不必立即消费。
                eventName = line.substring(EVENT_FIELD.length()).trim();
                continue;
            }
            if (!line.startsWith(DATA_FIELD)) continue;

            String payload = line.substring(DATA_FIELD.length()).trim();
            if (payload.isEmpty()) continue;
            return decoder.onEvent(eventName, payload);
        }
        return false;
    }

    // ------------------------------------------------------------ 解码器

    /**
     * 把 Anthropic 的事件流还原成一条回复。
     *
     * <p>刻意做成独立类而不是主流程里的一堆 if：它没有任何 I/O，
     * 状态就是「各内容块的累加器 + 是否见过终止事件」，
     * 因此可以单独推理，也方便以后补事件类型。
     */
    static final class StreamDecoder {

        private final StreamListener listener;
        private final AssistantTurn turn = new AssistantTurn();
        /** 按 index 排序，保证输出顺序与协议给的顺序一致（事件到达顺序不一定一致）。 */
        private final Map<Integer, BlockAccumulator> blocks = new TreeMap<>();

        boolean terminalEventSeen;

        StreamDecoder(StreamListener listener) {
            this.listener = listener;
        }

        /** @return 是否继续读流 */
        boolean onEvent(String eventName, String payload) throws Exception {
            JSONObject event = new JSONObject(payload);
            String type = event.optString("type", eventName == null ? "" : eventName);

            if ("error".equals(type)) {
                // 服务端主动报错：优先用它的 message，缺失时把整段 payload 带上。
                JSONObject error = event.optJSONObject("error");
                throw new IllegalStateException(error == null ? payload : error.optString("message", payload));
            }
            if ("message_stop".equals(type)) {
                terminalEventSeen = true;
                return false;
            }
            if ("message_start".equals(type)) {
                applyMessageStart(event);
                return true;
            }
            if ("content_block_start".equals(type)) {
                applyBlockStart(event);
                return true;
            }
            if ("content_block_delta".equals(type)) {
                applyBlockDelta(event);
                return true;
            }
            if ("message_delta".equals(type)) {
                applyMessageDelta(event);
                return true;
            }
            // ping / content_block_stop 等没有需要保留的信息，静默忽略而不是报错：
            // 报错会让服务端新增一个无害事件就打断整轮对话。
            return true;
        }

        private void applyMessageStart(JSONObject event) {
            JSONObject message = event.optJSONObject("message");
            JSONObject usage = message == null ? null : message.optJSONObject("usage");
            if (usage == null) return;
            turn.inputTokens = contextTokens(usage, turn.inputTokens);
            emitUsage();
        }

        private void applyBlockStart(JSONObject event) {
            int index = event.optInt("index", -1);
            JSONObject start = event.optJSONObject("content_block");
            if (index < 0 || start == null) return;
            BlockAccumulator accumulator = BlockAccumulator.of(start);
            blocks.put(index, accumulator);
            accumulator.seedFrom(start);
        }

        private void applyBlockDelta(JSONObject event) {
            BlockAccumulator accumulator = blocks.get(event.optInt("index", -1));
            JSONObject delta = event.optJSONObject("delta");
            if (accumulator == null || delta == null) return;
            accumulator.applyDelta(delta, listener);
        }

        private void applyMessageDelta(JSONObject event) {
            JSONObject delta = event.optJSONObject("delta");
            if (delta != null && delta.has("stop_reason") && !delta.isNull("stop_reason")) {
                turn.stopReason = delta.optString("stop_reason", null);
            }
            JSONObject usage = event.optJSONObject("usage");
            if (usage == null) return;
            // 只在确实报了 input 相关字段时才覆盖：message_delta 通常只给 output_tokens，
            // 无条件覆盖会把前面 message_start 里正确读到的 input 用量抹成 0。
            if (usage.has("input_tokens") || usage.has("cache_creation_input_tokens")
                    || usage.has("cache_read_input_tokens")) {
                turn.inputTokens = contextTokens(usage, turn.inputTokens);
            }
            if (usage.has("output_tokens")) {
                turn.outputTokens = usage.optLong("output_tokens", turn.outputTokens);
            }
            emitUsage();
        }

        private void emitUsage() {
            if (listener != null) listener.onUsage(turn.inputTokens, turn.outputTokens);
        }

        /** 组装最终回复：内容块按 index 顺序，工具调用单独收集。 */
        AssistantTurn toTurn() throws JSONException {
            for (BlockAccumulator accumulator : blocks.values()) {
                JSONObject block = accumulator.toContentBlock();
                if (block != null) turn.content.put(block);
                ToolCall call = accumulator.toToolCall();
                if (call != null) turn.toolCalls.add(call);
            }
            return turn;
        }

        /**
         * 占用上下文窗口的 token 数。
         *
         * <p>新 token、写缓存、读缓存三者<b>都</b>占用窗口，所以是相加关系。
         * 相加结果为 0 时保留原值：那说明这个事件里根本没报用量，
         * 而不是「用量是 0」。
         */
        private static long contextTokens(JSONObject usage, long fallback) {
            long total = usage.optLong("input_tokens", 0L)
                    + usage.optLong("cache_creation_input_tokens", 0L)
                    + usage.optLong("cache_read_input_tokens", 0L);
            return total > 0L ? total : fallback;
        }
    }

    // ------------------------------------------------------------ 内容块累加

    /**
     * 一个内容块的累加器。
     *
     * <p>用子类而不是一个「什么字段都带」的袋子：每种块保留哪些字段、
     * 认哪些增量类型，是明确的、写在各自的类里；输出时也不会多出无关字段
     * （多发的字段会被下一轮请求原样带回去，服务端可能因此拒绝）。
     */
    abstract static class BlockAccumulator {

        final String type;

        BlockAccumulator(String type) {
            this.type = type;
        }

        static BlockAccumulator of(JSONObject start) {
            String type = start.optString("type", "");
            if ("text".equals(type)) return new TextBlock();
            if ("thinking".equals(type)) return new ThinkingBlock();
            if ("tool_use".equals(type)) return new ToolUseBlock(start);
            return new PassthroughBlock(type, start);
        }

        /** 起始事件里可能已经带了内容，各子类按需取用。 */
        void seedFrom(JSONObject start) {}

        abstract void applyDelta(JSONObject delta, StreamListener listener);

        /** @return 要放进回复的内容块；{@code null} 表示这种块不保留 */
        abstract JSONObject toContentBlock() throws JSONException;

        /** @return 这是一个工具调用时返回它，否则 {@code null} */
        ToolCall toToolCall() throws JSONException {
            return null;
        }
    }

    static final class TextBlock extends BlockAccumulator {
        private final StringBuilder text = new StringBuilder();

        TextBlock() {
            super("text");
        }

        @Override
        void seedFrom(JSONObject start) {
            text.append(start.optString("text", ""));
        }

        @Override
        void applyDelta(JSONObject delta, StreamListener listener) {
            if (!"text_delta".equals(delta.optString("type", ""))) return;
            String chunk = delta.optString("text", "");
            if (chunk.isEmpty()) return;
            text.append(chunk);
            if (listener != null) listener.onTextDelta(chunk);
        }

        @Override
        JSONObject toContentBlock() throws JSONException {
            return new JSONObject().put("type", "text").put("text", text.toString());
        }
    }

    static final class ThinkingBlock extends BlockAccumulator {
        private final StringBuilder thinking = new StringBuilder();
        private final StringBuilder signature = new StringBuilder();

        ThinkingBlock() {
            super("thinking");
        }

        @Override
        void seedFrom(JSONObject start) {
            thinking.append(start.optString("thinking", ""));
            signature.append(start.optString("signature", ""));
        }

        @Override
        void applyDelta(JSONObject delta, StreamListener listener) {
            String kind = delta.optString("type", "");
            if ("thinking_delta".equals(kind)) {
                String chunk = delta.optString("thinking", "");
                if (chunk.isEmpty()) return;
                thinking.append(chunk);
                if (listener != null) listener.onThinkingDelta(chunk);
                return;
            }
            if ("signature_delta".equals(kind)) {
                // 签名不用通知界面：它是给服务端验证推理来源用的，不是给人看的。
                signature.append(delta.optString("signature", ""));
            }
        }

        @Override
        JSONObject toContentBlock() throws JSONException {
            JSONObject block = new JSONObject().put("type", "thinking").put("thinking", thinking.toString());
            // 空签名不进结果：发一个空串可能被判为「签名无效」而拒绝整段推理回放。
            if (signature.length() > 0) block.put("signature", signature.toString());
            return block;
        }
    }

    static final class ToolUseBlock extends BlockAccumulator {
        private final String id;
        private final String name;
        private final StringBuilder inputJson = new StringBuilder();

        ToolUseBlock(JSONObject start) {
            super("tool_use");
            // id 与 name 只在 content_block_start 里出现，增量事件里没有它们，
            // 所以必须在这里抓住 —— 这也是 onToolInputDelta 可能拿到 null 的原因。
            id = start.optString("id", null);
            name = start.optString("name", null);
        }

        @Override
        void applyDelta(JSONObject delta, StreamListener listener) {
            if (!"input_json_delta".equals(delta.optString("type", ""))) return;
            String partial = delta.optString("partial_json", "");
            inputJson.append(partial);
            if (listener != null) listener.onToolInputDelta(id, name, partial);
        }

        @Override
        JSONObject toContentBlock() throws JSONException {
            return new JSONObject()
                    .put("type", "tool_use")
                    .put("id", id)
                    .put("name", name)
                    .put("input", parseToolInput(inputJson.toString()));
        }

        @Override
        ToolCall toToolCall() throws JSONException {
            return new ToolCall(id, name, parseToolInput(inputJson.toString()));
        }
    }

    /**
     * 认不出的块类型：原样保留起始事件里的内容。
     *
     * <p>不丢弃是有意的 —— 服务端新增一种块类型时，把它原样带进历史
     * 比丢掉更可能让下一轮请求仍然有效（丢掉会让历史缺了模型自己发过的内容）。
     */
    static final class PassthroughBlock extends BlockAccumulator {
        private final JSONObject start;

        PassthroughBlock(String type, JSONObject start) {
            super(type);
            this.start = start;
        }

        @Override
        void applyDelta(JSONObject delta, StreamListener listener) {
            // 不知道怎么合并增量，所以一个字段都不改：宁可少一段也不能拼出非法结构。
        }

        @Override
        JSONObject toContentBlock() throws JSONException {
            return new JSONObject(start.toString());
        }
    }

    // ------------------------------------------------------------ 小工具

    /**
     * 把工具入参的 JSON 文本解成对象。
     *
     * <p>解不开时返回 {@code {"_raw_invalid_json": <原始文本>}} 而不是抛异常：
     * 这样 Agent 能在工具结果里看到坏掉的原文，从而判断是自己拼错了还是模型发坏了。
     * 直接抛异常会把整轮对话打断，而错因被埋在异常栈里。
     */
    private static JSONObject parseToolInput(String raw) {
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

    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) out.append(line).append('\n');
        }
        return out.toString();
    }

    private static String truncate(String value) {
        if (value == null) return "";
        return value.length() <= MAX_ERROR_CHARS
                ? value
                : value.substring(0, MAX_ERROR_CHARS) + "\n…truncated…";
    }
}
