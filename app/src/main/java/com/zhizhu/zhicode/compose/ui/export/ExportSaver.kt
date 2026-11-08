package com.zhizhu.zhicode.compose.ui.export

import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.model.ExportKind
import com.zhizhu.zhicode.compose.model.ExportPayload
import com.zhizhu.zhicode.compose.theme.LocalThemeSettings
import com.zhizhu.zhicode.compose.theme.findActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 导出的**唯一落盘入口**。
 *
 * ## 为什么用 SAF（`ACTION_CREATE_DOCUMENT`）而不是系统分享
 *
 * 之前走的是 `ACTION_SEND` + androidx FileProvider + 分享面板。在这台设备上
 * 反复失败，而且每次断的地方都不一样：provider authority 冲突、URI 没授权、
 * MIUI 分享面板解析不了自研 URI（点任何应用都没反应）。
 *
 * SAF 的方向正好相反 —— **是系统把 URI 交给我们**并自己完成授权，我们只负责
 * `contentResolver.openOutputStream(uri).write(...)`。这是 ContentResolver
 * 最基本的用法：不需要权限声明、不需要 provider、不经过分享面板，
 * 也不涉及后台启动 Activity（文件管理器由系统自己弹出）。
 *
 * ## 两种格式的差异
 *
 * - **Markdown**：全文已经在状态层拼好了（[ExportPayload.markdown]），
 *   这里只把字节写进流，纯 IO，可以放 `Dispatchers.IO`。
 * - **图片**：必须先渲染。`BitmapComposer` 要真实 Activity 和一个独立
 *   composition，只能在主线程做，所以这一段留在默认调度器上。
 *
 * @param payload    要落盘的内容（null = 没有请求）。
 * @param onConsumed 已经接管请求，让状态层清空它。
 * @param onDone     结果回报（文案 + 是否是错误），由调用方显示成提示条。
 */
@Composable
internal fun ExportSaver(
    payload: ExportPayload?,
    onConsumed: () -> Unit,
    onDone: (String, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val theme = LocalThemeSettings.current

    // 回调里要用的最新值。用 `rememberUpdatedState` 而不是直接闭包捕获：
    // launcher 的 onResult 在**注册时**就创建好了，直接捕获会拿到旧的 onDone。
    val latestOnDone by rememberUpdatedState(onDone)

    /*
     * SAF 回调时要写的那份内容。
     *
     * 为什么不能直接用 `payload`：拿到 URI 时状态层已经被 `onConsumed()` 清空了
     * （这一步是必须的 —— 否则旋转屏幕会再弹一次文件管理器）。所以启动前把内容
     * 存进槽位，回调里读它、读完清掉。
     *
     * 🔴 两种格式**各一个槽位**，不能共用一个：共用时先导 Markdown、再导图片，
     * 第二次会把槽位覆盖，而第一次的文件管理器若还开着，回来的回调就会把
     * PNG 写进本该是 .md 的那个 URI。分开之后两条路彻底不相干。
     * 同一种格式不会自我重叠 —— SAF 是全屏模态，第二次点击要等第一次结束。
     */
    var heldMarkdown by remember { mutableStateOf<ExportPayload?>(null) }
    var heldImage by remember { mutableStateOf<ExportPayload?>(null) }

    /** 把 [request] 的内容写进 [uri]，写不进去就退回应用自己的目录。两种格式共用结尾的提示逻辑。 */
    fun write(uri: Uri, request: ExportPayload) {
        scope.launch {
            val result = runCatching {
                val bytes = encodeExport(request, context, density, theme, scope)
                /*
                 * 第一选择：写进 SAF 给的 URI。
                 *
                 * 🔴 这一步在**沙箱里会失败**，而且症状极具误导性：
                 *   java.lang.IllegalArgumentException: No content provider:
                 *   content://com.android.providers.downloads.documents/document/608
                 *
                 * 原因不是权限、也不是 URI 失效 —— 是 BlackBox 把 guest 的
                 * ContentResolver 虚拟化了：`resolveContentProvider` 只查得到
                 * guest 自己安装的包，真机上的系统 provider（Downloads / MediaStore /
                 * 任何文件管理器）在 guest 看来**不存在**。所以这不是"调用外部软件
                 * 被拒"，而是"压根解析不到那个 provider"。
                 *
                 * 因此必须有第二条路 —— 见 [saveToAppDir]。它在两个环境里都成立：
                 * guest 的外部私有目录与宿主是**同一个真实路径**，
                 * 不受 BlackBox 的 dataDir 重映射影响。
                 */
                runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                            ?: error("无法写入所选位置")
                    }
                    "已保存"
                }.getOrElse { uriError ->
                    android.util.Log.w(
                        "ZhiCodeExport",
                        "SAF 目标不可写，退回应用目录：$uri / ${uriError.message}",
                    )
                    withContext(Dispatchers.IO) { saveToAppDir(context, request, bytes) }
                }
            }
            result
                .onSuccess { latestOnDone(it, false) }
                .onFailure { latestOnDone("导出失败：" + (it.message ?: "未知原因"), true) }
        }
    }

    /*
     * 两种格式各注册一个 launcher。
     *
     * 不能共用一个然后动态换 mimeType：`rememberLauncherForActivityResult` 内部是
     * `remember { ManagedActivityResultLauncher(registry, contract, ...) }` ——
     * contract 被 remember **固定**在首次组合时，之后传新的也不会生效。
     * 共用一个的话，先导 Markdown 再导图片，文件管理器仍然按 `text/markdown`
     * 处理，存出来的 PNG 会被冠上错误的 MIME。
     */
    val markdownSaver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown"),
    ) { uri ->
        val request = heldMarkdown
        heldMarkdown = null
        // uri == null 是用户点了取消：不是错误，安静返回（弹一条"已取消"只会烦人）。
        if (uri != null && request != null) write(uri, request)
    }

    val imageSaver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png"),
    ) { uri ->
        val request = heldImage
        heldImage = null
        if (uri != null && request != null) write(uri, request)
    }

    /*
     * 发起 SAF。
     *
     * 🔴 key 必须是 `nonce` 而不是 `payload`：`onConsumed()` 会把 payload 清成 null，
     * 拿整个对象当 key 时 effect 会立刻重启 —— 上一次的启动被打断，
     * 报错是 `The coroutine scope left the composition`（踩过）。
     * `nonce` 只在"新的一次导出"时变，清空不会让它变。
     */
    LaunchedEffect(payload?.nonce) {
        val request = payload ?: return@LaunchedEffect
        onConsumed()
        when (request.kind) {
            ExportKind.MARKDOWN -> {
                heldMarkdown = request
                markdownSaver.launch(request.suggestedName)
            }

            ExportKind.IMAGE -> {
                heldImage = request
                imageSaver.launch(request.suggestedName)
            }
        }
    }
}

