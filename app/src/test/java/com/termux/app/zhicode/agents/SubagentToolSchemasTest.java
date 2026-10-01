package com.termux.app.zhicode.agents;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * 子代理工具 schema 的**结构**断言。
 *
 * <h3>为什么必须有一条这样的测试</h3>
 *
 * 这里出过一次真事故，而且它在本机完全不报错：{@code Agent} 的入参 schema 少了最外层
 * 的 {@code {"type":"object","properties":{…}}} —— 返回的是**裸的属性表**。
 * 真因是"辅助方法的返回值被丢掉"（表达式 lambda 的返回值落进了 {@code void} 参数），
 * 编译通过、单测若只看"非空"也照样通过，直到换成严格的 OpenAI 兼容端点
 * （DeepSeek 等）才暴露：
 *
 * <pre>
 * OpenAI 兼容 API HTTP 400: Invalid schema for function 'Agent':
 * {"type":"description","description":"A short 3-5 word task description."}
 * is not of type "string"
 * </pre>
 *
 * <p>所以下面这些断言都是**对产出本身的形状**做检查（而不是"能跑就行"）：
 * 每个属性必须有合法的 {@code type}、对象层必须有 {@code properties}、
 * {@code required} 里的名字必须真的存在于 {@code properties}。
 * 这些正是"少一层包装"时立刻会红的东西。
 */
public final class SubagentToolSchemasTest {

    private static final Set<String> VALID_TYPES = new HashSet<>();

    static {
        VALID_TYPES.add("string");
        VALID_TYPES.add("number");
        VALID_TYPES.add("integer");
        VALID_TYPES.add("boolean");
        VALID_TYPES.add("object");
        VALID_TYPES.add("array");
        VALID_TYPES.add("null");
    }

    /**
     * 单个属性的 schema 必须自带合法 {@code type}。
     *
     * <p>这就是那个 400 的直接原因：属性表被当成 schema 之后，
     * 每个键（description / prompt …）被当成"属性名"，而它的值里
     * {@code type} 是 {@code "description"} 这种非法值。
     */
    private static void assertPropertySchema(String path, JSONObject schema) throws Exception {
        Object type = schema.opt("type");
        assertNotNull(path + " 缺少 type —— 属性 schema 必须自带类型，"
                + "否则严格的服务端会把属性名当成类型名（这正是 DeepSeek 报 400 的形状）", type);
        assertTrue(path + " 的 type 不是字符串: " + type, type instanceof String);
        assertTrue(path + " 的 type 非法: " + type + "（合法值: " + VALID_TYPES + "）",
                VALID_TYPES.contains(type));
        Object description = schema.opt("description");
        assertTrue(path + " 必须有 description：它是模型唯一能看到的说明",
                description instanceof String && !((String) description).isEmpty());
    }

    /** 对象层：必须有 type=object 与 properties。 */
    private static JSONObject assertObjectSchema(String path, JSONObject schema) throws Exception {
        assertEquals(path + " 的 type 必须是 object（少了这一层就是那次事故）",
                "object", schema.optString("type"));
        Object properties = schema.opt("properties");
        assertNotNull(path + " 缺少 properties", properties);
        assertTrue(path + " 的 properties 不是对象", properties instanceof JSONObject);
        JSONObject map = (JSONObject) properties;
        assertTrue(path + " 的 properties 为空（空 schema 等于没有参数说明）", map.length() > 0);
        return map;
    }

    /** required 里的每个名字都必须真的存在于 properties。 */
    private static void assertRequiredIsSubset(String path, JSONObject schema) throws Exception {
        JSONArray required = schema.optJSONArray("required");
        if (required == null) return;
        JSONObject properties = schema.getJSONObject("properties");
        for (int i = 0; i < required.length(); i++) {
            String name = required.optString(i);
            assertTrue(path + " 的 required 里有 " + name + "，但 properties 里没有它 —— "
                    + "服务端会直接拒绝这个 schema", properties.has(name));
        }
    }

