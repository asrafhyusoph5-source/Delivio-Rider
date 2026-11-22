package com.example.data.models

import com.example.data.constants.BatchConstants
import com.example.data.constants.OrderStepConstants
import com.example.data.constants.RiderPaySettings
import com.google.firebase.Timestamp
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToLong

data class DeliveryFeeRatesV2(
    val baseFare: Double = 0.0,
    val includedKm: Double = 0.0,
    val perKm: Double = 0.0,
    val minFee: Double = 0.0,
    val maxFee: Double? = null
) {
    fun calculate(km: Double): Double {
        val raw = baseFare + (km - includedKm).coerceAtLeast(0.0) * perKm
        val withMin = maxOf(raw, minFee)
        val upper = maxFee
        return if (upper != null && upper > 0.0) minOf(withMin, upper) else withMin
    }
}

data class PickupFeeRatesV2(
    val baseFee: Double = 0.0,
    val freeKm: Double = 0.0,
    val perKm: Double = 0.0,
    val maxFee: Double? = null
) {
    fun calculate(km: Double): Double {
        val raw = (baseFee + (km.coerceAtLeast(0.0) - freeKm).coerceAtLeast(0.0) * perKm).coerceAtLeast(0.0)
        val upper = maxFee
        return if (upper != null && upper > 0.0) minOf(raw, upper) else raw
    }
}

data class ServiceFeeConfigV2(
    val mode: String = "flat", // "flat" or "percent"
    val value: Double = 0.0
)

data class PeakRatesV2(
    val enabled: Boolean = false,
    val multiplier: Double = 1.0
)

data class NightRatesV2(
    val enabled: Boolean = false,
    val surcharge: Double = 0.0
)

data class FreeDeliveryConfigV2(
    val enabled: Boolean = false,
    val minSubtotal: Double = 0.0
)

data class StandardServiceRatesV2(
    val delivery: DeliveryFeeRatesV2 = DeliveryFeeRatesV2(),
    val pickup: PickupFeeRatesV2 = PickupFeeRatesV2(),
    val serviceFee: ServiceFeeConfigV2 = ServiceFeeConfigV2(),
    val peak: PeakRatesV2 = PeakRatesV2(),
    val night: NightRatesV2 = NightRatesV2(),
    val freeDelivery: FreeDeliveryConfigV2 = FreeDeliveryConfigV2(),
    val maxWeightKg: Double = 0.0,
    val extraStopFee: Double = 0.0,
    val active: Boolean = true
)

data class ParcelRatesV2(
    val motorcycle: StandardServiceRatesV2 = StandardServiceRatesV2(),
    val sedan: StandardServiceRatesV2 = StandardServiceRatesV2()
)

data class TaxiRatesV2(
    val motorcycle: StandardServiceRatesV2 = StandardServiceRatesV2(),
    val tricycle: StandardServiceRatesV2 = StandardServiceRatesV2(),
    val car4: StandardServiceRatesV2 = StandardServiceRatesV2(),
    val car8: StandardServiceRatesV2 = StandardServiceRatesV2()
)

data class ServicesRatesV2(
    val food: StandardServiceRatesV2 = StandardServiceRatesV2(),
    val convenience: StandardServiceRatesV2 = StandardServiceRatesV2(),
    val pabili: StandardServiceRatesV2 = StandardServiceRatesV2(),
    val instant: StandardServiceRatesV2 = StandardServiceRatesV2(),
    val parcel: ParcelRatesV2 = ParcelRatesV2(),
    val taxi: TaxiRatesV2 = TaxiRatesV2()
)

