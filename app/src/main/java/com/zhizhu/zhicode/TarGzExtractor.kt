package com.zhizhu.zhicode

import android.system.Os
import org.tukaani.xz.XZInputStream
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.zip.GZIPInputStream

/**
 * 纯 Kotlin `.tar.gz` / `.tar.xz` 流式解包器，用于解压云端 Ubuntu 根文件系统与内置 Node/Pi 包。
 */
internal object TarGzExtractor {

    private const val BLOCK_SIZE = 512

    fun interface ProgressCallback {
        fun onProgress(compressedBytesRead: Long)
    }

    private class CountingInputStream(private val delegate: InputStream) : InputStream() {
        var bytesRead: Long = 0L
            private set

        override fun read(): Int {
            val value = delegate.read()
            if (value >= 0) bytesRead++
            return value
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val count = delegate.read(b, off, len)
            if (count > 0) bytesRead += count
            return count
        }
    }

    @Throws(Exception::class)
    fun extract(
        input: InputStream,
        targetRoot: File,
        useXz: Boolean = false,
        stripComponents: Int = 0,
        progress: ProgressCallback? = null,
    ) {
        val counting = CountingInputStream(BufferedInputStream(input, 64 * 1024))
        val rootCanonical = targetRoot.canonicalPath
        val header = ByteArray(BLOCK_SIZE)
        val ioBuffer = ByteArray(64 * 1024)
        var lastReportedBytes = 0L

        var gnuLongName: String? = null
        var gnuLongLink: String? = null
        var paxPath: String? = null
        var paxLinkPath: String? = null
        var paxSize: Long? = null

        val decompressed: InputStream = if (useXz) {
            XZInputStream(counting)
        } else {
            GZIPInputStream(counting, 64 * 1024)
        }

        decompressed.use { tar ->
            while (readFullyOrEof(tar, header)) {
                if (isAllZero(header)) break

                val typeFlag = header[156].toInt().toChar()
                val rawName = parseHeaderName(header)
                val rawLink = parseCString(header, 157, 100)
                val mode = parseOctal(header, 100, 8).toInt()
                val size = paxSize ?: parseOctal(header, 124, 12)

                when (typeFlag) {
                    'L' -> {
                        gnuLongName = readTextEntry(tar, size).trimEnd('\u0000')
                        skipPadding(tar, size)
                        continue
                    }
                    'K' -> {
                        gnuLongLink = readTextEntry(tar, size).trimEnd('\u0000')
                        skipPadding(tar, size)
                        continue
                    }
                    'x' -> {
                        val pax = parsePaxHeaders(readTextEntry(tar, size))
                        paxPath = pax["path"] ?: paxPath
                        paxLinkPath = pax["linkpath"] ?: paxLinkPath
                        paxSize = pax["size"]?.toLongOrNull() ?: paxSize
                        skipPadding(tar, size)
                        continue
                    }
                    'g' -> {
                        skipFully(tar, size)
                        skipPadding(tar, size)
                        continue
                    }
                }

                val entryPath = paxPath ?: gnuLongName ?: rawName
                val linkTarget = paxLinkPath ?: gnuLongLink ?: rawLink
                gnuLongName = null
                gnuLongLink = null
                paxPath = null
                paxLinkPath = null
                paxSize = null

                val stripped = stripPathComponents(entryPath, stripComponents)
                if (stripped.isEmpty()) {
                    if (size > 0) {
                        skipFully(tar, size)
                        skipPadding(tar, size)
                    }
                    continue
                }

                val destination = safeRootPath(rootCanonical, stripped)
                when (typeFlag) {
                    '5' -> {
                        if (!destination.isDirectory && !destination.mkdirs() && !destination.isDirectory) {
                            throw IOException("Cannot create directory: $destination")
                        }
                        if (mode > 0) runCatching { Os.chmod(destination.absolutePath, mode and 0b111111111111) }
                    }
                    '2' -> {
                        destination.parentFile?.mkdirs()
                        deleteExistingEntry(destination)
                        Os.symlink(linkTarget, destination.absolutePath)
                    }
                    '1' -> {
                        // Android SELinux 禁止普通应用调用 link(2)，硬链接转为相对符号链接。
                        destination.parentFile?.mkdirs()
                        deleteExistingEntry(destination)
                        val cleanTarget = stripPathComponents(linkTarget, stripComponents)
                        val rel = relativeSymlinkTarget(stripped, cleanTarget)
                        Os.symlink(rel, destination.absolutePath)
                    }
                    '0', '\u0000' -> {
                        destination.parentFile?.mkdirs()
                        deleteExistingEntry(destination)
                        FileOutputStream(destination).use { out ->
                            var remaining = size
                            while (remaining > 0) {
                                val chunk = tar.read(ioBuffer, 0, minOf(ioBuffer.size.toLong(), remaining).toInt())
                                if (chunk < 0) throw IOException("Unexpected EOF extracting $stripped")
                                out.write(ioBuffer, 0, chunk)
                                remaining -= chunk
                            }
                        }
                        skipPadding(tar, size)
                        val cleanMode = if (mode > 0) mode and 0b111111111 else 0b110100100
                        Os.chmod(destination.absolutePath, cleanMode)
                    }
                    else -> {
                        if (size > 0) {
                            skipFully(tar, size)
                            skipPadding(tar, size)
                        }
                    }
                }

                if (progress != null && counting.bytesRead - lastReportedBytes >= 262_144L) {
                    lastReportedBytes = counting.bytesRead
                    progress.onProgress(counting.bytesRead)
                }
            }
        }
        progress?.onProgress(counting.bytesRead)
    }

