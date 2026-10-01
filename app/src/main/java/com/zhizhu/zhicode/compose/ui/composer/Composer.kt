package com.zhizhu.zhicode.compose.ui.composer

import com.zhizhu.zhicode.compose.theme.ZhiTextScale
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import com.zhizhu.zhicode.compose.model.Attachment
import com.zhizhu.zhicode.compose.model.EffortLevel
import com.zhizhu.zhicode.compose.model.PermissionMode
import com.zhizhu.zhicode.compose.model.SlashCommand
import com.zhizhu.zhicode.compose.model.WorkspaceUiState
import com.zhizhu.zhicode.compose.ui.Glass
import com.zhizhu.zhicode.compose.theme.ZhiColors
import com.zhizhu.zhicode.compose.ui.ZhiFilledIconButton
import com.zhizhu.zhicode.compose.ui.ZhiIconButton
import com.zhizhu.zhicode.compose.ui.ZhiIconDropdownMenu
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.ZhiMenuItem
import com.zhizhu.zhicode.compose.ui.ZhiMotion
import com.zhizhu.zhicode.compose.ui.ZhiSmallPill
import com.zhizhu.zhicode.compose.ui.ZhiTextDropdownChip
import com.zhizhu.zhicode.compose.ui.ZhiTextField
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
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
    onPickSlash: (SlashCommand) -> Unit,
    onRemoveAttachment: (Attachment) -> Unit,
    // ---- `+` 菜单的四个动作（原来只有一个 onAttach 直接弹相册）----
    onAttachFile: () -> Unit,
    onOpenSkills: () -> Unit,
    onOpenFilesTab: () -> Unit,
    onPickImage: () -> Unit,
    // ---- 页脚三个下拉 ----
    onPermissionSelected: (PermissionMode) -> Unit,
    onEffortSelected: (EffortLevel) -> Unit,
    onModelChip: () -> Unit,
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
                animationSpec = ZhiMotion.sizeSpec,
                expandFrom = Alignment.Bottom,
            ) + fadeIn(ZhiMotion.fadeInSpec),
            exit = shrinkVertically(
                animationSpec = ZhiMotion.sizeSpec,
                shrinkTowards = Alignment.Bottom,
            ) + fadeOut(ZhiMotion.fadeOutSpec),
        ) {
            SlashPalette(matches = state.slashMatches, onPick = onPickSlash)
        }

        // 输入框 + 发送键**并排**：输入箱在左（weight 1f），方角发送键在右。
        // 两者都走 Miuix：箱子用 FloatingToolbar（圆角 + 阴影由组件库负责），
        // 按键用转发到 IconButton 的 ZhiFilledIconButton(square = true)。
        FloatingToolbar(
            modifier = Modifier
                .fillMaxWidth()
                .then(glass.blur(Modifier, RoundedCornerShape(ZhiRadius.floating), radius = 24f)),
            color = glass.surfaceColor(scheme.surfaceContainer),
            cornerRadius = ZhiRadius.floating,
            outSidePadding = PaddingValues(0.dp),
            shadowElevation = 12.dp,
            showDivider = false,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(
                        animationSpec = ZhiMotion.sizeSpec,
                    )
                    .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 5.dp),
            ) {
            // 附件条随附件增减平滑展开/收起
            AnimatedVisibility(
                visible = state.attachments.isNotEmpty(),
                enter = expandVertically(ZhiMotion.sizeSpec) + fadeIn(ZhiMotion.fadeInSpec),
                exit = shrinkVertically(ZhiMotion.sizeSpec) + fadeOut(ZhiMotion.fadeOutSpec),
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


            // 输入行独占一排（参考图）：透明输入框 + 宽 placeholder
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                ZhiTextField(
                    value = state.composerText,
                    onValueChange = onTextChange,
                    label = "描述任务或向智蛛提问",
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
                        fontSize = ZhiTextScale.Subheading,
                        fontWeight = FontWeight.Normal,
                    ),
                    // 比原版的 minLines = 2 更矮，只占一行起，随内容长高
                    minLines = 1,
                    maxLines = 5,
                    modifier = Modifier.weight(1f).heightIn(min = 36.dp),
                )


            }

            // 底排（参考图）：+ 圆钮 | 权限/推理下拉 | 弹性 | 模型 pill | 停止 | 发送
            Row(
                modifier = Modifier.fillMaxWidth().height(40.dp).padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(36.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // `+` 不再直接弹相册：改成 Miuix 的动作菜单（OverlayIconDropdownMenu）。
                //
                // ⚠️ 它必须位于 Miuix `Scaffold` 内 —— Overlay 系列靠 Scaffold 提供的
                // MiuixPopupHost 渲染弹出内容。本工程根部就是 Scaffold。
                // ⚠️ 这里的 summary **必须短**（几个字，不要写成句子）。
                // 弹出面板的宽度是**内容撑出来的**：Miuix 用
                // `DropdownDefaults.MaxItemTextWidth`（实测 216dp）限行内容宽，
                // 再加左右各 `InsideHorizontalPadding`（20dp），所以一条长文案
                // 就能把面板撑到约 256dp —— 在这台 411dp 宽的设备上是 62%。
                // 面板本身没有宽度参数可调（`OverlayIconDropdownMenu` 的
                // `minWidth` 是给触发按钮的），所以**缩短文案是唯一不偏离库默认的收窄办法**。
                ZhiIconDropdownMenu(
                    items = listOf(
                        ZhiMenuItem(
                            text = "附加项目文件",
                            summary = "搜索并附加",
                            icon = ZhiIcons.file,
                            onClick = onAttachFile,
                        ),
                        ZhiMenuItem(
                            text = "Skill 管理器",
                            summary = "查看与编辑",
                            icon = ZhiIcons.skill,
                            onClick = onOpenSkills,
                        ),
                        ZhiMenuItem(
                            text = "打开文件工作区",
                            summary = "浏览与查看",
                            icon = ZhiIcons.files,
                            onClick = onOpenFilesTab,
                        ),
                        ZhiMenuItem(
                            text = "上传照片",
                            summary = "作为视觉输入",
                            icon = ZhiIcons.floatingBall,
                            onClick = onPickImage,
                        ),
                    ),
                ) {
                    Icon(
                        imageVector = ZhiIcons.attach,
                        contentDescription = "添加附件",
                        tint = scheme.onBackgroundVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }

                // 两个下拉 chip 的**外观与旁边的模型药丸完全一致**（10sp / 28dp /
                // 透明底 + 折叠箭头）。别换成 Miuix 的 OverlayDropdownPreference：
                // 那是 16sp / 40dp 的"设置行"，放进这条 34dp 的页脚会撑高整行、
                // 把标签挤到折行（实测过一次）。
                //
                // 三者共用 OverlayIconDropdownMenu：它的 content 可以是任意可组合内容，
                // 所以 `+` 放图标、这两个放"文字 + 箭头"，弹出菜单是同一套原生样式。
                ZhiTextDropdownChip(
                    label = state.permissionMode.label,
                    items = PermissionMode.entries.map { mode ->
                        ZhiMenuItem(
                            text = mode.label,
                            summary = mode.detail,
                            selected = mode == state.permissionMode,
                            onClick = { onPermissionSelected(mode) },
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
                ZhiTextDropdownChip(
                    label = "推理：${state.effort.label}",
                    items = EffortLevel.entries.map { level ->
                        ZhiMenuItem(
                            text = level.label,
                            selected = level == state.effort,
                            onClick = { onEffortSelected(level) },
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
                // 模型这一项**不是**下拉：它要异步拉目录、还要写回配置记录，
                // 表达不了"固定几项"，仍走原有的 ModelPickerOverlay。
                ZhiSmallPill(
                    // 原版是 shorten(profileName + " · " + model, wide?26:16)（MainActivity.java:1393）
                    label = shorten(
                        state.profileName + " · " + state.modelLabel,
                        if (wide) 26 else 16,
                    ),
                    onClick = onModelChip,
                    modifier = Modifier.weight(1f),
                )

                Spacer(modifier = Modifier.width(8.dp))
                // 停止键在任务运行时滑入，位置就在发送键左边
                AnimatedVisibility(

                    visible = state.composerBusy,
                    enter = fadeIn(ZhiMotion.fadeInSpec) + expandHorizontally(ZhiMotion.sizeSpec) +
                        scaleIn(initialScale = 0.7f, animationSpec = ZhiMotion.scaleEnterSpec),
                    exit = fadeOut(ZhiMotion.fadeOutSpec) + shrinkHorizontally(ZhiMotion.sizeSpec) +
                        scaleOut(targetScale = 0.7f, animationSpec = ZhiMotion.scaleExitSpec),
                ) {
                    ZhiFilledIconButton(
                        icon = ZhiIcons.stop,
                        description = "立即停止当前任务",
                        onClick = onStop,
                        // 走主题的 error 语义色而不是写死的红：浅色模式会自动换成暗红，
                        // 前景也必须是 onError，否则浅色下红底白字对比度不足。
                        containerColor = MiuixTheme.colorScheme.error,
                        contentColor = MiuixTheme.colorScheme.onError,
                        iconSize = 14.dp,
                        size = 36.dp,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }

                // 圆形发送键（参考图：上箭头）
                ZhiFilledIconButton(
                    description = "发送消息",
                    glyph = "↑",
                    glyphSize = 18.sp,
                    onClick = onSend,
                    containerColor = scheme.primary,
                    enabled = state.composerText.isNotBlank(),
                    size = 34.dp,
                )

            } // 底排 Row

            } // 悬浮面板内容 Column
        } // FloatingToolbar 外层
    }
}
}

@Composable
private fun AttachmentChip(attachment: Attachment, onRemove: () -> Unit) {
    val scheme = MiuixTheme.colorScheme
    Card(
        modifier = Modifier.height(26.dp),
        cornerRadius = ZhiRadius.inner,
        insideMargin = PaddingValues(start = 8.dp, end = 2.dp),
        colors = CardDefaults.defaultColors(
            color = scheme.surfaceContainerHighest,
            contentColor = scheme.onSurface,
        ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (attachment.isImage) ZhiIcons.floatingBall else ZhiIcons.file,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = attachment.label,
                fontSize = ZhiTextScale.Footnote,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(112.dp).padding(start = 4.dp),
            )
            // 直接用 ZhiIconButton 的 compact 参数压成 22dp 点击区，
            // 不再外面套一个固定尺寸的 Box（那样只有内层图标可点，命中区也不对）
            ZhiIconButton(
                icon = ZhiIcons.close,
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
