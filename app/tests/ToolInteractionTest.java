import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * 「工具操作菜单只作用在它自己那一行，且菜单里没有假动作」的守卫。
 *
 * <h2>钉住的三类退化</h2>
 *
 * <b>一、整组菜单冒充单行菜单</b>。`ToolGroupCard` 曾经把同一个 `onActions` 回调传给每一行，
 * 于是点某个工具的 `⋯` 弹出的是整组菜单，回调里也拿不到行号 —— 而"复制这条命令"
 * "展开这一条"这类动作全部作用在行上。现在要求回调带 toolId。
 *
 * <b>二、状态文字冒充菜单项</b>。TOOL_GROUP 那一档曾经是
 * `listOf("复制", if (completed) "已全部完成" else "执行中")` —— 第二项点下去什么都不发生，
 * 而用户会以为坏了。这里断言菜单文案里没有这类词。
 *
 * <b>三、决策表出现第二份实现</b>。"哪个状态该有哪些动作"只允许在 `ToolActions` 里算；
 * 界面若自己再判一次"有没有 diff ⇒ 显示哪个菜单"，两份迟早不一致。
 *
 * <p>与 `ZcodeProtocolTest` 一样，这里做的是**文本级**检查，因此：
 * 注释里提到这些词也会被抓到 —— 那正是想要的（提都不该在别处以那种形式提）。
 */
public final class ToolInteractionTest {

