# 沙箱宿主层

本文件讲的是 `com/zhizhu/zhicode/sandbox/` 这一层：它负责把「BlackBox 血统的虚拟化引擎」包成一个
可被 Agent 与界面安全调用的门面，并守住几条不能放宽的边界。

**保留的是引擎，重写的是宿主。**

| 层 | 内容 | 处置 |
| --- | --- | --- |
| 虚拟化引擎 | `Bcore/`、`black-reflection/`、`compiler/` | **保留**（第三方，Apache-2.0，来自上游 `ALEX5402/NewBlackbox`）。它 hook ART、BootClassLoader IO 与两百来个系统 Binder 服务，自研等价物不可行 |
| 宿主层 | `app/src/main/java/com/zhizhu/zhicode/sandbox/`（16 个类） | **本工程自己写的** |
| 引擎适配 | `Bcore` 里的进程名常量 | 收敛进 `SandboxContract`（见 [`provenance.md`](provenance.md)） |

## 进程模型

进程名的**单一来源**是 `Bcore/src/main/java/top/niunaijun/blackbox/SandboxContract.java`：

| 常量 | 值 | 角色 |
| --- | --- | --- |
| `CONTROLLER_PROCESS_SUFFIX` | `:zhisandbox` | 控制器：**hookless**，只编排 Binder / 包服务与高层指令 |
| `SERVER_PROCESS_SUFFIX` | `:black` | 引擎服务进程：承载 BlackBox 运行时与 native hook |
| `GUEST_PROCESS_PREFIX` + `GUEST_PROCESS_COUNT` | `:p0` … `:p49` | guest 代理进程池 |

`SandboxProcess` 在每个进程的 `Application.attachBaseContext` 里判定角色（从 `/proc/self/cmdline` 读进程名，
读不到时**宁可判成主进程**）。它**刻意不引用 `BlackBoxCore`** —— 读那个类的字段会触发静态初始化，
把 BlackBox 拉进本该干净的主进程。

**主进程必须保持干净**：编辑器、Agent 执行链、界面都在主进程里，沙箱崩溃不应把它们一起带走。

## 类职责

| 类 | 职责 |
| --- | --- |
| `ZhiSandbox` | 引擎门面：attach / install / launch / stop / uninstall / clear data 等对外唯一入口 |
| `SandboxProcess` | 进程角色判定（谁允许挂引擎、谁必须干净） |
| `SandboxRpcService` | 控制器进程（`:zhisandbox`）里的同 UID IPC 服务端 —— 宿主访问沙箱的高层入口 |
| `SandboxRpc` | 宿主侧的同 UID IPC 客户端 |
| `SandboxShell` | 内置 Termux 与沙箱之间的同 UID 文件桥；把 `zhisandbox` / `zhidebug` 两条命令写进 `$PREFIX/bin` |
| `SandboxStage` | **每进程一条**的启动阶段日志（按 pid 分文件、追加写） |
| `SandboxConsole` | 跨进程运行期事件日志（宿主与各 guest 共用一条时间线） |
| `SandboxPrefs` | 跨进程设置（`hide_root` / `show_floating_log` 等），原子写 |
| `SandboxGuestHost` | Sandbox / Debug 工具共用的跨进程控制面（guest 侧） |
| `SandboxGuestDebug` | guest 进程内的自省与调试后端（maps / 内存读写 / 线程 / 受控 `.so` 加载） |
| `SandboxFrida` | 单个 guest 进程内的 Frida Gadget 桥 |
| `FridaEnv` | 按需把官方 arm64 Frida Gadget 装进本应用私有存储 |
| `SandboxOverlay` / `SandboxPalette` | 注入到每个虚拟 Activity 上的控制栏及其配色 |
| `SandboxKeeper` | 前台保活：guest 盖住主界面时，保证 Agent 执行链不被系统回收 |
| `SandboxBoard.kt` | 沙箱管理界面（留在主进程，只通过上面的 provider 发指令） |

## 谁在调用它

- **Agent 侧**：系统提示里写明了两条命令（见 `core/SystemPromptBuilder.java`）：
  `zhisandbox <action> [package] [json]` 控制安装 / 启动 / UI / 日志，
  `zhidebug <action> [package-or-pid] [json]` 走同一套 Debug 桥。
- **界面侧**：`ui/sandbox/ZhiSandboxScreen.kt` 与 `SandboxBoard.kt`。
- **工具前置条件**：`AndroidManifest.xml` 里注册的 `${applicationId}.sandbox.control`
  （`SandboxRpcService`，`android:process=":zhisandbox"`，**不导出**）。

## 必须保留的安全边界

重写不得放宽以下任何一条：

1. **`load_library` 只接受本应用私有目录下的 `.so`**：`dataDir`、`/data/data/<pkg>`（旧版符号链接别名）、
   `filesDir`、`nativeLibraryDir` 四个根（都用运行时真实包名派生，不写死）。
   刻意**不**放行 `/data/user/0` —— 那等于允许任意应用的数据目录。
2. **内存写入保留单次上限与「目标页可写」检查**。
3. **`/proc/self/mem` 只经受控通道访问**，不对外暴露裸读写。
4. **引擎只 attach 在 `:zhisandbox` / `:black` / `:p0..:p49`**，主进程保持干净。
5. **启动阶段日志按 pid 分文件**（不得退回单文件覆盖写）：旧实现写单一文件，多进程并发时后写的会冲掉先写的，
   于是「卡在哪一步」查不出来。
6. **AndroidX Startup 的 provider / receiver 必须被清单移除**：`InitializationProvider` 是 ContentProvider，
   创建时机在 `Application.attachBaseContext` 之后、`onCreate` 之前，会带起 emoji 字体加载等后台 IO 与线程，
   在沙箱宿主里既无必要也不可控。

