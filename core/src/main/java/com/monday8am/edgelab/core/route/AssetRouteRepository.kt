package com.monday8am.edgelab.core.route

import android.content.Context
import com.monday8am.edgelab.data.route.RouteCoordinate
import com.monday8am.edgelab.data.route.RouteData
import com.monday8am.edgelab.data.route.RouteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private val ROUTE_NAMES = mapOf(
    "strade-bianche" to "Strade Bianche GranFondo",
)

/**
 * Loads route data from bundled JSON assets at routes/{routeId}/route.json.
 */
class AssetRouteRepository(private val context: Context) : RouteRepository {

    override suspend fun getRoute(routeId: String): RouteData? = withContext(Dispatchers.IO) {
        runCatching {
            val json = context.assets
                .open("routes/$routeId/route.json")
                .bufferedReader()
                .use { it.readText() }
            val root = JSONObject(json)
            val array = root.getJSONArray("coordinates")
            val coordinates = buildList(array.length()) {
                for (i in 0 until array.length()) {
                    val c = array.getJSONObject(i)
                    add(
                        RouteCoordinate(
                            lat = c.getDouble("lat"),
                            lng = c.getDouble("lng"),
                            alt = c.getDouble("alt"),
                            t = c.getLong("t"),
                        )
                    )
                }
            }
            RouteData(
                routeId = routeId,
                name = ROUTE_NAMES[routeId] ?: routeId,
                coordinates = coordinates,
            )
        }.getOrNull()
    }
}
