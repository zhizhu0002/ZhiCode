package com.zhizhu.zhicode.sandbox;

import android.content.Context;
import android.os.Process;
import android.os.SystemClock;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * ZhiCode 沙箱单个 guest 进程内的 Frida Gadget 桥。
 *
 * <p>Gadget 以 autonomous Script 模式运行：它自己在进程里拉起一个常驻脚本，
 * 脚本循环读一个私有命令文件、执行 Frida 原生操作、把 JSON 结果写回响应文件。
 * 这条「文件信箱」是刻意的选择——它让 Agent 拿到动态内存/插桩能力，
 * 而<b>不需要</b> adb、frida-server、ptrace，或手机上一个 Python Frida 客户端。
 * 在没有 root 的 Android 上，这几种常规做法要么不可用，要么要求用户先把手机变成可调试的。
 *
 * <h3>为什么自己写信箱而不是直接用 Gadget 的 rpc</h3>
 * Gadget 的 rpc 需要宿主侧有 frida-core 客户端来握手，而这里没有。
 * 文件信箱把「谁来调用」这件事简化成「谁能在同一个目录里读写」——
 * 目录权限就是访问控制，二者都在同一个 guest 进程的私有空间里。
 *
 * <h3>信箱协议</h3>
 * <pre>
 * 请求： 命令方把 {"id","op","payload"} 原子写入 command.json
 * 响应： 脚本把 {"id","ok","result"|"error"} 写入 response-&lt;id&gt;.json
 * 就绪： 脚本在 rpc.exports.init 结束时写 ready.json
 * 静默： 脚本的异步事件（Zhi.emit / watch）追加到 events.log
 * </pre>
 * 每个请求用唯一 id 与独立响应文件，因此<b>并发请求不会串线</b>；
 * Java 侧仍对 command(...) 加锁，因为 command.json 是单一信箱，
 * 两个线程同时覆写它必然丢请求。锁在 Java 这一侧，脚本那侧靠 id 去重。
 */
final class SandboxFrida {

    /** Gadget 载入后等待 ready.json 的上限。超时说明脚本没跑起来，而不是 Gadget 载入失败。 */
    private static final long LOAD_TIMEOUT_MS = 6_000;

    /** 等就绪 / 等响应的轮询间隔。太小会白烧 CPU，太大则让短命令也变慢。 */
    private static final long POLL_INTERVAL_MS = 20;
    private static final long READY_POLL_MS = 25;

    /** 命令超时下限：低于它连一次进程内往返都盖不住，会把正常命令误判成超时。 */
    private static final int MIN_COMMAND_TIMEOUT_MS = 800;

    /** events 导出的字符数上下限，与工具 schema 的 max_chars 一致。 */
    private static final int MIN_EVENTS_CHARS = 1_024;
    private static final int MAX_EVENTS_CHARS = 1_000_000;

    /** 拷贝 Gadget 的缓冲区；Gadget 约数十 MB，用大块减少系统调用次数。 */
    private static final int COPY_BUFFER_BYTES = 128 * 1024;

    private static final String GADGET_FILE = "libzhifrida.so";
    /**
     * Gadget 按「自己的文件名换个后缀」找配置文件，所以两者必须同步改。
     *
     * <p>不能写成 {@code libzhifrida.config.so}：那是把 Gadget 打进 APK 的
     * {@code lib/<abi>/} 时才用的兼容名，从私有目录直接 {@code System.load}
     * 时要的是裸 {@code .config}。
     */
    private static final String CONFIG_FILE = "libzhifrida.config";
    /** 桥脚本名，由 {@code interaction.path} 以相对 Gadget 目录的方式引用。 */
    private static final String SCRIPT_FILE = "zhi-agent.js";
    private static final String MAILBOX_FILE = "command.json";
    private static final String MAILBOX_TMP = "command.tmp";
    private static final String READY_FILE = "ready.json";
    private static final String EVENTS_FILE = "events.log";

    /** 本进程的 session 目录；null 表示尚未加载（或加载中）。 */
    private static volatile File sessionDir;
    private static volatile boolean loaded;

    private SandboxFrida() {}

    // ------------------------------------------------------------------ 装载

    /**
     * 把 Gadget 与桥脚本装进当前 guest 进程。可重复调用：已就绪则直接返回现状。
     *
     * <p>整个过程是「拷贝 → 写配置 → 写脚本 → 清信箱 → System.load → 等就绪」。
     * 清信箱这一步不能省：上一次进程退出时可能留下一个未被消费的 command.json，
     * 脚本启动后会立刻把它当成新请求执行一遍——那是一次幽灵命令。
     */
    public static synchronized JSONObject load(Context context) throws Exception {
        Context host = hostOf(context);
        if (loaded && sessionDir != null) return status(host);

        File master = FridaEnv.masterGadget(host);
        if (!FridaEnv.isInstalled(host)) {
            throw new IllegalStateException("Frida Gadget 未安装；先执行 Debug action=frida_install");
        }

        File dir = new File(FridaEnv.root(host), "sessions/" + Process.myPid());
        if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) {
            throw new IllegalStateException("无法创建 Frida Guest session: " + dir);
        }

