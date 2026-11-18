package com.example.data.models

import com.example.data.constants.OrderStepConstants
import java.util.TimeZone
import java.util.Locale
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.UUID

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
    val emergencyContactPhone: String = "",
    val priorityRider: Boolean = false,
    val documents: List<RiderDocument> = emptyList()
) {
    val user: User
        get() = User(id = uid, name = name, email = email, phone = phone, role = "rider", avatarUrl = photoUrl)

    val documentExpiryAlerts: List<DocumentExpiryAlert>
        get() = documents.mapNotNull { it.getExpiryAlert() }.sortedBy { it.daysDiff }
}

enum class DocumentAlertLevel {
    EXPIRED,
    WARNING_7_DAYS,
    NOTICE_14_DAYS,
    REMINDER_30_DAYS
}

data class DocumentExpiryAlert(
    val document: RiderDocument,
    val status: DocumentAlertLevel,
    val title: String,
    val message: String,
    val daysDiff: Int
)

data class RiderDocument(
    val type: String = "", // "license", "registration", "insurance"
    val expiresAt: Any? = null
) {
    val expiresAtMillis: Long
        get() = when (expiresAt) {
            is com.google.firebase.Timestamp -> expiresAt.toDate().time
            is Date -> expiresAt.time
            is Number -> {
                val num = expiresAt.toLong()
                if (num < 1000000000000L) num * 1000L else num
            }
            is String -> {
                val trimmed = expiresAt.trim()
                val num = trimmed.toLongOrNull()
                if (num != null) {
                    if (num < 1000000000000L) num * 1000L else num
                } else {
                    val patterns = listOf(
                        "yyyy-MM-dd'T'HH:mm:ss",
                        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                        "yyyy-MM-dd HH:mm:ss",
                        "yyyy-MM-dd",
                        "MM/dd/yyyy",
                        "MMM d, yyyy"
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

    val typeDisplayName: String
        get() = when (type.lowercase().trim()) {
            "license" -> "Driver's License"
            "registration" -> "Vehicle Registration"
            "insurance" -> "Vehicle Insurance"
            else -> type.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
        }

    val formattedExpiryDate: String
        get() {
            val ms = expiresAtMillis
            if (ms <= 0L) return "Not set"
            val sdf = SimpleDateFormat("MMM d, yyyy", Locale.US)
            return sdf.format(Date(ms))
        }

    fun getExpiryAlert(nowMillis: Long = System.currentTimeMillis()): DocumentExpiryAlert? {
        val ms = expiresAtMillis
        if (ms <= 0L) return null
        val diffMs = ms - nowMillis
        val days = Math.ceil(diffMs.toDouble() / (1000.0 * 60 * 60 * 24)).toInt()
        return when {
            days < 0 || diffMs <= 0 -> DocumentExpiryAlert(
                document = this,
                status = DocumentAlertLevel.EXPIRED,
                title = "Expired: $typeDisplayName",
                message = "Your $typeDisplayName has expired. Please update it immediately.",
                daysDiff = days
            )
            days <= 7 -> DocumentExpiryAlert(
                document = this,
                status = DocumentAlertLevel.WARNING_7_DAYS,
                title = "Expires in $days day${if (days == 1) "" else "s"}: $typeDisplayName",
                message = "Your $typeDisplayName expires on $formattedExpiryDate. Please renew soon.",
                daysDiff = days
            )
            days <= 14 -> DocumentExpiryAlert(
                document = this,
                status = DocumentAlertLevel.NOTICE_14_DAYS,
                title = "Expires in $days days: $typeDisplayName",
                message = "Your $typeDisplayName expires on $formattedExpiryDate.",
                daysDiff = days
            )
            days <= 30 -> DocumentExpiryAlert(
                document = this,
                status = DocumentAlertLevel.REMINDER_30_DAYS,
                title = "Renewal reminder: $typeDisplayName",
                message = "Your $typeDisplayName expires in $days days on $formattedExpiryDate.",
                daysDiff = days
            )
            else -> null
        }
    }
}

data class DriverShift(
    val id: String = UUID.randomUUID().toString(),
    val dayOfWeek: Int = 1, // 1 = Mon, 2 = Tue, 3 = Wed, 4 = Thu, 5 = Fri, 6 = Sat, 7 = Sun
    val startTime: String = "08:00", // "HH:mm"
    val endTime: String = "17:00",   // "HH:mm"
    val reminder15MinBefore: Boolean = true,
    val isEnabled: Boolean = true
) {
    val dayName: String
        get() = when (dayOfWeek) {
            1 -> "Monday"
            2 -> "Tuesday"
            3 -> "Wednesday"
            4 -> "Thursday"
            5 -> "Friday"
            6 -> "Saturday"
            7 -> "Sunday"
            else -> "Day $dayOfWeek"
        }

    val dayShortName: String
        get() = when (dayOfWeek) {
            1 -> "Mon"
            2 -> "Tue"
            3 -> "Wed"
            4 -> "Thu"
            5 -> "Fri"
            6 -> "Sat"
            7 -> "Sun"
            else -> "D$dayOfWeek"
        }

    val durationHours: Double
        get() {
            try {
                val startParts = startTime.split(":").map { it.toInt() }
                val endParts = endTime.split(":").map { it.toInt() }
                val startMins = startParts[0] * 60 + startParts.getOrElse(1) { 0 }
                val endMins = endParts[0] * 60 + endParts.getOrElse(1) { 0 }
                val diff = if (endMins >= startMins) endMins - startMins else (24 * 60 - startMins + endMins)
                return diff / 60.0
            } catch (_: Exception) {
                return 0.0
            }
        }
}

data class BusyGridCell(
    val id: String,
    val centerLat: Double,
    val centerLng: Double,
    val orderCount: Int,
    val minLat: Double,
    val maxLat: Double,
    val minLng: Double,
    val maxLng: Double
)

object BusyHeatMapCalculator {
    // 500 meters in latitude ≈ 0.0045 degrees
    const val LAT_GRID_STEP = 0.0045

    fun computeGridCells(orders: List<Order>, nowMillis: Long = System.currentTimeMillis()): List<BusyGridCell> {
        val windowMs = 60 * 60 * 1000L
        val minTime = nowMillis - windowMs

        // Filter recent orders from last 60 minutes
        val recentOrders = orders.filter { order ->
            val s = order.status.trim().lowercase()
            val isCurrentActive = s != "cancelled" && s != "canceled" && s != "rejected" && s != "delivered"
            if (isCurrentActive) {
                true
            } else {
                // For delivered / completed orders, check if delivered within last 60 minutes
                val dTime = OrderDocumentParser.parseTolerantTimeMillisOrZero(order.deliveredAt)
                dTime >= minTime
            }
        }

        // Group orders by ~500m grid cell
        val cellOrderMap = mutableMapOf<String, MutableList<Order>>()
        for (order in recentOrders) {
            val lat = if (order.merchantLat != 0.0) order.merchantLat else order.customerLat
            val lng = if (order.merchantLng != 0.0) order.merchantLng else order.customerLng
            if (lat == 0.0 || lng == 0.0) continue

            val lngGridStep = LAT_GRID_STEP / Math.cos(Math.toRadians(lat)).coerceAtLeast(0.1)
            val gridLat = Math.round(lat / LAT_GRID_STEP) * LAT_GRID_STEP
            val gridLng = Math.round(lng / lngGridStep) * lngGridStep

            val cellKey = "${String.format(Locale.US, "%.4f", gridLat)}_${String.format(Locale.US, "%.4f", gridLng)}"
            cellOrderMap.getOrPut(cellKey) { mutableListOf() }.add(order)
        }

        return cellOrderMap.map { (key, cellOrders) ->
            val firstOrder = cellOrders.first()
            val lat = if (firstOrder.merchantLat != 0.0) firstOrder.merchantLat else firstOrder.customerLat
            val lngGridStep = LAT_GRID_STEP / Math.cos(Math.toRadians(lat)).coerceAtLeast(0.1)
            val parts = key.split("_")
            val cLat = parts.getOrNull(0)?.toDoubleOrNull() ?: 0.0
            val cLng = parts.getOrNull(1)?.toDoubleOrNull() ?: 0.0

            BusyGridCell(
                id = key,
                centerLat = cLat,
                centerLng = cLng,
                orderCount = cellOrders.size,
                minLat = cLat - LAT_GRID_STEP / 2.0,
                maxLat = cLat + LAT_GRID_STEP / 2.0,
                minLng = cLng - lngGridStep / 2.0,
                maxLng = cLng + lngGridStep / 2.0
            )
        }.sortedByDescending { it.orderCount }
    }
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
    var isChecked: Boolean = false,
    val variantKey: String = "",
    val variantLabel: String = "",
    val variantImageUrl: String = "",
    val unitPrice: Double? = null,
    val regularUnitPrice: Double? = null
) {
    val displayName: String
        get() {
            val base = name.trim()
            val label = variantLabel.trim()
            return if (label.isNotBlank() && !base.endsWith("($label)", ignoreCase = true)) {
                if (base.isNotBlank()) "$base ($label)" else label
            } else {
                base
            }
        }

    val displayNameWithQty: String
        get() = "$displayName x$quantity"
}

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

data class SplitChildItem(
    val orderId: String = "",
    val items: List<String> = emptyList(),
    val seq: Int = 1
)

data class SplitOutInfo(
    val count: Int = 0,
    val children: List<SplitChildItem> = emptyList(),
    val at: Any? = null,
    val by: String = ""
)

enum class TaxiStepStage {
    TO_PICKUP,
    AT_PICKUP,
    TO_EXTRA_STOP,
    AT_EXTRA_STOP,
    TO_DROPOFF,
    AT_DROPOFF,
    COMPLETED
}

enum class PabiliStepStage {
    TO_VENDOR,
    AT_VENDOR,
    TO_RECIPIENT,
    AT_RECIPIENT,
    COMPLETED
}

data class PabiliTransitionSpec(
    val expectedPrevStatus: String,
    val nextStep: String,
    val nextStatus: String
)

object PabiliFlowRules {
    fun transitionFor(stage: PabiliStepStage): PabiliTransitionSpec? = when (stage) {
        PabiliStepStage.TO_VENDOR -> PabiliTransitionSpec(
            expectedPrevStatus = OrderStepConstants.STATUS_RIDER_ON_WAY_VENDOR,
            nextStep = OrderStepConstants.STEP_AT_VENDOR,
            nextStatus = OrderStepConstants.STATUS_ARRIVED_AT_VENDOR
        )
        PabiliStepStage.AT_VENDOR -> PabiliTransitionSpec(
            expectedPrevStatus = OrderStepConstants.STATUS_ARRIVED_AT_VENDOR,
            nextStep = OrderStepConstants.STEP_TO_RECIPIENT,
            nextStatus = OrderStepConstants.STATUS_RIDER_ON_WAY_RECIPIENT
        )
        PabiliStepStage.TO_RECIPIENT -> PabiliTransitionSpec(
            expectedPrevStatus = OrderStepConstants.STATUS_RIDER_ON_WAY_RECIPIENT,
            nextStep = OrderStepConstants.STEP_AT_RECIPIENT,
            nextStatus = OrderStepConstants.STATUS_ARRIVED_AT_RECIPIENT
        )
        PabiliStepStage.AT_RECIPIENT -> PabiliTransitionSpec(
            expectedPrevStatus = OrderStepConstants.STATUS_ARRIVED_AT_RECIPIENT,
            nextStep = OrderStepConstants.STEP_COMPLETED,
            nextStatus = OrderStepConstants.STATUS_DELIVERED
        )
        PabiliStepStage.COMPLETED -> null
    }

    fun isExpectedStatus(actualDbStatus: String?, expectedPrevStatus: String): Boolean {
        return (actualDbStatus ?: "").trim().equals(expectedPrevStatus.trim(), ignoreCase = true)
    }

    fun isValidReceiptInput(raw: String): Boolean {
        val trimmed = raw.trim()
        if (!Regex("^\\d+(\\.\\d{1,2})?$").matches(trimmed)) return false
        val parsed = trimmed.toDoubleOrNull() ?: return false
        return parsed > 0.0
    }

    fun parseReceiptOrNull(raw: String): Double? {
        if (!isValidReceiptInput(raw)) return null
        return raw.trim().toDoubleOrNull()?.takeIf { it > 0.0 }
    }
}

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
    val passengerCount: Int = 1,
    val pickupNote: String = "",

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
    val senderName: String = "",
    val branchName: String = "",
    val branchCode: String = "",
    val chainId: String = "",
    val merchantAddress: String = "",
    val merchantPhone: String = "",
    val merchantLat: Double = 0.0,
    val merchantLng: Double = 0.0,

    // Customer / Drop-off
    val customerId: String = "",
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
    val lateTipAmount: Double = 0.0,
    val lateTipAt: Any? = null,
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
    val budgetLimit: Double = 0.0,
    val finalReceiptTotal: Double = 0.0,
    val paidOnMerchant: Double = 0.0,
    val paidOnMerchantAt: Any? = null,
    val overBudgetApproved: Boolean = false,

    // Request a Rider
    val riderRequestStatus: String = "",

    // Distances & Timestamps
    val pickupDistanceKm: Double = 0.0,
    val deliveryDistanceKm: Double = 0.0,
    val placedAt: String = "",
    val pickedUpAt: Any? = null,
    val arrivedMerchantAt: Any? = null,
    val arrivedCustomerAt: Any? = null,
    val arrivedAtPickupAt: Any? = null,
    val arrivedAtDropoffAt: Any? = null,
    val completedAt: Any? = null,
    val deliveredAt: Any? = null,
    val updatedAt: Any? = null,
    val statusHistory: List<Map<String, Any?>> = emptyList(),
    val releaseReason: String? = null,

    // Batching & Dispatch
    val batchId: String? = null,
    val batchIndex: Int = 1,
    val riderFeeEstimate: Double = 0.0,
    val riderFeeFixed: Double? = null,
    val badge: String = "Standard", // "Priority", "Standard", "Saver", "SPLIT"
    val isPriority: Boolean = false,
    val isPoolable: Boolean = false,
    val maxBatch: Int = 2,
    val holdMinutes: Double = 0.0,
    val acceptedAtMillis: Long = 0L,
    val holdUntilMillis: Long = 0L,
    val endsWhenReady: Boolean? = null,
    val specialRadiusExtra: Double = 0.0,
    val excludeRiderIds: List<String> = emptyList(),
    val targetRiderId: String = "",
    val riderEarning: RiderEarning? = null,
    val splitOut: List<String> = emptyList(),
    val splitOutInfo: SplitOutInfo? = null,
    val splitParentOrderId: String? = null,
    val isSplitChild: Boolean = false
) {
    val hasSplitOut: Boolean
        get() = splitOutInfo != null || splitOut.isNotEmpty()

    val isSplitParent: Boolean
        get() = !isSplitChild && hasSplitOut

    val displayOrderNumber: String
        get() {
            val raw = orderNumber.trim()
            val num = if (raw.isNotBlank()) {
                raw.removePrefix("#")
            } else {
                id.takeLast(6).uppercase().ifBlank { "000000" }
            }
            return "#$num"
        }

    val displayMerchantName: String
        get() = formatMerchantDisplayName(sanitizeDisplayName(merchantName, ""), branchName, branchCode)

    val resolvedPickupNote: String
        get() = pickupNote.ifBlank { customerNotes }.trim()

    val displayPickupName: String
        get() {
            val s = serviceType.lowercase().trim()
            if (s == OrderStepConstants.SERVICE_PARCEL) {
                val sender = sanitizeDisplayName(senderName, "")
                    .takeUnless { it.equals("Merchant", ignoreCase = true) || it.equals("Passenger Pickup", ignoreCase = true) }
                if (!sender.isNullOrBlank()) return sender
                val merchantClean = sanitizeDisplayName(merchantName, "")
                    .takeUnless { it.equals("Merchant", ignoreCase = true) || it.equals("Passenger Pickup", ignoreCase = true) }
                return if (!merchantClean.isNullOrBlank()) merchantClean else "Sender"
            }
            if (s == OrderStepConstants.SERVICE_PABILI) {
                val merchantDisplay = formatMerchantDisplayName(
                    sanitizeDisplayName(merchantName, "")
                        .takeUnless { it.equals("Merchant", ignoreCase = true) || it.equals("Passenger Pickup", ignoreCase = true) }
                        ?: "",
                    branchName,
                    branchCode
                )
                if (merchantDisplay.isNotBlank()) return merchantDisplay
                val sender = sanitizeDisplayName(senderName, "")
                    .takeUnless { it.equals("Merchant", ignoreCase = true) || it.equals("Passenger Pickup", ignoreCase = true) }
                return if (!sender.isNullOrBlank()) sender else "Vendor"
            }
            if (s == OrderStepConstants.SERVICE_TAXI) {
                val cleanCustomer = sanitizeDisplayName(customerName, "")
                    .takeUnless {
                        it.equals("Merchant", ignoreCase = true) ||
                            it.equals("Passenger Pickup", ignoreCase = true) ||
                            it.equals("Passenger", ignoreCase = true)
                    }
                return if (cleanCustomer.isNullOrBlank()) "Passenger" else formatCustomerFirstName(cleanCustomer)
            }
            return displayMerchantName.ifBlank { "Merchant" }
        }

    val deliveredAtMillis: Long
        get() = OrderDocumentParser.parseTolerantTimeMillisOrZero(deliveredAt)

    val scheduledForMillis: Long
        get() = OrderDocumentParser.parseTolerantTimeMillisOrZero(scheduledFor)

    fun getReleaseTimeMillis(leadMinutes: Double = 0.0): Long {
        val sched = scheduledForMillis
        if (sched <= 0L) return 0L
        val leadMillis = (leadMinutes * 60 * 1000).toLong()
        return (sched - leadMillis).coerceAtLeast(0L)
    }

    fun isFutureScheduled(leadMinutes: Double = 0.0, nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (!isScheduled) return false
        val releaseTime = getReleaseTimeMillis(leadMinutes)
        return releaseTime > 0L && nowMillis < releaseTime
    }

    val resolvedEndsWhenReady: Boolean
        get() = endsWhenReady ?: when {
            badge.equals("saver", ignoreCase = true) -> false
            else -> true
        }

    val isTaxi: Boolean
        get() = serviceType.trim().lowercase(Locale.US) == OrderStepConstants.SERVICE_TAXI

    val isPabili: Boolean
        get() = serviceType.trim().lowercase(Locale.US) == OrderStepConstants.SERVICE_PABILI

    val resolvedBudgetLimit: Double
        get() = if (budgetLimit > 0.0) budgetLimit else pabiliBudget

    val resolvedPaidOnMerchant: Double
        get() = if (paidOnMerchant > 0.0) paidOnMerchant else finalReceiptTotal

    val pabiliStepStage: PabiliStepStage
        get() {
            if (!isPabili) return PabiliStepStage.COMPLETED
            val normStatus = status.trim().lowercase(Locale.US)
            val normStep = riderStep.trim().lowercase(Locale.US)

            return when {
                normStatus == OrderStepConstants.STATUS_DELIVERED.lowercase(Locale.US) ||
                    normStatus == OrderStepConstants.STATUS_COMPLETED.lowercase(Locale.US) ||
                    normStep == OrderStepConstants.STEP_COMPLETED ||
                    normStep == OrderStepConstants.STEP_DELIVERED -> PabiliStepStage.COMPLETED

                normStatus == OrderStepConstants.STATUS_ARRIVED_AT_RECIPIENT.lowercase(Locale.US) ||
                    normStep == OrderStepConstants.STEP_AT_RECIPIENT ||
                    normStep == OrderStepConstants.STEP_AT_CUSTOMER ||
                    normStep == OrderStepConstants.STEP_ARRIVED_AT_CUSTOMER -> PabiliStepStage.AT_RECIPIENT

                normStatus == OrderStepConstants.STATUS_RIDER_ON_WAY_RECIPIENT.lowercase(Locale.US) ||
                    normStep == OrderStepConstants.STEP_TO_RECIPIENT ||
                    normStep == OrderStepConstants.STEP_TO_CUSTOMER ||
                    normStep == OrderStepConstants.STEP_HEADING_TO_CUSTOMER ||
                    normStep == OrderStepConstants.STEP_PICKED_UP -> PabiliStepStage.TO_RECIPIENT

                normStatus == OrderStepConstants.STATUS_ARRIVED_AT_VENDOR.lowercase(Locale.US) ||
                    normStep == OrderStepConstants.STEP_AT_VENDOR ||
                    normStep == OrderStepConstants.STEP_ARRIVED_AT_MERCHANT -> PabiliStepStage.AT_VENDOR

                else -> PabiliStepStage.TO_VENDOR
            }
        }

    val isNonMerchantService: Boolean
        get() = isTaxi || serviceType.trim().lowercase() in listOf(
            OrderStepConstants.SERVICE_PARCEL,
            OrderStepConstants.SERVICE_PABILI
        )

    val isMart: Boolean
        get() = serviceType.trim().lowercase().let { it == OrderStepConstants.SERVICE_MART || it == "mart" }

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

    val hasArrivedAtDropoff: Boolean
        get() = arrivedAtDropoffAt != null && (arrivedAtDropoffAt !is String || arrivedAtDropoffAt.isNotBlank())

    val currentTaxiStopNumber: Int
        get() {
            if (!isTaxi) return 2
            val step = riderStep.lowercase()
            if (step.startsWith(OrderStepConstants.STEP_TO_STOP_PREFIX)) {
                return step.removePrefix(OrderStepConstants.STEP_TO_STOP_PREFIX).toIntOrNull() ?: 2
            }
            if (step.startsWith(OrderStepConstants.STEP_AT_STOP_PREFIX)) {
                return step.removePrefix(OrderStepConstants.STEP_AT_STOP_PREFIX).toIntOrNull() ?: 2
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

    val taxiStepStage: TaxiStepStage
        get() {
            if (!isTaxi) return TaxiStepStage.COMPLETED
            val normStatus = status.trim().lowercase(Locale.US)
            val normStep = riderStep.trim().lowercase(Locale.US)

            if (normStatus == OrderStepConstants.STATUS_COMPLETED.lowercase(Locale.US) ||
                normStatus == OrderStepConstants.STATUS_DELIVERED.lowercase(Locale.US) ||
                normStep == OrderStepConstants.STEP_COMPLETED ||
                normStep == OrderStepConstants.STEP_DELIVERED
            ) {
                return TaxiStepStage.COMPLETED
            }

            if (normStatus == OrderStepConstants.STATUS_DRIVER_ARRIVED.lowercase(Locale.US) ||
                normStep == OrderStepConstants.STEP_AT_PICKUP
            ) {
                return TaxiStepStage.AT_PICKUP
            }

            if (normStatus == OrderStepConstants.STATUS_ON_TRIP.lowercase(Locale.US) ||
                normStep.startsWith(OrderStepConstants.STEP_TO_STOP_PREFIX) ||
                normStep.startsWith(OrderStepConstants.STEP_AT_STOP_PREFIX) ||
                pickedUpAt != null
            ) {
                return if (!isAtLastTaxiStop) {
                    if (normStep.startsWith(OrderStepConstants.STEP_AT_STOP_PREFIX)) {
                        TaxiStepStage.AT_EXTRA_STOP
                    } else {
                        TaxiStepStage.TO_EXTRA_STOP
                    }
                } else {
                    if (hasArrivedAtDropoff) {
                        TaxiStepStage.AT_DROPOFF
                    } else {
                        TaxiStepStage.TO_DROPOFF
                    }
                }
            }

            return TaxiStepStage.TO_PICKUP
        }

    val estimatedEarnings: Double
        get() {
            if (isSplitChild && (riderFeeFixed ?: 0.0) > 0.0) {
                return (riderFeeFixed ?: 0.0) + tip + lateTipAmount
            }
            if (riderEarning != null && riderEarning.total > 0.0) {
                return riderEarning.total + lateTipAmount
            }
            if (riderFeeEstimate > 0.0) {
                return riderFeeEstimate + tip + lateTipAmount
            }
            if (isTaxi) {
                // net = fees.total x (1 - commissionPercent/100), default commission 15%
                val commPercent = if (platformCommission > 0.0) platformCommission else 15.0
                return feesTotal * (1.0 - commPercent / 100.0) + tip + lateTipAmount
            }
            val baseDelivery = if (feesDeliveryFee > 0.0) feesDeliveryFee else if (deliveryFee > 0.0) deliveryFee else (baseFare + distanceFare + surgeBonus)
            val netDelivery = if (platformCommission > 0.0) (baseDelivery - platformCommission) else (baseDelivery * 0.8)
            return netDelivery + tip + lateTipAmount + waitingPay + extraStopPay + returnTripPay + urgentBonus
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
        get() = serviceType.equals(OrderStepConstants.SERVICE_REQUEST, ignoreCase = true)

    val isParcel: Boolean
        get() = serviceType.lowercase() == OrderStepConstants.SERVICE_PARCEL

    val paymentType: PaymentType
        get() {
            val effectiveCash = if (isPabili && cashToCollect <= 0.0 && codAmount <= 0.0) {
                feesDeliveryFee + feesServiceFee
            } else {
                cashToCollect
            }
            return parsePaymentType(paymentMethod, effectiveCash, codAmount)
        }

    val isCod: Boolean
        get() = !isSplitChild && paymentType == PaymentType.COD

    val isPaidOnline: Boolean
        get() = paymentType == PaymentType.PAID_ONLINE

    val isPaymentUnknown: Boolean
        get() = false

    val paymentProviderLabel: String
        get() = formatPaymentProvider(paymentProvider)

    val parcelCollectAtSender: Boolean
        get() = serviceType.lowercase() == OrderStepConstants.SERVICE_PARCEL &&
                cashCollectAt.trim().lowercase() == "sender"

    // Section 2: CASH TO COLLECT
    // For COD, the cash to collect is fees.total, which already includes the tip.
    // COD, parcel: cashToCollect, from party in cashCollectAt ("sender" at pickup, "recipient" at delivery).
    // COD, pabili: cashToCollect stays delivery fee + service fee only (never add receipt/paidOnMerchant).
    // request_rider: cashToCollect (or codAmount fallback) collected from recipient.
    // Taxi COD: fees.total
    // Paid Online or wallet: no cash to collect.
    val cashAmountToCollect: Double
        get() {
            if (isSplitChild || paymentType == PaymentType.PAID_ONLINE) {
                return 0.0
            }
            return when (serviceType.lowercase()) {
                OrderStepConstants.SERVICE_TAXI -> if (cashToCollect > 0.0) cashToCollect else feesTotal
                OrderStepConstants.SERVICE_PARCEL -> cashToCollect
                OrderStepConstants.SERVICE_PABILI -> {
                    if (cashToCollect > 0.0) cashToCollect else (feesDeliveryFee + feesServiceFee)
                }
                OrderStepConstants.SERVICE_REQUEST -> if (cashToCollect > 0.0) cashToCollect else codAmount
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

fun parsePaymentType(
    paymentMethod: String?,
    cashToCollect: Double = 0.0,
    codAmount: Double = 0.0
): PaymentType {
    val pm = paymentMethod?.trim()?.lowercase() ?: ""
    return when (pm) {
        "cod", "cash", "cash on delivery" -> PaymentType.COD
        "wallet", "online", "paid", "paid online", "gcash", "maya", "card", "bank" -> PaymentType.PAID_ONLINE
        else -> if (cashToCollect > 0.0 || codAmount > 0.0) PaymentType.COD else PaymentType.PAID_ONLINE
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
    val sendAt: Any? = null,
    val timestampText: String = "",
    val isRead: Boolean = false
) {
    val sendAtMillis: Long
        get() {
            val raw = sendAt ?: createdAt
            return when (raw) {
                is com.google.firebase.Timestamp -> raw.toDate().time
                is Date -> raw.time
                is Number -> {
                    val num = raw.toLong()
                    if (num < 1000000000000L) num * 1000L else num
                }
                is String -> {
                    val trimmed = raw.trim()
                    val num = trimmed.toLongOrNull()
                    if (num != null) {
                        if (num < 1000000000000L) num * 1000L else num
                    } else {
                        val patterns = listOf(
                            "yyyy-MM-dd'T'HH:mm:ss",
                            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                            "yyyy-MM-dd HH:mm:ss",
                            "yyyy-MM-dd"
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
        }

    fun isFuture(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val s = sendAtMillis
        return s > 0L && s > nowMillis
    }
}

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

data class LateTipBannerInfo(
    val orderId: String = "",
    val orderNumber: String = "",
    val amount: Double = 0.0
) {
    val displayOrderNumber: String
        get() {
            val raw = orderNumber.trim()
            return when {
                raw.isNotBlank() -> if (raw.startsWith("#")) raw else "#$raw"
                orderId.isNotBlank() -> "#" + orderId.takeLast(6).uppercase()
                else -> "#000000"
            }
        }
}

data class RiderEarning(
    val bookingFee: Double = 0.0,
    val pickupFee: Double = 0.0,
    val extra: Double = 0.0,
    val tip: Double = 0.0,
    val total: Double = 0.0,
    val rank: Int = 1,
    val batchId: String = ""
)

enum class OfferType {
    SINGLE,
    DOUBLE_BOOKING,
    ADD_TO_BOOKING
}

data class OrderPayBreakdown(
    val orderId: String = "",
    val orderNumber: String = "",
    val rank: Int = 1,
    val deliveryFee: Double = 0.0,
    val pickupFee: Double = 0.0,
    val tip: Double = 0.0,
    val totalPay: Double = 0.0,
    val isCod: Boolean = false,
    val codAmount: Double = 0.0,
    val badge: String = "Standard",
    val customerArea: String = ""
) {
    val displayOrderNumber: String
        get() {
            val raw = orderNumber.trim()
            val num = if (raw.isNotBlank()) {
                raw.removePrefix("#")
            } else {
                orderId.takeLast(6).uppercase().ifBlank { "000000" }
            }
            return "#$num"
        }
}

data class OfferBundle(
    val id: String,
    val type: OfferType,
    val orders: List<Order>,
    val firstPickupDistanceKm: Double,
    val totalTripDistanceKm: Double,
    val pickupFee: Double,
    val breakdowns: List<OrderPayBreakdown>,
    val totalDriverPay: Double,
    val eligibleSince: Long,
    val waveDelaySeconds: Int,
    val badge: String = "Standard",
    val timerSeconds: Int = 30
) {
    val totalStopsCount: Int
        get() {
            // If all taxi, use taxi stops, otherwise each food/convenience order is 2 stops (pickup & dropoff)
            if (orders.size == 1 && orders.first().isTaxi) return orders.first().totalStopsCount
            return orders.size * 2
        }

    val isDoubleBooking: Boolean
        get() = type == OfferType.DOUBLE_BOOKING

    val isAddToBooking: Boolean
        get() = type == OfferType.ADD_TO_BOOKING

    val firstOrder: Order
        get() = orders.first()
}

data class SupportTicket(
    val id: String = "",
    val reporterType: String = "rider",
    val riderId: String = "",
    val riderName: String = "",
    val orderId: String = "",
    val orderNumber: String = "",
    val batchId: String = "",
    val serviceType: String = "",
    val reason: String = "",
    val reasonLabel: String = "",
    val title: String = "",
    val splitItems: List<String> = emptyList(),
    val status: String = "open", // "open", "accepted", "resolved"
    val resolutionNote: String = "",
    val createdAt: com.google.firebase.Timestamp? = null,
    val lastMessageAt: com.google.firebase.Timestamp? = null,
    val lastMessagePreview: String = "",
    val unreadByAdmin: Boolean = false,
    val unreadByRider: Boolean = false
) {
    val displayOrderNumber: String
        get() {
            val raw = orderNumber.trim()
            val num = if (raw.isNotBlank()) {
                raw.removePrefix("#")
            } else {
                orderId.takeLast(6).uppercase().ifBlank { "000000" }
            }
            return "#$num"
        }

    val isOpen: Boolean get() = status.equals("open", ignoreCase = true)
    val isAccepted: Boolean get() = status.equals("accepted", ignoreCase = true)
    val isResolved: Boolean get() = status.equals("resolved", ignoreCase = true)
    val isUnresolved: Boolean get() = isOpen || isAccepted
}

data class SupportTicketMessage(
    val id: String = "",
    val senderType: String = "rider", // "rider", "admin", "agent"
    val senderId: String = "",
    val text: String = "",
    val imageData: String? = null,
    val isInitial: Boolean = false,
    val createdAt: com.google.firebase.Timestamp? = null
) {
    val isAdmin: Boolean get() = senderType.equals("admin", ignoreCase = true) || senderType.equals("agent", ignoreCase = true)
    val isRider: Boolean get() = senderType.equals("rider", ignoreCase = true)
}

data class HelpCenterReason(
    val key: String,
    val label: String,
    val requiresSplitChecklist: Boolean = false
)

object HelpCenterConstants {
    val REASONS = listOf(
        HelpCenterReason("cant_fit_bag", "Can't fit on my bag", requiresSplitChecklist = true),
        HelpCenterReason("too_big", "Order is too big or heavy", requiresSplitChecklist = true),
        HelpCenterReason("accident", "Accident"),
        HelpCenterReason("wrong_items", "Wrong or missing items"),
        HelpCenterReason("merchant_wait", "Merchant is taking too long"),
        HelpCenterReason("merchant_closed", "Merchant is closed"),
        HelpCenterReason("customer_unreachable", "Customer unreachable"),
        HelpCenterReason("wrong_address", "Wrong address or pin"),
        HelpCenterReason("vehicle_breakdown", "Vehicle breakdown"),
        HelpCenterReason("safety", "Safety concern"),
        HelpCenterReason("other", "Other")
    )

    fun getLabelForKey(key: String): String {
        return REASONS.firstOrNull { it.key == key }?.label ?: key.replace("_", " ").replaceFirstChar { it.uppercase() }
    }
}

data class AdminMessageBannerInfo(
    val ticketId: String,
    val ticketTitle: String,
    val senderName: String,
    val messageText: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class QuestGoal(
    val level: Int = 1,
    val target: Double = 0.0,
    val reward: Double = 0.0,
    val minAcceptanceRate: Double? = null,
    val maxNoShows: Int? = null
)

data class Quest(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val status: String = "active", // "active", "completed", "inactive"
    val metric: String = "deliveries", // "deliveries" or "earnings"
    val startAt: com.google.firebase.Timestamp? = null,
    val endAt: com.google.firebase.Timestamp? = null,
    val hourFrom: Int? = null,
    val hourTo: Int? = null,
    val cityIds: List<String> = emptyList(),
    val vehicleTypes: List<String> = emptyList(),
    val riderIds: List<String> = emptyList(),
    val services: List<String> = emptyList(),
    val goals: List<QuestGoal> = emptyList(),
    val createdAt: com.google.firebase.Timestamp? = null
) {
    val startAtMillis: Long get() = startAt?.toDate()?.time ?: 0L
    val endAtMillis: Long get() = endAt?.toDate()?.time ?: 0L

    val sortedGoals: List<QuestGoal>
        get() = goals.sortedBy { it.target }

    val topReward: Double
        get() = sortedGoals.lastOrNull()?.reward ?: 0.0

    val topTarget: Double
        get() = sortedGoals.lastOrNull()?.target ?: 1.0

    fun formattedPeriod(): String {
        val tz = TimeZone.getTimeZone("Asia/Manila")
        val sdf = SimpleDateFormat("EEE, MMM d, HH:mm", Locale.US).apply { timeZone = tz }
        val startStr = if (startAtMillis > 0L) sdf.format(Date(startAtMillis)) else "Start"
        val endStr = if (endAtMillis > 0L) sdf.format(Date(endAtMillis)) else "End"
        return "$startStr - $endStr"
    }

    fun timeLeftText(now: Long = System.currentTimeMillis()): String {
        return when {
            now < startAtMillis -> {
                val diff = startAtMillis - now
                val days = diff / 86400000L
                val hours = (diff / 3600000L) % 24L
                if (days > 0L) "Starts in ${days}d ${hours}h" else "Starts in ${hours}h"
            }
            now in startAtMillis..endAtMillis -> {
                val diff = endAtMillis - now
                val days = diff / 86400000L
                val hours = (diff / 3600000L) % 24L
                val mins = (diff / 60000L) % 60L
                when {
                    days > 0L -> "${days}d ${hours}h left"
                    hours > 0L -> "${hours}h ${mins}m left"
                    else -> "${mins}m left"
                }
            }
            else -> "Ended"
        }
    }

    fun appliesTo(rider: Rider?): Boolean {
        if (rider == null) return false
        return appliesTo(rider.cityId, rider.vehicle.type, rider.uid, rider.allowedServices)
    }

    fun appliesTo(
        cityId: String,
        vehicleType: String,
        riderId: String,
        allowedServices: List<String>
    ): Boolean {
        if (cityIds.isNotEmpty() && cityId.isNotBlank() && !cityIds.contains(cityId)) {
            return false
        }
        if (vehicleTypes.isNotEmpty()) {
            val riderVeh = OrderStepConstants.normalizeVehicleType(vehicleType)
            val matched = vehicleTypes.any { OrderStepConstants.normalizeVehicleType(it) == riderVeh }
            if (!matched) return false
        }
        if (riderIds.isNotEmpty() && !riderIds.contains(riderId)) {
            return false
        }
        if (services.isNotEmpty()) {
            val hasService = services.any { svc ->
                allowedServices.any { it.equals(svc, ignoreCase = true) }
            }
            if (!hasService) return false
        }
        return true
    }
}

data class QuestClaim(
    val id: String = "",
    val questId: String = "",
    val questTitle: String = "",
    val riderId: String = "",
    val riderName: String = "",
    val metric: String = "deliveries",
    val value: Double = 0.0,
    val deliveries: Int = 0,
    val earnings: Double = 0.0,
    val acceptanceRate: Double = 100.0,
    val noShows: Int = 0,
    val levelReached: Int = -1,
    val rewardAmount: Double = 0.0,
    val status: String = "Pending", // "Pending", "Credited", "Rejected", "Not achieved"
    val note: String = "",
    val createdAt: com.google.firebase.Timestamp? = null
)

data class QuestProgress(
    val quest: Quest,
    val currentValue: Double,
    val currentDeliveries: Int,
    val currentEarnings: Double,
    val acceptanceRate: Double,
    val noShows: Int,
    val levelReachedIndex: Int,
    val currentReward: Double,
    val nextGoal: QuestGoal?,
    val claim: QuestClaim? = null
) {
    val isCompleted: Boolean get() = levelReachedIndex >= 0 && levelReachedIndex == quest.sortedGoals.size - 1
    val fraction: Float
        get() {
            val top = quest.topTarget
            return if (top > 0.0) (currentValue / top).coerceIn(0.0, 1.0).toFloat() else 0f
        }
}

fun formatGoalDescription(goal: QuestGoal, quest: Quest, currencySymbol: String = "₱"): String {
    val actionText = if (quest.metric.lowercase().contains("earning")) {
        "Earn $currencySymbol${String.format(Locale.US, "%.0f", goal.target)}"
    } else {
        "Complete ${goal.target.toInt()} deliveries"
    }

    val rewardText = "for $currencySymbol${String.format(Locale.US, "%.2f", goal.reward).replace(".00", "")}"

    val parts = mutableListOf<String>()
    parts.add("$actionText $rewardText")

    val conditions = mutableListOf<String>()
    if (goal.minAcceptanceRate != null && goal.minAcceptanceRate > 0.0) {
        val rateInt = goal.minAcceptanceRate.toInt()
        conditions.add("a $rateInt% acceptance rate")
    }
    if (goal.maxNoShows != null) {
        conditions.add("${goal.maxNoShows} no-shows")
    }

    if (conditions.isNotEmpty()) {
        parts.add("with " + conditions.joinToString(" and "))
    }

    if (quest.hourFrom != null && quest.hourTo != null) {
        val hFrom = String.format(Locale.US, "%02d:00", quest.hourFrom)
        val hTo = String.format(Locale.US, "%02d:00", quest.hourTo)
        parts.add("between $hFrom - $hTo")
    }

    if (quest.services.isNotEmpty()) {
        val sText = quest.services.joinToString(" or ") { OrderStepConstants.getServiceDisplayName(it) }
        parts.add("on $sText")
    }

    return parts.joinToString(" ")
}

fun sanitizeDisplayName(raw: String?, fallback: String = ""): String {
    val trimmed = (raw ?: "").trim()
    if (trimmed.isBlank()) return fallback
    if (trimmed.contains("@")) return fallback
    return trimmed
}

fun deduplicateAddress(raw: String?): String {
    val input = (raw ?: "").trim()
    if (input.isBlank()) return ""
    val parts = input.split(",").map { it.trim() }.filter { it.isNotBlank() }
    if (parts.isEmpty()) return input
    val seen = LinkedHashSet<String>()
    val result = mutableListOf<String>()
    for (part in parts) {
        val key = part.lowercase().replace("\\s+".toRegex(), " ")
        if (seen.add(key)) {
            result.add(part)
        }
    }
    return result.joinToString(", ")
}

fun formatMerchantDisplayName(
    merchantName: String,
    branchName: String = "",
    branchCode: String = ""
): String {
    val base = sanitizeDisplayName(merchantName, "")
    val branch = sanitizeDisplayName(branchName, "")
    val code = sanitizeDisplayName(branchCode, "")
    if (base.isBlank()) return ""
    val withBranch = if (branch.isNotBlank() &&
        !base.endsWith(" - $branch", ignoreCase = true) &&
        !base.contains(" - $branch (", ignoreCase = true)
    ) {
        "$base - $branch"
    } else {
        base
    }
    return if (code.isNotBlank() && !withBranch.endsWith("($code)", ignoreCase = true)) {
        "$withBranch ($code)"
    } else {
        withBranch
    }
}

enum class ListenerConnectionState {
    CONNECTED,
    RESTARTING,
    DISCONNECTED
}

data class DispatchListenerStatus(
    val state: ListenerConnectionState = ListenerConnectionState.CONNECTED,
    val lastUpdateMillis: Long = 0L,
    val restartAttempt: Int = 0,
    val nextRetrySeconds: Int = 0,
    val lastError: String? = null
)

data class SkippedOrderRecord(
    val orderId: String,
    val displayLabel: String,
    val reason: String,
    val timestampMillis: Long = System.currentTimeMillis(),
    val isBadData: Boolean = false
) {
    val formattedLine: String
        get() = "$displayLabel: $reason"

    companion object {
        fun labelForOrder(order: Order): String {
            return if (order.isTaxi) "Taxi" else "Order ${order.displayOrderNumber}"
        }

        fun labelForBadDoc(docId: String, orderNumberHint: String, serviceTypeHint: String): String {
            if (serviceTypeHint.equals(OrderStepConstants.SERVICE_TAXI, ignoreCase = true)) {
                return "Taxi"
            }
            val raw = orderNumberHint.trim().ifBlank { docId.takeLast(6).uppercase(Locale.US) }
            val prefixed = if (raw.startsWith("#")) raw else "#$raw"
            return "Order $prefixed"
        }
    }
}

data class TaxiConditionCheck(
    val label: String,
    val passed: Boolean,
    val detail: String = ""
)

data class TaxiOrderDiagnostics(
    val orderId: String,
    val orderLabel: String,
    val evaluatedAtMillis: Long = System.currentTimeMillis(),
    val conditions: List<TaxiConditionCheck> = emptyList()
)





