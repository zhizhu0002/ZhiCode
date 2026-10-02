#!/usr/bin/env bash
#
# 源码级结构测试套件（canonical source suite）。
#
# 逐个执行 app/tests 下的结构测试，参数统一为工程根。
# 之所以用脚本而不是 Gradle 任务：这些测试只读源码文本做断言，
# 不需要编译工程、不需要设备，用 JDK 单文件源码模式直接跑最快。
#
# ⚠ 这套测试是**文本级**的：它能防「重写时漏掉一个分支」，但**证明不了运行时行为**。
#   api/ 那一层是没有 android.* 依赖的纯 Java，另有真正的 JVM 单测：
#
#       ./gradlew :app:testDebugUnitTest      # app/src/test，真跑逻辑
#
#   两者互补：改 api/ 这类纯逻辑时两个都要跑。判断标准很简单 ——
#   如果一处改动「写错了也不会编译失败」，那它需要的是 JVM 单测，而不是文本断言。
#
# 用法: ./test-source-no-build.sh [工程根] [--only 测试名]
#
#   --only 只跑一条测试。给 teeth.sh 用（它要对同一条测试反复改-跑-还原多次，
#          跑全量的话每验证一条守卫就要等半分钟），也方便手动定位单条失败。
set -uo pipefail

ONLY=""
PROJECT_ROOT=""
while [ $# -gt 0 ]; do
    case "$1" in
        --only)   ONLY="${2:-}"; shift 2 ;;
        --only=*) ONLY="${1#--only=}"; shift ;;
        *)
            if [ -n "$PROJECT_ROOT" ]; then
                echo "多余的参数：$1" >&2
                exit 2
            fi
            PROJECT_ROOT="$1"; shift ;;
    esac
done
[ -n "$PROJECT_ROOT" ] || PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"
TESTS_DIR="$PROJECT_ROOT/app/tests"

if [ ! -d "$TESTS_DIR" ]; then
    echo "找不到测试目录: $TESTS_DIR" >&2
    exit 2
fi

PASS=0
FAIL=0
SKIP=0
FAILED_NAMES=""

# ---------- 把测试**编译一次**，之后每个用 `java -cp` 直接跑 ----------
#
# 之前是 `java app/tests/X.java`（JDK 单文件源码模式），每个测试都要**现场编译**一遍：
# 实测 1.1s/个，31 个就是半分钟。而真正的断言只跑几十毫秒 —— 慢的全是启动。
#
# 改成先一次性 javac 成 jar，之后每个 `java -cp jar X` 只要 0.15s（实测），
# 整套从约 32s 降到 5s 上下。改过测试源码后多花一次编译（8s 左右，几乎不随文件数增长）。
#
# 失效判断用「文件名 + 大小 + 修改时间」指纹，不用内容哈希：内容哈希要把所有测试源码
# 读一遍，而这套测试**本来就是逐个读源码文本的**，再哈希一遍是重复劳动。
BUILD_DIR="$PROJECT_ROOT/.test-build"
TESTS_JAR="$BUILD_DIR/tests.jar"
STAMP_FILE="$BUILD_DIR/sources.stamp"
COMPILED="no"

prepare_tests() {
    local stamp
    stamp=$(find "$TESTS_DIR" -maxdepth 1 -name '*.java' -printf '%f %s %T@\n' 2>/dev/null | LC_ALL=C sort)
    if [ -f "$TESTS_JAR" ] && [ -f "$STAMP_FILE" ] && [ "$stamp" = "$(cat "$STAMP_FILE")" ]; then
        return 0
    fi
    local sources
    sources=$(find "$TESTS_DIR" -maxdepth 1 -name '*.java' | LC_ALL=C sort)
    if [ -z "$sources" ]; then
        echo "app/tests 下没有 .java 测试" >&2
        exit 2
    fi
    rm -rf "$BUILD_DIR/classes" "$TESTS_JAR"
    mkdir -p "$BUILD_DIR/classes"
    # shellcheck disable=SC2086
    if ! javac -nowarn -d "$BUILD_DIR/classes" $sources 2>"$BUILD_DIR/javac.log"; then
        # ⚠️ 编译不过就**一个测试都不跑**，绝不能拿上一次留下的旧 class 继续跑：
        # 那样改坏了一个测试文件反而会得到「全绿」，正是这套东西最该避免的失败模式。
        echo "测试源码编译失败 —— 不跑任何测试：" >&2
        sed 's/^/  /' "$BUILD_DIR/javac.log" | head -30 >&2
        exit 2
    fi
    jar cf "$TESTS_JAR" -C "$BUILD_DIR/classes" .
    printf '%s' "$stamp" > "$STAMP_FILE"
    COMPILED="yes"
}

