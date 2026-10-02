import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 「Codex Responses 在协议下拉里真的能选到」的守卫。
 *
 * <h2>为什么需要它</h2>
 *
 * 这是一个**已经实现过、却谁也点不到**的功能：引擎侧那条 Codex 分支（端点
 * {@code /responses}、UA {@code codex_cli_rs/<ver>}、四个关联头、{@code store:false}、
 * 不发 {@code max_output_tokens}）当时就已经写完并有单测，但协议被当作
 * {@code openai-responses} 的**别名**处理，于是 UI 只能产出 {@code openai-responses}
 * —— 用户在下拉里根本看不到 Codex，那条路径永远走不到。
 *
 * <p>这类漏法的特点是：**编译通过、测试全绿、功能不可达**。既有的守卫也抓不到 ——
 * {@code ApiWireContractTest} 检查的是实现文件里有没有那几个头，
 * 而"用户能不能选到"跨越了 UI 枚举 → 两个映射 → 引擎枚举 → 分派 这一整条链。
 *
 * <h2>钉住的退化</h2>
 * <ol>
 *   <li>UI 枚举里没有 Codex，或标签名不是参考图表单里的那个；</li>
 *   <li>UI→引擎的映射漏了 codex（选了 Codex 却发 {@code openai-responses}）；</li>
 *   <li>引擎→UI 的映射漏了 codex（存了 codex 的配置显示成别的协议）；</li>
 *   <li>{@code toEngineProtocol} 里出现 {@code else ->} —— 一个兜底分支就能让新协议
 *       静默降级成别的协议，而不是编译期报错；</li>
 *   <li>引擎枚举没收录 codex，或没引用实现类里的线上名常量；</li>
 *   <li>分派把 Codex 指向了别的实现。</li>
 * </ol>
 *
 * <p><b>它证明不了什么</b>：这是文本级断言，只能证明这条链**接得上**，
 * 不能证明 Codex 端点运行时真的收我们的请求。
 */
public final class CodexProtocolTest {

    private static final String ENGINE_PROTOCOL =
            "app/src/main/java/com/termux/app/zhicode/api/ApiProtocol.java";
    private static final String PROVIDERS =
            "app/src/main/java/com/termux/app/zhicode/api/ModelProviders.java";
    private static final String RESOLVER =
            "app/src/main/java/com/termux/app/zhicode/api/ApiEndpointResolver.java";
    private static final String RESPONSES =
            "app/src/main/java/com/termux/app/zhicode/api/OpenAIResponsesProvider.java";
    private static final String UI_MODELS =
            "app/src/main/java/com/zhizhu/zhicode/compose/model/SettingsModels.kt";
    private static final String UI_STORE =
            "app/src/main/java/com/zhizhu/zhicode/compose/data/ApiConfigStore.kt";

    /** 参考图表单里的下拉项文字。用户看到的就是它，写错等于换了个功能名。 */
    private static final String LABEL = "Codex Responses";

    /** 线上名：写进设置与会话文件，是持久化契约的一部分。 */
    private static final String WIRE = "codex-responses";

    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static String read(Path root, String relative) throws Exception {
        Path file = root.resolve(relative);
        require(Files.isRegularFile(file), "缺少文件: " + relative);
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    /** 去掉注释，但认得字符串字面量（正则版会被 "*&#47;*" 之类的字面量骗到）。 */
    private static String stripComments(String text) {
        StringBuilder out = new StringBuilder(text.length());
        boolean inLine = false;
        boolean inBlock = false;
        boolean inString = false;
        boolean inChar = false;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            char next = i + 1 < text.length() ? text.charAt(i + 1) : '\0';
            if (inLine) {
                if (c == '\n') {
                    inLine = false;
                    out.append(c);
                }
                continue;
            }
            if (inBlock) {
                if (c == '*' && next == '/') {
                    inBlock = false;
                    i++;
                }
                continue;
            }
            if (inString || inChar) {
                out.append(c);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (inString && c == '"') {
                    inString = false;
                } else if (inChar && c == '\'') {
                    inChar = false;
                }
                continue;
            }
            if (c == '/' && next == '/') {
                inLine = true;
                i++;
                continue;
            }
            if (c == '/' && next == '*') {
                inBlock = true;
                i++;
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '\'') {
                inChar = true;
            }
            out.append(c);
        }
        return out.toString();
    }

    private static String squash(String text) {
        return text.replaceAll("\\s+", "");
    }

