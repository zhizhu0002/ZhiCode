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
 * <p>这些「改坏了不会编译失败」的事：
 *
 * <ol>
 *   <li><b>同一个文件里有多态二级页时，必须走 {@code SettingsPageStack}。</b>
 *       这是「三级窗口动画」唯一真正有牙的守卫。以前列表 / 表单 / 编辑器三态是
 *       一个 {@code when} 里的内容硬切，而 {@code AppScaffold} 的页面栈只反映
 *       「这一页开着没有」—— 栈深度不变，整页转场就不会触发，于是**一点动画都没有**，
 *       而且不会有任何编译或运行期症状。</li>
 *   <li><b>技能目录里的文件路径不得逃出技能目录。</b>这条路径是由界面输入框直接驱动的
 *       （「新建文件」的对话框，且现在**允许子目录**），只做字符集校验的代码在遇到新写法时
 *       很容易失效，所以真正的边界是 {@code canonicalPath} 前缀比对，而且必须**逐段**比对。</li>
 *   <li><b>{@code SKILL.md} 里的 {@code name} 必须等于技能名（目录名）。</b>
 *       技能目录名才是引擎定位技能的键；两者不一致之后，界面上的名字与 Agent 找的目录
 *       不是一回事，而用户完全看不出为什么。</li>
 *   <li><b>详情页的文件列表必须是**递归树**。</b>退化成平铺一层不会编译失败，
 *       只是子目录里的文件从此在界面上不存在了。</li>
 *   <li><b>从 URL 导入必须有体积上限与超时。</b>没有上限的话，一个指向大文件的地址
 *       就能把应用的内存吃光（而且是先吃完再判长度，等于没有限制）。</li>
 * </ol>
 */
public final class SkillsPageStructureTest {

    private static final String SRC = "app/src/main/java/com/zhizhu/zhicode/compose/";

