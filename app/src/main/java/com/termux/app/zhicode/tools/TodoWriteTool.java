package com.termux.app.zhicode.tools;

import com.termux.app.zhicode.model.SessionConfig;
import com.termux.app.zhicode.model.ToolExecutionResult;
import com.termux.shared.termux.TermuxConstants;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 旧版待办清单。
 *
 * <h3>为什么还留着</h3>
 * 它只有一条真实职责：把模型给的清单**原样存下来**并回一句「存好了」。
 * 结构化任务（依赖、负责人、状态机）走 {@code TaskCreate}/{@code TaskUpdate}，
 * 这个工具保留是因为模型仍会习惯性地调它 —— 而让它失败会让模型浪费一轮去摸索。
 * 描述里明确写了「优先用 TaskCreate」，这是给模型的提示，也是给将来读代码的人的提示。
 *
 * <h3>存储位置</h3>
 * 应用私有数据目录下的一个文件，整份覆盖写。不做合并、不做并发保护：
 * 一次会话里只有一个 agent 循环会写它，而共用的 {@code TaskStore} 才是需要
 * 并发安全的那一份。
 */
public final class TodoWriteTool implements ZhiTool {

    private static final String FILE_NAME = "todos.json";
    private static final int INDENT = 2;

    @Override public String name() { return "TodoWrite"; }

    @Override public String description() {
        return "Update the legacy ZhiCode todo list. Prefer TaskCreate/TaskUpdate for structured tasks.";
    }

    @Override public PermissionKind permissionKind() { return PermissionKind.INTERNAL; }

    @Override public JSONObject inputSchema() {
        try {
            JSONObject itemProperties = new JSONObject()
                .put("content", ToolSchemas.string("Todo text."))
                .put("status", ToolSchemas.enumString("Todo status.", "pending", "in_progress", "completed"))
                .put("activeForm", ToolSchemas.string("Present-continuous label."));
            JSONObject item = new JSONObject()
                .put("type", "object")
                .put("properties", itemProperties)
                .put("required", new JSONArray().put("content").put("status"));
            JSONObject properties = new JSONObject()
                .put("todos", new JSONObject().put("type", "array").put("items", item));
            return ToolSchemas.object(properties, "todos");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override public ToolExecutionResult execute(SessionConfig config, JSONObject input) throws Exception {
        // 缺失或类型不对时按空清单处理并照常写入：这样「清空待办」有一个明确的做法
        // （传空数组），而写坏的类型也不会让工具报错。
        JSONArray todos = input.optJSONArray("todos");
        if (todos == null) todos = new JSONArray();

        File file = new File(TermuxConstants.dataDir(), FILE_NAME);
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        try (FileOutputStream out = new FileOutputStream(file, false)) {
            out.write(todos.toString(INDENT).getBytes(StandardCharsets.UTF_8));
        }
        return ToolExecutionResult.ok("Todo list updated (" + todos.length() + " items). Stored at "
            + file.getAbsolutePath());
    }
}
