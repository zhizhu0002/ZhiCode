package com.zhizhu.zhicode.compose.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.zhizhu.zhicode.compose.ui.effects.ZhiSquircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.data.SessionReader
import com.zhizhu.zhicode.compose.model.ChatAttachment
import com.zhizhu.zhicode.compose.model.ChatImage
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import com.zhizhu.zhicode.compose.ui.settings.rememberLastNonNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme


/** 目标边长与降采样倍数。 */
internal fun sampleSizeFor(sourceWidth: Int, sourceHeight: Int, targetWidth: Int, targetHeight: Int): Int {
    if (sourceWidth <= 0 || sourceHeight <= 0) return 1
    if (targetWidth <= 0 || targetHeight <= 0) return 1
    var sample = 1
    // 条件是"仍然偏大"，两边都满足才继续加倍：一张很扁的图不该按高度一直降到糊。
    while (sourceWidth / (sample * 2) >= targetWidth && sourceHeight / (sample * 2) >= targetHeight) {
        sample *= 2
    }
    return sample
}

private class Decoded(val bitmap: ImageBitmap?, val sourceWidth: Int, val sourceHeight: Int) {
    /** 长宽比；尺寸未知时为 null。 */
    val aspect: Float?
        get() = if (sourceWidth > 0 && sourceHeight > 0) sourceWidth.toFloat() / sourceHeight else null
}

/** base64 → 该分辨率下的位图 + 原图尺寸。整体失败时尺寸为 0。 */
private fun decode(base64: String, reqWidth: Int, reqHeight: Int): Decoded {
    val bytes = runCatching { Base64.decode(base64, Base64.DEFAULT) }.getOrNull()
        ?: return Decoded(null, 0, 0)
    if (bytes.isEmpty()) return Decoded(null, 0, 0)

    // 第一遍：只量尺寸，不分配像素。
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return Decoded(null, 0, 0)

    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, reqWidth, reqHeight)
        // RGB_565 每像素 2 字节而不是 4：缩略图上肉眼无差别，内存直接减半。
        inPreferredConfig = Bitmap.Config.RGB_565
    }
    val bitmap = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) }.getOrNull()
    // 即使位图没解出来，尺寸也照带 —— 占位框能按正确比例留好。
    return Decoded(bitmap?.asImageBitmap(), bounds.outWidth, bounds.outHeight)
}

private val imageCache = object : LruCache<String, Decoded>(imageCacheBudgetBytes()) {
    override fun sizeOf(key: String, value: Decoded): Int {
        // 没有位图的条目（解码失败 / 只量到尺寸）没有真实内存占用
        val bitmap = value.bitmap ?: return 1024
        // 记账用 RGB_565 的**真实**占用（2 字节/像素，见 [decode] 里的 inPreferredConfig）。
        return bitmap.height * bitmap.width * 2
    }
}

/** 图片缓存预算：堆的 1/12，最少 8MB、最多 48MB。 */
private fun imageCacheBudgetBytes(): Int {
    val heap = runCatching { Runtime.getRuntime().maxMemory() }.getOrDefault(0L)
    if (heap <= 0L) return 8 * 1024 * 1024
    val twelfth = heap / 12
    return twelfth.coerceIn(8L * 1024 * 1024, 48L * 1024 * 1024).toInt()
}

/** 缓存键：内容本身（base64 很长，取哈希）+ 目标尺寸。 */
private fun cacheKey(data: String, reqWidth: Int, reqHeight: Int): String =
    "${data.hashCode()}:${data.length}:${reqWidth}x$reqHeight"

/** 缓存键：**按图的位置**，而不是按它的字节。 */
private fun cacheKeyFor(image: ChatImage, reqWidth: Int, reqHeight: Int): String {
    val origin = image.origin
    return if (origin == null) cacheKey(image.data, reqWidth, reqHeight)
    else "img:${origin.sessionId}:${origin.messageSeq}:${origin.ordinal}:${reqWidth}x$reqHeight"
}

/** 拿到这张图真正要解码的字节。 */
private suspend fun resolveImageData(image: ChatImage): String {
    if (image.data.isNotEmpty()) return image.data
    val origin = image.origin ?: return ""
    return withContext(Dispatchers.Default) { SessionReader.imageData(origin) }
}

/** 解码一张图（带缓存与降采样）。 */
@Composable
internal fun rememberDecodedImage(image: ChatImage, reqWidth: Int, reqHeight: Int): ImageBitmap? =
    rememberDecodeState(image, reqWidth, reqHeight).bitmap

