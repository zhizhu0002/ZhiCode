package com.zhizhu.zhicode.compose.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

internal object AttachmentReader {

    /** 单张图片上限。 */
    const val MAX_IMAGE_BYTES = 10 * 1024 * 1024

    /** 读取上限（比限制大一档，用来区分"刚好超限"和"读不完"）。 */
    private const val READ_LIMIT = 12 * 1024 * 1024

    class Image(val name: String, val mimeType: String, val bytes: ByteArray) {
        val sizeLabel: String get() = humanSize(bytes.size)
        /** 从 MIME 推导的短标签，用于附件条上的「PNG · 2.4 MB」。 */
        val formatLabel: String get() = mimeType.substringAfter('/', "").uppercase().ifEmpty { "图片" }
    }

    fun readImage(context: Context, uri: Uri): Result<Image> = runCatching {
        val resolver = context.contentResolver
        val detected = resolver.getType(uri)

        val bytes = resolver.openInputStream(uri)?.use { input ->
            val buffer = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(64 * 1024)
            var total = 0
            while (true) {
                val read = input.read(chunk)
                if (read <= 0) break
                total += read
                if (total > READ_LIMIT) throw IllegalArgumentException("图片超过 ${humanSize(MAX_IMAGE_BYTES)}，请选择较小图片")
                buffer.write(chunk, 0, read)
            }
            buffer.toByteArray()
        } ?: throw IllegalArgumentException("无法读取所选图片")

        if (bytes.isEmpty()) throw IllegalArgumentException("所选图片内容为空")
        if (bytes.size > MAX_IMAGE_BYTES) {
            throw IllegalArgumentException("图片超过 ${humanSize(MAX_IMAGE_BYTES)}，请选择较小图片")
        }
        Image(name = displayName(context, uri), mimeType = resolveMimeType(detected, bytes), bytes = bytes)
    }

    /** 定出真实的 MIME 类型。 */
    private fun resolveMimeType(declared: String?, bytes: ByteArray): String {
        if (!declared.isNullOrBlank() && declared.startsWith("image/")) return declared
        return sniffMimeType(bytes) ?: "image/jpeg"
    }

    /** 按文件头判断图片类型。只认几种最常见的，判不出来就交给调用方兜底。 */
    private fun sniffMimeType(bytes: ByteArray): String? {
        fun startsWith(vararg prefix: Int): Boolean {
            if (bytes.size < prefix.size) return false
            return prefix.withIndex().all { (index, value) -> (bytes[index].toInt() and 0xFF) == value }
        }
        return when {
            startsWith(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> "image/png"
            startsWith(0xFF, 0xD8, 0xFF) -> "image/jpeg"
            startsWith(0x47, 0x49, 0x46, 0x38) -> "image/gif"
            // WebP: "RIFF" .... "WEBP"
            startsWith(0x52, 0x49, 0x46, 0x46) && bytes.size >= 12 &&
                bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() &&
                bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte() -> "image/webp"
            startsWith(0x42, 0x4D) -> "image/bmp"
            // HEIC/HEIF: 第 4..7 字节是 "ftyp"
            bytes.size >= 12 && bytes[4] == 0x66.toByte() && bytes[5] == 0x74.toByte() &&
                bytes[6] == 0x79.toByte() && bytes[7] == 0x70.toByte() -> "image/heic"
            else -> null
        }
    }

    /** 取显示名。 */
    private fun displayName(context: Context, uri: Uri): String {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        val name = cursor.getString(index)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "图片"
    }

    private fun humanSize(bytes: Int): String = when {
        bytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
        bytes >= 1024 -> String.format(java.util.Locale.US, "%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
