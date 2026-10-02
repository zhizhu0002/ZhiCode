import java.nio.file.*;
import java.util.*;

/**
 * 「让手机文件管理器访问 HOME」的守卫。
 *
 * <h2>为什么是 DocumentsProvider 而不是搬目录</h2>
 *
 * HOME 在应用私有目录里（{@code 0700} + SELinux），第三方文件管理器一个都进不去，
 * 不给 root 没有例外。于是只有两条路：
 *
 * <ol>
 *   <li>把内容真的搬到共享存储 —— 那一片挂载是 {@code noexec}
 *       （{@code /proc/mounts}: {@code /dev/fuse /storage/emulated fuse rw,…,noexec}），
 *       搬过去之后 {@code ./gradlew} 这类脚本就不能直跑了；</li>
 *   <li>把 HOME 发布成 {@code DocumentsProvider}（SAF）—— HOME 一个字节都不搬。</li>
 * </ol>
 *
 * <p>本守卫钉的是第 2 条路上**不会编译失败的**几种退化。它们的共同点：
 * 编译过、跑得起来、只有文件管理器里"少一项"或"某一项打不开"。
 *
 * <ol>
 *   <li>清单里的 provider 被删/改名/丢 intent-filter —— 文件管理器的"添加存储"里静默少一项；</li>
 *   <li>{@code MANAGE_DOCUMENTS} 权限声明被去掉 —— 功能还在，但**任何应用**都调得到了，
 *       而私有目录是 {@code 0700} 的原因正是"别人不该进来"；</li>
 *   <li>越界校验被简化成字符串判断 —— 符号链接（{@code storage/shared → /storage/emulated/0}）
 *       就能读写到 HOME 之外，而且**没人会发现**；</li>
 *   <li>删符号链接跟进去 —— 一次删除带走用户的整块内部存储；</li>
 *   <li>名字规则与 {@code FileOps} 分家 —— 变成"一边能建、另一边打不开"；</li>
 *   <li>{@code MatrixCursor} 无条件写全部列 —— 部分投影下 provider 直接抛异常。</li>
 * </ol>
 *
 * <p>另外：这些逻辑都在 Kotlin 文件里，所以本测试同时钉住"它们必须仍能被
 * {@code test-jvm-fast.sh} 秒级测到"（Kotlin 主源码不带 android.jar 编译）。
 */
public final class LocalMountTest {

