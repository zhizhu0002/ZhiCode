#!/usr/bin/env bash
#
# 把 fork 出来的 termux-packages 改成用你自己的包名/前缀构建 bootstrap。
#
# 用法（在 fork 的仓库根目录执行）：
#   ./apply-prefix-changes.sh <新包名> [新Java命名空间] [新显示名]
#
# 例：
#   ./apply-prefix-changes.sh com.zhizhu.code com.zhizhu.code ZhiCode
#
# 只改 scripts/properties.sh 中**实际赋值**的行；路径链全部自动派生，无需手改。
# 幂等：重复运行不会叠加；每次改动保留 .bak。
#
set -euo pipefail

PKG="${1:-}"
NS="${2:-$PKG}"
NAME="${3:-}"

PROPS="./scripts/properties.sh"

if [[ -z "$PKG" ]]; then
    echo "用法: $0 <新包名> [新Java命名空间] [新显示名]" >&2
    echo "例:   $0 com.zhizhu.code com.zhizhu.code ZhiCode" >&2
    exit 64
fi

# 包名必须是合法的 Android applicationId（也是 SAFE_ABSOLUTE_PATH 的组件要求）
if [[ ! "$PKG" =~ ^[a-zA-Z][a-zA-Z0-9]*(\.[a-zA-Z][a-zA-Z0-9]*)+$ ]]; then
    echo "[!] 包名 '$PKG' 不合法：需形如 com.example.app，只允许字母数字和点。" >&2
    exit 65
fi
if [[ ! "$NS" =~ ^[a-zA-Z][a-zA-Z0-9]*(\.[a-zA-Z][a-zA-Z0-9]*)+$ ]]; then
    echo "[!] 命名空间 '$NS' 不合法。" >&2
    exit 65
fi

if [[ ! -f "$PROPS" ]]; then
    echo "[!] 找不到 $PROPS —— 请在 termux-packages 仓库根目录执行本脚本。" >&2
    exit 66
fi

# 官方建议：<=21 字符（最好 <=10），因为要拼进 unix socket 路径等
len=${#PKG}
if [[ $len -gt 21 ]]; then
    echo "[!] 包名长度 $len > 21，官方布局文档不推荐，可能触发 sun_path 超限。" >&2
fi

echo "[*] 目标包名   : $PKG  (长度 $len)"
echo "[*] 命名空间   : $NS"
[[ -n "$NAME" ]] && echo "[*] 显示名     : $NAME"
echo

# set_var <变量名> <新值> [期望当前值正则]
set_var() {
    local var="$1" new="$2" pattern="${3:-^${1}=}"
    local cur
    cur="$(grep -m1 -E "$pattern" "$PROPS" || true)"
    if [[ -z "$cur" ]]; then
        echo "  [!] 未找到变量 $var（模式 $pattern），跳过。" >&2
        return 1
    fi
    if [[ "$cur" == "${var}=\"${new}\"" ]]; then
        echo "  [=] $var 已是目标值，跳过。"
        return 0
    fi
    cp -f "$PROPS" "${PROPS}.bak"
    # 只替换第一次匹配，保留行尾（有些行后面可能带注释）
    awk -v v="$var" -v n="$new" -v p="$pattern" '
        !done && $0 ~ p {
            sub(/=.*/, "=\"" n "\"")
            done=1
        }
        { print }
    ' "${PROPS}.bak" > "$PROPS"
    echo "  [+] $var : $(echo "$cur" | sed 's/^[^=]*=//')  ->  \"$new\""
}

rc=0
set_var "TERMUX_APP__PACKAGE_NAME"        "$PKG"  || rc=1
set_var "TERMUX_APP__NAMESPACE"           "$NS"   || rc=1
[[ -n "$NAME" ]] && { set_var "TERMUX__NAME" "$NAME" || rc=1; }

# 故意**不改** TERMUX_REPO__*（TERMUX_REPO_APP__PACKAGE_NAME / DATA_DIR / CORE_DIR /
# APPS_DIR / ROOTFS / HOME / PREFIX）。官方 properties.sh 的注释写明：
#
#   "If a custom repo is not being hosted, and official Termux repos are still
#    defined in repo.json, then DO NOT change these values."
#
# 我们不自建 apt 源，所以按指示保持原样。这些值只被 build-package.sh 的
# -i/-I（从 apt 源下载预编译依赖）使用，而 build-bootstraps.sh 不传 -i/-I，
# 对 bootstrap 构建无影响；改了只会多出一行 "Ignoring -i option..." 警告。

echo
echo "=== 改动生效确认（直接读文件赋值，不依赖 source）==="
for v in TERMUX_APP__PACKAGE_NAME TERMUX_APP__NAMESPACE TERMUX__NAME; do
    printf "  %-32s %s\n" "$v" "$(grep -m1 -E "^${v}=" "$PROPS" | sed 's/^[^=]*=//')"
done
echo
echo "  以下路径由上面的值自动派生，无需手改："
echo "    TERMUX_APP__DATA_DIR = /data/data/$PKG"
echo "    TERMUX__ROOTFS       = /data/data/$PKG/files"
echo "    TERMUX__PREFIX       = /data/data/$PKG/files/usr"
echo "    TERMUX__HOME         = /data/data/$PKG/files/home"
echo "    am socket            = /data/data/$PKG/files/apps/$PKG/termux-am/am.sock"
echo
cat <<EOF
[*] 下一步（改了包名必须清缓存，官方帮助文本明确要求）：

    ./scripts/run-docker.sh ./clean.sh
    ./scripts/run-docker.sh ./scripts/build-bootstraps.sh --architectures aarch64 2>&1 | tee build.log
    # 产物：仓库根目录 bootstrap-aarch64.zip

[*] 验收：

    java -cp <本目录> VerifyBootstrap bootstrap-aarch64.zip ${PKG}

EOF

exit $rc
