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

    /** 模型选择面板。事故见 §19：搜索串误当成"要用的模型名"。 */
    private static final String MODEL_PICKER = SRC + "ui/dialogs/ModelPickerOverlay.kt";

    /**
     * 共用组件层。
     *
     * Miuix 的组件要在这一层转发一次（见 §21：加载圈走 ZhiLoadingIndicator），
     * 界面代码不直接 import Miuix —— 那样将来 Miuix 改签名会波及一片调用点。
     */
    private static final String COMMON = SRC + "ui/Common.kt";

    /**
     * 设置相关的界面模型。
     *
     * ⚠️ 与 [MODELS]（`model/UiModels.kt`）是**两个文件**：`ModelPickerState` 住在
     * 这里，`ChatItem` 住在那边。写混了会得到"字段不存在"这种看起来像代码坏了的报错。
     */
    private static final String SETTINGS_MODELS = SRC + "model/SettingsModels.kt";

    /** 浮层宿主：所有面板的接线点。 */
    private static final String OVERLAY_HOST = SRC + "ui/OverlayHost.kt";

    /** 宽窄屏的工作区布局（含底部导航栏与宽屏右栏）。 */
    private static final String WORKSPACE_LAYOUTS = SRC + "ui/WorkspaceLayouts.kt";

    /** 顶栏。Tab 行已从它移到底部，见 §20。 */
    private static final String TOP_BAR = SRC + "ui/TopBar.kt";

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

        // ---- 19. 模型面板：搜索已按用户要求移除，不得留下"没人用但还在"的字段 ----
        //
        // 这一节原本守的是「搜索串与要用的模型名必须是两个字段」（复用 `query` 的话，
        // 敲进搜索框的半个词会被当成模型名写进配置，服务端随后 400）。
        // 现在搜索框整个去掉了，那个 bug 的前提不存在了，所以改守它的**反面**：
        // 搜索留下的字段必须一并删掉，否则下一个人会以为还有搜索功能、
        // 或者更糟 —— 在某处接上 `search` 而又忘了它不参与过滤。
        String modelsForPicker = stripComments(read(root, SETTINGS_MODELS));
        require(!modelsForPicker.contains("val search: String"),
                SETTINGS_MODELS + " 不得再留 search 字段：搜索框已移除，留着是死字段");
        require(!modelsForPicker.contains("visibleModels"),
                SETTINGS_MODELS + " 不得再留 visibleModels：它是搜索的过滤器，搜索没了它就是死代码");

        String pickerFile = stripComments(read(root, MODEL_PICKER));
        requireContains(pickerFile, "OverlayBottomSheet(",
                MODEL_PICKER + " 必须用底部 Sheet 容器");
        requireContains(pickerFile, "ZhiTextField(",
                MODEL_PICKER + " 的模型名输入框必须走工程统一的 ZhiTextField");
        require(!pickerFile.contains("visibleModels"),
                "面板必须直接铺 models：搜索过滤已移除");
        require(!pickerFile.contains("onSearchChange"),
                "面板不得再收 onSearchChange：搜索框已移除");
        require(!pickerFile.contains("没有匹配"),
                MODEL_PICKER + " 不得再留「没有匹配」的空态：那只有搜索才可能为空，"
                        + "而调用方保证 models 非空 —— 留着就是一段永不执行的死分支");
        // ★ 卡片底色必须**显式**覆盖，且未选中用 secondaryContainer、选中用 primaryVariant。
        //
        // 这两条守的都是真实踩过的坑，不是风格偏好：
        // 1. Miuix 深色下 `CardDefaults` 默认色是 `surfaceContainer`，而
        //    `OverlayBottomSheet` 的默认背板色是 `background` —— **两者都是 #242424**。
        //    所以"照默认值写"的 Card 放进 sheet 里完全看不见（用户原话就是
        //    "背板和 card 背景一致"）。
        // 2. 选中态用蓝卡，令牌就是 Miuix 官方 Card 示例里那个 `primaryVariant`
        //    （深色 #0073DD）。用户指着示例里那张蓝卡说用它做高亮。
        requireContains(pickerFile, "if (selected) scheme.primaryVariant",
                MODEL_PICKER + " 的选中态必须是 primaryVariant 蓝卡（Miuix 官方 Card 示例的令牌）");
        requireContains(pickerFile, "else scheme.secondaryContainer",
                MODEL_PICKER + " 的未选中卡片必须显式用 secondaryContainer："
                        + "CardDefaults 默认的 surfaceContainer 与 sheet 背板 background 同值(#242424)，"
                        + "不覆盖的话未选中的卡片在背板上看不出来");
        require(pickerFile.contains("Card("),
                MODEL_PICKER + " 的模型行必须套 Miuix Card（带形状的容器），才能整块圆角变色");

        // ★ 文字色必须显式跟随卡片：`BasicComponentDefaults.titleColor()` 的默认值是
        // `onBackground`、`summaryColor()` 是 `onSurfaceVariantSummary`，**都不跟随卡片的
        // contentColor**（与 Material3 直觉相反）。不显式传的话蓝卡上会出现深色字。
        requireContains(pickerFile, "titleColor = BasicComponentDefaults.titleColor(",
                MODEL_PICKER + " 的标题色必须显式传：BasicComponent 的默认文字色不跟随卡片 contentColor，"
                        + "蓝卡上会变成深色字");
        requireContains(pickerFile, "if (selected) scheme.onPrimaryVariant",
                MODEL_PICKER + " 的选中行文字色必须是 onPrimaryVariant：蓝卡上要用浅蓝字");

        // ★ 无障碍语义不能因为去掉单选圈而丢。
        requireContains(pickerFile, "role = Role.RadioButton",
                MODEL_PICKER + " 必须保留 Role.RadioButton——这是同一个单选组的语义，"
                        + "不画单选圈不等于可以不告诉读屏软件");
        // 中灰陷阱：onSecondaryContainer 在深色下是 #7C7C7C，曾把它当选中前景色，
        // 导致"选中"看起来比未选中更暗。
        require(!pickerFile.contains("onSecondaryContainer"),
                MODEL_PICKER + " 不得把 onSecondaryContainer 当前景色："
                        + "它在深色下是 #7C7C7C 中灰，会让\"选中\"看起来比未选中更暗");
        require(!pickerFile.contains("startAction = {"),
                MODEL_PICKER + " 不得再放首字母头像：它既是手绘组件，"
                        + "又在同一配置下对所有模型取到同一个字母（deepseek-flash / deepseek-v4-pro 都是 D）");

        // ★ 结构照 Miuix 官方示例：小标题在卡片**外面**（走 SmallTitle），不用分隔线分组。
        requireContains(pickerFile, "ZhiSectionLabel(",
                MODEL_PICKER + " 的分组标题必须走 ZhiSectionLabel（= Miuix SmallTitle），"
                        + "而不是自己拼一行 Text");
        require(!pickerFile.contains("HorizontalDivider"),
                MODEL_PICKER + " 不得用分隔线分组：Miuix 官方示例靠卡片底色 + 间距分组，不画线");

        // 输入框的填充必须看得见 —— 同一个坑的另一半：
        // `TextFieldDefaults` 默认底色是 secondaryContainer(#434343)，覆写成
        // surfaceContainerHigh 就又是 #242424，填充直接融进背板。
        require(!pickerFile.contains("backgroundColor = scheme.surfaceContainerHigh"),
                MODEL_PICKER + " 不得把输入框底色覆写成 surfaceContainerHigh："
                        + "它是 #242424，与 sheet 背板同值、填充会消失；默认的 secondaryContainer 才对");

        String overlayHost = stripComments(read(root, OVERLAY_HOST));
        String vmForSearch = stripComments(read(root, VIEW_MODEL));
        require(!overlayHost.contains("setModelSearch"),
                OVERLAY_HOST + " 不得再把搜索接到 setModelSearch：搜索框已移除");
        require(!vmForSearch.contains("fun setModelSearch("),
                VIEW_MODEL + " 不得再留 setModelSearch：搜索框已移除");

        // 能力标签与收藏心形：上游目录只给 id + displayName（ModelCatalogClient
        // 只读 id/display_name/name），全工程也没有 favorites 概念。
        // 参考图里有这两块，但凭空造出来会让模型收到它并不支持的请求。
        require(!modelsForPicker.contains("favorite"),
                SETTINGS_MODELS + " 没有收藏的数据来源，不得凭空加一个 favorite 字段");

        // ---- 20. 模型面板不得加"单提供方下的死控件" -------------------------
        //
        // 参考图（rikkahub）里那些折叠箭头、吸顶分组头、底部提供方跳转条，成立的前提是
        // 它支持**多个提供方**；我们的面板永远只有一组。照截图补上就会多几个"点了没反应"
        // 的控件，所以这里把它们钉住，并要求代码里留下理由说明。
        require(!pickerFile.contains("stickyHeader"),
                MODEL_PICKER + " 不得加吸顶分组头：面板永远只有一组，吸顶没有东西可选");
        // ⚠️ 这一条必须查**原文**而不是 `stripComments` 的结果：理由说明就写在注释里，
        // 而 stripComments 会把注释删掉 —— 查 strip 后的文本会让断言恒为失败
        // （一个永远红着的测试和没有测试一样糟）。
        require(read(root, MODEL_PICKER).contains("画一个点了没用的控件比不画更糟"),
                MODEL_PICKER + " 必须保留\"不画死控件\"的理由说明："
                        + "否则以后有人照参考图补上折叠箭头，就多一个点了没反应的控件");

        // ---- 21. 点选即生效：高亮跟点击走、点完就存、按钮只负责收起 ----------
        //
        // 用户的原话是「应该是点击这个东西，他会高亮（蓝色），而不是点击使用模型高亮，
        // 而且选择该模型之后自动保存这个设置就可以吧使用模型改成完成之类的」。
        // 这四条都是「改错了也不编译失败、只是点起来不对」的类型。
        String commonFile = stripComments(read(root, COMMON));

        // 高亮必须取 `query`（= 要用的模型名），它在点击那一刻就被改过去。
        // 早先取的是 `currentModel`（**已保存**的值），而它只在按底部按钮时才变 ——
        // 于是"点了一行却什么都不亮"，用户会以为没点上。
        requireContains(pickerFile, "picked = picker.query",
                MODEL_PICKER + " 的高亮必须跟随 picker.query（点击即改），"
                        + "而不是 picker.currentModel（只有按按钮才改）—— "
                        + "后者会让\"点了一行却没有反馈\"");
        require(!pickerFile.contains("selected = model.id == picker.currentModel"),
                MODEL_PICKER + " 的高亮不得再判 picker.currentModel：那是已保存的值，"
                        + "点选之后要到按按钮才亮");

        // 点击那一刻就落盘，且**不关**面板（关掉就没法接着挑/看高亮了）。
        String vmForPick = stripComments(read(root, VIEW_MODEL));
        requireContains(vmForPick, "fun selectModel(",
                VIEW_MODEL + " 必须提供 selectModel：点击即保存用，与\"收起面板\"分开");
        requireContains(vmForPick, "if (target.isEmpty()) return",
                "selectModel 必须挡掉空白模型名：否则「完成」会把空串写进配置");
        // latest-wins：连点几下时上一个写入任务要被取消，否则落盘的可能是中间那一次。
        requireContains(vmForPick, "modelSaveJob?.cancel()",
                "模型落盘必须是 latest-wins（取消上一个任务）：并发写入的完成顺序不定，"
                        + "最后落盘的可能是中间点到的那个，而界面显示的是最后一次点的");

        // selectModel 不得关闭面板；applySelectedModel 必须关闭 —— 这正是两者的分工。
        int pickBody = vmForPick.indexOf("fun selectModel(");
        int applyBody = vmForPick.indexOf("fun applySelectedModel(");
        require(pickBody > 0 && applyBody > pickBody,
                VIEW_MODEL + " 里 selectModel 必须排在 applySelectedModel 之前，本断言按此顺序取函数体");
        String selectModelBody = vmForPick.substring(pickBody, applyBody);
        require(!selectModelBody.contains("closeModelPicker()"),
                "selectModel 不得关面板：关掉之后用户看不到高亮变没变，也没法接着挑下一个");
        require(vmForPick.substring(applyBody, applyBody + 800).contains("closeModelPicker()"),
                "applySelectedModel 必须关面板（它是「完成」按钮）");

        // 按钮文案与分工。
        requireContains(pickerFile, "\"完成\"",
                MODEL_PICKER + " 主按钮文案必须是「完成」：选择在点击那一刻已经存好，"
                        + "叫「使用模型」会让人以为不点它就不生效");
        requireContains(pickerFile, "\"添加 API\"",
                MODEL_PICKER + " 次按钮文案必须是「添加 API」");
        require(!pickerFile.contains("\"管理 API\""),
                MODEL_PICKER + " 不得再出现「管理 API」");
        requireContains(stripComments(read(root, OVERLAY_HOST)), "viewModel.newApiProfile()",
                OVERLAY_HOST + " 的「添加 API」必须直接进空白表单："
                        + "tab 栏已经承担了\"切到已有配置\"，停在列表页与按钮名字不符");

        // tab 栏：只有多于一条才画（一条时是死控件），且切换**不能**写 apiConfig。
        requireContains(pickerFile, "if (!picker.showProfileTabs) return",
                MODEL_PICKER + " 的 tab 栏必须按 showProfileTabs 门控："
                        + "只有一条 API 记录时它是死控件（点了切不到任何地方）");
        requireContains(pickerFile, "ZhiSegmentedTabs(",
                MODEL_PICKER + " 的 tab 栏必须走既有的 ZhiSegmentedTabs（Miuix TabRowWithContour）");
        // ⚠️ 这条守的是一个真会发生的 bug：`selectApiProfile` 会写 `apiConfig = state`，
        // 而 `apiConfig != null` 在本工程里的语义就是"API 配置弹窗打开"——
        // 在模型面板里复用它会在面板上凭空弹出一张配置页。
        int switchBody = vmForPick.indexOf("fun selectModelPickerProfile(");
        require(switchBody > 0, VIEW_MODEL + " 必须提供 selectModelPickerProfile");
        String switchFn = vmForPick.substring(switchBody, Math.min(switchBody + 1600, vmForPick.length()));
        require(!switchFn.contains("apiConfig ="),
                "selectModelPickerProfile 不得写 apiConfig：那个字段的语义是"
                        + "\"配置弹窗打开\"，写它会在模型面板上凭空弹出配置页");

        // 加载态：用 Miuix 的加载圈居中显示，且转发只经 Common.kt。
        requireContains(pickerFile, "ZhiLoadingIndicator(",
                MODEL_PICKER + " 的加载态必须用 ZhiLoadingIndicator（Miuix 加载圈）");
        requireContains(commonFile, "CircularProgressIndicator(",
                COMMON + " 的 ZhiLoadingIndicator 必须转发到 Miuix CircularProgressIndicator");
        requireContains(pickerFile, "PickerLoading(",
                MODEL_PICKER + " 必须有居中的加载态（圈 + 状态文字），而不是只留一行小字");

        // 高度：窗口高度必须取 LocalWindowInfo，**不能**取 BoxWithConstraints 的 maxHeight。
        //
        // ⚠️ 这条是实测出来的，不是风格偏好：最初用 BoxWithConstraints 算比例，结果面板
        // 反而变矮、`heightIn(min = …)` 完全不生效 —— sheet 测量内容时给的约束不是屏幕
        // 高度（它要先量一次内容才能决定自己多高）。Miuix 自己在 BottomSheetContentLayout
        // 里用的就是 LocalWindowInfo.current.containerDpSize.height。
        requireContains(pickerFile, "LocalWindowInfo.current.containerDpSize.height",
                MODEL_PICKER + " 的窗口高度必须取 LocalWindowInfo（Miuix 自己也用它）："
                        + "BoxWithConstraints 的 maxHeight 在这里是无效的");
        require(!pickerFile.contains("BoxWithConstraints"),
                MODEL_PICKER + " 不得用 BoxWithConstraints 算面板高度：sheet 测量内容时"
                        + "给的约束不是屏幕高度，实测那样算出来的最小高度完全不生效");
        // 列表上限按窗口高度取比例 —— 这才让面板"有空间时长高"，且仍然**有界**
        // （无界高度会让 LazyColumn 拿到 Infinity 约束直接崩，DialogScrollNestingTest）。
        requireContains(pickerFile, "val listMaxHeight = (windowHeight * ModelListHeightFraction)",
                MODEL_PICKER + " 的列表上限必须按窗口高度取比例，不能写死一个数");
        requireContains(pickerFile, "heightIn(max = listMaxHeight)",
                MODEL_PICKER + " 的列表必须保留 heightIn 上限：无界高度会让 LazyColumn 崩");
        requireContains(pickerFile, "windowHeight * LoadingHeightFraction",
                MODEL_PICKER + " 的加载态高度也必须按窗口高度给："
                        + "否则加载时面板很矮、加载完突然长高，还跳一下");

        // 动效：不许硬切。
        //
        // 用户反馈"很多地方看起来很生硬"。这里守两条最容易回退成硬切的：
        // 选中态的整块变色、以及加载态↔列表的整块替换。
        // ⚠️ 必须断到**卡片底色那一个** animateColorAsState，不能只查 `animateColorAsState(` ——
        // 同一行里还有标题色与副标题色两个动画，宽断言会被它们蒙过去：
        // 把底色的淡变删掉，测试照样绿。
        requireContains(pickerFile, "val cardColor by animateColorAsState(",
                MODEL_PICKER + " 的选中底色必须走 animateColorAsState："
                        + "硬切会让\"点一下整行啪地变蓝\"看起来很生硬");
        requireContains(pickerFile, "ZhiMotion.colorSpec",
                MODEL_PICKER + " 的动效必须用 ZhiMotion 的令牌，不能另写一条曲线："
                        + "那会与 Miuix 组件的手感不一致（理由见 Animations.kt 的完整说明）");
        requireContains(pickerFile, "AnimatedContent(",
                MODEL_PICKER + " 的加载态与列表之间必须有过渡（AnimatedContent）："
                        + "两块高度不同，硬切既是变色又是跳高");
        // ⚠️ 同样要断**实际用法**：`import androidx.compose.animation.togetherWith`
        // 那一行也含 "togetherWith"，只查这个词的话删掉用法测试照样绿。
        requireContains(pickerFile,
                "fadeIn(ZhiMotion.fadeInSpec) togetherWith fadeOut(ZhiMotion.fadeOutSpec)",
                MODEL_PICKER + " 的 AnimatedContent 必须显式给 transitionSpec"
                        + "（fadeIn togetherWith fadeOut），并复用 ZhiMotion 的时长");
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
