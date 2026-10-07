/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This file is adapted from ZalithLauncher2's ui/code_editor/SoraEditor.kt.
 * It keeps the original AndroidView lifecycle and deliberately does not add an
 * application-side IME show/hide bridge.
 */
package com.zhizhu.zhicode.compose.ui.panes

import android.graphics.Typeface
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.PublishSearchResultEvent
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.widget.schemes.SchemeGitHub

/**
 * ZL2 同款的单一 Sora AndroidView 包装。
 *
 * 关键点是把 ViewModel 中的 Content 实例直接交给 CodeEditor。编辑过程中
 * 不把整篇字符串回写到 Compose，也不在编辑页主动 hide/show 输入法；这让
 * Sora 的焦点和 InputConnection 成为唯一的键盘路径。
 */
@Composable
internal fun SoraEditor(
    modifier: Modifier = Modifier,
    content: Content?,
    isReadOnly: Boolean = false,
    language: Language? = null,
    scheme: EditorColorScheme = SchemeGitHub(),
    wordwrap: Boolean = false,
    lineNumberEnabled: Boolean = true,
    highlightLine: Boolean = false,
    nonPrintableFlags: Int = 0,
    fontSizePx: Float = 0f,
    onTextChange: (() -> Unit)? = null,
    onSearchResult: (() -> Unit)? = null,
    onEditorCreated: ((CodeEditor) -> Unit)? = null,
    topBar: @Composable () -> Unit = {},
    overlay: (@Composable BoxScope.() -> Unit)? = null,
    containerColor: Color = Color.Transparent,
) {
    Scaffold(
        modifier = modifier.fillMaxSize().imePadding(),
        containerColor = containerColor,
        topBar = topBar,
    ) { paddingValues ->
        var view by remember { mutableStateOf<CodeEditor?>(null) }
        val currentOnTextChange = rememberUpdatedState(onTextChange)
        val currentOnSearchResult = rememberUpdatedState(onSearchResult)
        val currentOnEditorCreated = rememberUpdatedState(onEditorCreated)

        Box(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentAlignment = Alignment.Center,
        ) {
            if (content == null) {
                InfiniteProgressIndicator()
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        CodeEditor(context).apply {
                            SoraEditorHost.configure(this)
                            typefaceText = Typeface.MONOSPACE
                            isEditable = !isReadOnly
                            isFocusable = true
                            isFocusableInTouchMode = true
                            isWordwrap = wordwrap
                            setLineNumberEnabled(lineNumberEnabled)
                            setHighlightCurrentLine(highlightLine)
                            setNonPrintablePaintingFlags(nonPrintableFlags)
                            if (fontSizePx > 0f) setTextSizePx(fontSizePx)
                            setColorScheme(scheme)
                            setText(content, true, null)
                            setEditorLanguage(language)
                            subscribeEvent(ContentChangeEvent::class.java) { event, _ ->
                                if (event.action != ContentChangeEvent.ACTION_SET_NEW_TEXT) {
                                    currentOnTextChange.value?.invoke()
                                }
                            }
                            subscribeEvent(PublishSearchResultEvent::class.java) { _, _ ->
                                post { currentOnSearchResult.value?.invoke() }
                            }
                        }.also {
                            view = it
                            currentOnEditorCreated.value?.invoke(it)
                        }
                    },
                    update = { editor ->
                        // ZL2 的判据：只有换了 Content 实例才重置正文，避免光标跳回开头。
                        if (editor.text !== content) editor.setText(content, true, null)
                        if (editor.editorLanguage !== language) editor.setEditorLanguage(language)
                        if (editor.colorScheme !== scheme) editor.setColorScheme(scheme)
                        if (editor.isEditable == isReadOnly) editor.isEditable = !isReadOnly
                        if (editor.isWordwrap != wordwrap) editor.isWordwrap = wordwrap
                        if (editor.isLineNumberEnabled != lineNumberEnabled) {
                            editor.setLineNumberEnabled(lineNumberEnabled)
                        }
                        if (editor.isHighlightCurrentLine != highlightLine) {
                            editor.setHighlightCurrentLine(highlightLine)
                        }
                        if (editor.getNonPrintablePaintingFlags() != nonPrintableFlags) {
                            editor.setNonPrintablePaintingFlags(nonPrintableFlags)
                        }
                    },
                    onRelease = { editor ->
                        view = null
                        editor.release()
                    },
                )
            }
            overlay?.invoke(this)
        }
    }
}