/** 解码状态。 */
internal class DecodeState(
    val bitmap: ImageBitmap?,
    val failed: Boolean,
    val sourceWidth: Int = 0,
    val sourceHeight: Int = 0,
) {
    /** 原图长宽比；尺寸未知时为 null。 */
    val aspect: Float?
        get() = if (sourceWidth > 0 && sourceHeight > 0) sourceWidth.toFloat() / sourceHeight else null
}

@Composable
internal fun rememberDecodeState(image: ChatImage, reqWidth: Int, reqHeight: Int): DecodeState {
    val key = cacheKeyFor(image, reqWidth, reqHeight)
    // 缓存命中时同步返回，不闪一下占位。
    val cached = remember(key) { imageCache.get(key) }
    val state by produceState(
        initialValue = if (cached != null) DecodeState(cached.bitmap, false, cached.sourceWidth, cached.sourceHeight)
        else DecodeState(null, false),
        key1 = key,
    ) {
        if (cached != null) return@produceState
        // 历史图片的字节不在内存里（见 [ChatImage.origin]）：第一步是把它取回来。
        // 取不到就按失败处理 —— 界面上会显示文件名，而不是永远转圈。
        val data = resolveImageData(image)
        if (data.isEmpty()) {
            value = DecodeState(null, true)
            return@produceState
        }
        val decoded = withContext(Dispatchers.Default) { decode(data, reqWidth, reqHeight) }
        imageCache.put(key, decoded)
        value = DecodeState(decoded.bitmap, decoded.bitmap == null, decoded.sourceWidth, decoded.sourceHeight)
    }
    return state
}

// ------------------------------------------------------------------ 组件

/** 由统一高度和原图长宽比算出这一张的宽度。 */
internal fun thumbWidthFor(height: Float, aspect: Float?, minWidth: Float, maxWidth: Float): Float {
    if (aspect == null || aspect <= 0f || !aspect.isFinite()) return height
    val low = minOf(minWidth, maxWidth)
    val high = maxOf(minWidth, maxWidth)
    return (height * aspect).coerceIn(low, high)
}

@Composable
internal fun ZhiImageThumb(
    image: ChatImage,
    height: Dp,
    maxWidth: Dp,
    minWidth: Dp = ThumbMinWidth,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val scheme = MiuixTheme.colorScheme
    // 传 dp 而不是 px：解码器要的是像素，但这里拿不到 density 以外的信息
    val state = rememberDecodeState(image, (maxWidth.value * 2).toInt(), (height.value * 2).toInt())

    // 宽度按原图长宽比算；尺寸还没量到时先占一个方框。
    val width = thumbWidthFor(height.value, state.aspect, minWidth.value, maxWidth.value).dp

    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(ZhiSquircle(ZhiRadius.inner))
            .background(scheme.surfaceContainerHigh)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = state.bitmap
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = image.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // 解码中与失败都先占位：失败时多一行文件名，用户至少知道这里本来是什么。
            FallbackLabel(name = image.name, failed = state.failed, maxWidth = maxWidth)
        }
    }
}

/** 解码不出来时的兜底：文件名 + （失败时）一行说明。 */
@Composable
private fun FallbackLabel(name: String, failed: Boolean, maxWidth: Dp, modifier: Modifier = Modifier) {
    val scheme = MiuixTheme.colorScheme
    Column(
        modifier = modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = name,
            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = maxWidth - 20.dp),
        )
        if (failed) {
            Text(
                text = "图片无法显示",
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                color = scheme.onSurfaceVariantSummary,
            )
        }
    }
}

// ------------------------------------------------------------------ 输入器里的附件缩略图

