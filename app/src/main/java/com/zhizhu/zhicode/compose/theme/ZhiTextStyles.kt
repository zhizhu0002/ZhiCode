package com.zhizhu.zhicode.compose.theme

import androidx.compose.ui.text.TextStyle
import top.yukonga.miuix.kmp.theme.TextStyles
import top.yukonga.miuix.kmp.theme.defaultTextStyles
import androidx.compose.ui.unit.sp

/**
 * 应用的 Miuix 文字主题。
 *
 * 这里仍然使用 Miuix 的官方 [TextStyles] 槽位，只把字号收成适合手机的信息密度；
 * 调用点不再依赖额外的字号 token，而是直接读取 `MiuixTheme.textStyles.*`。
 * 这样官方组件（BasicComponent、TextField、Button、Preference 等）和普通正文
 * 共享同一套 Miuix 主题接线，并且保留了恢复到 Miuix 默认字阶的单一切换点。
 */
fun zhiTextStyles(): TextStyles {
    val base = defaultTextStyles()
    fun TextStyle.sized(size: Float) = copy(fontSize = size.sp)
    return base.copy(
        // 标题：24 / 19 / 16 / 15sp
        title1 = base.title1.sized(24f),
        title2 = base.title2.sized(19f),
        title3 = base.title3.sized(16f),
        title4 = base.title4.sized(15f),
        // Miuix 的正文槽位保持原有层级，只收成手机紧凑档：14 / 13 / 12 / 11 / 10sp。
        main = base.main.sized(14f),
        paragraph = base.paragraph.sized(14f),
        button = base.button.sized(14f),
        headline1 = base.headline1.sized(14f),
        body1 = base.body1.sized(13f),
        headline2 = base.headline2.sized(13f),
        body2 = base.body2.sized(12f),
        subtitle = base.subtitle.sized(12f),
        footnote1 = base.footnote1.sized(11f),
        footnote2 = base.footnote2.sized(10f),
    )
}
