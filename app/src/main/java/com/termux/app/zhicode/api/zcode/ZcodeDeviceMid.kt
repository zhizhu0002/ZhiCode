package com.termux.app.zhicode.api.zcode

import android.content.Context
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * 本应用的「设备标识」（`X-Device-Mid`）。
 *
 * ## 为什么必须有它（不是可选的装饰）
 *
 * 缺这个头时，那个网关的额度接口会直接拒成 **`parameter error`**。这不是猜的 ——
 * 官方客户端源码里就是这么写的（`packages/server/src/stdioDeviceMid.ts` 的注释）：
 * 远端工作区过去没有任何进程写设备身份文件，于是 "billing/balance 请求因缺
 * X-Device-Mid 被服务端拒绝为 parameter error，远程工作区看不到 Start Plan 模型"。
 * 我们的额度卡片此前一直显示 `3001 parameter error`，就是同一个原因。
 *
 * ## 它是什么、不是什么
 *
 * - **是**：一个随机 UUID，用来让服务端把同一台设备的多次请求认成同一个来源。
 *   官方客户端也是这么做的（`createUuid()` 写进 `~/.zcode/v2/telemetry-state.json`）。
 * - **不是**：硬件指纹。它不由 IMEI / ANDROID_ID / serial / 机型 / 系统版本推导，
 *   卸载重装即变，也没有任何跨应用可见性。
 *
 * ## 两处刻意的不照做
 *
 * 1. **不共用另一个应用的文件**。IQ Code 把标识存在自己的 `filesDir/zcode/device.mid`；
 *    去读那个文件等于借用另一个产品的安装身份，而这个值本来就是"每次安装一个"的东西。
 * 2. **不让用户手填**。它能由代码稳定生成，让用户填只会填错（填成空、填成机型名），
 *    而填错的后果是又一次看不懂的 400。官方客户端同样不把它暴露给用户。
 *
 * ## 为什么是 [install] + [get] 而不是处处传 Context
 *
 * 取它的地方（provider、模型目录）现在都没有 Context：`ModelProviders.forConfig`
 * 是纯静态工厂，传 Context 进去要动一整条构造链。而本工程的既有做法就是
 * 「进程级单例 + 启动时注入」——`TermuxConstants.configure(base)`、
 * `SandboxConsole.init(base)` 都是这个形状，这里跟它一致。
 *
 * **没 [install] 过时**回退到进程内的临时值：能发出去、能工作，只是重启后会换一个。
 * 那种情况下额度可能认不出来 —— 所以 `ZhiCodeApplication` 必须调用 [install]，
 * 这一点有守卫钉住（`ZcodeProtocolTest`）。
 */
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

    /**
     * 取本安装的标识，必要时生成并落盘。
     *
     * 读文件失败（IO 异常、被清数据）时退到进程内值 —— 为了一个标识让整个协议不可用
     * 是不划算的。
     */
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
