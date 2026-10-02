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
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * OpenAI Responses 协议的流式传输，含 Codex 变体。
 *
 * <h3>Codex 变体与标准 Responses 的差异（都是刻意的）</h3>
 * <ul>
 *   <li>端点名字不同：标准用 {@code /v1/responses}，Codex 用 {@code /responses}；</li>
 *   <li>Codex 必须发 {@code store:false}，且<b>不发</b> {@code max_output_tokens}
 *       （那个端点会拒绝这个字段）；</li>
 *   <li>Codex 要求四个自称身份的头（{@code originator} / {@code session-id} /
 *       {@code thread-id} / {@code x-client-request-id}），并带 {@code prompt_cache_key}；</li>
 *   <li>{@code toolMode=native} 时 {@code Bash} 工具换成 {@code {"type":"shell"}}
 *       （Codex 有自己的 shell 工具，不认我们的 function 定义）。</li>
 * </ul>
 *
 * <h3>三处必须逐字保持的算法</h3>
 * <ol>
 *   <li><b>快照对账</b>（{@link #snapshotDelta}）：这个协议对同一段文本既发增量
 *       （{@code ....delta}）又发整段快照（{@code ....done}、终局 output）。直接追加会重复，
 *       直接覆盖会让界面上的正文跳一下。必须只补差值。</li>
 *   <li><b>历史配对</b>（{@link HistoryBuilder#pairedCallIds}）：这个协议会
 *       因为历史里存在一个没有对应结果的 {@code function_call} 而<b>整份请求被拒</b>。
 *       老会话在进程被杀、工具被取消、或早期版本压缩边界不干净时正好会长成那个形状。
 *       所以只发「确实有后续结果」的调用，其余降级成普通文本。</li>
 *   <li><b>Codex 的 commentary/final_answer 去重</b>（{@link TextAccumulator}）：
 *       该分支会把同一条最终答复发两遍（一遍作为 commentary、一遍作为
 *       final_answer）。不去重的话用户会看到整段回答出现两次。</li>
 * </ol>
 *
 * <p>结构上分三层：{@link HistoryBuilder}（历史 → 请求体）、
 * {@link StreamDecoder}（事件流 → 回复）、以及传输层（本类的 createMessage）。
 * 解码器不含 I/O，因此配对与去重这类状态机可以单独推理。
 */
public final class OpenAIResponsesProvider implements ModelProvider {

    /** Codex 客户端自称的版本号。改它等于对服务端声称是另一个客户端版本。 */
    private static final String CODEX_CLIENT_VERSION = "2.1.87";

    private static final String USER_AGENT_STANDARD = "ZhiCodeAndroid-JavaNative/0.15";
    private static final int MAX_ERROR_CHARS = 12_000;
    private static final int MAX_CACHE_KEY_CHARS = 512;

    private static final String DATA_FIELD = "data:";
    private static final String EVENT_FIELD = "event:";
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
        String baseUrl = ApiUrlPolicy.requireBaseUrl(config);
        if (config.apiKey == null || config.apiKey.trim().isEmpty()) {
            throw new IllegalStateException("API key is not configured");
        }
        boolean codex = "codex-responses".equals(config.protocol);
        HttpURLConnection conn = openConnection(baseUrl, config, codex);
        HttpRequestTracker.Scope request = requests.begin(conn);
        StreamDecoder decoder = new StreamDecoder(listener);

        try {
            JSONObject body = codex
                    ? HistoryBuilder.buildCodexRequest(config, systemPrompt, messages, tools)
                    : HistoryBuilder.buildStandardRequest(config, systemPrompt, messages, tools);
            writeRequestBody(conn, body);

            int status = conn.getResponseCode();
            request.markResponseStarted();
            if (status < 200 || status >= 300) {
                // 刻意不做「400 就降低推理档位重发」：那会让用户设的档位静默失效。
                throw new IllegalStateException("Responses API HTTP " + status + ": "
                        + truncate(readAll(conn.getErrorStream()), MAX_ERROR_CHARS));
            }

            if (isPlainJson(conn.getContentType())) {
                decoder.applyTerminalResponse(new JSONObject(readAll(conn.getInputStream())));
            } else {
                readSse(conn.getInputStream(), decoder);
                if (!decoder.terminalEventSeen) {
                    // 这个协议把「流提前结束」表达成结构化失败码，而不是错误文案。
                    // 换掉它会让引擎的重试静默失效。
                    throw new StreamFailure("stream_read_error",
                            "stream closed before response.completed/response.incomplete");
                }
            }
        } catch (IOException failure) {
            if (Thread.currentThread().isInterrupted()) throw failure;
            // StreamFailure 是 IOException 的子类，上面抛出的那个必须原样穿过，
            // 否则会被重新包装成网络错误而丢掉 stream_read_error 这个码。
            if (failure instanceof StreamFailure) throw failure;
            throw new StreamFailure(request.failureCode(failure), request.failureMessage(failure), failure);
        } finally {
            request.close();
        }

        return decoder.toTurn();
    }

    /** 非流式：content-type 明说是 json 且不是 event-stream。 */
    private static boolean isPlainJson(String contentType) {
        if (contentType == null) return false;
        String lower = contentType.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("json") && !lower.contains("event-stream");
    }

    private static HttpURLConnection openConnection(String baseUrl, SessionConfig config,
                                                    boolean codex) throws IOException {
        // 端点拼接待 /v1 去重（用户 base 里常带 /v1）：见 sessionEndpoint 的说明。
        // 叶子统一是 /responses，标准变体的 /v1 版本段由 versioned 负责 ——
        // 这里要是传整条 "/v1/responses" 当叶子，标准分支就会拼成 /v1/v1/responses。
        String endpoint = ApiEndpointResolver.sessionEndpoint(baseUrl, "/responses", !codex);
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setUseCaches(false);
        conn.setRequestProperty("content-type", "application/json");
        conn.setRequestProperty("accept", "text/event-stream");
        conn.setRequestProperty("authorization", "Bearer " + config.apiKey);
        conn.setRequestProperty("user-agent", codex
                ? "codex_cli_rs/" + CODEX_CLIENT_VERSION + " (zhicode-translation)"
                : USER_AGENT_STANDARD);
        if (codex) {
            // 这四个头是 Codex 端点用来关联会话的，缺一个都可能被拒。
            String session = nonBlank(config.sessionId) ? config.sessionId : UUID.randomUUID().toString();
            String thread = nonBlank(config.threadId) ? config.threadId : session;
            conn.setRequestProperty("originator", "codex_cli_rs");
            conn.setRequestProperty("session-id", session);
            conn.setRequestProperty("thread-id", thread);
            conn.setRequestProperty("x-client-request-id", truncate(thread, MAX_CACHE_KEY_CHARS));
        }
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
     * 读这个协议的 SSE。
     *
     * <p>与另外两家的关键差别：这里的 {@code data} <b>可以跨多行</b>，
     * 而事件边界是<b>空行</b>。逐行解析会在遇到多行 payload 时丢掉后半段
     * （表现为 JSON 解析失败或内容缺失）。
     */
    private static void readSse(InputStream in, StreamDecoder decoder) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String eventName = null;
            StringBuilder data = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("ZhiCode request interrupted");
                }
                if (line.isEmpty()) {
                    if (data.length() > 0) {
                        decoder.applyEvent(eventName, data.toString());
                        data.setLength(0);
                        if (decoder.terminalEventSeen) return;
                    }
                    eventName = null;
                    continue;
                }
                if (line.startsWith(EVENT_FIELD)) {
                    eventName = line.substring(EVENT_FIELD.length()).trim();
                } else if (line.startsWith(DATA_FIELD)) {
                    if (data.length() > 0) data.append('\n');
                    data.append(line.substring(DATA_FIELD.length()).trim());
                }
            }
            // 有些服务端最后一条事件后面没有空行，EOF 时要再处理一次。
            if (data.length() > 0) decoder.applyEvent(eventName, data.toString());
        }
    }

    // ------------------------------------------------------------ 历史 → 请求体

    /**
     * 把引擎的 Anthropic 形状历史翻译成 Responses 的 items，并组装请求体。
     *
     * <p>分标准与 Codex 两个入口，而不是传一个 boolean：两者被允许发的字段确实不同
     * （见类注释），把它写成两个方法后「这个变体发了什么」是一眼可见的，
     * 而一个 boolean 参数会让差异散落在 if 里。
     */
    static final class HistoryBuilder {

        private HistoryBuilder() {}

        static JSONObject buildStandardRequest(SessionConfig config, String systemPrompt,
                                               JSONArray messages, JSONArray tools) throws Exception {
            JSONObject body = baseRequest(config, systemPrompt, messages, tools, false);
            // 这个字段只有标准端点接受；Codex 端点会直接拒绝整份请求。
            body.put("max_output_tokens", config.maxTokens);
            return body;
        }

        static JSONObject buildCodexRequest(SessionConfig config, String systemPrompt,
                                            JSONArray messages, JSONArray tools) throws Exception {
            JSONObject body = baseRequest(config, systemPrompt, messages, tools, true);
            if (nonBlank(config.sessionId)) {
                String thread = nonBlank(config.threadId) ? config.threadId : config.sessionId;
                body.put("prompt_cache_key", truncate(thread, MAX_CACHE_KEY_CHARS));
            }
            return body;
        }

        private static JSONObject baseRequest(SessionConfig config, String systemPrompt, JSONArray messages,
                                             JSONArray tools, boolean codex) throws Exception {
            JSONObject body = new JSONObject();
            body.put("model", config.model);
            // 这个协议的 system 叫 instructions，而且是顶层字段。
            if (systemPrompt != null && !systemPrompt.isEmpty()) body.put("instructions", systemPrompt);
            body.put("input", buildInput(messages));
            body.put("stream", true);
            // 无论哪个变体都发 store:false：不把用户的对话留在服务端。
            body.put("store", false);

            JSONArray mappedTools = mapTools(tools, codex && "native".equals(config.toolMode));
            if (mappedTools.length() > 0) {
                body.put("tools", mappedTools);
                body.put("tool_choice", "auto");
                body.put("parallel_tool_calls", true);
            }

            String configured = ReasoningMapper.openAIResponsesConfiguredEffort(config.effort);
            String wire = ReasoningMapper.openAIResponsesWireEffort(config.effort);
            boolean reasoningOff = "none".equals(configured)
                    || "0".equals(ReasoningMapper.normalizeRequestedEffort(config.effort));
            if (wire != null && !reasoningOff) {
                JSONObject reasoning = new JSONObject().put("effort", wire);
                if (config.reasoningSummary != null && !config.reasoningSummary.isEmpty()
                        && !"off".equals(config.reasoningSummary)) {
                    reasoning.put("summary", config.reasoningSummary);
                }
                body.put("reasoning", reasoning);
            }
            if (!reasoningOff && config.preserveReasoningState) {
                // 索要加密推理内容，这样下一轮能把模型的推理原样还回去。
                body.put("include", new JSONArray().put("reasoning.encrypted_content"));
            }
            return body;
        }

        private static JSONArray mapTools(JSONArray tools, boolean nativeShell) throws Exception {
            JSONArray out = new JSONArray();
            if (tools == null) return out;
            for (JSONObject tool : JsonItems.of(tools)) {
                String name = tool.optString("name", "");
                if (name.isEmpty()) continue;
                if (nativeShell && "Bash".equals(name)) {
                    // 换成 Codex 自己的 shell 工具，原来的 schema 一并丢掉 ——
                    // 两者参数不同，带上我们的 schema 反而会让服务端拒绝。
                    out.put(new JSONObject().put("type", "shell"));
                    continue;
                }
                JSONObject schema = tool.optJSONObject("input_schema");
                out.put(new JSONObject()
                        .put("type", "function")
                        .put("name", name)
                        .put("description", tool.optString("description", ""))
                        .put("parameters", schema == null ? new JSONObject().put("type", "object") : schema)
                        .put("strict", false));
            }
            return out;
        }

        /**
         * 历史 → items。
         *
         * <p>两条降级规则（都要保留，否则老会话会让整份请求被拒）：
         * <ul>
         *   <li>assistant 里「没有配对结果的 function_call」→ 降级成普通文本
         *       {@code [Incomplete previous tool call <id>: <name> <args>]}；</li>
         *   <li>user 里「找不到对应调用的 tool_result」→ 降级成文本
         *       {@code [Previous tool result <id>]\n<output>}。</li>
         * </ul>
         * 降级而不是丢弃：那些内容对模型仍然有用，只是不能以协议项的形式出现。
         */
        static JSONArray buildInput(JSONArray messages) throws Exception {
            JSONArray out = new JSONArray();
            if (messages == null) return out;

            Set<String> paired = pairedCallIds(messages);
            Set<String> emittedCalls = new LinkedHashSet<>();
            Set<String> emittedOutputs = new LinkedHashSet<>();

            for (JSONObject message : JsonItems.of(messages)) {
                JSONArray blocks = message.optJSONArray("content");
                if (blocks == null) continue;
                if ("assistant".equals(message.optString("role", "user"))) {
                    appendAssistantItems(out, blocks, paired, emittedCalls);
                } else {
                    appendUserItems(out, blocks, paired, emittedCalls, emittedOutputs);
                }
            }
            return out;
        }

        private static void appendAssistantItems(JSONArray out, JSONArray blocks, Set<String> paired,
                                                 Set<String> emittedCalls) throws JSONException {
            JSONArray textParts = new JSONArray();
            for (JSONObject block : JsonItems.of(blocks)) {
                String type = block.optString("type", "");
                if ("text".equals(type)) {
                    String text = block.optString("text", "");
                    if (!text.isEmpty()) textParts.put(outputText(text));
                } else if ("tool_use".equals(type)) {
                    String callId = block.optString("id", "").trim();
                    String name = block.optString("name", UNKNOWN_TOOL);
                    JSONObject input = block.optJSONObject("input");
                    String arguments = input == null ? "{}" : input.toString();

                    // 只有「这个 id 确实有后续结果」且「还没发过」的调用才能作为
                    // function_call 发出去。否则整份请求会被拒。
                    boolean canEmit = !callId.isEmpty()
                            && !emittedCalls.contains(callId)
                            && paired.contains(callId);
                    if (!canEmit) {
                        String prefix = callId.isEmpty()
                                ? "Previous tool call"
                                : "Incomplete previous tool call " + callId;
                        textParts.put(outputText("[" + prefix + ": " + name + " " + arguments + "]"));
                        continue;
                    }
                    // 先把已有正文落盘，保证 items 顺序与历史一致
                    // （文本在调用之前，调用在结果之前）。
                    if (textParts.length() > 0) {
                        out.put(assistantMessage(textParts));
                        textParts = new JSONArray();
                    }
                    out.put(new JSONObject()
                            .put("type", "function_call")
                            .put("call_id", callId)
                            .put("name", name)
                            .put("arguments", arguments));
                    emittedCalls.add(callId);
                }
                // thinking 块不落进可见文本：它是传输层产物，
                // 当成「模型说过的话」回放会让它把内部推理误认为自己的输出。
            }
            if (textParts.length() > 0) out.put(assistantMessage(textParts));
        }

        private static void appendUserItems(JSONArray out, JSONArray blocks, Set<String> paired,
                                            Set<String> emittedCalls, Set<String> emittedOutputs)
                throws JSONException {
            JSONArray userParts = new JSONArray();
            for (JSONObject block : JsonItems.of(blocks)) {
                String type = block.optString("type", "");
                if ("tool_result".equals(type)) {
                    String callId = block.optString("tool_use_id", "").trim();
                    String output = toolResultText(block.opt("content"));
                    boolean canEmit = !callId.isEmpty()
                            && paired.contains(callId)
                            && emittedCalls.contains(callId)
                            && !emittedOutputs.contains(callId);
                    if (canEmit) {
                        // 同样先把累积的 user 片段落盘，否则顺序会反
                        //（结果必须紧跟在它所回应的调用之后）。
                        if (userParts.length() > 0) {
                            out.put(userMessage(userParts));
                            userParts = new JSONArray();
                        }
                        out.put(new JSONObject()
                                .put("type", "function_call_output")
                                .put("call_id", callId)
                                .put("output", output));
                        emittedOutputs.add(callId);
                    } else {
                        // 孤儿结果（有结果、没有调用）会让服务端以
                        // "No tool call found ..." 拒掉整份请求，所以降级成用户文本。
                        String label = callId.isEmpty() ? "unknown" : callId;
                        userParts.put(inputText("[Previous tool result " + label + "]\n" + output));
                    }
                } else if ("text".equals(type)) {
                    String text = block.optString("text", "");
                    if (!text.isEmpty()) userParts.put(inputText(text));
                } else if ("image".equals(type)) {
                    JSONObject source = block.optJSONObject("source");
                    if (source == null || !"base64".equals(source.optString("type"))) continue;
                    String data = source.optString("data", "");
                    if (data.isEmpty()) continue;
                    String media = source.optString("media_type", "image/jpeg");
                    userParts.put(new JSONObject()
                            .put("type", "input_image")
                            .put("image_url", "data:" + media + ";base64," + data));
                }
            }
            if (userParts.length() > 0) out.put(userMessage(userParts));
        }

        private static JSONObject assistantMessage(JSONArray parts) throws JSONException {
            return new JSONObject().put("type", "message").put("role", "assistant").put("content", parts);
        }

        private static JSONObject userMessage(JSONArray parts) throws JSONException {
            return new JSONObject().put("type", "message").put("role", "user").put("content", parts);
        }

        private static JSONObject outputText(String text) throws JSONException {
            return new JSONObject().put("type", "output_text").put("text", text).put("annotations", new JSONArray());
        }

        private static JSONObject inputText(String text) throws JSONException {
            return new JSONObject().put("type", "input_text").put("text", text);
        }

        /**
         * 找出「确实有后续结果」的调用 id。
         *
         * <p>不能简单地先收集全部调用 id、再看结果里有没有 —— 损坏的历史里可能出现
         * 「结果在前、同名调用在后」，那样按全集判断会认为配对成立，
         * 于是发出去一个仍然悬挂的 {@code function_call}。
         * 所以必须按顺序：只有先出现过的调用，其后的结果才算配对。
         */
        static Set<String> pairedCallIds(JSONArray messages) {
            Set<String> seenCalls = new LinkedHashSet<>();
            Set<String> paired = new LinkedHashSet<>();
            if (messages == null) return paired;
            for (JSONObject message : JsonItems.of(messages)) {
                JSONArray blocks = message.optJSONArray("content");
                if (blocks == null) continue;
                for (JSONObject block : JsonItems.of(blocks)) {
                    String type = block.optString("type", "");
                    if ("tool_use".equals(type)) {
                        String id = block.optString("id", "").trim();
                        if (!id.isEmpty()) seenCalls.add(id);
                    } else if ("tool_result".equals(type)) {
                        String id = block.optString("tool_use_id", "").trim();
                        if (!id.isEmpty() && seenCalls.contains(id)) paired.add(id);
                    }
                }
            }
            return paired;
        }

        /**
         * 工具结果 → 纯文本。
         *
         * <p>数组里的 text 片段取 {@code text} 字段；其它元素原样序列化后带上。
         * 后者看着难看但是无损的 —— 换成占位符会让模型彻底不知道那里有什么。
         */
        private static String toolResultText(Object content) {
            if (content == null || content == JSONObject.NULL) return "";
            if (content instanceof String) return (String) content;
            if (content instanceof JSONArray) {
                JSONArray array = (JSONArray) content;
                StringBuilder out = new StringBuilder();
                for (int i = 0; i < array.length(); i++) {
                    Object item = array.opt(i);
                    if (item instanceof JSONObject && "text".equals(((JSONObject) item).optString("type"))) {
                        if (out.length() > 0) out.append('\n');
                        out.append(((JSONObject) item).optString("text", ""));
                    } else if (item != null) {
                        if (out.length() > 0) out.append('\n');
                        out.append(String.valueOf(item));
                    }
                }
                return out.toString();
            }
            return String.valueOf(content);
        }
    }

    // ------------------------------------------------------------ 解码器

    /**
     * 把 Responses 的事件流还原成一条回复。
     *
     * <p>无 I/O，状态全在 {@link #keys}、{@link #texts}、{@link #calls} 里。
     * 之所以要按<b>键</b>（而不是按到达顺序）组织，是因为同一个逻辑项会被多个事件提到，
     * 而且不同事件里给的身份字段不一样：{@code delta} 事件可能只有
     * {@code output_index}，{@code item} 事件才有 {@code id}/{@code call_id}。
     * {@link KeyIndex} 负责把它们归并到同一个键上。
     */
    static final class StreamDecoder {

        private final StreamListener listener;
        private final AssistantTurn turn = new AssistantTurn();
        private final KeyIndex keys = new KeyIndex();
        /** 每个输出项的最新已知形态（item 事件是增量的，要合并）。 */
        private final Map<String, JSONObject> items = new LinkedHashMap<>();
        private final Map<String, TextAccumulator> texts = new LinkedHashMap<>();
        private final Map<String, CallAccumulator> calls = new LinkedHashMap<>();
        private final StringBuilder thinking = new StringBuilder();

        /** 上一条 commentary 文本，用于识别 Codex 把同一条回答重发一遍的情况。 */
        private String lastCommentary;

        boolean terminalEventSeen;

        StreamDecoder(StreamListener listener) {
            this.listener = listener;
        }

        void applyEvent(String eventName, String payload) throws Exception {
            // 这个协议会在末尾发一个 [DONE]，但它**不算**终止事件：
            // 真正的终局是 response.completed / response.incomplete。
            if (payload == null || payload.isEmpty() || "[DONE]".equals(payload)) return;

            JSONObject event = new JSONObject(payload);
            String type = event.optString("type", eventName == null ? "" : eventName);

            if ("error".equals(type) || "response.failed".equals(type)) {
                throw failureFrom(event, payload);
            }

            JSONObject item = event.optJSONObject("item");
            if (item != null && ("response.output_item.added".equals(type)
                    || "response.output_item.done".equals(type))) {
                // 先处理 item：它带 id/call_id，会把键登记好，
                // 后面 resolveKey 才能把只带 output_index 的 delta 事件归到同一个键上。
                captureItem(event, item, "response.output_item.done".equals(type));
            }

            String key = keys.keyFor(event, item, type);
            applyTypedEvent(type, event, item, key);

            if ("response.completed".equals(type) || "response.incomplete".equals(type)) {
                terminalEventSeen = true;
                JSONObject response = event.optJSONObject("response");
                if (response != null) applyTerminalResponse(response);
                if ("response.incomplete".equals(type)) turn.stopReason = "max_tokens";
            }
        }

        private static StreamFailure failureFrom(JSONObject event, String payload) {
            JSONObject response = event.optJSONObject("response");
            JSONObject error = event.optJSONObject("error");
            if (error == null && response != null) error = response.optJSONObject("error");
            String code = event.optString("code", "");
            if (code.isEmpty() && error != null) code = error.optString("code", "");
            String message = error == null
                    ? event.optString("message", payload)
                    : error.optString("message", payload);
            return new StreamFailure(code, message);
        }

        private void applyTypedEvent(String type, JSONObject event, JSONObject item, String key)
                throws Exception {
            JSONObject known = items.get(key);
            switch (type) {
                case "response.output_text.delta":
                    if (event.has("delta")) {
                        emitText(key, phaseOf(item, known), event.optString("delta", ""), false);
                    }
                    break;
                case "response.output_text.done":
                    if (event.has("text")) {
                        TextAccumulator accumulator = textAccumulator(key, phaseOf(item, known));
                        emitText(key, accumulator.phase,
                                snapshotDelta(event.optString("text", ""), accumulator.received()),
                                true);
                    }
                    break;
                case "response.reasoning_summary_text.delta":
                case "response.reasoning_text.delta":
                    if (event.has("delta")) appendThinking(event.optString("delta", ""));
                    break;
                case "response.reasoning_summary_text.done":
                case "response.reasoning_text.done":
                    if (event.has("text")) {
                        appendThinking(snapshotDelta(event.optString("text", ""), thinking.toString()));
                    }
                    break;
                case "response.function_call_arguments.delta": {
                    CallAccumulator accumulator = callAccumulator(key, item != null ? item : known, event);
                    String delta = event.optString("delta", "");
                    accumulator.appendArguments(delta, listener);
                    break;
                }
                case "response.function_call_arguments.done": {
                    CallAccumulator accumulator = callAccumulator(key, item != null ? item : known, event);
                    // 这里给的是完整参数，同样要走对账，否则已收的增量会被重复一遍。
                    String delta = snapshotDelta(event.optString("arguments", ""), accumulator.arguments());
                    accumulator.appendArguments(delta, listener);
                    break;
                }
                default:
                    // response.created / content_part.* / annotation.* 等没有任何需要保留的信息。
                    break;
            }
        }

        private void appendThinking(String delta) {
            if (delta == null || delta.isEmpty()) return;
            thinking.append(delta);
            if (listener != null) listener.onThinkingDelta(delta);
        }

        /**
         * 处理终局响应。
         *
         * <p>非流式响应也走这里（整包就是这个对象）。注意它<b>不</b>设置
         * {@code terminalEventSeen}：非流式本来就没有事件流，那面旗子只对流式有意义。
         */
        void applyTerminalResponse(JSONObject response) throws Exception {
            JSONArray output = response.optJSONArray("output");
            if (output != null) {
                for (int i = 0; i < output.length(); i++) {
                    JSONObject item = output.optJSONObject(i);
                    if (item == null) continue;
                    // 终局项没有事件包裹，伪造一个只带 output_index 的事件，
                    // 这样键归并与流式路径完全一致。
                    captureItem(new JSONObject().put("output_index", i).put("item", item), item, true);
                }
            } else {
                // 有些服务端把终局给出成一段纯文本而不是 output 数组。
                String text = response.optString("output_text", "");
                if (!text.isEmpty()) {
                    String key = keys.outputKeyOr(0, "message:0");
                    TextAccumulator accumulator = textAccumulator(key, null);
                    emitText(key, accumulator.phase,
                            snapshotDelta(text, accumulator.received()), true);
                }
            }

            JSONObject usage = response.optJSONObject("usage");
            if (usage != null) {
                turn.inputTokens = usage.optLong("input_tokens", turn.inputTokens);
                turn.outputTokens = usage.optLong("output_tokens", turn.outputTokens);
                if (listener != null) listener.onUsage(turn.inputTokens, turn.outputTokens);
            }
            JSONObject incomplete = response.optJSONObject("incomplete_details");
            if (incomplete != null && "max_output_tokens".equals(incomplete.optString("reason"))) {
                turn.stopReason = "max_tokens";
            }
        }

        private void captureItem(JSONObject event, JSONObject item, boolean terminal) throws Exception {
            String key = keys.keyFor(event, item, item.optString("type", "item"));
            items.put(key, merge(items.get(key), item));

            String type = item.optString("type", "");
            if ("function_call".equals(type) || "custom_tool_call".equals(type)) {
                CallAccumulator accumulator = callAccumulator(key, item, event);
                // 这两种调用的参数字段名不同：标准叫 arguments，custom 叫 input。
                String arguments = "function_call".equals(type)
                        ? item.optString("arguments", "")
                        : item.optString("input", "");
                if (!arguments.isEmpty()) {
                    accumulator.appendArguments(snapshotDelta(arguments, accumulator.arguments()), listener);
                }
                return;
            }
            if (!"message".equals(type)) return;

            JSONArray parts = item.optJSONArray("content");
            if (parts == null) return;
            for (JSONObject part : JsonItems.of(parts)) {
                if (!"output_text".equals(part.optString("type"))) continue;
                TextAccumulator accumulator = textAccumulator(key, item.optString("phase", null));
                emitText(key, accumulator.phase,
                        snapshotDelta(part.optString("text", ""), accumulator.received()), terminal);
            }
        }

        // -------------------------------------------------- 文本累积与去重

        /**
         * 累积一段输出文本，并按需要通知界面。
         *
         * <p>这里的 {@code duplicateCandidate} / {@code exactDuplicateSuppressed} 两个状态
         * 是为 Codex 那个分支准备的：它会把同一条最终答复先作为 {@code commentary}
         * 发一遍，再作为 {@code final_answer} 发一遍。
         *
         * <p>判定方式是「整段前缀匹配」而不是「看 phase 名」：因为重复发生时，
         * final_answer 的文本会<b>逐段</b>长成 commentary 的样子。
         * 在它还是 commentary 的前缀期间先不外发；一旦发现偏离，就把攒下的整段补发出去。
         * 只有到最后完全相等时，才认定这是同一条回答而整段抑制。
         */
        private void emitText(String key, String phase, String delta, boolean done) {
            TextAccumulator accumulator = textAccumulator(key, phase);
            if (delta != null && !delta.isEmpty()) accumulator.appendReceived(delta);

            if (accumulator.isWatchingForDuplicate() && accumulator.emittedLength() == 0) {
                String candidate = accumulator.duplicateCandidate;
                String received = accumulator.received();
                if (candidate.startsWith(received)) {
                    // 还在候选的前缀上：先不发，等看清它到底是不是同一条。
                    if (done && candidate.equals(received)) accumulator.markSuppressed();
                    return;
                }
                // 偏离了 —— 它不是重复，把攒下的补发出去。
                accumulator.clearDuplicateCandidate();
                accumulator.emit(received, listener);
            } else if (delta != null && !delta.isEmpty()) {
                accumulator.emit(delta, listener);
            }

            if (done && "commentary".equals(accumulator.phase) && !accumulator.received().isEmpty()) {
                lastCommentary = accumulator.received();
            }
        }

        private TextAccumulator textAccumulator(String key, String phase) {
            TextAccumulator accumulator = texts.get(key);
            if (accumulator == null) {
                accumulator = new TextAccumulator();
                accumulator.phase = phase;
                // 只有「紧接着 commentary 的 final_answer」才可能是重复，
                // 所以候选只在建这一项时取一次快照。
                if ("final_answer".equals(phase) && lastCommentary != null && !lastCommentary.isEmpty()) {
                    accumulator.duplicateCandidate = lastCommentary;
                }
                texts.put(key, accumulator);
                return accumulator;
            }
            if (accumulator.phase == null && phase != null) accumulator.phase = phase;
            return accumulator;
        }

        // -------------------------------------------------- 工具调用累积

        private CallAccumulator callAccumulator(String key, JSONObject item, JSONObject event) {
            CallAccumulator accumulator = calls.get(key);
            if (accumulator == null) {
                accumulator = new CallAccumulator(key);
                calls.put(key, accumulator);
            }
            // 三处来源按优先级补 id/name，且只补一次：后续事件里的空值不能把已有值抹掉。
            if (accumulator.callId == null && item != null) {
                accumulator.callId = nonBlankOrNull(item.optString("call_id", null));
                if (accumulator.callId == null) accumulator.callId = nonBlankOrNull(item.optString("id", null));
            }
            if (accumulator.name == null && item != null) {
                accumulator.name = nonBlankOrNull(item.optString("name", null));
            }
            if (accumulator.callId == null) {
                accumulator.callId = nonBlankOrNull(event.optString("call_id", null));
            }
            if (accumulator.callId == null) accumulator.callId = key;
            if (accumulator.name == null) accumulator.name = nonBlankOrNull(event.optString("name", null));
            if (accumulator.name == null) accumulator.name = UNKNOWN_TOOL;
            keys.registerCall(accumulator.callId, key);
            return accumulator;
        }

        // -------------------------------------------------- 收尾

        AssistantTurn toTurn() throws JSONException {
            for (TextAccumulator accumulator : texts.values()) {
                String text = accumulator.received();
                if (text.isEmpty()) continue;
                if (accumulator.shouldDropFromContent()) continue;
                turn.content.put(new JSONObject().put("type", "text").put("text", text));
            }
            if (thinking.length() > 0) {
                turn.content.put(new JSONObject().put("type", "thinking").put("thinking", thinking.toString()));
            }
            for (CallAccumulator accumulator : calls.values()) {
                // 名字都没认出来的调用整条丢弃：发出去会让服务端找不到对应工具，
                // 而它在下一轮里也只是一个会导致请求被拒的悬挂项。
                if (accumulator.name == null || UNKNOWN_TOOL.equals(accumulator.name)) continue;
                JSONObject input = parseToolArguments(accumulator.arguments());
                String id = accumulator.callId == null ? accumulator.key : accumulator.callId;
                turn.content.put(new JSONObject()
                        .put("type", "tool_use").put("id", id)
                        .put("name", accumulator.name).put("input", input));
                turn.toolCalls.add(new ToolCall(id, accumulator.name, input));
            }
            if (!turn.toolCalls.isEmpty() && turn.stopReason == null) turn.stopReason = "tool_use";
            if (turn.stopReason == null) turn.stopReason = turn.toolCalls.isEmpty() ? "end_turn" : "tool_use";
            return turn;
        }

        private static JSONObject merge(JSONObject previous, JSONObject update) throws JSONException {
            JSONObject out = previous == null ? new JSONObject() : new JSONObject(previous.toString());
            if (update == null) return out;
            Iterator<String> names = update.keys();
            while (names.hasNext()) {
                String name = names.next();
                Object value = update.opt(name);
                // 只覆盖非 null 字段：事件里带 null 表示「这一项这次没给」，
                // 而不是「把它清空」。
                if (value != null) out.put(name, value);
            }
            return out;
        }

        private static String phaseOf(JSONObject primary, JSONObject fallback) {
            if (primary != null) {
                String phase = nonBlankOrNull(primary.optString("phase", null));
                if (phase != null) return phase;
            }
            return fallback == null ? null : nonBlankOrNull(fallback.optString("phase", null));
        }
    }

    /**
     * 把事件里的各种身份字段归并成同一个键。
     *
     * <p>这是这个协议最难缠的地方：同一项在不同事件里可能只给
     * {@code output_index}、或只给 {@code id}、或只给 {@code call_id}。
     * 三个映射表互相登记，任何一条线索命中就复用已有的键。
     */
    static final class KeyIndex {

        private final Map<Integer, String> byOutputIndex = new LinkedHashMap<>();
        private final Map<String, String> byItemId = new LinkedHashMap<>();
        private final Map<String, String> byCallId = new LinkedHashMap<>();
        private int generated;

        String keyFor(JSONObject event, JSONObject item, String prefix) {
            String itemId = item == null ? null : nonBlankOrNull(item.optString("id", null));
            String callId = item == null ? null : nonBlankOrNull(item.optString("call_id", null));
            if (callId == null) callId = nonBlankOrNull(event.optString("call_id", null));
            int outputIndex = event.has("output_index") ? event.optInt("output_index", -1) : -1;

            // 已有线索：直接复用，并把新线索登记到同一个键上。
            if (callId != null && byCallId.containsKey(callId)) return byCallId.get(callId);
            if (itemId != null && byItemId.containsKey(itemId)) return byItemId.get(itemId);
            if (outputIndex >= 0 && byOutputIndex.containsKey(outputIndex)) {
                String existing = byOutputIndex.get(outputIndex);
                if (itemId != null) byItemId.put(itemId, existing);
                if (callId != null) byCallId.put(callId, existing);
                return existing;
            }

            String key;
            if (itemId != null) key = itemId;
            else if (callId != null) key = callId;
            else if (outputIndex >= 0) key = prefix + ":" + outputIndex;
            else key = prefix + ":generated:" + (++generated);

            if (itemId != null) byItemId.put(itemId, key);
            if (callId != null) byCallId.put(callId, key);
            if (outputIndex >= 0) byOutputIndex.put(outputIndex, key);
            return key;
        }

        void registerCall(String callId, String key) {
            if (callId != null) byCallId.put(callId, key);
        }

        String outputKeyOr(int outputIndex, String fallback) {
            String existing = byOutputIndex.get(outputIndex);
            return existing == null ? fallback : existing;
        }
    }

    /** 一段输出文本的累积器，含 Codex 重复回答的抑制状态。 */
    static final class TextAccumulator {

        String phase;
        /** 待确认的重复候选（上一条 commentary）。 */
        String duplicateCandidate;
        /** 已确认「就是同一条回答」——收尾时不写进 content。 */
        boolean exactDuplicateSuppressed;

        private final StringBuilder received = new StringBuilder();
        private final StringBuilder emitted = new StringBuilder();

        boolean isWatchingForDuplicate() {
            return duplicateCandidate != null;
        }

        void clearDuplicateCandidate() {
            duplicateCandidate = null;
        }

        void markSuppressed() {
            exactDuplicateSuppressed = true;
        }

        void appendReceived(String delta) {
            received.append(delta);
        }

        String received() {
            return received.toString();
        }

        int emittedLength() {
            return emitted.length();
        }

        /** 通知界面并把这段计入已发。调用方保证 delta 非空。 */
        void emit(String delta, StreamListener listener) {
            if (listener != null) listener.onTextDelta(delta);
            emitted.append(delta);
        }

        /** 收尾时这段文本是否不该进 content。 */
        boolean shouldDropFromContent() {
            if (exactDuplicateSuppressed) return true;
            return duplicateCandidate != null && duplicateCandidate.equals(received());
        }
    }

    /** 一个工具调用的累积器。 */
    static final class CallAccumulator {

        final String key;
        String callId;
        String name;
        private final StringBuilder arguments = new StringBuilder();

        CallAccumulator(String key) {
            this.key = key;
        }

        String arguments() {
            return arguments.toString();
        }

        void appendArguments(String delta, StreamListener listener) {
            if (delta != null && !delta.isEmpty()) {
                arguments.append(delta);
                if (listener != null) listener.onToolInputDelta(callId, name, delta);
            }
        }
    }

    // ------------------------------------------------------------ 小工具

    /**
     * 快照与已发内容的差值。
     *
     * <p>语义（四个分支，顺序不能换）：
     * <ol>
     *   <li>快照以已发内容开头 → 返回剩下的部分（最常见）；</li>
     *   <li>已发内容以快照结尾 → 这次快照没有新东西，返回空串；</li>
     *   <li>否则找最大的重叠后缀，只补重叠之后的部分；</li>
     *   <li>完全不相关 → 整段返回（宁可重复也不能丢内容）。</li>
     * </ol>
     * 第 1 与第 3 分支在「快照是已发内容的后缀」这类输入上会给出不同答案，
     * 所以顺序本身是契约的一部分。
     */
    static String snapshotDelta(String snapshot, String emitted) {
        if (snapshot == null || snapshot.isEmpty()) return "";
        String already = emitted == null ? "" : emitted;
        if (snapshot.startsWith(already)) return snapshot.substring(already.length());
        if (already.endsWith(snapshot)) return "";
        int max = Math.min(snapshot.length(), already.length());
        for (int overlap = max; overlap > 0; overlap--) {
            if (already.endsWith(snapshot.substring(0, overlap))) return snapshot.substring(overlap);
        }
        return snapshot;
    }

    /**
     * 解工具入参。
     *
     * <p>解不开时把原文放进 {@code _raw_invalid_json}：Agent 能在工具结果里
     * 看到坏掉的原文，从而判断是模型发坏了还是自己拼错了。
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

    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) out.append(line).append('\n');
        }
        return out.toString();
    }

    /** 注意这里**不加**省略标记：与另两家不同，这是既有行为。 */
    private static String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static boolean nonBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String nonBlankOrNull(String value) {
        return nonBlank(value) ? value : null;
    }
}
