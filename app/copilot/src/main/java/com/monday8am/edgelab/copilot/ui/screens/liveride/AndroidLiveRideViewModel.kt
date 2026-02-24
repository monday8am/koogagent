package com.monday8am.edgelab.copilot.ui.screens.liveride

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monday8am.edgelab.presentation.liveride.LiveRideAction
import com.monday8am.edgelab.presentation.liveride.LiveRideUiState
import com.monday8am.edgelab.presentation.liveride.LiveRideViewModelImpl
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class AndroidLiveRideViewModel(
    private val impl: LiveRideViewModelImpl = LiveRideViewModelImpl(),
) : ViewModel() {

    val uiState: StateFlow<LiveRideUiState> =
        impl.uiState.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000L),
            impl.uiState.value,
        )

    fun onUiAction(action: LiveRideAction) = impl.onUiAction(action)

    override fun onCleared() {
        super.onCleared()
        impl.dispose()
    }
}
