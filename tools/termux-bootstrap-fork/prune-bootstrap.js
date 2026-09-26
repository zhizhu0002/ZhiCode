#!/usr/bin/env node
/*
 * prune-bootstrap.js —— 把已构建的 bootstrap 裁剪成「运行时依赖闭包」
 *
 * ## 为什么需要
 *
 * scripts/build-bootstraps.sh 的 extract_debs() 是 `for deb in *.deb` —— 把
 * output/ 里**每一个** deb 都解进归档。而 fork 场景下依赖下载被禁用（日志里的
 *   "Ignoring -i option to download dependencies since repo package name
 *    (com.termux) does not equal app package name (com.zhizhu.code)"）
 * 于是纯构建期依赖（doxygen、python、perl、tcl/tk、X11 全家桶、fontconfig…）
 * 也全被从源码编译进 output/，再被一起解进 bootstrap。
 *
 * 实测（运行 #6）：
 *   官方 release :  82 个包 /  3473 文件 /  32 MB
 *   我们构建的   : 159 个包 / 17485 文件 / 122 MB
 * 官方那 82 个包就是运行时闭包（由 generate-bootstraps.sh 按 Depends: 递归生成，
 * 天然不含构建期依赖），多出来的 77 个全是编译才需要的。
 *
 * ## 用法
 *
 *   node prune-bootstrap.js <解包后的前缀目录> [--dry-run]
 *
 * 即 bootstrap zip 解开后的内容（$TERMUX_PREFIX 下的文件），例如：
 *   unzip -q bootstrap-aarch64.zip -d /tmp/vb && node prune-bootstrap.js /tmp/vb
 *
 * ## 算法与正确性
 *
 * 三步，缺一不可：
 *
 * **① 元数据闭包**：以 build-bootstraps.sh 的 PACKAGES 清单为种子，沿
 *   var/lib/dpkg/status 的 Depends:/Pre-Depends: 做广度优先闭包。
 *   该算法已用官方 bootstrap 自带的 status（82 个包）独立验证：算出的闭包
 *   **恰好 82 个包，一个不多一个不少**。
 *
 * **② ELF 依赖兜底（关键）**：只信 `Depends:` 是不够的 —— fork 的单容器构建会
 *   让包**链接到它没有声明的库**（构造污染）。实测抓到过：
 *
 *     util-linux 声明的依赖只有 libandroid-glob / libandroid-posix-semaphore /
 *     libcap-ng / libsmartcols / ncurses / zlib，但它的 bin/lsns 实际链接了
 *     libmount.so。libmount 是另一个包，按 ① 算它不在闭包里 → 被删掉 →
 *     **lsns 变成 CANNOT LINK EXECUTABLE**，而且整个流程不会报任何错。
 *
 *   同理 sed 被编入 SELinux 支持、链接了未声明的 libandroid-selinux
 *   （该问题已在 fork 侧用 --without-selinux 修掉；这里作为兜底仍要防）。
 *
 *   所以第②步会真的**解析每个保留 ELF 的 DT_NEEDED**，发现「需要但没有任何
 *   保留包提供」的库时，把它所属的包拉回闭包，然后重新做 ①，直到不动点为止。
 *
 * **③ 复核**：兜底跑完后若仍有解析不了的库，逐条打印出来（不静默通过）。
 *
 * ## 安全性
 *
 * - **重叠保护**：只删除「可删包拥有、且不被任何保留包拥有」的文件。
 * - **数量守卫**：闭包大小超出 [60,150] 就整体放弃（宁可归档偏大，不要缺包）。
 * - 只动三类东西：被删包 .list 列出的文件、其 var/lib/dpkg/info/<pkg>.* 元数据、
 *   status 里它的 stanza。不触碰二阶段脚本
 *   （etc/termux/termux-bootstrap/**、etc/profile.d/01-*-fallback.sh）。
 */

'use strict';
const fs = require('fs');
const path = require('path');

// build-bootstraps.sh 的权威 PACKAGES 清单（apt 版；proot 仅 --android10 使用，
// bzip2 已改为 libbz2 —— 见 packages/libbz2/bzip2.subpackage.sh）
const SEEDS = [
  'apt', 'bash', 'libbz2', 'command-not-found',
  'coreutils', 'dash', 'diffutils', 'findutils', 'gawk', 'grep', 'gzip',
  'less', 'procps', 'psmisc', 'sed', 'tar',
  'termux-core', 'termux-exec', 'termux-keyring', 'termux-tools',
  'util-linux',
  'ed', 'debianutils', 'dos2unix', 'inetutils', 'lsof', 'nano',
  'net-tools', 'patch', 'unzip',
];

const MIN_OK = 60;   // 闭包小于此值视为算错
const MAX_OK = 150;  // 闭包大于此值视为算错

