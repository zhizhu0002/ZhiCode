#!/usr/bin/env node
//
// 钉住一件事：**设备上真正执行的载荷**，与行为测试（frida-agent-harness.mjs）测的载荷
// 是同一份字节。
//
// 为什么需要这一条：行为测试自己实现了 Java text block 的还原规则（按最小缩进剥掉
// 附带的空白）。它一旦与真正的 Java 编译器不一致 —— 例如缩进剥多/剥少、转义处理不同 ——
// 那么那 160 多项断言测的就不是设备上跑的那份脚本，而且**不会有人发现**：
// 两边各自都「通过」。这是个纯本地、几秒钟就能消除的假设，不该留给真机去暴露。
//
// 做法：同一份源码，两条路径各抽一次，逐字节比对 ——
//   ① harness 的 extractPayload()（node，本文件 import 进来的那份实现）；
//   ② 真正的 Java：把那个 text block 原样抄进一个临时类里编译并运行，让它打印出来。
//
// 顺带说明为什么不用文本断言来代替：这里比对的是**两份抽取结果的字节**，
// 不是源码里有没有某个字符串 —— 后者正是这次重写要去掉的那类断言。
//
// 用法: node app/tests/js/frida-payload-embedding-check.mjs [工程根]
//
import {mkdtempSync, readFileSync, writeFileSync, rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {execFileSync} from 'node:child_process';
import path from 'node:path';
import process from 'node:process';

import {extractPayload} from './frida-agent-harness.mjs';

const root = process.argv[2] ? path.resolve(process.argv[2]) : process.cwd();
const source = path.join(root, 'app/src/main/java/com/zhizhu/zhicode/sandbox/SandboxFrida.java');

let failures = 0;

function fail(message) {
    failures++;
    console.error('  ✗ ' + message);
}

const java = readFileSync(source, 'utf8');
const open = java.indexOf('return """');
const close = open < 0 ? -1 : java.indexOf('"""', open + 10);
if (open < 0 || close < 0) {
    fail('SandboxFrida.java 里找不到载荷的 text block（嵌入形式变了？）');
    process.exit(1);
}

// ① harness 视角
const fromHarness = extractPayload(source, 'agentScript');

// ② Java 视角：把 text block 原样抄进一个临时类里运行
const work = mkdtempSync(path.join(tmpdir(), 'embed-check-'));
try {
    const block = java.slice(open + 'return '.length, close + 3);
    writeFileSync(path.join(work, 'EmbedCheck.java'),
        'public final class EmbedCheck {\n'
        + '    public static void main(String[] args) {\n'
        + '        System.out.print(' + block + ');\n'
        + '    }\n'
        + '}\n');
    let fromJava;
    try {
        fromJava = execFileSync('java', [path.join(work, 'EmbedCheck.java')],
            {encoding: 'utf8', timeout: 120000, maxBuffer: 32 * 1024 * 1024});
    } catch (error) {
        fail('无法用真正的 Java 抽这份载荷（编译或执行失败）：'
            + String(error && error.message ? error.message : error).split('\n')[0]);
        process.exit(1);
    }

    if (fromJava !== fromHarness) {
        fail('设备上执行的载荷与行为测试测的载荷**不是同一份字节** —— '
            + 'harness 的 text block 还原规则与 Java 编译器不一致。');
        const a = fromJava.split('\n');
        const b = fromHarness.split('\n');
        for (let i = 0; i < Math.max(a.length, b.length); i++) {
            if (a[i] !== b[i]) {
                console.error('    第一处不同在第 ' + (i + 1) + ' 行：');
                console.error('      Java  : ' + JSON.stringify(a[i]));
                console.error('      harness: ' + JSON.stringify(b[i]));
                break;
            }
        }
        if (a.length !== b.length) {
            console.error('    行数不同：Java ' + a.length + ' 行 / harness ' + b.length + ' 行');
        }
    }

    // 载荷自身的形状也要能在**Java 视角**下核对：它是 JS，不能是空的或半截的。
    if (!fromJava.includes('rpc.exports')) fail('Java 抽出的载荷里没有 rpc.exports');
    if (!fromJava.includes('setInterval')) fail('Java 抽出的载荷里没有信箱轮询');
} finally {
    rmSync(work, {recursive: true, force: true});
}

if (failures > 0) {
    console.error('FridaPayloadEmbeddingTest FAIL (' + failures + ' 项)');
    process.exit(1);
}
console.log('FridaPayloadEmbeddingTest PASS（Java 抽出的载荷与行为测试测的逐字节相同）');
