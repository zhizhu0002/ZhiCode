package com.termux.app.zhicode.tools;

import com.termux.app.zhicode.model.SessionConfig;
import com.termux.app.zhicode.model.ToolExecutionResult;

import org.json.JSONObject;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

/**
 * 列出一个目录的**直接**内容（不递归）。
 *
 * <h3>与 Tree、Glob 的分工</h3>
 * 这三个工具都「看目录」，但答的是不同问题：
 * {@code Tree} 看整体形状（会递归、但折叠产物目录），
 * {@code Glob} 按模式找文件（会递归、给绝对路径），
 * 这个工具看「这一层现在有什么」—— 不递归是有意的：
 * 模型想知道「这个目录里有没有那种文件」时，一次不递归的列目录
 * 比一次递归好得多（快、输出短、不会有权限导致的空洞）。
 *
 * <h3>输出格式</h3>
 * {@code d}/{@code f} 前缀 + 右对齐到 10 位的字节数。人一眼能看出是目录还是文件；
 * 模型也能据此判断「这个文件大到要不要分段读」。
 * 目录的 size 在多数文件系统上是个常数，不具信息量，所以照原样显示而不是隐藏 ——
 * 隐藏它会让两个本来对齐的列错位。
 */
public final class ListTool implements ZhiTool {

    private static final String SIZE_FORMAT = "%10d ";
    private static final String DIRECTORY_SUFFIX = "/";
    private static final String EMPTY = "(empty directory)";

    @Override public String name() { return "LS"; }

    @Override public String description() {
        return "List the immediate contents of a directory with type and size information.";
    }

    @Override public PermissionKind permissionKind() { return PermissionKind.READ; }

    @Override public JSONObject inputSchema() {
        try {
            JSONObject properties = new JSONObject()
                .put("path", ToolSchemas.string("Directory path. Defaults to the active project."));
            return ToolSchemas.object(properties);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override public ToolExecutionResult execute(SessionConfig config, JSONObject input) throws Exception {
        File directory = PathPolicy.resolve(config.projectDirectory,
            input.optString("path", config.projectDirectory));
        if (!directory.isDirectory()) return ToolExecutionResult.error("Not a directory: " + directory);

        File[] children = directory.listFiles();
        // listFiles 返回 null 表示「读了但读不到」（权限、或路径在中间被删）。
        // 这与「空目录」是两件事，不能都显示成 (empty directory)。
        if (children == null) return ToolExecutionResult.error("Cannot list: " + directory);

        Arrays.sort(children, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        StringBuilder out = new StringBuilder();
        for (File child : children) {
            out.append(child.isDirectory() ? "d " : "f ")
               .append(String.format(SIZE_FORMAT, child.length()))
               .append(child.getName());
            if (child.isDirectory()) out.append(DIRECTORY_SUFFIX);
            out.append('\n');
        }
        return ToolExecutionResult.ok(out.length() == 0 ? EMPTY : out.toString());
    }
}
