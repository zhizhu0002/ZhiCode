package com.termux.app.zhicode.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * 守住「面向模型的文本」一字未变。
 *
 * <h3>为什么这需要一个测试</h3>
 * {@code ContextCompactor} 产出的三样东西都会直接送给模型：摘要请求的提示词、
 * 压缩后的上下文包裹格式、以及被缩减过的历史。它们不像界面文案那样有人看，
 * 改坏了不会报错、不会崩 —— 只会让摘要质量悄悄变差，或者让模型收到一份它不认识的上下文。
 *
 * <p>基线文本 {@code resources/compaction-prompt-baseline.txt} 是重写<b>之前</b>的实现
 * 导出的（用一次性探针把它的输出原样 dump 出来）。所以这个测试的含义是：
 * 「重写实现，但不改行为」，而且这条断言是可复核的 —— 基线文件里就是那些句子本身。
 */
public final class ContextCompactionTextTest {

    @Test
    public void producesTheSameModelFacingText() throws Exception {
        String expected = baseline();
        String actual = currentDump();
        assertEquals("压缩相关的模型文本必须与基线逐字相同", expected, actual);
    }

    private static String baseline() throws Exception {
        try (InputStream input = ContextCompactionTextTest.class.getClassLoader()
                .getResourceAsStream("compaction-prompt-baseline.txt")) {
            assertNotNull("缺少基线文件 compaction-prompt-baseline.txt", input);
            try (Scanner scanner = new Scanner(input, StandardCharsets.UTF_8)) {
                return scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
            }
        }
    }

    /** 与探针当初导出的内容同构：同样的输入、同样的顺序。 */
    private static String currentDump() throws Exception {
        JSONArray source = sample();
        ContextCompactor.Plan prefixPlan = ContextCompactor.createPlan(source, 3);
        ContextCompactor.Plan wholePlan = ContextCompactor.createPlan(source, source.length());

        StringBuilder out = new StringBuilder();
        out.append("### prompt(custom=empty, recent=true)\n");
        out.append(promptOf(ContextCompactor.buildSummaryMessages(prefixPlan, "", 0))).append('\n');
        out.append("### prompt(custom=empty, recent=false)\n");
        out.append(promptOf(ContextCompactor.buildSummaryMessages(wholePlan, "", 0))).append('\n');
        out.append("### prompt(custom=中文重点, recent=true)\n");
        out.append(promptOf(ContextCompactor.buildSummaryMessages(prefixPlan, "重点保留测试结果与文件路径", 0)))
                .append('\n');
        out.append("### compactedMessages\n");
        JSONArray compacted = ContextCompactor.buildCompactedMessages(prefixPlan, "SUMMARY-BODY");
        for (int i = 0; i < compacted.length(); i++) {
            out.append(compacted.optJSONObject(i).toString()).append('\n');
        }
        out.append("### summaryMessages(attempt=0)\n");
        appendMessages(out, ContextCompactor.buildSummaryMessages(prefixPlan, "", 0));
        out.append("### summaryMessages(attempt=2)\n");
        appendMessages(out, ContextCompactor.buildSummaryMessages(prefixPlan, "X", 2));
        return out.toString();
    }

    /** 摘要提示词是 buildSummaryMessages 的最后一条消息。 */
    private static String promptOf(JSONArray messages) throws Exception {
        JSONObject last = messages.optJSONObject(messages.length() - 1);
        return last.optJSONArray("content").optJSONObject(0).optString("text", "");
    }

    private static void appendMessages(StringBuilder out, JSONArray messages) throws Exception {
        for (int i = 0; i < messages.length(); i++) {
            out.append(messages.optJSONObject(i).toString()).append('\n');
        }
    }

    /** 输入要覆盖每一种块类型：text / thinking / tool_use / tool_result / image。 */
    private static JSONArray sample() throws Exception {
        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "user").put("content", new JSONArray()
                .put(new JSONObject().put("type", "text").put("text", "第一条人类发言"))));
        messages.put(new JSONObject().put("role", "assistant").put("content", new JSONArray()
                .put(new JSONObject().put("type", "text").put("text", "assistant 正文"))
                .put(new JSONObject().put("type", "thinking").put("thinking", "思考内容"))
                .put(new JSONObject().put("type", "tool_use").put("id", "call_1")
                        .put("name", "Read").put("input", new JSONObject().put("file_path", "/tmp/a")))));
        messages.put(new JSONObject().put("role", "user").put("content", new JSONArray()
                .put(new JSONObject().put("type", "tool_result").put("tool_use_id", "call_1")
                        .put("content", "工具输出"))));
        messages.put(new JSONObject().put("role", "assistant").put("content", new JSONArray()
                .put(new JSONObject().put("type", "text").put("text", "第二轮回复"))
                .put(new JSONObject().put("type", "image").put("source", "x"))));
        messages.put(new JSONObject().put("role", "user").put("content", new JSONArray()
                .put(new JSONObject().put("type", "text").put("text", "第二条人类发言"))));
        messages.put(new JSONObject().put("role", "assistant").put("content", new JSONArray()
                .put(new JSONObject().put("type", "text").put("text", "第三轮回复"))));
        return messages;
    }
}
