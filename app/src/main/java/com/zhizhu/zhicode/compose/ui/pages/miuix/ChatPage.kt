package com.zhizhu.zhicode.compose.ui.pages.miuix

import com.zhizhu.zhicode.compose.ui.composer.ComposerTextState
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.model.AgentTask
import com.zhizhu.zhicode.compose.model.WorkspaceUiState
import com.zhizhu.zhicode.compose.state.WorkspaceViewModel
import com.zhizhu.zhicode.compose.ui.chat.AgentProgressCard
import com.zhizhu.zhicode.compose.ui.chat.ChatList
import com.zhizhu.zhicode.compose.ui.composer.Composer
import com.zhizhu.zhicode.compose.ui.FloatingBottomShell
import com.zhizhu.zhicode.compose.ui.Glass
import com.zhizhu.zhicode.compose.ui.LocalImeMotion
import com.zhizhu.zhicode.compose.ui.MessageBar
import com.zhizhu.zhicode.compose.ui.TopBarInsetCompact
import com.zhizhu.zhicode.compose.ui.ZhiAnchoredActionMenu
import com.zhizhu.zhicode.compose.ui.ZhiMotion
import com.zhizhu.zhicode.compose.ui.debug.ZhiFrameTrace
import com.zhizhu.zhicode.compose.ui.settings.rememberLastNonNull
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val SystemFileMimeTypes = arrayOf(listOf("*", "*").joinToString("/"))

private val ImageMimeTypes = arrayOf(listOf("image", "*").joinToString("/"))

