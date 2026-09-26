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
 * 以 build-bootstraps.sh 的 PACKAGES 清单为种子，沿 var/lib/dpkg/status 的
 * Depends:/Pre-Depends: 做广度优先闭包；不在闭包内的包视为「可删」。
 *
 * 该算法已用官方 bootstrap 自带的 status（82 个包）独立验证：算出的闭包
 * **恰好 82 个包，一个不多一个不少**（无漏、无多）。
 *
 * ## 安全性
 *
 * - **重叠保护**：只删除「可删包拥有、且不被任何保留包拥有」的文件。
 *   即使 dpkg 元数据异常，也不会误删保留包的文件。
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

function main() {
  const argv = process.argv.slice(2);
  const dryRun = argv.includes('--dry-run');
  const root = argv.find((a) => !a.startsWith('--'));
  if (!root) {
    console.error('用法: node prune-bootstrap.js <解包后的前缀目录> [--dry-run]');
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

  // ── 广度优先闭包 ──
  const keep = new Set();
  const queue = [...SEEDS];
  while (queue.length) {
    const pkg = queue.shift();
    if (keep.has(pkg)) continue;
    keep.add(pkg);
    const st = stanzas.get(pkg);
    if (!st) continue;
    for (const d of st.deps) if (!keep.has(d)) queue.push(d);
  }

  const removed = installed.filter((p) => !keep.has(p));
  const missing = [...keep].filter((p) => !stanzas.has(p));

  console.log(`[*] 已安装包数    : ${installed.length}`);
  console.log(`[*] 运行时闭包    : ${keep.size}`);
  console.log(`[*] 将删除        : ${removed.length}`);
  if (missing.length) console.log(`[*] 闭包中未安装  : ${missing.join(' ')}`);

  if (keep.size < MIN_OK || keep.size > MAX_OK) {
    console.log(`[!] 闭包大小 ${keep.size} 超出安全区间 ${MIN_OK}~${MAX_OK}`);
    console.log(`[!] 放弃裁剪（归档偏大但功能完好）`);
    process.exit(0);
  }
  if (!removed.length) {
    console.log('[=] 无包可删（已是运行时闭包）');
    process.exit(0);
  }
  console.log(`[*] 待删除: ${removed.join(' ')}`);

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
    // 该包的 dpkg 元数据
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
