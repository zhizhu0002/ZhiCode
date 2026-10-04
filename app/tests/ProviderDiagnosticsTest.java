import java.nio.file.*;
import java.util.*;

/**
 * 「provider 的失败必须能被看见」的守卫。
 *
 * <h2>为什么需要这一条</h2>
 *
 * SAF 的失败在两端都是**静音**的：
 *
 * <ol>
 *   <li>框架在 {@code DocumentsProvider.call()} 里把异常接住、写进 logcat、只回一个
 *       {@code null} —— 文件管理器那一侧于是只剩它自己的一句通用文案
 *       （真机上见到的就是 {@code Failed to create directory: 1}）；</li>
 *   <li>真机上取 logcat 要 adb 或 root，这个环境两样都没有。</li>
 * </ol>
 *
 * <p>于是"能看见原因"只能由代码自己保证 —— 而它恰恰是最容易在重构里被悄悄丢掉的
 * 东西：删掉一句日志不影响任何功能，编译、构建、运行全都不报错，
 * 只是下一次真机出问题时，用户手里又只剩一句没有信息量的通用文案。
 *
 * <h2>钉住的退化（全都是"不报错但坏了"）</h2>
 *
 * <ol>
 *   <li>新增或改动 provider 入口时忘了包 {@code traced} —— 那条路径的失败重新变成不可见；</li>
 *   <li>{@code createDocument} 不记调用方参数 —— 分不清"没传名字"和"传了空名字"；</li>
 *   <li>日志写失败把 provider 带崩 —— 诊断手段自己变成故障源；</li>
 *   <li>诊断页退回"写死一句可用" —— 报告看起来很健康，而链路其实是断的；</li>
 *   <li>自检在自己建的目录之外动手 —— 用户目录里留下 {@code zhicode-selftest-*} 垃圾；</li>
 *   <li>启动路径丢掉 {@code ~/storage} 的补建 —— 早先装好的环境永远没有那一块
 *       （**真发生过**：文件管理器里 ZhiCode HOME 只列出 projects 和 tmp）。</li>
 * </ol>
 */
public final class ProviderDiagnosticsTest {

    private static final String PROVIDER =
            "app/src/main/java/com/zhizhu/zhicode/ZhiDocumentsProvider.kt";
    private static final String LOG =
            "app/src/main/java/com/termux/app/zhicode/core/ProviderLog.kt";
    private static final String LOG_TEST =
            "app/src/test/java/com/termux/app/zhicode/core/ProviderLogTest.kt";
    private static final String ENV_DOCTOR =
            "app/src/main/java/com/zhizhu/zhicode/compose/runtime/EnvDoctor.kt";
    private static final String VIEW_MODEL =
            "app/src/main/java/com/zhizhu/zhicode/compose/state/WorkspaceViewModel.kt";
    private static final String INSTALLER =
            "app/src/main/java/com/zhizhu/zhicode/RuntimeInstaller.kt";
    private static final String FAST_SCRIPT = "test-jvm-fast.sh";

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
     * provider 里有一处 {@code COLUMN_MIME_TYPES} 的字面量是 {@code "&#42;&#47;&#42;"}
     * —— 它中间的「斜杠星号」会被正则当成块注释的开始，一路吞到下一个「星号斜杠」，
     * 把中间一大段代码"删"掉。于是断言看起来在检查代码，
     * 实际检查的是一段被掏空的文本：**该 FAIL 的时候会 PASS**。
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
        String sqProvider = squash(stripComments(read(root, PROVIDER)));
        String log = stripComments(read(root, LOG));
        String sqDoctor = squash(stripComments(read(root, ENV_DOCTOR)));

        // ---- 1. 每个 provider 入口都必须过 traced --------------------------
        // 加了一个入口却忘了包，那条路径的失败就重新变成不可见 —— 而这不会报错。
        for (String entry : new String[]{
                "queryRoots", "queryDocument", "queryChildDocuments", "isChildDocument",
                "openDocument", "createDocument", "deleteDocument", "renameDocument"}) {
            require(sqProvider.contains("traced(\"" + entry + "\""),
                    PROVIDER + " 的 " + entry + " 必须过 traced："
                            + "框架会把这里的异常吞成 null，不记下来就等于「失败原因不可见」");
        }

        // ---- 2. createDocument 必须记下调用方的参数 ------------------------
        require(sqProvider.contains("traced(\"createDocument\",parentDocumentId,mimeType,displayName)"),
                PROVIDER + " 的 createDocument 必须把 parentDocumentId / mimeType / displayName 全记下来："
                        + "「新建失败」最需要分辨的就是「没传名字」与「传了空名字」，"
                        + "以及 mimeType 到底是不是目录");

