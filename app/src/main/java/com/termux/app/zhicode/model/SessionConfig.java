package com.termux.app.zhicode.model;

import com.termux.shared.termux.TermuxConstants;

import java.util.UUID;

/**
 * 一次会话（或一次子代理运行）的全部可变配置。
 *
 * <h3>为什么是一个大对象而不是分组传递</h3>
 * 参与方太多：引擎、每个工具、每个 provider、界面设置页都要读其中若干项，
 * 而它们之间已经通过这个对象连接（工具签名是 {@code execute(SessionConfig, JSONObject)}）。
 * 拆成分组对象会让每个工具的签名都要跟着变，收益只是「少传几个字段」。
 *
 * <h3>字段顺序与分组</h3>
 * 字段按「传输 / 提示词 / 会话身份 / 权限 / 工作目录 / 预算 / 联网工具」分组。
 * 顺序本身没有语义（除了 {@link #threadId} 初值取自 {@link #sessionId}，
 * 必须排在它之后），但 {@link #copy()} 必须覆盖**每一个**字段 ——
 * 漏一个的后果是「某次复制之后设置悄悄回退成默认值」，这种 bug 极难定位，
 * 所以下面 copy() 的分组与参数顺序和字段声明严格一一对应。
 *
 * <h3>这里是唯一真实来源</h3>
 * 这些字段名也是**存盘格式的一部分**（会话 JSONL 与设置里用同名键），
 * 所以改名等于让旧会话读不回来。
 */
public final class SessionConfig {

    // ------------------------------------------------------------------ 传输

    public String protocol = "openai-responses";
    /** 用户填的 API 地址。在设置里配置之前**刻意**是空的（不内置任何厂商地址）。 */
    public String baseUrl = "";
    public String apiKey = "";
    /** 当前生效的配置文件 id 与其版本，用于识别「配置已被改过」。 */
    public String profileId = "";
    public int profileRevision = 1;
    /** 密钥槽位版本，见 {@link ApiProfile#credentialRevision}。 */
    public int credentialRevision = 1;
    /** 模型名。空串表示还没填，由引擎侧报明确错误，而不是发一个空模型名的请求。 */
    public String model = "";
    /** 推理强度档位，交给 ReasoningMapper 翻译成各协议的说法。 */
    public String effort = "high";

    // ---------------------------------------------------------------- 提示词

    /** 用户自己写的高优先级指令。仍然低于代码强制的安全与权限规则。 */
    public String customSystemPrompt = "";
    /** 角色卡。 */
    public String roleCard = "";
    /** 推理摘要的呈现方式。 */
    public String reasoningSummary = "auto";
    public boolean preserveReasoningState = true;
    /** 工具暴露策略（{@code auto} / {@code native} 等）。 */
    public String toolMode = "auto";
    public boolean streamThinking = true;
    /** 请求里是否带图片块。关掉后视觉模型也收不到图。 */
    public boolean visionEnabled = true;

    // ------------------------------------------------------------ 会话身份

    public String sessionId = UUID.randomUUID().toString();
    /** 传输层对话标识。与 sessionId 分开是因为重新开始传输不必换会话文件。 */
    public String threadId = sessionId;
    /** 任务命名空间，在一次编码工作流的生命周期内稳定。 */
    public String workflowId = UUID.randomUUID().toString();

    // ---------------------------------------------------------------- 权限

    public String permissionMode = "default";
    /** 进入计划模式之前的权限模式，退出时还原。 */
    public String permissionModeBeforePlan = "default";
    /** 计划流程的当前快照。**整份替换**，所以是 volatile。 */
    public volatile PlanWorkflowState planWorkflowState = PlanWorkflowState.idle();
    /** 只在沙箱内生效的完全授权。真机/系统进程不在范围内。 */
    public boolean sandboxAgentFullAccess = true;
    /** 打开 Root 工具入口。命令仍然需要真实的 su 授权。 */
    public boolean rootExecutionEnabled = false;
    /** 用户显式开启的常驻前台服务。 */
    public boolean forcedKeepAliveEnabled = false;

    // ------------------------------------------------------------ 工作目录

    public String projectDirectory = TermuxConstants.TERMUX_HOME_DIR_PATH;
    /** 建 worktree 之前所在的目录，退出 worktree 时回去。 */
    public String worktreeOriginalDirectory = "";
    /** 当前隔离工作树路径；空串表示没有隔离。 */
    public String worktreePath = "";

