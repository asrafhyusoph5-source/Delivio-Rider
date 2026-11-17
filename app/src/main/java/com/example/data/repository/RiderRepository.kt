package com.example.data.repository

import android.content.Context
import com.example.data.constants.BatchConstants
import com.example.data.constants.DispatchSettings
import com.example.data.constants.OrderStepConstants
import com.example.data.constants.RiderPayPickupRate
import com.example.data.constants.RiderPayRate
import com.example.data.constants.RiderPaySettings
import com.example.data.models.*
import com.example.data.service.RiderLocationService
import com.example.data.service.RiderLocationTracker
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

class RiderRepository(
    private val appContext: Context? = com.example.DelivioApplication.appContextOrNull
) {

    private val auth: FirebaseAuth by lazy {
        val ctx = appContext ?: com.example.DelivioApplication.appContextOrNull
        if (ctx != null) com.example.data.firebase.FirebaseClientProvider.getAuth(ctx) else FirebaseAuth.getInstance()
    }
    private val db: FirebaseFirestore by lazy {
        val ctx = checkNotNull(appContext ?: com.example.DelivioApplication.appContextOrNull) {
            "Application Context is required to initialize FirebaseFirestore with R.string.firestore_database_id"
        }
        com.example.data.firebase.FirebaseClientProvider.getFirestore(ctx)
    }
    private val scope = CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
    private val _riderDocLocationUpdatedAtMs = MutableStateFlow(0L)
    val riderDocLocationUpdatedAtMs: StateFlow<Long> = _riderDocLocationUpdatedAtMs.asStateFlow()

    private val _onlineSinceMillis = MutableStateFlow(0L)
    val onlineSinceMillis: StateFlow<Long> = _onlineSinceMillis.asStateFlow()

    private val _dispatchListenerStatus = MutableStateFlow(DispatchListenerStatus())
    val dispatchListenerStatus: StateFlow<DispatchListenerStatus> = _dispatchListenerStatus.asStateFlow()

    private val _ordersSeenCount = MutableStateFlow(0)
    val ordersSeenCount: StateFlow<Int> = _ordersSeenCount.asStateFlow()

    private val _badDataSkippedCount = MutableStateFlow(0)
    val badDataSkippedCount: StateFlow<Int> = _badDataSkippedCount.asStateFlow()

    private val _eligibleOrdersCount = MutableStateFlow(0)
    val eligibleOrdersCount: StateFlow<Int> = _eligibleOrdersCount.asStateFlow()

    private val _skippedOrderRecords = MutableStateFlow<List<SkippedOrderRecord>>(emptyList())
    val skippedOrderRecords: StateFlow<List<SkippedOrderRecord>> = _skippedOrderRecords.asStateFlow()

    private val _lastTaxiDiagnostics = MutableStateFlow<TaxiOrderDiagnostics?>(null)
    val lastTaxiDiagnostics: StateFlow<TaxiOrderDiagnostics?> = _lastTaxiDiagnostics.asStateFlow()

    private val badDocRecords = ArrayDeque<SkippedOrderRecord>()
    @Volatile
    private var latestEvaluationSkips: List<SkippedOrderRecord> = emptyList()
    private val skipLock = Any()

    // Live state
    private val _rider = MutableStateFlow<Rider?>(null)
    val rider: StateFlow<Rider?> = _rider.asStateFlow()

    private val _activeOrders = MutableStateFlow<List<Order>>(emptyList())
    val activeOrders: StateFlow<List<Order>> = _activeOrders.asStateFlow()

    private val _incomingOffer = MutableStateFlow<Order?>(null)
    val incomingOffer: StateFlow<Order?> = _incomingOffer.asStateFlow()

    private val _incomingOfferBundle = MutableStateFlow<OfferBundle?>(null)
    val incomingOfferBundle: StateFlow<OfferBundle?> = _incomingOfferBundle.asStateFlow()

    private val _dispatchSettings = MutableStateFlow(DispatchSettings())
    val dispatchSettings: StateFlow<DispatchSettings> = _dispatchSettings.asStateFlow()

    private val _riderPaySettings = MutableStateFlow(RiderPaySettings())
    val riderPaySettings: StateFlow<RiderPaySettings> = _riderPaySettings.asStateFlow()

    private val _deliveredOrders = MutableStateFlow<List<Order>>(emptyList())
    val deliveredOrders: StateFlow<List<Order>> = _deliveredOrders.asStateFlow()

    private val _tierConfigs = MutableStateFlow<List<TierConfig>>(emptyList())
    val tierConfigs: StateFlow<List<TierConfig>> = _tierConfigs.asStateFlow()

    private val _inboxMessages = MutableStateFlow<List<MessageNotification>>(emptyList())
    val inboxMessages: StateFlow<List<MessageNotification>> = _inboxMessages.asStateFlow()

    private val _supportPhone = MutableStateFlow<String?>(null)
    val supportPhone: StateFlow<String?> = _supportPhone.asStateFlow()

    private val _coins = MutableStateFlow(0)
    val coins: StateFlow<Int> = _coins.asStateFlow()

    private val _coinHistory = MutableStateFlow<List<CoinTransaction>>(emptyList())
    val coinHistory: StateFlow<List<CoinTransaction>> = _coinHistory.asStateFlow()

    private val _newlyReachedTier = MutableStateFlow<TierConfig?>(null)
    val newlyReachedTier: StateFlow<TierConfig?> = _newlyReachedTier.asStateFlow()

    private val _walletTransactions = MutableStateFlow<List<WalletTransaction>>(emptyList())
    val walletTransactions: StateFlow<List<WalletTransaction>> = _walletTransactions.asStateFlow()

    private val _sharedCustomerWalletBalance = MutableStateFlow<Double?>(null)
    val sharedCustomerWalletBalance: StateFlow<Double?> = _sharedCustomerWalletBalance.asStateFlow()

    private val _riderTransactions = MutableStateFlow<List<WalletTransaction>>(emptyList())
    private val _customerTransactions = MutableStateFlow<List<WalletTransaction>>(emptyList())

    private val _platformServices = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val platformServices: StateFlow<Map<String, Boolean>> = _platformServices.asStateFlow()

    private val _scheduledReleaseLeadMinutes = MutableStateFlow(0.0)
    val scheduledReleaseLeadMinutes: StateFlow<Double> = _scheduledReleaseLeadMinutes.asStateFlow()

    private val _cityServices = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val cityServices: StateFlow<Map<String, Boolean>> = _cityServices.asStateFlow()

    private val _currentCityName = MutableStateFlow<String>("")
    val currentCityName: StateFlow<String> = _currentCityName.asStateFlow()

    private val _offerLogs = MutableStateFlow<List<OfferLogEntry>>(emptyList())
    val offerLogs: StateFlow<List<OfferLogEntry>> = _offerLogs.asStateFlow()

    private val _releasedOrderIds = MutableStateFlow<Set<String>>(emptySet())
    val releasedOrderIds: StateFlow<Set<String>> = _releasedOrderIds.asStateFlow()

    private val _candidateOrders = MutableStateFlow<List<Order>>(emptyList())
    val candidateOrders: StateFlow<List<Order>> = _candidateOrders.asStateFlow()

    private val _customerReviews = MutableStateFlow<List<CustomerReview>>(emptyList())
    val customerReviews: StateFlow<List<CustomerReview>> = _customerReviews.asStateFlow()

    private val _chatThreads = MutableStateFlow<Map<String, OrderChatThread>>(emptyMap())
    val chatThreads: StateFlow<Map<String, OrderChatThread>> = _chatThreads.asStateFlow()

    private val _activeChatMessages = MutableStateFlow<List<OrderChatMessage>>(emptyList())
    val activeChatMessages: StateFlow<List<OrderChatMessage>> = _activeChatMessages.asStateFlow()

    private val _lateTipBanner = MutableStateFlow<LateTipBannerInfo?>(null)
    val lateTipBanner: StateFlow<LateTipBannerInfo?> = _lateTipBanner.asStateFlow()

    fun dismissLateTipBanner() {
        _lateTipBanner.value = null
    }

    private val _supportTickets = MutableStateFlow<List<SupportTicket>>(emptyList())
    val supportTickets: StateFlow<List<SupportTicket>> = _supportTickets.asStateFlow()

    private val _unresolvedTickets = MutableStateFlow<List<SupportTicket>>(emptyList())
    val unresolvedTickets: StateFlow<List<SupportTicket>> = _unresolvedTickets.asStateFlow()

    private val _activeTicket = MutableStateFlow<SupportTicket?>(null)
    val activeTicket: StateFlow<SupportTicket?> = _activeTicket.asStateFlow()

    private val _activeTicketMessages = MutableStateFlow<List<SupportTicketMessage>>(emptyList())
    val activeTicketMessages: StateFlow<List<SupportTicketMessage>> = _activeTicketMessages.asStateFlow()

    private val _adminMessageBanner = MutableStateFlow<AdminMessageBannerInfo?>(null)
    val adminMessageBanner: StateFlow<AdminMessageBannerInfo?> = _adminMessageBanner.asStateFlow()

    fun dismissAdminMessageBanner() {
        _adminMessageBanner.value = null
    }

    private val _quests = MutableStateFlow<List<Quest>>(emptyList())
    val quests: StateFlow<List<Quest>> = _quests.asStateFlow()

    private val _questClaims = MutableStateFlow<Map<String, QuestClaim>>(emptyMap())
    val questClaims: StateFlow<Map<String, QuestClaim>> = _questClaims.asStateFlow()

    private val knownLateTips = mutableMapOf<String, Double>()
    private var deliveredListenerInitialized = false

    private val _listenerError = MutableStateFlow<String?>(null)
    val listenerError: StateFlow<String?> = _listenerError.asStateFlow()

    fun clearListenerError() {
        _listenerError.value = null
    }

    fun isPlatformServiceOn(serviceType: String): Boolean {
        val norm = serviceType.lowercase().trim()
        return _platformServices.value[norm] != false
    }

    fun isCityServiceOn(serviceType: String, cityId: String = ""): Boolean {
        val norm = serviceType.lowercase().trim()
        val checkCity = if (cityId.isNotBlank()) cityId else (_rider.value?.cityId ?: "")
        if (checkCity.isNotBlank()) {
            val cityVal = _cityServices.value[norm]
            if (cityVal == false) return false
        }
        return true
    }

    fun isServiceOn(serviceType: String, cityId: String = ""): Boolean {
        if (!isPlatformServiceOn(serviceType)) return false
        return isCityServiceOn(serviceType, cityId)
    }

    fun getCityDisplayName(): String {
        val name = _currentCityName.value.trim()
        if (name.isNotBlank()) return name
        val cId = _rider.value?.cityId?.trim() ?: ""
        if (cId.isNotBlank()) {
            return cId.replace("_", " ").split(" ")
                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        }
        return "City"
    }

    // Listener registrations
    private var riderListener: ListenerRegistration? = null
    private var linkedUserListener: ListenerRegistration? = null
    private var customerTransactionsListener: ListenerRegistration? = null
    private var currentLinkedCustomerUid: String? = null
    private var platformSettingsListener: ListenerRegistration? = null
    private var citySettingsListener: ListenerRegistration? = null
    private var currentCityId: String? = null
    private var offerLogListener: ListenerRegistration? = null
    private var reviewsListener: ListenerRegistration? = null
    private var activeOrdersListener: ListenerRegistration? = null
    private var offersListener: ListenerRegistration? = null
    private var deliveredOrdersListener: ListenerRegistration? = null
    private var tierConfigListener: ListenerRegistration? = null
    private var inboxDirectListener: ListenerRegistration? = null
    private var inboxAllListener: ListenerRegistration? = null
    private var inboxReadsListener: ListenerRegistration? = null
    private var coinTxListener: ListenerRegistration? = null
    private var walletTransactionsListener: ListenerRegistration? = null

    private var inboxDirectList = emptyList<MessageNotification>()
    private var inboxAllList = emptyList<MessageNotification>()
    private var readMessageIds = emptySet<String>()

    private var dispatchSettingsListener: ListenerRegistration? = null
    private var deliveryRatesListener: ListenerRegistration? = null
    private var currentRatesCityId: String? = null
    private var supportTicketsListener: ListenerRegistration? = null
    private var activeTicketMessagesListener: ListenerRegistration? = null
    private var questsListener: ListenerRegistration? = null
    private var questClaimsListener: ListenerRegistration? = null
    private var chatThreadsListener: ListenerRegistration? = null
    private var activeOrderChatMessagesListener: ListenerRegistration? = null
    private var currentChatId: String? = null
    private var lastChatCleanupTimeMillis = 0L
    private var activeUserPrefs: UserPreferencesRepository? = null
    @Volatile
    private var activeSessionUid: String? = null
    private var activeOrdersRetryJob: Job? = null
    private var offersRetryJob: Job? = null
    private var deliveredOrdersRetryJob: Job? = null
    private var waveDelayJob: Job? = null

    private fun refreshCombinedSkippedOrders() {
        synchronized(skipLock) {
            val merged = (badDocRecords.toList() + latestEvaluationSkips)
                .distinctBy { "${it.orderId}:${it.isBadData}" }
                .sortedByDescending { it.timestampMillis }
                .take(MAX_SKIP_RECORDS)
            _skippedOrderRecords.value = merged
        }
    }

    private fun recordBadOrderDocument(doc: DocumentSnapshot, sourceLabel: String, throwable: Throwable) {
        val rawData = try {
            doc.data
        } catch (_: Throwable) {
            null
        }
        val orderNumberHint = OrderDocumentParser.safeStringOrNull(rawData?.get("orderNumber"))
            ?: OrderDocumentParser.safeStringOrNull(rawData?.get("orderId"))
            ?: doc.id
        val serviceTypeHint = OrderDocumentParser.safeString(rawData?.get("serviceType"))
        android.util.Log.e(
            OrderDocumentParser.TAG,
            "[$sourceLabel] Bad order document skipped (id=${doc.id}): ${throwable.message}",
            throwable
        )
        com.example.util.CrashReportStore.logNonFatalParseError(
            appContext,
            "orders/${doc.id}",
            throwable
        )
        synchronized(skipLock) {
            _badDataSkippedCount.value = _badDataSkippedCount.value + 1
            badDocRecords.removeAll { it.orderId == doc.id }
            val detail = throwable.message?.take(60)?.ifBlank { "corrupt fields" } ?: "corrupt fields"
            badDocRecords.addFirst(
                SkippedOrderRecord(
                    orderId = doc.id,
                    displayLabel = SkippedOrderRecord.labelForBadDoc(doc.id, orderNumberHint, serviceTypeHint),
                    reason = "bad data ($detail)",
                    timestampMillis = System.currentTimeMillis(),
                    isBadData = true
                )
            )
            while (badDocRecords.size > MAX_SKIP_RECORDS) {
                badDocRecords.removeLast()
            }
        }
        refreshCombinedSkippedOrders()
    }

    companion object {
        private const val MAX_SKIP_RECORDS = 10
        private val LISTENER_BACKOFF_SECONDS = listOf(2, 5, 10)
    }

    private fun syncDeliveryRates(cityId: String) {
        if (cityId.isBlank() || cityId == currentRatesCityId) return
        currentRatesCityId = cityId
        deliveryRatesListener?.remove()
        deliveryRatesListener = db.collection("deliveryRates").document(cityId)
            .addSnapshotListener { doc, _ ->
                if (doc != null && doc.exists()) {
                    val payMap = doc.get("riderPay") as? Map<*, *>
                    if (payMap != null) {
                        val singleMap = payMap["single"] as? Map<*, *>
                        val doubleMap = payMap["double"] as? Map<*, *>
                        val firstMap = doubleMap?.get("first") as? Map<*, *>
                        val secondMap = doubleMap?.get("second") as? Map<*, *>
                        val thirdMap = doubleMap?.get("third") as? Map<*, *>
                        val pickupMap = payMap["pickup"] as? Map<*, *>

                        _riderPaySettings.value = RiderPaySettings(
                            single = RiderPayRate(
                                minFare = (singleMap?.get("minFare") as? Number)?.toDouble() ?: 45.0,
                                includedKm = (singleMap?.get("includedKm") as? Number)?.toDouble() ?: 2.0,
                                perKmExtra = (singleMap?.get("perKmExtra") as? Number)?.toDouble() ?: 10.0
                            ),
                            doubleFirst = RiderPayRate(
                                minFare = (firstMap?.get("minFare") as? Number)?.toDouble() ?: 40.0,
                                includedKm = (firstMap?.get("includedKm") as? Number)?.toDouble() ?: 2.0,
                                perKmExtra = (firstMap?.get("perKmExtra") as? Number)?.toDouble() ?: 9.0
                            ),
                            doubleSecond = RiderPayRate(
                                minFare = (secondMap?.get("minFare") as? Number)?.toDouble() ?: 35.0,
                                includedKm = (secondMap?.get("includedKm") as? Number)?.toDouble() ?: 2.0,
                                perKmExtra = (secondMap?.get("perKmExtra") as? Number)?.toDouble() ?: 8.0
                            ),
                            doubleThird = RiderPayRate(
                                minFare = (thirdMap?.get("minFare") as? Number)?.toDouble() ?: 30.0,
                                includedKm = (thirdMap?.get("includedKm") as? Number)?.toDouble() ?: 2.0,
                                perKmExtra = (thirdMap?.get("perKmExtra") as? Number)?.toDouble() ?: 8.0
                            ),
                            pickup = RiderPayPickupRate(
                                minFare = (pickupMap?.get("minFare") as? Number)?.toDouble() ?: 15.0,
                                freeKm = (pickupMap?.get("freeKm") as? Number)?.toDouble() ?: 1.0,
                                perKmExtra = (pickupMap?.get("perKmExtra") as? Number)?.toDouble() ?: 8.0
                            )
                        )
                    }
                }
            }
    }

    private fun buildOfferBundle(
        type: OfferType,
        orders: List<Order>,
        driverLoc: Pair<Double, Double>?,
        paySettings: RiderPaySettings,
        dispatchSettings: DispatchSettings,
        startingRank: Int = 1,
        isPriorityRider: Boolean = false
    ): OfferBundle {
        val firstOrder = orders.first()
        val firstPickupDist = if (driverLoc != null && firstOrder.merchantLat != 0.0 && firstOrder.merchantLng != 0.0) {
            BatchConstants.distanceKm(driverLoc.first, driverLoc.second, firstOrder.merchantLat, firstOrder.merchantLng)
        } else {
            if (firstOrder.pickupDistanceKm > 0.0) firstOrder.pickupDistanceKm else 1.5
        }

        val isBatch = orders.size > 1 || startingRank > 1
        val pickupFee = if (startingRank == 1 && !firstOrder.isSplitChild && !firstOrder.badge.equals("split", ignoreCase = true)) {
            BatchConstants.calculatePickupFee(firstPickupDist, 1, paySettings)
        } else 0.0

        var runningTripDist = 0.0
        val breakdowns = orders.mapIndexed { idx, ord ->
            val rank = startingRank + idx
            val isSplit = ord.isSplitChild || ord.badge.equals("split", ignoreCase = true)
            val delFee = if (isSplit && (ord.riderFeeFixed ?: 0.0) > 0.0) {
                ord.riderFeeFixed ?: 0.0
            } else {
                BatchConstants.calculateDeliveryFee(ord, rank, isBatch, paySettings, dispatchSettings.specialRadiusRiderExtra)
            }
            val pFee = if (rank == 1 && !isSplit) pickupFee else 0.0
            val tip = ord.tip
            val totalPay = delFee + pFee + tip
            val tripDist = if (ord.deliveryDistanceKm > 0.0) ord.deliveryDistanceKm else 2.5
            runningTripDist += tripDist

            OrderPayBreakdown(
                orderId = ord.id,
                orderNumber = ord.orderNumber,
                rank = rank,
                deliveryFee = delFee,
                pickupFee = pFee,
                tip = tip,
                totalPay = totalPay,
                isCod = ord.isCod,
                codAmount = if (ord.isCod) ord.cashAmountToCollect else 0.0,
                badge = if (isSplit) "SPLIT" else ord.badge,
                customerArea = ord.customerArea
            )
        }

        val totalDriverPay = breakdowns.sumOf { it.totalPay }
        val km = floor(firstPickupDist).toInt()
        val hasInstantOrder = orders.any { it.isRequestRider }
        val hasTaxiOrder = orders.any { it.isTaxi }
        val waveDelaySeconds = if (hasInstantOrder) {
            if (isPriorityRider) 0 else dispatchSettings.priorityHeadStartSeconds + (km * dispatchSettings.waveSeconds)
        } else if (isPriorityRider) {
            min(km * dispatchSettings.waveSeconds, dispatchSettings.priorityHeadStartSeconds)
        } else {
            dispatchSettings.priorityHeadStartSeconds + (km * dispatchSettings.waveSeconds)
        }
        val eligibleSince = orders.minOfOrNull { ord ->
            if (ord.acceptedAtMillis > 0L) {
                ord.acceptedAtMillis
            } else if (ord.holdUntilMillis > 0L) {
                ord.holdUntilMillis - (dispatchSettings.holdMinutes * 60 * 1000).toLong()
            } else {
                System.currentTimeMillis()
            }
        } ?: System.currentTimeMillis()

        val combinedBadge = when {
            orders.any { it.isPriority } -> "Priority"
            orders.any { it.badge.equals("split", ignoreCase = true) } -> "SPLIT"
            orders.any { it.badge.equals("saver", ignoreCase = true) } -> "Saver"
            type == OfferType.DOUBLE_BOOKING -> "Double Booking"
            type == OfferType.ADD_TO_BOOKING -> "Add-on"
            else -> firstOrder.badge
        }

        val timerSeconds = when {
            hasInstantOrder -> com.example.data.constants.InstantDispatchEvaluator.INSTANT_TIMER_SECONDS
            hasTaxiOrder -> com.example.data.constants.TaxiDispatchEvaluator.TAXI_TIMER_SECONDS
            else -> com.example.data.constants.InstantDispatchEvaluator.DEFAULT_TIMER_SECONDS
        }

        return OfferBundle(
            id = orders.joinToString("_") { it.id },
            type = type,
            orders = orders,
            firstPickupDistanceKm = firstPickupDist,
            totalTripDistanceKm = runningTripDist,
            pickupFee = pickupFee,
            breakdowns = breakdowns,
            totalDriverPay = totalDriverPay,
            eligibleSince = eligibleSince,
            waveDelaySeconds = waveDelaySeconds,
            badge = combinedBadge,
            timerSeconds = timerSeconds
        )
    }

    private fun mergeWalletTransactions() {
        val merged = (_riderTransactions.value + _customerTransactions.value)
            .distinctBy { it.id }
            .sortedByDescending { it.dateRaw }
        _walletTransactions.value = merged
    }

    private fun syncLinkedCustomer(linkedUid: String?) {
        if (linkedUid == currentLinkedCustomerUid) return
        currentLinkedCustomerUid = linkedUid

        linkedUserListener?.remove()
        linkedUserListener = null
        customerTransactionsListener?.remove()
        customerTransactionsListener = null

        if (linkedUid != null) {
            // Live balance from users/{linkedCustomerUid}.walletBalance
            linkedUserListener = db.collection("users").document(linkedUid)
                .addSnapshotListener { userDoc, error ->
                    if (error != null) {
                        _listenerError.value = "Shared customer wallet error: ${error.localizedMessage}"
                        return@addSnapshotListener
                    }
                    val bal = userDoc?.getDouble("walletBalance") ?: 0.0
                    _sharedCustomerWalletBalance.value = bal
                }

            // Query walletTransactions where customerId == linkedCustomerUid (no orderBy; sort in code)
            customerTransactionsListener = db.collection("walletTransactions")
                .whereEqualTo("customerId", linkedUid)
                .addSnapshotListener { snapshots, error ->
                    if (error != null) {
                        _listenerError.value = "Customer wallet history error: ${error.localizedMessage}"
                        return@addSnapshotListener
                    }
                    val txs = snapshots?.documents?.mapNotNull { parseWalletTransaction(it) } ?: emptyList()
                    _customerTransactions.value = txs
                    mergeWalletTransactions()
                }
        } else {
            _sharedCustomerWalletBalance.value = null
            _customerTransactions.value = emptyList()
            mergeWalletTransactions()
        }
    }

    fun startListeningForRider(uid: String, userPrefs: UserPreferencesRepository) {
        stopListening()
        activeSessionUid = uid
        activeUserPrefs = userPrefs
        _onlineSinceMillis.value = userPrefs.getOnlineSinceMillis()

        // 1. Listen to riders/{uid}
        riderListener = db.collection("riders").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _listenerError.value = "Rider profile listener: ${error.localizedMessage}"
                    return@addSnapshotListener
                }
                if (snapshot == null || !snapshot.exists()) return@addSnapshotListener
                val allowed = (snapshot.get("allowedServices") as? List<*>)
                    ?.mapNotNull { it?.toString()?.trim()?.lowercase(Locale.US)?.takeIf { s -> s.isNotEmpty() } }
                    ?: OrderStepConstants.ALL_SERVICES
                val active = (snapshot.get("activeServices") as? List<*>)
                    ?.mapNotNull { it?.toString()?.trim()?.lowercase(Locale.US)?.takeIf { s -> s.isNotEmpty() } }
                    ?: allowed

                val vehicleMap = snapshot.get("vehicle") as? Map<*, *>
                val vehicleTypeVal = vehicleMap?.get("type")?.toString()?.takeIf { it.isNotBlank() }
                    ?: snapshot.getString("vehicleType")?.takeIf { it.isNotBlank() }
                    ?: OrderStepConstants.VEHICLE_MOTORCYCLE
                val vehicle = Vehicle(
                    type = vehicleTypeVal,
                    model = vehicleMap?.get("model")?.toString() ?: snapshot.getString("vehicleModel") ?: "",
                    plateNumber = vehicleMap?.get("plateNumber")?.toString() ?: snapshot.getString("plate") ?: "",
                    color = vehicleMap?.get("color")?.toString() ?: ""
                )

                val linkedCustomerUid = snapshot.getString("linkedCustomerUid")?.trim()?.takeIf { it.isNotEmpty() }

                val docsRaw = snapshot.get("documents")
                val docsList = when (docsRaw) {
                    is List<*> -> docsRaw.mapNotNull { item ->
                        val m = item as? Map<*, *> ?: return@mapNotNull null
                        val type = m["type"]?.toString()?.trim() ?: ""
                        val exp = m["expiresAt"]
                        if (type.isNotBlank()) RiderDocument(type = type, expiresAt = exp) else null
                    }
                    else -> emptyList()
                }

                val locMap = snapshot.get("location") as? Map<*, *>
                val rawLocUpdated = locMap?.get("updatedAt") ?: snapshot.get("locationUpdatedAt") ?: snapshot.get("lastSeenAt")
                _riderDocLocationUpdatedAtMs.value = OrderDocumentParser.parseTolerantTimeMillisOrZero(rawLocUpdated)
                val docLat = OrderDocumentParser.safeDouble(locMap?.get("lat"))
                    ?: OrderDocumentParser.safeDouble(locMap?.get("latitude"))
                    ?: OrderDocumentParser.safeDouble(snapshot.get("lat"))
                    ?: 0.0
                val docLng = OrderDocumentParser.safeDouble(locMap?.get("lng"))
                    ?: OrderDocumentParser.safeDouble(locMap?.get("longitude"))
                    ?: OrderDocumentParser.safeDouble(snapshot.get("lng"))
                    ?: 0.0
                if (RiderLocationTracker.currentLocation.value == null && docLat != 0.0 && docLng != 0.0) {
                    RiderLocationTracker.updateLocation(docLat, docLng)
                }

                val isOnlineNow = snapshot.getBoolean("isOnline") ?: false
                if (isOnlineNow) {
                    val serverOnlineSince = OrderDocumentParser.parseTolerantTimeMillisOrZero(snapshot.get("onlineSince"))
                    val resolvedSince = when {
                        serverOnlineSince > 0L -> serverOnlineSince
                        _onlineSinceMillis.value > 0L -> _onlineSinceMillis.value
                        userPrefs.getOnlineSinceMillis() > 0L -> userPrefs.getOnlineSinceMillis()
                        else -> OrderDocumentParser.parseTolerantTimeMillisOrZero(
                            snapshot.get("lastOnlineAt") ?: snapshot.get("lastSeenAt")
                        ).takeIf { it > 0L } ?: System.currentTimeMillis()
                    }
                    _onlineSinceMillis.value = resolvedSince
                    userPrefs.setOnlineSinceMillis(resolvedSince)
                } else {
                    if (_onlineSinceMillis.value != 0L) {
                        _onlineSinceMillis.value = 0L
                        userPrefs.setOnlineSinceMillis(0L)
                    }
                }

                val r = Rider(
                    uid = uid,
                    riderCode = snapshot.getString("riderCode") ?: snapshot.getString("code") ?: ("RD-" + uid.takeLast(4).uppercase()),
                    name = sanitizeDisplayName(snapshot.getString("name"), "Rider"),
                    email = snapshot.getString("email") ?: (auth.currentUser?.email ?: ""),
                    phone = snapshot.getString("phone") ?: "",
                    photoUrl = snapshot.getString("photoUrl") ?: snapshot.getString("avatarUrl") ?: "",
                    vehicle = vehicle,
                    cityId = snapshot.getString("cityId") ?: "puerto_princesa",
                    rating = snapshot.getDouble("rating") ?: 5.0,
                    memberSince = snapshot.getString("memberSince") ?: "Active Member",
                    isOnline = isOnlineNow,
                    status = snapshot.getString("status") ?: "active",
                    allowedServices = allowed,
                    activeServices = active,
                    walletBalance = snapshot.getDouble("walletBalance") ?: 0.0,
                    linkedCustomerUid = linkedCustomerUid,
                    cashRemitted = snapshot.getDouble("cashRemitted") ?: 0.0,
                    coins = snapshot.getLong("coins")?.toInt() ?: 0,
                    totalTrips = snapshot.getLong("totalTrips")?.toInt() ?: 0,
                    emergencyContactName = snapshot.getString("emergencyContactName") ?: "",
                    emergencyContactPhone = snapshot.getString("emergencyContactPhone") ?: "",
                    priorityRider = snapshot.getBoolean("priorityRider") ?: false,
                    documents = docsList
                )
                _rider.value = r
                _coins.value = r.coins

                syncLinkedCustomer(linkedCustomerUid)

                // Sync city services and delivery rates when rider cityId updates
                if (r.cityId.isNotBlank()) {
                    syncDeliveryRates(r.cityId)
                }
                if (r.cityId.isNotBlank() && r.cityId != currentCityId) {
                    currentCityId = r.cityId
                    citySettingsListener?.remove()
                    citySettingsListener = db.collection("cities").document(r.cityId)
                        .addSnapshotListener { cSnap, _ ->
                            if (cSnap != null && cSnap.exists()) {
                                val svcs = (cSnap.get("services") as? Map<*, *>)?.mapNotNull { (k, v) ->
                                    val key = k?.toString() ?: return@mapNotNull null
                                    val bool = when (v) {
                                        is Boolean -> v
                                        is String -> v.toBooleanStrictOrNull() ?: true
                                        else -> true
                                    }
                                    key to bool
                                }?.toMap() ?: emptyMap()
                                _cityServices.value = svcs
                                val name = cSnap.getString("name") ?: cSnap.getString("cityName") ?: r.cityId
                                _currentCityName.value = name
                                evaluateOffers(activeUserPrefs)
                            }
                        }
                }
                evaluateOffers(activeUserPrefs)
            }

        // 2. Listen to active orders assigned to rider (with automatic backoff restart)
        attachActiveOrdersListener(uid, attempt = 0)

        // 3. Listen to incoming offers (with automatic backoff restart)
        attachOffersListener(uid, attempt = 0)

        // 4. Delivered orders for earnings & wallet (with automatic backoff restart)
        attachDeliveredOrdersListener(uid, attempt = 0)

        // 5. Tier configs
        tierConfigListener = db.collection("tierConfig")
            .orderBy("sortOrder", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null) return@addSnapshotListener
                val configs = snapshots.documents.mapNotNull { doc ->
                    try {
                        TierConfig(
                            id = doc.id,
                            name = doc.getString("name") ?: doc.id,
                            ridesRequired = doc.getLong("ridesRequired")?.toInt() ?: 0,
                            coins = doc.getLong("coins")?.toInt() ?: 0,
                            sortOrder = doc.getLong("sortOrder")?.toInt() ?: 0
                        )
                    } catch (e: Exception) {
                        com.example.util.CrashReportStore.logNonFatalParseError(appContext, doc.reference.path, e)
                        null
                    }
                }
                _tierConfigs.value = configs
            }

        // 6. Inbox: riderId == uid
        inboxDirectListener = db.collection("inbox")
            .whereEqualTo("riderId", uid)
            .addSnapshotListener { snapshots, _ ->
                inboxDirectList = snapshots?.documents?.mapNotNull { parseInbox(it) } ?: emptyList()
                mergeInbox()
            }

        // 7. Inbox: audience == "all_riders"
        inboxAllListener = db.collection("inbox")
            .whereEqualTo("audience", "all_riders")
            .addSnapshotListener { snapshots, _ ->
                inboxAllList = snapshots?.documents?.mapNotNull { parseInbox(it) } ?: emptyList()
                mergeInbox()
            }

        // 8. Inbox read states
        inboxReadsListener = db.collection("riders").document(uid).collection("inboxReads")
            .addSnapshotListener { snapshots, _ ->
                readMessageIds = snapshots?.documents?.map { it.id }?.toSet() ?: emptySet()
                mergeInbox()
            }

        // 9. Coin Transactions
        coinTxListener = db.collection("riders").document(uid).collection("coinTransactions")
            .addSnapshotListener { snapshots, _ ->
                _coinHistory.value = snapshots?.documents?.mapNotNull { doc ->
                    try {
                        CoinTransaction(
                            id = doc.id,
                            title = doc.getString("title") ?: "Coin Credit",
                            amount = doc.getLong("amount")?.toInt() ?: 0,
                            timestampText = doc.getString("timestampText") ?: "Recent",
                            isCredit = doc.getBoolean("isCredit") ?: true
                        )
                    } catch (e: Exception) {
                        com.example.util.CrashReportStore.logNonFatalParseError(appContext, doc.reference.path, e)
                        null
                    }
                } ?: emptyList()
            }

        // 10. Platform settings for support phone and service status
        platformSettingsListener = db.collection("settings").document("platform")
            .addSnapshotListener { doc, _ ->
                if (doc != null && doc.exists()) {
                    _supportPhone.value = doc.getString("supportPhone")
                    val leadMin = (doc.get("scheduledReleaseLeadMinutes") as? Number)?.toDouble() ?: 0.0
                    _scheduledReleaseLeadMinutes.value = leadMin
                    val svcs = (doc.get("services") as? Map<*, *>)?.mapNotNull { (k, v) ->
                        val key = k?.toString() ?: return@mapNotNull null
                        val bool = when (v) {
                            is Boolean -> v
                            is String -> v.toBooleanStrictOrNull() ?: true
                            else -> true
                        }
                        key to bool
                    }?.toMap() ?: emptyMap()
                    _platformServices.value = svcs
                    evaluateOffers(activeUserPrefs)
                }
            }

        // 10b. Dispatch settings: settings/dispatch
        dispatchSettingsListener = db.collection("settings").document("dispatch")
            .addSnapshotListener { doc, _ ->
                if (doc != null && doc.exists()) {
                    val holdMin = (doc.get("holdMinutes") as? Number)?.toDouble() ?: 5.0
                    val maxB = (doc.get("maxBatch") as? Number)?.toInt() ?: 2
                    val pPickup = (doc.get("poolMaxPickupGapKm") as? Number)?.toDouble() ?: 2.0
                    val pDrop = (doc.get("poolMaxDropoffGapKm") as? Number)?.toDouble() ?: 2.5
                    val searchRad = (doc.get("searchRadiusKm") as? Number)?.toDouble() ?: 8.0
                    val waveSec = (doc.get("waveSeconds") as? Number)?.toInt() ?: 5
                    val specialMap = doc.get("specialRadius") as? Map<*, *>
                    val riderExtra = (specialMap?.get("riderExtra") as? Number)?.toDouble() ?: 0.0
                    val pMaxBatch = (doc.get("priorityMaxBatch") as? Number)?.toInt() ?: 4
                    val pHeadStart = (doc.get("priorityHeadStartSeconds") as? Number)?.toInt() ?: 15
                    val martBatching = doc.getBoolean("martBatching") ?: false
                    val taxiMap = doc.get("taxi") as? Map<*, *>
                    val taxiRadius = (doc.get("taxiSearchRadiusKm") as? Number)?.toDouble()
                        ?: (doc.get("taxiRadiusKm") as? Number)?.toDouble()
                        ?: (taxiMap?.get("searchRadiusKm") as? Number)?.toDouble()
                        ?: 5.0
                    _dispatchSettings.value = DispatchSettings(
                        holdMinutes = holdMin,
                        maxBatch = maxB,
                        poolMaxPickupGapKm = pPickup,
                        poolMaxDropoffGapKm = pDrop,
                        searchRadiusKm = searchRad,
                        waveSeconds = waveSec,
                        specialRadiusRiderExtra = riderExtra,
                        priorityMaxBatch = pMaxBatch,
                        priorityHeadStartSeconds = pHeadStart,
                        martBatching = martBatching,
                        taxiSearchRadiusKm = taxiRadius
                    )
                    evaluateOffers(activeUserPrefs)
                } else if (doc != null && !doc.exists()) {
                    _dispatchSettings.value = DispatchSettings()
                    evaluateOffers(activeUserPrefs)
                }
            }

        // 11. Wallet history: query walletTransactions where riderId == uid (no orderBy; sort newest first in code)
        walletTransactionsListener = db.collection("walletTransactions")
            .whereEqualTo("riderId", uid)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    _listenerError.value = "Wallet history error: ${error.localizedMessage}"
                    return@addSnapshotListener
                }
                if (snapshots == null) return@addSnapshotListener
                _riderTransactions.value = snapshots.documents.mapNotNull { parseWalletTransaction(it) }
                mergeWalletTransactions()
            }

        // 12. Offer log listener: riders/{uid}/offerLog
        offerLogListener = db.collection("riders").document(uid).collection("offerLog")
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null) return@addSnapshotListener
                val logs = snapshots.documents.mapNotNull { doc ->
                    try {
                        val oid = doc.getString("orderId") ?: doc.id
                        val st = doc.getString("serviceType") ?: ""
                        val outcome = doc.getString("outcome") ?: ""
                        val finalOutcome = doc.getString("finalOutcome")
                        val atTimestamp = doc.getTimestamp("at")
                        val finalAtTimestamp = doc.getTimestamp("finalAt")
                        OfferLogEntry(
                            orderId = oid,
                            serviceType = st,
                            outcome = outcome,
                            finalOutcome = finalOutcome,
                            at = atTimestamp,
                            finalAt = finalAtTimestamp
                        )
                    } catch (e: Exception) {
                        com.example.util.CrashReportStore.logNonFatalParseError(appContext, doc.reference.path, e)
                        null
                    }
                }
                _offerLogs.value = logs
                val releasedIds = logs.filter { it.finalOutcome == "released" }.map { it.orderId }.toSet()
                _releasedOrderIds.value = releasedIds
            }

        // 13. Customer reviews listener: reviews where riderId == uid, limit 200
        reviewsListener = db.collection("reviews")
            .whereEqualTo("riderId", uid)
            .limit(200)
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null) return@addSnapshotListener
                val revs = snapshots.documents.mapNotNull { doc ->
                    try {
                        val ratingVal = doc.getDouble("ratingRider")
                            ?: (doc.getDouble("stars") ?: (doc.getLong("stars")?.toDouble() ?: 0.0))
                        val comment = doc.getString("comment") ?: doc.getString("review") ?: ""
                        val rawCustomerName = doc.getString("customerName") ?: doc.getString("userName")
                        val customerName = sanitizeDisplayName(rawCustomerName, "Customer")
                        val orderNum = doc.getString("orderNumber") ?: doc.getString("orderId") ?: doc.id
                        val serviceType = doc.getString("serviceType") ?: doc.getString("service") ?: "Food"
                        val dateStr = doc.getString("date") ?: ""
                        val ts = doc.getTimestamp("createdAt") ?: doc.getTimestamp("timestamp") ?: doc.getTimestamp("date")
                        val dateMillis = ts?.toDate()?.time ?: 0L
                        CustomerReview(
                            id = doc.id,
                            riderId = doc.getString("riderId") ?: uid,
                            ratingRider = ratingVal,
                            comment = comment,
                            customerName = customerName,
                            orderNumber = orderNum,
                            serviceType = serviceType,
                            date = dateStr,
                            createdAt = ts,
                            dateMillis = dateMillis
                        )
                    } catch (e: Exception) {
                        com.example.util.CrashReportStore.logNonFatalParseError(appContext, doc.reference.path, e)
                        null
                    }
                }.sortedByDescending { it.dateMillis }
                _customerReviews.value = revs
            }

        // 14. Support tickets listener: supportTickets where riderId == uid
        supportTicketsListener = db.collection("supportTickets")
            .whereEqualTo("riderId", uid)
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null) return@addSnapshotListener
                val tickets = snapshots.documents.mapNotNull { parseSupportTicket(it) }
                    .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                _supportTickets.value = tickets
                val unresolved = tickets.filter { it.isUnresolved }
                _unresolvedTickets.value = unresolved

                val currentActiveId = _activeTicket.value?.id
                if (currentActiveId != null) {
                    val updated = tickets.firstOrNull { it.id == currentActiveId }
                    if (updated != null) {
                        _activeTicket.value = updated
                    }
                }
            }

        // 15. Quests listener: quests where status == "active"
        questsListener = db.collection("quests")
            .whereEqualTo("status", "active")
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null || snapshots.isEmpty) {
                    if (_quests.value.isEmpty()) {
                        _quests.value = getDemoQuests()
                    }
                    return@addSnapshotListener
                }
                val parsed = snapshots.documents.mapNotNull { parseQuest(it) }
                if (parsed.isNotEmpty()) {
                    _quests.value = parsed
                } else {
                    _quests.value = getDemoQuests()
                }
                checkAndCreateClaimsForEndedQuests()
            }

        // 16. QuestClaims listener: questClaims where riderId == uid
        questClaimsListener = db.collection("questClaims")
            .whereEqualTo("riderId", uid)
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null) return@addSnapshotListener
                val claims = snapshots.documents.mapNotNull { parseQuestClaim(it) }
                _questClaims.value = claims.associateBy { it.questId }
            }

        // 17. Customer Chat Threads (where riderId == uid and channel == "rider")
        chatThreadsListener?.remove()
        chatThreadsListener = db.collection("orderChats")
            .whereEqualTo("riderId", uid)
            .whereEqualTo("channel", "rider")
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null) return@addSnapshotListener
                val threads = snapshots.documents.mapNotNull { parseChatThread(it) }
                _chatThreads.value = threads.associateBy { it.orderId }
                cleanupExpiredChats()
            }
    }

    private fun attachActiveOrdersListener(uid: String, attempt: Int) {
        if (activeSessionUid != uid) return
        activeOrdersRetryJob?.cancel()
        activeOrdersListener?.remove()
        activeOrdersListener = db.collection("orders")
            .whereEqualTo("riderId", uid)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    android.util.Log.e(
                        OrderDocumentParser.TAG,
                        "Active orders listener error (attempt=$attempt): ${error.message}",
                        error
                    )
                    _listenerError.value = "Active orders listener: ${error.localizedMessage}"
                    val delaySec = LISTENER_BACKOFF_SECONDS.getOrElse(attempt) { LISTENER_BACKOFF_SECONDS.last() }
                    _dispatchListenerStatus.value = _dispatchListenerStatus.value.copy(
                        state = ListenerConnectionState.RESTARTING,
                        restartAttempt = attempt + 1,
                        nextRetrySeconds = delaySec,
                        lastError = error.localizedMessage
                    )
                    activeOrdersListener?.remove()
                    activeOrdersListener = null
                    activeOrdersRetryJob?.cancel()
                    activeOrdersRetryJob = scope.launch {
                        kotlinx.coroutines.delay(delaySec * 1000L)
                        if (activeSessionUid == uid) {
                            attachActiveOrdersListener(uid, attempt + 1)
                        }
                    }
                    return@addSnapshotListener
                }
                if (snapshots == null) return@addSnapshotListener
                _dispatchListenerStatus.value = DispatchListenerStatus(
                    state = ListenerConnectionState.CONNECTED,
                    lastUpdateMillis = System.currentTimeMillis(),
                    restartAttempt = 0,
                    nextRetrySeconds = 0,
                    lastError = null
                )
                val activeList = snapshots.documents.mapNotNull { parseOrderSafely(it, "activeOrders") }.filter { order ->
                    val s = order.status.trim().lowercase()
                    val step = order.riderStep.trim().lowercase()
                    s != "delivered" && s != "cancelled" && s != "canceled" && s != "rejected" && step != "completed"
                }
                _activeOrders.value = activeList
                val activeIds = activeList.map { it.id }.toSet()
                RiderLocationTracker.setActiveOrderIds(activeIds)
                evaluateOffers(activeUserPrefs)
            }
    }

    private fun attachOffersListener(uid: String, attempt: Int) {
        if (activeSessionUid != uid) return
        offersRetryJob?.cancel()
        offersListener?.remove()
        offersListener = db.collection("orders")
            .whereEqualTo("riderId", null)
            .whereIn(
                "status",
                listOf(
                    OrderStepConstants.STATUS_PLACED_ORDER,
                    OrderStepConstants.STATUS_PREPARING,
                    OrderStepConstants.STATUS_READY
                )
            )
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    android.util.Log.e(
                        OrderDocumentParser.TAG,
                        "Offers listener error (attempt=$attempt): ${error.message}",
                        error
                    )
                    val delaySec = LISTENER_BACKOFF_SECONDS.getOrElse(attempt) { LISTENER_BACKOFF_SECONDS.last() }
                    _dispatchListenerStatus.value = _dispatchListenerStatus.value.copy(
                        state = ListenerConnectionState.RESTARTING,
                        restartAttempt = attempt + 1,
                        nextRetrySeconds = delaySec,
                        lastError = error.localizedMessage
                    )
                    offersListener?.remove()
                    offersListener = null
                    offersRetryJob?.cancel()
                    offersRetryJob = scope.launch {
                        kotlinx.coroutines.delay(delaySec * 1000L)
                        if (activeSessionUid == uid) {
                            attachOffersListener(uid, attempt + 1)
                        }
                    }
                    return@addSnapshotListener
                }
                if (snapshots == null) return@addSnapshotListener
                _dispatchListenerStatus.value = DispatchListenerStatus(
                    state = ListenerConnectionState.CONNECTED,
                    lastUpdateMillis = System.currentTimeMillis(),
                    restartAttempt = 0,
                    nextRetrySeconds = 0,
                    lastError = null
                )
                val rawCandidates = snapshots.documents.mapNotNull { parseOrderSafely(it, "offers") }
                _candidateOrders.value = rawCandidates
                evaluateOffers(activeUserPrefs)
            }
    }

    private fun attachDeliveredOrdersListener(uid: String, attempt: Int) {
        if (activeSessionUid != uid) return
        deliveredOrdersRetryJob?.cancel()
        deliveredOrdersListener?.remove()
        deliveredOrdersListener = db.collection("orders")
            .whereEqualTo("riderId", uid)
            .whereIn(
                "status",
                listOf(
                    OrderStepConstants.STATUS_DELIVERED,
                    OrderStepConstants.STATUS_COMPLETED,
                    "Delivered",
                    "Completed"
                ).distinct()
            )
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    android.util.Log.e(
                        OrderDocumentParser.TAG,
                        "Delivered orders listener error (attempt=$attempt): ${error.message}",
                        error
                    )
                    val delaySec = LISTENER_BACKOFF_SECONDS.getOrElse(attempt) { LISTENER_BACKOFF_SECONDS.last() }
                    _dispatchListenerStatus.value = _dispatchListenerStatus.value.copy(
                        state = ListenerConnectionState.RESTARTING,
                        restartAttempt = attempt + 1,
                        nextRetrySeconds = delaySec,
                        lastError = error.localizedMessage
                    )
                    deliveredOrdersListener?.remove()
                    deliveredOrdersListener = null
                    deliveredOrdersRetryJob?.cancel()
                    deliveredOrdersRetryJob = scope.launch {
                        kotlinx.coroutines.delay(delaySec * 1000L)
                        if (activeSessionUid == uid) {
                            attachDeliveredOrdersListener(uid, attempt + 1)
                        }
                    }
                    return@addSnapshotListener
                }
                if (snapshots == null) return@addSnapshotListener
                _dispatchListenerStatus.value = DispatchListenerStatus(
                    state = ListenerConnectionState.CONNECTED,
                    lastUpdateMillis = System.currentTimeMillis(),
                    restartAttempt = 0,
                    nextRetrySeconds = 0,
                    lastError = null
                )
                val orders = snapshots.documents.mapNotNull { parseOrderSafely(it, "deliveredOrders") }
                _deliveredOrders.value = orders

                for (order in orders) {
                    val prev = knownLateTips[order.id] ?: 0.0
                    if (deliveredListenerInitialized && order.lateTipAmount > prev) {
                        val added = order.lateTipAmount - prev
                        _lateTipBanner.value = LateTipBannerInfo(
                            orderId = order.id,
                            orderNumber = order.orderNumber,
                            amount = added
                        )
                    }
                    if (order.lateTipAmount > 0.0) {
                        knownLateTips[order.id] = order.lateTipAmount
                    }
                }
                deliveredListenerInitialized = true

                checkTierProgression(uid, _rider.value?.name ?: "Rider", orders, activeUserPrefs ?: return@addSnapshotListener)
            }
    }

    private fun evaluateOffers(userPrefs: UserPreferencesRepository?) {
        waveDelayJob?.cancel()
        val rawCandidates = _candidateOrders.value
        val currentRider = _rider.value
        val activeList = _activeOrders.value
        val riderLoc = RiderLocationTracker.currentLocation.value
        val lastLocMs = maxOf(RiderLocationTracker.lastLocationTimeMillis.value, _riderDocLocationUpdatedAtMs.value)
        val dispatch = _dispatchSettings.value
        val rates = _riderPaySettings.value
        val nowMs = System.currentTimeMillis()
        val declinedIds = userPrefs?.declinedOrderIds?.value ?: emptySet()
        val releasedIds = _releasedOrderIds.value
        val leadMinutes = _scheduledReleaseLeadMinutes.value

        val activeSplitFamilyIds = activeList.mapNotNull { active ->
            val parentId = active.splitParentOrderId?.trim()
            if (!parentId.isNullOrEmpty()) {
                parentId
            } else if (active.hasSplitOut) {
                active.id
            } else null
        }.toSet()

        val canAcceptMore = currentRider != null &&
            currentRider.isOnline &&
            BatchConstants.canReceiveMoreOffers(activeList, dispatch, currentRider.priorityRider)

        val evalSkips = mutableListOf<SkippedOrderRecord>()
        val eligibleWithDist = mutableListOf<Pair<Order, Double>>()
        var latestTaxiCandidate: Pair<Order, TaxiOrderDiagnostics>? = null

        for (order in rawCandidates) {
            val checkCity = if (order.cityId.isNotBlank()) order.cityId else (currentRider?.cityId ?: "")
            if (order.isTaxi) {
                val diag = com.example.data.constants.TaxiDispatchEvaluator.diagnoseTaxiOrder(
                    order = order,
                    rider = currentRider,
                    activeOrders = activeList,
                    riderLoc = riderLoc,
                    isPlatformServiceEnabled = isPlatformServiceOn(OrderStepConstants.SERVICE_TAXI),
                    isCityServiceEnabled = isCityServiceOn(OrderStepConstants.SERVICE_TAXI, checkCity),
                    dispatch = dispatch,
                    declinedIds = declinedIds,
                    releasedIds = releasedIds,
                    evaluatedAtMillis = nowMs
                )
                val prev = latestTaxiCandidate?.first
                if (prev == null || order.acceptedAtMillis >= prev.acceptedAtMillis) {
                    latestTaxiCandidate = order to diag
                }
            }
            val eval = when {
                order.isRequestRider -> com.example.data.constants.InstantDispatchEvaluator.evaluateSkipReason(
                    order = order,
                    rider = currentRider,
                    activeOrders = activeList,
                    riderLoc = riderLoc,
                    lastLocationTimeMs = lastLocMs,
                    nowMs = nowMs,
                    isPlatformServiceEnabled = isPlatformServiceOn(OrderStepConstants.SERVICE_REQUEST),
                    isCityServiceEnabled = isCityServiceOn(OrderStepConstants.SERVICE_REQUEST, checkCity),
                    dispatch = dispatch,
                    declinedIds = declinedIds,
                    releasedIds = releasedIds
                )
                order.isTaxi -> com.example.data.constants.TaxiDispatchEvaluator.evaluateSkipReason(
                    order = order,
                    rider = currentRider,
                    activeOrders = activeList,
                    riderLoc = riderLoc,
                    isPlatformServiceEnabled = isPlatformServiceOn(OrderStepConstants.SERVICE_TAXI),
                    isCityServiceEnabled = isCityServiceOn(OrderStepConstants.SERVICE_TAXI, checkCity),
                    dispatch = dispatch,
                    declinedIds = declinedIds,
                    releasedIds = releasedIds
                )
                else -> com.example.data.constants.GeneralDispatchEvaluator.evaluateSkipReason(
                    order = order,
                    rider = currentRider,
                    activeOrders = activeList,
                    riderLoc = riderLoc,
                    isPlatformServiceEnabled = isPlatformServiceOn(order.serviceType),
                    isCityServiceEnabled = isCityServiceOn(order.serviceType, checkCity),
                    dispatch = dispatch,
                    activeSplitFamilyIds = activeSplitFamilyIds,
                    declinedIds = declinedIds,
                    releasedIds = releasedIds,
                    scheduledLeadMinutes = leadMinutes,
                    nowMs = nowMs
                )
            }

            if (!eval.isEligible) {
                val reasonText = eval.skipReason ?: "ineligible"
                android.util.Log.d(
                    OrderDocumentParser.TAG,
                    "${SkippedOrderRecord.labelForOrder(order)} skipped: $reasonText"
                )
                evalSkips.add(
                    SkippedOrderRecord(
                        orderId = order.id,
                        displayLabel = SkippedOrderRecord.labelForOrder(order),
                        reason = reasonText,
                        timestampMillis = nowMs,
                        isBadData = false
                    )
                )
            } else if (!canAcceptMore) {
                val reasonText = if (currentRider == null || !currentRider.isOnline) "offline" else "busy with active order"
                evalSkips.add(
                    SkippedOrderRecord(
                        orderId = order.id,
                        displayLabel = SkippedOrderRecord.labelForOrder(order),
                        reason = reasonText,
                        timestampMillis = nowMs,
                        isBadData = false
                    )
                )
            } else {
                eligibleWithDist.add(order to eval.distanceKm)
            }
        }

        _ordersSeenCount.value = rawCandidates.size
        _eligibleOrdersCount.value = eligibleWithDist.size
        latestEvaluationSkips = evalSkips
        latestTaxiCandidate?.second?.let { diag ->
            _lastTaxiDiagnostics.value = diag
        }
        refreshCombinedSkippedOrders()

        val activeRider = currentRider
        if (activeRider == null || !canAcceptMore) {
            _incomingOfferBundle.value = null
            _incomingOffer.value = null
            return
        }

        // Nearest rider first / nearest pickup order first
        val candidateOrders = eligibleWithDist
            .sortedWith(compareBy<Pair<Order, Double>> { it.second }.thenBy { it.first.acceptedAtMillis })
            .map { it.first }

        var selectedBundle: OfferBundle? = null
        val now = System.currentTimeMillis()

        if (activeList.isEmpty()) {
            val priorityCandidate = candidateOrders.firstOrNull { it.isPriority || it.badge.equals("priority", ignoreCase = true) }
            if (priorityCandidate != null) {
                selectedBundle = buildOfferBundle(OfferType.SINGLE, listOf(priorityCandidate), riderLoc, rates, dispatch, isPriorityRider = currentRider.priorityRider)
            }

            if (selectedBundle == null) {
                val nonHoldingPoolable = candidateOrders.firstOrNull { order ->
                    BatchConstants.isPoolable(order, dispatch) && !BatchConstants.isHolding(order, dispatch, now)
                }
                if (nonHoldingPoolable != null) {
                    selectedBundle = buildOfferBundle(OfferType.SINGLE, listOf(nonHoldingPoolable), riderLoc, rates, dispatch, isPriorityRider = currentRider.priorityRider)
                }
            }

            if (selectedBundle == null) {
                val holdingOrders = candidateOrders.filter { order ->
                    BatchConstants.isPoolable(order, dispatch) && BatchConstants.isHolding(order, dispatch, now)
                }
                for (hOrder in holdingOrders) {
                    val pairablePartners = holdingOrders.filter { other ->
                        other.id != hOrder.id && BatchConstants.isPairable(hOrder, other, dispatch)
                    }
                    if (pairablePartners.isNotEmpty()) {
                        val partner = pairablePartners.first()
                        val bundleList = mutableListOf(hOrder, partner)
                        val allowsPriorityMax = dispatch.martBatching || !hOrder.isMart
                        val maxLimit = if (currentRider.priorityRider && allowsPriorityMax) {
                            dispatch.priorityMaxBatch.coerceAtLeast(1)
                        } else {
                            min(hOrder.maxBatch.coerceAtLeast(2), partner.maxBatch.coerceAtLeast(2))
                        }
                        for (third in pairablePartners.drop(1)) {
                            if (bundleList.size < maxLimit && bundleList.all { BatchConstants.isPairable(it, third, dispatch) }) {
                                bundleList.add(third)
                            }
                        }
                        selectedBundle = buildOfferBundle(OfferType.DOUBLE_BOOKING, bundleList, riderLoc, rates, dispatch, isPriorityRider = currentRider.priorityRider)
                        break
                    }
                }
            }

            if (selectedBundle == null) {
                val nonPoolable = candidateOrders.firstOrNull { !BatchConstants.isPoolable(it, dispatch) && !it.isPriority }
                if (nonPoolable != null) {
                    selectedBundle = buildOfferBundle(OfferType.SINGLE, listOf(nonPoolable), riderLoc, rates, dispatch, isPriorityRider = currentRider.priorityRider)
                }
            }
        } else {
            val joinableCandidate = candidateOrders.firstOrNull { candidate ->
                BatchConstants.canJoin(activeList, candidate, dispatch, isPriorityRider = currentRider.priorityRider)
            }
            if (joinableCandidate != null) {
                selectedBundle = buildOfferBundle(
                    OfferType.ADD_TO_BOOKING,
                    listOf(joinableCandidate),
                    riderLoc,
                    rates,
                    dispatch,
                    startingRank = activeList.size + 1,
                    isPriorityRider = currentRider.priorityRider
                )
            }
        }

        if (selectedBundle != null) {
            val waveDelaySec = selectedBundle.waveDelaySeconds
            val elapsedSec = ((now - selectedBundle.eligibleSince) / 1000).coerceAtLeast(0)
            if (elapsedSec >= waveDelaySec || waveDelaySec <= 0) {
                _incomingOfferBundle.value = selectedBundle
                _incomingOffer.value = selectedBundle.firstOrder
            } else {
                _incomingOfferBundle.value = null
                _incomingOffer.value = null
                val remainingMs = ((waveDelaySec - elapsedSec) * 1000L + 50L).coerceAtMost(120_000L)
                waveDelayJob = scope.launch {
                    kotlinx.coroutines.delay(remainingMs)
                    if (activeSessionUid != null) {
                        evaluateOffers(activeUserPrefs)
                    }
                }
            }
        } else {
            _incomingOfferBundle.value = null
            _incomingOffer.value = null
        }
    }

    private fun mergeInbox() {
        val now = System.currentTimeMillis()
        val all = (inboxDirectList + inboxAllList)
            .distinctBy { it.id }
            .filter { !it.isFuture(now) }
            .map { it.copy(isRead = readMessageIds.contains(it.id)) }
            .sortedByDescending { it.sendAtMillis.takeIf { t -> t > 0L } ?: it.id.hashCode().toLong() }
        _inboxMessages.value = all
    }

    private fun checkTierProgression(
        riderId: String,
        riderName: String,
        delivered: List<Order>,
        userPrefs: UserPreferencesRepository
    ) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val firstDayMs = cal.timeInMillis

        val monthFormat = SimpleDateFormat("yyyy-MM", Locale.US)
        val monthKey = monthFormat.format(Date())

        val ridesThisMonth = delivered.count { order ->
            val delMs = OrderDocumentParser.parseTolerantTimeMillisOrZero(order.deliveredAt)
            delMs <= 0L || delMs > firstDayMs
        }

        val configs = _tierConfigs.value
        for (tier in configs) {
            if (ridesThisMonth >= tier.ridesRequired && tier.ridesRequired > 0) {
                val eventDocId = "${riderId}_${monthKey}_${tier.name}"
                val celebratedKey = "${monthKey}_${tier.name}"

                // Write tierEvents with fixed ID so it never duplicates
                val eventData = mapOf(
                    "riderId" to riderId,
                    "riderName" to riderName,
                    "tier" to tier.name,
                    "coins" to tier.coins,
                    "monthKey" to monthKey,
                    "ridesCount" to ridesThisMonth,
                    "status" to "Pending",
                    "reachedAt" to FieldValue.serverTimestamp()
                )

                db.collection("tierEvents").document(eventDocId)
                    .set(eventData, SetOptions.merge())
                    .addOnSuccessListener {
                        // Create adminNotification once
                        if (!userPrefs.celebratedTiers.value.contains(celebratedKey)) {
                            userPrefs.addCelebratedTier(celebratedKey)
                            _newlyReachedTier.value = tier

                            val notifData = mapOf(
                                "type" to "tier_reached",
                                "riderId" to riderId,
                                "tier" to tier.name,
                                "coins" to tier.coins,
                                "monthKey" to monthKey,
                                "createdAt" to FieldValue.serverTimestamp()
                            )
                            db.collection("adminNotifications").add(notifData)
                        }
                    }
            }
        }
    }

    fun dismissNewlyReachedTier() {
        _newlyReachedTier.value = null
    }

    // UPDATE PHONE
    fun updatePhone(newPhone: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val uid = auth.currentUser?.uid ?: run { onError("Not logged in"); return }
        db.collection("riders").document(uid).update("phone", newPhone)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.localizedMessage ?: "Failed to update phone") }
    }

    // SERVICE TYPE TOGGLE
    fun toggleServiceType(serviceKey: String, enabled: Boolean, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val currentRider = _rider.value ?: run { onError("Rider data not loaded"); return }
        val normKey = serviceKey.trim().lowercase(Locale.US)
        if (!currentRider.allowedServices.any { it.trim().lowercase(Locale.US) == normKey }) {
            onError("This service is disabled by admin")
            return
        }
        if (_activeOrders.value.isNotEmpty()) {
            onError("Cannot modify services while you have active orders")
            return
        }

        val updatedActive = currentRider.activeServices.toMutableList()
        if (enabled) {
            if (!updatedActive.contains(serviceKey)) updatedActive.add(serviceKey)
        } else {
            if (updatedActive.size <= 1 && updatedActive.contains(serviceKey)) {
                onError("At least one service must stay active")
                return
            }
            updatedActive.remove(serviceKey)
        }

        db.collection("riders").document(currentRider.uid).update("activeServices", updatedActive)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.localizedMessage ?: "Failed to update services") }
    }

    // ONLINE / OFFLINE TOGGLE
    fun setOnline(online: Boolean, context: Context, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val currentRider = _rider.value ?: run { onError("Rider profile not loaded"); return }
        if (!online && _activeOrders.value.isNotEmpty()) {
            onError("Going offline is blocked while there is an active order")
            return
        }

        db.collection("riders").document(currentRider.uid).update(
            "isOnline", online,
            "lastSeenAt", FieldValue.serverTimestamp()
        ).addOnSuccessListener {
            if (online) {
                val nowMs = System.currentTimeMillis()
                _onlineSinceMillis.value = nowMs
                activeUserPrefs?.setOnlineSinceMillis(nowMs)
                RiderLocationService.start(context)
            } else {
                _onlineSinceMillis.value = 0L
                activeUserPrefs?.setOnlineSinceMillis(0L)
                RiderLocationService.stop(context)
            }
            onSuccess()
        }.addOnFailureListener {
            onError(it.localizedMessage ?: "Failed to update online status")
        }
    }

    // ACCEPT OFFER BUNDLE (Single Transaction Across All Orders in the Offer)
    fun acceptOfferBundle(
        bundle: OfferBundle,
        onSuccess: () -> Unit,
        onError: (AcceptOrderErrorDetails) -> Unit
    ) {
        val currentRider = _rider.value
        val authUid = auth.currentUser?.uid ?: currentRider?.uid ?: ""

        if (currentRider == null || authUid.isEmpty()) {
            onError(
                AcceptOrderErrorDetails(
                    exceptionMessage = "Rider profile or FirebaseAuth session is not ready",
                    orderId = bundle.id,
                    currentStatus = "unknown",
                    authUid = authUid,
                    fieldsWritten = emptyMap()
                )
            )
            return
        }

        val startingIndex = if (bundle.type == OfferType.ADD_TO_BOOKING) _activeOrders.value.size + 1 else 1
        val existingBatchId = _activeOrders.value.firstOrNull { !it.batchId.isNullOrBlank() }?.batchId
        val batchId = existingBatchId ?: UUID.randomUUID().toString()

        var writeMap: Map<String, Any?> = emptyMap()

        db.runTransaction { tx ->
            // 1. Re-read all order documents in offer
            val orderDocs = bundle.orders.map { order ->
                val ref = db.collection("orders").document(order.id)
                val snapshot = tx.get(ref)
                if (!snapshot.exists()) {
                    throw FirebaseFirestoreException("Order already taken", FirebaseFirestoreException.Code.ABORTED)
                }
                val existingRider = snapshot.getString("riderId")
                if (!existingRider.isNullOrBlank()) {
                    throw FirebaseFirestoreException("Order already taken", FirebaseFirestoreException.Code.ABORTED)
                }
                val excludes = (snapshot.get("excludeRiderIds") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                if (excludes.contains(authUid)) {
                    throw FirebaseFirestoreException("Order not available", FirebaseFirestoreException.Code.ABORTED)
                }
                ref to snapshot
            }

            // 2. Perform writes across all orders
            orderDocs.forEachIndexed { idx, (ref, snapshot) ->
                val order = bundle.orders[idx]
                val rank = startingIndex + idx
                val breakdown = bundle.breakdowns.getOrNull(idx)
                val feeEstimate = breakdown?.totalPay ?: 0.0

                val currentStatus = snapshot.getString("status") ?: OrderStepConstants.STATUS_PLACED_ORDER
                val serviceType = (snapshot.getString("serviceType") ?: order.serviceType).lowercase()

                val stepForOrder = when (serviceType) {
                    OrderStepConstants.SERVICE_TAXI -> OrderStepConstants.STEP_TO_PICKUP
                    OrderStepConstants.SERVICE_PARCEL -> OrderStepConstants.STEP_TO_SENDER
                    OrderStepConstants.SERVICE_PABILI -> OrderStepConstants.STEP_TO_VENDOR
                    else -> OrderStepConstants.STEP_TO_MERCHANT
                }

                val updates = mutableMapOf<String, Any>()
                updates["riderId"] = authUid
                updates["riderName"] = currentRider.name
                updates["riderPhone"] = currentRider.phone
                updates["riderPhotoUrl"] = currentRider.photoUrl
                updates["riderStep"] = stepForOrder
                updates["batchId"] = batchId
                updates["batchIndex"] = rank
                updates["riderFeeEstimate"] = feeEstimate

                updates["riderVehicle"] = mapOf(
                    "type" to currentRider.vehicle.type,
                    "plate" to currentRider.vehicle.plateNumber,
                    "model" to currentRider.vehicle.model,
                    "color" to currentRider.vehicle.color
                )

                if (serviceType == OrderStepConstants.SERVICE_REQUEST || serviceType == OrderStepConstants.SERVICE_TAXI || snapshot.contains("riderRequest")) {
                    val existingReq = (snapshot.get("riderRequest") as? Map<*, *>)?.mapKeys { it.key.toString() }?.toMutableMap() ?: mutableMapOf()
                    existingReq["status"] = OrderStepConstants.REQ_RIDER_ASSIGNED
                    updates["riderRequest"] = existingReq
                }

                val newStatusForHistory = when (serviceType) {
                    OrderStepConstants.SERVICE_TAXI -> {
                        val taxiStatus = OrderStepConstants.STATUS_DRIVER_ON_WAY
                        updates["status"] = taxiStatus
                        taxiStatus
                    }
                    OrderStepConstants.SERVICE_PARCEL -> {
                        val parcelStatus = OrderStepConstants.STATUS_RIDER_ON_WAY_SENDER
                        updates["status"] = parcelStatus
                        parcelStatus
                    }
                    OrderStepConstants.SERVICE_PABILI -> {
                        val pabiliStatus = OrderStepConstants.STATUS_RIDER_ON_WAY_VENDOR
                        updates["status"] = pabiliStatus
                        pabiliStatus
                    }
                    else -> currentStatus
                }

                val historyEntry = mapOf(
                    "status" to newStatusForHistory,
                    "timestamp" to com.google.firebase.Timestamp.now()
                )
                updates["statusHistory"] = FieldValue.arrayUnion(historyEntry)
                updates["updatedAt"] = FieldValue.serverTimestamp()

                writeMap = updates
                tx.update(ref, updates)
            }
        }.addOnSuccessListener {
            _incomingOfferBundle.value = null
            _incomingOffer.value = null

            // Log outcome in offerLog for each order of bundle
            bundle.orders.forEach { order ->
                logOfferOutcome(order.id, order.serviceType, "accepted")
            }

            // Add newly accepted orders to _activeOrders in memory
            val newlyAccepted = bundle.orders.mapIndexed { idx, ord ->
                val rank = startingIndex + idx
                val breakdown = bundle.breakdowns.getOrNull(idx)
                val feeEstimate = breakdown?.totalPay ?: 0.0
                val serviceType = ord.serviceType.lowercase()
                val stepForOrder = when (serviceType) {
                    OrderStepConstants.SERVICE_TAXI -> OrderStepConstants.STEP_TO_PICKUP
                    OrderStepConstants.SERVICE_PARCEL -> OrderStepConstants.STEP_TO_SENDER
                    OrderStepConstants.SERVICE_PABILI -> OrderStepConstants.STEP_TO_VENDOR
                    else -> OrderStepConstants.STEP_TO_MERCHANT
                }
                val newStatus = when (serviceType) {
                    OrderStepConstants.SERVICE_TAXI -> OrderStepConstants.STATUS_DRIVER_ON_WAY
                    OrderStepConstants.SERVICE_PARCEL -> OrderStepConstants.STATUS_RIDER_ON_WAY_SENDER
                    OrderStepConstants.SERVICE_PABILI -> OrderStepConstants.STATUS_RIDER_ON_WAY_VENDOR
                    else -> ord.status
                }
                ord.copy(
                    riderId = authUid,
                    riderName = currentRider.name,
                    riderPhone = currentRider.phone,
                    riderPhotoUrl = currentRider.photoUrl,
                    riderStep = stepForOrder,
                    status = newStatus,
                    batchId = batchId,
                    batchIndex = rank,
                    riderFeeEstimate = feeEstimate
                )
            }
            _activeOrders.value = (_activeOrders.value.filter { existing -> bundle.orders.none { it.id == existing.id } } + newlyAccepted)
            RiderLocationTracker.setActiveOrderIds(_activeOrders.value.map { it.id }.toSet())
            onSuccess()
        }.addOnFailureListener { exception ->
            _incomingOfferBundle.value = null
            _incomingOffer.value = null
            val errorDetails = AcceptOrderErrorDetails(
                exceptionMessage = exception.message ?: exception.localizedMessage ?: "Order already taken",
                orderId = bundle.id,
                currentStatus = "unknown",
                authUid = authUid,
                fieldsWritten = writeMap
            )
            onError(errorDetails)
        }
    }

    // ACCEPT ORDER (Transaction) - delegates to acceptOfferBundle
    fun acceptOrder(
        orderId: String,
        onSuccess: () -> Unit,
        onError: (AcceptOrderErrorDetails) -> Unit
    ) {
        val currentBundle = _incomingOfferBundle.value
        if (currentBundle != null && currentBundle.orders.any { it.id == orderId }) {
            acceptOfferBundle(currentBundle, onSuccess, onError)
            return
        }
        val currentOrder = _incomingOffer.value
        if (currentOrder != null && currentOrder.id == orderId) {
            val singleBundle = buildOfferBundle(
                type = OfferType.SINGLE,
                orders = listOf(currentOrder),
                driverLoc = RiderLocationTracker.currentLocation.value,
                paySettings = _riderPaySettings.value,
                dispatchSettings = _dispatchSettings.value
            )
            acceptOfferBundle(singleBundle, onSuccess, onError)
            return
        }
        onError(
            AcceptOrderErrorDetails(
                exceptionMessage = "Offer is no longer available",
                orderId = orderId,
                currentStatus = "expired",
                authUid = auth.currentUser?.uid ?: "",
                fieldsWritten = emptyMap()
            )
        )
    }

    fun declineOfferBundle(bundle: OfferBundle, userPrefs: UserPreferencesRepository) {
        _incomingOfferBundle.value = null
        _incomingOffer.value = null
        bundle.orders.forEach { order ->
            userPrefs.addDeclinedOrderId(order.id)
            logOfferOutcome(order.id, order.serviceType, "declined")
        }
    }

    fun missOfferBundle(bundle: OfferBundle, userPrefs: UserPreferencesRepository) {
        _incomingOfferBundle.value = null
        _incomingOffer.value = null
        bundle.orders.forEach { order ->
            userPrefs.addDeclinedOrderId(order.id)
            logOfferOutcome(order.id, order.serviceType, "missed")
        }
    }

    // ADVANCE STEP
    fun advanceOrderStep(
        order: Order,
        finalReceiptTotal: Double? = null,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val orderRef = db.collection("orders").document(order.id)
        val updates = mutableMapOf<String, Any>(
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val serviceType = order.serviceType.lowercase()
        val currentStep = order.riderStep.lowercase()

        val nextStep: String
        val nextStatus: String
        var nextReqStatus: String? = null

        when (serviceType) {
            OrderStepConstants.SERVICE_TAXI -> {
                when {
                    currentStep == OrderStepConstants.STEP_TO_PICKUP -> {
                        nextStep = OrderStepConstants.STEP_AT_PICKUP
                        nextStatus = OrderStepConstants.STATUS_DRIVER_ARRIVED
                        updates["arrivedMerchantAt"] = FieldValue.serverTimestamp()
                    }
                    currentStep == OrderStepConstants.STEP_AT_PICKUP -> {
                        nextStep = "to_stop_2"
                        nextStatus = OrderStepConstants.STATUS_ON_TRIP
                        updates["pickedUpAt"] = FieldValue.serverTimestamp()
                    }
                    currentStep.startsWith(OrderStepConstants.STEP_TO_STOP_PREFIX) -> {
                        val stopNum = currentStep.removePrefix(OrderStepConstants.STEP_TO_STOP_PREFIX).toIntOrNull() ?: 2
                        val isLast = order.isAtLastTaxiStop
                        if (isLast) {
                            nextStep = OrderStepConstants.STEP_COMPLETED
                            nextStatus = OrderStepConstants.STATUS_COMPLETED
                            updates["deliveredAt"] = FieldValue.serverTimestamp()
                            updates["completedStops"] = FieldValue.arrayUnion(stopNum)
                        } else {
                            nextStep = "to_stop_${stopNum + 1}"
                            nextStatus = OrderStepConstants.STATUS_ON_TRIP
                            updates["completedStops"] = FieldValue.arrayUnion(stopNum)
                        }
                    }
                    else -> {
                        nextStep = OrderStepConstants.STEP_COMPLETED
                        nextStatus = OrderStepConstants.STATUS_COMPLETED
                        updates["deliveredAt"] = FieldValue.serverTimestamp()
                    }
                }
            }
            OrderStepConstants.SERVICE_PARCEL -> {
                when (currentStep) {
                    OrderStepConstants.STEP_TO_SENDER, OrderStepConstants.STEP_HEADING_TO_MERCHANT -> {
                        nextStep = OrderStepConstants.STEP_AT_SENDER
                        nextStatus = OrderStepConstants.STATUS_ARRIVED_AT_SENDER
                        updates["arrivedMerchantAt"] = FieldValue.serverTimestamp()
                    }
                    OrderStepConstants.STEP_AT_SENDER, OrderStepConstants.STEP_ARRIVED_AT_MERCHANT -> {
                        nextStep = OrderStepConstants.STEP_TO_RECIPIENT
                        nextStatus = OrderStepConstants.STATUS_RIDER_ON_WAY_RECIPIENT
                        updates["pickedUpAt"] = FieldValue.serverTimestamp()
                    }
                    OrderStepConstants.STEP_TO_RECIPIENT, OrderStepConstants.STEP_HEADING_TO_CUSTOMER, OrderStepConstants.STEP_PICKED_UP -> {
                        nextStep = OrderStepConstants.STEP_AT_RECIPIENT
                        nextStatus = OrderStepConstants.STATUS_ARRIVED_AT_RECIPIENT
                        updates["arrivedCustomerAt"] = FieldValue.serverTimestamp()
                    }
                    else -> { // at_recipient -> completed
                        nextStep = OrderStepConstants.STEP_COMPLETED
                        nextStatus = OrderStepConstants.STATUS_DELIVERED
                        updates["deliveredAt"] = FieldValue.serverTimestamp()
                    }
                }
            }
            OrderStepConstants.SERVICE_PABILI -> {
                when (currentStep) {
                    OrderStepConstants.STEP_TO_VENDOR, OrderStepConstants.STEP_HEADING_TO_MERCHANT -> {
                        nextStep = OrderStepConstants.STEP_AT_VENDOR
                        nextStatus = OrderStepConstants.STATUS_ARRIVED_AT_VENDOR
                        updates["arrivedMerchantAt"] = FieldValue.serverTimestamp()
                    }
                    OrderStepConstants.STEP_AT_VENDOR, OrderStepConstants.STEP_ARRIVED_AT_MERCHANT -> {
                        // Picked up / bought: requires finalReceiptTotal
                        if (finalReceiptTotal == null || finalReceiptTotal <= 0.0) {
                            onError("Receipt total is required for Pabili")
                            return
                        }
                        nextStep = OrderStepConstants.STEP_PICKED_UP
                        nextStatus = OrderStepConstants.STATUS_RIDER_ON_THE_WAY
                        updates["finalReceiptTotal"] = finalReceiptTotal
                        updates["pickedUpAt"] = FieldValue.serverTimestamp()
                    }
                    OrderStepConstants.STEP_PICKED_UP -> {
                        nextStep = OrderStepConstants.STEP_TO_CUSTOMER
                        nextStatus = OrderStepConstants.STATUS_RIDER_ON_THE_WAY
                    }
                    OrderStepConstants.STEP_TO_CUSTOMER, OrderStepConstants.STEP_HEADING_TO_CUSTOMER -> {
                        nextStep = OrderStepConstants.STEP_AT_CUSTOMER
                        nextStatus = OrderStepConstants.STATUS_RIDER_ON_THE_WAY
                        updates["arrivedCustomerAt"] = FieldValue.serverTimestamp()
                    }
                    else -> { // at_customer -> completed
                        nextStep = OrderStepConstants.STEP_COMPLETED
                        nextStatus = OrderStepConstants.STATUS_DELIVERED
                        updates["deliveredAt"] = FieldValue.serverTimestamp()
                    }
                }
            }
            else -> {
                // FOOD, CONVENIENCE (Mart), and REQUEST (Delivio Instant)
                val isRequest = serviceType == OrderStepConstants.SERVICE_REQUEST
                when (currentStep) {
                    OrderStepConstants.STEP_TO_MERCHANT, OrderStepConstants.STEP_HEADING_TO_MERCHANT -> {
                        nextStep = OrderStepConstants.STEP_AT_MERCHANT
                        nextStatus = order.status // food, convenience, and request keep status
                        if (isRequest) nextReqStatus = OrderStepConstants.REQ_ARRIVED_AT_MERCHANT
                        updates["arrivedMerchantAt"] = FieldValue.serverTimestamp()
                    }
                    OrderStepConstants.STEP_AT_MERCHANT, OrderStepConstants.STEP_ARRIVED_AT_MERCHANT -> {
                        nextStep = OrderStepConstants.STEP_PICKED_UP
                        // 1. CUSTOMER STATUS IN A DOUBLE BOOKING:
                        // When I pick up an order in a batch of two or more, write its status like this:
                        // if its customer stop is the NEXT unfinished customer stop, "Rider is on the way";
                        // otherwise "On another delivery".
                        val activeBatch = _activeOrders.value.filter {
                            it.status !in listOf("Delivered", "Completed", "Cancelled", "Rejected")
                        }
                        val loc = RiderLocationTracker.currentLocation.value
                        if (activeBatch.size >= 2) {
                            val stops = RouteStopCalculator.computeBatchStops(
                                activeBatch,
                                loc?.first ?: order.merchantLat,
                                loc?.second ?: order.merchantLng
                            )
                            val nextCustStop = stops.firstOrNull { it.isCustomer && !it.isFinished }
                            nextStatus = if (nextCustStop != null && nextCustStop.orderId == order.id) {
                                OrderStepConstants.STATUS_RIDER_ON_THE_WAY
                            } else {
                                OrderStepConstants.STATUS_ON_ANOTHER_DELIVERY
                            }
                        } else {
                            nextStatus = OrderStepConstants.STATUS_RIDER_ON_THE_WAY
                        }
                        if (isRequest) nextReqStatus = OrderStepConstants.REQ_PICKED_UP
                        updates["pickedUpAt"] = FieldValue.serverTimestamp()
                    }
                    OrderStepConstants.STEP_PICKED_UP -> {
                        nextStep = OrderStepConstants.STEP_TO_CUSTOMER
                        val activeBatch = _activeOrders.value.filter {
                            it.status !in listOf("Delivered", "Completed", "Cancelled", "Rejected")
                        }
                        val loc = RiderLocationTracker.currentLocation.value
                        if (activeBatch.size >= 2) {
                            val stops = RouteStopCalculator.computeBatchStops(
                                activeBatch,
                                loc?.first ?: order.merchantLat,
                                loc?.second ?: order.merchantLng
                            )
                            val nextCustStop = stops.firstOrNull { it.isCustomer && !it.isFinished }
                            nextStatus = if (nextCustStop != null && nextCustStop.orderId == order.id) {
                                OrderStepConstants.STATUS_RIDER_ON_THE_WAY
                            } else {
                                OrderStepConstants.STATUS_ON_ANOTHER_DELIVERY
                            }
                        } else {
                            nextStatus = OrderStepConstants.STATUS_RIDER_ON_THE_WAY
                        }
                        if (isRequest) nextReqStatus = OrderStepConstants.REQ_ON_THE_WAY
                    }
                    OrderStepConstants.STEP_TO_CUSTOMER, OrderStepConstants.STEP_HEADING_TO_CUSTOMER -> {
                        nextStep = OrderStepConstants.STEP_AT_CUSTOMER
                        nextStatus = OrderStepConstants.STATUS_RIDER_ON_THE_WAY
                        if (isRequest) nextReqStatus = OrderStepConstants.REQ_ON_THE_WAY
                        updates["arrivedCustomerAt"] = FieldValue.serverTimestamp()
                    }
                    else -> { // at_customer -> completed
                        nextStep = OrderStepConstants.STEP_COMPLETED
                        nextStatus = OrderStepConstants.STATUS_DELIVERED
                        if (isRequest) nextReqStatus = OrderStepConstants.REQ_DELIVERED
                        updates["deliveredAt"] = FieldValue.serverTimestamp()
                    }
                }
            }
        }

        updates["riderStep"] = nextStep
        updates["status"] = nextStatus
        if (nextReqStatus != null) {
            updates["riderRequest.status"] = nextReqStatus
        }

        // statusHistory (arrayUnion with Timestamp.now())
        val historyEntry = mapOf(
            "status" to nextStatus,
            "timestamp" to com.google.firebase.Timestamp.now()
        )
        updates["statusHistory"] = FieldValue.arrayUnion(historyEntry)

        // If completed: compute riderEarning and codCollected
        if (nextStep == OrderStepConstants.STEP_COMPLETED || nextStatus == OrderStepConstants.STATUS_DELIVERED || nextStatus == OrderStepConstants.STATUS_COMPLETED) {
            val codAmountToWrite = if (order.isCod) order.cashAmountToCollect else 0.0
            updates["codCollected"] = codAmountToWrite

            val cityId = order.cityId.ifEmpty { _rider.value?.cityId ?: "puerto_princesa" }

            if (serviceType == OrderStepConstants.SERVICE_TAXI) {
                val vehicleType = OrderStepConstants.normalizeVehicleType(order.vehicleType.ifEmpty { _rider.value?.vehicle?.type })
                db.collection("deliveryRates").document(cityId).get()
                    .addOnSuccessListener { rateDoc ->
                        val rideRates = rateDoc.get("rideRates") as? Map<*, *>
                        val vRates = rideRates?.get(vehicleType) as? Map<*, *>
                        val commissionPercent = (vRates?.get("commissionPercent") as? Number)?.toDouble() ?: 15.0
                        val fare = if (order.feesTotal > 0) order.feesTotal else (order.baseFare + order.distanceFare)
                        val net = fare * (1.0 - commissionPercent / 100.0)
                        val totalPay = net + order.tip

                        updates["riderEarning"] = mapOf(
                            "bookingFee" to fare,
                            "pickupFee" to 0.0,
                            "extra" to 0.0,
                            "tip" to order.tip,
                            "total" to totalPay,
                            "rank" to 1,
                            "batchId" to (order.batchId ?: ""),
                            "fare" to fare,
                            "commissionPercent" to commissionPercent,
                            "net" to net
                        )

                        orderRef.update(updates)
                            .addOnSuccessListener {
                                setOfferFinalOutcome(order.id, "completed")
                                _activeOrders.value = _activeOrders.value.filter { it.id != order.id }
                                RiderLocationTracker.setActiveOrderIds(_activeOrders.value.map { it.id }.toSet())
                                onSuccess()
                            }
                            .addOnFailureListener { onError(it.localizedMessage ?: "Failed to complete trip") }
                    }
                    .addOnFailureListener {
                        val fare = if (order.feesTotal > 0) order.feesTotal else (order.baseFare + order.distanceFare)
                        val net = fare * (1.0 - 15.0 / 100.0)
                        val totalPay = net + order.tip
                        updates["riderEarning"] = mapOf(
                            "bookingFee" to fare,
                            "pickupFee" to 0.0,
                            "extra" to 0.0,
                            "tip" to order.tip,
                            "total" to totalPay,
                            "rank" to 1,
                            "batchId" to (order.batchId ?: ""),
                            "fare" to fare,
                            "commissionPercent" to 15.0,
                            "net" to net
                        )
                        orderRef.update(updates)
                            .addOnSuccessListener {
                                setOfferFinalOutcome(order.id, "completed")
                                _activeOrders.value = _activeOrders.value.filter { it.id != order.id }
                                RiderLocationTracker.setActiveOrderIds(_activeOrders.value.map { it.id }.toSet())
                                onSuccess()
                            }
                            .addOnFailureListener { onError(it.localizedMessage ?: "Failed to complete trip") }
                    }
            } else {
                val isBatch = (order.batchId != null && order.batchId.isNotBlank()) || order.batchIndex > 1 || _activeOrders.value.size > 1
                val rank = order.batchIndex.coerceAtLeast(1)
                val isSplit = order.isSplitChild || order.badge.equals("split", ignoreCase = true)
                val bookingFee = if (isSplit && (order.riderFeeFixed ?: 0.0) > 0.0) {
                    order.riderFeeFixed ?: 0.0
                } else {
                    BatchConstants.calculateDeliveryFee(order, rank, isBatch, _riderPaySettings.value, _dispatchSettings.value.specialRadiusRiderExtra)
                }
                val pickupFee = if (isSplit) 0.0 else BatchConstants.calculatePickupFee(order.pickupDistanceKm, rank, _riderPaySettings.value)
                val extra = if (isSplit) 0.0 else (if (order.specialRadiusExtra > 0.0) order.specialRadiusExtra else _dispatchSettings.value.specialRadiusRiderExtra)
                val tip = order.tip
                val total = bookingFee + pickupFee + extra + tip

                val earningData = mapOf(
                    "bookingFee" to bookingFee,
                    "pickupFee" to pickupFee,
                    "extra" to extra,
                    "tip" to tip,
                    "total" to total,
                    "rank" to rank,
                    "batchId" to (order.batchId ?: "")
                )
                updates["riderEarning"] = earningData

                // 1. CUSTOMER STATUS IN A DOUBLE BOOKING:
                // When I complete a delivery, in the same batched write update the next customer's order from
                // "On another delivery" to "Rider is on the way" (and the same for each following customer in turn).
                val remainingOrders = _activeOrders.value.filter {
                    it.id != order.id && it.status !in listOf("Delivered", "Completed", "Cancelled", "Rejected")
                }
                val loc = RiderLocationTracker.currentLocation.value
                val nextStops = if (remainingOrders.isNotEmpty()) {
                    RouteStopCalculator.computeBatchStops(
                        remainingOrders,
                        loc?.first ?: order.customerLat,
                        loc?.second ?: order.customerLng
                    )
                } else emptyList()

                val nextCustStop = nextStops.firstOrNull { it.isCustomer && !it.isFinished }
                val nextOrderToPromote = if (nextCustStop != null) {
                    remainingOrders.firstOrNull {
                        it.id == nextCustStop.orderId && it.status == OrderStepConstants.STATUS_ON_ANOTHER_DELIVERY
                    }
                } else null

                val writeBatch = db.batch()
                writeBatch.update(orderRef, updates)

                if (nextOrderToPromote != null) {
                    val nextOrderRef = db.collection("orders").document(nextOrderToPromote.id)
                    val promoteUpdates = mapOf<String, Any>(
                        "status" to OrderStepConstants.STATUS_RIDER_ON_THE_WAY,
                        "updatedAt" to FieldValue.serverTimestamp(),
                        "statusHistory" to FieldValue.arrayUnion(
                            mapOf(
                                "status" to OrderStepConstants.STATUS_RIDER_ON_THE_WAY,
                                "timestamp" to com.google.firebase.Timestamp.now()
                            )
                        )
                    )
                    writeBatch.update(nextOrderRef, promoteUpdates)
                }

                writeBatch.commit()
                    .addOnSuccessListener {
                        setOfferFinalOutcome(order.id, "completed")
                        _activeOrders.value = _activeOrders.value
                            .filter { it.id != order.id }
                            .map { rem ->
                                if (nextOrderToPromote != null && rem.id == nextOrderToPromote.id) {
                                    rem.copy(status = OrderStepConstants.STATUS_RIDER_ON_THE_WAY)
                                } else rem
                            }
                        RiderLocationTracker.setActiveOrderIds(_activeOrders.value.map { it.id }.toSet())
                        onSuccess()
                    }
                    .addOnFailureListener { onError(it.localizedMessage ?: "Failed to complete delivery") }
            }
        } else {
            orderRef.update(updates)
                .addOnSuccessListener {
                    _activeOrders.value = _activeOrders.value.map {
                        if (it.id == order.id) it.copy(riderStep = nextStep, status = nextStatus) else it
                    }
                    onSuccess()
                }
                .addOnFailureListener { onError(it.localizedMessage ?: "Failed to advance step") }
        }
    }

    // RELEASE ORDER
    fun releaseOrder(
        order: Order,
        reason: String,
        userPrefs: UserPreferencesRepository? = null,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (order.isTaxi) {
            if (order.riderStep != OrderStepConstants.STEP_TO_PICKUP && order.riderStep != OrderStepConstants.STEP_AT_PICKUP) {
                onError("Trip cannot be released once trip has started")
                return
            }
        } else {
            if (order.riderStep != OrderStepConstants.STEP_HEADING_TO_MERCHANT && order.riderStep != OrderStepConstants.STEP_ARRIVED_AT_MERCHANT &&
                order.riderStep != OrderStepConstants.STEP_TO_MERCHANT && order.riderStep != OrderStepConstants.STEP_AT_MERCHANT &&
                order.riderStep != OrderStepConstants.STEP_TO_SENDER && order.riderStep != OrderStepConstants.STEP_AT_SENDER &&
                order.riderStep != OrderStepConstants.STEP_TO_VENDOR && order.riderStep != OrderStepConstants.STEP_AT_VENDOR) {
                onError("Order cannot be released once items/packages are picked up")
                return
            }
        }

        val revertStatus = when (order.serviceType.lowercase()) {
            OrderStepConstants.SERVICE_TAXI -> OrderStepConstants.STATUS_PLACED_ORDER
            OrderStepConstants.SERVICE_PARCEL, OrderStepConstants.SERVICE_PABILI -> OrderStepConstants.STATUS_PLACED_ORDER
            OrderStepConstants.SERVICE_FOOD, OrderStepConstants.SERVICE_MART -> OrderStepConstants.STATUS_READY
            OrderStepConstants.SERVICE_REQUEST -> OrderStepConstants.STATUS_PLACED_ORDER
            else -> OrderStepConstants.STATUS_READY
        }

        val updates = mutableMapOf<String, Any?>(
            "riderId" to null,
            "riderName" to null,
            "riderPhone" to null,
            "riderPhotoUrl" to null,
            "releaseReason" to reason,
            "status" to revertStatus,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        if (order.serviceType == OrderStepConstants.SERVICE_REQUEST || order.serviceType == OrderStepConstants.SERVICE_TAXI) {
            updates["riderRequest.status"] = OrderStepConstants.REQ_SEARCHING
        }

        db.collection("orders").document(order.id).update(updates)
            .addOnSuccessListener {
                setOfferFinalOutcome(order.id, "released")
                _releasedOrderIds.value = _releasedOrderIds.value + order.id
                userPrefs?.addDeclinedOrderId(order.id)
                onSuccess()
            }
            .addOnFailureListener { onError(it.localizedMessage ?: "Failed to release order") }
    }

    // OFFER LOGGING & OUTCOME HELPERS
    fun logOfferDisplayed(bundle: OfferBundle) {
        val uid = auth.currentUser?.uid ?: _rider.value?.uid ?: return
        val expiresAt = com.google.firebase.Timestamp(Date(System.currentTimeMillis() + bundle.timerSeconds * 1000L))
        bundle.orders.forEach { order ->
            val docRef = db.collection("riders").document(uid).collection("offerLog").document(order.id)
            val data = mapOf(
                "orderId" to order.id,
                "serviceType" to order.serviceType,
                "timerSeconds" to bundle.timerSeconds,
                "offeredAt" to FieldValue.serverTimestamp(),
                "expiresAt" to expiresAt
            )
            docRef.set(data, SetOptions.merge())
        }
    }

    fun logOfferOutcome(orderId: String, serviceType: String, outcome: String) {
        val uid = auth.currentUser?.uid ?: _rider.value?.uid ?: return
        val docRef = db.collection("riders").document(uid).collection("offerLog").document(orderId)
        val timerSeconds = if (
            serviceType.equals(OrderStepConstants.SERVICE_REQUEST, ignoreCase = true) ||
            serviceType.equals(OrderStepConstants.SERVICE_TAXI, ignoreCase = true)
        ) {
            com.example.data.constants.InstantDispatchEvaluator.INSTANT_TIMER_SECONDS
        } else {
            com.example.data.constants.InstantDispatchEvaluator.DEFAULT_TIMER_SECONDS
        }
        val data = mapOf(
            "orderId" to orderId,
            "serviceType" to serviceType,
            "outcome" to outcome,
            "timerSeconds" to timerSeconds,
            "at" to FieldValue.serverTimestamp()
        )
        docRef.set(data, SetOptions.merge())
    }

    fun setOfferFinalOutcome(orderId: String, finalOutcome: String) {
        val uid = auth.currentUser?.uid ?: _rider.value?.uid ?: return
        val docRef = db.collection("riders").document(uid).collection("offerLog").document(orderId)
        val data = mapOf(
            "finalOutcome" to finalOutcome,
            "finalAt" to FieldValue.serverTimestamp()
        )
        docRef.set(data, SetOptions.merge())
    }

    fun declineOffer(orderId: String, serviceType: String, userPrefs: UserPreferencesRepository) {
        userPrefs.addDeclinedOrderId(orderId)
        logOfferOutcome(orderId, serviceType, "declined")
        if (_incomingOffer.value?.id == orderId) {
            _incomingOffer.value = null
        }
        if (_incomingOfferBundle.value?.orders?.any { it.id == orderId } == true) {
            _incomingOfferBundle.value = null
        }
    }

    fun missOffer(orderId: String, serviceType: String, userPrefs: UserPreferencesRepository) {
        userPrefs.addDeclinedOrderId(orderId)
        logOfferOutcome(orderId, serviceType, "missed")
        if (_incomingOffer.value?.id == orderId) {
            _incomingOffer.value = null
        }
        if (_incomingOfferBundle.value?.orders?.any { it.id == orderId } == true) {
            _incomingOfferBundle.value = null
        }
    }

    // REPORT INCIDENT / PROBLEM
    fun reportIncident(orderId: String, reason: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val uid = auth.currentUser?.uid ?: run { onError("Not logged in"); return }
        val incident = mapOf(
            "type" to "incident",
            "riderId" to uid,
            "orderId" to orderId,
            "reason" to reason,
            "createdAt" to FieldValue.serverTimestamp()
        )
        db.collection("adminNotifications").add(incident)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.localizedMessage ?: "Failed to report incident") }
    }

    // INBOX ACTIONS
    fun markInboxMessageRead(messageId: String) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("riders").document(uid).collection("inboxReads").document(messageId)
            .set(mapOf("readAt" to FieldValue.serverTimestamp()))
    }

    fun markAllInboxRead() {
        val uid = auth.currentUser?.uid ?: return
        val batch = db.batch()
        for (msg in _inboxMessages.value) {
            val docRef = db.collection("riders").document(uid).collection("inboxReads").document(msg.id)
            batch.set(docRef, mapOf("readAt" to FieldValue.serverTimestamp()))
        }
        batch.commit()
    }

    fun stopListening() {
        activeSessionUid = null
        activeOrdersRetryJob?.cancel()
        activeOrdersRetryJob = null
        offersRetryJob?.cancel()
        offersRetryJob = null
        deliveredOrdersRetryJob?.cancel()
        deliveredOrdersRetryJob = null
        waveDelayJob?.cancel()
        waveDelayJob = null
        riderListener?.remove()
        activeOrdersListener?.remove()
        offersListener?.remove()
        deliveredOrdersListener?.remove()
        tierConfigListener?.remove()
        inboxDirectListener?.remove()
        inboxAllListener?.remove()
        inboxReadsListener?.remove()
        coinTxListener?.remove()
        walletTransactionsListener?.remove()
        platformSettingsListener?.remove()
        platformSettingsListener = null
        citySettingsListener?.remove()
        citySettingsListener = null
        offerLogListener?.remove()
        offerLogListener = null
        reviewsListener?.remove()
        reviewsListener = null
        currentCityId = null
        linkedUserListener?.remove()
        linkedUserListener = null
        customerTransactionsListener?.remove()
        currentLinkedCustomerUid = null
        deliveredListenerInitialized = false
        knownLateTips.clear()
        supportTicketsListener?.remove()
        supportTicketsListener = null
        activeTicketMessagesListener?.remove()
        activeTicketMessagesListener = null
        questsListener?.remove()
        questsListener = null
        questClaimsListener?.remove()
        questClaimsListener = null
        chatThreadsListener?.remove()
        chatThreadsListener = null
        activeOrderChatMessagesListener?.remove()
        activeOrderChatMessagesListener = null
        dispatchSettingsListener?.remove()
        dispatchSettingsListener = null
        currentChatId = null
        activeUserPrefs = null
        _lastTaxiDiagnostics.value = null
        _dispatchListenerStatus.value = DispatchListenerStatus(state = ListenerConnectionState.DISCONNECTED)
    }

    // SUBMIT SHOP REVIEW
    fun submitShopReview(
        orderId: String,
        merchantId: String,
        serviceType: String,
        ratingMerchant: Int,
        comment: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid ?: run {
            onError("Not authenticated")
            return
        }
        val data = mapOf(
            "orderId" to orderId,
            "riderId" to uid,
            "merchantId" to merchantId,
            "serviceType" to serviceType,
            "ratingMerchant" to ratingMerchant,
            "comment" to comment.trim().take(200),
            "createdAt" to FieldValue.serverTimestamp()
        )
        db.collection("riderReviews").document(orderId).set(data, SetOptions.merge())
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.localizedMessage ?: "Failed to submit review") }
    }

    // SUPPORT TICKET OPERATIONS
    fun selectTicket(ticketId: String) {
        val ticket = _supportTickets.value.firstOrNull { it.id == ticketId }
        _activeTicket.value = ticket
        listenToTicketMessages(ticketId)
        // Mark unreadByRider = false
        db.collection("supportTickets").document(ticketId).update("unreadByRider", false)
    }

    fun listenToTicketMessages(ticketId: String) {
        activeTicketMessagesListener?.remove()
        activeTicketMessagesListener = db.collection("supportTickets")
            .document(ticketId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null) return@addSnapshotListener
                val msgs = snapshots.documents.mapNotNull { parseSupportTicketMessage(it) }
                val prevMsgs = _activeTicketMessages.value
                _activeTicketMessages.value = msgs

                // Check if new admin message arrived to display in-app notification banner
                if (prevMsgs.isNotEmpty() && msgs.size > prevMsgs.size) {
                    val newMsg = msgs.last()
                    if (newMsg.isAdmin) {
                        val t = _supportTickets.value.firstOrNull { it.id == ticketId }
                        _adminMessageBanner.value = AdminMessageBannerInfo(
                            ticketId = ticketId,
                            ticketTitle = t?.title ?: "Delivio Support",
                            senderName = "Delivio Support",
                            messageText = newMsg.text.ifBlank { "Sent an attachment" }
                        )
                    }
                }
            }
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
        val currentRider = _rider.value
        val uid = currentRider?.uid ?: auth.currentUser?.uid
        if (uid == null) {
            onError("User is not signed in.")
            return
        }

        // Constraint: At most 3 unresolved reports
        if (_unresolvedTickets.value.size >= 3) {
            onError("You have reached the limit of 3 unresolved reports. Please wait for Delivio support to address your open reports.")
            return
        }

        val trimmedTitle = title.trim()
        if (trimmedTitle.length !in 3..80) {
            onError("Title must be between 3 and 80 characters.")
            return
        }

        val ticketRef = db.collection("supportTickets").document()
        val ticketId = ticketRef.id
        val batch = db.batch()

        val ticketData = hashMapOf<String, Any?>(
            "reporterType" to "rider",
            "riderId" to uid,
            "riderName" to (currentRider?.name?.ifBlank { "Driver" } ?: "Driver"),
            "orderId" to order.id,
            "orderNumber" to order.orderNumber,
            "batchId" to (order.batchId ?: ""),
            "serviceType" to order.serviceType,
            "reason" to reasonKey,
            "reasonLabel" to reasonLabel,
            "title" to trimmedTitle,
            "splitItems" to splitItems,
            "status" to "open",
            "createdAt" to FieldValue.serverTimestamp(),
            "lastMessageAt" to FieldValue.serverTimestamp(),
            "lastMessagePreview" to trimmedTitle,
            "unreadByAdmin" to true,
            "unreadByRider" to false
        )
        batch.set(ticketRef, ticketData)

        // The Details (if entered) as initial message
        if (details.trim().isNotEmpty()) {
            val detailsMsgRef = ticketRef.collection("messages").document()
            val detailsData = hashMapOf<String, Any?>(
                "senderType" to "rider",
                "senderId" to uid,
                "text" to details.trim().take(500),
                "isInitial" to true,
                "createdAt" to FieldValue.serverTimestamp()
            )
            batch.set(detailsMsgRef, detailsData)
        }

        // Each photo as its own message
        photosBase64.take(3).forEach { photoDataString ->
            val photoMsgRef = ticketRef.collection("messages").document()
            val photoData = hashMapOf<String, Any?>(
                "senderType" to "rider",
                "senderId" to uid,
                "text" to "",
                "imageData" to photoDataString,
                "isInitial" to true,
                "createdAt" to FieldValue.serverTimestamp()
            )
            batch.set(photoMsgRef, photoData)
        }

        batch.commit()
            .addOnSuccessListener {
                selectTicket(ticketId)
                onSuccess(ticketId)
            }
            .addOnFailureListener { e ->
                onError(e.localizedMessage ?: "Failed to submit report.")
            }
    }

    fun sendTicketMessage(
        ticketId: String,
        text: String,
        imageData: String?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val uid = _rider.value?.uid ?: auth.currentUser?.uid ?: run {
            onError("User not signed in")
            return
        }
        if (text.isBlank() && imageData.isNullOrBlank()) {
            onError("Message cannot be empty")
            return
        }

        val ticketRef = db.collection("supportTickets").document(ticketId)
        val msgRef = ticketRef.collection("messages").document()

        val msgData = hashMapOf<String, Any?>(
            "senderType" to "rider",
            "senderId" to uid,
            "text" to text.trim(),
            "imageData" to imageData,
            "isInitial" to false,
            "createdAt" to FieldValue.serverTimestamp()
        )

        val preview = when {
            text.isNotBlank() -> text.trim()
            !imageData.isNullOrBlank() -> "Photo sent"
            else -> "Message"
        }

        val batch = db.batch()
        batch.set(msgRef, msgData)
        batch.update(ticketRef, mapOf(
            "lastMessageAt" to FieldValue.serverTimestamp(),
            "lastMessagePreview" to preview,
            "unreadByAdmin" to true
        ))

        batch.commit()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.localizedMessage ?: "Failed to send message") }
    }

    // ==========================================
    // ORDER CUSTOMER CHAT (CHAT DATA CONTRACT)
    // ==========================================

    private fun parseChatThread(doc: DocumentSnapshot): OrderChatThread? {
        return try {
            OrderChatThread(
                id = doc.id,
                orderId = doc.getString("orderId") ?: "",
                orderNumber = doc.getString("orderNumber") ?: "",
                channel = doc.getString("channel") ?: "rider",
                customerId = doc.getString("customerId") ?: "",
                customerName = doc.getString("customerName") ?: "",
                merchantId = doc.getString("merchantId") ?: "",
                merchantName = doc.getString("merchantName") ?: "",
                branchName = doc.getString("branchName") ?: "",
                branchCode = doc.getString("branchCode") ?: "",
                riderId = doc.getString("riderId") ?: "",
                riderName = doc.getString("riderName") ?: "",
                createdAt = doc.get("createdAt"),
                lastMessageAt = doc.get("lastMessageAt"),
                lastMessageText = doc.getString("lastMessageText") ?: "",
                lastSenderRole = doc.getString("lastSenderRole") ?: "",
                customerReadAt = doc.get("customerReadAt"),
                partnerReadAt = doc.get("partnerReadAt"),
                expireAt = doc.get("expireAt")
            )
        } catch (e: Exception) {
            com.example.util.CrashReportStore.logNonFatalParseError(appContext, doc.reference.path, e)
            null
        }
    }

    private fun parseChatMessage(doc: DocumentSnapshot): OrderChatMessage? {
        return try {
            OrderChatMessage(
                id = doc.id,
                senderId = doc.getString("senderId") ?: "",
                senderRole = doc.getString("senderRole") ?: "customer",
                type = doc.getString("type") ?: "text",
                text = doc.getString("text") ?: "",
                image = doc.getString("image") ?: "",
                source = doc.getString("source") ?: "",
                createdAt = doc.get("createdAt"),
                expireAt = doc.get("expireAt")
            )
        } catch (e: Exception) {
            com.example.util.CrashReportStore.logNonFatalParseError(appContext, doc.reference.path, e)
            null
        }
    }

    /**
     * Automatic cleanup of expired chats (no server needed).
     * Whenever the app loads its list of chat threads (and when the chat screen opens),
     * delete expired data that belongs to the signed-in user:
     * For each thread I can read, query its chatMessages where expireAt < now (limit 100)
     * and delete them in a batch, then delete the thread itself if its own expireAt < now.
     * Runs at most once every 30 minutes, in the background, and ignores any errors silently.
     */
    fun cleanupExpiredChats(force: Boolean = false) {
        val currentRider = _rider.value
        val uid = currentRider?.uid ?: auth.currentUser?.uid ?: return

        val now = System.currentTimeMillis()
        synchronized(this) {
            if (!force && (now - lastChatCleanupTimeMillis < OrderChatConstants.CLEANUP_INTERVAL_MILLIS)) {
                return
            }
            lastChatCleanupTimeMillis = now
        }

        scope.launch(Dispatchers.IO) {
            try {
                val nowTimestamp = Timestamp.now()
                val nowMillis = nowTimestamp.toDate().time

                // Query threads I can read: orderChats where riderId == uid and channel == rider
                val threadsSnap = Tasks.await(
                    db.collection("orderChats")
                        .whereEqualTo("riderId", uid)
                        .whereEqualTo("channel", "rider")
                        .get()
                )

                for (doc in threadsSnap.documents) {
                    val threadRiderId = doc.getString("riderId") ?: ""
                    // Security requirement: Never delete threads of other users
                    if (threadRiderId != uid) continue

                    val threadRef = doc.reference

                    // 1. Query chatMessages where expireAt < now (limit 100) and delete in a batch
                    try {
                        val expiredMsgsSnap = Tasks.await(
                            threadRef.collection("chatMessages")
                                .whereLessThan("expireAt", nowTimestamp)
                                .limit(OrderChatConstants.CLEANUP_MESSAGES_LIMIT)
                                .get()
                        )

                        if (!expiredMsgsSnap.isEmpty) {
                            val batch = db.batch()
                            var deleteCount = 0
                            for (msgDoc in expiredMsgsSnap.documents) {
                                val msgExpire = parseChatTimestamp(msgDoc.get("expireAt"))
                                // Do not delete anything that is not expired
                                if (msgExpire in 1..nowMillis) {
                                    batch.delete(msgDoc.reference)
                                    deleteCount++
                                }
                            }
                            if (deleteCount > 0) {
                                Tasks.await(batch.commit())
                            }
                        }
                    } catch (_: Exception) {
                        // ignore any errors silently
                    }

                    // 2. Then delete the thread itself if its own expireAt < now
                    try {
                        val threadExpireMillis = parseChatTimestamp(doc.get("expireAt"))
                        // Do not delete anything that is not expired
                        if (threadExpireMillis in 1..nowMillis) {
                            Tasks.await(threadRef.delete())
                        }
                    } catch (_: Exception) {
                        // ignore any errors silently
                    }
                }
            } catch (_: Exception) {
                // ignore any errors silently
            }
        }
    }

    fun openOrderChat(chatId: String) {
        currentChatId = chatId
        activeOrderChatMessagesListener?.remove()

        // Clean up expired chats whenever chat opens (at most once every 30 minutes, in background)
        cleanupExpiredChats()

        // Set my ReadAt when I open the chat (marking as read updates only partnerReadAt)
        markChatRead(chatId)

        // Query messages: orderChats/{chatId}/chatMessages (no orderBy, sort on device)
        activeOrderChatMessagesListener = db.collection("orderChats")
            .document(chatId)
            .collection("chatMessages")
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null) return@addSnapshotListener
                val msgs = snapshots.documents.mapNotNull { parseChatMessage(it) }
                    .sortedBy { it.timeMillis }
                _activeChatMessages.value = msgs
            }
    }

    fun closeOrderChat() {
        activeOrderChatMessagesListener?.remove()
        activeOrderChatMessagesListener = null
        currentChatId = null
        _activeChatMessages.value = emptyList()
    }

    fun markChatRead(chatId: String) {
        if (chatId.isBlank()) return
        db.collection("orderChats").document(chatId)
            .update("partnerReadAt", FieldValue.serverTimestamp())
            .addOnFailureListener { /* silently ignore if doc not yet created */ }
    }

    fun sendOrderChatMessage(
        order: Order,
        text: String,
        imageDataUri: String? = null,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val currentRider = _rider.value ?: run {
            onError("Rider profile not loaded")
            return
        }
        val riderUid = currentRider.uid
        val chatId = "${order.id}_rider_${riderUid}"
        val threadRef = db.collection("orderChats").document(chatId)
        val messageRef = threadRef.collection("chatMessages").document()

        val isImage = !imageDataUri.isNullOrBlank()
        val messageType = if (isImage) "image" else "text"
        val lastPreview = if (isImage) "Photo" else text.trim().take(80)
        val expireDate = Date(System.currentTimeMillis() + 10L * 24 * 60 * 60 * 1000L)
        val expireTimestamp = com.google.firebase.Timestamp(expireDate)

        threadRef.get().addOnSuccessListener { snapshot ->
            val batch = db.batch()
            if (!snapshot.exists()) {
                val threadData = hashMapOf<String, Any?>(
                    "orderId" to order.id,
                    "orderNumber" to order.displayOrderNumber,
                    "channel" to "rider",
                    "customerId" to order.customerId,
                    "customerName" to formatCustomerFirstName(order.customerName),
                    "merchantId" to order.merchantId,
                    "merchantName" to order.displayMerchantName.ifBlank { order.merchantName },
                    "riderId" to riderUid,
                    "riderName" to currentRider.name,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "lastMessageText" to lastPreview,
                    "lastSenderRole" to "rider",
                    "customerReadAt" to null,
                    "partnerReadAt" to FieldValue.serverTimestamp(),
                    "expireAt" to expireTimestamp
                )
                batch.set(threadRef, threadData)
            } else {
                val updates = hashMapOf<String, Any?>(
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "lastMessageText" to lastPreview,
                    "lastSenderRole" to "rider",
                    "partnerReadAt" to FieldValue.serverTimestamp(),
                    "expireAt" to expireTimestamp
                )
                batch.update(threadRef, updates)
            }

            val messageData = hashMapOf<String, Any?>(
                "senderId" to riderUid,
                "senderRole" to "rider",
                "type" to messageType,
                "text" to (if (isImage) text.trim() else text.trim()),
                "image" to (imageDataUri ?: ""),
                "source" to (if (isImage) "camera" else ""),
                "createdAt" to FieldValue.serverTimestamp(),
                "expireAt" to expireTimestamp
            )
            batch.set(messageRef, messageData)

            batch.commit()
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener { e -> onError(e.localizedMessage ?: "Failed to send message") }
        }.addOnFailureListener { e ->
            onError(e.localizedMessage ?: "Failed to access chat thread")
        }
    }

    private fun parseSupportTicket(doc: DocumentSnapshot): SupportTicket? {
        return try {
            SupportTicket(
                id = doc.id,
                reporterType = doc.getString("reporterType") ?: "rider",
                riderId = doc.getString("riderId") ?: "",
                riderName = doc.getString("riderName") ?: "",
                orderId = doc.getString("orderId") ?: "",
                orderNumber = doc.getString("orderNumber") ?: "",
                batchId = doc.getString("batchId") ?: "",
                serviceType = doc.getString("serviceType") ?: "",
                reason = doc.getString("reason") ?: "",
                reasonLabel = doc.getString("reasonLabel") ?: "",
                title = doc.getString("title") ?: "",
                splitItems = (doc.get("splitItems") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList(),
                status = doc.getString("status") ?: "open",
                resolutionNote = doc.getString("resolutionNote") ?: "",
                createdAt = doc.getTimestamp("createdAt"),
                lastMessageAt = doc.getTimestamp("lastMessageAt"),
                lastMessagePreview = doc.getString("lastMessagePreview") ?: "",
                unreadByAdmin = doc.getBoolean("unreadByAdmin") ?: false,
                unreadByRider = doc.getBoolean("unreadByRider") ?: false
            )
        } catch (e: Exception) {
            com.example.util.CrashReportStore.logNonFatalParseError(appContext, doc.reference.path, e)
            null
        }
    }

    private fun parseSupportTicketMessage(doc: DocumentSnapshot): SupportTicketMessage? {
        return try {
            SupportTicketMessage(
                id = doc.id,
                senderType = doc.getString("senderType") ?: "rider",
                senderId = doc.getString("senderId") ?: "",
                text = doc.getString("text") ?: "",
                imageData = doc.getString("imageData"),
                isInitial = doc.getBoolean("isInitial") ?: false,
                createdAt = doc.getTimestamp("createdAt")
            )
        } catch (e: Exception) {
            com.example.util.CrashReportStore.logNonFatalParseError(appContext, doc.reference.path, e)
            null
        }
    }

    // Helpers
    private fun parseWalletTransaction(doc: DocumentSnapshot): WalletTransaction? {
        try {
            val id = doc.id
            val riderId = doc.getString("riderId") ?: ""
            val customerId = doc.getString("customerId") ?: ""
            val type = doc.getString("type") ?: doc.getString("transactionType") ?: "cash_in"
            val amount = doc.getDouble("amount")
                ?: (doc.getLong("amount")?.toDouble() ?: 0.0)
            val note = doc.getString("note")
                ?: doc.getString("description")
                ?: doc.getString("remarks")
                ?: ""
            val balanceAfter = doc.getDouble("balanceAfter")
                ?: doc.getLong("balanceAfter")?.toDouble()

            val rawDate = doc.get("createdAt")
                ?: doc.get("date")
                ?: doc.get("timestamp")

            var dateRaw = 0L
            val dateText = when (rawDate) {
                is com.google.firebase.Timestamp -> {
                    dateRaw = rawDate.toDate().time
                    val sdf = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
                    sdf.format(rawDate.toDate())
                }
                is Date -> {
                    dateRaw = rawDate.time
                    val sdf = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
                    sdf.format(rawDate)
                }
                is Number -> {
                    val num = rawDate.toLong()
                    val millis = if (num < 1000000000000L) num * 1000L else num
                    dateRaw = millis
                    val sdf = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
                    sdf.format(Date(millis))
                }
                else -> {
                    val str = doc.getString("dateText") ?: doc.getString("date") ?: doc.getString("createdAt") ?: ""
                    if (str.isNotBlank()) {
                        try {
                            dateRaw = java.time.Instant.parse(str).toEpochMilli()
                        } catch (_: Exception) {}
                    }
                    str.ifBlank { "Recent" }
                }
            }

            return WalletTransaction(
                id = id,
                riderId = riderId,
                customerId = customerId,
                type = type,
                amount = amount,
                note = note,
                dateText = dateText,
                dateRaw = dateRaw,
                balanceAfter = balanceAfter,
                title = if (note.isNotBlank()) note else WalletTransaction.formatType(type),
                referenceNo = "TXN-${id.takeLast(6).uppercase()}",
                category = type,
                isCredit = amount >= 0 && type.lowercase() !in listOf("cash_out", "deduction", "fee", "penalty", "debit")
            )
        } catch (e: Exception) {
            com.example.util.CrashReportStore.logNonFatalParseError(appContext, doc.reference.path, e)
            return null
        }
    }

    private fun formatScheduledDate(raw: Any?): String {
        if (raw == null) return ""
        return when (raw) {
            is com.google.firebase.Timestamp -> {
                val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                sdf.format(raw.toDate())
            }
            is Date -> {
                val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                sdf.format(raw)
            }
            is Number -> {
                val num = raw.toLong()
                val millis = if (num < 1000000000000L) num * 1000L else num
                val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                sdf.format(Date(millis))
            }
            is String -> {
                raw.trim()
            }
            else -> raw.toString()
        }
    }

    // Helpers
    private fun parseOrderSafely(doc: DocumentSnapshot, sourceLabel: String = "orders"): Order? {
        return try {
            val data = doc.data ?: return null
            val mId = OrderDocumentParser.safeString(data["merchantId"])
            val sType = OrderDocumentParser.safeString(data["serviceType"], OrderStepConstants.SERVICE_FOOD)
                .lowercase(Locale.US).trim()
            val isNonMerchant = sType == OrderStepConstants.SERVICE_TAXI ||
                sType == OrderStepConstants.SERVICE_PARCEL ||
                sType == OrderStepConstants.SERVICE_PABILI
            val cachedMerchant = if (!isNonMerchant && mId.isNotBlank()) merchantCache[mId] else null

            val order = OrderDocumentParser.parseOrderFromMap(
                id = doc.id,
                data = data,
                scheduledReleaseLeadMinutes = _scheduledReleaseLeadMinutes.value,
                fallbackMerchant = cachedMerchant
            )

            // Only fetch merchant fallback for merchant-backed services (food/convenience), never for taxi/parcel/pabili
            if (!order.isNonMerchantService && order.merchantId.isNotBlank() && cachedMerchant == null) {
                val needsMerchantFallback = order.merchantLat == 0.0 ||
                    (order.branchName.isBlank() && order.branchCode.isBlank()) ||
                    order.merchantName.isBlank() ||
                    order.merchantName.equals("Merchant", ignoreCase = true)
                if (needsMerchantFallback) {
                    fetchMerchantInfo(order.merchantId)
                }
            }
            order
        } catch (t: Throwable) {
            recordBadOrderDocument(doc, sourceLabel, t)
            null
        }
    }

    private fun parseInbox(doc: DocumentSnapshot): MessageNotification? {
        try {
            val title = doc.getString("title") ?: "Notification"
            val body = doc.getString("body") ?: doc.getString("message") ?: ""
            val category = doc.getString("category") ?: "system"
            val sendAt = doc.get("sendAt") ?: doc.get("createdAt")
            val msg = MessageNotification(
                id = doc.id,
                title = title,
                body = body,
                category = category,
                createdAt = doc.get("createdAt"),
                sendAt = sendAt,
                timestampText = formatInboxTimestamp(sendAt ?: doc.get("createdAt"))
            )
            // Hide any inbox item whose sendAt is in the future
            if (msg.isFuture()) {
                return null
            }
            return msg
        } catch (e: Exception) {
            com.example.util.CrashReportStore.logNonFatalParseError(appContext, doc.reference.path, e)
            return null
        }
    }

    private fun formatInboxTimestamp(raw: Any?): String {
        val ms = when (raw) {
            is com.google.firebase.Timestamp -> raw.toDate().time
            is Date -> raw.time
            is Number -> raw.toLong()
            else -> 0L
        }
        if (ms <= 0L) return "Recent"
        val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.US)
        return sdf.format(Date(ms))
    }

    private val merchantCache = java.util.concurrent.ConcurrentHashMap<String, Map<String, Any?>>()
    private val inFlightMerchantFetches = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private fun fetchMerchantInfo(merchantId: String) {
        val cached = merchantCache[merchantId]
        if (cached != null) {
            applyMerchantInfo(merchantId, cached)
            return
        }
        if (!inFlightMerchantFetches.add(merchantId)) return

        db.collection("merchants").document(merchantId).get()
            .addOnSuccessListener { doc ->
                inFlightMerchantFetches.remove(merchantId)
                if (doc.exists()) {
                    val mLat = (doc.get("lat") as? Number)?.toDouble()
                        ?: (doc.get("latitude") as? Number)?.toDouble() ?: 0.0
                    val mLng = (doc.get("lng") as? Number)?.toDouble()
                        ?: (doc.get("longitude") as? Number)?.toDouble() ?: 0.0
                    val mName = doc.getString("name")?.trim() ?: ""
                    val mBranchName = doc.getString("branchName")?.trim() ?: ""
                    val mBranchCode = doc.getString("branchCode")?.trim() ?: ""
                    val mChainId = doc.getString("chainId")?.trim() ?: ""
                    val mAddr = doc.getString("address") ?: ""
                    val mPhone = doc.getString("phone") ?: ""
                    val data = mapOf<String, Any?>(
                        "lat" to mLat,
                        "lng" to mLng,
                        "name" to mName,
                        "branchName" to mBranchName,
                        "branchCode" to mBranchCode,
                        "chainId" to mChainId,
                        "address" to mAddr,
                        "phone" to mPhone
                    )
                    merchantCache[merchantId] = data
                    applyMerchantInfo(merchantId, data)
                } else {
                    merchantCache[merchantId] = emptyMap()
                }
            }
            .addOnFailureListener {
                inFlightMerchantFetches.remove(merchantId)
            }
    }

    private fun applyMerchantInfo(merchantId: String, data: Map<String, Any?>) {
        if (data.isEmpty()) return
        val mLat = data["lat"] as? Double ?: 0.0
        val mLng = data["lng"] as? Double ?: 0.0
        val mName = (data["name"] as? String).orEmpty()
        val mBranchName = (data["branchName"] as? String).orEmpty()
        val mBranchCode = (data["branchCode"] as? String).orEmpty()
        val mChainId = (data["chainId"] as? String).orEmpty()
        val mAddr = (data["address"] as? String).orEmpty()
        val mPhone = (data["phone"] as? String).orEmpty()

        fun mergeOrder(o: Order): Order {
            if (o.merchantId != merchantId || o.isNonMerchantService) return o
            val useFallbackBranch = o.branchName.isBlank() && o.branchCode.isBlank()
            return o.copy(
                merchantLat = if (o.merchantLat == 0.0 && mLat != 0.0) mLat else o.merchantLat,
                merchantLng = if (o.merchantLng == 0.0 && mLng != 0.0) mLng else o.merchantLng,
                merchantName = if ((o.merchantName.isBlank() || o.merchantName.equals("Merchant", ignoreCase = true)) && mName.isNotBlank()) mName else o.merchantName,
                branchName = if (useFallbackBranch) mBranchName else o.branchName,
                branchCode = if (useFallbackBranch) mBranchCode else o.branchCode,
                chainId = if (o.chainId.isBlank() && mChainId.isNotBlank()) mChainId else o.chainId,
                merchantAddress = if (o.merchantAddress.isBlank() && mAddr.isNotBlank()) mAddr else o.merchantAddress,
                merchantPhone = if (o.merchantPhone.isBlank() && mPhone.isNotBlank()) mPhone else o.merchantPhone
            )
        }

        val currentActive = _activeOrders.value
        if (currentActive.any { it.merchantId == merchantId && !it.isNonMerchantService }) {
            _activeOrders.value = currentActive.map { mergeOrder(it) }
        }

        val currentDelivered = _deliveredOrders.value
        if (currentDelivered.any { it.merchantId == merchantId && !it.isNonMerchantService }) {
            _deliveredOrders.value = currentDelivered.map { mergeOrder(it) }
        }

        _incomingOfferBundle.value?.let { bundle ->
            if (bundle.orders.any { it.merchantId == merchantId && !it.isNonMerchantService }) {
                val updatedOrders = bundle.orders.map { mergeOrder(it) }
                _incomingOfferBundle.value = bundle.copy(orders = updatedOrders)
                _incomingOffer.value = updatedOrders.firstOrNull()
            }
        }
        _incomingOffer.value?.let { offer ->
            if (offer.merchantId == merchantId && !offer.isNonMerchantService && _incomingOfferBundle.value == null) {
                _incomingOffer.value = mergeOrder(offer)
            }
        }
    }

    private fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    // ================= QUESTS LOGIC =================

    private fun parseQuest(doc: DocumentSnapshot): Quest? {
        return try {
            val title = doc.getString("title") ?: "Quest"
            val description = doc.getString("description") ?: ""
            val status = doc.getString("status") ?: "active"
            val metric = doc.getString("metric") ?: "deliveries"
            val startAt = doc.getTimestamp("startAt")
            val endAt = doc.getTimestamp("endAt")
            val hourFrom = doc.getLong("hourFrom")?.toInt()
            val hourTo = doc.getLong("hourTo")?.toInt()
            val cityIds = (doc.get("cityIds") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
            val vehicleTypes = (doc.get("vehicleTypes") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
            val riderIds = (doc.get("riderIds") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
            val services = (doc.get("services") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
            val createdAt = doc.getTimestamp("createdAt")

            val rawGoals = (doc.get("goals") as? List<*>) ?: (doc.get("milestones") as? List<*>) ?: (doc.get("levels") as? List<*>)
            val goals = mutableListOf<QuestGoal>()
            rawGoals?.forEachIndexed { idx, raw ->
                if (raw is Map<*, *>) {
                    val targetVal = (raw["target"] as? Number)?.toDouble()
                        ?: (raw["deliveries"] as? Number)?.toDouble()
                        ?: (raw["amount"] as? Number)?.toDouble() ?: 0.0
                    val rewardVal = (raw["reward"] as? Number)?.toDouble()
                        ?: (raw["payout"] as? Number)?.toDouble()
                        ?: (raw["bonus"] as? Number)?.toDouble() ?: 0.0
                    val minAcc = (raw["minAcceptanceRate"] as? Number)?.toDouble()
                        ?: (raw["acceptanceRate"] as? Number)?.toDouble()
                    val maxNo = (raw["maxNoShows"] as? Number)?.toInt()
                        ?: (raw["noShows"] as? Number)?.toInt()
                    goals.add(
                        QuestGoal(
                            level = idx + 1,
                            target = targetVal,
                            reward = rewardVal,
                            minAcceptanceRate = minAcc,
                            maxNoShows = maxNo
                        )
                    )
                }
            }

            Quest(
                id = doc.id,
                title = title,
                description = description,
                status = status,
                metric = metric,
                startAt = startAt,
                endAt = endAt,
                hourFrom = hourFrom,
                hourTo = hourTo,
                cityIds = cityIds,
                vehicleTypes = vehicleTypes,
                riderIds = riderIds,
                services = services,
                goals = goals.sortedBy { it.target },
                createdAt = createdAt
            )
        } catch (e: Exception) {
            com.example.util.CrashReportStore.logNonFatalParseError(appContext, doc.reference.path, e)
            null
        }
    }

    private fun parseQuestClaim(doc: DocumentSnapshot): QuestClaim? {
        return try {
            QuestClaim(
                id = doc.id,
                questId = doc.getString("questId") ?: "",
                questTitle = doc.getString("questTitle") ?: "",
                riderId = doc.getString("riderId") ?: "",
                riderName = doc.getString("riderName") ?: "",
                metric = doc.getString("metric") ?: "deliveries",
                value = doc.getDouble("value") ?: 0.0,
                deliveries = doc.getLong("deliveries")?.toInt() ?: 0,
                earnings = doc.getDouble("earnings") ?: 0.0,
                acceptanceRate = doc.getDouble("acceptanceRate") ?: 100.0,
                noShows = doc.getLong("noShows")?.toInt() ?: 0,
                levelReached = doc.getLong("levelReached")?.toInt() ?: -1,
                rewardAmount = doc.getDouble("rewardAmount") ?: 0.0,
                status = doc.getString("status") ?: "Pending",
                note = doc.getString("note") ?: doc.getString("resolutionNote") ?: "",
                createdAt = doc.getTimestamp("createdAt")
            )
        } catch (e: Exception) {
            com.example.util.CrashReportStore.logNonFatalParseError(appContext, doc.reference.path, e)
            null
        }
    }

    fun computeQuestProgress(
        quest: Quest,
        deliveredOrders: List<Order>,
        offerLogs: List<OfferLogEntry>,
        claim: QuestClaim? = null
    ): QuestProgress {
        val startMillis = quest.startAtMillis
        val endMillis = quest.endAtMillis

        // Matching delivered orders within [startAt, endAt]
        val matchingOrders = deliveredOrders.filter { order ->
            val dMillis = order.deliveredAtMillis
            if (startMillis > 0L && dMillis < startMillis) return@filter false
            if (endMillis > 0L && dMillis > endMillis) return@filter false

            // Hour window check (in Philippine Time UTC+8)
            if (quest.hourFrom != null && quest.hourTo != null && dMillis > 0L) {
                val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Manila"), Locale.US).apply {
                    timeInMillis = dMillis
                }
                val hour = cal.get(Calendar.HOUR_OF_DAY)
                if (hour < quest.hourFrom || hour >= quest.hourTo) return@filter false
            }

            // Services filter (when set)
            if (quest.services.isNotEmpty()) {
                val matches = quest.services.any { svc -> order.serviceType.equals(svc, ignoreCase = true) }
                if (!matches) return@filter false
            }

            true
        }

        val totalDeliveries = matchingOrders.size
        val totalEarnings = matchingOrders.sumOf { it.riderEarning?.total ?: it.estimatedEarnings }

        val currentValue = if (quest.metric.lowercase().contains("earning")) {
            totalEarnings
        } else {
            totalDeliveries.toDouble()
        }

        // Offer log entries inside [startAt, endAt]
        val periodLogs = offerLogs.filter { log ->
            val at = log.atMillis
            if (startMillis > 0L && at < startMillis) return@filter false
            if (endMillis > 0L && at > endMillis) return@filter false
            true
        }

        val acceptedCount = periodLogs.count { it.outcome.equals("accepted", ignoreCase = true) }
        val declinedCount = periodLogs.count { it.outcome.equals("declined", ignoreCase = true) }
        val missedCount = periodLogs.count { it.outcome.equals("missed", ignoreCase = true) }
        val totalOffers = acceptedCount + declinedCount + missedCount
        val acceptanceRate = if (totalOffers > 0) (acceptedCount.toDouble() / totalOffers) * 100.0 else 100.0

        val noShows = periodLogs.count { it.finalOutcome.equals("released", ignoreCase = true) }

        // Evaluate goals (sorted by target ascending)
        val sortedGoals = quest.sortedGoals
        var levelReached = -1
        var currentReward = 0.0

        sortedGoals.forEachIndexed { idx, goal ->
            val targetMet = currentValue >= goal.target
            val minAcc = goal.minAcceptanceRate
            val accMet = minAcc == null || minAcc <= 0.0 || (acceptanceRate >= if (minAcc <= 1.0) minAcc * 100.0 else minAcc)
            val maxNo = goal.maxNoShows
            val noShowsMet = maxNo == null || noShows <= maxNo

            if (targetMet && accMet && noShowsMet) {
                levelReached = idx
                currentReward = goal.reward
            }
        }

        val nextGoal = if (levelReached + 1 < sortedGoals.size) {
            sortedGoals[levelReached + 1]
        } else null

        return QuestProgress(
            quest = quest,
            currentValue = currentValue,
            currentDeliveries = totalDeliveries,
            currentEarnings = totalEarnings,
            acceptanceRate = acceptanceRate,
            noShows = noShows,
            levelReachedIndex = levelReached,
            currentReward = currentReward,
            nextGoal = nextGoal,
            claim = claim
        )
    }

    fun checkAndCreateClaimsForEndedQuests() {
        val currentRider = _rider.value ?: return
        val uid = currentRider.uid
        if (uid.isBlank()) return

        val now = System.currentTimeMillis()
        val allQuests = _quests.value
        val endedApplicable = allQuests.filter { quest ->
            quest.endAtMillis in 1L until now && quest.appliesTo(currentRider)
        }

        for (quest in endedApplicable) {
            val docId = "${quest.id}_${uid}"
            val existing = _questClaims.value[quest.id]
            if (existing != null) continue

            val progress = computeQuestProgress(quest, _deliveredOrders.value, _offerLogs.value, null)
            val levelReached = progress.levelReachedIndex
            val rewardAmount = progress.currentReward
            val claimStatus = "Pending"

            val claimData = hashMapOf<String, Any?>(
                "questId" to quest.id,
                "questTitle" to quest.title,
                "riderId" to uid,
                "riderName" to currentRider.name,
                "metric" to quest.metric,
                "value" to progress.currentValue,
                "deliveries" to progress.currentDeliveries,
                "earnings" to progress.currentEarnings,
                "acceptanceRate" to progress.acceptanceRate,
                "noShows" to progress.noShows,
                "levelReached" to levelReached,
                "rewardAmount" to rewardAmount,
                "status" to claimStatus,
                "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )

            // Local optimistic claim so UI shows it immediately
            val localClaim = QuestClaim(
                id = docId,
                questId = quest.id,
                questTitle = quest.title,
                riderId = uid,
                riderName = currentRider.name,
                metric = quest.metric,
                value = progress.currentValue,
                deliveries = progress.currentDeliveries,
                earnings = progress.currentEarnings,
                acceptanceRate = progress.acceptanceRate,
                noShows = progress.noShows,
                levelReached = levelReached,
                rewardAmount = rewardAmount,
                status = claimStatus
            )
            val updatedMap = _questClaims.value.toMutableMap()
            updatedMap[quest.id] = localClaim
            _questClaims.value = updatedMap

            // Write to Firestore questClaims/{questId}_{uid} with fixed ID
            db.collection("questClaims").document(docId).get()
                .addOnSuccessListener { doc ->
                    if (!doc.exists()) {
                        db.collection("questClaims").document(docId).set(claimData)
                    }
                }
        }
    }

    private fun getDemoQuests(): List<Quest> {
        val tz = TimeZone.getTimeZone("Asia/Manila")

        // Quest 1: In progress (Lunch Rush Challenge)
        val calStart = Calendar.getInstance(tz, Locale.US).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val calEnd = Calendar.getInstance(tz, Locale.US).apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        val q1 = Quest(
            id = "quest_lunch_rush",
            title = "Lunch Rush Challenge",
            description = "Complete lunch deliveries to earn extra rewards on top of your standard fares. Keep your acceptance rate high!",
            status = "active",
            metric = "deliveries",
            startAt = com.google.firebase.Timestamp(calStart.time),
            endAt = com.google.firebase.Timestamp(calEnd.time),
            hourFrom = 11,
            hourTo = 14,
            services = listOf("food", "convenience"),
            goals = listOf(
                QuestGoal(level = 1, target = 6.0, reward = 35.0, minAcceptanceRate = 90.0, maxNoShows = 0),
                QuestGoal(level = 2, target = 12.0, reward = 75.0, minAcceptanceRate = 95.0, maxNoShows = 0),
                QuestGoal(level = 3, target = 18.0, reward = 120.0, minAcceptanceRate = 95.0, maxNoShows = 0)
            )
        )

        // Quest 2: Upcoming (Weekend Peak Surge)
        val calUpStart = Calendar.getInstance(tz, Locale.US).apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
        }
        val calUpEnd = Calendar.getInstance(tz, Locale.US).apply {
            add(Calendar.DAY_OF_YEAR, 3)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
        }
        val q2 = Quest(
            id = "quest_weekend_surge",
            title = "Weekend Peak Surge",
            description = "Weekend food and convenience surge quest for high-volume riders.",
            status = "active",
            metric = "deliveries",
            startAt = com.google.firebase.Timestamp(calUpStart.time),
            endAt = com.google.firebase.Timestamp(calUpEnd.time),
            services = listOf("food", "convenience"),
            goals = listOf(
                QuestGoal(level = 1, target = 10.0, reward = 60.0, minAcceptanceRate = 95.0, maxNoShows = 0),
                QuestGoal(level = 2, target = 20.0, reward = 150.0, minAcceptanceRate = 95.0, maxNoShows = 0)
            )
        )

        // Quest 3: History (Midweek Milestone - ended yesterday)
        val calPastStart = Calendar.getInstance(tz, Locale.US).apply {
            add(Calendar.DAY_OF_YEAR, -3)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
        }
        val calPastEnd = Calendar.getInstance(tz, Locale.US).apply {
            add(Calendar.DAY_OF_YEAR, -1)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
        }
        val q3 = Quest(
            id = "quest_midweek_milestone",
            title = "Midweek Milestone",
            description = "Complete midweek delivery goals to earn tier bonuses.",
            status = "active",
            metric = "deliveries",
            startAt = com.google.firebase.Timestamp(calPastStart.time),
            endAt = com.google.firebase.Timestamp(calPastEnd.time),
            goals = listOf(
                QuestGoal(level = 1, target = 8.0, reward = 50.0, minAcceptanceRate = 90.0, maxNoShows = 0),
                QuestGoal(level = 2, target = 15.0, reward = 100.0, minAcceptanceRate = 95.0, maxNoShows = 0)
            )
        )

        return listOf(q1, q2, q3)
    }
}
