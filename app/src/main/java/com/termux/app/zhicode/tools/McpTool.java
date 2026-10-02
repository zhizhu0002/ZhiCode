package com.termux.app.zhicode.tools;

import com.termux.app.zhicode.mcp.McpRuntime;
import com.termux.app.zhicode.model.SessionConfig;
import com.termux.app.zhicode.model.ToolExecutionResult;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * MCP 的两个入口：{@code mcp_list} 与 {@code mcp_call}。
 *
 * <h3>为什么是一个类两个实例</h3>
 * 两者共用同一个 {@link McpRuntime}（连接池、进程、握手状态都在它里面），
 * 唯一的差别是 {@link #list} 标志。写成两个类就得把运行时对象传两份、
 * 或者让它们各自持有一份 —— 后者会让 MCP 服务被启动两次。
 *
 * <h3>为什么不做成一个「自动判断」的工具</h3>
 * 模型需要先拿到服务器与工具名，才能发第二次调用。合成一个工具的话，
 * 它常常会跳过第一步、直接编一个工具名出来，而失败信息只会是「未知工具」。
 * 两个名字让「先查后调」变成提示词里能看到的两步。
 *
 * <h3>schema 为什么不用 {@link ToolSchemas}</h3>
 * MCP 的 {@code arguments} 是完全自由的（由远端服务器定义），
 * 而 {@link ToolSchemas#object} 会加上 {@code additionalProperties: false} ——
 * 那正好会把自由参数挡掉。所以这里直接手写。
 */
public final class McpTool implements ZhiTool {

    private static final String LIST = "mcp_list";
    private static final String CALL = "mcp_call";

    private final McpRuntime runtime;
    private final boolean list;

    public McpTool(McpRuntime runtime, boolean list) {
        this.runtime = runtime;
        this.list = list;
    }

    @Override public String name() { return list ? LIST : CALL; }

    @Override public String description() {
        return list
            ? "List enabled MCP servers and discover their available tools before calling them."
            : "Call a tool exposed by a configured MCP server. First use mcp_list to get the exact server and tool name.";
    }

    @Override public JSONObject inputSchema() {
        try {
            if (list) return new JSONObject().put("type", "object").put("properties", new JSONObject());
            JSONObject properties = new JSONObject()
                .put("server", new JSONObject().put("type", "string"))
                .put("tool", new JSONObject().put("type", "string"))
                // 远端服务器自己定义参数，所以这里不声明具体字段、也不关闭额外属性。
                .put("arguments", new JSONObject().put("type", "object"));
            return new JSONObject()
                .put("type", "object")
                .put("properties", properties)
                .put("required", new JSONArray().put("server").put("tool"));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** MCP 调用会出网，所以按网络权限对待，而不是内部工具。 */
    @Override public PermissionKind permissionKind() { return PermissionKind.NETWORK; }

    /**
     * 逐工具审批：用户可以为某一个远端工具单独要求确认。
     *
     * <p>{@code mcp_list} 不涉及这一步（它只是列清单）。
     *
     * <p>参数缺失时返回 false（不拦）：{@code input} 是模型给的，可能什么都没有。
     * 那种情况下这次调用本来就会被 {@code runtime.call} 拒绝（服务器或工具名为空），
     * 额外的确认框只会让用户对着一个必然失败的调用点一次"允许"。
     */
    @Override public boolean requiresApproval(JSONObject input) {
        if (list) return false;
        return runtime.requiresApproval(input.optString("server", ""), input.optString("tool", ""));
    }

    @Override public ToolExecutionResult execute(SessionConfig config, JSONObject input) {
        if (list) return ToolExecutionResult.ok(runtime.listServers().toString());
        return runtime.call(input.optString("server", ""), input.optString("tool", ""),
            input.optJSONObject("arguments"));
    }
}
