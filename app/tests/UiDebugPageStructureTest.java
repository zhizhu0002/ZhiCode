import java.nio.file.*;
import java.util.*;

/**
 * 「UI 调试页」与侧栏入口的守卫。
 *
 * <p>它守的是五件「改坏了不会编译失败」的事：
 *
 * <ol>
 *   <li><b>调试页只能从 debug 构建进得去</b>。它是一个纯调试面板（字母/色板/样例对话，
 *       对用户没有意义）。发布包里多出一个入口不会报错，只会让人问"这是什么"。</li>
 *   <li><b>它必须真的铺开生产组件</b>。这一页全部价值就是"看到的即真实组件"；
 *       重写时若退化成几张静态贴图（或只留一两个组件），编译通过、看着还在，
 *       而它已经测不出任何东西了。</li>
 *   <li><b>它不能自己写死视觉</b>。一旦页里出现裸字号或硬编码颜色，它就与真实的
 *       设置页/对话页脱钩，开始骗人 —— 那比没有这一页更糟。</li>
 *   <li><b>它必须可互动、且对话流覆盖各状态</b>。点按钮要能改样例数据（含"正在运行"
 *       的工具），长按要能复制（含复制图片信息）并弹吐司；否则它退回静态画廊。</li>
 *   <li><b>侧栏不得再长出「技能 / 自定义角色卡」</b>。这两项已按"配置类入口只留设置页
 *       一条路径"收敛掉；重新加回来是两行代码的事，而且不会有任何编译或运行期症状。</li>
 * </ol>
 */
public final class UiDebugPageStructureTest {

    private static final String SRC = "app/src/main/java/com/zhizhu/zhicode/compose/";

    private static final String DEBUG_PAGE = SRC + "ui/debug/UiDebugPage.kt";
    private static final String SETTINGS_DIALOG = SRC + "ui/settings/SettingsDialog.kt";
    private static final String APP_SCAFFOLD = SRC + "ui/AppScaffold.kt";
    private static final String SIDEBAR = SRC + "ui/Sidebar.kt";
    private static final String CHAT_AREA = SRC + "ui/ChatArea.kt";
    private static final String CHAT_LIST = SRC + "ui/chat/ChatList.kt";

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

    /** 断言 [haystack] 含 [needle]，失败时给出 [message]。 */
    private static void requireText(String haystack, String needle, String message) {
        require(haystack.contains(needle), message);
    }

