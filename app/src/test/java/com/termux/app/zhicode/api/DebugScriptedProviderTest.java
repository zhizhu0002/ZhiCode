package com.termux.app.zhicode.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.zhicode.model.AssistantTurn;
import com.termux.app.zhicode.model.ToolCall;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 脚本化传输里「工具清单」那条链路的断言。
 *
 * <h3>为什么要测一个"调试用"的类</h3>
 *
 * 它虽然不是产品代码，但它产出的是**界面素材**，而且有两件事错了都很难看出来：
 *
 * <ol>
 *   <li><b>清单漏工具</b>：调试时最怕"看着有、其实没列全" —— 我们会据此判断
 *       "某个工具没被注册/没下发"，从而去改根本没错的地方。所以这里断言
 *       **下发几个就必须列出几个**，且不在下发名单里的（白名单挡掉的）绝不出现在表格里。</li>
 *   <li><b>表格被说明里的竖线拆散</b>：工具说明里既有换行也有 `|`（正则、管道、表格本身），
 *       不转义的话渲染出来从那一行开始整张表错位。这不是渲染器的错，是素材没单元格化。</li>
 * </ol>
 *
 * <p>还有一条**安全**断言：这个场景会真的执行一批工具，而调试时人是会随手点「确认」的，
 * 所以那批名单里不许出现写操作类工具（Write / Edit / Delete / Move / Bash …）。
 */
public final class DebugScriptedProviderTest {

    /** 会改动工程或系统的工具：调试脚本里的"真执行"批次不许包含它们。 */
    private static final Set<String> WRITE_TOOLS = new HashSet<>(Arrays.asList(
            "Write", "Edit", "MultiEdit", "Copy", "Move", "Mkdir", "Delete",
            "Bash", "RootBash", "TermuxRepair", "EnterWorktree", "ExitWorktree",
            "AndroidIntent", "Sandbox", "UiCanvas", "Debug", "Agent"));

    // ------------------------------------------------------------------ 清单

    @Test
    public void toolScenarioListsEveryOfferedTool() throws Exception {
        JSONArray tools = tools(
                schema("Grep", "Search file contents with a Java compatible regular expression."),
                schema("Read", "Read a UTF-8 text file with line numbers.\nSecond paragraph."),
                schema("Bash", "Run a shell command; pipe is | and it is documented here."),
                schema("VeryLongToolNameForTruncation", "A description that easily exceeds the cell width limit."));

        String text = textOf(closingTurn(tools));

        for (String name : new String[]{"Grep", "Read", "Bash", "VeryLongToolNameForTruncation"}) {
            // 超宽工具名会被截断补齐省略号 —— 断言的是"这一行在"，而不是"名字原样在"。
            String cell = name.length() <= 24 ? name : name.substring(0, 23) + "…";
            assertTrue("清单必须列出下发过的每一个工具，缺：" + name, text.contains("| `" + cell + "` |"));
        }
        assertTrue("超宽说明要截断（否则会把表格列撑破）", text.contains("…"));
        assertTrue("清单要带上条数：否则「列全了没有」只能靠数着看",
                text.contains("（4 个）"));
    }

    @Test
    public void catalogRowsAreSingleLineAndPipesEscaped() throws Exception {
        // 第一句里同时有换行与竖线：这两样都会拆散表格，必须在这一格里处理掉。
        JSONArray tools = tools(
                schema("Bash", "Line one\nLine two | with a pipe."));

        String text = textOf(closingTurn(tools));

        List<String> rows = new ArrayList<>();
        for (String line : text.split("\n")) {
            if (line.startsWith("| ")) rows.add(line);
        }
        // 表头 + 分隔行 + 1 条工具
        assertEquals("表格应有的行数（表头 + 分隔 + 每条工具一行）", 3, rows.size());
        for (String row : rows) {
            assertEquals("每一行必须恰好三根竖线（两根列分隔 + 说明里那条被转义）",
                    3, countPipes(row) - countOccurrences(row, "\\|"));
        }
        String toolRow = rows.get(2);
        assertTrue("说明里的竖线必须被转义，否则渲染器会当成列分隔符", toolRow.contains("\\|"));
        assertTrue("说明里的换行必须被压平成空格：" + toolRow, toolRow.contains("Line one Line two"));
        assertTrue("每一行都得是完整一行（| 开头、| 结尾）：" + toolRow,
                toolRow.startsWith("| `") && toolRow.endsWith(" |"));
    }

    @Test
    public void emptyToolListIsReportedAsSuch() throws Exception {
        String text = textOf(closingTurn(new JSONArray()));
        assertTrue("没有下发工具时必须明说 —— 否则会被当成「模型不肯调工具」，查错方向从一开始就错",
                text.contains("没有下发任何工具"));
    }

    // ------------------------------------------------------------------ 真执行的那批

