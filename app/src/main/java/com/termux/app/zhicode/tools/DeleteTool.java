package com.termux.app.zhicode.tools;

import com.termux.app.zhicode.model.SessionConfig;
import com.termux.app.zhicode.model.ToolExecutionResult;
import com.termux.shared.termux.TermuxConstants;

import org.json.JSONObject;

import java.io.File;

/**
 * 删除文件，或（显式要求时）删除整个目录树。
 *
 * <h3>三层保护，各有各的理由</h3>
 * <ol>
 *   <li><b>保护区。</b>工程根、HOME、{@code /}、Termux prefix 一律拒绝。
 *       这些路径一旦被删，用户的整个环境就没了，而这不是「撤销上一次操作」能恢复的。
 *       比较用规范化路径（{@link PathPolicy#resolve} 已经做过），
 *       否则 {@code /data/…/home/..} 之类的写法能绕过字符串比较。</li>
 *   <li><b>非空目录必须显式递归。</b>模型有时会把「删掉这个目录」当成删掉它下面
 *       某一个文件。要求 {@code recursive=true} 强迫它把意图说出来。</li>
 *   <li><b>删除前留 diff。</b>只有 ≤5 MiB 的普通文件才留（再大的内容进界面显示的 diff
 *       只会把界面压垮），但只要有，用户就还有一份「刚才删的是什么」。</li>
 * </ol>
 *
 * <h3>不存在的路径算成功</h3>
 * 「已经没有了」与「我删掉了」对调用方是同一个结果，报错误只会让模型重试一遍。
 */
final class DeleteTool implements ZhiTool {

    private static final long MAX_DIFF_BYTES = 5L * 1024L * 1024L;

    private static final String ROOT_PATH = "/";

    @Override public String name() { return "Delete"; }

    @Override public String description() {
        return "Delete a file or, only when recursive=true, a directory tree. Use carefully.";
    }

    @Override public PermissionKind permissionKind() { return PermissionKind.WRITE; }

    @Override public JSONObject inputSchema() {
        try {
            JSONObject properties = new JSONObject()
                .put("path", ToolSchemas.string("Path to delete."))
                .put("recursive", ToolSchemas.bool("Required for non-empty directories."));
            return ToolSchemas.object(properties, "path");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override public ToolExecutionResult execute(SessionConfig config, JSONObject input) throws Exception {
        String requested = input.getString("path");
        File target = PathPolicy.resolve(config.projectDirectory, requested);

        if (isProtected(target, config)) {
            return ToolExecutionResult.error("Refusing to delete protected root: " + target);
        }
        if (!target.exists()) return ToolExecutionResult.ok("Already absent: " + target);

        boolean recursive = input.optBoolean("recursive", false);
        String nonEmpty = rejectNonEmptyDirectory(target, recursive);
        if (nonEmpty != null) return ToolExecutionResult.error(nonEmpty);

        UnifiedDiff.Result diff = deletionDiff(requested, target);
        if (!remove(target, recursive)) return ToolExecutionResult.error("Delete failed: " + target);
        return ToolExecutionResult.okWithDiff("Deleted " + target, diff.text, diff.additions, diff.deletions);
    }

    /**
     * 是否是绝不能删的根。
     *
     * <p>工程根与 HOME 的比较对象都是规范化之后的路径；prefix 与 {@code /} 用绝对路径
     * 直接比，因为它们本身就是规范形态。
     */
    private static boolean isProtected(File target, SessionConfig config) throws Exception {
        File project = new File(config.projectDirectory).getCanonicalFile();
        File home = new File(TermuxConstants.TERMUX_HOME_DIR_PATH).getCanonicalFile();
        String absolute = target.getAbsolutePath();
        return target.equals(project)
            || target.equals(home)
            || absolute.equals(ROOT_PATH)
            || absolute.equals(TermuxConstants.TERMUX_PREFIX_DIR_PATH);
    }

    /** 非空目录且没有显式递归时返回错误文案。 */
    private static String rejectNonEmptyDirectory(File target, boolean recursive) {
        if (recursive || !target.isDirectory()) return null;
        String[] children = target.list();
        if (children == null || children.length == 0) return null;
        return "Directory is not empty; set recursive=true explicitly.";
    }

    /** 删除前的内容快照，用于界面显示删除前后的 diff；文件太大就没有。 */
    private static UnifiedDiff.Result deletionDiff(String requested, File target) throws Exception {
        boolean worthRecording = target.isFile() && target.length() <= MAX_DIFF_BYTES;
        return worthRecording
            ? UnifiedDiff.deleted(requested, TextFiles.readText(target))
            : new UnifiedDiff.Result("", 0, 0);
    }

    /**
     * 递归删除。
     *
     * <p>{@code file.delete()} 对非空目录会直接返回 false，所以目录必须先删孩子。
     * 孩子里只要有一个删不掉就整体返回 false，让调用方报错而不是
     * 谎报「已删除」而留下半个目录树。
     */
    private static boolean remove(File target, boolean recursive) {
        if (target.isDirectory()) {
            File[] children = target.listFiles();
            if (children != null && children.length > 0) {
                if (!recursive) return false;
                for (File child : children) {
                    // 孩子一律按 recursive 传下去：上层已经同意删整棵树了。
                    if (!remove(child, true)) return false;
                }
            }
        }
        return target.delete();
    }
}
