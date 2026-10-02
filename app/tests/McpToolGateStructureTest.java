import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

/**
 * MCP 逐工具开关的守卫。
 *
 * <p>这个功能有四件事「改坏了不会编译失败」，而每一件都会让开关变成摆设：
 *
 * <ol>
 *   <li><b>{@code PermissionGate} 里那个判断必须在"这类工具永远放行"之前。</b>
 *       MCP 属于 {@code NETWORK}，而 NETWORK 是永远放行的。把逐工具审批的判断
 *       挪到 {@code isAlwaysAllowed} 之后 —— 代码照常编译、开关照常能点能存，
 *       只是从不拦截任何东西。</li>
 *   <li><b>{@code McpRuntime.call} 必须自己再挡一次被禁用的工具。</b>
 *       只靠"列表里过滤掉"不构成保护：模型可能在关闭之前就见过那个名字，
 *       或者从对话历史里记住了它。</li>
 *   <li><b>{@code McpConfigStore.Server} 的 {@code copy} / {@code toJson} /
 *       {@code fromJson} 三处都要带 {@code tools}。</b>漏掉任何一处都表现为
 *       "设了开关，重启就没了"或者"改了副本，原件没变"—— 都不会编译失败。</li>
 *   <li><b>缺省必须是"启用"。</b>已有配置里没有 {@code tools} 字段；
 *       把"没记录"当成禁用，升级一次就会把用户所有 MCP 工具悄悄关掉。</li>
 * </ol>
 */
public final class McpToolGateStructureTest {

    private static final String SRC = "app/src/main/java/com/termux/app/zhicode/";

    private static final String PERMISSION_GATE = SRC + "core/PermissionGate.java";
    private static final String RUNTIME = SRC + "mcp/McpRuntime.java";
    private static final String STORE = SRC + "storage/McpConfigStore.java";
    private static final String TOOL = SRC + "tools/McpTool.java";
    private static final String ZHI_TOOL = SRC + "tools/ZhiTool.java";
    private static final String REGISTRY = SRC + "tools/ToolRegistry.java";
    private static final String ENGINE = SRC + "core/ZhiCodeEngine.java";

    private static final String SUBPAGE =
        "app/src/main/java/com/zhizhu/zhicode/compose/ui/settings/SettingsSubPage.kt";

    private static final String OVERLAY =
        "app/src/main/java/com/zhizhu/zhicode/compose/ui/dialogs/McpConfigOverlay.kt";

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static String read(Path root, String path) throws Exception {
        return new String(Files.readAllBytes(root.resolve(path)), StandardCharsets.UTF_8);
    }

    /** 去掉注释：否则「代码删了、注释里还写着」会让断言静默通过。 */
    private static String stripComments(String text) {
        return text.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", " ");
    }

    private static boolean has(String source, String needle) {
        return source.replaceAll("\\s+", "").contains(needle.replaceAll("\\s+", ""));
    }

    private static int countOf(String source, String needle) {
        return source.split(Pattern.quote(needle), -1).length - 1;
    }

    /**
     * 取一个方法体的文本：从 [methodSignature] 起，到下一个同级方法声明之前。
     *
     * <p>⚠️ 不能用固定长度切片：本仓库的 MCP 运行时里方法挨得很近，
     * 切长了会把下一个方法（它里面含 {@code session(}）也算进来，
     * 于是断言在一个完全无关的地方失败 —— 实测踩过。
     * 判据是下一个 `\n    public ` / `\n    private ` 的位置。
     */
    private static String methodBody(String source, String methodSignature) {
        int start = source.indexOf(methodSignature);
        if (start < 0) throw new AssertionError("找不到方法：" + methodSignature);
        java.util.regex.Matcher next = Pattern.compile("\\n    (?:public|private|static) ")
            .matcher(source);
        next.region(start + methodSignature.length(), source.length());
        int end = next.find() ? next.start() : source.length();
        return source.substring(start, end);
    }

