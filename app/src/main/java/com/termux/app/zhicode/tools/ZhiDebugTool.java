package com.termux.app.zhicode.tools;

import android.content.Context;

import com.termux.app.zhicode.json.JsonItems;
import com.termux.app.zhicode.model.SessionConfig;
import com.termux.app.zhicode.model.ToolExecutionResult;
import com.termux.app.zhicode.termux.TermuxShellExecutor;
import com.termux.shared.termux.TermuxConstants;
import com.zhizhu.zhicode.sandbox.FridaEnv;
import com.zhizhu.zhicode.sandbox.SandboxConsole;
import com.zhizhu.zhicode.sandbox.SandboxGuestHost;
import com.zhizhu.zhicode.sandbox.SandboxRpc;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Agent 用来调试蜘蛛沙箱进程的工具，附带一条需要 Root 的真机路径。
 *
 * <h3>两个 scope，两条完全不同的权限路径</h3>
 * <ul>
 *   <li>{@code sandbox}（默认）：一切都在虚拟运行时内部完成。原始内存读写只走
 *       guest 进程内的 Frida 通道；加载 .so 只允许本应用私有目录。
 *       不需要 Root，也不需要 ptrace。</li>
 *   <li>{@code host}：真机上的进程。需要用户在设置里显式开启 Agent Root，
 *       且<b>不提供</b>原始内存读写与库注入——那两件事在本工具里只对沙箱开放。
 *       要在真机上做同样的事，请用你自己装的 lldb/gdb/frida 工具链。</li>
 * </ul>
 *
 * <h3>为什么 sandbox 里必须先解析 PID</h3>
 * 沙箱里的一个包可能同时有多个 guest 进程。动作要落到哪一个上，
 * 取决于调用者给的 pid，或者包名对应的进程名，或者第一个活着的进程。
 * 调用者给的 pid 会被核对是否真属于这个包——不核对的话，
 * 「PID 写错」会变成「对另一个应用的内存执行写入」。
 */
public final class ZhiDebugTool implements ZhiTool {

    private static final String SCOPE_SANDBOX = "sandbox";
    private static final String SCOPE_HOST = "host";

    // ------------------------------------------------------------ 沙箱 scope

    /** {@code ok:true} 的沙箱动作名 → 宿主桥的 proc_* 动作名。 */
    private static final Map<String, String> BRIDGE_ACTIONS;

    /** 统一转发到进程内 Frida 通道的那些动作。 */
    private static final String[] FRIDA_ACTIONS = {
            "frida_modules", "frida_ranges", "frida_read", "frida_write", "frida_scan",
            "frida_protect", "frida_patch", "frida_export", "frida_watch",
            "frida_watch_stop", "frida_watch_stop_all", "frida_eval", "frida_detach_all"
    };

    /** 动作名 → Frida 脚本侧的 op 名。 */
    private static final Map<String, String> FRIDA_OPS;

    static {
        Map<String, String> bridge = new LinkedHashMap<>();
        bridge.put("process_info", "proc_info");
        bridge.put("maps", "proc_maps");
        bridge.put("modules", "proc_modules");
        bridge.put("threads", "proc_threads");
        bridge.put("memory_read", "proc_memory_read");
        bridge.put("memory_write", "proc_memory_write");
        bridge.put("load_library", "proc_load_library");
        bridge.put("thread_dump", "proc_thread_dump");
        bridge.put("gc", "proc_gc");
        bridge.put("frida_status", "proc_frida_status");
        bridge.put("frida_load", "proc_frida_load");
        bridge.put("frida_events", "proc_frida_events");
        for (String frida : FRIDA_ACTIONS) bridge.put(frida, "proc_frida_command");
        BRIDGE_ACTIONS = Collections.unmodifiableMap(bridge);

        Map<String, String> ops = new LinkedHashMap<>();
        ops.put("frida_modules", "modules");
        ops.put("frida_ranges", "ranges");
        ops.put("frida_read", "read");
        ops.put("frida_write", "write");
        ops.put("frida_scan", "scan");
        ops.put("frida_protect", "protect");
        ops.put("frida_patch", "patch");
        ops.put("frida_export", "export");
        ops.put("frida_watch", "watch_start");
        ops.put("frida_watch_stop", "watch_stop");
        ops.put("frida_watch_stop_all", "watch_stop_all");
        ops.put("frida_eval", "eval");
        ops.put("frida_detach_all", "hook_detach_all");
        FRIDA_OPS = Collections.unmodifiableMap(ops);
    }

