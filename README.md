# ZhiCode

一个 **Android 上的编码 Agent**：Kotlin + Jetpack Compose + [Miuix](https://github.com/compose-miuix-ui/miuix)，
应用内自带一套 Termux 环境与一个 Android 虚拟化沙箱，可以直接在手机上跑命令、改代码、装/调试 APK。

包名 `com.zhizhu.code`，应用名 `ZhiCode`。目标档位从 **Android 8（API 26）** 起。

---

## 功能

**不是 UI 原型** —— 真实引擎、真实环境、真实沙箱都已经接上：

- **Agent 引擎** — `app/src/main/java/com/termux/app/zhicode/core/ZhiCodeEngine.java`：流式输出、工具调用、子代理、steering、视觉消息过滤
- **模型接入** — `app/src/main/java/com/termux/app/zhicode/api/` 下的 OpenAI Responses / Chat Completions / Anthropic 等协议适配，支持自定义 Base URL 与明文 HTTP 开关
- **内置终端** — 自带 Termux bootstrap（解压约 32 MB）→ 真实 PTY（`TerminalSession` + `libtermux.so`）
- **虚拟化沙箱** — `Bcore/`（BlackBox 血统）：免安装运行 APK、Frida 注入、原生调试
- **MCP / 技能 / 角色卡 / 记忆** — `McpStore`、`McpRuntime`、`SkillStore`、`RoleCardStore`、`MemoryStore`
- **会话与任务** — `SessionStore`、`TaskStore`、`PlanStore`
- **权限模式** — 每次询问 / 自动编辑 / 规划 / 自动 / 不询问 / 跳过权限，见 [`SECURITY.md`](SECURITY.md)

### 明确未做

- **悬浮球、Frida/Debug 图形界面**：调试走 `iqdebug` / Debug 工具，没有独立仪表盘页面。
- **原版 IQ Code 的三套自定义调色板**：这里走 Miuix **原生主题**，跟随系统深浅色。
- **iOS / 桌面端**：只针对 Android。
- 部分原版设置项仍未搬过来（`SettingsRows.kt` 里能看到当前覆盖范围）。

---

## 构建

```bash
./gradlew :app:assembleDebug --offline
# 产物：app/build/outputs/apk/debug/ZhiCode-debug.apk
```

工具链：JDK **21** / Gradle **9.3.1**（wrapper）/ AGP **9.1.1** / Kotlin **2.4.0**；
`compileSdk 37` / `minSdk 24` / `targetSdk 28`；Miuix **0.9.4**；仅 **arm64-v8a**。

> 在 Termux（bionic）里构建需要额外配一次 aapt2，普通开发机不需要 —— 见
> [`docs/build-and-release.md`](docs/build-and-release.md) 第 2 节。

### release

```bash
./gradlew :app:assembleRelease --offline
# 产物：app/build/outputs/apk/release/ZhiCode-release.apk
```

`app` 与 `Bcore` 的 release 都开 **R8**（`minifyEnabled true`，app 另有 `shrinkResources true`）；
**debug 不混淆**。实测体积 **43,210,837 → 35,911,932 字节（−16.9%）**。

本工程有反射与原生 hook，所以规则不是照抄模板 —— 每条都在
[`docs/build-and-release.md`](docs/build-and-release.md) 里写了它挡的是什么
（JNI 名字绑定、注解驱动反射、隐藏 API 的假警报）。

启动路径另有一份手写的 `app/src/main/baseline-prof.txt` + `androidx.profileinstaller`。
签名只启用 **APK Signature Scheme v2**；密钥从仓库之外读取，**不进版本库**。

发版时请一并留存 `app/build/outputs/mapping/release/mapping.txt` —— 没有它，用户报的崩溃栈
无法还原成源码位置。

---

## 代码结构

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

应用源码还包含 `app/src/main/java/com/termux/app/zhicode/` 下的协议、引擎、Termux 与沙箱宿主代码；
`Bcore/`、`black-reflection/`、`compiler/` 是独立模块。界面源码位于
`app/src/main/java/com/zhizhu/zhicode/compose/`，其中 `ui/chat/`、`ui/panes/` 与 `ui/dialogs/`
分别承载对话流、工作区面板和弹窗。

细节见 [`docs/architecture.md`](docs/architecture.md)。

---

## 界面层

全部走 Miuix，**唯一的转发层是 `compose/ui/Common.kt`** —— 调用点用 `Zhi*` 包装、不直接引库，
库升级只影响一个文件。尺寸 / 颜色 / 字阶一律取自 `ZhiColors` / `ZhiRadius` / `ZhiTextStyles` /
`ZhiMotion` 等 token，不要在调用点写新数字。工作区、文件根、模型 API、MCP 与调试页的分段控件
统一通过 `ZhiSegmentedTabs` 使用 Miuix `TabRowWithContour`；工作区与文件根的页内容由 Pager 驱动，
顶层页签禁用横向手势，点击是唯一切换入口。

有几处**刻意不用** Miuix 的组件（`WindowDialog`、`Checkbox`、`RadioButton`、`TabRow`、
`InputField`、`SnackbarHost` 等），每条都有理由；弹窗上还踩过一批只有真机才看得出来的坑。
改界面前请先读 [`docs/ui-miuix.md`](docs/ui-miuix.md)。

---

## 性能

目标档位包含 **Android 8（API 26）** 那一代设备（CPU 慢、闪存慢、GPU 弱），
所以流式热路径、超长输出的渲染、主线程 IO、组件收到的参数都做过针对性处理。

这些改动**都不会报错、不会崩溃、不会让界面坏掉** —— 只会掉帧、发烫。
所以每一条都由守卫测试与 JVM 单测钉住，取舍与理由见 [`docs/performance.md`](docs/performance.md)。

---

## 质量说明

项目历史上包含源码结构检查与 JVM 行为测试，但当前开发流程以源码审查、`git diff --check`
和离线 Kotlin/Release 编译为准；不要把旧测试入口当作发布构建入口。本文不列出已废弃的
守卫文件或测试命令，避免将过时流程误当成当前工具链的一部分。

当前 UI 性能约定包括：对话流使用稳定 block key 的单一外层 `LazyColumn`；工具组和思考面板
按高度收回/展开；工具输出使用有界窗口与内层 lazy chunks；文件查询在 IO 线程去抖并丢弃过期结果。

| 守卫 | 拦什么 |
| --- | --- |
| `TypographyScaleTest` | 裸 `fontSize = N.sp`、第二份字阶、主题未接上字阶 |
| `TextFieldConventionTest` | 绕过 `ZhiTextField` 直接用裸输入框（会丢文字色 / 光标位置） |
| `AnchoredMenuStructureTest` | 长按菜单接线断裂；观察器**消费事件**（会顶掉点击与无障碍语义） |
| `LayoutConsistencyTest` | 弹窗宽度/边距写字面量、气泡用强制比例宽度、触发方式写成点击 |
| `MarkdownStreamingTest` | 流式每 32ms 重解析整篇、行内解析不缓存、流式期间挂尺寸动画 |
| `ToolOutputBoundTest` | 超长输出没有行数上限、对整份输出做 O(n) 字符串手术、下拉 `items` 未缓存 |
| `MainThreadIoBoundTest` | 磁盘 IO 在主线程、热路径组件收整份 UiState、`@Immutable` 与字段类型不自洽 |
| `R8ConfigTest` | R8 被关掉、JNI 名字绑定的 keep 被删、baseline profile 规则非法 |
| `DialogScrollNestingTest` | 弹窗内嵌套滚动（点开就闪退） |
| `NoBundledThirdPartyEndpointTest` | 引入内置的第三方统计 / 上报端点 |

```bash
bash test-source-no-build.sh          # 全部结构测试
./gradlew :app:testDebugUnitTest      # JVM 行为测试
```

---

## 文档

| 文件 | 内容 |
| --- | --- |
| [`docs/architecture.md`](docs/architecture.md) | 代码结构、状态与引擎怎么接、沙箱层不得放宽的不变式 |
| [`docs/build-and-release.md`](docs/build-and-release.md) | 工具链、R8 规则、Baseline Profile、签名与 CI |
| [`docs/ui-miuix.md`](docs/ui-miuix.md) | Miuix 转发层、刻意不用的组件、弹窗踩过的坑、动画 token |
| [`docs/performance.md`](docs/performance.md) | 面向 Android 8 的每一条性能取舍与它守住的机制 |
| [`docs/sandbox-host.md`](docs/sandbox-host.md) | 沙箱宿主的实现与调试方式 |
| [`docs/provenance.md`](docs/provenance.md) | Bcore 相对上游的补丁、沙箱层不变式 |
| [`docs/licensing.md`](docs/licensing.md) | 许可判断依据与复核方式 |

---

## 贡献

欢迎参与！提交前请读 [`CONTRIBUTING.md`](CONTRIBUTING.md)，并保持修改范围与当前模块边界一致。
推荐的最小本地验证是 `git diff --check` 与 `./gradlew :app:compileReleaseKotlin --offline`；发布前再执行
`./gradlew :app:assembleRelease --offline`。

---

## 安全

⚠️ **这是一个会执行任意代码的应用**：自带 shell、可让模型决定执行什么命令，还能在虚拟沙箱里
运行任意 APK。

安全模型、密钥怎么存、声明了哪些权限、以及漏洞报告方式见 [`SECURITY.md`](SECURITY.md)。
**安全问题请不要开公开 issue。**

---

## 许可

本工程自身代码用 **MIT**（[`LICENSE`](LICENSE)）；发行物里含第三方组件（Apache-2.0 组件、
Termux 二进制等），逐项清单见 [`NOTICE`](NOTICE)，许可原文见 [`THIRD-PARTY-LICENSES/`](THIRD-PARTY-LICENSES)，
**判断依据与复核方式**见 [`docs/licensing.md`](docs/licensing.md)。

> 「哪些代码是自己写的」在 `docs/licensing.md` 里用**净相同行**做了可核对的说明，
> 并写出了这个数的算法与可能被做手脚的位置。作者的目标是把它降到 0，**当前尚未达到**。

---

## 上游与致谢

> **本工程的起点是 [IQ Code](https://github.com/iqisge-gif/IQ-Code-Android)（MIT，© 2026 IQge）。**
>
> 没有它就没有这个项目：`api/` 协议层、Agent 主循环与工具集、沙箱宿主层，都是从它那里起步的。
> 原作者还**明确许可**本工程改写、改名与发行，并同意不强制要求随附其版权声明 —— 也就是说下面这条
> 署名**不是许可义务，而是我们自愿保留的致谢**（完整记录见 [`NOTICE`](NOTICE) 第 1 节与
> [`docs/licensing.md`](docs/licensing.md)）。
>
> 本仓库至今仍用 [`tools/provenance.sh`](tools/provenance.sh) 拿它逐行比对、把「净相同行」一路降到 0
> —— 派生关系在这里本来就是自证的。**特别致谢。**

其余上游：

- [Miuix](https://github.com/compose-miuix-ui/miuix)（Apache-2.0）—— 全部界面组件与主题
- [BlackBox](https://github.com/ALEX5402/NewBlackbox)（Apache-2.0）—— 虚拟化沙箱引擎
- [Termux](https://github.com/termux)（各组件许可见 NOTICE）—— 内置 Linux 环境
- [Dobby](https://github.com/jmpews/Dobby)（Apache-2.0）—— 内联 hook
