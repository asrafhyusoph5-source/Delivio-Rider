package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlin.math.roundToInt

@Composable
fun OsmMapView(
    riderLat: Double,
    riderLng: Double,
    activeOrders: List<Order>,
    currentActiveOrder: Order?,
    recenterTrigger: Int,
    sheetHeightFraction: Float = 0.28f,
    isSatelliteLayer: Boolean = false,
    onRouteChange: (Double, Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }
    var routePoints by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }

    var routeDistanceKm by remember { mutableDoubleStateOf(0.0) }
    var routeEtaMinutes by remember { mutableIntStateOf(0) }
    var tappedPinLabel by remember { mutableStateOf<String?>(null) }

    // Auto-dismiss pin label after 3 seconds
    LaunchedEffect(tappedPinLabel) {
        if (tappedPinLabel != null) {
            kotlinx.coroutines.delay(3000)
            tappedPinLabel = null
        }
    }

    // Init osmdroid config once
    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
    }

    // Determine target location for navigation & polyline (pickup first, then drop-off once revealed; for taxi, pickup then current stop)
    val targetGeoPoint: GeoPoint? = remember(
        currentActiveOrder,
        currentActiveOrder?.riderStep,
        currentActiveOrder?.merchantLat,
        currentActiveOrder?.customerLat,
        currentActiveOrder?.completedStops
    ) {
        val o = currentActiveOrder ?: return@remember null
        if (o.isTaxi) {
            val step = o.riderStep.lowercase().trim()
            if (step in listOf<String>(OrderStepConstants.STEP_TAXI_TO_PICKUP, OrderStepConstants.STEP_TAXI_AT_PICKUP)) {
                if (o.merchantLat != 0.0 && o.merchantLng != 0.0) GeoPoint(o.merchantLat, o.merchantLng) else null
            } else {
                val targetStop = o.currentTargetStop
                if (targetStop != null && targetStop.lat != 0.0 && targetStop.lng != 0.0) {
                    GeoPoint(targetStop.lat, targetStop.lng)
                } else if (o.customerLat != 0.0 && o.customerLng != 0.0) {
                    GeoPoint(o.customerLat, o.customerLng)
                } else null
            }
        } else {
            val addressRevealed = OrderStepConstants.isCustomerAddressRevealed(o.serviceType, o.riderStep)
            if (addressRevealed) {
                // Heading to customer / recipient
                if (o.customerLat != 0.0 && o.customerLng != 0.0) GeoPoint(o.customerLat, o.customerLng) else null
            } else {
                // Heading to merchant / sender / vendor
                if (o.merchantLat != 0.0 && o.merchantLng != 0.0) GeoPoint(o.merchantLat, o.merchantLng) else null
            }
        }
    }

    // Fetch OSRM Polyline with straight-line fallback, calculating distance & ETA
    LaunchedEffect(riderLat, riderLng, targetGeoPoint) {
        if (riderLat == 0.0 || riderLng == 0.0 || targetGeoPoint == null) {
            routePoints = emptyList()
            routeDistanceKm = 0.0
            routeEtaMinutes = 0
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            var fetchedDistKm = 0.0
            var fetchedEta = 0
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
                    val distMeters = firstRoute?.optDouble("distance", 0.0) ?: 0.0
                    val durSeconds = firstRoute?.optDouble("duration", 0.0) ?: 0.0
                    if (distMeters > 0) fetchedDistKm = distMeters / 1000.0
                    if (durSeconds > 0) fetchedEta = Math.round(durSeconds / 60.0).toInt().coerceAtLeast(1)

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
            if (fetched != null && fetchedDistKm > 0) {
                routePoints = fetched
                routeDistanceKm = fetchedDistKm
                routeEtaMinutes = fetchedEta
            } else {
                val r = 6371.0
                val dLat = Math.toRadians(targetGeoPoint.latitude - riderLat)
                val dLon = Math.toRadians(targetGeoPoint.longitude - riderLng)
                val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                        Math.cos(Math.toRadians(riderLat)) * Math.cos(Math.toRadians(targetGeoPoint.latitude)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2)
                val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
                val straightDist = r * c
                routePoints = listOf(GeoPoint(riderLat, riderLng), targetGeoPoint)
                routeDistanceKm = straightDist
                routeEtaMinutes = Math.round(straightDist / 25.0 * 60).toInt().coerceAtLeast(1)
            }
        }
    }

    // Report route changes to caller
    LaunchedEffect(routeDistanceKm, routeEtaMinutes) {
        onRouteChange(routeDistanceKm, routeEtaMinutes)
    }

    // Fit route in the area above the sheet whenever sheet state, step, or recenter changes
    LaunchedEffect(sheetHeightFraction, currentActiveOrder?.riderStep, routePoints, recenterTrigger) {
        mapViewInstance?.let { map ->
            val points = if (routePoints.size >= 2) routePoints
            else if (targetGeoPoint != null && riderLat != 0.0 && riderLng != 0.0) listOf(GeoPoint(riderLat, riderLng), targetGeoPoint)
            else if (riderLat != 0.0 && riderLng != 0.0) listOf(GeoPoint(riderLat, riderLng))
            else emptyList()

            if (points.isNotEmpty()) {
                val minLat = points.minOf { it.latitude }
                val maxLat = points.maxOf { it.latitude }
                val minLng = points.minOf { it.longitude }
                val maxLng = points.maxOf { it.longitude }

                val latSpan = (maxLat - minLat).coerceAtLeast(0.004)
                val lngSpan = (maxLng - minLng).coerceAtLeast(0.004)

                // Offset center downwards so route is centered in the visible area above the sheet
                val centerLat = (minLat + maxLat) / 2.0 - (latSpan * sheetHeightFraction * 0.45)
                val centerLng = (minLng + maxLng) / 2.0

                val maxSpan = maxOf(latSpan / (1.0 - sheetHeightFraction).coerceAtLeast(0.35), lngSpan)
                val targetZoom = when {
                    maxSpan > 0.15 -> 12.0
                    maxSpan > 0.08 -> 13.0
                    maxSpan > 0.04 -> 14.0
                    maxSpan > 0.02 -> 15.0
                    maxSpan > 0.01 -> 16.0
                    else -> 16.5
                }

                map.controller.animateTo(GeoPoint(centerLat, centerLng))
                map.controller.setZoom(targetZoom)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
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
                // Switch tile layer if requested
                val targetTileSource = if (isSatelliteLayer) TileSourceFactory.USGS_SAT else TileSourceFactory.MAPNIK
                if (map.tileProvider.tileSource != targetTileSource) {
                    map.setTileSource(targetTileSource)
                }

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
                        infoWindow = null // Remove large address bubble
                        setOnMarkerClickListener { m, _ ->
                            tappedPinLabel = m.title ?: "My Location"
                            true
                        }
                    }
                    map.overlays.add(riderMarker)
                }

                // 3. Pickup Pin
                currentActiveOrder?.let { order ->
                    if (order.isTaxi) {
                        // Taxi pickup pin is green and disappears once the trip starts (step advances past at_pickup)
                        val isPickupStage = order.riderStep.lowercase().trim() in listOf<String>(
                            OrderStepConstants.STEP_TAXI_TO_PICKUP,
                            OrderStepConstants.STEP_TAXI_AT_PICKUP
                        )
                        if (isPickupStage && order.merchantLat != 0.0 && order.merchantLng != 0.0) {
                            val pickupMarker = Marker(map).apply {
                                position = GeoPoint(order.merchantLat, order.merchantLng)
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                icon = createTaxiPickupPinDrawable(context)
                                title = "Pickup (Stop 1): ${order.merchantAddress.ifBlank { order.merchantName }}"
                                infoWindow = null
                                setOnMarkerClickListener { m, _ ->
                                    tappedPinLabel = m.title ?: "Pickup"
                                    true
                                }
                            }
                            map.overlays.add(pickupMarker)
                        }
                    } else {
                        val isRevealed = OrderStepConstants.isCustomerAddressRevealed(order.serviceType, order.riderStep)
                        if (!isRevealed && order.merchantLat != 0.0 && order.merchantLng != 0.0) {
                            val pickupMarker = Marker(map).apply {
                                position = GeoPoint(order.merchantLat, order.merchantLng)
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                icon = createPickupPinDrawable(context, order.serviceType, order.merchantName)
                                title = "Pickup: ${order.merchantName}"
                                infoWindow = null // Remove large address bubble
                                setOnMarkerClickListener { m, _ ->
                                    tappedPinLabel = m.title ?: order.merchantName
                                    true
                                }
                            }
                            map.overlays.add(pickupMarker)
                        }
                    }
                }

                // 4. Drop-off / Next Stops Pins
                currentActiveOrder?.let { order ->
                    if (order.isTaxi) {
                        // For taxi: drop-off and every next stop are red pins numbered 2, 3, 4...
                        // A stop pin disappears when it is completed (in completedStops)
                        order.allTaxiStops.forEach { stop ->
                            if (!order.completedStops.contains(stop.stopNumber) && stop.lat != 0.0 && stop.lng != 0.0) {
                                val stopMarker = Marker(map).apply {
                                    position = GeoPoint(stop.lat, stop.lng)
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                    icon = createDropoffPinDrawable(context, stop.stopNumber.toString())
                                    title = "Stop ${stop.stopNumber}: ${stop.address.ifBlank { stop.area }}"
                                    infoWindow = null
                                    setOnMarkerClickListener { m, _ ->
                                        tappedPinLabel = m.title ?: "Stop ${stop.stopNumber}"
                                        true
                                    }
                                }
                                map.overlays.add(stopMarker)
                            }
                        }
                    }
                }

                // Non-taxi drop-off pins
                activeOrders.filter { !it.isTaxi }.forEachIndexed { index, order ->
                    val orderNumberLabel = (index + 1).toString() // "1" or "2"
                    val dropOffRevealed = OrderStepConstants.isCustomerAddressRevealed(order.serviceType, order.riderStep)
                    if (dropOffRevealed && order.customerLat != 0.0 && order.customerLng != 0.0) {
                        val dropPin = Marker(map).apply {
                            position = GeoPoint(order.customerLat, order.customerLng)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            icon = createDropoffPinDrawable(context, orderNumberLabel)
                            title = "Drop-off $orderNumberLabel: ${order.customerAddress.ifBlank { order.customerName }}"
                            infoWindow = null // Remove large address bubble
                            setOnMarkerClickListener { m, _ ->
                                tappedPinLabel = m.title ?: order.customerName
                                true
                            }
                        }
                        map.overlays.add(dropPin)
                    }
                }

                map.invalidate()
            }
        )

        // Tapping a pin shows a small label that disappears after 3 seconds
        tappedPinLabel?.let { label ->
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = androidx.compose.ui.graphics.Color(0xDD1E1E1E),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 96.dp)
            ) {
                Text(
                    text = label,
                    color = androidx.compose.ui.graphics.Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }
    }
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

