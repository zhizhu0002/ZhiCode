import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 三个协议实现的线协议契约（wire contract）。
 *
 * <p>为什么需要它：这一层要被重写，而它几乎没有任何测试保护 —— 现有 20 个结构测试里
 * 只有两行碰过它（图片帧的 type 字段）。重写一个流式解析器时最容易犯的错不是编译不过，
 * 而是**静默漏掉一个分支**：少认一个事件类型、少读一个字段、终止标记判断反了。
 * 这些错误在联调时只表现为「模型回复不完整」或「工具调用丢参数」，很难定位。
 *
 * <p>所以这里把「重写时不能变的东西」逐条钉住：
 * <ul>
 *   <li>端点如何拼接（谁补 /v1、谁不补）；</li>
 *   <li>请求头名字与鉴权头（Anthropic 用 x-api-key，两家 OpenAI 用 Bearer）；</li>
 *   <li>三种流各自的终止标记，以及缺少终止标记时的报错形态；</li>
 *   <li>SSE 事件切分方式（Responses 以空行为事件边界，另两家逐行解析）；</li>
 *   <li>usage 从哪里读；</li>
 *   <li>工具调用的 id/name 兜底与非法 JSON 的占位符；</li>
 *   <li>{@code ReasoningMapper} 的档位映射表（含那两处刻意的不对称）。</li>
 * </ul>
 *
 * <p><b>它证明不了什么</b>：这是文本级断言，只能防「漏掉一个分支」，
 * 不能证明运行时行为正确。真正的行为验证需要真机跑一轮对话。
 */
