import java.nio.file.*;
import java.util.*;

/**
 * 「高风险」红标的守卫。
 *
 * <p>用户报的是「设置成每次询问时，每个命令都提示高风险」。根因是一行判定:
 * {@code kind == SYSTEM || kind == SHELL} —— 按**工具种类**判，于是 {@code ls}
 * 和 {@code rm -rf /} 长得一模一样。红标一旦天天出现就不再是信息，
 * 用户学会的是忽略它，真正危险的那条反而混在队伍里看不见。
 *
 * <p>这条守钉三件事：
 *
 * <ol>
 *   <li>判定必须走 {@link RiskClassifier}（按命令内容），不能再退回按种类判。</li>
 *   <li>判定表本身必须仍然是「默认不危险」的：日常命令的用例、以及
 *       「工程内 rm -rf 不标」的语义都要在单测里留着 —— 不然下一个人只要
 *       删测试就能让规则变宽。</li>
 *   <li>{@code RiskClassifier} 不许 import android.*，否则它只能靠
 *       Gradle 单测跑（分钟级），秒级的 test-jvm-fast.sh 就再也守不住它。</li>
 * </ol>
 */
public final class RiskBadgeTest {

    private static final String CONTROLLER =
            "app/src/main/java/com/zhizhu/zhicode/compose/engine/ZhiEngineController.kt";
    private static final String CLASSIFIER =
            "app/src/main/java/com/termux/app/zhicode/core/RiskClassifier.java";
    private static final String CLASSIFIER_TEST =
            "app/src/test/java/com/termux/app/zhicode/core/RiskClassifierTest.java";
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
        String controller = stripComments(read(root, CONTROLLER));
        String classifier = stripComments(read(root, CLASSIFIER));
        String test = stripComments(read(root, CLASSIFIER_TEST));

        // ---- 1. 判定按内容，不按工具种类 --------------------------------
        require(!squash(controller).contains("PermissionKind.SYSTEM||"),
                CONTROLLER + " 又出现了按工具种类判高风险（kind == SYSTEM || SHELL）："
                        + "那等于每个 shell 命令都挂红标，用户报的就是这个");
        require(squash(controller).contains("RiskClassifier.isHighRisk(name,call?.input)"),
                CONTROLLER + " 必须把高风险判定交给 RiskClassifier（按命令内容）："
                        + "这一行是用户报的「每个命令都提示高风险」的唯一修法");

        // ---- 2. 判定表是「默认不危险」的 --------------------------------
        require(classifier.contains("ALWAYS_RISKY") && classifier.contains("isRiskyCommand"),
                CLASSIFIER + " 缺判定表本体（ALWAYS_RISKY / isRiskyCommand）");
        for (String pattern : new String[]{
                "outsideHomeTarget",   // 工程内 rm -rf 不算危险
                "pipesIntoShell",      // curl | sh
                "RISKY_PM",            // pm install 类
                "PACKAGE_MANAGERS",    // pkg/apt/dpkg/pip/npm
                "PROTECTED_PREFIXES"}) {
            require(classifier.contains(pattern),
                    CLASSIFIER + " 缺少判定分支 " + pattern);
        }
        // 「默认不危险」这条最容易被顺手改坏：必须有日常命令的反向用例。
        require(test.contains("everydayCommandsAreNotFlagged")
                        && test.contains("./gradlew :app:assembleDebug")
                        && test.contains("git status --porcelain"),
                CLASSIFIER_TEST + " 必须保留「日常命令不得被标红」的用例："
                        + "只留危险用例的话，规则被一路放宽也不会有人发现");
        require(test.contains("recursiveRemoveInsideProjectStaysNormal")
                        && test.contains("rm -rf build/"),
                CLASSIFIER_TEST + " 必须保留「工程内 rm -rf build/ 不算高危」的用例："
                        + "这是日常动作里最常跑的一条");

        // ---- 3. 秒级可测：不许依赖 android.* ---------------------------
        require(!classifier.contains("import android."),
                CLASSIFIER + " 不许 import android.*：那会让它只能靠 Gradle 单测跑，"
                        + "test-jvm-fast.sh（秒级）就守不住这张判定表了");
        require(read(root, FAST_SCRIPT).contains("RiskClassifierTest"),
                FAST_SCRIPT + " 必须把 RiskClassifierTest 挂在快路径里："
                        + "判定表不跑起来就只是一段注释");
    }
}