run() {
    local name="$1"
    local root="$2"
    if [ -n "$ONLY" ] && [ "$name" != "$ONLY" ]; then
        SKIP=$((SKIP + 1))
        return 0
    fi
    local output
    if output=$(java -cp "$TESTS_JAR" "$name" "$root" 2>&1); then
        echo "PASS  $name"
        PASS=$((PASS + 1))
    else
        echo "FAIL  $name"
        echo "$output" | sed 's/^/      /' | head -8
        FAIL=$((FAIL + 1))
        FAILED_NAMES="$FAILED_NAMES $name"
    fi
}

# 用 node 跑的测试：这些断言的对象是 JS 载荷的行为，不是源码文本。
#
# node 缺失时**明确失败**，不静默跳过 —— 「工具不在就跳过」会让套件在别人的机器上
# 一路绿，而那正是它什么都没测的意思（本仓库已经记过一次同类错误：断言看着在守，
# 其实守的是一句话）。用 `timeout` 兜住：载荷里若有同步死循环，它会把事件循环卡死，
# 连它自己的超时都不会触发，只能由外层掐掉。
run_node() {
    local name="$1"
    local root="$2"
    if [ -n "$ONLY" ] && [ "$name" != "$ONLY" ]; then
        SKIP=$((SKIP + 1))
        return 0
    fi
    local file="$TESTS_DIR/js/$name.mjs"
    if ! command -v node >/dev/null 2>&1; then
        echo "FAIL  $name（缺 node：这些行为断言只能用 JS 引擎跑）"
        FAIL=$((FAIL + 1))
        FAILED_NAMES="$FAILED_NAMES $name(no-node)"
        return
    fi
    if [ ! -f "$file" ]; then
        echo "FAIL  $name（缺少 $file）"
        FAIL=$((FAIL + 1))
        FAILED_NAMES="$FAILED_NAMES $name(missing)"
        return
    fi
    local output
    if output=$(timeout 120 node "$file" "$root" 2>&1); then
        echo "PASS  $name"
        PASS=$((PASS + 1))
    else
        echo "FAIL  $name"
        echo "$output" | sed 's/^/      /' | head -8
        FAIL=$((FAIL + 1))
        FAILED_NAMES="$FAILED_NAMES $name"
    fi
}

# ---------- 宿主层架构（本次重写建立的不变式） ----------
prepare_tests
# SandboxHostArchitectureTest.java
run SandboxHostArchitectureTest "$PROJECT_ROOT"
# ---------- 进程角色与隔离 ----------
# SandboxProcessIsolationStructureTest.java
run SandboxProcessIsolationStructureTest "$PROJECT_ROOT"
# SandboxMainProcessRoleStructureTest.java
run SandboxMainProcessRoleStructureTest "$PROJECT_ROOT"
# SandboxHooklessControllerStructureTest.java
run SandboxHooklessControllerStructureTest "$PROJECT_ROOT"
# ---------- 启动竞态与崩溃回归 ----------
# SandboxControllerStartupRaceTest.java
run SandboxControllerStartupRaceTest "$PROJECT_ROOT"
# SandboxStartupCrashRegressionTest.java
run SandboxStartupCrashRegressionTest "$PROJECT_ROOT"
# ---------- 接线与网络 ----------
# SandboxIntegrationStructureTest.java
run SandboxIntegrationStructureTest "$PROJECT_ROOT"
# SandboxNetworkPassthroughStructureTest.java
run SandboxNetworkPassthroughStructureTest "$PROJECT_ROOT"
# SandboxWebViewStructureTest.java
run SandboxWebViewStructureTest "$PROJECT_ROOT"
# SandboxFileProviderResourceRegressionTest.java
run SandboxFileProviderResourceRegressionTest "$PROJECT_ROOT"
# ---------- 设置与叠加层 ----------
# SandboxRootVisibilitySettingTest.java
run SandboxRootVisibilitySettingTest "$PROJECT_ROOT"
# SandboxFloatingLogStructureTest.java
run SandboxFloatingLogStructureTest "$PROJECT_ROOT"
# ---------- 截图桥 ----------
# SandboxScreenshotBridgeStructureTest.java
run SandboxScreenshotBridgeStructureTest "$PROJECT_ROOT"
# ---------- 调试桥 ----------
# SandboxDebugBridgeStructureTest.java
run SandboxDebugBridgeStructureTest "$PROJECT_ROOT"
# ---------- Frida ----------
# FridaSandboxStructureTest.java
run FridaSandboxStructureTest "$PROJECT_ROOT"
# FridaDeadlockRegressionTest.java
run FridaDeadlockRegressionTest "$PROJECT_ROOT"
# FridaGadgetConfigRegressionTest.java
run FridaGadgetConfigRegressionTest "$PROJECT_ROOT"
# FridaScriptBootstrapRegressionTest.java
run FridaScriptBootstrapRegressionTest "$PROJECT_ROOT"

