package com.termux.app.zhicode.tools;

import com.termux.app.zhicode.model.SessionConfig;
import com.termux.app.zhicode.model.ToolExecutionResult;

import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * 整份写入或覆盖一个文本文件。
 *
 * <h3>为什么要先读一遍旧内容</h3>
 * 只为生成 diff。但是**不能省**：Android 的变更列表靠它显示「这次改了什么」，
 * 而用户批准一次大改动之前最需要看到的正是这个。
 *
 * <h3>两个 20 MiB 上限的分工</h3>
 * 「写之前」检查的是**已有文件**：把一个巨大的文件用一小段文本覆盖掉，
 * 会让用户丢掉他原本并不打算丢的内容，所以要挡。写之前又按**字节数**检查一次
 * 是因为字符数与字节数不等（中文一个字三字节），按字符判会漏。
 */
final class WriteTool implements ZhiTool {

    private static final long MAX_BYTES = 20L * 1024L * 1024L;

    @Override public String name() { return "Write"; }

    @Override public String description() {
        return "Create or overwrite a UTF-8 text file. Parent directories are created automatically.";
    }

    @Override public PermissionKind permissionKind() { return PermissionKind.WRITE; }

    @Override public JSONObject inputSchema() {
        JSONObject properties = new JSONObject();
        try {
            properties.put("file_path", ToolSchemas.string("Absolute or project-relative destination path."));
            properties.put("content", ToolSchemas.string("Complete UTF-8 file contents."));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return ToolSchemas.object(properties, "file_path", "content");
    }

    @Override public ToolExecutionResult execute(SessionConfig config, JSONObject input) throws Exception {
        String requested = input.optString("file_path", "");
        File file = PathPolicy.resolve(config.projectDirectory, requested);
        boolean existed = file.isFile();
        if (existed && file.length() > MAX_BYTES) {
            return ToolExecutionResult.error("Refusing to overwrite a file larger than 20 MiB: " + file);
        }

        String previous = existed ? TextFiles.readText(file) : "";
        String content = input.optString("content", "");
        byte[] payload = content.getBytes(StandardCharsets.UTF_8);
        if (payload.length > MAX_BYTES) {
            return ToolExecutionResult.error("Refusing to write more than 20 MiB.");
        }

        String missingParent = ensureParent(file);
        if (missingParent != null) return ToolExecutionResult.error(missingParent);

        UnifiedDiff.Result diff = UnifiedDiff.create(input.optString("file_path", file.getPath()),
            previous, content, existed);
        TextFiles.writeText(file, content);
        return ToolExecutionResult.okWithDiff(
            "Wrote " + payload.length + " bytes to " + file.getAbsolutePath(),
            diff.text, diff.additions, diff.deletions);
    }

    /**
     * 建好父目录；失败时返回错误文案。
     *
     * <p>先算 diff 再建目录是**不能**反过来的：建目录成功而写入失败会留下空目录，
     * 而这里顺序无关紧要 —— diff 只依赖内存里的字符串。
     */
    private static String ensureParent(File file) {
        File parent = file.getParentFile();
        if (parent == null || parent.isDirectory() || parent.mkdirs()) return null;
        return "Could not create parent directory: " + parent;
    }
}
