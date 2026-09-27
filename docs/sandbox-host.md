# 蜘蛛沙箱宿主层

## 为什么会有这一层

蜘蛛原来的沙箱宿主层是**整份从 IQ Code 搬过来的**：`sandbox/` 下 14 个类共 2028 行，逐行对得上
官方 `com.iqge.sandbox`，只把命名与文案换掉了。本轮把它推倒重写。**保留的是引擎，换掉的是宿主。**

| 层 | 内容 | 处置 |
| --- | --- | --- |
| 虚拟化引擎 | `Bcore/`、`black-reflection/`、`compiler/` | **保留**。来自上游 `ALEX5402/NewBlackbox`（BlackBox 血统），与上游 `diff -rq` 只差一个预编译的 `libblackbox.so`。不是 IQ Code 的代码 |
| 宿主层 | `sandbox/` 下 14 个类 + `ZhiSandboxTool` + `ZhiDebugTool` | **重写** |
| 引擎适配 | `Bcore` 中 4 处硬编码进程名 | **只改这 4 处**，引擎逻辑一行不动 |

引擎不重写的理由：它 hook 了 ART、BootClassLoader IO 与两百来个系统 Binder 服务。自研等价物等于
重做一个 BlackBox，在没有 x86_64 宿主（无法跑 NDK）的前提下不可行。

### 命名

| 项 | 旧（IQ Code 的） | 新（蜘蛛的） |
| --- | --- | --- |
| 控制器进程 | `:iqsandbox` | `:zhisandbox` |
| Termux 命令 | `iqsandbox` / `iqdebug` | `zhisandbox` / `zhidebug` |
| 代理进程 | `:black` / `:p0..:p49` | 不变（引擎的进程池，不属于宿主层） |

**刻意保持不变的**：包名 `com.zhizhu.zhicode.sandbox`（本来就是我们自己的）；provider authority
`${applicationId}.sandbox.control`（换掉会让已安装版本报 `INSTALL_FAILED_CONFLICTING_PROVIDER`）；
工具 action 名（`dump_ui` / `screenshot` / `tap` / `frida_*` …，`SystemPromptBuilder` 与 Agent
系统提示里写死了这些名字，改动会连带改提示词契约）。

## 类职责表

新层按**职责**切分，不再照搬 IQ Code 的文件划分。

| 新类 | 替代 | 职责 |
| --- | --- | --- |
| `ZhiSandbox` | `ZhiSandboxEngine` | 引擎门面：attach / create / awaitReady、安装、启动、停止、卸载、清数据、Root 隐藏。对外只暴露这一个入口 |
| `SandboxProcess` | `SandboxProcessRole` | 进程角色判定：哪些进程该 attach 引擎，哪些必须保持干净 |
| `SandboxStage` | （新增） | **每进程一条**的阶段日志。旧实现的 `startup-stage.txt` 是单文件覆盖写，多进程并发时后写的把先写的冲掉 —— 嵌套运行时就因此丢了控制器阶段、无法定位。新实现按 pid 分文件、追加写 |
| `SandboxConsole` | `SandboxDebugLog` | 跨进程事件日志 + 快照（含 logcat tail） |
| `SandboxPrefs` | `SandboxSettingsStore` | 跨进程设置（`hide_root` / `show_floating_log`），原子写 |
| `SandboxRpc` | `SandboxHostClient` | 同 UID IPC 客户端 |
| `SandboxRpcService` | `SandboxControlProvider` | 控制器侧的 IPC 服务端，动作分发 |
| `SandboxGuestHost` | `SandboxAgentBridge` | guest 进程内的控制桥（广播 + 悬浮层 + UI 操作） |
| `SandboxGuestDebug` | `SandboxProcessDebug` | guest 内进程调试：maps / 内存读写 / 线程 / 受控 .so 加载 |
| `SandboxFrida` | `SandboxFridaBridge` | Frida 加载与命令通道 |
| `FridaEnv` | `FridaRuntimeManager` | Frida 运行时（库文件、Gadget 配置、auto-attach 开关） |
| `SandboxBoard` | `SandboxDashboardActivity` | 管理界面，顺带统一成 Miuix 风格 |
| `SandboxOverlay` | `SandboxFloatingController` | guest 内浮窗控制栏（返回 / 日志 / 停止） |
| `SandboxKeeper` | `SandboxGuardService` | 前台保活，保证 guest 盖住主界面时 Agent 执行链不被回收 |
| `SandboxShell` | `SandboxTermuxBridge` | Termux 侧桥：bridge 目录、CLI 脚本、请求/响应文件通道 |

