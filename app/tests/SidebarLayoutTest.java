import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 侧栏版式守卫（UI 重设计 R4 —— 对齐 Miuix 官方文档）。
 *
 * <p>判据是 `compose-miuix-ui.github.io` 的中文文档（本地在 `~/projects/miuix/docs/zh_CN/`），
 * 侧栏里所有**自定的**数都被换成了文档默认值（见 SidebarMetricsTest）；
 * 这一轮留下来的是那些文档**没有**规定、需要我们自己拿主意的部分：
 * 「新会话」不许是满宽主色条、组标题与行内容要对齐、会话行的选中态三样、
 * 空列表的两种文案。它们全都能编译通过，只有真机上看得出差别。
 *
 * <h2>1. 28dp 基线</h2>
 *
 * <p>Miuix 上游设置页的几何是三条默认值凑出来的：
 * {@code SmallTitleDefaults.InsideMargin} 横向 <b>28dp</b>、
 * {@code Card} 外挂 {@code padding(horizontal = 12dp)}、
 * {@code BasicComponentDefaults.InsideMargin} <b>16dp</b> ——
 * 于是组标题与行内容的左边缘**都在 28dp**。
 *
 * <p>本项目原来的侧栏是「标题 34dp、行 14dp」，差 20dp：删掉自己加的那 8dp 时
 * 只算了自己的，没算 {@code SmallTitle} 自带的 28dp。所以这条断言解成
 * 「页级横向 0 + 卡片 12 + 行 16 == 28」，任何一项被改动都会红。
 *
 * <h2>2. 主色不许当整行底色</h2>
 *
 * <p>{@code scheme.primary} 是 Miuix 的**品牌蓝**，不是语义色。它当整行底色时
 * 就成了整屏唯一的满饱和色块，把视觉重心压在一个"新建"按钮上。现在只允许它出现在
 * 「图标色」「选中竖条」「选中图标」这三处。见 {@link #requireNoPrimaryRowFill}。
 */
public final class SidebarLayoutTest {

    private static final String SIDEBAR =
            "app/src/main/java/com/zhizhu/zhicode/compose/ui/Sidebar.kt";

    /**
     * 组标题与行内容共同的左边缘。
     *
     * <p>来源是 Miuix 的两条默认值（{@code SmallTitleDefaults.InsideMargin} = 28dp、
     * {@code BasicComponentDefaults.InsideMargin} = 16dp）加本项目卡片外挂的 12dp。
     * 上游那两条在 `~/projects/miuix` 里，不在本仓库里，所以这里写死 28 并注明出处：
     * 它们一旦变了，真机上一眼就能看出错位，而这边的三条数字仍应保持相加等于它。
     */
    private static final int LEFT_BASELINE_DP = 28;

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static String read(String root, String relative) throws Exception {
        return new String(Files.readAllBytes(Paths.get(root, relative)), StandardCharsets.UTF_8);
    }

    /** 去掉注释：否则「代码删了、注释里还写着」会让 contains 断言静默通过。 */
    private static String stripComments(String text) {
        return text.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", " ");
    }

    private static String squash(String text) {
        return text.replaceAll("\\s+", "");
    }

    private static boolean has(String source, String needle) {
        return squash(source).contains(squash(needle));
    }

    /** 取 `private val NAME = 12.dp` 的数字；找不到返回 -1。 */
    private static int dpOf(String text, String name) {
        Matcher m = Pattern.compile("val\\s+" + name + "\\s*=\\s*(\\d+)\\.dp").matcher(text);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    /**
     * 从 `(` 的位置起取出配对括号之间的文本（跳过字符串字面量）。
     *
     * <p>用它取函数体，是为了把断言**限制在那一个函数里**：
     * 在整个文件上搜 `scheme.primary` 会把选中竖条也算进去。
     */
    private static String balancedBody(String text, int openAt) {
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
            }
        }
        return "";
    }

    /** 某个顶层 `private fun NAME(` 的函数体。 */
    private static String functionBody(String text, String signature) {
        int at = text.indexOf(signature);
        if (at < 0) throw new AssertionError("找不到函数：" + signature + "（断言过期，请更新守卫）");
        int brace = text.indexOf('{', at + signature.length());
        if (brace < 0) throw new AssertionError("找不到函数体：" + signature);
        return balancedBody(text, brace);
    }

    public static void main(String[] args) throws Exception {
        String root = args.length > 0 ? args[0] : ".";
        String sidebar = stripComments(read(root, SIDEBAR));

        // ---- 1. 28dp 基线 ----------------------------------------------------
        int inset = dpOf(sidebar, "SidebarInset");
        require(inset > 0,
                SIDEBAR + " 里找不到 SidebarInset —— 断言过期，请更新守卫");

        String rowBody = functionBody(sidebar, "private fun SidebarRow(");
        String sessionBody = functionBody(sidebar, "private fun SessionRowInner(");

        // 行内边距与行高现在**不覆盖**（Miuix 文档默认 16dp / 56dp 最小高），
        // 所以基线只钉卡片这一半；"不许覆盖"由 SidebarMetricsTest 负责。
        require(inset + 16 == LEFT_BASELINE_DP,
                "卡片 " + inset + "dp + Miuix 行内边距 16dp 必须等于 " + LEFT_BASELINE_DP + "dp："
                        + "组标题的 28dp 是 SmallTitle 自带的，动这项就会让"
                        + "标题与行内容错开（用户报的「标题缩进比行深一截」就是这个）");

        // ---- 2. 组标题：裸 SmallTitle，且页级不加横向内边距 ---------------------
        require(sidebar.contains("SmallTitle("),
                "组标题必须用 Miuix 的 SmallTitle（裸调用，靠它自带的 28dp 内边距对齐）");
        require(!sidebar.contains("ZhiSectionLabel"),
                "侧栏不许再用 ZhiSectionLabel：它是 SmallTitle 外面又套了一层 padding，"
                        + "自己再加 start 就会把标题推离 28dp 基线 —— "
                        + "上一版正是 8(start) + 28(SmallTitle) = 36dp，行在 14dp");
        require(has(sidebar, "Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))"),
                "面板的 Column 只能有纵向内边距：横向留白一律交给 SidebarInset（卡片那一层）。"
                        + "在页级再加一层会把组标题与行**一起**推走，并把分组卡片挤窄");

        // ---- 3. 主色不许当整行底色 -------------------------------------------
        requireNoPrimaryRowFill(rowBody);
        require(!sidebar.contains("emphasized"),
                "SidebarRow 的 emphasized 参数(整行 scheme.primary 填充)不许回来："
                        + "那正是截图里最抢眼的那条满宽蓝条");
        require(has(sidebar, "iconTint = scheme.primary"),
                "「新会话」现在靠 iconTint = scheme.primary 表达\"这是主操作\"："
                        + "整行不上色、只有图标上色，与「运行环境就绪」只有图标变绿同一种做法");

        // ---- 4. 会话行的选中态：三样齐全 -------------------------------------
        require(sessionBody.contains("scheme.secondaryContainer"),
                "选中行的底色必须用 secondaryContainer，不能用 primary："
                        + "主色只是品牌蓝，整行铺开就把\"我在这里\"变成了\"这一条被选中\"");
        require(has(sessionBody, "targetValue = if (active) scheme.secondaryContainer"),
                "选中底色必须由 active 驱动（否则选中态根本不会出现）");
        require(has(sessionBody, "fontWeight = if (active) FontWeight.Bold"),
                "选中行的标题必须加粗（用户点名要的「明显的选中态」三样之一）");
        require(has(sessionBody, "color = titleColor") && has(sessionBody, "active -> scheme.onSecondaryContainer"),
                "选中行的标题必须换成 onSecondaryContainer："
                        + "底色亮了而字色还是 onSurface，对比度会掉到看不清");
        require(sessionBody.contains(".width(3.dp)"),
                "选中行左侧必须有 3dp 主色竖条（用户点名要的三样之三）");
        require(has(sessionBody, ".align(Alignment.CenterStart)"),
                "竖条必须用 align(Alignment.CenterStart) **贴在卡片左缘**："
                        + "排进 Row 里的话为了让它落在 28dp，图标会被推到 39dp，"
                        + "于是会话行的图标与「更多」那些行差 11dp");

        // ---- 5. 会话行排版：R5 收紧后的**单行**布局 --------------------------
        require(has(sessionBody, "style = MiuixTheme.textStyles.headline1"),
                "会话行标题用 textStyles.headline1（文档 basiccomponent 一节标题的字号）");
        require(has(sessionBody, "style = MiuixTheme.textStyles.footnote1"),
                "会话行的时间/条数用 textStyles.footnote1(13sp)：R5 把它从**第二行**挪到了"
                        + "标题右侧。原来标题一行、摘要一行，一行会话要 ~72dp，一屏只放得下五条"
                        + "（用户原话：「你不觉得这个侧栏UI很扩散吗，收紧一点不懂吗」）");
        require(!has(sessionBody, "style = MiuixTheme.textStyles.body2"),
                "会话行里不许再出现 body2 的第二行摘要：那正是 R5 要拆掉的"
                        + "「标题 + 摘要」两行布局（单行后整行降到 44dp）");
        require(has(sessionBody, "session.updatedAtLabel") && has(sessionBody, "session.messageCount"),
                "时间与条数必须保留（会话列表里最有用的两个信号）—— R5 只把它们从第二行"
                        + "挪到标题右侧，信息不能丢");
        require(has(sessionBody, "painter = ZhiIcons.chat"),
                "会话行要有会话图标（上一版整行只有两行文字，看着不像一个列表项）");
        // ⚠️ 行尾**不放**任何按钮（用户原话：「对话中的三个点有什么用，为啥不改成长按
        // 触发下拉菜单」）。长按这一条已经会弹 `ZhiAnchoredActionMenu`（与对话流同一
        // 组件），⋯ 只是让"还有个菜单"可见，代价是每行多一个 40dp 触摸区。
        require(!has(sessionBody, "endActions"),
                "会话行不许再挂行尾按钮（⋯ / 删除键都不行）：长按这一条已经弹"
                        + "ZhiAnchoredActionMenu（与对话流同一组件），里面就有重命名与删除 —— "
                        + "行尾再放一个是第二个入口，点开会话时手指滑一下就会碰到");
        require(!sidebar.contains("ZhiIcons.close"),
                "会话行不许再出现删除键：删除只能在长按菜单里（见 SidebarNavigationTest 第 6 节）");
        // R5：会话行**不再**用 BasicComponent。
        //
        // 它内部是 `heightIn(min = 56.dp)` + 「标题/摘要」两行插槽，单行布局套上去
        // 只会白白多出十几 dp 的空白。交互没丢 —— 外层 Card 自己带
        // onClick / onLongPress，按压反馈由 pressFeedbackType 给。
        require(!has(sessionBody, "BasicComponent("),
                "会话行不许再用 BasicComponent：它是为「标题 + 摘要」两行设计的"
                        + "（内部 heightIn(min = 56.dp)），单行布局会被撑高 —— "
                        + "交互靠外层 Card 的 onClick/onLongPress + pressFeedbackType");
        require(has(sessionBody, "heightIn(min = SessionRowMinHeight)"),
                "会话行必须有显式的高度下限（SessionRowMinHeight）：不再有 BasicComponent"
                        + "替我们撑高度，缺了它行会缩到图标那么高、触摸区不达标");
        require(has(sessionBody, "Modifier.weight(1f)"),
                "标题必须 weight(1f)：标题过长要省略号，而不是把右侧的时间/条数挤出屏幕");

        // ---- 6. 搜索 ----------------------------------------------------------
        require(sidebar.contains("InputField("),
                "会话搜索必须用 Miuix 的 InputField（文档 SearchBar 一节的胶囊搜索框）；"
                        + "它不是 TextFieldConventionTest 管的那种裸 TextField —— "
                        + "胶囊底、自带放大镜与清除、自己取 LocalContentColor 与 primary 光标");
        require(has(sidebar, "expanded = searchExpanded"),
                "InputField 的 expanded 必须接真实状态：hasFocusReassignBug 在 API ≤ 27"
                        + "（本工程 minSdk 24）为 true，未展开时输入框是 disabled 的，"
                        + "传常量 false 会让 Android 8.x 上这个搜索框永远点不进去");
        require(has(sidebar, "val sessions = remember(state.sessions, query) { filterSessions(state.sessions, query) }"),
                "过滤必须走 filterSessions 且以 (state.sessions, query) 为 remember 键："
                        + "少一个键就会过滤不动，或多做一次无谓的列表分配");
        require(sidebar.contains("private fun filterSessions("),
                "filterSessions 必须存在（按标题或备注过滤，与参考实现 renderSidebarSessionRows 同判据）");
        require(sidebar.contains("\"没有匹配的会话\"") && sidebar.contains("\"暂无已保存会话\""),
                "两种空列表要分开说：「暂无已保存会话」与「没有匹配的会话」—— "
                        + "搜索没命中时说「暂无已保存会话」会让人以为会话丢了");

        // ---- 7. 本测试自身必须被 canonical suite 执行 -------------------------
        String script = read(root, "test-source-no-build.sh");
        require(script.contains("SidebarLayoutTest \"$PROJECT_ROOT\""),
                "canonical source suite 必须执行本守卫");

        System.out.println("SidebarLayoutTest PASS"
                + "（" + LEFT_BASELINE_DP + "dp 基线（卡片 " + inset + "dp）"
                + " · 主色只用于图标/竖条 · 选中态三样齐全 · 文档字号 headline1/body2"
                + " · 搜索走 InputField）");
    }

    /**
     * 行**整行**不许被主色填充。
     *
     * <p>判据是「把底色接到主色上」这两种写法都不许出现：
     * {@code CardDefaults.defaultColors(color = scheme.primary…} 与
     * {@code background(scheme.primary)}。它们正是"满宽蓝条"的实现方式，
     * 而且删掉任意一处都不会编译失败 —— 只会让那一行突然变成最抢眼的东西。
     */
    private static void requireNoPrimaryRowFill(String rowBody) {
        require(!has(rowBody, "color = scheme.primary"),
                "SidebarRow 不许把整行底色设成主色（那就是满宽蓝条）");
        require(!has(rowBody, "background(scheme.primary)"),
                "SidebarRow 不许用 background(scheme.primary) 铺整行");
        require(!has(rowBody, "scheme.primary"),
                "SidebarRow 函数体里不该出现 scheme.primary："
                        + "整行配色只走 tint（整行换色）与 iconTint（只换图标）两个参数");
    }
}
