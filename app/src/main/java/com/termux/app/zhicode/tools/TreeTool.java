package com.termux.app.zhicode.tools;

import com.termux.app.zhicode.model.SessionConfig;
import com.termux.app.zhicode.model.ToolExecutionResult;

import org.json.JSONObject;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;

/**
 * 有界的递归目录树。
 *
 * <h3>两层「有界」</h3>
 * <ol>
 *   <li><b>深度</b>默认 4、上限 8。默认值是个折中：大多数工程的源码到第四层就够了，
 *       再深一层往往就进了构建产物。</li>
 *   <li><b>条目总数 {@value #MAX_ENTRIES}</b>。这是为了兜住「深度不大但每层极宽」
 *       的情况（一个目录里几万个文件）。触顶后停止并说明已截断 ——
 *       静默截断会让模型以为树只有这么长。</li>
 * </ol>
 *
 * <h3>为什么折叠构建产物目录</h3>
 * {@link #BULKY_DIRECTORIES} 里的名字一旦出现，只打印目录名并标注 {@code […]}，
 * 不再往下走。它们的内容对「理解这个工程」没有信息量，却能吃掉整个预算。
 * 只在 {@code level > 0} 时折叠：工程根恰好叫 {@code build} 之类时，
 * 直接显示「什么都没有」比列出真实内容更糟。
 *
 * <h3>排序</h3>
 * 不区分大小写按名字排。用系统默认排序会让同一棵树在两次调用里顺序不同
 * （不区分大小写、或按 inode 顺序），而模型会把「顺序变了」当成「结构变了」。
 */
public final class TreeTool implements ZhiTool {

    private static final int DEFAULT_DEPTH = 4;
    private static final int MAX_DEPTH = 8;
    private static final int MAX_ENTRIES = 5000;

    private static final String BRANCH = "├─ ";
    private static final String INDENT_STEP = "│  ";
    private static final String COLLAPSED_MARK = "/  […]\n";
    private static final String TRUNCATED = "…tree truncated at 5000 entries…\n";
    private static final String DIRECTORY_SUFFIX = "/";

    private static final Set<String> BULKY_DIRECTORIES = Set.of("node_modules", ".gradle", "build", "dist");

    @Override public String name() { return "Tree"; }

    @Override public String description() {
        return "Show a bounded recursive project directory tree. Hidden files may be included and common"
            + " build/cache directories can be skipped.";
    }

    @Override public PermissionKind permissionKind() { return PermissionKind.READ; }

    @Override public JSONObject inputSchema() {
        try {
            JSONObject properties = new JSONObject();
            properties.put("path", ToolSchemas.string("Directory path."));
            properties.put("depth", ToolSchemas.integer("Maximum recursion depth (1-8).", 1));
            properties.put("include_hidden", ToolSchemas.bool("Include dotfiles/directories."));
            return ToolSchemas.object(properties);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override public ToolExecutionResult execute(SessionConfig config, JSONObject input) throws Exception {
        File root = PathPolicy.resolve(config.projectDirectory,
            input.optString("path", config.projectDirectory));
        if (!root.isDirectory()) return ToolExecutionResult.error("Not a directory: " + root);

        int depth = Math.max(1, Math.min(input.optInt("depth", DEFAULT_DEPTH), MAX_DEPTH));
        // 默认包含隐藏文件：.github、.gitignore 这些是理解工程的一部分，
        // 而默认跳过会让模型对工程结构形成错误印象。
        boolean includeHidden = input.optBoolean("include_hidden", true);

        StringBuilder out = new StringBuilder();
        out.append(root.getAbsolutePath()).append(DIRECTORY_SUFFIX).append('\n');
        Cursor cursor = new Cursor(includeHidden);
        walk(root, "", 1, depth, out, cursor);
        if (cursor.hitLimit()) out.append(TRUNCATED);
        return ToolExecutionResult.ok(out.toString());
    }

    /**
     * 递归展开。
     *
     * <p>{@code level} 从 1 起（根目录算第 0 层，它的孩子是第 1 层），
     * 到达 {@code maxDepth} 即停止 —— 也就是「children 不再展开」。
     */
    private static void walk(File directory, String indent, int level, int maxDepth,
                             StringBuilder out, Cursor cursor) {
        if (level > maxDepth || cursor.full()) return;
        File[] children = directory.listFiles();
        if (children == null) return;
        Arrays.sort(children, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));

        for (File child : children) {
            if (cursor.full()) return;
            String name = child.getName();
            if (!cursor.includeHidden && name.startsWith(".")) continue;

            if (isCollapsed(child, name, level)) {
                out.append(indent).append(BRANCH).append(name).append(COLLAPSED_MARK);
                cursor.count();
                continue;
            }

            out.append(indent).append(BRANCH).append(name);
            if (child.isDirectory()) out.append(DIRECTORY_SUFFIX);
            out.append('\n');
            cursor.count();
            if (child.isDirectory()) {
                walk(child, indent + INDENT_STEP, level + 1, maxDepth, out, cursor);
            }
        }
    }

    /** 构建产物目录：名字在名单里、是目录、且不是第一层。 */
    private static boolean isCollapsed(File child, String name, int level) {
        return level > 0 && child.isDirectory() && BULKY_DIRECTORIES.contains(name);
    }

    /** 展开状态：是否含隐藏项、已输出多少条、是否触顶。 */
    private static final class Cursor {
        private final boolean includeHidden;
        private int entries;

        private Cursor(boolean includeHidden) {
            this.includeHidden = includeHidden;
        }

        private void count() { entries++; }

        private boolean full() { return entries >= MAX_ENTRIES; }

        private boolean hitLimit() { return entries >= MAX_ENTRIES; }
    }
}
