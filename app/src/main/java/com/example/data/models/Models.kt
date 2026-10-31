package com.example.data.models

import com.example.data.constants.OrderStepConstants

data class User(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val role: String = "rider",
    val avatarUrl: String = ""
)

data class Vehicle(
    val type: String = "motorcycle",
    val model: String = "",
    val plateNumber: String = "",
    val color: String = "",
    val seats: Int = 1
)

data class Rider(
    val uid: String = "",
    val riderCode: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val photoUrl: String = "",
    val vehicle: Vehicle = Vehicle(),
    val cityId: String = "",
    val rating: Double = 5.0,
    val memberSince: String = "",
    val isOnline: Boolean = false,
    val status: String = "active", // "active", "suspended", "inactive"
    val allowedServices: List<String> = listOf("food", "convenience", "parcel", "pabili", "request_rider", "taxi"),
    val activeServices: List<String> = listOf("food", "convenience", "parcel", "pabili", "request_rider", "taxi"),
    val walletBalance: Double = 0.0,
    val linkedCustomerUid: String? = null,
    val cashRemitted: Double = 0.0,
    val coins: Int = 0,
    val totalTrips: Int = 0,
    val emergencyContactName: String = "",
    val emergencyContactPhone: String = ""
) {
    val user: User
        get() = User(id = uid, name = name, email = email, phone = phone, role = "rider", avatarUrl = photoUrl)
}

data class RiderApplication(
    val ownerUid: String = "",
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val cityId: String = "",
    val vehicleType: String = "motorcycle",
    val plate: String = "",
    val vehicleModel: String = "",
    val vehicleColor: String = "",
    val seats: Int = 1,
    val emergencyContact: Map<String, String> = emptyMap(),
    val documents: Map<String, String> = emptyMap(), // licenseUrl, orcrUrl, nbiUrl, validIdUrl, selfieUrl, vehiclePhotoUrl, franchiseUrl
    val status: String = "Pending", // "Pending", "Under Review", "Needs Info", "Rejected", "Approved"
    val rejectionReason: String = "",
    val needsInfoReason: String = "",
    val submittedAt: Any? = null
)

data class OrderItem(
    val name: String = "",
    val quantity: Int = 1,
    val price: Double = 0.0,
    val specialInstructions: String = "",
    val selections: List<String> = emptyList(),
    var isChecked: Boolean = false
)

data class PabiliItem(
    val product: String = "",
    val quantity: Int = 1,
    val estimatedPrice: Double = 0.0
)

data class TaxiStop(
    val stopNumber: Int = 2,
    val address: String = "",
    val area: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0
)

