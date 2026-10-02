import java.nio.file.*;
import java.util.*;

/**
 * A3 的守卫：主线程上不许做磁盘 IO、热路径组件不许收整份 UiState。
 *
 * <p>这里守的两类问题**都不会报错**：
 *
 * <ol>
 *   <li><b>文件读写跑在主线程上。</b>技能文件是手写的参考文档，几百 KB 很常见；
 *       Android 8 那代设备的闪存更慢。压在点击那一帧上就是一次肉眼可见的卡顿 ——
 *       不崩、不抛 ANR（几百毫秒还够不到 ANR 门槛），所以只会被当成"这应用有点卡"。
 *       本工程的既有约定是 `viewModelScope.launch(Dispatchers.IO)`（`initSessionState`
 *       等十几处都是这么写的），所以这里断言的是**顺序**：
 *       `launch(Dispatchers.IO)` 必须出现在 SkillStore 调用**之前**。</li>
 *   <li><b>顶栏 / 侧栏收整份 `WorkspaceUiState`。</b>它们各自只读四项到六项，
 *       而整份状态里带着**对话流与全部工具输出**。流式回复期间 `_state` 每 32ms
 *       换一次新实例（`ZhiEngineController.DELTA_MERGE_MS`），于是这两个组件
 *       每秒要为一百多次与它们无关的正文增量做判等 + 重组。</li>
 * </ol>
 *
 * <p>⚠️ 本文件**没有**断言 `WorkspaceUiState` 本身带 `@Immutable`，这是刻意的，
 * 别把它补上：那个类有五十多个字段、其中十几个是 `List`。Kotlin 的 `List` 只是
 * 只读视图、背后可能是 `ArrayList`，标 `@Immutable` 等于向编译器保证一件在类型上
 * 根本核实不了的事 —— 一旦哪里原地 `add` 一下，界面会**静默停更**（最难查的一类 bug），
 * 而收益只是省掉一次判等。所以能量化的地方（顶栏/侧栏收窄）就收窄，能量不到的地方
 * 不假装。（`SidebarState` 也**故意**不带 `@Immutable`，理由同样是它含 `List`；
 * 而 `TopBarState` 可以带，因为它全是 String/Int/Boolean/enum —— 下面有断言钉住这一点，
 * 防止以后有人往里面塞一个 List 却忘了摘掉注解。）
 */
public final class MainThreadIoBoundTest {

    private static final String VM = "app/src/main/java/com/zhizhu/zhicode/compose/state/WorkspaceViewModel.kt";
    private static final String TOPBAR = "app/src/main/java/com/zhizhu/zhicode/compose/ui/TopBar.kt";
    private static final String SIDEBAR = "app/src/main/java/com/zhizhu/zhicode/compose/ui/Sidebar.kt";
    private static final String TERMINAL = "app/src/main/java/com/zhizhu/zhicode/compose/ui/panes/TerminalPane.kt";

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

    private static String squash(String text) {
        return text.replaceAll("\\s+", "");
    }

