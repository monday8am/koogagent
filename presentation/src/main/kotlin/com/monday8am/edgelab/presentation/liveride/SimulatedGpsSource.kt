package com.monday8am.edgelab.presentation.liveride

import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow

class SimulatedGpsSource(
    private val routePoints: List<LatLng>,
    private val playbackState: StateFlow<PlaybackState>,
    private val tickIntervalMs: Long = 1000L,
) : GpsSource {

    override val positions: Flow<GpsPosition> = flow {
        if (routePoints.size < 2) return@flow

        var currentIndex = 0
        var distanceTravelled = 0f
        var power = 170

        while (true) {
            delay(tickIntervalMs)

            val state = playbackState.value
            if (!state.isPlaying) continue

            val steps = state.speedMultiplier.toInt().coerceIn(1, 8)
            repeat(steps) {
                if (currentIndex >= routePoints.size - 1) {
                    currentIndex = 0
                    distanceTravelled = 0f
                }
                val from = routePoints[currentIndex]
                currentIndex++
                val to = routePoints[currentIndex]

                val segmentKm = haversineKm(from, to)
                distanceTravelled += segmentKm
                power = (power + Random.nextInt(-10, 11)).coerceIn(130, 220)

                emit(
                    GpsPosition(
                        latLng = to,
                        heading = bearing(from, to),
                        speedKmh = (segmentKm * 3600f).coerceIn(5f, 80f),
                        distanceTravelledKm = distanceTravelled,
                        power = power,
                        routePointIndex = currentIndex,
                    )
                )
            }
        }
    }
}