/**
 * 把 [request] 渲染成要落盘的字节。
 *
 * ## 为什么先算字节、再决定往哪写
 *
 * 落盘有两条路（SAF 给的 URI / 应用私有目录）。如果每条路各自渲染一次，
 * 图片那条会渲染两遍 —— 慢一倍，而且两次的像素可能因为流式内容已经变化而不一致，
 * 于是"兜底存的"和"本来要存的"不是同一张图。
 *
 * ## 为什么放在文件级而不是组合里的局部函数
 *
 * Kotlin 的局部函数**不能前向引用**：[ExportSaver] 里的 `write` 调它，
 * 它就必须声明在 `write` 之前。放在文件级之后顺序不再重要，
 * 而且这几个参数本来就是它唯一的输入，不需要捕获组合作用域。
 */
private suspend fun encodeExport(
    request: ExportPayload,
    context: android.content.Context,
    density: androidx.compose.ui.unit.Density,
    theme: com.zhizhu.zhicode.compose.data.ThemeSettings,
    scope: kotlinx.coroutines.CoroutineScope,
): ByteArray = when (request.kind) {
    ExportKind.MARKDOWN -> request.markdown.toByteArray()

    ExportKind.IMAGE -> {
        val activity = context.findActivity() ?: error("找不到当前界面")
        val bitmap = BitmapComposer.composableToBitmap(
            activity = activity,
            width = 540.dp,
            // 高度交给内容决定：对话长短差别很大，写死会把长对话截断。
            height = null,
            density = density,
            scope = scope,
        ) {
            ExportedChatImage(
                items = request.items,
                title = request.title,
                theme = theme,
                includeReasoning = request.includeReasoning,
                expandReasoning = request.expandReasoning,
            )
        }
        try {
            withContext(Dispatchers.IO) {
                ByteArrayOutputStream().use { out ->
                    // PNG 无损，quality 被忽略；给 100 只是表明意图。
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    out.toByteArray()
                }
            }
        } finally {
            // 图片可能几 MB，不主动回收会一直占着直到 GC 决定动手。
            bitmap.recycle()
        }
    }
}

/**
 * 兜底落盘：写进本应用的**外部私有目录**，并返回一句能照做的提示。
 *
 * ## 为什么是这里
 *
 * | 位置 | 沙箱里可用？ | 用户找得到？ |
 * |---|---|---|
 * | SAF URI（系统文件管理器选的） | ❌ provider 解析不到 | ✅ |
 * | `Download/`（直接写文件） | ⚠️ 受分区存储限制 | ✅ |
 * | `getExternalFilesDir()` | ✅ 不需要任何权限 | ⚠️ 路径较深，但文件管理器能看到 |
 * | `filesDir` | ✅ | ❌ 在 `/data/data` 下，非 root 读不到 |
 *
 * 选第三行：它是唯一一个**在两个环境里行为一致**的位置。guest 的外部私有目录
 * 与宿主是同一个真实路径（BlackBox 只重映射 `/data/data`，不动
 * `/storage/emulated/0/Android/data/<pkg>`），所以导出在沙箱里也能成功。
 *
 * ## 为什么返回文案要带完整路径
 *
 * 落盘位置与用户刚才选的不一样，这必须说清 —— 只说"已保存"会让人去 Download
 * 里翻，找不到就以为又失败了。
 */
private fun saveToAppDir(
    context: android.content.Context,
    request: ExportPayload,
    bytes: ByteArray,
): String {
    val suffix = if (request.kind == ExportKind.IMAGE) ".png" else ".md"
    val base = request.suggestedName.substringBeforeLast('.').ifEmpty { "export" }
    // 外部私有不总是可用（存储被卸载/未挂载）。挂不上就退回内部目录：
    // 路径更难找，但"存下来了"永远比"什么都没存"有用。
    val root = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        ?: File(context.filesDir, "exports").apply { mkdirs() }
    if (!root.isDirectory && !root.mkdirs() && !root.isDirectory) {
        error("无法创建导出目录：${root.absolutePath}")
    }
    val target = uniqueFile(root, base, suffix)
    target.writeBytes(bytes)
    if (!target.isFile || target.length() != bytes.size.toLong()) {
        error("导出文件写入不完整：${target.absolutePath}")
    }
    return "已保存到 ${target.absolutePath}"
}

/** 重名时加 `-1`、`-2`…，不覆盖用户已有的文件。 */
private fun uniqueFile(dir: File, base: String, suffix: String): File {
    var candidate = File(dir, base + suffix)
    var index = 1
    while (candidate.exists()) {
        candidate = File(dir, "$base-$index$suffix")
        index++
        if (index > 999) break
    }
    return candidate
}
