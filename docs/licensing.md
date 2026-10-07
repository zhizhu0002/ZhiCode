# 许可与归属

本文件记录**结论是怎么得出的**，而不只是结论。许可判断的价值在于可核对：上游若改了许可，
你应该能拿这里的每一条重新验证一遍。

结论清单在 [`NOTICE`](../NOTICE)，许可原文在 [`THIRD-PARTY-LICENSES/`](../THIRD-PARTY-LICENSES)。

> **本文件不构成法律意见。** 涉及「某条链路是否构成衍生作品」「上游当时是什么许可」这类判断，
> 下面都会写明证据强度：能核到的给出命令与结果，核不到的标成**未确证**。
> 本文件也**不抄会漂移的数字**（逐区域行数随每次重写变化）—— 要看数字请跑
> [`tools/provenance.sh`](../tools/provenance.sh)。

---

## 1. 一句话结论

**本工程自己写的代码用 MIT**（[`LICENSE`](../LICENSE)）。
发行物里另有 Apache-2.0 组件、LGPL-2.1 依赖，以及数百个各自许可的 Termux 用户空间程序，
所以**发行物不是纯 MIT 包**，但本工程整体的许可**不必因此改成 GPL**（论证与证据强度见第 5、7 节）。

---

## 2. 发行物里到底有哪几类东西

| 类别 | 例子 | 许可 | 怎么核实 |
| --- | --- | --- | --- |
| 本工程代码 | `compose/`、`sandbox/`、`core/`、`tools/` | MIT | 根 [`LICENSE`](../LICENSE) |
| 并入源码的第三方模块 | `Bcore/`、`black-reflection/`、`compiler/` | Apache-2.0 | 各模块目录下的 `LICENSE` + [`NOTICE`](../NOTICE) 第 2 节 |
| 并入源码的第三方代码 | `com/termux/terminal/`、`com/termux/view/` | Apache-2.0 | [`NOTICE`](../NOTICE) 第 3 节 |
| 链接进进程的原生库 | `app/src/main/jniLibs/arm64-v8a/libtermux.so` | Apache-2.0 | [`NOTICE`](../NOTICE) 第 3 节 + 第 7.3 节的符号核对 |
| 预编译用户空间归档 | `app/src/main/assets/bootstrap-aarch64.zip` | **各程序各自的许可**（含 GPL-3.0） | [`NOTICE`](../NOTICE) 第 5 节 + 第 7.2 节的包清单核对 |
| Gradle 依赖 | Miuix（Apache-2.0）、Sora Editor（LGPL-2.1）、AndroidX | 随构件分发 | [`NOTICE`](../NOTICE) 第 6、9 节 |
| 数据文件 | Material Symbols 路径、TextMate 语法与主题 | Apache-2.0 / MIT | [`NOTICE`](../NOTICE) 第 7 节 与 `app/src/main/assets/sora/textmate/NOTICE.md` |

---

## 3. MIT：覆盖什么，要求什么

[`LICENSE`](../LICENSE) 由两部分组成：**MIT 标准文本**（`Copyright (c) 2026 zhizhu0002`）与一段中文说明。
中文说明只用来划清范围，**不修改也不替代 MIT 条款**。

MIT 只有一条实质性条件（与 <https://choosealicense.com/licenses/mit/> 的措辞一致）：

> The above copyright notice and this permission notice shall be included in all
> copies or substantial portions of the Software.

也就是说，**只要分发物里含本工程的代码，就必须随附这份版权与许可声明**。
本工程的做法是：根目录提供 [`LICENSE`](../LICENSE)，第三方部分另提供
[`NOTICE`](../NOTICE) 与 [`THIRD-PARTY-LICENSES/`](../THIRD-PARTY-LICENSES)。

**因此：发行时除 `LICENSE` 外，必须一并提供 `NOTICE` 与 `THIRD-PARTY-LICENSES/`。**
删掉它们不影响编译，也不影响运行 —— **所以没有任何构建期或测试期的机制会替你发现漏发**。
原先有一条结构断言 `LicenseNoticeStructureTest` 盯着这些文件与其中的关键表述，
**它已随 `app/tests/` 整套删除**；现在这一步只能靠发行前按本文件人工清点。

