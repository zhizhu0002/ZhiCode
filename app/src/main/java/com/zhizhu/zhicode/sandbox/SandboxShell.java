package com.zhizhu.zhicode.sandbox;

import android.content.Context;

import com.termux.app.zhicode.model.SessionConfig;
import com.termux.app.zhicode.model.ToolExecutionResult;
import com.termux.app.zhicode.storage.ApiSettingsStore;
import com.termux.app.zhicode.termux.TermuxShellExecutor;
import com.termux.app.zhicode.tools.ZhiDebugTool;
import com.termux.app.zhicode.tools.ZhiSandboxTool;
import com.termux.shared.termux.TermuxConstants;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 内置 Termux 与沙箱之间的同 UID 文件桥。
 *
 * <p>让 {@code zhisandbox} / {@code zhidebug} 两条命令在 Termux 里直接调用沙箱与调试后端，
 * 与 Agent 走同一套实现、同一份状态。不需要 adb、不导出任何 Android 组件、不开网络端口、
 * 不需要 root —— 只是同一 UID 下的两个目录。
 *
 * <h3>协议</h3>
 * <ul>
 *   <li><b>请求</b> {@code requests/<id>.req}：单行 JSON
 *       {@code {"tool":"Sandbox|Debug","action":"...","target":"...","payload":{...}}}。
 *       用 JSON 而非多行文本，是因为 payload 本身是 JSON、可能含换行，按行切分会把它切坏。</li>
 *   <li><b>响应</b> {@code responses/<id>.res}：第一行是退出码（0 成功），其余为原始输出。
 *       不做转义，CLI 只要 {@code head -n1} 取码、{@code tail -n+2} 取正文。</li>
 * </ul>
 * 两侧都以「写临时文件再 rename」提交，避免读到写了一半的内容。
 */
public final class SandboxShell {

    private static final String BRIDGE_DIR = "sandbox/termux-bridge";
    private static final String REQUEST_SUFFIX = ".req";
    private static final String RESPONSE_SUFFIX = ".res";
    /** 有活儿时缩短轮询间隔，空闲时放宽，兼顾延迟与耗电。 */
    private static final long POLL_BUSY_MS = 15L;
    private static final long POLL_IDLE_MS = 60L;
    private static final long POLL_ERROR_MS = 250L;
    /** CLI 等待响应的上限：240 × 50ms = 12 秒。 */
    private static final int CLI_WAIT_TICKS = 240;

    private static final AtomicBoolean STARTED = new AtomicBoolean();
    private static volatile Context appContext;

    private SandboxShell() {}

    /** 主进程启动时调用一次；非主进程直接返回。 */
    public static void start(Context context) {
        appContext = context.getApplicationContext();
        if (!isMainProcess(appContext)) return;
        ensureDirectories(appContext);
        ensureCliInstalled(appContext);
        if (!STARTED.compareAndSet(false, true)) return;
        Thread worker = new Thread(SandboxShell::serve, "zhi-termux-sandbox-bridge");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * 把 {@code zhisandbox} / {@code zhidebug} 写进内置 Termux 的 bin。
     *
     * <p>内容一致就只补执行位，不重写文件，避免每次调用都动时间戳。
     */
    public static void ensureCliInstalled(Context context) {
        try {
            File bin = new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH);
            if (!bin.isDirectory()) return;
            writeCommand(new File(bin, "zhisandbox"), "Sandbox");
            writeCommand(new File(bin, "zhidebug"), "Debug");
        } catch (Throwable error) {
            SandboxConsole.event("Termux 沙箱命令安装失败: " + error);
        }
    }

    /** bridge 目录。Termux 侧可用 ZHICODE_SANDBOX_BRIDGE_DIR 覆盖。 */
    public static String bridgeDir(Context context) {
        return new File(context.getFilesDir(), BRIDGE_DIR).getAbsolutePath();
    }

    // ------------------------------------------------------------------ 服务循环

