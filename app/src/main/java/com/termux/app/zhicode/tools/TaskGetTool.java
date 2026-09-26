package com.termux.app.zhicode.tools;

import com.termux.app.zhicode.model.SessionConfig;
import com.termux.app.zhicode.model.ToolExecutionResult;
import com.termux.app.zhicode.tasks.TaskStore;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 按 id 查一个任务的全部细节。
 *
 * <h3>与 TaskList 的分工</h3>
 * {@link TaskListTool} 给的是概览（一行一个任务），这个工具给的是一条任务的细节，
 * 包括描述与被依赖关系。分开是因为列表输出要短 —— 它会被频繁调用，
 * 而细节只在模型决定动手做某一项时才需要。
 *
 * <h3>「没找到」是成功而不是错误</h3>
 * 任务可能刚被删掉、或 id 是模型记错的。返回一句 {@code Task not found} 让模型
 * 自己重新列表；报成错误会让它以为工具坏了而放弃整条路径。
 */
final class TaskGetTool implements ZhiTool {

    private static final String NOT_FOUND = "Task not found";

    @Override public String name() { return "TaskGet"; }

    @Override public String description() {
        return "Get a task by ID from the current project task list.";
    }

    @Override public PermissionKind permissionKind() { return PermissionKind.INTERNAL; }

    @Override public JSONObject inputSchema() {
        try {
            return ToolSchemas.object(new JSONObject().put("taskId", ToolSchemas.string("Task ID.")), "taskId");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override public ToolExecutionResult execute(SessionConfig config, JSONObject input) throws Exception {
        JSONObject task = new TaskStore(config.projectDirectory, config.workflowId)
            .get(input.optString("taskId", ""));
        if (task == null) return ToolExecutionResult.ok(NOT_FOUND);

        StringBuilder out = new StringBuilder();
        out.append("Task #").append(task.optString("id")).append(": ").append(task.optString("subject")).append('\n');
        out.append("Status: ").append(task.optString("status")).append('\n');
        out.append("Description: ").append(task.optString("description"));
        // 依赖关系只在非空时才加一行：空关系的两行是纯噪音，而模型会把它当成
        // 「这个任务有（空的）依赖」。
        appendReferences(out, "\nBlocked by: ", task.optJSONArray("blockedBy"));
        appendReferences(out, "\nBlocks: ", task.optJSONArray("blocks"));
        return ToolExecutionResult.ok(out.toString());
    }

    private static void appendReferences(StringBuilder out, String label, JSONArray ids) {
        if (ids == null || ids.length() == 0) return;
        out.append(label);
        for (int i = 0; i < ids.length(); i++) {
            if (i > 0) out.append(", ");
            out.append('#').append(ids.optString(i));
        }
    }
}
