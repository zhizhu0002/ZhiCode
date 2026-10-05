package com.zhizhu.zhicode.compose.ui.panes

import android.text.InputType
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.zhizhu.zhicode.compose.model.OpenFile
import com.zhizhu.zhicode.compose.ui.ZhiIconButton
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.ZhiTextField
import com.zhizhu.zhicode.compose.ui.dialogs.DialogShell
import com.zhizhu.zhicode.compose.ui.dialogs.PrimaryButton
import com.zhizhu.zhicode.compose.ui.dialogs.SecondaryButton
import com.zhizhu.zhicode.compose.ui.settings.rememberLastNonNull
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.menu.OverlayIconCascadingDropdownMenu
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Sora-backed text editor page with a compact search row. */
@Composable
internal fun FileEditorPage(
    file: OpenFile,
    draft: String?,
    onBack: () -> Unit,
    onSave: (String) -> Unit,
    onCancelEdit: () -> Unit,
    onReload: () -> Unit,
    onCharsetChange: (String) -> Unit,
) {
    val editable = !file.truncated && !isPreviewMarker(file)
    val editing = draft != null && editable
    val scheme = MiuixTheme.colorScheme
    val editorRef = remember(file.path, file.content, file.charsetName) { mutableStateOf<CodeEditor?>(null) }
    val original = remember(file.path, file.content, file.charsetName) { draft ?: file.content }
    var revision by remember { mutableIntStateOf(0) }
    var discardPrompt by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var wrapText by remember(file.path) { mutableStateOf(false) }
    var searchOpen by remember(file.path) { mutableStateOf(false) }
    var searchQuery by remember(file.path) { mutableStateOf("") }
    var replaceQuery by remember(file.path) { mutableStateOf("") }
    var replaceOpen by remember(file.path) { mutableStateOf(false) }
    var regexSearch by remember(file.path) { mutableStateOf(false) }
    var wholeWordSearch by remember(file.path) { mutableStateOf(false) }
    var caseSensitiveSearch by remember(file.path) { mutableStateOf(false) }
    var longTextPrompt by remember(file.path) { mutableStateOf(file.truncated) }
    val shownPrompt = rememberLastNonNull(if (discardPrompt) true else null)
    val shownLongTextPrompt = rememberLastNonNull(if (longTextPrompt) true else null)
    val charsets = remember(file.charsetName) {
        listOf("UTF-8", "GB18030", "GBK", "UTF-16LE", "UTF-16BE", "UTF-32LE", "UTF-32BE", "Big5", "Shift_JIS", "ISO-8859-1", "windows-1252")
            .mapNotNull { name -> runCatching { java.nio.charset.Charset.forName(name) }.getOrNull() }
            .let { list -> if (list.any { it.name().equals(file.charsetName, true) }) list else list + runCatching { java.nio.charset.Charset.forName(file.charsetName) }.getOrNull() }
            .filterNotNull()
            .distinctBy { it.name().lowercase() }
    }
    val current = revision.let { editorRef.value?.text?.toString() ?: original }
    val dirty = editing && current != original

    fun closeSearch() {
        searchOpen = false
        replaceOpen = false
        searchQuery = ""
        replaceQuery = ""
        editorRef.value?.takeUnless { it.isReleased }?.let { editor -> runCatching { editor.getSearcher().stopSearch() } }
    }

    LaunchedEffect(searchOpen, searchQuery, regexSearch, wholeWordSearch, caseSensitiveSearch, editorRef.value) {
        val editor = editorRef.value ?: return@LaunchedEffect
        if (editor.isReleased) return@LaunchedEffect
        val searcher = runCatching { editor.getSearcher() }.getOrNull() ?: return@LaunchedEffect
        if (!searchOpen || searchQuery.isEmpty()) {
            runCatching { searcher.stopSearch() }
            return@LaunchedEffect
        }
        delay(80)
        if (!editor.isReleased && editor.isAttachedToWindow && searchQuery.isNotEmpty()) {
            runCatching {
                val options = if (regexSearch) {
                    EditorSearcher.SearchOptions(EditorSearcher.SearchOptions.TYPE_REGULAR_EXPRESSION, !caseSensitiveSearch)
                } else if (wholeWordSearch) {
                    EditorSearcher.SearchOptions(EditorSearcher.SearchOptions.TYPE_WHOLE_WORD, !caseSensitiveSearch)
                } else {
                    EditorSearcher.SearchOptions(!caseSensitiveSearch, false)
                }
                searcher.search(searchQuery, options)
            }
        }
    }

    fun request(action: () -> Unit) {
        if (dirty) { pendingAction = action; discardPrompt = true } else action()
    }
    fun close() = request(onBack)
    fun saveAndExit() {
        editorRef.value?.takeUnless { it.isReleased }?.let { editor -> onSave(editor.text.toString()); onBack() }
    }

    BackHandler(onBack = ::close)
    Scaffold(
        topBar = {
            TopAppBar(
                title = "",
                color = scheme.surface,
                navigationIcon = { ZhiIconButton(ZhiIcons.back, "返回文件列表", ::close) },
                actions = {
                    Box(Modifier.weight(1f).horizontalScroll(rememberScrollState()), contentAlignment = Alignment.CenterStart) {
                        Text(if (dirty) "*${file.name}" else file.name, color = scheme.onBackground, maxLines = 1)
                    }
                    if (editing) ZhiIconButton(ZhiIcons.save, "保存文件", { editorRef.value?.takeUnless { it.isReleased }?.let { onSave(it.text.toString()) } }, tint = scheme.onBackground)
                    OverlayIconCascadingDropdownMenu(
                        entry = DropdownEntry(items = listOf(
                            DropdownItem("自动换行", selected = wrapText, onClick = {
                                wrapText = !wrapText
                                editorRef.value?.takeUnless { it.isReleased }?.setWordwrap(wrapText)
                            }),
                            DropdownItem("搜索", onClick = { searchOpen = true }),
                            DropdownItem("重新加载", onClick = { request(onReload) }),
                            DropdownItem("编码", children = charsets.map { charset ->
                                DropdownItem(charset.displayName(), selected = charset.name().equals(file.charsetName, true), onClick = { request { onCharsetChange(charset.name()) } })
                            }),
                        )),
                        content = { Icon(ZhiIcons.moreVert, "更多操作", tint = scheme.onBackground) },
                    )
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())) {
            if (searchOpen) {
                EditorSearchPanel(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    replaceQuery = replaceQuery,
                    onReplaceQueryChange = { replaceQuery = it },
                    replaceOpen = replaceOpen,
                    onReplaceOpenChange = { replaceOpen = it },
                    regex = regexSearch,
                    wholeWord = wholeWordSearch,
                    caseSensitive = caseSensitiveSearch,
                    onRegexChange = { regexSearch = it },
                    onWholeWordChange = { wholeWordSearch = it },
                    onCaseSensitiveChange = { caseSensitiveSearch = it },
                    onClose = ::closeSearch,
                    onPrevious = { editorRef.value?.takeUnless { it.isReleased }?.let { runCatching { it.getSearcher().gotoPrevious() } } },
                    onNext = { editorRef.value?.takeUnless { it.isReleased }?.let { runCatching { it.getSearcher().gotoNext() } } },
                    onReplace = { if (searchQuery.isNotEmpty()) editorRef.value?.takeUnless { it.isReleased }?.let { runCatching { it.getSearcher().replaceCurrentMatch(replaceQuery) } } },
                    onReplaceAll = { if (searchQuery.isNotEmpty()) editorRef.value?.takeUnless { it.isReleased }?.let { runCatching { it.getSearcher().replaceAll(replaceQuery) } } },
                )
            }
            key(file.path, file.content, file.charsetName) {
                AndroidView(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 2.dp).weight(1f),
                    factory = { context ->
                        CodeEditor(context).apply {
                            editorRef.value = this
                            isFocusable = true
                            isFocusableInTouchMode = true
                            isClickable = true
                            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                            runCatching { SoraEditorHost.configure(this, context, file.language.takeUnless { file.truncated }.orEmpty()) }.onFailure { setEditorLanguage(null) }
                            setText(draft ?: file.content)
                            setEditable(editing)
                            subscribeEvent(ContentChangeEvent::class.java) { _, _ -> revision++ }
                            post { if (isAttachedToWindow && editing) requestFocus() }
                        }
                    },
                    update = { editor ->
                        editorRef.value = editor
                        editor.setEditable(editing)
                        editor.isFocusable = true
                        editor.isFocusableInTouchMode = true
                    },
                )
            }

        }
    }
    DisposableEffect(editorRef.value) {
        val editor = editorRef.value
        onDispose {
            editor?.takeUnless { it.isReleased }?.let {
                runCatching { it.getSearcher().stopSearch() }
                it.release()
            }
        }
    }

    OverlayDialog(show = shownLongTextPrompt == true, onDismissRequest = { longTextPrompt = false }) {
        DialogShell(title = "超长文本", actions = { PrimaryButton("知道了", { longTextPrompt = false }) }) {
            Text("文件内容较长，已启用有界预览，只显示已读取的前缀；为避免打开时掉帧，本页不启用语法分析，也不能直接保存此预览。", color = scheme.onBackground)
        }
    }
    OverlayDialog(show = shownPrompt == true, onDismissRequest = { discardPrompt = false }) {
        DialogShell(title = "放弃修改", actions = {
            SecondaryButton(text = "继续编辑", onClick = { discardPrompt = false })
            SecondaryButton(text = "不保存", onClick = { discardPrompt = false; onCancelEdit(); pendingAction?.invoke(); pendingAction = null })
            PrimaryButton(text = "保存并退出", onClick = { discardPrompt = false; saveAndExit(); pendingAction = null })
        }) { Text("文档有未保存的修改，要保存后退出吗？", color = scheme.onBackground) }
    }
}

