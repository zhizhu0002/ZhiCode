package com.termux.app.zhicode.core;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 在「只给提供方的消息快照」上，把图片降级成文字占位。
 *
 * <h3>为什么落在这里而不是请求构造处</h3>
 * 每个提供方的报文格式不同（Responses 用 {@code input_image}，
 * Chat Completions 用 {@code image_url}，Anthropic 用 {@code source}）。
 * 在统一的消息快照上先做一次转换，三个提供方就都不需要各自处理
 * 「视觉关掉了要怎么办」。
 *
 * <h3>为什么是替换而不是丢弃</h3>
 * 直接把图片块删掉会让模型完全不知道用户发过东西，它会回答一个
 * 与实际输入无关的内容。换成一行说明，模型至少知道
 * 「这里本来有一张图，但没发过来」——这比静默丢失更接近真实情况，
 * 用户看历史时也能理解为什么模型当时没看图。
 *
 * <h3>调用约定</h3>
 * 传入的 {@link JSONArray} 会被<b>就地修改</b>。调用方传的是为本次请求新做的快照
 * （见 {@code ZhiCodeEngine} 的两处调用），不是持久化的历史，
 * 因此这里不去额外深拷贝一遍 —— 那会在每轮请求上多复制一次整个对话。
 */
final class VisionMessageFilter {

    private static final String BLOCK_TYPE = "type";
    private static final String BLOCK_IMAGE = "image";
    private static final String BLOCK_TEXT = "text";

    /** 图片没有名字时用的占位名。 */
    private static final String DEFAULT_IMAGE_NAME = "图片";

    private VisionMessageFilter() {}

    /**
     * @param messages      提供方消息快照；会被就地修改
     * @param visionEnabled 为真时原样返回（不做任何遍历）
     * @return 处理后的同一个数组
     */
    static JSONArray apply(JSONArray messages, boolean visionEnabled) throws Exception {
        // 视觉开启是最常见的情况，先返回可以省掉一次全量遍历。
        if (messages == null || visionEnabled) return messages;

        for (int i = 0; i < messages.length(); i++) {
            JSONObject message = messages.optJSONObject(i);
            if (message == null) continue;
            JSONArray content = message.optJSONArray("content");
            if (content == null) continue;

            JSONArray filtered = new JSONArray();
            for (int j = 0; j < content.length(); j++) {
                Object raw = content.opt(j);
                if (!(raw instanceof JSONObject)) {
                    // 内容数组里理论上只放对象；真遇到别的类型就原样带走，
                    // 不要在这里把它丢掉——丢弃是比类型不符更难查的问题。
                    if (raw != null) filtered.put(raw);
                    continue;
                }
                JSONObject block = (JSONObject) raw;
                if (!BLOCK_IMAGE.equals(block.optString(BLOCK_TYPE, ""))) {
                    filtered.put(block);
                    continue;
                }
                filtered.put(placeholderFor(block));
            }
            message.put("content", filtered);
        }
        return messages;
    }

    /** 用一行说明替换图片块，带上文件名以便用户对得上是哪张图。 */
    private static JSONObject placeholderFor(JSONObject imageBlock) throws Exception {
        String name = imageBlock.optString("name", DEFAULT_IMAGE_NAME).trim();
        if (name.isEmpty()) name = DEFAULT_IMAGE_NAME;
        return new JSONObject()
                .put(BLOCK_TYPE, BLOCK_TEXT)
                .put("text", "[图片未发送：API 设置中的视觉输入已关闭；文件：" + name + "]");
    }
}
