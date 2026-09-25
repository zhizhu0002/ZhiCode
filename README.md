# IQ-Code-Compose

用 **Kotlin + Jetpack Compose + Miuix** 重写的 IQ Code 主界面。

目标是把 `IQ-Code-Android`（纯 Java 程序化构建 UI，`MainActivity.java` 单文件 5158 行 / 391 KB）
的界面结构，用 Miuix 组件重做一遍，观感保持一致。

## 当前状态

第一期：**主界面全量壳 + 内存 Mock 数据**。

已完成并在 IQ 沙箱逐屏截图验证：

| 区域 | 对应原版方法 | 状态 |
| --- | --- | --- |
| 顶栏 | `buildGlobalBar()` | ✅ 品牌名 / 模型状态 / 上下文 chip / 设备时间 / 主题切换 / 悬浮球 / 设置 |
| 工作区 Tab | `buildWorkspaceTabs()` | ✅ 对话 / 变更 / 终端 / 文件 |
| 对话流 | `renderChat()` `addChatView()` | ✅ 空态 / 用户气泡 / 助手卡 / 工具卡 / 折叠工具组 / 思考面板 / 上下文页脚 / 工作指示器 |
| Agent 进度卡 | `AgentProgressView` | ✅ 任务清单 + 进度条 + 计划状态 |
| 输入器 | `buildComposer()` | ✅ 多行输入 / 附件条 / 权限·推理·模型 chips / 发送 / 停止 |
| 斜杠命令面板 | `updateSlashPalette()` | ✅ 34 条命令按前缀过滤，与原版命令表一致 |
| 侧栏 | `buildSidebar()` `sessionRow()` | ✅ 新会话 / 项目上下文 / 切换路径 / 会话列表 / 工作区入口 / 运行环境 / 设置 |
| 变更面板 | `renderChanges()` `colorDiff()` | ✅ diff 逐行着色（+绿 / −红 / @@强调）+ `+N −M` 统计 |
| 终端面板 | `renderTerminal()` | ⚠️ 只还原外观（等宽缓冲 + 输入行），**未接真实 PTY** |
| 文件面板 | `renderFiles()` | ✅ 目录浏览 + 面包屑 + 只读代码查看（带行号） |
| 授权 / 计划 / 选择器弹窗 | `showPermissionDialog()` `showPlanApprovalDialog()` `showChoicePicker()` | ✅ |

## 明确未做

- **真实 Agent / LLM 引擎**：全部是 Mock（`MockWorkspaceRepository` 模拟流式回复与工具进度）。
- **真实 Termux PTY**：终端面板是只读占位。
- **真实 Git 读取 / 文件读写**：变更与文件面板用假数据，文件面板只读。
- **设置页全量表单**：原版的设置页有多组 Spinner / 调色板 / API Profile 管理，本期只在侧栏与顶栏留入口（落到 `/config`）。
- **悬浮球、IQ 沙箱仪表盘、Frida/Debug 界面**。
- **IQ Code 的三套自定义调色板**（经典 / 夜间 / dark-neon）：本期按设计改为 **Miuix 原生主题**，跟随系统深浅色并动态取色。

## 构建

```bash
cd ~/projects/IQ-Code-Compose
./gradlew :app:assembleDebug --offline
# 产物：app/build/outputs/apk/debug/IQCodeCompose-debug.apk
```

工具链（与本机已缓存的版本严格对应，注意 Termux 环境的两处特殊设置）：

- Gradle wrapper **9.3.1**、AGP **9.1.1**、Kotlin **2.4.0** + compose 编译器插件 **2.4.0**
- `compileSdk 37` / `minSdk 24` / `targetSdk 28`
- `gradle.properties` 里**必须**有：
  ```
  android.aapt2FromMavenOverride=/data/user/0/com.iqge/files/usr/bin/aapt2
  android.suppressUnsupportedCompileSdk=37
  ```
- 依赖 Miuix **0.9.4**：`top.yukonga.miuix.kmp:miuix-{ui,core,icons,preference,shader,squircle}`

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
app/src/main/java/com/iqge/iqcode/compose/
  MainActivity.kt                     ComponentActivity → setContent { IqCodeApp() }
  theme/IqTheme.kt                    Dimens 尺度常量 + IqColors（diff 用绿/红，Miuix 原生色板没有）
  model/UiModels.kt                   ChatItem / ToolActivity / SessionSummary / WorkspaceTab /
                                      PermissionMode / EffortLevel / SLASH_COMMANDS 等
  data/WorkspaceRepository.kt         数据来源接口（真实引擎的接入点）
  data/MockWorkspaceRepository.kt     内存 Mock：假会话、假 diff、假文件树、模拟流式与工具序列
  state/WorkspaceViewModel.kt         StateFlow<WorkspaceUiState>，全部交互入口
  ui/Animations.kt                    IqMotion 动画时长/缓动常量 + iqPressScale / IqPressable
  ui/IqIcons.kt                       图标统一出口，全部取自 MiuixIcons.Regular
  ui/ButtonGroup.kt                   IqButtonGroup / IqButtonGroupBar（分段按键组）
  ui/Common.kt                        图标按钮 / chip / pill / 分区标题 / 分隔线 / 用量条
  ui/TopBar.kt                        顶栏
  ui/Sidebar.kt                       侧栏与会话行
  ui/AppScaffold.kt                   Scaffold + 宽窄屏布局 + 弹窗挂载点
  ui/chat/ChatList.kt                 对话流（LazyColumn + 自动吸底）
  ui/chat/MessageCards.kt             各类消息卡片
  ui/chat/AgentProgressCard.kt        Agent 进度卡
  ui/composer/Composer.kt             输入器
  ui/composer/SlashPalette.kt         斜杠命令面板
  ui/panes/{ChangesPane,TerminalPane,FilesPane,PaneHeader}.kt
  ui/dialogs/Dialogs.kt               授权 / 计划审批 / 通用选择器
