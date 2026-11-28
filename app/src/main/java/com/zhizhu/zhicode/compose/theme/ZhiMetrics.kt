package com.zhizhu.zhicode.compose.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 「Zhi 平面玻璃」设计语言的令牌层：间距、行高、半径、玻璃档。 */
object ZhiSpace {

    /** 微间距：图标与文字之间、 chip 组内缝隙。 */
    val xs: Dp = 4.dp

    /** 小间距：相关元素之间（行内 padding、分组内行距）。 */
    val s: Dp = 8.dp

    /** 标准间距：区块之间、列表左右留白。 */
    val m: Dp = 12.dp

    /** 大间距：独立区块之间、弹窗 insideMargin 基准。 */
    val l: Dp = 16.dp

    /** 特大间距：页面级留白、弹窗标题与正文之间。 */
    val xl: Dp = 24.dp
}

object ZhiRow {
    val height: Dp = MochiBaseline.rowHeight

    /** 紧凑行（次级信息、终端扩展键等触控要求低的场景）。 */
    val compact: Dp = MochiBaseline.compactRowHeight
}

/** 半径语义档，转发到 [ZhiRadius] 的现有值上 —— 老常量名继续由守卫与 */
object ZhiShape {
    val sheet: Dp = ZhiRadius.floating
    val card: Dp = ZhiRadius.card
    val button: Dp = ZhiRadius.button
    val inner: Dp = ZhiRadius.inner
}

/** 玻璃模糊三档（Glass.kt 的调用约定） */
object ZhiGlass {
    const val TopBarAlpha: Float = 0.72f
    const val FloatingBlur: Float = 24f
    const val DialogBlur: Float = 48f
}
