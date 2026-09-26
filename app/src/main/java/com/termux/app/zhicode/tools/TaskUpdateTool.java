package com.termux.app.zhicode.tools;

import com.termux.app.zhicode.model.SessionConfig;
import com.termux.app.zhicode.model.ToolExecutionResult;
import com.termux.app.zhicode.tasks.TaskStore;

import org.json.JSONObject;

/**
 * 改任务的状态、内容或依赖关系，也可以删除。
 *
 * <h3>整个入参对象直接转交给存储层</h3>
 * 与其它工具不同，这里**不**逐字段读取，而是把 {@link JSONObject} 原样传给
 * {@link TaskStore#update}。原因：这个工具要支持「只改其中一个字段」，
 * 而「没传的字段」与「传了空值」必须区分开 —— 逐字段读取时两者都会变成
 * 默认值，于是「只改状态」会顺手把描述清空。
 * schema 把可改字段全部声明出来，是为了让模型知道能改什么。
 *
 * <h3>删除复用同一个入口</h3>
 * {@code status=deleted} 走的也是 update，返回值里带一个 {@code deleted} 标志。
 * 不另开一个删任务工具，是因为「任务被删掉」与「任务状态变成 deleted」
 * 对调用方是同一件事，分开会让模型在两个工具之间选错。
 */
public final class TaskUpdateTool implements ZhiTool {

    @Override public String name() { return "TaskUpdate"; }

    @Override public String description() {
        return "Update a task's status/details/dependencies or delete it.";
    }

    @Override public PermissionKind permissionKind() { return PermissionKind.INTERNAL; }

    @Override public JSONObject inputSchema() {
        JSONObject properties = new JSONObject();
        try {
            properties.put("taskId", ToolSchemas.string("Task ID."));
            properties.put("subject", ToolSchemas.string("New task subject."));
            properties.put("description", ToolSchemas.string("New task description."));
            properties.put("activeForm", ToolSchemas.string("New active form."));
            properties.put("status", ToolSchemas.enumString("New status.",
                "pending", "in_progress", "completed", "deleted"));
            properties.put("owner", ToolSchemas.string("Optional owner."));
            properties.put("addBlocks", ToolSchemas.stringArray("Task IDs that this task blocks."));
            properties.put("addBlockedBy", ToolSchemas.stringArray("Task IDs that block this task."));
            properties.put("metadata", ToolSchemas.freeObject("Metadata keys to merge; null removes a key."));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return ToolSchemas.object(properties, "taskId");
    }

    @Override public ToolExecutionResult execute(SessionConfig config, JSONObject input) throws Exception {
        String id = input.optString("taskId", "");
        JSONObject updated = new TaskStore(config.projectDirectory, config.workflowId).update(id, input);
        // null 表示这个 id 不存在；存储层用 null 而不是空对象来表达，
        // 因为「存在但内容为空」在任务里是可能的。
        if (updated == null) return ToolExecutionResult.error("Task not found: #" + id);
        if (updated.optBoolean("deleted", false)) {
            return ToolExecutionResult.ok("Task #" + id + " deleted");
        }
        return ToolExecutionResult.ok("Task #" + id + " updated: ["
            + updated.optString("status") + "] " + updated.optString("subject"));
    }
}
