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
     * <p>除 Codex 变体外都走同一个 {@code /v1/models}：这既是 OpenAI 的约定，
     * 也是 Anthropic 与各兼容网关广为接受的路径。
     *
     * <p><b>为什么要为 Codex 单独排除</b>：那个后端（{@code .../backend-api/codex}）
     * 没有 {@code /v1/models} 这个接口。照旧拼一个出来会让"拉模型列表"必然 404，
     * 而失败的现象只是"列表空着"，看不出是"这个端点本来就不存在"。
     * 返回空串表示**该协议没有目录接口**，调用方据此跳过这次请求、回落到手填模型名。
     *
     * <p>先校验地址、再判断协议，这个顺序是刻意的：地址没配是**配置缺失**，
     * 报出来用户才知道要去填；协议没有目录接口是**能力缺失**，此时返回空串让界面
     * 回落到手填模型名。若反过来，一个「协议不认识 + 地址没填」的配置会得到
     * 「该协议没有目录接口」，用户会以为是协议的问题。
     *
     * @return 端点 URL；协议没有可用的目录接口时返回空串（调用方据此跳过这次请求，
     *         而不是去请求一个猜出来的地址）
     */
    public static String modelCatalogEndpoint(SessionConfig config) {
        String base = stripTrailingSlash(ApiUrlPolicy.requireBaseUrl(config));
        ApiProtocol protocol = protocolOf(config);
        if (protocol == null) return "";
        if (protocol == ApiProtocol.CODEX_RESPONSES) return "";
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

    /**
     * 会话端点（{@code chat/completions} / {@code responses} / {@code messages}
     * 这类 POST 端点）。
     *
     * <p>比模型目录多一层宽容：用户除了 {@code https://host} 与
     * {@code https://host/v1} 两种习惯之外，还会把**完整端点**整个粘进来。
     * 三种写法必须得到同一个地址，否则就是 404。
     *
     * <p>这条去重规则原先只长在 chat 端点上；responses 与 messages 各自
     * 直接拼 {@code /v1/...}，用户 base 里带了 {@code /v1} 就会拼出
     * {@code /v1/v1/responses} —— 实测表现是「同一份配置，chat 能通、
     * responses/anthropic 全 404」。收编到这里之后，新协议没有机会再漏。
     *
     * @param leafPath 带前导斜杠的端点叶子，如 {@code "/responses"}
     * @param versioned 该端点是否带 {@code /v1} 版本段（codex 变体不带）
     */
    static String sessionEndpoint(String baseUrl, String leafPath, boolean versioned) {
        String base = stripTrailingSlash(baseUrl);
        String lower = base.toLowerCase(Locale.US);
        if (lower.endsWith(leafPath)) return base;
        if (versioned && lower.endsWith("/" + VERSION_SEGMENT)) return base + leafPath;
        return versioned ? base + "/" + VERSION_SEGMENT + leafPath : base + leafPath;
    }
}
