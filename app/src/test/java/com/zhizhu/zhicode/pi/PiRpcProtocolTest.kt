package com.zhizhu.zhicode.pi

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

class PiRpcProtocolTest {

    @Test
    fun readStrictJsonLines_preservesUnicodeLineSeparatorsInsideJsonStrings() {
        val payload = "{\"type\":\"text_delta\",\"delta\":\"line1\u2028line2\u2029end\"}\r\n{\"type\":\"agent_settled\"}\n"
        val input = ByteArrayInputStream(payload.toByteArray(StandardCharsets.UTF_8))
        val lines = mutableListOf<String>()

        PiRpcBackend.readStrictJsonLines(input) { lines.add(it) }

        assertEquals(2, lines.size)
        val first = JSONObject(lines[0])
        assertEquals("line1\u2028line2\u2029end", first.getString("delta"))
        assertEquals("agent_settled", JSONObject(lines[1]).getString("type"))
    }

    @Test
    fun parseExtensionConfirmTitle_extractsToolAndRisk() {
        val (tool, highRisk) = PiRpcBackend.parseExtensionConfirmTitle("zhi-perm:Root:1")
        assertEquals("Root", tool)
        assertTrue(highRisk)

        val (bash, lowRisk) = PiRpcBackend.parseExtensionConfirmTitle("zhi-perm:bash:0")
        assertEquals("bash", bash)
        assertFalse(lowRisk)
    }

    @Test
    fun extractContentImagesAndDiffCounts_parseRichToolResults() {
        val content = JSONArray()
            .put(JSONObject().put("type", "text").put("text", "Screenshot saved"))
            .put(JSONObject().put("type", "image").put("data", "iVBORw0KGgo=").put("mimeType", "image/png"))

        assertEquals("Screenshot saved", PiRpcBackend.extractContentText(content))
        val images = PiRpcBackend.extractContentImages(content)
        assertEquals(1, images.size)
        assertEquals("iVBORw0KGgo=", images[0].data)
        assertEquals("image/png", images[0].mimeType)

        val diff = "--- a/Foo.kt\n+++ b/Foo.kt\n@@ -1,2 +1,3 @@\n-old\n+new1\n+new2\n context"
        val (added, deleted) = PiRpcBackend.countDiffLines(diff)
        assertEquals(2, added)
        assertEquals(1, deleted)
    }
}
