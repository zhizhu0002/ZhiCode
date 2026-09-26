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
# 用法: ./test-source-no-build.sh [工程根]
set -uo pipefail

PROJECT_ROOT="${1:-$(cd "$(dirname "$0")" && pwd)}"
TESTS_DIR="$PROJECT_ROOT/app/tests"

if [ ! -d "$TESTS_DIR" ]; then
    echo "找不到测试目录: $TESTS_DIR" >&2
    exit 2
fi

PASS=0
FAIL=0
FAILED_NAMES=""

run() {
    local name="$1"
    local root="$2"
    local output
    if output=$(java "$TESTS_DIR/$name.java" "$root" 2>&1); then
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

echo "-----"
echo "通过 $PASS / 失败 $FAIL"
if [ "$FAIL" -ne 0 ]; then
    echo "失败项:$FAILED_NAMES"
    exit 1
fi
