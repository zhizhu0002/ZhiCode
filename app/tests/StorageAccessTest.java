import java.nio.file.*;
import java.util.*;

/**
 * 「能访问手机共享存储」的守卫（用户：「软件可以访问 .storage/ 目录，参考 IQcode」）。
 *
 * <p>参考实现是 Termux 的 {@code termux-setup-storage}：在 {@code $HOME/storage/}
 * 下建六个指向 {@code /storage/emulated/0} 的符号链接。它出错的方式全都**静默**：
 *
 * <ul>
 *   <li><b>只有安装路径会建</b>：老用户已经装好了环境，而"重装一次要几十秒"——
 *       于是这个功能对他们等于不存在。必须挂到**每次启动**的幂等修复路径上。</li>
 *   <li><b>建了但指错</b>：链接存在、命令不报错，Agent 往那儿写"成功"了，
 *       用户在「内部存储」里却找不到。所以必须有逐条实测的报告，而不是一个 OK/FAIL。</li>
 *   <li><b>名字跟 Termux 不一致</b>：模型见过大量 Termux 语料，写
 *       {@code ~/storage/shared/...} 是肌肉记忆；自己另起一套名字会让它每次猜错一次。</li>
 *   <li><b>失败让安装整个炸掉</b>：用户还没给「所有文件访问权限」时链接建不出来是
 *       预期之内的，不该让几十秒的安装白跑。</li>
 * </ul>
 */
public final class StorageAccessTest {

    private static final String LINKS =
            "app/src/main/java/com/termux/app/zhicode/core/StorageLinks.java";
    private static final String LINKS_TEST =
            "app/src/test/java/com/termux/app/zhicode/core/StorageLinksTest.java";
    private static final String INSTALLER =
            "app/src/main/java/com/zhizhu/zhicode/RuntimeInstaller.kt";
    private static final String ENV_DOCTOR =
            "app/src/main/java/com/zhizhu/zhicode/compose/runtime/EnvDoctor.kt";
    private static final String MANIFEST = "app/src/main/AndroidManifest.xml";
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
        String links = stripComments(read(root, LINKS));
        String installer = stripComments(read(root, INSTALLER));
        String doctor = stripComments(read(root, ENV_DOCTOR));
        String manifest = stripComments(read(root, MANIFEST));

        // ---- 1. 六个名字必须与 Termux 官方一致 ---------------------------
        for (String name : new String[]{
                "\"shared\"", "\"dcim\"", "\"downloads\"",
                "\"movies\"", "\"music\"", "\"pictures\""}) {
            require(links.contains(name),
                    LINKS + " 缺少链接名 " + name + "：名字必须与 Termux 的 "
                            + "termux-setup-storage 一致 —— 模型写 ~/storage/shared/... "
                            + "是肌肉记忆，另起一套名字会让它每次猜错一次");
        }
        for (String target : new String[]{
                "\"DCIM\"", "\"Download\"", "\"Movies\"", "\"Music\"", "\"Pictures\""}) {
            require(links.contains(target),
                    LINKS + " 缺少目标 " + target + "（共享存储根下的大小写必须照抄）");
        }
        require(links.contains("/storage/emulated/0"),
                LINKS + " 的共享存储根必须是 /storage/emulated/0");

        // ---- 2. 幂等 + 只增不删 + 会修错的链接 ---------------------------
        require(links.contains("isCorrectLink(link, target)"),
                LINKS + " 必须跳过**已经正确**的链接：每次启动都删了重建有窗口期，"
                        + "用户恰好在那一刻访问就会失败");
        require(links.contains("link.delete()"),
                LINKS + " 必须能替换掉挡路的普通文件/指错的链接：直接 createSymbolicLink "
                        + "会抛 FileAlreadyExistsException，而它很容易被吞掉 —— "
                        + "结果是「配置看起来跑了，链接没建」");
        // 「只增不删」只能看**代码**：清空整个目录（官方脚本会警告并重建）在这里是
        // 没必要的破坏 —— 用户可能往 ~/storage 里放了自己的东西。
        require(squash(links).contains("for(Map.Entry<String,String>entry:LINKS.entrySet())"),
                LINKS + " 的 setup 必须**只遍历自己的链接表**："
                        + "遍历目录里的全部条目再删的话，用户放在 ~/storage 下的东西会被清掉");
        require(!links.contains("deleteRecursive") && !links.contains("FileTree"),
                LINKS + " 不许对自己的链接表之外做递归删除："
                        + "只增不删 —— 目录里不属于本表的条目一律不动");

