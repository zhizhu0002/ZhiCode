#!/usr/bin/env bash
#
# 度量「蜘蛛工程有多少代码仍与 IQ Code 逐行相同」。
#
# 做法：把两棵树的包名/品牌/类名归一化后，按映射路径逐文件比对「逐行相同」的行数。
# 归一化只抹命名，不动代码形态 —— 所以「相同」意味着代码本身没被改写。
#
# 需要本机存在原版：projects/IQ-Code-Android
# 用法: bash tools/provenance.sh
set -uo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUR="$PROJECT_ROOT/app/src/main/java"
OFF="${IQCODE_ORIGINAL:-$(cd "$PROJECT_ROOT/.." && pwd)/IQ-Code-Android}/app/src/main/java"

if [ ! -d "$OFF" ]; then
    echo "找不到原版 IQ Code: $OFF" >&2
    echo "可用 IQCODE_ORIGINAL=<path> 指定其工程根。" >&2
    exit 2
fi

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# 去掉缩进差异与空行
squash() { tr -s ' \t' ' ' | sed 's/^ //; s/ $//' | grep -v '^$'; }

# 只抹命名与品牌，不动代码形态
normalize() {
    sed -e 's/com\.zhizhu\.zhicode/com.iqge/g' \
        -e 's/com\.termux\.app\.zhicode/com.termux.app.iqcode/g' \
        -e 's/ZhiCodeEngine/IQCodeEngine/g' \
        -e 's/ZhiSandboxTool/IQSandboxTool/g' \
        -e 's/ZhiDebugTool/IQDebugTool/g' \
        -e 's/zhisandbox/iqsandbox/g' -e 's/zhidebug/iqdebug/g' \
        -e 's/ZHICODE_SANDBOX/IQGE_SANDBOX/g' \
        -e 's/ZhiCode/IQCode/g' -e 's/Zhi/IQ/g' \
        -e 's/蜘蛛/IQ Code/g' "$1"
}

REPORT="$WORK/report.txt"
: > "$REPORT"

# 改了文件名的类（沙箱层 + Agent 工具层）：这些走后面的配对表，
# 必须在按路径的循环里跳过，否则会被算两遍、把文件数与行数虚增。
RENAMED_BASENAMES="SandboxGuestHost.java SandboxGuestDebug.java SandboxFrida.java FridaEnv.java
SandboxBoard.java SandboxOverlay.java SandboxKeeper.java SandboxShell.java
SandboxRpcService.java SandboxPrefs.java SandboxConsole.java SandboxRpc.java
SandboxProcess.java ZhiSandbox.java
ZhiSandboxTool.java ZhiDebugTool.java"

is_renamed() {
    local base="$1"
    for candidate in $RENAMED_BASENAMES; do
        [ "$base" = "$candidate" ] && return 0
    done
    return 1
}

