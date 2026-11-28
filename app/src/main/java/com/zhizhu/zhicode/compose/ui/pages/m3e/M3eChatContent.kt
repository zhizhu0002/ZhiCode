@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.zhizhu.zhicode.compose.ui.pages.m3e

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.model.TaskState
import com.zhizhu.zhicode.compose.model.WorkspaceUiState
import com.zhizhu.zhicode.compose.state.WorkspaceViewModel
import com.zhizhu.zhicode.compose.ui.FloatingBottomShell
import com.zhizhu.zhicode.compose.ui.Glass
import com.zhizhu.zhicode.compose.ui.LocalBottomBarInset
import com.zhizhu.zhicode.compose.ui.LocalFloatingNavInset
import com.zhizhu.zhicode.compose.ui.LocalImeMotion
import com.zhizhu.zhicode.compose.ui.LocalNavBarProgress
import com.zhizhu.zhicode.compose.ui.MessageBar
import com.zhizhu.zhicode.compose.ui.TopBarInsetCompact
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.chat.ChatList
import com.zhizhu.zhicode.compose.ui.pages.miuix.ZhiAnchoredMenuHost
import com.zhizhu.zhicode.compose.ui.composer.Composer
import com.zhizhu.zhicode.compose.ui.composer.ComposerTextState

/** 悬浮层之上再留的余量。 */
private val M3eFloatingBottomGap = 8.dp

/** 实测值到手之前的兜底下限。 */
private val M3eMinFloatingInset = 72.dp

/**
 * M3E 对话区。
 *
 * 消息列表与输入器沿用共享实现（它们的圆角/底色已由 [com.zhizhu.zhicode.compose.ui.pages.ZhiPageSet]
 * 的 token 驱动），这里重写的是**外壳**：任务进度卡、反馈条与它们的定位。
 */
@Composable
internal fun M3eChatContent(
    state: WorkspaceUiState,
    viewModel: WorkspaceViewModel,
    wide: Boolean,
    modifier: Modifier,
    glass: Glass,
) {
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { focusManager.clearFocus(force = true) }
    val floating = state.tasks.isNotEmpty()

    Box(modifier = modifier.fillMaxSize()) {
        var floatingContentHeightPx by remember { mutableStateOf(0) }
        var composerFocused by remember { mutableStateOf(false) }
        val imeMotion = LocalImeMotion.current
        val density = LocalDensity.current
        val liftByFullScreenOverlay = state.sidebarOpen || state.settingsOpen ||
            state.uiDebugOpen || state.environmentOpen
        val settleBar = LocalFloatingNavInset.current
        val navBarProgressState = LocalNavBarProgress.current

        val bottomBarPx = with(density) { LocalBottomBarInset.current.roundToPx() }
        val floatingBarPx = with(density) { settleBar.roundToPx() }
        val occupiedBottomPx = maxOf(bottomBarPx, floatingBarPx)

        val bottomInset = with(density) {
            (floatingContentHeightPx.toDp() + settleBar + M3eFloatingBottomGap)
                .coerceAtLeast(M3eMinFloatingInset)
        }

        ChatList(
            state = state,
            onToggleTool = viewModel::toggleToolExpanded,
            onToggleGroup = viewModel::toggleGroupExpanded,
            onToggleThinking = viewModel::toggleThinking,
            onMessageActions = viewModel::showMessageActions,
            onToolAction = { item, toolId, label ->
                viewModel.applyToolAction(item.id, toolId, label)
            },
            onToggleSelect = viewModel::toggleMultiSelect,
            onRangeSelect = viewModel::setMultiSelectRange,
            onCancelSelect = viewModel::cancelMultiSelect,
            onExportSelect = viewModel::openExportSheet,
            onToggleSelectAll = viewModel::toggleSelectAll,
            anchoredMenu = { anchorId, fingerOffset ->
                ZhiAnchoredMenuHost(state, viewModel, anchorId, fingerOffset)
            },
            onLoadOlder = viewModel::loadOlderTranscript,
            onLoadLatest = viewModel::jumpToLatest,
            modifier = glass.capture(Modifier.fillMaxSize()),
            bottomInset = bottomInset,
            occupiedBottomPx = occupiedBottomPx,
            suppressImeResize = liftByFullScreenOverlay,
            imeOpenGeneration = if (liftByFullScreenOverlay) 0 else imeMotion.openGeneration,
            composerFocused = composerFocused,
            topInset = TopBarInsetCompact,
            debugMode = state.debugAppMode,
            debugTasks = if (state.debugAppMode) state.tasks else emptyList(),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .graphicsLayer {
                    val progress = navBarProgressState.value
                    if (progress > 0f) translationY = settleBar.toPx() * progress
                }
                .padding(bottom = settleBar)
                .offset {
                    val imeBottom = if (liftByFullScreenOverlay) 0 else imeMotion.imeBottomPx
                    IntOffset(0, -maxOf(0, imeBottom - occupiedBottomPx))
                }
                .onSizeChanged { floatingContentHeightPx = it.height },
        ) {
            val shellMotion = MaterialTheme.motionScheme
            AnimatedVisibility(
                visible = floating,
                enter = slideInVertically(shellMotion.fastSpatialSpec()) { it / 2 } +
                    fadeIn(shellMotion.defaultEffectsSpec()),
                exit = slideOutVertically(shellMotion.fastSpatialSpec()) { it / 2 } +
                    fadeOut(shellMotion.fastEffectsSpec()),
            ) {
                M3eAgentStatusCard(
                    status = state.workingStatus.orEmpty(),
                    taskCount = state.tasks.size,
                    doneCount = state.tasks.count { it.state == TaskState.DONE },
                    onExpand = viewModel::openTaskList,
                    wide = wide,
                    glass = glass,
                )
            }

            MessageBar(
                message = state.message,
                isError = state.messageIsError,
                onDismiss = { viewModel.clearMessage() },
                wide = wide,
                glass = glass,
            )

            M3eComposerHost(
                state = state,
                viewModel = viewModel,
                wide = wide,
                glass = glass,
                onFocusChanged = { composerFocused = it },
            )
        }
    }
}

