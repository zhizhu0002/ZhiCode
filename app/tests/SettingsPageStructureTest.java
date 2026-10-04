import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 设置页的守卫：有限联网数值走滑杆、自由数值仍可输入、页面**自动保存**、
 * 以及 API 配置那一行**要带**行首图标块（小米设置观感）。
 *
 * <p>这些都是「改坏了不会编译失败」的事：
 *
 * <ol>
 *   <li><b>联网结果数与超时使用有限范围滑杆</b>；上下文窗口和压缩上限仍是自由输入字段。</li>
 *   <li><b>输入过程中不得回写自由数值文本框</b>。这条是最容易在重构里被"顺手优化"掉的：
 *       看着更友好（"越界就立刻纠正"），实际会让最小值为 5 / 50 的项**根本没法输入**
 *       —— 打 {@code 15} 的第一个字符 {@code 1} 被判越界、clamp 成 5、文本被改成 {@code 5}，
 *       接着那个 5 就变成 {@code 55}。所以越界只在**提交**时 clamp，文本框到**失焦**才收敛。</li>
 *   <li><b>设置页只能有一条写入路径，且没有「保存」按钮</b>。改动即时生效；
 *       同时不能留一条"只改 draft 不生效"的旧入口，那会让"改了没反应"重新出现。</li>
 * </ol>
 */
public final class SettingsPageStructureTest {

    private static final String SRC = "app/src/main/java/com/zhizhu/zhicode/compose/";

    private static final String DIALOG = SRC + "ui/settings/SettingsDialog.kt";
    private static final String ROWS = SRC + "ui/settings/SettingsRows.kt";
    private static final String MODELS = SRC + "model/SettingsModels.kt";
    private static final String VIEW_MODEL = SRC + "state/WorkspaceViewModel.kt";
    private static final String APP_SCAFFOLD = SRC + "ui/AppScaffold.kt";

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static String read(Path root, String path) throws Exception {
        return new String(Files.readAllBytes(root.resolve(path)), StandardCharsets.UTF_8);
    }

    /** 去掉注释：否则「代码删了、注释里还写着」会让断言静默通过。 */
    private static String stripComments(String text) {
        return text.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", " ");
    }

    /** 去掉所有空白后再比较：检查代码形态时不该被缩进/换行影响。 */
    private static boolean has(String source, String needle) {
        return source.replaceAll("\\s+", "").contains(needle.replaceAll("\\s+", ""));
    }

    private static int countOf(String source, String needle) {
        return source.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }

    /** 取 [from, to) 之间的片段，用来把断言限定在某一个函数/lambda 体内。 */
    private static String between(String source, String from, String to) {
        int start = source.indexOf(from);
        if (start < 0) throw new AssertionError("找不到起点：" + from);
        int end = source.indexOf(to, start + from.length());
        if (end < 0) throw new AssertionError("找不到终点：" + to);
        return source.substring(start, end);
    }

