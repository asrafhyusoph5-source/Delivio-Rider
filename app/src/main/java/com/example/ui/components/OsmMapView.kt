package com.example.ui.components

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
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
import com.example.data.models.OsrmRouteResult
import com.example.data.models.OsrmRouteService
import com.example.data.models.RouteStop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.Polygon

@Composable
fun OsmMapView(
    riderLat: Double,
    riderLng: Double,
    driverHeading: Float = 0f,
    batchStops: List<RouteStop>,
    focusedStop: RouteStop? = null,
    recenterTrigger: Int = 0,
    sheetHeightFraction: Float = 0.28f,
    isSatelliteLayer: Boolean = false,
    showBusyAreas: Boolean = false,
    busyGridCells: List<com.example.data.models.BusyGridCell> = emptyList(),
    onRouteCalculated: (OsrmRouteResult) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }

    // Remaining (unfinished) stops
    val remainingStops = remember(batchStops) {
        batchStops.filter { !it.isFinished }
    }

    var routeResult by remember { mutableStateOf<OsrmRouteResult?>(null) }
    var finishedLegPoints by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
    var tappedPinLabel by remember { mutableStateOf<String?>(null) }
    var lastFetchedRemainingStopIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var lastFetchTime by remember { mutableLongStateOf(0L) }
    var lastOffRouteRerouteTime by remember { mutableLongStateOf(0L) }

    // Init osmdroid config
    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
    }

    // Auto-dismiss tapped pin label after 3 seconds
    LaunchedEffect(tappedPinLabel) {
        if (tappedPinLabel != null) {
            kotlinx.coroutines.delay(3000)
            tappedPinLabel = null
        }
    }

    // Check if remaining stops changed -> store previous current leg as finished leg
    val remainingIds = remember(remainingStops) {
        remainingStops.map { "${it.orderId}_${it.type.name}" }
    }

    // Route calculation with OSRM (real roads driving profile)
    // Limits:
    // - Request a new route at most once every 5 seconds (plus when a stop is added or finished)
    // - Re-route when off-route (>40m) no more often than every 15 seconds
    LaunchedEffect(riderLat, riderLng, remainingIds) {
        if (riderLat == 0.0 || riderLng == 0.0 || remainingStops.isEmpty()) {
            routeResult = null
            finishedLegPoints = emptyList()
            return@LaunchedEffect
        }

        val now = System.currentTimeMillis()
        val currentPoints = routeResult?.currentLegPoints ?: emptyList()
        val isOffRoute = OsrmRouteService.isOffRoute(riderLat, riderLng, currentPoints, thresholdMeters = 40.0)
        val stopsChanged = remainingIds != lastFetchedRemainingStopIds

        val shouldFetch = when {
            stopsChanged -> true // Stop added or finished -> immediate
            routeResult == null -> true // Initial load -> immediate
            isOffRoute -> (now - lastOffRouteRerouteTime >= 15000L) // Off-route: at most once every 15s
            else -> (now - lastFetchTime >= 5000L) // Regular request / retry: at most once every 5s
        }

        if (shouldFetch) {
            if (isOffRoute) {
                lastOffRouteRerouteTime = now
            }
            lastFetchTime = now

            // If a stop finished, append previously finished leg points to finishedLegPoints
            if (stopsChanged && lastFetchedRemainingStopIds.isNotEmpty() && currentPoints.isNotEmpty()) {
                finishedLegPoints = (finishedLegPoints + currentPoints).takeLast(100)
            }

            lastFetchedRemainingStopIds = remainingIds

            withContext(Dispatchers.IO) {
                val result = OsrmRouteService.fetchRoute(
                    riderLat = riderLat,
                    riderLng = riderLng,
                    remainingStops = remainingStops
                )
                routeResult = result
                withContext(Dispatchers.Main) {
                    onRouteCalculated(result)
                }
            }
        }
    }

    // Focus on specific stop if requested (e.g. user tapped stop in timeline)
    LaunchedEffect(focusedStop) {
        focusedStop?.let { stop ->
            mapViewInstance?.let { map ->
                map.controller.animateTo(GeoPoint(stop.lat, stop.lng))
                map.controller.setZoom(17.0)
            }
        }
    }

    // Camera fitting: center route above the bottom sheet
    LaunchedEffect(
        sheetHeightFraction,
        recenterTrigger,
        remainingStops.size,
        routeResult?.currentLegPoints?.size
    ) {
        mapViewInstance?.let { map ->
            val points = mutableListOf<GeoPoint>()
            if (riderLat != 0.0 && riderLng != 0.0) {
                points.add(GeoPoint(riderLat, riderLng))
            }
            routeResult?.currentLegPoints?.let { pts ->
                points.addAll(pts)
            }
            if (points.isEmpty()) {
                remainingStops.firstOrNull()?.let {
                    points.add(GeoPoint(it.lat, it.lng))
                }
            }

            if (points.isNotEmpty()) {
                val minLat = points.minOf { it.latitude }
                val maxLat = points.maxOf { it.latitude }
                val minLng = points.minOf { it.longitude }
                val maxLng = points.maxOf { it.longitude }

                val latSpan = (maxLat - minLat).coerceAtLeast(0.0035)
                val lngSpan = (maxLng - minLng).coerceAtLeast(0.0035)

                // Offset center downwards so route is centered in visible area above sheet
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
                    val initPoint = if (riderLat != 0.0 && riderLng != 0.0) {
                        GeoPoint(riderLat, riderLng)
                    } else {
                        GeoPoint(9.7392, 118.7353) // Puerto Princesa center
                    }
                    controller.setCenter(initPoint)
                    mapViewInstance = this
                }
            },
            update = { map ->
                val targetTileSource = if (isSatelliteLayer) TileSourceFactory.USGS_SAT else TileSourceFactory.MAPNIK
                if (map.tileProvider.tileSource != targetTileSource) {
                    map.setTileSource(targetTileSource)
                }

                map.overlays.clear()

                // 0. Demand Heat Map: Shaded ~500m grid areas by recent order count (Busy areas)
                if (showBusyAreas && busyGridCells.isNotEmpty()) {
                    busyGridCells.forEach { cell ->
                        val poly = Polygon(map).apply {
                            val pts = listOf(
                                GeoPoint(cell.minLat, cell.minLng),
                                GeoPoint(cell.maxLat, cell.minLng),
                                GeoPoint(cell.maxLat, cell.maxLng),
                                GeoPoint(cell.minLat, cell.maxLng)
                            )
                            points = pts
                            val (fillColor, strokeColor) = when {
                                cell.orderCount >= 4 -> Pair(
                                    Color.argb(130, 220, 20, 60),
                                    Color.argb(220, 220, 20, 60)
                                )
                                cell.orderCount >= 2 -> Pair(
                                    Color.argb(105, 255, 87, 34),
                                    Color.argb(200, 255, 87, 34)
                                )
                                else -> Pair(
                                    Color.argb(80, 255, 152, 0),
                                    Color.argb(180, 255, 152, 0)
                                )
                            }
                            fillPaint.color = fillColor
                            outlinePaint.color = strokeColor
                            outlinePaint.strokeWidth = 3f
                            title = "Busy area: ${cell.orderCount} orders in last 60m"
                            setOnClickListener { _, _, _ ->
                                tappedPinLabel = "🔥 Busy area: ${cell.orderCount} orders in last 60m"
                                true
                            }
                        }
                        map.overlays.add(poly)

                        val badgeMarker = Marker(map).apply {
                            position = GeoPoint(cell.centerLat, cell.centerLng)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            icon = createBusyAreaBadgeDrawable(context, cell.orderCount)
                            title = "🔥 ${cell.orderCount} orders"
                            infoWindow = null
                            setOnMarkerClickListener { _, _ ->
                                tappedPinLabel = "🔥 Busy area: ${cell.orderCount} orders in last 60m"
                                true
                            }
                        }
                        map.overlays.add(badgeMarker)
                    }
                }

                val result = routeResult

                // 1. Finished legs: Grey line
                if (finishedLegPoints.size >= 2) {
                    val finishedPoly = Polyline(map).apply {
                        setPoints(finishedLegPoints)
                        outlinePaint.color = Color.parseColor("#9E9E9E") // Grey
                        outlinePaint.strokeWidth = 9f
                        outlinePaint.strokeCap = Paint.Cap.ROUND
                    }
                    map.overlays.add(finishedPoly)
                }

                // 2. Future remaining legs (legs after the current stop): Lighter blue line
                if (result != null && result.futureLegPoints.size >= 2) {
                    val futurePoly = Polyline(map).apply {
                        setPoints(result.futureLegPoints)
                        outlinePaint.color = Color.parseColor("#64B5F6") // Soft Sky Blue
                        outlinePaint.strokeWidth = 10f
                        outlinePaint.strokeCap = Paint.Cap.ROUND
                    }
                    map.overlays.add(futurePoly)
                }

                // 3. Current leg: Bright blue line over real roads
                if (result != null && result.currentLegPoints.size >= 2) {
                    val currentPoly = Polyline(map).apply {
                        setPoints(result.currentLegPoints)
                        outlinePaint.color = Color.parseColor("#1E88E5") // Bright Blue
                        outlinePaint.strokeWidth = 14f
                        outlinePaint.strokeCap = Paint.Cap.ROUND
                    }
                    map.overlays.add(currentPoly)
                }

                // 4. Midpoint Bubble on current leg: "X min - Y km"
                if (result != null && result.midpointCurrentLeg != null && result.currentLegDistanceKm > 0.0) {
                    val bubbleText = "${result.currentLegEtaMinutes} min • ${String.format(java.util.Locale.US, "%.1f", result.currentLegDistanceKm)} km"
                    val bubbleMarker = Marker(map).apply {
                        position = result.midpointCurrentLeg
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = createRouteBubbleDrawable(context, bubbleText)
                        infoWindow = null
                    }
                    map.overlays.add(bubbleMarker)
                }

                // 5. Driver Location: Blue arrow that follows heading
                if (riderLat != 0.0 && riderLng != 0.0) {
                    val riderMarker = Marker(map).apply {
                        position = GeoPoint(riderLat, riderLng)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = createHeadingArrowDrawable(context, driverHeading)
                        title = "My Position"
                        infoWindow = null
                        setOnMarkerClickListener { m, _ ->
                            tappedPinLabel = m.title ?: "My Position"
                            true
                        }
                    }
                    map.overlays.add(riderMarker)
                }

                // 6. Remaining Stop Pins:
                // Merchant stops are GREEN numbered pins (1, 2, 3...)
                // Customer stops are RED numbered pins continuing the numbering (e.g. 1 and 2 green, 3 and 4 red)
                // Finished stop pins disappear!
                remainingStops.forEach { stop ->
                    if (stop.lat != 0.0 && stop.lng != 0.0) {
                        val pinMarker = Marker(map).apply {
                            position = GeoPoint(stop.lat, stop.lng)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            icon = createNumberedPinDrawable(context, stop.stopNumber, stop.isMerchant)
                            title = "Stop ${stop.stopNumber}: ${stop.name} (${if (stop.isMerchant) "Pickup" else "Drop-off"})"
                            infoWindow = null
                            setOnMarkerClickListener { m, _ ->
                                tappedPinLabel = m.title ?: stop.name
                                true
                            }
                        }
                        map.overlays.add(pinMarker)
                    }
                }

                map.invalidate()
            }
        )

        // Route Fallback Direct warning banner if OSRM failed (429, 5xx, or timeout)
        AnimatedVisibility(
            visible = routeResult?.isFallbackDirect == true,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 80.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = androidx.compose.ui.graphics.Color(0xFFFFC107),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = androidx.compose.ui.graphics.Color(0xFF212121),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Route unavailable",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = androidx.compose.ui.graphics.Color(0xFF212121)
                    )
                }
            }
        }

        // Attribution: "Routing by OSRM, © OpenStreetMap contributors"
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = androidx.compose.ui.graphics.Color(0xCCFFFFFF),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 6.dp, bottom = 4.dp)
        ) {
            Text(
                text = "Routing by OSRM, © OpenStreetMap contributors",
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                color = androidx.compose.ui.graphics.Color(0xFF333333),
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
            )
        }

        // Tapped pin toast label (auto-dismisses after 3 seconds)
        tappedPinLabel?.let { label ->
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = androidx.compose.ui.graphics.Color(0xDD1E1E1E),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 110.dp)
            ) {
                Text(
                    text = label,
                    color = androidx.compose.ui.graphics.Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                )
            }
        }
    }
}

