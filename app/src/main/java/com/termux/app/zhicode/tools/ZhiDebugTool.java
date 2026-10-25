package com.termux.app.zhicode.tools;

import android.content.Context;

import com.termux.app.zhicode.model.SessionConfig;
import com.termux.app.zhicode.model.ToolExecutionResult;
import com.termux.app.zhicode.termux.TermuxShellExecutor;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Agent 用来检查<b>真机</b>进程的调试工具（只读为主）。
 *
 * <h3>这个工具原来还有一整套 sandbox scope，已随沙箱一起删除</h3>
 *
 * 原先它有两个 scope：
 * <ul>
 *   <li>{@code sandbox}（默认）：在 BlackBox 虚拟运行时内部走 Frida 通道做内存读写、
 *       库注入、扫描、hook —— 一整条不依赖 Root 也不依赖 ptrace 的调试链；</li>
 *   <li>{@code host}：真机进程，需要 Agent Root。</li>
 * </ul>
 *
 * 沙箱功能已整体移除，{@code sandbox} 这一支（含全部 {@code frida_*}、
 * {@code load_library}、PID 归属核对）一并删掉。现在只剩 {@code host}，
 * 因此 {@code scope} 参数退化为一个**只接受 {@code host} 或空串**的兼容位。
 *
 * <p>保留 {@code scope} 而不是直接删参：模型侧的工具 schema 与既有提示词里
 * 都写着这个词，突然消失会让旧调用报"未知参数"，那比留一个无用参数更吵。
 * 它现在的唯一职责是**拦住**写了 {@code scope=sandbox} 的旧调用并给出明确原因。
 *
 * <h3>为什么 host 不提供原始内存读写与库注入</h3>
 *
 * 放开它等于把「任意读写本机进程内存、往任意进程里 dlopen」做成一键工具。
 * 而真机上的正当需求（调试自己的进程）用常规工具链更合适 ——
 * 一个 Agent 随手就能写别的应用内存，风险与收益完全不成比例。
 */
public final class ZhiDebugTool implements ZhiTool {

    private static final String SCOPE_HOST = "host";

    /** 真机侧命令的超时。 */
    private static final int HOST_COMMAND_TIMEOUT_MS = 20000;
    private static final int HOST_PIDOF_TIMEOUT_MS = 10000;

    private final Context context;
    private final TermuxShellExecutor shell;

    public ZhiDebugTool(Context context, TermuxShellExecutor shell) {
        this.context = context.getApplicationContext();
        this.shell = shell;
    }

    @Override
    public String name() {
        return "Debug";
    }

    @Override
    public String description() {
        return "Inspect a real-phone process (maps/modules/threads/memory_read/signal). "
                + "Requires Agent Root. The embedded virtual runtime was removed, so "
                + "scope=sandbox is no longer accepted.";
    }

    @Override
    public PermissionKind permissionKind() {
        return PermissionKind.SYSTEM;
    }