data class DeliveryRatesV2(
    val schema: Int = 0,
    val version: Long = 1L,
    val updatedAtMillis: Long = 0L,
    val services: ServicesRatesV2 = ServicesRatesV2()
) {
    val isSchema2: Boolean get() = schema == 2

    fun rateFor(serviceType: String, vehicleType: String = "motorcycle"): StandardServiceRatesV2 {
        val svc = serviceType.trim().lowercase(Locale.US)
        val veh = vehicleType.trim().lowercase(Locale.US)
        return when (svc) {
            "food", OrderStepConstants.SERVICE_FOOD -> services.food
            "convenience", "mart", OrderStepConstants.SERVICE_MART -> services.convenience
            "pabili", OrderStepConstants.SERVICE_PABILI -> services.pabili
            "instant", "request", "request_rider", OrderStepConstants.SERVICE_REQUEST -> services.instant
            "parcel", OrderStepConstants.SERVICE_PARCEL -> when (veh) {
                "sedan", "car4", "car8", "car", "4 seater car", "8 seater car", "4-seater car", "8-seater car" ->
                    services.parcel.sedan
                else -> services.parcel.motorcycle
            }
            "taxi", OrderStepConstants.SERVICE_TAXI -> when (veh) {
                "tricycle", "trike" -> services.taxi.tricycle
                "car4", "car_4", "4 seater car", "4-seater car", "sedan", "car" -> services.taxi.car4
                "car8", "car_8", "8 seater car", "8-seater car" -> services.taxi.car8
                else -> services.taxi.motorcycle
            }
            else -> services.food
        }
    }

    companion object {
        private fun numOrNull(v: Any?): Double? = (v as? Number)?.toDouble() ?: (v as? String)?.trim()?.toDoubleOrNull()

        private fun boolVal(v: Any?, default: Boolean = false): Boolean = when (v) {
            is Boolean -> v
            is String -> v.trim().lowercase(Locale.US).let { it == "true" || it == "1" }
            is Number -> v.toInt() != 0
            else -> default
        }

        private fun asMap(v: Any?): Map<*, *>? = v as? Map<*, *>

        fun parseStandardService(map: Map<*, *>?): StandardServiceRatesV2 {
            if (map == null) return StandardServiceRatesV2()
            val delivMap = asMap(map["delivery"])
            val pickupMap = asMap(map["pickup"])
            val feeMap = asMap(map["serviceFee"])
            val peakMap = asMap(map["peak"])
            val nightMap = asMap(map["night"])
            val freeDelMap = asMap(map["freeDelivery"])

            val delivery = DeliveryFeeRatesV2(
                baseFare = numOrNull(delivMap?.get("baseFare")) ?: 0.0,
                includedKm = numOrNull(delivMap?.get("includedKm")) ?: 0.0,
                perKm = numOrNull(delivMap?.get("perKm")) ?: 0.0,
                minFee = numOrNull(delivMap?.get("minFee")) ?: 0.0,
                maxFee = numOrNull(delivMap?.get("maxFee"))
            )
            val pickup = PickupFeeRatesV2(
                baseFee = numOrNull(pickupMap?.get("baseFee")) ?: 0.0,
                freeKm = numOrNull(pickupMap?.get("freeKm")) ?: 0.0,
                perKm = numOrNull(pickupMap?.get("perKm")) ?: 0.0,
                maxFee = numOrNull(pickupMap?.get("maxFee"))
            )
            val serviceFee = ServiceFeeConfigV2(
                mode = (feeMap?.get("mode")?.toString()?.trim()?.lowercase(Locale.US))?.ifBlank { "flat" } ?: "flat",
                value = numOrNull(feeMap?.get("value")) ?: 0.0
            )
            val peak = PeakRatesV2(
                enabled = boolVal(peakMap?.get("enabled"), false),
                multiplier = numOrNull(peakMap?.get("multiplier")) ?: 1.0
            )
            val night = NightRatesV2(
                enabled = boolVal(nightMap?.get("enabled"), false),
                surcharge = numOrNull(nightMap?.get("surcharge")) ?: 0.0
            )
            val freeDelivery = FreeDeliveryConfigV2(
                enabled = boolVal(freeDelMap?.get("enabled"), false),
                minSubtotal = numOrNull(freeDelMap?.get("minSubtotal")) ?: 0.0
            )
            return StandardServiceRatesV2(
                delivery = delivery,
                pickup = pickup,
                serviceFee = serviceFee,
                peak = peak,
                night = night,
                freeDelivery = freeDelivery,
                maxWeightKg = numOrNull(map["maxWeightKg"]) ?: 0.0,
                extraStopFee = numOrNull(map["extraStopFee"]) ?: 0.0,
                active = boolVal(map["active"], true)
            )
        }

        fun fromMap(data: Map<String, Any?>?): DeliveryRatesV2? {
            if (data == null) return null
            val schema = (data["schema"] as? Number)?.toInt() ?: 0
            val version = (data["version"] as? Number)?.toLong() ?: 1L
            val updatedAtMs = OrderDocumentParser.parseTolerantTimeMillisOrZero(data["updatedAt"])
            val servicesMap = asMap(data["services"])
            if (schema != 2 && servicesMap == null) return DeliveryRatesV2(schema = schema, version = version, updatedAtMillis = updatedAtMs)

            val parcelMap = asMap(servicesMap?.get("parcel"))
            val taxiMap = asMap(servicesMap?.get("taxi"))
            val convenienceRaw = asMap(servicesMap?.get("convenience")) ?: asMap(servicesMap?.get("mart"))
            val instantRaw = asMap(servicesMap?.get("instant")) ?: asMap(servicesMap?.get("request_rider"))

            return DeliveryRatesV2(
                schema = schema,
                version = version,
                updatedAtMillis = updatedAtMs,
                services = ServicesRatesV2(
                    food = parseStandardService(asMap(servicesMap?.get("food"))),
                    convenience = parseStandardService(convenienceRaw),
                    pabili = parseStandardService(asMap(servicesMap?.get("pabili"))),
                    instant = parseStandardService(instantRaw),
                    parcel = ParcelRatesV2(
                        motorcycle = parseStandardService(asMap(parcelMap?.get("motorcycle"))),
                        sedan = parseStandardService(asMap(parcelMap?.get("sedan")))
                    ),
                    taxi = TaxiRatesV2(
                        motorcycle = parseStandardService(asMap(taxiMap?.get("motorcycle"))),
                        tricycle = parseStandardService(asMap(taxiMap?.get("tricycle"))),
                        car4 = parseStandardService(asMap(taxiMap?.get("car4"))),
                        car8 = parseStandardService(asMap(taxiMap?.get("car8")))
                    )
                )
            )
        }
    }
}

