import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 「图标集是自洽的，而且每个图标和它的作用相符」的守卫。
 *
 * <h2>这一版守的是什么</h2>
 *
 * 图标集现在是 **Material Symbols（Rounded, Fill1）** —— 实心圆角的 MD3 字形，
 * 上游是 `google/material-design-icons`，路径数据逐字拷进
 * `ui/ZhiMaterialIcons.kt`（见那个文件顶部的取证过程）。
 *
 * 这个决定不是审美偏好：用户要的是「扁平化（**不是线条**）和 rikka 等 md3 图标
 * 结合起来」，而 rikkahub 实际用的 HugeIcons 与 Lucide **两套都是线条集** ——
 * 照抄正好是他明确排除的那一种。rikkahub 用的那一族里满足"实心"的只有
 * Material 3 / Material Symbols。
 *
 * 于是曾经在 Miuix、AOSP 那两轮里写下的退化，必须重新钉一遍：
 *
 * <ol>
 * <li><b>名字对、形状不对</b>。这是本项目真实踩过的坑，也是"怪怪的框"的根因：
 *     用 Miuix 那套时，`More` 是竖排三点（旧代码拿 `rotate(90f)` 硬掰）、
 *     `ExpandMore` 是「L 形框 + 圆点 + L 形框」而不是箭头、
 *     `Theme` 是滚筒形状而不是明暗切换。现在这三个字形都换了直系上游字形
 *     （`more_horiz` / `expand_more` / `contrast`），而且**自绘那一整套已经删掉**：
 *     下面第 1 节直接拿声明的字形集合与引用集合对账，两头都不许对不上。</li>
 * <li><b>图标互换后语义又对不上</b>。这是改动的全部意义：原先终端挂"笔记"、
 *     命令挂"待办清单"、运行环境挂"对勾"、失败与关闭共用一个字形。
 *     两两互换回去照样能编译、界面也照样能跑 —— 只有第 3 节能拦住。</li>
 * <li><b>变回线条</b>。这是本轮反复最多的一条：`HugeIcons` / `Lucide` 都是线条，
 *     Material 的 M2 那套是老直角，而"看着像实心"和"真的是实心"在代码里只差
 *     `stroke` 与 `fill` 两个词。第 2 节把整集钉成**只许 fill**。</li>
 * <li><b>两种来源并存</b>。上一轮是"自绘 + 库里"两套，并排就能看出笔重不同。
 *     第 2 节要求整个 `compose/ui/` 下**没有任何 Miuix 图标引用**
 *     （Miuix 组件内部用的那些不算，那是它自己的实现）。</li>
 * </ol>
 *
 * <h2>为什么还要断言"画廊是全集"</h2>
 *
 * 图标好不好看只能靠眼睛，而眼睛需要一处能一次看全的地方 —— UI 调试页那张图标表。
 * 它一旦漏掉新图标，就等于没有验收入口。所以这里要求它覆盖 `ZhiIcons` 的**每一个**名字。
 */
public final class IconSetTest {

    private static final String SRC = "app/src/main/java/com/zhizhu/zhicode/compose/";
    private static final String ICONS = SRC + "ui/ZhiIcons.kt";
    private static final String MATERIAL = SRC + "ui/ZhiMaterialIcons.kt";
    private static final String CARDS = SRC + "ui/chat/MessageCards.kt";
    private static final String DEBUG_PAGE = SRC + "ui/debug/UiDebugPage.kt";
    private static final String UI_DIR = SRC + "ui";

    /** 旧 AOSP 那一轮留下的图标资源目录。必须为空，否则两套来源会并存。 */
    private static final String DRAWABLES = "app/src/main/res/drawable";

    /** 已经删掉的自绘字形文件。它回来就意味着"两种来源"又回来了。 */
    private static final String RETIRED_VECTOR_ICONS = SRC + "ui/ZhiVectorIcons.kt";

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static String read(String root, String relative) throws Exception {
        return new String(Files.readAllBytes(Paths.get(root, relative)),
                StandardCharsets.UTF_8);
    }

    /** 去注释：注释里提到某个名字不该让断言通过。 */
    private static String stripComments(String text) {
        String noBlock = text.replaceAll("(?s)/\\*.*?\\*/", " ");
        return noBlock.replaceAll("(?m)//[^\\n]*", " ");
    }

    /** 去掉全部空白，便于对格式化免疫地做子串匹配。 */
    private static String squash(String text) {
        return text.replaceAll("\\s+", "");
    }

