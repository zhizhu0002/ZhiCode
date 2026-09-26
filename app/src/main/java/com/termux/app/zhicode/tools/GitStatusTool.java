package com.termux.app.zhicode.tools;

import com.termux.app.zhicode.model.SessionConfig;
import com.termux.app.zhicode.model.ToolExecutionResult;
import com.termux.app.zhicode.termux.TermuxShellExecutor;

import org.json.JSONObject;

/**
 * 只读的 git 概览。
 *
 * <h3>为什么不用 {@code git status --porcelain} 直接输出</h3>
 * 一次调用里同时给出四段信息：分支与暂存区状态、未暂存的改动统计、
 * 已暂存的改动统计、最近五次提交。分成四次调用会让模型看到的是**四个时刻**的仓库
 * （它自己中途可能改了文件），而一次调用得到的是一个一致的快照。
 *
 * <h3>开头那句 {@code git rev-parse … || exit 2}</h3>
 * 它同时干两件事：确认当前目录确实在某个仓库里，并把退出码固定成 2。
 * 不在仓库里时后续所有 git 命令都会往标准错误刷一堆「not a git repository」，
 * 而模型从那段噪音里看不出真正的原因就是「这里不是仓库」。
 *
 * <h3>{@code --short --branch} 而不是 {@code --porcelain}</h3>
 * 带 {@code --branch} 时它会先输出一行 {@code ## branch…}。模型据此知道
 * 当前分支 —— 否则「改动了三个文件」这句话缺少它最需要的上下文（在哪个分支上）。
 */
public final class GitStatusTool implements ZhiTool {

    private static final int TIMEOUT_MS = 30_000;

    /** 四段查询，按输出次序拼接。每段都带自己的标题行，便于模型定位。 */
    private static final String[] SECTIONS = {
        "printf '\\n-- status --\\n'; git status --short --branch",
        "printf '\\n-- diff stat --\\n'; git diff --stat",
        "printf '\\n-- staged stat --\\n'; git diff --cached --stat",
        "printf '\\n-- recent --\\n'; git log -5 --oneline --decorate 2>/dev/null || true",
    };

    /** 见类注释：不在仓库里时立刻以 2 退出，不要刷一堆 git 报错。 */
    private static final String REQUIRE_REPOSITORY = "git rev-parse --show-toplevel 2>/dev/null || exit 2; ";

    private final TermuxShellExecutor shell;

    public GitStatusTool(TermuxShellExecutor shell) {
        this.shell = shell;
    }

    @Override public String name() { return "GitStatus"; }

    @Override public String description() {
        return "Inspect repository branch, porcelain status, changed-file summary and recent commits"
            + " without editing files.";
    }

    @Override public PermissionKind permissionKind() { return PermissionKind.READ; }

    @Override public JSONObject inputSchema() { return ToolSchemas.object(new JSONObject()); }

    @Override public ToolExecutionResult execute(SessionConfig config, JSONObject input) throws Exception {
        TermuxShellExecutor.Result result = shell.execute(command(), config.projectDirectory, TIMEOUT_MS);
        return ToolExecutionResult.command(result.exitCode, result.combined());
    }

    private static String command() {
        StringBuilder command = new StringBuilder(REQUIRE_REPOSITORY);
        for (int i = 0; i < SECTIONS.length; i++) {
            if (i > 0) command.append("; ");
            command.append(SECTIONS[i]);
        }
        return command.toString();
    }
}
