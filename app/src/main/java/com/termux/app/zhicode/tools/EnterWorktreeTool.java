package com.termux.app.zhicode.tools;

import com.termux.app.zhicode.model.SessionConfig;
import com.termux.app.zhicode.model.ToolExecutionResult;
import com.termux.app.zhicode.termux.TermuxShellExecutor;
import com.termux.shared.termux.TermuxConstants;

import org.json.JSONObject;

import java.io.File;
import java.util.UUID;

/**
 * 建一个 git worktree 并把后续文件/命令操作都切过去。
 *
 * <h3>它解决什么</h3>
 * 「试一个改动，不行就整个丢掉」在没有隔离时只能靠手工回滚，而回滚本身就可能出错。
 * worktree 是同一仓库的一个独立检出：改动、编译、运行都在里面发生，
 * 主工作区一个字节都不动。{@link ExitWorktreeTool} 负责回来。
 *
 * <h3>{@code --detach} 是必要的</h3>
 * 不 detach 的话 worktree 会占用一个分支名，于是「同一个分支不能同时被两个 worktree
 * 检出」这条 git 规则会让第二次调用直接失败。而这里根本不需要分支 ——
 * 它是一个临时的试验场，HEAD 就够了。
 *
 * <h3>路径放在应用私有目录</h3>
 * 放在 {@code dataDir()/worktrees} 而不是工程旁边：工程目录可能是用户的项目仓库，
 * 往里面塞东西会污染他的 {@code .gitignore} 或导致误提交。
 *
 * <h3>标签要消毒</h3>
 * {@code name} 参数会进入文件路径，所以非 {@code [A-Za-z0-9._-]} 的字符一律换成
 * {@code -}：模型偶尔会用中文或空格当标签，那会造出一个路径里有空格的目录，
 * 后面每一次 shell 调用都要额外注意引号。
 */
final class EnterWorktreeTool implements ZhiTool {

    private static final int TIMEOUT_MS = 120_000;
    private static final String DEFAULT_LABEL = "zhi";
    private static final String UNSAFE_LABEL_CHARS = "[^A-Za-z0-9._-]";
    private static final int SHORT_ID_CHARS = 8;

    private final TermuxShellExecutor shell;

    public EnterWorktreeTool(TermuxShellExecutor shell) {
        this.shell = shell;
    }

    @Override public String name() { return "EnterWorktree"; }

    @Override public String description() {
        return "Create and enter an isolated git worktree for subsequent ZhiCode file and shell operations.";
    }

    @Override public PermissionKind permissionKind() { return PermissionKind.SHELL; }

    @Override public JSONObject inputSchema() {
        JSONObject properties = new JSONObject();
        try {
            properties.put("name", ToolSchemas.string("Optional short worktree label."));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return ToolSchemas.object(properties);
    }

    @Override public ToolExecutionResult execute(SessionConfig config, JSONObject input) throws Exception {
        if (alreadyInsideWorktree(config)) {
            return ToolExecutionResult.ok("Already in worktree: " + config.worktreePath);
        }
        String root = new File(config.projectDirectory).getCanonicalPath();
        String path = worktreePath(input.optString("name", ""));

        TermuxShellExecutor.Result result = shell.execute(addWorktreeCommand(root, path), root, TIMEOUT_MS);
        if (result.exitCode != 0) return ToolExecutionResult.command(result.exitCode, result.combined());

        // 三处都要改：记住原来的目录、记住新路径、把 projectDirectory 切过去。
        // 切 projectDirectory 是关键 —— 所有文件工具都从它解析相对路径。
        config.worktreeOriginalDirectory = root;
        config.worktreePath = path;
        config.projectDirectory = path;
        return ToolExecutionResult.ok("Entered isolated worktree: " + path
            + "\nAll subsequent file/shell tools now operate there until ExitWorktree.");
    }

    private static boolean alreadyInsideWorktree(SessionConfig config) {
        return config.worktreePath != null
            && !config.worktreePath.isEmpty()
            && new File(config.worktreePath).isDirectory();
    }

    /** 消毒后的标签 + 一段随机后缀，见类注释。 */
    private static String worktreePath(String requestedLabel) {
        String label = requestedLabel.replaceAll(UNSAFE_LABEL_CHARS, "-");
        if (label.isEmpty()) label = DEFAULT_LABEL;
        String unique = UUID.randomUUID().toString().substring(0, SHORT_ID_CHARS);
        return new File(TermuxConstants.dataDir(), "worktrees/" + label + "-" + unique).getAbsolutePath();
    }

    /**
     * 建 worktree 的命令。
     *
     * <p>第一步的 {@code rev-parse --is-inside-work-tree} 是前置检查：不在仓库里时
     * 直接失败，而不是先建出一堆空目录再让 {@code git worktree add} 报错 ——
     * 后者会留下需要手工清理的残骸。
     */
    private static String addWorktreeCommand(String root, String path) {
        return "git -C " + quoted(root) + " rev-parse --is-inside-work-tree >/dev/null"
            + " && mkdir -p " + quoted(new File(path).getParent())
            + " && git -C " + quoted(root) + " worktree add --detach " + quoted(path) + " HEAD";
    }

    /** 单引号包裹 + 内层单引号转义。用户填的路径可能含空格或引号，不能直接拼进命令。 */
    private static String quoted(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
