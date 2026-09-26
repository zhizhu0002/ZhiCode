package com.zhizhu.zhicode.sandbox;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

import com.termux.shared.termux.TermuxConstants;

/**
 * 沙箱界面配色。
 *
 * <p>宿主层有两处 UI 需要同一套颜色：管理界面（{@link SandboxBoard}）与注入 guest 的
 * 控制栏（{@link SandboxOverlay}）。抽出来是为了避免两边各自解析主题、各自硬编码色值 ——
 * 那正是"改了一处忘了另一处"的常见来源。
 *
 * <p>主题键沿用宿主既有的 {@code <BRAND_SLUG>_ui_preferences / ui_theme}，支持四档：
 * {@code day} / {@code neon-purple} / {@code custom}（读 {@code palette_*} 键）/
 * 其它一律按 {@code classic}。
 *
 * <p>本类只依赖 Context 与 SharedPreferences，不触碰引擎。
 */
final class SandboxPalette {

    private static final String PREFS_NAME = TermuxConstants.BRAND_SLUG + "_ui_preferences";
    private static final String THEME_KEY = "ui_theme";

    final int background;
    final int surface;
    final int text;
    final int muted;
    final int accent;
    final int danger;
    final int success;
    /** 卡片内按钮底色：自定义主题没有专门的键，由表面色与文字色混出对比。 */
    final int cardButton;
    /** 背景是否为浅色。系统栏图标明暗据此决定。 */
    final boolean lightBackground;

    private SandboxPalette(int background, int surface, int text, int muted, int accent,
                           int danger, int success, int cardButton, boolean lightBackground) {
        this.background = background;
        this.surface = surface;
        this.text = text;
        this.muted = muted;
        this.accent = accent;
        this.danger = danger;
        this.success = success;
        this.cardButton = cardButton;
        this.lightBackground = lightBackground;
    }

    /** 解析当前主题。读不到设置或键值未知时回退 classic，不抛异常。 */
    static SandboxPalette resolve(Context context) {
        String theme = themeOf(context);
        if ("day".equals(theme)) {
            return new SandboxPalette(0xFFF6F8FC, 0xFFFFFFFF, 0xFF1D2433, 0xFF5D687A,
                    0xFF535BD6, 0xFFC2414B, 0xFF1C895B, 0xFFE7EBF3, true);
        }
        if ("neon-purple".equals(theme)) {
            return new SandboxPalette(0xFF090D18, 0xFF131B2F, 0xFFEFF3FF, 0xFF99A6C4,
                    0xFF6C8CFF, 0xFFFF8899, 0xFF73D69C, 0xFF1A2340, false);
        }
        if ("custom".equals(theme)) {
            SharedPreferences prefs = prefs(context);
            int background = prefs.getInt("palette_background", 0xFF12110F);
            int text = prefs.getInt("palette_text", 0xFFF0ECE5);
            int surface = prefs.getInt("palette_surface", 0xFF191816);
            return new SandboxPalette(
                    background,
                    surface,
                    text,
                    blend(text, background, 0.42f),
                    prefs.getInt("palette_accent", 0xFFD97757),
                    prefs.getInt("palette_red", 0xFFD6786F),
                    prefs.getInt("palette_green", 0xFF7EB288),
                    blend(surface, text, 0.10f),
                    false);
        }
        return new SandboxPalette(0xFF12110F, 0xFF191816, 0xFFF0ECE5, 0xFFA49D93,
                0xFFD97757, 0xFFD6786F, 0xFF7EB288, 0xFF2A2723, false);
    }

    private static String themeOf(Context context) {
        try {
            String theme = prefs(context).getString(THEME_KEY, "classic");
            return theme == null ? "classic" : theme;
        } catch (Throwable ignored) {
            return "classic";
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** 按比例在两色之间插值。 */
    static int blend(int from, int to, float amount) {
        return Color.rgb(
                Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * amount),
                Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * amount),
                Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * amount));
    }
}
