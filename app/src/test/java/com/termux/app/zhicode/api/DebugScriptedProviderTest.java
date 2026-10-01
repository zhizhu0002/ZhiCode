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
        DebugScriptedProvider provider = new DebugScriptedProvider();
        return provider.createMessage(null, "system", userOnly("工具"), tools, null);
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
