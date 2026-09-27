# 许可与归属

本文件记录**这些结论是怎么得出的**，不只是结论本身。许可判断的价值在于可核对：
上游若改了许可，你应该能拿这里的每一条重新验证一遍。

结论清单在 [`NOTICE`](../NOTICE)，许可原文在 [`THIRD-PARTY-LICENSES/`](../THIRD-PARTY-LICENSES)。

> **本文件只写方法与判据，不抄会漂移的数字。** 逐区域、逐文件的数字随每次重写变化，
> 抄进文档就会变成假话（本文件历史上至少错过两次，见文末「它犯过的错」）。
> 要看数字请跑 [`tools/provenance.sh`](../tools/provenance.sh)。

---

## 一句话结论

蜘蛛自己的代码用 **MIT**。发行物里另有 Apache-2.0 组件与一批各自许可的 Termux
二进制程序，但**本项目整体不必 GPL 化**。

代码层面的独立性看**净相同行** = 逐行相同 − 骨架行 − 跨组件协议串行。这个数的定义、
算法与它可能被做手脚的地方见「残留构成与净相同行」一节。

---

## 为什么不必是 GPL —— 这一步曾经看起来是反的

工程里有一个 `libtermux.so` 和一个 33 MB 的 Termux bootstrap，第一眼像「必须 GPLv3」。
查下来不是：两个组件分属两条不同的线。

### 1. `libtermux.so` 是 Apache-2.0，不是 GPL

它是唯一一个**被链接进本应用进程**的原生库（`System.load`）。如果它是 GPL，
整份应用就是衍生作品、本工程就必须 GPL 化。判定依据：

```bash
# 看它导出什么符号，据此确定它对应上游哪个文件
tr -c '[:print:]' '\n' < app/src/main/jniLibs/arm64-v8a/libtermux.so \
  | grep -aE '.{6,}' | head
# 输出里有：
#   Java_com_termux_terminal_JNI_createSubprocess
#   Java_com_termux_terminal_JNI_setPtyWindowSize
#   Java_com_termux_terminal_JNI_setPtyUTF8Mode
#   Java_com_termux_terminal_JNI_waitFor
#   Java_com_termux_terminal_JNI_close
```

这组符号名对应 `termux-app` 的 `terminal-emulator` 子库（该库的 `JNI.java`
里声明的正是这几个 native 方法）。而 `termux-app` 根 `LICENSE.md` 的 Exceptions 段写着：

