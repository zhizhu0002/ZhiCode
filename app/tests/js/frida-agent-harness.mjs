#!/usr/bin/env node
//
// 内嵌 Frida 载荷的行为测试（真跑，不是读源码做字符串匹配）。
//
// 为什么需要它：载荷是 Gadget 在 guest 进程里执行的 QJS 脚本，它此前**唯一的守卫**
// 是三个 Java 结构测试里的逐字拼写断言，例如
//
//     require(bridge.contains("boundedInteger(p.chunk_size,4194304,65536,8388608)"), ...)
//
// 那种断言等于「不许改」—— 改一个空格就红，写出等价但不同的表达也红，而真正的
// 行为漂移（少重叠一个字节、某块失败后整条命令失败、上限停止条件反了）它一个也拦不住。
// 本文件把载荷从 Java 源码里**抽出来真加载**，用桩替换 Frida 的宿主对象，
// 然后按它自己的信箱协议发命令、读响应，对返回值做断言。
//
// 两个刻意的设计：
//   1. 断言只读**响应 JSON**，不读源码文本 —— 所以载荷换成任何等价写法都不会误报；
//   2. 走真实路径：写 command.json → 触发 tick → 读 response-<id>.json。
//      连「ready 标记」「按 id 去重」「op 名与响应字段名」这些跨语言契约也一起被覆盖。
//
// 局限（不夸大，别把它当运行时验证）：桩只能验证**载荷自己**的逻辑。
// 真实的 Gadget 载入、Interceptor/Stalker 钩子、真实 Memory.scan 在真机/沙箱里的行为
// 这里一律证明不了；那些要在 IQ 沙箱里跑起来才能确认。
// 另外 「override/unreadable」这类条件是由桩**模拟**出来的（见 makeEnv 的注释），
// 它们是「按载荷的代码路径推出来的场景」，不是实测到的设备行为。
//
// 用法: node app/tests/js/frida-agent-harness.mjs [工程根]
//
import {readFileSync} from 'node:fs';
import {createContext, runInContext} from 'node:vm';
import path from 'node:path';
import process from 'node:process';

// ---------------------------------------------------------------- 断言

let passed = 0;
const failures = [];

function ok(cond, message) {
    if (cond) { passed++; return true; }
    failures.push(message);
    return false;
}

function eq(actual, expected, message) {
    const same = Object.is(actual, expected)
        || JSON.stringify(actual) === JSON.stringify(expected);
    return ok(same, message + '：期望 ' + JSON.stringify(expected) + '，实际 ' + JSON.stringify(actual));
}

// ------------------------------------------------- 从 Java 源码抽出载荷
//
// 载荷在 Java 里是**一份自己写的 text block**（`return """ … """.replace("__BASE__", base)`）;
// 这里按 text block 的规则还原它。支持两种形态：text block（当前）与字符串拼接（旧写法）——
// 后者留着是因为「载荷以什么形式嵌进 Java」是实现的自由，不是这份契约；
// 但**载荷内容必须只有一份**：从源码抽而不是复制一份，复制一份就变成
// 「测试另一个人写的副本」，源码改了测试不会红 —— 那正是本仓库反复记过的错误。
export function extractPayload(javaPath, methodName) {
    const src = readFileSync(javaPath, 'utf8');
    const start = src.indexOf('private static String ' + methodName + '(');
    if (start < 0) throw new Error('找不到方法: ' + methodName);

    const blockAt = src.indexOf('return """', start);
    const literalAt = src.indexOf('return "', start);
    if (blockAt >= 0 && (literalAt < 0 || blockAt <= literalAt)) {
        return extractTextBlock(src, blockAt + 'return '.length);
    }
    return extractConcatenation(src, literalAt);
}

/**
 * 还原 text block 的内容。
 *
 * 两步：按**最小缩进**（Java 的 incidental whitespace）脱缩进，再把 `\\` 还原成 `\`。
 * 少了第一步，JS 源码会带着 16 个空格的缩进 —— 那样 `return """` 里的内容照样能跑，
 * 但每一行都不等于源文件里的那一行，抽出来比对就没有意义了。
 */
function extractTextBlock(src, at) {
    const open = src.indexOf('"""', at) + 3;
    if (open < 3) throw new Error('text block 的起始 """ 没找到');
    const close = src.indexOf('"""', open);
    if (close < 0) throw new Error('text block 的结束 """ 没找到');
    let body = src.slice(open, close);
    if (body.startsWith('\r\n')) body = body.slice(2);
    else if (body.startsWith('\n')) body = body.slice(1);
    const lines = body.split('\n');
    const widths = lines.filter((l) => l.trim().length > 0)
        .map((l) => l.length - l.trimStart().length);
    const strip = widths.length ? Math.min(...widths) : 0;
    return lines.map((l) => (l.trim().length ? l.slice(strip) : '')).join('\n')
        .replace(/\\\\/g, '\\');
}

