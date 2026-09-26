package com.termux.app.zhicode.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.termux.app.zhicode.model.AssistantTurn;
import com.termux.app.zhicode.model.SessionConfig;
import com.termux.app.zhicode.model.ToolCall;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 三个协议实现的**流式解析**行为测试：真的走一次 HTTP 往返。
 *
 * <h3>为什么要这么测</h3>
 * 这里是批 B 重写的对象，而它此前几乎零覆盖。文本断言能证明「代码里提到了这个事件类型」，
 * 但证明不了「喂进一段真实 SSE 后能解析出正确的内容与工具调用」——
 * 而重写解析器时的典型错误恰恰是后者：拼接顺序错了、
 * 快照去重把内容吃掉了、工具参数少拼了一段、usage 从错的地方读。
 *
 * <p>做法：起一个本地回环上的最小 HTTP 服务，回放一段手工构造的 SSE，
 * 把 {@code baseUrl} 指向它，然后调真实的 {@code createMessage}。
 * 不需要设备、不需要网络、不需要模拟器 —— api/ 包没有任何 {@code android.*} 依赖。
 *
 * <p><b>为什么自己写 socket 服务而不用 {@code com.sun.net.httpserver}</b>：
 * 后者虽然属于 JDK，但 Android 单测的编译类路径走的是 android.jar，
 * 那个包不在里面（编译期就报 package does not exist）。
 * 自己处理一次请求-响应只需几十行，而且行为完全可预测 —— 对测试来说这是优点。
 *
 * <h3>这些用例本身就是契约</h3>
 * 每条断言旁边的注释写的是「为什么必须是这个值」，不是「代码现在返回什么」。
 * 重写后如果某条挂了，先判断是契约变了还是实现错了，不要直接改断言。
 */
public class ApiSseBehaviourTest {

    private final List<MiniServer> servers = new ArrayList<>();

    @After
    public void stopServers() {
        for (MiniServer server : servers) server.close();
        servers.clear();
    }

    /**
     * 回环上的最小 HTTP 服务：每个请求回一段固定响应，并记下收到的请求体。
     *
     * <p>刻意用 {@code Connection: close} 并每请求一个连接：省掉 keep-alive 与
     * 分块编码的处理，让「发的什么、回的什么」都一目了然。
     */
    private static final class MiniServer implements AutoCloseable {
        private final ServerSocket socket;
        private final int status;
        private final String contentType;
        private final String body;
        private final AtomicReference<String> lastRequestBody = new AtomicReference<>("");
        private volatile boolean running = true;

        MiniServer(int status, String contentType, String body) throws IOException {
            this.status = status;
            this.contentType = contentType;
            this.body = body;
            this.socket = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
            Thread thread = new Thread(this::loop, "mini-http");
            thread.setDaemon(true);
            thread.start();
        }

        String baseUrl() {
            return "http://127.0.0.1:" + socket.getLocalPort();
        }

        String lastRequestBody() {
            return lastRequestBody.get();
        }

        private void loop() {
            while (running) {
                try (Socket connection = socket.accept()) {
                    connection.setSoTimeout(10_000);
                    InputStream in = connection.getInputStream();
                    ByteArrayOutputStream head = new ByteArrayOutputStream();
                    // 读到请求头结束（连续的 \r\n\r\n）为止，用一个小状态机跟踪已匹配到第几个字符。
                    int state = 0;
                    int b;
                    while (state < 4 && (b = in.read()) != -1) {
                        head.write(b);
                        state = advance(state, b);
                    }
                    String headers = head.toString("UTF-8");
                    int length = contentLength(headers);
                    ByteArrayOutputStream requestBody = new ByteArrayOutputStream();
                    for (int i = 0; i < length; i++) {
                        int next = in.read();
                        if (next == -1) break;
                        requestBody.write(next);
                    }
                    lastRequestBody.set(requestBody.toString("UTF-8"));

                    byte[] payload = body.getBytes(StandardCharsets.UTF_8);
                    String response = "HTTP/1.1 " + status + " \r\n"
                            + "Content-Type: " + contentType + "\r\n"
                            + "Content-Length: " + payload.length + "\r\n"
                            + "Connection: close\r\n\r\n";
                    OutputStream out = connection.getOutputStream();
                    out.write(response.getBytes(StandardCharsets.UTF_8));
                    out.write(payload);
                    out.flush();
                } catch (IOException ignored) {
                    // 关闭时的 accept/read 异常是正常路径。
                }
            }
        }

