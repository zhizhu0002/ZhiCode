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
 * | [Menu] | `menu` | `symbols/web/menu/materialsymbolsrounded/menu_fill1_24px.svg` |
 * | [Contrast] | `contrast` | `symbols/web/contrast/materialsymbolsrounded/contrast_fill1_24px.svg` |
 * | [Circle] | `circle` | `symbols/web/circle/materialsymbolsrounded/circle_fill1_24px.svg` |
 * | [Settings] | `settings` | `symbols/web/settings/materialsymbolsrounded/settings_fill1_24px.svg` |
 * | [ChatBubble] | `chat_bubble` | `symbols/web/chat_bubble/materialsymbolsrounded/chat_bubble_fill1_24px.svg` |
 * | [Difference] | `difference` | `symbols/web/difference/materialsymbolsrounded/difference_fill1_24px.svg` |
 * | [Terminal] | `terminal` | `symbols/web/terminal/materialsymbolsrounded/terminal_fill1_24px.svg` |
 * | [Folder] | `folder` | `symbols/web/folder/materialsymbolsrounded/folder_fill1_24px.svg` |
 * | [AddCircle] | `add_circle` | `symbols/web/add_circle/materialsymbolsrounded/add_circle_fill1_24px.svg` |
 * | [History] | `history` | `symbols/web/history/materialsymbolsrounded/history_fill1_24px.svg` |
 * | [Home] | `home` | `symbols/web/home/materialsymbolsrounded/home_fill1_24px.svg` |
 * | [Person] | `person` | `symbols/web/person/materialsymbolsrounded/person_fill1_24px.svg` |
 * | [Extension] | `extension` | `symbols/web/extension/materialsymbolsrounded/extension_fill1_24px.svg` |
 * | [DeployedCode] | `deployed_code` | `symbols/web/deployed_code/materialsymbolsrounded/deployed_code_fill1_24px.svg` |
 * | [Build] | `build` | `symbols/web/build/materialsymbolsrounded/build_fill1_24px.svg` |
 * | [Add] | `add` | `symbols/web/add/materialsymbolsrounded/add_fill1_24px.svg` |
 * | [Send] | `send` | `symbols/web/send/materialsymbolsrounded/send_fill1_24px.svg` |
 * | [Stop] | `stop` | `symbols/web/stop/materialsymbolsrounded/stop_fill1_24px.svg` |
 * | [ChevronRight] | `chevron_right` | `symbols/web/chevron_right/materialsymbolsrounded/chevron_right_fill1_24px.svg` |
 * | [ExpandMore] | `expand_more` | `symbols/web/expand_more/materialsymbolsrounded/expand_more_fill1_24px.svg` |
 * | [Close] | `close` | `symbols/web/close/materialsymbolsrounded/close_fill1_24px.svg` |
 * | [Edit] | `edit` | `symbols/web/edit/materialsymbolsrounded/edit_fill1_24px.svg` |
 * | [Refresh] | `refresh` | `symbols/web/refresh/materialsymbolsrounded/refresh_fill1_24px.svg` |
 * | [Info] | `info` | `symbols/web/info/materialsymbolsrounded/info_fill1_24px.svg` |
 * | [InkEraser] | `ink_eraser` | `symbols/web/ink_eraser/materialsymbolsrounded/ink_eraser_fill1_24px.svg` |
 * | [ArrowUpward] | `arrow_upward` | `symbols/web/arrow_upward/materialsymbolsrounded/arrow_upward_fill1_24px.svg` |
 * | [ArrowBack] | `arrow_back` | `symbols/web/arrow_back/materialsymbolsrounded/arrow_back_fill1_24px.svg` |
 * | [MoreHoriz] | `more_horiz` | `symbols/web/more_horiz/materialsymbolsrounded/more_horiz_fill1_24px.svg` |
 * | [MoreVert] | `more_vert` | `symbols/web/more_vert/materialsymbolsrounded/more_vert_fill1_24px.svg` |
 * | [Search] | `search` | `symbols/web/search/materialsymbolsrounded/search_fill1_24px.svg` |
 * | [CheckCircle] | `check_circle` | `symbols/web/check_circle/materialsymbolsrounded/check_circle_fill1_24px.svg` |
 * | [Check] | `check` | `symbols/web/check/materialsymbolsrounded/check_fill1_24px.svg` |
 * | [Error] | `error` | `symbols/web/error/materialsymbolsrounded/error_fill1_24px.svg` |
 * | [RadioButtonUnchecked] | `radio_button_unchecked` | `symbols/web/radio_button_unchecked/materialsymbolsrounded/radio_button_unchecked_fill1_24px.svg` |
 * | [Help] | `help` | `symbols/web/help/materialsymbolsrounded/help_fill1_24px.svg` |
 * | [FolderOpen] | `folder_open` | `symbols/web/folder_open/materialsymbolsrounded/folder_open_fill1_24px.svg` |
 * | [DriveFileMove] | `drive_file_move` | `symbols/web/drive_file_move/materialsymbolsrounded/drive_file_move_fill1_24px.svg` |
 * | [Description] | `description` | `symbols/web/description/materialsymbolsrounded/description_fill1_24px.svg` |
 * | [Delete] | `delete` | `symbols/web/delete/materialsymbolsrounded/delete_fill1_24px.svg` |
 * | [Image] | `image` | `symbols/web/image/materialsymbolsrounded/image_fill1_24px.svg` |
 * | [Cloud] | `cloud` | `symbols/web/cloud/materialsymbolsrounded/cloud_fill1_24px.svg` |
 * | [List] | `list` | `symbols/web/list/materialsymbolsrounded/list_fill1_24px.svg` |
 * | [Timer] | `timer` | `symbols/web/timer/materialsymbolsrounded/timer_fill1_24px.svg` |
 * | [Link] | `link` | `symbols/web/link/materialsymbolsrounded/link_fill1_24px.svg` |
 * | [Tune] | `tune` | `symbols/web/tune/materialsymbolsrounded/tune_fill1_24px.svg` |
 * | [Lock] | `lock` | `symbols/web/lock/materialsymbolsrounded/lock_fill1_24px.svg` |
 * | [Compress] | `compress` | `symbols/web/compress/materialsymbolsrounded/compress_fill1_24px.svg` |
 * | [Keyboard] | `keyboard` | `symbols/web/keyboard/materialsymbolsrounded/keyboard_fill1_24px.svg` |
 * | [Hub] | `hub` | `symbols/web/hub/materialsymbolsrounded/hub_fill1_24px.svg` |
 * | [Layers] | `layers` | `symbols/web/layers/materialsymbolsrounded/layers_fill1_24px.svg` |
 * | [Code] | `code` | `symbols/web/code/materialsymbolsrounded/code_fill1_24px.svg` |
 *
 * 取用日期：见 git 首次提交本文件的时间；重新取用请跑 `bash tools/material-symbols-fetch.sh`。
 */