    private static final String UI_MODELS =
            "app/src/main/java/com/zhizhu/zhicode/compose/model/UiModels.kt";
    private static final String TOOL_ACTIONS =
            "app/src/main/java/com/zhizhu/zhicode/compose/model/ToolActions.kt";
    private static final String VM =
            "app/src/main/java/com/zhizhu/zhicode/compose/state/WorkspaceViewModel.kt";
    private static final String CARDS =
            "app/src/main/java/com/zhizhu/zhicode/compose/ui/chat/MessageCards.kt";
    private static final String CHAT_LIST =
            "app/src/main/java/com/zhizhu/zhicode/compose/ui/chat/ChatList.kt";
    private static final String CHAT_AREA =
            "app/src/main/java/com/zhizhu/zhicode/compose/ui/ChatArea.kt";
    private static final String FAST_SCRIPT = "test-jvm-fast.sh";

    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static String read(Path root, String relative) throws Exception {
        Path file = root.resolve(relative);
        require(Files.isRegularFile(file), "缺少文件: " + relative);
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    /** 去掉注释，但认得字符串字面量（正则版会被字符串里的 &#42;&#47; 骗到）。 */
    private static String stripComments(String text) {
        StringBuilder out = new StringBuilder(text.length());
        boolean inLine = false;
        boolean inBlock = false;
        boolean inString = false;
        boolean inChar = false;
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
            if (inString) {
                out.append(c);
                if (c == '\\') {
                    if (i + 1 < text.length()) out.append(text.charAt(++i));
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (inChar) {
                out.append(c);
                if (c == '\\') {
                    if (i + 1 < text.length()) out.append(text.charAt(++i));
                } else if (c == '\'') {
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
            if (c == '"') inString = true;
            if (c == '\'') inChar = true;
            out.append(c);
        }
        return out.toString();
    }

    private static String squash(String text) {
        return text.replaceAll("\\s+", "");
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int from = 0;
        while (true) {
            int at = text.indexOf(needle, from);
            if (at < 0) return count;
            count++;
            from = at + needle.length();
        }
    }

    public static void main(String[] args) throws Exception {
        Path root = Paths.get(args.length == 0 ? "." : args[0]).toAbsolutePath().normalize();

        String uiModels = stripComments(read(root, UI_MODELS));
        String toolActions = stripComments(read(root, TOOL_ACTIONS));
        String vm = stripComments(read(root, VM));
        String cards = stripComments(read(root, CARDS));
        String chatList = stripComments(read(root, CHAT_LIST));
        String chatArea = stripComments(read(root, CHAT_AREA));

        // ---- 1. 回调必须带 toolId（这一条正是那个"点单行弹整组菜单"的 bug）----
        require(squash(cards).contains("onToolActions:(String)->Unit"),
                CARDS + " 的 ToolGroupCard 必须接收 onToolActions: (String) -> Unit："
                        + "参数是**那一行**的 toolId —— 少了它，回调里根本不知道用户点的是哪一行");
        require(squash(cards).contains("onActions={onToolActions(tool.id)}"),
                CARDS + " 必须把每一行自己的 id 传出去（onActions = { onToolActions(tool.id) }）："
                        + "传整组的回调就是那个 bug 本身");
        require(!squash(cards).contains("onActions=onActions"),
                CARDS + " 不许把外层的 onActions 直接透传给每一行：那会让所有行共用一个动作");

        // 从 ChatList 一路到 ChatArea 都要带上 toolId，断在中间任何一处都是"点了没反应"。
        require(squash(chatList).contains("onToolActions:(ChatItem,String)->Unit"),
                CHAT_LIST + " 必须声明 onToolActions: (ChatItem, String) -> Unit");
        require(squash(chatList).contains("onToolActions(item,toolId)"),
                CHAT_LIST + " 必须把 (item, toolId) 一起转发出去");
        require(squash(chatArea).contains("onToolActions=viewModel::showToolActions"),
                CHAT_AREA + " 必须把 onToolActions 接到 viewModel.showToolActions");

        // ---- 2. ViewModel 侧：单行菜单的入口与目标 ----
        require(squash(vm).contains("funshowToolActions(item:ChatItem,toolId:String)"),
                VM + " 必须有 showToolActions(item, toolId)："
                        + "签名里没有 toolId 就无法为某一行构造菜单");
        require(squash(vm).contains("pendingToolAction"),
                VM + " 必须记住待执行的工具操作目标："
                        + "选择器回调里只能拿到选项文案，不记住就会作用于别的工具");
        require(squash(vm).contains("ChoiceIntent.TOOL_ACTION"),
                VM + " 必须用独立的 ChoiceIntent.TOOL_ACTION 分派工具动作："
                        + "混进 MESSAGE_ACTION 会让两类动作争同一个目标字段");
        require(squash(uiModels).contains("TOOL_ACTION,"),
                UI_MODELS + " 的 ChoiceIntent 必须有 TOOL_ACTION");
        require(squash(vm).contains("funapplyToolAction("),
                VM + " 必须有 applyToolAction 执行菜单项");

        // 菜单内容必须来自纯逻辑层，而不是在 VM 里现拼。
        require(squash(vm).contains("ToolActions.options(toolActionFlags(tool))"),
                VM + " 的菜单项必须由 ToolActions.options 决定："
                        + "在 VM 里另拼一份，两份菜单迟早不一致");
        require(squash(vm).contains("ToolText.isFileDiff(tool.toolName,tool.output)"),
                VM + " 的 hasDiff 判据必须复用 ToolText.isFileDiff："
                        + "自己再判一次会出现「显示成 diff 却没有复制 Diff 这一项」");

        // ---- 3. 菜单里不许出现状态文字冒充动作 ----
        // 只查 TOOL_GROUP 那一档附近的文本：banned 词在别处（比如"执行中"用于状态行）是合理的。
        int groupAt = vm.indexOf("else -> listOf(");
        require(groupAt >= 0, VM + " 找不到 TOOL_GROUP 的选项分支（结构被改过了？）");
        int groupEnd = vm.indexOf("\n", groupAt);
        String groupOptions = groupAt + 1 < groupEnd ? vm.substring(groupAt, groupEnd) : vm.substring(groupAt);
        for (String banned : new String[]{"已全部完成", "执行中"}) {
            require(!groupOptions.contains(banned),
                    VM + " 的工具组菜单里不许出现状态文字 \"" + banned + "\"："
                            + "那不是动作，点下去什么都不发生，而用户会以为坏了。"
                            + "单个工具的动作走 showToolActions");
        }

        // 决策表只允许一处实现：这些文案是契约，散落就说明有第二份。
        require(squash(toolActions).contains("constvalCOPY_COMMAND=\"复制命令\""),
                TOOL_ACTIONS + " 的菜单文案必须是具名常量（契约）：VM 按它们分派动作");
        int labelDefs = countOccurrences(squash(vm), "\"复制命令\"");
        require(labelDefs == 0,
                VM + " 里不许再出现菜单文案字面量（\"复制命令\" 出现 " + labelDefs + " 处）："
                        + "文案只有 ToolActions 一处定义，VM 要用就用它的常量");

        // ---- 4. 耗时：必须有起点，且界面侧要续走 ----
        require(squash(uiModels).contains("valstartedAtMs:Long=0L"),
                UI_MODELS + " 的 ToolActivity 必须有 startedAtMs："
                        + "引擎的 elapsedMs 只在有输出时更新，没有起点就无法在界面侧续走");
        require(squash(vm).contains("startedAtMs=System.currentTimeMillis()"),
                VM + " 登记工具时必须记下 startedAtMs");
        require(squash(cards).contains("ToolActions.displayElapsedMs("),
                CARDS + " 的耗时显示必须走 ToolActions.displayElapsedMs："
                        + "取值规则（取较大者、不许倒退）只有那一处实现");
        require(squash(cards).contains("rememberRunningClock("),
                CARDS + " 必须有界面侧的运行时钟：光靠引擎推的值会让长时间不吐字的命令冻住");
        // 没有运行中的工具时不许继续排队 —— 常驻定时器会一直触发重组。
        require(squash(cards).contains("item.tools.any{!it.completed}"),
                CARDS + " 的运行时钟必须以「还有未完成的工具」为条件");

        // ---- 5. 秒级回路 ----
        String script = read(root, FAST_SCRIPT);
        require(script.contains("model/ToolActions.kt"),
                FAST_SCRIPT + " 必须把 ToolActions.kt 列进 MAIN_KT_SOURCES："
                        + "决策表要能在秒级回路里跑");
        require(script.contains("ToolActionsTest"),
                FAST_SCRIPT + " 必须把 ToolActionsTest 列进默认测试");
        require(!toolActions.contains("import android."),
                TOOL_ACTIONS + " 不许 import android.*：它是纯逻辑，"
                        + "带上 Android 就只能靠分钟级的 Gradle 单测跑");
    }
}
