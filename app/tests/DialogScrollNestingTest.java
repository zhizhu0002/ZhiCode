import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/**
 * 弹窗内「滚动套滚动」的守卫。
 *
 * <p><b>为什么需要这条。</b>2026-09-27 用户报告「添加 MCP 服务器崩溃」。复现后拿到的
 * 异常是：
 *
 * <pre>
 * java.lang.IllegalStateException: Vertically scrollable component was measured with
 * an infinity maximum height constraints, which is disallowed.
 *   at androidx.compose.foundation.CheckScrollableContainerConstraintsKt.checkScrollable…
 *   at androidx.compose.foundation.ScrollNode.measure-3p2s80s(Scroll.kt:447)
 *   at top.yukonga.miuix.kmp.utils.MiuixOverscrollEffectNode.measure-3p2s80s(OverscrollFactory.kt:401)
 *   at androidx.compose.foundation.ScrollNode.measure-3p2s80s(Scroll.kt:457)   ← 第二层
 * </pre>
 *
 * 栈里那<b>两个</b> {@code ScrollNode} 就是病根：{@link #SHELL} 已经把 body 放进
 * {@code verticalScroll}，而 MCP 表单 / 技能编辑 / 记忆编辑又在 body 里各加了一层。
 * 内层因此拿到「高度无限」的约束，Compose 直接抛异常 —— 发生在 main 线程的首帧测量里，
 * 所以表现不是报错而是<b>应用闪退</b>。
 *
 * <p><b>为什么它必须由文本断言来守。</b>这个缺陷<b>编译完全通过</b>，静态检查也看不出来：
 * 两层 {@code verticalScroll} 各自都是合法写法，只有真正测量到那一步才会炸，
 * 而且只在打开那个弹窗时才炸。单元测试碰不到 Compose 测量，设备上跑一次才知道。
 * 属于本仓库定义的那一类：「写错了也不会编译失败，只会在真机上表现为闪退」。
 *
 * <p>注意 {@link #stripComments}：修好后代码里就留着「这里不能再用 verticalScroll」
 * 的注释，而注释里正含这个词。不去注释的话，这条守卫会因为一句说明文字而自己变红。
 */
public final class DialogScrollNestingTest {

    private static final String DIALOGS =
            "app/src/main/java/com/zhizhu/zhicode/compose/ui/dialogs/";
    private static final String SHELL = DIALOGS + "DialogShell.kt";

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static String read(String root, String relative) throws Exception {
        return new String(Files.readAllBytes(Paths.get(root, relative)),
                java.nio.charset.StandardCharsets.UTF_8);
    }

    /** 去掉注释：否则「代码删了、注释里还写着」会让断言静默通过（或反过来假红）。 */
    private static String stripComments(String text) {
        String noBlock = text.replaceAll("(?s)/\\*.*?\\*/", " ");
        return noBlock.replaceAll("(?m)//[^\\n]*", " ");
    }

    private static List<String> sources(String root) throws Exception {
        try (Stream<Path> walk = Files.walk(Paths.get(root, DIALOGS))) {
            return walk.filter(p -> p.toString().endsWith(".kt"))
                    .map(p -> Paths.get(root).relativize(p).toString().replace('\\', '/'))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    private static int count(String text, String needle) {
        int found = 0;
        for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + 1)) found++;
        return found;
    }

    /**
     * 取从 {@code open}（一个 {@code (}）到配对右括号之间的实参文本。
     * 找不到配对时返回空串 —— 调用方按「读不到就报错」处理。
     */
    private static String callArgs(String code, int open) {
        int depth = 0;
        for (int i = open; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) return code.substring(open, i + 1);
            }
        }
        return "";
    }

    public static void main(String[] args) throws Exception {
        String root = args.length > 0 ? args[0] : ".";
        List<String> files = sources(root);
        require(!files.isEmpty(), "在 " + DIALOGS + " 下一个 .kt 都没找到，路径写错了？");

        // ---- 1. 外壳必须自己提供且只提供一次滚动出口 ----
        //
        // 这条同时守住两个方向：删掉外壳的 verticalScroll（长表单没有滚动出口、
        // 按钮被挤出屏幕），以及把外壳的滚动改成嵌套写法。
        String shell = stripComments(read(root, SHELL));
        require(count(shell, "verticalScroll(rememberScrollState())") == 1,
                "DialogShell 应当恰好有一处 verticalScroll(rememberScrollState()) —— 它是所有弹窗"
                        + " body 的唯一滚动出口。实际 "
                        + count(shell, "verticalScroll(rememberScrollState())") + " 处。");

        // ---- 2. 除外壳外，弹窗里不得再出现任何 verticalScroll ----
        //
        // 这就是崩溃本体。DialogShell 的 body 已经处在竖向滚动容器内，
        // 任何一层额外的竖向滚动都会拿到无限高度约束。
        List<String> offenders = new ArrayList<>();
        for (String file : files) {
            if (file.equals(SHELL)) continue;
            String code = stripComments(read(root, file));
            if (code.contains("verticalScroll")) offenders.add(file);
        }
        require(offenders.isEmpty(),
                "以下弹窗在 DialogShell 的滚动区里又套了一层 verticalScroll：\n"
                        + "        " + String.join("\n        ", offenders) + "\n"
                        + "      DialogShell 已经把 body 放进竖向滚动容器，嵌套会让内层拿到无限大"
                        + "      高度约束，Compose 抛 IllegalStateException，main 线程首帧即崩、"
                        + "      进程被杀（表现是点开该弹窗直接闪退）。\n"
                        + "      修法：删掉内层那一层，表单变长由 DialogShell 的外层滚动负责。");

        // ---- 3. 弹窗里的 LazyColumn 必须自带高度上限 ----
        //
        // 同一个坑的另一半：竖直方向的 LazyColumn 若不给上限，也会得到无限高度约束。
        // 现有四个都用 heightIn(max = …) 限住了，这条防的是「新加一个忘了限」。
        List<String> unbounded = new ArrayList<>();
        for (String file : files) {
            String code = stripComments(read(root, file));
            for (int at = code.indexOf("LazyColumn("); at >= 0; at = code.indexOf("LazyColumn(", at + 1)) {
                String call = callArgs(code, at + "LazyColumn".length());
                if (call.isEmpty() || !call.contains("heightIn")) {
                    unbounded.add(file + "（第 " + lineOf(code, at) + " 行）");
                }
            }
        }
        require(unbounded.isEmpty(),
                "以下 LazyColumn 没有 heightIn 上限：" + unbounded
                        + "\n      弹窗正处在竖向滚动容器里，无限高的 LazyColumn 会触发同一条"
                        + " Infinity maximum height constraints 崩溃。");

        System.out.println("DialogScrollNestingTest: 通过（弹窗 " + files.size()
                + " 个文件，外壳滚动 1 处，无嵌套滚动，" + "LazyColumn 均有高度上限）");
    }

    private static int lineOf(String text, int offset) {
        int line = 1;
        for (int i = 0; i < offset; i++) if (text.charAt(i) == '\n') line++;
        return line;
    }

    private DialogScrollNestingTest() {
    }
}
