import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;

/**
 * 两处「写死的几何」的守卫：长按触发方式、以及弹窗/气泡宽度。
 *
 * <p>它们都属于「改坏了不会编译失败、只会看起来不对或点起来不对」的那一类：
 *
 * <ol>
 *   <li><b>触发方式</b>。用户气泡曾经写作 {@code onClick = onLongPress}，
 *       于是<b>短按</b>就弹出操作菜单 —— 而同一个函数的文档与 {@code AssistantCard}
 *       都是「长按」。Miuix {@code Card} 用 {@code combinedClickable} 且
 *       {@code isClickable = hasOnClick || hasLongPress}（核过 v0.9.4 源码），
 *       所以只给 {@code onLongPress} 是被正确支持的，不需要拿 onClick 顶替。</li>
 *   <li><b>宽度</b>。弹窗曾经有四个互不相同、也无规则的 {@code maxWidth}：
 *       560 / 620 / 640 / 860，表现是「太窄」与「太宽」并存；用户气泡则用
 *       {@code fillMaxWidth(0.86f)} <b>强制</b>占 86%，于是内容只有「1」这种
 *       短消息也被撑成一条几乎整行宽的色带。</li>
 * </ol>
 */
public final class LayoutConsistencyTest {

    private static final String SRC = "app/src/main/java/com/zhizhu/zhicode/compose/";
    private static final String MESSAGE_CARDS = SRC + "ui/chat/MessageCards.kt";
    private static final String DIALOG_SHELL = SRC + "ui/dialogs/DialogShell.kt";

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static String read(String root, String relative) throws Exception {
        return new String(Files.readAllBytes(Paths.get(root, relative)),
                java.nio.charset.StandardCharsets.UTF_8);
    }

    /** 去掉注释：否则「代码删了、注释里还写着」会让断言静默通过。 */
    private static String stripComments(String text) {
        String noBlock = text.replaceAll("(?s)/\\*.*?\\*/", " ");
        return noBlock.replaceAll("(?m)//[^\\n]*", " ");
    }

