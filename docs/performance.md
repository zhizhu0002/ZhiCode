# 性能：为什么这样写

目标设备包含 **Android 7 / 8 那一代**（`minSdk 24`，Android 7.0 起）：CPU 慢、闪存慢、GPU 弱。
这不是"顺手优化一下"就能覆盖的场景，所以下面每一条都写清**不这么做会发生什么**。

这些改动的共同点是：**都不会报错、不会崩溃、不会让界面坏掉** —— 只会掉帧、发烫。
所以它们只能靠约定与 review 守，而不是靠"看着还行"。

> ⚠️ **这些约定目前没有自动化防线。** 工程早期那套源码文本级结构断言
> （`app/tests/*.java` + `test-source-no-build.sh`）**已整套删除**，它当时有一部分是红的。
> 所以本文第 1、2 条这类约定的「当前是否满足」**没有任何机器结论** ——
> 只能读代码自己判断，别把它当成已经验证过的事实。

---

## 1. 流式回复：不要每个 delta 重解析整篇

正文每来一个 delta 就变一次（`ZhiEngineController.DELTA_MERGE_MS = 32`，约每秒 31 次）。

如果渲染层写成 `remember(source) { parseMarkdown(source) }`，**整篇累积文本就会每秒被完整重解析 31 次** ——
一条 20 KB 的回复累计是 O(n²)。

现在的做法是按「已完结前缀」切分：只在跨块时才重解析。切点由 `MarkdownParse.settledPrefixLength` 算，
它找的是**不在围栏代码块内部的最后一个空行之后**的位置。

⚠️ 它每次 delta 都会被调用，所以里面**只做下标扫描，不做 `substring` / `split`** ——
否则省下来的分配又花回去了。

行内解析要走 `rememberInline`（`ui/Markdown.kt`）：`inline()` 不是 `@Composable`，
直接在参数位置调用会让**每个段落每次重组**都重跑一遍 `parseInline` + `buildAnnotatedString`。

切点函数本身的行为由 `app/src/test/java/com/zhizhu/zhicode/compose/ui/MarkdownStreamSplitTest.kt`（JVM 单测）守；
而「渲染层有没有绕开它」这类问题**已经没有自动化断言**（原 `MarkdownStreamingTest` 已删），只能靠 review。

---

## 2. 单个 `LazyColumn` item 里不许渲染上千个节点

`DiffLines` 与 `OutputLines`（`ui/chat/ToolOutputText.kt`）是**每一行一个 `Text`**，
而工具卡的输出上限是 40 000 字符（`WorkspaceViewModel.LIVE_OUTPUT_LIMIT = 40_000`）。

1000 行的 diff 就是 1000 个组合 + 1000 个 layout 节点，而且它们**全在同一个 item 里** ——
那个 item 比视口还高，**懒加载的复用彻底失效**（滚动时每帧都要处理全部 1000 行）。

现在两者共用 `limitLines()`：默认最多 `MaxRenderedLines = 300` 行，下面给一行
「还有 N 行 · 点按显示全部」，点开摊全量。

> **数据一个字节都不丢。** 用户是靠这些输出来判断工具到底做了什么，不能因为性能把它们藏没 ——
> 上限只影响**默认渲染**。

行数口径必须与真的画出来的行数一致（换行数 + 1、**保留尾部空串**），否则「还有 N 行」点开后对不上。
`app/src/test/java/com/zhizhu/zhicode/compose/ui/panes/LimitLinesTest.kt` 做守恒断言：显示行数 + 隐藏行数 == 总行数。
**渲染层有没有用上这个上限，目前没有自动化断言**（原 `ToolOutputBoundTest` 已删）—— 改工具输出渲染时请手动核对。

---

## 3. 不要把 O(n) 的字符串手术放在重组路径上

`compactToolSummary` / `firstUsefulErrorLine` / `ToolText.isFileDiff` 原来的写法是
`output.trim()` → `split('\n')`，而它们的结果其实只有三个数：去掉首尾空白后的边界（两个下标）、
行数（数换行符）、以及单行且不超长时的那段文本。

而它们在**每次重组**都会被调用（滚动、状态变化、展开/收起都算）。全部改成**下标扫描**；
`isFileDiff` 还能提前 `return`（`@@` 头通常在开头，原来却要先把整份输出切完才去找它）。

`ToolRow` 里那两个 O(输出长度) 的计算已移进 `remember`。⚠️ **key 不能是 `activity` 整体**：
它带 `elapsedMs`，运行中每秒都在变，那样等于每秒白算一次。

---

## 4. `items` 必须 `remember`（下拉菜单）

`ZhiIconDropdownMenu`（`ui/Common.kt`）内部按 `remember(items, scheme.primary)` 缓存整份条目
（含每个条目的 icon 可组合 lambda）。

而写在参数位置上的 `listOf(…)` / `entries.map { … }` **每次重组都是新实例** —— 身份不等，
那份缓存永远命不中，等于没做。输入器随 `state.composerText` **每敲一个字**重组一次，账单按字符数付。
所以三个调用点（`+` 菜单、权限 chip、推理 chip）都已包进 `remember`。

---

## 5. 主线程上不做磁盘 IO

技能文件是手写的参考文档，几百 KB 很常见；Android 7/8 那代闪存更慢。压在点击那一帧上就是一次肉眼可见的卡顿 ——
而几百毫秒**够不到 ANR 门槛**，所以只会被当成"这应用有点卡"。

约定就是 `viewModelScope.launch(Dispatchers.IO)`（`WorkspaceViewModel` 里目前有 50 多处用到 `Dispatchers.IO`），
技能文件的保存 / 删除 / 编辑都在其中。

