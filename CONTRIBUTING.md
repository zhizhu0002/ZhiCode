# 贡献指南

这个工程的维护者不多，所以下面这些约定主要是为了**别把已经踩过的坑再踩一遍**。

## 1. 提交前先跑这些

```bash
git diff --check                            # 先看空白与补丁完整性
./gradlew :app:compileReleaseKotlin --offline   # 编得过再说

bash test-jvm-fast.sh                       # 改到纯逻辑时跑这个（秒级）
```

发版前另外跑：

```bash
./gradlew :app:assembleRelease
```

测试入口只有一条，覆盖面的差别只在跑法：

| 入口 | 覆盖 | 特点 |
| --- | --- | --- |
| `bash test-jvm-fast.sh` | `app/src/test/` 里被列出的那批纯 JVM 单测 | 秒级，只编被测试依赖的那几个文件 |
| `./gradlew :app:testDebugUnitTest` | 全部 JVM 单测 | 分钟级，会走完整 Kotlin / 资源编译 |

> ⚠️ **仓库里不再有「源码文本级结构断言」。** 工程早期有一整套 `app/tests/*.java` +
> `test-source-no-build.sh`，靠 grep 源码字符串钉住约定；它当时**有一部分是红的**，已整套删除。
> 所以：**不要把已经删掉的那条入口写回文档或 CI**，也不要指望「跑一遍测试」能拦住约定类问题 ——
> 现在只能靠下面的约定本身 + review。

## 2. 约定与它们的失败模式（**没有自动化守卫**）

下面这些都是「写错了也不会编译失败」的约定。**它们现在只靠文档与 review 来守** ——
工程早期那套源码文本级断言（`app/tests/*.java`）已经整套删除，不要再引用它。

| 约定 | 违反后的表现 | 为什么值得守 |
| --- | --- | --- |
| 裸 `fontSize = 12.sp`、第二套字阶 | 编译通过，界面「有点不对劲」 | 同一层次的尺寸被逐处手写之后，数字会自己长出近似值 |
| 绕过 `ZhiTextField` 用裸输入框 | 编译通过，文字色看不见 / 光标恒在 0 | 会同时丢掉「可见的文字色」与「正确的光标位置」 |
| 长按菜单的指针观察器**消费**事件 | 编译通过，卡片点不动、无障碍语义消失 | 消费事件会顶掉卡片的点击、长按与无障碍语义 |
| 弹窗宽度与边距写字面量、气泡用强制比例 | 「太宽太窄」「点击误触发」 | 都不会编译失败，只在真机上看出来 |
| 流式期间重解析整篇、行内解析不缓存、流式期间挂尺寸动画 | 每次回复都掉帧 | 不报错，「变慢」没有主人 |
| 超长输出没有行数上限、对整份输出做 O(n) 字符串手术 | 上千行 diff 在**同一个 `LazyColumn` item** 里生成上千个节点 | 懒加载复用彻底失效 |
| 磁盘 IO 在主线程、热路径组件收整份 `WorkspaceUiState`、含 `List` 的类加 `@Immutable` | 几百毫秒够不到 ANR 门槛，只被当成「这应用有点卡」 | 见 [`docs/performance.md`](docs/performance.md) |
| R8 被关掉、JNI 名字绑定的 keep 被删、baseline profile 规则非法 | 只在**运行期**炸（终端起不来、沙箱打不开） | 编译与单测全绿也说明不了 |
| 弹窗内嵌套滚动 | 编译通过，只在测量时抛 `Infinity maximum height constraints` | 点开就闪退 |
| 沙箱层的进程角色 / 隔离 / 启动竞态 / 日志分文件 | 只在真机上表现为「沙箱打不开」 | 不变式见 [`docs/architecture.md`](docs/architecture.md) 第 5 节 |

### 想加回自动化约束，就写成 JVM 单测

`app/src/test/` 下的 JVM 单测是唯一还活着的测试入口。要加新约束时：

1. **写进 `app/src/test/java/`**，并在 `test-jvm-fast.sh` 的 `DEFAULT_TESTS` 里登记（那个脚本是逐条显式调用的，不登记就不会被跑到）；
2. **确认它真的会失败**：把源码改坏 → 必须 FAIL → 还原。顺序很重要 —— 先确认「改坏了会红」，这条断言才算有牙；
   写成 `if` 里恒真的条件、或者被自己的注释喂饱的 `contains`，都会让断言变成装饰品；
3. **纯逻辑才放进 `app/src/test/`**。这些文件刻意不 `import android.*`（`test-jvm-fast.sh` 编它们时**不带 `android.jar`**，带上就编不过），
   所以需要 Android 运行时或真实 View 树的约束，目前没有承载它的地方 —— 这类问题请写进 `docs/` 的约定里，并在 PR 描述里说明你是怎么验证的。

## 3. 不要顺手做的事

- **不要顺手重构**。改动范围贴着你要修的那个问题。仓库里的注释记了大量「为什么不是另一种写法」，
  大规模重排会让那些理由与代码对不上。
