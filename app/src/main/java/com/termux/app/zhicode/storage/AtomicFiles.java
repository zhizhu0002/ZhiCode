package com.termux.app.zhicode.storage;

import android.system.ErrnoException;
import android.system.Os;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 本工程里所有「不能被读到半截」的文件都走这里。
 *
 * <h3>为什么必须原子</h3>
 * 这些文件都是**配置与状态**：计划文本、MCP 服务器清单。它们的写入时机往往是
 * 「用户刚点了某个按钮」，而读取时机是「界面重建、或另一个进程启动」。
 * 直接写目标文件的话，读取方可能看到长度还是 0、或者只写了一半的内容 ——
 * 而一份残缺的配置在下次保存时会被当成「当前状态」写回去，坏数据就此固化。
 *
 * <h3>写入顺序固定</h3>
 * <pre>
 *   写同目录临时文件 → flush → fsync → rename
 * </pre>
 * 三点都不能省：
 * <ul>
 *   <li>临时文件必须在**同一目录**（同一文件系统），否则 rename 会退化成
 *       跨设备拷贝，原子性就没了。</li>
 *   <li>{@code fsync} 不能省。它保证 rename 之后另一个进程读到的不是
 *       「目录项已更新、但数据页还在页缓存里」的零长度文件。</li>
 *   <li>用 {@link Os#rename} 而不是 {@link File#renameTo}：后者只返回布尔值，
 *       失败原因被丢掉。这里需要区分「目标不是目录」「跨设备」这类情况。</li>
 * </ul>
 *
 * <h3>为什么读取要有上限</h3>
 * 上限挡的是「文件已经坏了」这种情况：一个被写坏的文件会先把内存吃光、
 * 再在别处报一个与真正原因无关的错。有上限时错误信息直接指向那个文件。
 */
final class AtomicFiles {

    private static final int READ_BUFFER_BYTES = 8192;
    /** 临时文件名里随机部分的长度。 */
    private static final int RANDOM_SUFFIX_CHARS = 36;

    private AtomicFiles() {}

    /**
     * 原子发布一份文本内容。
     *
     * <p>临时文件名以点号开头：这些目录会被列出来给人看，点号开头的名字在常规
     * 列举里天然被隐藏（前提是调用方输出的目录里没有别的东西恰好也以点号开头）。
     * 再加随机后缀，同一目标的并发写入不会踩到对方的临时文件。
     *
     * @return 发布后的目标文件
     */
    static File publishText(File target, String text) throws IOException {
        return publish(target, (text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 原子发布一份字节内容。
     *
     * <p>{@code finally} 里清掉临时文件：发布成功时它已经被 rename 掉了
     * （{@code exists()} 为假），发布失败时它留在磁盘上才是垃圾。
     */
    static File publish(File target, byte[] data) throws IOException {
        File directory = target.getParentFile();
        File temporary = new File(directory,
            "." + target.getName() + "." + UUID.randomUUID() + ".tmp");
        boolean published = false;
        try {
            try (FileOutputStream output = new FileOutputStream(temporary, false)) {
                output.write(data);
                output.flush();
                // 见类注释：不加这一步，rename 之后另一个进程可能读到零长度文件。
                output.getFD().sync();
            }
            try {
                Os.rename(temporary.getAbsolutePath(), target.getAbsolutePath());
            } catch (ErrnoException failure) {
                throw new IOException("无法发布文件 " + target, failure);
            }
            published = true;
            return target;
        } finally {
            if (!published && temporary.exists()) temporary.delete();
        }
    }

    /** 读整个文件；文件不存在返回空串（而不是抛错）。 */
    static String readText(File file) throws IOException {
        if (!file.isFile()) return "";
        return new String(readBytes(file, Integer.MAX_VALUE), StandardCharsets.UTF_8);
    }

    /**
     * 读一个文件，字节数不超过 {@code maxBytes}。
     *
     * <p>两处都检查大小：开读之前的 {@code length()} 是廉价的第一道闸
     * （能挡住「文件本来就是巨物」），读的过程中再累加是第二道 ——
     * 文件可能在读的过程中被写大，而只信第一次的长度会读到一半就停，
     * 得到一个截断的内容且毫无提示。
     */
    static byte[] readBytes(File file, int maxBytes) throws IOException {
        long reported = file.length();
        if (reported > maxBytes) {
            throw new IOException("文件超出 " + maxBytes + " 字节上限: " + file);
        }
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output =
                 new ByteArrayOutputStream((int) Math.max(64, Math.min(reported, maxBytes)))) {
            byte[] buffer = new byte[READ_BUFFER_BYTES];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > maxBytes) {
                    throw new IOException("文件在读取期间增长超出上限: " + file);
                }
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    /** 建目录；已经存在时什么都不做。 */
    static void ensureDirectory(File directory, String what) throws IOException {
        if (directory.isDirectory()) return;
        if (!directory.mkdirs() && !directory.isDirectory()) {
            throw new IOException("无法创建" + what + " " + directory);
        }
    }
}