/** 旧的拼接写法：`return "…" + "…" + base + "…";`，变量留成 `__VAR_name__` 占位。 */
function extractConcatenation(src, ret) {
    if (ret < 0) throw new Error('找不到 return 的字面量');
    let i = ret + 'return '.length;
    let out = '';
    while (i < src.length) {
        const ch = src[i];
        if (/\s/.test(ch)) { i++; continue; }
        if (ch === '+') { i++; continue; }
        if (ch === ';') break;
        if (ch === '"') {
            i++;
            while (i < src.length) {
                const c = src[i];
                if (c === '\\') {
                    const n = src[i + 1];
                    out += n === 'n' ? '\n' : n === 'r' ? '\r' : n === 't' ? '\t'
                        : n === '"' ? '"' : n === '\\' ? '\\' : n;
                    i += 2;
                    continue;
                }
                if (c === '"') { i++; break; }
                out += c;
                i++;
            }
            continue;
        }
        const m = /^[A-Za-z_][A-Za-z0-9_]*/.exec(src.slice(i));
        if (m) { out += '__VAR_' + m[0] + '__'; i += m[0].length; continue; }
        throw new Error('拼接里出现了没处理的东西: ' + JSON.stringify(src.slice(i, i + 24)));
    }
    return out;
}
// ------------------------------------------------------------ Frida 桩
//
// 桩只实现载荷真正用到的那部分 API（其余不实现，用到就会报错 —— 这比默默返回
// undefined 好：那样错会以「结果不对」的形式出现，而不是以「测试不会红」的形式）。
//
// 注意这里返回的是一个**可变**的 env 对象，桩内部一律通过 `env.xxx` 读它。
// 一开始写成 `{...state, globals}` 的拷贝，结果测试里 `env.failBlocks = …`
// 打不到闭包看见的那份，用例就静默退化成「没有失败块」—— 断言全绿但什么都没测。
function makeEnv(opts = {}) {
    const files = new Map();          // 虚拟文件系统，路径 → 文本
    const timers = new Map();         // setInterval 的 id → 回调
    const intervals = [];
    const events = [];                // 记录 detachAll 之类的调用，供断言
    const modules = new Map();
    for (const [name, spec] of Object.entries(opts.modules || {})) {
        modules.set(name, {name, base: BigInt(spec.base), size: spec.size, path: spec.path || name});
    }
    const ranges = (opts.ranges || []).map((r) => ({
        base: BigInt(r.base), size: r.size, protection: r.protection || 'r--',
        content: r.content || Buffer.alloc(r.size),
    }));

    let scanCalls = 0;
    const env = {
        files, intervals, events, modules, ranges,
        // 下面几个是测试可以随时改的模拟条件（改的是同一份，桩读的就是它）
        scanDelayMs: opts.scanDelayMs || 0,
        failBlocks: opts.failBlocks || null,
        onBlock: opts.onBlock || null,
        reportWholeSlice: !!opts.reportWholeSlice,
        getScanCalls: () => scanCalls,
        resetScanCalls: () => { scanCalls = 0; },
        globals: null,                // 下面填
    };

    const hexOf = (v) => '0x' + v.toString(16);

    // 指针之所以要是一个**具名构造器**，是因为载荷的 jsonSafe 按
    // `v.constructor.name==='NativePointer'` 判断「这是个指针，转成地址字符串」。
    // 用普通对象字面量的话，它会被当成普通对象序列化成 {__addr:"4096"}
    // —— 工具拿到的地址就不是地址了（这里第一次跑就撞上了）。
    function NativePointer(value) {
        this.__addr = typeof value === 'bigint' ? value : BigInt(value);
    }
    NativePointer.prototype.toString = function () { return hexOf(this.__addr); };
    NativePointer.prototype.add = function (n) {
        return new NativePointer(this.__addr + BigInt(typeof n === 'bigint' ? n : Number(n)));
    };
    NativePointer.prototype.sub = function (n) {
        return new NativePointer(this.__addr - BigInt(typeof n === 'bigint' ? n : Number(n)));
    };
    NativePointer.prototype.compare = function (o) {
        return this.__addr < o.__addr ? -1 : this.__addr > o.__addr ? 1 : 0;
    };
    NativePointer.prototype.isNull = function () { return this.__addr === 0n; };
    NativePointer.prototype.readVolatile = function (n) { return readBytes(this.__addr, n); };
    NativePointer.prototype.readByteArray = function (n) { return readBytes(this.__addr, n); };
    NativePointer.prototype.writeVolatile = function (b) { writeBytes(this.__addr, b); };
    NativePointer.prototype.writeByteArray = function (b) { writeBytes(this.__addr, b); };

    const makePtr = (v) => new NativePointer(v);

    function findRange(addr, size) {
        return ranges.find((r) => addr >= r.base && addr + BigInt(size) <= r.base + BigInt(r.size));
    }

    function readBytes(addr, size) {
        const r = findRange(addr, size);
        if (!r) throw new Error('read outside readable range at ' + hexOf(addr));
        const off = Number(addr - r.base);
        // 必须按 byteOffset/byteLength 切：直接取 `.buffer` 拿到的是**整块**底层内存
        // （Buffer 的 pool / 整段 alloc），于是「读 4 字节」会返回 65536 字节。
        const view = r.content.subarray(off, off + size);
        return view.buffer.slice(view.byteOffset, view.byteOffset + view.byteLength);
    }

    function writeBytes(addr, buf) {
        const bytes = buf instanceof ArrayBuffer ? new Uint8Array(buf) : buf;
        const r = findRange(addr, BigInt(bytes.byteLength));
        if (!r) throw new Error('write outside readable range at ' + hexOf(addr));
        Buffer.from(bytes).copy(r.content, Number(addr - r.base));
    }

    function decodePattern(pattern) {
        const [bytePart, maskPart] = String(pattern).split(':');
        const hexBytes = bytePart.trim().split(/\s+/);
        return hexBytes.map((h, i) => {
            const mask = maskPart ? maskPart.trim().split(/\s+/)[i] : 'ff';
            return {value: parseInt(h, 16), mask: parseInt(mask, 16)};
        });
    }

    function matchesAt(content, off, bytes) {
        for (let i = 0; i < bytes.length; i++) {
            const actual = content[off + i];
            if ((actual & bytes[i].mask) !== (bytes[i].value & bytes[i].mask)) return false;
        }
        return true;
    }

    const Memory = {
        queryProtection: (a) => (findRange(a.__addr, 1) ? 'r--' : '---'),
        protect: () => true,
        patchCode: (a, len, fn) => { fn({writeByteArray: (b) => writeBytes(a.__addr, b)}); },
        scan: (base, size, pattern, callbacks) => {
            const call = scanCalls++;
            const b = BigInt(base.__addr ?? base);
            const n = Number(size);
            const fire = () => {
                try {
                    if (env.onBlock) env.onBlock(call, {base: b, size: n});
                    if (env.failBlocks && env.failBlocks.has(call)) {
                        callbacks.onError('access violation at ' + hexOf(b));
                        return;
                    }
                    const bytes = decodePattern(pattern);
                    // reportWholeSlice 模拟「同一片内存被重复上报」：真实设备上 range
                    // 刷新/coalesce 之后会看到同一地址被报两次。载荷按 address:size 去重。
                    const holder = env.reportWholeSlice
                        ? ranges.find((r) => b >= r.base && b < r.base + BigInt(r.size))
                        : null;
                    const from = holder ? holder.base : b;
                    const to = holder ? holder.base + BigInt(holder.size) : b + BigInt(n);
                    const r = findRange(from, Number(to - from));
                    if (!r) {
                        callbacks.onError('not fully readable at ' + hexOf(from));
                        return;
                    }
                    const start = Number(from - r.base);
                    const end = Number(to - r.base);
                    let stopped = false;
                    for (let off = start; off + bytes.length <= end && !stopped; off++) {
                        if (!matchesAt(r.content, off, bytes)) continue;
                        if (callbacks.onMatch(makePtr(r.base + BigInt(off)), bytes.length) === 'stop') stopped = true;
                    }
                    callbacks.onComplete();
                } catch (error) {
                    callbacks.onError(String(error && error.message ? error.message : error));
                }
            };
            if (env.scanDelayMs > 0) setTimeout(fire, env.scanDelayMs); else setImmediate(fire);
        },
    };

    const Process = {
        id: 4242,
        arch: 'arm64',
        platform: 'linux',
        pageSize: 4096,
        enumerateModules: () => [...modules.values()].map((m) => ({
            name: m.name, base: makePtr(m.base), size: m.size, path: m.path,
        })),
        enumerateRanges: () => ranges
            .map((r) => ({base: makePtr(r.base), size: r.size, protection: r.protection, file: null})),
        getModuleByName: (name) => {
            const m = modules.get(name);
            if (!m) throw new Error('module not found: ' + name);
            return {name: m.name, base: makePtr(m.base), size: m.size, path: m.path};
        },
    };

    const Module = {
        findGlobalExportByName: (name) => (name === 'missing_export' ? null : makePtr(0x7f000000n)),
    };

    const Interceptor = {detachAll: () => { events.push('detachAll'); }};
    const Stalker = {};

    function FileStub(p, mode) {
        this.path = String(p);
        this.mode = mode;
    }
    FileStub.prototype.write = function (chunk) {
        files.set(this.path, (files.get(this.path) || '') + String(chunk));
    };
    FileStub.prototype.flush = function () {};
    FileStub.prototype.close = function () {};
    FileStub.writeAllText = (p, text) => { files.set(String(p), String(text)); };
    FileStub.readAllText = (p) => {
        const key = String(p);
        if (!files.has(key)) throw new Error('no such file: ' + key);
        return files.get(key);
    };

    env.globals = {
        rpc: {exports: {}},
        File: FileStub,
        Process,
        Module,
        Memory,
        Interceptor,
        Stalker,
        Thread: {},
        MemoryAccessMonitor: {},
        DebugSymbol: {},
        Backtracer: {},
        NativeFunction: function () {},
        NativeCallback: function () {},
        CModule: function () {},
        ptr: (v) => makePtr(typeof v === 'string' ? BigInt(v) : v),
        setInterval: (fn) => { const id = intervals.length; intervals.push(fn); return id; },
        clearInterval: (id) => { timers.delete(id); },
        setImmediate,
        setTimeout,
        clearTimeout,
        console,
    };
    return env;
}

