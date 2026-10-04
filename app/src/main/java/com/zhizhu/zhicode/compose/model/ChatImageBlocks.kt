package com.zhizhu.zhicode.compose.model

import org.json.JSONArray

/**
 * 从一串内容块里取出图片块（纯函数，无 Android 依赖）。
 *
 * ## 块的结构
 *
 * ```
 * {"type":"image",
 *  "source":{"type":"base64","media_type":"image/png","data":"<base64>"},
 *  "name":"shot.png"}
 * ```
 *
 * ## 为什么放在 `model` 而不是 `data`
 *
 * 这个形状**有两个来源**，它们互不相关但字节结构完全一致：
 *
 * 1. **会话文件**里的用户消息（`ZhiCodeEngine.buildUserContent` 写入）——
 *    由 [com.zhizhu.zhicode.compose.data.SessionReader] 在读历史时调用；
 * 2. **工具的附加内容**（`ToolExecutionResult.additionalContent`）——
 *    由 `ZhiEngineController` 在工具结束时调用，把沙箱截图/画布文档带回界面。
 *
 * 两个调用方分处 `data` 与 `engine` 两个包，而 `engine` 的既有依赖方向是**只依赖
 * `model`**（它从不 import `data`）。把它留在 `data` 里会让引擎反向依赖数据层，
 * 所以移到这里，两边共用同一份实现 —— 也共用同一份单测
 * （[com.zhizhu.zhicode.compose.model.ChatImageBlocksTest]）。
 *
 * ## 两条必须守住的规则
 *
 * - **任何一块读不出来就跳过那一块**，不影响同一串里的其它图：一条坏数据不该让
 *   整条历史、或整张工具卡的图全部消失；
 * - **只认 base64**。`url` 类型的图要联网加载，与"离线也要能显示"的前提冲突，
 *   得有单独的决定 —— 在那之前不能悄悄当成 base64 去解。
 *
 * ## 为什么不在这里解码
 *
 * 返回的 [ChatImage] 只**持有 base64 字符串**，不做 `Base64.decode` / `BitmapFactory`。
 * 解码发生在界面层（`ZhiImage.rememberDecodeState`）且跑在 `Dispatchers.Default` 上 ——
 * 这样这个函数可以在主线程被随意调用而没有解码开销。
 *
 * ## 明确不处理的类型
 *
 * `type == "ui_canvas"`（`UiCanvasTool` 的产出）**在这里被静默跳过**，这是有意的：
 * 画布是**实时**机制（`UiCanvasController.apply` 直接改活的视图树，
 * `UiCanvasStore.publishPreview` 负责广播），不是一份可以静态渲染的文档快照；
 * 要把它画进工具卡得另写一个"文档 → 预览"的渲染器，属于另一件事。
 */
internal fun readChatImageBlocks(content: JSONArray): List<ChatImage> {
    val images = mutableListOf<ChatImage>()
    for (i in 0 until content.length()) {
        val block = content.optJSONObject(i) ?: continue
        if (block.optString("type", "") != "image") continue
        val source = block.optJSONObject("source") ?: continue
        if (source.optString("type", "") != "base64") continue
        val data = source.optString("data", "")
        if (data.isEmpty()) continue
        images.add(
            ChatImage(
                data = data,
                mimeType = source.optString("media_type", "image/png"),
                name = block.optString("name", "").ifBlank { "图片" },
            ),
        )
    }
    return images
}
