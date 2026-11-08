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
 * Agent 的调试工具，两个 scope。
 *
 * <table>
 *   <tr><th>scope</th><th>对象</th><th>前提</th><th>能力</th></tr>
 *   <tr>
 *     <td>{@code sandbox}（默认）</td>
 *     <td>沙箱里的 guest 进程</td>
 *     <td>无 —— 不需要 Root，也不需要 ptrace</td>
 *     <td>maps / modules / threads / 内存读写 / 库注入 / Frida</td>
 *   </tr>
 *   <tr>
 *     <td>{@code host}</td>
 *     <td>真机进程</td>
 *     <td>Agent Root</td>
 *     <td>只读为主：maps / modules / threads / signal</td>
 *   </tr>
 * </table>
 *
 * <h3>为什么 sandbox 能做的事情比 host 多得多</h3>
 * 因为拓扑不同，不是权限不同：
 *
 * · **sandbox**：调试代码和 guest 在**同一个进程**里。宿主
 *   （`SandboxProcessDebug`）是被广播唤起、跑在 guest 自己的进程内的，
 *   它对这个进程的内存是「自己的内存」—— 不需要 ptrace，也不需要 root。
 *   边界由虚拟运行时自己划定，且只作用于它自己的 guest。
 *
 * · **host**：那是**别的进程**。同样的能力要做成「任意读写本机进程内存、
 *   往任意进程里 dlopen」，一键可达 —— 风险与收益完全不成比例。
 *   真机上的正当需求（调试自己的进程）用常规工具链（lldb/gdb/frida）更合适。
 *
 * <h3>为什么默认是 sandbox</h3>
 * 因为它是**没有前置条件**的那一个。默认值指向需要用户先去设置里开 Root 的
 * 分支，只会让工具在第一次调用时失败。
 */
public final class ZhiDebugTool implements ZhiTool {

    private static final String SCOPE_SANDBOX = "sandbox";
    private static final String SCOPE_HOST = "host";

    /** 真机侧命令的超时。 */
    private static final int HOST_COMMAND_TIMEOUT_MS = 20000;
    private static final int HOST_PIDOF_TIMEOUT_MS = 10000;

