# 蜘蛛工程的代码归属与许可

> **历史文档（重写开始前的规划），不要当现状读。**
>
> 本文里的数字、批次编号与结论都已被后来的工作取代。**现行唯一来源是
> `docs/licensing.md`** —— 那里的数字每次改动前由 `bash tools/provenance.sh` 重新量过，
> 批次编号也与本文不同（本文的 A–I 是**旧编号**，与现在的批 A/B/… 不是一回事）。
> 保留本文只为记录当时的判断依据；若两者冲突，以 `docs/licensing.md` 为准。
>
> 已知已过时之处（不再逐处改写，只列出来免得误读）：
> - 第二节的「约 8,821 行 / 约 90 个文件」→ 现为 **4,551 行**（Termux 上游 7,274 行不变）；
> - 第四节的 A–I 批次表 → 旧编号，勿与现行批次对照；
> - 第四节的「F–I 共约 11,400 行」→ 估计值，实际远低于此。

本文记录**实测**的代码归属现状、许可义务、以及"独立于 IQ Code"的完整工作量。
所有数字都可用 `tools/provenance.sh` 复现。

## 一、许可事实（先看这段，它决定要不要重写）

**IQ Code 是 MIT 许可。**

```
MIT License
Copyright (c) 2026 IQge
```

来源：`projects/IQ-Code-Android/LICENSE`（本机原版）。

MIT 授予的权利（原文）：

> Permission is hereby granted, free of charge, to any person obtaining a copy of
> this software ... to deal in the Software without restriction, including without
> limitation the rights to **use, copy, modify, merge, publish, distribute,
> sublicense, and/or sell** copies of the Software ...

也就是：**用、改、改名发行都不需要重写成自己的代码。**

MIT 唯一的硬性要求：

> The above copyright notice and this permission notice shall be **included in all
> copies or substantial portions** of the Software.

### 当时的三处缺口（都已补上）

- ~~蜘蛛工程没有 `LICENSE` 文件~~ → 已有 `LICENSE`（MIT，© 2026 zhizhu0002）。
- ~~`Bcore/`、`black-reflection/`、`compiler/` 三个模块都没有自己的许可文件~~
  → 三个模块各自都补上了 `LICENSE` 与 `NOTICE`（BlackBox 上游是 Apache-2.0，
  要求保留声明与修改声明）。
- ~~README 自认为衍生作品、却没附上游的版权声明~~ → `NOTICE` 与
  `THIRD-PARTY-LICENSES/` 已建立；本节下面的判断依据因此**仍然适用**，只是缺口已闭合。

### 一个必须说清的点

**重写代码并不免除这个义务。** 只要分发物里仍含 IQ Code 的代码，
就要附它的版权声明。只有做到 100% 不残留其代码才免掉 ——
那等于把 Agent 运行时整个重做（见第四节）。

> 注（后续补充）：以上是**只读许可原文**时的推论，逻辑仍然成立。后来原作者已明确
> 许可本工程改写，且同意不强制要求随附其版权声明 —— 这一新事实如何处置（我们仍然
> 保留致谢）见 `docs/licensing.md` 的「署名」一节。

**结论：合规的最小动作是「补 LICENSE 与 NOTICE」，而不是重写。**
重写是产品/工程层面的目标，与合规无关。

## 二、实测归属

口径：把两棵树的包名/品牌/类名归一化后，按映射路径逐文件比对「逐行相同」的行数。
归一化只抹命名，不动代码形态。

路径映射：
- `com/zhizhu/zhicode/*` → `com/iqge/*`
- `com/termux/app/zhicode/*` → `com/termux/app/iqcode/*`

| 归属区域 | 文件 | 行数 | 仍与 IQ Code 逐行相同 | 占比 |
|---|---|---|---|---|
| **Compose 界面层** | 57 | 15,401 | **0** | 已是我们的 |
| **沙箱宿主层** | 15 | 2,313 | **0** | 已是我们的 |
| Termux 集成层 | 30 | 4,625 | 4,579 | 99% |
| Agent 工具 | 43 | 2,384 | 2,052 | 86% |
| Agent 核心 | 8 | 1,946 | 688 | 35% |
| 后台保活 + 其它 | 12 | 2,472 | 1,502 | 61% |
| *Termux 上游（见下）* | *23* | *7,377* | *7,274* | *不适用* |

- 蜘蛛合计 **188 文件 / 40,082 行**
- 逐行相同合计 **16,095 行**
- **已是我们的：17,714 行（44%）**

### Termux 上游不算法

