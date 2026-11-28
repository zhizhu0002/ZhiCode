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
import com.zhizhu.zhicode.compose.ui.floatingNavSheetInset
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
import com.zhizhu.zhicode.compose.ui.dialogs.ZhiSheetCardColors
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


// ---------------------------------------------------------------- 任务清单

/** 任务清单 sheet 的定高参数（含义与取值理由见 ModelPickerOverlay 的同名常量）。 */
private const val TaskSheetHeightFraction = 0.55f
private val TaskSheetHeightCap = 520.dp
private val TaskSheetHeightFloor = 300.dp

@Composable
fun TaskListOverlay(
    tasks: List<AgentTask>,
    open: Boolean,
    onDismiss: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme

    OverlayBottomSheet(
        show = open,
        onDismissRequest = onDismiss,
        title = "任务清单",
        // 其余参数一律用 Miuix 默认值（同 ModelPickerOverlay：backgroundColor 别动）。
    ) {
        val windowHeight = LocalWindowInfo.current.containerDpSize.height
        /* 为悬浮导航胶囊让位。 */
        val navInset = floatingNavSheetInset()
        val sheetHeight = (
            (windowHeight * TaskSheetHeightFraction).coerceAtMost(TaskSheetHeightCap) - navInset
            ).coerceAtLeast(TaskSheetHeightFloor)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(sheetHeight)
                .padding(bottom = navInset),
        ) {
            val done = tasks.count { it.state == TaskState.DONE }
            Text(
                text = "$done / ${tasks.size} 已完成",
                color = scheme.onSurfaceVariantSummary,
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                modifier = Modifier.padding(bottom = 10.dp),
            )

            /* 清单区装进一张 `Card`（底色走 [ZhiSheetCardColors]），内部再滚动。 */
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = ZhiSheetCardColors(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                ) {
                    // 整块内容可选中：长按选中、拖动扩展选区
                    SelectionContainer {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            tasks.forEachIndexed { index, task ->
                                if (index > 0) {
                                    ZhiHorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TaskStatusIndicator(state = task.state, size = 14.dp)
                                    Text(
                                        text = task.title,
                                        color = if (task.state == TaskState.PENDING) {
                                            scheme.onSurfaceVariantSummary
                                        } else {
                                            scheme.onSurface
                                        },
                                        fontSize = MiuixTheme.textStyles.title4.fontSize,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(start = 8.dp),
                                    )
                                }
                                if (task.detail.isNotBlank()) {
                                    ZhiMarkdown(
                                        source = task.detail,
                                        modifier = Modifier.padding(start = 21.dp, top = 6.dp),
                                        bodyFontSize = 11.5.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            PrimaryButton(
                text = "完成",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        }
    }
}
