package com.monday8am.edgelab.presentation.liveride

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
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

            val steps = state.speedMultiplier.toInt().coerceAtLeast(1)
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
