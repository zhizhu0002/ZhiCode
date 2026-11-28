/* Zalith Launcher 2 */
package com.zhizhu.zhicode.compose.ui.pages.miuix

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import com.zhizhu.zhicode.compose.ui.effects.ZhiSquircle
import android.widget.Toast
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zhizhu.zhicode.compose.editor.EditorFileIo
import com.zhizhu.zhicode.compose.editor.EditorViewModel
import com.zhizhu.zhicode.compose.theme.LocalZhiDark
import com.zhizhu.zhicode.compose.theme.MochiBaseline
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import com.zhizhu.zhicode.compose.ui.ZhiMotion
import com.zhizhu.zhicode.compose.ui.FloatingBottomShell
import com.zhizhu.zhicode.compose.ui.Glass
import com.zhizhu.zhicode.compose.ui.rememberGlass
import com.zhizhu.zhicode.compose.ui.ZhiIconButton
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.ZhiTextField
import com.zhizhu.zhicode.compose.ui.dialogs.DialogShell
import com.zhizhu.zhicode.compose.ui.effects.PageBlurBar
import com.zhizhu.zhicode.compose.ui.effects.rememberPageBlurBackdrop
import com.zhizhu.zhicode.compose.ui.dialogs.DialogWideInsideMargin
import com.zhizhu.zhicode.compose.ui.dialogs.DialogWideOutsideMargin
import com.zhizhu.zhicode.compose.ui.dialogs.PrimaryButton
import com.zhizhu.zhicode.compose.ui.dialogs.SecondaryButton
import com.zhizhu.zhicode.compose.ui.dialogs.ZhiDialogWidth
import com.zhizhu.zhicode.compose.ui.panes.SoraEditor
import com.zhizhu.zhicode.compose.ui.panes.SoraEditorHost
import com.zhizhu.zhicode.compose.ui.settings.rememberLastNonNull
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula
import io.github.rosemoe.sora.widget.schemes.SchemeGitHub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.menu.OverlayIconCascadingDropdownMenu
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val EDITOR_PREFS = "zl2_editor"