        File gadget = new File(dir, GADGET_FILE);
        File config = new File(dir, CONFIG_FILE);
        File script = new File(dir, SCRIPT_FILE);

        copyElf(master, gadget);
        writeText(config, gadgetConfig(script));
        writeText(script, agentScript(dir));
        new File(dir, MAILBOX_FILE).delete();
        new File(dir, READY_FILE).delete();

        System.load(gadget.getAbsolutePath());

        File ready = new File(dir, READY_FILE);
        long deadline = SystemClock.uptimeMillis() + LOAD_TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline && !ready.isFile()) Thread.sleep(READY_POLL_MS);
        if (!ready.isFile()) throw new IllegalStateException("Frida Gadget 已加载，但脚本桥还没就绪");

        loaded = true;
        sessionDir = dir;
        SandboxConsole.event("Frida Gadget 已进入 Guest pid=" + Process.myPid());
        return status(context);
    }

    /**
     * Gadget 的配置文件。
     *
     * <p>{@code path} 必须是<b>相对 Gadget 所在目录</b>的脚本名，不能是绝对路径：
     * Gadget 是按自己的所在目录解析它的，写绝对路径反而会加载失败。
     *
     * <p>{@code on_change:ignore} 表示运行期不重载脚本——重载会丢掉已注册的
     * Interceptor 与 watch，而 Agent 正靠它们观察进程。
     *
     * <p>{@code teardown:minimal} 让 Gadget 在卸载时少做收尾，避免在 guest 退出路径上
     * 卡住（那会表现为「关闭应用时闪退/挂起」）。
     */
    private static String gadgetConfig(File script) throws Exception {
        JSONObject interaction = new JSONObject()
                .put("type", "script")
                .put("path", script.getName())
                .put("on_change", "ignore");
        return new JSONObject()
                .put("interaction", interaction)
                .put("runtime", "qjs")
                .put("teardown", "minimal")
                .toString(2);
    }

    // ------------------------------------------------------------------ 状态

    public static JSONObject status(Context context) throws Exception {
        Context host = hostOf(context);
        JSONObject out = new JSONObject()
                .put("installed", FridaEnv.isInstalled(host))
                .put("version", FridaEnv.VERSION)
                .put("loaded", loaded)
                .put("pid", Process.myPid());
        File dir = sessionDir;
        if (dir != null) {
            out.put("session_dir", dir.getAbsolutePath())
                    .put("ready", new File(dir, READY_FILE).isFile());
        }
        return out;
    }

    // ------------------------------------------------------------ 命令信箱

    /**
     * 发一条命令并等它的响应。
     *
     * <p>{@code synchronized} 是必需的：command.json 是单一信箱，
     * 两个 Java 线程同时写会互相覆盖，表现为「命令偶发丢失/超时」。
     * 加锁只序列化<b>发起</b>这一步；脚本侧用 id 区分，因此多个长命令仍可并行执行，
     * 这也是这里不能设「全局忙位」的原因——那会把并行的长任务串成一条队列。
     *
     * @return 脚本的响应对象（已确认为 {@code ok:true}）
     */
    public static synchronized JSONObject command(Context context, String op, JSONObject payload, int timeoutMs) throws Exception {
        if (!loaded || sessionDir == null) load(context);
        File dir = sessionDir;
        if (dir == null) throw new IllegalStateException("Frida session 不可用");

        String id = Long.toHexString(System.nanoTime()) + "-" + Process.myPid();
        JSONObject request = new JSONObject()
                .put("id", id)
                .put("op", op == null ? "" : op)
                .put("payload", payload == null ? new JSONObject() : payload);

        File response = new File(dir, "response-" + id + ".json");
        publish(dir, request);

        long deadline = SystemClock.uptimeMillis() + Math.max(MIN_COMMAND_TIMEOUT_MS, timeoutMs);
        while (SystemClock.uptimeMillis() < deadline) {
            if (response.isFile()) {
                String text = readText(response);
                response.delete();
                JSONObject reply = new JSONObject(text);
                if (!reply.optBoolean("ok", false)) {
                    throw new IllegalStateException(reply.optString("error", reply.toString()));
                }
                return reply;
            }
            Thread.sleep(POLL_INTERVAL_MS);
        }
        // 超时是常态路径之一（例如长扫描），因此带上 op 名，让上层能区分是哪个命令。
        throw new IllegalStateException("Frida command timeout: " + op);
    }

    /**
     * 把请求放进信箱：先写 command.tmp，再 rename 成 command.json。
     *
     * <p>必须先落地再改名。脚本的轮询是 30ms 一次，如果直接写 command.json，
     * 它很可能在写到一半时读到半个 JSON——解析失败就被丢掉，命令永远不执行。
     * rename 在同一目录内是原子的，脚本要么看到旧内容，要么看到完整的新内容。
     */
    private static void publish(File dir, JSONObject request) throws Exception {
        File mailbox = new File(dir, MAILBOX_FILE);
        File tmp = new File(dir, MAILBOX_TMP);
        writeText(tmp, request.toString());
        if (tmp.renameTo(mailbox)) return;
        // 少数文件系统上 rename 到已存在目标会失败，退回直写；此时用「写前不动」的
        // 方式尽量缩小半截文件窗口。
        writeText(mailbox, request.toString());
        tmp.delete();
    }

    /**
     * 读走脚本累积的异步事件（{@code Zhi.emit} 与内存 watch 都写这里）。
     *
     * <p>只截尾部而不是整个文件：这段文本会被塞进 Agent 的上下文，
     * 超长时<em>最新</em>的事件显然比最早的有用。
     */
    public static String events(int maxChars) throws Exception {
        File dir = sessionDir;
        if (dir == null) return "";
        File log = new File(dir, EVENTS_FILE);
        if (!log.isFile()) return "";
        String text = readText(log);
        int limit = Math.max(MIN_EVENTS_CHARS, Math.min(MAX_EVENTS_CHARS, maxChars));
        return text.length() <= limit ? text : text.substring(text.length() - limit);
    }

    // ------------------------------------------------------------------ 文件

    /**
     * 把已安装的主 Gadget 拷进本进程的 session 目录。
     *
     * <p>每个 guest 一份拷贝是必要的：Gadget 会按「和自己同目录」去找配置文件，
     * 而各进程的脚本与信箱必须隔离——共用一份会让两个 guest 抢同一个 command.json。
     *
     * <p>拷贝前先比对「长度相同且是 ELF」，命中就跳过，避免每次 load 重拷几十 MB。
     */
    private static void copyElf(File source, File destination) throws Exception {
        if (destination.isFile() && destination.length() == source.length() && isElf(destination)) return;
        File tmp = new File(destination.getParentFile(), destination.getName() + ".tmp");
        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(tmp)) {
            byte[] buffer = new byte[COPY_BUFFER_BYTES];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        }
        // dlopen 需要可执行位；group/other 也可读可执行，因为 Frida 在部分路径上
        // 会以非 owner 身份打开它。可写位只留给 owner。
        tmp.setReadable(true, false);
        tmp.setExecutable(true, false);
        tmp.setWritable(true, true);
        if (tmp.renameTo(destination)) return;
        // rename 失败可能是目标已存在；删掉重试一次，仍失败才报错。
        if (destination.exists()) destination.delete();
        if (!tmp.renameTo(destination)) throw new IllegalStateException("无法放置 Frida Gadget: " + destination);
    }

    private static boolean isElf(File file) {
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] header = new byte[4];
            return in.read(header) == 4
                    && (header[0] & 0xff) == 0x7f
                    && header[1] == 'E' && header[2] == 'L' && header[3] == 'F';
        } catch (Throwable unreadable) {
            return false;
        }
    }

    private static void writeText(File file, String content) throws Exception {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory()) parent.mkdirs();
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
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

    private static Context hostOf(Context context) {
        Context host = ZhiSandbox.hostContext();
        return host == null ? context : host;
    }

    /** 把一段文本转义成可以嵌进 JS 双引号字符串的形式。 */
    private static String js(String text) {
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    // ------------------------------------------------------------ 桥脚本载荷

    /**
     * Frida agent 源码。
     *
     * <p><b>为什么是一份 text block 而不是 assets 里的 .js</b>：Gadget 的 Script 交互要求
     * 脚本是一个与 Gadget 同目录的文件，而内容必须在进程内即可生成。也曾考虑过把脚本挪进
     * {@code assets/} 或 {@code res/raw/} —— 但那样它就从 {@code tools/provenance.sh} 的
     * 比对范围里消失了（那个脚本只比对 {@code .java}/{@code .kt}）：一行都没重写，
     * 数字却会变好看。这份载荷有五百多行，摆在 Java 里确实不好看，但摆在那里才看得见。
     *
     * <p><b>归属（这里曾经写过一句不成立的话，记下来）</b>：这份载荷原先与
     * {@code IQ-Code-Android/app/src/main/java/com/iqge/sandbox/SandboxFridaBridge.java}
     * 里 {@code bridgeScript} 的返回串<b>同文</b> —— 把品牌名对齐（{@code Zhi} ↔ {@code IQ}）
     * 之后逐行相同。本类此前用一句注释把它声明成「从外部引入的冻结资源，不是本次重写的对象」，
     * 那正是本工程明确要防的写法：<b>一句注释就能让度量里的对象自己消失</b>。
     * （Termux 上游那七千多行确实被单列，但那是按写明的规则、且有独立的来源与许可；
     * 这里当初没有那样的依据，写下的却是一样的结论。）它现在按自己的结构重写过：
     * 常量集中在头部、op 走一张分派表、扫描器分块带名字、错误文案是自己的。
     *
     * <p><b>说清楚这一次重写买到了什么</b>：它买到的是度量上那 40 行（净相同行 2157 → 2118），
     * <b>不是</b>「因此就不再是派生实现」—— 算法与线上契约按设计保持不变，所以它仍然是
     * 派生作品（IQ Code 是 MIT，原作者也已许可改写与发行，这不冲突）。
     * 真正与度量无关、也是这次顺带做成的收益是可读性（原先有一行是 3210 个字符）
     * 与测试：原先守这 40 行的全是逐字拼写断言（{@code contains("…")}），等于「不许改」。
     *
     * <p><b>边界与守卫</b>：{@code rpc.exports} / 信箱文件名 / ready 标记 / 响应字段是
     * Java 侧与本脚本的协议边界，改一侧必须同步改另一侧。行为由
     * {@code app/tests/js/frida-agent-harness.mjs} 真跑着验证（把这份载荷从下面的 text block
     * 里抽出来，用桩替换 Frida 宿主对象，按信箱协议发命令读响应：五种停止原因、
     * 部分失败要返回部分结果、重叠不虚增 scanned、重复上报去重、慢命令不阻塞短命令……）；
     * 名字一致性与两棵树的数值关系由 {@code FridaScriptBootstrapRegressionTest} 与
     * {@code FridaDeadlockRegressionTest} 守着。
     * 那个 harness <b>证明不了</b>真实的 Gadget 载入、{@code Interceptor}/{@code Stalker}
     * 钩子与真实 {@code Memory.scan} 的行为 —— 那些要在 IQ 沙箱里跑起来才知道。
     */
    private static String agentScript(File dir) {
        String base = js(dir.getAbsolutePath());
        return """
                rpc.exports = {
                  init() {
                    const DIR = "__BASE__";
                    const MAILBOX = DIR + "/command.json";
                    const READY = DIR + "/ready.json";
                    const EVENTS = DIR + "/events.log";

                    const HIT_LIMIT = 2048;
                    const HIT_DEFAULT = 256;
                    const TEXT_LIMIT = 262144;
                    const ARRAY_LIMIT = 2048;
                    const KEY_LIMIT = 256;
                    const DEPTH_LIMIT = 8;
                    const ERR_LIMIT = 128;
                    const PATTERN_MAX = 8388608;
                    const CHUNK_MIN = 65536;
                    const CHUNK_MAX = 8388608;
                    const CHUNK_DEFAULT = 4194304;
                    const SCAN_TIMEOUT_MIN = 1000;
                    const SCAN_TIMEOUT_MAX = 120000;
                    const SCAN_TIMEOUT_DEFAULT = 30000;
                    const EVAL_TIMEOUT_MIN = 1000;
                    const EVAL_TIMEOUT_MAX = 120000;
                    const EVAL_TIMEOUT_DEFAULT = 30000;
                    const WATCH_MIN = 1;
                    const WATCH_MAX = 65536;
                    const WATCH_INTERVAL_MIN = 20;
                    const WATCH_INTERVAL_MAX = 60000;
                    const READ_MIN = 1;
                    const READ_MAX = 1048576;
                    const RANGE_LIMIT_DEFAULT = 4000;

                    const hooks = new Map();
                    const watches = new Map();
                    const inflight = new Map();
                    let lastId = '';

                    function clamp(value, fallback, low, high) {
                      if (value === undefined || value === null || value === '' || typeof value === 'boolean') return fallback;
                      const n = Number(value);
                      return Number.isFinite(n) ? Math.max(low, Math.min(high, Math.floor(n))) : fallback;
                    }

                    function gap(from, to) {
                      const n = parseInt(to.sub(from).toString(), 16);
                      if (!Number.isSafeInteger(n) || n < 0) throw new Error('scan range is too large');
                      return n;
                    }

                    function toHex(buffer) {
                      if (buffer === null) return '';
                      const view = new Uint8Array(buffer);
                      let out = '';
                      for (let i = 0; i < view.length; i++) out += view[i].toString(16).padStart(2, '0');
                      return out;
                    }

                    function toBytes(text) {
                      const cleaned = String(text || '').replace(/0x/g, '').replace(/[^0-9a-f]/gi, '');
                      if (cleaned.length % 2) throw new Error('hex length must be even');
                      const out = new Uint8Array(cleaned.length / 2);
                      for (let i = 0; i < out.length; i++) out[i] = parseInt(cleaned.substr(i * 2, 2), 16);
                      return out;
                    }

                    function jsonSafe(value, depth, seen) {
                      depth = depth || 0;
                      seen = seen || new Set();
                      if (value === undefined || value === null) return null;
                      if (typeof value === 'bigint') return value.toString();
                      if (typeof value === 'string') return value.length > TEXT_LIMIT ? value.slice(0, TEXT_LIMIT) + '…[truncated]' : value;
                      if (typeof value !== 'object') return value;
                      if (value && value.constructor && value.constructor.name === 'NativePointer') return value.toString();
                      if (depth >= DEPTH_LIMIT) return '[depth-limit]';
                      if (seen.has(value)) return '[circular]';
                      seen.add(value);
                      try {
                        if (Array.isArray(value)) {
                          const kept = Math.min(value.length, ARRAY_LIMIT);
                          const out = [];
                          for (let i = 0; i < kept; i++) out.push(jsonSafe(value[i], depth + 1, seen));
                          if (value.length > kept) out.push('[+' + (value.length - kept) + ' more]');
                          return out;
                        }
                        const out = {};
                        let keys = 0;
                        for (const key in value) {
                          if (keys++ >= KEY_LIMIT) { out.__truncated__ = true; break; }
                          try { out[key] = jsonSafe(value[key], depth + 1, seen); }
                          catch (_) { out[key] = '[unserializable]'; }
                        }
                        return out;
                      } finally {
                        seen.delete(value);
                      }
                    }

                    function appendEvent(value) {
                      try {
                        const log = new File(EVENTS, 'a');
                        log.write(JSON.stringify({ts: Date.now(), value: jsonSafe(value)}) + '\\n');
                        log.flush();
                        log.close();
                      } catch (_) {
                      }
                    }

                    const Zhi = {emit: appendEvent, hooks, watches, ptr: (v) => ptr(v), module: (n) => Process.getModuleByName(n)};

                    function slicesOf(start, end) {
                      const ranges = Process.enumerateRanges({protection: 'r--', coalesce: false}).slice().sort((a, b) => a.base.compare(b.base));
                      const out = [];
                      let floor = start;
                      for (const range of ranges) {
                        const rangeEnd = range.base.add(range.size);
                        if (rangeEnd.compare(floor) <= 0 || range.base.compare(end) >= 0) continue;
                        const from = range.base.compare(floor) < 0 ? floor : range.base;
                        const to = rangeEnd.compare(end) > 0 ? end : rangeEnd;
                        if (from.compare(to) < 0) {
                          out.push({base: from, size: gap(from, to)});
                          floor = to;
                          if (floor.compare(end) >= 0) break;
                        }
                      }
                      return out;
                    }

                    function scanBlock(base, size, pattern, found) {
                      return new Promise((resolve) => {
                        let settled = false;
                        try {
                          Memory.scan(base, size, pattern, {
                            onMatch(address, matchSize) {
                              const key = address.toString() + ':' + matchSize;
                              if (found.seen.has(key)) {
                                found.repeats++;
                              } else {
                                found.seen.add(key);
                                found.hits.push({address: address.toString(), size: matchSize});
                              }
                              if (found.hits.length >= found.limit) {
                                found.capped = true;
                                return 'stop';
                              }
                            },
                            onError(reason) {
                              if (!settled) { settled = true; resolve({ok: false, error: String(reason)}); }
                            },
                            onComplete() {
                              if (!settled) { settled = true; resolve({ok: true}); }
                            }
                          });
                        } catch (error) {
                          if (!settled) { settled = true; resolve({ok: false, error: String(error)}); }
                        }
                      });
                    }

                    function cellCount(pattern) {
                      const halves = pattern.split(':');
                      if (halves.length > 2) throw new Error('invalid scan pattern');
                      const cells = halves[0].trim().split(/\\s+/);
                      if (cells.length === 0 || cells.some((v) => !/[0-9a-f?]{2}/i.test(v) || v.length !== 2)) throw new Error('invalid scan pattern');
                      if (halves.length === 2) {
                        const masks = halves[1].trim().split(/\\s+/);
                        if (masks.length !== cells.length || masks.some((v) => !/[0-9a-f]{2}/i.test(v) || v.length !== 2)) throw new Error('invalid scan pattern mask');
                      }
                      return cells.length;
                    }

                    function scanTarget(params) {
                      const moduleName = String(params.module || '').trim();
                      const hasAddress = params.address !== undefined && params.address !== null && String(params.address).trim() !== '';
                      const hasSize = params.size !== undefined && params.size !== null && String(params.size).trim() !== '';
                      if (moduleName && (hasAddress || hasSize)) throw new Error('scan accepts either module or address/size, not both');
                      let base, size;
                      if (moduleName) {
                        const found = Process.getModuleByName(moduleName);
                        base = found.base;
                        size = Number(found.size);
                      } else {
                        if (!hasAddress || !hasSize) throw new Error('scan requires module or address with size');
                        base = ptr(params.address);
                        size = Number(params.size);
                      }
                      if (!Number.isSafeInteger(size) || size <= 0) throw new Error('scan size must be a positive safe integer');
                      const end = base.add(size);
                      if (end.compare(base) <= 0) throw new Error('scan range overflow');
                      return {base, end, size, module: moduleName};
                    }

                    function stillMapped(target) {
                      if (!target.module) return true;
                      try {
                        const found = Process.getModuleByName(target.module);
                        return found.base.compare(target.base) === 0 && Number(found.size) === target.size;
                      } catch (_) {
                        return false;
                      }
                    }

                    async function boundedScan(options, outerDeadline) {
                      const params = options || {};
                      const target = scanTarget(params);
                      const pattern = String(params.pattern || '').trim();
                      if (!pattern) throw new Error('scan pattern is empty');
                      const cells = cellCount(pattern);
                      if (cells > PATTERN_MAX) throw new Error('scan pattern is too large');
                      const limit = clamp(params.max, HIT_DEFAULT, 1, HIT_LIMIT);
                      const wanted = clamp(params.chunk_size, CHUNK_DEFAULT, CHUNK_MIN, CHUNK_MAX);
                      const chunk = Math.min(CHUNK_MAX, Math.max(wanted, cells * 2));
                      const budget = clamp(params.timeout_ms, SCAN_TIMEOUT_DEFAULT, SCAN_TIMEOUT_MIN, SCAN_TIMEOUT_MAX);
                      const ownDeadline = Date.now() + budget;
                      const deadline = Number.isFinite(outerDeadline) ? Math.min(ownDeadline, outerDeadline) : ownDeadline;
                      const found = {seen: new Set(), hits: [], limit, capped: false, repeats: 0};
                      const errors = [];
                      let fails = 0;
                      let attempted = 0;
                      let scanned = 0;
                      let skipped = 0;
                      let failed = 0;
                      let cursor = target.base;
                      let expired = false;
                      let remapped = false;
                      while (cursor.compare(target.end) < 0 && !found.capped) {
                        if (Date.now() >= deadline) { expired = true; break; }
                        if (!stillMapped(target)) { remapped = true; break; }
                        const slices = slicesOf(cursor, target.end);
                        if (slices.length === 0) {
                          skipped += gap(cursor, target.end);
                          cursor = target.end;
                          break;
                        }
                        let retry = false;
                        for (const slice of slices) {
                          if (cursor.compare(slice.base) < 0) {
                            skipped += gap(cursor, slice.base);
                            cursor = slice.base;
                          }
                          const sliceEnd = slice.base.add(slice.size);
                          let first = true;
                          while (cursor.compare(sliceEnd) < 0) {
                            if (Date.now() >= deadline) { expired = true; break; }
                            if (!stillMapped(target)) { remapped = true; break; }
                            const overlap = first ? 0 : cells - 1;
                            const blockBase = overlap > 0 ? cursor.sub(overlap) : cursor;
                            const part = Math.min(chunk, gap(blockBase, sliceEnd));
                            const next = blockBase.add(part);
                            const advance = gap(cursor, next);
                            if (advance <= 0) throw new Error('scan chunk made no progress');
                            attempted += part;
                            const result = await scanBlock(blockBase, part, pattern, found);
                            cursor = next;
                            first = false;
                            if (result.ok) {
                              if (!found.capped) scanned += advance;
                            } else {
                              failed += advance;
                              fails++;
                              if (errors.length < ERR_LIMIT) errors.push({address: blockBase.toString(), size: part, error: result.error});
                              retry = true;
                            }
                            if (found.capped || expired || remapped || retry) break;
                            await new Promise((resolve) => setImmediate(resolve));
                          }
                          if (found.capped || expired || remapped || retry) break;
                        }
                        if (found.capped || expired || remapped) break;
                        if (retry) {
                          await new Promise((resolve) => setImmediate(resolve));
                          continue;
                        }
                        if (cursor.compare(target.end) < 0) {
                          skipped += gap(cursor, target.end);
                          cursor = target.end;
                        }
                      }
                      const reason = found.capped ? 'max_matches' : expired ? 'timeout' : remapped ? 'mapping_changed' : fails > 0 ? 'scan_errors' : 'complete';
                      return {
                        base: target.base.toString(),
                        size: target.size,
                        module: target.module || null,
                        pattern_size: cells,
                        chunk_size: chunk,
                        chunk_overlap: cells - 1,
                        timeout_ms: budget,
                        max_matches: limit,
                        attempted,
                        scanned,
                        skipped,
                        failed_size: failed,
                        match_count: found.hits.length,
                        duplicate_matches: found.repeats,
                        error_count: fails,
                        errors_truncated: fails > errors.length,
                        errors,
                        complete: reason === 'complete' && cursor.compare(target.end) >= 0,
                        stop_reason: reason,
                        matches: found.hits
                      };
                    }

                    function legacyScan(address, size, pattern, deadline) {
                      return boundedScan({address, size, pattern}, deadline).then((report) => {
                        if (!report.complete) throw new Error('Memory.scanSync compatibility scan stopped: ' + report.stop_reason);
                        return report.matches;
                      });
                    }

                    function translateScanSync(source) {
                      const token = 'Memory.scanSync';
                      let out = '';
                      let cursor = 0;
                      for (;;) {
                        const at = source.indexOf(token, cursor);
                        if (at < 0) {
                          out += source.slice(cursor);
                          break;
                        }
                        const open = source.indexOf('(', at + token.length);
                        if (open < 0) {
                          out += source.slice(cursor);
                          break;
                        }
                        let depth = 0;
                        let quote = '';
                        let escaped = false;
                        let close = -1;
                        const args = [];
                        let last = open + 1;
                        for (let i = open + 1; i < source.length; i++) {
                          const ch = source[i];
                          if (quote) {
                            if (escaped) escaped = false;
                            else if (ch === String.fromCharCode(92)) escaped = true;
                            else if (ch === quote) quote = '';
                            continue;
                          }
                          if (ch === String.fromCharCode(34) || ch === String.fromCharCode(39) || ch === String.fromCharCode(96)) {
                            quote = ch;
                            continue;
                          }
                          if (ch === '(' || ch === '[' || ch === '{') {
                            depth++;
                            continue;
                          }
                          if (ch === ')') {
                            if (depth === 0) {
                              args.push(source.slice(last, i));
                              close = i;
                              break;
                            }
                            depth--;
                            continue;
                          }
                          if (ch === ',' && depth === 0) {
                            args.push(source.slice(last, i));
                            last = i + 1;
                          }
                        }
                        if (close < 0 || args.length !== 3) {
                          out += source.slice(cursor, at + token.length);
                          cursor = at + token.length;
                          continue;
                        }
                        out += source.slice(cursor, at) + '(await __legacyScan(' + args[0] + ',' + args[1] + ',' + args[2] + '))';
                        cursor = close + 1;
                      }
                      return out;
                    }

                    const OPS = {};

                    OPS['ping'] = () => ({pid: Process.id, arch: Process.arch, platform: Process.platform, page_size: Process.pageSize});

                    OPS['modules'] = () => Process.enumerateModules().map((m) => ({name: m.name, base: m.base.toString(), size: m.size, path: m.path}));

                    OPS['ranges'] = (params) => Process.enumerateRanges({
                      protection: params.protection || 'r--',
                      coalesce: params.coalesce !== false
                    }).slice(0, params.max || RANGE_LIMIT_DEFAULT).map((r) => ({
                      base: r.base.toString(),
                      size: r.size,
                      protection: r.protection,
                      file: r.file ? {path: r.file.path, offset: r.file.offset, size: r.file.size} : null
                    }));

                    OPS['read'] = (params) => {
                      const size = Math.max(READ_MIN, Math.min(READ_MAX, Number(params.size || 64)));
                      const address = ptr(params.address);
                      const data = params.volatile === false ? address.readByteArray(size) : address.readVolatile(size);
                      return {
                        address: address.toString(),
                        size,
                        protection: Memory.queryProtection(address),
                        volatile: params.volatile !== false,
                        hex: toHex(data)
                      };
                    };

                    OPS['write'] = (params) => {
                      const address = ptr(params.address);
                      const data = toBytes(params.data);
                      if (params.volatile === false) address.writeByteArray(data.buffer);
                      else address.writeVolatile(data.buffer);
                      return {
                        address: address.toString(),
                        written: data.byteLength,
                        volatile: params.volatile !== false,
                        protection: Memory.queryProtection(address)
                      };
                    };

                    OPS['protect'] = (params) => {
                      const address = ptr(params.address);
                      const size = Math.max(1, Number(params.size || Process.pageSize));
                      return {
                        address: address.toString(),
                        size,
                        protection: params.protection,
                        success: Memory.protect(address, size, String(params.protection || 'rw-'))
                      };
                    };

                    OPS['patch'] = (params) => {
                      const address = ptr(params.address);
                      const data = toBytes(params.data);
                      Memory.patchCode(address, data.byteLength, (writer) => writer.writeByteArray(data.buffer));
                      return {address: address.toString(), patched: data.byteLength};
                    };

                    OPS['scan'] = (params) => boundedScan(params);

                    OPS['watch_start'] = (params) => {
                      const id = String(params.watch_id || ('watch-' + Date.now()));
                      if (watches.has(id)) throw new Error('watch already exists: ' + id);
                      const address = ptr(params.address);
                      const size = Math.max(WATCH_MIN, Math.min(WATCH_MAX, Number(params.size || 4)));
                      const every = Math.max(WATCH_INTERVAL_MIN, Math.min(WATCH_INTERVAL_MAX, Number(params.interval_ms || 100)));
                      let previous = null;
                      const timer = setInterval(() => {
                        try {
                          const now = toHex(address.readVolatile(size));
                          if (now !== previous) {
                            appendEvent({type: 'memory_watch', watch_id: id, address: address.toString(), size, previous, data: now});
                            previous = now;
                          }
                        } catch (error) {
                          appendEvent({type: 'memory_watch_error', watch_id: id, error: String(error)});
                        }
                      }, every);
                      watches.set(id, timer);
                      return {watch_id: id, address: address.toString(), size, interval_ms: every};
                    };

                    OPS['watch_stop'] = (params) => {
                      const id = String(params.watch_id || '');
                      const timer = watches.get(id);
                      if (timer !== undefined) {
                        clearInterval(timer);
                        watches.delete(id);
                      }
                      return {watch_id: id, stopped: timer !== undefined};
                    };

                    OPS['watch_stop_all'] = () => {
                      for (const timer of watches.values()) {
                        try { clearInterval(timer); } catch (_) {}
                      }
                      const count = watches.size;
                      watches.clear();
                      return {stopped: count};
                    };

                    OPS['export'] = (params) => {
                      const owner = params.module ? Process.getModuleByName(String(params.module)) : null;
                      const address = owner ? owner.findExportByName(String(params.name)) : Module.findGlobalExportByName(String(params.name));
                      return {address: address ? address.toString() : null};
                    };

                    OPS['hook_detach_all'] = () => {
                      for (const handle of hooks.values()) {
                        try { handle.detach(); } catch (_) {}
                      }
                      hooks.clear();
                      Interceptor.detachAll();
                      return {detached: true};
                    };

                    OPS['eval'] = async (params) => {
                      const ms = Math.max(EVAL_TIMEOUT_MIN, Math.min(EVAL_TIMEOUT_MAX, Number(params.timeout_ms || EVAL_TIMEOUT_DEFAULT)));
                      const deadline = Date.now() + ms;
                      const source = translateScanSync(String(params.script || ''));
                      const AsyncFunction = Object.getPrototypeOf(async function () {}).constructor;
                      const invokeUser = new AsyncFunction(
                        'Zhi', 'Process', 'Module', 'Memory', 'MemoryAccessMonitor', 'Interceptor', 'Stalker', 'Thread',
                        'DebugSymbol', 'Backtracer', 'NativeFunction', 'NativeCallback', 'CModule', 'ptr', '__legacyScan',
                        '"use strict";\\n' + source
                      );
                      const scanSyncShim = (address, size, pattern) => legacyScan(address, size, pattern, deadline);
                      const userZhi = Object.assign({}, Zhi, {scan: (options) => boundedScan(options || {}, deadline)});
                      const userMemory = new Proxy(Memory, {
                        get(store, key) {
                          if (key === 'scanSync') return scanSyncShim;
                          return store[key];
                        },
                        set(store, key, value) {
                          store[key] = value;
                          return true;
                        }
                      });
                      let timer;
                      try {
                        const value = await Promise.race([
                          Promise.resolve(invokeUser(userZhi, Process, Module, userMemory, MemoryAccessMonitor, Interceptor, Stalker,
                            Thread, DebugSymbol, Backtracer, NativeFunction, NativeCallback, CModule, ptr, scanSyncShim)),
                          new Promise((_, reject) => {
                            timer = setTimeout(() => reject(new Error('frida_eval deadline exceeded: ' + ms + 'ms')), ms);
                          })
                        ]);
                        return {value};
                      } finally {
                        if (timer !== undefined) clearTimeout(timer);
                      }
                    };

                    function writeReply(id, reply) {
                      try {
                        File.writeAllText(DIR + '/response-' + id + '.json', JSON.stringify(reply));
                      } catch (error) {
                        appendEvent({type: 'response_write_error', id, error: String(error)});
                      }
                      inflight.delete(id);
                    }

                    function invoke(entry) {
                      if (inflight.has(entry.id)) return;
                      inflight.set(entry.id, true);
                      const reply = {id: entry.id, ok: true};
                      Promise.resolve().then(() => {
                        const handler = OPS[entry.op];
                        if (typeof handler !== 'function') throw new Error('unknown Frida op: ' + entry.op);
                        return handler(entry.payload || {});
                      }).then(
                        (value) => { reply.result = jsonSafe(value); },
                        (error) => { reply.ok = false; reply.error = String(error && error.stack ? error.stack : error); }
                      ).then(
                        () => writeReply(entry.id, reply),
                        (error) => { reply.ok = false; reply.error = String(error); writeReply(entry.id, reply); }
                      );
                    }

                    function pollMailbox() {
                      let entry;
                      try {
                        entry = JSON.parse(File.readAllText(MAILBOX));
                      } catch (_) {
                        return;
                      }
                      if (!entry || !entry.id || entry.id === lastId) return;
                      lastId = entry.id;
                      invoke(entry);
                    }

                    File.writeAllText(READY, '1');
                    setInterval(pollMailbox, 30);
                  }
                };
                """.replace("__BASE__", base);
    }
}
