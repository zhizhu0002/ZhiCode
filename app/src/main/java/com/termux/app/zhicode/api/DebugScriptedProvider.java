package com.termux.app.zhicode.api;

import com.termux.app.zhicode.model.AssistantTurn;
import com.termux.app.zhicode.model.SessionConfig;
import com.termux.app.zhicode.model.ToolCall;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * **调试用的脚本化传输**：不发任何网络请求，按脚本产出助手回复。
 *
 * <h3>它解决什么问题</h3>
 *
 * 对话流的界面问题（流式追加、思考折叠、工具行的四种状态、工具组聚合、diff 计数、
 * Markdown 渲染、权限确认、计划审批、Agent 任务卡…）原本只能靠**真跑一次任务**去凑，
 * 而那要求：真密钥、能出网、模型恰好按你的意思调工具。三个条件缺一个就复现不了。
 *
 * 有了它，只要在 debug 构建里把当前 API 配置切到「调试 · 本地模拟」，
 * 发一句话就能走完整条链路：脚本给出文本增量、思考增量、**真实的工具调用**，
 * 而工具由引擎照常执行（Read / Grep / Glob / GitStatus / TaskCreate …），
 * 于是工具行里的输出、耗时、diff 全都是真的 —— 只有"模型"是假的。
 *
 * <h3>为什么不返回假工具结果</h3>
 *
 * 本类**只产出模型的意图**，工具执行仍旧走 {@code ToolRegistry}。这样调试的是
 * 真实的执行与渲染链路；如果这里把工具结果也一并伪造，那工具行渲染出问题时
 * 就分不清是渲染的错还是伪造数据的错。
 *
 * <h3>无状态</h3>
 *
 * {@link ModelProvider} 要求实现不得把"一次请求"的状态放进字段（两次请求会互相踩）。
 * 所以脚本进度**只从 messages 推导**：第几轮 = 上下文里已经有几条助手消息。
 * 同一个上下文永远得到同一个下一轮，这也是重试语义正确的必要条件。
 *
 * <h3>安全边界</h3>
 *
 * - 只有 debug 构建会用它（见 {@link ModelProviders}）：发布包里这个协议名一律报错。
 * - 脚本里的命令行刻意只用无副作用的读取类命令（`echo` / `ls` / `pwd`），
 *   即使误授权也不会改动工程；高风险工具（Edit / Delete / Move）只用
 *   `EnterPlanMode` / 只读类替代，避免"点一下确认就把仓库改了"。
 */
public final class DebugScriptedProvider implements ModelProvider {

    /** 与设置里那份调试配置的协议名一致，改一处必须改两处（见 ApiProtocol）。 */
    public static final String WIRE_NAME = "debug-scripted";

    /** 每一步之间的间隔：够慢到能看清"流式追加"，又够快不至于让人等。 */
    private static final long STEP_DELAY_MS = 22L;

    /** 每次回调携带的字符数。 */
    private static final int CHUNK = 6;

    @Override
    public AssistantTurn createMessage(
            SessionConfig config,
            String systemPrompt,
            JSONArray messages,
            JSONArray tools,
            StreamListener listener
    ) throws Exception {
        Scenario scenario = Scenario.pick(userText(messages));
        int step = assistantTurnCount(messages);
        List<Step> plan = scenario.steps();
        Step current = step < plan.size() ? plan.get(step) : scenario.closing();

        emit(listener, current);
        return turnOf(current);
    }

    // ------------------------------------------------------------------ 产出

    /** 把一步脚本"流"出去：先思考、再正文、最后工具入参。 */
    private void emit(StreamListener listener, Step step) throws InterruptedException {
        if (listener == null) return;
        stream(listener::onThinkingDelta, step.thinking);
        stream(listener::onTextDelta, step.text);
        // 工具入参也按下发增量走一遍：真实协议就是这么给的，
        // 引擎侧那套"先收开始事件、再拼入参"的路径不能只在真模型那里才被走到。
        for (int i = 0; i < step.tools.size(); i++) {
            ToolCall call = step.tools.get(i);
            String json = call.input.toString();
            for (int at = 0; at < json.length(); at += CHUNK) {
                listener.onToolInputDelta(call.id, call.name, json.substring(at, Math.min(json.length(), at + CHUNK)));
            }
        }
        listener.onUsage(1_200L + step.text.length(), 320L + step.text.length() / 2L);
    }

