package com.zhizhu.zhicode.compose.ui.pages.miuix.overlays

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.selection.SelectionContainer
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import com.zhizhu.zhicode.compose.model.AgentTask
import com.zhizhu.zhicode.compose.model.TaskState
import com.zhizhu.zhicode.compose.ui.chat.TaskStatusIndicator
import androidx.compose.ui.unit.sp
import com.zhizhu.zhicode.compose.model.ChoicePickerState
import com.zhizhu.zhicode.compose.model.PermissionRequest
import com.zhizhu.zhicode.compose.model.PlanApproval
import com.zhizhu.zhicode.compose.model.RiskLevel
import com.zhizhu.zhicode.compose.ui.dialogs.DialogShell
import com.zhizhu.zhicode.compose.ui.dialogs.DialogWideInsideMargin
import com.zhizhu.zhicode.compose.ui.dialogs.DialogWideOutsideMargin
import com.zhizhu.zhicode.compose.ui.dialogs.PrimaryButton
import com.zhizhu.zhicode.compose.ui.dialogs.SecondaryButton
import com.zhizhu.zhicode.compose.ui.dialogs.ZhiDialogWidth
import com.zhizhu.zhicode.compose.theme.ZhiColors
import com.zhizhu.zhicode.compose.ui.ZhiHorizontalDivider
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.ZhiMotion
import com.zhizhu.zhicode.compose.ui.ZhiMarkdown
import com.zhizhu.zhicode.compose.ui.ZhiTextField
import com.zhizhu.zhicode.compose.ui.settings.rememberLastNonNull
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog


// ---------------------------------------------------------------- 工具授权

@Composable
fun PermissionOverlay(
    request: PermissionRequest?,
    onDecision: (requestId: String, allow: Boolean, alwaysAllow: Boolean) -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    // 退场那 250~260ms 里 `request` 已经是 null，内容不能跟着空掉（否则看起来是
    // 弹窗先缩成一张空壳再淡出）—— 见 rememberLastNonNull。
    val shownRequest = rememberLastNonNull(request)
    // 也按 shownRequest 记：按 request 记的话，关闭那一刻「始终允许」会自己弹回去。
    var alwaysAllow by remember(shownRequest) { mutableStateOf(false) }

    OverlayDialog(
        show = request != null,
        onDismissRequest = { request?.let { onDecision(it.id, false, false) } },
        // 居中形态（`largeScreen = true` 是**居中**开关，不是「大屏专用」）。
        largeScreen = true,
        maxWidth = ZhiDialogWidth.Compact,
        outsideMargin = DialogWideOutsideMargin,
        insideMargin = DialogWideInsideMargin,
    ) {
        val req = shownRequest ?: return@OverlayDialog
        DialogShell(
            title = "工具授权",
            groupBody = true,
            actions = {
                SecondaryButton(text = "拒绝", onClick = { onDecision(req.id, false, false) })
                PrimaryButton(
                    text = "允许",
                    onClick = { onDecision(req.id, true, alwaysAllow) },
                    modifier = Modifier.padding(start = 8.dp),
                )
            },
        ) {
            // 工具名 + 说明 + 风险徽标：一个 Miuix BasicComponent 搞定，不再手拼 Row/Column
            BasicComponent(
                title = req.tool,
                titleColor = BasicComponentDefaults.titleColor(color = scheme.onBackground),
                summary = req.subtitle,
                summaryColor = BasicComponentDefaults.summaryColor(
                    color = scheme.onSurfaceVariantSummary,
                ),
                startAction = {
                    Icon(
                        painter = ZhiIcons.tool(req.tool),
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                },
                endActions = {
                    if (req.riskLevel == RiskLevel.HIGH) {
                        // Miuix Badge：容器/文字色走主题，浅色模式自动变浅红底 + 暗红字。
                        Badge(
                            containerColor = scheme.errorContainer,
                            contentColor = scheme.error,
                        ) {
                            Text(text = "高风险", fontSize = MiuixTheme.textStyles.footnote1.fontSize)
                        }
                    }
                },
                insideMargin = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
            )

            // 详情：Card 提供比弹窗底色更亮的容器
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
                    .heightIn(min = 44.dp, max = 180.dp),
                cornerRadius = ZhiRadius.inner,
                insideMargin = PaddingValues(10.dp),
                colors = CardDefaults.defaultColors(
                    color = scheme.surfaceContainerHigh,
                    contentColor = scheme.onSurface,
                ),
            ) {
                Text(
                    text = req.detail,
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Switch(checked = alwaysAllow, onCheckedChange = { alwaysAllow = it })
                Text(
                    text = "始终允许",
                    color = scheme.onSurfaceVariantSummary,
                    fontSize = MiuixTheme.textStyles.body1.fontSize,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}
