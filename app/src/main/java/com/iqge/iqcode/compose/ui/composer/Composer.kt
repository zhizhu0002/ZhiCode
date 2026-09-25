package com.iqge.iqcode.compose.ui.composer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iqge.iqcode.compose.theme.IqRadius
import com.iqge.iqcode.compose.model.Attachment
import com.iqge.iqcode.compose.model.SlashCommand
import com.iqge.iqcode.compose.model.WorkspaceUiState
import com.iqge.iqcode.compose.ui.Glass
import com.iqge.iqcode.compose.theme.IqColors
import com.iqge.iqcode.compose.ui.IqFilledIconButton
import com.iqge.iqcode.compose.ui.IqIconButton
import com.iqge.iqcode.compose.ui.IqIcons
import com.iqge.iqcode.compose.ui.IqMotion
import com.iqge.iqcode.compose.ui.IqSmallPill
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 输入器，对应原版 buildComposer()：
 * 斜杠面板 / 附件条 / 多行输入 / 三个 chip + 发送与停止。
 *
 * 外观做成**悬浮输入条**：整个输入器是一块带阴影的圆角面板，浮在对话之上
 * （不再是贴底的普通卡片）。相比原版把 `minLines` 收到 1、页脚压到 34dp，
 * 整体更矮更紧凑。
 */