        // ---- 3. 安装路径与**每次启动**的修复路径都要建 -------------------
        require(squash(installer).contains("setupStorageLinks()"),
                INSTALLER + " 必须提供 setupStorageLinks()");
        int calls = installer.split("setupStorageLinks\\(\\)", -1).length - 1;
        require(calls >= 3,
                INSTALLER + " 的 setupStorageLinks() 至少要被调用两次（定义 1 次 + "
                        + "安装路径 + 修复路径），现在只出现 " + calls + " 次。"
                        + "只挂在安装路径上的话，已经装好环境的老用户等于没有这个功能 —— "
                        + "而「重装一次要几十秒」没人会为了一个软链接去做");
        int repairAt = installer.indexOf("fun repairIfInstalled()");
        int repairEnd = installer.indexOf("fun install(", repairAt);
        require(repairAt >= 0 && repairEnd > repairAt,
                INSTALLER + " 里找不到 repairIfInstalled()/install() 的边界");
        require(installer.substring(repairAt, repairEnd).contains("setupStorageLinks()"),
                INSTALLER + " 的 repairIfInstalled() 必须建存储链接：它跑在每次启动的路径上，"
                        + "是「已经装好的环境」能拿到 ~/storage 的唯一机会");
        require(squash(installer).contains("runCatching{StorageLinks.setup("),
                INSTALLER + " 建链接失败**不许**抛：用户还没给「所有文件访问权限」时"
                        + "建不出来是预期之内的，不该让几十秒的安装白跑一遍");

        // ---- 4. 报告必须逐条实测 -----------------------------------------
        require(doctor.contains("StorageLinks.describe("),
                ENV_DOCTOR + " 的「存储访问」必须逐条实测（StorageLinks.describe）："
                        + "链接在不在 / 指向哪 / 读不读得到是三件不同的事，"
                        + "合成一个 OK/FAIL 就查不出是哪一种");
        require(doctor.contains("可写="),
                ENV_DOCTOR + " 必须报告共享存储根的**可写**状态："
                        + "「能列目录但写不进去」是最常见的一种（没给 MANAGE_EXTERNAL_STORAGE），"
                        + "只报可读会让人以为一切正常");

        // ---- 5. 权限必须在清单里（否则链接建了也读不到） -----------------
        require(manifest.contains("android.permission.MANAGE_EXTERNAL_STORAGE"),
                MANIFEST + " 必须声明 MANAGE_EXTERNAL_STORAGE：Android 11+ 上"
                        + "没有它就只能读自己创建的文件，共享存储整体不可达");
        require(manifest.contains("android:requestLegacyExternalStorage=\"true\""),
                MANIFEST + " 必须保留 requestLegacyExternalStorage："
                        + "Android 10 上它决定能不能用路径方式访问共享存储");

        // ---- 6. 纯 Java + 必须有单测 -------------------------------------
        require(!links.contains("import android."),
                LINKS + " 不许 import android.*：那会让它只能靠 Gradle 单测跑，"
                        + "秒级的 test-jvm-fast.sh 就守不住「建了吗/指对了吗」");
        String test = stripComments(read(root, LINKS_TEST));
        for (String caseName : new String[]{
                "linkNamesMatchTermux",
                "setupCreatesAllSixLinks",
                "setupIsIdempotentAndKeepsGoodLinks",
                "plainFileInTheWayIsReplaced",
                "wrongLinkTargetIsRepaired",
                "unrelatedEntriesAreLeftAlone",
                "describeReportsEachLinkWithReachability"}) {
            require(test.contains(caseName),
                    LINKS_TEST + " 缺少用例 " + caseName);
        }
        require(read(root, FAST_SCRIPT).contains("StorageLinksTest"),
                FAST_SCRIPT + " 必须把 StorageLinksTest 挂在快路径里");
    }
}