data class Order(
    val id: String = "",
    val orderNumber: String = "",
    val serviceType: String = OrderStepConstants.SERVICE_FOOD,
    val status: String = OrderStepConstants.STATUS_PLACED_ORDER,
    val riderStep: String = OrderStepConstants.STEP_HEADING_TO_MERCHANT,
    val riderId: String? = null,
    val riderName: String? = null,
    val riderPhone: String? = null,
    val riderPhotoUrl: String? = null,
    val cityId: String = "",
    val merchantId: String = "",

    // Vehicle type for taxi/parcel dispatch
    val vehicleType: String = "motorcycle",

    // Taxi stops (Stop 1 is pickup; Stop 2 is drop-off; Stop 3, 4... are extra stops)
    val stops: List<TaxiStop> = emptyList(),
    val completedStops: List<Int> = emptyList(),

    // Fulfillment & Scheduling
    val fulfillment: String = "delivery",
    val isScheduled: Boolean = false,
    val scheduledFor: Any? = null,
    val scheduledDateText: String = "",
    val merchantAccepted: Boolean = false,
    val prepMinutes: Int = 0,
    val editedAt: Any? = null,

    // Merchant / Pickup
    val merchantName: String = "",
    val merchantAddress: String = "",
    val merchantPhone: String = "",
    val merchantLat: Double = 0.0,
    val merchantLng: Double = 0.0,

    // Customer / Drop-off
    val customerName: String = "",
    val customerPhone: String = "",
    val customerAddress: String = "",
    val customerArea: String = "",
    val customerLat: Double = 0.0,
    val customerLng: Double = 0.0,
    val customerNotes: String = "",

    // Items
    val items: List<OrderItem> = emptyList(),

    // Pricing
    val baseFare: Double = 0.0,
    val distanceFare: Double = 0.0,
    val tip: Double = 0.0,
    val surgeBonus: Double = 0.0,
    val deliveryFee: Double = 0.0,
    val waitingPay: Double = 0.0,
    val extraStopPay: Double = 0.0,
    val returnTripPay: Double = 0.0,
    val urgentBonus: Double = 0.0,
    val platformCommission: Double = 0.0,

    // Payment & Collection fields
    val paymentMethod: String = "",
    val paymentProvider: String = "",
    val cashToCollect: Double = 0.0,
    val cashCollectAt: String = "",
    val codAmount: Double = 0.0,
    val feesTotal: Double = 0.0,
    val feesDeliveryFee: Double = 0.0,
    val feesServiceFee: Double = 0.0,
    val codCollected: Double = 0.0,

    // Parcel specifications
    val parcelSize: String = "M", // S, M, L, XL
    val parcelWeightKg: Double = 1.0,
    val parcelItemDescription: String = "",
    val parcelFragile: Boolean = false,
    val parcelFeePayer: String = "SENDER", // SENDER or RECEIVER

    // Pabili specifications
    val pabiliItems: List<PabiliItem> = emptyList(),
    val pabiliEstimatedTotal: Double = 0.0,
    val pabiliBudget: Double = 0.0,
    val finalReceiptTotal: Double = 0.0,

    // Request a Rider
    val riderRequestStatus: String = "",

    // Distances & Timestamps
    val pickupDistanceKm: Double = 0.0,
    val deliveryDistanceKm: Double = 0.0,
    val placedAt: String = "",
    val pickedUpAt: Any? = null,
    val arrivedMerchantAt: Any? = null,
    val arrivedCustomerAt: Any? = null,
    val deliveredAt: Any? = null,
    val releaseReason: String? = null
) {
    val isTaxi: Boolean
        get() = serviceType.lowercase() == OrderStepConstants.SERVICE_TAXI

    val allTaxiStops: List<TaxiStop>
        get() {
            if (!isTaxi) return emptyList()
            val list = mutableListOf<TaxiStop>()
            // Stop 2 is the primary drop-off
            list.add(
                TaxiStop(
                    stopNumber = 2,
                    address = customerAddress,
                    area = customerArea.ifEmpty { "Drop-off" },
                    lat = customerLat,
                    lng = customerLng
                )
            )
            // Extra stops (3, 4...)
            stops.forEachIndexed { index, stop ->
                val num = if (stop.stopNumber >= 3) stop.stopNumber else (index + 3)
                list.add(stop.copy(stopNumber = num))
            }
            return list
        }

    val totalStopsCount: Int
        get() = if (isTaxi) (1 + allTaxiStops.size) else 1

    val currentTaxiStopNumber: Int
        get() {
            if (!isTaxi) return 2
            val step = riderStep.lowercase()
            if (step.startsWith(OrderStepConstants.STEP_TO_STOP_PREFIX)) {
                val numStr = step.removePrefix(OrderStepConstants.STEP_TO_STOP_PREFIX)
                return numStr.toIntOrNull() ?: 2
            }
            return 2
        }

    val isAtLastTaxiStop: Boolean
        get() {
            if (!isTaxi) return true
            val maxStop = allTaxiStops.maxOfOrNull { it.stopNumber } ?: 2
            return currentTaxiStopNumber >= maxStop
        }

    val currentTargetStop: TaxiStop?
        get() = allTaxiStops.firstOrNull { it.stopNumber == currentTaxiStopNumber }

    val estimatedEarnings: Double
        get() {
            if (isTaxi) {
                // net = fees.total x (1 - commissionPercent/100), default commission 15%
                val commPercent = if (platformCommission > 0.0) platformCommission else 15.0
                return feesTotal * (1.0 - commPercent / 100.0) + tip
            }
            val baseDelivery = if (feesDeliveryFee > 0.0) feesDeliveryFee else if (deliveryFee > 0.0) deliveryFee else (baseFare + distanceFare + surgeBonus)
            val netDelivery = if (platformCommission > 0.0) (baseDelivery - platformCommission) else (baseDelivery * 0.8)
            return netDelivery + tip + waitingPay + extraStopPay + returnTripPay + urgentBonus
        }

    val isPickup: Boolean
        get() {
            val f = fulfillment.trim().lowercase().replace("-", "").replace("_", "")
            return f == "pickup"
        }

    val isEditedByMerchant: Boolean
        get() = editedAt != null && (editedAt !is Boolean || editedAt == true) && (editedAt !is String || (editedAt as String).isNotBlank())

    val prepStatusText: String
        get() {
            val s = status.trim().lowercase()
            return when {
                s == "ready" || s == OrderStepConstants.STATUS_READY.lowercase() -> "Order is ready"
                s == "preparing" || s == OrderStepConstants.STATUS_PREPARING.lowercase() -> {
                    val mins = if (prepMinutes > 0) prepMinutes else 15
                    "Merchant is preparing - about $mins min"
                }
                else -> ""
            }
        }

    val scheduledChipText: String
        get() = if (scheduledDateText.isNotBlank()) "Scheduled for $scheduledDateText" else "Scheduled delivery"

    val tipChipText: String
        get() = if (tip % 1.0 == 0.0) "Tip ₱${String.format("%.0f", tip)}" else "Tip ₱${String.format("%.2f", tip)}"

    val isRequestRider: Boolean
        get() = serviceType == OrderStepConstants.SERVICE_REQUEST

    val isParcel: Boolean
        get() = serviceType.lowercase() == OrderStepConstants.SERVICE_PARCEL

    val paymentType: PaymentType
        get() = parsePaymentType(paymentMethod)

    val isCod: Boolean
        get() = paymentType == PaymentType.COD

    val isPaidOnline: Boolean
        get() = paymentType == PaymentType.PAID_ONLINE

    val isPaymentUnknown: Boolean
        get() = paymentType == PaymentType.UNKNOWN

    val paymentProviderLabel: String
        get() = formatPaymentProvider(paymentProvider)

    val parcelCollectAtSender: Boolean
        get() = serviceType.lowercase() == OrderStepConstants.SERVICE_PARCEL &&
                cashCollectAt.trim().lowercase() == "sender"

    // Section 2: CASH TO COLLECT
    // For COD, the cash to collect is fees.total, which already includes the tip.
    // COD, parcel: cashToCollect, from party in cashCollectAt ("sender" at pickup, "recipient" at delivery).
    // COD, pabili: finalReceiptTotal + fees.deliveryFee + fees.serviceFee + tip, collected at delivery.
    // request_rider: codAmount (collected from recipient). Delivery fee shows "Paid by merchant".
    // Taxi COD: fees.total
    // Paid Online or wallet: no cash to collect.
    val cashAmountToCollect: Double
        get() {
            if (paymentType == PaymentType.PAID_ONLINE || paymentType == PaymentType.UNKNOWN) {
                return 0.0
            }
            return when (serviceType.lowercase()) {
                OrderStepConstants.SERVICE_TAXI -> if (cashToCollect > 0.0) cashToCollect else feesTotal
                OrderStepConstants.SERVICE_PARCEL -> cashToCollect
                OrderStepConstants.SERVICE_PABILI -> {
                    val itemsTotal = if (finalReceiptTotal > 0.0) finalReceiptTotal else pabiliEstimatedTotal
                    itemsTotal + feesDeliveryFee + feesServiceFee + tip
                }
                OrderStepConstants.SERVICE_REQUEST, "request" -> codAmount
                else -> {
                    // COD food or convenience: cashToCollect, falling back to fees.total (which includes tip)
                    if (cashToCollect > 0.0) cashToCollect else feesTotal
                }
            }
        }
}

