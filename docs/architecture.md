# 架构与代码结构

本文件讲**代码是怎么组织的、以及哪些约定不能动**。
构建与发布见 [`build-and-release.md`](build-and-release.md)，界面层见 [`ui-miuix.md`](ui-miuix.md)，
性能取舍见 [`performance.md`](performance.md)，沙箱宿主层见 [`sandbox-host.md`](sandbox-host.md)。

---

## 1. 它现在是什么

**不是 UI 原型**。引擎、Termux 环境、沙箱、会话存储都已接上真实实现。

| 能力 | 实现 |
| --- | --- |
| Agent 主循环 | `com/termux/app/zhicode/core/ZhiCodeEngine.java`（流式、工具调用、子代理、steering、历史自愈） |
| 模型协议 | `com/termux/app/zhicode/api/`（OpenAI Responses / Chat Completions / Anthropic + 兼容与 zcode 适配），支持自定义 Base URL 与明文 HTTP |
| 工具集 | `core/` 的 `ToolRegistry` + `tools/` 下的 `ZhiTool` 实现（Bash、读写文件、WebSearch / WebFetch、沙箱、Debug、MCP、技能…） |
| 内置终端 | 自带 Termux bootstrap（`assets/bootstrap-aarch64.zip`）→ 真实 PTY（`TerminalSession` + `libtermux.so`） |
| 虚拟化沙箱 | `Bcore/`（BlackBox 血统）+ 宿主层 `com/zhizhu/zhicode/sandbox/`（16 个类） |
| MCP / 技能 / 角色卡 / 记忆 | `McpStore`、`McpRuntime`、`SkillStore`、`RoleCardStore`、`MemoryStore` |
| 会话与任务 | `SessionStore`、`TaskStore`、`PlanStore` |
| 界面 | 全部走 Miuix；语义色、圆角与动画 token 集中在 `theme/` |

### 当前 UI 边界

- `ui/WorkspaceLayouts.kt` 负责宽窄屏工作区、页签与 Pager；主板块禁用横向手势，点击页签是唯一切换入口。
- `ui/panes/` 承载终端、文件与编辑器面板；文件的实际复制 / 移动等操作仍由 `state/WorkspaceViewModel` 与
  `core/FileOps` 负责。
- 编辑器是**独立 Activity**（`.editor.EditorActivity`，`android:process=":editor"`），
  返回键与输入法只有一个主人；Sora 的 `AndroidView` 配置集中在编辑器宿主文件里。
- `ui/chat/` 按回合块渲染对话；Markdown 表格在自己的横向滚动容器里，超长正文默认有界预览，
  工具输出用有界的内层 `LazyColumn` 分块渲染（见 [`performance.md`](performance.md)）。

### 明确未做

- **悬浮球、Frida / Debug 图形面板**：调试走内置命令 `zhisandbox` / `zhidebug` 与 Agent 的 Debug 工具。
- **原版 IQ Code 的三套自定义调色板**：这里走 Miuix 原生主题，跟随系统深浅色。
- **iOS / 桌面端**：只针对 Android，且只出 arm64-v8a。

---

## 2. 模块与目录

四个 Gradle 模块：`app`（应用本体）、`Bcore`、`black-reflection`、`compiler`（后三者是第三方虚拟化运行时与其注解处理，Apache-2.0，见 [`NOTICE`](../NOTICE)）。

```
app/src/main/java/
  com/termux/app/zhicode/      Agent 侧（以纯 Java 为主）
    api/                       协议层与各提供方适配（api/compat、api/zcode 为兼容与自有适配）
    core/                      引擎、权限门与策略、风险分类、上下文压缩、文件操作、工具注册表
    tools/                     工具实现
    mcp/  agents/  tasks/  storage/  model/  security/  json/  termux/
  com/termux/                  Termux 终端模拟与视图（Apache-2.0，第三方；termux/ 为集成层）
    terminal/  view/  shared/termux/
  com/zhizhu/zhicode/          应用侧
    compose/
      MainActivity.kt           ComponentActivity → setContent { ZhiCodeApp() }
      ZhiCodeApplication.kt     进程角色判定与启动期装配
      theme/                    颜色 / 圆角 / 动效 / 字阶 token
      model/                    UiModels（含 WorkspaceUiState）、ToolKind、TurnLayout 等
      data/                     数据层（API 配置、MCP、记忆、会话、Git 变更…）
      state/WorkspaceViewModel  StateFlow<WorkspaceUiState> + 引擎事件
      engine/                   引擎控制器（ZhiEngineController）与流式合并
      runtime/  editor/         运行时安装器与编辑器宿主
      ui/Common.kt              ★ Miuix 组件的唯一转发层（Zhi* 包装与 token）
      ui/AppScaffold.kt         Scaffold + 宽窄屏布局 + 弹窗挂载点
      ui/chat/  ui/composer/  ui/panes/  ui/dialogs/  ui/settings/  ui/sandbox/  ui/debug/
    sandbox/                    沙箱宿主层（ZhiSandbox、SandboxProcess、SandboxRpc…、SandboxFrida、SandboxGuestDebug）
    background/                 前台保活
Bcore/  black-reflection/  compiler/    第三方（Apache-2.0）
app/src/main/assets/           bootstrap-aarch64.zip、zhicode/*.sh、sora/textmate/**
app/src/test/                  JVM 单测（**唯一**的测试入口）
docs/  tools/                  本目录 / 度量与构建脚本
```

