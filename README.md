# ZhiCode

一个 **Android 上的编码 Agent**：Kotlin + Jetpack Compose + [Miuix](https://github.com/compose-miuix-ui/miuix)，
应用内自带一套 Termux 环境与一个 Android 虚拟化沙箱，可以直接在手机上跑命令、改代码、装/调试 APK。

包名 `com.zhizhu.code`，应用名 `ZhiCode`。

## 它现在是什么

**不是 UI 原型**。真实引擎、真实环境、真实沙箱都已经接上：

| 能力 | 实现 |
| --- | --- |
| Agent 引擎 | `core/ZhiCodeEngine.java`（流式、工具调用、子代理、steering、视觉消息过滤） |
| 模型接入 | `api/` 下的 OpenAI Responses / Chat Completions / Anthropic 等协议适配，支持自定义 Base URL 与明文 HTTP 开关 |
| 内置终端 | 自带 Termux bootstrap（`RuntimeInstaller` 解压 32 MB）→ 真实 PTY（`TerminalSession` + `libtermux.so`） |
| 虚拟化沙箱 | `Bcore/`（BlackBox 血统）—— 免安装运行 APK、Frida 注入、原生调试 |
| MCP / 技能 / 角色卡 / 记忆 | `McpStore`、`McpRuntime`、`SkillStore`、`RoleCardStore`、`MemoryStore` |
| 会话与任务 | `SessionStore`、`TaskStore`、`PlanStore` |
| 界面 | 全部走 Miuix 组件；语义色与字阶集中在 `ZhiColors` / `ZhiTextScale` / `ZhiRadius` / `ZhiDialogWidth` |

## 明确未做

- **悬浮球、Frida/Debug 图形界面**：调试走 `iqdebug` / Debug 工具，没有独立仪表盘页面。
- **原版 IQ Code 的三套自定义调色板**：这里走 Miuix **原生主题**，跟随系统深浅色。
- **iOS / 桌面端**：只针对 Android。
- 部分原版设置项仍未搬过来（`SettingsRows.kt` 里能看到当前覆盖范围）。

## 构建

```bash
./gradlew :app:assembleDebug --offline
# 产物：app/build/outputs/apk/debug/ZhiCode-debug.apk
```

工具链：Gradle **9.3.1** / AGP **9.1.1** / Kotlin **2.4.0** + compose 插件 2.4.0；
`compileSdk 37` / `minSdk 24` / `targetSdk 28`；Miuix **0.9.4**（`miuix-{ui,core,icons,preference,shader,squircle,blur-android}`）。
`gradle.properties` 里的 `android.suppressUnsupportedCompileSdk=37` 是必须的。

**为什么整条链升到 AGP 9 / Gradle 9**：`BreadcrumbBar` 只在 Miuix 0.9.4 里有，而 0.9.4 拉入
Compose 1.12.0，后者要求 AGP ≥ 9.1.0。升级带来三处必须改的写法：① 不再需要
`org.jetbrains.kotlin.android` 插件（AGP 9 内建 Kotlin 支持，留着会报 `plugin is no longer required`）；
② `android.applicationVariants` 已移除，自定义 APK 输出名改用
`androidComponents { onVariants(…) }`；③ JDK 21 成为必需。

### release 开 R8（混淆 + 资源压缩）

`release` 开着 `minifyEnabled true` + `shrinkResources true`，实测体积
**43,210,837 → 35,911,932 字节（−16.9%）**。本工程有反射与原生 hook，所以规则不是照抄模板 ——
`app/proguard-rules.pro` 里每条规则前面都写了它挡的是什么：

- **JNI 名字绑定**。`app/src/main/jniLibs/arm64-v8a/libtermux.so` 是预编译产物，导出符号写死为
  `Java_com_termux_terminal_JNI_createSubprocess` 这种形式。R8 改名后终端**永远起不来**，报的是
  运行时 `UnsatisfiedLinkError` 而不是编译错误。除了
  `-keepclasseswithmembernames class * { native <methods>; }`，还必须整类 keep
  `com.termux.terminal.JNI` —— 它的方法只被原生侧符号引用，Java 代码里看不到调用者，会被当死代码删掉。
- **注解驱动的反射**。Bcore 的 black-reflection 按 `@BClass` / `@BMethod(name = …)` 反射成员。
  这几条 keep **原先只写在 `Bcore/proguard-rules.pro`**，而那个文件只作用于 Bcore 自己的构建；
  作为库被依赖时传给使用方的是 `consumer-rules.pro`。这是个真实缺口，已补齐。
- **`-dontwarn`**。Bcore 要 hook 的本来就是 `android.jar` 里不存在的类（`ActivityThread`、
  `ServiceManager`、`dalvik.system.*`），R8 报的 "Missing class" 是假警报。

验证方式：开 R8 后重新构建并**在沙箱里实测** —— 应用启动、界面与资源完整、终端出 `bash-5.3$`
并能执行命令（覆盖 `createSubprocess` / `setPtyWindowSize` / `waitFor` / `close`），签名仍是 v2-only。
`app/tests/R8ConfigTest.java` 守着「R8 被关掉」与「非它不可的 keep 被删」这两类问题。

> 保留行号（`-keepattributes SourceFile, LineNumberTable`）是有意的：混淆后的崩溃栈若没有行号，
> 拿到手也定位不了。代价几 KB。

### 只有 Termux / bionic 环境才需要的一处设置

AGP 自带的 aapt2 在 Termux（bionic libc）里跑不起来，必须换成 Termux 的 aapt2。
**这是本机配置、不在仓库里** —— 它是一条绝对路径，提交进去会让别人 clone 后指向不存在的文件：

```bash
# ~/.gradle/gradle.properties
android.aapt2FromMavenOverride=$PREFIX/bin/aapt2
```

也可临时传：`./gradlew :app:assembleDebug -Pandroid.aapt2FromMavenOverride=$PREFIX/bin/aapt2`。
普通 Linux / macOS / Windows 开发机**不需要**这一项。

## 代码结构

```
app/src/main/java/com/zhizhu/zhicode/compose/
  MainActivity.kt                     ComponentActivity → setContent { ZhiCodeApp() }
  theme/                              ZhiColors / ZhiRadius / ZhiMotion / ZhiTextScale（自有紧凑字阶）
  model/UiModels.kt                   ChatItem / ToolActivity / SessionSummary / PermissionMode 等
  data/                               真实数据层（ApiConfig / Mcp / Memory / Session / GitChanges …）
  state/WorkspaceViewModel.kt         StateFlow<WorkspaceUiState> + EngineEvents 实现
  ui/Common.kt                        ★ Miuix 组件的唯一转发层（Zhi* 包装与 token）
  ui/AppScaffold.kt                   Scaffold + 宽窄屏布局 + 弹窗挂载点
  ui/chat/                            对话流、消息卡片、进度卡
  ui/composer/                        输入器与斜杠命令面板
  ui/panes/                           Changes / Terminal / Files 三个面板
  ui/dialogs/                         各弹窗（外壳见 dialogs/DialogShell.kt）
  ui/settings/                        设置页与设置行
```

## 界面层：Miuix 怎么用

**唯一的转发层是 `ui/Common.kt`**：调用点用 `Zhi*` 包装，不直接引库 —— 库升级只影响一个文件。
已接入的组件（按当前代码里的实际 import 列）：`Scaffold`、`Surface`、`Card`、`Text`、`Icon`、
`IconButton`、`Button` / `TextButton`、`SmallTitle`、`HorizontalDivider` / `VerticalDivider`、
`LinearProgressIndicator` / `InfiniteProgressIndicator`、`Badge`、`Switch`、`BasicComponent`、
`TabRowWithContour`、`OverlayDialog`、`OverlayDropdownPopup`、`DropdownEntry` / `DropdownItem`、
`BreadcrumbBar`、`VerticalScrollBar`、`TooltipBox`、`TextField`。

**几处刻意不用**（改动前先看清理由，各自在源码注释里）：

- `WindowDialog` —— 它另开一个 **独立 Android Window**，拿不到 `Scaffold` 的 `popupHost`，
  内部的 `Overlay*` 会失效，也参与不了主窗口的背景模糊。**全部弹窗改用 `OverlayDialog`。**
- `Checkbox` —— 固定 26dp 且是圆形，从外部改不小（`requiredSize` 在调用方 modifier 之后）。
  下拉/选择器里的选中态改用与 Miuix 一致的 `Check` 图标 + `DropdownDefaults.CheckIconSize`。
- `RadioButton` —— **未选中时不绘制任何东西**，行首只剩一块空白。
- `TabRow` —— 选中胶囊向外绘制会盖住相邻内容，改用 `TabRowWithContour`。
- `TopAppBar` —— 大标题布局与原版 48dp 紧凑栏差异大，顶栏用自写单行 `Row`，只复用色板与文字样式。
- `InputField` —— 内部 `defaultMinSize` 下限 45dp，用在文件面板会把列表挤下去。
- `SnackbarHost` —— 浮层遮挡底部输入器，已按需求删除。

自写的按压缩放（曾经的 `iqPressScale` / `IqPressable`）已**删除**：Miuix `Card` / `Surface(onClick)`
自带按压反馈，再叠自写缩放会双重触发。

改造前后对比：手写 `Box + background + clip(RoundedCornerShape)` 的站点 **78 → 9**，剩下的是传给
Miuix `Surface(shape = …)` 的 Shape 参数，以及 diff 逐行的**语义着色**（`+` 绿 / `−` 红，不是装饰容器）。

## 弹窗踩过的坑（改之前先读）

| 现象 | 原因（从 Miuix 0.9.4 字节码实读） | 处理 |
| --- | --- | --- |
| 窗口贴在屏幕底部 | `DialogDefaults.isLargeScreen` 按**容器 width ≥ 840dp 且 height ≥ 480dp** 推断；手机判 false → `Alignment.BottomCenter` | 显式传 `largeScreen = true` |
| 窗口又窄又小 | `outsideMargin` 12dp + `insideMargin` 24dp，360dp 屏上内容宽度只剩 288dp | 传 `DialogWideOutsideMargin` / `DialogWideInsideMargin` |
| 标题被强制居中染色 | `DialogContent` 里 `title` / `summary` 硬编码 `TextAlign.Center` + `titleColor` | **不用** `title` 参数，自渲染左对齐标题 |
| 弹窗内卡片「隐身」 | 深色方案 `background` 与 `surfaceContainerHigh` **都是 `#242424`**，完全同色 | 中间内容区加一层纯黑 `Surface` 底板 |
| 底部按钮被挤出屏幕 | 内容区写死 `heightIn(max = …)`，叠加标题/说明后总高超出屏高 | 内容区改 `weight(1f, fill = false)` **吃剩余高度**并内部滚动 |
| 点选项窗口就关了 | 选项回调直接调 `onSelect()`，ViewModel 立即提交并把 `choicePicker` 置空 | 选项点击只改**本地**选中态；「提交」是唯一提交入口，未选时按钮 `enabled = false` |
| 两个按钮观感不统一 | 次按钮曾传 `color = Color.Transparent` 做纯文字 | 改用 `TextButton` **默认配色** |

字号：Miuix 字号阶梯偏大（`title1=32sp` … `body2=14sp`），且 `BasicComponent` 的标题走 `headline1`、
说明走 `body2`，`TextField` / `TextButton` 也走主题样式 —— **逐个传 `fontSize` 无效**。因此弹窗内嵌
一层 `MiuixTheme(textStyles = compactDialogTextStyles())` 整体收小。

## 动画

统一收在 `ui/Animations.kt` 的 `ZhiMotion`，不要在各处硬编码时长：`FAST` 160ms（遮罩淡入淡出）、
`EXPAND` 240ms（`animateContentSize` 卡片展开）、`MEDIUM` 280ms（抽屉/面板位移、颜色过渡）、
`linear` 1200ms（顶栏无限扫动进度条）。

按类别：**按压反馈**交给 Miuix 自带（不叠自写缩放）；**颜色过渡**用
`animateColorAsState(tween(MEDIUM))`；**伸缩**用 `animateContentSize(tween(EXPAND))`；
**进场退场**用 `AnimatedVisibility` / `AnimatedContent`（窄屏侧栏 `slideInHorizontally` + 遮罩 `fadeIn`）；
**列表增删**用 `Modifier.animateItem()`（注意 `fadeOutSpec = null` 是刻意的，见 `ChatList.kt` 注释）。

## 架构要点

`WorkspaceViewModel` 实现 `EngineEvents`，真实引擎 `ZhiCodeEngine` 在**第一次发消息时**懒创建。
流式回复、工具调用、权限询问、子代理都走它，不再是模拟数据。

⚠️ `WorkspaceRepository` / `MockWorkspaceRepository` **现在只剩终端占位横幅一项用途**，名字容易误导 ——
对话流、工具执行、会话文件都已经不经它了。

- **授权弹窗**：`requestPermission()` 用 `CompletableDeferred` 挂起等待用户点击，真实实现把引擎侧的
  权限请求映射成 `PermissionRequest`。
- **终端面板**：`TerminalPane` 接收 PTY 的增量输出；`TerminalSession` 管理真实会话。

### 两条硬约定（都踩过坑）

- **输入框一律走 `ZhiTextField`**（`compose/ui/Common.kt`）—— 它负责两件不加就出错的事：显式文字色
  （否则会被弹窗里 `Card` 的 `contentColor` 吃掉，输入的字与底色分不清），以及外部改值时把光标放到
  末尾（否则光标恒在 0，输入 `123` 会变成 `231`）。
- **长按动作菜单走 `ZhiAnchoredActionMenu`**：Miuix 下拉菜单 + 一个**只观察不消费**的指针修饰符
  （`zhiObservePointer`）拿手指位置，于是菜单从手指处长出来，同时卡片自己的点击/长按与无障碍语义都保留。

## 沙箱调试提示

Compose 在 `dump_ui` 里只会呈现一个 `AndroidComposeView`（无法定位内部节点），
因此验证界面**必须靠截图**：

```bash
iqsandbox install com.zhizhu.code   # 或 Sandbox action=install
iqsandbox launch  com.zhizhu.code
iqsandbox screenshot com.zhizhu.code
```

`Sandbox action=tap` 的 `x/y` 是 **window 像素**（直接派发 `MotionEvent` 给 decor view），不是截图像素。
本机截图为 688×1452、window 为 1080×2279，换算系数约 **1.5696**。

## 质量守卫

除常规单测外，`test-source-no-build.sh` 里有一组**只读源码的结构测试**（不需要 Android SDK，
`java` 直接跑单文件即可），专门拦「改坏了不会编译失败、只会表现为界面或行为不对」的那类问题：

| 守卫 | 拦什么 |
| --- | --- |
| `TypographyScaleTest` | 裸 `fontSize = N.sp`、第二份字阶、主题未接上字阶 |
| `TextFieldConventionTest` | 绕过 `ZhiTextField` 直接用裸输入框（会丢文字色 / 光标位置） |
| `AnchoredMenuStructureTest` | 长按菜单接线断裂；观察器**消费事件**（会顶掉点击与无障碍语义） |
| `LayoutConsistencyTest` | 弹窗宽度/边距写字面量、气泡用强制比例宽度、触发方式写成点击 |
| `R8ConfigTest` | R8 被关掉、JNI 按名字绑定的 keep 被删、blackreflection 的 consumer 规则漏失 |

```bash
bash test-source-no-build.sh          # 全部结构测试
./gradlew :app:testDebugUnitTest      # JVM 行为测试
```

## 发布与签名

```bash
./gradlew :app:assembleRelease --offline
# 产物：app/build/outputs/apk/release/ZhiCode-release.apk
```

该构建开着 R8；混淆映射表在 `app/build/outputs/mapping/release/mapping.txt`，**发版时要一并留存** ——
没有它，用户报的崩溃栈无法还原成源码位置。

**签名方案：只启用 APK Signature Scheme v2**（v1/v3/v4 都关）。v2 校验的是整个 APK 文件而不是 JAR
条目，能挡住 v1 时代「改一个字节仍通过校验」那类篡改；而 `minSdk 24` 起所有目标设备都支持 v2，
所以 v1 没有必要，留着只会多一份可被旧式攻击面利用的签名。签名信息只保留 `CN=zhizhu0002`
（自签，RSA 4096 / SHA256withRSA，有效期 30 年）。复核：

```bash
$ANDROID_HOME/build-tools/<版本>/apksigner verify --verbose app/build/outputs/apk/release/ZhiCode-release.apk
# Verified using v2 scheme (APK Signature Scheme v2): true
```

### 密钥不进版本库

签名配置从**仓库之外**读取，按优先级：`release.properties`（本机专属，**已 gitignore**）→
环境变量 `ZHICODE_STORE_FILE` / `ZHICODE_STORE_PASSWORD` / `ZHICODE_KEY_ALIAS` /
`ZHICODE_KEY_PASSWORD`（CI 用）。两者都读不到时，`assembleRelease` **仍能构建**，只是产物未签名
并打印一条告警 —— 别人 clone 之后不会因为缺密钥而卡住。

### CI 签名（缺 secret 会直接失败）

`.github/workflows/build.yml` 用的 secret 名与 `ControlLayoutConverter` 一致，两个仓库配一次即可：

| secret | 值 |
| --- | --- |
| `RELEASE_KEYSTORE` | 密钥库的 base64，**单行**：`base64 -w0 ~/.android-keys/zhicode-release.jks` |
| `KEYSTORE_PASSWORD` | `release.properties` 里的 `storePassword` |
| `KEY_ALIAS` | 同上，`keyAlias` |
| `KEY_PASSWORD` | 同上，`keyPassword` |

添加到 **仓库 → Settings → Secrets and variables → Actions → New repository secret**。

**四个缺任何一个，CI 会在开始构建前直接失败**，而不是产出一个「看起来正常、其实没签名」的 release
包。原来是后者：CI 全绿，下载下来的 APK 却没有签名 —— 属于「失败静默通过」，所以改成失败要响。
唯一例外是 **fork 的 PR**（GitHub 不给 fork 传 secret），那种情况只警告，产物名带 `-unsigned` 后缀。

CI 里 release 产物**总是**构建（映射表因此始终有），并额外断言：签名是 v2-only（v1/v3/v4 均为 false）、
签名者 `CN=zhizhu0002`、签名者数量为 1。换一把密钥签出来的包与已发布版本签名不符，用户无法覆盖安装，
这几条断言把那种事故挡在发布之前。

### ⚠️ 本机用环境变量签名：先 `--stop`，否则会静默签不上

签名配置通过环境变量 `ZHICODE_*` 交给 `app/build.gradle`，而 **Gradle 守护进程不会接收客户端新加的
环境变量**。实测（用 init 脚本打印 `System.getenv`）：

| 场景 | `System.getenv("ZHICODE_STORE_FILE")` |
| --- | --- |
| 守护进程先启动（当时没这些变量） | `null` |
| **复用**该守护进程，客户端这次带上变量 | **`null`** ← 变量没传进去 |
| `--no-daemon`（全新进程） | 正常读到 |

后果很隐蔽：构建**成功**，只是产物未签名，而本机没人替你验证。所以本机用环境变量签名时要先

```bash
./gradlew --stop        # 让下一个构建重新拉起守护进程，带上新变量
```

或者直接给那一次构建加 `--no-daemon`。**用 `release.properties`（文件）不受影响** —— 文件是守护进程
自己去读的，这也是本机推荐的做法。

CI 不受这个问题困扰：`ZHICODE_*` 声明在**作业级** `env` 里，本作业第一个 `gradlew` 启动守护进程时
就已经带着它们了（放步骤级就会踩上面那个坑，workflow 里有同样的说明）。

> ⚠️ **密钥库与口令必须单独备份**（放在仓库之外，例如 `~/.android-keys/`）。
> Android 只认签名、不认人：密钥丢了就**再也发不出同一个应用的更新** —— 签名不同的 APK 无法覆盖
> 安装，用户必须先卸载，等于清空他们的数据。

## 许可

本工程自身代码用 **MIT**（[`LICENSE`](LICENSE)）；发行物里含第三方组件（Apache-2.0 组件、Termux
二进制等），逐项清单见 [`NOTICE`](NOTICE)，许可原文见 [`THIRD-PARTY-LICENSES/`](THIRD-PARTY-LICENSES)，
**判断依据与复核方式**见 [`docs/licensing.md`](docs/licensing.md)。

> 「哪些代码是自己写的」在 `docs/licensing.md` 里用**净相同行**做了可核对的说明，并写出了这个数的
> 算法与可能被做手脚的位置。作者的目标是把它降到 0，**当前尚未达到**。

## 上游与致谢

### 特别致谢

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

## 贡献与安全

- 提交代码前请读 [`CONTRIBUTING.md`](CONTRIBUTING.md)（特别是「守卫测试」与「不要顺手重构」两条）。
- **这是一个会执行任意代码的应用**（自带 shell、可在虚拟沙箱里运行 APK）。
  安全模型与漏洞报告方式见 [`SECURITY.md`](SECURITY.md)。
