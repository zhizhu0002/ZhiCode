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
 * <h3>为什么是 Keystore，而不是自己存一把密钥</h3>
 * 密钥本体由系统持有，且**不可导出** —— 它不会随应用数据一起被备份出去，
 * 也不会被读到应用私有目录的人拿走（那里只有密文与 IV）。
 * GCM 还顺带提供了完整性校验：密文被改过就解不出来，而不是解出一段垃圾。
 *
 * <h3>两种槽形态</h3>
 * <ul>
 *   <li><b>按 profile 分槽</b>（三参的 {@link #setApiKey(String, int, String)}）：
 *       一个 profile 一个槽。{@code credentialRevision} 参与槽名，所以
 *       「换掉某个 profile 的密钥」等于换一个新槽 —— 旧密文自然被弃用，
 *       不需要原地覆盖，也就不存在「覆盖到一半」的中间态。</li>
 *   <li><b>单一槽</b>（{@link #setApiKey(String)}）：多 profile 之前的形态，
 *       那时只有一份密钥、键名固定。它仍被 {@code ApiSettingsStore} 的旧配置
 *       回退读取用到，所以保留。</li>
 * </ul>
 *
 * <h3>槽名算法不能动</h3>
 * {@link #slotOf} 的输入输出必须是**纯函数**：同 profile、同 revision 永远算出
 * 同一个槽。改算法（哪怕只是换一种编码）等于让所有已存的密钥失效。
 *
 * <h3>读失败必须能与「没设置」区分开</h3>
 * 两者都返回空串，但在真实场景里含义完全不同：设备凭据变更后 Keystore 里的密钥
 * 会被作废，此时密文还在、却解不开。界面只看到「未配置 API Key」，
 * 用户重新填一遍就好了，于是没人知道真正发生了什么。{@link #lastError()} 留下这条线索。
 */
public final class AndroidSecretStore {

    private static final String KEYSTORE = "AndroidKeyStore";

    /** Keystore 别名与 prefs 文件名。它们是持久化标识，改名会让已存密钥解不出来。 */
    private static final String KEY_ALIAS = "zhicode_api_key_v1";
    private static final String PREFS = "zhicode_secrets";

    /** 一个槽的两个键：密文与 IV。 */
    private static final String VALUE_KEY = "api_key_ciphertext";
    private static final String IV_KEY = "api_key_iv";

    /** 按 profile 分槽时的键名前缀。 */
    private static final String PROFILE_PREFIX = "profile_";
    private static final String SLOT_CIPHERTEXT_SUFFIX = "_ciphertext";
    private static final String SLOT_IV_SUFFIX = "_iv";

    private static final String CIPHER = "AES/GCM/NoPadding";
    /** GCM 认证标签长度（位）。128 是常规取值。 */
    private static final int GCM_TAG_BITS = 128;

    /**
     * 最近一次读取失败的原因；空串表示上次读取正常。
     *
     * <p>刻意做成静态字段：本类是轻量对象，每次用都会新建一个实例，
     * 实例字段挂不住这条跨实例的信息。
     */
    private static volatile String lastError = "";

    private final Context context;

    public AndroidSecretStore(Context context) {
        this.context = context.getApplicationContext();
    }

    // ------------------------------------------------------------ 单一槽

    public synchronized void setApiKey(String value) throws Exception {
        write(VALUE_KEY, IV_KEY, value);
    }

    public synchronized String getApiKey() {
        return readSlot(VALUE_KEY, IV_KEY);
    }

    // ------------------------------------------------------------ profile 分槽

    public synchronized void setApiKey(String profileId, int credentialRevision, String value)
            throws Exception {
        String slot = slotOf(profileId, credentialRevision);
        write(slotValueKey(slot), slotIvKey(slot), value);
    }

    public synchronized String getApiKey(String profileId, int credentialRevision) {
        String slot = slotOf(profileId, credentialRevision);
        return readSlot(slotValueKey(slot), slotIvKey(slot));
    }

    public synchronized void removeApiKey(String profileId, int credentialRevision) {
        String slot = slotOf(profileId, credentialRevision);
        prefs().edit().remove(slotValueKey(slot)).remove(slotIvKey(slot)).apply();
    }

    // ---------------------------------------------------------------- 写入

    /**
     * 写一个槽。
     *
     * <p>空值的语义是**删除**而不是「存一个空串」：后者会让 {@code getApiKey}
     * 返回空串却仍被判为「已配置」，而界面据此显示一个假的「已设置」。
     */
    private void write(String valueKey, String ivKey, String value) throws Exception {
        SharedPreferences preferences = prefs();
        if (value == null || value.isEmpty()) {
            preferences.edit().remove(valueKey).remove(ivKey).apply();
            clearError();
            return;
        }
        Encrypted encrypted = encrypt(value);
        preferences.edit()
            .putString(valueKey, Base64.encodeToString(encrypted.payload, Base64.NO_WRAP))
            .putString(ivKey, Base64.encodeToString(encrypted.iv, Base64.NO_WRAP))
            .apply();
        clearError();
    }

    /**
     * 一次加密的产物。
     *
     * <p>返回成对的值，而不是把 IV 暂存到字段里：暂存会让 {@link #write} 的正确性
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
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        return new Encrypted(cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)), cipher.getIV());
    }

    // ---------------------------------------------------------------- 读取

    /**
     * 读一个槽。
     *
     * @return 明文；该槽不存在时返回空串
     */
    private String readSlot(String valueKey, String ivKey) {
        SharedPreferences preferences = prefs();
        String payload = preferences.getString(valueKey, null);
        String iv = preferences.getString(ivKey, null);
        // 两个键缺一不可：只有一个是写坏的状态，当成「不存在」处理。
        if (payload == null || iv == null) return "";
        try {
            KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
            keyStore.load(null);
            SecretKey key = (SecretKey) keyStore.getKey(KEY_ALIAS, null);
            if (key == null) {
                recordError("Keystore 中没有别名 " + KEY_ALIAS, null);
                return "";
            }
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, key,
                new GCMParameterSpec(GCM_TAG_BITS, Base64.decode(iv, Base64.NO_WRAP)));
            byte[] plaintext = cipher.doFinal(Base64.decode(payload, Base64.NO_WRAP));
            clearError();
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception failure) {
            // 常见原因：设备凭据变更导致 Keystore 密钥作废、或密文被截断。
            // 返回空串让界面能继续工作，但把原因留下（见类注释）。
            recordError("API Key 解密失败", failure);
            return "";
        }
    }

    // ---------------------------------------------------------------- 槽名

    /**
     * 槽标识。见类注释：这必须是纯函数，算法与编码都不能动。
     *
     * <p>{@code Math.max(1, revision)} —— revision 0 与 1 归到同一个槽。
     * 早期版本写的可能是 0，而「未设置」与「第 1 版」在语义上就是同一件事。
     *
     * <p>用 URL-safe 的 Base64 且去掉填充：槽名会成为 prefs 的键，
     * 而 {@code +}、{@code /}、{@code =} 在键名里合法但容易在人工排查时看错。
     */
    private static String slotOf(String profileId, int credentialRevision) {
        String raw = (profileId == null ? "" : profileId) + "@" + Math.max(1, credentialRevision);
        return Base64.encodeToString(raw.getBytes(StandardCharsets.UTF_8),
            Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    private static String slotValueKey(String slot) {
        return PROFILE_PREFIX + slot + SLOT_CIPHERTEXT_SUFFIX;
    }

    private static String slotIvKey(String slot) {
        return PROFILE_PREFIX + slot + SLOT_IV_SUFFIX;
    }

    private SharedPreferences prefs() {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------ Keystore 键

    /** 取现有密钥；没有就生成一把。生成的密钥永不导出。 */
    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
        keyStore.load(null);
        SecretKey existing = (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        if (existing != null) return existing;

        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            // 本类用的就是 GCM + 无填充；这里声明成同一组参数，
            // 否则加解密时会因参数不符而失败（keystore 会把参数当作约束校验）。
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
        lastError = cause == null
            ? what
            : what + ": " + cause.getClass().getSimpleName()
                + (cause.getMessage() == null ? "" : " " + cause.getMessage());
    }

    private static void clearError() {
        lastError = "";
    }
}
