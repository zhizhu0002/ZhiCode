package com.termux.app.zhicode.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * 用 Android Keystore 保管 API 令牌。
 *
 * <h3>放在 Keystore 里，而不是自己存密钥</h3>
 * 密钥本体由系统持有，且不随应用数据备份流出（Keystore 的密钥不可导出）。
 * 我们只存密文与 IV。这样即使有人读到了应用私有目录，也拿不到明文令牌。
 *
 * <h3>两种存放形态，都必须保留</h3>
 * <ul>
 *   <li><b>按 profile 分槽</b>（{@link #setApiKey(String, int, String)}）：
 *       一个 profile 一个密钥。{@code credentialRevision} 参与槽名，
 *       因此「换掉某 profile 的密钥」= 换一个新槽，旧密文自然被弃用，
 *       不需要原地覆盖。</li>
 *   <li><b>单一旧槽</b>（{@link #setApiKey(String)}）：早期版本只有一个密钥。
 *       保留它是因为 {@code ApiSettingsStore} 的迁移逻辑要读它，
 *       把老用户的密钥搬进 profile 分槽。删掉它等于让老用户重新填一次密钥。</li>
 * </ul>
 *
 * <h3>关于命名的搬迁</h3>
 * Keystore 别名与 SharedPreferences 文件名里原先带有旧品牌标识。
 * 它们是持久化标识，改名会让已存的密钥失效，所以这里做了搬迁：
 * 新的标识为当前值，同时保留旧标识作为<b>只读</b>来源；
 * 一旦在旧位置找到数据，就用旧别名解出来、再用新别名写回去。
 * 因此改名对用户是无感的，而不是「升级后密钥没了、请重新填写」。
 *
 * <h3>读失败必须能与「没设置」区分</h3>
 * 原先两者都返回空串。这在真实场景里会误导：设备凭据变更后 Keystore 里的密钥会被作废，
 * 此时密文还在、却解不开；界面显示「未配置 API Key」，用户重新填一遍好了，
 * 于是没人知道真正发生了什么。{@link #lastError()} 保留了这条线索。
 */
public final class AndroidSecretStore {

    private static final String KEYSTORE = "AndroidKeyStore";

    /** 当前 Keystore 别名与 prefs 文件名。 */
    private static final String KEY_ALIAS = "zhicode_api_key_v1";
    private static final String PREFS = "zhicode_secrets";

    /** 搬迁来源：只读，不写入。 */
    private static final String LEGACY_KEY_ALIAS = "iq_code_android_api_key_v1";
    private static final String LEGACY_PREFS = "iq_code_android_secrets";

    /** 单一旧槽的键名。 */
    private static final String LEGACY_VALUE_KEY = "api_key_ciphertext";
    private static final String LEGACY_IV_KEY = "api_key_iv";

    private static final String CIPHER = "AES/GCM/NoPadding";
    /** GCM 认证标签长度（位）。128 是常规取值。 */
    private static final int GCM_TAG_BITS = 128;
    private static final String PROFILE_PREFIX = "profile_";

    /**
     * 最近一次读取失败的原因；空串表示上次读取正常。
     *
     * <p>刻意做成静态字段：{@code AndroidSecretStore} 是轻量值对象，
     * 每次用都会新建一个，实例字段挂不住这条信息。
     */
    private static volatile String lastError = "";

    private final Context context;

    public AndroidSecretStore(Context context) {
        this.context = context.getApplicationContext();
    }

    // ------------------------------------------------------------ 单一旧槽

    public synchronized void setApiKey(String value) throws Exception {
        write(PREFS, LEGACY_VALUE_KEY, LEGACY_IV_KEY, value);
    }

    public synchronized String getApiKey() {
        return readWithMigration(LEGACY_VALUE_KEY, LEGACY_IV_KEY);
    }

    // ------------------------------------------------------------ profile 分槽

    public synchronized void setApiKey(String profileId, int credentialRevision, String value) throws Exception {
        String slot = slotOf(profileId, credentialRevision);
        write(PREFS, slotValueKey(slot), slotIvKey(slot), value);
    }

    public synchronized String getApiKey(String profileId, int credentialRevision) {
        String slot = slotOf(profileId, credentialRevision);
        return readWithMigration(slotValueKey(slot), slotIvKey(slot));
    }

    public synchronized void removeApiKey(String profileId, int credentialRevision) {
        String slot = slotOf(profileId, credentialRevision);
        String valueKey = slotValueKey(slot);
        String ivKey = slotIvKey(slot);
        preferences(PREFS).edit().remove(valueKey).remove(ivKey).apply();
        // 旧位置也清掉：否则搬迁逻辑会在下一次读取时把刚删掉的密钥搬回来。
        preferences(LEGACY_PREFS).edit().remove(valueKey).remove(ivKey).apply();
    }

    // ---------------------------------------------------------------- 写入

    /**
     * 写入一个槽。空值语义是「删除」而不是「存一个空串」——
     * 后者会让 {@code getApiKey} 返回空串却仍被判为「已配置」。
     */
    private void write(String prefsName, String valueKey, String ivKey, String value) throws Exception {
        SharedPreferences prefs = preferences(prefsName);
        if (value == null || value.isEmpty()) {
            prefs.edit().remove(valueKey).remove(ivKey).apply();
            clearError();
            return;
        }
        Encrypted encrypted = encrypt(value);
        prefs.edit()
                .putString(valueKey, Base64.encodeToString(encrypted.payload, Base64.NO_WRAP))
                .putString(ivKey, Base64.encodeToString(encrypted.iv, Base64.NO_WRAP))
                .apply();
        clearError();
    }

    /**
     * 一次加密的产物。
     *
     * <p>返回成对的值而不是把 IV 暂存到字段里：暂存会让 {@code write} 的正确性
     * 依赖调用顺序，而本类实例并不保证只被一个线程使用。
     */
    private static final class Encrypted {
        final byte[] payload;
        final byte[] iv;

        Encrypted(byte[] payload, byte[] iv) {
            this.payload = payload;
            this.iv = iv;
        }
    }

    private static Encrypted encrypt(String plaintext) throws Exception {
        Cipher cipher = Cipher.getInstance(CIPHER);
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(KEY_ALIAS));
        return new Encrypted(cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)), cipher.getIV());
    }

    // ---------------------------------------------------------------- 读取

    /** 先读当前位置；没有则尝试旧位置，命中就搬到新位置。 */
    private String readWithMigration(String valueKey, String ivKey) {
        String found = read(preferences(PREFS), KEY_ALIAS, valueKey, ivKey);
        if (found != null) return found;

        SharedPreferences legacy = preferences(LEGACY_PREFS);
        if (!legacy.contains(valueKey)) return "";
        String migrated = read(legacy, LEGACY_KEY_ALIAS, valueKey, ivKey);
        if (migrated == null || migrated.isEmpty()) return "";
        // 搬到新位置。搬失败也不算错：本次已经读到值了，下次再试。
        try {
            write(PREFS, valueKey, ivKey, migrated);
        } catch (Exception moveFailed) {
            recordError("密钥搬迁失败", moveFailed);
        }
        return migrated;
    }

    /**
     * 从指定 prefs 与指定 Keystore 别名解出一个槽。
     *
     * @return 明文；该槽不存在时返回 {@code null}（与「存在但为空串」区分开）
     */
    private String read(SharedPreferences prefs, String keyAlias, String valueKey, String ivKey) {
        String encoded = prefs.getString(valueKey, null);
        String encodedIv = prefs.getString(ivKey, null);
        if (encoded == null || encodedIv == null) return null;
        try {
            KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
            keyStore.load(null);
            SecretKey key = (SecretKey) keyStore.getKey(keyAlias, null);
            if (key == null) {
                recordError("Keystore 中没有别名 " + keyAlias, null);
                return "";
            }
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(GCM_TAG_BITS, Base64.decode(encodedIv, Base64.NO_WRAP)));
            byte[] plaintext = cipher.doFinal(Base64.decode(encoded, Base64.NO_WRAP));
            clearError();
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception failure) {
            // 常见原因：设备凭据变更导致 Keystore 密钥作废、密文被截断。
            // 返回空串让界面能继续工作，但把原因留下。
            recordError("API Key 解密失败", failure);
            return "";
        }
    }

    // ---------------------------------------------------------------- 槽名

    /**
     * 槽标识。同一 profile 与同一 revision 必须永远算出同一个槽，
     * 否则升级后老密钥就找不到了——所以算法与编码方式都不能动。
     */
    private static String slotOf(String profileId, int credentialRevision) {
        String raw = (profileId == null ? "" : profileId) + "@" + Math.max(1, credentialRevision);
        return Base64.encodeToString(raw.getBytes(StandardCharsets.UTF_8),
                Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    private static String slotValueKey(String slot) {
        return PROFILE_PREFIX + slot + "_ciphertext";
    }

    private static String slotIvKey(String slot) {
        return PROFILE_PREFIX + slot + "_iv";
    }

    private SharedPreferences preferences(String name) {
        return context.getSharedPreferences(name, Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------ Keystore 键

    /** 取现有密钥；没有就在 Keystore 里生成一把，永不导出。 */
    private static SecretKey getOrCreateKey(String alias) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
        keyStore.load(null);
        SecretKey existing = (SecretKey) keyStore.getKey(alias, null);
        if (existing != null) return existing;

        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return generator.generateKey();
    }

    // ------------------------------------------------------------ 诊断

    /** 最近一次读取失败的可读原因；空串表示上次读取未出错。 */
    public static String lastError() {
        return lastError;
    }

    private static void recordError(String what, Throwable cause) {
        lastError = cause == null ? what : what + ": " + cause.getClass().getSimpleName()
                + (cause.getMessage() == null ? "" : " " + cause.getMessage());
    }

    private static void clearError() {
        lastError = "";
    }
}