/** （从 `AppScaffold.kt` 原地拆出，内容逐字未改。） */
@Composable
internal fun ChatPage(
    state: WorkspaceUiState,
    viewModel: WorkspaceViewModel,
    wide: Boolean,
    modifier: Modifier = Modifier,
    glass: Glass,
) {
    // 这张卡是「任务进度」，所以**只在真有任务时**才出现。
    ZhiFrameTrace.countRecompose("ChatArea")
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) {
        // 只清理首帧系统/Compose 自动分配的焦点；用户点击输入框后的焦点不受影响。
        focusManager.clearFocus(force = true)
    }
    val floating = state.tasks.isNotEmpty()

    Box(modifier = modifier.fillMaxSize()) {
        var floatingContentHeightPx by remember { mutableStateOf(0) }

        var composerFocused by remember { mutableStateOf(false) }
        val imeMotion = LocalImeMotion.current
        val density = LocalDensity.current
        // 全屏浮层盖住工作区时，窗口级 IME 属于浮层里的输入框，不应抬起后面的聊天输入器。
        val liftByFullScreenOverlay = state.sidebarOpen || state.settingsOpen ||
            state.uiDebugOpen || state.environmentOpen

        val settleBar = com.zhizhu.zhicode.compose.ui.LocalFloatingNavInset.current
        // 悬浮栏隐藏进度（补间后的显示值）。只在**绘制阶段**读它，不参与布局。
        val navBarProgressState = com.zhizhu.zhicode.compose.ui.LocalNavBarProgress.current

        val bottomBarPx = with(density) {
            com.zhizhu.zhicode.compose.ui.LocalBottomBarInset.current.roundToPx()
        }
        val floatingBarPx = with(density) { settleBar.roundToPx() }
        val occupiedBottomPx = maxOf(bottomBarPx, floatingBarPx)

        val bottomInset = with(density) {
            (floatingContentHeightPx.toDp() + settleBar + FloatingBottomGap)
                .coerceAtLeast(MinFloatingInset)
        }

        ChatList(
            state = state,
            onToggleTool = viewModel::toggleToolExpanded,
            // 组键由界面按 toolId 推导（见 ToolGrouping），所以这里直接把它交给 VM 
            onToggleGroup = viewModel::toggleGroupExpanded,
            onToggleThinking = viewModel::toggleThinking,
            onMessageActions = viewModel::showMessageActions,
            // 单个工具的 ⋯ 菜单里选中了一项：动作作用在某一行上，所以把 toolId 一起传下去。
            onToolAction = { item, toolId, label -> viewModel.applyToolAction(item.id, toolId, label) },
            // 多选导出：勾选、取消、导出三个动作都只改状态层，界面按状态重绘。
            onToggleSelect = viewModel::toggleMultiSelect,
            // 拖动对选：整段区间一次性替换选中集合（见 ChatList 的手势说明）。
            onRangeSelect = viewModel::setMultiSelectRange,
            onCancelSelect = viewModel::cancelMultiSelect,
            // 导出：打开导出面板（格式与选项在面板里选，照 rikkahub 的 ChatExportSheet）。
            onExportSelect = viewModel::openExportSheet,
            onToggleSelectAll = viewModel::toggleSelectAll,
            // 长按消息的动作菜单：由那一条消息自己渲染（从手指位置长出来）
            anchoredMenu = { anchorId, fingerOffset ->
                ZhiAnchoredMenuHost(state, viewModel, anchorId, fingerOffset)
            },
            // 列表滚到顶时往前补一页更早的历史。恢复历史是分页的，重复调用由
            // ViewModel 里的守卫挡掉（"正在加载"/"已经没有更早的"）。
            onLoadOlder = viewModel::loadOlderTranscript,
            // 常驻窗口的尾部被淘汰过时，末尾的「回到最新」和"发送时人还在历史里"
            // 都会走到这里（用最新一页替换整份 transcript）。
            onLoadLatest = viewModel::jumpToLatest,
            modifier = glass.capture(Modifier.fillMaxSize()),
            // 底部留白 = 实测的悬浮层高度 + 一点余量：让被盖住的内容也能滑上来。
            bottomInset = bottomInset,
            occupiedBottomPx = occupiedBottomPx,
            suppressImeResize = liftByFullScreenOverlay,
            imeOpenGeneration = if (liftByFullScreenOverlay) 0 else imeMotion.openGeneration,
            composerFocused = composerFocused,
            // 首条消息落在顶栏（含 Tab 行）下缘；列表全高，滚动时消息从顶栏 blur 下穿过
            topInset = TopBarInsetCompact,
            // 主体调试模式：在真实消息上就地显示类型/长度/工具计数 + Markdown 源码开关；
            // 并把每条消息的细节默认全展开、任务清单内联进对话流（见 ChatList 的同名参数）
            debugMode = state.debugAppMode,
            // 内联任务清单的数据：调试模式才传，非调试时是空列表（不渲染）。
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
                // 悬浮胶囊的高度由这一列自己让开：内容层现在铺到屏幕底边
                .padding(bottom = settleBar)
                .offset {
                    val imeBottom = if (liftByFullScreenOverlay) 0 else imeMotion.imeBottomPx
                    IntOffset(0, -maxOf(0, imeBottom - occupiedBottomPx))
                }
                .onSizeChanged {
                    // 曾在 DEBUG 下顺带写 `nav-probe chat-floating` 日志，用来核对
                    floatingContentHeightPx = it.height
                },
        ) {
            AnimatedVisibility(
                visible = floating,
                enter = slideInVertically(ZhiMotion.enterSpec) { it / 2 } +
                    fadeIn(ZhiMotion.fadeInSpec),
                exit = slideOutVertically(ZhiMotion.exitSpec) { it / 2 } +
                    fadeOut(ZhiMotion.fadeOutSpec),
            ) {
                FloatingAgentStatus(
                    status = state.workingStatus.orEmpty(),
                    tasks = state.tasks,
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

            // 悬浮输入器
            ComposerHost(
                state = state,
                viewModel = viewModel,
                wide = wide,
                glass = glass,
                onFocusChanged = { composerFocused = it },
            )
        }
    }
}

/** 悬浮层之上再留的余量。 */
private val FloatingBottomGap = 8.dp

/** 实测值到手之前的兜底下限。 */
private val MinFloatingInset = 72.dp

/** IME 抬起量「停稳」的判定窗口（毫秒）。 */

/** 悬浮的任务与状态卡。 */
@Composable
private fun FloatingAgentStatus(
    status: String,
    tasks: List<AgentTask>,
    onExpand: () -> Unit,
    wide: Boolean,
    glass: Glass,
) {
    ZhiFrameTrace.countRecompose("FloatingCard")
    val scheme = MiuixTheme.colorScheme
    // 外壳走共用的 FloatingBottomShell：内缩/圆角/阴影/模糊与反馈条、输入器同源
    FloatingBottomShell(
        wide = wide,
        glass = glass,
        verticalPadding = 8.dp,
    ) {
        AgentProgressCard(
            status = status,
            tasks = tasks,
            onExpand = onExpand,
            embedded = true,
        )
    }
}

@Composable
private fun ComposerHost(
    state: WorkspaceUiState,
    viewModel: WorkspaceViewModel,
    wide: Boolean,
    glass: Glass,
    onFocusChanged: (Boolean) -> Unit,
) {
    val textState = remember { ComposerTextState() }

    /* 切会话时存旧草稿、取新草稿。 */
    var lastSessionId by remember { mutableStateOf(state.activeSessionId) }
    LaunchedEffect(state.activeSessionId) {
        if (state.activeSessionId == lastSessionId) return@LaunchedEffect
        textState.saveDraft(lastSessionId)
        lastSessionId = state.activeSessionId
        textState.restoreDraft(state.activeSessionId)
    }
    // 用 GetMultipleContents 而不是 GetContent：相册里一次挑多张是常态
    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
    ) { uris -> viewModel.attachImages(uris) }

    // 系统文件管理器：`OpenMultipleDocuments` 而不是 `GetMultipleContents` 
    val pickSystemFiles = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> uris?.forEach { viewModel.attachDocument(it) } }

    Composer(
        state = state,
        wide = wide,
        glass = glass,
        // 文字归这个对象所有（见 ComposerTextState）：按键不再写回状态层，
        // 于是打字时整个对话流不会跟着重组。
        textState = textState,
        // 只有进入/离开斜杠命令态时才通知状态层；普通打字一次都不走。
        onSlashQueryChanged = viewModel::onSlashQueryChanged,
        // `@` 文件引用：查询串交给状态层算候选，选中后把路径写回文字。
        onFileQueryChanged = viewModel::onFileQueryChanged,
        onPickFile = { path ->
            textState.replaceLastAtToken(path)
            // 选中后立刻退出引用态：文字里已经补成 `@路径 `（带空格）
            viewModel.onFileQueryChanged(null)
        },
        onSend = { viewModel.sendFromComposerText(textState) },
        // 长按发送键：只发进对话流，不触发模型回复。
        onSendWithoutReply = { viewModel.sendWithoutReplyFromComposerText(textState) },
        onStop = viewModel::stop,
        onRemoveAttachment = { viewModel.removeAttachment(it.id) },
        // `+` 菜单的四个动作
        onAttachFile = viewModel::openAttachPicker,
        // `GetMultipleContents` 收 `String`（一个 MIME），`OpenMultipleDocuments`
        // 收 `Array<String>` —— 所以一个取单个元素、一个传整份数组，不是笔误。
        onPickImage = { pickImage.launch(ImageMimeTypes.single()) },
        onPickSystemFiles = { pickSystemFiles.launch(SystemFileMimeTypes) },
        // 输入器里待发图片的缩略图数据（按附件 id 取，不进 state）
        onAttachmentImage = viewModel::currentAttachmentImage,
        // 页脚的下拉：选中即生效，不再弹选择器
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
        // 排队中的预输入：取回（编辑后再发）与取消。
        onRemoveQueued = viewModel::removeQueuedPrompt,
        onEditQueued = viewModel::editQueuedPrompt,
        // 主体调试模式：输入行下方实时渲染当前输入的 Markdown（与对话流同一渲染器）
        debugMode = state.debugAppMode,
        onFocusChanged = onFocusChanged,
    )
}

