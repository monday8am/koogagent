package com.monday8am.edgelab.copilot.ui.screens.liveride

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.gson.JsonObject
import com.monday8am.edgelab.presentation.liveride.LatLng as RideLatLng
import com.monday8am.edgelab.presentation.liveride.PoiCategory
import com.monday8am.edgelab.presentation.liveride.PoiMarker
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng as MapLatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

const val MAPLIBRE_STYLE_URL = "https://tiles.openfreemap.org/styles/dark"

private const val SOURCE_ROUTE = "source-route"
private const val SOURCE_COMPLETED = "source-completed"
private const val SOURCE_RIDER = "source-rider"
private const val SOURCE_POIS = "source-pois"
private const val LAYER_ROUTE = "layer-route"
private const val LAYER_COMPLETED = "layer-completed"
private const val LAYER_RIDER = "layer-rider"
private const val LAYER_POIS = "layer-pois"

private class MapState {
    var map: MapLibreMap? = null
    var style: Style? = null
}

@Composable
fun MapLibreMapView(
    routePolyline: List<RideLatLng>,
    completedPolyline: List<RideLatLng>,
    riderPosition: RideLatLng,
    pois: List<PoiMarker>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapState = remember { MapState() }

    val mapView = remember(context) {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    AndroidView(
        factory = { mapView },
        update = { _ ->
            val style = mapState.style ?: return@AndroidView
            val map = mapState.map ?: return@AndroidView

            // Update rider position dot
            (style.getSource(SOURCE_RIDER) as? GeoJsonSource)?.setGeoJson(
                Feature.fromGeometry(
                    Point.fromLngLat(riderPosition.longitude, riderPosition.latitude)
                )
            )

            // Update completed segment
            if (completedPolyline.size >= 2) {
                (style.getSource(SOURCE_COMPLETED) as? GeoJsonSource)?.setGeoJson(
                    Feature.fromGeometry(toLineString(completedPolyline))
                )
            }

            // Follow rider with camera
            map.animateCamera(
                CameraUpdateFactory.newLatLng(
                    MapLatLng(riderPosition.latitude, riderPosition.longitude)
                )
            )
        },
        modifier = modifier,
    )

    // Initialize map and style once
    LaunchedEffect(Unit) {
        mapView.getMapAsync { map ->
            mapState.map = map
            map.setStyle(MAPLIBRE_STYLE_URL) { style ->
                mapState.style = style
                setupLayers(style, routePolyline, pois)
                map.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        MapLatLng(riderPosition.latitude, riderPosition.longitude),
                        12.0,
                    )
                )
            }
        }
    }
}

private fun setupLayers(style: Style, routePolyline: List<RideLatLng>, pois: List<PoiMarker>) {
    // Full route source
    val routeFeatures =
        if (routePolyline.size >= 2) listOf(Feature.fromGeometry(toLineString(routePolyline)))
        else emptyList()
    style.addSource(GeoJsonSource(SOURCE_ROUTE, FeatureCollection.fromFeatures(routeFeatures)))

    // Completed segment source (empty initially)
    style.addSource(
        GeoJsonSource(SOURCE_COMPLETED, FeatureCollection.fromFeatures(emptyList<Feature>()))
    )

    // Rider position source
    val startPoint =
        Point.fromLngLat(
            routePolyline.firstOrNull()?.longitude ?: 0.0,
            routePolyline.firstOrNull()?.latitude ?: 0.0,
        )
    style.addSource(GeoJsonSource(SOURCE_RIDER, Feature.fromGeometry(startPoint)))

    // POI source
    style.addSource(
        GeoJsonSource(SOURCE_POIS, FeatureCollection.fromFeatures(pois.map { toPoiFeature(it) }))
    )

    // Route line (dimmed — shows the full route ahead)
    style.addLayer(
        LineLayer(LAYER_ROUTE, SOURCE_ROUTE).withProperties(
            PropertyFactory.lineColor("#0EA5E9"),
            PropertyFactory.lineWidth(6f),
            PropertyFactory.lineOpacity(0.35f),
        )
    )

    // Completed segment (full opacity)
    style.addLayer(
        LineLayer(LAYER_COMPLETED, SOURCE_COMPLETED).withProperties(
            PropertyFactory.lineColor("#0EA5E9"),
            PropertyFactory.lineWidth(6f),
        )
    )

    // Rider dot
    style.addLayer(
        CircleLayer(LAYER_RIDER, SOURCE_RIDER).withProperties(
            PropertyFactory.circleRadius(10f),
            PropertyFactory.circleColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(3f),
            PropertyFactory.circleStrokeColor("#0EA5E9"),
        )
    )

    // POI emoji labels
    style.addLayer(
        SymbolLayer(LAYER_POIS, SOURCE_POIS).withProperties(
            PropertyFactory.textField(Expression.get("label")),
            PropertyFactory.textSize(18f),
            PropertyFactory.textAllowOverlap(true),
            PropertyFactory.textIgnorePlacement(true),
        )
    )
}

private fun toLineString(points: List<RideLatLng>): LineString =
    LineString.fromLngLats(points.map { Point.fromLngLat(it.longitude, it.latitude) })

private fun toPoiFeature(poi: PoiMarker): Feature {
    val emoji =
        when (poi.category) {
            PoiCategory.CAFE -> "\u2615" // ☕
            PoiCategory.WATER -> "\uD83D\uDCA7" // 💧
            PoiCategory.BIKE_SHOP -> "\uD83D\uDD27" // 🔧
            PoiCategory.SHELTER -> "\u26FA" // ⛺
        }
    val props = JsonObject().apply { addProperty("label", emoji) }
    return Feature.fromGeometry(
        Point.fromLngLat(poi.position.longitude, poi.position.latitude),
        props,
    )
}