internal object ZhiMaterialIcons {

    /** 上游 `Menu`（materialsymbolsrounded / fill1）。 */
    val Menu: ImageVector by lazy { material("Menu", "M160-240q-17 0-28.5-11.5T120-280q0-17 11.5-28.5T160-320h640q17 0 28.5 11.5T840-280q0 17-11.5 28.5T800-240H160Zm0-200q-17 0-28.5-11.5T120-480q0-17 11.5-28.5T160-520h640q17 0 28.5 11.5T840-480q0 17-11.5 28.5T800-440H160Zm0-200q-17 0-28.5-11.5T120-680q0-17 11.5-28.5T160-720h640q17 0 28.5 11.5T840-680q0 17-11.5 28.5T800-640H160Z") }

    /** 上游 `Contrast`（materialsymbolsrounded / fill1）。 */
    val Contrast: ImageVector by lazy { material("Contrast", "M480-80q-83 0-156-31.5T197-197q-54-54-85.5-127T80-480q0-83 31.5-156T197-763q54-54 127-85.5T480-880q83 0 156 31.5T763-763q54 54 85.5 127T880-480q0 83-31.5 156T763-197q-54 54-127 85.5T480-80Zm40-83q119-15 199.5-104.5T800-480q0-123-80.5-212.5T520-797v634Z") }

    /** 上游 `Circle`（materialsymbolsrounded / fill1）。 */
    val Circle: ImageVector by lazy { material("Circle", "M480-80q-83 0-156-31.5T197-197q-54-54-85.5-127T80-480q0-83 31.5-156T197-763q54-54 127-85.5T480-880q83 0 156 31.5T763-763q54 54 85.5 127T880-480q0 83-31.5 156T763-197q-54 54-127 85.5T480-80Z") }

    /** 上游 `Settings`（materialsymbolsrounded / fill1）。 */
    val Settings: ImageVector by lazy { material("Settings", "M433-80q-27 0-46.5-18T363-142l-9-66q-13-5-24.5-12T307-235l-62 26q-25 11-50 2t-39-32l-47-82q-14-23-8-49t27-43l53-40q-1-7-1-13.5v-27q0-6.5 1-13.5l-53-40q-21-17-27-43t8-49l47-82q14-23 39-32t50 2l62 26q11-8 23-15t24-12l9-66q4-26 23.5-44t46.5-18h94q27 0 46.5 18t23.5 44l9 66q13 5 24.5 12t22.5 15l62-26q25-11 50-2t39 32l47 82q14 23 8 49t-27 43l-53 40q1 7 1 13.5v27q0 6.5-2 13.5l53 40q21 17 27 43t-8 49l-48 82q-14 23-39 32t-50-2l-60-26q-11 8-23 15t-24 12l-9 66q-4 26-23.5 44T527-80h-94Zm49-260q58 0 99-41t41-99q0-58-41-99t-99-41q-59 0-99.5 41T342-480q0 58 40.5 99t99.5 41Z") }

    /** 上游 `ChatBubble`（materialsymbolsrounded / fill1）。 */
    val ChatBubble: ImageVector by lazy { material("ChatBubble", "m240-240-92 92q-19 19-43.5 8.5T80-177v-623q0-33 23.5-56.5T160-880h640q33 0 56.5 23.5T880-800v480q0 33-23.5 56.5T800-240H240Z") }

    /** 上游 `Difference`（materialsymbolsrounded / fill1）。 */
    val Difference: ImageVector by lazy { material("Difference", "M500-600v40q0 17 11.5 28.5T540-520q17 0 28.5-11.5T580-560v-40h40q17 0 28.5-11.5T660-640q0-17-11.5-28.5T620-680h-40v-40q0-17-11.5-28.5T540-760q-17 0-28.5 11.5T500-720v40h-40q-17 0-28.5 11.5T420-640q0 17 11.5 28.5T460-600h40Zm-40 240h160q17 0 28.5-11.5T660-400q0-17-11.5-28.5T620-440H460q-17 0-28.5 11.5T420-400q0 17 11.5 28.5T460-360ZM320-200q-33 0-56.5-23.5T240-280v-560q0-33 23.5-56.5T320-920h247q16 0 30.5 6t25.5 17l194 194q11 11 17 25.5t6 30.5v367q0 33-23.5 56.5T760-200H320ZM160-40q-33 0-56.5-23.5T80-120v-520q0-17 11.5-28.5T120-680q17 0 28.5 11.5T160-640v520h400q17 0 28.5 11.5T600-80q0 17-11.5 28.5T560-40H160Z") }

