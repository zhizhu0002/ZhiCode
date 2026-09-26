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
 * 按项目保存计划文本（Markdown），位置是 {@code ~/.iq/projects/<项目键>/plans}。
 *
 * <p>一份计划就是一个 {@code <workflowId>.md} 文件。
 *
 * <h3>写入必须原子</h3>
 * 计划文本会被「提交审批 → 用户阅读」这条链路读到，而且写入发生在用户点击
 * ExitPlanMode 的瞬间。如果直接写目标文件，读者可能读到写了一半的内容 ——
 * 用户看到的会是一份残缺的计划，而它还是被批准的那一份。
 *
 * <p>因此写入顺序固定为：写同目录临时文件 → {@code fsync} → POSIX {@code rename}。
 * rename 在同一文件系统内是原子的，读者要么看到旧版本、要么看到完整的新版本。
 * {@code fsync} 不能省：不加的话 rename 之后另一个进程仍可能读到长度还是 0 的文件。
 *
 * <h3>为什么用 {@code Os.rename} 而不是 {@code File.renameTo}</h3>
 * 后者只返回一个布尔值，失败原因被丢掉。这里的失败需要能区分
 * 「目标不是目录」「跨设备」这类情况，所以用会给出 errno 的 POSIX 接口。
 */
public final class PlanStore {

    /** workflowId 的长度上限。它是文件名的一部分，过长会撞到文件系统的名字限制。 */
    private static final int MAX_WORKFLOW_ID_LENGTH = 128;

    /** 单份计划的大小上限。计划是给人看的文本，超过这个量级说明调用方传错了东西。 */
    private static final int MAX_PLAN_BYTES = 4 * 1024 * 1024;

    private static final String PLANS_DIR = "plans";
    private static final String PLAN_SUFFIX = ".md";

    private final File directory;

    public PlanStore(String projectDirectory) {
        directory = new File(SessionStore.projectsDirectory(),
                SessionStore.projectKey(projectDirectory) + File.separator + PLANS_DIR);
    }

    public File getDirectory() {
        return directory;
    }

    public File getPlanFile(String workflowId) {
        return new File(directory, requireWorkflowId(workflowId) + PLAN_SUFFIX);
    }

    /**
     * 原子的完整写入。
     *
     * <p>临时文件名以 {@code .} 开头：计划目录会被列出来给人看，
     * 点号开头的文件在常规列举里天然被隐藏。再加上随机后缀，
     * 同一 workflow 的并发写入不会互相踩到对方的临时文件。
     *
     * @return 发布后的目标文件
     */
    public synchronized File write(String workflowId, String planText) throws IOException {
        ensureDirectory();
        File target = getPlanFile(workflowId);
        File temporary = new File(directory,
                "." + target.getName() + "." + UUID.randomUUID() + ".tmp");
        boolean published = false;
        try {
            byte[] data = (planText == null ? "" : planText).getBytes(StandardCharsets.UTF_8);
            try (FileOutputStream output = new FileOutputStream(temporary, false)) {
                output.write(data);
                output.flush();
                output.getFD().sync();
            }
            try {
                Os.rename(temporary.getAbsolutePath(), target.getAbsolutePath());
            } catch (ErrnoException failure) {
                throw new IOException("无法发布计划文件 " + target, failure);
            }
            published = true;
            return target;
        } finally {
            // 发布失败时清掉临时文件。成功时它已经不存在（被 rename 掉了）。
            if (!published && temporary.exists()) temporary.delete();
        }
    }

    /**
     * 读一份计划。
     *
     * <p>「文件不存在」与「计划是空的」都返回空串：调用方在这两种情况下要做的事一样
     * （当作还没有计划），把它们分开只会多一个分支。真正需要区分的是
     * <b>读失败</b>，那个会抛出去。
     *
     * <p>读取长度受 {@link #MAX_PLAN_BYTES} 限制。不设限的话，
     * 一个被写坏的文件会先把内存吃光再报错。
     */
    public String read(String workflowId) throws IOException {
        File file = getPlanFile(workflowId);
        if (!file.isFile()) return "";
        if (file.length() > MAX_PLAN_BYTES) {
            throw new IOException("计划文件超出 " + MAX_PLAN_BYTES + " 字节上限: " + file);
        }
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream((int) Math.max(64, file.length()))) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAX_PLAN_BYTES) throw new IOException("计划文件在读取期间增长超出上限: " + file);
                output.write(buffer, 0, count);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    public boolean exists(String workflowId) {
        return getPlanFile(workflowId).isFile();
    }

    /** 删除是幂等的：本来就不存在也算成功，调用方不必先查一次。 */
    public synchronized boolean delete(String workflowId) {
        File file = getPlanFile(workflowId);
        return !file.exists() || file.delete();
    }

    private void ensureDirectory() throws IOException {
        if (directory.isDirectory()) return;
        if (!directory.mkdirs() && !directory.isDirectory()) {
            throw new IOException("无法创建计划目录 " + directory);
        }
    }

    /**
     * 校验 workflowId。
     *
     * <p>它会直接拼进文件名，所以必须挡住路径穿越。白名单比黑名单可靠：
     * 只允许字母数字开头、其余为字母数字与 {@code . _ -}，
     * 并显式排除 {@code .} 与 {@code ..}（它们能匹配上面的模式）。
     */
    private static String requireWorkflowId(String workflowId) {
        String id = workflowId == null ? "" : workflowId.trim();
        if (id.isEmpty()
                || id.length() > MAX_WORKFLOW_ID_LENGTH
                || !id.matches("[A-Za-z0-9][A-Za-z0-9._-]*")
                || ".".equals(id)
                || "..".equals(id)) {
            throw new IllegalArgumentException("非法的计划 workflow ID: " + workflowId);
        }
        return id;
    }
}