cd "$OUR"
while IFS= read -r rel; do
    if is_renamed "$(basename "$rel")"; then
        continue
    fi
    case "$rel" in
        com/zhizhu/zhicode/*)     counterpart="com/iqge/${rel#com/zhizhu/zhicode/}" ;;
        com/termux/app/zhicode/*) counterpart="com/termux/app/iqcode/${rel#com/termux/app/zhicode/}" ;;
        *)                        counterpart="$rel" ;;
    esac

    normalize "$OUR/$rel" | squash > "$WORK/ours.txt"
    ours=$(wc -l < "$WORK/ours.txt")

    if [ -f "$OFF/$counterpart" ]; then
        squash < "$OFF/$counterpart" > "$WORK/theirs.txt"
        theirs=$(wc -l < "$WORK/theirs.txt")
        shared=$(diff --unchanged-group-format='%=' --old-group-format='' \
                      --new-group-format='' --changed-group-format='' \
                      "$WORK/theirs.txt" "$WORK/ours.txt" | grep -c .)
        shared=${shared:-0}
    else
        theirs=0; shared=0
    fi
    echo "$rel|$counterpart|$theirs|$ours|$shared" >> "$REPORT"
done < <(find . -name '*.java' -o -name '*.kt' | sed 's#^\./##')

# 单独处理「改了文件名」的类：按英文名配对后重新比对。
# 不做这一步的话，它们会因为路径对不上而被算成「无对应文件」，
# 把重合度误报成 0 —— 数字必须诚实，否则据此做的判断都是错的。
#
# 配对表以「本工程相对目录|原版相对目录|本工程文件名|原版文件名」给出，
# 因为改名同时跨了两个包：沙箱层在 com/zhizhu/zhicode/sandbox → com/iqge/sandbox，
# Agent 工具层在 com/termux/app/zhicode/tools → com/termux/app/iqcode/tools。
PAIRS="
com/zhizhu/zhicode/sandbox|com/iqge/sandbox|SandboxGuestHost.java|SandboxAgentBridge.java
com/zhizhu/zhicode/sandbox|com/iqge/sandbox|SandboxGuestDebug.java|SandboxProcessDebug.java
com/zhizhu/zhicode/sandbox|com/iqge/sandbox|SandboxFrida.java|SandboxFridaBridge.java
com/zhizhu/zhicode/sandbox|com/iqge/sandbox|FridaEnv.java|FridaRuntimeManager.java
com/zhizhu/zhicode/sandbox|com/iqge/sandbox|SandboxBoard.java|SandboxDashboardActivity.java
com/zhizhu/zhicode/sandbox|com/iqge/sandbox|SandboxOverlay.java|SandboxFloatingController.java
com/zhizhu/zhicode/sandbox|com/iqge/sandbox|SandboxKeeper.java|SandboxGuardService.java
com/zhizhu/zhicode/sandbox|com/iqge/sandbox|SandboxShell.java|SandboxTermuxBridge.java
com/zhizhu/zhicode/sandbox|com/iqge/sandbox|SandboxRpcService.java|SandboxControlProvider.java
com/zhizhu/zhicode/sandbox|com/iqge/sandbox|SandboxPrefs.java|SandboxSettingsStore.java
com/zhizhu/zhicode/sandbox|com/iqge/sandbox|SandboxConsole.java|SandboxDebugLog.java
com/zhizhu/zhicode/sandbox|com/iqge/sandbox|SandboxRpc.java|SandboxHostClient.java
com/zhizhu/zhicode/sandbox|com/iqge/sandbox|SandboxProcess.java|SandboxProcessRole.java
com/zhizhu/zhicode/sandbox|com/iqge/sandbox|ZhiSandbox.java|IQSandboxEngine.java
com/termux/app/zhicode/tools|com/termux/app/iqcode/tools|ZhiSandboxTool.java|IQSandboxTool.java
com/termux/app/zhicode/tools|com/termux/app/iqcode/tools|ZhiDebugTool.java|IQDebugTool.java
"

pair_renamed_in() {
    local ourDir="$1" theirDir="$2" ourName="$3" theirName="$4"
    local ourFile="$OUR/$ourDir/$ourName"
    local theirFile="$OFF/$theirDir/$theirName"
    [ -f "$ourFile" ] && [ -f "$theirFile" ] || return 0
    normalize "$ourFile" | squash > "$WORK/ours.txt"
    squash < "$theirFile" > "$WORK/theirs.txt"
    local shared
    shared=$(diff --unchanged-group-format='%=' --old-group-format='' \
                   --new-group-format='' --changed-group-format='' \
                   "$WORK/theirs.txt" "$WORK/ours.txt" | grep -c .)
    echo "$ourDir/$ourName|$theirDir/$theirName|$(wc -l < "$WORK/theirs.txt")|$(wc -l < "$WORK/ours.txt")|${shared:-0}" >> "$REPORT"
}

while IFS='|' read -r ourDir theirDir ourName theirName; do
    [ -n "$ourDir" ] || continue
    pair_renamed_in "$ourDir" "$theirDir" "$ourName" "$theirName"
done <<< "$PAIRS"

awk -F'|' '
function area(path) {
  if (path ~ /^com\/termux\/(terminal|view|shared)\//)      return "Termux 上游（非 IQ Code）"
  if (path ~ /zhicode\/sandbox\//)                          return "沙箱宿主层"
  if (path ~ /zhicode\/compose\//)                           return "Compose 界面层"
  if (path ~ /termux\/app\/zhicode\/tools\//)                return "Agent 工具"
  if (path ~ /termux\/app\/zhicode\/core\//)                 return "Agent 核心"
  if (path ~ /termux\/app\/zhicode\/background\//)           return "后台保活"
  if (path ~ /termux\/app\/zhicode\//)                       return "Termux 集成层"
  return "其它"
}
{
  a = area($1)
  files[a]++; ours[a] += $4; shared[a] += $5; theirs[a] += $3
  total_files++; total_ours += $4; total_shared += $5
  if ($5 > 0) iqcode_lines += $5
}
END {
  printf "%-28s %6s %10s %14s\n", "归属区域", "文件", "行数", "仍与 IQCode 相同"
  for (a in files)
    printf "%-28s %6d %10d %14d\n", a, files[a], ours[a], shared[a]
  printf "%-28s %6d %10d %14d\n", "合计", total_files, total_ours, total_shared
  printf "\n"
  termux_upstream = shared["Termux 上游（非 IQ Code）"]
  printf "已是我们自己的:        %d 行\n", total_ours - total_shared
  printf "逐行相同合计:          %d 行\n", total_shared
  printf "  其中 Termux 上游:     %d 行（Termux 自己的代码，与独立性无关）\n", termux_upstream
  printf "  真正属于 IQ Code:     %d 行\n", total_shared - termux_upstream
}
' "$REPORT"

# 逐个文件的清单。默认不输出，因为日常只需要上面那张汇总表；
# 但要决定「下一步重写哪些文件」时必须有它 —— 否则排批次只能靠感觉。
# 用法: PROVENANCE_PER_FILE=1 bash tools/provenance.sh
if [ "${PROVENANCE_PER_FILE:-0}" = "1" ]; then
    echo
    echo "按重合行数排序（只列仍与 IQ Code 逐行相同的文件）："
    printf "%7s %7s %8s  %s\n" "相同" "现在" "重合率" "文件"
    awk -F'|' '$5 > 0 { printf "%7d %7d %7.1f%%  %s\n", $5, $4, $5 * 100 / $4, $1 }' "$REPORT" \
        | sort -k1,1nr
fi
