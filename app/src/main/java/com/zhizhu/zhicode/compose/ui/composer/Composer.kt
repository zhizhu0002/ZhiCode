package com.zhizhu.zhicode.compose.ui.composer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhizhu.zhicode.pi.PiModelConfig
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import com.zhizhu.zhicode.compose.model.Attachment
import com.zhizhu.zhicode.compose.model.ChatImage
import com.zhizhu.zhicode.compose.model.EffortLevel
import com.zhizhu.zhicode.compose.model.PermissionMode
import com.zhizhu.zhicode.compose.model.SlashCommand
import com.zhizhu.zhicode.compose.model.WorkspaceUiState
import com.zhizhu.zhicode.compose.ui.Glass
import com.zhizhu.zhicode.compose.theme.ZhiColors
import com.zhizhu.zhicode.compose.ui.ZhiFilledIconButton
import com.zhizhu.zhicode.compose.ui.dialogs.DialogShell
import com.zhizhu.zhicode.compose.ui.dialogs.DialogWideInsideMargin
import com.zhizhu.zhicode.compose.ui.dialogs.DialogWideOutsideMargin
import com.zhizhu.zhicode.compose.ui.dialogs.PrimaryButton
import com.zhizhu.zhicode.compose.ui.dialogs.SecondaryButton
import com.zhizhu.zhicode.compose.ui.dialogs.ZhiDialogWidth
import com.zhizhu.zhicode.compose.ui.effects.selfPressSink
import com.zhizhu.zhicode.compose.ui.FloatingBottomShell
import com.zhizhu.zhicode.compose.ui.ZhiPendingImageChip
import com.zhizhu.zhicode.compose.ui.ZhiHorizontalDivider
import com.zhizhu.zhicode.compose.ui.ZhiIconButton
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.ZhiMarkdown
import com.zhizhu.zhicode.compose.ui.ZhiMotion
import com.zhizhu.zhicode.compose.ui.ZhiSmallPill
import com.zhizhu.zhicode.compose.ui.ZhiTextField
import com.zhizhu.zhicode.compose.ui.effects.ZhiSquircle
import com.zhizhu.zhicode.compose.ui.debug.ZhiFrameTrace
import com.zhizhu.zhicode.compose.ui.pages.LocalZhiPageSet
import com.zhizhu.zhicode.compose.ui.pages.m3e.Material3ExpressivePageSet
import com.zhizhu.zhicode.compose.model.formatTokenCountShort
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 输入器，对应原版 buildComposer() */
@Composable
fun Composer(
    state: WorkspaceUiState,
    wide: Boolean,
    glass: Glass,
    /** 输入框文字的所有权在这里（见 [ComposerTextState] 的说明）。 */
    textState: ComposerTextState,
    /** 斜杠查询变化时通知状态层（它要算匹配列表、决定面板显不显示）。 */
    onSlashQueryChanged: (String?) -> Unit,
    /** `@` 文件引用查询变化（`@` 之后的那一段；`null` = 不在引用态）。 */
    onFileQueryChanged: (String?) -> Unit,
    /** 从 `@` 补全面板里选中了一个路径。 */
    onPickFile: (String) -> Unit,
    /** 取消一条排队中的预输入。 */
    onRemoveQueued: (String) -> Unit = {},
    /** 把一条排队中的预输入取回输入框。 */
    onEditQueued: (String) -> Unit = {},
    onSend: () -> Unit,
    /** 长按发送键：只把消息发进对话流，不触发模型回复。 */
    onSendWithoutReply: () -> Unit,
    onStop: () -> Unit,
    onPickSlash: (SlashCommand) -> Unit,
    onRemoveAttachment: (Attachment) -> Unit,
    // ---- `+` 菜单的动作 ----
    onAttachFile: () -> Unit,
    onPickImage: () -> Unit,
    /** 走**系统文件管理器**（SAF）挑文件附加，可多选。 */
    onPickSystemFiles: () -> Unit,
    /** 取某个待发附件的图片（输入器里的缩略图）。 */
    onAttachmentImage: (String) -> ChatImage?,
    // ---- 页脚三个下拉 ----
    onPermissionSelected: (PermissionMode) -> Unit,
    onModelThinkingChip: () -> Unit,
    onEnterModelThinkingSubmenu: (PiModelConfig) -> Unit = {},
    onBackToModelThinkingList: () -> Unit = {},
    onSelectModelAndEffort: (String, EffortLevel) -> Unit = { _, _ -> },
    onDismissModelThinking: () -> Unit = {},
    onConfigureModels: () -> Unit = {},
    onModelChip: () -> Unit,
    onSelectProvider: (String) -> Unit = {},
    onDismissProvider: () -> Unit = {},
    onAddProvider: () -> Unit = {},
    onManageProviders: () -> Unit = {},
    onCompactContext: () -> Unit = {},
    debugMode: Boolean = false,
    modifier: Modifier = Modifier,
    onFocusChanged: (Boolean) -> Unit = {},
) {
    ZhiFrameTrace.countRecompose("Composer")
    val scheme = MiuixTheme.colorScheme
    var contextDetailOpen by remember { mutableStateOf(false) }

    /* 发送键可用性：从输入框状态派生，只在"空/非空"翻转时让读它的地方失效。 */
    val canSend by remember(textState) {
        derivedStateOf { textState.field.text.isNotBlank() }
    }


    /* 输入触发：`/` 命令 与 `@` 文件引用。 */
    LaunchedEffect(textState) {
        snapshotFlow { textState.field.text.toString() }
            .map { text ->
                val lastAt = text.lastIndexOf('@')
                val atQuery = if (lastAt >= 0) {
                    val tail = text.substring(lastAt + 1)
                    if (tail.contains(' ') || tail.contains('\n')) null else tail
                } else null
                val slashQuery =
                    if (text.startsWith("/") && !text.contains(' ') && !text.contains('\n')) text
                    else null
                slashQuery to atQuery
            }
            .distinctUntilChanged()
            .debounce(60)
            .collect { (slashQuery, atQuery) ->
                onSlashQueryChanged(slashQuery)
                onFileQueryChanged(atQuery)
            }
    }

    /* 外部动作写入输入框：「编辑后发送」「恢复草稿」「设置目标」等。 */
    var lastAppliedEpoch by remember { mutableStateOf(state.composerTextEpoch) }
    LaunchedEffect(state.composerTextEpoch) {
        if (state.composerTextEpoch == lastAppliedEpoch) return@LaunchedEffect
        lastAppliedEpoch = state.composerTextEpoch
        textState.setText(state.composerText)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            // 横向内缩**不在这里**做：交给 FloatingBottomShell（Common.kt）统一处理。
            .padding(top = 4.dp, bottom = 10.dp),
    ) {

        /* 排队中的预输入。 */
        AnimatedVisibility(
            visible = state.pendingInputs.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            QueuedPromptPanel(
                items = state.pendingInputs,
                onRemove = onRemoveQueued,
                onEdit = onEditQueued,
            )
        }

        AnimatedVisibility(
            visible = state.slashQuery != null && state.slashMatches.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            SlashPalette(matches = state.slashMatches, onPick = onPickSlash)
        }

        /* `@` 文件引用面板。 */
        AnimatedVisibility(
            visible = state.fileQuery != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            FilePalette(
                query = state.fileQuery.orEmpty(),
                matches = state.fileQueryMatches,
                onPick = onPickFile,
            )
        }

        // 输入框 + 发送键**并排**：输入箱在左（weight 1f），方角发送键在右。
        // 圆角 / 阴影 / 横向内缩来源，保证三块"同框"。
        FloatingBottomShell(wide = wide, glass = glass) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(
                        animationSpec = ZhiMotion.sizeSpec,
                    )
                    .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
            ) {
            // 附件条随附件增减平滑展开/收起。
            AnimatedVisibility(
                visible = state.attachments.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                val pendingImages = state.attachments.filter { it.isImage }
                    .mapNotNull { attachment -> onAttachmentImage(attachment.id)?.let { attachment to it } }
                val pendingFiles = state.attachments.filter { !it.isImage }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // ── 文件：**横向**滚动，每个是固定宽的窄卡片 ──────────────
                    if (pendingFiles.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            pendingFiles.forEach { attachment ->
                                AttachmentChip(attachment) { onRemoveAttachment(attachment) }
                            }
                        }
                    }

                    // ── 图片：横向滚动的缩略图带 ─────────────────────────
                    if (pendingImages.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            pendingImages.forEach { (attachment, image) ->
                                ZhiPendingImageChip(
                                    image = image,
                                    onRemove = { onRemoveAttachment(attachment) },
                                )
                            }
                        }
                    }
                }
            }


            // 输入行独占一排（参考图）：透明输入框 + 宽 placeholder
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                ZhiTextField(
                    // 文字归 ComposerTextState 所有（见参数说明）。
                    state = textState.field,
                    label = "描述任务或向 ZhiCode 提问",
                    useLabelAsPlaceholder = true,
                    // 透明容器 + 透明描边：只留外层方角框
                    colors = TextFieldDefaults.textFieldColors(
                        // 浅色模式下不再用半透明的最高层容器，否则输入框会变成
                        // 一块偏灰的脏色；使用主题明确提供的输入容器层。
                        backgroundColor = if (ZhiColors.isDark()) scheme.surfaceContainerHighest else ZhiColors.panelSurface(),
                        labelColor = scheme.onSurfaceVariantSummary,
                        borderColor = Color.Transparent,
                    ),
                    // 收紧 Miuix TextField 的内部留白，让输入区更矮
                    insideMargin = DpSize(6.dp, 1.dp),
                    // 常规字重的正文样式：Miuix 主题默认文字样式偏粗，会显得比原版重
                    textStyle = MiuixTheme.textStyles.main.copy(
                        fontSize = MiuixTheme.textStyles.title4.fontSize,
                        fontWeight = FontWeight.Normal,
                    ),
                    // 比原版的 minLines = 2 更矮，只占一行起，随内容长高
                    minLines = 1,
                    maxLines = 5,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 36.dp)
                        .onFocusChanged { onFocusChanged(it.isFocused) },
                )

                /* 暂停键：**只在会话运行时出现**，位置在发送键左边（即输入框右侧）。 */
                AnimatedVisibility(
                    visible = state.composerBusy || state.stopping,
                    enter = fadeIn(ZhiMotion.fadeInSpec) + expandHorizontally(ZhiMotion.sizeSpec),
                    exit = fadeOut(ZhiMotion.fadeOutSpec) + shrinkHorizontally(ZhiMotion.sizeSpec),
                ) {
                    ZhiFilledIconButton(
                        icon = ZhiIcons.stop,
                        description = if (state.stopping) {
                            "停止指令已发出，再次点击会重发中断"
                        } else {
                            "立即停止当前任务"
                        },
                        onClick = onStop,
                        containerColor = scheme.error,
                        contentColor = scheme.onError,
                        square = true,
                        iconSize = 12.dp,
                        size = ComposerActionSize,
                        modifier = Modifier.padding(start = 6.dp, bottom = 2.dp).selfPressSink(),
                    )
                }

                Box(
                    modifier = Modifier
                        .padding(start = 6.dp, bottom = 2.dp)
                        .selfPressSink()
                        .combinedClickable(
                            enabled = canSend,
                            onClick = {},
                            onLongClick = onSendWithoutReply,
                        ),
                ) {
                    ZhiFilledIconButton(
                        description = "发送消息（长按：只发送不等待回复）",
                        icon = ZhiIcons.send,
                        iconSize = 16.dp,
                        // 点击必须**真的**接在这里：见上面那段说明，
                        // 交给外层是收不到的。
                        onClick = onSend,
                        containerColor = scheme.primary,
                        contentColor = scheme.onPrimary,
                        enabled = canSend,
                        square = true,
                        size = ComposerActionSize,
                    )
                }
            }

            // ---- 主体调试模式：输入行下方的 Markdown 实时预览 ----
            // 用生产渲染器 ZhiMarkdown，所以这里看到的排版就是消息里会有的排版。
            if (debugMode) {
                // 调试面板会读文字长度与内容 —— 这里**可以**在组合期读 textState
                val debugText = textState.text()
                ZhiHorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    color = scheme.outline.copy(alpha = 0.45f),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "M↓ 实时预览",
                        color = scheme.primary,
                        fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "  ${debugText.length} 字 · ${state.attachments.size} 附件 · " +
                            (state.slashQuery?.let { "斜杠「$it」" } ?: "无斜杠"),
                        color = scheme.onSurfaceVariantSummary,
                        fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                if (debugText.isBlank()) {
                    Text(
                        text = "输入框为空；在上面写点 Markdown（# 标题 / - 列表 / **粗体** / `代码` / 表格）即会在此渲染。",
                        color = scheme.onSurfaceVariantSummary,
                        fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                        modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
                    )
                } else {
                    ZhiMarkdown(
                        source = debugText,
                        bodyFontSize = MiuixTheme.textStyles.body2.fontSize,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                    )
                }
            }

            // 底排（参考图）：+ 圆钮 | 权限/推理下拉 | 弹性 | 模型 pill | 停止 | 发送
            Row(
                modifier = Modifier.fillMaxWidth().height(40.dp).padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AttachMenuPicker(
                    onAttachFile = onAttachFile,
                    onPickSystemFiles = onPickSystemFiles,
                    onPickImage = onPickImage,
                )

                PermissionModePicker(mode = state.permissionMode, onSelect = onPermissionSelected)
                val keyboard = LocalSoftwareKeyboardController.current
                val efforts = remember(state.availableEfforts) {
                    state.availableEfforts.ifEmpty { EffortLevel.DEFAULT_AVAILABLE }
                }
                val modelChipLabel = remember(state.modelLabel, state.effort, efforts) {
                    if (efforts.size > 1) "${state.modelLabel} · ${state.effort.label}" else state.modelLabel
                }
                ComposerPickerAnchor(modifier = Modifier.weight(1f)) { maxMenuHeight ->
                    ZhiSmallPill(
                        label = modelChipLabel,
                        marquee = true,
                        onClick = {
                            keyboard?.hide()
                            onModelThinkingChip()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ModelThinkingPopupMenu(
                        picker = state.modelThinkingPicker,
                        maxMenuHeight = maxMenuHeight,
                        onDismiss = onDismissModelThinking,
                        onEnterSubmenu = onEnterModelThinkingSubmenu,
                        onBackToModels = onBackToModelThinkingList,
                        onSelectModelAndEffort = onSelectModelAndEffort,
                        onConfigureModels = onConfigureModels,
                    )
                }
                ComposerPickerAnchor { maxMenuHeight ->
                    ZhiSmallPill(
                        label = shorten(state.profileName.ifBlank { "未配置" }, if (wide) 18 else 10),
                        onClick = {
                            keyboard?.hide()
                            onModelChip()
                        },
                    )
                    ProviderPopupMenu(
                        picker = state.modelPicker,
                        maxMenuHeight = maxMenuHeight,
                        configurationOpen = state.apiConfig != null,
                        onDismiss = onDismissProvider,
                        onSelectProfile = onSelectProvider,
                        onAddProvider = onAddProvider,
                        onManageProviders = onManageProviders,
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))
                ContextUsageRingBadge(
                    tokens = state.contextTokens,
                    window = state.contextWindow,
                    onClick = { contextDetailOpen = true },
                )
            } // 底排 Row
        } // FloatingBottomShell
    }
    ContextDetailsDialog(
        show = contextDetailOpen,
        state = state,
        onCompact = {
            contextDetailOpen = false
            onCompactContext()
        },
        onDismiss = { contextDetailOpen = false },
    )
}
}

