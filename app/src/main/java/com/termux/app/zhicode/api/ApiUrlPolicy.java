package com.termux.app.zhicode.api;

import com.termux.app.zhicode.model.SessionConfig;

import java.util.Locale;

/**
 * 校验用户填写的 API 地址。
 *
 * <p>这个类<b>只做校验，不选主机、不改写主机</b>。请求永远发到用户自己配置的地址上；
 * 任何「补一个默认域名」「把 http 改成 https」「把大写改成小写」的行为都会让流量
 * 去到一个用户没指定的地方，所以这里刻意什么都不做，只把不合法的配置挡下来并说清原因。
 *
 * <p>不校验主机是否可信：用户有权把 Base URL 指向内网或自建服务。
 * 唯一的要求是协议头明确（{@code http://} 或 {@code https://}），
 * 否则 {@link java.net.URL} 会把它当成相对路径、或者被系统补上一个意料之外的默认协议。
 *
 * <p>返回值是**原字符串**（只去掉首尾空白），不是规范化之后的结果：
 * 上层要拼的端点在 {@link ApiEndpointResolver}，那里也不改写用户填的部分。
 */
final class ApiUrlPolicy {

    private static final String HTTPS_SCHEME = "https://";
    private static final String HTTP_SCHEME = "http://";

    /** 白名单而不是黑名单：将来出现 {@code file://} 之类的写法也会被挡下。 */
    private static final String[] ALLOWED_SCHEMES = {HTTPS_SCHEME, HTTP_SCHEME};

    private static final String NOT_CONFIGURED = "API 地址未配置，请在设置中填写 Base URL";
    private static final String SCHEME_REQUIRED = "Base URL 必须以 https:// 或 http:// 开头";

    private ApiUrlPolicy() {}

    /**
     * 取出可用作请求基址的 URL。
     *
     * @throws IllegalStateException 未配置，或缺少协议头
     */
    static String requireBaseUrl(SessionConfig config) {
        String base = trimmedUrl(config);
        if (base.isEmpty()) throw new IllegalStateException(NOT_CONFIGURED);
        if (!hasAllowedScheme(base)) throw new IllegalStateException(SCHEME_REQUIRED);
        return base;
    }

    private static String trimmedUrl(SessionConfig config) {
        return config == null || config.baseUrl == null ? "" : config.baseUrl.trim();
    }

    /**
     * 是否以白名单里的协议头开头。
     *
     * <p>用 {@link Locale#US} 做小写化：某些语言环境下 {@code "HTTPS"} 的默认小写
     * 不是 {@code "https"}（土耳其语的 {@code I} 会变成 {@code ı}），
     * 那会让一条合法配置被判为非法。
     */
    private static boolean hasAllowedScheme(String url) {
        String lower = url.toLowerCase(Locale.US);
        for (String scheme : ALLOWED_SCHEMES) {
            if (lower.startsWith(scheme)) return true;
        }
        return false;
    }
}
