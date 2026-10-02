import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 「ZCode 协议接通了，而且没有把别人的身份嵌进来」的守卫。
 *
 * <h2>钉住的两类退化</h2>
 *
 * <b>一类是接不上</b>（与 Codex 那次同样的漏法）：引擎侧写了 provider、UI 里却选不到，
 * 或者选了 ZCode 却发出别的协议名 —— 编译通过、测试全绿、功能不可达。
 *
 * <b>另一类是接错了</b>，这一类比上面严重：把网关地址或那组身份头**硬编码进请求路径**。
 * 后果不是报错，而是——用户看不见自己连到了哪；对方一改标识，所有用户一起断而
 * 我们连原因都看不到；而且那是替用户向一个第三方宣称了一个客户端身份。
 * 所以本守卫专门断言那些取值**不在** provider 与纯逻辑层里，只允许出现在界面的
 * 可粘贴提示中（用户看得见、可修改，见 `ApiConfigOverlay.ZCODE_HEADERS_HINT`）。
 *
 * <h2>还钉住"复用而不是重写"</h2>
 * ZCode 说的是 Anthropic Messages，所以它必须复用
 * {@code AnthropicMessagesProvider.StreamDecoder} 与 {@code readEvent}。
 * 一旦有人在这里另写一套 SSE 分帧，就说明那两处最容易错的逻辑（多行 data: 的分帧、
 * 缺终止标记时报什么错）有了第二份实现 —— 而两份实现迟早会不一致。
 */
public final class ZcodeProtocolTest {

    private static final String ENG_PROTOCOL =
            "app/src/main/java/com/termux/app/zhicode/api/ApiProtocol.java";
    private static final String PROVIDERS =
            "app/src/main/java/com/termux/app/zhicode/api/ModelProviders.java";
    private static final String RESOLVER =
            "app/src/main/java/com/termux/app/zhicode/api/ApiEndpointResolver.java";
    private static final String ANTHROPIC =
            "app/src/main/java/com/termux/app/zhicode/api/AnthropicMessagesProvider.java";
    private static final String ZCODE_PROVIDER =
            "app/src/main/java/com/termux/app/zhicode/api/ZcodeProvider.kt";
    private static final String ZCODE_WIRE =
            "app/src/main/java/com/termux/app/zhicode/api/zcode/ZcodeWire.kt";
    private static final String SESSION =
            "app/src/main/java/com/termux/app/zhicode/model/SessionConfig.java";
    private static final String PROFILE =
            "app/src/main/java/com/termux/app/zhicode/model/ApiProfile.java";
    private static final String STORE =
            "app/src/main/java/com/termux/app/zhicode/storage/ApiSettingsStore.java";
    private static final String UI_MODELS =
            "app/src/main/java/com/zhizhu/zhicode/compose/model/SettingsModels.kt";
    private static final String UI_STORE =
            "app/src/main/java/com/zhizhu/zhicode/compose/data/ApiConfigStore.kt";
    private static final String UI_OVERLAY =
            "app/src/main/java/com/zhizhu/zhicode/compose/ui/dialogs/ApiConfigOverlay.kt";
    private static final String FAST_SCRIPT = "test-jvm-fast.sh";

    /** 线上名：写进设置与会话文件，是持久化契约的一部分。 */
    private static final String WIRE = "zcode";

    /** 参考实现里那个网关主机。它**只能**出现在界面的可粘贴提示里。 */
    private static final String GATEWAY_HOST = "z.ai";

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
        String engine = stripComments(read(root, ENG_PROTOCOL));
        String providers = squash(stripComments(read(root, PROVIDERS)));
        String resolver = squash(stripComments(read(root, RESOLVER)));
        String anthropic = stripComments(read(root, ANTHROPIC));
        String provider = stripComments(read(root, ZCODE_PROVIDER));
        String wire = stripComments(read(root, ZCODE_WIRE));
        String session = stripComments(read(root, SESSION));
        String profile = stripComments(read(root, PROFILE));
        String store = stripComments(read(root, STORE));
        String uiModels = stripComments(read(root, UI_MODELS));
        String uiStore = stripComments(read(root, UI_STORE));
        String uiOverlay = stripComments(read(root, UI_OVERLAY));

        // ---- 1. UI 里能选到 ZCode，且两个映射都不缺 -------------------------
        require(uiModels.contains("ZCODE(\"ZCode\")"),
                UI_MODELS + " 的协议枚举里必须有 ZCODE(\"ZCode\")："
                        + "缺了它，引擎侧那份 provider 谁也点不到（Codex 就是这么漏掉的）");
        String sqUiStore = squash(uiStore);
        require(sqUiStore.contains("\"" + WIRE + "\"->ApiProtocol.ZCODE"),
                UI_STORE + " 必须把线上名 " + WIRE + " 映射到 ZCODE");
        require(sqUiStore.contains("ApiProtocol.ZCODE->\"" + WIRE + "\""),
                UI_STORE + " 必须把 ZCODE 映射回线上名 " + WIRE + "："
                        + "漏了的话用户选了 ZCode，存下去的却是别的协议名");

