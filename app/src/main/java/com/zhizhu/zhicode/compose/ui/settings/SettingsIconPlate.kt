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
import com.zhizhu.zhicode.compose.theme.ZhiColors

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

internal object SettingsPlateColors {
    val blue = ZhiColors.plateBlue
    val green = ZhiColors.plateGreen
    val orange = ZhiColors.plateOrange
    val red = ZhiColors.plateRed
    val purple = ZhiColors.platePurple
    val gray = ZhiColors.plateGray
}

/** 画一块行首图标。 */
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
