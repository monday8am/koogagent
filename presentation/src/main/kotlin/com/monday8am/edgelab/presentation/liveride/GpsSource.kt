package com.monday8am.edgelab.presentation.liveride

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data class GpsPosition(
    val latLng: LatLng,
    val heading: Float,
    val speedKmh: Float,
    val distanceTravelledKm: Float,
    val power: Int?,
    val routePointIndex: Int,
)

interface GpsSource {
    val positions: Flow<GpsPosition>
}

fun interface GpsSourceFactory {
    fun create(routePoints: List<LatLng>, playbackState: StateFlow<PlaybackState>): GpsSource
}
