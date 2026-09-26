package com.termux.app.zhicode.tools;

import com.termux.app.zhicode.model.SessionConfig;
import com.termux.app.zhicode.model.ToolExecutionResult;
import com.termux.app.zhicode.tasks.TaskStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 列出任务与依赖状态，一行一个。
 *
 * <h3>依赖只显示「还没被满足的」</h3>
 * 一个任务被三个任务阻塞，其中两个已经完成时，只显示剩下那一个。
 * 全部显示出来会让模型每次都要自己去比对状态 —— 而这个比对正是
 * 「任务能不能开始」的判断，把它做在工具里，模型读一行就知道能不能动手。
 *
 * <h3>{@code _internal} 元数据的任务不出现在列表里</h3>
 * 引擎自己会建一些内部任务（比如压缩上下文的占位）。它们对用户没有意义，
 * 而把内部机制混进用户的待办列表会让人以为那是他交代的事。
 */
public final class TaskListTool implements ZhiTool {

    private static final String NO_TASKS = "No tasks found";
    private static final String INTERNAL_FLAG = "_internal";
    private static final String COMPLETED = "completed";

    @Override public String name() { return "TaskList"; }

    @Override public String description() {
        return "List structured tasks and dependency status for the current coding project.";
    }

    @Override public PermissionKind permissionKind() { return PermissionKind.INTERNAL; }

    @Override public JSONObject inputSchema() { return ToolSchemas.object(new JSONObject()); }

    @Override public ToolExecutionResult execute(SessionConfig config, JSONObject input) {
        List<JSONObject> tasks = new TaskStore(config.projectDirectory, config.workflowId).list();
        if (tasks.isEmpty()) return ToolExecutionResult.ok(NO_TASKS);

        Set<String> done = completedIds(tasks);
        StringBuilder out = new StringBuilder();
        for (JSONObject task : tasks) {
            if (isInternal(task)) continue;
            out.append('#').append(task.optString("id"))
               .append(" [").append(task.optString("status")).append("] ")
               .append(task.optString("subject"));
            String owner = task.optString("owner", "");
            if (!owner.isEmpty()) out.append(" (").append(owner).append(')');
            appendOpenBlockers(out, task.optJSONArray("blockedBy"), done);
            out.append('\n');
        }
        return ToolExecutionResult.ok(out.toString());
    }

    private static Set<String> completedIds(List<JSONObject> tasks) {
        Set<String> done = new HashSet<>();
        for (JSONObject task : tasks) {
            if (COMPLETED.equals(task.optString("status"))) done.add(task.optString("id"));
        }
        return done;
    }

    private static boolean isInternal(JSONObject task) {
        JSONObject metadata = task.optJSONObject("metadata");
        return metadata != null && metadata.optBoolean(INTERNAL_FLAG, false);
    }

    /** 追加 {@code [blocked by #a, #b]}；没有未完成的阻塞项时什么都不加。 */
    private static void appendOpenBlockers(StringBuilder out, JSONArray blockedBy, Set<String> done) {
        if (blockedBy == null) return;
        StringBuilder open = new StringBuilder();
        for (int i = 0; i < blockedBy.length(); i++) {
            String id = blockedBy.optString(i, "");
            if (id.isEmpty() || done.contains(id)) continue;
            if (open.length() > 0) open.append(", ");
            open.append('#').append(id);
        }
        if (open.length() > 0) out.append(" [blocked by ").append(open).append(']');
    }
}
