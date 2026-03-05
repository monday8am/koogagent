package com.monday8am.edgelab.presentation.ridesetup

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

enum class GpsMode {
    SIMULATION,
    DEVICE_GPS,
}

enum class Difficulty {
    EASY,
    MEDIUM,
    HARD,
    EXPERT,
}

enum class PlaybackSpeed(val multiplier: Float) {
    SLOW(0.5f),
    NORMAL(1f),
    FAST(2f),
    VERY_FAST(5f),
    ULTRA_FAST(10f),
}

data class AdvancedSettings(
    val useRemoteLLM: Boolean = true,
    val showDeveloperHUD: Boolean = false,
    val enableAutoVoice: Boolean = true,
)

data class RouteInfo(
    val id: String,
    val name: String,
    val distanceKm: Float,
    val elevationGainM: Int,
    val difficulty: Difficulty,
)

data class UiState(
    val gpsMode: GpsMode = GpsMode.SIMULATION,
    val routes: ImmutableList<RouteInfo> = persistentListOf(),
    val selectedRouteId: String? = null,
    val playbackSpeed: PlaybackSpeed = PlaybackSpeed.NORMAL,
    val advancedSettings: AdvancedSettings = AdvancedSettings(),
    val isAdvancedExpanded: Boolean = false,
    val isStartEnabled: Boolean = false,
)

sealed class UiAction {
    data class SelectGpsMode(val mode: GpsMode) : UiAction()

    data class SelectRoute(val routeId: String) : UiAction()

    data class SetPlaybackSpeed(val speed: PlaybackSpeed) : UiAction()

    data class UpdateAdvancedSettings(val settings: AdvancedSettings) : UiAction()

    data object ToggleAdvancedExpanded : UiAction()

    data object StartRide : UiAction()
}

interface RideSetupViewModel {
    val uiState: StateFlow<UiState>

    fun onUiAction(action: UiAction)

    fun dispose()
}

class RideSetupViewModelImpl(
    @Suppress("UNUSED_PARAMETER") ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : RideSetupViewModel {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val internalState = MutableStateFlow(UiState(routes = ROUTE_CATALOG))

    override val uiState: StateFlow<UiState> =
        internalState
            .map { state ->
                state.copy(
                    isStartEnabled =
                        state.gpsMode == GpsMode.DEVICE_GPS || state.selectedRouteId != null
                )
            }
            .stateIn(scope, SharingStarted.Eagerly, UiState(routes = ROUTE_CATALOG))

    override fun onUiAction(action: UiAction) {
        when (action) {
            is UiAction.SelectGpsMode -> internalState.update { it.copy(gpsMode = action.mode) }
            is UiAction.SelectRoute ->
                internalState.update { it.copy(selectedRouteId = action.routeId) }
            is UiAction.SetPlaybackSpeed ->
                internalState.update { it.copy(playbackSpeed = action.speed) }
            is UiAction.UpdateAdvancedSettings ->
                internalState.update { it.copy(advancedSettings = action.settings) }
            is UiAction.ToggleAdvancedExpanded ->
                internalState.update { it.copy(isAdvancedExpanded = !it.isAdvancedExpanded) }
            is UiAction.StartRide -> {
                /* Navigation handled in UI layer */
            }
        }
    }

    override fun dispose() {
        scope.cancel()
    }

    companion object {
        val ROUTE_CATALOG: ImmutableList<RouteInfo> =
            persistentListOf(
                RouteInfo(
                    id = "strade-bianche",
                    name = "Strade Bianche GranFondo",
                    distanceKm = 144f,
                    elevationGainM = 2220,
                    difficulty = Difficulty.HARD,
                )
            )
    }
}
