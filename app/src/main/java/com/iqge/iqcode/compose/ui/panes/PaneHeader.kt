package com.iqge.iqcode.compose.ui.panes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iqge.iqcode.compose.ui.IqHorizontalDivider
import com.iqge.iqcode.compose.ui.IqIconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 三个工作区面板共用的标题栏，对应原版 paneHeader()。
 *
 * ## 动作为什么是图标按钮而不是文字按钮
 *
 * 这里以前用 Miuix `TextButton` 配 `minHeight = 26.dp`，结果按钮被压扁、文字被裁。
 * 原因是 `ButtonDefaults` 写死 `MinWidth = 58dp` / `MinHeight = 40dp`
 * （从 0.9.4 字节码读出：`bipush 58` / `bipush 40`），
 * 强行传小值只会把内部布局压坏 —— 34dp 高的标题行根本容不下文字按钮。
 *
 * 所以动作改成 [IqIconButton] + `compact`：这是本项目里已验证能安全压到 30dp 的做法
 * （工具行就是 25dp），并且图标比中文两字标签更省横向空间。
 *
 * [onAction] 为空时**不显示按钮**：可点却什么都不发生比没有按钮更糟。
 */
@Composable
fun PaneHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionIcon: ImageVector? = null,
    actionDescription: String? = null,
    onAction: (() -> Unit)? = null,
    subtitle: String? = null,
) {
    val scheme = MiuixTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(36.dp).padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = scheme.onBackground,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = scheme.onSurfaceVariantSummary,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            // 把动作按钮推到行尾
            Row(modifier = Modifier.weight(1f)) {}

            if (actionIcon != null && onAction != null) {
                IqIconButton(
                    icon = actionIcon,
                    description = actionDescription ?: title,
                    onClick = onAction,
                    iconSize = 16.dp,
                    compact = 30.dp,
                )
            }
        }
        IqHorizontalDivider()
    }
}