/**
 * Creates green pin for merchants or red pin for customers, with white bold number.
 */
private fun createNumberedPinDrawable(context: Context, number: Int, isMerchant: Boolean): BitmapDrawable {
    val width = 72
    val height = 86
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val pinColor = if (isMerchant) {
        Color.parseColor("#2E7D32") // Deep Green
    } else {
        Color.parseColor("#E53935") // Vivid Red
    }

    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#40000000")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(width / 2f + 2f, 32f + 2f, 26f, shadowPaint)

    // Teardrop pointer path
    val pointerPath = Path().apply {
        moveTo(width / 2f, height.toFloat() - 4f)
        lineTo(width / 2f - 14f, 44f)
        lineTo(width / 2f + 14f, 44f)
        close()
    }
    val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = pinColor
        style = Paint.Style.FILL
    }
    canvas.drawPath(pointerPath, pointerPaint)

    // Circle head
    val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = pinColor
        style = Paint.Style.FILL
    }
    canvas.drawCircle(width / 2f, 30f, 26f, circlePaint)

    // White border
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    canvas.drawCircle(width / 2f, 30f, 26f, borderPaint)

    // Centered number text
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = if (number >= 10) 24f else 28f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    canvas.drawText("$number", width / 2f, 40f, textPaint)

    return BitmapDrawable(context.resources, bitmap)
}

