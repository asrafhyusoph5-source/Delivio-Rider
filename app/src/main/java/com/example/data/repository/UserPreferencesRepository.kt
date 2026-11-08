package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UserPreferencesRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("delivio_rider_prefs", Context.MODE_PRIVATE)

    private val _isDarkMode = MutableStateFlow(prefs.getBoolean(KEY_DARK_MODE, false))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _soundEnabled = MutableStateFlow(prefs.getBoolean(KEY_SOUND_ENABLED, true))
    val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()

    private val _vibrationEnabled = MutableStateFlow(prefs.getBoolean(KEY_VIBRATION_ENABLED, true))
    val vibrationEnabled: StateFlow<Boolean> = _vibrationEnabled.asStateFlow()

    private val _currencySymbol = MutableStateFlow(prefs.getString(KEY_CURRENCY, "₱") ?: "₱")
    val currencySymbol: StateFlow<String> = _currencySymbol.asStateFlow()

    // Locally stored declined orders
    private val _declinedOrderIds = MutableStateFlow(prefs.getStringSet(KEY_DECLINED_ORDERS, emptySet()) ?: emptySet())
    val declinedOrderIds: StateFlow<Set<String>> = _declinedOrderIds.asStateFlow()

    // Locally celebrated tiers to prevent duplicate celebration popups
    private val _celebratedTiers = MutableStateFlow(prefs.getStringSet(KEY_CELEBRATED_TIERS, emptySet()) ?: emptySet())
    val celebratedTiers: StateFlow<Set<String>> = _celebratedTiers.asStateFlow()

    fun setDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply()
        _isDarkMode.value = enabled
    }

    fun setSoundEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply()
        _soundEnabled.value = enabled
    }

    fun setVibrationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VIBRATION_ENABLED, enabled).apply()
        _vibrationEnabled.value = enabled
    }

    fun addDeclinedOrderId(orderId: String) {
        val updated = _declinedOrderIds.value.toMutableSet()
        updated.add(orderId)
        prefs.edit().putStringSet(KEY_DECLINED_ORDERS, updated).apply()
        _declinedOrderIds.value = updated
    }

    fun addCelebratedTier(key: String) {
        val updated = _celebratedTiers.value.toMutableSet()
        updated.add(key)
        prefs.edit().putStringSet(KEY_CELEBRATED_TIERS, updated).apply()
        _celebratedTiers.value = updated
    }

    // Navigation app preference
    private val _navigationApp = MutableStateFlow(prefs.getString(KEY_NAV_APP, "Google Maps") ?: "Google Maps")
    val navigationApp: StateFlow<String> = _navigationApp.asStateFlow()

    fun setNavigationApp(app: String) {
        prefs.edit().putString(KEY_NAV_APP, app).apply()
        _navigationApp.value = app
    }

    // Driver Shift Schedule (Personal Planner)
    private val _shifts = MutableStateFlow<List<com.example.data.models.DriverShift>>(loadShifts())
    val shifts: StateFlow<List<com.example.data.models.DriverShift>> = _shifts.asStateFlow()

    private fun loadShifts(): List<com.example.data.models.DriverShift> {
        val jsonStr = prefs.getString(KEY_DRIVER_SHIFTS, null)
        if (jsonStr.isNullOrBlank()) {
            // Default sample shifts: Mon-Fri 08:00 - 17:00
            return (1..5).map { day ->
                com.example.data.models.DriverShift(
                    dayOfWeek = day,
                    startTime = "08:00",
                    endTime = "17:00",
                    reminder15MinBefore = true,
                    isEnabled = true
                )
            }
        }
        return try {
            val jsonArray = org.json.JSONArray(jsonStr)
            val list = mutableListOf<com.example.data.models.DriverShift>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    com.example.data.models.DriverShift(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        dayOfWeek = obj.optInt("dayOfWeek", 1),
                        startTime = obj.optString("startTime", "08:00"),
                        endTime = obj.optString("endTime", "17:00"),
                        reminder15MinBefore = obj.optBoolean("reminder15MinBefore", true),
                        isEnabled = obj.optBoolean("isEnabled", true)
                    )
                )
            }
            list.sortedWith(compareBy({ it.dayOfWeek }, { it.startTime }))
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveShifts(shifts: List<com.example.data.models.DriverShift>) {
        val sorted = shifts.sortedWith(compareBy({ it.dayOfWeek }, { it.startTime }))
        val jsonArray = org.json.JSONArray()
        for (shift in sorted) {
            val obj = org.json.JSONObject()
            obj.put("id", shift.id)
            obj.put("dayOfWeek", shift.dayOfWeek)
            obj.put("startTime", shift.startTime)
            obj.put("endTime", shift.endTime)
            obj.put("reminder15MinBefore", shift.reminder15MinBefore)
            obj.put("isEnabled", shift.isEnabled)
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_DRIVER_SHIFTS, jsonArray.toString()).apply()
        _shifts.value = sorted
    }

    fun addShift(shift: com.example.data.models.DriverShift) {
        val updated = _shifts.value.toMutableList()
        updated.add(shift)
        saveShifts(updated)
    }

    fun updateShift(shift: com.example.data.models.DriverShift) {
        val updated = _shifts.value.map { if (it.id == shift.id) shift else it }
        saveShifts(updated)
    }

    fun deleteShift(shiftId: String) {
        val updated = _shifts.value.filter { it.id != shiftId }
        saveShifts(updated)
    }

    fun toggleShift(shiftId: String, enabled: Boolean) {
        val updated = _shifts.value.map { if (it.id == shiftId) it.copy(isEnabled = enabled) else it }
        saveShifts(updated)
    }

    fun toggleShiftReminder(shiftId: String, reminderEnabled: Boolean) {
        val updated = _shifts.value.map { if (it.id == shiftId) it.copy(reminder15MinBefore = reminderEnabled) else it }
        saveShifts(updated)
    }

    fun applyPreset(presetType: String) {
        val newShifts = when (presetType) {
            "peak" -> {
                // Lunch: 11:00-14:00, Dinner: 18:00-21:00 for Mon-Sun
                (1..7).flatMap { day ->
                    listOf(
                        com.example.data.models.DriverShift(dayOfWeek = day, startTime = "11:00", endTime = "14:00", reminder15MinBefore = true),
                        com.example.data.models.DriverShift(dayOfWeek = day, startTime = "18:00", endTime = "21:00", reminder15MinBefore = true)
                    )
                }
            }
            "full_week" -> {
                // Mon-Sun 08:00 - 17:00
                (1..7).map { day ->
                    com.example.data.models.DriverShift(dayOfWeek = day, startTime = "08:00", endTime = "17:00", reminder15MinBefore = true)
                }
            }
            else -> {
                // Standard Weekday (Mon-Fri 08:00 - 17:00)
                (1..5).map { day ->
                    com.example.data.models.DriverShift(dayOfWeek = day, startTime = "08:00", endTime = "17:00", reminder15MinBefore = true)
                }
            }
        }
        saveShifts(newShifts)
    }

    companion object {
        private const val KEY_NAV_APP = "key_nav_app"
        private const val KEY_DARK_MODE = "key_dark_mode"
        private const val KEY_SOUND_ENABLED = "key_sound_enabled"
        private const val KEY_VIBRATION_ENABLED = "key_vibration_enabled"
        private const val KEY_CURRENCY = "key_currency"
        private const val KEY_DECLINED_ORDERS = "key_declined_orders"
        private const val KEY_CELEBRATED_TIERS = "key_celebrated_tiers"
        private const val KEY_DRIVER_SHIFTS = "key_driver_shifts"
    }
}
