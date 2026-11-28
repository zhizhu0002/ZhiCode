package com.zhizhu.zhicode.compose.state

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.zhizhu.zhicode.compose.data.MockWorkspaceRepository
import com.zhizhu.zhicode.compose.data.WorkspaceRepository

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
