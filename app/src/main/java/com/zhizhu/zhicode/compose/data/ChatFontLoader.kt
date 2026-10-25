package com.zhizhu.zhicode.compose.data

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import java.io.File

/**
 * 聊天正文字体的加载与导入。
 *
 * ## 来源
 *
 * 导入逻辑照 rikkahub 的 `SettingPreferencesUIPage` 里的 `importCustomChatFontInternal`，
 * 那套写法的三个关键点**逐字保留**，因为它们各自挡的是真实的坏情况：
 *
 * 1. **先写临时文件、校验过再 `rename`** —— 直接往目标路径写，中途失败（进程被杀、
 *    存储写满）会留下半个文件，而半个字体文件在 `Typeface.createFromFile` 那里
 *    只表现为"加载出来是默认字体"，用户完全看不出发生了什么。
 * 2. **`Typeface.createFromFile` 先验一遍** —— 用户可能选到 `.zip` / 改错后缀的图片。
 *    不验的话要到渲染那一刻才炸，而那时的栈已经离"导入字体"很远了。
 * 3. **失败时把旧文件改名回去** —— 否则一次失败的替换会**同时弄坏新字体和旧字体**，
 *    用户既没有新的、也丢了原来的。
 *
 * ## 为什么单独一个文件而不是塞进 `ThemeStore`
 *
 * `ThemeStore` 的核心是"一份设置怎么落盘"，它要能被纯 JVM 的单测覆盖
 * （`test-jvm-fast.sh` 里编 Kotlin **不带** android.jar）。`Typeface` 是 `android.graphics`，
 * 放进去会把它拖出快路径。所以：设置形状在 `ThemeStore`，字体文件操作在这里。
 */
object ChatFontLoader {

    /** 自定义字体存放目录（相对 `filesDir`）。 */
    private const val FONT_DIR = "fonts"

    /** 允许的字体扩展名。`ttc` 是字体集合，`Typeface` 也能读。 */
    private val ALLOWED_EXTENSIONS = setOf("ttf", "otf", "ttc")