    /** 上游 `Terminal`（materialsymbolsrounded / fill1）。 */
    val Terminal: ImageVector by lazy { material("Terminal", "M160-160q-33 0-56.5-23.5T80-240v-480q0-33 23.5-56.5T160-800h640q33 0 56.5 23.5T880-720v480q0 33-23.5 56.5T800-160H160Zm0-80h640v-400H160v400Zm187-200-76-76q-12-12-11.5-28t12.5-28q12-11 28-11.5t28 11.5l104 104q12 12 12 28t-12 28L328-308q-11 11-27.5 11.5T272-308q-11-11-11-28t11-28l75-76Zm173 160q-17 0-28.5-11.5T480-320q0-17 11.5-28.5T520-360h160q17 0 28.5 11.5T720-320q0 17-11.5 28.5T680-280H520Z") }

    /** 上游 `Folder`（materialsymbolsrounded / fill1）。 */
    val Folder: ImageVector by lazy { material("Folder", "M160-160q-33 0-56.5-23.5T80-240v-480q0-33 23.5-56.5T160-800h207q16 0 30.5 6t25.5 17l57 57h320q33 0 56.5 23.5T880-640v400q0 33-23.5 56.5T800-160H160Z") }

    /** 上游 `AddCircle`（materialsymbolsrounded / fill1）。 */
    val AddCircle: ImageVector by lazy { material("AddCircle", "M440-440v120q0 17 11.5 28.5T480-280q17 0 28.5-11.5T520-320v-120h120q17 0 28.5-11.5T680-480q0-17-11.5-28.5T640-520H520v-120q0-17-11.5-28.5T480-680q-17 0-28.5 11.5T440-640v120H320q-17 0-28.5 11.5T280-480q0 17 11.5 28.5T320-440h120Zm40 360q-83 0-156-31.5T197-197q-54-54-85.5-127T80-480q0-83 31.5-156T197-763q54-54 127-85.5T480-880q83 0 156 31.5T763-763q54 54 85.5 127T880-480q0 83-31.5 156T763-197q-54 54-127 85.5T480-80Z") }

    /** 上游 `History`（materialsymbolsrounded / fill1）。 */
    val History: ImageVector by lazy { material("History", "M480-120q-126 0-223-76.5T131-392q-4-15 6-27.5t27-14.5q16-2 29 6t18 24q24 90 99 147t170 57q117 0 198.5-81.5T760-480q0-117-81.5-198.5T480-760q-69 0-129 32t-101 88h70q17 0 28.5 11.5T360-600q0 17-11.5 28.5T320-560H160q-17 0-28.5-11.5T120-600v-160q0-17 11.5-28.5T160-800q17 0 28.5 11.5T200-760v54q51-64 124.5-99T480-840q75 0 140.5 28.5t114 77q48.5 48.5 77 114T840-480q0 75-28.5 140.5t-77 114q-48.5 48.5-114 77T480-120Zm40-376 100 100q11 11 11 28t-11 28q-11 11-28 11t-28-11L452-452q-6-6-9-13.5t-3-15.5v-159q0-17 11.5-28.5T480-680q17 0 28.5 11.5T520-640v144Z") }

    /** 上游 `Home`（materialsymbolsrounded / fill1）。 */
    val Home: ImageVector by lazy { material("Home", "M160-200v-360q0-19 8.5-36t23.5-28l240-180q21-16 48-16t48 16l240 180q15 11 23.5 28t8.5 36v360q0 33-23.5 56.5T720-120H600q-17 0-28.5-11.5T560-160v-200q0-17-11.5-28.5T520-400h-80q-17 0-28.5 11.5T400-360v200q0 17-11.5 28.5T360-120H240q-33 0-56.5-23.5T160-200Z") }

    /** 上游 `Person`（materialsymbolsrounded / fill1）。 */
    val Person: ImageVector by lazy { material("Person", "M480-480q-66 0-113-47t-47-113q0-66 47-113t113-47q66 0 113 47t47 113q0 66-47 113t-113 47ZM160-240v-32q0-34 17.5-62.5T224-378q62-31 126-46.5T480-440q66 0 130 15.5T736-378q29 15 46.5 43.5T800-272v32q0 33-23.5 56.5T720-160H240q-33 0-56.5-23.5T160-240Z") }

    /** 上游 `Extension`（materialsymbolsrounded / fill1）。 */
    val Extension: ImageVector by lazy { material("Extension", "M352-120H200q-33 0-56.5-23.5T120-200v-152q48 0 84-30.5t36-77.5q0-47-36-77.5T120-568v-152q0-33 23.5-56.5T200-800h160q0-42 29-71t71-29q42 0 71 29t29 71h160q33 0 56.5 23.5T800-720v160q42 0 71 29t29 71q0 42-29 71t-71 29v160q0 33-23.5 56.5T720-120H568q0-50-31.5-85T460-240q-45 0-76.5 35T352-120Z") }

    /** 上游 `DeployedCode`（materialsymbolsrounded / fill1）。 */
    val DeployedCode: ImageVector by lazy { material("DeployedCode", "M440-91 160-252q-19-11-29.5-29T120-321v-318q0-22 10.5-40t29.5-29l280-161q19-11 40-11t40 11l280 161q19 11 29.5 29t10.5 40v318q0 22-10.5 40T800-252L520-91q-19 11-40 11t-40-11Zm0-366v274l40 23 40-23v-274l240-139v-42l-43-25-237 137-237-137-43 25v42l240 139Z") }

