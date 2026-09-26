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
Termux 上游（非 IQ Code）             23       7394           7274
Termux 集成层                       35       9327           1932
Compose 界面层                      59      16115              0
其它                                7       2287            286
Agent 工具                         44       5673            933
Agent 核心                          9       3629            867
沙箱宿主层                            16       4728            533
合计                              193      49153          11825

已是我们自己的:        37328 行
逐行相同合计:          11825 行
  其中 Termux 上游:     7274 行（Termux 自己的代码，与独立性无关）
  真正属于 IQ Code:     4551 行
```

### 这 4551 行是什么（`PROVENANCE_COMPOSITION=1`）

「逐行相同」这个数字本身不够用：它把 `import android.os.Process;`、`}`、`return out;`
与真正的算法代码算在同一格里。把这个数字当成「还抄了多少」，会得出一个偏大得多的结论
（也会让「压到 0」变成一个不可能、因而没有意义的目标）。所以脚本会把残留行分三桶，
分类规则简单到可以人工核对：

```
骨架行（括号分号 / import / javadoc 分隔符）: 2057 行
含字面量的行（协议键名与用户可见文案）:      515 行
其它行（仍需逐条看的地方）:                  1979 行
合计:                                        4551 行
```

三桶的含义与可否归零：

| 桶 | 行数 | 能不能归零 | 为什么 |
| --- | --- | --- | --- |
| 骨架 | 2057 | **不能** | 任何 Java 文件都以 `import …` 开头、以 `}` 结尾。把这些行改得不一样等于删 import 或往里塞噪声 —— 两者都不是我们想要的 |
| 字面量 | 515 | **不能** | JSON 字段名、动作名是跨组件协议（宿主 `SandboxGuestHost`、Agent 工具、Frida 脚本三方对齐），改了会让两边对不上；用户可见文案是刻意逐字保留的 |
| 其它 | 1979 | 能，而且应该压 | 这才是「读起来还像原版」的地方 |

第三桶里占了绝大多数的是声明与签名，例如 `public final String id;`、
`public static List<AgentDefinition> loadAll(String projectDirectory) {`、
`if (files == null) return;`、`try (FileOutputStream out = new FileOutputStream(file, false)) {`。
它们相同不是因为抄，而是因为**这是 Java 里写同一件事的唯一写法**。
所以「压」的目标不是把 1979 变成 0，而是把里面真正有判断与结构的行重写完——
那之后剩下的会是语言本身的形状。

脚本会把第三桶全文写到 `build/provenance-other-lines.txt` 并打印路径，供逐条核对
（不相信上面这行总结的人可以自己看）。

**这个路径以前是打不开的**：第三桶原先写在 `mktemp -d` 建的工作目录里，而脚本
退出时会 `trap` 把它整个删掉 —— 于是「供逐条核对」是一句空头承诺，打印出来的路径
在下一行就已经不存在了。现在它固定写到工程内的 `build/` 下（1979 行，已验证留存）。

### 这个数字曾经是错的（记下来，因为它会再次发生）

上一版这里写的是「真正属于 IQ Code: 8644 行」。那个数字**低估了 1488 行**，
原因是 `tools/provenance.sh` 的路径映射只在「文件名不变」时成立：
一个文件如果改了类名、因而**文件名也变了**（`ZhiCodeEngine` 对 `IQCodeEngine`），
它既被跳过名单排除、又不在配对表里，于是**不出现在报告的任何一行里**，
从总数上静默消失。被这样漏掉的四个文件是：

| 文件 | 行数 | 当时被记为 |
| --- | --- | --- |
| `core/ZhiCodeEngine.java` | 1353 | 0 重合（实测 1238 行相同，99.2%） |
| `com/zhizhu/zhicode/ZhiTool.java` | 33 | 0 |
| `com/zhizhu/zhicode/ZhiDocumentsProvider.java` | 126 | — | 批 A 已删除 |
| `com/zhizhu/zhicode/ZhiFileProvider.java` | 199 | 0 |

最严重的是第一个：它是全工程最大的单文件、且几乎整文件与原版逐行相同，
却因为这条漏算在表里显示为 0 —— 按这张表排批次会**正好把最大的一块漏掉**。

现在脚本加了两条自检，都是「宁可吵闹也不静默」：
1. 跳过名单（`RENAMED_BASENAMES`）与配对表（`PAIRS`）必须一致，
   名单里有而配对表里没有的，直接打警告；
2. 配对表指向的本工程/原版文件必须真的存在，写错名字会报出来。

两条都做了反向验证：删掉 `ZhiCodeEngine` 的配对 → 报「静默算成 0」；
把原版文件名改成 `IQCodeEnginTypo.java` → 报「原版文件不存在」；恢复后无警告。

**一个重要的范围澄清**：重合行数最大的那些文件里有相当一部分**不是 IQ Code 的代码**：

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
| `FridaEnv` | 135 | 289 | 31 | 10.7% |
| `SandboxKeeper` | 34 | 99 | 12 | 12.1% |
| `ZhiSandbox` | 190 | 332 | 46 | 13.9% |
| `SandboxRpcService` | 110 | 246 | 42 | 17.1% |
| `SandboxConsole` | 78 | 212 | 37 | 17.5% |
| `SandboxGuestDebug` | 288 | 572 | 119 | 20.8% |
| `SandboxPrefs` | 96 | 146 | 33 | 22.6% |
| `SandboxFrida` | 141 | 339 | 75 | 22.1% |
| `SandboxProcess` | 40 | 69 | 18 | 26.1% |
| `SandboxRpc` | 29 | 45 | 13 | 28.9% |
| `SandboxStage` | — | 110 | — | 新文件 |
| `SandboxPalette` | — | 93 | — | 新文件 |

**没有重写的部分，如实说明：**

- `SandboxFrida` 里那段 agent JS 的**载荷行为**是**刻意冻结**的：`rpc.exports`、
  信箱文件名、ready 标记是 Java 与脚本之间的协议边界，改一侧必须同步改另一侧；
  它的行为细节（有界扫描、部分失败可返回、legacy scanSync 重写、eval 沙箱与超时）
  都有回归断言盯着，重写只会引入行为漂移。
  该类的 22.1% 主要就是这段载荷。
  **改过的只有名字**：注入对象由旧品牌标识改为 `Zhi`（`Zhi.emit` / `Zhi.hooks` /
  `Zhi.scan`），磁盘文件名改为 `libzhifrida.so` / `libzhifrida.config` / `zhi-agent.js`。
  名字不是行为，但它会同时出现在工具 schema 与系统提示词里，所以三处必须一起改。
- **Agent 运行时仍在进行中**，是当前比例最高的残留区（见下文「仍然剩下的」）。

### 改名与旧数据（A–D 与阶段 0.5）

把源码里旧品牌相关的字符串全部清掉，**只改名，不改行为**。
到阶段 0.5 结束时，源码（`app/src/main`）里已经**一个都不剩**：
`iq_code_*`、`<iq_internal_continue>`、`.iq`、`IQ.md`、`libiqfrida*`、`iq-agent.js`、
`ctoken.top`、`com.iqge`、`iqcode-*`、以及 `IQ.emit/hooks/scan` 全部消失。

**代价是明确接受的**：为了不留任何旧名字，历史上那几处「只读兼容」被一并删除。
这意味着从更早版本升级上来时，旧名字下的设置、API Key 与旧会话里的内部续跑标记
不再被识别。取舍理由是不留旧品牌标识这件事优先。

各项改动的具体内容：

- **A（`7a390a9`）** 品牌串与线程名，并修掉两个真 bug：
  `iq-patch-deb` 这个脚本名从来没存在过（正确名由 `BRAND_SLUG` 派生），
  以及 `KeepAliveService` 写的是旧 prefs、导致关掉保活不会生效。
- **B（`312c89c`）** 数据目录改名与记忆文件改名。
- **C（`3829aa9`）** `LocalIqDark`、空态文案、注释里的旧产品名。
- **D（`53be6c3`）** Gadget 磁盘文件名 `libiqfrida.so` → `libzhifrida.so`。
- **阶段 0（`12e019c`）** 下线内置的厂商 API 配置（`OFFICIAL_*` 常量、注册链接、
  「不可删/不可改」保护），并加了一次性清除 + `NoBundledThirdPartyEndpointTest`。
- **阶段 0.5** 清除全部旧品牌残留：所有「只读兼容」与迁移逻辑删除，
  `LegacyDataMigration` 整个删掉，数据目录候选列表收敛为单一位置，
  注入脚本对象名改为 `Zhi`。

**一处仍然保留的引用**：`THIRD-PARTY-LICENSES/IQ-Code-MIT.txt` 与 `NOTICE` 里
对 IQ Code 的署名。这不是残留，是 MIT 的硬性要求（版权声明必须随附）。
等到「仍与原版逐行相同的行数」降到 0 之后再考虑是否撤销，届时需要单独确认。

### 仍然剩下的（按重合行数排序）

`PROVENANCE_PER_FILE=1 bash tools/provenance.sh` 会打印完整清单。当前最前面的几项：

| 相同 | 原版 | 现在 | 重合 | 文件 | 处置 |
| --- | --- | --- | --- | --- | --- |
| 492 | 1245 | 2094 | 23.5% | `com/termux/app/zhicode/core/ZhiCodeEngine.java` | 已重写；余量是 Listener 接口与用户可见文案 |
| 256 | 622 | 929 | 27.6% | `com/termux/app/zhicode/api/OpenAIResponsesProvider.java` | 已重写 |
| 204 | 354 | 545 | 37.4% | `com/termux/app/zhicode/tasks/TaskStore.java` | 已重写 |
| 199 | 642 | 1138 | 17.5% | `com/termux/app/zhicode/storage/SessionStore.java` | 已重写 |
| 195 | 390 | 619 | 31.5% | `com/termux/app/zhicode/core/ContextCompactor.java` | 已重写（模型文本有基线测试钉住） |
| 189 | 468 | 929 | 20.3% | `com/termux/app/zhicode/termux/TermuxShellExecutor.java` | 已重写 |
| 156 | 451 | 596 | 26.2% | `com/termux/app/zhicode/api/OpenAIChatCompletionsProvider.java` | 已重写 |
| 141 | 310 | 607 | 23.2% | `com/termux/app/zhicode/tools/AndroidIntentBridge.java` | 已重写 |
| 122 | 257 | 460 | 26.5% | `com/termux/app/zhicode/api/AnthropicMessagesProvider.java` | 已重写 |
| 102 | 181 | 319 | 32.0% | `com/termux/app/zhicode/tools/UnifiedDiff.java` | 已重写 |
| 100 | 139 | 207 | 48.3% | `com/termux/app/zhicode/model/PlanWorkflowState.java` | 已重写（批 H）；余量见下节，是声明与签名 |
| 100 | 583 | 945 | 10.6% | `com/zhizhu/zhicode/TermuxTerminalPane.java` | **已重写（批 F 2/6）；余量是 31 行 import + 只能在 View 侧写的 PTY 调用** |
| 93 | 331 | 699 | 13.3% | `com/termux/app/zhicode/storage/ApiSettingsStore.java` | 已重写 |
| 89 | 288 | 584 | 15.2% | `com/zhizhu/zhicode/sandbox/SandboxGuestDebug.java` | 已重写（批 I）；余量是动作名与 JSON 键（协议） |
| 75 | 141 | 339 | 22.1% | `com/zhizhu/zhicode/sandbox/SandboxFrida.java` | 已重写；余量主要是内嵌 Frida 脚本的大串 |
| 75 | 98 | 152 | 49.3% | `com/termux/app/zhicode/core/PlanApprovalGate.java` | 已重写（批 H）；同上 |
| 74 | 136 | 234 | 31.6% | `com/zhizhu/zhicode/ZhiFileProvider.java` | 已重写 |
| 53 | 299 | 886 | 6.0% | `com/zhizhu/zhicode/sandbox/SandboxGuestHost.java` | 已重写 |
| 50 | 76 | 130 | 38.5% | `com/termux/app/zhicode/api/HttpRequestTracker.java` | 已重写（批 H）；同上 |
| 46 | 55 | 120 | 38.3% | `com/termux/app/zhicode/termux/BashCompletionCoordinator.java` | 已重写（批 H）；同上 |


### 批 F（2/6）：终端面板改用 Compose 重写（已完成，待真机复验）

这一块是**全工程最大的单个残留**：`com/zhizhu/zhicode/TermuxTerminalPane.java`
583 行里有 565 行与原版逐行相同（95.4%）。拆开看它由两部分组成：

- **外壳**：工具栏、左边缘手势、会话抽屉、两行扩展键、快捷动作与重命名两个对话框、
  主题刷色、`termux.properties` 读取 —— 全是 IQ Code 自己写的界面代码；
- **终端本体**：Termux 上游的 `TerminalView`（Canvas 逐字符绘制 + CSI 解析 + 选区 +
  缩放手势，2453+1297 行，属那 7274 行 Termux 上游代码）。

所以重写的是**外壳**，终端本体仍然由 `AndroidView` 承载（`TerminalPane.kt` 里早就写明
「不把 TerminalView 重写成 Compose」，这个判断继续有效）。

**另一个发现**：`UiMotion.java` 的全部 25 个调用点**都在这个文件里**。
外壳一改成 Compose，它就成了死代码 —— 于是它连同 104 行重合一起被删除。
`UiMotion` 的职责（进场动画、按压反馈、抽屉滑动）现在由 `ZhiMotion` + Compose 的
`AnimatedVisibility` / `animateDpAsState` 承担。

结构：

```
compose/ui/panes/TerminalChrome.kt    工具栏 / 抽屉 / 扩展键 / 两种失败占位 / 状态接线
compose/ui/panes/TerminalDialogs.kt   快捷动作（11 项）与重命名
compose/ui/panes/TerminalPane.kt      接线（TerminalHolder 签名未变）
com/zhizhu/zhicode/TermuxTerminalPane.java   只剩 PTY：会话列表、JNI、环境变量、
                                             WakeLock、剪贴板、ANSI 调色板、两个 Client 接口
```

宿主对 Compose 的接口是「**不可变快照 + 变更回调**」而不是 `StateFlow`：
它是 Java，而 `StateFlow` 是 Kotlin 类型，从 Java 构造要绕到 `StateFlowKt` ——
那等于让最底层的宿主反过来依赖界面框架。回调只发**低频**变化：
`onTextChanged`（每次按键回声都触发）只负责让 `TerminalView` 重画，**不**通知观察者，
否则每敲一个字符就要重建快照并驱动一整轮 Compose 重组。

结果：

| | 重合行 | 现在行数 |
| --- | --- | --- |
| `TermuxTerminalPane.java` | 565 → **100** | 592 → 945 |
| `UiMotion.java` | 104 → **0**（文件已删） | 392 → 0 |
| 全工程「真正属于 IQ Code」 | 5120 → **4551** | |

那剩下的 100 行是：31 行 `import`、若干 `}`、字段与构造器声明，以及
**只能在 View 一侧写**的 PTY 调用 —— `new TerminalView(getContext(), null)`、
`view.attachSession(attached)`、`view.post(...)` 里的三重校验、`JSONArray` 解析骨架。
这些相同不是因为抄，而是因为它们就是「在 View 里驱动这个上游渲染器」的唯一写法。
文件行数涨了（592 → 945）是刻意的：注释写明了每处不许改的原因。

**外观只动了一处**：外壳配色改为跟随深浅主题（原先写死深色档）。深色档色值与改动前
逐字节相同（就是 `applyPaletteValues` 的那张表），浅色档启用了原作者写下但从未走到过的那张。
终端的 ANSI 调色板**没动** —— 让它跟随主题是另一次可见变更，不夹在结构重写里做。

**验证**：新增 `app/tests/TerminalPaneContractTest.java`（文本级第 23 条），钉住三件
「改坏了不报错、只会在真机上表现为终端不好用」的事：12 个扩展键的转义字节（用解码后的
字面量做**相邻性**检查，所以 ESC 与 TAB 的序列被互换会被抓出来）、11 项快捷动作的标签
与顺序（调用方按下标分发，重排会让「字体变大」点成「杀掉 shell」）、四个修饰键的
读后即清语义。另有编译、JVM 单测、provenance 全量重跑。
**UI 行为尚未验证**：沙箱与真机的逐步比对在下面「验证」一节里另有记录。


### 批 H 的结论与预期相反：这四个文件已经没得改了

批 H 原本按上面的表面数字排出来 —— 四个「重合率 38%~55%」的小文件，
看着像最好啃的一块。真去读之后结论是反的：

| 文件 | 非空行（原版→现在） | 相同 | 这些相同行是什么 |
| --- | --- | --- | --- |
| `model/PlanWorkflowState.java` | 139 → 207 | 100 | 5 个枚举成员、9 个 `public final` 字段、12 个方法签名、2 处状态判断 |
| `core/PlanApprovalGate.java` | 98 → 152 | 75 | 3 个枚举成员、内部类声明、7 个 `import`、`try/finally` 骨架 |
| `api/HttpRequestTracker.java` | 76 → 130 | 50 | 2 个超时常量、`Scope` 内部类声明、10 个方法签名 |
| `termux/BashCompletionCoordinator.java` | 55 → 120 | 46 | 2 个 `CountDownLatch` 字段、4 个 getter 签名、`parseControlLine` 的 8 行 |

把相同行去重后逐条看，真正的**算法行**每个文件只剩 4~6 行，且都是「Java 里只有
一种写法」的那种：`long now = Math.max(System.currentTimeMillis(), updatedAt + 1L);`、
`if (separator != token.length() || separator <= 0) return null;`。

所以这一批实际能做的只有两件事（都已做）：

1. **去掉同一件事的两份写法**
   - `PlanWorkflowState`：六个推进操作里有五个不动计划正文，却各自把
     `planFile, planText` 两个参数写了一遍 → 收成一个三参数的 `step(...)` 重载；
     `copy()` 与 `restore()` 原本各写一遍九个字段 → `copy()` 改为委托；
   - `BashCompletionCoordinator`：`publishControl` 与 `publishProcessExit` 里
     那 5 行「定下退出码」逻辑逐字相同 → 收成一个 `resolve(code, from)`，
     「先到者为准」的判定从此只有一处；
   - `PlanApprovalGate`：两个 catch 块做的是同一件事 → 合成
     `catch (InterruptedException | RuntimeException failed)`（精确重抛会原类型抛出，
     签名已声明 `InterruptedException`，合法）。
2. **删掉确认没人调用的 API**（`withPlanText` / `withPlanFile` / `awaitApproval` /
   `activeCount`，全仓库含 Kotlin 与测试搜不到调用方）。
   `pendingRequests()` **留着**：它是「批准卡片必须能被重新拿到」这条要求唯一的落点，
   同 `steeringToolInterrupt` 的处理 —— 在注释里写明它现在没有调用方，而不是删掉。

结果：四个文件合计 291 行相同 → 271 行（-20）。行数反而涨了（注释变多），
这与批 D 一样是刻意的。

**这条结论要记住**：`PROVENANCE_PER_FILE` 的重合率是「相同行 ÷ 现在行数」，
它**不区分**相同的是算法还是签名。所以按重合率排批次会系统性地把
「公开面大、逻辑少」的数据类排到前面 —— 而恰恰是这类文件最没得改。
下一步该按「相同行里有多少是算法」排序，不是按重合率。



### 批 I：沙箱宿主层（已完成，但只有一件真正可改）

沙箱宿主层 14 个文件合计 563 行相同。这些文件全都改过名
（`SandboxGuestDebug` ← `SandboxProcessDebug`、`ZhiSandbox` ← `IQSandboxEngine` 等），
所以必须走 `PAIRS` 配对表，按路径找是找不到的。

先用三桶口径逐文件量了一遍，**结果决定了这一批只做了一个文件**：

| 文件（现在 ← 原版） | 相同 | 骨架 | 字面量 | 其它 |
| --- | --- | --- | --- | --- |
| `SandboxGuestDebug` ← `SandboxProcessDebug` | 119 | 51 | 34 | **34** |
| `SandboxFrida` ← `SandboxFridaBridge` | 75 | 21 | 43 | 11 |
| `SandboxGuestHost` ← `SandboxAgentBridge` | 53 | 45 | 0 | 8 |
| `ZhiSandbox` ← `IQSandboxEngine` | 46 | 36 | 0 | 10 |
| `SandboxRpcService` ← `SandboxControlProvider` | 42 | 27 | 7 | 8 |
| `SandboxConsole` ← `SandboxDebugLog` | 37 | 19 | 2 | 16 |
| `SandboxBoard` ← `SandboxDashboardActivity` | 37 | 33 | 0 | 4 |
| `SandboxPrefs` ← `SandboxSettingsStore` | 33 | 19 | 0 | 14 |
| `FridaEnv` ← `FridaRuntimeManager` | 31 | 21 | 6 | 4 |
| `SandboxOverlay` ← `SandboxFloatingController` | 24 | 16 | 0 | 8 |
| `SandboxShell` ← `SandboxTermuxBridge` | 23 | 23 | 0 | **0** |
| `SandboxProcess` ← `SandboxProcessRole` | 18 | 11 | 1 | 6 |
| `SandboxRpc` ← `SandboxHostClient` | 13 | 9 | 0 | 4 |
| `SandboxKeeper` ← `SandboxGuardService` | 12 | 12 | 0 | **0** |
| 合计 | 563 | 343 | 93 | **127** |

两个文件（`SandboxShell`、`SandboxKeeper`）的相同行**全是括号与 import**。
把这 127 行按内容打出来看，绝大多数是 `break;`（13 个）、`return true;`、
`try {`、`case MotionEvent.ACTION_DOWN:`、方法签名与字段声明。
真正的算法只有十来行，而且都是绕不开的：`System.load(canonical);`、
`Runtime.getRuntime().gc();`、`while ((b = in.read()) != -1 && b != 0) out.write(b);`

所以只改了一个：**`SandboxGuestDebug` 的 13 分支 switch**。
`dispatch()` 原先是一个十三个 case 的 switch，而每个 case 做的都是同一件事——
算一个值、放进同一个信封、`break`。同一件事写十三遍的代价不是长度，
而是「加一个动作要动四行、还得自己找对位置」。现在是一张
`Map<String, Route>` 路由表（键名 + 算法），加一个动作只加一行。

这一处就吃掉了这一批的大部分余量：

    相同行   119 → 89
    其它桶    34 → 18
    沙箱宿主层 563 → 533

**剩下的 93 行没有再动**，因为它们要么是协议串（动作名、JSON 键，宿主与 Agent 工具
按同一份名字对齐，改了会让两边对不上），要么是 `return true;` 这种没有第二种写法的行。
为了把数字压低去重排一个 `try` 的位置，只会让代码变难看。


### 批 E（1/3、2/3）：终端执行器、任务存储与提示词装配（已完成）

| 文件 | 重合 | 现在 | 做了什么 |
| --- | --- | --- | --- |
| `tools/AndroidIntentBridge.java` | 297 → 141 | 23.2% | 三类意图各自成方法；`am start` 解析与 shell 词法分析独立出来 |
| `agents/AgentDefinitionLoader.java` | 126 → 45 | 30.0% | 解析与合并分开；四层来源的优先级写进注释 |
| `background/*` 之外的小件 | — | — | `AndroidSecretStore`、`PermissionGate`、`VisionMessageFilter` |
| `core/SystemPromptBuilder.java` | 48 → 6 | 2.5% | 规则清单从字符串相加改成数组 |
| `tasks/TaskStore.java` | 352 → 204 | 37.4% | 一任务一文件、id 高水位文件、进程内锁 + 文件锁 |
| `termux/TermuxShellExecutor.java` | 435 → 189 | 20.3% | 拆成启动前检查 / 包管理锁 / 进程构建 / 等待完成 / 收尾 |
| `storage/SessionStore.java` 等 | 见上一节 | — | 存储层统一走 `AtomicFiles` |

这一批里最需要记下来的一件事是**系统提示词的逐字节校验**。

`SystemPromptBuilder` 是行为契约：那些英文句子决定 agent 会不会先确认再动手、
会不会声称自己做了没做的事。改写它属于「改动行为」，不能和「改写实现」混在一起。
所以这次不是靠看，而是**用程序验**：把改动前后两个版本的 `build()` 各自编译成一个
不依赖 Android 的独立程序（把四个 `config.*` 字段访问换成固定值），用同一组输入打印
提示词，然后比较：

```
old  12381 bytes
new  12381 bytes
cmp  →  完全相同
```

顺带修掉一个更隐蔽的毛病：`SandboxIntegrationStructureTest` 与
`FridaDeadlockRegressionTest` 都对 `SystemPromptBuilder.java` 的**源码**做 `contains`，
于是「换行打在哪个位置」也变成了契约 —— 重排之后它们失败了，而提示词输出并没有变。
两个测试现在改成先从源码里抽取双引号字面量、拼回内容再比较，检查的是
「这个文件最终会输出什么文字」，而不是「作者把这些字打在哪儿」。

### 批 E（3/3）：存储层最后两块（已完成）

| 文件 | 重合 | 现在 | 做了什么 |
| --- | --- | --- | --- |
| `storage/SessionStore.java` | 284 → 199 | 17.5% | 「读」与「写」各收敛成一个入口，见下 |
| `storage/ApiSettingsStore.java` | 109 → 93 | 13.3% | 设置项改成一张表，读写同源 |

`SessionStore` 的三处结构收敛：

1. **三种读法收成一个 `scan(file, strict)`**。原先宽容读（`readRows`）与严格读
   （改历史前必须「整个文件都读懂了」）是两段几乎一样的逐行循环。合成一个之后
   `strict` 就不是一个零散的布尔参数，而是「这份数据我能不能安全重写」这件事本身。
2. **两种写法收成一个 `appendLines(file, lines, durable)`**。区别只有是否 fsync：
   消息是高频写入（每次 fsync 会让打字卡顿），改备注是低频且重要。原先两份方法体
   完全重复。
3. **整文件替换改走 `AtomicFiles`**。「写临时文件 → fsync → rename」这段在上一批已经
   抽成 `AtomicFiles` 了，这里跟着用，本类只保留它特有的一步：替换后恢复原 mtime。

另外把 `ROW_*` / `"type"` / `"payload"` 这类固定字符串收进 `RowType` / `Role` /
`Block` / `Field` 四个内部常量类。理由不是好看：`"payload"` 在原先的代码里出现十几次，
每处各写一份字面量的话，改字段名时漏掉的那一处只表现为「某类历史读不出来」。

`ApiSettingsStore` 的改动更值得说，因为它修的是一类真实缺陷：原先 `loadGlobal()` 与
`saveGlobal()` 是两份各 25 行的键名清单，必须一直保持一致 —— 而「加了新设置但只加了
一半」的后果是「设置能存进去、重启后消失」，不报错、不崩，只表现为用户抱怨。
现在只有一张 `SETTINGS` 表，读与写都从它派生，默认值直接取 `SessionConfig` 的字段初值
（所以也不存在「默认值改了一处忘了另一处」）。压缩比例是 double、prefs 只支持按位存
long，因此显式留在表外。

这两块都没有改行为：`SessionStore` 的公开方法签名一个没动，`ApiSettingsStore` 的
prefs 键名与值格式一字未改（键名是持久化契约，改了等于丢用户设置）。
### 批 D（1/2）：Agent 主循环（已完成）

这是全工程最大的一块（1233 行重合，98.6%），也是唯一一块「改错了会让 agent 变傻」的地方。

| 文件 | 重合 | 现在 | 做了什么 |
| --- | --- | --- | --- |
| `core/ZhiCodeEngine.java` | 1233 → 492 | 23.5% | 主循环、历史自愈、上下文压缩三段各自拆开 |
| `core/SteeringQueue.java` | 新文件 | — | 预输入队列与两个打断标记从引擎里搬出来 |

主循环原来的形状是一个三百多行的双层 `try`，`continue` 散布在四处。现在按「一轮”拆开：

```
runAgent        异常分类（取消 vs 真错误）与收尾
 └ runTurnLoop  maxAgentTurns 的计数
    └ runOneTurn  请模型回一条消息
       ├ finishAssistantTurn   没有工具调用：收尾 / 续跑 / 空回复重试
       └ runToolPhase ─ runToolCalls  执行工具并写回结果
```

拆的时候有三处**不能**拆错的语义，都写进了注释：

1. `continue` 在 `for` 循环里会执行 `turn++`，所以拆成「返回 false 表示再来一轮」之后，
   计数必须由调用方推进 —— 写错的话 `maxAgentTurns` 会变成一个不生效的上限。
2. `onToolBatchCompleted` 在 `finally` 里，某条工具抛异常时也必须发出去，否则界面上
   那一批的进度条会一直转。
3. 工具结果必须写回历史，**即使后面的工具被取消**。丢掉它们等于让模型以为那些工具
   从没被调用过，它会重做一遍 —— 而重做的可能是有副作用的操作。

顺手修掉的一处**死变量**：`repairToolHistory` 的第一遍扫描里算了三个集合
（`callsSeenInOrder` / `paired` / `allCalls`），其中 `allCalls` 从头到尾没被读过，
`callsSeenInOrder` 也只在那一遍里用。整个第一遍其实就是在问一句
「哪些调用先出现、之后才出现同 id 的结果」，现在它就是一个方法
（`findPairedToolCallIds`）。

### 一处**从未被置为 true** 的标记（记下来，因为它看起来很像是活的）

`steeringToolInterrupt` 与 `steeringModelInterrupt` 两个标记，在引擎里只有
`getAndSet(false)`、`get()` 和 `set(false)` 三种读法 —— **没有任何地方把它们置为 true**。
也就是说：

- 「模型请求被预输入打断」那条分支（它会保留已经流出来的半截回复、通知界面、
  然后按新输入继续）永远走不到；
- 「工具执行因为用户纠正而中断」那条错误文案也永远走不到；
- `consumeSteeringInterruptIfNeeded()` 里那句 `Thread.interrupted()` 永远不会执行。

这一批**没有删掉**它们，而是把它们搬进了 `SteeringQueue`，并在类注释里写清了
「目前没有任何地方把它们置为 true」。理由是界面（`ZhiEngineController`）已经实现了
`onResponseInterruptedBySteering`，说明这条路径是被期望存在的；删掉标记等于把这个
功能的存在证据一起删掉。留着的代价是一个读者可能误以为它在起作用 ——
所以那句话必须写在那儿。

### 行数涨了，而且是刻意的

| | 原版 | 重写前 | 重写后 |
| --- | --- | --- | --- |
| 非空行 | 1245 | 1250 | 2094 |
| 其中注释 | 46 | 48 | 442 |
| 其中代码 | 1199 | 1202 | 1652 |

代码多了约 38%，注释多了近十倍。两个原因：一是原先大量「一行三个语句」的紧凑写法
在重写时必须展开（紧凑写法正是与原版逐字相同的那部分）；二是这次把**行为契约**
写在了函数旁边，例如「`continue` 会执行 `turn++`」「工具结果即使被取消也要写回」
「提交压缩前必须比对历史」—— 这些是改动这个文件时最容易踩坏、而踩坏了不一定报错的地方。

### 批 D（2/2）：`core/ContextCompactor.java`（已完成）

388 → 195 行重合（99.5% → 31.5%）。它负责压缩的切点计算、摘要提示词组装与结果解析。

这一块的特别之处在于：它产出的东西几乎全是**给模型看的**（摘要提示词、压缩后的上下文
包裹格式、被缩减过的历史），而且改坏了不会报错 —— 只会让摘要质量悄悄变差。

所以这里用的办法与 `SystemPromptBuilder` 那次一样，但做得更硬：

1. 先写一个一次性探针，把**重写前**的实现对同一组输入（覆盖 text / thinking / tool_use /
   tool_result / image 五种块）的完整输出原样 dump 出来；
2. 把 dump 存成 `app/src/test/resources/compaction-prompt-baseline.txt`；
3. 重写之后，由 `ContextCompactionTextTest` 重新生成同一份 dump 并与基线逐字比较。

这条断言是长期保留的 —— 以后再动这个文件，提示词改了一个字都会红。
基线文件里存的就是那些句子本身，可以直接查阅，不需要相信任何人的描述。

除此之外的处理：三个重试档位（正文 30k/14k/8k、工具 8k/3.5k/2k）从内联三目改成数组；
摘要提示词的九条要求从九行 `append` 改成一份 `SUMMARY_SECTIONS` 数组（它是一份清单，
要能一眼看出有没有漏掉某一类信息）；逐消息、逐块的 token 估算各拆成一个方法。

### 批 B：`api/` 协议层（已完成）

这一批针对工程里「最不该看着像原版」的地方 —— 协议层是纯粹的技术实现：
没有界面、没有用户可见的取舍，写出来长什么样完全取决于写的人。

已经重写完的（每一条都是先写契约断言、再改实现）：

| 文件 | 重合行 | 现在 | 做了什么 |
| --- | --- | --- | --- |
| `api/compat/ReasoningMapper.java` | 97% → 16.4% | 档位映射拆成明确的分档表，删掉两个零调用的死方法 |
| `api/AnthropicMessagesProvider.java` | 98.8% → 26.5% | 传输 / `StreamDecoder` / 块累积器三层 |
| `api/OpenAIChatCompletionsProvider.java` | 99.6% → 26.2% | 统一流式与非流式的字段读取，抽出 `HistoryMapper` |
| `api/OpenAIResponsesProvider.java` | 99.8% → 27.6% | `HistoryBuilder` / `StreamDecoder` / `KeyIndex` 四层 |
| `api/ModelCatalogClient.java` | 100% → 21.4% | 网络与解析分开；协议名不再内联 |
| `api/HttpRequestTracker.java` | 88% → 39.6% | 失败归类提成纯函数 `RequestFailure` |
| `api/ModelProvider.java` | 100% → 26.9% | 接口只留两个方法，两个嵌套类型提成顶层 |

顺带做掉的重复：`stripTrailingSlash` 原来在四个文件里各有一份（三个 provider 各私有
一份 + 端点类一份），现在只剩 `ApiEndpointResolver` 里那一份。它们其实并不完全一样 ——
Anthropic 那份遇到 `null` 会抛 NPE，另两份返回空串；合并时取了后者
（更宽容，且没有任何调用点依赖那个 NPE）。

另外两个协议名常量集合并成了一个枚举 `ApiProtocol`。原来「协议名 → 实现」和
「协议名 → 目录端点」是两条各自内联字符串的 `if` 链，而协议名是**存盘格式的一部分**，
写错一处不会编译失败，只会表现为「报文格式对不上、服务端报解析错误」。
收进枚举之后，「认识的协议」只有一处定义，而且 `switch` 覆盖枚举要求列全，
将来加了协议却忘了分派会直接编译不过。

小文件的重合率有**下限**：`api/ApiUrlPolicy.java` 从 43% 降到 18.5%，剩下的 10 行是
`package`、两个 `import`、类声明、`return base;` 这种「一行只干一件事」的行 ——
任何人在这个需求下都会写出同样的形状。判断「改没改写」不能只看百分比，
要看**算法与结构**：上表里降到大件的都换了结构，剩下的百分比都落在这类不可避免的行上。

### 批 C：数据类与 Agent 工具（已完成）

这一批是「接口与数据形状」层：字段名、工具名、schema 键名、输出格式都是**契约**
（写进了会话 JSONL、设置、以及发给模型的提示词），所以改的只能是内部结构、
分解方式与命名。共 40 个文件、重合行 8632 → 7917（-715）。

按性质分三组。

**值对象（字段名一律不动）**：`model/PlanWorkflowState`（55.1% → 新的状态机写法）、
`model/SessionConfig`、`model/ApiProfile`、`model/ToolExecutionResult`、
`model/AssistantTurn`、`model/ToolCall`、`agents/AgentDefinition`、`agents/AgentTask`。
其中三处是真正的结构改动，不只是重写注释：

1. `PlanWorkflowState` 把「哪个阶段允许哪种操作」写成了一个统一的前置检查
   （`requireStatus` / `requireWorkflow`），并**把时间戳改成严格单调**：
   新快照的 `updatedAt` 取「当前时间」与「上一版 + 1」的较大者。系统时钟被回拨时
   （对时、换时区），原来的写法会让新快照看起来比它替换掉的那一版更旧，
   而界面是按时间戳判断版本的。
2. `SessionConfig.copy()` 把 volatile 的 `planWorkflowState` 先落到局部变量再拷：
   原来直接读两次，两次之间可能被别的线程换掉，于是拷到一个「半新半旧」的组合。
3. `ToolExecutionResult` 的六个工厂方法收敛到一个私有构造路径，
   每一种「成功/失败 + 有无 diff + 有无附加内容」的组合都由一个具名方法表达。

**路径与 schema 基础件**：`tools/PathPolicy`（`Set.of` 换成 `Arrays.asList`，见下）、
`tools/ToolSchemas`、`tools/ZhiTool`（接口，签名未动）、`core/PermissionModePolicy`。

`ToolSchemas` 的重写解决了一个真实的签名污染：`JSONObject.put` 声明受检的
`JSONException`，于是原来每个工具的 `inputSchema()` 都要自己写 try/catch
（45 行里有 5 段几乎相同的 catch）。现在只有一处 `with(...)` 负责转换，
其余地方链式拼装。

`PermissionModePolicy.capSubagent`（97 行 → 89 行，重合 35 → 21）把原来嵌套四层
`if` 的判断改成了一张「父级模式 → 子代理可以要求哪些模式」的表。表里的每一行都能
从一条原则推出来（**子代理只能收紧、不能放宽父级权限**），而原来的嵌套 if 读不出
这条原则 —— 改错一个分支的后果是某个子代理悄悄获得了它不该有的写权限。

**Agent 工具（36 个）**：`Read`/`ReadMany`/`Stat`/`Tree`/`LS`/`Glob`/`Grep`/
`Write`/`Edit`/`MultiEdit`/`Copy`/`Move`/`Mkdir`/`Delete`/`TaskCreate`/`TaskGet`/
`TaskList`/`TaskUpdate`/`mcp_list`/`mcp_call`/`GitStatus`/`EnterWorktree`/`AndroidIntent`/
`Root`/`ui_canvas`/`ToolRegistry` 等。

工具名、schema 属性名、输出格式与错误文案全部保持逐字不变（它们都被测试与提示词
依赖）。改的是：把重复的文件读写抽成 `TextFiles`；把每个工具里的内联上限判断
提成具名常量并在注释里写清「这个上限挡的是哪一种失败」；
`ToolRegistry` 的构造从 36 条平铺的 `register(...)` 改成按用途分组的清单，
顺序仍然固定（顺序决定提示词内容，从而决定上游的提示词缓存能不能命中）。

**一个必须记下来的坑**：第一批写法里用了 `Set.of(...)` / `List.of(...)`。
它们是 Java 9 的 API，在 Android 上要 API 30 才有，而本工程 minSdk 24
且**没有开启核心库脱糖** —— 也就是说编译会通过、在 Android 7～9 的设备上
一运行就 `NoSuchMethodError`。已全部换成 `Arrays.asList`。
这个错误编译器和单元测试都发现不了（单元测试跑在 JDK 上，那里什么都有），
所以它值得单独写在这里。

**另一个坑**：`GlobTool` 的 javadoc 里写了正则片段 `(?:.*/)?`，
其中的 `*/` 提前结束了注释块，后面整段代码被当成类体外面 —— 报错是
「illegal start of type」加一串看不懂的错位信息。写含正则的注释时要注意这个。

### 批 A：删掉的死代码（已完成）

这四个文件在整个工程（含脚本、清单、Bcore）里都搜不到引用，逐个确认后删除：

| 文件 | 行 | 确认方式 |
| --- | --- | --- |
| `com/zhizhu/zhicode/MarkdownRenderer.java` | 265 | 全仓 grep 无任何引用；Compose 侧有自己的 Markdown 渲染层 |
| `com/zhizhu/zhicode/ToolActivityGrouper.java` | 91 | 全仓 grep 无任何引用 |
| `com/zhizhu/zhicode/ZhiDocumentsProvider.java` | 126 | 全仓 grep 无引用，且**清单里根本没有注册**（DocumentsProvider 不注册就不可能被调用） |
| `com/zhizhu/zhicode/FloatingOverlayService.java` | 130 | 只在清单里注册，代码里没有任何启动点 |

顺带做了一件必要的事：`FloatingOverlayService` 是 `SYSTEM_ALERT_WINDOW` 权限的唯一使用者，
它既然删了，那条权限声明也一并去掉 —— 留着一个没有使用者的「可在其它应用上层显示」权限，
对用户只是无谓的授权请求。`EnvDoctor` 仍会显示这项权限的授予状态，那只是诊断信息。

删除后重合行从 10132 降到 **9592**（-540），文件数 189 → 185。
度量工具的自检立刻起了作用：它报出「PAIRS 里的本工程文件不存在:
com/zhizhu/zhicode/ZhiDocumentsProvider.java」—— 删文件后忘了同步配对表，
正是这类自检要防的静默失真。

三点必须说清楚：

1. 头部那些 Termux 文件重合 100% 是**正确状态**，不是漏洞。
   它们由 Apache-2.0 授权且已在 `NOTICE` 里声明，重写它们既无意义也是错的。
2. 排在最前面的**不是** `api/`，而是 `core/ZhiCodeEngine.java`（1238 行、99.2%）。
   它此前因为度量工具的漏算显示为 0，是全工程最大的单块残留。**应最先处理**。
   （它同样出现在 `ZhiTool.java`：27 行、100%，但那是接口，按约定只改实现不动签名。）
3. 真正剩下、值得处理的是 `core/`、`api/` 与 Compose 界面层的几个大文件，
   它们目前几乎是原样保留的。全部逐行相同行数、扣除 Termux 上游之后是 **9592 行**。
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

16 个文件，563 / 4716 行 = **11.9%**（从重写前的 96% 降下来）：

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

# 4. 行为测试（与上一条互补，不是替代）
#    上面那套是文本级断言：读源码字符串，能防「重写时漏掉一个分支」，
#    但证明不了运行时行为 —— 档位映射写错一档、路径去重判断反了，
#    都不会让编译失败。api/ 那一层没有任何 android.* 依赖，所以有真正的 JVM 单测。
./gradlew :app:testDebugUnitTest
```

**两套测试的分工**（判断标准：如果一处改动「写错了也不会编译失败」，它需要的是 JVM 单测）：

| | `app/tests/` + `test-source-no-build.sh` | `app/src/test/` + `testDebugUnitTest` |
| --- | --- | --- |
| 层次 | 源码文本断言 | 真跑逻辑 |
| 依赖 | 只用 JDK 单文件源码模式，零依赖 | `org.json` + JUnit |
| 速度 | 秒级（不编译工程） | 十几秒（要走 Gradle） |
| 能防 | 漏分支、契约被改、声明被删 | 计算结果错、边界条件错 |
| 不能防 | 逻辑写错但不改结构 | 结构性遗漏 |

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
