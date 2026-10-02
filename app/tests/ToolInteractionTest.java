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
    private static final String SESSION_READER =
            "app/src/main/java/com/zhizhu/zhicode/compose/data/SessionReader.kt";
    private static final String LIVE_OUTPUT =
            "app/src/main/java/com/zhizhu/zhicode/compose/model/LiveOutput.kt";
    private static final String ERROR_SUMMARY =
            "app/src/main/java/com/zhizhu/zhicode/compose/model/ErrorSummary.kt";
    private static final String CHAT_NAV =
            "app/src/main/java/com/zhizhu/zhicode/compose/model/ChatNavigation.kt";
    private static final String AUTO_FOLLOW =
            "app/src/main/java/com/zhizhu/zhicode/compose/ui/chat/AutoFollow.kt";
    private static final String MARKDOWN =
            "app/src/main/java/com/zhizhu/zhicode/compose/ui/Markdown.kt";
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
        String reader = stripComments(read(root, SESSION_READER));
        String liveOutput = stripComments(read(root, LIVE_OUTPUT));
        String errorSummary = stripComments(read(root, ERROR_SUMMARY));
        String chatNav = stripComments(read(root, CHAT_NAV));
        String autoFollow = stripComments(read(root, AUTO_FOLLOW));
        String markdown = stripComments(read(root, MARKDOWN));

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

        // ---- 4. 耗时：必须有起点（单调时钟），且界面侧要续走 ----
        require(squash(uiModels).contains("valstartedAtMs:Long=0L"),
                UI_MODELS + " 的 ToolActivity 必须有 startedAtMs："
                        + "引擎的 elapsedMs 只在有输出时更新，没有起点就无法在界面侧续走");
        require(squash(vm).contains("startedAtMs=SystemClock.elapsedRealtime()"),
                VM + " 登记工具时必须用**单调时钟**记下 startedAtMs"
                        + "（SystemClock.elapsedRealtime()）：用墙上时钟的话，"
                        + "系统对时或用户改时间会让秒表跳一大步甚至变负");
        require(!squash(vm).contains("startedAtMs=System.currentTimeMillis()"),
                VM + " 不许用 currentTimeMillis 当耗时基准：它会被拨动");
        require(squash(cards).contains("SystemClock.elapsedRealtime()"),
                CARDS + " 的界面时钟必须与 startedAtMs 同一个时钟（elapsedRealtime）："
                        + "混用两种时钟，「现在 - 起点」就没有意义");
        require(squash(cards).contains("ToolText.formatElapsed("),
                CARDS + " 的耗时文案必须走 ToolText.formatElapsed（mm:ss，与原版一致）："
                        + "曾经这里有个私有的 \"7.0s\" 版本，两版截图对不上");
        require(!squash(cards).contains("privatefunformatElapsed("),
                CARDS + " 不许再留第二份 formatElapsed 实现");
        require(squash(cards).contains("ToolActions.displayElapsedMs("),
                CARDS + " 的耗时取值必须走 ToolActions.displayElapsedMs："
                        + "取值规则（取较大者、不许倒退）只有那一处实现");
        require(squash(cards).contains("rememberRunningClock("),
                CARDS + " 必须有界面侧的运行时钟：光靠引擎推的值会让长时间不吐字的命令冻住");
        // 没有运行中的工具时不许继续排队 —— 常驻定时器会一直触发重组。
        require(squash(cards).contains("item.tools.any{!it.completed}"),
                CARDS + " 的运行时钟必须以「还有未完成的工具」为条件");

        // ---- 4b. 运行中就看得见输出（对齐参考实现 addToolCard 的运行分支）----
        //
        // 只显示"运行中 · 00:12"是不够的：一条跑两分钟都不吐字的命令与一条正在刷日志的
        // 命令在界面上长得一模一样，用户没法判断它在干什么。
        require(squash(cards).contains("LiveOutput.preview(") && squash(cards).contains("LiveOutput.tail("),
                CARDS + " 运行中的行内必须摊出实时输出：命令类按行取（LiveOutput.preview）、"
                        + "其他工具按尾部字符取（LiveOutput.tail）");
        require(squash(cards).contains("\"等待程序输出…\""),
                CARDS + " 命令类工具还没有输出时必须显示「等待程序输出…」："
                        + "留一片空白会被当成卡死");
        require(squash(cards).contains("LiveOutput.volume("),
                CARDS + " 运行标签必须报出输出体量（LiveOutput.volume）："
                        + "字符数是单调递增的，屏幕那几行没变时它也在动");

        // 运行标签的文案与"两个计数"缺一不可。
        require(squash(vm).contains("stdoutChars=buffer.stdoutChars")
                        && squash(vm).contains("stderrChars=buffer.stderrChars"),
                VM + " 必须把 stdout/stderr 两个计数落进 state："
                        + "运行标签要显示「错误输出 0 B」，只看 output 总长做不到");
        require(squash(uiModels).contains("valstdoutChars:Int=0")
                        && squash(uiModels).contains("valstderrChars:Int=0"),
                UI_MODELS + " 的 ToolActivity 必须有 stdoutChars / stderrChars");
        // 标记只在流切换时插一次 —— 判据在纯逻辑层，所以这里查它有没有被绕过。
        require(squash(vm).contains("LiveOutput.append("),
                VM + " 的实时缓冲必须走 LiveOutput.append："
                        + "它负责换行归一化与「[stderr] 只在切换时插一次」");
        require(!squash(vm).contains("buffer.append(\"[stderr]\\n\")"),
                VM + " 不许再每个 stderr chunk 都插一行 [stderr]："
                        + "连续报错的命令会把输出刷成一片标记");

        // ---- 4c. 展开态要看得见完整命令 ----
        require(squash(uiModels).contains("valcommand:String=\"\""),
                UI_MODELS + " 的 ToolActivity 必须有 command（原始命令行）");
        require(squash(cards).contains("activity.expanded&&activity.command.isNotBlank()"),
                CARDS + " 展开态必须显示**完整**命令：折叠摘要被 truncateCommand + "
                        + "shorten(190) 截过，展开还看它等于没展开");
        require(squash(reader).contains("command=input?.optString(\"command\",\"\")"),
                SESSION_READER + " 恢复历史时必须读回命令行："
                        + "不读的话展开一条旧 Bash 只能看到被截短的摘要");

        // ---- 4d. 恢复历史时给"没有结果的工具"收口 ----
        //
        // 会话记录里可能留着只有调用、没有结果的事件（进程被杀、写入中断、旧格式）。
        // 不收口的话，恢复出来的那条工具会**永远**转圈显示「运行中…」——
        // 一条几天前的记录里挂着一个"正在运行"，看着就像卡死。
        require(squash(reader).contains("\"会话记录未包含该工具的结果。\""),
                SESSION_READER + " 恢复历史时必须给没有结果的工具补一句说明"
                        + "（「会话记录未包含该工具的结果。」）："
                        + "否则界面上会永远挂着一个转圈的「运行中…」");
        require(squash(reader).contains("failed=true"),
                SESSION_READER + " 恢复历史时那些工具必须标成失败："
                        + "它们的状态是「记录里没有」，不是「还在跑」");

        // ---- 4e. 错误摘要要挑"有用"的那一行 ----
        require(squash(cards).contains("ErrorSummary.firstUsefulLine("),
                CARDS + " 的错误摘要必须转发到 ErrorSummary.firstUsefulLine："
                        + "「哪一行才是有用信息」是纯逻辑（有单测），"
                        + "取第一条非空行会得到 'Reading package lists...' 这种没用的摘要");
        // 关键词表只允许有一处。
        require(squash(errorSummary).contains("\"permissiondenied\"")
                        && squash(errorSummary).contains("\"unableto\"")
                        && squash(errorSummary).contains("\"notfound\""),
                ERROR_SUMMARY + " 的关键词表必须包含 permission denied / unable to / not found："
                        + "这三类正好覆盖权限、依赖与路径问题");
        require(!squash(cards).contains("\"permissiondenied\""),
                CARDS + " 里不许再出现错误关键词：关键词表只有 ErrorSummary 一处");

        // ---- 6. 对话流的对位行为（同一批参考实现里"值得移植"的几条）----
        //
        // 6a. 发消息后无条件吸底：用户上翻历史时发出的消息不能落在屏幕外。
        require(squash(uiModels).contains("valscrollToBottomToken:Long=0L"),
                UI_MODELS + " 必须有 scrollToBottomToken："
                        + "「暂停跟随」住在 ChatList 自己的 remember 里，"
                        + "界面外部只有靠这个令牌才能让它恢复");
        require(squash(vm).contains("scrollToBottomToken=it.scrollToBottomToken+1")
                        && squash(vm).contains("scrollToBottomToken=s.scrollToBottomToken+1"),
                VM + " 两条发送路径（普通发送 / 预输入）都必须递增 scrollToBottomToken");
        require(squash(chatList).contains("rememberAutoFollow(listState,forceFollowToken=state.scrollToBottomToken)"),
                CHAT_LIST + " 必须把令牌交给 rememberAutoFollow："
                        + "只递增而没人用，等于没做");
        require(squash(autoFollow).contains("if(forceFollowToken>0L)autoFollow.value=true"),
                "AutoFollow.kt 必须真的把令牌翻译成「恢复跟随」");

        // 6b. 「重试上一问」必须从被点的那条往前找。
        //
        // 只说"签名里有 from"是不够的（那样把函数体里的定位换回"取最后一条"也能通过）。
        // 真正的判据是：定位**只有一处实现**且在那个纯逻辑文件里，VM 只做翻译与调用。
        require(squash(vm).contains("privatefunretryLastUserPrompt(from:ChatItem?)"),
                VM + " retryLastUserPrompt 必须接收被点的那条消息："
                        + "取全对话最后一条用户消息时，对旧回复点「重试」会重发**最新**那个问题");
        require(squash(vm).contains("ChatNavigation.previousUserPrompt("),
                VM + " 的定位必须走 ChatNavigation.previousUserPrompt："
                        + "在 VM 里就地写循环的话，「点 A 发了 B」这个 bug 只能靠肉眼发现");
        require(squash(chatNav).contains("downTo0"),
                CHAT_NAV + " 必须真的从锚点往前遍历（downTo）："
                        + "写成从后往前取第一条就退回了旧行为");
        require(!squash(vm).contains("transcript.lastOrNull{it.kind==ChatKind.USER"),
                VM + " 不许再出现「取最后一条用户消息」的写法");
        require(squash(vm).contains("->retryLastUserPrompt(target)"),
                VM + " 必须把消息操作的目标交给 retryLastUserPrompt");

        // 6c. 代码块要能单独复制。
        require(squash(markdown).contains("Clipboard.copy("),
                MARKDOWN + " 的代码块必须有「复制」："
                        + "在这之前唯一的复制途径是长按整条消息，那会把整篇回答一起复制走");
        require(squash(markdown).contains("\"已复制\"") && squash(markdown).contains("CopyFeedbackMs"),
                MARKDOWN + " 复制后必须给出「已复制」反馈并在约 520ms 后复原"
                        + "（对齐参考实现 MarkdownRenderer.postDelayed(..., 520)）");

        // ---- 5. 秒级回路 ----
        String script = read(root, FAST_SCRIPT);
        require(script.contains("model/ToolActions.kt"),
                FAST_SCRIPT + " 必须把 ToolActions.kt 列进 MAIN_KT_SOURCES："
                        + "决策表要能在秒级回路里跑");
        require(script.contains("ToolActionsTest"),
                FAST_SCRIPT + " 必须把 ToolActionsTest 列进默认测试");
        require(script.contains("model/ToolKind.kt"),
                FAST_SCRIPT + " 必须把 ToolKind.kt 列进 MAIN_KT_SOURCES："
                        + "ToolActions.flags 按它判「是不是命令类」，少了它就编译不过");
        require(script.contains("model/LiveOutput.kt"),
                FAST_SCRIPT + " 必须把 LiveOutput.kt 列进 MAIN_KT_SOURCES："
                        + "实时输出的累积规则要能在秒级回路里跑");
        require(script.contains("LiveOutputTest"),
                FAST_SCRIPT + " 必须把 LiveOutputTest 列进默认测试");
        require(script.contains("model/ErrorSummary.kt") && script.contains("ErrorSummaryTest"),
                FAST_SCRIPT + " 必须把 ErrorSummary.kt 与 ErrorSummaryTest 一起列进快回路："
                        + "「哪一行才是有用信息」这类判据只有逐条断言才靠得住");
        require(script.contains("model/ChatNavigation.kt") && script.contains("ChatNavigationTest"),
                FAST_SCRIPT + " 必须把 ChatNavigation.kt 与 ChatNavigationTest 一起列进快回路："
                        + "「点 A 发了 B」只能靠行为测试发现");
        require(!toolActions.contains("import android."),
                TOOL_ACTIONS + " 不许 import android.*：它是纯逻辑，"
                        + "带上 Android 就只能靠分钟级的 Gradle 单测跑");
        require(!liveOutput.contains("import android."),
                LIVE_OUTPUT + " 不许 import android.*：实时输出的累积/裁剪是纯字符串运算，"
                        + "放在纯逻辑层才能在秒级回路里逐条钉住（见 LiveOutputTest）");
        require(!errorSummary.contains("import android."),
                ERROR_SUMMARY + " 不许 import android.*：错误摘要的挑行规则是纯字符串运算");
        require(!chatNav.contains("import android."),
                CHAT_NAV + " 不许 import android.*：定位是纯逻辑，"
                        + "带上 Android 就只能靠分钟级的 Gradle 单测跑");
    }
}
