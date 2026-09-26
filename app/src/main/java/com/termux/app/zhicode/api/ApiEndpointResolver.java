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
 * 用户可能填 {@code https://host/V1/}。注意**只影响判断，不改写**：
 * 大小写是用户自己填的，我们没资格替他改（{@code /V1/models} 是原样保留的）。
 *
 * <p>协议名到端点的对应关系由 {@link ApiProtocol} 决定 —— 那里也是
 * 「哪些协议名是认识的」的唯一出处，见 {@link #modelCatalogEndpoint(SessionConfig)}。
 */
public final class ApiEndpointResolver {

    private static final String VERSION_SEGMENT = "v1";
    private static final String MODELS_PATH = "models";

    private ApiEndpointResolver() {}

    /**
     * 模型目录端点。
     *
     * <p>三种协议都走同一个 {@code /v1/models}：这既是 OpenAI 的约定，
     * 也是 Anthropic 与各兼容网关广为接受的路径。将来若出现没有标准目录接口的协议，
     * 在 {@link ApiProtocol} 上加一项并在这里排除即可。
     *
     * <p>先校验地址、再判断协议，这个顺序是刻意的：地址没配是**配置缺失**，
     * 报出来用户才知道要去填；协议不认识是**能力缺失**，此时返回空串让界面
     * 回落到手填模型名。若反过来，一个「协议不认识 + 地址没填」的配置会得到
     * 「该协议没有目录接口」，用户会以为是协议的问题。
     *
     * @return 端点 URL；协议没有可用的目录接口时返回空串（调用方据此跳过这次请求，
     *         而不是去请求一个猜出来的地址）
     */
    public static String modelCatalogEndpoint(SessionConfig config) {
        String base = stripTrailingSlash(ApiUrlPolicy.requireBaseUrl(config));
        if (protocolOf(config) == null) return "";
        return withVersionSegment(base, MODELS_PATH);
    }

    /** 读取配置里的协议；{@code config} 为空或协议未收录都返回 {@code null}。 */
    private static ApiProtocol protocolOf(SessionConfig config) {
        return ApiProtocol.fromWire(config == null ? null : config.protocol);
    }

    /**
     * 去掉末尾的全部斜杠。
     *
     * <p>用循环而不是 {@code replaceAll("/+$", "")}：后者会为正则付出编译代价，
     * 而这里每拼一个端点都要调用一次。
     *
     * <p>{@code null} 与空白都归到空串 —— 拼端点失败应当报出「地址没配」，
     * 而不是在某个深处抛一个 NPE。
     */
    static String stripTrailingSlash(String base) {
        String out = base == null ? "" : base.trim();
        while (out.endsWith("/")) out = out.substring(0, out.length() - 1);
        return out;
    }

    /** 基址已含 {@code /v1} 时直接拼路径，否则先补上版本段。 */
    private static String withVersionSegment(String base, String path) {
        boolean versioned = base.toLowerCase(Locale.US).endsWith("/" + VERSION_SEGMENT);
        return versioned ? base + "/" + path : base + "/" + VERSION_SEGMENT + "/" + path;
    }
}
