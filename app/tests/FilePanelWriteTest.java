import java.nio.file.*;
import java.util.*;

/**
 * 文件面板「可读写」的守卫（用户：「挂载 home 目录（需要可读写）」）。
 *
 * <p>这一屏原先的源码注释就是「本期为只读浏览 + 只读查看，不做写入」，
 * 而且根被钉死在项目目录上（`rootPath() = projectPath`）—— HOME 与共享存储
 * 都走不到，「上一级」也会被弹回项目顶部。
 *
 * <p>守这条的理由：**退化成只读不会编译失败**。删掉编辑按钮、把根写死，
 * 代码照样编译、界面照样能用，只是用户再也改不了文件 —— 而这一点只有
 * 真去改的时候才发现。
 *
 * <p>本轮（按小米文件管理器的信息层级重做文件页）新增的判据：
 * <ol>
 *   <li>面包屑行**只有一个**新建入口（`+`），且两个提交按钮传的是不同的值；</li>
 *   <li>建文件还是建文件夹由**按钮**决定，不再靠 `name.contains('.')` 猜
 *       （Makefile / LICENSE / .gitignore 会被建成文件夹）；</li>
 *   <li>提交失败必须**就地**回填 `FileNameForm.failure` —— 原先只写
 *       `state.message`，而那条反馈挂在 ChatArea 上，文件页根本看不见；</li>
 *   <li>长按进选择模式：全选 / 退出 / 底部三个动作，且换目录换根要清空选择；</li>
 *   <li>列表行两行（文件名 + 大小 · 修改时间），大小/时间的格式化有纯 JVM 单测；</li>
 *   <li>空态居中、未授权有真能点的「去授权」；</li>
 *   <li>附加文件也能走系统文件管理器（SAF），且**不需要**任何存储权限。</li>
 * </ol>
 */
public final class FilePanelWriteTest {

