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
 * <b>四、菜单退化成居中对话框</b>。菜单曾经经过 `choicePicker` 中转，而
 * `ChoiceIntent.TOOL_ACTION` 不在 `ChoicePickerState.isActionMenu` 的名单里 ——
 * 于是点某一行的 `⋯`，弹出来的是**屏幕正中的对话框**。现在它必须是
 * Miuix 下拉菜单（`ZhiIconDropdownMenu`，与输入器底排同一组件）。
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

        // ---- 1. `⋯` 必须是 Miuix 下拉菜单触发器，且回调必须带 toolId ----
        //
        // 这一节钉的是两件事，它们曾经一起坏掉：
        //   a) 每一行的 `⋯` 用的是同一个"整组"回调 —— 点单行弹整组菜单，回调里也不知道
        //      用户点的是哪一行；
        //   b) 菜单经过 `choicePicker` 中转 —— 而 `ChoiceIntent.TOOL_ACTION` 不在
        //      `isActionMenu` 的名单里，于是**退化成屏幕中央的对话框**：一个只作用于
        //      某一行的动作，弹窗却出现在屏幕正中。
        // 现在菜单由那一行自己画（Miuix `OverlayIconDropdownMenu`，与输入器底排的
        // `+` / 权限 / 推理同一个组件），选中的文案随 (itemId, toolId) 一起回到 ViewModel。
        require(squash(cards).contains("ZhiIconDropdownMenu("),
                CARDS + " 的 ⋯ 必须是 Miuix 下拉菜单触发器（ZhiIconDropdownMenu）："
                        + "与输入器底排的 + / 权限 / 推理同一个组件。用普通 IconButton + "
                        + "外部状态就会重演「只作用于某一行的动作却弹出居中对话框」");
        require(squash(cards).contains("ToolActions.options(menuFlags)"),
                CARDS + " 的菜单项必须由 ToolActions.options 决定："
                        + "在界面里另拼一份，两份菜单迟早不一致");
        require(squash(cards).contains("ToolActions.flags("),
                CARDS + " 的菜单判据必须来自 ToolActions.flags —— 它是界面与 VM 共用的"
                        + "唯一一份「哪个状态该有哪个动作」映射");
        require(squash(cards).contains("ToolText.isFileDiff(toolName,output)"),
                CARDS + " 的 hasDiff 判据必须复用 ToolText.isFileDiff："
                        + "自己再判一次会出现「显示成 diff 却没有复制 Diff 这一项」");
        require(!squash(cards).contains("choicePicker"),
                CARDS + " 不许再碰 choicePicker：工具菜单不再经过选择器中转");
        require(squash(cards).contains("onToolAction:(String,String)->Unit"),
                CARDS + " 的 ToolGroupCard 必须接收 onToolAction: (String, String) -> Unit："
                        + "参数是**那一行**的 toolId 与菜单文案 —— 少了 toolId，回调里根本"
                        + "不知道用户点的是哪一行");
        require(squash(cards).contains("onToolAction={label->onToolAction(tool.id,label)}"),
                CARDS + " 必须把每一行自己的 id 传出去："
                        + "onToolAction = { label -> onToolAction(tool.id, label) }");
        require(!squash(cards).contains("onToolAction=onToolAction"),
                CARDS + " 不许把外层的回调直接透传给每一行：那会让所有行共用一组动作");
        // 每行必须 `key(tool.id)`：工具是边跑边追加的，按位置归属会让"打开的菜单 /
        // 展开的输出"串到新插入的那一行上。
        require(squash(cards).contains("key(tool.id){ToolRow("),
                CARDS + " 的每一行必须 `key(tool.id) { ToolRow(...) }`："
                        + "工具边跑边追加，按位置归属会让行内状态串行");

        // 从 ChatList 一路到 ChatArea 都要带上 toolId 与文案，断在中间任何一处都是"点了没反应"。
        require(squash(chatList).contains("onToolAction:(ChatItem,String,String)->Unit"),
                CHAT_LIST + " 必须声明 onToolAction: (ChatItem, String, String) -> Unit");
        require(squash(chatList).contains("onToolAction(item,toolId,label)"),
                CHAT_LIST + " 必须把 (item, toolId, label) 一起转发出去");
        require(squash(chatArea).contains(
                        "onToolAction={item,toolId,label->viewModel.applyToolAction(item.id,toolId,label)}"),
                CHAT_AREA + " 必须把 onToolAction 接到 viewModel.applyToolAction(itemId, toolId, label)");
        require(squash(chatArea).contains("applyToolAction(item.id,toolId,label)"),
                CHAT_AREA + " 必须把**消息 id**（不是整条 ChatItem）交给 ViewModel："
                        + "VM 只需要能在 transcript 里定位目标的键");

        // ---- 2. ViewModel 侧：只有"执行"，不再有"弹菜单" ----
        require(squash(vm).contains("funapplyToolAction(itemId:String,toolId:String,label:String)"),
                VM + " 必须有 applyToolAction(itemId, toolId, label)："
                        + "目标与文案一起从界面传进来，一进来就能执行");
        require(!squash(vm).contains("pendingToolAction"),
                VM + " 不许再有 pendingToolAction：它存在的唯一理由是"
                        + "「菜单经过选择器中转、回调里只剩文案」，那条路已经删掉了");
        require(!squash(vm).contains("TOOL_ACTION"),
                VM + " 不许再出现 ChoiceIntent.TOOL_ACTION："
                        + "工具菜单不再经过 choicePicker");
        require(!squash(uiModels).contains("TOOL_ACTION,"),
                UI_MODELS + " 的 ChoiceIntent 不许再有 TOOL_ACTION："
                        + "`isActionMenu` 只认 MESSAGE_ACTION / SESSION_ACTION，"
                        + "用 TOOL_ACTION 的后果就是工具菜单退化成居中对话框");
        require(!squash(chatArea).contains("showToolActions"),
                CHAT_AREA + " 不许再引用 showToolActions（那个入口已经不存在）");
        require(!squash(vm).contains("ToolActionFlags("),
                VM + " 不许自己构造 ToolActionFlags：判据的构造只有 ToolActions.flags 一份");

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