- **不要顺手升级依赖**。Miuix `0.9.4` / AGP `9.1.1` / Gradle `9.3.1` / Kotlin `2.4.0` 这条链是**互相约束**的，
  单独动一个会散架（理由见 [`docs/build-and-release.md`](docs/build-and-release.md) 第 1 节）。
- **不要为了省事把注释删掉**。很多注释记的是「这么写会出什么错」，删掉下一个人就会再写一遍错的。
- **不要为了让测试变绿而放宽断言或删测试**。

## 4. 改界面时的硬约定

1. **Miuix 组件只在 `compose/ui/Common.kt` 里转发**：调用点用 `Zhi*` 包装，不直接引库 —— 库升级只影响一个文件。
2. **尺寸 / 颜色 / 字阶取既有 token 或 Miuix 主题**：`ZhiColors`、`ZhiRadius`、`ZhiMotion`、`ZhiMetrics`
   与 `MiuixTheme.textStyles.*`。要加新档位就加到 token 里（并写清理由），不要在调用点写新数字。
   字号覆盖只允许出现在主题入口 `zhiTextStyles()` 一处。

弹窗上有一批**只有真机才看得出来**的坑（贴底、隐身、按钮被挤出屏幕、点一下窗口就关……），
以及几个**刻意不用**的 Miuix 组件。动手前请先读 [`docs/ui-miuix.md`](docs/ui-miuix.md)。

## 5. 改性能相关代码前

[`docs/performance.md`](docs/performance.md) 里每条取舍都写了「不这么做会发生什么」。最容易踩的几条：

- **不要把 `animateContentSize` 挂到流式中的内容上** —— 内容每 32ms 变一次，动画会被反复重新触发；
- **不要在 `LazyColumn` 的单个 item 里渲染上千个节点**（工具输出的默认上限是
  `ToolOutputText.kt` 的 `MaxRenderedLines = 300`）；
- **不要把整份 `WorkspaceUiState` 传给只读几个字段的组件**；
- **不要给含 `List` 的类加 `@Immutable`** —— Kotlin 的 `List` 背后可能是 `ArrayList`，
  这个承诺在类型上核实不了，标上去之后一次原地 `add` 就会让界面静默停更。

## 6. 改构建配置前

R8 规则、Baseline Profile、签名配置都只有**在运行期或发布时**才会暴露问题，见
[`docs/build-and-release.md`](docs/build-and-release.md)。特别是：

- 别用 `-dontoptimize` / `-dontshrink` 之类把 R8 变成空转；
- 往 `baseline-prof.txt` 加规则时，**每条必须带 `H` / `S` / `P` 至少一个 flag**，否则构建直接失败；
- **别把 `Bcore` 的 `minifyEnabled` 关回去**。它开着时如果因为 Missing class 编不过，正确做法是补
  `-dontwarn`，不是关 R8。

## 7. 提交历史（一次已完成的改写）

开源前对历史做过**一次**改写，用 [`tools/rewrite-history-index-filter.sh`](tools/rewrite-history-index-filter.sh)：

1. **统一身份**：把作者与提交者统一为 `zhizhu0002 <zhizhu0002@users.noreply.github.com>`。
2. **去掉 bootstrap 的重复版本**：`app/src/main/assets/bootstrap-aarch64.zip` 很大且是已压缩数据，
   git 做不出有效 delta，历史里每留一版就实打实再占一份。

所以如果你看到「大部分提交都是同一个人」，那是**有意**的，不是伪造多人协作。想自己核对就跑一遍：

```bash
git rev-list --count HEAD                     # 提交总数（会随新提交增长）
git log --format='%an <%ae>' | sort | uniq -c # 作者身份分布
git log --format='%cn <%ce>' | sort | uniq -c # 提交者身份分布
```

> 本文**不抄这些数字**：改写是过去某一次动作，之后每条新提交都会改变它们。要看结论就自己跑上面三行。

也就是说：**改写只覆盖那次改写当时的历史**，之后新增的提交由各自提交者的本地身份决定。如果你本地用的是别的
`user.name/user.email`，历史就会重新出现第二种身份 —— 提交前请确认 `git config user.email`。

⚠️ 改写会改掉全部提交哈希，已经 clone 过的人必须重新 clone。改写脚本的头部写了完整的验证步骤
（含「改写后必须核对 `HEAD^{tree}` 哈希不变」这条要求）。

## 8. 许可

提交即表示你同意你的贡献以本工程的 **MIT** 许可发布（见 [`LICENSE`](LICENSE)）。

如果你的改动**引入新的第三方代码、资源或数据**，请同时更新 [`NOTICE`](NOTICE) 与
[`docs/licensing.md`](docs/licensing.md)：那份文件的规矩是「每条结论都要能被重新核对」，
所以请写清来源、许可、对应文件与核实命令，不要只写结论。

## 9. 报告问题

- 功能缺陷 / 界面问题：开 issue，附上**截图**与复现步骤。
- **安全问题**：不要开公开 issue，见 [`SECURITY.md`](SECURITY.md)。
