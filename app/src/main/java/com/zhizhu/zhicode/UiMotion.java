package com.zhizhu.zhicode;

import android.animation.LayoutTransition;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.animation.PathInterpolator;
import android.widget.TextView;

/**
 * 原生工作区的动效语言。
 *
 * <h3>只动合成器友好的属性</h3>
 * 只有 alpha、translation、scale 会被动画化。它们由渲染线程直接合成，不需要重新布局 ——
 * 而布局动画会与终端里的高频输出互相挤，表现是「一边刷日志一边滑抽屉就卡住」。
 * 终端画面本身永远不做动画。
 *
 * <h3>「要不要动」是一个每帧都在问的问题</h3>
 * 答案取决于两件事：系统里动画总开关是否被关掉（无障碍设置、开发者选项），
 * 以及是否处于省电模式。两者都要读系统设置，而调用点可能在一帧里被问很多次，
 * 所以结果缓存一秒：花一秒的延迟去换掉每帧一次的系统查询。
 *
 * <p>关掉动画时不是「不做任何事」，而是直接落到终态：异步回调的调用方
 * （例如抽屉关闭后的收尾）依赖动画结束来继续，什么都不做会让它永远不执行。
 *
 * <h3>为什么进场动画都长得一样</h3>
 * 十几个「入场」只有微小的差别（从哪个方向偏移多少、初始透明度、要不要单独一层）。
 * 与其写十几份同样的四行，不如把它们表达成一个起点：见 {@link From} 与 {@link #enter}。
 * 参数集中在调用处一列排开，调整某个动效时不必在十几处之间对照。
 */
public final class UiMotion {

    /** 常规曲线：起步快、收尾平稳，用于大多数进场。 */
    private static final TimeInterpolator STANDARD = new PathInterpolator(.20f, 0f, 0f, 1f);
    /** 强调曲线：更快起步、更长收尾，用于「整块东西出现/消失」。 */
    private static final TimeInterpolator EMPHASIZED = new PathInterpolator(.16f, .84f, .22f, 1f);

    /** 缓存的时效。见类注释：这是「每秒最多查一次系统设置」的那一秒。 */
    private static final long MOTION_STATE_TTL_MS = 1_000L;

    /** 动画时长的下限与缩放。太短的动画看起来像闪烁，太长会挡住连续操作。 */
    private static final long MIN_DURATION_MS = 70L;
    private static final float DURATION_SCALE = .78f;
    /** 省电模式下的额外缩短比例。 */
    private static final float POWER_SAVE_SCALE = .72f;

    /** 长按反馈（ACTION_DOWN）与回弹（UP/CANCEL）的基准时长。 */
    private static final long PRESS_DOWN_MS = 65L;
    private static final long PRESS_UP_MS = 120L;

    private static volatile long motionStateCheckedAt;
    private static volatile boolean animationsEnabled = true;
    private static volatile boolean powerSaveMode;

    private UiMotion() {}

    // ------------------------------------------------------------ 全局状态

    /** 当前是否应当播放动画。内部每次都会刷新（带一秒缓存）。 */
    public static boolean enabled(Context context) {
        refreshMotionState(context);
        return animationsEnabled;
    }

    /**
     * 刷新两个系统状态。
     *
     * <p>双重检查加锁：这里读的是两个 volatile 字段，而调用点来自多个线程
     * （主线程动画、后台任务的进度回调都会问）。只加缓存不测两次的话，
     * 同时进入的线程会各自查一遍系统设置。
     */
    private static void refreshMotionState(Context context) {
        if (context == null) return;
        long now = SystemClock.uptimeMillis();
        if (now - motionStateCheckedAt < MOTION_STATE_TTL_MS) return;
        synchronized (UiMotion.class) {
            if (now - motionStateCheckedAt < MOTION_STATE_TTL_MS) return;
            try {
                animationsEnabled = animatorsAllowed(context);
                powerSaveMode = isPowerSaveMode(context);
            } catch (Throwable unreadable) {
                // 读不到系统设置时按「可以动」处理：动画只是观感，不该因为一次读取失败
                // 让整个界面看起来像卡死了。
                animationsEnabled = true;
                powerSaveMode = false;
            }
            motionStateCheckedAt = now;
        }
    }