data class CommissionSettings(
    val single: Double = 10.0,
    val second: Double = 20.0,
    val thirdPlus: Double = 30.0,
    val merchantDefault: Double = 15.0,
    val updatedAtMillis: Long = 0L
) {
    fun rateForBatchIndex(batchIndex: Int): Double {
        return when {
            batchIndex <= 1 -> single
            batchIndex == 2 -> second
            else -> thirdPlus
        }
    }

    companion object {
        fun fromMap(data: Map<String, Any?>?): CommissionSettings {
            if (data == null) return CommissionSettings()
            val riderMap = data["rider"] as? Map<*, *>
            val single = (riderMap?.get("single") as? Number)?.toDouble() ?: 10.0
            val second = (riderMap?.get("second") as? Number)?.toDouble() ?: 20.0
            val thirdPlus = (riderMap?.get("thirdPlus") as? Number)?.toDouble() ?: 30.0
            val merchantDefault = (data["merchantDefault"] as? Number)?.toDouble() ?: 15.0
            val updatedAtMillis = OrderDocumentParser.parseTolerantTimeMillisOrZero(data["updatedAt"])
            return CommissionSettings(
                single = single,
                second = second,
                thirdPlus = thirdPlus,
                merchantDefault = merchantDefault,
                updatedAtMillis = updatedAtMillis
            )
        }
    }
}

data class OrderPricingSnapshot(
    val schema: Int = 2,
    val ratesVersion: Long = 0L,
    val serviceKey: String = "",
    val vehicle: String = "",
    val deliveryFee: Double = 0.0,
    val serviceFee: Double = 0.0,
    val tip: Double = 0.0,
    val vat: Double = 0.0,
    val itemsSubtotal: Double = 0.0,
    val total: Double = 0.0,
    val payer: String = "customer",
    val merchantCommissionRate: Double = 0.0
) {
    companion object {
        fun fromMap(map: Map<*, *>?): OrderPricingSnapshot? {
            if (map == null || map.isEmpty()) return null
            return OrderPricingSnapshot(
                schema = OrderDocumentParser.safeInt(map["schema"]) ?: 2,
                ratesVersion = (map["ratesVersion"] as? Number)?.toLong() ?: 0L,
                serviceKey = OrderDocumentParser.safeString(map["serviceKey"]),
                vehicle = OrderDocumentParser.safeString(map["vehicle"]),
                deliveryFee = OrderDocumentParser.safeDouble(map["deliveryFee"]) ?: 0.0,
                serviceFee = OrderDocumentParser.safeDouble(map["serviceFee"]) ?: 0.0,
                tip = OrderDocumentParser.safeDouble(map["tip"]) ?: 0.0,
                vat = OrderDocumentParser.safeDouble(map["vat"]) ?: 0.0,
                itemsSubtotal = OrderDocumentParser.safeDouble(map["itemsSubtotal"]) ?: 0.0,
                total = OrderDocumentParser.safeDouble(map["total"]) ?: 0.0,
                payer = OrderDocumentParser.safeString(map["payer"], "customer"),
                merchantCommissionRate = OrderDocumentParser.safeDouble(map["merchantCommissionRate"]) ?: 0.0
            )
        }
    }
}