    public static void main(String[] args) throws Exception {
        Path root = Paths.get(args.length == 0 ? "." : args[0]).toAbsolutePath().normalize();
        String gate = stripComments(read(root, PERMISSION_GATE));
        String runtime = stripComments(read(root, RUNTIME));
        String store = stripComments(read(root, STORE));
        String tool = stripComments(read(root, TOOL));
        String zhiTool = stripComments(read(root, ZHI_TOOL));
        String registry = stripComments(read(root, REGISTRY));
        String engine = stripComments(read(root, ENGINE));
        String overlay = stripComments(read(root, OVERLAY));
        String subPage = stripComments(read(root, SUBPAGE));

        // ---- 1. 逐工具审批必须在「永远放行」之前 -----------------------------
        //
        // 这条是整块功能里最容易失效的一处：把顺序调换过来，一切照常工作，
        // 只是那个开关永远不拦任何东西。所以判据落在**位置**上，不是"提到过"。
        int approvalCheck = gate.indexOf("tool.requiresApproval(call.input)");
        int alwaysAllowed = gate.indexOf("if (isAlwaysAllowed(kind)) return true;");
        require(approvalCheck > 0,
            "PermissionGate 必须查 tool.requiresApproval(call.input)："
                + "逐工具审批是这个功能唯一真正拦截调用的地方");
        require(alwaysAllowed > 0 && approvalCheck < alwaysAllowed,
            "逐工具审批的判断必须在 isAlwaysAllowed 之前：MCP 属于 NETWORK，"
                + "而 NETWORK 在 isAlwaysAllowed 里是永远放行的 —— 放到后面，"
                + "那个开关就是个摆设（点得动、存得下，只是从不拦截，也不报错）");
        // bypass 仍然要能压过它：那一档是用户明确说过"别问我"。
        require(has(gate, "tool.requiresApproval(call.input) && !PermissionModePolicy.BYPASS.equals(mode)"),
            "bypass 权限模式必须仍然能压过逐工具审批：那一档的语义是「别问我」");
        // PLAN / DONT_ASK 都不能让这条路径被"直接执行"绕过。
        //
        // ⚠️ 两者**实现方式不同**，这是刻意的：
        //   PLAN     → 直接拒绝（只读语义：不执行任何需要确认的东西）；
        //   DONT_ASK → 走 ask()（「不询问」= 不问就做，但需要批准的高危调用仍要提示，
        //              与 UI 文案「不再弹出确认，高风险操作仍会提示」一致）。
        //
        // 原先两者都 return false，于是「不询问」被实现成「不问也不做」——
        // 用户看到的是切到不询问之后**几乎所有命令都执行失败**。
        // 这里守的**不变量**是「gated 调用在 PLAN/DONT_ASK 下不会被静默放行」，
        // 而不是某一行字面量。
        require(has(gate, "if (PermissionModePolicy.PLAN.equals(mode)) return false;"),
            "PLAN 必须仍然拒绝需要确认的调用：它的语义是「不执行需要确认的东西」，"
                + "而不是「那就直接执行」");
        int gatedAsks = gate.indexOf("return ask(call, kind, mode, listener);", approvalCheck);
        require(gatedAsks > 0,
            "DONT_ASK 下 gated 调用必须走 ask()：它既不能被静默放行，"
                + "也不能被拒绝 —— 后者就是把「不询问」变成「不问也不做」的原 bug");

        // ---- 2. ZhiTool 的默认实现与 McpTool 的覆写 ---------------------------
        require(has(zhiTool, "default boolean requiresApproval(JSONObject input)") && has(zhiTool, "return false;"),
            "ZhiTool 必须提供 requiresApproval 的默认实现（false）："
                + "绝大多数工具不需要它，让它们都实现一遍只是噪音");
        require(has(tool, "public boolean requiresApproval(JSONObject input)") && has(tool, "if (list) return false;"),
            "McpTool 必须覆写 requiresApproval，且 mcp_list 不参与（它只是列清单）");
        require(has(tool, "runtime.requiresApproval(input.optString(\"server\", \"\"), input.optString(\"tool\", \"\"))"),
            "McpTool.requiresApproval 必须把服务器名与工具名一起交给运行时判定");

        // ---- 3. 判定路径绝不能连服务器 ---------------------------------------
        //
        // 权限判定发生在每次工具调用上。那里去起进程或发 HTTP 的话：
        // 一次判定要等 30 秒（连接超时），而且会在用户批准之前就把服务器拉起来了。
        int approvalMethod = runtime.indexOf("public boolean requiresApproval(");
        require(approvalMethod > 0, "McpRuntime 必须提供 requiresApproval（只读配置）");
        String approvalBody = methodBody(runtime, "public boolean requiresApproval(");
        require(!approvalBody.contains("session("),
            "权限判定里绝不能连服务器（session() 会起子进程/发 HTTP）："
                + "判定发生在每次调用上，等 30 秒超时，而且抢在用户批准之前");

        // ---- 4. call 必须自己再挡一次被禁用的工具 ----------------------------
        require(runtime.indexOf("public ToolExecutionResult call(") > 0, "找不到 McpRuntime.call");
        String callBody = methodBody(runtime, "public ToolExecutionResult call(");
        require(callBody.contains("if (!server.isToolEnabled(toolName))"),
            "McpRuntime.call 必须拒绝被用户禁用的工具：只靠 listServers 里过滤掉不构成保护，"
                + "模型可能在关闭之前就见过那个名字，或从对话历史里记住了它");
        require(callBody.contains("MCP tool disabled by user"),
            "拒绝时要说清原因（MCP tool disabled by user），"
                + "否则模型只会看到一个没头没尾的失败");

        // ---- 5. listServers 要标注 + 过滤 ------------------------------------
        require(runtime.indexOf("public JSONArray listServers()") > 0, "找不到 McpRuntime.listServers");
        String listBody = methodBody(runtime, "public JSONArray listServers()");
        require(listBody.contains("tool.put(\"enabled\", toolEnabled)") && listBody.contains("tool.put(\"approval\","),
            "listServers 必须把每个工具的 enabled / approval 标出来："
                + "界面要能显示「这个工具被关掉了」，只发启用的会让用户以为工具消失了");
        require(listBody.contains("if (toolEnabled) tools.put(tool)"),
            "listServers 必须把被禁用的工具从结果里去掉 —— "
                + "这是「不下发给模型」的落点（mcp_list 是模型发现工具的唯一入口）");

        // ---- 6. 存储三处都要带 tools ----------------------------------------
        //
        // copy / toJson / fromJson 漏任何一处都表现为"设了开关重启就没了"
        // 或者"改了副本原件没变"，两者都不会编译失败。
        require(has(store, "clone.tools = deepCopy(tools);"),
            "Server.copy() 必须一起拷 tools：漏掉就是「改了副本、原件没变」");
        require(has(store, ".put(KEY_TOOLS, tools == null ? new JSONObject() : tools);"),
            "Server.toJson() 必须写出 tools：漏掉就是「设了开关、重启就没了」");
        require(has(store, "JSONObject tools = source.optJSONObject(KEY_TOOLS);") && has(store, "server.tools = deepCopy(tools);"),
            "Server.fromJson() 必须读回 tools：漏掉就是「设了开关、重启就没了」");

        // ---- 7. 缺省必须是启用 ----------------------------------------------
        //
        // 已有配置里没有 tools 字段。把「没记录」当成禁用，升级一次就会把
        // 用户所有的 MCP 工具悄悄关掉，而界面上只是几个开关关着、看不出发生过什么。
        require(has(store, "return entry == null || entry.optBoolean(TOOL_ENABLED, true);"),
            "isToolEnabled 的缺省必须是 true：没有记录 = 启用。"
                + "写成缺省 false，升级一次就会把用户所有 MCP 工具悄悄关掉");
        require(has(store, "return entry != null && entry.optBoolean(TOOL_APPROVAL, false);"),
            "isToolApprovalRequired 的缺省必须是 false（不额外打扰用户）");

        // ---- 8. 界面不得自动测试连接 -----------------------------------------
        //
        // 测试会真的起子进程 / 发 HTTP，单台超时 30 秒。打开页面就自动跑，
        // 等于把"看配置"变成"把每台服务器都启动一遍"，而且在用户还没决定
        // 要改什么之前就产生了副作用。
        require(has(overlay, "onTest: (String) -> Unit") && has(overlay, "测试连接"),
            "列表页必须提供显式的「测试连接」入口");
        require(!has(overlay, "LaunchedEffect(Unit) { onTest(") && !has(overlay, "LaunchedEffect(config) { onTest("),
            "不得在打开页面时自动测试连接：那会真的启动每台服务器，"
                + "单台最长等 30 秒，而且用户还没决定要改什么");
        require(has(engine, "public JSONArray mcpServers()"),
            "ZhiCodeEngine 必须转发 mcpRuntime().listServers()");
        require(has(registry, "public McpRuntime mcpRuntime()"),
            "ToolRegistry 必须暴露它建的那一个 McpRuntime：另建一个会让测试与真实调用"
                + "落在不同的运行时上");

        // ---- 9. 每工具开关必须真的落盘 + 立刻反映到状态 -----------------------
        require(has(overlay, "onToolOptionsChange") && has(overlay, "调用前需要确认"),
            "表单的「工具」分组必须给出「启用」与「调用前需要确认」两个开关");
        require(has(overlay, "enabled = tool.enabled"),
            "停用的工具，审批开关要跟着灰掉：永远不会走到确认那一步，"
                + "留着可点会让人以为它还有作用");

        // ---- 10. 「工具」必须是独立的一页，靠 TAB 栏切换 ----------------------
        //
        // 工具清单长度完全取决于服务器（3 个到 40 个都有）。和「基本设置」排在
        // 同一页里，那些每次都要改的字段（名称/URL/作用范围）会被推到几十屏之前。
        require(has(overlay, "private val MCP_FORM_TABS = listOf(\"基本设置\", \"工具\")"),
            "MCP 表单必须是「基本设置 / 工具」两个 TAB");
        // 形态必须与**工作区顶栏那一条**（对话 / 终端 / 文件）一致：
        // 用的是 ZhiSegmentedTabs（Miuix `TabRowWithContour`），不是裸 TabRow。
        // 两处控件长得一样，用户不必学第二套；整宽等分也是照抄它的做法。
        require(has(overlay, "ZhiSegmentedTabs(") && has(overlay, "selectedIndex = tab")
                        && has(overlay, "onSelect = { tab = it }") && has(overlay, "matchWidth = true"),
            "两个 TAB 必须用 ZhiSegmentedTabs 切换，且整宽等分 —— "
                + "与工作区顶栏那一条是同一个组件（用户不必学第二套切换控件）");
        require(has(overlay, "if (tab == 0)") && has(overlay, "McpConnectionForm(")
                        && has(overlay, "McpToolsTab("),
            "两个 TAB 的内容必须各自独立成组件，按 tab 下标渲染");

        // TAB 栏必须走**固定** header 槽位，不能混进滚动内容里：
        // 切到「工具」往下滚几屏之后还得能切回「基本设置」。
        require(has(overlay, "header = {") && has(subPage, "header: (@Composable () -> Unit)? = null"),
            "TAB 栏必须通过 SettingsSubPage 的 header 槽位固定在顶栏之下，"
                + "不能放进会滚走的内容里（切不回去的 TAB 栏等于没有）");
        // ⚠️ body 在 (0,0)、顶栏画在它上面，所以固定 header 必须自己补顶栏高度，
        // 否则会被顶栏整个盖住 —— 表现是"TAB 栏不见了"，而不会报任何错。
        require(has(subPage, ".padding(top = padding.calculateTopPadding())"),
            "固定 header 必须自己补上顶栏高度：Miuix 把 body 放在 (0,0)、顶栏画在它上面，"
                + "不补就是整块被盖住（不报错，只是看不见）");
        // 补过之后滚动区就**不能**再算一次顶栏高度，否则内容前面凭空多一段空白。
        require(has(subPage, "if (header == null) padding.calculateTopPadding() else 0.dp"),
            "有 header 时滚动区不得重复计算顶栏高度：会多出一段与顶栏等高的空白");

        // 工具页的开关要按**服务器名**落盘，名字无效时写进去会"保存成功但没生效"。
        require(has(overlay, "if (!draft.isEditing || !draft.saveable)"),
            "名称/连接信息还没填好时，「工具」页要挡住开关："
                + "setToolOptions 是按服务器名找的，名字对不上会静默失败");
    }
}
