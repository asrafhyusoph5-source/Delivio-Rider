package com.example.data.constants

import com.example.data.models.Order
import kotlin.math.*

data class RiderPayRate(
    val minFare: Double = 45.0,
    val includedKm: Double = 2.0,
    val perKmExtra: Double = 10.0
)

data class RiderPayPickupRate(
    val minFare: Double = 15.0,
    val freeKm: Double = 1.0,
    val perKmExtra: Double = 8.0
)

data class RiderPaySettings(
    val single: RiderPayRate = RiderPayRate(minFare = 45.0, includedKm = 2.0, perKmExtra = 10.0),
    val doubleFirst: RiderPayRate = RiderPayRate(minFare = 40.0, includedKm = 2.0, perKmExtra = 9.0),
    val doubleSecond: RiderPayRate = RiderPayRate(minFare = 35.0, includedKm = 2.0, perKmExtra = 8.0),
    val doubleThird: RiderPayRate = RiderPayRate(minFare = 30.0, includedKm = 2.0, perKmExtra = 8.0),
    val pickup: RiderPayPickupRate = RiderPayPickupRate(minFare = 15.0, freeKm = 1.0, perKmExtra = 8.0)
)

data class DispatchSettings(
    val holdMinutes: Double = 5.0,
    val maxBatch: Int = 2,
    val poolMaxPickupGapKm: Double = 2.0,
    val poolMaxDropoffGapKm: Double = 2.5,
    val searchRadiusKm: Double = 8.0,
    val waveSeconds: Int = 5,
    val specialRadiusRiderExtra: Double = 0.0,
    val priorityMaxBatch: Int = 4,
    val priorityHeadStartSeconds: Int = 15
)

object BatchConstants {

    // Helper: Haversine distance in km
    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        if (lat1 == 0.0 || lon1 == 0.0 || lat2 == 0.0 || lon2 == 0.0) return 0.0
        val earthRadius = 6371.0 // km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    /**
     * The pickup phase is open while at least one order has not been picked up (riderStep before picked_up).
     * When every pickup is done the batch is closed until ALL its orders are delivered.
     */
    fun isPickupPhaseOpen(batch: List<Order>): Boolean {
        if (batch.isEmpty()) return false
        val prePickupSteps = setOf(
            OrderStepConstants.STEP_TO_MERCHANT,
            OrderStepConstants.STEP_AT_MERCHANT,
            OrderStepConstants.STEP_HEADING_TO_MERCHANT,
            OrderStepConstants.STEP_ARRIVED_AT_MERCHANT,
            OrderStepConstants.STEP_TO_SENDER,
            OrderStepConstants.STEP_AT_SENDER,
            OrderStepConstants.STEP_TO_VENDOR,
            OrderStepConstants.STEP_AT_VENDOR,
            OrderStepConstants.STEP_TO_PICKUP,
            OrderStepConstants.STEP_AT_PICKUP
        )
        return batch.any { order ->
            val step = order.riderStep.lowercase().trim()
            step in prePickupSteps
        }
    }

    /**
     * poolable(order) = order.dispatch.poolable == true, serviceType is food or convenience,
     * fulfillment is not pickup. Priority orders are never pooled.
     */
    fun isPoolable(order: Order, dispatch: DispatchSettings): Boolean {
        if (order.isPriority || order.isSplitChild) return false
        val badge = order.badge.trim().lowercase()
        if (badge == "priority" || badge == "split") return false
        if (!order.isPoolable) return false
        val s = order.serviceType.trim().lowercase()
        if (s != "food" && s != "convenience" && s != "instant" && s != OrderStepConstants.SERVICE_FOOD) return false
        if (order.isPickup) return false
        return true
    }