    /** 系统里动画是否被允许。API 26 之前没有 {@code areAnimatorsEnabled}，只能看时长比例。 */
    private static boolean animatorsAllowed(Context context) {
        boolean platformEnabled = Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled();
        float scale = Settings.Global.getFloat(context.getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        return platformEnabled && scale > 0f;
    }

    private static boolean isPowerSaveMode(Context context) {
        PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return power != null && power.isPowerSaveMode();
    }

    /**
     * 把请求的时长换算成实际时长。
     *
     * <p>返回 0 表示「不要动画」：调用方据此走终态分支，而不是启动一个零时长动画
     * （零时长动画仍会走一遍异步回调，但会让中间状态闪一下）。
     */
    private static long duration(View view, long requestedMs) {
        refreshMotionState(view == null ? null : view.getContext());
        if (!animationsEnabled) return 0L;
        long tuned = Math.max(MIN_DURATION_MS, (long) (requestedMs * DURATION_SCALE));
        return powerSaveMode ? Math.max(MIN_DURATION_MS, (long) (tuned * POWER_SAVE_SCALE)) : tuned;
    }

    private static float dp(View view, float value) {
        return value * view.getResources().getDisplayMetrics().density;
    }

    /** 把视图的视觉属性归位到「静止态」。关掉动画时的终态就是这个。 */
    private static void rest(View view) {
        if (view == null) return;
        view.animate().cancel();
        view.setAlpha(1f);
        view.setTranslationX(0f);
        view.setTranslationY(0f);
        view.setScaleX(1f);
        view.setScaleY(1f);
    }

    // ------------------------------------------------------------ 进场

    /**
     * 入场动画的起点。
     *
     * <p>终点永远是「静止态」（alpha 1、无偏移、无缩放），所以只需要描述从哪里来。
     * 链式写法是为了让调用处读起来像一句配置，而不是一串 setter。
     */
    private static final class From {
        float alpha = 1f;
        float scale = 1f;
        float translateXdp;
        float translateYdp;
        TimeInterpolator interpolator = STANDARD;
        long startDelayMs;
        boolean ownLayer;

        From alpha(float value) {
            alpha = value;
            return this;
        }

        From scale(float value) {
            scale = value;
            return this;
        }

        From up(float dp) {
            translateYdp = dp;
            return this;
        }

        From sideways(float dp) {
            translateXdp = dp;
            return this;
        }

        From emphasized() {
            interpolator = EMPHASIZED;
            return this;
        }

        From delay(long ms) {
            startDelayMs = ms;
            return this;
        }

        /**
         * 单独占一层。
         *
         * <p>用在「会盖住内容」的动画上（对话框、抽屉、工具卡片）：不单独一层时，
         * 每次属性变化都可能触发父容器重绘，而父容器里可能有终端画面。
         */
        From layer() {
            ownLayer = true;
            return this;
        }
    }

    private static From from() {
        return new From();
    }

    /**
     * 播一次「从某个起点回到静止态」的动画。
     *
     * <p>关掉动画时直接落到静止态 —— 见类注释：不能什么都不做，
     * 因为有些调用方依赖动画结束后它们自己再收尾。
     */
    private static void enter(View view, long requestedMs, From start) {
        if (view == null) return;
        long duration = duration(view, requestedMs);
        if (duration == 0L) {
            rest(view);
            return;
        }
        // 先取消旧动画再设起点：反过来的话旧动画的最后一次属性写入会覆盖刚设的起点，
        // 表现是「偶尔从一半的位置飞进来」。
        view.animate().cancel();
        view.setAlpha(start.alpha);
        view.setScaleX(start.scale);
        view.setScaleY(start.scale);
        view.setTranslationX(dp(view, start.translateXdp));
        view.setTranslationY(dp(view, start.translateYdp));

        ViewPropertyAnimator animator = view.animate()
                .alpha(1f).scaleX(1f).scaleY(1f).translationX(0f).translationY(0f)
                .setInterpolator(start.interpolator)
                .setDuration(duration);
        if (start.startDelayMs > 0) animator.setStartDelay(start.startDelayMs);
        if (start.ownLayer) animator.withLayer();
        animator.start();
    }

    /** 整块界面首次出现。缩放幅度很小 —— 再大就会在终端文字上看得出模糊。 */
    public static void pageIn(View view) {
        enter(view, 150, from().alpha(0f).up(3f));
    }

    /** 整块界面重绘（换主题、换语言）。比普通进场更亮一点，用来「说明界面换了」。 */
    public static void themeChanged(View view) {
        enter(view, 330, from().alpha(.38f).scale(.988f).emphasized().layer());
    }

    /** 侧栏/设置弹层出现。缩放加下移，像从下面浮上来。 */
    public static void dialogIn(View view) {
        enter(view, 270, from().alpha(0f).scale(.955f).up(14f).emphasized().layer());
    }

    /** 一条内容更新了。强调版给「这次是你要的结果」，普通版给后台刷新。 */
    public static void contentUpdated(View view, boolean emphasized) {
        enter(view, emphasized ? 210 : 120,
                emphasized ? from().alpha(.76f).up(2f) : from().alpha(.93f).up(1f));
    }

    // ------------------------------------------------------------ 抽屉

    /**
     * 打开抽屉。
     *
     * <p>两层一起动，而且遮罩比抽屉快一点结束（{@code duration - 35}）：
     * 遮罩先到位，抽屉再落下来，看起来才有「推进去」的层次。
     */
    public static void drawerIn(View scrim, View drawer, float widthDp) {
        if (scrim == null || drawer == null) return;
        long duration = duration(drawer, 260);
        scrim.animate().cancel();
        drawer.animate().cancel();
        scrim.setVisibility(View.VISIBLE);
        drawer.setVisibility(View.VISIBLE);
        if (duration == 0L) {
            scrim.setAlpha(1f);
            rest(drawer);
            return;
        }
        scrim.setAlpha(0f);
        drawer.setAlpha(.82f);
        drawer.setTranslationX(dp(drawer, -Math.abs(widthDp)));
        drawer.animate().alpha(1f).translationX(0f)
                .setInterpolator(EMPHASIZED).setDuration(duration).withLayer().start();
        scrim.animate().alpha(1f).setInterpolator(STANDARD).setDuration(duration - 35L).start();
    }

    /**
     * 关闭抽屉，然后在动画真正结束后收尾。
     *
     * <p>收尾（隐藏两层、把 alpha 复位）挂在抽屉动画的 {@code withEndAction} 上，
     * 而不是调用方自己起个定时器：定时器与实际动画时长会随时间脱节。
     */
    public static void drawerOut(View scrim, View drawer, float widthDp, Runnable onFinished) {
        if (scrim == null || drawer == null) {
            if (onFinished != null) onFinished.run();
            return;
        }
        long duration = duration(drawer, 205);
        scrim.animate().cancel();
        drawer.animate().cancel();
        if (duration == 0L) {
            hideDrawer(scrim, drawer, onFinished);
            return;
        }
        scrim.animate().alpha(0f).setInterpolator(STANDARD).setDuration(duration - 25L).start();
        drawer.animate().alpha(.88f).translationX(dp(drawer, -Math.abs(widthDp)))
                .setInterpolator(STANDARD).setDuration(duration).withLayer()
                .withEndAction(() -> hideDrawer(scrim, drawer, onFinished))
                .start();
    }

    private static void hideDrawer(View scrim, View drawer, Runnable onFinished) {
        scrim.setVisibility(View.GONE);
        drawer.setVisibility(View.GONE);
        drawer.setAlpha(1f);
        if (onFinished != null) onFinished.run();
    }

    // ------------------------------------------------------------ 列表

    /**
     * 让最近加入的几个子项依次进场。
     *
     * <p>只处理末尾的 {@code maxChildren} 个：一次刷新可能新增几十条，
     * 让全部一起动既是白费力气也看不出层次。间隔上限 150ms，
     * 再长会让最后一项迟迟不出现。
     */
    public static void staggerChildren(ViewGroup parent, int maxChildren) {
        if (parent == null) return;
        int count = parent.getChildCount();
        int first = Math.max(0, count - Math.max(1, maxChildren));
        for (int i = first; i < count; i++) {
            long stagger = Math.min(150L, (long) (i - first) * 22L);
            enter(parent.getChildAt(i), 220, from().alpha(0f).up(7f).scale(.992f).delay(stagger).layer());
        }
    }

    // ------------------------------------------------------------ 交互反馈

    /**
     * 给整棵视图树加上统一的按压反馈。
     *
     * <p>它<b>不替换</b>已有的点击监听：只加一个触摸监听并且在最后返回 false，
     * 让事件继续往下走。已经处理过的视图不会重复绑定（弱引用表记录）。
     */
    public static void bindInteractive(View root) {
        if (root == null) return;
        if (root.hasOnClickListeners() && !BOUND.contains(root)) {
            BOUND.add(root);
            root.setOnTouchListener(UiMotion::handleTouch);
        }
        if (!(root instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) bindInteractive(group.getChildAt(i));
    }

    /** 按压反馈的触摸处理。返回值恒为 false，见 {@link #bindInteractive}。 */
    private static boolean handleTouch(View view, MotionEvent event) {
        if (!view.isEnabled()) return false;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) pressDown(view);
        else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) releasePress(view);
        return false;
    }

