package com.termux.app.zhicode.api;

/**
 * 本工程支持的传输协议，以及它们的线上名字。
 *
 * <h3>为什么是枚举</h3>
 * 在这之前，「协议名 → 传输实现」和「协议名 → 目录端点」是两张各自维护的
 * if 链，各自内联字符串。多一处内联就多一处写错名字的机会，而写错的表现是
 * 「报文格式对不上、服务端报解析错误」，很难联想到是协议名字打错了。
 * 收到一个枚举里之后，「认识的协议」只有一处定义。
 *
 * <h3>{@link #wireName()} 是持久化字符串</h3>
 * 它被写进用户设置（{@code api_profiles_v1} 里每个配置的 {@code protocol} 字段）
 * 和会话 JSONL，所以这些字面量是**存盘格式的一部分**，不能随手改：
 * 改了之后旧配置会被判成「未知协议」而拒绝加载。
 *
 * <h3>别名</h3>
 * 两个只在路由层存在的同义写法：{@code codex-responses} 与
 * {@code openai-compatible}。它们是历史上出现过的写法，报文格式与主名完全一致，
 * 所以归到同一个枚举值，而不是各建一项 —— 各建一项会让 {@code switch}
 * 出现永远走不到的分支，也会让人以为它们的报文不同。
 */
enum ApiProtocol {

    ANTHROPIC("anthropic"),
    OPENAI_CHAT("openai-chat"),
    OPENAI_RESPONSES("openai-responses");

    /** 与 {@link #OPENAI_RESPONSES} 同义的历史写法。 */
    static final String ALIAS_CODEX_RESPONSES = "codex-responses";
    /** 与 {@link #OPENAI_CHAT} 同义的历史写法（自建/第三方网关常用）。 */
    static final String ALIAS_OPENAI_COMPATIBLE = "openai-compatible";

    private final String wireName;

    ApiProtocol(String wireName) {
        this.wireName = wireName;
    }

    /** 写进设置、发给服务端判定用的字符串。 */
    String wireName() {
        return wireName;
    }

    /**
     * 把设置里读到的字符串解析成协议。
     *
     * <p>比较是**精确**的：不 trim、不忽略大小写。用户配置里出现
     * {@code " anthropic"} 这种值时，与其猜他的意思，不如按未知协议报错 ——
     * 静默接受一个大小写不同的名字，会让人以为「两种写法都行」。
     *
     * @return 对应协议；{@code null} 或未收录的写法返回 {@code null}
     */
    static ApiProtocol fromWire(String wire) {
        if (wire == null) return null;
        for (ApiProtocol protocol : values()) {
            if (protocol.wireName.equals(wire)) return protocol;
        }
        if (ALIAS_CODEX_RESPONSES.equals(wire)) return OPENAI_RESPONSES;
        if (ALIAS_OPENAI_COMPATIBLE.equals(wire)) return OPENAI_CHAT;
        return null;
    }
}
