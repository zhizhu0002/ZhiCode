# 性能：为什么这样写（Android 8 起要流畅）

目标设备包含 **Android 8（API 26）那一代**：CPU 慢、闪存慢、GPU 弱。
这不是"顺手优化一下"就能覆盖的场景，所以下面每一条都写清**不这么做会发生什么**。

这些改动的共同点是：**都不会报错、不会崩溃、不会让界面坏掉** ——
只会掉帧、发烫、或者每个月多花几 MB。所以它们全部由守卫测试与 JVM 单测钉住，
而不是靠"看着还行"。

---

## 1. 流式回复：不要每个 delta 重解析整篇

正文每来一个 delta 就变一次（`ZhiEngineController.DELTA_MERGE_MS = 32`，约每秒 31 次）。

原来的渲染层是：

```kotlin
val blocks = remember(source) { parseMarkdown(source) }
```

于是**整篇累积文本每秒被完整重解析 31 次** —— 一条 20 KB 的回复累计是 O(n²)。

现在按「已完结前缀」切分：只在跨块时才重解析（一条回复里几十次）。
切点由 `MarkdownParse.settledPrefixLength` 算 —— 找**不在围栏代码块内部的最后一个空行之后**的位置。

⚠️ 它每次 delta 都会被调用，所以里面**只做下标扫描，不做 `substring` / `split`**
（否则省下来的分配又花回去了）。

同时行内解析要走 `rememberInline`：`inline()` 非 `@Composable`，内部跑 `parseInline` +
`buildAnnotatedString`，直接在参数位置调用会让**每个段落每次重组**都重跑一遍。

**守住**：`MarkdownStreamingTest` + `MarkdownStreamSplitTest`（13 条边界：围栏、CRLF、
空行、尾部只有空白）。

---

## 2. 单个 `LazyColumn` item 里不许渲染上千个节点

`DiffLines` 是**每一行一个 `Text`**，而工具卡展开的输出最长 40 000 字符
（`WorkspaceViewModel.LIVE_OUTPUT_LIMIT`）。

1000 行的 diff 就是 1000 个组合 + 1000 个 layout 节点，而且它们**全在同一个 item 里** ——
那个 item 比视口还高，**懒加载的复用彻底失效**（滚动时每帧都要处理全部 1000 行）。

现在 `DiffLines` 与 `OutputLines` 共用 `boundedTextLines`：默认最多 `MaxRenderedLines = 300` 行，
下面给一行「还有 N 行 · 点按显示全部」，点开摊全量。

> **数据一个字节都不丢。** 用户是靠这些输出来判断工具到底做了什么，不能因为性能把它们藏没 ——
> 上限只影响**默认渲染**。

行数的口径就是 `split('\n')` 的分段数（换行数 + 1，**保留尾部空串**）—— 必须与 `DiffLines`
真的画出来的行数一致，否则「还有 N 行」点开后对不上。

**守住**：`ToolOutputBoundTest` + `LimitLinesTest`（12 条，含守恒：显示行数 + 隐藏行数 == 总行数）。

---

## 3. 不要把 O(n) 的字符串手术放在重组路径上

`compactToolSummary` / `firstUsefulErrorLine` / `ToolText.isFileDiff` 原来的写法是
`output.trim()` → `split('\n')`，而它们的结果只有三个数：

- 去掉首尾空白后的边界（两个下标）
- 行数（数换行符）
- 单行且不超 96 字符时的那段文本（一行，很短）

而它们在**每次重组**都会被调用（滚动、状态变化、展开/收起都算），输出上限 40 000 字符
—— trim 复制整份文本，split 再建出所有行的数组。

全部改成**下标扫描**。`isFileDiff` 还能提前 `return`：`@@` 头通常在开头，
原来却要先把整份输出切完才去找它。

`ToolRow` 里那两个 O(输出长度) 的计算也移进了 `remember`。
⚠️ **key 不能是 `activity` 整体**：它带 `elapsedMs`，运行中每秒都在变，那样等于每秒白算一次。

**守住**：`ToolOutputBoundTest`。

---

## 4. `items` 必须 remember（下拉菜单）

`ZhiIconDropdownMenu` 内部按 `remember(items, scheme.primary)` 缓存整份 `DropdownEntry`
（含每个条目的 icon 可组合 lambda）。

而写在参数位置上的 `listOf(…)` / `entries.map { … }` **每次重组都是新实例** ——
身份不等，那份缓存永远命不中，等于没做。输入器随 `state.composerText` **每敲一个字**重组一次，
账单按字符数付。

三个调用点（`+` 菜单、权限 chip、推理 chip）都已包进 `remember`。

**守住**：`ToolOutputBoundTest`。

---

## 5. 主线程上不做磁盘 IO

技能文件是手写的参考文档，**几百 KB 很常见**；Android 8 那代闪存更慢。
压在点击那一帧上就是一次肉眼可见的卡顿 —— 而几百毫秒**够不到 ANR 门槛**，
所以只会被当成"这应用有点卡"。

