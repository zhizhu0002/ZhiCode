# 界面层：Miuix 怎么用，以及哪些组件刻意不用

全部界面走 [Miuix](https://github.com/compose-miuix-ui/miuix) **原生主题**（`0.9.4`），
不再有原版 IQ Code 那三套自定义调色板。

本文件收三件事：转发层约定、**组件取舍**（每条都有理由）、以及弹窗上踩过的一批坑。

---

## 1. 唯一的转发层是 `ui/Common.kt`

调用点用 `Zhi*` 包装，**不直接引库** —— 库升级只影响一个文件。`Common.kt` 当前从 Miuix 引入的组件：

| 分类 | 组件 |
| --- | --- |
| 容器与文本 | `Surface`、`Card`、`CardDefaults`、`Text`、`Icon`、`IconButton`、`SmallTitle`、`HorizontalDivider`、`VerticalDivider` |
| 按钮与进度 | `FloatingActionButton`、`FloatingToolbar`、`LinearProgressIndicator`、`InfiniteProgressIndicator`、`ProgressIndicatorDefaults` |
| 输入与选择 | `TextField`、`TextFieldColors`、`TextFieldDefaults`、`CheckboxPreference`、`CheckboxLocation` |
| 页签与菜单 | `TabRowWithContour`、`TabRowDefaults`、`OverlayIconDropdownMenu`、`OverlayDropdownPopup`、`DropdownEntry`、`DropdownItem`、`DropdownDefaults` |
| 主题与反馈 | `MiuixTheme`、`PressFeedbackType` |

**尺寸 / 颜色 / 字阶优先取 token 或 Miuix 主题**：颜色走 `MiuixTheme.colorScheme` / `ZhiColors`，
圆角走 Miuix 默认值或 `ZhiRadius` 的语义例外，字号直接用 `MiuixTheme.textStyles.*`。
主题入口 `zhiTextStyles()`（`theme/ZhiTextStyles.kt`）只在一处保留手机紧凑值 ——
调用点不再维护第二套字号 token，也不写新的裸字号（**原先由 `TypographyScaleTest` 守着，该套件已删**）。

自写的按压缩放（曾经的 `iqPressScale` / `IqPressable`）已**删除**：
Miuix `Card` / `Surface(onClick)` 自带按压反馈，再叠自写缩放会双重触发。

---

## 2. 组件取舍

「不用」的条目在代码里仍可能被 **注释**提到（说明为什么不用），因此不要用 grep 计数来判断是否在用。

| 组件 | 现状 | 理由 |
| --- | --- | --- |
| `WindowDialog` | **不用** | 它会创建**独立 Android Window**，拿不到 `Scaffold` 的 `popupHost`，内部的 `Overlay*` 会失效，也参与不了主窗口的背景模糊。全部弹窗改用 `OverlayDialog`（见 `ui/dialogs/`） |
| `RadioButton` / `RadioButtonPreference` | **仅在 `ui/debug/` 的组件陈列页使用** | 常规选择行用 `CheckboxPreference`（经 `ZhiCheckboxPreference` 转发）；`RadioButton` 未选中时不绘制任何东西，行首只剩一块空白 |
| `TabRow` | **不用** | 选中胶囊向外绘制会盖住相邻内容；改用 `TabRowWithContour`（工程里统一走 `ZhiSegmentedTabs`） |
| `TopAppBar`（大标题版） | **不用** | 大标题布局与紧凑栏差异大；顶栏已改用官方 `SmallTopAppBar`（保留官方 padding 与高度，不再覆写） |
| `InputField` | **仅在侧栏搜索框使用** | 它内部 `defaultMinSize` 下限 45dp，用在文件面板会把列表挤下去；且未展开时是 disabled 的，需要"先展开再聚焦"的配合 |
| `SnackbarHost` / `SnackbarHostState` | **不用** | 浮层会遮挡底部输入器。反馈改为在悬浮输入器**上方**内联显示（`ui/MessageBar.kt`） |

---

## 3. 弹窗踩过的坑（改之前先读）

这些是**按 Miuix 0.9.4 实读 / 实测**记录下来的；升级 Miuix 之后请重新核对一遍。

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 窗口贴在屏幕底部 | `DialogDefaults.isLargeScreen` 按容器尺寸推断，手机判 false → `Alignment.BottomCenter` | 显式传 `largeScreen = true` |
| 窗口又窄又小 | `outsideMargin` + `insideMargin` 叠加后，360dp 屏上内容宽度所剩不多 | 传更小的边距常量（工程内已统一） |
| 标题被强制居中染色 | `DialogContent` 里 `title` / `summary` 硬编码 `TextAlign.Center` + `titleColor` | **不用** `title` 参数，自渲染左对齐标题 |
| 弹窗内卡片「隐身」 | 深色方案下 `background` 与 `surfaceContainerHigh` 同色 | 中间内容区加一层纯色 `Surface` 底板 |
| 底部按钮被挤出屏幕 | 内容区写死 `heightIn(max = …)`，叠加标题/说明后总高超出屏高 | 内容区改 `weight(1f, fill = false)` **吃剩余高度**并内部滚动 |
| 点选项窗口就关了 | 选项回调直接调 `onSelect()`，ViewModel 立即提交并把选择器置空 | 选项点击只改**本地**选中态；「提交」是唯一提交入口，未选时按钮 `enabled = false` |
| 两个按钮观感不统一 | 次按钮曾传 `Color.Transparent` 做纯文字 | 改用 `TextButton` **默认配色** |
| 点开某个弹窗直接闪退 | 表单里套了第二层 `verticalScroll`，测量时抛 `Infinity maximum height constraints` | `DialogShell` 的 body 已带滚动，表单里不得再套（**原先由 `DialogScrollNestingTest` 守着，该套件已删** —— 加表单时请自己确认没有再套一层滚动） |

### 字号

Miuix 组件统一读取官方 `TextStyles` 槽位：`BasicComponent` 的标题走 `headline1`、说明走 `body2`，
`TextField` / `TextButton` / Preference 也走主题样式 —— **逐个传 `fontSize` 改变不了这些内部文字**。

应用在 `AppScaffold` 的 `MiuixTheme(textStyles = zhiTextStyles())` 入口保留手机紧凑字号，
恢复默认只需移除那一处覆盖 —— 恢复路径是单一的。

---

## 4. 下拉菜单的宽度是被内容撑出来的

`OverlayIconDropdownMenu` 的面板宽度由**内容**决定（受库自己的最大文本宽与左右内边距约束），
面板本身**没有宽度参数可调**（`minWidth` 是给触发按钮的）。所以**缩短文案是唯一不偏离库默认的收窄办法**。

输入器页脚的三个 chip（`+` / 权限 / 推理）共用它：`content` 槽位传任意可组合内容，
于是 `+` 放图标、两个 chip 放「文字 + 折叠箭头」，三者在同一行里尺寸一致。
**别换回 `OverlayDropdownPreference`** —— 那是"设置行"尺寸，放进紧凑页脚会撑高整行、把标签挤到折行。

⚠️ `items` 参数必须 `remember`（见 [`performance.md`](performance.md) 第 4 节），否则库内那份缓存永远命不中。

---

## 5. 动画

统一收在 `ui/Animations.kt` 的 `ZhiMotion`，**不要在各处硬编码时长**。
每条都取自 Miuix 上游的动效预设并注明出处，所以新写裸 `tween` 会立刻变成"两套手感"。

| token | 用途 |
| --- | --- |
| `fadeInSpec` / `fadeOutSpec` | 遮罩与淡入淡出（对应上游 `PopupDimEnter` / `PopupDimExit`） |
| `enterSpec` / `exitSpec` | 位移进出场（`folmeSpring` / 同曲线退场） |
| `sizeSpec` | 尺寸变化（展开/收起、`animateContentSize`） |
| `colorSpec` | 颜色过渡 |
| `progressSpec` | 进度类动画 |
| `pressSpec` | 按压反馈 |
| `scaleEnterSpec` / `scaleExitSpec` | 缩放进出场 |

按类别：**按压反馈**交给 Miuix 自带（不叠自写缩放）；**颜色过渡**用 `animateColorAsState`；
**伸缩**用 `animateContentSize`；**进场退场**用 `AnimatedVisibility` / `AnimatedContent`；
**列表增删**用 `Modifier.animateItem()`。

### 热路径上的动画要按状态门控

⚠️ `animateContentSize` 挂在**每 32ms 变化一次**的内容上会被反复重新触发，
整张卡在整个回复期间持续重测量。助手正文的处理见 `ui/chat/MessageCards.kt`：

```kotlin
.then(if (item.streaming) Modifier else Modifier.animateContentSize(animationSpec = ZhiMotion.sizeSpec))
```

同一个文件里记了一条**推翻早期理由**的注释：曾经认为"流式期间必须门控"，
但那个理由本身站不住 —— 门控保留是因为它确实减少了无意义的测量，而不是因为某条原理。
读代码时以注释为准，别照抄旧文档里的因果。

### `Crossfade` 只管 alpha，高度归 `animateContentSize`

两者分工明确，不要用 `Crossfade` 去表达"内容变高了"。
⚠️ `Crossfade` 的 `targetState` 必须是**收敛过的枚举**，不能是布尔组合，更不能是运行中每秒变化的值
（例如 `elapsedMs`），否则动画会被打断重放。

---

## 6. 两条硬约定（都踩过坑）

### 输入框一律走 `ZhiTextField`

（`ui/Common.kt`）它负责两件不加就出错的事：

1. **显式文字色** —— 否则会被弹窗里 `Card` 的 `contentColor` 吃掉，输入的字与底色分不清；
2. **外部改值时把光标放到末尾** —— 否则光标恒在 0，输入 `123` 会变成 `231`。

**这两条现在都没有自动化断言**（原 `TextFieldConventionTest` 已随 `app/tests/` 删除），
改 `ZhiTextField` 时要按上面两点自己核对。

### 长按动作菜单走 `ZhiAnchoredActionMenu`

Miuix 下拉菜单 + 一个**只观察不消费**的指针修饰符拿手指位置，于是菜单从手指处长出来，
同时卡片自己的点击 / 长按与无障碍语义都保留。

⚠️ 观察器一旦**消费**事件，就会顶掉点击与无障碍语义。**这条同样没有自动化断言**
（原 `AnchoredMenuStructureTest` 已删）—— 改这个修饰符时，请手动确认卡片的点击、长按
与无障碍语义都还在。
