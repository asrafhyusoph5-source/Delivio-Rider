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

class RiderRepository {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val scope = CoroutineScope(Dispatchers.IO + Job())

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
        val waveDelaySeconds = if (isPriorityRider) {
            min(km * dispatchSettings.waveSeconds, dispatchSettings.priorityHeadStartSeconds)
        } else {
            dispatchSettings.priorityHeadStartSeconds + (km * dispatchSettings.waveSeconds)
        }
        val eligibleSince = orders.minOfOrNull { it.holdUntilMillis - (dispatchSettings.holdMinutes * 60 * 1000).toLong() } ?: System.currentTimeMillis()

        val combinedBadge = when {
            orders.any { it.isPriority } -> "Priority"
            orders.any { it.badge.equals("split", ignoreCase = true) } -> "SPLIT"
            orders.any { it.badge.equals("saver", ignoreCase = true) } -> "Saver"
            type == OfferType.DOUBLE_BOOKING -> "Double Booking"
            type == OfferType.ADD_TO_BOOKING -> "Add-on"
            else -> firstOrder.badge
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
            badge = combinedBadge
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

        // 1. Listen to riders/{uid}
        riderListener = db.collection("riders").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _listenerError.value = "Rider profile listener: ${error.localizedMessage}"
                    return@addSnapshotListener
                }
                if (snapshot == null || !snapshot.exists()) return@addSnapshotListener
                val allowed = (snapshot.get("allowedServices") as? List<*>)?.mapNotNull { it?.toString() }
                    ?: OrderStepConstants.ALL_SERVICES
                val active = (snapshot.get("activeServices") as? List<*>)?.mapNotNull { it?.toString() }
                    ?: allowed