enum class PaymentType {
    COD,
    PAID_ONLINE,
    UNKNOWN
}

fun parsePaymentType(paymentMethod: String?): PaymentType {
    val pm = paymentMethod?.trim()?.lowercase() ?: ""
    return when (pm) {
        "cod", "cash", "cash on delivery" -> PaymentType.COD
        "wallet", "online" -> PaymentType.PAID_ONLINE
        else -> PaymentType.UNKNOWN
    }
}

fun formatPaymentProvider(provider: String?): String {
    return when (provider?.trim()?.lowercase()) {
        "wallet" -> "Delivio Wallet"
        "gcash" -> "GCash"
        "maya" -> "Maya"
        "bank" -> "Bank"
        "card" -> "Card"
        else -> if (!provider.isNullOrBlank()) provider.trim().replaceFirstChar { it.uppercase() } else ""
    }
}

data class TierConfig(
    val id: String = "",
    val name: String = "",
    val ridesRequired: Int = 0,
    val coins: Int = 0,
    val sortOrder: Int = 0
)

data class CoinTransaction(
    val id: String = "",
    val title: String = "",
    val amount: Int = 0,
    val timestampText: String = "",
    val isCredit: Boolean = true
)

data class MessageNotification(
    val id: String = "",
    val title: String = "",
    val body: String = "",
    val category: String = "system", // admin, order, promo, safety, system
    val createdAt: Any? = null,
    val timestampText: String = "",
    val isRead: Boolean = false
)

