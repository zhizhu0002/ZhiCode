package com.zhizhu.zhicode.compose.editor

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.zhizhu.zhicode.compose.data.ThemeSettings
import com.zhizhu.zhicode.compose.data.ThemeStore
import com.zhizhu.zhicode.compose.theme.ZhiAppTheme
import com.zhizhu.zhicode.compose.theme.ZhiThemeMode
import com.zhizhu.zhicode.compose.ui.pages.miuix.EditorPage

class EditorActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_PATH = "com.zhizhu.zhicode.editor.EXTRA_PATH"

        /** 打开编辑器的 Intent。 */
        fun intent(context: Context, path: String): Intent =
            Intent(context, EditorActivity::class.java).putExtra(EXTRA_PATH, path)
    }

    private val viewModel: EditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // edge-to-edge 必须在 super.onCreate 之前：它设的是窗口 decor 的布局策略，
        // 晚于内容视图创建就只在下一帧生效（会看到一次状态栏闪动）。
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        applyStatusBarAppearance()

        // 这一屏的窗口策略来自清单：adjustResize，且**不带** stateAlwaysHidden。

        viewModel.load(intent.getStringExtra(EXTRA_PATH).orEmpty())

        onBackPressedDispatcher.addCallback(this) { handleBack() }

        // 主题值只读一次：`stored()` 与 `ThemeStore.read` 都要走一次 SharedPreferences，
        // 放在 lambda 里会变成「每次根重组都读一次盘」。
        val themeSettings = runCatching { ThemeStore.read(this) }
            .getOrDefault(ThemeSettings())
        setContent {
            // 与主界面同一个主题栈（ZhiThemeMode 是深浅的唯一判定处）。
            ZhiAppTheme(theme = themeSettings) {
                EditorPage(viewModel = viewModel, onExit = { finishWithResult() })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 从别的窗口（输入法设置、分享面板…）回来时系统栏外观可能被改过，对齐一次。
        applyStatusBarAppearance()
    }

    /** 唯一的返回入口。 */
    private fun handleBack() {
        val state = viewModel.state.value
        when {
            state.exitConfirm -> viewModel.cancelExitConfirm()
            state.saving -> viewModel.cancelSave()
            state.dirty -> viewModel.requestExitConfirm()
            else -> finishWithResult()
        }
    }

    /** 关掉这一屏，并把"有没有保存过"回给主界面（它据此刷新文件列表）。 */
    private fun finishWithResult() {
        setResult(if (viewModel.savedAtLeastOnce) RESULT_OK else RESULT_CANCELED)
        finish()
    }

    /** 状态栏透明 + 图标明暗跟随应用主题；判定与落地都在 [ZhiThemeMode]。 */
    private fun applyStatusBarAppearance() {
        ZhiThemeMode.appliedTo(
            this,
            ZhiThemeMode.resolve(ZhiThemeMode.stored(this), ZhiThemeMode.systemDark(this)),
        )
    }
}
