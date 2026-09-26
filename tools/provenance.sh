#!/usr/bin/env bash
#
# 度量「蜘蛛工程有多少代码仍与 IQ Code 逐行相同」。
#
# 做法：把两棵树的包名/品牌/类名归一化后，按映射路径逐文件比对「逐行相同」的行数。
# 归一化只抹命名，不动代码形态 —— 所以「相同」意味着代码本身没被改写。
#
# 需要本机存在原版：projects/IQ-Code-Android
#
# 汇总表下面会给出**净相同行** = 逐行相同 - 骨架行 - 跨组件协议串行。
# 「差多少才叫独立」看的是这个数：前两类行不是「别人的代码」，任何人在这个需求下都会那么写。
#
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

# ------------------------------------------------------------ 跨组件协议串
#
# 「逐行相同」这个数字本身不够用：它把 `import android.os.Process;`、`}`、`return out;`
# 与真正的算法代码算在同一格里。要回答「还差多少才叫独立」，必须把**不构成派生的行**扣掉。
#
# 可扣的只有两类，规则刻意窄到可以人工核对（声明行与语句行**永远**不扣）：
#   1. 骨架行：整行只有括号分号、import/package、javadoc。
#      任何 Java/Kotlin 文件都长这样，与原版相同不说明任何问题。
#   2. 跨组件协议串行：该行的**每一个**字符串字面量都在本工程 >=2 个文件里出现。
#      这些串是「两个组件按同一个名字对齐」（宿主 / Agent 工具 / Frida 脚本 / 持久化键名），
#      改了会让两边对不上 —— 那是协议，不是表达。
#
# 这一条是**规则**，不是事后挑数字：脚本会把每个被扣的串连同它出现的文件写进 build/ 供核对
# （「对齐的另外两处」要能被指出来，而不是让你相信这条规则）。
# 已知的松处：一句用户可见的文案若恰好出现在两个文件里，也会被算进这一类 —— 所以净数字
# 要连同那份审计文件一起看，不能只看一个数。
#
# 提取用**归一化后**的内容，与比对时看到的是同一份文字（否则 `"蜘蛛"` 这类串会对不上）。
PROTO_AUDIT="$PROJECT_ROOT/build/provenance-protocol-strings.txt"
PROTO_LIST="$WORK/protocol-strings.txt"
: > "$WORK/string-files.tsv"
while IFS= read -r f; do
    case "$f" in
        ./com/termux/terminal/*|./com/termux/view/*|./com/termux/shared/*) continue ;;
    esac
    normalize "$f" | awk -v file="${f#./}" '
        {
          while (match($0, /"[^"]*"/)) {
            print substr($0, RSTART, RLENGTH) "\t" file
            $0 = substr($0, RSTART + RLENGTH)
          }
        }' >> "$WORK/string-files.tsv"
done < <(find . -name '*.java' -o -name '*.kt')

# 三个条件同时成立才判为「协议串」—— 少任何一个都会把不该扣的行扣掉：
#   1. 在本工程 >=2 个文件里出现：它存在的理由是「两个组件按同一个名字对齐」；
#   2. 不是空串 ""：空串不表达任何东西，但它出现在 97 个文件里，
#      只按第 1 条判会一次多扣几十行（第一版就是这么错的，实测多扣 344 行）；
#   3. 全 ASCII 且至少含一个字母或数字：本工程里的中文串是**用户可见文案**，
#      那是可以改写的（重写文案正是要做的事），所以它属于「还要重写」，不属于「可扣」。
#      只由符号组成的串（", "、":"）是分隔符，同样**不扣** —— 宁可把数字算高。
sort -u "$WORK/string-files.tsv" | awk -F'\t' '
    {
      files[$1] = files[$1] (files[$1] == "" ? "" : " ") $2
      n[$1]++
    }
    END {
      for (s in n) {
        if (n[s] < 2) continue
        # 先把转义序列摘掉再找字母数字：否则 `"\n"`（21 个文件里都出现）会因为那个 n
        # 被当成「含字母的名字」而被扣 —— 它其实和 `","` 一样是分隔符。
        plain = s
        gsub(/\\[^"\\]/, "", plain)
        ok = (s != "\"\"" && s ~ /^"[ -~]*"$/ && plain ~ /[A-Za-z0-9]/)
        printf "%s\t%s\t%d\t%s\n", (ok ? "P" : "X"), s, n[s], files[s]
      }
    }' | sort > "$WORK/strings-classified.tsv"

awk -F'\t' '$1 == "P" { print $2 }' "$WORK/strings-classified.tsv" > "$PROTO_LIST"

mkdir -p "$(dirname "$PROTO_AUDIT")"
{
    echo "# 被判为「跨组件协议串」的字符串字面量（默认运行即写，供核对）"
    echo "# 规则: 在本工程 >=2 个文件里出现 + 非空 + 全 ASCII + 至少含一个字母/数字。列: 串<TAB>文件数<TAB>出现的文件"
    echo "# 这些串所在的残留行会从「逐行相同」里扣除。若某一行本该改、却出现在此，就是这条规则太松。"
    awk -F'\t' '$1 == "P" { printf "%s\t%d\t%s\n", $2, $3, $4 }' "$WORK/strings-classified.tsv" | sort -k2,2nr
    echo "#"
    echo "# 下面是**没有被扣**的重复串（中文文案 / 空串 / 纯符号）。它们留在净相同行里，"
    echo "# 也就是仍然要重写或被论证 —— 列在这里是为了让人能检查「是不是有协议串被漏掉了」。"
    awk -F'\t' '$1 == "X" { printf "%s\t%d\t%s\n", $2, $3, $4 }' "$WORK/strings-classified.tsv" | sort -k2,2nr
} > "$PROTO_AUDIT"

# 这一段**始终执行**：净相同行是要放在汇总表下面的头条数字，不能只在一个模式里出现。
# 语句行全文的 dump 才是有开关的那部分（它很大，只有逐条核对时才需要）。
#
# 用法: PROVENANCE_COMPOSITION=1 bash tools/provenance.sh   # 追加语句行全文 + 去重种数
#      SHAPE 模式要用到这里产出的语句行全文（$STATEMENT_LINES），所以它隐含 COMPOSITION ——
#      这样 diff 循环在脚本里仍然只有这一处，与「分类器只此一份」是同一个理由。
#
# 语句行全文不能放在 $WORK 里 —— 那个目录在脚本退出时被 trap 删掉，
# 打印出来的路径到时已经不存在了（这个坑踩过一次：承诺「供逐条核对」而文件已删）。
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
        # 五个数：骨架 / 字面量 / 声明 / 语句 / 其中可扣的字面量行（跨组件协议串）。
        # 第五个数是 L 的**子集**，不是另加一桶 —— 合计仍然是前四个之和。
        awk -F'|' -v proto="$PROTO_LIST" '
            BEGIN { while ((getline s < proto) > 0) PROTO[s] = 1 }
            # 该行算「跨组件协议串行」吗：它必须至少含一个字符串，
            # 且**每一个**字符串都在 >=2 个文件里出现过。有一个不是，这行就不能扣。
            function deductibleL(text,   n, s, ok) {
              n = 0; ok = 1
              while (match(text, /"[^"]*"/)) {
                n++
                s = substr(text, RSTART, RLENGTH)
                if (!(s in PROTO)) { ok = 0; break }
                text = substr(text, RSTART + RLENGTH)
              }
              return (n > 0 && ok)
            }
            {
              if ($1 == "S") s++
              else if ($1 == "L") { l++; if (deductibleL(substr($0, 3))) p++ }
              else if ($1 == "D") d++
              else t++
            }
            END { printf "%d %d %d %d %d\n", s + 0, l + 0, d + 0, t + 0, p + 0 }
            ' "$WORK/comp-classified.txt" >> "$WORK/comp-counts.txt"
        if [ "${PROVENANCE_COMPOSITION:-0}" = "1" ] || [ "${PROVENANCE_SHAPE:-0}" = "1" ]; then
            # 语句桶全文要按文件分段留下名字。没有文件名的清单是没法逐条核对的 ——
            # 「这句话到底是从哪来的」是看这份清单的唯一理由。
            grep '^T|' "$WORK/comp-classified.txt" | cut -d'|' -f2- > "$WORK/comp-statements.txt"
            if [ -s "$WORK/comp-statements.txt" ]; then
                printf '=== %s\n' "$rel" >> "$STATEMENT_LINES"
                cat "$WORK/comp-statements.txt" >> "$STATEMENT_LINES"
            fi
        fi
    done < "$REPORT"

    awk '{ s += $1; l += $2; d += $3; t += $4; p += $5 }
         END {
           printf "\n残留构成（只看非 Termux 上游的文件）：\n"
           printf "  骨架行（括号分号 / import / javadoc）:      %5d 行\n", s
           printf "  含字面量的行（协议键名与用户可见文案）:      %5d 行\n", l
           printf "  声明行（字段、签名、注解、静态常量）:        %5d 行\n", d
           printf "  语句行（有判断与动作 —— 最该重写的地方）:    %5d 行\n", t
           printf "  合计:                                        %5d 行\n", s + l + d + t
           printf "\n  可以扣掉的（不构成「留着别人的代码」）：\n"
           printf "    骨架行（任何 Java 文件都长这样）:           %5d 行\n", s
           printf "    跨组件协议串行（>=2 个文件按同一名字对齐）:  %5d 行\n", p
           printf "    可扣合计:                                  %5d 行\n", s + p
           printf "  净相同行（合计 - 可扣 = 还差多少）:          %5d 行\n", s + l + d + t - s - p
         }' "$WORK/comp-counts.txt"
    printf '  协议串审计（每个被扣的串出现在哪几个文件）: %s\n' "$PROTO_AUDIT"
    if [ "${PROVENANCE_COMPOSITION:-0}" = "1" ] || [ "${PROVENANCE_SHAPE:-0}" = "1" ]; then
        # 去重后那一个数：原版里大量「语句行」是同一个写法被反复写（十几个同形 getter、
        # 一张 switch 的几十个 case）。逐条核对时看的是形状，所以这个数比上面的 t 更接近
        # 「还剩多少种要重写的东西」。
        dedup=$(grep -v '^=== ' "$STATEMENT_LINES" | sort -u | grep -c .)
        # 注意：格式串必须用单引号、参数跟在空格之后。写成 printf "…", "$x" 时那个逗号
        # 会紧贴在右引号后、被 shell 拼进格式串本身 —— 格式串末尾多一个逗号，而它在 \n 之后
        # 又没有换行，于是会被下一行输出接到行首（这个坑本文件里踩过两次）。
        printf '  其中去重后只有:                              %5d 种\n' "${dedup:-0}"
        printf '  「语句行」全文（按文件分段）: %s\n' "$STATEMENT_LINES"
    fi

# ------------------------------------------------------------ 形状收敛度量
#
# 要回答的是：剩下的语句行里，有多少是**同一件事被写了几遍** —— 那是唯一一种
# 「收敛就能省行」的余量，也是唯一值得再投入重写的理由。
#
# 为什么必须扫**连续多行**窗口，而不是统计单行形状：
#   `if (x == null) continue;` 本身就是一行，重复二十次也只是二十行。
#   把它提取成一个 helper 之后是「一个 helper + 二十个调用点」，行数**更多**。
#   所以单行形状的重复度只能说明「模板化程度」，不能算收益（下面的摘要里单独列出）。
#   真正能省行的是**多行模板**（取值 → 判空 → 用），它在语句清单里表现为几行连续重复。
#   因此这里扫长度 2~4 的连续窗口，再按「极大窗口」汇总，避免 2 行窗口被算进 4 行模板里。
#
# 两个局限（必须与数字一起读，否则会高估收益）：
#   1. 窗口取自 diff 的**未变块**流，可能跨过已经被我们改写过的行 ——
#      也就是说窗口里相邻的两行在源文件里未必真的相邻。这里给的邻接性是**近似**的，
#      只能当排序指标；真正动手前必须去读源文件确认。
#   2. 只出现一次的形状里，既有「Java 里只有这一种写法」的惯用法，也有真正独有的逻辑。
#      「这段能不能换个写法」是人的判断，度量分不出来（能收敛的行要人去读）。
#   3. 归一化把字段名也抹掉了，于是「连续几行字段赋值」也会长得一样。那不是模板
#      —— 每一行赋的是**不同**字段，没有共同的「体」可以提取。所以窗口里若
#      **没有任何一行在调用方法**，就不计入收益（见 countable()）。这是规则，不是事后挑数字。
#   所以下面那个合计是**上限**（天花板），不是可完成的工作量。
#
# 用法: PROVENANCE_SHAPE=1 bash tools/provenance.sh
#      PROVENANCE_SHAPE_TOP=20 可调每档列出的条数（默认 10）
SHAPE_OF='
  function shapeOf(line,   out, pre, w, rest, nxt, r) {
    HADCALL = 0
    # 1) 去掉字符串字面量的内容（保留引号，看得出「这里有个字符串参数」）
    gsub(/"[^"]*"/, "\"\"", line)
    # 2) 一趟扫标识符，扫过的部分不回扫 —— 否则 <T>/<v> 里的字母会被再当成标识符
    out = ""
    while (match(line, /[A-Za-z_][A-Za-z0-9_]*/)) {
      pre  = substr(line, 1, RSTART - 1)
      w    = substr(line, RSTART, RLENGTH)
      rest = substr(line, RSTART + RLENGTH)
      nxt  = substr(rest, 1, 1)
      if (w in KW)                      r = w    # 关键字原样
      else if (nxt == "(") { r = w; HADCALL = 1 }   # 方法/构造器名：形状的实质；顺便标记「这行真的在调用」
      else if (w ~ /^[A-Z][A-Z0-9_]*$/) r = w    # 常量（IDLE / UTF_8 / ACTION_VIEW）
      else if (w ~ /^[A-Z]/)            r = "<T>" # 类型
      else                              r = "<v>" # 变量/字段
      out = out pre r
      line = rest
    }
    out = out line
    # 3) 数字 → #。必须放在标识符那一趟**之后**：若先换成 <n>，那个 n 会被当成变量名
    gsub(/[0-9]+/, "#", out)
    gsub(/[ \t]+/, " ", out)
    sub(/^ /, "", out)
    sub(/ $/, "", out)
    return out
  }

  # 「能计数的窗口」= 出现 >=3 次、且窗口里至少有一行**真的在调用方法**（HADCALL）。
  # 为什么加后一个条件：连续几行字段赋值（this.id = id; this.rev = rev; …）归一化之后长得
  # 一模一样，但它们不是模板 —— 每一行赋的是**不同**字段，没有共同的「体」可以提取，
  # 收敛它们只会让代码更难读。所以这类窗口不计入收益。
  function countable(L, g, j,   m, wk) {
    if (j < 1 || j + L - 1 > C[g]) return 0
    wk = WK[L SUBSEP g SUBSEP j]
    if (wk == "" || W[wk] < 3) return 0
    for (m = 0; m < L; m++) if (HAS[g, j + m]) return 1
    return 0
  }

  BEGIN {
    n = split("if else for while do return throw catch switch case default new instanceof break continue try finally class interface extends implements public private protected static final void int long boolean double float char byte short true false null this super throws synchronized abstract native package import enum assert in volatile transient strictfp", kw, " ")
    for (i = 1; i <= n; i++) KW[kw[i]] = 1
  }
  /^=== / { f = substr($0, 5); nf++; F[nf] = f; next }
  # HAS[f,c]：这一行在归一化前是否真的调用了方法 —— 由 shapeOf 通过 HADCALL 带出来
  { c = ++C[f]; S[f, c] = shapeOf($0); HAS[f, c] = HADCALL }
  END {
    # ---- 单行形状：只统计，不计入收益
    for (i = 1; i <= nf; i++) {
      g = F[i]
      for (j = 1; j <= C[g]; j++) single[S[g, j]]++
    }
    for (k in single) {
      if (single[k] >= 2) { repShapes++; repLines += single[k] }
      else { onceShapes++; onceLines += single[k] }
    }

    # ---- 连续 2~4 行窗口计数；key 存下来供后面复用，避免重复拼接
    for (i = 1; i <= nf; i++) {
      g = F[i]
      for (L = 2; L <= 4; L++) {
        for (j = 1; j + L - 1 <= C[g]; j++) {
          key = S[g, j]
          for (m = 2; m <= L; m++) key = key SUBSEP S[g, j + m - 1]
          wk = L SUBSEP key
          if (!(wk in LW)) { LW[wk] = L; SHAPE[wk] = key }
          W[wk]++
          WK[L SUBSEP g SUBSEP j] = wk
          for (m = 0; m < L; m++) if (HAS[g, j + m]) HASW[wk] = 1
          fk = wk SUBSEP g
          if (!(fk in seenFile)) { seenFile[fk] = 1; WFILE[wk]++ }
        }
      }
    }

    # ---- 极大窗口的贪心汇总：只算「不被更长的计数窗口包含」的那些，且每个形状只计一次
    total = 0; maximalShapes = 0
    for (i = 1; i <= nf; i++) {
      g = F[i]
      for (j = 1; j <= C[g]; j++) {
        for (L = 4; L >= 2; L--) {
          if (!countable(L, g, j)) continue
          # 被更长的计数窗口包住（左边多一行、或右边多一行）就不算极大
          if (L < 4 && (countable(L + 1, g, j - 1) || countable(L + 1, g, j))) continue
          wk = WK[L SUBSEP g SUBSEP j]
          if (wk in counted) continue
          counted[wk] = 1
          sv = (L - 1) * W[wk] - (L + 2)
          if (sv > 0) { total += sv; maximalShapes++ }
        }
      }
    }

    # ---- 候选明细（供 shell 排序）。只列出现 >= 3 次的。
    excluded = 0
    for (wk in W) {
      if (W[wk] < 3) continue
      L = LW[wk]
      sv = (L - 1) * W[wk] - (L + 2)
      if (sv < 0) sv = 0
      body = (wk in HASW) ? 1 : 0
      if (!body) excluded++
      shape = SHAPE[wk]
      gsub(SUBSEP, " ;; ", shape)
      printf "%d\t%d\t%d\t%d\t%d\t%s\n", L, W[wk], WFILE[wk], sv, body, shape >> detail
    }

    printf "语句行总数: %d 行 / %d 个文件\n", onceLines + repLines, nf
    printf "  属于「出现 >=2 次的单行形状」: %5d 行（%d 种）—— 单行重复不省行，只看模板化程度\n", repLines, repShapes
    printf "  只出现一次的独立形状:          %5d 行（%d 种）\n", onceLines, onceShapes
    printf "\n估算可省语句行上限: %d 行（来自 %d 个极大模板形状，彼此不重叠）\n", total, maximalShapes
    printf "  另有 %d 个重复窗口已排除：它们全是赋值/声明，没有共同的「体」可提取\n", excluded
    printf "省行公式: (窗口行数 - 1) x 出现次数 - (窗口行数 + 2)\n"
  }
