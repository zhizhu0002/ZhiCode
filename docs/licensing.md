# 许可与归属

本文件记录**这些结论是怎么得出的**，而不只是结论本身。许可判断的价值在于可核对：
如果某天上游改了许可，你应当能拿这里的每一条重新验证一遍。

结论性的清单在 [`NOTICE`](../NOTICE)，许可原文在 [`THIRD-PARTY-LICENSES/`](../THIRD-PARTY-LICENSES)。

---

## 一句话结论

蜘蛛自己的代码用 **MIT**。发行物里另有 Apache-2.0 组件与一批各自许可的
Termux 二进制程序，但**本项目整体不必转为 GPL**。

---

## 为什么不必是 GPL —— 这一步曾经看起来是反的

第一眼看到工程里有 `libtermux.so` 和一个 33 MB 的 Termux bootstrap 时，
结论很像是「这必须是 GPLv3」。实际查下来不是，原因是两个组件分属两条不同的线：

### 1. `libtermux.so` 是 Apache-2.0，不是 GPL

它是唯一一个**被链接进本应用进程**的原生库（`System.load`）。
如果它是 GPL，整份应用就是衍生作品，本工程就必须 GPL 化。

判断依据：

```bash
# 看它导出什么符号，据此确定它对应上游哪个文件
tr -c '[:print:]' '\n' < app/src/main/jniLibs/arm64-v8a/libtermux.so \
  | grep -aE '.{6,}' | head
```

输出里有：

```
Java_com_termux_terminal_JNI_createSubprocess
Java_com_termux_terminal_JNI_setPtyWindowSize
Java_com_termux_terminal_JNI_setPtyUTF8Mode
Java_com_termux_terminal_JNI_waitFor
Java_com_termux_terminal_JNI_close
Cannot open /dev/ptmx
```

这组符号名对应 `termux-app` 的 `terminal-emulator` 子库
（该库的 `JNI.java` 里声明的正是这几个 native 方法）。

而 `termux-app` 根 `LICENSE.md` 原文写着：