    private static List<String> kotlinSources(String root, String dir) throws Exception {
        try (Stream<Path> walk = Files.walk(Paths.get(root, dir))) {
            return walk.filter(p -> p.toString().endsWith(".kt"))
                    .map(p -> Paths.get(root).relativize(p).toString())
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    private static int lineOf(String text, int offset) {
        int line = 1;
        for (int i = 0; i < offset; i++) if (text.charAt(i) == '\n') line++;
        return line;
    }

    /** 取某个 composable 函数体的片段（从 `fun 名字(` 到下一个顶层 `}`）。 */
    private static String bodyOf(String code, String funSignature) {
        int at = code.indexOf(funSignature);
        if (at < 0) return "";
        int depth = 0;
        boolean seenBrace = false;
        for (int i = at; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '{') { depth++; seenBrace = true; }
            else if (c == '}') {
                depth--;
                if (seenBrace && depth == 0) return code.substring(at, i + 1);
            }
        }
        return code.substring(at);
    }

    public static void main(String[] args) throws Exception {
        String root = args.length > 0 ? args[0] : ".";

        // ---- 1. 用户气泡必须「长按」触发 -------------------------------------
        String cards = stripComments(read(root, MESSAGE_CARDS));
        String bubble = bodyOf(cards, "fun UserBubble(");
        require(!bubble.isEmpty(), MESSAGE_CARDS + " 里找不到 UserBubble");
        require(bubble.contains("onLongPress = onLongPress"),
                "UserBubble 必须用 onLongPress = onLongPress 触发操作菜单。"
                        + "曾经写作 onClick = onLongPress，表现为短按就弹菜单，"
                        + "与函数文档和 AssistantCard 都不一致。");
        require(!bubble.contains("onClick"),
                "UserBubble 里不应出现 onClick：这张卡只有长按才有动作，"
                        + "给它加 onClick 会让短按也触发（而按压反馈已按 AssistantCard"
                        + " 的同样理由设为 None，短按本就该什么都不发生）。");

        // 助手卡同样是长按，一起钉住（两张卡的触发方式必须一致）
        String assistant = bodyOf(cards, "fun AssistantCard(");
        require(assistant.contains("onLongPress = onLongPress"),
                "AssistantCard 必须保持 onLongPress（与 UserBubble 一致）");

        // ---- 2. 气泡宽度必须是「上限」而不是「强制比例」 ----------------------
        require(!cards.contains("fillMaxWidth(0."),
                MESSAGE_CARDS + " 里不应再用 fillMaxWidth(比例)：那会**强制**占位，"
                        + "短消息也会被撑成整行宽的色带。应当用 widthIn(max = 可用宽 × 比例)。");
        require(bubble.contains("widthIn(max = maxBubbleWidth)"),
                "UserBubble 必须用 widthIn(max = …) 限制宽度上限（内容自适应）");
        require(cards.contains("BoxWithConstraints"),
                "UserBubble 需要可用宽度才能把上限表达成比例，"
                        + "所以要用 BoxWithConstraints 取 maxWidth");

        // ---- 2b. 流式文字的切换必须有过渡，不许只让卡片自己动 ----------------
        //
        // 用户原话："有些文本没有动画，只有card有"。当时卡片高度有 animateContentSize，
        // 但思考面板展开/收起、工具行状态区（运行中→摘要→输出）、工具组标题
        // 的文字都是瞬间蹦出来/消失的，流式光标也是一块静止的字符。
        require(cards.split(Pattern.quote("Crossfade(")).length - 1 >= 3,
                "思考面板 / 工具行状态区 / 工具组标题的文字切换必须走 Crossfade 淡变"
                        + "（至少 3 处）：只让卡片高度动、文字瞬间跳变是被点名的观感问题。"
                        + "淡变时长必须取 ZhiMotion 的令牌，不要写裸 tween。");
        require(cards.contains("rememberInfiniteTransition("),
                "流式光标必须闪烁（rememberInfiniteTransition + graphicsLayer），"
                        + "静止的 ▍ 看不出流式还在进行");
        require(cards.contains("AnimatedVisibility(") && cards.contains("ContextFooter("),
                "上下文页脚必须用 AnimatedVisibility 淡入，"
                        + "不许在流式结束时瞬间出现");

        // ---- 3. 弹窗宽度必须取自 ZhiDialogWidth ------------------------------
        String shell = stripComments(read(root, DIALOG_SHELL));
        require(shell.contains("object ZhiDialogWidth"),
                DIALOG_SHELL + " 必须定义 ZhiDialogWidth 三档宽度");
        for (String tier : new String[]{"Compact", "Regular", "Wide"}) {
            require(shell.contains("val " + tier + " ="),
                    "ZhiDialogWidth 必须提供 " + tier + " 档");
        }

        List<String> offenders = new ArrayList<>();
        for (String file : kotlinSources(root, SRC)) {
            String text = stripComments(read(root, file));
            Matcher m = Pattern.compile("maxWidth\\s*=\\s*[0-9]+(\\.\\d+)?\\s*\\.dp").matcher(text);
            while (m.find()) {
                offenders.add(file + ":" + lineOf(text, m.start()) + "  " + m.group());
            }
        }
        require(offenders.isEmpty(),
                "弹窗宽度必须取自 ZhiDialogWidth 的档位，不要写新的字面量：\n"
                        + "  收敛之前有 560/620/640/860 四个无规则的值，"
                        + "表现是「太窄」与「太宽」并存。\n  "
                        + String.join("\n  ", offenders));

        // 弹窗边距不允许写字面量；BottomSheet 横向零边距是为了扩大可用宽度的有意例外。
        // （注意断言的是「不得出现 outsideMargin = DpSize(…) 这种字面量」，
        //  而不是「用到常量的文件数量」—— 后者在任何一处漏改时反而会通过。）
        List<String> marginLiteralOffenders = new ArrayList<>();
        int tokenUsers = 0;
        for (String file : kotlinSources(root, SRC)) {
            String text = stripComments(read(root, file));
            if (text.contains("outsideMargin = DialogWideOutsideMargin") || text.contains("outsideMargin = DialogSheetOutsideMargin")) tokenUsers++;
            Matcher m = Pattern.compile("outsideMargin\\s*=\\s*DpSize\\s*\\(").matcher(text.replace("outsideMargin = DpSize(0.dp, 0.dp)", "outsideMargin = DialogWideOutsideMargin").replace("DialogSheetOutsideMargin = DpSize", "DialogSheetOutsideMargin ="));
            while (m.find()) {
                marginLiteralOffenders.add(file + ":" + lineOf(text, m.start()));
            }
        }
        require(marginLiteralOffenders.isEmpty(),
                "弹窗外边距必须统一走 DialogWideOutsideMargin，不要写字面量 DpSize：\n  "
                        + String.join("\n  ", marginLiteralOffenders));
        // 设置及其二级页（API/MCP/技能/角色卡/记忆）已整页化（SettingsSubPage），
        // 任务清单 / 附件搜索也已迁到 OverlayBottomSheet（sheet 不收 outsideMargin），
        // 常量的自然用户随之减少 —— 阈值只保证「剩余弹窗没绕过」。
        require(tokenUsers >= 4,
                "应当有多个弹窗使用 DialogWideOutsideMargin（现在只有 " + tokenUsers
                        + " 处）——过少说明有人绕过了它");

        // ---- 4. 输入法弹出时，底部悬浮层必须自己让位 -------------------------
        //
        // 真机症状（用户：「打开输入法，但是输入框没弹起来」）：键盘盖住了输入框。
        //
        // 根因不是清单少了 adjustResize（那里有），而是 `MainActivity.onCreate` 调了
        // `enableEdgeToEdge()` = `setDecorFitsSystemWindows(false)`：DecorView 不再消费
        // 系统窗口 inset，系统那套"把窗口缩小让出键盘"随之失效，IME 的高度只能应用自己接。
        // 参考实现（反编译版 `installKeyboardMotion()`）同样是自己挂
        // `setOnApplyWindowInsetsListener` + `WindowInsetsAnimation.Callback` 顶起根布局。
        //
        // 这里钉两件事：
        //   · 悬浮层必须按 IME 让位（否则输入框永远在键盘底下）；
        //   · **不能**用 `Modifier.imePadding()` 图省事 —— Scaffold 已经在内容容器底部
        //     让过导航栏，再叠一次 IME 高度会多出"一条导航栏"的空隙。所以要是差值。
        String chatArea = stripComments(read(root, SRC + "ui/ChatArea.kt"));
        // ⚠️ 让位的**形式**后来从 `padding(bottom = imeLift)` 换成了 `offset { IntOffset(0, -lift) }`，
        //    但"必须让位"没变，而且换成 offset 是**必须**的、不是风格选择：
        //    `padding` 会把让给键盘的那一段算进**本节点的尺寸**，于是下面那个
        //    `onSizeChanged` 报出来的高度含 IME；而 IME 在键盘动画期间**每帧都变**，
        //    顺着 `bottomInset` 一路把 ChatList 也拖成每帧重组（实测 ChatArea=69/s）。
        //    探针数字见 DebugHudStructureTest §33。
        require(chatArea.contains(".offset {") && chatArea.contains("IntOffset(0, -lift)"),
                "悬浮层（任务卡 + 反馈条 + 输入器）必须按 IME 让位（offset { IntOffset(0, -lift) }）："
                        + "edge-to-edge 之后系统不再替应用缩小窗口，不让位就是输入框被键盘盖住。"
                        + "且只能用 offset：用 padding 会让这段高度进节点尺寸，"
                        + "把 bottomInset 变成逐帧变化");
        String imeMotion = read(root, SRC + "ui/ImeMotion.kt");
        require(imeMotion.contains("WindowInsetsCompat.Type.ime()")
                        && imeMotion.contains("WindowInsetsCompat.Type.navigationBars()"),
                "让位量必须由 Activity 级 IME/导航栏 inset 算出：凭空给常量在不同机型上会错");
        require(!chatArea.contains("imePadding()"),
                "不要改用 Modifier.imePadding()：它垫的是**整个** IME 高度（含导航栏那段），"
                        + "与 Scaffold 已让出的导航栏叠加后，输入框会悬空一条缝");
        // 顺序仍然要紧，但含义反过来了：onSizeChanged 必须在让位**之外**（offset 的右边），
        // 量到的是**内容高度**；让位量自己走偏移、不参与尺寸，所以尺寸是稳的。
        int sizeChanged = chatArea.indexOf(".onSizeChanged { floatingContentHeightPx = it.height }");
        int lift = chatArea.indexOf(".offset {");
        require(sizeChanged > 0 && lift > 0 && sizeChanged > lift,
                "onSizeChanged 必须写在 offset { } **右边**并回报**内容**高度："
                        + "放到左边量到的是含 IME 的合并高度 —— 那正是每帧重组的源头");
        // 列表要留白的键盘高度不再来自 onSizeChanged（它只剩内容高度了），
        // 而是那份"停稳"的 IME 值。少了它，被键盘挡住的内容滑不上来（原始 bug 回归）。
        require(chatArea.contains("((floatingContentHeightPx + settledImeLiftPx).toDp()"),
                "bottomInset 必须同时含内容高度与**停稳后**的 IME 高度："
                        + "少了 settledImeLiftPx，被键盘挡住的内容就滑不上来（原始 bug 回归）；"
                        + "直接塞逐帧的 IME 值，则又是每帧重组");

        // ---- 4b. 画在 Scaffold 之上的浮层，要自己让出系统栏 -------------------
        //
        // 真机症状（截图）：侧栏抽屉的第一行「新会话」整个压在状态栏药丸底下，
        // 既点不到也看不清。
        //
        // 根因是**两个默认值刚好都没兜住**：主界面是 edge-to-edge（DecorView 不再消费
        // 系统窗口 inset），而侧栏是画在 `Scaffold` **之上**的浮层 —— 它不吃 Scaffold
        // 给内容区的 `padding`，自己也没有任何 inset 修饰符，于是内容从 y=0 开始。
        // 同一个坑对 IME 也成立（见第 4 节），只是那里另一个浮层已经自己处理过了。
        //
        // 这里钉两件事：抽屉必须让出系统栏；而且内边距只能加在**内容**上 ——
        // 加在面板上会让底色缩进去，抽屉滑入时状态栏那一条会露出后面的工作区。
        String drawer = stripComments(read(root, SRC + "ui/SideDrawer.kt"));
        require(drawer.contains("windowInsetsPadding(WindowInsets.systemBars)"),
                "侧栏抽屉必须让出系统栏（Modifier.windowInsetsPadding(WindowInsets.systemBars)）："
                        + "它画在 Scaffold 之上、不吃内容区的 padding，第一行会压到状态栏底下");
        int panelSurface = drawer.indexOf("Surface(");
        int insetPadding = drawer.indexOf("windowInsetsPadding(WindowInsets.systemBars)");
        require(panelSurface >= 0 && insetPadding > panelSurface,
                "系统栏内边距必须加在**面板内容**上，不能加在面板的 Surface 上："
                        + "面板底色要一直铺到屏幕边缘，否则抽屉滑入时露出后面的工作区");

        // ---- 5. 本测试自身必须被 canonical suite 执行 ------------------------
        String script = read(root, "test-source-no-build.sh");
        require(script.contains("LayoutConsistencyTest \"$PROJECT_ROOT\""),
                "canonical source suite 必须执行本守卫");

        System.out.println("LayoutConsistencyTest PASS"
                + "（长按触发 · 气泡宽度自适应 · 弹窗宽度三档 · 边距 " + tokenUsers
                + " 处统一 · 输入法让位）");
    }
}
