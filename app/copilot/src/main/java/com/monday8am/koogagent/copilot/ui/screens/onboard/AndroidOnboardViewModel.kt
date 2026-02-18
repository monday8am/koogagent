package com.monday8am.koogagent.copilot.ui.screens.onboard

import androidx.lifecycle.ViewModel
import com.monday8am.presentation.modelselector.ModelSelectorViewModelImpl
import com.monday8am.presentation.modelselector.UiAction

/**
 * Android wrapper ViewModel for Onboard screen.
 * Delegates to platform-agnostic ModelSelectorViewModelImpl from :presentation module.
 */
class AndroidOnboardViewModel(private val impl: ModelSelectorViewModelImpl) : ViewModel() {

    val uiState = impl.uiState

    fun onUiAction(action: UiAction) {
        impl.onUiAction(action)
    }

    override fun onCleared() {
        impl.dispose()
    }
}