    /** 上游 `Build`（materialsymbolsrounded / fill1）。 */
    val Build: ImageVector by lazy { material("Build", "M360-360q-100 0-170-70t-70-170q0-20 3-40t11-38q5-10 12.5-15t16.5-7q9-2 18.5.5T199-689l105 105 72-72-105-105q-8-8-10.5-17.5T260-797q2-9 7-16.5t15-12.5q18-8 38-11t40-3q100 0 170 70t70 170q0 23-4 43.5T584-516l202 200q29 29 29 71t-29 71q-29 29-71 29t-71-30L444-376q-20 8-40.5 12t-43.5 4Z") }

    /** 上游 `Add`（materialsymbolsrounded / fill1）。 */
    val Add: ImageVector by lazy { material("Add", "M440-440H240q-17 0-28.5-11.5T200-480q0-17 11.5-28.5T240-520h200v-200q0-17 11.5-28.5T480-760q17 0 28.5 11.5T520-720v200h200q17 0 28.5 11.5T760-480q0 17-11.5 28.5T720-440H520v200q0 17-11.5 28.5T480-200q-17 0-28.5-11.5T440-240v-200Z") }

    /** 上游 `Send`（materialsymbolsrounded / fill1）。 */
    val Send: ImageVector by lazy { material("Send", "M176-183q-20 8-38-3.5T120-220v-180l320-80-320-80v-180q0-22 18-33.5t38-3.5l616 260q25 11 25 37t-25 37L176-183Z") }

    /** 上游 `Stop`（materialsymbolsrounded / fill1）。 */
    val Stop: ImageVector by lazy { material("Stop", "M240-320v-320q0-33 23.5-56.5T320-720h320q33 0 56.5 23.5T720-640v320q0 33-23.5 56.5T640-240H320q-33 0-56.5-23.5T240-320Z") }

    /** 上游 `ChevronRight`（materialsymbolsrounded / fill1）。 */
    val ChevronRight: ImageVector by lazy { material("ChevronRight", "M504-480 348-636q-11-11-11-28t11-28q11-11 28-11t28 11l184 184q6 6 8.5 13t2.5 15q0 8-2.5 15t-8.5 13L404-268q-11 11-28 11t-28-11q-11-11-11-28t11-28l156-156Z") }

    /** 上游 `ExpandMore`（materialsymbolsrounded / fill1）。 */
    val ExpandMore: ImageVector by lazy { material("ExpandMore", "M480-362q-8 0-15-2.5t-13-8.5L268-557q-11-11-11-28t11-28q11-11 28-11t28 11l156 156 156-156q11-11 28-11t28 11q11 11 11 28t-11 28L508-373q-6 6-13 8.5t-15 2.5Z") }

    /** 上游 `Close`（materialsymbolsrounded / fill1）。 */
    val Close: ImageVector by lazy { material("Close", "M480-424 284-228q-11 11-28 11t-28-11q-11-11-11-28t11-28l196-196-196-196q-11-11-11-28t11-28q11-11 28-11t28 11l196 196 196-196q11-11 28-11t28 11q11 11 11 28t-11 28L536-480l196 196q11 11 11 28t-11 28q-11 11-28 11t-28-11L480-424Z") }

    /** 上游 `Edit`（materialsymbolsrounded / fill1）。 */
    val Edit: ImageVector by lazy { material("Edit", "M160-120q-17 0-28.5-11.5T120-160v-97q0-16 6-30.5t17-25.5l505-504q12-11 26.5-17t30.5-6q16 0 31 6t26 18l55 56q12 11 17.5 26t5.5 30q0 16-5.5 30.5T817-647L313-143q-11 11-25.5 17t-30.5 6h-97Zm544-528 56-56-56-56-56 56 56 56Z") }

    /** 上游 `Refresh`（materialsymbolsrounded / fill1）。 */
    val Refresh: ImageVector by lazy { material("Refresh", "M480-160q-134 0-227-93t-93-227q0-134 93-227t227-93q69 0 132 28.5T720-690v-70q0-17 11.5-28.5T760-800q17 0 28.5 11.5T800-760v200q0 17-11.5 28.5T760-520H560q-17 0-28.5-11.5T520-560q0-17 11.5-28.5T560-600h128q-32-56-87.5-88T480-720q-100 0-170 70t-70 170q0 100 70 170t170 70q68 0 124.5-34.5T692-367q8-14 22.5-19.5t29.5-.5q16 5 23 21t-1 30q-41 80-117 128t-169 48Z") }

    /** 上游 `Info`（materialsymbolsrounded / fill1）。 */
    val Info: ImageVector by lazy { material("Info", "M480-280q17 0 28.5-11.5T520-320v-160q0-17-11.5-28.5T480-520q-17 0-28.5 11.5T440-480v160q0 17 11.5 28.5T480-280Zm0-320q17 0 28.5-11.5T520-640q0-17-11.5-28.5T480-680q-17 0-28.5 11.5T440-640q0 17 11.5 28.5T480-600Zm0 520q-83 0-156-31.5T197-197q-54-54-85.5-127T80-480q0-83 31.5-156T197-763q54-54 127-85.5T480-880q83 0 156 31.5T763-763q54 54 85.5 127T880-480q0 83-31.5 156T763-197q-54 54-127 85.5T480-80Z") }

    /** 上游 `InkEraser`（materialsymbolsrounded / fill1）。 */
    val InkEraser: ImageVector by lazy { material("InkEraser", "M690-240h150q17 0 28.5 11.5T880-200q0 17-11.5 28.5T840-160H610l80-80Zm-483 80q-8 0-15.5-3t-13.5-9l-73-73q-23-23-23.5-57t22.5-58l440-456q23-24 56.5-24t56.5 23l199 199q23 23 23 57t-23 57L532-172q-6 6-13.5 9t-15.5 3H207Z") }

