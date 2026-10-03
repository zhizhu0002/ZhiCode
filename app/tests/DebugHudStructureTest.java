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

    /**
     * 内容块 → 图片的解析。
     *
     * 它**曾经住在 `SessionReader` 里**，后来搬到 `model`：同一个块结构有两个来源
     * （会话文件的用户消息、以及工具结果的 `additionalContent`），而后者要由 `engine`
     * 调用，`engine` 的既有依赖方向是只依赖 `model`、从不 import `data`。
     */
    private static final String CHAT_IMAGE_BLOCKS = SRC + "model/ChatImageBlocks.kt";

    /** 引擎事件接口与控制器：工具结果回界面的唯一通道。 */
    private static final String ENGINE_CONTROLLER = SRC + "engine/ZhiEngineController.kt";

    /** 用户气泡与助手卡片。图片行的接入点在 `UserBubble`。 */
    private static final String MESSAGE_CARDS = SRC + "ui/chat/MessageCards.kt";

    /** 界面模型。工具卡的预览图字段（`ToolActivity.previews`）在这里（§30）。 */
    private static final String UI_MODELS = SRC + "model/UiModels.kt";

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

    /** 文件面板：列表留白与行距（用户反馈过"太紧凑"）。 */
    private static final String FILES_PANE = SRC + "ui/panes/FilesPane.kt";

    /** 侧栏：会话列表（删除/重排时行要不瞬移，见 §25）。 */
    private static final String SIDEBAR = SRC + "ui/Sidebar.kt";

    /** 变更面板：diff 文件列表（同上）。 */
    private static final String CHANGES_PANE = SRC + "ui/panes/ChangesPane.kt";

    /** 斜杠命令面板：打字时命令集收窄，落选的行要滑走（同上）。 */
    private static final String SLASH_PALETTE = SRC + "ui/composer/SlashPalette.kt";

    /** 顶栏。Tab 行已从它移到底部，见 §20。 */
    private static final String TOP_BAR = SRC + "ui/TopBar.kt";

    // ---- §26~§29：动效契约（浮层常驻 / 面板多态 / 终端不变量 / 选中态变色）--------

    /** 技能管理整页：6 个浮层 + 树展开 + 两处空态切换（§26/§27）。 */
    private static final String SKILLS_OVERLAY = SRC + "ui/dialogs/SkillsOverlay.kt";

    /** MCP 配置整页：4 个浮层 + TAB 内容过渡。 */
    private static final String MCP_CONFIG_OVERLAY = SRC + "ui/dialogs/McpConfigOverlay.kt";

    /** 二级页基座：`overlay` 槽位与 `rememberLastNonNull` 都住在这里。 */
    private static final String SETTINGS_SUB_PAGE = SRC + "ui/settings/SettingsSubPage.kt";

    /** 环境自检弹窗（退场期间内容曾被拆空）。 */
    private static final String ENVIRONMENT_OVERLAY = SRC + "ui/dialogs/EnvironmentOverlay.kt";

    /** 附加项目文件面板（同上）。 */
    private static final String ATTACH_FILE_OVERLAY = SRC + "ui/dialogs/AttachFileOverlay.kt";

    /** 终端面板：三态多态 + 「组合里至多一个 AndroidView」这条硬不变量（§28）。 */
    private static final String TERMINAL_PANE = SRC + "ui/panes/TerminalPane.kt";

    /** 终端外壳：扩展键与会话行的选中态变色（§29）。 */
    private static final String TERMINAL_CHROME = SRC + "ui/panes/TerminalChrome.kt";

    /** 终端宿主（纯 Java 的 FrameLayout）：ANSI 调色板在这里，不在 Compose 侧。 */
    private static final String TERMINAL_HOST = SRC + "../TermuxTerminalPane.java";

    /** 沙箱页：错误/空/列表三态 + 应用卡片的 animateItem（§26/§27）。 */
    private static final String SANDBOX_SCREEN = SRC + "ui/sandbox/ZhiSandboxScreen.kt";

    /** API 配置 / 角色卡 / 搜索服务三个列表页：选中行的标题色（§29）。 */
    private static final String API_CONFIG_OVERLAY = SRC + "ui/dialogs/ApiConfigOverlay.kt";
    private static final String ROLE_CARDS_OVERLAY = SRC + "ui/dialogs/RoleCardsOverlay.kt";
    private static final String SEARCH_SERVICES_OVERLAY = SRC + "ui/dialogs/SearchServicesOverlay.kt";

    /** 动效令牌表（§29 要确认 colorSpec 的出处仍写着）。 */
    private static final String ANIMATIONS = SRC + "ui/Animations.kt";

    /** 设置主页：两处折叠（§27）。 */
    private static final String SETTINGS_DIALOG = SRC + "ui/settings/SettingsDialog.kt";

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

    /**
     * 取出某个函数/枚举/组合块的**完整正文**（按花括号配对，不是取到文件末尾）。
     *
     * <p>为什么必须按块取：`contains("pressFeedbackType = PressFeedbackType.Sink")`
     * 这种断言在同一个文件里有**第二处合法用法**时会静默失效 ——
     * §29 的"根切换条要有下沉反馈"就这样漏过一次：把那一处的 `Sink` 改回 `None`，
     * 断言照样绿，因为 `FileRow` 那张卡也是 `Sink`。
     * 反向验证（teeth）里改坏 25 处、这一条没被抓住，才发现。
     *
     * @param signature 在 `code` 里唯一的那段签名（例如 "private fun FileRootSwitcher("）
     * @return 从签名起到配对花括号结束的子串；找不到或签名不唯一时返回空串
     *         （让调用处的断言去报"没有"，而不是猜一个出来）
     */
    private static String bodyOf(String code, String signature) {
        int at = code.indexOf(signature);
        if (at < 0) return "";
        if (code.indexOf(signature, at + 1) >= 0) return "";
        int depth = 0;
        boolean seen = false;
        for (int i = at; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '{') { depth++; seen = true; }
            else if (c == '}') {
                depth--;
                if (seen && depth == 0) return code.substring(at, i + 1);
            }
        }
        return code.substring(at);
    }

    /**
     * 从 `needle` 起取到**行尾**。
     *
     * <p>用于没有花括号的单行声明/赋值（例如 `val hasDetails = …`）——
     * {@link #bodyOf} 对这类文本会一路找到后面某个函数的 `{`，取出一块与断言无关的东西，
     * 于是断言看着在守、守的其实是别处（本仓踩过这个坑，见 §29 的注释）。
     */
    private static String lineAt(String code, String needle) {
        int at = code.indexOf(needle);
        if (at < 0) return "";
        int end = code.indexOf('\n', at);
        return end < 0 ? code.substring(at) : code.substring(at, end);
    }

    /**
     * 从 `needle` 起取到**下一个 `fun `**（用于接口里没有方法体的签名）。
     *
     * <p>同样是绕开 {@link #bodyOf} 在"没有 `{}` 的声明"上的失效：接口方法后面紧跟着
     * 的合法 `{` 属于别的函数。
     */
    private static String signatureAt(String code, String needle) {
        int at = code.indexOf(needle);
        if (at < 0) return "";
        int end = code.indexOf("fun ", at + needle.length());
        return end < 0 ? code.substring(at) : code.substring(at, end);
    }

    /**
     * 断言某段正文里**不出现**某段文本（{@link #requireContains} 的反面）。
     *
     * <p>它守的是"这一处不许再有硬切"。用整文件 `contains` 做不到 ——
     * 同一个文件里往往还有别的合法用法。
     */
    private static void requireAbsentIn(String body, String banned, String message) {
        require(!body.contains(banned), message);
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
        // ⚠️ 每一位成员必须有**自己**的 Box（手指追踪挂它、菜单锚点也在它里面）。
        //
        // `anchoredMenu` 走 `Modifier.absoluteOffset` 定位，而 absoluteOffset 的坐标是
        // 相对**直接父节点**的。回合容器（`TurnLayout` 之后一层助手回合 = 一个 Column）
        // 把多条消息并在一起之后，如果菜单锚点被提到那个 Column 下面，
        // 第 2 条之后的菜单就会整体偏掉前面所有成员的高度 —— 这正是"改层级时最容易漏"的一处，
        // 而且只有长按非首条消息才会暴露。
        require(chatList.contains("Box(modifier = Modifier.fillMaxWidth().then(finger.modifier))"),
                "每一位成员必须有自己的 Box 承载手指追踪（Box(modifier = Modifier.fillMaxWidth()"
                        + ".then(finger.modifier))）：菜单锚点与手指坐标必须在同一个坐标系里");

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
        // 这一条原来直接钉 `scheme.errorContainer` 出现在 MessageBar.kt 里。
        // 现在错误那一档改走与沙箱页共用的通知条分量，配色搬到了 [ZhiNoticeBar]，
        // 但**不变式没变**：错误必须是主题里的 errorContainer，不能写死红色。
        // 所以断言拆成两半，并且加一条"路由"断言 —— 否则把 MessageBar 的错误分支
        // 整个删掉，这条也照样过（它只要求"某处有 errorContainer"）。
        requireContains(messageBar, "ZhiNoticeBar(",
                "错误提示必须走共用的通知条分量（ZhiNoticeBar），与沙箱页同款");
        requireContains(messageBar, "ZhiNoticeTone.ERROR",
                "错误那一档必须显式传 ZhiNoticeTone.ERROR，否则会渲染成普通提示色");
        String commonForNotice = stripComments(read(root, COMMON));
        requireContains(commonForNotice, "scheme.errorContainer",
                "通知条的错误底色必须走主题的 errorContainer，不能在代码里写死红色");
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
        // 解析器本身住在 `model/ChatImageBlocks.kt`（见那里的说明），
        // 这里分两半守：**解析在 model、调用在 SessionReader**。
        String chatImageBlocks = stripComments(read(root, CHAT_IMAGE_BLOCKS));
        requireContains(chatImageBlocks, "fun readChatImageBlocks(",
                CHAT_IMAGE_BLOCKS + " 必须能从内容块数组里读回 image 块");
        requireContains(sessionReader, "readChatImageBlocks(content)",
                SESSION_READER + " 读历史时必须调用共享的那份解析（不许自己再写一遍："
                        + "两份实现迟早不一致，而失败模式是「图没出来」，没有报错");
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

        // 加载态：用 Miuix 的加载指示器居中显示，且转发只经 Common.kt。
        requireContains(pickerFile, "ZhiLoadingIndicator(",
                MODEL_PICKER + " 的加载态必须用 ZhiLoadingIndicator");
        // ⚠️ 换过实现：先是 `CircularProgressIndicator`（弧长会变的那种），
        // 观感在居中放大时偏笨重，改用 Miuix 的轨道点式 `InfiniteProgressIndicator`。
        // 断言绑的是"必须转发到 Miuix 的加载指示器"，不绑具体哪一个 ——
        // 但两个都要是 Miuix 的，不能自绘。
        requireContains(commonFile, "InfiniteProgressIndicator(",
                COMMON + " 的 ZhiLoadingIndicator 必须转发到 Miuix 的加载指示器"
                        + "（当前用 InfiniteProgressIndicator：细环 + 轨道点）");
        requireContains(pickerFile, "PickerLoading(",
                MODEL_PICKER + " 必须有居中的加载态（指示器 + 状态文字），而不是只留一行小字");

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
        // 列表上限按面板高度取（**有界**是硬性要求：无界高度会让 LazyColumn 拿到
        // Infinity 约束直接崩，DialogScrollNestingTest 守着这条）。
        requireContains(pickerFile, "val listMaxHeight = sheetHeight * 0.8f",
                MODEL_PICKER + " 的列表上限必须由面板高度推出，不能写死一个数");
        requireContains(pickerFile, "heightIn(max = listMaxHeight)",
                MODEL_PICKER + " 的列表必须保留 heightIn 上限：无界高度会让 LazyColumn 崩");

        // 面板**定高**，不随模型条数自适应。
        //
        // 用户的原话：「可以把 sheet 写高一点，（无论模型只有 1 还是 2 个）不要自适应」。
        // 自适应的问题很具体：只有 1~2 个模型时面板缩成薄薄一条，加载指示器挤在一条缝里，
        // 而且**切换 API 时高度会跳**（1 个模型 → 5 个模型）。
        requireContains(pickerFile, "height(sheetHeight)",
                MODEL_PICKER + " 的面板内容必须定高（height(sheetHeight)）："
                        + "自适应会让只有 1~2 个模型时缩成一条、切 API 时高度跳动");
        requireContains(pickerFile, "val sheetHeight = (windowHeight * SheetHeightFraction)",
                MODEL_PICKER + " 的定高值必须按窗口高度算（并 clamp 上下限），不能写死一个数");
        // 定高之后中间那块要 weight 吃掉余量，否则余量会掉到**按钮下面**变成一条空白。
        requireContains(pickerFile, "Modifier.weight(1f).fillMaxWidth()",
                MODEL_PICKER + " 定高列里的中间内容必须 weight(1f)："
                        + "不 weight 的话按钮上方会留一条空白");

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

        // ---- 22. 切 tab 要"点了立刻切"，且不得重建面板、不得丢滚动位置 -------------
        //
        // 用户先后提了三次，这是同一条链上的三个毛病：
        //  · 「每次切换对话那一栏的 TAB，对话每次都会回到最上层」；
        //  · 「使整个软件使用起来很卡」；
        //  · 「点击之后过了大约 0.1~0.2s 才切换页面（此时切换是流畅的）」。
        //
        // 根因与修法（每一步都有实测支撑，数字见各自断言里的说明）：
        //  1. `AnimatedContent(targetState = state.tab)` 会销毁离场面板、重建入场面板；
        //  2. 页码曾有**两个真源**（`rememberPagerState` 与 `state.tab` 各恢复一份）；
        //  3. 点击后的滞后来自三处叠加：suspend 的 `scrollToPage`（212ms）、
        //     `LaunchedEffect` 晚一轮组合才派发（39ms）、落定弹簧（116ms）；
        //  4. 最后剩下的 ~116ms 是目标面板**首次测量布局**（终端页含原生 View）堵在点击路径上。
        //
        // 最终形态：`HorizontalPager` + 页码单一真源 + 组合期非挂起
        // `requestScrollToPage` + 官方弹簧落定 + `beyondViewportPageCount = tabs.size`
        // 预先摆放三页。实测「抬起 → 切完」263.7ms → **29.8ms**，
        // 转场 avg 8.8ms / max 16.7ms / janks 0（官方 Miuix 基准 8.3ms / 11ms / 0）。
        //
        // ⚠️ 落定用的是**官方弹簧**而不是 `snap()`。中途确实改过 `snap()`：那时动画
        // 显得卡，是因为面板在重建（单帧 458ms）+ 请求是挂起的（212ms 死等）。
        // 根因修掉后帧率已与官方持平（8.3ms），动画重新变成加分项 —— 于是恢复成
        // 官方自己的那一条（`TabRowSection.kt:58-71`），瞬时落定反而与官方手感不一致。
        // **但它只在跑得动的时候才是加分项**：帧数据若退化，第一件该做的就是换回 `snap()`。
        String layouts = stripComments(read(root, WORKSPACE_LAYOUTS));
        requireContains(layouts, "HorizontalPager(",
                WORKSPACE_LAYOUTS + " 的工作区面板必须用 HorizontalPager："
                        + "AnimatedContent 会销毁离场面板（滚动位置丢失 + 单帧 458ms 的重建）");
        requireContains(layouts, "beyondViewportPageCount = tabs.size",
                WORKSPACE_LAYOUTS + " 必须让**三页全部**预先组合并摆放"
                        + "（beyondViewportPageCount = tabs.size）："
                        + "只要 1 时相邻页只是被组合、未必被摆放，切过去那一刻要现场测量布局"
                        + "（终端页含原生 AndroidView），实测主线程被堵 116ms、"
                        + "「抬起 → 切完」要 263.7ms；改成 tabs.size 后降到 29.8ms");
        // 落定用**官方那条弹簧**，不是 snap()。
        //
        // 这条断言的方向中途翻转过一次：为了让「点完等一会儿才切换」消失，这里曾经
        // 要求 `snapAnimationSpec = snap()`。那个 263.7ms 的真身是「挂起的
        // scrollToPage 死等 layout 依赖」+「面板首次测量布局堵在点击路径上」，
        // 两处都在别处修掉了（见上面两条断言）。修掉之后每秒能画的帧数与官方一致，
        // 官方自己就是用这条 spring 的（`example TabRowSection.kt:58-71`），
        // 于是落定交还给官方 —— 瞬时落定是"跑不动时的补救"，不是目标形态。
        //
        // ⚠️ 因此这里**只断正面形态，不断"禁止 snap()"**：帧数据万一退化，
        // 正确动作恰恰是把它换回 `snap()`（`WorkspaceLayouts.kt` 里那段 ⚠️ 注释
        // 写的就是这件事）。禁止它会让那条退路变成"改了测试才能走"的路。
        requireContains(layouts, "snapAnimationSpec = PagerNavigationSpringSpec",
                WORKSPACE_LAYOUTS + " 的落定必须用官方那条弹簧（PagerNavigationSpringSpec）："
                        + "官方 example 的 TabRowSection 就是它，瞬时落定会与官方手感不一致。"
                        + "⚠️ 它只在**跑得动**时才是加分项 —— 帧数据退化时应当换回 snap()，"
                        + "那时要连同这条断言一起改，而不是让测试挡住退路");
        requireContains(layouts, "requestScrollToPage(requestedPage)",
                WORKSPACE_LAYOUTS + " 必须用非挂起的 requestScrollToPage："
                        + "scrollToPage 是 suspend 的，要 awaitScrollDependencies，实测多花 212ms");
        // 反向：既然 Pager 已经保住了面板，就不该再出现 AnimatedContent 那套。
        require(!layouts.contains("AnimatedContent("),
                WORKSPACE_LAYOUTS + " 不应再用 AnimatedContent 切面板："
                        + "它和 Pager 是两套互相打架的机制，留着会让面板又被销毁一次");
        // 反向：不得用 suspend 的 scrollToPage（应用空闲不产帧，它会一直等 layout 依赖）。
        require(!layouts.contains("scrollToPage(target)"),
                WORKSPACE_LAYOUTS + " 不得再用 suspend 的 scrollToPage："
                        + "实测它自己就要 212ms");
        // 单一真源：页码必须从 state.tab 推导（rememberPagerState 会与它打架）。
        requireContains(layouts, "PagerState(currentPage = tabs.indexOf(state.tab)",
                WORKSPACE_LAYOUTS + " 的 Pager 页码必须从 state.tab 推导（单一真源）："
                        + "用 rememberPagerState 会和 state.tab 各存一份，恢复后可能对不上");
        require(!layouts.contains("rememberPagerState"),
                WORKSPACE_LAYOUTS + " 不能用 rememberPagerState：它是 rememberSaveable，"
                        + "会与 state.tab 这条恢复路径打架");

        // ---- 23. 文件列表的留白走工程令牌，且行距只在一处给 ----------------------
        //
        // 用户反馈「文件一栏做的太紧凑了，列表可以宽散一点」。原来左右只留 4dp，
        // 而行距在**两个地方**各给了一次（LazyColumn 无关 + Card 的 padding(vertical = 1.dp)）——
        // 后一个几乎等于没有，还让"以后调行距要改两处"。
        String filesPane = stripComments(read(root, FILES_PANE));
        requireContains(filesPane, ".padding(horizontal = ZhiSpace.m)",
                FILES_PANE + " 的列表左右留白必须走 ZhiSpace.m（12dp）："
                        + "它的注释写的就是\"列表左右留白\"，之前那 4dp 实测太挤");
        requireContains(filesPane, "Arrangement.spacedBy(ZhiSpace.xs)",
                FILES_PANE + " 的行距必须由 LazyColumn 的 spacedBy 统一给");
        require(!filesPane.contains("padding(vertical = 1.dp)"),
                FILES_PANE + " 不得再在行上加 padding(vertical = 1.dp)："
                        + "行距只在 spacedBy 一处给，两处会给调参带来两个入口");

        // ---- 24. 非对话面板必须自己顶开顶栏高度 --------------------------------
        //
        // 事故：用户截图里文件列表"上面有一行被裁掉"，而面板的头部（"文件 · N 项"、
        // 向上按钮、面包屑）整个不见了。
        //
        // 根因是**两句互相矛盾的注释**：AppScaffold 的 content lambda 有意丢掉了
        // `padding.top`（为了顶栏 blur 有内容可采样），并注明"各面板自己用
        // TopBarTotalInset 在内容头部留白"—— 而 `TopBarTotalInset` 这个常量**根本不存在**；
        // 同时 WorkspaceLayouts 那边写着"顶栏已占布局高度，面板不需要手工让位"。
        // 照着后一句做，面板就从 y=0 开始画，头部被顶栏盖住。
        //
        // ⚠️ 这条守卫断的是"AppScaffold 丢掉了 top padding"这个**前提**，
        // 以及"非对话面板补了 inset"这个**结论**。只断结论的话，哪天前提改了
        // （有人把 padding.top 加回来）就会变成双重留白，而测试照样绿。
        //
        // ⚠️⚠️ 凡是要查**注释里那句话**的，都必须用 `read` 的**原文**，
        // 不能用 `stripComments` 的结果 —— 注释正是被它删掉的东西。
        // （这个坑我在这份文件里踩过两次了：断言恒为真、看着在守其实什么都没守。）
        String scaffoldRaw = read(root, APP_SCAFFOLD);
        String layoutsRaw = read(root, WORKSPACE_LAYOUTS);
        String scaffoldPad = stripComments(scaffoldRaw);

        // 前提：那条 padding 里**只有** bottom，没有 top。
        requireContains(scaffoldPad, "bottom = padding.calculateBottomPadding(),",
                APP_SCAFFOLD + " 的 content lambda 必须保留 bottom padding");
        require(!scaffoldPad.contains("top = padding.calculateTopPadding()"),
                APP_SCAFFOLD + " 的 content lambda 不得再把 top padding 加回来："
                        + "加了之后非对话面板又补一份 inset 就是双重留白。"
                        + "真要加回来，得同时删掉下面那条『面板自己顶开』的断言");
        // 结论：非对话面板自己顶开。
        requireContains(layouts, "padding(top = TopBarInsetWithTabs)",
                WORKSPACE_LAYOUTS + " 的非对话面板必须自己顶开顶栏高度："
                        + "Scaffold 丢掉了 padding.top，不顶开的话面板头与第一行会被顶栏盖住");
        // ---- 顶开的高度必须**包含状态栏** ------------------------------------
        //
        // 真机症状（用户：「终端和对话划到最顶上，顶部栏把这些东西全部遮盖了」）：
        // 顶栏真实高度 = 状态栏 + 52 + Tab 行，而 `TopBarInsetWithTabs` 原先只算了后两项。
        // `SmallTopAppBar` 内部是**无条件**加
        // `windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))` 的
        // ——`defaultWindowInsetsPadding = false` 只关掉横向那两条，纵向关不掉。
        // 而 Miuix Scaffold 的 `bodyContentPlaceable.place(0, 0)` 说明内容从屏幕 y=0 起排，
        // 于是少算的那一段正好等于第一行被盖住的高度。
        require(layouts.contains("WindowInsets.systemBars.only(WindowInsetsSides.Top)"),
                WORKSPACE_LAYOUTS + " 的顶栏让位必须加上状态栏 inset："
                        + "顶栏自己无条件消费了 systemBars 的 top，少算这一段就是第一行被盖住");
        // ⚠️ 这一条要断言**表达式本身**，不能只查"文件里出现了 systemBars"。
        // 只查前者的话，把 `+ topBarWindowInset()` 从常量里删掉、只留那个私有函数，
        // 守卫照样通过 —— 那正是第一次反向验证时漏掉的情形。
        require(layouts.contains("+ TopBarTabRowPadding + topBarWindowInset()"),
                WORKSPACE_LAYOUTS + " 的 TopBarInsetWithTabs 必须真的把状态栏那段加上"
                        + "（`+ TopBarTabRowPadding + topBarWindowInset()`）："
                        + "定义了 inset 函数却不在和里加，等于没做");
        require(layouts.contains("internal val TopBarInsetWithTabs: Dp")
                        && layouts.contains("@Composable get()"),
                WORKSPACE_LAYOUTS + " 的 TopBarInsetWithTabs 必须是 @Composable get()（读得到窗口 inset），"
                        + "不能再是一个编译期常量 —— 状态栏高度各家不同，写死必然在某些机型上错");
        // 那句骗过人的注释不得复活（查**原文**，注释会被 stripComments 删掉）。
        require(!layoutsRaw.contains("面板不需要手工让位"),
                WORKSPACE_LAYOUTS + " 不得再写『面板不需要手工让位』："
                        + "这句话是错的（Scaffold 丢掉了 top padding），照着做会让面板头部被盖住");
        require(!scaffoldRaw.contains("TopBarTotalInset"),
                APP_SCAFFOLD + " 不得再引用 TopBarTotalInset：那个常量不存在，"
                        + "注释指向一个不存在的常量比没有注释更糟（本次事故就是这么来的）");

        // ---- 25. 列表增删/移动不得硬切，展开收起走官方裸默认 ----------------------
        //
        // 来源：用户「重写所有动画」那一轮里点名的两类缺口。两类的共同点是
        // **删掉/改错都不会编译失败，也不会让界面坏掉**，只表现为"硬切一下"，
        // 所以只能静态钉住。
        //
        //  ① **列表项的增删/移动**。四个列表都给了 `key`，于是很容易以为"这就够了"——
        //     但 `key` 只能让 Compose 认出"还是那一行"，**位置变化的补间是
        //     `animateItem()` 做的**。少了它，删掉一条之后下面所有行会"啪"地整体
        //     上跳一位；打字收窄命令列表时，剩下的命令会瞬移而不是滑过去。
        //
        //  ② **展开/收起**。斜杠面板与附件条传的是 `ZhiMotion.sizeSpec`
        //     （`tween(200, DecelerateEasing(1.5))`）。那套数字**本身抄自 Miuix**，
        //     但抄的是**弹窗位移退出**那条曲线；而 Miuix 自己所有"整块展开/收起"
        //     走的是 `fadeIn() + expandVertically()` 的 spring
        //     （`example/AppContent.kt:529`、`component/SwitchSection.kt:80`，
        //     一个字都不多）。拿退出曲线去做展开，正是用户说的"很割裂"的来源。
        //
        // ⚠️ 这里**不**要求 `Composer.kt` 里那处横向的停止键揭示（`expandHorizontally`
        // + `scaleIn`）也改成裸默认：它是**插进 Row** 的元素，宽度从 0 长出来是它
        // 让旁边发送键平移让位的机制 —— 换成不改变尺寸的 `fadeIn() + scaleIn()`，
        // 发送键会在动画开始那一瞬间跳位，比现在更硬。官方那处
        // （`SuperSearchBar.kt:257`）是**覆盖式**的 trailingIcon，不挤动邻居，不通用。
        String sidebar = stripComments(read(root, SIDEBAR));
        String changesPane = stripComments(read(root, CHANGES_PANE));
        String slashPalette = stripComments(read(root, SLASH_PALETTE));
        // `composer` 复用本方法前面（§… 处）已经读好的那一份：同一个
        // `stripComments(read(root, COMPOSER))`，不必再读一遍盘。
        // ① 四处列表的增删/移动要平滑。
        requireContains(sidebar, "modifier = Modifier.animateItem()",
                SIDEBAR + " 的会话列表行必须挂 animateItem()：删一条会话后，"
                        + "下面所有行会整体上跳一位（硬切）—— `key = it.id` 只让 Compose "
                        + "认出身份，位置补间是 animateItem() 做的");
        requireContains(filesPane, "modifier = Modifier.animateItem()",
                FILES_PANE + " 的文件列表行必须挂 animateItem()：删一个文件后"
                        + "下面所有行会整体上跳一位");
        requireContains(changesPane, "modifier = Modifier.animateItem()",
                CHANGES_PANE + " 的 diff 文件列表行必须挂 animateItem()："
                        + "重新算 diff 换掉文件集时，同名行应当滑到新位置而不是瞬移");
        requireContains(slashPalette, "Modifier.animateItem()",
                SLASH_PALETTE + " 的命令行必须挂 animateItem()：打字时命令集一直在收窄，"
                        + "命中的行要滑上去、落选的行淡出，而不是每行原地闪一下");
        // ② 展开/收起交还官方裸默认。
        //
        // 用 countOf 而不是 contains：这里要的是**两处都改**（斜杠面板 + 附件条），
        // 只改一处的话 contains 照样绿 —— 那正是"改一半"最容易漏掉的形态。
        require(countOf(composer, "enter = fadeIn() + expandVertically()") >= 2,
                COMPOSER + " 的斜杠面板与附件条展开都必须走官方裸默认"
                        + "（fadeIn() + expandVertically()，当前只找到 "
                        + countOf(composer, "enter = fadeIn() + expandVertically()") + " 处）："
                        + "出处 example/AppContent.kt:529、component/SwitchSection.kt:80");
        require(countOf(composer, "exit = fadeOut() + shrinkVertically()") >= 2,
                COMPOSER + " 的同两处收起也必须走官方裸默认（fadeOut() + shrinkVertically()）");
        // 反向：竖向展开不得再传 ZhiMotion 的那条曲线。
        //
        // ⚠️ 只断竖向的两个 —— `Composer.kt` 里 `ZhiMotion.sizeSpec` 还有合法用途
        // （`animateContentSize` 在流式期间管输入器的高度，届时改了它没有收益）；
        // 泛断"不许出现 ZhiMotion.sizeSpec"会把那些一起误伤。
        require(!composer.contains("expandVertically(ZhiMotion.sizeSpec)"),
                COMPOSER + " 的竖向展开不得再传 ZhiMotion.sizeSpec："
                        + "那条曲线抄的是**弹窗位移退出**，而展开在 Miuix 里走 spring");
        require(!composer.contains("shrinkVertically(ZhiMotion.sizeSpec)"),
                COMPOSER + " 的竖向收起不得再传 ZhiMotion.sizeSpec（同上）");

        // ---- 26. Miuix 浮层的★★常驻契约★★ -------------------------------------
        //
        // 这一条是用户「很多地方都没有动画（参考 miuix 例子里的 bottomsheet /
        // dialogwindow）」的直接根因，也是本轮里唯一**光看代码看不出来**的一条。
        //
        // 事实链（全部核过本仓 `~/projects/miuix`，版本 0.9.4，与 app 依赖一致）：
        //  · `layout/DialogContentLayout.kt:123,133-166` 里退场是
        //    遮罩 `tween(250)` + 内容 `tween(260, DecelerateEasing(1.5))`；
        //  · 同文件 `:168` 是 `if (!show && !internalVisible.value) return`
        //    —— **先播完退场才早退**；
        //  · `overlay/OverlayDialog.kt:80-81`、`OverlayBottomSheet.kt:81-82` 给外层
        //    `DialogLayout` 传的是 `EnterTransition.None` / `ExitTransition.None`，
        //    所以**没有第二道兜底**；
        //  · `utils/MiuixPopupUtils.kt:226-231` 的 `onDispose` 直接把 `showState`
        //    掰 false，不给动画机会。
        //
        // 结论：**调用点写 `if (open) { Floating(...) }` 就是硬切** —— 组件一被移除，
        // 那个 `Animatable` 随 composition 一起走，退场从未启动。
        // 官方 demo（`example/component/DialogSection.kt:107-131`、
        // `BottomSheetSection.kt:116-129`）一律 `show = <状态>`，从不套 `if`。
        String skills = stripComments(read(root, SKILLS_OVERLAY));
        String mcp = stripComments(read(root, MCP_CONFIG_OVERLAY));
        String common = stripComments(read(root, COMMON));
        // ① 浮层组件内部不得再硬编码 `show = true`（那是"调用点必须包 if"的写法）。
        for (String[] pair : new String[][] {
                {SKILLS_OVERLAY, skills}, {MCP_CONFIG_OVERLAY, mcp},
        }) {
            require(!pair[1].contains("show = true,"),
                    pair[0] + " 里不得再出现 `show = true`：浮层的 `show` 必须由调用方传进来 —— "
                            + "硬编码 true 意味着调用点只能用 `if (open) { … }` 包住它，"
                            + "而那样退场动画永远不会播（见 §26 的事实链）");
        }
        // ② 数据驱动的浮层必须过 rememberLastNonNull：退场那 250~260ms 里状态已是 null。
        //
        // ⚠️ 这里**逐个点名**，不能只数个数、更不能只 `contains`：
        //  · `contains` 会被文件里别的合法调用点骗过（`shownDetail` 一直都在）；
        //  · `countOf >= 3` 也会 —— 当时有 6 处，删掉一处还剩 5，仍然 ≥3。
        //    teeth 第一轮就是这么漏的，两处都改成点名之后才抓住。
        // 每个串都是「变量名 + 它兜的那个字段」的完整语句，改错任何一处都会红。
        String[] skillRemembered = {
                "val shownDetail = rememberLastNonNull(state.detail)",
                "val editing = rememberLastNonNull(state.editing)",
                "val fileDraft = rememberLastNonNull(state.fileDraft)",
                "val deleting = rememberLastNonNull(deleteTarget)",
                "val createDraft = rememberLastNonNull(state.createForm)",
                "val urlDraft = rememberLastNonNull(state.urlDraft)",
        };
        for (String line : skillRemembered) {
            requireContains(skills, line,
                    SKILLS_OVERLAY + " 缺一处「最后一次非空」兜底：`" + line + "`。"
                            + "退场期间那 250~260ms 里 state 字段已是 null，"
                            + "直接用会让离场那一屏画成空壳");
        }
        String[] mcpRemembered = {
                "val shownForm = rememberLastNonNull(form)",
                "val shownErrorDetail = rememberLastNonNull(errorDetail)",
                "val shownImportText = rememberLastNonNull(config.importText)",
        };
        for (String line : mcpRemembered) {
            requireContains(mcp, line,
                    MCP_CONFIG_OVERLAY + " 缺一处「最后一次非空」兜底：`" + line + "`（同上）");
        }
        // ③ 长按动作菜单：`open` 入参 + 六个调用点不得再用 `if` 包住。
        requireContains(common, "open: Boolean,",
                COMMON + " 的 ZhiAnchoredActionMenu 必须收 `open: Boolean` 并转发给 "
                        + "OverlayDropdownPopup 的 `show`：`ListPopupLayout.kt:120` 那句 "
                        + "`if (!show && !internalVisible.value) return` 同样是"
                        + "「先播完退场才早退」");
        requireContains(common, "show = open,",
                COMMON + " 的 ZhiAnchoredActionMenu 必须把 `open` 转发给 `show = open`");
        for (String[] pair : new String[][] {
                {SKILLS_OVERLAY, skills}, {MCP_CONFIG_OVERLAY, mcp},
                {SANDBOX_SCREEN, stripComments(read(root, SANDBOX_SCREEN))},
        }) {
            require(!pair[1].contains("if (menuAt != null) {")
                            && !pair[1].contains("if (menuOpen) {"),
                    pair[0] + " 的动作菜单不得再用 `if` 包住：改成 `open = menuAt != null` "
                            + "且无条件组合，否则长按菜单的收起是硬切");
        }
        // ④ B 节：退场期间"内容先被拆空"的几处，不得再有 `if (x == null) return@OverlayX`。
        //
        // 这 9 处只是**漏用了自家原语**（6 个设置类 overlay 早已在用），
        // 所以断言直接盯着那几句早退。
        for (String[] pair : new String[][] {
                {DIALOGS, stripComments(read(root, DIALOGS))},
                {ENVIRONMENT_OVERLAY, stripComments(read(root, ENVIRONMENT_OVERLAY))},
                {ATTACH_FILE_OVERLAY, stripComments(read(root, ATTACH_FILE_OVERLAY))},
                {MODEL_PICKER, stripComments(read(root, MODEL_PICKER))},
                {ZHI_IMAGE, stripComments(read(root, ZHI_IMAGE))},
        }) {
            require(!pair[1].contains("if (request == null) return@OverlayDialog")
                            && !pair[1].contains("if (plan == null) return@OverlayDialog")
                            && !pair[1].contains("if (picker == null) return@OverlayDialog")
                            && !pair[1].contains("if (!open) return@OverlayDialog")
                            && !pair[1].contains("if (!open) return@OverlayBottomSheet")
                            && !pair[1].contains("val current = picker ?: return@OverlayBottomSheet")
                            && !pair[1].contains("val shown = image ?: return@OverlayDialog"),
                    pair[0] + " 不得在浮层退出时把内容拆空（`if (x == null) return@OverlayX`）："
                            + "那会让退场那 250~260ms 只剩一张空壳在缩/滑 —— 改读 rememberLastNonNull");
        }

        // ---- 27. 面板/页面的多态切换必须有过渡 --------------------------------
        //
        // 与 §26 相反：这些地方**不是**浮层，退场时组件本来就会被销毁，
        // 所以要用 `AnimatedContent` 或 `AnimatedVisibility` 显式给过渡。
        // 共同点同样是"删掉不会编译失败、界面也不坏，只是硬切一下"。
        String workspaceLayouts = stripComments(read(root, WORKSPACE_LAYOUTS));
        String changesPane2 = stripComments(read(root, CHANGES_PANE));
        String filesPane2 = stripComments(read(root, FILES_PANE));
        String terminalPane = stripComments(read(root, TERMINAL_PANE));
        String sandbox = stripComments(read(root, SANDBOX_SCREEN));
        String settingsDialog = stripComments(read(root, SETTINGS_DIALOG));
        String settingsSubPage = stripComments(read(root, SETTINGS_SUB_PAGE));
        // ① 宽屏副栏与窄屏用**同一套** Pager 机制（保留相邻页组合 + 官方弹簧）。
        //
        // 宽屏原先还是 `when (tab)` 硬切，切一次就重建离场/入场面板 ——
        // 终端要把原生 AndroidView 重新挂上去。这里要求两处都出现
        // `beyondViewportPageCount` 与 `PagerNavigationSpringSpec`。
        //
        // ⚠️ 光数个数不够：两处的值必须是**页数**。改成 0（= 不预摆放）时
        // `countOf` 照样是 2 —— teeth 实测漏过一次。所以断言值本身。
        requireContains(workspaceLayouts, "beyondViewportPageCount = tabs.size,",
                WORKSPACE_LAYOUTS + " 的窄屏 Pager 必须 `beyondViewportPageCount = tabs.size`："
                        + "只有 1 时相邻页只是被组合、未必被摆放，切换那一刻才付首次测量的钱"
                        + "（实测过单帧 116.7ms）");
        requireContains(workspaceLayouts, "beyondViewportPageCount = secondaryTabs.size,",
                WORKSPACE_LAYOUTS + " 的宽屏副栏 Pager 必须 `beyondViewportPageCount = "
                        + "secondaryTabs.size`（同上，值不能是 0 或 1）");
        require(countOf(workspaceLayouts, "snapAnimationSpec = PagerNavigationSpringSpec") >= 2,
                WORKSPACE_LAYOUTS + " 的宽窄两套 Pager 都必须用官方那一条弹簧落定"
                        + "（当前 " + countOf(workspaceLayouts, "snapAnimationSpec = PagerNavigationSpringSpec")
                        + " 处）");
        // 反向：宽屏副栏不得再有 `PaneHost` 的裸 `when` 直挂 —— 那正是硬切的来源。
        require(!workspaceLayouts.contains("secondaryPaneStateHolder.SaveableStateProvider(state.tab.name)"),
                WORKSPACE_LAYOUTS + " 的宽屏副栏不得再按 `state.tab` 直接挂 PaneHost："
                        + "那是 when(tab) 硬切（切一次重建一次面板）");
        // 宽屏副栏不得能被手指横滑（页签在顶栏，面板横滑会与列表手势打架）。
        requireContains(workspaceLayouts, "userScrollEnabled = false,",
                WORKSPACE_LAYOUTS + " 的宽屏副栏 Pager 必须 userScrollEnabled = false："
                        + "它的页签在顶栏那一行，面板本身横滑会与列表手势打架");
        // ② 其它多态处必须有 AnimatedContent / AnimatedVisibility。
        //
        // ⚠️ 判据带 `targetState`/`visible`：只查 "AnimatedContent(" 会被
        // `if (false) AnimatedContent(` 这种（关掉但保留）骗过 —— teeth 实测过。
        String[] needsTransition = {
                CHANGES_PANE, FILES_PANE, TERMINAL_PANE, SKILLS_OVERLAY, SETTINGS_DIALOG,
        };
        for (String file : needsTransition) {
            String text = stripComments(read(root, file));
            require(text.contains("AnimatedContent(\n")
                            || text.contains("AnimatedVisibility(\n")
                            || text.contains("AnimatedVisibility(visible ="),
                    file + " 的形态切换必须有 AnimatedContent / AnimatedVisibility："
                            + "裸 `if / when` 换分支是一帧内整块换掉（用户说的\"很多地方没有动画\"）");
        }
        // MCP 的两个 TAB 单列：它的 targetState 只能是**下标**。
        require(mcp.contains("AnimatedContent(") && mcp.contains("targetState = tab,"),
                MCP_CONFIG_OVERLAY + " 的两个 TAB 必须包在 AnimatedContent 里，且 "
                        + "`targetState = tab,`（下标）：把 draft 编进去的话每敲一个键都重放转场");
        require(!mcp.contains("AnimatedContent(\n                    targetState = draft"),
                MCP_CONFIG_OVERLAY + " 的 TAB 转场不得以 draft 为 key");
        // ②' 沙箱页的三态（错误 / 空 / 列表）是**例外**，而且是有代价的取舍。
        //
        // `AnimatedContent` 必须包在 LazyColumn **外面**，于是分支一换整页重建：
        // 安装/卸载一个包时用户正停在列表中部，会被弹回顶部；而且顶栏折叠的
        // `padding.calculateTopPadding()` 与 `nestedScroll` 都挂在这个 LazyColumn 上，
        // 包一层会让那两处错位。
        //
        // 所以改成：三个分支各给**稳定 key** + `animateItem()`。
        // 旧分支因此一定是"消失"而不是"复用成新内容"，观感与淡变一致，
        // 而滚动位置、惰性、顶栏联动全部保留。
        //
        // 三条断言缺一不可：少了 key 就变成"复用同一项、只换内容"（等于硬切），
        // 少了 animateItem 就完全没有补间。
        requireContains(sandbox, "item(key = \"sandboxError\")",
                SANDBOX_SCREEN + " 的错误态 item 必须有稳定 key（没有 key 的话分支切换时"
                        + "Compose 会把它当成同一项只换内容 —— 等于硬切）");
        requireContains(sandbox, "item(key = \"sandboxEmpty\")",
                SANDBOX_SCREEN + " 的空态 item 必须有稳定 key（同上）");
        require(countOf(sandbox, "Modifier.animateItem()") >= 3,
                SANDBOX_SCREEN + " 的三个分支（错误/空/应用卡片）都必须挂 animateItem()"
                        + "（当前 " + countOf(sandbox, "Modifier.animateItem()") + " 处）："
                        + "这里是 §27 的唯一例外 —— 因为它不能包 AnimatedContent"
                        + "（会重置滚动位置、并让顶栏折叠 padding 与 nestedScroll 错位）");
        // ③ C-4 的 key 不得把正在编辑的文本编进去。
        //
        // 这是本组里最容易犯且**最不容易被发现**的一条：把 draft 编进 targetState，
        // 每敲一个键 targetState 都变 → 每敲一个键都重放一次转场。
        // 表现为"打字时整块在闪"，很容易被误诊成输入法问题。
        requireContains(filesPane2, "targetState = stage,",
                FILES_PANE + " 的 AnimatedContent 必须以**小枚举** stage 为 key，"
                        + "不能把 draft（正在编辑的文本）编进去 —— 那样每敲一个键都会重放转场");
        require(filesPane2.contains("private enum class FileStage"),
                FILES_PANE + " 必须有 FileStage 枚举（LIST/VIEW/EDIT）："
                        + "把 `openFile == null` / `draft != null` 直接拼成 key 时，"
                        + "draft 一变就换 targetState");
        // ③' §27 补：`AnimatedContent` 的**每个分支只能吐一个** composable。
        //
        // 这条是用户报「文件列表和面包屑重叠」之后补的。根因既不在面包屑也不在列表，
        // 而在 `AnimatedContent` 的**容器语义**：它是**叠放**（转场时它必须把新旧两屏
        // 放在同一个位置才能交叉淡变），它**不是** `Column`。
        // 所以分支里并列写几个 composable 时，「表头 + 面包屑 + 列表」会被放到
        // **同一个原点**上互相盖住 —— 实测就是这个重叠。
        //
        // 为什么以前不重叠：这层**原先是裸 `when`**，挂在 `Column` 下时兄弟节点是竖排的。
        // C-4 把它换成 `AnimatedContent` 之后，竖排语义变成了叠放 ——
        // 这正是本项目把那次重构标为「最容易改崩」的那个原因，这里就是它崩掉的地方。
        //
        // ⚠️ 断言必须**成对**（正向 + 反向）：只查正向的话，把 `Column(…) {` 换成
        // 另一个同样单子节点的容器（等于把这一坨参数藏进新函数、却漏传一个形参）
        // 照样绿；只查反向则完全没守住。两边都写才拦得住回归到裸 `{`。
        requireContains(filesPane2, "FileStage.LIST -> Column(modifier = Modifier.fillMaxSize()) {",
                FILES_PANE + " 的 LIST 分支必须**只吐一个** composable（外层那个 `Column`）："
                        + "AnimatedContent 的容器是叠放语义，分支里并列多个节点会让"
                        + "「表头 + 面包屑 + 列表」叠在同一个原点 —— 用户报的"
                        + "「文件列表和面包屑重叠」就是这个");
        requireContains(filesPane2, "FileStage.VIEW, FileStage.EDIT -> Column(modifier = Modifier.fillMaxSize()) {",
                FILES_PANE + " 的 VIEW/EDIT 分支同上：它并列的是「表头 + 面包屑 + 内容」，"
                        + "不包一层 `Column` 同样会叠在一起");
        requireAbsentIn(filesPane2, "FileStage.LIST -> {",
                FILES_PANE + " 的 LIST 分支不得回到裸 `{`：那是「裸 when 换成 AnimatedContent」"
                        + "的回归形态，观感就是列表与面包屑重叠");
        requireAbsentIn(filesPane2, "FileStage.VIEW, FileStage.EDIT -> {",
                FILES_PANE + " 的 VIEW/EDIT 分支不得回到裸 `{`（同上）");
        requireContains(sandbox, "items(state.packages, key = { it })",
                SANDBOX_SCREEN + " 的应用列表必须保留稳定 key（否则 animateItem 没有意义）");
        requireContains(sandbox, "modifier = Modifier.animateItem(),",
                SANDBOX_SCREEN + " 的应用卡片必须挂 animateItem()：安装/卸载后列表重排时"
                        + "卡片会瞬移（这一处上一轮漏了 —— items 本来给了 key，只是没挂动效）");
        // ⚠️ 数个数（两处折叠都要、不能只改一处）—— teeth 实测只查 contains 会漏。
        require(countOf(settingsDialog, "enter = fadeIn() + expandVertically(),") >= 2,
                SETTINGS_DIALOG + " 的两处折叠窗都必须显式给出展开方向（官方 SettingsPage 的写法）"
                        + "（当前 " + countOf(settingsDialog, "enter = fadeIn() + expandVertically(),")
                        + " 处，应为 2：联网搜索 + 上下文压缩）");
        require(countOf(settingsDialog, "exit = fadeOut() + shrinkVertically(),") >= 2,
                SETTINGS_DIALOG + " 的同两处收起也必须给出（fadeOut() + shrinkVertically()）");

        // ---- 28. TerminalPane：组合里至多一个 AndroidView ★硬不变量★ ----------
        //
        // C-6 是把裸 `when` 换成 `AnimatedContent`。这一步**最容易改崩**：
        // `AnimatedContent` 在转场期间会同时组合 initialState 与 targetState 两份内容，
        // 若两个分支都含 `AndroidView`，同一个 `TermuxTerminalPane` 实例会被挂到
        // 两个父容器上 —— Android 直接抛 "already has a parent"。
        //
        // 唯一的成立条件是：**`AndroidView` 只出现在一个分支里**，而
        // `AnimatedContent` 转场时两个 state 必然不等，所以那一对里至多一个命中。
        require(countOf(terminalPane, "AndroidView(") == 1,
                TERMINAL_PANE + " 里必须**只有一处** AndroidView（当前 "
                        + countOf(terminalPane, "AndroidView(") + " 处）："
                        + "AnimatedContent 转场期间同时组合两个分支，两处都有的话"
                        + "同一个 View 实例会被挂到两个父容器上，Android 直接抛异常");
        requireContains(terminalPane, "TerminalStage.REAL -> key(viewEpoch) {",
                TERMINAL_PANE + " 的 AndroidView 必须仍在自己那一支 `TerminalStage.REAL -> "
                        + "key(viewEpoch) { … }` 里（`key(viewEpoch)` 是重挂渲染用的）");
        // ⚠️ 断言带 `{ REAL, FAILURE, NOTICE }`：只查 "private enum class TerminalStage"
        // 会被改名成 `TerminalStageX` 骗过（它是那个字符串的前缀）—— teeth 实测过。
        requireContains(terminalPane, "private enum class TerminalStage { REAL, FAILURE, NOTICE }",
                TERMINAL_PANE + " 必须用 TerminalStage 枚举当 AnimatedContent 的 key："
                        + "直接拿 failureDetail/runtimeNotice 那两个**字符串**当 key 的话，"
                        + "同一种状态下文案一变也会被当成\"换了一屏\"而重放转场");
        // ⚠️ `detachFromParent(pane)` 在同一个文件里还有一处合法调用
        // （`onDispose { detachFromParent(pane) }`），所以必须**按块**断言 ——
        // 整文件 contains 的话删掉 AndroidView 里那一句照样绿，teeth 实测过。
        String androidViewBody = bodyOf(terminalPane, "                                    AndroidView(");
        require(androidViewBody.isEmpty() == false,
                TERMINAL_PANE + " 找不到 AndroidView( 那一块的正文 —— 它的缩进变了，"
                        + "请同步更新本断言的 signature");
        require(androidViewBody.contains("detachFromParent(pane)"),
                TERMINAL_PANE + " 的 AndroidView 块里必须保留 detachFromParent(pane)："
                        + "它是 §28 这条不变量的最后一道兜底"
                        + "（⚠️ 不能只查整文件 —— 文件里 `onDispose` 那处也含同一串）");
        require(countOf(terminalPane, "TerminalStage.FAILURE ->") == 1
                        && countOf(terminalPane, "TerminalStage.NOTICE ->") == 1,
                TERMINAL_PANE + " 的三个 TerminalStage 分支必须各出现一次");
        // ⚠️ targetState 的**判断**必须读 `state.*`，不能读那两个 rememberLastNonNull 变量。
        // 那是本仓最容易顺手写错的一处：`failure` 一旦非空就**永远**非空
        // （rememberLastNonNull 的定义就是这样），于是失败屏再也退不出去 ——
        // 重试成功了界面还停在"终端已退出"。teeth 实测过这种改法能骗过旧的断言。
        String terminalStageKey = bodyOf(terminalPane, "                            targetState = when {");
        require(!terminalStageKey.isEmpty(),
                TERMINAL_PANE + " 找不到 targetState = when { 那一块（缩进变了？）");
        require(terminalStageKey.contains("state.failureDetail != null")
                        && terminalStageKey.contains("state.runtimeNotice != null"),
                TERMINAL_PANE + " 的 targetState 判断必须读 `state.failureDetail` / "
                        + "`state.runtimeNotice`，**不能**读 `failure` / `notice` 那两个"
                        + "「最后一次非空」变量：它们一旦非空就永远非空，"
                        + "失败屏会再也退不出去（重试成功了界面还停在「终端已退出」）");
        requireAbsentIn(terminalStageKey, "failure != null ->",
                TERMINAL_PANE + " 的 targetState 不得用 `failure != null` 当条件（同上）");
        requireAbsentIn(terminalStageKey, "notice != null ->",
                TERMINAL_PANE + " 的 targetState 不得用 `notice != null` 当条件（同上）");

        // ---- 29. 选中态变色必须过 animateColorAsState --------------------------
        //
        // 本仓早有一条专盯 ModelPicker 的同类断言（"硬切会让'点一下整行啪地变蓝'
        // 看起来很生硬"），但同一条道理没被推广开：全仓只有 6 处用过
        // `animateColorAsState`，而这 8 处选中态全是硬切。
        //
        // ⚠️⚠️ 这里**绝不能**只断言"文件里出现过 animateColorAsState"。
        // teeth 第一轮就是这么写的，结果是 5/5 全部漏掉：把动画值的**使用处**
        // 换回硬编码 `color = if (…)` 之后，那个 `animateColorAsState(…)` 的
        // **声明**还在文件里，`contains` 照样绿 —— 而那正是本仓记录过的失败模式
        // （"断言看着在守，守的其实是别的东西"）。
        //
        // 所以这一节每一处都断言**一对**：
        //  · 正向 = 动画出来的那个变量**真的被用在组件参数上**；
        //  · 反向 = 同一段块里不得残留那个硬编码的 `if (…)` 配色。
        // 反向用 block 级（bodyOf）而不是整文件，因为文件里往往还有别的合法硬切
        // （例如 `color = if (prompt.destructive) { 红 } else { 正常 }` —— 那是一次性的
        //  提示，不是"选中态在两种稳定状态之间来回变"，没有补间的意义）。
        String terminalChrome = stripComments(read(root, TERMINAL_CHROME));
        // ① FilesPane 的根切换条。
        String rootSwitcher = bodyOf(filesPane2, "private fun FileRootSwitcher(");
        require(!rootSwitcher.isEmpty(),
                FILES_PANE + " 找不到 FileRootSwitcher 的正文（签名变了？）");
        require(rootSwitcher.contains("color = segmentColor,")
                        && rootSwitcher.contains("contentColor = segmentContent,"),
                FILES_PANE + " 的根切换条必须把 animateColorAsState 的结果用在 Card 的 "
                        + "color / contentColor 上：只声明不用等于没做动画");
        requireAbsentIn(rootSwitcher, "color = if (active)",
                FILES_PANE + " 的根切换条不得再出现 `color = if (active) …` 的硬切配色");
        requireAbsentIn(rootSwitcher, "contentColor = if (active)",
                FILES_PANE + " 的根切换条不得再出现 `contentColor = if (active) …` 的硬切配色");
        // 下沉反馈也必须落在**这一段**里（FileRow 那张卡也是 Sink，整文件查会漏）。
        requireAbsentIn(rootSwitcher, "pressFeedbackType = PressFeedbackType.None",
                FILES_PANE + " 的根切换条必须给 pressFeedbackType = PressFeedbackType.Sink，"
                        + "它是真按钮，按下去要有下沉反馈（Miuix Card 的默认值是 None；"
                        + "对照官方 CardSection.kt:122）");
        requireContains(rootSwitcher, "pressFeedbackType = PressFeedbackType.Sink",
                FILES_PANE + " 的根切换条缺 pressFeedbackType = Sink");
        // ② TerminalChrome：扩展键字色 + 会话行底色/字色。
        requireContains(terminalChrome, "color = keyColor,",
                TERMINAL_CHROME + " 的扩展键必须把 animateColorAsState 的结果用在字色上");
        requireAbsentIn(terminalChrome, "color = if (active) palette.accent",
                TERMINAL_CHROME + " 的扩展键不得再硬切字色");
        requireContains(terminalChrome, ".background(rowBg)",
                TERMINAL_CHROME + " 的会话行必须把 animateColorAsState 的结果用在 background 上");
        requireContains(terminalChrome, "color = rowText,",
                TERMINAL_CHROME + " 的会话行必须把 animateColorAsState 的结果用在字色上");
        requireAbsentIn(terminalChrome, ".background(if (session.selected)",
                TERMINAL_CHROME + " 的会话行不得再硬切底色");
        requireAbsentIn(terminalChrome, "color = if (session.selected)",
                TERMINAL_CHROME + " 的会话行不得再硬切字色");
        // ③ Dialogs 的选择窗口选项行。
        // ⚠️ 复用本方法前面（§… 处）已经读好的那一份 `dialogs`：同一个
        // `stripComments(read(root, DIALOGS))`，不必再读一遍盘。
        requireContains(dialogs, "color = cardColor,",
                DIALOGS + " 的选项行必须把 animateColorAsState 的结果用在 Card 的 color 上");
        require(dialogs.contains("color = titleColor)")
                        || dialogs.contains("color = titleColor,"),
                DIALOGS + " 的选项行标题必须把 animateColorAsState 的结果喂给 "
                        + "BasicComponentDefaults.titleColor(color = titleColor)");
        requireAbsentIn(dialogs, "color = if (checked) scheme.surfaceContainerHighest",
                DIALOGS + " 的选项行不得再硬切底色");
        // ⚠️ 反向锚点必须带上前缀 `titleColor = BasicComponentDefaults.titleColor(` ——
        // 光禁 `if (checked) scheme.primary else scheme.onBackground` 会把
        // animateColorAsState 的 **targetValue** 一起误伤（那一行本来就长这样）。
        requireAbsentIn(dialogs,
                "titleColor = BasicComponentDefaults.titleColor(\n"
                        + "                            color = if (checked)",
                DIALOGS + " 的选项行标题不得再硬切颜色（titleColor 里直接写 if）");
        // ④ 三个列表页的"当前生效"行。
        for (String[] pair : new String[][] {
                {API_CONFIG_OVERLAY, "apiProfileTitle"},
                {ROLE_CARDS_OVERLAY, "roleCardTitle"},
                {SEARCH_SERVICES_OVERLAY, "searchServiceSummary"},
        }) {
            String text = stripComments(read(root, pair[0]));
            requireContains(text, "label = \"" + pair[1] + "\",",
                    pair[0] + " 的选中态必须有一条 animateColorAsState（label = \"" + pair[1] + "\"）"
                            + " —— 按 label 点名而不是查\"文件里出现过 animateColorAsState\"："
                            + "后者在动画被换成硬切之后照样绿");
            requireAbsentIn(text, "color = if (active) scheme.primary else scheme.onBackground",
                    pair[0] + " 的选中行不得再出现 `color = if (active) scheme.primary else …` 的硬切");
            requireAbsentIn(text, "color = if (active) scheme.primary else scheme.onSurfaceVariantSummary",
                    pair[0] + " 的选中行不得再硬切副标题色");
        }
        // ⚠️ colorSpec 的锚点必须带上**类型声明那一行**：光查
        // `tween(FADE_OUT_MILLIS, easing = SinOutEasing)` 在这个文件里出现两次
        // （colorSpec 与 fadeOutSpec 共用同一条），改掉一处另一处会让断言照样绿 ——
        // teeth 第一轮的锚点就撞在这一点上，直接 SKIP 了。
        requireContains(stripComments(read(root, ANIMATIONS)),
                "val colorSpec: FiniteAnimationSpec<Color> =\n"
                        + "        tween(FADE_OUT_MILLIS, easing = SinOutEasing)",
                ANIMATIONS + " 的 colorSpec 必须仍是 `tween(150, SinOutEasing)`"
                        + "（这是 Miuix 弹窗淡出那条曲线，8 处选中态都引用它）");

        // ---- 30. 工具卡的富内容预览（`additionalContent`）必须真的到界面 --------
        //
        // 这条链**每一环都可能被悄悄掐断，而全程不会有任何编译错误或运行时报错**：
        //
        //   工具产出 additionalContent
        //     → 引擎（本来只发给模型！界面拿不到）
        //       → EngineEvents.onEngineToolResult 的**签名**
        //         → 控制器转发
        //           → ToolActivity.previews
        //             → MessageCards 渲染
        //
        // 原来的事实链是：`ZhiSandboxTool` 一直在产出 `{type:image, base64…}`，
        // `ZhiCodeEngine` 一直在把它当 user 消息发给模型，而**界面这一层从头到尾
        // 没有接过** —— 于是"沙箱截的图，模型看得到、用户看不到"。
        // `UiCanvasTool` 里那句注释「包装成界面能直接消费的附加内容块」当时并不成立。
        //
        // 所以这里逐环点名，而不是查"文件里出现过 additionalContent"：
        // 后者在中间任何一环被删掉之后**照样绿**（上游还在产出、下游还在渲染，
        // 只是没人接）。teeth 会逐个改坏来确认每条断言都不是空的。
        String engineController = stripComments(read(root, ENGINE_CONTROLLER));
        String uiModels = stripComments(read(root, UI_MODELS));
        String vmForPreviews = stripComments(read(root, VIEW_MODEL));
        String cardsForPreviews = stripComments(read(root, MESSAGE_CARDS));

        // ① 签名里必须有这个参数。少了它，控制器传什么都编译不过 —— 这是最外层的一道。
        //
        // ⚠️ 用 signatureAt 而**不是** bodyOf：`EngineEvents` 里这是一句**没有方法体的接口声明**，
        // bodyOf 会一路找到后面别的函数的 `{`，取出一块与断言无关的文本 —— 那样即使
        // 参数被删掉，断言也可能因为"附近某处恰好有这串字"而变绿。
        String resultEvent = signatureAt(engineController, "fun onEngineToolResult(");
        require(!resultEvent.isEmpty(),
                ENGINE_CONTROLLER + " 找不到 onEngineToolResult 的签名（缩进/名字变了？）");
        requireContains(resultEvent, "previews: List<ChatImage>,",
                ENGINE_CONTROLLER + " 的 EngineEvents.onEngineToolResult 必须有 previews 参数："
                        + "它曾经不存在，于是工具带回来的富内容在这一层被丢掉，"
                        + "而引擎照样把它发给模型");

        // ② 控制器必须真的把 additionalContent 交给解析器，而不是只声明一个没人用的参数。
        requireContains(engineController, "readChatImageBlocks(",
                ENGINE_CONTROLLER + " 必须调用 readChatImageBlocks 解析 additionalContent");
        requireContains(engineController, "result.additionalContent()",
                ENGINE_CONTROLLER + " 必须从 result.additionalContent() 取数据 —— "
                        + "参数声明了却不转发是最容易发生的退化");

        // ③ 界面模型要有落点。
        requireContains(uiModels, "val previews: List<ChatImage> = emptyList(),",
                UI_MODELS + " 的 ToolActivity 必须有 previews 字段");
        requireContains(vmForPreviews, "previews = previews,",
                VIEW_MODEL + " 的 onEngineToolResult 必须把 previews 存进 ToolActivity —— "
                        + "同 `diff` 那一处：漏了这行，截图停在这一层，编译器不会提醒");

        // ④ 渲染：必须在 EXPANDED 里真的画出来，而且**必须复用** ZhiImageRow。
        String expandedBlock = bodyOf(cardsForPreviews, "ToolStatusRegion.EXPANDED ->");
        require(!expandedBlock.isEmpty(),
                MESSAGE_CARDS + " 找不到 EXPANDED 分支（缩进变了？）");
        requireContains(expandedBlock, "activity.previews.isNotEmpty()",
                MESSAGE_CARDS + " 的展开态必须判 activity.previews（不判就等于没画）");
        requireContains(expandedBlock, "ZhiImageRow(",
                MESSAGE_CARDS + " 的展开态必须复用 ZhiImageRow 画预览图："
                        + "横向可滑、同高、长宽比夹取与解码缓存都在那个组件里，"
                        + "自己拼一行图会重复实现并漏掉\"同一轴不能再套滚动\"那条约束");
        requireContains(expandedBlock, "images = activity.previews",
                MESSAGE_CARDS + " 的 ZhiImageRow 必须接 activity.previews（而不是从别处找数据）");
        // 点了要有反应：`onOpen` 必须接到那个回调上，否则图能看、点不开。
        requireContains(expandedBlock, "onOpen = onImageOpen,",
                MESSAGE_CARDS + " 展开态的 ZhiImageRow 必须把 onOpen 接到 onImageOpen —— "
                        + "否则缩略图画得出来、点下去没反应（全屏查看进不去）");

        // ⑤ 回调必须从 ToolBatch 一路透传到行里。
        //
        // 断在 ToolGroupCard 那一层的后果是**分组的工具丢预览、单条的还在** ——
        // 表现出来像"有时有图有时没有"，最难排查的一种。
        String toolGroupCardBlock = bodyOf(cardsForPreviews, "private fun ToolGroupCard(");
        require(!toolGroupCardBlock.isEmpty(),
                MESSAGE_CARDS + " 找不到 ToolGroupCard（缩进变了？）");
        requireContains(toolGroupCardBlock, "onImageOpen = onImageOpen,",
                MESSAGE_CARDS + " 的 ToolGroupCard 必须把 onImageOpen 透传给行 —— "
                        + "漏了它，折叠组里的工具就没有预览，而单条工具还有");

        // ⑥ 点开要能全屏 —— 宿主在 ToolBatch 内部（本地 UI 状态，照 UserBubble 的约定）。
        String toolBatchBlock = bodyOf(cardsForPreviews, "fun ToolBatch(");
        require(!toolBatchBlock.isEmpty(),
                MESSAGE_CARDS + " 找不到 ToolBatch（缩进变了？）");
        requireContains(toolBatchBlock, "ZhiImageViewer(image = viewing",
                MESSAGE_CARDS + " 的 ToolBatch 必须挂 ZhiImageViewer —— "
                        + "否则预览图点了没反应");
        requireContains(toolBatchBlock, "onImageOpen = { viewing = it }",
                MESSAGE_CARDS + " 必须把 onImageOpen 接到本地 viewing 状态上");

        // ⑦ 两个判据必须带上 previews。
        //
        // 这是本功能最容易留下的**静默吞图**：`toolStatusRegion` 判 QUIET 的条件若只写
        // `output.isBlank()`，一个"只有截图、自述为空"的工具展开之后什么都不画
        // （QUIET 分支是 `-> Unit`）；沙箱截图目前恰好总带一段 JSON 自述所以碰不到，
        // 但那是巧合不是保证。
        String regionBody = bodyOf(cardsForPreviews, "private fun toolStatusRegion(");
        require(!regionBody.isEmpty(),
                MESSAGE_CARDS + " 找不到 toolStatusRegion（缩进变了？）");
        requireContains(regionBody, "activity.previews.isEmpty()",
                MESSAGE_CARDS + " 的 toolStatusRegion 判 QUIET 时必须一并看 previews："
                        + "只判 output 的话，只有截图没有自述的工具展开后是空白的");
        require(!regionBody.contains("activity.output.isBlank() -> ToolStatusRegion.QUIET"),
                MESSAGE_CARDS + " 的 toolStatusRegion 不得退回「只判 output」的写法（同上）");
        // ⚠️ 同理用 lineAt：`val hasDetails = …` 是一句没有花括号的赋值，
        // bodyOf 会跑到后面某个函数里去。
        String hasDetailsLine = lineAt(cardsForPreviews, "val hasDetails =");
        require(!hasDetailsLine.isEmpty(),
                MESSAGE_CARDS + " 找不到 val hasDetails（改名了？）");
        requireContains(hasDetailsLine, "activity.previews.isNotEmpty()",
                MESSAGE_CARDS + " 的 hasDetails 必须包含 previews："
                        + "否则只有预览的工具连展开入口都没有");

        // ⑧ 折叠态摘要要说"图"，不能显示成"N 行"（那是自述 JSON 的行数）。
        String summaryBody = bodyOf(cardsForPreviews, "private fun compactToolSummary(");
        require(!summaryBody.isEmpty(),
                MESSAGE_CARDS + " 找不到 compactToolSummary（缩进变了？）");
        requireContains(summaryBody, "activity.previews.isNotEmpty()",
                MESSAGE_CARDS + " 的 compactToolSummary 必须先说预览图："
                        + "否则沙箱截图会显示成「28 行 · 点按展开」，用户看不出有图可看");

        // ---- 31. 终端必须跟随应用主题 ------------------------------------------
        //
        // 用户报「终端对深浅色不适配」。事实是：ANSI 调色板一直写死深色档，而**浅色档
        // 那张表历史上写过却从来没走到过** —— 它唯一的入口 applyTheme 没有任何调用方，
        // 宿主类注释里把这件事记成了"应该单独做"。本轮就是单独做它。
        //
        // ⚠️ 这一节的重点不是"浅色档存在"，而是**两档都得在**：
        //  · 只钉"浅色档接上了" → 把深色档也顺手换成浅色值照样绿，
        //    而深色是默认档，那等于**所有人**的外观都被改了；
        //  · 只钉"深色档没变" → 什么都没做也能绿。
        // 所以下面每对断言都是正向 + 反向一起给。
        //
        // 为什么值得单列一节：配色这条链断了**不会有任何编译错误或运行时报错**，
        // 界面只是安静地回到"深色模式看不出问题、浅色模式一片黑"。
        String terminalHost = stripComments(read(root, TERMINAL_HOST));

        // ① 必须按主题分档，而不是写死一张表。
        String applyPalette = bodyOf(terminalHost,
                "private void applyTerminalPalette(TerminalSession session)");
        require(!applyPalette.isEmpty(),
                TERMINAL_HOST + " 找不到 applyTerminalPalette 的正文（签名变了？）");
        requireContains(applyPalette, "if (!darkTheme) {",
                TERMINAL_HOST + " 的 applyTerminalPalette 必须按 darkTheme 分深浅两档："
                        + "只写死一张表的话，浅色模式下终端仍然是黑底白字");
        // ② 浅色档必须**背景与前景成对**给。只改背景不改前景 = 浅底上留白字，等于看不见。
        requireContains(applyPalette,
                "colors[TextStyle.COLOR_INDEX_BACKGROUND] = Color.rgb(246, 248, 252);",
                TERMINAL_HOST + " 的浅色档必须给出背景色（旧表里的 BG 246,248,252）");
        requireContains(applyPalette,
                "colors[TextStyle.COLOR_INDEX_FOREGROUND] = Color.rgb(29, 36, 51);",
                TERMINAL_HOST + " 的浅色档必须同时给出前景色（TEXT 29,36,51）："
                        + "只改背景会让文字变成浅底上的浅字，等于什么都看不见");
        // ③ 深色档不许变 —— 改浅色档时顺手"调整"一下深色档，浅色下看不出来，
        //    而深色是默认档，外观就被悄悄改动了。
        requireContains(applyPalette,
                "colors[TextStyle.COLOR_INDEX_BACKGROUND] = Color.rgb(0, 0, 0);",
                TERMINAL_HOST + " 的深色档背景必须仍是纯黑：改浅色档时不得顺手动深色档，"
                        + "深色是默认档，动了等于改了所有用户的外观");
        // ④ 空档期（还没有会话 / 会话已退出）的底色不得写死。
        require(!terminalHost.contains("setBackgroundColor(Color.BLACK)"),
                TERMINAL_HOST + " 不得再写死 setBackgroundColor(Color.BLACK)："
                        + "浅色档下没有会话时会闪出一块纯黑 —— 那正是「深浅色不适配」的一处");
        // ⑤ 必须有推送入口。
        require(terminalHost.contains("public void setDarkTheme(boolean dark)"),
                TERMINAL_HOST + " 必须有 public void setDarkTheme(boolean dark)："
                        + "应用主题的唯一权威在 Compose 那边（LocalZhiDark），"
                        + "宿主是纯 Java 的 FrameLayout，读不到它");
        // ⑥ 光是"有这个入口"没用 —— 浅色表当年就是死在"没人调"上。界面侧必须真的推。
        requireContains(terminalPane, "LaunchedEffect(isDark) { pane.setDarkTheme(isDark) }",
                TERMINAL_PANE + " 必须在 isDark 变化时调用 pane.setDarkTheme(isDark)："
                        + "宿主加了 setDarkTheme 却没人调用，等于浅色档仍然走不到 ——"
                        + "历史上那张浅色表走不到，原因正是它的入口 applyTheme 没有调用方");
        // ⑦ 外壳的两个占位不得再写死黑底（它们也铺满整个面板）。
        require(!terminalChrome.contains("background(Color.Black)"),
                TERMINAL_CHROME + " 的占位不得再写死 Color.Black 底色（同上："
                        + "浅色模式下会从浅底里闪出一块纯黑）");

        // ---- 32. 对话流的文字动画（表头淡变 / 新成员一次性进入）------------------
        //
        // 用户的原话：「对话流的动画还不是很完善（尤其是思考和正文还有工具卡），
        // 文字是直接没动画」。这一节钉住本轮补上的两处。
        String cardsForMotion = stripComments(read(root, MESSAGE_CARDS));

        // ① 思考面板的表头文案必须过 Crossfade。
        //
        // 表头是「思考过程」+ 流式期间多一个「 · 进行中」。不淡变的话，尾缀会在
        // 流式开始/结束那两下硬蹦出来/消失 —— 整块面板看着像"卡"了一下。
        // 做法与工具组标题同一口径（「Crossfade(targetState = ToolGrouping.label(...))」）。
        String thinkingPanel = bodyOf(cardsForMotion, "private fun ThinkingPanel(");
        require(!thinkingPanel.isEmpty(),
                MESSAGE_CARDS + " 找不到 ThinkingPanel 的正文（签名变了？）");
        requireContains(thinkingPanel, "label = \"thinking-label\",",
                MESSAGE_CARDS + " 的 ThinkingPanel 表头文案必须过 Crossfade（label = "
                        + "\"thinking-label\"）：不加的话「 · 进行中」这个尾缀是硬蹦的");
        // ⚠️ 反向：只查正向的话，Crossfade 声明留着、使用处退回裸 Text 照样绿
        //    —— 本仓 §29 记过这个失败模式。
        require(!thinkingPanel.contains("text = \"思考过程\" + (if (item.streaming)"),
                MESSAGE_CARDS + " 的 ThinkingPanel 表头不得退回裸 Text："
                        + "那样尾缀又是硬切（Crossfade 声明留着也没用）");

        // ② 新成员的一次性进入动画。
        //
        // 为什么需要：一轮助手回合是**一个** LazyColumn item，回合内部追加的工具卡/正文
        // 不产生新 item，item 级的 animateItem 根本不会触发 —— 文字就是"蹦"出来的。
        String chatListForMotion = stripComments(read(root, CHAT_LIST));
        requireContains(chatListForMotion, "private val MemberEnterRise = 8.dp",
                CHAT_LIST + " 必须有 MemberEnterRise 常量（新成员上移的距离）");
        String animatedMember = bodyOf(chatListForMotion, "private fun AnimatedMember(");
        require(!animatedMember.isEmpty(),
                CHAT_LIST + " 找不到 AnimatedMember（签名变了？）");
        // ⚠️⚠️ 这一节最重要的一条：**历史消息不能在滚动中补播动画**。
        //
        // LazyColumn 会回收组合，往上滚到旧消息时那一条是"重新进入组合"的。
        // 若判据写成"刚进入组合就播"，历史消息会在滚动里不停闪 —— 比没有动画糟得多。
        // 所以：可见性判据必须来自 「seenIds」（首次组合时已把当时的 transcript 全部登记），
        // 而不是"它进来了"。
        requireContains(chatListForMotion, "animate = remember(item.id) {",
                CHAT_LIST + " 的成员进入动画必须以「这个 id 以前没见过」为判据"
                        + "（animate = remember(item.id) { … }）："
                        + "写成「刚进入组合就播」会让往上滚到的历史消息不停闪");
        // 判据的来源要单独钉一次：上一条只看 `animate = remember(item.id) {`，
        // 把里面换成任何别的条件（比如"刚进组合"）它照样绿。
        requireContains(chatListForMotion, "val fresh = seenIds.add(item.id)",
                CHAT_LIST + " 的进入判据必须取自 seenIds.add(...) 的返回值："
                        + "换成别的来源就把「历史消息补播动画」那个失败模式放回来了");
        requireContains(chatListForMotion, "val seenIds = remember(state.activeSessionId) {",
                CHAT_LIST + " 必须有 seenIds，且以 activeSessionId 为 key："
                        + "换会话时重新快照一次，否则新会话里的消息会集体播一次淡入");
        // ⚠️ 播种的**实现位置**后来挪了：集合改放文件级、播种收进 `seenIdsFor`，
        //    因为 `remember` 的寿命只到本层组合被丢弃为止 —— 组合一旦重建，
        //    「用当前 transcript 再播种一次」会把**刚刚新到的那条**也登记成已见过，
        //    于是它的动画静默消失（详情见 ChatList 里的长注释）。
        //    所以这里改钉两处：调用点传入了当时快照，且播种发生在"按会话只做一次"的门内。
        requireContains(chatListForMotion, "seenIdsFor(state.activeSessionId, state.transcript)",
                CHAT_LIST + " 的 seenIds 初次组合必须把**当时的 transcript 快照**交给播种函数："
                        + "它们是历史，不该补播动画");
        String seenIdsForBody = bodyOf(chatListForMotion, "private fun seenIdsFor(");
        require(!seenIdsForBody.isEmpty(),
                CHAT_LIST + " 找不到 seenIdsFor 的正文（改名了？）");
        requireContains(seenIdsForBody, "seenMessageIds.addAll(transcript.map { it.id })",
                CHAT_LIST + " 的 seenIdsFor 必须把**当时的 transcript 全部**登记为已见过："
                        + "它们是历史，不该补播动画");
        requireContains(seenIdsForBody, "if (seenSeededSession != sessionId) {",
                CHAT_LIST + " 的 seenIdsFor 播种必须**按会话只做一次**："
                        + "组合重建时又播种一次，会把刚新到的那条也标成已见过，动画就没了");
        // 反向：不得无条件播（这是最容易写出的错误版本）。
        require(!chatListForMotion.contains("AnimatedMember(animate = true)"),
                CHAT_LIST + " 不得无条件播进入动画（历史消息会在滚动中不停闪）");
        // 历史那条路必须零开销：不建 layer。
        requireContains(animatedMember, "if (!animate) {",
                CHAT_LIST + " 的 AnimatedMember 必须在 animate 为假时原样输出内容："
                        + "历史消息占绝大多数，给每条都常驻一个 graphicsLayer 是白付开销");
        requireContains(animatedMember, "Modifier.graphicsLayer {",
                CHAT_LIST + " 的 AnimatedMember 必须用 graphicsLayer 做淡入+位移");

        // ③ 进度必须**在绘制期读**。
        //
        // 这不是风格问题。写成 `val p = progress.value` 再 `graphicsLayer { alpha = p }`，
        // 那个 `by`/`=` 就是组合期读：动画的**每一帧**都让本组件重组一次，
        // 而本组件是消息列表的成员包装，重组就把 `content()` 整条（含 Markdown 正文）
        // 重新求值。外面同时还有逐帧的列表布局在跑 —— 两边叠起来就是用户报的
        // 「没有动画，只看到卡」。
        //
        // ⚠️ 只钉正向不够：`Modifier.graphicsLayer {` 留着、lambda 里改成读一个
        //    组合期算好的局部变量，照样绿 —— 而那正是要拦的写法。所以正反一起给。
        require(!animatedMember.contains("val p = progress.value"),
                CHAT_LIST + " 的 AnimatedMember 不得在组合期读进度"
                        + "（`val p = progress.value` 会让动画每帧重组整条消息，"
                        + "正文一起重建 → 动画被淹没）：读数必须放进 graphicsLayer 的 lambda");
        requireContains(animatedMember, "val v = progress.value",
                CHAT_LIST + " 的 AnimatedMember 必须在 graphicsLayer 的 lambda 里读进度"
                        + "（`val v = progress.value` 写在 lambda 内 = 绘制期读，动画期间不重组）");

        // ⚠️ 第 32 节到此为止只覆盖"文字成员进入"。下面 33 管的是**同一条链路的性能前提** ——
        // 逐帧重组不解决，上面这些动画再多也看不出来。

        // ---- 33. 悬浮层的底部留白与 IME 抬起（性能契约）------------------------
        //
        // 用户报「操作 5、6、7 都没有动画」，而这三条的共同前提是**列表不在每帧重组**。
        // 实测（真机 zhi-frame.log，逐秒聚合的 recompose 计数）：
        //
        //     recompose/s ChatArea=69 ChatList=64     ← 60Hz 下就是每帧一次
        //     recompose/s Composer=1 ChatList=1 ChatArea=1
        //
        // 关键在**同一行的 Composer 只有 1**：Composer 是 ChatArea 的子级、参数里
        // **没有** bottomInset，所以它被跳过了；而 ChatList 的参数里有 bottomInset，
        // 于是跟着抖。这组数字就是这个机制的指纹，不是巧合。
        //
        // 成因（两处，都在这一节钉住）：
        //   ① `onSizeChanged` 挂在 `padding(bottom = imeLift)` **左边** → 报出来的高度
        //      含 IME → 键盘动画期间每帧变 → ChatArea 每帧重组 → bottomInset 每帧变
        //      → ChatList 每帧重组。
        //   ② `imeLift` 本身是组合期算出来的 `Dp` → 同样是每帧重组。
        //
        // 这一节的断言都是「正反成对」的：只钉"新写法在"，把旧写法改回去还能绿是没用的。
        String chatAreaForPerf = stripComments(read(root, CHAT_AREA));

        // ① 高度必须分成两段，且列表那份只认"停稳"的 IME 值。
        requireContains(chatAreaForPerf, "var floatingContentHeightPx by remember { mutableStateOf(0) }",
                CHAT_AREA + " 必须把悬浮层高度存成**内容**高度（floatingContentHeightPx）："
                        + "存成含 IME 的合并高度，键盘一动它就每帧变");
        requireContains(chatAreaForPerf, "var settledImeLiftPx by remember { mutableStateOf(0) }",
                CHAT_AREA + " 必须有「停稳」的 IME 抬起量（settledImeLiftPx）专门喂给列表留白");
        requireContains(chatAreaForPerf, "private const val ImeSettleMs = 120L",
                CHAT_AREA + " 必须有 ImeSettleMs 常量（去抖窗口）："
                        + "没有它就只能把逐帧的 IME 值直接塞进 bottomInset");
        // ② 那份 IME 值必须经 snapshotFlow + 去抖，而不是组合期读。
        requireContains(chatAreaForPerf, "delay(ImeSettleMs)",
                CHAT_AREA + " 的 IME 去抖必须真的等 ImeSettleMs（collectLatest + delay）："
                        + "只声明常量不用，等于没去抖");
        require(!chatAreaForPerf.contains("val imeLift = if ("),
                CHAT_AREA + " 不得再在组合期算出 imeLift 这个 Dp："
                        + "组合期读 WindowInsets.ime = 键盘动画每帧重组整棵 ChatArea");
        require(!chatAreaForPerf.contains("padding(bottom = imeLift)"),
                CHAT_AREA + " 不得再用 padding(bottom = imeLift)："
                        + "padding 会把它算进节点尺寸，于是 onSizeChanged 报的高度又含 IME");
        // ③ 抬起必须走布局阶段的 lambda。
        requireContains(chatAreaForPerf, ".offset {",
                CHAT_AREA + " 的 IME 抬起必须走 Modifier.offset { }（lambda 在布局阶段求值）："
                        + "这样键盘动画每帧只让布局失效，组合一次都不跑");
        requireContains(chatAreaForPerf, "imeInsets.getBottom(this)",
                CHAT_AREA + " 必须在 offset 的 lambda 里读 insets（imeInsets.getBottom(this)）："
                        + "挪到组合里就又变成每帧重组了");
        // ④ 量高度的那一处必须在 offset **右边**，且报的是内容高度。
        requireContains(chatAreaForPerf, ".onSizeChanged { floatingContentHeightPx = it.height },",
                CHAT_AREA + " 的 onSizeChanged 必须在 offset { } **右边**并回报内容高度："
                        + "放到左边量到的是含 IME 的合并高度（这就是每帧重组的源头）");
        // ⑤ 全屏浮层那条老语义不许丢：盖住时既不抬、留白也不算键盘。
        requireContains(chatAreaForPerf, "if (liftByFullScreenOverlay) {",
                CHAT_AREA + " 必须保留「全屏浮层盖住时不抬」的语义"
                        + "（否则侧栏里的搜索框一提键盘，后面的对话输入器会跟着抬起来 ——"
                        + "这是用户实测报过的 bug）");
        requireContains(chatAreaForPerf, "settledImeLiftPx = 0",
                CHAT_AREA + " 在浮层盖住时还得把 settledImeLiftPx 归零："
                        + "只让位移不抬、留白却仍算着键盘那一段，列表底部会白留一截");

        // ⑥ 流式光标的呼吸闪烁同理：alpha 必须在绘制期读。
        //
        // 这个无限动画原先写成 `val cursorAlpha by cursor.animateFloat(…)` 再
        // `graphicsLayer { alpha = cursorAlpha }` —— 而 `by` 就是 `getValue()`，
        // 它在**组合期**把当前值读出来，于是整张 AssistantCard 每帧重组一次
        // （正文 Markdown 一起重建）。注释当年还写着"走 draw 层，不重组"，与事实相反。
        String assistantCard = bodyOf(cardsForMotion, "fun AssistantCard(");
        require(!assistantCard.isEmpty(),
                MESSAGE_CARDS + " 找不到 AssistantCard 的正文（签名变了？）");
        require(!assistantCard.contains("val cursorAlpha by"),
                MESSAGE_CARDS + " 的流式光标不得写成 `val cursorAlpha by cursor.animateFloat(…)`："
                        + "那个 `by` 是组合期读，会让整张卡片每帧重组");
        requireContains(assistantCard, "alpha = cursorAlpha.value",
                MESSAGE_CARDS + " 的流式光标必须在 graphicsLayer 的 lambda 里读 alpha"
                        + "（`alpha = cursorAlpha.value` = 绘制期读，动画期间不重组）");
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
