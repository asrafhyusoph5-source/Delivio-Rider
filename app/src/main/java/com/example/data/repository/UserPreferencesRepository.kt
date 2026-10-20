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

    companion object {
        private const val KEY_NAV_APP = "key_nav_app"
        private const val KEY_DARK_MODE = "key_dark_mode"
        private const val KEY_SOUND_ENABLED = "key_sound_enabled"
        private const val KEY_VIBRATION_ENABLED = "key_vibration_enabled"
        private const val KEY_CURRENCY = "key_currency"
        private const val KEY_DECLINED_ORDERS = "key_declined_orders"
        private const val KEY_CELEBRATED_TIERS = "key_celebrated_tiers"
    }
}
