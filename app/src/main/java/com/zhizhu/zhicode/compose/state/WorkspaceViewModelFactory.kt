package com.zhizhu.zhicode.compose.state

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.zhizhu.zhicode.compose.data.MockWorkspaceRepository
import com.zhizhu.zhicode.compose.data.WorkspaceRepository

/**
 * [WorkspaceViewModel] 的构造工厂。
 *
 * 为什么需要它：默认的 `viewModel()` 走反射构造。`WorkspaceViewModel` 的构造器是
 * `(Application, WorkspaceRepository = 默认值)` —— Kotlin 只为**全部参数都有默认值**的构造器
 * 生成合成无参构造，参数里有一个没有默认值时，生成的只有
 * `(Application, WorkspaceRepository, int, DefaultConstructorMarker)`。
 * `AndroidViewModelFactory` 找的是精确的 `(Application)`，于是抛
 * `NoSuchMethodException`。所以必须显式提供工厂。
 *
 * 另一个原因更长远：真实引擎接入后，`WorkspaceRepository` 要换成接真数据的实现，
 * 这个工厂就是注入点。
 */
class WorkspaceViewModelFactory(
    private val application: Application,
    private val repo: WorkspaceRepository = MockWorkspaceRepository(),
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WorkspaceViewModel::class.java)) {
            return WorkspaceViewModel(application, repo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
