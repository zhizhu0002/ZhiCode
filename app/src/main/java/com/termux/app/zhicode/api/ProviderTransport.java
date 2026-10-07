package com.termux.app.zhicode.api;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

/**
 * Providers 共用的阻塞式 HTTP 传输小工具。
 *
 * <p>协议类只负责端点、请求头、JSON 和事件解码；连接生命周期、请求体编码和有界
 * 错误读取集中在这里，避免三份传输代码在修复超时/资源释放时逐渐分叉。
 */
final class ProviderTransport {

    private ProviderTransport() {}

    static void writeJson(HttpURLConnection connection, String body) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8))) {
            writer.write(body == null ? "" : body);
        }
    }

    static String readError(HttpURLConnection connection, int maxChars) throws Exception {
        return HttpResponseReader.read(connection.getErrorStream(), maxChars);
    }

    static String readBody(HttpURLConnection connection, int maxChars) throws Exception {
        return HttpResponseReader.read(connection.getInputStream(), maxChars);
    }
}
