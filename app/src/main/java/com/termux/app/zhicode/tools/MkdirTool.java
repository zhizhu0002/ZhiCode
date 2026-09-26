package com.termux.app.zhicode.tools;

import com.termux.app.zhicode.model.SessionConfig;
import com.termux.app.zhicode.model.ToolExecutionResult;

import org.json.JSONObject;

import java.io.File;

/**
 * 建目录。
 *
 * <h3>「已经存在」算成功</h3>
 * 与 {@code mkdir}（不带 {@code -p}）不同：调用方的意图是「确保这个目录存在」，
 * 而它已经存在时意图已经满足了。报错误会让模型在重试与放弃之间浪费几轮。
 */
final class MkdirTool implements ZhiTool {

    @Override public String name() { return "Mkdir"; }

    @Override public String description() {
        return "Create a directory, including missing parent directories.";
    }

    @Override public PermissionKind permissionKind() { return PermissionKind.WRITE; }

    @Override public JSONObject inputSchema() {
        try {
            return ToolSchemas.object(
                new JSONObject().put("path", ToolSchemas.string("Directory path.")), "path");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override public ToolExecutionResult execute(SessionConfig config, JSONObject input) throws Exception {
        File directory = PathPolicy.resolve(config.projectDirectory, input.getString("path"));
        if (directory.isDirectory()) {
            return ToolExecutionResult.ok("Directory already exists: " + directory);
        }
        if (!directory.mkdirs()) {
            return ToolExecutionResult.error("Could not create directory: " + directory);
        }
        return ToolExecutionResult.ok("Created " + directory);
    }
}
