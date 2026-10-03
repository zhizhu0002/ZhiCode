import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
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
 *
 * <h2>本文件修过的两个自身缺陷（都是实测出来的）</h2>
 *
 * <ol>
 *   <li><b>取回调的正则跨不过右括号。</b>原来是
 *       {@code SidebarRow\([^)]*onClick = (on[A-Za-z]+)}，而「运行环境就绪」那一行的
 *       {@code tint} 里有一个 {@code ZhiColors.green()} —— 于是那一行从来没被
 *       第 4/5 节覆盖。实测：把那行的 {@code onClick} 删掉，本节照样 PASS。
 *       现在改成括号配对扫描。</li>
 *   <li><b>靠名字猜入口，猜不到就误报。</b>原来是
 *       {@code onXxx → openXxx / xxx / Xxx} 三个候选名，猜不到就退化成
 *       "要求接线里直接写 {@code closeSidebar()}"。而 {@code onRuntime} 的真实接线是
 *       {@code viewModel::openEnvironment}（连词根都不同），于是它会被判成"没入口"
 *       —— 可它收得好好的。现在不猜名字：直接读**接线**里被调用的
 *       {@code viewModel.xxx(} / {@code viewModel::xxx}，再查那个函数体。</li>
 * </ol>
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

        // 只取 **`ZhiSidebar(...)` 那一次调用** 的实参。
        //
        // 不能在整个 WorkspaceLayouts 里找 `onSettings = `：宽屏顶栏那里也有一个，
        // 而且它在文件里**更靠前**，于是下面会一直查到顶栏那一处去
        // （两处恰好都接 openSettings，所以以前是"碰巧对"）。
        String sidebarArgs = sidebarWiring(layouts);

        // 侧栏签名里声明的回调。下面的第 4/5 节都靠它做**双向**核对。
        Set<String> declared = declaredCallbacks(sidebar);

        // ---- 1. 侧栏里的每一行都要接到一个入口 ----
        for (String[] row : ROWS) {
            // 判据是**回调名**出现在 ZhiSidebar 的接线里：
            // Sidebar 只声明 `onSandbox` 这类回调，真正接到哪个入口由调用方决定。
            require(has(sidebarArgs, row[1] + " = "),
                "侧栏「" + row[0] + "」那一行没有接线（ZhiSidebar 的实参里找不到 "
                    + row[1] + " = ）：它点了不会有任何反应");
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

        // ---- 4. 反向：侧栏里每一个回调最终都要落在一个会收起侧栏的地方 ---------
        //
        // 这一条防的是**新增**入口时的漏写（第 2 条只覆盖今天写死的那三行）。
        //
        // 判据是"接线里被调用的每个 ViewModel 入口，其函数体都收起侧栏"；
        // 不换页的行则要求接线里自己调了 closeSidebar()。见文件头「修过的两个自身缺陷」。
        List<String> unwired = new ArrayList<>();
        for (String callback : sidebarCallbacks(sidebar)) {
            require(declared.contains(callback),
                callback + " 不是 ZhiSidebar 声明的回调："
                    + "行上写的名字与签名里的对不上（改动时最容易漏的就是这一步，"
                    + "Kotlin 会编译不过，但把名字写到一个**没接线**的本地变量上就不会）");
            String wiring = wiringFor(sidebarArgs, callback);
            if (wiring == null) {
                unwired.add(callback + "（ZhiSidebar 的实参里找不到它的接线）");
                continue;
            }
            if (wiring.contains("closeSidebar()")) continue;
            List<String> entries = viewModelEntries(wiring);
            if (entries.isEmpty()) {
                unwired.add(callback + "（接线里既没调 ViewModel 入口、也没调 closeSidebar()）");
                continue;
            }
            for (String entry : entries) {
                String signature = "fun " + entry + "(";
                if (!viewModel.contains(signature)) {
                    unwired.add(callback + " → " + entry + "（ViewModel 里找不到这个函数）");
                    continue;
                }
                String entryBody = body(viewModel, signature);
                if (!entryBody.contains("hideSidebarForNavigation()")
                        && !entryBody.contains("sidebarOpen = false")) {
                    unwired.add(callback + " → " + entry);
                }
            }
        }
        require(unwired.isEmpty(),
            "这些侧栏行点下去侧栏不会收起（它是画在内容上层的浮层，"
                + "目标页会被整个盖住，表现是「点了没反应」）："
                + String.join("、", unwired)
                + "。修法：在对应的 ViewModel 入口开头调 hideSidebarForNavigation()；"
                + "不换页的行则在 ZhiSidebar 的接线里先调 closeSidebar()");

        // ---- 5. 反过来：签名里声明的回调都必须真的挂在一行上 -------------------
        //
        // ⚠️ 第 4 节有一处方向性的漏洞，实测抓到的：它遍历的是"侧栏里**找到的**回调"，
        // 所以把某一行的 `onClick = onRuntime` **删掉/注释掉**时，那个回调就不在
        // 遍历集合里了 —— 本节整体 PASS，而运行环境那一行已经点不动。
        //
        // 所以补这一条：`fun ZhiSidebar(` 签名里声明的每个 `onXxx`，都必须出现在
        // `SidebarRow(` 或 `SessionRow(` 的实参里。判据完全**推导**出来，不写死名字，
        // 于是新增一个回调参数却忘了挂到行上时也会红。
        Set<String> used = usedCallbacks(sidebar);
        Set<String> dangling = new TreeSet<>(declared);
        dangling.removeAll(used);
        require(dangling.isEmpty(),
            "ZhiSidebar 声明了这些回调，但没有任何一行在用它们："
                + String.join("、", dangling)
                + "。要么把行接回去，要么把参数删掉 —— 留着会让人以为那行还在。");

        // ---- 6. 删除会话只能有一个入口：长按菜单 -----------------------------
        //
        // 侧栏的会话行原来是「长按弹菜单（含删除会话）+ 行尾常驻一个 ✕」。同一个
        // 破坏性动作两条路径，其中一条还常年摆在最容易误触的位置（行尾、40dp 触摸区
        // 配 14dp 字形），而长按菜单里本来就有它（`showSessionActions` 的选项表）。
        //
        // 这条守的是"不许再冒出第二个删除入口"：再出现一个行内按钮 / 滑动删除，
        // 就应该先想清楚为什么长按菜单不够用，而不是默默地再加一个。
        require(!has(sidebar, "ZhiIcons.close"),
            "侧栏会话行不许再挂行内的 ✕ 删除键：删除已经在长按菜单里（「删除会话」），"
                + "两个入口并存只会让破坏性动作更容易误触");
        require(viewModel.contains("\"删除会话\""),
            "长按菜单里的「删除会话」不见了：那现在是删除会话的唯一入口");

        // ---- 7. 重命名必须挂在长按菜单里，且真的写 titleOverride --------------
        //
        // 「重命名」是长按菜单的第一个选项（`showSessionActions` 的选项表），
        // 提交后走 `onSubmitFreeForm` 的 SESSION_RENAME 分支，最终调用
        // `SessionReader.updateMetadata(file, note, title)` —— 注意第三个实参必须
        // 是**非空的标题**（空串在 SessionStore 语义里是"清除标题"）。备注路径
        // (`saveSessionNote`) 传的则是空串 + 备注正文，两者不能写混。
        require(viewModel.contains("\"重命名\" -> editSessionTitle(target)"),
            "长按菜单里的「重命名」没有接线（SESSION_ACTION 分派里缺 \"重命名\" 分支）");
        require(has(viewModel, "ChoiceIntent.SESSION_RENAME"),
            "ChoiceIntent 里没有 SESSION_RENAME：重命名和备注共用一个 intent 会把标题写成备注");
        String titleBody = body(viewModel, "private fun saveSessionTitle(");
        require(has(titleBody, "SessionReader.updateMetadata(File(session.id), session.note, title)"),
            "saveSessionTitle 必须把新标题作为 titleOverride（第三实参）写进去，且备注原样带回");

        System.out.println("SidebarNavigationTest PASS");
    }

    /**
     * `fun ZhiSidebar(` 签名里声明的 `onXxx` 参数名。
     *
     * <p>取配对括号之间的实参文本，再挑出 `onXxx:` 形态的参数名。
     * `anchoredMenu: @Composable (String, DpOffset?) -> Unit` 里的括号要靠配对扫描跳过，
     * 否则会在那里截断、漏掉后面那三个导航回调。
     */
    private static Set<String> declaredCallbacks(String sidebar) {
        String marker = "fun ZhiSidebar(";
        int at = sidebar.indexOf(marker);
        if (at < 0) throw new AssertionError("Sidebar.kt 里找不到 fun ZhiSidebar(");
        String params = balancedArgs(sidebar, at + marker.length() - 1);
        Set<String> names = new TreeSet<>();
        Matcher m = Pattern.compile("\\bon([A-Z][A-Za-z]*)\\s*:").matcher(params);
        while (m.find()) names.add("on" + m.group(1));
        return names;
    }

    /**
     * 侧栏里**所有被用到的** `onXxx`：`SidebarRow(` / `SessionRow(` 实参里出现的
     * `onXxx` 标识符。
     *
     * <p>要连 `onOpen = { onOpenSession(session) }` 这种**包在 lambda 里**的也算上，
     * 所以这里挑的是"实参里出现的 `onXxx` 标识符"，而不是"`onClick = onXxx` 这个形状"。
     * 代价是会连带匹配到被调函数的参数名（`onClick` / `onOpen` / `onActions`），
     * 但第 5 节只问 `declared ⊆ used`，多出来的不参与判断。
     */
    private static Set<String> usedCallbacks(String sidebar) {
        Set<String> used = new TreeSet<>();
        for (String callee : new String[]{"SidebarRow(", "SessionRow("}) {
            Matcher call = Pattern.compile(Pattern.quote(callee)).matcher(sidebar);
            while (call.find()) {
                String args = balancedArgs(sidebar, call.end() - 1);
                Matcher m = Pattern.compile("\\bon[A-Z][A-Za-z]*").matcher(args);
                while (m.find()) used.add(m.group());
            }
        }
        return used;
    }

    /**
     * `ZhiSidebar(...)` 那一次调用的实参文本。
     *
     * <p>为什么要单独取：`onSettings = ` 在整个 WorkspaceLayouts 里出现两次
     * （宽屏顶栏一次、侧栏一次），而顶栏那次在文件里**更靠前** ——
     * 在全文里搜就会一直查到顶栏那一处去。
     */
    private static String sidebarWiring(String layouts) {
        int at = layouts.indexOf("ZhiSidebar(");
        if (at < 0) throw new AssertionError("WorkspaceLayouts 里找不到 ZhiSidebar( 的调用");
        return balancedArgs(layouts, at + "ZhiSidebar".length());
    }

    /**
     * 从 `(` 的位置起取出配对括号之间的文本。
     *
     * <p>跳过字符串字面量：标签里出现 `(` `)` 时（`"运行环境就绪"` 这类中文标签目前没有，
     * 但 `"· ${n} 条"` 之类的模板随时可能加）不能把括号数错。
     * 不配对时返回空串 —— 那时下面的断言会以「找不到接线」报红，而不是抛越界。
     */
    private static String balancedArgs(String text, int openAt) {
        int depth = 0;
        for (int i = openAt; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                i++;
                while (i < text.length() && text.charAt(i) != '"') {
                    i += text.charAt(i) == '\\' ? 2 : 1;
                }
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) return text.substring(openAt + 1, i);
            }
        }
        return "";
    }

    /**
     * 侧栏里所有 `SidebarRow(... onClick = onXxx)` 的回调名。
     *
     * <p>⚠️ 原来是正则 `SidebarRow\([^)]*onClick = (on[A-Za-z]+)`。`[^)]*` 跨不过右括号，
     * 而「运行环境就绪」那一行的 `tint` 里有一个 `ZhiColors.green()` —— 于是那一行的
     * 回调**从来没被第 4 节覆盖**（实测：把那行的 onClick 删掉，本节照样 PASS）。
     * 现在先做括号配对取整段实参，再在里面找 `onClick = onXxx`。
     *
     * <p>`SidebarRow(` 也会命中它自己的**函数定义**，那里写的是 `onClick: () -> Unit`，
     * 不是 `onClick = on…`，所以不会贡献名字。
     */
    private static List<String> sidebarCallbacks(String sidebar) {
        List<String> names = new ArrayList<>();
        Matcher call = Pattern.compile("SidebarRow\\s*\\(").matcher(sidebar);
        while (call.find()) {
            String args = balancedArgs(sidebar, call.end() - 1);
            Matcher onClick = Pattern.compile("onClick\\s*=\\s*(on[A-Za-z]+)").matcher(args);
            while (onClick.find()) names.add(onClick.group(1));
        }
        return names;
    }

    /**
     * 一段接线里被调用的 ViewModel 入口名。
     *
     * <p>两种写法都要认：`viewModel.openSandbox()`（lambda 里）与
     * `viewModel::openEnvironment`（函数引用）—— 侧栏这四个回调两种都有，
     * 只认一种会把另一种当成"没有入口"，然后以一条**错误**的理由报红。
     */
    private static List<String> viewModelEntries(String wiring) {
        List<String> names = new ArrayList<>();
        Matcher m = Pattern.compile("viewModel\\s*(?:\\.|::)\\s*([A-Za-z]+)").matcher(wiring);
        while (m.find()) names.add(m.group(1));
        return names;
    }

    /** 取 `onXxx = …` 这一段接线（到该实参的下一个 `,` 或行尾之前）。 */
    private static String wiringFor(String sidebarArgs, String callback) {
        int at = sidebarArgs.indexOf(callback + " = ");
        if (at < 0) return null;
        int end = sidebarArgs.indexOf("\n", at);
        // 接线可能是多行的 lambda（`onXxx = { … }`），往后多取一段再截到
        // 下一个同级实参：用 `,\n        on` 作为分界，够稳且不会串到下一行。
        String tail = sidebarArgs.substring(at, Math.min(sidebarArgs.length(), at + 400));
        Matcher next = Pattern.compile(",\\s*\\n\\s*on[A-Za-z]+ = ").matcher(tail);
        if (next.find()) end = at + next.start();
        return sidebarArgs.substring(at, end < 0 ? sidebarArgs.length() : end);
    }
}
