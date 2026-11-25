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

    private val _onlineSinceMillis = MutableStateFlow(prefs.getLong(KEY_ONLINE_SINCE_MS, 0L))
    val onlineSinceMillis: StateFlow<Long> = _onlineSinceMillis.asStateFlow()

    fun getOnlineSinceMillis(): Long = _onlineSinceMillis.value

    fun setOnlineSinceMillis(millis: Long) {
        prefs.edit().putLong(KEY_ONLINE_SINCE_MS, millis).apply()
        _onlineSinceMillis.value = millis
    }

    // Track real online time intervals [startMs, endMs] for session claim onlineMinutes calculation
    private val _onlineIntervals = MutableStateFlow(loadOnlineIntervals())
    val onlineIntervals: StateFlow<List<OnlineInterval>> = _onlineIntervals.asStateFlow()

    data class OnlineInterval(val startMs: Long, val endMs: Long)

    private fun loadOnlineIntervals(): List<OnlineInterval> {
        val raw = prefs.getString(KEY_ONLINE_INTERVALS, null) ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(raw)
            val cutoff = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000L)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val start = obj.optLong("startMs", 0L)
                    val end = obj.optLong("endMs", 0L)
                    if (start > 0L && end > start && end >= cutoff) {
                        add(OnlineInterval(start, end))
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun recordOnlineInterval(startMs: Long, endMs: Long) {
        if (startMs <= 0L || endMs <= startMs) return
        val cutoff = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000L)
        val updated = (_onlineIntervals.value + OnlineInterval(startMs, endMs))
            .filter { it.endMs >= cutoff }
            .distinctBy { it.startMs to it.endMs }
            .sortedBy { it.startMs }
        val arr = org.json.JSONArray()
        updated.forEach { iv ->
            arr.put(org.json.JSONObject().apply {
                put("startMs", iv.startMs)
                put("endMs", iv.endMs)
            })
        }
        prefs.edit().putString(KEY_ONLINE_INTERVALS, arr.toString()).apply()
        _onlineIntervals.value = updated
    }

    fun getNotifiedReminderSessionIds(): Set<String> {
        return prefs.getStringSet(KEY_NOTIFIED_SESSION_REMINDERS, emptySet()) ?: emptySet()
    }

    fun markReminderNotified(sessionId: String) {
        val updated = getNotifiedReminderSessionIds().toMutableSet()
        updated.add(sessionId)
        prefs.edit().putStringSet(KEY_NOTIFIED_SESSION_REMINDERS, updated).apply()
    }

    fun clearReminderNotified(sessionId: String) {
        val updated = getNotifiedReminderSessionIds().toMutableSet()
        if (updated.remove(sessionId)) {
            prefs.edit().putStringSet(KEY_NOTIFIED_SESSION_REMINDERS, updated).apply()
        }
    }

    // Pending COD wallet ledger retry queue (persisted across restarts)
    private val _pendingCodLedgerEntries = MutableStateFlow(loadPendingCodLedgerEntries())
    val pendingCodLedgerEntries: StateFlow<List<com.example.data.models.PendingCodLedgerEntry>> =
        _pendingCodLedgerEntries.asStateFlow()

    private fun loadPendingCodLedgerEntries(): List<com.example.data.models.PendingCodLedgerEntry> {
        val raw = prefs.getString(KEY_PENDING_COD_LEDGER, null) ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val orderId = obj.optString("orderId", "")
                    val riderId = obj.optString("riderId", "")
                    val orderNumber = obj.optString("orderNumber", "")
                    val amount = obj.optDouble("amount", 0.0)
                    val createdAtMillis = obj.optLong("createdAtMillis", 0L)
                    if (orderId.isNotBlank() && riderId.isNotBlank() && amount > 0.0) {
                        add(
                            com.example.data.models.PendingCodLedgerEntry(
                                orderId = orderId,
                                riderId = riderId,
                                orderNumber = orderNumber,
                                amount = amount,
                                createdAtMillis = createdAtMillis
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun savePendingCodLedgerEntries(entries: List<com.example.data.models.PendingCodLedgerEntry>) {
        val arr = org.json.JSONArray()
        entries.forEach { entry ->
            arr.put(org.json.JSONObject().apply {
                put("orderId", entry.orderId)
                put("riderId", entry.riderId)
                put("orderNumber", entry.orderNumber)
                put("amount", entry.amount)
                put("createdAtMillis", entry.createdAtMillis)
            })
        }
        prefs.edit().putString(KEY_PENDING_COD_LEDGER, arr.toString()).apply()
        _pendingCodLedgerEntries.value = entries
    }

    @Synchronized
    fun enqueuePendingCodLedgerEntry(entry: com.example.data.models.PendingCodLedgerEntry) {
        if (entry.orderId.isBlank() || entry.riderId.isBlank() || entry.amount <= 0.0) return
        val current = _pendingCodLedgerEntries.value.filterNot { it.orderId == entry.orderId }
        savePendingCodLedgerEntries(current + entry)
    }

    @Synchronized
    fun removePendingCodLedgerEntry(orderId: String) {
        val current = _pendingCodLedgerEntries.value
        val updated = current.filterNot { it.orderId == orderId }
        if (updated.size != current.size) {
            savePendingCodLedgerEntries(updated)
        }
    }

    companion object {
        private const val KEY_NAV_APP = "key_nav_app"
        private const val KEY_DARK_MODE = "key_dark_mode"
        private const val KEY_SOUND_ENABLED = "key_sound_enabled"
        private const val KEY_VIBRATION_ENABLED = "key_vibration_enabled"
        private const val KEY_CURRENCY = "key_currency"
        private const val KEY_DECLINED_ORDERS = "key_declined_orders"
        private const val KEY_CELEBRATED_TIERS = "key_celebrated_tiers"
        private const val KEY_ONLINE_SINCE_MS = "key_online_since_ms"
        private const val KEY_ONLINE_INTERVALS = "key_online_intervals"
        private const val KEY_NOTIFIED_SESSION_REMINDERS = "key_notified_session_reminders"
        private const val KEY_PENDING_COD_LEDGER = "key_pending_cod_ledger_v1"
    }
}
