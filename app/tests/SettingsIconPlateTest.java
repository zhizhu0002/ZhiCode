import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 设置页「行首彩色图标块」的守卫。
 *
 * <h2>它守的是哪一条要求</h2>
 *
 * 用户看过小米「设置」的整页截图之后说：
 *
 * <blockquote>那就借鉴小米自带的设置的图标</blockquote>
 *
 * 参考物里**没有一行是裸字形**：行首一律是一块彩色圆角方块，字形是白色的、缩在方块里。
 * 这块底不是装饰细节 —— 它正是"图标显得扁扁的"的根因（裸字形在 56dp 行高里太轻）。
 * 所以"每一行都要有那块底"本身就是要求，而不是可以各自发挥的地方。
 *
 * <h2>为什么这些事必须靠守卫</h2>
 *
 * 全部都是「改坏了不会编译失败」的：
 *
 * <ol>
 *   <li><b>漏一行</b>。多一个少一个 `plate =` 参数照样编译、照样跑，只是那一行变回
 *       改版前的单色裸字形。整页扫一遍才发现"就它没有底"。</li>
 *   <li><b>只给一半</b>。只给 `icon` 没给 `plate`（或反过来）会静默退化成"没有图标的行"
 *       或者"一块空色块"，`plateStartAction` 里那个 `null` 分支不会报任何错。</li>
 *   <li><b>两行一模一样</b>。同页出现两个相同的（字形, 底色），用户会以为它们共用一条设置。
 *       这是并排才看得出来的错误，单看每一处都正常。</li>
 *   <li><b>底色亮度</b>。小米原色的绿/橙配白字对比度只有 ≈2.0:1，白字形会糊在亮底色上。
 *       这里逐个数算 WCAG 对比度并卡 ≥3:1 —— 手感调色很容易悄悄掉到线下。</li>
 *   <li><b>底从超椭圆退回普通圆角</b>，或者<b>字形颜色从白改成主题色</b>。前者丢掉小米那套
 *       连续曲率，后者会跟着深色模式变暗、在彩色底上彻底看不清。</li>
 * </ol>
 */
public final class SettingsIconPlateTest {

    private static final String SRC = "app/src/main/java/com/zhizhu/zhicode/compose/";
    private static final String PLATE = SRC + "ui/settings/SettingsIconPlate.kt";
    private static final String ROWS = SRC + "ui/settings/SettingsRows.kt";
    private static final String DIALOG = SRC + "ui/settings/SettingsDialog.kt";

    /** 行式组件：每一个调用点都必须带一对 `icon` + `plate`。 */
    private static final String[] ROW_CALLS = {
            "SettingsChoice",
            "SettingsToggle",
            "SettingsIntField",
            "SettingsTextField",
            "SettingsNumber",
            "SettingsEntry",
            "SettingsIconEntry",
            // 「搜索服务」那一行是裸 BasicComponent（不要行尾箭头），图标块直接挂 startAction。
            "BasicComponent",
    };

    /** 一页里至少要这么多行带图标；比这个少说明扫描失效或整页退回去了。 */
    private static final int MIN_ROWS = 20;

    /** 行式组件自己的声明（`private fun Xxx(`）不是调用点，扫描时要跳过。 */
    private static boolean isDeclaration(String source, int matchStart) {
        return matchStart >= 4 && source.startsWith("fun ", matchStart - 4);
    }

    /** 白字形与底色的对比度下限。 */
    private static final double MIN_CONTRAST = 3.0;

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static String read(Path root, String relative) throws Exception {
        return new String(Files.readAllBytes(root.resolve(relative)), StandardCharsets.UTF_8);
    }

    /** 去注释：注释里写着 `plate =` 不该让断言通过。 */
    private static String stripComments(String text) {
        return text.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//[^\\n]*", " ");
    }

    private static String squash(String text) {
        return text.replaceAll("\\s+", "");
    }