        /** 已匹配 {@code state} 个字符的 {@code \r\n\r\n} 前缀，再读到一个字节 {@code b} 之后的状态。 */
        private static int advance(int state, int b) {
            if (state == 0 && b == '\r') return 1;
            if (state == 1 && b == '\n') return 2;
            if (state == 2 && b == '\r') return 3;
            if (state == 3 && b == '\n') return 4;
            // 不匹配：但一个孤立的 \r 可能是下一段的前缀开头，所以不能一律归零。
            return b == '\r' ? 1 : 0;
        }

        private static int contentLength(String headers) {
            for (String line : headers.split("\r\n")) {
                int colon = line.indexOf(':');
                if (colon < 0) continue;
                if (!line.substring(0, colon).trim().equalsIgnoreCase("Content-Length")) continue;
                try {
                    return Integer.parseInt(line.substring(colon + 1).trim());
                } catch (NumberFormatException ignored) {
                    return 0;
                }
            }
            return 0;
        }

        @Override
        public void close() {
            running = false;
            try {
                socket.close();
            } catch (IOException ignored) {
                // 已经关了。
            }
        }
    }

    /** 起一个只回一段固定 SSE 的服务，返回可用的 baseUrl。 */
    private String serve(String contentType, String body) throws Exception {
        MiniServer server = new MiniServer(200, contentType, body);
        servers.add(server);
        return server.baseUrl();
    }

    private static SessionConfig config(String protocol, String baseUrl) {
        SessionConfig config = new SessionConfig();
        config.protocol = protocol;
        config.baseUrl = baseUrl;
        config.apiKey = "test-key";
        config.model = "test-model";
        return config;
    }

    /** 收集全部流式回调，便于断言「内容既进了最终结果、也逐段通知了界面」。 */
    private static final class Recorder implements ModelProvider.StreamListener {
        final StringBuilder text = new StringBuilder();
        final StringBuilder thinking = new StringBuilder();
        final List<String> toolDeltas = new ArrayList<>();
        long inputTokens = -1;
        long outputTokens = -1;

        @Override public void onTextDelta(String delta) { text.append(delta); }
        @Override public void onThinkingDelta(String delta) { thinking.append(delta); }
        @Override public void onToolInputDelta(String id, String name, String partial) {
            toolDeltas.add((id == null ? "?" : id) + "/" + (name == null ? "?" : name) + ":" + partial);
        }
        @Override public void onUsage(long input, long output) {
            // 引擎会在多个事件里多次收到 usage，最后一次为准（后面的更完整）。
            inputTokens = input;
            outputTokens = output;
        }
    }

    // ============================================================ Anthropic

