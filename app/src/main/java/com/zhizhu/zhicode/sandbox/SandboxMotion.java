package com.zhizhu.zhicode.sandbox;

import android.content.Context;
import android.provider.Settings;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

/**
 * Guest 进程里的**纯 View 动画**小工具。
 *
 * <h3>为什么需要它，而不是直接用界面的动画层</h3>
 * 这个类只被 {@link SandboxFloatingController} 使用 —— 那个悬浮调试条是往
 * **guest 的 Window 里**注入的原生 `View` 树，不是 Compose 树。
 * 界面层的 {@code compose/ui/Animations.kt} 里的 {@code ZhiMotion} 全是
 * {@code @Composable} / {@code Animatable} 的世界，拿不到「对一个 View 做一次
 * 淡入位移」这件事；在 guest 里也不该为了一条悬浮条去起一个 Compose 组合。
 *
 * <h3>为什么不直接搬原版的 {@code com.iqge.UiMotion}</h3>
 * 那个类有 500+ 行，绝大多数是主界面用的（内容更新、列表入场、卡片按压…），
 * 沙盒只用得到下面三个方法。搬整套等于把一个 UI 框架塞进 guest 侧，
 * 而 guest 侧要的是**最小、最不可能崩**的一段代码 —— 它跑在别人的应用进程里。
 *
 * <p>曲线与时长沿用 Miuix 的强调档（cubic-bezier(0.2, 0, 0, 1)），
 * 这样悬浮条的手感与主界面一致，而不是另造一套。
 */
final class SandboxMotion {

    private SandboxMotion() {}

    /** Miuix 强调曲线：起步快、收尾稳。 */
    private static final Interpolator EMPHASIZED =
            new PathInterpolator(0.2f, 0f, 0f, 1f);

    /** API 21 以下没有 PathInterpolator，退回系统曲线。 */
    private static final Interpolator FALLBACK = new AccelerateDecelerateInterpolator();

    static Interpolator emphasized() {
        return EMPHASIZED != null ? EMPHASIZED : FALLBACK;
    }

    /**
     * 按系统「动画时长缩放」折算时长。
     *
     * <p>用户在开发者选项里关掉动画（scale = 0）时，这里必须返回 0 ——
     * 否则一条「本该立刻出现」的悬浮条会莫名其妙地卡 190ms，
     * 而在那种设置下所有系统动画都是瞬时的。
     */
    static long durationFor(Context context, long normal) {
        if (context == null) return normal;
        try {
            float scale = Settings.Global.getFloat(
                    context.getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
            if (scale <= 0f) return 0L;
            return (long) Math.max(0L, Math.min(10f * normal, normal * scale));
        } catch (Throwable ignored) {
            return normal;
        }
    }

    /**
     * 从 (fromDx, fromDy) 偏移处淡入并位移到 0。
     *
     * <p>调用方负责先把 view 设为 VISIBLE —— 这里不碰可见性，
     * 因为「该不该显示」是业务判断，不是动画的事。
     */
    static void fadeSlideIn(View view, float fromDx, float fromDy, long normal) {
        if (view == null) return;
        Context context = view.getContext();
        long duration = durationFor(context, normal);
        if (duration <= 0L) {
            view.setAlpha(1f);
            view.setTranslationX(0f);
            view.setTranslationY(0f);
            return;
        }
        view.setAlpha(0f);
        view.setTranslationX(fromDx);
        view.setTranslationY(fromDy);
        view.animate()
                .alpha(1f)
                .translationX(0f)
                .translationY(0f)
                .setDuration(duration)
                .setInterpolator(emphasized())
                .start();
    }
}