// ------------------------------------------------- 在桩里加载载荷并驱动它

const READY = 'ready.json';
const CMD = 'command.json';

function boot(javaFile, env) {
    const payload = extractPayload(javaFile, 'agentScript')
        .replace('__BASE__', '/guest/session')
        .replace('__VAR_base__', '/guest/session');
    const context = createContext(env.globals);
    // 载荷的结构：rpc.exports = { init(){ ……全部实现…… } }。加载只是定义它，
    // init() 才会写 ready 标记并装上信箱轮询 —— 与 Gadget 的行为一致。
    runInContext(payload, context, {filename: 'zhi-agent.js'});
    ok(typeof env.globals.rpc.exports.init === 'function',
        '载荷必须通过 rpc.exports.init 初始化（Gadget Script 模式靠它拉起）');
    env.globals.rpc.exports.init();
    ok(env.files.get('/guest/session/' + READY) !== undefined,
        'init 结束必须写 ready 标记，否则 Java 侧会当成「脚本没跑起来」');
    ok(env.intervals.length === 1,
        'init 必须留下且只留下一个信箱轮询定时器（否则每 tick 会重复消费命令）');
    return {tick: env.intervals[0]};
}

let seq = 0;

/** 把一条命令放进信箱并踢一次轮询。 */
function post(env, tick, id, op, payload) {
    env.files.set('/guest/session/' + CMD, JSON.stringify({id, op, payload: payload || {}}));
    tick();
}

/** 让出若干轮事件循环 —— 载荷内部的 Promise 链靠它推进。 */
async function settle(turns = 8) {
    for (let i = 0; i < turns; i++) await new Promise((r) => setImmediate(r));
}

/**
 * 等某个响应文件出现；超时返回 null（由调用方断言），不抛错。
 *
 * 必须按**真实时间**等，不能只数「让出事件循环的轮数」：`setImmediate` 一轮只有微秒级，
 * 而有些场景（超时、分块延迟）要真的过几百毫秒才会出结果 —— 只数轮数会让长命令
 * 被误判成「没有响应」（这里踩过一次：超时用例其实正常返回了 complete）。
 */
async function awaitResponse(env, id, maxMs = 5000) {
    const responsePath = '/guest/session/response-' + id + '.json';
    const deadline = Date.now() + maxMs;
    for (;;) {
        if (env.files.has(responsePath)) return env.files.get(responsePath);
        if (Date.now() >= deadline) return null;
        await new Promise((r) => setImmediate(r));
    }
}

