import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 侧栏导航的守卫：**点侧栏里的任何一行都必须把侧栏收起来**。
 *
 * <p><b>为什么需要这条。</b>侧栏是画在内容**上层**的浮层（`AppScaffold` 的 Sidebar
 * 分支），而各个目标的 `openXxx()` 只负责把自己那面页推上来，谁也没管侧栏。
 * 于是点「设置」→ 设置页铺满，但侧栏还盖在上面；点「运行环境就绪」同理；
 * 点「ZhiCode 沙箱」更明显（那是个跨进程的 Activity）。用户看到一层"点不动"的
 * 界面，得先返回一次才回到目标页。
 *
 * <p><b>为什么它必须由文本断言来守。</b>这个缺陷编译完全通过、运行也不报错：
 * 目标页**确实**打开了，只是被盖住了。单元测试碰不到 Compose 的层级，
 * 只有在设备上点一次才看得出来。属于本仓库定义的那一类：
 * 「写错了不会编译失败，只是功能悄悄没了」。
 *
 * <p>判据落在**接线**上：`Sidebar.kt` 的每一个 `onXxx` 回调，最终都要接到一个
 * 会收起侧栏的入口。反过来也守：新增一个入口时，若它没收起侧栏，这里会红。
 */
public final class SidebarNavigationTest {

    private static final String SRC = "app/src/main/java/com/zhizhu/zhicode/compose/";

    private static final String SIDEBAR = SRC + "ui/Sidebar.kt";
    private static final String LAYOUTS = SRC + "ui/WorkspaceLayouts.kt";
    private static final String VIEW_MODEL = SRC + "state/WorkspaceViewModel.kt";

    /**
     * 侧栏里的每一行：{文案, `Sidebar` 的回调名, ViewModel 的入口名}。
     *
     * <p>回调名与入口名**不一定同构**（`onRuntime` → `openEnvironment`），
     * 所以三个都写出来，不要靠字符串拼接去猜。
     *
     * <p>「新会话」不在此列：它本来就自己设了 `sidebarOpen = false`
     * （收侧栏是它语义的一部分 —— 新会话要立刻看见空对话流）。
     */
    private static final String[][] ROWS = {
        {"ZhiCode 沙箱", "onSandbox", "openSandbox"},
        {"运行环境", "onRuntime", "openEnvironment"},
        {"设置", "onSettings", "openSettings"},
    };

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static String read(Path root, String path) throws Exception {
        return new String(Files.readAllBytes(root.resolve(path)), StandardCharsets.UTF_8);
    }

    /** 去掉注释：否则「代码删了、注释里还写着」会让断言静默通过。 */
    private static String stripComments(String text) {
        return text.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", " ");
    }

    private static boolean has(String source, String needle) {
        return source.replaceAll("\\s+", "").contains(needle.replaceAll("\\s+", ""));
    }

    /**
     * 取一个函数体的文本：从 [signature] 起，到下一个同级成员声明之前。
     *
     * <p>不能用固定长度切片 —— 本文件里函数挨得很近，切长了会把下一个函数
     * （它可能是 `openSettings`，里面就有 `sidebarOpen = false`）算进来，
     * 于是断言在一个完全无关的地方变绿。实测踩过这个坑。
     */
    private static String body(String source, String signature) {
        int start = source.indexOf(signature);
        if (start < 0) throw new AssertionError("找不到函数：" + signature);
        Matcher next = Pattern.compile("\\n    (?:private |internal |public )?(?:fun|val|var|companion) ")
            .matcher(source);
        next.region(start + signature.length(), source.length());
        int end = next.find() ? next.start() : source.length();
        return source.substring(start, end);
    }

