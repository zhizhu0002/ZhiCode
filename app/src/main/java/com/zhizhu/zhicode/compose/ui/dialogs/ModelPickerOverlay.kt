package com.zhizhu.zhicode.compose.ui.dialogs

import com.zhizhu.zhicode.compose.theme.ZhiTextScale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhizhu.zhicode.compose.model.ModelOption
import com.zhizhu.zhicode.compose.model.ModelPickerState
import com.zhizhu.zhicode.compose.theme.ZhiColors
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.ZhiIconButton
import com.zhizhu.zhicode.compose.ui.ZhiTextField
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/**
 * 模型选择面板。
 *
 * 对应原版 `showModelPanel()`：标题带当前配置名、状态行、异步填充的模型列表、
 * 手动输入框、底部「管理 API / 使用模型」。
 *
 * ## 为什么列表是懒的
 *
 * 目录最多 250 项（引擎侧 `MAX_MODELS` 上限）。虽然不算多，但把这些行全部即时组合
 * 进弹窗不如用 [LazyColumn]；同时给一个 [heightIn] 上限，否则长列表会把弹窗顶出屏幕，
 * 底部的「使用模型」按钮就点不到了。
 *
 * ## 列表为空不等于失败
 *
 * [ModelPickerState.status] 已经区分了「加载中 / 获取到 N 个 / 未返回 / 具体错误」，
 * 所以这里只负责把它显示出来；出现空列表时旁边的输入框就是兜底路径，
 * 这不是"错误态"而是"另一种用法"。
 */
@Composable
fun ModelPickerOverlay(
    picker: ModelPickerState?,
    onDismiss: () -> Unit,
    onQueryChange: (String) -> Unit,
    onUse: (String) -> Unit,
    onOpenApiConfig: () -> Unit,
) {
    OverlayDialog(
        show = picker != null,
        onDismissRequest = onDismiss,
        largeScreen = true,
        maxWidth = 560.dp,
        outsideMargin = DialogWideOutsideMargin,
        insideMargin = DialogWideInsideMargin,
    ) {
        val current = picker ?: return@OverlayDialog
        ModelPickerBody(
            picker = current,
            onQueryChange = onQueryChange,
            onUse = onUse,
            onOpenApiConfig = onOpenApiConfig,
        )
    }
}

@Composable
private fun ModelPickerBody(
    picker: ModelPickerState,
    onQueryChange: (String) -> Unit,
    onUse: (String) -> Unit,
    onOpenApiConfig: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme

    DialogShell(
        title = "选择模型 · ${picker.profileName}",
        groupBody = true,
        actions = {
            SecondaryButton(text = "管理 API", onClick = onOpenApiConfig)
            PrimaryButton(
                text = "使用模型",
                enabled = picker.query.isNotBlank(),
                onClick = { onUse(picker.query) },
                modifier = Modifier.padding(start = 8.dp),
            )
        },
    ) {
        Text(
            text = picker.status,
            color = if (picker.models.isEmpty() && !picker.loading) {
                scheme.onSurfaceVariantSummary
            } else {
                scheme.primary
            },
            fontSize = ZhiTextScale.Footnote,
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        )

        if (picker.models.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                cornerRadius = ZhiRadius.card,
                insideMargin = PaddingValues(0.dp),
                colors = CardDefaults.defaultColors(
                    color = scheme.surfaceContainerHigh,
                    contentColor = scheme.onBackground,
                ),
                pressFeedbackType = PressFeedbackType.None,
            ) {
                LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                    items(picker.models, key = { it.id }) { model ->
                        ModelRow(
                            option = model,
                            selected = model.id == picker.currentModel,
                            onPick = { onQueryChange(model.id) },
                        )
                    }
                }
            }
        }

        ZhiTextField(
            value = picker.query,
            onValueChange = onQueryChange,
            label = "模型名",
            useLabelAsPlaceholder = true,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = "点列表里的模型会填进上面的框；再点「使用模型」才生效。" +
                "任务正在运行时切换，会在下一轮完整请求生效。",
            color = scheme.onSurfaceVariantSummary,
            fontSize = ZhiTextScale.Footnote,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
    }
}

/**
 * 目录里的一行。
 *
 * `displayName` 与 `id` 相同时只显示一次，否则每行会重复两遍同一个名字
 * （服务端常常不给 display_name，那种情况下它会被回落成 id）。
 */
@Composable
private fun ModelRow(
    option: ModelOption,
    selected: Boolean,
    onPick: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    val duplicated = option.displayName == option.id
    BasicComponent(
        title = option.displayName,
        titleColor = BasicComponentDefaults.titleColor(
            color = if (selected) scheme.primary else scheme.onBackground,
        ),
        summary = if (duplicated) null else option.id,
        summaryColor = BasicComponentDefaults.summaryColor(color = scheme.onSurfaceVariantSummary),
        startAction = {
            // 选中标记跟 Miuix 下拉列表一致：Check 图标 + 它自己的尺寸常量。
            // 不用 Checkbox（固定 26dp 且是圆的，配 11~13sp 行文字偏大），
            // 理由详见 Dialogs.kt 里的同一处注释。
            if (selected) {
                Icon(
                    imageVector = MiuixIcons.Basic.Check,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(DropdownDefaults.CheckIconSize),
                )
            }
        },
        onClick = onPick,
        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}
