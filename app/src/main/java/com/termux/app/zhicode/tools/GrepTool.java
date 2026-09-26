package com.termux.app.zhicode.tools;

import com.termux.app.zhicode.model.SessionConfig;
import com.termux.app.zhicode.model.ToolExecutionResult;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 按正则递归搜索文件内容。
 *
 * <h3>三个上限，各挡一种失控</h3>
 * <ul>
 *   <li>{@value #MAX_RESULTS} 条命中。热门模式（比如搜一个常用词）在一个大仓库里
 *       能命中几十万行，而这些内容塞进上下文既没用又昂贵。</li>
 *   <li>{@value #MAX_FILE_BYTES} —— 单个文件的大小。跳过二进制/打包产物性质的巨大文件；
 *       对它们做逐行正则本来也不会得到有意义的结果。</li>
 *   <li>{@value #MAX_LINE_CHARS} —— 单行长度。压缩过的 JS 或单行 JSON 会命中，
 *       不截断的话「一条命中」就能吃掉整个上下文。</li>
 * </ul>
 *
 * <h3>输出格式 {@code path:line:text}</h3>
 * 这个格式是刻意的：它是几乎所有编辑器与 {@code grep -n} 的通用格式，
 * 人和模型都能直接照着去读对应位置。改成 JSON 反而让模型要多解析一层。
 *
 * <h3>读不了的文件静默跳过</h3>
 * 二进制文件用 UTF-8 读会失败，权限不足也会失败。这里一律跳过而不是报错：
 * 一次搜索里出现几个读不了的文件是常态（.git 里的对象、系统目录），
 * 为它们中断整次搜索会让工具在真实仓库里不可用。
 */
final class GrepTool implements ZhiTool {

    private static final int MAX_RESULTS = 2000;
    private static final long MAX_FILE_BYTES = 8L * 1024L * 1024L;
    private static final int MAX_LINE_CHARS = 3000;

    private static final String NO_MATCHES = "(no matches)\n";
    private static final String LIMIT_REACHED = "…result limit reached…\n";
    private static final String LINE_TRUNCATED_SUFFIX = " …[line truncated]";

    /** 递归时跳过的目录名（与 {@link GlobTool} 保持同一份判断）。 */
    private static final List<String> SKIPPED_DIRECTORIES = Arrays.asList(".git", ".gradle");

    @Override public String name() { return "Grep"; }

    @Override public String description() {
        return "Search file contents recursively with a Java regular expression. Returns file:line:text"
            + " matches. Use glob to restrict filenames.";
    }

    @Override public PermissionKind permissionKind() { return PermissionKind.READ; }

    @Override public JSONObject inputSchema() {
        try {
            JSONObject properties = new JSONObject();
            properties.put("pattern", ToolSchemas.string("Java-compatible regular expression."));
            properties.put("path", ToolSchemas.string("File or directory to search. Defaults to the active project."));
            properties.put("glob", ToolSchemas.string("Optional filename glob such as **/*.java."));
            properties.put("case_insensitive", ToolSchemas.bool("Case-insensitive matching."));
            return ToolSchemas.object(properties, "pattern");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override public ToolExecutionResult execute(SessionConfig config, JSONObject input) throws Exception {
        Pattern content;
        try {
            content = Pattern.compile(input.optString("pattern", ""), flagsFor(input));
        } catch (PatternSyntaxException invalid) {
            // 报成工具错误而不是异常：模型写错正则是很常见的一件事，
            // 它需要看到原因才能改对，而抛出异常会让它看不到自己在写什么。
            return ToolExecutionResult.error("Invalid regex: " + invalid.getMessage());
        }

        String glob = input.optString("glob", "");
        Pattern fileFilter = glob.isEmpty() ? null : Pattern.compile(GlobTool.globToRegex(glob));
        File root = PathPolicy.resolve(config.projectDirectory,
            input.optString("path", config.projectDirectory));

        StringBuilder out = new StringBuilder();
        Hits hits = new Hits();
        if (root.isFile()) {
            scan(root.getParentFile(), root, content, fileFilter, out, hits);
        } else if (root.isDirectory()) {
            descend(root, root, content, fileFilter, out, hits);
        } else {
            return ToolExecutionResult.error("Search path does not exist: " + root);
        }

        if (hits.count == 0) out.append(NO_MATCHES);
        else if (hits.count >= MAX_RESULTS) out.append(LIMIT_REACHED);
        return ToolExecutionResult.ok(out.toString());
    }

    /**
     * 正则编译标志。
     *
     * <p>不区分大小写时要一起加 {@link Pattern#UNICODE_CASE}：只有
     * {@code CASE_INSENSITIVE} 时，非 ASCII 字母的大小写规则是错的
     * （中文没有大小写，但土耳其语、希腊语、带变音符的拉丁字母都有）。
     */
    private static int flagsFor(JSONObject input) {
        return input.optBoolean("case_insensitive", false)
            ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
            : 0;
    }

    private static void descend(File root, File directory, Pattern content, Pattern fileFilter,
                                StringBuilder out, Hits hits) {
        if (hits.full()) return;
        File[] children = directory.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (hits.full()) return;
            if (child.isDirectory()) {
                if (!SKIPPED_DIRECTORIES.contains(child.getName())) {
                    descend(root, child, content, fileFilter, out, hits);
                }
            } else {
                scan(root, child, content, fileFilter, out, hits);
            }
        }
    }

    /**
     * 扫描单个文件。
     *
     * @param root 相对路径的基准。单文件搜索时传的是它的父目录，
     *             这样输出里的相对路径就是文件名本身，而不是一串 {@code ../../..}
     */
    private static void scan(File root, File file, Pattern content, Pattern fileFilter,
                             StringBuilder out, Hits hits) {
        if (hits.full() || file.length() > MAX_FILE_BYTES) return;
        String relative = root == null ? file.getName() : relativePath(root, file);
        if (fileFilter != null && !fileFilter.matcher(relative).matches()) return;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            int lineNo = 0;
            while (hits.count < MAX_RESULTS && (line = reader.readLine()) != null) {
                lineNo++;
                if (!content.matcher(line).find()) continue;
                out.append(file.getAbsolutePath()).append(':').append(lineNo).append(':')
                   .append(clip(line)).append('\n');
                hits.count++;
            }
        } catch (Exception unreadable) {
            // 见类注释：二进制或权限不足，跳过这一个文件。
        }
    }

    private static String relativePath(File root, File file) {
        return root.toURI().relativize(file.toURI()).getPath();
    }

    private static String clip(String line) {
        return line.length() <= MAX_LINE_CHARS
            ? line
            : line.substring(0, MAX_LINE_CHARS) + LINE_TRUNCATED_SUFFIX;
    }

    /** 命中计数。单个 int 的可变盒子，避免为它引入一个完整的返回对象。 */
    private static final class Hits {
        private int count;

        private boolean full() { return count >= MAX_RESULTS; }
    }
}
