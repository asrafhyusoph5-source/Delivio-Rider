package com.example.data.service

import com.example.data.constants.BatchConstants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

object RiderLocationTracker {
    private val _currentLocation = MutableStateFlow<Pair<Double, Double>?>(null)
    val currentLocation: StateFlow<Pair<Double, Double>?> = _currentLocation.asStateFlow()

    private val _heading = MutableStateFlow<Float>(0f)
    val heading: StateFlow<Float> = _heading.asStateFlow()

    private val _speedKmh = MutableStateFlow<Int>(0)
    val speedKmh: StateFlow<Int> = _speedKmh.asStateFlow()

    private val _activeOrderIds = MutableStateFlow<Set<String>>(emptySet())
    val activeOrderIds: StateFlow<Set<String>> = _activeOrderIds.asStateFlow()

    private var lastLocationTime = 0L

    fun updateLocation(lat: Double, lng: Double, headingBearing: Float? = null, speed: Float? = null) {
        val prev = _currentLocation.value
        val now = System.currentTimeMillis()
        if (prev != null && (prev.first != lat || prev.second != lng)) {
            val calcBearing = calculateBearing(prev.first, prev.second, lat, lng)
            _heading.value = headingBearing ?: calcBearing

            val timeDiffSec = (now - lastLocationTime) / 1000.0
            if (timeDiffSec in 0.5..15.0) {
                val distKm = BatchConstants.distanceKm(prev.first, prev.second, lat, lng)
                val calcSpeed = ((distKm / timeDiffSec) * 3600.0).roundToInt().coerceIn(0, 120)
                _speedKmh.value = speed?.roundToInt() ?: calcSpeed
            }
        }
        lastLocationTime = now
        _currentLocation.value = Pair(lat, lng)
    }

    private fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val dLon = Math.toRadians(lon2 - lon1)
        val y = Math.sin(dLon) * Math.cos(Math.toRadians(lat2))
        val x = Math.cos(Math.toRadians(lat1)) * Math.sin(Math.toRadians(lat2)) -
                Math.sin(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.cos(dLon)
        val brng = Math.toDegrees(Math.atan2(y, x))
        return ((brng + 360) % 360).toFloat()
    }

    fun setActiveOrderIds(ids: Set<String>) {
        _activeOrderIds.value = ids
    }
}
