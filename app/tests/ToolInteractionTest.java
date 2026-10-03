import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * 「工具操作菜单只作用在它自己那一行，且菜单里没有假动作」的守卫。
 *
 * <h2>钉住的三类退化</h2>
 *
 * <b>一、整组菜单冒充单行菜单</b>。`ToolGroupCard` 曾经把同一个 `onActions` 回调传给每一行，
 * 于是点某个工具的 `⋯` 弹出的是整组菜单，回调里也拿不到行号 —— 而"复制这条命令"
 * "展开这一条"这类动作全部作用在行上。现在要求回调带 toolId。
 *
 * <b>二、状态文字冒充菜单项</b>。TOOL_GROUP 那一档曾经是
 * `listOf("复制", if (completed) "已全部完成" else "执行中")` —— 第二项点下去什么都不发生，
 * 而用户会以为坏了。这里断言菜单文案里没有这类词。
 *
 * <b>三、决策表出现第二份实现</b>。"哪个状态该有哪些动作"只允许在 `ToolActions` 里算；
 * 界面若自己再判一次"有没有 diff ⇒ 显示哪个菜单"，两份迟早不一致。
 *
 * <b>四、菜单退化成居中对话框</b>。菜单曾经经过 `choicePicker` 中转，而
 * `ChoiceIntent.TOOL_ACTION` 不在 `ChoicePickerState.isActionMenu` 的名单里 ——
 * 于是点某一行的 `⋯`，弹出来的是**屏幕正中的对话框**。现在它必须是
 * Miuix 下拉菜单（`ZhiIconDropdownMenu`，与输入器底排同一组件）。
 *
 * <p>与 `ZcodeProtocolTest` 一样，这里做的是**文本级**检查，因此：
 * 注释里提到这些词也会被抓到 —— 那正是想要的（提都不该在别处以那种形式提）。
 */
public final class ToolInteractionTest {

