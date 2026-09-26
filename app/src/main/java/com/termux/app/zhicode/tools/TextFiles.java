package com.termux.app.zhicode.tools;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 文件工具的公共读写。
 *
 * <h3>为什么新建一个类</h3>
 * 「整份读成 UTF-8」这段在 {@code Read}/{@code Write}/{@code Edit}/{@code Delete}
 * 里各有一份，而且写法互不相同（有的按 {@code length()} 开数组、有的用 64 KiB 循环）。
 * 三处副本的差别没有一处是有意的，只是谁抄谁的问题；合并成一处之后，
 * 「超限怎么办」这类判断只需要在一个地方想清楚。
 *
 * <h3>为什么不用 {@code java.nio.file.Files}</h3>
 * 本工程要跑到 Android 7。{@code Files.readAllBytes} 在 API 26 才有，
 * 用它就得靠脱糖或运行时判版本，而这里的循环在任何版本上都能跑。
 *
 * <h3>为什么不直接按 {@code length()} 开数组</h3>
 * {@code length()} 与「实际能读到的字节数」不是一回事：文件可能在被读的过程中被
 * 截断（用户正在编译、或另一个工具刚改过它），那么按 length 开的数组里会留下
 * 尾部零字节。用循环读到 EOF 得到的才是真实内容。
 */
final class TextFiles {

    private static final int BUFFER_BYTES = 64 * 1024;

    private TextFiles() {}

    /** 整份读成 UTF-8 文本；文件不存在时返回空串。 */
    static String readText(File file) throws IOException {
        try (FileInputStream in = new FileInputStream(file);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[BUFFER_BYTES];
            int count;
            while ((count = in.read(buffer)) > 0) out.write(buffer, 0, count);
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    /**
     * 覆盖写入 UTF-8 文本。
     *
     * <p>{@code append=false} 是刻意的：这些工具的语义都是「整份替换」，
     * 追加语义由 Bash 的重定向负责，两处都支持反而会让人搞不清哪个生效。
     */
    static void writeText(File file, String text) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file, false)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }
}
