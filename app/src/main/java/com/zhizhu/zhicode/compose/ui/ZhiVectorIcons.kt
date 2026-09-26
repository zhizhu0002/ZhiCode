package com.zhizhu.zhicode.compose.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 自绘矢量图标：补齐 Miuix 图标库缺失的语义。
 *
 * 背景：[ZhiIcons] 统一走 `MiuixIcons.Regular`，但那个集合只有 **156 个**图标
 * （`top/yukonga/miuix/kmp/icon/extended` 下每个图标一个 `*Kt.class`），实测确实没有
 * 上/右箭头、空心圆、终端、diff、历史这些语义，于是原先只能拿不相干的图标顶上，
 * 例如终端面板挂的是 `Notes`（笔记）、项目历史挂的是 `Refresh`（刷新）。
 * 与其等上游补图，不如自己画：[ImageVector] 本身就是纯 Kotlin 描述的矢量路径，
 * 不依赖任何图片资源，也无需 Miuix 发包。
 *
 * ## 风格必须与 Miuix 一致，否则新旧图标并排会明显不搭
 * 用 javap 反汇编 `AddKt.class` 实测出的 Miuix 做法：
 *  - **填充（fill）风格**：`SolidColor(Color.Black)` 传给 `addPath` 的 fill 参数，
 *    stroke 保持默认（null）——所以这里一律只用 `fill`，不用 `stroke`。
 *  - 内部视口是 `1137.6 × 1137.6`（不是常见的 24），外层套一个 `addGroup`。
 *  - Regular 字重的笔画在该视口下约 80 单位 → 折算到 24 视口约 `1.7`，
 *    本文件的笔画厚度都按这个量级取值（[STROKE]）。
 *
 * 视口用 24 而不是 1137.6 是安全的：`defaultWidth/Height` 才是最终显示尺寸，
 * 视口只是内部坐标缩放，两者等比缩放后视觉大小一致。
 *
 * ## 填充风格下的两个坑
 * 1. **重叠子路径会被挖空**：`+` 号若拆成横条+竖条两个矩形，在 `EvenOdd` 下
 *    交叠处会变成透明。所以这里把 `+` 写成**单一多边形**（十字 12 个点）。
 * 2. **同心圆环要靠 `EvenOdd`**：外圆 + 同向内圆，`EvenOdd` 才会挖出中间的空心。
 */
internal object ZhiVectorIcons {

    /** 24×24 内部视口。 */
    private const val VP = 24f

    /** 等效笔画厚度（24 视口下）。对应 Miuix Regular 的 80/1137.6。 */
    @Suppress("unused")
    private const val STROKE = 1.7f

    /** 图标填充色。Miuix 同款写法，实际显示颜色由 `Icon` 的 tint / LocalContentColor 决定。 */
    private val INK = SolidColor(Color.Black)

    // ── 构造辅助 ────────────────────────────────────────────────────────────