## 必须保留的安全边界

重写不得放宽以下任何一条：

1. `load_library` 只接受**本应用私有目录**下的 `.so`（`/data/user/0/<pkg>/` 与 `/data/data/<pkg>/`，
   两者都用运行时真实包名派生，不写死）。刻意**不**放行 `/data/user/0` 上一级。
2. 内存写入必须保留单次上限与「目标页可写」检查。
3. `/proc/self/mem` 只经受控通道访问，不对外暴露裸读写。
4. 引擎只 attach 在 `:zhisandbox` / `:black` / `:pN`，主进程保持干净。

## 回归网

官方 IQ Code 带了一套 17 个沙箱/Frida 结构测试（纯 `main()` 断言），蜘蛛此前没有。本轮按新命名
移植进来，作为宿主层的回归网。四条「必须存在」的不变式（进程角色、进程隔离、启动竞态、
WebView/网络透传）在重写后必须仍然通过。

## 实测记录

### 第一次端到端实跑（2026-09-26）

在此之前全部批次只验证过「编译 + JVM 单测 + 文本级断言」。这一次把包真装进 IQ Sandbox 跑起来：

| 项 | 结果 |
| --- | --- |
| 启动 | `MainActivity` 起来，进程 `com.zhizhu.code`（pid 2178） |
| 首屏渲染 | 完整：顶栏、四个标签、欢迎语、输入器 |
| 四个标签 | 「对话」「终端」切换均正常渲染 |
| 终端标签 | `TermuxTerminalPane` 经 Compose 互操作嵌入，会话条 / 终端 / 键盘行齐全 |
| guest 进程 | `com.zhizhu.code:zhisandbox`、`com.zhizhu.code:black` 各自在跑 |
| guest shell | guest 的 `bash` 已 fork 出来（pid 2444），给出了提示符 `bash-5.3$` |

最后一条值得单独说：guest 里的 `bash` 能起来并输出提示符，说明 guest 侧的 `fork/exec`、bootstrap
前缀、`LD_LIBRARY_PATH`（重写时**保留**了它 —— 内置 ELF 的 `DT_RUNPATH` 指向
`/data/data/com.termux/...`）这一整套是通的。

### 已知问题：guest 里 login shell 读不到 profile

终端第一行输出是（截图 + 日志双证）：

```
bash: /data/data/com.zhizhu.code/files/usr/etc/profile: Permission denied
```

排查结论是**沙箱路径虚拟化导致的，不是我们代码的缺陷**：

- BlackBox 为 guest 造的虚拟数据目录是
  `/data/user/0/com.iqge/blackbox/data/user/0/com.zhizhu.code/files`；
- 应用内部按 `Context.getFilesDir()` 拼出的 `PREFIX` 是 `/data/data/com.zhizhu.code/files/usr`；
- 同 UID 的进程去 `stat /data/data/com.zhizhu.code` 得到的是 **EACCES 而不是 ENOENT** ——
  说明那条路径存在、但被 SELinux 挡住；`find` 在另一个根下也**找不到**那个 `profile`；
- 而 guest 自己跑 `bash -l` 时凭同样的 `PREFIX` 能启动（说明它能穿越），但读 `$PREFIX/etc/profile`
  仍被拒。