    @Test
    public void agentSchemaIsAWrappedObjectWithTypedProperties() throws Exception {
        JSONObject schema = SubagentToolSchemas.agentInputSchema();
        JSONObject properties = assertObjectSchema("Agent.input_schema", schema);
        assertRequiredIsSubset("Agent.input_schema", schema);

        for (Iterator<String> it = properties.keys(); it.hasNext(); ) {
            String name = it.next();
            assertPropertySchema("Agent.input_schema.properties." + name, properties.getJSONObject(name));
        }

        // 这两个是必填项：漏掉的话模型可以只回一个工具名就完事。
        JSONArray required = schema.getJSONArray("required");
        Set<String> requiredNames = new HashSet<>();
        for (int i = 0; i < required.length(); i++) requiredNames.add(required.getString(i));
        assertTrue("Agent 必须要求 description", requiredNames.contains("description"));
        assertTrue("Agent 必须要求 prompt", requiredNames.contains("prompt"));
    }

    @Test
    public void isolationIsAnEnumAndCwdExplainsTheExclusivity() throws Exception {
        JSONObject properties = SubagentToolSchemas.agentInputSchema().getJSONObject("properties");

        // isolation 是枚举：模型不该发明第三种隔离模式。
        JSONObject isolation = properties.getJSONObject("isolation");
        assertNotNull("isolation 必须是枚举", isolation.optJSONArray("enum"));
        assertEquals("idempotent", "worktree", isolation.getJSONArray("enum").getString(0));

        // isolation 与 cwd 互斥：这条只能靠描述告诉模型，缺了它模型会把两个都填上。
        String cwd = properties.getJSONObject("cwd").getString("description");
        assertTrue("cwd 的描述必须写明与 isolation 互斥，否则模型会两个都填",
                cwd.contains("isolation"));
    }

    @Test
    public void taskSchemasAreWrappedAndRequireTaskId() throws Exception {
        for (String name : new String[]{"TaskOutput", "TaskStop"}) {
            JSONObject schema = name.equals("TaskOutput")
                    ? SubagentToolSchemas.taskOutputSchema()
                    : SubagentToolSchemas.taskStopSchema();
            JSONObject properties = assertObjectSchema(name, schema);
            assertRequiredIsSubset(name, schema);

            for (Iterator<String> it = properties.keys(); it.hasNext(); ) {
                String key = it.next();
                assertPropertySchema(name + ".properties." + key, properties.getJSONObject(key));
            }
            assertTrue(name + " 必须要求 task_id",
                    schema.getJSONArray("required").toString().contains("task_id"));
        }

        // timeout 是 integer 且下限为 0：负数超时没有意义。
        JSONObject timeout = SubagentToolSchemas.taskOutputSchema()
                .getJSONObject("properties").getJSONObject("timeout");
        assertEquals("integer", timeout.getString("type"));
        assertEquals(0, timeout.getInt("minimum"));
    }

    @Test
    public void apiSchemasExposeThreeToolsWithDistinctNames() throws Exception {
        JSONArray schemas = SubagentToolSchemas.apiSchemas();
        assertEquals("子代理工具恰好三个（Agent / TaskOutput / TaskStop）", 3, schemas.length());

        Set<String> names = new HashSet<>();
        for (int i = 0; i < schemas.length(); i++) {
            JSONObject tool = schemas.getJSONObject(i);
            String name = tool.optString("name");
            assertFalse("工具名不能为空", name.isEmpty());
            assertTrue("工具名重复: " + name, names.add(name));

            assertTrue(name + " 缺少 description（模型靠它判断何时用这个工具）",
                    tool.optString("description").length() > 10);

            // 每个工具的入参都必须是**包好的对象 schema**，而不是裸属性表。
            JSONObject input = tool.optJSONObject("input_schema");
            assertNotNull(name + " 缺少 input_schema", input);
            assertObjectSchema(name + ".input_schema", input);
            assertRequiredIsSubset(name + ".input_schema", input);

            // 逐个属性检查类型：这是那次事故唯一能提前发现的断言。
            JSONObject properties = input.getJSONObject("properties");
            for (Iterator<String> it = properties.keys(); it.hasNext(); ) {
                String key = it.next();
                JSONObject property = properties.optJSONObject(key);
                if (property == null) {
                    fail(name + ".input_schema.properties." + key + " 不是对象");
                }
                assertPropertySchema(name + ".input_schema.properties." + key, property);
            }
        }

        assertTrue("必须含 Agent", names.contains("Agent"));
        assertTrue("必须含 TaskOutput", names.contains("TaskOutput"));
        assertTrue("必须含 TaskStop", names.contains("TaskStop"));
    }
}
