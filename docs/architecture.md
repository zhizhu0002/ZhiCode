# 架构与代码结构

本文件讲**代码是怎么组织的、以及哪些约定不能动**。
构建与发布见 [`build-and-release.md`](build-and-release.md)，界面层见 [`ui-miuix.md`](ui-miuix.md)，
性能上的取舍见 [`performance.md`](performance.md)。

---

## 1. 它现在是什么

**不是 UI 原型**。真实引擎、真实环境、真实沙箱都已经接上：

| 能力 | 实现 |
| --- | --- |
| Agent 引擎 | `core/ZhiCodeEngine.java`（流式、工具调用、子代理、steering、视觉消息过滤） |
| 模型接入 | `api/` 下的 OpenAI Responses / Chat Completions / Anthropic 等协议适配，支持自定义 Base URL 与明文 HTTP 开关 |
| 内置终端 | 自带 Termux bootstrap（`RuntimeInstaller` 解压约 32 MB）→ 真实 PTY（`TerminalSession` + `libtermux.so`） |
| 虚拟化沙箱 | `Bcore/`（BlackBox 血统）—— 免安装运行 APK、Frida 注入、原生调试 |
| MCP / 技能 / 角色卡 / 记忆 | `McpStore`、`McpRuntime`、`SkillStore`、`RoleCardStore`、`MemoryStore` |
| 会话与任务 | `SessionStore`、`TaskStore`、`PlanStore` |
| 界面 | 全部走 Miuix 组件；语义色、字阶与动画 token 集中在 `ZhiColors` / `ZhiTextStyles` / `ZhiRadius` / `ZhiMotion` |

### 当前 UI 边界

- `WorkspaceLayouts.kt` 只负责宽窄屏工作区、标签页与 Pager；主板块禁止手势横滑，点击 Tab 通过统一 Pager 动画切页，页签状态在 Pager settle 后单向写回 `WorkspaceUiState.tab`。
- `FilesPane.kt` 负责文件浏览、紧凑搜索、分区列表/四列无卡片宫格与拖动批量选择；文件复制/移动等实际操作仍由 `WorkspaceViewModel` 与 `FileOps` 负责。
- `FileEditorPage.kt` 负责编辑器页面生命周期、空查询安全搜索、紧凑查找行与可展开的 MT 风格单列工具面板；Sora 的 AndroidView 配置集中在 `SoraEditorHost.kt`。
- `ui/chat/` 按回合块渲染对话。Markdown 表格在自己的横向滚动容器中渲染，超长助手正文默认有界预览，思考区与工具组按高度展开/收回，工具输出使用有界内层 LazyColumn 分块渲染。

### 明确未做

- **悬浮球、Frida/Debug 图形界面**：调试走 `iqdebug` / Debug 工具，没有独立仪表盘页面。
- **原版 IQ Code 的三套自定义调色板**：这里走 Miuix **原生主题**，跟随系统深浅色。
- **iOS / 桌面端**：只针对 Android。
- 部分原版设置项仍未搬过来（`SettingsRows.kt` 里能看到当前覆盖范围）。

---

## 2. 目录

```
app/src/main/java/com/zhizhu/zhicode/compose/
  MainActivity.kt                     ComponentActivity → setContent { ZhiCodeApp() }
  theme/                              ZhiColors / ZhiRadius / ZhiMotion / ZhiTextStyles（主题字阶）
  model/UiModels.kt                   ChatItem / ToolActivity / SessionSummary / PermissionMode 等
  data/                               真实数据层（ApiConfig / Mcp / Memory / Session / GitChanges …）
  state/WorkspaceViewModel.kt         StateFlow<WorkspaceUiState> + EngineEvents 实现
  ui/Common.kt                        ★ Miuix 组件的唯一转发层（Zhi* 包装与 token）
  ui/AppScaffold.kt                   Scaffold + 宽窄屏布局 + 弹窗挂载点
  ui/chat/                            对话流、消息卡片、进度卡
  ui/composer/                        输入器与斜杠命令面板
  ui/panes/                           Terminal / Files / Sora 编辑器面板
  ui/dialogs/                         各弹窗（外壳见 dialogs/DialogShell.kt）
  ui/settings/                        设置页与设置行
```

其余顶层目录：

| 目录 | 内容 |
| --- | --- |
| `app/src/main/java/com/termux/app/zhicode/api/` | 纯 Java 的协议层与协议适配 |
| `app/src/main/java/com/termux/app/zhicode/core/` | Agent 主循环与工具集 |
| `app/src/main/java/com/termux/app/zhicode/sandbox/` | 沙箱宿主层（`ZhiSandbox` 等） |
| `Bcore/` | 虚拟化引擎（BlackBox 血统），含预编译的 `libblackbox.so` |
| `black-reflection/` `compiler/` | Bcore 的注解驱动反射与其注解处理器 |
| `app/src/main/assets/sora/` | Sora TextMate grammar 资源（只保留实际使用的语言） |
| `tools/` | `provenance.sh`、历史改写脚本等 |
| `docs/` | 本目录下这些文档 |

