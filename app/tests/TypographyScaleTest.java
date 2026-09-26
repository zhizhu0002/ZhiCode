import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;

/**
 * 字阶的**单一来源**守卫。
 *
 * <p>为什么需要它：UI 的"看起来没设计过"基本上都来自同一个模式 ——
 * 同一层次的尺寸被逐处手写，于是数字自己长出近似值。圆角已经踩过一次
 * （`RoundedCornerShape(7/8/9/11/13/16dp)` 满地跑，后来收敛到 `ZhiRadius`），
 * 但那时候**没有守卫**，所以同样的坑在字号上又踩了第二次：
 * 收口前全工程有 115 处裸 `fontSize = N.sp`、15 种不同字号。
 *
 * <p>它守四件事，每一件都是「改坏了不会编译失败」的：
 * <ol>
 *   <li>没有新的裸字号 —— 想加字号必须用 {@code ZhiTextScale} 的档位，
 *       或者明确地修改字阶本身（那是刻意的决定，会被看见）；</li>
 *   <li>字阶只有一处定义、`textStyles` 只在两个地方出现（根主题 + 字阶文件）——
 *       防的是"再写第二份紧凑字阶"，弹窗那层就是这么长出来的；</li>
 *   <li>用了 `ZhiTextScale` 的文件必须显式 import，不靠同包巧合；</li>
 *   <li>根主题必须真的把字阶接上 —— 删掉那一行不会有任何编译错误，
 *       界面会静默退回 Miuix 的平板尺度。</li>
 * </ol>
 *
 * <p>第 1 条是白名单式的严格断言（当前裸字号为 0，所以白名单是空的）。
 * 若将来确实需要某个例外，请加进 {@link #BARE_FONT_ALLOWED} 并在注释里写清理由，
 * 而不要放宽这条正则。
 */
public final class TypographyScaleTest {

    /** 工程里唯一定义字阶的地方。 */
    private static final String SCALE_FILE =
            "app/src/main/java/com/zhizhu/zhicode/compose/theme/ZhiTextStyles.kt";

    /** UI 源码根（只扫这一层：api/ 那些纯逻辑层不画界面）。 */
    private static final String UI_ROOT =
            "app/src/main/java/com/zhizhu/zhicode/compose";

    /**
     * 允许保留裸 `fontSize = N.sp` 的文件 → 理由。
     *
     * <p>当前为空：115 处已全部迁到字阶档位。留这张表是为了让下一个例外
     * **必须写下来**，而不是悄悄放宽断言。
     */
    private static final Map<String, String> BARE_FONT_ALLOWED = new LinkedHashMap<>();

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

    /** `fontSize = 12.sp` 与 `fontSize: TextUnit = 12.sp` 都算裸字号。 */
    private static final Pattern BARE_FONT =
            Pattern.compile("fontSize\\s*:\\s*[A-Za-z.]+\\s*=\\s*[0-9.]+\\s*\\.sp"
                    + "|fontSize\\s*=\\s*[0-9.]+\\s*\\.sp");

    public static void main(String[] args) throws Exception {
        String root = args.length > 0 ? args[0] : ".";

        // ---- 1. 不得再出现新的裸字号 ----------------------------------------
        List<String> offenders = new ArrayList<>();
        for (String file : kotlinSources(root)) {
            if (BARE_FONT_ALLOWED.containsKey(file)) continue;
            String text = read(root, file);
            Matcher m = BARE_FONT.matcher(text);
            while (m.find()) {
                int line = 1;
                for (int i = 0; i < m.start(); i++) if (text.charAt(i) == '\n') line++;
                offenders.add(file + ":" + line + "  " + m.group().replaceAll("\\s+", " "));
            }
        }
        require(offenders.isEmpty(),
                "字号必须取自 ZhiTextScale 的档位，不能写新的裸数值。\n"
                        + "  如需例外，请加进 TypographyScaleTest.BARE_FONT_ALLOWED 并写明理由。\n  "
                        + String.join("\n  ", offenders));

        // ---- 2. 字阶只有一处定义、textStyles 只在这两处出现 -------------------
        String scale = read(root, SCALE_FILE);
        require(scale.contains("object ZhiTextScale"),
                "字阶必须定义在 " + SCALE_FILE + " 的 ZhiTextScale 里");

        // 档位是 9 个：数量变化本身不是错，但必须是有意的 —— 所以钉住名字集合。
        List<String> steps = new ArrayList<>();
        Matcher stepMatcher = Pattern.compile(
                "val\\s+(Title|TitleSmall|Heading|Subheading|Body|BodySmall|Caption|Footnote|Micro)\\s*=")
                .matcher(scale);
        while (stepMatcher.find()) steps.add(stepMatcher.group(1));
        require(steps.size() == 9 && new HashSet<>(steps).size() == 9,
                "ZhiTextScale 应有 9 个互不重名的档位，实际 " + steps.size() + " 个: " + steps);

        List<String> textStylesSites = new ArrayList<>();
        for (String file : kotlinSources(root)) {
            String text = read(root, file);
            Matcher m = Pattern.compile("textStyles\\s*=").matcher(text);
            if (m.find() && !file.equals(SCALE_FILE)) textStylesSites.add(file);
        }
        require(textStylesSites.size() == 1,
                "textStyles 只应在根主题出现一处（现在是 " + textStylesSites.size() + " 处: "
                        + textStylesSites + "）。第二份紧凑字阶就是这么长出来的 —— "
                        + "它不会编译失败，只会让两个界面的字号不一致。");

        // ---- 3. 用到档位的文件必须显式 import --------------------------------
        for (String file : kotlinSources(root)) {
            if (file.equals(SCALE_FILE)) continue;
            String text = read(root, file);
            if (!text.contains("ZhiTextScale.")) continue;
            require(text.contains("import com.zhizhu.zhicode.compose.theme.ZhiTextScale"),
                    file + " 用了 ZhiTextScale 却没 import（不要依赖同包巧合）");
        }

        // ---- 4. 根主题必须真的把字阶接上 -------------------------------------
        String app = read(root, "app/src/main/java/com/zhizhu/zhicode/compose/ui/AppScaffold.kt");
        require(app.contains("zhiTextStyles()"),
                "根 MiuixTheme 必须传 textStyles = zhiTextStyles()："
                        + "删掉它没有任何编译错误，界面会静默退回 Miuix 的平板尺度字阶");

        // ---- 5. 本测试自身必须被 canonical suite 执行 ------------------------
        String script = read(root, "test-source-no-build.sh");
        require(script.contains("TypographyScaleTest \"$PROJECT_ROOT\""),
                "canonical source suite 必须执行本守卫");

        System.out.println("TypographyScaleTest PASS"
                + "（裸字号 0 处 · 档位 " + steps.size() + " 个 · textStyles 单一来源）");
    }
}