/** ZL2 式编辑器，但控件全部接入本项目 Miuix 组件。 */
@Composable
internal fun EditorPage(viewModel: EditorViewModel, onExit: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val dark = LocalZhiDark.current
    val colors = MiuixTheme.colorScheme
    val glass = rememberGlass()
    val pageBackdrop = rememberPageBlurBackdrop()
    val prefs = remember(context) {
        context.getSharedPreferences(EDITOR_PREFS, android.content.Context.MODE_PRIVATE)
    }

    var editor by remember { mutableStateOf<CodeEditor?>(null) }
    var language by remember { mutableStateOf<Language?>(null) }
    var scheme by remember(dark) {
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
        scheme = withContext(Dispatchers.IO) { SoraEditorHost.colorScheme(context, dark) }
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
        modifier = Modifier.fillMaxSize().imePadding(),
        topBar = {
            PageBlurBar(backdrop = pageBackdrop, blurActive = pageBackdrop != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (pageBackdrop != null) Color.Transparent else colors.surface)
                    .statusBarsPadding()
                    .height(MiuixTopBarCenterHeight)
                    .padding(start = MiuixTopBarNavPadding, end = MiuixTopBarActionPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ZhiIconButton(ZhiIcons.back, "返回文件列表", onClick = requestExit)

                Text(
                    text = if (state.dirty) "• ${state.fileName}" else state.fileName,
                    color = colors.onSurface,
                    fontSize = MiuixTheme.textStyles.title4.fontSize,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    // `weight(1f)` 吃掉返回按钮与右侧图标之间的全部空间：
                    // 标题因此紧贴返回按钮，空隙只来自这行 `titleGap`。
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = MiuixTopBarTitleGap),
                )

                if (state.content != null && !readOnly) {
                    ZhiIconButton(ZhiIcons.save, "保存文件", onClick = { viewModel.save() })
                }
                EditorMoreMenu(
                    enabled = state.content != null,
                    expanded = menuExpanded,
                    onExpandedChange = { menuExpanded = it },
                    wordwrap = wordwrap,
                    completion = completion,
                    lineNumbers = lineNumbers,
                    highlightLine = highlightLine,
                    charsetName = state.charsetName,
                    onSearch = { searchVisible = true },
                    onToggleWordwrap = {
                        wordwrap = !wordwrap
                        prefs.edit().putBoolean("wordwrap", wordwrap).apply()
                        editor?.isWordwrap = wordwrap
                    },
                    onToggleCompletion = {
                        completion = !completion
                        prefs.edit().putBoolean("completion", completion).apply()
                        editor?.getComponent(EditorAutoCompletion::class.java)?.setEnabled(completion)
                    },
                    onToggleLineNumbers = {
                        lineNumbers = !lineNumbers
                        prefs.edit().putBoolean("line_number", lineNumbers).apply()
                        editor?.setLineNumberEnabled(lineNumbers)
                    },
                    onToggleHighlightLine = {
                        highlightLine = !highlightLine
                        prefs.edit().putBoolean("highlight_line", highlightLine).apply()
                        editor?.setHighlightCurrentLine(highlightLine)
                    },
                    onCharset = viewModel::reload,
                )
            }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .then(if (pageBackdrop != null) Modifier.layerBackdrop(pageBackdrop) else Modifier),
        ) {
            val content = state.content
            if (content == null) {
                EditorPlaceholder(
                    text = if (state.loading) "正在打开 ${state.fileName}…"
                    else state.error ?: "无法打开这个文件",
                )
            } else {
                SoraEditor(
                    modifier = glass.capture(Modifier.fillMaxSize()),
                    content = content,
                    isReadOnly = readOnly,
                    language = language,
                    scheme = scheme,
                    wordwrap = wordwrap,
                    lineNumberEnabled = lineNumbers,
                    highlightLine = highlightLine,
                    onTextChange = viewModel::markDirty,
                    onSearchResult = ::updateMatches,
                    onEditorCreated = { created ->
                        editor = created
                        // 语言/主题是异步加载的；编辑器创建时先把交互设置一次，
                        // 后续只更新 View 属性，不重新 setText，避免光标和键盘跳动。
                        created.isWordwrap = wordwrap
                        created.setLineNumberEnabled(lineNumbers)
                        created.setHighlightCurrentLine(highlightLine)
                        created.getComponent(EditorAutoCompletion::class.java).setEnabled(completion)
                    },
                )
            }
            /* 搜索栏进出场。 */
            AnimatedVisibility(
                visible = searchVisible,
                enter = slideInVertically(ZhiMotion.enterSpec) { -it } + fadeIn(ZhiMotion.fadeInSpec),
                exit = slideOutVertically(ZhiMotion.exitSpec) { -it } + fadeOut(ZhiMotion.fadeOutSpec),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                EditorSearchTopBar(
                    glass = glass,
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

    val shownExit = rememberLastNonNull(if (state.exitConfirm) true else null)
    OverlayDialog(
        show = state.exitConfirm,
        onDismissRequest = viewModel::cancelExitConfirm,
        largeScreen = true,
        maxWidth = ZhiDialogWidth.Compact,
        outsideMargin = DialogWideOutsideMargin,
        insideMargin = DialogWideInsideMargin,
    ) {
        if (shownExit != true) return@OverlayDialog
        DialogShell(
            title = "保存更改",
            actions = {
                SecondaryButton("取消", viewModel::cancelExitConfirm)
                SecondaryButton("不保存", {
                    viewModel.discardAndExit()
                    onExit()
                }, Modifier.padding(start = 8.dp))
                PrimaryButton("保存并退出", {
                    viewModel.save { saved ->
                        if (saved) {
                            viewModel.discardAndExit()
                            onExit()
                        }
                    }
                }, Modifier.padding(start = 8.dp))
            },
        ) {
            Text("「${state.fileName}」有未保存的修改，退出前要保存吗？", color = colors.onSurface)
        }
    }

    val shownSaving = rememberLastNonNull(if (state.saving) true else null)
    OverlayDialog(
        show = state.saving,
        onDismissRequest = viewModel::cancelSave,
        largeScreen = true,
        maxWidth = ZhiDialogWidth.Compact,
        outsideMargin = DialogWideOutsideMargin,
        insideMargin = DialogWideInsideMargin,
    ) {
        if (shownSaving != true) return@OverlayDialog
        DialogShell(
            title = "正在保存",
            actions = { SecondaryButton("取消", viewModel::cancelSave) },
        ) { Text("正在写入 ${state.fileName}…", color = colors.onSurface) }
    }

    val shownSaveError = rememberLastNonNull(state.saveError)
    OverlayDialog(
        show = state.saveError != null,
        onDismissRequest = viewModel::dismissSaveError,
        largeScreen = true,
        maxWidth = ZhiDialogWidth.Compact,
        outsideMargin = DialogWideOutsideMargin,
        insideMargin = DialogWideInsideMargin,
    ) {
        val message = shownSaveError ?: return@OverlayDialog
        DialogShell(
            title = "保存失败",
            actions = { PrimaryButton("知道了", viewModel::dismissSaveError) },
        ) { Text(message, color = colors.onSurface) }
    }
    }
}

@Composable
private fun EditorPlaceholder(text: String) {
    val colors = MiuixTheme.colorScheme
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = colors.onSurfaceVariantSummary)
    }
}

