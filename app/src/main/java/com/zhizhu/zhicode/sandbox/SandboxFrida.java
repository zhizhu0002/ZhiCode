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
 * 蜘蛛沙箱单个 guest 进程内的 Frida Gadget 桥。
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
 * 静默： 脚本的异步事件（IQ.emit / watch）追加到 events.log
 * </pre>
 * 每个请求用唯一 id 与独立响应文件，因此<b>并发请求不会串线</b>；
 * Java 侧仍对 command(...) 加锁，因为 command.json 是单一信箱，
 * 两个线程同时覆写它必然丢请求。锁在 Java 这一侧，脚本那侧靠 id 去重。
 */
public final class SandboxFrida {

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

    private static final String GADGET_FILE = "libiqfrida.so";
    private static final String CONFIG_FILE = "libiqfrida.config";
    private static final String SCRIPT_FILE = "iq-agent.js";
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
        if (!ready.isFile()) throw new IllegalStateException("Frida Gadget 已加载但脚本桥未就绪");

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
     * 读走脚本累积的异步事件（{@code IQ.emit} 与内存 watch 都写这里）。
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
     * Frida agent 源码。写成字符串常量而不是放在 assets 里，是因为 Gadget 的
     * Script 交互要求脚本就是一个同目录的文件，而这份内容必须在进程内即可生成。
     *
     * <p><b>归属说明（重要）</b>：下面的 JS 是从 IQ Code 原样保留的 agent 载荷，
     * 属于<b>故意的冻结资源</b>，不是本次重写的对象。原因是它是一份对外契约：
     * <ul>
     *   <li>{@code IQ.emit} / {@code IQ.hooks} / {@code IQ.scan} 三个注入 API 名
     *       同时被工具 schema 与注入脚本引用，改名等于破坏 Agent 的既有用法；</li>
     *   <li>{@code rpc.exports} / 信箱文件名 / ready 标记是 Java 侧与本脚本的协议边界，
     *       改一侧就必须同步改另一侧；</li>
     *   <li>它的行为细节（有界扫描、部分失败可返回、legacy scanSync 重写、
     *       eval 沙箱与超时）都有回归断言盯着，重写只会引入行为漂移。</li>
     * </ul>
     * 因此这里只做了一件事：把它的<b>装载方式</b>（谁写、写去哪、何时清信箱）
     * 纳入本类的重写范围，而载荷本身保持字节不变。
     */
    private static String agentScript(File dir) {
        String base = js(dir.getAbsolutePath());
        return "rpc.exports={\n" +
            "init(){let I=\"" + base + "\";\n" +
            "const CMD=I+'/command.json'; const READY=I+'/ready.json'; const EVENTS=I+'/events.log';\n" +
            "const hooks=new Map(); const watches=new Map(); const active=new Map(); let lastId=''; const MAX_SCAN_MATCHES=2048; const DEFAULT_SCAN_MATCHES=256; const SCAN_SYNC_ERROR='Memory.scanSync is disabled in frida_eval; use Debug action=frida_scan or await IQ.scan(options).';\n" +
            "function jsonSafe(v,depth,seen){ depth=depth||0; seen=seen||new Set(); if(v===undefined||v===null)return null; if(typeof v==='bigint')return v.toString(); if(typeof v==='string')return v.length>262144?v.slice(0,262144)+'…[truncated]':v; if(typeof v!=='object')return v; if(v&&v.constructor&&v.constructor.name==='NativePointer')return v.toString(); if(depth>=8)return '[depth-limit]'; if(seen.has(v))return '[circular]'; seen.add(v); try{ if(Array.isArray(v)){const n=Math.min(v.length,2048),a=[];for(let i=0;i<n;i++)a.push(jsonSafe(v[i],depth+1,seen));if(v.length>n)a.push('[+'+(v.length-n)+' more]');return a;} const o={}; let count=0; for(const k in v){if(count++>=256){o.__truncated__=true;break;}try{o[k]=jsonSafe(v[k],depth+1,seen);}catch(e){o[k]='[unserializable]';}} return o;} finally{seen.delete(v);} }\n" +
            "function emit(v){ try{ const f=new File(EVENTS,'a'); f.write(JSON.stringify({ts:Date.now(),value:jsonSafe(v)})+'\\n'); f.flush(); f.close(); }catch(_){} }\n" +
            "const IQ={emit, hooks, watches, ptr:(v)=>ptr(v), module:(n)=>Process.getModuleByName(n)};\n" +
            "function hex(ab){ if(ab===null)return ''; const a=new Uint8Array(ab); let s=''; for(let i=0;i<a.length;i++)s+=a[i].toString(16).padStart(2,'0'); return s;}\n" +
            "function bytes(s){ s=String(s||'').replace(/0x/g,'').replace(/[^0-9a-f]/gi,''); if(s.length%2)throw new Error('hex length must be even'); const a=new Uint8Array(s.length/2); for(let i=0;i<a.length;i++)a[i]=parseInt(s.substr(i*2,2),16); return a;}\n" +
            "function pointerDistance(start,end){const n=parseInt(end.sub(start).toString(),16);if(!Number.isSafeInteger(n)||n<0)throw new Error('scan range is too large');return n;}\n" +
            "function legacyScan(a,n,pattern,deadline){return safeScan({address:a,size:n,pattern:pattern},deadline).then(r=>{if(!r.complete)throw new Error('Memory.scanSync compatibility scan stopped: '+r.stop_reason);return r.matches;});}\n" +
            "function rewriteLegacyScan(source){const token='Memory.scanSync';let out='',cursor=0;for(;;){const i=source.indexOf(token,cursor);if(i<0){out+=source.slice(cursor);break;}const open=source.indexOf('(',i+token.length);if(open<0){out+=source.slice(cursor);break;}let depth=0,quote='',escaped=false,end=-1,args=[],last=open+1;for(let j=open+1;j<source.length;j++){const ch=source[j];if(quote){if(escaped)escaped=false;else if(ch===String.fromCharCode(92))escaped=true;else if(ch===quote)quote='';continue;}if(ch===String.fromCharCode(34)||ch===String.fromCharCode(39)||ch===String.fromCharCode(96)){quote=ch;continue;}if(ch==='('||ch==='['||ch==='{'){depth++;continue;}if(ch===')'){if(depth===0){args.push(source.slice(last,j));end=j;break;}depth--;continue;}if(ch===','&&depth===0){args.push(source.slice(last,j));last=j+1;}}if(end<0||args.length!==3){out+=source.slice(cursor,i+token.length);cursor=i+token.length;continue;}out+=source.slice(cursor,i)+'(await __legacyScan('+args[0]+','+args[1]+','+args[2]+'))';cursor=end+1;}return out;}\n" +
            "function boundedInteger(value,fallback,min,max){if(value===undefined||value===null||value===''||typeof value==='boolean')return fallback;const n=Number(value);return Number.isFinite(n)?Math.max(min,Math.min(max,Math.floor(n))):fallback;}\n" +
            "function validateScanPattern(pattern){const parts=pattern.split(':');if(parts.length>2)throw new Error('invalid scan pattern');const bytes=parts[0].trim().split(/\\s+/);if(bytes.length===0||bytes.some(v=>!/[0-9a-f?]{2}/i.test(v)||v.length!==2))throw new Error('invalid scan pattern');if(parts.length===2){const mask=parts[1].trim().split(/\\s+/);if(mask.length!==bytes.length||mask.some(v=>!/[0-9a-f]{2}/i.test(v)||v.length!==2))throw new Error('invalid scan pattern mask');}return bytes.length;}\n" +
            "function resolveScanTarget(p){const moduleName=String(p.module||'').trim();const hasAddress=p.address!==undefined&&p.address!==null&&String(p.address).trim()!=='';const hasSize=p.size!==undefined&&p.size!==null&&String(p.size).trim()!=='';if(moduleName&&(hasAddress||hasSize))throw new Error('scan accepts either module or address/size, not both');let base,size;if(moduleName){const m=Process.getModuleByName(moduleName);base=m.base;size=Number(m.size);}else{if(!hasAddress||!hasSize)throw new Error('scan requires module or address with size');base=ptr(p.address);size=Number(p.size);}if(!Number.isSafeInteger(size)||size<=0)throw new Error('scan size must be a positive safe integer');const end=base.add(size);if(end.compare(base)<=0)throw new Error('scan range overflow');return {base,end,size,module:moduleName};}\n" +
            "function moduleUnchanged(target){if(!target.module)return true;try{const m=Process.getModuleByName(target.module);return m.base.compare(target.base)===0&&Number(m.size)===target.size;}catch(_){return false;}}\n" +
            "function readableSlices(start,end){const ranges=Process.enumerateRanges({protection:'r--',coalesce:false}).slice().sort((a,b)=>a.base.compare(b.base));const out=[];let floor=start;for(const range of ranges){const rangeEnd=range.base.add(range.size);if(rangeEnd.compare(floor)<=0||range.base.compare(end)>=0)continue;const sliceStart=range.base.compare(floor)<0?floor:range.base;const sliceEnd=rangeEnd.compare(end)>0?end:rangeEnd;if(sliceStart.compare(sliceEnd)<0){out.push({base:sliceStart,size:pointerDistance(sliceStart,sliceEnd)});floor=sliceEnd;if(floor.compare(end)>=0)break;}}return out;}\n" +
            "function scanBlock(base,size,pattern,state){return new Promise(resolve=>{let settled=false;try{Memory.scan(base,size,pattern,{onMatch(address,matchSize){const key=address.toString()+':'+matchSize;if(state.seen.has(key))state.duplicateMatches++;else{state.seen.add(key);state.matches.push({address:address.toString(),size:matchSize});}if(state.matches.length>=state.max){state.capped=true;return 'stop';}},onError(reason){if(!settled){settled=true;resolve({ok:false,error:String(reason)});}},onComplete(){if(!settled){settled=true;resolve({ok:true});}}});}catch(error){if(!settled){settled=true;resolve({ok:false,error:String(error)});}}});}\n" +
            "async function safeScan(options,outerDeadline){const p=options||{};const target=resolveScanTarget(p);const pattern=String(p.pattern||'').trim();if(!pattern)throw new Error('scan pattern is empty');const patternSize=validateScanPattern(pattern);if(patternSize>8388608)throw new Error('scan pattern is too large');const max=boundedInteger(p.max,DEFAULT_SCAN_MATCHES,1,MAX_SCAN_MATCHES);const requestedChunk=boundedInteger(p.chunk_size,4194304,65536,8388608);const chunkSize=Math.min(8388608,Math.max(requestedChunk,patternSize*2));const timeoutMs=boundedInteger(p.timeout_ms,30000,1000,120000);const localDeadline=Date.now()+timeoutMs;const deadline=Number.isFinite(outerDeadline)?Math.min(localDeadline,outerDeadline):localDeadline;const state={seen:new Set(),matches:[],max,capped:false,duplicateMatches:0};const errors=[];let errorCount=0,attempted=0,scanned=0,skipped=0,failedSize=0;let cursor=target.base,timedOut=false,mappingChanged=false;while(cursor.compare(target.end)<0&&!state.capped){if(Date.now()>=deadline){timedOut=true;break;}if(!moduleUnchanged(target)){mappingChanged=true;break;}const slices=readableSlices(cursor,target.end);if(slices.length===0){skipped+=pointerDistance(cursor,target.end);cursor=target.end;break;}let refresh=false;for(const slice of slices){if(cursor.compare(slice.base)<0){skipped+=pointerDistance(cursor,slice.base);cursor=slice.base;}const sliceEnd=slice.base.add(slice.size);let firstBlock=true;while(cursor.compare(sliceEnd)<0){if(Date.now()>=deadline){timedOut=true;break;}if(!moduleUnchanged(target)){mappingChanged=true;break;}const overlap=firstBlock?0:patternSize-1;const blockBase=overlap>0?cursor.sub(overlap):cursor;const part=Math.min(chunkSize,pointerDistance(blockBase,sliceEnd));const nextCursor=blockBase.add(part);const advance=pointerDistance(cursor,nextCursor);if(advance<=0)throw new Error('scan chunk made no progress');attempted+=part;const result=await scanBlock(blockBase,part,pattern,state);cursor=nextCursor;firstBlock=false;if(result.ok){if(!state.capped)scanned+=advance;}else{failedSize+=advance;errorCount++;if(errors.length<128)errors.push({address:blockBase.toString(),size:part,error:result.error});refresh=true;}if(state.capped||timedOut||mappingChanged||refresh)break;await new Promise(resolve=>setImmediate(resolve));}if(state.capped||timedOut||mappingChanged||refresh)break;}if(state.capped||timedOut||mappingChanged)break;if(refresh){await new Promise(resolve=>setImmediate(resolve));continue;}if(cursor.compare(target.end)<0){skipped+=pointerDistance(cursor,target.end);cursor=target.end;}}const stopReason=state.capped?'max_matches':timedOut?'timeout':mappingChanged?'mapping_changed':errorCount>0?'scan_errors':'complete';return {base:target.base.toString(),size:target.size,module:target.module||null,pattern_size:patternSize,chunk_size:chunkSize,chunk_overlap:patternSize-1,timeout_ms:timeoutMs,max_matches:max,attempted,scanned,skipped,failed_size:failedSize,match_count:state.matches.length,duplicate_matches:state.duplicateMatches,error_count:errorCount,errors_truncated:errorCount>errors.length,errors,complete:stopReason==='complete'&&cursor.compare(target.end)>=0,stop_reason:stopReason,matches:state.matches};}\n" +
            "IQ.scan=(options)=>safeScan(options||{});\n" +
            "async function run(c){ const p=c.payload||{}; switch(c.op){\n" +
            "case 'ping': return {pid:Process.id,arch:Process.arch,platform:Process.platform,page_size:Process.pageSize};\n" +
            "case 'modules': return Process.enumerateModules().map(m=>({name:m.name,base:m.base.toString(),size:m.size,path:m.path}));\n" +
            "case 'ranges': return Process.enumerateRanges({protection:p.protection||'r--',coalesce:p.coalesce!==false}).slice(0,p.max||4000).map(r=>({base:r.base.toString(),size:r.size,protection:r.protection,file:r.file?{path:r.file.path,offset:r.file.offset,size:r.file.size}:null}));\n" +
            "case 'read': { const n=Math.max(1,Math.min(1048576,Number(p.size||64))); const a=ptr(p.address); const data=p.volatile===false?a.readByteArray(n):a.readVolatile(n); return {address:a.toString(),size:n,protection:Memory.queryProtection(a),volatile:p.volatile!==false,hex:hex(data)}; }\n" +
            "case 'write': { const a=ptr(p.address); const b=bytes(p.data); if(p.volatile===false)a.writeByteArray(b.buffer);else a.writeVolatile(b.buffer); return {address:a.toString(),written:b.byteLength,volatile:p.volatile!==false,protection:Memory.queryProtection(a)}; }\n" +
            "case 'protect': { const a=ptr(p.address); const n=Math.max(1,Number(p.size||Process.pageSize)); return {address:a.toString(),size:n,protection:p.protection,success:Memory.protect(a,n,String(p.protection||'rw-'))}; }\n" +
            "case 'patch': { const a=ptr(p.address); const b=bytes(p.data); Memory.patchCode(a,b.byteLength,code=>code.writeByteArray(b.buffer)); return {address:a.toString(),patched:b.byteLength}; }\n" +
            "case 'scan': return safeScan(p);\n" +
            "case 'watch_start': { const id=String(p.watch_id||('watch-'+Date.now())); if(watches.has(id))throw new Error('watch already exists: '+id); const a=ptr(p.address),n=Math.max(1,Math.min(65536,Number(p.size||4))),ms=Math.max(20,Math.min(60000,Number(p.interval_ms||100))); let last=null; const timer=setInterval(()=>{try{const cur=hex(a.readVolatile(n));if(cur!==last){emit({type:'memory_watch',watch_id:id,address:a.toString(),size:n,previous:last,data:cur});last=cur;}}catch(e){emit({type:'memory_watch_error',watch_id:id,error:String(e)});}},ms); watches.set(id,timer); return {watch_id:id,address:a.toString(),size:n,interval_ms:ms}; }\n" +
            "case 'watch_stop': { const id=String(p.watch_id||''); const timer=watches.get(id); if(timer!==undefined){clearInterval(timer);watches.delete(id);} return {watch_id:id,stopped:timer!==undefined}; }\n" +
            "case 'watch_stop_all': { for(const timer of watches.values())try{clearInterval(timer);}catch(_){} const count=watches.size; watches.clear(); return {stopped:count}; }\n" +
            "case 'export': { const m=p.module?Process.getModuleByName(String(p.module)):null; const a=m?m.findExportByName(String(p.name)):Module.findGlobalExportByName(String(p.name)); return {address:a?a.toString():null}; }\n" +
            "case 'hook_detach_all': { for(const h of hooks.values()){try{h.detach();}catch(_){}} hooks.clear(); Interceptor.detachAll(); return {detached:true}; }\n" +
            "case 'eval': { const source=rewriteLegacyScan(String(p.script||'')); const AsyncFunction=Object.getPrototypeOf(async function(){}).constructor; const fn=new AsyncFunction('IQ','Process','Module','Memory','MemoryAccessMonitor','Interceptor','Stalker','Thread','DebugSymbol','Backtracer','NativeFunction','NativeCallback','CModule','ptr','__legacyScan','\"use strict\";\\n'+source); const ms=Math.max(1000,Math.min(120000,Number(p.timeout_ms||30000))); const deadline=Date.now()+ms; const evalIQ=Object.assign({},IQ,{scan:(options)=>safeScan(options||{},deadline)}); const evalMemory=new Proxy(Memory,{get(target,property){if(property==='scanSync')return (a,n,pattern)=>legacyScan(a,n,pattern,deadline);return target[property];},set(target,property,value){target[property]=value;return true;}}); let timer; try{const value=await Promise.race([Promise.resolve(fn(evalIQ,Process,Module,evalMemory,MemoryAccessMonitor,Interceptor,Stalker,Thread,DebugSymbol,Backtracer,NativeFunction,NativeCallback,CModule,ptr,(a,n,pattern)=>legacyScan(a,n,pattern,deadline))),new Promise((_,reject)=>{timer=setTimeout(()=>reject(new Error('frida_eval deadline exceeded: '+ms+'ms')),ms);})]); return {value:jsonSafe(value)};} finally{if(timer!==undefined)clearTimeout(timer);} }\n" +
            "default: throw new Error('unknown Frida op: '+c.op); }}\n" +
            "function finish(c,out){ try{File.writeAllText(I+'/response-'+c.id+'.json',JSON.stringify(out));}catch(e){emit({type:'response_write_error',id:c.id,error:String(e)});} active.delete(c.id); }\nfunction start(c){ if(active.has(c.id))return; active.set(c.id,true); const out={id:c.id,ok:true}; Promise.resolve().then(()=>run(c)).then(v=>{out.result=jsonSafe(v);},e=>{out.ok=false;out.error=String(e&&e.stack?e.stack:e);}).then(()=>finish(c,out),e=>{out.ok=false;out.error=String(e);finish(c,out);}); }\nfunction tick(){ let c; try{c=JSON.parse(File.readAllText(CMD));}catch(_){return;} if(!c||!c.id||c.id===lastId)return; lastId=c.id; start(c); }\n" +
            "File.writeAllText(READY,'1');\n" +
            "setInterval(tick,30);}};\n";
    }
}