/** 按信箱协议发一条命令并等它的响应文件。 */
async function send(env, tick, op, payload, options = {}) {
    const id = options.id || 'auto-' + (++seq);
    post(env, tick, id, op, payload);
    const text = await awaitResponse(env, id, options.maxMs || 5000);
    if (text === null) {
        throw new Error('命令没有响应（' + op + '）；当前虚拟文件: '
            + [...env.files.keys()].join(', ') + '；Memory.scan 调用次数: '
            + env.getScanCalls() + '；events: '
            + String(env.files.get('/guest/session/events.log')));
    }
    const reply = JSON.parse(text);
    if (!options.keepResponse) env.files.delete('/guest/session/response-' + id + '.json');
    return reply;
}

/** 发一条命令并断言它成功，返回 result。 */
async function ask(env, tick, op, payload, note) {
    const reply = await send(env, tick, op, payload);
    if (!ok(reply.ok === true, (note || op) + ' 应当成功，实际 ' + JSON.stringify(reply).slice(0, 200))) {
        return null;
    }
    eq(reply.id !== undefined, true, (note || op) + ' 的响应必须带回请求 id（并发命令靠它区分）');
    return reply.result;
}

/** 用 eval 在载荷内部跑一段脚本并取回值 —— 纯函数只能用这个口子验证。 */
async function evalIn(env, tick, source, note) {
    const label = note || 'eval';
    const reply = await send(env, tick, 'eval', {script: source, timeout_ms: 5000});
    // eval 失败必须**抛错**而不是返回 null：返回 null 只会在下游变成一串
    // 「期望 X，实际 null」的噪声，看不出真正的原因（这里踩过一次：
    // 桩漏了一个全局名，结果所有 eval 断言都在报无效值）。
    if (reply.ok !== true) {
        throw new Error(label + ' 的 eval 失败: ' + String(reply.error).split('\n')[0]);
    }
    return reply.result === undefined ? null : reply.result.value;
}

// ---------------------------------------------------------------- 场景

function moduleSpec() {
    return {
        modules: {'libtarget.so': {base: 0x10000000, size: 0x10000, path: '/data/app/libtarget.so'}},
        ranges: [{
            base: 0x10000000,
            size: 0x10000,
            content: Buffer.alloc(0x10000),
        }],
    };
}

/** 在一片内存里放若干个 'DE AD' 匹配，用于验证命中数、上限与去重。 */
function plant(env, offsets, pattern = [0xde, 0xad]) {
    const r = env.ranges[0];
    for (const off of offsets) {
        r.content[off] = pattern[0];
        r.content[off + 1] = pattern[1];
    }
}

const PATTERN = 'de ad';

async function testContract(env, tick) {
    // ping / modules / ranges：op 名与响应字段名是 Java 侧按名解析的契约。
    const ping = await ask(env, tick, 'ping', {}, 'ping');
    eq(ping && ping.pid, 4242, 'ping 必须返回当前 pid');
    eq(ping && ping.arch, 'arm64', 'ping 必须返回 arch');
    eq(ping && ping.page_size, 4096, 'ping 必须返回 page_size（Java 侧用它算默认长度）');

    const mods = await ask(env, tick, 'modules', {}, 'modules');
    ok(Array.isArray(mods) && mods.length === 1, 'modules 必须返回数组');
    eq(mods && mods[0].base, '0x10000000', 'modules 的 base 必须是十六进制字符串');
    eq(mods && mods[0].name, 'libtarget.so', 'modules 必须带上 name');

    const rangeList = await ask(env, tick, 'ranges', {}, 'ranges');
    ok(Array.isArray(rangeList) && rangeList.length === 1, 'ranges 必须返回数组');
    eq(rangeList && rangeList[0].protection, 'r--', 'ranges 必须带上 protection');

    // read / write 的往返，以及 volatile 默认值。
    const written = await ask(env, tick, 'write',
        {address: '0x10000010', data: '41424344'}, 'write');
    eq(written && written.written, 4, 'write 必须返回写入字节数（不是「成功」布尔）');
    const read = await ask(env, tick, 'read',
        {address: '0x10000010', size: 4}, 'read');
    eq(read && read.hex, '41424344', 'read 必须按 hex 返回读到的字节');

    // export：找不到的导出要返回 address:null，而不是抛错（宿主会把它当成「没有这个符号」）。
    const found = await ask(env, tick, 'export', {name: 'some_symbol'}, 'export');
    eq(found && found.address, '0x7f000000', 'export 必须返回符号地址字符串');
    const missing = await ask(env, tick, 'export', {name: 'missing_export'}, 'export(缺失)');
    eq(missing && missing.address, null, '找不到的导出必须返回 address:null');

    // watch 三兄弟：start/stop/stop_all 的返回字段名。
    const w1 = await ask(env, tick, 'watch_start',
        {watch_id: 'w1', address: '0x10000010', size: 4, interval_ms: 20}, 'watch_start');
    eq(w1 && w1.watch_id, 'w1', 'watch_start 必须回显 watch_id');
    const w2 = await ask(env, tick, 'watch_start',
        {address: '0x10000020', size: 4, interval_ms: 20}, 'watch_start(自动 id)');
    ok(w2 && typeof w2.watch_id === 'string' && w2.watch_id.startsWith('watch-'),
        'watch_start 未给 id 时必须自己生成一个（Java 侧用它去 stop）');
    const stopped = await ask(env, tick, 'watch_stop', {watch_id: 'w1'}, 'watch_stop');
    eq(stopped && stopped.stopped, true, 'watch_stop 必须报告真的停掉了一个');
    const stoppedAll = await ask(env, tick, 'watch_stop_all', {}, 'watch_stop_all');
    eq(stoppedAll && stoppedAll.stopped, 1, 'watch_stop_all 必须报告停掉的个数');

    // hook_detach_all 必须连 Interceptor 全局表一起清。
    const detached = await ask(env, tick, 'hook_detach_all', {}, 'hook_detach_all');
    eq(detached && detached.detached, true, 'hook_detach_all 必须返回 detached:true');
    ok(env.events.includes('detachAll'),
        'hook_detach_all 必须同时调用 Interceptor.detachAll（只清自己的 Map 会漏掉 eval 里注册的钩子）');

    // 未知 op：必须是 ok:false + error，而不是静默成功。
    const unknown = await send(env, tick, 'no_such_op', {});
    eq(unknown.ok, false, '未知 op 必须返回 ok:false');
    ok(String(unknown.error).includes('no_such_op'),
        '未知 op 的 error 里必须带上 op 名（否则报错时看不出是哪个命令）');

    // 同一个 id 只执行一次 —— 这是「幽灵命令」的守卫：进程退出时可能留下一个没被消费的
    // command.json，脚本重启后会读到它；而 Java 侧一次超时重试也会重新投递。
    // 断言的是「不重复执行」，**不是**「再投一次会再回一个响应」：
    // 脚本按 id 去重，第二次投递会被整个无视（生产路径上 id 唯一，所以这是刻意的）。
    const reply = await send(env, tick, 'scan',
        {address: '0x10000000', size: 16, pattern: PATTERN}, {id: 'dup-scan'});
    ok(reply.ok === true, '第一次 dup-scan 应当成功');
    const callsAfterFirst = env.getScanCalls();
    post(env, tick, 'dup-scan', 'scan', {address: '0x10000000', size: 16, pattern: PATTERN});
    await settle();
    eq(env.getScanCalls(), callsAfterFirst, '同一个 id 重复投递不得再次执行（一次超时重试会变成两次插桩）');
    ok(!env.files.has('/guest/session/response-dup-scan.json'),
        '同一个 id 重复投递不得再写一个响应（否则 Java 侧会读到过期结果）');
}