也就是说：guest 侧「按 `PREFIX` 取文件」与「内核实际解析该路径」在 BlackBox 内部并不一致。
同一类问题很可能也影响 `apt`（它要读 `$PREFIX/etc/apt/*`）。

**尚未定位到可修的一步**，所以记为已知问题、不做猜测性修改。继续查的方向：guest 进程里
`ls -la $PREFIX/etc/`、`/proc/self/mountinfo` 里有没有 BlackBox 的 bind mount、以及
`BActivityThread` 是否提供「按虚拟路径读文件」的接口。

### 终端面板改 Compose 之后

沙箱里逐步点过一遍：切标签、收键盘（终端占满整屏、扩展键贴底）、开抽屉、新建会话、11 项快捷动作
按原顺序列出、点 `Font larger`（下标分发正确）、扩展键 `/` 送达 PTY、`ALT` 锁定后高亮、再点 `ESC`
自动清掉、用输入法敲 `whiami` 回车得到 `command not found` —— **IME → PTY → 回声 → 命令执行
整条链路通**。

最后两条值得单独说：原来的实现在锁定键被 `TerminalView` 读走后不清界面状态，高亮会停在上一次按下
时的颜色上。重写时补了这条，上面的观感就是它的直接证据。

**一条容易被误判成缺陷的现象**：收起键盘前的截图里扩展键下方有一大块空白（约占屏幕 40%）。
那是**键盘弹起导致窗口 resize**，不是布局缺陷 —— `softInputMode="adjustResize"`，而代码在 attach
后 180ms 会调 `showKeyboard()`；沙箱不渲染输入法，那块被缩掉的高度就是空的。布局本身是对的
（原作者那套自己算偏移的 `setKeyboardOffset` 其实从来没有调用方）。

## H3：旋转后终端一片空白（已修）

真机上按 A–H 清单跑两轮：第一轮发现 H3，修完第二轮复验，**无遗留**。

### 根因

三处上游 `TerminalView` 的代码拼起来就是完整机制：

| 位置 | 事实 |
| --- | --- |
| `attachSession()` 第 290–304 行 | 先 `mEmulator = null`，再立刻 `updateSize()` |
| `updateSize()` 第 984–987 行 | `viewWidth == 0 \|\| viewHeight == 0 \|\| mTermSession == null` 时**静默 return** |
| `onDraw()` 第 1008–1011 行 | `mEmulator == null` 时**只画一块纯黑** ← 这就是「空白」 |

所以只要 `mEmulator` 是 null，屏幕就是纯黑；而唯一会设它的入口 `updateSize()` 只在 `onSizeChanged`
里被调 —— 配置变化后尺寸若恰好不再变化，它就再也不会被调。辅助证据：`MainActivity` 声明了
`configChanges="orientation|…"`，所以旋转**不重建 Activity**；`runtime.log` 里那段时间只有两条
「启动检查」，进一步排除「旋转导致重建」。

### 第一版修法修反了（这段最值得留）

第一版加了 `key(LocalConfiguration.current)` 包住 `AndroidView`，意图是让节点随配置重建、
`factory` 重跑、重新挂载宿主。**真机上仍然空白** —— 而且这正好暴露了它的副作用：

> 宿主是同一个 `View` 实例。同一帧内它从一个 `AndroidView` 节点迁移到新节点时，**旧节点的释放会把
> 刚被新节点挂上的宿主再摘一次** → 宿主不在视图树上 → 没有测量/布局 → 宽高恒为 0 →
> `updateSize()` 因 `viewWidth == 0` 静默返回 → `mEmulator` 永远是 null → `onDraw()` 只画纯黑。

那一版的 `key(...)` 不是在修，而是在**制造**「宿主被摘掉」这个状态。用户给的「切标签才恢复」正是
判定这一点的证据：切标签是**先彻底离开组合、再干净地进来**，绕过了这个争用。