    /** 上游 `ArrowUpward`（materialsymbolsrounded / fill1）。 */
    val ArrowUpward: ImageVector by lazy { material("ArrowUpward", "M440-647 244-451q-12 12-28 11.5T188-452q-11-12-11.5-28t11.5-28l264-264q6-6 13-8.5t15-2.5q8 0 15 2.5t13 8.5l264 264q11 11 11 27.5T772-452q-12 12-28.5 12T715-452L520-647v447q0 17-11.5 28.5T480-160q-17 0-28.5-11.5T440-200v-447Z") }

    /** 上游 `ArrowBack`（materialsymbolsrounded / fill1）。 */
    val ArrowBack: ImageVector by lazy { material("ArrowBack", "m313-440 196 196q12 12 11.5 28T508-188q-12 11-28 11.5T452-188L188-452q-6-6-8.5-13t-2.5-15q0-8 2.5-15t8.5-13l264-264q11-11 27.5-11t28.5 11q12 12 12 28.5T508-715L313-520h447q17 0 28.5 11.5T800-480q0 17-11.5 28.5T760-440H313Z") }

    /** 上游 `MoreHoriz`（materialsymbolsrounded / fill1）。 */
    val MoreHoriz: ImageVector by lazy { material("MoreHoriz", "M240-400q-33 0-56.5-23.5T160-480q0-33 23.5-56.5T240-560q33 0 56.5 23.5T320-480q0 33-23.5 56.5T240-400Zm240 0q-33 0-56.5-23.5T400-480q0-33 23.5-56.5T480-560q33 0 56.5 23.5T560-480q0 33-23.5 56.5T480-400Zm240 0q-33 0-56.5-23.5T640-480q0-33 23.5-56.5T720-560q33 0 56.5 23.5T800-480q0 33-23.5 56.5T720-400Z") }

    /** 上游 `MoreVert`（materialsymbolsrounded / fill1）。 */
    val MoreVert: ImageVector by lazy { material("MoreVert", "M480-160q-33 0-56.5-23.5T400-240q0-33 23.5-56.5T480-320q33 0 56.5 23.5T560-240q0 33-23.5 56.5T480-160Zm0-240q-33 0-56.5-23.5T400-480q0-33 23.5-56.5T480-560q33 0 56.5 23.5T560-480q0 33-23.5 56.5T480-400Zm0-240q-33 0-56.5-23.5T400-720q0-33 23.5-56.5T480-800q33 0 56.5 23.5T560-720q0 33-23.5 56.5T480-640Z") }

    /** 上游 `Search`（materialsymbolsrounded / fill1）。 */
    val Search: ImageVector by lazy { material("Search", "M380-320q-109 0-184.5-75.5T120-580q0-109 75.5-184.5T380-840q109 0 184.5 75.5T640-580q0 44-14 83t-38 69l224 224q11 11 11 28t-11 28q-11 11-28 11t-28-11L532-372q-30 24-69 38t-83 14Zm0-80q75 0 127.5-52.5T560-580q0-75-52.5-127.5T380-760q-75 0-127.5 52.5T200-580q0 75 52.5 127.5T380-400Z") }

    /** 上游 `CheckCircle`（materialsymbolsrounded / fill1）。 */
    val CheckCircle: ImageVector by lazy { material("CheckCircle", "m424-408-86-86q-11-11-28-11t-28 11q-11 11-11 28t11 28l114 114q12 12 28 12t28-12l226-226q11-11 11-28t-11-28q-11-11-28-11t-28 11L424-408Zm56 328q-83 0-156-31.5T197-197q-54-54-85.5-127T80-480q0-83 31.5-156T197-763q54-54 127-85.5T480-880q83 0 156 31.5T763-763q54 54 85.5 127T880-480q0 83-31.5 156T763-197q-54 54-127 85.5T480-80Z") }

    /** 上游 `Check`（materialsymbolsrounded / fill1）。 */
    val Check: ImageVector by lazy { material("Check", "m382-354 339-339q12-12 28-12t28 12q12 12 12 28.5T777-636L410-268q-12 12-28 12t-28-12L182-440q-12-12-11.5-28.5T183-497q12-12 28.5-12t28.5 12l142 143Z") }

    /** 上游 `Error`（materialsymbolsrounded / fill1）。 */
    val Error: ImageVector by lazy { material("Error", "M480-280q17 0 28.5-11.5T520-320q0-17-11.5-28.5T480-360q-17 0-28.5 11.5T440-320q0 17 11.5 28.5T480-280Zm0-160q17 0 28.5-11.5T520-480v-160q0-17-11.5-28.5T480-680q-17 0-28.5 11.5T440-640v160q0 17 11.5 28.5T480-440Zm0 360q-83 0-156-31.5T197-197q-54-54-85.5-127T80-480q0-83 31.5-156T197-763q54-54 127-85.5T480-880q83 0 156 31.5T763-763q54 54 85.5 127T880-480q0 83-31.5 156T763-197q-54 54-127 85.5T480-80Z") }

    /** 上游 `RadioButtonUnchecked`（materialsymbolsrounded / fill1）。 */
    val RadioButtonUnchecked: ImageVector by lazy { material("RadioButtonUnchecked", "M480-80q-83 0-156-31.5T197-197q-54-54-85.5-127T80-480q0-83 31.5-156T197-763q54-54 127-85.5T480-880q83 0 156 31.5T763-763q54 54 85.5 127T880-480q0 83-31.5 156T763-197q-54 54-127 85.5T480-80Zm0-80q134 0 227-93t93-227q0-134-93-227t-227-93q-134 0-227 93t-93 227q0 134 93 227t227 93Zm0 0q-134 0-227-93t-93-227q0-134 93-227t227-93q134 0 227 93t93 227q0 134-93 227t-227 93Z") }