# ---------- 内嵌 Frida 载荷的**行为**（这是上面几个 Frida 测试守不到的那一半）----------
# 上面那些断言读的是源码文本，例如 contains("boundedInteger(p.chunk_size,4194304,65536,8388608)")
# —— 改一个空格就红，而真正的行为漂移（某块读不了就整条命令失败、上限停止条件反了、
# 重叠把 scanned 算多了、命中被重复报出没去重）它一个也拦不住。
# 这一条把载荷从 Java 源码里抽出来，用桩替换 Frida 宿主对象，按它自己的信箱协议
# 发命令读响应，对返回值做断言。
# 局限写在那个文件里：真实的 Gadget 载入、Interceptor/Stalker、真实 Memory.scan
# 一律证明不了 —— 那些要在 IQ 沙箱里跑起来才知道。
run_node frida-agent-harness "$PROJECT_ROOT"

# 再用**真正的 Java** 抽一次同一份载荷，逐字节比对 —— 保证行为测试测的载荷
# 与设备上执行的是同一份（harness 自己实现的 text block 还原规则若与 Java 不一致，
# 两边会各自「通过」而没人发现）。
run_node frida-payload-embedding-check "$PROJECT_ROOT"

# ---------- 数据层的格式迁移 ----------
# 内部续跑标记会写进持久化历史，改名后若只认新标记，
# 旧会话里的续跑指令会变成可编辑的「人类发言」。这种回归只在旧数据上出现，
# 编译和新会话都不报错，所以要专门守。
# SessionMarkerMigrationTest.java
run SessionMarkerMigrationTest "$PROJECT_ROOT"

# ---------- 许可与归属 ----------
# 这些声明是纯附加的：删掉它们编译照过、功能照跑、别的测试也照过，
# 所以要有一条测试专门盯着，否则只能等到别人指出侵权时才发现。
# LicenseNoticeStructureTest.java
run LicenseNoticeStructureTest "$PROJECT_ROOT"

# ---------- 不内置任何厂商端点 ----------
# 应用不再预置任何厂商地址；这条测试对整个源码树做字面量扫描。
# 它守的是一类“加一行默认值就够方便”的改动，而那行的后果是把用户流量导向某个具体服务。
# NoBundledThirdPartyEndpointTest.java
run NoBundledThirdPartyEndpointTest "$PROJECT_ROOT"

# ---------- api/ 的线协议契约 ----------
# 这一层要被重写，而现有测试里只有两行碰过它。重写流式解析器最容易犯的错不是编译不过，
# 而是静默漏掉一个分支（少认一个事件类型、少读一个字段、终止标记判断反了）——
# 那在上层只表现为「回复不完整」或「工具调用丢参数」，很难定位。
# ApiWireContractTest.java
run ApiWireContractTest "$PROJECT_ROOT"