    private static final String UI_MODELS =
            "app/src/main/java/com/zhizhu/zhicode/compose/model/UiModels.kt";
    private static final String TOOL_ACTIONS =
            "app/src/main/java/com/zhizhu/zhicode/compose/model/ToolActions.kt";
    private static final String VM =
            "app/src/main/java/com/zhizhu/zhicode/compose/state/WorkspaceViewModel.kt";
    private static final String CARDS =
            "app/src/main/java/com/zhizhu/zhicode/compose/ui/chat/MessageCards.kt";
    private static final String CHAT_LIST =
            "app/src/main/java/com/zhizhu/zhicode/compose/ui/chat/ChatList.kt";
    private static final String CHAT_AREA =
            "app/src/main/java/com/zhizhu/zhicode/compose/ui/ChatArea.kt";
    private static final String SESSION_READER =
            "app/src/main/java/com/zhizhu/zhicode/compose/data/SessionReader.kt";
    private static final String LIVE_OUTPUT =
            "app/src/main/java/com/zhizhu/zhicode/compose/model/LiveOutput.kt";
    private static final String ERROR_SUMMARY =
            "app/src/main/java/com/zhizhu/zhicode/compose/model/ErrorSummary.kt";
    private static final String CHAT_NAV =
            "app/src/main/java/com/zhizhu/zhicode/compose/model/ChatNavigation.kt";
    private static final String AUTO_FOLLOW =
            "app/src/main/java/com/zhizhu/zhicode/compose/ui/chat/AutoFollow.kt";
    private static final String MARKDOWN =
            "app/src/main/java/com/zhizhu/zhicode/compose/ui/Markdown.kt";
    private static final String THEME =
            "app/src/main/java/com/zhizhu/zhicode/compose/theme/ZhiTheme.kt";
    private static final String ZHI_ICONS =
            "app/src/main/java/com/zhizhu/zhicode/compose/ui/ZhiIcons.kt";
    private static final String ZHI_MATERIAL_ICONS =
            "app/src/main/java/com/zhizhu/zhicode/compose/ui/ZhiMaterialIcons.kt";
    private static final String MATERIAL_FETCH_TOOL = "tools/material-symbols-fetch.sh";
    private static final String FAST_SCRIPT = "test-jvm-fast.sh";

    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static String read(Path root, String relative) throws Exception {
        Path file = root.resolve(relative);
        require(Files.isRegularFile(file), "缺少文件: " + relative);
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    /** 去掉注释，但认得字符串字面量（正则版会被字符串里的 &#42;&#47; 骗到）。 */
    private static String stripComments(String text) {
        StringBuilder out = new StringBuilder(text.length());
        boolean inLine = false;
        boolean inBlock = false;
        boolean inString = false;
        boolean inChar = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            char next = i + 1 < text.length() ? text.charAt(i + 1) : '\0';
            if (inLine) {
                if (c == '\n') {
                    inLine = false;
                    out.append(c);
                }
                continue;
            }
            if (inBlock) {
                if (c == '*' && next == '/') {
                    inBlock = false;
                    i++;
                }
                continue;
            }
            if (inString) {
                out.append(c);
                if (c == '\\') {
                    if (i + 1 < text.length()) out.append(text.charAt(++i));
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (inChar) {
                out.append(c);
                if (c == '\\') {
                    if (i + 1 < text.length()) out.append(text.charAt(++i));
                } else if (c == '\'') {
                    inChar = false;
                }
                continue;
            }
            if (c == '/' && next == '/') {
                inLine = true;
                i++;
                continue;
            }
            if (c == '/' && next == '*') {
                inBlock = true;
                i++;
                continue;
            }
            if (c == '"') inString = true;
            if (c == '\'') inChar = true;
            out.append(c);
        }
        return out.toString();
    }

    private static String squash(String text) {
        return text.replaceAll("\\s+", "");
    }

    /**
     * 取 `ToolBatch` 里 `Segment.Single ->` 那一段（到 `Segment.Group ->` 为止）。
     *
     * 用来断言"单条工具的画法里不许有卡片" —— 这条只能在那个分支里查，
     * 整个文件里当然有 `Card(`（输出井自己要用）。
     */
    private static String singleBranch(String cards) {
        int at = cards.indexOf("Segment.Single ->");
        if (at < 0) return "";
        int end = cards.indexOf("Segment.Group ->", at);
        return end < 0 ? cards.substring(at) : cards.substring(at, end);
    }

    /** 取 [from] 到下一个顶层 `private fun` 之间的片段（找不到终点就吃到末尾）。 */
    private static String section(String source, String from, String to) {
        int at = source.indexOf(from);
        if (at < 0) return "";
        int end = source.indexOf(to, at + from.length());
        return end < 0 ? source.substring(at) : source.substring(at, end);
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int from = 0;
        while (true) {
            int at = text.indexOf(needle, from);
            if (at < 0) return count;
            count++;
            from = at + needle.length();
        }
    }

    public static void main(String[] args) throws Exception {
        Path root = Paths.get(args.length == 0 ? "." : args[0]).toAbsolutePath().normalize();

        String uiModels = stripComments(read(root, UI_MODELS));
        String toolActions = stripComments(read(root, TOOL_ACTIONS));
        String vm = stripComments(read(root, VM));
        String cards = stripComments(read(root, CARDS));
        String chatList = stripComments(read(root, CHAT_LIST));
        String chatArea = stripComments(read(root, CHAT_AREA));
        String reader = stripComments(read(root, SESSION_READER));
        String liveOutput = stripComments(read(root, LIVE_OUTPUT));
        String errorSummary = stripComments(read(root, ERROR_SUMMARY));
        String chatNav = stripComments(read(root, CHAT_NAV));
        String autoFollow = stripComments(read(root, AUTO_FOLLOW));
        String markdown = stripComments(read(root, MARKDOWN));
        String theme = stripComments(read(root, THEME));
        String models = stripComments(read(root, UI_MODELS));
        // 快回路脚本要在两处检查（1b/1d 与第 5 节），早读一次。
        String script0 = read(root, FAST_SCRIPT);

        // ---- 1. `⋯` 必须是 Miuix 下拉菜单触发器，且回调必须带 toolId ----
        //
        // 这一节钉的是两件事，它们曾经一起坏掉：
        //   a) 每一行的 `⋯` 用的是同一个"整组"回调 —— 点单行弹整组菜单，回调里也不知道
        //      用户点的是哪一行；
        //   b) 菜单经过 `choicePicker` 中转 —— 而 `ChoiceIntent.TOOL_ACTION` 不在
        //      `isActionMenu` 的名单里，于是**退化成屏幕中央的对话框**：一个只作用于
        //      某一行的动作，弹窗却出现在屏幕正中。
        // 现在菜单由那一行自己画（Miuix `OverlayIconDropdownMenu`，与输入器底排的
        // `+` / 权限 / 推理同一个组件），选中的文案随 (itemId, toolId) 一起回到 ViewModel。
        require(squash(cards).contains("ZhiIconDropdownMenu("),
                CARDS + " 的 ⋯ 必须是 Miuix 下拉菜单触发器（ZhiIconDropdownMenu）："
                        + "与输入器底排的 + / 权限 / 推理同一个组件。用普通 IconButton + "
                        + "外部状态就会重演「只作用于某一行的动作却弹出居中对话框」");
        require(squash(cards).contains("ToolActions.options(menuFlags)"),
                CARDS + " 的菜单项必须由 ToolActions.options 决定："
                        + "在界面里另拼一份，两份菜单迟早不一致");
        require(squash(cards).contains("ToolActions.flags("),
                CARDS + " 的菜单判据必须来自 ToolActions.flags —— 它是界面与 VM 共用的"
                        + "唯一一份「哪个状态该有哪个动作」映射");
        require(squash(cards).contains("ToolText.isFileDiff(toolName,output)"),
                CARDS + " 的 hasDiff 判据必须复用 ToolText.isFileDiff："
                        + "自己再判一次会出现「显示成 diff 却没有复制 Diff 这一项」");
        require(!squash(cards).contains("choicePicker"),
                CARDS + " 不许再碰 choicePicker：工具菜单不再经过选择器中转");
        require(squash(cards).contains("onToolAction:(String,String)->Unit"),
                CARDS + " 的 ToolGroupCard 必须接收 onToolAction: (String, String) -> Unit："
                        + "参数是**那一行**的 toolId 与菜单文案 —— 少了 toolId，回调里根本"
                        + "不知道用户点的是哪一行");
        require(squash(cards).contains("onToolAction={label->onToolAction(tool.id,label)}"),
                CARDS + " 必须把每一行自己的 id 传出去："
                        + "onToolAction = { label -> onToolAction(tool.id, label) }");
        require(!squash(cards).contains("onToolAction=onToolAction"),
                CARDS + " 不许把外层的回调直接透传给每一行：那会让所有行共用一组动作");
        // 每行必须 `key(tool.id)`：工具是边跑边追加的，按位置归属会让"打开的菜单 /
        // 展开的输出"串到新插入的那一行上。
        require(squash(cards).contains("key(tool.id){ToolRow("),
                CARDS + " 的每一行必须 `key(tool.id) { ToolRow(...) }`："
                        + "工具边跑边追加，按位置归属会让行内状态串行");

        // 从 ChatList 一路到 ChatArea 都要带上 toolId 与文案，断在中间任何一处都是"点了没反应"。
        require(squash(chatList).contains("onToolAction:(ChatItem,String,String)->Unit"),
                CHAT_LIST + " 必须声明 onToolAction: (ChatItem, String, String) -> Unit");
        require(squash(chatList).contains("onToolAction(item,toolId,label)"),
                CHAT_LIST + " 必须把 (item, toolId, label) 一起转发出去");
        require(squash(chatArea).contains(
                        "onToolAction={item,toolId,label->viewModel.applyToolAction(item.id,toolId,label)}"),
                CHAT_AREA + " 必须把 onToolAction 接到 viewModel.applyToolAction(itemId, toolId, label)");
        require(squash(chatArea).contains("applyToolAction(item.id,toolId,label)"),
                CHAT_AREA + " 必须把**消息 id**（不是整条 ChatItem）交给 ViewModel："
                        + "VM 只需要能在 transcript 里定位目标的键");

        // ---- 1b. 批次必须按"分段"渲染：单条工具是扁平行，成组才用卡片 ----
        //
        // 这一节钉的是那个观感差异：ZhiCode 曾经把**每个**批次套进一张
        // 「已运行 N 个工具」卡片，于是单独一条 Bash 也被包进带标题的大卡里；
        // 而参考实现（IQ Code）里单条工具就是一行（`addToolCard(item, destination)`），
        // 只有"连续的 read/search 且 ≥2"才折成一张卡片（`addCollapsedToolActivity`）。
        require(squash(cards).contains("funToolBatch("),
                CARDS + " 必须有 ToolBatch：批次按分段渲染");
        require(squash(chatList).contains("ChatKind.TOOL_GROUP->ToolBatch("),
                CHAT_LIST + " 的 TOOL_GROUP 必须交给 ToolBatch（不是整批一张卡片）");
        require(squash(cards).contains("ToolGrouping.group("),                CARDS + " 的分段必须来自 ToolGrouping.group："
                        + "在界面里另写一套候选集/断开规则，两份迟早不一致");
        require(squash(cards).contains("Segment.Single->"),
                CARDS + " 必须分别处理 Single 与 Group 两种段");
        // 单条工具是**透明的一行**（反编译版 `addToolCard` 整行没有任何背景），
        // 也不许有徽章：有底色的只有它下面那口**终端井**（输出 / diff / 实时输出）。
        // 曾经在这里套过一张 Card —— 那是"卡里装着一个井"，两层底、两层圆角，
        // 一屏全是框。用户的原话是「底色比其他地方要黑的就是我说的预览框」：
        // 框来自**内容**，不是来自给每条工具套壳。
        require(!squash(singleBranch(cards)).contains("Card("),
                CARDS + " 的 Single 分支不许出现 Card(：单条工具就是透明一行，"
                        + "有底色的只有它下面的终端井（见 terminalSurface）");
        require(!squash(singleBranch(cards)).contains("Badge("),
                CARDS + " 的 Single 分支里不许有 Badge：单条工具的卡不冒充整批的汇总");
        require(squash(singleBranch(cards)).contains("key(tool.id){")
                        && squash(singleBranch(cards)).contains("ToolRow("),
                CARDS + " 的 Single 分支必须 key(tool.id) 包住这一条工具："
                        + "工具是边跑边追加的，按位置归属会让行内状态串到别的行上");
        // 通用计数标题不许回来 —— 它回答"有几个"而不是"在干什么"。
        require(!squash(cards).contains("已运行"),
                CARDS + " 里不许再出现「已运行 N 个工具」："
                        + "组标题必须说明干了什么（见 ToolGrouping.label）");
        require(!squash(cards).contains("正在运行工具"),
                CARDS + " 里不许再出现「正在运行工具」这种通用标题");
        require(squash(cards).contains("ToolGrouping.label(") && squash(cards).contains("ToolGrouping.subtitle("),
                CARDS + " 的组标题与副行必须来自 ToolGrouping："
                        + "文案与计数口径只有那一处实现（有单测）");
        require(squash(cards).contains("ToolGrouping.isDone(") && squash(cards).contains("ToolGrouping.hasFailure("),
                CARDS + " 的组状态必须按**这一组**算（ToolGrouping.isDone/hasFailure）："
                        + "取整批的状态会让同批里已读完的那组一直显示「正在读取」");
        // 判据的构造与渲染用的必须是同一份映射。
        require(squash(cards).contains("privatefunToolActivity.toGroupingEntry()"),
                CARDS + " 必须有一处 ToolActivity → ToolGrouping.Entry 的映射："
                        + "hint/readRequests 要在登记工具时算好，而不是渲染时猜");
        // 展开态不许再借成员的 expanded（那个管的是"这条工具的输出展开"）。
        require(squash(uiModels).contains("valexpandedGroups:Set<String>=emptySet()"),
                UI_MODELS + " 的 ChatItem 必须有 expandedGroups："
                        + "整组展开与单行输出展开是两件事，共用一个标志位时"
                        + "点开一条工具的输出会连带把整组摊开");
        require(squash(vm).contains("funtoggleGroupExpanded(id:String,groupKey:String)"),
                VM + " 的 toggleGroupExpanded 必须收 groupKey："
                        + "分组由界面按 toolId 推导，VM 不该再算「哪几条属于哪一组」");
        // 只查签名是弱守卫：把函数体写成"永远置空集合"同样能编译、同样能通过上面那条。
        // 真正的契约是它必须**按 groupKey 增删**那个集合。
        require(squash(vm).contains("expandedGroups=if(groupKeyinopen)open-groupKeyelseopen+groupKey"),
                VM + " 的 toggleGroupExpanded 必须按 groupKey 在 expandedGroups 里增删："
                        + "写成固定值（例如恒置空集）会编译通过、但点表头永远展不开");
        require(!squash(vm).contains("groupLabel"),
                VM + " 里不许再有 groupLabel：批次级汇总标签已经没有渲染位置了");
        // ---- 1b2. diff 预览井：整条数据链必须接通 ----------------------------
        //
        // 真机上的症状：IQ Code 里「修改文件」下面有一块**更暗**的 diff 预览井，
        // ZhiCode 里从来没有 —— 而引擎其实把它算好了
        // （`tools/UnifiedDiff.create` → `okWithDiff` → `ZhiEngineController` 一路
        // `diff = result.diff` 传进来），只是在 ViewModel 那一层被丢掉：
        // `ToolActivity` 根本没有 diff 字段，渲染侧也就只认"输出文本本身像 diff"，
        // 而写文件类工具的输出是一句自述（"Wrote 505 bytes to …"）。
        //
        // 这条链有四个环节，任何一环掉了，症状都一样（井是空的），
        // 所以每一环都要单独钉住 —— 只在渲染侧断言会漏掉"数据没送到"。
        require(squash(models).contains("valdiff:String=\"\""),
                UI_MODELS + " 的 ToolActivity 必须有 diff 字段（默认空串）："
                        + "引擎算好的统一 diff 要有地方存，否则界面永远画不出那块井");
        require(squash(vm).contains("diff=diff,"),
                VM + " 的 onEngineToolResult 必须把入参 diff 落到工具上（diff = diff）："
                        + "参数一路传到这里却被丢掉，编译器不会提醒，井就永远是空的");
        require(squash(reader).contains("optString(\"diff\""),
                SESSION_READER + " 必须读 tool_diff 事件里的 \"diff\" 正文："
                        + "只读 additions/deletions 的话，恢复历史会话后井是空的");
        require(squash(reader).contains("diff=diff?.text?:tool.diff"),
                SESSION_READER + " 必须把 diff 正文写回工具（diff = diff?.text ?: tool.diff）");
        String expanded = section(cards, "ToolStatusRegion.EXPANDED ->", "ToolStatusRegion.QUIET");
        require(!expanded.isEmpty(), CARDS + " 里找不到 EXPANDED 分支");
        // 两块井都要在：diff 在前（参考实现 `colorDiff(item.diff)` 先画），
        // 输出在后。写文件类工具的自述（"Wrote N bytes to …"）也有信息，
        // 用 diff 替掉它是**减信息**。
        require(expanded.contains("DiffLines("),
                "EXPANDED 分支必须画 diff 井（DiffLines）：这是「底色更黑的那个预览框」");
        require(expanded.contains("OutputLines(activity.output)"),
                "EXPANDED 分支必须保留输出井（OutputLines(activity.output)）："
                        + "diff 与输出是并列的两块，不是二选一");
        require(squash(cards).contains("valhasDiff=activity.diff.isNotBlank()"),
                CARDS + " 必须显式算 hasDiff："
                        + "只靠 ToolText.isFileDiff(output) 判不出引擎单独给的 diff");
        require(squash(cards).contains("hasDetails=activity.output.isNotBlank()||hasDiff"),
                CARDS + " 的 hasDetails 必须把 diff 也算进去："
                        + "否则只有 diff、没有输出的工具连展开箭头都不显示（内容摸不到）");
        require(squash(cards).contains("isFileDiff=isFileDiff||hasDiff"),
                CARDS + " 交给 ToolActions.flags 的判据必须含 diff："
                        + "否则「复制 diff」在这类工具上不出现");

        // ---- 1b3. 折叠控件必须是箭头，且工具行不再显示箭头 ------------------
        //
        // 真机症状（用户：「这个框怪怪的」）：工具行右端的展开箭头渲染成一个小方框。
        // 根因是那一对指向了 Miuix 的 `ExpandMore` / `ExpandLess`
        // —— 那两个字形的路径是「左上 L 形框 + 中间圆点 + 右下 L 形框」，**不是箭头**
        // （见 `miuix-icons/.../extended/ExpandMore.kt` 的 PathNode）。
        //
        // 现在整集换成了 Material Symbols，这一对落在 `chevron_right` / `expand_more`
        // （上游没有 `chevron_down`，向下的箭头就叫 expand_more）。
        // 这里钉三件事：
        // 1. expand / collapse 走那两个字形；
        // 2. 不许再出现 Miuix 的 ExpandMore / ExpandLess；
        // 3. **工具行与工具组表头不再显示箭头**（用户：「⌃/⌄ 箭头可以去掉，
        //    因为点击内容可以快速收回或展开」）—— 整行本身就是开关。
        String icons = stripComments(read(root, ZHI_ICONS));
        String materialIcons = stripComments(read(root, ZHI_MATERIAL_ICONS));
        require(squash(icons).contains(
                        "valexpand:Painter@Composableget()=vector(ZhiMaterialIcons.ChevronRight)"),
                ZHI_ICONS + " 的 expand 必须是 ChevronRight："
                        + "Miuix 的 ExpandMore 是「L 形框 + 圆点」，渲染出来就是那个怪框");
        require(squash(icons).contains(
                        "valcollapse:Painter@Composableget()=vector(ZhiMaterialIcons.ExpandMore)"),
                ZHI_ICONS + " 的 collapse 必须是 ExpandMore（上游的向下箭头就叫这个名字，"
                        + "它本来就是 chevron 的形状，不是 Miuix 那个框）");
        require(!squash(icons).contains("set.ExpandMore") && !squash(icons).contains("set.ExpandLess"),
                ZHI_ICONS + " 不得再用 Miuix 的 ExpandMore / ExpandLess："
                        + "那两个字形不是箭头（用户看到的「怪怪的框」就是它们）");
        // 工具行 / 组表头里不许再出现任何折叠箭头。
        String cards2 = stripComments(read(root, CARDS));
        require(!squash(cards2).contains("chevronUp") && !squash(cards2).contains("chevronDown"),
                CARDS + " 里不许再有 chevronUp/chevronDown：工具行与组表头的展开/收起"
                        + "靠整行点击，不再用图标表达（用户明确要求去掉）");
        // 来源、许可与"怎么重新取一遍"必须写在文件里（查**原文**，注释会被 stripComments 删掉）。
        // 不写清楚，下一个人不知道这些路径数据是从哪来的，也没法核对抄对没有。
        String iconsRaw = read(root, ZHI_ICONS);
        String materialRaw = read(root, ZHI_MATERIAL_ICONS);
        require(iconsRaw.contains("Material Symbols") && iconsRaw.contains("Apache"),
                ZHI_ICONS + " 必须写明图标来源（Material Symbols，Apache-2.0）");
        require(materialRaw.contains("Material Symbols")
                        && materialRaw.contains("google/material-design-icons")
                        && materialRaw.contains("Apache")
                        && materialRaw.contains("rikkahub"),
                ZHI_MATERIAL_ICONS + " 必须写明**取证过程**：为什么是 Material Symbols"
                        + "（rikkahub 实际用的 HugeIcons / Lucide 都是线条，与「不是线条」冲突）、"
                        + "上游是谁、许可是什么。否则后来的人会以为这些路径是随手画的");
        require(Files.isRegularFile(root.resolve(MATERIAL_FETCH_TOOL)),
                MATERIAL_FETCH_TOOL + " 必须存在：" + ZHI_MATERIAL_ICONS
                        + " 里那份路径数据是从上游拷来的，" + MATERIAL_FETCH_TOOL
                        + " 是唯一能证明「它还是上游那份」的东西（也是换字形的唯一入口）");
        require(materialRaw.contains("tools/material-symbols-fetch.sh"),
                ZHI_MATERIAL_ICONS + " 必须指向取用脚本（tools/material-symbols-fetch.sh）："
                        + "否则没人知道该怎么重新取一遍、也没法核对数据有没有被手改过");

        // ---- 1e. 权限模式 / 推理档必须活过一次启动 --------------------------
        //
        // 真机症状（用户：「聊天框那边的权限在退出软件等一系列操作，就会恢复成原来的样子」）。
        //
        // 这两项是 `SessionConfig` 的字段、也真的会被 `store.save()` 写盘，
        // 但界面状态**从来不读回来**，于是形成一个固定回路：
        //   1. 底排选了「跳过权限」→ 只有 state 变了；
        //   2. 发送时 configure() 写盘（这一步是对的）；
        //   3. 重启 → state 又是默认的「每次询问」；
        //   4. 启动路径里的 syncActiveProfile() → configure() 立刻**落盘**，
        //      把界面那份默认值写回去，盘上的「跳过权限」被冲掉。
        //
        // 所以"读回来"这一步不只是为了显示，它决定盘上的值能不能活过一次启动；
        // 而它**必须早于任何 configure()**，否则读到的就是刚被冲掉的那份。
        require(squash(vm).contains("privatesuspendfunrestoreRuntimeChoices()"),
                VM + " 必须有 restoreRuntimeChoices()："
                        + "权限模式/推理档存在 SessionConfig 里，但界面从不读回来");
        require(squash(vm).contains("engineModeToUi(o.permissionMode)")
                        && squash(vm).contains("engineEffortToUi(o.effort)"),
                VM + " 的读回必须走 engineModeToUi / engineEffortToUi："
                        + "盘上存的是引擎字符串（acceptEdits / low），界面枚举自己解析一遍迟早分叉");
        require(squash(vm).contains("privatefunpersistRuntimeChoice(mode:PermissionMode?=null,effort:EffortLevel?=null)"),
                VM + " 必须有 persistRuntimeChoice()："
                        + "只靠发送前那次 configure() 落盘，用户「选完就退出」仍然会丢");
        require(squash(vm).contains("funsetPermissionMode(mode:PermissionMode){")
                        && squash(vm).contains("persistRuntimeChoice(mode=mode)"),
                VM + " setPermissionMode 必须落盘：输入器底排选完的那一次就是它的唯一入口");
        require(squash(vm).contains("funsetEffort(level:EffortLevel){")
                        && squash(vm).contains("persistRuntimeChoice(effort=level)"),
                VM + " setEffort 必须落盘");
        // 三条改动权限模式的路径必须都落盘，否则"启动时读回"会读出一个过期的值，
        // 反而把界面打回旧模式 —— 比不读回还糟。
        require(countOccurrences(squash(vm), "persistRuntimeChoice(") >= 5,
                VM + " 里 persistRuntimeChoice( 的调用点太少（现在 "
                        + countOccurrences(squash(vm), "persistRuntimeChoice(") + " 处）："
                        + "定义 1 处 + setPermissionMode + setEffort + 斜杠 /permissions + "
                        + "「总是允许」这四路都必须写盘");
        // 顺序：读回必须排在 syncActiveProfile（它会 configure() 并落盘）之前。
        int restore = squash(vm).indexOf("restoreRuntimeChoices()");
        int sync = squash(vm).indexOf("restoreUiSettings()");
        int profile = squash(vm).indexOf("syncActiveProfile()", sync);
        require(restore > 0 && sync > 0 && profile > 0 && restore > sync && restore < profile,
                VM + " 的 restoreRuntimeChoices() 必须排在 restoreUiSettings() 之后、"
                        + "syncActiveProfile() 之前：syncActiveProfile 会 configure() 并落盘，"
                        + "读得晚一步就只能读到刚被默认值冲掉的那份");

        // ---- 1c. 预览面板：一口**比页面更暗**的终端井 ------------------------        //
        // 用户的原话：「底色比其他地方要黑的就是我说的预览框」。
        // 反编译版的 `TERMINAL_BG` 三套调色板都遵守这条（#0A0B0C / #040914 / #F1EFE8），
        // 而 ZhiCode 之前用的是 `cardInnerSurface()` —— 深色下取 Miuix 的
        // `surfaceContainerHighest`，**比卡片亮一档**，方向恰好相反。
        require(squash(theme).contains("funterminalSurface()"),
                THEME + " 必须有 terminalSurface()：工具输出/diff/实时预览共用的一口井");
        // 井必须**比页面更暗**，而且这条要钉住具体的取值（只查"定义了函数"是弱守卫：
        // 把井改成比页面还亮的颜色同样能编译、同样通过上一条）。
        require(squash(theme).contains("TerminalDark=Color(0xFF0A0B0C)"),
                THEME + " 的深色井必须是 #0A0B0C（IQ Code 暖黑调色板的 TERMINAL_BG）："
                        + "页面是 #242424，井更暗才像\"程序吐出来的原始字节\"");
        require(squash(theme).contains("TerminalLight=Color(0xFFE7E5DE)"),
                THEME + " 的浅色井必须是 #E7E5DE：页面是 #EDEDED，方向同样是\"更暗\""
                        + "（IQ Code 亮色里 #F1EFE8 vs 页面 #F7F6F2 也是更暗）");
        // 三处必须都用它，而不是各写各的底：
        for (String call : new String[]{"terminalSurface()"}) {
            require(countOccurrences(squash(cards), call) >= 3,
                    CARDS + " 的输出/diff/运行中预览三处都必须画在 terminalSurface() 上"
                            + "（当前只出现 " + countOccurrences(squash(cards), call) + " 次）");
        }
        require(!squash(cards).contains("color=ZhiColors.cardInnerSurface()"),
                CARDS + " 的工具面板不许再用 cardInnerSurface()："
                        + "那是\"比卡片亮一档\"的卡片语义，方向与终端井相反");
        // 运行中的命令类必须是整块井：圆点 + 名称 + 右对齐计时 + 命令行 + 分隔线 + 实时输出。
        require(squash(cards).contains("funRunningPanel("),
                CARDS + " 必须把运行中的那块抽成 RunningPanel");
        String running = section(cards, "private fun RunningPanel(", "\nprivate fun ");
        require(!running.isEmpty(), CARDS + " 里找不到 RunningPanel");
        for (String piece : new String[]{
                "terminalSurface()", "ZhiHorizontalDivider(", "等待程序输出…", ".size(7.dp)",
        }) {
            require(running.contains(piece),
                    "RunningPanel 里缺少 " + piece + "："
                            + "反编译版的运行块是「圆点 + bash + 计时 → 命令行 → 分隔线 → 实时输出」");
        }
        require(squash(cards).contains("ZhiTextScale.Footnote")
                        || squash(cards).contains("ZhiTextScale.Micro"),
                CARDS + " 输出面板的字号必须走 ZhiTextScale 的档位（不要写新字面量）");

        // ---- 1d. 助手回合容器：透明，且只由 USER 断开 ------------------------
        require(squash(chatList).contains("TurnLayout.blocks("),
                CHAT_LIST + " 必须按 TurnLayout.blocks 分块渲染："
                        + "「用户发言断开容器、其余归进同一轮」这条规则不能在界面里另写一遍");
        // ⚠️ 对话流只允许**一套**渲染路径。
        //
        // 真机症状：打开会话后快速上滑，"用户消息才显示出来，而且是从下往上飞起来的"。
        // 原因是旧那套逐条 `items(state.transcript, key = { it.id })` 没删干净，
        // 与新的按块 `items(blocks)` 同时留着 —— 同一批 id 在同一个 LazyColumn 里
        // 出现两次（`Block.Standalone` 的 key 就是消息 id），于是：
        //   · 每条消息被画两遍，滚上去看到的那一份是"副本"；
        //   · 重复 key 会把项判成"新出现"，于是 `animateItem` 的出现动画
        //     在快速滚动中反复播放 —— 看起来就是"从下往上飞"。
        require(!squash(chatList).contains("items(state.transcript"),
                CHAT_LIST + " 里不许再有逐条渲染的 items(state.transcript)："
                        + "它与按块渲染并存时，同一批 id 会在一个 LazyColumn 里出现两次");
        int animates = countOccurrences(squash(chatList), "animateItem(");
        require(animates == 1,
                CHAT_LIST + " 里 animateItem( 只该出现 1 次（就是渲染对话的那处 items），"
                        + "现在有 " + animates + " 次 —— 多出来的那处说明还有第二套渲染路径");
        require(squash(chatList).contains("isTurnLayout.Block.Turn->"),
                CHAT_LIST + " 必须分别处理 Standalone 与 Turn");
        require(squash(chatList).contains("if(blockisTurnLayout.Block.Turn)"),
                CHAT_LIST + " 的回合间距必须只加在 Turn 上（用户消息在容器外）");
        // 容器必须**透明**：反编译版的 beginConversation 是 padding 0 + 无背景。
        require(!squash(chatList).contains("TurnContainer("),
                CHAT_LIST + " 里不该出现给回合套壳的容器组件："
                        + "反编译版的回合容器是透明 vbox（padding 0、无背景），"
                        + "画个框就把\"井\"的唯一性抢走了");
        require(squash(script0).contains("model/TurnLayout.kt") && squash(script0).contains("TurnLayoutTest"),
                FAST_SCRIPT + " 必须把 TurnLayout.kt 与 TurnLayoutTest 一起列进快回路");
        require(squash(script0).contains("model/ChatKind.kt"),
                FAST_SCRIPT + " 必须把 ChatKind.kt 列进 MAIN_KT_SOURCES："
                        + "TurnLayout 与 ToolGrouping 都要用它，少了它就编译不过");        require(!squash(uiModels).contains("valgroupLabel:String"),
                UI_MODELS + " 的 ChatItem 不许再有 groupLabel（死状态）");
        // 调试模式要求"把每一组都摊开"，所以它必须按 ToolGrouping 算出组的 key 集合
        // 塞进 expandedGroups —— 只写 `expandedGroups = emptySet()` 同样能编译，
        // 而表现是"调试模式下折叠组全是收起的"，正是那个模式要避免的。
        require(squash(chatList).contains("expandedGroups=ToolGrouping.group("),
                CHAT_LIST + " 的 fullyExpanded 必须按 ToolGrouping 算出组键集合塞进 expandedGroups："
                        + "调试模式的意义就是「没有藏起来的东西」");

        // ---- 2. ViewModel 侧：只有"执行"，不再有"弹菜单" ----
        require(squash(vm).contains("funapplyToolAction(itemId:String,toolId:String,label:String)"),
                VM + " 必须有 applyToolAction(itemId, toolId, label)："
                        + "目标与文案一起从界面传进来，一进来就能执行");
        require(!squash(vm).contains("pendingToolAction"),
                VM + " 不许再有 pendingToolAction：它存在的唯一理由是"
                        + "「菜单经过选择器中转、回调里只剩文案」，那条路已经删掉了");
        require(!squash(vm).contains("TOOL_ACTION"),
                VM + " 不许再出现 ChoiceIntent.TOOL_ACTION："
                        + "工具菜单不再经过 choicePicker");
        require(!squash(uiModels).contains("TOOL_ACTION,"),
                UI_MODELS + " 的 ChoiceIntent 不许再有 TOOL_ACTION："
                        + "`isActionMenu` 只认 MESSAGE_ACTION / SESSION_ACTION，"
                        + "用 TOOL_ACTION 的后果就是工具菜单退化成居中对话框");
        require(!squash(chatArea).contains("showToolActions"),
                CHAT_AREA + " 不许再引用 showToolActions（那个入口已经不存在）");
        require(!squash(vm).contains("ToolActionFlags("),
                VM + " 不许自己构造 ToolActionFlags：判据的构造只有 ToolActions.flags 一份");

        // ---- 3. 菜单里不许出现状态文字冒充动作 ----
        // 只查 TOOL_GROUP 那一档附近的文本：banned 词在别处（比如"执行中"用于状态行）是合理的。
        int groupAt = vm.indexOf("else -> listOf(");
        require(groupAt >= 0, VM + " 找不到 TOOL_GROUP 的选项分支（结构被改过了？）");
        int groupEnd = vm.indexOf("\n", groupAt);
        String groupOptions = groupAt + 1 < groupEnd ? vm.substring(groupAt, groupEnd) : vm.substring(groupAt);
        for (String banned : new String[]{"已全部完成", "执行中"}) {
            require(!groupOptions.contains(banned),
                    VM + " 的工具组菜单里不许出现状态文字 \"" + banned + "\"："
                            + "那不是动作，点下去什么都不发生，而用户会以为坏了。"
                            + "单个工具的动作走 showToolActions");
        }

        // 决策表只允许一处实现：这些文案是契约，散落就说明有第二份。
        require(squash(toolActions).contains("constvalCOPY_COMMAND=\"复制命令\""),
                TOOL_ACTIONS + " 的菜单文案必须是具名常量（契约）：VM 按它们分派动作");
        int labelDefs = countOccurrences(squash(vm), "\"复制命令\"");
        require(labelDefs == 0,
                VM + " 里不许再出现菜单文案字面量（\"复制命令\" 出现 " + labelDefs + " 处）："
                        + "文案只有 ToolActions 一处定义，VM 要用就用它的常量");

        // ---- 4. 耗时：必须有起点（单调时钟），且界面侧要续走 ----
        require(squash(uiModels).contains("valstartedAtMs:Long=0L"),
                UI_MODELS + " 的 ToolActivity 必须有 startedAtMs："
                        + "引擎的 elapsedMs 只在有输出时更新，没有起点就无法在界面侧续走");
        require(squash(vm).contains("startedAtMs=SystemClock.elapsedRealtime()"),
                VM + " 登记工具时必须用**单调时钟**记下 startedAtMs"
                        + "（SystemClock.elapsedRealtime()）：用墙上时钟的话，"
                        + "系统对时或用户改时间会让秒表跳一大步甚至变负");
        require(!squash(vm).contains("startedAtMs=System.currentTimeMillis()"),
                VM + " 不许用 currentTimeMillis 当耗时基准：它会被拨动");
        require(squash(cards).contains("SystemClock.elapsedRealtime()"),
                CARDS + " 的界面时钟必须与 startedAtMs 同一个时钟（elapsedRealtime）："
                        + "混用两种时钟，「现在 - 起点」就没有意义");
        require(squash(cards).contains("ToolText.formatElapsed("),
                CARDS + " 的耗时文案必须走 ToolText.formatElapsed（mm:ss，与原版一致）："
                        + "曾经这里有个私有的 \"7.0s\" 版本，两版截图对不上");
        require(!squash(cards).contains("privatefunformatElapsed("),
                CARDS + " 不许再留第二份 formatElapsed 实现");
        require(squash(cards).contains("ToolActions.displayElapsedMs("),
                CARDS + " 的耗时取值必须走 ToolActions.displayElapsedMs："
                        + "取值规则（取较大者、不许倒退）只有那一处实现");
        require(squash(cards).contains("rememberRunningClock("),
                CARDS + " 必须有界面侧的运行时钟：光靠引擎推的值会让长时间不吐字的命令冻住");
        // 没有运行中的工具时不许继续排队 —— 常驻定时器会一直触发重组。
        require(squash(cards).contains("item.tools.any{!it.completed}"),
                CARDS + " 的运行时钟必须以「还有未完成的工具」为条件");

        // ---- 4b. 运行中就看得见输出（对齐参考实现 addToolCard 的运行分支）----
        //
        // 只显示"运行中 · 00:12"是不够的：一条跑两分钟都不吐字的命令与一条正在刷日志的
        // 命令在界面上长得一模一样，用户没法判断它在干什么。
        require(squash(cards).contains("LiveOutput.preview(") && squash(cards).contains("LiveOutput.tail("),
                CARDS + " 运行中的行内必须摊出实时输出：命令类按行取（LiveOutput.preview）、"
                        + "其他工具按尾部字符取（LiveOutput.tail）");
        require(squash(cards).contains("\"等待程序输出…\""),
                CARDS + " 命令类工具还没有输出时必须显示「等待程序输出…」："
                        + "留一片空白会被当成卡死");
        require(squash(cards).contains("LiveOutput.volume("),
                CARDS + " 运行标签必须报出输出体量（LiveOutput.volume）："
                        + "字符数是单调递增的，屏幕那几行没变时它也在动");

        // 运行标签的文案与"两个计数"缺一不可。
        require(squash(vm).contains("stdoutChars=buffer.stdoutChars")
                        && squash(vm).contains("stderrChars=buffer.stderrChars"),
                VM + " 必须把 stdout/stderr 两个计数落进 state："
                        + "运行标签要显示「错误输出 0 B」，只看 output 总长做不到");
        require(squash(uiModels).contains("valstdoutChars:Int=0")
                        && squash(uiModels).contains("valstderrChars:Int=0"),
                UI_MODELS + " 的 ToolActivity 必须有 stdoutChars / stderrChars");
        // 标记只在流切换时插一次 —— 判据在纯逻辑层，所以这里查它有没有被绕过。
        require(squash(vm).contains("LiveOutput.append("),
                VM + " 的实时缓冲必须走 LiveOutput.append："
                        + "它负责换行归一化与「[stderr] 只在切换时插一次」");
        require(!squash(vm).contains("buffer.append(\"[stderr]\\n\")"),
                VM + " 不许再每个 stderr chunk 都插一行 [stderr]："
                        + "连续报错的命令会把输出刷成一片标记");

        // ---- 4c. 展开态要看得见完整命令 ----
        require(squash(uiModels).contains("valcommand:String=\"\""),
                UI_MODELS + " 的 ToolActivity 必须有 command（原始命令行）");
        require(squash(cards).contains("activity.expanded&&activity.command.isNotBlank()"),
                CARDS + " 展开态必须显示**完整**命令：折叠摘要被 truncateCommand + "
                        + "shorten(190) 截过，展开还看它等于没展开");
        require(squash(reader).contains("command=input?.optString(\"command\",\"\")"),
                SESSION_READER + " 恢复历史时必须读回命令行："
                        + "不读的话展开一条旧 Bash 只能看到被截短的摘要");

        // ---- 4d. 恢复历史时给"没有结果的工具"收口 ----
        //
        // 会话记录里可能留着只有调用、没有结果的事件（进程被杀、写入中断、旧格式）。
        // 不收口的话，恢复出来的那条工具会**永远**转圈显示「运行中…」——
        // 一条几天前的记录里挂着一个"正在运行"，看着就像卡死。
        require(squash(reader).contains("\"会话记录未包含该工具的结果。\""),
                SESSION_READER + " 恢复历史时必须给没有结果的工具补一句说明"
                        + "（「会话记录未包含该工具的结果。」）："
                        + "否则界面上会永远挂着一个转圈的「运行中…」");
        require(squash(reader).contains("failed=true"),
                SESSION_READER + " 恢复历史时那些工具必须标成失败："
                        + "它们的状态是「记录里没有」，不是「还在跑」");

        // ---- 4e. 错误摘要要挑"有用"的那一行 ----
        require(squash(cards).contains("ErrorSummary.firstUsefulLine("),
                CARDS + " 的错误摘要必须转发到 ErrorSummary.firstUsefulLine："
                        + "「哪一行才是有用信息」是纯逻辑（有单测），"
                        + "取第一条非空行会得到 'Reading package lists...' 这种没用的摘要");
        // 关键词表只允许有一处。
        require(squash(errorSummary).contains("\"permissiondenied\"")
                        && squash(errorSummary).contains("\"unableto\"")
                        && squash(errorSummary).contains("\"notfound\""),
                ERROR_SUMMARY + " 的关键词表必须包含 permission denied / unable to / not found："
                        + "这三类正好覆盖权限、依赖与路径问题");
        require(!squash(cards).contains("\"permissiondenied\""),
                CARDS + " 里不许再出现错误关键词：关键词表只有 ErrorSummary 一处");

        // ---- 6. 对话流的对位行为（同一批参考实现里"值得移植"的几条）----
        //
        // 6a. 发消息后无条件吸底：用户上翻历史时发出的消息不能落在屏幕外。
        require(squash(uiModels).contains("valscrollToBottomToken:Long=0L"),
                UI_MODELS + " 必须有 scrollToBottomToken："
                        + "「暂停跟随」住在 ChatList 自己的 remember 里，"
                        + "界面外部只有靠这个令牌才能让它恢复");
        require(squash(vm).contains("scrollToBottomToken=it.scrollToBottomToken+1")
                        && squash(vm).contains("scrollToBottomToken=s.scrollToBottomToken+1"),
                VM + " 两条发送路径（普通发送 / 预输入）都必须递增 scrollToBottomToken");
        require(squash(chatList).contains("rememberAutoFollow(listState,forceFollowToken=state.scrollToBottomToken)"),
                CHAT_LIST + " 必须把令牌交给 rememberAutoFollow："
                        + "只递增而没人用，等于没做");
        require(squash(autoFollow).contains("if(forceFollowToken!=lastForceToken)")
                        && squash(autoFollow).contains("lastForceToken=forceFollowToken")
                        && squash(autoFollow).contains("autoFollow.value=true"),
                "AutoFollow.kt 必须把令牌翻译成「恢复跟随」，"
                        + "而且判据是令牌**变了**而不是「非零」："
                        + "LaunchedEffect 每次重新进入组合都会重跑，切回页签时令牌早就 > 0，"
                        + "按「非零」判会把用户刚翻到的位置又拽回底部");

        // 6a2. 切页签不许把人拽回底部 ----------------------------------------
        //
        // 真机症状（用户：「切换页面，对话老是回到最低端」）：对话/终端切走再切回时，
        // 面板被 `AnimatedContent` 销毁重建，三个"回到底部"的入口会同时重跑：
        //   1. `autoFollow` 若是普通 `remember`，重建后又变回 true；
        //   2. 令牌 effect 若只判"非零"，重建时又滚一次；
        //   3. 吸底 snapshotFlow 的首帧发射 + (1) 的 true 一起，立刻拽到底。
        // 三条都改掉，缺一条症状依旧。
        require(squash(autoFollow).contains("rememberSaveable("),
                "AutoFollow.kt 的 autoFollow 必须用 rememberSaveable："
                        + "普通 remember 会在面板被销毁重建时重置回 true，切回页签立刻拽到底");
        require(squash(autoFollow).contains("Saver<MutableState<Boolean>,Boolean>"),
                "autoFollow 的 rememberSaveable 必须给一个明确的 Saver（存 Boolean 本身）："
                        + "MutableState 不是 Bundle 能直接存的类型，缺 Saver 会在保存时抛异常");
        require(squash(chatList).contains("if(state.scrollToBottomToken==lastScrollToken)return@LaunchedEffect"),
                CHAT_LIST + " 的 scrollToBottomToken effect 必须只对**变化**反应"
                        + "（记下 lastScrollToken 并比对）："
                        + "否则切回页签时它重启一次就又滚到底了");
        require(squash(chatList).contains("varlastScrollTokenbyremember{mutableStateOf(state.scrollToBottomToken)}"),
                CHAT_LIST + " 必须记住已处理的令牌（lastScrollToken），初始值取当前令牌："
                        + "首次组合不该误触发滚动");

        // 6b. 「重试上一问」必须从被点的那条往前找。
        //
        // 只说"签名里有 from"是不够的（那样把函数体里的定位换回"取最后一条"也能通过）。
        // 真正的判据是：定位**只有一处实现**且在那个纯逻辑文件里，VM 只做翻译与调用。
        require(squash(vm).contains("privatefunretryLastUserPrompt(from:ChatItem?)"),
                VM + " retryLastUserPrompt 必须接收被点的那条消息："
                        + "取全对话最后一条用户消息时，对旧回复点「重试」会重发**最新**那个问题");
        require(squash(vm).contains("ChatNavigation.previousUserPrompt("),
                VM + " 的定位必须走 ChatNavigation.previousUserPrompt："
                        + "在 VM 里就地写循环的话，「点 A 发了 B」这个 bug 只能靠肉眼发现");
        require(squash(chatNav).contains("downTo0"),
                CHAT_NAV + " 必须真的从锚点往前遍历（downTo）："
                        + "写成从后往前取第一条就退回了旧行为");
        require(!squash(vm).contains("transcript.lastOrNull{it.kind==ChatKind.USER"),
                VM + " 不许再出现「取最后一条用户消息」的写法");
        require(squash(vm).contains("->retryLastUserPrompt(target)"),
                VM + " 必须把消息操作的目标交给 retryLastUserPrompt");

        // 6c. 代码块要能单独复制。
        require(squash(markdown).contains("Clipboard.copy("),
                MARKDOWN + " 的代码块必须有「复制」："
                        + "在这之前唯一的复制途径是长按整条消息，那会把整篇回答一起复制走");
        require(squash(markdown).contains("\"已复制\"") && squash(markdown).contains("CopyFeedbackMs"),
                MARKDOWN + " 复制后必须给出「已复制」反馈并在约 520ms 后复原"
                        + "（对齐参考实现 MarkdownRenderer.postDelayed(..., 520)）");

        // ---- 5. 秒级回路 ----
        String script = read(root, FAST_SCRIPT);
        require(script.contains("model/ToolActions.kt"),
                FAST_SCRIPT + " 必须把 ToolActions.kt 列进 MAIN_KT_SOURCES："
                        + "决策表要能在秒级回路里跑");
        require(script.contains("ToolActionsTest"),
                FAST_SCRIPT + " 必须把 ToolActionsTest 列进默认测试");
        require(script.contains("model/ToolKind.kt"),
                FAST_SCRIPT + " 必须把 ToolKind.kt 列进 MAIN_KT_SOURCES："
                        + "ToolActions.flags 按它判「是不是命令类」，少了它就编译不过");
        require(script.contains("model/LiveOutput.kt"),
                FAST_SCRIPT + " 必须把 LiveOutput.kt 列进 MAIN_KT_SOURCES："
                        + "实时输出的累积规则要能在秒级回路里跑");
        require(script.contains("LiveOutputTest"),
                FAST_SCRIPT + " 必须把 LiveOutputTest 列进默认测试");
        require(script.contains("model/ErrorSummary.kt") && script.contains("ErrorSummaryTest"),
                FAST_SCRIPT + " 必须把 ErrorSummary.kt 与 ErrorSummaryTest 一起列进快回路："
                        + "「哪一行才是有用信息」这类判据只有逐条断言才靠得住");
        require(script.contains("model/ChatNavigation.kt") && script.contains("ChatNavigationTest"),
                FAST_SCRIPT + " 必须把 ChatNavigation.kt 与 ChatNavigationTest 一起列进快回路："
                        + "「点 A 发了 B」只能靠行为测试发现");
        require(!toolActions.contains("import android."),
                TOOL_ACTIONS + " 不许 import android.*：它是纯逻辑，"
                        + "带上 Android 就只能靠分钟级的 Gradle 单测跑");
        require(!liveOutput.contains("import android."),
                LIVE_OUTPUT + " 不许 import android.*：实时输出的累积/裁剪是纯字符串运算，"
                        + "放在纯逻辑层才能在秒级回路里逐条钉住（见 LiveOutputTest）");
        require(!errorSummary.contains("import android."),
                ERROR_SUMMARY + " 不许 import android.*：错误摘要的挑行规则是纯字符串运算");
        require(!chatNav.contains("import android."),
                CHAT_NAV + " 不许 import android.*：定位是纯逻辑，"
                        + "带上 Android 就只能靠分钟级的 Gradle 单测跑");
    }
}
