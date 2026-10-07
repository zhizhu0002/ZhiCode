package com.zhizhu.zhicode.compose.ui.panes

import android.content.Context
import android.graphics.Typeface
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.DefaultGrammarDefinition
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula
import io.github.rosemoe.sora.widget.schemes.SchemeGitHub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.tm4e.core.registry.IGrammarSource
import org.eclipse.tm4e.core.registry.IThemeSource
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** 一条可检测的 TextMate 语言记录。 */
private data class LanguageSpec(
    val name: String,
    val grammarFile: String,
    val scope: String,
    val extensions: Set<String> = emptySet(),
    val fileNames: Set<String> = emptySet(),
    val fallback: Boolean = false,
)

/**
 * TextMate/Sora 注册表。
 *
 * 语言识别集中在 [languageCatalog]，所以新增一种格式只需要增加一条目录记录，
 * 不再同时修改 grammar 注册、扩展名 map 和特殊文件名 map。
 */
internal object SoraEditorHost {
    private const val GRAMMAR_DIR = "sora/textmate/grammars"
    private const val THEME_DIR = "sora/textmate/themes"
    private const val DARK_THEME = "dark_vs"
    private const val LIGHT_THEME = "light_vs"

    private val lock = ReentrantLock()
    private val grammarRegistry = GrammarRegistry.getInstance()
    private val themeRegistry = ThemeRegistry.getInstance()
    private val languageCache = ConcurrentHashMap<String, TextMateLanguage>()
    private val schemeCache = ConcurrentHashMap<Boolean, EditorColorScheme>()

    /**
     * 语言目录。
     *
     * 现有 ZL2/VS Code grammar 使用专用高亮；没有独立 grammar 的格式使用最后的
     * source.generic 基础 grammar。这样扩展名覆盖可以很广，但不会把 fallback
     * 冒充成完整语义分析。
     */
    private val languageCatalog = listOf(
        LanguageSpec(
            name = "C",
            grammarFile = "c.tmLanguage.json",
            scope = "source.c",
            extensions = setOf("c", "h", "m"),
        ),
        LanguageSpec(
            name = "C++",
            grammarFile = "cpp.tmLanguage.json",
            scope = "source.cpp",
            extensions = setOf("cpp", "cc", "cxx", "hpp", "hh", "hxx", "mm"),
        ),
        LanguageSpec(
            name = "CSS",
            grammarFile = "css.tmLanguage.json",
            scope = "source.css",
            extensions = setOf("css"),
        ),
        LanguageSpec(
            name = "HTML",
            grammarFile = "html.tmLanguage.json",
            scope = "text.html.basic",
            extensions = setOf("html", "htm", "xhtml", "jsp", "asp", "aspx"),
        ),
        LanguageSpec(
            name = "Java",
            grammarFile = "java.tmLanguage.json",
            scope = "source.java",
            extensions = setOf("java"),
        ),
        LanguageSpec(
            name = "JavaScript",
            grammarFile = "javascript.tmLanguage.json",
            scope = "source.js",
            extensions = setOf("js", "jsx", "mjs", "cjs", "cjsx"),
        ),
        LanguageSpec(
            name = "JSON",
            grammarFile = "json.tmLanguage.json",
            scope = "source.json",
            extensions = setOf("json", "jsonc", "json5", "jsonl", "ndjson", "geojson"),
        ),
        LanguageSpec(
            name = "Kotlin",
            grammarFile = "kotlin.tmLanguage.json",
            scope = "source.kotlin",
            extensions = setOf("kt", "kts"),
        ),
        LanguageSpec(
            name = "Log",
            grammarFile = "log.tmLanguage.json",
            scope = "text.log",
            extensions = setOf("log", "logs", "trace", "stacktrace"),
        ),
        LanguageSpec(
            name = "Markdown",
            grammarFile = "markdown.tmLanguage.json",
            scope = "text.html.markdown",
            extensions = setOf("md", "markdown", "mdown", "mkdn", "mdx"),
        ),
        LanguageSpec(
            name = "Python",
            grammarFile = "python.tmLanguage.json",
            scope = "source.python",
            extensions = setOf("py", "pyw", "pyi"),
        ),
        LanguageSpec(
            name = "Shell",
            grammarFile = "shellscript.tmLanguage.json",
            scope = "source.shell",
            extensions = setOf("sh", "bash", "zsh", "fish", "ksh", "command"),
            fileNames = setOf("dockerfile", "containerfile", ".gitignore", ".gitattributes"),
        ),
        LanguageSpec(
            name = "SQL",
            grammarFile = "sql.tmLanguage.json",
            scope = "source.sql",
            extensions = setOf("sql", "sqlite", "sqlite3", "mysql", "pgsql", "plsql"),
        ),
        LanguageSpec(
            name = "TypeScript",
            grammarFile = "typescript.tmLanguage.json",
            scope = "source.ts",
            extensions = setOf("ts", "tsx", "mts", "cts"),
        ),
        LanguageSpec(
            name = "XML",
            grammarFile = "xml.tmLanguage.json",
            scope = "text.xml",
            extensions = setOf("xml", "xsd", "xsl", "xslt", "svg", "plist", "pom", "iml"),
        ),
        LanguageSpec(
            name = "YAML",
            grammarFile = "yaml.tmLanguage.json",
            scope = "source.yaml",
            extensions = setOf("yaml", "yml"),
        ),
        // YAML grammar 的嵌套 scope，不能直接按文件扩展名选择。
        LanguageSpec(
            name = "YAML 1.2",
            grammarFile = "yaml-1.2.tmLanguage.json",
            scope = "source.yaml.1.2",
        ),
        LanguageSpec(
            name = "YAML embedded",
            grammarFile = "yaml-embedded.tmLanguage.json",
            scope = "source.yaml.embedded",
        ),
        LanguageSpec(
            name = "通用代码/配置（基础高亮）",
            grammarFile = "generic-programming.tmLanguage.json",
            scope = "source.generic",
            fallback = true,
            extensions = setOf(
                // Backend and modern languages without a bundled dedicated grammar.
                "go", "rs", "rust", "php", "rb", "ruby", "dart", "swift", "cs", "csharp",
                "scala", "groovy", "gradle", "lua", "r", "jl", "julia", "ex", "exs",
                "erl", "hrl", "hs", "lhs", "clj", "cljs", "fs", "fsx", "asm", "s", "sol",
                "zig", "nim", "v", "cr", "pl", "pm", "tcl", "vim", "tex", "proto",
                // Web/config/data formats without a dedicated grammar in this APK.
                "scss", "sass", "less", "ps1", "psm1", "bat", "cmd", "toml", "ini", "cfg",
                "conf", "properties", "editorconfig", "env", "lock", "csv", "tsv", "graphql",
                "gql", "vue", "svelte", "astro", "hcl", "tf", "tfvars", "nix", "bicep",
                "http", "rest", "httpyac", "diff", "patch", "gitconfig", "gitmodules", "license",
                "gradle.kts",
            ),
            fileNames = setOf(
                "makefile", "gnumakefile", "cmakelists.txt", "jenkinsfile", "vagrantfile",
                "gemfile", "rakefile", ".env", ".editorconfig", "docker-compose.yml",
                "docker-compose.yaml", "compose.yml", "compose.yaml",
            ),
        ),
    )