    private static String functionBody(String code, String signature) {
        int at = code.indexOf(signature);
        if (at < 0) return "";
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
     * 断言 [body] 里的磁盘调用确实在 `launch(Dispatchers.IO)` **之后**。
     *
     * <p>用下标先后而不是"出现了就行"：只判"有没有 Dispatchers.IO"的话，
     * 把 IO 块挪到落盘之后（或者干脆只在旁边留一个空 IO 块）仍然会通过。
     */
    private static void requireIoBefore(String body, String what, String diskCall, String where) {
        int io = body.indexOf("Dispatchers.IO");
        int disk = body.indexOf(diskCall);
        require(io >= 0,
                where + " 的 " + what + " 必须在 IO 线程上落盘：找不到 `Dispatchers.IO`。"
                        + "主线程上写几百 KB 的技能文件就是一次肉眼可见的卡顿"
                        + "（本工程既有约定见 WorkspaceViewModel.initSessionState）。");
        require(disk >= 0,
                where + " 的 " + what + " 里找不到 " + diskCall + " —— 断言本身过期了，请更新守卫");
        require(io < disk,
                where + " 的 " + what + " 把 " + diskCall + " 放在了 `launch(Dispatchers.IO)` **之前**："
                        + "那仍然是在主线程上做磁盘 IO（读者要注意的是**执行顺序**，"
                        + "不是文件里出现过 `Dispatchers.IO` 这几个字）。");
    }

    /** 取出 `data class X(` 到配对右括号之间的正文（用来检查字段类型）。 */
    private static String dataClassBody(String code, String name) {
        int at = code.indexOf("data class " + name + "(");
        if (at < 0) return "";
        int depth = 0;
        for (int i = at; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) return code.substring(at, i + 1);
            }
        }
        return code.substring(at);
    }

    public static void main(String[] args) throws Exception {
        String root = args.length > 0 ? args[0] : ".";

        // ---- 1. 四处技能文件的落盘必须在 IO 线程上 --------------------------
        String vm = stripComments(read(root, VM));

        String saveFile = functionBody(vm, "fun saveSkillFile()");
        require(!saveFile.isEmpty(), VM + " 里找不到 saveSkillFile");
        requireIoBefore(saveFile, "saveSkillFile", "SkillStore.writeFile(", VM);
        // 存在性检查与写入必须在**同一个** IO 块里，否则会多出一个「查过了但还没写」
        // 的窗口 —— 双击能建出两份同名文件。
        requireIoBefore(saveFile, "saveSkillFile 的存在性检查", "SkillStore.fileExists(", VM);

        String deleteFile = functionBody(vm, "fun deleteSkillFile(");
        require(!deleteFile.isEmpty(), VM + " 里找不到 deleteSkillFile");
        requireIoBefore(deleteFile, "deleteSkillFile", "SkillStore.deleteFile(", VM);
        // 真正花钱的是它后面那次重列文件树（walk 目录 + 逐文件取大小）。
        requireIoBefore(deleteFile, "deleteSkillFile 的重列目录", "SkillStore.listTree(", VM);

        String edit = functionBody(vm, "fun editSkill(");
        require(!edit.isEmpty(), VM + " 里找不到 editSkill");
        requireIoBefore(edit, "editSkill", "SkillStore.readFile(", VM);

        String save = functionBody(vm, "fun saveSkill()");
        require(!save.isEmpty(), VM + " 里找不到 saveSkill");
        requireIoBefore(save, "saveSkill", "SkillStore.writeFile(", VM);

        // ---- 2. 顶栏 / 侧栏收窄，不再收整份 UiState --------------------------
        String topBar = stripComments(read(root, TOPBAR));
        String topBarFn = functionBody(topBar, "fun ZhiTopBar(");
        require(!topBarFn.isEmpty(), TOPBAR + " 里找不到 ZhiTopBar");
        require(topBarFn.contains("state: TopBarState"),
                "ZhiTopBar 必须收 TopBarState 而不是整份 WorkspaceUiState："
                        + "它只读六项，而整份状态带着对话流与全部工具输出 —— "
                        + "流式期间每 32ms 换一次实例，顶栏就要为一次无关的正文增量重组一遍。");
        require(!topBarFn.contains("state: WorkspaceUiState"),
                "ZhiTopBar 又收整份 WorkspaceUiState 了（理由同上）");

        String topBarState = dataClassBody(topBar, "TopBarState");
        require(!topBarState.isEmpty(), TOPBAR + " 里找不到 TopBarState");
        // `@Immutable` 是给编译器的**保证**：里面不能有 List、不能有可变对象。
        // 这条断言就是让那个保证一直成立 —— 塞一个 List 进去而注解还在，
        // 编译器会据此跳过该组件的重组，界面从此静默不更新。
        require(squash(topBar).contains("@ImmutabledataclassTopBarState("),
                "TopBarState 必须带 @Immutable（它全是 String/Int/Boolean/enum，"
                        + "这个承诺是可核对的；去掉注解等于白做这次收窄）");
        require(!topBarState.contains("List<"),
                "TopBarState 里出现了 List：那就不能再带 @Immutable 了 —— "
                        + "Kotlin 的 List 只是只读视图、背后可能是 ArrayList，"
                        + "标成不可变会让编译器的跳过判断出错，界面会静默停更。"
                        + "确实需要列表的话，请去掉注解并在这里说明理由。");

        String sidebar = stripComments(read(root, SIDEBAR));
        String sidebarFn = functionBody(sidebar, "fun ZhiSidebar(");
        require(!sidebarFn.isEmpty(), SIDEBAR + " 里找不到 ZhiSidebar");
        require(sidebarFn.contains("state: SidebarState"),
                "ZhiSidebar 必须收 SidebarState 而不是整份 WorkspaceUiState（理由同 ZhiTopBar）");
        require(!sidebarFn.contains("state: WorkspaceUiState"),
                "ZhiSidebar 又收整份 WorkspaceUiState 了（理由同 ZhiTopBar）");
        require(!squash(sidebar).contains("@ImmutabledataclassSidebarState("),
                "SidebarState **不能**带 @Immutable：它含 List<SessionSummary>，"
                        + "而 Kotlin 的 List 背后可能是 ArrayList，这个承诺在类型上核实不了。"
                        + "标上去之后一次原地 add 就会让侧栏静默不更新。");

        // ---- 3. 终端面板的自动贴底不许用挂起动画 -----------------------------
        String terminal = stripComments(read(root, TERMINAL));
        require(!terminal.contains("animateScrollToItem"),
                "TerminalPane 又用回 animateScrollToItem 了：它是**挂起**的，"
                        + "而 effect 的 key 是 lines.size —— 终端每来一行就变一次，"
                        + "effect 被不停取消重启，滚动常常在真正滚到底之前就被取消，"
                        + "表现为「滚一下停一下」。用 requestScrollToItem（非挂起，下次测量生效）。"
                        + "同一坑的完整推导见 ui/chat/ChatList.kt。");
        require(terminal.contains("requestScrollToItem"),
                "TerminalPane 必须用 requestScrollToItem 做自动贴底");

        // ---- 4. 本测试自身必须被 canonical suite 执行 ------------------------
        String script = read(root, "test-source-no-build.sh");
        require(script.contains("MainThreadIoBoundTest \"$PROJECT_ROOT\""),
                "canonical source suite 必须执行本守卫");

        System.out.println("MainThreadIoBoundTest PASS"
                + "（技能文件读写都在 IO 线程 · 顶栏/侧栏已收窄 · 终端贴底不再用挂起动画）");
    }
}