---

## 3. 状态与引擎怎么接

`WorkspaceViewModel` 实现 `EngineEvents`，真实引擎 `ZhiCodeEngine` 在**第一次发消息时**懒创建。
流式回复、工具调用、权限询问、子代理都走它，不再是模拟数据。

⚠️ `WorkspaceRepository` / `MockWorkspaceRepository` **现在只剩终端占位横幅一项用途**，
名字容易误导 —— 对话流、工具执行、会话文件都已经不经它了。

- **授权弹窗**：`requestPermission()` 用 `CompletableDeferred` 挂起等待用户点击，
  真实实现把引擎侧的权限请求映射成 `PermissionRequest`。
- **终端面板**：`TerminalPane` 接收 PTY 的增量输出；`TerminalSession` 管理真实会话。

### 关于 `WorkspaceUiState` 的大小

它是一个有五十多个字段的**单一** `data class`，其中十几个是 `List`（对话流、会话列表、
工具输出……）。这带来两个已知代价：

1. **没有 `@Immutable`**，也不该加（理由见 [`performance.md`](performance.md) 第 6 节）；
2. 传它的组件会为**每一次**无关字段的变化付判等费 —— 所以顶栏与侧栏都改成了各自的窄类型。

新增组件时请先问一句：**它真的需要整份状态吗？**

---

## 4. 两条硬约定（都踩过坑）

### 输入框一律走 `ZhiTextField`

（`compose/ui/Common.kt`）它负责两件不加就出错的事：显式文字色（否则会被弹窗里 `Card` 的
`contentColor` 吃掉，输入的字与底色分不清），以及外部改值时把光标放到末尾
（否则光标恒在 0，输入 `123` 会变成 `231`）。

`TextFieldConventionTest` 守着。

### 长按动作菜单走 `ZhiAnchoredActionMenu`

Miuix 下拉菜单 + 一个**只观察不消费**的指针修饰符（`zhiObservePointer`）拿手指位置，
于是菜单从手指处长出来，同时卡片自己的点击 / 长按与无障碍语义都保留。

`AnchoredMenuStructureTest` 守着。

---

## 5. 沙箱层的不变式（不得放宽）

无论怎么重写，以下约束不得放宽 —— 它们已由 `app/tests/` 的断言守住：

- 引擎只 attach 在 `:zhisandbox` / `:black` / `:p0..:p49`，主进程保持干净
  （进程名在 `Bcore/.../SandboxContract.java`，单一来源）；
- 控制器进程名单一来源（`SandboxContract`），引擎与宿主层都引用它；
- 引擎调用面收敛：除 `ZhiSandbox` 外宿主层不得触碰 `BlackBoxCore`；
- guest 调试：只允许本应用私有目录的 `.so`、内存写入有上限、目标页可写检查；
- 启动阶段日志按 pid 分文件（不得退回单文件覆盖写）；
- AndroidX Startup 的 provider/receiver 必须被清单移除。

> 为什么这几条要单独留一份：它们描述的是**「哪些东西看起来可以简化、其实不能动」**。
> 后续重写时最容易踩的就是把上面某一条当成冗余顺手删掉 —— 而删掉不会编译失败。

细节与 Bcore 相对上游的补丁见 [`provenance.md`](provenance.md) 与 [`sandbox-host.md`](sandbox-host.md)。

---

## 6. 验证与边界

当前源码整理与 UI 改动采用非测试验证入口：先执行 `git diff --check`，再执行离线 Kotlin
编译，发布前执行离线 Release APK 构建。旧的结构检查与 JVM 测试目录不属于本轮源码整理范围，
也不作为本文的构建入口。

引擎、权限、文件访问与沙箱属于独立运行时边界；UI 性能改动不得通过修改这些层来绕过成本。

<!--

> **如果一处改动「写错了也不会编译失败」，那它需要的是守卫或单测**，而不是指望 review 时有人看见。

| 类别 | 位置 | 特点 |
| --- | --- | --- |
| 源码结构测试 | `app/tests/` | 零依赖、只读源码文本断言，`java` 直接跑，几秒钟 |
| JVM 行为测试 | `app/src/test/` | 真跑逻辑；`api/` 与纯函数（如切点、截断）在这里 |

```bash
bash test-source-no-build.sh          # 全部结构测试
./gradlew :app:testDebugUnitTest      # JVM 行为测试
```

完整的源码说明见 [`README`](../README.md) 与 [`CONTRIBUTING.md`](../CONTRIBUTING.md)。
-->
