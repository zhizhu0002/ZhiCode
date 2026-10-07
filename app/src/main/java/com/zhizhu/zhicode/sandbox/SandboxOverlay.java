package com.zhizhu.zhicode.sandbox;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.zhizhu.zhicode.compose.MainActivity;

/**
 * 注入到每个虚拟 Activity 上的沙箱控制栏。
 *
 * <p><b>为什么是注入而不是悬浮窗。</b>它直接 {@code addContentView} 到 guest 的 decor 上，
 * 属于该 Activity 的视图树，因此<b>不需要 SYSTEM_ALERT_WINDOW</b>，也不会在 guest 被系统
 * 销毁后残留成孤儿窗口。代价是每个 resumed 的虚拟 Activity 都要注入一次 ——
 * 由 {@link ZhiSandbox} 的生命周期回调负责，并且用 {@link #TAG} 去重，
 * 避免同一个 decor 上叠出多层。
 *
 * <p>收起时只是一个圆形气泡；点一下展开成「日志 / 返回 / 停止」三个动作：
 * <ul>
 *   <li><b>日志</b>：弹出可复制、可刷新的日志面板（内容来自 {@link SandboxConsole#snapshot}）</li>
 *   <li><b>返回</b>：回到 ZhiCode 界面</li>
 *   <li><b>停止</b>：停掉当前 guest 并收回控制栏</li>
 * </ul>
 * 拖动只移动整个面板；位移小于 {@link #TAP_SLOP_DP} 视为点击。
 */
final class SandboxOverlay {

    /** 视图标记。用于在 decor 上查找与去重（同一个 decor 只允许一层）。 */
    public static final Integer OVERLAY_TAG = 0x5A484942; // "ZHIB"


    /** 位移小于这个值（dp）视为点击而非拖动。 */
    private static final int TAP_SLOP_DP = 10;
    private static final int CORNER_RADIUS_DP = 16;
    private static final int PANEL_PADDING_DP = 6;
    private static final int PANEL_PADDING_VERTICAL_DP = 4;
    private static final int BUBBLE_WIDTH_DP = 46;
    private static final int ACTION_WIDTH_DP = 48;
    private static final int HEIGHT_DP = 40;
    private static final int START_X_DP = 10;
    private static final int START_Y_DP = 36;

    private SandboxOverlay() {}

