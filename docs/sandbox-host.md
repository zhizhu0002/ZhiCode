# 蜘蛛沙箱宿主层设计

## 为什么要有这份文档

蜘蛛原来的沙箱宿主层是**整份从 IQ Code 搬过来的**：`app/src/main/java/com/zhizhu/zhicode/sandbox/`
下 14 个类共 2028 行，逐行对得上官方 `com.iqge.sandbox`，只把命名和文案换成了蜘蛛。

本轮把这层推倒重写，形成蜘蛛自己的实现。**保留的是引擎，换掉的是宿主。**

## 边界：什么留、什么换

| 层 | 内容 | 处置 |
|---|---|---|
| 虚拟化引擎 | `Bcore/`、`black-reflection/`、`compiler/` | **保留**。它来自上游 `ALEX5402/NewBlackbox`（BlackBox 血统），与上游 `diff -rq` 只差一个预编译 `jniLibs/arm64-v8a/libblackbox.so`。不是 IQ Code 的代码。 |
| 宿主层 | `sandbox/` 下 14 个类 + `ZhiSandboxTool` + `ZhiDebugTool` | **重写** |
| 引擎适配 | `Bcore` 中 4 处硬编码进程名 | **只改这 4 处**，引擎逻辑一行不动 |

引擎不重写的理由：它 hook 了 ART、BootClassLoader IO、以及两百来个系统 Binder 服务。
自研等价物等于重做一个 BlackBox，在没有 x86_64 宿主（无法跑 NDK）的前提下不可行。

## 命名

| 项 | 旧（IQ Code 的） | 新（蜘蛛的） |
|---|---|---|
| 控制器进程 | `:iqsandbox` | `:zhisandbox` |
| Termux 命令 | `iqsandbox` / `iqdebug` | `zhisandbox` / `zhidebug` |
| 代理进程 | `:black` / `:p0..:p49` | 不变（引擎的进程池，不属于宿主层） |

### 刻意保持不变的

- **包名** `com.zhizhu.zhicode.sandbox` —— 本来就是我们自己的，换它只增加改动面。
- **provider authority** `${applicationId}.sandbox.control` —— 换掉会让已安装版本报
  `INSTALL_FAILED_CONFLICTING_PROVIDER`。
- **工具 action 名**（`dump_ui` / `screenshot` / `tap` / `frida_*` …）——
  `SystemPromptBuilder` 与 Agent 系统提示里写死了这些名字，改动会连带改提示词契约。

## 类职责表

新层按**职责**切分，不再照搬 IQ Code 的文件划分。

| 新类 | 替代 | 职责 |
|---|---|---|
| `ZhiSandbox` | `ZhiSandboxEngine` | 引擎门面：attach / create / awaitReady、安装、启动、停止、卸载、清数据、Root 隐藏。对外只暴露这一个入口。 |
| `SandboxProcess` | `SandboxProcessRole` | 进程角色判定：哪些进程该 attach 引擎（`:zhisandbox` / `:black` / `:p0..:p49`），哪些必须保持干净。 |
| `SandboxStage` | （新增） | **每进程一条**的阶段日志。旧实现的 `startup-stage.txt` 是单文件覆盖写，多进程并发时后写的把先写的冲掉——嵌套运行时就因此丢了控制器阶段，无法定位。新实现按 pid 分文件、追加写。 |
| `SandboxConsole` | `SandboxDebugLog` | 跨进程事件日志 + 快照（含 logcat tail）。 |
| `SandboxPrefs` | `SandboxSettingsStore` | 跨进程设置（`hide_root` / `show_floating_log`），原子写。 |
| `SandboxRpc` | `SandboxHostClient` | 同 UID IPC 客户端。 |
| `SandboxRpcService` | `SandboxControlProvider` | 控制器侧的 IPC 服务端，动作分发。 |
| `SandboxGuestHost` | `SandboxAgentBridge` | guest 进程内的控制桥（广播 + 悬浮层 + UI 操作）。 |
| `SandboxGuestDebug` | `SandboxProcessDebug` | guest 内进程调试：maps / 内存读写 / 线程 / 受控 .so 加载。 |
| `SandboxFrida` | `SandboxFridaBridge` | Frida 加载与命令通道。 |
| `FridaEnv` | `FridaRuntimeManager` | Frida 运行时（库文件、Gadget 配置、auto-attach 开关）。 |
| `SandboxBoard` | `SandboxDashboardActivity` | 管理界面。顺带统一成 Miuix 风格。 |
| `SandboxOverlay` | `SandboxFloatingController` | guest 内浮窗控制栏（返回 / 日志 / 停止）。 |
| `SandboxKeeper` | `SandboxGuardService` | 前台保活，保证 guest 盖住主界面时 Agent 执行链不被回收。 |
| `SandboxShell` | `SandboxTermuxBridge` | Termux 侧桥：bridge 目录、CLI 脚本、请求/响应文件通道。 |