    private void stream(java.util.function.Consumer<String> sink, String text) throws InterruptedException {
        if (text == null || text.isEmpty()) return;
        for (int at = 0; at < text.length(); at += CHUNK) {
            Thread.sleep(STEP_DELAY_MS);
            sink.accept(text.substring(at, Math.min(text.length(), at + CHUNK)));
        }
    }

    /**
     * 把一步脚本装成一次请求的结果。
     *
     * <p>方法内的 JSON 构造只可能因为"脚本本身写错"而失败（键名不合法之类），
     * 那不是运行期用户能遇到的错误，所以直接包成
     * {@link IllegalStateException}：真发生了要改的是这里的脚本，
     * 而不是让上层去处理一个它无从下手的受检异常。
     */
    private static AssistantTurn turnOf(Step step) {
        try {
            return turnOfChecked(step);
        } catch (JSONException failure) {
            throw new IllegalStateException("脚本化传输的结果构造失败（脚本定义有误）", failure);
        }
    }

    private static AssistantTurn turnOfChecked(Step step) throws JSONException {
        AssistantTurn turn = new AssistantTurn();
        if (!step.thinking.isEmpty()) {
            turn.content.put(new JSONObject().put("type", "thinking").put("thinking", step.thinking));
        }
        if (!step.text.isEmpty()) {
            turn.content.put(new JSONObject().put("type", "text").put("text", step.text));
        }
        for (ToolCall call : step.tools) {
            turn.content.put(new JSONObject()
                    .put("type", "tool_use")
                    .put("id", call.id)
                    .put("name", call.name)
                    .put("input", call.input));
            turn.toolCalls.add(call);
        }
        // 有工具就叫工具，没有就收尾：这与真实协议的约定一致，引擎据此决定是否继续循环。
        turn.stopReason = step.tools.isEmpty() ? "end_turn" : "tool_use";
        turn.inputTokens = 1_200L + step.text.length();
        turn.outputTokens = 320L + step.text.length() / 2L;
        return turn;
    }

    // ------------------------------------------------------------------ 上下文解析

    /** 上下文里第一条用户消息的正文。 */
    private static String userText(JSONArray messages) {
        if (messages == null) return "";
        for (int i = 0; i < messages.length(); i++) {
            JSONObject message = messages.optJSONObject(i);
            if (message == null || !"user".equals(message.optString("role"))) continue;
            String text = textOf(message.opt("content"));
            if (!text.isEmpty()) return text;
        }
        return "";
    }

    /** 已经发生过几轮助手回复 —— 也就是脚本走到第几步。 */
    private static int assistantTurnCount(JSONArray messages) {
        if (messages == null) return 0;
        int count = 0;
        for (int i = 0; i < messages.length(); i++) {
            JSONObject message = messages.optJSONObject(i);
            if (message != null && "assistant".equals(message.optString("role"))) count++;
        }
        return count;
    }

    /** content 既可能是字符串，也可能是内容块数组。 */
    private static String textOf(Object content) {
        if (content instanceof String) return (String) content;
        if (!(content instanceof JSONArray)) return "";
        StringBuilder text = new StringBuilder();
        JSONArray blocks = (JSONArray) content;
        for (int i = 0; i < blocks.length(); i++) {
            JSONObject block = blocks.optJSONObject(i);
            if (block == null) continue;
            Object value = block.opt("text");
            if (value instanceof String) text.append((String) value);
        }
        return text.toString();
    }

    // ------------------------------------------------------------------ 脚本

    /** 一步：一段思考、一段正文、若干个工具调用。 */
    private static final class Step {
        final String thinking;
        final String text;
        final List<ToolCall> tools;

        Step(String thinking, String text, List<ToolCall> tools) {
            this.thinking = thinking == null ? "" : thinking;
            this.text = text == null ? "" : text;
            this.tools = tools == null ? new ArrayList<>() : tools;
        }

        static Step say(String thinking, String text) {
            return new Step(thinking, text, null);
        }
    }

    /**
     * 场景。按用户第一句话里的关键词挑一个，默认走"全链路"。
     *
     * 关键词刻意用中文与英文各一份：调试时手边是哪台输入法就用哪个。
     */
    enum Scenario {

        /** 默认：搜索 → 读取 → 列目录 → Git 状态 → 汇总（把工具行四种状态都走一遍）。 */
        FULL_CHAIN("全链路", new String[]{"", "工具", "tool", "全链路", "默认"}),

