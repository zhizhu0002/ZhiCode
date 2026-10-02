import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * 「深浅只有一个来源」的守卫。
 *
 * <p>用户报的是「全局黑白模式是有问题的（沙箱在浅色模式下仍有问题）」和
 * 「深浅色模式很不完善」。根因不是某个颜色取错了，而是**判定本身有三份**，
 * 而且三份不一样：
 *
 * <pre>
 *   ZhiCodeApp           读应用设置 ThemeMode          （对）
 *   MainActivity 状态栏   读应用设置 ThemeMode          （对）
 *   SandboxBoard 沙箱页   读 isSystemInDarkTheme()      （错）
 * </pre>
 *
 * <p>「设置里选浅色、系统是深色」时，主界面浅色、沙箱页整片深色。默认主题是
 * 「跟随系统」，所以在开发机上永远看不出来。
 *
 * <p>这类缺陷的形状很固定：**每一处单独看都说得通，只是没人负责让它们相等**。
 * 所以这条守不是钉某个色值，而是钉「判定只能出现一次」：
 *
 * <ol>
 *   <li>{@code theme/ZhiThemeMode.kt} 之外不许出现 {@code isSystemInDarkTheme()}；</li>
 *   <li>不许再手写 {@code ThemeMode.LIGHT -> false} 这种分支表；</li>
 *   <li>两个 Activity 的状态栏都走同一个 [ZhiThemeMode.appliedTo]；</li>
 *   <li>应用内换主题时状态栏要跟着换（只在 onResume 应用是不够的）；</li>
 *   <li>判定必须有单测。</li>
 * </ol>
 */
public final class ThemeConsistencyTest {

    private static final String THEME_MODE =
            "app/src/main/java/com/zhizhu/zhicode/compose/theme/ZhiThemeMode.kt";
    private static final String SRC = "app/src/main/java";
    private static final String MAIN_ACTIVITY =
            "app/src/main/java/com/zhizhu/zhicode/compose/MainActivity.kt";
    private static final String APP_SCAFFOLD =
            "app/src/main/java/com/zhizhu/zhicode/compose/ui/AppScaffold.kt";
    private static final String SANDBOX_BOARD =
            "app/src/main/java/com/zhizhu/zhicode/sandbox/SandboxBoard.kt";
    private static final String THEME_TEST =
            "app/src/test/java/com/zhizhu/zhicode/compose/theme/ZhiThemeModeTest.kt";

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static String read(String root, String relative) throws Exception {
        return new String(Files.readAllBytes(Paths.get(root, relative)),
                java.nio.charset.StandardCharsets.UTF_8);
    }

    /** 去掉注释：注释里会**讨论**这些写法，不该被当成违规。 */
    private static String stripComments(String text) {
        String noBlock = text.replaceAll("(?s)/\\*.*?\\*/", " ");
        return noBlock.replaceAll("(?m)//[^\\n]*", " ");
    }

    private static String squash(String text) {
        return text.replaceAll("\\s+", "");
    }

    private static List<String> kotlinSources(String root) throws Exception {
        List<String> out = new ArrayList<>();
        Path base = Paths.get(root, SRC);
        if (!Files.isDirectory(base)) return out;
        try (java.util.stream.Stream<Path> stream = Files.walk(base)) {
            stream.filter(p -> p.toString().endsWith(".kt")).forEach(p -> out.add(
                    base.relativize(p).toString().replace('\\', '/')));
        }
        Collections.sort(out);
        return out;
    }

