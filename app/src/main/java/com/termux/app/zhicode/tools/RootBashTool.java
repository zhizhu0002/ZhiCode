package com.termux.app.zhicode.tools;

import com.termux.app.zhicode.model.SessionConfig;
import com.termux.app.zhicode.model.ToolExecutionResult;
import com.termux.app.zhicode.termux.TermuxShellExecutor;

import org.json.JSONObject;

import java.io.File;

/**
 * 以 Android root（uid 0）身份执行命令。
 *
 * <h3>三道门槛，缺一不可</h3>
 * <ol>
 *   <li><b>设置开关。</b>{@code rootExecutionEnabled} 默认关闭。关着的时候工具不是
 *       「隐藏」而是**返回错误**：模型能看到它、也会知道为什么用不了。</li>
 *   <li><b>真实授权。</b>命令外面包了一段 {@code id -u} 检查。这不是防用户，
 *       而是防「su 提示被超时拒绝、命令却继续以普通身份跑完」——
 *       那时模型会以为它真的以 root 改成了某个系统文件。检查不过就 exit 126 并说明。</li>
 *   <li><b>超时。</b>默认跟随 shell 的默认值，上限一小时。root 命令更容易写出
 *       死循环（比如误操作 {@code /sys} 下的某个节点），没有上限会挂住整个会话。</li>
 * </ol>
 *
 * <h3>{@code cwd} 为什么允许绝对路径</h3>
 * root 命令常常要在工程之外（{@code /data/adb}、{@code /system}）操作。
 * 但相对路径仍然从工程目录解析 —— 模型用相对路径时想指的几乎总是工程内的位置。
 */
final class RootBashTool implements ZhiTool {

    private static final int MIN_TIMEOUT_MS = 1000;
    private static final int MAX_TIMEOUT_MS = 3_600_000;
    private static final int EXIT_TIMED_OUT = 124;
    private static final int EXIT_NOT_ROOT = 126;

    /** 见类注释第 2 点。这段会拼在用户命令**之前**，所以它自己的退出码优先。 */
    private static final String ASSERT_ROOT =
        "if [ \"$(id -u)\" != 0 ]; then echo '[ZhiCode] su did not grant uid 0' >&2; exit "
            + EXIT_NOT_ROOT + "; fi; ";

    private static final String DISABLED = "Agent Root is disabled in Settings";
    private static final String EMPTY_COMMAND = "Root command is empty";

    private final TermuxShellExecutor executor;

    public RootBashTool(TermuxShellExecutor executor) {
        this.executor = executor;
    }

    @Override public String name() { return "Root"; }

    @Override public String description() {
        return "Run an Android uid-0 command via su; only when Bash is insufficient; never for pkg/apt.";
    }

    @Override public PermissionKind permissionKind() { return PermissionKind.SYSTEM; }

    @Override public JSONObject inputSchema() {
        JSONObject properties = new JSONObject();
        try {
            properties.put("command", ToolSchemas.string("Command to execute as Android root (uid 0)."));
            properties.put("cwd", ToolSchemas.string(
                "Optional working directory. Absolute root-only paths are allowed;"
                    + " relative paths resolve from the active project."));
            properties.put("timeout_ms", ToolSchemas.integer("Timeout in milliseconds (maximum 3600000).", 1000));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return ToolSchemas.object(properties, "command");
    }

    @Override public ToolExecutionResult execute(SessionConfig config, JSONObject input) throws Exception {
        return execute(config, input, null);
    }

    @Override public ToolExecutionResult execute(SessionConfig config, JSONObject input, ProgressListener progress)
            throws Exception {
        if (config == null || !config.rootExecutionEnabled) {
            return ToolExecutionResult.error(DISABLED);
        }
        String command = input.optString("command", "").trim();
        if (command.isEmpty()) return ToolExecutionResult.error(EMPTY_COMMAND);

        int timeout = clampTimeout(input.optInt("timeout_ms", TermuxShellExecutor.DEFAULT_TIMEOUT_MS));
        TermuxShellExecutor.OutputListener live = progress == null
            ? null
            : (chunk, stderr, elapsed) -> progress.onProgress(chunk, stderr, elapsed);

        TermuxShellExecutor.Result result = executor.executeAsRoot(
            ASSERT_ROOT + command, resolveCwd(config, input), timeout, live);
        // 超时单独映射成 124（与 timeout(1) 的约定一致），否则模型会把
        // 「被杀掉的进程留下的退出码」当成命令自己的结论。
        int exitCode = result.timedOut ? EXIT_TIMED_OUT : result.exitCode;
        return ToolExecutionResult.command(exitCode, result.combined());
    }

    private static int clampTimeout(int requested) {
        return Math.max(MIN_TIMEOUT_MS, Math.min(MAX_TIMEOUT_MS, requested));
    }

    /** 相对路径从工程目录解析；见类注释。 */
    private static String resolveCwd(SessionConfig config, JSONObject input) {
        String requested = input.optString("cwd", config.projectDirectory);
        if (requested == null || requested.trim().isEmpty()) requested = config.projectDirectory;
        File cwd = new File(requested);
        if (!cwd.isAbsolute()) cwd = new File(config.projectDirectory, requested);
        return cwd.getAbsolutePath();
    }
}