    private static final String SUB_PAGE = SRC + "ui/settings/SettingsSubPage.kt";
    private static final String SKILLS_OVERLAY = SRC + "ui/dialogs/SkillsOverlay.kt";
    private static final String SKILL_STORE = SRC + "data/SkillStore.kt";
    private static final String SKILL_IMPORT = SRC + "data/SkillImport.kt";
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
        String imports = stripComments(read(root, SKILL_IMPORT));
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
                        && has(subPage, "floatingActionButton = {")
                        && has(subPage, "floatingActionButton?.let { fab ->"),
                "SettingsSubPage 必须把 FAB 转发给 Miuix Scaffold 的 floatingActionButton 槽位");
        require(!has(skills, "align(Alignment.BottomEnd)"),
                "技能列表的 FAB 不得再用 Box + align(BottomEnd) 手工叠在页面上："
                        + "那样它的点击与布局都不归 Scaffold 管（用户报过「加号点不了」）");
        require(has(skills, "floatingActionButton = {") && has(skills, "ZhiFloatingActionButton("),
                "技能列表必须通过 floatingActionButton 槽位挂 ZhiFloatingActionButton");
        // 详情页的 FAB（新建文件）要在向下滚动时收起来 —— 与参考实现一致。
        // 判据落在机制上（方向由滚动增量判定 + AnimatedVisibility 收放），
        // 不是"有没有写那行调用"。
        require(has(subPage, "hideFabOnScrollDown: Boolean = false")
                        && has(subPage, "val fabVisible by remember")
                        && has(subPage, "derivedStateOf")
                        && has(subPage, "AnimatedVisibility(")
                        && has(subPage, "listState.firstVisibleItemScrollOffset"),
                "SettingsSubPage 必须支持「向下滚动收起 FAB」：方向由 LazyColumn 的滚动增量判定，"
                        + "收放用 AnimatedVisibility（直接换布局会闪一下）");
        require(has(skills, "hideFabOnScrollDown = true"),
                "技能详情页的新建文件 FAB 必须开启向下滚动收起");

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
        // 示例是框里的**初始内容**（改一改就能存），不是在框下面另起一块：
        // 用户手上的是"某个技能的说明文字"，未必知道 frontmatter 那两行是必需的，
        // 把必需形状摆在光标底下比任何提示都短。
        require(has(store, "val starterTemplate: String")
                        && has(store, "name: <技能名"),
                "示例必须是 SkillStore 里的一份模板，且把必需的 frontmatter 两行摆出来");
        require(has(viewModel, "createForm = SkillCreateDraft(")
                        && has(viewModel, "content = content,")
                        && has(viewModel, "val content = SkillStore.starterTemplate"),
                "「手动添加」必须**预填**示例内容，而不是给一个空框");
        require(!has(skills, "SkillExampleBlock(") && !has(skills, "SKILL_MD_EXAMPLE"),
                "示例不得再单独占一块：它应当就是输入框里的初始内容");
        // ⚠️ 模板里的名字必须是**占位符**（过不了名字校验）：若填一个合法默认名，
        // 打开对话框点两下就会建出一个用户没打算建的技能。
        require(has(models, "val saveable: Boolean get() = name.isNotBlank() && !nameInvalid"),
                "占位符名字必须过不了校验，从而让「保存」一开始是禁用的");
        require(!has(skills, "SettingsPageKey(\"skills.create\""),
                "「手动添加」不得再占一层页面栈：只有内容的表单开一整页没有意义");
        // 官方 demo 的形态是一个 Card 包住若干行；每行各套一张卡会碎成一堆便签。
        require(has(skills, "CardDefaults.defaultColors(color = scheme.secondaryContainer)"),
                "sheet 里的 Card 必须显式给 secondaryContainer：sheet 背板是 background，"
                        + "而 Card 默认色是 surfaceContainer —— 暗色下是同一个值（#242424），"
                        + "不传就是一张看不见的卡");
        // 编辑文件改成**对话框**之后，页面栈只剩「列表 → 详情」两层，不该再有编辑器那一层。
        // 这是用户报过的「创建的时候她会再显示一个重复的」的根因（一次推两页）。
        require(!has(skills, "SettingsPageKey(\"skills.editor\""),
                "编辑文件不得再占一层页面栈（否则一次会推两页，看起来像凭空多出来一层）："
                        + "必须做成对话框");
        require(has(skills, "SkillEditDialog(")
                        && has(skills, "val editing = rememberLastNonNull(state.editing)")
                        && has(skills, "show = state.editing != null,"),
                "编辑文件必须做成常驻对话框（挂在详情页的 overlay 里才有宿主）："
                        + "`show` 由调用方传、内容用 rememberLastNonNull 兜住 —— 用 `if` 包住会丢退出动画");

        // ---- 3. 技能文件路径不得逃出技能目录 ---------------------------------
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
        // 子目录支持之后，**逐段**校验成了新的关键点：把整串 `sub/doc.md` 直接 resolve
        // 会让 `../x.md` 绕过"单段名字"那道校验。这条断言就是钉住逐段这件事。
        require(has(store, "relativePath.split('/').forEach { segment ->")
                        && has(store, "target = fileInSkillDir(target, segment)"),
                "允许子目录的路径解析必须**逐段**过 fileInSkillDir；"
                        + "直接把整串相对路径 resolve 会让 ../ 绕过单段校验");
        // ⚠️ 计数要算上**定义那一行**，而且要数**新的那个解析入口**：
        // 支持子目录之后，读 / 写 / 判重 / 删除四条路径都改成走 `fileInSkillDirByPath`
        // （它内部再逐段调用 `fileInSkillDir`）。所以「定义 + 读 + 写 + 删除 + 判重」= 5。
        // 这个阈值前后写错过两次（4 / 5），每次都是删掉一条真实调用后仍然能过 ——
        // 校准办法是 `grep -c` 数一遍实际值，不要凭记忆推。
        require(countOf(store, "fileInSkillDirByPath(") >= 5,
                "读 / 写 / 判重 / 删除都必须走逐段校验的 fileInSkillDirByPath，实际只用了 "
                        + (countOf(store, "fileInSkillDirByPath(") - 1) + " 次");

        // ---- 4. 手动添加：名字由内容解析，解析不出来不许提交 -------------------
        //
        // 形状照参考实现（rikkahub 的「添加技能」）：**一个「SKILL.md 内容」框 + 取消/保存**，
        // 没有独立的名字字段 —— 用户手上拿到的本来就是一份完整的 SKILL.md，
        // 让它自己声明名字，目录名就不会和内容里的 name 不一致。
        //
        // 守住的两条：
        //   · 解析不出来（或解析出的名字非法）时**禁止提交**，绝不回退到默认名
        //     （取第一行、取文件名会建出一个用户没打算建的目录，界面上完全看不出来）；
        //   · 从文件 / 从 URL 导入后**不直接建**，都进这个对话框让用户确认。
        require(has(models, "data class SkillCreateDraft(")
                        && has(models, "val content: String = \"\"")
                        && has(models, "val name: String = \"\""),
                "SkillCreateDraft 必须是「内容 + 解析出的名字」（对话框只有一个内容框）");
        require(has(models, "val nameMissing: Boolean get() = content.isNotBlank() && name.isBlank()")
                        && has(models, "val saveable: Boolean get() = name.isNotBlank() && !nameInvalid"),
                "提交资格必须要求名字非空且合法：解析不出名字时猜一个目录是错的");
        require(has(store, "fun nameFromContent(") && store.contains("frontMatterBlock"),
                "解析 frontmatter 的能力必须保留（名字就是从这里来的）");
        require(has(viewModel, "createForm = SkillCreateDraft(content = text, name = name)")
                        && has(viewModel, "createForm = SkillCreateDraft("),
                "从文件 / 从 URL 导入必须填进「手动添加」对话框，而不是直接建目录");
        require(skills.contains("从文件导入") && skills.contains("pickSkillFile.launch("),
                "「添加技能」选择表必须提供从文件导入这条路，并且真的拉起选择器");
        require(skills.contains("从 URL 导入") && has(skills, "SkillUrlDialog("),
                "「添加技能」选择表必须提供从 URL 导入这条路，并且真的弹出对话框");

        // ---- 5. 详情页必须是**递归文件树** -----------------------------------
        // 这是这次重做的核心能力：技能可以带子目录，参考文档要能列出来、点进去编辑。
        // 「详情页退化成平铺一层」不会编译失败，只是子目录里的文件从此在界面上不存在了。
        require(has(store, "children = buildNodes(child, relative, depth + 1, seen)"),
                "SkillStore 建树必须递归进子目录（buildNodes 要调自己）");
        require(has(skills, "is SkillFileNode.DirNode -> DirNodeRow")
                        && has(skills, "SkillTree(node.children, depth + 1, entry, onEdit, onDeleteFile)"),
                "详情页必须递归渲染整棵树（目录节点要渲染它的 children）");
        require(has(skills, "onEdit(entry, file.relativePath)") && has(skills, "file.primary"),
                "详情页的每个文件都要能点进编辑器，且技能本体要用不同颜色标出来");
        require(has(models, "val relativePath: String,"),
                "SkillFile 必须带相对路径：只用文件名的话子目录里两个 api.md 会互相覆盖");
        // 不用 walkTopDown：它会跟着目录符号链接走，一个循环链接就能让界面无限递归
        // （而且是在主线程上）。深度与数量上限也是必须的，技能目录是用户可写的。
        require(!has(store, "walkTopDown") && has(store, "TREE_MAX_DEPTH") && has(store, "TREE_MAX_FILES"),
                "建树不得用 walkTopDown（会跟着目录符号链接走），且必须有深度/数量上限");

        // ---- 6. SKILL.md 的名字必须与技能名一致 ------------------------------
        // 技能目录名才是引擎 SkillTool 定位技能的键，内容里的 name 只是文本。
        // 不一致之后，界面上的名字与 Agent 找的目录不是一回事，用户完全看不出为什么。
        require(has(store, "fun nameConflict(skillName: String, content: String): String?"),
                "必须有一处「SKILL.md 的 name 与技能名是否一致」的判定（参考实现也挡这一条）");
        require(has(store, "if (relativePath == FILE_NAME) {")
                        && has(store, "nameConflict(skillName, body)?.let { throw IllegalArgumentException(it) }"),
                "写 SKILL.md 时必须先过名字一致性校验再落盘："
                        + "否则界面上技能叫 A、内容说自己是 B，Agent 会去找一个不存在的目录");
        require(has(viewModel, "nameError = nameErrorFor(editing.relativePath, editing.name, body)"),
                "编辑内容时每次改动都要重算名字冲突：对话框要能**实时**提示 name 对不上");
        require(has(skills, "enabled = target.saveable"),
                "名字对不上时必须禁用保存（而不是等点了保存才报错）");

        // ---- 7. SKILL.md 不能单独删 ------------------------------------------
        // 删掉目录还在，列表里就会出现一个"看得见但打不开"的幽灵技能。
        require(has(store, "require(relativePath != FILE_NAME) {")
                        && store.contains("SKILL.md 是技能本体，不能单独删除"),
                "SkillStore.deleteFile 必须拒绝删除 SKILL.md（它是技能本体）");
        require(has(skills, "if (!file.primary) {") && has(skills, "ZhiIcons.delete"),
                "详情页不得给 SKILL.md 删除按钮：一个必然失败的按钮不该存在");

        // ---- 8. 从 URL 导入：体积上限 + 超时 + 分流 ---------------------------
        // 网络 + 解压是外部输入，任何一处不设限都会变成"点一下就把应用卡死/吃光内存"。
        require(has(imports, "if (total > limit) throw IllegalStateException"),
                "下载必须**边读边计数**、超限立刻断开");
        require(!has(imports, "readBytes()"),
                "不得先读完再判长度：那样限制形同虚设 —— 内存已经在判定之前被吃光了");
        require(has(imports, "connectTimeout = CONNECT_TIMEOUT_MS")
                        && has(imports, "readTimeout = READ_TIMEOUT_MS"),
                "必须设连接与读取超时：一个不回包的服务器能把协程挂到天荒地老");
        require(has(imports, "require(isHttpUrl(text))")
                        && has(imports, "require(status == HttpURLConnection.HTTP_OK)"),
                "必须挡掉非 http(s) 与 HTTP 非 200（file:/content: 会让它变成任意本地读）");
        // ⚠️ 锚点必须连**抛出**那一段一起查：只查 `if (entries > MAX_ZIP_ENTRIES)` 的话，
        // 把函数体换成空块（校验名存实亡）断言依然是绿的 —— 实测过。
        require(has(imports, "if (entries > MAX_ZIP_ENTRIES) { throw IllegalStateException(")
                        && has(imports, "readAllBounded(zip, MAX_SKILL_BYTES)"),
                "zip 解包同样要有上限（条目数与单个条目大小），且超限要真的抛："
                        + "zip bomb 就是从这里进来的");
        require(has(viewModel, "SkillImport.looksLikeZip(bytes)")
                        && has(viewModel, "SkillImport.looksLikeText(bytes)"),
                "必须区分 zip / 文本 / 都不是：二进制内容塞进内容框只会让用户看到一屏乱码");
        require(has(imports, "fun skillsFromZip(") && has(imports, "equals(SKILL_FILE, ignoreCase = true)"),
                "zip 里要认出任意深度的 SKILL.md（仓库压缩包的第一层永远是 repo-main/）");

        require(has(store, "fun list(") && has(store, "if (!seen.add(dir.canonicalPath))") && has(store, "val seen = mutableSetOf<String>()"),
                "SkillStore.list 必须按规范路径去重：默认项目目录就是 $HOME，"
                        + "两个作用域会指向同一个文件夹，不去重同一个技能会被列成两条"
                        + "（磁盘上只有一个文件，删一条会把另一条也弄没）");

        System.out.println("SkillsPageStructureTest PASS");
    }
}