data class DocumentItem(
    val id: String = "",
    val title: String = "",
    val status: String = "Pending",
    val expiryDate: String = "",
    val documentNumber: String = ""
)

data class ActiveCallInfo(
    val recipientName: String,
    val phoneNumber: String,
    val callType: String = "Delivio Voice Call",
    val isConnected: Boolean = true
)

enum class TierLevel(
    val title: String,
    val monthlyRidesRequired: Int,
    val rewardCoins: Int,
    val badgeColorHex: Long,
    val perkDescription: String
) {
    BRONZE("Bronze", 500, 200, 0xFFCD7F32, "Basic dispatch priority & milestone reward coins"),
    SILVER("Silver", 650, 500, 0xFFC0C0C0, "Higher dispatch priority during peak hours"),
    GOLD("Gold", 850, 700, 0xFFFFD700, "VIP order assignment priority + peak surge bonuses"),
    PLATINUM("Platinum", 1000, 1000, 0xFFE5E4E2, "Top priority dispatch + maximum coin rewards")
}

data class RiderTierStatus(
    val currentMonthRides: Int = 0,
    val currentTier: TierLevel? = null,
    val currentCoins: Int = 0,
    val daysRemainingInMonth: Int = 12,
    val claimedTierRewards: Set<String> = emptySet(),
    val coinHistory: List<CoinTransaction> = emptyList(),
    val newlyAchievedTier: TierLevel? = null
)

data class EarningSummary(
    val todayNet: Double = 0.0,
    val weekNet: Double = 0.0,
    val monthNet: Double = 0.0,
    val jobsCompletedToday: Int = 0,
    val onlineHoursToday: Double = 0.0
)

data class ChatMessage(
    val id: String = "",
    val senderName: String = "",
    val message: String = "",
    val timestampText: String = "",
    val isFromMe: Boolean = false,
    val senderType: String = "rider"
)

enum class RiderStep(val stepKey: String) {
    HEADING_TO_MERCHANT(OrderStepConstants.STEP_HEADING_TO_MERCHANT),
    ARRIVED_AT_MERCHANT(OrderStepConstants.STEP_ARRIVED_AT_MERCHANT),
    PICKED_UP(OrderStepConstants.STEP_PICKED_UP),
    HEADING_TO_CUSTOMER(OrderStepConstants.STEP_HEADING_TO_CUSTOMER),
    ARRIVED_AT_CUSTOMER(OrderStepConstants.STEP_ARRIVED_AT_CUSTOMER),
    DELIVERED(OrderStepConstants.STEP_DELIVERED);

