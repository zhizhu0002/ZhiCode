package com.termux.app.zhicode.tools;

import com.termux.app.zhicode.model.SessionConfig;
import com.termux.app.zhicode.model.ToolExecutionResult;

import org.json.JSONObject;

/**
 * 进入计划模式的入口。
 *
 * <h3>为什么它什么都不做</h3>
 * 模式切换是**引擎**的事：它要在切换前后处理权限基线、写计划文件、
 * 更新 {@code PlanWorkflowState}，并把状态同步给常驻的通知与界面。
 * 工具在这里能做的只有「返回一句话」—— 真正的切换发生在引擎看到
 * 这个工具被调用之后。
 *
 * <p>那为什么还要有这个工具？因为**模型需要一个显式的动作**来表达
 * 「我打算先计划」。如果没有它，模型只能靠自然语言说「我先规划一下」，
 * 而引擎无法可靠地从文本里判断这件事该不该发生。有了工具调用，
 * 触发点就变成一个可解析的事件。
 *
 * <p>{@link PermissionKind#INTERNAL}：它不改任何外部状态，
 * 也不该因为它而弹一次确认 —— 那会把「进入只读的计划模式」变成一个需要批准的动作。
 */
final class EnterPlanModeTool implements ZhiTool {

    @Override public String name() { return "EnterPlanMode"; }

    @Override public String description() {
        return "Enter a read-only plan overlay before exploring and designing an implementation;"
            + " it cannot change the user-owned permission mode.";
    }

    @Override public PermissionKind permissionKind() { return PermissionKind.INTERNAL; }

    @Override public JSONObject inputSchema() { return ToolSchemas.object(new JSONObject()); }

    @Override public ToolExecutionResult execute(SessionConfig config, JSONObject input) {
        return ToolExecutionResult.ok("Plan mode transition is managed by the ZhiCode engine.");
    }
}
