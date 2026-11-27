package com.zhizhu.zhicode.pi

import android.content.Context
import com.zhizhu.zhicode.ContainerPaths
import java.io.File
import java.security.MessageDigest

internal object PiBridgeFiles {
    private const val BRIDGE_ASSET = "pi/zhicode-bridge.ts"

    private val legacyCommands = mapOf(
        "zhi-root" to "cabff824a35c2d4ab5fed67ac5f96e716ee98c46a6b73e5749bed577debe6a2e",
        "zhi-shizuku" to "5eb78bd00450b777beb4c482d2a06a7b7606f63bb1330bd376dc2e76c3d0b7a0",
        "zhi-open" to "e4fc72c2c7a3e9110e8d915839fbcd8c9c3f2ec45b26097f2eaf3d69034bbdf8",
    )

    fun bridgeExtensionGuestPath(): String = "/etc/zhicode/zhicode-bridge.ts"

    @Synchronized
    fun ensureInstalled(context: Context) {
        val rootfs = ContainerPaths.rootfsDir()
        if (!File(rootfs, "bin/bash").exists()) return

        val assetBytes = context.assets.open(BRIDGE_ASSET).use { it.readBytes() }
        writeIfChanged(File(rootfs, "etc/zhicode/zhicode-bridge.ts"), assetBytes)
        removeLegacyCommands(rootfs)
    }

    internal fun removeLegacyCommands(rootfs: File) {
        val bin = File(rootfs.canonicalFile, "usr/local/bin")
        if (bin.canonicalFile != bin) return
        for ((name, expectedHash) in legacyCommands) {
            val file = File(bin, name)
            runCatching {
                if (file.canonicalFile != file || !file.isFile || file.length() > 4096) return@runCatching
                val hash = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
                    .joinToString("") { "%02x".format(it) }
                if (hash == expectedHash) file.delete()
            }
        }
    }

    private fun writeIfChanged(target: File, bytes: ByteArray) {
        target.parentFile?.mkdirs()
        if (target.isFile && target.length() == bytes.size.toLong() && target.readBytes().contentEquals(bytes)) return
        val tmp = File(target.parentFile, ".${target.name}.tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) {
            target.writeBytes(bytes)
            tmp.delete()
        }
    }
}