/** 并行性：慢扫描进行中，ping 必须立刻回来（没有全局忙位）。 */
async function testNoGlobalBusyGate() {
    const spec = moduleSpec();
    spec.ranges = [{base: 0x10000000, size: 0x40000, content: Buffer.alloc(0x40000)}];
    const env = makeEnv({...spec, scanDelayMs: 40});
    const {tick} = boot(JAVA_FILE, env);
    for (let i = 0; i < 2048; i += 7) plant(env, [i]);
    post(env, tick, 'slow-1', 'scan',
        {address: '0x10000000', size: 0x40000, pattern: PATTERN, chunk_size: 65536});
    post(env, tick, 'fast-1', 'ping', {});
    const fast = await awaitResponse(env, 'fast-1', 200);
    ok(fast !== null, '慢扫描还在跑时，ping 必须能得到响应（脚本里不能有全局忙位）');
    if (fast !== null) eq(JSON.parse(fast).result.pid, 4242, '并行时 ping 的响应必须是对的');
    ok(!env.files.has('/guest/session/response-slow-1.json'),
        '短命令不该等长命令跑完（那正是「长扫描把整条队列串起来」的病症）');
    const slow = await awaitResponse(env, 'slow-1', 40000);
    ok(slow !== null, '慢扫描最终必须给出响应');
}

