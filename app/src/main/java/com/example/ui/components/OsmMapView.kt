package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.constants.OrderStepConstants
import com.example.data.models.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun OsmMapView(
    riderLat: Double,
    riderLng: Double,
    activeOrders: List<Order>,
    currentActiveOrder: Order?,
    recenterTrigger: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }
    var routePoints by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }

    // Init osmdroid config once
    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
    }

    // Determine target location for navigation & polyline
    val targetGeoPoint: GeoPoint? = remember(currentActiveOrder) {
        val o = currentActiveOrder ?: return@remember null
        val isAtOrPastPickup = o.riderStep != OrderStepConstants.STEP_HEADING_TO_MERCHANT &&
                o.riderStep != OrderStepConstants.STEP_ARRIVED_AT_MERCHANT
        val dropOffRevealed = o.riderStep == OrderStepConstants.STEP_ARRIVED_AT_CUSTOMER

        if (!isAtOrPastPickup) {
            // Heading to merchant / pickup
            if (o.merchantLat != 0.0 && o.merchantLng != 0.0) GeoPoint(o.merchantLat, o.merchantLng) else null
        } else if (dropOffRevealed) {
            // Heading to customer / recipient
            if (o.customerLat != 0.0 && o.customerLng != 0.0) GeoPoint(o.customerLat, o.customerLng) else null
        } else {
            null
        }
    }

    // Fetch OSRM Polyline with straight-line fallback
    LaunchedEffect(riderLat, riderLng, targetGeoPoint) {
        if (riderLat == 0.0 || riderLng == 0.0 || targetGeoPoint == null) {
            routePoints = emptyList()
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            val fetched = try {
                val urlStr = "https://router.project-osrm.org/route/v1/driving/$riderLng,$riderLat;${targetGeoPoint.longitude},${targetGeoPoint.latitude}?overview=full&geometries=geojson"
                val conn = URL(urlStr).openConnection() as HttpURLConnection
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "DelivioRiderApp/1.0")

                if (conn.responseCode == 200) {
                    val stream = conn.inputStream
                    val jsonStr = stream.bufferedReader().use { it.readText() }
                    val json = JSONObject(jsonStr)
                    val routes = json.optJSONArray("routes")
                    val firstRoute = routes?.optJSONObject(0)
                    val geometry = firstRoute?.optJSONObject("geometry")
                    val coords = geometry?.optJSONArray("coordinates")
                    val points = mutableListOf<GeoPoint>()
                    if (coords != null) {
                        for (i in 0 until coords.length()) {
                            val coord = coords.getJSONArray(i)
                            val lon = coord.getDouble(0)
                            val lat = coord.getDouble(1)
                            points.add(GeoPoint(lat, lon))
                        }
                    }
                    if (points.isNotEmpty()) points else null
                } else null
            } catch (_: Exception) {
                null
            }

            // Fallback straight-line
            routePoints = fetched ?: listOf(GeoPoint(riderLat, riderLng), targetGeoPoint)
        }
    }

    // Recenter map
    LaunchedEffect(recenterTrigger, riderLat, riderLng) {
        mapViewInstance?.let { map ->
            if (riderLat != 0.0 && riderLng != 0.0) {
                map.controller.animateTo(GeoPoint(riderLat, riderLng))
                if (map.zoomLevelDouble < 14.0) {
                    map.controller.setZoom(16.5)
                }
            }
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                isTilesScaledToDpi = true
                controller.setZoom(16.5)
                val initPoint = if (riderLat != 0.0 && riderLng != 0.0) GeoPoint(riderLat, riderLng) else GeoPoint(9.7392, 118.7353) // Puerto Princesa center
                controller.setCenter(initPoint)
                mapViewInstance = this
            }
        },
        update = { map ->
            // Clear current overlays
            map.overlays.clear()

            // 1. Draw route polyline
            if (routePoints.size >= 2) {
                val poly = Polyline(map).apply {
                    setPoints(routePoints)
                    outlinePaint.color = Color.parseColor("#FFC107") // Delivio Yellow
                    outlinePaint.strokeWidth = 12f
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                }
                map.overlays.add(poly)
            }

            // 2. Rider Live Location Marker
            if (riderLat != 0.0 && riderLng != 0.0) {
                val riderMarker = Marker(map).apply {
                    position = GeoPoint(riderLat, riderLng)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = createRiderPinDrawable(context)
                    title = "My Location"
                }
                map.overlays.add(riderMarker)
            }

            // 3. Pickup / Merchant Pin (for current active order if not yet picked up)
            currentActiveOrder?.let { order ->
                val notPickedUp = order.riderStep == OrderStepConstants.STEP_HEADING_TO_MERCHANT ||
                        order.riderStep == OrderStepConstants.STEP_ARRIVED_AT_MERCHANT
                if (notPickedUp && order.merchantLat != 0.0 && order.merchantLng != 0.0) {
                    val pickupMarker = Marker(map).apply {
                        position = GeoPoint(order.merchantLat, order.merchantLng)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        icon = createMerchantPinDrawable(context, order.merchantName)
                        title = "Pickup: ${order.merchantName}"
                    }
                    map.overlays.add(pickupMarker)
                }
            }

            // 4. Numbered Drop-off pins "1" and "2"
            // Rule: appear on the map for each order ONLY once its drop-off address is revealed (stage 5: arrived_at_customer).
            // Numbering follows acceptance order in activeOrders (first is 1, second is 2).
            activeOrders.forEachIndexed { index, order ->
                val orderNumberLabel = (index + 1).toString() // "1" or "2"
                val dropOffRevealed = order.riderStep == OrderStepConstants.STEP_ARRIVED_AT_CUSTOMER
                if (dropOffRevealed && order.customerLat != 0.0 && order.customerLng != 0.0) {
                    val dropPin = Marker(map).apply {
                        position = GeoPoint(order.customerLat, order.customerLng)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        icon = createDropoffPinDrawable(context, orderNumberLabel)
                        title = "Drop-off $orderNumberLabel: ${order.customerAddress}"
                    }
                    map.overlays.add(dropPin)
                }
            }

            map.invalidate()
        }
    )
}

// Helpers for marker icons
private fun createRiderPinDrawable(context: Context): BitmapDrawable {
    val size = 56
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFC107") // Yellow circle
        style = Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 4, paint)

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 4, borderPaint)

    val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A1A1A")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f, 10f, centerPaint)

    return BitmapDrawable(context.resources, bitmap)
}

private fun createMerchantPinDrawable(context: Context, label: String): BitmapDrawable {
    val width = 64
    val height = 74
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E88E5") // Blue pin
        style = Paint.Style.FILL
    }
    canvas.drawCircle(width / 2f, 30f, 26f, paint)

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 24f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    canvas.drawText("P", width / 2f, 38f, textPaint)

    return BitmapDrawable(context.resources, bitmap)
}

private fun createDropoffPinDrawable(context: Context, number: String): BitmapDrawable {
    val width = 64
    val height = 74
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Red drop pin for customer destination
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E53935") // Red
        style = Paint.Style.FILL
    }
    canvas.drawCircle(width / 2f, 30f, 28f, paint)

    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    canvas.drawCircle(width / 2f, 30f, 28f, border)

    // Number text "1" or "2"
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 30f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    canvas.drawText(number, width / 2f, 40f, textPaint)

    return BitmapDrawable(context.resources, bitmap)
}
