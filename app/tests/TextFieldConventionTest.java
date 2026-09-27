import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;

/**
 * 输入框的**唯一入口**守卫。
 *
 * <p>为什么需要它：真机上出现过两个看起来毫无关联、实则同源的缺陷，
 * 而两处都**不会编译失败**，只会让人对着屏幕猜：
 *
 * <ol>
 *   <li><b>输入的字看不见</b>。Miuix 的输入字色是
 *       {@code textStyle.color.takeOrElse { LocalContentColor.current }}，
 *       而 Miuix {@code Card} 会用它的 {@code contentColor} 覆盖
 *       {@code LocalContentColor}。弹窗内容区（{@code DialogShell} 的
 *       {@code groupBody}）是一张 {@code contentColor = onSurfaceContainerHigh}
 *       的 Card，暗色方案里它是 {@code #666666} —— 与占位符
 *       {@code onSecondaryContainer}（{@code #7C7C7C}）在 {@code #242424}
 *       底上几乎同色。</li>
 *   <li><b>光标停在第一个字母前面，输入顺序错乱</b>（输入 {@code 123} 显示
 *       {@code 231}）。{@code TextField(value: String, …)} 那条重载内部是
 *       {@code remember {}} 建一个选区为 {@code TextRange.Zero} 的
 *       {@code TextFieldValue}，之后每次外部文本变化只换 text、<b>选区原样保留</b>，
 *       于是光标恒在索引 0，输入法按「光标在 0」提交。</li>
 * </ol>
 *
 * <p>两条都靠 {@code ZhiTextField}（{@code compose/ui/Common.kt}）一处修好：
 * 它走 {@code TextFieldState} 重载、外部改值用
 * {@code setTextAndPlaceCursorAtEnd}、并显式补一个看得清的文字色。
 * 本守卫负责的是**不让裸输入框再长回来**：
 *
 * <ol>
 *   <li>{@code ui/} 下除了那一个转发点，任何地方都不得直接调用
 *       {@code TextField(}；</li>
 *   <li>转发点必须真的含 {@code setTextAndPlaceCursorAtEnd} 与显式文字色
 *       （删掉任意一个都不会编译失败，界面会静默退化）；</li>
 *   <li>用 {@code ZhiTextField} 的文件必须显式 import，不靠同包巧合；</li>
 *   <li>Miuix 的 {@code TextField} 只允许被那一个文件 import。</li>
 * </ol>
 */
public final class TextFieldConventionTest {

    /** 唯一的输入框转发点。 */
    private static final String FORWARDING_FILE =
            "app/src/main/java/com/zhizhu/zhicode/compose/ui/Common.kt";

    /** UI 源码根（只扫这一层：api/ 那些纯逻辑层不画界面）。 */
    private static final String UI_ROOT =
            "app/src/main/java/com/zhizhu/zhicode/compose";

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static String read(String root, String relative) throws Exception {
        return new String(Files.readAllBytes(Paths.get(root, relative)),
                java.nio.charset.StandardCharsets.UTF_8);
    }

