package com.example.data.models

import com.example.data.constants.BatchConstants
import com.example.data.constants.OrderStepConstants

enum class StopType {
    MERCHANT, // Green pin
    CUSTOMER  // Red pin
}

enum class StopStatus {
    DONE,     // Finished stop: pin disappears from map, shows "Done" in timeline
    CURRENT,  // Active stop currently being traveled to or arrived at
    NEXT      // Upcoming stop in the sequence
}

data class RouteStop(
    val stopNumber: Int = 1,
    val type: StopType = StopType.MERCHANT,
    val orderId: String = "",
    val orderNumber: String = "",
    val serviceType: String = "food",
    val name: String = "",
    val fullName: String = "",
    val address: String = "",
    val fullAddress: String = "",
    val area: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val status: StopStatus = StopStatus.CURRENT,
    val isFinished: Boolean = false,
    val phone: String = "",
    val items: List<OrderItem> = emptyList(),
    val cashToCollect: Double = 0.0,
    val isCod: Boolean = false,
    val isPaidOnline: Boolean = true,
    val paymentProviderLabel: String = "Online",
    val order: Order = Order()
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

    val isMerchant: Boolean get() = type == StopType.MERCHANT
    val isCustomer: Boolean get() = type == StopType.CUSTOMER
    val isDone: Boolean get() = status == StopStatus.DONE
    val isCurrent: Boolean get() = status == StopStatus.CURRENT

    val displayActionTitle: String
        get() = if (isMerchant) {
            "Pick up at $name"
        } else {
            val firstName = name.trim().split(" ").firstOrNull() ?: name
            "Drop off to $firstName"
        }
}

object RouteStopCalculator {

    /**
     * Determines whether the merchant/pickup stop for this order is finished.
     */
    fun isMerchantStopFinished(order: Order): Boolean {
        val step = order.riderStep.lowercase().trim()
        return when (order.serviceType.lowercase().trim()) {
            OrderStepConstants.SERVICE_PARCEL -> {
                step in setOf(
                    OrderStepConstants.STEP_TO_RECIPIENT,
                    OrderStepConstants.STEP_AT_RECIPIENT,
                    OrderStepConstants.STEP_COMPLETED,
                    OrderStepConstants.STEP_DELIVERED
                )
            }
            OrderStepConstants.SERVICE_TAXI -> {
                step !in setOf(
                    OrderStepConstants.STEP_TAXI_TO_PICKUP,
                    OrderStepConstants.STEP_TAXI_AT_PICKUP
                )
            }
            else -> {
                step in setOf(
                    OrderStepConstants.STEP_PICKED_UP,
                    OrderStepConstants.STEP_TO_CUSTOMER,
                    OrderStepConstants.STEP_AT_CUSTOMER,
                    OrderStepConstants.STEP_HEADING_TO_CUSTOMER,
                    OrderStepConstants.STEP_ARRIVED_AT_CUSTOMER,
                    OrderStepConstants.STEP_COMPLETED,
                    OrderStepConstants.STEP_DELIVERED
                )
            }
        }
    }

    /**
     * Determines whether the customer/drop-off stop for this order is finished.
     */
    fun isCustomerStopFinished(order: Order): Boolean {
        val step = order.riderStep.lowercase().trim()
        val status = order.status.lowercase().trim()
        return step in setOf(
            OrderStepConstants.STEP_COMPLETED,
            OrderStepConstants.STEP_DELIVERED
        ) || status in setOf("delivered", "completed")
    }