---

## 4. 上游 IQ Code（MIT）

- 来源：<https://github.com/iqisge-gif/IQ-Code-Android>
- 许可原文：`THIRD-PARTY-LICENSES/IQ-Code-MIT.txt`（`Copyright (c) 2026 IQge`，标准 MIT 文本，**逐字保留**）

本工程的起点是 IQ Code：协议层、Agent 主循环与工具集、沙箱宿主层的早期实现都从它那里起步，
之后被分批重写。**重写不免除 MIT 的署名义务** —— 只要分发物里还留着它的代码，就必须随附它的版权与许可声明。
这一点与「代码被改写多少」无关，两件事不要混：

- 「重写」解决的是**归属清楚**（谁写的、改了多少）；
- 「随附声明」是**分发条件**。

### 关于「原作者另行许可」这条记录

`LICENSE` 与 `NOTICE` 第 1 节都提到：原作者已明确许可本工程的改写、改名与发行，
并同意**不强制**要求随附其版权声明。这是仓库内**历史记录的转述**：

- 它**无法从本仓库核实**（仓库里没有该许可的书面材料、邮件或凭证）；
- 因此本文件的立场是：**按 MIT 的既有义务来做署名与随附声明**，不把那段说明当作免除义务的依据；
- 保留这份致谢的理由与法律无关 —— 它是**自愿**的：本工程的归属度量一直在拿两棵树逐行比对，
  派生关系本来就是自证的，把出处写清楚比含糊过去更诚实，成本也只是一个段落。

换句话说：**即使「不强制随附」那句话完全成立，我们仍然保留署名（自愿）；即使它不成立，
我们也已经履行了 MIT 要求的那一份声明（义务）。** 两种情况下都不欠账。

---

## 5. Apache-2.0 组件

### 5.1 涉及哪些

| 组件 | 对应内容 |
| --- | --- |
| BlackBox 虚拟化引擎（`Bcore/`、`black-reflection/`、`compiler/`） | 来源 <https://github.com/ALEX5402/NewBlackbox> |
| Termux 终端模拟与视图 | `com/termux/terminal/`、`com/termux/view/`；来源 termux-app 的 `terminal-emulator` / `terminal-view` |
| Material Symbols 图标路径数据 | `compose/ui/ZhiMaterialIcons.kt`（生成物） |

### 5.2 Apache-2.0 第 4 条要求什么（原文摘要）

> 4. Redistribution … provided that You meet the following conditions:
> (a) You must give any other recipients of the Work or Derivative Works a copy of this License; and
> (b) You must cause any modified files to carry prominent notices stating that You changed the files; and
> (c) You must retain, in the Source form of any Derivative Works …, all copyright, patent, trademark, and
> attribution notices from the Source form of the Work …; and
> (d) If the Work includes a "NOTICE" text file …, then any Derivative Works that You distribute must include
> a readable copy of the attribution notices contained within such NOTICE file …

（原文见 <https://www.apache.org/licenses/LICENSE-2.0>，仓库内副本为
[`THIRD-PARTY-LICENSES/Apache-2.0.txt`](../THIRD-PARTY-LICENSES/Apache-2.0.txt)。）

### 5.3 我们怎么履行，以及**还没履行的部分**