    // ------------------------------------------------------------ 预算与压缩

    public int maxTokens = 32768;
    /** 单次用户请求内允许的最大模型轮数。 */
    public int maxAgentTurns = 100;
    public int contextWindowTokens = 128000;
    public boolean autoCompact = true;
    /** 用户可选的压缩阈值系数；Claude 式输出预留与 13k 安全余量仍然是权威。 */
    public double autoCompactRatio = 1.0;

    // ------------------------------------------------------------ 联网工具

    public boolean webSearchEnabled = true;
    public String webSearchProvider = "auto";
    public int webSearchMaxResults = 6;
    /** 密钥制搜索服务（tavily/exa/brave）的 Key；免费后端忽略。 */
    public String webSearchApiKey = "";
    /** SearXNG 实例地址；只有 provider=searxng 用到。 */
    public String webSearchBaseUrl = "";
    public int webFetchMaxChars = 30000;
    public int webTimeoutMs = 15000;

    /** 换一个传输层对话标识（服务端要求新一轮对话时用）。会话文件不受影响。 */
    public void renewTransportSession() {
        sessionId = UUID.randomUUID().toString();
        threadId = sessionId;
    }

    /**
     * 深拷一份。
     *
     * <p>会话在运行中会派生子代理、重试请求、跨线程传递快照，每一处都需要一份
     * 「之后只属于我自己」的配置；共享一份的话，子代理改设置会改到主会话。
     *
     * <p>唯一需要特殊对待的是 {@link #planWorkflowState}：它自己也必须是新快照
     * （见 {@link PlanWorkflowState}），且读取时要先落到局部变量 —— 它是 volatile，
     * 两次读之间可能被别人换掉，那样会拷到一个「半新半旧」的组合。
     */
    public SessionConfig copy() {
        SessionConfig copy = new SessionConfig();

        copy.protocol = protocol;
        copy.baseUrl = baseUrl;
        copy.apiKey = apiKey;
        copy.profileId = profileId;
        copy.profileRevision = profileRevision;
        copy.credentialRevision = credentialRevision;
        copy.model = model;
        copy.effort = effort;

        copy.customSystemPrompt = customSystemPrompt;
        copy.roleCard = roleCard;
        copy.reasoningSummary = reasoningSummary;
        copy.preserveReasoningState = preserveReasoningState;
        copy.toolMode = toolMode;
        copy.streamThinking = streamThinking;
        copy.visionEnabled = visionEnabled;

        copy.sessionId = sessionId;
        copy.threadId = threadId;
        copy.workflowId = workflowId;

        copy.permissionMode = permissionMode;
        copy.permissionModeBeforePlan = permissionModeBeforePlan;
        copy.planWorkflowState = snapshotPlanWorkflowState();
        copy.sandboxAgentFullAccess = sandboxAgentFullAccess;
        copy.rootExecutionEnabled = rootExecutionEnabled;
        copy.forcedKeepAliveEnabled = forcedKeepAliveEnabled;

        copy.projectDirectory = projectDirectory;
        copy.worktreeOriginalDirectory = worktreeOriginalDirectory;
        copy.worktreePath = worktreePath;

        copy.maxTokens = maxTokens;
        copy.maxAgentTurns = maxAgentTurns;
        copy.contextWindowTokens = contextWindowTokens;
        copy.autoCompact = autoCompact;
        copy.autoCompactRatio = autoCompactRatio;

        copy.webSearchEnabled = webSearchEnabled;
        copy.webSearchProvider = webSearchProvider;
        copy.webSearchMaxResults = webSearchMaxResults;
        copy.webSearchApiKey = webSearchApiKey;
        copy.webSearchBaseUrl = webSearchBaseUrl;
        copy.webFetchMaxChars = webFetchMaxChars;
        copy.webTimeoutMs = webTimeoutMs;

        return copy;
    }

    /** 取计划快照的一份拷贝；为 null（外部塞进来的）时给空快照而不是 null。 */
    private PlanWorkflowState snapshotPlanWorkflowState() {
        PlanWorkflowState snapshot = planWorkflowState;
        return snapshot == null ? PlanWorkflowState.idle() : snapshot.copy();
    }
}