    @Test
    public void readOnlyBatchOnlyCallsOfferedTools() throws Exception {
        JSONArray tools = tools(schema("Grep", "grep"), schema("Read", "read"), schema("Bash", "bash"));
        AssistantTurn first = firstTurn(tools);

        assertFalse("只读批次不该是空的（下发名单里有只读工具）", first.toolCalls.isEmpty());
        for (ToolCall call : first.toolCalls) {
            assertTrue("只调用本次真的下发过的工具，否则只会换来一行 Unknown tool：" + call.name,
                    hasTool(tools, call.name));
        }
        assertEquals("step 0 有工具调用 → 停止原因必须是 tool_use", "tool_use", first.stopReason);
    }

    @Test
    public void readOnlyBatchNeverWrites() throws Exception {
        // 给一份"全都下发"的名单，看批次会不会自己收敛。
        JSONArray tools = tools(schema("Grep", "grep"), schema("Read", "read"), schema("Bash", "bash"),
                schema("Write", "write"), schema("Delete", "delete"));
        AssistantTurn first = firstTurn(tools);

        assertFalse(first.toolCalls.isEmpty());
        for (ToolCall call : first.toolCalls) {
            assertFalse("调试脚本的\"真执行\"批次里出现了会改动工程的工具：" + call.name,
                    WRITE_TOOLS.contains(call.name));
        }
    }

    // ------------------------------------------------------------------ 关键词

    @Test
    public void keywordPicksScenario() {
        assertEquals("输入「工具」必须走全工具清单这条链路",
                DebugScriptedProvider.Scenario.ALL_TOOLS, DebugScriptedProvider.Scenario.pick("工具"));
        assertEquals("不带关键词走默认全链路",
                DebugScriptedProvider.Scenario.FULL_CHAIN, DebugScriptedProvider.Scenario.pick("随便说点什么"));
        assertEquals(DebugScriptedProvider.Scenario.TASKS, DebugScriptedProvider.Scenario.pick("帮我列任务"));
        assertEquals(DebugScriptedProvider.Scenario.MARKDOWN, DebugScriptedProvider.Scenario.pick("来段长文"));
    }

    /**
     * **会话中途换场景**：这是"输入『工具』什么都没发生"的那条真因。
     *
     * <p>上下文里已经有好几轮对话时，关键词必须取自**最后一条提问**；
     * 取第一条的话，只有整段会话的第一句话能选场景 —— 之后发什么都不会变，
     * 用户看到的就是"打了『工具』但链接没动"。
     */
    @Test
    public void scenarioComesFromTheLatestPrompt() throws Exception {
        JSONArray messages = new JSONArray()
                .put(new JSONObject().put("role", "user").put("content", "随便聊两句"))
                .put(new JSONObject().put("role", "assistant").put("content", "好的"))
                .put(new JSONObject().put("role", "user").put("content", "列一下工具"));

        AssistantTurn turn = new DebugScriptedProvider()
                .createMessage(null, "system", messages, tools(schema("Grep", "grep")), null);

        assertFalse("换场景后必须从本轮的**第 0 步**重新开始，而不是接着上一轮往下跑："
                        + "否则新场景的第一步永远不会被执行（表现是「没有工具跑起来」）",
                turn.toolCalls.isEmpty());
        assertEquals("只读批次里的工具才是本轮第 0 步该产出的东西",
                "Grep", turn.toolCalls.get(0).name);
    }

    /**
     * **工具结果不是新提问**。
     *
     * <p>工具结果以 {@code role=user} 追加（协议要求），如果把这种消息当成新提问，
     * 脚本每一步都会退回第 0 步 —— 引擎的工具循环就永远出不来（死循环）。
     * 这条断言守的正是那个循环的出口。
     */
    @Test
    public void toolResultsDoNotResetTheStep() throws Exception {
        JSONArray messages = new JSONArray()
                .put(new JSONObject().put("role", "user").put("content", "工具"))
                .put(new JSONObject().put("role", "assistant").put("content", "（第 0 步：只读批次）"))
                .put(new JSONObject().put("role", "user").put("content", new JSONArray()
                        .put(new JSONObject().put("type", "tool_result")
                                .put("tool_use_id", "all-1").put("content", "匹配到 3 处"))));

        AssistantTurn turn = new DebugScriptedProvider()
                .createMessage(null, "system", messages, tools(schema("Grep", "grep")), null);

        assertTrue("工具结果之后应当走到收尾这一步（第 0 步已经用掉了）", turn.toolCalls.isEmpty());
        assertEquals("收尾必须是最终回复，否则引擎会继续循环下去", "end_turn", turn.stopReason);
        assertTrue("收尾正文就是全工具清单", textOf(turn).contains("全部工具清单"));
    }

    /** 一轮之内工具循环会多次请求：步数必须逐次往前走。 */
    @Test
    public void stepAdvancesInsideToolLoop() throws Exception {
        JSONArray messages = new JSONArray()
                .put(new JSONObject().put("role", "user").put("content", "随便说点什么"))
                .put(new JSONObject().put("role", "assistant").put("content", "（第 0 步）"))
                .put(new JSONObject().put("role", "user").put("content", new JSONArray()
                        .put(new JSONObject().put("type", "tool_result")
                                .put("tool_use_id", "chain-1").put("content", "ok"))));

        AssistantTurn second = new DebugScriptedProvider()
                .createMessage(null, "system", messages, tools(schema("Read", "read")), null);

        assertFalse("第 1 步（默认全链路是读取）必须有工具调用", second.toolCalls.isEmpty());
        assertEquals("Read", second.toolCalls.get(0).name);
    }