async function testPureLogic(env, tick) {
    // jsonSafe：这个函数决定「工具看到什么」，它出错的形态是「看到 [object Object]」
    // 或「整个响应因为一个循环引用而失败」。
    const circular = await evalIn(env, tick, 'const o={a:1}; o.self=o; return o;', 'jsonSafe(循环引用)');
    eq(circular && circular.self, '[circular]', '循环引用必须序列化成 [circular] 而不是抛错');

    const deep = await evalIn(env, tick,
        'let o={}; let cur=o; for(let i=0;i<12;i++){cur.next={}; cur=cur.next;} return o;',
        'jsonSafe(深嵌套)');
    let cursor = deep;
    for (let i = 0; i < 20 && cursor && cursor.next; i++) cursor = cursor.next;
    eq(cursor, '[depth-limit]', '超过深度上限必须截成 [depth-limit]（否则超大对象会拖死序列化）');

    const big = await evalIn(env, tick, 'return {s: "x".repeat(300000)};', 'jsonSafe(超长字符串)');
    ok(big && big.s.endsWith('…[truncated]'), '超长字符串必须截断并带标记');
    eq(big && big.s.length, 262144 + '…[truncated]'.length, '截断长度必须是 262144 + 标记');

    const bigintValue = await evalIn(env, tick, 'return {n: 7n};', 'jsonSafe(bigint)');
    eq(bigintValue && bigintValue.n, '7', 'bigint 必须转成字符串（JSON 没有 bigint）');

    // 数组截断：上限 2048 项，超出时追加一条 `[+N more]`，N 是**真实被截掉的条数**。
    //
    // 这一条曾经是错的，而且是这套测试的第一次运行抓出来的：旧实现在 eval 路径上
    // 把 jsonSafe 套了两次（`run` 里一次、`start` 里又一次），第二次看到的是已经被
    // 截断的 2049 项数组，于是算出 `2049 - 2048 = 1` —— 无论原始是 3000 项还是 5000 项，
    // 标记永远是 `[+1 more]`。原先的断言把这个错**钉成了契约**（写的是
    // `assert equals "[+1 more]"`），所以这里必须用两个不同的规模来断言：
    // 一个规模只能证明「有个数字」，两个规模才能证明「数字是对的」。
    for (const size of [3000, 5000]) {
        const arrayValue = await evalIn(env, tick, 'return {a: new Array(' + size + ').fill(1)};',
            'jsonSafe(超长数组 ' + size + ')');
        eq(arrayValue && arrayValue.a.length, 2049,
            '超长数组必须截到 2048 + 一条标记（否则一次 frida_read 就能撑爆上下文）');
        eq(arrayValue && arrayValue.a[2048], '[+' + (size - 2048) + ' more]',
            size + ' 项数组的标记必须报出真实的截断条数（' + size + ' − 2048）');
    }

    const ptrValue = await evalIn(env, tick, 'return {p: ptr("0x1000")};', 'jsonSafe(NativePointer)');
    eq(ptrValue && ptrValue.p, '0x1000', 'NativePointer 必须序列化成地址字符串');

    // rewriteLegacyScan：这是 eval 里最容易被误改的一处（模板字符串/嵌套括号/参数里的逗号）。
    const legacy = await evalIn(env, tick,
        'const r = await Memory.scanSync(ptr("0x10000000"), 64, "' + PATTERN + '");'
        + 'return r.length;', 'legacy scanSync 翻译');
    eq(legacy, 0, 'legacy scanSync 必须真的执行扫描（这里没有命中，应当返回空数组长度 0）');

    const legacyNoMatch = await evalIn(env, tick,
        'const r = await Memory.scanSync(ptr("0x10000000"), 64, "' + PATTERN + '");'
        + 'return {count: r.length, first: r.length ? r[0].address : null};',
        'legacy scanSync 结果形状');
    eq(legacyNoMatch && legacyNoMatch.count, 0, 'legacy scanSync 的返回值必须是命中数组');
    ok(legacyNoMatch && legacyNoMatch.first === null,
        'legacy scanSync 命中项必须带 address 字段（形状要与 Memory.scanSync 一致）');

    // 有命中时 legacy 路径必须把命中真的带回来（上面两条都在「空结果」上，容易假通过）。
    plant(env, [0x2000, 0x3000]);
    const legacyHit = await evalIn(env, tick,
        'const r = await Memory.scanSync(ptr("0x10000000"), 0x10000, "' + PATTERN + '");'
        + 'return {count: r.length, address: r.length ? r[0].address : null};',
        'legacy scanSync 命中');
    ok(legacyHit && legacyHit.count >= 1, 'legacy scanSync 有命中时必须把命中带回来');
    ok(legacyHit && typeof legacyHit.address === 'string',
        'legacy scanSync 的命中项必须带字符串形式的 address');

    // 参数里有逗号/字符串/嵌套括号时不能被改写坏。这里用一个会把
    // 错误参数传给 scanSync 的写法：若重写器把参数切错，它会抛别的错。
    const nested = await evalIn(env, tick,
        'const fn = (a, b) => b;'
        + 'const n = fn(1, 2);'
        + 'const m = fn((1,2), [3,4].length);'
        + 'return n + m;', '重写器不得误伤普通调用');
    eq(nested, 2 + 2, '没有 scanSync 的代码必须原样执行（重写器只动 scanSync 调用）');

    // boundedInteger 的上下限：分块大小与命中上限都有硬边界。
    const chunkLow = await evalIn(env, tick,
        'const r = await Zhi.scan({address:"0x10000000", size: 64, pattern:"' + PATTERN + '", chunk_size: 1});'
        + 'return r.chunk_size;', 'chunk_size 下限');
    eq(chunkLow, 65536, 'chunk_size 低于 64 KiB 必须被抬到 64 KiB');

    const maxHigh = await evalIn(env, tick,
        'const r = await Zhi.scan({address:"0x10000000", size: 64, pattern:"' + PATTERN + '", max: 999999});'
        + 'return r.max_matches;', 'max 上限');
    eq(maxHigh, 2048, 'max 超过 2048 必须被压到 2048（硬上限）');

    // 非法 pattern 必须报错，而不是当成「没有命中」。
    // 掩码那条用 `de ad:ff`：`de:ff` 其实是**合法**的（单字节 + 单字节掩码）。
    for (const [bad, why] of [['de a', '奇数长度'], ['zz', '非法字符'],
                              ['de ad:ff', '掩码长度与字节数不符']]) {
        const reply = await send(env, tick, 'scan',
            {address: '0x10000000', size: 64, pattern: bad});
        eq(reply.ok, false, '非法 pattern（' + why + '）必须失败，不能静默返回 0 个命中');
    }
}