    public static void main(String[] args) throws Exception {
        Path root = Paths.get(args.length == 0 ? "." : args[0]).toAbsolutePath().normalize();
        String dialog = stripComments(read(root, DIALOG));
        String rows = stripComments(read(root, ROWS));
        String models = stripComments(read(root, MODELS));
        String viewModel = stripComments(read(root, VIEW_MODEL));
        String scaffold = stripComments(read(root, APP_SCAFFOLD));

        // ---- 1. 固定范围的联网数值走滑杆，自由输入仍留在文本框 -------------------
        require(has(dialog, "title=\"默认搜索结果数（1-50）\"")
                        && has(dialog, "title=\"联网超时（秒）\"")
                        && countOf(dialog, "SliderPreference(") == 3,
                "搜索结果数、联网超时和自动压缩上限必须使用 Miuix SliderPreference");
        require(has(dialog, "title=\"上下文窗口\"")
                        && countOf(dialog, "SettingsIntField(") == 1,
                "上下文窗口仍按自由输入字段保留现有语义");
        require(!has(dialog, "SettingsNumber("),
                "设置页不该改回枚举候选滚轮");

        require(has(dialog, "valueRange=WEB_RESULTS_MIN.toFloat()..WEB_RESULTS_MAX.toFloat()")
                        && has(dialog, "steps=WEB_RESULTS_MAX-WEB_RESULTS_MIN-1")
                        && has(dialog, "valueRange=WEB_TIMEOUT_MIN_SEC.toFloat()..WEB_TIMEOUT_MAX_SEC.toFloat()")
                        && has(dialog, "steps=WEB_TIMEOUT_MAX_SEC-WEB_TIMEOUT_MIN_SEC-1")
                        && has(dialog, "valueRange=COMPACT_PERCENT_MIN.toFloat()..COMPACT_PERCENT_MAX.toFloat()")
                        && has(dialog, "steps=COMPACT_PERCENT_MAX-COMPACT_PERCENT_MIN-1"),
                "滑杆及剩余数值字段的范围必须由 WEB_* / COMPACT_* 常量约束");
        // 上下文窗口的两种写法：能解析 `128k / 1.5m`，也要能显示回短写法。
        require(has(dialog, "parse=::parseTokenCount") && has(dialog, "format=::formatTokenCountShort"),
                "上下文窗口必须复用 parseTokenCount / formatTokenCountShort，"
                        + "否则「支持 128k / 1.5m 写法」这句提示就是假的");

        // ---- 2. 剩余 SettingsIntField 的两步提交语义 --------------------------
        String inputPath = between(rows, "onValueChange = { raw ->", "summary = summary,");
        require(has(inputPath, "text=raw"),
                "输入路径要先如实回显用户打的字");
        require(has(inputPath, "coerceIn(min,max)"),
                "越界只能在**提交**时 clamp");
        require(!inputPath.contains("format("),
                "输入过程中不得把文本框改写成 format(...)：最小值为 5 / 50 的项会在"
                        + "打第一个字符时就被改写，两位数的值根本输入不进去");
        // 只提交不收敛的话，屏幕上会留着一个跟真实值不一致的数（打了 999、实际是 10），
        // 用户会以为 999 生效了 —— 比不画这个控件更糟。
        require(has(rows, "onFocusChanged") && has(rows, "if(!focus.isFocused)text=format(value)"),
                "失焦时要把文本框收敛成真实值（打了 999 应当看到它变成 10）");

        // ---- 3. 自动保存：没有「保存」按钮，也没有第二条写入路径 ----------------
        require(!has(dialog, "onSave") && !dialog.contains("TextButton"),
                "设置主页顶栏不该再有「保存」按钮：改动即时生效，没有需要用户再确认的东西");
        require(!has(dialog, "actions="),
                "顶栏的 actions 槽位也该一并撤掉，留着会让人以为只是「暂时」没放按钮");
        // 只把断言限定在设置主页那一次调用上：二级页（API 配置 / MCP / 技能 / 角色卡 / 记忆）
        // 的 `onSave` 是**真的**提交动作，不能一起否掉。
        String hubEntry = between(scaffold, "entry<SettingsKey.Hub>", "entry<SettingsKey.Api>");
        require(has(hubEntry, "onChange=viewModel::applySettingsDraft") && !has(hubEntry, "onSave"),
                "设置页的唯一入口必须是 applySettingsDraft（改 draft + 立刻写回 state），"
                        + "并且不再传 onSave");
        require(has(viewModel, "draft.applyTo(it,keepDraft=true)"),
                "applySettingsDraft 必须走 SettingsDraft.applyTo 并保留 draft（draft 是这一屏的显示源）");
        // 归一化（clampWebResults / projectPath.ifBlank）只在 applyTo 里，绕过它等于
        // 把用户原始输入直接塞进引擎配置。
        require(has(models, "settingsDraft=if(keepDraft)") && has(models, "keepDraft:Boolean=false"),
                "applyTo 要能保留 draft（自动保存）同时保持旧的「提交并关闭」默认值");
        // 旧的"只改 draft"入口若复活，就会重新出现「改了没反应」。
        require(!has(viewModel, "fun setSettingsDraft(") && !has(viewModel, "fun updateSettingsDraft(")
                        && !has(viewModel, "fun saveSettings("),
                "设置不能再有第二条写入路径（setSettingsDraft / updateSettingsDraft / saveSettings）");

        // ---- 4. API 配置那一行：入口行，且必须带行首图标块 --------------------
        //
        // 这一条**反向改过一次**。原先是「这一行不带图标」，理由是"同屏只有它一个有图标
        // 会显得像另一种优先级，而且那个图标是自绘的、与同屏 Miuix 图标不是一套"。
        // 用户要求「借鉴小米自带的设置的图标」之后，整页每一行都有彩色图标块，
        // 缺一个反而成了唯一的例外 —— 于是这里改成要求它**必须有**。
        // 反向验证时改回"去掉这一行的图标"必须让这条失败。
        String modelService = between(dialog, "private fun ModelServicePage", "private fun AgentSecurityPage");
        require(has(modelService, "SettingsEntry(title=\"API配置\""),
                "「API 配置」这一行要用 SettingsEntry（带当前值的入口行）");
        require(has(modelService, "icon=ZhiIcons.link")
                        && has(modelService, "plate=SettingsPlateColors.blue"),
                "「API 配置」这一行必须带行首图标块（icon = ZhiIcons.link + plate = …blue）："
                        + "整页每一行都有图标块，缺一个会成为唯一的例外");
        require(!modelService.contains("SettingsIconEntry"),
                "这一行不该用 SettingsIconEntry：那是「扩展」组的入口行组件，"
                        + "这里要的是把当前模型值当副标题的 SettingsEntry");
        require(!dialog.contains("API 配置记录"),
                "标题已收成「API 配置」，别再退回带「记录」的长名字");

        System.out.println("SettingsPageStructureTest PASS");
    }
}
