package com.zhizhu.zhicode.compose.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 「Zhi 平面玻璃」设计语言的令牌层：间距、行高、半径、玻璃档。
 *
 * 全部新 UI 代码的间距/行高/半径一律从这里取，不再散落字面量 ——
 * 让整个应用共享同一个 4dp 节奏，这是"看起来被设计过"的最低保障。
 */
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

/**
 * 统一行高基准：列表行、面板头、抽屉行、设置行全部 44dp。
 * 44dp 同时也是 Android 无障碍推荐的最小触控目标。
 */
object ZhiRow {
    val height: Dp = 44.dp

    /** 紧凑行（次级信息、终端扩展键等触控要求低的场景）。 */
    val compact: Dp = 36.dp
}

/**
 * 半径语义档，转发到 [ZhiRadius] 的现有值上 —— 老常量名继续由守卫与
 * 调用点使用，这里提供同一套值的"设计语言读法"：
 *
 *  · [sheet]   浮层/抽屉/贴底面板（= ZhiRadius.floating 20dp）
 *  · [card]    卡片、弹窗容器（= ZhiRadius.card 14dp）
 *  · [button]  按钮（= ZhiRadius.button 12dp）
 *  · [inner]   卡内小块（= ZhiRadius.inner 10dp）
 *
 * chip/图标按钮的正圆不在此处：按高度取 `height / 2`。
 */
object ZhiShape {
    val sheet: Dp = ZhiRadius.floating
    val card: Dp = ZhiRadius.card
    val button: Dp = ZhiRadius.button
    val inner: Dp = ZhiRadius.inner
}

/**
 * 玻璃模糊三档（Glass.kt 的调用约定）：
 *
 *  · [TopBarAlpha]   顶栏/面板头半透明底 alpha
 *  · [FloatingBlur]  悬浮条（Composer、任务卡）blur 半径
 *  · [DialogBlur]    弹窗背板 blur 半径
 */
object ZhiGlass {
    const val TopBarAlpha: Float = 0.72f
    const val FloatingBlur: Float = 24f
    const val DialogBlur: Float = 48f
}
