package com.termux.app.zhicode.core;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 在「只给提供方的消息快照」上，把图片降级成文字占位。
 *
 * <h3>为什么落在这里，而不是每个提供方各自处理</h3>
 * 三家提供方的报文格式不同（Responses 用 {@code input_image}，
 * Chat Completions 用 {@code image_url}，Anthropic 用 {@code source}）。
 * 在统一的消息快照上先转换一次，三家就都不必各自回答
 * 「视觉关掉了该怎么办」—— 那个问题回答三次，就有三种不一致的可能。
 *
 * <h3>为什么是替换而不是丢弃</h3>
 * 直接把图片块删掉，模型会完全不知道用户发过东西，于是回答一个与实际输入
 * 无关的内容，而用户看到的是一个「莫名其妙答非所问」的 agent。
 * 换成一行说明之后，模型至少知道「这里本来有一张图、但没发过来」，
 * 用户看历史时也能理解当时为什么没看图。
 *
 * <h3>调用约定：就地修改</h3>
 * 传入的 {@link JSONArray} 会被**就地修改**。调用方传的是为本次请求新做的快照
 * （见 {@code ZhiCodeEngine} 的两处调用），不是持久化的会话历史 ——
 * 这里不去额外深拷贝一遍，因为那会在每一轮请求上多复制一次整个对话。
 */
final class VisionMessageFilter {

    private static final String KEY_TYPE = "type";
    private static final String KEY_CONTENT = "content";
    private static final String KEY_TEXT = "text";
    private static final String KEY_NAME = "name";

    private static final String TYPE_IMAGE = "image";
    private static final String TYPE_TEXT = "text";

    /** 图片没有名字时用的占位名。 */
    private static final String DEFAULT_IMAGE_NAME = "图片";

    /**
     * 占位文本。
     *
     * <p>模板拼在一个 String 里而不是写成 {@code String.format}：这段文字里有全角
     * 括号和中文，用 format 时要小心 {@code %} 与转义，收益为负。
     */
    private static final String PLACEHOLDER_PREFIX =
        "[图片未发送：API 设置中的视觉输入已关闭；文件：";
    private static final String PLACEHOLDER_SUFFIX = "]";

    private VisionMessageFilter() {}

    /**
     * @param messages      提供方消息快照；会被就地修改
     * @param visionEnabled 为真时原样返回（连一次遍历都不做）
     * @return 处理后的同一个数组
     */
    static JSONArray apply(JSONArray messages, boolean visionEnabled) throws Exception {
        // 视觉开启是最常见的情况。提前返回省掉一次对整段对话的遍历。
        if (messages == null || visionEnabled) return messages;

        for (int i = 0; i < messages.length(); i++) {
            JSONObject message = messages.optJSONObject(i);
            if (message == null) continue;
            JSONArray content = message.optJSONArray(KEY_CONTENT);
            if (content == null) continue;
            message.put(KEY_CONTENT, filtered(content));
        }
        return messages;
    }

    /** 逐块过滤一条消息的内容数组。 */
    private static JSONArray filtered(JSONArray content) throws Exception {
        JSONArray kept = new JSONArray();
        for (int i = 0; i < content.length(); i++) {
            Object raw = content.opt(i);
            if (!(raw instanceof JSONObject)) {
                // 内容数组里理论上只放对象。真遇到别的类型就原样带走 ——
                // 「丢弃」是比「类型不符」更难查的问题：它会表现为内容凭空少了一块。
                if (raw != null) kept.put(raw);
                continue;
            }
            JSONObject block = (JSONObject) raw;
            kept.put(TYPE_IMAGE.equals(block.optString(KEY_TYPE, "")) ? placeholder(block) : block);
        }
        return kept;
    }

    /**
     * 把图片块换成一行说明。
     *
     * <p>带上文件名是给用户看的：一次请求里可能有多张图，没有名字的话
     * 「哪张没发出去」就无从判断。
     */
    private static JSONObject placeholder(JSONObject imageBlock) throws Exception {
        String name = imageBlock.optString(KEY_NAME, DEFAULT_IMAGE_NAME).trim();
        if (name.isEmpty()) name = DEFAULT_IMAGE_NAME;
        return new JSONObject()
            .put(KEY_TYPE, TYPE_TEXT)
            .put(KEY_TEXT, PLACEHOLDER_PREFIX + name + PLACEHOLDER_SUFFIX);
    }
}