        /** 只读一个文件，用来快速验证单行工具卡。 */
        SINGLE_TOOL("单个工具", new String[]{"单个", "single", "read"}),

        /** 跑一条命令：在 ASK 权限模式下会弹出**权限确认**浮层。 */
        PERMISSION("权限确认", new String[]{"权限", "permission", "确认"}),

        /** 先规划再执行：会走 **EnterPlanMode / ExitPlanMode** 的计划审批链路。 */
        PLAN("计划审批", new String[]{"计划", "plan", "审批"}),

        /** 建任务清单：验证 **Agent 任务卡**（悬浮进度卡 + 任务清单窗口）。 */
        TASKS("任务清单", new String[]{"任务", "task", "清单"}),

        /** 失败链路：不存在的文件 + 非法命令，验证红色错误行与退出码。 */
        FAILURE("失败", new String[]{"失败", "错误", "fail", "error"}),

        /** 长文与 Markdown：验证折行、代码块、表格、列表、引用。 */
        MARKDOWN("长文 Markdown", new String[]{"长文", "markdown", "md", "文档"});

        private final String label;
        private final String[] keywords;

        Scenario(String label, String[] keywords) {
            this.label = label;
            this.keywords = keywords;
        }

        String label() {
            return label;
        }

        /** 按用户输入挑场景。 */
        static Scenario pick(String userText) {
            String lower = userText == null ? "" : userText.toLowerCase(Locale.US);
            for (Scenario scenario : values()) {
                if (scenario == FULL_CHAIN) continue;
                for (String keyword : scenario.keywords) {
                    if (!keyword.isEmpty() && lower.contains(keyword)) return scenario;
                }
            }
            return FULL_CHAIN;
        }

        /** 用户第一句话里是否点名了场景（用在脚本正文里如实说明"为什么是这个场景"）。 */
        boolean matches(String userText) {
            String lower = userText == null ? "" : userText.toLowerCase(Locale.US);
            for (String keyword : keywords) {
                if (!keyword.isEmpty() && lower.contains(keyword)) return true;
            }
            return this == FULL_CHAIN;
        }

        List<Step> steps() {
            try {
                return stepsChecked();
            } catch (JSONException failure) {
                throw new IllegalStateException("脚本化传输的场景定义有误（JSON 构造失败）", failure);
            }
        }

        private List<Step> stepsChecked() throws JSONException {
            switch (this) {
                case SINGLE_TOOL:
                    return List.of(
                            new Step("只读一个文件就够看得出工具卡的排版。", "我先读一下构建脚本。",
                                    List.of(tool("read-1", "Read", new JSONObject().put("file_path", "settings.gradle")))),
                            finalStep("读完了。")
                    );
                case PERMISSION:
                    return List.of(
                            new Step("要执行命令，会先向用户请求授权。", "下面这条命令需要你确认。",
                                    List.of(tool("perm-1", "Bash", new JSONObject()
                                            .put("command", "echo debug-scripted-provider")
                                            .put("description", "调试用只读命令")))),
                            finalStep("命令已执行。")
                    );
                case PLAN:
                    return List.of(
                            new Step("先进入计划模式，把方案写出来再等审批。", "我先进入计划模式。",
                                    List.of(tool("plan-1", "EnterPlanMode", new JSONObject()))),
                            finalStep("计划模式已进入。")
                    );
                case TASKS:
                    return List.of(
                            new Step("把步骤列成任务清单。", "我把接下来的步骤列成清单。",
                                    List.of(
                                            tool("task-1", "TaskCreate", new JSONObject()
                                                    .put("subject", "读取工程结构")
                                                    .put("description", "扫描目录并统计模块")),
                                            tool("task-2", "TaskCreate", new JSONObject()
                                                    .put("subject", "替换动效令牌")
                                                    .put("description", "把调用点收口到 Animations.kt"))
                                    )),
                            new Step("", "第一条已经完成，继续第二条。",
                                    List.of(tool("task-3", "TaskList", new JSONObject()))),
                            finalStep("清单列完了。")
                    );
                case FAILURE:
                    return List.of(
                            new Step("故意读一个不存在的路径，看失败行怎么画。", "我读一个不存在的文件。",
                                    List.of(tool("fail-1", "Read", new JSONObject()
                                            .put("file_path", "does-not-exist/debug-probe.txt")))),
                            new Step("再跑一条注定失败的命令，看退出码那一行。", "再来一条会失败的命令。",
                                    List.of(tool("fail-2", "Bash", new JSONObject()
                                            .put("command", "exit 3")
                                            .put("description", "调试用失败命令")))),
                            finalStep("失败链路走完了。")
                    );
                case MARKDOWN:
                    return List.of(finalStep(""));
                case FULL_CHAIN:
                default:
                    return List.of(
                            new Step("先看看工程里跟动效有关的符号都在哪。", "我先搜一下动效相关的位置。",
                                    List.of(
                                            tool("chain-1", "Grep", new JSONObject()
                                                    .put("pattern", "ZhiMotion")
                                                    .put("glob", "**/*.kt")),
                                            tool("chain-2", "Glob", new JSONObject()
                                                    .put("pattern", "**/ui/**/*.kt"))
                                    )),
                            new Step("把关键文件读出来，确认当前用的是哪几条曲线。", "再读一下动效令牌的定义。",
                                    List.of(tool("chain-3", "Read", new JSONObject()
                                            .put("file_path", "app/src/main/java/com/zhizhu/zhicode/compose/ui/Animations.kt")))),
                            new Step("顺手看一眼仓库状态，确认改动范围。", "顺便看一下工作区改动。",
                                    List.of(tool("chain-4", "GitStatus", new JSONObject()))),
                            finalStep("")
                    );
            }
        }

