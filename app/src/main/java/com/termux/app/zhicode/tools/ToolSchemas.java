package com.termux.app.zhicode.tools;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * 工具入参的 JSON Schema 拼装。
 *
 * <h3>为什么到处都要包一层 {@link #with}</h3>
 * {@link JSONObject#put} 声明了受检的 {@link JSONException}，而它在这里**不可能**发生：
 * 键是字面量、值是本类自己造的对象。若不加处理，每个工具的 {@code inputSchema()}
 * 都得声明 {@code throws Exception} —— 那个签名会一路传染到
 * {@link ZhiTool} 接口，最后让每一个调用点都补 try。
 * {@link #with} 把「不可能发生的受检异常」在一个地方转成运行时异常，
 * 于是下面的代码可以顺畅地链式拼装。
 *
 * <h3>键名是发给模型的契约</h3>
 * {@code "type"} / {@code "properties"} / {@code "required"} 等是 JSON Schema 关键字，
 * 不能改名。{@code additionalProperties: false} 是有意加的：不关掉的话模型会发明
 * 额外字段，而那些字段没有工具会读 —— 静默丢弃比让它们出现在提示词里更干净。
 */
final class ToolSchemas {

    private static final String KEY_TYPE = "type";
    private static final String KEY_DESCRIPTION = "description";
    private static final String KEY_PROPERTIES = "properties";
    private static final String KEY_REQUIRED = "required";
    private static final String KEY_ADDITIONAL = "additionalProperties";
    private static final String KEY_ITEMS = "items";
    private static final String KEY_MINIMUM = "minimum";
    private static final String KEY_ENUM = "enum";

    private static final String TYPE_OBJECT = "object";
    private static final String TYPE_STRING = "string";
    private static final String TYPE_INTEGER = "integer";
    private static final String TYPE_ARRAY = "array";
    private static final String TYPE_BOOLEAN = "boolean";

    private ToolSchemas() {}

    /**
     * 对象 schema。
     *
     * <p>没有必填项时**不加** {@code required} 键（而不是加一个空数组）：
     * 空数组会被某些服务端判成 schema 非法。
     */
    static JSONObject object(JSONObject properties, String... required) {
        JSONObject schema = with(typed(TYPE_OBJECT), KEY_PROPERTIES, properties);
        JSONArray names = new JSONArray();
        if (required != null) {
            for (String name : required) names.put(name);
        }
        if (names.length() > 0) schema = with(schema, KEY_REQUIRED, names);
        return with(schema, KEY_ADDITIONAL, false);
    }

    static JSONObject string(String description) {
        return documented(TYPE_STRING, description);
    }

    /** 整数参数。{@code minimum} 是契约的一部分：模型据此知道不能给 0 或负数。 */
    static JSONObject integer(String description, int minimum) {
        return with(documented(TYPE_INTEGER, description), KEY_MINIMUM, minimum);
    }

    static JSONObject stringArray(String description) {
        return with(documented(TYPE_ARRAY, description), KEY_ITEMS, typed(TYPE_STRING));
    }

    static JSONObject enumString(String description, String... values) {
        JSONArray allowed = new JSONArray();
        for (String value : values) allowed.put(value);
        return with(documented(TYPE_STRING, description), KEY_ENUM, allowed);
    }

    /**
     * 一个「任意对象」参数，**不带** {@code additionalProperties: false}。
     *
     * <p>与 {@link #object} 的差别是有意的：这里承载自由格式数据
     * （任务元数据、意图 extras），键由调用方现编，所以不能声明成封闭对象。
     */
    static JSONObject freeObject(String description) {
        return documented(TYPE_OBJECT, description);
    }

    static JSONObject bool(String description) {
        return documented(TYPE_BOOLEAN, description);
    }

    private static JSONObject documented(String type, String description) {
        return with(typed(type), KEY_DESCRIPTION, description);
    }

    /** 只有 {@code type} 的骨架。 */
    private static JSONObject typed(String type) {
        return with(new JSONObject(), KEY_TYPE, type);
    }

    /**
     * 带 catch 的 put。
     *
     * <p>它让上层可以链式拼装而不必层层声明受检异常。见类注释。
     */
    private static JSONObject with(JSONObject target, String key, Object value) {
        try {
            return target.put(key, value);
        } catch (JSONException impossible) {
            // 键是字面量、值是本类自己造的对象，不会失败。真失败说明
            // JSONObject 的实现换了，报出来比吞掉好。
            throw new IllegalStateException(impossible);
        }
    }
}
