package com.termux.app.zhicode.agents;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 子代理三个工具（{@code Agent} / {@code TaskOutput} / {@code TaskStop}）的 JSON Schema。
 *
 * <h3>为什么单独一个类</h3>
 *
 * 这段逻辑是**纯 JSON 构造**：不碰 Context、不碰线程、不碰引擎状态。放在
 * {@link SubagentManager} 里时它只能靠"发一次真请求"来验证，而错误的 schema
 * 不一定每次都被服务端指出来 —— 见下面这个真实事故。
 *
 * <h3>踩过的坑：组装结果的返回值被丢掉</h3>
 *
 * 这里原本用的是两段式辅助方法：
 *
 * <pre>{@code
 * with(new JSONObject(),
 *      properties -> properties.put("description", string("...")).put("prompt", string("...")),
 *      properties -> objectSchema(properties, "description", "prompt"));
 * }</pre>
 *
 * 而 {@code with(target, properties, incomplete)} 的实现是"依次对同一个 target 跑两个
 * builder 并返回 target"。问题在于 {@code objectSchema(...)} 是**新建一个对象并返回**的，
 * 而 builder 的返回类型是 {@code void}（表达式 lambda 的返回值被丢弃）—— 于是
 * 外层那圈 {@code {"type":"object","properties":{…},"required":[…]}} **根本没有落到结果里**，
 * {@code agentInputSchema()} 返回的是**裸的属性表**。
 *
 * <p>表现：Anthropic 那边容错，一直没暴露；换成严格的 OpenAI 兼容端点（DeepSeek 等）后
 * 直接 400：
 *
 * <pre>
 * Invalid schema for function 'Agent': {"type":"description","description":"A short 3-5 word
 * task description."} is not of type "string"
 * </pre>
 *
 * <p>即"整个属性表被当成 schema 本身"之后的连锁解读错误。这类"算出来了但没接上"的 bug
 * 不会编译失败、也不会在本机报错，只能靠**对产出做断言**来防 —— 所以现在它有了 JVM 单测
 * （{@code SubagentToolSchemasTest}），逐条检查每个属性的 {@code type} 是否合法。
 */
final class SubagentToolSchemas {

    private static final String TYPE_OBJECT = "object";
    private static final String TYPE_STRING = "string";
    private static final String TYPE_BOOLEAN = "boolean";
    private static final String TYPE_INTEGER = "integer";

    private static final String KEY_TYPE = "type";
    private static final String KEY_PROPERTIES = "properties";
    private static final String KEY_REQUIRED = "required";

    private SubagentToolSchemas() {}

    /** 发给模型的三个子代理工具。它们不注册在 {@code ToolRegistry} 里，由 SubagentManager 自己实现。 */
    static JSONArray apiSchemas() {
        JSONArray schemas = new JSONArray();
        schemas.put(toolSchema("Agent",
                "Launch a specialized ZhiCode subagent in an independent context."
                        + " Subagents cannot spawn other subagents.",
                agentInputSchema()));
        schemas.put(toolSchema("TaskOutput",
                "Read the current or final output of a background subagent task.",
                taskOutputSchema()));
        schemas.put(toolSchema("TaskStop",
                "Stop a running background subagent task.",
                taskStopSchema()));
        return schemas;
    }

    private static JSONObject toolSchema(String name, String description, JSONObject input) {
        JSONObject schema = new JSONObject();
        return put(schema, name, description, input);
    }

    private static JSONObject put(JSONObject schema, String name, String description, JSONObject input) {
        try {
            schema.put("name", name);
            schema.put("description", description);
            schema.put("input_schema", input);
            return schema;
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /**
     * {@code Agent} 的入参 schema。
     *
     * <p>这些描述是模型唯一能看到的说明，因此每一条都要说清「填什么」，
     * 尤其是 {@code isolation} 与 {@code cwd} 互斥这一条 —— 不写的话模型会把两个都填上。
     */
    static JSONObject agentInputSchema() {
        JSONObject properties = new JSONObject();
        putProperty(properties, "description", string("A short 3-5 word task description."));
        putProperty(properties, "prompt", string("The task for the subagent to perform."));
        putProperty(properties, "subagent_type", string("Specialized agent type, such as Explore, Plan,"
                + " general-purpose, verification, or a custom agent name."));
        putProperty(properties, "model", string("Optional model override: inherit, sonnet, opus, haiku,"
                + " or a full model ID."));
        putProperty(properties, "run_in_background", bool("Run in background and return a task_id immediately."));
        putProperty(properties, "isolation", enumeration("Optional isolation mode.", "worktree"));
        putProperty(properties, "cwd", string("Optional absolute working directory; mutually exclusive"
                + " with isolation."));
        return objectSchema(properties, "description", "prompt");
    }

    static JSONObject taskOutputSchema() {
        JSONObject properties = new JSONObject();
        putProperty(properties, "task_id", string("Background agent task ID."));
        putProperty(properties, "block", bool("Wait for completion if still running. Default true."));
        putProperty(properties, "timeout", integer("Maximum wait in milliseconds (max 600000)."));
        return objectSchema(properties, "task_id");
    }

    static JSONObject taskStopSchema() {
        JSONObject properties = new JSONObject();
        putProperty(properties, "task_id", string("Background agent task ID."));
        return objectSchema(properties, "task_id");
    }

    // ------------------------------------------------------------------ 组装

    /**
     * 把属性表包成对象 schema。
     *
     * <p>这是**唯一**产生 {@code type:object} 那一层的地方；调用方必须使用它的返回值
     * （见类注释里那次事故）。
     */
    private static JSONObject objectSchema(JSONObject properties, String... required) {
        JSONObject schema = new JSONObject();
        try {
            schema.put(KEY_TYPE, TYPE_OBJECT);
            schema.put(KEY_PROPERTIES, properties);
            // 显式关闭额外属性：让模型知道它不能自己发明参数名。
            schema.put("additionalProperties", false);
            if (required.length > 0) {
                JSONArray requiredList = new JSONArray();
                for (String name : required) requiredList.put(name);
                schema.put(KEY_REQUIRED, requiredList);
            }
            return schema;
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void putProperty(JSONObject properties, String name, JSONObject schema) {
        try {
            properties.put(name, schema);
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static JSONObject string(String description) {
        return typed(TYPE_STRING, description);
    }

    private static JSONObject bool(String description) {
        return typed(TYPE_BOOLEAN, description);
    }

    private static JSONObject integer(String description) {
        JSONObject schema = typed(TYPE_INTEGER, description);
        try {
            schema.put("minimum", 0);
            return schema;
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static JSONObject enumeration(String description, String... values) {
        JSONObject schema = typed(TYPE_STRING, description);
        try {
            JSONArray options = new JSONArray();
            for (String value : values) options.put(value);
            schema.put("enum", options);
            return schema;
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static JSONObject typed(String type, String description) {
        JSONObject schema = new JSONObject();
        try {
            schema.put(KEY_TYPE, type);
            schema.put("description", description);
            return schema;
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