data class RiderQuote(
    val pickupKm: Double = 0.0,
    val pickupFee: Double = 0.0,
    val deliveryFee: Double = 0.0,
    val batchIndex: Int = 1,
    val commissionRate: Double = 10.0,
    val commissionAmount: Double = 0.0,
    val gross: Double = 0.0,
    val net: Double = 0.0,
    val calculatedAtMillis: Long = System.currentTimeMillis(),
    val ratesVersion: Long = 0L
) {
    fun toFirestoreMap(useServerTimestamp: Boolean = false): Map<String, Any> {
        val calcAt: Any = if (useServerTimestamp) {
            com.google.firebase.firestore.FieldValue.serverTimestamp()
        } else {
            Timestamp(java.util.Date(calculatedAtMillis))
        }
        return mapOf(
            "pickupKm" to PricingEngine.round2(pickupKm),
            "pickupFee" to PricingEngine.round2(pickupFee),
            "deliveryFee" to PricingEngine.round2(deliveryFee),
            "batchIndex" to batchIndex,
            "commissionRate" to PricingEngine.round2(commissionRate),
            "commissionAmount" to PricingEngine.round2(commissionAmount),
            "gross" to PricingEngine.round2(gross),
            "net" to PricingEngine.round2(net),
            "ratesVersion" to ratesVersion,
            "calculatedAt" to calcAt
        )
    }

    companion object {
        fun fromMap(map: Map<*, *>?): RiderQuote? {
            if (map == null || map.isEmpty()) return null
            val calcAt = OrderDocumentParser.parseTolerantTimeMillisOrZero(map["calculatedAt"])
            return RiderQuote(
                pickupKm = OrderDocumentParser.safeDouble(map["pickupKm"]) ?: 0.0,
                pickupFee = OrderDocumentParser.safeDouble(map["pickupFee"]) ?: 0.0,
                deliveryFee = OrderDocumentParser.safeDouble(map["deliveryFee"]) ?: 0.0,
                batchIndex = OrderDocumentParser.safeInt(map["batchIndex"]) ?: 1,
                commissionRate = OrderDocumentParser.safeDouble(map["commissionRate"]) ?: 10.0,
                commissionAmount = OrderDocumentParser.safeDouble(map["commissionAmount"]) ?: 0.0,
                gross = OrderDocumentParser.safeDouble(map["gross"]) ?: 0.0,
                net = OrderDocumentParser.safeDouble(map["net"]) ?: 0.0,
                calculatedAtMillis = calcAt,
                ratesVersion = (map["ratesVersion"] as? Number)?.toLong() ?: 0L
            )
        }
    }
}

data class SessionClaim(
    val id: String = "",
    val sessionId: String = "",
    val title: String = "",
    val riderId: String = "",
    val rewardAmount: Double = 0.0,
    val status: String = "Pending",
    val createdAtMillis: Long = 0L
)

object PricingEngine {

    fun round2(v: Double): Double = (v * 100.0).roundToLong() / 100.0

