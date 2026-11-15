// SPDX-License-Identifier: AGPL-3.0-or-later
package adidoo.mateview.ui.components

import adidoo.mateview.data.repo.GeoPoint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** OpenStreetMap raster tiles: free, no key, attribution required. */
private const val OSM_STYLE = """{
  "version": 8,
  "sources": {
    "osm": {
      "type": "raster",
      "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
      "tileSize": 256,
      "maxzoom": 19,
      "attribution": "© OpenStreetMap contributors"
    }
  },
  "layers": [{ "id": "osm", "type": "raster", "source": "osm" }]
}"""

/**
 * A small static map: the route of a drive, or a single place. Gestures are
 * off so the page keeps scrolling over it.
 */
@Composable
fun TrackMap(
    track: List<GeoPoint>,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF4C8DF6),
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }

    // MapView has its own lifecycle, which must follow the screen's.
    DisposableEffect(lifecycle, mapView) {
        var started = false
        var resumed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (!started) { mapView.onStart(); started = true }
                Lifecycle.Event.ON_RESUME -> if (!resumed) { mapView.onResume(); resumed = true }
                Lifecycle.Event.ON_PAUSE -> if (resumed) { mapView.onPause(); resumed = false }
                Lifecycle.Event.ON_STOP -> if (started) { mapView.onStop(); started = false }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (resumed) mapView.onPause()
            if (started) mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView, track) {
        if (track.isEmpty()) return@LaunchedEffect
        mapView.getMapAsync { map ->
            map.uiSettings.setAllGesturesEnabled(false)
            map.uiSettings.isLogoEnabled = false
            map.uiSettings.isAttributionEnabled = false
            map.setStyle(Style.Builder().fromJson(OSM_STYLE)) { style ->
                val points = track.map { Point.fromLngLat(it.longitude, it.latitude) }
                val ends = listOf(points.first(), points.last()).distinct()
                style.addSource(
                    GeoJsonSource("ends", FeatureCollection.fromFeatures(ends.map { Feature.fromGeometry(it) })),
                )
                if (points.size > 1) {
                    style.addSource(GeoJsonSource("route", Feature.fromGeometry(LineString.fromLngLats(points))))
                    style.addLayer(
                        LineLayer("route", "route").withProperties(
                            PropertyFactory.lineColor(color.toArgb()),
                            PropertyFactory.lineWidth(4f),
                            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                        ),
                    )
                }
                style.addLayer(
                    CircleLayer("ends", "ends").withProperties(
                        PropertyFactory.circleColor(color.toArgb()),
                        PropertyFactory.circleRadius(6f),
                        PropertyFactory.circleStrokeColor(Color.White.toArgb()),
                        PropertyFactory.circleStrokeWidth(2f),
                    ),
                )
                val padding = (32 * context.resources.displayMetrics.density).toInt()
                val camera = if (points.size == 1 || track.distinct().size == 1) {
                    CameraUpdateFactory.newLatLngZoom(LatLng(track.first().latitude, track.first().longitude), 15.0)
                } else {
                    val bounds = LatLngBounds.Builder()
                        .includes(track.map { LatLng(it.latitude, it.longitude) })
                        .build()
                    CameraUpdateFactory.newLatLngBounds(bounds, padding)
                }
                map.moveCamera(camera)
            }
        }
    }

    Box(modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp))) {
        AndroidView(factory = { mapView }, modifier = Modifier.matchParentSize())
        // Required by the OpenStreetMap tile usage policy.
        Text(
            "© OpenStreetMap",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Black,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .background(Color.White.copy(alpha = 0.75f))
                .padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}
