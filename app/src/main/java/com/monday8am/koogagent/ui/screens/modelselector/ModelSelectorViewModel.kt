package com.monday8am.koogagent.ui.screens.modelselector

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.monday8am.koogagent.data.ModelConfiguration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel for Model Selector screen.
 *
 * Currently a placeholder for future model selection functionality.
 * Future features:
 * - Display list of available models from ModelCatalog
 * - Handle model selection state
 * - Navigate to notification screen with selected model
 */
class ModelSelectorViewModel(
    private val availableModels: List<ModelConfiguration>,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModelSelectorUiState())
    val uiState: StateFlow<ModelSelectorUiState> = _uiState.asStateFlow()

    // Placeholder for future actions
    fun onModelSelected(model: ModelConfiguration) {
        // Future: Update selected model state and navigate
    }
}

/**
 * UI state for model selector screen.
 * Currently minimal placeholder structure.
 */
data class ModelSelectorUiState(
    val selectedModel: ModelConfiguration? = null,
    val isLoading: Boolean = false,
)

/**
 * Factory for creating ModelSelectorViewModel instances.
 * Follows same pattern as NotificationViewModelFactory.
 */
class ModelSelectorViewModelFactory(
    private val availableModels: List<ModelConfiguration>,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ModelSelectorViewModel::class.java)) {
            return ModelSelectorViewModel(availableModels) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