    private static void serve() {
        while (true) {
            long sleepMs = POLL_IDLE_MS;
            try {
                Context context = appContext;
                if (context == null) {
                    Thread.sleep(100L);
                    continue;
                }
                ensureDirectories(context);
                ensureCliInstalled(context);
                File[] pending = listRequests(context);
                if (pending.length > 0) {
                    // 按修改时间升序：先到先服务
                    Arrays.sort(pending, Comparator.comparingLong(File::lastModified));
                    for (File request : pending) handle(context, request);
                    sleepMs = POLL_BUSY_MS;
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            } catch (Throwable error) {
                SandboxConsole.event("Termux 沙箱桥异常: " + error);
                sleepMs = POLL_ERROR_MS;
            }
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static File[] listRequests(Context context) {
        File[] files = new File(bridgeDir(context), "requests")
                .listFiles((dir, name) -> name.endsWith(REQUEST_SUFFIX));
        return files == null ? new File[0] : files;
    }

    private static void handle(Context context, File request) {
        File response = new File(new File(bridgeDir(context), "responses"), responseName(request));
        try {
            JSONObject envelope = new JSONObject(readText(request));
            String tool = envelope.optString("tool", "Sandbox");
            JSONObject input = envelope.optJSONObject("payload");
            if (input == null) input = new JSONObject();
            input.put("action", envelope.optString("action", "status"));

            String target = envelope.optString("target", "").trim();
            if (!target.isEmpty()) {
                // Debug 的目标可以是 PID，Sandbox 的目标是包名
                if (isDebug(tool) && target.matches("[0-9]+")) {
                    input.put("pid", Integer.parseInt(target));
                } else {
                    input.put("package", target);
                }
            }

            ToolExecutionResult result = execute(context, tool, input);
            writeResponse(response, result.isError ? 1 : 0, result.content);
        } catch (Throwable error) {
            writeResponse(response, 1, error.getClass().getSimpleName() + ": " + error.getMessage());
        } finally {
            // 无论成败都要删掉请求，否则会被反复重放
            if (!request.delete()) request.deleteOnExit();
        }
    }

    /** 两个工具类的 execute 都声明了 throws Exception，调用方（{@link #handle}）统一兜住。 */
    private static ToolExecutionResult execute(Context context, String tool, JSONObject input)
            throws Exception {
        if (isDebug(tool)) {
            // 复用 Agent 持久化的 Root 开关：终端与 Agent 必须是同一套权限模型，
            // 否则同一个用户在两个入口会拿到不同的能力边界。
            SessionConfig config = new ApiSettingsStore(context).load();
            if (config.projectDirectory == null || config.projectDirectory.trim().isEmpty()) {
                config.projectDirectory = TermuxConstants.TERMUX_HOME_DIR_PATH;
            }
            return new ZhiDebugTool(context, new TermuxShellExecutor(context)).execute(config, input);
        }
        return new ZhiSandboxTool(context).execute(new SessionConfig(), input);
    }

    private static boolean isDebug(String tool) {
        return "Debug".equalsIgnoreCase(tool);
    }

    private static String responseName(File request) {
        String name = request.getName();
        String id = name.endsWith(REQUEST_SUFFIX)
                ? name.substring(0, name.length() - REQUEST_SUFFIX.length())
                : name;
        return id + RESPONSE_SUFFIX;
    }

    // ------------------------------------------------------------------ 文件读写

    private static boolean isMainProcess(Context context) {
        try {
            String cmdline = readText(new File("/proc/self/cmdline")).replace('\0', ' ').trim();
            return context.getPackageName().equals(cmdline);
        } catch (Throwable ignored) {
            // 读不到就认为不是主进程：宁可少一条桥，也不要在 guest 进程里起重复的服务线程
            return false;
        }
    }

    private static void ensureDirectories(Context context) {
        new File(bridgeDir(context), "requests").mkdirs();
        new File(bridgeDir(context), "responses").mkdirs();
    }

    /** 先写临时文件再 rename，避免 CLI 读到半截内容。 */
    private static void writeResponse(File destination, int code, String body) {
        String text = Integer.toString(code) + "\n" + (body == null ? "" : body);
        File temporary = new File(destination.getParentFile(), destination.getName() + ".tmp");
        try {
            try (FileOutputStream out = new FileOutputStream(temporary)) {
                out.write(text.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
            if (!temporary.renameTo(destination)) {
                try (FileOutputStream out = new FileOutputStream(destination)) {
                    out.write(text.getBytes(StandardCharsets.UTF_8));
                }
                temporary.delete();
            }
        } catch (Throwable ignored) {
            // 写不出去时 CLI 会走超时分支，无需额外处理
        }
    }

    private static String readText(File file) throws Exception {
        try (FileInputStream in = new FileInputStream(file);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    // ------------------------------------------------------------------ CLI 生成

    private static void writeCommand(File file, String tool) throws Exception {
        String script = commandScript(tool);
        if (file.isFile()) {
            try {
                if (readText(file).equals(script)) {
                    file.setExecutable(true, false);
                    return;
                }
            } catch (Throwable ignored) {
                // 读不到就当需要重写
            }
        }
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(script.getBytes(StandardCharsets.UTF_8));
        }
        file.setReadable(true, false);
        file.setWritable(true, true);
        file.setExecutable(true, false);
    }

    /**
     * 生成一条 CLI。用法：{@code <命令> [action] [target] [payload-json]}
     *
     * <p>action / target 会先剥掉引号与反斜杠 —— 它们要嵌进 JSON，
     * 不能让调用方经这两个参数构造出畸形 JSON。payload 由调用方直接提供，须是合法 JSON 对象。
     */
    private static String commandScript(String tool) {
        String root = TermuxConstants.TERMUX_FILES_DIR_PATH + "/" + BRIDGE_DIR;
        String defaultAction = isDebug(tool) ? "process_list" : "status";
        return "#!" + TermuxConstants.TERMUX_BASH_PATH + "\n"
                + "set -u\n"
                + "TOOL='" + tool + "'\n"
                + "ROOT=\"${ZHICODE_SANDBOX_BRIDGE_DIR:-" + root + "}\"\n"
                + "ACTION=\"${1:-" + defaultAction + "}\"\n"
                + "TARGET=\"${2:-}\"\n"
                + "PAYLOAD=\"${3:-{}}\"\n"
                + "ACTION=$(printf '%s' \"$ACTION\" | tr -d '\"\\\\')\n"
                + "TARGET=$(printf '%s' \"$TARGET\" | tr -d '\"\\\\')\n"
                + "ID=\"$(date +%s 2>/dev/null)-$$-${RANDOM:-0}\"\n"
                + "REQ=\"$ROOT/requests/$ID.req\"\n"
                + "RES=\"$ROOT/responses/$ID.res\"\n"
                + "mkdir -p \"$ROOT/requests\" \"$ROOT/responses\"\n"
                + "TMP=\"$REQ.tmp.$$\"\n"
                + "printf '{\"tool\":\"%s\",\"action\":\"%s\",\"target\":\"%s\",\"payload\":%s}' "
                + "\"$TOOL\" \"$ACTION\" \"$TARGET\" \"$PAYLOAD\" >\"$TMP\" && mv \"$TMP\" \"$REQ\"\n"
                + "i=0\n"
                + "while [ $i -lt " + CLI_WAIT_TICKS + " ]; do\n"
                + "  if [ -f \"$RES\" ]; then\n"
                + "    CODE=$(head -n 1 \"$RES\" 2>/dev/null || echo 1)\n"
                + "    tail -n +2 \"$RES\" 2>/dev/null\n"
                + "    rm -f \"$RES\"\n"
                + "    case \"$CODE\" in 0) exit 0;; *) exit 1;; esac\n"
                + "  fi\n"
                + "  sleep 0.05\n"
                + "  i=$((i+1))\n"
                + "done\n"
                + "rm -f \"$REQ\" \"$RES\"\n"
                + "echo 'ZhiCode 沙箱 bridge timeout' >&2\n"
                + "exit 124\n";
    }
}
