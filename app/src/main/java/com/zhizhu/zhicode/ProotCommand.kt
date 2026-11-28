package com.zhizhu.zhicode

import android.content.Context
import android.os.Environment
import android.system.Os
import java.io.File

/** 组装在 Android 上通过 `proot` 启动 Debian 容器的命令与环境变量。 */
object ProotCommand {

    const val DEFAULT_PATH = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

    @JvmStatic
    fun prootBinary(context: Context): File =
        File(context.applicationInfo.nativeLibraryDir, "libproot.so")

    @JvmStatic
    fun loaderBinary(context: Context): File =
        File(context.applicationInfo.nativeLibraryDir, "libprootloader.so")

    @JvmStatic
    fun ensureProotTmpDir(): File {
        val dir = ContainerPaths.prootTmpDir()
        if (!dir.isDirectory) dir.mkdirs()
        runCatching { Os.chmod(dir.absolutePath, 0b111000000) }
        return dir
    }

    /** 构建执行任意命令的完整 `libproot.so` 参数列表。 */
    @JvmStatic
    @JvmOverloads
    fun buildArgs(
        context: Context,
        guestCwd: String?,
        command: List<String>,
        extraGuestEnv: Map<String, String> = emptyMap(),
        interactive: Boolean = false,
    ): List<String> {
        val rootfs = ContainerPaths.rootfsDir()
        val resolvedCwd = ContainerPaths.hostToGuest(guestCwd)
        val args = ArrayList<String>(36 + command.size + extraGuestEnv.size)

        args += prootBinary(context).absolutePath
        args += "-0"
        args += "--link2symlink"
        args += "-r"
        args += rootfs.absolutePath

        for (sysBind in listOf("/dev", "/proc", "/sys")) {
            args += "-b"
            args += sysBind
        }
        if (File("/dev/urandom").exists()) {
            args += "-b"
            args += "/dev/urandom:/dev/random"
        }

        val sharedStorage = Environment.getExternalStorageDirectory()
        if (sharedStorage != null && sharedStorage.isDirectory) {
            args += "-b"
            args += "${sharedStorage.absolutePath}:${sharedStorage.absolutePath}"
        }
        val sdcard = File("/sdcard")
        if (sdcard.exists()) {
            args += "-b"
            args += "/sdcard:/sdcard"
        }

        // Frida 缓存与产物目录单独挂进容器，避免与整个 filesDir 嵌套冲突。
        val fridaDir = File(context.filesDir, "sandbox_frida")
        if (fridaDir.isDirectory || fridaDir.mkdirs()) {
            args += "-b"
            args += "${fridaDir.absolutePath}:${fridaDir.absolutePath}"
        }

        args += "-w"
        args += resolvedCwd

        // 通过 env -i 隔离宿主 Android 环境变量（如 LD_LIBRARY_PATH / CLASSPATH）。
        args += "/usr/bin/env"
        args += "-i"
        args += "HOME=/root"
        args += "USER=root"
        args += "TERM=xterm-256color"
        args += "COLORTERM=truecolor"
        args += "LANG=C.UTF-8"
        args += "LC_ALL=C.UTF-8"
        args += "TMPDIR=/tmp"
        args += "SHELL=/bin/bash"
        args += "PATH=$DEFAULT_PATH"
        args += "ZHICODE_APP=1"
        args += "ZHICODE_CONTAINER=ubuntu"
        args += if (interactive) "ZHICODE_TERMINAL_SESSION=1" else "CI=true"
        for ((k, v) in extraGuestEnv) {
            args += "$k=$v"
        }
        args.addAll(command)
        return args
    }

    /** 宿主侧 ProcessBuilder 所需的 `PROOT_*` 环境变量。 */
    @JvmStatic
    fun applyHostEnv(context: Context, env: MutableMap<String, String>) {
        env["PROOT_LOADER"] = loaderBinary(context).absolutePath
        env["PROOT_TMP_DIR"] = ensureProotTmpDir().absolutePath
        // 清理宿主进程可能继承的库路径，避免干扰 proot 本身。
        env.remove("LD_LIBRARY_PATH")
        env.remove("LD_PRELOAD")
    }

    /** 宿主侧 `envp` 数组，供 PTY (`forkpty`) 使用。 */
    @JvmStatic
    fun hostEnvArray(context: Context): Array<String> = arrayOf(
        "PROOT_LOADER=${loaderBinary(context).absolutePath}",
        "PROOT_TMP_DIR=${ensureProotTmpDir().absolutePath}",
        "TERM=xterm-256color",
        "COLORTERM=truecolor",
        "LANG=C.UTF-8",
    )
}