        /** 收尾一步：正文按场景给一段像样的回答（也是 Markdown 渲染器的实测素材）。 */
        Step closing() {
            if (this == MARKDOWN) {
                return Step.say("", LONG_MARKDOWN);
            }
            return finalStep("");
        }

        private Step finalStep(String lead) {
            StringBuilder text = new StringBuilder();
            if (lead != null && !lead.isEmpty()) text.append(lead).append("\n\n");
            text.append("已按脚本走完「").append(label()).append("」场景。\n\n")
                    .append("这一步走到的链路：**流式正文** → 工具调用（由引擎真实执行）→ 工具结果回收 → 收尾。\n\n")
                    .append("想换场景，直接在下一条消息里带上关键词：\n\n")
                    .append("- `工具`：搜索 / 读取 / 列目录 / Git 状态（默认）\n")
                    .append("- `单个`：只读一个文件\n")
                    .append("- `权限`：触发**权限确认**浮层\n")
                    .append("- `计划`：触发**计划模式 / 审批**\n")
                    .append("- `任务`：触发 **Agent 任务卡**与任务清单\n")
                    .append("- `失败`：触发失败工具行（含退出码与错误输出）\n")
                    .append("- `长文`：触发长 Markdown（标题/列表/表格/代码块/引用）\n");
            return Step.say("脚本已收尾。", text.toString());
        }
    }

    private static ToolCall tool(String id, String name, JSONObject input) {
        return new ToolCall(id, name, input);
    }

    /** 长 Markdown 素材：把渲染器支持的块级语法都覆盖一遍。 */
    private static final String LONG_MARKDOWN = String.join("\n",
            "## 调试用长文",
            "",
            "这一段是**脚本化传输**产出的，用来实测 Markdown 渲染：折行、行高、代码块宽度、表格溢出。",
            "",
            "### 块级语法",
            "",
            "1. 有序列表第一项",
            "2. 有序列表第二项",
            "   - 嵌套的无序项",
            "   - 另一项",
            "",
            "- [x] 已完成的待办",
            "- [ ] 未完成的待办",
            "",
            "> 引用块里也能放代码：",
            "> ```kotlin",
            "> val fadeOutSpec = tween(150, easing = SinOutEasing)",
            "> ```",
            "",
            "| 令牌 | 时长 | 曲线 |",
            "| --- | --- | --- |",
            "| fadeInSpec | 300ms | SinOutEasing |",
            "| fadeOutSpec | 150ms | SinOutEasing |",
            "| exitSpec | 200ms | DecelerateEasing(1.5f) |",
            "",
            "行内：`ZhiMarkdown`、**粗体**、*斜体*、~~删除线~~、[链接](https://example.com)。",
            "",
            "```kotlin",
            "object ZhiMotion {",
            "    val fadeInSpec = tween(300, easing = SinOutEasing)",
            "    val fadeOutSpec = tween(150, easing = SinOutEasing)",
            "}",
            "```",
            "",
            "---",
            "",
            "结尾一段：如果上面的表格没有横向溢出、代码块没有把卡片撑破、长行能正常折行，"
                    + "那这一屏的 Markdown 渲染就是好的。");
}
