# 许可与归属

本文件记录**这些结论是怎么得出的**，而不只是结论本身。许可判断的价值在于可核对：
如果某天上游改了许可，你应当能拿这里的每一条重新验证一遍。

结论性的清单在 [`NOTICE`](../NOTICE)，许可原文在 [`THIRD-PARTY-LICENSES/`](../THIRD-PARTY-LICENSES)。

---

## 一句话结论

蜘蛛自己的代码用 **MIT**。发行物里另有 Apache-2.0 组件与一批各自许可的
Termux 二进制程序，但**本项目整体不必转为 GPL**。

代码层面的独立性看的是**净相同行 2118 行**：逐行相同 4406 行，扣掉 2063 行骨架与
225 行跨组件协议串 —— 后两类不是「别人的代码」，任何人在这个需求下都会那么写。
这个数的定义、算法与它可能被做手脚的地方见下文「净相同行」一节；
「接口名只出现在我们一个文件里」这一类（另一端是模型 API / HTTP 头 / 磁盘格式）
以前扣不掉、现在按写明的判据扣，见「接口名也只出现在一个文件里」一节。

---

## 为什么不必是 GPL —— 这一步曾经看起来是反的

第一眼看到工程里有 `libtermux.so` 和一个 33 MB 的 Termux bootstrap 时，
结论很像是「这必须是 GPLv3」。实际查下来不是，原因是两个组件分属两条不同的线：

### 1. `libtermux.so` 是 Apache-2.0，不是 GPL

它是唯一一个**被链接进本应用进程**的原生库（`System.load`）。
如果它是 GPL，整份应用就是衍生作品，本工程就必须 GPL 化。

判断依据：

```bash
# 看它导出什么符号，据此确定它对应上游哪个文件
tr -c '[:print:]' '\n' < app/src/main/jniLibs/arm64-v8a/libtermux.so \
  | grep -aE '.{6,}' | head
```

输出里有：

```
Java_com_termux_terminal_JNI_createSubprocess
Java_com_termux_terminal_JNI_setPtyWindowSize
Java_com_termux_terminal_JNI_setPtyUTF8Mode
Java_com_termux_terminal_JNI_waitFor
Java_com_termux_terminal_JNI_close
Cannot open /dev/ptmx
```

这组符号名对应 `termux-app` 的 `terminal-emulator` 子库
（该库的 `JNI.java` 里声明的正是这几个 native 方法）。

而 `termux-app` 根 `LICENSE.md` 原文写着：

