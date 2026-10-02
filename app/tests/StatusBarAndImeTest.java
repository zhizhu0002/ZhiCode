import java.nio.file.*;
import java.util.*;

/**
 * 状态栏（#12）与终端输入法（#11）的守卫。
 *
 * <h2>状态栏：用户说「每次打开软件上面黑乎乎的」</h2>
 *
 * 真因是**清单里的主题**：{@code Theme.Material.NoActionBar.Fullscreen} 把状态栏
 * 整个藏掉，屏幕最上方留一条黑边。这类问题在代码里完全看不出来（Compose 侧一切正常），
 * 只有清单里那一行决定它 —— 所以必须静态钉住清单。
 *
 * <h2>终端输入法：用户说「有部分设备不是软件输入法而是系统的安全输入法」</h2>
 *
 * {@code shouldEnforceCharBasedInput()} 恒 true 时走
 * {@code TYPE_TEXT_VARIATION_VISIBLE_PASSWORD}，部分输入法把它当密码/安全键盘。
 * 现在改由设置项驱动（默认 false = {@code TYPE_NULL} = 正常软件输入法）。
 */
public final class StatusBarAndImeTest {

    private static final String MANIFEST = "app/src/main/AndroidManifest.xml";
    private static final String ACTIVITY =
            "app/src/main/java/com/zhizhu/zhicode/compose/MainActivity.kt";
    /**
     * 状态栏明暗的**实现**处。
     *
     * 它原先写在 MainActivity 里，现在收进 ZhiThemeMode 供两个 Activity 共用 ——
     * 于是断言也拆成两半：调用点在 MainActivity、实现在这里。
     * 只留调用点（实现被删）时两边会一起变哑，所以两边都要钉。
     */
    private static final String THEME =
            "app/src/main/java/com/zhizhu/zhicode/compose/theme/ZhiThemeMode.kt";
    private static final String PANE =
            "app/src/main/java/com/zhizhu/zhicode/TermuxTerminalPane.java";
    private static final String STORE =
            "app/src/main/java/com/termux/app/zhicode/storage/ApiSettingsStore.java";
    private static final String SETTINGS =
            "app/src/main/java/com/zhizhu/zhicode/compose/ui/settings/SettingsDialog.kt";
    private static final String VM =
            "app/src/main/java/com/zhizhu/zhicode/compose/state/WorkspaceViewModel.kt";

    private static String squash(String text) {
        return text.replaceAll("\\s+", "");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static String read(String root, String relative) throws Exception {
        return new String(Files.readAllBytes(Paths.get(root, relative)),
                java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String stripComments(String text) {
        String noBlock = text.replaceAll("(?s)/\\*.*?\\*/", " ");
        return noBlock.replaceAll("(?m)//[^\\n]*", " ");
    }

    public static void main(String[] args) throws Exception {
        String root = args.length > 0 ? args[0] : ".";
        String manifest = read(root, MANIFEST);
        String activity = stripComments(read(root, ACTIVITY));
        String themeMode = stripComments(read(root, THEME));
        String pane = stripComments(read(root, PANE));
        String store = stripComments(read(root, STORE));
        String settings = stripComments(read(root, SETTINGS));
        String vm = stripComments(read(root, VM));

        // ---- 1. 状态栏必须显示出来 -------------------------------------------
        require(!manifest.contains("NoActionBar.Fullscreen"),
                MANIFEST + " 的应用主题不得再是 Fullscreen：那一档会把状态栏整个藏掉，"
                        + "表现就是用户说的「每次打开软件上面黑乎乎的」。"
                        + "状态栏区域的避让交给 Miuix TopAppBar（它默认带 "
                        + "defaultWindowInsetsPadding）。");
        require(manifest.contains("@android:style/Theme.Material.NoActionBar\""),
                MANIFEST + " 的默认主题应当是 Theme.Material.NoActionBar（可显示状态栏）");
        require(activity.contains("enableEdgeToEdge()"),
                ACTIVITY + " 必须开启 edge-to-edge：状态栏透明后由内容铺满，"
                        + "否则会出现一条与背板不同色的状态栏色带");
        require(activity.contains("isAppearanceLightStatusBars")
                        || activity.contains("ZhiThemeMode.appliedTo("),
                ACTIVITY + " 必须按主题设置状态栏图标明暗（isAppearanceLightStatusBars）："
                        + "浅色主题下白图标在白底上完全看不见。"
                        + "实现收在 ZhiThemeMode.appliedTo 里也可以，但这里必须调到它。");
        require(themeMode.contains("isAppearanceLightStatusBars"),
                THEME + " 必须真的设置状态栏图标明暗：两个 Activity 都转发到这里，"
                        + "只留下调用点而实现没了的话两边一起变哑");
        // 明暗必须**跟着 dark 走**，不能写死。写死一个值也能编译、也是"设了明暗"，
        // 但浅色主题下白图标压在白背板上 —— 用户报的那个症状会原样回来。
        require(squash(themeMode).contains("isAppearanceLightStatusBars=!dark")
                        && squash(themeMode).contains("isAppearanceLightNavigationBars=!dark"),
                THEME + " 的状态栏/导航栏图标明暗必须由 dark 取反得到（= !dark）："
                        + "写死成常量的话，浅色主题下白图标在白底上完全看不见 —— "
                        + "这正是用户报的「上面黑乎乎的」另一面");
        require(themeMode.contains("ThemeMode.SYSTEM"),
                THEME + " 判定深浅必须覆盖 ThemeMode.SYSTEM 分支："
                        + "否则「跟随系统」时图标明暗会固定在一种颜色上");
        require(!activity.contains("hide(WindowInsetsCompat"),
                ACTIVITY + " 不得隐藏状态栏 —— 用户明确要求「把状态栏显示出来」");

        // ---- 2. 终端输入法由设置项驱动，且默认走正常输入法 -------------------
        require(pane.contains("getTerminalCharMode(getContext())"),
                PANE + " 的 shouldEnforceCharBasedInput() 必须读设置项。恒 true 时走 "
                        + "TYPE_TEXT_VARIATION_VISIBLE_PASSWORD，部分输入法会把它当成"
                        + "安全/密码键盘 —— 这正是用户报告的「调出来的不是我常用的输入法」。");
        require(!pane.matches("(?s).*shouldEnforceCharBasedInput\\(\\)\\s*\\{\\s*return true;.*"),
                PANE + " 的 shouldEnforceCharBasedInput() 不得再写死 return true");
        require(store.contains("getTerminalCharMode") && store.contains("setTerminalCharMode"),
                STORE + " 必须提供终端字符模式的读与写");
        require(store.contains("getBoolean(Key.TERMINAL_CHAR_MODE, false)"),
                STORE + " 的默认值必须是 **false**（正常软件输入法）："
                        + "默认 true 等于把「部分设备会调出安全键盘」这个 bug 留给所有人");
        require(settings.contains("terminalCharMode"),
                SETTINGS + " 必须在设置页暴露这个开关：两种输入类型各有代价，"
                        + "只能由用户按自己机型选");
        require(vm.contains("setTerminalCharMode") && vm.contains("getTerminalCharMode"),
                VM + " 必须落盘并在启动时读回该开关：不落盘就是「设置了重启就丢」");
    }
}