/** 输入器里的待发图片：缩略图 + 右上角圆形删除键。 */
@Composable
internal fun ZhiPendingImageChip(
    image: ChatImage,
    onRemove: () -> Unit,
    onClick: (() -> Unit)? = null,
) {
    val scheme = MiuixTheme.colorScheme
    Box(modifier = Modifier.size(PendingImageSize)) {
        ZhiImageThumb(
            image = image,
            height = PendingImageSize,
            maxWidth = PendingImageSize,
            // 上下限都钉在同一个值上 = **固定正方形**。待发区里不需要"按比例不等宽"，
            // 反而必须等宽：删除键压在右上角，宽度一变 X 的位置就会跟着飘。
            minWidth = PendingImageSize,
            onClick = onClick,
        )
        // 删除键：走工程统一的 ZhiFilledIconButton（square = false 时 Miuix 用
        ZhiFilledIconButton(
            icon = ZhiIcons.close,
            description = "移除图片",
            onClick = onRemove,
            containerColor = scheme.surfaceContainerHighest,
            contentColor = scheme.onSurface,
            iconSize = 11.dp,
            size = 20.dp,
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}

/** 输入器里待发图片的边长。与附件条高度同一量级。 */
private val PendingImageSize = 56.dp

// ------------------------------------------------------------------ 放大查看

/** 点缩略图后的放大查看。 */
@Composable
internal fun ZhiImageViewer(image: ChatImage?, onDismiss: () -> Unit) {
    // 退场时 `image` 已经是 null，内容不能跟着空掉（见 rememberLastNonNull）。
    val shownImage = rememberLastNonNull(image)
    OverlayDialog(
        show = image != null,
        onDismissRequest = onDismiss,
        // 全屏：外边距 / 内边距 / 圆角全清零，背景纯黑。
        maxWidth = ViewerMaxDimension,
        outsideMargin = ViewerZeroInsets,
        insideMargin = ViewerZeroInsets,
        cornerRadius = 0.dp,
        backgroundColor = Color.Black,
        enableWindowDim = false,
        // 状态栏/导航栏的避让由下面自己的 padding 负责：全屏看图时
        // 内容顶到状态栏下面才是"全屏"，× 按钮单独避让即可。
        defaultWindowInsetsPadding = false,
    ) {
        val shown = shownImage ?: return@OverlayDialog
        // 放大查看不做降采样上限（按 2048 解），但仍是 Fit：长截图在屏幕上
        // 本来就只能看到缩略形态，硬裁会把内容藏起来。
        val state = rememberDecodeState(shown, 2048, 2048)
        val bitmap = state.bitmap
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                // 避让状态栏：× 按钮不能顶进刘海/挖孔里。
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = shown.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                FallbackLabel(
                    name = shown.name,
                    failed = state.failed,
                    maxWidth = ViewerMaxDimension,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            ZhiFilledIconButton(
                icon = ZhiIcons.close,
                description = "关闭图片",
                onClick = onDismiss,
                containerColor = Color.White.copy(alpha = 0.14f),
                contentColor = Color.White,
                iconSize = 16.dp,
                size = 40.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 12.dp),
            )
        }
    }
}

/** 全屏查看层的尺寸上限（够大，实际都会被屏幕夹住）。 */
private val ViewerMaxDimension = 4096.dp

/** 全屏查看层的边距/内边距清零。 */
private val ViewerZeroInsets = DpSize(0.dp, 0.dp)

// ------------------------------------------------------------------ 气泡里的图片行

@Composable
internal fun ZhiImageRow(images: List<ChatImage>, onOpen: (ChatImage) -> Unit, modifier: Modifier = Modifier) {
    if (images.isEmpty()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        images.forEach { image ->
            ZhiImageThumb(
                image = image,
                height = BubbleImageHeight,
                maxWidth = BubbleImageMaxWidth,
                onClick = { onOpen(image) },
            )
        }
    }
}

@Composable
internal fun ZhiAttachmentCard(
    attachment: ChatAttachment,
    modifier: Modifier = Modifier,
) {
    val scheme = MiuixTheme.colorScheme
    Row(
        modifier = modifier
            .clip(ZhiSquircle(ZhiRadius.inner))
            .background(scheme.surfaceContainerHigh)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(ZhiSquircle(ZhiRadius.inner))
                .background(scheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = ZhiIcons.file,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(15.dp),
            )
        }
        Column(
            modifier = Modifier.padding(start = 8.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = attachment.label,
                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 150.dp),
            )
            if (attachment.detail.isNotBlank()) {
                Text(
                    text = attachment.detail,
                    fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                    color = scheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 150.dp),
                )
            }
        }
    }
}

@Composable
internal fun ZhiAttachmentRow(
    attachments: List<ChatAttachment>,
    images: List<ChatImage>,
    onOpenImage: (ChatImage) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (attachments.isEmpty() && images.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth()) {
        if (attachments.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                attachments.forEach { attachment ->
                    ZhiAttachmentCard(
                        attachment = attachment,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        if (images.isNotEmpty()) {
            ZhiImageRow(images = images, onOpen = onOpenImage)
        }
    }
}

/** 气泡里缩略图的**统一高度**。 */
private val BubbleImageHeight = 140.dp

/** 单张缩略图的宽度上限。 */
private val BubbleImageMaxWidth = 260.dp

/** 单张缩略图的宽度下限。 */
private val ThumbMinWidth = 72.dp