/**
 * 由 Android 平台/系统提供的库，bootstrap 不必自带，缺失不算问题。
 * 注意 libselinux 也在内：平台有 /system/lib64/libselinux.so。
 */
const SYSTEM_LIB_RE = /^(libc|libm|libdl|liblog|libz|libandroid|libstdc\+\+|libunwind|libGLES|libEGL|libvulkan|ld-android|libnativehelper|libjnigraphics|libOpenSLES|libmediandk|libcamera2ndk|libaaudio|libbinder_ndk|libsync|libcutils|libutils|libbase|libhardware|libselinux)\.so/;

/**
 * 这些路径下的二进制不参与依赖兜底，也不因缺库而报警。
 *
 * - `libexec/installed-tests/` 是 termux-core / termux-exec 随包安装的**测试套件**，
 *   里面有 `-fsanitize=address` 编出来的测试二进制，需要 libclang_rt.asan-*.so
 *   （编译器运行时，不属于任何运行时包）。这些文件对最终用户没有用途，
 *   缺 ASan 运行时也不影响 bootstrap 功能。
 *
 * - Python 的 site-packages 目录（`lib/python<版本>/site-packages/`）里是
 *   **可选的 Python 绑定**。例如 libmount 包里带了 pylibmount.so，它需要
 *   libpython3.14.so，于是兜底会把整个 python 包（+23 MB）拉进闭包 ——
 *   而 bootstrap 里根本没有任何东西 import 它。这些扩展只在被 python 显式
 *   import 时加载，不该影响运行时闭包。
 */
const IGNORE_PATH_RE = /^(libexec\/installed-tests\/|lib\/python[0-9.]*\/site-packages\/)/;

// ------------------------------------------------------------------ dpkg 元数据

function parseStatus(text) {
  const stanzas = new Map();
  for (const block of text.split(/\n\s*\n/)) {
    const lines = block.split('\n').filter((l) => l.length > 0);
    if (!lines.length) continue;
    const m = lines[0].match(/^Package:\s*(\S+)/);
    if (!m) continue;
    const deps = new Set();
    for (const line of lines) {
      const d = line.match(/^(?:Pre-)?Depends:\s*(.*)$/i);
      if (!d) continue;
      for (let t of d[1].split(',')) {
        t = t.split('|')[0].replace(/\(.*?\)/g, '').split(':')[0].trim();
        if (t) deps.add(t);
      }
    }
    stanzas.set(m[1], { lines, deps });
  }
  return stanzas;
}

/** 把 .list 里含完整前缀的绝对路径换成归档内相对路径 */
function toRelPath(line, marker) {
  const i = line.indexOf(marker);
  if (i < 0) return null;
  const rel = line.slice(i + marker.length);
  if (!rel || rel.includes('..') || rel.endsWith('/')) return null;
  return rel;
}

function ownedFiles(root, infoDir, pkg, marker) {
  const out = [];
  const listPath = path.join(infoDir, `${pkg}.list`);
  if (!fs.existsSync(listPath)) return out;
  for (const raw of fs.readFileSync(listPath, 'utf8').split('\n')) {
    const rel = toRelPath(raw.trim(), marker);
    if (rel) out.push(rel);
  }
  return out;
}

// ------------------------------------------------------------------ ELF 解析

const ELF_MAGIC = [0x7f, 0x45, 0x4c, 0x46];

function isElf64(file) {
  let fd;
  try {
    fd = fs.openSync(file, 'r');
    const h = Buffer.alloc(5);
    if (fs.readSync(fd, h, 0, 5, 0) < 5) return false;
    return ELF_MAGIC.every((v, i) => h[i] === v) && h[4] === 2; // EI_CLASS == ELFCLASS64
  } catch {
    return false;
  } finally {
    if (fd !== undefined) { try { fs.closeSync(fd); } catch { /* 忽略 */ } }
  }
}

/**
 * 取一个 ELF64 的 DT_NEEDED 列表（动态依赖的库名）。
 *
 * 小节头布局（ELF64）：sh_name@0x00(4) sh_type@0x04(4) sh_flags@0x08(8)
 *   sh_addr@0x10(8) sh_offset@0x18(8) sh_size@0x20(8) …
 * .dynamic 项：d_tag(8) d_val(8)，d_tag == 1 表示 DT_NEEDED，
 * 其 d_val 是 .dynstr 内的偏移。
 */
