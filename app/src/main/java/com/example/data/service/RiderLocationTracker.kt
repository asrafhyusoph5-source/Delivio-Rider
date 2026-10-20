package com.example.data.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object RiderLocationTracker {
    private val _currentLocation = MutableStateFlow<Pair<Double, Double>?>(null)
    val currentLocation: StateFlow<Pair<Double, Double>?> = _currentLocation.asStateFlow()

    private val _activeOrderIds = MutableStateFlow<Set<String>>(emptySet())
    val activeOrderIds: StateFlow<Set<String>> = _activeOrderIds.asStateFlow()

    fun updateLocation(lat: Double, lng: Double) {
        _currentLocation.value = Pair(lat, lng)
    }

    fun setActiveOrderIds(ids: Set<String>) {
        _activeOrderIds.value = ids
    }
}