四处已下到 `Dispatchers.IO`：`saveSkillFile` / `deleteSkillFile` / `editSkill` / `saveSkill`。
既有约定就是 `viewModelScope.launch(Dispatchers.IO)`（`initSessionState` 等十几处都这么写）。

⚠️ `saveSkillFile` 的**存在性检查与写入必须留在同一个 IO 块里**：拆成两块会多出一个
「查过了但还没写」的窗口，双击能建出两份同名文件。

**守住**：`MainThreadIoBoundTest`（断言的是**执行顺序** —— `launch(Dispatchers.IO)` 必须出现在
`SkillStore` 调用**之前**，而不是"文件里出现过 `Dispatchers.IO`"）。

---

## 6. 热路径组件不要把整份 UiState 收进来

`ZhiTopBar` 只读六项（`composerBusy` / `modelLabel` / `contextTokens` / `contextWindow` /
`deviceStatus` / `tab`），`ZhiSidebar` 只读四项 —— 而它们原来收的是**整份 `WorkspaceUiState`**，
里面带着**对话流与全部工具输出**。

流式期间 `_state` 每 32ms 换一次新实例，于是这两个组件：

1. 每次都要重组（虽然一个像素都不会变）；
2. 判等本身也要钱 —— `List.equals` 是**逐个元素**比的，比到那条正在流式的消息才会停下，
   也就是说前面每一条消息的所有字段都被逐个走过。

所以新增了 `TopBarState` / `SidebarState`（各带 `from(state)`）。

### `@Immutable` 只在能核对的时候才加

`TopBarState` 带 `@Immutable` —— 它全是 `String` / `Int` / `Boolean` / enum，
**没有 List、没有可变对象**，所以这个承诺是**可核对的**。

⚠️ `SidebarState` **故意不带**：它含 `List<SessionSummary>`，而 Kotlin 的 `List` 只是只读视图、
背后完全可能是 `ArrayList`。标成不可变等于向编译器保证一件在类型上核实不了的事 ——
一旦哪里原地 `add` 一下，界面会**静默停更**（最难查的一类 bug），而收益只是省掉一次判等。

同理 **`WorkspaceUiState` 本身没有加 `@Immutable`**（五十多个字段、十几个 `List`）。
**这是刻意的决定**：能量化的地方（顶栏 / 侧栏收窄）就收窄，能量不到的地方不假装。
`MainThreadIoBoundTest` 里写明了这一点，防止以后有人"顺手补上"。

---

## 7. 列表自动贴底：不要用挂起动画

`animateScrollToItem` 是**挂起**的，而 effect 的 key 是内容长度 —— 内容每变一次就取消重启，
滚动常常在真正生效之前就被取消掉，表现为「滚一下停一下」。

改用 `requestScrollToItem`：**非挂起**，只登记一个目标下标，在**下一次测量**里生效，取消不掉。

对话流（`ui/chat/ChatList.kt`）与终端面板（`ui/panes/TerminalPane.kt`）都走这条。
对话流那个还改成了**长驻** effect + `snapshotFlow` 观察内容指纹，而不是把长度当 `LaunchedEffect`
的 key —— 后者每个 token 都要重建一次协程。

**守住**：`MainThreadIoBoundTest`（终端那处）。

---

## 8. 构建期的性能工作

见 [`build-and-release.md`](build-and-release.md)：

- `app` 与 `Bcore` 的 release 都开 R8（体积 43,210,837 → 35,911,932 字节是 app 那一档的贡献）；
- `app/src/main/baseline-prof.txt` 手写冷启动路径 + `androidx.profileinstaller` 装机
  （`minSdk 24`，而 API 26~27 没有系统级 profile 安装流程，正是最需要它的档位）。

---

## 9. 这些约束怎么被守住

| 机制 | 覆盖 |
| --- | --- |
| `MarkdownStreamingTest`（源码结构） | 流式切分、行内缓存、流式期间不挂尺寸动画 |
| `MarkdownStreamSplitTest`（JVM 单测） | 切点函数的行为与边界 |
| `ToolOutputBoundTest`（源码结构） | 行数上限、不做 O(n) 字符串手术、`items` 已缓存 |
| `LimitLinesTest`（JVM 单测） | 截断规则的行为、口径、守恒 |
| `MainThreadIoBoundTest`（源码结构） | IO 线程顺序、组件收窄、`@Immutable` 自洽、贴底不挂起 |
| `R8ConfigTest`（源码结构） | R8 配置、keep 规则、baseline profile 合法性 |

```bash
bash test-source-no-build.sh          # 全部结构测试
./gradlew :app:testDebugUnitTest      # JVM 行为测试
```

> 判断标准很简单：**如果一处改动「写错了也不会编译失败」，那它需要的是守卫或单测**，
> 而不是指望 review 时有人看见。
