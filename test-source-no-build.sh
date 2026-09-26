#!/usr/bin/env bash
#
# 蜘蛛沙箱宿主层的结构测试套件（canonical source suite）。
#
# 与官方 IQ Code 的同名脚本等价：逐个执行 app/tests 下的结构测试，参数统一为工程根。
# 之所以用脚本而不是 Gradle 任务：这些测试只读源码文本做断言，
# 不需要编译工程、不需要设备，用 JDK 单文件源码模式直接跑最快。
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

# ---------- 许可与归属 ----------
# 这些声明是纯附加的：删掉它们编译照过、功能照跑、别的测试也照过，
# 所以要有一条测试专门盯着，否则只能等到别人指出侵权时才发现。
# LicenseNoticeStructureTest.java
run LicenseNoticeStructureTest "$PROJECT_ROOT"

echo "-----"
echo "通过 $PASS / 失败 $FAIL"
if [ "$FAIL" -ne 0 ]; then
    echo "失败项:$FAILED_NAMES"
    exit 1
fi
