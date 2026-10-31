package com.zhizhu.zhicode.compose.ui.pages

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

/**
 * 麻薯（Mochi）对照陈列：在**同一屏**里并排放两套组件，用来验收「视觉对齐 / 比例调教」。
 *
 * ## 为什么需要这一页
 *
 * `theme/MochiSkin.kt` 里的字号 / 圆角 / 行高映射是**量出来的数字**（Miuix 14 槽、
 * Mochi 20 槽，逐个从字节码的 `bipush` 实参读出）。但数字对不对只能靠并排看：
 * 同一段文字用 17sp 还是 16sp，单独看都合理，放一行里差 1sp 就看得出来；
 * 同样 14dp 圆角，普通圆弧和 G2 连续圆角在视觉上并不等价。
 *
 * 所以这一节不是"组件演示"，是**调教结果的验收台**：
 * - 每一组左右同容器、同高、同底色；
 * - 每行标出它实际用的字号 / 高度，改完立刻能对照；
 * - 字阶那几行把「期望值 vs 运行时实测值」并排打出来，映射写错会当场露出来。
 *
 * ## 边界：这里不替换任何产品界面
 *
 * 本节只挂在 `UiDebugPage` 里。产品路径（聊天 / 终端 / 文件 / 设置）仍全部走 Miuix；
 * 真正的替换按页面逐个迁移，每迁一个就在本节加一组对照 —— 不允许"一次全换再回来看"。
 */
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

/**
 * 一格对照：固定 [MochiMetrics.rowHeight] 高的内容区 + 下面一行说明。
 *
 * 说明文字**必须**在内容区之外：放进去会把行撑高，左右两格就不再同高了，
 * 那样"行高对齐"这条就白测了。
 */
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

/**
 * 色值对照行：`名称  Miuix=#xxxxxx  Mochi=#xxxxxx  ✓/✗`。
 *
 * 颜色"看起来差不多"是最不可靠的验收方式 —— 两列并排时人眼能容忍相当大的色差，
 * 而组件一混排立刻显脏。所以直接打 hex，相等就是相等。
 */
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