    private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = "ZhiIcons.$name",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = VP,
            viewportHeight = VP,
        ).apply(block).build()

    /** 顺时针圆角矩形。Compose 的 [PathBuilder] 没有 roundRect 辅助，手写。 */
    private fun PathBuilder.roundRect(x0: Float, y0: Float, x1: Float, y1: Float, r: Float) {
        moveTo(x0 + r, y0)
        lineTo(x1 - r, y0)
        arcToRelative(r, r, 0f, false, true, r, r)
        lineTo(x1, y1 - r)
        arcToRelative(r, r, 0f, false, true, -r, r)
        lineTo(x0 + r, y1)
        arcToRelative(r, r, 0f, false, true, -r, -r)
        lineTo(x0, y0 + r)
        arcToRelative(r, r, 0f, false, true, r, -r)
        close()
    }

    /** 整圆（两段半圆圆弧拼成）。 */
    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx, cy - r)
        arcToRelative(r, r, 0f, true, true, 0f, 2 * r)
        arcToRelative(r, r, 0f, true, true, 0f, -2 * r)
        close()
    }

    // ── 六个图标 ────────────────────────────────────────────────────────────

    /**
     * 向上箭头。用于面板标题栏的「返回上一级」。
     * 原先用 `ExpandLess`（`^` 折角），方向对但不是箭头形状。
     */
    val ArrowUp: ImageVector by lazy {
        icon("ArrowUp") {
            path(fill = INK) {
                moveTo(12f, 3f)
                lineTo(21f, 12.6f)
                lineTo(15.5f, 12.6f)
                lineTo(15.5f, 21f)
                lineTo(8.5f, 21f)
                lineTo(8.5f, 12.6f)
                lineTo(3f, 12.6f)
                close()
            }
        }
    }

    /**
     * 向右箭头。用于发送键。
     * 原先用 `Forward`（转发）——形状相近但语义是「转发」。
     */
    val ArrowRight: ImageVector by lazy {
        icon("ArrowRight") {
            path(fill = INK) {
                moveTo(21f, 12f)
                lineTo(13.5f, 4.5f)
                lineTo(13.5f, 10.5f)
                lineTo(3f, 10.5f)
                lineTo(3f, 13.5f)
                lineTo(13.5f, 13.5f)
                lineTo(13.5f, 19.5f)
                close()
            }
        }
    }

    /**
     * 空心圆。用于任务清单里「尚未开始」的项。
     * 原先用 `MoreCircle`——那是**带点的圆**（⋯ 的外圈），不是空圈。
     */
    val RadioButtonUnchecked: ImageVector by lazy {
        icon("RadioButtonUnchecked") {
            path(fill = INK, pathFillType = PathFillType.EvenOdd) {
                circle(12f, 12f, 8.5f)
                circle(12f, 12f, 6.7f)
            }
        }
    }

    /**
     * 终端。用于终端面板。
     * 原先用 `Notes`（笔记），语义完全不符，是当时最将就的一处。
     *
     * 造型：圆角方框（`EvenOdd` 挖空成环）+ `>` 提示符 + 光标下划线。
     */
    val Terminal: ImageVector by lazy {
        icon("Terminal") {
            path(fill = INK, pathFillType = PathFillType.EvenOdd) {
                // 外框与内框同向，靠 EvenOdd 得到 1.8 宽的边框
                roundRect(2.2f, 4.2f, 21.8f, 19.8f, 3f)
                roundRect(4f, 6f, 20f, 18f, 1.5f)

                // `>`：顶点在 (10.6, 12)，带厚度回程（单一多边形，不自交）
                moveTo(6.4f, 8.6f)
                lineTo(10.6f, 12f)
                lineTo(6.4f, 15.4f)
                lineTo(5.2f, 13.9f)
                lineTo(8.2f, 12f)
                lineTo(5.2f, 10.1f)
                close()

                // 光标下划线
                moveTo(12.6f, 14.3f)
                lineTo(17.6f, 14.3f)
                lineTo(17.6f, 16.1f)
                lineTo(12.6f, 16.1f)
                close()
            }
        }
    }

    /**
     * 变更 / diff。用于「变更」面板。
     * 原先用 `Replace`（替换），不像 diff。
     *
     * 造型：圆角方框 + 上 `+` 下 `−`。`+` 必须写成单一多边形，
     * 否则横竖两条在 EvenOdd 下交叠处会被挖空。
     */
    val Diff: ImageVector by lazy {
        icon("Diff") {
            path(fill = INK, pathFillType = PathFillType.EvenOdd) {
                roundRect(3f, 4f, 21f, 20f, 2.5f)
                roundRect(4.8f, 5.8f, 19.2f, 18.2f, 1f)

                // `+`：中心 (12, 8.9)，臂半长 2.2，臂半宽 0.75
                moveTo(11.25f, 6.7f)
                lineTo(12.75f, 6.7f)
                lineTo(12.75f, 8.15f)
                lineTo(14.2f, 8.15f)
                lineTo(14.2f, 9.65f)
                lineTo(12.75f, 9.65f)
                lineTo(12.75f, 11.1f)
                lineTo(11.25f, 11.1f)
                lineTo(11.25f, 9.65f)
                lineTo(9.8f, 9.65f)
                lineTo(9.8f, 8.15f)
                lineTo(11.25f, 8.15f)
                close()

                // `−`：中心 (12, 15.1)
                moveTo(9.4f, 14.35f)
                lineTo(14.6f, 14.35f)
                lineTo(14.6f, 15.85f)
                lineTo(9.4f, 15.85f)
                close()
            }
        }
    }

    /**
     * 历史。用于侧栏「项目历史」入口。
     * 原先用 `Refresh`（刷新），语义偏了。
     *
     * 造型：表盘（EvenOdd 圆环）+ L 形指针。
     */
    val History: ImageVector by lazy {
        icon("History") {
            path(fill = INK, pathFillType = PathFillType.EvenOdd) {
                circle(12f, 12.5f, 8.5f)
                circle(12f, 12.5f, 6.7f)

                // 指针：竖针朝上（≈12 点）、横针朝右（≈3 点），单一 L 形多边形
                moveTo(11.25f, 12.5f)
                lineTo(11.25f, 8.55f)
                lineTo(12.75f, 8.55f)
                lineTo(12.75f, 11.75f)
                lineTo(15.65f, 11.75f)
                lineTo(15.65f, 13.25f)
                lineTo(11.25f, 13.25f)
                close()
            }
        }
    }
}