    private static List<String> kotlinSources(String root) throws Exception {
        try (Stream<Path> walk = Files.walk(Paths.get(root, UI_ROOT))) {
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

    /**
     * 去掉注释。
     *
     * <p>这一步是<b>必须</b>的：下面有几条断言查的是具体调用，而最初版本直接在原文上
     * 用 {@code contains}，于是「真正的调用被删掉、但 JavaDoc 里还写着这个词」时守卫
     * 会静默通过 —— 这是实测出来的：把 setTextAndPlaceCursorAtEnd 换成普通 edit、
     * 把 takeOrElse 删掉，守卫都报 PASS。剥掉注释后两条都会被抓到。
     */
    private static String stripComments(String text) {
        String noBlock = text.replaceAll("(?s)/\\*.*?\\*/", " ");
        return noBlock.replaceAll("(?m)//[^\\n]*", " ");
    }

    /**
     * 裸输入框调用。
     *
     * <p>前面的 {@code (?<![A-Za-z])} 是必须的：否则 {@code ZhiTextField(} 里的
     * {@code TextField(} 也算命中。{@code TextFieldDefaults.…} /
     * {@code TextFieldState(} 因为后面接的不是左括号，天然不会被匹配。
     */
    private static final Pattern BARE_TEXT_FIELD =
            Pattern.compile("(?<![A-Za-z])TextField\\s*\\(");

    public static void main(String[] args) throws Exception {
        String root = args.length > 0 ? args[0] : ".";

        // ---- 1. 不得再有裸输入框 --------------------------------------------
        List<String> offenders = new ArrayList<>();
        for (String file : kotlinSources(root)) {
            if (file.equals(FORWARDING_FILE)) continue;
            String text = read(root, file);
            Matcher m = BARE_TEXT_FIELD.matcher(text);
            while (m.find()) offenders.add(file + ":" + lineOf(text, m.start()));
        }
        require(offenders.isEmpty(),
                "输入框必须走 ZhiTextField（" + FORWARDING_FILE + "）：\n"
                        + "  直接用裸 TextField 会同时丢掉「可见的文字色」与"
                        + "「正确的光标位置」两件事，且不会编译失败。\n  "
                        + String.join("\n  ", offenders));

        // ---- 2. 转发点必须真的修好这两件事 -----------------------------------
        // 先剥掉注释：否则「代码删了、注释里还写着这个词」会让下面几条静默通过。
        String forwarding = read(root, FORWARDING_FILE);
        String code = stripComments(forwarding);
        require(code.contains("fun ZhiTextField("),
                FORWARDING_FILE + " 必须定义 ZhiTextField");
        require(code.contains("setTextAndPlaceCursorAtEnd("),
                "ZhiTextField 必须用 setTextAndPlaceCursorAtEnd 写回外部改值："
                        + "换成普通 edit / copy(text = …) 会保留选区（恒为 0），"
                        + "于是光标停在第一个字母前面、输入顺序错乱。");
        require(code.contains(".takeOrElse {"),
                "ZhiTextField 必须给文字补一个显式颜色（takeOrElse）："
                        + "否则文本色会回落到 LocalContentColor，被弹窗里那张 Card 的"
                        + " contentColor（暗色下 #666666）吃掉，输入的字和底色分不出来。");
        // 断言写成 `TextFieldState(` 与 `state = ` 两条、而不是「文件里提到过」：
        // `TextFieldState` 也可能只出现在 import 行里。同理 takeOrElse 要用带
        // 花括号的调用形态（`import …graphics.takeOrElse` 不含它）。
        require(code.contains("TextFieldState("),
                "ZhiTextField 必须用 TextFieldState 建状态");
        require(code.contains("state = "),
                "ZhiTextField 必须把 state 交给 Miuix 的 TextFieldState 重载："
                        + "改回 `value = 字符串` 那条旧重载会重新引入「光标恒在 0」的缺陷。");

        // ---- 3. 用到它的文件必须显式 import ----------------------------------
        for (String file : kotlinSources(root)) {
            if (file.equals(FORWARDING_FILE)) continue;
            String text = read(root, file);
            if (!text.contains("ZhiTextField(")) continue;
            require(text.contains("import com.zhizhu.zhicode.compose.ui.ZhiTextField"),
                    file + " 用了 ZhiTextField 却没 import（不要依赖同包巧合）");
        }

        // ---- 4. Miuix 的 TextField 只允许被转发点 import ----------------------
        List<String> importers = new ArrayList<>();
        for (String file : kotlinSources(root)) {
            if (file.equals(FORWARDING_FILE)) continue;
            String text = read(root, file);
            if (text.contains("import top.yukonga.miuix.kmp.basic.TextField\n")) {
                importers.add(file);
            }
        }
        require(importers.isEmpty(),
                "除转发点外不得 import Miuix 的 TextField：" + importers
                        + "（这些地方应当用 ZhiTextField）");

        // ---- 5. 本测试自身必须被 canonical suite 执行 ------------------------
        String script = read(root, "test-source-no-build.sh");
        require(script.contains("TextFieldConventionTest \"$PROJECT_ROOT\""),
                "canonical source suite 必须执行本守卫");

        System.out.println("TextFieldConventionTest PASS"
                + "（裸输入框 0 处 · 转发点已带光标与文字色修复）");
    }
}