    @Test
    public void anthropic_parsesTextToolCallUsageAndTerminator() throws Exception {
        String sse =
            "event: message_start\n"
          + "data: {\"type\":\"message_start\",\"message\":{\"usage\":{\"input_tokens\":10,"
          + "\"cache_creation_input_tokens\":2,\"cache_read_input_tokens\":3}}}\n"
          + "\n"
          + "event: content_block_start\n"
          + "data: {\"type\":\"content_block_start\",\"index\":0,"
          + "\"content_block\":{\"type\":\"text\",\"text\":\"\"}}\n"
          + "\n"
          + "event: content_block_delta\n"
          + "data: {\"type\":\"content_block_delta\",\"index\":0,"
          + "\"delta\":{\"type\":\"text_delta\",\"text\":\"Hello\"}}\n"
          + "\n"
          + "event: content_block_start\n"
          + "data: {\"type\":\"content_block_start\",\"index\":1,"
          + "\"content_block\":{\"type\":\"tool_use\",\"id\":\"toolu_1\",\"name\":\"Read\",\"input\":{}}}\n"
          + "\n"
          + "event: content_block_delta\n"
          + "data: {\"type\":\"content_block_delta\",\"index\":1,"
          + "\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"path\\\":\"}}\n"
          + "\n"
          + "event: content_block_delta\n"
          + "data: {\"type\":\"content_block_delta\",\"index\":1,"
          + "\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"\\\"a.txt\\\"}\"}}\n"
          + "\n"
          + "event: message_delta\n"
          + "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"tool_use\"},"
          + "\"usage\":{\"output_tokens\":7}}\n"
          + "\n"
          + "event: message_stop\n"
          + "data: {\"type\":\"message_stop\"}\n";

        String baseUrl = serve("text/event-stream", sse);
        Recorder recorder = new Recorder();
        AssistantTurn turn = new AnthropicMessagesProvider()
                .createMessage(config("anthropic", baseUrl), "sys", new JSONArray(), new JSONArray(), recorder);

        assertEquals("两段 content_block_delta 必须拼成完整文本", "Hello", recorder.text.toString());
        assertEquals("最终 content 里要有一个 text 块", "Hello", turn.content.getJSONObject(0).optString("text"));
        assertEquals("工具调用必须被组装出来", 1, turn.toolCalls.size());
        ToolCall call = turn.toolCalls.get(0);
        assertEquals("toolu_1", call.id);
        assertEquals("Read", call.name);
        assertEquals("两段 partial_json 必须拼成完整的 JSON 入参；"
                + "少拼一段或顺序反了都会让工具收到坏参数", "a.txt", call.input.optString("path"));
        assertEquals("input token 必须是三个字段之和：它们都占用上下文，只取一个会低估用量",
                15L, turn.inputTokens);
        assertEquals(7L, turn.outputTokens);
        assertEquals("tool_use", turn.stopReason);
        assertEquals("工具参数增量必须逐段通知界面（虽然引擎当前忽略它，但这是接口契约）",
                2, recorder.toolDeltas.size());
    }

    // ============================================================ Chat Completions

    @Test
    public void chat_parsesTextThinkingToolCallUsageAndDone() throws Exception {
        String sse =
            "data: {\"choices\":[{\"delta\":{\"content\":\"Hi\"}}]}\n"
          + "\n"
          + "data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"think\"}}]}\n"
          + "\n"
          + "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_1\","
          + "\"function\":{\"name\":\"Read\",\"arguments\":\"{\\\"pa\"}}]}}]}\n"
          + "\n"
          + "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,"
          + "\"function\":{\"arguments\":\"th\\\":\\\"b\\\"}\"}}]}}]}\n"
          + "\n"
          + "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"tool_calls\"}],"
          + "\"usage\":{\"prompt_tokens\":5,\"completion_tokens\":6}}\n"
          + "\n"
          + "data: [DONE]\n";

        String baseUrl = serve("text/event-stream", sse);
        Recorder recorder = new Recorder();
        AssistantTurn turn = new OpenAIChatCompletionsProvider()
                .createMessage(config("openai-chat", baseUrl), "sys", new JSONArray(), new JSONArray(), recorder);

        assertEquals("Hi", recorder.text.toString());
        assertEquals("think", recorder.thinking.toString());
        assertEquals(1, turn.toolCalls.size());
        ToolCall call = turn.toolCalls.get(0);
        assertEquals("id 只在第一个分片里出现，后续分片必须沿用它", "call_1", call.id);
        assertEquals("name 同上，必须沿用", "Read", call.name);
        assertEquals("两个分片的 arguments 必须按顺序拼接", "b", call.input.optString("path"));
        assertEquals(5L, turn.inputTokens);
        assertEquals(6L, turn.outputTokens);
        assertEquals("finish_reason=tool_calls 必须映射成 tool_use", "tool_use", turn.stopReason);
        // 推理内容与正文都进 content，且 thinking 在前（与 Chat 协议的语义一致）。
        assertTrue("thinking 块要在 content 里", turn.content.toString().contains("think"));
    }

