# 界面层：Miuix 怎么用，以及哪些组件刻意不用

本工程的全部界面走 [Miuix](https://github.com/compose-miuix-ui/miuix) **原生主题**，
不再有原版 IQ Code 那三套自定义调色板。

本文件收三件事：转发层约定、**刻意不用的组件**（每条都有理由，改之前先读）、
以及弹窗上踩过的一批坑（现象 → 字节码层面读到的原因 → 处理）。

---

## 1. 唯一的转发层是 `ui/Common.kt`

调用点用 `Zhi*` 包装，**不直接引库** —— 库升级只影响一个文件。

已接入的组件（按当前代码里的实际 import 列）：`Scaffold`、`Surface`、`Card`、`Text`、`Icon`、
`IconButton`、`Button` / `TextButton`、`SmallTitle`、`HorizontalDivider` / `VerticalDivider`、
`LinearProgressIndicator` / `InfiniteProgressIndicator`、`Badge`、`Switch`、`BasicComponent`、
`TabRowWithContour`、`OverlayDialog`、`OverlayDropdownPopup`、`DropdownEntry` / `DropdownItem`、
`BreadcrumbBar`、`VerticalScrollBar`、`TooltipBox`、`TextField`。

**尺寸 / 颜色 / 字阶一律取自 token**：`ZhiColors`、`ZhiRadius`、`ZhiTextScale`、`ZhiDialogWidth`、
`ZhiMotion`、`ZhiSpace`。要加新档位就加到 token 里（并写清理由），不要在调用点写新数字 ——
`TypographyScaleTest` 会拦住裸 `fontSize = N.sp`。

自写的按压缩放（曾经的 `iqPressScale` / `IqPressable`）已**删除**：Miuix `Card` / `Surface(onClick)`
自带按压反馈，再叠自写缩放会双重触发。

改造前后对比：手写 `Box + background + clip(RoundedCornerShape)` 的站点 **78 → 9**，
剩下的是传给 Miuix `Surface(shape = …)` 的 Shape 参数，以及 diff 逐行的**语义着色**
（`+` 绿 / `−` 红，不是装饰容器）。

---

## 2. 刻意不用的组件（每条都有理由）

| 组件 | 为什么不用 |
| --- | --- |
| `WindowDialog` | 它另开一个**独立 Android Window**，拿不到 `Scaffold` 的 `popupHost`，内部的 `Overlay*` 会失效，也参与不了主窗口的背景模糊。**全部弹窗改用 `OverlayDialog`。** |
| `Checkbox` | 固定 26dp 且是圆形，从外部改不小（`requiredSize` 施加在调用方 modifier 之后）。下拉/选择器里的选中态改用与 Miuix 一致的 `Check` 图标 + `DropdownDefaults.CheckIconSize`。 |
| `RadioButton` | **未选中时不绘制任何东西**，行首只剩一块空白。 |
| `TabRow` | 选中胶囊向外绘制会盖住相邻内容，改用 `TabRowWithContour`。 |
| `TopAppBar` | 大标题布局与原版 48dp 紧凑栏差异大。不过顶栏现在已改用官方 `SmallTopAppBar`（保留官方 padding 与 50dp 高度，不再覆写）。 |
| `InputField` | 内部 `defaultMinSize` 下限 45dp，用在文件面板会把列表挤下去。 |
| `SnackbarHost` | 浮层遮挡底部输入器，已按需求删除。反馈改为在悬浮输入器**上方**内联显示（见 `ui/MessageBar.kt`）。 |

---

## 3. 弹窗踩过的坑（改之前先读）

| 现象 | 原因（从 Miuix 0.9.4 字节码实读） | 处理 |
| --- | --- | --- |
| 窗口贴在屏幕底部 | `DialogDefaults.isLargeScreen` 按**容器 width ≥ 840dp 且 height ≥ 480dp** 推断；手机判 false → `Alignment.BottomCenter` | 显式传 `largeScreen = true` |
| 窗口又窄又小 | `outsideMargin` 12dp + `insideMargin` 24dp，360dp 屏上内容宽度只剩 288dp | 传 `DialogWideOutsideMargin` / `DialogWideInsideMargin` |
| 标题被强制居中染色 | `DialogContent` 里 `title` / `summary` 硬编码 `TextAlign.Center` + `titleColor` | **不用** `title` 参数，自渲染左对齐标题 |
| 弹窗内卡片「隐身」 | 深色方案 `background` 与 `surfaceContainerHigh` **都是 `#242424`**，完全同色 | 中间内容区加一层纯黑 `Surface` 底板 |
| 底部按钮被挤出屏幕 | 内容区写死 `heightIn(max = …)`，叠加标题/说明后总高超出屏高 | 内容区改 `weight(1f, fill = false)` **吃剩余高度**并内部滚动 |
| 点选项窗口就关了 | 选项回调直接调 `onSelect()`，ViewModel 立即提交并把 `choicePicker` 置空 | 选项点击只改**本地**选中态；「提交」是唯一提交入口，未选时按钮 `enabled = false` |
| 两个按钮观感不统一 | 次按钮曾传 `color = Color.Transparent` 做纯文字 | 改用 `TextButton` **默认配色** |
| 点开某个弹窗直接闪退 | 表单里套了第二层 `verticalScroll`，测量时抛 `Infinity maximum height constraints` | `DialogShell` 的 body 已带滚动，表单里不得再套（`DialogScrollNestingTest` 守着） |

### 字号

Miuix 字号阶梯偏大（`title1=32sp` … `body2=14sp`），且 `BasicComponent` 的标题走 `headline1`、
说明走 `body2`，`TextField` / `TextButton` 也走主题样式 —— **逐个传 `fontSize` 无效**。

因此弹窗内嵌一层 `MiuixTheme(textStyles = compactDialogTextStyles())` 整体收小。

---

## 4. 下拉菜单的宽度是被内容撑出来的

`OverlayIconDropdownMenu` 用 `DropdownDefaults.MaxItemTextWidth`（实测 216dp）限行内容宽，
再加左右各 `InsideHorizontalPadding`（20dp）—— 所以**一条长文案就能把面板撑到约 256dp**，
在本机 411dp 宽的设备上是 62%。

面板本身**没有宽度参数可调**（`minWidth` 是给触发按钮的），所以**缩短文案是唯一不偏离库默认的
收窄办法**。

输入器页脚的三个 chip（`+` / 权限 / 推理）共用它：`content` 槽位传任意可组合内容，
于是 `+` 放图标、两个 chip 放「文字 + 折叠箭头」，三者在同一行里尺寸一致。
**别换回 `OverlayDropdownPreference`** —— 那是 16sp / 40dp 的"设置行"，放进 34dp 的页脚会撑高整行、
把标签挤到折行。

---

## 5. 动画

统一收在 `ui/Animations.kt` 的 `ZhiMotion`，**不要在各处硬编码时长**。
每条都逐字抄自 Miuix 上游并注明出处（`PopupDimEnter` / `PopupDimExit` 等），
所以新写裸 `tween` 会立刻变成"两套手感"。

| token | 值 | 对应上游 |
| --- | --- | --- |
| `fadeInSpec` | `tween(300, SinOutEasing)` | `PopupDimEnter` |
| `fadeOutSpec` | `tween(150, SinOutEasing)` | `PopupDimExit` |
| `exitSpec` / `sizeSpec` | `tween(200, DecelerateEasing(1.5f))` | |
| `enterSpec` | `folmeSpring(0.9f, 0.3f)` | |
| `colorSpec` | 同 `fadeOutSpec` | |
| `progressSpec` | `folmeSpring(1.0f, 0.3f)` | |
| `pressSpec` | `folmeSpring(0.95f, 0.35f)` | |
| `scaleEnterSpec` / `scaleExitSpec` | | |

按类别：**按压反馈**交给 Miuix 自带（不叠自写缩放）；**颜色过渡**用 `animateColorAsState`；
**伸缩**用 `animateContentSize`；**进场退场**用 `AnimatedVisibility` / `AnimatedContent`；
**列表增删**用 `Modifier.animateItem()`（注意 `fadeOutSpec = null` 是刻意的，见 `ChatList.kt` 注释）。

### 热路径上的动画要按状态门控

⚠️ `animateContentSize` 挂在**每 32ms 变化一次**的内容上会被反复重新触发，整张卡在整个回复期间
持续重测量。所以三处都按状态分写：

- `AssistantCard`：`if (item.streaming) Modifier else Modifier.animateContentSize(…)`
- `ToolGroupCard`：按 `item.groupCompleted`
- `ToolRow`：按 `activity.completed`

`MarkdownStreamingTest` 守着这三处。

### `Crossfade` 只管 alpha，高度归 `animateContentSize`

两者分工明确，不要用 `Crossfade` 去表达"内容变高了"。

⚠️ `Crossfade` 的 `targetState` 必须是**收敛过的枚举**，不能是布尔组合，更不能是运行中每秒变化的
值（例如 `elapsedMs`）—— 否则动画会被打断重放。

---

## 6. 两条硬约定（都踩过坑）

### 输入框一律走 `ZhiTextField`

（`compose/ui/Common.kt`）它负责两件不加就出错的事：

1. **显式文字色** —— 否则会被弹窗里 `Card` 的 `contentColor` 吃掉，输入的字与底色分不清；
2. **外部改值时把光标放到末尾** —— 否则光标恒在 0，输入 `123` 会变成 `231`。

`TextFieldConventionTest` 守着。

### 长按动作菜单走 `ZhiAnchoredActionMenu`

Miuix 下拉菜单 + 一个**只观察不消费**的指针修饰符（`zhiObservePointer`）拿手指位置，
于是菜单从手指处长出来，同时卡片自己的点击 / 长按与无障碍语义都保留。

⚠️ 观察器一旦**消费**事件，就会顶掉点击与无障碍语义。`AnchoredMenuStructureTest` 守着。