@Composable
private fun AttachmentChip(attachment: Attachment, onRemove: () -> Unit) {
    val scheme = MiuixTheme.colorScheme
    Card(
        // 横向排布所以是**定宽**。160dp 是"一行放两个半"的折中：
        // 文件名能显示 8~10 个汉字，再窄就只剩图标；超过就横滑（见调用点）。
        modifier = Modifier.width(AttachmentChipWidth),
        cornerRadius = ZhiRadius.inner,
        insideMargin = PaddingValues(start = 6.dp, end = 2.dp, top = 5.dp, bottom = 5.dp),
        colors = CardDefaults.defaultColors(
            color = scheme.surfaceContainerHigh,
            contentColor = scheme.onSurface,
        ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(ZhiSquircle(ZhiRadius.inner))
                    .background(scheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = if (attachment.isImage) ZhiIcons.image else ZhiIcons.file,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(15.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f).padding(start = 8.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = attachment.label,
                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // 体积：技能那条路一直是空的（`sizeLabel` 在技能 Store 里才有），
                // 空串就不画第二行，免得留一条没有内容的空行高。
                if (attachment.sizeLabel.isNotBlank()) {
                    Text(
                        text = attachment.sizeLabel,
                        fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                        color = scheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
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

/** 待发文件卡片的固定宽度。 */
private val AttachmentChipWidth = 160.dp

private fun shorten(value: String, max: Int): String =
    if (value.length <= max) value else value.substring(0, max - 1) + "…"

/** 底排「发送 / 停止」两个动作键的边长。 */
private val ComposerActionSize = 30.dp

@Composable
internal fun ContextProgressRing(
    fraction: Float,
    ringSize: Dp,
    strokeWidth: Dp,
    accentColor: Color,
    trackColor: Color,
) {
    Canvas(modifier = Modifier.size(ringSize)) {
        val stroke = strokeWidth.toPx()
        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = stroke),
        )
        if (fraction > 0f) {
            drawArc(
                color = accentColor,
                startAngle = -90f,
                sweepAngle = 360f * fraction,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun ContextUsageRingBadge(
    tokens: Int,
    window: Int,
    onClick: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    val fraction = if (window > 0) (tokens.toFloat() / window).coerceIn(0f, 1f) else 0f
    val pct = (fraction * 100f).roundToInt().coerceIn(0, 100)
    val pctLabel = formatContextPercentLabel(tokens, window)
    val accent = when {
        pct >= 90 -> scheme.error
        pct >= 72 -> ZhiColors.amber()
        else -> scheme.primary
    }
    val textColor = when {
        pct >= 90 -> scheme.error
        pct >= 72 -> ZhiColors.amber()
        else -> scheme.onSurfaceVariantSummary
    }
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ContextProgressRing(
            fraction = fraction,
            ringSize = 13.dp,
            strokeWidth = 2.dp,
            accentColor = accent,
            trackColor = scheme.onSurface.copy(alpha = 0.14f),
        )
        Text(
            text = pctLabel,
            color = textColor,
            fontSize = MiuixTheme.textStyles.footnote2.fontSize,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            style = MiuixTheme.textStyles.footnote2.copy(fontFeatureSettings = "tnum"),
        )
    }
}

internal fun formatContextPercentLabel(used: Int, window: Int): String {
    if (used <= 0 || window <= 0) return "0%"
    val rawTenths = ((used.toDouble() / window.toDouble()) * 1000.0).roundToInt().coerceIn(0, 1000)
    val tenths = if (rawTenths == 0) 1 else rawTenths
    return if (tenths % 10 == 0) "${tenths / 10}%" else "${tenths / 10}.${tenths % 10}%"
}

@Composable
private fun ContextDetailsDialog(
    show: Boolean,
    state: WorkspaceUiState,
    onCompact: () -> Unit,
    onDismiss: () -> Unit,
) {
    val used = state.contextTokens.coerceAtLeast(0)
    val window = state.contextWindow.coerceAtLeast(0)
    val remaining = if (window > 0) (window - used).coerceAtLeast(0) else 0
    val fraction = if (window > 0) (used.toFloat() / window).coerceIn(0f, 1f) else 0f
    val pct = (fraction * 100f).roundToInt().coerceIn(0, 100)
    val pctLabel = formatContextPercentLabel(used, window)
    if (LocalZhiPageSet.current === Material3ExpressivePageSet) {
        ExpressiveContextDetailsDialog(
            show = show,
            state = state,
            used = used,
            window = window,
            remaining = remaining,
            fraction = fraction,
            pct = pct,
            pctLabel = pctLabel,
            onCompact = onCompact,
            onDismiss = onDismiss,
        )
        return
    }
    val scheme = MiuixTheme.colorScheme
    val accent = when {
        pct >= 90 -> scheme.error
        pct >= 72 -> ZhiColors.amber()
        else -> scheme.primary
    }

    OverlayDialog(
        show = show,
        onDismissRequest = onDismiss,
        largeScreen = true,
        maxWidth = ZhiDialogWidth.Compact,
        outsideMargin = DialogWideOutsideMargin,
        insideMargin = DialogWideInsideMargin,
    ) {
        DialogShell(
            title = "上下文占用",
            actions = {
                if (!state.composerBusy && used > 0) {
                    SecondaryButton(
                        text = "压缩上下文",
                        onClick = onCompact,
                    )
                }
                PrimaryButton(
                    text = "关闭",
                    onClick = onDismiss,
                    modifier = Modifier.padding(start = 8.dp),
                )
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ContextProgressRing(
                        fraction = fraction,
                        ringSize = 22.dp,
                        strokeWidth = 3.dp,
                        accentColor = accent,
                        trackColor = scheme.onSurface.copy(alpha = 0.14f),
                    )
                    Text(
                        text = "$pctLabel  ·  ${formatTokenCountShort(used)} / ${formatTokenCountShort(window)}",
                        color = scheme.onBackground,
                        fontSize = MiuixTheme.textStyles.body1.fontSize,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text = "已用：${"%,d".format(used)} tokens\n" +
                        "剩余：${"%,d".format(remaining)} tokens\n" +
                        "窗口上限：${"%,d".format(window)} tokens\n" +
                        "当前模型：${state.profileName} · ${state.modelLabel}\n" +
                        "自动压缩：${if (state.settings.autoCompact) "开启（达到 ${state.settings.autoCompactPercent}% 触发）" else "关闭"}",
                    color = scheme.onSurfaceVariantSummary,
                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                    lineHeight = 20.sp,
                )
            }
        }
    }
}
