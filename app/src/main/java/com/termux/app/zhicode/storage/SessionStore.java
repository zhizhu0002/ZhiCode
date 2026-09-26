package com.termux.app.zhicode.storage;

import android.system.Os;
import android.system.OsConstants;

import com.termux.app.zhicode.json.JsonItems;
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
 * <h3>一次会话就是一个 JSONL 文件</h3>
 * 项目路径先规范化，再按规范化结果分命名空间：
 * {@code ~/.zhicode/projects/<路径键>/<时间>-<随机>.jsonl}。
 * 一行一个 JSON 对象，只追加。选 JSONL 而不是单个大 JSON 的理由有两条：
 * 追加不必重写整个文件；而且它天然可读、可手工修复。
 *
 * <h3>流水行与快照行</h3>
 * <ul>
 *   <li><b>流水行</b>（{@code message} / {@code session_start} / {@code plan_*} 等）只追加，
 *       永不修改。界面渲染的完整对话就是它们。</li>
 *   <li><b>快照行</b>（{@code context_snapshot}）是给模型看的压缩后上下文。
 *       {@link #loadMessages} 遇到它就<b>整体替换</b>已读到的内容，之后的消息行再往后续接。
 *       要点：它<b>不删</b>任何流水行，所以界面照旧显示完整原始对话，
 *       而模型只看到压缩后的版本。</li>
 * </ul>
 *
 * <h3>改一条历史消息为什么要引用校验</h3>
 * JSONL 无法原地改一行，所以 {@link #editMessage} / {@link #deleteMessage} 是整文件重写。
 * 危险在于：用户按下的是「第 3 条消息」，而磁盘上的行号会随快照行、损坏行、空行漂移，
 * 中间还可能有别的进程在追加。因此调用方必须给出 {@code messageId}（或旧行号）
 * <b>外加内容摘要</b>；对不上就拒绝修改（「要修改的历史消息已变化」），而不是猜一条去改。
 *
 * <p>还有一个不显眼但必须做对的点：删掉一条历史之后，它<b>之后</b>的 {@code context_*}
 * 行要一并丢弃。那些快照是基于「包含这条消息」的上下文生成的，留着会让模型继续看到
 * 改之前的版本，而界面显示的是改之后的 —— 这种不一致极难排查。
 *
 * <h3>并发与崩溃安全</h3>
 * 同一个文件的写入按规范路径取进程内锁串行化；跨进程靠「追加 + 原子替换」。
 * 所有整文件重写都走「写临时文件 → fsync → rename」，替换完成后恢复原有的 mtime ——
 * 这样「改一条备注」不会把会话在列表里的活跃时间顶到最上面。
 */
public final class SessionStore {

    // ------------------------------------------------------------ 文件与长度

    private static final String SESSION_SUFFIX = ".jsonl";
    private static final String PROJECT_INDEX_FILE = "project.json";

    /** 备注长度上限。它只用于列表显示，不需要更长。 */
    private static final int MAX_NOTE_CHARS = 500;
    /** 标题覆盖长度上限。 */
    private static final int MAX_TITLE_OVERRIDE_CHARS = 120;
    /** 自动生成标题（取人类首条发言）的长度上限。 */
    private static final int MAX_TITLE_CHARS = 48;
    /** 路径键里可读段的长度上限。 */
    private static final int MAX_SLUG_CHARS = 72;

    /** 压缩策略标识。写进历史，便于事后判断某次压缩是谁做的。 */
    private static final String COMPACTION_STRATEGY = "model_semantic_api_round_v2";

    /** 上下文摘要块的起始标记，由 {@code ContextCompactor} 写入。 */
    private static final String CONTEXT_SUMMARY_MARKER = "<context_summary>";

    /**
     * 内部续跑标记。
     *
     * <p>由 {@code ZhiCodeEngine} 在「模型输出被截断、需要继续」时写进历史。它的作用就是让
     * 这段文本<b>不被当成人类发言</b>：否则它会变成会话标题，还会出现在可编辑的用户消息里。
     */
    private static final String INTERNAL_CONTINUE_MARKER = "<zhicode_internal_continue>";

    /** 每个文件一把进程内锁；键是规范路径。跨进程由「追加 + 原子替换」保证。 */
    private static final ConcurrentHashMap<String, Object> FILE_LOCKS = new ConcurrentHashMap<>();

    // ------------------------------------------------------------ 固定字符串

    /**
     * 行类型。这些串会写进历史文件，属于持久化契约：改一个就等于读不懂旧会话。
     */
    private static final class RowType {
        static final String SESSION_START = "session_start";
        static final String MESSAGE = "message";
        static final String METADATA = "session_metadata";
        static final String SNAPSHOT = "context_snapshot";
        static final String COMPACTION = "context_compaction";
        static final String PROFILE_BINDING = "profile_binding";
        static final String TURN_CONFIG = "turn_config";

        private RowType() {}
    }

    /** 消息角色。只有这两种会被写进历史。 */
    private static final class Role {
        static final String USER = "user";
        static final String ASSISTANT = "assistant";

        private Role() {}
    }

    /** 内容块类型。{@code thinking} 与 {@code reasoning} 是同一种东西的两种历史写法。 */
    private static final class Block {
        static final String TEXT = "text";
        static final String THINKING = "thinking";
        static final String REASONING = "reasoning";
        static final String IMAGE = "image";

        private Block() {}
    }

    /**
     * JSON 字段名。
     *
     * <p>集中在一处是因为同一个字段名（例如 {@code payload}）在本类里出现十几次，
     * 每处各写一份字面量的话，改字段名时一定会漏掉某一处，而漏掉的那处只会表现为
     * 「某类历史读不出来」。
     */
    private static final class Field {
        static final String TYPE = "type";
        static final String ROLE = "role";
        static final String CONTENT = "content";
        static final String PAYLOAD = "payload";
        static final String TIMESTAMP = "timestamp";
        static final String MESSAGE_ID = "message_id";
        static final String TURN_ID = "turn_id";
        static final String ORIGIN = "origin";
        static final String PROJECT = "project";
        static final String PROJECT_KEY = "project_key";
        static final String CREATED_AT = "created_at";
        static final String WORKFLOW_ID = "workflow_id";
        static final String TEXT = "text";
        static final String THINKING = "thinking";
        static final String PROFILE_ID = "profile_id";
        static final String PROFILE_REVISION = "profile_revision";
        static final String CREDENTIAL_REVISION = "credential_revision";
        static final String PROTOCOL = "protocol";
        static final String BASE_URL = "base_url";
        static final String MODEL = "model";
        static final String MESSAGES = "messages";

        private Field() {}
    }

    // ------------------------------------------------------------ 值对象

    /** 一次会话的备注与标题覆盖。两者都要清理过才落盘。 */
    public static final class SessionMetadata {
        public final String note;
        public final String titleOverride;

        public SessionMetadata(String note, String titleOverride) {
            this.note = sanitizeMetadata(note, MAX_NOTE_CHARS);
            // 标题是单行的：换行会让它在列表里被截成两截，看起来像半个标题。
            this.titleOverride = singleLine(sanitizeMetadata(titleOverride, MAX_TITLE_OVERRIDE_CHARS));
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
        /** 最后一条非元数据事件的时间。改备注不得改变它。 */
        public final long activityModifiedAt;
        /** 兼容旧调用方的别名，与 {@link #activityModifiedAt} 同值。 */
        public final long modifiedAt;
        public final int messageCount;

        SessionSummary(File file, String project, String title, String note, String titleOverride,
                       long createdAt, long activityModifiedAt, int messageCount) {
            this.file = file;
            this.project = orEmpty(project);
            String candidate = orEmpty(title).trim();
            this.title = candidate.isEmpty() ? file.getName() : candidate;
            this.note = orEmpty(note);
            this.titleOverride = orEmpty(titleOverride);
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
     * （旧格式）<b>加上</b> {@code contentHash}。任何一项对不上就拒绝修改。
     */
    public static final class MessageReference {
        public final String messageId;
        public final int legacyRowIndex;
        public final String contentHash;

        public MessageReference(String messageId, int legacyRowIndex, String contentHash) {
            this.messageId = orEmpty(messageId);
            this.legacyRowIndex = legacyRowIndex;
            this.contentHash = orEmpty(contentHash);
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
            profileId = payload.optString(Field.PROFILE_ID, "");
            profileRevision = Math.max(1, payload.optInt(Field.PROFILE_REVISION, 1));
            credentialRevision = Math.max(1, payload.optInt(Field.CREDENTIAL_REVISION, 1));
            protocol = payload.optString(Field.PROTOCOL, "");
            baseUrl = payload.optString(Field.BASE_URL, "");
            model = payload.optString(Field.MODEL, "");
        }
    }

    /**
     * 文件里的一行。
     *
     * <p>{@link #raw} 留着是为了重写时原样抄回去：重新序列化会改变键顺序与转义方式，
     * 让整个文件产生一堆无意义的 diff，也更容易出错。
     * {@link #json} 为 null 表示这行没解出来（空行或损坏行）。
     */
    private static final class JsonLine {
        final String raw;
        final JSONObject json;
        /** 只在解析成功时递增的序号，旧格式的行号引用按它计。 */
        final int index;

        JsonLine(String raw, JSONObject json, int index) {
            this.raw = raw;
            this.json = json;
            this.index = index;
        }
    }

    private final File sessionFile;

    // ------------------------------------------------------------ 构造与路径

    /** 在一个项目下新建一次会话，并写下起始行。 */
    public SessionStore(String projectDirectory) {
        String project = canonicalProject(projectDirectory);
        File directory = sessionDirectory(project);
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        sessionFile = new File(directory, stamp + "-" + UUID.randomUUID().toString().substring(0, 8)
                + SESSION_SUFFIX);
        try {
            appendLine(new JSONObject()
                    .put(Field.TYPE, RowType.SESSION_START)
                    .put(Field.PROJECT, project)
                    .put(Field.PROJECT_KEY, projectKey(project))
                    .put(Field.CREATED_AT, System.currentTimeMillis()));
        } catch (Exception ignored) {
            // 起始行写不进去不该让构造失败：后续追加会再建文件，而 summarize()
            // 对缺失起始行有兜底（项目名留空、创建时间取 mtime）。
        }
    }

    private SessionStore(File existing) {
        sessionFile = existing;
    }

    public static SessionStore resume(File existing) {
        if (existing == null || !existing.isFile()) throw new IllegalArgumentException("会话文件不存在");
        return new SessionStore(existing);
    }

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

    /** 规范化项目路径：空值落到 Termux HOME；能取规范路径就取（顺带解开符号链接）。 */
    public static String canonicalProject(String projectDirectory) {
        String raw = isBlank(projectDirectory) ? TermuxConstants.TERMUX_HOME_DIR_PATH : projectDirectory.trim();
        try {
            return new File(raw).getCanonicalPath();
        } catch (Exception notResolvable) {
            return new File(raw).getAbsolutePath();
        }
    }

    /**
     * 目录名 = 可读段 + 短哈希。
     *
     * <p>只用可读段会撞车（{@code a/b} 与 {@code a_b} 会变成同一个），只用哈希则没人看得懂。
     * 拼接之后同名目录几乎不可能出现，而人还能一眼认出是哪个项目。
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

    public File getSessionFile() {
        return sessionFile;
    }

    // ------------------------------------------------------------ 写入

    public synchronized String appendMessage(String role, JSONArray content) {
        return appendMessage(role, content, "", "", "");
    }

    /**
     * 追加一条消息行。
     *
     * @return 这条消息的 id（调用方给了就用给的），供以后编辑/删除精确定位
     */
    public synchronized String appendMessage(String role, JSONArray content, String messageId,
                                             String turnId, String origin) {
        String id = isBlank(messageId) ? UUID.randomUUID().toString() : messageId;
        try {
            JSONObject row = new JSONObject()
                    .put(Field.TYPE, RowType.MESSAGE)
                    .put(Field.ROLE, role)
                    .put(Field.CONTENT, content)
                    .put(Field.MESSAGE_ID, id)
                    .put(Field.TIMESTAMP, System.currentTimeMillis());
            // 这两个字段只在有值时写：写空串不只是让文件变长，还会让
            // 「这条消息有没有 turn 归属」这件事没法用「字段存在与否」判断。
            if (!isBlank(turnId)) row.put(Field.TURN_ID, turnId);
            if (!isBlank(origin)) row.put(Field.ORIGIN, origin);
            appendLine(row);
        } catch (Exception ignored) {
            // 写失败仍然返回 id：调用方拿到的是「这条消息的标识」，不是写入成功的凭据。
        }
        return id;
    }

    public synchronized void appendProfileBinding(SessionConfig config) {
        appendEvent(RowType.PROFILE_BINDING, profilePayload(config));
    }

    public synchronized void appendTurnConfig(SessionConfig config, String turnId) {
        try {
            JSONObject payload = profilePayload(config);
            payload.put(Field.TURN_ID, orEmpty(turnId));
            appendEvent(RowType.TURN_CONFIG, payload);
        } catch (Exception ignored) {
            // 见 appendEvent 的说明。
        }
    }

    public synchronized void appendEvent(String type, JSONObject payload) {
        try {
            appendLine(new JSONObject()
                    .put(Field.TYPE, type)
                    .put(Field.PAYLOAD, payload == null ? new JSONObject() : payload)
                    .put(Field.TIMESTAMP, System.currentTimeMillis()));
        } catch (Exception ignored) {
            // 事件行是辅助信息。写不进去时静默忽略 —— 「记录一个事件」这种动作
            // 不该把主流程（发消息、跑工具）带崩。
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
                    .put("summary", orEmpty(summary))
                    .put("removed_messages", removedMessages)
                    .put("retained_messages", retainedMessages)
                    .put("strategy", COMPACTION_STRATEGY)
                    .put("trigger", trigger == null ? "unknown" : trigger)
                    .put(Field.MODEL, orEmpty(model));
            if (beforeTokens >= 0) payload.put("before_tokens", beforeTokens);
            if (afterTokens >= 0) payload.put("after_tokens", afterTokens);
            appendEvent(RowType.COMPACTION, payload);
        } catch (Exception ignored) {
            // 见 appendEvent 的说明。
        }
    }

    /**
     * 记录面向提供方的压缩后上下文。
     *
     * <p>完整的人类对话仍然留在消息行里；这个快照只影响 {@link #loadMessages}。
     * 落盘前深拷贝一次：调用方之后还会继续改它传进来的那个数组。
     */
    public synchronized void appendContextSnapshot(JSONArray messages) {
        try {
            appendEvent(RowType.SNAPSHOT, new JSONObject()
                    .put(Field.MESSAGES, messages == null ? new JSONArray() : copy(messages)));
        } catch (Exception ignored) {
            // 见 appendEvent 的说明。
        }
    }

    /**
     * 更新会话的备注与标题覆盖。
     *
     * <p>写入前后<b>刻意不动文件的 mtime</b>：会话列表按活跃时间排序，
     * 而「改个备注」不是一次对话活动，让它跳到列表顶部会让人困惑。
     * 所以先记下 mtime，写完再恢复。
     */
    public static void updateSessionMetadata(File file, String note, String titleOverride) throws Exception {
        if (file == null || !file.isFile()) throw new IllegalArgumentException("会话不存在");
        File canonical = file.getCanonicalFile();
        SessionMetadata metadata = new SessionMetadata(note, titleOverride);
        JSONObject row = new JSONObject()
                .put(Field.TYPE, RowType.METADATA)
                .put(Field.PAYLOAD, new JSONObject()
                        .put("note", metadata.note)
                        .put("title_override", metadata.titleOverride))
                .put(Field.TIMESTAMP, System.currentTimeMillis());
        synchronized (fileLock(canonical)) {
            long modifiedAt = canonical.lastModified();
            appendLines(canonical, Collections.singletonList(row.toString()), true);
            canonical.setLastModified(modifiedAt);
            fsyncDirectory(canonical.getParentFile());
        }
    }

    // ------------------------------------------------------------ 读取

    /**
     * 按行读一个文件。
     *
     * <p>{@code strict} 的两种取值对应两种真实需求：
     * <ul>
     *   <li>宽容（界面、摘要）：解不开的行<b>跳过</b>。会话是追加写的，
     *       一次断电完全可能留下半行，为了半行就让人打不开整段历史，代价太大。</li>
     *   <li>严格（改历史之前）：任何一行解不开就抛出，并带上物理行号。重写整个文件时，
     *       那些没读懂的行「原样抄回去还是丢掉」两种选择都可能毁数据，所以直接拒绝这次操作。</li>
     * </ul>
     *
     * <p>行号（{@link JsonLine#index}）只对解出来的行递增，因为旧格式的引用按
     * 「第几条 JSON」计，而不是按物理行。
     */
    private static List<JsonLine> scan(File file, boolean strict) throws Exception {
        ArrayList<JsonLine> lines = new ArrayList<>();
        if (file == null || !file.isFile()) return lines;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String raw;
            int physicalLine = 0;
            int parsedLines = 0;
            while ((raw = reader.readLine()) != null) {
                physicalLine++;
                String trimmed = raw.trim();
                if (trimmed.isEmpty()) {
                    // 空行本身没有内容，但在严格模式下要留下来 —— 重写时得把它抄回去。
                    if (strict) lines.add(new JsonLine(raw, null, -1));
                    continue;
                }
                JSONObject json;
                try {
                    json = new JSONObject(trimmed);
                } catch (Exception broken) {
                    if (strict) {
                        throw new IllegalStateException("会话历史第 " + physicalLine + " 行损坏，已拒绝修改", broken);
                    }
                    continue;
                }
                lines.add(new JsonLine(raw, json, parsedLines++));
            }
        }
        return lines;
    }

    /** 宽容读取：返回能解出来的行。损坏行被跳过。 */
    public static JSONArray readRows(File file) throws Exception {
        JSONArray rows = new JSONArray();
        for (JsonLine line : scan(file, false)) {
            if (line.json != null) rows.put(line.json);
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
        JSONArray conversation = new JSONArray();
        for (JsonLine line : scan(file, false)) {
            JSONObject row = line.json;
            if (row == null) continue;

            String type = row.optString(Field.TYPE, "");
            if (RowType.SNAPSHOT.equals(type)) {
                JSONObject payload = row.optJSONObject(Field.PAYLOAD);
                JSONArray snapshot = payload == null ? null : payload.optJSONArray(Field.MESSAGES);
                if (snapshot != null) conversation = copy(snapshot);
                continue;
            }
            if (!RowType.MESSAGE.equals(type)) continue;

            JSONArray content = row.optJSONArray(Field.CONTENT);
            if (content == null) continue;
            conversation.put(new JSONObject()
                    .put(Field.ROLE, row.optString(Field.ROLE, Role.USER))
                    .put(Field.CONTENT, copy(content)));
        }
        return mergeAdjacentRoles(conversation);
    }

    /**
     * 把相邻的同角色消息合并成一条。
     *
     * <p>提供方（OpenAI / Anthropic）要求角色交替出现；而我们的历史里
     * 「人类发言」与「工具结果」都是 user 角色，不合并就会被拒。
     *
     * <p>合并方式是<b>直接往后拼接内容块</b>，不重排。这一点很重要：
     * {@code tool_use} 与 {@code tool_result} 的配对靠的是相对顺序，
     * 一旦重排（例如按块类型分组），提供方就会报「找不到对应的工具调用」。
     */
    private static JSONArray mergeAdjacentRoles(JSONArray conversation) throws Exception {
        JSONArray merged = new JSONArray();
        for (JSONObject message : JsonItems.of(conversation)) {
            JSONArray content = message.optJSONArray(Field.CONTENT);
            if (content == null) continue;
            String role = message.optString(Field.ROLE, Role.USER);

            JSONObject tail = merged.length() == 0 ? null : merged.optJSONObject(merged.length() - 1);
            if (tail != null && role.equals(tail.optString(Field.ROLE))) {
                appendBlocks(tail.optJSONArray(Field.CONTENT), content);
            } else {
                merged.put(new JSONObject()
                        .put(Field.ROLE, role)
                        .put(Field.CONTENT, copy(content)));
            }
        }
        return merged;
    }

    /** 把 {@code blocks} 原样接到 {@code target} 后面（见 mergeAdjacentRoles 的说明）。 */
    private static void appendBlocks(JSONArray target, JSONArray blocks) throws Exception {
        if (target == null) return;
        for (int i = 0; i < blocks.length(); i++) {
            target.put(copy(blocks.getJSONObject(i)));
        }
    }

    // ------------------------------------------------------------ 列表与摘要

    /** 全部会话（各项目目录），按活跃时间倒序。 */
    public static List<SessionSummary> listSessions() {
        List<SessionSummary> out = new ArrayList<>();
        File[] projectDirectories = projectsDirectory().listFiles(File::isDirectory);
        if (projectDirectories != null) {
            for (File directory : projectDirectories) collect(out, directory);
        }
        Collections.sort(out, (a, b) -> Long.compare(b.activityModifiedAt, a.activityModifiedAt));
        return out;
    }

    /** 只列某个项目的会话。用 {@link #sessionDirectory} 取目录，它会顺带建目录、刷新索引。 */
    public static List<SessionSummary> listSessions(String projectDirectory) {
        List<SessionSummary> out = new ArrayList<>();
        collect(out, sessionDirectory(projectDirectory));
        Collections.sort(out, (a, b) -> Long.compare(b.modifiedAt, a.modifiedAt));
        return out;
    }

    /** 把一个目录里的每个会话文件读成一行；单个文件读不出来就跳过它，其余照常列出。 */
    private static void collect(List<SessionSummary> out, File directory) {
        File[] files = directory.listFiles((dir, name) -> name.endsWith(SESSION_SUFFIX));
        if (files == null) return;
        for (File file : files) {
            try {
                out.add(summarize(file));
            } catch (Exception ignored) {
                // 见方法注释。
            }
        }
    }

    /**
     * 读一个文件算出摘要。一遍扫描就够，因为每一行都自带类型与时间戳：
     * 起始行给项目与创建时间，元数据行给备注与标题覆盖，消息行给条数与首条人类发言。
     *
     * <p>活跃时间的口径是「最后一条<b>非元数据</b>行的时间戳」，不是文件 mtime ——
     * 改备注会改 mtime，但那不是一次对话活动。
     */
    public static SessionSummary summarize(File file) throws Exception {
        long fallbackTime = file.lastModified();
        long createdAt = fallbackTime;
        long activityAt = fallbackTime;
        int messageCount = 0;
        String project = "";
        String firstHuman = "";
        String note = "";
        String titleOverride = "";

        for (JsonLine line : scan(file, false)) {
            JSONObject row = line.json;
            if (row == null) continue;
            String type = row.optString(Field.TYPE, "");
            long timestamp = row.optLong(Field.TIMESTAMP, 0L);
            if (timestamp > activityAt && !RowType.METADATA.equals(type)) activityAt = timestamp;

            if (RowType.SESSION_START.equals(type)) {
                project = row.optString(Field.PROJECT, project);
                createdAt = row.optLong(Field.CREATED_AT, createdAt);
            } else if (RowType.METADATA.equals(type)) {
                JSONObject payload = row.optJSONObject(Field.PAYLOAD);
                if (payload != null) {
                    note = sanitizeMetadata(payload.optString("note", ""), MAX_NOTE_CHARS);
                    titleOverride = singleLine(sanitizeMetadata(
                            payload.optString("title_override", ""), MAX_TITLE_OVERRIDE_CHARS));
                }
            } else if (RowType.MESSAGE.equals(type)) {
                messageCount++;
                if (firstHuman.isEmpty() && Role.USER.equals(row.optString(Field.ROLE))) {
                    firstHuman = firstHumanText(row.optJSONArray(Field.CONTENT));
                }
            }
        }

        String title = titleOverride.isEmpty() ? firstHuman : titleOverride;
        if (title.isEmpty()) title = fallbackTitle(project);
        return new SessionSummary(file, project, compactTitle(title), note, titleOverride,
                createdAt, activityAt, messageCount);
    }

    /** 没有可用发言时退回项目目录名；连项目都不知道就叫 Session。 */
    private static String fallbackTitle(String project) {
        String folder = project.isEmpty() ? "" : new File(project).getName();
        return isBlank(folder) ? "Session" : folder;
    }

    // ------------------------------------------------------------ 删除

    /**
     * 删除一次会话。
     *
     * <p>只允许删 {@code projects/<项目键>/} 的直接子 {@code .jsonl} 文件。
     * 这道检查防的是「路径来自界面」的场景 —— 一个被拼错的路径不该让这个方法
     * 变成任意文件删除器。
     */
    public static boolean deleteSession(File file) {
        if (file == null) return false;
        try {
            File projects = projectsDirectory().getCanonicalFile();
            File target = file.getCanonicalFile();
            File parent = target.getParentFile();

            boolean directChild = parent != null && parent.getParentFile() != null
                    && parent.getParentFile().equals(projects);
            if (!directChild || !under(projects, target)) return false;
            if (!target.getName().endsWith(SESSION_SUFFIX)) return false;

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
            String recorded = recordedWorkflowId(file);
            if (!recorded.trim().isEmpty()) return recorded;
        } catch (Exception ignored) {
            // 读不出来就退到下面的「按路径派生」。
        }
        return "session-" + shortHash(canonicalPathOf(file));
    }

    /** 历史里记过的 workflow id：起始行、{@code config} 事件、{@code plan_*} 事件都算。 */
    private static String recordedWorkflowId(File file) throws Exception {
        String workflow = "";
        for (JsonLine line : scan(file, false)) {
            JSONObject row = line.json;
            if (row == null) continue;
            String type = row.optString(Field.TYPE, "");
            if (RowType.SESSION_START.equals(type)) {
                workflow = row.optString(Field.WORKFLOW_ID, workflow);
                continue;
            }
            JSONObject payload = row.optJSONObject(Field.PAYLOAD);
            if (payload != null && ("config".equals(type) || type.startsWith("plan_"))) {
                workflow = payload.optString(Field.WORKFLOW_ID, workflow);
            }
        }
        return workflow;
    }

    /** 最后一次记录下来的 API 配置绑定；没有就返回 null。 */
    public static ProfileBinding loadProfileBinding(File file) {
        try {
            ProfileBinding binding = null;
            for (JsonLine line : scan(file, false)) {
                JSONObject row = line.json;
                if (row == null) continue;
                String type = row.optString(Field.TYPE, "");
                if (!RowType.PROFILE_BINDING.equals(type) && !RowType.TURN_CONFIG.equals(type)) continue;
                JSONObject payload = row.optJSONObject(Field.PAYLOAD);
                // 取最后一条有效绑定：中途换过配置时以最新那次为准。
                if (payload != null && !payload.optString(Field.PROFILE_ID, "").isEmpty()) {
                    binding = new ProfileBinding(payload);
                }
            }
            return binding;
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 计划流程状态：取最后一条 {@code plan_*} 事件。 */
    public static PlanWorkflowState loadPlanState(File file) {
        String workflow = loadWorkflowId(file);
        PlanWorkflowState state = PlanWorkflowState.idle();
        try {
            for (JsonLine line : scan(file, false)) {
                JSONObject row = line.json;
                if (row == null) continue;
                String type = row.optString(Field.TYPE, "");
                if (!type.startsWith("plan_")) continue;
                JSONObject payload = row.optJSONObject(Field.PAYLOAD);
                if (payload == null) continue;

                PlanWorkflowState.Status status;
                try {
                    status = PlanWorkflowState.Status.valueOf(payload.optString("status", ""));
                } catch (Exception unknownStatus) {
                    // 早期记录里可能没有 status 字段，按事件类型推断。
                    status = statusForEventType(type);
                }
                state = PlanWorkflowState.restore(status,
                        payload.optString(Field.WORKFLOW_ID, workflow),
                        payload.optLong("revision", state.revision),
                        payload.optString("previous_permission_mode", state.previousPermissionMode),
                        payload.optString("approved_permission_mode", state.approvedPermissionMode),
                        payload.optString("plan_file", state.planFile),
                        payload.optString("plan_text", state.planText),
                        payload.optString("feedback", state.feedback),
                        payload.optLong("updated_at", row.optLong(Field.TIMESTAMP, System.currentTimeMillis())));
            }
        } catch (Exception ignored) {
            // 读不出来就当这次会话还没进过计划模式。
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

    // ------------------------------------------------------------ 消息引用与编辑

    /** 内容块的稳定摘要，用于校验「要改的确实是这一条」。 */
    public static String messageContentHash(JSONArray content) {
        return shortHash(content == null ? "[]" : content.toString());
    }

    /**
     * 判断一行能不能作为可编辑的目标。
     *
     * <p>只有 user / assistant 两种角色的消息行算数，且必须含可编辑内容：
     * <ul>
     *   <li>user：{@code origin} 必须为空或 {@code human} —— 工具结果也是 user 角色，
     *       但它们不是人写的，不该出现在可编辑列表里；另外必须含真实人类内容；</li>
     *   <li>assistant：必须含 text 或 thinking 正文。</li>
     * </ul>
     */
    public static MessageReference messageReference(JSONObject row, int rowIndex) {
        if (row == null || !RowType.MESSAGE.equals(row.optString(Field.TYPE, ""))) return null;
        String role = row.optString(Field.ROLE, "");
        boolean human = Role.USER.equals(role);
        if (!human && !Role.ASSISTANT.equals(role)) return null;

        if (human) {
            String origin = row.optString(Field.ORIGIN, "");
            if (!origin.isEmpty() && !"human".equals(origin)) return null;
        }
        JSONArray content = row.optJSONArray(Field.CONTENT);
        if (!hasEditableContent(content, role)) return null;

        return new MessageReference(row.optString(Field.MESSAGE_ID, ""), rowIndex, messageContentHash(content));
    }

    public static void editHumanMessage(File file, MessageReference target, String newText) throws Exception {
        rewrite(file, target, requireText(newText, "消息不能为空"), false);
    }

    public static void deleteHumanMessage(File file, MessageReference target) throws Exception {
        rewrite(file, target, "", true);
    }

    /** 编辑用户或助手的正文，或助手的思考内容。 */
    public static void editMessage(File file, MessageReference target, String replacement) throws Exception {
        rewrite(file, target, requireText(replacement, "消息内容不能为空"), false);
    }

    public static void deleteMessage(File file, MessageReference target) throws Exception {
        rewrite(file, target, "", true);
    }

    /** 空内容写进去等于把这条消息删了，却在界面上显示成一条空消息 —— 所以直接拒绝。 */
    private static String requireText(String value, String message) {
        String trimmed = orEmpty(value).trim();
        if (trimmed.isEmpty()) throw new IllegalArgumentException(message);
        return trimmed;
    }

    /**
     * 改写或删除一条消息，整文件重写。
     *
     * <p>两个关键决定：
     * <ol>
     *   <li><b>未改动的行原样照抄</b>（{@link JsonLine#raw}），不重新序列化 ——
     *       重新序列化会改动键顺序与转义，让整个文件产生无意义的 diff。</li>
     *   <li><b>目标之后的所有 {@code context_*} 行一律丢弃</b>。见类注释：
     *       留着它们会让模型继续看到改之前的版本。丢弃等于让引擎下次重新压缩一次，
     *       代价可以接受。</li>
     * </ol>
     */
    private static void rewrite(File file, MessageReference target, String replacement, boolean delete)
            throws Exception {
        if (file == null || !file.isFile() || target == null) throw new IllegalArgumentException("无效的会话消息");
        File canonical = file.getCanonicalFile();
        synchronized (fileLock(canonical)) {
            List<JsonLine> lines = scan(canonical, true);
            int targetIndex = findTarget(lines, target);
            if (targetIndex < 0) throw new IllegalStateException("要修改的历史消息已变化，请重新打开会话");

            ArrayList<String> output = new ArrayList<>(lines.size());
            for (int i = 0; i < lines.size(); i++) {
                JsonLine line = lines.get(i);
                if (i == targetIndex) {
                    if (!delete) output.add(rewriteTextBlock(line.json, replacement).toString());
                    continue;
                }
                if (i > targetIndex && line.json != null
                        && isContextEvent(line.json.optString(Field.TYPE, ""))) {
                    continue;
                }
                output.add(line.raw);
            }
            replaceFile(canonical, output, canonical.lastModified());
        }
    }

    /**
     * 定位要改的那一行。
     *
     * <p>校验策略：先按 id 或旧行号筛出候选，再要求<b>内容摘要也一致</b>。
     * 摘要这一项是必需的 —— 光凭 id 发现不了「这条消息已被别处改过」，
     * 那样会用一份过期内容覆盖一份新的。
     *
     * <p>命中重复一律拒绝，而不是取第一个：出现重复说明这个引用本身已经不可信。
     */
    private static int findTarget(List<JsonLine> lines, MessageReference target) throws Exception {
        if (target.contentHash.trim().isEmpty()) throw new IllegalArgumentException("消息引用缺少内容校验");
        boolean byId = !target.messageId.trim().isEmpty();
        if (!byId && target.legacyRowIndex < 0) throw new IllegalArgumentException("消息引用缺少稳定位置");

        if (byId) {
            int occurrences = 0;
            for (JsonLine line : lines) {
                if (line.json == null) continue;
                if (!RowType.MESSAGE.equals(line.json.optString(Field.TYPE, ""))) continue;
                if (target.messageId.equals(line.json.optString(Field.MESSAGE_ID, ""))) occurrences++;
            }
            if (occurrences > 1) throw new IllegalStateException("消息标识重复，已拒绝修改");
        }

        int match = -1;
        for (int i = 0; i < lines.size(); i++) {
            JsonLine line = lines.get(i);
            if (line.json == null) continue;
            MessageReference candidate = messageReference(line.json, line.index);
            if (candidate == null) continue;
            boolean matches = byId
                    ? target.messageId.equals(candidate.messageId)
                            && target.contentHash.equals(candidate.contentHash)
                    : candidate.messageId.isEmpty()
                            && target.legacyRowIndex == candidate.legacyRowIndex
                            && target.contentHash.equals(candidate.contentHash);
            if (!matches) continue;
            if (match >= 0) throw new IllegalStateException("消息标识重复，已拒绝修改");
            match = i;
        }
        return match;
    }

    /**
     * 替换消息里的第一个可编辑文本块。
     *
     * <p>只改第一个：一条消息可能同时含正文与思考，两者都算「可编辑」，
     * 但用户编辑的是他看到的那个正文。改第一个可编辑块与界面语义一致。
     * 若一个可编辑块都没有（例如只有图片），就补一个新的文本块，
     * 而不是静默地什么都没改。
     */
    private static JSONObject rewriteTextBlock(JSONObject original, String replacement) throws Exception {
        JSONArray before = original.optJSONArray(Field.CONTENT);
        if (before == null) throw new IllegalStateException("消息内容无效");

        JSONArray after = new JSONArray();
        boolean replaced = false;
        for (JSONObject block : JsonItems.of(before)) {
            String type = block.optString(Field.TYPE, "");
            if (!isEditableBlock(block, type)) {
                after.put(copy(block));
                continue;
            }
            // 同一消息里的第二个及之后的文本/思考块直接丢弃：它们与用户看到的正文
            // 是同一段内容的重复表述。
            if (replaced) continue;
            JSONObject edited = copy(block);
            edited.put(Block.TEXT.equals(type) ? Field.TEXT : Field.THINKING, replacement);
            after.put(edited);
            replaced = true;
        }
        if (!replaced) after.put(new JSONObject().put(Field.TYPE, Block.TEXT).put(Field.TEXT, replacement));

        JSONObject rewritten = copy(original);
        rewritten.put(Field.CONTENT, after);
        return rewritten;
    }

    /** 一个内容块是否能被用户编辑。内部注入的块（上下文摘要、续跑指令）不算。 */
    private static boolean isEditableBlock(JSONObject block, String type) {
        if (Block.TEXT.equals(type)) return !isInternalMarkerText(block.optString(Field.TEXT, "").trim());
        if (Block.THINKING.equals(type) || Block.REASONING.equals(type)) {
            return !block.optString(Field.THINKING, "").trim().isEmpty();
        }
        return false;
    }

    /** 这段文本是不是我们自己注入的内部标记。 */
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

    /** 哪些派生事件会在历史被改动后失效 —— 它们必须一起丢弃。 */
    private static boolean isContextEvent(String type) {
        if (type == null) return false;
        return RowType.SNAPSHOT.equals(type)
                || RowType.COMPACTION.equals(type)
                || type.startsWith("context_");
    }

    // ------------------------------------------------------------ 可编辑判定

    /**
     * 一个内容块数组里有没有「真实内容」。
     *
     * <p>user 的图片算内容（只发图也是发言）；assistant 的思考算内容
     * （有些模型只产出思考）。其余情况要求文本非空。
     */
    private static boolean hasEditableContent(JSONArray content, String role) {
        if (content == null) return false;
        boolean human = Role.USER.equals(role);
        boolean assistant = Role.ASSISTANT.equals(role);

        for (JSONObject block : JsonItems.of(content)) {
            String type = block.optString(Field.TYPE, "");
            if (human && Block.IMAGE.equals(type)) return true;

            boolean textLike = Block.TEXT.equals(type)
                    || (assistant && (Block.THINKING.equals(type) || Block.REASONING.equals(type)));
            if (!textLike) continue;
            String body = block.optString(Block.TEXT.equals(type) ? Field.TEXT : Field.THINKING, "");
            if (!body.trim().isEmpty()) return true;
        }
        return false;
    }

    /**
     * 取第一条真正的人类发言，用作会话标题。
     *
     * <p>跳过内部注入的文本（上下文摘要、续跑指令）：拿它们当标题只会得到
     * 一串没有信息量的标记。
     */
    private static String firstHumanText(JSONArray content) {
        if (content == null) return "";
        for (JSONObject block : JsonItems.of(content)) {
            if (!Block.TEXT.equals(block.optString(Field.TYPE))) continue;
            String text = block.optString(Field.TEXT, "").trim();
            if (!text.isEmpty() && !isInternalMarkerText(text)) return text;
        }
        return "";
    }

    // ------------------------------------------------------------ 落盘

    /** 追加一行到当前会话。 */
    private void appendLine(JSONObject row) throws Exception {
        File target = sessionFile.getCanonicalFile();
        synchronized (fileLock(target)) {
            appendLines(target, Collections.singletonList(row.toString()), false);
        }
    }

    /**
     * 往文件末尾追加若干行。
     *
     * <p>{@code durable} 为真时 fsync。默认不 fsync：消息是高频写入，
     * 每次都同步会让打字明显卡顿；只有低频而重要的写入（例如改备注）才需要它。
     */
    private static void appendLines(File target, List<String> lines, boolean durable) throws Exception {
        File parent = target.getParentFile();
        if (parent != null) AtomicFiles.ensureDirectory(parent, "会话目录");
        try (FileOutputStream out = new FileOutputStream(target, true)) {
            out.write(encode(lines));
            out.flush();
            if (durable) out.getFD().sync();
        }
    }

    /** 把若干行拼成要写进文件的字节。追加与整文件重写共用，转义方式才不会分叉。 */
    private static byte[] encode(List<String> lines) {
        StringBuilder text = new StringBuilder();
        for (String line : lines) text.append(line).append('\n');
        return text.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 整文件替换。
     *
     * <p>临时文件、fsync、rename 都交给 {@link AtomicFiles}（与其它配置文件的写法一致）。
     * 这里额外补两件事：恢复原 mtime（改备注不是一次对话活动），以及 fsync 目录。
     * 最后那步容易被漏掉：rename 落在目录项上，不 fsync 目录的话，掉电后可能出现
     * 「文件内容在、目录项没更新」的中间状态。
     */
    private static void replaceFile(File target, List<String> lines, long originalModifiedAt) throws Exception {
        if (target.getParentFile() == null) throw new IllegalStateException("会话目录无效");
        AtomicFiles.publish(target, encode(lines));
        target.setLastModified(originalModifiedAt);
        fsyncDirectory(target.getParentFile());
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

    /** 写项目索引，供界面显示项目名。失败不影响会话功能，因此静默。 */
    private static void writeProjectIndex(File directory, String project) {
        try {
            File index = new File(directory, PROJECT_INDEX_FILE);
            JSONObject content = new JSONObject()
                    .put("version", 1)
                    .put(Field.PROJECT, project)
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
     * 先 putIfAbsent 再判断，避免两次检查之间被别的线程插进来。
     */
    private static Object fileLock(File file) throws Exception {
        String key = file.getCanonicalPath();
        Object created = new Object();
        Object existing = FILE_LOCKS.putIfAbsent(key, created);
        return existing == null ? created : existing;
    }

    // ------------------------------------------------------------ 小工具

    /** 一份 profile 绑定的 payload。字段名与 {@link ProfileBinding} 的读取端一一对应。 */
    private static JSONObject profilePayload(SessionConfig config) {
        JSONObject payload = new JSONObject();
        if (config == null) return payload;
        try {
            payload.put(Field.PROFILE_ID, orEmpty(config.profileId));
            payload.put(Field.PROFILE_REVISION, config.profileRevision);
            payload.put(Field.CREDENTIAL_REVISION, config.credentialRevision);
            payload.put(Field.PROTOCOL, orEmpty(config.protocol));
            payload.put(Field.BASE_URL, orEmpty(config.baseUrl));
            payload.put(Field.MODEL, orEmpty(config.model));
        } catch (Exception ignored) {
            // 组装失败时返回已放进去的字段；调用方按缺字段处理。
        }
        return payload;
    }

    /** 深拷贝。JSONObject/JSONArray 都没有拷贝构造，只能走一次序列化。 */
    private static JSONObject copy(JSONObject value) throws Exception {
        return new JSONObject(value.toString());
    }

    private static JSONArray copy(JSONArray value) throws Exception {
        return new JSONArray(value.toString());
    }

    /** 标题压成单行并截断。用省略号而不是硬切，让人知道后面还有内容。 */
    private static String compactTitle(String value) {
        String text = singleLine(orEmpty(value)).trim();
        if (text.length() <= MAX_TITLE_CHARS) return text;
        return text.substring(0, MAX_TITLE_CHARS - 1) + "…";
    }

    /** 把可能的换行（含 CRLF、单独 CR）统一成 LF。 */
    private static String singleLine(String value) {
        return orEmpty(value).replace('\n', ' ');
    }

    /**
     * 清理元数据文本。
     *
     * <p>保留换行与制表符，其余控制字符删掉 —— 它们会在列表渲染时变成乱码方块。
     * 按字符数而不是字节数截断：这里的上限是给人看的宽度。
     */
    private static String sanitizeMetadata(String value, int maxChars) {
        String input = orEmpty(value).replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder clean = new StringBuilder(Math.min(input.length(), maxChars));
        for (int i = 0; i < input.length() && clean.length() < maxChars; i++) {
            char c = input.charAt(i);
            if (c == '\n' || c == '\t' || !Character.isISOControl(c)) clean.append(c);
        }
        return clean.toString().trim();
    }

    /** child 是否位于 root 之下（不含 root 自身）。 */
    private static boolean under(File root, File child) {
        try {
            String prefix = root.getCanonicalPath() + File.separator;
            return child.getCanonicalPath().startsWith(prefix);
        } catch (Exception e) {
            return false;
        }
    }

    /** 拿不到规范路径（符号链接断了等）时退回绝对路径；连文件都没有就用一个固定词。 */
    private static String canonicalPathOf(File file) {
        try {
            return file.getCanonicalPath();
        } catch (Exception unresolvable) {
            return file == null ? "session" : file.getAbsolutePath();
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * 前 6 字节的 SHA-256，即 12 个十六进制字符。
     *
     * <p>用作路径键的一部分时，碰撞概率远低于实际会遇到的目录数。
     * 拿不到摘要算法时退回 {@code hashCode} —— 质量差一些，但比抛异常好。
     */
    private static String shortHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(12);
            for (int i = 0; i < 6; i++) hex.append(String.format(Locale.US, "%02x", digest[i] & 255));
            return hex.toString();
        } catch (Exception noDigest) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
