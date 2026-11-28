@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.zhizhu.zhicode.compose.ui.pages.m3e

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.model.FileEntry
import com.zhizhu.zhicode.compose.model.WorkspaceUiState
import com.zhizhu.zhicode.compose.state.WorkspaceViewModel
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.pages.miuix.FilesPage
import com.zhizhu.zhicode.compose.ui.pages.miuix.TerminalPage
import com.zhizhu.zhicode.compose.ui.chat.rememberAutoFollow

/**
 * M3E 文件区。
 *
 * 列表本体沿用共享 [FilesPage]：它的行高、圆角与底色已由页面组 token 驱动，
 * 且 40+ 个业务回调只在这里接线一次。
 */
@Composable
internal fun M3eFilesContent(
    state: WorkspaceUiState,
    viewModel: WorkspaceViewModel,
    modifier: Modifier,
) {
    FilesPage(
        filePath = state.filePath,
        entries = state.fileEntries,
        emptyNote = state.fileNote,
        listOptions = state.fileListOptions,
        onQueryChange = viewModel::updateFileQuery,
        onSortChange = viewModel::updateFileSort,
        onToggleSortDirection = viewModel::toggleFileSortDirection,
        onOpen = viewModel::openFile,
        onNavigate = viewModel::navigateTo,
        root = state.fileRoot,
        onSwitchRoot = viewModel::switchFileRoot,
        nameForm = state.fileNameForm,
        onNewEntry = viewModel::newFileForm,
        onRename = viewModel::renameForm,
        onNameDraftChange = viewModel::updateFileNameDraft,
        onSubmitName = viewModel::submitFileNameForm,
        onCancelName = viewModel::cancelFileNameForm,
        deletePrompt = state.fileDeletePrompt,
        onConfirmDelete = viewModel::confirmDelete,
        onCancelDelete = viewModel::cancelDelete,
        sharedStorageGranted = state.sharedStorageGranted,
        onGrantSharedStorage = viewModel::openSharedStorageSettings,
        selection = state.fileSelection,
        selectionMode = state.fileSelectionMode,
        fileClipboardCount = state.fileClipboard.size,
        fileClipboardMove = state.fileClipboardMove,
        onPasteFiles = viewModel::pasteFilesIntoCurrentDirectory,
        onLongPressEntry = viewModel::longPressFileEntry,
        onToggleEntry = viewModel::toggleFileSelection,
        onSetSelection = viewModel::setFileSelection,
        onToggleSelectAll = viewModel::toggleSelectAllFiles,
        onClearSelection = viewModel::clearFileSelection,
        onRenameSelected = viewModel::renameSelectedEntry,
        onDeleteSelected = viewModel::deleteSelectedEntries,
        onAttachSelected = viewModel::attachSelectedEntries,
        active = state.tab == com.zhizhu.zhicode.compose.model.WorkspaceTab.FILES,
        modifier = modifier,
    )
}

/**
 * M3E 终端区。
 *
 * 环境就绪时走共享 [TerminalPage]：真实终端是 `AndroidView` 承载的原生 xterm 视图，
 * 与主题无关。环境未就绪时用 M3E 的只读占位。
 */
@Composable
internal fun M3eTerminalContent(
    state: WorkspaceUiState,
    viewModel: WorkspaceViewModel,
    modifier: Modifier,
) {
    if (state.runtimeReady) {
        TerminalPage(
            lines = state.terminalLines,
            projectName = state.projectName,
            runtimeReady = true,
            workingDirectory = state.projectPath,
            terminalHolder = { ctx -> viewModel.terminalPane(ctx) },
            modifier = modifier,
            installing = state.runtimeInstalling,
            progress = state.runtimeProgress,
            message = state.runtimeMessage,
            onInstall = viewModel::installRuntime,
        )
        return
    }
    M3eTerminalPlaceholder(
        projectName = state.projectName,
        lines = state.terminalLines.map { it.text },
        installing = state.runtimeInstalling,
        progress = state.runtimeProgress,
        message = state.runtimeMessage,
        onInstall = viewModel::installRuntime,
        modifier = modifier,
    )
}

/** 环境未就绪时的只读占位，说明为什么不能输入。 */
@Composable
private fun M3eTerminalPlaceholder(
    projectName: String,
    lines: List<String>,
    installing: Boolean,
    progress: Int,
    message: String,
    onInstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    val autoFollow by rememberAutoFollow(listState)
    LaunchedEffect(lines.size, autoFollow) {
        if (autoFollow && lines.isNotEmpty()) listState.scrollToItem(lines.size - 1)
    }

    Surface(modifier = modifier.fillMaxSize(), color = scheme.surface) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = M3eDimens.RowPaddingH,
                        end = M3eDimens.RowPaddingH,
                        top = 12.dp,
                        bottom = 8.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = ZhiIcons.terminal,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp),
                ) {
                    Text(
                        text = "终端",
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.onSurface,
                    )
                    if (projectName.isNotBlank()) {
                        Text(
                            text = projectName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = M3eDimens.RowPaddingH),
                shape = m3eSegmentShape(0, 1),
                color = scheme.surfaceContainer,
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        items(count = lines.size) { index ->
                            Text(
                                text = lines[index].ifEmpty { " " },
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (installing) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(12.dp),
                        ) {
                            Text(
                                text = message.ifBlank { "正在安装内置运行环境…" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant,
                            )
                            LinearWavyProgressIndicator(
                                progress = { if (progress in 1..100) progress / 100f else 0f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = M3eDimens.RowPaddingH, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = if (installing) {
                        "正在安装内置运行环境，完成后这里会变成可输入的终端。"
                    } else {
                        message.ifBlank {
                            "内置运行环境还没就绪，所以这里是只读的。装好之后就是真终端。"
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (installing || message.isBlank()) {
                        scheme.onSurfaceVariant
                    } else {
                        scheme.error
                    },
                )
                if (!installing) {
                    Button(onClick = onInstall) {
                        Text(if (message.isBlank()) "安装运行环境" else "重试安装")
                    }
                }
            }
        }
    }
}