    /** 在 {@code activity} 上挂控制栏。幂等：已挂过则直接返回。 */
    public static void attach(Activity activity, String guestPackage) {
        if (activity == null || activity.isFinishing()) return;
        SandboxConsole.event("准备添加沙箱控制栏: " + guestPackage + " / " + activity.getClass().getName());
        activity.runOnUiThread(() -> {
            View decor = activity.getWindow().getDecorView();
            if (!(decor instanceof ViewGroup)) return;
            ViewGroup root = (ViewGroup) decor;
            if (root.findViewWithTag(OVERLAY_TAG) != null) return;

            View panel = buildPanel(activity, guestPackage);
            activity.addContentView(panel, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            panel.setX(dp(activity, START_X_DP));
            panel.setY(dp(activity, START_Y_DP));
        });
    }

    /** 摘掉控制栏。安全：未挂载时什么都不做。 */
    static void detach(Activity activity) {
        if (activity == null) return;
        activity.runOnUiThread(() -> {
            View decor = activity.getWindow().getDecorView();
            if (!(decor instanceof ViewGroup)) return;
            View panel = ((ViewGroup) decor).findViewWithTag(OVERLAY_TAG);
            if (panel == null) return;
            if (panel.getParent() instanceof ViewGroup) {
                ((ViewGroup) panel.getParent()).removeView(panel);
            }
        });
    }

    // ------------------------------------------------------------------ 面板构建

    private static View buildPanel(Activity activity, String guestPackage) {
        SandboxPalette palette = paletteOf(activity);

        LinearLayout panel = new LinearLayout(activity);
        panel.setTag(OVERLAY_TAG);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setPadding(dp(activity, PANEL_PADDING_DP), dp(activity, PANEL_PADDING_VERTICAL_DP),
                dp(activity, PANEL_PADDING_DP), dp(activity, PANEL_PADDING_VERTICAL_DP));
        panel.setBackground(rounded(palette.surface, CORNER_RADIUS_DP));
        panel.setElevation(dp(activity, 14));

        TextView bubble = action(activity, "ZhiCode", palette.accent, palette);
        TextView log = action(activity, "日志", palette.text, palette);
        TextView back = action(activity, "返回", palette.text, palette);
        TextView stop = action(activity, "停止", palette.danger, palette);

        panel.addView(bubble, new LinearLayout.LayoutParams(dp(activity, BUBBLE_WIDTH_DP), dp(activity, HEIGHT_DP)));
        panel.addView(log, new LinearLayout.LayoutParams(dp(activity, ACTION_WIDTH_DP), dp(activity, HEIGHT_DP)));
        panel.addView(back, new LinearLayout.LayoutParams(dp(activity, ACTION_WIDTH_DP), dp(activity, HEIGHT_DP)));
        panel.addView(stop, new LinearLayout.LayoutParams(dp(activity, ACTION_WIDTH_DP), dp(activity, HEIGHT_DP)));

        // 展开态在首次渲染时保持收起
        log.setVisibility(View.GONE);
        back.setVisibility(View.GONE);
        stop.setVisibility(View.GONE);

        installDrag(activity, panel, bubble, log, back, stop);
        log.setOnClickListener(v -> showLog(activity));
        back.setOnClickListener(v -> returnToHost(activity));
        stop.setOnClickListener(v -> {
            new Thread(() -> {
                try {
                    ZhiSandbox.stop(guestPackage);
                    SandboxKeeper.stop(activity);
                } catch (Throwable error) {
                    SandboxConsole.event("停止 guest 失败: " + error);
                }
                activity.runOnUiThread(() -> {
                    detach(activity);
                    returnToHost(activity);
                });
            }, "zhi-sandbox-stop").start();
        });
        return panel;
    }

    /**
     * 拖动只在气泡上生效 —— 三个动作按钮仍走各自的点击。
     *
     * <p>移动量按「手指原始坐标的增量」算，而不是 getX/getY：
     * 后者是相对父容器的坐标，会和面板自身的 translation 互相干扰，拖起来会漂。
     */
    private static void installDrag(Activity activity, View panel, View handle,
                                    View log, View back, View stop) {
        final DragState state = new DragState();
        handle.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    state.begin(event.getRawX(), event.getRawY(), panel);
                    // 阻止 ScrollView 之类的祖先抢走手势
                    view.getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    state.move(event.getRawX(), event.getRawY());
                    state.schedule(panel);
                    return true;
                case MotionEvent.ACTION_UP:
                    state.cancelPending(panel);
                    state.apply(panel);
                    if (state.isTap(event.getRawX(), event.getRawY(), dp(activity, TAP_SLOP_DP))) {
                        toggleExpanded(log, back, stop);
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    state.cancelPending(panel);
                    return true;
                default:
                    return false;
            }
        });
    }

    private static void toggleExpanded(View log, View back, View stop) {
        boolean expand = log.getVisibility() != View.VISIBLE;
        int target = expand ? View.VISIBLE : View.GONE;
        log.setVisibility(target);
        back.setVisibility(target);
        stop.setVisibility(target);
    }

    private static void returnToHost(Activity activity) {
        Intent intent = new Intent(activity, MainActivity.class).addFlags(
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("zhisandbox_return", true);
        activity.startActivity(intent);
    }

    // ------------------------------------------------------------------ 日志面板

    /**
     * 日志面板。
     *
     * <p><b>三处颜色必须我们自己定，不能留给主题。</b>真机症状（截图）：面板是一片
     * 纯白，看着像没有内容。根因不是日志空，而是颜色：正文原来写死
     * {@code Color.WHITE}，而 {@code AlertDialog} 的底色跟着<b>当前 Activity 的主题</b>走
     * —— 这里是 guest 的 Activity（截图里是浅色的 PHIRA）→ 白底白字。
     * 日志一直好好地写在宿主的目录里。
     *
     * <p>所以：正文色、窗口底色、标题与三个按钮全部显式上色（见 {@link #tintDialog}）。
     * 少任何一处都会退回“guest 主题说了算”，而 guest 主题是我们控制不了的。
     */
    private static void showLog(Activity activity) {
        SandboxPalette palette = paletteOf(activity);

        TextView body = new TextView(activity);
        body.setText("正在读取日志…");
        body.setTextColor(palette.text);
        body.setTextSize(10);
        body.setTypeface(Typeface.MONOSPACE);
        body.setTextIsSelectable(true);
        body.setGravity(Gravity.TOP);

        int pad = dp(activity, 8);
        ScrollView scroll = new ScrollView(activity);
        scroll.setPadding(pad, dp(activity, 4), pad, dp(activity, 4));
        scroll.addView(body);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("ZhiCode 沙箱日志")
                .setView(scroll)
                .setNegativeButton("关闭", null)
                .setNeutralButton("复制全部", null)
                .setPositiveButton("刷新", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            tintDialog(dialog, palette);
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                ClipboardManager clipboard =
                        (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("ZhiCode 沙箱日志", body.getText()));
                }
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> loadLog(activity, body));
        });
        dialog.show();
        loadLog(activity, body);
    }

    /**
     * 把对话框的底色、标题与三个按钮改成我们自己的色。
     *
     * <p>只在 {@code show()} 之后调（`getButton` 在未 show 时会抛）。
     */
    private static void tintDialog(AlertDialog dialog, SandboxPalette palette) {
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(rounded(palette.surface, CORNER_RADIUS_DP));
        }
        TextView title = dialog.findViewById(android.R.id.title);
        if (title != null) {
            title.setTextColor(palette.text);
        }
        int[] which = {AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEUTRAL, AlertDialog.BUTTON_NEGATIVE};
        for (int button : which) {
            Button view = dialog.getButton(button);
            if (view != null) {
                view.setTextColor(palette.accent);
                // MIUI/HyperOS 的中文界面里按钮从不全大写，默认的 ALL CAPS 只对拉丁字母可见。
                view.setAllCaps(false);
            }
        }
    }

    /**
     * 日志在后台线程采集（要读文件 + 跑 logcat），回主线程前确认视图还挂着。
     *
     * <p><b>必须从宿主的 context 读。</b>{@code activity} 是 guest 的 Activity，
     * 它的 {@code filesDir} 可能被引擎重定向到虚拟数据目录，用它去读
     * {@link SandboxConsole} 会读到另一个文件（或读不到）——
     * 而 {@link ZhiSandbox#attach} 记下的 {@code appContext} 才是宿主自己的。
     */
    private static void loadLog(Activity activity, TextView body) {
        final Context reader;
        Context host = ZhiSandbox.hostContext();
        reader = host != null ? host : activity.getApplicationContext();
        new Thread(() -> {
            String text;
            try {
                text = SandboxConsole.snapshot(reader);
            } catch (Throwable error) {
                text = "读取日志失败: " + error;
            }
            final String result = text;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (body.getWindowToken() != null) body.setText(result);
            });
        }, "zhi-sandbox-log").start();
    }

    // ------------------------------------------------------------------ 小工具

    /**
     * 调色板。优先用宿主 context：guest 的 Activity 主题是别人应用的，
     * 颜色不该跟着它走（悬浮窗自己的外观应该始终是 ZhiCode 的 Miuix 主题）。
     */
    private static SandboxPalette paletteOf(Activity activity) {
        Context host = ZhiSandbox.hostContext();
        return SandboxPalette.resolve(host != null ? host : activity);
    }

    private static TextView action(Activity activity, String label, int color, SandboxPalette palette) {
        TextView view = new TextView(activity);
        view.setText(label);
        view.setTextColor(color);
        view.setTextSize(11);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setGravity(Gravity.CENTER);
        // 点下去有反馈：这是 View 树里手搭的“按钮”，没有 Composable 的 press indication，
        // 不给涟漪的话点起来像没反应（尤其“日志”这种要等一秒的功能）。
        int ripple = (palette.text & 0x00FFFFFF) | 0x33000000;
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(ripple), null, null));
        view.setClickable(true);
        return view;
    }

    private static GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radiusDp);
        return drawable;
    }

    private static int dp(Activity activity, float value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f);
    }

    /** 拖动状态。抽出来是因为匿名监听器里需要一个可变的引用位。 */
    private static final class DragState {
        private float downRawX, downRawY;
        private float startTranslationX, startTranslationY;
        private float currentRawX, currentRawY;
        private Runnable pendingFrame;

        void begin(float rawX, float rawY, View panel) {
            downRawX = rawX;
            downRawY = rawY;
            currentRawX = rawX;
            currentRawY = rawY;
            startTranslationX = panel.getTranslationX();
            startTranslationY = panel.getTranslationY();
        }

        void move(float rawX, float rawY) {
            currentRawX = rawX;
            currentRawY = rawY;
        }

        /** 每帧只应用一次位置更新：连续 MOVE 事件不会各自触发一次布局。 */
        void schedule(View panel) {
            if (pendingFrame != null) return;
            pendingFrame = () -> {
                pendingFrame = null;
                apply(panel);
            };
            panel.postOnAnimation(pendingFrame);
        }

        void cancelPending(View panel) {
            if (pendingFrame != null) {
                panel.removeCallbacks(pendingFrame);
                pendingFrame = null;
            }
        }

        void apply(View panel) {
            panel.setTranslationX(startTranslationX + currentRawX - downRawX);
            panel.setTranslationY(startTranslationY + currentRawY - downRawY);
        }

        boolean isTap(float rawX, float rawY, int slopPx) {
            return Math.abs(rawX - downRawX) < slopPx && Math.abs(rawY - downRawY) < slopPx;
        }
    }

}
