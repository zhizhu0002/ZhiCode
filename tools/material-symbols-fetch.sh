#!/data/data/com.termux/files/usr/bin/bash
# 重新取一遍 Material Symbols 的字形数据并重写 ui/ZhiMaterialIcons.kt。
#
# 为什么要有这个脚本：工程里那份 `d` 数据是**从上游逐字拷来的**，不是手画的。
# 没有脚本的话，"这份数据还是上游那份吗"就只能靠相信；有了它，
# 任何人（包括 reviewer）都能重跑一次、diff 出结果。
#
# 上游：https://github.com/google/material-design-icons
#       symbols/web/<名字>/materialsymbolsrounded/<名字>_fill1_24px.svg
# 许可：Apache-2.0（见 NOTICE 第 8 节）
#
# 用法： bash tools/material-symbols-fetch.sh [输出路径]
#        默认输出 app/src/main/java/com/zhizhu/zhicode/compose/ui/ZhiMaterialIcons.kt
set -eu

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${1:-$ROOT/app/src/main/java/com/zhizhu/zhicode/compose/ui/ZhiMaterialIcons.kt}"
BASE="https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web"

# Kotlin 属性名 : 上游字形名
# 上游字形名与 Kotlin 名大多只是大小写/下划线的差别，但有几处是**不同的写法**，
# 故意分开列出以免以后有人以为写错了：
#   ExpandMore  -> expand_more    （上游没有 chevron_down；向下的箭头就是 expand_more）
#   InkEraser   -> ink_eraser     （清屏的橡皮擦）
#   ArrowBack   -> arrow_back
#   DriveFileMove -> drive_file_move （移动文件/目录；与 FolderOpen 分开，两者在同一屏会出现）
GLYPHS="Menu:menu
Contrast:contrast
Circle:circle
Settings:settings
ChatBubble:chat_bubble
Difference:difference
Terminal:terminal
Folder:folder
AddCircle:add_circle
History:history
Home:home
Person:person
Extension:extension
DeployedCode:deployed_code
Build:build
Add:add
Send:send
Stop:stop
ChevronRight:chevron_right
ExpandMore:expand_more
Close:close
Edit:edit
Refresh:refresh
Info:info
InkEraser:ink_eraser
ArrowBack:arrow_back
MoreHoriz:more_horiz
MoreVert:more_vert
Search:search
CheckCircle:check_circle
Check:check
Error:error
RadioButtonUnchecked:radio_button_unchecked
Help:help
FolderOpen:folder_open
DriveFileMove:drive_file_move
Description:description
Delete:delete
Image:image
Cloud:cloud
List:list
Timer:timer
Link:link
Tune:tune
Lock:lock
Compress:compress
Keyboard:keyboard
Hub:hub
Layers:layers
Code:code"

# 校验：Kotlin 名不许重名，上游名不许重名
for col in 1 2; do
    dup="$(printf '%s\n' "$GLYPHS" | cut -d: -f"$col" | sort | uniq -d)"
    if [ -n "$dup" ]; then
        echo "错误：第 $col 列有重复项：$dup" >&2
        exit 1
    fi
done

TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT
COUNT=0