> [Terminal Emulator for Android](https://github.com/jackpal/Android-Terminal-Emulator)
> code is used which is released under [Apache 2.0] license.

`libtermux.so`、`com/termux/terminal/`、`com/termux/view/` 全部落在这个例外里
→ **Apache-2.0**。

### 2. bootstrap 里是独立程序，与我们是聚合关系

`app/src/main/assets/bootstrap-aarch64.zip` 解压后有 4,533 个文件，里面确实是 GPL 程序
（`bash`、`coreutils`、`sed` 等），但它们是**独立可执行文件**，由本应用以子进程方式启动：

```
蜘蛛进程  ──fork/exec──▶  $PREFIX/bin/bash（GPLv3，独立进程）
```

GPL 明确允许把 GPL 程序与其它许可的程序**聚合**（aggregate）分发，只要聚合体里每一部分
仍受各自许可约束。这与「把 GPL 库链接进同一个进程」是两件不同的事：前者不产生衍生作品，
后者会。`libtermux.so` 走的是后一条路，但它恰好是 Apache-2.0 —— 所以两条链路都不传染。

**因此 MIT 是安全的。**

### 一处仍需自己确认的事

bootstrap 里的程序许可各自适用，且要求源码对接收者可得。出处是 `termux-packages`
与本工程的 fork 配方（[`tools/termux-bootstrap-fork/`](../tools/termux-bootstrap-fork)，
含构建工作流 `build-bootstrap.yml` 与验收脚本 `VerifyBootstrap.java`）。
**发行时请确认这份源码对接收者可达。**

---

## 为什么「重写」不能免除署名义务

这是最容易搞错的一点：MIT 唯一的硬性要求是版权声明与许可声明必须
「included in all copies or substantial portions of the Software」。

所以：用 IQ Code 的代码 → 不用重写也合法；改名发行 → 合法；
**重写 → 也不免除署名义务**，只要分发物里还留着它的代码。

「重写」解决的是**归属清楚**（谁写的、改了多少），不是**免除署名**。两件事不要混。

---

## 开源前的许可复核（2026-09-27）

本节记录**为开源而做的那次复核**：核了什么、怎么核的、结论是什么，
以及**哪些部分我核不出来**。复核方式是可复现的 `curl` GitHub public API 命令，不是凭印象。
GitHub 上的仓库状态会变，过一段时间请按同样命令重跑。

### 1. 我们 vendored 的那一份：`ALEX5402/NewBlackbox` → Apache-2.0（已确证）

```
curl -s https://api.github.com/repos/ALEX5402/NewBlackbox/license
# → "spdx_id": "Apache-2.0"（"key": "apache-2.0"，HTTP 200）
```

这与仓库里 `Bcore/LICENSE`、`black-reflection/LICENSE`、`compiler/LICENSE` 三份
Apache-2.0 原文一致，所以**对直接上游的署名与许可要求是满足的**。

> ⚠️ GitHub 的 license API 是**自动识别**（拿 LICENSE 文本去比对特征）：
> 它能证明「这个文件在、且是 Apache-2.0 的标准文本」，不能替代法律判断。

### 2. 血统更上游的 `FBlackBox/BlackBox`：源码与 LICENSE 已被作者移除（**无法确证**）

这是我这次**核不出来**的部分，如实写在这里。

- 仓库**仍然存在**（`curl` 该 repo → HTTP 200）。所以「仓库被删了」这种说法不准确 ——
  准确的说法是**源码被删了**：它的根目录现在只剩下 `.gitattributes`、`.github`、
  `.gitignore`、`README.md`。
- 因此它没有许可文件可查：`/license` 端点 → **HTTP 404**；仓库元信息 `"license": null`。
- README 只剩 293 字节，原文就是作者宣布删除项目：

  > Currently, I think everybody has knows this event, this project affects so many
  > innocent developers. So I decide to dissolve the telegram group and delete this project.

- 另一个可能是源头的仓库 `niunaijun/BlackBox` 返回 **HTTP 404**（已不存在）。
  本工程代码里保留的包名 `top.niunaijun.*` 正是来自这个名字。

**结论（严格限定为「我确证不了」）**：本工程 vendored 的是 Apache-2.0 的
`ALEX5402/NewBlackbox`，这一层的义务我们履行了；但**再往上一层的原始项目当时是否
Apache-2.0（或其它许可），今天已经无从查证** —— 文件被作者删了。我**不对此下法律结论**，
只把它标成一个已知的、无法当场关闭的疑点。能补上这一环的只有第三方持有的删除前快照
（例如 Software Heritage、其它 fork）；本仓库没有这样的材料。

### 3. 保留上游包名 `top.niunaijun.*`：是**故意**的，不是漏改

```
git ls-files | grep -c 'top/niunaijun/'                       # → 318 个路径
git ls-files -z | xargs -0 grep -l 'top\.niunaijun' | wc -l   # → 551 个文件提到
```

不能改名的技术原因：Bcore 的原生代码是**按字符串**找 Java 类的，名字一改就崩：

```cpp
// Bcore/src/main/cpp/JniHook/JniHook.cpp
env->FindClass("top/niunaijun/jnihook/jni/JniHook");
env->GetStaticMethodID(clazz, "nativeOffset", "()V");

// Bcore/src/main/cpp/BoxCore.cpp（VMCORE_CLASS）
env->FindClass(VMCORE_CLASS);
```

好处是顺带也满足了「保留来源标识」—— 谁看代码都能认出这套虚拟化引擎的出处。

### 4. 本节的边界

- 只陈述**用命令核出来的事实**与**核不出来的地方**，不构成法律意见。
- 复核日期 2026-09-27；上述仓库状态之后可能变化。
- 相关守卫 `LicenseNoticeStructureTest` 守各模块许可文件的存在，但它**证明不了**
  上面第 2 点那个疑点已被解决 —— 那个疑点目前是**开放的**。

---

## 度量：怎么知道还留着多少别人的代码

用 [`tools/provenance.sh`](../tools/provenance.sh)：把两棵树的包名、品牌、类名做**归一化**
（`com.zhizhu.zhicode`→`com.iqge`、`Zhi`→`IQ`、`蜘蛛`→`IQ Code` 等），再按映射路径
逐文件比对**逐行相同**的行数。关键是**只抹命名、不动代码形态** —— 因此「相同」意味着
代码本身没被改写，而不只是「看起来像」。

### 这个脚本本身出过三次错，都是同一类

它曾把「改了文件名的类」算成「无对应文件」，于是重合度**误报为 0**：

| 第几次 | 范围 | 后果 |
| --- | --- | --- |
| 1 | 沙箱层 14 个改了名的类 | 沙箱层重合度误报成 0 |
| 2 | 修好第 1 次时又按路径算了一遍 | 文件数与行数虚增到 203 / 40572 |
| 3 | Agent 工具层 2 个改了名的类 | `tools/` 目录的重合度误报成 0 |

现在配对表是四元组（本工程目录 | 原版目录 | 本工程文件名 | 原版文件名），同时用
`RENAMED_BASENAMES` 把这些名字从按路径的循环里排除，避免重复计数。

**这类错误的危害不在于数字难看，而在于它让「未来是否漂移」变得不可检测** ——
一个恒报 0 的区域，永远不会提醒你它的重合度回升了。反向自检的做法：把一个上游文件
当成我们自己的去比对，结果应当接近 100%（已验证：`自比对: 93 行中相同 93 行`）。

---

## 残留构成（四桶）与「净相同行」

「逐行相同」这个数字本身不够用：它把 `import android.os.Process;`、`}`、`return out;`
与真正的算法代码算在同一格里。把这个数字当成「还抄了多少」，会得出一个偏大得多的结论。
所以脚本把残留行分四桶，分类规则简单到可以人工核对 —— `CLASSIFY` 在 composition 与
algorithm **两个模式下共用同一份程序**（写在脚本里一份，不各写一遍：本文件别处已经踩过
「同一规则写两遍、然后各自漂移」的坑）：

| 桶 | 能不能归零 | 为什么 |
| --- | --- | --- |
| **骨架**（`{}();,[]` / `import` / javadoc） | **不能** | 任何 Java 文件都以 `import …` 开头、以 `}` 结尾。把这些行改得不一样等于删 import 或往里塞噪声 |
| **字面量**（协议键名与用户可见文案） | **不能** | JSON 字段名、动作名是跨组件协议（宿主 / Agent 工具 / Frida 脚本三方对齐），改了会让两边对不上；用户可见文案是刻意逐字保留的 |
| **声明**（字段、签名、注解、静态常量） | 基本不能 | 相同不是因为抄，而是因为**这是 Java 里写同一件事的唯一写法** |
| **语句**（有判断与动作） | 能，而且应该压 | **这才是「读起来还像原版」的地方** |

**关于「字段带初始化器」的界线**：`private final AtomicBoolean busy = new AtomicBoolean(false);`
这类行算**语句**，不算声明 —— 规则是「有赋值即语句」，而那个初始化器本身是可改写的表达式
（改成 `new AtomicBoolean()` 也编译得过）。真正算声明的只有「没有赋值、没有方法调用」的行。
这条界线看起来吹毛求疵，但它是「声明桶能不能收窄」那个结论的前提 ——
界线挪一格，结论就换一个。

**为什么不再分三桶**：最初只有「骨架 / 字面量 / 其它」三桶，而「其它」里混着大量
`public final String planFile;` 这类声明，于是某个文件显示「其它行、占比 70%」——
与人工查阅的结论差了一个数量级。分成四桶之后同一个文件是 17 行语句，数量级这才对得上。

### 净相同行 = 逐行相同 − 骨架行 − 跨组件协议串行

**可扣的只有这两类，规则刻意窄到可以人工核对。声明行与语句行永远不扣** ——
只要这两桶里还留着一行与改写前的原版相同，净数字就不是 0。这是这个数字唯一有价值的地方。

| 可扣类 | 判据 | 为什么它能扣 |
| --- | --- | --- |
| 骨架行 | 整行只由 `{}();,[]`、`import`、`package`、javadoc 前缀构成 | 任何 Java/Kotlin 文件都长这样 |
| 跨组件协议串行 | 该行的**每一个**字符串字面量都合格：非空、全 ASCII、去掉转义后至少含一个字母或数字，且**满足两选一** —— ① 在本工程 ≥2 个文件里出现；② 它出现的**每一处**都在**协议位置**：引号之前最近的 `(` 的名字在白名单（`tools/provenance.sh` 里的 `PROTO_CALLS`，**只此一份**）里，且这个 `(` 到该引号之间没有别的字符串；或形如 `case "…":` | ① 是「两个组件按同一个名字对齐」（宿主 / Agent 工具 / Frida 脚本 / 持久化键名）；② 是「另一端不在本工程里」（模型 API 字段名、HTTP 头、apt/环境变量、用户磁盘上的文档格式）。两类都是**改了会让对面失配**的名字 —— 那是协议，不是表达 |

**这条规则是脚本里写死的，不是事后挑数字**，而且它宁可把数字算高：

- **中文串一律不扣**。本工程里的中文串是用户可见文案 —— 它可以改写，所以它属于
  「还要重写」，不属于「可扣」。把文案改成自己的措辞，净数字才会下降。
- **纯符号串一律不扣**。`, `、`:`、`\n` 是分隔符，按第 1 条它们也会入选协议串 ——
  但那样一次就能多扣几十行，所以排除在外（`\n` 里那个 `n` 会被误当成「含字母的名字」，
  因此规则是**先摘掉转义序列再找字母数字**）。
- **空串一律不扣**。`""` 出现在 97 个文件里，第一版实现就把它算进了协议串，于是协议串行
  从 203 行虚增到 344 行 —— 这个缺陷当时没被看出来，是因为只看了合计数。这一条现在还在
  起作用，而且是**行级**的：`String type = block.optString("type", "")` 这一行里 `"type"`
  虽是接口名，但整行里有 `""`，所以**整行不扣**（行级规则是「每一个串都要合格」）。
- **「值」不算接口名（比字面判据严一格）**。判协议位置时要求「这个 `(` 到该引号之间没有
  别的字符串」，也就是只认白名单调用的**第一个字符串实参**：`body.put("key", "value")`
  里的 `"value"`、`setRequestProperty("k", "v")` 里的 `"v"` 都不算。不这么卡的话，
  `new JSONObject().put("root","workspace.root")` 这类**文档内容**（画布的 id / type 值，
  本来就是模型可以自己生成的）会被当成契约扣掉。这一格值 8 行（严 22 行 vs 宽 30 行）——
  宁可把数字算高，所以从严。

每一次扣减都能被推翻：脚本会把**被扣的每一个串连同它出现的文件**写进
`build/provenance-protocol-strings.txt`，分三段 —— `[files]`（≥2 个文件的，「对齐的另外
两处」指得出来）、`[position]`（只出现一次、仅靠协议位置可扣的，每一处都给出「文件:调用名」，
看得出是哪个调用里的参数）、以及**没有被扣**的串（中文文案 / 空串 / 纯符号 / 只在文案位置
出现的英文串）。最后一行给出「只在一个文件里出现、也不在协议位置上」的串有多少个 ——
不逐条列出（上千条，列出来只会淹没上面三段）。

已知还剩下的松处：一句纯 ASCII 的用户可见文案若恰好出现在两个文件里，也会被算进协议串。
**所以净数字要连审计文件一起看，不能只看一个数。**

### 接口名也只出现在一个文件里（已按方案 A 扣减）

`PROVENANCE_LITERALS=1` 把字面量桶拆成两份之后暴露了口径的一个漏洞：
**协议串的判据是「≥2 个文件」，但接口的另一端不一定是我们自己的另一个文件。**

现在这一类也扣（判据 v2）：`"output_config"`、`"reasoning_effort"`、`"session-id"`、
`"stop_reason"` 这类**只出现在我们一个文件里、但每一处都在协议位置上**的串 ——
另一端是 OpenAI / Anthropic 的服务器、是统一的 HTTP 头格式、是已经写在用户磁盘上的会话文件，
改不得。**放宽的方向是「诚实」意义上的：它让净相同行变小，而不是变大。**

两个数各自独立量过，结果一致：只读原型 `build/measure-rule-a.awk` 直接判那批行，
得「可扣 22 / 仍不扣 265」；改完脚本后 203 → 225。差值是同一批行。

**上一版这一节举的例子是错的**（写文档时也得去审计文件里核一遍）：`"input_schema"`、
`"max_output_tokens"`、`"payload"` 其实都出现在 ≥2 个文件里，本来就在 `[files]` 类里被
扣掉了（`grep` 一下审计文件就知道）；`"+ "` 则在整个工程里**根本不存在**这个串字面量 ——
`UnifiedDiff` 写的是 `out.append("--- ")` 这类形式，而 `append` 也不在白名单里，所以它从来
不是候选。记下来是因为这类例子最容易「看着像」就被写进文档，而它正是读者用来核对规则的样本。

**白名单只此一份**（`tools/provenance.sh` 的 `PROTO_CALLS`，审计文件照印，文档不再抄第二份）。
行级判据没有变：该行的**每一个**串都要合格，一个不合格就不扣。

### 语句行还能收敛多少（`PROVENANCE_SHAPE=1`）

净相同行里最大的一桶是语句行。要判断「还值不值得继续投入重写」，需要知道其中有多少是
**同一件事被写了几遍** —— 那是唯一一种「收敛就能省行」的余量。

**为什么必须扫连续多行窗口而不是统计单行形状**：`if (x == null) continue;` 本身就是一行，
重复二十次也只是二十行；把它提取成一个 helper 之后是「一个 helper + 二十个调用点」，
行数**更多**。所以单行重复度只说明模板化程度，不能算收益。真正能省行的是多行模板，
它在语句清单里表现为几行连续重复。

**这个上限现在是 0，而且是这个工具先量出数据、再按数据做出来的**（不是反过来：先改代码
再声明已经没有余量了）。它第一次跑出来时是 **52 行 / 6 个极大模板**，全部是同一族 ——
「遍历 `JSONArray` → `optJSONObject` → 判空」，收进 `JsonItems`（见下）之后，工具再跑报
**可省 0 行**。

那张 52 行的表本身要当**上限**看，不是可完成的工作量，理由三条都写在脚本注释里：窗口取自
diff 的未变块，相邻两行在源文件里未必真的相邻（邻接性是近似的，动手前必须读源码确认）；
只出现一次的形状里惯用法与真正独有的逻辑混在一起；而且「窗口里必须至少一行在调用方法」
这条规则会排掉连续字段赋值序列 —— 那一类恰恰是某些批次真正做出成绩的地方，所以当时的真实
量级在 52 到 84 之间（84 是加这条规则之前的数，含假阳性）。实际做完落在 52 那一侧，
说明这条规则没有把真实工作漏掉太多。

**这里要标一句口径的变动，否则上面几个数字对不上**：那 52 行是在**旧的分类器**下量出来的
（当时语句桶 1020 行）。后来发现分类器把 217 行语句算进了声明桶（见下），修好之后语句桶变成
1160 行 —— 同一个桶，定义变了，行数就变了。`JsonItems` 那一笔的减量（-60 行）不受影响：
它减掉的是那个模板族本身。

### 462 行「只出现一次的形状」逐条判读的结果

工具报「可省 0 行」之后，语句桶剩下的那些「只出现一次的形状」是逐条读过的 —— 清单由

```bash
PROVENANCE_SHAPE=1 PROVENANCE_SHAPE_UNIQUE=1 bash tools/provenance.sh
```

导出到 `build/provenance-unique-shapes.txt`（每行带文件名）。

> **这一节要连口径一起读**：形状完全相同的行会被算进「重复」那一档，所以这些行
> **在结构上彼此不同** —— 这是算法的定义决定的，不是巧合。这既说明「没有剩下可收敛的
> 重复」，也说明下面这张表要一个个看类别，不能只看总数。而**只看结论不看这一行的口径，
> 它就会变成一句无法核对的话**。

| 类别 | 有第二种写法吗 | 判读 |
| --- | --- | --- |
| 控制流（`if` / `for` / `while` / `try` / `} else {`） | 很少 | **不改**：每一条都是具体的业务条件（`if (status < 200 \|\| status >= 300)`）。它的内容是那个条件本身，换写法只能靠取反或改成三目，两者都让意图更难读 |
| 局部变量初始化（`T x = expr;`） | 有（内联、改名） | **不改**：内联只在这条语句只被用一次时成立，而改的后果是把一行变成主语义里的一小段 —— 可读性换数字 |
| 调用语句（`reload();`、`super.onResume();`、`view.requestFocus();`） | 有个别 | 见下面「唯一一处真正像重复的地方」 |
| `return` 表达式 | 很少 | **不改**：同上，内容是表达式本身 |
| 其它（`case …:`、注解行、折行续行等） | 没有 | 语法本身要求这么写 |
| 字段/常量声明并赋值 | 有个别 | `private final JSONArray messages = new JSONArray();` —— 初始化器本身可改，但改它就只是换个写法 |
| 构造期 `this.x = y` | 没有 | `this.toolCalls = …` 与形参同名，只能这么写 |

这 462 行里**唯一有实质重复嫌疑的地方**是三处「把一个数组的元素全部追加到另一个数组」：

```java
for (int i = 0; i < restored.length(); i++) messages.put(restored.getJSONObject(i));   // ZhiCodeEngine
for (int i = 0; i < extra.length(); i++) additionalToolContent.put(extra.get(i));      // ZhiCodeEngine
for (int i = 0; i < plan.recent.length(); i++) out.put(plan.recent.getJSONObject(i));  // ContextCompactor
```

**它们看着可以收成一个 helper，但不能收。** 前两行的 `getJSONObject` 在遇到非对象元素时
**抛 JSONException**，第三行的 `get` 什么都能放 —— 也就是说「跳过非对象」与「遇到非对象
就抛」是两种不同的行为，而这三处**刻意选了不同的那一种**。收成一个 helper 必须给一个
`strict` 开关，那等于把三行变成「一个带开关的 helper + 三处调用」，行更多、且把
「这里到底要不要容忍畸形数据」这个决定藏进了一个参数里。所以留着。

另外两处**曾经**是全工程唯一还在用「一行塞十几个语句」写法的文件：`UiCanvasStore`
与 `UiCanvasController`。**这两处已经重写掉了**（见「画布两层」一节），也就是这一节剩下来的
「没有第二种写法」是**在那两处按原样压着的情况下**读出来的结论 ——
换了个写法之后要重新核对一遍，不能直接沿用。

**这一节的结论**：语句桶里「同一件事被写几遍」的部分已经收完（`可省 0 行`），剩下那些逐条
读过之后，**没有一行是「因为没想到更好的写法才与上游相同」** —— 它们或是 Java 里写同一件事
的唯一写法，或是改了就只为让那一行看起来不一样。也就是说：**再往下压语句桶，收益已经不是
代码质量，只是数字。**

---

## 几处结构性的收窄

### `JsonItems`：把「取下标 → `optJSONObject` → 判空」收成一处

上一节那张 52 行的模板表就是这一笔的依据。改动本身是一行一处：

```java
// 改前（32 处各写一遍）
for (int i = 0; i < messages.length(); i++) {
    JSONObject message = messages.optJSONObject(i);
    if (message == null) continue;

// 改后
for (JSONObject message : JsonItems.of(messages)) {
```

**真正的理由不是行数，是「漏掉判空的那一处不报错」。** 这些报文来自服务端或别的组件，
数组里可以放 null、也可以放非对象。漏判的那一处只在这条路径第一次遇到畸形报文时抛 NPE ——
本地测不到，用户那边表现为偶发崩溃。收进 `JsonItems` 之后，「跳过非对象」只有一个实现、
只有一处需要被读。

两个站点**刻意没有改**，因为下标本身是语义的一部分：

| 位置 | 为什么留着 `int i` |
| --- | --- |
| `OpenAIResponsesProvider`：`put("output_index", i)` | 下标要作为协议字段发出去，不是遍历的副产品 |
| `OpenAIChatCompletionsProvider`：`has("index") ? call.optInt("index", i) : i` | 同上，且带一个回退到「第几项」的语义 |

这两处也是 `JsonItems` 的 javadoc 里写下的那条「什么时候不要用它」。

**动手前必须先跑 `build/check-index-use.awk`**，原因是这个坑编译查不出来：把 `for (int i …)`
换成 for-each 之后，循环体里若还残留 `i`，它会**静默改绑到外层变量**（外层恰好也有 `i` 时），
编译一声不响、行为已经变了。先扫一遍，结果正好就是上面那两处；若没有这一步，它们会带着错误
的下标发出去，只有在服务端回报文时才会发现。（`build/check-index-use.awk` 与
`build/rewrite-json-loops.awk` 是一次性工具，在 `build/` 下，不进仓库 ——
它们要说明的事情已经写在这一节里。）

### 声明桶怎么收窄：36 个顶层类型改为包内可见

声明桶里能动的只有一类：**顶层类型的公开面**。`public final class ReadTool implements ZhiTool`
与上游逐字相同，因为那是 Java 里声明这个类的唯一写法 —— 除非把这个类**降为包内可见**：
`final class ReadTool`。

判据不是「grep 一下有没有别的包引用」，而是**编译**：

```bash
./gradlew :app:compileDebugJavaWithJavac   # 降级后若有跨包引用，javac 直接报错
./gradlew :app:assembleDebug               # 再走一遍清单合并与资源链接
```

再加一遍非 Java 资源的类名扫描（`assets/`、`res/`、`AndroidManifest.xml`）—— 反射按类名找的
路径不在编译器的检查范围内。**这两遍都过了才算数。**

**必须保持 `public` 的四类**（逐类点名，因为它们看起来与上面那 36 个没区别）：

| 类 | 为什么必须是 public |
| --- | --- |
| `SandboxBoard`、`SandboxKeeper`、`SandboxRpcService`、`ZhiFileProvider`、`KeepAliveService`、`MainActivity`、`ZhiCodeApplication` | 在 `AndroidManifest.xml` 里注册，由框架实例化（后两者还受基类强制） |
| `TermuxTerminalPane`、`SandboxOverlay` | 是 View；界面层要按类型引用，且可能被框架反射实例化 |
| `ZhiTool`、`ToolRegistry`、`BashTool`、`McpTool`、`SkillTool`、`WebSearchTool`、`WebFetchTool`、`ZhiSandboxTool`、`ZhiDebugTool`、`AndroidIntentBridge` | 被别包引用（`ZhiCodeEngine` 在 `core`，工具在 `tools`） |
| `UiCanvasStore`、`UiCanvasController`、`FridaEnv`、`SandboxGuestHost`、`SandboxPrefs` 等 | 同上；沙箱层与 Agent 层是两个包 |

**Termux 上游那三个包不动**（`com/termux/terminal`、`com/termux/view`、`com/termux/shared`）。
理由不是「它们也是我们的」，而是：那是 Apache-2.0 第三方文件，改它们的公开面属于**修改第三方
代码** —— 要按 Apache-2.0 第 4 条附修改声明，收益只是几个数字、成本是一份义务。这类文件在
度量里本来就被单列成「Termux 上游（非 IQ Code）」，不参与净相同行。

**这一节还留下一个教训**：`TerminalColors`、`TerminalColorScheme`、`TerminalRenderer`、
`Logger`、`SandboxFrida`、`SandboxGuestDebug`、`SandboxOverlay` 在纯 Java 层面确实只被同包用，
但「只被同包引用」**不等于**「可以降」—— `TerminalOutput` 就是反例：它被同包的
`TerminalSession extends TerminalOutput` 继承，而 AOSP 的 `TerminalSession` 里有
`TerminalOutput.context` 这个字段的反射依赖（那是 AOSP 的代码，不是本工程的，所以本工程里
查不出来）。结论就是前面那句：判据必须是编译 + 资源扫描两遍，而不是 grep。

**量级要说清楚**：这条路本批只产出二十几行（占当时净相同行的 1% 量级）。它值得做的理由不是
数字，而是**公开面本来就该最小**；指望靠它把净数字压到 0 是不现实的，继续往下的主通道是把
剩余的用户可见文案改成自己的措辞。

### 用户可见文案改成本工程措辞

字面量桶拆成两份之后（`PROVENANCE_LITERALS=1`），不可扣的那部分里**含中文的只有二十几行**
（同串计 25 处）—— 它们才是真正的用户/模型可见文案。样例：

```
压缩失败：模型没有返回结果          →  压缩未完成：模型没有返回任何内容
压缩中断：摘要达到输出上限，请调高…  →  压缩未完成：摘要用满了输出上限，请调高…
消息标识重复，已拒绝修改            →  消息标识出现重复，修改已拒绝
原 API 配置                        →  内置 API 配置
沙箱日志已清空                      →  已清空沙箱日志
Android Intent 失败：               →  Android Intent 调用失败：
需要 hide_root                      →  缺少 hide_root 参数
query 不能为空                      →  缺少 query 参数
```

改之前先查了两件事，因为它们决定「改文案」会不会变成「改行为」：

1. **没有任何测试钉住这些串**（`app/tests` 与 `app/src/test` 全查）—— 否则测试会把
   「作者把这些字打在哪儿」变成契约（本文件另一处记过这个坑）；
2. **没有任何代码按这些文案做分支判断**（`contains` / `startsWith` / `equals` 全查）——
   有的话，改文案就是改行为。

语义完全不变，只换措辞；协议键名（`hide_root`、`show_floating_log`、`payload` 等）一律不动。
骨架/声明/语句三桶各有 1~4 行的微小波动 —— 那不是改到了这些桶，而是 diff 对齐游标随改动
平移后的正常噪声。

### 内嵌 Frida 载荷：一句注释差点让目标消失

同一件事在仓库里曾经有**三份互相矛盾**的写法，其中两份的效果是**让度量里的重写对象消失**：

| 位置 | 说法 | 判定 |
| --- | --- | --- |
| `SandboxFrida.java` 的「归属说明」 | 「从外部引入的，属于**冻结资源**，不是本次重写的对象」 | **不成立**（见下） |
| 上面「字面量」一节 | 「它是我们自己的代码……**可改、也应该改**，是这一桶里唯一真正的重写对象」 | 成立 |
| 按语句行排序那节 | 「这 43 行是内嵌脚本的大串 —— 这一类**不该再花时间**」 | 错 |

判定不是靠印象，是**量出来的**：把两个文件里的载荷文本抽出来，只对齐品牌名（`Zhi` ↔ `IQ`）
再 diff —— 差异只有两处标识符名（`const Zhi=…` 与 eval 那行的形参名），其余**逐行相同**。所以：

- 它不是「外部引入」的，它是 `IQ-Code-Android/.../SandboxFridaBridge.java` 的 `bridgeScript`
  原样留在我们文件里；
- 它照旧计入「净相同行」（字面量桶里一个只在一个文件出现、又不在协议位置的大串，按现行规则
  **不可扣**），所以只要它原样留着，净相同行就 ≥ 40，到不了 0；
- 「冻结资源」那句话是本工程明确要防的写法：**一句注释就能让度量对象自己消失**。Termux 上游
  那七千多行确实被单列，但那是按写明的规则、且有独立的来源与许可；这里当初没有那样的依据，
  写下的却是一样的结论。

**这一步的收益要说实话**：它买到的是指标上那 40 行，不是「真正独立」。算法与线上契约按设计
保持不变，所以它仍是派生实现（MIT + 作者许可，这是允许的）。真正与度量无关、值得单独做的工程
收益是另外两件，而且它们排在重写**之前**：

1. 把这段压缩文本拆成有名字的可读结构（原先有一行是 **3210 个字符**）；
2. 给它装上能真跑的测试。原先守这 40 行的**全是逐字拼写断言**（例如
   `contains("boundedInteger(p.chunk_size,4194304,65536,8388608)")`），等于「不许改」——
   「冻结」那句话的技术依据就是这个。现在用 `node` 跑 `app/tests/js/frida-agent-harness.mjs`：
   把载荷从 Java 源码里抽出来，用桩（`Process` / `Memory` / `File` / `ptr` / `NativePointer` /
   `Interceptor` / `Stalker`）加载，按载荷自己的信箱协议发命令、读响应，对返回值做断言（163 项）。
   局限也写在那个文件里：它**证明不了**真实 Gadget 载入、`Interceptor`/`Stalker` 钩子与真实
   `Memory.scan` 的行为 —— 那要在 IQ 沙箱里跑起来才能确认。

**重写后它是怎么嵌进 Java 的**：一份 text block（JS 原样可读）。曾经想过把它挪进 `assets/`
或 `res/raw/` —— 那样这个文件会好看很多，但 `tools/provenance.sh` 只比对 `.java`/`.kt`，
载荷会**从度量里直接消失**：一行都没重写，数字却变好看。这条被明确拒掉了，理由与上面那句
「冻结资源」是同一个：不能让度量对象自己消失。

**这套测试上手就抓到两件事**（都不是重写引入的，是原版代码里就有的）：

1. `jsonSafe` 在 eval 路径上被套了两次，于是大数组的 `[+N more]` 标记里的数字永远显示
   `[+1 more]` —— 第二次看到的是**已被截断**的 2049 项数组，算出 `2049 − 2048 = 1`，
   而真实被截掉的是几百项。原先的断言把这个错**钉成了契约**（写的就是 `[+1 more]`）；
   现在用两个不同规模（3000 / 5000 项）断言，一个规模只能证明「有个数字」，
   两个规模才能证明「数字是对的」。重写时去掉了重复那一层。
2. `frida_eval` 的 `timeout_ms` 只能打断**会让出事件循环**的脚本：同步的 `while(true){}`
   会把 guest 卡死，连脚本自己的 deadline 都轮不到（JS 没有抢占）。第一次跑 harness 就是
   这么满载 spinning 了五分钟。载荷里现在把这个限制写在明处，harness 断言的是
   「可达成的那一半」。

### 画布两层：从「一行塞十几个语句」改成有结构的写法

`UiCanvasStore`（持久化 + 校验 + 撤销重做）与 `UiCanvasController`（把文档套到 View 树 /
在一份副本上执行操作表）是全工程最后两处「一行塞十几个语句」的写法：最长一行 **507 字符**，
`validated()` / `applyProperties()` / `applyOperations()` 各自是一整行。

改法与代价：

- 改成具名方法 + 常量表（`SETTABLE` 白名单、`DEFAULT_SLOTS`、`PALETTE_KEYS/VALUES`、
  三档取值范围），并把**每一条边界为什么存在**写在注释里 —— 它们决定「模型能造成多大破坏」；
- 两处**不变式**故意保持不变，因为它们各自是一个决定而不是代码风格：`load` 读到坏文档
  **退回默认值**（抛错会让画布功能整体崩溃），`save` 则**抛**（用户明确要求保存时静默丢改动
  更糟）；`remove` 只把节点标成 `removed` 而不真删（预览要可逆）；
- 顺手修掉一个**潜在空指针**：原先的 `index()` 对非对象元素直接 `.optString()`，
  一份手写的畸形文档会让它 NPE，现在按「没有这个 id」处理。

**这两处此前一行测试都没有。** 现在有 `app/src/test/.../UiCanvasLogicTest.java`（19 个用例，
纯 JVM 单测）覆盖「写错了也编译得过、只在运行时表现为『修改好像没生效』」的分支：越界值是
忽略还是报错、`move`/`reparent` 与 `set` 指向不存在的 id 时各建什么样的节点、`add` 撞上已有
id 是否静默覆盖、白名单之外的字段是否会被写进文档、80 个操作的上限是不是真的 80（而不是 79）、
入参文档有没有被改。用变异测试核对过这些用例不是摆设：`MAX_OPS` 改 79 → 抓到；白名单里删掉
`text` → 抓到（2 个用例）；`remove` 改成真删节点 → 抓到。`apply(View,…)` 与
`load/save/undo/redo` 需要真实 View 树与 `SharedPreferences`，不在 JVM 单测范围里 ——
这一点写在测试文件的类注释里，不假装覆盖了。

---

## 重写批次汇总

各批的过程记录曾经各占一节（内容互相重复），现在合并成这张表。数字都是**当时的**，现行值请跑
脚本；「实质改动」那一列才是值得读的部分。

| 批次 | 范围 | 当时的重合变化 | 实质改动 |
| --- | --- | --- | --- |
| **A** | 删掉的死代码 | 10132 → 9592（-540） | 删 4 个全仓搜不到引用的文件（`MarkdownRenderer` 265 行、`ToolActivityGrouper` 91、`ZhiDocumentsProvider` 126、`FloatingOverlayService` 130）。顺带去掉 `SYSTEM_ALERT_WINDOW` 权限 —— 它是已删的 `FloatingOverlayService` 的唯一使用者，留着一个没有使用者的「可在其它应用上层显示」对用户只是无谓的授权请求。度量工具的自检立刻报出「PAIRS 里的本工程文件不存在」—— 删文件后忘了同步配对表，正是这类自检要防的静默失真 |
| **B** | `api/` 协议层 | 各文件 88%~100% → 16%~40% | 每一条都是**先写契约断言、再改实现**。`stripTrailingSlash` 原来 4 个文件各有一份（三个 provider 各私有一份 + 端点类一份）→ 只剩 `ApiEndpointResolver` 里那一份；它们其实并不完全一样（Anthropic 那份遇 `null` 会抛 NPE，另两份返回空串），合并时取了后者，因为没有任何调用点依赖那个 NPE。另外两个「协议名 → 实现」/「协议名 → 端点」的常量集合合并成枚举 `ApiProtocol`（协议名是**存盘格式的一部分**，写错一处不会编译失败，只会表现为报文格式对不上；收进枚举后 `switch` 要求列全，将来加协议忘分派会直接编译不过） |
| **C** | 数据类与 Agent 工具（40 文件） | 8632 → 7917（-715） | 值对象字段名一律不动（写进了会话 JSONL、设置与模型提示词）。结构改动：`PlanWorkflowState` 把「哪个阶段允许哪种操作」收成统一前置检查（`requireStatus`/`requireWorkflow`），并把时间戳改成**严格单调**（取「当前时间」与「上一版 + 1」的较大者 —— 系统时钟被回拨时，原写法会让新快照看起来比它替换掉的那版更旧，而界面按时间戳判断版本）；`SessionConfig.copy()` 先把 volatile 字段落到局部变量再拷（原写法两次读之间可能被别的线程换掉，拷到「半新半旧」的组合）；`ToolExecutionResult` 六个工厂方法收敛到一个私有构造路径；`ToolSchemas` 用一处 `with(...)` 收掉 `JSONObject.put` 的受检异常（原来 45 行里有 5 段几乎相同的 catch）；`PermissionModePolicy.capSubagent` 把嵌套四层 `if` 换成「父级模式 → 子代理可以要求哪些模式」的表，表里每行都能从一条原则推出（**子代理只能收紧、不能放宽父级权限**），而原嵌套 `if` 读不出这条原则。工具名、schema 属性名、输出格式与错误文案全部逐字不变；`ToolRegistry` 的构造从平铺的 `register(...)` 改成按用途分组的清单，**顺序仍然固定**（顺序决定提示词内容，从而决定上游的提示词缓存能不能命中） |
| **D** | Agent 主循环 + `ContextCompactor` | `ZhiCodeEngine` 1233 → 492；`ContextCompactor` 388 → 195 | 主循环从三百多行双层 `try` 拆成 `runAgent`（异常分类与收尾）→ `runTurnLoop`（`maxAgentTurns` 计数）→ `runOneTurn`（请模型回一条）→ `{finishAssistantTurn, runToolPhase → runToolCalls}`；预输入队列与两个打断标记搬进 `SteeringQueue`。`ContextCompactor` 用**基线文件**钉住模型可见输出（见下节）。顺手修掉一处死变量：`repairToolHistory` 第一遍算了三个集合，其中 `allCalls` 从头到尾没被读过，整个第一遍其实只是在问「哪些调用先出现、之后才出现同 id 的结果」，现在就是一个方法 `findPairedToolCallIds` |
| **E** | 终端执行器 / 任务存储 / 提示词装配 / 存储层 | 见各文件 | `TaskStore`：一任务一文件、id 高水位文件、进程内锁 + 文件锁。`SystemPromptBuilder`：规则清单从字符串相加改成数组。`SessionStore` 三处收敛：三种读法收成一个 `scan(file, strict)`（原先宽容读与严格读是两段几乎一样的逐行循环；合成后 `strict` 不是零散布尔参数，而是「这份数据我能不能安全重写」这件事本身）；两种写法收成一个 `appendLines(file, lines, durable)`（区别只有是否 fsync：消息高频写入，改备注低频且重要，原先两份方法体完全重复）；整文件替换改走 `AtomicFiles`，本类只保留特有的一步「替换后恢复原 mtime」。固定字符串收进 `RowType`/`Role`/`Block`/`Field` 四个常量类（`"payload"` 原先出现十几次，改字段名时漏掉的那处只表现为「某类历史读不出来」）。`ApiSettingsStore`：原先 `loadGlobal()`/`saveGlobal()` 是两份各 25 行的键名清单，必须一直保持一致 —— 而「加了新设置但只加一半」的后果是「设置能存进去、重启后消失」，不报错、不崩；现在只有一张 `SETTINGS` 表，读写同源，默认值直接取 `SessionConfig` 的字段初值。prefs 键名与值格式一字未改（键名是持久化契约） |
| **F** | 终端面板改用 Compose | `TermuxTerminalPane` 565 → 100；`UiMotion` 104 → 0（文件已删） | 这一块是**全工程最大的单个残留**（583 行里 565 行相同，95.4%）。拆开看：**外壳**（工具栏、左边缘手势、会话抽屉、两行扩展键、快捷动作与重命名两个对话框、主题刷色、`termux.properties` 读取）是 IQ Code 自己写的界面代码，重写成 Compose；**终端本体**（Termux 上游 `TerminalView`，2453+1297 行，属那些 Termux 上游代码）仍由 `AndroidView` 承载 —— `TerminalPane.kt` 里早就写明「不把 TerminalView 重写成 Compose」，这个判断继续有效。**另一个发现**：`UiMotion.java` 的全部 25 个调用点都在这个文件里，外壳一改它就是死代码，于是连同 104 行重合一起删除；它的职责（进场动画、按压反馈、抽屉滑动）现在由 `ZhiMotion` + Compose 的 `AnimatedVisibility`/`animateDpAsState` 承担 |
| **H** | 4 个「重合率 38%~55%」的小文件 | 291 → 271（-20） | 结论与预期相反：**这四个文件已经没得改了**。相同行主要是枚举成员、`public final` 字段、方法签名、状态判断、内部类声明、`import`、`try/finally` 骨架。实际只做了两件事：**① 去掉同一件事的两份写法** —— `PlanWorkflowState` 六个推进操作里有五个不动计划正文却各自把 `planFile, planText` 写了一遍 → 收成一个三参数的 `step(...)` 重载；`copy()` 与 `restore()` 原本各写一遍九个字段 → `copy()` 改为委托；`BashCompletionCoordinator` 里 `publishControl` 与 `publishProcessExit` 那 5 行「定下退出码」逐字相同 → 收成 `resolve(code, from)`，「先到者为准」的判定从此只有一处；`PlanApprovalGate` 两个 catch 块做同一件事 → 合成 `catch (InterruptedException \| RuntimeException failed)`（精确重抛会原类型抛出，签名已声明 `InterruptedException`，合法）。**② 删掉确认没人调用的 API**（`withPlanText`/`withPlanFile`/`awaitApproval`/`activeCount`）。`pendingRequests()` **留着** —— 它是「批准卡片必须能被重新拿到」这条要求唯一的落点，在注释里写明它现在没有调用方，而不是删掉。**顺带修掉分类器一处真误收**：`public static final Status IDLE = Status.IDLE;` 原先因带赋值号被算成语句行，它是枚举成员那种写法，现在归声明桶。行数反而涨了（注释变多），与批 D 一样是刻意的 |
| **I** | 沙箱宿主层 14 文件 | 563 → 533 | 这些文件全都改过名（`SandboxGuestDebug` ← `SandboxProcessDebug`、`ZhiSandbox` ← `IQSandboxEngine` 等），必须走 `PAIRS` 配对表，按路径找不到。**结果决定了这一批只做了一个文件**：`SandboxGuestDebug` 的 13 分支 switch —— 每个 case 都做同一件事（算一个值、放进同一个信封、`break`），同一件事写十三遍的代价不是长度，而是「加一个动作要动四行、还得自己找对位置」；现在是一张 `Map<String, Route>` 路由表（键名 + 算法），加一个动作只加一行（该文件相同行 119 → 89）。`SandboxShell` 与 `SandboxKeeper` 的相同行**全是括号与 import 或注解** —— 按现在的口径它们的语句行是 **0**，这正是「这两个文件没什么可改」的形式化说法。这一层真正剩下的算法只有十来行，且都是绕不开的：`System.load(canonical);`、`Runtime.getRuntime().gc();`、`while ((b = in.read()) != -1 && b != 0) out.write(b);` |
| **数据层** | 5 个存储类 | 见下 | `PlanStore` 的重合率偏高需要解释，否则这个数字会误导：71 行相同里，9 行是 `import`、16 行是单独一个右括号、2 行是注释标记；剩下 44 行「实质相同」主要由两类构成 —— **必须保持不变的公开 API 签名**（被 `ZhiCodeEngine` 调用）以及原子写入的固定步骤（写 tmp → fsync → `Os.rename`）。**这份文件本来就短而且是正确的，没有为了压低数字去改写正确的代码。** |

### 批 F 的两个补充（值得单独记）

**宿主对 Compose 的接口是「不可变快照 + 变更回调」而不是 `StateFlow`**：宿主是 Java，而
`StateFlow` 是 Kotlin 类型，从 Java 构造要绕到 `StateFlowKt` —— 那等于让最底层的宿主反过来
依赖界面框架。回调只发**低频**变化：`onTextChanged`（每次按键回声都触发）只负责让
`TerminalView` 重画，**不**通知观察者，否则每敲一个字符就要重建快照并驱动一整轮重组。

**外观只动了一处**：外壳配色改为跟随深浅主题（原先写死深色档）。深色档色值与改动前
**逐字节相同**（就是 `applyPaletteValues` 的那张表），浅色档启用了原作者写下但从未走到过的
那张。终端的 ANSI 调色板**没动** —— 让它跟随主题是另一次可见变更，不夹在结构重写里做。

验证：新增 `app/tests/TerminalPaneContractTest.java`，钉住三件「改坏了不报错、只会在真机上
表现为终端不好用」的事：12 个扩展键的转义字节（用解码后的字面量做**相邻性**检查，所以 ESC
与 TAB 的序列被互换会被抓出来）、11 项快捷动作的标签与顺序（调用方按下标分发，重排会让
「字体变大」点成「杀掉 shell」）、四个修饰键的读后即清语义。

UI 行为已按 A–H 清单跑了两轮**真机验收**：第一轮发现并修掉 H3（旋转后终端一片空白），第二轮
复验无遗留；覆盖范围含反复切标签与开关抽屉、改名、关闭会话、**切走再切回会话仍在**这个历史
bug 回归点、剪贴板等。H3 的根因、第一版修法为什么修反了、以及最终三层修法记在
[`sandbox-host.md`](sandbox-host.md)（其中「`key(...)` 重建节点与复用同一个 View 实例互斥」
是一条可复用的教训）。**仍然没有验证**：任何真实模型请求（会话内从未配置 API Key）。

---

## 模型可见文本：用基线钉住

`SystemPromptBuilder` 与 `ContextCompactor` 产出的东西几乎全是**给模型看的**（系统提示词、
摘要提示词、压缩后的上下文包裹格式、被缩减过的历史），而改坏了不会报错 —— 只会让 agent 的
行为悄悄变差：那些英文句子决定它会不会先确认再动手、会不会声称自己做了没做的事。所以改写它们
属于「改动行为」，不能和「改写实现」混在一起，办法是**用程序验**，不是靠看：

| 文件 | 办法 |
| --- | --- |
| `SystemPromptBuilder` | 把改动前后两个版本的 `build()` 各自编译成一个**不依赖 Android** 的独立程序（把四个 `config.*` 字段访问换成固定值），用同一组输入打印提示词再比较：`old 12381 bytes / new 12381 bytes / cmp → 完全相同` |
| `ContextCompactor` | 先写一个一次性探针，把**重写前**的实现对同一组输入（覆盖 text / thinking / tool_use / tool_result / image 五种块）的完整输出原样 dump 出来，存成 `app/src/test/resources/compaction-prompt-baseline.txt`；重写之后由 `ContextCompactionTextTest` 重新生成同一份 dump 并**逐字比较**。这条断言长期保留 —— 以后再动这个文件，提示词改一个字都会红。基线里存的就是那些句子本身，可以直接查阅，不需要相信任何人的描述 |

`SystemPromptBuilder` 还顺带修掉一个更隐蔽的毛病：`SandboxIntegrationStructureTest` 与
`FridaDeadlockRegressionTest` 原先都对 `SystemPromptBuilder.java` 的**源码**做 `contains`，
于是「换行打在哪个位置」也变成了契约 —— 重排之后它们失败了，而提示词输出并没有变。两个测试
现在改成先从源码里抽取双引号字面量、拼回内容再比较：检查的是「这个文件最终会输出什么文字」，
而不是「作者把这些字打在哪儿」。

---

## 这个数字的边界，与它犯过的错

### 它是字面判据，不是法律判据

「净相同行 = 0」只说明**没有一行与 IQ Code 逐行相同**。它说明不了：

- 算法与结构没被沿用（重写一段代码的字面不会消除派生关系）；
- 也不构成「无需署名」的依据 —— 本工程保留 IQ Code 的 MIT 原文与致谢，依据是
  **原作者许可 + 自愿致谢**，与这个数字无关。

写下来是因为这个数字是头条结论，最容易被读成「所以我们完全没用过它的代码」。不是这个意思，
前面「为什么不必是 GPL」一节也从不建立在这个数字上。

### 这个数字曾经是错的（记下来，因为它会再次发生）

上一版这里写的是「真正属于 IQ Code: 8644 行」。那个数字**低估了 1488 行**，原因是
`tools/provenance.sh` 的路径映射只在「文件名不变」时成立：一个文件如果改了类名、因而
**文件名也变了**（`ZhiCodeEngine` 对 `IQCodeEngine`），它既被跳过名单排除、又不在配对表里，
于是**不出现在报告的任何一行里**，从总数上静默消失。被这样漏掉的四个文件里，
`core/ZhiCodeEngine.java` 是全工程最大的单文件（1353 行、实测 1238 行相同、99.2%），
却因为这条漏算在表里显示为 **0** —— 按这张表排批次会**正好把最大的一块漏掉**。

现在脚本加了两条自检，都是「宁可吵闹也不静默」：① 跳过名单（`RENAMED_BASENAMES`）与配对表
（`PAIRS`）必须一致，名单里有而配对表里没有的直接打警告；② 配对表指向的本工程/原版文件必须
真的存在，写错名字会报出来。两条都做了反向验证：删掉 `ZhiCodeEngine` 的配对 → 报「静默算成
0」；把原版文件名改成 `IQCodeEnginTypo.java` → 报「原版文件不存在」；恢复后无警告。

### 这个范围里有一批文件不该参与

重合行数最大的那些文件里有相当一部分**不是 IQ Code 的代码**：
`com/termux/terminal/TerminalEmulator.java`、`com/termux/view/TerminalView.java`、
`WcWidth.java`、`TerminalBuffer.java` 等是 **Termux 上游**（`terminal-emulator` /
`terminal-view`，Apache-2.0），由脚本归入「Termux 上游（非 IQ Code）」一类。它们的正确做法是
原样保留并履行 Apache-2.0 义务（已在 `NOTICE` 中声明）—— **重写它们既没有意义也是错的。**

### 分类器曾经算错 217 行

这是度量工具出过的**第二次**同类错误（第一次是路径映射漏算），而且这次错得更隐蔽：数字总量
没变，只是桶之间分错了。原来的语句规则是「命中关键字 / 有赋值 / 调用了 11 个白名单方法名
之一」，下面几类一条都不满足，于是全都落进了「声明」桶：

| 混进声明桶的行 | 为什么原来没被算成语句 |
| --- | --- |
| `try {`（64 行）、`break;` / `continue;`（9 行）、`synchronized (x) {`（12 行） | 不在关键字表里（表里只有 if/for/while/return/throw/catch/switch/case/else/new/instanceof） |
| `/**`（27 行） | 骨架规则只认 `^*`，不认 `/**` |
| 无白名单方法名的调用语句（`maybeAutoCompact();`，105 行） | 无参调用没有点号，11 个白名单方法名一个都不匹配 |
| `x++;`（8 行） | 自增没有赋值号 |

后果不只是「数字不好看」：文档当时据此写下「声明桶基本不能归零」这个结论，而它是建立在一个
**混了 217 行语句的桶**上的。修法的关键不是把五类逐个打补丁，而是写下一条可判定的规则：
「以标识符加点号或括号开头、以分号结尾」= 一条调用语句（要求紧跟点号/括号，是为了不误伤
`void onUsage(long a, long b);` 这种签名 —— 那里 `void` 后面是空格）。

**教训与「规则只此一份」是同一个，但方向相反**：那条讲的是同一份规则被写两遍会漂移，这条讲的
是**一份规则自己要能被人工核对，而核对的对象是行，不是总量**。这也是为什么每次改分类器都要
重跑四桶并**重新读一遍**某一桶的全文：数字总量不变的时候，没有任何东西会提醒你桶分错了。

### 口径变动之后，所有引用它的数字都要重算

同一个文件、同样 481 行共享，旧分类器（`CLASSIFY` 688 字节）给出 `127/125/186/43`，当前
（1043 字节）给出 `174/76/188/43`。所以「138 → 127」这类历史对照也跟着错了：口径变了之后是
**138 → 174**（不是因为多了代码，而是那些行本来就该算语句）。只改正文、不改表，就会留下一张
看起来权威、实际来自另一个口径的表。本文件因此**不再抄逐文件的数字**，只写方法与判据。

### 度量口径本身也变过一次

早先的提交说明里用过「归一化后**排序**再取交集」，它会把两文件里出现在不同位置的相同行也计入，
因此同一个文件会得出偏高的数字。`diff` 对齐（本文档与脚本采用）只在它们真的落在同一位置时才
算。两种数字不可直接比较，**以脚本为准**；差别集中在 `}`、`import`、空行这类随处可见的行上 ——
排序后它们必然「相同」，对齐后只有当它们真的落在同一位置时才算。

---

## 仍然剩下的

```bash
PROVENANCE_ALGORITHM=1    bash tools/provenance.sh   # 按语句行排序（排下一批看这个）
PROVENANCE_PER_FILE=1     bash tools/provenance.sh   # 按重合行数排序（历史口径，仅作参考）
PROVENANCE_DECLARATIONS=1 bash tools/provenance.sh   # 声明桶全文（判公开面能不能收窄）
```

**按重合行数排，但排下一批不该按它排。** 重合率的分母是**重写之后**的行数，所以注释写得越细、
同一处残留显示得越夸张；它又**不区分**相同的是算法还是签名，于是会系统性地把「公开面大、逻辑
少」的数据类排在最前面 —— 而这类文件恰恰最没得改（批 H 就是这么被排出来的，四个文件实际各只有
4~6 行可动）。换成按**语句行**排序之后，顺序与人工判读一致得多：前几名是 `ZhiCodeEngine`、
`TaskStore`、`ContextCompactor`、`OpenAIResponsesProvider`、`TermuxShellExecutor` 这些真正还
留着「读起来像原版」的判断与动作的地方，而批 H 那几个文件退到了中段。

各类残留的性质：

- `ZhiCodeEngine`：余量主要在 Listener 接口与用户可见文案；
- `SandboxFrida`：余量主要是内嵌 Frida 脚本的大串（**已重写**）；
- `TermuxTerminalPane`：余量是 31 行 `import`、若干 `}`、字段与构造器声明，以及**只能在 View
  一侧写**的 PTY 调用 —— `new TerminalView(getContext(), null)`、`view.attachSession(attached)`、
  `view.post(...)` 里的三重校验、`JSONArray` 解析骨架。这些相同不是因为抄，而是因为它们就是
  「在 View 里驱动这个上游渲染器」的唯一写法。文件行数涨了（592 → 1056）是刻意的：注释写明了
  每处不许改的原因；
- `SandboxGuestDebug`：余量是动作名与 JSON 键（协议）；
- 小文件的重合率有**下限**：剩下的常是 `package`、`import`、类声明、`return base;` 这种
  「一行只干一件事」的行。判断「改没改写」不能只看百分比，要看**算法与结构**。

**`SandboxFrida` 里那段 agent JS 的载荷行为是刻意冻结的**：`rpc.exports`、信箱文件名、ready
标记是 Java 与脚本之间的协议边界，改一侧必须同步改另一侧；它的行为细节（有界扫描、部分失败可
返回、legacy scanSync 重写、eval 沙箱与超时）都有回归断言盯着，重写只会引入行为漂移。**改过的
只有名字**：注入对象由旧品牌标识改为 `Zhi`（`Zhi.emit` / `Zhi.hooks` / `Zhi.scan`），磁盘文件名
改为 `libzhifrida.so` / `libzhifrida.config` / `zhi-agent.js`。名字不是行为，但它会同时出现在
工具 schema 与系统提示词里，所以三处必须一起改。

**Agent 运行时仍在进行中**，是当前比例最高的残留区。**目标是把净相同行降到 0，目前尚未达到。**

---

## 其余需要记住的坑（散落各批的教训）

| 坑 | 内容 |
| --- | --- |
| **`Set.of` / `List.of` 在 Android 上不可用** | 它们是 Java 9 API，Android 要 API 30 才有，而本工程 `minSdk 24` 且**没有开启核心库脱糖** —— 编译会通过，在 Android 7~9 的设备上一运行就 `NoSuchMethodError`。已全部换成 `Arrays.asList`。**编译器和单元测试都发现不了**（单元测试跑在 JDK 上，那里什么都有），所以它值得单独写在这里 |
| **javadoc 里的正则片段** | `GlobTool` 的 javadoc 里写了 `(?:.*/)?`，其中的 `*/` 提前结束了注释块，后面整段代码被当成类体外面 —— 报错是「illegal start of type」加一串看不懂的错位信息 |
| **一处从未被置为 true 的标记** | `steeringToolInterrupt` 与 `steeringModelInterrupt` 在引擎里只有 `getAndSet(false)`、`get()` 和 `set(false)` 三种读法 —— **没有任何地方把它们置为 true**。于是「模型请求被预输入打断」那条分支（它会保留已经流出来的半截回复、通知界面、然后按新输入继续）永远走不到，「工具执行因为用户纠正而中断」那条错误文案也永远走不到。**没有删掉**，而是把它们搬进 `SteeringQueue` 并在类注释里写清现状 —— 理由：界面（`ZhiEngineController`）已经实现了 `onResponseInterruptedBySteering`，说明这条路径是被期望存在的；删掉标记等于把这个功能的存在证据一起删掉。代价是一个读者可能误以为它在起作用 —— 所以那句话必须写在那儿 |
| **行数涨了，而且是刻意的** | `ZhiCodeEngine` 非空行 1245（原版）→ 1250（重写前）→ 2094（重写后），其中注释 46 → 48 → **442**，代码 1199 → 1202 → 1652（多了约 38%，注释多了近十倍）。两个原因：原先大量「一行三个语句」的紧凑写法在重写时必须展开（紧凑写法正是与原版逐字相同的那部分）；以及这次把**行为契约**写在了函数旁边。三条最容易踩坏、而踩坏了不一定报错的地方：① `continue` 在 `for` 循环里会执行 `turn++`，拆成「返回 false 表示再来一轮」之后计数必须由调用方推进 —— 写错的话 `maxAgentTurns` 会变成一个不生效的上限；② `onToolBatchCompleted` 在 `finally` 里，某条工具抛异常时也必须发出去，否则界面上那一批的进度条会一直转；③ **工具结果必须写回历史，即使后面的工具被取消** —— 丢掉等于让模型以为那些工具从没被调用过，它会重做一遍，而重做的可能是有副作用的操作 |
| **真机事故：暂存目录删不掉，报错却说是「建不出来」**（已修，待真机复验） | 报错 `初始化失败：Cannot create staging prefix: …/files/usr-staging`，可同一份报告里 `PREFIX 存在: true (8 项)`、`bin 条目数: 424`、`bash 可执行=true` —— 环境**是装好的、能用的**；两个本该在装完时清掉的残留目录 `usr-staging`、`usr-backup` 都还在。所以原因**不是**「建不出来」，是**删不干净**：`Os.symlink` 建出的一部分链接**悬空**，而 `File.exists()` 与 `File.isDirectory()` **会跟随符号链接** —— 悬空链接的 `exists()` 是 false，老实现把它当成「不存在」直接 `return`，这些链接**从来没被删过**；父目录因此永远非空、永远删不掉，下一轮 `mkdirs()` 返回 false（对已存在的目录也返回 false），失败就被报成了「建不出来」—— 一个把原因说反的消息。不变更真机就复现出来了（JVM，用一个与老实现逐行等价的函数）：那两条链接其实**都删得掉**，只是从没被尝试过。修法：删除逻辑移到 `FileTree`，**不跟随**地看待每一个目录项（真机走 `Os.lstat`，API 21 就有，而不是要 API 26 的 `java.nio.file` —— 后者是本工程明确不用的）；只有**真目录**才递归（顺着符号链接递归进去会删掉链接指向的目录里的内容，等于删了别人的文件，链接本身 unlink 就够）；删除失败不再静默吞掉；`install()` 先确认「上一轮残留真的删掉了」才 `mkdirs()`，删不干净时消息里带上残留项。单测 `FileTreeTest`（4 项，跑真 JVM 文件系统与真符号链接）**只覆盖递归与「不跟随」这两条共用规则** —— 变异核对过两次：把递归判定改回 `file.isDirectory()` → 1 失败；把存在性判定改回 `file.exists()` → 1 失败。**尚未验证**：修好之后真机上那两个残留目录是否真的被清掉、初始化是否真的能过 —— 只能等真机复验，上面那些代码级证据都**不能**替代它。（`EnvDoctor` 里那句「前缀等长 不通过」是同一类噪音，它按「等长替换」时代的假设写的，记在这里、暂不改。） |
| **改名与旧数据（只改名，不改行为）** | 阶段 0.5 结束时源码（`app/src/main`）里旧品牌标识已**一个都不剩**：`iq_code_*`、`<iq_internal_continue>`、`.iq`、`IQ.md`、`libiqfrida*`、`iq-agent.js`、`ctoken.top`、`com.iqge`、`iqcode-*` 等。**代价是明确接受的**：为了不留任何旧名字，历史上那几处「只读兼容」被一并删除 —— 从更早版本升级上来时，旧名字下的设置、API Key 与旧会话里的内部续跑标记不再被识别。取舍理由是不留旧品牌标识优先。顺带修掉两个真 bug：`iq-patch-deb` 这个脚本名从来没存在过（正确名由 `BRAND_SLUG` 派生）；`KeepAliveService` 写的是旧 prefs，导致关掉保活不生效 |

---

## 署名：为什么仍然保留（**自愿**，不是义务）

`THIRD-PARTY-LICENSES/IQ-Code-MIT.txt` 与 `NOTICE` 第 1 节对 IQ Code 的署名，
**不是尚在生效的许可义务**：

- **原作者**是版权人，他除了 MIT 之外**另外明确许可**本工程改写、改名与发行，并**同意
  不强制**要求随附其版权声明（由项目方转述；时间与形式待补记录）。版权人可以豁免自己许可里的
  条件，所以「必须随附声明」这一条**已经被解开**。
- 理论上现在就可以把那一节删掉。我们选择保留，理由与法律无关：本文件的整篇、以及
  `tools/provenance.sh` 的全部作用，都是**拿本工程与原版逐行比对** —— 归一化表里写着
  `com.zhizhu.zhicode → com.iqge`，还有一张 19 行的 `SandboxGuestDebug ← SandboxProcessDebug`
  配对表。派生关系在这个仓库里是**自证的**，删掉署名只会变成「一边处处与原版比对、一边声明
  毫无关系」；而那些逐行相同的代码删不删署名都一样留着。**保留署名不减少、也不削弱任何关于
  「独立」的主张。**

`app/tests/LicenseNoticeStructureTest.java` 因此把「MIT 版权行必须逐字保留」这条正向断言改成了
两条更贴合现状的断言：**许可记录本身必须在**（写清内容、来源与范围），且必须说明保留署名是
**自愿致谢**。记录是新的最弱一环 —— 它一旦丢失，「我们为什么可以这样做」就只剩一句无法核对的
话。

---

## 怎么重新验证本文件的每一条

```bash
# 1. 各模块是否都带了许可文件
find . -maxdepth 2 -name LICENSE -o -maxdepth 2 -name NOTICE | grep -v '/build/'

# 2. 归属度量（需要本机有原版 IQ Code）。默认输出就够看结论：
#    汇总表 + 四桶构成 + 可扣行 + 净相同行 + 漏算自检（有 ⚠ 说明数字被低估）
bash tools/provenance.sh
#    叠加口径：
PROVENANCE_COMPOSITION=1 bash tools/provenance.sh   # 语句行去重种数 + 语句行全文
PROVENANCE_SHAPE=1      bash tools/provenance.sh    # 语句行还能收敛多少（多行模板，给上限）
PROVENANCE_SHAPE=1 PROVENANCE_SHAPE_UNIQUE=1 bash tools/provenance.sh
                                                    # 再加「只出现一次的形状」逐行清单
PROVENANCE_DECLARATIONS=1 bash tools/provenance.sh  # 声明桶全文
PROVENANCE_LITERALS=1     bash tools/provenance.sh  # 字面量桶拆两份：可扣协议串 / 其余文案
PROVENANCE_ALGORITHM=1  bash tools/provenance.sh    # 按语句行排序（排下一批看这个）
PROVENANCE_PER_FILE=1   bash tools/provenance.sh    # 按重合行数排序（历史口径，仅参考）
#    审计文件 build/provenance-protocol-strings.txt 每跑一次都重写，分三段
#    [files] / [position] / 没有被扣的 —— 净相同行的每一处扣减都能在里面找到出处
```

**语句行全文以前是打不开的**：它原先写在 `mktemp -d` 建的工作目录里，而脚本退出时会 `trap`
把它整个删掉 —— 于是「供逐条核对」是一句空头承诺，打印出来的路径在下一行就已经不存在。现在
固定写到工程内的 `build/` 下，**按文件分段**，每段前一行 `=== 相对路径`（没有文件名的清单没法
核对，「这句话到底是从哪来的」是看它的唯一理由）。

```bash
# 3. 结构测试（含 LicenseNoticeStructureTest 守着这些文件的存在）
bash test-source-no-build.sh

# 3b. 只跑内嵌 Frida 载荷那条行为测试（改载荷时用，比整套快）
node app/tests/js/frida-agent-harness.mjs .
# 3c. 「载荷嵌入得对不对」不由上面那条自己说了算：这一条把真正的 Java 编译起来跑一次
#     （text block 的脱缩进与转义由 Java 自己算），再用它抽出的载荷跟 harness 抽的逐字节比。
#     没有它，两边可以各自「通过」而没人发现它们抽的不是同一份东西。
node app/tests/js/frida-payload-embedding-check.mjs .

# 4. 行为测试（与第 3 条互补，不是替代）
./gradlew :app:testDebugUnitTest
```

node 不在时会**明确失败**，不静默跳过 —— 静默跳过等于这条守卫不存在。

**两套测试的分工**（判断标准：改动「写错了也不会编译失败」→ 需要 JVM 单测）：

| | `app/tests/` + `test-source-no-build.sh` | `app/src/test/` + `testDebugUnitTest` |
| --- | --- | --- |
| 层次 | 源码文本断言 | 真跑逻辑 |
| 依赖 | 只用 JDK 单文件源码模式，零依赖 | `org.json` + JUnit |
| 速度 | 秒级（不编译工程） | 十几秒（要走 Gradle） |
| 能防 | 漏分支、契约被改、声明被删 | 计算结果错、边界条件错 |
| 不能防 | 逻辑写错但不改结构 | 结构性遗漏 |

各项许可的**完整原文**：

| 组件 | 许可 | 原文位置 |
| --- | --- | --- |
| BlackBox (Bcore / black-reflection / compiler) | Apache-2.0 | `Bcore/LICENSE` 等三个模块目录，以及 `THIRD-PARTY-LICENSES/Apache-2.0.txt` |
| Termux terminal-emulator / terminal-view / libtermux.so | Apache-2.0 | `THIRD-PARTY-LICENSES/Apache-2.0.txt` |
| termux-shared `TermuxConstants.java` | MIT | `THIRD-PARTY-LICENSES/MIT.txt` |
| IQ Code | MIT (© 2026 IQge) | `THIRD-PARTY-LICENSES/IQ-Code-MIT.txt`（原作者已另许可改写与不强制署名，保留为**自愿**致谢，见「署名」一节） |
| Termux bootstrap（bash / coreutils / apt / …） | 各自许可 | 各自程序与 `termux-packages` |
| 蜘蛛自身 | MIT | `LICENSE` |

---

## 发行前还需处理

1. ~~**把版权主体填成真实名称。**~~ **已完成**：`LICENSE` 与 `THIRD-PARTY-LICENSES/MIT.txt`
   的版权行现在是 `Copyright (c) 2026 zhizhu0002`。`LicenseNoticeStructureTest` 守着它不再是
   占位写法。
2. **确认 bootstrap 的源码对接收者可达**（见「一处仍需自己确认的事」）。
3. **`NOTICE` 提到但不覆盖**：通过 Gradle 引入的 Miuix、AndroidX、Compose 构件会随 APK 一并
   分发，其许可原文随构件本身提供。若要做正式发行，建议在「关于」页里放一份可滚动查看的完整
   许可列表。
4. **「开源前的许可复核」第 2 点那个疑点是开放的**：血统更上游的原始项目当时用什么许可，今天
   查不到（文件被作者删了）。这一条**不会**因为本文件其它部分完备而消失。
