package com.zhizhu.zhicode.compose.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.squircle.squircleBackground

/**
 * 设置行的行首图标块：**彩色圆角方块 + 白字形**。
 *
 * ## 为什么是这样一块底色
 *
 * 用户的上一轮反馈是「有些图标扁扁的，不好看」，随后指定：
 *
 * > 那就借鉴小米自带的设置的图标
 *
 * 参考物是小米「设置」应用本身（用户贴了整页截图）：每一行的行首都不是一个裸字形，
 * 而是一块**彩色圆角方块**，字形是白色的、缩在方块里。整页的观感有一大半来自这块底色
 * —— 裸字形无论画得多大，在 56dp 的行高里都显得轻、显得"扁"。
 *
 * 这也解释了为什么"把字形画大"解决不了问题：**缺的是那层底**，不是字号。
 *
 * ## 尺寸（从截图量的比例，不是随手定的）
 *
 * | 量 | 值 | 依据 |
 * |---|---|---|
 * | 方块边长 | 30dp | 行最小高 56dp（Miuix `BasicComponent` 写死），方块占 54% |
 * | 圆角 | 9dp | 30% —— 截图里小米的方块就是超椭圆观感 |
 * | 字形边长 | 18dp | 方块的 60% |
 *
 * 小米截图里字形/方块 ≈ 0.55，这里取 0.60：30dp 的方块比小米的 40dp 小一圈，
 * 比例跟着放大一点，否则字形缩到 16dp 反而看不清笔画。
 *
 * ## 为什么底色是超椭圆而不是 `RoundedCornerShape`
 *
 * 小米那套方块的角是**连续曲率**的（超椭圆/squircle），不是圆弧。
 * `miuix-squircle` 本来就是本工程的依赖（`Card` 的圆角走的就是它），
 * 所以直接复用 [squircleBackground]：Android 13 以下没有 runtime shader 时
 * 它会自己退回 `RoundedCornerShape`，不会画不出来。
 *
 * ## 配色：小米的色相，但把明度收了一点
 *
 * [SettingsPlateColors] 取的是小米那一页的色相（蓝/绿/橙/红/紫/灰）。
 * 唯一**故意**偏离参考物的是明度：小米原色的绿（≈`#35C759`）与橙（≈`#FF9F0A`）
 * 配白字形的对比度只有 ≈2.0:1，方块本身又是纯装饰（语义在行标题里），
 * 白字形糊在亮底色上只会更难认。所以六个色都取到**白字对比度 ≥3:1**
 * ——这条不是审美，是 `SettingsIconPlateTest` 会逐个数算并拦住的硬指标。
 *
 * 深浅色模式共用一套：小米也是这么做的，彩色方块在深色底上本来就是亮点。
 */
internal object SettingsPlate {

    /** 方块边长。 */
    val Size = 30.dp

    /** 方块圆角半径（超椭圆的"半径"，30% 边长）。 */
    val Radius = 9.dp

    /** 方块内字形的边长（方块的 60%）。 */
    val Glyph = 18.dp

    /** 字形颜色。小米的方块里就是纯白，与主题色无关。 */
    val GlyphTint = Color.White
}

/**
 * 行首图标块的配色。
 *
 * 色相跟着小米设置页走（WLAN 蓝、移动网络绿、显示与亮度橙、通知红、
 * 个性化紫、更多设置灰），明度按 [SettingsPlate] 的说明往回收。
 *
 * ⚠️ 新增颜色必须同时满足两件事，否则 `SettingsIconPlateTest` 会失败：
 * 一是与白色的对比度 ≥3:1；二是这一页里**不能有两个 (字形, 底色) 完全相同的行**
 * （并排出现两个一模一样的块，会让人以为它们是同一个设置）。
 */
internal object SettingsPlateColors {
    val blue = Color(0xFF3182FF)
    val green = Color(0xFF1B9C48)
    val orange = Color(0xFFD97500)
    val red = Color(0xFFF2453D)
    val purple = Color(0xFF7A5AF8)
    val gray = Color(0xFF8A9099)
}

/**
 * 画一块行首图标。
 *
 * `contentDescription = null`：这一块是**装饰**，行的语义由标题/副标题承担，
 * 给方块再加一份描述只会让 TalkBack 把同一件事念两遍。
 */
@Composable
internal fun SettingsIconPlate(
    icon: Painter,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(SettingsPlate.Size)
            .squircleBackground(color = color, cornerRadius = SettingsPlate.Radius),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            modifier = Modifier.size(SettingsPlate.Glyph),
            tint = SettingsPlate.GlyphTint,
        )
    }
}
