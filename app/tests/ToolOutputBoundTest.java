import java.nio.file.*;
import java.util.*;

/**
 * 超长工具输出 / diff 的守卫。
 *
 * <p>这里守的是两件**不会报错、只会让老设备发烫**的事，以及一件会直接让界面卡死的事：
 *
 * <ol>
 *   <li><b>单个 LazyColumn item 里不许渲染上千个节点。</b>工具卡展开的输出最长
 *       40 000 字符（{@code WorkspaceViewModel.LIVE_OUTPUT_LIMIT}），而 {@code DiffLines}
 *       是**每一行一个 {@code Text}**。1000 行的 diff 就是 1000 个组合 + 1000 个 layout 节点，
 *       而且它们全在同一个 item 里 —— 那个 item 比视口还高，懒加载的复用彻底失效。
 *       现在有 {@code MaxRenderedLines} 上限，并把全量留给「点按显示全部」。</li>
 *   <li><b>不许再对输出做 O(n) 的字符串手术。</b>原来 {@code compactToolSummary} 每次重组
 *       都 {@code activity.output.trim()}（复制整份文本）再 {@code clean.split('\n')}
 *       （建出所有行）；{@code ToolText.isFileDiff} 也一样。这些函数在工具行每次重组时
 *       都会被调用（滚动、状态变化、展开/收起都算），而折叠摘要与行数其实只要扫一遍下标。
 *       结果只有三个数：去空白后的边界、行数、以及单行时才要的那段文本。</li>
 *   <li><b>{@code items} 必须 remember。</b>{@code ZhiIconDropdownMenu} 内部按
 *       {@code remember(items, …)} 缓存整份 {@code DropdownEntry}；调用点若在参数位置上
 *       现写一个 {@code listOf(...)}，每次重组都是新实例、缓存永远命不中 —— 输入器随
 *       {@code state.composerText} 每敲一个字重组一次，等于按字符数付费。</li>
 * </ol>
 *
 * <p>{@code limitLines} 的**行为**由 JVM 单测守（{@code LimitLinesTest}：上限、边界、
 * 「收尾换行算不算一行」的口径、守恒）。本文件只管源码结构，两者互补。
 */
public final class ToolOutputBoundTest {

