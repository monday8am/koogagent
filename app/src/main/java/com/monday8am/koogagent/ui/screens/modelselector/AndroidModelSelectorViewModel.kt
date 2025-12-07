package com.monday8am.koogagent.ui.screens.modelselector

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.monday8am.koogagent.data.ModelConfiguration
import com.monday8am.presentation.modelselector.ModelSelectorViewModel
import com.monday8am.presentation.modelselector.ModelSelectorViewModelImpl
import com.monday8am.presentation.modelselector.UiState
import com.monday8am.presentation.modelselector.ModelDownloadManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Android-specific wrapper for ModelSelectorViewModel
 * Connects platform-agnostic implementation to Android lifecycle
 */
class AndroidModelSelectorViewModel(
    private val impl: ModelSelectorViewModel,
) : ViewModel(),
    ModelSelectorViewModel by impl {
    override val uiState: StateFlow<UiState> =
        impl.uiState.stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState(),
        )

    override fun onCleared() {
        super.onCleared()
        impl.dispose()
    }
}

/**
 * Factory for creating AndroidModelSelectorViewModel instances
 */
class ModelSelectorViewModelFactory(
    private val availableModels: List<ModelConfiguration>,
    private val modelDownloadManager: ModelDownloadManager,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AndroidModelSelectorViewModel::class.java)) {
            val impl =
                ModelSelectorViewModelImpl(
                    availableModels = availableModels,
                    modelDownloadManager = modelDownloadManager,
                )
            return AndroidModelSelectorViewModel(impl) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