    /** 允许透传给 guest 自省层的字段。白名单而不是全量转发：调用者的输入不该无约束地到达那里。 */
    private static final String[] PROC_PAYLOAD_KEYS = {
            "filter", "address", "size", "data", "format", "path",
            "max_lines", "max_modules", "max_chars", "timeout_ms"
    };

    /** 允许透传给 Frida 脚本的字段。 */
    private static final String[] FRIDA_PAYLOAD_KEYS = {
            "filter", "address", "size", "data", "pattern", "module", "protection",
            "name", "script", "max", "chunk_size", "coalesce", "volatile",
            "watch_id", "interval_ms"
    };

    /** 长任务（写入、注入、Frida）允许的最小请求超时；其余沙箱动作用短超时。 */
    private static final int SHORT_REQUEST_TIMEOUT_MS = 5000;
    private static final int LONG_REQUEST_TIMEOUT_MS = 8000;

    /** Frida 命令的默认与最长运行时长。扫描与 eval 天然更慢，给更宽的上限。 */
    private static final int FRIDA_DEFAULT_TIMEOUT_MS = 12000;
    private static final int FRIDA_SLOW_TIMEOUT_MS = 60000;
    private static final int FRIDA_MAX_TIMEOUT_MS = 118000;

    /**
     * Java 侧等待时长比脚本侧自己的截止多留一点。
     *
     * <p>脚本到点会返回一个结构化的「已超时」结果，那比 Java 直接放弃有用得多。
     * 若两边取同一个值，Java 几乎总是先超时，于是永远看不到那份结构化结果。
     */
    private static final int FRIDA_TIMEOUT_HEADROOM_MS = 1500;
    private static final int FRIDA_COMMAND_MAX_TIMEOUT_MS = 120000;

    /** 真机侧命令的超时。 */
    private static final int HOST_COMMAND_TIMEOUT_MS = 20000;
    private static final int HOST_PIDOF_TIMEOUT_MS = 10000;

