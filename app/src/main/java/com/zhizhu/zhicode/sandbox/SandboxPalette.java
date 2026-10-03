package com.zhizhu.zhicode.sandbox;

import android.content.Context;

import com.zhizhu.zhicode.compose.theme.ZhiOverlayPalette;

/**
 * 注入 guest 的沙箱控制栏配色。
 *
 * <p><b>这里现在只是 {@link ZhiOverlayPalette} 的转发。</b>
 *
 * <p>原来它自己维护了一整套主题解析：读宿主的
 * {@code <BRAND_SLUG>_ui_preferences / ui_theme} 键，支持
 * {@code day} / {@code neon-purple} / {@code custom（palette_*）} / {@code classic} 四档，
 * 每档一组硬编码色值。问题在于<b>那套键跟现在的应用主题毫无关系</b> ——
 * 应用主题早已统一到 Miuix（`ZhiThemeMode` + `darkColorScheme()`/`lightColorScheme()`），
 * 而 `ui_theme` 是 IQ Code 时代的遗留键，没人再写它，于是这个开关永远取到
 * {@code classic} 那一档深棕黑。用户看到的正是「悬浮窗还不是 miuix，而是 IQ Code 的」。
 *
 * <p>现在配色只有一处来源（Miuix 的主题色板），本类只负责给它一个
 * 面向 View 代码的短名字。四个遗留档位整段删掉 —— 留着一个取不到值的解析器，
 * 下一个人只会以为它还在生效。
 */
final class SandboxPalette {

    /** 面板/对话框底色。 */
    final int surface;
    /** 主文字色。 */
    final int text;
    /** 副文字色（说明、次要信息）。 */
    final int muted;
    /** 强调色（品牌名、可点动作）。 */
    final int accent;
    /** 危险动作色（「停止」）。 */
    final int danger;
    /** 底色是否为浅色。 */
    final boolean lightBackground;

    private SandboxPalette(int surface, int text, int muted, int accent, int danger,
                           boolean lightBackground) {
        this.surface = surface;
        this.text = text;
        this.muted = muted;
        this.accent = accent;
        this.danger = danger;
        this.lightBackground = lightBackground;
    }

    /**
     * 解析当前主题。
     *
     * <p>{@code context} 只用来读用户选的深浅模式；传 null 时按深色处理
     * （悬浮窗压在任何 guest 界面上，深色底白字是最稳的选择）。
     */
    static SandboxPalette resolve(Context context) {
        ZhiOverlayPalette.Snapshot snapshot = ZhiOverlayPalette.resolve(context);
        return new SandboxPalette(
                snapshot.surface,
                snapshot.onSurface,
                snapshot.muted,
                snapshot.accent,
                snapshot.danger,
                snapshot.light);
    }
}
