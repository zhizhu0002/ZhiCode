import java.nio.file.*;
import java.util.*;

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
        requireContains(chatList, "Markdown 源码",
                "主体调试模式必须能摊出原始 Markdown 源码："
                        + "看到渲染不对时，第一件事就是对照源码判断是素材还是渲染器的问题");
        requireContains(chatList, "onSurfaceVariantSummary",
                "调试条的配色必须走主题令牌（自己写死颜色会在浅色模式下糊掉）");

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
    }
}