    /** 文件选择器的 MIME 过滤。`font/*` 在部分机型上没人响应，所以兜底写 `*/*` 再按扩展名筛。 */
    val MIME_TYPES: Array<String> = arrayOf(
        "font/*",
        "application/x-font-ttf",
        "application/x-font-otf",
        "application/vnd.ms-opentype",
        "application/octet-stream",
        "*/*",
    )

    /** 一次导入的结果。[relativePath] 是相对 `filesDir` 的路径，与 `ThemeSettings.customFontPath` 同形。 */
    data class Imported(val relativePath: String, val displayName: String)

    /**
     * 按当前设置解析出 `FontFamily`。
     *
     * 关键点：**任何失败都回落到默认字体，绝不抛异常**。
     * 这个函数在每次重组时都会跑（聊天气泡的 `fontFamily`），
     * 一个抛异常的实现会让"字体文件丢了"变成"打开会话就崩"。
     *
     * `CUSTOM` 但路径为空 / 文件不存在时同样回落默认 —— 那种状态是
     * "用户选了自定义但还没导入"，界面应该显示默认字体而不是空白。
     */
    @Composable
    fun rememberChatFontFamily(settings: ThemeSettings): FontFamily {
        val context = LocalContext.current
        return remember(settings.chatFont, settings.customFontPath) {
            resolve(context, settings)
        }
    }

    /** 非 Composable 版本，给测试与后台逻辑用。 */
    fun resolve(context: Context, settings: ThemeSettings): FontFamily {
        val family = ChatFontFamily.entries
            .firstOrNull { it.name == settings.chatFont } ?: ChatFontFamily.DEFAULT
        return when (family) {
            ChatFontFamily.DEFAULT -> FontFamily.Default
            ChatFontFamily.SERIF -> FontFamily.Serif
            ChatFontFamily.MONOSPACE -> FontFamily.Monospace
            ChatFontFamily.CUSTOM -> loadCustom(context, settings.customFontPath) ?: FontFamily.Default
        }
    }

    /**
     * 读自定义字体。
     *
     * 路径安全：`customFontPath` 是从 `SharedPreferences` 读出来的字符串，
     * 理论上可被外部改写（备份恢复、root 用户手改）。所以这里**必须**做越界检查 ——
     * 否则 `../../` 就能让应用去读任意文件并当成字体加载。
     * 做法与 rikkahub 的 `deleteCustomChatFontInternal` 一致：canonical 化之后比前缀。
     */
    private fun loadCustom(context: Context, relativePath: String): FontFamily? {
        if (relativePath.isBlank()) return null
        val file = safeFontFile(context, relativePath) ?: return null
        if (!file.isFile) return null
        return runCatching {
            val typeface = Typeface.createFromFile(file)
            FontFamily(typeface)
        }.getOrNull()
    }

    /**
     * 把相对路径解析成一个"确定在 `filesDir` 之内"的绝对文件。
     *
     * 越界、canonical 失败、路径逃逸都返回 `null` —— 调用方据此回落，
     * 而不是拿着一个来路不明的路径继续 `createFromFile`。
     */
    private fun safeFontFile(context: Context, relativePath: String): File? {
        val filesDir = runCatching { context.filesDir.canonicalFile }.getOrNull() ?: return null
        val candidate = runCatching { File(filesDir, relativePath).canonicalFile }.getOrNull() ?: return null
        val prefix = filesDir.path + File.separator
        return candidate.takeIf { it.path.startsWith(prefix) }
    }

    /**
     * 导入一个字体文件。
     *
     * 返回 `Result`：失败原因要能被界面显示出来（"这个文件不是字体"与
     * "读不到文件"是两件不同的事，用户能据此决定换一个还是重选）。
     */
    fun import(context: Context, uri: Uri, displayName: String): Result<Imported> = runCatching {
        val name = displayName.ifBlank { "custom_font" }
        val extension = name.substringAfterLast('.', "")
            .lowercase()
            .takeIf { it in ALLOWED_EXTENSIONS }
            // 后缀不认识也继续：有些文件选择器给的名字没有扩展名，
            // 真正的判定交给下面的 `Typeface.createFromFile`。
            ?: "ttf"

        val fontDir = File(context.filesDir, FONT_DIR).apply { mkdirs() }
        val target = File(fontDir, "chat_font.$extension")
        val temp = File(fontDir, "chat_font_import.tmp")

        try {
            temp.delete()
            val input = context.contentResolver.openInputStream(uri)
                ?: error("无法打开所选文件")
            input.use { source ->
                temp.outputStream().use { sink -> source.copyTo(sink) }
            }

            // 先验一遍：不是字体就在这里失败，而不是等到渲染时。
            runCatching { Typeface.createFromFile(temp) }.onFailure { error ->
                throw IllegalArgumentException(error.message ?: "这不是一个可用的字体文件", error)
            }

            replace(fontDir, temp, target)
        } catch (error: Throwable) {
            temp.delete()
            throw error
        }

        Imported(relativePath = "$FONT_DIR/${target.name}", displayName = name)
    }

    /**
     * 用 [temp] 原子替换 [target]，失败时把旧文件改名回去。
     *
     * 之所以先全部改成 `previous_*` 再逐个删：`renameTo` 在同一个目录内是原子的，
     * 但"删旧的 + 改新的"是两步。中途失败必须能回到"旧的还在"的状态，
     * 否则用户既没拿到新字体、也丢了原来的。
     */
    private fun replace(fontDir: File, temp: File, target: File) {
        val existing = fontDir.listFiles { file ->
            file.isFile && file.name.startsWith("chat_font.") && file != temp
        }?.toList().orEmpty()
        val backups = existing.map { file ->
            file to File(fontDir, "previous_${file.name}").also { it.delete() }
        }

        try {
            backups.forEach { (file, backup) ->
                check(file.renameTo(backup)) { "无法备份已有字体" }
            }
            check(temp.renameTo(target)) { "无法保存所选字体" }
            backups.forEach { (_, backup) -> backup.delete() }
        } catch (error: Throwable) {
            temp.delete()
            backups.forEach { (file, backup) ->
                if (!file.exists() && backup.exists()) {
                    backup.renameTo(file)
                }
            }
            throw error
        }
    }

    /**
     * 删除自定义字体文件。
     *
     * 路径检查与 [loadCustom] 同一套：这是**删除**，越界的后果比读取更严重 ——
     * 一个错误的相对路径可能删掉 `filesDir` 之外的、本应用根本不该碰的文件。
     */
    fun delete(context: Context, relativePath: String) {
        if (relativePath.isBlank()) return
        safeFontFile(context, relativePath)?.takeIf { it.isFile }?.delete()
    }
}