**可复用的教训**：跨 Tab 保活要求复用同一个 `View` 实例（这正是 `TerminalHolder` /
`WorkspaceViewModel.terminalPane` 存在的原因），那就**不能**让它跨 Compose 节点迁移。
**`key(...)` 重建节点与「复用同一个 View 实例」是互斥的做法** —— 以后再想用 `key(...)` 解决渲染
问题，会撞上同一堵墙。

### 最终修法（三层，都不依赖 Compose 重建节点）

1. **去掉 `key(configuration)`**：同一个 `AndroidView` 节点跨旋转保持不动。
2. **把「切标签再切回」那条已验证可行的路径做在 View 这一层**（宿主内）：

   ```
   removeAllViews();
   addView(view, MATCH_PARENT, MATCH_PARENT);
   view.post(() -> { view.updateSize(); view.onScreenUpdated(); view.invalidate(); });
   view.postDelayed(() -> { view.updateSize(); view.invalidate(); }, 300);
   ```

   显式调 `updateSize()` 是必须的：同一个 View 摘下来再挂回**同样尺寸**时 `onSizeChanged` 不会触发，
   而 `updateSize()` 是唯一会设 `mEmulator` 的入口；`postDelayed` 那次是重试，覆盖「第一次调用时
   布局还没完成」（它在尺寸为 0 时静默返回）。宿主另加 `onAttachedToWindow` 钩子：重新进入视图树时
   也补量一次。
3. **兜底网**：宿主把「自己在不在视图树上」暴露成 `hostInTree`；界面层**只在它报告自己真的不在树上
   时**才重建 `AndroidView` —— 那是它唯一无法自救的情况。

### 留下的一行取证日志（有长期价值，不要删）

`refreshTerminal()` 里的 `Log.i("ZhiTerminal", …)` 打出判定所需的几个量：`inTree` / 宽高 / 会话数 /
是否已挂会话。旋转在沙箱里无法复现（没有旋转 API），这条路径只在配置变化时走到，所以它是唯一的
取证手段：真机上 `logcat -d | grep ZhiTerminal` 就能看出「自动重挂有没有发生、宿主在不在树上、
尺寸是多少」。

### 一个被证据推翻的假设（记下来，免得再猜一遍）

`[Process completed (signal 9)]` 最初怀疑来自启动时那条**静默**的版本自检重装
（`autoInstallRuntimeIfStale` → `installRuntimeBlocking` → `releaseTerminalPane()` → `closeAll()`
→ SIGKILL）。**用户的 `~/.zhicode/runtime.log` 证伪了它**：两次都是「一致=true」、没有「安装完成」，
`usr` 根本没被重装。

真正来源：`TerminalSession.finishIfRunning()` **显式发 SIGKILL**（`Os.kill(mShellPid, SIGKILL)`），
打印那行文字的是上游 `TerminalSession`（`exitCode < 0` 时输出 `(signal N)`）。所以它来自我们自己的
三条路径 —— `Kill shell` / `Close session` / Activity 销毁时的 `closeAll()` —— 属于**准确上报，
不是异常**。

## 至今仍未验证的部分（不许因为上面这些通过而顺推）

- **任何真实模型请求** —— 会话内从未配置 API Key，也就是说「对话」那条主链路至今一次都没真正跑过；
- **工具调用**（Read/Bash/Edit…）在沙箱内的实际执行；
- 计划模式、权限询问、子代理、后台任务的前台/后台路径；
- 沙箱端的会话改名/关闭/切换、剪贴板、失败路径、稳定性（反复切标签/开关抽屉/旋转）——
  真机已覆盖，沙箱未覆盖；
- Frida 调试桥（`frida_install` / `frida_load` / `frida_eval`）；
- `Debug scope=host`（需要 Agent Root，本会话为关闭状态）；
- 真机（非沙箱）上的一些路径，以及真机上与 root 相关的保活（`RootKeepAliveController`）。
