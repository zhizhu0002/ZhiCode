package com.termux.app.zhicode.model;

import org.json.JSONObject;

import java.util.UUID;

/**
 * 一条 API 配置的**元数据**。
 *
 * <h3>密钥不在这里</h3>
 * 这个对象的 {@link #toJson()} 会写进设置的 {@code api_profiles_v1}，
 * 而设置是一个明文 JSON。所以密钥放在 {@code AndroidSecretStore}（Keystore 加密），
 * 这里只留一个 {@link #credentialRevision} 作为「密钥换过第几版」的指针 ——
 * 两者靠这个版本号对应起来，改密钥不需要重写整条配置。
 *
 * <h3>JSON 键是存盘格式</h3>
 * {@link #toJson()} 的键名与 {@link #fromJson} 读的键名必须严格对应，
 * 且不能随手改名：改了之后旧配置的字段会读成默认值，
 * 表现为「配置里的地址/模型莫名其妙没了」。
 */
public final class ApiProfile {

    /** 记录格式版本。将来要迁移时用它区分老数据。 */
    public static final int SCHEMA_VERSION = 1;

    private static final String DEFAULT_NAME = "API 配置";
    private static final String DEFAULT_PROTOCOL = "openai-responses";
    private static final int FIRST_REVISION = 1;

    private static final String KEY_SCHEMA_VERSION = "schema_version";
    private static final String KEY_ID = "id";
    private static final String KEY_NAME = "name";
    private static final String KEY_PROTOCOL = "protocol";
    private static final String KEY_BASE_URL = "base_url";
    private static final String KEY_DEFAULT_MODEL = "default_model";
    private static final String KEY_REVISION = "revision";
    private static final String KEY_CREDENTIAL_REVISION = "credential_revision";

    public String id;
    /** 显示给用户的名字。空名字在界面上是一条无法分辨的配置，所以有默认值。 */
    public String name;
    public String protocol;
    public String baseUrl;
    /** 新建会话时预填的模型名；用户可以为单次会话改掉它。 */
    public String defaultModel;
    /** 配置本身的版本号，每次修改 +1。 */
    public int revision;
    /** 密钥的版本号，指向 AndroidSecretStore 里的那个槽位。 */
    public int credentialRevision;

    public ApiProfile() {
        id = UUID.randomUUID().toString();
        name = DEFAULT_NAME;
        protocol = DEFAULT_PROTOCOL;
        baseUrl = "";
        defaultModel = "";
        revision = FIRST_REVISION;
        credentialRevision = FIRST_REVISION;
    }

    public ApiProfile copy() {
        ApiProfile copy = new ApiProfile();
        copy.id = id;
        copy.name = name;
        copy.protocol = protocol;
        copy.baseUrl = baseUrl;
        copy.defaultModel = defaultModel;
        copy.revision = revision;
        copy.credentialRevision = credentialRevision;
        return copy;
    }

    public JSONObject toJson() {
        try {
            return new JSONObject()
                .put(KEY_SCHEMA_VERSION, SCHEMA_VERSION)
                .put(KEY_ID, id)
                .put(KEY_NAME, name)
                .put(KEY_PROTOCOL, protocol)
                .put(KEY_BASE_URL, baseUrl)
                .put(KEY_DEFAULT_MODEL, defaultModel)
                .put(KEY_REVISION, revision)
                .put(KEY_CREDENTIAL_REVISION, credentialRevision);
        } catch (Exception impossible) {
            // 这里的键与值都是本类自己给的，不存在编码失败的可能；
            // 真出现了说明 JSONObject 的实现变了，报出来比吞掉好。
            throw new IllegalStateException("Unable to encode API profile", impossible);
        }
    }

    /**
     * 从设置里读回来。
     *
     * <p>缺字段一律落回默认值（而不是抛错）：老版本写下的配置里可能没有后来才加的键，
     * 读不回来等于把用户的配置丢了。缺失、空白、以及不认识的字段都按同一套规则处理。
     */
    public static ApiProfile fromJson(JSONObject json) {
        ApiProfile profile = new ApiProfile();
        profile.id = trimmed(json.optString(KEY_ID, profile.id));
        profile.name = trimmed(json.optString(KEY_NAME, profile.name));
        profile.protocol = trimmed(json.optString(KEY_PROTOCOL, profile.protocol));
        profile.baseUrl = trimmed(json.optString(KEY_BASE_URL, profile.baseUrl));
        profile.defaultModel = trimmed(json.optString(KEY_DEFAULT_MODEL, profile.defaultModel));
        profile.revision = Math.max(FIRST_REVISION, json.optInt(KEY_REVISION, profile.revision));
        profile.credentialRevision = Math.max(FIRST_REVISION,
            json.optInt(KEY_CREDENTIAL_REVISION, profile.credentialRevision));
        // 空白的 id/名字/协议会被上面归一化成空串，这里补回可用的值。
        if (profile.id.isEmpty()) profile.id = UUID.randomUUID().toString();
        if (profile.name.isEmpty()) profile.name = DEFAULT_NAME;
        if (profile.protocol.isEmpty()) profile.protocol = DEFAULT_PROTOCOL;
        return profile;
    }

    /** 去掉首尾空白；null 归到空串。 */
    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }
}