    // ------------------------------------------------------------------ 选择窗口

    /**
     * 「提问」场景必须产出一次**多选**的 {@code AskUserQuestion}。
     *
     * <p>这个窗口原先只有真模型肯调该工具时才出现，于是"多选行的勾选框对不对"
     * 只能碰运气复现。脚本给出同名 tool_use 就能走完整链路
     * （窗口 → 用户作答 → 工具结果 → 收尾）—— 前提是字段名与引擎那份 schema 对得上，
     * 写歪了不会报错，只会静默少一个窗口。
     */
    @Test
    public void questionScenarioAsksAMultiSelectQuestion() throws Exception {
        AssistantTurn first = firstTurn(tools(schema("Read", "read")), "提问");

        assertEquals("「提问」场景第 0 步必须产出一次工具调用", 1, first.toolCalls.size());
        ToolCall call = first.toolCalls.get(0);
        assertEquals("必须叫 AskUserQuestion（引擎按名字分派，名字写歪只会得到 Unknown tool）",
                "AskUserQuestion", call.name);
        assertEquals("有工具调用 → 停止原因必须是 tool_use", "tool_use", first.stopReason);

        JSONArray questions = call.input.optJSONArray("questions");
        assertNotNull("入参必须是 questions[]（引擎 schema 的必填字段）", questions);
        JSONObject question = questions.optJSONObject(0);
        assertNotNull(question);
        assertTrue("必须是多选：单选不画勾选框，用它测等于没测",
                question.optBoolean("multiSelect", false));
        assertTrue("要有提问正文（窗口顶部的加粗提问）",
                !question.optString("question").isEmpty());
        assertTrue("要有窗口标题（header）", !question.optString("header").isEmpty());

        JSONArray options = question.optJSONArray("options");
        assertNotNull(options);
        assertTrue("选项至少两条，否则看不出「多选」这件事", options.length() >= 2);
        for (int i = 0; i < options.length(); i++) {
            JSONObject option = options.optJSONObject(i);
            assertNotNull(option);
            assertFalse("每个选项必须有 label（引擎 schema 的必填字段）",
                    option.optString("label").isEmpty());
        }
        assertTrue("要留一条**没有说明**的选项：说明可空，少了它行高会变 —— 那正是要看的东西之一",
                hasOptionWithoutDescription(options));
    }

    private static boolean hasOptionWithoutDescription(JSONArray options) {
        for (int i = 0; i < options.length(); i++) {
            JSONObject option = options.optJSONObject(i);
            if (option != null && option.optString("description").isEmpty()) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ 工具

    private static JSONObject schema(String name, String description) throws Exception {
        return new JSONObject()
                .put("name", name)
                .put("description", description)
                .put("input_schema", new JSONObject().put("type", "object"));
    }

    private static JSONArray tools(JSONObject... schemas) {
        JSONArray array = new JSONArray();
        for (JSONObject schema : schemas) array.put(schema);
        return array;
    }

    private static boolean hasTool(JSONArray tools, String name) {
        for (int i = 0; i < tools.length(); i++) {
            JSONObject schema = tools.optJSONObject(i);
            if (schema != null && name.equals(schema.optString("name"))) return true;
        }
        return false;
    }

    /** 脚本第 0 步（还没发生过助手回复）。 */
    private static AssistantTurn firstTurn(JSONArray tools) throws Exception {
        return firstTurn(tools, "工具");
    }

    /** 指定第一句话的脚本第 0 步。 */
    private static AssistantTurn firstTurn(JSONArray tools, String prompt) throws Exception {
        DebugScriptedProvider provider = new DebugScriptedProvider();
        return provider.createMessage(null, "system", userOnly(prompt), tools, null);
    }

    /** 收尾那一步（上下文里已经有一次助手回复）。 */
    private static AssistantTurn closingTurn(JSONArray tools) throws Exception {
        JSONArray messages = userOnly("工具");
        messages.put(new JSONObject().put("role", "assistant").put("content", "（脚本第 0 步）"));
        DebugScriptedProvider provider = new DebugScriptedProvider();
        return provider.createMessage(null, "system", messages, tools, null);
    }

    private static JSONArray userOnly(String text) throws Exception {
        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "user").put("content", text));
        return messages;
    }

    /** 取回复里的正文块拼起来。 */
    private static String textOf(AssistantTurn turn) {
        assertNotNull(turn);
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < turn.content.length(); i++) {
            JSONObject block = turn.content.optJSONObject(i);
            if (block == null) continue;
            if ("text".equals(block.optString("type"))) text.append(block.optString("text"));
        }
        return text.toString();
    }

    private static int countPipes(String row) {
        int count = 0;
        for (int i = 0; i < row.length(); i++) {
            if (row.charAt(i) == '|') count++;
        }
        return count;
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + needle.length())) {
            count++;
        }
        return count;
    }
}
