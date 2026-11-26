package com.zhizhu.zhicode

import android.system.Os
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream

/**
 * 纯 Kotlin `.tar.gz` 流式解包器，用于解压内置 Debian 根文件系统。
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
    fun extract(input: InputStream, targetRoot: File, progress: ProgressCallback? = null) {
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

        GZIPInputStream(counting, 64 * 1024).use { tar ->
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

                val cleanPath = entryPath.trimStart('.').trimStart('/').trimEnd('/')
                if (cleanPath.isEmpty()) {
                    if (size > 0) {
                        skipFully(tar, size)
                        skipPadding(tar, size)
                    }
                    continue
                }

                val destination = safeRootPath(rootCanonical, cleanPath)
                when (typeFlag) {
                    '5' -> {
                        if (!destination.isDirectory && !destination.mkdirs() && !destination.isDirectory) {
                            throw IOException("Cannot create directory: $destination")
                        }
                        if (mode > 0) runCatching { Os.chmod(destination.absolutePath, mode and 0b111111111111) }
                    }
                    '2' -> {
                        destination.parentFile?.mkdirs()
                        if (destination.exists()) destination.delete()
                        Os.symlink(linkTarget, destination.absolutePath)
                    }
                    '1' -> {
                        // Android SELinux 禁止普通应用调用 link(2)，硬链接转为相对符号链接。
                        destination.parentFile?.mkdirs()
                        if (destination.exists()) destination.delete()
                        val cleanTarget = linkTarget.trimStart('.').trimStart('/')
                        val rel = relativeSymlinkTarget(cleanPath, cleanTarget)
                        Os.symlink(rel, destination.absolutePath)
                    }
                    '0', '\u0000' -> {
                        destination.parentFile?.mkdirs()
                        if (destination.exists()) destination.delete()
                        FileOutputStream(destination).use { out ->
                            var remaining = size
                            while (remaining > 0) {
                                val chunk = tar.read(ioBuffer, 0, minOf(ioBuffer.size.toLong(), remaining).toInt())
                                if (chunk < 0) throw IOException("Unexpected EOF extracting $cleanPath")
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
            }
            i++
        }
        return result
    }

    private fun parsePaxHeaders(text: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (line in text.split('\n')) {
            val space = line.indexOf(' ')
            if (space <= 0 || space + 1 >= line.length) continue
            val rest = line.substring(space + 1)
            val eq = rest.indexOf('=')
            if (eq <= 0) continue
            out[rest.substring(0, eq)] = rest.substring(eq + 1)
        }
        return out
    }

    private fun readTextEntry(input: InputStream, size: Long): String {
        val out = ByteArrayOutputStream(size.toInt().coerceAtLeast(64))
        val buf = ByteArray(4096)
        var remaining = size
        while (remaining > 0) {
            val read = input.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
            if (read < 0) throw IOException("Unexpected EOF in tar metadata")
            out.write(buf, 0, read)
            remaining -= read
        }
        return String(out.toByteArray(), StandardCharsets.UTF_8)
    }

    private fun skipPadding(input: InputStream, size: Long) {
        val rem = (BLOCK_SIZE - (size % BLOCK_SIZE).toInt()) % BLOCK_SIZE
        if (rem > 0) skipFully(input, rem.toLong())
    }

    private fun skipFully(input: InputStream, bytes: Long) {
        var remaining = bytes
        val buf = ByteArray(4096)
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else {
                val read = input.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                if (read < 0) throw IOException("Unexpected EOF while skipping")
                remaining -= read
            }
        }
    }

    private fun readFullyOrEof(input: InputStream, buffer: ByteArray): Boolean {
        var offset = 0
        while (offset < buffer.size) {
            val read = input.read(buffer, offset, buffer.size - offset)
            if (read < 0) {
                if (offset == 0) return false
                throw IOException("Truncated tar header")
            }
            offset += read
        }
        return true
    }

    private fun isAllZero(block: ByteArray): Boolean {
        for (b in block) if (b != 0.toByte()) return false
        return true
    }
}