## 必须保留的安全边界

重写不得放宽以下任何一条：

1. `load_library` 只接受**本应用私有目录**下的 `.so`（`/data/user/0/<pkg>/` 与
   `/data/data/<pkg>/`，两者都用运行时真实包名派生，不写死）。
   刻意**不**放行 `/data/user/0` 上一级。
2. 内存写入必须保留单次上限与"目标页可写"检查。
3. `/proc/self/mem` 只经受控通道访问，不对外暴露裸读写。
4. 引擎只 attach 在 `:zhisandbox` / `:black` / `:pN`，主进程保持干净。

## 已知问题（重写要修掉的）

**管理界面卡在「正在连接沙箱后端…」**（截图 + 日志双证）：

- `SandboxRpcService.call()` 里 `awaitReady(12000)` 是**同步**等待；
- 而 `doCreate()` 在内层控制器进程里没返回（日志停在 `IQ Sandbox create 开始`）；
- 于是第一次 `status` 调用被挡住，界面停在初始文案。

批次 1 的修法：

- `awaitReady` 超时必须抛出**可读原因**（含该进程最后一次阶段），不能只给一句超时；
- `SandboxStage` 按 pid 分文件，使"哪个进程停在哪一步"可直接查；
- 控制器 create 失败必须让 `status` 立刻返回失败，而不是让调用方一直等。

## 验证

官方 IQ Code 带了一套 17 个沙箱/Frida 结构测试（`app/tests/`，纯 `main()` 断言），
蜘蛛此前没有。本轮把它们按新命名移植进来，作为宿主层的回归网：

```
java app/tests/<Test>.java <归一化工程根>
```

四个断言"必须存在"的不变式（进程角色、进程隔离、启动竞态、WebView/网络透传）
在重写后必须仍然通过。

## 第一次端到端实跑（2026-09-26 23:2x）

在此之前，全部批次只验证过「编译 + JVM 单测 + 文本级断言」。这一节记录第一次把
包真的装进 IQ Sandbox 并跑起来的观察结果，以及**没跑到的部分**。

做了什么：

```
./gradlew :app:assembleDebug                → ZhiCode-debug.apk（47 MB）
iqsandbox install → launch com.zhizhu.code  → 启动成功
screenshot / dump_ui                         → 见下
Debug process_list                           → 见下
```

观察到的事实：

| 项 | 结果 |
| --- | --- |
| 启动 | `MainActivity` 起来，进程 `com.zhizhu.code`（pid 2178） |
| 首屏渲染 | 完整：顶栏（蜘蛛 / 0/200k / 三个图标）、四个标签、欢迎语、输入器 |
| 四个标签 | 「对话」「终端」切换均正常渲染 |
| 终端标签 | `TermuxTerminalPane` 经 Compose 互操作嵌入，会话条 / 终端 / 键盘行齐全 |
| guest 进程 | `com.zhizhu.code:zhisandbox`、`com.zhizhu.code:black` 各自在跑 |
| guest shell | guest 的 `bash` 已 fork 出来（pid 2444），并给出了提示符 `bash-5.3$` |

**这一条值得单独说**：guest 里的 `bash` 能起来并输出提示符，说明 guest 侧的
`fork/exec`、bootstrap 前缀、`LD_LIBRARY_PATH`（重写时保留了它 —— 内置 ELF 的
`DT_RUNPATH` 指向 `/data/data/com.termux/...`）这一整套是通的。

### 发现的问题：guest 里 login shell 读不到 profile