> The `termux/termux-app` repository is released under [GPLv3 only] license.
>
> ### Exceptions
>
> - [Terminal Emulator for Android](https://github.com/jackpal/Android-Terminal-Emulator)
>   code is used which is released under [Apache 2.0] license.
>   Check [`terminal-view`](terminal-view) and [`terminal-emulator`](terminal-emulator) libraries.

`libtermux.so`、`com/termux/terminal/`、`com/termux/view/` 全部落在
这个例外里 → **Apache-2.0**。

### 2. bootstrap 里是独立程序，与我们是聚合关系

`app/src/main/assets/bootstrap-aarch64.zip` 解压后有 4,533 个文件，
里面确实是 GPL 程序（`bash` 880 KB、`coreutils`、`sed` 等）。
但它们是**独立可执行文件**，由本应用以子进程方式启动：

```
Spider 进程  ──fork/exec──▶  $PREFIX/bin/bash（GPLv3，独立进程）
```

GPL 明确允许把 GPL 程序与其它许可的程序聚合（aggregate）分发，
只要聚合体里每一部分仍受各自许可约束。这与「把 GPL 库链接进同一个进程」
是两件不同的事。前者不产生衍生作品，后者会。

对照第 1 点：`libtermux.so` 走的是后一条路（链接进进程），
但它恰好是 Apache-2.0 —— 所以两条链路都不产生 GPL 传染。

**因此 MIT 是安全的。**

### 一处仍需自己确认的事

bootstrap 里的程序许可各自适用，且要求源码对接收者可得。
出处是 `termux-packages` 与本工程的 fork 配方
（见 [`tools/termux-bootstrap-fork/`](../tools/termux-bootstrap-fork)，
含构建工作流 `build-bootstrap.yml` 与验收脚本 `VerifyBootstrap.java`）。
**发行时请确认这份源码对接收者可达。**

---

## 为什么「重写」不能免除署名义务

这是本次工作中最容易搞错的一点：

> MIT 允许使用、复制、修改、合并、发布、再分发、再授权、出售。
> 它唯一的硬性要求是：版权声明与许可声明必须
> 「included in all copies or substantial portions of the Software」。

所以：

- 用 IQ Code 的代码 → 不用重写也合法；
- 改名发行 → 合法；
- **重写 → 也不免除署名义务**，只要分发物里还留着它的代码。

「重写」解决的是**归属清楚**（谁写的、改了多少），
不是**免除署名**。两件事不要混。

---

## 度量：怎么知道还留着多少别人的代码

用 [`tools/provenance.sh`](../tools/provenance.sh)。它的做法是：

1. 把两棵树的包名、品牌、类名做**归一化**（`com.zhizhu.zhicode`→`com.iqge`、
   `Zhi`→`IQ`、`蜘蛛`→`IQ Code` 等）；
2. 按映射路径逐文件比对**逐行相同**的行数。

关键是**只抹命名、不动代码形态** —— 因此「相同」意味着代码本身没被改写，
而不只是「看起来像」。

### 这个脚本本身出过三次错，都是同一类

它曾把「改了文件名的类」算成「无对应文件」，于是重合度**误报为 0**。
三次分别是：

| 第几次 | 范围 | 后果 |
| --- | --- | --- |
| 1 | 沙箱层 14 个改了名的类 | 沙箱层重合度误报成 0 |
| 2 | 修好第 1 次时又按路径算了一遍 | 文件数与行数虚增到 203 / 40572 |
| 3 | Agent 工具层 2 个改了名的类 | `tools/` 目录的重合度误报成 0 |

现在配对表是四元组（本工程目录 | 原版目录 | 本工程文件名 | 原版文件名），
同时用 `RENAMED_BASENAMES` 把这些名字从按路径的循环里排除，避免重复计数。

**这类错误的危害不在于数字难看，而在于它让「未来是否漂移」变得不可检测**——
一个恒报 0 的区域，永远不会提醒你它的重合度回升了。

验证脚本是否还在正确工作：把一个上游文件当成我们自己的去比对，
结果应当是接近 100%（我们的实现里做过这个反向验证：

```
自比对: 93 行中相同 93 行
```

）。

### 当前数字（2026 年，本轮重写后）

```
归属区域                             文件         行数   仍与 IQCode 相同
Termux 上游（非 IQ Code）             23       7420           7274
Termux 集成层                       31       6075           3833
Compose 界面层                      57      15515              0
其它                               12       2476           1499
Agent 工具                         43       2971           2069
Agent 核心                          8       1985            680
沙箱宿主层                            16       4761            563
合计                              190      41203          15918

已是我们自己的:        25285 行
逐行相同合计:          15918 行
  其中 Termux 上游:     7274 行（Termux 自己的代码，与独立性无关）
  真正属于 IQ Code:     8644 行

**一个重要的范围澄清**：重合行数最大的那些文件**不是 IQ Code 的代码**：

```
2453 行  com/termux/terminal/TerminalEmulator.java
1297 行  com/termux/view/TerminalView.java
 552 行  com/termux/terminal/WcWidth.java
 444 行  com/termux/terminal/TerminalBuffer.java
 ...
```

这些是 **Termux 上游**（`terminal-emulator` / `terminal-view`，Apache-2.0），
由 `provenance.sh` 归入「Termux 上游（非 IQ Code）」一类。
它们的正确做法是原样保留并履行 Apache-2.0 义务（已在 `NOTICE` 中声明），
**重写它们既没有意义也是错的**。真正需要处理的是下面这两张表里的文件。

| 类 | 原版行 | 现在行 | 相同行 | 重合 |
| --- | --- | --- | --- | --- |
| `ZhiDebugTool`（Agent 工具） | 179 | 435 | 21 | 4.8% |
| `SandboxBoard` | 193 | 727 | 37 | 5.1% |
| `ZhiSandboxTool`（Agent 工具） | 93 | 290 | 15 | 5.2% |
| `SandboxGuestHost` | 299 | 886 | 53 | 6.0% |
| `SandboxOverlay` | 104 | 281 | 24 | 8.5% |
| `SandboxShell` | 102 | 270 | 23 | 8.5% |
| `FridaEnv` | 135 | 335 | 31 | 9.3% |
| `SandboxKeeper` | 34 | 99 | 12 | 12.1% |
| `ZhiSandbox` | 190 | 332 | 46 | 13.9% |
| `SandboxRpcService` | 110 | 246 | 42 | 17.1% |
| `SandboxConsole` | 78 | 212 | 37 | 17.5% |
| `SandboxGuestDebug` | 288 | 572 | 119 | 20.8% |
| `SandboxPrefs` | 96 | 146 | 33 | 22.6% |
| `SandboxFrida` | 141 | 338 | 75 | 22.2% |
| `SandboxProcess` | 40 | 69 | 18 | 26.1% |
| `SandboxRpc` | 29 | 45 | 13 | 28.9% |
| `SandboxStage` | — | 110 | — | 新文件 |
| `SandboxPalette` | — | 93 | — | 新文件 |

**没有重写的部分，如实说明：**

- `SandboxFrida` 里那段 agent JS 是**刻意冻结**的。它是对外契约：
  `IQ.emit` / `IQ.hooks` / `IQ.scan` 三个注入 API 名同时被工具 schema 与注入脚本引用，
  `rpc.exports`、信箱文件名、ready 标记是 Java 与脚本之间的协议边界。
  该类的 22.2% 主要就是这段必须保留的载荷。
  （Gadget 的**磁盘文件名**已改为 `libzhifrida.so` / `libzhifrida.config` / `zhi-agent.js`，
  而注入 API 名保持原样：它们不是品牌串，是已经写在工具 schema 与系统提示词里的契約。）
- **Agent 运行时仍在进行中**，是当前比例最高的残留区（见下文「仍然剩下的」）。

### 改名迁移（A–D，已完成）

把仍活着的旧品牌串改成「蜘蛛 / zhicode」。**只改名，不改行为**。
下面几处是例外——它们是已经落盘的用户数据或已经写死的对外契约，改了就读不到了：

| 位置 | 内容 | 为什么不能改 |
| --- | --- | --- |
| `AndroidSecretStore` | `iq_code_android_secrets` / `iq_code_android_api_key_v1` | 用户机器上的 API Key 就在这两个名字下，改名 = 读不到 |
| `ApiSettingsStore` | `iq_code_android_settings` | 同上，设置会全部看起来「没配置过」 |
| `SessionStore` | `<iq_internal_continue>`、`_iq_compacted_input` | 老会话里已经写进去的标记；不认它就会把那段字当成用户消息显示出来 |
| `TermuxConstants` | `LEGACY_DATA_DIR_NAME = ".iq"` 等 | 它本身就是旧名，用途就是兼容读取 |
| `SandboxFrida` | `IQ.emit` / `IQ.hooks` / `IQ.scan` | 注入脚本 API 名，同时写在工具 schema 与系统提示词里 |
| 各处 | `com.iqge` | 是**另一个应用**的包名，不是我们的品牌；路径伪装与文档 provider 需要它 |

各项改动的具体内容：

- **A（`7a390a9`）** 品牌串与线程名，并修掉两个真 bug：
  `iq-patch-deb` 这个脚本名从来没存在过（正确名由 `BRAND_SLUG` 派生），
  以及 `KeepAliveService` 写的是旧 prefs、导致关掉保活不会生效。
- **B（`312c89c`）** `$HOME/.iq` → `$HOME/.zhicode`（**整体 rename**，不是两处都读）、
  `IQ.md` → `ZhiCode.md`。用户**项目目录**里的 `.iq` 不动（那是他的版本库），
  读取端改成新名优先、旧名兜底。
  这里有个容易漏的点：`/init` 指令里的目标文件名如果不改，
  模型会去写 `IQ.md` 而界面读的是 `ZhiCode.md`，看起来就像「`/init` 什么都没做」。
- **C（`3829aa9`）** `LocalIqDark`、空态文案、注释里的旧产品名。
- **D（`53be6c3`）** Gadget 磁盘文件名 `libiqfrida.so` → `libzhifrida.so`（及同名 `.config`、`iq-agent.js`）。
  主副本是几十 MB 的下载产物，所以加了就地 rename 迁移，不让用户重下。

### 仍然剩下的（按重合行数排序）

`PROVENANCE_PER_FILE=1 bash tools/provenance.sh` 会打印完整清单。当前最前面的几项：

| 相同 | 原版 | 现在 | 重合 | 文件 |
| --- | --- | --- | --- | --- |
| 2453 | 2453 | 2453 | 100% | `com/termux/terminal/TerminalEmulator.java` — Termux 上游，**应当原样** |
| 1297 | 1297 | 1297 | 100% | `com/termux/view/TerminalView.java` — 同上 |
| 621 | 622 | 622 | 99.8% | `api/OpenAIResponsesProvider.java` |
| 565 | 583 | 592 | 95.4% | `com/zhizhu/zhicode/TermuxTerminalPane.java` |
| 449 | 451 | 451 | 99.6% | `api/OpenAIChatCompletionsProvider.java` |
| 435 | 468 | 475 | 91.6% | `termux/TermuxShellExecutor.java` |
| 388 | 390 | 390 | 99.5% | `core/ContextCompactor.java` |
| 352 | 354 | 354 | 99.4% | `tasks/TaskStore.java` |
| 310 | 310 | 310 | 100% | `com/zhizhu/zhicode/UiMotion.java` |
| 254 | 257 | 257 | 98.8% | `api/AnthropicMessagesProvider.java` |
| 240 | 241 | 241 | 99.6% | `com/zhizhu/zhicode/MarkdownRenderer.java` |
| 298 | 310 | 311 | 95.8% | `tools/AndroidIntentBridge.java` |
| 285 | 642 | 1221 | 23.3% | `storage/SessionStore.java`（已重写，仅列作对照） |

两点必须说清楚：

1. 头部那些 Termux 文件重合 100% 是**正确状态**，不是漏洞。
   它们由 Apache-2.0 授权且已在 `NOTICE` 里声明，重写它们既无意义也是错的。
2. 真正剩下、值得处理的是 `api/` 请求层与 Compose 界面层的几个大文件，
   它们目前几乎是原样保留的。全部逐行相同行数、扣除 Termux 上游之后是 **8644 行**。

### 数据层（已完成）

| 类 | 原版行 | 现在行 | 相同行 | 重合 |
| --- | --- | --- | --- | --- |
| `McpConfigStore` | 80 | 239 | 32 | 13.4% |
| `AndroidSecretStore` | 112 | 224 | 43 | 19.2% |
| `ApiSettingsStore` | 331 | 584 | 117 | 20.0% |
| `SessionStore` | 642 | 1202 | 285 | 23.7% |
| `PlanStore` | 87 | 142 | 72 | **50.7%** |

`PlanStore` 的 50.7% 需要解释，否则这个数字会误导：71 行相同里，
9 行是 `import`、16 行是单独一个右括号、2 行是注释标记；剩下 44 行「实质相同」
主要由两类构成 —— **必须保持不变的公开 API 签名**（被 `ZhiCodeEngine` 调用）
以及原子写入的固定步骤（写 tmp → fsync → `Os.rename`）。
这份文件本来就短而且是正确的，没有为了压低数字去改写正确的代码。

**由此得出一个度量上的结论**：对「小、正确、API 受约束」的工具类，
重合率主要由保留的 API 与语言固定写法决定，用「还剩多少别人的代码」这个口径
去读它会失真。比较不同文件时请留意 `原版行` 与 `现在行` 的差距
（差距越大，说明改动越多），而不是只看百分比。

### 度量口径

本表与上面的总计都取自 `provenance.sh` 的方法（`diff` 对齐后的未变块）。
此前的若干提交说明里我用过另一种方法（归一化后**排序**再取交集），
它会把两文件里出现在不同位置的相同行也计入，因此同一个文件会得出偏高的数字。
两种数字不可直接比较，**以本文档为准**：

| 类 | 排序取交集（早先引用的数字） | diff 对齐（本文档与脚本采用） |
| --- | --- | --- |
| `SandboxGuestHost` | 8.5% | **6.0%** |
| `SandboxPrefs` | 28.1% | **22.6%** |
| `ZhiSandboxTool` | 5.9% | **5.2%** |
| `ZhiDebugTool` | 7.1% | **4.8%** |

差别集中在 `}`、`import`、空行这类随处可见的行上 —— 排序后它们必然「相同」，
而对齐后只有当它们真的落在同一位置时才算。

### 沙箱宿主层（已完成，含 Agent 侧两个工具类）

16 个文件，563 / 4761 行 = **11.8%**（从重写前的 96% 降下来）：

---

## 怎么重新验证本文件的每一条

```bash
# 1. 各模块是否都带了许可文件
find . -maxdepth 2 -name LICENSE -o -maxdepth 2 -name NOTICE | grep -v '/build/'