    private static void pressDown(View view) {
        long duration = duration(view, PRESS_DOWN_MS);
        if (duration == 0L) return;
        view.animate().cancel();
        view.animate().scaleX(.985f).scaleY(.985f).alpha(.86f)
                .setInterpolator(STANDARD).setDuration(duration).start();
    }

    private static void releasePress(View view) {
        long duration = duration(view, PRESS_UP_MS);
        if (duration == 0L) {
            rest(view);
            return;
        }
        view.animate().cancel();
        view.animate().scaleX(1f).scaleY(1f).alpha(1f)
                .setInterpolator(EMPHASIZED).setDuration(duration).start();
    }

    // ------------------------------------------------------------ 布局变化

    /**
     * 打开低频的增删过渡。
     *
     * <p>{@code CHANGE_*} 三种一律关掉：它们在每次尺寸变化时都会重新计算整棵子树，
     * 而这里的容器里可能有终端视图。只保留 APPEARING / DISAPPEARING（真正的增删）。
     */
    public static void enableLayoutChanges(ViewGroup group) {
        if (group == null || !enabled(group.getContext())) return;
        LayoutTransition transition = new LayoutTransition();
        transition.disableTransitionType(LayoutTransition.CHANGING);
        transition.disableTransitionType(LayoutTransition.CHANGE_APPEARING);
        transition.disableTransitionType(LayoutTransition.CHANGE_DISAPPEARING);
        transition.setDuration(LayoutTransition.APPEARING, 145L);
        transition.setDuration(LayoutTransition.DISAPPEARING, 105L);
        transition.setStartDelay(LayoutTransition.APPEARING, 0L);
        transition.setStartDelay(LayoutTransition.DISAPPEARING, 0L);
        transition.setInterpolator(LayoutTransition.APPEARING, STANDARD);
        transition.setInterpolator(LayoutTransition.DISAPPEARING, STANDARD);
        group.setLayoutTransition(transition);
    }