    /** grammar 注册顺序保留目录顺序，并自动去除 YAML 嵌套等重复 scope。 */
    private val grammarScopes = languageCatalog
        .map { it.grammarFile to it.scope }
        .distinct()

    private val fileNameScopes: Map<String, String> = buildMap {
        languageCatalog.forEach { spec ->
            spec.fileNames.forEach { put(it.lowercase(), spec.scope) }
        }
    }

    private val extensionScopes: Map<String, String> = buildMap {
        languageCatalog.forEach { spec ->
            spec.extensions.forEach { extension ->
                // 专用 grammar 在目录中排在 fallback 前面；不覆盖已有专用映射。
                putIfAbsent(extension.lowercase(), spec.scope)
            }
        }
    }

    private var loaded = false
    private val failedGrammars = ConcurrentHashMap<String, String>()
    private val failedLanguages = ConcurrentHashMap<String, String>()
    private var darkThemeLoaded = false
    private var lightThemeLoaded = false

    /**
     * 不再用一组容易随 jcodings 版本变化的内部类名关闭整个 TextMate。
     * tm4e/joni 会在真正解析 grammar 时报告缺失依赖；此时只让当前 grammar
     * 降级，不能让 XML、JS、HTML、Log 等所有语言一起变成纯文本。
     */
    private suspend fun ensureLoaded(context: Context) = withContext(Dispatchers.IO) {
        lock.withLock {
            if (loaded) return@withLock
            val assets = context.applicationContext.assets
            grammarScopes.forEach { (file, scope) ->
                runCatching {
                    val path = "$GRAMMAR_DIR/$file"
                    val source = IGrammarSource.fromInputStream(
                        assets.open(path), path, StandardCharsets.UTF_8,
                    )
                    grammarRegistry.loadGrammar(
                        DefaultGrammarDefinition.withGrammarSource(source, scope, scope),
                    )
                }.onFailure { failedGrammars[file] = it.message ?: it.javaClass.simpleName }
            }
            listOf(DARK_THEME to true, LIGHT_THEME to false).forEach { (name, dark) ->
                runCatching {
                    val path = "$THEME_DIR/$name.json"
                    val source = IThemeSource.fromInputStream(
                        assets.open(path), path, StandardCharsets.UTF_8,
                    )
                    themeRegistry.loadTheme(source, dark)
                    themeRegistry.findThemeByFileName(name)?.isDark = dark
                }.onSuccess {
                    if (dark) darkThemeLoaded = true else lightThemeLoaded = true
                }
            }
            loaded = true
        }
    }