## 回归网：**已经没有了**

⚠️ 本层原本有一整套源码文本级结构断言，专门钉住上面这些不变式 ——
`SandboxHostArchitectureTest`、`SandboxProcessIsolationStructureTest`、`SandboxMainProcessRoleStructureTest`、
`SandboxHooklessControllerStructureTest`、`SandboxControllerStartupRaceTest`、`SandboxStartupCrashRegressionTest`、
`SandboxIntegrationStructureTest`、`SandboxNetworkPassthroughStructureTest`、`SandboxWebViewStructureTest`、
`SandboxFileProviderResourceRegressionTest`、`SandboxRootVisibilitySettingTest`、`SandboxFloatingLogStructureTest`、
`SandboxScreenshotBridgeStructureTest`、`SandboxDebugBridgeStructureTest`、`SandboxPageStructureTest`，
以及 `FridaSandboxStructureTest` / `FridaDeadlockRegressionTest` / `FridaGadgetConfigRegressionTest` /
`FridaScriptBootstrapRegressionTest`（后两个的对象是内嵌 JS 载荷，由 `app/tests/js/` 下的 node 用例执行）。

**它们已随 `app/tests/` 整套删除。** 所以本层的不变式现在**没有任何自动化防线** ——
改沙箱宿主层时，上面「必须保留的安全边界」六条只能靠逐条读代码与在 IQ 沙箱里真跑一遍来确认：
直接去对照 `SandboxContract.java`、`SandboxProcess`、`SandboxRpcService` 与 `SandboxGuestDebug` 的实现。

## 实测与已知问题

### 端到端实跑能确认到什么

在 IQ 沙箱里装包启动：`MainActivity` 起来（主进程）、首屏渲染完整、页签切换正常、
`:zhisandbox` 与 `:black` 两个进程各自在跑、guest 里的 `bash` 能给出提示符 ——
这说明 guest 侧的 `fork/exec`、bootstrap 前缀与环境变量这一整套是通的。

### 已知问题：guest 里 login shell 读不到 profile

终端第一行可能输出：

```
bash: /data/data/com.zhizhu.code/files/usr/etc/profile: Permission denied
```

排查结论是**沙箱路径虚拟化导致的，不是宿主层代码的缺陷**：BlackBox 为 guest 造的虚拟数据目录与
应用内部按 `Context.getFilesDir()` 拼出的 `PREFIX` 不是同一条路径；同 UID 去 `stat` 得到的是
**EACCES 而不是 ENOENT**（路径存在、被 SELinux 挡住）。同一类问题很可能也影响 `apt`
（它要读 `$PREFIX/etc/apt/*`）。**尚未定位到可修的一步**，因此记为已知问题、不做猜测性修改。

### 已修：旋转后终端一片空白

根因是三处上游 `TerminalView` 行为叠加：`attachSession()` 先把 `mEmulator` 置空再 `updateSize()`，
而 `updateSize()` 在宽或高为 0 时**静默 return**，`onDraw()` 在 `mEmulator == null` 时只画一块纯黑。
而 `MainActivity` 声明了 `configChanges="orientation|…"`，旋转不重建 Activity，尺寸若恰好不再变化
`onSizeChanged` 就不会再触发 —— 于是永远黑屏。

第一版修法（用 `key(LocalConfiguration.current)` 包 `AndroidView`）**修反了**：跨 Tab 保活要求复用同一个
`View` 实例，而 Compose 节点迁移时旧节点的释放会把刚挂上的宿主再摘一次 → 宿主不在视图树上 → 宽高恒为 0。

最终修法三层，都不依赖 Compose 重建节点：

1. 去掉 `key(configuration)`：同一个 `AndroidView` 节点跨旋转保持不动；
2. 把「切标签再切回」那条已验证可行的路径**做在 View 这一层**（`removeAllViews()` → `addView()` →
   `post { updateSize(); onScreenUpdated(); invalidate() }` → `postDelayed` 重试一次），
   并加 `onAttachedToWindow` 钩子；
3. 兜底：宿主把「自己在不在视图树上」暴露成 `hostInTree`，界面层**只在它报告自己真的不在树上**时才重建。

> 教训：**`key(...)` 重建节点与「复用同一个 View 实例」是互斥的做法。**

### `[Process completed (signal 9)]` 不是异常

它来自 `TerminalSession.finishIfRunning()` 显式发 `SIGKILL`（关闭会话 / 杀 shell / Activity 销毁时），
由上游在 `exitCode < 0` 时打印 —— 属于**准确上报**。曾经怀疑它来自启动期的静默版本自检重装，
后来用 `~/.zhicode/runtime.log` 证伪了那个假设（没有发生重装）。

---

## 至今仍未验证的部分（不许因为上面这些通过而顺推）

上面「实测与已知问题」一节写的是**已经真跑过、能确认的那几件事**。下面这些**一律没有验证过**
（而且本层的结构断言已删，所以也不会有机器替你发现回归）：

- **真实模型请求**的完整链路（取决于你是否配置了 API Key）；
- **工具调用**（Bash / Read / Edit…）在沙箱内的实际执行路径；
- 计划模式、权限询问、子代理、后台任务的前台 / 后台路径；
- Frida 调试桥（`frida_install` / `frida_load` / `frida_eval`）在真机上的端到端行为；
- 沙箱端的会话改名 / 关闭 / 切换、剪贴板与失败路径；
- `Bcore` 合并进来的**导出组件面**（约 200 个 `Proxy*` 组件）的可利用性 —— 见 [`SECURITY.md`](../SECURITY.md) 第 4.3 节；
- 真机（非沙箱）上与 root 相关的保活路径。
