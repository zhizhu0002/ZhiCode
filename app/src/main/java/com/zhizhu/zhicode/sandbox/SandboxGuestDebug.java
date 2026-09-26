package com.zhizhu.zhicode.sandbox;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.Process;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import top.niunaijun.blackbox.app.BActivityThread;

/**
 * 蜘蛛沙箱 guest 进程内的自省与调试后端。
 *
 * <p>本类只读 {@code /proc/self}，只操作「当前虚拟进程自己」的内存与已加载镜像。
 * 主 Agent 先在宿主侧选定一个具体的沙箱 PID（见 {@link SandboxGuestHost}），
 * 广播落到那个进程里，再由该进程对自己执行动作。这条约束把原始内存/注入能力
 * 关在蜘蛛沙箱内部，而不是让蜘蛛变成一台全系统注入器。
 *
 * <p>设计上的三条硬边界，任何改动都不应放宽：
 * <ol>
 *   <li><b>不直接碰内存</b>：读写都绕到进程内的 Frida 通道；
 *       {@code /proc/self/mem} 与 {@code Unsafe} 都被刻意弃用（前者在 Android 15 上可能 EACCES，
 *       后者会在过期地址上直接硬崩，而不是抛一个可捕获的异常）。</li>
 *   <li><b>写入有上限，且目标页必须已映射为可写</b>。</li>
 *   <li><b>.so 注入只允许本应用私有目录内的文件</b>，路径白名单由运行时包名派生，不写死。</li>
 * </ol>
 *
 * <p>本类的协议面（动作名、响应键名、上限数值）与宿主侧 {@link SandboxGuestHost}
 * 及 {@code ZhiDebugTool} 严格对齐，重写内部结构时这些串不能动。
 */
public final class SandboxGuestDebug {

    /** maps 文本输出的字节上限（防止一次请求把整个地址空间文本拖进内存）。 */
    private static final int MAX_MAP_TEXT = 512_000;
    /** 单次内存读写允许的字节数。 */
    private static final int MAX_WRITE_BYTES = 65_536;
    /** maps 行数上限（对外参数 max_lines 的钳位上界）。 */
    private static final int MAX_MAP_LINES = 6_000;
    /** modules 条目上限（对外参数 max_modules 的钳位上界）。 */
    private static final int MAX_MODULES = 1_200;
    /** 线程栈导出的最大帧数。 */
    private static final int MAX_STACK_DEPTH = 96;
    /** memory_read 未显式给 size 时的默认读取长度。 */
    private static final int DEFAULT_READ_SIZE = 64;
    /** 进程内 Frida 单次内存操作的超时。 */
    private static final int FRIDA_TIMEOUT_MS = 12_000;

    private static final String PROC_MAPS = "/proc/self/maps";
    private static final String PROC_TASK = "/proc/self/task";

    /**
     * 刻意<b>不</b>使用的原始通道。留成常量而不是散落各处，是为了让「为什么不用它」
     * 只有一个落点；出现在报错文案里，方便使用者确定该先装 Frida 而不是怀疑权限。
     */
    private static final String RAW_MEM_CHANNEL = "/proc/self/mem";

    private SandboxGuestDebug() {}

    // ------------------------------------------------------------------ 分发