    companion object {
        fun fromKey(key: String): RiderStep = values().firstOrNull { it.stepKey == key } ?: HEADING_TO_MERCHANT
    }
}

data class WalletTransaction(
    val id: String = "",
    val riderId: String = "",
    val customerId: String = "",
    val type: String = "cash_in", // "cash_in", "adjustment", etc.
    val amount: Double = 0.0,
    val note: String = "",
    val dateText: String = "",
    val dateRaw: Long = 0L,
    val balanceAfter: Double? = null,
    val title: String = "",
    val referenceNo: String = "",
    val category: String = "Wallet",
    val isCredit: Boolean = true
) {
    val isPositive: Boolean
        get() = when {
            amount > 0 -> true
            amount < 0 -> false
            type.lowercase() in listOf("cash_in", "top_up", "credit", "bonus", "reward") -> true
            type.lowercase() in listOf("cash_out", "deduction", "fee", "penalty", "debit") -> false
            else -> isCredit
        }

    val displayAmount: String
        get() {
            val sign = if (isPositive) "+" else "-"
            return "$sign${String.format("%.2f", kotlin.math.abs(amount))}"
        }

    val formattedType: String
        get() = formatType(type)

    companion object {
        fun formatType(rawType: String): String {
            return when (rawType.trim().lowercase()) {
                "cash_in" -> "Cash In"
                "adjustment" -> "Adjustment"
                "cash_out" -> "Cash Out"
                "top_up" -> "Top Up"
                "penalty" -> "Penalty"
                "fee" -> "Fee"
                else -> if (rawType.isNotBlank()) {
                    rawType.replace("_", " ").split(" ")
                        .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                } else "Transaction"
            }
        }
    }
}

data class AcceptOrderErrorDetails(
    val exceptionMessage: String = "",
    val orderId: String = "",
    val currentStatus: String = "",
    val authUid: String = "",
    val fieldsWritten: Map<String, Any?> = emptyMap()
)

data class OfferLogEntry(
    val orderId: String = "",
    val serviceType: String = "",
    val outcome: String = "", // "accepted", "declined", "missed"
    val finalOutcome: String? = null, // "released", "completed"
    val at: com.google.firebase.Timestamp? = null,
    val finalAt: com.google.firebase.Timestamp? = null,
    val atMillis: Long = at?.toDate()?.time ?: 0L,
    val finalAtMillis: Long? = finalAt?.toDate()?.time
)

data class CustomerReview(
    val id: String = "",
    val riderId: String = "",
    val ratingRider: Double = 5.0,
    val comment: String = "",
    val customerName: String = "",
    val orderNumber: String = "",
    val serviceType: String = "food",
    val date: String = "",
    val createdAt: com.google.firebase.Timestamp? = null,
    val dateMillis: Long = createdAt?.toDate()?.time ?: 0L
) {
    val stars: Int
        get() = ratingRider.toInt().coerceIn(1, 5)

    val dateText: String
        get() = if (date.isNotBlank()) date else {
            if (dateMillis > 0L) {
                val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.US)
                sdf.format(java.util.Date(dateMillis))
            } else "Recent"
        }
}

data class ServiceRateItem(
    val serviceType: String = "",
    val displayName: String = "",
    val ratePercentage: Double? = null, // null if no data
    val numerator: Int = 0,
    val denominator: Int = 0,
    val countLabel: String = "" // e.g. "12 of 14 offers" or "No data yet"
) {
    val hasData: Boolean
        get() = ratePercentage != null && denominator > 0

    val progress: Float
        get() = if (denominator > 0) (numerator.toFloat() / denominator.toFloat()).coerceIn(0f, 1f) else 0f
}

