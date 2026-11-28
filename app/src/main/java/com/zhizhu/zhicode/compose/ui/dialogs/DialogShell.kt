package com.zhizhu.zhicode.compose.ui.dialogs
import com.zhizhu.zhicode.compose.theme.ZhiRadius

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.overlay.OverlayDialog

/** 所有 Miuix [OverlayDialog] 共用的外壳、按钮与紧凑文字样式。 */

/** 弹窗边距（UI 重构 S4：回归官方默认）。 */
// 标准弹窗上下留白收紧一点；横向仍保留官方 12dp，避免手机屏上窗口显得过窄。
internal val DialogWideOutsideMargin = DpSize(12.dp, 8.dp)
internal val DialogWideInsideMargin = DpSize(24.dp, 24.dp)

/** 弹窗宽度档位（UI 重构 S4：全部回归官方 DialogDefaults.MaxWidth = 420dp）。 */
internal object ZhiDialogWidth {
    /** 短确认/单输入弹窗。 */
    val Compact = 360.dp
    val Regular = 420.dp
    val Wide = 420.dp
}

/** 贴底弹窗（Sheet 档）的外边距：横向贴边、纵向正常 —— 窄屏长列表弹窗 */
internal val DialogSheetOutsideMargin = DpSize(0.dp, 8.dp)

@Composable
internal fun ZhiSheetCardColors(): CardColors = CardDefaults.defaultColors(
    color = MiuixTheme.colorScheme.secondaryContainer,
    contentColor = MiuixTheme.colorScheme.onBackground,
)

@Composable
internal fun DialogShell(
    title: String,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    titleAction: (@Composable () -> Unit)? = null,
    header: (@Composable () -> Unit)? = null,
    prompt: (@Composable () -> Unit)? = null,
    /** 把中间内容区包进 Miuix [Card]，用于设置/选项这类需要卡片分组的弹窗。 */
    groupBody: Boolean = false,
    footer: (@Composable () -> Unit)? = null,
    fillBody: Boolean = false,
    actions: @Composable () -> Unit,
    body: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
    ) {
        // 标题行：标题靠左吃剩余宽度，右侧留一个可选动作位（设置页放 × 关闭）
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = titleColor,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Start,
                modifier = Modifier.weight(1f),
            )
            if (titleAction != null) titleAction()
        }

        if (header != null) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { header() }
        }

        if (prompt != null) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { prompt() }
        }

        // The OverlayDialog window uses Miuix `background` as its sheet/dialog
        val middleModifier = Modifier.fillMaxWidth().weight(1f, fill = fillBody)

        val scrollableBody: @Composable () -> Unit = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                body()
            }
        }

        if (!groupBody) {
            Column(modifier = middleModifier.padding(top = 8.dp)) { scrollableBody() }
        } else {
            Surface(
                modifier = middleModifier.padding(top = 10.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)) {
                    scrollableBody()
                }
            }
        }

        if (footer != null) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { footer() }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            content = { actions() },
        )
    }
}

/** 高亮的"确定"类按钮：主色实心，对应参考图的「提交」「批准并开始」「允许」。 */
@Composable
internal fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
    ) {
        Text(text = text)
    }
}

/** 次要按钮：走 M3E 的文字按钮默认配色。 */
@Composable
internal fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
    ) {
        Text(text = text)
    }
}