    suspend fun languageFor(context: Context, fileName: String): TextMateLanguage? {
        ensureLoaded(context)
        val normalized = fileName.substringAfterLast('/').lowercase()
        val extension = normalized.substringAfterLast('.', "")
        val scope = fileNameScopes[normalized] ?: extensionScopes[extension] ?: return null
        return withContext(Dispatchers.IO) {
            lock.withLock {
                languageCache[scope] ?: runCatching {
                    TextMateLanguage.create(scope, grammarRegistry, themeRegistry, true)
                        .also { languageCache[scope] = it }
                }.onFailure {
                    failedLanguages[scope] = it.message ?: it.javaClass.simpleName
                }.getOrNull()
            }
        }
    }

    suspend fun colorScheme(context: Context, dark: Boolean): EditorColorScheme {
        ensureLoaded(context)
        return withContext(Dispatchers.IO) {
            lock.withLock {
                schemeCache[dark] ?: runCatching {
                    val themeName = if (dark) DARK_THEME else LIGHT_THEME
                    val themeLoaded = if (dark) darkThemeLoaded else lightThemeLoaded
                    if (!themeLoaded) error("TextMate theme is not registered: $themeName")
                    themeRegistry.setTheme(themeName)
                    val model = themeRegistry.currentThemeModel
                        ?: error("TextMate theme is not registered: $themeName")
                    TextMateColorScheme.create(themeRegistry, model).also {
                        it.setColor(
                            EditorColorScheme.CURRENT_LINE,
                            if (dark) 0x1AFFFFFF else 0x10000000,
                        )
                    }
                }.getOrElse {
                    readableScheme(if (dark) SchemeDarcula() else SchemeGitHub())
                }.also { schemeCache[dark] = it }
            }
        }
    }

    private fun readableScheme(scheme: EditorColorScheme): EditorColorScheme {
        val dark = scheme.isDark()
        scheme.setColor(EditorColorScheme.WHOLE_BACKGROUND, if (dark) 0xFF1E1E1E.toInt() else 0xFFFFFFFF.toInt())
        scheme.setColor(EditorColorScheme.TEXT_NORMAL, if (dark) 0xFFD4D4D4.toInt() else 0xFF202124.toInt())
        scheme.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, if (dark) 0xFF252526.toInt() else 0xFFF3F3F3.toInt())
        scheme.setColor(EditorColorScheme.LINE_NUMBER, if (dark) 0xFF858585.toInt() else 0xFF6E6E6E.toInt())
        scheme.setColor(EditorColorScheme.LINE_NUMBER_CURRENT, if (dark) 0xFFE0E0E0.toInt() else 0xFF202124.toInt())
        scheme.setColor(EditorColorScheme.CURRENT_LINE, if (dark) 0x1AFFFFFF else 0x10000000)
        scheme.setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, if (dark) 0xFF264F78.toInt() else 0xFFBBDFFF.toInt())
        scheme.setColor(EditorColorScheme.SELECTION_HANDLE, if (dark) 0xFF569CD6.toInt() else 0xFF1976D2.toInt())
        scheme.setColor(EditorColorScheme.SELECTION_INSERT, if (dark) 0xFFD4D4D4.toInt() else 0xFF202124.toInt())
        return scheme
    }

    fun configure(editor: CodeEditor) {
        editor.setTypefaceText(Typeface.MONOSPACE)
        val density = editor.resources.displayMetrics.density
        editor.setTextSize(12.5f)
        editor.setScaleTextSizes(8f * density, 96f * density)
        editor.setScalable(true)
        editor.setLineNumberEnabled(true)
        editor.setPinLineNumber(true)
    }
}
