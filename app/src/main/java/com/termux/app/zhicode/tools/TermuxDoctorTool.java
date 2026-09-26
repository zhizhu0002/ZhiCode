package com.termux.app.zhicode.tools;

import com.termux.app.zhicode.model.SessionConfig;
import com.termux.app.zhicode.model.ToolExecutionResult;
import com.termux.app.zhicode.termux.TermuxShellExecutor;

import org.json.JSONObject;

/**
 * 只读的环境诊断。
 *
 * <h3>它回答的问题</h3>
 * 「为什么命令跑不起来」在这套环境里有几个固定原因：PATH/PREFIX 没设对、
 * 包管理器处于半配置状态、工具链根本没装、磁盘满了。这个工具一次性
 * 把这四类事实都取回来，模型就不必靠一次次试命令去排除。
 *
 * <h3>为什么一次调用里塞这么多段</h3>
 * 分段执行意味着多次往返，而每次往返模型都可能基于不完整的信息做出判断。
 * 这里用一次 shell 调用取回一个**同一时刻**的快照，段与段之间用标题行分隔。
 *
 * <h3>{@code dpkg --audit} 不是可有可无的</h3>
 * 它每次都返回 0，输出的是「还剩什么没配好」。少了它，一次全部正常的检查与
 * 一次「有半配置包」的检查在退出码上完全一样，而后者正是最常见的故障原因。
 *
 * <h3>{@code || true} 的用法</h3>
 * 每一段都以 {@code || true} 收尾：某一段失败（没有 sources.list、df 不可用）
 * 不该让整次诊断只返回第一段的输出。诊断工具最怕的就是「因为环境坏得比较厉害，
 * 所以诊断也坏了」。
 */
public final class TermuxDoctorTool implements ZhiTool {

    private static final int TIMEOUT_MS = 30_000;

    /** 需要确认存在性的常用工具。缺哪个往往就是某个命令失败的直接原因。 */
    private static final String TOOLCHAIN = "bash pkg apt dpkg git python node bun java javac clang make cmake";

    private final TermuxShellExecutor shell;

    public TermuxDoctorTool(TermuxShellExecutor shell) {
        this.shell = shell;
    }

    @Override public String name() { return "TermuxDoctor"; }

    @Override public String description() {
        return "Inspect the embedded Termux runtime, PATH/PREFIX, package manager, dpkg state,"
            + " architecture, disk space, and common compilers without modifying the environment.";
    }

    @Override public PermissionKind permissionKind() { return PermissionKind.READ; }

    @Override public JSONObject inputSchema() { return ToolSchemas.object(new JSONObject()); }

    @Override public ToolExecutionResult execute(SessionConfig config, JSONObject input) throws Exception {
        TermuxShellExecutor.Result result = shell.execute(command(), config.projectDirectory, TIMEOUT_MS);
        return ToolExecutionResult.command(result.exitCode, result.combined());
    }

    private static String command() {
        StringBuilder command = new StringBuilder();
        // 四段：环境变量 → 平台与工具链 → dpkg/apt 状态 → 磁盘。
        command.append("printf 'HOME=%s\\nPREFIX=%s\\nPATH=%s\\nTMPDIR=%s\\n'")
               .append(" \"$HOME\" \"$PREFIX\" \"$PATH\" \"$TMPDIR\"; ");
        command.append("uname -a; ");
        command.append("getprop ro.product.cpu.abi 2>/dev/null || true; ");
        command.append("for x in ").append(TOOLCHAIN)
               .append("; do printf '%-8s ' \"$x\"; command -v \"$x\" || echo missing; done; ");
        command.append("printf '\\n-- dpkg audit --\\n'; dpkg --audit 2>&1 || true; ");
        command.append("printf '\\n-- apt sources --\\n'; ");
        command.append("grep -Rhv '^[[:space:]]*#' \"$PREFIX/etc/apt/sources.list\" ")
               .append("\"$PREFIX/etc/apt/sources.list.d\"/*.list 2>/dev/null || true; ");
        command.append("printf '\\n-- disk --\\n'; df -h \"$HOME\" 2>/dev/null || true");
        return command.toString();
    }
}
