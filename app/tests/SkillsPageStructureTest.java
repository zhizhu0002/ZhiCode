import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 二级页转场与技能页的守卫。
 *
 * <p>三件「改坏了不会编译失败」的事：
 *
 * <ol>
 *   <li><b>同一个文件里有多态二级页时，必须走 {@code SettingsPageStack}。</b>
 *       这是「三级窗口动画」唯一真正有牙的守卫。以前列表 / 表单 / 编辑器三态是
 *       一个 {@code when} 里的内容硬切，而 {@code AppScaffold} 的页面栈只反映
 *       「这一页开着没有」—— 栈深度不变，整页转场就不会触发，于是**一点动画都没有**，
 *       而且不会有任何编译或运行期症状。往后新增第六个二级页时，
 *       只要写两个 {@code SettingsSubPage} 分支就会重新掉进这个坑。</li>
 *   <li><b>技能目录里的文件名不得逃出技能目录。</b>这条路径是由界面输入框直接驱动的
 *       （「新建文件」的对话框），只做字符集校验的代码在遇到新写法时很容易失效，
 *       所以真正的边界是 {@code canonicalPath} 前缀比对，不能只有正则。</li>
 *   <li><b>「手动添加」必须从内容解析名字，且解析不出来时不许提交。</b>
 *       回退到默认名（取第一行、取文件名）会建出一个用户没打算建的目录，
 *       而这件事在界面上完全看不出来。</li>
 * </ol>
 */
public final class SkillsPageStructureTest {

    private static final String SRC = "app/src/main/java/com/zhizhu/zhicode/compose/";

    private static final String SUB_PAGE = SRC + "ui/settings/SettingsSubPage.kt";
    private static final String SKILLS_OVERLAY = SRC + "ui/dialogs/SkillsOverlay.kt";
    private static final String SKILL_STORE = SRC + "data/SkillStore.kt";
    private static final String MODELS = SRC + "model/SettingsModels.kt";
    private static final String VIEW_MODEL = SRC + "state/WorkspaceViewModel.kt";

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

