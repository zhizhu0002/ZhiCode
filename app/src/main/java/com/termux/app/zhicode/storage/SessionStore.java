package com.termux.app.zhicode.storage;

import android.system.Os;
import android.system.OsConstants;

import com.termux.app.zhicode.model.PlanWorkflowState;
import com.termux.app.zhicode.model.SessionConfig;
import com.termux.shared.termux.TermuxConstants;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话历史的持久化。
 *
 * <h3>格式与布局</h3>
 * 每个规范化的项目路径拥有自己的历史命名空间：{@code ~/.zhicode/projects/<路径键>/}。
 * 一次会话就是其中一个 {@code <时间>-<随机>.jsonl} 文件 ——
 * <b>一行一个 JSON 对象</b>，追加写。选 JSONL 而不是单个大 JSON 有两个实际理由：
 * 追加不需要重写整个文件；而且它天然是人类可读、可手工修复的。
 *
 * <h3>两种行：流水与快照</h3>
 * <ul>
 *   <li><b>流水行</b>（{@code message} / {@code session_start} / {@code plan_*} 等）
 *       只追加，永不修改。界面渲染的完整对话就是它们。</li>
 *   <li><b>快照行</b>（{@code context_snapshot}）是给模型看的压缩后上下文。
 *       {@link #loadMessages} 遇到它就<b>重置</b>已读到的历史，之后的消息行再接上去。
 *       关键点：它<b>不删除</b>任何流水行，所以界面仍能显示完整原始对话，
 *       而模型只看到压缩后的版本。</li>
 * </ul>
 *
 * <h3>改一条历史消息为什么要这么小心</h3>
 * {@link #editMessage} / {@link #deleteMessage} 是<b>重写整个文件</b>（JSONL 无法原地改一行）。
 * 危险在于：用户看到的是「第 3 条消息」，而磁盘上的行号会随快照行、损坏行、空行而漂移；
 * 中间还有别的进程可能在追加。因此编辑采用的是引用校验：
 * 调用方必须给出 {@code messageId} 或旧的行号，<b>外加内容摘要</b>。
 * 三者对不上就拒绝修改（「要修改的历史消息已变化」），而不是猜一条去改。
 * 另外，删掉一条历史后，它之后的 {@code context_*} 快照行必须一并丢弃 ——
 * 那些快照是基于包含该消息的上下文生成的，留着会让模型看到改之前的版本。
 *
 * <h3>并发与崩溃安全</h3>
 * 同一文件的操作按规范路径取进程内锁串行化；跨进程靠「追加写 + 原子替换」。
 * 所有重写都走「写临时文件 → fsync → rename」，并在替换后恢复原有的 mtime，
 * 这样「编辑一条备注」不会把会话在列表里的活跃时间改掉。
 */
public final class SessionStore {

    /** 会话文件扩展名。 */
    private static final String SESSION_SUFFIX = ".jsonl";

    /** 备注长度上限。它是给列表显示用的，不需要更长。 */
    private static final int MAX_NOTE_CHARS = 500;
    /** 标题覆盖长度上限。 */
    private static final int MAX_TITLE_OVERRIDE_CHARS = 120;
    /** 自动生成标题的长度上限。 */
    private static final int MAX_TITLE_CHARS = 48;
    /** 路径键里可读段的长度上限。 */
    private static final int MAX_SLUG_CHARS = 72;

    // ------------------------------------------------------------ 消息行类型

    private static final String ROW_SESSION_START = "session_start";
    private static final String ROW_MESSAGE = "message";
    private static final String ROW_SESSION_METADATA = "session_metadata";
    private static final String ROW_CONTEXT_SNAPSHOT = "context_snapshot";
    private static final String ROW_CONTEXT_COMPACTION = "context_compaction";
    private static final String ROW_PROFILE_BINDING = "profile_binding";
    private static final String ROW_TURN_CONFIG = "turn_config";

    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";

    private static final String BLOCK_TEXT = "text";
    private static final String BLOCK_THINKING = "thinking";
    private static final String BLOCK_REASONING = "reasoning";
    private static final String BLOCK_IMAGE = "image";

    /** 压缩策略标识。写进历史，便于事后判断某次压缩是谁做的。 */
    private static final String COMPACTION_STRATEGY = "model_semantic_api_round_v2";

    // -------------------------------------------------------- 内部标记

    /** 上下文摘要块的起始标记。由 {@code ContextCompactor} 写入；本身不含品牌，无需搬迁。 */
    private static final String CONTEXT_SUMMARY_MARKER = "<context_summary>";

    /**
     * 内部续跑标记的当前写法。
     *
     * <p>由 {@code ZhiCodeEngine} 在「模型输出被截断、需要继续」时写入历史。
     * 它的作用是让这段文本<b>不被当成人类发言</b>：否则它会成为会话标题，
     * 也会出现在可编辑的用户消息里。
     */
    private static final String INTERNAL_CONTINUE_MARKER = "<zhicode_internal_continue>";

    // ------------------------------------------------------------ 单例式表

    /** 按规范路径串行化同一文件的读写。跨进程由「追加 + 原子替换」保证。 */
    private static final ConcurrentHashMap<String, Object> FILE_LOCKS = new ConcurrentHashMap<>();

    /** 一次会话的备注与标题覆盖。两者都做长度与控制字符清理后才落盘。 */
    public static final class SessionMetadata {
        public final String note;
        public final String titleOverride;

        public SessionMetadata(String note, String titleOverride) {
            this.note = sanitizeMetadata(note, MAX_NOTE_CHARS);
            // 标题是单行的：换行会让它在列表里被截断成两截，显示成半个标题。
            this.titleOverride = sanitizeMetadata(titleOverride, MAX_TITLE_OVERRIDE_CHARS).replace('\n', ' ');
        }
    }

    /** 会话列表里的一行。 */
    public static final class SessionSummary {
        public final File file;
        public final String project;
        public final String title;
        public final String note;
        public final String titleOverride;
        public final long createdAt;
        /** 最后一条非元数据事件的时间。编辑备注不得改变它。 */
        public final long activityModifiedAt;
        /** 兼容旧界面调用方的别名，与 {@link #activityModifiedAt} 同值。 */
        public final long modifiedAt;
        public final int messageCount;

        SessionSummary(File file, String project, String title, String note, String titleOverride,
                       long createdAt, long activityModifiedAt, int messageCount) {
            this.file = file;
            this.project = project == null ? "" : project;
            this.title = title == null || title.trim().isEmpty() ? file.getName() : title.trim();
            this.note = note == null ? "" : note;
            this.titleOverride = titleOverride == null ? "" : titleOverride;
            this.createdAt = createdAt;
            this.activityModifiedAt = activityModifiedAt;
            this.modifiedAt = activityModifiedAt;
            this.messageCount = messageCount;
        }
    }

    /**
     * 指向一条历史消息的引用。
     *
     * <p>三个字段一起构成校验：{@code messageId}（新格式）或 {@code legacyRowIndex}
     * （旧格式）+ {@code contentHash}。任何一项对不上就拒绝修改。
     */
    public static final class MessageReference {
        public final String messageId;
        public final int legacyRowIndex;
        public final String contentHash;

        public MessageReference(String messageId, int legacyRowIndex, String contentHash) {
            this.messageId = messageId == null ? "" : messageId;
            this.legacyRowIndex = legacyRowIndex;
            this.contentHash = contentHash == null ? "" : contentHash;
        }
    }

    /**
     * 一条原始行。{@link #raw} 用于在重写时原样保留未改动的行 ——
     * 重新序列化会改变键顺序与转义，那会让 diff 噪声变大、也更容易出错。
     */
    private static final class StrictRow {
        final String raw;
        final JSONObject json;
        /** 仅在 json 解析成功时递增的序号，用于兼容旧格式的行号引用。 */
        final int jsonIndex;

        StrictRow(String raw, JSONObject json, int jsonIndex) {
            this.raw = raw;
            this.json = json;
            this.jsonIndex = jsonIndex;
        }
    }

    /** 会话创建时用的 API 配置快照。用于事后判断「这次对话是哪个模型跑的」。 */
    public static final class ProfileBinding {
        public final String profileId;
        public final int profileRevision;
        public final int credentialRevision;
        public final String protocol;
        public final String baseUrl;
        public final String model;

        ProfileBinding(JSONObject payload) {
            profileId = payload.optString("profile_id", "");
            profileRevision = Math.max(1, payload.optInt("profile_revision", 1));
            credentialRevision = Math.max(1, payload.optInt("credential_revision", 1));
            protocol = payload.optString("protocol", "");
            baseUrl = payload.optString("base_url", "");
            model = payload.optString("model", "");
        }
    }

    private final File sessionFile;

    // ------------------------------------------------------------ 构造

    /** 在一个项目下新建一次会话，并写下起始行。 */
    public SessionStore(String projectDirectory) {
        String project = canonicalProject(projectDirectory);
        File directory = sessionDirectory(project);
        if (!directory.isDirectory()) directory.mkdirs();
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        sessionFile = new File(directory, stamp + "-" + UUID.randomUUID().toString().substring(0, 8) + SESSION_SUFFIX);
        try {
            append(new JSONObject()
                    .put("type", ROW_SESSION_START)
                    .put("project", project)
                    .put("project_key", projectKey(project))
                    .put("created_at", System.currentTimeMillis()));
        } catch (Exception ignored) {
            // 起始行写不进去不该让构造失败：后续的追加会再创建文件，
            // 而 summarize() 对缺失起始行有兜底（project 留空、created 取 mtime）。
        }
    }

    private SessionStore(File existing) {
        sessionFile = existing;
    }

    public static SessionStore resume(File existing) {
        if (existing == null || !existing.isFile()) throw new IllegalArgumentException("会话文件不存在");
        return new SessionStore(existing);
    }

    // ------------------------------------------------------------ 路径

    /** 按项目分目录的根。 */
    public static File projectsDirectory() {
        return new File(TermuxConstants.dataDir(), "projects");
    }

    /** 某项目的会话目录；顺带确保它存在并刷新项目索引。 */
    public static File sessionDirectory(String projectDirectory) {
        String project = canonicalProject(projectDirectory);
        File directory = new File(projectsDirectory(), projectKey(project));
        if (!directory.isDirectory()) directory.mkdirs();
        writeProjectIndex(directory, project);
        return directory;
    }

    /** 规范化项目路径：空值落到 Termux HOME；能取规范路径就取（解开符号链接）。 */
    public static String canonicalProject(String projectDirectory) {
        String raw = projectDirectory == null || projectDirectory.trim().isEmpty()
                ? TermuxConstants.TERMUX_HOME_DIR_PATH
                : projectDirectory.trim();
        try {
            return new File(raw).getCanonicalPath();
        } catch (Exception notResolvable) {
            return new File(raw).getAbsolutePath();
        }
    }

    /**
     * 目录名 = 可读段 + 短哈希。
     *
     * <p>只用可读段会撞车（{@code a/b} 与 {@code a_b} 都变成同一个），只用哈希则没人看得懂。
     * 两者拼接后，同名目录几乎不可能出现，而人还能一眼看出是哪个项目。
     *
     * <p>超长时截<b>尾部</b>而不是头部：路径的末尾通常才是区分度所在。
     */
    public static String projectKey(String projectDirectory) {
        String canonical = canonicalProject(projectDirectory);
        String slug = canonical.replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("-+", "-");
        if (slug.length() > MAX_SLUG_CHARS) slug = slug.substring(slug.length() - MAX_SLUG_CHARS);
        if (slug.isEmpty()) slug = "project";
        return slug + "-" + shortHash(canonical);
    }

    // ------------------------------------------------------------ 当前会话写入

    public synchronized String appendMessage(String role, JSONArray content) {
        return appendMessage(role, content, "", "", "");
    }

    /**
     * 追加一条消息行。
     *
     * @return 这条消息的 id（调用方给的就用给的），供后续编辑/删除精确定位
     */
    public synchronized String appendMessage(String role, JSONArray content, String messageId, String turnId, String origin) {
        String id = messageId == null || messageId.trim().isEmpty() ? UUID.randomUUID().toString() : messageId;
        try {
            JSONObject row = new JSONObject()
                    .put("type", ROW_MESSAGE)
                    .put("role", role)
                    .put("content", content)
                    .put("message_id", id)
                    .put("timestamp", System.currentTimeMillis());
            // 这两个字段只在有值时写入：空串写进去只会让历史文件变长，
            // 而且会让「有没有 turn 归属」这件事变得没法用 contains 判断。
            if (turnId != null && !turnId.trim().isEmpty()) row.put("turn_id", turnId);
            if (origin != null && !origin.trim().isEmpty()) row.put("origin", origin);
            append(row);
        } catch (Exception ignored) {
            // 追加失败仍返回 id：调用方拿到的是「这条消息的标识」，而不是写入成功的凭据。
        }
        return id;
    }

    public synchronized void appendProfileBinding(SessionConfig config) {
        appendEvent(ROW_PROFILE_BINDING, profilePayload(config));
    }

    public synchronized void appendTurnConfig(SessionConfig config, String turnId) {
        try {
            JSONObject payload = profilePayload(config);
            payload.put("turn_id", turnId == null ? "" : turnId);
            appendEvent(ROW_TURN_CONFIG, payload);
        } catch (Exception ignored) {
            // 见 appendEvent 的说明。
        }
    }

    public synchronized void appendEvent(String type, JSONObject payload) {
        try {
            append(new JSONObject()
                    .put("type", type)
                    .put("payload", payload == null ? new JSONObject() : payload)
                    .put("timestamp", System.currentTimeMillis()));
        } catch (Exception ignored) {
            // 事件行是辅助信息。写不进去时静默忽略，不能让「记录一个事件」这种动作
            // 把主流程（发消息、跑工具）带崩。
        }
    }

    public synchronized void appendCompaction(String summary, int removedMessages, int retainedMessages) {
        appendCompaction(summary, removedMessages, retainedMessages, -1, -1, "legacy", "");
    }

    /**
     * 记录一次上下文压缩。
     *
     * <p>token 计数用负数表示「未知」，此时字段不写入 —— 写 0 会被误读成
     * 「压缩后剩 0 个 token」。
     */
    public synchronized void appendCompaction(String summary, int removedMessages, int retainedMessages,
                                             int beforeTokens, int afterTokens, String trigger, String model) {
        try {
            JSONObject payload = new JSONObject()
                    .put("summary", summary == null ? "" : summary)
                    .put("removed_messages", removedMessages)
                    .put("retained_messages", retainedMessages)
                    .put("strategy", COMPACTION_STRATEGY)
                    .put("trigger", trigger == null ? "unknown" : trigger)
                    .put("model", model == null ? "" : model);
            if (beforeTokens >= 0) payload.put("before_tokens", beforeTokens);
            if (afterTokens >= 0) payload.put("after_tokens", afterTokens);
            appendEvent(ROW_CONTEXT_COMPACTION, payload);
        } catch (Exception ignored) {
            // 见 appendEvent 的说明。
        }
    }

    /**
     * 记录面向提供方的压缩后上下文。
     *
     * <p>完整的人类对话仍留在消息行里；这个快照只影响 {@link #loadMessages}。
     * 深拷贝一次再写入：调用方之后还会继续改它传入的数组。
     */
    public synchronized void appendContextSnapshot(JSONArray messages) {
        try {
            appendEvent(ROW_CONTEXT_SNAPSHOT, new JSONObject().put("messages",
                    messages == null ? new JSONArray() : new JSONArray(messages.toString())));
        } catch (Exception ignored) {
            // 见 appendEvent 的说明。
        }
    }

    public File getSessionFile() {
        return sessionFile;
    }

    /**
     * 更新会话的备注与标题覆盖。
     *
     * <p>写入前后<b>刻意不动文件的 mtime</b>：会话列表是按活跃时间排序的，
     * 而「改个备注」不是一次对话活动，让它跳到列表顶部会让人困惑。
     * 所以先把 mtime 记下来，追加完再恢复。
     */
    public static void updateSessionMetadata(File file, String note, String titleOverride) throws Exception {
        if (file == null || !file.isFile()) throw new IllegalArgumentException("会话不存在");
        File canonical = file.getCanonicalFile();
        SessionMetadata metadata = new SessionMetadata(note, titleOverride);
        JSONObject row = new JSONObject()
                .put("type", ROW_SESSION_METADATA)
                .put("payload", new JSONObject()
                        .put("note", metadata.note)
                        .put("title_override", metadata.titleOverride))
                .put("timestamp", System.currentTimeMillis());
        synchronized (fileLock(canonical)) {
            long modified = canonical.lastModified();
            appendRowSynced(canonical, row);
            canonical.setLastModified(modified);
            fsyncDirectory(canonical.getParentFile());
        }
    }

    // ------------------------------------------------------------ 读取基础

    /**
     * 逐行解析。
     *
     * <p>损坏的行被<b>跳过</b>而不是报错：会话文件是追加写的，一次断电完全可能
     * 留下半行。为了半行就让人打不开整段历史，代价太大。
     * 需要严格模式的调用方（编辑历史）走 {@link #readStrictRows}。
     */
    public static JSONArray readRows(File file) throws Exception {
        JSONArray rows = new JSONArray();
        if (file == null || !file.isFile()) return rows;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                try {
                    rows.put(new JSONObject(trimmed));
                } catch (Exception malformedLine) {
                    // 见方法注释：跳过损坏行。
                }
            }
        }
        return rows;
    }

    /**
     * 重建给提供方的消息列表。
     *
     * <p>规则：遇到 {@code context_snapshot} 就把已累积的结果<b>整体替换</b>为快照内容，
     * 之后出现的消息行再往后续接。这样「压缩之后又聊了几轮」能被正确还原，
     * 而压缩之前的完整对话仍然留在文件里给界面用。
     */
    public static JSONArray loadMessages(File file) throws Exception {
        JSONArray out = new JSONArray();
        JSONArray rows = readRows(file);
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;

            String type = row.optString("type");
            if (ROW_CONTEXT_SNAPSHOT.equals(type)) {
                JSONObject payload = row.optJSONObject("payload");
                JSONArray snapshot = payload == null ? null : payload.optJSONArray("messages");
                if (snapshot != null) out = new JSONArray(snapshot.toString());
                continue;
            }
            if (!ROW_MESSAGE.equals(type)) continue;

            JSONArray content = row.optJSONArray("content");
            if (content == null) continue;
            out.put(new JSONObject()
                    .put("role", row.optString("role", ROLE_USER))
                    .put("content", new JSONArray(content.toString())));
        }
        return mergeAdjacentSameRole(out);
    }

    /**
     * 把相邻的同角色消息合并成一条。
     *
     * <p>提供方（OpenAI / Anthropic）要求角色交替出现；而我们的历史里
     * 「人类发言 + 工具结果」都会以 user 角色出现，不合并就会被拒。
     *
     * <p>合并方式是<b>直接拼接内容块</b>，不重排。这一点很重要：
     * {@code tool_use} 与 {@code tool_result} 的配对靠的是相对顺序，
     * 一旦重排（例如按类型分组），提供方就会报「找不到对应的工具调用」。
     */
    private static JSONArray mergeAdjacentSameRole(JSONArray input) throws Exception {
        JSONArray mergedOutput = new JSONArray();
        for (int i = 0; i < input.length(); i++) {
            JSONObject message = input.optJSONObject(i);
            if (message == null) continue;
            JSONArray content = message.optJSONArray("content");
            if (content == null) continue;
            String role = message.optString("role", ROLE_USER);

            JSONObject previous = mergedOutput.length() == 0 ? null : mergedOutput.optJSONObject(mergedOutput.length() - 1);
            if (previous != null && role.equals(previous.optString("role"))) {
                JSONArray accumulated = previous.optJSONArray("content");
                for (int j = 0; j < content.length(); j++) {
                    accumulated.put(new JSONObject(content.getJSONObject(j).toString()));
                }
            } else {
                mergedOutput.put(new JSONObject()
                        .put("role", role)
                        .put("content", new JSONArray(content.toString())));
            }
        }
        return mergedOutput;
    }

    // ------------------------------------------------------------ 列表与摘要

    /** 全部会话（各项目目录），按活跃时间倒序。 */
    public static List<SessionSummary> listSessions() {
        List<SessionSummary> out = new ArrayList<>();
        addProjectDirectories(out, projectsDirectory());
        Collections.sort(out, (a, b) -> Long.compare(b.activityModifiedAt, a.activityModifiedAt));
        return out;
    }

    /**
     * 只列某个项目的会话。
     *
     * <p>{@code sessionDirectory(project)} 会顺带建出目录并刷新项目索引，
     * 所以这里用它，而不是自己拼一条路径。
     */
    public static List<SessionSummary> listSessions(String projectDirectory) {
        List<SessionSummary> out = new ArrayList<>();
        addSummaries(out, sessionDirectory(canonicalProject(projectDirectory)));
        Collections.sort(out, (a, b) -> Long.compare(b.modifiedAt, a.modifiedAt));
        return out;
    }

    /** 扫描一个「项目根」下的所有项目子目录。 */
    private static void addProjectDirectories(List<SessionSummary> out, File projectsRoot) {
        File[] directories = projectsRoot.listFiles(File::isDirectory);
        if (directories == null) return;
        for (File directory : directories) addSummaries(out, directory);
    }

    /**
     * 读一个文件算出摘要。
     *
     * <p>只需要一遍扫描，因为每一行都自带类型与时间戳：起始行给项目与创建时间，
     * 元数据行给备注与标题覆盖，消息行给条数与首个用户发言（作为标题兜底）。
     *
     * <p>活跃时间的口径是「最后一条<b>非元数据</b>行的时间戳」，
     * 而不是文件的 mtime —— 改备注会改 mtime，但不是一次对话活动。
     */
    public static SessionSummary summarize(File file) throws Exception {
        String project = "";
        String title = "";
        String note = "";
        String titleOverride = "";
        long created = file.lastModified();
        long activity = file.lastModified();
        int count = 0;

        JSONArray rows = readRows(file);
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;
            String type = row.optString("type", "");
            long timestamp = row.optLong("timestamp", 0L);
            if (!ROW_SESSION_METADATA.equals(type) && timestamp > activity) activity = timestamp;

            if (ROW_SESSION_START.equals(type)) {
                project = row.optString("project", project);
                created = row.optLong("created_at", created);
            } else if (ROW_SESSION_METADATA.equals(type)) {
                JSONObject payload = row.optJSONObject("payload");
                if (payload != null) {
                    note = sanitizeMetadata(payload.optString("note", ""), MAX_NOTE_CHARS);
                    titleOverride = sanitizeMetadata(payload.optString("title_override", ""), MAX_TITLE_OVERRIDE_CHARS)
                            .replace('\n', ' ');
                }
            } else if (ROW_MESSAGE.equals(type)) {
                count++;
                if (title.isEmpty() && ROLE_USER.equals(row.optString("role"))) {
                    title = firstHumanText(row.optJSONArray("content"));
                }
            }
        }

        if (!titleOverride.isEmpty()) title = titleOverride;
        if (title.isEmpty()) {
            // 没有可用发言时退回项目目录名；连项目都不知道就叫 Session。
            String folder = project.isEmpty() ? "" : new File(project).getName();
            title = folder == null || folder.isEmpty() ? "Session" : folder;
        }
        return new SessionSummary(file, project, compactTitle(title), note, titleOverride, created, activity, count);
    }

    private static void addSummaries(List<SessionSummary> out, File directory) {
        File[] files = directory.listFiles((dir, name) -> name.endsWith(SESSION_SUFFIX));
        if (files == null) return;
        for (File file : files) {
            try {
                out.add(summarize(file));
            } catch (Exception ignored) {
                // 单个文件读不出来时跳过它，其余会话照常列出。
            }
        }
    }

    // ------------------------------------------------------------ 删除

    /**
     * 删除一次会话。
     *
     * <p>只允许删 {@code projects/<项目键>/} 的直接子 {@code .jsonl} 文件。
     * 这道检查是在防「路径来自界面」的场景 —— 一个被拼错的路径不该让这个方法
     * 变成任意文件删除器。
     */
    public static boolean deleteSession(File file) {
        if (file == null) return false;
        try {
            File projects = projectsDirectory().getCanonicalFile();
            File target = file.getCanonicalFile();

            File parent = target.getParentFile();
            boolean projectSession = under(projects, target)
                    && parent != null
                    && parent.getParentFile() != null
                    && parent.getParentFile().equals(projects);
            if (!projectSession || !target.getName().endsWith(SESSION_SUFFIX)) return false;

            synchronized (fileLock(target)) {
                return !target.exists() || target.delete();
            }
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------------ 派生信息

    /** 会话的 workflow id；历史里没有就按规范路径派生一个稳定的。 */
    public static String loadWorkflowId(File file) {
        try {
            JSONArray rows = readRows(file);
            String workflow = "";
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row == null) continue;
                if (ROW_SESSION_START.equals(row.optString("type"))) {
                    workflow = row.optString("workflow_id", workflow);
                }
                JSONObject payload = row.optJSONObject("payload");
                String type = row.optString("type");
                if (payload != null && ("config".equals(type) || type.startsWith("plan_"))) {
                    workflow = payload.optString("workflow_id", workflow);
                }
            }
            if (!workflow.trim().isEmpty()) return workflow;
        } catch (Exception ignored) {
            // 下面用路径派生兜底。
        }
        try {
            return "session-" + shortHash(file.getCanonicalPath());
        } catch (Exception e) {
            return "session-" + shortHash(file == null ? "session" : file.getAbsolutePath());
        }
    }

    /** 最后一次记录下来的 API 配置绑定；没有则返回 null。 */
    public static ProfileBinding loadProfileBinding(File file) {
        try {
            JSONArray rows = readRows(file);
            ProfileBinding binding = null;
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row == null) continue;
                String type = row.optString("type", "");
                if (!ROW_PROFILE_BINDING.equals(type) && !ROW_TURN_CONFIG.equals(type)) continue;
                JSONObject payload = row.optJSONObject("payload");
                // 取最后一条有效的绑定：中途换过配置时，以最新那次为准。
                if (payload != null && !payload.optString("profile_id", "").isEmpty()) {
                    binding = new ProfileBinding(payload);
                }
            }
            return binding;
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 计划流程状态：取最后一条 plan_* 事件。 */
    public static PlanWorkflowState loadPlanState(File file) {
        String workflow = loadWorkflowId(file);
        PlanWorkflowState state = PlanWorkflowState.idle();
        try {
            JSONArray rows = readRows(file);
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row == null) continue;
                String type = row.optString("type", "");
                if (!type.startsWith("plan_")) continue;
                JSONObject payload = row.optJSONObject("payload");
                if (payload == null) continue;

                PlanWorkflowState.Status status;
                try {
                    status = PlanWorkflowState.Status.valueOf(payload.optString("status", ""));
                } catch (Exception unknownStatus) {
                    // 旧记录里可能没有 status 字段，按事件类型推断。
                    status = statusForEventType(type);
                }
                state = PlanWorkflowState.restore(status,
                        payload.optString("workflow_id", workflow),
                        payload.optLong("revision", state.revision),
                        payload.optString("previous_permission_mode", state.previousPermissionMode),
                        payload.optString("approved_permission_mode", state.approvedPermissionMode),
                        payload.optString("plan_file", state.planFile),
                        payload.optString("plan_text", state.planText),
                        payload.optString("feedback", state.feedback),
                        payload.optLong("updated_at", row.optLong("timestamp", System.currentTimeMillis())));
            }
        } catch (Exception ignored) {
            // 读不出来就当还没进入过计划模式。
        }
        return state;
    }

    private static PlanWorkflowState.Status statusForEventType(String type) {
        if ("plan_entered".equals(type)) return PlanWorkflowState.PLANNING;
        if ("plan_proposed".equals(type)) return PlanWorkflowState.AWAITING_APPROVAL;
        if ("plan_approved".equals(type)) return PlanWorkflowState.EXECUTING;
        if ("plan_cancelled".equals(type)) return PlanWorkflowState.CANCELLED;
        return PlanWorkflowState.PLANNING;
    }

    // ------------------------------------------------------------ 消息引用

    /** 内容块的稳定摘要，用于校验「要改的确实是这一条」。 */
    public static String messageContentHash(JSONArray content) {
        return shortHash(content == null ? "[]" : content.toString());
    }

    /**
     * 判断一行是否可以作为一个可编辑的编辑目标。
     *
     * <p>只有 user / assistant 两种角色的消息行算数，且必须含有可编辑内容：
     * <ul>
     *   <li>user：{@code origin} 必须为空或 {@code human}（工具结果也是 user 角色，
     *       但它们不是人写的，不该出现在可编辑列表里），且必须含真实人类内容；</li>
     *   <li>assistant：必须含 text 或 thinking 正文。</li>
     * </ul>
     */
    public static MessageReference messageReference(JSONObject row, int rowIndex) {
        if (row == null || !ROW_MESSAGE.equals(row.optString("type", ""))) return null;
        String role = row.optString("role", "");
        if (!ROLE_USER.equals(role) && !ROLE_ASSISTANT.equals(role)) return null;

        if (ROLE_USER.equals(role)) {
            String origin = row.optString("origin", "");
            if (!origin.isEmpty() && !"human".equals(origin)) return null;
        }
        JSONArray content = row.optJSONArray("content");
        if (ROLE_USER.equals(role) && !hasEditableContent(content, ROLE_USER)) return null;
        if (ROLE_ASSISTANT.equals(role) && !hasEditableContent(content, ROLE_ASSISTANT)) return null;

        return new MessageReference(row.optString("message_id", ""), rowIndex, messageContentHash(content));
    }

    public static void editHumanMessage(File file, MessageReference target, String newText) throws Exception {
        String replacement = newText == null ? "" : newText.trim();
        if (replacement.isEmpty()) throw new IllegalArgumentException("消息不能为空");
        mutateMessage(file, target, replacement, false);
    }

    public static void deleteHumanMessage(File file, MessageReference target) throws Exception {
        mutateMessage(file, target, "", true);
    }

    /** 编辑用户/助手的正文，或助手的思考内容。 */
    public static void editMessage(File file, MessageReference target, String replacement) throws Exception {
        String value = replacement == null ? "" : replacement.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("消息内容不能为空");
        mutateMessage(file, target, value, false);
    }

    public static void deleteMessage(File file, MessageReference target) throws Exception {
        mutateMessage(file, target, "", true);
    }

    /**
     * 改写或删除一条消息，重写整个文件。
     *
     * <p>两个关键决策：
     * <ol>
     *   <li><b>未改动的行原样照抄</b>（用 {@link StrictRow#raw}），不重新序列化 ——
     *       重新序列化会改动键顺序与转义方式，让整个文件产生无意义的 diff。</li>
     *   <li><b>目标之后的所有 {@code context_*} 行一律丢弃</b>。那些
     *       {@code context_snapshot} / {@code context_compaction} 是基于
     *       「包含这条消息」的上下文生成的；留着它们，模型会继续看到改之前的版本，
     *       而界面显示的是改之后的 —— 这个不一致极难排查。
     *       丢弃等于让引擎下次重新压缩一次，代价可以接受。</li>
     * </ol>
     */
    private static void mutateMessage(File file, MessageReference target, String replacement, boolean delete) throws Exception {
        if (file == null || !file.isFile() || target == null) throw new IllegalArgumentException("无效的会话消息");
        File canonical = file.getCanonicalFile();
        synchronized (fileLock(canonical)) {
            List<StrictRow> rows = readStrictRows(canonical);
            int targetIndex = findTargetRow(rows, target);
            if (targetIndex < 0) throw new IllegalStateException("要修改的历史消息已变化，请重新打开会话");

            ArrayList<String> output = new ArrayList<>(rows.size());
            for (int i = 0; i < rows.size(); i++) {
                StrictRow row = rows.get(i);
                if (i == targetIndex) {
                    if (!delete) output.add(rewriteTextBlock(row.json, replacement).toString());
                    continue;
                }
                if (i > targetIndex && row.json != null && isContextEvent(row.json.optString("type", ""))) continue;
                output.add(row.raw);
            }
            atomicReplace(canonical, output, canonical.lastModified());
        }
    }

    /**
     * 定位要改的那一行。
     *
     * <p>校验策略：先按 id 或旧行号筛出候选，再要求<b>内容摘要也一致</b>。
     * 摘要这一项是必需的 —— 光凭 id 无法发现「消息已被别处改过」，
     * 那样会用一份过期的内容去覆盖一份新的。
     *
     * <p>重复命中一律拒绝，而不是取第一个：出现重复说明引用本身已经不可信。
     */
    private static int findTargetRow(List<StrictRow> rows, MessageReference target) throws Exception {
        if (target.contentHash.trim().isEmpty()) throw new IllegalArgumentException("消息引用缺少内容校验");
        boolean byId = !target.messageId.trim().isEmpty();
        if (!byId && target.legacyRowIndex < 0) throw new IllegalArgumentException("消息引用缺少稳定位置");

        if (byId) {
            int occurrences = 0;
            for (StrictRow row : rows) {
                if (row.json != null
                        && ROW_MESSAGE.equals(row.json.optString("type", ""))
                        && target.messageId.equals(row.json.optString("message_id", ""))) {
                    occurrences++;
                }
            }
            if (occurrences > 1) throw new IllegalStateException("消息标识重复，已拒绝修改");
        }

        int match = -1;
        for (int i = 0; i < rows.size(); i++) {
            StrictRow row = rows.get(i);
            if (row.json == null) continue;
            MessageReference candidate = messageReference(row.json, row.jsonIndex);
            if (candidate == null) continue;

            boolean matches;
            if (byId) {
                matches = target.messageId.equals(candidate.messageId)
                        && target.contentHash.equals(candidate.contentHash);
            } else {
                matches = candidate.messageId.isEmpty()
                        && target.legacyRowIndex == candidate.legacyRowIndex
                        && target.contentHash.equals(candidate.contentHash);
            }
            if (!matches) continue;
            if (match >= 0) throw new IllegalStateException("消息标识重复，已拒绝修改");
            match = i;
        }
        return match;
    }

    /**
     * 替换消息里的第一个可编辑文本块。
     *
     * <p>只改第一个：一条消息可能同时含正文与思考，两者都属于「可编辑」，
     * 但用户编辑的是他看到的那个正文。改第一个可编辑块是与界面语义一致的。
     * 若一个可编辑块都没有（例如只有图片），就补一个新的文本块，
     * 而不是静默地什么都没改。
     */
    private static JSONObject rewriteTextBlock(JSONObject original, String replacement) throws Exception {
        JSONObject rewritten = new JSONObject(original.toString());
        JSONArray before = original.optJSONArray("content");
        if (before == null) throw new IllegalStateException("消息内容无效");

        JSONArray after = new JSONArray();
        boolean replaced = false;
        for (int i = 0; i < before.length(); i++) {
            JSONObject block = before.optJSONObject(i);
            if (block == null) continue;
            String type = block.optString("type", "");

            boolean editable = isEditableBlock(block, type);
            if (editable) {
                if (!replaced) {
                    JSONObject edited = new JSONObject(block.toString());
                    if (BLOCK_TEXT.equals(type)) edited.put("text", replacement);
                    else edited.put("thinking", replacement);
                    after.put(edited);
                    replaced = true;
                }
                // 同一消息里的第二个及之后的文本/思考块直接丢弃：
                // 它们与用户看到的正文是同一段内容的重复表述。
                continue;
            }
            after.put(new JSONObject(block.toString()));
        }
        if (!replaced) after.put(new JSONObject().put("type", BLOCK_TEXT).put("text", replacement));
        rewritten.put("content", after);
        return rewritten;
    }

    /** 一个内容块是否可被用户编辑。内部注入的块（上下文摘要、续跑指令）不算。 */
    private static boolean isEditableBlock(JSONObject block, String type) {
        String text = block.optString("text", "");
        String thinking = block.optString("thinking", "");
        if (BLOCK_TEXT.equals(type)) {
            return !isInternalMarkerText(text.trim());
        }
        if (BLOCK_THINKING.equals(type) || BLOCK_REASONING.equals(type)) {
            return !thinking.trim().isEmpty();
        }
        return false;
    }

    /**
     * 这段文本是不是我们自己注入的内部标记。
     */
    private static boolean isInternalMarkerText(String text) {
        return text.startsWith(CONTEXT_SUMMARY_MARKER)
                || text.startsWith(INTERNAL_CONTINUE_MARKER);
    }

    /**
     * 完整的内部续跑文本。
     *
     * <p>放在这里而不是让 {@code ZhiCodeEngine} 自己拼字面量：这个串会写进历史，
     * 而识别它的逻辑就在本类。两处各写一份的话，哪天改了一处就会出现
     * 「续跑消息被当成人话」这种只在旧会话上复现的问题。
     */
    public static String internalContinuationText(String stopReason) {
        String reason = stopReason == null ? "unknown" : stopReason;
        return INTERNAL_CONTINUE_MARKER
                + "Previous model output stopped because of " + reason
                + ". Continue the same task from exactly where it stopped."
                + " Do not repeat completed work; inspect the latest tool/results and continue"
                + " until the user's requested task is actually complete."
                + "</zhicode_internal_continue>";
    }

    /** 压缩相关的派生事件。改过历史之后它们都失效，必须一起丢弃。 */
    private static boolean isContextEvent(String type) {
        if (type == null) return false;
        return ROW_CONTEXT_SNAPSHOT.equals(type)
                || ROW_CONTEXT_COMPACTION.equals(type)
                || "context_usage".equals(type)
                || "context_compaction_failed".equals(type)
                || type.startsWith("context_");
    }

    /**
     * 严格读取：任何一行解析失败就抛出，并带上物理行号。
     *
     * <p>与 {@link #readRows} 的宽容模式相反。改历史的场景必须先保证
     * 「整个文件都读懂了」——否则重写会把没读懂的那些行原样抄回去还是丢掉？
     * 两种选择都可能毁数据，所以直接拒绝这次操作。
     *
     * <p>行号只对可解析的行递增（{@code jsonIndex}），因为旧格式的引用是按
     * 「第几条 JSON」计的，而不是按物理行。
     */
    private static List<StrictRow> readStrictRows(File file) throws Exception {
        ArrayList<StrictRow> rows = new ArrayList<>();
        int jsonIndex = 0;
        int physicalLine = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                physicalLine++;
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    rows.add(new StrictRow(line, null, -1));
                    continue;
                }
                try {
                    rows.add(new StrictRow(line, new JSONObject(trimmed), jsonIndex++));
                } catch (Exception broken) {
                    throw new IllegalStateException("会话历史第 " + physicalLine + " 行损坏，已拒绝修改", broken);
                }
            }
        }
        return rows;
    }

    // ------------------------------------------------------------ 原子替换

    /**
     * 原子替换整个文件。
     *
     * <p>顺序固定：写临时文件 → {@code fsync} → POSIX {@code rename} → 恢复 mtime → fsync 目录。
     * 最后那步 fsync 目录容易被漏掉：rename 本身要落在目录项上，
     * 不 fsync 目录的话，掉电后可能出现「文件内容在、目录项没更新」的中间状态。
     */
    private static void atomicReplace(File target, List<String> rows, long originalModifiedAt) throws Exception {
        File parent = target.getParentFile();
        if (parent == null) throw new IllegalStateException("会话目录无效");
        File temporary = new File(parent, target.getName() + ".rewrite-" + UUID.randomUUID() + ".tmp");
        try (FileOutputStream stream = new FileOutputStream(temporary, false)) {
            for (String row : rows) {
                stream.write(row.getBytes(StandardCharsets.UTF_8));
                stream.write('\n');
            }
            stream.flush();
            stream.getFD().sync();
        } catch (Exception writeFailed) {
            temporary.delete();
            throw writeFailed;
        }
        try {
            Os.rename(temporary.getAbsolutePath(), target.getAbsolutePath());
            target.setLastModified(originalModifiedAt);
            fsyncDirectory(parent);
        } catch (Exception renameFailed) {
            temporary.delete();
            throw renameFailed;
        }
    }

    /** fsync 目录，确保目录项变更真正落盘。拿不到 fd 时静默跳过。 */
    private static void fsyncDirectory(File directory) {
        if (directory == null) return;
        FileDescriptor descriptor = null;
        try {
            descriptor = Os.open(directory.getAbsolutePath(), OsConstants.O_RDONLY, 0);
            Os.fsync(descriptor);
        } catch (Exception ignored) {
            // 部分文件系统不支持对目录 fsync；这不是致命问题。
        } finally {
            if (descriptor != null) {
                try {
                    Os.close(descriptor);
                } catch (Exception ignored) {
                    // 关闭失败无需处理。
                }
            }
        }
    }

    // ------------------------------------------------------------ 可编辑判定

    /**
     * 一个内容块数组里是否存在「真实内容」。
     *
     * <p>user 角色的图片算内容（只发图也是发言）；
     * assistant 的思考算内容（有些模型只产出思考）。
     * 其余情况要求文本非空。
     */
    private static boolean hasEditableContent(JSONArray content, String role) {
        if (content == null) return false;
        for (int i = 0; i < content.length(); i++) {
            JSONObject block = content.optJSONObject(i);
            if (block == null) continue;
            String type = block.optString("type", "");
            if (ROLE_USER.equals(role) && BLOCK_IMAGE.equals(type)) return true;
            boolean textLike = BLOCK_TEXT.equals(type)
                    || (ROLE_ASSISTANT.equals(role) && (BLOCK_THINKING.equals(type) || BLOCK_REASONING.equals(type)));
            if (!textLike) continue;
            String key = BLOCK_TEXT.equals(type) ? "text" : "thinking";
            if (!block.optString(key, "").trim().isEmpty()) return true;
        }
        return false;
    }

    /**
     * 取第一条真正的人类发言，用作会话标题。
     *
     * <p>跳过内部注入的文本（上下文摘要、续跑指令）：
     * 拿它们当标题会得到一串没有信息量的标记。
     */
    private static String firstHumanText(JSONArray content) {
        if (content == null) return "";
        for (int i = 0; i < content.length(); i++) {
            JSONObject block = content.optJSONObject(i);
            if (block == null) continue;
            if (!BLOCK_TEXT.equals(block.optString("type"))) continue;
            String text = block.optString("text", "").trim();
            if (!text.isEmpty() && !isInternalMarkerText(text)) return text;
        }
        return "";
    }

    // ------------------------------------------------------------ 小工具

    /** 标题压成单行并截断。用省略号而不是硬切，让人知道还有内容。 */
    private static String compactTitle(String value) {
        String text = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').trim();
        return text.length() <= MAX_TITLE_CHARS ? text : text.substring(0, MAX_TITLE_CHARS - 1) + "…";
    }

    /**
     * 清理元数据文本。
     *
     * <p>保留换行与制表符，其余控制字符删掉 —— 它们会在列表渲染时变成乱码方块。
     * 按字符数截断而不是字节数：这里的上限是给人看的宽度。
     */
    private static String sanitizeMetadata(String value, int maxChars) {
        String input = value == null ? "" : value.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder clean = new StringBuilder(Math.min(input.length(), maxChars));
        for (int i = 0; i < input.length() && clean.length() < maxChars; i++) {
            char c = input.charAt(i);
            if (c == '\n' || c == '\t' || !Character.isISOControl(c)) clean.append(c);
        }
        return clean.toString().trim();
    }

    private static JSONObject profilePayload(SessionConfig config) {
        JSONObject payload = new JSONObject();
        if (config == null) return payload;
        try {
            payload.put("profile_id", config.profileId == null ? "" : config.profileId);
            payload.put("profile_revision", config.profileRevision);
            payload.put("credential_revision", config.credentialRevision);
            payload.put("protocol", config.protocol == null ? "" : config.protocol);
            payload.put("base_url", config.baseUrl == null ? "" : config.baseUrl);
            payload.put("model", config.model == null ? "" : config.model);
        } catch (Exception ignored) {
            // 组装失败时返回已放入的字段；调用方按缺字段处理。
        }
        return payload;
    }

    // ------------------------------------------------------------ 文件操作

    private void append(JSONObject row) throws Exception {
        File target = sessionFile.getCanonicalFile();
        synchronized (fileLock(target)) {
            appendRow(target, row);
        }
    }

    /** 追加一行。不 fsync：消息是高频写入，每次都 fsync 会让打字明显卡顿。 */
    private static void appendRow(File target, JSONObject row) throws Exception {
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory()) parent.mkdirs();
        try (FileOutputStream out = new FileOutputStream(target, true)) {
            out.write(row.toString().getBytes(StandardCharsets.UTF_8));
            out.write('\n');
            out.flush();
        }
    }

    /** 追加一行并 fsync。用于低频但重要的写入（例如改备注），保证它不会因掉电丢失。 */
    private static void appendRowSynced(File target, JSONObject row) throws Exception {
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory()) parent.mkdirs();
        try (FileOutputStream out = new FileOutputStream(target, true)) {
            out.write(row.toString().getBytes(StandardCharsets.UTF_8));
            out.write('\n');
            out.flush();
            out.getFD().sync();
        }
    }

    /** 写项目索引，供界面显示项目名。失败不影响会话功能，因此静默。 */
    private static void writeProjectIndex(File directory, String project) {
        try {
            File index = new File(directory, "project.json");
            JSONObject content = new JSONObject()
                    .put("version", 1)
                    .put("project", project)
                    .put("display_name", new File(project).getName())
                    .put("updated_at", System.currentTimeMillis());
            try (FileOutputStream out = new FileOutputStream(index, false)) {
                out.write(content.toString(2).getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
            // 见方法注释。
        }
    }

    /**
     * 同一文件的进程内锁。
     *
     * <p>用规范路径做键：同一个文件通过不同写法（符号链接、相对路径）进来时必须拿到同一把锁。
     * 先 putIfAbsent 再取，避免两次检查之间被别的线程插入。
     */
    private static Object fileLock(File file) throws Exception {
        String key = file.getCanonicalPath();
        Object created = new Object();
        Object existing = FILE_LOCKS.putIfAbsent(key, created);
        return existing == null ? created : existing;
    }

    // ------------------------------------------------------------ 判定工具

    /** child 是否位于 root 之下（不含 root 自身）。 */
    private static boolean under(File root, File child) {
        try {
            String prefix = root.getCanonicalPath() + File.separator;
            return child.getCanonicalPath().startsWith(prefix);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 前 6 字节的 SHA-256。
     *
     * <p>12 个十六进制字符。用作路径键的一部分时，碰撞概率远低于实际会遇到的目录数。
     * 拿不到摘要算法时退回 hashCode —— 质量差一些，但比抛异常好。
     */
    private static String shortHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(12);
            for (int i = 0; i < 6; i++) hex.append(String.format(Locale.US, "%02x", digest[i] & 255));
            return hex.toString();
        } catch (Exception noDigest) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
