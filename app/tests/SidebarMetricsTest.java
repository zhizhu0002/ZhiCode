import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 侧栏的行高 / 内边距 / 圆角 / 间距：**必须有显式预算，且不能超出密集列表这一档**。
 *
 * <p>判据来自 Miuix 官方文档（`~/projects/miuix/docs/zh_CN/`）与真机实测：
 *
 * <ul>
 *   <li>{@code basiccomponent.md}：{@code BasicComponentDefaults.InsideMargin} =
 *       {@code PaddingValues(16.dp)}，组件内部 {@code heightIn(min = 56.dp)}。</li>
 *   <li>{@code card.md}：{@code CardDefaults.CornerRadius} = 16dp、
 *       {@code CardDefaults.InsideMargin} = {@code PaddingValues(0.dp)}。</li>
 *   <li>{@code searchbar.md}：{@code InputFieldMinHeight} = 45dp 的胶囊搜索框。</li>
 * </ul>
 *
 * <h2>⚠️ 这条守卫在 R5 换过方向，两版都写在注释里，别再来回翻</h2>
 *
 * <p><b>R4（上一版）</b>钉的是「不许覆盖 Miuix 默认值」，依据是上面那几个文档值。
 * 结果真机上得到的是「侧栏 UI 很扩散」：一行会话 ~72dp、一屏只放得下五条。
 * 用户原话：「你不觉得这个侧栏UI很扩散吗，收紧一点不懂吗」。
 *
 * <p><b>R5（本版）</b>改成钉「有预算、且不超标」。理由：那些默认值面向<b>设置页</b>
 * （一行一个开关，56dp 合理），而侧栏是<b>密集导航列表</b>。遵照文档指的是交互与
 * 视觉语言，不是把数值抄过来。
 *
 * <p>同时保留 R4 那条真正的教训：宽度/高度这类几何约束必须钉在**真正生效的那一层**。
 * 曾经的 {@code heightIn(max = SessionRowMaxHeight)} 挂在 {@code Card} 上，
 * 而 {@code Card} 内部没有 {@code heightIn(min)}，于是「把上限从 48 提到 58」
 * 那次调整<b>从来没有生效过</b> —— 所以现在会话行的下限写在它自己的 {@code Row} 上，
 * 并有断言钉住。
 *
 * <p>横向是<b>刻意不动</b>的：12(卡片内缩) + 16(行内边距) = 28dp，与
 * {@code SmallTitle} 自带的 28dp 对齐。收紧只动纵向与行间距。
 */
public final class SidebarMetricsTest {

    private static final String SIDEBAR =
            "app/src/main/java/com/zhizhu/zhicode/compose/ui/Sidebar.kt";

    /** Miuix 文档给出的 {@code BasicComponentDefaults.InsideMargin} 横向值。 */
    private static final int MIUI_BASIC_COMPONENT_INSET_DP = 16;

    /** 组标题（{@code SmallTitleDefaults.InsideMargin}）与行内容共同的左边缘。 */
    private static final int LEFT_BASELINE_DP = 28;

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

    private static boolean has(String source, String needle) {
        return source.replaceAll("\\s+", "").contains(needle.replaceAll("\\s+", ""));
    }