    /**
     * 工具输出的行数上限与渲染。
     *
     * ⚠️ 本轮它**搬过家**：原先住在 `ui/panes/ChangesPane.kt` —— 那个文件混了一个
     * 已经删掉的「变更」面板和这套还活着的工具。所以这里跟着换成新路径，
     * 而不是把断言删掉（它们守的东西一个字没变）。
     */
    private static final String TOOL_OUTPUT =
            "app/src/main/java/com/zhizhu/zhicode/compose/ui/chat/ToolOutputText.kt";
    private static final String CARDS = "app/src/main/java/com/zhizhu/zhicode/compose/ui/chat/MessageCards.kt";
    private static final String TOOL_TEXT = "app/src/main/java/com/zhizhu/zhicode/compose/engine/ToolText.kt";
    private static final String LIVE_OUTPUT = "app/src/main/java/com/zhizhu/zhicode/compose/model/LiveOutput.kt";
    private static final String COMPOSER = "app/src/main/java/com/zhizhu/zhicode/compose/ui/composer/Composer.kt";
    private static final String COMMON = "app/src/main/java/com/zhizhu/zhicode/compose/ui/Common.kt";

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static String read(String root, String relative) throws Exception {
        return new String(Files.readAllBytes(Paths.get(root, relative)),
                java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * 去注释。
     *
     * <p>⚠️ 必须做：本文件守的两处改动都在注释里写了「不要写成 `output.split('\n')`」
     * 之类的反面教材，不去注释的话断言会被自己的注释喂饱。字符串里的 {@code /*} 会触发
     * 块注释正则这一点与其它守卫同样处理。
     */
    private static String stripComments(String text) {
        String noBlock = text.replaceAll("(?s)/\\*.*?\\*/", " ");
        return noBlock.replaceAll("(?m)//[^\\n]*", " ");
    }

    /** 去掉所有空白：断言只关系代码结构，不该因为一次重新缩进就红。 */
    private static String squash(String text) {
        return text.replaceAll("\\s+", "");
    }

    /**
     * {@link #squash} 之后再去掉**尾随逗号**。
     *
     * <p>Kotlin 允许参数表最后一项带逗号（本工程到处都这么写），而它落在 {@code )} 前面时
     * 会让「参数完全一致」的字面断言变成假的失败 —— 加一个逗号就红，那不是我们守的东西。
     */
    private static String squashArgs(String text) {
        return squash(text).replace(",)", ")").replace(",}", "}");
    }

    /** 取出 {@code 签名 … 配对右花括号} 之间的函数体；找不到就返回空串（调用点会立刻报错）。 */
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

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int at = text.indexOf(needle);
        while (at >= 0) {
            count++;
            at = text.indexOf(needle, at + needle.length());
        }
        return count;
    }

    public static void main(String[] args) throws Exception {
        String root = args.length > 0 ? args[0] : ".";

        // ---- 1. 两处渲染长文本的入口都必须走同一个行数上限 -------------------
        String changes = stripComments(read(root, TOOL_OUTPUT));
        require(changes.contains("internal const val MaxRenderedLines = 300"),
                TOOL_OUTPUT + " 里找不到 `internal const val MaxRenderedLines = 300` —— "
                        + "超长输出的行数上限没了，一个 1000 行的 diff 会在**同一个 LazyColumn item** "
                        + "里生成 1000 个 Text，那个 item 比视口还高，懒加载复用彻底失效");

        // 上限必须真的被接到渲染路径上，不能只是个没人用的常量。
        String bounded = functionBody(changes, "private fun boundedTextLines(");
        require(!bounded.isEmpty(), TOOL_OUTPUT + " 里找不到 boundedTextLines —— 上限的执行者没了");
        require(squash(bounded).contains("limitLines(text,MaxRenderedLines)"),
                "boundedTextLines 必须把 MaxRenderedLines 交给 limitLines 执行："
                        + "常量摆在那儿而渲染不走它，等于没有上限");
        require(squash(bounded).contains("if(showAll)LimitedLines(text,0)elselimitLines(text,MaxRenderedLines)"),
                "boundedTextLines 必须保留「点按显示全部」这条出路（showAll）："
                        + "上限只该影响默认渲染，一个字节的数据都不能因为性能被藏没");

        String diffLines = functionBody(changes, "fun DiffLines(");
        require(!diffLines.isEmpty(), TOOL_OUTPUT + " 里找不到 DiffLines");
        require(diffLines.contains("boundedTextLines(diff)"),
                "DiffLines 必须经过 boundedTextLines()：上限、以及「点按显示全部」这条出路"
                        + "都在那里。直接把 diff 交给 split('\\n') 就等于没有上限。");
        require(!diffLines.contains("diff.split("),
                "DiffLines 里又出现了 diff.split( —— 逐行渲染没有行数上限，"
                        + "上千行的输出会全部变成 Text 节点");

        String outputLines = functionBody(changes, "fun OutputLines(");
        require(!outputLines.isEmpty(),
                TOOL_OUTPUT + " 里找不到 OutputLines —— 展开的工具输出必须有与 DiffLines 同一套上限的"
                        + "渲染入口（它就是原来参数位置上的裸 Text(activity.output)）");
        require(outputLines.contains("boundedTextLines("),
                "OutputLines 必须经过 boundedTextLines()（理由同 DiffLines）");

        // 上限函数本身不许做字符串手术 —— 它存在的意义就是**避免** split。
        String limit = functionBody(changes, "internal fun limitLines(");
        require(!limit.isEmpty(), TOOL_OUTPUT + " 里找不到 limitLines —— 截断规则被删了");
        require(!limit.contains(".split("),
                "limitLines 里出现了 split：它的全部意义就是「在截断的同一次下标扫描里"
                        + "顺手把总行数算出来」，用 split 反而把要省的那次分配做了两遍");

        // ---- 2. 工具卡不许再对输出做 O(n) 的字符串手术 -----------------------
        String cards = stripComments(read(root, CARDS));
        String toolRow = functionBody(cards, "private fun ToolRow(");
        require(!toolRow.isEmpty(), CARDS + " 里找不到 ToolRow");
        require(toolRow.contains("isFileDiff = remember("),
                "ToolRow 必须缓存 isFileDiff 的结果（remember）：它是 O(输出长度) 的扫描，"
                        + "而展开分支里要用两次、每次重组都跑。"
                        + "⚠️ key 不能是 activity 整体 —— 它带 elapsedMs，运行中每秒都变。");
        require(squash(toolRow).contains("remember(activity.toolName,activity.output){activity.isFileDiff()}"),
                "ToolRow 里的 isFileDiff 必须按 (toolName, output) 取 key 并现场计算："
                        + "用 activity 整体会让运行中每秒白算一次");
        require(squashArgs(toolRow).contains(
                        "remember(activity.kind,activity.output,activity.additions,activity.deletions,"
                                + "activity.failed,activity.exitCode){compactToolSummary(activity)}"),
                "ToolRow 必须缓存折叠摘要（remember(…){ compactToolSummary(activity) }）："
                        + "它要遍历整份输出，而折叠态是默认状态、每次重组都会走到");
        require(toolRow.contains("OutputLines(activity.output)"),
                "ToolRow 展开分支必须用 OutputLines(activity.output) 渲染："
                        + "整段输出当一个 Text 放进去，那个 item 会比视口还高");

        String summary = functionBody(cards, "private fun compactToolSummary(");
        require(!summary.isEmpty(), CARDS + " 里找不到 compactToolSummary");
        require(!summary.contains(".split("),
                "compactToolSummary 里又出现了 split：它每次重组都跑一遍，"
                        + "会为整份输出建出所有行的数组，而结果只有「行数」这一个数");
        require(!summary.contains(".trim()"),
                "compactToolSummary 里又出现了 trim()：它会**复制整份输出**"
                        + "（上限 40 000 字符），而这里只需要去空白后的两个下标");

        // 「哪一行才是有用的错误信息」现在在纯逻辑层（见 ToolInteractionTest 里
        // ErrorSummary 的那几条断言 + ErrorSummaryTest），所以这里只钉一点：
        // 界面不许自己再扫一遍。
        String errorLine = functionBody(cards, "private fun firstUsefulErrorLine(");
        require(errorLine.isEmpty(),
                CARDS + " 里不许再留着 firstUsefulErrorLine 的自有实现："
                        + "判据已挪到 ErrorSummary（纯逻辑、有单测）");
        require(squash(cards).contains("ErrorSummary.firstUsefulLine("),
                CARDS + " 的错误摘要必须转发到 ErrorSummary.firstUsefulLine");

        // ---- 2b. 运行中的实时输出也必须有上限 ------------------------------
        //
        // 运行标签那几行是**每次都重算**的（工具行随重组刷新），所以裁剪同样
        // 必须是"按下标取尾部"而不是先把整段切碎。判据在纯逻辑层，有单测。
        String liveOutput = stripComments(read(root, LIVE_OUTPUT));
        require(liveOutput.contains("fun preview(") && liveOutput.contains("fun tail("),
                LIVE_OUTPUT + " 必须提供 tail / preview："
                        + "运行中的行内输出按「尾部 N 字符」与「尾部 N 行」两种口径取");
        require(liveOutput.contains("DEFAULT_KEEP"),
                LIVE_OUTPUT + " 必须给缓冲一个上限（对应原版的 keep = 40000）："
                        + "一条 apt install 能刷出几十万字符");

        // ---- 3. isFileDiff 不许 split --------------------------------------
        String toolText = stripComments(read(root, TOOL_TEXT));
        String isFileDiff = functionBody(toolText, "fun isFileDiff(");
        require(!isFileDiff.isEmpty(), TOOL_TEXT + " 里找不到 isFileDiff");
        require(!isFileDiff.contains(".split("),
                "ToolText.isFileDiff 里又出现了 split：它要在一份最长 40 000 字符的输出里"
                        + "找 @@ / --- / +++ 三行，而这三行**通常在开头** —— "
                        + "split 会先把整份输出切成行数组才开始找，白建一遍就为了提前 return");

        // ---- 4. 下拉菜单的 items 必须 remember ------------------------------
        String composer = stripComments(read(root, COMPOSER));
        require(!composer.contains("items = listOf("),
                "Composer.kt 里又出现了写在参数位置上的 items = listOf(…)："
                        + "每次重组都是新的 List 实例，ZhiIconDropdownMenu 里那份 "
                        + "remember(items, …) 永远命不中，等于没做。输入器随 composerText "
                        + "每敲一个字重组一次。");
        require(!composer.contains("items = PermissionMode.entries.map"),
                "权限 chip 的 items 必须 remember（理由同上）；"
                        + "key 取 (state.permissionMode, onPermissionSelected)");
        require(!composer.contains("items = EffortLevel.entries.map"),
                "推理 chip 的 items 必须 remember（理由同上）");
        int rememberedItems = countOccurrences(composer, "items = remember(");
        require(rememberedItems >= 3,
                "Composer.kt 里必须有三处 items = remember(…)：`+` 菜单、权限 chip、推理 chip，"
                        + "现在只有 " + rememberedItems + " 处");

        String common = stripComments(read(root, COMMON));
        String menu = functionBody(common, "fun ZhiIconDropdownMenu(");
        require(!menu.isEmpty(), COMMON + " 里找不到 ZhiIconDropdownMenu");
        require(squash(menu).contains("valentry=remember(items,scheme.primary)"),
                "ZhiIconDropdownMenu 必须按 (items, scheme.primary) 缓存整份 DropdownEntry："
                        + "它含每个条目的 icon 可组合 lambda，而输入器每敲一个字就重组一次。"
                        + "scheme.primary 是 iconSlot 里真正读到的主题色，漏了它会让图标留在旧配色上。");

        // ---- 5. 本测试自身必须被 canonical suite 执行 ------------------------
        String script = read(root, "test-source-no-build.sh");
        require(script.contains("ToolOutputBoundTest \"$PROJECT_ROOT\""),
                "canonical source suite 必须执行本守卫");

        System.out.println("ToolOutputBoundTest PASS"
                + "（长输出有行数上限 · 工具行不再做 O(n) 字符串手术 · 下拉 items 已缓存）");
    }
}
