package com.monday8am.edgelab.data.route

data class RouteCoordinate(
    val lat: Double,
    val lng: Double,
    val alt: Double,
    val t: Long, // ms elapsed from route start (Komoot pace)
)

data class RouteData(val routeId: String, val name: String, val coordinates: List<RouteCoordinate>)

interface RouteRepository {
    /** Returns route data for the given routeId, or null if not found. */
    suspend fun getRoute(routeId: String): RouteData?
}