    /**
     * HOLD RULES:
     * - For an order with dispatch.holdMinutes > 0: holdUntil = acceptedAt + holdMinutes.
     * - If dispatch.endsWhenReady is true (Standard) the order stops holding when its status becomes "Ready"
     *   or now >= holdUntil, whichever comes first.
     * - If it is false (Saver) it holds until now >= holdUntil exactly, even when Ready.
     * - Priority never holds.
     * - A missing endsWhenReady means true for standard and false for saver.
     */
    fun isHolding(order: Order, dispatch: DispatchSettings, nowMillis: Long = System.currentTimeMillis()): Boolean {
        // Priority never holds
        if (order.isPriority || order.badge.equals("priority", ignoreCase = true)) {
            return false
        }
        // Split orders never hold
        if (order.isSplitChild || order.badge.equals("split", ignoreCase = true)) {
            return false
        }
        // Must be poolable
        if (!isPoolable(order, dispatch)) return false

        // Check hold minutes: must be > 0
        val effectiveHoldMinutes = if (order.holdMinutes > 0.0) {
            order.holdMinutes
        } else if (order.holdUntilMillis > 0L) {
            1.0
        } else {
            dispatch.holdMinutes
        }
        if (effectiveHoldMinutes <= 0.0) return false

        // holdUntil = acceptedAt + holdMinutes
        val effectiveHoldUntil = if (order.holdUntilMillis > 0L) {
            order.holdUntilMillis
        } else if (order.acceptedAtMillis > 0L && effectiveHoldMinutes > 0.0) {
            order.acceptedAtMillis + (effectiveHoldMinutes * 60 * 1000).toLong()
        } else {
            0L
        }
        if (effectiveHoldUntil <= 0L) return false

        // If now >= holdUntil, the hold has ended exactly
        if (nowMillis >= effectiveHoldUntil) {
            return false
        }

        // Check endsWhenReady: missing means true for standard and false for saver
        val endsWhenReady = order.resolvedEndsWhenReady
        val s = order.status.trim().lowercase()
        val isReady = s == "ready" || s == OrderStepConstants.STATUS_READY.lowercase()

        // If endsWhenReady is true (Standard): stops holding when status becomes "Ready" or now >= holdUntil, whichever comes first
        if (endsWhenReady && isReady) {
            return false
        }

        // If false (Saver): holds until now >= holdUntil exactly, even when Ready
        return true
    }

    /**
     * pairable(o1, o2): both poolable, same city, unassigned,
     * pickups within poolMaxPickupGapKm, drop-offs within poolMaxDropoffGapKm.
     */
    fun isPairable(o1: Order, o2: Order, dispatch: DispatchSettings): Boolean {
        if (!isPoolable(o1, dispatch) || !isPoolable(o2, dispatch)) return false
        if (o1.riderId != null || o2.riderId != null) return false
        if (o1.cityId.isNotBlank() && o2.cityId.isNotBlank() && o1.cityId != o2.cityId) return false

        // Pickups distance
        val pickupGap = if (o1.merchantLat != 0.0 && o2.merchantLat != 0.0) {
            distanceKm(o1.merchantLat, o1.merchantLng, o2.merchantLat, o2.merchantLng)
        } else {
            0.0
        }
        if (pickupGap > dispatch.poolMaxPickupGapKm) return false

        // Drop-offs distance
        val dropoffGap = if (o1.customerLat != 0.0 && o2.customerLat != 0.0) {
            distanceKm(o1.customerLat, o1.customerLng, o2.customerLat, o2.customerLng)
        } else {
            0.0
        }
        if (dropoffGap > dispatch.poolMaxDropoffGapKm) return false

        return true
    }