private fun createPickupPinDrawable(context: Context, serviceType: String, label: String): BitmapDrawable {
    val width = 64
    val height = 74
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val (pinColor, letter) = when (serviceType.lowercase().trim()) {
        OrderStepConstants.SERVICE_PARCEL -> Color.parseColor("#E65100") to "S" // Orange / Sender
        OrderStepConstants.SERVICE_PABILI -> Color.parseColor("#7B1FA2") to "V" // Purple / Vendor
        else -> Color.parseColor("#1E88E5") to "M" // Delivio Blue / Merchant
    }

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = pinColor
        style = Paint.Style.FILL
    }
    canvas.drawCircle(width / 2f, 30f, 26f, paint)

    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    canvas.drawCircle(width / 2f, 30f, 26f, border)

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 24f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    canvas.drawText(letter, width / 2f, 38f, textPaint)

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

private fun createTaxiPickupPinDrawable(context: Context): BitmapDrawable {
    val width = 64
    val height = 74
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Green pin for taxi passenger pickup
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2E7D32") // Green
        style = Paint.Style.FILL
    }
    canvas.drawCircle(width / 2f, 30f, 28f, paint)

    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    canvas.drawCircle(width / 2f, 30f, 28f, border)

    // Number text "1"
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 30f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    canvas.drawText("1", width / 2f, 40f, textPaint)

    return BitmapDrawable(context.resources, bitmap)
}
