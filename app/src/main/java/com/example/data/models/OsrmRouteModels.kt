package com.example.data.models

import com.example.data.constants.BatchConstants
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

const val ROUTING_BASE_URL = "https://router.project-osrm.org"

data class OsrmManeuver(
    val type: String = "turn",
    val modifier: String = "straight",
    val streetName: String = "",
    val instruction: String = "Continue straight",
    val distanceMeters: Double = 0.0
) {
    val distanceText: String
        get() = if (distanceMeters >= 1000) {
            String.format(Locale.US, "In %.1f km", distanceMeters / 1000.0)
        } else {
            "In ${distanceMeters.roundToInt().coerceAtLeast(10)} m"
        }
}

data class OsrmLeg(
    val distanceKm: Double,
    val durationMinutes: Int,
    val points: List<GeoPoint>,
    val steps: List<OsrmManeuver>
)

data class OsrmRouteResult(
    val fullPoints: List<GeoPoint>,
    val currentLegPoints: List<GeoPoint>,
    val futureLegPoints: List<GeoPoint>,
    val currentLegDistanceKm: Double,
    val currentLegEtaMinutes: Int,
    val totalRemainingDistanceKm: Double,
    val totalRemainingEtaMinutes: Int,
    val etaClockTime: String,
    val nextManeuver: OsrmManeuver?,
    val isFallbackDirect: Boolean = false,
    val fallbackNotice: String = "Route unavailable",
    val midpointCurrentLeg: GeoPoint? = null
)

object OsrmRouteService {

    private var lastWaypointsKey: String? = null
    private var lastCachedResult: OsrmRouteResult? = null

    fun clearCache() {
        lastWaypointsKey = null
        lastCachedResult = null
    }

    fun getCachedResult(key: String): OsrmRouteResult? {
        if (key == lastWaypointsKey && lastCachedResult != null && !lastCachedResult!!.isFallbackDirect) {
            return lastCachedResult
        }
        return null
    }

    /**
     * Checks if rider has deviated from the route polyline by more than 40 meters.
     */
    fun isOffRoute(
        riderLat: Double,
        riderLng: Double,
        routePoints: List<GeoPoint>,
        thresholdMeters: Double = 40.0
    ): Boolean {
        if (routePoints.isEmpty() || riderLat == 0.0 || riderLng == 0.0) return false
        val thresholdKm = thresholdMeters / 1000.0

        val minDistance = routePoints.minOfOrNull { pt ->
            BatchConstants.distanceKm(riderLat, riderLng, pt.latitude, pt.longitude)
        } ?: Double.MAX_VALUE

        return minDistance > thresholdKm
    }

