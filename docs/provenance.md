# 引擎层的补丁与沙箱层不变式

本文件只保留**两件在别处没有的事**：Bcore 相对上游 BlackBox 打了哪些补丁，
以及沙箱层**不得放宽的不变式**。

> **其余内容已移出或删除。** 本文件原先还含许可事实、实测归属表、分批工作量与复现方式 ——
> 那些要么与 [`licensing.md`](licensing.md) 重复，要么是重写**开始前**的规划（旧批次编号、
> 当时的数字）。**现行唯一来源是 [`licensing.md`](licensing.md)**，数字请跑
> `bash tools/provenance.sh`。

---

## 一、Bcore 相对上游的补丁（保留，不要还原）

`Bcore/` 本体来自上游 BlackBox（`ALEX5402/NewBlackbox` 血统，Apache-2.0），
但 IQ Code 给它打过补丁，现在还在：

| 位置 | 内容 |
| --- | --- |
| `BlackBoxCore.doAttachBaseContext()` | 控制器进程 hookless 分支判定，attach 后直接 return |
| `BlackBoxCore.doCreate()` | `create:controller-*` 阶段机，控制器只起服务不装 hook |
| `BlackBoxCore.writeStartupStage()` | 新增方法，落盘启动阶段 |

**建议保留。** 那层隔离正是「沙箱崩溃不拖死主进程」的关键 —— 去掉就等于让 guest 的 native
hook 跑在主进程里，是功能倒退。我们已把这些补丁的**进程名收敛进 `SandboxContract`**，
行为不变、命名归我们。

---

## 二、沙箱层的不变式（不得放宽）

无论怎么重写，以下约束不得放宽 —— 它们已由 `app/tests/` 的断言守住：

- 引擎只 attach 在 `:zhisandbox` / `:black` / `:p0..:p49`，主进程保持干净
  （进程名在 `Bcore/.../SandboxContract.java`，单一来源）
- 控制器进程名单一来源（`SandboxContract`），引擎与宿主层都引用它
- 引擎调用面收敛：除 `ZhiSandbox` 外宿主层不得触碰 `BlackBoxCore`
- guest 调试：只允许本应用私有目录的 `.so`、内存写入有上限、目标页可写检查
- 启动阶段日志按 pid 分文件（不得退回单文件覆盖写）
- AndroidX Startup 的 provider/receiver 必须被清单移除

> 为什么这几条要单独留一份：它们描述的是**「哪些东西看起来可以简化、其实不能动」**。
> 后续重写时最容易踩的就是把上面某一条当成冗余顺手删掉 —— 而删掉不会编译失败。
