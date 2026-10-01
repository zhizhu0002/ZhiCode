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

        // 弹窗边距也不允许写字面量：应当统一走 DialogWideOutsideMargin。
        // （注意断言的是「不得出现 outsideMargin = DpSize(…) 这种字面量」，
        //  而不是「用到常量的文件数量」—— 后者在任何一处漏改时反而会通过。）
        List<String> marginLiteralOffenders = new ArrayList<>();
        int tokenUsers = 0;
        for (String file : kotlinSources(root, SRC)) {
            String text = stripComments(read(root, file));
            if (text.contains("outsideMargin = DialogWideOutsideMargin") || text.contains("outsideMargin = DialogSheetOutsideMargin")) tokenUsers++;
            Matcher m = Pattern.compile("outsideMargin\\s*=\\s*DpSize\\s*\\(").matcher(text.replace("DialogSheetOutsideMargin = DpSize", "DialogSheetOutsideMargin ="));
            while (m.find()) {
                marginLiteralOffenders.add(file + ":" + lineOf(text, m.start()));
            }
        }
        require(marginLiteralOffenders.isEmpty(),
                "弹窗外边距必须统一走 DialogWideOutsideMargin，不要写字面量 DpSize：\n  "
                        + String.join("\n  ", marginLiteralOffenders));
        // 设置及其二级页（API/MCP/技能/角色卡/记忆）已整页化（SettingsSubPage），
        // 不再走 OverlayDialog，常量的自然用户随之减少 —— 阈值只保证「剩余弹窗没绕过」。
        require(tokenUsers >= 5,
                "应当有多个弹窗使用 DialogWideOutsideMargin（现在只有 " + tokenUsers
                        + " 处）——过少说明有人绕过了它");

        // ---- 4. 本测试自身必须被 canonical suite 执行 ------------------------
        String script = read(root, "test-source-no-build.sh");
        require(script.contains("LayoutConsistencyTest \"$PROJECT_ROOT\""),
                "canonical source suite 必须执行本守卫");

        System.out.println("LayoutConsistencyTest PASS"
                + "（长按触发 · 气泡宽度自适应 · 弹窗宽度三档 · 边距 " + tokenUsers + " 处统一）");
    }
}