| 条件 | 现状 |
| --- | --- |
| (a) 给接收者许可证副本 | ✅ 各模块目录下有 `LICENSE`，另有 `THIRD-PARTY-LICENSES/Apache-2.0.txt` |
| (b) 修改过的文件携带显眼声明 | ⚠️ **未做**。本工程对 Bcore 的修改记录在 [`Bcore/NOTICE`](../Bcore/NOTICE)（模块级汇总声明），但**被修改的文件本身没有声明头**。实测：`Bcore/src/main/java` 下 499 个 Java 文件里，带 Apache 声明头的为 **0 个** |
| (c) 保留上游声明 | ✅ 未改动或删除任何许可与版权声明文本。上游源码本身没有逐文件版权头（Bcore 里 `Licensed under the Apache License` 出现 0 次），因此「保留」的对象是模块级 `LICENSE` 与包名 `top.niunaijun.*`（包名保留也是刻意的，见 [`provenance.md`](provenance.md)） |
| (d) 上游 NOTICE | ✅ 上游仓库没有 NOTICE 文件，因此没有需要原样保留的 NOTICE；我们自己的 `NOTICE` 是**本工程的**声明 |

**只改了哪些、没改哪些（已核对）**

| 模块 | 源码是否被本工程改过 | 依据 |
| --- | --- | --- |
| `black-reflection` | **没有** | 与今天抓取的上游 `main` 逐文件 `diff -rq` **无输出**（内容一致），且模块内不含本工程标记 |
| `compiler` | **没有** | 同上 |
| `Bcore` | **有**（新增 `SandboxContract.java`；改 `BlackBoxCore.java`；改 `AndroidManifest.xml`） | 三处都能在提交 `f849e14` 之后的历史里查到，且 `BlackBoxCore.java` 内命中 6 处本工程标记 |

**一处必须说清的归因边界**

拿 `Bcore` 与**今天**的上游 `main` 做全树 diff，会看到 **20 个文件内容不同**，
另有 3 个上游文件不在本模块里（`utils/LogSender.java`、`utils/SimpleCrashFix.java`、
`closecode/Entry.java`），以及 1 个本模块独有目录（`jniLibs/`）。

但**这些差异不能都算在本工程头上**，原因是本模块的来源链是多跳的：

```
ALEX5402/NewBlackbox ──(IQ Code 的 vendor 副本)──▶ IQ Code ──(本工程整棵复制)──▶ 本仓库
```

差异里混着三件事：上游自己之后的演进、IQ Code 当时的改动、本工程后来的改动。
实测那 20 个文件里**只有 `BlackBoxCore.java` 带本工程标记**，其余 19 个不含任何
`zhizhu` / `ZhiCode` 字样 —— 也就是说它们更可能来自前两者，**但本工程没有钉住
vendoring 时的上游 revision，所以无法逐文件下结论**。

> 因此 `Bcore/NOTICE` 里**已删掉**旧版本那句「未删除上游文件」—— 它无法核实：
> 那 3 个文件在当前对照里确实不在模块中，究竟是当时的副本就没有、还是后来被删，判定不了。

**怎么关闭这两件事**：

```bash
# 归因：钉住 vendoring 时的上游 revision，再重新 diff
git show f849e14 --stat -- Bcore    # 该提交信息逐条列出了当时做的改动
# 4(b)：给被改动的三个位置各加一段简短声明头
#   Bcore/src/main/java/top/niunaijun/blackbox/SandboxContract.java
#   Bcore/src/main/java/top/niunaijun/blackbox/BlackBoxCore.java
#   Bcore/src/main/AndroidManifest.xml
```

### 5.4 预编译原生库 `libblackbox.so`：**只核出了一项内容（Dobby）**

`Bcore/src/main/jniLibs/arm64-v8a/libblackbox.so`（343,624 字节，AARCH64 ELF）是**二进制产物**：

- 本仓库**不含**生成它的构建过程（当前上游 `main` 反而是走 `ndk-build`、不提供预编译库）；
- 它的来源是 vendoring 时从 IQ Code 的树里取的（提交 `f849e14` 的信息里写明：
  因本机宿主是 aarch64 而 NDK 只发布 linux-x86_64 宿主工具链，所以改用「同一份源码产出的预编译库」）；
- 它依赖 `libart.so` / `libandroid_runtime.so` / `libc.so` 等系统库（`tr -c '[:print:]' '\n' < … | grep '^lib.*\.so'`）。

**目前从中确认的一项内容**：

