本目录是**许可原文**目录，随发行物一起分发
================================================================================

它回答一个问题：**「分发这份 APK / 源码包时，必须把哪些许可文本一并交出去？」**
答案是：`LICENSE`（仓库根目录）+ 本目录 + `NOTICE`。三者缺一不可。

| 文件 | 覆盖什么 | 能不能改 |
| --- | --- | --- |
| `Apache-2.0.txt` | BlackBox 虚拟化引擎（`Bcore/`、`black-reflection/`、`compiler/`）、Termux 终端模拟与视图（`com/termux/terminal/`、`com/termux/view/`、`libtermux.so`）、Material Symbols 图标路径数据 | ❌ **一个字节都不能改**（Apache-2.0 第 4 条要求随附许可证副本；改动它的文本会破坏「这是原文」这个前提） |
| `MIT.txt` | 本工程自己代码的 MIT 全文（与根目录 `LICENSE` 的条款部分相同，另在开头注明了覆盖面），同时也是 `termux-shared` 里 `TermuxConstants.java` 所用的那份 MIT 文本 | ❌ 条款部分不能改；只允许在**开头以分节形式**补充「这份文件覆盖哪些组件」的说明（当前就是这样做的） |
| `IQ-Code-MIT.txt` | 上游 IQ Code 的 MIT 原文（`Copyright (c) 2026 IQge`） | ❌ **逐字保留，不得改写** —— 版权行与条款都是 MIT 要求随附的内容 |

不在本目录里的许可
--------------------------------------------------------------------------------

- **LGPL-2.1**（Rosemoe Sora Editor）：以 Gradle 依赖引入，许可文本在构件内，
  原文见 <https://github.com/Rosemoe/sora-editor/blob/main/LICENSE>。
- **GPL-3.0 / GPL-2.0+ / 其它**（`bootstrap-aarch64.zip` 里的 bash、coreutils、apt、sed、grep 等）：
  这些程序各自随包分发许可，完整列表以归档内各包自带文档与 termux-packages 的
  `<package>/build.sh` 声明为准。**它们不是本工程的代码，不要用本目录的文件去覆盖。**
- **TextMate 语法与主题（MIT）**：逐项版权行与未核实项见
  [`../app/src/main/assets/sora/textmate/NOTICE.md`](../app/src/main/assets/sora/textmate/NOTICE.md)。

为什么本工程整体不必 GPL 化
--------------------------------------------------------------------------------

`bootstrap-aarch64.zip` 里确实有 GPL 程序，但它们是**独立可执行文件**，由本应用以子进程方式启动，
与本工程代码之间是**聚合**（aggregate）关系；而唯一被链接进本应用进程的原生库 `libtermux.so`
按 termux-app 的例外条款属于 **Apache-2.0**。
两条链路的判定依据与核对命令见 [`../docs/licensing.md`](../docs/licensing.md) 第 5、7 节。

本目录的维护规则
--------------------------------------------------------------------------------

1. **不要改这里的文本**。要补充说明，写到本文件或根目录 `NOTICE` 里。
2. 新增第三方组件时，**同时**更新：`NOTICE`、本目录（如需新的许可原文）、
   以及 `docs/licensing.md` 的判断依据。这三处的分工见 `NOTICE` 末尾的附表。
3. ⚠️ **这里没有任何自动化检查。** 原先有一条结构断言 `LicenseNoticeStructureTest`
   盯着这些文件的存在与关键表述，**它已随 `app/tests/` 整套删除**。
   改完之后请**人工**逐项核对：

   ```bash
   # 各文件是否都在、版权行是否还在（把 <owner> 换成对应版权人）
   ls LICENSE NOTICE THIRD-PARTY-LICENSES/
   grep -n 'Copyright' LICENSE THIRD-PARTY-LICENSES/*.txt
   # 本文件是否仍逐项说明了「覆盖什么 / 能不能改」
   grep -n '^|' README.md | head
   ```

   核对标准是 `docs/licensing.md` 的清单：**来源 URL、许可类型、对应文件、修改声明、
   许可原文路径五者必须彼此一致**，而不只是「文件还在」。