    /** 上游 `Help`（materialsymbolsrounded / fill1）。 */
    val Help: ImageVector by lazy { material("Help", "M478-240q21 0 35.5-14.5T528-290q0-21-14.5-35.5T478-340q-21 0-35.5 14.5T428-290q0 21 14.5 35.5T478-240Zm2 160q-83 0-156-31.5T197-197q-54-54-85.5-127T80-480q0-83 31.5-156T197-763q54-54 127-85.5T480-880q83 0 156 31.5T763-763q54 54 85.5 127T880-480q0 83-31.5 156T763-197q-54 54-127 85.5T480-80Zm4-572q25 0 43.5 16t18.5 40q0 22-13.5 39T502-525q-23 20-40.5 44T444-427q0 14 10.5 23.5T479-394q15 0 25.5-10t13.5-25q4-21 18-37.5t30-31.5q23-22 39.5-48t16.5-58q0-51-41.5-83.5T484-720q-38 0-72.5 16T359-655q-7 12-4.5 25.5T368-609q14 8 29 5t25-17q11-15 27.5-23t34.5-8Z") }

    /** 上游 `FolderOpen`（materialsymbolsrounded / fill1）。 */
    val FolderOpen: ImageVector by lazy { material("FolderOpen", "M160-160q-33 0-56.5-23.5T80-240v-480q0-33 23.5-56.5T160-800h207q16 0 30.5 6t25.5 17l57 57h360q17 0 28.5 11.5T880-680q0 17-11.5 28.5T840-640H314q-62 0-108 39t-46 99v262l79-263q8-26 29.5-41.5T316-560h516q41 0 64.5 32.5T909-457l-72 240q-8 26-29.5 41.5T760-160H160Z") }

    /** 上游 `DriveFileMove`（materialsymbolsrounded / fill1）。 */
    val DriveFileMove: ImageVector by lazy { material("DriveFileMove", "M160-160q-33 0-56.5-23.5T80-240v-480q0-33 23.5-56.5T160-800h207q16 0 30.5 6t25.5 17l57 57h320q33 0 56.5 23.5T880-640v400q0 33-23.5 56.5T800-160H160Zm328-240-37 37q-11 11-11 28t11 28q11 11 28 11t28-11l105-105q12-12 12-28t-12-28L507-573q-11-11-28-11t-28 11q-11 11-11 28t11 28l37 37H360q-17 0-28.5 11.5T320-440q0 17 11.5 28.5T360-400h128Z") }

    /** 上游 `Description`（materialsymbolsrounded / fill1）。 */
    val Description: ImageVector by lazy { material("Description", "M360-240h240q17 0 28.5-11.5T640-280q0-17-11.5-28.5T600-320H360q-17 0-28.5 11.5T320-280q0 17 11.5 28.5T360-240Zm0-160h240q17 0 28.5-11.5T640-440q0-17-11.5-28.5T600-480H360q-17 0-28.5 11.5T320-440q0 17 11.5 28.5T360-400ZM240-80q-33 0-56.5-23.5T160-160v-640q0-33 23.5-56.5T240-880h287q16 0 30.5 6t25.5 17l194 194q11 11 17 25.5t6 30.5v447q0 33-23.5 56.5T720-80H240Zm280-560q0 17 11.5 28.5T560-600h160L520-800v160Z") }

    /** 上游 `Delete`（materialsymbolsrounded / fill1）。 */
    val Delete: ImageVector by lazy { material("Delete", "M280-120q-33 0-56.5-23.5T200-200v-520q-17 0-28.5-11.5T160-760q0-17 11.5-28.5T200-800h160q0-17 11.5-28.5T400-840h160q17 0 28.5 11.5T600-800h160q17 0 28.5 11.5T800-760q0 17-11.5 28.5T760-720v520q0 33-23.5 56.5T680-120H280Zm120-160q17 0 28.5-11.5T440-320v-280q0-17-11.5-28.5T400-640q-17 0-28.5 11.5T360-600v280q0 17 11.5 28.5T400-280Zm160 0q17 0 28.5-11.5T600-320v-280q0-17-11.5-28.5T560-640q-17 0-28.5 11.5T520-600v280q0 17 11.5 28.5T560-280Z") }

    /** 上游 `Image`（materialsymbolsrounded / fill1）。 */
    val Image: ImageVector by lazy { material("Image", "M200-120q-33 0-56.5-23.5T120-200v-560q0-33 23.5-56.5T200-840h560q33 0 56.5 23.5T840-760v560q0 33-23.5 56.5T760-120H200Zm80-160h400q12 0 18-11t-2-21L586-459q-6-8-16-8t-16 8L450-320l-74-99q-6-8-16-8t-16 8l-80 107q-8 10-2 21t18 11Z") }

    /** 上游 `Cloud`（materialsymbolsrounded / fill1）。 */
    val Cloud: ImageVector by lazy { material("Cloud", "M260-160q-91 0-155.5-63T40-377q0-78 47-139t123-78q25-92 100-149t170-57q117 0 198.5 81.5T760-520q69 8 114.5 59.5T920-340q0 75-52.5 127.5T740-160H260Z") }