    /**
     * Computes the ordered sequence of stops across all active orders in the batch.
     *
     * 1. Visit ALL merchants first, then ALL customers; never mix them.
     * 2. Merchants: nearest to driver's position first, then each next nearest to the previous stop.
     * 3. Customers: nearest to the last merchant first, then each next nearest.
     * 4. Finished stops keep their numbers.
     * 5. When an order joins, reorders only the remaining stops the same way.
     * 6. Each order keeps its own flow and visibility rules.
     */
    fun computeBatchStops(
        activeOrders: List<Order>,
        driverLat: Double,
        driverLng: Double,
        previousStops: List<RouteStop> = emptyList()
    ): List<RouteStop> {
        if (activeOrders.isEmpty()) return emptyList()

        // 1. Collect finished stops that already have fixed stopNumbers
        val finishedStopsMap = previousStops
            .filter { it.isFinished }
            .associateBy { "${it.orderId}_${it.type.name}" }

        val resultFinishedStops = mutableListOf<RouteStop>()
        val unvisitedMerchants = mutableListOf<RouteStopCandidate>()
        val unvisitedCustomers = mutableListOf<RouteStopCandidate>()

        activeOrders.forEach { order ->
            val merchantFinished = isMerchantStopFinished(order)
            val customerFinished = isCustomerStopFinished(order)

            val mKey = "${order.id}_${StopType.MERCHANT.name}"
            val cKey = "${order.id}_${StopType.CUSTOMER.name}"

            // Merchant Stop Candidate
            if (merchantFinished) {
                val existing = finishedStopsMap[mKey]
                if (existing != null) {
                    resultFinishedStops.add(existing.copy(status = StopStatus.DONE, isFinished = true, order = order))
                } else {
                    resultFinishedStops.add(
                        createStop(
                            number = 0, // will re-index finished stops if needed
                            type = StopType.MERCHANT,
                            order = order,
                            status = StopStatus.DONE,
                            isFinished = true
                        )
                    )
                }
            } else {
                unvisitedMerchants.add(
                    RouteStopCandidate(
                        order = order,
                        type = StopType.MERCHANT,
                        lat = if (order.merchantLat != 0.0) order.merchantLat else 9.7392,
                        lng = if (order.merchantLng != 0.0) order.merchantLng else 118.7353
                    )
                )
            }

            // Customer Stop Candidate
            if (customerFinished) {
                val existing = finishedStopsMap[cKey]
                if (existing != null) {
                    resultFinishedStops.add(existing.copy(status = StopStatus.DONE, isFinished = true, order = order))
                } else {
                    resultFinishedStops.add(
                        createStop(
                            number = 0,
                            type = StopType.CUSTOMER,
                            order = order,
                            status = StopStatus.DONE,
                            isFinished = true
                        )
                    )
                }
            } else {
                unvisitedCustomers.add(
                    RouteStopCandidate(
                        order = order,
                        type = StopType.CUSTOMER,
                        lat = if (order.customerLat != 0.0) order.customerLat else 9.7400,
                        lng = if (order.customerLng != 0.0) order.customerLng else 118.7360
                    )
                )
            }
        }

        // Sort finished stops by their original stopNumber
        resultFinishedStops.sortBy { it.stopNumber }
        // Ensure finished stops have consecutive 1..K numbering if any was initialized with 0
        var stopCounter = 1
        val finalFinishedStops = resultFinishedStops.map { stop ->
            val num = if (stop.stopNumber > 0) stop.stopNumber else stopCounter
            stopCounter = maxOf(stopCounter, num + 1)
            stop.copy(stopNumber = num)
        }.sortedBy { it.stopNumber }

        val currentStopCount = finalFinishedStops.lastOrNull()?.stopNumber ?: 0
        var nextStopNumber = currentStopCount + 1

        // 2. Sort Remaining Merchants: nearest to driver's position first, then nearest to previous stop
        val sortedRemainingMerchants = mutableListOf<RouteStopCandidate>()
        var refLat = if (driverLat != 0.0) driverLat else 9.7392
        var refLng = if (driverLng != 0.0) driverLng else 118.7353

        // If driver location not provided, fallback to last finished stop
        if (driverLat == 0.0 && finalFinishedStops.isNotEmpty()) {
            refLat = finalFinishedStops.last().lat
            refLng = finalFinishedStops.last().lng
        }

        val merchantsPool = unvisitedMerchants.toMutableList()
        while (merchantsPool.isNotEmpty()) {
            val nearest = merchantsPool.minByOrNull {
                BatchConstants.distanceKm(refLat, refLng, it.lat, it.lng)
            } ?: merchantsPool.first()
            merchantsPool.remove(nearest)
            sortedRemainingMerchants.add(nearest)
            refLat = nearest.lat
            refLng = nearest.lng
        }

        // 3. Sort Remaining Customers: nearest to the last merchant first, then each next nearest
        val sortedRemainingCustomers = mutableListOf<RouteStopCandidate>()
        var custRefLat = refLat
        var custRefLng = refLng

        // If no remaining merchants, reference last finished merchant stop if available
        if (sortedRemainingMerchants.isEmpty()) {
            val lastFinishedMerchant = finalFinishedStops.lastOrNull { it.isMerchant }
            if (lastFinishedMerchant != null) {
                custRefLat = lastFinishedMerchant.lat
                custRefLng = lastFinishedMerchant.lng
            }
        }

        val customersPool = unvisitedCustomers.toMutableList()
        while (customersPool.isNotEmpty()) {
            val nearest = customersPool.minByOrNull {
                BatchConstants.distanceKm(custRefLat, custRefLng, it.lat, it.lng)
            } ?: customersPool.first()
            customersPool.remove(nearest)
            sortedRemainingCustomers.add(nearest)
            custRefLat = nearest.lat
            custRefLng = nearest.lng
        }

        // 4. Build remaining RouteStops continuing the numbering
        val remainingStops = mutableListOf<RouteStop>()
        var isFirstUnfinished = true

        sortedRemainingMerchants.forEach { candidate ->
            val status = if (isFirstUnfinished) StopStatus.CURRENT else StopStatus.NEXT
            isFirstUnfinished = false
            remainingStops.add(
                createStop(
                    number = nextStopNumber++,
                    type = StopType.MERCHANT,
                    order = candidate.order,
                    status = status,
                    isFinished = false
                )
            )
        }

        sortedRemainingCustomers.forEach { candidate ->
            val status = if (isFirstUnfinished) StopStatus.CURRENT else StopStatus.NEXT
            isFirstUnfinished = false
            remainingStops.add(
                createStop(
                    number = nextStopNumber++,
                    type = StopType.CUSTOMER,
                    order = candidate.order,
                    status = status,
                    isFinished = false
                )
            )
        }

        return finalFinishedStops + remainingStops
    }

