package com.termux.app.zhicode.tools;

import com.termux.app.zhicode.model.SessionConfig;
import com.termux.app.zhicode.model.ToolExecutionResult;
import com.termux.app.zhicode.termux.TermuxShellExecutor;

import org.json.JSONObject;

import java.io.File;

/**
 * 退出隔离工作树，回到原来的工程目录。
 *
 * <h3>三种结局</h3>
 * <ol>
 *   <li><b>干净且没要求保留</b> → 把工作树删掉。这是最常见的情况：
 *       试验成功、改动已经合并回主工作区（或者根本不需要），留下一个空目录没有意义。</li>
 *   <li><b>有改动</b> → 保留，并在回复里说明「有改动、已保留」。
 *       自动删除会丢掉用户还没决定要不要的东西。</li>
 *   <li><b>清理失败</b> → 保留并报出失败原因。git 会因为「工作树里还有未跟踪文件」
 *       拒绝删除，而那句话本身没告诉我们发生了什么；把它原样带出来更有用。</li>
 * </ol>
 *
 * <h3>为什么无论哪种结局都要清掉会话里的工作树状态</h3>
 * 用户说「退出」就是退出。哪怕工作树还留在磁盘上，后续的文件与命令操作也必须
 * 回到原工程目录 —— 否则 {@code ExitWorktree} 之后模型以为自己在原目录、
 * 实际还在隔离区里改文件。
 */
public final class ExitWorktreeTool implements ZhiTool {

    private static final int STATUS_TIMEOUT_MS = 30_000;
    private static final int REMOVE_TIMEOUT_MS = 60_000;
    private static final String NOT_IN_WORKTREE = "Not currently in a ZhiCode worktree.";

    private final TermuxShellExecutor shell;

    public ExitWorktreeTool(TermuxShellExecutor shell) {
        this.shell = shell;
    }

    @Override public String name() { return "ExitWorktree"; }

    @Override public String description() {
        return "Leave the current ZhiCode worktree and return to the original project."
            + " Clean worktrees are removed automatically; changed worktrees can be kept.";
    }

    @Override public PermissionKind permissionKind() { return PermissionKind.SHELL; }

    @Override public JSONObject inputSchema() {
        JSONObject properties = new JSONObject();
        try {
            properties.put("keep", ToolSchemas.bool("Keep the worktree even if clean or after changes."));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return ToolSchemas.object(properties);
    }

    @Override public ToolExecutionResult execute(SessionConfig config, JSONObject input) throws Exception {
        String worktree = config.worktreePath == null ? "" : config.worktreePath;
        String root = config.worktreeOriginalDirectory == null ? "" : config.worktreeOriginalDirectory;
        if (worktree.isEmpty()) return ToolExecutionResult.ok(NOT_IN_WORKTREE);

        String note = input.optBoolean("keep", false)
            ? keepWithoutChecking()
            : settle(worktree, root);

        returnToProject(config, root);
        return ToolExecutionResult.ok("Exited worktree. Active project: " + config.projectDirectory
            + "\n" + note);
    }

    /** 用户明确要求保留时不查状态：省一次 git 调用，也不会因为清不掉而报错。 */
    private static String keepWithoutChecking() {
        return "Worktree kept.";
    }

    /** 干净就删、有改动就留。 */
    private String settle(String worktree, String root) throws Exception {
        TermuxShellExecutor.Result status =
            shell.execute("git status --porcelain", worktree, STATUS_TIMEOUT_MS);
        String changes = status.combined().trim();

        boolean clean = status.exitCode == 0 && changes.isEmpty();
        if (!clean) {
            // 「有改动」与「git 命令本身失败」走同一个分支：两种情况下都不该删，
            // 区别只在提示语里说不说得出是哪种。
            return changes.isEmpty()
                ? "Worktree kept."
                : "Worktree has changes and was kept: " + worktree;
        }

        TermuxShellExecutor.Result removed = shell.execute(
            "git -C " + quoted(root) + " worktree remove --force " + quoted(worktree),
            root, REMOVE_TIMEOUT_MS);
        return removed.exitCode == 0
            ? "Clean worktree removed."
            : "Returned to project, but worktree cleanup failed: " + removed.combined();
    }

    /**
     * 状态归位。
     *
     * <p>只有原目录**确实存在**时才切回去：它可能已经被删掉（用户在隔离期间清理过），
     * 那时保留当前目录比切到一个不存在的路径好 —— 后者会让后续每个工具都以
     * 「目录不存在」失败。
     */
    private static void returnToProject(SessionConfig config, String root) {
        if (new File(root).isDirectory()) config.projectDirectory = root;
        config.worktreePath = "";
        config.worktreeOriginalDirectory = "";
    }

    private static String quoted(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