@Composable
private fun EditorSearchPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    replaceQuery: String,
    onReplaceQueryChange: (String) -> Unit,
    replaceOpen: Boolean,
    onReplaceOpenChange: (Boolean) -> Unit,
    regex: Boolean,
    wholeWord: Boolean,
    caseSensitive: Boolean,
    onRegexChange: (Boolean) -> Unit,
    onWholeWordChange: (Boolean) -> Unit,
    onCaseSensitiveChange: (Boolean) -> Unit,
    onClose: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onReplace: () -> Unit,
    onReplaceAll: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    val menu = remember(regex, wholeWord, caseSensitive) {
        DropdownEntry(items = listOf(
            DropdownItem("正则表达式", selected = regex, onClick = { onRegexChange(!regex) }),
            DropdownItem("全词匹配", selected = wholeWord, onClick = { onWholeWordChange(!wholeWord) }),
            DropdownItem("区分大小写", selected = caseSensitive, onClick = { onCaseSensitiveChange(!caseSensitive) }),
            DropdownItem("关闭", onClick = onClose),
        ))
    }
    Column(Modifier.fillMaxWidth().background(scheme.surfaceContainer).padding(horizontal = 8.dp, vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 36.dp, max = 40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("查找", fontSize = 13.sp, color = scheme.onSurfaceVariantSummary, modifier = Modifier.width(34.dp))
            ZhiTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f).heightIn(min = 34.dp, max = 40.dp),
                label = "",
                useLabelAsPlaceholder = true,
                singleLine = true,
                insideMargin = DpSize(6.dp, 0.dp),
                leadingIcon = { Icon(ZhiIcons.search, "搜索", tint = scheme.onSurfaceVariantSummary, modifier = Modifier.size(16.dp)) },
            )
            SearchAction("↑", onPrevious)
            SearchAction("↓", onNext)
            SearchAction("替换", { onReplaceOpenChange(true) })
            SearchAction("全部", onReplaceAll)
            OverlayIconDropdownMenu(menu, minHeight = 34.dp, minWidth = 34.dp) { Icon(ZhiIcons.moreVert, "搜索选项", tint = scheme.onSurfaceVariantSummary, modifier = Modifier.size(18.dp)) }
            ZhiIconButton(ZhiIcons.close, "关闭搜索", onClose, compact = 34.dp, iconSize = 16.dp)
        }
        if (replaceOpen) {
            Row(Modifier.fillMaxWidth().heightIn(min = 34.dp, max = 38.dp).padding(start = 34.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("替换", fontSize = 13.sp, color = scheme.onSurfaceVariantSummary, modifier = Modifier.width(34.dp))
                ZhiTextField(value = replaceQuery, onValueChange = onReplaceQueryChange, modifier = Modifier.weight(1f).heightIn(min = 34.dp, max = 38.dp), label = "", singleLine = true, insideMargin = DpSize(6.dp, 0.dp))
                SearchAction("替换", onReplace)
                SearchAction("收起", { onReplaceOpenChange(false) })
            }
        }
    }
}

@Composable
private fun SearchAction(label: String, onClick: () -> Unit) {
    val scheme = MiuixTheme.colorScheme
    Surface(onClick = onClick, modifier = Modifier.height(34.dp).padding(horizontal = 1.dp), color = Color.Transparent, contentColor = scheme.onSurface, shape = RoundedCornerShape(8.dp)) {
        Box(Modifier.padding(horizontal = 5.dp), contentAlignment = Alignment.Center) { Text(label, fontSize = 12.sp, color = scheme.onSurface) }
    }
}


private fun isPreviewMarker(file: OpenFile): Boolean =
    file.content.startsWith("（二进制文件，") || file.content.startsWith("（读取失败")