```bash
tr -c '[:print:]' '\n' < Bcore/src/main/jniLibs/arm64-v8a/libblackbox.so | grep -aiE 'dobby' | sort -u
# → Dobby / DobbyHook / DobbySymbolResolver 与一批 Dobby 源码路径
#    （/Users/runner/work/Dobby/Dobby/...，说明它是在某个 CI 的 macOS runner 上编出来的）
```

也就是说：**Dobby（内联 hook，Apache-2.0）被静态链接在这枚 .so 里** —— 它不会出现在 Gradle 依赖列表里，
也不会以文件形式躺在仓库中，只能从二进制上认出来。它已按 Apache-2.0 记入 [`NOTICE`](../NOTICE) 第 2.1 节
（含来源、许可、核对命令与「本工程未修改其源码」的声明）。

**仍然未知的部分**：除 Dobby 之外，该 .so 里是否还静态含有别的第三方组件、以及它究竟对应上游哪个
revision，**目前无法从本仓库回答**。这是一条如实记录的开放项（第 9 节），不要当成「已经确认没问题」。

### 5.5 血统更上游的 `FBlackBox/BlackBox`：**今天已查不到许可**

- 仓库**仍然存在**：`curl -s https://api.github.com/repos/FBlackBox/BlackBox` → HTTP 200（含描述与元信息）。
- 但它**没有许可文件**：`/license` 端点 → **HTTP 404**，因此该仓库今天声明的是什么许可**无法从 GitHub 确认**。
- 本工程代码里保留的包名 `top.niunaijun.*` 正来自这一血统。

**结论（严格限定）**：本工程 vendored 的是 Apache-2.0 的 `ALEX5402/NewBlackbox`（已确证，见第 7.1 节），
这一层的义务由本工程承担；**再往上一层的原始项目当时是什么许可，今天无从查证**。
本文件不对它下法律结论，只把它标成一个已知的、无法当场关闭的疑点。

---

## 6. 交互参考与 LGPL 依赖

### 6.1 MaterialFiles —— 仅设计参考（GPL-3.0，**未复制**源码）

- 来源：<https://github.com/zhanghai/MaterialFiles>，许可 GPL-3.0。

本工程参考了它的独立文件页、顶部返回 / 文件名层级与正文占满页面的交互形态。
**本工程没有复制它的 Kotlin / Java / XML / 资源或类名**：编辑页是基于本工程现有 Compose/Miuix 与
Sora Editor 的独立实现。因此本项是**设计参考**，不是随发行物链接或内嵌的 MaterialFiles 第三方代码，
也就不会把本工程拖进 GPL-3.0。

### 6.2 Rosemoe Sora Editor —— LGPL-2.1 依赖

- 来源：<https://github.com/Rosemoe/sora-editor>
- 构件：`io.github.rosemoe:editor:0.24.6`、`io.github.rosemoe:language-textmate:0.24.6`
- 许可：LGPL-2.1（原文 <https://github.com/Rosemoe/sora-editor/blob/main/LICENSE>）

本工程使用它的增量文本布局、缩放、滚动与 TextMate 语法高亮能力；**未复制其源码**。

LGPL 的常见义务包括随附许可声明、提供源码出处，以及允许使用者替换为修改后的库版本。
**目前的状态**：`NOTICE` 第 9 节给出了来源与许可链接，源码出处可由上游仓库获得；
**「可替换性」这一条尚未评估**，属于开放项 —— 要把它当作明确满足，需要写出具体做法
（例如提供未混淆的库构件与重打包说明），而不是只在文档里声明。

---

## 7. 复核方法（可逐条重跑）

### 7.1 直接上游的许可

```bash
curl -s https://api.github.com/repos/ALEX5402/NewBlackbox/license | head -c 200
# → {"name":"LICENSE","path":"LICENSE", … "content":"QXBhY2hlIExpY2Vuc2UK…"}（base64 解出为 Apache License 2.0）
```

