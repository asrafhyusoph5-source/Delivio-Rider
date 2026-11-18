package com.example.data.models

import android.util.Log
import com.example.data.constants.OrderStepConstants
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Tolerant Firestore field & timestamp readers + per-document order parser.
 * Accepts missing fields or differing scalar types with safe defaults, and throws
 * a descriptive exception when a date field has an invalid non-time type so callers
 * can skip only the bad document without failing the rest of the order list.
 */
object OrderDocumentParser {
    const val TAG = "DISPATCH"

    private val ISO_AND_DATE_PATTERNS = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
        "yyyy-MM-dd'T'HH:mm:ssZ",
        "yyyy-MM-dd'T'HH:mm:ss.SSS",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd",
        "MMM d, yyyy h:mm a",
        "MMM d, yyyy",
        "MMM d, h:mm a",
        "MM/dd/yyyy"
    )

    private val LITERAL_RECENT_WORDS = setOf("recently", "just now", "now", "pending")

    /**
     * Tolerant time reader accepting Timestamp, Date, Number (epoch sec/ms),
     * ISO-8601 / common date text, `{seconds, nanoseconds}` Map, or missing (null/blank).
     * Throws IllegalArgumentException for wrong/unparseable date types (e.g. Boolean, List,
     * unrelated Map, or malformed date text).
     */
    fun parseTolerantTimeMillis(
        raw: Any?,
        fieldName: String = "timestamp",
        allowLiteralRecentWords: Boolean = false,
        allowFalseBoolean: Boolean = false
    ): Long? {
        if (raw == null) return null
        return when (raw) {
            is Timestamp -> raw.toDate().time
            is Date -> raw.time
            is Number -> {
                val asDouble = raw.toDouble()
                if (asDouble.isNaN() || asDouble.isInfinite()) {
                    throw IllegalArgumentException("Invalid numeric $fieldName")
                }
                val num = raw.toLong()
                if (num <= 0L) 0L else if (num < 1_000_000_000_000L) num * 1000L else num
            }
            is Map<*, *> -> {
                val secRaw = raw["seconds"] ?: raw["_seconds"]
                if (secRaw != null) {
                    val sec = safeDouble(secRaw)
                        ?: throw IllegalArgumentException("Invalid map seconds for $fieldName")
                    val nanos = safeDouble(raw["nanoseconds"] ?: raw["_nanoseconds"]) ?: 0.0
                    (sec * 1000.0).toLong() + (nanos / 1_000_000.0).toLong()
                } else {
                    throw IllegalArgumentException("Wrong date type (Map) for $fieldName")
                }
            }
            is String -> {
                val trimmed = raw.trim()
                if (trimmed.isEmpty()) return null
                if (allowLiteralRecentWords && trimmed.lowercase(Locale.US) in LITERAL_RECENT_WORDS) {
                    return null
                }
                val num = trimmed.toLongOrNull()
                if (num != null) {
                    return if (num <= 0L) 0L else if (num < 1_000_000_000_000L) num * 1000L else num
                }
                try {
                    return Instant.parse(trimmed).toEpochMilli()
                } catch (_: Throwable) {
                }
                try {
                    return OffsetDateTime.parse(trimmed).toInstant().toEpochMilli()
                } catch (_: Throwable) {
                }
                for (pattern in ISO_AND_DATE_PATTERNS) {
                    try {
                        val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                            isLenient = false
                            if (pattern.endsWith("'Z'") || pattern.endsWith("Z")) {
                                timeZone = TimeZone.getTimeZone("UTC")
                            }
                        }
                        val parsed = sdf.parse(trimmed)
                        if (parsed != null) return parsed.time
                    } catch (_: Throwable) {
                    }
                }
                throw IllegalArgumentException("Wrong date format for $fieldName: '$trimmed'")
            }
            is Boolean -> {
                if (allowFalseBoolean && !raw) null
                else throw IllegalArgumentException("Wrong date type (Boolean) for $fieldName")
            }
            else -> throw IllegalArgumentException(
                "Wrong date type (${raw::class.java.simpleName}) for $fieldName"
            )
        }
    }

    /**
     * Non-throwing variant for model convenience getters (e.g. `Order.deliveredAtMillis`).
     */
    fun parseTolerantTimeMillisOrZero(raw: Any?): Long {
        return try {
            parseTolerantTimeMillis(raw, allowLiteralRecentWords = true, allowFalseBoolean = true) ?: 0L
        } catch (_: Throwable) {
            0L
        }
    }

    fun formatScheduledDate(raw: Any?): String {
        if (raw == null || raw == false) return ""
        val millis = try {
            parseTolerantTimeMillis(raw, fieldName = "scheduledFor", allowFalseBoolean = true)
        } catch (_: Throwable) {
            null
        }
        if (millis != null && millis > 0L) {
            return SimpleDateFormat("MMM d, h:mm a", Locale.US).format(Date(millis))
        }
        return (raw as? String)?.trim() ?: ""
    }

    fun formatPlacedAtDisplay(raw: Any?): String {
        if (raw == null) return "Recently"
        val millis = parseTolerantTimeMillis(
            raw,
            fieldName = "placedAt",
            allowLiteralRecentWords = true
        )
        if (millis != null && millis > 0L) {
            return SimpleDateFormat("MMM d, h:mm a", Locale.US).format(Date(millis))
        }
        return (raw as? String)?.trim()?.takeIf { it.isNotBlank() } ?: "Recently"
    }

    fun safeString(raw: Any?, default: String = ""): String {
        return when (raw) {
            null -> default
            is String -> raw
            is Number, is Boolean -> raw.toString()
            else -> default
        }
    }

    fun safeStringOrNull(raw: Any?): String? {
        return when (raw) {
            null -> null
            is String -> raw
            is Number, is Boolean -> raw.toString()
            else -> null
        }
    }

    fun safeDouble(raw: Any?): Double? {
        return when (raw) {
            is Number -> {
                val d = raw.toDouble()
                if (d.isNaN() || d.isInfinite()) null else d
            }
            is String -> {
                val d = raw.trim().toDoubleOrNull()
                if (d != null && (d.isNaN() || d.isInfinite())) null else d
            }
            else -> null
        }
    }

    fun safeInt(raw: Any?): Int? {
        return when (raw) {
            is Number -> raw.toInt()
            is String -> raw.trim().toIntOrNull() ?: raw.trim().toDoubleOrNull()?.toInt()
            else -> null
        }
    }

    fun safeBoolean(raw: Any?): Boolean? {
        return when (raw) {
            is Boolean -> raw
            is Number -> raw.toInt() != 0
            is String -> when (raw.trim().lowercase(Locale.US)) {
                "true", "1", "yes" -> true
                "false", "0", "no" -> false
                else -> null
            }
            else -> null
        }
    }

    fun safeMap(raw: Any?): Map<*, *>? = raw as? Map<*, *>

    fun safeList(raw: Any?): List<*>? = raw as? List<*>

    private fun firstValidDisplayName(vararg candidates: Any?, fallback: String = ""): String {
        for (c in candidates) {
            val s = safeStringOrNull(c)?.trim() ?: continue
            if (s.isBlank()) continue
            if (s.contains("@")) continue
            if (s.equals("Merchant", ignoreCase = true) || s.equals("Passenger Pickup", ignoreCase = true)) continue
            return s
        }
        return fallback
    }

    fun parseOrderFromMap(
        id: String,
        data: Map<String, Any?>?,
        scheduledReleaseLeadMinutes: Double = 0.0,
        fallbackMerchant: Map<String, Any?>? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): Order {
        if (data == null) {
            throw IllegalStateException("Order document $id has no data")
        }

        val orderNumber = safeStringOrNull(data["orderNumber"])?.takeIf { it.isNotBlank() }
            ?: safeStringOrNull(data["orderId"])?.takeIf { it.isNotBlank() }
            ?: id.takeLast(6).uppercase(Locale.US)
        val serviceType = safeString(data["serviceType"], OrderStepConstants.SERVICE_FOOD)
            .ifBlank { OrderStepConstants.SERVICE_FOOD }
        val status = safeString(data["status"], OrderStepConstants.STATUS_PLACED_ORDER)
            .ifBlank { OrderStepConstants.STATUS_PLACED_ORDER }
        val riderStep = safeString(data["riderStep"], OrderStepConstants.STEP_HEADING_TO_MERCHANT)
            .ifBlank { OrderStepConstants.STEP_HEADING_TO_MERCHANT }
        val riderId = safeStringOrNull(data["riderId"])?.takeIf { it.isNotBlank() }
        val riderName = safeStringOrNull(data["riderName"])
        val riderPhone = safeStringOrNull(data["riderPhone"])
        val riderPhotoUrl = safeStringOrNull(data["riderPhotoUrl"])
        val cityId = safeString(data["cityId"])
        val merchantId = safeString(data["merchantId"])

        val fulfillment = safeStringOrNull(data["fulfillment"])
            ?: safeStringOrNull(data["fulfillmentType"])
            ?: "delivery"

        val scheduledForRaw = data["scheduledFor"]
            ?: data["scheduledDeliveryAt"]
            ?: data["scheduledTime"]
            ?: data["scheduleDate"]
        val schedMillis = parseTolerantTimeMillis(
            scheduledForRaw,
            fieldName = "scheduledFor",
            allowFalseBoolean = true
        ) ?: 0L
        val scheduledDateText = formatScheduledDate(scheduledForRaw)
        val isScheduled = safeBoolean(data["isScheduled"])
            ?: (safeStringOrNull(data["deliveryType"])?.equals("scheduled", ignoreCase = true) == true
                || safeStringOrNull(data["orderType"])?.equals("scheduled", ignoreCase = true) == true
                || schedMillis > 0L)

        val merchantAccepted = safeBoolean(data["merchantAccepted"])
            ?: (status.equals(OrderStepConstants.STATUS_PREPARING, ignoreCase = true) ||
                status.equals(OrderStepConstants.STATUS_READY, ignoreCase = true))

        val prepMinutes = safeInt(data["prepMinutes"])
            ?: safeInt(data["preparationMinutes"])
            ?: safeInt(data["estimatedPrepTime"])
            ?: safeInt(data["prepTime"])
            ?: 0

        val editedAt = data["editedAt"] ?: data["merchantEditedAt"]

        // Pickup / merchant coordinates & metadata (supporting taxi, parcel, pabili, and merchant orders)
        val pickupMap = safeMap(data["pickup"])
            ?: safeMap(data["pickupLocation"])
            ?: safeMap(data["origin"])
            ?: safeMap(data["sender"])
            ?: safeMap(data["merchant"])

        val stopsRaw = safeList(data["stops"])
        var stop1PickupLat = 0.0
        var stop1PickupLng = 0.0
        var stop1PickupAddress = ""
        val taxiStops = mutableListOf<TaxiStop>()
        stopsRaw?.forEachIndexed { index, obj ->
            val m = safeMap(obj) ?: return@forEachIndexed
            val stopNum = safeInt(m["stopNumber"]) ?: (index + 3)
            val stopType = safeString(m["type"]).lowercase(Locale.US)
            val sAddr = deduplicateAddress(safeString(m["address"]))
            val sArea = deduplicateAddress(safeString(m["area"]))
            val sLat = safeDouble(m["lat"]) ?: safeDouble(m["latitude"]) ?: 0.0
            val sLng = safeDouble(m["lng"]) ?: safeDouble(m["longitude"]) ?: 0.0
            if (stopNum == 1 || stopType == "pickup") {
                if (stop1PickupLat == 0.0) stop1PickupLat = sLat
                if (stop1PickupLng == 0.0) stop1PickupLng = sLng
                if (stop1PickupAddress.isBlank()) stop1PickupAddress = sAddr
            } else {
                taxiStops.add(
                    TaxiStop(
                        stopNumber = stopNum,
                        address = sAddr,
                        area = sArea,
                        lat = sLat,
                        lng = sLng
                    )
                )
            }
        }

        val rawMerchantLat = safeDouble(pickupMap?.get("lat"))
            ?: safeDouble(pickupMap?.get("latitude"))
            ?: safeDouble(data["pickupLat"])
            ?: safeDouble(data["senderLat"])
            ?: safeDouble(data["originLat"])
            ?: safeDouble(data["merchantLat"])
            ?: stop1PickupLat
        val rawMerchantLng = safeDouble(pickupMap?.get("lng"))
            ?: safeDouble(pickupMap?.get("longitude"))
            ?: safeDouble(data["pickupLng"])
            ?: safeDouble(data["senderLng"])
            ?: safeDouble(data["originLng"])
            ?: safeDouble(data["merchantLng"])
            ?: stop1PickupLng

        val parcelMapPre = safeMap(data["parcel"])
        val pabiliMapPre = safeMap(data["pabili"])
        val vendorMapPre = safeMap(data["vendor"]) ?: safeMap(pabiliMapPre?.get("vendor"))
        val merchantObjMap = safeMap(data["merchant"])
        val rawMerchantName = firstValidDisplayName(
            pickupMap?.get("name"),
            pickupMap?.get("vendorName"),
            data["merchantName"],
            merchantObjMap?.get("name"),
            data["shopName"],
            data["storeName"],
            data["vendorName"],
            pabiliMapPre?.get("vendorName"),
            vendorMapPre?.get("name"),
            fallback = ""
        )
        val rawSenderName = firstValidDisplayName(
            pickupMap?.get("senderName"),
            data["senderName"],
            parcelMapPre?.get("senderName"),
            safeMap(data["sender"])?.get("name"),
            pickupMap?.get("contactName"),
            fallback = ""
        )
        val rawBranchName = sanitizeDisplayName(
            safeStringOrNull(data["branchName"]) ?: safeStringOrNull(pickupMap?.get("branchName")),
            ""
        )
        val rawBranchCode = sanitizeDisplayName(
            safeStringOrNull(data["branchCode"]) ?: safeStringOrNull(pickupMap?.get("branchCode")),
            ""
        )
        val rawChainId = (safeStringOrNull(data["chainId"])?.trim()?.takeIf { it.isNotBlank() }
            ?: safeStringOrNull(pickupMap?.get("chainId"))?.trim()?.takeIf { it.isNotBlank() }
            ?: "")
        val pickupStructuredAddr = listOfNotNull(
            safeStringOrNull(pickupMap?.get("street"))?.takeIf { it.isNotBlank() },
            safeStringOrNull(pickupMap?.get("barangay"))?.takeIf { it.isNotBlank() },
            safeStringOrNull(pickupMap?.get("city"))?.takeIf { it.isNotBlank() }
        ).joinToString(", ")
        val rawMerchantAddress = deduplicateAddress(
            safeStringOrNull(pickupMap?.get("address"))
                ?: safeStringOrNull(data["pickupAddress"])
                ?: safeStringOrNull(data["merchantAddress"])
                ?: safeStringOrNull(data["senderAddress"])
                ?: stop1PickupAddress.ifBlank { null }
                ?: pickupStructuredAddr
        )
        val rawMerchantPhone = safeStringOrNull(pickupMap?.get("phone"))
            ?: safeStringOrNull(data["pickupPhone"])
            ?: safeStringOrNull(data["merchantPhone"])
            ?: safeStringOrNull(data["senderPhone"])
            ?: ""

        val normService = serviceType.lowercase(Locale.US).trim()
        val isNonMerchant = normService == OrderStepConstants.SERVICE_TAXI ||
            normService == OrderStepConstants.SERVICE_PARCEL ||
            normService == OrderStepConstants.SERVICE_PABILI
        val cachedMerchant = if (!isNonMerchant && merchantId.isNotBlank()) fallbackMerchant else null

        val merchantLat = if (rawMerchantLat != 0.0) rawMerchantLat else (safeDouble(cachedMerchant?.get("lat")) ?: 0.0)
        val merchantLng = if (rawMerchantLng != 0.0) rawMerchantLng else (safeDouble(cachedMerchant?.get("lng")) ?: 0.0)
        val merchantName = when {
            rawMerchantName.isNotBlank() && !rawMerchantName.equals("Merchant", ignoreCase = true) -> rawMerchantName
            else -> sanitizeDisplayName(safeStringOrNull(cachedMerchant?.get("name")), "").ifBlank { rawMerchantName }
        }
        val branchName = if (rawBranchName.isNotBlank() || rawBranchCode.isNotBlank()) {
            rawBranchName
        } else {
            sanitizeDisplayName(safeStringOrNull(cachedMerchant?.get("branchName")), "")
        }
        val branchCode = if (rawBranchName.isNotBlank() || rawBranchCode.isNotBlank()) {
            rawBranchCode
        } else {
            sanitizeDisplayName(safeStringOrNull(cachedMerchant?.get("branchCode")), "")
        }
        val chainId = if (rawChainId.isNotBlank()) {
            rawChainId
        } else {
            safeString(cachedMerchant?.get("chainId"))
        }
        val merchantAddress = rawMerchantAddress.ifBlank {
            deduplicateAddress(safeString(cachedMerchant?.get("address")))
        }
        val merchantPhone = rawMerchantPhone.ifBlank { safeString(cachedMerchant?.get("phone")) }

        // Dropoff / customer
        val dropoffMap = safeMap(data["dropoff"])
            ?: safeMap(data["dropOff"])
            ?: safeMap(data["customer"])
        val customerLat = safeDouble(dropoffMap?.get("lat"))
            ?: safeDouble(dropoffMap?.get("latitude"))
            ?: safeDouble(data["dropoffLat"])
            ?: safeDouble(data["customerLat"])
            ?: 0.0
        val customerLng = safeDouble(dropoffMap?.get("lng"))
            ?: safeDouble(dropoffMap?.get("longitude"))
            ?: safeDouble(data["dropoffLng"])
            ?: safeDouble(data["customerLng"])
            ?: 0.0
        val customerId = safeStringOrNull(dropoffMap?.get("customerId"))
            ?: safeStringOrNull(dropoffMap?.get("uid"))
            ?: safeString(data["customerId"])
        val taxiMap = safeMap(data["taxi"]) ?: safeMap(data["ride"])
        val passengerMap = safeMap(data["passenger"])
        val customerMap = safeMap(data["customer"])
        val isTaxiService = normService == OrderStepConstants.SERVICE_TAXI
        val customerName = if (isTaxiService) {
            firstValidDisplayName(
                taxiMap?.get("passengerName"),
                data["passengerName"],
                pickupMap?.get("passengerName"),
                passengerMap?.get("name"),
                dropoffMap?.get("name"),
                data["customerName"],
                customerMap?.get("name"),
                data["userName"],
                data["recipientName"],
                pickupMap?.get("name"),
                fallback = "Passenger"
            )
        } else {
            firstValidDisplayName(
                dropoffMap?.get("recipientName"),
                dropoffMap?.get("name"),
                data["recipientName"],
                data["customerName"],
                customerMap?.get("name"),
                fallback = "Customer"
            )
        }
        val pickupNote = (
            safeStringOrNull(data["pickupNote"])
                ?: safeStringOrNull(data["pickupNotes"])
                ?: safeStringOrNull(pickupMap?.get("note"))
                ?: safeStringOrNull(pickupMap?.get("notes"))
                ?: safeStringOrNull(pickupMap?.get("instructions"))
                ?: safeStringOrNull(taxiMap?.get("note"))
                ?: safeStringOrNull(taxiMap?.get("pickupNote"))
                ?: ""
            ).trim()
        val passengerCount = (
            safeInt(data["passengerCount"])
                ?: safeInt(taxiMap?.get("passengerCount"))
                ?: safeInt(data["passengers"])
                ?: safeInt(taxiMap?.get("passengers"))
                ?: safeInt(data["pax"])
                ?: safeInt(taxiMap?.get("pax"))
                ?: safeInt(pickupMap?.get("passengerCount"))
                ?: 1
            ).coerceAtLeast(1)
        val dropoffStructuredAddr = listOfNotNull(
            safeStringOrNull(dropoffMap?.get("street"))?.takeIf { it.isNotBlank() },
            safeStringOrNull(dropoffMap?.get("barangay"))?.takeIf { it.isNotBlank() },
            safeStringOrNull(dropoffMap?.get("city"))?.takeIf { it.isNotBlank() }
        ).joinToString(", ")
        val customerAddress = deduplicateAddress(
            safeStringOrNull(dropoffMap?.get("address"))
                ?: safeStringOrNull(data["dropoffAddress"])
                ?: safeStringOrNull(data["customerAddress"])
                ?: safeStringOrNull(data["recipientAddress"])
                ?: dropoffStructuredAddr
        )
        val customerPhone = safeStringOrNull(dropoffMap?.get("phone"))
            ?: safeStringOrNull(data["customerPhone"])
            ?: safeStringOrNull(data["recipientPhone"])
            ?: ""
        val customerArea = deduplicateAddress(
            safeStringOrNull(dropoffMap?.get("area"))
                ?: safeStringOrNull(data["customerArea"])
                ?: safeStringOrNull(data["recipientArea"])
                ?: "Downtown"
        )
        val customerNotes = safeStringOrNull(dropoffMap?.get("notes"))
            ?: safeString(data["customerNotes"])

        // Items
        val items = safeList(data["items"])?.mapNotNull { itemObj ->
            val m = safeMap(itemObj) ?: return@mapNotNull null
            val selectionsRaw = m["selections"] ?: m["options"] ?: m["modifiers"]
            val selectionsList = mutableListOf<String>()
            when (selectionsRaw) {
                is List<*> -> selectionsRaw.forEach { sel ->
                    when (sel) {
                        is String -> sel.replace("()", "").trim().takeIf { it.isNotBlank() }?.let { selectionsList.add(it) }
                        is Map<*, *> -> {
                            val selName = safeStringOrNull(sel["name"])
                                ?: safeStringOrNull(sel["title"])
                                ?: safeStringOrNull(sel["optionName"])
                                ?: safeStringOrNull(sel["value"])
                                ?: ""
                            selName.replace("()", "").trim().takeIf { it.isNotBlank() }?.let { selectionsList.add(it) }
                        }
                    }
                }
                is Map<*, *> -> selectionsRaw.values.forEach { v ->
                    when (v) {
                        is String -> v.replace("()", "").trim().takeIf { it.isNotBlank() }?.let { selectionsList.add(it) }
                        is List<*> -> v.forEach { sub ->
                            val subStr = when (sub) {
                                is String -> sub
                                is Map<*, *> -> safeStringOrNull(sub["name"]) ?: safeStringOrNull(sub["title"]) ?: ""
                                else -> safeString(sub)
                            }
                            subStr.replace("()", "").trim().takeIf { it.isNotBlank() }?.let { selectionsList.add(it) }
                        }
                    }
                }
                is String -> if (selectionsRaw.isNotBlank()) {
                    selectionsRaw.split(",", ";").forEach { s ->
                        s.replace("()", "").trim().takeIf { it.isNotBlank() }?.let { selectionsList.add(it) }
                    }
                }
            }

            val unitPrice = safeDouble(m["unitPrice"])
            val regularUnitPrice = safeDouble(m["regularUnitPrice"])
            val parsedPrice = unitPrice ?: safeDouble(m["price"]) ?: 0.0

            OrderItem(
                name = safeStringOrNull(m["name"]) ?: safeString(m["title"]),
                quantity = safeInt(m["quantity"]) ?: 1,
                price = parsedPrice,
                specialInstructions = safeString(m["specialInstructions"]),
                selections = selectionsList,
                variantKey = safeString(m["variantKey"]).trim(),
                variantLabel = safeString(m["variantLabel"]).trim(),
                variantImageUrl = safeString(m["variantImageUrl"]).trim(),
                unitPrice = unitPrice,
                regularUnitPrice = regularUnitPrice
            )
        } ?: emptyList()

        // Fees & Pricing
        val baseFare = safeDouble(data["baseFare"]) ?: 50.0
        val distanceFare = safeDouble(data["distanceFare"]) ?: 30.0
        val feesMap = safeMap(data["fees"])
        val tip = safeDouble(feesMap?.get("tip")) ?: safeDouble(data["tip"]) ?: 0.0
        val surgeBonus = safeDouble(data["surgeBonus"]) ?: 0.0
        val feesDeliveryFee = safeDouble(feesMap?.get("deliveryFee"))
            ?: safeDouble(data["deliveryFee"])
            ?: (baseFare + distanceFare + surgeBonus)
        val feesServiceFee = safeDouble(feesMap?.get("serviceFee")) ?: safeDouble(data["serviceFee"]) ?: 0.0
        val feesTotal = safeDouble(feesMap?.get("total"))
            ?: safeDouble(data["totalAmount"])
            ?: safeDouble(data["totalFee"])
            ?: (feesDeliveryFee + feesServiceFee + tip)

        val paymentMethod = safeStringOrNull(data["paymentMethod"])
            ?: safeStringOrNull(data["paymentType"])
            ?: ""
        val paymentProvider = safeString(data["paymentProvider"])
        val cashToCollect = safeDouble(data["cashToCollect"])
            ?: safeDouble(data["totalAmountToCollect"])
            ?: 0.0
        val cashCollectAt = safeString(data["cashCollectAt"])
        val codAmount = safeDouble(data["codAmount"]) ?: 0.0
        val codCollected = safeDouble(data["codCollected"]) ?: 0.0

        // Parcel
        val parcelMap = safeMap(data["parcel"])
        val parcelSize = safeStringOrNull(parcelMap?.get("size")) ?: safeString(data["parcelSize"], "M")
        val parcelWeightKg = safeDouble(parcelMap?.get("weightKg")) ?: safeDouble(data["parcelWeightKg"]) ?: 1.0
        val parcelItemDesc = safeStringOrNull(parcelMap?.get("itemDescription")) ?: safeString(data["parcelItemDescription"])
        val parcelFragile = safeBoolean(parcelMap?.get("fragile")) ?: safeBoolean(data["parcelFragile"]) ?: false
        val parcelFeePayer = safeStringOrNull(parcelMap?.get("feePayer")) ?: safeString(data["parcelFeePayer"], "SENDER")

        // Pabili
        val pabiliMap = safeMap(data["pabili"])
        val pabiliItemsRaw = safeList(pabiliMap?.get("items")) ?: safeList(data["pabiliItems"])
        val pabiliItems = pabiliItemsRaw?.mapNotNull { itm ->
            val m = safeMap(itm) ?: return@mapNotNull null
            PabiliItem(
                product = safeStringOrNull(m["product"]) ?: safeString(m["name"]),
                quantity = safeInt(m["quantity"]) ?: 1,
                estimatedPrice = safeDouble(m["estimatedPrice"]) ?: 0.0
            )
        } ?: emptyList()
        val pabiliEstimatedTotal = safeDouble(pabiliMap?.get("estimatedTotal")) ?: safeDouble(data["pabiliEstimatedTotal"]) ?: 0.0
        val budgetLimit = safeDouble(data["budgetLimit"])
            ?: safeDouble(pabiliMap?.get("budgetLimit"))
            ?: safeDouble(pabiliMap?.get("budget"))
            ?: safeDouble(data["pabiliBudget"])
            ?: 0.0
        val pabiliBudget = safeDouble(pabiliMap?.get("budget")) ?: safeDouble(data["pabiliBudget"]) ?: budgetLimit
        val paidOnMerchant = safeDouble(data["paidOnMerchant"]) ?: safeDouble(pabiliMap?.get("paidOnMerchant")) ?: 0.0
        val finalReceiptTotal = safeDouble(data["finalReceiptTotal"]) ?: paidOnMerchant
        val paidOnMerchantAtRaw = (data["paidOnMerchantAt"] ?: pabiliMap?.get("paidOnMerchantAt"))
            ?.takeUnless { it is String && it.isBlank() }
        parseTolerantTimeMillis(paidOnMerchantAtRaw, fieldName = "paidOnMerchantAt")
        val overBudgetApproved = safeBoolean(data["overBudgetApproved"])
            ?: safeBoolean(pabiliMap?.get("overBudgetApproved"))
            ?: false
        val resolvedItems = if (items.isNotEmpty()) {
            items
        } else {
            pabiliItems.map { pItem ->
                OrderItem(
                    name = pItem.product,
                    quantity = pItem.quantity,
                    price = pItem.estimatedPrice
                )
            }
        }

        val riderReqMap = safeMap(data["riderRequest"])
        val riderReqStatus = safeStringOrNull(riderReqMap?.get("status")) ?: safeString(data["riderRequestStatus"])

        // Taxi vehicleType fallback checks
        val vehicleMap = safeMap(data["vehicle"])
        val vehicleType = safeStringOrNull(data["vehicleType"])?.takeIf { it.isNotBlank() }
            ?: safeStringOrNull(taxiMap?.get("vehicleType"))?.takeIf { it.isNotBlank() }
            ?: safeStringOrNull(vehicleMap?.get("type"))?.takeIf { it.isNotBlank() }
            ?: OrderStepConstants.VEHICLE_MOTORCYCLE
        val completedStopsRaw = safeList(data["completedStops"])?.mapNotNull { safeInt(it) } ?: emptyList()

        val lateTipMap = safeMap(data["lateTip"])
        val lateTipAmount = safeDouble(lateTipMap?.get("amount"))
            ?: safeDouble(data["lateTip.amount"])
            ?: safeDouble(data["lateTipAmount"])
            ?: 0.0
        val lateTipAtRaw = lateTipMap?.get("createdAt") ?: lateTipMap?.get("at") ?: data["lateTipAt"]
        // Validate lateTipAt if present
        parseTolerantTimeMillis(lateTipAtRaw, fieldName = "lateTipAt")

        val badge = safeString(data["badge"], "Standard").ifBlank { "Standard" }
        val splitParentOrderId = safeStringOrNull(data["splitParentOrderId"])?.takeIf { it.isNotBlank() }
        val isSplitChild = safeBoolean(data["isSplitChild"])
            ?: (badge.equals("split", ignoreCase = true) || !splitParentOrderId.isNullOrBlank())
        val finalBadge = if (isSplitChild) "SPLIT" else badge

        val splitOutRaw = data["splitOut"] ?: data["splitItems"]
        val splitOutInfo = safeMap(splitOutRaw)?.let { m ->
            val count = safeInt(m["count"]) ?: 0
            val children = safeList(m["children"])?.mapNotNull { child ->
                val cm = safeMap(child) ?: return@mapNotNull null
                SplitChildItem(
                    orderId = safeString(cm["orderId"]),
                    items = safeList(cm["items"])?.mapNotNull { safeStringOrNull(it) } ?: emptyList(),
                    seq = safeInt(cm["seq"]) ?: 1
                )
            } ?: emptyList()
            SplitOutInfo(
                count = count,
                children = children,
                at = m["at"],
                by = safeString(m["by"])
            )
        }
        val splitOut = when (splitOutRaw) {
            is Map<*, *> -> splitOutInfo?.children?.flatMap { it.items } ?: emptyList()
            is List<*> -> splitOutRaw.mapNotNull { safeStringOrNull(it) }
            is String -> if (splitOutRaw.isNotBlank()) listOf(splitOutRaw) else emptyList()
            else -> emptyList()
        }

        val isPriority = safeBoolean(data["isPriority"])
            ?: (badge.equals("priority", ignoreCase = true) ||
                safeString(data["priority"]).equals("high", ignoreCase = true))

        val dispatchDocMap = safeMap(data["dispatch"])
        val isPoolable = !isSplitChild && (
            safeBoolean(dispatchDocMap?.get("poolable"))
                ?: safeBoolean(data["poolable"])
                ?: safeBoolean(data["dispatch.poolable"])
                ?: (!isPriority && normService in listOf("food", "convenience", OrderStepConstants.SERVICE_FOOD))
            )
        val targetRiderId = (safeStringOrNull(dispatchDocMap?.get("targetRiderId"))
            ?: safeStringOrNull(dispatchDocMap?.get("offeredRiderId"))
            ?: safeStringOrNull(data["targetRiderId"])
            ?: safeStringOrNull(data["offeredRiderId"])
            ?: "").trim()
        val maxBatch = safeInt(dispatchDocMap?.get("maxBatch"))
            ?: safeInt(data["maxBatch"])
            ?: 2
        val holdMinutesOrder = safeDouble(dispatchDocMap?.get("holdMinutes"))
            ?: safeDouble(data["holdMinutes"])
            ?: if (finalBadge.equals("saver", ignoreCase = true) || finalBadge.equals("standard", ignoreCase = true)) 5.0 else 0.0
        val endsWhenReadyOrder = safeBoolean(dispatchDocMap?.get("endsWhenReady"))
            ?: safeBoolean(data["endsWhenReady"])
            ?: safeBoolean(data["dispatch.endsWhenReady"])
        val specialExtra = safeDouble(safeMap(dispatchDocMap?.get("specialRadius"))?.get("riderExtra"))
            ?: safeDouble(data["specialRadiusExtra"])
            ?: 0.0

        // Validate & read timestamps using tolerant time reader
        val placedAtRaw = data["placedAt"]
        val placedAtDisplay = formatPlacedAtDisplay(placedAtRaw)
        val createdAtRaw = data["createdAt"]
        val acceptedAtFieldRaw = data["acceptedAt"]
        val pickedUpAtRaw = data["pickedUpAt"]
        val arrivedAtPickupAtRaw = data["arrivedAtPickupAt"]?.takeUnless { it is String && it.isBlank() }
        val arrivedMerchantAtRaw = data["arrivedMerchantAt"] ?: arrivedAtPickupAtRaw
        val arrivedAtDropoffAtRaw = data["arrivedAtDropoffAt"]?.takeUnless { it is String && it.isBlank() }
        val arrivedCustomerAtRaw = data["arrivedCustomerAt"] ?: arrivedAtDropoffAtRaw
        val completedAtRaw = data["completedAt"]?.takeUnless { it is String && it.isBlank() }
        val deliveredAtRaw = data["deliveredAt"] ?: completedAtRaw
        val updatedAtRaw = data["updatedAt"] ?: data["statusUpdatedAt"]
        val explicitHoldUntilRaw = data["holdUntil"] ?: data["holdUntilMillis"]

        val acceptedAtParsed = parseTolerantTimeMillis(acceptedAtFieldRaw, fieldName = "acceptedAt")
        val placedAtParsed = parseTolerantTimeMillis(placedAtRaw, fieldName = "placedAt", allowLiteralRecentWords = true)
        val createdAtParsed = parseTolerantTimeMillis(createdAtRaw, fieldName = "createdAt")
        parseTolerantTimeMillis(pickedUpAtRaw, fieldName = "pickedUpAt")
        parseTolerantTimeMillis(arrivedAtPickupAtRaw, fieldName = "arrivedAtPickupAt")
        parseTolerantTimeMillis(arrivedMerchantAtRaw, fieldName = "arrivedMerchantAt")
        parseTolerantTimeMillis(arrivedAtDropoffAtRaw, fieldName = "arrivedAtDropoffAt")
        parseTolerantTimeMillis(arrivedCustomerAtRaw, fieldName = "arrivedCustomerAt")
        parseTolerantTimeMillis(completedAtRaw, fieldName = "completedAt")
        parseTolerantTimeMillis(deliveredAtRaw, fieldName = "deliveredAt")
        parseTolerantTimeMillis(updatedAtRaw, fieldName = "updatedAt")
        val explicitHoldUntilMillis = parseTolerantTimeMillis(explicitHoldUntilRaw, fieldName = "holdUntil") ?: 0L

        val acceptedAtMillis = acceptedAtParsed ?: placedAtParsed ?: createdAtParsed ?: nowMillis
        val releaseTimeMillis = if (isScheduled && schedMillis > 0L) {
            (schedMillis - (scheduledReleaseLeadMinutes * 60 * 1000).toLong()).coerceAtLeast(0L)
        } else 0L

        val baseHoldStartMillis = if (isScheduled && releaseTimeMillis > 0L) {
            releaseTimeMillis
        } else {
            acceptedAtMillis
        }

        val holdUntilMillis = when {
            explicitHoldUntilMillis > 0L && !isScheduled -> explicitHoldUntilMillis
            holdMinutesOrder > 0.0 && baseHoldStartMillis > 0L -> baseHoldStartMillis + (holdMinutesOrder * 60 * 1000).toLong()
            holdMinutesOrder > 0.0 -> nowMillis + (holdMinutesOrder * 60 * 1000).toLong()
            else -> 0L
        }

        val excludeRiderIds = safeList(data["excludeRiderIds"])?.mapNotNull { safeStringOrNull(it) } ?: emptyList()
        val riderFeeFixed = safeDouble(data["riderFeeFixed"])
        val riderFeeEstimate = safeDouble(data["riderFeeEstimate"]) ?: 0.0
        val batchId = safeStringOrNull(data["batchId"])?.takeIf { it.isNotBlank() }
        val batchIndex = safeInt(data["batchIndex"]) ?: 1

        val earningMap = safeMap(data["riderEarning"])
        val riderEarning = earningMap?.let {
            RiderEarning(
                bookingFee = safeDouble(it["bookingFee"]) ?: 0.0,
                pickupFee = safeDouble(it["pickupFee"]) ?: 0.0,
                extra = safeDouble(it["extra"]) ?: 0.0,
                tip = safeDouble(it["tip"]) ?: 0.0,
                total = safeDouble(it["total"]) ?: 0.0,
                rank = safeInt(it["rank"]) ?: 1,
                batchId = safeString(it["batchId"])
            )
        }

        val statusHistory = safeList(data["statusHistory"])?.mapNotNull { item ->
            val rawMap = safeMap(item) ?: return@mapNotNull null
            rawMap.entries.associate { (k, v) -> k.toString() to v }
        } ?: emptyList()

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
            passengerCount = passengerCount,
            pickupNote = pickupNote,
            fulfillment = fulfillment,
            isScheduled = isScheduled,
            scheduledFor = scheduledForRaw,
            scheduledDateText = scheduledDateText,
            merchantAccepted = merchantAccepted,
            prepMinutes = prepMinutes,
            editedAt = editedAt,
            merchantName = merchantName,
            senderName = rawSenderName,
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
            items = resolvedItems,
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
            budgetLimit = budgetLimit,
            finalReceiptTotal = finalReceiptTotal,
            paidOnMerchant = paidOnMerchant,
            paidOnMerchantAt = paidOnMerchantAtRaw,
            overBudgetApproved = overBudgetApproved,
            riderRequestStatus = riderReqStatus,
            pickupDistanceKm = safeDouble(data["pickupDistanceKm"]) ?: 0.0,
            deliveryDistanceKm = safeDouble(data["deliveryDistanceKm"]) ?: 3.2,
            placedAt = placedAtDisplay,
            pickedUpAt = pickedUpAtRaw,
            arrivedMerchantAt = arrivedMerchantAtRaw,
            arrivedCustomerAt = arrivedCustomerAtRaw,
            arrivedAtPickupAt = arrivedAtPickupAtRaw,
            arrivedAtDropoffAt = arrivedAtDropoffAtRaw,
            completedAt = completedAtRaw,
            deliveredAt = deliveredAtRaw,
            updatedAt = updatedAtRaw,
            statusHistory = statusHistory,
            releaseReason = safeStringOrNull(data["releaseReason"]),
            lateTipAmount = lateTipAmount,
            lateTipAt = lateTipAtRaw,
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
            targetRiderId = targetRiderId,
            riderEarning = riderEarning,
            splitOut = splitOut,
            splitOutInfo = splitOutInfo,
            splitParentOrderId = splitParentOrderId,
            isSplitChild = isSplitChild
        )
    }

    /**
     * Parses a list of `(id, data)` pairs inside per-document try/catch, invoking [onBadDocument]
     * for any failed document and keeping all valid documents. Safe for JVM unit testing without
     * Firestore snapshots.
     */
    fun parseOrderMapsIndividually(
        docs: List<Pair<String, Map<String, Any?>?>>,
        sourceLabel: String = "orders",
        scheduledReleaseLeadMinutes: Double = 0.0,
        nowMillis: Long = System.currentTimeMillis(),
        onBadDocument: (id: String, orderNumberHint: String, serviceTypeHint: String, error: Throwable) -> Unit = { _, _, _, _ -> }
    ): List<Order> {
        val result = mutableListOf<Order>()
        for ((docId, data) in docs) {
            try {
                result.add(
                    parseOrderFromMap(
                        id = docId,
                        data = data,
                        scheduledReleaseLeadMinutes = scheduledReleaseLeadMinutes,
                        nowMillis = nowMillis
                    )
                )
            } catch (t: Throwable) {
                val hintNum = safeStringOrNull(data?.get("orderNumber"))
                    ?: safeStringOrNull(data?.get("orderId"))
                    ?: docId
                val hintService = safeString(data?.get("serviceType"))
                try {
                    Log.e(TAG, "[$sourceLabel] Skipped bad order document id=$docId: ${t.message}", t)
                } catch (_: Throwable) {
                }
                onBadDocument(docId, hintNum, hintService, t)
            }
        }
        return result
    }
}
