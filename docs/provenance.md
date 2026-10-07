# 引擎补丁与沙箱层不变式

本文件只保留**两件在别处没有的事**：Bcore 相对上游打了哪些补丁，以及沙箱层**不得放宽的不变式**。

> 许可结论、度量方法与未确证事项一律以 [`licensing.md`](licensing.md) 为唯一来源；
> 「本工程还有多少行与 IQ Code 逐行相同」这类**会漂移的数字**请自己跑 [`tools/provenance.sh`](../tools/provenance.sh)，
> 本文不再抄第二份。

---

## 一、Bcore 相对上游的补丁（保留，不要还原）

`Bcore/` 本体来自上游 BlackBox（`ALEX5402/NewBlackbox` 血统，Apache-2.0），
但来源是**多跳**的：`ALEX5402/NewBlackbox` → IQ Code 的 vendor 副本 → 本仓库（提交 `f849e14`）。
因此「本工程改了什么」与「上游后来演进了什么」不能只靠 diff 分开 —— 逐文件核对与归因边界
写在 [`Bcore/NOTICE`](../Bcore/NOTICE) 与 [`licensing.md`](licensing.md) 第 5.3 节。

**本工程自己的改动（有标记可查，保留不要还原）：**

| 位置 | 内容 |
| --- | --- |
| `SandboxContract.java`（新增） | 把「沙箱控制器进程名」等收敛成**编译期常量**的单一来源。刻意不挂在 `BlackBoxCore` 上：读它的字段会触发静态初始化，把 BlackBox 拉进本该干净的主进程 |
| `BlackBoxCore.java` | 新增 `sandboxControllerProcessName()`；hookless 控制器初始化分支改为与上述单一来源比较；启动阶段记录（`writeStartupStage`）改为**按 pid 分文件** |
| `Bcore/src/main/AndroidManifest.xml` | 控制器进程名由 `:iqsandbox` 改为 `:zhisandbox`，与 Bcore 清单、宿主清单三处保持一致（**原先有结构测试守着这条不变式，该套件已删除**，三处是否一致要自己 `grep` 核对） |

**vendoring 时带来的改动**（记在提交 `f849e14` 的信息里，集中在 `Bcore/build.gradle`）：
不走 ndk-build 而改用预编译的 `jniLibs/arm64-v8a/libblackbox.so`、`compileSdk` 固定 35、
`aidlPackagedList` 改属性赋值、去掉 lint 的报告参数、新增 jitpack 仓库。

`black-reflection` 与 `compiler` 的源码**未被本工程修改**（已与上游 `main` 逐文件核对）。

> ⚠️ 两件未关闭的事：被修改的 Bcore 文件**没有文件级声明头**（Apache-2.0 §4(b)）；
> 且 `libblackbox.so` 是**未经内容审计**的二进制产物。见 [`licensing.md`](licensing.md) 第 9 节。

**建议保留这些改动。** 那层隔离正是「沙箱崩溃不拖死主进程」的关键 —— 去掉就等于让 guest 的 native hook
跑在主进程里，是功能倒退。

---

## 二、沙箱层的不变式（不得放宽）

无论怎么重写，以下约束不得放宽：

- 引擎只 attach 在 `:zhisandbox` / `:black` / `:p0..:p49`，主进程保持干净
  （进程名在 `Bcore/src/main/java/top/niunaijun/blackbox/SandboxContract.java`，单一来源）；
- 控制器进程名单一来源，引擎与宿主层都引用它；
- 宿主层里真正引用 `BlackBoxCore` 的只有 `ZhiSandbox`（引擎门面）；
- guest 调试：只允许本应用私有目录（`dataDir` / `/data/data/<pkg>` / `filesDir` / `nativeLibraryDir`）下的 `.so`、
  内存写入有单次上限、目标页可写检查；
- 启动阶段日志按 pid 分文件（不得退回单文件覆盖写）；
- AndroidX Startup 的 provider / receiver 必须被清单移除。

> 为什么这几条要单独留一份：它们描述的是**「哪些东西看起来可以简化、其实不能动」**。
> 后续重写时最容易踩的就是把上面某一条当成冗余顺手删掉 —— 而删掉不会编译失败。

⚠️ **这几条现在没有任何自动化防线**：原先逐一钉住它们的结构断言（`Sandbox*Test` / `Frida*Test`）
已随 `app/tests/` 整套删除。改这一层时请自己逐条对照源码，并在 IQ 沙箱里真跑一遍。

各条的**理由与人工核对位置**见 [`sandbox-host.md`](sandbox-host.md) 与
[`architecture.md`](architecture.md) 第 5 节。

---

## 三、怎么度量「还留着多少上游代码」

```bash
# 需要本机存在原版仓库；默认取 ../IQ-Code-Android，可用环境变量覆盖
IQCODE_ORIGINAL=/path/to/IQ-Code-Android bash tools/provenance.sh
```

脚本做三件事：

1. **归一化命名**（`com.zhizhu.zhicode` → `com.iqge`、`Zhi`/`ZhiCode` → `IQ`/`IQCode` 等），
   **只抹命名、不动代码形态** —— 所以「逐行相同」意味着代码本身没被改写；
2. 按映射路径逐文件比对逐行相同的行数，并单独处理**改了名的类**（配对表在脚本里，
   漏配对会把重合度静默算成 0，脚本自带漏算自检）；
3. 把残留行分成骨架 / 字面量 / 声明 / 语句四桶，再给出**净相同行** = 逐行相同 − 骨架行 − 跨组件协议串行。

脚本的口径讨论、它历史上犯过的错、以及「哪些扣减是可被推翻的」都写在 [`licensing.md`](licensing.md) 里 ——
因为那些结论本身需要可核对，而脚本的输出只给数字。

**不要把这套度量当成代码质量指标。** 它回答的只有一个问题：*派生关系还剩多少可被逐行识别*。