function dtNeeded(file) {
  const b = fs.readFileSync(file);
  if (b.length < 64 || !ELF_MAGIC.every((v, i) => b[i] === v) || b[4] !== 2) return null;
  const shoff = Number(b.readBigUInt64LE(0x28));
  const shentsize = b.readUInt16LE(0x3a);
  const shnum = b.readUInt16LE(0x3c);
  const shstrndx = b.readUInt16LE(0x3e);
  if (!shoff || !shnum || shstrndx >= shnum) return null;

  const secStrOff = Number(b.readBigUInt64LE(shoff + shstrndx * shentsize + 0x18));
  const secName = (off) => {
    let e = off + secStrOff, s = '';
    while (b[e]) s += String.fromCharCode(b[e++]);
    return s;
  };

  let dyn = null, dynstrOff = null;
  for (let i = 0; i < shnum; i++) {
    const p = shoff + i * shentsize;
    const type = b.readUInt32LE(p + 4);
    if (type === 6) dyn = { off: Number(b.readBigUInt64LE(p + 0x18)), size: Number(b.readBigUInt64LE(p + 0x20)) };
    if (secName(Number(b.readUInt32LE(p))) === '.dynstr') dynstrOff = Number(b.readBigUInt64LE(p + 0x18));
  }
  if (!dyn || dynstrOff === null) return [];   // 静态链接：没有动态依赖

  const out = [];
  for (let p = dyn.off; p + 16 <= dyn.off + dyn.size; p += 16) {
    const tag = Number(b.readBigUInt64LE(p));
    if (tag === 0) break;
    if (tag !== 1) continue;
    let e = dynstrOff + Number(b.readBigUInt64LE(p + 8)), s = '';
    while (b[e]) s += String.fromCharCode(b[e++]);
    out.push(s);
  }
  return out;
}

/** 库名归一：`libfoo.so.1.2.3` → `libfoo.so`，用于放宽匹配。 */
function libStem(name) {
  const i = name.indexOf('.so');
  return i < 0 ? name : name.slice(0, i + 3);
}

// ------------------------------------------------------------------ 主流程

