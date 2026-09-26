package com.termux.app.zhicode.tools;

import com.termux.app.zhicode.model.SessionConfig;
import com.termux.app.zhicode.model.ToolExecutionResult;

import org.json.JSONObject;

/**
 * 等一会儿再继续。
 *
 * <h3>为什么不给「等到某个条件成立」</h3>
 * 那会变成一个带超时与轮询间隔的小调度器：参数一多，模型就会写出
 * 等待时间过长或者条件永远不成立的那种调用，而两者在结果上都表现为「卡住了」。
 * 只给秒数，让模型自己决定等多久、以及下一轮再检查什么。
 *
 * <h3>上限 60 秒</h3>
 * 更长的等待会一直占着一个 agent 回合，用户看不到任何进展、只能取消。
 * 需要等更久的场景（长时间构建）应该交给 Bash 的 timeout 参数 ——
 * 那条路径有实时输出，用户能看到它在动。
 *
 * <h3>中断要能穿透</h3>
 * {@link Thread#sleep} 抛 {@link InterruptedException} 时不吞掉：
 * 用户点取消就是靠中断这个线程来生效的。吞掉它会让「取消」要等到睡完为止。
 */
final class SleepTool implements ZhiTool {

    private static final int MAX_SECONDS = 60;
    private static final long MILLIS_PER_SECOND = 1000L;

    @Override public String name() { return "Sleep"; }

    @Override public String description() {
        return "Wait briefly before continuing, useful for polling background processes.";
    }

    @Override public PermissionKind permissionKind() { return PermissionKind.INTERNAL; }

    @Override public JSONObject inputSchema() {
        JSONObject properties = new JSONObject();
        try {
            properties.put("seconds", ToolSchemas.integer("Seconds to wait (maximum 60).", 0));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return ToolSchemas.object(properties, "seconds");
    }

    @Override public ToolExecutionResult execute(SessionConfig config, JSONObject input) throws Exception {
        int seconds = Math.max(0, Math.min(MAX_SECONDS, input.optInt("seconds", 1)));
        Thread.sleep(seconds * MILLIS_PER_SECOND);
        return ToolExecutionResult.ok("Waited " + seconds + " second" + (seconds == 1 ? "" : "s"));
    }
}
