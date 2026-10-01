import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * 「全局调试浮层」与「Markdown 全语法样例」的守卫。
 *
 * <p>它守的是几件「删了不会编译失败」的事：
 *
 * <ol>
 *   <li><b>浮层只能进 debug 构建</b>。它是压在工作区之上的仪表盘，发布包里出现
 *       就是事故。两处门控都要在：挂载点与开关。</li>
 *   <li><b>它必须真的读实时状态</b>。这一层的全部价值是"边用边看"；若退化成只显示
 *       几个常量，它就不再能回答"我打的字到哪去了"。</li>
 *   <li><b>它必须用对话流的渲染器做实时预览</b>。这是需求里点名的能力
 *       （"输入东西可以展示 markdown"）。自己写一个简易渲染会给出**与真实消息
 *       不一致**的观感，那比没有更糟。</li>
 *   <li><b>Markdown 样例必须覆盖解析器支持的全部语法</b>。解析器支持 10 类块级 +
 *       12 类行内（见 `MarkdownParse.kt` 文件头）；样例少一类，那一类回归就没人发现。</li>
 *   <li><b>它不得挡住输入</b>。浮层盖在工作区上，全展开会遮住输入器与对话 ——
 *       必须保留"收起为窄药丸"的形态。</li>
 * </ol>
 */
public final class DebugHudStructureTest {

    private static final String SRC = "app/src/main/java/com/zhizhu/zhicode/compose/";

    private static final String HUD = SRC + "ui/debug/DebugHud.kt";
    private static final String DEBUG_PAGE = SRC + "ui/debug/UiDebugPage.kt";
    private static final String APP_SCAFFOLD = SRC + "ui/AppScaffold.kt";
    private static final String MODELS = SRC + "model/UiModels.kt";
    private static final String VIEW_MODEL = SRC + "state/WorkspaceViewModel.kt";
    private static final String MARKDOWN_PARSE = SRC + "ui/MarkdownParse.kt";
    /** 主体调试模式落在真实界面上的三个文件。 */
    private static final String CHAT_LIST = SRC + "ui/chat/ChatList.kt";
    private static final String COMPOSER = SRC + "ui/composer/Composer.kt";
    private static final String CHAT_AREA = SRC + "ui/ChatArea.kt";

    /** 调试 API（脚本化传输）涉及的文件：引擎侧与界面侧各两处。 */
    private static final String PROTOCOL_ENUM = "app/src/main/java/com/termux/app/zhicode/api/ApiProtocol.java";
    private static final String MODEL_PROVIDERS = "app/src/main/java/com/termux/app/zhicode/api/ModelProviders.java";
    private static final String SCRIPTED_PROVIDER =
            "app/src/main/java/com/termux/app/zhicode/api/DebugScriptedProvider.java";
    private static final String DEBUG_PROFILE = SRC + "data/DebugApiProfile.kt";

    /** 模态窗口：选择窗口的行首控件在这里决定。 */
    private static final String DIALOGS = SRC + "ui/dialogs/Dialogs.kt";

    /** 悬浮任务卡：条数与"画不画任务行"这两个决定都在这里。 */
    private static final String AGENT_PROGRESS_CARD = SRC + "ui/chat/AgentProgressCard.kt";

    /** 操作反馈条。事故见 §15：`state.message` 曾被写 29 次却没人渲染。 */
    private static final String MESSAGE_BAR = SRC + "ui/MessageBar.kt";

    /** 图片解码与缩略图。事故见 §16：图片发给了模型，界面上什么都不显示。 */
    private static final String ZHI_IMAGE = SRC + "ui/ZhiImage.kt";

    /** 会话读取：把 JSONL 里的 image 块读回界面模型。 */
    private static final String SESSION_READER = SRC + "data/SessionReader.kt";

    /** 用户气泡与助手卡片。图片行的接入点在 `UserBubble`。 */
    private static final String MESSAGE_CARDS = SRC + "ui/chat/MessageCards.kt";

    /** 工具实现所在目录：脚本里点名的工具名要在这里能找到出处。 */
    private static final String TOOLS_DIR = "app/src/main/java/com/termux/app/zhicode/tools";

    /** 工具自己声明的名字。 */
    private static final Pattern TOOL_NAME =
            Pattern.compile("public\\s+String\\s+name\\(\\)\\s*\\{\\s*return\\s+\"([^\"]+)\"");

    /** 脚本里的 `tool("id", "Name", …)`。 */
    private static final Pattern TOOL_CALL =
            Pattern.compile("tool\\(\"[^\"]*\",\\s*\"([^\"]+)\"");

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static String read(String root, String relative) throws Exception {
        return new String(Files.readAllBytes(Paths.get(root, relative)),
                java.nio.charset.StandardCharsets.UTF_8);
    }

    /** 去掉注释：否则「代码删了、注释里还写着」会让断言静默通过。 */
    private static String stripComments(String text) {
        String noBlock = text.replaceAll("(?s)/\\*.*?\\*/", " ");
        return noBlock.replaceAll("(?m)//[^\\n]*", " ");
    }

    private static void requireContains(String haystack, String needle, String message) {
        require(haystack.contains(needle), message);
    }

