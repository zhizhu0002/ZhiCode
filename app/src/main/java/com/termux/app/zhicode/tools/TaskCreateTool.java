package com.termux.app.zhicode.tools;

import com.termux.app.zhicode.model.SessionConfig;
import com.termux.app.zhicode.model.ToolExecutionResult;
import com.termux.app.zhicode.tasks.TaskStore;

import org.json.JSONObject;

/**
 * 新建一个结构化任务。
 *
 * <h3>任务属于「工作流」而不是「会话」</h3>
 * {@link TaskStore} 的两个参数分别是工程目录与 {@code workflowId}。用工作流而不是
 * 会话 id 作命名空间是有意的：一次编码任务常常跨多个会话（用户关掉应用再回来），
 * 而任务列表应当继续存在。反过来，子代理有各自的会话但共享同一个工作流，
 * 所以它们看到的是同一份任务列表 —— 这正是「主循环与子代理协调进度」需要的。
 *
 * <p>{@link PermissionKind#INTERNAL}：它只写本应用自己的数据目录，
 * 不碰用户文件，所以不需要向用户确认。
 */
final class TaskCreateTool implements ZhiTool {

    @Override public String name() { return "TaskCreate"; }

    @Override public String description() {
        return "Create a structured task for the current coding project.";
    }

    @Override public PermissionKind permissionKind() { return PermissionKind.INTERNAL; }

    @Override public JSONObject inputSchema() {
        JSONObject properties = new JSONObject();
        try {
            properties.put("subject", ToolSchemas.string("Brief actionable task title."));
            properties.put("description", ToolSchemas.string("What needs to be done."));
            properties.put("activeForm", ToolSchemas.string("Optional present-continuous label while in progress."));
            properties.put("metadata", ToolSchemas.freeObject("Optional task metadata."));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return ToolSchemas.object(properties, "subject", "description");
    }

    @Override public ToolExecutionResult execute(SessionConfig config, JSONObject input) throws Exception {
        JSONObject created = store(config).create(
            input.optString("subject", ""),
            input.optString("description", ""),
            input.optString("activeForm", ""),
            input.optJSONObject("metadata"));
        // 回显 id 与标题：模型随后要靠这个 id 更新状态或建立依赖关系。
        return ToolExecutionResult.ok("Task #" + created.getString("id")
            + " created successfully: " + created.getString("subject"));
    }

    private static TaskStore store(SessionConfig config) {
        return new TaskStore(config.projectDirectory, config.workflowId);
    }
}