    /** 上游 `List`（materialsymbolsrounded / fill1）。 */
    val List: ImageVector by lazy { material("List", "M320-600q-17 0-28.5-11.5T280-640q0-17 11.5-28.5T320-680h480q17 0 28.5 11.5T840-640q0 17-11.5 28.5T800-600H320Zm0 160q-17 0-28.5-11.5T280-480q0-17 11.5-28.5T320-520h480q17 0 28.5 11.5T840-480q0 17-11.5 28.5T800-440H320Zm0 160q-17 0-28.5-11.5T280-320q0-17 11.5-28.5T320-360h480q17 0 28.5 11.5T840-320q0 17-11.5 28.5T800-280H320ZM160-600q-17 0-28.5-11.5T120-640q0-17 11.5-28.5T160-680q17 0 28.5 11.5T200-640q0 17-11.5 28.5T160-600Zm0 160q-17 0-28.5-11.5T120-480q0-17 11.5-28.5T160-520q17 0 28.5 11.5T200-480q0 17-11.5 28.5T160-440Zm0 160q-17 0-28.5-11.5T120-320q0-17 11.5-28.5T160-360q17 0 28.5 11.5T200-320q0 17-11.5 28.5T160-280Z") }

    /** 上游 `Timer`（materialsymbolsrounded / fill1）。 */
    val Timer: ImageVector by lazy { material("Timer", "M400-840q-17 0-28.5-11.5T360-880q0-17 11.5-28.5T400-920h160q17 0 28.5 11.5T600-880q0 17-11.5 28.5T560-840H400Zm80 440q17 0 28.5-11.5T520-440v-160q0-17-11.5-28.5T480-640q-17 0-28.5 11.5T440-600v160q0 17 11.5 28.5T480-400Zm0 320q-74 0-139.5-28.5T226-186q-49-49-77.5-114.5T120-440q0-74 28.5-139.5T226-694q49-49 114.5-77.5T480-800q62 0 119 20t107 58l28-28q11-11 28-11t28 11q11 11 11 28t-11 28l-28 28q38 50 58 107t20 119q0 74-28.5 139.5T734-186q-49 49-114.5 77.5T480-80Z") }

    /** 上游 `Link`（materialsymbolsrounded / fill1）。 */
    val Link: ImageVector by lazy { material("Link", "M280-280q-83 0-141.5-58.5T80-480q0-83 58.5-141.5T280-680h120q17 0 28.5 11.5T440-640q0 17-11.5 28.5T400-600H280q-50 0-85 35t-35 85q0 50 35 85t85 35h120q17 0 28.5 11.5T440-320q0 17-11.5 28.5T400-280H280Zm80-160q-17 0-28.5-11.5T320-480q0-17 11.5-28.5T360-520h240q17 0 28.5 11.5T640-480q0 17-11.5 28.5T600-440H360Zm200 160q-17 0-28.5-11.5T520-320q0-17 11.5-28.5T560-360h120q50 0 85-35t35-85q0-50-35-85t-85-35H560q-17 0-28.5-11.5T520-640q0-17 11.5-28.5T560-680h120q83 0 141.5 58.5T880-480q0 83-58.5 141.5T680-280H560Z") }

    /** 上游 `Tune`（materialsymbolsrounded / fill1）。 */
    val Tune: ImageVector by lazy { material("Tune", "M480-120q-17 0-28.5-11.5T440-160v-160q0-17 11.5-28.5T480-360q17 0 28.5 11.5T520-320v40h280q17 0 28.5 11.5T840-240q0 17-11.5 28.5T800-200H520v40q0 17-11.5 28.5T480-120Zm-320-80q-17 0-28.5-11.5T120-240q0-17 11.5-28.5T160-280h160q17 0 28.5 11.5T360-240q0 17-11.5 28.5T320-200H160Zm160-160q-17 0-28.5-11.5T280-400v-40H160q-17 0-28.5-11.5T120-480q0-17 11.5-28.5T160-520h120v-40q0-17 11.5-28.5T320-600q17 0 28.5 11.5T360-560v160q0 17-11.5 28.5T320-360Zm160-80q-17 0-28.5-11.5T440-480q0-17 11.5-28.5T480-520h320q17 0 28.5 11.5T840-480q0 17-11.5 28.5T800-440H480Zm160-160q-17 0-28.5-11.5T600-640v-160q0-17 11.5-28.5T640-840q17 0 28.5 11.5T680-800v40h120q17 0 28.5 11.5T840-720q0 17-11.5 28.5T800-680H680v40q0 17-11.5 28.5T640-600Zm-480-80q-17 0-28.5-11.5T120-720q0-17 11.5-28.5T160-760h320q17 0 28.5 11.5T520-720q0 17-11.5 28.5T480-680H160Z") }

    /** 上游 `Lock`（materialsymbolsrounded / fill1）。 */
    val Lock: ImageVector by lazy { material("Lock", "M240-80q-33 0-56.5-23.5T160-160v-400q0-33 23.5-56.5T240-640h40v-80q0-83 58.5-141.5T480-920q83 0 141.5 58.5T680-720v80h40q33 0 56.5 23.5T800-560v400q0 33-23.5 56.5T720-80H240Zm240-200q33 0 56.5-23.5T560-360q0-33-23.5-56.5T480-440q-33 0-56.5 23.5T400-360q0 33 23.5 56.5T480-280ZM360-640h240v-80q0-50-35-85t-85-35q-50 0-85 35t-35 85v80Z") }