    public static void main(String[] args) throws Exception {
        String root = args.length > 0 ? args[0] : ".";

        // ---- 1. 浮层存在，且挂载点/开关双门控 --------------------------------
        String hud = stripComments(read(root, HUD));
        requireContains(hud, "fun ZhiDebugHud(", HUD + " 必须定义 ZhiDebugHud()");

        String scaffold = stripComments(read(root, APP_SCAFFOLD));
        int mount = scaffold.indexOf("ZhiDebugHud(");
        require(mount > 0, APP_SCAFFOLD + " 必须把调试浮层挂到工作区之上");
        String before = scaffold.substring(0, mount);
        int lastGate = before.lastIndexOf("BuildConfig.DEBUG");
        require(lastGate > 0 && mount - lastGate < 400,
                "挂载调试浮层的 if 必须紧邻 BuildConfig.DEBUG 门控 —— "
                        + "发布包里冒出调试仪表盘是事故，门控不许与它隔开");

        String page = stripComments(read(root, DEBUG_PAGE));
        int toggle = page.indexOf("setDebugOverlayEnabled");
        require(toggle > 0, DEBUG_PAGE + " 必须提供调试浮层的开关");
        require(page.lastIndexOf("BuildConfig.DEBUG", toggle) > 0,
                "调试浮层的开关必须在 BuildConfig.DEBUG 之内（它只在 debug 构建里存在）");

        // ---- 2. 状态字段与 ViewModel 入口 -----------------------------------
        String models = stripComments(read(root, MODELS));
        requireContains(models, "debugOverlayEnabled", MODELS + " 必须定义 debugOverlayEnabled");
        requireContains(models, "debugOverlayExpanded", MODELS + " 必须定义 debugOverlayExpanded");
        String viewModel = stripComments(read(root, VIEW_MODEL));
        requireContains(viewModel, "fun setDebugOverlayEnabled(",
                VIEW_MODEL + " 必须提供 setDebugOverlayEnabled()");
        requireContains(viewModel, "fun setDebugOverlayExpanded(",
                VIEW_MODEL + " 必须提供 setDebugOverlayExpanded()（收成药丸要靠它）");

        // ---- 3. 必须读实时状态（这一层的全部价值） --------------------------
        for (String field : new String[]{
                "composerText", "slashQuery", "slashMatches", "attachments", "pendingInputs",
                "transcript", "workingStatus", "tasks", "projectPath", "profileName", "modelLabel",
        }) {
            requireContains(hud, "state." + field,
                    "调试浮层必须读实时字段 state." + field
                            + " —— 它存在的意义就是\"边用边看\"，少一项就少一个排查入口");
        }
        requireContains(hud, "allTools()",
                "调试浮层必须把**所有**工具调用列出来（含运行中/失败/等待授权），"
                        + "只看最后一条是不够的");
        requireContains(hud, "awaitingPermission",
                "工具列表必须区分\"等待授权\"这一态（它是唯一需要人介入的状态）");
        requireContains(hud, "exitCode",
                "工具列表必须显示退出码（失败时最关键的信息）");

        // ---- 4. Markdown 实时预览必须用生产渲染器 --------------------------
        requireContains(hud, "ZhiMarkdown(",
                "调试浮层的 Markdown 预览必须调用生产的 ZhiMarkdown —— "
                        + "自己写一个简易渲染会给出与真实消息不一致的观感");
        requireContains(hud, "Markdown 实时预览",
                "预览区必须有明确的标题，否则看到一个会随输入变化的区域会让人困惑");

        // ---- 5. 复制快照 + 吐司 --------------------------------------------
        requireContains(hud, "Clipboard.copy(", "浮层必须能复制调试快照（出问题时贴出来就能复现）");
        requireContains(hud, "Toast.makeText(", "复制快照后必须弹吐司（调试时最要紧的是当场可见的反馈）");

        // ---- 6. 收起形态必须存在（否则会挡住输入器） ------------------------
        requireContains(hud, "CollapsedPill(",
                "调试浮层必须保留\"收起为窄药丸\"的形态：全展开会挡住输入器与对话");
        requireContains(hud, "setDebugOverlayExpanded(false)",
                "浮层里必须有收起动作（收起成药丸，而不是只能整个关掉）");

        // ---- 7. Markdown 样例必须覆盖解析器的全部语法 -----------------------
        // 语法清单写在 MarkdownParse.kt 的**文件头注释**里（那是它对外的语法契约），
        // 所以这一条要读原始文本，不能剥注释 —— 剥掉之后清单本身也就没了。
        String parseRaw = read(root, MARKDOWN_PARSE);
        requireContains(parseRaw, "## 支持的语法",
                "MarkdownParse.kt 必须保留\"支持的语法\"清单（本测试按它逐项核对样例）");
        requireContains(parseRaw, "有意不支持的",
                "MarkdownParse.kt 必须保留\"有意不支持的\"清单："
                        + "没有它，将来有人会以为\"不支持\"是漏做的 bug");
        String[] mustHave = {
                "# 一级标题",                 // ATX 标题
                "Setext 一级标题",             // Setext 标题
                "===============",            // Setext 下划线
                "```kotlin",                  // 反引号围栏
                "~~~",                        // 波浪线围栏
                "- 无序项",                   // 无序列表（减号）
                "* 星号标记",                 // 无序列表（星号）
                "+ 加号标记",                 // 无序列表（加号）
                "1. 有序第一项",               // 有序列表（点）
                "1) 括号编号第一项",           // 有序列表（括号）
                "- [ ] 未完成的待办",          // 任务项（未完成）
                "- [x] 已完成的待办",          // 任务项（已完成）
                "> 第一层引用",                // 引用
                "| 列一 | 列二 | 列三 |",      // 表格
                "**B**",                      // 粗体
                "__B__",                      // 下划线粗体
                "*I*",                        // 斜体
                "_I_",                        // 下划线斜体
                "***BI***",                   // 粗斜体
                "~~GONE~~",                   // 删除线
                "[ZhiCode 仓库](https://example.com/zhicode)", // 链接
                "<https://example.com/auto>", // 自动链接
                "https://example.com/bare?x=1", // 裸 URL
                "![替代文字](https://example.com/image.png)", // 图片
                "\\*这不该是斜体\\*",          // 转义
                "未闭合的围栏",                // 病态输入：未闭合围栏
                "未闭合的强调",                // 病态输入：未闭合强调
        };
        // ⚠️ 这一段的断言必须用**原始文本**（不剥注释）：样例是 Kotlin 字符串字面量，
        // 里面含 `https://…`，而剥注释的正则会把它当成行注释、把整行后面都删掉 ——
        // 用剥过的文本去查，链接类语法永远"缺失"。
        String pageRaw = read(root, DEBUG_PAGE);
        for (String syntax : mustHave) {
            requireContains(pageRaw, syntax,
                    "Markdown 全语法样例缺少「" + syntax + "」。解析器支持它，"
                            + "样例里没有 → 它退化时没人会发现。若确实不再支持该语法，"
                            + "请连同 MarkdownParse.kt 的清单与本测试一起改。");
        }

        // ---- 8. 样例必须给出"源码 ↔ 渲染"对照 -------------------------------
        requireContains(pageRaw, "MarkdownSampleBlock(",
                "每组 Markdown 样例必须有\"源码 ↔ 渲染\"对照块："
                        + "只给渲染结果的话，看到观感不对时无从判断是素材问题还是渲染问题");
        requireContains(pageRaw, "AssistantCard(",
                "Markdown 样例必须放在**生产的助手气泡**里渲染（与真实回复同一条路径）");
        requireContains(pageRaw, "查看源码",
                "每组的源码必须可展开查看（长样例默认展开会把页面拉得很长）");

        // ---- 9. **主体调试模式**：把真实界面当调试面板 -----------------------
        //
        // 这一项与"另开一页"是不同的调试手段：真实控件上加料，才能看到**真实排版**。
        // 下面每一条都是"删掉不会编译失败、只会静默退化"的。
        String chatList = stripComments(read(root, CHAT_LIST));
        String composer = stripComments(read(root, COMPOSER));
        String chatArea = stripComments(read(root, CHAT_AREA));

        requireContains(models, "debugAppMode", MODELS + " 必须定义 debugAppMode");
        requireContains(viewModel, "fun setDebugAppMode(",
                VIEW_MODEL + " 必须提供 setDebugAppMode()");

        requireContains(chatList, "debugMode: Boolean = false",
                CHAT_LIST + " 的 ChatList 必须接受 debugMode（真实对话流就地加料）");
        requireContains(chatList, "MessageDebugStrip(",
                "主体调试模式必须在真实消息上加调试条（类型/长度/工具计数）");
        requireContains(chatList, "源码",
                "主体调试模式必须能摊出原始 Markdown 源码："
                        + "看到渲染不对时，第一件事就是对照源码判断是素材还是渲染器的问题");
        requireContains(chatList, "onSurfaceVariantSummary",
                "调试条的配色必须走主题令牌（自己写死颜色会在浅色模式下糊掉）");

        // ---- 回归：调试条**不得**与消息卡叠在一起 ---------------------------
        //
        // 真机上出现过：调试条与消息正文糊在一起（`ASSISTANT ...` 那行字压在卡片上）。
        // 真因是 LazyColumn 每一项的那个外层 **Box 是用来给长按菜单定位的**，
        // 而 Box 的子项是**叠放**不是竖排 —— 把调试条直接并列进去就会被卡片盖住。
        // 这类错误不会编译失败，只在屏幕上烂掉，所以在这里钉住：
        // 调试条必须出现在一个 Column 里（与卡片竖排），叠放只留给那一个菜单。
        int strip = chatList.indexOf("MessageDebugStrip(item)");
        require(strip > 0, "找不到调试条的调用点");
        int columnBefore = chatList.lastIndexOf("Column(modifier = Modifier.fillMaxWidth())", strip);
        require(columnBefore > 0 && strip - columnBefore < 400,
                "调试条必须包在 Column 里再与消息卡竖排。这个位置的外层是给长按菜单定位的 Box，"
                        + "Box 子项是叠放：直接并列会让调试条被卡片盖住（真机上表现为文字糊在一起）。"
                        + "要拆掉这个 Column 的话，请先确认 CardList 每一项的层级改成竖排容器。");
        int menuAfter = chatList.indexOf("anchoredMenu(item.id, fingerOffset)", strip);
        require(menuAfter > 0,
                "长按菜单必须仍挂在最外层 Box 上（它要盖在卡片上，不能进竖排的 Column）");

        // 调试条的开关必须是**小胶囊**，不能是 Miuix TextButton：
        // 后者最小高 40dp、字号走主题 button 档，会把调试条撑得比消息卡还显眼。
        requireContains(chatList, "ZhiSmallPill(",
                "调试条的「源码」开关必须用 ZhiSmallPill（24dp 胶囊）");
        int pill = chatList.indexOf("ZhiSmallPill(");
        require(chatList.substring(strip, pill).indexOf("TextButton(") < 0,
                "调试条里不得出现 Miuix TextButton：它 40dp 的最小高会把这条注记撑成主角");

        requireContains(composer, "debugMode: Boolean = false",
                COMPOSER + " 的 Composer 必须接受 debugMode");
        requireContains(composer, "实时预览",
                "主体调试模式必须在真实输入器里做 Markdown 实时预览");
        requireContains(composer, "ZhiMarkdown(",
                "输入器的实时预览必须用生产渲染器 ZhiMarkdown —— "
                        + "自己写一个简易渲染会显示与真实消息不一致的观感");

        requireContains(chatArea, "debugMode = state.debugAppMode",
                CHAT_AREA + " 必须把 state.debugAppMode 接给对话流与输入器："
                        + "开关拨了但没接线，表现是\"开关没反应\"，而且不报错");

        int gate2 = page.indexOf("setDebugAppMode");
        require(gate2 > 0, DEBUG_PAGE + " 必须提供主体调试模式的开关");
        require(page.lastIndexOf("BuildConfig.DEBUG", gate2) > 0,
                "主体调试模式的开关必须在 BuildConfig.DEBUG 之内");

        // ---- 10. **调试 API（脚本化传输）** ---------------------------------
        //
        // 这一条守的是"网络边界"：脚本化传输让应用在**不出网**的情况下回话，
        // 一旦它在发布包里可用，用户会以为自己连上了某个服务。
        String protocol = read(root, PROTOCOL_ENUM);
        requireContains(protocol, "DEBUG_SCRIPTED(",
                PROTOCOL_ENUM + " 必须收录 DEBUG_SCRIPTED —— 它是一条能被正常选中/切换的配置协议");
        requireContains(protocol, "DebugScriptedProvider.WIRE_NAME",
                "调试协议的线上名必须引用 DebugScriptedProvider.WIRE_NAME，"
                        + "不要在两边各写一份字面量（写歪了表现是\"协议没实现\"）");

        String providers = stripComments(read(root, MODEL_PROVIDERS));
        int dispatch = providers.indexOf("case DEBUG_SCRIPTED");
        require(dispatch > 0, MODEL_PROVIDERS + " 必须分派 DEBUG_SCRIPTED（枚举 switch 少一个分支编译不过，"
                + "但把结果接错不会）");
        int providerGate = providers.indexOf("BuildConfig.DEBUG", dispatch >= 0 ? dispatch : 0);
        require(providerGate > 0 && providerGate - dispatch < 200,
                "DEBUG_SCRIPTED 的分派必须紧邻 BuildConfig.DEBUG 检查："
                        + "发布包里它必须走\"未知协议\"那条失败路径，而不是悄悄可用");
        requireContains(providers, "UNSUPPORTED",
                "发布包里的调试协议必须报\"没有实现\"（复用同一条错误文案），"
                        + "不要发明第二套说法");

        String scripted = stripComments(read(root, SCRIPTED_PROVIDER));
        requireContains(scripted, "createMessage(",
                SCRIPTED_PROVIDER + " 必须实现 ModelProvider.createMessage");
        requireContains(scripted, "onTextDelta", "脚本化传输必须真的走流式增量回调");
        requireContains(scripted, "onThinkingDelta",
                "脚本化传输必须产出思考增量：思考面板是对话流里最容易出问题的一块");
        requireContains(scripted, "onToolInputDelta",
                "脚本化传输必须产出工具入参增量：引擎侧\"拼入参\"的路径不能只在真模型那里被走到");
        require(!scripted.contains("new JSONObject() {}") && scripted.contains("assistantTurnCount("),
                "脚本进度必须从 messages 推导（本类必须无状态）："
                        + "ModelProvider 的实现不许把\"一次请求\"的状态放进字段");
        // 脚本里的命令必须是无副作用的读取类命令：调试时点一下"确认"不该改动工程。
        require(!scripted.contains("\"rm ") && !scripted.contains("rm -rf"),
                "脚本里的命令行不得包含删除类命令：调试时误授权就会改动工程");
        require(scripted.contains("Scenario"),
                "脚本化传输必须有多场景（关键词可选中不同链路），"
                        + "否则调试\"权限确认/计划审批/任务卡\"这几条链路仍然只能靠真模型凑");

        String debugProfile = stripComments(read(root, DEBUG_PROFILE));
        requireContains(debugProfile, "fun activate(",
                DEBUG_PROFILE + " 必须提供 activate()（建并选中调试配置）");
        requireContains(debugProfile, "fun restore(",
                DEBUG_PROFILE + " 必须提供 restore()：关掉调试模式要切回原来那条配置，"
                        + "否则用户下次正常发消息还在走脚本化传输，而界面显示\"调试已关闭\"");
        requireContains(debugProfile, "BuildConfig.DEBUG",
                "调试配置的写入必须自己再挡一道 BuildConfig.DEBUG");
        // ⚠️ 这一条必须用**原始文本**：`debug://scripted` 含 `//`，
        // 而剥注释的正则会把它当行注释、把该行后面全删掉 —— 用剥过的文本查，永远"缺失"。
        requireContains(read(root, DEBUG_PROFILE), "\"debug://",
                "调试配置的地址不得是 http(s)：脚本化传输不出网，"
                        + "写一个真地址会让人以为它在连某个服务");

        requireContains(viewModel, "DebugApiProfile.activate(",
                "打开主体调试模式时必须真的切到调试 API 配置");
        requireContains(viewModel, "DebugApiProfile.restore(",
                "关闭主体调试模式时必须真的切回原配置");
        requireContains(models, "debugPreviousProfileId",
                MODELS + " 必须记住\"打开调试前生效的那条配置\"，否则关掉时无从还原");

        // ---- 11. 全工具清单：清单读现场数据，且点名的工具必须真的存在 -----------
        //
        // 「输入『工具』列出所有工具」这条需求里最容易悄悄做坏的两件事：
        //   1. 清单写死一份名单 —— 注册表加了工具它不会跟着变，而界面上看不出来；
        //   2. 工具名写歪（`LS` 写成 `List`、`GitStatus` 写成 `Git`）—— 编译过、运行不报错，
        //      界面上只多一行 "Unknown tool"，看起来像工具的错，实际是脚本的错。
        requireContains(scripted, "ALL_TOOLS",
                SCRIPTED_PROVIDER + " 必须有「列出全部工具」的场景（关键词「工具」）");
        requireContains(scripted, "toolCatalog(",
                "全工具清单必须由 toolCatalog( 生成：它要读引擎这一次下发的 tools 数组，"
                        + "写死一份名单迟早与注册表对不上，而\"对不上\"在界面上看不出来");
        requireContains(scripted, "readOnlyBatch(",
                "全工具清单场景还要把只读那批**真跑一遍**：清单说明有什么，工具行说明它跑起来什么样");
        requireContains(scripted, "isOffered(",
                "只读批次必须按本次真下发的工具名单过滤：白名单挡掉的工具硬调只会换来一行 "
                        + "Unknown tool，而那是脚本的错，不该显示成工具的错");

        Set<String> registered = registeredToolNames(root);
        require(registered.size() > 20,
                "从 tools/ 里只认出 " + registered.size() + " 个工具名，取值方式大概写错了");
        Set<String> called = scriptedToolCalls(scripted);
        require(!called.isEmpty(), "没从脚本里认出任何工具调用，正则与写法大概已经对不上了");
        for (String name : called) {
            require(registered.contains(name),
                    "脚本化传输调用了不存在的工具 `" + name + "`（tools/ 下没有任何工具声明这个名字）："
                            + "界面上只会显示成一行 Unknown tool");
        }

        // ---- 12. 场景与步数必须**按轮**推导 ---------------------------------
        //
        // 「输入『工具』什么都没发生」这条真因就在这儿：关键词原本取自上下文的**第一条**
        // 用户消息，于是只有整段会话的第一句话能选场景。这类错编译通过、运行不报错，
        // 只在"会话已经聊了几轮之后再打关键词"时才显形 —— 必须由守卫钉住。
        requireContains(scripted, "lastPromptIndex(",
                "场景关键词必须取自**最后一条人类提问**（lastPromptIndex）："
                        + "取第一条的话，会话说久了再打关键词什么都不会发生");
        requireContains(scripted, "carriesToolResult(",
                "必须排除搬运工具结果的 user 消息：工具结果也以 role=user 追加，"
                        + "把它当新提问会让脚本每一步都退回第 0 步（引擎的工具循环出不来）");
        requireContains(scripted, "assistantTurnCount(messages, promptAt)",
                "步数必须相对**本轮提问**来数（助手回复也按轮数，不是整段会话的总数）："
                        + "按总数数的话，聊得久了新场景直接跳到收尾，第一步永远不执行");
        require(!scripted.contains("userText("),
                "旧的\"取第一条用户消息\"那个辅助方法必须删掉 —— 留着它，"
                        + "下一个改这里的人很容易又接回去");

        // ---- 13. 选择窗口的行首控件：未选中也要看得见 -----------------------
        //
        // 选项行原来只在**选中**时画一个 Miuix Check 图标（未选中 tint 透明）——
        // 一列选项看起来就是普通文字，看不出能点、也看不出能多选。
        // 能画出未选中态的 Miuix 选择控件只有 Checkbox，所以单选多选都用它。
        String dialogs = stripComments(read(root, DIALOGS));
        int picker = dialogs.indexOf("fun ChoicePickerOverlay(");
        require(picker > 0, DIALOGS + " 必须保留 ChoicePickerOverlay");
        int startAction = dialogs.indexOf("startAction = {", picker);
        require(startAction > 0, "选择窗口必须有行首控件（startAction）");
        int checkbox = dialogs.indexOf("Checkbox(", startAction);
        require(checkbox > 0 && checkbox - startAction < 600,
                "选择窗口的行首必须是 Miuix Checkbox（单选多选都一样）："
                        + "Miuix RadioButton 只画那个勾，未选中时整行不画任何东西，"
                        + "单选行会连\"这里能点\"都看不出来");
        require(dialogs.indexOf("startAction = if (", picker) < 0,
                "行首控件不得再按条件分成两个分支：一旦分开，某一支很容易又退化回\"看不见\"");
        requireContains(dialogs, "ToggleableState",
                "Checkbox 的选中态必须走 ToggleableState（On/Off），不要用别的近似控件代替");
        // 单选与多选的差别留在**行为**上：单选点另一行要换掉原来那行。
        requireContains(dialogs, "if (multi) {",
                "单选/多选的差别必须在 toggle 行为里保留（单选点另一行换掉原选项），"
                        + "控件统一不等于行为统一");

        // 选择窗口原先只有真模型肯调 AskUserQuestion 才出现，"选项行长什么样"只能碰运气复现。
        // 脚本化传输直接把这条链路变成一句话就能触发（引擎自己实现该工具，不注册在注册表里）。
        requireContains(scripted, "QUESTION",
                SCRIPTED_PROVIDER + " 必须有触发选择窗口的场景");
        requireContains(scripted, "\"AskUserQuestion\"",
                "触发选择窗口靠的是**同名 tool_use**：该工具由引擎自己实现"
                        + "（不注册在 ToolRegistry 里），所以脚本只能按名字给调用");
        requireContains(scripted, "\"multiSelect\", true",
                "选择窗口场景必须是**多选**：单选不画勾选框，用它测等于没测");

        // ---- 14. 悬浮任务卡必须真的露出任务行 -------------------------------
        //
        // 事故：`currentWindow(if (compact) 0 else maxTasks)` 配 `if (!compact)` 渲染 ——
        // 一个把 0 当"全部"、另一个把 0 当"不画"，两边理解相反，
        // 结果悬浮卡只剩「任务进度 3 / 7」和一根进度条，用户看到的就是"任务怎么没显现出来"。
        // 这两处必须同时钉住：条数参数是**真实条数**，且任务行不按 compact 决定画不画。
        String progressCard = stripComments(read(root, AGENT_PROGRESS_CARD));
        require(!progressCard.contains("if (compact) 0 else"),
                AGENT_PROGRESS_CARD + " 不得再出现 `if (compact) 0 else maxTasks`："
                        + "0 条任务与\"不画任务行\"是两回事，混在一起会让悬浮卡静默地什么都不显示");
        require(!progressCard.contains("if (!compact) visibleTasks"),
                "任务行不得包在 `if (!compact)` 里：悬浮形态也要露出当前窗口的几条任务");
        requireContains(progressCard, "currentWindow(maxTasks)",
                "条数参数必须原样透传给 currentWindow：悬浮与详情只差**条数**，不差\"画不画\"");
        int windowHelper = progressCard.indexOf("fun List<AgentTask>.currentWindow(");
        require(windowHelper > 0, AGENT_PROGRESS_CARD + " 必须保留 currentWindow 助手");
        requireContains(progressCard.substring(windowHelper, windowHelper + 600), "if (max <= 0) return emptyList()",
                "max <= 0 必须返回**空列表**（而不是\"全部\"）："
                        + "调用点弄错时至少会让\"还有 N 条\"的提示兜住，不会静默吞内容");

        // ---- 15. 操作反馈必须真的被渲染出来 -------------------------------
        //
        // 事故：Snackbar 因为"浮层挡输入器"被删掉，`state.message` 却留着 ——
        // 29 处 `copy(message = …)`（保存失败、附件读不了、没有可复制的内容…）
        // 从此写进一个**没人渲染**的字段，用户点保存失败时界面毫无反应。
        // 这是最难自查的一类 bug：字段有值、代码能编译、单测也过，只有人眼能发现。
        // 所以这里钉住三件事：写方有语义标志、读方存在、读方被真的调用。
        String messageBar = stripComments(read(root, MESSAGE_BAR));
        requireContains(messageBar, "fun MessageBar(",
                MESSAGE_BAR + " 必须提供 MessageBar 组合函数");
        requireContains(messageBar, "scheme.errorContainer",
                "错误提示必须走主题的 errorContainer，不能在代码里写死红色");
        requireContains(messageBar, "delay(",
                "提示条必须自己超时消失：否则一次失败会永久占着输入器上方那条空间");

        String chatAreaMsg = stripComments(read(root, CHAT_AREA));
        requireContains(chatAreaMsg, "MessageBar(",
                CHAT_AREA + " 必须真的调用 MessageBar —— 光有组件不算修复，"
                        + "这次事故正是\"有字段没人渲染\"，必须钉在调用点上");
        requireContains(chatAreaMsg, "message = state.message",
                "调用点必须接 state.message（而不是另起一个字段）");
        requireContains(chatAreaMsg, "onDismiss = { viewModel.clearMessage() }",
                "提示条要能被点掉/自动关掉，必须接上 clearMessage");

        String vmForMessage = stripComments(read(root, VIEW_MODEL));
        requireContains(vmForMessage, "fun clearMessage(",
                VIEW_MODEL + " 必须提供 clearMessage");
        requireContains(vmForMessage, "current.message != text",
                "clearMessage 必须先比对文案再清：否则一次无关点击会把刚弹出的新提示也抹掉");

        String modelsForMessage = stripComments(read(root, MODELS));
        requireContains(modelsForMessage, "val messageIsError: Boolean",
                MODELS + " 必须记录 message 的**语义**（是不是错误）："
                        + "从文案里猜（含\"失败\"就当错误）会在正常提示上判错，且改一个字就静默失效");

        // ---- 16. 用户发的图片必须能显示出来 -------------------------------
        //
        // 事故：图片确实发给了模型（引擎收到 base64 块），但界面上什么都不显示 ——
        // ChatItem 只有 body，图片字节存在 ViewModel 的 attachmentPayloads 里，
        // 而 send() 一进来就 clearAttachments() 把它清空；SessionReader 又把
        // JSONL 里的 image 块静默跳过。三处各自"看起来没问题"，合起来图就没了。
        //
        // 这类"数据在、没人画"的 bug 单测抓不住（字段有值、能编译、逻辑也过），
        // 所以按每一环各钉一条：模型有字段 → 读得回来 → 真的画了 → 发送时带上。
        requireContains(modelsForMessage, "val images: List<ChatImage>",
                MODELS + " 的 ChatItem 必须有 images 字段：图片要成为消息自己的一部分，"
                        + "而不是存在别处等被 clearAttachments() 清掉");

        String zhiImage = stripComments(read(root, ZHI_IMAGE));
        requireContains(zhiImage, "fun sampleSizeFor(",
                ZHI_IMAGE + " 必须提供 sampleSizeFor：4 万像素级的原图直接解码会 OOM");
        requireContains(zhiImage, "inJustDecodeBounds",
                "必须先量尺寸再降采样 —— inSampleSize 只有在解码**前**给出才生效");
        requireContains(zhiImage, "ContentScale.Crop",
                "缩略图必须用 Crop 而不是 Fit：同一行要**同高**，"
                        + "而 Fit 会让每张图按自己的比例停在不同高度上（用户原话"
                        + "\"不能同高吗，写的好丑\"）。宽度已由原图长宽比算出并夹进范围，"
                        + "所以只有极端长宽比才会真的裁到");
        requireContains(zhiImage, "fun thumbWidthFor(",
                ZHI_IMAGE + " 的宽度夹取必须抽成纯函数，并带单测："
                        + "coerceIn 在 min > max 时抛 IllegalArgumentException，"
                        + "而这个函数有两个调用点、上下限各不相同");
        requireContains(zhiImage, "val low = minOf(minWidth, maxWidth)",
                "thumbWidthFor 必须自己把上下限摆正：调用点传反了就是\"打开对话直接崩\"");
        require(!zhiImage.contains("coil"),
                ZHI_IMAGE + " 不得引入 coil 之类的图片库：构建走 --offline，加依赖会直接构建失败");

        String messageCards = stripComments(read(root, MESSAGE_CARDS));
        requireContains(messageCards, "ZhiImageRow(",
                MESSAGE_CARDS + " 的 UserBubble 必须真的画图片行 —— 这是整个 bug 的修复点");
        requireContains(messageCards, "images = item.images",
                "图片行必须接 item.images（而不是从别处找数据）");

        String sessionReader = stripComments(read(root, SESSION_READER));
        requireContains(sessionReader, "fun readImages(",
                SESSION_READER + " 必须能从会话行里读回 image 块");
        requireContains(sessionReader, "images.isNotEmpty()",
                "出气泡的判据必须包含 images：只发图不写字的消息没有 text 块，"
                        + "按 text.isNotBlank() 判断会把整条消息丢掉（用户翻历史会发现图连带消息都没了）");

        String vmForImages = stripComments(read(root, VIEW_MODEL));
        requireContains(vmForImages, "images = currentImages()",
                VIEW_MODEL + " 的 send() 必须把图片挂到出站气泡上");
        requireContains(vmForImages, "fun currentImages()",
                VIEW_MODEL + " 必须提供 currentImages()");
        // 顺序：取图片必须在 clearAttachments() **调用**之前，否则拿到的是空列表。
        //
        // ⚠️ 必须把范围限定在 send() 函数体内再比位置：`clearAttachments()` 这个子串
        // 在它自己的**定义**处（`private fun clearAttachments() {`）也出现，
        // 而那个位置在文件里永远更靠前 —— 不限定范围的话这条断言恒为失败，
        // 等于一个永远红着的测试（那和没有测试一样糟糕）。
        int sendAt = vmForImages.indexOf("fun send()");
        require(sendAt > 0, VIEW_MODEL + " 必须保留 send()");
        // 切到下一个顶层函数为止，避免把后面别的方法也算进 send() 里。
        int sendEnd = vmForImages.indexOf("\n    private fun ", sendAt + 1);
        if (sendEnd < 0) sendEnd = vmForImages.indexOf("\n    fun ", sendAt + 1);
        if (sendEnd < 0) sendEnd = vmForImages.length();
        String sendBody = vmForImages.substring(sendAt, sendEnd);
        int firstClear = sendBody.indexOf("clearAttachments()");
        int firstImages = sendBody.indexOf("images = currentImages()");
        require(firstClear > 0 && firstImages > 0 && firstImages < firstClear,
                "send() 里 currentImages() 必须出现在 clearAttachments() 之前："
                        + "附件载荷由它清空，顺序反了图片就是空的（这正是原来的 bug）");

        // ---- 17. 发送 / 停止键必须是方角且同尺寸 ---------------------------
        //
        // Common.kt 的 ZhiFilledIconButton 文档写着「square 为 true 时改成方角，
        // 发送键即用这个形态」，但两个调用点从来没传过 square —— 文档与代码
        // 互相矛盾了很久。另外两个键尺寸不同（34 / 36）会在切换时跳一下。
        String composerActionKeys = stripComments(read(root, COMPOSER));
        int squareCount = countOf(composerActionKeys, "square = true");
        require(squareCount >= 2,
                COMPOSER + " 的发送键与停止键都必须传 square = true，实际只有 " + squareCount + " 处");
        require(!composerActionKeys.contains("size = 36.dp") && !composerActionKeys.contains("size = 34.dp"),
                COMPOSER + " 的两个动作键必须共用 ComposerActionSize，"
                        + "各写一个数字会重新出现\"切换时按钮跳一下\"");
        requireContains(composerActionKeys, "private val ComposerActionSize",
                COMPOSER + " 必须用一个常量统一两个动作键的边长");

        // ---- 18. 图片行必须能横向滑完，且三个 chip 不许均分 -----------------
        //
        // 两件事都是"能编译、能跑、但用起来不对"的类型，单测抓不到：
        //
        // (a) 图片行曾经是 `take(4)` + 「+K」角标 —— 第 5 张起只能看到"还有 N 张"，
        //     想确认自己发了哪几张得退出应用去翻相册。用户发的常是一串对比截图。
        // (b) 底部三个 chip 曾经都是 `weight(1f)` —— 「每次询问」只要 ~55dp 却和各
        //     占 1/3，忙时停止键再抢 ~36dp，「推理：自动」就被切成「推理: …」、
        //     模型名被切成「deepse」，都是**词中被切**，看着像坏了。
        requireContains(zhiImage, "horizontalScroll(",
                ZHI_IMAGE + " 的图片行必须横向可滑动：截断成「+K」会让屏幕外的图看不到，"
                        + "而用户要确认的正是自己到底发了哪几张");
        require(!zhiImage.contains("take(maxVisible)") && !zhiImage.contains("maxVisible"),
                ZHI_IMAGE + " 不得再用「最多 N 张 + K」的截断：横向滑动已经能看全，"
                        + "留着截断只会让人以为只发出了前几张");

        String composerChips = stripComments(read(root, COMPOSER));
        require(countOf(composerChips, "modifier = Modifier.weight(1f),") <= 1,
                "底排最多只能有一处 `weight(1f)`（模型那条）。"
                        + "多于一处就回到了\"三个 chip 均分宽度\"：长度固定的"
                        + "「每次询问」「推理：自动」会被切成「推理: …」这类半截词，"
                        + "而它们本来就只需要自己的自然宽度");
    }

    /** 子串出现次数。 */
    private static int countOf(String text, String needle) {
        int count = 0;
        int from = 0;
        while (true) {
            int at = text.indexOf(needle, from);
            if (at < 0) return count;
            count++;
            from = at + needle.length();
        }
    }

    /** tools/ 下每个工具自己声明的名字（`public String name() { return "Read"; }`）。 */
    private static Set<String> registeredToolNames(String root) throws Exception {
        Set<String> names = new HashSet<>();
        try (DirectoryStream<Path> stream =
                     Files.newDirectoryStream(Paths.get(root, TOOLS_DIR), "*.java")) {
            for (Path file : stream) {
                String text = new String(Files.readAllBytes(file),
                        java.nio.charset.StandardCharsets.UTF_8);
                Matcher matcher = TOOL_NAME.matcher(stripComments(text));
                while (matcher.find()) names.add(matcher.group(1));
            }
        }
        return names;
    }

    /** 脚本里 `tool("id", "Name", …)` 用到的工具名。 */
    private static Set<String> scriptedToolCalls(String scripted) {
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = TOOL_CALL.matcher(scripted);
        while (matcher.find()) names.add(matcher.group(1));
        return names;
    }
}