{
    cat <<'HEADER'
package com.zhizhu.zhicode.compose.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.unit.dp

/**
 * 全局图标集的字形数据：**Material Symbols（Rounded, Fill1）**。
 *
 * ## 为什么是这一套
 *
 * 用户的说法是：
 *
 * > 算了和，还是把扁平化（不是线条）和 rikka 等 md3 图标结合起来，沙盒的有点不太好看，MCP 也是
 *
 * 这一段里其实有三件事，各自都可以查证：
 *
 * 1. **「rikka 等 md3 图标」指的是 rikkahub。** 去它的源码取证：它用的是
 *    `me.rerere.hugeicons.stroke.*`（400+ 处）与 `com.composables.icons.lucide`（10 处），
 *    也就是 HugeIcons 与 Lucide —— **两套都是 24×24 的线条集**。
 *    所以「直接抄 rikka 那套」正好落到用户明确排除的「线条」上，不能用。
 * 2. **「扁平化（不是线条）」= 实心填充**。rikkahub 用的那一族里满足这一点的只有
 *    **Material 3 / Material Symbols**（Android 自己的设置页就是这套字形）。
 * 3. **「沙盒的有点不太好看，MCP 也是」** 指上一轮的两个自绘字形
 *    （`ZhiVectorIcons.Cube` 与 `ZhiVectorIcons.Wrench` 那一批）。自绘的东西即使把
 *    包围盒铺到同一个网格上，曲率与圆角也很难跟整套一致 —— 这一版把它们全部
 *    换成上游字形，自绘文件整个删掉了。
 *
 * 于是定为 `Material Symbols Rounded, Fill1`（实心圆角版），上游是
 * `google/material-design-icons`，Apache-2.0。
 *
 * ## 为什么是「把路径数据拷进来」而不是「加一个 Gradle 依赖」
 *
 * 三个候选都查过：
 *
 * | 方案 | 问题 |
 * |---|---|
 * | `androidx.compose.material:material-icons-extended` | 上游已把它标为 deprecated，而且那是 **Material（M2）** 字形、不是 Symbols（形状是老的直角版本） |
 * | rikkahub 的 `me.rerere.hugeicons:stroke` / `com.composables:icons-lucide` | 都是**线条**集，与「不是线条」直接冲突 |
 * | 把上游 SVG 的 `d` 拷进来（**采用**） | 无新依赖；数据逐字来自上游；出处可以用 `tools/material-symbols-fetch.sh` 重跑核对 |
 *
 * 本工程有一条约束是「除 `androidx.profileinstaller` 外不再加新依赖」，
 * 而它在这一轮**依然成立** —— 下面每个字形只是一段字符串。
 *
 * ## 数据是怎么变成 `ImageVector` 的
 *
 * 上游 SVG 的 `viewBox` 是 `0 -960 960 960`：y 轴向上、值域 `[-960, 0]`。
 * Compose 的视口是 y 轴向下、值域 `[0, 24]`。换算是一次缩放 + 一次平移：
 *
 * ```
 * x_comp = x_svg / 40        y_comp = y_svg / 40 + 24
 * ```
 *
 * 落在 `group(scaleX = VIEWPORT_SCALE, scaleY = VIEWPORT_SCALE, translationY = VIEWPORT_FLIP)`
 * 里（`VectorGroup` 的变换是「先 scale 后 translate」，pivot 取 0）。
 * **不要**把它写成 `scaleY = -1f`：那是 Miuix 那边的坐标约定，两边相反。
 *
 * 路径字符串交给 Compose 自己的 `PathParser`（`ui-graphics` 的公开 API）解析，
 * 所以这里**没有第二套解析器**，也不存在"抄错一个数字"的可能 ——
 * 抄错了要么在解析时就炸，要么被 `MaterialSymbolGeometryTest` 量出来。
 *
 * ## 为什么字形以属性而不是 `object` 分组
 *
 * 与 [ZhiIcons] 一样产出 `ImageVector`，由 `ZhiIcons.vector()` 统一转成 `Painter`
 * （那是两种来源合流的唯一通道，见那边的说明）。这里的名字沿用**上游的英文名**，
 * 因为语义名在 [ZhiIcons] 那边（它维护着「哪一行该用哪个字形」的映射表）。
 *
 * ## 只许填充，不许描边
 *
 * 整个文件**不得出现** `stroke` / `strokeLineWidth`：这一集是实心字形，混进一个
 * 描边字形就会在一排实心块里显得像没画完。`IconSetTest` 会逐字检查这一条。
 *
 * ⚠️ **本文件由 `tools/material-symbols-fetch.sh` 生成。**
 * 手改这里的路径数据会在下一次重跑时被覆盖，而且会让"与上游逐字一致"这句话失效。
 * 要换字形，请改脚本里的映射表再重跑。
 *
 * ## 字形清单
 *
 * 下面这张表是**全集**：`ZhiMaterialIcons` 里有多少个字形，它就有多少行。
 * `IconSetTest` 两头都查 —— 表里少一行、或者多出一个没人用的字形，都会失败。
 * 第二条尤其要紧：没人引用的字形说明 [ZhiIcons] 那边的映射改了名字，
 * 留着它只会让人以为"这个字形还在用"。
 *
 * | Kotlin 名 | 上游字形 | 上游路径 |
 * |---|---|---|
HEADER

    # 先把全部字形取下来。分两趟是因为清单表要写在 KDoc 里、
    # 而 KDoc 必须在 object 之前 —— 一趟流式输出做不到。
    GLYPH_DATA="$(mktemp)"
    trap 'rm -f "$GLYPH_DATA"' EXIT
    while IFS=: read -r kotlin_name upstream; do
        [ -z "$kotlin_name" ] && continue
        url="$BASE/$upstream/materialsymbolsrounded/${upstream}_fill1_24px.svg"
        svg="$(curl -fsS --max-time 60 "$url")" || {
            echo "错误：取不到 $url" >&2
            exit 1
        }
        d="$(printf '%s' "$svg" | grep -o 'd="[^"]*"' | head -1 | cut -c4- | rev | cut -c2- | rev)"
        if [ -z "$d" ]; then
            echo "错误：$url 里没有 path 的 d 属性" >&2
            exit 1
        fi
        case "$d" in
            *'"'*|*'$'*|*'\'*)
                echo "错误：$upstream 的 d 里含有会破坏 Kotlin 字符串的字符" >&2
                exit 1
                ;;
        esac
        printf '%s\t%s\n' "$kotlin_name" "$d" >> "$GLYPH_DATA"
        printf ' * | [%s] | `%s` | `symbols/web/%s/materialsymbolsrounded/%s_fill1_24px.svg` |\n' \
            "$kotlin_name" "$upstream" "$upstream" "$upstream"
        COUNT=$((COUNT + 1))
    done <<< "$GLYPHS"

    cat <<'HEADER2'
 *
 * 取用日期：见 git 首次提交本文件的时间；重新取用请跑 `bash tools/material-symbols-fetch.sh`。
 */
internal object ZhiMaterialIcons {
HEADER2

    while IFS="$(printf '\t')" read -r kotlin_name d; do
        printf '\n    /** 上游 `%s`（materialsymbolsrounded / fill1）。 */\n' "$kotlin_name"
        printf '    val %s: ImageVector by lazy { material("%s", "%s") }\n' \
            "$kotlin_name" "$kotlin_name" "$d"
    done < "$GLYPH_DATA"

    cat <<'FOOTER'
}

/**
 * 上游 svg 的 `viewBox` 边长的一半的倒数：960 → 24 就是除以 40。
 *
 * `VectorGroup` 的变换顺序是「先 scale 后 translate」，pivot 取 0。
 */
private const val VIEWPORT_SCALE = 0.025f

/** 把上游 `[-960, 0]` 的 y 轴搬到 Compose 的 `[0, 24]`（y 轴方向也一并翻正）。 */
private const val VIEWPORT_FLIP = 24f

/**
 * 把上游 SVG 的一段 `d` 变成 `ImageVector`。
 *
 * 这是本文件里唯一的构造入口。做成一个函数（而不是每个字形各写一遍
 * `ImageVector.Builder(...)`）的理由是：视口换算只写一次，
 * 改它的时候不可能只改一半 —— 那正是"这个图标和其它不一样大"的来源。
 */
private fun material(name: String, pathData: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
        .group(
            scaleX = VIEWPORT_SCALE,
            scaleY = VIEWPORT_SCALE,
            translationY = VIEWPORT_FLIP,
        ) {
            addPath(
                pathData = PathParser().parsePathString(pathData).toNodes(),
                fill = SolidColor(Color.Black),
            )
        }
        .build()
FOOTER
} > "$TMP"

mv "$TMP" "$OUT"
trap - EXIT
echo "已写入 $OUT（$COUNT 个字形）"