                val vehicleMap = snapshot.get("vehicle") as? Map<*, *>
                val vehicle = Vehicle(
                    type = vehicleMap?.get("type")?.toString() ?: snapshot.getString("vehicleType") ?: "Motorcycle",
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

                val r = Rider(
                    uid = uid,
                    riderCode = snapshot.getString("riderCode") ?: snapshot.getString("code") ?: ("RD-" + uid.takeLast(4).uppercase()),
                    name = snapshot.getString("name") ?: "Rider",
                    email = snapshot.getString("email") ?: (auth.currentUser?.email ?: ""),
                    phone = snapshot.getString("phone") ?: "",
                    photoUrl = snapshot.getString("photoUrl") ?: snapshot.getString("avatarUrl") ?: "",
                    vehicle = vehicle,
                    cityId = snapshot.getString("cityId") ?: "puerto_princesa",
                    rating = snapshot.getDouble("rating") ?: 5.0,
                    memberSince = snapshot.getString("memberSince") ?: "Active Member",
                    isOnline = snapshot.getBoolean("isOnline") ?: false,
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
                            }
                        }
                }
            }

        // 2. Listen to active orders assigned to rider (find active order by rider, not by status in query)
        activeOrdersListener = db.collection("orders")
            .whereEqualTo("riderId", uid)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    _listenerError.value = "Active orders listener: ${error.localizedMessage}"
                    return@addSnapshotListener
                }
                if (snapshots == null) return@addSnapshotListener
                val activeList = snapshots.documents.mapNotNull { parseOrder(it) }.filter { order ->
                    val s = order.status.trim().lowercase()
                    val step = order.riderStep.trim().lowercase()
                    s != "delivered" && s != "cancelled" && s != "canceled" && s != "rejected" && step != "completed"
                }
                _activeOrders.value = activeList
                val activeIds = activeList.map { it.id }.toSet()
                RiderLocationTracker.setActiveOrderIds(activeIds)
            }

        // 3. Listen to incoming offers: riderId == null and status in ["Placed Order", "Preparing", "Ready"]
        offersListener = db.collection("orders")
            .whereEqualTo("riderId", null)
            .whereIn("status", listOf(
                OrderStepConstants.STATUS_PLACED_ORDER,
                OrderStepConstants.STATUS_PREPARING,
                OrderStepConstants.STATUS_READY
            ))
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null) return@addSnapshotListener
                val rawCandidates = snapshots.documents.mapNotNull { parseOrder(it) }
                _candidateOrders.value = rawCandidates
                val currentRider = _rider.value
                val activeList = _activeOrders.value
                val riderLoc = RiderLocationTracker.currentLocation.value
                val dispatch = _dispatchSettings.value
                val rates = _riderPaySettings.value

                if (currentRider == null || !currentRider.isOnline) {
                    _incomingOfferBundle.value = null
                    _incomingOffer.value = null
                    return@addSnapshotListener
                }

                // If driver currently has an active Taxi order, no other orders can be combined
                if (activeList.any { it.isTaxi }) {
                    _incomingOfferBundle.value = null
                    _incomingOffer.value = null
                    return@addSnapshotListener
                }

                // Priority orders are never pooled. Driver holding a Priority order receives no other offers
                // 1. Booking Exclusivity:
                // Only Food and Convenience orders can be combined in a batch (double booking).
                // Parcel, Pabili, Taxi, and Request a Rider are always single bookings:
                // such an order is offered only when I have no active orders, and while holding one, no other offers are generated.
                val hasNonFoodActive = activeList.any {
                    val s = it.serviceType.lowercase().trim()
                    s !in listOf("food", "convenience", "instant", OrderStepConstants.SERVICE_FOOD)
                }
                if (hasNonFoodActive) {
                    _incomingOfferBundle.value = null
                    _incomingOffer.value = null
                    return@addSnapshotListener
                }

                // If any order in the batch is Priority, it can never be pooled
                if (activeList.any { it.isPriority || it.badge.equals("priority", ignoreCase = true) }) {
                    _incomingOfferBundle.value = null
                    _incomingOffer.value = null
                    return@addSnapshotListener
                }

                // When every pickup is done the batch is closed until ALL its orders are delivered
                if (activeList.isNotEmpty() && !BatchConstants.isPickupPhaseOpen(activeList)) {
                    _incomingOfferBundle.value = null
                    _incomingOffer.value = null
                    return@addSnapshotListener
                }

                // Batch limit:
                // Priority rider: settings/dispatch.priorityMaxBatch (default 4, minimum 1) for Standard and Saver orders. Priority-option orders and all other services stay single (limit 1).
                // Normal rider: smallest maxBatch among all active orders (Standard and Saver allow 2 or more).
                val currentBatchLimit = if (currentRider.priorityRider) {
                    val anyPriority = activeList.any { it.isPriority || it.badge.equals("priority", ignoreCase = true) }
                    if (anyPriority) 1 else dispatch.priorityMaxBatch.coerceAtLeast(1)
                } else {
                    activeList.map { it.maxBatch.coerceAtLeast(2) }.minOrNull() ?: dispatch.maxBatch
                }
                if (activeList.size >= currentBatchLimit) {
                    _incomingOfferBundle.value = null
                    _incomingOffer.value = null
                    return@addSnapshotListener
                }

                // If driver holds a split child order, it is a single booking: no other offers
                if (activeList.any { it.isSplitChild || it.badge.equals("split", ignoreCase = true) }) {
                    _incomingOfferBundle.value = null
                    _incomingOffer.value = null
                    return@addSnapshotListener
                }

                // Family IDs of active orders to prevent sibling split delivery assignment
                val activeSplitFamilyIds = activeList.mapNotNull { active ->
                    val parentId = active.splitParentOrderId?.trim()
                    if (!parentId.isNullOrEmpty()) {
                        parentId
                    } else if (active.hasSplitOut) {
                        active.id
                    } else null
                }.toSet()

                val declinedIds = userPrefs.declinedOrderIds.value
                val releasedIds = _releasedOrderIds.value
                val uid = currentRider.uid

                // Filter unassigned candidate orders
                val candidateOrders = snapshots.documents.mapNotNull { parseOrder(it) }.filter { order ->
                    // Never offer again an order I released or declined
                    if (declinedIds.contains(order.id) || releasedIds.contains(order.id)) return@filter false

                    // Skip any whose excludeRiderIds contains my uid
                    if (order.excludeRiderIds.contains(uid)) return@filter false

                    // Never offer orders where fulfillment == "pickup" (filter in code)
                    if (order.isPickup) return@filter false

                    // Ignore any order whose scheduledFor is in the future (after release time: scheduledFor - settings/platform.scheduledReleaseLeadMinutes)
                    val leadMinutes = _scheduledReleaseLeadMinutes.value
                    if (order.isFutureScheduled(leadMinutes)) return@filter false

                    // Split child orders are strictly single bookings: cannot be added to an active batch
                    val isCandidateSplitChild = order.isSplitChild || order.badge.equals("split", ignoreCase = true)
                    if (isCandidateSplitChild && activeList.isNotEmpty()) return@filter false

                    // Sibling check: a rider who holds one split order cannot receive another sibling
                    val candidateFamilyId = if (!order.splitParentOrderId.isNullOrBlank()) {
                        order.splitParentOrderId!!.trim()
                    } else if (order.hasSplitOut) {
                        order.id
                    } else null

                    if (candidateFamilyId != null && activeSplitFamilyIds.contains(candidateFamilyId)) {
                        return@filter false
                    }

                    // While holding a Food or Convenience batch, I get no Parcel, Pabili, Taxi, or Request offers
                    val isFoodOrConv = order.serviceType.lowercase().trim() in listOf(
                        "food", "convenience", "instant", OrderStepConstants.SERVICE_FOOD
                    )
                    if (activeList.isNotEmpty() && !isFoodOrConv) return@filter false

                    // Check if service is switched off in city or platform (missing means on)
                    val checkCity = if (order.cityId.isNotBlank()) order.cityId else currentRider.cityId
                    if (!isServiceOn(order.serviceType, checkCity)) return@filter false

                    if (!currentRider.activeServices.contains(order.serviceType)) return@filter false
                    if (!currentRider.allowedServices.contains(order.serviceType)) return@filter false
                    if (order.cityId.isNotEmpty() && currentRider.cityId.isNotEmpty() && order.cityId != currentRider.cityId) {
                        return@filter false
                    }

                    val riderVehicleType = OrderStepConstants.normalizeVehicleType(currentRider.vehicle.type)
                    val orderVehicleType = OrderStepConstants.normalizeVehicleType(order.vehicleType)

                    // TAXI SPECIFIC CRITERIA:
                    // 1. driver has no other active order (a taxi trip cannot be combined with other orders)
                    // 2. orders.vehicleType == riders.vehicleType
                    // 3. pickup within 5 km
                    if (order.isTaxi) {
                        if (activeList.isNotEmpty()) return@filter false
                        if (orderVehicleType != riderVehicleType) return@filter false

                        val distKm = if (riderLoc != null && order.merchantLat != 0.0 && order.merchantLng != 0.0) {
                            BatchConstants.distanceKm(riderLoc.first, riderLoc.second, order.merchantLat, order.merchantLng)
                        } else order.pickupDistanceKm
                        if (distKm > 5.0) return@filter false

                        return@filter order.status in listOf(
                            OrderStepConstants.STATUS_PLACED_ORDER,
                            OrderStepConstants.STATUS_PREPARING,
                            OrderStepConstants.STATUS_READY
                        )
                    }

                    // Non-taxi orders: check vehicle suitability
                    val allowedForVehicle = OrderStepConstants.getServicesForVehicle(riderVehicleType)
                    if (!allowedForVehicle.contains(order.serviceType)) return@filter false

                    // Skip any beyond dispatch.searchRadiusKm from the pickup, UNLESS priority rider for Food/Convenience
                    val skipRadius = currentRider.priorityRider && isFoodOrConv
                    if (!skipRadius) {
                        val distKm = if (riderLoc != null && order.merchantLat != 0.0 && order.merchantLng != 0.0) {
                            BatchConstants.distanceKm(riderLoc.first, riderLoc.second, order.merchantLat, order.merchantLng)
                        } else order.pickupDistanceKm
                        if (distKm > dispatch.searchRadiusKm) return@filter false
                    }

                    // Readiness check:
                    if (order.isScheduled) {
                        order.merchantAccepted ||
                            order.status == OrderStepConstants.STATUS_PREPARING ||
                            order.status == OrderStepConstants.STATUS_READY ||
                            order.status.lowercase() in listOf("preparing", "ready") ||
                            order.status != OrderStepConstants.STATUS_PLACED_ORDER
                    } else {
                        when (order.serviceType.lowercase()) {
                            OrderStepConstants.SERVICE_FOOD, OrderStepConstants.SERVICE_INSTANT, "convenience", "instant" -> {
                                val s = order.status.trim().lowercase()
                                s in listOf("preparing", "ready") ||
                                    order.status == OrderStepConstants.STATUS_PREPARING ||
                                    order.status == OrderStepConstants.STATUS_READY ||
                                    (order.merchantAccepted && s != "placed order" && s != "placed_order")
                            }
                            OrderStepConstants.SERVICE_REQUEST -> {
                                order.riderRequestStatus == OrderStepConstants.REQ_SEARCHING
                            }
                            OrderStepConstants.SERVICE_PARCEL, OrderStepConstants.SERVICE_PABILI -> {
                                order.status == OrderStepConstants.STATUS_PLACED_ORDER
                            }
                            else -> false
                        }
                    }
                }

                var selectedBundle: OfferBundle? = null
                val now = System.currentTimeMillis()

                if (activeList.isEmpty()) {
                    // DRIVER IS IDLE:
                    // 1. Priority orders are offered as a single booking
                    val priorityCandidate = candidateOrders.firstOrNull { it.isPriority || it.badge.equals("priority", ignoreCase = true) }
                    if (priorityCandidate != null) {
                        selectedBundle = buildOfferBundle(OfferType.SINGLE, listOf(priorityCandidate), riderLoc, rates, dispatch, isPriorityRider = currentRider.priorityRider)
                    }

                    // 2. Poolable orders that are NOT holding (hold ended or Ready) are offered as a single booking
                    if (selectedBundle == null) {
                        val nonHoldingPoolable = candidateOrders.firstOrNull { order ->
                            BatchConstants.isPoolable(order, dispatch) && !BatchConstants.isHolding(order, dispatch, now)
                        }
                        if (nonHoldingPoolable != null) {
                            selectedBundle = buildOfferBundle(OfferType.SINGLE, listOf(nonHoldingPoolable), riderLoc, rates, dispatch, isPriorityRider = currentRider.priorityRider)
                        }
                    }

                    // 3. Holding poolable orders are offered ONLY as a "Double booking" bundle with a pairable partner
                    // (if three or more are pairable, bundle can grow up to the smallest maxBatch or priorityMaxBatch)
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
                                val maxLimit = if (currentRider.priorityRider) {
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

                    // 4. Non-poolable orders (Taxi, Parcel, Pabili, Request) offered as single booking
                    if (selectedBundle == null) {
                        val nonPoolable = candidateOrders.firstOrNull { !BatchConstants.isPoolable(it, dispatch) && !it.isPriority }
                        if (nonPoolable != null) {
                            selectedBundle = buildOfferBundle(OfferType.SINGLE, listOf(nonPoolable), riderLoc, rates, dispatch, isPriorityRider = currentRider.priorityRider)
                        }
                    }
                } else {
                    // DRIVER HAS ACTIVE BATCH AND PICKUP PHASE IS OPEN:
                    // Offer every order where canJoin(batch, order) is true as "Add to your booking"
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

                // Wave delay: floor(km from my position to the first pickup) * waveSeconds counted from when order/bundle became eligible
                if (selectedBundle != null) {
                    val waveDelaySec = selectedBundle.waveDelaySeconds
                    val elapsedSec = ((now - selectedBundle.eligibleSince) / 1000).coerceAtLeast(0)
                    if (elapsedSec >= waveDelaySec || waveDelaySec <= 0) {
                        _incomingOfferBundle.value = selectedBundle
                        _incomingOffer.value = selectedBundle.firstOrder
                    } else {
                        _incomingOfferBundle.value = null
                        _incomingOffer.value = null
                    }
                } else {
                    _incomingOfferBundle.value = null
                    _incomingOffer.value = null
                }
            }

        // 4. Delivered orders for earnings & wallet
        deliveredOrdersListener = db.collection("orders")
            .whereEqualTo("riderId", uid)
            .whereIn("status", listOf(OrderStepConstants.STATUS_DELIVERED, OrderStepConstants.STATUS_COMPLETED, "Delivered", "Completed").distinct())
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null) return@addSnapshotListener
                val orders = snapshots.documents.mapNotNull { parseOrder(it) }
                _deliveredOrders.value = orders

                // Check for late tip updates arriving while app is open
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

                // Check monthly tier progress
                checkTierProgression(uid, _rider.value?.name ?: "Rider", orders, userPrefs)
            }

        // 5. Tier configs
        tierConfigListener = db.collection("tierConfig")
            .orderBy("sortOrder", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null) return@addSnapshotListener
                val configs = snapshots.documents.mapNotNull { doc ->
                    TierConfig(
                        id = doc.id,
                        name = doc.getString("name") ?: doc.id,
                        ridesRequired = doc.getLong("ridesRequired")?.toInt() ?: 0,
                        coins = doc.getLong("coins")?.toInt() ?: 0,
                        sortOrder = doc.getLong("sortOrder")?.toInt() ?: 0
                    )
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
                    CoinTransaction(
                        id = doc.id,
                        title = doc.getString("title") ?: "Coin Credit",
                        amount = doc.getLong("amount")?.toInt() ?: 0,
                        timestampText = doc.getString("timestampText") ?: "Recent",
                        isCredit = doc.getBoolean("isCredit") ?: true
                    )
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
                    _dispatchSettings.value = DispatchSettings(
                        holdMinutes = holdMin,
                        maxBatch = maxB,
                        poolMaxPickupGapKm = pPickup,
                        poolMaxDropoffGapKm = pDrop,
                        searchRadiusKm = searchRad,
                        waveSeconds = waveSec,
                        specialRadiusRiderExtra = riderExtra,
                        priorityMaxBatch = pMaxBatch,
                        priorityHeadStartSeconds = pHeadStart
                    )
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
                    val ratingVal = doc.getDouble("ratingRider")
                        ?: (doc.getDouble("stars") ?: (doc.getLong("stars")?.toDouble() ?: 0.0))
                    val comment = doc.getString("comment") ?: doc.getString("review") ?: ""
                    val customerName = doc.getString("customerName") ?: doc.getString("userName") ?: "Customer"
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
        val firstDayOfMonth = cal.time

        val monthFormat = SimpleDateFormat("yyyy-MM", Locale.US)
        val monthKey = monthFormat.format(Date())

        val ridesThisMonth = delivered.count { order ->
            when (val delAt = order.deliveredAt) {
                is com.google.firebase.Timestamp -> delAt.toDate().after(firstDayOfMonth)
                is Date -> delAt.after(firstDayOfMonth)
                else -> true
            }
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
        if (!currentRider.allowedServices.contains(serviceKey)) {
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
                RiderLocationService.start(context)
            } else {
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
                // FOOD, CONVENIENCE (Instant), and REQUEST
                val isRequest = serviceType == OrderStepConstants.SERVICE_REQUEST || serviceType == "request"
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
            OrderStepConstants.SERVICE_FOOD, OrderStepConstants.SERVICE_INSTANT -> OrderStepConstants.STATUS_READY
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
    fun logOfferOutcome(orderId: String, serviceType: String, outcome: String) {
        val uid = auth.currentUser?.uid ?: _rider.value?.uid ?: return
        val docRef = db.collection("riders").document(uid).collection("offerLog").document(orderId)
        val data = mapOf(
            "orderId" to orderId,
            "serviceType" to serviceType,
            "outcome" to outcome,
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
    }

    fun missOffer(orderId: String, serviceType: String, userPrefs: UserPreferencesRepository) {
        userPrefs.addDeclinedOrderId(orderId)
        logOfferOutcome(orderId, serviceType, "missed")
        if (_incomingOffer.value?.id == orderId) {
            _incomingOffer.value = null
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
        currentChatId = null
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
        } catch (_: Exception) {
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
        } catch (_: Exception) {
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
        } catch (_: Exception) {
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
        } catch (_: Exception) {
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
    private fun parseOrder(doc: DocumentSnapshot): Order? {
        try {
            val id = doc.id
            val orderNumber = doc.getString("orderNumber") ?: doc.getString("orderId") ?: id.takeLast(6).uppercase()
            val serviceType = doc.getString("serviceType") ?: OrderStepConstants.SERVICE_FOOD
            val status = doc.getString("status") ?: OrderStepConstants.STATUS_PLACED_ORDER
            val riderStep = doc.getString("riderStep") ?: OrderStepConstants.STEP_HEADING_TO_MERCHANT
            val riderId = doc.getString("riderId")
            val riderName = doc.getString("riderName")
            val riderPhone = doc.getString("riderPhone")
            val riderPhotoUrl = doc.getString("riderPhotoUrl")
            val cityId = doc.getString("cityId") ?: ""

            val merchantId = doc.getString("merchantId") ?: ""

            val fulfillment = doc.getString("fulfillment") ?: doc.getString("fulfillmentType") ?: "delivery"
            val isScheduled = doc.getBoolean("isScheduled")
                ?: (doc.getString("deliveryType")?.equals("scheduled", ignoreCase = true) == true
                    || doc.getString("orderType")?.equals("scheduled", ignoreCase = true) == true
                    || doc.get("scheduledFor") != null
                    || doc.get("scheduledDeliveryAt") != null
                    || doc.getString("scheduleDate") != null)
            val scheduledForRaw = doc.get("scheduledFor")
                ?: doc.get("scheduledDeliveryAt")
                ?: doc.getString("scheduledTime")
                ?: doc.getString("scheduleDate")
            val scheduledDateText = formatScheduledDate(scheduledForRaw)
            val merchantAccepted = doc.getBoolean("merchantAccepted")
                ?: (status == OrderStepConstants.STATUS_PREPARING || status == OrderStepConstants.STATUS_READY)

            val prepMinutes = doc.getLong("prepMinutes")?.toInt()
                ?: (doc.get("prepMinutes") as? Number)?.toInt()
                ?: doc.getLong("preparationMinutes")?.toInt()
                ?: (doc.get("preparationMinutes") as? Number)?.toInt()
                ?: doc.getLong("estimatedPrepTime")?.toInt()
                ?: (doc.get("estimatedPrepTime") as? Number)?.toInt()
                ?: doc.getLong("prepTime")?.toInt()
                ?: (doc.get("prepTime") as? Number)?.toInt()
                ?: 0
            val editedAt = doc.get("editedAt") ?: doc.get("merchantEditedAt")

            // 1. Pickup location: orders.pickup.lat/lng or merchant.latitude/longitude
            val pickupMap = (doc.get("pickup") as? Map<*, *>) ?: (doc.get("merchant") as? Map<*, *>)
            val rawMerchantLat = (pickupMap?.get("lat") as? Number)?.toDouble()
                ?: (pickupMap?.get("latitude") as? Number)?.toDouble()
                ?: doc.getDouble("merchantLat") ?: 0.0
            val rawMerchantLng = (pickupMap?.get("lng") as? Number)?.toDouble()
                ?: (pickupMap?.get("longitude") as? Number)?.toDouble()
                ?: doc.getDouble("merchantLng") ?: 0.0
            val rawMerchantName = pickupMap?.get("name")?.toString()?.trim()?.takeIf { it.isNotBlank() }
                ?: doc.getString("merchantName")?.trim()?.takeIf { it.isNotBlank() }
                ?: doc.getString("senderName")?.trim()?.takeIf { it.isNotBlank() }
                ?: ""
            val rawBranchName = doc.getString("branchName")?.trim()?.takeIf { it.isNotBlank() }
                ?: pickupMap?.get("branchName")?.toString()?.trim()?.takeIf { it.isNotBlank() }
                ?: ""
            val rawBranchCode = doc.getString("branchCode")?.trim()?.takeIf { it.isNotBlank() }
                ?: pickupMap?.get("branchCode")?.toString()?.trim()?.takeIf { it.isNotBlank() }
                ?: ""
            val rawChainId = doc.getString("chainId")?.trim()?.takeIf { it.isNotBlank() }
                ?: pickupMap?.get("chainId")?.toString()?.trim()?.takeIf { it.isNotBlank() }
                ?: ""
            val rawMerchantAddress = pickupMap?.get("address")?.toString()
                ?: doc.getString("merchantAddress") ?: doc.getString("senderAddress") ?: ""
            val rawMerchantPhone = pickupMap?.get("phone")?.toString()
                ?: doc.getString("merchantPhone") ?: doc.getString("senderPhone") ?: ""

            val isTaxiOrder = serviceType.equals(OrderStepConstants.SERVICE_TAXI, ignoreCase = true)
            val cachedMerchant = if (merchantId.isNotBlank() && !isTaxiOrder) merchantCache[merchantId] else null

            val merchantLat = if (rawMerchantLat != 0.0) rawMerchantLat else (cachedMerchant?.get("lat") as? Double ?: 0.0)
            val merchantLng = if (rawMerchantLng != 0.0) rawMerchantLng else (cachedMerchant?.get("lng") as? Double ?: 0.0)
            val merchantName = when {
                rawMerchantName.isNotBlank() && !rawMerchantName.equals("Merchant", ignoreCase = true) -> rawMerchantName
                else -> (cachedMerchant?.get("name") as? String)?.takeIf { it.isNotBlank() } ?: rawMerchantName.ifBlank { "Merchant" }
            }
            val branchName = if (rawBranchName.isNotBlank() || rawBranchCode.isNotBlank()) {
                rawBranchName
            } else {
                cachedMerchant?.get("branchName") as? String ?: ""
            }
            val branchCode = if (rawBranchName.isNotBlank() || rawBranchCode.isNotBlank()) {
                rawBranchCode
            } else {
                cachedMerchant?.get("branchCode") as? String ?: ""
            }
            val chainId = if (rawChainId.isNotBlank()) {
                rawChainId
            } else {
                cachedMerchant?.get("chainId") as? String ?: ""
            }
            val merchantAddress = rawMerchantAddress.ifBlank { cachedMerchant?.get("address") as? String ?: "" }
            val merchantPhone = rawMerchantPhone.ifBlank { cachedMerchant?.get("phone") as? String ?: "" }

            // Asynchronously fall back to merchants/{shopId} when lat/lng or outlet branch snapshots are missing
            val needsMerchantFallback = !isTaxiOrder && merchantId.isNotBlank() && (
                merchantLat == 0.0 ||
                (rawBranchName.isBlank() && rawBranchCode.isBlank()) ||
                rawMerchantName.isBlank() ||
                rawMerchantName.equals("Merchant", ignoreCase = true)
            )
            if (needsMerchantFallback && cachedMerchant == null) {
                fetchMerchantInfo(merchantId)
            }

            // 2. Dropoff location: orders.dropoff.lat/lng or customer.latitude/longitude
            val dropoffMap = (doc.get("dropoff") as? Map<*, *>)
                ?: (doc.get("dropOff") as? Map<*, *>)
                ?: (doc.get("customer") as? Map<*, *>)
            val customerLat = (dropoffMap?.get("lat") as? Number)?.toDouble()
                ?: (dropoffMap?.get("latitude") as? Number)?.toDouble()
                ?: doc.getDouble("customerLat") ?: 0.0
            val customerLng = (dropoffMap?.get("lng") as? Number)?.toDouble()
                ?: (dropoffMap?.get("longitude") as? Number)?.toDouble()
                ?: doc.getDouble("customerLng") ?: 0.0
            val customerId = dropoffMap?.get("customerId")?.toString()
                ?: dropoffMap?.get("uid")?.toString()
                ?: doc.getString("customerId") ?: ""
            val customerName = dropoffMap?.get("name")?.toString()
                ?: doc.getString("customerName") ?: doc.getString("recipientName") ?: "Customer"
            val customerAddress = dropoffMap?.get("address")?.toString()
                ?: doc.getString("customerAddress") ?: doc.getString("recipientAddress") ?: ""
            val customerPhone = dropoffMap?.get("phone")?.toString()
                ?: doc.getString("customerPhone") ?: doc.getString("recipientPhone") ?: ""
            val customerArea = dropoffMap?.get("area")?.toString()
                ?: doc.getString("customerArea") ?: doc.getString("recipientArea") ?: "Downtown"
            val customerNotes = dropoffMap?.get("notes")?.toString()
                ?: doc.getString("customerNotes") ?: ""

            val itemsRaw = doc.get("items") as? List<*>
            val items = itemsRaw?.mapNotNull { itemObj ->
                val m = itemObj as? Map<*, *> ?: return@mapNotNull null
                val selectionsRaw = m["selections"] ?: m["options"] ?: m["modifiers"]
                val selectionsList = mutableListOf<String>()
                when (selectionsRaw) {
                    is List<*> -> {
                        selectionsRaw.forEach { sel ->
                            when (sel) {
                                is String -> {
                                    val cleaned = sel.replace("()", "").trim()
                                    if (cleaned.isNotBlank()) selectionsList.add(cleaned)
                                }
                                is Map<*, *> -> {
                                    val selName = sel["name"]?.toString()
                                        ?: sel["title"]?.toString()
                                        ?: sel["optionName"]?.toString()
                                        ?: sel["value"]?.toString()
                                        ?: ""
                                    val cleaned = selName.replace("()", "").trim()
                                    if (cleaned.isNotBlank()) selectionsList.add(cleaned)
                                }
                            }
                        }
                    }
                    is Map<*, *> -> {
                        selectionsRaw.values.forEach { v ->
                            when (v) {
                                is String -> {
                                    val cleaned = v.replace("()", "").trim()
                                    if (cleaned.isNotBlank()) selectionsList.add(cleaned)
                                }
                                is List<*> -> {
                                    v.forEach { sub ->
                                        val subStr = when (sub) {
                                            is String -> sub
                                            is Map<*, *> -> sub["name"]?.toString() ?: sub["title"]?.toString() ?: ""
                                            else -> sub?.toString() ?: ""
                                        }
                                        val cleaned = subStr.replace("()", "").trim()
                                        if (cleaned.isNotBlank()) selectionsList.add(cleaned)
                                    }
                                }
                            }
                        }
                    }
                    is String -> {
                        if (selectionsRaw.isNotBlank()) {
                            selectionsRaw.split(",", ";").forEach { s ->
                                val cleaned = s.replace("()", "").trim()
                                if (cleaned.isNotBlank()) selectionsList.add(cleaned)
                            }
                        }
                    }
                }

                OrderItem(
                    name = m["name"]?.toString() ?: m["title"]?.toString() ?: "",
                    quantity = (m["quantity"] as? Number)?.toInt() ?: 1,
                    price = (m["price"] as? Number)?.toDouble() ?: 0.0,
                    specialInstructions = m["specialInstructions"]?.toString() ?: "",
                    selections = selectionsList
                )
            } ?: emptyList()

            val baseFare = doc.getDouble("baseFare") ?: 50.0
            val distanceFare = doc.getDouble("distanceFare") ?: 30.0
            val feesMap = doc.get("fees") as? Map<*, *>
            val feesTip = (feesMap?.get("tip") as? Number)?.toDouble() ?: doc.getDouble("tip") ?: 0.0
            val tip = feesTip
            val surgeBonus = doc.getDouble("surgeBonus") ?: 0.0
            val feesDeliveryFee = (feesMap?.get("deliveryFee") as? Number)?.toDouble() ?: doc.getDouble("deliveryFee") ?: (baseFare + distanceFare + surgeBonus)
            val feesServiceFee = (feesMap?.get("serviceFee") as? Number)?.toDouble() ?: doc.getDouble("serviceFee") ?: 0.0
            val feesTotal = (feesMap?.get("total") as? Number)?.toDouble()
                ?: doc.getDouble("totalAmount")
                ?: doc.getDouble("totalFee")
                ?: (feesDeliveryFee + feesServiceFee + tip)

            // 1. READ PAYMENT FROM THE ORDER (never default to "Paid Online")
            // Read orders.paymentMethod, trimmed and lower-cased. "cod", "cash", "cash on delivery" = COD. "wallet" or "online" = Paid Online. If missing or unknown, show "Payment unknown - contact support"
            val paymentMethod = doc.getString("paymentMethod")
                ?: doc.getString("paymentType")
                ?: ""
            val paymentProvider = doc.getString("paymentProvider") ?: ""
            val cashToCollect = doc.getDouble("cashToCollect")
                ?: doc.getDouble("totalAmountToCollect")
                ?: 0.0
            val cashCollectAt = doc.getString("cashCollectAt") ?: ""
            val codAmount = doc.getDouble("codAmount") ?: 0.0
            val codCollected = doc.getDouble("codCollected") ?: 0.0

            val parcelMap = doc.get("parcel") as? Map<*, *>
            val parcelSize = parcelMap?.get("size")?.toString() ?: doc.getString("parcelSize") ?: "M"
            val parcelWeightKg = (parcelMap?.get("weightKg") as? Number)?.toDouble() ?: doc.getDouble("parcelWeightKg") ?: 1.0
            val parcelItemDesc = parcelMap?.get("itemDescription")?.toString() ?: doc.getString("parcelItemDescription") ?: ""
            val parcelFragile = parcelMap?.get("fragile") as? Boolean ?: doc.getBoolean("parcelFragile") ?: false
            val parcelFeePayer = parcelMap?.get("feePayer")?.toString() ?: doc.getString("parcelFeePayer") ?: "SENDER"

            val pabiliMap = doc.get("pabili") as? Map<*, *>
            val pabiliItemsRaw = pabiliMap?.get("items") as? List<*> ?: doc.get("pabiliItems") as? List<*>
            val pabiliItems = pabiliItemsRaw?.mapNotNull { itm ->
                val m = itm as? Map<*, *> ?: return@mapNotNull null
                PabiliItem(
                    product = m["product"]?.toString() ?: m["name"]?.toString() ?: "",
                    quantity = (m["quantity"] as? Number)?.toInt() ?: 1,
                    estimatedPrice = (m["estimatedPrice"] as? Number)?.toDouble() ?: 0.0
                )
            } ?: emptyList()
            val pabiliEstimatedTotal = (pabiliMap?.get("estimatedTotal") as? Number)?.toDouble() ?: doc.getDouble("pabiliEstimatedTotal") ?: 0.0
            val pabiliBudget = (pabiliMap?.get("budget") as? Number)?.toDouble() ?: doc.getDouble("pabiliBudget") ?: 0.0
            val finalReceiptTotal = doc.getDouble("finalReceiptTotal") ?: 0.0

            val riderReqStatus = (doc.get("riderRequest") as? Map<*, *>)?.get("status")?.toString() ?: doc.getString("riderRequestStatus") ?: ""

            val vehicleType = doc.getString("vehicleType") ?: "motorcycle"
            val stopsRaw = doc.get("stops") as? List<*>
            val taxiStops = stopsRaw?.mapIndexedNotNull { index, obj ->
                val m = obj as? Map<*, *> ?: return@mapIndexedNotNull null
                TaxiStop(
                    stopNumber = (m["stopNumber"] as? Number)?.toInt() ?: (index + 3),
                    address = m["address"]?.toString() ?: "",
                    area = m["area"]?.toString() ?: "",
                    lat = (m["lat"] as? Number)?.toDouble() ?: 0.0,
                    lng = (m["lng"] as? Number)?.toDouble() ?: 0.0
                )
            } ?: emptyList()
            val completedStopsRaw = (doc.get("completedStops") as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()

            val lateTipMap = doc.get("lateTip") as? Map<*, *>
            val lateTipAmount = (lateTipMap?.get("amount") as? Number)?.toDouble()
                ?: doc.getDouble("lateTip.amount")
                ?: doc.getDouble("lateTipAmount")
                ?: 0.0
            val lateTipAt = lateTipMap?.get("createdAt") ?: lateTipMap?.get("at") ?: doc.get("lateTipAt")

            val badge = doc.getString("badge") ?: "Standard"
            val splitParentOrderId = doc.getString("splitParentOrderId")
            val isSplitChild = doc.getBoolean("isSplitChild")
                ?: (badge.equals("split", ignoreCase = true) || !splitParentOrderId.isNullOrBlank())
            val finalBadge = if (isSplitChild) "SPLIT" else badge
            val splitOutRaw = doc.get("splitOut") ?: doc.get("splitItems")
            val splitOutInfo = when (splitOutRaw) {
                is Map<*, *> -> {
                    val count = (splitOutRaw["count"] as? Number)?.toInt() ?: 0
                    val rawChildren = (splitOutRaw["children"] as? List<*>) ?: emptyList<Any>()
                    val children = rawChildren.mapNotNull { child ->
                        val childMap = child as? Map<*, *> ?: return@mapNotNull null
                        val ordId = childMap["orderId"]?.toString() ?: ""
                        val rawItems = (childMap["items"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                        val seq = (childMap["seq"] as? Number)?.toInt() ?: 1
                        SplitChildItem(orderId = ordId, items = rawItems, seq = seq)
                    }
                    SplitOutInfo(
                        count = count,
                        children = children,
                        at = splitOutRaw["at"],
                        by = splitOutRaw["by"]?.toString() ?: ""
                    )
                }
                else -> null
            }
            val splitOut = when (splitOutRaw) {
                is Map<*, *> -> splitOutInfo?.children?.flatMap { it.items } ?: emptyList()
                is List<*> -> splitOutRaw.mapNotNull { it?.toString() }
                is String -> if (splitOutRaw.isNotBlank()) listOf(splitOutRaw) else emptyList()
                else -> emptyList()
            }
            val isPriority = doc.getBoolean("isPriority")
                ?: (badge.equals("priority", ignoreCase = true) || doc.getString("priority")?.equals("high", ignoreCase = true) == true)
            val dispatchDocMap = doc.get("dispatch") as? Map<*, *>
            val isPoolable = !isSplitChild && ((dispatchDocMap?.get("poolable") as? Boolean)
                ?: doc.getBoolean("poolable")
                ?: doc.getBoolean("dispatch.poolable")
                ?: (!isPriority && serviceType.lowercase() in listOf("food", "convenience", "instant", OrderStepConstants.SERVICE_FOOD)))
            val maxBatch = (dispatchDocMap?.get("maxBatch") as? Number)?.toInt()
                ?: doc.getLong("maxBatch")?.toInt()
                ?: 2
            val holdMinutesOrder = (dispatchDocMap?.get("holdMinutes") as? Number)?.toDouble()
                ?: doc.getDouble("holdMinutes")
                ?: (doc.get("holdMinutes") as? Number)?.toDouble()
                ?: (if (finalBadge.equals("saver", ignoreCase = true) || finalBadge.equals("standard", ignoreCase = true)) 5.0 else 0.0)
            val endsWhenReadyOrder = (dispatchDocMap?.get("endsWhenReady") as? Boolean)
                ?: doc.getBoolean("endsWhenReady")
                ?: doc.getBoolean("dispatch.endsWhenReady")
            val specialExtra = ((dispatchDocMap?.get("specialRadius") as? Map<*, *>)?.get("riderExtra") as? Number)?.toDouble()
                ?: doc.getDouble("specialRadiusExtra")
                ?: 0.0

            val acceptedAtRaw = doc.get("acceptedAt") ?: doc.get("placedAt") ?: doc.get("createdAt")
            val acceptedAtMillis = when (acceptedAtRaw) {
                is com.google.firebase.Timestamp -> acceptedAtRaw.toDate().time
                is Date -> acceptedAtRaw.time
                is Number -> acceptedAtRaw.toLong()
                else -> System.currentTimeMillis()
            }

            val leadMinutes = _scheduledReleaseLeadMinutes.value
            val schedMillis = when (scheduledForRaw) {
                is com.google.firebase.Timestamp -> scheduledForRaw.toDate().time
                is Date -> scheduledForRaw.time
                is Number -> {
                    val num = scheduledForRaw.toLong()
                    if (num < 1000000000000L) num * 1000L else num
                }
                is String -> {
                    val trimmed = scheduledForRaw.trim()
                    val num = trimmed.toLongOrNull()
                    if (num != null) {
                        if (num < 1000000000000L) num * 1000L else num
                    } else {
                        val patterns = listOf(
                            "yyyy-MM-dd'T'HH:mm:ss",
                            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                            "yyyy-MM-dd HH:mm:ss",
                            "yyyy-MM-dd",
                            "MMM d, yyyy h:mm a",
                            "MMM d, h:mm a"
                        )
                        var parsed = 0L
                        for (pat in patterns) {
                            try {
                                val sdf = SimpleDateFormat(pat, Locale.US)
                                val d = sdf.parse(trimmed)
                                if (d != null) {
                                    parsed = d.time
                                    break
                                }
                            } catch (_: Exception) {}
                        }
                        parsed
                    }
                }
                else -> 0L
            }
            val releaseTimeMillis = if (isScheduled && schedMillis > 0L) {
                (schedMillis - (leadMinutes * 60 * 1000).toLong()).coerceAtLeast(0L)
            } else 0L

            val baseHoldStartMillis = if (isScheduled && releaseTimeMillis > 0L) {
                releaseTimeMillis
            } else {
                acceptedAtMillis
            }

            val explicitHoldUntil = doc.get("holdUntil") ?: doc.get("holdUntilMillis")
            val explicitHoldUntilMillis = when (explicitHoldUntil) {
                is com.google.firebase.Timestamp -> explicitHoldUntil.toDate().time
                is Date -> explicitHoldUntil.time
                is Number -> explicitHoldUntil.toLong()
                else -> 0L
            }
            val holdUntilMillis = when {
                explicitHoldUntilMillis > 0L && !isScheduled -> explicitHoldUntilMillis
                holdMinutesOrder > 0.0 && baseHoldStartMillis > 0L -> baseHoldStartMillis + (holdMinutesOrder * 60 * 1000).toLong()
                holdMinutesOrder > 0.0 -> System.currentTimeMillis() + (holdMinutesOrder * 60 * 1000).toLong()
                else -> 0L
            }

            val excludeRiderIds = (doc.get("excludeRiderIds") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
            val riderFeeFixed = doc.getDouble("riderFeeFixed") ?: (doc.get("riderFeeFixed") as? Number)?.toDouble()
            val riderFeeEstimate = doc.getDouble("riderFeeEstimate") ?: 0.0
            val batchId = doc.getString("batchId")
            val batchIndex = doc.getLong("batchIndex")?.toInt() ?: 1

            val earningMap = doc.get("riderEarning") as? Map<*, *>
            val riderEarning = if (earningMap != null) {
                RiderEarning(
                    bookingFee = (earningMap["bookingFee"] as? Number)?.toDouble() ?: 0.0,
                    pickupFee = (earningMap["pickupFee"] as? Number)?.toDouble() ?: 0.0,
                    extra = (earningMap["extra"] as? Number)?.toDouble() ?: 0.0,
                    tip = (earningMap["tip"] as? Number)?.toDouble() ?: 0.0,
                    total = (earningMap["total"] as? Number)?.toDouble() ?: 0.0,
                    rank = (earningMap["rank"] as? Number)?.toInt() ?: 1,
                    batchId = earningMap["batchId"]?.toString() ?: ""
                )
            } else null

            return Order(
                id = id,
                orderNumber = orderNumber,
                serviceType = serviceType,
                status = status,
                riderStep = riderStep,
                riderId = riderId,
                riderName = riderName,
                riderPhone = riderPhone,
                riderPhotoUrl = riderPhotoUrl,
                cityId = cityId,
                merchantId = merchantId,
                vehicleType = vehicleType,
                stops = taxiStops,
                completedStops = completedStopsRaw,
                fulfillment = fulfillment,
                isScheduled = isScheduled,
                scheduledFor = scheduledForRaw,
                scheduledDateText = scheduledDateText,
                merchantAccepted = merchantAccepted,
                prepMinutes = prepMinutes,
                editedAt = editedAt,
                merchantName = merchantName,
                branchName = branchName,
                branchCode = branchCode,
                chainId = chainId,
                merchantAddress = merchantAddress,
                merchantPhone = merchantPhone,
                merchantLat = merchantLat,
                merchantLng = merchantLng,
                customerId = customerId,
                customerName = customerName,
                customerPhone = customerPhone,
                customerAddress = customerAddress,
                customerArea = customerArea,
                customerLat = customerLat,
                customerLng = customerLng,
                customerNotes = customerNotes,
                items = items,
                baseFare = baseFare,
                distanceFare = distanceFare,
                tip = tip,
                surgeBonus = surgeBonus,
                deliveryFee = feesDeliveryFee,
                paymentMethod = paymentMethod,
                paymentProvider = paymentProvider,
                cashToCollect = if (isSplitChild) 0.0 else cashToCollect,
                cashCollectAt = cashCollectAt,
                codAmount = if (isSplitChild) 0.0 else codAmount,
                feesTotal = feesTotal,
                feesDeliveryFee = feesDeliveryFee,
                feesServiceFee = feesServiceFee,
                codCollected = codCollected,
                parcelSize = parcelSize,
                parcelWeightKg = parcelWeightKg,
                parcelItemDescription = parcelItemDesc,
                parcelFragile = parcelFragile,
                parcelFeePayer = parcelFeePayer,
                pabiliItems = pabiliItems,
                pabiliEstimatedTotal = pabiliEstimatedTotal,
                pabiliBudget = pabiliBudget,
                finalReceiptTotal = finalReceiptTotal,
                riderRequestStatus = riderReqStatus,
                pickupDistanceKm = doc.getDouble("pickupDistanceKm") ?: 1.5,
                deliveryDistanceKm = doc.getDouble("deliveryDistanceKm") ?: 3.2,
                placedAt = doc.getString("placedAt") ?: "Recently",
                pickedUpAt = doc.get("pickedUpAt"),
                arrivedMerchantAt = doc.get("arrivedMerchantAt"),
                arrivedCustomerAt = doc.get("arrivedCustomerAt"),
                deliveredAt = doc.get("deliveredAt"),
                updatedAt = doc.get("updatedAt") ?: doc.get("statusUpdatedAt"),
                statusHistory = (doc.get("statusHistory") as? List<*>)?.mapNotNull { it as? Map<String, Any?> } ?: emptyList(),
                releaseReason = doc.getString("releaseReason"),
                lateTipAmount = lateTipAmount,
                lateTipAt = lateTipAt,
                batchId = batchId,
                batchIndex = batchIndex,
                riderFeeEstimate = riderFeeEstimate,
                riderFeeFixed = riderFeeFixed,
                badge = finalBadge,
                isPriority = isPriority,
                isPoolable = isPoolable,
                maxBatch = maxBatch,
                holdMinutes = holdMinutesOrder,
                acceptedAtMillis = baseHoldStartMillis,
                holdUntilMillis = holdUntilMillis,
                endsWhenReady = endsWhenReadyOrder,
                specialRadiusExtra = specialExtra,
                excludeRiderIds = excludeRiderIds,
                riderEarning = riderEarning,
                splitOut = splitOut,
                splitOutInfo = splitOutInfo,
                splitParentOrderId = splitParentOrderId,
                isSplitChild = isSplitChild
            )
        } catch (_: Exception) {
            return null
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
        } catch (_: Exception) {
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
            if (o.merchantId != merchantId || o.isTaxi) return o
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
        if (currentActive.any { it.merchantId == merchantId && !it.isTaxi }) {
            _activeOrders.value = currentActive.map { mergeOrder(it) }
        }

        val currentDelivered = _deliveredOrders.value
        if (currentDelivered.any { it.merchantId == merchantId && !it.isTaxi }) {
            _deliveredOrders.value = currentDelivered.map { mergeOrder(it) }
        }

        _incomingOfferBundle.value?.let { bundle ->
            if (bundle.orders.any { it.merchantId == merchantId && !it.isTaxi }) {
                val updatedOrders = bundle.orders.map { mergeOrder(it) }
                _incomingOfferBundle.value = bundle.copy(orders = updatedOrders)
                _incomingOffer.value = updatedOrders.firstOrNull()
            }
        }
        _incomingOffer.value?.let { offer ->
            if (offer.merchantId == merchantId && !offer.isTaxi && _incomingOfferBundle.value == null) {
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
        } catch (_: Exception) {
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
        } catch (_: Exception) {
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