@Composable
fun Composer(
    state: WorkspaceUiState,
    wide: Boolean,
    glass: Glass,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onAttach: () -> Unit,
    onRemoveAttachment: (Attachment) -> Unit,
    onPermissionChip: () -> Unit,
    onEffortChip: () -> Unit,
    onModelChip: () -> Unit,
    onPickSlash: (SlashCommand) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MiuixTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = if (wide) 24.dp else 12.dp,
                end = if (wide) 24.dp else 12.dp,
                top = 4.dp,
                bottom = 10.dp,
            ),
    ) {

        // 斜杠面板展开/收起走动画
        AnimatedVisibility(
            visible = state.slashQuery != null && state.slashMatches.isNotEmpty(),
            enter = expandVertically(
                animationSpec = tween(IqMotion.EXPAND, easing = EaseOutCubic),
                expandFrom = Alignment.Bottom,
            ) + fadeIn(tween(IqMotion.FAST)),
            exit = shrinkVertically(
                animationSpec = tween(IqMotion.FAST),
                shrinkTowards = Alignment.Bottom,
            ) + fadeOut(tween(IqMotion.FAST)),
        ) {
            SlashPalette(matches = state.slashMatches, onPick = onPickSlash)
        }

        // 输入框 + 发送键**并排**：输入箱在左（weight 1f），方角发送键在右。
        // 两者都走 Miuix：箱子用 FloatingToolbar（圆角 + 阴影由组件库负责），
        // 按键用转发到 IconButton 的 IqFilledIconButton(square = true)。
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
        FloatingToolbar(
            modifier = Modifier.weight(1f)
                .then(glass.blur(Modifier, RoundedCornerShape(IqRadius.floating), radius = 24f)),
            color = glass.surfaceColor(scheme.surfaceContainer),
            cornerRadius = IqRadius.floating,
            outSidePadding = PaddingValues(0.dp),
            shadowElevation = 12.dp,
            showDivider = false,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(
                        animationSpec = tween(IqMotion.EXPAND, easing = FastOutSlowInEasing),
                    )
                    .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 5.dp),
            ) {
            // 附件条随附件增减平滑展开/收起
            AnimatedVisibility(
                visible = state.attachments.isNotEmpty(),
                enter = expandVertically(tween(IqMotion.EXPAND)) + fadeIn(tween(IqMotion.FAST)),
                exit = shrinkVertically(tween(IqMotion.FAST)) + fadeOut(tween(IqMotion.FAST)),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    state.attachments.take(4).forEach { attachment ->
                        AttachmentChip(attachment) { onRemoveAttachment(attachment) }
                    }
                }
            }

            // 输入框 + 暂停键 + 发送键**同一行**：
            // 输入框本身不画容器（透明），方角外框由外层 FloatingToolbar 负责，
            // 这样不会出现"盒中盒"；行高下限与按钮同高（32dp），多行时按钮贴底。
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                TextField(
                    value = state.composerText,
                    onValueChange = onTextChange,
                    label = "描述任务或向 IQ 提问",
                    useLabelAsPlaceholder = true,
                    // 透明容器 + 透明描边：只留外层方角框
                    colors = TextFieldDefaults.textFieldColors(
                        backgroundColor = Color.Transparent,
                        labelColor = scheme.onSurfaceVariantSummary,
                        borderColor = Color.Transparent,
                    ),
                    // 收紧 Miuix TextField 的内部留白，让输入区更矮
                    insideMargin = DpSize(6.dp, 1.dp),
                    // 常规字重的正文样式：Miuix 主题默认文字样式偏粗，会显得比原版重
                    textStyle = MiuixTheme.textStyles.main.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                    ),
                    // 比原版的 minLines = 2 更矮，只占一行起，随内容长高
                    minLines = 1,
                    maxLines = 5,
                    modifier = Modifier.weight(1f).heightIn(min = 36.dp),
                )

                // 停止键在任务运行时滑入，位置就在发送键左边
                AnimatedVisibility(
                    visible = state.composerBusy,
                    enter = fadeIn(tween(IqMotion.FAST)) + expandHorizontally(tween(IqMotion.EXPAND)) +
                        scaleIn(initialScale = 0.7f, animationSpec = tween(IqMotion.EXPAND)),
                    exit = fadeOut(tween(IqMotion.FAST)) + shrinkHorizontally(tween(IqMotion.FAST)) +
                        scaleOut(targetScale = 0.7f, animationSpec = tween(IqMotion.FAST)),
                ) {
                    IqFilledIconButton(
                        icon = IqIcons.stop,
                        description = "立即停止当前任务",
                        onClick = onStop,
                        // 走主题的 error 语义色而不是写死的红：浅色模式会自动换成暗红，
                        // 前景也必须是 onError，否则浅色下红底白字对比度不足。
                        containerColor = MiuixTheme.colorScheme.error,
                        contentColor = MiuixTheme.colorScheme.onError,
                        iconSize = 14.dp,
                        size = 36.dp,
                        square = true,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }

                // 方角发送键；图标库没有纯右箭头，用文字字形 `→`（原版发送键也是文字字形）
                IqFilledIconButton(
                    description = "发送消息",
                    glyph = "→",
                    glyphSize = 17.sp,
                    onClick = onSend,
                    containerColor = scheme.primary,
                    enabled = state.composerText.isNotBlank(),
                    size = 36.dp,
                    square = true,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().height(34.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IqIconButton(
                    icon = IqIcons.attach,
                    description = "添加附件",
                    onClick = onAttach,
                    iconSize = 18.dp,
                )

                IqSmallPill(
                    label = state.permissionMode.label,
                    onClick = onPermissionChip,
                    modifier = Modifier.weight(1f),
                    highlighted = state.permissionMode != com.iqge.iqcode.compose.model.PermissionMode.ASK,
                )
                IqSmallPill(
                    label = "推理：${state.effort.label}",
                    onClick = onEffortChip,
                    modifier = Modifier.weight(1f),
                )
                IqSmallPill(
                    // 原版是 shorten(profileName + " · " + model, wide?26:16)（MainActivity.java:1393）
                    label = shorten(
                        state.profileName + " · " + state.modelLabel,
                        if (wide) 26 else 16,
                    ),
                    onClick = onModelChip,
                    modifier = Modifier.weight(1f),
                )
            } // 面板内页脚 Row（+ 与三个 chip）
            } // 悬浮面板内容 Column
        } // FloatingToolbar
        } // 输入框 + 停止/发送键 Row
    }
}

@Composable
private fun AttachmentChip(attachment: Attachment, onRemove: () -> Unit) {
    val scheme = MiuixTheme.colorScheme
    Card(
        modifier = Modifier.height(26.dp),
        cornerRadius = IqRadius.inner,
        insideMargin = PaddingValues(start = 8.dp, end = 2.dp),
        colors = CardDefaults.defaultColors(
            color = scheme.surfaceContainerHighest,
            contentColor = scheme.onSurface,
        ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (attachment.isImage) IqIcons.floatingBall else IqIcons.file,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = attachment.label,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(112.dp).padding(start = 4.dp),
            )
            // 直接用 IqIconButton 的 compact 参数压成 22dp 点击区，
            // 不再外面套一个固定尺寸的 Box（那样只有内层图标可点，命中区也不对）
            IqIconButton(
                icon = IqIcons.close,
                description = "移除附件",
                onClick = onRemove,
                iconSize = 12.dp,
                compact = 22.dp,
            )
        }
    }
}

private fun shorten(value: String, max: Int): String =
    if (value.length <= max) value else value.substring(0, max - 1) + "…"