/** 悬浮的任务进度卡，M3E 形态。 */
@Composable
private fun M3eAgentStatusCard(
    status: String,
    taskCount: Int,
    doneCount: Int,
    onExpand: () -> Unit,
    wide: Boolean,
    glass: Glass,
) {
    val scheme = MaterialTheme.colorScheme
    FloatingBottomShell(
        wide = wide,
        glass = glass,
        verticalPadding = 8.dp,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            shape = m3eSegmentShape(0, 1),
            color = scheme.surfaceContainerHigh,
            onClick = onExpand,
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = ZhiIcons.skill,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "任务进度 $doneCount / $taskCount",
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp),
                    )
                    Text(
                        text = "点开",
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.primary,
                    )
                }
                if (status.isNotBlank()) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                LinearWavyProgressIndicator(
                    progress = { if (taskCount == 0) 0f else doneCount.toFloat() / taskCount },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
        }
    }
}

/** 输入器宿主：草稿存取与图片/文件选择器的接线，渲染交给共享 Composer。 */
@Composable
private fun M3eComposerHost(
    state: WorkspaceUiState,
    viewModel: WorkspaceViewModel,
    wide: Boolean,
    glass: Glass,
    onFocusChanged: (Boolean) -> Unit,
) {
    val textState = remember { ComposerTextState() }

    var lastSessionId by remember { mutableStateOf(state.activeSessionId) }
    LaunchedEffect(state.activeSessionId) {
        if (state.activeSessionId == lastSessionId) return@LaunchedEffect
        textState.saveDraft(lastSessionId)
        lastSessionId = state.activeSessionId
        textState.restoreDraft(state.activeSessionId)
    }
    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
    ) { uris -> viewModel.attachImages(uris) }
    val pickSystemFiles = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> uris?.forEach { viewModel.attachDocument(it) } }

    Composer(
        state = state,
        wide = wide,
        glass = glass,
        textState = textState,
        onSlashQueryChanged = viewModel::onSlashQueryChanged,
        onFileQueryChanged = viewModel::onFileQueryChanged,
        onPickFile = { path ->
            textState.replaceLastAtToken(path)
            viewModel.onFileQueryChanged(null)
        },
        onSend = { viewModel.sendFromComposerText(textState) },
        onSendWithoutReply = { viewModel.sendWithoutReplyFromComposerText(textState) },
        onStop = viewModel::stop,
        onRemoveAttachment = { viewModel.removeAttachment(it.id) },
        onAttachFile = viewModel::openAttachPicker,
        onPickImage = { pickImage.launch(M3eImageMimeTypes.single()) },
        onPickSystemFiles = { pickSystemFiles.launch(M3eSystemFileMimeTypes) },
        onAttachmentImage = viewModel::currentAttachmentImage,
        onPermissionSelected = viewModel::setPermissionMode,
        onModelThinkingChip = viewModel::openModelThinkingPicker,
        onEnterModelThinkingSubmenu = viewModel::enterModelThinkingSubmenu,
        onBackToModelThinkingList = viewModel::backToModelThinkingList,
        onSelectModelAndEffort = viewModel::selectModelAndEffort,
        onDismissModelThinking = viewModel::closeModelThinkingPicker,
        onConfigureModels = viewModel::openApiConfigEditActiveProvider,
        onModelChip = viewModel::showModelPicker,
        onSelectProvider = viewModel::selectModelPickerProfile,
        onDismissProvider = viewModel::closeModelPicker,
        onAddProvider = viewModel::openApiProfileFormFromModelPicker,
        onManageProviders = viewModel::openApiConfigManageFromPicker,
        onCompactContext = {
            viewModel.requestComposerText("/compact")
            viewModel.send()
        },
        onPickSlash = viewModel::pickSlashCommand,
        onRemoveQueued = viewModel::removeQueuedPrompt,
        onEditQueued = viewModel::editQueuedPrompt,
        debugMode = state.debugAppMode,
        onFocusChanged = onFocusChanged,
    )
}

private val M3eImageMimeTypes = arrayOf(listOf("image", "*").joinToString("/"))
private val M3eSystemFileMimeTypes = arrayOf(listOf("*", "*").joinToString("/"))
