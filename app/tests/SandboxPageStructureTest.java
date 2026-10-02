import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 「ZhiCode 沙箱」整页的结构契约。
 *
 * <p>这一页是**独立 Activity**（`SandboxBoard`）里的整页，不属于设置页栈，
 * 所以它自己拼骨架 —— 而它上一次拼出了三个只有真机上点得出来的毛病：
 *
 * <ol>
 *   <li><b>弹窗宿主挂在 Scaffold 之外</b>：四个框（诊断详情、隐藏 Root 确认、
 *       清数据/卸载确认、Frida 安装确认）全部不显示，而且 `state.dialog` 停在
 *       非 null —— 点了没反应，不崩、不报错。这是 Miuix 弹层的注册机制决定的
 *       （见下面 {@link #requireOverlaysInsideScaffold} 的注释），
 *       和技能页当初「加号点不了」是同一个坑。</li>
 *   <li><b>骨架没跟上二级页</b>：`SmallTopAppBar` 固定小标题、没有
 *       `nestedScroll` / `overScrollVertical` / `VerticalScrollBar`，
 *       于是滚动手感与设置二级页不一致。</li>
 *   <li><b>应用卡片六个按钮排两行三列</b>：`进程 / SO 基址` 只有 1/3 屏宽，
 *       而 Miuix 按钮最小宽度是 58dp，文字会被挤断；六个同权重按钮也没有主次。</li>
 * </ol>
 *
 * <p>这些都是「编译得过、跑起来也不抛」的问题，只能靠结构断言钉住。
 */
public final class SandboxPageStructureTest {
    private static final String PAGE =
        "app/src/main/java/com/zhizhu/zhicode/compose/ui/sandbox/ZhiSandboxScreen.kt";

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static String read(Path root, String path) throws Exception {
        return new String(Files.readAllBytes(root.resolve(path)), StandardCharsets.UTF_8);
    }

    /** 去掉全部空白后再比较：断言关心的是标识符与先后关系，不该被缩进/换行左右。 */
    private static String squash(String source) { return source.replaceAll("\\s+", ""); }

    /**
     * 去掉注释，但**保留字符串字面量**。
     *
     * <p>反面断言（"这东西必须不存在"）在这份工程里被咬过一次：注释里为了说明
     * 「这里原来写的是手写 rotate(-90f) 假左箭头」而把那段代码原样引了一遍，
     * 于是"必须删掉"的断言去匹配自己的说明文字，永远红。
     * 反面断言一律在去注释后的源码上做。
     */
    private static String stripComments(String src) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < src.length()) {
            char c = src.charAt(i);
            if (c == '/' && i + 1 < src.length() && src.charAt(i + 1) == '/') {
                int nl = src.indexOf('\n', i);
                i = nl < 0 ? src.length() : nl;
                continue;
            }
            if (c == '/' && i + 1 < src.length() && src.charAt(i + 1) == '*') {
                int close = src.indexOf("*/", i + 2);
                i = close < 0 ? src.length() : close + 2;
                continue;
            }
            if (c == '"') {
                int j = i + 1;
                while (j < src.length() && src.charAt(j) != '"' && src.charAt(j) != '\n') {
                    j += src.charAt(j) == '\\' ? 2 : 1;
                }
                out.append(src, i, Math.min(j + 1, src.length()));
                i = j + 1;
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    /**
     * 跳过字符串字面量与注释，返回 [from] 之后下一个 [open] 的下标。
     *
     * <p>不做这件事就会踩到本工程已经被咬过两次的坑：注释与字符串里出现的
     * `{` / `}` / `/*` 会把括号配平算歪，于是断言在一个**错的位置**上成立或失败。
     * 三引号字符串、行注释、块注释都要跳。
     */
    private static int nextOpen(String src, int from, char open) {
        int i = from;
        while (i < src.length()) {
            char c = src.charAt(i);
            if (c == '/' && i + 1 < src.length() && src.charAt(i + 1) == '/') {
                int nl = src.indexOf('\n', i);
                i = nl < 0 ? src.length() : nl + 1;
                continue;
            }
            if (c == '/' && i + 1 < src.length() && src.charAt(i + 1) == '*') {
                int close = src.indexOf("*/", i + 2);
                i = close < 0 ? src.length() : close + 2;
                continue;
            }
            if (c == '"') {
                int j = i + 1;
                while (j < src.length() && src.charAt(j) != '"') {
                    j += src.charAt(j) == '\\' ? 2 : 1;
                }
                i = j + 1;
                continue;
            }
            if (c == open) return i;
            i++;
        }
        return -1;
    }

    /** 从 [at]（必须是 [open]）配平到对应的 [close]，返回它的下标；失败返回 -1。 */
    private static int matchPair(String src, int at, char open, char close) {
        int depth = 0;
        int i = at;
        while (i < src.length()) {
            char c = src.charAt(i);
            if (c == '/' && i + 1 < src.length() && src.charAt(i + 1) == '/') {
                int nl = src.indexOf('\n', i);
                i = nl < 0 ? src.length() : nl + 1;
                continue;
            }
            if (c == '/' && i + 1 < src.length() && src.charAt(i + 1) == '*') {
                int close2 = src.indexOf("*/", i + 2);
                i = close2 < 0 ? src.length() : close2 + 2;
                continue;
            }
            if (c == '"') {
                int j = i + 1;
                while (j < src.length() && src.charAt(j) != '"') {
                    j += src.charAt(j) == '\\' ? 2 : 1;
                }
                i = j + 1;
                continue;
            }
            if (c == open) depth++;
            else if (c == close) {
                depth--;
                if (depth == 0) return i;
            }
            i++;
        }
        return -1;
    }

    /**
     * 取 `Scaffold(...) { ... }` 那个**尾随 lambda 的函数体**源码。
     *
     * <p>做法：找到 `Scaffold(`，把它的实参括号 `(...)` 配平到 `)`，
     * 之后的第一个 `{` 就是尾随 lambda 的开头，再配平到 `}`。
     * 因为实参里还有 `topBar = { ... }` 这样的块，不能简单取「第一个 `{`」。
     */
    private static String trailingLambdaBody(String src, String call) {
        int at = src.indexOf(call);
        require(at > 0, "找不到调用：" + call);
        int argOpen = src.indexOf('(', at);
        require(argOpen > 0, call + " 后面应该有实参括号");
        int argClose = matchPair(src, argOpen, '(', ')');
        require(argClose > 0, call + " 的实参括号没有配平");
        int bodyOpen = nextOpen(src, argClose + 1, '{');
        require(bodyOpen > 0, call + " 后面应该有尾随 lambda");
        int bodyClose = matchPair(src, bodyOpen, '{', '}');
        require(bodyClose > bodyOpen, call + " 的 lambda 没有配平");
        return src.substring(bodyOpen, bodyClose);
    }

    public static void main(String[] args) throws Exception {
        Path root = Paths.get(args.length == 0 ? "." : args[0]).toAbsolutePath().normalize();
        String page = read(root, PAGE);
        // 断言一律对**去注释后**的源码做：注释里为了讲清缘由会引到那段代码本身，
        // 反面断言（"必须删掉"）会因此匹配到自己的说明文字。
        String code = stripComments(page);
        String squashed = squash(code);

        // ---- 1. 弹窗宿主必须在 Scaffold 的 composition 之内 ----------------
        //
        // Miuix 的弹层不是"就地画"的：`DialogLayout` 只是把一个 DialogState 注册进
        // **Scaffold 提供的**那张表（`LocalRootDialogStates.current ?: LocalDialogStates.current`），
        // 真正的绘制由该 Scaffold 的 `MiuixPopupHost` 负责。在 Scaffold 之外调用，
        // 这两个 composition local 都还是各自的默认值（null / 一个空的 mutableStateListOf），
        // 状态被加进一张**没人画的孤儿表** —— 弹窗永远不出现，且不报错。
        requireOverlaysInsideScaffold(page);

        // ---- 2. 骨架与设置二级页同款 ------------------------------------
        //
        // 这里原来钉的形态是 SmallTopAppBar + 固定小标题 + 返回键手动 rotate(-90f)
        // 假装左箭头。断言的不是"必须用某个组件"，而是**这四个性质**：
        // 大标题可折叠、滚动连接、越界回弹、右侧滚动条、官方返回图标。
        require(squashed.contains("TopAppBar("),
            "顶栏要用 Miuix TopAppBar（大标题随滚动折叠），不能用固定小标题的 SmallTopAppBar");
        require(!squashed.contains("SmallTopAppBar("),
            "SmallTopAppBar 必须删掉：它与设置二级页的头部不是一套");
        require(squashed.contains("MiuixScrollBehavior()"),
            "必须有 scrollBehavior：大标题折叠靠它驱动");
        require(squashed.contains("nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)"),
            "列表必须把滚动喂给顶栏，否则大标题不会折叠");
        require(squashed.contains(".overScrollVertical()"),
            "缺 overScrollVertical()：滚动手感与二级页不一致（少了越界回弹）");
        require(squashed.contains("VerticalScrollBar(") && squashed.contains("rememberScrollBarAdapter("),
            "长列表要有 Miuix 滚动条，与二级页一致");
        require(squashed.contains("MiuixIcons.Back"),
            "返回键要用 Miuix 官方的 Back 图标，不要拿上箭头 rotate 出来");
        require(!squashed.contains("rotate(-90f)"),
            "手写 rotate(-90f) 假左箭头必须删掉");
        // 顶栏高度只能从 padding 算：大标题的高度是变的，写死会盖住首行。
        require(squashed.contains("padding.calculateTopPadding()"),
            "内容必须从 Scaffold 给的 padding 里让开顶栏高度");

        // ---- 3. 开关与分组走设置页的分量 ---------------------------------
        //
        // 原来是自己搭 Card + 裸 SwitchPreference：没有组标题、内外边距与设置页不同。
        require(squashed.contains("SettingsGroup(\"沙箱行为\",horizontalPadding=0.dp)"),
            "两个开关必须放进设置页的 SettingsGroup 分组（组标题 + 圆角卡）");
        require(squashed.contains("SettingsToggle("),
            "开关要用设置页的 SettingsToggle，不能裸用 SwitchPreference");
        require(!squashed.contains("SwitchPreference("),
            "页面里不该再直接出现 SwitchPreference：统一走 SettingsToggle");
        // 这个 LazyColumn 上已挂整页 12dp 内边距，分组再各加一次会变成 24dp。
        require(squashed.contains("padding(horizontal=ZhiSpace.m)"),
            "列表左右留白走 ZhiSpace.m 令牌，不要写 12.dp 字面量");

        // ---- 4. 应用卡片的动作分层 --------------------------------------
        //
        // 六个 TextButton 排两行三列时，「进程 / SO 基址」只有 1/3 屏宽而 Miuix
        // 按钮最小宽度 58dp，文字会被挤断；而且六个同权重按钮没有主次。
        String appCard = section(code, "private fun AppCard(", "private fun Action(");
        require(!appCard.contains("ActionRow("),
            "两行三列的 ActionRow 必须删掉");
        require(appCard.contains("BasicComponent("),
            "包名要走 BasicComponent 当标题行（高度/按压态/留白由 Miuix 负责）");
        require(appCard.contains("ZhiAnchoredActionMenu("),
            "调试入口与卸载要收进 ⋮ 溢出菜单（与 MCP 列表页同一套写法）");
        for (String label : new String[]{"进程 / SO 基址", "Frida", "卸载"}) {
            require(appCard.contains("\"" + label + "\""),
                "溢出菜单里缺少：" + label);
        }
        // 主次：日常动作留在明面上，且只有三个。
        for (String label : new String[]{"运行", "停止", "清数据"}) {
            require(appCard.contains("Action(\"" + label + "\""),
                "明面动作里缺少：" + label);
        }
        require(countOccurrences(appCard, "ZhiIconButton(") == 1,
            "标题行只该有一个行尾按钮（⋮）；多一个就说明又有动作被摊到明面上");

        // ---- 5. ⋮ 菜单的标签顺序与分发一一对应 ---------------------------
        //
        // 按下标分发，重排会让"卸载"点到"Frida"。
        int labelsAt = appCard.indexOf("listOf(\"进程 / SO 基址\"");
        require(labelsAt > 0, "⋮ 菜单的标签列表必须以「进程 / SO 基址」开头");
        require(appCard.indexOf("1 -> onFrida(packageName)") > labelsAt,
            "下标 1 必须对应 Frida（与标签列表同序）");
        require(appCard.indexOf("else -> onUninstall(packageName)") > labelsAt,
            "默认分支必须对应卸载（最后一个标签），否则顺序一改就点错");

        // ---- 6. 诊断不能被「后端卡住」本身饿死 ------------------------------
        //
        // 用户报「诊断也读不出来什么」的根因：`SandboxRpcService.dispatch` 入口是
        // `ZhiSandbox.awaitReady(12000)`，引擎没就绪时每个 RPC 烧满 12 秒才抛超时；
        // `reload()` 在**唯一**的 worker 线程上连做 status + list（≈24 秒）并带重试，
        // 而诊断原来也 `worker.execute { … }` —— 永远排不到。
        // 专门用来解释「后端为什么卡住」的工具，被「后端卡住」饿死。
        String boardPath = "app/src/main/java/com/zhizhu/zhicode/sandbox/SandboxBoard.kt";
        String board = squash(stripComments(read(root, boardPath)));
        require(board.contains("privatevaldiagnostics:ExecutorService=Executors.newSingleThreadExecutor()"),
            "诊断必须有自己的第二条线程（diagnostics），不能排在 worker 上");
        require(board.contains("diagnostics.shutdownNow()"),
            "onDestroy 要把诊断线程一并关掉，否则 Activity 泄漏一条线程");
        String diag = section(stripComments(read(root, boardPath)),
            "private fun showDiagnostics()", "private fun fetchStagesFromBackend()");
        require(!squash(diag).contains("worker.execute"),
            "showDiagnostics 里不许出现 worker.execute：那正是诊断被饿死的原因");
        require(squash(diag).contains("diagnostics.execute"),
            "后端那一块必须走 diagnostics 线程");
        // 本地两块（事件记录 + 阶段文件）必须在**向后端发起请求之前**就显示出来：
        // 后端卡住时，「卡在哪一步」的答案本来就躺在 startup-stage.txt 里。
        //
        // 判据取「第一次 updateDetail 出现在 fetchStagesFromBackend 之前」而不是
        // 「存在一次 updateDetail」：只要求存在的话，把前面那次删掉、只留后端回来之后
        // 那次，断言照样过 —— 而那恰恰是 bug 本身（用户盯着「正在读取…」等一个
        // 永远不来的后端）。
        String diagSquashed = squash(diag);
        int snapshotAt = diagSquashed.indexOf("SandboxConsole.snapshot(this)");
        int enqueueAt = diagSquashed.indexOf("diagnostics.execute");
        int firstDetailAt = diagSquashed.indexOf("updateDetail(");
        int backendAt = diagSquashed.indexOf("fetchStagesFromBackend()");
        require(snapshotAt > 0, "本地诊断信息必须在 showDiagnostics 里读出来（SandboxConsole.snapshot）");
        require(enqueueAt > 0 && snapshotAt > enqueueAt,
            "snapshot() 内部要跑 logcat（含 3 秒兜底等待），必须放在 diagnostics 线程里，"
                + "在按钮回调里直接调会卡住主线程");
        require(firstDetailAt > 0 && firstDetailAt < backendAt,
            "本地那一块要在向后端索取之前就 updateDetail 显示出来，"
                + "否则后端一卡住用户只看得到「正在读取…」");
        require(countOccurrences(diagSquashed, "updateDetail(") == 2,
            "updateDetail 应该恰好两次：一次显示本地内容，一次补上后端结果");

        // ---- 7. 快照必须分段限额，不能整体截尾 ----------------------------
        //
        // 「诊断读不出来什么」的第二半：snapshot 原来拼完全部内容再做一次
        // `tail(text, MAX_TEXT)`（保尾砍头），而 logcat 排在最后、又自带一份
        // 和总上限一样大的额度 —— 一份完整 logcat 就能把「进程阶段」与
        // 「事件时间线」整段挤掉，用户看到的第一屏是 libc 的 property 警告刷屏。
        String console = squash(stripComments(
            read(root, "app/src/main/java/com/zhizhu/zhicode/sandbox/SandboxConsole.java")));
        require(!console.contains("returntail(text.toString(),MAX_TEXT)"),
            "快照不许再对整份内容做 tail()：那会把阶段与事件整段挤掉");
        require(!console.contains("privatestaticStringtail("),
            "tail() 已经没有调用方，必须删掉（留着一个专门用来砍掉主证据的助手很危险）");
        require(console.contains("intbudget=Math.min(MAX_LOGCAT_CHARS,Math.max(8_000,remaining))"),
            "logcat 的额度必须是「剩余预算」与 MAX_LOGCAT_CHARS 取小，不能各写一份上限");
        require(console.contains("logcatTail(budget)"),
            "logcatTail 必须接收额度参数");
        require(console.contains("MAX_LOGCAT_CHARS=120_000"),
            "logcat 的额度要显著小于 MAX_TEXT，否则它还能把前两段挤掉");
        // 顺序：阶段与事件先写进去，logcat 最后 —— 第一屏就该是主证据。
        int stagesAt = console.indexOf("=====进程阶段（按pid）=====");
        int eventsAt = console.indexOf("=====事件时间线=====");
        int logcatAt = console.indexOf("=====APP-UIDLOGCAT=====");
        require(stagesAt > 0 && eventsAt > stagesAt && logcatAt > eventsAt,
            "快照顺序必须是 环境 → 进程阶段 → 事件时间线 → logcat");
        // libc 的 property 警告要静音（一次启动几百行，全是噪音）。
        require(console.contains("\"*:V\",\"libc:S\""),
            "logcat 要显式静音 libc：那个 tag 的 property 警告会吃光整段预算");

        // ---- 8. 后端错误用通知条，不是"卡片里放红字" ----------------------
        //
        // 原来是一张 `surfaceContainer` 的 Card 里面放红字 —— 看起来就是一张普通卡片，
        // 只是字恰好是红的；红色只落在文字上，色块面积太小，一眼不像"出事了"。
        // 现在与对话流的错误共用同一个通知条分量（红底红字、无阴影）。
        String errorDetail = section(code, "private fun ErrorDetail(", "private fun EmptyState(");
        require(errorDetail.contains("ZhiNoticeBar("),
            "沙箱后端错误必须用共用的通知条分量（与对话流错误同款）");
        require(errorDetail.contains("ZhiNoticeTone.ERROR"),
            "后端错误必须显式传 ZhiNoticeTone.ERROR，否则会渲染成普通提示色");
        require(errorDetail.contains("SelectionContainer"),
            "错误详情要保留可选中：整段复制去搜是它最常见的用法");
        require(!errorDetail.contains("Card("),
            "错误详情不该再套 Card：卡片外观正是它原来'看着不像出错'的原因");

        // ---- 9. 后端不可用时不许在开关上显示假值 ---------------------------
        //
        // 原来 `showBackendError` 只清了 packages 与在飞标志，`hideRoot` / `floatingLog`
        // 原样留着 —— 后端一挂界面就显示「隐藏 Root 开 / 悬浮窗开」，而真实值是
        // hide_root=true / show_floating_log=false（实测 iqsandbox status）。
        // 两个开关里有一个是假的，且没有任何提示说它不可信。
        require(board.contains("SandboxPrefs.isRootHidden(this)"),
            "后端异常时要回读持久化的 Root 隐藏值，不能停在界面上的旧值");
        require(board.contains("SandboxPrefs.isFloatingLogEnabled(this)"),
            "后端异常时要回读持久化的悬浮窗值");
        // 段落终点用一个**代码**标记（下一个函数声明），不能用注释里的
        // `// ---`：`stripComments` 已经把注释删掉了，拿它当终点永远找不到。
        String errFn = section(stripComments(read(root, boardPath)),
            "private fun showBackendError(", "private fun confirmRootVisibilityChange(");
        require(squash(errFn).contains("setRootSwitch(persistedRoot,false)"),
            "回读到的真值要写回开关，并且压成不可点（后端不通时点它只会再失败一次）");
        require(squash(errFn).contains("setFloatingLog(persistedLog,false)"),
            "悬浮窗同理");
        // 反向：不许再靠 "把在飞标志清掉" 当兜底 —— 那正是留着假值的原因。
        require(!squash(errFn).contains("hideRootInteractive=false,"),
            "不该再直接改 ui 里的在飞标志绕过 setRootSwitch：那不会更新开关的值");

        // ---- 10. authority 未注册要算「还没好」，不能报配置错误 ---------------
        //
        // `ContentResolver.call` 在 provider 还没注册完时会回 Unknown authority。
        // 清单里的 authority 是对的（构建产物核对过），所以这是**冷启动窗口**，
        // 不是配置错。原来它不在可重试名单里，于是第一次进这一页必然显示
        // 「后端异常」，点「刷新」又好了 —— 表现为"时好时坏"。
        String retryFn = section(stripComments(read(root, boardPath)),
            "private fun retryOrShow(", "private fun showBackendError(");
        require(squash(retryFn).contains("message.contains(\"authority\")"),
            "authority 未注册必须算可重试的瞬时失败");

        // ---- 11. 「出错了」在这份工程里只能有一种长相 ------------------------
        //
        // 曾经有三种：沙箱页是深灰 Card 配红字、对话流 ErrorCard 是左边一根 3dp 红竖条、
        // 输入器上方那处是带投影的浮动工具栏。同一件事三种长相，用户的要求是收成一种。
        // 判据落在"调用点"上：三个地方都要走 ZhiNoticeBar（而不是各自画一根竖条 / 一张卡）。
        String chatCards = stripComments(
            read(root, "app/src/main/java/com/zhizhu/zhicode/compose/ui/chat/MessageCards.kt"));
        String errorCard = section(chatCards, "fun ErrorCard(", "fun InfoCard(");
        // 段落终点取"InfoCard 之后的第一个顶层声明"，取不到就吃到文件末尾。
        //
        // 这里原来锚的是 `private fun formatElapsed(`，而那个函数已经删掉了
        // （耗时格式统一走 `ToolText.formatElapsed`），锚点随之失效 ——
        // 而且 `InfoCard` 现在就是文件最后一个函数，写死"下一个函数名"这种锚法
        // 只要动一下文件尾部就会误报。所以改成"下一个顶层声明或文件末尾"。
        int infoAt = chatCards.indexOf("fun InfoCard(");
        require(infoAt >= 0, "找不到 InfoCard：外观统一那条断言失去落点");
        int nextDecl = chatCards.indexOf("\nprivate fun ", infoAt);
        String infoCard = nextDecl < 0 ? chatCards.substring(infoAt) : chatCards.substring(infoAt, nextDecl);
        require(errorCard.contains("ZhiNoticeBar(") && errorCard.contains("ZhiNoticeTone.ERROR"),
            "对话流的 ErrorCard 必须走共用通知条（ERROR 档）");
        require(infoCard.contains("ZhiNoticeBar(") && infoCard.contains("ZhiNoticeTone.WARN"),
            "对话流的 InfoCard 必须走共用通知条（WARN 档）");
        // 反向：那根手写的红/琥珀竖条不许回来 —— 它正是"三种长相"的来源。
        require(!errorCard.contains("width(3.dp)") && !infoCard.contains("width(3.dp)"),
            "手写的 3dp 状态竖条必须删掉：外观已由通知条统一负责");
        // 正文的 Markdown 是**功能**（错误建议里有 `代码` 与列表），换外观不能把它丢掉。
        require(errorCard.contains("ZhiMarkdown(") && infoCard.contains("ZhiMarkdown("),
            "错误/提示正文必须仍然走 Markdown：里面有 `代码` 与列表形态的可操作建议");

        System.out.println("SandboxPageStructureTest OK");
    }

    /**
     * 断言四个弹窗的宿主调用点在 `Scaffold` 的尾随 lambda **里面**。
     *
     * <p>正反两面都查：
     * <ul>
     *   <li>正面 —— lambda 体里必须有 `SandboxDialogHost(`；</li>
     *   <li>反面 —— lambda 体**之后**的源码里不许再有它（旧写法就是写在 Scaffold 外面，
     *       那一份同样能编译、同样能通过"存在性"检查）。</li>
     * </ul>
     */
    private static void requireOverlaysInsideScaffold(String page) {
        String body = trailingLambdaBody(page, "Scaffold(");
        require(body.contains("SandboxDialogHost("),
            "SandboxDialogHost 必须写在 Scaffold 的 content 里：写在它外面时 Miuix 的"
                + "弹层会注册进一张没人画的孤儿表，四个框全部不显示且不报错");
        require(body.contains("LazyColumn("),
            "SandboxDialogHost 必须与列表同处 content 内（且在列表之外，"
                + "否则列表项滚出可视区会把它一起销毁）");
        int bodyStart = page.indexOf(body);
        String tail = page.substring(bodyStart + body.length());
        // 文件里除了「那一处调用」只应剩「定义本身」（`private fun SandboxDialogHost(`，在文件后半段）。
        // 多出来的任何一处都是写在 Scaffold 外面的第二份调用 —— 它同样能编译。
        require(countOccurrences(tail, "SandboxDialogHost(") == 1
                        && tail.contains("fun SandboxDialogHost("),
            "Scaffold 之外还有第二处 SandboxDialogHost 调用 —— 那是永远画不出来的那一份");
        require(countOccurrences(page, "SandboxDialogHost(") == 2,
            "SandboxDialogHost 在整页里应当只出现两次：一处定义 + 一处调用");
    }

    /** 取 [from] 到 [to] 之间的源码。用它把断言限定在一个函数里。 */
    private static String section(String source, String from, String to) {
        int at = source.indexOf(from);
        require(at >= 0, "找不到段落起点：" + from);
        int end = source.indexOf(to, at + from.length());
        require(end > at, "找不到段落终点：" + to);
        return source.substring(at, end);
    }

    private static int countOccurrences(String source, String needle) {
        int count = 0;
        int i = source.indexOf(needle);
        while (i >= 0) {
            count++;
            i = source.indexOf(needle, i + needle.length());
        }
        return count;
    }
}
