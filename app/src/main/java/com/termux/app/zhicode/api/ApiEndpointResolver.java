package com.termux.app.zhicode.api;

import com.termux.app.zhicode.model.SessionConfig;

import java.util.Locale;

/**
 * 在用户配置的主机上拼出各协议的端点。
 *
 * <p>只在用户给的 Base URL 上拼接，不引入任何第三方主机。
 *
 * <h3>{@code /v1} 的处理</h3>
 * 用户填 Base URL 的习惯不统一，两种写法都常见：{@code https://host} 与
 * {@code https://host/v1}。这里按「已经带了就不重复加」处理，
 * 否则会拼出 {@code /v1/v1/models} 这种 404 地址 ——
 * 而那种失败表现为「模型列表拉不到」，很难联想到是地址拼重了。
 *
 * <p>判断用不区分大小写的比较，同时也要跳过末尾斜杠：
 * 用户可能填 {@code https://host/V1/}。
 */
public final class ApiEndpointResolver {

    /** 协议名。与 {@link ModelProviders} 使用同一组取值。 */
    public static final String PROTOCOL_ANTHROPIC = "anthropic";
    public static final String PROTOCOL_OPENAI_CHAT = "openai-chat";
    public static final String PROTOCOL_OPENAI_RESPONSES = "openai-responses";

    private static final String VERSION_SEGMENT = "v1";
    private static final String MODELS_PATH = "models";

    private ApiEndpointResolver() {}

    /**
     * 模型目录端点。
     *
     * <p>三种协议都走同一个 {@code /v1/models}：这既是 OpenAI 的约定，
     * 也是 Anthropic 与各兼容网关广为接受的路径。
     *
     * @return 端点 URL；协议没有可用的目录接口时返回空串（调用方据此跳过这次请求，
     *         而不是去请求一个猜出来的地址）
     */
    public static String modelCatalogEndpoint(SessionConfig config) {
        String base = stripTrailingSlash(ApiUrlPolicy.requireBaseUrl(config));
        String protocol = config == null || config.protocol == null ? "" : config.protocol;
        if (PROTOCOL_ANTHROPIC.equals(protocol)
                || PROTOCOL_OPENAI_CHAT.equals(protocol)
                || PROTOCOL_OPENAI_RESPONSES.equals(protocol)) {
            return appendVersionedPath(base, MODELS_PATH);
        }
        return "";
    }

    /**
     * 去掉末尾的全部斜杠。
     *
     * <p>用循环而不是 {@code replaceAll("/+$", "")}：后者会为正则付出编译代价，
     * 而这里每拼一个端点都要调用一次。
     */
    public static String stripTrailingSlash(String base) {
        String out = base == null ? "" : base.trim();
        while (out.endsWith("/")) out = out.substring(0, out.length() - 1);
        return out;
    }

    /** 基址已含 {@code /v1} 时直接拼路径，否则先补上版本段。 */
    private static String appendVersionedPath(String base, String path) {
        String lower = base.toLowerCase(Locale.US);
        return lower.endsWith("/" + VERSION_SEGMENT)
                ? base + "/" + path
                : base + "/" + VERSION_SEGMENT + "/" + path;
    }
}