    /**
     * canJoin(batch, order):
     * the batch is non-empty, all its orders are poolable, the pickup phase is open,
     * the order's merchant has not already been picked up in the batch,
     * batch size + 1 <= the smallest maxBatch among all orders (including the new one),
     * same city,
     * the distance from the order's pickup to the nearest remaining pickup stop <= poolMaxPickupGapKm,
     * and from its drop-off to the nearest batch drop-off <= poolMaxDropoffGapKm.
     */
    fun canJoin(
        batch: List<Order>,
        order: Order,
        dispatch: DispatchSettings,
        isPriorityRider: Boolean = false
    ): Boolean {
        if (batch.isEmpty()) return false
        if (!batch.all { isPoolable(it, dispatch) }) return false
        if (!isPoolable(order, dispatch)) return false
        if (!isPickupPhaseOpen(batch)) return false

        // Check if the order's merchant has already been picked up in the batch
        val prePickupSteps = setOf(
            OrderStepConstants.STEP_TO_MERCHANT,
            OrderStepConstants.STEP_AT_MERCHANT,
            OrderStepConstants.STEP_HEADING_TO_MERCHANT,
            OrderStepConstants.STEP_ARRIVED_AT_MERCHANT
        )
        val merchantAlreadyPickedUpInBatch = batch.any { bOrder ->
            val sameMerchant = (bOrder.merchantId.isNotBlank() && bOrder.merchantId == order.merchantId) ||
                    (bOrder.merchantAddress.isNotBlank() && bOrder.merchantAddress == order.merchantAddress)
            val alreadyPickedUp = bOrder.riderStep.lowercase().trim() !in prePickupSteps
            sameMerchant && alreadyPickedUp
        }
        if (merchantAlreadyPickedUpInBatch) return false

        // Batch capacity:
        // For priority rider: limit is settings/dispatch.priorityMaxBatch (default 4) for Standard and Saver orders (minimum 1)
        // For regular rider: smallest maxBatch among all orders in it (minimum 2)
        val capacity = if (isPriorityRider) {
            dispatch.priorityMaxBatch.coerceAtLeast(1)
        } else {
            (batch.map { it.maxBatch.coerceAtLeast(2) } + order.maxBatch.coerceAtLeast(2)).minOrNull() ?: dispatch.maxBatch
        }
        if (batch.size + 1 > capacity) return false

        // Same city
        if (order.cityId.isNotBlank() && batch.any { it.cityId.isNotBlank() && it.cityId != order.cityId }) {
            return false
        }

        // Remaining pickup stops in batch
        val remainingPickups = batch.filter { it.riderStep.lowercase().trim() in prePickupSteps }
        val targetsForPickup = if (remainingPickups.isNotEmpty()) remainingPickups else batch
        val minPickupGap = targetsForPickup.minOfOrNull { bOrder ->
            if (order.merchantLat != 0.0 && bOrder.merchantLat != 0.0) {
                distanceKm(order.merchantLat, order.merchantLng, bOrder.merchantLat, bOrder.merchantLng)
            } else 0.0
        } ?: 0.0
        if (minPickupGap > dispatch.poolMaxPickupGapKm) return false

        // Nearest batch drop-off
        val minDropoffGap = batch.minOfOrNull { bOrder ->
            if (order.customerLat != 0.0 && bOrder.customerLat != 0.0) {
                distanceKm(order.customerLat, order.customerLng, bOrder.customerLat, bOrder.customerLng)
            } else 0.0
        } ?: 0.0
        if (minDropoffGap > dispatch.poolMaxDropoffGapKm) return false

        return true
    }

    /**
     * RIDER PAY FORMULAS:
     * - Delivery fee per order:
     *   Single orders use riderPay.single
     *   Batches use riderPay.double.first, riderPay.double.second, riderPay.double.third (3rd+)
     *   Fee formula: minFare + max(0, km - includedKm) * perKmExtra (km = merchant to customer),
     *   plus dispatch.specialRadius.riderExtra when present (never itemized).
     * - If an order has riderFeeFixed (split child), use that amount and nothing else.
     */
    fun calculateDeliveryFee(
        order: Order,
        rank: Int,
        isBatch: Boolean,
        paySettings: RiderPaySettings,
        specialRadiusExtra: Double = 0.0
    ): Double {
        // If an order has riderFeeFixed (split child), use that amount and nothing else
        order.riderFeeFixed?.let { fixed ->
            if (fixed > 0.0) return fixed
        }

        val rate = if (!isBatch) {
            paySettings.single
        } else {
            when (rank) {
                1 -> paySettings.doubleFirst
                2 -> paySettings.doubleSecond
                else -> paySettings.doubleThird
            }
        }

        val km = if (order.deliveryDistanceKm > 0.0) order.deliveryDistanceKm else 2.0
        val extraKm = (km - rate.includedKm).coerceAtLeast(0.0)
        val calculated = rate.minFare + (extraKm * rate.perKmExtra)

        // Add special radius extra (never itemized, included in delivery fee)
        val extra = if (order.specialRadiusExtra > 0.0) order.specialRadiusExtra else specialRadiusExtra
        return calculated + extra
    }

    /**
     * Pickup fee: paid once per batch on the first booking (rank 1):
     * pickup.minFare + max(0, km - pickup.freeKm) * pickup.perKmExtra (km = driver position at accept time to first merchant).
     * For rank 2+, pickupFee = 0.
     */
    fun calculatePickupFee(
        pickupDistanceKm: Double,
        rank: Int,
        paySettings: RiderPaySettings
    ): Double {
        if (rank > 1) return 0.0
        val rate = paySettings.pickup
        val km = pickupDistanceKm.coerceAtLeast(0.0)
        val extraKm = (km - rate.freeKm).coerceAtLeast(0.0)
        return rate.minFare + (extraKm * rate.perKmExtra)
    }
}
