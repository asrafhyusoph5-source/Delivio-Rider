package com.example.data.repository

import android.content.Context
import com.example.data.constants.OrderStepConstants
import com.example.data.models.*
import com.example.data.service.RiderLocationService
import com.example.data.service.RiderLocationTracker
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

    private val _listenerError = MutableStateFlow<String?>(null)
    val listenerError: StateFlow<String?> = _listenerError.asStateFlow()

    fun clearListenerError() {
        _listenerError.value = null
    }

    // Listener registrations
    private var riderListener: ListenerRegistration? = null
    private var linkedUserListener: ListenerRegistration? = null
    private var customerTransactionsListener: ListenerRegistration? = null
    private var currentLinkedCustomerUid: String? = null
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
                    emergencyContactPhone = snapshot.getString("emergencyContactPhone") ?: ""
                )
                _rider.value = r
                _coins.value = r.coins

                syncLinkedCustomer(linkedCustomerUid)
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

        // 3. Listen to incoming offers: riderId == null and status in ["Placed Order", "Preparing", "Ready", "preparing", "ready"]
        offersListener = db.collection("orders")
            .whereEqualTo("riderId", null)
            .whereIn("status", listOf(
                OrderStepConstants.STATUS_PLACED_ORDER,
                OrderStepConstants.STATUS_PREPARING,
                OrderStepConstants.STATUS_READY,
                "preparing",
                "ready"
            ))
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null) return@addSnapshotListener
                val currentRider = _rider.value
                val activeCount = _activeOrders.value.size
                val riderLoc = RiderLocationTracker.currentLocation.value

                if (currentRider == null || !currentRider.isOnline || activeCount >= 2) {
                    _incomingOffer.value = null
                    return@addSnapshotListener
                }

                // If driver currently has an active Taxi order, no other orders can be combined
                if (_activeOrders.value.any { it.isTaxi }) {
                    _incomingOffer.value = null
                    return@addSnapshotListener
                }

                val declinedIds = userPrefs.declinedOrderIds.value

                // Find candidate offer
                val eligibleOffer = snapshots.documents.mapNotNull { parseOrder(it) }.firstOrNull { order ->
                    if (declinedIds.contains(order.id)) return@firstOrNull false

                    // Never offer orders where fulfillment == "pickup" (filter in code)
                    if (order.isPickup) return@firstOrNull false

                    if (!currentRider.activeServices.contains(order.serviceType)) return@firstOrNull false
                    if (!currentRider.allowedServices.contains(order.serviceType)) return@firstOrNull false
                    if (order.cityId.isNotEmpty() && currentRider.cityId.isNotEmpty() && order.cityId != currentRider.cityId) {
                        return@firstOrNull false
                    }

                    val riderVehicleType = OrderStepConstants.normalizeVehicleType(currentRider.vehicle.type)
                    val orderVehicleType = OrderStepConstants.normalizeVehicleType(order.vehicleType)

                    // TAXI SPECIFIC CRITERIA:
                    // 1. driver has no other active order (a taxi trip cannot be combined with other orders)
                    // 2. orders.vehicleType == riders.vehicleType
                    // 3. pickup within 5 km
                    if (order.isTaxi) {
                        if (activeCount > 0) return@firstOrNull false
                        if (orderVehicleType != riderVehicleType) return@firstOrNull false

                        if (riderLoc != null && order.merchantLat != 0.0 && order.merchantLng != 0.0) {
                            val distKm = calculateDistanceKm(riderLoc.first, riderLoc.second, order.merchantLat, order.merchantLng)
                            if (distKm > 5.0) return@firstOrNull false
                        } else if (order.pickupDistanceKm > 5.0) {
                            return@firstOrNull false
                        }

                        // Readiness check for Taxi
                        return@firstOrNull order.status in listOf(
                            OrderStepConstants.STATUS_PLACED_ORDER,
                            OrderStepConstants.STATUS_PREPARING,
                            OrderStepConstants.STATUS_READY
                        )
                    }

                    // Non-taxi orders:
                    // If driver vehicle cannot service this order type according to SERVICES_BY_VEHICLE
                    val allowedForVehicle = OrderStepConstants.getServicesForVehicle(riderVehicleType)
                    if (!allowedForVehicle.contains(order.serviceType)) return@firstOrNull false

                    // Check pickup distance within 8 km if coordinates available
                    if (riderLoc != null && order.merchantLat != 0.0 && order.merchantLng != 0.0) {
                        val distKm = calculateDistanceKm(riderLoc.first, riderLoc.second, order.merchantLat, order.merchantLng)
                        if (distKm > 8.0) return@firstOrNull false
                    }

                    // Readiness check:
                    // Scheduled-delivery orders are offered normally once the merchant accepts
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

                _incomingOffer.value = eligibleOffer
            }

        // 4. Delivered orders for earnings & wallet
        deliveredOrdersListener = db.collection("orders")
            .whereEqualTo("riderId", uid)
            .whereEqualTo("status", OrderStepConstants.STATUS_DELIVERED)
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null) return@addSnapshotListener
                val orders = snapshots.documents.mapNotNull { parseOrder(it) }
                _deliveredOrders.value = orders

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

        // 10. Platform settings for support phone
        db.collection("settings").document("platform").get()
            .addOnSuccessListener { doc ->
                _supportPhone.value = doc.getString("supportPhone")
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
    }

    private fun mergeInbox() {
        val all = (inboxDirectList + inboxAllList)
            .distinctBy { it.id }
            .map { it.copy(isRead = readMessageIds.contains(it.id)) }
            .sortedByDescending { it.id } // Newest first
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

    // ACCEPT ORDER (Transaction)
    fun acceptOrder(
        orderId: String,
        onSuccess: () -> Unit,
        onError: (AcceptOrderErrorDetails) -> Unit
    ) {
        val currentRider = _rider.value
        val authUid = auth.currentUser?.uid ?: currentRider?.uid ?: ""
        var fetchedStatus = _incomingOffer.value?.status ?: "unknown"

        if (currentRider == null || authUid.isEmpty()) {
            onError(
                AcceptOrderErrorDetails(
                    exceptionMessage = "Rider profile or FirebaseAuth session is not ready",
                    orderId = orderId,
                    currentStatus = fetchedStatus,
                    authUid = authUid,
                    fieldsWritten = emptyMap()
                )
            )
            return
        }

        if (_activeOrders.value.any { it.isTaxi }) {
            onError(
                AcceptOrderErrorDetails(
                    exceptionMessage = "A taxi trip cannot be combined with other orders",
                    orderId = orderId,
                    currentStatus = fetchedStatus,
                    authUid = authUid,
                    fieldsWritten = emptyMap()
                )
            )
            return
        }

        val targetService = _incomingOffer.value?.serviceType?.lowercase() ?: OrderStepConstants.SERVICE_FOOD
        if (targetService == OrderStepConstants.SERVICE_TAXI && _activeOrders.value.isNotEmpty()) {
            onError(
                AcceptOrderErrorDetails(
                    exceptionMessage = "A taxi trip cannot be combined with any active orders",
                    orderId = orderId,
                    currentStatus = fetchedStatus,
                    authUid = authUid,
                    fieldsWritten = emptyMap()
                )
            )
            return
        }

        if (_activeOrders.value.size >= 2) {
            onError(
                AcceptOrderErrorDetails(
                    exceptionMessage = "Cannot hold more than 2 active orders",
                    orderId = orderId,
                    currentStatus = fetchedStatus,
                    authUid = authUid,
                    fieldsWritten = emptyMap()
                )
            )
            return
        }

        val initialStep = when (targetService) {
            OrderStepConstants.SERVICE_TAXI -> OrderStepConstants.STEP_TO_PICKUP
            OrderStepConstants.SERVICE_PARCEL -> OrderStepConstants.STEP_TO_SENDER
            OrderStepConstants.SERVICE_PABILI -> OrderStepConstants.STEP_TO_VENDOR
            else -> OrderStepConstants.STEP_TO_MERCHANT
        }

        val orderRef = db.collection("orders").document(orderId)
        var writeMap: Map<String, Any?> = mapOf(
            "riderId" to authUid,
            "riderName" to currentRider.name,
            "riderPhone" to currentRider.phone,
            "riderPhotoUrl" to currentRider.photoUrl,
            "riderStep" to initialStep,
            "statusHistory" to "[arrayUnion(status=$fetchedStatus, timestamp=Timestamp.now())]",
            "updatedAt" to "FieldValue.serverTimestamp()"
        )

        db.runTransaction { tx ->
            val snapshot = tx.get(orderRef)
            val currentStatus = snapshot.getString("status") ?: OrderStepConstants.STATUS_PLACED_ORDER
            fetchedStatus = currentStatus

            val existingRider = snapshot.getString("riderId")
            if (existingRider != null) {
                throw FirebaseFirestoreException(
                    "Order already taken by rider: $existingRider",
                    FirebaseFirestoreException.Code.ABORTED
                )
            }

            val serviceType = snapshot.getString("serviceType") ?: OrderStepConstants.SERVICE_FOOD
            val stepForOrder = when (serviceType.lowercase()) {
                OrderStepConstants.SERVICE_TAXI -> OrderStepConstants.STEP_TO_PICKUP
                OrderStepConstants.SERVICE_PARCEL -> OrderStepConstants.STEP_TO_SENDER
                OrderStepConstants.SERVICE_PABILI -> OrderStepConstants.STEP_TO_VENDOR
                else -> OrderStepConstants.STEP_TO_MERCHANT
            }

            val updates = mutableMapOf<String, Any>()
            // riderId (must be the FirebaseAuth uid, never the riderCode)
            updates["riderId"] = authUid
            // riderName
            updates["riderName"] = currentRider.name
            // riderPhone
            updates["riderPhone"] = currentRider.phone
            // riderPhotoUrl
            updates["riderPhotoUrl"] = currentRider.photoUrl
            // riderStep (first stage is to_pickup, to_merchant, to_sender, or to_vendor)
            updates["riderStep"] = stepForOrder

            // riderVehicle: {type, plate, model, color}
            updates["riderVehicle"] = mapOf(
                "type" to currentRider.vehicle.type,
                "plate" to currentRider.vehicle.plateNumber,
                "model" to currentRider.vehicle.model,
                "color" to currentRider.vehicle.color
            )

            // riderRequest
            val isRequestOrder = serviceType.equals(OrderStepConstants.SERVICE_REQUEST, ignoreCase = true) ||
                    serviceType.equals("request", ignoreCase = true) ||
                    serviceType.equals(OrderStepConstants.SERVICE_TAXI, ignoreCase = true) ||
                    snapshot.contains("riderRequest")
            if (isRequestOrder) {
                val existingReq = (snapshot.get("riderRequest") as? Map<*, *>)?.mapKeys { it.key.toString() }?.toMutableMap() ?: mutableMapOf()
                existingReq["status"] = OrderStepConstants.REQ_RIDER_ASSIGNED
                updates["riderRequest"] = existingReq
            }

            // status:
            // For taxi: "Driver is on the way"
            // For parcel: "Rider is on the way to sender"
            // For pabili: "Rider is on the way to vendor"
            // For food, convenience, or request orders: never change status
            val newStatusForHistory: String
            when (serviceType.lowercase()) {
                OrderStepConstants.SERVICE_TAXI -> {
                    val taxiStatus = OrderStepConstants.STATUS_DRIVER_ON_WAY
                    updates["status"] = taxiStatus
                    newStatusForHistory = taxiStatus
                }
                OrderStepConstants.SERVICE_PARCEL -> {
                    val parcelStatus = OrderStepConstants.STATUS_RIDER_ON_WAY_SENDER
                    updates["status"] = parcelStatus
                    newStatusForHistory = parcelStatus
                }
                OrderStepConstants.SERVICE_PABILI -> {
                    val pabiliStatus = OrderStepConstants.STATUS_RIDER_ON_WAY_VENDOR
                    updates["status"] = pabiliStatus
                    newStatusForHistory = pabiliStatus
                }
                else -> {
                    newStatusForHistory = currentStatus
                }
            }

            // statusHistory (arrayUnion with Timestamp.now(), never serverTimestamp)
            val historyEntry = mapOf(
                "status" to newStatusForHistory,
                "timestamp" to com.google.firebase.Timestamp.now()
            )
            updates["statusHistory"] = FieldValue.arrayUnion(historyEntry)

            // updatedAt
            updates["updatedAt"] = FieldValue.serverTimestamp()

            writeMap = updates
            tx.update(orderRef, updates)
        }.addOnSuccessListener {
            val accepted = _incomingOffer.value
            _incomingOffer.value = null
            if (accepted != null && accepted.id == orderId) {
                val stepForAccepted = when (accepted.serviceType.lowercase()) {
                    OrderStepConstants.SERVICE_TAXI -> OrderStepConstants.STEP_TO_PICKUP
                    OrderStepConstants.SERVICE_PARCEL -> OrderStepConstants.STEP_TO_SENDER
                    OrderStepConstants.SERVICE_PABILI -> OrderStepConstants.STEP_TO_VENDOR
                    else -> OrderStepConstants.STEP_TO_MERCHANT
                }
                val newStatus = when (accepted.serviceType.lowercase()) {
                    OrderStepConstants.SERVICE_TAXI -> OrderStepConstants.STATUS_DRIVER_ON_WAY
                    OrderStepConstants.SERVICE_PARCEL -> OrderStepConstants.STATUS_RIDER_ON_WAY_SENDER
                    OrderStepConstants.SERVICE_PABILI -> OrderStepConstants.STATUS_RIDER_ON_WAY_VENDOR
                    else -> accepted.status
                }
                val immediateOrder = accepted.copy(
                    riderId = authUid,
                    riderName = currentRider.name,
                    riderPhone = currentRider.phone,
                    riderPhotoUrl = currentRider.photoUrl,
                    riderStep = stepForAccepted,
                    status = newStatus
                )
                _activeOrders.value = (_activeOrders.value.filter { it.id != orderId } + immediateOrder)
                RiderLocationTracker.setActiveOrderIds(_activeOrders.value.map { it.id }.toSet())
            }
            onSuccess()
        }.addOnFailureListener { exception ->
            _incomingOffer.value = null
            val errorDetails = AcceptOrderErrorDetails(
                exceptionMessage = exception.message ?: exception.localizedMessage ?: "Failed to accept order",
                orderId = orderId,
                currentStatus = fetchedStatus,
                authUid = authUid,
                fieldsWritten = writeMap
            )
            onError(errorDetails)
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
                        nextStatus = OrderStepConstants.STATUS_RIDER_ON_THE_WAY
                        if (isRequest) nextReqStatus = OrderStepConstants.REQ_PICKED_UP
                        updates["pickedUpAt"] = FieldValue.serverTimestamp()
                    }
                    OrderStepConstants.STEP_PICKED_UP -> {
                        nextStep = OrderStepConstants.STEP_TO_CUSTOMER
                        nextStatus = OrderStepConstants.STATUS_RIDER_ON_THE_WAY
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

                        updates["riderEarning"] = mapOf(
                            "fare" to fare,
                            "commissionPercent" to commissionPercent,
                            "net" to net
                        )

                        orderRef.update(updates)
                            .addOnSuccessListener {
                                _activeOrders.value = _activeOrders.value.filter { it.id != order.id }
                                RiderLocationTracker.setActiveOrderIds(_activeOrders.value.map { it.id }.toSet())
                                onSuccess()
                            }
                            .addOnFailureListener { onError(it.localizedMessage ?: "Failed to complete trip") }
                    }
                    .addOnFailureListener {
                        val fare = if (order.feesTotal > 0) order.feesTotal else (order.baseFare + order.distanceFare)
                        val net = fare * (1.0 - 15.0 / 100.0)
                        updates["riderEarning"] = mapOf(
                            "fare" to fare,
                            "commissionPercent" to 15.0,
                            "net" to net
                        )
                        orderRef.update(updates)
                            .addOnSuccessListener {
                                _activeOrders.value = _activeOrders.value.filter { it.id != order.id }
                                RiderLocationTracker.setActiveOrderIds(_activeOrders.value.map { it.id }.toSet())
                                onSuccess()
                            }
                            .addOnFailureListener { onError(it.localizedMessage ?: "Failed to complete trip") }
                    }
            } else {
                db.collection("deliveryRates").document(cityId).get()
                    .addOnSuccessListener { rateDoc ->
                        val commissionPercent = rateDoc.getDouble("riderCommissionPercent") ?: 20.0
                        val deliveryFee = if (order.deliveryFee > 0) order.deliveryFee else (order.baseFare + order.distanceFare + order.surgeBonus)
                        val net = deliveryFee * (1.0 - commissionPercent / 100.0) + order.tip

                        updates["riderEarning"] = mapOf(
                            "deliveryFee" to deliveryFee,
                            "commissionPercent" to commissionPercent,
                            "tip" to order.tip,
                            "net" to net
                        )

                        orderRef.update(updates)
                            .addOnSuccessListener {
                                _activeOrders.value = _activeOrders.value.filter { it.id != order.id }
                                RiderLocationTracker.setActiveOrderIds(_activeOrders.value.map { it.id }.toSet())
                                onSuccess()
                            }
                            .addOnFailureListener { onError(it.localizedMessage ?: "Failed to complete delivery") }
                    }
                    .addOnFailureListener {
                        // Fallback default 20%
                        val deliveryFee = if (order.deliveryFee > 0) order.deliveryFee else (order.baseFare + order.distanceFare + order.surgeBonus)
                        val net = deliveryFee * 0.8 + order.tip
                        updates["riderEarning"] = mapOf(
                            "deliveryFee" to deliveryFee,
                            "commissionPercent" to 20.0,
                            "tip" to order.tip,
                            "net" to net
                        )
                        orderRef.update(updates)
                            .addOnSuccessListener {
                                _activeOrders.value = _activeOrders.value.filter { it.id != order.id }
                                RiderLocationTracker.setActiveOrderIds(_activeOrders.value.map { it.id }.toSet())
                                onSuccess()
                            }
                            .addOnFailureListener { onError(it.localizedMessage ?: "Failed to complete delivery") }
                    }
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
    fun releaseOrder(order: Order, reason: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
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
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.localizedMessage ?: "Failed to release order") }
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
        linkedUserListener?.remove()
        linkedUserListener = null
        customerTransactionsListener?.remove()
        customerTransactionsListener = null
        currentLinkedCustomerUid = null
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
            val merchantLat = (pickupMap?.get("lat") as? Number)?.toDouble()
                ?: (pickupMap?.get("latitude") as? Number)?.toDouble()
                ?: doc.getDouble("merchantLat") ?: 0.0
            val merchantLng = (pickupMap?.get("lng") as? Number)?.toDouble()
                ?: (pickupMap?.get("longitude") as? Number)?.toDouble()
                ?: doc.getDouble("merchantLng") ?: 0.0
            val merchantName = pickupMap?.get("name")?.toString()
                ?: doc.getString("merchantName") ?: doc.getString("senderName") ?: "Merchant"
            val merchantAddress = pickupMap?.get("address")?.toString()
                ?: doc.getString("merchantAddress") ?: doc.getString("senderAddress") ?: ""
            val merchantPhone = pickupMap?.get("phone")?.toString()
                ?: doc.getString("merchantPhone") ?: doc.getString("senderPhone") ?: ""

            // Asynchronously resolve merchant if lat/lng is missing
            if (merchantLat == 0.0 && merchantId.isNotBlank()) {
                fetchMerchantLocation(merchantId, id)
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
                OrderItem(
                    name = m["name"]?.toString() ?: m["title"]?.toString() ?: "",
                    quantity = (m["quantity"] as? Number)?.toInt() ?: 1,
                    price = (m["price"] as? Number)?.toDouble() ?: 0.0,
                    specialInstructions = m["specialInstructions"]?.toString() ?: ""
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
                merchantAddress = merchantAddress,
                merchantPhone = merchantPhone,
                merchantLat = merchantLat,
                merchantLng = merchantLng,
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
                cashToCollect = cashToCollect,
                cashCollectAt = cashCollectAt,
                codAmount = codAmount,
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
                releaseReason = doc.getString("releaseReason")
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
            return MessageNotification(
                id = doc.id,
                title = title,
                body = body,
                category = category,
                createdAt = doc.get("createdAt"),
                timestampText = "Recent"
            )
        } catch (_: Exception) {
            return null
        }
    }

    private val merchantCache = mutableMapOf<String, Map<String, Any?>>()

    private fun fetchMerchantLocation(merchantId: String, orderId: String) {
        val cached = merchantCache[merchantId]
        if (cached != null) {
            applyMerchantInfo(cached, orderId)
            return
        }
        db.collection("merchants").document(merchantId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val mLat = (doc.get("lat") as? Number)?.toDouble()
                        ?: (doc.get("latitude") as? Number)?.toDouble() ?: 0.0
                    val mLng = (doc.get("lng") as? Number)?.toDouble()
                        ?: (doc.get("longitude") as? Number)?.toDouble() ?: 0.0
                    val mName = doc.getString("name") ?: "Merchant"
                    val mAddr = doc.getString("address") ?: ""
                    val mPhone = doc.getString("phone") ?: ""
                    val data = mapOf<String, Any?>(
                        "lat" to mLat,
                        "lng" to mLng,
                        "name" to mName,
                        "address" to mAddr,
                        "phone" to mPhone
                    )
                    merchantCache[merchantId] = data
                    applyMerchantInfo(data, orderId)
                }
            }
    }

    private fun applyMerchantInfo(data: Map<String, Any?>, orderId: String) {
        val mLat = data["lat"] as? Double ?: 0.0
        val mLng = data["lng"] as? Double ?: 0.0
        val mName = data["name"] as? String ?: "Merchant"
        val mAddr = data["address"] as? String ?: ""
        val mPhone = data["phone"] as? String ?: ""

        if (mLat != 0.0 && mLng != 0.0) {
            _activeOrders.value = _activeOrders.value.map { o ->
                if (o.id == orderId && o.merchantLat == 0.0) {
                    o.copy(
                        merchantLat = mLat,
                        merchantLng = mLng,
                        merchantName = if (o.merchantName.isBlank() || o.merchantName == "Merchant") mName else o.merchantName,
                        merchantAddress = if (o.merchantAddress.isBlank()) mAddr else o.merchantAddress,
                        merchantPhone = if (o.merchantPhone.isBlank()) mPhone else o.merchantPhone
                    )
                } else o
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
}
