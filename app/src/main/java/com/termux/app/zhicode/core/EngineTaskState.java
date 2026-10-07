package com.termux.app.zhicode.core;

/** 引擎当前任务阶段；状态值集中定义，避免用多个布尔值表达同一生命周期。 */
public enum EngineTaskState {
    IDLE,
    MODEL,
    TOOL,
    WAITING_PERMISSION,
    WAITING_QUESTION,
    WAITING_PLAN_APPROVAL,
    COMPACTING,
    CANCELLING,
    FAILED;

    public boolean isActive() {
        return this != IDLE && this != FAILED;
    }
}