async function testScanner() {
    // ---- 正常扫描：命中数、分块覆盖、complete 与 stop_reason
    {
        const env = makeEnv(moduleSpec());
        const {tick} = boot(JAVA_FILE, env);
        plant(env, [0x100, 0x180, 0x1000]);
        const r = await ask(env, tick, 'scan',
            {address: '0x10000000', size: 0x10000, pattern: PATTERN, chunk_size: 65536}, 'scan(正常)');
        eq(r && r.match_count, 3, 'scan 必须报出全部命中');
        eq(r && r.complete, true, '完整扫完必须报告 complete:true');
        eq(r && r.stop_reason, 'complete', '完整扫完的 stop_reason 必须是 complete');
        eq(r && r.chunk_overlap, PATTERN.split(' ').length - 1, 'chunk_overlap 必须等于 pattern 长度 - 1');
        eq(r && r.scanned, r.size, '扫完时 scanned 必须等于区间大小（重叠不得虚增）');
        ok(r && r.attempted >= r.size,
            'attempted 必须 ≥ 区间大小（重叠会让它略大，那是预期的）');
        eq(r && r.error_count, 0, '正常扫描不该有错误');
        eq(r && r.duplicate_matches, 0, '不重叠上报时不该有重复命中');
        eq(r && r.matches.length, r.match_count, 'matches 数组长度必须与 match_count 一致');
        eq(r && r.matches[0].address, '0x10000100', '命中地址必须是十六进制字符串');
    }

    // ---- 多块扫描：重叠字节不得被算进 scanned
    //     这条单独列出来，是因为**只有一块**时 advance 与 part 恰好相等，
    //     于是「scanned 累加的是 advance（真实推进）还是 part（含重叠）」改错了也不会红
    //     —— 变异测试证实过：把 scanned+=advance 改成 scanned+=part，上面那条正常扫描
    //     用例照样全绿。三块以上才会暴露。
    {
        const spec = moduleSpec();
        spec.ranges = [{base: 0x10000000, size: 0x30000, content: Buffer.alloc(0x30000)}];
        const env = makeEnv(spec);
        const {tick} = boot(JAVA_FILE, env);
        const r = await ask(env, tick, 'scan',
            {address: '0x10000000', size: 0x30000, pattern: PATTERN, chunk_size: 65536}, 'scan(多块)');
        eq(r && r.scanned, 0x30000,
            '多块扫描时 scanned 必须等于区间大小（重叠只算一次，否则「扫了多少」会虚高）');
        ok(r && r.attempted > r.size,
            'attempted 应当略大于区间大小 —— 那正是重叠的部分，两个数必须是不同的东西');
        eq(r && r.complete, true, '多块扫完仍要报告 complete:true');
    }

    // ---- 命中上限：必须停下、报告 max_matches、且不多扫
    {
        const env = makeEnv(moduleSpec());
        const {tick} = boot(JAVA_FILE, env);
        plant(env, [0x100, 0x110, 0x120, 0x130, 0x140]);
        const r = await ask(env, tick, 'scan',
            {address: '0x10000000', size: 0x10000, pattern: PATTERN, max: 2}, 'scan(上限)');
        eq(r && r.matches.length, 2, '命中上限必须被遵守（多扫就是把结果丢掉，白烧时间）');
        eq(r && r.stop_reason, 'max_matches', '达到上限时 stop_reason 必须是 max_matches');
        eq(r && r.complete, false, '达到上限时不能报告 complete:true');
    }

    // ---- 某一块读不了：必须返回部分结果，而不是整条命令失败
    //     必须有**两个以上**分块，否则「第 2 块失败」这个场景根本不会发生
    //     （区间只够一块时，failBlocks 里写什么都不会触发 —— 这样的用例是假绿）。
    {
        const spec = moduleSpec();
        spec.ranges = [{base: 0x10000000, size: 0x20000, content: Buffer.alloc(0x20000)}];
        const env = makeEnv(spec);
        const {tick} = boot(JAVA_FILE, env);
        plant(env, [0x100, 0x4000]);      // 都在第 1 块里
        env.failBlocks = new Set([1]);    // 第 2 块模拟「枚举时还在、扫到时已经不可读」
        const r = await ask(env, tick, 'scan',
            {address: '0x10000000', size: 0x20000, pattern: PATTERN, chunk_size: 65536}, 'scan(部分失败)');
        ok(r && r.error_count >= 1, '有一块失败时必须记进 error_count');
        eq(r && r.stop_reason, 'scan_errors', '有失败块且扫完时 stop_reason 必须是 scan_errors');
        eq(r && r.complete, false, '有失败块时不能报告 complete:true');
        ok(r && r.failed_size > 0, 'failed_size 必须记录失败覆盖的字节数');
        ok(r && r.matches.length >= 2,
            '失败块之外的命中必须照样返回（一块读不了不该让整条命令失败）');
        ok(r && r.errors.length >= 1 && typeof r.errors[0].address === 'string',
            'errors 里必须带失败块的地址（否则 Agent 无法判断是哪一段读不到）');
    }

    // ---- 不可读空洞：必须跳过并计入 skipped
    {
        const spec = moduleSpec();
        spec.ranges = [
            {base: 0x10000000, size: 0x2000, content: Buffer.alloc(0x2000)},
            {base: 0x10004000, size: 0x2000, content: Buffer.alloc(0x2000)},
        ];
        const env = makeEnv(spec);
        const {tick} = boot(JAVA_FILE, env);
        const r = await ask(env, tick, 'scan',
            {address: '0x10000000', size: 0x8000, pattern: PATTERN}, 'scan(空洞)');
        eq(r && r.skipped, 0x8000 - 0x4000, '不可读空洞必须计入 skipped');
        eq(r && r.scanned, 0x4000, '只有可读的两段该被算进 scanned');
        eq(r && r.complete, true, '空洞不是错误：扫完仍应 complete:true');
    }

    // ---- 重复上报同一片区域：必须去重
    //     桩模拟的是「range 刷新后同一块内存被再次扫到」——真实设备上 Frida 在
    //     coalesce 的区间里会这样报。载荷按 address:size 去重，这里验证它确实去重。
    //     同样要有**两个以上**分块：每块都回报整片内存，才会真的出现重复。
    {
        const spec = moduleSpec();
        spec.ranges = [{base: 0x10000000, size: 0x20000, content: Buffer.alloc(0x20000)}];
        const env = makeEnv({...spec, reportWholeSlice: true});
        const {tick} = boot(JAVA_FILE, env);
        plant(env, [0x100, 0x110]);
        const r = await ask(env, tick, 'scan',
            {address: '0x10000000', size: 0x20000, pattern: PATTERN, chunk_size: 65536}, 'scan(重复上报)');
        eq(r && r.match_count, 2, '同一地址被重复报出时必须去重（否则命中数会被放大）');
        ok(r && r.duplicate_matches > 0, '去重掉的次数必须记进 duplicate_matches');
    }

    // ---- 扫描期间模块基址变了：必须停下并报告 mapping_changed
    //     注意：module 与 address/size 互斥，给 module 时**不能**再给 size
    //     （载荷会直接报错 —— 那是故意的，含糊的目标比报错更危险）。
    //     还要有**两块以上**：基址在第 1 块扫描途中变掉，第 2 块开始前的
    //     moduleUnchanged 检查才发现 —— 只有一块时循环直接结束，永远看不到这个分支。
    {
        const spec = moduleSpec();
        spec.modules['libtarget.so'].size = 0x20000;
        spec.ranges = [{base: 0x10000000, size: 0x20000, content: Buffer.alloc(0x20000)}];
        const env = makeEnv(spec);
        const {tick} = boot(JAVA_FILE, env);
        env.onBlock = (call) => { if (call === 0) env.modules.get('libtarget.so').base = 0x20000000n; };
        const r = await ask(env, tick, 'scan',
            {module: 'libtarget.so', pattern: PATTERN, chunk_size: 65536},
            'scan(映射变化)');
        eq(r && r.stop_reason, 'mapping_changed',
            '模块被重新加载时必须停下（继续按旧基址扫会扫到错的地址）');
        eq(r && r.complete, false, '映射变化时不能报告 complete:true');
    }

    // ---- 超时：必须自己停下并报告 timeout
    //     分块延迟 90ms、16 个分块 —— 会真的超过 1000ms，所以这条用例必须按真实时间等。
    {
        const spec = moduleSpec();
        spec.ranges = [{base: 0x10000000, size: 0x100000, content: Buffer.alloc(0x100000)}];
        const env = makeEnv({...spec, scanDelayMs: 90});
        const {tick} = boot(JAVA_FILE, env);
        const r = await ask(env, tick, 'scan',
            {address: '0x10000000', size: 0x100000, pattern: PATTERN, chunk_size: 65536, timeout_ms: 1000},
            'scan(超时)');
        eq(r && r.stop_reason, 'timeout', '到时间必须自己停下并报告 timeout');
        eq(r && r.complete, false, '超时不能报告 complete:true');
        eq(r && r.timeout_ms, 1000, 'timeout_ms 必须回显实际使用的值');
        ok(r && r.scanned < r.size, '超时时必须报告「没扫完」（scanned 小于区间大小）');
    }

    // ---- module 与 address/size 互斥
    {
        const env = makeEnv(moduleSpec());
        const {tick} = boot(JAVA_FILE, env);
        const reply = await send(env, tick, 'scan',
            {module: 'libtarget.so', address: '0x10000000', size: 64, pattern: PATTERN});
        eq(reply.ok, false, '同时给 module 与 address/size 必须失败（含糊的目标比报错更危险）');
    }

    // ---- eval 的超时：脚本 await 住不放时，必须被上层 deadline 打断
    //
    // **不要**写成 `while(true){}` 来测这一条：JS 没有抢占，同步死循环会把整个
    // 事件循环卡死，setTimeout 永远轮不到 —— 这正是本 harness 第一次跑就撞上的事
    // （进程满载 spinning，既不返回也不报错）。那是载荷的一个**真实限制**：
    // frida_eval 的 timeout_ms 只能打断「会让出事件循环」的脚本
    // （`await`、`Promise`、分块扫描这些），同步死循环没有止损手段 ——
    // 除了杀掉 guest 进程。这里按真实的、可达成的那一半断言，并把这个限制留在注释里。
    //
    // ⚠️ 这里的 sleep 只要**比 deadline 长**就够了，**不要**写成 60000 这种数：
    // 它多验证不了任何东西（deadline 是 1000ms，2000ms 已经超过），但那个 timer 会
    // 一直挂在事件循环上、把 node 进程拖到它烧完才退出 —— 实测这一行让整套测试
    // 从 2s 变成 62s，成了整个套件的耗时大头（其余 32 条加起来才 10s）。
    // 断言的是"harness 提前返回"，不是"等得够久"，所以缩短它不损失任何覆盖。
    {
        const env = makeEnv(moduleSpec());
        const {tick} = boot(JAVA_FILE, env);
        const reply = await send(env, tick, 'eval',
            {script: 'await new Promise(r=>setTimeout(r, 2000)); return 1;', timeout_ms: 1000},
            {maxMs: 30000});
        eq(reply.ok, false, 'eval 里 await 住超过 deadline 时必须返回错误，而不是永远挂着');
        ok(String(reply.error).includes('deadline'),
            'eval 超时的错误里必须写明 deadline（否则看不出是超时还是脚本自己抛的）');
    }
}