/** 长按动作菜单的**宿主插槽**：交给被长按的那一项去调用。 */
@Composable
internal fun ZhiAnchoredMenuHost(
    state: WorkspaceUiState,
    viewModel: WorkspaceViewModel,
    anchorId: String,
    fingerOffset: DpOffset?,
    renderInRootScaffold: Boolean = true,
) {
    val picker = state.choicePicker
    // 菜单从手指位置长出来，而退场动画期间 choicePicker 已经是 null —— 不兜住的话，
    // 面板会在退场那几帧里弹回条目左上角再消失。
    val shownPicker = rememberLastNonNull(picker) ?: return
    val open = picker != null && picker.isActionMenu && picker.anchorId == anchorId
    if (!shownPicker.isActionMenu || shownPicker.anchorId != anchorId) return
    ZhiAnchoredActionMenu(
        // 不套 `if`：退出动画需要浮层常驻组合（见 ZhiAnchoredActionMenu 的说明）。
        open = open,
        labels = shownPicker.options.map { it.label },
        onSelect = viewModel::onChoiceSelected,
        onDismiss = viewModel::dismissChoicePicker,
        // 非空时面板从**手指那一点**长出来；为空则退回贴条目锚定。
        fingerOffset = fingerOffset,
        renderInRootScaffold = renderInRootScaffold,
    )
}