# ---------- 终端面板的行为契约 ----------
# 终端外壳改用 Compose 重写（批 F 2/6）。它是全工程唯一一块「重写后无法用单测
# 证明行为没变」的地方：手势 + IME + PTY 的组合，编译通过只能说明类型对得上。
# 这里钉住三件改坏了不报错、只会在真机上表现为"终端不好用"的东西：
# 12 个扩展键的转义字节、11 项快捷动作的标签与顺序（调用方按下标分发）、
# 四个修饰键的读后即清语义。
# TerminalPaneContractTest.java
run TerminalPaneContractTest "$PROJECT_ROOT"
# ---------- 字阶单一来源（防"同一层次的尺寸被逐处手写"再犯） ----------
# TypographyScaleTest.java
run TypographyScaleTest "$PROJECT_ROOT"
# ---------- 输入框唯一入口（防"隐形的文字色/光标"缺陷回来） ----------
# TextFieldConventionTest.java
run TextFieldConventionTest "$PROJECT_ROOT"
# ---------- 长按动作菜单的接线（防"长按后什么都不弹/两边同时弹"） ----------
# AnchoredMenuStructureTest.java
run AnchoredMenuStructureTest "$PROJECT_ROOT"
# ---------- 设置页（数值项是可输入的文本框 · 自动保存 · 唯一写入路径） ----------
# SettingsPageStructureTest.java
run SettingsPageStructureTest "$PROJECT_ROOT"
# ---------- 二级页转场（多态页必须走页面栈）与技能页安全边界 ----------
# 用户报过「设置很多地方的动画非常不完整（比如三级窗口）」：根因是多态二级页被
# when 硬切，而 AppScaffold 的页面栈只反映"这一页开着没有"。
# SkillsPageStructureTest.java
run SkillsPageStructureTest "$PROJECT_ROOT"
# ---------- MCP 逐工具开关（放行顺序 · 禁用拦截 · 三处序列化 · 缺省启用） ----------
# 这个功能有四处「改坏了不会编译失败」：把逐工具审批的判断挪到 isAlwaysAllowed
# 之后（MCP 属于 NETWORK，而它是永远放行的）—— 开关点得动、存得下，只是从不拦截。
# McpToolGateStructureTest.java
run McpToolGateStructureTest "$PROJECT_ROOT"
# ---------- 侧栏导航（点任何一行都要收起侧栏，否则目标被浮层盖住） ----------
# SidebarNavigationTest.java
run SidebarNavigationTest "$PROJECT_ROOT"
# ---------- 沙箱页（弹窗宿主挂载点 · 骨架同二级页 · 卡片动作分层） ----------
# 这一页是独立 Activity 里的整页，自己拼骨架，于是拼出过只有真机上点得出来的毛病：
# 弹窗宿主写在 Scaffold 之外 → 四个框全部不显示且不报错（点了没反应，状态卡在非 null）。
# SandboxPageStructureTest.java
run SandboxPageStructureTest "$PROJECT_ROOT"
# ---------- 写死的几何（长按触发 · 气泡宽度 · 弹窗宽度） ----------
# LayoutConsistencyTest.java
run LayoutConsistencyTest "$PROJECT_ROOT"
# ---------- 流式渲染热路径（重解析 · 行内缓存 · 尺寸动画） ----------
# 用户的要求是「在安卓 8 及以上也能流畅使用」。这三条守的都是**不会编译失败、
# 也不会让界面坏掉、只会让老设备变慢**的东西：正文按每个 delta 重解析整篇、
# 行内解析裸调不缓存、流式期间挂着尺寸动画。切点函数的行为由
# MarkdownStreamSplitTest（JVM 单测）守，两者互补。
# MarkdownStreamingTest.java
run MarkdownStreamingTest "$PROJECT_ROOT"
# ---------- 超长工具输出（行数上限 · 不再对输出做 O(n) 字符串手术） ----------
# 工具卡展开的输出最长 40 000 字符，而 DiffLines 是**每一行一个 Text**，
# 那些节点又全在同一个 LazyColumn item 里 —— 上千行就是"那个 item 比视口还高"，
# 懒加载的复用彻底失效。另一条是 compactToolSummary / isFileDiff 每次重组都
# 对整份输出 trim + split（结果只有三个数）。两条都不会报错，只会让老设备发烫。
# 截断规则的行为由 LimitLinesTest（JVM 单测）守，两者互补。
# ToolOutputBoundTest.java
run ToolOutputBoundTest "$PROJECT_ROOT"
run R8ConfigTest "$PROJECT_ROOT"
# ---------- 弹窗内滚动嵌套（防「点开某个弹窗直接闪退」重犯） ----------
# 用户报告过「添加 MCP 服务器崩溃」，真因是 DialogShell 的 body 已带 verticalScroll，
# 而表单里又套了一层 —— 编译通过、只有测量时才抛 Infinity maximum height constraints。
# DialogScrollNestingTest.java
run DialogScrollNestingTest "$PROJECT_ROOT"
# ---------- UI 调试页的入口门控与"真的铺开组件" ----------
# 这一页只在 debug 构建可见；它一旦被搬进发布包、或退化成静态贴图、
# 或自己写死字号与颜色，都不会编译失败 —— 只会在没人注意的时候失去意义。
# 侧栏那两项（技能 / 自定义角色卡）的收敛也一并守在这里。
# UiDebugPageStructureTest.java
run UiDebugPageStructureTest "$PROJECT_ROOT"
# ---------- 全局调试浮层 + Markdown 全语法样例 ----------
# 浮层的 BuildConfig.DEBUG 门控、必须读实时状态、Markdown 预览必须用生产渲染器、
# 以及"样例必须覆盖解析器支持的每一类语法" —— 这些全是删掉不会编译失败的东西。
# DebugHudStructureTest.java
run DebugHudStructureTest "$PROJECT_ROOT"

echo "-----"
if [ -n "$ONLY" ]; then
    echo "通过 $PASS / 失败 $FAIL（--only $ONLY，跳过 $SKIP 条）"
else
    echo "通过 $PASS / 失败 $FAIL"
fi
[ "$COMPILED" = "yes" ] && echo "（本次先编译了测试：慢的就是这一次，之后复用 .test-build/tests.jar）"
if [ "$FAIL" -ne 0 ]; then
    echo "失败项:$FAILED_NAMES"
    exit 1
fi