    /**
     * Fetches route over real roads using OSRM driving profile.
     * Waypoints: rider position -> remaining stop 1 -> remaining stop 2 ...
     * If request fails (HTTP 429, 5xx, timeout, or network error),
     * falls back to direct straight lines with isFallbackDirect = true and fallbackNotice = "Route unavailable".
     */
    fun fetchRoute(
        riderLat: Double,
        riderLng: Double,
        remainingStops: List<RouteStop>
    ): OsrmRouteResult {
        if (remainingStops.isEmpty() || riderLat == 0.0 || riderLng == 0.0) {
            return OsrmRouteResult(
                fullPoints = emptyList(),
                currentLegPoints = emptyList(),
                futureLegPoints = emptyList(),
                currentLegDistanceKm = 0.0,
                currentLegEtaMinutes = 0,
                totalRemainingDistanceKm = 0.0,
                totalRemainingEtaMinutes = 0,
                etaClockTime = SimpleDateFormat("h:mm a", Locale.US).format(Date()),
                nextManeuver = null
            )
        }

        val waypoints = listOf(GeoPoint(riderLat, riderLng)) + remainingStops.map { GeoPoint(it.lat, it.lng) }
        val coordsParam = waypoints.joinToString(";") { "${String.format(Locale.US, "%.5f", it.longitude)},${String.format(Locale.US, "%.5f", it.latitude)}" }

        // Cache lookup: return cached result for same waypoints
        getCachedResult(coordsParam)?.let { cached ->
            return cached
        }

        val urlStr = "$ROUTING_BASE_URL/route/v1/driving/$coordsParam?overview=full&geometries=geojson&steps=true"

        try {
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Delivio Driver (Android; com.deliviorider.app)")

            if (conn.responseCode == 200) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonStr)
                val routes = json.optJSONArray("routes")
                val route = routes?.optJSONObject(0)

                if (route != null) {
                    val totalDistanceMeters = route.optDouble("distance", 0.0)
                    val totalDurationSeconds = route.optDouble("duration", 0.0)
                    val totalDistKm = totalDistanceMeters / 1000.0
                    val totalEtaMins = (totalDurationSeconds / 60.0).roundToInt().coerceAtLeast(1)

                    // Parse geometry
                    val geometry = route.optJSONObject("geometry")
                    val coords = geometry?.optJSONArray("coordinates")
                    val allPoints = mutableListOf<GeoPoint>()
                    if (coords != null) {
                        for (i in 0 until coords.length()) {
                            val coord = coords.getJSONArray(i)
                            allPoints.add(GeoPoint(coord.getDouble(1), coord.getDouble(0)))
                        }
                    }

                    // Parse legs
                    val legsArray = route.optJSONArray("legs")
                    val firstLeg = legsArray?.optJSONObject(0)
                    val firstLegDistM = firstLeg?.optDouble("distance", 0.0) ?: 0.0
                    val firstLegDurS = firstLeg?.optDouble("duration", 0.0) ?: 0.0
                    val currentLegDistKm = firstLegDistM / 1000.0
                    val currentLegEta = (firstLegDurS / 60.0).roundToInt().coerceAtLeast(1)

                    // Parse next maneuver from firstLeg steps
                    val stepsArray = firstLeg?.optJSONArray("steps")
                    var nextManeuver: OsrmManeuver? = null
                    if (stepsArray != null && stepsArray.length() > 0) {
                        // Usually step 0 is depart, step 1 is the next action
                        val targetStepIdx = if (stepsArray.length() > 1) 1 else 0
                        val stepObj = stepsArray.getJSONObject(targetStepIdx)
                        val street = stepObj.optString("name", "").trim()
                        val stepDist = stepObj.optDouble("distance", 0.0)
                        val manObj = stepObj.optJSONObject("maneuver")
                        val manType = manObj?.optString("type", "turn") ?: "turn"
                        val modifier = manObj?.optString("modifier", "straight") ?: "straight"

                        val instruction = buildInstruction(manType, modifier, street)
                        nextManeuver = OsrmManeuver(
                            type = manType,
                            modifier = modifier,
                            streetName = if (street.isNotBlank()) street else "Street ahead",
                            instruction = instruction,
                            distanceMeters = stepDist
                        )
                    }

                    // Divide points into current leg vs future legs based on waypoints
                    val currentLegPoints: List<GeoPoint>
                    val futureLegPoints: List<GeoPoint>
                    val firstStopPt = remainingStops.first()
                    val splitIdx = findNearestPointIndex(allPoints, firstStopPt.lat, firstStopPt.lng)

                    if (splitIdx in 1 until allPoints.size) {
                        currentLegPoints = allPoints.subList(0, splitIdx + 1)
                        futureLegPoints = allPoints.subList(splitIdx, allPoints.size)
                    } else {
                        currentLegPoints = allPoints
                        futureLegPoints = emptyList()
                    }

                    val midpoint = if (currentLegPoints.isNotEmpty()) {
                        currentLegPoints[currentLegPoints.size / 2]
                    } else null

                    val etaClock = SimpleDateFormat("h:mm a", Locale.US)
                        .format(Date(System.currentTimeMillis() + (currentLegEta * 60 * 1000L)))

                    val result = OsrmRouteResult(
                        fullPoints = allPoints,
                        currentLegPoints = currentLegPoints,
                        futureLegPoints = futureLegPoints,
                        currentLegDistanceKm = currentLegDistKm,
                        currentLegEtaMinutes = currentLegEta,
                        totalRemainingDistanceKm = totalDistKm,
                        totalRemainingEtaMinutes = totalEtaMins,
                        etaClockTime = etaClock,
                        nextManeuver = nextManeuver,
                        isFallbackDirect = false,
                        midpointCurrentLeg = midpoint
                    )
                    lastWaypointsKey = coordsParam
                    lastCachedResult = result
                    return result
                }
            }
        } catch (_: Exception) {
            // Network, 429, 5xx, or timeout -> fallback straight line with "Route unavailable"
        }

        // Direct straight line fallback between waypoints
        val directPoints = waypoints
        var totalDirectDist = 0.0
        for (i in 0 until waypoints.size - 1) {
            totalDirectDist += BatchConstants.distanceKm(
                waypoints[i].latitude, waypoints[i].longitude,
                waypoints[i + 1].latitude, waypoints[i + 1].longitude
            )
        }
        val currentDirectDist = BatchConstants.distanceKm(
            riderLat, riderLng,
            remainingStops.first().lat, remainingStops.first().lng
        )
        val currentEta = (currentDirectDist / 25.0 * 60).roundToInt().coerceAtLeast(1)
        val totalEta = (totalDirectDist / 25.0 * 60).roundToInt().coerceAtLeast(1)

        val etaClock = SimpleDateFormat("h:mm a", Locale.US)
            .format(Date(System.currentTimeMillis() + (currentEta * 60 * 1000L)))

        val directManeuver = OsrmManeuver(
            type = "straight",
            modifier = "straight",
            streetName = remainingStops.first().name,
            instruction = "Head directly towards ${remainingStops.first().name}",
            distanceMeters = currentDirectDist * 1000.0
        )

        return OsrmRouteResult(
            fullPoints = directPoints,
            currentLegPoints = listOf(directPoints[0], directPoints[1]),
            futureLegPoints = if (directPoints.size > 2) directPoints.subList(1, directPoints.size) else emptyList(),
            currentLegDistanceKm = currentDirectDist,
            currentLegEtaMinutes = currentEta,
            totalRemainingDistanceKm = totalDirectDist,
            totalRemainingEtaMinutes = totalEta,
            etaClockTime = etaClock,
            nextManeuver = directManeuver,
            isFallbackDirect = true,
            fallbackNotice = "Route unavailable",
            midpointCurrentLeg = GeoPoint(
                (directPoints[0].latitude + directPoints[1].latitude) / 2.0,
                (directPoints[0].longitude + directPoints[1].longitude) / 2.0
            )
        )
    }

    private fun findNearestPointIndex(points: List<GeoPoint>, targetLat: Double, targetLng: Double): Int {
        var minDistance = Double.MAX_VALUE
        var bestIndex = 0
        for (i in points.indices) {
            val dist = BatchConstants.distanceKm(points[i].latitude, points[i].longitude, targetLat, targetLng)
            if (dist < minDistance) {
                minDistance = dist
                bestIndex = i
            }
        }
        return bestIndex
    }

    private fun buildInstruction(type: String, modifier: String, street: String): String {
        val target = if (street.isNotBlank()) "onto $street" else ""
        return when (modifier.lowercase()) {
            "right" -> "Turn right $target".trim()
            "left" -> "Turn left $target".trim()
            "slight right" -> "Keep right $target".trim()
            "slight left" -> "Keep left $target".trim()
            "sharp right" -> "Make a sharp right $target".trim()
            "sharp left" -> "Make a sharp left $target".trim()
            "uturn" -> "Make a U-turn $target".trim()
            else -> when (type.lowercase()) {
                "roundabout", "rotary" -> "Enter the roundabout"
                "arrive" -> "Arriving at destination"
                else -> if (street.isNotBlank()) "Continue on $street" else "Continue straight"
            }
        }
    }
}