    @Test
    public void chat_mapsFinishReasonLengthToMaxTokens() throws Exception {
        // 这个映射决定引擎是否会续跑被截断的回答，错了就表现为「回答突然断了」。
        String sse = "data: {\"choices\":[{\"delta\":{\"content\":\"x\"},\"finish_reason\":\"length\"}]}\n"
                   + "\n"
                   + "data: [DONE]\n";
        String baseUrl = serve("text/event-stream", sse);
        AssistantTurn turn = new OpenAIChatCompletionsProvider()
                .createMessage(config("openai-chat", baseUrl), "sys", new JSONArray(), new JSONArray(), new Recorder());
        assertEquals("max_tokens", turn.stopReason);
    }

    @Test
    public void chat_reportsMissingTerminatorAsRetryableStreamError() throws Exception {
        // 流提前断掉必须报出来。引擎据此触发一次重试，而它判定的依据是
        // 错误消息里含有 stream_read_error 这个子串 —— 所以这个措辞是契约。
        String sse = "data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}\n";
        String baseUrl = serve("text/event-stream", sse);
        try {
            new OpenAIChatCompletionsProvider()
                    .createMessage(config("openai-chat", baseUrl), "sys", new JSONArray(), new JSONArray(),
                            new Recorder());
            fail("缺少 [DONE] 时必须报错，否则用户会拿到一段被静默截断的回答");
        } catch (Exception expected) {
            assertTrue("错误消息必须带 stream_read_error（引擎按子串判定可重试）: " + expected.getMessage(),
                    String.valueOf(expected.getMessage()).contains("stream_read_error"));
        }
    }

    // ============================================================ Responses

    @Test
    public void responses_parsesDeltaSnapshotToolCallUsageAndCompleted() throws Exception {
        String sse =
            "event: response.output_item.added\n"
          + "data: {\"type\":\"response.output_item.added\",\"output_index\":0,"
          + "\"item\":{\"id\":\"item_1\",\"type\":\"message\",\"role\":\"assistant\","
          + "\"content\":[],\"phase\":\"final_answer\"}}\n"
          + "\n"
          + "event: response.output_text.delta\n"
          + "data: {\"type\":\"response.output_text.delta\",\"output_index\":0,\"delta\":\"Hey\"}\n"
          + "\n"
          + "event: response.output_text.done\n"
          + "data: {\"type\":\"response.output_text.done\",\"output_index\":0,\"text\":\"Hey there\"}\n"
          + "\n"
          + "event: response.output_item.done\n"
          + "data: {\"type\":\"response.output_item.done\",\"output_index\":1,"
          + "\"item\":{\"id\":\"item_2\",\"type\":\"function_call\",\"call_id\":\"call_x\","
          + "\"name\":\"Bash\",\"arguments\":\"{\\\"command\\\":\\\"ls\\\"}\"}}\n"
          + "\n"
          + "event: response.completed\n"
          + "data: {\"type\":\"response.completed\",\"response\":{\"output\":[],"
          + "\"usage\":{\"input_tokens\":11,\"output_tokens\":22}}}\n"
          + "\n";

        String baseUrl = serve("text/event-stream", sse);
        Recorder recorder = new Recorder();
        AssistantTurn turn = new OpenAIResponsesProvider()
                .createMessage(config("openai-responses", baseUrl), "sys", new JSONArray(), new JSONArray(), recorder);

        // 这是整个文件里最值钱的一条断言：delta 只发了 "Hey"，done 发的是全文 "Hey there"。
        // 直接用 done 的文本覆盖会让界面上的正文跳一下；直接追加会让 "Hey" 出现两次。
        // 正确行为是「补上差值」，所以逐段回调与最终结果都必须是 "Hey there"。
        assertEquals("快照去重必须只补差值，既不能重复也不能覆盖",
                "Hey there", recorder.text.toString());
        assertTrue("最终 content 里要有完整正文", turn.content.toString().contains("Hey there"));

        assertEquals(1, turn.toolCalls.size());
        ToolCall call = turn.toolCalls.get(0);
        assertEquals("call_id 必须原样保留：它是与 function_call_output 配对的唯一凭据，"
                + "拼错会导致下一轮请求被拒（no tool output found for function call）",
                "call_x", call.id);
        assertEquals("Bash", call.name);
        assertEquals("ls", call.input.optString("command"));

        assertEquals("usage 只在终局响应里给出", 11L, turn.inputTokens);
        assertEquals(22L, turn.outputTokens);
        // 这一轮里捕获到了一个工具调用，所以 stopReason 是 tool_use 而不是 end_turn：
        // 二者不能同时成立（引擎按它决定是「继续跑工具」还是「这一轮结束」），
        // 有工具时给 end_turn 会让引擎直接结束回合、工具永远不执行。
        assertEquals("tool_use", turn.stopReason);
    }