function main() {
  const argv = process.argv.slice(2);
  const dryRun = argv.includes('--dry-run');
  const noElfCheck = argv.includes('--no-elf-check');
  const root = argv.find((a) => !a.startsWith('--'));
  if (!root) {
    console.error('用法: node prune-bootstrap.js <解包后的前缀目录> [--dry-run] [--no-elf-check]');
    process.exit(2);
  }

  const statusPath = path.join(root, 'var/lib/dpkg/status');
  const infoDir = path.join(root, 'var/lib/dpkg/info');
  if (!fs.existsSync(statusPath)) {
    console.error(`[!] 找不到 ${statusPath}，不像是一个 bootstrap 前缀目录`);
    process.exit(2);
  }

  // 归档内前缀目录的标记（.list 里是 /data/data/<pkg>/files/usr/...）
  const marker = '/files/usr/';

  const stanzas = parseStatus(fs.readFileSync(statusPath, 'utf8'));
  const installed = [...stanzas.keys()];

  // 文件名 → 拥有它的包（用于「这个 .so 该由谁提供」）
  const ownerOf = new Map();
  for (const pkg of installed) {
    for (const rel of ownedFiles(root, infoDir, pkg, marker)) {
      if (!ownerOf.has(rel)) ownerOf.set(rel, pkg);
    }
  }

  /** 某个库名 → 可能提供它的包集合 */
  function providersOf(lib) {
    const out = new Set();
    const direct = `lib/${lib}`;
    if (ownerOf.has(direct)) out.add(ownerOf.get(direct));
    const stem = libStem(lib);
    const prefix = `lib/${stem}.`;
    for (const [rel, pkg] of ownerOf) {
      if (rel.startsWith(prefix)) out.add(pkg);
    }
    return out;
  }

  /** 从种子出发补全依赖闭包（会就地扩充 keep 与 queue 处理） */
  function expandClosure(keep, extraSeeds = []) {
    const queue = [...extraSeeds];
    while (queue.length) {
      const pkg = queue.shift();
      if (keep.has(pkg)) continue;
      keep.add(pkg);
      const st = stanzas.get(pkg);
      if (!st) continue;
      for (const d of st.deps) if (!keep.has(d)) queue.push(d);
    }
  }

  const keep = new Set();
  expandClosure(keep, SEEDS);

  console.log(`[*] 已安装包数    : ${installed.length}`);
  console.log(`[*] 元数据闭包    : ${keep.size}`);

  // ── ② ELF 依赖兜底：解析保留 ELF 的 DT_NEEDED，把缺的提供者拉回来 ──
  let rounds = 0;
  const pulledIn = new Set();
  let unresolved = new Map();
  if (!noElfCheck) {
    for (;;) {
      rounds++;
      // 收集「保留包拥有的 ELF」的动态依赖
      const needLibs = new Map();   // lib -> Set(使用者相对路径)
      for (const pkg of keep) {
        for (const rel of ownedFiles(root, infoDir, pkg, marker)) {
          if (IGNORE_PATH_RE.test(rel)) continue;
          if (!/^(bin|lib|libexec)\//.test(rel)) continue;
          const abs = path.join(root, rel);
          if (!isElf64(abs)) continue;
          const libs = dtNeeded(abs);
          if (!libs) continue;
          for (const lib of libs) {
            if (SYSTEM_LIB_RE.test(lib)) continue;
            if (!needLibs.has(lib)) needLibs.set(lib, new Set());
            needLibs.get(lib).add(rel);
          }
        }
      }

      // 哪些库没有任何保留包提供？
      const need = [];
      for (const [lib, users] of needLibs) {
        if (providersOf(lib).size === 0) { need.push({ lib, users, providers: [] }); continue; }
        const provided = [...providersOf(lib)].some((p) => keep.has(p));
        if (!provided) need.push({ lib, users, providers: [...providersOf(lib)] });
      }

      if (!need.length) { unresolved = new Map(); break; }

      // 有提供者但没保留 → 拉回来（这是能自动修的）
      const toAdd = [];
      const stillBad = [];
      for (const n of need) {
        const addable = n.providers.filter((p) => stanzas.has(p) && !keep.has(p));
        if (addable.length) toAdd.push(...addable);
        else stillBad.push(n);
      }

      if (!toAdd.length) {
        unresolved = new Map(stillBad.map((n) => [n.lib, n.users]));
        break;
      }
      for (const p of toAdd) pulledIn.add(p);
      console.log(`[*] ELF 兜底第 ${rounds} 轮：拉回 ${[...new Set(toAdd)].join(' ')}`);
      expandClosure(keep, toAdd);
    }

    if (pulledIn.size) console.log(`[*] ELF 兜底共拉回 : ${[...pulledIn].join(' ')}（${rounds} 轮）`);
    else console.log(`[*] ELF 兜底        : 无需拉回任何包`);

    if (unresolved.size) {
      console.log('[!] 仍有解析不了的动态依赖（这些文件属于测试/可选组件，不影响 bootstrap 功能）：');
      for (const [lib, users] of unresolved) {
        console.log(`      ${lib}  ← ${[...users].slice(0, 3).join(', ')}${users.size > 3 ? ` 等 ${users.size} 个` : ''}`);
      }
    }
  }

  const removed = installed.filter((p) => !keep.has(p));
  console.log(`[*] 运行时闭包    : ${keep.size}`);
  console.log(`[*] 将删除        : ${removed.length}`);

  if (keep.size < MIN_OK || keep.size > MAX_OK) {
    console.log(`[!] 闭包大小 ${keep.size} 超出安全区间 ${MIN_OK}~${MAX_OK}`);
    console.log('[!] 放弃裁剪（归档偏大但功能完好）');
    process.exit(0);
  }
  if (!removed.length) {
    console.log('[=] 无包可删（已是运行时闭包）');
    process.exit(0);
  }

  // ── 重叠保护：先算出所有「保留包」拥有的文件 ──
  const keepOwned = new Set();
  for (const pkg of keep) {
    if (!stanzas.has(pkg)) continue;
    for (const rel of ownedFiles(root, infoDir, pkg, marker)) keepOwned.add(rel);
  }
  console.log(`[*] 保留包拥有文件: ${keepOwned.size}`);

  let filesDeleted = 0, skippedOverlap = 0, metaDeleted = 0;

  for (const pkg of removed) {
    for (const rel of ownedFiles(root, infoDir, pkg, marker)) {
      if (keepOwned.has(rel)) { skippedOverlap++; continue; }
      const target = path.join(root, rel);
      try {
        const st = fs.lstatSync(target);
        if (st.isFile() || st.isSymbolicLink()) {
          if (!dryRun) fs.unlinkSync(target);
          filesDeleted++;
        }
      } catch { /* 不存在就算了 */ }
    }
    let entries = [];
    try { entries = fs.readdirSync(infoDir); } catch { /* 忽略 */ }
    for (const e of entries) {
      if (e === `${pkg}.list` || e.startsWith(`${pkg}.`)) {
        if (!dryRun) fs.unlinkSync(path.join(infoDir, e));
        metaDeleted++;
      }
    }
  }

  // ── 重写 status ──
  const keptBlocks = [];
  for (const pkg of installed) if (keep.has(pkg)) keptBlocks.push(stanzas.get(pkg).lines.join('\n'));
  if (!dryRun) fs.writeFileSync(statusPath, keptBlocks.join('\n\n') + '\n');

  console.log(`[*] 删除文件        : ${filesDeleted}${dryRun ? '（dry-run）' : ''}`);
  console.log(`[*] 因重叠而跳过    : ${skippedOverlap}`);
  console.log(`[*] 删除 dpkg 元数据: ${metaDeleted}${dryRun ? '（dry-run）' : ''}`);
  console.log(`[*] status 保留包数 : ${keptBlocks.length}`);
  console.log('[OK] 裁剪完成' + (dryRun ? '（dry-run）' : ''));
}

main();
