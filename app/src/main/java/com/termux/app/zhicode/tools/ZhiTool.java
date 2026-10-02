package com.termux.app.zhicode.tools;

import com.termux.app.zhicode.model.SessionConfig;
import com.termux.app.zhicode.model.ToolExecutionResult;

import org.json.JSONObject;

/**
 * 工具插件接口。
 *
 * <h3>签名为什么是这样</h3>
 * 每个方法都是「问工具要一个静态的事实」或「让它干一次活」，
 * {@link #execute} 收的 {@link SessionConfig} 是所有工具共用的运行上下文
 * （工作目录、权限模式、联网开关…）—— 拆成单独的参数对象会让每个工具的签名
 * 随新增配置字段而变，而它们其实只用其中一两项。
 *
 * <p>{@link #inputSchema()} 的字段名是**发给模型的契约**，不能随手改：
 * 改了模型就会继续按旧名字发参，而工具收到空值后表现为「参数没生效」。
 */
public interface ZhiTool {

    /** 模型看到的工具名。改它等于让所有已保存的会话与提示词里的引用失效。 */
    String name();

    /** 给模型看的一句话说明。它决定模型在什么情况下会想起这个工具。 */
    String description();

    /** 入参的 JSON Schema。 */
    JSONObject inputSchema();

    /** 权限分类，用于决定要不要向用户确认。 */
    PermissionKind permissionKind();

    /**
     * 这一次调用是否**必须**由用户确认（不看权限分类的默认规则）。
     *
     * <h3>为什么需要它：分类是静态的，而需求是逐次的</h3>
     * {@link #permissionKind()} 回答的是"这个工具属于哪一类"，所以它只能给出
     * 一个**整类**的规则。但有些限制是逐次、逐目标的：MCP 就是典型 ——
     * 同一个 {@code mcp_call} 工具，用户可能只对其中某一个远端工具要求确认。
     *
     * <p>默认 false：绝大多数工具不需要这个能力，让它们都实现一遍只是噪音。
     *
     * <p>⚠️ 引擎必须**在"这类工具永远放行"的判断之前**查这个方法。MCP 属于
     * {@link PermissionKind#NETWORK}，而 NETWORK 是永远放行的 ——
     * 放在那之后，这个开关就是个摆设：点了没有任何效果，也不会报错。
     *
     * <p>仍然受 {@code bypass} 权限模式压制：那档是用户明确说过"别问我"。
     *
     * @param input 模型给的调用参数（与 {@link #execute} 收到的是同一份）。
     *              可能缺字段、可能是空对象，实现方要自己宽容处理。
     */
    default boolean requiresApproval(JSONObject input) {
        return false;
    }

    ToolExecutionResult execute(SessionConfig config, JSONObject input) throws Exception;

    /**
     * 带实时进度的执行。
     *
     * <p>默认实现直接丢掉进度并转调两参版本：只有长时任务（跑命令、装包）需要流式进度，
     * 让不关心的工具也实现一遍只是噪音。
     */
    default ToolExecutionResult execute(SessionConfig config, JSONObject input, ProgressListener progress)
            throws Exception {
        return execute(config, input);
    }

    /**
     * 进度回调。
     *
     * <p>实现方（引擎）会在工作线程上被同步调用，所以实现里要快速返回 ——
     * 它正卡在工具的输出循环里。
     */
    interface ProgressListener {
        /**
         * @param chunk    新产出的输出片段；空串表示这是一次心跳（只是告诉界面「还活着」）
         * @param stderr   该片段来自标准错误
         * @param elapsedMs 工具已经跑了多久
         */
        void onProgress(String chunk, boolean stderr, long elapsedMs);
    }

    /**
     * 权限分类。
     *
     * <p>顺序无关，但语义要分清：
     * {@link #INTERNAL} 只动本应用自己的数据、{@link #READ} 只读、
     * {@link #NETWORK} 会出网、{@link #WRITE} 会改文件、{@link #SHELL} 会起进程、
     * {@link #SYSTEM} 会碰应用之外的系统状态（开别的 App、动 root）。
     */
    enum PermissionKind {
        INTERNAL,
        READ,
        NETWORK,
        WRITE,
        SHELL,
        SYSTEM
    }
}