    internal fun stripPathComponents(rawPath: String, stripCount: Int): String {
        val segments = rawPath.trimStart('.').trimStart('/').trimEnd('/').split('/').filter { it.isNotEmpty() }
        if (stripCount <= 0) return segments.joinToString("/")
        if (segments.size <= stripCount) return ""
        return segments.drop(stripCount).joinToString("/")
    }

    private fun deleteExistingEntry(target: File) {
        val path = target.toPath()
        if (Files.isSymbolicLink(path)) {
            Files.deleteIfExists(path)
        } else if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            FileTree.deleteRecursive(target)
        } else {
            Files.deleteIfExists(path)
        }
    }

    private fun relativeSymlinkTarget(linkPath: String, targetPath: String): String {
        val linkParts = linkPath.split('/').dropLast(1)
        val targetParts = targetPath.split('/')
        var common = 0
        while (common < linkParts.size && common < targetParts.size && linkParts[common] == targetParts[common]) {
            common++
        }
        val up = List(linkParts.size - common) { ".." }
        val rest = targetParts.drop(common)
        return (up + rest).joinToString("/").ifEmpty { "." }
    }

    @Throws(IOException::class)
    private fun safeRootPath(rootCanonical: String, relative: String): File {
        for (segment in relative.split('/')) {
            if (segment == "." || segment == "..") {
                throw IOException("Unsafe tar entry: $relative")
            }
        }
        val candidate = File(rootCanonical, relative)
        val parentCanonical = candidate.parentFile?.canonicalFile
        val rootFile = File(rootCanonical)
        if (parentCanonical != null && parentCanonical != rootFile &&
            !parentCanonical.path.startsWith(rootCanonical + File.separator)
        ) {
            throw IOException("Unsafe tar parent escape: $relative")
        }
        return candidate
    }

    private fun parseHeaderName(header: ByteArray): String {
        val name = parseCString(header, 0, 100)
        val prefix = parseCString(header, 345, 155)
        return if (prefix.isEmpty()) name else "$prefix/$name"
    }

    private fun parseCString(buf: ByteArray, offset: Int, maxLen: Int): String {
        var end = offset
        val limit = offset + maxLen
        while (end < limit && buf[end] != 0.toByte()) end++
        return String(buf, offset, end - offset, StandardCharsets.UTF_8)
    }

    private fun parseOctal(buf: ByteArray, offset: Int, len: Int): Long {
        if ((buf[offset].toInt() and 0x80) != 0) {
            var value = 0L
            for (i in 1 until len) {
                value = (value shl 8) or (buf[offset + i].toLong() and 0xffL)
            }
            return value
        }
        var result = 0L
        var i = offset
        val end = offset + len
        while (i < end && (buf[i] == ' '.code.toByte() || buf[i] == 0.toByte())) i++
        while (i < end) {
            val b = buf[i].toInt().toChar()
            if (b == ' ' || b == '\u0000') break
            if (b in '0'..'7') {
                result = (result shl 3) or (b - '0').toLong()
            } else {
                break
            }
            i++
        }
        return result
    }

    private fun parsePaxHeaders(text: String): Map<String, String> {
        val out = HashMap<String, String>()
        var idx = 0
        while (idx < text.length) {
            val sp = text.indexOf(' ', idx)
            if (sp <= idx) break
            val eq = text.indexOf('=', sp + 1)
            val nl = text.indexOf('\n', sp + 1)
            val len = text.substring(idx, sp).toIntOrNull()
            if (eq > sp && nl > eq) {
                val key = text.substring(sp + 1, eq)
                val value = text.substring(eq + 1, nl)
                out[key] = value
            }
            idx = if (len != null && len > 0) idx + len else if (nl >= 0) nl + 1 else text.length
        }
        return out
    }

    private fun readTextEntry(stream: InputStream, size: Long): String {
        val bytes = ByteArray(size.toInt())
        var offset = 0
        while (offset < bytes.size) {
            val n = stream.read(bytes, offset, bytes.size - offset)
            if (n < 0) break
            offset += n
        }
        return String(bytes, 0, offset, StandardCharsets.UTF_8)
    }

    private fun skipPadding(stream: InputStream, size: Long) {
        val rem = (size % BLOCK_SIZE).toInt()
        if (rem > 0) skipFully(stream, (BLOCK_SIZE - rem).toLong())
    }

    private fun skipFully(stream: InputStream, total: Long) {
        var left = total
        val buf = ByteArray(8192)
        while (left > 0) {
            val n = stream.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (n < 0) break
            left -= n
        }
    }

    private fun readFullyOrEof(stream: InputStream, buffer: ByteArray): Boolean {
        var offset = 0
        while (offset < buffer.size) {
            val n = stream.read(buffer, offset, buffer.size - offset)
            if (n < 0) return false
            offset += n
        }
        return true
    }

    private fun isAllZero(block: ByteArray): Boolean {
        for (b in block) {
            if (b != 0.toByte()) return false
        }
        return true
    }
}