---

## 3. 状态与引擎怎么接

引擎是**懒创建**的：`compose/engine/ZhiEngineController.kt` 的 `engine()` 第一次被调用时才
`ZhiCodeEngine(appContext, this)`，之后复用。发消息前 `configure()` 会重新读一次设置，
所以改模型 / 改权限模式不需要重启应用。

几条不能动的顺序约定：

- 引擎相关的多次调用（取消当前回合、载入历史会话……）走**单线程 executor**（`zhi-engine-ops`），
  因为它们**必须按提交顺序执行**：乱序会让「载入历史」被前一条「取消」取消掉。
- `sendPrompt` 留在调用线程（错误要当场报给发送方），排队中的取消靠 generation 守卫失效。
- 引擎回调全部发生在 agent 线程上，实现方负责切回主线程。

### 关于 `WorkspaceUiState` 的大小

它是 `model/UiModels.kt` 里一个**单一** `data class`：当前约 **70 个字段**，其中 **9 个是 `List`**
（对话流、会话列表、工具输出……）。这带来两个已知代价：

1. **没有 `@Immutable`，也不该加**（理由见 [`performance.md`](performance.md) 的第 6 节）；
2. 传它的组件会为**每一次**无关字段的变化付判等费 —— 所以顶栏与侧栏都改成了各自的窄类型
   （`TopBarState`、`SidebarState`）。

新增组件时先问一句：**它真的需要整份状态吗？**

---

## 4. 界面硬约定（两条，都踩过坑）

### 输入框一律走 `ZhiTextField`

`compose/ui/Common.kt` 里的它负责两件不加就出错的事：显式文字色（否则会被弹窗里 `Card` 的
`contentColor` 吃掉），以及外部改值时把光标放到末尾（否则光标恒在 0，输入 `123` 会变成 `231`）。
**这条没有自动化断言**（原 `TextFieldConventionTest` 已删），改输入框时按这两点自查。

### 长按动作菜单走 `ZhiAnchoredActionMenu`

Miuix 下拉菜单 + 一个**只观察不消费**的指针修饰符拿手指位置，于是菜单从手指处长出来，
同时卡片自己的点击 / 长按与无障碍语义都保留。观察器一旦**消费**事件就会顶掉这些语义。
**这条同样没有自动化断言**（原 `AnchoredMenuStructureTest` 已删）：改这个修饰符时，
要自己确认卡片的点击、长按与无障碍语义还在。

---

## 5. 沙箱层的不变式（不得放宽）

无论怎么重写，以下约束不得放宽：

1. **主进程保持干净**：引擎只 attach 在 `:zhisandbox` / `:black` / `:p0..:p49`，编辑器与 Agent 执行链不受
   native hook 影响。进程角色判定在 `sandbox/SandboxProcess.java`（从 `/proc/self/cmdline` 读进程名，
   读不到时**宁可判成主进程**）。
2. **进程名单一来源**：`Bcore/src/main/java/top/niunaijun/blackbox/SandboxContract.java` 里的编译期常量
   （`:zhisandbox`、`:black`、`:p0`…）。
   `SandboxProcess` **刻意不引用 `BlackBoxCore`** —— 读它的字段会触发静态初始化，把 BlackBox 拉进主进程。
3. **宿主调用面收敛**：宿主层里真正引用 `BlackBoxCore` 的只有 `ZhiSandbox`（引擎门面）。
4. **guest 调试受限**：只允许载入本应用私有目录下的 `.so`（`dataDir` / `/data/data/<pkg>` 等四个根），
   内存写入有单次上限，且要检查目标页可写。
5. **启动阶段日志按 pid 分文件、追加写**（`SandboxStage`）；单文件覆盖写会让多进程并发时的阶段互相抹掉。
6. **AndroidX Startup 的 provider / receiver 必须被清单移除**（见 `app/src/main/AndroidManifest.xml`）。

> 这几条要单独留一份，是因为它们描述的是**「哪些东西看起来可以简化、其实不能动」**。
> 后续重写时最容易踩的就是把其中某一条当成冗余顺手删掉 —— 而删掉不会编译失败。

细节与 Bcore 相对上游的补丁见 [`provenance.md`](provenance.md) 与 [`sandbox-host.md`](sandbox-host.md)。

---

## 6. 验证入口

只有一条测试入口，跑法见 [`CONTRIBUTING.md`](../CONTRIBUTING.md)：

```bash
bash test-jvm-fast.sh                 # 纯 JVM 单测快路径（只编被测试依赖的那几个文件）
./gradlew :app:testDebugUnitTest      # 完整 JVM 单测
```

`app/src/test/java/` 下是真正跑起来的单测，覆盖 `zhicode/api/`、`zhicode/core/`、`zhicode/tools/`
与 `compose/model/`、`compose/theme/` 这些**不 import `android.*`** 的纯逻辑。

⚠️ **本工程的约定类问题现在没有自动化防线**：曾经的源码文本级断言套件（`app/tests/*.java` +
`test-source-no-build.sh`）**已整套删除**，它当时有一部分是红的。也就是说本文第 4、5 节列的那些
「不能这么写」的约束，只能靠读代码与 review 守。

引擎、权限、文件访问与沙箱属于独立运行时边界：**UI 性能改动不得通过修改这些层来绕过成本**。
