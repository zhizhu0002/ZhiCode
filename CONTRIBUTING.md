# 贡献指南

感谢愿意帮忙。这个工程的作者不多，所以下面这些约定主要是为了**别把已经踩过的坑再踩一遍**。

## 先跑这两个

```bash
# 结构测试（只读源码，不需要 Android SDK，几秒钟）
bash test-source-no-build.sh

# 单元测试
./gradlew :app:testDebugUnitTest
```

提交前两个都应该是绿的。如果你的改动让某个守卫测试红了，**先看它拦的是什么** ——
这些守卫的每一条都对应一个真实发生过的缺陷，报错信息里写了原因。

## 守卫测试拦的那些事

`app/tests/` 下的结构测试是**故意的**，不要为了让它们通过而放宽断言：

| 守卫 | 它拦的缺陷 | 为什么值得拦 |
| --- | --- | --- |
| `TypographyScaleTest` | 裸 `fontSize = 12.sp`、第二份字阶 | 同一层次的尺寸被逐处手写，数字会自己长出近似值 |
| `TextFieldConventionTest` | 绕过 `ZhiTextField` 用裸输入框 | 会同时丢掉「可见的文字色」与「正确的光标位置」，且不报错 |
| `AnchoredMenuStructureTest` | 长按菜单接线断裂 / 观察器消费事件 | 消费事件会顶掉卡片的点击、长按与无障碍语义 |
| `LayoutConsistencyTest` | 宽度写字面量、气泡用强制比例、触发方式写成点击 | 「太宽太窄」与「点击误触发」都不会编译失败 |
| `MarkdownStreamingTest` | 流式每 32ms 重解析整篇、行内解析不缓存、流式期间挂尺寸动画 | 不报错，只是每次回复都掉帧；"变慢"没有主人 |
| `ToolOutputBoundTest` | 超长输出没有行数上限、对整份输出做 O(n) 字符串手术、下拉 `items` 未缓存 | 上千行的 diff 会在**同一个 LazyColumn item** 里生成上千个节点，懒加载复用彻底失效 |
| `MainThreadIoBoundTest` | 磁盘 IO 在主线程、热路径组件收整份 `WorkspaceUiState`、`@Immutable` 与字段类型不自洽 | 几百毫秒够不到 ANR 门槛，只会被当成"这应用有点卡" |
| `R8ConfigTest` | R8 被关掉、JNI 名字绑定的 keep 被删、baseline profile 规则非法 | 只在**运行期**炸（终端起不来、沙箱打不开），编译与单测全绿 |
| `DialogScrollNestingTest` | 弹窗内嵌套滚动 | 编译通过，只有测量时才抛 `Infinity maximum height constraints` |

确实需要例外时，**加进白名单并写明理由**，不要改断言本身。

### 加了新的「不会编译失败」的约束，就配一条守卫

这类约束（性能、约定、配置）的共同点是**错了没人报错**，所以它们的正确性只能靠断言。
加守卫时请一并做两件事：

1. **在 `test-source-no-build.sh` 里注册**（守卫自己会断言这一条，漏了它就红）；
2. **逐条反向验证**：把源码改坏 → 必须 FAIL → 还原。
   顺序很重要 —— 先确认"改坏了会红"，这条断言才算真的有牙。
   写成 `if` 里恒真的条件、或者被自己的注释喂饱的 `contains`，都会让守卫变成装饰品。

## 关于提交历史（一次已完成的改写）

开源前对历史做过**一次**改写，两件事一起做：

1. **统一身份**：全部 136 条提交的作者与提交者统一为
   `zhizhu0002 <zhizhu0002@users.noreply.github.com>`。
   改写前存在 5 种身份（其中 87 条是 `IQ Code Agent <agent@iqge.local>`）。
2. **去掉 bootstrap 的重复版本**：`app/src/main/assets/bootstrap-aarch64.zip`
   约 33MB 且是压缩数据、git 做不出有效 delta，历史里每留一版就实打实再占一份。
   历史上一共有 **4 个**版本，把其中 2 个统一到当前版本后：
   `.git` **68M → 37M**（省约 31MB），**提交数不变（136）**。

所以如果你看到：

- 所有提交都是同一个人 —— 这是有意的，不是伪造多人协作；
- 那个 zip 在历史大多数提交里内容完全一样 —— 也是有意去重。

