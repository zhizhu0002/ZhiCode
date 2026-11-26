package com.zhizhu.zhicode

import com.termux.shared.termux.TermuxConstants
import java.io.File

/**
 * 宿主私有目录与 Debian 容器内虚拟路径的互转。
 */
object ContainerPaths {

    @JvmStatic
    fun rootfsDir(): File = File(TermuxConstants.DEBIAN_ROOTFS_DIR_PATH)

    @JvmStatic
    fun homeDir(): File = File(TermuxConstants.TERMUX_HOME_DIR_PATH)

    @JvmStatic
    fun prootTmpDir(): File = File(TermuxConstants.PROOT_TMP_DIR_PATH)

    /** 宿主路径转换为 Debian 容器内路径，不在容器内但已挂载共享存储的保持原路径。 */
    @JvmStatic
    fun hostToGuest(hostPath: String?): String {
        val raw = hostPath?.trim().orEmpty()
        if (raw.isEmpty()) return "/root"

        val rootfs = rootfsDir().absolutePath.trimEnd('/')
        if (raw == rootfs) return "/"
        if (raw.startsWith("$rootfs/")) {
            return raw.substring(rootfs.length)
        }
        if (raw == "/sdcard" || raw.startsWith("/sdcard/") || raw.startsWith("/storage/emulated/")) {
            return raw
        }
        if (raw.startsWith("/root") || raw.startsWith("/home") || raw.startsWith("/etc") ||
            raw.startsWith("/usr") || raw.startsWith("/var") || raw.startsWith("/tmp")
        ) {
            return raw
        }
        return "/root"
    }

    /** 容器内路径转换为宿主真实文件，若属于共享存储或宿主自有路径则直接返回。 */
    @JvmStatic
    fun guestToHost(guestPath: String?): File {
        val raw = guestPath?.trim().orEmpty()
        if (raw.isEmpty()) return homeDir()
        val rootfs = rootfsDir()
        if (raw == "/") return rootfs
        if (raw.startsWith(rootfs.absolutePath) ||
            raw.startsWith("/storage/emulated/") ||
            raw.startsWith("/sdcard") ||
            raw.startsWith(TermuxConstants.TERMUX_FILES_DIR_PATH)
        ) {
            return File(raw)
        }
        val normalized = raw.trimStart('/')
        return if (normalized.isEmpty()) rootfs else File(rootfs, normalized)
    }
}
