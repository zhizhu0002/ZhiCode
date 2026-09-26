package com.termux.app.zhicode.tools;

import com.termux.app.zhicode.model.SessionConfig;
import com.termux.app.zhicode.model.ToolExecutionResult;

import org.json.JSONObject;

/**
 * 交出计划、等用户批准。
 *
 * <h3>批准不是提权</h3>
 * 用户点「批准」只表示「按这份计划执行」，**不会**改变他自己设的权限模式。
 * 两者混在一起看起来方便（「既然批了计划，那顺手放开写权限吧」），
 * 但那样一来用户就没办法「同意这个方案、但仍要我确认每次改动」了。
 *
 * <h3>{@code plan} 参数的用途</h3>
 * 它是给用户看的那份文本，引擎把它写进计划文件、并渲染成待批准卡片。
 * 真正的流转（进入待批准阶段、记录权限基线）由引擎完成 ——
 * 工具只负责把内容交出去，见 {@link EnterPlanModeTool} 里同样的说明。
 */
public final class ExitPlanModeTool implements ZhiTool {

    @Override public String name() { return "ExitPlanMode"; }

    @Override public String description() {
        return "Present a plan for user approval; approval does not change the user-owned permission mode.";
    }

    @Override public PermissionKind permissionKind() { return PermissionKind.INTERNAL; }

    @Override public JSONObject inputSchema() {
        JSONObject properties = new JSONObject();
        try {
            properties.put("plan", ToolSchemas.string("Concise implementation plan being proposed."));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return ToolSchemas.object(properties);
    }

    @Override public ToolExecutionResult execute(SessionConfig config, JSONObject input) {
        return ToolExecutionResult.ok("Plan approval is managed by the ZhiCode engine.");
    }
}
