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

        // ---- 2. 页面栈本身的两个不能省的细节 ---------------------------------
        require(has(subPage, "rememberSaveableStateHolder()")
                        && subPage.contains("SaveableStateProvider("),
                "SettingsPageStack 必须用 SaveableStateHolder 保留各页状态："
                        + "AnimatedContent 会销毁离场页，列表的滚动位置会丢（回到最顶上）");
        // holder 必须在 AnimatedContent **之前**求值，否则它自己也活不过页面切换。
        require(subPage.indexOf("rememberSaveableStateHolder()") < subPage.indexOf("AnimatedContent("),
                "rememberSaveableStateHolder 必须在 AnimatedContent 外面（源码顺序上先出现）");
        require(has(subPage, "BackHandler(enabled=current.depth>0")
                        && has(subPage, "BackHandler("),
                "SettingsPageStack 必须用 BackHandler 让系统返回先退回上一层："
                        + "少了它，三级页按返回会直接关掉整个二级页");

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

        // ---- 4. 手动添加：名字从内容解析，解析不出就不许提交 ------------------
        //
        // 同样要锚在**函数体**上：只查"文件里提到过 frontMatterBlock"没用 ——
        // `describe()` 也在用它，把 nameFromContent 改成全文扫描照样绿（实测如此）。
        require(has(store, "fun nameFromContent(content: String): String? = frontMatterBlock(content)"),
                "技能名必须从 SKILL.md 的 frontmatter 块里解析，不能全文扫描正文里的 name:");
        require(has(skills, "draft.nameMissing ->")
                        && has(skills, "action=\"创建\"to(if(draft.saveable)onCreateelse null)"),
                "「手动添加」必须在解析不出名字时明确报错**并禁用提交**："
                        + "回退到默认名会建出用户没打算建的目录");
        // 提交资格本身也留在模型里，界面上的报错与它必须同步。
        require(has(models, "val saveable: Boolean get() = name.isNotBlank() && !nameInvalid"),
                "SkillCreateDraft.saveable 必须要求有合法名字，不能无论内容如何都能提交");
        // ⚠️ 锚在**用户看得见的那两项**与**真的拉起选择器**上，不能只查标识符
        // `onImportFile`：把它改名成 `onImportFileGone` 时，旧断言照样是绿的
        // （子串匹配），于是"这条路被删掉了"这件事完全没被守住。
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
