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

# 改了文件名的类：这些走后面的配对表（PAIRS），
# 必须在按路径的循环里跳过，否则会被算两遍、把文件数与行数虚增。
#
# 这一份名单**只是**为了让按路径的循环跳过它们；真正决定「谁和谁配对」的是 PAIRS。
# 两者必须一致 —— 名单里有、PAIRS 里没有的条目会让该文件在两条路径上都被跳过，
# 于是它的重合度被静默算成 0。下面的自检就是为了让这种情况报出来。
RENAMED_BASENAMES="SandboxGuestHost.java SandboxGuestDebug.java SandboxFrida.java FridaEnv.java
SandboxBoard.java SandboxOverlay.java SandboxKeeper.java SandboxShell.java
SandboxRpcService.java SandboxPrefs.java SandboxConsole.java SandboxRpc.java
SandboxProcess.java ZhiSandbox.java
ZhiSandboxTool.java ZhiDebugTool.java
ZhiCodeEngine.java ZhiTool.java ZhiFileProvider.java"

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
com/termux/app/zhicode/core|com/termux/app/iqcode/core|ZhiCodeEngine.java|IQCodeEngine.java
com/termux/app/zhicode/tools|com/termux/app/iqcode/tools|ZhiTool.java|IQTool.java
com/zhizhu/zhicode|com/iqge|ZhiFileProvider.java|IqFileProvider.java
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

# ------------------------------------------------------------ 漏算自检
#
# 这个脚本出过的最大一次错是：路径映射只在「文件名不变」时成立，于是改了类名、
# 因而文件名也变了的文件被静默算成 0 重合。当时最大的一个文件（1353 行、约 96% 相同）
# 就这样在全表里显示为 0，直接导致排批次排错了对象。
#
# 有两种漏法，各查一遍：
#   (a) 文件在 RENAMED_BASENAMES 里（所以按路径的循环跳过了它），但 PAIRS 里没有它
#       —— 它不会出现在报告的任何一行里，从总数上静默消失；
#   (b) 配对表里的本工程文件或原版文件根本不存在 —— pair_renamed_in 会直接 return 0，
#       看着像「无对应文件」，其实是名字写错了。
unpaired=0
warn() {
    unpaired=$((unpaired + 1))
    printf '⚠ %s\n' "$1" >&2
}

# (a) 两份名单必须一致。
for name in $RENAMED_BASENAMES; do
    if ! printf '%s\n' "$PAIRS" | grep -q "|$name|"; then
        warn "改名的 $name 在 RENAMED_BASENAMES 里但没有 PAIRS 配对，它的重合度被静默算成 0"
    fi
done

# (b) 配对表指向的文件必须真的存在。
while IFS='|' read -r ourDir theirDir ourName theirName; do
    [ -n "$ourDir" ] || continue
    [ -f "$OUR/$ourDir/$ourName" ] || warn "PAIRS 里的本工程文件不存在: $ourDir/$ourName"
    [ -f "$OFF/$theirDir/$theirName" ] || warn "PAIRS 里的原版文件不存在: $theirDir/$theirName"
done <<< "$PAIRS"

# (c) 没进跳过名单、映射后也找不到对应文件的行：去掉 Zhi/IQ 前后缀再找一次。
#     找到就说明它其实是个改了名的文件。
while IFS='|' read -r rel counterpart theirs ours shared; do
    [ -n "$rel" ] || continue
    [ "$theirs" = "0" ] && [ "$ours" != "0" ] || continue
    base=$(basename "$rel")
    dir=$(dirname "$rel")
    stem="${base%.*}"
    ext="${base##*.}"
    for prefix in Zhi IQ; do
        candidate="$dir/$prefix$stem.$ext"
        if [ -f "$OFF/$candidate" ]; then
            warn "未配对: $rel —— 按品牌前缀找得到原版 $candidate，应当加进 PAIRS"
            break
        fi
    done
done < "$REPORT"

if [ "$unpaired" -gt 0 ]; then
    echo >&2
    echo "⚠ 共 $unpaired 处，上面的重合度数字不可信（被低估）。修好再重跑。" >&2
fi

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

