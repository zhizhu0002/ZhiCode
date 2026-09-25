package com.zhizhu.zhicode.compose;

/**
 * 桥接点：从原版直搬过来的 Java 组件（保活服务、悬浮窗服务）需要引用 Compose 侧的入口。
 *
 * <p>原版里这些服务与 {@code MainActivity} 同包，直接写 {@code MainActivity.class} 就能编译。
 * 本工程的入口在 {@code com.zhizhu.zhicode.compose}，且 Compose 版没有原版那个
 * {@code static reopenWorkspaceOverlay()}，所以这里放一层极薄的间接引用，
 * 避免为了编译去改动那些组件的实现细节。
 *
 * <p>所有引用都是懒的、可选的：Compose 侧还没注册时返回 {@code false} / 兜底 Activity，
 * 不会 NPE。
 */
public final class HostRefs {

    /** 悬浮窗收回后返回主界面的回调，由 MainActivity 注册。 */
    public interface OverlayReopener {
        boolean reopen();
    }

    private static volatile OverlayReopener overlayReopener;
    private static volatile Class<?> mainActivity = MainActivity.class;

    /** 入口 Activity，供通知点击、悬浮窗跳转使用。 */
    public static Class<?> mainActivity() {
        return mainActivity;
    }

    /** 允许替换入口（测试或切换到别的承载体时用）。 */
    public static void setMainActivity(Class<?> activity) {
        if (activity != null) mainActivity = activity;
    }

    public static void setOverlayReopener(OverlayReopener reopener) {
        overlayReopener = reopener;
    }

    /** 尝试把工作区从悬浮窗收回主界面；没人注册时返回 false，调用方自行回退。 */
    public static boolean reopenOverlay() {
        OverlayReopener reopener = overlayReopener;
        return reopener != null && reopener.reopen();
    }

    private HostRefs() {}
}
