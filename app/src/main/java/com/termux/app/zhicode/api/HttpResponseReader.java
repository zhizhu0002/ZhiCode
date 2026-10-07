package com.termux.app.zhicode.api;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

/** 有界地读取 HTTP 文本响应，避免错误页或非流式响应无限增长。 */
final class HttpResponseReader {

    static final int DEFAULT_MAX_CHARS = 4 * 1024 * 1024;

    private HttpResponseReader() {}

    static String read(InputStream input, int maxChars) throws IOException {
        if (input == null) return "";
        int limit = Math.max(1, maxChars);
        StringBuilder out = new StringBuilder(Math.min(limit, 8 * 1024));
        boolean truncated = false;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, Charset.forName("UTF-8")))) {
            char[] buffer = new char[8192];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                int remaining = limit - out.length();
                if (remaining <= 0) {
                    truncated = true;
                    break;
                }
                int accepted = Math.min(count, remaining);
                out.append(buffer, 0, accepted);
                if (accepted < count) {
                    truncated = true;
                    break;
                }
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("HTTP response read interrupted");
                }
            }
        }
        if (truncated) out.append("\n…response truncated…");
        return out.toString();
    }
}