    /** 上游 `Compress`（materialsymbolsrounded / fill1）。 */
    val Compress: ImageVector by lazy { material("Compress", "M200-400q-17 0-28.5-11.5T160-440q0-17 11.5-28.5T200-480h560q17 0 28.5 11.5T800-440q0 17-11.5 28.5T760-400H200Zm0-120q-17 0-28.5-11.5T160-560q0-17 11.5-28.5T200-600h560q17 0 28.5 11.5T800-560q0 17-11.5 28.5T760-520H200ZM480-80q-17 0-28.5-11.5T440-120v-88l-36 36q-11 11-28 11t-28-11q-11-11-11-28t11-28l104-104q6-6 13-8.5t15-2.5q8 0 15 2.5t13 8.5l104 104q11 11 11.5 27.5T612-172q-11 11-27.5 11.5T556-171l-36-35v86q0 17-11.5 28.5T480-80Zm0-577q-8 0-15-2.5t-13-8.5L348-772q-11-11-11-28t11-28q11-11 28-11t28 11l36 36v-88q0-17 11.5-28.5T480-920q17 0 28.5 11.5T520-880v88l36-36q11-11 28-11t28 11q11 11 11 28t-11 28L508-668q-6 6-13 8.5t-15 2.5Z") }

    /** 上游 `Keyboard`（materialsymbolsrounded / fill1）。 */
    val Keyboard: ImageVector by lazy { material("Keyboard", "M160-200q-33 0-56.5-23.5T80-280v-400q0-33 23.5-56.5T160-760h640q33 0 56.5 23.5T880-680v400q0 33-23.5 56.5T800-200H160Zm200-120h240q17 0 28.5-11.5T640-360q0-17-11.5-28.5T600-400H360q-17 0-28.5 11.5T320-360q0 17 11.5 28.5T360-320ZM240-560q17 0 28.5-11.5T280-600q0-17-11.5-28.5T240-640q-17 0-28.5 11.5T200-600q0 17 11.5 28.5T240-560Zm120 0q17 0 28.5-11.5T400-600q0-17-11.5-28.5T360-640q-17 0-28.5 11.5T320-600q0 17 11.5 28.5T360-560Zm120 0q17 0 28.5-11.5T520-600q0-17-11.5-28.5T480-640q-17 0-28.5 11.5T440-600q0 17 11.5 28.5T480-560Zm120 0q17 0 28.5-11.5T640-600q0-17-11.5-28.5T600-640q-17 0-28.5 11.5T560-600q0 17 11.5 28.5T600-560Zm120 0q17 0 28.5-11.5T760-600q0-17-11.5-28.5T720-640q-17 0-28.5 11.5T680-600q0 17 11.5 28.5T720-560ZM240-440q17 0 28.5-11.5T280-480q0-17-11.5-28.5T240-520q-17 0-28.5 11.5T200-480q0 17 11.5 28.5T240-440Zm120 0q17 0 28.5-11.5T400-480q0-17-11.5-28.5T360-520q-17 0-28.5 11.5T320-480q0 17 11.5 28.5T360-440Zm120 0q17 0 28.5-11.5T520-480q0-17-11.5-28.5T480-520q-17 0-28.5 11.5T440-480q0 17 11.5 28.5T480-440Zm120 0q17 0 28.5-11.5T640-480q0-17-11.5-28.5T600-520q-17 0-28.5 11.5T560-480q0 17 11.5 28.5T600-440Zm120 0q17 0 28.5-11.5T760-480q0-17-11.5-28.5T720-520q-17 0-28.5 11.5T680-480q0 17 11.5 28.5T720-440Z") }

    /** 上游 `Hub`（materialsymbolsrounded / fill1）。 */
    val Hub: ImageVector by lazy { material("Hub", "M240-40q-50 0-85-35t-35-85q0-50 35-85t85-35q14 0 26 3t23 8l57-71q-28-31-39-70t-5-78l-81-27q-17 25-43 40t-58 15q-50 0-85-35T0-580q0-50 35-85t85-35q50 0 85 35t35 85v8l81 28q20-36 53.5-61t75.5-32v-87q-39-11-64.5-42.5T360-840q0-50 35-85t85-35q50 0 85 35t35 85q0 42-26 73.5T510-724v87q42 7 75.5 32t53.5 61l81-28v-8q0-50 35-85t85-35q50 0 85 35t35 85q0 50-35 85t-85 35q-32 0-58.5-15T739-515l-81 27q6 39-5 77.5T614-340l57 70q11-5 23-7.5t26-2.5q50 0 85 35t35 85q0 50-35 85t-85 35q-50 0-85-35t-35-85q0-20 6.5-38.5T624-232l-57-71q-41 23-87.5 23T392-303l-56 71q11 15 17.5 33.5T360-160q0 50-35 85t-85 35Z") }

    /** 上游 `Layers`（materialsymbolsrounded / fill1）。 */
    val Layers: ImageVector by lazy { material("Layers", "M161-366q-16-12-15.5-31.5T162-429q11-8 24-8t24 8l270 209 270-209q11-8 24-8t24 8q16 12 16.5 31.5T799-366L529-156q-22 17-49 17t-49-17L161-366Zm270 8L201-537q-31-24-31-63t31-63l230-179q22-17 49-17t49 17l230 179q31 24 31 63t-31 63L529-358q-22 17-49 17t-49-17Z") }

    /** 上游 `Code`（materialsymbolsrounded / fill1）。 */
    val Code: ImageVector by lazy { material("Code", "m193-479 155 155q11 11 11 28t-11 28q-11 11-28 11t-28-11L108-452q-6-6-8.5-13T97-480q0-8 2.5-15t8.5-13l184-184q12-12 28.5-12t28.5 12q12 12 12 28.5T349-635L193-479Zm574-2L612-636q-11-11-11-28t11-28q11-11 28-11t28 11l184 184q6 6 8.5 13t2.5 15q0 8-2.5 15t-8.5 13L668-268q-12 12-28 11.5T612-269q-12-12-12-28.5t12-28.5l155-155Z") }
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
