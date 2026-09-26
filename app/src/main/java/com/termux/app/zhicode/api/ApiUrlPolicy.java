package com.termux.app.zhicode.api;

import com.termux.app.zhicode.model.SessionConfig;

import java.util.Locale;

/**
 * 校验用户填写的 API 地址。
 *
 * <p>这个类<b>只做校验，不选主机、不改写主机</b>。请求永远发到用户自己配置的地址上；
 * 任何「补一个默认域名」「把 http 改成 https」的行为都会让流量去到一个用户没指定的地方，
 * 所以这里刻意什么都不做，只把不合法的配置挡下来并说清原因。
 *
 * <p>不校验主机是否可信：用户有权把 Base URL 指向内网或自建服务。
 * 唯一的要求是协议头明确（{@code http://} 或 {@code https://}），
 * 否则 {@link java.net.URL} 会把它当成相对路径、或者被系统补上一个意料之外的默认协议。
 */
final class ApiUrlPolicy {

    private static final String HTTPS_PREFIX = "https://";
    private static final String HTTP_PREFIX = "http://";

    private ApiUrlPolicy() {}

    /**
     * 取出可用作请求基址的 URL。
     *
     * @throws IllegalStateException 未配置，或缺少协议头
     */
    static String requireBaseUrl(SessionConfig config) {
        String base = config == null || config.baseUrl == null ? "" : config.baseUrl.trim();
        if (base.isEmpty()) {
            throw new IllegalStateException("API 地址未配置，请在设置中填写 Base URL");
        }
        // 用 Locale.US 做小写化：某些语言环境下 "HTTPS" 的默认小写不是 "https"，
        // 那会让一条合法配置被判为非法。
        String lower = base.toLowerCase(Locale.US);
        if (!lower.startsWith(HTTPS_PREFIX) && !lower.startsWith(HTTP_PREFIX)) {
            throw new IllegalStateException("Base URL 必须以 https:// 或 http:// 开头");
        }
        return base;
    }
}
