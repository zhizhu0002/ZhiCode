package com.zhizhu.zhicode.compose.data

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import java.io.File

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

    /** 按当前设置解析出 `FontFamily`。 */
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

    /** 读自定义字体。 */
    private fun loadCustom(context: Context, relativePath: String): FontFamily? {
        if (relativePath.isBlank()) return null
        val file = safeFontFile(context, relativePath) ?: return null
        if (!file.isFile) return null
        return runCatching {
            val typeface = Typeface.createFromFile(file)
            FontFamily(typeface)
        }.getOrNull()
    }

    /** 把相对路径解析成一个"确定在 `filesDir` 之内"的绝对文件。 */
    private fun safeFontFile(context: Context, relativePath: String): File? {
        val filesDir = runCatching { context.filesDir.canonicalFile }.getOrNull() ?: return null
        val candidate = runCatching { File(filesDir, relativePath).canonicalFile }.getOrNull() ?: return null
        val prefix = filesDir.path + File.separator
        return candidate.takeIf { it.path.startsWith(prefix) }
    }

    /** 导入一个字体文件。 */
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

    /** 用 [temp] 原子替换 [target]，失败时把旧文件改名回去。 */
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

    /** 删除自定义字体文件。 */
    fun delete(context: Context, relativePath: String) {
        if (relativePath.isBlank()) return
        safeFontFile(context, relativePath)?.takeIf { it.isFile }?.delete()
    }
}