> The `termux/termux-app` repository is released under [GPLv3 only] license.
>
> ### Exceptions
>
> - [Terminal Emulator for Android](https://github.com/jackpal/Android-Terminal-Emulator)
>   code is used which is released under [Apache 2.0] license.
>   Check [`terminal-view`](terminal-view) and [`terminal-emulator`](terminal-emulator) libraries.

`libtermux.so`、`com/termux/terminal/`、`com/termux/view/` 全部落在
这个例外里 → **Apache-2.0**。

### 2. bootstrap 里是独立程序，与我们是聚合关系

`app/src/main/assets/bootstrap-aarch64.zip` 解压后有 4,533 个文件，
里面确实是 GPL 程序（`bash` 880 KB、`coreutils`、`sed` 等）。
但它们是**独立可执行文件**，由本应用以子进程方式启动：

```
Spider 进程  ──fork/exec──▶  $PREFIX/bin/bash（GPLv3，独立进程）
```

GPL 明确允许把 GPL 程序与其它许可的程序聚合（aggregate）分发，
只要聚合体里每一部分仍受各自许可约束。这与「把 GPL 库链接进同一个进程」
是两件不同的事。前者不产生衍生作品，后者会。

对照第 1 点：`libtermux.so` 走的是后一条路（链接进进程），
但它恰好是 Apache-2.0 —— 所以两条链路都不产生 GPL 传染。

**因此 MIT 是安全的。**

### 一处仍需自己确认的事

bootstrap 里的程序许可各自适用，且要求源码对接收者可得。
出处是 `termux-packages` 与本工程的 fork 配方
（见 [`tools/termux-bootstrap-fork/`](../tools/termux-bootstrap-fork)，
含构建工作流 `build-bootstrap.yml` 与验收脚本 `VerifyBootstrap.java`）。
**发行时请确认这份源码对接收者可达。**

---

## 为什么「重写」不能免除署名义务

这是本次工作中最容易搞错的一点：

> MIT 允许使用、复制、修改、合并、发布、再分发、再授权、出售。
> 它唯一的硬性要求是：版权声明与许可声明必须
> 「included in all copies or substantial portions of the Software」。

所以：

- 用 IQ Code 的代码 → 不用重写也合法；
- 改名发行 → 合法；
- **重写 → 也不免除署名义务**，只要分发物里还留着它的代码。

「重写」解决的是**归属清楚**（谁写的、改了多少），
不是**免除署名**。两件事不要混。

---

## 度量：怎么知道还留着多少别人的代码

用 [`tools/provenance.sh`](../tools/provenance.sh)。它的做法是：

1. 把两棵树的包名、品牌、类名做**归一化**（`com.zhizhu.zhicode`→`com.iqge`、
   `Zhi`→`IQ`、`蜘蛛`→`IQ Code` 等）；
2. 按映射路径逐文件比对**逐行相同**的行数。

关键是**只抹命名、不动代码形态** —— 因此「相同」意味着代码本身没被改写，
而不只是「看起来像」。

### 这个脚本本身出过三次错，都是同一类

它曾把「改了文件名的类」算成「无对应文件」，于是重合度**误报为 0**。
三次分别是：

| 第几次 | 范围 | 后果 |
| --- | --- | --- |
| 1 | 沙箱层 14 个改了名的类 | 沙箱层重合度误报成 0 |
| 2 | 修好第 1 次时又按路径算了一遍 | 文件数与行数虚增到 203 / 40572 |
| 3 | Agent 工具层 2 个改了名的类 | `tools/` 目录的重合度误报成 0 |

现在配对表是四元组（本工程目录 | 原版目录 | 本工程文件名 | 原版文件名），
同时用 `RENAMED_BASENAMES` 把这些名字从按路径的循环里排除，避免重复计数。

**这类错误的危害不在于数字难看，而在于它让「未来是否漂移」变得不可检测**——
一个恒报 0 的区域，永远不会提醒你它的重合度回升了。

验证脚本是否还在正确工作：把一个上游文件当成我们自己的去比对，
结果应当是接近 100%（我们的实现里做过这个反向验证：

```
自比对: 93 行中相同 93 行
```

）。

### 当前数字（2026 年，本轮重写后）
```
归属区域                             文件         行数   仍与 IQCode 相同
Termux 上游（非 IQ Code）             23       7394           7274
Termux 集成层                       36       9361           1907
Compose 界面层                      59      16137              0
其它                                7       2398            286
Agent 工具                         44       5672            933
Agent 核心                          9       3602            840
沙箱宿主层                            16       4724            533
合计                              194      49288          11773

已是我们自己的:        37515 行
逐行相同合计:          11773 行
  其中 Termux 上游:     7274 行（Termux 自己的代码，与独立性无关）
  真正属于 IQ Code:     4406 行
```

### 这 4406 行是什么（默认输出）

「逐行相同」这个数字本身不够用：它把 `import android.os.Process;`、`}`、`return out;`
与真正的算法代码算在同一格里。把这个数字当成「还抄了多少」，会得出一个偏大得多的结论。
所以脚本把残留行分四桶，分类规则简单到可以人工核对 —— 下面这份 `CLASSIFY` 是
composition 与 algorithm **两个模式共用**的同一份程序（写在脚本里一份，不各写一遍：
本文件别处已经踩过「同一规则写两遍、然后各自漂移」的坑）：

```
骨架行（括号分号 / import / javadoc）:       2063 行
含字面量的行（协议键名与用户可见文案）:        451 行
声明行（字段、签名、注解、静态常量）:          733 行
语句行（有判断与动作 —— 最该重写的地方）:    1159 行
合计:                                        4406 行

  可以扣掉的（不构成「留着别人的代码」）：
    骨架行（任何 Java 文件都长这样）:            2063 行
    跨组件协议串行（>=2 个文件，或每一处都在协议位置）: 225 行
    可扣合计:                                   2288 行
  净相同行（合计 - 可扣 = 还差多少）:           2118 行
```

**声明与语句这条界线改过一次，见下面「分类器曾经算错 217 行」一节** ——
只记住「声明 758」而不记得它是怎么来的，就会重犯同一个错。

（`PROVENANCE_COMPOSITION=1` 会在上面之后追加「语句行去重后 897 种」与语句行全文。）

四桶的含义与可否归零：

| 桶 | 行数 | 能不能归零 | 为什么 |
| --- | --- | --- | --- |
| 骨架 | 2063 | **不能** | 任何 Java 文件都以 `import …` 开头、以 `}` 结尾。把这些行改得不一样等于删 import 或往里塞噪声 —— 两者都不是我们想要的 |
| 字面量 | 451 | **不能** | JSON 字段名、动作名是跨组件协议（宿主 `SandboxGuestHost`、Agent 工具、Frida 脚本三方对齐），改了会让两边对不上；用户可见文案是刻意逐字保留的。这一桶从 490 降到 451，是本轮把内嵌 Frida 载荷重写掉的结果（见下面「内嵌 Frida 载荷」一节） |
| 声明 | 733 | 基本不能 | 字段、方法签名、注解，以及 `public static final Status IDLE = Status.IDLE;` 这类静态常量。相同不是因为抄，而是因为**这是 Java 里写同一件事的唯一写法** |
| 语句 | 1159 | 能，而且应该压 | **这才是「读起来还像原版」的地方**，也是下面排批次看的那一列 |

**关于「字段带初始化器」的界线**：`private final AtomicBoolean busy = new AtomicBoolean(false);`
这类行算**语句**，不算声明 —— 因为规则是「有赋值即语句」，而那个初始化器本身是可改写的表达式
（改成 `new AtomicBoolean()` 也编译得过）。真正算声明的只有「没有赋值、没有方法调用」的行：
字段名、方法签名、注解。这条界线看起来吹毛求疵，但它是后面「声明桶能不能收窄」那个结论的前提 ——
界线挪一格，结论就换一个。

「去重后只有 897 种」是同一批行的另一个切面：一张 switch 的几十个 `case`、十几个同形
getter，逐条核对时看的是**形状**，不是条数。

脚本会把语句行全文写到 `build/provenance-statement-lines.txt` 并打印路径，供逐条核对
（不相信上面这行总结的人可以自己看）。这份全文**按文件分段**，每段前面一行
`=== 相对路径` —— 没有文件名的清单是没法核对的，「这句话到底是从哪来的」是看它的唯一理由。

**这个路径以前是打不开的**：这份全文原先写在 `mktemp -d` 建的工作目录里，而脚本
退出时会 `trap` 把它整个删掉 —— 于是「供逐条核对」是一句空头承诺，打印出来的路径
在下一行就已经不存在了。现在它固定写到工程内的 `build/` 下（1159 行 / 897 种，已验证留存）。

**为什么不再分三桶**：最初只有「骨架 / 字面量 / 其它」三桶，而「其它」里混着大量
`public final String planFile;` 这类声明，于是 `PlanWorkflowState` 显示有 70 行
「其它行、占比 70%」—— 与人工查阅的结论差了一个数量级。分成四桶之后同一个文件是
17 行语句（另见下面批 H 一节对这个数字的对照），数量级这才对得上。

### 净相同行：把「不构成派生的行」扣掉（这才是「还差多少」）

上表的 4406 行里有相当一部分既不能改、改了也没意义。把它们扣掉之后剩下的那个数，
才是「独立于 IQ Code 还差多少」：

```
净相同行 = 逐行相同 - 骨架行 - 跨组件协议串行
         = 4406 - 2063 - 225
         = 2118
```

**可扣的只有两类，规则刻意窄到可以人工核对。声明行与语句行永远不扣** —— 只要这两桶里
还留着一行与改写前的原版相同，净数字就不是 0。这是这个数字唯一有价值的地方。

| 可扣类 | 判据 | 为什么它能扣 |
| --- | --- | --- |
| 骨架行 | 整行只由 `{}();,[]`、`import`、`package`、javadoc 前缀构成 | 任何 Java/Kotlin 文件都长这样。把它改得不一样等于删 import 或往里塞噪声 |
| 跨组件协议串行 | 该行的**每一个**字符串字面量都合格：非空、全 ASCII、去掉转义后至少含一个字母或数字，且**满足两选一** —— ① 在本工程 ≥2 个文件里出现；② 它出现的**每一处**都在**协议位置**：引号之前最近的 `(` 的名字在白名单（`tools/provenance.sh` 里的 `PROTO_CALLS`，只此一份）里，且这个 `(` 到该引号之间没有别的字符串；或形如 `case "…":` | ① 是「两个组件按同一个名字对齐」（宿主 / Agent 工具 / Frida 脚本 / 持久化键名）；② 是「另一端不在本工程里」（模型 API 字段名、HTTP 头、apt/环境变量、用户磁盘上的文档格式）。两类都是**改了会让对面失配**的名字 —— 那是协议，不是表达 |

**这条规则是脚本里写死的，不是事后挑数字**，而且它宁可把数字算高：

- **中文串一律不扣**。本工程里的中文串是用户可见文案 —— 它可以改写，所以它属于
  「还要重写」，不属于「可扣」。把文案改成自己的措辞，净数字才会下降。
- **纯符号串一律不扣**。`, `、`:`、`\n` 是分隔符，按第 1 条它们也会入选协议串 ——
  但那样一次就能多扣几十行，所以排除在外（`\n` 里那个 `n` 会被误当成「含字母的名字」，
  因此规则是**先摘掉转义序列再找字母数字**）。
- **空串一律不扣**。`""` 出现在 97 个文件里，第一版实现就把它算进了协议串，
  于是协议串行从 203 行虚增到 344 行 —— 这个缺陷当时没被看出来，是因为只看了合计数。
  这一条现在还在起作用，而且是行级的：`String type = block.optString("type", "")` 这一行里
  `"type"` 虽是接口名，但整行里有 `""`，所以**整行不扣**（行级规则是「每一个串都要合格」）。
- **「值」不算接口名（比字面判据严一格）**。判协议位置时要求「这个 `(` 到该引号之间
  没有别的字符串」，也就是只认白名单调用的**第一个字符串实参**：
  `body.put("key", "value")` 里的 `"value"`、`setRequestProperty("k", "v")` 里的 `"v"`
  都不算。不这么卡的话，`new JSONObject().put("root","workspace.root")` 这类**文档内容**
  （画布的 id / type 值，本来就是模型可以自己生成的）会被当成契约扣掉。
  这一格值 8 行（严 22 行 vs 宽 30 行）—— 宁可把数字算高，所以从严。

每一次扣减都能被推翻：脚本会把**被扣的每一个串连同它出现的文件**写进
`build/provenance-protocol-strings.txt`，分三段 —— `[files]`（≥2 个文件的，
「对齐的另外两处」指得出来）、`[position]`（只出现一次、仅靠协议位置可扣的，
每一处都给出「文件:调用名」，看得出是哪个调用里的参数）、以及**没有被扣**的串
（中文文案 / 空串 / 纯符号 / 只在文案位置出现的英文串）。最后一行给出「只在一个文件里
出现、也不在协议位置上」的串有多少个 —— 不逐条列出（上千条，列出来只会淹没上面三段）。

已知还剩下的松处：一句纯 ASCII 的用户可见文案若恰好出现在两个文件里，也会被算进协议串。
所以净数字要连审计文件一起看，不能只看一个数。

### 语句行还能收敛多少（`PROVENANCE_SHAPE=1`）

净相同行里最大的一桶是语句行（1159 行）。要判断「还值不值得继续投入重写」，需要知道
其中有多少是**同一件事被写了几遍** —— 那是唯一一种「收敛就能省行」的余量：

```
语句行总数: 1159 行 / 73 个文件
  属于「出现 >=2 次的单行形状」:   682 行（143 种）—— 单行重复不省行，只看模板化程度
  只出现一次的独立形状:            477 行（477 种）

估算可省语句行上限: 0 行（来自 0 个极大模板形状，彼此不重叠）
  另有 14 个重复窗口已排除：它们全是赋值/声明，没有共同的「体」可提取
```

为什么必须扫**连续多行**窗口而不是统计单行形状：`if (x == null) continue;` 本身就是一行，
重复二十次也只是二十行；把它提取成一个 helper 之后是「一个 helper + 二十个调用点」，
行数**更多**。所以单行重复度只说明模板化程度，不能算收益。真正能省行的是多行模板，
它在语句清单里表现为几行连续重复。

**这个上限现在是 0，而且是这个工具先量出数据、再按数据做出来的**（不是反过来：
先改代码再声明已经没有余量了）。它第一次跑出来时是 **52 行 / 6 个极大模板**，
全部是同一族 —— 「遍历 `JSONArray` → `optJSONObject` → 判空」：

| 可省 | 行数 | 出现 | 文件 | 形状 |
| --- | --- | --- | --- | --- |
| 25 | 3 | 15 | 7 | `for (…) {` / `<T> <v> = <v>.optJSONObject(<v>);` / `if (<v> == null) continue;` |
| 6 | 4 | 4 | 3 | 上一行族 + 两个判空 |
| 3 | 4 | 3 | 2 | 同族变体（`return <v>;` 收尾） |
| 3 | 4 | 3 | 3 | 同族变体（`return false;` 收尾） |
| 3 | 3 | 4 | 3 | 同族变体 |
| 1 | 3 | 3 | 2 | 同族变体 |

按这张表把那一族收进 `JsonItems`（见下一节）之后，工具再跑报 **可省 0 行**；
语句行的减量是 1020 → 960（当时的口径）。也就是说：**语句行里「同一件事写几遍」
这一部分已经没有了**，剩下的是只出现一次的形状 —— 其中有「Java 里只有这一种写法」的惯用法，
也有真正独有的逻辑，而「这段能不能换个写法」是度量分不出来的，只能逐条读。

**这里要标一句口径的变动，否则上面几个数字对不上**：那 52 行是在**旧的分类器**下量出来的
（当时语句桶 1020 行）。后来发现分类器把 217 行语句算进了声明桶（见下面「分类器曾经算错」
一节），修好之后语句桶变成 1160 行 —— 同一个桶，定义变了，行数就变了。
`JsonItems` 那一笔的减量（-60 行）不受影响：它减掉的是那个模板族本身。

那张 52 行的表本身也要当**上限**看，不是可完成的工作量，理由三条都写在脚本注释里：
窗口取自 diff 的未变块，相邻两行在源文件里未必真的相邻（邻接性是近似的，动手前必须读源码
确认）；只出现一次的形状里惯用法与真正独有的逻辑混在一起；而且「窗口里必须至少一行在
调用方法」这条规则会排掉连续字段赋值序列 —— 那一类恰恰是某些批次真正做出成绩的地方，
所以当时的真实量级在 52 到 84 之间（84 是加这条规则之前的数，含假阳性）。
实际做完落在 52 那一侧，说明这条规则没有把真实工作漏掉太多。

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
| `OpenAIChatCompletionsProvider`：`int index = call.has("index") ? call.optInt("index", i) : i` | 同上，且带一个回退到「第几项」的语义 |

这两处也是 `JsonItems` 的 javadoc 里写下的那条「什么时候不要用它」。

**动手前必须先跑 `build/check-index-use.awk`**，原因是这个坑编译查不出来：
把 `for (int i …)` 换成 for-each 之后，循环体里若还残留 `i`，它会**静默改绑到外层变量**
（外层恰好也有 `i` 时），编译一声不响、行为已经变了。先扫一遍，结果正好就是上面那两处；
若没有这一步，它们会带着错误的下标发出去，只有在服务端回报文时才会发现。

顺带一并收掉的 10 个变体（`if (x != null) put(x);`、多重条件判空、内层 `j` 循环等）不是共享
行，改它们只为一致：一个 helper 只用在二十处、另十二处各写各的，比不改更难读。
（`build/check-index-use.awk` 与 `build/rewrite-json-loops.awk` 是一次性工具，在 `build/` 下，
不进仓库 —— 它们要说明的事情已经写在这一节里。）

### 477 行「只出现一次的形状」逐条判读的结果

工具报「可省 0 行」之后，语句桶剩下的 477 行是逐条读过的 —— 清单由
`PROVENANCE_SHAPE=1 PROVENANCE_SHAPE_UNIQUE=1 bash tools/provenance.sh
                                                    # 再加「只出现一次的形状」逐行清单` 导出到
`build/provenance-unique-shapes.txt`（每行带文件名，共 477 行）。

**先说清这 477 行是按什么筛出来的**：形状完全相同的行会被算进「重复」那一档，
所以这 477 行**在结构上彼此不同** —— 这是算法的定义决定的，不是巧合。
这既说明「没有剩下可收敛的重复」，也说明下面那张表要一个个看类别，不能只看总数。

| 类别 | 行数 | 有第二种写法吗 | 判读 |
| --- | --- | --- | --- |
| 控制流（`if` / `for` / `while` / `try` / `} else {`） | 121 | 很少 | **不改**：每一条都是具体的业务条件（`if (status < 200 \|\| status >= 300)`）。它的内容是那个条件本身，换写法只能靠取反或改成三目，两者都让意图更难读 |
| 局部变量初始化（`T x = expr;`） | 115 | 有（内联、改名） | **不改**：内联只在这条语句只被用一次时成立，而改的后果是把一行变成主语义里的一小段 —— 可读性换数字 |
| 调用语句（`reload();`、`super.onResume();`、`view.requestFocus();`） | 111 | 有个别 | 见下面「唯一一处真正像重复的地方」 |
| `return` 表达式 | 46 | 很少 | **不改**：同上，内容是表达式本身 |
| 其它（`case …:`、注解行、折行续行等） | 41 | 没有 | 语法本身要求这么写 |
| 字段/常量声明并赋值 | 30 | 有个别 | `private final JSONArray messages = new JSONArray();` —— 初始化器本身可改，但改它就只是换个写法 |
| 构造期 `this.x = y` | 13 | 没有 | `this.toolCalls = …` 与形参同名，只能这么写 |

这 477 行里**唯一有实质重复嫌疑的地方**是三处「把一个数组的元素全部追加到另一个数组」：

```java
for (int i = 0; i < restored.length(); i++) messages.put(restored.getJSONObject(i));   // ZhiCodeEngine
for (int i = 0; i < extra.length(); i++) additionalToolContent.put(extra.get(i));      // ZhiCodeEngine
for (int i = 0; i < plan.recent.length(); i++) out.put(plan.recent.getJSONObject(i));  // ContextCompactor
```

**它们看着可以收成一个 helper，但不能收。** 前两行的 `getJSONObject` 在遇到非对象元素时
**抛 JSONException**，第三行的 `get` 什么都能放 —— 也就是说「跳过非对象」与「遇到非对象就抛」
是两种不同的行为，而这三处**刻意选了不同的那一种**。收成一个 helper 必须给一个
`strict` 开关，那等于把三行变成「一个带开关的 helper + 三处调用」，行更多、且把
「这里到底要不要容忍畸形数据」这个决定藏进了一个参数里。所以留着。

另外两处**不属于本任务**：`UiCanvasStore`（11 行压缩写法，最长一行 507 字符）与
`UiCanvasController`（1 行，233 字符）—— 它们是全工程唯一还在用「一行塞十几个语句」写法的
文件，也确实是原样保留的残留。这两处已单列为批 F 的收尾（见下文批 F 一节），因为
要改的不是这几行，是整个文件的写法。

**这一节的结论**：语句桶里「同一件事被写几遍」的部分已经收完（`可省 0 行`），
剩下 477 行逐条读过之后，**没有一行是「因为没想到更好的写法才与上游相同」** ——
它们或是 Java 里写同一件事的唯一写法，或是改了就只为让那一行看起来不一样。
也就是说：**再往下压语句桶，收益已经不是代码质量，只是数字。**

（这一节原先写的是「423 行」，是在**旧的分类器**下数的。分类器修好之后桶的定义变了，
行数跟着变成 477，于是整节重数了一遍 —— 类别与结论都没变，变的是那几个数字。
这件事本身就是下面那一节的教训。）

### 分类器曾经算错 217 行：`try {`、`/**`、`break;`、`i++;`、`foo();` 都在声明桶里

这是度量工具出过的**第二次**同类错误（第一次是路径映射漏算，见下一节），而且这次
错得更隐蔽：数字总量没变，只是桶之间分错了。

原来的语句规则是「命中关键字 / 有赋值 / 调用了 11 个白名单方法名之一」。下面五类
一条都不满足，于是全都落进了「声明」桶：

| 混进声明桶的行 | 行数 | 为什么原来没被算成语句 |
| --- | --- | --- |
| `try {` | 64 | `try` 不在关键字表里（表里只有 if/for/while/return/throw/catch/switch/case/else/new/instanceof） |
| `/**` | 27 | 骨架规则只认 `^*`，不认 `/**` |
| 无白名单方法名的调用语句（`maybeAutoCompact();`） | 105 | 无参调用没有点号，11 个白名单方法名一个都不匹配 |
| `break;` / `continue;` | 9 | 同 `try` |
| `x++;` | 8 | 自增没有赋值号 |
| `synchronized (x) {` | 12 | 不在关键字表里 |

后果不只是「数字不好看」：文档当时据此写下「声明桶 987 行基本不能归零」这个结论，
而它是建立在一个**混了 217 行语句的桶**上的。修好之后：声明 987 → **758**、
语句 960 → **1160**、骨架 2039 → **2068**。

**教训与「规则只此一份」是同一个，但方向相反**：那条讲的是同一份规则被写两遍会漂移，
这条讲的是**一份规则自己要能被人工核对，而核对的对象是行，不是总量**。
修法的关键不是把五类逐个打补丁，而是写下一条可判定的规则：
「以标识符加点号或括号开头、以分号结尾」= 一条调用语句（要求紧跟点号/括号，
是为了不误伤 `void onUsage(long a, long b);` 这种签名 —— 那里 `void` 后面是空格）。

这也是为什么每次改分类器都要重跑四桶并**重新读一遍**某一桶的全文：
数字总量不变的时候，没有任何东西会提醒你桶分错了。

### 声明桶怎么收窄：36 个顶层类型改为包内可见

声明桶（758 行）里能动的只有一类：**顶层类型的公开面**。
`public final class ReadTool implements ZhiTool` 与上游逐字相同，因为那是 Java 里
声明这个类的唯一写法 —— 除非把这个类**降为包内可见**：`final class ReadTool`。

判据不是「grep 一下有没有别的包引用」，而是**编译**：

```
./gradlew :app:compileDebugJavaWithJavac   # 降级后若有跨包引用，javac 直接报错
./gradlew :app:assembleDebug               # 再走一遍清单合并与资源链接
```

再加一遍非 Java 资源的类名扫描（`assets/`、`res/`、`AndroidManifest.xml`）——
反射按类名找的路径不在编译器的检查范围内。这两遍都过了才算数。

**结果**：36 个文件降级，其中 **24 个**的类声明行原本与上游逐字相同，因此这 24 行不再相同；
另外 12 个的类声明行本来就不相同（原版那一行的写法不同），所以降级不改变数字。
逐文件核对的方式是把改动前的 `PROVENANCE_PER_FILE` 报告与改动后的并排比对，
每行各降 1 —— **净相同行 2228 → 2204（当时的数）**，全部落在声明桶（758 → 734，-24）。

**必须保持 `public` 的四类**（逐类点名，因为它们看起来与上面那 36 个没区别）：

| 类 | 为什么必须是 public |
| --- | --- |
| `SandboxBoard`、`SandboxKeeper`、`SandboxRpcService`、`ZhiFileProvider`、`KeepAliveService`、`MainActivity`、`ZhiCodeApplication` | 在 `AndroidManifest.xml` 里注册，由框架实例化（后两者还受基类强制） |
| `TermuxTerminalPane`、`SandboxOverlay` | 是 View；界面层要按类型引用，且可能被框架反射实例化 |
| `ZhiTool`、`ToolRegistry`、`BashTool`、`McpTool`、`SkillTool`、`WebSearchTool`、`WebFetchTool`、`ZhiSandboxTool`、`ZhiDebugTool`、`AndroidIntentBridge` | 被别包引用（`ZhiCodeEngine` 在 `core`，工具在 `tools`） |
| `UiCanvasStore`、`UiCanvasController`、`FridaEnv`、`SandboxGuestHost`、`SandboxPrefs` 等 | 同上；沙箱层与 Agent 层是两个包 |

**Termux 上游那三个包不动**（`com/termux/terminal`、`com/termux/view`、`com/termux/shared`）。
理由不是「它们也是我们的」，而是：那是 Apache-2.0 第三方文件，改它们的公开面属于
**修改第三方代码** —— 要按第 4 条附修改声明，收益只是几个数字、成本是一份义务。
这类文件在度量里本来就被单列成「Termux 上游（非 IQ Code）」，不参与净相同行。

**这一节还留下一个教训**：`TerminalColors`、`TerminalColorScheme`、`TerminalRenderer`、
`Logger`、`SandboxFrida`、`SandboxGuestDebug`、`SandboxOverlay` 在纯 Java 层面确实只被同包用，
但「只被同包引用」**不等于**「可以降」——`TerminalOutput` 就是反例：它被同包的
`TerminalSession extends TerminalOutput` 继承，而 AOSP 的 `TerminalSession` 里有
`TerminalOutput.context` 这个字段的反射依赖（那是 AOSP 的代码，不是本工程的，
所以本工程里查不出来）。结论就是前面那句：判据必须是编译 + 资源扫描两遍，而不是 grep。

**量级要说清楚**：这条路本批只产出 24 行（占当时净相同行的 1.1%）。
它值得做的理由不是数字，而是**公开面本来就该最小**；指望靠它把净数字压到 0 是不现实的，
继续往下的主通道是把剩余的用户可见文案改成自己的措辞（见「字面量」那条）。

### 25 处中文文案改成本工程措辞（净相同行 2204 → 2179）

字面量桶拆成两份之后（`PROVENANCE_LITERALS=1`），310 行「不可扣」里**含中文的只有 24 行**
（同串计 25 处）—— 它们才是真正的用户/模型可见文案，其余 286 行见下一节的构成分析。

```
压缩失败：模型没有返回结果          →  压缩未完成：模型没有返回任何内容
压缩中断：摘要达到输出上限，请调高…  →  压缩未完成：摘要用满了输出上限，请调高…
消息标识重复，已拒绝修改            →  消息标识出现重复，修改已拒绝
原 API 配置                        →  内置 API 配置
沙箱日志已清空                      →  已清空沙箱日志
Android Intent 失败：               →  Android Intent 调用失败：
需要 hide_root                      →  缺少 hide_root 参数
query 不能为空                      →  缺少 query 参数
如需阅读网页正文，请对目标 URL…     →  要读网页正文，请对该 URL…
```

改之前先查了两件事，因为它们决定「改文案」会不会变成「改行为」：

1. **没有任何测试钉住这些串**（`app/tests` 与 `app/src/test` 全查）——
   否则测试会把「作者把这些字打在哪儿」变成契约（本文件另一处记过这个坑）；
2. **没有任何代码按这些文案做分支判断**（`contains` / `startsWith` / `equals` 全查）——
   有的话，改文案就是改行为。

语义完全不变，只换措辞；协议键名（`hide_root`、`show_floating_log`、`payload` 等）一律不动。
实测净相同行 2204 → 2179（-25）。骨架 2068 → 2064、声明 734 → 733、语句 1160 → 1159
各降 1~4 —— 那不是改到了这些桶，而是 diff 对齐游标随改动平移后的正常噪声。

### 接口名也只出现在一个文件里（已按方案 A 扣减）

`PROVENANCE_LITERALS=1` 把字面量桶拆成两份之后暴露了口径的一个漏洞：
**协议串的判据是「≥2 个文件」，但接口的另一端不一定是我们自己的另一个文件。**
当时不可扣的字面量行是 287 行（490 − 203）。

现在这一类也扣（判据 v2）：`"output_config"`、`"reasoning_effort"`、`"session-id"`、
`"stop_reason"` 这类**只出现在我们一个文件里、但每一处都在协议位置上**的串 ——
另一端是 OpenAI / Anthropic 的服务器、是统一的 HTTP 头格式、是已经写在用户磁盘上的会话文件，
改不得。**放宽的方向是「诚实」意义上的：它让净相同行变小，而不是变大。**

```
不可扣的字面量行:   287  →  265
  其中可扣:          22              （原型用 11 个白名单名先量出 21 行；
                                      补上 isNull / opt* 后 22 行）
可扣协议串行:       203  →  225      （+22）
净相同行:          2179  →  2157      （−22）
```

两个数各自独立量过，结果一致：只读原型 `build/measure-rule-a.awk` 直接判这 287 行，
得「可扣 22 / 仍不扣 265」；改完脚本后 203 → 225。差值是同一批行。

新被扣掉的行，样例（全表见 `build/provenance-literal-protocol.txt`；每个串的出处见审计文件
里 `[position]` 那一段，格式是「文件:调用名」）：

| 样例行 | 起作用的协议位置 |
| --- | --- |
| `body.put("output_config", new JSONObject().put("effort", effort));` | `put(` |
| `if (delta != null && delta.has("stop_reason") && !delta.isNull("stop_reason")) {` | `has(` / `isNull(` |
| `conn.setRequestProperty("session-id", session);` | `setRequestProperty(` |
| `event.has("output_index") ? event.optInt("output_index", -1) : -1` | `has(` / `optInt(` |
| `.put("asset_sha256", GADGET_XZ_SHA256);` | `put(` |

**上一版这一节举的例子是错的**（写文档时也得去审计文件里核一遍）：
`"input_schema"`、`"max_output_tokens"`、`"payload"` 其实都出现在 ≥2 个文件里，
本来就在 `[files]` 类里被扣掉了（`grep` 一下审计文件就知道）；`"+ "` 则在整个工程里
**根本不存在**这个串字面量 —— `UnifiedDiff` 写的是 `out.append("--- ")` 这类形式，
而 `append` 也不在白名单里，所以它从来不是候选。记下来是因为这类例子最容易
「看着像」就被写进文档，而它正是读者用来核对规则的样本。

**白名单只此一份**（`tools/provenance.sh` 的 `PROTO_CALLS`，审计文件照印，文档不再抄第二份）：
`put putOpt get getJSONObject getJSONArray getString getInt has isNull remove setRequestProperty
opt optString optInt optBoolean optLong optDouble optJSONObject optJSONArray`，外加形如
`case "…":` 的位置。行级判据没有变：该行的**每一个**串都要合格，一个不合格就不扣。

判据刻意比字面更严一格（只认白名单调用的**第一个字符串实参**），代价是少扣 8 行
（严 22 行 vs 宽 30 行）：从宽的话 `new JSONObject().put("root","workspace.root")` 这种
**文档内容**（画布的 id / type 值，本来就是模型可以自己生成的）会被当成契约扣掉。

剩下的 225 行去掉之后，字面量桶里还留着 226 行（451 − 225），按文件分布：

```
 24 行  core/ZhiCodeEngine.java            工具 schema 与模型可见文案
 24 行  api/OpenAIResponsesProvider.java   OpenAI Responses 的报文字段名
 19 行  core/ContextCompactor.java         摘要提示词（有基线测试钉住）
 12 行  tools/AndroidIntentBridge.java     Intent 参数名
 12 行  api/OpenAIChatCompletionsProvider.java
 10 行  tools/UnifiedDiff.java             diff 标记（"--- " / "+++ "）
  8 行  zhizhu/UiCanvasStore.java          画布文档内容（压缩写法）
```

**原先排第一的那 40 行（`sandbox/SandboxFrida.java` 的内嵌 Frida JS）已经重写掉了**
（净相同行 2157 → 2118）。它不属于「接口名」—— 它是**我们自己的代码**，
而且当初把它同时写成「冻结资源」与「不该再花时间」都是错的：
见下面「内嵌 Frida 载荷：一句注释差点让目标消失」一节。

### 内嵌 Frida 载荷：一句注释差点让目标消失

同一件事在仓库里曾经有**三份互相矛盾**的写法，其中两份的效果是**让度量里的重写对象消失**：

| 位置 | 说法 | 判定 |
| --- | --- | --- |
| `SandboxFrida.java` 的 `归属说明` | 「从外部引入的，属于**冻结资源**，不是本次重写的对象」 | **不成立**（见下） |
| 本节上面那句 | 「它是我们自己的代码……**可改、也应该改**，是这一桶里唯一真正的重写对象」 | 成立 |
| 本文后面按语句行排序那节 | 「这 43 行是内嵌脚本的大串 —— 这一类**不该再花时间**」 | 错 |

判定不是靠印象，是**量出来的**：把两个文件里的载荷文本抽出来，只对齐品牌名
（`Zhi` ↔ `IQ`）再 diff —— 差异只有两处标识符名（`const Zhi=…` 与 eval 那行的形参名），
其余**逐行相同**。所以：

- 它不是「外部引入」的，它是 `IQ-Code-Android/.../SandboxFridaBridge.java` 的
  `bridgeScript` 原样留在我们文件里；
- 它照旧计入「净相同行」（字面量桶里一个只在一个文件出现、又不在协议位置的大串，
  按现行规则**不可扣**），所以只要它原样留着，净相同行就 ≥ 40，到不了 0；
- 「冻结资源」那句话是本工程明确要防的写法：**一句注释就能让度量对象自己消失**。
  Termux 上游那七千多行确实被单列，但那是按写明的规则、且有独立的来源与许可；
  这里当初没有那样的依据，写下的却是一样的结论。

**这一步的收益要说实话**：它买到的是指标上那 40 行（2157 → 2118），不是「真正独立」。
算法与线上契约按设计保持不变，所以它仍是派生实现（MIT + 作者许可，这是允许的）。
真正与度量无关、值得单独做的工程收益是另外两件，而且它们排在重写**之前**：

1. 把这段压缩文本拆成有名字的可读结构（原先有一行是 3210 个字符）；
2. 给它装上能真跑的测试。原先守这 40 行的**全是逐字拼写断言**
   （例如 `contains("boundedInteger(p.chunk_size,4194304,65536,8388608)")`），
   等于「不许改」—— 「冻结」那句话的技术依据就是这个。
   现在用 `node` 跑 `app/tests/js/frida-agent-harness.mjs`：把载荷从 Java 源码里抽出来，
   用桩（`Process` / `Memory` / `File` / `ptr` / `NativePointer` / `Interceptor` / `Stalker`）
   加载，按载荷自己的信箱协议发命令、读响应，对返回值做断言（163 项）。
   局限也写在那个文件里：它**证明不了**真实 Gadget 载入、`Interceptor`/`Stalker` 钩子与
   真实 `Memory.scan` 的行为 —— 那要在 IQ 沙箱里跑起来才能确认。

**重写后它是怎么嵌进 Java 的**：一份 text block（JS 原样可读）。曾经想过把它挪进
`assets/` 或 `res/raw/` —— 那样这个文件会好看很多，但 `tools/provenance.sh` 只比对
`.java`/`.kt`，载荷会**从度量里直接消失**：一行都没重写，数字却变好看。这条被明确拒掉了，
理由与上面那句「冻结资源」是同一个：不能让度量对象自己消失。

**这套测试上手就抓到两件事**（都不是重写引入的，是原版代码里就有的）：

1. `jsonSafe` 在 eval 路径上被套了两次，于是大数组的 `[+N more]` 标记里的数字永远显示
   `[+1 more]` —— 第二次看到的是**已被截断**的 2049 项数组，算出 `2049 − 2048 = 1`，
   而真实被截掉的是几百项。原先的断言把这个错**钉成了契约**（写的就是 `[+1 more]`）；
   现在用两个不同规模（3000 / 5000 项）断言，一个规模只能证明「有个数字」，
   两个规模才能证明「数字是对的」。重写时去掉了重复那一层。
2. `frida_eval` 的 `timeout_ms` 只能打断**会让出事件循环**的脚本：同步的 `while(true){}`
   会把 guest 卡死，连脚本自己的 deadline 都轮不到（JS 没有抢占）。
   第一次跑 harness 就是这么满载 spinning 了五分钟。载荷里现在把这个限制写在明处，
   harness 断言的是「可达成的那一半」。

### 这个数字的边界：它是字面判据，不是法律判据

「净相同行 = 0」只说明**没有一行与 IQ Code 逐行相同**。它说明不了：

- 算法与结构没被沿用（重写一段代码的字面不会消除派生关系）；
- 也不构成「无需署名」的依据 —— 本工程保留 IQ Code 的 MIT 原文与致谢，依据是
  **原作者许可 + 自愿致谢**，与这个数字无关。

写下来是因为这个数字是头条结论，最容易被读成「所以我们完全没用过它的代码」。
不是这个意思，前面「为什么不必是 GPL」一节也从不建立在这个数字上。

### 这个数字曾经是错的（记下来，因为它会再次发生）

上一版这里写的是「真正属于 IQ Code: 8644 行」。那个数字**低估了 1488 行**，
原因是 `tools/provenance.sh` 的路径映射只在「文件名不变」时成立：
一个文件如果改了类名、因而**文件名也变了**（`ZhiCodeEngine` 对 `IQCodeEngine`），
它既被跳过名单排除、又不在配对表里，于是**不出现在报告的任何一行里**，
从总数上静默消失。被这样漏掉的四个文件是：

| 文件 | 行数 | 当时被记为 |
| --- | --- | --- |
| `core/ZhiCodeEngine.java` | 1353 | 0 重合（实测 1238 行相同，99.2%） |
| `com/zhizhu/zhicode/ZhiTool.java` | 33 | 0 |
| `com/zhizhu/zhicode/ZhiDocumentsProvider.java` | 126 | — | 批 A 已删除 |
| `com/zhizhu/zhicode/ZhiFileProvider.java` | 199 | 0 |

最严重的是第一个：它是全工程最大的单文件、且几乎整文件与原版逐行相同，
却因为这条漏算在表里显示为 0 —— 按这张表排批次会**正好把最大的一块漏掉**。

现在脚本加了两条自检，都是「宁可吵闹也不静默」：
1. 跳过名单（`RENAMED_BASENAMES`）与配对表（`PAIRS`）必须一致，
   名单里有而配对表里没有的，直接打警告；
2. 配对表指向的本工程/原版文件必须真的存在，写错名字会报出来。

两条都做了反向验证：删掉 `ZhiCodeEngine` 的配对 → 报「静默算成 0」；
把原版文件名改成 `IQCodeEnginTypo.java` → 报「原版文件不存在」；恢复后无警告。

**一个重要的范围澄清**：重合行数最大的那些文件里有相当一部分**不是 IQ Code 的代码**：

```
2453 行  com/termux/terminal/TerminalEmulator.java
1297 行  com/termux/view/TerminalView.java
 552 行  com/termux/terminal/WcWidth.java
 444 行  com/termux/terminal/TerminalBuffer.java
 ...
```

这些是 **Termux 上游**（`terminal-emulator` / `terminal-view`，Apache-2.0），
由 `provenance.sh` 归入「Termux 上游（非 IQ Code）」一类。
它们的正确做法是原样保留并履行 Apache-2.0 义务（已在 `NOTICE` 中声明），
**重写它们既没有意义也是错的**。真正需要处理的是下面这两张表里的文件。

| 类 | 原版行 | 现在行 | 相同行 | 重合 |
| --- | --- | --- | --- | --- |
| `ZhiDebugTool`（Agent 工具） | 179 | 435 | 21 | 4.8% |
| `SandboxBoard` | 193 | 727 | 37 | 5.1% |
| `ZhiSandboxTool`（Agent 工具） | 93 | 290 | 15 | 5.2% |
| `SandboxGuestHost` | 299 | 886 | 53 | 6.0% |
| `SandboxOverlay` | 104 | 281 | 24 | 8.5% |
| `SandboxShell` | 102 | 270 | 23 | 8.5% |
| `FridaEnv` | 135 | 289 | 31 | 10.7% |
| `SandboxKeeper` | 34 | 99 | 12 | 12.1% |
| `ZhiSandbox` | 190 | 332 | 46 | 13.9% |
| `SandboxRpcService` | 110 | 246 | 42 | 17.1% |
| `SandboxConsole` | 78 | 212 | 37 | 17.5% |
| `SandboxGuestDebug` | 288 | 572 | 119 | 20.8% |
| `SandboxPrefs` | 96 | 146 | 33 | 22.6% |
| `SandboxFrida` | 141 | 339 | 75 | 22.1% |
| `SandboxProcess` | 40 | 69 | 18 | 26.1% |
| `SandboxRpc` | 29 | 45 | 13 | 28.9% |
| `SandboxStage` | — | 110 | — | 新文件 |
| `SandboxPalette` | — | 93 | — | 新文件 |

**没有重写的部分，如实说明：**

- `SandboxFrida` 里那段 agent JS 的**载荷行为**是**刻意冻结**的：`rpc.exports`、
  信箱文件名、ready 标记是 Java 与脚本之间的协议边界，改一侧必须同步改另一侧；
  它的行为细节（有界扫描、部分失败可返回、legacy scanSync 重写、eval 沙箱与超时）
  都有回归断言盯着，重写只会引入行为漂移。
  该类的 22.1% 主要就是这段载荷。
  **改过的只有名字**：注入对象由旧品牌标识改为 `Zhi`（`Zhi.emit` / `Zhi.hooks` /
  `Zhi.scan`），磁盘文件名改为 `libzhifrida.so` / `libzhifrida.config` / `zhi-agent.js`。
  名字不是行为，但它会同时出现在工具 schema 与系统提示词里，所以三处必须一起改。
- **Agent 运行时仍在进行中**，是当前比例最高的残留区（见下文「仍然剩下的」）。

### 改名与旧数据（A–D 与阶段 0.5）

把源码里旧品牌相关的字符串全部清掉，**只改名，不改行为**。
到阶段 0.5 结束时，源码（`app/src/main`）里已经**一个都不剩**：
`iq_code_*`、`<iq_internal_continue>`、`.iq`、`IQ.md`、`libiqfrida*`、`iq-agent.js`、
`ctoken.top`、`com.iqge`、`iqcode-*`、以及 `IQ.emit/hooks/scan` 全部消失。

**代价是明确接受的**：为了不留任何旧名字，历史上那几处「只读兼容」被一并删除。
这意味着从更早版本升级上来时，旧名字下的设置、API Key 与旧会话里的内部续跑标记
不再被识别。取舍理由是不留旧品牌标识这件事优先。

各项改动的具体内容：

- **A（`7a390a9`）** 品牌串与线程名，并修掉两个真 bug：
  `iq-patch-deb` 这个脚本名从来没存在过（正确名由 `BRAND_SLUG` 派生），
  以及 `KeepAliveService` 写的是旧 prefs、导致关掉保活不会生效。
- **B（`312c89c`）** 数据目录改名与记忆文件改名。
- **C（`3829aa9`）** `LocalIqDark`、空态文案、注释里的旧产品名。
- **D（`53be6c3`）** Gadget 磁盘文件名 `libiqfrida.so` → `libzhifrida.so`。
- **阶段 0（`12e019c`）** 下线内置的厂商 API 配置（`OFFICIAL_*` 常量、注册链接、
  「不可删/不可改」保护），并加了一次性清除 + `NoBundledThirdPartyEndpointTest`。
- **阶段 0.5** 清除全部旧品牌残留：所有「只读兼容」与迁移逻辑删除，
  `LegacyDataMigration` 整个删掉，数据目录候选列表收敛为单一位置，
  注入脚本对象名改为 `Zhi`。

### 署名：为什么仍然保留（这一节是「自愿」，不是「义务」）

`THIRD-PARTY-LICENSES/IQ-Code-MIT.txt` 与 `NOTICE` 第 1 节对 IQ Code 的署名，
**不是尚在生效的许可义务**：

- 原作者是版权人，他除了 MIT 之外**另外明确许可**本工程改写、改名与发行，
  并**同意不强制要求随附其版权声明**（由项目方转述；时间与形式待补记录）。
  版权人可以豁免自己许可里的条件，所以「必须随附声明」这一条**已经被解开**。
- 理论上现在就可以把那一节删掉。我们选择保留，理由与法律无关：
  本文件的整篇、以及 `tools/provenance.sh` 的全部作用，都是**拿本工程与原版逐行比对**
  —— 归一化表里写着 `com.zhizhu.zhicode → com.iqge`，还有一张 19 行的
  `SandboxGuestDebug ← SandboxProcessDebug` 配对表。派生关系在这个仓库里是**自证的**，
  删掉署名只会变成「一边处处与原版比对、一边声明毫无关系」；而 4,551 行相同代码
  删不删署名都一样留着。**保留署名不减少、也不削弱任何关于「独立」的主张。**

`app/tests/LicenseNoticeStructureTest.java` 因此把「MIT 版权行必须逐字保留」这条
正向断言改成了两条更贴合现状的断言：**许可记录本身必须在**（写清内容、来源与范围），
且必须说明保留署名是**自愿致谢**。记录是新的最弱一环 —— 它一旦丢失，
「我们为什么可以这样做」就只剩一句无法核对的话。

### 仍然剩下的（按重合行数排序）

`PROVENANCE_PER_FILE=1 bash tools/provenance.sh` 会打印完整清单。当前最前面的几项：

| 相同 | 原版 | 现在 | 重合 | 文件 | 处置 |
| --- | --- | --- | --- | --- | --- |
| 492 | 1245 | 2094 | 23.5% | `com/termux/app/zhicode/core/ZhiCodeEngine.java` | 已重写；余量是 Listener 接口与用户可见文案 |
| 256 | 622 | 929 | 27.6% | `com/termux/app/zhicode/api/OpenAIResponsesProvider.java` | 已重写 |
| 204 | 354 | 545 | 37.4% | `com/termux/app/zhicode/tasks/TaskStore.java` | 已重写 |
| 199 | 642 | 1138 | 17.5% | `com/termux/app/zhicode/storage/SessionStore.java` | 已重写 |
| 195 | 390 | 619 | 31.5% | `com/termux/app/zhicode/core/ContextCompactor.java` | 已重写（模型文本有基线测试钉住） |
| 189 | 468 | 929 | 20.3% | `com/termux/app/zhicode/termux/TermuxShellExecutor.java` | 已重写 |
| 156 | 451 | 596 | 26.2% | `com/termux/app/zhicode/api/OpenAIChatCompletionsProvider.java` | 已重写 |
| 141 | 310 | 607 | 23.2% | `com/termux/app/zhicode/tools/AndroidIntentBridge.java` | 已重写 |
| 122 | 257 | 460 | 26.5% | `com/termux/app/zhicode/api/AnthropicMessagesProvider.java` | 已重写 |
| 102 | 181 | 319 | 32.0% | `com/termux/app/zhicode/tools/UnifiedDiff.java` | 已重写 |
| 100 | 139 | 207 | 48.3% | `com/termux/app/zhicode/model/PlanWorkflowState.java` | 已重写（批 H）；余量见下节，是声明与签名 |
| 100 | 583 | 1056 | 9.5% | `com/zhizhu/zhicode/TermuxTerminalPane.java` | **已重写（批 F 2/6）；余量是 31 行 import + 只能在 View 侧写的 PTY 调用** |
| 93 | 331 | 699 | 13.3% | `com/termux/app/zhicode/storage/ApiSettingsStore.java` | 已重写 |
| 89 | 288 | 584 | 15.2% | `com/zhizhu/zhicode/sandbox/SandboxGuestDebug.java` | 已重写（批 I）；余量是动作名与 JSON 键（协议） |
| 75 | 141 | 339 | 22.1% | `com/zhizhu/zhicode/sandbox/SandboxFrida.java` | 已重写；余量主要是内嵌 Frida 脚本的大串 |
| 75 | 98 | 152 | 49.3% | `com/termux/app/zhicode/core/PlanApprovalGate.java` | 已重写（批 H）；同上 |
| 74 | 136 | 234 | 31.6% | `com/zhizhu/zhicode/ZhiFileProvider.java` | 已重写 |
| 53 | 299 | 886 | 6.0% | `com/zhizhu/zhicode/sandbox/SandboxGuestHost.java` | 已重写 |
| 50 | 76 | 130 | 38.5% | `com/termux/app/zhicode/api/HttpRequestTracker.java` | 已重写（批 H）；同上 |
| 46 | 55 | 120 | 38.3% | `com/termux/app/zhicode/termux/BashCompletionCoordinator.java` | 已重写（批 H）；同上 |

**这张表按重合行数排，但排下一批不该按它排。** 重合率的分母是**重写之后**的行数，
所以注释写得越细、同一处残留显示得越夸张；它又**不区分**相同的是算法还是签名，
于是会系统性地把「公开面大、逻辑少」的数据类排在最前面 —— 而这类文件恰恰最没得改
（批 H 就是这么被排出来的，四个文件实际各只有 4~6 行可动）。

换成按**语句行**排序之后（`PROVENANCE_ALGORITHM=1 bash tools/provenance.sh`），
前面几项是：

```
 语句  声明  骨架 字面量 语句占比  文件
  127    125    186     43     26%  core/ZhiCodeEngine.java
   71     52     73      8     35%  tasks/TaskStore.java
   62     36     51     33     34%  core/ContextCompactor.java
   54     16     96     74     22%  api/OpenAIResponsesProvider.java
   42     22     55     22     30%  tools/AndroidIntentBridge.java
   41     20     30     11     40%  tools/UnifiedDiff.java
   38     60     66     25     20%  termux/TermuxShellExecutor.java
   34     21     63     33     23%  api/OpenAIChatCompletionsProvider.java
   32     18     42      1     34%  storage/ApiSettingsStore.java
   27     14     31      2     36%  (zhizhu) ZhiFileProvider.java
```

这一列与人工判读的顺序一致得多：前十个文件正是真正还留着「读起来像原版」的判断与动作
的地方，而 `PlanWorkflowState`（17）、`PlanApprovalGate`（22）、`HttpRequestTracker`（15）
这些批 H 文件退到了中段。`SandboxKeeper`、`SandboxShell` 的语句行是 0，
`SandboxFrida` 的 75 行里有 43 行曾经是内嵌脚本的大串（字面量桶）——
那一段已经重写掉了（净相同行 2157 → 2118），过程与代价见下面「内嵌 Frida 载荷」一节。

（这四个文件在这一笔 `JsonItems` 之后都降了：ZhiCodeEngine 138 → 127、
ContextCompactor 74 → 62、OpenAIResponsesProvider 70 → 54、
OpenAIChatCompletionsProvider 39 → 34 —— 这一族模板正是最后那几行「读起来像原版」的语句。）


### 批 F（2/6）：终端面板改用 Compose 重写（已完成，待真机复验）

这一块是**全工程最大的单个残留**：`com/zhizhu/zhicode/TermuxTerminalPane.java`
583 行里有 565 行与原版逐行相同（95.4%）。拆开看它由两部分组成：

- **外壳**：工具栏、左边缘手势、会话抽屉、两行扩展键、快捷动作与重命名两个对话框、
  主题刷色、`termux.properties` 读取 —— 全是 IQ Code 自己写的界面代码；
- **终端本体**：Termux 上游的 `TerminalView`（Canvas 逐字符绘制 + CSI 解析 + 选区 +
  缩放手势，2453+1297 行，属那 7274 行 Termux 上游代码）。

所以重写的是**外壳**，终端本体仍然由 `AndroidView` 承载（`TerminalPane.kt` 里早就写明
「不把 TerminalView 重写成 Compose」，这个判断继续有效）。

**另一个发现**：`UiMotion.java` 的全部 25 个调用点**都在这个文件里**。
外壳一改成 Compose，它就成了死代码 —— 于是它连同 104 行重合一起被删除。
`UiMotion` 的职责（进场动画、按压反馈、抽屉滑动）现在由 `ZhiMotion` + Compose 的
`AnimatedVisibility` / `animateDpAsState` 承担。

结构：

```
compose/ui/panes/TerminalChrome.kt    工具栏 / 抽屉 / 扩展键 / 两种失败占位 / 状态接线
compose/ui/panes/TerminalDialogs.kt   快捷动作（11 项）与重命名
compose/ui/panes/TerminalPane.kt      接线（TerminalHolder 签名未变）
com/zhizhu/zhicode/TermuxTerminalPane.java   只剩 PTY：会话列表、JNI、环境变量、
                                             WakeLock、剪贴板、ANSI 调色板、两个 Client 接口
```

宿主对 Compose 的接口是「**不可变快照 + 变更回调**」而不是 `StateFlow`：
它是 Java，而 `StateFlow` 是 Kotlin 类型，从 Java 构造要绕到 `StateFlowKt` ——
那等于让最底层的宿主反过来依赖界面框架。回调只发**低频**变化：
`onTextChanged`（每次按键回声都触发）只负责让 `TerminalView` 重画，**不**通知观察者，
否则每敲一个字符就要重建快照并驱动一整轮 Compose 重组。

结果：

| | 重合行 | 现在行数 |
| --- | --- | --- |
| `TermuxTerminalPane.java` | 565 → **100** | 592 → 1056 |
| `UiMotion.java` | 104 → **0**（文件已删） | 392 → 0 |
| 全工程「真正属于 IQ Code」 | 5120 → **4551**（批 F 当时的数；现行 4406） | |

那剩下的 100 行是：31 行 `import`、若干 `}`、字段与构造器声明，以及
**只能在 View 一侧写**的 PTY 调用 —— `new TerminalView(getContext(), null)`、
`view.attachSession(attached)`、`view.post(...)` 里的三重校验、`JSONArray` 解析骨架。
这些相同不是因为抄，而是因为它们就是「在 View 里驱动这个上游渲染器」的唯一写法。
文件行数涨了（592 → 1056）是刻意的：注释写明了每处不许改的原因。
（其中最后一次增长来自 H3 的修复：重新挂载宿主 + 两次重试 + 取证日志。）

**外观只动了一处**：外壳配色改为跟随深浅主题（原先写死深色档）。深色档色值与改动前
逐字节相同（就是 `applyPaletteValues` 的那张表），浅色档启用了原作者写下但从未走到过的那张。
终端的 ANSI 调色板**没动** —— 让它跟随主题是另一次可见变更，不夹在结构重写里做。

**验证**：新增 `app/tests/TerminalPaneContractTest.java`（文本级第 23 条），钉住三件
「改坏了不报错、只会在真机上表现为终端不好用」的事：12 个扩展键的转义字节（用解码后的
字面量做**相邻性**检查，所以 ESC 与 TAB 的序列被互换会被抓出来）、11 项快捷动作的标签
与顺序（调用方按下标分发，重排会让「字体变大」点成「杀掉 shell」）、四个修饰键的
读后即清语义。另有编译、JVM 单测、provenance 全量重跑。

**UI 行为已通过真机验收**：按 A–H 清单跑了两轮，第一轮发现并修掉 H3
（旋转后终端一片空白），第二轮复验无遗留问题。覆盖范围含 H1/H2（反复切标签与开关抽屉）、
C4/C5/C6（改名、关闭会话、**切走再切回会话仍在**这个历史 bug 回归点）、D1、
E1/E2（剪贴板）、G1/H4、E7。H3 的根因、第一版修法为什么修反了、以及最终三层修法
记在 `docs/sandbox-host.md`（其中「`key(...)` 重建节点与复用同一个 View 实例互斥」
是一条可复用的教训）。

**仍然没有验证**：任何真实模型请求（会话内从未配置 API Key）。


### 批 H 的结论与预期相反：这四个文件已经没得改了

批 H 原本按上面的表面数字排出来 —— 四个「重合率 38%~55%」的小文件，
看着像最好啃的一块。真去读之后结论是反的：

| 文件 | 非空行（原版→现在） | 相同 | 这些相同行是什么 |
| --- | --- | --- | --- |
| `model/PlanWorkflowState.java` | 139 → 207 | 100 | 5 个枚举成员、9 个 `public final` 字段、12 个方法签名、2 处状态判断 |
| `core/PlanApprovalGate.java` | 98 → 152 | 75 | 3 个枚举成员、内部类声明、7 个 `import`、`try/finally` 骨架 |
| `api/HttpRequestTracker.java` | 76 → 130 | 50 | 2 个超时常量、`Scope` 内部类声明、10 个方法签名 |
| `termux/BashCompletionCoordinator.java` | 55 → 120 | 46 | 2 个 `CountDownLatch` 字段、4 个 getter 签名、`parseControlLine` 的 8 行 |

把相同行去重后逐条看，真正的**算法行**每个文件只剩 4~6 行，且都是「Java 里只有
一种写法」的那种：`long now = Math.max(System.currentTimeMillis(), updatedAt + 1L);`、
`if (separator != token.length() || separator <= 0) return null;`。

**这个「4~6」后来被拿来当作新分类器的标尺，结果发现两个数说的不是一件事。**
`PROVENANCE_ALGORITHM=1` 现在给出 `PlanWorkflowState` 有 **17 行语句**，
它们逐条是：4 行构造期归一化（`this.revision = Math.max(0L, revision);` 这种）、
6 行单行谓词（`public boolean isIdle() { return status == Status.IDLE; }`）、
`if (id.isEmpty()) id = UUID.randomUUID().toString();`、
两处 `return new PlanWorkflowState(…)`、以及 `Math.max(System.currentTimeMillis(), …)`。
「4~6」当时数的只是**有分支的行**（那两处状态判断），而分类器不可能、也不应该
把「单行谓词」从「语句」里摘出去 —— 它确实是一句有返回值的话。
所以 17 和 4~6 都对，只是口径不同：**排批次看 17 这个数**（它可跨文件比较），
判断「还剩多少真逻辑」时再逐条读全文。

顺带修掉分类器里一处真的误收：`public static final Status IDLE = Status.IDLE;`
原先因为带赋值号被算成语句行；它是枚举成员那种写法，现在归声明桶
（这个文件的语句行因此从 22 降到 17）。

所以这一批实际能做的只有两件事（都已做）：

1. **去掉同一件事的两份写法**
   - `PlanWorkflowState`：六个推进操作里有五个不动计划正文，却各自把
     `planFile, planText` 两个参数写了一遍 → 收成一个三参数的 `step(...)` 重载；
     `copy()` 与 `restore()` 原本各写一遍九个字段 → `copy()` 改为委托；
   - `BashCompletionCoordinator`：`publishControl` 与 `publishProcessExit` 里
     那 5 行「定下退出码」逻辑逐字相同 → 收成一个 `resolve(code, from)`，
     「先到者为准」的判定从此只有一处；
   - `PlanApprovalGate`：两个 catch 块做的是同一件事 → 合成
     `catch (InterruptedException | RuntimeException failed)`（精确重抛会原类型抛出，
     签名已声明 `InterruptedException`，合法）。
2. **删掉确认没人调用的 API**（`withPlanText` / `withPlanFile` / `awaitApproval` /
   `activeCount`，全仓库含 Kotlin 与测试搜不到调用方）。
   `pendingRequests()` **留着**：它是「批准卡片必须能被重新拿到」这条要求唯一的落点，
   同 `steeringToolInterrupt` 的处理 —— 在注释里写明它现在没有调用方，而不是删掉。

结果：四个文件合计 291 行相同 → 271 行（-20）。行数反而涨了（注释变多），
这与批 D 一样是刻意的。

**这条结论要记住**：`PROVENANCE_PER_FILE` 的重合率是「相同行 ÷ 现在行数」，
它**不区分**相同的是算法还是签名。所以按重合率排批次会系统性地把
「公开面大、逻辑少」的数据类排到前面 —— 而恰恰是这类文件最没得改。
下一步该按「相同行里有多少是算法」排序，不是按重合率。



### 批 I：沙箱宿主层（已完成，但只有一件真正可改）

沙箱宿主层 14 个文件合计 563 行相同。这些文件全都改过名
（`SandboxGuestDebug` ← `SandboxProcessDebug`、`ZhiSandbox` ← `IQSandboxEngine` 等），
所以必须走 `PAIRS` 配对表，按路径找是找不到的。

先用度量逐个文件量了一遍，**结果决定了这一批只做了一个文件**。
下表是**重写之后**的四桶口径（用当前分类器测得；`SandboxGuestDebug` 一行的变化见下）：

| 文件（现在 ← 原版） | 相同 | 骨架 | 字面量 | 声明 | 语句 |
| --- | --- | --- | --- | --- | --- |
| `SandboxGuestDebug` ← `SandboxProcessDebug` | 89 | 48 | 21 | 8 | **12** |
| `SandboxOverlay` ← `SandboxFloatingController` | 24 | 16 | 0 | 0 | 8 |
| `SandboxFrida` ← `SandboxFridaBridge` | 75 | 20 | 43 | 7 | 5 |
| `SandboxPrefs` ← `SandboxSettingsStore` | 33 | 19 | 0 | 9 | 5 |
| `SandboxConsole` ← `SandboxDebugLog` | 37 | 19 | 2 | 11 | 5 |
| `SandboxProcess` ← `SandboxProcessRole` | 18 | 11 | 1 | 2 | 4 |
| `ZhiSandbox` ← `IQSandboxEngine` | 46 | 35 | 0 | 7 | 4 |
| `SandboxRpcService` ← `SandboxControlProvider` | 42 | 26 | 7 | 6 | 3 |
| `SandboxGuestHost` ← `SandboxAgentBridge` | 53 | 44 | 0 | 7 | 2 |
| `SandboxRpc` ← `SandboxHostClient` | 13 | 9 | 0 | 3 | 1 |
| `FridaEnv` ← `FridaRuntimeManager` | 31 | 20 | 6 | 4 | 1 |
| `SandboxBoard` ← `SandboxDashboardActivity` | 37 | 32 | 0 | 4 | 1 |
| `SandboxShell` ← `SandboxTermuxBridge` | 23 | 22 | 0 | 1 | **0** |
| `SandboxKeeper` ← `SandboxGuardService` | 12 | 12 | 0 | 0 | **0** |
| 合计 | 533 | 333 | 80 | 69 | **51** |

（重写前这张表是 563 行相同：三桶口径下 343 骨架 / 93 字面量 / 127 其它。
两个数字的差 30 行就是下面那一处重写的收益；三桶与四桶不可逐格对比，
因为分桶规则本身变了。）

两个文件（`SandboxShell`、`SandboxKeeper`）的相同行**全是括号与 import 或注解** ——
按现在的口径它们的语句行是 0，这正是「这两个文件没什么可改」的形式化说法。
把这一层里非骨架、非字面量的行（现在口径下是 69 行声明 + 51 行语句）按内容打出来看，
绝大多数是 `break;`（13 个）、`return true;`、`try {`、`case MotionEvent.ACTION_DOWN:`、
方法签名与字段声明。真正的算法只有十来行，而且都是绕不开的：`System.load(canonical);`、
`Runtime.getRuntime().gc();`、`while ((b = in.read()) != -1 && b != 0) out.write(b);`

所以只改了一个：**`SandboxGuestDebug` 的 13 分支 switch**。
`dispatch()` 原先是一个十三个 case 的 switch，而每个 case 做的都是同一件事——
算一个值、放进同一个信封、`break`。同一件事写十三遍的代价不是长度，
而是「加一个动作要动四行、还得自己找对位置」。现在是一张
`Map<String, Route>` 路由表（键名 + 算法），加一个动作只加一行。

这一处就吃掉了这一批的大部分余量：

    相同行           119 → 89
    语句行（该文件的） 34 → 12   （那个 34 是三桶口径下的「其它行」）
    沙箱宿主层        563 → 533

**剩下的 51 行语句没有再动**，因为它们要么是协议串（动作名、JSON 键，宿主与 Agent 工具
按同一份名字对齐，改了会让两边对不上），要么是 `return true;` 这种没有第二种写法的行。
为了把数字压低去重排一个 `try` 的位置，只会让代码变难看。


### 批 E（1/3、2/3）：终端执行器、任务存储与提示词装配（已完成）

| 文件 | 重合 | 现在 | 做了什么 |
| --- | --- | --- | --- |
| `tools/AndroidIntentBridge.java` | 297 → 141 | 23.2% | 三类意图各自成方法；`am start` 解析与 shell 词法分析独立出来 |
| `agents/AgentDefinitionLoader.java` | 126 → 45 | 30.0% | 解析与合并分开；四层来源的优先级写进注释 |
| `background/*` 之外的小件 | — | — | `AndroidSecretStore`、`PermissionGate`、`VisionMessageFilter` |
| `core/SystemPromptBuilder.java` | 48 → 6 | 2.5% | 规则清单从字符串相加改成数组 |
| `tasks/TaskStore.java` | 352 → 204 | 37.4% | 一任务一文件、id 高水位文件、进程内锁 + 文件锁 |
| `termux/TermuxShellExecutor.java` | 435 → 189 | 20.3% | 拆成启动前检查 / 包管理锁 / 进程构建 / 等待完成 / 收尾 |
| `storage/SessionStore.java` 等 | 见上一节 | — | 存储层统一走 `AtomicFiles` |

这一批里最需要记下来的一件事是**系统提示词的逐字节校验**。

`SystemPromptBuilder` 是行为契约：那些英文句子决定 agent 会不会先确认再动手、
会不会声称自己做了没做的事。改写它属于「改动行为」，不能和「改写实现」混在一起。
所以这次不是靠看，而是**用程序验**：把改动前后两个版本的 `build()` 各自编译成一个
不依赖 Android 的独立程序（把四个 `config.*` 字段访问换成固定值），用同一组输入打印
提示词，然后比较：

```
old  12381 bytes
new  12381 bytes
cmp  →  完全相同
```

顺带修掉一个更隐蔽的毛病：`SandboxIntegrationStructureTest` 与
`FridaDeadlockRegressionTest` 都对 `SystemPromptBuilder.java` 的**源码**做 `contains`，
于是「换行打在哪个位置」也变成了契约 —— 重排之后它们失败了，而提示词输出并没有变。
两个测试现在改成先从源码里抽取双引号字面量、拼回内容再比较，检查的是
「这个文件最终会输出什么文字」，而不是「作者把这些字打在哪儿」。

### 批 E（3/3）：存储层最后两块（已完成）

| 文件 | 重合 | 现在 | 做了什么 |
| --- | --- | --- | --- |
| `storage/SessionStore.java` | 284 → 199 | 17.5% | 「读」与「写」各收敛成一个入口，见下 |
| `storage/ApiSettingsStore.java` | 109 → 93 | 13.3% | 设置项改成一张表，读写同源 |

`SessionStore` 的三处结构收敛：

1. **三种读法收成一个 `scan(file, strict)`**。原先宽容读（`readRows`）与严格读
   （改历史前必须「整个文件都读懂了」）是两段几乎一样的逐行循环。合成一个之后
   `strict` 就不是一个零散的布尔参数，而是「这份数据我能不能安全重写」这件事本身。
2. **两种写法收成一个 `appendLines(file, lines, durable)`**。区别只有是否 fsync：
   消息是高频写入（每次 fsync 会让打字卡顿），改备注是低频且重要。原先两份方法体
   完全重复。
3. **整文件替换改走 `AtomicFiles`**。「写临时文件 → fsync → rename」这段在上一批已经
   抽成 `AtomicFiles` 了，这里跟着用，本类只保留它特有的一步：替换后恢复原 mtime。

另外把 `ROW_*` / `"type"` / `"payload"` 这类固定字符串收进 `RowType` / `Role` /
`Block` / `Field` 四个内部常量类。理由不是好看：`"payload"` 在原先的代码里出现十几次，
每处各写一份字面量的话，改字段名时漏掉的那一处只表现为「某类历史读不出来」。

`ApiSettingsStore` 的改动更值得说，因为它修的是一类真实缺陷：原先 `loadGlobal()` 与
`saveGlobal()` 是两份各 25 行的键名清单，必须一直保持一致 —— 而「加了新设置但只加了
一半」的后果是「设置能存进去、重启后消失」，不报错、不崩，只表现为用户抱怨。
现在只有一张 `SETTINGS` 表，读与写都从它派生，默认值直接取 `SessionConfig` 的字段初值
（所以也不存在「默认值改了一处忘了另一处」）。压缩比例是 double、prefs 只支持按位存
long，因此显式留在表外。

这两块都没有改行为：`SessionStore` 的公开方法签名一个没动，`ApiSettingsStore` 的
prefs 键名与值格式一字未改（键名是持久化契约，改了等于丢用户设置）。
### 批 D（1/2）：Agent 主循环（已完成）

这是全工程最大的一块（1233 行重合，98.6%），也是唯一一块「改错了会让 agent 变傻」的地方。

| 文件 | 重合 | 现在 | 做了什么 |
| --- | --- | --- | --- |
| `core/ZhiCodeEngine.java` | 1233 → 492 | 23.5% | 主循环、历史自愈、上下文压缩三段各自拆开 |
| `core/SteeringQueue.java` | 新文件 | — | 预输入队列与两个打断标记从引擎里搬出来 |

主循环原来的形状是一个三百多行的双层 `try`，`continue` 散布在四处。现在按「一轮”拆开：

```
runAgent        异常分类（取消 vs 真错误）与收尾
 └ runTurnLoop  maxAgentTurns 的计数
    └ runOneTurn  请模型回一条消息
       ├ finishAssistantTurn   没有工具调用：收尾 / 续跑 / 空回复重试
       └ runToolPhase ─ runToolCalls  执行工具并写回结果
```

拆的时候有三处**不能**拆错的语义，都写进了注释：

1. `continue` 在 `for` 循环里会执行 `turn++`，所以拆成「返回 false 表示再来一轮」之后，
   计数必须由调用方推进 —— 写错的话 `maxAgentTurns` 会变成一个不生效的上限。
2. `onToolBatchCompleted` 在 `finally` 里，某条工具抛异常时也必须发出去，否则界面上
   那一批的进度条会一直转。
3. 工具结果必须写回历史，**即使后面的工具被取消**。丢掉它们等于让模型以为那些工具
   从没被调用过，它会重做一遍 —— 而重做的可能是有副作用的操作。

顺手修掉的一处**死变量**：`repairToolHistory` 的第一遍扫描里算了三个集合
（`callsSeenInOrder` / `paired` / `allCalls`），其中 `allCalls` 从头到尾没被读过，
`callsSeenInOrder` 也只在那一遍里用。整个第一遍其实就是在问一句
「哪些调用先出现、之后才出现同 id 的结果」，现在它就是一个方法
（`findPairedToolCallIds`）。

### 一处**从未被置为 true** 的标记（记下来，因为它看起来很像是活的）

`steeringToolInterrupt` 与 `steeringModelInterrupt` 两个标记，在引擎里只有
`getAndSet(false)`、`get()` 和 `set(false)` 三种读法 —— **没有任何地方把它们置为 true**。
也就是说：

- 「模型请求被预输入打断」那条分支（它会保留已经流出来的半截回复、通知界面、
  然后按新输入继续）永远走不到；
- 「工具执行因为用户纠正而中断」那条错误文案也永远走不到；
- `consumeSteeringInterruptIfNeeded()` 里那句 `Thread.interrupted()` 永远不会执行。

这一批**没有删掉**它们，而是把它们搬进了 `SteeringQueue`，并在类注释里写清了
「目前没有任何地方把它们置为 true」。理由是界面（`ZhiEngineController`）已经实现了
`onResponseInterruptedBySteering`，说明这条路径是被期望存在的；删掉标记等于把这个
功能的存在证据一起删掉。留着的代价是一个读者可能误以为它在起作用 ——
所以那句话必须写在那儿。

### 行数涨了，而且是刻意的

| | 原版 | 重写前 | 重写后 |
| --- | --- | --- | --- |
| 非空行 | 1245 | 1250 | 2094 |
| 其中注释 | 46 | 48 | 442 |
| 其中代码 | 1199 | 1202 | 1652 |

代码多了约 38%，注释多了近十倍。两个原因：一是原先大量「一行三个语句」的紧凑写法
在重写时必须展开（紧凑写法正是与原版逐字相同的那部分）；二是这次把**行为契约**
写在了函数旁边，例如「`continue` 会执行 `turn++`」「工具结果即使被取消也要写回」
「提交压缩前必须比对历史」—— 这些是改动这个文件时最容易踩坏、而踩坏了不一定报错的地方。

### 批 D（2/2）：`core/ContextCompactor.java`（已完成）

388 → 195 行重合（99.5% → 31.5%）。它负责压缩的切点计算、摘要提示词组装与结果解析。

这一块的特别之处在于：它产出的东西几乎全是**给模型看的**（摘要提示词、压缩后的上下文
包裹格式、被缩减过的历史），而且改坏了不会报错 —— 只会让摘要质量悄悄变差。

所以这里用的办法与 `SystemPromptBuilder` 那次一样，但做得更硬：

1. 先写一个一次性探针，把**重写前**的实现对同一组输入（覆盖 text / thinking / tool_use /
   tool_result / image 五种块）的完整输出原样 dump 出来；
2. 把 dump 存成 `app/src/test/resources/compaction-prompt-baseline.txt`；
3. 重写之后，由 `ContextCompactionTextTest` 重新生成同一份 dump 并与基线逐字比较。

这条断言是长期保留的 —— 以后再动这个文件，提示词改了一个字都会红。
基线文件里存的就是那些句子本身，可以直接查阅，不需要相信任何人的描述。

除此之外的处理：三个重试档位（正文 30k/14k/8k、工具 8k/3.5k/2k）从内联三目改成数组；
摘要提示词的九条要求从九行 `append` 改成一份 `SUMMARY_SECTIONS` 数组（它是一份清单，
要能一眼看出有没有漏掉某一类信息）；逐消息、逐块的 token 估算各拆成一个方法。

### 批 B：`api/` 协议层（已完成）

这一批针对工程里「最不该看着像原版」的地方 —— 协议层是纯粹的技术实现：
没有界面、没有用户可见的取舍，写出来长什么样完全取决于写的人。

已经重写完的（每一条都是先写契约断言、再改实现）：

| 文件 | 重合行 | 现在 | 做了什么 |
| --- | --- | --- | --- |
| `api/compat/ReasoningMapper.java` | 97% → 16.4% | 档位映射拆成明确的分档表，删掉两个零调用的死方法 |
| `api/AnthropicMessagesProvider.java` | 98.8% → 26.5% | 传输 / `StreamDecoder` / 块累积器三层 |
| `api/OpenAIChatCompletionsProvider.java` | 99.6% → 26.2% | 统一流式与非流式的字段读取，抽出 `HistoryMapper` |
| `api/OpenAIResponsesProvider.java` | 99.8% → 27.6% | `HistoryBuilder` / `StreamDecoder` / `KeyIndex` 四层 |
| `api/ModelCatalogClient.java` | 100% → 21.4% | 网络与解析分开；协议名不再内联 |
| `api/HttpRequestTracker.java` | 88% → 39.6% | 失败归类提成纯函数 `RequestFailure` |
| `api/ModelProvider.java` | 100% → 26.9% | 接口只留两个方法，两个嵌套类型提成顶层 |

顺带做掉的重复：`stripTrailingSlash` 原来在四个文件里各有一份（三个 provider 各私有
一份 + 端点类一份），现在只剩 `ApiEndpointResolver` 里那一份。它们其实并不完全一样 ——
Anthropic 那份遇到 `null` 会抛 NPE，另两份返回空串；合并时取了后者
（更宽容，且没有任何调用点依赖那个 NPE）。

另外两个协议名常量集合并成了一个枚举 `ApiProtocol`。原来「协议名 → 实现」和
「协议名 → 目录端点」是两条各自内联字符串的 `if` 链，而协议名是**存盘格式的一部分**，
写错一处不会编译失败，只会表现为「报文格式对不上、服务端报解析错误」。
收进枚举之后，「认识的协议」只有一处定义，而且 `switch` 覆盖枚举要求列全，
将来加了协议却忘了分派会直接编译不过。

小文件的重合率有**下限**：`api/ApiUrlPolicy.java` 从 43% 降到 18.5%，剩下的 10 行是
`package`、两个 `import`、类声明、`return base;` 这种「一行只干一件事」的行 ——
任何人在这个需求下都会写出同样的形状。判断「改没改写」不能只看百分比，
要看**算法与结构**：上表里降到大件的都换了结构，剩下的百分比都落在这类不可避免的行上。

### 批 C：数据类与 Agent 工具（已完成）

这一批是「接口与数据形状」层：字段名、工具名、schema 键名、输出格式都是**契约**
（写进了会话 JSONL、设置、以及发给模型的提示词），所以改的只能是内部结构、
分解方式与命名。共 40 个文件、重合行 8632 → 7917（-715）。

按性质分三组。

**值对象（字段名一律不动）**：`model/PlanWorkflowState`（55.1% → 新的状态机写法）、
`model/SessionConfig`、`model/ApiProfile`、`model/ToolExecutionResult`、
`model/AssistantTurn`、`model/ToolCall`、`agents/AgentDefinition`、`agents/AgentTask`。
其中三处是真正的结构改动，不只是重写注释：

1. `PlanWorkflowState` 把「哪个阶段允许哪种操作」写成了一个统一的前置检查
   （`requireStatus` / `requireWorkflow`），并**把时间戳改成严格单调**：
   新快照的 `updatedAt` 取「当前时间」与「上一版 + 1」的较大者。系统时钟被回拨时
   （对时、换时区），原来的写法会让新快照看起来比它替换掉的那一版更旧，
   而界面是按时间戳判断版本的。
2. `SessionConfig.copy()` 把 volatile 的 `planWorkflowState` 先落到局部变量再拷：
   原来直接读两次，两次之间可能被别的线程换掉，于是拷到一个「半新半旧」的组合。
3. `ToolExecutionResult` 的六个工厂方法收敛到一个私有构造路径，
   每一种「成功/失败 + 有无 diff + 有无附加内容」的组合都由一个具名方法表达。

**路径与 schema 基础件**：`tools/PathPolicy`（`Set.of` 换成 `Arrays.asList`，见下）、
`tools/ToolSchemas`、`tools/ZhiTool`（接口，签名未动）、`core/PermissionModePolicy`。

`ToolSchemas` 的重写解决了一个真实的签名污染：`JSONObject.put` 声明受检的
`JSONException`，于是原来每个工具的 `inputSchema()` 都要自己写 try/catch
（45 行里有 5 段几乎相同的 catch）。现在只有一处 `with(...)` 负责转换，
其余地方链式拼装。

`PermissionModePolicy.capSubagent`（97 行 → 89 行，重合 35 → 21）把原来嵌套四层
`if` 的判断改成了一张「父级模式 → 子代理可以要求哪些模式」的表。表里的每一行都能
从一条原则推出来（**子代理只能收紧、不能放宽父级权限**），而原来的嵌套 if 读不出
这条原则 —— 改错一个分支的后果是某个子代理悄悄获得了它不该有的写权限。

**Agent 工具（36 个）**：`Read`/`ReadMany`/`Stat`/`Tree`/`LS`/`Glob`/`Grep`/
`Write`/`Edit`/`MultiEdit`/`Copy`/`Move`/`Mkdir`/`Delete`/`TaskCreate`/`TaskGet`/
`TaskList`/`TaskUpdate`/`mcp_list`/`mcp_call`/`GitStatus`/`EnterWorktree`/`AndroidIntent`/
`Root`/`ui_canvas`/`ToolRegistry` 等。

工具名、schema 属性名、输出格式与错误文案全部保持逐字不变（它们都被测试与提示词
依赖）。改的是：把重复的文件读写抽成 `TextFiles`；把每个工具里的内联上限判断
提成具名常量并在注释里写清「这个上限挡的是哪一种失败」；
`ToolRegistry` 的构造从 36 条平铺的 `register(...)` 改成按用途分组的清单，
顺序仍然固定（顺序决定提示词内容，从而决定上游的提示词缓存能不能命中）。

**一个必须记下来的坑**：第一批写法里用了 `Set.of(...)` / `List.of(...)`。
它们是 Java 9 的 API，在 Android 上要 API 30 才有，而本工程 minSdk 24
且**没有开启核心库脱糖** —— 也就是说编译会通过、在 Android 7～9 的设备上
一运行就 `NoSuchMethodError`。已全部换成 `Arrays.asList`。
这个错误编译器和单元测试都发现不了（单元测试跑在 JDK 上，那里什么都有），
所以它值得单独写在这里。

**另一个坑**：`GlobTool` 的 javadoc 里写了正则片段 `(?:.*/)?`，
其中的 `*/` 提前结束了注释块，后面整段代码被当成类体外面 —— 报错是
「illegal start of type」加一串看不懂的错位信息。写含正则的注释时要注意这个。

### 批 A：删掉的死代码（已完成）

这四个文件在整个工程（含脚本、清单、Bcore）里都搜不到引用，逐个确认后删除：

| 文件 | 行 | 确认方式 |
| --- | --- | --- |
| `com/zhizhu/zhicode/MarkdownRenderer.java` | 265 | 全仓 grep 无任何引用；Compose 侧有自己的 Markdown 渲染层 |
| `com/zhizhu/zhicode/ToolActivityGrouper.java` | 91 | 全仓 grep 无任何引用 |
| `com/zhizhu/zhicode/ZhiDocumentsProvider.java` | 126 | 全仓 grep 无引用，且**清单里根本没有注册**（DocumentsProvider 不注册就不可能被调用） |
| `com/zhizhu/zhicode/FloatingOverlayService.java` | 130 | 只在清单里注册，代码里没有任何启动点 |

顺带做了一件必要的事：`FloatingOverlayService` 是 `SYSTEM_ALERT_WINDOW` 权限的唯一使用者，
它既然删了，那条权限声明也一并去掉 —— 留着一个没有使用者的「可在其它应用上层显示」权限，
对用户只是无谓的授权请求。`EnvDoctor` 仍会显示这项权限的授予状态，那只是诊断信息。

删除后重合行从 10132 降到 **9592**（-540），文件数 189 → 185。
度量工具的自检立刻起了作用：它报出「PAIRS 里的本工程文件不存在:
com/zhizhu/zhicode/ZhiDocumentsProvider.java」—— 删文件后忘了同步配对表，
正是这类自检要防的静默失真。

三点必须说清楚：

1. 头部那些 Termux 文件重合 100% 是**正确状态**，不是漏洞。
   它们由 Apache-2.0 授权且已在 `NOTICE` 里声明，重写它们既无意义也是错的。
2. 排在最前面的**不是** `api/`，而是 `core/ZhiCodeEngine.java`（1238 行、99.2%）。
   它此前因为度量工具的漏算显示为 0，是全工程最大的单块残留。**应最先处理**。
   （它同样出现在 `ZhiTool.java`：27 行、100%，但那是接口，按约定只改实现不动签名。）
3. 真正剩下、值得处理的是 `core/`、`api/` 与 Compose 界面层的几个大文件，
   它们目前几乎是原样保留的。全部逐行相同行数、扣除 Termux 上游之后是 **9592 行**。
### 数据层（已完成）

| 类 | 原版行 | 现在行 | 相同行 | 重合 |
| --- | --- | --- | --- | --- |
| `McpConfigStore` | 80 | 239 | 32 | 13.4% |
| `AndroidSecretStore` | 112 | 224 | 43 | 19.2% |
| `ApiSettingsStore` | 331 | 584 | 117 | 20.0% |
| `SessionStore` | 642 | 1202 | 285 | 23.7% |
| `PlanStore` | 87 | 142 | 72 | **50.7%** |

`PlanStore` 的 50.7% 需要解释，否则这个数字会误导：71 行相同里，
9 行是 `import`、16 行是单独一个右括号、2 行是注释标记；剩下 44 行「实质相同」
主要由两类构成 —— **必须保持不变的公开 API 签名**（被 `ZhiCodeEngine` 调用）
以及原子写入的固定步骤（写 tmp → fsync → `Os.rename`）。
这份文件本来就短而且是正确的，没有为了压低数字去改写正确的代码。

**由此得出一个度量上的结论**：对「小、正确、API 受约束」的工具类，
重合率主要由保留的 API 与语言固定写法决定，用「还剩多少别人的代码」这个口径
去读它会失真。比较不同文件时请留意 `原版行` 与 `现在行` 的差距
（差距越大，说明改动越多），而不是只看百分比。

### 度量口径

本表与上面的总计都取自 `provenance.sh` 的方法（`diff` 对齐后的未变块）。
此前的若干提交说明里我用过另一种方法（归一化后**排序**再取交集），
它会把两文件里出现在不同位置的相同行也计入，因此同一个文件会得出偏高的数字。
两种数字不可直接比较，**以本文档为准**：

| 类 | 排序取交集（早先引用的数字） | diff 对齐（本文档与脚本采用） |
| --- | --- | --- |
| `SandboxGuestHost` | 8.5% | **6.0%** |
| `SandboxPrefs` | 28.1% | **22.6%** |
| `ZhiSandboxTool` | 5.9% | **5.2%** |
| `ZhiDebugTool` | 7.1% | **4.8%** |

差别集中在 `}`、`import`、空行这类随处可见的行上 —— 排序后它们必然「相同」，
而对齐后只有当它们真的落在同一位置时才算。

### 沙箱宿主层（已完成，含 Agent 侧两个工具类）

16 个文件，563 / 4716 行 = **11.9%**（从重写前的 96% 降下来）：

---

## 怎么重新验证本文件的每一条

```bash
# 1. 各模块是否都带了许可文件
find . -maxdepth 2 -name LICENSE -o -maxdepth 2 -name NOTICE | grep -v '/build/'

# 2. 归属度量（需要本机有原版 IQ Code）
#    默认一份输出就够看结论：汇总表 + 四桶构成 + 可扣行 + 净相同行 + 漏算自检
#    （有 ⚠ 就说明数字被低估）
bash tools/provenance.sh
#    下面几个是叠加口径，按需要加：
PROVENANCE_COMPOSITION=1 bash tools/provenance.sh   # 追加语句行去重种数 + 语句行全文
PROVENANCE_SHAPE=1      bash tools/provenance.sh    # 语句行还能收敛多少（多行模板，给的是上限）
PROVENANCE_SHAPE=1 PROVENANCE_SHAPE_UNIQUE=1 bash tools/provenance.sh
                                                    # 再加「只出现一次的形状」逐行清单 ——
                                                    # 「这一行有没有第二种写法」靠它逐条读
PROVENANCE_DECLARATIONS=1 bash tools/provenance.sh  # 声明桶全文（判公开面能不能收窄）
PROVENANCE_LITERALS=1     bash tools/provenance.sh  # 字面量桶拆两份：可扣的协议串 / 其余文案
#    （审计文件 build/provenance-protocol-strings.txt 每跑一次都重写，分三段：
#      [files] / [position] / 没有被扣的 —— 「净相同行」的每一处扣减都能在里面找到出处）
PROVENANCE_ALGORITHM=1  bash tools/provenance.sh    # 按语句行排序的清单（排下一批看这个）
PROVENANCE_PER_FILE=1   bash tools/provenance.sh    # 按重合行数排序（历史口径，仅作参考）

# 3. 结构测试里有 LicenseNoticeStructureTest 守着这些文件的存在，
#    它们不会在下一次重构里被静默删掉。
#    这一套里还包含一条**用 node 跑的**（app/tests/js/frida-agent-harness.mjs）：
#    它把内嵌 Frida 载荷从 Java 源码里抽出来真加载、按信箱协议发命令读响应。
#    node 不在时会**明确失败**，不静默跳过 —— 静默跳过等于这条守卫不存在。
bash test-source-no-build.sh

# 3b. 只跑那一条行为测试（改内嵌载荷时用它，比整套快）
node app/tests/js/frida-agent-harness.mjs .

# 4. 行为测试（与上一条互补，不是替代）
#    上面那套是文本级断言：读源码字符串，能防「重写时漏掉一个分支」，
#    但证明不了运行时行为 —— 档位映射写错一档、路径去重判断反了，
#    都不会让编译失败。api/ 那一层没有任何 android.* 依赖，所以有真正的 JVM 单测。
./gradlew :app:testDebugUnitTest
```

**两套测试的分工**（判断标准：如果一处改动「写错了也不会编译失败」，它需要的是 JVM 单测）：

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
| IQ Code | MIT (© 2026 IQge) | `THIRD-PARTY-LICENSES/IQ-Code-MIT.txt`（原作者已另许可改写与不强制署名，保留为自愿致谢，见「署名」一节） |
| Termux bootstrap（bash / coreutils / apt / …） | 各自许可 | 各自程序与 `termux-packages` |
| 蜘蛛自身 | MIT | `LICENSE` |

---

## 发行前还需处理

1. **把版权主体填成真实名称。** `LICENSE` 与 `THIRD-PARTY-LICENSES/MIT.txt` 里
   目前写的是占位符 `蜘蛛 (ZhiCode) contributors`。
2. **确认 bootstrap 的源码对接收者可达**（见上文「一处仍需自己确认的事」）。
3. **`NOTICE` 提到但不覆盖**：通过 Gradle 引入的 Miuix、AndroidX、Compose 构件
   会随 APK 一并分发，其许可原文随构件本身提供。若要做正式发行，
   建议在「关于」页里放一份可滚动查看的完整许可列表。
