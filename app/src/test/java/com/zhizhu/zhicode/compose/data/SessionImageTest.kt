package com.zhizhu.zhicode.compose.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 从会话行里读回图片块。
 *
 * <p>这段逻辑原来根本不存在 —— `SessionReader.transcript()` 的 user 分支只认
 * `text` / `tool_result`，image 块被静默跳过，于是"发出去的图片不见了"。
 * 它的失败模式（少读一张、把非图片当图片、整条消息消失）在界面上都只表现为
 * "图没出来"，光看界面分不清是哪一种，所以逐条钉在这里。
 *
 * <p>用真实的 `org.json`（工程给单测配了 `org.json:json` 实现，
 * 不是 android.jar 里那个抛异常的桩）。
 */
class SessionImageTest {

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
        val images = SessionReader.readImages(JSONArray().put(imageBlock("QUJD")))
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
        val images = SessionReader.readImages(content)
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
        assertTrue(SessionReader.readImages(JSONArray().put(empty)).isEmpty())
    }

    @Test
    fun `缺 source 的图片块被跳过`() {
        val broken = JSONObject().put("type", "image").put("name", "x.png")
        assertTrue(SessionReader.readImages(JSONArray().put(broken)).isEmpty())
    }

    @Test
    fun `非 base64 来源的图片块被跳过`() {
        // 引擎目前只写 base64。url 类型的图要联网加载，与"离线也能看历史图"
        // 的前提冲突，得有单独的决定 —— 在那之前不能悄悄当成 base64 去解。
        val url = JSONObject()
            .put("type", "image")
            .put("source", JSONObject().put("type", "url").put("url", "https://example.com/a.png"))
        assertTrue(SessionReader.readImages(JSONArray().put(url)).isEmpty())
    }

    @Test
    fun `空数组返回空列表`() {
        assertTrue(SessionReader.readImages(JSONArray()).isEmpty())
    }

    @Test
    fun `没有名字时给一个可读的默认名`() {
        // 解码失败时要显示名字，空名字会让兜底文案变成一块空白。
        val images = SessionReader.readImages(JSONArray().put(imageBlock("QUJD", name = "")))
        assertEquals(1, images.size)
        assertEquals("图片", images[0].name)
    }

    @Test
    fun `缺 media_type 时按 png 兜底`() {
        val noType = JSONObject()
            .put("type", "image")
            .put("source", JSONObject().put("type", "base64").put("data", "QUJD"))
        val images = SessionReader.readImages(JSONArray().put(noType))
        assertEquals(1, images.size)
        // 这个值只用于展示提示，不参与解码路径选择（BitmapFactory 看魔数），
        // 所以兜底值不对也不会解错图 —— 但仍然要非空，免得界面显示成空白。
        assertEquals("image/png", images[0].mimeType)
    }
}
