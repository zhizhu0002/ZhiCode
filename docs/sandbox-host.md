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
