package com.termux.app.zhicode.tools;

import com.termux.app.zhicode.model.SessionConfig;
import com.termux.app.zhicode.model.ToolExecutionResult;
import com.termux.app.zhicode.termux.TermuxShellExecutor;

import org.json.JSONObject;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 在内置 Debian（PRoot）容器里跑 bash 命令。
 */
public final class BashTool implements ZhiTool {

    private static final int MIN_TIMEOUT_MS = 1000;
    private static final int MAX_TIMEOUT_MS = 2 * 60 * 60 * 1000;
    private static final int MIN_PACKAGE_TIMEOUT_MS = 60 * 60 * 1000;
    private static final int MIN_BUILD_TIMEOUT_MS = 15 * 60 * 1000;
    private static final int TIMEOUT_EXIT_CODE = 124;

    private static final List<String> BUILD_COMMANDS = Arrays.asList(
        "gradlew", "gradle", "mvn", "mvnw", "cmake --build", "ndk-build", "ninja",
        "cargo build", "cargo test", "npm run build", "npm test",
        "bun run build", "bun test", "make");

    private final TermuxShellExecutor executor;
    /** 可为 null（单测里不需要意图桥）。 */
    private final AndroidIntentBridge androidIntent;

    public BashTool(TermuxShellExecutor executor) {
        this(executor, null);
    }

    public BashTool(TermuxShellExecutor executor, AndroidIntentBridge androidIntent) {
        this.executor = executor;
        this.androidIntent = androidIntent;
    }

    @Override public String name() { return "Bash"; }

    @Override public String description() {
        return "Run bash inside the Ubuntu 24.04 (PRoot) container for project commands, builds, tests, git, and apt packages.";
    }

    @Override public JSONObject inputSchema() {
        JSONObject properties = new JSONObject();
        try {
            properties.put("command", ToolSchemas.string("Shell command to execute with bash -lc."));
            properties.put("cwd", ToolSchemas.string(
                "Optional working directory. Relative paths are resolved against the active project."));
            properties.put("timeout_ms", ToolSchemas.integer("Timeout in milliseconds.", 1000));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return ToolSchemas.object(properties, "command");
    }

    @Override public PermissionKind permissionKind() { return PermissionKind.SHELL; }

    @Override public ToolExecutionResult execute(SessionConfig config, JSONObject input) throws Exception {
        return execute(config, input, null);
    }

    @Override public ToolExecutionResult execute(SessionConfig config, JSONObject input, ProgressListener progress)
            throws Exception {
        String command = input.optString("command", "");

        if (androidIntent != null) {
            ToolExecutionResult bridged = androidIntent.tryExecuteAmStart(command);
            if (bridged != null) return bridged;
        }

        String cwd = PathPolicy.resolve(config.projectDirectory,
            input.optString("cwd", config.projectDirectory)).getAbsolutePath();
        String lower = command.toLowerCase(Locale.US);
        boolean packageCommand = isPackageCommand(lower);
        int timeout = effectiveTimeout(input, lower, packageCommand);

        TermuxShellExecutor.OutputListener live = progress == null
            ? null
            : (chunk, stderr, elapsed) -> progress.onProgress(chunk, stderr, elapsed);
        TermuxShellExecutor.Result result = executor.execute(command, cwd, timeout, live);
        return ToolExecutionResult.command(result.timedOut ? TIMEOUT_EXIT_CODE : result.exitCode, result.combined());
    }

    private static int effectiveTimeout(JSONObject input, String lowerCommand, boolean packageCommand) {
        int requested = input.optInt("timeout_ms", TermuxShellExecutor.DEFAULT_TIMEOUT_MS);
        int timeout = Math.max(MIN_TIMEOUT_MS, Math.min(requested, MAX_TIMEOUT_MS));
        if (packageCommand) return Math.max(timeout, MIN_PACKAGE_TIMEOUT_MS);
        if (looksLikeBuildCommand(lowerCommand)) return Math.max(timeout, MIN_BUILD_TIMEOUT_MS);
        return timeout;
    }

    private static boolean isPackageCommand(String lowerCommand) {
        return lowerCommand.contains("apt ") || lowerCommand.contains("apt-get ")
            || lowerCommand.contains("dpkg ") || lowerCommand.contains("dpkg-");
    }

    private static boolean looksLikeBuildCommand(String lowerCommand) {
        for (String command : BUILD_COMMANDS) {
            if (mentionsCommand(lowerCommand, command)) return true;
        }
        return false;
    }

    private static boolean mentionsCommand(String lowerCommand, String command) {
        if (lowerCommand.startsWith(command)) return true;
        for (String prefix : new String[]{" ", ";", "&&", "|"}) {
            if (lowerCommand.contains(prefix + command)) return true;
        }
        return false;
    }
}