    @Test
    public void responses_reportsMissingTerminatorAsRetryableStreamError() throws Exception {
        String sse = "event: response.output_text.delta\n"
                   + "data: {\"type\":\"response.output_text.delta\",\"output_index\":0,\"delta\":\"x\"}\n"
                   + "\n";
        String baseUrl = serve("text/event-stream", sse);
        try {
            new OpenAIResponsesProvider()
                    .createMessage(config("openai-responses", baseUrl), "sys", new JSONArray(), new JSONArray(),
                            new Recorder());
            fail("缺少 response.completed 时必须报错");
        } catch (ModelProvider.StreamFailure failure) {
            // 断言的是**结构化**的失败码，不是消息文本 —— 而这里藏着一个真实的差异：
            // Responses 用 StreamFailure.code 表达「可重试」；另外两家
            // （Anthropic / Chat）抛的是 IllegalStateException，把 "stream_read_error"
            // 写在消息里，引擎只能靠文本匹配。
            // 后者更脆（文案改一个字就静默失去重试），但那是既有契约，
            // 顺手统一它属于行为变更，不在重写范围内。
            // 若重写 Responses 时把它也改成抛 IllegalStateException，
            // 这条断言会失败，而引擎的重试会静默失效 —— 正是要拦住的那种改动。
            assertEquals("stream_read_error", failure.code);
        }
    }

    @Test
    public void responses_incompleteWithMaxOutputTokensMapsToMaxTokens() throws Exception {
        // 这个映射决定引擎是否续跑；错了表现为「回答被截断且不续」。
        String sse = "event: response.incomplete\n"
                   + "data: {\"type\":\"response.incomplete\",\"response\":{\"output\":[],"
                   + "\"incomplete_details\":{\"reason\":\"max_output_tokens\"},"
                   + "\"usage\":{\"input_tokens\":1,\"output_tokens\":2}}}\n"
                   + "\n";
        String baseUrl = serve("text/event-stream", sse);
        AssistantTurn turn = new OpenAIResponsesProvider()
                .createMessage(config("openai-responses", baseUrl), "sys", new JSONArray(), new JSONArray(),
                        new Recorder());
        assertEquals("max_tokens", turn.stopReason);
    }

    // ============================================================ 错误处理

    @Test
    public void nonSuccessStatus_keepsTheStatusInTheMessage() throws Exception {
        // 引擎靠消息里的 "http 408/502/503/504" 子串判定可重试，
        // 所以状态码必须出现在消息里，且形如 "HTTP 503"。
        MiniServer server = new MiniServer(503, "application/json",
                "{\"error\":{\"message\":\"upstream unavailable\"}}");
        servers.add(server);

        try {
            new OpenAIChatCompletionsProvider()
                    .createMessage(config("openai-chat", server.baseUrl()), "sys", new JSONArray(), new JSONArray(),
                            new Recorder());
            fail("非 2xx 必须报错");
        } catch (Exception expected) {
            String message = String.valueOf(expected.getMessage());
            assertTrue("消息里必须有 HTTP 503（引擎据此判定可重试）: " + message, message.contains("HTTP 503"));
            assertTrue("错误体要带上便于排查: " + message, message.contains("upstream unavailable"));
        }
    }