/** Miuix 更多菜单：仅保留搜索、编辑显示选项和编码二级菜单。 */
@Composable
private fun EditorMoreMenu(
    enabled: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    wordwrap: Boolean,
    completion: Boolean,
    lineNumbers: Boolean,
    highlightLine: Boolean,
    charsetName: String,
    onSearch: () -> Unit,
    onToggleWordwrap: () -> Unit,
    onToggleCompletion: () -> Unit,
    onToggleLineNumbers: () -> Unit,
    onToggleHighlightLine: () -> Unit,
    onCharset: (String) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val charsetItems = EditorFileIo.selectableCharsets.map { charset ->
        DropdownItem(
            text = charset,
            selected = charset.equals(charsetName, ignoreCase = true),
            onClick = {
                onExpandedChange(false)
                onCharset(charset)
            },
        )
    }
    val items = listOf(
        DropdownItem("搜索", enabled = enabled, onClick = {
            onExpandedChange(false)
            onSearch()
        }),
        DropdownItem("自动换行", selected = wordwrap, onClick = onToggleWordwrap),
        DropdownItem("代码补全", selected = completion, onClick = onToggleCompletion),
        DropdownItem("行号", selected = lineNumbers, onClick = onToggleLineNumbers),
        DropdownItem("当前行高亮", selected = highlightLine, onClick = onToggleHighlightLine),
        DropdownItem("编码 $charsetName", children = charsetItems),
    )
    OverlayIconCascadingDropdownMenu(
        entry = DropdownEntry(items = items),
        onExpandedChange = onExpandedChange,
        content = { Icon(ZhiIcons.moreVert, "更多操作", tint = colors.onSurface) },
    )
}

