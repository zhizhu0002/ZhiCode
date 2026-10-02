#!/usr/bin/env bash
#
# 纯 JVM 单测的**快路径**。
#
# <h3>为什么不直接用 `./gradlew :app:testDebugUnitTest`</h3>
#
# 那个任务在本机上是**分钟级**的：`--tests` 只过滤「跑哪些测试」，
# 不过滤「编译什么」—— Gradle 仍要走 compileDebugKotlin（带 Compose 编译器插件）、
# compileDebugJavaWithJavac（Termux + zhicode 那棵上千文件的 Java 树）、
# processDebugResources 与打包依赖。改一行代码等几分钟，验证循环就废了。
#
# 而 `app/src/test/` 里有一批测试是**纯 JVM** 的（`zhicode/core/`、`zhicode/api/`、
# `zhicode/tools/` 这些包刻意不 import 任何 `android.*`，见 ApiPureLogicTest 的类注释）。
# 它们只需要：几个源码文件 + 已经编译好的主类目录 + junit + org.json。
# 用 javac 现场编这几个文件，再用 JUnitCore 跑，就是**秒级**。
#
# 覆盖面与 Gradle 那条路完全一致（同一个 JUnit、同一份 org.json 真实现），
# 差别只是不重编整个 app。所以：日常改动的验证走这里，
# 大批次收尾时再跑一次完整的 `./gradlew :app:testDebugUnitTest`。
#
# 用法：
#   bash test-jvm-fast.sh [类名 …]      # 缺省跑下面 NEEDS 里列的全部
#
set -uo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_ROOT" || exit 1

# 需要重新编译的**主源码**（只有被测试直接依赖、且我们可能改动的那几个）。
# 其余主类从 Gradle 已编译的目录里取 —— 那些不是本次改动的对象。
MAIN_SOURCES=(
    app/src/main/java/com/termux/app/zhicode/core/PermissionGate.java
    app/src/main/java/com/termux/app/zhicode/core/PermissionModePolicy.java
    app/src/main/java/com/termux/app/zhicode/tools/WebSearchJson.java
    app/src/main/java/com/termux/app/zhicode/tools/WebSearchTool.java
)

# 需要编译并运行的测试类（相对 app/src/test/java、点号包名）。
DEFAULT_TESTS=(
    com.termux.app.zhicode.core.PermissionModeMatrixTest
    com.termux.app.zhicode.api.ApiPureLogicTest
    com.termux.app.zhicode.tools.WebSearchJsonTest
)

TARGETS=("$@")
if [ ${#TARGETS[@]} -eq 0 ]; then
    TARGETS=("${DEFAULT_TESTS[@]}")
fi

# ---- 依赖 jar：都从 Gradle 缓存里找，避免再引一份版本 ----
find_jar() {
    find "$HOME/.gradle/caches/modules-2" -name "$1" 2>/dev/null | head -1
}
JUNIT="$(find_jar 'junit-4.13.2.jar')"
HAMCREST="$(find_jar 'hamcrest-core-1.3.jar')"
JSON="$(find_jar 'json-20240303.jar')"

# android.jar 只为**编译**几个 import 了 android.* 的主源码（当前只有
# WebSearchTool 的 android.text.Html）。测试本身不碰 android，运行时不需要它。
ANDROID_JAR=""
SDK_DIR="$(sed -n 's/^sdk.dir=//p' local.properties 2>/dev/null)"
if [ -n "$SDK_DIR" ] && [ -d "$SDK_DIR/platforms" ]; then
    # 取版本号最大的那个 platforms/android-*
    ANDROID_JAR="$(ls -d "$SDK_DIR"/platforms/android-* 2>/dev/null | sort -V | tail -1)/android.jar"
    [ -f "$ANDROID_JAR" ] || ANDROID_JAR=""
fi

if [ -z "$JUNIT" ] || [ -z "$JSON" ]; then
    echo "找不到 junit / org.json（Gradle 缓存被清过？）" >&2
    echo "  junit: $JUNIT" >&2
    echo "  json : $JSON" >&2
    exit 1
fi

# ---- 已编译的主类目录：Java（javac）与 Kotlin（kotlin-classes）各一份 ----
MAIN_CLASSES_CP=""
for d in \
    app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes \
    app/build/tmp/kotlin-classes/debug; do
    if [ -d "$d" ]; then
        MAIN_CLASSES_CP="${MAIN_CLASSES_CP:+$MAIN_CLASSES_CP:}$d"
    fi
done
if [ -z "$MAIN_CLASSES_CP" ]; then
    echo "找不到已编译的主类目录：先跑一次 ./gradlew :app:assembleDebug" >&2
    exit 1
fi

CP_BASE="$MAIN_CLASSES_CP:$JUNIT:$HAMCREST:$JSON"
# 编译用 classpath 额外带 android.jar（运行时不带：带着反而会让 android.* 走到桩实现）。
CP_COMPILE="$CP_BASE${ANDROID_JAR:+:$ANDROID_JAR}"
OUT=".test-jvm"

# ---- 编译：主源码 + 测试源码，一次 javac ----
# 增量判断偷懒但正确：只要有一个源文件比 OUT 里的标记新就重编。
NEED_BUILD=0
STAMP="$OUT/.stamp"
if [ ! -f "$STAMP" ]; then
    NEED_BUILD=1
else
    for f in "${MAIN_SOURCES[@]}"; do
        [ "$f" -nt "$STAMP" ] && NEED_BUILD=1
    done
    for t in "${TARGETS[@]}"; do
        tf="app/src/test/java/$(echo "$t" | tr '.' '/').java"
        [ -f "$tf" ] && [ "$tf" -nt "$STAMP" ] && NEED_BUILD=1
    done
fi

if [ "$NEED_BUILD" = "1" ]; then
    mkdir -p "$OUT/classes"
    TEST_SOURCES=()
    for t in "${TARGETS[@]}"; do
        tf="app/src/test/java/$(echo "$t" | tr '.' '/').java"
        [ -f "$tf" ] && TEST_SOURCES+=("$tf")
    done
    # -implicit:none：只编我们点名的这几个，依赖的类走 classpath。
    if ! javac -nowarn -implicit:none -d "$OUT/classes" -cp "$CP_COMPILE" \
            "${MAIN_SOURCES[@]}" "${TEST_SOURCES[@]}" 2>"$OUT/javac.log"; then
        echo "编译失败：" >&2
        sed 's/^/  /' "$OUT/javac.log" | head -40 >&2
        exit 1
    fi
    touch "$STAMP"
fi

# ---- 运行：JUnitCore ----
FAILED=0
for t in "${TARGETS[@]}"; do
    tf="app/src/test/java/$(echo "$t" | tr '.' '/').java"
    if [ ! -f "$tf" ]; then
        echo "PASS  $t （源码不存在，跳过）"
        continue
    fi
    if out=$(java -cp "$OUT/classes:$CP_BASE" org.junit.runner.JUnitCore "$t" 2>&1); then
        n=$(echo "$out" | grep -o "OK ([0-9]* test" | grep -o "[0-9]*")
        echo "PASS  ${t##*.}  （${n:-?} 条）"
    else
        echo "FAIL  ${t##*.}"
        echo "$out" | sed 's/^/      /' | head -20
        FAILED=1
    fi
done

echo "-----"
if [ "$FAILED" = "0" ]; then
    echo "全部通过"
else
    echo "有失败项"
fi
exit "$FAILED"
