package com.zhizhu.zhicode.compose.theme

import androidx.compose.ui.unit.dp

object MochiBaseline {

    // ---- 圆角九档（1:1 对齐 MochiShapes）----

    val cornerExtraSmall = 4.dp
    val cornerSmall = 8.dp
    val cornerMedium = 12.dp
    val cornerLarge = 16.dp
    val cornerLargeIncreased = 20.dp
    val cornerExtraLarge = 28.dp
    val cornerExtraLargeIncreased = 32.dp
    val cornerExtraExtraLarge = 48.dp

    // ---- 间距节奏 ----

    /** 页面级水平边距（手机单手防误触基准）。 */
    val pageMargin = 16.dp

    /** 页面级水平边距（折叠屏 / 平板）。 */
    val pageMarginWide = 24.dp

    /** 列表行文字的**内容起点**（不含卡片内边距）。 */
    val rowTextInset = 16.dp

    /** 页面内一级区块之间的距离。 */
    val sectionGap = 12.dp

    /** 行与行之间、行内元素之间。 */
    val rowGap = 8.dp

    /** 相邻可点元素之间的最小间距（防误触）。 */
    val touchGap = 8.dp

    // ---- 行高与控件高度 ----

    val rowHeight = 52.dp

    /** 紧凑行（终端扩展键、次级信息）。 */
    val compactRowHeight = 44.dp

    /** 按钮 / 输入框的标准高度。 */
    val controlHeight = 48.dp

    /** 最小触控热区（Android 无障碍规范）。 */
    val minTouchTarget = 48.dp

    /** 密集工具行里的触控热区下限。 */
    val compactTouchTarget = 40.dp

    // ---- 同心 ----

    /** 由外层圆角与内边距推出内层圆角，落实 `inner = outer − padding`。 */
    fun concentric(outerRadius: androidx.compose.ui.unit.Dp, padding: androidx.compose.ui.unit.Dp) =
        (outerRadius - padding).coerceAtLeast(0.dp)
}
