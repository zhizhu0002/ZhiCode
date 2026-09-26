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
Termux 集成层                       35       8734           2063
Compose 界面层                      57      15427              0
其它                                8       1980           1138
Agent 工具                         44       5673            933
Agent 核心                          8       2444           1804
沙箱宿主层                            16       4716            563
合计                              191      46368          13775

已是我们自己的:        32593 行
逐行相同合计:          13775 行
  其中 Termux 上游:     7274 行（Termux 自己的代码，与独立性无关）
  真正属于 IQ Code:     6501 行
```

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
| 1233 | 1245 | 1250 | 98.6% | `com/termux/app/zhicode/core/ZhiCodeEngine.java` | 批 D |
| 565 | 583 | 592 | 95.4% | `com/zhizhu/zhicode/TermuxTerminalPane.java` | 批 F |
| 388 | 390 | 390 | 99.5% | `com/termux/app/zhicode/core/ContextCompactor.java` | 批 D |
| 310 | 310 | 310 | 100.0% | `com/zhizhu/zhicode/UiMotion.java` | 批 F |
| 256 | 622 | 929 | 27.6% | `com/termux/app/zhicode/api/OpenAIResponsesProvider.java` | 批 B（已重写） |
| 204 | 354 | 545 | 37.4% | `com/termux/app/zhicode/tasks/TaskStore.java` | 批 E（已重写） |
| 199 | 642 | 1138 | 17.5% | `com/termux/app/zhicode/storage/SessionStore.java` | 批 E（已重写） |
| 189 | 468 | 929 | 20.3% | `com/termux/app/zhicode/termux/TermuxShellExecutor.java` | 批 E（已重写） |
| 156 | 451 | 596 | 26.2% | `com/termux/app/zhicode/api/OpenAIChatCompletionsProvider.java` | 批 B（已重写） |
| 148 | 155 | 162 | 91.4% | `com/termux/app/zhicode/agents/SubagentManager.java` | 批 G |
| 141 | 310 | 607 | 23.2% | `com/termux/app/zhicode/tools/AndroidIntentBridge.java` | 批 C（已重写） |
| 122 | 257 | 460 | 26.5% | `com/termux/app/zhicode/api/AnthropicMessagesProvider.java` | 批 B（已重写） |
| 119 | 288 | 572 | 20.8% | `com/zhizhu/zhicode/sandbox/SandboxGuestDebug.java` | 批 G |
| 108 | 139 | 196 | 55.1% | `com/termux/app/zhicode/model/PlanWorkflowState.java` | 批 C |
| 102 | 181 | 319 | 32.0% | `com/termux/app/zhicode/tools/UnifiedDiff.java` | 批 C |
| 93 | 331 | 699 | 13.3% | `com/termux/app/zhicode/storage/ApiSettingsStore.java` | 批 E（已重写） |
| 78 | 98 | 150 | 52.0% | `com/termux/app/zhicode/core/PlanApprovalGate.java` | 批 D |
| 75 | 141 | 339 | 22.1% | `com/zhizhu/zhicode/sandbox/SandboxFrida.java` | 批 G |
| 74 | 136 | 234 | 31.6% | `com/zhizhu/zhicode/ZhiFileProvider.java` | 批 E（已重写） |
| 74 | 80 | 84 | 88.1% | `com/zhizhu/zhicode/background/KeepAliveService.java` | 批 G |
| 57 | 59 | 66 | 86.4% | `com/zhizhu/zhicode/background/RootKeepAliveController.java` | 批 G |
| 53 | 299 | 886 | 6.0% | `com/zhizhu/zhicode/sandbox/SandboxGuestHost.java` | 沙箱层 |

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
