package com.termux.app.zhicode.api.compat;

import java.util.Locale;

/**
 * 把「推理强度」这一个用户概念，翻译成三个协议各自的说法。
 *
 * <h3>为什么需要翻译</h3>
 * 用户在设置里只选一次强度。但三家协议对这件事的表达完全不同：
 * <ul>
 *   <li><b>OpenAI Chat</b> 收 {@code reasoning_effort}，最高只到 {@code xhigh}；</li>
 *   <li><b>OpenAI Responses</b> 收 {@code reasoning.effort}，而且它有 {@code max}/{@code ultra}
 *       这两个更高档 —— 上游会把它们降级，这里刻意不降（见下）；</li>
 *   <li><b>Anthropic</b> 收 {@code output_config.effort}，最高档叫 {@code max}，
 *       而且没有 {@code minimal} 这一档。</li>
 * </ul>
 *
 * <h3>三条不能动的规则</h3>
 * <ol>
 *   <li><b>纯数字是「token 预算」，不是档位名。</b> 用户可能填 {@code 8192}；
 *       按预算落到对应档位。判定必须是「全部字符都是数字」——
 *       {@code -1}、{@code +100}、{@code 1e3}、{@code 12a} 都不算数字，
 *       它们要走档位名那条路（然后因为认不出而返回 null）。</li>
 *   <li><b>Responses 的 max/ultra 绝不降级。</b> 这是本类唯一的「不对称」：
 *       Chat 把 max/ultra 折叠成 xhigh（那个协议没有更高的表达），
 *       而 Responses 精确单发。{@code xxhigh} 是唯一一个本地别名，发出时写成 {@code xhigh}。
 *       把这条改成「和 Chat 一样」会让用户选了最高档却拿到次高档，
 *       而失败方式是完全静默的。</li>
 *   <li><b>认不出的值返回 null，绝不猜。</b> 返回 null 表示「完全不发这个字段」，
 *       让服务端用它自己的默认值 —— 这比发一个错的档位安全。
 *       唯一的例外是 {@link #openAIResponsesConfiguredEffort}：那里刻意原样透传未知值，
 *       因为 Responses 允许自定义档位名。</li>
 * </ol>
 *
 * <p>实现上按「先归一 → 再解析成一个档位 → 最后按协议取名」三步走，
 * 而不是三个方法各自一长串字符串比较。这样「哪一档存在、谁映射到谁」是一张表，
 * 加协议或加档位时只需要改表。
 */
public final class ReasoningMapper {

    /**
     * 档位，从低到高。
     *
     * <p>顺序即语义：数字预算靠 {@link #budgetTier} 落到某一档。
     * 刻意不含「最高档」{@code max}/{@code ultra}：它们没有 token 预算，
     * 只能由用户显式写下名字，所以单独由 {@link #MAX_HIGH_END} 那组名字表达。
     */
    private enum Tier {
        MINIMAL, LOW, MEDIUM, HIGH, XHIGH
    }

    /** 数字预算的分界（含上界）。超过最后一档就是 {@link Tier#XHIGH}。 */
    private static final long MINIMAL_UPTO = 2_048L;
    private static final long LOW_UPTO = 4_096L;
    private static final long MEDIUM_UPTO = 8_192L;
    private static final long HIGH_UPTO = 16_384L;

    /** 表示「最高档」的名字。Responses 能发出去，Chat 折叠成 xhigh，Anthropic 写成 max。 */
    private static final String[] MAX_HIGH_END = {"max", "ultra"};

    /** 表示「不指定推理强度」的名字；看到它们就不该往请求里放这个字段。 */
    private static final String[] UNSPECIFIED = {"auto", "adaptive"};

    private ReasoningMapper() {}

    // ------------------------------------------------------------------ 归一