    @Override
    public JSONObject inputSchema() {
        try {
            JSONObject properties = new JSONObject();
            // `scope` 现在只有 host 一个合法值，但**必须留着**：见类注释最后一段。
            properties.put("scope", ToolSchemas.string(
                    "Only \"host\" (default). The sandbox scope was removed with the embedded virtual runtime."));
            properties.put("action", ToolSchemas.string(
                    "process_list, process_info, maps, modules, threads, memory_read, "
                            + "memory_write, thread_dump, signal"));
            properties.put("package", ToolSchemas.string("Android package used for PID resolution."));
            properties.put("pid", ToolSchemas.integer("Concrete process id.", 1));
            properties.put("filter", ToolSchemas.string("Optional substring filter for maps/modules."));
            properties.put("address", ToolSchemas.string("Hex virtual address for memory_read/memory_write."));
            properties.put("size", ToolSchemas.integer("Byte count. memory_read is capped at 65536.", 1));
            properties.put("data", ToolSchemas.string("Hex or base64 bytes for memory_write."));
            properties.put("format", ToolSchemas.string("hex (default) or base64."));
            properties.put("signal", ToolSchemas.integer("Linux signal number to send.", 1));
            properties.put("max_lines", ToolSchemas.integer("Maximum maps lines.", 1));
            properties.put("max_modules", ToolSchemas.integer("Maximum module rows.", 1));
            return ToolSchemas.object(properties, "action");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public ToolExecutionResult execute(SessionConfig config, JSONObject in) throws Exception {
        String scope = in.optString("scope", SCOPE_HOST).trim().toLowerCase(Locale.US);
        String action = in.optString("action", "").trim().toLowerCase(Locale.US);
        if (action.isEmpty()) return ToolExecutionResult.error("Debug action 不能为空");
        if ("sandbox".equals(scope)) {
            // 明确的迁移提示，而不是"未知 scope" —— 后者会让人以为是拼写问题。
            return ToolExecutionResult.error(
                    "Debug 的 sandbox scope 已随内置虚拟运行时一起移除；"
                            + "现在只支持 scope=host（需在设置中开启 Agent Root）");
        }
        if (!SCOPE_HOST.equals(scope) && !scope.isEmpty()) {
            return ToolExecutionResult.error("未知 Debug scope: " + scope);
        }
        try {
            return host(config, action, in);
        } catch (Throwable e) {
            return ToolExecutionResult.error("Debug host/" + action + " 失败: "
                    + e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()));
        }
    }

    // ------------------------------------------------------------ 真机 scope

    /**
     * 真机进程检查。仅在用户显式开启 Agent Root 后可用。
     *
     * <p>这里不提供原始内存读写与库注入。放开它等于把「任意读写本机进程内存、
     * 往任意进程里 dlopen」做成一键工具，而这条能力在真机上的正当需求
     * （调试自己的进程）用常规工具链更合适。沙箱内部不受此限——
     * 那里的边界由虚拟运行时自己划定，且只作用于它自己的 guest。
     */
    private ToolExecutionResult host(SessionConfig config, String action, JSONObject in) throws Exception {
        if (config == null || !config.rootExecutionEnabled) {
            return ToolExecutionResult.error("host 进程调试需要先在 ZhiCode 设置中开启 Agent Root");
        }
        if ("memory_read".equals(action) || "memory_write".equals(action) || "load_library".equals(action)) {
            return ToolExecutionResult.error("真机 raw memory/远程库注入不由 Debug 自动执行。该能力仅对 ZhiCode 沙箱 Guest 内置开放；如确实要调试自有真机进程，请显式使用 Root + 你安装的 lldb/gdb/frida 工具链。");
        }
        if ("process_list".equals(action)) {
            return rootCommand("ps -A -o PID,UID,NAME,ARGS 2>/dev/null || ps -A", config.projectDirectory, HOST_COMMAND_TIMEOUT_MS);
        }

        int pid = resolveHostPid(config, in);
        String cwd = config.projectDirectory;
        switch (action) {
            case "process_info":
                return rootCommand(procInfoCommand(pid), cwd, HOST_COMMAND_TIMEOUT_MS);
            case "maps": {
                String filter = in.optString("filter", "").trim();
                String command = "cat /proc/" + pid + "/maps";
                // grep 找不到匹配时退出码为 1；加 || true 免得把它报成命令失败。
                if (!filter.isEmpty()) command += " | grep -F -- " + quote(filter) + " || true";
                return rootCommand(command, cwd, HOST_COMMAND_TIMEOUT_MS);
            }
            case "modules": {
                ToolExecutionResult raw = rootCommand("cat /proc/" + pid + "/maps", cwd, HOST_COMMAND_TIMEOUT_MS);
                if (raw.exitCode != 0) return raw;
                return ToolExecutionResult.ok(hostModules(raw.content, in.optString("filter", "")).toString(2));
            }
            case "threads":
                return rootCommand("for d in /proc/" + pid + "/task/[0-9]*; do [ -d \"$d\" ] || continue; printf '%s ' \"${d##*/}\"; cat \"$d/comm\" 2>/dev/null || echo '?'; done", cwd, HOST_COMMAND_TIMEOUT_MS);
            case "thread_dump":
                // 真机上拿 Java 栈只能靠 SIGQUIT，它会把栈写进 logcat/tombstone，
                // 因此这里只负责发信号并说明去哪儿看，不假装能直接返回栈内容。
                return rootCommand("kill -3 " + pid + "; echo 'SIGQUIT sent to PID " + pid + "; inspect logcat/tombstone for runtime dump'", cwd, HOST_COMMAND_TIMEOUT_MS);
            case "gc":
                return ToolExecutionResult.error("host gc 没有通用安全接口；对沙箱 Guest 使用 scope=sandbox action=gc");
            case "signal": {
                int signal = Math.max(1, Math.min(64, in.optInt("signal", 3)));
                return rootCommand("kill -" + signal + " " + pid, cwd, HOST_COMMAND_TIMEOUT_MS);
            }
            default:
                return ToolExecutionResult.error("未知 host Debug action: " + action);
        }
    }

    private static String procInfoCommand(int pid) {
        return "printf '%s\\n' '--- status ---'; cat /proc/" + pid + "/status"
                + "; printf '%s\\n' '--- cmdline ---'; tr '\\000' ' ' </proc/" + pid + "/cmdline; echo"
                + "; printf '%s\\n' '--- exe ---'; readlink /proc/" + pid + "/exe || true";
    }

    /** 真机 PID 解析：优先用调用者给的 pid，否则用包名去 pidof。 */
    private int resolveHostPid(SessionConfig config, JSONObject in) throws Exception {
        int pid = in.optInt("pid", -1);
        if (pid > 1) return pid;
        String pkg = in.optString("package", "").trim();
        if (pkg.isEmpty()) throw new IllegalArgumentException("host 调试需要 pid 或 package");
        TermuxShellExecutor.Result result = shell.executeAsRoot(
                "pidof " + quote(pkg) + " 2>/dev/null | awk '{print $1}'",
                config.projectDirectory, HOST_PIDOF_TIMEOUT_MS, null);
        String stdout = result.stdout.trim();
        if (result.exitCode != 0 || stdout.isEmpty()) throw new IllegalStateException("找不到真机进程: " + pkg);
        // 一个包名可能对应多个进程，取第一个。
        return Integer.parseInt(stdout.split("\\s+")[0]);
    }

    /**
     * 以 root 身份执行一条命令。
     *
     * <p>前置一段 uid 自检。有些设备的 su 会在拒绝提权时静默退回普通用户身份，
     * 于是命令「成功」执行了，只是权限不对——那会得到一批看起来正常、
     * 实则读不到目标进程的结果。宁可让它在第一步就明确失败。
     */
    private ToolExecutionResult rootCommand(String command, String cwd, int timeoutMs) throws Exception {
        String verified = "if [ \"$(id -u)\" != 0 ]; then echo '[ZhiCode] su did not grant uid 0' >&2; exit 126; fi; " + command;
        TermuxShellExecutor.Result result = shell.executeAsRoot(verified, cwd, timeoutMs, null);
        return ToolExecutionResult.command(result.timedOut ? 124 : result.exitCode, result.combined());
    }

    /** 把 {@code /proc/<pid>/maps} 的文本按文件路径聚合成模块列表。 */
    private static JSONArray hostModules(String maps, String filter) throws Exception {
        String needle = filter == null ? "" : filter.trim().toLowerCase(Locale.US);
        Map<String, long[]> grouped = new LinkedHashMap<>();
        for (String line : maps.split("\\n")) {
            String[] parts = line.trim().split("\\s+", 6);
            if (parts.length < 6) continue;
            String path = parts[5];
            if (path.isEmpty() || path.startsWith("[")) continue;
            if (!needle.isEmpty() && !path.toLowerCase(Locale.US).contains(needle)) continue;
            String[] range = parts[0].split("-", 2);
            if (range.length != 2) continue;
            try {
                long start = Long.parseUnsignedLong(range[0], 16);
                long end = Long.parseUnsignedLong(range[1], 16);
                long base = start - Long.parseUnsignedLong(parts[2], 16);
                long[] aggregate = grouped.get(path);
                if (aggregate == null) {
                    grouped.put(path, new long[]{base, start, end});
                } else {
                    // 地址空间比较一律走无符号：内核地址在高位，按有符号比会得到反的结果。
                    if (Long.compareUnsigned(base, aggregate[0]) < 0) aggregate[0] = base;
                    if (Long.compareUnsigned(start, aggregate[1]) < 0) aggregate[1] = start;
                    if (Long.compareUnsigned(end, aggregate[2]) > 0) aggregate[2] = end;
                }
            } catch (Throwable malformed) {
                // 读 /proc 时进程可能已经退出，行会缺字段；跳过即可。
            }
        }
        JSONArray out = new JSONArray();
        for (Map.Entry<String, long[]> entry : grouped.entrySet()) {
            long[] aggregate = entry.getValue();
            out.put(new JSONObject()
                    .put("path", entry.getKey())
                    .put("base", hex(aggregate[0]))
                    .put("start", hex(aggregate[1]))
                    .put("end", hex(aggregate[2]))
                    .put("size", aggregate[2] - aggregate[1]));
        }
        return out;
    }

    // ------------------------------------------------------------------ 杂项

    /** 按白名单抽取调用者给的字段，缺省的不填。 */
    private static JSONObject selectFields(JSONObject in, String[] keys) throws Exception {
        JSONObject out = new JSONObject();
        for (String key : keys) {
            if (in.has(key)) out.put(key, in.get(key));
        }
        return out;
    }

    private static String hex(long value) {
        return "0x" + Long.toUnsignedString(value, 16);
    }

    /** 单引号包裹并转义内部单引号，供 shell 命令拼接。 */
    private static String quote(String value) {
        return "'" + (value == null ? "" : value.replace("'", "'\\''")) + "'";
    }
}