终端第一行输出是：

```
bash: /data/data/com.zhizhu.code/files/usr/etc/profile: Permission denied
```

排查结论是**沙箱路径虚拟化导致的，不是我们代码的缺陷**：

- BlackBox 为 guest 造的虚拟数据目录是
  `/data/user/0/com.iqge/blackbox/data/user/0/com.zhizhu.code/files`；
- 应用内部按 `Context.getFilesDir()` 拼出来的 `PREFIX` 是
  `/data/data/com.zhizhu.code/files/usr`（`TermuxConstants` 第 67/140 行的组合结果）；
- 我这个进程（uid 10330，与 guest 同 UID）去 `stat /data/data/com.zhizhu.code`
  得到的是 **EACCES 而不是 ENOENT** —— 说明那条路径存在、但被 SELinux 挡住；
- 证据：`ls` 报 `Permission denied`，而 `find` 在另一个根下**找不到**任何
  `com.zhizhu.code/files/usr/etc/profile`；
- 而 guest 自己跑 `bash -l` 时，凭同样的 `PREFIX` 能启动（说明它能穿越），
  但读 `$PREFIX/etc/profile` 仍被拒。

也就是说：guest 侧「按 `PREFIX` 取文件」与「内核实际解析该路径」这两件事在
BlackBox 内部并不一致。同一类问题很可能也影响 `apt`（它要读 `$PREFIX/etc/apt/*`）。

**尚未定位到可修的一步**，因此这一条记为已知问题，不做猜测性修改。
要继续查的方向：guest 进程里 `ls -la $PREFIX/etc/` 的实际结果、
`/proc/self/mountinfo` 里有没有 BlackBox 的 bind mount、以及 BlackBox 的
`BActivityThread` 是否提供「按虚拟路径读文件」的接口。

### 明确没有验证的部分

以下**一次都没有跑过**，不得据本节推断它们可用：

- 任何真实模型请求（会话内没有配置 API Key，界面显示「未配置」）；
- 工具调用（Read/Bash/Edit…）在沙箱内的实际执行；
- 计划模式、权限询问、子代理、后台任务的前台/后台路径；
- Frida 调试桥（`frida_install` / `frida_load` / `frida_eval`）；
- `Debug scope=host`（需要 Agent Root，本会话为关闭状态）；
- 真机（非沙箱）上的任何一次运行。

## 终端面板（批 F 2/6）的沙箱验证

终端面板的外壳从 Java 拼 View 改成 Compose 之后，在沙箱里逐步点过一遍。
这一节记**实际看到的**，以及**没看到的**。

### 通过的部分

| 步 | 操作 | 实际结果 |
| --- | --- | --- |
| 1 | 切到「终端」标签 | 工具栏 `☰  bash 1  ⌨  ⋮` 齐全；两行扩展键齐全；提示符 `bash-5.3$` |
| 2 | 点 `⌨` 收键盘 | 键盘收起后**终端占满整屏、扩展键贴底** |
| 3 | 点 `☰` | 抽屉自左滑入、遮罩压暗终端；内容为 `Termux sessions` / `＋  New session` / 分隔线 / `●  bash 1` + `running` + `×` / 底部 `⌨  Toggle keyboard` 与 `↻  Reload properties` |
| 4 | 点 `＋  New session` | 标题栏变 `bash 2`，新 PTY 起来并给出提示符，抽屉自动关闭 |
| 5 | 点 `⋮` | 11 项按原顺序全部列出（`Paste` … `Toggle wake lock`） |
| 6 | 点 `Font larger`（下标 8） | 终端字号明显变大、对话框关闭 → **下标分发正确** |
| 7 | 点扩展键 `/` | 提示符里立刻出现 `/` → **点击送达 PTY** |
| 8 | 点扩展键 `ALT` | 该键变成 accent 色 → **锁定键高亮正确** |
| 9 | 再点 `ESC` | `ALT` 变回普通色 → **发一键之后锁定键自动清掉，界面同步** |
| 10 | 在沙箱里用输入法敲 `whiami` 回车 | 终端回显 `bash: whiami: command not found`；此前还出现过 `^C` → **IME → PTY → 回声 → 命令执行整条链路通** |

