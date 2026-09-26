package com.termux.app.zhicode.model;

import org.json.JSONObject;

/**
 * 模型要求执行的一次工具调用。
 *
 * <p>{@link #input} 是**模型给的**参数，不保证符合工具的 schema ——
 * 校验是每个工具自己在 {@code execute} 里做的，这里只负责搬运。
 * 因此它可能是空对象、可能缺字段、可能多出根本不认识的键。
 */
public final class ToolCall {

    /** 协议给的调用标识，工具结果要按它回填。 */
    public final String id;

    /** 工具名，可能不存在于注册表（模型偶尔会发明工具名，由引擎回落处理）。 */
    public final String name;

    /** 调用参数。构造时保证非 null：模型给空参数时这里是 {@code {}} 而不是 null。 */
    public final JSONObject input;

    public ToolCall(String id, String name, JSONObject input) {
        this.id = id;
        this.name = name;
        // 统一成空对象，省掉每个使用点一次判空；空对象与「没有参数」在语义上等价。
        this.input = input == null ? new JSONObject() : input;
    }
}
