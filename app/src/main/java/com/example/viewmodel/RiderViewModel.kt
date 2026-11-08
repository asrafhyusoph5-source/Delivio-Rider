package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.constants.OrderStepConstants
import com.example.data.models.*
import com.example.data.repository.RiderRepository
import com.example.data.repository.UserPreferencesRepository
import com.example.data.service.RiderLocationTracker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class RiderViewModel(application: Application) : AndroidViewModel(application) {

    val prefsRepository = UserPreferencesRepository(application.applicationContext)
    val riderRepository = RiderRepository()
    private val auth: FirebaseAuth by lazy {
        try {
            FirebaseAuth.getInstance()
        } catch (_: Throwable) {
            com.example.DelivioApplication.ensureFirebase(application)
            FirebaseAuth.getInstance()
        }
    }
    private val db: FirebaseFirestore by lazy {
        try {
            FirebaseFirestore.getInstance()
        } catch (_: Throwable) {
            com.example.DelivioApplication.ensureFirebase(application)
            FirebaseFirestore.getInstance()
        }
    }

    // Theme & preferences
    val isDarkMode = prefsRepository.isDarkMode
    val soundEnabled = prefsRepository.soundEnabled
    val vibrationEnabled = prefsRepository.vibrationEnabled
    val currencySymbol = prefsRepository.currencySymbol

    // Shift Schedule (Personal Planner)
    val shifts = prefsRepository.shifts

    fun addShift(shift: DriverShift) = prefsRepository.addShift(shift)
    fun updateShift(shift: DriverShift) = prefsRepository.updateShift(shift)
    fun deleteShift(shiftId: String) = prefsRepository.deleteShift(shiftId)
    fun toggleShift(shiftId: String, enabled: Boolean) = prefsRepository.toggleShift(shiftId, enabled)
    fun toggleShiftReminder(shiftId: String, reminderEnabled: Boolean) = prefsRepository.toggleShiftReminder(shiftId, reminderEnabled)
    fun applyShiftPreset(presetType: String) = prefsRepository.applyPreset(presetType)

    // Demand Heat Map ("Busy areas")
    private val _isBusyAreasOverlayVisible = MutableStateFlow(false)
    val isBusyAreasOverlayVisible: StateFlow<Boolean> = _isBusyAreasOverlayVisible.asStateFlow()

    fun toggleBusyAreasOverlay() {
        _isBusyAreasOverlayVisible.value = !_isBusyAreasOverlayVisible.value
    }

    val busyGridCells: StateFlow<List<BusyGridCell>> = combine(
        riderRepository.candidateOrders,
        riderRepository.activeOrders,
        riderRepository.deliveredOrders
    ) { candidates, active, delivered ->
        val allReadable = (candidates + active + delivered).distinctBy { it.id }
        BusyHeatMapCalculator.computeGridCells(allReadable)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Document-expiry reminders (license, registration, insurance)
    private val _dismissedDocumentAlertTypes = MutableStateFlow<Set<String>>(emptySet())

    val documentExpiryAlerts: StateFlow<List<DocumentExpiryAlert>> = combine(
        riderRepository.rider,
        _dismissedDocumentAlertTypes
    ) { currentRider, dismissed ->
        currentRider?.documentExpiryAlerts?.filter { it.document.type.lowercase().trim() !in dismissed } ?: emptyList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun dismissDocumentAlert(type: String) {
        _dismissedDocumentAlertTypes.value = _dismissedDocumentAlertTypes.value + type.lowercase().trim()
    }

    // Live state from repository
    val rider = riderRepository.rider
    val activeOrders = riderRepository.activeOrders
    val incomingOffer = riderRepository.incomingOffer
    val incomingOfferBundle = riderRepository.incomingOfferBundle
    val deliveredOrders = riderRepository.deliveredOrders
    val tierConfigs = riderRepository.tierConfigs
    val inboxMessages = riderRepository.inboxMessages
    val supportPhone = riderRepository.supportPhone
    val coins = riderRepository.coins
    val coinHistory = riderRepository.coinHistory
    val newlyReachedTier = riderRepository.newlyReachedTier
    val listenerError = riderRepository.listenerError
    val currentLocation = RiderLocationTracker.currentLocation
    val heading: StateFlow<Float> = RiderLocationTracker.heading
    val speedKmh: StateFlow<Int> = RiderLocationTracker.speedKmh

    // Order Switcher (index 0 or 1)
    private val _selectedOrderIndex = MutableStateFlow(0)
    val selectedOrderIndex: StateFlow<Int> = _selectedOrderIndex.asStateFlow()

    private var previousStopsCache = listOf<RouteStop>()

    val batchStops: StateFlow<List<RouteStop>> = combine(
        activeOrders,
        currentLocation
    ) { orders, loc ->
        val lat = loc?.first ?: 0.0
        val lng = loc?.second ?: 0.0
        val computed = RouteStopCalculator.computeBatchStops(
            activeOrders = orders,
            driverLat = lat,
            driverLng = lng,
            previousStops = previousStopsCache
        )
        previousStopsCache = computed
        computed
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val currentRouteStop: StateFlow<RouteStop?> = batchStops.map { stops ->
        stops.firstOrNull { it.status == StopStatus.CURRENT }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _focusedStopOnMap = MutableStateFlow<RouteStop?>(null)
    val focusedStopOnMap: StateFlow<RouteStop?> = _focusedStopOnMap.asStateFlow()

    fun focusStopOnMap(stop: RouteStop?) {
        _focusedStopOnMap.value = stop
    }

    val currentActiveOrder: StateFlow<Order?> = combine(activeOrders, currentRouteStop, _selectedOrderIndex) { orders, currentStop, idx ->
        if (orders.isEmpty()) null
        else if (currentStop != null) {
            orders.firstOrNull { it.id == currentStop.orderId } ?: orders.getOrNull(idx) ?: orders.firstOrNull()
        } else {
            orders.getOrNull(idx) ?: orders.firstOrNull()
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Auth & Application State
    private val _isLoggedIn = MutableStateFlow(try { auth.currentUser != null } catch (_: Throwable) { false })
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _applicationState = MutableStateFlow<RiderApplication?>(null)
    val applicationState: StateFlow<RiderApplication?> = _applicationState.asStateFlow()

    private val _isBlocked = MutableStateFlow<String?>(null)
    val isBlocked: StateFlow<String?> = _isBlocked.asStateFlow()

    // Snackbar & Toast error message state
    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()
    val toastMessage: StateFlow<String?> = snackbarMessage

    // Accept Order Error Dialog State
    private val _acceptOrderError = MutableStateFlow<AcceptOrderErrorDetails?>(null)
    val acceptOrderError: StateFlow<AcceptOrderErrorDetails?> = _acceptOrderError.asStateFlow()

    fun dismissAcceptOrderError() {
        _acceptOrderError.value = null
    }

    // Notifications & Inbox
    val notifications: StateFlow<List<MessageNotification>> = inboxMessages
    val unreadNotificationCount: StateFlow<Int> = inboxMessages.map { list ->
        list.count { !it.isRead }
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0)

    // Navigation App Preference
    val navigationApp: StateFlow<String> = prefsRepository.navigationApp

    // Monthly Tier Status & Summary
    val tierStatus: StateFlow<RiderTierStatus> = combine(
        deliveredOrders,
        coins,
        coinHistory,
        newlyReachedTier
    ) { delivered, coinCount, history, newlyReached ->
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val firstDayOfMonth = cal.time

        val ridesThisMonth = delivered.count { order ->
            when (val delAt = order.deliveredAt) {
                is com.google.firebase.Timestamp -> delAt.toDate().after(firstDayOfMonth)
                is java.util.Date -> delAt.after(firstDayOfMonth)
                else -> true
            }
        }

        val currentTier = when {
            ridesThisMonth >= TierLevel.PLATINUM.monthlyRidesRequired -> TierLevel.PLATINUM
            ridesThisMonth >= TierLevel.GOLD.monthlyRidesRequired -> TierLevel.GOLD
            ridesThisMonth >= TierLevel.SILVER.monthlyRidesRequired -> TierLevel.SILVER
            ridesThisMonth >= TierLevel.BRONZE.monthlyRidesRequired -> TierLevel.BRONZE
            else -> null
        }

        val nowCal = java.util.Calendar.getInstance()
        val maxDays = nowCal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
        val currentDay = nowCal.get(java.util.Calendar.DAY_OF_MONTH)
        val daysRemaining = (maxDays - currentDay).coerceAtLeast(0)

        val claimedRewards = mutableSetOf<String>()
        if (currentTier != null) {
            when (currentTier) {
                TierLevel.PLATINUM -> claimedRewards.addAll(listOf("BRONZE", "SILVER", "GOLD", "PLATINUM"))
                TierLevel.GOLD -> claimedRewards.addAll(listOf("BRONZE", "SILVER", "GOLD"))
                TierLevel.SILVER -> claimedRewards.addAll(listOf("BRONZE", "SILVER"))
                TierLevel.BRONZE -> claimedRewards.add("BRONZE")
            }
        }

        val achievedTierLevel = when (newlyReached?.id?.lowercase()) {
            "platinum" -> TierLevel.PLATINUM
            "gold" -> TierLevel.GOLD
            "silver" -> TierLevel.SILVER
            "bronze" -> TierLevel.BRONZE
            else -> null
        }

        RiderTierStatus(
            currentMonthRides = ridesThisMonth,
            currentTier = currentTier,
            currentCoins = coinCount,
            daysRemainingInMonth = daysRemaining,
            claimedTierRewards = claimedRewards,
            coinHistory = history,
            newlyAchievedTier = achievedTierLevel
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, RiderTierStatus())

    val earningsSummary: StateFlow<EarningSummary> = deliveredOrders.map { orders ->
        val totalNet = orders.sumOf { it.estimatedEarnings }
        EarningSummary(
            todayNet = totalNet,
            weekNet = totalNet,
            monthNet = totalNet,
            jobsCompletedToday = orders.size,
            onlineHoursToday = 6.5
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, EarningSummary())

    val isSharedWallet: StateFlow<Boolean> = rider.map { r ->
        !r?.linkedCustomerUid.isNullOrBlank()
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    // Wallet balance: live from users/{linkedCustomerUid}.walletBalance if linked, or riders/{uid}.walletBalance
    val walletBalance: StateFlow<Double> = combine(rider, riderRepository.sharedCustomerWalletBalance) { r, sharedBal ->
        if (!r?.linkedCustomerUid.isNullOrBlank() && sharedBal != null) {
            sharedBal
        } else {
            r?.walletBalance ?: 0.0
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0.0)

    val earningsAvailableToWithdraw: StateFlow<Double> = deliveredOrders.map { orders ->
        orders.sumOf { it.estimatedEarnings }
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0.0)

    // Real COD cash collected and Remittance due
    val totalCodCollected: StateFlow<Double> = deliveredOrders.map { orders ->
        orders.filter { it.isCod }.sumOf { it.codCollected }
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0.0)

    val cashRemitted: StateFlow<Double> = rider.map { r ->
        r?.cashRemitted ?: 0.0
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0.0)

    val remittanceDue: StateFlow<Double> = combine(totalCodCollected, cashRemitted) { cod, remitted ->
        (cod - remitted).coerceAtLeast(0.0)
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0.0)

    val cashOnHand: StateFlow<Double> = combine(deliveredOrders, rider) { orders, r ->
        val totalCod = orders.filter { it.isCod }.sumOf { it.codCollected }
        (totalCod - (r?.cashRemitted ?: 0.0)).coerceAtLeast(0.0)
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0.0)

    // Wallet history live from walletTransactions collection (sorted newest first in code)
    val transactions: StateFlow<List<WalletTransaction>> = riderRepository.walletTransactions
    val walletHistory: StateFlow<List<WalletTransaction>> = transactions

    // Offer Logs and Customer Reviews
    val offerLogs: StateFlow<List<OfferLogEntry>> = riderRepository.offerLogs
    val customerReviews: StateFlow<List<CustomerReview>> = riderRepository.customerReviews

    val averageCustomerRating: StateFlow<Double> = customerReviews.map { revs ->
        if (revs.isEmpty()) 5.0 else revs.map { it.ratingRider }.average()
    }.stateIn(viewModelScope, SharingStarted.Lazily, 5.0)

    val customerReviewCount: StateFlow<Int> = customerReviews.map { it.size }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val lateTipBanner: StateFlow<LateTipBannerInfo?> = riderRepository.lateTipBanner

    fun dismissLateTipBanner() {
        riderRepository.dismissLateTipBanner()
    }

    // Live Support Tickets
    val supportTickets: StateFlow<List<SupportTicket>> = riderRepository.supportTickets
    val unresolvedTickets: StateFlow<List<SupportTicket>> = riderRepository.unresolvedTickets
    val activeTicket: StateFlow<SupportTicket?> = riderRepository.activeTicket
    val activeTicketMessages: StateFlow<List<SupportTicketMessage>> = riderRepository.activeTicketMessages
    val adminMessageBanner: StateFlow<AdminMessageBannerInfo?> = riderRepository.adminMessageBanner

    val hasUnresolvedTicket: StateFlow<Boolean> = unresolvedTickets.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    val hasUnreadAdminMessage: StateFlow<Boolean> = unresolvedTickets.map { tickets ->
        tickets.any { it.unreadByRider }
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    // Live Quests & Claims
    val quests: StateFlow<List<Quest>> = riderRepository.quests
    val questClaims: StateFlow<Map<String, QuestClaim>> = riderRepository.questClaims

    val questProgressList: StateFlow<List<QuestProgress>> = combine(
        riderRepository.quests,
        riderRepository.deliveredOrders,
        riderRepository.offerLogs,
        riderRepository.questClaims,
        riderRepository.rider
    ) { questsList, delivered, logs, claims, currentRider ->
        questsList.filter { it.appliesTo(currentRider) }.map { q ->
            riderRepository.computeQuestProgress(q, delivered, logs, claims[q.id])
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun refreshQuests() {
        riderRepository.checkAndCreateClaimsForEndedQuests()
    }

    val hasOrderAtMerchant: StateFlow<Boolean> = activeOrders.map { orders ->
        orders.any { ord ->
            val s = ord.serviceType.lowercase().trim()
            val isFoodOrConv = s in listOf("food", "convenience", "instant", OrderStepConstants.SERVICE_FOOD)
            val step = ord.riderStep.lowercase().trim()
            val atMerchant = step in setOf(
                OrderStepConstants.STEP_AT_MERCHANT,
                OrderStepConstants.STEP_ARRIVED_AT_MERCHANT,
                "at_merchant",
                "arrived_at_merchant"
            )
            isFoodOrConv && atMerchant
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    fun selectSupportTicket(ticketId: String) {
        riderRepository.selectTicket(ticketId)
    }

    fun dismissAdminMessageBanner() {
        riderRepository.dismissAdminMessageBanner()
    }

    fun createSupportTicket(
        order: Order,
        reasonKey: String,
        reasonLabel: String,
        splitItems: List<String>,
        title: String,
        details: String,
        photosBase64: List<String>,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        riderRepository.createSupportTicket(
            order = order,
            reasonKey = reasonKey,
            reasonLabel = reasonLabel,
            splitItems = splitItems,
            title = title,
            details = details,
            photosBase64 = photosBase64,
            onSuccess = onSuccess,
            onError = onError
        )
    }

    fun sendTicketMessage(
        ticketId: String,
        text: String,
        imageData: String? = null,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        riderRepository.sendTicketMessage(ticketId, text, imageData, onSuccess, onError)
    }

    // Rate the shop dialog state
    private val _rateShopOrder = MutableStateFlow<Order?>(null)
    val rateShopOrder: StateFlow<Order?> = _rateShopOrder.asStateFlow()

    fun promptRateShop(order: Order) {
        _rateShopOrder.value = order
    }

    fun dismissRateShop() {
        _rateShopOrder.value = null
    }

    fun submitShopReview(order: Order, ratingMerchant: Int, comment: String) {
        val oId = order.id
        val mId = order.merchantId.ifBlank { "unknown_merchant" }
        val sType = order.serviceType
        dismissRateShop()
        riderRepository.submitShopReview(
            orderId = oId,
            merchantId = mId,
            serviceType = sType,
            ratingMerchant = ratingMerchant,
            comment = comment,
            onSuccess = {
                showSnackbar("Thank you! Shop rating submitted.")
            },
            onError = { showSnackbar(it) }
        )
    }

    val platformServices = riderRepository.platformServices
    val cityServices = riderRepository.cityServices
    val currentCityName = riderRepository.currentCityName

    fun isServiceOn(serviceType: String): Boolean = riderRepository.isServiceOn(serviceType)
    fun isPlatformServiceOn(serviceType: String): Boolean = riderRepository.isPlatformServiceOn(serviceType)
    fun isCityServiceOn(serviceType: String): Boolean = riderRepository.isCityServiceOn(serviceType)
    fun getCityDisplayName(): String = riderRepository.getCityDisplayName()

    fun getServiceLockReason(serviceType: String): String? {
        val currentRider = rider.value
        val isAllowed = currentRider?.allowedServices?.contains(serviceType) ?: true
        val isPlatformOn = isPlatformServiceOn(serviceType)
        val isCityOn = isCityServiceOn(serviceType)
        return OrderStepConstants.getServiceLockReason(
            isPlatformOn = isPlatformOn,
            isCityOn = isCityOn,
            isAllowed = isAllowed,
            cityName = getCityDisplayName()
        )
    }

    // Support chat
    private val _supportChatMessages = MutableStateFlow<List<ChatMessage>>(listOf(
        ChatMessage(
            id = "welcome",
            senderName = "Delivio Support",
            message = "Hi! How can we assist you with your deliveries today?",
            timestampText = "Just now",
            isFromMe = false,
            senderType = "support"
        )
    ))
    val supportChatMessages: StateFlow<List<ChatMessage>> = _supportChatMessages.asStateFlow()

    // Success dialog for completed delivery
    private val _completedDeliveryOrder = MutableStateFlow<Order?>(null)
    val completedDeliveryOrder: StateFlow<Order?> = _completedDeliveryOrder.asStateFlow()

    // Active in-app call info
    private val _activeCall = MutableStateFlow<ActiveCallInfo?>(null)
    val activeCall: StateFlow<ActiveCallInfo?> = _activeCall.asStateFlow()

    init {
        try {
            auth.currentUser?.let { user ->
                checkUserRoleAndListen(user.uid)
            }
        } catch (_: Throwable) {
            _isLoggedIn.value = false
        }
        viewModelScope.launch {
            try {
                riderRepository.listenerError.collect { err ->
                    err?.let {
                        showSnackbar(it)
                        riderRepository.clearListenerError()
                    }
                }
            } catch (_: Throwable) {}
        }
    }

    fun selectOrderIndex(index: Int) {
        _selectedOrderIndex.value = index
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun clearToast() = clearSnackbar()

    fun showSnackbar(msg: String) {
        _snackbarMessage.value = msg
    }

    fun showToast(msg: String) = showSnackbar(msg)

    fun toggleTheme(isDark: Boolean) {
        prefsRepository.setDarkMode(isDark)
    }

    fun setNavigationApp(app: String) {
        prefsRepository.setNavigationApp(app)
    }

    fun dismissDeliverySuccess() {
        _completedDeliveryOrder.value = null
    }

    fun dismissCelebration() {
        riderRepository.dismissNewlyReachedTier()
    }

    fun dismissTierCelebration() {
        riderRepository.dismissNewlyReachedTier()
    }

    fun markNotificationRead(id: String) {
        riderRepository.markInboxMessageRead(id)
    }

    fun markAllNotificationsRead() {
        riderRepository.markAllInboxRead()
    }

    fun endActiveCall() {
        endCall()
    }

    fun openDialer(phone: String, label: String = "") {
        launchExternalDialer(phone)
    }

    fun launchExternalDialer(phoneNumber: String) {
        try {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            getApplication<Application>().startActivity(intent)
        } catch (_: Exception) {
            showSnackbar("Cannot open dialer for $phoneNumber")
        }
    }

    fun setSoundEnabled(enabled: Boolean) {
        prefsRepository.setSoundEnabled(enabled)
    }

    fun setVibrationEnabled(enabled: Boolean) {
        prefsRepository.setVibrationEnabled(enabled)
    }

    fun performLogout(onComplete: () -> Unit) {
        auth.signOut()
        _isLoggedIn.value = false
        riderRepository.stopListening()
        onComplete()
    }

    fun cashOut(amount: Double = 0.0, method: String = ""): Boolean {
        showSnackbar("Coming soon")
        return true
    }

    fun sendSupportMessage(text: String) {
        val userMsg = ChatMessage(
            id = System.currentTimeMillis().toString(),
            senderName = rider.value?.name ?: "Rider",
            message = text,
            timestampText = "Now",
            isFromMe = true,
            senderType = "rider"
        )
        _supportChatMessages.value = _supportChatMessages.value + userMsg
    }

    // CHECK USER ROLE & STATUS
    fun checkUserRoleAndListen(uid: String) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { userDoc ->
                val role = userDoc.getString("role")
                if (role == "rider") {
                    // Check riders/{uid}.status
                    db.collection("riders").document(uid).get()
                        .addOnSuccessListener { riderDoc ->
                            val status = riderDoc.getString("status") ?: "active"
                            if (status != "active") {
                                _isBlocked.value = "Your rider account is currently $status. Please contact support."
                                _applicationState.value = null
                                _isLoggedIn.value = false
                            } else {
                                _isBlocked.value = null
                                _applicationState.value = null
                                _isLoggedIn.value = true
                                riderRepository.startListeningForRider(uid, prefsRepository)
                            }
                        }
                        .addOnFailureListener {
                            _isBlocked.value = "Failed to load rider status: ${it.localizedMessage}"
                            _isLoggedIn.value = false
                        }
                } else {
                    // Check riderApplications/{uid}
                    db.collection("riderApplications").document(uid).get()
                        .addOnSuccessListener { appDoc ->
                            if (appDoc.exists()) {
                                val emergencyMap = appDoc.get("emergencyContact") as? Map<*, *>
                                val docsMap = appDoc.get("documents") as? Map<*, *>
                                val app = RiderApplication(
                                    ownerUid = appDoc.getString("ownerUid") ?: uid,
                                    name = appDoc.getString("name") ?: "",
                                    phone = appDoc.getString("phone") ?: "",
                                    email = appDoc.getString("email") ?: "",
                                    cityId = appDoc.getString("cityId") ?: "",
                                    vehicleType = appDoc.getString("vehicleType") ?: "motorcycle",
                                    plate = appDoc.getString("plate") ?: "",
                                    vehicleModel = appDoc.getString("vehicleModel") ?: "",
                                    vehicleColor = appDoc.getString("vehicleColor") ?: "",
                                    seats = appDoc.getLong("seats")?.toInt() ?: 1,
                                    emergencyContact = emergencyMap?.entries?.associate { it.key.toString() to (it.value?.toString() ?: "") } ?: emptyMap(),
                                    documents = docsMap?.entries?.associate { it.key.toString() to (it.value?.toString() ?: "") } ?: emptyMap(),
                                    status = appDoc.getString("status") ?: "Pending",
                                    rejectionReason = appDoc.getString("rejectionReason") ?: "",
                                    needsInfoReason = appDoc.getString("needsInfoReason") ?: ""
                                )
                                _applicationState.value = app
                                _isLoggedIn.value = false
                            } else {
                                _isBlocked.value = "No driver account found for this user. Please register as a driver."
                                _isLoggedIn.value = false
                            }
                        }
                        .addOnFailureListener {
                            _isBlocked.value = "Failed to check application: ${it.localizedMessage}"
                            _isLoggedIn.value = false
                        }
                }
            }
            .addOnFailureListener {
                _isBlocked.value = "Login verification failed: ${it.localizedMessage}"
                _isLoggedIn.value = false
            }
    }

    // LOGIN
    fun login(email: String, pass: String, onSuccess: () -> Unit) {
        if (email.isBlank() || pass.isBlank()) {
            showSnackbar("Please enter your email and password")
            return
        }

        auth.signInWithEmailAndPassword(email.trim(), pass.trim())
            .addOnSuccessListener { authResult ->
                val uid = authResult.user?.uid ?: return@addOnSuccessListener
                checkUserRoleAndListen(uid)
                onSuccess()
            }
            .addOnFailureListener {
                showSnackbar(it.localizedMessage ?: "Login failed")
            }
    }

    // FORGOT PASSWORD
    fun forgotPassword(email: String, onSuccess: () -> Unit) {
        if (email.isBlank()) {
            showSnackbar("Please enter your email to reset password")
            return
        }
        auth.sendPasswordResetEmail(email.trim())
            .addOnSuccessListener {
                showSnackbar("Password reset email sent to $email")
                onSuccess()
            }
            .addOnFailureListener {
                showSnackbar(it.localizedMessage ?: "Failed to send reset email")
            }
    }

    // REGISTER AS A DRIVER
    fun registerAsDriver(
        name: String,
        phone: String,
        email: String,
        pass: String,
        cityId: String,
        vehicleType: String,
        plate: String,
        vehicleModel: String,
        vehicleColor: String,
        seats: Int,
        vehiclePhotoUrl: String,
        franchiseUrl: String? = null,
        emergencyName: String,
        emergencyPhone: String,
        licenseUrl: String,
        orcrUrl: String,
        nbiUrl: String,
        validIdUrl: String,
        selfieUrl: String,
        onSuccess: () -> Unit
    ) {
        auth.createUserWithEmailAndPassword(email.trim(), pass.trim())
            .addOnSuccessListener { authResult ->
                val uid = authResult.user?.uid ?: return@addOnSuccessListener
                val docsMap = mutableMapOf(
                    "licenseUrl" to licenseUrl.trim(),
                    "orcrUrl" to orcrUrl.trim(),
                    "nbiUrl" to nbiUrl.trim(),
                    "validIdUrl" to validIdUrl.trim(),
                    "selfieUrl" to selfieUrl.trim(),
                    "vehiclePhotoUrl" to vehiclePhotoUrl.trim()
                )
                if (!franchiseUrl.isNullOrBlank()) {
                    docsMap["franchiseUrl"] = franchiseUrl.trim()
                }

                val applicationData = mapOf(
                    "ownerUid" to uid,
                    "name" to name.trim(),
                    "phone" to phone.trim(),
                    "email" to email.trim(),
                    "cityId" to cityId.trim(),
                    "vehicleType" to vehicleType,
                    "plate" to plate.trim().uppercase(),
                    "vehicleModel" to vehicleModel.trim(),
                    "vehicleColor" to vehicleColor.trim(),
                    "seats" to seats,
                    "emergencyContact" to mapOf("name" to emergencyName.trim(), "phone" to emergencyPhone.trim()),
                    "documents" to docsMap,
                    "status" to "Pending",
                    "submittedAt" to FieldValue.serverTimestamp()
                )

                db.collection("riderApplications").document(uid).set(applicationData)
                    .addOnSuccessListener {
                        showSnackbar("Application submitted. Wait for admin approval.")
                        checkUserRoleAndListen(uid)
                        onSuccess()
                    }
                    .addOnFailureListener {
                        showSnackbar("Application saved with error: ${it.localizedMessage}")
                    }
            }
            .addOnFailureListener {
                showSnackbar(it.localizedMessage ?: "Registration failed")
            }
    }

    // Legacy alias
    fun registerAsRider(
        name: String,
        phone: String,
        email: String,
        pass: String,
        cityId: String,
        vehicleType: String,
        plate: String,
        vehicleModel: String,
        emergencyName: String,
        emergencyPhone: String,
        licenseUrl: String,
        orcrUrl: String,
        nbiUrl: String,
        validIdUrl: String,
        selfieUrl: String,
        onSuccess: () -> Unit
    ) {
        registerAsDriver(
            name = name,
            phone = phone,
            email = email,
            pass = pass,
            cityId = cityId,
            vehicleType = vehicleType,
            plate = plate,
            vehicleModel = vehicleModel,
            vehicleColor = "",
            seats = if (vehicleType == OrderStepConstants.VEHICLE_CAR8) 8 else 4,
            vehiclePhotoUrl = "",
            franchiseUrl = null,
            emergencyName = emergencyName,
            emergencyPhone = emergencyPhone,
            licenseUrl = licenseUrl,
            orcrUrl = orcrUrl,
            nbiUrl = nbiUrl,
            validIdUrl = validIdUrl,
            selfieUrl = selfieUrl,
            onSuccess = onSuccess
        )
    }

    // RESUBMIT APPLICATION (when status is Needs Info)
    fun resubmitApplication(
        phone: String,
        vehicleModel: String,
        plate: String,
        licenseUrl: String,
        orcrUrl: String,
        nbiUrl: String,
        validIdUrl: String,
        selfieUrl: String,
        onSuccess: () -> Unit
    ) {
        val uid = auth.currentUser?.uid ?: return
        val updates = mutableMapOf<String, Any>(
            "phone" to phone.trim(),
            "vehicleModel" to vehicleModel.trim(),
            "plate" to plate.trim().uppercase(),
            "documents" to mapOf(
                "licenseUrl" to licenseUrl.trim(),
                "orcrUrl" to orcrUrl.trim(),
                "nbiUrl" to nbiUrl.trim(),
                "validIdUrl" to validIdUrl.trim(),
                "selfieUrl" to selfieUrl.trim()
            ),
            "status" to "Pending",
            "submittedAt" to FieldValue.serverTimestamp()
        )

        db.collection("riderApplications").document(uid).update(updates)
            .addOnSuccessListener {
                showSnackbar("Application updated and submitted for review.")
                checkUserRoleAndListen(uid)
                onSuccess()
            }
            .addOnFailureListener {
                showSnackbar(it.localizedMessage ?: "Failed to resubmit application")
            }
    }

    fun logout(onSuccess: () -> Unit) {
        riderRepository.stopListening()
        auth.signOut()
        _isLoggedIn.value = false
        _applicationState.value = null
        _isBlocked.value = null
        onSuccess()
    }

    // PROFILE UPDATE (phone only editable)
    fun updatePhone(newPhone: String) {
        riderRepository.updatePhone(newPhone,
            onSuccess = { showSnackbar("Phone number updated successfully") },
            onError = { showSnackbar(it) }
        )
    }

    // SERVICE TOGGLE
    fun toggleService(serviceKey: String, enabled: Boolean) {
        riderRepository.toggleServiceType(serviceKey, enabled,
            onSuccess = { showSnackbar("Service settings updated") },
            onError = { showSnackbar(it) }
        )
    }

    // ONLINE / OFFLINE TOGGLE
    fun toggleOnline(context: Context) {
        val currentOnline = rider.value?.isOnline ?: false
        val newOnline = !currentOnline
        riderRepository.setOnline(newOnline, context,
            onSuccess = {
                showSnackbar(if (newOnline) "You are now Online and receiving dispatches" else "You went Offline")
            },
            onError = { showSnackbar(it) }
        )
    }

    // ACCEPT OFFER BUNDLE
    fun acceptIncomingOfferBundle(bundle: OfferBundle) {
        triggerVibration()
        riderRepository.acceptOfferBundle(bundle,
            onSuccess = {
                val msg = when (bundle.type) {
                    OfferType.DOUBLE_BOOKING -> "Double booking accepted! Heading to pickup."
                    OfferType.ADD_TO_BOOKING -> "Order added to your booking! Heading to pickup."
                    else -> "Order accepted! Heading to pickup."
                }
                showSnackbar(msg)
            },
            onError = { errorDetails ->
                _acceptOrderError.value = errorDetails
            }
        )
    }

    // DECLINE OFFER BUNDLE
    fun declineIncomingOfferBundle(bundle: OfferBundle) {
        riderRepository.declineOfferBundle(bundle, prefsRepository)
    }

    // MISS OFFER BUNDLE
    fun missIncomingOfferBundle(bundle: OfferBundle) {
        riderRepository.missOfferBundle(bundle, prefsRepository)
    }

    // ACCEPT OFFER (Single or fallback)
    fun acceptIncomingOffer(orderId: String) {
        val bundle = incomingOfferBundle.value
        if (bundle != null && bundle.orders.any { it.id == orderId }) {
            acceptIncomingOfferBundle(bundle)
            return
        }
        triggerVibration()
        riderRepository.acceptOrder(orderId,
            onSuccess = {
                showSnackbar("Order accepted! Heading to pickup.")
            },
            onError = { errorDetails ->
                _acceptOrderError.value = errorDetails
            }
        )
    }

    // DECLINE OFFER
    fun declineIncomingOffer(orderId: String, serviceType: String = "") {
        val bundle = incomingOfferBundle.value
        if (bundle != null && bundle.orders.any { it.id == orderId }) {
            declineIncomingOfferBundle(bundle)
            return
        }
        val sType = serviceType.ifEmpty { incomingOffer.value?.serviceType ?: OrderStepConstants.SERVICE_FOOD }
        riderRepository.declineOffer(orderId, sType, prefsRepository)
    }

    // MISS OFFER (30s timer expired)
    fun missIncomingOffer(orderId: String, serviceType: String = "") {
        val bundle = incomingOfferBundle.value
        if (bundle != null && bundle.orders.any { it.id == orderId }) {
            missIncomingOfferBundle(bundle)
            return
        }
        val sType = serviceType.ifEmpty { incomingOffer.value?.serviceType ?: OrderStepConstants.SERVICE_FOOD }
        riderRepository.missOffer(orderId, sType, prefsRepository)
    }

    // ADVANCE STEP
    fun advanceCurrentOrderStep(finalReceiptTotal: Double? = null, targetOrderId: String? = null) {
        val targetId = targetOrderId ?: currentRouteStop.value?.orderId ?: currentActiveOrder.value?.id ?: return
        val order = activeOrders.value.firstOrNull { it.id == targetId } ?: currentActiveOrder.value ?: return
        triggerVibration()
        val step = order.riderStep.lowercase()
        val isFinal = when (order.serviceType.lowercase()) {
            OrderStepConstants.SERVICE_PARCEL -> step in listOf(OrderStepConstants.STEP_AT_RECIPIENT, OrderStepConstants.STEP_ARRIVED_AT_CUSTOMER)
            OrderStepConstants.SERVICE_TAXI -> order.isAtLastTaxiStop && step.startsWith(OrderStepConstants.STEP_TO_STOP_PREFIX)
            else -> step in listOf(OrderStepConstants.STEP_AT_CUSTOMER, OrderStepConstants.STEP_ARRIVED_AT_CUSTOMER)
        }
        riderRepository.advanceOrderStep(order, finalReceiptTotal,
            onSuccess = {
                if (isFinal) {
                    // Show celebration delivery success dialog with accurate codCollected
                    val codAmountFinal = if (order.isCod) order.cashAmountToCollect else 0.0
                    val finalStatus = if (order.isTaxi) OrderStepConstants.STATUS_COMPLETED else OrderStepConstants.STATUS_DELIVERED
                    _completedDeliveryOrder.value = order.copy(
                        status = finalStatus,
                        riderStep = OrderStepConstants.STEP_COMPLETED,
                        codCollected = codAmountFinal
                    )
                } else {
                    showSnackbar("Stage updated successfully")
                }
            },
            onError = { showSnackbar(it) }
        )
    }

    // RELEASE ORDER
    fun releaseOrder(reason: String) {
        val order = currentActiveOrder.value ?: return
        riderRepository.releaseOrder(order, reason, prefsRepository,
            onSuccess = {
                showSnackbar("Order released back to dispatch")
            },
            onError = { showSnackbar(it) }
        )
    }

    // ACCEPTANCE & CANCELLATION RATE STATS
    fun getFilteredOfferLogs(period: String): List<OfferLogEntry> {
        val allLogs = offerLogs.value
        val now = System.currentTimeMillis()
        val cutoff = when (period) {
            "7 days" -> now - (7L * 24 * 60 * 60 * 1000L)
            "30 days" -> now - (30L * 24 * 60 * 60 * 1000L)
            else -> 0L
        }
        return allLogs.filter { log ->
            if (cutoff <= 0L) true
            else {
                val millis = log.at?.toDate()?.time ?: log.atMillis
                millis >= cutoff
            }
        }.sortedByDescending { it.at?.toDate()?.time ?: it.atMillis }
    }

    fun calculateAcceptanceRate(logs: List<OfferLogEntry>): Double? {
        val accepted = logs.count { it.outcome == "accepted" }
        val declined = logs.count { it.outcome == "declined" }
        val missed = logs.count { it.outcome == "missed" }
        val total = accepted + declined + missed
        if (total == 0) return null
        return (accepted.toDouble() / total) * 100.0
    }

    fun calculateCancellationRate(logs: List<OfferLogEntry>): Double? {
        val accepted = logs.count { it.outcome == "accepted" }
        if (accepted == 0) return null
        val released = logs.count { it.finalOutcome == "released" }
        return (released.toDouble() / accepted) * 100.0
    }

    fun getServiceAcceptanceBreakdown(period: String): List<ServiceRateItem> {
        val logs = getFilteredOfferLogs(period)
        val services = OrderStepConstants.SERVICE_DEFINITIONS.map {
            it.key to it.displayName
        }
        return services.map { (key, displayName) ->
            val svcLogs = logs.filter {
                val st = it.serviceType.lowercase().trim()
                when (key) {
                    "convenience" -> st == "convenience" || st == "mart" || st == "instant"
                    "request_rider" -> st == "request_rider" || st == "request"
                    else -> st == key
                }
            }
            val accepted = svcLogs.count { it.outcome == "accepted" }
            val declined = svcLogs.count { it.outcome == "declined" }
            val missed = svcLogs.count { it.outcome == "missed" }
            val total = accepted + declined + missed
            if (total == 0) {
                ServiceRateItem(
                    serviceType = key,
                    displayName = displayName,
                    ratePercentage = null,
                    numerator = 0,
                    denominator = 0,
                    countLabel = "No data yet"
                )
            } else {
                val rate = (accepted.toDouble() / total) * 100.0
                ServiceRateItem(
                    serviceType = key,
                    displayName = displayName,
                    ratePercentage = rate,
                    numerator = accepted,
                    denominator = total,
                    countLabel = "$accepted of $total offers"
                )
            }
        }
    }

    fun getServiceCancellationBreakdown(period: String): List<ServiceRateItem> {
        val logs = getFilteredOfferLogs(period)
        val services = OrderStepConstants.SERVICE_DEFINITIONS.map {
            it.key to it.displayName
        }
        return services.map { (key, displayName) ->
            val svcLogs = logs.filter {
                val st = it.serviceType.lowercase().trim()
                when (key) {
                    "convenience" -> st == "convenience" || st == "mart" || st == "instant"
                    "request_rider" -> st == "request_rider" || st == "request"
                    else -> st == key
                }
            }
            val accepted = svcLogs.count { it.outcome == "accepted" }
            val released = svcLogs.count { it.finalOutcome == "released" }
            if (accepted == 0) {
                ServiceRateItem(
                    serviceType = key,
                    displayName = displayName,
                    ratePercentage = null,
                    numerator = 0,
                    denominator = 0,
                    countLabel = "No data yet"
                )
            } else {
                val rate = (released.toDouble() / accepted) * 100.0
                ServiceRateItem(
                    serviceType = key,
                    displayName = displayName,
                    ratePercentage = rate,
                    numerator = released,
                    denominator = accepted,
                    countLabel = "$released of $accepted accepted"
                )
            }
        }
    }

    // REPORT INCIDENT
    fun reportProblem(orderId: String, reason: String) {
        riderRepository.reportIncident(orderId, reason,
            onSuccess = { showSnackbar("Report submitted to dispatch") },
            onError = { showSnackbar(it) }
        )
    }

    // IN-APP CALL & SUPPORT
    fun startCall(name: String, phone: String) {
        if (phone.isBlank()) {
            showSnackbar("Phone number not available")
            return
        }
        _activeCall.value = ActiveCallInfo(recipientName = name, phoneNumber = phone)
    }

    fun endCall() {
        _activeCall.value = null
    }

    fun contactSupport(context: Context) {
        val phone = supportPhone.value
        if (!phone.isNullOrBlank()) {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            try {
                context.startActivity(intent)
            } catch (_: Exception) {
                showSnackbar("Unable to open dialer for support: $phone")
            }
        } else {
            showSnackbar("Support line is currently offline")
        }
    }

    // INBOX ACTIONS
    fun markInboxRead(msgId: String) {
        riderRepository.markInboxMessageRead(msgId)
    }

    fun markAllInboxRead() {
        riderRepository.markAllInboxRead()
    }

    private fun triggerVibration() {
        if (!prefsRepository.vibrationEnabled.value) return
        try {
            val ctx = getApplication<Application>().applicationContext
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                v?.vibrate(150)
            }
        } catch (_: Exception) {}
    }

    override fun onCleared() {
        riderRepository.stopListening()
        super.onCleared()
    }
}