    /**
     * 去掉空白并小写化；空串与空白归一成 {@code null}。
     *
     * <p>刻意**只**做这两件事，不做档位归一 —— 「{@code xxhigh} 要写成 {@code xhigh}」
     * 是具体协议的规则，不属于「把用户输入变成可比较的形式」这一步。
     * 分开的另一个好处是中间结果可读：调试时能一眼看出「用户填了什么」与「发出去什么」。
     */
    public static String normalizeRequestedEffort(String value) {
        if (value == null) return null;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    // ------------------------------------------------------------ 用户输入 → 档位

    /**
     * 把归一后的字符串解析成一个档位；认不出返回 {@code null}。
     *
     * <p>{@code max}/{@code ultra} 与 {@code xhigh}/{@code xxhigh} 在此**合并**：
     * 区不区分是各协议那一步的事（Chat 与 Anthropic 都把它们归到同一档，
     * Responses 才需要知道原始名字），所以这里保留原始字符串，
     * 让调用方在需要时自己判断。
     */
    private static Tier namedTier(String normalized) {
        if (normalized == null) return null;
        switch (normalized) {
            case "minimal": return Tier.MINIMAL;
            case "low": return Tier.LOW;
            case "medium": return Tier.MEDIUM;
            case "high": return Tier.HIGH;
            case "xhigh":
            case "xxhigh": return Tier.XHIGH;
            default: return null;
        }
    }

    private static boolean isMaxHighEnd(String normalized) {
        for (String name : MAX_HIGH_END) if (name.equals(normalized)) return true;
        return false;
    }

    private static boolean isUnspecified(String normalized) {
        for (String name : UNSPECIFIED) if (name.equals(normalized)) return true;
        return false;
    }

    /**
     * 纯数字才当 token 预算。
     *
     * <p>不用 {@code Long.parseLong} 去试，因为它会接受 {@code +100}；
     * 也不接受 {@code 1e3} 这类浮点写法 —— 预算是个整数，
     * 而「猜用户是不是想写 1000」这种事不该做。
     */
    private static Long parseBudget(String normalized) {
        if (normalized == null || normalized.isEmpty()) return null;
        for (int i = 0; i < normalized.length(); i++) {
            if (normalized.charAt(i) < '0' || normalized.charAt(i) > '9') return null;
        }
        try {
            return Long.valueOf(normalized);
        } catch (NumberFormatException tooLarge) {
            return null;
        }
    }

    /** 数字预算 → 档位。分界值本身落在下界那一档。 */
    private static Tier budgetTier(long budget) {
        if (budget <= MINIMAL_UPTO) return Tier.MINIMAL;
        if (budget <= LOW_UPTO) return Tier.LOW;
        if (budget <= MEDIUM_UPTO) return Tier.MEDIUM;
        if (budget <= HIGH_UPTO) return Tier.HIGH;
        return Tier.XHIGH;
    }

    private static String tierName(Tier tier) {
        switch (tier) {
            case MINIMAL: return "minimal";
            case LOW: return "low";
            case MEDIUM: return "medium";
            case HIGH: return "high";
            default: return "xhigh";
        }
    }

    // ------------------------------------------------------------ 各协议取名

    /**
     * OpenAI Chat 的 {@code reasoning_effort}。
     *
     * <p>这个协议的顶格是 {@code xhigh}，所以 {@code max}/{@code ultra} 折叠过去 ——
     * 不是「降级」，是「这个协议没有更高的表达」。{@code minimal} 保留：
     * Chat 有这个档，只有 Anthropic 才需要把它上抬成 low。
     */
    public static String openAIChatEffort(String value) {
        String v = normalizeRequestedEffort(value);
        Long budget = parseBudget(v);
        if (budget != null) return tierName(budgetTier(budget));
        if (isMaxHighEnd(v)) return "xhigh";
        Tier tier = namedTier(v);
        return tier == null ? null : tierName(tier);
    }

    /**
     * OpenAI Responses 的 {@code reasoning.effort} 在套用 wire 别名**之前**的值。
     *
     * <p>与 Chat 的关键差别：**未知值原样保留**。Responses 允许自定义档位名，
     * 把认不出的值丢掉会让用户以为「设置没生效」。
     * {@code auto}/{@code adaptive} 仍然表示「不指定」，所以返回 null。
     *
     * <p>数字 0 归一成 {@code "none"}：那是「显式关闭推理」的表示，
     * 调用方靠它决定完全不发 reasoning 字段（而不是发一个 effort）。
     */
    public static String openAIResponsesConfiguredEffort(String value) {
        String v = normalizeRequestedEffort(value);
        Long budget = parseBudget(v);
        if (budget != null) {
            return budget.longValue() <= 0L ? "none" : tierName(budgetTier(budget.longValue()));
        }
        if (v == null || isUnspecified(v)) return null;
        return v;
    }

    /**
     * OpenAI Responses 真正发到线上的 {@code reasoning.effort}。
     *
     * <p>精确单发：{@code max} 就是 {@code max}、{@code ultra} 就是 {@code ultra}。
     * {@code xxhigh} 是**唯一**的本地别名（写成 {@code xhigh}）。
     * 没有候选序列、没有重试降级 —— 上游曾经「400 就换低一档重发」，
     * 那个行为会让用户设的档位悄悄失效，这里不做。
     */
    public static String openAIResponsesWireEffort(String value) {
        String configured = openAIResponsesConfiguredEffort(value);
        if (configured == null) return null;
        return "xxhigh".equals(configured) ? "xhigh" : configured;
    }

    /**
     * Anthropic 的 {@code output_config.effort}。
     *
     * <p>两处与别家不同：
     * <ul>
     *   <li>没有 {@code minimal} 档 —— 所以它（以及由预算落到该档的值）上抬成 {@code low}。
     *       上抬而不是原样发出：发一个不存在的档位会被服务端直接拒绝。</li>
     *   <li>最高档叫 {@code max} —— 所以 {@code max}/{@code ultra} 归到这里，
     *       而不是像 Chat 那样折叠成 xhigh。</li>
     * </ul>
     */
    public static String anthropicEffort(String value) {
        String v = normalizeRequestedEffort(value);
        Long budget = parseBudget(v);
        if (budget != null) {
            Tier tier = budgetTier(budget);
            return tier == Tier.MINIMAL ? "low" : tierName(tier);
        }
        if (isMaxHighEnd(v)) return "max";
        Tier tier = namedTier(v);
        if (tier == null) return null;
        // minimal 与 xhigh/xxhigh 都要按本协议的叫法输出。
        return tier == Tier.MINIMAL ? "low" : tierName(tier);
    }
}
