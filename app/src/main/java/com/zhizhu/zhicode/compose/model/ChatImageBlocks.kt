package com.zhizhu.zhicode.compose.model

import org.json.JSONArray

/** 一张存在会话库里的图的位置。 */
data class ImageOrigin(
    val sessionId: String,
    val messageSeq: Long,
    val ordinal: Int,
)

internal fun readChatImageBlocks(
    content: JSONArray,
    origin: ((Int) -> ImageOrigin?)? = null,
): List<ChatImage> {
    val images = mutableListOf<ChatImage>()
    for (i in 0 until content.length()) {
        val block = content.optJSONObject(i) ?: continue
        if (block.optString("type", "") != "image") continue
        val source = block.optJSONObject("source") ?: continue
        if (source.optString("type", "") != "base64") continue
        val data = source.optString("data", "")
        val place = origin?.invoke(i)
        if (data.isEmpty() && place == null) continue
        images.add(
            ChatImage(
                data = data,
                mimeType = source.optString("media_type", "image/png"),
                name = block.optString("name", "").ifBlank { "图片" },
                origin = place,
            ),
        )
    }
    return images
}