# 「逐行相同」这个数字本身不够用：它把 `import android.os.Process;`、`}`、`return out;`
# 与真正的算法代码算在同一格里。要决定「还剩多少要重写」，必须知道这些行都是什么。
#
# 分类器**只此一份**（下面这个 CLASSIFY），composition 与 algorithm 两个模式共用。
# 这一点是刻意的：本文件其它地方已经踩过「同一份规则写两遍、然后各自漂移」的坑
# （见上面 RENAMED_BASENAMES 与 PAIRS 必须一致的那段自检）。
#
# 分四桶，规则刻意简单到可以人工核对：
#   骨架 S —— 只有括号分号、import/package、javadoc 的分隔与正文行。
#             任何 Java/Kotlin 文件都长这样，与原版相同不说明任何问题。
#   字面量 L —— 含双引号字符串的行。协议键名（JSON 字段、动作名）与用户可见文案属于
#             契约，改了会让两个组件对不上；这一桶要逐条看过，不能只看总数。
#   声明 D —— 既没有控制流、也没有赋值或方法调用的行：字段、方法签名、注解。
#             它们相同不是因为抄，而是因为「写同一件事只有这一种写法」。
#             全大写名的静态常量（`public static final Status IDLE = Status.IDLE;`，也就是
#             枚举成员那种写法）也算在这一桶：赋值号在，但它没有判断与动作。
#   语句 T —— 剩下的，也就是有判断与动作的行。**这一桶才是排批次该看的那个数**。
#             注意它**不区分**「一行一个方法体的 getter」与真正的分支逻辑 ——
#             `public boolean isIdle() { return status == Status.IDLE; }` 也在里面。
#             所以 T 和 composition 模式打印的「去重后」两个数要一起看：原版里大量相同行
#             其实是同一个写法被重复十九遍。
#
# 第三桶的存在是有来历的：最初只分三桶（骨架/字面量/其它），而「其它」里混着大量
# `public final String planFile;` 这类声明，导致 PlanWorkflowState 看起来有 70 行
# 「其它行、占比 70%」—— 与人工查阅的结论（真正算法只有 4~6 行）差了一个数量级。
CLASSIFY='
  BEGIN {
    kw = "(^|[^A-Za-z0-9_])(if|for|while|return|throw|catch|switch|case|else|new|instanceof)([^A-Za-z0-9_]|$)"
  }
  {
    line = $0
    if (line ~ /^[{}();,\[\] ]*$/ || line ~ /^import / || line ~ /^package / || line ~ /^\*/) {
      print "S|" line; next
    }
    if (line ~ /"[^"]*"/) { print "L|" line; next }
    if (line ~ /static final [A-Za-z0-9_<>\[\]]+ [A-Z][A-Z0-9_]* = /) {
      print "D|" line; next
    }
    if (line ~ kw || line ~ /&&/ || line ~ /\|\|/ || line ~ /->/ \
        || line ~ /(^|[^=!<>])=([^=]|$)/ \
        || line ~ /\.(put|get|add|set|remove|append|write|read|apply|of|run|call)\(/) {
      print "T|" line; next
    }
    print "D|" line
  }
'

# 用法: PROVENANCE_COMPOSITION=1 bash tools/provenance.sh
if [ "${PROVENANCE_COMPOSITION:-0}" = "1" ]; then
    # 这份全文要能被人拿去逐条看，所以不能放在 $WORK 里 ——
    # 那个目录在脚本退出时被 trap 删掉，打印出来的路径到时已经不存在了。
    STATEMENT_LINES="$PROJECT_ROOT/build/provenance-statement-lines.txt"
    mkdir -p "$(dirname "$STATEMENT_LINES")"
    : > "$STATEMENT_LINES"
    : > "$WORK/comp-counts.txt"
    while IFS='|' read -r rel counterpart theirs ours shared; do
        [ -n "$rel" ] || continue
        case "$rel" in
            com/termux/terminal/*|com/termux/view/*|com/termux/shared/*) continue ;;
        esac
        [ -f "$OUR/$rel" ] && [ -f "$OFF/$counterpart" ] || continue
        normalize "$OUR/$rel" | squash > "$WORK/comp-ours.txt"
        squash < "$OFF/$counterpart" > "$WORK/comp-theirs.txt"
        diff --unchanged-group-format='%=' --old-group-format='' \
             --new-group-format='' --changed-group-format='' \
             "$WORK/comp-theirs.txt" "$WORK/comp-ours.txt" \
            | awk "$CLASSIFY" > "$WORK/comp-classified.txt"
        awk -F'|' '{ c[$1]++ }
             END { printf "%d %d %d %d\n", c["S"] + 0, c["L"] + 0, c["D"] + 0, c["T"] + 0 }' \
            "$WORK/comp-classified.txt" >> "$WORK/comp-counts.txt"
        # 语句桶全文要按文件分段留下名字。没有文件名的清单是没法逐条核对的 ——
        # 「这句话到底是从哪来的」是看这份清单的唯一理由。
        grep '^T|' "$WORK/comp-classified.txt" | cut -d'|' -f2- > "$WORK/comp-statements.txt"
        if [ -s "$WORK/comp-statements.txt" ]; then
            printf '=== %s\n' "$rel" >> "$STATEMENT_LINES"
            cat "$WORK/comp-statements.txt" >> "$STATEMENT_LINES"
        fi
    done < "$REPORT"

    awk '{ s += $1; l += $2; d += $3; t += $4 }
         END {
           printf "\n残留构成（只看非 Termux 上游的文件）：\n"
           printf "  骨架行（括号分号 / import / javadoc）:      %5d 行\n", s
           printf "  含字面量的行（协议键名与用户可见文案）:      %5d 行\n", l
           printf "  声明行（字段、签名、注解、静态常量）:        %5d 行\n", d
           printf "  语句行（有判断与动作 —— 最该重写的地方）:    %5d 行\n", t
           printf "  合计:                                        %5d 行\n", s + l + d + t
         }' "$WORK/comp-counts.txt"
    # 去重后那一个数：原版里大量「语句行」是同一个写法被反复写（十几个同形 getter、
    # 一张 switch 的几十个 case）。逐条核对时看的是形状，所以这个数比上面的 t 更接近
    # 「还剩多少种要重写的东西」。
    dedup=$(grep -v '^=== ' "$STATEMENT_LINES" | sort -u | grep -c .)
    printf "  其中去重后只有:                              %5d 种\n", "${dedup:-0}"
    printf "  「语句行」全文（按文件分段）: %s\n", "$STATEMENT_LINES"
fi

# 按**语句行**排序的清单 —— 这才是排批次该看的那一列。
#
# 为什么要另开一个口径：`PROVENANCE_PER_FILE` 的重合率是「相同行 ÷ 现在行数」
# （分母是重写后的行数，所以注释写得越细，同一处残留显示的重合率越高）。
# 它**不区分**相同的是算法还是签名，于是会系统性地把「公开面大、逻辑少」的数据类
# 排到最前面 —— 而这类文件恰恰最没得改：
#
#   批 H 四个文件（重合率 38%~55%）实测每个只有 4~6 行算法可动；
#   批 I 沙箱宿主层 563 行相同里只有 127 行不是骨架或协议。
#
# 所以这里用**同一个** CLASSIFY 分类器做按文件计数，并按语句行降序排列。
# 用法: PROVENANCE_ALGORITHM=1 bash tools/provenance.sh
if [ "${PROVENANCE_ALGORITHM:-0}" = "1" ]; then
    echo
    echo "按「语句行」排序（重合行里有判断与动作的那些 —— 最该重写的地方）："
    printf "%7s %7s %7s %7s %8s  %s\n" "语句" "声明" "骨架" "字面量" "语句占比" "文件"
    while IFS='|' read -r rel counterpart theirs ours shared; do
        [ -n "$rel" ] || continue
        case "$rel" in
            com/termux/terminal/*|com/termux/view/*|com/termux/shared/*) continue ;;
        esac
        [ -f "$OUR/$rel" ] && [ -f "$OFF/$counterpart" ] || continue
        normalize "$OUR/$rel" | squash > "$WORK/alg-ours.txt"
        squash < "$OFF/$counterpart" > "$WORK/alg-theirs.txt"
        diff --unchanged-group-format='%=' --old-group-format='' \
             --new-group-format='' --changed-group-format='' \
             "$WORK/alg-theirs.txt" "$WORK/alg-ours.txt" \
            | awk "$CLASSIFY" > "$WORK/alg-classified.txt"
        printf '%s %s\n' "$rel" \
            "$(awk -F'|' '{ c[$1]++ } END { printf "%d %d %d %d", c["T"]+0, c["D"]+0, c["S"]+0, c["L"]+0 }' \
                "$WORK/alg-classified.txt")"
    done < "$REPORT" | awk '
        { t = $2; if (t <= 0) next
          total = t + $3 + $4 + $5
          printf "%7d %7d %7d %7d %7.0f%%  %s\n", t, $3, $4, $5, t * 100 / total, $1 }
    ' | sort -k1,1nr
    echo
    echo "注：语句 / 声明 / 骨架 / 字面量 四桶的规则见 CLASSIFY（composition 模式共用同一份）。"
    echo "    「语句」相同的行通常仍不是抄，而是「写同一件事的唯一写法」；要下判断得看全文 ——"
    echo "    PROVENANCE_COMPOSITION=1 输出的那一份就是这些行的全文。"
fi

# 逐个文件的清单。默认不输出，因为日常只需要上面那张汇总表；
# 但要决定「下一步重写哪些文件」时必须有它 —— 否则排批次只能靠感觉。
# 用法: PROVENANCE_PER_FILE=1 bash tools/provenance.sh
if [ "${PROVENANCE_PER_FILE:-0}" = "1" ]; then
    echo
    echo "按重合行数排序（只列仍与 IQ Code 逐行相同的文件）："
    printf "%7s %7s %7s %8s  %s\n" "相同" "原版" "现在" "重合率" "文件"
    awk -F'|' '$5 > 0 { printf "%7d %7d %7d %7.1f%%  %s\n", $5, $3, $4, $5 * 100 / $4, $1 }' "$REPORT" \
        | sort -k1,1nr
fi
