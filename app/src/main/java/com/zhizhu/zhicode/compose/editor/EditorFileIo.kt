package com.zhizhu.zhicode.compose.editor

import com.termux.app.zhicode.core.FileOps
import io.github.rosemoe.sora.text.Content
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

internal object EditorFileIo {

    /** 单个文件的上限。 */
    private const val MAX_EDIT_BYTES = 8L * 1024 * 1024

    /** 读盘成功的结果：文本模型 + 该文件真正用的编码 + 是否可写。 */
    internal class Loaded(
        val content: Content,
        val charsetName: String,
        val writable: Boolean,
    )

    /** 读盘失败（文件不存在 / 是二进制 / 太大 / 权限不足）。消息是直接给用户看的中文。 */
    internal class Failure(message: String) : Exception(message)

    internal fun load(path: String, forceCharset: String? = null): Loaded {
        val file = File(path)
        if (!file.isFile) throw Failure("文件不存在或不可读：$path")
        val size = runCatching { file.length() }.getOrDefault(-1L)
        if (size < 0) throw Failure("无法读取文件大小：$path")
        if (size > MAX_EDIT_BYTES) {
            throw Failure("文件过大（$size 字节），编辑器最多支持 $MAX_EDIT_BYTES 字节")
        }
        val bytes = runCatching { file.readBytes() }.getOrElse {
            throw Failure("读取失败，可能是权限不足：${it.message ?: "未知原因"}")
        }
        // 含 NUL 的按二进制处理：显示乱码比明说"这是二进制文件"更没用，
        // 而且一旦被"编辑-保存"就会把原文件写坏。
        if (bytes.any { it == 0.toByte() }) {
            throw Failure("这是二进制文件（$size 字节），不能用文本编辑器打开")
        }
        // forceCharset 来自菜单里的「编码」选择（用户在纠正自动识别结果）；
        // 不指定时按 BOM → 严格 UTF-8 → GBK 这一串推定。
        val charset = forceCharset
            ?.let { runCatching { Charset.forName(it) }.getOrNull() }
            ?: detectCharset(bytes)
        return Loaded(Content(String(bytes, charset)), charset.name(), file.canWrite())
    }

    /** 按 charsetName 把文本写回文件。 */
    internal fun save(path: String, text: String, charsetName: String): String? {
        val charset = runCatching { Charset.forName(charsetName) }.getOrDefault(StandardCharsets.UTF_8)
        return FileOps.write(File(path), text, charset)
    }

    /** 先看 BOM，再试**严格** UTF-8，最后回退 GBK（历史中文文本文件的常见编码）。 */
    private fun detectCharset(bytes: ByteArray): Charset {
        val bom = detectBom(bytes)
        if (bom != null) return bom
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(bytes))
            StandardCharsets.UTF_8
        } catch (e: CharacterCodingException) {
            runCatching { Charset.forName("GBK") }.getOrDefault(StandardCharsets.UTF_8)
        }
    }

    private fun detectBom(bytes: ByteArray): Charset? = when {
        bytes.size >= 3 && bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() -> StandardCharsets.UTF_8

        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
            StandardCharsets.UTF_16LE

        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
            StandardCharsets.UTF_16BE

        else -> null
    }

    /** 用户可以手动指定的编码（菜单里的「编码」子菜单）。 */
    internal val selectableCharsets: List<String> = listOf(
        "UTF-8", "GB18030", "GBK", "UTF-16LE", "UTF-16BE", "Big5", "Shift_JIS", "ISO-8859-1",
    ).filter { runCatching { Charset.forName(it) }.isSuccess }
}