> ⚠️ GitHub 的 license API 是**自动识别**：它能证明「这个文件在、且是 Apache-2.0 的标准文本」，
> 不能替代法律判断。

### 7.2 Termux 用户空间里的程序是什么许可

```bash
unzip -p app/src/main/assets/bootstrap-aarch64.zip var/lib/dpkg/status | grep -c '^Package:'   # 当前 85
unzip -p app/src/main/assets/bootstrap-aarch64.zip var/lib/dpkg/status \
  | grep -E '^Package: (bash|coreutils|apt|dpkg|sed|grep|openssl)$' | sort -u
```

许可本身要看 termux-packages 对应包的 `build.sh`，例如：

```bash
curl -s https://raw.githubusercontent.com/termux/termux-packages/master/packages/bash/build.sh \
  | grep TERMUX_PKG_LICENSE
# → TERMUX_PKG_LICENSE="GPL-3.0"
```

归档体量（2026-09 的那一版）：`bootstrap-aarch64.zip` 33,484,104 字节，4,533 个条目，解压后 90,872,448 字节。

### 7.3 `libtermux.so` 为什么是 Apache-2.0 而不是 GPL

```bash
tr -c '[:print:]' '\n' < app/src/main/jniLibs/arm64-v8a/libtermux.so \
  | grep -a '^Java_com_termux_terminal_JNI_' | sort -u
# → Java_com_termux_terminal_JNI_close / createSubprocess / setPtyUTF8Mode / setPtyWindowSize / waitFor
```

