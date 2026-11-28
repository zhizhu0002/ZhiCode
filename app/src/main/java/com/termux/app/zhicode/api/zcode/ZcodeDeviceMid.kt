package com.termux.app.zhicode.api.zcode

import android.content.Context
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID

object ZcodeDeviceMid {

    private const val FILE_NAME = "device-mid"

    @Volatile
    private var appContext: Context? = null

    /** 进程内缓存：同一进程里每次读取都返回同一个值，避免同一会话里标识跳变。 */
    @Volatile
    private var cached: String? = null

    /** 启动时注入应用 Context。在 `ZhiCodeApplication.attachBaseContext` 里调用。 */
    fun install(context: Context) {
        appContext = context.applicationContext
    }

    /** 取本安装的标识，必要时生成并落盘。 */
    fun get(): String {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val context = appContext
            val loaded = context?.let { readFromDisk(it) }
            val value = loaded?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
            if (context != null && loaded == null) writeToDisk(context, value)
            cached = value
            return value
        }
    }

    /** 测试用：清掉状态，让下一次 [get] 重新走一遍。 */
    fun resetForTest() {
        cached = null
        appContext = null
    }

    private fun file(context: Context): File = File(File(context.filesDir, "zcode"), FILE_NAME)

    private fun readFromDisk(context: Context): String? {
        val file = file(context)
        if (!file.isFile) return null
        return runCatching { file.readBytes().toString(StandardCharsets.UTF_8).trim() }.getOrNull()
    }

    private fun writeToDisk(context: Context, value: String) {
        // 写失败不抛：标识拿不到只是"少一个头"，不该让协议整体不可用。
        runCatching {
            val file = file(context)
            file.parentFile?.mkdirs()
            file.writeBytes(value.toByteArray(StandardCharsets.UTF_8))
        }
    }
}
