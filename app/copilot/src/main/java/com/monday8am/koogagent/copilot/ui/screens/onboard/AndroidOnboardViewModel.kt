package com.monday8am.koogagent.copilot.ui.screens.onboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monday8am.presentation.modelselector.ModelSelectorViewModel
import com.monday8am.presentation.modelselector.UiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Android wrapper ViewModel for Onboard screen.
 * Delegates to platform-agnostic ModelSelectorViewModel from :presentation module.
 * Follows edgelab pattern with stateIn for lifecycle-aware state collection.
 */
class AndroidOnboardViewModel(private val impl: ModelSelectorViewModel) :
    ViewModel(), ModelSelectorViewModel by impl {

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