这组符号名对应 termux-app 的 `terminal-emulator` 子库。而 termux-app 的
[`LICENSE.md`](https://raw.githubusercontent.com/termux/termux-app/master/LICENSE.md) 写着：

> The `termux/termux-app` repository is released under [GPLv3 only] license.
> ### Exceptions
> - [Terminal Emulator for Android](https://github.com/jackpal/Android-Terminal-Emulator) code is used which is
>   released under [Apache 2.0] license. Check `terminal-view` and `terminal-emulator` libraries.

`libtermux.so`、`com/termux/terminal/`、`com/termux/view/` 全部落在这个例外里 → **Apache-2.0**。
这一点很关键：它是**唯一一个被链接进本应用进程**的原生库；如果它是 GPL，整份应用就是衍生作品。

### 7.4 TextMate 语法与主题的上游

```bash
for u in \
  https://raw.githubusercontent.com/microsoft/vscode/main/LICENSE.txt \
  https://raw.githubusercontent.com/fwcd/vscode-kotlin/main/LICENSE \
  https://raw.githubusercontent.com/emilast/vscode-logfile-highlighter/master/LICENSE ; do
  printf '%s\n' "--- $u"; curl -s "$u" | head -4
done

# YAML 语法：注意许可文件叫 LICENSE.md，不是 LICENSE（找错文件名会得到 404，
# 而那会被误读成「这个仓库没有许可」—— 本文献就错过一次，见第 9、10 节）
curl -s https://api.github.com/repos/RedCMD/YAML-Syntax-Highlighter/license \
  | grep -o '"spdx_id": *"[^"]*"' | head -1
# → "spdx_id": "MIT"
curl -s https://raw.githubusercontent.com/RedCMD/YAML-Syntax-Highlighter/main/LICENSE.md | head -3
# → MIT License / Copyright 2024 RedCMD
```

当前结果：以上四项均为 **MIT**。逐文件清单与**仍未核实项**（`darcula.json`、`quietlight.json`、
`extra/generic.tmLanguage.json`、`languages.json`，以及未被清单引用的 `grammars/generic-programming.tmLanguage.json`）写在
[`app/src/main/assets/sora/textmate/NOTICE.md`](../app/src/main/assets/sora/textmate/NOTICE.md)。

---

## 8. 度量：怎么知道还留着多少上游代码

```bash
IQCODE_ORIGINAL=/path/to/IQ-Code-Android bash tools/provenance.sh
```

做法是把两棵树的包名、品牌、类名做**归一化**（`com.zhizhu.zhicode`→`com.iqge`、`Zhi`/`ZhiCode`→`IQ`/`IQCode`，
另有一条 `蜘蛛`→`IQ Code` 的**历史别名**规则，只为让早期提交仍能对上），
再按映射路径逐文件比对**逐行相同**的行数。关键是**只抹命名、不动代码形态** ——
因此「相同」意味着代码本身没被改写，而不只是「看起来像」。

**这个脚本出过三次同类错误**（都写在脚本注释里）：路径映射只在「文件名不变」时成立，
于是改了类名、文件名也变了的文件被静默算成 0 重合 —— 一个恒报 0 的区域永远不会提醒你它的重合度回升了。
现在改名走同一份配对表，脚本自带漏算自检（两份名单不一致、配对文件不存在都会报警）。

它还把残留行分成四桶，再给出**净相同行** = 逐行相同 − 骨架行 − 跨组件协议串行：

| 桶 | 能不能归零 | 为什么 |
| --- | --- | --- |
| 骨架（`{}();,[]` / `import` / javadoc 前缀） | **不能** | 任何 Java/Kotlin 文件都以 `import` 开头、以 `}` 结尾 |
| 字面量（协议键名与用户可见文案） | 中文串**不能**（它可改写，属于"还要重写"）；跨组件协议串可扣 | JSON 字段名、动作名是跨组件契约，改了会让对面失配 |
| 声明（字段、签名、注解） | 基本不能 | 相同不是因为抄，而是因为这是写同一件事的唯一写法 |
| 语句（有判断与动作） | 能，而且应该压 | **这才是「读起来还像原版」的地方** |

### 8.1 扣减规则是可被推翻的

扣减只认两类：**骨架行**与**跨组件协议串行**。协议串的判据是「该行的每一个字符串字面量都合格」，
且满足两选一 —— ① 在本工程 ≥ 2 个文件里出现；② 它出现的**每一处**都在**协议位置**
（引号之前最近的 `(` 的名字在**白名单** `PROTO_CALLS` 里，且这个 `(` 到该引号之间没有别的字符串；
或形如 `case "…":`）。**白名单只此一份**（`tools/provenance.sh` 的 `PROTO_CALLS`），
文档不抄第二份 —— 两份各写各的必然漂移。

规则刻意窄到可以人工核对，并且**宁可把净相同行算高**：中文串一律不扣、空串一律不扣、纯符号串一律不扣；
`body.put("key", "value")` 里的 `"value"` 这类**值**也不算协议串。

**这条判据放宽过一次**：只出现在我们一个文件里、但每一处都在协议位置上的串（模型 API 字段名、
统一 HTTP 头、已写在用户磁盘上的会话文件键名）现在也扣。放宽的方向与「把数字改好看」相反 ——
**它让净相同行变小**，所以这是更诚实的口径，而不是更宽松的口径。

每一次扣减都会把被扣的串连同它出现的文件写进
`build/provenance-protocol-strings.txt`，分三段（`[files]` / `[position]` / 没有被扣的串），
所以**净数字要连审计文件一起看**，不能只看一个数。

### 8.2 语句行还剩多少可收敛

```bash
PROVENANCE_SHAPE=1 bash tools/provenance.sh                       # 连续多行模板的重复度（可省行数）
PROVENANCE_SHAPE=1 PROVENANCE_SHAPE_UNIQUE=1 bash tools/provenance.sh   # 「只出现一次的形状」清单
```

第二个模式导出到 `build/provenance-unique-shapes.txt`（每行带文件名），供逐条判读。
这一步是必要的：报「可省 0 行」这个结论若没有清单支撑，读者只能相信一句话。

> 口径要连定义一起读：形状完全相同的行会被算进「重复」那一档，所以清单里的行**在结构上彼此不同** ——
> 这是算法定义决定的，不是巧合。

---

## 9. 开放项汇总（按可关闭程度排序）

| # | 事项 | 状态 |
| --- | --- | --- |
| 1 | Apache-2.0 §4(b)：被修改的 Bcore 文件缺**文件级**声明头（仅模块级 NOTICE 有汇总声明） | 可低成本关闭：给 3 个位置加声明头（见 5.3） |
| 2 | Bcore 与上游的差异**不可逐文件归因**（未钉住 vendoring 时的上游 revision；来源链还经过 IQ Code） | 需记录当时的 upstream revision 后重新 `git diff` 归因 |
| 3 | `libblackbox.so` 是二进制产物：**已核出内含 Dobby（Apache-2.0，已记入 NOTICE）**，但其余静态内容与对应上游 revision 未知 | 需取得构建配方，或做完整静态内容清单（见 5.4） |
| 4 | YAML 语法（**已关闭**，见「已关闭」一节）、`darcula.json`、`quietlight.json`、`extra/generic.tmLanguage.json`、`languages.json` 的来源 / 许可未核到 | `textmate/NOTICE.md` 已逐项标注；未关闭的那几项需换源核实 |
| 5 | LGPL-2.1（Sora）的「可替换性」未评估 | 需写出具体做法，或明确声明现状 |
| 6 | bootstrap 内 GPL 程序的**源码对接收者可得** | 由 termux-packages 与本仓库 [`tools/termux-bootstrap-fork/`](../tools/termux-bootstrap-fork) 提供；发行时需确认可达 |
| 7 | 更上游 `FBlackBox/BlackBox` 的许可无法查证 | 无法当场关闭（见 5.5） |
| 8 | 关于「原作者另行许可」的仓库内说明 | 无法从仓库核实，因此按 MIT 既有义务履行署名（见第 4 节） |

**已关闭**：

- `black-reflection` 与 `compiler` 的「是否被改过」此前是未知项（两个模块的 NOTICE 里
  写着「未比对过上游」）；现已与上游 `main` 逐文件核对，结论是**未修改**，并写进了各自的 NOTICE。
- **YAML 语法（`grammars/yaml*.tmLanguage.json`）的许可**：`textmate/NOTICE.md` 曾写「`main`/`master` 的
  `LICENSE` 路径都取不到（HTTP 404），不要把这一项当成已确认的 MIT」。**那个结论是错的** ——
  上游仓库有许可文件，只是文件名叫 `LICENSE.md` 而不是 `LICENSE`。实测：

  ```bash
  curl -s https://api.github.com/repos/RedCMD/YAML-Syntax-Highlighter/license \
    | grep -o '"spdx_id": *"[^"]*"' | head -1
  # → "spdx_id": "MIT"（文件为 LICENSE.md，Copyright 2024 RedCMD）
  ```

  因此这一项已核实为 **MIT**，`NOTICE` 与 `textmate/NOTICE.md` 已同步更正。

---

## 10. 本文件自己也可能出错

历史上它至少错过三次：一次是**举错了协议串的例子**（写文档时没有去审计文件里核一遍）；
一次是在重写时删掉了几句许可说明，导致文档与实际文件范围不一致；最近一次是**把
「取不到许可」当成了结论**（见第 9 节 YAML 那条：实际是文件名不同，不是没有许可）。现在的规矩是：

- 凡数字，要么给命令让读者自己跑，要么不写；
- 凡结论，要么给可重跑的命令，要么明确标成未确证；
- **「核不到」不等于「没有」**：换文件名、换分支、换 API 端点各试一次再下结论，
  并把试过哪些路径写下来；
- 凡涉及法律判断的，都说清这是**作者的理解**，不是法律意见；
- **改完这类文件要人工复核**（原 `LicenseNoticeStructureTest` 已随 `app/tests/` 删除）：
  来源 URL、许可类型、对应文件、修改声明、许可原文路径必须彼此一致；
  同时确认根目录 `LICENSE`、`NOTICE` 与 `THIRD-PARTY-LICENSES/` 三者齐全。

如果你按本文的命令复核后发现结论不成立，**那是本文的错**，请连同你的命令一起开 issue。
