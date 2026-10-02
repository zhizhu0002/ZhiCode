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
 * <p>另外三条是"写坏了"的具体形状：
 * <ol>
 *   <li>删除确认不报**会一起消失多少条** —— 删目录等于没告诉用户代价；</li>
 *   <li>删掉的正是当前打开的文件时**不关掉编辑器** —— 面板继续显示一份
 *       已经不存在的文件的正文，再点保存会把它**建回来**；</li>
 *   <li>二进制/读取失败的预览也允许进入编辑态 —— 保存回去就把文件写坏了
 *       （`FileBrowser.read` 对这两种情况返回的是给人看的提示文字）。</li>
 * </ol>
 */
public final class FilePanelWriteTest {

    private static final String PANE =
            "app/src/main/java/com/zhizhu/zhicode/compose/ui/panes/FilesPane.kt";
    private static final String VM =
            "app/src/main/java/com/zhizhu/zhicode/compose/state/WorkspaceViewModel.kt";
    private static final String MODELS =
            "app/src/main/java/com/zhizhu/zhicode/compose/model/UiModels.kt";
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

    public static void main(String[] args) throws Exception {
        String root = args.length > 0 ? args[0] : ".";
        String pane = stripComments(read(root, PANE));
        String vm = stripComments(read(root, VM));
        String models = stripComments(read(root, MODELS));
        String ops = stripComments(read(root, OPS));
        String sqVm = squash(vm);
        String sqPane = squash(pane);

        // ---- 1. 写操作齐全：新建 / 重命名 / 删除 / 保存 -------------------
        for (String op : new String[]{
                "fun newFileForm(", "fun renameForm(", "fun requestDelete(",
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

        // ---- 2. 根可切换：项目 / HOME / 共享存储 --------------------------
        require(models.contains("enum class FileRoot"),
                MODELS + " 必须有 FileRoot：三个根是三种不同的活儿"
                        + "（项目=代码、HOME=配置、共享存储=用户的文件）");
        for (String tier : new String[]{"PROJECT", "HOME", "SHARED"}) {
            require(models.contains(tier), MODELS + " 的 FileRoot 缺少 " + tier + " 档");
        }
        require(!squash(vm).contains("privatesuspendfunrootPath"),
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
                "onStartEdit", "onSave", "onCancelEdit", "onNewFile", "onNewDirectory",
                "onRename", "onRequestDelete", "onConfirmDelete", "onSwitchRoot"}) {
            require(pane.contains(callback),
                    PANE + " 缺少 " + callback + "：写操作必须在界面上真的有入口，"
                            + "只在 ViewModel 里有函数等于没有");
        }
        require(pane.contains("ZhiTextField("),
                PANE + " 的编辑态必须能真的输入（ZhiTextField）："
                        + "只显示正文而没有输入控件等于还是只读");
        require(pane.contains("ZhiFieldError("),
                PANE + " 的名字表单错误行必须走 ZhiFieldError："
                        + "标题下方那一条要和其他表单「有错才出现、左边缘对齐」一致");

        // ---- 4. 删除确认必须说清代价 -------------------------------------
        require(models.contains("count: Int"),
                MODELS + " 的 FileDeletePrompt 必须带 count（会一起消失的条数）："
                        + "只说「确定删除 sub 吗？」等于没告诉用户删目录会带走里面的全部内容");
        require(sqVm.contains("FileOps.countForDelete(File(entry.path))"),
                VM + " 的删除确认必须用 FileOps.countForDelete 算条数");
        require(pane.contains("会一起消失"),
                PANE + " 的删除确认必须把代价写出来（「其中的 N 项内容会一起消失」）");

        // ---- 5. 删掉当前打开的文件时必须关掉编辑器 ------------------------
        // 注意：这里是**已经压掉空白**的写法（和 sqVm 同一口径），别往里加空格。
        require(sqVm.contains("openFile=if(s.openFile?.path==prompt.entry.path)nullelses.openFile"),
                VM + " 在删掉的正是当前打开的文件时必须把 openFile 置空："
                        + "否则面板继续显示一份已经不存在的文件的正文，"
                        + "再点保存会把它**建回来**");

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

        // ---- 7. 接线：布局层必须把回调真的传下去 --------------------------
        String layouts = stripComments(read(root, LAYOUTS));
        for (String wiring : new String[]{
                "onSwitchRoot = viewModel::switchFileRoot",
                "onSave = viewModel::saveFile",
                "onConfirmDelete = viewModel::confirmDelete",
                "onRequestDelete = viewModel::requestDelete",
                "onStartEdit = viewModel::startEditingFile"}) {
            require(layouts.contains(wiring),
                    LAYOUTS + " 缺少接线 " + wiring + "："
                            + "FilesPane 加了形参而布局层不传，界面上的按钮就什么都不做");
        }

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