    /**
     * 在一个已选定的 guest 进程内执行一次调试动作。
     *
     * <p>异常直接往上抛：调用方（{@link SandboxGuestHost}）负责把 Throwable 折成
     * {@code {"ok":false,...}} 信封，因此这里不做吞异常的兜底，避免把失败伪装成成功。
     */
    public static JSONObject dispatch(Context context, String action, JSONObject args) throws Exception {
        final JSONObject out = envelope();
        final String op = action == null ? "" : action;
        final JSONObject p = args == null ? new JSONObject() : args;
        switch (op) {
            case "proc_info":
                out.put("info", info(context));
                break;
            case "proc_maps":
                out.put("maps", maps(p.optString("filter", ""),
                        clamp(p.optInt("max_lines", 1600), 1, MAX_MAP_LINES)));
                break;
            case "proc_modules":
                out.put("modules", modules(p.optString("filter", ""),
                        clamp(p.optInt("max_modules", 400), 1, MAX_MODULES)));
                break;
            case "proc_threads":
                out.put("threads", threads());
                break;
            case "proc_memory_read":
                out.put("memory", memoryRead(context,
                        p.optString("address", ""),
                        clamp(p.optInt("size", DEFAULT_READ_SIZE), 1, MAX_WRITE_BYTES),
                        p.optString("format", "hex")));
                break;
            case "proc_memory_write":
                out.put("memory", memoryWrite(context,
                        p.optString("address", ""),
                        p.optString("data", ""),
                        p.optString("format", "hex")));
                break;
            case "proc_load_library":
                out.put("injection", loadLibrary(context, p.optString("path", "")));
                break;
            case "proc_thread_dump":
                out.put("thread_dump", threadDump());
                break;
            case "proc_gc":
                Runtime.getRuntime().gc();
                out.put("message", "GC 已请求");
                break;
            case "proc_frida_status":
                out.put("frida", SandboxFrida.status(context));
                break;
            case "proc_frida_load":
                out.put("frida", SandboxFrida.load(context));
                break;
            case "proc_frida_command":
                out.put("frida", SandboxFrida.command(context,
                        p.optString("op", ""),
                        p.optJSONObject("frida_payload"),
                        clamp(p.optInt("timeout_ms", 10_000), 1_000, 120_000)));
                break;
            case "proc_frida_events":
                out.put("events", SandboxFrida.events(clamp(p.optInt("max_chars", 200_000), 1_024, 1_000_000)));
                break;
            default:
                out.put("ok", false).put("error", "未知进程调试动作: " + op);
        }
        return out;
    }

    /** 每个响应的公共头：是谁、属于哪个虚拟包与虚拟进程。 */
    private static JSONObject envelope() throws Exception {
        return new JSONObject()
                .put("ok", true)
                .put("pid", Process.myPid())
                .put("uid", Process.myUid())
                .put("package", safe(BActivityThread.getAppPackageName()))
                .put("process", safe(BActivityThread.getAppProcessName()));
    }

    // ------------------------------------------------------------- 进程与线程

    private static JSONObject info(Context context) throws Exception {
        JSONObject o = new JSONObject()
                .put("pid", Process.myPid())
                .put("uid", Process.myUid())
                .put("package", safe(BActivityThread.getAppPackageName()))
                .put("process", safe(BActivityThread.getAppProcessName()))
                .put("sdk", Build.VERSION.SDK_INT)
                .put("abi", firstAbi())
                .put("cmdline", Proc.read("/proc/self/cmdline", 8_192).replace('\0', ' ').trim())
                .put("status", Proc.read("/proc/self/status", 32_768));
        try {
            o.put("native_library_dir", context.getApplicationInfo().nativeLibraryDir);
        } catch (Throwable ignored) {
            // 虚拟运行时下 ApplicationInfo 可能尚未完全填充，缺这一项不影响其它字段。
        }
        return o;
    }

    private static String firstAbi() {
        String[] abis = Build.SUPPORTED_ABIS;
        if (abis != null && abis.length > 0) return abis[0];
        return Build.CPU_ABI;
    }

    /** {@code proc_threads}：列出本进程的 tid、线程名与调度状态。 */
    private static JSONArray threads() throws Exception {
        JSONArray out = new JSONArray();
        File[] entries = new File(PROC_TASK).listFiles();
        if (entries == null) return out;
        List<File> tids = new ArrayList<>();
        Collections.addAll(tids, entries);
        Collections.sort(tids, Comparator.comparingInt(f -> parseInt(f.getName(), Integer.MAX_VALUE)));
        for (File dir : tids) {
            if (!dir.isDirectory()) continue;
            JSONObject t = new JSONObject().put("tid", parseInt(dir.getName(), -1));
            try {
                t.put("name", Proc.read(new File(dir, "comm").getAbsolutePath(), 2_048).trim());
            } catch (Throwable ignored) {
                // 线程可能在这两次读取之间退出，缺名字不是错误。
            }
            try {
                String status = Proc.read(new File(dir, "status").getAbsolutePath(), 8_192);
                int at = status.indexOf("State:");
                if (at >= 0) {
                    int eol = status.indexOf('\n', at);
                    t.put("state", (eol < 0 ? status.substring(at + 6) : status.substring(at + 6, eol)).trim());
                }
            } catch (Throwable ignored) {
                // 同上。
            }
            out.put(t);
        }
        return out;
    }

