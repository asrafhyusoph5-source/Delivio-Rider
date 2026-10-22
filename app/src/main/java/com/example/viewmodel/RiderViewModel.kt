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
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    // Theme & preferences
    val isDarkMode = prefsRepository.isDarkMode
    val soundEnabled = prefsRepository.soundEnabled
    val vibrationEnabled = prefsRepository.vibrationEnabled
    val currencySymbol = prefsRepository.currencySymbol

    // Live state from repository
    val rider = riderRepository.rider
    val activeOrders = riderRepository.activeOrders
    val incomingOffer = riderRepository.incomingOffer
    val deliveredOrders = riderRepository.deliveredOrders
    val tierConfigs = riderRepository.tierConfigs
    val inboxMessages = riderRepository.inboxMessages
    val supportPhone = riderRepository.supportPhone
    val coins = riderRepository.coins
    val coinHistory = riderRepository.coinHistory
    val newlyReachedTier = riderRepository.newlyReachedTier
    val listenerError = riderRepository.listenerError
    val currentLocation = RiderLocationTracker.currentLocation

    // Order Switcher (index 0 or 1)
    private val _selectedOrderIndex = MutableStateFlow(0)
    val selectedOrderIndex: StateFlow<Int> = _selectedOrderIndex.asStateFlow()

    val currentActiveOrder: StateFlow<Order?> = combine(activeOrders, _selectedOrderIndex) { orders, idx ->
        if (orders.isEmpty()) null
        else orders.getOrNull(idx) ?: orders.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Auth & Application State
    private val _isLoggedIn = MutableStateFlow(auth.currentUser != null)
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

    // Wallet balance (live from riders/{uid}.walletBalance) & Withdrawable Earnings
    val walletBalance: StateFlow<Double> = rider.map { r ->
        r?.walletBalance ?: 0.0
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
        auth.currentUser?.let { user ->
            checkUserRoleAndListen(user.uid)
        }
        viewModelScope.launch {
            riderRepository.listenerError.collect { err ->
                err?.let {
                    showSnackbar(it)
                    riderRepository.clearListenerError()
                }
            }
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
                                    emergencyContact = emergencyMap?.entries?.associate { it.key.toString() to (it.value?.toString() ?: "") } ?: emptyMap(),
                                    documents = docsMap?.entries?.associate { it.key.toString() to (it.value?.toString() ?: "") } ?: emptyMap(),
                                    status = appDoc.getString("status") ?: "Pending",
                                    rejectionReason = appDoc.getString("rejectionReason") ?: "",
                                    needsInfoReason = appDoc.getString("needsInfoReason") ?: ""
                                )
                                _applicationState.value = app
                                _isLoggedIn.value = false
                            } else {
                                _isBlocked.value = "No rider account found for this user. Please register as a rider."
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

    // REGISTER AS A RIDER
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
        auth.createUserWithEmailAndPassword(email.trim(), pass.trim())
            .addOnSuccessListener { authResult ->
                val uid = authResult.user?.uid ?: return@addOnSuccessListener
                val applicationData = mapOf(
                    "ownerUid" to uid,
                    "name" to name.trim(),
                    "phone" to phone.trim(),
                    "email" to email.trim(),
                    "cityId" to cityId.trim(),
                    "vehicleType" to vehicleType,
                    "plate" to plate.trim().uppercase(),
                    "vehicleModel" to vehicleModel.trim(),
                    "emergencyContact" to mapOf("name" to emergencyName.trim(), "phone" to emergencyPhone.trim()),
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

    // ACCEPT OFFER
    fun acceptIncomingOffer(orderId: String) {
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
    fun declineIncomingOffer(orderId: String) {
        prefsRepository.addDeclinedOrderId(orderId)
    }

    // ADVANCE STEP
    fun advanceCurrentOrderStep(finalReceiptTotal: Double? = null) {
        val order = currentActiveOrder.value ?: return
        triggerVibration()
        val step = order.riderStep.lowercase()
        val isFinal = when (order.serviceType.lowercase()) {
            OrderStepConstants.SERVICE_PARCEL -> step in listOf(OrderStepConstants.STEP_AT_RECIPIENT, OrderStepConstants.STEP_ARRIVED_AT_CUSTOMER)
            else -> step in listOf(OrderStepConstants.STEP_AT_CUSTOMER, OrderStepConstants.STEP_ARRIVED_AT_CUSTOMER)
        }
        riderRepository.advanceOrderStep(order, finalReceiptTotal,
            onSuccess = {
                if (isFinal) {
                    // Show celebration delivery success dialog with accurate codCollected
                    val codAmountFinal = if (order.isCod) order.cashAmountToCollect else 0.0
                    _completedDeliveryOrder.value = order.copy(
                        status = OrderStepConstants.STATUS_DELIVERED,
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
        riderRepository.releaseOrder(order, reason,
            onSuccess = {
                showSnackbar("Order released back to dispatch")
            },
            onError = { showSnackbar(it) }
        )
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