    fun isNightTime(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Manila"), Locale.US).apply {
            timeInMillis = nowMillis
        }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        return hour >= 22 || hour < 5
    }

    fun resolveServiceKey(order: Order): String {
        val snapKey = order.pricing?.serviceKey?.trim()?.lowercase(Locale.US)
        if (!snapKey.isNullOrBlank()) return snapKey
        return when (order.serviceType.trim().lowercase(Locale.US)) {
            "convenience", "mart", OrderStepConstants.SERVICE_MART -> "convenience"
            "pabili", OrderStepConstants.SERVICE_PABILI -> "pabili"
            "instant", "request", "request_rider", OrderStepConstants.SERVICE_REQUEST -> "instant"
            "parcel", OrderStepConstants.SERVICE_PARCEL -> "parcel"
            "taxi", OrderStepConstants.SERVICE_TAXI -> "taxi"
            else -> "food"
        }
    }

    fun resolveRatesForOrder(
        order: Order,
        liveRates: DeliveryRatesV2?,
        versionedRates: Map<Long, DeliveryRatesV2> = emptyMap()
    ): DeliveryRatesV2? {
        val requestedVersion = order.pricing?.ratesVersion ?: 0L
        if (requestedVersion > 0L) {
            versionedRates[requestedVersion]?.let { return it }
            if (liveRates != null && liveRates.version == requestedVersion) return liveRates
        }
        return liveRates
    }

    fun calculatePickupFeeV2(
        pickupKm: Double,
        serviceType: String,
        vehicleType: String,
        batchIndex: Int,
        rates: DeliveryRatesV2
    ): Double {
        if (batchIndex > 1) return 0.0
        val serviceRate = rates.rateFor(serviceType, vehicleType)
        return round2(serviceRate.pickup.calculate(pickupKm.coerceAtLeast(0.0)))
    }

    fun calculateDeliveryFeeV2(
        order: Order,
        rates: DeliveryRatesV2,
        isPeakNow: Boolean = false,
        isNightNow: Boolean = isNightTime()
    ): Double {
        order.riderFeeFixed?.let { fixed ->
            if (fixed > 0.0) return round2(fixed)
        }
        val snapshot = order.pricing
        if (snapshot != null && snapshot.schema == 2 && snapshot.deliveryFee > 0.0) {
            return round2(snapshot.deliveryFee)
        }

        val serviceKey = resolveServiceKey(order)
        val vehicleKey = snapshot?.vehicle?.ifBlank { order.vehicleType } ?: order.vehicleType
        val svcRate = rates.rateFor(serviceKey, vehicleKey)
        val km = if (order.deliveryDistanceKm > 0.0) order.deliveryDistanceKm else 0.0
        var fee = svcRate.delivery.calculate(km)
        if (svcRate.peak.enabled && isPeakNow) {
            fee *= svcRate.peak.multiplier
        }
        if (svcRate.night.enabled && isNightNow) {
            fee += svcRate.night.surcharge
        }
        if (order.isTaxi && svcRate.extraStopFee > 0.0 && order.stops.isNotEmpty()) {
            fee += order.stops.size * svcRate.extraStopFee
        }
        if (fee <= 0.0 && order.feesDeliveryFee > 0.0) {
            return round2(order.feesDeliveryFee)
        }
        return round2(fee)
    }

    /**
     * Computes a [RiderQuote] for accepting (or re-positioning in a batch) an [order].
     *
     * Uses Schema 2 `deliveryRates/{cityId}` + `settings/commission` when the city has schema 2 rates
     * (or the order carries a schema 2 `pricing` snapshot). Otherwise falls back to legacy
     * `BatchConstants.calculateDeliveryFee` & `BatchConstants.calculatePickupFee`.
     */
    fun computeRiderQuote(
        order: Order,
        pickupKm: Double,
        batchIndex: Int,
        rates: DeliveryRatesV2?,
        commission: CommissionSettings = CommissionSettings(),
        legacyPaySettings: RiderPaySettings = RiderPaySettings(),
        legacySpecialRadiusExtra: Double = 0.0,
        isBatch: Boolean = batchIndex > 1,
        calculatedAtMillis: Long = System.currentTimeMillis()
    ): RiderQuote {
        val rank = batchIndex.coerceAtLeast(1)
        val isSplit = order.isSplitChild || order.badge.equals("split", ignoreCase = true)
        val hasSchema2 = (rates?.isSchema2 == true) || (order.pricing?.schema == 2)

        if (!hasSchema2) {
            val legacyDeliv = BatchConstants.calculateDeliveryFee(
                order = order,
                rank = rank,
                isBatch = isBatch,
                paySettings = legacyPaySettings,
                specialRadiusExtra = legacySpecialRadiusExtra
            )
            val legacyPickup = if (isSplit || rank > 1) 0.0 else {
                BatchConstants.calculatePickupFee(pickupKm.coerceAtLeast(0.0), rank, legacyPaySettings)
            }
            val tipVal = order.tip.coerceAtLeast(0.0)
            val gross = round2(legacyDeliv + legacyPickup + tipVal)
            return RiderQuote(
                pickupKm = round2(pickupKm.coerceAtLeast(0.0)),
                pickupFee = round2(legacyPickup),
                deliveryFee = round2(legacyDeliv),
                batchIndex = rank,
                commissionRate = 0.0,
                commissionAmount = 0.0,
                gross = gross,
                net = gross,
                calculatedAtMillis = calculatedAtMillis,
                ratesVersion = 0L
            )
        }

        val resolvedRates = rates ?: DeliveryRatesV2(schema = 2)
        val serviceKey = resolveServiceKey(order)
        val vehicleKey = order.pricing?.vehicle?.ifBlank { order.vehicleType } ?: order.vehicleType
        val delivFee = if (isSplit && (order.riderFeeFixed ?: 0.0) > 0.0) {
            round2(order.riderFeeFixed ?: 0.0)
        } else if (order.pricing?.schema == 2 && order.pricing.deliveryFee > 0.0) {
            round2(order.pricing.deliveryFee)
        } else if (resolvedRates.isSchema2) {
            calculateDeliveryFeeV2(order, resolvedRates)
        } else {
            round2(order.feesDeliveryFee.coerceAtLeast(order.deliveryFee))
        }

        val pickupFee = if (isSplit || rank > 1) {
            0.0
        } else {
            calculatePickupFeeV2(
                pickupKm = pickupKm,
                serviceType = serviceKey,
                vehicleType = vehicleKey,
                batchIndex = rank,
                rates = resolvedRates
            )
        }

        val commissionRate = if (isSplit) 0.0 else commission.rateForBatchIndex(rank)
        val commissionable = delivFee + pickupFee
        val commissionAmount = round2(commissionable * (commissionRate / 100.0))
        val tipVal = order.tip.coerceAtLeast(0.0)
        val gross = round2(commissionable + tipVal)
        val net = round2((commissionable - commissionAmount) + tipVal)

        return RiderQuote(
            pickupKm = round2(pickupKm.coerceAtLeast(0.0)),
            pickupFee = pickupFee,
            deliveryFee = delivFee,
            batchIndex = rank,
            commissionRate = commissionRate,
            commissionAmount = commissionAmount,
            gross = gross,
            net = net,
            calculatedAtMillis = calculatedAtMillis,
            ratesVersion = order.pricing?.ratesVersion?.takeIf { it > 0L } ?: resolvedRates.version
        )
    }

    /**
     * Re-indexes active orders in a batch starting from 1 and recalculates [RiderQuote] for any order
     * whose batch position or quote needs updating (for example after an earlier batch order is released).
     */
    fun recalculateQuotesForBatchPositionChange(
        orderedBatch: List<Order>,
        pickupKmByOrderId: Map<String, Double>,
        liveRates: DeliveryRatesV2?,
        versionedRates: Map<Long, DeliveryRatesV2> = emptyMap(),
        commission: CommissionSettings = CommissionSettings(),
        legacyPaySettings: RiderPaySettings = RiderPaySettings(),
        legacySpecialRadiusExtra: Double = 0.0,
        calculatedAtMillis: Long = System.currentTimeMillis()
    ): List<Pair<Order, RiderQuote>> {
        val isBatch = orderedBatch.size > 1
        return orderedBatch.mapIndexed { idx, order ->
            val newIndex = idx + 1
            val pickupKm = pickupKmByOrderId[order.id]
                ?: order.riderQuote?.pickupKm
                ?: order.pickupDistanceKm
            val ratesForOrder = resolveRatesForOrder(order, liveRates, versionedRates)
            val updatedQuote = computeRiderQuote(
                order = order,
                pickupKm = pickupKm,
                batchIndex = newIndex,
                rates = ratesForOrder,
                commission = commission,
                legacyPaySettings = legacyPaySettings,
                legacySpecialRadiusExtra = legacySpecialRadiusExtra,
                isBatch = isBatch,
                calculatedAtMillis = calculatedAtMillis
            )
            order.copy(
                batchIndex = newIndex,
                riderFeeEstimate = updatedQuote.net,
                riderQuote = updatedQuote
            ) to updatedQuote
        }
    }
}