    public static void main(String[] args) throws Exception {
        Path root = Paths.get(args.length == 0 ? "." : args[0]).toAbsolutePath().normalize();
        String sidebar = stripComments(read(root, SIDEBAR));
        String layouts = stripComments(read(root, LAYOUTS));
        String viewModel = stripComments(read(root, VIEW_MODEL));

        // ---- 1. 侧栏里的每一行都要接到一个入口 ----
        for (String[] row : ROWS) {
            // 判据是**回调名**出现在 WorkspaceLayouts 的接线里：
            // Sidebar 只声明 `onSandbox` 这类回调，真正接到哪个入口由调用方决定。
            boolean wired = has(layouts, row[1] + " = ")
                || has(layouts, "viewModel::" + row[2]);
            require(wired,
                "侧栏「" + row[0] + "」那一行没有接到 " + row[2] + "："
                    + "它点了不会有任何反应");
        }

        // ---- 2. 那些入口必须收起侧栏 ----------------------------------------
        //
        // 这是本测试的核心。侧栏是浮层，openXxx 只推自己的页，不收侧栏就等于
        // 「目标打开了但被盖住」—— 用户看到的是"点了没反应"。
        for (String[] row : ROWS) {
            String entry = body(viewModel, "fun " + row[2] + "(");
            require(entry.contains("hideSidebarForNavigation()") || entry.contains("sidebarOpen = false"),
                "侧栏「" + row[0] + "」对应的 " + row[2] + " 没有收起侧栏："
                    + "侧栏是画在内容上层的浮层，目标页会被它整个盖住，"
                    + "表现是「点了没反应」（要按一次返回才看到目标页）");
        }

        // ---- 3. 收口函数必须真的存在且只做一件事 ------------------------------
        //
        // 为什么要求"收口在一个函数里"而不是各写一行：四个入口各写一遍，
        // 加第五个时必然漏 —— 那正是它坏掉的原因。
        require(has(viewModel, "private fun hideSidebarForNavigation()"),
            "必须有 hideSidebarForNavigation 作为唯一收口点："
                + "每个入口各写一遍 sidebarOpen = false，新增入口时必然漏");
        String hide = body(viewModel, "private fun hideSidebarForNavigation(");
        require(hide.contains("sidebarOpen = false"),
            "hideSidebarForNavigation 必须真的把 sidebarOpen 置为 false");

        // ---- 4. 反向：不得留下"打开了页面却不收侧栏"的入口 ---------------------
        //
        // 这一条防的是**新增**入口时的漏写（第 2 条只覆盖今天已知的三行）。
        // 判据：侧栏里出现的 onXxx 回调名，对应的 ViewModel 入口都要收起侧栏。
        for (String[] offender : scanRows(sidebar, layouts, viewModel)) {
            throw new AssertionError(offender[1]);
        }

        // ---- 5. 「不换页」的行也要收起侧栏 -----------------------------------
        //
        // 第 4 条按 `onXxx → openXxx` 的命名约定去找 ViewModel 入口；找不到就 `continue`。
        // 「项目路径与会话」正是从那个 `continue` 里漏掉的：它的接线是往输入器塞一条
        // `/status` 命令，ViewModel 里没有 `openProjectPath`，于是它成了唯一一行
        // 点了侧栏还赖着不走的地方 —— 而它发出的 `/status` 结果正好被侧栏盖住。
        //
        // 所以补一条：**凡是找不到对应入口的行，它的接线里必须自己调 closeSidebar()**。
        // 侧栏是"去哪儿"的导航，点完就该让开，不管目标是页面还是一个动作。
        List<String> unwired = new ArrayList<>();
        for (String callback : sidebarCallbacks(sidebar)) {
            if (resolveEntry(viewModel, callback) != null) continue; // 第 4 条已覆盖
            String wiring = wiringFor(layouts, callback);
            if (wiring == null || !wiring.contains("closeSidebar()")) {
                unwired.add(callback);
            }
        }
        require(unwired.isEmpty(),
            "这些侧栏行没有对应的 ViewModel 入口（不是换页，而是别的动作），"
                + "但接线里也没有 closeSidebar()，所以点了侧栏不会收起："
                + String.join("、", unwired)
                + "。修法：在 WorkspaceLayouts 的接线里先调 viewModel.closeSidebar()");

        System.out.println("SidebarNavigationTest PASS");
    }

    /** 侧栏里所有 `SidebarRow(... onClick = onXxx)` 的回调名。 */
    private static List<String> sidebarCallbacks(String sidebar) {
        List<String> names = new ArrayList<>();
        Matcher rows = Pattern.compile("SidebarRow\\([^)]*onClick = (on[A-Za-z]+)").matcher(sidebar);
        while (rows.find()) names.add(rows.group(1));
        return names;
    }

    /**
     * 按 `onXxx` 找 ViewModel 里的入口函数名。
     *
     * <p>先试 `openXxx`（`onSettings` → `openSettings`），再试原样去掉 `on`
     * （`onNewSession` → `newSession`）。两个都试是必要的：侧栏的命名与入口
     * 并不是一套约定（`onRuntime` → `openEnvironment` 连词根都不同），
     * 只试一种会把它当成"没有入口的行"而走到第 5 条去。
     */
    private static String resolveEntry(String viewModel, String callback) {
        String suffix = callback.substring(2);
        // 去掉 `on` 之后首字母要还原成小写：`onNewSession` → `newSession`
        // （不是 `NewSession`，那是属性名的写法）。漏掉这一步会把
        // 「新会话」误判成"没有入口的行"，然后被第 5 条以一条**错误**的理由报红。
        String lower = suffix.isEmpty()
            ? suffix
            : Character.toLowerCase(suffix.charAt(0)) + suffix.substring(1);
        for (String candidate : new String[]{"open" + suffix, lower, suffix}) {
            if (viewModel.contains("fun " + candidate + "(")) return candidate;
        }
        return null;
    }

    /** 取 `onXxx = …` 这一段接线（到该实参的下一个 `,` 或行尾之前）。 */
    private static String wiringFor(String layouts, String callback) {
        int at = layouts.indexOf(callback + " = ");
        if (at < 0) return null;
        int end = layouts.indexOf("\n", at);
        // 接线可能是多行的 lambda（`onProjectPath = { … }`），往后多取一段再截到
        // 下一个同级实参：用 `,\n        on` 作为分界，够稳且不会串到下一行。
        String tail = layouts.substring(at, Math.min(layouts.length(), at + 400));
        Matcher next = Pattern.compile(",\\s*\\n\\s*on[A-Za-z]+ = ").matcher(tail);
        if (next.find()) end = at + next.start();
        return layouts.substring(at, end < 0 ? layouts.length() : end);
    }

    /**
     * 逐行检查：回调 → 入口 → 入口必须收起侧栏。找不到入口的行交给第 5 条。
     *
     * @return 每个违规项是 {回调名, 报错文案}；空表示全部合格。
     */
    private static List<String[]> scanRows(String sidebar, String layouts, String viewModel) {
        List<String[]> offenders = new ArrayList<>();
        for (String callback : sidebarCallbacks(sidebar)) {
            String entryName = resolveEntry(viewModel, callback);
            if (entryName == null) continue; // 见第 5 条
            String entry = body(viewModel, "fun " + entryName + "(");
            if (!entry.contains("hideSidebarForNavigation()") && !entry.contains("sidebarOpen = false")) {
                offenders.add(new String[]{callback, "这些侧栏入口打开了页面但没有收起侧栏"
                    + "（目标会被浮层盖住）：" + callback + " → " + entryName
                    + "。修法：在入口开头调 hideSidebarForNavigation()"});
            }
        }
        return offenders;
    }
}