        // ---- 3. 日志不许把 provider 弄坏 ----------------------------------
        require(sqProvider.contains("outcome:String){runCatching{"),
                PROVIDER + " 的 log 必须整段包在 runCatching 里："
                        + "磁盘满或权限不足时，日志自身绝不能把 provider 变成故障源");

        // ---- 4. 日志必须落在用户打得到的地方 ------------------------------
        require(sqProvider.contains("File(TermuxConstants.TERMUX_HOME_DIR_PATH,\"tmp\")"),
                PROVIDER + " 的日志必须落在 $HOME/tmp 下："
                        + "那个位置用户自己就能打开（应用内文件面板和文件管理器都读得到），"
                        + "不需要 adb 或 root 才能把原因带出来");

        // ---- 5. 诊断页必须**真调用**，且自己收尾 --------------------------
        require(sqDoctor.contains("resolver.query("),
                ENV_DOCTOR + " 的自检必须真的查询 provider，而不是只读一遍文件系统");
        require(sqDoctor.contains("DocumentsContract.createDocument("),
                ENV_DOCTOR + " 的自检必须真的调用 createDocument："
                        + "写一句「文件管理器可用」当证据等于把报告做成安慰剂 —— "
                        + "注册查出来是「是」只说明清单没写错，不代表这条链路能跑通");
        require(sqDoctor.contains("DocumentsContract.deleteDocument("),
                ENV_DOCTOR + " 的自检必须删掉自己建的那个目录："
                        + "诊断动作不许在用户目录里留下东西");
        // 只查前缀：代码里是模板串（"zhicode-selftest-" + 时间戳），写死闭合引号会误判
        require(sqDoctor.contains("zhicode-selftest-"),
                ENV_DOCTOR + " 自检目录名必须带固定前缀 zhicode-selftest-："
                        + "万一删除失败，用户一眼就知道那是什么、能不能删");

        // ---- 6. 报告必须把日志尾巴贴出来 ----------------------------------
        require(sqDoctor.contains("providerLogTail()"),
                ENV_DOCTOR + " 的报告里必须贴出 provider 日志的尾巴："
                        + "框架把异常吞成 null，只有我们自己记的那句 FAIL 能说清原因");
        require(sqDoctor.contains("ProviderLog.tail("),
                ENV_DOCTOR + " 必须用 ProviderLog.tail 取有界的尾巴，"
                        + "而不是把整份日志塞进报告");

        // ---- 7. ProviderLog 是纯逻辑，必须留在秒级回路里 -------------------
        require(!log.contains("import android."),
                LOG + " 不许 import android.*：它是纯逻辑，"
                        + "带上 Android 就只能靠分钟级的 Gradle 单测跑，反向验证也就没人会做了");
        String script = read(root, FAST_SCRIPT);
        require(script.contains("ProviderLog.kt"),
                FAST_SCRIPT + " 必须把 ProviderLog.kt 列进 MAIN_KT_SOURCES");
        require(script.contains("ProviderLogTest"),
                FAST_SCRIPT + " 必须把 ProviderLogTest 列进默认测试");
        String logTest = stripComments(read(root, LOG_TEST));
        for (String caseName : new String[]{
                "nullAndEmptyArgumentsAreDistinguishable",
                "trimTailNeverSplitsACharacter",
                "trimTailGivesUpWhenThereIsNoLineBoundary"}) {
            require(logTest.contains(caseName),
                    LOG_TEST + " 缺少用例 " + caseName + "："
                            + "这几条各对应一种「不报错但日志不可信」的情况");
        }

        // ---- 8. 启动路径必须补建 ~/storage（真发生过的 bug）---------------
        String sqViewModel = squash(stripComments(read(root, VIEW_MODEL)));
        require(sqViewModel.contains("installer.setupStorageLinks()"),
                VIEW_MODEL + " 的启动路径必须调用 setupStorageLinks()："
                        + "它此前只在用户手动点「修复」时才跑，结果是早先装好的环境永远没有 "
                        + "~/storage —— 真机上就撞到了：文件管理器里 ZhiCode HOME 只列出 projects 和 tmp");
        // 这一条查**原文**（不 stripComments）：它钉的正是注释里那句说明。
        String installer = read(root, INSTALLER);
        require(installer.contains("不能只靠这里"),
                INSTALLER + " 里那句「repairIfInstalled 本来就跑在每次启动的路径上」是错的"
                        + "（它唯一调用点是手动「修复」），改正后的说明必须留住");
    }
}