    private static final String PROVIDER =
            "app/src/main/java/com/zhizhu/zhicode/ZhiDocumentsProvider.kt";
    private static final String TREE =
            "app/src/main/java/com/termux/app/zhicode/core/DocumentTree.kt";
    private static final String TREE_TEST =
            "app/src/test/java/com/termux/app/zhicode/core/DocumentTreeTest.kt";
    private static final String OPS =
            "app/src/main/java/com/termux/app/zhicode/core/FileOps.java";
    private static final String MANIFEST = "app/src/main/AndroidManifest.xml";
    private static final String FAST_SCRIPT = "test-jvm-fast.sh";
    private static final String ENV_DOCTOR =
            "app/src/main/java/com/zhizhu/zhicode/compose/runtime/EnvDoctor.kt";

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static String read(String root, String relative) throws Exception {
        return new String(Files.readAllBytes(Paths.get(root, relative)),
                java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * 去掉注释，但**认得字符串字面量**。
     *
     * <p>⚠️ 不能用 {@code replaceAll("(?s)/&#42;.*?&#42;/", " ")} 那种正则写法：
     * provider 里 {@code COLUMN_MIME_TYPES} 的字面量是 {@code "&#42;&#47;&#42;"}，
     * 它中间的「斜杠星号」会被正则当成块注释的开始，一路吞到下一个「星号斜杠」
     * —— 中间那一段断言等于在检查一段被掏空的文本，**该 FAIL 的时候会 PASS**。
     * 这个坑是 ProviderDiagnosticsTest 加进来时才暴露的：它检查的
     * queryDocument 恰好落在被吞掉的那一段里。
     */
    private static String stripComments(String text) {
        StringBuilder out = new StringBuilder(text.length());
        boolean inLine = false;
        boolean inBlock = false;
        boolean inString = false;
        boolean inChar = false;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            char next = i + 1 < text.length() ? text.charAt(i + 1) : '\0';
            if (inLine) {
                if (c == '\n') {
                    inLine = false;
                    out.append(c);
                }
                continue;
            }
            if (inBlock) {
                if (c == '*' && next == '/') {
                    inBlock = false;
                    i++;
                }
                continue;
            }
            if (inString || inChar) {
                out.append(c);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (inString && c == '"') {
                    inString = false;
                } else if (inChar && c == '\'') {
                    inChar = false;
                }
                continue;
            }
            if (c == '/' && next == '/') {
                inLine = true;
                i++;
                continue;
            }
            if (c == '/' && next == '*') {
                inBlock = true;
                i++;
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '\'') {
                inChar = true;
            }
            out.append(c);
        }
        return out.toString();
    }

    private static String squash(String text) {
        return text.replaceAll("\\s+", "");
    }

    public static void main(String[] args) throws Exception {
        String root = args.length > 0 ? args[0] : ".";
        String provider = stripComments(read(root, PROVIDER));
        String tree = stripComments(read(root, TREE));
        String manifest = read(root, MANIFEST);
        String sqManifest = squash(manifest);
        String sqTree = squash(tree);
        String sqProvider = squash(provider);

        // ---- 1. 清单：注册 + 权限 + intent-filter -------------------------
        require(manifest.contains("com.zhizhu.zhicode.ZhiDocumentsProvider"),
                MANIFEST + " 必须注册 ZhiDocumentsProvider："
                        + "少了它，文件管理器的「添加存储」里静默少一项 —— 编译与运行都不报错");
        require(sqManifest.contains("android:authorities=\"${applicationId}.documents\""),
                MANIFEST + " 的 authority 必须是 ${applicationId}.documents"
                        + "（与 EnvDoctor 报告里那个值一致，写死包名会在换包名时错位）");
        require(sqManifest.contains("android:permission=\"android.permission.MANAGE_DOCUMENTS\""),
                MANIFEST + " 必须声明 MANAGE_DOCUMENTS：它是 signature|privileged 权限，"
                        + "只有系统进程持有 —— 这既是 SAF 的要求，也是"
                        + "「发布出去」不等于「对所有应用敞开」的唯一依据。"
                        + "去掉它，任何应用都能读写用户的私有目录");
        require(sqManifest.contains("android.content.action.DOCUMENTS_PROVIDER"),
                MANIFEST + " 必须有 DOCUMENTS_PROVIDER 的 intent-filter："
                        + "系统靠它发现这个 provider，丢了就只是「注册了但没人知道」");

        // ---- 2. 越界校验必须走规范化，不能退化成字符串判断 ----------------
        require(sqTree.contains("canonicalUnderRoot"),
                TREE + " 必须有 canonicalUnderRoot");
        require(sqTree.contains("file.canonicalFile") || sqTree.contains("getCanonicalFile()"),
                TREE + " 的越界校验必须用 getCanonicalFile："
                        + "只比较字符串的话，HOME 里那条 storage/shared → /storage/emulated/0 "
                        + "就能读写到 HOME 之外，而且没人会发现");
        // 前缀比较必须补分隔符：/…/home-evil 不该被算作 /…/home 之内
        require(sqTree.contains("canonicalRoot.path+File.separator"),
                TREE + " 的前缀比较必须补 File.separator："
                        + "只写 startsWith(rootPath) 会让同前缀的兄弟目录 home-evil 被误判成在 home 之内");
        // ID 的 . / .. 必须按**段**判断（子串判断会拒掉合法的 a..b.txt）
        require(sqTree.contains("for(segmentinrelative.split('/'))"),
                TREE + " 必须按段遍历 ID：子串式的 contains(\"..\") 会连合法的 "
                        + "a..b.txt 一起拒掉，而那个 ID 是我们自己生成的 —— 于是它转不回来");

        // ---- 3. 删符号链接不能跟进去 --------------------------------------
        require(sqTree.contains("Files.isSymbolicLink"),
                TREE + " 的 deleteTree 必须用 Files.isSymbolicLink 判断："
                        + "isDirectory() 对链接返回的是它指向的目标的类型，"
                        + "跟进去递归删会把用户的整块内部存储删掉");

        // ---- 4. 名字规则只有一处实现 --------------------------------------
        require(sqTree.contains("FileOps.nameError"),
                TREE + " 的 safeName 必须转发到 FileOps.nameError："
                        + "应用内文件面板与文件管理器写的是同一个目录，"
                        + "两处口径不同就是「一边能建、另一边打不开」");
        require(read(root, OPS).contains("public static String nameError"),
                OPS + " 必须仍然提供 nameError（名字规则的唯一实现处）");

        // ---- 5. MatrixCursor 必须按投影写 ---------------------------------
        require(sqProvider.contains("columnNames"),
                PROVIDER + " 写行时必须按 cursor.columnNames 遍历："
                        + "MatrixCursor.add(列名, 值) 对不在投影里的列名会抛异常，"
                        + "而各厂商文件管理器会传各种子集投影");
        // 目录/文件的可做动作必须分开
        require(sqProvider.contains("FLAG_DIR_SUPPORTS_CREATE"),
                PROVIDER + " 目录必须声明 FLAG_DIR_SUPPORTS_CREATE："
                        + "缺了它文件管理器里长按目录没有「新建文件夹」，"
                        + "「可读写」的承诺只兑现了一半");
        require(sqProvider.contains("FLAG_SUPPORTS_WRITE") && sqProvider.contains("FLAG_SUPPORTS_RENAME")
                        && sqProvider.contains("FLAG_SUPPORTS_DELETE"),
                PROVIDER + " 文件必须声明 write/rename/delete："
                        + "少一个就在文件管理器里对应少一个菜单项");
        // 不声明没实现的能力
        require(!sqProvider.contains("FLAG_SUPPORTS_SEARCH"),
                PROVIDER + " 不许声明 FLAG_SUPPORTS_SEARCH：我们没实现 querySearchDocuments，"
                        + "声明了等于给用户一个点了没反应的搜索框");

        // ---- 6. 私有目录对文件管理器隐藏，但对应用内面板不隐藏 ------------
        require(sqTree.contains("privateNames"),
                TREE + " 必须有 privateNames：应用元数据（.zhicode）不该交给文件管理器随手改");
        require(sqTree.contains("BRAND_SLUG"),
                TREE + " 的私有目录名必须从 TermuxConstants.BRAND_SLUG 派生："
                        + "写死字符串会在改品牌名时静默失效（等于没挡）");
        require(!stripComments(read(root, OPS)).contains("DocumentTree"),
                OPS + " 应用内文件面板不该被私有目录黑名单挡住："
                        + "技能文件写错了就是要能就地修，那正是那个面板的用途");

        // ---- 7. 秒级可测：Kotlin 且不带 android.jar ------------------------
        require(!tree.contains("import android."),
                TREE + " 不许 import android.*：它是纯逻辑，"
                        + "带上 Android 就只能靠分钟级的 Gradle 单测跑了");
        String script = read(root, FAST_SCRIPT);
        require(script.contains("DocumentTree.kt"),
                FAST_SCRIPT + " 必须把 DocumentTree.kt 列进 MAIN_KT_SOURCES："
                        + "不列进去这条链路就没有秒级回路，反向验证只能等几分钟一轮");
        require(script.contains("DocumentTreeTest"),
                FAST_SCRIPT + " 必须把 DocumentTreeTest 列进默认测试");
        require(script.contains("kotlin-stdlib"),
                FAST_SCRIPT + " 必须把 kotlin-stdlib 放进 classpath："
                        + "少了它的报错是 NoClassDefFoundError: kotlin/Unit，"
                        + "完全看不出是「缺个 jar」");

        String treeTest = stripComments(read(root, TREE_TEST));
        for (String caseName : new String[]{
                "symlinkEscapingHomeIsRejected",
                "siblingWithSamePrefixIsNotInside",
                "deleteTreeDoesNotFollowSymlinks",
                "nameRulesAgreeWithFileOps",
                "namesContainingDotsAreNotPathTraversal",
                "privateDirectoriesAreUnreachableById"}) {
            require(treeTest.contains(caseName),
                    TREE_TEST + " 缺少用例 " + caseName + "："
                            + "这六条各对应一种「不报错但错了」的情况");
        }

        // ---- 8. 自检报告要能查这条链路 ------------------------------------
        String doctor = stripComments(read(root, ENV_DOCTOR));
        require(doctor.contains("DocumentTree.describe("),
                ENV_DOCTOR + " 必须报告这条链路的状态："
                        + "文件管理器里看不到东西时，用户唯一能提供的就是那几行事实");
        require(doctor.contains("queryIntentContentProviders"),
                ENV_DOCTOR + " 必须**实测** provider 是否被系统登记："
                        + "清单写错时编译与运行都不报错，只有这一问能查出来");
    }
}