    // ------------------------------------------------------------ 文字

    /**
     * 换一段文字，中间带一个淡出淡入。
     *
     * <p>内容没变就什么都不做：设置同样的文本也会触发一次重排，而这个词条的
     * 更新源往往是「每秒刷一次的状态」。
     */
    public static void setTextCrossfade(TextView view, CharSequence value) {
        if (view == null) return;
        CharSequence next = value == null ? "" : value;
        if (String.valueOf(view.getText()).contentEquals(next)) return;

        long duration = duration(view, 170);
        if (duration == 0L) {
            view.setText(next);
            return;
        }
        view.animate().cancel();
        view.animate().alpha(.18f).translationY(dp(view, -2f)).setDuration(duration / 2L)
                .setInterpolator(STANDARD).withEndAction(() -> {
                    // 换文字放在最暗的那一瞬间。在动画开始时换会让人看到旧字的位移。
                    view.setText(next);
                    view.setTranslationY(dp(view, 2f));
                    view.animate().alpha(1f).translationY(0f).setDuration(duration / 2L + 20L)
                            .setInterpolator(STANDARD).start();
                }).start();
    }

    /**
     * 记录「这个视图已经绑过按压反馈」。
     *
     * <p>弱引用集合：界面重建会造出全新的视图，用强引用留着它们等于让整棵旧视图树
     * 无法回收。
     */
    private static final java.util.Set<View> BOUND =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<View, Boolean>());
}
