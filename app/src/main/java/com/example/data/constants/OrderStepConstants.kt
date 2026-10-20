package com.example.data.constants

object OrderStepConstants {
    // Shared exact strings for riderStep
    const val STEP_HEADING_TO_MERCHANT = "heading_to_merchant" // or heading to sender / vendor
    const val STEP_ARRIVED_AT_MERCHANT = "arrived_at_merchant" // or arrived at sender / vendor
    const val STEP_PICKED_UP = "picked_up"
    const val STEP_HEADING_TO_CUSTOMER = "heading_to_customer" // or heading to recipient
    const val STEP_ARRIVED_AT_CUSTOMER = "arrived_at_customer" // or arrived at recipient
    const val STEP_DELIVERED = "delivered"

    // Platform statuses
    const val STATUS_PLACED_ORDER = "Placed Order"
    const val STATUS_PREPARING = "Preparing"
    const val STATUS_READY = "Ready"
    const val STATUS_RIDER_ON_THE_WAY = "Rider is on the way"
    const val STATUS_RIDER_ON_WAY_SENDER = "Rider is on the way to sender"
    const val STATUS_ARRIVED_AT_SENDER = "Arrived at sender"
    const val STATUS_RIDER_ON_WAY_RECIPIENT = "Rider is on the way to recipient"
    const val STATUS_ARRIVED_AT_RECIPIENT = "Arrived at recipient"
    const val STATUS_RIDER_ON_WAY_VENDOR = "Rider is on the way to vendor"
    const val STATUS_ARRIVED_AT_VENDOR = "Arrived at vendor"
    const val STATUS_DELIVERED = "Delivered"

    // Rider Request status strings
    const val REQ_SEARCHING = "Searching"
    const val REQ_RIDER_ASSIGNED = "Rider assigned"
    const val REQ_ARRIVED_AT_MERCHANT = "Arrived at merchant"
    const val REQ_PICKED_UP = "Picked up"
    const val REQ_ON_THE_WAY = "On the way"
    const val REQ_DELIVERED = "Delivered"

    // Service Type keys (exactly 5 services)
    const val SERVICE_FOOD = "food"
    const val SERVICE_INSTANT = "convenience"
    const val SERVICE_PARCEL = "parcel"
    const val SERVICE_PABILI = "pabili"
    const val SERVICE_REQUEST = "request_rider"

    val ALL_SERVICES = listOf(
        SERVICE_FOOD,
        SERVICE_INSTANT,
        SERVICE_PARCEL,
        SERVICE_PABILI,
        SERVICE_REQUEST
    )

    fun getServiceDisplayName(serviceType: String): String {
        return when (serviceType.lowercase()) {
            SERVICE_FOOD -> "Delivio Food"
            SERVICE_INSTANT -> "Delivio Instant"
            SERVICE_PARCEL -> "Delivio Parcel"
            SERVICE_PABILI -> "Delivio Pabili"
            SERVICE_REQUEST -> "Delivio Request"
            else -> "Delivio Delivery"
        }
    }

    fun getServiceBadge(serviceType: String): String {
        return when (serviceType.lowercase()) {
            SERVICE_FOOD -> "Food"
            SERVICE_INSTANT -> "Instant"
            SERVICE_PARCEL -> "Parcel"
            SERVICE_PABILI -> "Pabili"
            SERVICE_REQUEST -> "Request"
            else -> "Food"
        }
    }
}
