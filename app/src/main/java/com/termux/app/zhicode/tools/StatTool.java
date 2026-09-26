package com.termux.app.zhicode.tools;

import com.termux.app.zhicode.model.SessionConfig;
import com.termux.app.zhicode.model.ToolExecutionResult;

import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 查看一个路径的规范化位置、类型、大小、权限、修改时间与子项数量。
 *
 * <h3>输出为什么是 {@code key: value} 的纯文本</h3>
 * 它是给模型看的，不是给程序解析的 —— 键名固定、一行一项，
 * 模型不必理解嵌套结构就能引用其中一项。不存在的路径也返回成功（而不是错误）：
 * 「它不存在」本身就是这个工具最有用的答案，报成错误会让模型以为工具坏了。
 *
 * <h3>时间格式为什么要带时区</h3>
 * 不带时区的时间在跨时区协作或设备换过时区之后会产生歧义，
 * 而模型常常要判断「这个文件是这次改的还是上次的」。
 * 用 {@link Locale#US} 固定格式，避免跟随系统语言变化。
 */
final class StatTool implements ZhiTool {

    private static final String TIME_FORMAT = "yyyy-MM-dd HH:mm:ss Z";
    private static final String UNAVAILABLE = "unavailable";

    @Override public String name() { return "Stat"; }

    @Override public String description() {
        return "Inspect a path's canonical location, type, size, permissions, modified time and child count.";
    }

    @Override public PermissionKind permissionKind() { return PermissionKind.READ; }

    @Override public JSONObject inputSchema() {
        try {
            return ToolSchemas.object(
                new JSONObject().put("path", ToolSchemas.string("File or directory path.")), "path");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override public ToolExecutionResult execute(SessionConfig config, JSONObject input) throws Exception {
        File file = PathPolicy.resolve(config.projectDirectory, input.getString("path"));
        StringBuilder out = new StringBuilder();
        out.append("path: ").append(file.getAbsolutePath()).append('\n');
        out.append("exists: ").append(file.exists()).append('\n');
        // 不存在就到此为止：后面的 isDirectory/isFile 都会是 false、
        // lastModified 会是 0，列出来只是噪音。
        if (!file.exists()) return ToolExecutionResult.ok(out.toString());

        out.append("type: ").append(kindOf(file)).append('\n');
        out.append("size: ").append(file.length()).append(" bytes\n");
        out.append("readable: ").append(file.canRead()).append('\n');
        out.append("writable: ").append(file.canWrite()).append('\n');
        out.append("executable: ").append(file.canExecute()).append('\n');
        out.append("modified: ").append(formatTime(file.lastModified())).append('\n');
        if (file.isDirectory()) {
            String[] children = file.list();
            out.append("children: ")
               .append(children == null ? UNAVAILABLE : Integer.toString(children.length))
               .append('\n');
        }
        return ToolExecutionResult.ok(out.toString());
    }

    /** 类型三分：目录、普通文件、其它（设备、FIFO、socket 都归到 other）。 */
    private static String kindOf(File file) {
        if (file.isDirectory()) return "directory";
        if (file.isFile()) return "file";
        return "other";
    }

    private static String formatTime(long millis) {
        return new SimpleDateFormat(TIME_FORMAT, Locale.US).format(new Date(millis));
    }
}