@Composable
private fun EditorSearchTopBar(
    glass: Glass,
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
    val colors = MiuixTheme.colorScheme
    FloatingBottomShell(
        wide = false,
        glass = glass,
        modifier = modifier,
        verticalPadding = 0.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            /* 第一行：返回 · 输入框 · 匹配计数 · 上一个 · 下一个 */
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                ZhiIconButton(ZhiIcons.back, "关闭搜索", onClose, compact = 36.dp)
                ZhiTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f).heightIn(min = 42.dp, max = 48.dp),
                    label = "搜索文件内容",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    enabled = enabled,
                    insideMargin = androidx.compose.ui.unit.DpSize(8.dp, 0.dp),
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            ZhiIconButton(ZhiIcons.clear, "清空搜索", onClick = { onQueryChange("") }, compact = 30.dp)
                        }
                    },
                )
                /* 匹配计数：数字变化时**滚动替换**而不是瞬跳。 */
                Box(modifier = Modifier.widthIn(min = 46.dp), contentAlignment = Alignment.Center) {
                    AnimatedContent(
                        targetState = if (totalMatches > 0) "$currentMatch/$totalMatches" else "无匹配",
                        transitionSpec = {
                            (fadeIn(ZhiMotion.fadeInSpec) + slideInVertically(ZhiMotion.enterSpec) { it / 3 })
                                .togetherWith(fadeOut(ZhiMotion.fadeOutSpec) + slideOutVertically(ZhiMotion.exitSpec) { -it / 3 })
                                .using(SizeTransform(clip = false))
                        },
                        label = "matchCount",
                    ) { text ->
                        Text(
                            text = text,
                            color = if (totalMatches > 0) colors.primary else colors.onSurfaceVariantActions,
                            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
                ZhiIconButton(ZhiIcons.collapse, "上一个匹配", onPrevious, compact = 34.dp)
                ZhiIconButton(ZhiIcons.expand, "下一个匹配", onNext, compact = 34.dp)
            }

            /* 第二行：三个选项 chip + 替换开关。 */
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                EditorSearchOption("区分大小写", matchCase, enabled, onToggleMatchCase)
                EditorSearchOption("全字匹配", wholeWord, enabled, onToggleWholeWord)
                EditorSearchOption("正则", regex, enabled, onToggleRegex)
                Spacer(Modifier.weight(1f))
                EditorSearchOption(
                    label = if (replaceExpanded) "收起" else "替换",
                    checked = replaceExpanded,
                    enabled = replaceEnabled,
                    onToggle = onToggleReplace,
                    accent = true,
                )
            }

            /* 替换区：展开/收起走项目统一的 `ZhiMotion.sizeSpec` */
            AnimatedVisibility(
                visible = replaceExpanded,
                enter = expandVertically(ZhiMotion.sizeSpec, expandFrom = Alignment.Top) + fadeIn(ZhiMotion.fadeInSpec),
                exit = shrinkVertically(ZhiMotion.sizeSpec, shrinkTowards = Alignment.Top) + fadeOut(ZhiMotion.fadeOutSpec),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ZhiTextField(
                        value = replaceQuery,
                        onValueChange = onReplaceQueryChange,
                        modifier = Modifier.weight(1f).heightIn(min = 42.dp, max = 48.dp),
                        label = "替换为",
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                        enabled = replaceEnabled,
                    )
                    EditorTextButton("替换", replaceEnabled && query.isNotEmpty() && totalMatches > 0, onReplace)
                    EditorTextButton("全部替换", replaceEnabled && query.isNotEmpty(), onReplaceAll)
                }
            }
        }
    }
}

@Composable
private fun EditorSearchOption(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    accent: Boolean = false,
) {
    val colors = MiuixTheme.colorScheme
    val active = checked && enabled
    val fill by animateColorAsState(
        targetValue = when {
            !enabled -> colors.surfaceContainer
            checked && accent -> colors.primary
            checked -> colors.primary
            else -> colors.secondaryContainer
        },
        animationSpec = ZhiMotion.colorSpec,
        label = "optionFill",
    )
    val ink by animateColorAsState(
        targetValue = when {
            !enabled -> colors.onSurfaceVariantActions
            checked -> colors.onPrimary
            else -> colors.onSecondaryContainer
        },
        animationSpec = ZhiMotion.colorSpec,
        label = "optionInk",
    )
    Surface(
        onClick = onToggle,
        enabled = enabled,
        color = fill,
        contentColor = ink,
        shape = ZhiSquircle(ZhiRadius.button),
    ) {
        Text(
            text = label,
            color = ink,
            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun EditorTextButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = Color.Transparent,
        contentColor = MiuixTheme.colorScheme.primary,
        shape = ZhiSquircle(MochiBaseline.cornerLarge),
    ) {
        Text(
            label,
            color = if (enabled) MiuixTheme.colorScheme.primary
            else MiuixTheme.colorScheme.onSurfaceVariantActions,
            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
        )
    }
}

/* 编辑器自绘顶栏的尺寸常量。 */
private val MiuixTopBarCenterHeight = 50.dp
private val MiuixTopBarNavPadding = 16.dp
private val MiuixTopBarActionPadding = 16.dp
private val MiuixTopBarTitleGap = 4.dp