    /**
     * guest 侧动作的超时。
     *
     * <p>比真机那条短：sandbox 的动作都是同进程内的读取或一次内存拷贝，
     * 没有「等一个命令行工具跑起来」这种不确定延迟。内存写入与 Frida 另算
     * （见 {@link #sandbox}），它们要等目标线程进入安全点。
     */
    private static final int SANDBOX_QUICK_TIMEOUT_MS = 5000;
    private static final int SANDBOX_HEAVY_TIMEOUT_MS = 15000;

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
        return "Inspect a process. scope=sandbox (default) targets an IQ Sandbox guest: "
                + "process_list / process_info / maps / modules / threads / memory_read / memory_write / "
                + "load_library / thread_dump / gc / frida_install / frida_status / frida_load / "
                + "frida_command / frida_events. "
                + "No Root needed — the agent code runs inside the guest process itself. "
                + "frida_install / frida_status act on host private storage and need no running guest. "
                + "scope=host targets a real-phone process and requires Agent Root (read-only subset).";
    }

    @Override
    public PermissionKind permissionKind() {
        return PermissionKind.SYSTEM;
    }

    @Override
    public JSONObject inputSchema() {
        try {
            JSONObject properties = new JSONObject();
            properties.put("scope", ToolSchemas.string(
                    "\"sandbox\" (default) for an IQ Sandbox guest, or \"host\" for a real-phone process (needs Agent Root)."));
            properties.put("action", ToolSchemas.string(
                    "sandbox: process_list, process_info, maps, modules, threads, memory_read, memory_write, "
                            + "load_library, thread_dump, gc, frida_install, frida_status, frida_load, "
                            + "frida_command, frida_events. "
                            + "host: process_list, process_info, maps, modules, threads, thread_dump, signal"));
            properties.put("package", ToolSchemas.string("Guest package (sandbox) or Android package (host) used for PID resolution."));
            properties.put("pid", ToolSchemas.integer("Concrete process id.", 1));
            properties.put("filter", ToolSchemas.string("Optional substring filter for maps/modules."));
            properties.put("address", ToolSchemas.string("Hex virtual address for memory_read/memory_write."));
            properties.put("size", ToolSchemas.integer("Byte count. memory_read is capped at 65536.", 1));
            properties.put("data", ToolSchemas.string("Hex or base64 bytes for memory_write."));
            properties.put("format", ToolSchemas.string("hex (default) or base64."));
            properties.put("path", ToolSchemas.string("Absolute .so path in ZhiCode private storage for sandbox load_library."));
            properties.put("script", ToolSchemas.string("JavaScript body for frida_command (frida_eval semantics)."));
            properties.put("signal", ToolSchemas.integer("Linux signal number to send (host only).", 1));
            properties.put("max_lines", ToolSchemas.integer("Maximum maps lines.", 1));
            properties.put("max_modules", ToolSchemas.integer("Maximum module rows.", 1));
            properties.put("max", ToolSchemas.integer("Maximum Frida rows/matches.", 1));
            properties.put("timeout_ms", ToolSchemas.integer("Frida command timeout.", 1000));
            return ToolSchemas.object(properties, "action");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public ToolExecutionResult execute(SessionConfig config, JSONObject in) throws Exception {
        String scope = in.optString("scope", SCOPE_SANDBOX).trim().toLowerCase(Locale.US);
        String action = in.optString("action", "").trim().toLowerCase(Locale.US);
        if (action.isEmpty()) return ToolExecutionResult.error("Debug action 不能为空");

        if (SCOPE_SANDBOX.equals(scope) || scope.isEmpty()) {
            try {
                return sandbox(action, in);
            } catch (Throwable e) {
                return ToolExecutionResult.error("Debug sandbox/" + action + " 失败: "
                        + e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()));
            }
        }
        if (!SCOPE_HOST.equals(scope)) {
            return ToolExecutionResult.error("未知 Debug scope: " + scope + "（只接受 sandbox / host）");
        }
        try {
            return host(config, action, in);
        } catch (Throwable e) {
            return ToolExecutionResult.error("Debug host/" + action + " 失败: "
                    + e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()));
        }
    }

    // ------------------------------------------------------------ sandbox scope

    /**
     * 沙箱 guest 进程调试。
     *
     * <h3>通道是定向广播，不是 Binder</h3>
     * 调试必须在 **guest 自己的进程**里执行 —— 只有那里才能读它自己的内存、
     * 枚举它自己的模块。宿主（主进程）用一条定向广播唤起它，
     * 结果落在 `filesDir/sandbox/agent-results/<id>.json`，由
     * {@link com.zhizhu.zhicode.sandbox.SandboxAgentBridge#request} 轮询取回。
     *
     * <p>目标进程靠 `target_pid` 匹配：广播每个 guest 都会收到，
     * 但只有 `Process.myPid() == target_pid` 的那个才会动手。
     *
     * <h3>为什么要先解析 PID</h3>
     * 一个包名常有多个 guest 进程（主进程 + `:remote`）。
     * 不指定 PID 就发广播等于让它们**同时**动手，结果文件互相覆盖。
     * 所以这里先查进程列表，取第一个未指定时的候选，并把它回显给模型。
     */
    private ToolExecutionResult sandbox(String action, JSONObject in) throws Exception {
        String pkg = in.optString("package", "").trim();

        /*
         * Frida Gadget 的**安装**不作用于任何 guest 进程 —— 它落在宿主私有目录
         * (`filesDir/sandbox/frida/`)，供之后每个 guest 各自 dlopen。所以它必须走
         * provider（`:iqsandbox`），不能走那条"定向广播给持有 Activity 的 guest"
         * 的通道：没有 guest 在跑时那条通道永远没人应答。
         *
         * 这条 action 原先**根本不存在**，而 `SandboxProcessDebug` /
         * `SandboxFridaBridge` 的报错文案却在教人「先执行 Debug action=frida_install」——
         * 照做只会收到「未知进程调试动作: proc_frida_install」。现在补上。
         */
        if ("frida_install".equals(action) || "frida_status".equals(action)) {
            JSONObject r = com.zhizhu.zhicode.sandbox.SandboxHostClient.call(
                    context, action, new JSONObject());
            if (!r.optBoolean("ok", false)) return ToolExecutionResult.error(sandboxError(r));
            return ToolExecutionResult.ok(r.toString(2));
        }

        if ("process_list".equals(action)) {
            JSONObject payload = new JSONObject();
            if (!pkg.isEmpty()) payload.put("package", pkg);
            JSONObject r = com.zhizhu.zhicode.sandbox.SandboxHostClient.call(context, "process_list", payload);
            if (!r.optBoolean("ok", false)) {
                return ToolExecutionResult.error(sandboxError(r));
            }
            com.zhizhu.zhicode.sandbox.SandboxHostClient.call(context, "status", new JSONObject());
            JSONArray list = r.optJSONArray("processes");
            if (list == null || list.length() == 0) {
                return ToolExecutionResult.ok("沙箱里没有正在运行的 guest 进程"
                        + (pkg.isEmpty() ? "" : "（package=" + pkg + "）")
                        + "；先用 Sandbox action=launch 启动一个。\n" + r.toString(2));
            }
            return ToolExecutionResult.ok(r.toString(2));
        }

        if (pkg.isEmpty()) {
            return ToolExecutionResult.error("sandbox 调试需要 package（用 action=process_list 先查有哪些在跑）");
        }

        String bridgeAction = "proc_" + (action.startsWith("proc_") ? action.substring(5) : action);
        boolean heavy = "proc_memory_write".equals(bridgeAction)
                || "proc_load_library".equals(bridgeAction)
                || bridgeAction.startsWith("proc_frida_");
        int timeout = heavy
                ? Math.max(SANDBOX_HEAVY_TIMEOUT_MS, in.optInt("timeout_ms", 0) + 2000)
                : SANDBOX_QUICK_TIMEOUT_MS;

        int pid = in.optInt("pid", -1);
        if (pid <= 0) {
            pid = resolveSandboxPid(pkg);
            if (pid <= 0) {
                return ToolExecutionResult.error("沙箱里没有 " + pkg + " 正在运行的进程；"
                        + "先 Sandbox action=launch 启动它，再用 action=process_list 确认 PID。");
            }
        }

        JSONObject payload = new JSONObject();
        for (String key : new String[]{"filter", "address", "data", "format", "path", "script", "name", "module",
                "pattern", "protection", "watch_id", "action_name"}) {
            if (in.has(key)) payload.put(key, in.get(key));
        }
        for (String key : new String[]{"size", "max", "max_lines", "max_modules", "timeout_ms", "interval_ms"}) {
            if (in.has(key)) payload.put(key, in.optInt(key));
        }
        payload.put("pid", pid);

        JSONObject r = com.zhizhu.zhicode.sandbox.SandboxAgentBridge.request(
                context, bridgeAction, pkg, pid, payload, timeout);
        if (!r.optBoolean("ok", false)) {
            return ToolExecutionResult.error(sandboxError(r));
        }
        return ToolExecutionResult.ok(r.toString(2));
    }

    /** 从引擎的进程列表里取该包名的第一个 PID。0 表示没有在跑。 */
    private int resolveSandboxPid(String pkg) throws Exception {
        JSONObject payload = new JSONObject().put("package", pkg);
        JSONObject r = com.zhizhu.zhicode.sandbox.SandboxHostClient.call(context, "process_list", payload);
        JSONArray list = r.optJSONArray("processes");
        if (list == null) return 0;
        for (int i = 0; i < list.length(); i++) {
            JSONObject row = list.optJSONObject(i);
            if (row == null) continue;
            if (!pkg.equals(row.optString("package", ""))) continue;
            int pid = row.optInt("pid", 0);
            if (pid > 0) return pid;
        }
        return 0;
    }

    /**
     * 沙箱侧失败文案：把引擎带回来的 `hint` / `stage` 一起说。
     *
     * <p>这些字段是给**模型**看的，不是给人看的日志：它们在提醒下一步该做什么，
     * 而不是"哪里出错了"。
     */
    private static String sandboxError(JSONObject r) {
        String error = r.optString("error", "");
        if (error.isEmpty()) error = r.toString();
        StringBuilder b = new StringBuilder(error);
        String stage = r.optString("stage", "");
        if (!stage.isEmpty()) b.append("\n阶段: ").append(stage);
        String hint = r.optString("hint", "");
        if (!hint.isEmpty()) b.append("\n建议: ").append(hint);
        return b.toString();
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
            return ToolExecutionResult.error("真机 raw memory/远程库注入不由 Debug 自动执行。该能力仅对沙箱 Guest 内置开放；如确实要调试自有真机进程，请显式使用 Root + 你安装的 lldb/gdb/frida 工具链。");
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