第 8、9 两条值得单独说：原来的实现在锁定键被 `TerminalView` 读走后不清界面状态，
高亮会停在上一次按下时的颜色上。重写时补了这一条，上面的观感就是它的直接证据。

### 一条容易被误判成缺陷的现象

收起键盘前的截图里，扩展键下方有一大块空白（约占屏幕 40%）。这是**键盘弹起导致窗口
resize**，不是布局缺陷：`AndroidManifest` 第 97 行是 `softInputMode="adjustResize"`，
而代码在 attach 之后 180ms 会调 `showKeyboard()`；沙箱不渲染输入法，那块被缩掉的高度
就是空的。布局本身是对的 —— 键盘弹起时扩展键正好落在键盘上方（原作者的设计意图，
`setKeyboardOffset` 那套自己算偏移的代码其实从来没有调用方）。

### 这一轮**没有**验证的部分

沙箱这一轮没有验证的有：长按改名、点 `×` 关闭会话、切换会话、抽屉底部两个动作、
需要剪贴板的两项、主题切换、失败路径、稳定性（反复切标签/开关抽屉/旋转）。
其中大部分**已经在真机上覆盖**（见下面一节）。

## 真机验收（两轮）与 H3 的修复

外壳改成 Compose 之后，在真机上按 A–H 清单跑了两轮：第一轮发现 H3，修完第二轮复验。
**第二轮无遗留问题。**

| 编号 | 第一轮结果 | 判定 |
| --- | --- | --- |
| **B4** | 按 `TAB` 出现 `Display all 488 possibilities? (y or n)`、`--More--`、`^C` | ✅ 通过 —— 那是 bash 自己的补全与分页行为，而且**它证明 `TAB` 确实是以 `\t` 送进 PTY 的**（否则不会触发补全） |
| **F1** | 浅色主题下外壳变浅、ANSI 配色不变、扩展键变成 `A` `B` | ✅ 通过 —— 外壳按设计跟随主题；`A`/`B` 是 D2 写进 `termux.properties` 的配置被正确读到 |
| **H3** | 旋转屏幕后终端一片空白，切到别的标签再切回来才恢复 | ❌ 真 bug → 已修，第二轮复验通过（见下） |

第二轮复验**由用户覆盖**的条目（结论来自用户复验，不是我逐条观测）：H3、H1/H2
（反复切标签、反复开关抽屉）、C4/C5/C6（改名、关闭会话、**切走再切回会话仍在**这个历史
bug 回归点）、D1、E1/E2（剪贴板两项）、G1/H4、E7。

### H3 的根因

三处上游 `TerminalView` 的代码拼起来就是完整机制：

| 位置 | 事实 |
| --- | --- |
| `attachSession()` 第 290–304 行 | 先 `mEmulator = null`，再立刻 `updateSize()` |
| `updateSize()` 第 984–987 行 | `viewWidth == 0 \|\| viewHeight == 0 \|\| mTermSession == null` 时**静默 return** |
| `onDraw()` 第 1008–1011 行 | `mEmulator == null` 时**只画一块纯黑** ← 这就是「空白」 |

所以只要 `mEmulator` 是 null，屏幕就是纯黑；而唯一会设它的入口 `updateSize()` 只在
`onSizeChanged` 里被调 —— 配置变化后尺寸若恰好不再变化，它就再也不会被调。

辅助证据两条：`MainActivity` 声明了
`configChanges="orientation|screenSize|screenLayout|keyboardHidden|uiMode"`（manifest 第 95 行），
所以旋转**不重建 Activity**；`runtime.log` 里那段时间只有两条「启动检查」，
说明 ViewModel 只被创建过两次（第二次是用户杀应用重进），进一步排除了「旋转导致重建」。

### 第一版修法修反了（这段最值得留）

第一版加了 `key(LocalConfiguration.current)` 包住 `AndroidView`，意图是让节点随配置重建、
`factory` 重跑、重新挂载宿主。**真机上仍然空白** —— 而且这正好暴露了它的副作用：

> 宿主是同一个 `View` 实例。同一帧内它从一个 `AndroidView` 节点迁移到新节点时，
> **旧节点的释放会把刚被新节点挂上的宿主再摘一次** → 宿主不在视图树上 →
> 没有测量/布局 → 宽高恒为 0 → `updateSize()` 因 `viewWidth == 0` 静默返回 →
> `mEmulator` 永远是 null → `onDraw()` 只画纯黑。