    /**
     * 取 [from] 到下一个顶层 `private fun` 之间的片段（找不到终点就吃到末尾）。
     *
     * 用途是把断言**限定在某个函数体内**：同一个文件里别的函数用同一个图标是合法的
     * （例如 `expand`/`collapse` 在思考过程面板里就该有），只有工具行里不许有。
     */
    private static String section(String source, String from, String to) {
        int at = source.indexOf(from);
        if (at < 0) return "";
        int end = source.indexOf(to, at + from.length());
        return end < 0 ? source.substring(at) : source.substring(at, end);
    }

    /** 找出 `ZhiIcons.kt` 里用到的全部字形名（`ZhiMaterialIcons.xxx`）。 */
    private static LinkedHashSet<String> referencedGlyphs(String icons) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        Matcher m = Pattern.compile("ZhiMaterialIcons\\.([A-Za-z0-9_]+)").matcher(icons);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    /** 找出 `ZhiMaterialIcons.kt` 里声明的全部字形名（`val xxx: ImageVector`）。 */
    private static LinkedHashSet<String> declaredGlyphs(String material) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        Matcher m = Pattern.compile("val\\s+([A-Za-z0-9_]+)\\s*:\\s*ImageVector").matcher(material);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    /** 找出 `ZhiIcons` 里声明的全部图标名（`val xxx: Painter`）。 */
    private static LinkedHashSet<String> declaredIconNames(String icons) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        Matcher m = Pattern.compile("val\\s+([A-Za-z0-9_]+)\\s*:\\s*Painter").matcher(icons);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    public static void main(String[] args) throws Exception {
        String root = args.length > 0 ? args[0] : ".";
        String iconsRaw = read(root, ICONS);
        String icons = stripComments(iconsRaw);
        String materialRaw = read(root, MATERIAL);
        String material = stripComments(materialRaw);
        String cards = stripComments(read(root, CARDS));
        String debugPage = stripComments(read(root, DEBUG_PAGE));

        // ---- 1. 字形集合：声明的、引用的、写在清单里的，三者必须完全一致 ------
        //
        // 这三边任何一边对不上都是真问题：
        //   · 引用了没声明的 → 编译失败，但错误信息往往看不出是"名字写错"；
        //   · 声明了没引用的 → 说明 ZhiIcons 那边的映射改了名，留着的字形没人用，
        //     后来者会以为它还在生效（本工程真出过一次：`ClearAll` 被换成库里的
        //     橡皮擦之后，自绘的那个还留在文件里）；
        //   · 清单少一行 → 出处无从核对。
        LinkedHashSet<String> referenced = referencedGlyphs(icons);
        LinkedHashSet<String> declared = declaredGlyphs(material);
        require(declared.size() >= 50,
                MATERIAL + " 里只声明了 " + declared.size() + " 个字形："
                        + "整集应该主要走 ZapMaterialIcons（Material Symbols），"
                        + "数量骤减说明有人退回自绘或别的来源了");
        List<String> notDeclared = new ArrayList<>(referenced);
        notDeclared.removeAll(declared);
        require(notDeclared.isEmpty(),
                ICONS + " 引用了 " + MATERIAL + " 里不存在的字形：" + notDeclared);
        List<String> unused = new ArrayList<>(declared);
        unused.removeAll(referenced);
        require(unused.isEmpty(),
                MATERIAL + " 里这些字形没有任何地方在用：" + unused
                        + "\n（多半是 " + ICONS + " 那边的映射改了名。没人用的字形会被误认为"
                        + "仍在生效 —— 要么删掉，要么把它接回映射表）");
        for (String glyph : declared) {
            require(materialRaw.contains("| [" + glyph + "] |"),
                    MATERIAL + " 的字形清单里没有 " + glyph + " 这一行："
                            + "每个字形都要写明它对应上游的哪个名字与哪条路径");
        }

        // 出处与许可必须写在文件里（查**原文**，注释会被 stripComments 删掉）。
        for (String token : new String[]{
                "Material Symbols", "google/material-design-icons", "Apache",
                "tools/material-symbols-fetch.sh", "materialsymbolsrounded"}) {
            require(materialRaw.contains(token),
                    MATERIAL + " 必须写明字形来源与取用方式（缺 \"" + token + "\"）："
                            + "否则下一个人不知道这是上游数据、也不知道怎么重新取一遍");
        }
        require(iconsRaw.contains("Material Symbols") && iconsRaw.contains("Apache"),
                ICONS + " 必须写明图标来源（Material Symbols，Apache-2.0）");

        // ---- 2. 只许填充，且整集只有一个来源 -----------------------------------
        //
        // 「扁平化（不是线条）」这一条在代码里只差 `fill` 与 `stroke` 两个词，
        // 而两者都能编译、都能显示 —— 只有这一节能拦住"某几个字形偷偷变成线条"。
        require(!squash(material).contains("strokeLineWidth"),
                MATERIAL + " 里出现了 `strokeLineWidth`：这一集是**实心**字形，"
                        + "混进一个描边字形就会在一排实心块里显得像没画完");
        require(!squash(material).contains("stroke=SolidColor"),
                MATERIAL + " 里出现了描边画法：整集必须是 fill（扁平化的意思就是实心）");
        // 每个字形都必须走同一个构造入口 —— 手写 ImageVector.Builder 的地方
        // 就是"这个图标和别人不一样大"的来源。
        require(material.contains("private fun material(name: String, pathData: String): ImageVector"),
                MATERIAL + " 必须有唯一的 material() 构造入口（视口换算只写一次）");
        int lazyCount = countOf(material, ": ImageVector by lazy { material(")
                + countOf(material, ": ImageVector by lazy { material(");
        require(lazyCount == declared.size() * 2,
                MATERIAL + " 里有 " + declared.size() + " 个字形，但只有 " + lazyCount
                        + " 个走 material()：绕开它的那个会用自己的视口换算，"
                        + "大小就会和整集不一致");
        // Miuix 图标不许再出现在我们自己的界面代码里（Miuix 组件内部的实现不算）。
        Path uiDir = Paths.get(root, UI_DIR);
        List<String> miuixUsers = new ArrayList<>();
        if (Files.isDirectory(uiDir)) {
            Files.walk(uiDir)
                    .filter(p -> p.toString().endsWith(".kt"))
                    .forEach(p -> {
                        try {
                            String body = stripComments(
                                    new String(Files.readAllBytes(p), StandardCharsets.UTF_8));
                            if (body.contains("MiuixIcons.")) {
                                miuixUsers.add(uiDir.relativize(p).toString());
                            }
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
        require(miuixUsers.isEmpty(),
                "这些文件里还在直接引用 Miuix 的图标：" + miuixUsers
                        + "\n（整集已经统一到 Material Symbols；同一个界面里混两套字形，"
                        + "笔重与圆角对不上正是「这个图标怪怪的」的来源。"
                        + "Miuix 组件内部用的那几个不算，它们不在我们源码里）");
        // 自绘那一套已经删掉了：它回来就意味着"两种来源"又回来了。
        require(!Files.exists(Paths.get(root, RETIRED_VECTOR_ICONS)),
                RETIRED_VECTOR_ICONS + " 又出现了：自绘字形与库里那套并排时，"
                        + "曲率、圆角、笔重都很难同调 —— 上一轮的「沙盒的有点不太好看，MCP 也是」"
                        + "就是这么来的，已经全部换成上游字形");
        require(!squash(icons).contains("R.drawable") && !squash(icons).contains("painterResource"),
                ICONS + " 不得引用 R.drawable / painterResource："
                        + "图标只有 ImageVector 一种来源（否则又会出现两套）");
        File drawableDir = new File(root, DRAWABLES);
        File[] leftover = drawableDir.listFiles((dir, name) -> name.startsWith("ic_"));
        require(leftover == null || leftover.length == 0,
                DRAWABLES + " 里还留着 " + (leftover == null ? 0 : leftover.length)
                        + " 个图标 XML：两套图标来源并存，改了一套另一套还在生效");
        // 图标**属性**必须返回 Painter。`ImageVector` 只允许出现在
        // `vector(imageVector: ImageVector): Painter` 那个适配函数里 ——
        // 它是字形层与语义层合流的唯一通道。
        require(!squash(icons).contains(":ImageVector@Composableget()"),
                ICONS + " 的图标属性不得返回 ImageVector：统一返回 Painter，"
                        + "否则调用点会各自处理字形");
        require(squash(icons).contains("privatefunvector(imageVector:ImageVector):Painter"),
                ICONS + " 必须有唯一的 vector() 适配：ImageVector → Painter 只允许一处");

        // ---- 3. 语义映射逐条钉死 ---------------------------------------------
        //
        // 每一条都对应一个真实出现过的错误映射，注释里写明"为什么是这个字形"。
        // 互换回去能编译、能跑，只有这条能拦住。
        String[][] pinned = {
                {"menu", "ZhiMaterialIcons.Menu", "汉堡按钮"},
                {"theme", "ZhiMaterialIcons.Contrast", "主题是半明半暗的圆"},
                {"floatingBall", "ZhiMaterialIcons.Circle", "悬浮球是实心圆"},
                {"settings", "ZhiMaterialIcons.Settings", "设置是齿轮"},
                {"chat", "ZhiMaterialIcons.ChatBubble", "对话是气泡"},
                {"changes", "ZhiMaterialIcons.Difference", "变更是方框 + 上 + 下 −"},
                {"terminal", "ZhiMaterialIcons.Terminal", "终端页签；原先挂「笔记」"},
                {"files", "ZhiMaterialIcons.Folder", "文件页签是文件夹"},
                {"newSession", "ZhiMaterialIcons.AddCircle", "新建会话是圆里的 +"},
                {"projectHistory", "ZhiMaterialIcons.History", "历史是时钟；原先挂「刷新」"},
                {"projectPath", "ZhiMaterialIcons.Home", "项目目录是房子"},
                {"roleCard", "ZhiMaterialIcons.Person", "角色卡是人像"},
                {"skill", "ZhiMaterialIcons.Extension", "技能是拼图；原先挂待办清单，与命令撞脸"},
                {"sandbox", "ZhiMaterialIcons.DeployedCode",
                        "沙箱是「装了代码的箱子」；上一版自绘的线框箱子被用户点名不好看"},
                {"runtime", "ZhiMaterialIcons.Build", "运行环境是扳手；原先是对勾"},
                {"attach", "ZhiMaterialIcons.Add", "附件是裸 +"},
                {"send", "ZhiMaterialIcons.Send", "发送是纸飞机"},
                {"stop", "ZhiMaterialIcons.Stop", "停止是实心方块；两条竖杠是「暂停」"},
                {"arrowRight", "ZhiMaterialIcons.ChevronRight", "向右的小箭头"},
                {"close", "ZhiMaterialIcons.Close", "关闭是叉"},
                {"edit", "ZhiMaterialIcons.Edit", "编辑是铅笔"},
                {"expand", "ZhiMaterialIcons.ChevronRight", "折叠态 ›（与 arrowRight 同字形）"},
                {"collapse", "ZhiMaterialIcons.ExpandMore",
                        "展开态 ⌄：上游没有 chevron_down，向下的箭头就叫 expand_more"},
                {"refresh", "ZhiMaterialIcons.Refresh", "刷新"},
                {"info", "ZhiMaterialIcons.Info", "信息"},
                {"clear", "ZhiMaterialIcons.InkEraser", "清屏是橡皮擦；三条横杠只是「列表」"},
                {"back", "ZhiMaterialIcons.ArrowBack", "返回是左箭头，同样不靠旋转"},
                {"more", "ZhiMaterialIcons.MoreHoriz", "更多操作是**横排**三点"},
                {"moreVert", "ZhiMaterialIcons.MoreVert", "竖向排布处的更多是竖排三点"},
                {"search", "ZhiMaterialIcons.Search", "搜索是放大镜"},
                {"add", "ZhiMaterialIcons.Add",
                        "新建是裸 +（与 attach 同字形，但语义名分开：一个在文件面板、一个在输入器，从不同屏出现）"},
                {"done", "ZhiMaterialIcons.CheckCircle", "完成是圆里的对勾"},
                {"failed", "ZhiMaterialIcons.Error",
                        "失败是圆里的感叹号；原先与 close 共用同一个字形"},
                {"pending", "ZhiMaterialIcons.RadioButtonUnchecked", "尚未开始是空心圆环"},
                {"awaiting", "ZhiMaterialIcons.Help", "等待授权是圆圈问号"},
                {"check", "ZhiMaterialIcons.Check",
                        "列表行的单选标记是**裸对勾**，与圆形的 done 区分"},
                {"directory", "ZhiMaterialIcons.FolderOpen", "目录是**打开**的文件夹"},
                {"file", "ZhiMaterialIcons.Description", "文件是文档"},
                {"delete", "ZhiMaterialIcons.Delete", "删除是垃圾桶"},
                {"image", "ZhiMaterialIcons.Image", "图片是照片；原先借 floatingBall（悬浮球）"},
                {"cloud", "ZhiMaterialIcons.Cloud", "搜索服务入口是云"},
                {"listCount", "ZhiMaterialIcons.List", "搜索结果条数是列表"},
                {"timeout", "ZhiMaterialIcons.Timer", "联网超时是计时器"},
                {"link", "ZhiMaterialIcons.Link", "API 配置是链接（接口地址 + 密钥）"},
                {"tune", "ZhiMaterialIcons.Tune",
                        "推理强度是滑杆；与「自动压缩上限」同字形、靠底色区分"},
                {"lock", "ZhiMaterialIcons.Lock", "权限模式是锁"},
                {"compress", "ZhiMaterialIcons.Compress", "上下文压缩是向内的双向箭头"},
                {"keyboard", "ZhiMaterialIcons.Keyboard", "终端字符模式是键盘"},
                {"mindMap", "ZhiMaterialIcons.Hub",
                        "MCP 是中心节点 + 四向连线；上一版自绘的形状被用户点名不好看"},
                {"layers", "ZhiMaterialIcons.Layers", "UI 调试是组件分层陈列"},
                {"tool(COMMAND)", "ZhiMaterialIcons.Terminal",
                        "跑命令必须是终端；原先挂待办清单"},
                {"tool(READ)", "ZhiMaterialIcons.Description", "读文件是文档；原先挂 File"},
                {"tool(SEARCH)", "ZhiMaterialIcons.Search", "搜索是放大镜"},
                {"tool(EDIT)", "ZhiMaterialIcons.Edit", "写文件是铅笔"},
                {"tool(OTHER)", "ZhiMaterialIcons.Code",
                        "未知工具用中性的代码括号；且不能与 runtime 共用扳手"},
        };
        String body = squash(icons);
        for (String[] row : pinned) {
            String name = row[0];
            String expected = row[1];
            // 形如 `val runtime: Painter @Composable get() = vector(ZhiMaterialIcons.Build)`
            String key = name.startsWith("tool(")
                    ? "ToolKind." + name.substring(5, name.length() - 1) + "->"
                    : "val" + name + ":Painter@Composableget()=";
            int at = body.indexOf(key);
            require(at >= 0, ICONS + " 里找不到 " + name + " 的定义");
            String window = body.substring(at, Math.min(body.length(), at + 160));
            // ⚠️ 必须连结尾的 `)` 一起比，不能只比字形名。
            //
            // 实测（teeth §37 ①d MISS）：`add` 期望的是 `ZhiMaterialIcons.Add`，
            // 而 `ZhiMaterialIcons.AddCircle` **以它开头** —— 单纯 `contains` 时，
            // 把 `add` 的映射改成 `AddCircle` 照样通过。这是常见的**前缀陷阱**：
            // 断言看着在守，其实恒真（`newSession` ↔ `AddCircle` 那一对就是靠
            // 这条才拦得住）。
            require(window.contains(expected + ")"),
                    "「" + name + "」必须指向 " + expected + " —— " + row[2]
                            + "\n（注意：只比字形名会被前缀骗过 —— `Add` 是 `AddCircle` 的前缀）");
        }

        // ---- 4. 两两不许撞脸 -------------------------------------------------
        //
        // 「两个不同的东西共用同一个字形」是最难自己发现的一类错误：
        // 单看每一处都不觉得有问题，只有并排出现时才显得怪。
        String[][] mustDiffer = {
                {"sandbox", "floatingBall", "沙箱与悬浮球是两个不同的东西"},
                {"failed", "close", "「失败」和「关闭」不该是同一个字形"},
                {"skill", "tool(COMMAND)", "技能与跑命令不该撞脸"},
                {"runtime", "tool(OTHER)", "运行环境与「未知工具」是两件事"},
                {"more", "moreVert", "横排三点与竖排三点是不同的控件"},
                {"add", "newSession",
                        "「新建」是裸 +，「新建会话」是圆里的 +："
                                + "两者还是**前缀**关系（Add / AddCircle），最容易在断言里被放过"},
                {"floatingBall", "image", "「悬浮球」与「图片」是两件事"},
                {"directory", "files", "「目录」是打开的文件夹，页签是合口的"},
                {"done", "check", "「完成」是圆里的对勾，列表标记是裸对勾"},
                {"directory", "tool(Move)", "「目录」与「移动文件」是两件事"},
                {"terminal", "tool(COMMAND)", "终端页签与「跑命令」本来就该是同一个字形"},
        };
        Map<String, String> resolved = new HashMap<>();
        for (String[] row : pinned) resolved.put(row[0], row[1]);
        resolved.put("tool(Move)", "ZhiMaterialIcons.DriveFileMove");
        for (String[] row : mustDiffer) {
            String a = resolved.get(row[0]);
            String b = resolved.get(row[1]);
            require(a != null && b != null, "无法解析 " + row[0] + " / " + row[1] + " 的映射");
            if (row[0].equals("terminal") && row[1].equals("tool(COMMAND)")) {
                // 这一对是**应当相同**的反向例子：同一个东西在两处必须用同一个字形。
                require(a.equals(b), "「终端页签」与「跑命令」必须共用同一个字形（" + a
                        + " / " + b + "）—— " + row[2]);
            } else {
                require(!a.equals(b), "「" + row[0] + "」与「" + row[1] + "」不能共用字形 "
                        + a + " —— " + row[2]);
            }
        }

        // ---- 5. 工具行不许再用折叠箭头 ---------------------------------------
        //
        // 用户：「⌃/⌄ 箭头可以去掉，因为点击内容可以快速收回或展开」。
        //
        // ⚠️ 不能只禁 `chevronUp`/`chevronDown` 这两个名字 —— 那样"换成
        // `ZhiIcons.collapse` 再插一个箭头"照样能通过。真正的不变量是
        // "**工具行与工具组表头里没有任何折叠箭头**"，而 `expand`/`collapse`
        // 在同一个文件的**思考过程面板**里是合法的，所以要把检查限定在这两个函数体内。
        String toolRow = section(cards, "private fun ToolRow(", "\nprivate fun ");
        String groupCard = section(cards, "private fun ToolGroupCard(", "\nprivate fun ");
        require(!toolRow.isEmpty(), CARDS + " 里找不到 ToolRow（这条断言要按函数体切片，找不到就失效了）");
        require(!groupCard.isEmpty(), CARDS + " 里找不到 ToolGroupCard");
        for (String arrow : new String[]{
                "chevronUp", "chevronDown", "ZhiIcons.collapse", "ZhiIcons.expand"}) {
            require(!squash(toolRow).contains(arrow),
                    "单条工具行里不许出现折叠箭头（" + arrow + "）："
                            + "整行点击已经是开关，箭头是用图标重复表达同一件事");
            require(!squash(groupCard).contains(arrow),
                    "工具组表头里不许出现折叠箭头（" + arrow + "）：同上");
        }
        require(!squash(cards).contains("rotate(90f)"),
                CARDS + " 的 `⋯` 不许再靠 rotate(90f) 凑横排："
                        + "`ZhiIcons.more` 本身就是横排三点");

        // ---- 6. 整个图标集里不许再出现"靠旋转凑形状" ---------------------------
        require(!squash(icons).contains("rotate(90f)") && !squash(icons).contains("rotate(-90f)"),
                ICONS + " 里出现了 rotate(±90f)：旋转能让形状看起来对，"
                        + "但旁边的小图形会跟着歪、且换个字形就失效。字形本身对了之后旋转就是噪音");

        // ---- 7. 调试页的图标画廊必须是全集 -----------------------------------
        //
        // 好不好看只能靠眼睛，而眼睛需要一处能一次看全的地方。
        LinkedHashSet<String> iconNames = declaredIconNames(icons);
        require(!iconNames.isEmpty(), ICONS + " 里没解析到任何 val xxx: Painter");
        List<String> notInGallery = new ArrayList<>();
        for (String name : iconNames) {
            if (!debugPage.contains("ZhiIcons." + name)) notInGallery.add(name);
        }
        require(notInGallery.isEmpty(),
                DEBUG_PAGE + " 的图标画廊漏了这些图标：" + String.join("、", notInGallery)
                        + "\n（画廊是「图标像不像该做的事」唯一的人工验收入口，必须覆盖全集）");

        System.out.println("IconSetTest PASS（Material Symbols 字形 " + declared.size()
                + " 个（全部有引用、全部有清单行） · 语义映射 " + pinned.length
                + " 条 · 画廊覆盖 " + iconNames.size() + " 个图标）");
    }

    private static int countOf(String source, String needle) {
        return source.split(Pattern.quote(needle), -1).length - 1;
    }
}
