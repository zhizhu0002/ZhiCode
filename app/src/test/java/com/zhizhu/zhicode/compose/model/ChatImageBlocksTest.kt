package com.zhizhu.zhicode.compose.model

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 内容块 → 图片的解析。
 *
 * <h3>为什么值得逐条钉</h3>
 *
 * 这段逻辑的失败模式（少读一张、把非图片当图片、整串图全消失）在界面上都只表现为
 * **"图没出来"**，光看界面分不清是哪一种。
 *
 * <h3>两个调用方，一份实现</h3>
 *
 * 这个块结构有两个互不相关的来源，字节形状完全一致：
 * 会话文件里的用户消息（`SessionReader` 读历史），以及工具结果的
 * `additionalContent`（`ZhiEngineController` 把沙箱截图带回界面）。
 * 所以它住在 `model` 而不是 `data` —— 见 [readChatImageBlocks] 的说明。
 *
 * <p>用真实的 `org.json`（工程给单测配了 `org.json:json` 实现，
 * 不是 android.jar 里那个抛异常的桩）。
 */
class ChatImageBlocksTest {

    private fun imageBlock(data: String, mediaType: String = "image/png", name: String = "a.png") =
        JSONObject()
            .put("type", "image")
            .put(
                "source",
                JSONObject().put("type", "base64").put("media_type", mediaType).put("data", data),
            )
            .put("name", name)

    @Test
    fun `读出一张图片`() {
        val images = readChatImageBlocks(JSONArray().put(imageBlock("QUJD")))
        assertEquals(1, images.size)
        assertEquals("QUJD", images[0].data)
        assertEquals("image/png", images[0].mimeType)
        assertEquals("a.png", images[0].name)
    }

    @Test
    fun `多种内容块混在一起时只取图片`() {
        val content = JSONArray()
            .put(JSONObject().put("type", "text").put("text", "看这张图"))
            .put(imageBlock("QUJD"))
            .put(JSONObject().put("type", "tool_result").put("tool_use_id", "t1").put("content", "ok"))
            .put(imageBlock("REVG", name = "b.jpg"))
        val images = readChatImageBlocks(content)
        assertEquals(2, images.size)
        assertEquals("QUJD", images[0].data)
        assertEquals("REVG", images[1].data)
    }

    @Test
    fun `没有 data 的图片块被跳过而不是产生空图`() {
        // 空 data 会被 Base64.decode 解成空字节，解码必然失败 →
        // 与其让气泡里出现一块"图片无法显示"的占位，不如当它不存在。
        val empty = JSONObject()
            .put("type", "image")
            .put("source", JSONObject().put("type", "base64").put("media_type", "image/png").put("data", ""))
        assertTrue(readChatImageBlocks(JSONArray().put(empty)).isEmpty())
    }

    @Test
    fun `缺 source 的图片块被跳过`() {
        val broken = JSONObject().put("type", "image").put("name", "x.png")
        assertTrue(readChatImageBlocks(JSONArray().put(broken)).isEmpty())
    }

    @Test
    fun `非 base64 来源的图片块被跳过`() {
        // 引擎目前只写 base64。url 类型的图要联网加载，与"离线也能看历史图"
        // 的前提冲突，得有单独的决定 —— 在那之前不能悄悄当成 base64 去解。
        val url = JSONObject()
            .put("type", "image")
            .put("source", JSONObject().put("type", "url").put("url", "https://example.com/a.png"))
        assertTrue(readChatImageBlocks(JSONArray().put(url)).isEmpty())
    }

    @Test
    fun `空数组返回空列表`() {
        assertTrue(readChatImageBlocks(JSONArray()).isEmpty())
    }

    @Test
    fun `没有名字时给一个可读的默认名`() {
        // 解码失败时要显示名字，空名字会让兜底文案变成一块空白。
        val images = readChatImageBlocks(JSONArray().put(imageBlock("QUJD", name = "")))
        assertEquals(1, images.size)
        assertEquals("图片", images[0].name)
    }

    @Test
    fun `缺 media_type 时按 png 兜底`() {
        val noType = JSONObject()
            .put("type", "image")
            .put("source", JSONObject().put("type", "base64").put("data", "QUJD"))
        val images = readChatImageBlocks(JSONArray().put(noType))
        assertEquals(1, images.size)
        // 这个值只用于展示提示，不参与解码路径选择（BitmapFactory 看魔数），
        // 所以兜底值不对也不会解错图 —— 但仍然要非空，免得界面显示成空白。
        assertEquals("image/png", images[0].mimeType)
    }

    // ------------------------------------------------ 工具附加内容特有的情形

    @Test
    fun `画布文档块被跳过而不是当成图片`() {
        // `UiCanvasTool` 会产出 {"type":"ui_canvas","document":…}。它的键里没有
        // source/data，所以本来就走不到加图那一步 —— 这条断言钉的是**别退化成**
        // "把任何有 type 的块都当图"，否则画布消息会在工具卡里出现一块空白图。
        val canvas = JSONObject()
            .put("type", "ui_canvas")
            .put("document", JSONObject().put("nodes", JSONArray()))
        assertTrue(readChatImageBlocks(JSONArray().put(canvas)).isEmpty())
    }

    @Test
    fun `画布与截图混在一起时只取截图`() {
        // 真实场景：一个工具批次里可能既有画布文档又有沙箱截图。
        val content = JSONArray()
            .put(JSONObject().put("type", "ui_canvas").put("document", JSONObject()))
            .put(imageBlock("QUJD", name = "shot.png"))
        val images = readChatImageBlocks(content)
        assertEquals(1, images.size)
        assertEquals("shot.png", images[0].name)
    }

    @Test
    fun `一块坏数据不影响同一串里的其它图`() {
        // 一条坏块不该让整张工具卡的图全部消失（也不该让整条历史里的图消失）。
        val content = JSONArray()
            .put(imageBlock("QUJD", name = "好图.png"))
            .put("这不是对象")
            .put(JSONObject().put("type", "image"))
            .put(imageBlock("REVG", name = "也好.png"))
        val images = readChatImageBlocks(content)
        assertEquals(2, images.size)
        assertEquals("好图.png", images[0].name)
        assertEquals("也好.png", images[1].name)
    }
}