    private static final String PANE =
            "app/src/main/java/com/zhizhu/zhicode/compose/ui/panes/FilesPane.kt";
    private static final String DIALOGS =
            "app/src/main/java/com/zhizhu/zhicode/compose/ui/panes/FileDialogs.kt";
    private static final String CHROME =
            "app/src/main/java/com/zhizhu/zhicode/compose/ui/panes/FileChrome.kt";
    private static final String VM =
            "app/src/main/java/com/zhizhu/zhicode/compose/state/WorkspaceViewModel.kt";
    private static final String MODELS =
            "app/src/main/java/com/zhizhu/zhicode/compose/model/UiModels.kt";
    private static final String BROWSER =
            "app/src/main/java/com/zhizhu/zhicode/compose/data/FileBrowser.kt";
    private static final String COMMON =
            "app/src/main/java/com/zhizhu/zhicode/compose/ui/Common.kt";
    private static final String COMPOSER =
            "app/src/main/java/com/zhizhu/zhicode/compose/ui/composer/Composer.kt";
    private static final String CHAT_AREA =
            "app/src/main/java/com/zhizhu/zhicode/compose/ui/ChatArea.kt";
    private static final String FORMAT =
            "app/src/main/java/com/zhizhu/zhicode/compose/model/FileFormat.kt";
    private static final String FORMAT_TEST =
            "app/src/test/java/com/zhizhu/zhicode/compose/model/FileFormatTest.kt";
    private static final String LAYOUTS =
            "app/src/main/java/com/zhizhu/zhicode/compose/ui/WorkspaceLayouts.kt";
    private static final String OPS =
            "app/src/main/java/com/termux/app/zhicode/core/FileOps.java";
    private static final String OPS_TEST =
            "app/src/test/java/com/termux/app/zhicode/core/FileOpsTest.java";
    private static final String FAST_SCRIPT = "test-jvm-fast.sh";

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static String read(String root, String relative) throws Exception {
        return new String(Files.readAllBytes(Paths.get(root, relative)),
                java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String stripComments(String text) {
        String noBlock = text.replaceAll("(?s)/\\*.*?\\*/", " ");
        return noBlock.replaceAll("(?m)//[^\\n]*", " ");
    }

    private static String squash(String text) {
        return text.replaceAll("\\s+", "");
    }

    private static void requireContains(String haystack, String needle, String message) {
        require(haystack.contains(needle), message + "\n（找不到：" + needle + "）");
    }

    private static void requireAbsentIn(String haystack, String needle, String message) {
        require(!haystack.contains(needle),
                message + "\n（不该出现：" + needle + "）");
    }

    /**
     * 从 `anchor` 起往后 [span] 个字符的窗口。
     *
     * <p>用于**只钉住某一个调用点**：`contains(x)` 只看"文件里有没有 x"，
     * 而同一个文件里往往有同名函数的**定义**与**调用** —— 于是"改坏调用点"
     * 照样通过（本轮 teeth 实测踩到 4 次：`readBounded`、`documentDisplayName`
     * 都是定义还在、调用被换掉，断言无感）。
     * 精确到"标签/锚点之后的那一小段"才分辨得出是谁在用。
     */
    private static String windowAfter(String source, String anchor, int span) {
        int at = source.indexOf(anchor);
        if (at < 0) return "";
        return source.substring(at, Math.min(source.length(), at + span));
    }

    private static int countOf(String source, String needle) {
        int n = 0, at = 0;
        while ((at = source.indexOf(needle, at)) >= 0) { n++; at += needle.length(); }
        return n;
    }

    /**
     * 取**一次函数调用的实参文本**：从 `anchor` 里的 `(` 起按**括号**配对找到相配的 `)`。
     *
     * <p>⚠️ 这里不能用 [bodyOf]（花括号配对）。`bodyOf` 找的是锚点之后的第一个 `{`，
     * 而调用里往往**先出现一个 lambda 实参** —— 于是它配到那个 lambda 的 `}` 就返回，
     * 切片里只剩 `{ if (...) ... }` 这一小段。
     * teeth §37 实测：想在 `FileListRow(...)` 里加回 `trailing = { … }` 时，
     * 用 `bodyOf` 切片的断言看不到它，**MISS**。
     */
    private static String callArgsOf(String source, String anchor) {
        int at = source.indexOf(anchor);
        if (at < 0) return "";
        int open = source.indexOf('(', at);
        if (open < 0) return "";
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) return source.substring(open, i + 1);
            }
        }
        return "";
    }

    /**
     * 取某个函数/分支的正文：从锚点起，按花括号配对找到它的结尾。
     *
     * <p>⚠️ 锚点必须选在**函数体开头之前**、且函数签名里**没有** `{}`
     * 默认参数 —— 花括号配对会被参数默认值里的那对 `{}` 提前满足，
     * 于是返回一段错的正文而断言"过了"（`DebugHudStructureTest.bodyOf` 的注释里
     * 记着这个坑）。本类用到的三个锚点都满足这个条件。
     */
    private static String bodyOf(String source, String anchor) {
        int at = source.indexOf(anchor);
        if (at < 0) return "";
        int open = source.indexOf('{', at + anchor.length() - 1);
        if (open < 0) return "";
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return source.substring(open, i + 1);
            }
        }
        return "";
    }

    public static void main(String[] args) throws Exception {
        String root = args.length > 0 ? args[0] : ".";
        String rawPane = read(root, PANE);
        String pane = stripComments(rawPane);
        String vm = stripComments(read(root, VM));
        String models = stripComments(read(root, MODELS));
        String ops = stripComments(read(root, OPS));
        String dialogText = stripComments(read(root, DIALOGS));
        String chrome = stripComments(read(root, CHROME));
        String common = stripComments(read(root, COMMON));
        String layouts = stripComments(read(root, LAYOUTS));
        String composer = stripComments(read(root, COMPOSER));
        String chatArea = stripComments(read(root, CHAT_AREA));
        String sqVm = squash(vm);
        String sqPane = squash(pane);
        String sqDialog = squash(dialogText);

        // ---- 1. 写操作齐全：新建 / 重命名 / 删除 / 保存 -------------------
        for (String op : new String[]{
                "fun newFileForm(", "fun renameForm(", "fun deleteSelectedEntries(",
                "fun confirmDelete(", "fun saveFile(", "fun startEditingFile("}) {
            require(vm.contains(op),
                    VM + " 缺少 " + op + "：文件面板必须是可读写的 —— "
                            + "退回只读不会编译失败，只会在用户真去改的时候才发现");
        }
        // 写操作必须走 FileOps（名字校验、原子保存、删除不跟符号链接都在那里，
        // 且有 26 条单测）。就地写 File.writeText 会绕过全部这些。
        require(sqVm.contains("FileOps.write(File(open.path),draft)"),
                VM + " 的保存必须走 FileOps.write：它做的是「先写 .tmp 再改名」，"
                        + "中途被杀不会留下半截源文件 —— 就地 writeText 会");
        require(sqVm.contains("FileOps.createFile(") && sqVm.contains("FileOps.createDirectory(")
                        && sqVm.contains("FileOps.rename(") && sqVm.contains("FileOps.delete("),
                VM + " 的增/改/删都必须走 FileOps：名字校验（带 / 会写到别的目录去）、"
                        + "拒绝覆盖、删除不跟符号链接，全在那一处");

        // ---- 1b. 建文件还是建文件夹**由按钮决定**，不靠扩展名猜 ------------
        //
        // 原先 `newFileForm(directory)` 直接把类型定死（面包屑行上有两个按钮），
        // 提交时按 `name.contains('.')` 再猜一次 —— 于是 `Makefile`、`LICENSE`、
        // `.gitignore` 这类**没有扩展名的文件**会被建成目录：用户点的是「文件」，
        // 得到的是一个文件夹。改成弹窗之后类型只能来自被按下的那个按钮。
        require(!sqVm.contains("name.contains('.')"),
                VM + " 不得再用 name.contains('.') 猜是文件还是目录："
                        + "Makefile / LICENSE / .gitignore 会被建成文件夹，"
                        + "而用户点的是「文件」");
        require(sqVm.contains("elseif(directory){"),
                VM + " 的 submitFileNameForm 必须按 directory 形参分支："
                        + "类型来自按钮（重命名时走 target 那一支）");
        String newFormBody = bodyOf(vm, "fun newFileForm(");
        require(!newFormBody.isEmpty(), VM + " 找不到 newFileForm 的正文（改名了？）");
        // ⚠️ 签名要单独看：`bodyOf` 是从**第一个 `{`** 起算的，而参数在 `{` 之前 ——
        // 写成 `fun newFileForm(directory: Boolean)` 时函数体里当然没有 "directory"，
        // 于是 `!body.contains("directory")` 照样通过（teeth 实测 MISS 过一次）。
        require(sqVm.contains("funnewFileForm()"),
                VM + " 的 newFileForm 必须是**无参**的："
                        + "建什么由弹窗里按下的按钮决定，打开表单那一刻还不知道"
                        + "（原先它收 directory，等于在打开表单前就把类型定死了）");
        require(!sqVm.contains("funnewFileForm(directory"),
                VM + " 不得再有 newFileForm(directory)：同上");
        requireContains(newFormBody, "draft = \"\"",
                VM + " 的 newFileForm 必须把名字框**留空**："
                        + "预填「新建文件.txt」然后点「文件夹」会建出一个叫 .txt 的文件夹，"
                        + "预填「新建文件夹」然后点「文件」会建出一个没有扩展名的文件");
        // 留空的直接后果：那个"避开重名的建议名"助手没有用武之地了，已经删掉。
        // 钉住它不许回来 —— 没有调用者的工具函数会被后来者当成"现有能力"引用，
        // 而它连用例都没有了（重名现在是提交时由 FileOps.create 拒绝、原因回填弹窗）。
        requireAbsentIn(ops, "suggestName",
                OPS + " 不得再有 suggestName：它唯一的调用者是预填名字的旧新建表单，"
                        + "而新弹窗的名字框是空的（重名由 create 拒绝并回填失败原因）");

        // 两个提交按钮必须**都传出去**，而且传的是**不同的值**。
        require(sqDialog.contains("onSubmit(false)") && sqDialog.contains("onSubmit(true)"),
                DIALOGS + " 的「文件」/「文件夹」两个按钮必须分别 onSubmit(false) / onSubmit(true)："
                        + "两个都传同一个值（或漏传）时，界面照常出、点了也照常建 —— "
                        + "只是建出来的永远是同一种");
        requireContains(dialogText, "text = \"文件\",", DIALOGS + " 必须有「文件」这个提交按钮");
        requireContains(dialogText, "text = \"文件夹\",", DIALOGS + " 必须有「文件夹」这个提交按钮");
        // ⚠️ 逐个按钮看它**自己**有没有门控。
        // 写成 `requireContains(dialogText, "enabled = shown.saveable,")` 是没用的：
        // 文件里本来就有三处（文件 / 文件夹 / 确定），删掉其中一处还剩两处，
        // 断言照样通过（teeth 实测 MISS 过一次）。
        for (String label : new String[]{
                "text = \"文件\",", "text = \"文件夹\",", "text = \"确定\","}) {
            String window = windowAfter(dialogText, label, 240);
            require(!window.isEmpty(), DIALOGS + " 找不到提交按钮 " + label);
            require(window.contains("enabled = shown.saveable,"),
                    DIALOGS + " 的 " + label + " 按钮必须按 saveable 门控："
                            + "名字非法时还能点，用户只会得到一句「已经存在」而不知道为什么");
        }

        // ---- 2. 根可切换：HOME / 共享存储 ----------------------------------
        //
        // ⚠️ 本轮删掉了第三档「项目」。它指向设置里的项目路径，而那个路径默认就是
        // HOME —— 于是两个按钮指向同一个目录（用户截图里点了「项目」，面包屑却停在
        // `home`），纯冗余。所以这里从三档改成两档，并**反过来**钉住它不许回来：
        // 它回来的唯一方式就是有人又把 projectPath 当成一个独立的位置。
        require(models.contains("enum class FileRoot"),
                MODELS + " 必须有 FileRoot：两个根是两种不同的活儿"
                        + "（HOME=代码与配置、共享存储=用户的文件）");
        for (String tier : new String[]{"HOME", "SHARED"}) {
            require(models.contains(tier), MODELS + " 的 FileRoot 缺少 " + tier + " 档");
        }
        require(!models.contains("PROJECT"),
                MODELS + " 的 FileRoot 不该再有 PROJECT 档：它默认与 HOME 是同一个目录，"
                        + "两个入口指向同一处只会让人怀疑自己点错了");
        require(!sqVm.contains("privatesuspendfunrootPath"),
                VM + " 的 rootPath 不该是常量：它原先恒等于 projectPath，"
                        + "于是 HOME 与共享存储都走不到");
        require(sqVm.contains("when(_state.value.fileRoot)"),
                VM + " 的 rootPath 必须按 fileRoot 分支："
                        + "写死一个根的话，用户在界面上换根只是换了个高亮");
        require(vm.contains("StorageLinks.EXTERNAL_ROOT"),
                VM + " 的共享存储根必须取自 StorageLinks.EXTERNAL_ROOT："
                        + "再写一份字面量迟早与 ~/storage 那套链接不一致");
        require(sqVm.contains("funswitchFileRoot(root:FileRoot)"),
                VM + " 必须有 switchFileRoot：切根时还要**回到该根顶层**并清掉打开的文件，"
                        + "否则会停在上一个根里的路径上");

        // ---- 3. UI 上是真的能操作 -----------------------------------------
        for (String callback : new String[]{
                "onStartEdit", "onSave", "onCancelEdit", "onNewEntry",
                "onRename", "onConfirmDelete", "onSwitchRoot"}) {
            require(pane.contains(callback),
                    PANE + " 缺少 " + callback + "：写操作必须在界面上真的有入口，"
                            + "只在 ViewModel 里有函数等于没有");
        }
        require(pane.contains("ZhiTextField("),
                PANE + " 的编辑态必须能真的输入（ZhiTextField）："
                        + "只显示正文而没有输入控件等于还是只读");
        require(dialogText.contains("ZhiFieldError("),
                DIALOGS + " 的名字表单错误行必须走 ZhiFieldError："
                        + "标题下方那一条要与其他表单「有错才出现、左边缘对齐」一致");

        // ---- 3b. 只有一个新建入口（面包屑行上的 `+`） ----------------------
        //
        // 原先那一行并排放着「新建文件」「新建文件夹」两个图标 —— 占地方、
        // 两个图标看不出区别，而小米文件管理器那一行只有一个
        // （`res/layout/phone_file_explorer_list.xml` 的 `@id/action_create`，
        // contentDescription 就是「新建」）。用户的要求也是「合并到一起，用 + 表示」。
        String listBranch = bodyOf(pane, "FileStage.LIST -> Column(modifier = Modifier.fillMaxSize())");
        require(!listBranch.isEmpty(), PANE + " 找不到列表分支的正文（写法变了？）");
        requireContains(listBranch, "icon = ZhiIcons.add,",
                PANE + " 的面包屑行必须用一个 `+`（ZhiIcons.add）作为唯一的新建入口");
        requireContains(listBranch, "description = \"新建\",",
                PANE + " 的 `+` 必须自述为「新建」：contentDescription 是读屏用户唯一的线索");
        requireAbsentIn(listBranch, "\"新建文件\"",
                PANE + " 不得再留「新建文件」这个独立按钮：它与「新建文件夹」已经合成一个 +");
        requireAbsentIn(listBranch, "\"新建文件夹\"",
                PANE + " 不得再留「新建文件夹」这个独立按钮：同上");

        // 两个界面的行依旧必须**共用同一份实现**（不是"长得像"）。
        require(!pane.contains("private fun FileListRow("),
                PANE + " 不得自己再写一份 FileListRow：共用件在 FileChrome.kt"
                        + "（那一行的两行信息层级在两个界面必须一致）");

        // ---- 3c. 提交失败必须**就地**说明原因 -------------------------------
        //
        // 原先失败只写 `state.message`，而 `MessageBar` 挂在 ChatArea 里 ——
        // 在文件页上提交失败时界面上什么都没出现，弹窗还照常关掉，
        // 看起来像"建成功了但列表里没有"。小米的 textinput_dialog 也是同样的形状
        // （输入框下面一行默认 gone 的错误行）。
        require(models.contains("val failure: String? = null"),
                MODELS + " 的 FileNameForm 必须有 failure 字段（提交失败的原因）");
        require(sqVm.contains("copy(failure=error)"),
                VM + " 提交失败时必须把原因回填到表单（copy(failure = error)），"
                        + "而不是只写 state.message —— 那条反馈在文件页上根本看不见");
        require(sqVm.contains("failure=null"),
                VM + " 改名字（updateFileNameDraft）时必须清掉上一次的 failure："
                        + "不清的话用户已经换成另一个名字了，看到的还是上一个错");
        requireContains(dialogText, "touched = form.draft.isNotEmpty() || form.failure != null",
                DIALOGS + " 的错误行必须在「打过字」或「失败过」时才飘红："
                        + "新建弹窗一打开名字框是空的，无条件显示就是"
                        + "「一打开表单就一片红字」，而 FieldErrorAlignmentTest 正是守这个的");

        // ---- 3d. 长按多选（对齐小米的选择模式） ----------------------------
        String selectHeader = bodyOf(pane, "if (selectionOn) {");
        require(!selectHeader.isEmpty(), PANE + " 找不到选择模式标题那一支（写法变了？）");
        requireContains(selectHeader, "\"已选择 ${selection.size} 项\"",
                PANE + " 的选择模式必须说清选了几项");
        requireContains(selectHeader, "description = if (allSelected) \"取消全选\" else \"全选\",",
                PANE + " 的选择模式必须有「全选 / 取消全选」："
                        + "只靠一条一条点，选 50 个文件要点 50 次");
        requireContains(selectHeader, "description = \"退出选择\",",
                PANE + " 的选择模式必须有「退出选择」："
                        + "否则用户只能靠删光选择来退出，而选择模式里点一行是勾选、不是打开");
        requireContains(listBranch, "onLongPress = { onLongPressEntry(entry) },",
                PANE + " 长按一行必须进选择模式");
        requireContains(listBranch, "onOpen = { if (selectionOn) onToggleEntry(entry) else onOpen(entry) },",
                PANE + " 选择模式下点一行必须是勾/取消勾，不能再打开："
                        + "否则用户进了选择模式一点就跳进别的目录，那份选择全白费");
        require(listBranch.contains("canRename = selection.size == 1,"),
                PANE + " 「重命名」必须只在恰好选中一条时可用："
                        + "一批文件改成同一个名字没有意义");
        require(listBranch.contains("onAttach = onAttachSelected,"),
                PANE + " 底部操作栏必须有「附加到对话」—— 一次挑几个文件比在附件面板里点好几次快");
        require(listBranch.contains("onDelete = onDeleteSelected,"),
                PANE + " 底部操作栏必须有「删除」");

        // 文件行的**行尾不许再挂常驻动作**（这是用户截图上最杂的一处：
        // 一屏十几行就有二十几个图标把文件名挤成省略号）。
        // 动作已经搬进选择模式的底部栏。
        //
        // ⚠️ 只能用 callArgsOf（**括号**配对）取这次调用的实参：
        // `bodyOf`（花括号配对）会先撞上 `onOpen = { … }` 那个 lambda，
        // 在它的 `}` 就返回 —— 切片里根本到不了后面的 `trailing`（teeth 实测 MISS 过）。
        String rowCall = callArgsOf(pane, "FileListRow(");
        require(!rowCall.isEmpty(), PANE + " 找不到 FileListRow 的调用（写法变了？）");
        requireContains(rowCall, "modifier = Modifier.animateItem(),",
                PANE + " 取到的必须真的是 FileListRow 那次调用（切片起点错了？）");
        requireAbsentIn(rowCall, "trailing",
                PANE + " 的文件行不得再挂行尾动作："
                        + "整行点击＝打开/勾选，重命名与删除在选择模式的底部栏里 —— "
                        + "每行两个图标会把文件名挤成省略号（用户截图那一处）");

        // 选择状态必须按**路径**存，且换目录/换根时清空。
        require(models.contains("val fileSelection: Set<String> = emptySet()"),
                MODELS + " 的选择必须按路径存（Set<String>）而不是存 FileEntry："
                        + "列表刷新后每条都是新对象，存对象的话选择会莫名其妙地丢掉");
        requireContains(bodyOf(vm, "fun navigateTo("), "fileSelection = emptySet()",
                VM + " 的 navigateTo 必须清空选择："
                        + "选择存的是路径，进了别的目录之后那些路径指的是另一批文件 —— "
                        + "不清的话会在新目录里「删除」用户根本没选的东西");
        requireContains(bodyOf(vm, "fun switchFileRoot("), "fileSelection = emptySet()",
                VM + " 的 switchFileRoot 必须清空选择（同上，而且跨根更明显）");
        requireContains(bodyOf(vm, "fun confirmDelete()"), "fileSelection = s.fileSelection - removed",
                VM + " 删除成功后必须把被删的路径从选择里摘掉："
                        + "摘掉之后集合空了，选择模式自然结束；不摘的话它一直开着");

        // 删除确认的条数必须按**条**累加（多选），而不是只看第一条。
        require(models.contains("val entries: List<FileEntry>"),
                MODELS + " 的 FileDeletePrompt 必须带一组 entries：多选删除要一次说清删的是哪几条");
        require(sqVm.contains("entries.sumOf{FileOps.countForDelete(File(it.path))}"),
                VM + " 的删除确认必须把每一条的 countForDelete 累加："
                        + "只看第一条的话，删五个目录只会报其中一个的代价");

        // ---- 4. 删除确认必须说清代价 -------------------------------------
        require(models.contains("val count: Int"),
                MODELS + " 的 FileDeletePrompt 必须带 count（会一起消失的条数）："
                        + "只说「确定删除 sub 吗？」等于没告诉用户删目录会带走里面的全部内容");
        require(sqVm.contains("FileOps.countForDelete(File(it.path))"),
                VM + " 的删除确认必须用 FileOps.countForDelete 算条数");
        require(dialogText.contains("会一起消失"),
                DIALOGS + " 的删除确认必须把代价写出来（「其中的 N 项内容会一起消失」）");

        // ---- 5. 删掉当前打开的文件时必须关掉编辑器 ------------------------
        // 注意：这里是**已经压掉空白**的写法（和 sqVm 同一口径），别往里加空格。
        require(sqVm.contains("openFile=if(s.openFile?.pathinremoved)null"),
                VM + " 在删掉的正是当前打开的文件时必须把 openFile 置空："
                        + "否则面板继续显示一份已经不存在的文件的正文，"
                        + "再点保存会把它**建回来**");
        require(sqVm.contains("valremoved=targets.map{it.path}.toSet()"),
                VM + " 删除后要按**被删的那一组路径**判断（removed 集合），"
                        + "不能只看第一条 —— 多选删除时漏判会让编辑器停在一个已消失的文件上");

        // ---- 6. 二进制/读失败的预览不许进入编辑态 -------------------------
        require(sqVm.contains("isEditablePreview("),
                VM + " 必须用 isEditablePreview 门控编辑："
                        + "FileBrowser.read 对二进制返回的是「（二进制文件，N 字节…）」"
                        + "这句**给人看的提示**，把它当正文保存回去会把文件写坏");
        for (String marker : new String[]{"（二进制文件，", "（读取失败"}) {
            require(vm.contains(marker),
                    VM + " 的 isEditablePreview 必须拦住「" + marker + "」这类预览");
        }

        // 空文件也必须能编辑 —— 用“内容为空”当判据会把新建的空文件锁死。
        require(sqVm.contains("it.copy(fileDraft=open.content)"),
                VM + " 进入编辑态应当直接把正文放进 draft："
                        + "不能用「内容非空」当条件，否则新建的空文件永远编辑不了");

        // ---- 6b. 列表行：两行信息层级 + 大小/时间的格式化 -------------------
        //
        // 「大小 · 修改时间」这一行原先只有字节数，而时间戳与格式化是纯逻辑，
        // 错了不会崩：少一档大小就永远显示「1234.5 KB」，差一天就把「昨天 23:10」
        // 写成「今天」。所以它有一份**纯 JVM 单测**（FileFormatTest）钉具体输出。
        require(models.contains("val modifiedAt: Long = 0L"),
                MODELS + " 的 FileEntry 必须带 modifiedAt：第二行要显示修改时间");
        require(stripComments(read(root, BROWSER)).contains("file.lastModified()"),
                BROWSER + " 的 children 必须真的取 lastModified()："
                        + "字段存在但从没被填过的话，那一行永远只有大小");
        requireContains(chrome, "internal fun fileInfoLine(",
                CHROME + " 的列表行必须提供 fileInfoLine（两行的第二行）");
        // ⚠️ 光有定义不够 —— 必须**调用**它。
        // teeth 实测：把调用点改成 `val info = ""` + `if (false)` 时，
        // 只断言"定义存在"的写法毫无感觉（定义还在），而界面上第二行整行消失。
        requireContains(chrome, "val info = fileInfoLine(entry, now)",
                CHROME + " 的行必须真的调用 fileInfoLine 并把结果画出来："
                        + "只留一个没人调用的定义，等于第二行根本没做");
        requireContains(chrome, "if (info.isNotEmpty()) {",
                CHROME + " 的第二行必须按 isNotEmpty 门控（目录只有一行小字时不留空行）");
        requireContains(chrome, "FileFormat.size(entry.size)",
                CHROME + " 的列表行必须走 FileFormat.size（唯一一份大小格式化）");
        require(Files.exists(Paths.get(root, FORMAT)),
                FORMAT + " 必须存在：大小与时间的格式化要能被纯 JVM 单测覆盖");
        require(Files.exists(Paths.get(root, FORMAT_TEST)),
                FORMAT_TEST + " 必须存在：显示格式只有单测钉得住");
        require(read(root, FORMAT_TEST).contains("今天"),
                FORMAT_TEST + " 必须钉住「今天 / 昨天 / 同年 / 往年」四档");
        require(read(root, FAST_SCRIPT).contains("FileFormatTest"),
                FAST_SCRIPT + " 必须把 FileFormatTest 挂在快路径里");
        // 旧的两份重复实现不许回来（它们字节完全相同，是「三处使用者」那句话的反例）。
        requireAbsentIn(common, "fun zhiFormatSize(",
                COMMON + " 不得再有 zhiFormatSize：它已经收成 FileFormat.size 一份");
        requireAbsentIn(chrome, "fun formatFileSize(",
                CHROME + " 不得再有 formatFileSize：同上，两份实现迟早只有一处被改");

        // ---- 6c. 空态 / 未授权态：图标 + 一行说明 + 去授权入口 --------------
        requireContains(chrome, "internal fun fileCountSummary(",
                CHROME + " 必须提供 fileCountSummary（「文件夹 N · 文件 M」）："
                        + "两处（文件面板 / 附加面板）的同一行必须显示同一串字符");
        require(listBranch.contains("fileCountSummary(entries)"),
                PANE + " 的路径行必须把这一层的条数显示出来："
                        + "一屏只放得下七八行，用户需要知道还有多少在下面");
        require(listBranch.contains("EmptyDirectoryNote("),
                PANE + " 的空目录必须走居中的空态（图标 + 一行说明）："
                        + "原先是一行小字挂在列表底部，看起来像「列表还没加载完」");
        require(listBranch.contains("ZhiNoticeCard("),
                PANE + " 共享存储没授权时必须有说明卡片");
        requireContains(listBranch, "onAction = onGrantSharedStorage,",
                PANE + " 共享存储没授权时那个按钮必须真的能点："
                        + "原先只有一段让用户自己去系统设置里找的文案，界面上没有出路");
        require(layouts.contains("onGrantSharedStorage = viewModel::openSharedStorageSettings"),
                LAYOUTS + " 缺接线 onGrantSharedStorage：弹窗里的按钮会什么都不做");
        requireContains(vm, "ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION",
                VM + " 的 openSharedStorageSettings 必须打开系统那一页："
                        + "只说一句「去授权」而不跳转等于没做");

        // ---- 7. 接线：布局层必须把回调真的传下去 --------------------------
        for (String wiring : new String[]{
                "onSwitchRoot = viewModel::switchFileRoot",
                "onSave = viewModel::saveFile",
                "onConfirmDelete = viewModel::confirmDelete",
                "onStartEdit = viewModel::startEditingFile",
                "onNewEntry = viewModel::newFileForm",
                "onLongPressEntry = viewModel::longPressFileEntry",
                "onToggleSelectAll = viewModel::toggleSelectAllFiles",
                "onDeleteSelected = viewModel::deleteSelectedEntries",
                "onAttachSelected = viewModel::attachSelectedEntries"}) {
            require(layouts.contains(wiring),
                    LAYOUTS + " 缺少接线 " + wiring + "："
                            + "FilesPane 加了形参而布局层不传，界面上的按钮就什么都不做");
        }

        // ---- 7b. 附加文件也能走系统文件管理器（SAF） -----------------------
        //
        // 附件在引擎那边是**内联文本**（Attachment.textBody），不是路径 ——
        // 所以不需要把文件复制进项目、也不需要任何存储权限：SAF 给一次性读授权，
        // 我们当场读、读完进内存。这也是这条入口能工作的原因（直接扫 /sdcard
        // 反而要「所有文件访问权限」）。
        require(sqVm.contains("funattachDocument(uri:android.net.Uri)"),
                VM + " 必须有 attachDocument(uri)：附加文件要能从系统文件管理器挑");
        requireContains(chatArea, "ActivityResultContracts.OpenMultipleDocuments()",
                CHAT_AREA + " 的系统文件入口必须用 OpenMultipleDocuments"
                        + "（它给的是「文档」入口；GetMultipleContents 给的是相册那一类）");
        requireContains(chatArea, "viewModel.attachDocument(it)",
                CHAT_AREA + " 必须把每个挑中的 Uri 交给 attachDocument");
        requireContains(composer, "onPickSystemFiles",
                COMPOSER + " 的 `+` 菜单必须有「文件管理器」这一项");
        requireContains(composer, "onClick = onPickSystemFiles,",
                COMPOSER + " 的那一项必须真的接上 onPickSystemFiles");
        require(sqVm.contains("DocumentAttachLimit"),
                VM + " 的 attachDocument 必须有字节上限："
                        + "没有上限时挑一个 500 MB 的视频会直接把内存吃光");
        // ⚠️ 这两条必须钉**调用点**，不能只钉函数名 ——
        // `private fun readBounded(` / `private fun documentDisplayName(` 这两个定义
        // 一直躺在文件里，所以"contains(名字)"在调用点被换掉时照样通过
        // （teeth 实测：换成 `input.readBytes()` 与 `uri.lastPathSegment` 两次都 MISS）。
        require(sqVm.contains("readBounded(input,DocumentAttachLimit)"),
                VM + " 的 attachDocument 必须真的调 readBounded(input, DocumentAttachLimit)："
                        + "换成 readBytes() 时那个上限形同虚设（挑一个大视频就吃光内存），"
                        + "而函数还留在文件里、contains 检查感觉不到");
        require(sqVm.contains("documentDisplayName(context,uri)"),
                VM + " 必须真的调 documentDisplayName(context, uri) 取显示名："
                        + "换成 uri.lastPathSegment 的话附件标签会是一串文档 id 数字");
        // 三个入口必须共用同一段"放进状态"的代码，否则截断/去重规则迟早只有一处生效。
        require(sqVm.contains("privatesuspendfunattachPath("),
                VM + " 必须把「放进状态」抽成 attachPath，供附件面板 / 选择模式批量 / SAF 三处共用："
                        + "各写一遍的话，「256 KB 截断、二进制要拒绝、同一路径去重」"
                        + "会慢慢只有一处生效");
        require(sqVm.contains("attachPath(entry.path,entry.name)"),
                VM + " 的批量附加（attachSelectedEntries）必须走同一个 attachPath");
        require(sqVm.contains("if(!attachPath(path,name))"),
                VM + " 的 attachProjectFile 也必须走同一个 attachPath："
                        + "三条入口留下一条自己写，正是上面那句要防的事");

        // ---- 8. FileOps 是纯 Java 且必须有单测 ----------------------------
        require(!ops.contains("import android."),
                OPS + " 不许 import android.*：那会让它只能靠 Gradle 单测跑，"
                        + "秒级的 test-jvm-fast.sh 就守不住「名字校验/删除不跟链接」");
        String opsTest = stripComments(read(root, OPS_TEST));
        for (String caseName : new String[]{
                "pathSeparatorsAreRejected",
                "deleteDoesNotFollowSymlinks",
                "renameRefusesToOverwriteAndKeepsOriginal",
                "createValidatesTheName",
                "countIncludesTheTargetItself",
                "writePreservesUtf8AndLineEndings"}) {
            require(opsTest.contains(caseName),
                    OPS_TEST + " 缺少用例 " + caseName);
        }
        require(read(root, FAST_SCRIPT).contains("FileOpsTest"),
                FAST_SCRIPT + " 必须把 FileOpsTest 挂在快路径里");
    }
}
