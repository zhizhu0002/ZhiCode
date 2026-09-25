package com.iqge.iqcode.compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.iqge.iqcode.compose.state.WorkspaceViewModel
import com.iqge.iqcode.compose.state.WorkspaceViewModelFactory
import com.iqge.iqcode.compose.ui.IqCodeApp
import com.termux.app.iqcode.tools.AndroidIntentBridge

/**
 * 主界面宿主。
 *
 * ViewModel 在 **Activity 这一层**创建并显式传给 [IqCodeApp]（而不是在 Composable 里
 * 用默认参数创建）。原因：`onResume` 需要访问它——从系统「安装未知应用」权限页返回后，
 * 要继续那个被挂起的 APK 安装流程，并把结果写回对话流。
 *
 * 两者拿到的是**同一个实例**：`viewModel(factory = …)` 与 `by viewModels { … }`
 * 都以本 Activity 作为 `ViewModelStoreOwner`，只是入口不同。
 */
class MainActivity : ComponentActivity() {

    private val viewModel: WorkspaceViewModel by viewModels {
        WorkspaceViewModelFactory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { IqCodeApp(viewModel) }
    }

    override fun onResume() {
        super.onResume()
        // APK 安装权限被拒绝时，AndroidIntentBridge 会把安装 Intent 暂存起来并跳去系统设置页。
        // 用户授权后回到本页面，必须在这里把流程接上——没有这一步，
        // 「授予权限」之后安装就永远停在半路，用户只能再让 Agent 重做一遍。
        // 返回 null 表示当前没有待续的安装（绝大多数情况），此时什么都不做。
        val pending = runCatching { AndroidIntentBridge.resumePendingApkInstall(this) }
            .getOrElse { error ->
                viewModel.reportExternalEvent("继续安装失败", error.message ?: "未知原因")
                return
            } ?: return
        viewModel.reportExternalEvent(
            if (pending.isError) "继续安装失败" else "安装 APK",
            pending.content,
        )
    }
}
