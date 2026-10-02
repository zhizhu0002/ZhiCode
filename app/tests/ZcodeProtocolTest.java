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
    private static final String UI_PICKER =
            "app/src/main/java/com/zhizhu/zhicode/compose/ui/dialogs/ModelPickerOverlay.kt";
    private static final String UI_CATALOG_STORE =
            "app/src/main/java/com/zhizhu/zhicode/compose/data/ModelCatalogStore.kt";
    private static final String UI_VM =
            "app/src/main/java/com/zhizhu/zhicode/compose/state/WorkspaceViewModel.kt";
    private static final String APP_CLASS =
            "app/src/main/java/com/zhizhu/zhicode/compose/ZhiCodeApplication.kt";
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

    /** 数一段文本里出现几次（用来确定"两条链路都传了"这种要求）。 */
    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int from = 0;
        while (true) {
            int at = text.indexOf(needle, from);
            if (at < 0) return count;
            count++;
            from = at + needle.length();
        }
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

        // ---- 4. 网关地址不许进请求路径 -------------------------------------
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

        // ---- 5. 来源头按官方客户端在运行时生成 ------------------------------
        // 这一节此前是反过来的：它要求 provider/wire **不许**出现身份标识，理由是
        // "那是别人的客户端身份"。两个事实让那个判断失效了：
        //   1. 那个客户端（zai-org/ZCode）以 Apache-2.0 开源，这些头是它公开的协议契约；
        //   2. 只发"功能上必需"的头时，真机上额度恒回 400/3001 parameter error、
        //      会话恒回 405/3012 blocked —— 官方源码写明缺 X-Device-Mid 就会被判参数错误。
        // 所以现在钉住的是"按官方那套发"，以及一条仍然成立的底线：地址只来自 baseUrl。
        require(wire.contains("X-Device-Mid") || wire.contains("x-device-mid"),
                ZCODE_WIRE + " 必须发 X-Device-Mid：缺它时额度接口被判 parameter error"
                        + "（官方 packages/server/src/stdioDeviceMid.ts 的注释原文）");
        // provider 的两条链路（会话 + 额度）都必须把它传下去。
        int deviceMidUses = countOccurrences(provider, "deviceMid = deviceId()");
        require(deviceMidUses >= 2,
                ZCODE_PROVIDER + " 的会话与额度两条链路都必须带上 deviceMid（现在只有 "
                        + deviceMidUses + " 处）：只给其中一条，另一条还是会 parameter error");
        require(provider.contains("ZcodeWire.newRequestId()"),
                ZCODE_PROVIDER + " 的会话请求必须带请求级归因头（x-request-id / x-zcode-trace-id）："
                        + "官方在模型请求上生成它们，服务端用它区分主对话与子代理");
        require(provider.contains("ZcodeWire.SESSION_TYPE_MAIN"),
                ZCODE_PROVIDER + " 的会话请求必须带 x-zcode-session-type = main");
        // 官方有**两条不同的头路径**，取值不一样，这一点必须钉住：
        //   会话（model-config.ts）   有 X-ZCode-Agent: glm，没有 X-Device-Mid 之外的差异
        //   额度（zcode-source-headers.ts）没有 X-ZCode-Agent
        // 真机现象正好印证：补上 X-Device-Mid 后额度通了，会话仍 405/3012 —— 缺的就是这个头。
        require(squash(provider).contains("agent=ZcodeWire.DEFAULT_AGENT"),
                ZCODE_PROVIDER + " 的**会话**请求必须带 X-ZCode-Agent（官方 model-config.ts 有、"
                        + "额度那条路径没有）：缺它时真机上会话被拒 405/3012 unusual activity");
        require(squash(wire).contains("constvalDEFAULT_AGENT=\"glm\""),
                ZCODE_WIRE + " 必须有 DEFAULT_AGENT = glm（官方就是硬编码这个值）");
        // 额度请求刚被证明可用，别顺手给它也加上。
        int quotaMethodAt = provider.indexOf("fun fetchBalance");
        int openAt = provider.indexOf("private fun open(");
        require(quotaMethodAt >= 0 && openAt > quotaMethodAt,
                "定位不到 fetchBalance 的实现（方法被改名或重排了？）："
                        + "本断言要检查它不带会话专用的头");
        String fetchBalance = provider.substring(quotaMethodAt, openAt);
        require(!fetchBalance.contains("agent ="),
                ZCODE_PROVIDER + " 的**额度**请求不许带 X-ZCode-Agent："
                        + "官方额度路径没有它，而那条路径刚在真机上被证明可用，不要动");
        // HTTP-Referer 由 baseUrl 推 —— 不写死域名（官方也只在等于默认 origin 时才改写）。
        require(wire.contains("fun originOf("),
                ZCODE_WIRE + " 必须从 baseUrl 推 origin 供 HTTP-Referer 用："
                        + "写死域名会让「请求发到哪」与「对端看到我们来自哪」不一致");
        require(!provider.contains("HTTP-Referer") && !provider.contains("http-referer"),
                ZCODE_PROVIDER + " 不许自己拼 HTTP-Referer：那是 ZcodeWire.headers 的职责");
        // 版本号只有一处：UA 与 X-ZCode-App-Version 必须说同一个版本。
        require(squash(wire).contains("constvalDEFAULT_APP_VERSION=\"3.14.3\""),
                ZCODE_WIRE + " 必须有一个默认版本常量（官方对应编译期常量 ZCODE_VERSION）："
                        + "它是额度查询 app_version 与默认 User-Agent 的共同来源");
        require(squash(wire).contains("\"user-agent\"to\"ZCode/$version\""),
                ZCODE_WIRE + " 的默认 User-Agent 必须由那个版本拼出来："
                        + "两处各写一个字面量，迟早会一个改了另一个没改");
        // 设备标识必须在应用启动时注入，否则每次启动都是一个新身份，
        // 服务端认不出是同一台设备，额度与套餐模型会时有时无。
        require(read(root, APP_CLASS).contains("ZcodeDeviceMid.install("),
                APP_CLASS + " 的 attachBaseContext 必须调用 ZcodeDeviceMid.install："
                        + "没注入时 provider 会回退到进程内的临时值，每次启动换一个身份");

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

        // ---- 8. 额度：数字与倒计时只允许有一处实现 -------------------------
        // 界面自己再算一遍单位换算或倒计时分档，两处迟早不一致 —— 而那种不一致
        // 表现为"两个地方数字不一样"，最难判哪个对。
        String overlay = stripComments(read(root, UI_OVERLAY));
        require(wire.contains("fun formatCountdown") && wire.contains("fun formatUnits"),
                ZCODE_WIRE + " 必须提供 formatCountdown 与 formatUnits（唯一的实现处）");
        for (String banned : new String[]{"86400", "3600", "10000.0", "100000000"}) {
            require(!overlay.contains(banned),
                    UI_OVERLAY + " 里不许出现倒计时/单位换算的阈值 " + banned + "："
                            + "那些属于协议知识，只有 ZcodeWire 一处实现");
        }
        String picker = stripComments(read(root, UI_PICKER));
        require(!picker.contains("86400") && !picker.contains("/ 10000.0"),
                UI_PICKER + " 里不许自己算额度数字或倒计时");

        // ---- 9. 额度必须来自真实解析，且走同一次请求 -----------------------
        // 解析在 provider 里（它转调 ZcodeWire），store 只负责编排 —— 断言写在正确的层次上。
        require(provider.contains("ZcodeWire.parseBalancePayload"),
                ZCODE_PROVIDER + " 必须用 ZcodeWire.parseBalancePayload 解析额度响应："
                        + "在 provider 里现场抠字段，等于让响应形状有了第二处实现");
        require(!provider.contains("optLong(\"remaining_units\""),
                ZCODE_PROVIDER + " 不许自己解析额度字段：那是 ZcodeWire 的职责（可单测）");
        String catalogStore = read(root, UI_CATALOG_STORE);
        require(catalogStore.contains("ZcodeWire.entitledModelIds"),
                UI_CATALOG_STORE + " 的 ZCode 模型列表必须来自套餐 entitlement："
                        + "参考实现就是拿额度返回里的 capabilities 筛出「仅套餐可用模型」");
        require(squash(catalogStore).contains("ZcodeProvider().fetchBalance(config)"),
                UI_CATALOG_STORE + " 必须只调一次 fetchBalance 同时拿到模型列表与额度："
                        + "分两次请求会多一次往返，还可能拿到互相不一致的两份数据");

        // ---- 9b. 模型列表不许挂在额度请求成功上 ---------------------------
        // 真机上出过这个画面：额度接口回 HTTP 400，面板里一个模型都选不到、只剩一行错误。
        // 而模型名我们本来就写着（内置 11 项），额度只是一次网络请求 —— 它失败不该
        // 让"能选哪些模型"也一起消失。参考实现同样是分开的：列表来自内置表，
        // 额度另有卡片、失败只改那一行字。
        String sqCatalog = squash(stripComments(catalogStore));
        int tableAt = sqCatalog.indexOf("ZcodeWire.MODEL_NAMES");
        int balanceAt = sqCatalog.indexOf("ZcodeProvider().fetchBalance(config)");
        require(tableAt >= 0,
                UI_CATALOG_STORE + " 的 ZCode 分支必须摆出内置模型表 ZcodeWire.MODEL_NAMES："
                        + "列表得有个不依赖网络的来源");
        require(balanceAt > tableAt,
                UI_CATALOG_STORE + " 必须先摆好内置模型表再去请求额度："
                        + "次序反过来说明列表又挂在请求成功上了 —— 额度一失败整张列表就没了");
        require(sqCatalog.contains("quotaError"),
                UI_CATALOG_STORE + " 必须把额度失败降级成一句 quotaError（显示在额度卡片里）："
                        + "额度失败是常态，不该让整个面板只剩错误");
        require(sqCatalog.contains("models=all,quotaError="),
                UI_CATALOG_STORE + " 额度失败那条分支必须**照样给出整张内置表**"
                        + "（`models = all` 与 quotaError 在同一个 Catalog 里）："
                        + "只带一个原因、不带模型，就还是「额度一坏全都没得选」");
        require(sqCatalog.contains("runCatching{ZcodeProvider().fetchBalance(config)}"),
                UI_CATALOG_STORE + " 的额度请求必须被 runCatching 兜住（失败降级而不是整次失败）");
        require(squash(read(root, UI_MODELS)).contains("valquotaError:String=\"\""),
                UI_MODELS + " 的 ModelPickerState 必须有 quotaError 字段："
                        + "「没有额度」与「读不到额度」是两件事，卡片要说的话也不一样");
        // 接了字段却没人往下传 / 没人显示，是同一类漏法的下一环：状态里有了、用户看不到。
        require(squash(read(root, UI_VM)).contains("quotaError=fetched.quotaError"),
                UI_VM + " 必须把 fetched.quotaError 铺进面板状态："
                        + "少了这一行，额度失败的原因就停在数据层，用户什么都看不到");
        require(squash(read(root, UI_PICKER)).contains("picker.quotaError.isNotEmpty()"),
                UI_PICKER + " 的额度卡片必须在 quotaError 非空时也显示："
                        + "只按 quota 非空判断的话，读不到额度时卡片直接消失 ——"
                        + "而「卡片不见了」和「额度是 0」在界面上分不出来");

        // ---- 9c. 模型名必须是官方规范形式（发给服务端的东西，大小写不由我们定）-----
        // 官方把 GLM 规范 id 定义成大写（official-glm-model-id.ts 的 OFFICIAL_GLM_MODEL_IDS），
        // 内置模型名单（zcode-builtin.json 的 builtinModelIds）用的也是大写。
        // 之前这里是全小写 —— 照另一个应用的反编译表抄的，而那个表很可能是它自己从来没
        // 跑通的原因之一。模型名是**发给服务端的**，写错就是一个我们看不懂的 invalid_request。
        String sqWire = squash(wire);
        require(sqWire.contains("\"GLM-5.3\"to\"GLM5.3\""),
                ZCODE_WIRE + " 的内置模型表必须用官方规范大写 id（GLM-5.3）："
                        + "官方 OFFICIAL_GLM_MODEL_IDS 与 zcode-builtin.json 都是大写形式");
        require(!sqWire.contains("\"glm-5.3\"to"),
                ZCODE_WIRE + " 不许把模型 id 写回全小写（glm-5.3）");
        require(wire.contains("fun normalizeModel"),
                ZCODE_WIRE + " 必须有 normalizeModel 把用户输入折成规范形式："
                        + "官方有 normalizeOfficialGlmModelId，同一个角色");
        require(sqWire.contains("it.equals(trimmed,ignoreCase=true)"),
                ZCODE_WIRE + " 的 normalizeModel 必须**不区分大小写**地折算："
                        + "用户手打 glm-5.3 时发出去的必须是服务端认的那个写法");
        require(sqWire.contains("wanted=entitled.map{it.trim().lowercase(Locale.US)}.toSet()"),
                ZCODE_WIRE + " 的 filterEntitled 必须不区分大小写匹配："
                        + "服务端 capabilities 里回哪种大小写不由我们决定，"
                        + "用 == 比对时一次大小写差异就会静默筛空并回落到整张表");

        // ---- 9d. 3012 必须被翻译，不许以原始 JSON 丢给用户 -------------------
        // 它看着像"请求写错了"（HTTP 405 + JSON），实际是**账号级风控**，与客户端实现无关：
        // zai-org/feedback#716 里官方客户端 3.12.3/3.14.4 上一个字的输入同样被拒、
        // headersApplied=true、额度接口正常，触发点是短时间并发调用后的持续标记。
        // 不翻译的后果是每个人（包括我们）都会再花一轮去查请求哪一项不对。
        require(sqWire.contains("fundescribeHttpFailure("),
                ZCODE_WIRE + " 必须提供 describeHttpFailure 把 3012 翻成人能读的话");
        require(sqWire.contains("constvalCODE_BLOCKED=3012"),
                ZCODE_WIRE + " 必须把 3012 定义为具名常量，不许在别处散落字面量");
        int blockedUses = countOccurrences(provider, "ZcodeWire.describeHttpFailure(");
        require(blockedUses >= 2,
                ZCODE_PROVIDER + " 的会话与额度两条失败路径都要过 describeHttpFailure（现在 "
                        + blockedUses + " 处）：漏掉的那条会继续把原始 JSON 丢给用户");
        require(provider.contains("truncate(text)"),
                ZCODE_PROVIDER + " 必须保留原始的 HTTP 失败文案作为兜底："
                        + "只认 3012 一个码，别的错误原样留着，否则会吞掉服务端真正有用的说明");

        // ---- 10. 「填入 ZCode 默认值」必须两个字段一起填 -------------------
        require(overlay.contains("填入 ZCode 默认值"),
                UI_OVERLAY + " 必须有「填入 ZCode 默认值」入口："
                        + "没有它，用户要自己找网关地址与那组身份头，这个协议基本不可用");
        require(squash(overlay).contains("it.copy(baseUrl=ZCODE_GATEWAY,extraHeaders=ZCODE_HEADERS_JSON)")
                        || squash(overlay).contains("copy(baseUrl=ZCODE_GATEWAY,extraHeaders=ZCODE_HEADERS_JSON)"),
                UI_OVERLAY + " 的一次填入必须**同时**写 baseUrl 与 extraHeaders："
                        + "只填一样必然连不上，而分两次填会让人以为步骤还没做完");
        require(overlay.contains("填写 ZCode 授权码"),
                UI_OVERLAY + " 的 ZCode 密钥框要提示「填写 ZCode 授权码」："
                        + "同一件事在一边叫 API Key、一边叫授权码，用户会去找另一样东西");
    }
}
