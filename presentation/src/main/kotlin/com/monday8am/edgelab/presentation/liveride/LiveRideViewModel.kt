package com.monday8am.edgelab.presentation.liveride

import com.monday8am.edgelab.data.route.RouteCoordinate
import com.monday8am.edgelab.data.route.RouteRepository
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LatLng(val latitude: Double, val longitude: Double)

data class HudMetrics(
    val speed: Float,
    val distance: Float,
    val power: Int?,
    val batteryPercent: Int,
)

enum class PoiCategory {
    CAFE,
    WATER,
    BIKE_SHOP,
    SHELTER,
}

data class PoiMarker(
    val id: String,
    val position: LatLng,
    val category: PoiCategory,
    val name: String,
)

sealed interface ChatMessage {
    val id: String

    data class User(override val id: String, val text: String) : ChatMessage

    data class Copilot(override val id: String, val text: String) : ChatMessage

    data class ToolCallDebug(override val id: String, val text: String) : ChatMessage
}

data class PlaybackState(
    val isPlaying: Boolean,
    val speedMultiplier: Float,
    val currentKm: Float,
    val totalKm: Float,
)

data class LiveRideUiState(
    val routeName: String,
    val isLoading: Boolean,
    val routePolyline: ImmutableList<LatLng>,
    val completedPolyline: ImmutableList<LatLng>,
    val currentPosition: LatLng,
    val currentHeading: Float,
    val hudMetrics: HudMetrics,
    val pois: ImmutableList<PoiMarker>,
    val chatMessages: ImmutableList<ChatMessage>,
    val isChatExpanded: Boolean,
    val isVoiceRecording: Boolean,
    val isProcessing: Boolean,
    val playbackState: PlaybackState,
)

sealed interface LiveRideAction {
    data object TogglePlayback : LiveRideAction

    data object CycleSpeed : LiveRideAction

    data object ExpandChat : LiveRideAction

    data object CollapseChat : LiveRideAction

    data class SendTextMessage(val text: String) : LiveRideAction

    data object EndRide : LiveRideAction
}

interface LiveRideViewModel {
    val uiState: StateFlow<LiveRideUiState>

    fun onUiAction(action: LiveRideAction)

    fun dispose()
}

