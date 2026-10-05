package com.zhizhu.zhicode.compose.ui.panes

import android.content.Context
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import org.eclipse.tm4e.core.registry.IThemeSource
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import io.github.rosemoe.sora.widget.CodeEditor

/** Sora 的一次性 TextMate 注册与编辑器配置。 */
internal object SoraEditorHost {
    private val initialized = AtomicBoolean(false)

    fun initialize(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        try {
            // 不再引入 oniguruma-native。language-textmate 会自动使用自带的 Joni
            // JVM fallback，避免 Android 15/HyperOS 上 libonig 的 AsyncAnalyzer SIGSEGV。
            val files = FileProviderRegistry.getInstance()
            files.addFileProvider(AssetsFileResolver(context.applicationContext.assets))
            val themes = ThemeRegistry.getInstance()
            val stream = files.tryGetInputStream("sora/textmate/darcula.json")
                ?: error("Sora theme asset missing")
            val source = IThemeSource.fromInputStream(
                stream,
                "sora/textmate/darcula.json",
                StandardCharsets.UTF_8,
            )
            themes.loadTheme(ThemeModel(source, "darcula").apply { setDark(true) }, true)
            // 只加载 scopeName 与 grammar 内部声明完全一致的条目。一个 grammar
            // 不能被复用给多个 scope，否则 TextMate 会在主线程测量编辑器时直接抛错。
            GrammarRegistry.getInstance().loadGrammars("sora/textmate/languages.json")
        } catch (_: Throwable) {
            // 高亮是增强能力，不应成为打开文本文件的前置条件。
            // configure() 还会对单个语言创建做一次隔离，失败时退回纯文本。
            initialized.set(false)
        }
    }

    fun configure(editor: CodeEditor, context: Context, language: String) {
        initialize(context)
        editor.setTextSize(16f)
        editor.setScaleTextSizes(11f, 32f)
        editor.setScalable(true)
        // Sora draws and handles its own scrollbars. Its RenderNode path can leave a
        // stale opaque rectangle behind while the scrollbar thumb is dragged on some
        // Android/MIUI GPU combinations, so keep the editor on the Canvas path.
        // This does not disable hardware acceleration for the Activity; it only disables
        // Sora's optional cached line RenderNodes.
        editor.setHardwareAcceleratedDrawAllowed(false)
        editor.setLineNumberEnabled(true)
        editor.setDisplayLnPanel(true)
        editor.setLineInfoTextSize(12f)
        editor.setLineNumberMarginLeft(8f)
        editor.setWordwrap(false)
        editor.setScrollBarEnabled(true)
        val scope = languageScope(language)
        if (scope == null) {
            editor.setEditorLanguage(null)
        } else {
            runCatching { TextMateLanguage.create(scope, false) }
                .onSuccess(editor::setEditorLanguage)
                .onFailure { editor.setEditorLanguage(null) }
        }
        runCatching { editor.setColorScheme(TextMateColorScheme.create(ThemeRegistry.getInstance())) }
    }

    fun subscribeTextChanges(editor: CodeEditor, onChanged: () -> Unit) =
        editor.subscribeEvent(ContentChangeEvent::class.java) { _, _ -> onChanged() }

    fun languageForEditor(language: String): String? = languageScope(language)

    private fun languageScope(language: String): String? = when (language.lowercase()) {
        "java" -> "source.java"
        "kotlin" -> "source.kotlin"
        "python" -> "source.python"
        "xml", "html" -> "text.xml"
        "markdown" -> "text.html.markdown"
        // 其他扩展名暂时保持纯文本；没有对应 grammar 时不要猜 scope，避免
        // TextMate 在加载时校验 scopeName 失败并让整个编辑页崩溃。
        "shell" -> "source.shell"
        else -> null
    }
}