```

## Miuix 组件映射

| 界面元素 | Miuix 组件 |
| --- | --- |
| 应用容器 | `Scaffold`（Overlay 系列弹窗必须在其内容里才能找到 `popupHost`） |
| 主题 | `MiuixTheme(colors = lightColorScheme()/darkColorScheme())` |
| 工作区 Tab | `IqButtonGroupBar`（自写分段按键组，Miuix `TabRow` 的选中胶囊会溢出盖住内容） |
| 文件路径 | `BreadcrumbBar`（0.9.4 新增，点击任一层级直接跳转） |
| 图标 | `MiuixIcons.Regular.*`（经 `ui/IqIcons.kt` 统一出口，无自绘/字形图标） |
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
| `TabRow` | **不用** | 选中胶囊向外绘制会盖住相邻内容，改用自写 `IqButtonGroupBar` |
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

统一收在 `ui/Animations.kt` 的 `IqMotion` 里，不要在各处硬编码时长：

| 常量 | 值 | 用途 |
| --- | --- | --- |
| `IqMotion.FAST` | 160ms | 遮罩淡入淡出、按钮展开收拢 |
| `IqMotion.EXPAND` | 240ms | `animateContentSize` 卡片展开 |
| `IqMotion.MEDIUM` | 280ms | 抽屉/面板位移、颜色过渡、进度条 |
| `IqMotion.linear` | 1200ms | 顶栏无限扫动进度条 |

按类别分布：

- **按压反馈**：交给 Miuix `Card` / `Surface(onClick)` 自带的 `pressFeedbackType`，
  不再叠加自写缩放（否则会形成"双重按压"）。`Modifier.iqPressScale` 已删除。
- **颜色过渡**：chip / 分段按键组 / 侧栏行用 `animateColorAsState(tween(IqMotion.MEDIUM))`。
- **伸缩**：消息卡、工具组、思考面板、diff 文件卡用 `animateContentSize(tween(IqMotion.EXPAND))`。
- **进场退场**：斜杠面板、附件条、停止按钮用 `AnimatedVisibility`；
  工作区面板切换用 `AnimatedContent`（位移 + 淡入淡出）；窄屏侧栏用 `slideInHorizontally` + 遮罩 `fadeIn`。
- **列表增删**：对话流每项包 `Modifier.animateItem()`，新消息与工作状态条都带插入动画。

## 接入真实引擎

UI 不直接依赖 Mock。把这一处换掉即可：

```kotlin
// state/WorkspaceViewModel.kt
class WorkspaceViewModel(
    private val repo: WorkspaceRepository = MockWorkspaceRepository(),  // ← 换成真实实现
) : ViewModel()
```

需要实现 `WorkspaceRepository` 的成员：

- `sessions()` / `transcript(sessionId)` —— 接 `SessionStore`
- `changes()` —— 接 `git diff`
- `rootFiles()` / `childrenOf()` / `readFile()` —— 接真实文件系统或 `IqDocumentsProvider`
- `terminalBanner()` —— 接 `TermuxTerminalPane` 的输出流（需要改成可增量追加）
- `assistantReply()` / `toolSequence()` —— 换成 `IQCodeEngine` 的流式回调

另外几处 UI 相关的替换点：

- **流式回复**：目前 `runMockTurn()` 用 `delay` 拼接分片；真实引擎应把 `onTextDelta` 之类回调
  转发成对 `WorkspaceUiState.transcript` 的增量更新。
- **授权弹窗**：`requestPermission()` 用 `CompletableDeferred` 挂起等待用户点击，
  真实实现应把 `PermissionGate.PermissionRequest` 映射到 `PermissionRequest`。
- **终端面板**：`TerminalPane` 目前接收静态 `List<TerminalLine>`，接真实 PTY 时改为可滚动的增量缓冲。

## 沙箱调试提示

Compose 在 `dump_ui` 里只会呈现一个 `AndroidComposeView`（无法定位内部节点），
因此验证界面**必须靠截图**：

```bash
iqsandbox install com.iqge.iqcode.compose   # 或 Sandbox action=install
iqsandbox launch  com.iqge.iqcode.compose
iqsandbox screenshot com.iqge.iqcode.compose
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
  app/src/main/java/com/iqge/iqcode/compose | awk -F: '{s+=$2} END {print s}'
```

- 手写绘制站点 **78 → 9**，剩余的是传给 Miuix `Surface(shape = …)` 的 Shape 参数，
  以及 diff 逐行的**语义着色**（`+` 绿 / `−` 红，不是装饰容器）。
- 新增接入的 Miuix 组件：`Card`、`Surface(onClick)`、`IconButton`、`HorizontalDivider`、
  `VerticalDivider`、`SmallTitle`、`LinearProgressIndicator`、`Badge`、`Switch`、
  `Checkbox`、`BasicComponent`、`WindowDialog`、`InputField`、`VerticalScrollBar`、`TooltipBox`。

`Modifier.iqPressScale` / `IqPressable` 已**删除**（零调用点）：Miuix `Card` / `Surface(onClick)`
自带按压反馈，再叠自写缩放会双重触发。