    /** 沙箱动作里的 {@code frida_auto_attach} 在改完策略后会顺手对活着的 guest 生效。 */
    private static final int LIVE_ATTACH_TIMEOUT_MS = 15000;

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
        return "Inspect/debug 蜘蛛沙箱 processes via Frida; scope=host requires Root.";
    }

    @Override
    public PermissionKind permissionKind() {
        return PermissionKind.SYSTEM;
    }

    @Override
    public JSONObject inputSchema() {
        try {
            JSONObject properties = new JSONObject();
            properties.put("scope", ToolSchemas.string("sandbox (default) or host."));
            properties.put("action", ToolSchemas.string("process_list, process_info, maps, modules, threads, memory_read, memory_write, load_library, thread_dump, gc, signal, frida_runtime_status, frida_install, frida_auto_attach, frida_status, frida_load, frida_modules, frida_ranges, frida_read, frida_write, frida_scan, frida_protect, frida_patch, frida_export, frida_watch, frida_watch_stop, frida_watch_stop_all, frida_eval, frida_events, frida_detach_all"));
            properties.put("package", ToolSchemas.string("Guest package for sandbox scope, or Android package for host PID resolution."));
            properties.put("pid", ToolSchemas.integer("Concrete process id. For sandbox this must be one of the package's 蜘蛛沙箱 guest PIDs.", 1));
            properties.put("filter", ToolSchemas.string("Optional substring filter for maps/modules."));
            properties.put("address", ToolSchemas.string("Hex virtual address. frida_scan uses address together with size when module is omitted."));
            properties.put("size", ToolSchemas.integer("Byte count. memory_read is capped at 65536; frida_scan accepts a larger scan window split across current readable mappings.", 1));
            properties.put("data", ToolSchemas.string("Hex or base64 bytes for memory_write."));
            properties.put("format", ToolSchemas.string("hex (default) or base64."));
            properties.put("path", ToolSchemas.string("Absolute .so path in ZhiCode private storage for sandbox load_library."));
            properties.put("signal", ToolSchemas.integer("Linux signal number for rooted host signal action.", 1));
            properties.put("max_lines", ToolSchemas.integer("Maximum maps lines.", 1));
            properties.put("max_modules", ToolSchemas.integer("Maximum module rows.", 1));
            properties.put("pattern", ToolSchemas.string("Frida memory scan pattern, e.g. 13 37 ?? ff. Scans only current readable mappings and may return partial matches with complete=false and errors."));
            properties.put("module", ToolSchemas.string("Frida module name used for module-scoped scan/export. For frida_scan, module is mutually exclusive with address/size."));
            properties.put("protection", ToolSchemas.string("Frida memory protection such as r--, rw-, r-x or rwx."));
            properties.put("name", ToolSchemas.string("Export/symbol name for frida_export."));
            properties.put("script", ToolSchemas.string("JavaScript body for frida_eval inside the selected 蜘蛛沙箱 Guest. Memory.scanSync is translated to a bounded async scan; prefer Debug frida_scan or await Zhi.scan(options) Zhi.emit(value) appends events; Zhi.hooks retains Interceptor handles."));
            properties.put("max", ToolSchemas.integer("Maximum Frida rows/matches. frida_scan is hard-capped at 2048; default 256 to prevent hit explosion.", 1));
            properties.put("chunk_size", ToolSchemas.integer("frida_scan chunk bytes. Runtime clamps to 64 KiB..8 MiB; default 4 MiB so long scans yield between chunks.", 65536));
            properties.put("max_chars", ToolSchemas.integer("Maximum Frida event log characters.", 1));
            properties.put("timeout_ms", ToolSchemas.integer("Frida command timeout in milliseconds.", 1000));
            properties.put("enabled", ToolSchemas.bool("Enable/disable Frida auto-attach before Guest Application.onCreate for frida_auto_attach."));
            properties.put("volatile", ToolSchemas.bool("For frida_read/frida_write. Defaults true and uses Frida volatile access for safer live-process memory I/O."));
            properties.put("watch_id", ToolSchemas.string("Stable identifier for frida_watch/frida_watch_stop."));
            properties.put("interval_ms", ToolSchemas.integer("Memory watch polling interval; 20-60000 ms.", 20));
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
        try {
            if (SCOPE_HOST.equals(scope)) return host(config, action, in);
            if (!SCOPE_SANDBOX.equals(scope) && !scope.isEmpty()) {
                return ToolExecutionResult.error("未知 Debug scope: " + scope);
            }
            return sandbox(action, in);
        } catch (Throwable e) {
            SandboxConsole.event("Debug 失败: " + scope + "/" + action + " / " + e);
            return ToolExecutionResult.error("Debug " + scope + "/" + action + " 失败: "
                    + e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()));
        }
    }

    // ---------------------------------------------------------- 沙箱 scope

    /**
     * 沙箱 scope 的分发。
     *
     * <p>前三个动作不需要目标包（它们操作的是 Frida 运行环境本身而非某个 guest），
     * 因此要在「必须给 package」这条校验之前处理掉。
     */
    private ToolExecutionResult sandbox(String action, JSONObject in) throws Exception {
        if ("process_list".equals(action)) return ToolExecutionResult.ok(sandboxProcessList(in.optString("package", "")).toString(2));
        if ("frida_runtime_status".equals(action)) return ToolExecutionResult.ok(FridaEnv.status(context).toString(2));
        if ("frida_install".equals(action)) {
            return ToolExecutionResult.ok(FridaEnv.install(context, shell, TermuxConstants.TERMUX_HOME_DIR_PATH).toString(2));
        }

        String pkg = in.optString("package", "").trim();
        if ("frida_auto_attach".equals(action)) return autoAttach(pkg, in);
        if (pkg.isEmpty()) return ToolExecutionResult.error("sandbox 调试需要 package；先启动 Guest 或传入 package");

        int pid = resolveSandboxPid(pkg, in.optInt("pid", -1));
        String bridgeAction = BRIDGE_ACTIONS.get(action);
        if (bridgeAction == null) {
            if ("signal".equals(action)) {
                return ToolExecutionResult.error("sandbox signal 请使用 Sandbox stop；原始 signal 不直接开放，避免误杀整个蜘蛛虚拟进程池");
            }
            return ToolExecutionResult.error("未知 sandbox Debug action: " + action);
        }

        JSONObject payload = selectFields(in, PROC_PAYLOAD_KEYS);
        payload.put("pid", pid);
        int requestTimeoutMs = SHORT_REQUEST_TIMEOUT_MS;
        if ("proc_frida_command".equals(bridgeAction)) {
            int commandTimeoutMs = prepareFridaCommand(action, in, payload);
            requestTimeoutMs = Math.max(LONG_REQUEST_TIMEOUT_MS, commandTimeoutMs + FRIDA_TIMEOUT_HEADROOM_MS);
        } else if ("memory_write".equals(action) || "load_library".equals(action)) {
            requestTimeoutMs = LONG_REQUEST_TIMEOUT_MS;
        }

        JSONObject response = SandboxGuestHost.request(context, bridgeAction, pkg, pid, payload, requestTimeoutMs);
        return response.optBoolean("ok", false)
                ? ToolExecutionResult.ok(response.toString(2))
                : ToolExecutionResult.error(response.optString("error", response.toString()));
    }

    /**
     * 组装一条 Frida 命令的两层超时：脚本侧自己的截止，以及 Java 侧的等待上限。
     *
     * @return Java 侧应当使用的命令超时
     */
    private static int prepareFridaCommand(String action, JSONObject in, JSONObject payload) throws Exception {
        boolean slow = "frida_scan".equals(action) || "frida_eval".equals(action);
        int requested = in.optInt("timeout_ms", slow ? FRIDA_SLOW_TIMEOUT_MS : FRIDA_DEFAULT_TIMEOUT_MS);
        int runtimeTimeoutMs = Math.min(FRIDA_MAX_TIMEOUT_MS, Math.max(1000, Math.min(FRIDA_COMMAND_MAX_TIMEOUT_MS, requested)));
        int commandTimeoutMs = Math.min(FRIDA_COMMAND_MAX_TIMEOUT_MS, runtimeTimeoutMs + FRIDA_TIMEOUT_HEADROOM_MS);

        JSONObject fridaPayload = selectFields(in, FRIDA_PAYLOAD_KEYS);
        fridaPayload.put("timeout_ms", runtimeTimeoutMs);
        payload.put("op", FRIDA_OPS.get(action));
        payload.put("frida_payload", fridaPayload);
        payload.put("timeout_ms", commandTimeoutMs);
        return commandTimeoutMs;
    }

    /**
     * 打开/关闭某个包的 auto-attach。
     *
     * <p>策略改完只是让<b>下次</b>启动生效。如果该包此刻正有 guest 在跑，
     * 顺手尝试一次即时加载，免得用户以为开关没反应。这一步失败不算错误——
     * 进程可能刚好在退出——把结果放进 {@code live_load} 里如实说明即可。
     */
    private ToolExecutionResult autoAttach(String pkg, JSONObject in) throws Exception {
        if (pkg.isEmpty()) return ToolExecutionResult.error("frida_auto_attach 需要 package");
        boolean enabled = in.optBoolean("enabled", true);
        JSONObject state = FridaEnv.setAutoAttach(context, pkg, enabled);
        if (enabled && FridaEnv.isInstalled(context)) {
            try {
                int livePid = resolveSandboxPid(pkg, in.optInt("pid", -1));
                JSONObject live = SandboxGuestHost.request(context, "proc_frida_load", pkg, livePid,
                        new JSONObject().put("pid", livePid), LIVE_ATTACH_TIMEOUT_MS);
                state.put("live_load", live);
            } catch (Throwable e) {
                state.put("live_load", "deferred: " + e.getMessage());
            }
        }
        return ToolExecutionResult.ok(state.toString(2));
    }

    private JSONArray sandboxProcessList(String packageFilter) throws Exception {
        JSONObject payload = new JSONObject();
        if (packageFilter != null && !packageFilter.trim().isEmpty()) payload.put("package", packageFilter.trim());
        JSONObject response = SandboxRpc.call(context, "process_list", payload);
        if (!response.optBoolean("ok", false)) {
            throw new IllegalStateException(response.optString("error", response.toString()));
        }
        JSONArray processes = response.optJSONArray("processes");
        return processes == null ? new JSONArray() : processes;
    }

    /**
     * 决定动作落到哪个 guest 进程上。
     *
     * <p>三种来源按可靠性排序：调用者显式给的 pid（必须核对归属）、
     * 包名对应的那个进程、以及第一个活着的进程。
     * 显式 pid 之所以要核对，是因为写错一个数字的后果是「操作了另一个应用的内存」，
     * 而这条错误路径上没有任何东西会替我们拦住它。
     */
    private int resolveSandboxPid(String pkg, int requested) throws Exception {
        JSONArray processes = sandboxProcessList(pkg);
        if (processes.length() == 0) throw new IllegalStateException("沙箱应用当前没有运行进程: " + pkg);

        if (requested > 0) {
            for (JSONObject process : JsonItems.of(processes)) {
                if (process.optInt("pid", -1) == requested) return requested;
            }
            throw new SecurityException("PID " + requested + " 不属于当前 蜘蛛沙箱 包 " + pkg);
        }
        for (JSONObject process : JsonItems.of(processes)) {
            if (pkg.equals(process.optString("process", ""))) return process.optInt("pid", -1);
        }
        return processes.optJSONObject(0).optInt("pid", -1);
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
            return ToolExecutionResult.error("host 进程调试需要先在蜘蛛设置中开启 Agent Root");
        }
        if ("memory_read".equals(action) || "memory_write".equals(action) || "load_library".equals(action)) {
            return ToolExecutionResult.error("真机 raw memory/远程库注入不由 Debug 自动执行。该能力仅对 蜘蛛沙箱 Guest 内置开放；如确实要调试自有真机进程，请显式使用 Root + 你安装的 lldb/gdb/frida 工具链。");
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