    private static int lineOf(String text, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < text.length(); i++) {
            if (text.charAt(i) == '\n') line++;
        }
        return line;
    }

    public static void main(String[] args) throws Exception {
        String root = args.length > 0 ? args[0] : ".";

        // ---- 1. 系统深浅只能在一个文件里读 --------------------------------
        List<String> systemDarkReaders = new ArrayList<>();
        for (String file : kotlinSources(root)) {
            String text = stripComments(read(root, SRC + "/" + file));
            if (text.contains("isSystemInDarkTheme(")) {
                systemDarkReaders.add(SRC + "/" + file);
            }
        }
        require(systemDarkReaders.size() == 1 && systemDarkReaders.get(0).endsWith("theme/ZhiThemeMode.kt"),
                "「系统是否深色」只允许 theme/ZhiThemeMode.kt 读一次，现在出现在：\n  "
                        + String.join("\n  ", systemDarkReaders)
                        + "\n第二处读系统深浅的地方，就是「设置里选浅色、沙箱页却整片深色」的来源。"
                        + "Compose 侧要深浅请用 ZhiThemeMode.rememberCurrentDark(...)。");

        // ---- 2. 不许再手写「模式 → 深浅」的分支表 -------------------------
        List<String> branchTables = new ArrayList<>();
        for (String file : kotlinSources(root)) {
            if (file.endsWith("theme/ZhiThemeMode.kt")) continue;
            String text = stripComments(read(root, SRC + "/" + file));
            Matcher m = Pattern.compile("ThemeMode\\.(LIGHT|DARK)\\s*->\\s*(true|false)").matcher(text);
            while (m.find()) {
                branchTables.add(SRC + "/" + file + ":" + lineOf(text, m.start()) + "  " + m.group());
            }
        }
        require(branchTables.isEmpty(),
                "「模式 → 是否深色」的分支表只允许出现在 ZhiThemeMode.resolve：\n  "
                        + String.join("\n  ", branchTables)
                        + "\n每写一份就多一个「忘了改」的地方 —— 沙箱页那次就是这么错的。");

        // ---- 3. 判定本体：函数在、三档齐、必须有单测 ----------------------
        String themeMode = stripComments(read(root, THEME_MODE));
        String sqTheme = squash(themeMode);
        require(sqTheme.contains("funresolve(mode:ThemeMode,systemDark:Boolean):Boolean"),
                THEME_MODE + " 必须提供 resolve(mode, systemDark) —— 纯函数才测得了");
        for (String tier : new String[]{"ThemeMode.LIGHT->false", "ThemeMode.DARK->true",
                "ThemeMode.SYSTEM->systemDark"}) {
            require(sqTheme.contains(tier),
                    THEME_MODE + " 的 resolve 缺少分支 " + tier + "："
                            + "三档主题必须各有明确结果，不能有「掉进去也算」的默认分支");
        }
        require(themeMode.contains("ApiSettingsStore.getThemeMode"),
                THEME_MODE + " 必须从**落盘**的设置读 ThemeMode："
                        + "只读内存状态的话，独立启动的沙箱页永远看不到用户选的主题");
        require(themeMode.contains("fun appliedTo(activity: Activity, dark: Boolean)"),
                THEME_MODE + " 必须提供 appliedTo(activity, dark)："
                        + "状态栏/导航栏的透明与图标明暗要一处实现，两个 Activity 共用");
        require(themeMode.contains("fun ApplySystemBars("),
                THEME_MODE + " 必须提供 ApplySystemBars："
                        + "只在 onResume 应用是不够的 —— 用户**在应用内**换主题时不会 resume，"
                        + "状态栏图标会一直停在旧明暗（浅色主题下白图标压在白背板上看不见）");

        // ---- 4. 三个入口都必须走它 ---------------------------------------
        String main = stripComments(read(root, MAIN_ACTIVITY));
        require(squash(main).contains("ZhiThemeMode.appliedTo(")
                        && main.contains("ZhiThemeMode.resolve("),
                MAIN_ACTIVITY + " 的状态栏必须走 ZhiThemeMode.appliedTo/resolve");

        String app = stripComments(read(root, APP_SCAFFOLD));
        require(squash(app).contains("ZhiThemeMode.rememberCurrentDark(state.themeMode)"),
                APP_SCAFFOLD + " 的 isDark 必须取自 ZhiThemeMode.rememberCurrentDark");
        require(squash(app).contains("ZhiThemeMode.ApplySystemBars(isDark)"),
                APP_SCAFFOLD + " 必须调用 ZhiThemeMode.ApplySystemBars："
                        + "应用内换主题时状态栏图标要跟着换");

        String sandbox = stripComments(read(root, SANDBOX_BOARD));
        require(squash(sandbox).contains("ZhiThemeMode.rememberCurrentDark(context)"),
                SANDBOX_BOARD + " 必须读**应用设置**里的主题（ZhiThemeMode.rememberCurrentDark）："
                        + "它原先读 isSystemInDarkTheme()，于是「设置里选浅色、系统是深色」时"
                        + "这一屏整片深色 —— 用户报的正是它");
        require(squash(sandbox).contains("ZhiThemeMode.ApplySystemBars(isDark)"),
                SANDBOX_BOARD + " 也必须应用状态栏明暗（与主界面同一处实现）");
        require(squash(sandbox).contains("enableEdgeToEdge()"),
                SANDBOX_BOARD + " 必须 enableEdgeToEdge：否则这一屏的状态栏由框架主题上色，"
                        + "会出现一条与背板不同的色带");

        // ---- 5. 判定必须有单测 -------------------------------------------
        String test = stripComments(read(root, THEME_TEST));
        require(test.contains("class ZhiThemeModeTest"),
                THEME_TEST + " 必须存在");
        for (String caseName : new String[]{
                "lightModeIsLightEvenWhenSystemIsDark",
                "darkModeIsDarkEvenWhenSystemIsLight",
                "systemModeFollowsTheSystem",
                "explicitModeOverridesTheSystem"}) {
            require(test.contains(caseName),
                    THEME_TEST + " 缺少用例 " + caseName + "："
                            + "这四条正是「应用设置优先于系统」的全部内容，"
                            + "少一条就有一条语义可以被悄悄改掉");
        }
    }
}