async function testEvents(env, tick) {
    // Zhi.emit 是 Agent 读异步钩子事件的唯一通道：行格式（JSON + 换行）与字段名是契约。
    await evalIn(env, tick, 'Zhi.emit({type: "probe", n: 1}); return 1;', 'Zhi.emit');
    const text = env.files.get('/guest/session/events.log') || '';
    const lines = text.trim().split('\n').filter(Boolean);
    ok(lines.length >= 1, 'Zhi.emit 必须把事件追加进 events.log');
    const event = JSON.parse(lines[lines.length - 1]);
    eq(event.value && event.value.type, 'probe', '事件值必须原样序列化到 value 里');
    ok(typeof event.ts === 'number' && event.ts > 0, '每条事件必须带 ts（Java 侧按它排序/截尾）');
}

// ---------------------------------------------------------------- 入口

const PROJECT_ROOT = process.argv[2] ? path.resolve(process.argv[2]) : process.cwd();
const JAVA_FILE = path.join(PROJECT_ROOT,
    'app/src/main/java/com/zhizhu/zhicode/sandbox/SandboxFrida.java');

async function main() {
    const contractEnv = makeEnv(moduleSpec());
    const {tick} = boot(JAVA_FILE, contractEnv);

    await testContract(contractEnv, tick);
    await testPureLogic(contractEnv, tick);
    await testEvents(contractEnv, tick);
    await testScanner();
    await testNoGlobalBusyGate();

    if (failures.length > 0) {
        console.error('FridaAgentHarnessTest FAIL (' + failures.length + ' 项)');
        for (const f of failures) console.error('  ✗ ' + f);
        process.exit(1);
    }
    console.log('FridaAgentHarnessTest PASS (' + passed + ' 项断言)');
}

const IS_MAIN = process.argv[1] && process.argv[1].endsWith('frida-agent-harness.mjs');
if (IS_MAIN) {
    main().catch((error) => {
        console.error('FridaAgentHarnessTest FAIL（harness 自己出错）');
        console.error(error && error.stack ? error.stack : error);
        process.exit(1);
    });
}