    /** {@code proc_thread_dump}：按线程名排序导出 Java 侧栈。 */
    private static JSONArray threadDump() throws Exception {
        List<Map.Entry<Thread, StackTraceElement[]>> entries =
                new ArrayList<>(Thread.getAllStackTraces().entrySet());
        Collections.sort(entries, Comparator.comparing(e -> safe(e.getKey().getName())));
        JSONArray out = new JSONArray();
        for (Map.Entry<Thread, StackTraceElement[]> entry : entries) {
            Thread t = entry.getKey();
            JSONArray stack = new JSONArray();
            int depth = 0;
            for (StackTraceElement frame : entry.getValue()) {
                if (depth++ >= MAX_STACK_DEPTH) break;
                stack.put(frame.toString());
            }
            out.put(new JSONObject()
                    .put("id", t.getId())
                    .put("name", safe(t.getName()))
                    .put("state", String.valueOf(t.getState()))
                    .put("stack", stack));
        }
        return out;
    }

    // ---------------------------------------------------------- 地址空间快照

    /**
     * {@code proc_maps}：过滤后的 /proc/self/maps 原文。
     *
     * <p>刻意回原文而不是结构化数据：Agent 常常要肉眼扫一行，改过形态反而难用。
     */
    private static String maps(String filter, int maxLines) throws Exception {
        final String needle = needle(filter);
        StringBuilder out = new StringBuilder();
        int emitted = 0;
        for (MapLine line : MapsSnapshot.capture().lines) {
            if (emitted >= maxLines || out.length() >= MAX_MAP_TEXT) break;
            if (!needle.isEmpty() && !line.raw.toLowerCase(Locale.US).contains(needle)) continue;
            out.append(line.raw).append('\n');
            emitted++;
        }
        return out.toString();
    }

    /** {@code proc_modules}：把快照按文件路径聚合，给出每张 .so / 可执行映射的基址与跨度。 */
    private static JSONArray modules(String filter, int maxModules) throws Exception {
        final String needle = needle(filter);
        Map<String, Module> grouped = new LinkedHashMap<>();
        for (MapLine line : MapsSnapshot.capture().lines) {
            String path = line.path;
            if (path.isEmpty() || path.charAt(0) == '[') continue;
            if (!needle.isEmpty() && !path.toLowerCase(Locale.US).contains(needle)) continue;
            Module mod = grouped.get(path);
            if (mod == null) {
                mod = new Module(path);
                grouped.put(path, mod);
            }
            mod.observe(line);
        }
        List<Module> ordered = new ArrayList<>(grouped.values());
        Collections.sort(ordered, Comparator.comparingLong(m -> m.base));
        JSONArray out = new JSONArray();
        for (Module mod : ordered) {
            if (out.length() >= maxModules) break;
            out.put(new JSONObject()
                    .put("path", mod.path)
                    .put("base", Codec.format(mod.base))
                    .put("start", Codec.format(mod.start))
                    .put("end", Codec.format(mod.end))
                    .put("size", mod.end - mod.start)
                    .put("perms", mod.perms.toString()));
        }
        return out;
    }

    /**
     * 定位包含 {@code [address, address+length)} 的那一条映射。
     *
     * <p>名字保持 {@code findMapping}：这是结构测试点名要保留的落点，
     * 也确实是「地址 → 映射」的唯一入口，读写两条路径都经过它。
     */
    private static MapLine findMapping(long address, int length, boolean needWritable) throws Exception {
        return MapsSnapshot.capture().locate(address, length, needWritable);
    }

    /**
     * 一次请求内的 /proc/self/maps 只读一遍。
     *
     * <p>动机很实际：maps、modules、locate 原本各自打开一次文件、各自写一套解析。
     * 同一请求里如果同时用到（例如先定位再读内存），三次读取之间映射可能已经变了，
     * 于是「读到的地址」和「校验过的权限」可以对不上。改成一次快照后，同一请求内的
     * 判定基于同一份数据，也少两次 IO。
     */
    private static final class MapsSnapshot {
        final List<MapLine> lines;

        private MapsSnapshot(List<MapLine> lines) {
            this.lines = lines;
        }

