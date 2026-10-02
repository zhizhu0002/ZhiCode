import java.nio.file.*;
import java.util.*;

/**
 * 侧栏行高与会话行高（#8）。
 *
 * <p>用户反馈「项目历史列表太狭窄了，可以加高一点点」。原来的 48dp 是
 * 「在有限高度里尽量多塞」的取向，但两行内容（标题 + 元信息）挤在 48dp 里
 * 视觉上很紧，且已接近 44dp 的可点下限，长标题更容易误触隔壁行。
 *
 * <p>守的是**两行内容必须有足够高度**这个不变量，而不是某个具体数字：
 * 值可以再调，但不能回落到把两行压扁的量级。
 */
public final class SidebarMetricsTest {

    private static final String SIDEBAR =
            "app/src/main/java/com/zhizhu/zhicode/compose/ui/Sidebar.kt";

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

    /** 从 {@code private val NAME = 48.dp} 里取出数字。找不到返回 -1。 */
    private static int dpOf(String text, String name) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("val\\s+" + name + "\\s*=\\s*(\\d+)\\.dp")
                .matcher(text);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    public static void main(String[] args) throws Exception {
        String root = args.length > 0 ? args[0] : ".";
        String sidebar = stripComments(read(root, SIDEBAR));

        int session = dpOf(sidebar, "SessionRowMaxHeight");
        int row = dpOf(sidebar, "SidebarRowMaxHeight");

        require(session > 0, SIDEBAR + " 里找不到 SessionRowMaxHeight —— 断言过期，请更新守卫");
        require(row > 0, SIDEBAR + " 里找不到 SidebarRowMaxHeight —— 断言过期，请更新守卫");

        // 会话行是「标题 + 元信息」两行：48dp 是用户明确说"太狭窄"的值。
        require(session >= 54,
                "SessionRowMaxHeight 现在是 " + session + "dp。它承载**两行**内容，"
                        + "48dp 时用户反馈「项目历史列表太狭窄了」；"
                        + "改回 48 或更低会让两行重新贴在一起。");
        // 单行项目（工具栏/入口行）：也要够点。
        require(row >= 44,
                "SidebarRowMaxHeight 现在是 " + row + "dp，低于 44dp 的可点下限："
                        + "侧栏这些行是主要点击目标，太矮会不好按。");

        // 两个值的关系：会话行必须比单行行高，否则「加高一点点」没有落到会话列表上。
        require(session > row,
                "SessionRowMaxHeight(" + session + ") 必须大于 SidebarRowMaxHeight(" + row
                        + ")：会话行是两行内容，单行入口是一行。");
    }
}