`com/termux/terminal/*`、`com/termux/view/*`、`com/termux/shared/*` 共 7,377 行，
其中 7,274 行与 IQ Code 相同 —— 但**这些是 Termux 自己的代码**，不是 IQ Code 的：

- 包名就是 Termux 的原始包名（`com.termux.terminal.TerminalEmulator`、
  `com.termux.view.TerminalView`、`com.termux.shared.termux.TermuxConstants` ……）
- 蜘蛛与 IQ Code 都内嵌了 Termux，所以两边相同是**必然且正常**的

这部分与"独立于 IQ Code"无关，不需要动。

### 真正属于 IQ Code 的残留

16,095 − 7,274（Termux 上游）= **约 8,821 行 / 约 90 个文件。**

> 这个数是当时的，已过时：现行数字是 **4,551 行**（见 `docs/licensing.md`）。

集中在 Agent 运行时：会话存储、API 客户端、工具集、上下文压缩、权限门、Termux 集成。

## 三、引擎层的 IQ Code 补丁

`Bcore/` 本体来自上游 BlackBox（`ALEX5402/NewBlackbox` 血统，Apache-2.0）。
但 IQ Code 给它打过补丁，现在还在：

| 位置 | 内容 |
|---|---|
| `BlackBoxCore.java` `doAttachBaseContext()` | 控制器进程 hookless 分支判定，attach 后直接 return |
| `BlackBoxCore.java` `doCreate()` | `create:controller-*` 阶段机，控制器只起服务不装 hook |
| `BlackBoxCore.java` `writeStartupStage()` | 新增方法，落盘启动阶段 |

**建议保留。** 那层隔离正是"沙箱崩溃不拖死主进程"的关键 ——
去掉就等于让 guest 的 native hook 跑在主进程里，是功能倒退。
我们已把这些补丁的进程名收敛进 `SandboxContract`，行为不变、命名归我们。

## 四、"完全独立"的分批工作量

| 批次 | 范围 | 行数 | 状态 |
|---|---|---|---|
| A | `SandboxKeeper` + `SandboxShell` | 154 | **已完成**（重合 78%/79% → 13%/6.7%） |
| B | `SandboxBoard` + `SandboxOverlay` | 331 | 待做 |
| C | `SandboxGuestDebug` | 313 | 待做 |
| D | `FridaEnv` + `SandboxFrida` | 302 | 待做 |
| E | `SandboxGuestHost` | 320 | 待做 |
| — | **沙箱层小计** | **1,420** | 完成后沙箱层 100% 是我们的 |
| F | Termux 集成层 | 4,625 | 待做（最大一块） |
| G | Agent 工具 | 2,384 | 待做 |
| H | Agent 核心 | 1,946 | 待做 |
| I | 后台保活 + 其它 | 2,472 | 待做 |
| — | **Agent 运行时小计** | **~11,400** | |

每批的验证方式一致：
1. `./gradlew :app:compileDebugJavaWithJavac`
2. `bash test-source-no-build.sh`（结构测试全过；当时 18 个，现为 23 个）
3. 归一化重合度复查（应显著下降）

### 对 Agent 运行时的坦白评估

F–I 四批共约 11,400 行，是把这个应用**再做一遍**：
会话持久化、OpenAI 兼容 API 客户端、43 个工具、上下文压缩、权限门、Termux 集成。
**功能上没有任何收益**（这些代码不含 IQ Code 品牌、不依赖 IQ Code、也不与它通信），
收益只有"代码归属干净"一条。

如果目标只是合规 —— 补 LICENSE 就够了，不需要做 F–I。

## 五、不变式

无论怎么重写，以下约束不得放宽（已由 `app/tests/` 的断言守住）：

- 引擎只 attach 在 `:zhisandbox` / `:black` / `:p0..:p49`，主进程保持干净
- 控制器进程名单一来源（`Bcore/SandboxContract`），引擎与宿主层都引用它
- 引擎调用面收敛：除 `ZhiSandbox` 外宿主层不得触碰 `BlackBoxCore`
- guest 调试：只允许本应用私有目录的 `.so`、内存写入有上限、目标页可写检查
- 启动阶段日志按 pid 分文件（不得退回单文件覆盖写）
- AndroidX Startup 的 provider/receiver 必须被清单移除

## 六、复现方式

```bash
# 归属度量测（需要本机存在原版 projects/IQ-Code-Android）
bash tools/provenance.sh

# 结构测试（纯源码断言，不需要设备；当时 18 个，现为 23 个）
bash test-source-no-build.sh
```
