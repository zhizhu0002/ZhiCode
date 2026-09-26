package com.termux.app.zhicode.storage;

import com.termux.shared.termux.TermuxConstants;

import java.io.File;

/**
 * 把 HOME 下旧品牌名的数据目录搬到当前名下（一次性）。
 *
 * <h3>为什么用「搬目录」而不是「两处都读」</h3>
 * HOME 下的数据包含会话、任务、技能、项目计划、MCP 配置，其中一部分（例如
 * {@code tasks/<key>/.highwatermark}、{@code .version}、{@code .lock}）是
 * 一个目录内部自洽的小数据库 —— 只读其中一半会出现「ID 从 1 重新开始」
 * 这类静默错乱。整体搬迁则保持目录内部完整，没有中间状态。
 *
 * <p>用户<b>项目目录</b>里的同名子目录不在这里处理：那属于用户的版本库，
 * 搬动它等于改动别人的仓库。那部分由读取端做「新名优先、旧名兜底」，
 * 见 {@link TermuxConstants#dataDirCandidatesIn}.
 *
 * <h3>调用时机</h3>
 * {@code Application.attachBaseContext()} 里、{@link TermuxConstants#configure} 之后、
 * 任何读写这些目录的代码之前。它必须幂等：每次启动都会调用。
 */
public final class LegacyDataMigration {

    /** 记忆文件的旧名（放在数据目录根下）。 */
    private static final String LEGACY_MEMORY_FILE = TermuxConstants.LEGACY_MEMORY_FILE_NAME;

    private LegacyDataMigration() {}

    /**
     * 执行搬迁。
     *
     * @return 一段可读的结果说明，用于日志；没有需要处理的事情时返回空串
     */
    public static String run() {
        StringBuilder notes = new StringBuilder();
        File current = TermuxConstants.dataDir();
        File legacy = TermuxConstants.legacyDataDir();

        // 只在「新目录还不存在」且「旧目录存在」时搬。
        // 两边都存在说明用户已经在新目录下产生了数据，此时合并两个目录是有风险的
        // （同名子目录谁覆盖谁无法判断），因此不动手、只记录。
        if (!current.exists() && legacy.isDirectory()) {
            if (current.getParentFile() != null && !current.getParentFile().isDirectory()) {
                current.getParentFile().mkdirs();
            }
            if (legacy.renameTo(current)) {
                notes.append("数据目录已迁移: ").append(legacy.getName())
                        .append(" -> ").append(current.getName());
            } else {
                // 搬不动（跨设备、权限）时保留旧目录并如实说明：读取端的兜底逻辑
                // 仍然能拿到数据，只是这次没搬成功。
                notes.append("数据目录迁移失败，继续使用 ").append(legacy.getAbsolutePath());
            }
        } else if (current.isDirectory() && legacy.isDirectory()) {
            notes.append("新旧数据目录同时存在，保持原样以免互相覆盖: ")
                    .append(legacy.getAbsolutePath());
        }

        // 记忆文件改名在数据目录内部进行，与上面的目录搬迁彼此独立。
        String memory = renameMemoryFile(TermuxConstants.dataDir());
        if (!memory.isEmpty()) {
            if (notes.length() > 0) notes.append("；");
            notes.append(memory);
        }
        return notes.toString();
    }

    /**
     * 把数据目录根下的旧记忆文件改成新名。
     *
     * @return 结果说明；无需处理时返回空串
     */
    private static String renameMemoryFile(File dataDir) {
        if (dataDir == null || !dataDir.isDirectory()) return "";
        File legacy = new File(dataDir, LEGACY_MEMORY_FILE);
        File current = new File(dataDir, TermuxConstants.MEMORY_FILE_NAME);
        // 新文件已存在就不动：两个都在时以新的为准，旧的原样留着（不删用户内容）。
        if (!legacy.isFile() || current.exists()) return "";
        if (legacy.renameTo(current)) return LEGACY_MEMORY_FILE + " -> " + TermuxConstants.MEMORY_FILE_NAME;
        return "";
    }
}
