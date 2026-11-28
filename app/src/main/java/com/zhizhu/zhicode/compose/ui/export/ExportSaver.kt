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

    var heldMarkdown by remember { mutableStateOf<ExportPayload?>(null) }
    var heldImage by remember { mutableStateOf<ExportPayload?>(null) }

    /** 把 [request] 的内容写进 [uri]，写不进去就退回应用自己的目录。两种格式共用结尾的提示逻辑。 */
    fun write(uri: Uri, request: ExportPayload) {
        scope.launch {
            val result = runCatching {
                val bytes = encodeExport(request, context, density, theme, scope)
                /* 第一选择：写进 SAF 给的 URI。 */
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

    /* 两种格式各注册一个 launcher。 */
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

    /* 发起 SAF。 */
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
