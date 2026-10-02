import java.nio.file.*;
import java.util.*;

/**
 * 「自动吸底」的守卫。
 *
 * <p>用户报的是「把自动滚动优化一下，感觉不是很顺畅」。这段逻辑的特点是
 * **判错完全静默**：不编译失败、不报错、不崩，只让人觉得手感不对。
 * 所以只能静态钉住它的形状：
 *
 * <ol>
 *   <li>跟/不跟必须走 [rememberAutoFollow] / [AutoFollowPolicy] —— 不许退回
 *       「松手时读一次 canScrollForward」。流式输出下内容每 32ms 长一次，
 *       松手那一刻的读数很可能已经不是用户手指的位置了，这正是「不顺畅」的来源。</li>
 *   <li>滞回阈值必须存在且非零：手指抖 3px 不该切换状态。</li>
 *   <li>「拖动过程中到过底部」必须被记录：只看松手那一刻是不够的。</li>
 *   <li>终端占位面板也必须走同一套判定 —— 之前它无条件贴底，
 *       一边看历史一边有新行进来就会被反复拽回去。</li>
 *   <li>决策逻辑必须有单测。纯逻辑没有测试，下一个人把滞回删掉也没人知道。</li>
 * </ol>
 */
public final class AutoScrollSmoothnessTest {

    private static final String CHAT_LIST =
            "app/src/main/java/com/zhizhu/zhicode/compose/ui/chat/ChatList.kt";
    private static final String AUTO_FOLLOW =
            "app/src/main/java/com/zhizhu/zhicode/compose/ui/chat/AutoFollow.kt";
    private static final String TERMINAL =
            "app/src/main/java/com/zhizhu/zhicode/compose/ui/panes/TerminalPane.kt";
    private static final String POLICY_TEST =
            "app/src/test/java/com/zhizhu/zhicode/compose/ui/chat/AutoFollowPolicyTest.kt";

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
        String chat = stripComments(read(root, CHAT_LIST));
        String follow = stripComments(read(root, AUTO_FOLLOW));
        String terminal = stripComments(read(root, TERMINAL));
        String policyTest = stripComments(read(root, POLICY_TEST));
        String sqFollow = squash(follow);

        // ---- 1. 判定只有一处实现 -----------------------------------------
        require(squash(chat).contains("valautoFollowbyrememberAutoFollow(listState)"),
                CHAT_LIST + " 的 autoFollow 必须取自 rememberAutoFollow："
                        + "在页面里就地算一份的话，滞回/到过底部这些判据会被漏掉，"
                        + "而这是静默的 —— 只表现为「滚动不顺」");
        // 旧写法：直接由 isScrollInProgress 推出 autoFollow
        require(!squash(chat).contains("autoFollow=if(scrolling)false"),
                CHAT_LIST + " 又出现了「松手时读一次 canScrollForward 就定状态」的旧写法："
                        + "流式内容每 32ms 长一次，那样会把「贴底松手」判成「停在半途」，"
                        + "跟随直接断掉");
        require(sqFollow.contains("funrememberAutoFollow("),
                AUTO_FOLLOW + " 必须提供 rememberAutoFollow");

        // ---- 2. 滞回必须存在且非零 ---------------------------------------
        require(sqFollow.contains("hysteresis:Dp=8.dp"),
                AUTO_FOLLOW + " 的滞回阈值必须有非零默认值（8dp）："
                        + "没有它的话手指在屏幕上抖几个像素就会切换跟随状态");
        require(sqFollow.contains("hysteresisPx:Int") && sqFollow.contains("hysteresisPx=hysteresisPx"),
                AUTO_FOLLOW + " 必须把滞回换算成 px 并传进判定："
                        + "判定内部要按像素比较，传 dp 进去等于没比较");

        // ---- 3. 「拖动过程中到过底部」必须记录 ----------------------------
        require(sqFollow.contains("sawBottomDuringDrag"),
                AUTO_FOLLOW + " 必须记录「拖动过程中到过底部」："
                        + "只看松手那一刻的话，内容刚长高就会把它判成没到过底部");
        require(sqFollow.contains("listState.isScrollInProgress&&!listState.canScrollForward"),
                AUTO_FOLLOW + " 必须在拖动期间持续采样贴底状态（isScrollInProgress && !canScrollForward）："
                        + "整个判定最关键的输入就是它");
        require(sqFollow.contains("launch{"),
                AUTO_FOLLOW + " 的「采样贴底」和「拖动起止」必须是两条独立的流："
                        + "合成一条、把 index/offset 也包进去的话，每个滚动像素都会发射一次，"
                        + "而在那条分支里重置起点会把整段判定算成「没动」");

        // ---- 4. 终端占位面板走同一套判定 ---------------------------------
        require(squash(terminal).contains("valautoFollowbyrememberAutoFollow(listState)"),
                TERMINAL + " 的终端列表也必须用 rememberAutoFollow："
                        + "原先它无条件贴底，一边看历史一边来新行就会被反复拽回底部");
        require(squash(terminal).contains("if(autoFollow&&lines.isNotEmpty())"),
                TERMINAL + " 的自动贴底必须受 autoFollow 门控");

        // ---- 5. 判定逻辑必须有单测 ---------------------------------------
        require(policyTest.contains("class AutoFollowPolicyTest"),
                POLICY_TEST + " 必须存在");
        for (String caseName : new String[]{
                "draggingTowardOlderContentPausesFollow",
                "draggingTowardNewContentResumesOnlyIfItReachedTheBottom",
                "tinyDragInsideHysteresisKeepsFollowing",
                "sawBottomDuringDragWinsOverTheReleaseInstantReading"}) {
            require(policyTest.contains(caseName),
                    POLICY_TEST + " 缺少用例 " + caseName + "："
                            + "滞回、到过底部、往旧方向拖 —— 这三个分支各对应一种手感问题，"
                            + "少一个就有一条规则可以被悄悄删掉");
        }
    }
}