也就是说那一版的 `key(...)` 不是在修，而是在**制造**「宿主被摘掉」这个状态。
用户给的「切标签才恢复」正是判定这一点的证据：切标签是**先彻底离开组合、再干净地进来**，
绕过了这个争用。

**可复用的教训**：跨 Tab 保活要求复用同一个 `View` 实例（这正是
`TerminalHolder` / `WorkspaceViewModel.terminalPane` 存在的原因），那就**不能**让它跨
Compose 节点迁移。`key(...)` 重建节点与「复用同一个 View 实例」是互斥的做法。
以后再想用 `key(...)` 解决渲染问题，会撞上同一堵墙。

### 最终修法（三层，都不依赖 Compose 重建节点）

1. **去掉 `key(configuration)`**：同一个 `AndroidView` 节点跨旋转保持不动。
2. **把「切标签再切回」那条已验证可行的路径做在 View 这一层**（宿主内）：

   ```
   removeAllViews();
   addView(view, MATCH_PARENT, MATCH_PARENT);
   view.post(() -> { view.updateSize(); view.onScreenUpdated(); view.invalidate(); });
   view.postDelayed(() -> { view.updateSize(); view.invalidate(); }, 300);
   ```

   显式调 `updateSize()` 是必须的：同一个 View 摘下来再挂回**同样尺寸**时
   `onSizeChanged` 不会触发，而 `updateSize()` 是唯一会设 `mEmulator` 的入口；
   `postDelayed` 那次是重试，覆盖「第一次调用时布局还没完成」（它在尺寸为 0 时静默返回）。
   宿主另加 `onAttachedToWindow` 钩子：重新进入视图树时也补量一次。
3. **兜底网**：宿主把「自己在不在视图树上」暴露成 `hostInTree`；界面层**只在它报告
   自己真的不在树上时**才重建 `AndroidView` —— 那是它唯一无法自救的情况。

### 顺手留下的一行取证日志

`refreshTerminal()` 里有一行 `Log.i("ZhiTerminal", ...)`，内容是判定所需的几个量：
`inTree` / 宽高 / 会话数 / 是否已挂会话。旋转在沙箱里无法复现（没有旋转 API），
这条路径只在配置变化时走到，所以它是唯一的取证手段：真机上
`logcat -d | grep ZhiTerminal` 就能看出「自动重挂有没有发生、宿主在不在树上、尺寸是多少」。
它有长期价值，保留。

### 一个被证据推翻的假设（记下来，免得再猜一遍）

`[Process completed (signal 9)]` 最初怀疑来自启动时那条**静默**的版本自检重装
（`autoInstallRuntimeIfStale` → `installRuntimeBlocking` 第 3122 行显式 `releaseTerminalPane()`
→ `closeAll()` → SIGKILL）。**用户的 `~/.zhicode/runtime.log` 证伪了它**：
两次都是「一致=true」、没有「安装完成」，`usr` 根本没被重装。

真正来源：`TerminalSession.finishIfRunning()` 第 234–243 行**显式发 SIGKILL**
（`Os.kill(mShellPid, SIGKILL)`），打印那行文字的是上游 `TerminalSession` 第 349–367 行
（`exitCode < 0` 时输出 `(signal N)`）。所以它来自我们自己的三条路径 ——
`Kill shell` / `Close session` / Activity 销毁时的 `closeAll()` —— 属于**准确上报，不是异常**。

### 至今仍然没有验证的部分（不许因为上面这轮通过而顺推）

- **任何真实模型请求** —— 会话内从未配置 API Key，也就是说「对话」那条主链路
  至今一次都没有真正跑过；
- **沙箱端**的会话改名/关闭/切换、剪贴板、失败路径（真机已覆盖，沙箱未覆盖）；
- `Debug scope=host`（需要 Agent Root，本次会话为关闭状态）；
- Frida 调试桥（`frida_install` / `frida_load` / `frida_eval`）；
- 真机上与 root 相关的保活路径（`RootKeepAliveController`）。



