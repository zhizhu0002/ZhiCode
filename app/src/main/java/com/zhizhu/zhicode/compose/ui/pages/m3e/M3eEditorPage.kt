@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.zhizhu.zhicode.compose.ui.pages.m3e

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zhizhu.zhicode.compose.editor.EditorFileIo
import com.zhizhu.zhicode.compose.editor.EditorViewModel
import com.zhizhu.zhicode.compose.ui.panes.SoraEditorHost
import com.zhizhu.zhicode.compose.theme.LocalZhiDark
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import io.github.rosemoe.sora.lang.Language

import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

import com.zhizhu.zhicode.compose.ui.panes.SoraEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula
import io.github.rosemoe.sora.widget.schemes.SchemeGitHub

private const val M3eEditorPrefs = "zhi_editor"

@Composable
internal fun M3eEditorPage(viewModel: EditorViewModel, onExit: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val dark = LocalZhiDark.current
    val scheme = MaterialTheme.colorScheme
    val prefs = remember(context) {
        context.getSharedPreferences(M3eEditorPrefs, Context.MODE_PRIVATE)
    }

    var editor by remember { mutableStateOf<CodeEditor?>(null) }
    var language by remember { mutableStateOf<Language?>(null) }
    var soraScheme by remember(dark) {
        mutableStateOf<EditorColorScheme>(if (dark) SchemeDarcula() else SchemeGitHub())
    }
    var menuExpanded by remember { mutableStateOf(false) }
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var replaceExpanded by remember { mutableStateOf(false) }
    var currentMatch by remember { mutableIntStateOf(0) }
    var totalMatches by remember { mutableIntStateOf(0) }
    var matchCase by remember { mutableStateOf(false) }
    var wholeWord by remember { mutableStateOf(false) }
    var regex by remember { mutableStateOf(false) }
    var wordwrap by remember { mutableStateOf(prefs.getBoolean("wordwrap", true)) }
    var completion by remember { mutableStateOf(prefs.getBoolean("completion", true)) }
    var lineNumbers by remember { mutableStateOf(prefs.getBoolean("line_number", true)) }
    var highlightLine by remember { mutableStateOf(prefs.getBoolean("highlight_line", true)) }

    val readOnly = !state.writable

    LaunchedEffect(state.fileName, dark) {
        language = SoraEditorHost.languageFor(context, state.fileName)
        soraScheme = withContext(Dispatchers.IO) { SoraEditorHost.colorScheme(context, dark) }
    }

    LaunchedEffect(state.notice) {
        state.notice?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    fun searcher(): EditorSearcher? = editor
        ?.takeUnless { it.isReleased }
        ?.let { runCatching { it.getSearcher() }.getOrNull() }

    fun clearMatches() {
        currentMatch = 0
        totalMatches = 0
    }

    fun updateMatches() {
        val active = searcher()
        if (active == null || !active.hasQuery()) {
            clearMatches()
            return
        }
        runCatching {
            totalMatches = active.getMatchedPositionCount().coerceAtLeast(0)
            currentMatch = (active.getCurrentMatchedPositionIndex() + 1)
                .coerceIn(0, totalMatches)
        }.onFailure { clearMatches() }
    }

    fun options(): EditorSearcher.SearchOptions {
        val type = when {
            regex -> EditorSearcher.SearchOptions.TYPE_REGULAR_EXPRESSION
            wholeWord -> EditorSearcher.SearchOptions.TYPE_WHOLE_WORD
            else -> EditorSearcher.SearchOptions.TYPE_NORMAL
        }
        return EditorSearcher.SearchOptions(type, !matchCase)
    }

    fun applySearch(pattern: String = searchQuery) {
        val active = searcher() ?: return clearMatches()
        if (pattern.isEmpty()) {
            runCatching { active.stopSearch() }
            clearMatches()
            return
        }
        if (runCatching { active.search(pattern, options()) }.isFailure) {
            runCatching { active.stopSearch() }
        }
        updateMatches()
    }

    fun closeSearch() {
        searchVisible = false
        searchQuery = ""
        replaceQuery = ""
        replaceExpanded = false
        runCatching { searcher()?.stopSearch() }
        clearMatches()
    }

    LaunchedEffect(searchVisible, searchQuery, matchCase, wholeWord, regex) {
        if (searchVisible) applySearch() else clearMatches()
    }

    val requestExit: () -> Unit = {
        if (state.dirty) viewModel.requestExitConfirm() else onExit()
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        containerColor = scheme.surface,
        topBar = {
            Surface(color = scheme.surfaceContainerLow, tonalElevation = 2.dp) {
                CenterAlignedTopAppBar(
                    modifier = Modifier.statusBarsPadding(),
                    title = {
                        Text(
                            text = if (state.dirty) "• ${state.fileName}" else state.fileName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        FilledTonalIconButton(
                            onClick = requestExit,
                            modifier = Modifier.padding(start = 8.dp),
                        ) {
                            Icon(painter = ZhiIcons.back, contentDescription = "返回文件列表")
                        }
                    },
                    actions = {
                        if (state.content != null && !readOnly) {
                            FilledTonalIconButton(onClick = { viewModel.save() }) {
                                Icon(painter = ZhiIcons.save, contentDescription = "保存文件")
                            }
                        }
                        Box {
                            FilledTonalIconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier.padding(end = 8.dp),
                            ) {
                                Icon(painter = ZhiIcons.moreVert, contentDescription = "更多操作")
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("搜索") },
                                    enabled = state.content != null,
                                    onClick = {
                                        menuExpanded = false
                                        searchVisible = true
                                    },
                                )
                                M3eEditorToggleItem("自动换行", wordwrap) {
                                    menuExpanded = false
                                    wordwrap = !wordwrap
                                    prefs.edit().putBoolean("wordwrap", wordwrap).apply()
                                    editor?.isWordwrap = wordwrap
                                }
                                M3eEditorToggleItem("代码补全", completion) {
                                    menuExpanded = false
                                    completion = !completion
                                    prefs.edit().putBoolean("completion", completion).apply()
                                    editor?.getComponent(EditorAutoCompletion::class.java)
                                        ?.setEnabled(completion)
                                }
                                M3eEditorToggleItem("行号", lineNumbers) {
                                    menuExpanded = false
                                    lineNumbers = !lineNumbers
                                    prefs.edit().putBoolean("line_number", lineNumbers).apply()
                                    editor?.setLineNumberEnabled(lineNumbers)
                                }
                                M3eEditorToggleItem("当前行高亮", highlightLine) {
                                    menuExpanded = false
                                    highlightLine = !highlightLine
                                    prefs.edit().putBoolean("highlight_line", highlightLine).apply()
                                    editor?.setHighlightCurrentLine(highlightLine)
                                }
                                EditorFileIo.selectableCharsets.forEach { charset ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "编码 $charset" +
                                                    if (charset.equals(state.charsetName, true)) " ✓" else "",
                                            )
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            viewModel.reload(charset)
                                        },
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                    ),
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
        ) {
            val content = state.content
            if (content == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (state.loading) "正在打开 ${state.fileName}…"
                        else state.error ?: "无法打开这个文件",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            } else {
                SoraEditor(
                    modifier = Modifier.fillMaxSize(),
                    content = content,
                    isReadOnly = readOnly,
                    language = language,
                    scheme = soraScheme,
                    wordwrap = wordwrap,
                    lineNumberEnabled = lineNumbers,
                    highlightLine = highlightLine,
                    onTextChange = viewModel::markDirty,
                    onSearchResult = ::updateMatches,
                    onEditorCreated = { created ->
                        editor = created
                        created.isWordwrap = wordwrap
                        created.setLineNumberEnabled(lineNumbers)
                        created.setHighlightCurrentLine(highlightLine)
                        created.getComponent(EditorAutoCompletion::class.java).setEnabled(completion)
                    },
                )
            }
            val searchMotion = MaterialTheme.motionScheme
            AnimatedVisibility(
                visible = searchVisible,
                enter = slideInVertically(searchMotion.fastSpatialSpec()) { -it } +
                    fadeIn(searchMotion.defaultEffectsSpec()),
                exit = slideOutVertically(searchMotion.fastSpatialSpec()) { -it } +
                    fadeOut(searchMotion.fastEffectsSpec()),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                M3eEditorSearchBar(
                    modifier = Modifier.padding(top = 8.dp),
                    query = searchQuery,
                    currentMatch = currentMatch,
                    totalMatches = totalMatches,
                    replaceExpanded = replaceExpanded,
                    replaceQuery = replaceQuery,
                    matchCase = matchCase,
                    wholeWord = wholeWord,
                    regex = regex,
                    replaceEnabled = !readOnly,
                    enabled = editor != null,
                    onQueryChange = { searchQuery = it; applySearch(it) },
                    onToggleReplace = { replaceExpanded = !replaceExpanded },
                    onReplaceQueryChange = { replaceQuery = it },
                    onToggleMatchCase = { matchCase = !matchCase; applySearch() },
                    onToggleWholeWord = { wholeWord = !wholeWord; applySearch() },
                    onToggleRegex = { regex = !regex; applySearch() },
                    onPrevious = {
                        searcher()?.takeIf { it.hasQuery() }?.let { runCatching { it.gotoPrevious() } }
                        updateMatches()
                    },
                    onNext = {
                        searcher()?.takeIf { it.hasQuery() }?.let { runCatching { it.gotoNext() } }
                        updateMatches()
                    },
                    onReplace = {
                        searcher()?.takeIf { it.hasQuery() }?.let {
                            runCatching { it.replaceCurrentMatch(replaceQuery) }
                            applySearch()
                        }
                    },
                    onReplaceAll = {
                        searcher()?.takeIf { it.hasQuery() }?.let {
                            runCatching { it.replaceAll(replaceQuery) { applySearch() } }
                            applySearch()
                        }
                    },
                    onClose = ::closeSearch,
                )
            }
        }
    }

    if (state.exitConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = viewModel::cancelExitConfirm,
            title = { Text("保存更改") },
            text = { Text("「${state.fileName}」有未保存的修改，退出前要保存吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.save { saved ->
                            if (saved) {
                                viewModel.discardAndExit()
                                onExit()
                            }
                        }
                    },
                ) { Text("保存并退出") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = viewModel::cancelExitConfirm) { Text("取消") }
                    TextButton(
                        onClick = {
                            viewModel.discardAndExit()
                            onExit()
                        },
                    ) { Text("不保存") }
                }
            },
        )
    }
    if (state.saving) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = viewModel::cancelSave,
            title = { Text("正在保存") },
            text = { Text("正在写入 ${state.fileName}…") },
            confirmButton = { TextButton(onClick = viewModel::cancelSave) { Text("取消") } },
        )
    }
    state.saveError?.let { message ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = viewModel::dismissSaveError,
            title = { Text("保存失败") },
            text = { Text(message) },
            confirmButton = {
                Button(onClick = viewModel::dismissSaveError) { Text("知道了") }
            },
        )
    }
}