    /**
     * 从 [from] 处的调用开始，找到它那个 `{ … }` 块的**结束下标之后**一位。
     *
     * <p>用途：判断某个调用是"在另一个调用的 lambda 里面"还是"在外面"。
     * 只比源码先后顺序是不行的 —— 被检查的东西排在后面，不代表它在里面。
     *
     * <p>⚠️ 这是**朴素**的括号计数，会被字符串字面量里未配平的 `{` / `}` 带偏
     * （本仓库的 Kotlin 里有字符串模板与注释，实测就踩到了）。
     * 所以能用别的判据就别用它 —— 现在的浮层断言改成了直接查挂载点。
     */
    private static int endOfBlock(String source, int from) {
        int open = source.indexOf('{', from);
        if (open < 0) throw new AssertionError("找不到代码块：offset " + from);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return i + 1;
        }
        throw new AssertionError("代码块没有配平：offset " + from);
    }

    private static int countOf(String source, String needle) {
        return source.split(Pattern.quote(needle), -1).length - 1;
    }

    private static List<String> kotlinSources(Path root) throws Exception {
        Path base = root.resolve("app/src/main/java");
        List<String> out = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(base)) {
            stream.filter(p -> p.toString().endsWith(".kt")).forEach(p -> out.add(p.toString()));
        }
        return out;
    }

    public static void main(String[] args) throws Exception {
        Path root = Paths.get(args.length == 0 ? "." : args[0]).toAbsolutePath().normalize();
        String subPage = stripComments(read(root, SUB_PAGE));
        String skills = stripComments(read(root, SKILLS_OVERLAY));
        String store = stripComments(read(root, SKILL_STORE));
        String models = stripComments(read(root, MODELS));
        String viewModel = stripComments(read(root, VIEW_MODEL));

        // ---- 1. 多态二级页必须走页面栈 ---------------------------------------
        //
        // 判据是「这个文件里出现了两次以上 SettingsSubPage(」——一次是单态页（不需要栈），
        // 两次以上就意味着有几态要互相切换，那就必须有转场容器，否则是硬切。
        //
        // 写成**扫描全部源码**而不是"逐个点名这五个文件"：点名只能守住今天这五个，
        // 新增的页面不在名单里就自动豁免了，而这正是最需要被拦住的一种改动。
        List<String> offenders = new ArrayList<>();
        for (String file : kotlinSources(root)) {
            if (file.endsWith("/SettingsSubPage.kt")) continue; // 这是定义处
            String text = stripComments(read(root, Paths.get(file).toString()));
            if (countOf(text, "SettingsSubPage(") >= 2 && !text.contains("SettingsPageStack(")) {
                offenders.add(file.substring(root.toString().length() + 1));
            }
        }
        require(offenders.isEmpty(),
                "这些文件里有多个二级页状态却没有用 SettingsPageStack，页面之间会被硬切"
                        + "（没有转场动画，且不会有任何编译/运行期症状）：\n  "
                        + String.join("\n  ", offenders));

        // 五个已知的页面都要真的在栈里，防止"只 import 不用"也能过上面的检查。
        for (String file : new String[]{
                "ui/dialogs/ApiConfigOverlay.kt",
                "ui/dialogs/McpConfigOverlay.kt",
                "ui/dialogs/SkillsOverlay.kt",
                "ui/dialogs/RoleCardsOverlay.kt",
                "ui/dialogs/MemoryOverlay.kt",
        }) {
            require(stripComments(read(root, SRC + file)).contains("SettingsPageStack("),
                    file + " 必须用 SettingsPageStack 承载它的多个页面状态");
        }

        // ---- 2. 页面栈必须用 Miuix 官方的 NavDisplay，而不是手搓的整页滑动 --------
        //
        // 第一版是 `AnimatedContent` + `slideInHorizontally` / `slideOutHorizontally`，
        // 用户实测报了**两个毛病**，两个都出在它身上：
        //   · 卡 —— 那是**布局**型动画（改 `Modifier.offset`），整屏 Scaffold +
        //     LazyColumn 每帧重新测量布局；Miuix 自己的转场走 graphicsLayer，
        //     源码里明确写着 "cost zero recomposition"。
        //   · 奇怪 —— 进入用 spring（会过冲）、退出用 tween(200)，两条曲线时长不匹配，
        //     而且没有官方转场自带的 dim 与跟随屏幕圆角的裁剪。
        //
        // 所以这里守两件事：用的是 NavDisplay + MiuixDefault，且**不再有**手搓的
        // 整页水平滑动。后者是真正的回归点 —— 谁再写回去，症状要用户滑一遍才发现。
        require(has(subPage, "NavDisplay(") && has(subPage, "NavTransitions.MiuixDefault"),
                "SettingsPageStack 必须用 Miuix 的 NavDisplay + NavTransitions.MiuixDefault："
                        + "手搓 AnimatedContent + slide 是布局型动画，整屏每帧重排会卡，"
                        + "且进出曲线时长不匹配、没有官方 dim 与圆角裁剪");
        require(!has(subPage, "slideInHorizontally") && !has(subPage, "slideOutHorizontally"),
                "不得再用手搓的整页水平滑动转场（那是上面那两个毛病的根因）");
        require(has(subPage, "NavDisplayEffects(") && has(subPage, "rememberNavSystemCornerRadius()"),
                "页面栈的转场效果层必须与外层 AppScaffold 一致：跟随屏幕圆角的裁剪 + 调暗");
        // key 必须是值相等的 data class：Miuix 拿 key 实例做 entry 的 contentKey
        // （页面状态的存档标识），toString() 必须由值派生，否则进程重启后状态静默重置。
        require(has(subPage, "internal data class SettingsPageKey(")
                        && has(subPage, "SettingsPageKey(val id: String, val depth: Int) : NavKey"),
                "SettingsPageKey 必须是实现 NavKey 的 data class（值相等 + 值派生的 toString）");
        // 调用方必须传**整条路径**：根页的 id 只有调用方知道，栈里合成一个"根"键
        // 会与 entry 对不上、页面渲染成空白。
        require(has(subPage, "path: List<SettingsPageKey>"),
                "SettingsPageStack 必须接收完整路径（含最底下那一页），不能只收当前页");

        // ---- 2b. FAB 必须走 Scaffold 的原生槽位 --------------------------------
        //
        // 用户报「skill 的加号点不了」。当时 FAB 是在页面里套 Box + align(BottomEnd)
        // 手工盖上去的 —— 它不归 Scaffold 管，位置/层级/点击都会被内容层影响。
        require(has(subPage, "floatingActionButton: (@Composable () -> Unit)? = null")
                        && has(subPage, "floatingActionButton = { floatingActionButton?.invoke() }"),
                "SettingsSubPage 必须把 FAB 转发给 Miuix Scaffold 的 floatingActionButton 槽位");
        require(!has(skills, "align(Alignment.BottomEnd)"),
                "技能列表的 FAB 不得再用 Box + align(BottomEnd) 手工叠在页面上："
                        + "那样它的点击与布局都不归 Scaffold 管（用户报过「加号点不了」）");
        require(has(skills, "floatingActionButton = {") && has(skills, "ZhiFloatingActionButton("),
                "技能列表必须通过 floatingActionButton 槽位挂 ZhiFloatingActionButton");

        // ---- 2c. 整屏浮层必须挂在**某个 Scaffold 里** --------------------------
        //
        // 「点加号完全没反应」的真正根因（我是实际复现之后才查清的）：
        // Miuix 的弹层不是画在哪都行 —— `DialogLayout` 只是把一个 DialogState
        // 注册进 **Scaffold 提供的**那个列表（`LocalDialogStates` /
        // `LocalRootDialogStates`，见 Miuix `Scaffold.kt` 的 CompositionLocalProvider），
        // 真正的绘制由该 Scaffold 的 `MiuixPopupHost` 负责。
        //
        // 而二级页是 `NavDisplay` 的 entry，与工作区那个 Scaffold 是**兄弟**：
        // 它们自己这一层**没有任何 Scaffold**。所以浮层写在二级页顶层就等于没宿主，
        // 点了完全没有反应、也没有任何报错。
        //
        // 判据直接查机制本身（不再用括号配平那种脆弱写法）：
        //   · SettingsSubPage 必须有 overlay 挂载点，且在它自己的 Scaffold 内调用；
        //   · 技能页的浮层必须从 overlay 传入，不能在顶层自己画。
        require(has(subPage, "overlay: (@Composable () -> Unit)? = null")
                        && has(subPage, "overlay?.invoke()"),
                "SettingsSubPage 必须提供 overlay 挂载点并在自己的 Scaffold 里调用它："
                        + "Miuix 的弹层靠 Scaffold 提供宿主，而二级页那一层没有 Scaffold");
        require(has(skills, "overlay = {") && has(skills, "AddSkillSheet("),
                "技能页的浮层必须通过 SettingsSubPage 的 overlay 传入（那里才有 Scaffold 宿主）");
        require(has(skills, "SkillCreateDialog("),
                "「手动添加」必须是对话框（SkillCreateDialog），不是新开一层页面");
        require(!has(skills, "SettingsPageKey(\"skills.create\""),
                "「手动添加」不得再占一层页面栈：两个字段的表单开一整页没有意义");
        // 官方 demo 的形态是一个 Card 包住若干行；每行各套一张卡会碎成一堆便签。
        require(has(skills, "CardDefaults.defaultColors(color = scheme.secondaryContainer)"),
                "sheet 里的 Card 必须显式给 secondaryContainer：sheet 背板是 background，"
                        + "而 Card 默认色是 surfaceContainer —— 暗色下是同一个值（#242424），"
                        + "不传就是一张看不见的卡");

        // ---- 3. 技能文件名不得逃出技能目录 -----------------------------------
        //
        // ⚠️ 锚点必须落在**那个 require 调用**上，不能只查"文件里提到过 canonicalPath"：
        // `val root = dir.canonicalPath` 那一行本身就含这个词，
        // 把真正的校验删掉、断言照样是绿的（实测如此）。
        require(has(store, "require(target.canonicalPath.startsWith(root))"),
                "fileInSkillDir 必须用 canonicalPath 前缀比对确认文件没跑出技能目录："
                        + "只靠字符集正则挡不住新的写法，而这条路径由界面输入框直接驱动");
        require(has(store, "require(isValidFileName(fileName))"),
                "fileInSkillDir 必须先做名字校验，再做路径比对（两道防线都要在）");
        // 只允许 fileInSkillDir **自己**拼路径。别处再出现一次 `File(dir, …)`
        // 就等于把校验放到了一边 —— 而那种漏法在正常使用下完全看不出来。
        require(countOf(store, "File(dir, fileName)") == 1
                        && countOf(store, "File(dir, FILE_NAME)") == 0,
                "不得绕过 fileInSkillDir 直接 File(dir, …)：路径解析必须只有一处");
        // ⚠️ 计数要算上**定义那一行**：`private fun fileInSkillDir(...)` 本身也算一次。
        // 所以「定义 + 读 + 写 + 判重 + 列表 + 新建」= 6。
        // 这个阈值前后写错过两次（4 / 5），每次都是删掉一条真实调用后仍然能过 ——
        // 校准办法是 `grep -c` 数一遍实际值，不要凭记忆推。
        require(countOf(store, "fileInSkillDir(") >= 6,
                "fileInSkillDir 必须被读 / 写 / 判重 / 列表 / 新建五条路径都用到，实际只用了 "
                        + (countOf(store, "fileInSkillDir(") - 1) + " 次");

        // ---- 4. 手动添加：名字是用户**看得见、改得动**的 ------------------------
        //
        // 形状照参考图：文件名 + 内容 + 取消/创建，做成**对话框**。
        // 早先的版本让名字由内容的 frontmatter 隐式决定（解析不出来就不让提交），
        // 用户改主意只能重来；现在名字是表单里的一个字段。
        //
        // 仍然要守住的两条：
        //   · 名字必须过与目录名同一套校验（否则会建出引擎拒绝加载的目录）；
        //   · 解析 frontmatter 的能力还在（导入文件时用来**预填**名字）。
        require(has(models, "data class SkillCreateDraft(")
                        && has(models, "val fileName: String = \"\"")
                        && has(models, "val content: String = \"\""),
                "SkillCreateDraft 必须是「文件名 + 内容」（对话框的两个字段）");
        require(has(models, "val saveable: Boolean get() = fileName.isNotBlank() && nameError == null")
                        && has(models, "fileName == \".\" || fileName == \"..\""),
                "提交资格必须要求文件名合法，且挡住 `.` / `..` 这两个名字");
        require(has(store, "fun nameFromContent(") && store.contains("frontMatterBlock"),
                "解析 frontmatter 的能力必须保留（导入文件时用它预填技能名）");
        require(has(viewModel, "val suggested = SkillStore.nameFromContent(text).orEmpty()"),
                "从文件导入必须**预填**解析出的名字，而不是直接建目录");
        require(skills.contains("从文件导入") && skills.contains("pickSkillFile.launch("),
                "「添加技能」选择表必须提供从文件导入这条路，并且真的拉起选择器"
                        + "（不做 GitHub 导入：要联网 + 解压，与离线构建、不加依赖冲突）");
        require(!skills.contains("GitHub"),
                "本工程不联网构建，技能页不得出现 GitHub 导入");

        // ---- 5. 详情页必须真的列多文件 ---------------------------------------
        // 这是这次重做的核心能力：技能不只有 SKILL.md，参考文档要能列出来、点进去编辑。
        // 「详情页退化成只有一份 SKILL.md 的编辑器」不会编译失败，只是能力悄悄没了。
        require(has(skills, "detail.files.forEach")
                        && has(skills, "onEdit(detail.entry, file.name)")
                        && has(skills, "file.primary"),
                "技能详情页必须列出该技能目录下的**所有**文件，并支持逐个点进编辑器");
        require(has(store, "fun listFiles(") && has(store, "it.name != FILE_NAME"),
                "SkillStore.listFiles 必须把 SKILL.md 排在最前（它是本体，不能淹没在附件里）");

        System.out.println("SkillsPageStructureTest PASS");
    }
}
