package com.termux.app.zhicode.json;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * 只遍历 {@link JSONArray} 里真正的对象元素，跳过 null 与非对象项。
 *
 * <p>org.json 的数组可以放任意类型，也可以放 null（{@code put(null)}、解析失败的元素、
 * 以及 {@code JSONObject.NULL}）。于是「遍历数组里的对象」在一份实现里总要写成三行：
 *
 * <pre>
 * for (int i = 0; i &lt; array.length(); i++) {
 *     JSONObject item = array.optJSONObject(i);
 *     if (item == null) continue;
 * </pre>
 *
 * <p>本工程里这个形状出现过三十多处。它的问题不是啰嗦，而是**漏掉判空的那一处不报错**：
 * 只会在这条路径第一次遇到畸形报文时抛 NPE，而报文来自服务端或别的组件，本地测不到。
 * 收成一处之后，「跳过非对象」这件事只有一个实现，也只有一处需要被读。
 *
 * <h3>什么时候不要用它</h3>
 *
 * <p>当下标本身是语义的一部分时不要用 —— 例如「第几项」要报进错误信息、
 * 或要作为协议字段发出去（{@code output_index}、{@code fallback index}）。
 * 那时候循环变量不是遍历的副产品，而是一个要往外传的值，保留 {@code for (int i …)} 更诚实。
 *
 * <p>本类只做遍历，不做转换：{@link #of(JSONArray)} 传 null 视为空数组，
 * 因此调用点不再需要「先判数组为 null」那一层 —— 但保留它也没有坏处。
 */
public final class JsonItems implements Iterable<JSONObject> {

    private final JSONArray array;

    private JsonItems(JSONArray array) {
        this.array = array;
    }

    /** 数组为 null 时视为空数组。 */
    public static JsonItems of(JSONArray array) {
        return new JsonItems(array == null ? new JSONArray() : array);
    }

    @Override
    public Iterator<JSONObject> iterator() {
        final JSONArray source = array;
        return new Iterator<JSONObject>() {

            private int index;
            private JSONObject next = advance();

            /** 停在下一个对象元素上；没有就返回 null。 */
            private JSONObject advance() {
                while (index < source.length()) {
                    JSONObject candidate = source.optJSONObject(index++);
                    if (candidate != null) return candidate;
                }
                return null;
            }

            @Override
            public boolean hasNext() {
                return next != null;
            }

            @Override
            public JSONObject next() {
                if (next == null) throw new NoSuchElementException("JSONArray 里没有下一个对象元素");
                JSONObject current = next;
                next = advance();
                return current;
            }
        };
    }
}