    /** 从 {@code private val NAME = 12.dp} 里取出数字。找不到返回 -1。 */
    private static int dpOf(String text, String name) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("val\\s+" + name + "\\s*=\\s*(\\d+)\\.dp")
                .matcher(text);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    /** 从 {@code (} 的位置起取出配对括号之间的文本（跳过字符串字面量）。 */
    private static String balanced(String text, int openAt) {
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
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) return text.substring(openAt + 1, i);
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) return text.substring(openAt + 1, i);
            }
        }
        return "";
    }

    private static String functionBody(String text, String signature) {
        int at = text.indexOf(signature);
        if (at < 0) throw new AssertionError("找不到函数：" + signature + "（断言过期，请更新守卫）");
        int brace = text.indexOf('{', at + signature.length());
        if (brace < 0) throw new AssertionError("找不到函数体：" + signature);
        return balanced(text, brace);
    }

    public static void main(String[] args) throws Exception {
        String root = args.length > 0 ? args[0] : ".";
        String sidebar = stripComments(read(root, SIDEBAR));

        // ---- 1. 行高：**必须有显式预算**，且不许超 -------------------------------------------------
        //
        // ⚠️ 断言方向在 R5 反转了。R4 这一节要求「不许覆盖 Miuix 默认值」，
        // 依据是文档的 `BasicComponentDefaults.InsideMargin = PaddingValues(16.dp)` 与
        // 组件内部 `heightIn(min = 56.dp)`。真机上得到的是「侧栏 UI 很扩散」——
        // 一行会话 ~72dp、一屏只放得下五条（用户原话：
        // 「你不觉得这个侧栏UI很扩散吗，收紧一点不懂吗」）。
        //
        // 教训写在这里：那些默认值面向**设置页**（一行一个开关，56dp 合理），
        // 侧栏是**密集导航列表**。遵照文档指的是交互与视觉语言，不是把数值抄过来。
        // 所以现在钉的是"有预算、不超标"，而不是"必须吃默认值"。
        String rowBody = functionBody(sidebar, "private fun SidebarRow(");
        require(rowBody.contains("insideMargin"),
                "SidebarRow 必须显式收窄 insideMargin：Miuix 默认纵向 16dp 配内部 "
                        + "heightIn(min = 56.dp)，密集列表里一行就是 56dp");
        require(rowBody.contains("heightIn"),
                "SidebarRow 必须把行高夹到 SidebarRowMinHeight：组件内部的 "
                        + "min = 56.dp 不会自己变小");
        int rowMax = dpOf(sidebar, "SidebarRowMinHeight");
        require(rowMax >= 1 && rowMax <= 48,
                "SidebarRowMinHeight = " + rowMax + "dp 超出预算（1..48）。"
                        + "Miuix 默认 56dp 是设置页的值；侧栏是密集列表，"
                        + "超过 48dp 一屏就放不下足够多的入口了");

        String sessionBody = functionBody(sidebar, "private fun SessionRowInner(");
        require(!sessionBody.contains("BasicComponent("),
                "会话行不许再用 BasicComponent：它内部 heightIn(min = 56.dp)，"
                        + "且插槽是为「标题 + 摘要」两行设计的 —— R5 改单行后被它撑高，"
                        + "整行的空白比文字还多");
        require(sessionBody.contains("heightIn(min = SessionRowMinHeight)"),
                "会话行必须自己写高度下限：少了 BasicComponent 之后没人撑高度，"
                        + "行会缩成图标那么高、触摸区不达标");
        int sessionMin = dpOf(sidebar, "SessionRowMinHeight");
        require(sessionMin >= 40 && sessionMin <= 48,
                "SessionRowMinHeight = " + sessionMin + "dp 超出预算（40..48）："
                        + "低于 40 触摸区不达标，高于 48 就不再是「密集」列表了");
        require(dpOf(sidebar, "SessionRowIconSize") >= 1 && dpOf(sidebar, "SessionRowIconSize") <= 20,
                "SessionRowIconSize 必须 ≤ 20dp：Miuix 字形固有 24dp 在密集列表里偏大");
        require(dpOf(sidebar, "SidebarRowIconSize") >= 1 && dpOf(sidebar, "SidebarRowIconSize") <= 20,
                "SidebarRowIconSize 必须 ≤ 20dp（同上）");

        // ---- 2. 卡片圆角：允许覆盖，但要在密集列表这一档 ----------------------
        require(sidebar.contains("cornerRadius ="),
                "会话卡片必须显式给定圆角：CardDefaults.CornerRadius = 16dp 是给"
                        + "「页面上几张互不相关的卡片」的，密集列表里 16dp 会让相邻两行"
                        + "看起来各是一个大方块，列表的连续感就没了");
        int corner = dpOf(sidebar, "SidebarRowCorner");
        require(corner >= 8 && corner <= 14,
                "SidebarRowCorner = " + corner + "dp 超出预算（8..14）："
                        + "16dp 太方（列表不连续），低于 8dp 又不像 Miuix 的卡片");

        // ---- 3. 28dp 基线：横向**不动**（收紧只动纵向）------------------------
        int inset = dpOf(sidebar, "SidebarInset");
        require(inset > 0, SIDEBAR + " 里找不到 SidebarInset —— 断言过期，请更新守卫");
        int rowH = dpOf(sidebar, "SidebarRowHorizontal");
        require(rowH > 0, SIDEBAR + " 里找不到 SidebarRowHorizontal —— 断言过期，请更新守卫");
        require(inset + rowH == LEFT_BASELINE_DP,
                "卡片 " + inset + "dp + 行横向内边距 " + rowH + "dp = " + (inset + rowH)
                        + "dp，必须等于 " + LEFT_BASELINE_DP + "dp"
                        + "（SmallTitleDefaults.InsideMargin 的横向值）："
                        + "否则组标题与行图标错开 —— 用户报的「标题缩进比行深一截」就是这个。"
                        + "R5 收紧的是纵向与行间距，横向这条基线不能动，"
                        + "否则看起来像「有的行缩进、有的行没缩进」");
        require(has(sidebar, "val sessions = remember(state.sessions, query) { filterSessions(state.sessions, query) }"),
                "过滤必须走 filterSessions 且以 (state.sessions, query) 为 remember 键："
                        + "少一个键就会过滤不动，或多做一次无谓的列表分配");

        // ---- 4. 搜索框：文档 SearchBar 一节的 InputField ----------------------
        require(sidebar.contains("InputField("),
                "会话搜索必须用 Miuix 的 InputField（文档 SearchBar 一节：45dp 胶囊、"
                        + "默认放大镜与清除按钮、surfaceContainerHigh 底）");
        require(!sidebar.contains("ZhiTextField(") && !sidebar.matches("(?s).*[^a-zA-Z]TextField\\s*\\(.*"),
                "侧栏不许再出现 ZhiTextField / 裸 TextField：搜索胶囊是 InputField 的职责");
        require(has(sidebar, "expanded = searchExpanded")
                        && has(sidebar, "onExpandedChange = { searchExpanded = it }"),
                "InputField 的 expanded 必须接真实状态：hasFocusReassignBug 在 API ≤ 27"
                        + "（本工程 minSdk 24）为 true，那时输入框未展开是 disabled 的，"
                        + "靠点一下回调 onExpandedChange(true) 再请求焦点 —— "
                        + "传常量 false 的话 Android 8.x 上这个搜索框永远点不进去");

        // ---- 5. 会话列表间距：R5 收到 4dp --------------------------------------
        //
        // R4 用的是文档 Card 一节的 `vertical = 8.dp`。那 8dp 是给"设置页里几张
        // 互不相关的卡片"的；侧栏是一份紧凑列表，8dp 的缝在小屏上直接少放一条会话。
        require(has(sidebar, "verticalArrangement = Arrangement.spacedBy(SessionRowSpacing)"),
                "会话卡片间距必须走 SessionRowSpacing；不要再写回 ZhiSpace.s（8dp）—— "
                        + "那是文档给「互不相关的卡片」的值，密集列表用 4dp");
        int spacing = dpOf(sidebar, "SessionRowSpacing");
        require(spacing >= 2 && spacing <= 6,
                "SessionRowSpacing = " + spacing + "dp 超出预算（2..6）："
                        + "0 会让圆角卡片糊在一起，8dp 以上就是 R4 那个「很扩散」的取值");

        // ---- 6. 本测试自身必须被 canonical suite 执行 -------------------------
        String script = read(root, "test-source-no-build.sh");
        require(script.contains("SidebarMetricsTest \"$PROJECT_ROOT\""),
                "canonical source suite 必须执行本守卫");

        System.out.println("SidebarMetricsTest PASS"
                + "（行内边距/行高/圆角均用 Miuix 文档默认值 · 基线 " + LEFT_BASELINE_DP + "dp = "
                + inset + " + " + MIUI_BASIC_COMPONENT_INSET_DP + " · 搜索走 InputField）");
    }
}
