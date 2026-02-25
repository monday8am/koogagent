package com.monday8am.edgelab.presentation.liveride

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
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
    val routePolyline: List<LatLng>,
    val completedPolyline: List<LatLng>,
    val currentPosition: LatLng,
    val currentHeading: Float,
    val hudMetrics: HudMetrics,
    val pois: List<PoiMarker>,
    val chatMessages: List<ChatMessage>,
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

class LiveRideViewModelImpl(dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate) :
    LiveRideViewModel {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private var currentPositionIndex = 0
    private var powerWalk = 170
    private var batteryPercent = 88
    private var distanceTravelled = 0.0f
    private var messageIdCounter = 1

    companion object {
        private val ROUTE_POINTS =
            listOf(
                LatLng(40.6580, -3.8850), // 0: Start - Collado Villalba
                LatLng(40.6700, -3.9050), // 1
                LatLng(40.6850, -3.9250), // 2
                LatLng(40.7000, -3.9400), // 3
                LatLng(40.7150, -3.9550), // 4
                LatLng(40.7300, -3.9700), // 5
                LatLng(40.7450, -3.9850), // 6: El Boalo
                LatLng(40.7600, -3.9950), // 7
                LatLng(40.7750, -4.0050), // 8
                LatLng(40.7900, -4.0150), // 9
                LatLng(40.8050, -4.0200), // 10
                LatLng(40.8200, -4.0150), // 11: Alto del Puerto
                LatLng(40.8300, -4.0050), // 12: Bar El Puerto (cafe)
                LatLng(40.8350, -3.9850), // 13
                LatLng(40.8300, -3.9650), // 14
                LatLng(40.8200, -3.9450), // 15
                LatLng(40.8050, -3.9250), // 16
                LatLng(40.7900, -3.9100), // 17
                LatLng(40.7700, -3.8950), // 18: Fuente km28 (water)
                LatLng(40.7500, -3.8850), // 19
                LatLng(40.7300, -3.8750), // 20
                LatLng(40.7100, -3.8700), // 21
                LatLng(40.6950, -3.8750), // 22: Taller Ciclismo (bike shop)
                LatLng(40.6800, -3.8800), // 23
                LatLng(40.6580, -3.8850), // 24: End - back to start
            )

        private val POIS =
            listOf(
                PoiMarker("poi_cafe", LatLng(40.8300, -4.0050), PoiCategory.CAFE, "Bar El Puerto"),
                PoiMarker("poi_water", LatLng(40.7700, -3.8950), PoiCategory.WATER, "Fuente km28"),
                PoiMarker(
                    "poi_bikeshop",
                    LatLng(40.6950, -3.8750),
                    PoiCategory.BIKE_SHOP,
                    "Taller Ciclismo",
                ),
            )

        private val INITIAL_CHAT: List<ChatMessage> =
            listOf(
                ChatMessage.Copilot(
                    id = "msg_0",
                    text =
                        "Ride started! Guadarrama Loop, 62km. Weather looks clear. " +
                            "First climb in 8km \u2014 El Boalo, 3.2km at 6%. Ready when you are \uD83D\uDEB4",
                )
            )

        private val SPEED_MULTIPLIERS = listOf(1.0f, 2.0f, 4.0f, 8.0f)
        private const val TOTAL_KM = 62.0f
        private const val TICK_INTERVAL_MS = 1000L
    }

    private val _uiState =
        MutableStateFlow(
            LiveRideUiState(
                routePolyline = ROUTE_POINTS,
                completedPolyline = listOf(ROUTE_POINTS[0]),
                currentPosition = ROUTE_POINTS[0],
                currentHeading = 0f,
                hudMetrics =
                    HudMetrics(speed = 0f, distance = 0f, power = 170, batteryPercent = 88),
                pois = POIS,
                chatMessages = INITIAL_CHAT,
                isChatExpanded = false,
                isVoiceRecording = false,
                isProcessing = false,
                playbackState =
                    PlaybackState(
                        isPlaying = true,
                        speedMultiplier = 1.0f,
                        currentKm = 0f,
                        totalKm = TOTAL_KM,
                    ),
            )
        )

    override val uiState: StateFlow<LiveRideUiState> = _uiState.asStateFlow()

    init {
        startPlaybackLoop()
    }

    private fun startPlaybackLoop() {
        scope.launch {
            while (true) {
                delay(TICK_INTERVAL_MS)
                val state = _uiState.value
                if (!state.playbackState.isPlaying) continue
                val steps = state.playbackState.speedMultiplier.toInt().coerceIn(1, 4)
                repeat(steps) { advancePosition() }
            }
        }
    }

    private fun advancePosition() {
        val maxIndex = ROUTE_POINTS.size - 1
        if (currentPositionIndex >= maxIndex) {
            currentPositionIndex = 0
            distanceTravelled = 0f
        }
        val from = ROUTE_POINTS[currentPositionIndex]
        currentPositionIndex++
        val to = ROUTE_POINTS[currentPositionIndex]

        val segmentKm = haversineKm(from, to)
        distanceTravelled += segmentKm
        val speedKmh = (segmentKm * 3600f).coerceIn(5f, 80f)
        powerWalk = (powerWalk + Random.nextInt(-10, 11)).coerceIn(130, 220)
        val heading = bearing(from, to)

        _uiState.update { state ->
            state.copy(
                completedPolyline = ROUTE_POINTS.take(currentPositionIndex + 1),
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
        _uiState.update { it.copy(chatMessages = it.chatMessages + userMsg) }
        scope.launch {
            delay(1500)
            val reply =
                ChatMessage.Copilot(
                    id = "msg_${messageIdCounter++}",
                    text = "Got it! Keeping an eye on the route ahead.",
                )
            _uiState.update { it.copy(chatMessages = it.chatMessages + reply) }
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