@Composable
private fun M3eEditorToggleItem(label: String, checked: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(if (checked) "$label ✓" else label) },
        onClick = onClick,
    )
}

@Composable
private fun M3eEditorSearchBar(
    modifier: Modifier = Modifier,
    query: String,
    currentMatch: Int,
    totalMatches: Int,
    replaceExpanded: Boolean,
    replaceQuery: String,
    matchCase: Boolean,
    wholeWord: Boolean,
    regex: Boolean,
    replaceEnabled: Boolean,
    enabled: Boolean,
    onQueryChange: (String) -> Unit,
    onToggleReplace: () -> Unit,
    onReplaceQueryChange: (String) -> Unit,
    onToggleMatchCase: () -> Unit,
    onToggleWholeWord: () -> Unit,
    onToggleRegex: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onReplace: () -> Unit,
    onReplaceAll: () -> Unit,
    onClose: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = M3eDimens.RowPaddingH),
        shape = RoundedCornerShape(28.dp),
        color = scheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    label = { Text("查找") },
                    singleLine = true,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (totalMatches > 0) "$currentMatch/$totalMatches" else "0",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )
                TextButton(onClick = onPrevious, enabled = totalMatches > 0) { Text("↑") }
                TextButton(onClick = onNext, enabled = totalMatches > 0) { Text("↓") }
                TextButton(onClick = onClose) { Text("关闭") }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                M3eEditorSearchOption("Aa", matchCase, onToggleMatchCase)
                M3eEditorSearchOption("ab", wholeWord, onToggleWholeWord)
                M3eEditorSearchOption(".*", regex, onToggleRegex)
                TextButton(onClick = onToggleReplace) {
                    Text(if (replaceExpanded) "收起替换" else "替换")
                }
            }
            if (replaceExpanded) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    OutlinedTextField(
                        value = replaceQuery,
                        onValueChange = onReplaceQueryChange,
                        label = { Text("替换为") },
                        singleLine = true,
                        enabled = replaceEnabled,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onReplace, enabled = replaceEnabled) { Text("替换") }
                    TextButton(onClick = onReplaceAll, enabled = replaceEnabled) { Text("全部") }
                }
            }
        }
    }
}

@Composable
private fun M3eEditorSearchOption(label: String, active: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (active) scheme.secondaryContainer else Color.Transparent,
        contentColor = if (active) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
        onClick = onClick,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}
