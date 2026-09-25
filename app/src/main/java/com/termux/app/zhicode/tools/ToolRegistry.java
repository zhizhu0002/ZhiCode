package com.termux.app.zhicode.tools;

import android.content.Context;

import com.termux.app.zhicode.model.SessionConfig;
import com.termux.app.zhicode.model.ToolExecutionResult;
import com.termux.app.zhicode.mcp.McpRuntime;
import com.termux.app.zhicode.termux.TermuxShellExecutor;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ToolRegistry {
    private final Map<String, ZhiTool> tools = new LinkedHashMap<>();

    public ToolRegistry(Context context) {
        TermuxShellExecutor shell = new TermuxShellExecutor(context);
        AndroidIntentBridge androidIntent = new AndroidIntentBridge(context);
        register(new BashTool(shell, androidIntent));
        register(new RootBashTool(shell));
        register(new AndroidIntentTool(androidIntent));
        register(new ZhiSandboxTool(context));
        register(new ZhiDebugTool(context, shell));
        register(new UiCanvasTool(context));
        register(new GitStatusTool(shell));
        register(new WebSearchTool());
        register(new WebFetchTool());
        register(new TermuxDoctorTool(shell));
        register(new TermuxRepairTool(shell));
        register(new EnterWorktreeTool(shell));
        register(new ExitWorktreeTool(shell));
        register(new ReadTool());
        register(new ReadManyTool());
        register(new StatTool());
        register(new TreeTool());
        register(new WriteTool());
        register(new CopyTool());
        register(new EditTool());
        register(new MultiEditTool());
        register(new MkdirTool());
        register(new MoveTool());
        register(new DeleteTool());
        register(new GlobTool());
        register(new GrepTool());
        register(new ListTool());
        register(new TaskCreateTool());
        register(new TaskGetTool());
        register(new TaskListTool());
        register(new TaskUpdateTool());
        register(new SkillTool());
        register(new SleepTool());
        register(new EnterPlanModeTool());
        register(new ExitPlanModeTool());
        McpRuntime mcp=new McpRuntime();
        register(new McpTool(mcp,true));
        register(new McpTool(mcp,false));
        register(new TodoWriteTool());
    }

    public void register(ZhiTool tool) { tools.put(tool.name(), tool); }
    public ZhiTool get(String name) { return tools.get(name); }

    public JSONArray apiSchemas() {
        JSONArray array = new JSONArray();
        for (ZhiTool tool : tools.values()) {
            JSONObject schema = new JSONObject();
            try {
                schema.put("name", tool.name());
                schema.put("description", tool.description());
                schema.put("input_schema", tool.inputSchema());
                array.put(schema);
            } catch (Exception e) { throw new IllegalStateException(e); }
        }
        return array;
    }

    public ToolExecutionResult execute(SessionConfig config, String name, JSONObject input) throws Exception {
        return execute(config, name, input, null);
    }

    public ToolExecutionResult execute(SessionConfig config, String name, JSONObject input, ZhiTool.ProgressListener progress) throws Exception {
        ZhiTool tool = tools.get(name);
        if (tool == null) return ToolExecutionResult.error("Unknown tool: " + name);
        return tool.execute(config, input, progress);
    }
}
