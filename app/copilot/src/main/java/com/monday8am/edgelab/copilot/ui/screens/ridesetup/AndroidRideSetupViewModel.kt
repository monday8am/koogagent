package com.monday8am.edgelab.copilot.ui.screens.ridesetup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monday8am.edgelab.presentation.ridesetup.RideSetupViewModel
import com.monday8am.edgelab.presentation.ridesetup.UiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class AndroidRideSetupViewModel(private val impl: RideSetupViewModel) :
    ViewModel(), RideSetupViewModel by impl {

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
