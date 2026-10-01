import java.nio.file.*;
import java.util.*;

/**
 * 「UI 调试页」与侧栏入口的守卫。
 *
 * <p>它守的是四件「改坏了不会编译失败」的事：
 *
 * <ol>
 *   <li><b>调试页只能从 debug 构建进得去</b>。它是一个纯调试面板（字母/色板/样例对话，
 *       对用户没有意义）。发布包里多出一个入口不会报错，只会让人问"这是什么"。</li>
 *   <li><b>它必须真的铺开生产组件</b>。这一页全部价值就是"看到的即真实组件"；
 *       重写时若退化成几张静态贴图（或只留一两个组件），编译通过、看着还在，
 *       而它已经测不出任何东西了。</li>
 *   <li><b>它不能自己写死视觉</b>。一旦页里出现裸字号或硬编码颜色，它就与真实的
 *       设置页/对话页脱钩，开始骗人 —— 那比没有这一页更糟。</li>
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

        // ---- 5. 页面栈接线：注册了 entry，且返回优先关它 ----------------------
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

        // ---- 6. 侧栏不得再长出技能 / 自定义角色卡 -----------------------------
        String sidebar = stripComments(read(root, SIDEBAR));
        for (String gone : new String[]{"onSkills", "onRoleCard", "技能", "自定义角色卡"}) {
            require(!sidebar.contains(gone),
                    "侧栏不得再出现「" + gone + "」：配置类入口只保留设置页一条路径"
                            + "（见 SettingsDialog 的 ExtensionsPage）。"
                            + "要恢复的话请连同这条断言一起改，并写清为什么要两条路径。");
        }
    }
}