    public static void main(String[] args) throws Exception {
        String root = args.length > 0 ? args[0] : ".";

        // ---- 1. 调试页存在，且只有一处定义 ----------------------------------
        String page = stripComments(read(root, DEBUG_PAGE));
        require(page.contains("fun UiDebugPage("),
                DEBUG_PAGE + " 必须定义 UiDebugPage()");

        // ---- 2. 入口被 BuildConfig.DEBUG 包住 ---------------------------------
        String settings = stripComments(read(root, SETTINGS_DIALOG));
        int gate = settings.indexOf("BuildConfig.DEBUG");
        int entry = settings.indexOf("\"uiDebug\"");
        require(gate >= 0, SETTINGS_DIALOG + " 里的 UI 调试入口必须用 BuildConfig.DEBUG 门控");
        require(entry > gate,
                "UI 调试入口必须在 BuildConfig.DEBUG 之后（即位于同一个 if 块内）");
        require(entry - gate < 400,
                "BuildConfig.DEBUG 与 UI 调试入口相距过远，看起来不在同一个 if 块里；"
                        + "请让门控与入口紧邻，别把开关与它守的东西拆开");

        // ---- 3. 必须真的调用生产组件 ----------------------------------------
        String[] required = {
                "UserBubble(", "AssistantCard(", "ToolGroupCard(", "ErrorCard(", "InfoCard(",
                "EmptyState(", "AgentProgressCard(", "Composer(", "ZhiSegmentedTabs(",
                "SettingsGroup(",
        };
        for (String call : required) {
            require(page.contains(call),
                    "UI 调试页必须调用生产组件 " + call
                            + " —— 这一页存在的意义就是\"看到的即真实组件\"，"
                            + "退化成静态贴图后它测不出任何问题。若某个组件确实不该出现在这里，"
                            + "请在本测试的 required 列表里显式删掉它并写明理由。");
        }

        // ---- 4. 不得自己写死视觉 --------------------------------------------
        require(!page.matches("(?s).*fontSize\\s*=\\s*[0-9].*"),
                "UI 调试页不得出现裸数字字号，必须用 ZhiTextScale 档位");
        require(!page.contains("Color(0x"),
                "UI 调试页不得硬编码颜色，必须走 MiuixTheme.colorScheme / ZhiColors");
        // 用「不引入 compose.animation 包」表达「这一页不自己做动效」：
        // 直接扫 `tween(` / `spring(` 是不行的 —— 页里那段样例对话的正文就写着
        // `tween(150, easing = SinOutEasing)`（它是**要展示的文本**，不是代码），
        // 文本级断言分不清字符串与代码，所以改从 import 上守。
        require(!page.contains("import androidx.compose.animation"),
                "UI 调试页不得自己写动效（不要引入 androidx.compose.animation）："
                        + "它展示的是组件在真实页面里的样子，自带一套曲线会与之不一致");
        require(!page.contains("import androidx.compose.foundation.animation"),
                "UI 调试页不得自己写动效（不要引入 foundation.animation）");

        // ---- 5. 必须**可互动** ----------------------------------------------
        // 这一页的价值一半在"点了有反应"。下面每一条都是"删掉不会编译失败、
        // 只会退化成静态贴图"的东西。
        require(page.contains("mutableStateListOf"),
                "UI 调试页的样例数据必须是**可变**状态，否则按钮点了界面不会变");
        for (String control : new String[]{
                "追加用户消息", "起一个工具", "让运行中的成功", "让运行中的失败",
                "放行等待授权", "重置样例对话", "推进任务状态", "切到终端", "打开侧栏",
        }) {
            require(page.contains(control),
                    "UI 调试页必须保留可互动控件「" + control
                            + "」—— 静态画廊看不出状态切换对不对");
        }

        // ---- 6. 对话流必须覆盖各状态（含**运行中**的工具） --------------------
        require(page.contains("streaming = true"),
                "对话流样例必须含一条**流式**回复");
        require(page.contains("thinkingExpanded = false"),
                "对话流样例必须含**可展开的思考内容**");
        require(page.contains("thinkingExpanded = true"),
                "对话流样例必须含**已展开**的思考内容（两种状态都要看到）");
        require(page.contains("awaitingPermission = true"),
                "对话流样例必须含**等待授权**的工具行");
        require(page.contains("completed = false"),
                "对话流样例必须含**运行中**的工具行（这是最容易漏的一态）");
        require(page.contains("exitCode = 1"),
                "对话流样例必须含**失败**的工具行（带退出码）");
        require(page.contains("expanded = true"),
                "对话流样例必须含**已展开输出**的工具行");
        require(page.contains("aspectRatio"),
                "媒体区必须真的按比例布局图片（写死高度就测不出极端比例下的圆角裁切）");

        // 工具分组：五种 ToolKind 都要有样例，否则分组文案漏掉一类没人发现。
        for (String kind : new String[]{
                "ToolKind.SEARCH", "ToolKind.READ", "ToolKind.EDIT", "ToolKind.COMMAND", "ToolKind.OTHER",
        }) {
            require(page.contains(kind),
                    "对话流样例必须覆盖 " + kind + "：分组文案是按 kind 拼的，"
                            + "少一类就可能出现\"搜索 0 个模式\"这种空话");
        }
        require(page.contains("groupCompleted = true"),
                "对话流样例必须含**已全部完成**的工具组（标题走另一条分支）");
        require(page.contains("contextTokens = 191_000"),
                "对话流样例必须含**上下文接近上限**的回复：脚注会变色，"
                        + "不摆一份就永远看不到那个状态");

        // 任务卡：三种状态、单条、超上限、超长标题都要能看到。
        require(page.contains("TaskCardLabel("),
                "任务卡区必须分组标注每一张卡在演示什么（六张卡不标注就分不清差别）");
        require(page.contains("maxTasks = 2"),
                "任务卡必须演示**窄屏上限**（maxTasks=2）：悬浮卡过高会盖住对话");
        require(page.contains("超长标题与详情"),
                "任务卡必须演示超长标题/详情（省略号与卡片高度都在这里才会暴露）");

        // ---- 6b. 对话面板：底部留白必须**实测**，不得写死 ----
        //
        // 真机上出现过：滑到最底部仍有内容被输入器盖住。真因是留白是两个写死的常量
        // （输入器 92dp + 任务卡 140dp），而悬浮层的高度会变 —— 输入器多长一行、
        // 挂上附件条、开了调试模式的 Markdown 实时预览、任务卡里任务变多，
        // 写死的值都不会跟着变。这件事不会编译失败，只在屏幕上表现为"被挡住"。
        String chatArea = stripComments(read(root, CHAT_AREA));
        requireText(chatArea, "onSizeChanged",
                CHAT_AREA + " 必须**实测**底部悬浮层高度（onSizeChanged）来算对话列表的底部留白。"
                        + "写死常量会随内容变化而失准：输入器长高、挂附件、开调试预览、任务变多 —— "
                        + "表现就是\"滑到底还有内容被遮住\"。");
        requireText(chatArea, "bottomInset = bottomInset",
                "实测出来的高度必须真的接到 ChatList 的 bottomInset 上（算了不用等于没算）");
        String[] deadInsets = {"ComposerInset", "TaskCardInset"};
        for (String dead : deadInsets) {
            require(!chatArea.contains(dead),
                    CHAT_AREA + " 不得再出现写死的 " + dead
                            + "：那两个常量正是\"不随内容自适应\"的来源。要调余量请改 "
                            + "FloatingBottomGap / MinFloatingInset 并说明理由。");
        }

        // ---- 6c. 对话面板（调试模式）必须把细节全摊开 + 内联任务清单 ----
        String chatList = stripComments(read(root, CHAT_LIST));
        requireText(chatList, "debugTasks",
                "ChatList 必须接受 debugTasks：完整任务清单要在调试模式下内联进对话流"
                        + "（悬浮卡只显示前 2 条，剩下的原本只在另一个窗口里）");
        requireText(chatList, "fullyExpanded(",
                "调试模式必须把思考/工具输出**默认全展开**：排版问题只在内容全铺开时才看得出来");
        requireText(chatList, "thinkingExpanded = true",
                "全展开必须覆盖思考面板");
        requireText(chatList, "expanded = true",
                "全展开必须覆盖工具输出");
        requireText(chatList, "InlineTaskList(",
                "必须有内联的完整任务清单（不过滤条数、不打折信息）");
        requireText(chatArea, "debugTasks = if (state.debugAppMode) state.tasks else emptyList()",
                "内联任务清单只在调试模式下传数据：非调试时必须是空列表（不渲染）");

        // ---- 7. 长按复制 + 吐司 ---------------------------------------------
        require(page.contains("ZhiAnchoredActionMenu("),
                "长按菜单必须用生产组件 ZhiAnchoredActionMenu（自己写一个居中对话框会与真实"
                        + "对话页的锚定行为不一致，也就测不出问题）");
        require(page.contains("rememberFingerTracker()"),
                "长按菜单必须从**手指位置**长出来（用生产组件 rememberFingerTracker）");
        require(page.contains("Clipboard.copy("),
                "复制必须走工程统一的 Clipboard（它处理了空内容不得清空剪贴板这个坑）");
        require(page.contains("Toast.makeText("),
                "复制后必须弹**吐司**提示：调试时最要紧的就是\"我点了它到底有没有生效\"。"
                        + "注意不要改成生产路径那种静默复制 —— 安卓 13+ 系统虽然也会弹，"
                        + "但这里需要的是当场可见的反馈。");
        require(page.contains("复制图片信息"),
                "图片必须能复制**图片信息**（文件名/尺寸/体积/类型）");
        require(page.contains("fun info()"),
                "图片信息必须由数据本身给出（DebugItem.Image.info()），"
                        + "否则改名/改字段后文案会与实际不一致");
        require(page.contains("InteractionLog("),
                "必须有交互日志：没有它就只能靠猜\"刚才那一按到底有没有响应\"");

        // ---- 8. 页面栈接线：注册了 entry，且返回优先关它 ----------------------
        String scaffold = stripComments(read(root, APP_SCAFFOLD));
        require(scaffold.contains("entry<AppKey.UiDebug>"),
                APP_SCAFFOLD + " 必须把 UI 调试页注册进 NavDisplay 的页面栈");
        int onBackBlock = scaffold.indexOf("onBack = {");
        require(onBackBlock > 0, APP_SCAFFOLD + " 里找不到 NavDisplay 的 onBack 分支");
        String onBack = scaffold.substring(onBackBlock);
        int uiDebug = onBack.indexOf("closeUiDebug()");
        int apiConfig = onBack.indexOf("closeApiConfig()");
        require(uiDebug > 0 && apiConfig > 0,
                "onBack 必须同时处理 UI 调试页与设置子页的关闭");
        require(uiDebug < apiConfig,
                "onBack 的分支顺序必须与页面栈深度一致：UI 调试页在设置子页之上，"
                        + "所以它必须先被判断 —— 顺序反了会一次返回直接跳掉两层。");

        // ---- 9. 侧栏不得再长出技能 / 自定义角色卡 -----------------------------
        String sidebar = stripComments(read(root, SIDEBAR));
        for (String gone : new String[]{"onSkills", "onRoleCard", "技能", "自定义角色卡"}) {
            require(!sidebar.contains(gone),
                    "侧栏不得再出现「" + gone + "」：配置类入口只保留设置页一条路径"
                            + "（见 SettingsDialog 的 ExtensionsPage）。"
                            + "要恢复的话请连同这条断言一起改，并写清为什么要两条路径。");
        }
    }
}
