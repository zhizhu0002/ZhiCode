package com.termux.app.zhicode.storage;

import android.content.Context;
import android.content.SharedPreferences;

import com.termux.app.zhicode.model.ApiProfile;
import com.termux.app.zhicode.model.SessionConfig;
import com.termux.app.zhicode.security.AndroidSecretStore;
import com.termux.shared.termux.TermuxConstants;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 全局设置与 API 配置的持久化。
 *
 * <h3>秘密不在这里</h3>
 * 本类只存<b>不含密钥</b>的元数据（地址、模型名、协议）。API Key 一律走
 * {@link AndroidSecretStore}，落到 Android Keystore 里。这样即使设置被导出或备份，
 * 也不会连带泄露令牌。{@code ApiProfile} 里没有密钥字段，是刻意的。
 *
 * <h3>「官方配置」是受保护的一条</h3>
 * 它总是存在、总是排在第一位、地址与模型名不可改、也不可删除。
 * 这个约束在三处写入路径上都要成立（保存单个 profile、保存全局设置、规范化列表），
 * 所以实现成<b>一个</b> {@link #enforceOfficialProfile}，避免三份副本将来各自漂移。
 *
 * <h3>读取路径不应该写盘</h3>
 * 规范化的结果只在<b>确实变化</b>时才落盘。原先每次 {@code getProfiles()} 都会写一次，
 * 而调用方常在循环里取它——既浪费，也让「读」有机会覆盖别的线程刚写下的内容。
 *
 * <h3>损坏的 profile 列表不得被静默覆盖</h3>
 * 原先解析失败只返回空列表，而 {@link #load()} 紧接着会走迁移逻辑并<b>写回</b>一份新列表，
 * 于是用户自建的 profile 全部消失。现在坏内容会先留档，且原因可查
 * （{@link #lastError()}）。
 */
public final class ApiSettingsStore {

    /** 当前 prefs 文件名。由品牌短标识派生，保证与其它模块拼出的名字一致。 */
    private static final String PREFS = TermuxConstants.BRAND_SLUG + "_settings";
    /** 搬迁来源：只读，首次启动时整体拷一次，之后不再触碰。 */
    private static final String LEGACY_PREFS = "iq_code_android_settings";

    // ------------------------------------------------------------ 存储键
    // 这些键名是持久化契约，改动等于丢掉用户已有设置，因此集中在一处并说明。

    private static final String KEY_PROFILES = "api_profiles_v1";
    private static final String KEY_ACTIVE_PROFILE = "active_api_profile_id";
    private static final String KEY_MIGRATED_PROFILE = "legacy_api_profile_id";
    private static final String KEY_ROLE_CARDS = "role_cards_v1";
    private static final String KEY_ACTIVE_ROLE_CARD = "active_role_card_id";
    private static final String KEY_LAST_SESSION = "last_session_path";

    /** 键名与 {@link SessionConfig} 的字段一一对应；改一个就要一起改 {@code loadGlobal/saveGlobal}。 */
    private static final String KEY_PROTOCOL = "protocol";
    private static final String KEY_BASE_URL = "base_url";
    private static final String KEY_ENDPOINT_CONFIG_VERSION = "api_endpoint_config_version";
    private static final String KEY_MODEL = "model";
    private static final String KEY_VISION_ENABLED = "vision_enabled";
    private static final String KEY_EFFORT = "effort";
    private static final String KEY_CUSTOM_PROMPT = "custom_system_prompt";
    private static final String KEY_ROLE_CARD = "role_card";
    private static final String KEY_REASONING_SUMMARY = "reasoning_summary";
    private static final String KEY_PRESERVE_REASONING = "preserve_reasoning_state";
    private static final String KEY_TOOL_MODE = "tool_mode";
    private static final String KEY_PERMISSION_MODE = "permission_mode";
    private static final String KEY_SANDBOX_FULL_ACCESS = "sandbox_agent_full_access";
    private static final String KEY_ROOT_EXECUTION = "root_execution_enabled";
    private static final String KEY_FORCED_KEEP_ALIVE = "forced_keep_alive_enabled";
    private static final String KEY_PROJECT_DIRECTORY = "project_directory";
    private static final String KEY_MAX_TOKENS = "max_tokens";
    private static final String KEY_CONTEXT_WINDOW = "context_window_tokens";
    private static final String KEY_AUTO_COMPACT = "auto_compact";
    private static final String KEY_AUTO_COMPACT_RATIO = "auto_compact_ratio";
    private static final String KEY_COMPACTION_VERSION = "context_compaction_logic_version";
    private static final String KEY_WEB_SEARCH_ENABLED = "web_search_enabled";
    private static final String KEY_WEB_SEARCH_PROVIDER = "web_search_provider";
    private static final String KEY_WEB_SEARCH_MAX_RESULTS = "web_search_max_results";
    private static final String KEY_WEB_FETCH_MAX_CHARS = "web_fetch_max_chars";
    private static final String KEY_WEB_TIMEOUT = "web_timeout_ms";

    // ------------------------------------------------------------ 版本与迁移

    /**
     * 上下文压缩逻辑的版本。
     *
     * <p>第 1 版的默认压缩比例是 0.78，第 2 版改成 1.0（即只在真的满了才压缩）。
     * 升级时必须把「用户没动过、仍是 0.78」的那批值改写过来，
     * 否则老用户会继续用过时的保守比例。用户手工设过的值不动。
     */
    private static final int COMPACTION_LOGIC_VERSION = 2;

    /** 端点配置版本。用于把早期内置的一个第三方地址清掉。 */
    private static final int API_ENDPOINT_CONFIG_VERSION = 1;

    /** 早期内置的第三方端点。命中就清空，让用户自己填。 */
    private static final String LEGACY_BUNDLED_HOST = "ctoken.top";

    /** 第 1 版压缩比例，仅用于识别「用户没动过」。 */
    private static final double LEGACY_COMPACT_RATIO = 0.78d;

    // ------------------------------------------------------------ 官方配置

    public static final String OFFICIAL_PROFILE_ID = "zhicode-official";
    public static final String OFFICIAL_BASE_URL = "https://api.ginka.cloud/";
    public static final String OFFICIAL_SIGNUP_URL = "https://api.ginka.cloud/sign-up?aff=caHN";
    public static final String OFFICIAL_DEFAULT_MODEL = "gpt-5.6-sol";

    private static final String OFFICIAL_DISPLAY_NAME = "蜘蛛 官方 API";
    private static final String OFFICIAL_PROTOCOL = "openai-responses";

    private final SharedPreferences prefs;
    private final AndroidSecretStore secrets;

    public ApiSettingsStore(Context context) {
        Context app = context.getApplicationContext();
        prefs = openMigrated(app);
        secrets = new AndroidSecretStore(app);
    }

    // -------------------------------------------------------- 一次性搬迁

    /**
     * 打开当前 prefs；若它是空的而旧文件有内容，则整体搬一次。
     *
     * <p>不删旧文件：万一需要回退版本，旧设置还在。搬迁只在新文件为空时发生，
     * 所以不会把用户后来的修改用旧值覆盖回去。
     */
    private static SharedPreferences openMigrated(Context app) {
        SharedPreferences current = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!current.getAll().isEmpty()) return current;

        SharedPreferences legacy = app.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE);
        Map<String, ?> entries = legacy.getAll();
        if (entries.isEmpty()) return current;

        SharedPreferences.Editor editor = current.edit();
        for (Map.Entry<String, ?> entry : entries.entrySet()) {
            copyValue(editor, entry.getKey(), entry.getValue());
        }
        editor.apply();
        return current;
    }

    /** SharedPreferences 的值是若干具体类型，没有通用的 put，只能逐个匹配。 */
    private static void copyValue(SharedPreferences.Editor editor, String key, Object value) {
        if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
        else if (value instanceof Integer) editor.putInt(key, (Integer) value);
        else if (value instanceof Long) editor.putLong(key, (Long) value);
        else if (value instanceof Float) editor.putFloat(key, (Float) value);
        else if (value instanceof String) editor.putString(key, (String) value);
        else if (value instanceof java.util.Set) {
            @SuppressWarnings("unchecked")
            java.util.Set<String> strings = (java.util.Set<String>) value;
            editor.putStringSet(key, strings);
        }
        // 其余类型（不存在于本应用的设置里）直接跳过而不是抛错：搬迁失败不该让应用起不来。
    }

    // ------------------------------------------------------------ 对外读取

    public synchronized SessionConfig load() {
        SessionConfig config = loadGlobal();
        List<ApiProfile> profiles = normalizedProfiles();
        String activeId = prefs.getString(KEY_ACTIVE_PROFILE, "");
        ApiProfile active = findProfile(profiles, activeId);
        if (active == null) {
            active = profiles.get(0);
            prefs.edit().putString(KEY_ACTIVE_PROFILE, active.id).apply();
        }
        applyProfile(config, active, apiKeyFor(active));
        return config;
    }

    public synchronized List<ApiProfile> getProfiles() {
        List<ApiProfile> copies = new ArrayList<>();
        for (ApiProfile profile : normalizedProfiles()) copies.add(profile.copy());
        return copies;
    }

    public synchronized ApiProfile getActiveProfile() {
        List<ApiProfile> profiles = getProfiles();
        ApiProfile active = findProfile(profiles, prefs.getString(KEY_ACTIVE_PROFILE, ""));
        return active == null ? profiles.get(0) : active;
    }

    public synchronized ApiProfile getProfile(String profileId) {
        ApiProfile profile = findProfile(getProfiles(), profileId);
        return profile == null ? null : profile.copy();
    }

    public synchronized String getActiveProfileId() {
        return getActiveProfile().id;
    }

    public synchronized void selectProfile(String profileId) {
        if (findProfile(getProfiles(), profileId) == null) {
            throw new IllegalArgumentException("未知的 API 配置: " + profileId);
        }
        prefs.edit().putString(KEY_ACTIVE_PROFILE, profileId).apply();
    }

    /** 取某 profile 解析后的完整会话配置；profile 不存在返回 null。 */
    public synchronized SessionConfig resolveProfile(String profileId, SessionConfig base) {
        ApiProfile profile = findProfile(getProfiles(), profileId);
        if (profile == null) return null;
        SessionConfig resolved = base == null ? loadGlobal() : base.copy();
        applyProfile(resolved, profile, apiKeyFor(profile));
        return resolved;
    }

    // ------------------------------------------------------------ 对外写入

    /**
     * 保存一个 profile。
     *
     * <p>{@code replaceKey} 为真时才动密钥。为假表示「只改元数据」，
     * 此时密钥槽与 {@code credentialRevision} 必须原样保留，否则用户每改一次模型名
     * 就要重新填一次密钥。
     */
    public synchronized ApiProfile saveProfile(ApiProfile draft, String apiKey, boolean replaceKey) throws Exception {
        if (draft == null) throw new IllegalArgumentException("需要 API 配置");
        List<ApiProfile> profiles = getProfiles();
        ApiProfile existing = findProfile(profiles, draft.id);
        ApiProfile saved = draft.copy();
        if (isBlank(saved.id)) saved.id = new ApiProfile().id;
        saved.name = firstNonBlank(saved.name, "API 配置");
        saved.protocol = firstNonBlank(saved.protocol, OFFICIAL_PROTOCOL);
        saved.baseUrl = trimToEmpty(saved.baseUrl);
        saved.defaultModel = firstNonBlank(saved.defaultModel, "gpt-5.6-terra");

        if (existing == null) {
            saved.revision = 1;
            saved.credentialRevision = 1;
            profiles.add(saved);
        } else {
            saved.revision = existing.revision;
            saved.credentialRevision = existing.credentialRevision;
            // 端点三元组变了才算一次新的配置修订（用于让上层知道要重建连接）。
            if (!sameEndpoint(existing, saved)) saved.revision++;
            replaceAt(profiles, saved);
        }
        enforceOfficialProfile(saved);

        if (replaceKey) {
            if (existing != null) saved.credentialRevision = existing.credentialRevision + 1;
            secrets.setApiKey(saved.id, saved.credentialRevision, trimToEmpty(apiKey));
        }
        String activeId = prefs.getString(KEY_ACTIVE_PROFILE, saved.id);
        persistProfiles(profiles, activeId.isEmpty() ? saved.id : activeId);
        return saved.copy();
    }

    public synchronized void deleteProfile(String profileId) {
        if (OFFICIAL_PROFILE_ID.equals(profileId)) throw new IllegalStateException("官方 API 配置不能删除");
        List<ApiProfile> profiles = getProfiles();
        if (profiles.size() <= 1) throw new IllegalStateException("至少需要保留一个 API 配置");
        ApiProfile profile = findProfile(profiles, profileId);
        if (profile == null) return;
        profiles.remove(profile);
        secrets.removeApiKey(profile.id, profile.credentialRevision);
        String active = prefs.getString(KEY_ACTIVE_PROFILE, "");
        if (profile.id.equals(active)) active = profiles.get(0).id;
        persistProfiles(profiles, active);
    }

    /**
     * 保存全局设置，并把相关改动落到当前生效的 profile 上。
     *
     * <p>密钥只有<b>真的变了</b>才写入新的 credentialRevision。
     * 每次保存都递增的话，用户改一下网页搜索开关就会让密钥槽搬家，
     * 而旧槽永远留着没人清理。
     */
    public synchronized void save(SessionConfig config) throws Exception {
        config.customSystemPrompt = sanitizePrompt(config.customSystemPrompt);
        List<ApiProfile> profiles = getProfiles();
        ApiProfile profile = findProfile(profiles, config.profileId);
        if (profile == null) profile = findProfile(profiles, prefs.getString(KEY_ACTIVE_PROFILE, ""));
        if (profile == null) profile = profiles.get(0);

        ApiProfile updated = profile.copy();
        updated.protocol = firstNonBlank(config.protocol, updated.protocol);
        updated.baseUrl = trimToEmpty(config.baseUrl);
        updated.defaultModel = firstNonBlank(config.model, updated.defaultModel);
        enforceOfficialProfile(updated);
        if (!sameEndpoint(profile, updated)) updated.revision++;

        String previousKey = apiKeyFor(profile);
        String nextKey = trimToEmpty(config.apiKey);
        if (!nextKey.equals(previousKey)) {
            updated.credentialRevision = profile.credentialRevision + 1;
            secrets.setApiKey(updated.id, updated.credentialRevision, nextKey);
        }
        replaceAt(profiles, updated);
        persistProfiles(profiles, updated.id);
        applyProfile(config, updated, apiKeyFor(updated));
        saveGlobal(config);
    }

    // ------------------------------------------------------- 会话文件记忆

    public synchronized File getLastSessionFile() {
        String path = prefs.getString(KEY_LAST_SESSION, "");
        if (isBlank(path)) return null;
        File file = new File(path.trim());
        return file.isFile() ? file : null;
    }

    public synchronized void setLastSessionFile(File file) {
        prefs.edit().putString(KEY_LAST_SESSION, file == null ? "" : file.getAbsolutePath()).apply();
    }

    // ------------------------------------------------------------ 角色卡

    public synchronized JSONArray getRoleCards() {
        try {
            return new JSONArray(prefs.getString(KEY_ROLE_CARDS, "[]"));
        } catch (Exception unreadable) {
            return new JSONArray();
        }
    }

    public synchronized void saveRoleCards(JSONArray cards, String activeId) {
        prefs.edit()
                .putString(KEY_ROLE_CARDS, cards == null ? "[]" : cards.toString())
                .putString(KEY_ACTIVE_ROLE_CARD, activeId == null ? "" : activeId)
                .apply();
    }

    public synchronized String getActiveRoleCardId() {
        return prefs.getString(KEY_ACTIVE_ROLE_CARD, "");
    }

    /**
     * 只写「强制后台保活」这一个开关，供 {@code KeepAliveService} 在自我停止时回调。
     *
     * <p>存在的理由是一个真实的缺陷：{@code KeepAliveService} 曾经自己拼出
     * prefs 文件名与键名直接写盘，而它写的是<b>旧版</b> prefs 文件
     * （搬迁用的那个只读来源）。结果是「用户从通知里停掉保活 → 开关并没有被关掉 →
     * 下次启动界面仍显示开启」。键属于本类，写入就该由本类负责，
     * 否则键名一旦改动就会有第二次同样的漂移。
     *
     * <p>只改这一个键、不经过 {@link #save(SessionConfig)}：服务停止时只关心
     * 「开关归位」，重新读写整份设置既多余，也可能把界面尚未保存的草稿覆盖掉。
     */
    public static void setForcedKeepAliveEnabled(Context context, boolean enabled) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        if (app == null) app = context;
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_FORCED_KEEP_ALIVE, enabled)
                .apply();
    }

    // ------------------------------------------------------------ 全局字段

    private SessionConfig loadGlobal() {
        SessionConfig config = new SessionConfig();
        config.protocol = prefs.getString(KEY_PROTOCOL, config.protocol);

        String baseUrl = trimToEmpty(prefs.getString(KEY_BASE_URL, config.baseUrl));
        int endpointVersion = prefs.getInt(KEY_ENDPOINT_CONFIG_VERSION, 0);
        if (endpointVersion < API_ENDPOINT_CONFIG_VERSION && isLegacyBundledEndpoint(baseUrl)) {
            // 旧版本内置过一个第三方地址；升级时清掉，让用户自己填。
            baseUrl = "";
        }
        config.baseUrl = baseUrl;

        config.model = prefs.getString(KEY_MODEL, config.model);
        config.visionEnabled = prefs.getBoolean(KEY_VISION_ENABLED, config.visionEnabled);
        config.effort = prefs.getString(KEY_EFFORT, config.effort);
        config.customSystemPrompt = sanitizePrompt(prefs.getString(KEY_CUSTOM_PROMPT, ""));
        config.roleCard = sanitizePrompt(prefs.getString(KEY_ROLE_CARD, ""));
        config.reasoningSummary = prefs.getString(KEY_REASONING_SUMMARY, config.reasoningSummary);
        config.preserveReasoningState = prefs.getBoolean(KEY_PRESERVE_REASONING, config.preserveReasoningState);
        config.toolMode = prefs.getString(KEY_TOOL_MODE, config.toolMode);
        config.permissionMode = prefs.getString(KEY_PERMISSION_MODE, config.permissionMode);
        config.sandboxAgentFullAccess = prefs.getBoolean(KEY_SANDBOX_FULL_ACCESS, config.sandboxAgentFullAccess);
        config.rootExecutionEnabled = prefs.getBoolean(KEY_ROOT_EXECUTION, config.rootExecutionEnabled);
        config.forcedKeepAliveEnabled = prefs.getBoolean(KEY_FORCED_KEEP_ALIVE, config.forcedKeepAliveEnabled);
        config.projectDirectory = prefs.getString(KEY_PROJECT_DIRECTORY, TermuxConstants.TERMUX_HOME_DIR_PATH);
        config.maxTokens = prefs.getInt(KEY_MAX_TOKENS, config.maxTokens);
        config.contextWindowTokens = prefs.getInt(KEY_CONTEXT_WINDOW, config.contextWindowTokens);
        config.autoCompact = prefs.getBoolean(KEY_AUTO_COMPACT, config.autoCompact);

        // double 在 SharedPreferences 里只能按位存 long；原样往返，避免精度问题。
        config.autoCompactRatio = Double.longBitsToDouble(
                prefs.getLong(KEY_AUTO_COMPACT_RATIO, Double.doubleToRawLongBits(config.autoCompactRatio)));

        int compactionVersion = prefs.getInt(KEY_COMPACTION_VERSION, 0);
        if (compactionVersion < COMPACTION_LOGIC_VERSION
                && Math.abs(config.autoCompactRatio - LEGACY_COMPACT_RATIO) < 0.000001d) {
            // 仍是旧版默认值 → 说明用户没动过，跟着新版一起改。
            config.autoCompactRatio = 1.0d;
        }
        if (compactionVersion < COMPACTION_LOGIC_VERSION) {
            // 版本号与比例一次性写回，避免每次启动都重算一遍。
            prefs.edit()
                    .putInt(KEY_COMPACTION_VERSION, COMPACTION_LOGIC_VERSION)
                    .putLong(KEY_AUTO_COMPACT_RATIO, Double.doubleToRawLongBits(config.autoCompactRatio))
                    .apply();
        }

        config.webSearchEnabled = prefs.getBoolean(KEY_WEB_SEARCH_ENABLED, config.webSearchEnabled);
        config.webSearchProvider = prefs.getString(KEY_WEB_SEARCH_PROVIDER, config.webSearchProvider);
        config.webSearchMaxResults = prefs.getInt(KEY_WEB_SEARCH_MAX_RESULTS, config.webSearchMaxResults);
        config.webFetchMaxChars = prefs.getInt(KEY_WEB_FETCH_MAX_CHARS, config.webFetchMaxChars);
        config.webTimeoutMs = prefs.getInt(KEY_WEB_TIMEOUT, config.webTimeoutMs);
        return config;
    }

    private void saveGlobal(SessionConfig config) {
        prefs.edit()
                .putString(KEY_PROTOCOL, config.protocol)
                .putString(KEY_BASE_URL, trimToEmpty(config.baseUrl))
                .putInt(KEY_ENDPOINT_CONFIG_VERSION, API_ENDPOINT_CONFIG_VERSION)
                .putString(KEY_MODEL, config.model)
                .putBoolean(KEY_VISION_ENABLED, config.visionEnabled)
                .putString(KEY_EFFORT, config.effort)
                .putString(KEY_CUSTOM_PROMPT, sanitizePrompt(config.customSystemPrompt))
                .putString(KEY_ROLE_CARD, sanitizePrompt(config.roleCard))
                .putString(KEY_REASONING_SUMMARY, config.reasoningSummary)
                .putBoolean(KEY_PRESERVE_REASONING, config.preserveReasoningState)
                .putString(KEY_TOOL_MODE, config.toolMode)
                .putString(KEY_PERMISSION_MODE, config.permissionMode)
                .putBoolean(KEY_SANDBOX_FULL_ACCESS, config.sandboxAgentFullAccess)
                .putBoolean(KEY_ROOT_EXECUTION, config.rootExecutionEnabled)
                .putBoolean(KEY_FORCED_KEEP_ALIVE, config.forcedKeepAliveEnabled)
                .putString(KEY_PROJECT_DIRECTORY, config.projectDirectory)
                .putInt(KEY_MAX_TOKENS, config.maxTokens)
                .putInt(KEY_CONTEXT_WINDOW, config.contextWindowTokens)
                .putBoolean(KEY_AUTO_COMPACT, config.autoCompact)
                .putLong(KEY_AUTO_COMPACT_RATIO, Double.doubleToRawLongBits(config.autoCompactRatio))
                .putInt(KEY_COMPACTION_VERSION, COMPACTION_LOGIC_VERSION)
                .putBoolean(KEY_WEB_SEARCH_ENABLED, config.webSearchEnabled)
                .putString(KEY_WEB_SEARCH_PROVIDER, config.webSearchProvider)
                .putInt(KEY_WEB_SEARCH_MAX_RESULTS, config.webSearchMaxResults)
                .putInt(KEY_WEB_FETCH_MAX_CHARS, config.webFetchMaxChars)
                .putInt(KEY_WEB_TIMEOUT, config.webTimeoutMs)
                .apply();
    }

    // ------------------------------------------------------- profile 列表

    /**
     * 规范化后的列表：官方配置一定存在且排在首位，其余保持原顺序。
     *
     * <p>规范化结果只在<b>与已存的字符串不同</b>时才写回。
     * 这条判断是必需的：本方法由 {@link #getProfiles()} 调用，而调用方常在循环里取它；
     * 每次都写盘不仅浪费，还会让「读」有机会覆盖别的线程刚写下的内容。
     */
    private List<ApiProfile> normalizedProfiles() {
        List<ApiProfile> stored = readProfiles();
        if (stored.isEmpty()) {
            // 完全没有记录时才走迁移：这时没有任何东西会丢。
            return migrateLegacyProfile(loadGlobal());
        }

        List<ApiProfile> normalized = new ArrayList<>(stored.size() + 1);
        ApiProfile official = findProfile(stored, OFFICIAL_PROFILE_ID);
        official = official == null ? officialProfile() : official.copy();
        enforceOfficialProfile(official);
        normalized.add(official);
        for (ApiProfile profile : stored) {
            if (!OFFICIAL_PROFILE_ID.equals(profile.id)) normalized.add(profile);
        }

        String active = prefs.getString(KEY_ACTIVE_PROFILE, OFFICIAL_PROFILE_ID);
        if (findProfile(normalized, active) == null) active = OFFICIAL_PROFILE_ID;
        if (!profilesJson(normalized).equals(prefs.getString(KEY_PROFILES, ""))
                || !active.equals(prefs.getString(KEY_ACTIVE_PROFILE, ""))) {
            persistProfiles(normalized, active);
        }
        return normalized;
    }

    /**
     * 读 profile 列表。
     *
     * <p>关键区分：<b>没有记录</b>与<b>记录损坏</b>是两件事。
     * 前者可以安全迁移；后者若被当成前者，调用方接着写回新列表就会抹掉用户的数据。
     * 因此损坏时把原文留档到 {@code api_profiles_v1.corrupt} 并记下原因，
     * 而不是假装没有记录。
     */
    private List<ApiProfile> readProfiles() {
        ArrayList<ApiProfile> profiles = new ArrayList<>();
        String raw = prefs.getString(KEY_PROFILES, "");
        if (isBlank(raw)) {
            recordError("");
            return profiles;
        }
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject json = array.optJSONObject(i);
                if (json == null) continue;
                ApiProfile profile = ApiProfile.fromJson(json);
                // 去重：同一个 id 只保留第一个，避免界面上出现两份同名配置。
                if (findProfile(profiles, profile.id) == null) profiles.add(profile);
            }
            recordError("");
            return profiles;
        } catch (Exception broken) {
            String keptKey = KEY_PROFILES + ".corrupt";
            if (!prefs.contains(keptKey)) prefs.edit().putString(keptKey, raw).apply();
            recordError("API 配置解析失败，原文已保存在 " + keptKey + "；原因: " + broken);
            return profiles;
        }
    }

    private void persistProfiles(List<ApiProfile> profiles, String activeId) {
        prefs.edit()
                .putString(KEY_PROFILES, profilesJson(profiles))
                .putString(KEY_ACTIVE_PROFILE, activeId)
                .apply();
    }

    private static String profilesJson(List<ApiProfile> profiles) {
        JSONArray array = new JSONArray();
        for (ApiProfile profile : profiles) array.put(profile.toJson());
        return array.toString();
    }

    /**
     * 从「全局设置」迁移出第一份 profile。
     *
     * <p>只在没有任何 profile 记录时才会被调用。官方配置总是建立，
     * 另外：如果旧版本里填过地址或密钥，就再补一份「原 API 配置」，
     * 免得用户升级后以为配置丢了。
     */
    private List<ApiProfile> migrateLegacyProfile(SessionConfig legacy) {
        ArrayList<ApiProfile> profiles = new ArrayList<>();
        profiles.add(officialProfile());

        String legacyUrl = trimToEmpty(legacy.baseUrl);
        String legacyKey = trimToEmpty(secrets.getApiKey());
        if (!legacyUrl.isEmpty() || !legacyKey.isEmpty()) {
            ApiProfile profile = new ApiProfile();
            profile.name = "原 API 配置";
            profile.protocol = firstNonBlank(legacy.protocol, profile.protocol);
            profile.baseUrl = legacyUrl;
            profile.defaultModel = firstNonBlank(legacy.model, profile.defaultModel);
            if (!legacyKey.isEmpty()) {
                try {
                    secrets.setApiKey(profile.id, profile.credentialRevision, legacyKey);
                } catch (Exception keyMoveFailed) {
                    // 密钥没搬过去也没关系：apiKeyFor() 对这份 profile 会回退读旧槽。
                    recordError("旧 API Key 搬迁失败: " + keyMoveFailed);
                }
            }
            profiles.add(profile);
            prefs.edit().putString(KEY_MIGRATED_PROFILE, profile.id).apply();
        }
        persistProfiles(profiles, OFFICIAL_PROFILE_ID);
        return profiles;
    }

    // ------------------------------------------------------------ 官方配置

    private ApiProfile officialProfile() {
        ApiProfile profile = new ApiProfile();
        profile.id = OFFICIAL_PROFILE_ID;
        profile.protocol = OFFICIAL_PROTOCOL;
        enforceOfficialProfile(profile);
        return profile;
    }

    /**
     * 把官方配置的不可变字段钉回标准值。
     *
     * <p>三处写入路径都会调它，所以写成唯一一份。就地修改传入对象，
     * 调用方负责是否需要先 copy。
     */
    private static void enforceOfficialProfile(ApiProfile profile) {
        if (!OFFICIAL_PROFILE_ID.equals(profile.id)) return;
        profile.name = OFFICIAL_DISPLAY_NAME;
        profile.baseUrl = OFFICIAL_BASE_URL;
        profile.defaultModel = OFFICIAL_DEFAULT_MODEL;
    }

    // ------------------------------------------------------------ 小工具

    private void applyProfile(SessionConfig config, ApiProfile profile, String apiKey) {
        config.profileId = profile.id;
        config.profileRevision = profile.revision;
        config.credentialRevision = profile.credentialRevision;
        config.protocol = profile.protocol;
        config.baseUrl = profile.baseUrl;
        config.model = profile.defaultModel;
        config.apiKey = apiKey;
    }

    /** 取某 profile 的密钥；该槽为空时，若它是从旧配置迁移来的，回退读旧槽。 */
    private String apiKeyFor(ApiProfile profile) {
        String key = secrets.getApiKey(profile.id, profile.credentialRevision);
        if (!key.isEmpty()) return key;
        return profile.id.equals(prefs.getString(KEY_MIGRATED_PROFILE, "")) ? secrets.getApiKey() : "";
    }

    private static void replaceAt(List<ApiProfile> profiles, ApiProfile replacement) {
        for (int i = 0; i < profiles.size(); i++) {
            if (replacement.id.equals(profiles.get(i).id)) {
                profiles.set(i, replacement);
                return;
            }
        }
        profiles.add(replacement);
    }

    private static ApiProfile findProfile(List<ApiProfile> profiles, String id) {
        if (id == null) return null;
        for (ApiProfile profile : profiles) {
            if (id.equals(profile.id)) return profile;
        }
        return null;
    }

    /** 端点是否等价。不等价才算一次新的配置修订。 */
    private static boolean sameEndpoint(ApiProfile left, ApiProfile right) {
        return trimToEmpty(left.protocol).equals(trimToEmpty(right.protocol))
                && trimToEmpty(left.baseUrl).equals(trimToEmpty(right.baseUrl))
                && trimToEmpty(left.defaultModel).equals(trimToEmpty(right.defaultModel));
    }

    /**
     * 规范化用户自定义提示词。
     *
     * <p>统一换行符，并拒绝 NUL 与控制字符：这些字符会一路传到 HTTP 请求体里，
     * 在那里表现为难以定位的协议错误。早一点在这里报出来，错误信息才对得上原因。
     */
    private static String sanitizePrompt(String value) {
        String input = value == null ? "" : value.replace("\r\n", "\n").replace('\r', '\n');
        if (input.indexOf('\0') >= 0) throw new IllegalArgumentException("自定义提示词不能包含 NUL 字符");
        StringBuilder clean = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); ) {
            int codePoint = input.codePointAt(i);
            if (Character.isISOControl(codePoint) && codePoint != '\n' && codePoint != '\t') {
                throw new IllegalArgumentException("自定义提示词包含不支持的控制字符");
            }
            clean.appendCodePoint(codePoint);
            i += Character.charCount(codePoint);
        }
        return clean.toString().trim();
    }

    private static boolean isLegacyBundledEndpoint(String value) {
        String lower = trimToEmpty(value).toLowerCase(Locale.US);
        return lower.equals("http://" + LEGACY_BUNDLED_HOST)
                || lower.startsWith("http://" + LEGACY_BUNDLED_HOST + "/")
                || lower.equals("https://" + LEGACY_BUNDLED_HOST)
                || lower.startsWith("https://" + LEGACY_BUNDLED_HOST + "/");
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String firstNonBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    // ------------------------------------------------------------ 诊断

    private volatile String lastError = "";

    /**
     * 最近一次加载失败的原因；空串表示上次加载没有出错。
     *
     * <p>没有这条信息，「配置读不出来」与「还没有配置」在界面上长得一模一样。
     */
    public String lastError() {
        return lastError;
    }

    private void recordError(String reason) {
        lastError = reason == null ? "" : reason;
    }
}