class LiveRideViewModelImpl(
    private val routeId: String,
    private val routeRepository: RouteRepository,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : LiveRideViewModel {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private var routePoints: List<LatLng> = emptyList()
    private var currentPositionIndex = 0
    private var powerWalk = 170
    private var batteryPercent = 88
    private var distanceTravelled = 0.0f
    private var messageIdCounter = 1

    companion object {
        private val SPEED_MULTIPLIERS = listOf(1.0f, 2.0f, 4.0f, 8.0f)
        private const val TICK_INTERVAL_MS = 1000L
    }

    private val _uiState =
        MutableStateFlow(
            LiveRideUiState(
                routeName = "",
                isLoading = true,
                routePolyline = persistentListOf(),
                completedPolyline = persistentListOf(),
                currentPosition = LatLng(43.3226, 11.3223), // Siena — default before load
                currentHeading = 0f,
                hudMetrics =
                    HudMetrics(speed = 0f, distance = 0f, power = null, batteryPercent = 88),
                pois = persistentListOf(),
                chatMessages = persistentListOf(),
                isChatExpanded = false,
                isVoiceRecording = false,
                isProcessing = false,
                playbackState =
                    PlaybackState(
                        isPlaying = false,
                        speedMultiplier = 1.0f,
                        currentKm = 0f,
                        totalKm = 0f,
                    ),
            )
        )

    override val uiState: StateFlow<LiveRideUiState> = _uiState.asStateFlow()

    init {
        scope.launch {
            loadRoute()
            startPlaybackLoop()
        }
    }

    private suspend fun loadRoute() {
        val data = routeRepository.getRoute(routeId) ?: return
        routePoints = data.coordinates.map { LatLng(it.lat, it.lng) }
        val startPos = routePoints.first()
        val totalKm = withContext(Dispatchers.Default) { computeTotalKm(data.coordinates) }

        _uiState.update { state ->
            state.copy(
                routeName = data.name,
                isLoading = false,
                routePolyline = routePoints.toImmutableList(),
                completedPolyline = persistentListOf(startPos),
                currentPosition = startPos,
                hudMetrics =
                    HudMetrics(speed = 0f, distance = 0f, power = 170, batteryPercent = 88),
                chatMessages =
                    persistentListOf(
                        ChatMessage.Copilot(
                            id = "msg_0",
                            text = "Ride started! ${data.name}. Ready when you are 🚴",
                        )
                    ),
                playbackState = state.playbackState.copy(isPlaying = true, totalKm = totalKm),
            )
        }
    }

    private fun computeTotalKm(coords: List<RouteCoordinate>): Float {
        var total = 0.0
        for (i in 1 until coords.size) {
            total +=
                haversineKm(
                    LatLng(coords[i - 1].lat, coords[i - 1].lng),
                    LatLng(coords[i].lat, coords[i].lng),
                )
        }
        return total.toFloat()
    }

    private suspend fun startPlaybackLoop() {
        while (true) {
            delay(TICK_INTERVAL_MS)
            val state = _uiState.value
            if (state.isLoading || !state.playbackState.isPlaying || routePoints.size < 2) continue
            val steps = state.playbackState.speedMultiplier.toInt().coerceIn(1, 4)
            repeat(steps) { advancePosition() }
        }
    }

    private fun advancePosition() {
        if (routePoints.size < 2) return
        val maxIndex = routePoints.size - 1
        if (currentPositionIndex >= maxIndex) {
            currentPositionIndex = 0
            distanceTravelled = 0f
        }
        val from = routePoints[currentPositionIndex]
        currentPositionIndex++
        val to = routePoints[currentPositionIndex]

        val segmentKm = haversineKm(from, to)
        distanceTravelled += segmentKm
        val speedKmh = (segmentKm * 3600f).coerceIn(5f, 80f)
        powerWalk = (powerWalk + Random.nextInt(-10, 11)).coerceIn(130, 220)
        val heading = bearing(from, to)

        _uiState.update { state ->
            state.copy(
                completedPolyline = routePoints.take(currentPositionIndex + 1).toImmutableList(),
                currentPosition = to,
                currentHeading = heading,
                hudMetrics =
                    HudMetrics(
                        speed = speedKmh,
                        distance = distanceTravelled,
                        power = powerWalk,
                        batteryPercent = batteryPercent,
                    ),
                playbackState = state.playbackState.copy(currentKm = distanceTravelled),
            )
        }
    }

    override fun onUiAction(action: LiveRideAction) {
        when (action) {
            LiveRideAction.TogglePlayback -> togglePlayback()
            LiveRideAction.CycleSpeed -> cycleSpeed()
            LiveRideAction.ExpandChat -> _uiState.update { it.copy(isChatExpanded = true) }
            LiveRideAction.CollapseChat -> _uiState.update { it.copy(isChatExpanded = false) }
            is LiveRideAction.SendTextMessage -> sendTextMessage(action.text)
            LiveRideAction.EndRide -> {
                /* navigation handled in UI layer */
            }
        }
    }

    private fun togglePlayback() {
        _uiState.update {
            it.copy(playbackState = it.playbackState.copy(isPlaying = !it.playbackState.isPlaying))
        }
    }

    private fun cycleSpeed() {
        _uiState.update { state ->
            val idx = SPEED_MULTIPLIERS.indexOf(state.playbackState.speedMultiplier)
            val next = SPEED_MULTIPLIERS[(idx + 1) % SPEED_MULTIPLIERS.size]
            state.copy(playbackState = state.playbackState.copy(speedMultiplier = next))
        }
    }

    private fun sendTextMessage(text: String) {
        if (text.isBlank()) return
        val userMsg = ChatMessage.User(id = "msg_${messageIdCounter++}", text = text)
        _uiState.update { it.copy(chatMessages = (it.chatMessages + userMsg).toImmutableList()) }
        scope.launch {
            delay(1500)
            val reply =
                ChatMessage.Copilot(
                    id = "msg_${messageIdCounter++}",
                    text = "Got it! Keeping an eye on the route ahead.",
                )
            _uiState.update { it.copy(chatMessages = (it.chatMessages + reply).toImmutableList()) }
        }
    }

    override fun dispose() {
        scope.cancel()
    }

    private fun haversineKm(from: LatLng, to: LatLng): Float {
        val r = 6371.0
        val dLat = Math.toRadians(to.latitude - from.latitude)
        val dLon = Math.toRadians(to.longitude - from.longitude)
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val a =
            sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return (r * c).toFloat()
    }

    private fun bearing(from: LatLng, to: LatLng): Float {
        val dLon = Math.toRadians(to.longitude - from.longitude)
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)).toFloat() + 360f) % 360f
    }
}
