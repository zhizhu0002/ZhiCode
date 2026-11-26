package com.zhizhu.zhicode.bridge

import android.content.Context
import com.termux.app.zhicode.model.AssistantTurn
import com.termux.app.zhicode.model.SessionConfig
import com.termux.app.zhicode.model.ToolExecutionResult
import com.termux.app.zhicode.storage.ApiSettingsStore
import com.termux.app.zhicode.tools.ToolRegistry
import com.zhizhu.zhicode.ContainerPaths
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 宿主能力桥（仅监听 127.0.0.1 随机端口，随机 Token 鉴权），供 Debian 容器内的 Pi 与终端调用 Root / Shizuku / Intent / Debug / Sandbox / UiCanvas。
 */
internal class AndroidHostBridge(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val registry = ToolRegistry(appContext)
    private val executor: ExecutorService = Executors.newCachedThreadPool { r ->
        Thread(r, "zhi-host-bridge").apply { isDaemon = true }
    }

    @Volatile
    var configSupplier: () -> SessionConfig = { ApiSettingsStore(appContext).load() }

    val token: String = generateToken()

    fun bridgeEnv(): Map<String, String> = mapOf(
        "ZHICODE_BRIDGE_URL" to ensureStarted(),
        "ZHICODE_BRIDGE_TOKEN" to token,
    )

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var acceptThread: Thread? = null

    @Volatile
    var port: Int = -1
        private set

    fun ensureStarted(): String {
        val current = serverSocket
        if (current != null && !current.isClosed && port > 0) {
            return "http://127.0.0.1:$port"
        }
        synchronized(this) {
            val existing = serverSocket
            if (existing != null && !existing.isClosed && port > 0) {
                return "http://127.0.0.1:$port"
            }
            val loopback = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
            val socket = ServerSocket(0, 16, loopback)
            serverSocket = socket
            port = socket.localPort
            acceptThread = Thread({ acceptLoop(socket) }, "zhi-bridge-accept").apply {
                isDaemon = true
                start()
            }
            return "http://127.0.0.1:$port"
        }
    }

    fun shutdown() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        port = -1
        executor.shutdownNow()
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (!socket.isClosed) {
            val client = try {
                socket.accept()
            } catch (_: IOException) {
                break
            }
            executor.execute { handleClient(client) }
        }
    }

    private fun handleClient(socket: Socket) {
        socket.use { sock ->
            sock.soTimeout = 300_000
            val input = BufferedInputStream(sock.getInputStream())
            val output = sock.getOutputStream()
            try {
                val requestLine = readAsciiLine(input) ?: return
                val parts = requestLine.split(' ')
                if (parts.size < 2) {
                    writeJson(output, 400, JSONObject().put("ok", false).put("error", "Bad request"))
                    return
                }
                val method = parts[0].uppercase()
                val path = parts[1]

                var contentLength = 0
                var providedToken = ""
                while (true) {
                    val line = readAsciiLine(input) ?: break
                    if (line.isEmpty()) break
                    val colon = line.indexOf(':')
                    if (colon <= 0) continue
                    val name = line.substring(0, colon).trim().lowercase()
                    val value = line.substring(colon + 1).trim()
                    if (name == "content-length") contentLength = value.toIntOrNull() ?: 0
                    if (name == "x-zhicode-token") providedToken = value
                }

                if (!constantTimeEquals(token, providedToken)) {
                    writeJson(output, 403, JSONObject().put("ok", false).put("error", "Forbidden"))
                    return
                }
                if (method != "POST" || path != "/invoke") {
                    writeJson(output, 404, JSONObject().put("ok", false).put("error", "Not found"))
                    return
                }
                if (contentLength < 0 || contentLength > 4 * 1024 * 1024) {
                    writeJson(output, 413, JSONObject().put("ok", false).put("error", "Payload too large"))
                    return
                }

                val bodyBytes = readExactly(input, contentLength)
                val req = JSONObject(String(bodyBytes, StandardCharsets.UTF_8))
                val toolName = req.optString("tool", "").trim()
                val rawInput = req.optJSONObject("input") ?: JSONObject()

                if (toolName !in ALLOWED_TOOLS) {
                    writeJson(output, 400, JSONObject().put("ok", false).put("error", "Tool not exposed on bridge: $toolName"))
                    return
                }

                val mappedInput = translateGuestPaths(toolName, rawInput)
                val config = configSupplier()
                val result = registry.execute(config, toolName, mappedInput)
                writeJson(output, 200, serializeResult(result))
            } catch (t: Throwable) {
                runCatching {
                    writeJson(
                        output,
                        500,
                        JSONObject().put("ok", false).put("isError", true).put("text", t.message ?: t.javaClass.simpleName),
                    )
                }
            }
        }
    }

    private fun translateGuestPaths(toolName: String, input: JSONObject): JSONObject {
        val copy = JSONObject(input.toString())
        if (toolName == "AndroidIntent") {
            val apkPath = copy.optString("path", "").trim()
            if (apkPath.isNotEmpty() && !apkPath.startsWith("/storage/") && !apkPath.startsWith("/sdcard")) {
                copy.put("path", ContainerPaths.guestToHost(apkPath).absolutePath)
            }
        } else if (toolName == "Sandbox") {
            val action = copy.optString("action", "").trim().lowercase()
            val apkPath = copy.optString("path", "").trim()
            if (action == "install" && apkPath.isNotEmpty() && !apkPath.startsWith("/storage/") && !apkPath.startsWith("/sdcard")) {
                copy.put("path", ContainerPaths.guestToHost(apkPath).absolutePath)
            }
        } else if (toolName == "Debug") {
            val action = copy.optString("action", "").trim().lowercase()
            val libPath = copy.optString("path", "").trim()
            if (action == "load_library" && libPath.isNotEmpty() && !libPath.startsWith("/storage/") && !libPath.startsWith("/sdcard")) {
                copy.put("path", ContainerPaths.guestToHost(libPath).absolutePath)
            }
        }
        return copy
    }

    private fun serializeResult(result: ToolExecutionResult): JSONObject {
        val out = JSONObject()
            .put("ok", !result.isError)
            .put("isError", result.isError)
            .put("exitCode", result.exitCode)
            .put("text", result.content ?: "")

        val images = JSONArray()
        val extra: JSONArray? = result.additionalContent()
        if (extra != null) {
            for (i in 0 until extra.length()) {
                val block = extra.optJSONObject(i) ?: continue
                if ("image" != block.optString("type")) continue
                val source = block.optJSONObject("source") ?: continue
                val data = source.optString("data", "")
                val mime = source.optString("media_type", "image/png")
                if (data.isNotEmpty()) {
                    images.put(JSONObject().put("data", data).put("mimeType", mime))
                }
            }
        }
        if (images.length() > 0) out.put("images", images)
        return out
    }

    private fun readAsciiLine(input: BufferedInputStream): String? {
        val buf = ByteArrayOutputStream(128)
        while (true) {
            val b = input.read()
            if (b < 0) return if (buf.size() == 0) null else String(buf.toByteArray(), StandardCharsets.US_ASCII)
            if (b == '\n'.code) break
            if (b != '\r'.code) buf.write(b)
        }
        return String(buf.toByteArray(), StandardCharsets.US_ASCII)
    }

    private fun readExactly(input: BufferedInputStream, length: Int): ByteArray {
        val out = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = input.read(out, offset, length - offset)
            if (read < 0) throw IOException("Unexpected EOF")
            offset += read
        }
        return out
    }

    private fun writeJson(output: OutputStream, status: Int, json: JSONObject) {
        val bytes = json.toString().toByteArray(StandardCharsets.UTF_8)
        val header = ("HTTP/1.1 $status OK\r\n" +
            "Content-Type: application/json; charset=utf-8\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Connection: close\r\n\r\n").toByteArray(StandardCharsets.US_ASCII)
        output.write(header)
        output.write(bytes)
        output.flush()
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }

    private fun generateToken(): String {
        val bytes = ByteArray(24)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private val ALLOWED_TOOLS = setOf(
            "Root", "Shizuku", "AndroidIntent", "Debug", "Sandbox", "ui_canvas", "WebSearch", "WebFetch",
        )

        @Volatile
        private var sharedInstance: AndroidHostBridge? = null

        fun shared(context: Context): AndroidHostBridge {
            sharedInstance?.let { return it }
            return synchronized(this) {
                sharedInstance ?: AndroidHostBridge(context.applicationContext).also { sharedInstance = it }
            }
        }
    }
}