/**
 * Creates blue directional navigation arrow that follows driver heading.
 */
private fun createHeadingArrowDrawable(context: Context, headingDeg: Float): BitmapDrawable {
    val size = 76
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    canvas.save()
    // Rotate canvas around center by heading
    canvas.rotate(headingDeg, size / 2f, size / 2f)

    // Outer glow / halo
    val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#442196F3")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2f, haloPaint)

    // Directional chevron arrow
    val arrowPath = Path().apply {
        moveTo(size / 2f, 6f) // Tip pointing up
        lineTo(size - 14f, size - 14f)
        lineTo(size / 2f, size - 24f)
        lineTo(14f, size - 14f)
        close()
    }

    val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E88E5") // Bright Blue
        style = Paint.Style.FILL
    }
    canvas.drawPath(arrowPath, arrowPaint)

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeJoin = Paint.Join.ROUND
    }
    canvas.drawPath(arrowPath, borderPaint)

    canvas.restore()
    return BitmapDrawable(context.resources, bitmap)
}

/**
 * Creates bubble for current leg showing "X min • Y km".
 */
private fun createRouteBubbleDrawable(context: Context, text: String): BitmapDrawable {
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 24f
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }

    val textBounds = Rect()
    textPaint.getTextBounds(text, 0, text.length, textBounds)
    val paddingH = 24
    val paddingV = 16
    val width = textBounds.width() + paddingH * 2
    val height = textBounds.height() + paddingV * 2

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#DD212121") // Dark semi-transparent
        style = Paint.Style.FILL
    }
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#88FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    val rect = RectF(2f, 2f, width - 2f, height - 2f)
    canvas.drawRoundRect(rect, height / 2f, height / 2f, bgPaint)
    canvas.drawRoundRect(rect, height / 2f, height / 2f, borderPaint)

    val textY = (height / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
    canvas.drawText(text, width / 2f, textY, textPaint)

    return BitmapDrawable(context.resources, bitmap)
}

/**
 * Creates a badge icon showing the order count for busy areas.
 */
private fun createBusyAreaBadgeDrawable(context: Context, count: Int): BitmapDrawable {
    val text = "🔥 $count"
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 30f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    val bounds = Rect()
    textPaint.getTextBounds(text, 0, text.length, bounds)
    val width = bounds.width() + 32
    val height = bounds.height() + 22
    val bitmap = Bitmap.createBitmap(width.coerceAtLeast(48), height.coerceAtLeast(34), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = when {
            count >= 4 -> Color.parseColor("#D32F2F")
            count >= 2 -> Color.parseColor("#FF5722")
            else -> Color.parseColor("#FF9800")
        }
        style = Paint.Style.FILL
    }
    val rect = RectF(2f, 2f, bitmap.width - 2f, bitmap.height - 2f)
    canvas.drawRoundRect(rect, 14f, 14f, bgPaint)

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    canvas.drawRoundRect(rect, 14f, 14f, borderPaint)

    val textY = (bitmap.height / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
    canvas.drawText(text, bitmap.width / 2f, textY, textPaint)

    return BitmapDrawable(context.resources, bitmap)
}
