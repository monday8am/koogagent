package com.monday8am.edgelab.copilot.ui.screens.liveride

import androidx.lifecycle.ViewModel
import com.monday8am.edgelab.data.route.RouteRepository
import com.monday8am.edgelab.presentation.liveride.LiveRideAction
import com.monday8am.edgelab.presentation.liveride.LiveRideUiState
import com.monday8am.edgelab.presentation.liveride.LiveRideViewModelImpl
import kotlinx.coroutines.flow.StateFlow

class AndroidLiveRideViewModel(
    routeId: String,
    routeRepository: RouteRepository,
    playbackSpeed: Float = 1.0f,
) : ViewModel() {

    private val impl = LiveRideViewModelImpl(routeId, routeRepository, playbackSpeed)

    val uiState: StateFlow<LiveRideUiState> = impl.uiState

    fun onUiAction(action: LiveRideAction) = impl.onUiAction(action)

    override fun onCleared() {
        super.onCleared()
        impl.dispose()
    }
}