    public static void main(String[] args) throws Exception {
        Path root = Paths.get(args.length == 0 ? "." : args[0]).toAbsolutePath().normalize();
        String engine = stripComments(read(root, ENGINE_PROTOCOL));
        String providers = stripComments(read(root, PROVIDERS));
        String resolver = stripComments(read(root, RESOLVER));
        String responses = stripComments(read(root, RESPONSES));
        String uiModels = stripComments(read(root, UI_MODELS));
        String uiStore = stripComments(read(root, UI_STORE));
        String sqUiModels = squash(uiModels);
        String sqUiStore = squash(uiStore);

        // ---- 1. UI 枚举里必须能选到它，且标签是参考图里那个名字 ----------------
        // ⚠️ 这一条要查**未 squash** 的文本：squash 会把空白全删掉（字面量里的空格
        // 也一起删），于是 "Codex Responses" 会变成 "CodexResponses"，
        // 断言就永远不成立 —— 而它看起来像是在检查标签，实际什么都没检查。
        require(uiModels.contains("CODEX_RESPONSES(\"" + LABEL + "\")"),
                UI_MODELS + " 的协议枚举里必须有 `" + LABEL + "`："
                        + "缺了它，用户在下拉里选不到 Codex —— 而引擎侧那条分支已经实现了，"
                        + "症状就是「功能不可达」，编译与测试都不会报错");

        // ---- 2. 两个映射都不能漏 -------------------------------------------
        require(sqUiStore.contains("\"" + WIRE + "\"->ApiProtocol.CODEX_RESPONSES"),
                UI_STORE + " 必须把线上名 " + WIRE + " 映射到 CODEX_RESPONSES："
                        + "漏了的话，选中的 Codex 会被当成「不认识」而回落到 OpenAI Responses");
        require(sqUiStore.contains("ApiProtocol.CODEX_RESPONSES->\"" + WIRE + "\""),
                UI_STORE + " 必须把 CODEX_RESPONSES 映射回线上名 " + WIRE + "："
                        + "漏了的话，用户选 Codex 存下去的是别的协议名，"
                        + "引擎那边永远进不到 codex 分支");

        // ---- 3. toEngineProtocol 不许有兜底 else ---------------------------
        // 一个 `else -> "openai-responses"` 就能让新协议**静默**降级；
        // 而没有 else 时，Kotlin 会在编译期拦住漏掉的取值。
        //
        // ⚠️ 锚点必须落在**声明**上：`indexOf("toEngineProtocol")` 会命中文件里
        // 第 42 行的调用点（draft.protocol.toEngineProtocol()），从那里截到文件尾
        // 就把 toUiProtocol 里的 else 也算进来了 —— 那样这条断言必然失败，
        // 而失败原因看起来像"真的有 else"。
        int engineMapStart = uiStore.indexOf("ApiProtocol.toEngineProtocol()");
        require(engineMapStart > 0, UI_STORE + " 里找不到 toEngineProtocol 的声明（读路径或函数名变了？）");
        String engineMap = uiStore.substring(engineMapStart);
        // 锚点自校验：这一段必须真的包含全部五个取值，否则我们检查的不是那个函数。
        for (String mapping : new String[]{
                "OPENAI_RESPONSES", "OPENAI_CHAT", "ANTHROPIC", "CODEX_RESPONSES", "DEBUG_SCRIPTED"}) {
            require(engineMap.contains(mapping),
                    UI_STORE + " 的 toEngineProtocol 里缺少 " + mapping + " 的映射："
                            + "每个取值都要有显式映射，不能靠兜底");
        }
        require(!engineMap.contains("else ->"),
                UI_STORE + " 的 toEngineProtocol 里不许有 else 兜底："
                        + "那会让漏掉的协议静默降级成另一个协议，"
                        + "而正确的形态是让编译器在漏改时直接报错");

        // ---- 4. 引擎枚举收录，且线上名只有一处定义 -------------------------
        require(squash(engine).contains("CODEX_RESPONSES(OpenAIResponsesProvider.WIRE_CODEX_RESPONSES)"),
                ENGINE_PROTOCOL + " 必须以 OpenAIResponsesProvider.WIRE_CODEX_RESPONSES 收录 Codex："
                        + "线上名两边各写一份字面量，写歪了表现是「协议没实现」");
        require(responses.contains("WIRE_CODEX_RESPONSES = \"" + WIRE + "\""),
                RESPONSES + " 必须定义 WIRE_CODEX_RESPONSES = \"" + WIRE + "\"："
                        + "这个取值是持久化契约（写进设置与会话文件），不能改");

        // ---- 5. 分派指向同一个实现，且按原始串分支 -------------------------
        require(squash(providers).contains("caseCODEX_RESPONSES->newOpenAIResponsesProvider()"),
                PROVIDERS + " 必须把 CODEX_RESPONSES 分派到 OpenAIResponsesProvider："
                        + "两种 Responses 变体共用传输实现，但那个类会重读原始线上名来分支");
        // 注意针里不能带空格：squash 后的源码里 "boolean codex" 已经变成 "booleancodex"。
        require(squash(responses).contains("booleancodex=WIRE_CODEX_RESPONSES.equals(config.protocol)"),
                RESPONSES + " 必须按原始线上名判断是不是 Codex 变体："
                        + "ApiProtocol 把两种变体指向同一个实现，进来之后只能靠这个区分");

        // ---- 6. 模型目录必须排除 Codex -------------------------------------
        require(squash(resolver).contains("protocol==ApiProtocol.CODEX_RESPONSES"),
                RESOLVER + " 必须把 Codex 排除在模型目录之外："
                        + "那个后端没有 /v1/models，照旧拼一个出来只会让列表静默空着");
    }
}