# 2. 归属度量（需要本机有原版 IQ Code）
bash tools/provenance.sh

# 3. 结构测试里有 LicenseNoticeStructureTest 守着这些文件的存在，
#    它们不会在下一次重构里被静默删掉
bash test-source-no-build.sh
```

各项许可的**完整原文**：

| 组件 | 许可 | 原文位置 |
| --- | --- | --- |
| BlackBox (Bcore / black-reflection / compiler) | Apache-2.0 | `Bcore/LICENSE` 等三个模块目录，以及 `THIRD-PARTY-LICENSES/Apache-2.0.txt` |
| Termux terminal-emulator / terminal-view / libtermux.so | Apache-2.0 | `THIRD-PARTY-LICENSES/Apache-2.0.txt` |
| termux-shared `TermuxConstants.java` | MIT | `THIRD-PARTY-LICENSES/MIT.txt` |
| IQ Code | MIT (© 2026 IQge) | `THIRD-PARTY-LICENSES/IQ-Code-MIT.txt` |
| Termux bootstrap（bash / coreutils / apt / …） | 各自许可 | 各自程序与 `termux-packages` |
| 蜘蛛自身 | MIT | `LICENSE` |

---

## 发行前还需处理

1. **把版权主体填成真实名称。** `LICENSE` 与 `THIRD-PARTY-LICENSES/MIT.txt` 里
   目前写的是占位符 `蜘蛛 (ZhiCode) contributors`。
2. **确认 bootstrap 的源码对接收者可达**（见上文「一处仍需自己确认的事」）。
3. **`NOTICE` 提到但不覆盖**：通过 Gradle 引入的 Miuix、AndroidX、Compose 构件
   会随 APK 一并分发，其许可原文随构件本身提供。若要做正式发行，
   建议在「关于」页里放一份可滚动查看的完整许可列表。