⚠️ `saveSkillFile` 的**存在性检查与写入必须留在同一个 IO 块里**：拆成两块会多出一个
「查过了但还没写」的窗口，双击能建出两份同名文件。

---

## 6. 热路径组件不要把整份 UiState 收进来

`TopBarState` 只读六项（`composerBusy` / `modelLabel` / `contextTokens` / `contextWindow` /
`deviceStatus` / `tab`），`SidebarState` 只读四项 —— 而它们原来收的是**整份 `WorkspaceUiState`**，
里面带着对话流与全部工具输出。

流式期间 `_state` 每 32ms 换一次新实例，于是这两个组件：

1. 每次都要重组（虽然一个像素都不会变）；
2. 判等本身也要钱 —— `List.equals` 是**逐个元素**比的，比到那条正在流式的消息才会停下。

两者都在各自文件里带 `from(state)` 用来收窄。

### `@Immutable` 只在能核对的时候才加

`TopBarState` 带 `@Immutable` —— 它全是 `String` / `Int` / `Boolean` / enum，**没有 List、没有可变对象**，
所以这个承诺是**可核对的**。

⚠️ `SidebarState` **故意不带**：它含 `List<SessionSummary>`，而 Kotlin 的 `List` 只是只读视图、
背后完全可能是 `ArrayList`。标成不可变等于向编译器保证一件在类型上核实不了的事 ——
一旦哪里原地 `add` 一下，界面会**静默停更**（最难查的一类 bug），而收益只是省掉一次判等。

同理 **`WorkspaceUiState` 本身没有加 `@Immutable`**（约 70 个字段、9 个 `List`）。**这是刻意的决定**：
能量化的地方（顶栏 / 侧栏收窄）就收窄，能量不到的地方不假装。
⚠️ 原来有一条 `MainThreadIoBoundTest` 把这一点钉住，**它已随整套源码文本级断言删除** ——
所以「以后别顺手给 `WorkspaceUiState` 补 `@Immutable`」这句话现在只有本文件在说。

---

## 7. 列表自动贴底：不要用挂起动画

`animateScrollToItem` 是**挂起**的，而 effect 的 key 是内容长度 —— 内容每变一次就取消重启，
滚动常常在真正生效之前就被取消掉，表现为「滚一下停一下」。

改用 `requestScrollToItem`：**非挂起**，只登记一个目标下标，在**下一次测量**里生效，取消不掉。
对话流（`ui/chat/ChatList.kt`）与终端面板（`ui/panes/TerminalPane.kt`）都走这条，
两处都留了注释说明为什么不能用挂起版本。

---

## 8. 图片与超长正文

图片链路在 `ui/ZhiImage.kt`：先用 `BitmapFactory.Options.inJustDecodeBounds` 读原图尺寸，
再按显示尺寸用 `sampleSizeFor()` 选 `inSampleSize`（与官方"高效加载大图"一致），解码走 `BitmapFactory`
并放在 `Dispatchers` 的工作线程上；解码结果进一个有界 `LruCache`（8 MB）。
所以高清截图不会按原始像素直接塞进主线程，也不会因为 `LazyColumn` 回收而每次重新解码。

助手正文超过 `MaxInitialMarkdownChars`（`ui/chat/MessageCards.kt` 里 = 120 000）时先渲染有界前缀，
用户主动点击后才展开完整正文 —— 避免一条异常长回复在首帧同时触发完整 Markdown 解析和巨大测量。
工具 diff / 输出继续用 `MaxRenderedLines` 的行数上限，完整内容仍保留在模型里。

流式消息不再对每个中间高度启动 `animateContentSize`（见 [`ui-miuix.md`](ui-miuix.md) 第 5 节）；
消息定稿后的展开 / 收起仍保留尺寸动画。

---

## 9. 构建期的性能工作

见 [`build-and-release.md`](build-and-release.md)：

- `app` 与 `Bcore` 的 release 都开 R8（体积请自己量，文档不写死数字）；
- `app/src/main/baseline-prof.txt` 手写冷启动路径 + `androidx.profileinstaller` 装机
  （`minSdk 24`，而 Android 8.0/8.1（API 26/27）没有系统级 profile 安装流程，正是最需要它的档位）；
- `gradle.properties` 开守护进程、并行、构建缓存、configuration cache，并把 worker 数限制为 2
  （移动设备上无界并发会导致内存抖动）；
- Kotlin / AGP 编译没有可安全开启的"GPU 加速"开关；构建加速主要来自缓存、配置缓存、守护进程与受控并行。

---

## 10. 改完怎么验

```bash
git diff --check                              # 空白与补丁完整性
bash test-jvm-fast.sh                         # 纯 JVM 单测（含 LimitLinesTest 等）
./gradlew :app:compileReleaseKotlin --offline # 编得过
./gradlew :app:assembleRelease --offline      # 发布前
```

性能类改动是**唯一一类**「编得过、单测全绿也不能说明没坏」的改动 —— 而且本文提到的那些反模式
**现在一条自动化断言都没有**（原 `MarkdownStreamingTest` / `ToolOutputBoundTest` / `MainThreadIoBoundTest`
已随源码文本级断言套件删除）：

- **没有机器能替你发现**「又开始每 32ms 重解析整篇」或「又在同一个 `LazyColumn` item 里渲染上千个节点」；
- 真正的观感只能靠**在低端设备或 IQ 沙箱里滚一遍长对话 / 长工具输出**。

所以性能改动的验证责任整个落在改动者身上：**发行前请至少手工滚一遍**，并在 PR 描述里写清你测了什么。
