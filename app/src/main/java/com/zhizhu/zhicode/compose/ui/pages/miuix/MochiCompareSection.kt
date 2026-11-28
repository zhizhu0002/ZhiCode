package com.zhizhu.zhicode.compose.ui.pages.miuix

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.theme.MochiMetrics
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import com.zhizhu.zhicode.compose.ui.effects.ZhiSquircle
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme
import ui.feice.mochi.basic.MochiButton
import ui.feice.mochi.basic.MochiButtonStyle
import ui.feice.mochi.basic.MochiSwitch
import ui.feice.mochi.theme.MochiTheme

@Composable
internal fun MochiCompareSection() {
    val miuix = MiuixTheme.colorScheme
    val mochi = MochiTheme.colorScheme
    var toggle by remember { mutableStateOf(true) }

    DebugSection(
        title = "麻薯（Mochi）对照",
        subtitle = "左 = Miuix，右 = Mochi；字阶与圆角按 theme/MochiSkin.kt 的映射对齐",
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Group("列表行高 · 两套同高 ${MochiMetrics.rowHeight.value.toInt()}dp")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CompareCell(
                    modifier = Modifier.weight(1f),
                    caption = "Miuix · body1 16sp",
                    scheme = miuix.surfaceContainerHigh,
                ) {
                    MiuixText(
                        text = "行内容 Aa 123",
                        fontSize = MiuixTheme.textStyles.body1.fontSize,
                        color = miuix.onSurfaceVariantSummary,
                    )
                }

                CompareCell(
                    modifier = Modifier.weight(1f),
                    caption = "Mochi · bodyLarge 16/24",
                    scheme = mochi.surfaceContainerHigh,
                ) {
                    MiuixText(
                        text = "行内容 Aa 123",
                        fontSize = MochiTheme.textStyles.bodyLarge.fontSize,
                        lineHeight = MochiTheme.textStyles.bodyLarge.lineHeight,
                        color = miuix.onSurfaceVariantSummary,
                    )
                }
            }

            Group("字阶 · Mochi 被覆盖的 4 个槽位（期望 vs 实测）")
            val textStyles = MochiTheme.textStyles
            MetricRow("titleLarge", 20, textStyles.titleLarge.fontSize.value.toInt(), miuix.onSurfaceVariantSummary)
            MetricRow("titleMedium", 18, textStyles.titleMedium.fontSize.value.toInt(), miuix.onSurfaceVariantSummary)
            MetricRow("labelLarge", 16, textStyles.labelLarge.fontSize.value.toInt(), miuix.onSurfaceVariantSummary)
            MetricRow("labelMedium", 13, textStyles.labelMedium.fontSize.value.toInt(), miuix.onSurfaceVariantSummary)

            Group("圆角 · 同一个 14dp 的两种画法")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ShapeCell(
                    modifier = Modifier.weight(1f),
                    caption = "ZhiSquircle(28dp) · G2",
                    shape = ZhiSquircle(ZhiRadius.card),
                    fill = miuix.primary.copy(alpha = 0.22f),
                    captionColor = miuix.onSurfaceVariantSummary,
                )
                ShapeCell(
                    modifier = Modifier.weight(1f),
                    caption = "Mochi · shapes.extraLarge",
                    shape = MochiTheme.shapes.extraLarge,
                    fill = mochi.primary.copy(alpha = 0.22f),
                    captionColor = miuix.onSurfaceVariantSummary,
                )
            }

            Group("强调色 · 两边必须是同一个值（色差会直接毁掉并排观感）")
            MetricRow("primary", hexOf(miuix.primary), hexOf(mochi.primary), ok = miuix.primary == mochi.primary)
            MetricRow(
                "onSurfaceVariant",
                hexOf(miuix.onSurfaceVariantSummary),
                hexOf(mochi.onSurfaceVariant),
                ok = miuix.onSurfaceVariantSummary == mochi.onSurfaceVariant,
            )
            MetricRow(
                "surfaceContainer",
                hexOf(miuix.surfaceContainer),
                hexOf(mochi.surfaceContainer),
                ok = miuix.surfaceContainer == mochi.surfaceContainer,
            )

            Group("控件 · 按钮与开关")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MochiButton(
                    onClick = {},
                    style = MochiButtonStyle.Filled,
                ) {
                    MiuixText(
                        text = "Mochi 按钮",
                        fontSize = MochiTheme.textStyles.labelLarge.fontSize,
                        // ⚠️ 不能写 Color.White：Mochi 的 primary 若是浅色，白字对比度只有
                        // 1.9:1（第一版就是这么错的）。文字色必须跟随 onPrimary。
                        color = mochi.onPrimary,
                    )
                }
                MochiSwitch(checked = toggle, onCheckedChange = { toggle = it })
            }
        }
    }
}

@Composable
private fun Group(text: String) {
    MiuixText(
        text = text,
        fontSize = MiuixTheme.textStyles.footnote2.fontSize,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

/** 一格对照：固定 [MochiMetrics.rowHeight] 高的内容区 + 下面一行说明。 */
@Composable
private fun CompareCell(
    modifier: Modifier,
    caption: String,
    scheme: Color,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(MochiMetrics.rowHeight)
                .background(color = scheme, shape = RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(modifier = Modifier.padding(horizontal = 12.dp)) { content() }
        }
        MiuixText(
            text = caption,
            fontSize = MiuixTheme.textStyles.footnote2.fontSize,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun MetricRow(name: String, expected: Int, actual: Int, color: Color) {
    val ok = expected == actual
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        MiuixText(
            text = name,
            fontSize = MiuixTheme.textStyles.footnote2.fontSize,
            color = color,
            modifier = Modifier.width(100.dp),
        )
        MiuixText(
            text = if (ok) "期望 ${expected}sp · 实测 ${actual}sp ✓" else "期望 ${expected}sp · 实测 ${actual}sp ✗",
            fontSize = MiuixTheme.textStyles.footnote2.fontSize,
            color = if (ok) color else MiuixTheme.colorScheme.error,
        )
    }
}

/** 色值对照行：`名称  Miuix=#xxxxxx  Mochi=#xxxxxx  ✓/✗`。 */
@Composable
private fun MetricRow(name: String, miuixHex: String, mochiHex: String, ok: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        MiuixText(
            text = name,
            fontSize = MiuixTheme.textStyles.footnote2.fontSize,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.width(100.dp),
        )
        MiuixText(
            text = "$miuixHex → $mochiHex ${if (ok) "✓" else "✗"}",
            fontSize = MiuixTheme.textStyles.footnote2.fontSize,
            color = if (ok) MiuixTheme.colorScheme.onSurfaceVariantSummary else MiuixTheme.colorScheme.error,
        )
    }
}

/** `#RRGGBB` 形式的 ARGB 十六进制（忽略 alpha：本页对照的都是不透明语义色）。 */
private fun hexOf(color: Color): String {
    val argb = color.toArgb()
    return String.format("#%06X", 0xFFFFFF and argb)
}

@Composable
private fun ShapeCell(modifier: Modifier, caption: String, shape: Shape, fill: Color, captionColor: Color) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(color = fill, shape = shape),
        )
        MiuixText(
            text = caption,
            fontSize = MiuixTheme.textStyles.footnote2.fontSize,
            color = captionColor,
        )
    }
}