public final class ApiWireContractTest {

    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static String read(Path root, String relative) throws Exception {
        Path file = root.resolve(relative);
        require(Files.isRegularFile(file), "缺少文件: " + relative);
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    public static void main(String[] args) throws Exception {
        Path root = Paths.get(args.length == 0 ? "." : args[0]).toAbsolutePath().normalize();
        String dir = "app/src/main/java/com/termux/app/zhicode/api/";
        String anthropic = read(root, dir + "AnthropicMessagesProvider.java");
        String chat = read(root, dir + "OpenAIChatCompletionsProvider.java");
        String responses = read(root, dir + "OpenAIResponsesProvider.java");
        String tracker = read(root, dir + "HttpRequestTracker.java");
        String resolver = read(root, dir + "ApiEndpointResolver.java");
        String urlPolicy = read(root, dir + "ApiUrlPolicy.java");
        String providers = read(root, dir + "ModelProviders.java");
        String iface = read(root, dir + "ModelProvider.java");
        String reasoning = read(root, dir + "compat/ReasoningMapper.java");

        // 读到的内容必须像样，否则下面「没找到坏东西」的结论毫无意义。
        for (String[] pair : new String[][]{
                {"AnthropicMessagesProvider", anthropic}, {"OpenAIChatCompletionsProvider", chat},
                {"OpenAIResponsesProvider", responses}, {"ReasoningMapper", reasoning}}) {
            require(pair[1].length() > 2000, pair[0] + " 读到的内容过短，读路径可能写错了");
        }

        // ---------------------------------------------------------- 端点拼接
        // 三家规则不同，且这个差异是有意的：Responses 有个 codex 变体不要 /v1 前缀。
        require(anthropic.contains("\"/v1/messages\""), "Anthropic 端点必须是 base + /v1/messages");
        require(responses.contains("\"/v1/responses\"") && responses.contains("\"/responses\""),
                "Responses 端点必须区分标准（/v1/responses）与 codex（/responses）");
        require(responses.contains("\"codex-responses\".equals(config.protocol)"),
                "codex 变体必须按协议名判定");
        // Chat 侧必须自己去掉重复的 /v1 与 /chat/completions，否则会拼出 /v1/v1/... 的 404。
        require(chat.contains("/chat/completions") && chat.contains("\"/v1\""),
                "Chat 端点必须能识别已带 /v1 或 /chat/completions 的 base，避免拼重");
        // 模型目录端点：三种协议统一走 /v1/models，且要避免 /v1/v1/models。
        require(resolver.contains("modelCatalogEndpoint") && resolver.contains("\"v1\"")
                        && resolver.contains("\"models\""),
                "ApiEndpointResolver 必须提供 /v1/models 且避免版本段重复");
        // URL 校验只校验、不改写：不能出现任何「补默认域名」的行为。
        require(urlPolicy.contains("requireBaseUrl") && urlPolicy.contains("https://")
                        && urlPolicy.contains("http://"),
                "ApiUrlPolicy 必须校验协议头");
        require(!urlPolicy.contains("ginka") && !urlPolicy.contains("api."),
                "ApiUrlPolicy 不得内置任何主机名");

        // ------------------------------------------------------------ 请求头
        require(anthropic.contains("\"x-api-key\"") && anthropic.contains("\"anthropic-version\""),
                "Anthropic 必须用 x-api-key 与 anthropic-version 两个头");
        require(anthropic.contains("2023-06-01"), "anthropic-version 的取值是契约的一部分");
        require(chat.contains("\"authorization\"") && responses.contains("\"authorization\""),
                "两家 OpenAI 协议必须用 authorization: Bearer");
        require(anthropic.contains("\"content-type\"") && anthropic.contains("\"accept\""),
                "三家都必须发 content-type 与 accept");
        // codex 变体靠这几个头自报身份，少一个都可能被服务端拒绝。
        for (String header : new String[]{"\"originator\"", "\"session-id\"", "\"thread-id\"", "\"x-client-request-id\""}) {
            require(responses.contains(header), "codex 变体必须发 " + header + " 头");
        }
        require(responses.contains("codex_cli_rs"), "codex 变体的 User-Agent 必须自称 codex_cli_rs");

        // ------------------------------------------------- 流终止标记与报错形态
        require(anthropic.contains("message_stop"), "Anthropic 流以 message_stop 终止");
        require(chat.contains("\"[DONE]\""), "Chat Completions 流以 [DONE] 终止");
        require(responses.contains("response.completed") && responses.contains("response.incomplete"),
                "Responses 流以 response.completed / response.incomplete 终止");
        // 缺少终止标记时必须报错，且必须带上 ZhiCodeEngine 用来判定重试的子串。
        require(anthropic.contains("stream_read_error") && chat.contains("stream_read_error")
                        && responses.contains("stream_read_error"),
                "三家缺少终止标记时都必须报 stream_read_error");
        // HTTP 非 2xx 的文案里必须保留 `HTTP <status>` 这一形式：引擎靠小写子串匹配
        // 判定 408/5xx 可重试，改掉这个形式会让重试静默失效。
        // 断言的是「HTTP " + status」这个拼接形态，而不是某一句完整文案 ——
        // 前缀（"ZhiCode API" / "OpenAI 兼容 API" / "Responses API"）可以改，这个不能。
        for (String[] pair : new String[][]{{"Anthropic", anthropic}, {"Chat", chat}, {"Responses", responses}}) {
            require(pair[1].contains("HTTP \" + status"),
                    pair[0] + " 的错误文案必须保留 `HTTP \" + status` 形式（引擎据此判定可重试）");
        }

        // ------------------------------------------------------ SSE 事件切分
        // Responses 以空行为事件边界（data 可跨多行），另两家是逐行一个 JSON。
        require(responses.contains("startsWith(\"event:\")") && responses.contains("startsWith(\"data:\")"),
                "Responses 必须同时解析 event: 与 data: 行");
        require(chat.contains("startsWith(\"data:\")"), "Chat 只按 data: 行解析");
        require(anthropic.contains("startsWith(\"event:\")") && anthropic.contains("startsWith(\"data:\")"),
                "Anthropic 需要 event: 行给出的类型名");

        // ---------------------------------------------------- Responses 事件集合
        // 这些 type 少认一个就是少一段内容或丢一次工具参数。
        for (String type : new String[]{
                "response.output_text.delta", "response.output_text.done",
                "response.reasoning_summary_text.delta", "response.reasoning_summary_text.done",
                "response.reasoning_text.delta", "response.reasoning_text.done",
                "response.function_call_arguments.delta", "response.function_call_arguments.done",
                "response.output_item.added", "response.output_item.done",
                "response.failed"}) {
            require(responses.contains(type), "Responses 必须处理事件 " + type);
        }
        // 快照事件必须去重后再追加：服务端会把已发的文本整段重发一遍。
        require(responses.contains("snapshotDelta"), "Responses 必须用 snapshotDelta 处理快照事件");
        // Chat 侧的 thinking 字段有三个别名，因为不同兼容网关叫法不同。
        for (String field : new String[]{"reasoning_content", "reasoning", "thinking"}) {
            require(chat.contains(field), "Chat 必须识别推理字段别名 " + field);
        }
        // Anthropic 的 input token 是三个字段之和，只取一个会低估上下文用量。
        for (String field : new String[]{"cache_creation_input_tokens", "cache_read_input_tokens"}) {
            require(anthropic.contains(field), "Anthropic 的 input token 必须含 " + field);
        }

        // -------------------------------------------- 工具调用兜底与非法 JSON
        for (String[] pair : new String[][]{{"Anthropic", anthropic}, {"Chat", chat}, {"Responses", responses}}) {
            require(pair[1].contains("_raw_invalid_json"),
                    pair[0] + " 必须把非法 JSON 的工具入参降级成 _raw_invalid_json");
        }
        require(chat.contains("unknown_tool") && responses.contains("unknown_tool"),
                "工具名缺失时两家 OpenAI 协议必须回落 unknown_tool");

        // ------------------------------------------------------------ 超时与取消
        require(tracker.contains("CONNECT_TIMEOUT_MS") && tracker.contains("READ_IDLE_TIMEOUT_MS"),
                "连接与读空闲超时必须集中在一处");
        for (String code : new String[]{"request_timeout", "unexpected_eof", "connection_reset"}) {
            require(tracker.contains(code), "HttpRequestTracker 必须产出失败码 " + code);
        }
        require(iface.contains("cancelRequest"), "ModelProvider 接口必须保留 cancelRequest");
        require(iface.contains("createMessage") && iface.contains("StreamListener"),
                "ModelProvider 接口的核心方法签名是契约，不得改动");

        // --------------------------------------------------------- 协议路由
        // 五个别名都要能路由。前三个用 ApiEndpointResolver 的常量而不是字面量
        // （那正是我们希望的写法，所以断言常量引用而不是字符串），
        // 后两个是只在路由层存在的别名，仍然内联。
        require(providers.contains("PROTOCOL_OPENAI_RESPONSES") && providers.contains("PROTOCOL_OPENAI_CHAT")
                        && providers.contains("PROTOCOL_ANTHROPIC"),
                "ModelProviders 必须通过 ApiEndpointResolver 的协议常量路由");
        require(providers.contains("\"codex-responses\"") && providers.contains("\"openai-compatible\""),
                "ModelProviders 必须认识 codex-responses 与 openai-compatible 两个别名");
        require(providers.contains("IllegalArgumentException"),
                "未知协议必须报错而不是回落到某个默认协议");

        // ------------------------------------------- ReasoningMapper 的档位映射
        require(reasoning.contains("\"minimal\"") && reasoning.contains("\"low\"")
                        && reasoning.contains("\"medium\"") && reasoning.contains("\"high\"")
                        && reasoning.contains("\"xhigh\""),
                "ReasoningMapper 必须定义全部档位名");
        // 两处刻意的不对称：Chat 把 max/ultra 折叠成 xhigh；
        // Responses 精确单发（不降级），只有 xxhigh 是本地别名。
        require(reasoning.contains("openAIChatEffort") && reasoning.contains("openAIResponsesWireEffort")
                        && reasoning.contains("openAIResponsesConfiguredEffort"),
                "ReasoningMapper 必须为 Responses 与 Chat 分别提供映射");
        require(reasoning.contains("anthropicEffort"), "ReasoningMapper 必须提供 Anthropic 映射");
        // 分界值是契约，但**写法不是**：Java 的数字字面量允许下划线分隔，
        // 2048 与 2_048 是同一个值。所以先把下划线去掉再检查 ——
        // 否则一次纯粹的排版改动就会被判成契约被改。
        // （真正的行为保证在 app/src/test 的 ApiPureLogicTest.numericBudget_mapsToBuckets，
        //   这条只是「源码里还看得见这些数字」的兜底。）
        String reasoningDigits = reasoning.replace("_", "");
        for (String boundary : new String[]{"2048", "4096", "8192", "16384"}) {
            require(reasoningDigits.contains(boundary),
                    "数字 token 预算的分界值 " + boundary + " 是契约，不得改动");
        }

        System.out.println("ApiWireContractTest PASS");
    }
}