    @Test
    public void requestBodyCarriesTheContractFieldsForEachProtocol() throws Exception {
        // 请求体字段少一个通常不会报错，只会让服务端行为不同（例如丢掉工具定义、
        // 丢掉推理档位、把流式当成非流式），所以必须逐字段钉住。
        for (String protocol : new String[]{"anthropic", "openai-chat", "openai-responses"}) {
            // 回一段最小可用的流，让调用能正常结束。
            String stream = "anthropic".equals(protocol)
                    ? "event: message_stop\ndata: {\"type\":\"message_stop\"}\n\n"
                    : "openai-chat".equals(protocol)
                        ? "data: [DONE]\n\n"
                        : "event: response.completed\n"
                          + "data: {\"type\":\"response.completed\",\"response\":{\"output\":[]}}\n\n";
            MiniServer server = new MiniServer(200, "text/event-stream", stream);
            servers.add(server);

            SessionConfig config = config(protocol, server.baseUrl());
            config.effort = "high";
            config.maxTokens = 4096;
            JSONArray tools = new JSONArray().put(new JSONObject()
                    .put("name", "Read")
                    .put("description", "read a file")
                    .put("input_schema", new JSONObject().put("type", "object")));
            JSONArray messages = new JSONArray().put(new JSONObject()
                    .put("role", "user")
                    .put("content", new JSONArray().put(new JSONObject().put("type", "text").put("text", "hi"))));

            ModelProviders.forConfig(config)
                    .createMessage(config, "sys", messages, tools, new Recorder());

            JSONObject body = new JSONObject(server.lastRequestBody());
            assertEquals(protocol + " 必须带上 model", "test-model", body.optString("model"));
            assertTrue(protocol + " 必须开流式", body.optBoolean("stream", false));

            if ("anthropic".equals(protocol)) {
                assertEquals("Anthropic 的 system 是顶层字段", "sys", body.optString("system"));
                assertTrue("Anthropic 必须有 max_tokens", body.has("max_tokens"));
                assertEquals("工具必须原样带上 input_schema",
                        "object", body.getJSONArray("tools").getJSONObject(0)
                                .getJSONObject("input_schema").optString("type"));
                assertEquals("effort 要映射到 output_config.effort",
                        "high", body.getJSONObject("output_config").optString("effort"));
            } else if ("openai-chat".equals(protocol)) {
                JSONArray sent = body.getJSONArray("messages");
                assertEquals("Chat 的 system 是 messages 的第一条", "system",
                        sent.getJSONObject(0).optString("role"));
                assertEquals("工具必须包成 function 形状", "function",
                        body.getJSONArray("tools").getJSONObject(0).optString("type"));
                assertTrue("有工具时必须发 tool_choice", body.has("tool_choice"));
                assertEquals("effort 走 reasoning_effort", "high", body.optString("reasoning_effort"));
                assertEquals("maxTokens 大于 0 时才发", 4096, body.optInt("max_tokens"));
            } else {
                assertEquals("Responses 的 system 叫 instructions", "sys", body.optString("instructions"));
                assertTrue("Responses 必须有 input", body.has("input"));
                assertFalse("Responses 固定 store=false（不把对话留在服务端）",
                        body.optBoolean("store", true));
                assertTrue("Responses 的 tools 项是扁平形状（name 在顶层）",
                        body.getJSONArray("tools").getJSONObject(0).has("name"));
                assertTrue("有工具时必须开并行调用", body.optBoolean("parallel_tool_calls", false));
                assertEquals("effort 走 reasoning.effort", "high",
                        body.getJSONObject("reasoning").optString("effort"));
                assertEquals("非 codex 才发 max_output_tokens", 4096, body.optInt("max_output_tokens"));
            }
        }
    }
}