        static MapsSnapshot capture() throws Exception {
            List<MapLine> lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new FileReader(PROC_MAPS))) {
                String raw;
                while ((raw = reader.readLine()) != null) {
                    MapLine line = MapLine.parse(raw);
                    if (line != null) lines.add(line);
                }
            }
            return new MapsSnapshot(lines);
        }

        MapLine locate(long address, int length, boolean needWritable) {
            long end = address + Math.max(1, length);
            if (end < address) throw new IllegalArgumentException("地址范围溢出");
            for (MapLine m : lines) {
                if (address < m.start || end > m.end) continue;
                if (!m.readable()) throw new SecurityException("目标内存页不可读: " + m.raw);
                if (needWritable && !m.writable()) throw new SecurityException("目标内存页不可写: " + m.raw);
                return m;
            }
            throw new IllegalArgumentException("地址不在当前 Guest 的有效映射中: " + Codec.format(address));
        }
    }

    /** 一条 /proc/self/maps 记录。 */
    private static final class MapLine {
        final long start;
        final long end;
        final long offset;
        final String perms;
        final String path;
        final String raw;

        private MapLine(long start, long end, long offset, String perms, String path, String raw) {
            this.start = start;
            this.end = end;
            this.offset = offset;
            this.perms = perms;
            this.path = path;
            this.raw = raw;
        }

        static MapLine parse(String raw) {
            try {
                String[] fields = raw.trim().split("\\s+", 6);
                if (fields.length < 5) return null;
                int dash = fields[0].indexOf('-');
                if (dash <= 0) return null;
                long start = Long.parseUnsignedLong(fields[0].substring(0, dash), 16);
                long end = Long.parseUnsignedLong(fields[0].substring(dash + 1), 16);
                long offset = Long.parseUnsignedLong(fields[2], 16);
                String path = fields.length >= 6 ? fields[5].trim() : "";
                return new MapLine(start, end, offset, fields[1], path, raw);
            } catch (Throwable malformed) {
                // 读一半的文件行、内核新格式等都会落到这里；跳过这一行即可。
                return null;
            }
        }

        boolean readable() {
            return perms.length() >= 1 && perms.charAt(0) == 'r';
        }

        boolean writable() {
            return perms.length() >= 2 && perms.charAt(1) == 'w';
        }
    }

    /** 同一个映射文件的所有段。 */
    private static final class Module {
        final String path;
        long base = Long.MAX_VALUE;
        long start = Long.MAX_VALUE;
        long end = 0;
        final StringBuilder perms = new StringBuilder();

        Module(String path) {
            this.path = path;
        }

        /** 基址取 {@code start-offset} 的最小值；地址空间比较一律走无符号。 */
        void observe(MapLine m) {
            long candidate = m.start - m.offset;
            if (Long.compareUnsigned(candidate, base) < 0) base = candidate;
            if (Long.compareUnsigned(m.start, start) < 0) start = m.start;
            if (Long.compareUnsigned(m.end, end) > 0) end = m.end;
            if (perms.indexOf(m.perms) < 0) {
                if (perms.length() > 0) perms.append(',');
                perms.append(m.perms);
            }
        }
    }

    // -------------------------------------------------------------- 内存读写

    /**
     * {@code proc_memory_read}：读 guest 自己的内存。
     *
     * <p>先做映射与可读性校验，再交给进程内的 Frida；校验不通过时连 Frida 都不会被拉起，
     * 这样「地址非法」与「Frida 缺失」是两类可区分的错误。
     */
    private static JSONObject memoryRead(Context context, String addressText, int size, String format) throws Exception {
        long address = Codec.parseAddress(addressText);
        int count = clamp(size, 1, MAX_WRITE_BYTES);
        MapLine mapping = findMapping(address, count, false);
        JSONObject payload = new JSONObject()
                .put("address", Codec.format(address))
                .put("size", count)
                .put("volatile", true);
        JSONObject result = fridaRoundTrip(context, "read", payload, "memory_read/frida_read");
        String hexData = result.optString("hex", "");
        String normalized = Codec.normalizeFormat(format);
        String data = "base64".equals(normalized)
                ? Base64.encodeToString(Codec.fromHex(hexData), Base64.NO_WRAP)
                : hexData;
        return new JSONObject()
                .put("address", result.optString("address", Codec.format(address)))
                .put("size", result.optInt("size", count))
                .put("mapping", mapping.raw)
                .put("backend", "frida_in_process")
                .put("volatile", true)
                .put("format", normalized)
                .put("data", data);
    }

    /** {@code proc_memory_write}：写 guest 自己的内存，写入前核对长度上限与目标页可写。 */
    private static JSONObject memoryWrite(Context context, String addressText, String encoded, String format) throws Exception {
        long address = Codec.parseAddress(addressText);
        byte[] data = Codec.decode(encoded, Codec.normalizeFormat(format));
        if (data.length == 0) throw new IllegalArgumentException("写入数据不能为空");
        if (data.length > MAX_WRITE_BYTES) {
            throw new IllegalArgumentException("单次最多写入 " + MAX_WRITE_BYTES + " 字节");
        }
        MapLine mapping = findMapping(address, data.length, true);
        JSONObject payload = new JSONObject()
                .put("address", Codec.format(address))
                .put("data", Codec.toHex(data))
                .put("volatile", true);
        JSONObject result = fridaRoundTrip(context, "write", payload, "memory_write/frida_write");
        return new JSONObject()
                .put("address", result.optString("address", Codec.format(address)))
                .put("size", result.optInt("written", data.length))
                .put("mapping", mapping.raw)
                .put("backend", "frida_in_process")
                .put("volatile", true)
                .put("written", true);
    }

    /**
     * 走进程内 Frida 完成一次内存操作，两端（读/写）共用。
     *
     * <p>Frida 未就绪时抛的是一句「告诉你怎么做」的错误，而不是让调用方去猜权限问题。
     */
    private static JSONObject fridaRoundTrip(Context context, String op, JSONObject payload, String retryHint) throws Exception {
        Context host = ZhiSandbox.hostContext();
        if (host == null) host = context;
        if (!FridaEnv.isInstalled(host)) throw rawChannelUnavailable(retryHint);
        JSONObject response = SandboxFrida.command(context, op, payload, FRIDA_TIMEOUT_MS);
        JSONObject result = response == null ? null : response.optJSONObject("result");
        if (result == null) throw new IllegalStateException("Frida " + op + " 未返回 result: " + response);
        return result;
    }

    private static IllegalStateException rawChannelUnavailable(String retryHint) {
        return new IllegalStateException(
                "安全内存通道未就绪：已禁用 " + RAW_MEM_CHANNEL + " 与 Unsafe 直访以避免 EACCES/闪退。"
                        + "先执行 Debug action=frida_install，再重试 " + retryHint + "。");
    }

    // ---------------------------------------------------------------- 注入

    /**
     * {@code proc_load_library}：受控地把一个 .so 载入当前 guest 进程。
     *
     * <p>只接受本应用私有目录内的绝对路径。这不是怕麻烦——
     * 一旦放行任意路径，这个动作就等于「把任意本机文件 dlopen 进一个持有本应用全部权限的进程」。
     */
    private static JSONObject loadLibrary(Context context, String pathText) throws Exception {
        File target = injectionTarget(context, pathText);
        String canonical = target.getAbsolutePath();
        System.load(canonical);
        SandboxConsole.event("Guest 受控加载调试库: " + canonical + " pid=" + Process.myPid());
        return new JSONObject().put("loaded", true).put("path", canonical).put("pid", Process.myPid());
    }

    /** 校验并规范化待注入的 .so 路径；失败时抛出带原因的异常。 */
    private static File injectionTarget(Context context, String pathText) throws Exception {
        if (pathText == null || pathText.trim().isEmpty()) {
            throw new IllegalArgumentException("load_library 需要绝对 .so 路径");
        }
        File candidate = new File(pathText.trim()).getCanonicalFile();
        if (!candidate.isAbsolute() || !candidate.isFile() || !candidate.getName().endsWith(".so")) {
            throw new IllegalArgumentException("不是有效 .so 文件: " + candidate);
        }
        String canonical = candidate.getAbsolutePath();
        for (String root : injectionRoots(context)) {
            if (canonical.startsWith(root + File.separator)) return candidate;
        }
        throw new SecurityException("沙箱调试注入只允许加载本应用私有目录中的 .so；"
                + "先把调试库复制到 files/home 或 sandbox/debug-libs");
    }

    /**
     * 允许注入的目录前缀，<b>全部由运行时信息派生</b>，一个都不写死。
     *
     * <p>列了四个而不是只留 {@code dataDir}，是因为虚拟运行时里可能存在两套 Context：
     * 宿主 Context 与 guest Context 的 {@code dataDir} 拼写一致，但 {@code filesDir} /
     * {@code nativeLibraryDir} 可能已被重定向到别处，只认 {@code dataDir} 会漏掉合法目标。
     *
     * <p>{@code /data/data/<pkg>} 是 {@code dataDir} 的旧版符号链接别名，同一目录的另一种写法，
     * 因此必须一起放行，否则通过符号链接传进来的合法路径会被误拒。
     *
     * <p>刻意<b>不</b>放行 {@code /data/user/0}（上一级）：那等于允许任意应用的数据目录，
     * 而这里是一道安全边界，宁可少允许也不能放宽。
     */
    private static List<String> injectionRoots(Context context) {
        List<String> roots = new ArrayList<>(4);
        ApplicationInfo self = context.getApplicationInfo();
        addRoot(roots, self == null ? null : self.dataDir);
        addRoot(roots, "/data/data/" + context.getPackageName());
        try {
            addRoot(roots, context.getFilesDir() == null ? null : context.getFilesDir().getAbsolutePath());
        } catch (Throwable ignored) {
            // filesDir 极端情况下不可用；dataDir 仍然覆盖它。
        }
        addRoot(roots, self == null ? null : self.nativeLibraryDir);
        return roots;
    }

    private static void addRoot(List<String> roots, String root) {
        if (root == null || root.isEmpty()) return;
        if (!roots.contains(root)) roots.add(root);
    }

    // --------------------------------------------------------------- 编解码

    /** hex / base64 / 地址解析的集中落点，避免各处各写一遍格式判断。 */
    private static final class Codec {
        private Codec() {}

        static String normalizeFormat(String format) {
            return "base64".equalsIgnoreCase(format) ? "base64" : "hex";
        }

        /** 宽松解析：容忍空白、{@code 0x} 前缀与分隔符，但长度必须成对。 */
        static byte[] decode(String encoded, String format) {
            String text = encoded == null ? "" : encoded.trim();
            if ("base64".equals(format)) return Base64.decode(text, Base64.DEFAULT);
            return fromHex(text);
        }

        static byte[] fromHex(String text) {
            String clean = text == null ? "" : text.replace("0x", "").replaceAll("[^0-9A-Fa-f]", "");
            if ((clean.length() & 1) != 0) throw new IllegalArgumentException("hex 数据长度必须为偶数");
            byte[] out = new byte[clean.length() / 2];
            for (int i = 0; i < out.length; i++) {
                out[i] = (byte) Integer.parseInt(clean.substring(i * 2, i * 2 + 2), 16);
            }
            return out;
        }

        static String toHex(byte[] data) {
            StringBuilder out = new StringBuilder(data.length * 2);
            for (byte b : data) out.append(String.format(Locale.US, "%02x", b & 0xff));
            return out.toString();
        }

        static long parseAddress(String text) {
            String s = text == null ? "" : text.trim().toLowerCase(Locale.US);
            if (s.startsWith("0x")) s = s.substring(2);
            if (s.isEmpty()) throw new IllegalArgumentException("需要 address，例如 0x7a12340000");
            return Long.parseUnsignedLong(s, 16);
        }

        static String format(long value) {
            return "0x" + Long.toUnsignedString(value, 16);
        }
    }

    /** /proc 小文件的有限读取。 */
    private static final class Proc {
        private Proc() {}

        static String read(String path, int max) throws Exception {
            try (FileInputStream in = new FileInputStream(path);
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) != -1 && out.size() < max) {
                    out.write(buf, 0, Math.min(n, max - out.size()));
                }
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        }
    }

    // ------------------------------------------------------------------ 小工具

    private static String needle(String filter) {
        return filter == null ? "" : filter.trim().toLowerCase(Locale.US);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s);
        } catch (Throwable e) {
            return fallback;
        }
    }
}
