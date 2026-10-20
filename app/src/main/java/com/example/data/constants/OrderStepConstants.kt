package com.example.data.constants

object OrderStepConstants {
    // Exact riderStep values per specification
    const val STEP_TO_MERCHANT = "to_merchant"
    const val STEP_AT_MERCHANT = "at_merchant"
    const val STEP_PICKED_UP = "picked_up"
    const val STEP_TO_CUSTOMER = "to_customer"
    const val STEP_AT_CUSTOMER = "at_customer"
    const val STEP_COMPLETED = "completed"

    const val STEP_TO_SENDER = "to_sender"
    const val STEP_AT_SENDER = "at_sender"
    const val STEP_TO_RECIPIENT = "to_recipient"
    const val STEP_AT_RECIPIENT = "at_recipient"

    const val STEP_TO_VENDOR = "to_vendor"
    const val STEP_AT_VENDOR = "at_vendor"

    // Legacy aliases for backward compatibility
    const val STEP_HEADING_TO_MERCHANT = "heading_to_merchant"
    const val STEP_ARRIVED_AT_MERCHANT = "arrived_at_merchant"
    const val STEP_HEADING_TO_CUSTOMER = "heading_to_customer"
    const val STEP_ARRIVED_AT_CUSTOMER = "arrived_at_customer"
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

    fun isCustomerAddressRevealed(serviceType: String, riderStep: String): Boolean {
        val step = riderStep.lowercase().trim()
        return when (serviceType.lowercase().trim()) {
            SERVICE_PARCEL -> step in listOf(STEP_TO_RECIPIENT, STEP_AT_RECIPIENT, STEP_HEADING_TO_CUSTOMER, STEP_ARRIVED_AT_CUSTOMER)
            else -> step in listOf(STEP_TO_CUSTOMER, STEP_AT_CUSTOMER, STEP_HEADING_TO_CUSTOMER, STEP_ARRIVED_AT_CUSTOMER)
        }
    }

    fun isPickupStage(serviceType: String, riderStep: String): Boolean {
        val step = riderStep.lowercase().trim()
        return when (serviceType.lowercase().trim()) {
            SERVICE_PARCEL -> step in listOf(STEP_TO_SENDER, STEP_AT_SENDER, STEP_HEADING_TO_MERCHANT, STEP_ARRIVED_AT_MERCHANT)
            SERVICE_PABILI -> step in listOf(STEP_TO_VENDOR, STEP_AT_VENDOR, STEP_HEADING_TO_MERCHANT, STEP_ARRIVED_AT_MERCHANT)
            else -> step in listOf(STEP_TO_MERCHANT, STEP_AT_MERCHANT, STEP_HEADING_TO_MERCHANT, STEP_ARRIVED_AT_MERCHANT)
        }
    }

    fun getStageTitle(serviceType: String, riderStep: String): String {
        val step = riderStep.lowercase().trim()
        return when (serviceType.lowercase().trim()) {
            SERVICE_PARCEL -> when (step) {
                STEP_TO_SENDER, STEP_HEADING_TO_MERCHANT -> "Heading to Sender"
                STEP_AT_SENDER, STEP_ARRIVED_AT_MERCHANT -> "At Sender"
                STEP_TO_RECIPIENT, STEP_HEADING_TO_CUSTOMER -> "Heading to Recipient"
                STEP_AT_RECIPIENT, STEP_ARRIVED_AT_CUSTOMER -> "At Recipient"
                STEP_COMPLETED -> "Delivery Completed"
                else -> "On the Way"
            }
            SERVICE_PABILI -> when (step) {
                STEP_TO_VENDOR, STEP_HEADING_TO_MERCHANT -> "Heading to Vendor"
                STEP_AT_VENDOR, STEP_ARRIVED_AT_MERCHANT -> "At Vendor"
                STEP_PICKED_UP -> "Items Picked Up"
                STEP_TO_CUSTOMER, STEP_HEADING_TO_CUSTOMER -> "Heading to Customer"
                STEP_AT_CUSTOMER, STEP_ARRIVED_AT_CUSTOMER -> "At Customer"
                STEP_COMPLETED -> "Delivery Completed"
                else -> "On the Way"
            }
            else -> when (step) {
                STEP_TO_MERCHANT, STEP_HEADING_TO_MERCHANT -> "Heading to Merchant"
                STEP_AT_MERCHANT, STEP_ARRIVED_AT_MERCHANT -> "At Merchant"
                STEP_PICKED_UP -> "Order Picked Up"
                STEP_TO_CUSTOMER, STEP_HEADING_TO_CUSTOMER -> "Heading to Customer"
                STEP_AT_CUSTOMER, STEP_ARRIVED_AT_CUSTOMER -> "At Customer"
                STEP_COMPLETED -> "Delivery Completed"
                else -> "On the Way"
            }
        }
    }

    fun getSlideButtonText(serviceType: String, riderStep: String): String {
        val step = riderStep.lowercase().trim()
        return when (serviceType.lowercase().trim()) {
            SERVICE_PARCEL -> when (step) {
                STEP_TO_SENDER, STEP_HEADING_TO_MERCHANT -> "Slide to confirm Arrival at Sender"
                STEP_AT_SENDER, STEP_ARRIVED_AT_MERCHANT -> "Slide to confirm Picked Up"
                STEP_TO_RECIPIENT, STEP_HEADING_TO_CUSTOMER -> "Slide to confirm Arrival at Recipient"
                STEP_AT_RECIPIENT, STEP_ARRIVED_AT_CUSTOMER -> "Slide to Complete Delivery"
                else -> "Slide to Complete Delivery"
            }
            SERVICE_PABILI -> when (step) {
                STEP_TO_VENDOR, STEP_HEADING_TO_MERCHANT -> "Slide to confirm Arrival at Merchant"
                STEP_AT_VENDOR, STEP_ARRIVED_AT_MERCHANT -> "Slide to confirm Picked Up"
                STEP_PICKED_UP -> "Slide to start heading to Customer"
                STEP_TO_CUSTOMER, STEP_HEADING_TO_CUSTOMER -> "Slide to confirm Arrival at Customer"
                STEP_AT_CUSTOMER, STEP_ARRIVED_AT_CUSTOMER -> "Slide to Complete Delivery"
                else -> "Slide to Complete Delivery"
            }
            else -> when (step) {
                STEP_TO_MERCHANT, STEP_HEADING_TO_MERCHANT -> "Slide to confirm Arrival at Merchant"
                STEP_AT_MERCHANT, STEP_ARRIVED_AT_MERCHANT -> "Slide to confirm Picked Up"
                STEP_PICKED_UP -> "Slide to start heading to Customer"
                STEP_TO_CUSTOMER, STEP_HEADING_TO_CUSTOMER -> "Slide to confirm Arrival at Customer"
                STEP_AT_CUSTOMER, STEP_ARRIVED_AT_CUSTOMER -> "Slide to Complete Delivery"
                else -> "Slide to Complete Delivery"
            }
        }
    }
}