    /**
     * 从 `at` 处的 `(` 开始做括号配平，返回括号内的参数文本。
     *
     * 必须跳过字符串字面量：这一页的文案里有中文全角括号，也有 `${...}` 模板，
     * 直接数括号会被文案里的括号带偏（那样后一行会被算进前一行，断言就静默失效了）。
     */
    private static String argsAt(String source, int openParen) {
        int depth = 0;
        boolean inString = false;
        for (int i = openParen; i < source.length(); i++) {
            char c = source.charAt(i);
            if (inString) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) return source.substring(openParen + 1, i);
            }
        }
        throw new AssertionError("括号没有配平（从下标 " + openParen + " 开始）—— 断言会失效，先修这段源码");
    }

    /** WCAG 相对亮度里的单通道线性化。 */
    private static double channel(int value) {
        double s = value / 255.0;
        return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }

    /** 与白色的 WCAG 对比度。 */
    private static double contrastWithWhite(int rgb) {
        double l = 0.2126 * channel((rgb >> 16) & 0xFF)
                + 0.7152 * channel((rgb >> 8) & 0xFF)
                + 0.0722 * channel(rgb & 0xFF);
        return 1.05 / (l + 0.05);
    }

    /** 取 `val 名字 = 3.0.dp` 里的数值；[what] 用于报错定位。 */
    private static double dpConstant(String plate, String name, String what) {
        Matcher m = Pattern.compile("val\\s+" + name + "\\s*=\\s*([0-9]+(?:\\.[0-9]+)?)f?\\.dp").matcher(plate);
        require(m.find(), PLATE + " 里找不到 `val " + name + " = ...dp`（" + what + "）");
        return Double.parseDouble(m.group(1));
    }

    public static void main(String[] args) throws Exception {
        Path root = Paths.get(args.length == 0 ? "." : args[0]).toAbsolutePath().normalize();
        String plateRaw = read(root, PLATE);
        String plate = stripComments(plateRaw);
        String rows = stripComments(read(root, ROWS));
        String dialogRaw = read(root, DIALOG);
        String dialog = stripComments(dialogRaw);

        // ---- 0. 这一块必须写明自己是照着什么做的 --------------------------------
        //
        // 少了这句话，下一个人只会看到"这里为什么有块底色"，然后把它当成可有可无的装饰删掉。
        for (String token : new String[]{"小米", "设置", "对比度"}) {
            require(plateRaw.contains(token),
                    PLATE + " 必须写明参考物与规则（缺 \"" + token + "\"）："
                            + "否则那块底色会被当成随手加的装饰，改坏也没人知道");
        }

        // ---- 1. 底色的几何：超椭圆 + 白字形 ------------------------------------
        require(plate.contains("squircleBackground"),
                PLATE + " 的底必须走 Miuix 的 `squircleBackground`："
                        + "小米那套方块的角是连续曲率的超椭圆，退回 RoundedCornerShape 就不是那个观感了");
        require(!plate.contains("RoundedCornerShape"),
                PLATE + " 里不该出现 RoundedCornerShape：底只有超椭圆一种画法");
        require(plate.contains("tint = SettingsPlate.GlyphTint"),
                PLATE + " 的字形必须用 SettingsPlate.GlyphTint（纯白）："
                        + "跟着主题色走会在深色模式变暗，在彩色底上直接看不清");
        require(plate.contains("GlyphTint = Color.White"),
                PLATE + " 的 GlyphTint 必须是纯白");
        require(plate.contains("contentDescription = null"),
                PLATE + " 的图标块是装饰（行的语义在标题里），必须 contentDescription = null："
                        + "否则 TalkBack 会把同一件事念两遍");

        double size = dpConstant(plate, "Size", "方块边长");
        double radius = dpConstant(plate, "Radius", "方块圆角");
        double glyph = dpConstant(plate, "Glyph", "字形边长");
        double glyphRatio = glyph / size;
        double radiusRatio = radius / size;
        require(glyphRatio >= 0.55 && glyphRatio <= 0.65,
                "字形/方块 = " + glyph + "/" + size + " = " + String.format("%.3f", glyphRatio)
                        + "，超出 [0.55, 0.65]：小米截图里这个比例是 0.55，太小就成了"
                        + "「图标扁扁的」，太大字形会顶到圆角");
        require(radiusRatio >= 0.25 && radiusRatio <= 0.38,
                "圆角/方块 = " + String.format("%.3f", radiusRatio)
                        + "，超出 [0.25, 0.38]：这个区间才是超椭圆的观感，"
                        + "小了像方块、大了像圆点");
        require(size >= 24 && size <= 36,
                "方块边长 " + size + "dp 超出 [24, 36]：Miuix 的行最小高是 56dp，"
                        + "太大会把两行文字挤扁，太小则又回到「轻飘飘」的样子");

        // ---- 2. 六个底色：与白字形的对比度必须 ≥3:1 ------------------------------
        Matcher colorMatcher = Pattern.compile(
                "val\\s+([A-Za-z0-9_]+)\\s*=\\s*Color\\(0x[Ff]{2}([0-9A-Fa-f]{6})\\)").matcher(plate);
        Map<String, Integer> palette = new LinkedHashMap<>();
        while (colorMatcher.find()) {
            palette.put(colorMatcher.group(1), (int) Long.parseLong(colorMatcher.group(2), 16));
        }
        require(palette.size() >= 6,
                PLATE + " 只解析到 " + palette.size() + " 个底色（至少要 6 个）："
                        + "解析变少说明常量换了写法，这条断言会静默失效");
        StringBuilder contrastReport = new StringBuilder();
        for (Map.Entry<String, Integer> entry : palette.entrySet()) {
            double contrast = contrastWithWhite(entry.getValue());
            contrastReport.append(String.format("%s=%.2f ", entry.getKey(), contrast));
            require(contrast >= MIN_CONTRAST,
                    "底色 `" + entry.getKey() + "` 与白字形的对比度只有 "
                            + String.format("%.2f", contrast) + ":1（要求 ≥" + MIN_CONTRAST + "）—— "
                            + "小米原色偏亮，直接抄会让白字形糊在底上，明度要往回收一点");
        }

        // ---- 3. 设置页每一行都必须带一对 icon + plate ---------------------------
        //
        // 逐**调用点**数，不只数总数：总数对得上完全可能是"某一行漏了、另一行写了两遍"。
        Set<String> usedColors = new LinkedHashSet<>();
        Set<String> pairs = new LinkedHashSet<>();
        List<String> pairsDuplicate = new ArrayList<>();
        int rowCount = 0;
        for (String name : ROW_CALLS) {
            Matcher m = Pattern.compile(Pattern.quote(name) + "\\(").matcher(dialog);
            while (m.find()) {
                // `private fun SettingsIconEntry(` 也是同一个字面量，它不是调用点。
                if (isDeclaration(dialog, m.start())) continue;
                String callArgs = argsAt(dialog, m.end() - 1);
                String squashed = squash(callArgs);
                boolean pairedForm = squashed.contains("icon=ZhiIcons.")
                        && squashed.contains("plate=SettingsPlateColors.");
                boolean inlineForm = squashed.contains("SettingsIconPlate(icon=ZhiIcons.")
                        && squashed.contains("color=SettingsPlateColors.");
                require(pairedForm || inlineForm,
                        "`" + name + "` 的这一个调用点没有行首图标块：\n  "
                                + squashed.substring(0, Math.min(120, squashed.length())) + "…\n"
                                + "设置页每一行都要有「彩色圆角方块 + 白字形」（小米设置观感）。"
                                + "一对参数要成对出现：只有 icon 是改版前的单色裸字形，"
                                + "只有 plate 是一块空色块");
                rowCount++;

                Matcher pair = Pattern.compile(
                        "(?:icon=ZhiIcons\\.([A-Za-z0-9_]+),?plate=SettingsPlateColors\\.([A-Za-z0-9_]+))"
                                + "|(?:SettingsIconPlate\\(icon=ZhiIcons\\.([A-Za-z0-9_]+),"
                                + "color=SettingsPlateColors\\.([A-Za-z0-9_]+)\\))").matcher(squashed);
                if (pair.find()) {
                    String icon = pair.group(1) != null ? pair.group(1) : pair.group(3);
                    String color = pair.group(2) != null ? pair.group(2) : pair.group(4);
                    usedColors.add(color);
                    String key = icon + " / " + color;
                    if (!pairs.add(key)) pairsDuplicate.add(key);
                }
            }
        }
        require(rowCount >= MIN_ROWS,
                DIALOG + " 里只数到 " + rowCount + " 个设置行（要求 ≥" + MIN_ROWS + "）："
                        + "行式组件的调用形式变了，或者是整页被换回了没图标的写法");
        require(!pairs.isEmpty(), "没能从 " + DIALOG + " 里解析出任何一对 (字形, 底色)");
        require(pairs.size() == rowCount,
                "有 " + (rowCount - pairs.size()) + " 个设置行没解析出 (字形, 底色) 这一对："
                        + "参数顺序/写法变了就会这样，守卫需要先跟上");
        require(pairsDuplicate.isEmpty(),
                "同一页里这些 (字形, 底色) 出现了不止一次：" + pairsDuplicate
                        + "\n两个一模一样的块并排出现，用户会以为它们共用同一条设置 —— "
                        + "换成「同底色不同字形」或「同字形不同底色」");

        // ---- 4. 底色不许有僵尸常量 ---------------------------------------------
        List<String> unused = new ArrayList<>();
        for (String name : palette.keySet()) {
            if (!usedColors.contains(name)) unused.add(name);
        }
        require(unused.isEmpty(),
                PLATE + " 里这些底色没有任何一行在用：" + unused
                        + "\n（调色板留着不用的颜色，下一个人会照着它继续加，"
                        + "而对比度与去重两条都只对「用到的」生效）");
        List<String> unknown = new ArrayList<>();
        for (String name : usedColors) {
            if (!palette.containsKey(name)) unknown.add(name);
        }
        require(unknown.isEmpty(),
                DIALOG + " 用了没定义的底色：" + unknown);

        // ---- 5. 行式组件必须真的把这一对转发下去 --------------------------------
        //
        // 只在调用点写 `icon = ...` 而组件内部丢掉它，是这套参数最容易出的错：
        // 编译通过、页面照旧（没有图标），断言全绿。
        for (String name : ROW_CALLS) {
            if (name.equals("BasicComponent") || name.equals("SettingsIconEntry")) continue;
            require(rows.contains("internal fun " + name + "("),
                    ROWS + " 里找不到 `internal fun " + name + "(`");
        }
        int forwarded = 0;
        Matcher fwd = Pattern.compile("startAction=plateStartAction\\(icon,plate\\)").matcher(squash(rows));
        while (fwd.find()) forwarded++;
        require(forwarded >= 5,
                ROWS + " 里只有 " + forwarded + " 处把 icon/plate 转发给 startAction（要求 ≥5）："
                        + "漏掉的那几个组件会收下参数然后什么都不做");
        require(squash(rows).contains("privatefunplateStartAction(icon:Painter?,plate:Color?)"),
                ROWS + " 里的 plateStartAction 必须同时收 icon 与 plate："
                        + "只有一个参数就表达不出「成对」这个约束");

        System.out.println("SettingsIconPlateTest PASS（设置行 " + rowCount
                + " 行全部带图标块 · (字形, 底色) " + pairs.size() + " 组互不重复 · 底色 "
                + palette.size() + " 个，白字对比度 " + contrastReport.toString().trim() + "）");
    }
}