'

# 用法: PROVENANCE_SHAPE=1 bash tools/provenance.sh
if [ "${PROVENANCE_SHAPE:-0}" = "1" ]; then
    SHAPE_OUT="$PROJECT_ROOT/build/provenance-shapes.txt"
    SHAPE_TOP="${PROVENANCE_SHAPE_TOP:-10}"
    DETAIL="$WORK/shape-candidates.tsv"
    : > "$DETAIL"
    mkdir -p "$(dirname "$SHAPE_OUT")"

    echo
    echo "形状收敛度量（与文件无关，只看「同一件事被写了几遍」）："
    awk -v detail="$DETAIL" "$SHAPE_OF" "$STATEMENT_LINES"
    echo
    echo "重复出现的多行模板（窗口 4 / 3 / 2 行，各取前 $SHAPE_TOP 项）："
    printf "%8s %4s %6s %6s  %s\n" "可省" "行数" "出现" "文件" "模板形状（<T>=类型 <v>=变量 #=数字）"
    for L in 4 3 2; do
        awk -F'\t' -v L="$L" '$1 == L && $5 == 1 { printf "%8d %4d %6d %6d  %s\n", $4, $1, $2, $3, $6 }' "$DETAIL" \
            | sort -k1,1nr | head -"$SHAPE_TOP"
    done
    echo
    echo "  每一行都是一处「可以收敛成 helper」的地方，「可省」是估算值。"
    echo "  两个局限（别当承诺看）："
    echo "   1) 窗口取自 diff 的未变块，可能跨过已被改写的行 —— 邻接性是近似的，"
    echo "      动手前必须读源文件确认这几行真的连在一起。"
    echo "   2) 只出现一次的形状里，惯用法与真正独有的逻辑混在一起；"
    echo "      「这段能不能换个写法」度量分不出来。"
    echo "   所以上面那个上限是天花板，不是可完成的工作量。"
    {
        echo "# 形状收敛度量（PROVENANCE_SHAPE=1 bash tools/provenance.sh）"
        echo "# 列: 窗口行数<TAB>出现次数<TAB>涉及文件数<TAB>估算可省行数<TAB>是否模板(1/0)<TAB>形状"
        echo "# 局限见 tools/provenance.sh 里的注释：邻接性是近似的；合计是上限。"
        sort -k4,4nr "$DETAIL"
    } > "$SHAPE_OUT"
    printf '  候选明细全文: %s\n' "$SHAPE_OUT"
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
