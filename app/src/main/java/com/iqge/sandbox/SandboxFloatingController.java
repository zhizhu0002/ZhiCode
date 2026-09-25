package com.iqge.sandbox;

/**
 * 沙箱 guest 窗口上那条「返回 IQ Code / 看日志 / 停止 guest」控制栏的占位常量。
 *
 * <p><b>这是 Phase 7（Bcore 移植）之前的桩。</b>原版这一个类负责把控制栏注入 guest 窗口，
 * 并支持拖拽、缩放、最小化成悬浮球 —— 依赖 BlackBox 的 guest Activity 接管能力。
 *
 * <p>{@link SandboxAgentBridge} 只用到这个 tag 来「在截图时排除控制栏自己」，
 * 所以这里仅保留常量，取值与原版一致（{@code 0x49515342} = ASCII "IQSB"），
 * 保证 Phase 7 接回真实实现时 tag 语义不变。
 */
public final class SandboxFloatingController {

    /** 控制栏的 View tag，取值与原版一致，勿改。 */
    public static final Integer OVERLAY_TAG = 0x49515342; // "IQSB"

    private SandboxFloatingController() {}
}