**但不是历史上每一版都变成了同一份**：改写只合并了 2 个版本，另一个早期版本
（`4cb25af`，33,119,132 字节，2026-09-26 的 8 条提交使用）**原样保留** ——
它是历史上最早的**真实** bootstrap，且在 pack 里已被存成 delta、实际只占约 2.3MB，
为这点空间抹掉一个真实历史版本不划算。

> 这个脚本最初把 `4cb25af` 误判为「94KB 占位文件」，原因是**误读了
> `git verify-pack -v` 的 size 列** —— 对 deltified 对象，那一列给的是 delta 的大小，
> 不是对象本身的大小。判断大小请一律用 `git cat-file -s <sha>`。
> 详情写在 `tools/rewrite-history-index-filter.sh` 的头部。

改写脚本与完整的验证步骤见 [`tools/rewrite-history-index-filter.sh`](tools/rewrite-history-index-filter.sh)
（含「改写后必须核对 `HEAD^{tree}` 哈希不变」这条要求）。

⚠️ **改写会改掉全部提交哈希。** 已经 clone 过的人再拉取会冲突，
必须重新 clone。仓库内 `git config user.name/user.email` 也一并设成了统一身份 ——
如果你本地用别的身份提交，历史会重新变成两种身份。

## 不要顺手做的事

- **不要顺手重构**。改动范围尽量贴着你修的那个问题。这个仓库的注释里记了大量「为什么不是另一种写法」，
  大规模重排会让那些理由与代码对不上。
- **不要顺手升级依赖**。`Miuix 0.9.4` / `AGP 9.1.1` / `Gradle 9.3.1` 这条链是**互相约束**的
  （Miuix 0.9.4 拉 Compose 1.12.0，后者要求 AGP ≥ 9.1.0），单独动一个会散架。
- **不要为了省事把注释删掉**。很多注释记的是「这么写会出什么错」，删掉下一个人就会再写一遍错的。

## 改界面时的两条硬约定

1. **Miuix 组件只在 `compose/ui/Common.kt` 里转发**。调用点用 `Zhi*` 包装，不直接引库 ——
   这样库升级只影响一个文件。
2. **尺寸/颜色/字阶取自既有 token**：`ZhiColors`、`ZhiRadius`、`ZhiTextScale`、`ZhiDialogWidth`、
   `ZhiMotion`。要加新档位就加到 token 里（并写清理由），不要在调用点写新数字。

弹窗上有一批**只有真机才看得出来**的坑（贴底、隐身、按钮被挤出屏幕、点一下窗口就关……），
以及几个**刻意不用**的 Miuix 组件。动手前请先读 [`docs/ui-miuix.md`](docs/ui-miuix.md)。

## 改性能相关代码前

`docs/performance.md` 里每条取舍都写了「不这么做会发生什么」。其中最容易踩的是：

- **不要把 `animateContentSize` 挂到流式中的内容上** —— 内容每 32ms 变一次，动画会被反复重新触发；
- **不要在 `LazyColumn` 的单个 item 里渲染上千个节点** —— 那个 item 会比视口还高，懒加载复用失效；
- **不要把整份 `WorkspaceUiState` 传给只读几个字段的组件**；
- **不要给含 `List` 的类加 `@Immutable`** —— Kotlin 的 `List` 背后可能是 `ArrayList`，
  这个承诺在类型上核实不了，标上去之后一次原地 `add` 就会让界面静默停更。

## 改构建配置前

R8 规则、baseline profile、签名配置都只有**在运行期或发布时**才会暴露问题，见
[`docs/build-and-release.md`](docs/build-and-release.md)。特别是：

- 别用 `-dontoptimize` / `-dontshrink` 之类把 R8 变成空转；
- 往 baseline profile 加规则时，**每条必须带 `H`/`S`/`P` 至少一个 flag**，否则构建直接失败；
- **别把 `Bcore` 的 `minifyEnabled` 关回去**。它开着的时候如果因为 Missing class 编不过，
  正确做法是补 `-dontwarn`，不是关 R8。

## 许可

提交即表示你同意你的贡献以本工程的 **MIT** 许可发布（见 `LICENSE`）。

如果你的改动**引入新的第三方代码或资源**，请同时更新 `NOTICE` 与 `docs/licensing.md` ——
那个文件的规矩是「每条结论都要能被重新核对」，所以请写清来源与核实方式，不要只写结论。

## 报告问题

- 功能缺陷 / 界面问题：开 issue，附上**截图**与复现步骤。
- **安全问题**：不要开公开 issue，见 `SECURITY.md`。
