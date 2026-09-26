package com.termux.app.zhicode.tools;

import com.termux.app.zhicode.model.SessionConfig;
import com.termux.app.zhicode.model.ToolExecutionResult;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 按 glob 模式递归找文件。
 *
 * <h3>模式是相对「搜索根」而不是绝对路径</h3>
 * 匹配对象是 {@code root} 到文件的相对路径，所以 {@code **}{@code /*.java} 的含义
 * 与用户直觉一致（从搜索根往下任意层）。用绝对路径去匹配的话，
 * 模式里就得到处写 {@code **}{@code /data/**}{@code /home/…}，没法用。
 *
 * <h3>结果按修改时间倒序</h3>
 * 这是最省事的相关性排序：刚改过的文件几乎总是当前任务要看的。
 * 命中数触顶时，被截掉的是「最老的」而不是随机的一批。
 *
 * <h3>跳过 {@code .git} 与 {@code .gradle}</h3>
 * 它们可能有几十万个文件，而内容永远不是答案。只跳过这两个名字（而不是所有隐藏目录）：
 * {@code .github}、{@code .termux} 里的文件是正常的搜索目标。
 */
final class GlobTool implements ZhiTool {

    private static final int MAX_RESULTS = 3000;
    private static final String DEFAULT_PATTERN = "**/*";
    private static final String NO_MATCHES = "(no matches)\n";
    private static final String LIMIT_REACHED = "…result limit reached…\n";

    /** 递归时跳过的目录名。 */
    private static final List<String> SKIPPED_DIRECTORIES = List.of(".git", ".gradle");

    @Override public String name() { return "Glob"; }

    @Override public String description() {
        return "Find files by glob pattern recursively, such as **/*.java or app/src/**/*.xml."
            + " Results are sorted by newest modification time first.";
    }

    @Override public PermissionKind permissionKind() { return PermissionKind.READ; }

    @Override public JSONObject inputSchema() {
        try {
            JSONObject properties = new JSONObject();
            properties.put("pattern", ToolSchemas.string("Glob pattern, for example **/*.java."));
            properties.put("path", ToolSchemas.string("Directory to search. Defaults to the active project."));
            return ToolSchemas.object(properties, "pattern");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override public ToolExecutionResult execute(SessionConfig config, JSONObject input) throws Exception {
        File root = PathPolicy.resolve(config.projectDirectory,
            input.optString("path", config.projectDirectory));
        if (!root.isDirectory()) return ToolExecutionResult.error("Search path is not a directory: " + root);

        Pattern pattern = Pattern.compile(globToRegex(input.optString("pattern", DEFAULT_PATTERN)));
        List<File> matches = new ArrayList<>();
        collect(root, root, pattern, matches);
        matches.sort((newer, older) -> Long.compare(older.lastModified(), newer.lastModified()));

        StringBuilder out = new StringBuilder();
        for (File match : matches) out.append(match.getAbsolutePath()).append('\n');
        if (matches.isEmpty()) out.append(NO_MATCHES);
        if (matches.size() >= MAX_RESULTS) out.append(LIMIT_REACHED);
        return ToolExecutionResult.ok(out.toString());
    }

    private static void collect(File root, File directory, Pattern pattern, List<File> matches) {
        if (matches.size() >= MAX_RESULTS) return;
        File[] children = directory.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (matches.size() >= MAX_RESULTS) return;
            if (child.isDirectory()) {
                if (SKIPPED_DIRECTORIES.contains(child.getName())) continue;
                collect(root, child, pattern, matches);
            } else if (pattern.matcher(relativePath(root, child)).matches()) {
                matches.add(child);
            }
        }
    }

    /** root 到 file 的相对路径（以 {@code /} 分隔）。 */
    private static String relativePath(File root, File file) {
        return root.toURI().relativize(file.toURI()).getPath();
    }

    /**
     * glob → 正则。
     *
     * <h3>三条规则，各自都有个容易写错的点</h3>
     * <ul>
     *   <li>{@code **} 后紧跟 {@code /} 时，编成「任意层目录、且可以一层都没有」的
     *       形式（正则写作非捕获组加可选量词）。它必须能匹配**空**，
     *       即 {@code **} 加斜杠加 {@code *.java} 要能匹配根目录下的 {@code A.java}，
     *       否则「顶层文件永远搜不到」—— 这个错误很隐蔽，因为深层文件都搜得到。</li>
     *   <li>单个 {@code *} → {@code [^/]*}，**不能**跨路径分隔符。否则
     *       {@code src} 加斜杠加 {@code *.java} 会匹配到 {@code src/sub/A.java}。</li>
     *   <li>{@code [}…{@code ]} 原样搬过去（字符类语法本来就一样），
     *       但括号不配对时退化成字面量 —— 直接抛正则语法错会让模型收到一个
     *       与它写的模式对不上的报错。</li>
     * </ul>
     *
     * <p>返回值带 {@code ^}…{@code $}，所以调用方一律用 {@code matches()} 而不是
     * {@code find()}（见 {@link #collect}）。
     */
    static String globToRegex(String glob) {
        StringBuilder regex = new StringBuilder("^");
        int i = 0;
        while (i < glob.length()) {
            char c = glob.charAt(i);
            if (c == '*') {
                boolean doubleStar = i + 1 < glob.length() && glob.charAt(i + 1) == '*';
                if (!doubleStar) {
                    regex.append("[^/]*");
                    i++;
                    continue;
                }
                boolean followedBySlash = i + 2 < glob.length() && glob.charAt(i + 2) == '/';
                regex.append(followedBySlash ? "(?:.*/)?" : ".*");
                i += followedBySlash ? 3 : 2;
            } else if (c == '?') {
                regex.append("[^/]");
                i++;
            } else if (c == '[') {
                int close = glob.indexOf(']', i + 1);
                if (close > i) {
                    regex.append(glob, i, close + 1);
                    i = close + 1;
                } else {
                    regex.append("\\[");
                    i++;
                }
            } else {
                if (REGEX_SPECIALS.indexOf(c) >= 0) regex.append('\\');
                regex.append(c);
                i++;
            }
        }
        return regex.append('$').toString();
    }

    /** 需要转义的正则元字符（不含 {@code *} 与 {@code ?}，它们已在上面单独处理）。 */
    private static final String REGEX_SPECIALS = "\\.(){}+$^|";
}
