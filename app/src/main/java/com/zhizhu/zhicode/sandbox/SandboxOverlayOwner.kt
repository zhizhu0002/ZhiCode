package com.zhizhu.zhicode.sandbox

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import android.view.View

/**
 * 给挂在 `WindowManager` 上的 Compose 视图补一套 lifecycle / savedstate owner。
 *
 * ## 为什么必须有这个类
 *
 * Compose 的运行时会在挂载时沿 `View` 树向上找三个 owner：
 *
 * | 查询 | 谁在普通界面里提供 |
 * |---|---|
 * | `ViewTreeLifecycleOwner` | `ComponentActivity` |
 * | `ViewTreeSavedStateRegistryOwner` | `ComponentActivity` |
 * | `ViewTreeViewModelStoreOwner` | `ComponentActivity`（`NavBackStackEntry` 再覆盖） |
 *
 * 系统悬浮窗的 `ComposeView` **不在任何 Activity 的视图树里**，所以这三个全查不到，
 * 一挂载就抛：
 *
 * ```
 * IllegalStateException: ViewTreeLifecycleOwner not found from DecorView@…
 * ```
 *
 * 这与 `Dialog` / `PopupWindow` 里放 Compose 是同一类问题，解法也一样：
 * 自己实现三个接口，在 `setContentView` 之前设到 `View` 上。
 *
 * ## 为什么是"只读"实现
 *
 * 它不需要真的承载配置变更与进程重建 —— 悬浮条的生命周期完全由
 * [SandboxFloatingController] 掌控（guest Activity 一 pause 就摘掉），
 * 自己的状态（位置、展开与否）也由那边持久化。
 * 所以这里只满足接口的形状，不做状态恢复。
 */
@SuppressLint("ViewConstructor")
internal class SandboxOverlayOwner(context: Context) :
    LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    /** 挂载前调用：三个接口都准备好了，Compose 才能安全地组合。 */
    fun attachTo(view: View) {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    /** 摘除时调用：把状态推到 DESTROYED，Compose 才会跑 `onDispose`。 */
    fun destroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }
}
