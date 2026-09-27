# 蜘蛛 (ZhiCode)

一个 **Android 上的编码 Agent**：Kotlin + Jetpack Compose + [Miuix](https://github.com/compose-miuix-ui/miuix)，
应用内自带一套 Termux 环境与一个 Android 虚拟化沙箱，可以直接在手机上跑命令、改代码、装/调试 APK。

包名 `com.zhizhu.code`，应用名「蜘蛛」。

## 它现在是什么

**不是 UI 原型**。真实引擎、真实环境、真实沙箱都已经接上：

| 能力 | 实现 |
| --- | --- |
| Agent 引擎 | `core/ZhiCodeEngine.java`（流式、工具调用、子代理、steering、视觉消息过滤） |
| 模型接入 | `api/` 下的 OpenAI Responses / Chat Completions 等协议适配，支持自定义 `Base URL` 与明文 HTTP 开关 |
| 内置终端 | 自带 Termux bootstrap（`RuntimeInstaller` 解压 32MB）→ 真实 PTY（`TerminalSession`） |
| 虚拟化沙箱 | `Bcore/`（BlackBox 血统）—— 免安装运行 APK、Frida 注入、原生调试 |
| MCP | `McpStore` / `McpConfigStore` / `McpRuntime` |
| 技能 / 角色卡 / 记忆 | `SkillStore`、`RoleCardStore`、`MemoryStore` |
| 会话与任务 | `SessionStore`、`TaskStore`、`PlanStore` |
| 界面 | 全部走 Miuix 组件，语义色与字阶集中在 `ZhiColors` / `ZhiTextScale` / `ZhiRadius` / `ZhiDialogWidth` |

界面层的约定（都带守卫测试，见下文「质量守卫」）：

- 输入框统一走 `ZhiTextField`（显式文字色 + 光标落末尾）
- 长按动作菜单走 `ZhiAnchoredActionMenu`（Miuix 下拉菜单，从手指位置展开）
- Miuix 组件的转发集中在 `compose/ui/Common.kt`，调用点不直接引库

## 明确未做

- **悬浮球、Frida/Debug 图形界面**：调试走 `iqdebug` / Debug 工具，没有独立的仪表盘页面。
- **原版 IQ Code 的三套自定义调色板**（经典 / 夜间 / dark-neon）：这里按 Miuix 的
  **原生主题**走，跟随系统深浅色。
- **iOS / 桌面端**：只针对 Android。
- 部分原版设置项仍未搬过来（`SettingsRows.kt` 里能直接看到当前覆盖到的那些）。

## 构建

```bash
./gradlew :app:assembleDebug --offline
# 产物：app/build/outputs/apk/debug/ZhiCode-debug.apk
```

工具链：

- Gradle wrapper **9.3.1**、AGP **9.1.1**、Kotlin **2.4.0** + compose 编译器插件 **2.4.0**
- `compileSdk 37` / `minSdk 24` / `targetSdk 28`
- 依赖 Miuix **0.9.4**：`top.yukonga.miuix.kmp:miuix-{ui,core,icons,preference,shader,squircle}`
- `gradle.properties` 里的 `android.suppressUnsupportedCompileSdk=37` 是必须的
  （compileSdk 37 比 AGP 9.1.1 认识的更高）

### 只有 Termux / bionic 环境才需要的一处设置

AGP 自带的 aapt2 在 Termux（bionic libc）里跑不起来，必须换成 Termux 的 aapt2。
**这属于本机配置，不在仓库里** —— 它是一条绝对路径，提交进仓库会让别人 clone 之后
构建指向一个不存在的文件。

在 Termux 上开发时，写进**用户级**配置（不进版本库）：

```bash
# ~/.gradle/gradle.properties
android.aapt2FromMavenOverride=$PREFIX/bin/aapt2
```

也可以临时用命令行传：`./gradlew :app:assembleDebug -Pandroid.aapt2FromMavenOverride=$PREFIX/bin/aapt2`。

普通 Linux / macOS / Windows 开发机**不需要**这一项。

### 为什么是 AGP 9 / Gradle 9

`BreadcrumbBar` 只在 Miuix **0.9.4** 中存在，而 0.9.4 会拉入 Compose **1.12.0**，
后者要求 **AGP ≥ 9.1.0**，于是整条链一起升到 Gradle 9.3.1 + AGP 9.1.1。
升级带来三处必须改的写法：

1. **不再需要 `org.jetbrains.kotlin.android` 插件**——AGP 9 内建 Kotlin 支持，
   保留它只会得到 `plugin is no longer required` 的报错。现在只声明
   `com.android.application` + `org.jetbrains.kotlin.plugin.compose`。
2. **`android.applicationVariants` 已移除**——自定义 APK 输出名要改用
   `androidComponents { onVariants(selector().all()) { it.outputs.forEach { o -> o.outputFileName.set(...) } } }`。
3. **JDK 21** 成为必需（AGP 9 的最低 JDK）。

## 代码结构

```
app/src/main/java/com/zhizhu/zhicode/compose/
  MainActivity.kt                     ComponentActivity → setContent { ZhiCodeApp() }
  theme/ZhiTheme.kt                   ZhiColors / ZhiRadius / ZhiMotion + 深浅色装配
  theme/ZhiTextStyles.kt             应用自有紧凑字阶（ZhiTextScale + zhiTextStyles()）
  model/UiModels.kt                   ChatItem / ToolActivity / SessionSummary / WorkspaceTab /
                                      PermissionMode / EffortLevel / SLASH_COMMANDS 等
  data/                              ApiConfig / Mcp / Memory / ModelCatalog / RoleCard / Skill /
                                      Session / GitChanges / FileBrowser 等真实数据层
  state/WorkspaceViewModel.kt         StateFlow<WorkspaceUiState> + EngineEvents 实现
  ui/Animations.kt                    ZhiMotion 动画时长/缓动常量
  ui/ZhiIcons.kt / ZhiVectorIcons.kt  图标统一出口
  ui/Common.kt                        ★ Miuix 组件的唯一转发层（ZhiIconButton / ZhiChip /
                                      ZhiTextField / ZhiAnchoredActionMenu / ZhiDialogWidth …）
  ui/TopBar.kt                        顶栏
  ui/Sidebar.kt                       侧栏与会话行
  ui/AppScaffold.kt                   Scaffold + 宽窄屏布局 + 弹窗挂载点
  ui/chat/ChatList.kt                 对话流（LazyColumn + 自动吸底）
  ui/chat/MessageCards.kt             各类消息卡片
  ui/chat/AgentProgressCard.kt        Agent 进度卡
  ui/composer/Composer.kt             输入器
  ui/composer/SlashPalette.kt         斜杠命令面板
  ui/panes/{ChangesPane,TerminalPane,FilesPane,PaneHeader}.kt
  ui/dialogs/                         各弹窗（外壳见 dialogs/DialogShell.kt）
  ui/settings/                        设置页与设置行
```

## Miuix 组件映射

| 界面元素 | Miuix 组件 |
| --- | --- |
| 应用容器 | `Scaffold`（Overlay 系列弹窗必须在其内容里才能找到 `popupHost`） |
| 主题 | `MiuixTheme(colors = lightColorScheme()/darkColorScheme())` |
| 工作区 Tab | `ZhiSegmentedTabs` → Miuix `TabRowWithContour`（带轮廓的分段控件） |
| 文件路径 | `BreadcrumbBar`（0.9.4 新增，点击任一层级直接跳转） |
| 图标 | `MiuixIcons.Regular.*`（经 `ui/ZhiIcons.kt` 统一出口，无自绘/字形图标） |
| 消息与工具卡片 | `Card`（含 `pressFeedbackType = Sink` 与原生的按压/涟漪反馈） |
| 输入框 | `TextField` |
| 按钮 | `Button` / `TextButton` |
| 忙碌指示 | `InfiniteProgressIndicator` |
| 窄屏侧栏 | 自写 `IqSideDrawer`（左侧滑入 + 半透明遮罩点击关闭） |
| 授权 / 计划 / 选择器 | `WindowDialog`（真正的独立 Window；选择器行用 `Card` + `Checkbox`） |
| 会话搜索 | `InputField`（`SearchBar` 的子组件） |
| 滚动条 | `VerticalScrollBar` + `rememberScrollBarAdapter` |
| 计数标签 | `Badge` |
| 图标提示 | `TooltipBox` |

对话流用 `androidx.compose.foundation.lazy.LazyColumn`——Miuix 已移除自己的 LazyColumn。

> 注：Miuix `TopAppBar` 是大标题布局，与原版 48dp 紧凑顶栏差异较大，
> 因此顶栏用自写的单行 Row（保持相同的 `buildGlobalBar()` 结构），只复用 Miuix 色板与文字样式。

### Miuix 组件使用清单

Miuix 0.9.4 提供 **33 个 basic + 4 个 overlay** 组件。下面逐项标注使用情况；
**偏离的都写明了理由**，不是遗漏。

| 组件 | 状态 | 用在哪 / 为何不用 |
| --- | --- | --- |
| `Scaffold` / `Surface` / `Card` | 已用 | 容器、抽屉、侧栏、面板底色、对话卡、工具卡、diff 卡、输入卡片、斜杠面板行、选择器行 |
| `Text` / `Icon` | 已用 | 全局 |
| `IconButton` | 已用 | `IqIconButton` / `IqFilledIconButton` 的内部实现 |
| `LinearProgressIndicator` | 已用 | 顶栏忙碌条（`progress = null` 不确定态）、Agent 进度条 |
| `InfiniteProgressIndicator` | 已用 | 工作指示器 |
| `HorizontalDivider` / `VerticalDivider` | 已用 | 所有分隔线 |
| `SmallTitle` | 已用 | 侧栏分区标题 |
| `Badge` | 已用 | 工具组完成计数 |
| `Switch` | 已用 | 授权弹窗的"始终允许" |
| `Checkbox` | 已用 | 选择器选中态。未选中时 Miuix `RadioButton` 完全不绘制，行首只剩一块空白，所以改用 `Checkbox` |
| `WindowDialog` | 已用 | 授权 / 计划审批 / 通用选择器（见下方「模态窗口」） |
| `BreadcrumbBar` | 已用 | 文件面板路径（0.9.4 新增） |
| `TextField` | 已用 | 输入器 |
| `InputField` | 已用 | 侧栏会话搜索 |
| `Button` / `TextButton` | 已用 | 弹窗动作：主按钮 `buttonColorsPrimary`，次按钮 `TextButton` 默认配色 |
| `VerticalScrollBar` + `rememberScrollBarAdapter` | 已用 | 对话流、终端缓冲、文件列表 |
| `TooltipBox` | 已用 | 顶栏三个图标按钮的说明气泡 |
| `SnackbarHost` | **已移除** | 浮层遮挡底部输入器，按需求删除 |
| `TabRow` | **不用** | 选中胶囊向外绘制会盖住相邻内容，改用 `ZhiSegmentedTabs`（Miuix `TabRowWithContour`） |
| `TopAppBar` | **不用** | 大标题布局与原版 48dp 紧凑栏差异大，改用自写单行 `Row` |
| `OverlayBottomSheet` | **不用** | 需求改为左侧滑入抽屉，自写 `IqSideDrawer` |
| `OverlayListPopup` | **不用** | 其 `PopupPositionProvider.calculatePosition` 需要真实锚点矩形，是**锚定下拉/右键菜单** API，拿来当居中模态选择器属于误用 |
| `InputField` 用在文件面板 | **不用** | 内部 `defaultMinSize` 下限 45dp，常驻会把文件列表挤下去，与"文件 UI 紧凑"冲突；改放到侧栏 |
| `ScrollBar` / `HorizontalScrollBar` | 未用 | 无横向滚动场景 |
| `Dropdown` | 未用 | 模型 / 权限 / 推理目前走选择器弹窗，改 Dropdown 需重构触发点语义 |
| `SearchBar` | 未用 | 只用其 `InputField` 子组件 |
| `PullToRefresh` | 未用 | Mock 数据源没有真实刷新源 |
| `FloatingActionButton` | 未用 | 发送/停止按钮已在输入器内 |
| `NavigationBar` / `NavigationRail` | 未用 | 窄屏用按键组 + 抽屉，宽屏用常驻侧栏 |
| `Slider` / `NumberPicker` / `ColorPicker` / `ColorPalette` | 未用 | 无对应设置项（设置页本身未实现） |
| `miuix-squircle` | 间接已用 | `Card` / `Surface` / `IconButton` 内部走 `squircleSurface`，无需手动调用 |
| `miuix-preference` 全部 | 未用 | 对应设置页，属于新功能而非替换 |
| `miuix-shader` | 未用 | 无自定义着色需求 |

## 模态窗口（授权 / 提交计划 / 选择器）

三个模态窗口都在 `ui/dialogs/Dialogs.kt`，统一走 Miuix `window` 包的 `WindowDialog`
（**真正的独立 Android Window**，不是 `overlay` 包的 `OverlayDialog` 覆盖层）。

踩过的坑与对应处理，改这三个窗口前请先读：

| 现象 | 原因（从 0.9.4 字节码实读） | 处理 |
| --- | --- | --- |
| 窗口贴在屏幕底部 | `DialogDefaults.isLargeScreen` 按**容器 width ≥ 840dp 且 height ≥ 480dp** 推断；手机判 false → `Alignment.BottomCenter` | 显式传 `largeScreen = true` → `Alignment.Center` |
| 窗口又窄又小 | `outsideMargin` 12dp×12dp + `insideMargin` 24dp×24dp，360dp 屏上内容宽度只剩 288dp | 传 `DialogWideOutsideMargin` (6,12) + `DialogWideInsideMargin` (14,14) |
| 标题被强制居中染色 | `DialogContent` 里 `title` / `summary` 硬编码 `TextAlign.Center` + `titleColor` | **不用** `title` 参数，自渲染左对齐标题 |
| 弹窗内卡片"隐身" | 深色方案 `background` 与 `surfaceContainerHigh` **都是 `#242424`**，完全同色 | 中间内容区加一层纯黑 `Surface` 底板（`contentBackdrop = Color.Black`） |
| 底部按钮被挤出屏幕 | 内容区写死 `heightIn(max = …)`，叠加标题/说明/输入框后总高超出屏高 | 内容区改 `weight(1f, fill = false)` **吃剩余高度**并内部滚动，按钮区永远留在屏内 |
| 选择器行首一块空白 | Miuix `RadioButton` **未选中时不绘制任何东西**（对比 `Checkbox` 未选中画方框） | 改用 `Checkbox` |
| 点选项窗口就关了 | 选项回调直接调 `onSelect()`，ViewModel 立即提交并把 `choicePicker` 置空 | 选项点击只改**本地** `localSelected`；「提交」是唯一提交入口，未选时按钮 `enabled = false` |
| 两个按钮观感不统一 | 次按钮曾传 `color = Color.Transparent` 做纯文字 | 改用 `TextButton` **默认配色**（`secondaryVariant` 填充 + `onSecondaryVariant` 文字） |

字号：Miuix 字号阶梯偏大（`title1=32sp` … `body2=14sp`、`button=17sp`），且
`BasicComponent` 的标题走 `headline1`、说明走 `body2`，`TextField` / `TextButton`
也走主题样式 —— **逐个传 `fontSize` 无效**。因此弹窗内嵌一层
`MiuixTheme(textStyles = compactDialogTextStyles())` 整体收小，覆盖
`main/paragraph/body1/body2/button/footnote1/footnote2/subtitle/headline1/headline2`。

> **验证限制**：`WindowDialog` 是独立 Window，IQ 沙箱的 `screenshot`（PixelCopy）
> 与 `dump_ui` 只能观察 MainActivity 自己的窗口，**看不到它**。
> 因此弹窗排版只能在真机（或临时换成能观察的人合成）确认。

## 动画

统一收在 `ui/Animations.kt` 的 `ZhiMotion` 里，不要在各处硬编码时长：

| 常量 | 值 | 用途 |
| --- | --- | --- |
| `ZhiMotion.FAST` | 160ms | 遮罩淡入淡出、按钮展开收拢 |
| `ZhiMotion.EXPAND` | 240ms | `animateContentSize` 卡片展开 |
| `ZhiMotion.MEDIUM` | 280ms | 抽屉/面板位移、颜色过渡、进度条 |
| `ZhiMotion.linear` | 1200ms | 顶栏无限扫动进度条 |

按类别分布：

- **按压反馈**：交给 Miuix `Card` / `Surface(onClick)` 自带的 `pressFeedbackType`，
  不再叠加自写缩放（否则会形成"双重按压"）。按压反馈由 Miuix 自带，不再自写缩放。
- **颜色过渡**：chip / 分段按键组 / 侧栏行用 `animateColorAsState(tween(ZhiMotion.MEDIUM))`。
- **伸缩**：消息卡、工具组、思考面板、diff 文件卡用 `animateContentSize(tween(ZhiMotion.EXPAND))`。
- **进场退场**：斜杠面板、附件条、停止按钮用 `AnimatedVisibility`；
  工作区面板切换用 `AnimatedContent`（位移 + 淡入淡出）；窄屏侧栏用 `slideInHorizontally` + 遮罩 `fadeIn`。
- **列表增删**：对话流每项包 `Modifier.animateItem()`，新消息与工作状态条都带插入动画。

## 架构要点

### 引擎已经接上了

`WorkspaceViewModel` 实现 `EngineEvents`，真实引擎 `ZhiCodeEngine` 在**第一次发消息时**
懒创建。流式回复、工具调用、权限询问、子代理都走它，不再是模拟数据。

```kotlin
class WorkspaceViewModel(
    application: Application,
    private val repo: WorkspaceRepository = MockWorkspaceRepository(),
) : AndroidViewModel(application), EngineEvents
```

⚠️ `WorkspaceRepository` / `MockWorkspaceRepository` **现在只剩终端占位横幅一项用途**，
名字容易误导。对话流、工具执行、会话文件都已经不经它了 —— 看到这个名字别以为主流程还是 Mock。

### 权限与终端

- **授权弹窗**：`requestPermission()` 用 `CompletableDeferred` 挂起等待用户点击，
  真实实现把引擎侧的权限请求映射成 `PermissionRequest`。
- **终端面板**：`TerminalPane` 接收 PTY 的增量输出；`TerminalSession` 管理真实会话。

### 长按菜单与输入框的两条约定

这两个都是踩过坑之后收口的，改动前请先读对应源码里的注释：

- 输入框一律走 `ZhiTextField`（`compose/ui/Common.kt`）—— 它负责两件不加就出错的事：
  显式文字色（否则会被弹窗里 `Card` 的 `contentColor` 吃掉、输入的字与底色分不清）、
  以及外部改值时把光标放到末尾（否则光标恒在 0，输入 `123` 会变成 `231`）。
- 长按动作菜单走 `ZhiAnchoredActionMenu`：Miuix 下拉菜单 + 一个**只观察不消费**的
  指针修饰符（`zhiObservePointer`）拿到手指位置，于是菜单从手指处长出来，
  同时卡片自己的点击/长按与无障碍语义都保留。

## 沙箱调试提示

Compose 在 `dump_ui` 里只会呈现一个 `AndroidComposeView`（无法定位内部节点），
因此验证界面**必须靠截图**：

```bash
iqsandbox install com.zhizhu.code   # 或 Sandbox action=install
iqsandbox launch  com.zhizhu.code
iqsandbox screenshot com.zhizhu.code
```

`Sandbox action=tap` 的 `x/y` 是 **window 像素**（直接派发 `MotionEvent` 给 decor view），
不是截图像素。本机截图为 688×1452、window 为 1080×2279，换算系数约 **1.5696**。

## Miuix 组件合规统计

改造前：Miuix 的 33 个 basic 组件里只用了 **12 个**，工程内有 **78 处**手写
`Box + background + clip(RoundedCornerShape)`（本应由 `Card` / `Surface` 承担）。

改造后：

```
./gradlew :app:assembleDebug
grep -rcE '\.background\(|RoundedCornerShape\(' --include=*.kt \
  app/src/main/java/com/zhizhu/zhicode/compose | awk -F: '{s+=$2} END {print s}'
```

- 手写绘制站点 **78 → 9**，剩余的是传给 Miuix `Surface(shape = …)` 的 Shape 参数，
  以及 diff 逐行的**语义着色**（`+` 绿 / `−` 红，不是装饰容器）。
- 接入的 Miuix 组件（这一行按当前代码里的实际 import 列，不是历史清单）：
  `Card`、`Surface(onClick)`、`IconButton`、`Button`/`TextButton`、`SmallTitle`、
  `HorizontalDivider`、`VerticalDivider`、`LinearProgressIndicator`、`Badge`、`Switch`、
  `BasicComponent`、`TabRowWithContour`、`OverlayDialog`、`OverlayDropdownPopup`、
  `DropdownEntry`/`DropdownItem`、`BreadcrumbBar`、`VerticalScrollBar`、`TooltipBox`、
  `TextField`、`Scaffold`。

**几处刻意不用**（改动前先看清理由，各自在源码注释里）：

- `WindowDialog` —— 它另开一个 Android 窗口，拿不到 `Scaffold` 的 `popupHost`，
  内部的 `Overlay*` 会失效，也参与不了主窗口的背景模糊。全部改用 `OverlayDialog`。
- `Checkbox` —— 固定 26dp 且是圆形，从外部改不小（`requiredSize` 在调用方 modifier 之后）。
  下拉/选择器里的选中态改用与 Miuix 一致的 `Check` 图标 + `DropdownDefaults.CheckIconSize`。
- `TabRow` —— 选中胶囊向外绘制会盖住相邻内容，改用 `TabRowWithContour`。

自写的按压缩放（曾经的 `iqPressScale` / `IqPressable`）已**删除**（零调用点）：Miuix `Card` / `Surface(onClick)`
自带按压反馈，再叠自写缩放会双重触发。

## 质量守卫

除常规单测外，`test-source-no-build.sh` 里有一组**只读源码的结构测试**（不需要 Android SDK，
`java` 直接跑单文件即可），专门拦「改坏了不会编译失败、只会表现为界面或行为不对」的那类问题：

| 守卫 | 拦什么 |
| --- | --- |
| `TypographyScaleTest` | 裸 `fontSize = N.sp`、第二份字阶、主题未接上字阶 |
| `TextFieldConventionTest` | 绕过 `ZhiTextField` 直接用裸输入框（会丢文字色 / 光标位置） |
| `AnchoredMenuStructureTest` | 长按菜单接线断裂；观察器**消费事件**（会顶掉点击与无障碍语义） |
| `LayoutConsistencyTest` | 弹窗宽度/边距写字面量、气泡用强制比例宽度、触发方式写成点击 |

```
bash test-source-no-build.sh
```

## 发布与签名

```bash
./gradlew :app:assembleRelease --offline
# 产物：app/build/outputs/apk/release/ZhiCode-release.apk
```

**签名方案：只启用 APK Signature Scheme v2**（v1/v3/v4 都关）。
v2 校验的是整个 APK 文件而不是 JAR 条目，能挡住 v1 时代「改一个字节仍通过校验」
那类篡改；而 `minSdk 24` 起所有目标设备都支持 v2，所以 v1 没有必要，留着只会
多一份可被旧式攻击面利用的签名。可用 `apksigner` 复核：

```bash
$ANDROID_HOME/build-tools/<版本>/apksigner verify --verbose app/build/outputs/apk/release/ZhiCode-release.apk
# Verified using v2 scheme (APK Signature Scheme v2): true
```

签名信息只保留 `CN=zhizhu0002`（自签证书，RSA 4096 / SHA256withRSA，有效期 30 年）。

### 密钥不进版本库

签名配置从**仓库之外**读取，按优先级：

1. `release.properties`（本机专属，**已加入 .gitignore**）
2. 环境变量：`ZHICODE_STORE_FILE` / `ZHICODE_STORE_PASSWORD` / `ZHICODE_KEY_ALIAS` / `ZHICODE_KEY_PASSWORD`（CI 用）

两者都读不到时，`assembleRelease` **仍能构建**，只是产物未签名并打印一条告警 ——
别人 clone 之后不会因为缺密钥而卡住。

> ⚠️ **密钥库与口令必须单独备份**（密钥库放在仓库之外，例如 `~/.android-keys/`）。
> Android 只认签名、不认人：密钥丢了就**再也发不出同一个应用的更新** ——
> 签名不同的 APK 无法覆盖安装，用户必须先卸载，等于清空他们的数据。

## 许可

- **本工程自身代码：MIT**，见 [`LICENSE`](LICENSE)。
- 分发物里含第三方组件（Apache-2.0 组件、Termux 二进制等），逐项清单见
  [`NOTICE`](NOTICE)，许可原文见 [`THIRD-PARTY-LICENSES/`](THIRD-PARTY-LICENSES)，
  **判断依据与复核方式**见 [`docs/licensing.md`](docs/licensing.md)。

> 关于「哪些代码是自己写的」：`docs/licensing.md` 里用**净相同行**做了可核对的说明
> （逐行相同多少行、其中骨架与协议串扣掉多少），并写明了这个数的算法与可能被做手脚的位置。
> 作者的目标是把它降到 0，**当前尚未达到**。

## 上游与致谢

- [Miuix](https://github.com/compose-miuix-ui/miuix)（Apache-2.0）—— 全部界面组件与主题
- [BlackBox](https://github.com/ALEX5402/NewBlackbox)（Apache-2.0）—— 虚拟化沙箱引擎
- [Termux](https://github.com/termux)（各组件许可见 NOTICE）—— 内置 Linux 环境
- [Dobby](https://github.com/jmpews/Dobby)（Apache-2.0）—— 内联 hook

## 贡献与安全

- 提交代码前请读 [`CONTRIBUTING.md`](CONTRIBUTING.md)（特别是「守卫测试」与「不要顺手重构」两条）。
- **这是一个会执行任意代码的应用**（自带 shell、可在虚拟沙箱里运行 APK）。
  安全模型与漏洞报告方式见 [`SECURITY.md`](SECURITY.md)。