    private data class RouteStopCandidate(
        val order: Order,
        val type: StopType,
        val lat: Double,
        val lng: Double
    )

    private fun createStop(
        number: Int,
        type: StopType,
        order: Order,
        status: StopStatus,
        isFinished: Boolean
    ): RouteStop {
        val isMerchant = type == StopType.MERCHANT
        val name = if (isMerchant) {
            if (order.isTaxi) {
                order.stops.firstOrNull()?.address ?: "Passenger Pickup"
            } else {
                order.merchantName.ifBlank { "Merchant" }
            }
        } else {
            order.customerName.ifBlank { "Customer" }
        }

        val fullAddress = if (isMerchant) {
            order.merchantAddress.ifBlank { order.merchantName }
        } else {
            order.customerAddress.ifBlank { order.customerArea }
        }

        // Address according to stage visibility rules
        val address = if (isMerchant) {
            fullAddress
        } else {
            val isRevealed = OrderStepConstants.isCustomerAddressRevealed(order.serviceType, order.riderStep)
            if (isRevealed) {
                fullAddress
            } else {
                if (order.customerArea.isNotBlank()) {
                    "${order.customerArea} (Full address revealed after pickup)"
                } else {
                    "Destination hidden until pickup"
                }
            }
        }

        val phone = if (isMerchant) {
            order.merchantPhone
        } else {
            order.customerPhone
        }

        val lat = if (isMerchant) {
            if (order.merchantLat != 0.0) order.merchantLat else 9.7392
        } else {
            if (order.customerLat != 0.0) order.customerLat else 9.7400
        }

        val lng = if (isMerchant) {
            if (order.merchantLng != 0.0) order.merchantLng else 118.7353
        } else {
            if (order.customerLng != 0.0) order.customerLng else 118.7360
        }

        return RouteStop(
            stopNumber = number,
            type = type,
            orderId = order.id,
            orderNumber = order.orderNumber,
            serviceType = order.serviceType,
            name = name,
            fullName = name,
            address = address,
            fullAddress = fullAddress,
            area = if (isMerchant) order.merchantAddress else order.customerArea,
            lat = lat,
            lng = lng,
            status = status,
            isFinished = isFinished,
            phone = phone,
            items = order.items,
            cashToCollect = if (!isMerchant) order.cashAmountToCollect else 0.0,
            isCod = !isMerchant && order.isCod,
            isPaidOnline = !isMerchant && order.isPaidOnline,
            paymentProviderLabel = order.paymentProviderLabel,
            order = order
        )
    }
}