        // ---- 2. 引擎侧收录 + 分派 ------------------------------------------
        require(squash(engine).contains("ZCODE(ZcodeWire.WIRE_NAME)"),
                ENG_PROTOCOL + " 必须以 ZcodeWire.WIRE_NAME 收录 ZCODE："
                        + "线上名两边各写一份字面量，写歪了表现是「协议没实现」");
        require(providers.contains("caseZCODE->newZcodeProvider()"),
                PROVIDERS + " 必须把 ZCODE 分派到 ZcodeProvider");
        require(squash(wire).contains("constvalWIRE_NAME=\"zcode\""),
                ZCODE_WIRE + " 的 WIRE_NAME 必须逐字是 zcode（它是存盘格式的一部分）");
        require(resolver.contains("protocol==ApiProtocol.ZCODE"),
                RESOLVER + " 必须把 ZCode 排除在通用模型目录之外："
                        + "它的模型列表在自己的端点，拼一个 /v1/models 出来只会静默空着");

        // ---- 3. 复用而不是重写 SSE 分帧 -----------------------------------
        require(provider.contains("AnthropicMessagesProvider.StreamDecoder"),
                ZCODE_PROVIDER + " 必须复用 AnthropicMessagesProvider.StreamDecoder："
                        + "那个网关说的就是 Anthropic Messages，重写一遍等于让"
                        + "「多行 data: 的分帧」「缺终止标记报什么错」有了第二份实现");
        require(provider.contains("AnthropicMessagesProvider.readEvent"),
                ZCODE_PROVIDER + " 必须复用 AnthropicMessagesProvider.readEvent");
        require(anthropic.contains("static boolean readEvent("),
                ANTHROPIC + " 的 readEvent 必须是包内可见（去掉 private）才能被复用；"
                        + "放宽可见性是有意的最小改动，比复制一份实现好");

        // ---- 4. 网关与身份标识不许进请求路径 -------------------------------
        require(!provider.contains(GATEWAY_HOST),
                ZCODE_PROVIDER + " 不许出现网关主机名（" + GATEWAY_HOST + "）："
                        + "地址必须来自用户填的 baseUrl —— 写死之后「请求发到哪」就不透明了，"
                        + "对方一改路径所有用户一起断，而用户改不了我们代码");
        require(!wire.contains(GATEWAY_HOST),
                ZCODE_WIRE + " 不许出现网关主机名：纯逻辑层只负责拼装，不该认识任何主机");
        // 允许它出现在界面的**可粘贴提示**里，而且只有那里 —— 用户看得见、能改。
        require(uiOverlay.contains(GATEWAY_HOST),
                UI_OVERLAY + " 应当把网关地址写在可粘贴提示里："
                        + "不给的话这个协议对用户就是不可用的（他得从别处找这个值）");

        // ---- 5. 那组身份头只能出现在提示里 ---------------------------------
        // 判据取一个只属于「冒充官方 CLI」的取值：X-Platform 自称 linux。
        for (String baked : new String[]{"linux-x64", "X-ZCode-Agent", "zcode_cli_rs"}) {
            require(!provider.contains(baked),
                    ZCODE_PROVIDER + " 不许内置身份标识 " + baked + "："
                            + "那是那个服务自己客户端的标识，内置等于替用户宣称一个身份");
            require(!wire.contains(baked), ZCODE_WIRE + " 不许内置身份标识 " + baked);
        }

        // ---- 6. extraHeaders 必须真的从配置流到请求 -------------------------
        require(session.contains("public String extraHeaders"),
                SESSION + " 必须有 extraHeaders 字段（provider 要读它）");
        require(squash(session).contains("copy.extraHeaders=extraHeaders"),
                SESSION + " 的 copy() 必须覆盖 extraHeaders："
                        + "漏一个字段的后果是「某次复制之后设置悄悄回退成默认值」");
        require(profile.contains("\"extra_headers\""),
                PROFILE + " 必须把 extraHeaders 写进存盘 JSON（键名 extra_headers）");
        require(squash(profile).contains("copy.extraHeaders=extraHeaders")
                        && squash(profile).contains(".put(KEY_EXTRA_HEADERS,extraHeaders)")
                        && squash(profile).contains("json.optString(KEY_EXTRA_HEADERS"),
                PROFILE + " 的 copy()/toJson()/fromJson() 都必须带上 extraHeaders");
        require(squash(store).contains("config.extraHeaders=profile.extraHeaders"),
                STORE + " 的 applyProfile 必须把 extraHeaders 铺到会话配置上："
                        + "少了这一行，用户在设置里填的头永远不会被发出去，"
                        + "而现象只是「网关不认」");
        require(squash(store).contains("Key.EXTRA_HEADERS"),
                STORE + " 的 SETTINGS 表必须收录 extra_headers："
                        + "设置项只有一份清单，加了字段却没进表 = 能存进去、重启后消失");
        require(squash(uiStore).contains("it.extraHeaders=draft.extraHeaders.trim()")
                        && squash(uiStore).contains("extraHeaders=extraHeaders"),
                UI_STORE + " 的保存与读取都必须带上 extraHeaders");

        // ---- 7. 纯逻辑层必须留在秒级回路里 ---------------------------------
        require(!wire.contains("import android."),
                ZCODE_WIRE + " 不许 import android.*：它是纯逻辑，"
                        + "带上 Android 就只能靠分钟级的 Gradle 单测跑");
        String script = read(root, FAST_SCRIPT);
        require(script.contains("ZcodeWire.kt"),
                FAST_SCRIPT + " 必须把 ZcodeWire.kt 列进 MAIN_KT_SOURCES");
        require(script.contains("ZcodeWireTest"),
                FAST_SCRIPT + " 必须把 ZcodeWireTest 列进默认测试");
    }
}
