package com.termux.app.zhicode.tools;

import com.termux.app.zhicode.model.SessionConfig;
import com.termux.app.zhicode.model.ToolExecutionResult;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 一次调用读多个文件。
 *
 * <h3>它解决的问题是往返次数</h3>
 * 理解一段跨文件的逻辑（一个接口加它的三处实现）需要读四五个文件。
 * 逐个读就是四五次模型往返，每次都要重新发送全部上下文 ——
 * 这个工具把那些往返压成一次，是省 token 与省时间最直接的手段。
 *
 * <h3>三层上限</h3>
 * <ol>
 *   <li>{@value #MAX_FILES} 个文件。再多就说明调用方该改用 Grep 缩小范围；
 *       超出部分**不静默丢弃**，末尾会写明跳过了几个。</li>
 *   <li>每个文件的默认 {@value #DEFAULT_LIMIT_PER_FILE} 行、上限
 *       {@value #MAX_LIMIT_PER_FILE} 行 —— 让每个文件都露个头，
 *       而不是第一个文件就吃掉全部预算。</li>
 *   <li>输出总长 {@value #MAX_OUTPUT_CHARS} 字符，触顶即停。这是最后一道闸：
 *       上面两个上限都是**每份**的，乘起来仍然可以很大。</li>
 * </ol>
 *
 * <h3>为什么直接复用 {@link ReadTool} 而不是另写一份</h3>
 * 单文件读取的所有规则（超长行截断、危险设备文件、行号格式）都应当一致；
 * 两处实现会在某次修改后悄悄分叉，然后模型看到两种不同的行号格式。
 */
final class ReadManyTool implements ZhiTool {

    private static final int MAX_FILES = 24;
    private static final int DEFAULT_LIMIT_PER_FILE = 500;
    private static final int MAX_LIMIT_PER_FILE = 2000;
    private static final int MAX_OUTPUT_CHARS = 1_800_000;

    private static final String HEADER_FORMAT = "===== %s =====\n";
    private static final String TRUNCATED = "…multi-file output truncated…\n";

    @Override public String name() { return "ReadMany"; }

    @Override public String description() {
        return "Read several UTF-8 source files in one call. Each file is line-numbered and bounded;"
            + " useful for understanding related code before editing.";
    }

    @Override public PermissionKind permissionKind() { return PermissionKind.READ; }

    @Override public JSONObject inputSchema() {
        try {
            JSONObject properties = new JSONObject();
            properties.put("paths", ToolSchemas.stringArray("File paths to read; maximum 24."));
            properties.put("limit_per_file", ToolSchemas.integer("Maximum lines per file.", 1));
            return ToolSchemas.object(properties, "paths");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override public ToolExecutionResult execute(SessionConfig config, JSONObject input) throws Exception {
        JSONArray paths = input.getJSONArray("paths");
        if (paths.length() == 0) return ToolExecutionResult.error("paths is empty");

        int requested = Math.min(paths.length(), MAX_FILES);
        int lineBudget = Math.max(1, Math.min(input.optInt("limit_per_file", DEFAULT_LIMIT_PER_FILE),
            MAX_LIMIT_PER_FILE));

        ReadTool reader = new ReadTool();
        StringBuilder out = new StringBuilder();
        int read = 0;
        for (; read < requested; read++) {
            out.append(String.format(HEADER_FORMAT, paths.getString(read)));
            JSONObject request = new JSONObject()
                .put("file_path", paths.getString(read)).put("offset", 1).put("limit", lineBudget);
            // 单个文件的失败（不存在、太大、是设备文件）当成这个文件的正文写进去：
            // 它只影响这一份，不该让整批读取失败。
            out.append(reader.execute(config, request).content).append("\n\n");
            if (out.length() > MAX_OUTPUT_CHARS) {
                out.append(TRUNCATED);
                break;
            }
        }
        if (paths.length() > read) {
            out.append("[skipped ").append(paths.length() - read).append(" file(s); max 24]\n");
        }
        return ToolExecutionResult.ok(out.toString());
    }
}
