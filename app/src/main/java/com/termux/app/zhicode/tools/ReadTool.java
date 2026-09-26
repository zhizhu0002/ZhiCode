package com.termux.app.zhicode.tools;

import com.termux.app.zhicode.model.SessionConfig;
import com.termux.app.zhicode.model.ToolExecutionResult;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 带行号读取文本文件的一段。
 *
 * <h3>为什么必须有上限</h3>
 * 三个上限各挡一种真实的失败：
 * <ul>
 *   <li>{@value #MAX_FILE_BYTES} —— 文件本身太大。读它会把整个内容塞进一次模型请求，
 *       既超上下文也白花钱；这种情况应当改用 Grep 缩小范围。</li>
 *   <li>{@link #DEFAULT_LIMIT} / {@link #MAX_LIMIT} —— 返回行数。默认值是个折中：
 *       常见的源码文件能一次看完，而一个几万行的日志不会把上下文占满。</li>
 *   <li>{@value #MAX_LINE_CHARS} —— 单行长度。压缩过的 JS、单行 JSON、base64
 *       都在这一档上；不截断的话「一行」就能吃掉整个上下文窗口。</li>
 * </ul>
 *
 * <h3>行号格式</h3>
 * {@code %6d} 右对齐、后跟制表符：模型据此引用行号，而等宽对齐让它在长文件里
 * 更容易对上位置。这个格式是给模型看的**约定**，改了会让它对不上。
 */
public final class ReadTool implements ZhiTool {

    private static final int DEFAULT_LIMIT = 2000;
    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 5000;
    private static final long MAX_FILE_BYTES = 20L * 1024L * 1024L;
    private static final int MAX_LINE_CHARS = 12000;

    private static final String LINE_FORMAT = "%6d\t%s\n";
    private static final String LINE_TRUNCATED_SUFFIX = " …[line truncated]";
    private static final String EMPTY_RANGE = "(no lines in requested range)\n";

    @Override public String name() { return "Read"; }

    @Override public String description() {
        return "Read a UTF-8 text file with line numbers. Use offset and limit for large files.";
    }

    @Override public PermissionKind permissionKind() { return PermissionKind.READ; }

    @Override public JSONObject inputSchema() {
        JSONObject properties = new JSONObject();
        try {
            properties.put("file_path", ToolSchemas.string("Absolute or project-relative file path."));
            properties.put("offset", ToolSchemas.integer("1-based first line to return.", 1));
            properties.put("limit", ToolSchemas.integer("Maximum number of lines to return.", 1));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return ToolSchemas.object(properties, "file_path");
    }

    @Override public ToolExecutionResult execute(SessionConfig config, JSONObject input) throws Exception {
        File file = PathPolicy.resolve(config.projectDirectory, input.optString("file_path", ""));
        String rejection = reject(file);
        if (rejection != null) return ToolExecutionResult.error(rejection);

        int firstLine = Math.max(1, input.optInt("offset", 1));
        int lineBudget = clampLimit(input.optInt("limit", DEFAULT_LIMIT));

        StringBuilder out = new StringBuilder();
        int emitted = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (lineNo < firstLine) continue;
                if (emitted >= lineBudget) break;
                out.append(String.format(LINE_FORMAT, lineNo, clip(line)));
                emitted++;
            }
        }

        if (emitted == 0) out.append(EMPTY_RANGE);
        out.append("\n[file: ").append(file.getAbsolutePath())
           .append(", returned ").append(emitted).append(" line(s)]");
        return ToolExecutionResult.ok(out.toString());
    }

    /** 返回拒绝理由；可以读时返回 {@code null}。 */
    private static String reject(File file) {
        if (PathPolicy.isDangerousPseudoFile(file)) {
            return "Refusing to read blocking/infinite device: " + file;
        }
        if (!file.isFile()) {
            return "File does not exist or is not a regular file: " + file;
        }
        if (file.length() > MAX_FILE_BYTES) {
            return "File is larger than 20 MiB; use Grep or Bash to inspect a bounded range.";
        }
        return null;
    }

    /** 把行数请求夹到允许区间；请求 0 或负数按 1 处理（而不是当成「读全部」）。 */
    private static int clampLimit(int requested) {
        return Math.max(MIN_LIMIT, Math.min(requested, MAX_LIMIT));
    }

    /** 超长行截断并留下标记，让模型知道这里还有内容。 */
    private static String clip(String line) {
        return line.length() <= MAX_LINE_CHARS
            ? line
            : line.substring(0, MAX_LINE_CHARS) + LINE_TRUNCATED_SUFFIX;
    }
}
