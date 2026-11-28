package com.zhizhu.zhicode.compose.ui.panes

import com.zhizhu.zhicode.compose.theme.MochiBaseline
import com.zhizhu.zhicode.compose.theme.ZhiSpace
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhizhu.zhicode.compose.ui.ZhiHorizontalDivider
import com.zhizhu.zhicode.compose.ui.ZhiIconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun PaneHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionIcon: Painter? = null,
    actionDescription: String? = null,
    onAction: (() -> Unit)? = null,
    subtitle: String? = null,
    /** 可选的最左侧动作（终端会话抽屉入口）。 */
    leadingAction: (@Composable RowScope.() -> Unit)? = null,
    /** 行尾的多个动作。 */
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val scheme = MiuixTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            // 面板头不跟列表行走同一个 token：列表行是 52dp（Mochi iOS 26 基准），
            // 而面板头是**栏**，栏高应受顶栏 48dp 的量级约束，否则一屏里两条横栏
            // 高度差 4dp 反而更显眼。这里取 Mochi 的 controlHeight。
            modifier = Modifier
                .fillMaxWidth()
                .height(MochiBaseline.controlHeight)
                .padding(horizontal = ZhiSpace.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingAction != null) {
                Row(verticalAlignment = Alignment.CenterVertically, content = leadingAction)
            }
            Text(
                text = title,
                color = scheme.onBackground,
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                fontWeight = FontWeight.SemiBold,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = scheme.onSurfaceVariantSummary,
                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                    modifier = Modifier.padding(start = ZhiSpace.s),
                )
            }
            // 把动作按钮推到行尾
            Row(modifier = Modifier.weight(1f)) {}

            if (actions != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions,
                )
            }

            if (actionIcon != null && onAction != null) {
                ZhiIconButton(
                    icon = actionIcon,
                    description = actionDescription ?: title,
                    onClick = onAction,
                    iconSize = 16.dp,
                    compact = 30.dp,
                )
            }
        }
        ZhiHorizontalDivider()
    }
}
