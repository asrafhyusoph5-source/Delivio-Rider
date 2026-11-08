package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.constants.AuthConstants
import com.example.data.constants.OrderStepConstants
import com.example.data.models.Order
import com.example.data.models.Rider
import com.example.data.models.RouteStop
import com.example.data.models.OrderPayBreakdown
import com.example.data.models.Quest
import com.example.data.models.QuestGoal
import com.example.data.models.QuestClaim
import com.example.data.models.QuestProgress
import com.example.data.models.formatGoalDescription
import com.example.data.models.SupportTicket
import com.example.data.models.TierLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class ExampleRobolectricTest {

    @Test
    fun `verify app name resource id`() {
        assertTrue(R.string.app_name > 0)
    }

    @Test
    fun `verify demo auth constants`() {
        assertEquals("testr@gmail.com", AuthConstants.DEMO_EMAIL)
        assertEquals("Lidesma@1234", AuthConstants.DEMO_PASSWORD)
        assertEquals("rider", AuthConstants.DEMO_ROLE)
    }

    @Test
    fun `verify monthly tier rides and coin thresholds`() {
        val tiers = TierLevel.values()
        assertEquals(4, tiers.size)

        val bronze = tiers.first { it.title == "Bronze" }
        assertEquals(500, bronze.monthlyRidesRequired)
        assertEquals(200, bronze.rewardCoins)

        val silver = tiers.first { it.title == "Silver" }
        assertEquals(650, silver.monthlyRidesRequired)
        assertEquals(500, silver.rewardCoins)

        val gold = tiers.first { it.title == "Gold" }
        assertEquals(850, gold.monthlyRidesRequired)
        assertEquals(700, gold.rewardCoins)

        val platinum = tiers.first { it.title.contains("Platinum") }
        assertEquals(1000, platinum.monthlyRidesRequired)
        assertEquals(1000, platinum.rewardCoins)
    }

    @Test
    fun `verify platform order step constants`() {
        assertEquals("food", OrderStepConstants.SERVICE_FOOD)
        assertEquals("convenience", OrderStepConstants.SERVICE_INSTANT)
        assertEquals("parcel", OrderStepConstants.SERVICE_PARCEL)
        assertEquals("pabili", OrderStepConstants.SERVICE_PABILI)
        assertEquals("request_rider", OrderStepConstants.SERVICE_REQUEST)

        assertEquals("heading_to_merchant", OrderStepConstants.STEP_HEADING_TO_MERCHANT)
        assertEquals("arrived_at_merchant", OrderStepConstants.STEP_ARRIVED_AT_MERCHANT)
        assertEquals("picked_up", OrderStepConstants.STEP_PICKED_UP)
        assertEquals("heading_to_customer", OrderStepConstants.STEP_HEADING_TO_CUSTOMER)
        assertEquals("arrived_at_customer", OrderStepConstants.STEP_ARRIVED_AT_CUSTOMER)
        assertEquals("delivered", OrderStepConstants.STEP_DELIVERED)
    }

    @Test
    fun `verify rider profile immutability and user mapping`() {
        val rider = Rider(
            uid = "rider_123",
            riderCode = "RD-9821",
            name = "John Doe",
            email = "john@delivio.ph",
            phone = "+63 912 345 6789"
        )
        val user = rider.user
        assertEquals("rider_123", user.id)
        assertEquals("John Doe", user.name)
        assertEquals("john@delivio.ph", user.email)
        assertEquals("rider", user.role)
    }

    @Test
    fun `verify accept order error details data class`() {
        val errorDetails = com.example.data.models.AcceptOrderErrorDetails(
            exceptionMessage = "PERMISSION_DENIED: Missing or insufficient permissions.",
            orderId = "order_abc_123",
            currentStatus = "Placed Order",
            authUid = "auth_rider_uid_999",
            fieldsWritten = mapOf(
                "riderId" to "auth_rider_uid_999",
                "riderName" to "Juan Dela Cruz",
                "riderStep" to OrderStepConstants.STEP_HEADING_TO_MERCHANT
            )
        )

        assertEquals("PERMISSION_DENIED: Missing or insufficient permissions.", errorDetails.exceptionMessage)
        assertEquals("order_abc_123", errorDetails.orderId)
        assertEquals("Placed Order", errorDetails.currentStatus)
        assertEquals("auth_rider_uid_999", errorDetails.authUid)
        assertEquals(3, errorDetails.fieldsWritten.size)
        assertEquals("auth_rider_uid_999", errorDetails.fieldsWritten["riderId"])
    }

    @Test
    fun `verify payment parsing logic`() {
        // COD variants
        assertEquals(com.example.data.models.PaymentType.COD, com.example.data.models.parsePaymentType("cod"))
        assertEquals(com.example.data.models.PaymentType.COD, com.example.data.models.parsePaymentType("  COD  "))
        assertEquals(com.example.data.models.PaymentType.COD, com.example.data.models.parsePaymentType("cash"))
        assertEquals(com.example.data.models.PaymentType.COD, com.example.data.models.parsePaymentType("cash on delivery"))

        // Paid Online variants
        assertEquals(com.example.data.models.PaymentType.PAID_ONLINE, com.example.data.models.parsePaymentType("wallet"))
        assertEquals(com.example.data.models.PaymentType.PAID_ONLINE, com.example.data.models.parsePaymentType("online"))
        assertEquals(com.example.data.models.PaymentType.PAID_ONLINE, com.example.data.models.parsePaymentType(" ONLINE "))

        // Unknown variants (never defaults to Paid Online)
        assertEquals(com.example.data.models.PaymentType.UNKNOWN, com.example.data.models.parsePaymentType(null))
        assertEquals(com.example.data.models.PaymentType.UNKNOWN, com.example.data.models.parsePaymentType(""))
        assertEquals(com.example.data.models.PaymentType.UNKNOWN, com.example.data.models.parsePaymentType("credit_card_stripe"))
    }

    @Test
    fun `verify payment provider labels`() {
        assertEquals("Delivio Wallet", com.example.data.models.formatPaymentProvider("wallet"))
        assertEquals("GCash", com.example.data.models.formatPaymentProvider("gcash"))
        assertEquals("Maya", com.example.data.models.formatPaymentProvider("maya"))
        assertEquals("Bank", com.example.data.models.formatPaymentProvider("bank"))
        assertEquals("Card", com.example.data.models.formatPaymentProvider("card"))
    }

    @Test
    fun `verify cash to collect calculation for all services`() {
        // 1. Food COD with cashToCollect
        val foodOrder = com.example.data.models.Order(
            serviceType = "food",
            paymentMethod = "cod",
            cashToCollect = 350.0,
            feesTotal = 320.0
        )
        assertEquals(350.0, foodOrder.cashAmountToCollect, 0.001)

        // Food COD falling back to feesTotal
        val foodOrderFallback = com.example.data.models.Order(
            serviceType = "food",
            paymentMethod = "cod",
            cashToCollect = 0.0,
            feesTotal = 280.0
        )
        assertEquals(280.0, foodOrderFallback.cashAmountToCollect, 0.001)

        // 2. Parcel COD with sender collection
        val parcelSender = com.example.data.models.Order(
            serviceType = "parcel",
            paymentMethod = "cod",
            cashToCollect = 150.0,
            cashCollectAt = "sender"
        )
        assertEquals(150.0, parcelSender.cashAmountToCollect, 0.001)
        assertTrue(parcelSender.parcelCollectAtSender)

        // 3. Pabili COD with estimated total + fees
        val pabiliOrder = com.example.data.models.Order(
            serviceType = "pabili",
            paymentMethod = "cash",
            pabiliEstimatedTotal = 500.0,
            feesDeliveryFee = 60.0,
            feesServiceFee = 25.0
        )
        assertEquals(585.0, pabiliOrder.cashAmountToCollect, 0.001)

        // Pabili COD with final receipt total
        val pabiliReceipt = com.example.data.models.Order(
            serviceType = "pabili",
            paymentMethod = "cash",
            pabiliEstimatedTotal = 500.0,
            finalReceiptTotal = 540.0,
            feesDeliveryFee = 60.0,
            feesServiceFee = 25.0
        )
        assertEquals(625.0, pabiliReceipt.cashAmountToCollect, 0.001)

        // 4. Request Rider COD
        val requestOrder = com.example.data.models.Order(
            serviceType = "request_rider",
            paymentMethod = "cod",
            codAmount = 400.0,
            feesDeliveryFee = 75.0
        )
        assertEquals(400.0, requestOrder.cashAmountToCollect, 0.001)

        // 5. Paid online / wallet order (never asks for cash)
        val onlineOrder = com.example.data.models.Order(
            serviceType = "food",
            paymentMethod = "online",
            paymentProvider = "gcash",
            feesTotal = 400.0
        )
        assertEquals(0.0, onlineOrder.cashAmountToCollect, 0.001)
        assertEquals("GCash", onlineOrder.paymentProviderLabel)

        // 6. Unknown payment (never asks for cash)
        val unknownOrder = com.example.data.models.Order(
            serviceType = "food",
            paymentMethod = "",
            feesTotal = 400.0
        )
        assertEquals(0.0, unknownOrder.cashAmountToCollect, 0.001)
        assertTrue(unknownOrder.isPaymentUnknown)
    }

    @Test
    fun `verify rider wallet balance and remittance due calculation`() {
        val rider = Rider(
            uid = "rider_abc",
            name = "Rider Test",
            walletBalance = 1500.0,
            cashRemitted = 300.0
        )
        assertEquals(1500.0, rider.walletBalance, 0.001)
        assertEquals(300.0, rider.cashRemitted, 0.001)

        val totalCod = 800.0
        val remittanceDue = (totalCod - rider.cashRemitted).coerceAtLeast(0.0)
        assertEquals(500.0, remittanceDue, 0.001)
    }

    @Test
    fun `verify wallet transaction model formatting and sorting`() {
        val cashInTx = com.example.data.models.WalletTransaction(
            id = "tx_1",
            riderId = "rider_abc",
            type = "cash_in",
            amount = 500.0,
            note = "Admin Cash In Bonus",
            dateText = "Oct 22, 2026 • 09:30 AM",
            dateRaw = 1729589400000L,
            balanceAfter = 1500.0
        )
        assertTrue(cashInTx.isPositive)
        assertEquals("+500.00", cashInTx.displayAmount)
        assertEquals("Cash In", cashInTx.formattedType)
        assertEquals(1500.0, cashInTx.balanceAfter ?: 0.0, 0.001)

        val adjustmentDeduction = com.example.data.models.WalletTransaction(
            id = "tx_2",
            riderId = "rider_abc",
            type = "adjustment",
            amount = -150.0,
            note = "Correction adjustment",
            dateText = "Oct 22, 2026 • 10:00 AM",
            dateRaw = 1729591200000L,
            balanceAfter = 1350.0
        )
        org.junit.Assert.assertFalse(adjustmentDeduction.isPositive)
        assertEquals("-150.00", adjustmentDeduction.displayAmount)
        assertEquals("Adjustment", adjustmentDeduction.formattedType)
        assertEquals(1350.0, adjustmentDeduction.balanceAfter ?: 0.0, 0.001)

        // Verify in-code sorting by dateRaw newest first (no orderBy in query)
        val list = listOf(cashInTx, adjustmentDeduction).sortedByDescending { it.dateRaw }
        assertEquals("tx_2", list.first().id)
        assertEquals("tx_1", list.last().id)
    }

    @Test
    fun `verify pickup orders are recognized and flagged for exclusion`() {
        val pickupOrder = Order(
            id = "ord_pickup",
            fulfillment = "pickup",
            status = OrderStepConstants.STATUS_READY
        )
        assertTrue(pickupOrder.isPickup)

        val deliveryOrder = Order(
            id = "ord_delivery",
            fulfillment = "delivery",
            status = OrderStepConstants.STATUS_READY
        )
        assertFalse(deliveryOrder.isPickup)
    }

    @Test
    fun `verify scheduled order chip text and merchant acceptance logic`() {
        val scheduledOrder = Order(
            id = "ord_sched",
            isScheduled = true,
            scheduledDateText = "Oct 23, 5:30 PM",
            status = OrderStepConstants.STATUS_PREPARING,
            merchantAccepted = true
        )
        assertTrue(scheduledOrder.isScheduled)
        assertEquals("Scheduled for Oct 23, 5:30 PM", scheduledOrder.scheduledChipText)

        // Acceptance check
        val isReadyForRider = scheduledOrder.merchantAccepted || scheduledOrder.status == OrderStepConstants.STATUS_PREPARING
        assertTrue(isReadyForRider)
    }

    @Test
    fun `verify tip chip text and net earnings with zero commission on tip`() {
        val orderWithTip = Order(
            id = "ord_tip_1",
            feesDeliveryFee = 100.0,
            deliveryFee = 100.0,
            tip = 35.0,
            feesTotal = 135.0,
            paymentMethod = "cod"
        )

        assertEquals("Tip ₱35", orderWithTip.tipChipText)
        // deliveryFee 100 * (1 - 0.20) + tip 35 = 80 + 35 = 115
        assertEquals(115.0, orderWithTip.estimatedEarnings, 0.001)

        // For COD, the cash to collect is fees.total which already includes the tip
        assertEquals(135.0, orderWithTip.cashAmountToCollect, 0.001)
    }

    @Test
    fun `verify shared wallet linked customer account and merged history`() {
        val riderWithLinkedCustomer = Rider(
            uid = "rider_99",
            name = "Rider With Customer Account",
            walletBalance = 250.0,
            linkedCustomerUid = "customer_88"
        )
        assertEquals("customer_88", riderWithLinkedCustomer.linkedCustomerUid)

        val riderTx = com.example.data.models.WalletTransaction(
            id = "tx_rider",
            riderId = "rider_99",
            amount = 100.0,
            dateRaw = 1000L
        )
        val customerTx = com.example.data.models.WalletTransaction(
            id = "tx_customer",
            customerId = "customer_88",
            amount = 50.0,
            dateRaw = 2000L
        )

        val merged = (listOf(riderTx) + listOf(customerTx))
            .distinctBy { it.id }
            .sortedByDescending { it.dateRaw }

        assertEquals(2, merged.size)
        assertEquals("tx_customer", merged.first().id)
        assertEquals("tx_rider", merged.last().id)
    }

    @Test
    fun `verify vehicle types and services by vehicle mapping`() {
        // Motorcycle services
        val motoServices = OrderStepConstants.SERVICES_BY_VEHICLE[OrderStepConstants.VEHICLE_MOTORCYCLE]
        assertEquals(listOf("food", "convenience", "parcel", "pabili", "request_rider", "taxi"), motoServices)

        // Tricycle services
        val trikeServices = OrderStepConstants.SERVICES_BY_VEHICLE[OrderStepConstants.VEHICLE_TRICYCLE]
        assertEquals(listOf("parcel", "taxi"), trikeServices)

        // Car 4-seater services
        val car4Services = OrderStepConstants.SERVICES_BY_VEHICLE[OrderStepConstants.VEHICLE_CAR4]
        assertEquals(listOf("parcel", "taxi"), car4Services)

        // Car 8-seater services
        val car8Services = OrderStepConstants.SERVICES_BY_VEHICLE[OrderStepConstants.VEHICLE_CAR8]
        assertEquals(listOf("parcel", "taxi"), car8Services)

        // Vehicle display names
        assertEquals("Motorcycle", OrderStepConstants.getVehicleDisplayName(OrderStepConstants.VEHICLE_MOTORCYCLE))
        assertEquals("Tricycle", OrderStepConstants.getVehicleDisplayName(OrderStepConstants.VEHICLE_TRICYCLE))
        assertEquals("4 Seater Car", OrderStepConstants.getVehicleDisplayName(OrderStepConstants.VEHICLE_CAR4))
        assertEquals("8 Seater Car", OrderStepConstants.getVehicleDisplayName(OrderStepConstants.VEHICLE_CAR8))
    }

    @Test
    fun `verify taxi earnings formula with default and custom commission`() {
        // Taxi order with 15% default commission: net = fees.total x (1 - 0.15)
        val taxiOrderDefault = Order(
            id = "txi_1",
            serviceType = OrderStepConstants.SERVICE_TAXI,
            feesTotal = 200.0,
            platformCommission = 15.0,
            tip = 0.0,
            paymentMethod = "cod"
        )
        assertTrue(taxiOrderDefault.isTaxi)
        // 200 * (1 - 0.15) = 170.0
        assertEquals(170.0, taxiOrderDefault.estimatedEarnings, 0.001)

        // Taxi order with tip: net = fees.total x (1 - 0.15) + tip (no commission on tip)
        val taxiOrderWithTip = Order(
            id = "txi_2",
            serviceType = OrderStepConstants.SERVICE_TAXI,
            feesTotal = 200.0,
            platformCommission = 15.0,
            tip = 50.0,
            paymentMethod = "cod"
        )
        assertEquals(220.0, taxiOrderWithTip.estimatedEarnings, 0.001)

        // Taxi COD: fees.total is collected from passenger
        assertEquals(200.0, taxiOrderDefault.cashAmountToCollect, 0.001)
    }

    @Test
    fun `verify taxi stops sequencing and completion`() {
        val taxiOrder = Order(
            id = "txi_stops",
            serviceType = OrderStepConstants.SERVICE_TAXI,
            customerAddress = "Rizal Ave, Puerto Princesa",
            customerArea = "Downtown",
            stops = listOf(
                com.example.data.models.TaxiStop(stopNumber = 3, address = "Airport Terminal", area = "Airport"),
                com.example.data.models.TaxiStop(stopNumber = 4, address = "Hotel Centro", area = "San Pedro")
            ),
            riderStep = "to_stop_2"
        )

        assertEquals(3, taxiOrder.allTaxiStops.size) // Stop 2, Stop 3, Stop 4
        assertEquals(4, taxiOrder.totalStopsCount) // Stop 1 pickup + 3 destination stops
        assertEquals(2, taxiOrder.currentTaxiStopNumber)
        assertFalse(taxiOrder.isAtLastTaxiStop)

        // At Stop 4 (last stop)
        val atLastStop = taxiOrder.copy(riderStep = "to_stop_4")
        assertEquals(4, atLastStop.currentTaxiStopNumber)
        assertTrue(atLastStop.isAtLastTaxiStop)

        // Slide button text
        assertEquals("Slide to confirm Arrival at Pickup", OrderStepConstants.getSlideButtonText(OrderStepConstants.SERVICE_TAXI, "to_pickup", 2, false))
        assertEquals("Slide to Start Trip", OrderStepConstants.getSlideButtonText(OrderStepConstants.SERVICE_TAXI, "at_pickup", 2, false))
        assertEquals("Slide to confirm Arrival at Stop 2", OrderStepConstants.getSlideButtonText(OrderStepConstants.SERVICE_TAXI, "to_stop_2", 2, false))
        assertEquals("Slide to confirm Arrival at Stop 3", OrderStepConstants.getSlideButtonText(OrderStepConstants.SERVICE_TAXI, "to_stop_3", 3, false))
        assertEquals("Slide to Complete Trip", OrderStepConstants.getSlideButtonText(OrderStepConstants.SERVICE_TAXI, "to_stop_4", 4, true))
    }

    @Test
    fun `verify taxi candidate offer criteria`() {
        val carDriver = Rider(
            uid = "driver_car",
            vehicle = com.example.data.models.Vehicle(type = OrderStepConstants.VEHICLE_CAR4, plateNumber = "ABC-1234"),
            cityId = "puerto_princesa",
            isOnline = true,
            activeServices = listOf(OrderStepConstants.SERVICE_PARCEL, OrderStepConstants.SERVICE_TAXI),
            allowedServices = listOf(OrderStepConstants.SERVICE_PARCEL, OrderStepConstants.SERVICE_TAXI)
        )

        val taxiOrderCar4 = Order(
            id = "txi_car4",
            serviceType = OrderStepConstants.SERVICE_TAXI,
            vehicleType = OrderStepConstants.VEHICLE_CAR4,
            cityId = "puerto_princesa",
            pickupDistanceKm = 3.2
        )

        val taxiOrderMoto = Order(
            id = "txi_moto",
            serviceType = OrderStepConstants.SERVICE_TAXI,
            vehicleType = OrderStepConstants.VEHICLE_MOTORCYCLE,
            cityId = "puerto_princesa",
            pickupDistanceKm = 2.0
        )

        val taxiOrderFar = Order(
            id = "txi_far",
            serviceType = OrderStepConstants.SERVICE_TAXI,
            vehicleType = OrderStepConstants.VEHICLE_CAR4,
            cityId = "puerto_princesa",
            pickupDistanceKm = 6.5 // > 5km
        )

        // Car driver matches taxiOrderCar4
        assertTrue(carDriver.vehicle.type == taxiOrderCar4.vehicleType)
        assertTrue(carDriver.cityId == taxiOrderCar4.cityId)
        assertTrue(taxiOrderCar4.pickupDistanceKm <= 5.0)
        assertTrue(carDriver.activeServices.contains(OrderStepConstants.SERVICE_TAXI))

        // Car driver does NOT match motorcycle taxi
        assertFalse(carDriver.vehicle.type == taxiOrderMoto.vehicleType)

        // Far taxi order is rejected (> 5km)
        assertFalse(taxiOrderFar.pickupDistanceKm <= 5.0)
    }

    @Test
    fun `verify food and convenience offer qualification when merchant accepts`() {
        val rider = Rider(
            uid = "rider_food",
            isOnline = true,
            activeServices = listOf(OrderStepConstants.SERVICE_FOOD, OrderStepConstants.SERVICE_INSTANT),
            allowedServices = listOf(OrderStepConstants.SERVICE_FOOD, OrderStepConstants.SERVICE_INSTANT)
        )

        // Food order with status Preparing, riderId null, fulfillment delivery, NO riderRequest
        val acceptedFoodOrder = Order(
            id = "food_1",
            serviceType = OrderStepConstants.SERVICE_FOOD,
            status = OrderStepConstants.STATUS_PREPARING,
            riderId = null,
            fulfillment = "delivery",
            prepMinutes = 20,
            riderRequestStatus = "" // riderRequest does not exist
        )

        assertTrue(acceptedFoodOrder.riderId.isNullOrBlank())
        assertFalse(acceptedFoodOrder.isPickup)
        assertTrue(acceptedFoodOrder.status in listOf(OrderStepConstants.STATUS_PREPARING, OrderStepConstants.STATUS_READY))
        assertEquals("Merchant is preparing - about 20 min", acceptedFoodOrder.prepStatusText)

        // Ready status
        val readyFoodOrder = acceptedFoodOrder.copy(status = OrderStepConstants.STATUS_READY)
        assertEquals("Order is ready", readyFoodOrder.prepStatusText)

        // Placed Order before merchant accepted should not qualify
        val unacceptedOrder = acceptedFoodOrder.copy(
            status = OrderStepConstants.STATUS_PLACED_ORDER,
            merchantAccepted = false
        )
        val s = unacceptedOrder.status.trim().lowercase()
        val qualifies = s in listOf("preparing", "ready") ||
            (unacceptedOrder.merchantAccepted && s != "placed order")
        assertFalse(qualifies)
    }

    @Test
    fun `verify pickup orders are strictly never offered`() {
        val pickupOrder1 = Order(id = "p1", fulfillment = "pickup")
        val pickupOrder2 = Order(id = "p2", fulfillment = "pick_up")
        val pickupOrder3 = Order(id = "p3", fulfillment = "pick-up")
        val deliveryOrder = Order(id = "d1", fulfillment = "delivery")

        assertTrue(pickupOrder1.isPickup)
        assertTrue(pickupOrder2.isPickup)
        assertTrue(pickupOrder3.isPickup)
        assertFalse(deliveryOrder.isPickup)
    }

    @Test
    fun `verify merchant edit detection and live cash to collect updates`() {
        // Initial order before edit
        val originalOrder = Order(
            id = "ord_edit_1",
            serviceType = OrderStepConstants.SERVICE_FOOD,
            paymentMethod = "cod",
            cashToCollect = 300.0,
            feesTotal = 300.0,
            editedAt = null
        )
        assertFalse(originalOrder.isEditedByMerchant)
        assertEquals(300.0, originalOrder.cashAmountToCollect, 0.001)

        // Merchant edits the order (editedAt timestamp set, items/total updated)
        val updatedOrder = originalOrder.copy(
            editedAt = "2026-10-28T21:00:00Z",
            cashToCollect = 450.0,
            feesTotal = 450.0
        )
        assertTrue(updatedOrder.isEditedByMerchant)
        assertEquals(450.0, updatedOrder.cashAmountToCollect, 0.001)

        // If cashToCollect is 0, falls back to feesTotal
        val updatedFallback = originalOrder.copy(
            editedAt = true,
            cashToCollect = 0.0,
            feesTotal = 420.0
        )
        assertTrue(updatedFallback.isEditedByMerchant)
        assertEquals(420.0, updatedFallback.cashAmountToCollect, 0.001)
    }

    @Test
    fun `verify item selections formatting never shows empty parentheses`() {
        val item1 = com.example.data.models.OrderItem(
            name = "Chicken Inasal",
            quantity = 2,
            price = 180.0,
            selections = listOf("Extra Garlic Rice", "Spicy Marinade", "()", "", "  ")
        )
        val validSelections = item1.selections
            .map { it.trim() }
            .filter { it.isNotBlank() && it != "()" && it != "()" }

        assertEquals(2, validSelections.size)
        assertEquals("Extra Garlic Rice", validSelections[0])
        assertEquals("Spicy Marinade", validSelections[1])
        assertFalse(validSelections.contains("()"))
    }

    @Test
    fun `verify city and platform service lock evaluation`() {
        val platformServices = mutableMapOf<String, Boolean>()
        val cityServices = mutableMapOf<String, Boolean>()

        fun isServiceOn(service: String): Boolean {
            val norm = service.lowercase().trim()
            if (platformServices[norm] == false) return false
            if (cityServices[norm] == false) return false
            return true
        }

        // Default: missing means on
        assertTrue(isServiceOn("food"))
        assertTrue(isServiceOn("taxi"))

        // City admin turns off taxi
        cityServices["taxi"] = false
        assertFalse(isServiceOn("taxi"))
        assertTrue(isServiceOn("food"))

        // Platform turns off food
        platformServices["food"] = false
        assertFalse(isServiceOn("food"))

        // City admin turns taxi back on (or removed)
        cityServices["taxi"] = true
        assertTrue(isServiceOn("taxi"))
    }

    @Test
    fun `verify acceptance and cancellation rate calculation formulas`() {
        val logs = listOf(
            com.example.data.models.OfferLogEntry(orderId = "1", serviceType = "food", outcome = "accepted", finalOutcome = "completed"),
            com.example.data.models.OfferLogEntry(orderId = "2", serviceType = "food", outcome = "accepted", finalOutcome = "released"),
            com.example.data.models.OfferLogEntry(orderId = "3", serviceType = "taxi", outcome = "declined"),
            com.example.data.models.OfferLogEntry(orderId = "4", serviceType = "parcel", outcome = "missed")
        )

        val accepted = logs.count { it.outcome == "accepted" } // 2
        val declined = logs.count { it.outcome == "declined" } // 1
        val missed = logs.count { it.outcome == "missed" }     // 1
        val total = accepted + declined + missed               // 4

        val acceptanceRate = (accepted.toDouble() / total) * 100.0
        assertEquals(50.0, acceptanceRate, 0.001)

        val released = logs.count { it.finalOutcome == "released" } // 1
        val cancellationRate = (released.toDouble() / accepted) * 100.0
        assertEquals(50.0, cancellationRate, 0.001)
    }

    @Test
    fun `verify customer review calculation and star bars`() {
        val reviews = listOf(
            com.example.data.models.CustomerReview(id = "r1", ratingRider = 5.0, comment = "Great driver"),
            com.example.data.models.CustomerReview(id = "r2", ratingRider = 4.0, comment = ""),
            com.example.data.models.CustomerReview(id = "r3", ratingRider = 5.0, comment = "Fast delivery")
        )

        val avg = reviews.map { it.ratingRider }.average()
        assertEquals(4.666, avg, 0.01)
        val formattedAvg = String.format(java.util.Locale.US, "%.1f", avg)
        assertEquals("4.7", formattedAvg)

        assertEquals(2, reviews.count { it.stars == 5 })
        assertEquals(1, reviews.count { it.stars == 4 })
        assertEquals(0, reviews.count { it.stars == 3 })
        assertEquals(2, reviews.count { it.comment.isNotBlank() })
    }

    @Test
    fun `verify late tip adds 100 percent to driver earnings with zero commission`() {
        val orderWithLateTip = Order(
            id = "order_tip_1",
            orderNumber = "DLV-9999",
            serviceType = "food",
            baseFare = 60.0,
            distanceFare = 40.0,
            deliveryFee = 100.0,
            platformCommission = 10.0,
            tip = 25.0,
            lateTipAmount = 50.0
        )

        // base delivery = 100, platformCommission = 10 -> netDelivery = 90
        // net = netDelivery (90) + tip (25) + lateTipAmount (50) = 165.0
        assertEquals(165.0, orderWithLateTip.estimatedEarnings, 0.001)

        val taxiOrderWithLateTip = Order(
            id = "taxi_tip_1",
            orderNumber = "TX-8888",
            serviceType = OrderStepConstants.SERVICE_TAXI,
            vehicleType = "car4",
            feesTotal = 200.0,
            platformCommission = 15.0, // 15%
            tip = 30.0,
            lateTipAmount = 40.0
        )

        // net = 200 * (1 - 0.15) + 30 + 40 = 170 + 70 = 240.0
        assertEquals(240.0, taxiOrderWithLateTip.estimatedEarnings, 0.001)
    }

    @Test
    fun `verify BatchConstants poolable evaluation`() {
        val dispatch = com.example.data.constants.DispatchSettings()
        val foodOrder = Order(id = "o1", serviceType = "food", fulfillment = "delivery", isPoolable = true)
        assertTrue(com.example.data.constants.BatchConstants.isPoolable(foodOrder, dispatch))

        val convenienceOrder = Order(id = "o2", serviceType = "convenience", fulfillment = "delivery", isPoolable = true)
        assertTrue(com.example.data.constants.BatchConstants.isPoolable(convenienceOrder, dispatch))

        val pickupOrder = Order(id = "o3", serviceType = "food", fulfillment = "pickup", isPoolable = true)
        assertFalse(com.example.data.constants.BatchConstants.isPoolable(pickupOrder, dispatch))

        val priorityOrder = Order(id = "o4", serviceType = "food", fulfillment = "delivery", badge = "priority", isPoolable = true)
        assertFalse(com.example.data.constants.BatchConstants.isPoolable(priorityOrder, dispatch))

        val parcelOrder = Order(id = "o5", serviceType = "parcel", fulfillment = "delivery", isPoolable = true)
        assertFalse(com.example.data.constants.BatchConstants.isPoolable(parcelOrder, dispatch))
    }

    @Test
    fun `verify BatchConstants isHolding evaluation`() {
        val dispatch = com.example.data.constants.DispatchSettings(holdMinutes = 3.0)
        val now = 1000000L

        // Standard order: endsWhenReady defaults to true
        val standardPreparing = Order(
            id = "o_std",
            badge = "Standard",
            status = OrderStepConstants.STATUS_PREPARING,
            isPoolable = true,
            serviceType = "food",
            fulfillment = "delivery",
            holdUntilMillis = now + 120000L // 2 minutes remaining
        )
        assertTrue(standardPreparing.resolvedEndsWhenReady)
        assertTrue(com.example.data.constants.BatchConstants.isHolding(standardPreparing, dispatch, now))

        // Standard stops holding when Ready (even if now < holdUntil)
        val standardReady = standardPreparing.copy(status = OrderStepConstants.STATUS_READY)
        assertFalse(com.example.data.constants.BatchConstants.isHolding(standardReady, dispatch, now))

        // Standard stops holding when holdUntil expires
        val standardExpired = standardPreparing.copy(holdUntilMillis = now - 1000L)
        assertFalse(com.example.data.constants.BatchConstants.isHolding(standardExpired, dispatch, now))

        // Saver order: endsWhenReady defaults to false
        val saverPreparing = Order(
            id = "o_sav",
            badge = "Saver",
            status = OrderStepConstants.STATUS_PREPARING,
            isPoolable = true,
            serviceType = "food",
            fulfillment = "delivery",
            holdUntilMillis = now + 120000L
        )
        assertFalse(saverPreparing.resolvedEndsWhenReady)
        assertTrue(com.example.data.constants.BatchConstants.isHolding(saverPreparing, dispatch, now))

        // Saver HOLDS even when status becomes Ready while now < holdUntil
        val saverReady = saverPreparing.copy(status = OrderStepConstants.STATUS_READY)
        assertTrue(com.example.data.constants.BatchConstants.isHolding(saverReady, dispatch, now))

        // Saver stops holding ONLY when now >= holdUntil exactly
        val saverExpired = saverReady.copy(holdUntilMillis = now - 1000L)
        assertFalse(com.example.data.constants.BatchConstants.isHolding(saverExpired, dispatch, now))

        // Priority order never holds
        val priorityOrder = standardPreparing.copy(isPriority = true, badge = "Priority")
        assertFalse(com.example.data.constants.BatchConstants.isHolding(priorityOrder, dispatch, now))

        // Explicit endsWhenReady overrides badge default
        val saverWithExplicitEndsWhenReady = saverReady.copy(endsWhenReady = true)
        assertFalse(com.example.data.constants.BatchConstants.isHolding(saverWithExplicitEndsWhenReady, dispatch, now))
    }

    @Test
    fun `verify BatchConstants isPickupPhaseOpen`() {
        val batch = listOf(
            Order(id = "o1", riderStep = OrderStepConstants.STEP_ARRIVED_AT_MERCHANT),
            Order(id = "o2", riderStep = OrderStepConstants.STEP_PICKED_UP)
        )
        // o1 has not been picked up yet -> pickup phase open
        assertTrue(com.example.data.constants.BatchConstants.isPickupPhaseOpen(batch))

        val batchAllPickedUp = listOf(
            Order(id = "o1", riderStep = OrderStepConstants.STEP_PICKED_UP),
            Order(id = "o2", riderStep = OrderStepConstants.STEP_HEADING_TO_CUSTOMER)
        )
        // All picked up -> pickup phase closed
        assertFalse(com.example.data.constants.BatchConstants.isPickupPhaseOpen(batchAllPickedUp))
    }

    @Test
    fun `verify BatchConstants canJoin rules`() {
        val dispatch = com.example.data.constants.DispatchSettings(
            poolMaxPickupGapKm = 1.0,
            poolMaxDropoffGapKm = 2.0,
            maxBatch = 2
        )

        val batchOrder = Order(
            id = "b1",
            merchantId = "m1",
            serviceType = "food",
            fulfillment = "delivery",
            cityId = "puerto_princesa",
            merchantLat = 9.7390,
            merchantLng = 118.7350,
            customerLat = 9.7400,
            customerLng = 118.7360,
            isPoolable = true,
            riderStep = OrderStepConstants.STEP_HEADING_TO_MERCHANT
        )
        val activeBatch = listOf(batchOrder)

        // Close order that can join
        val candidateOrder = Order(
            id = "c1",
            merchantId = "m2",
            serviceType = "convenience",
            fulfillment = "delivery",
            cityId = "puerto_princesa",
            merchantLat = 9.7395,
            merchantLng = 118.7355,
            customerLat = 9.7405,
            customerLng = 118.7365,
            isPoolable = true
        )
        assertTrue(com.example.data.constants.BatchConstants.canJoin(activeBatch, candidateOrder, dispatch))

        // Same merchant cannot be joined if already picked up
        val candidateSamePickedMerchant = candidateOrder.copy(
            merchantId = "m1"
        )
        val batchWithPickedUpM1 = listOf(batchOrder.copy(riderStep = OrderStepConstants.STEP_PICKED_UP))
        assertFalse(com.example.data.constants.BatchConstants.canJoin(batchWithPickedUpM1, candidateSamePickedMerchant, dispatch))

        // Far away pickup > 1.0 km
        val candidateFar = candidateOrder.copy(
            merchantLat = 9.8000,
            merchantLng = 118.8000
        )
        assertFalse(com.example.data.constants.BatchConstants.canJoin(activeBatch, candidateFar, dispatch))
    }

    @Test
    fun `verify Rider Pay calculation formulas`() {
        val paySettings = com.example.data.constants.RiderPaySettings(
            single = com.example.data.constants.RiderPayRate(minFare = 45.0, includedKm = 2.0, perKmExtra = 10.0),
            doubleFirst = com.example.data.constants.RiderPayRate(minFare = 40.0, includedKm = 2.0, perKmExtra = 10.0),
            doubleSecond = com.example.data.constants.RiderPayRate(minFare = 35.0, includedKm = 2.0, perKmExtra = 10.0),
            pickup = com.example.data.constants.RiderPayPickupRate(minFare = 15.0, freeKm = 1.0, perKmExtra = 8.0)
        )

        val order = Order(
            id = "o_pay",
            deliveryDistanceKm = 3.5 // 1.5 km over includedKm
        )

        // Single order: 45 + (3.5 - 2.0) * 10 = 45 + 15 = 60.0
        val singleFee = com.example.data.constants.BatchConstants.calculateDeliveryFee(order, rank = 1, isBatch = false, paySettings = paySettings, specialRadiusExtra = 0.0)
        assertEquals(60.0, singleFee, 0.001)

        // Double batch rank 1: 40 + 1.5 * 10 = 55.0
        val rank1Fee = com.example.data.constants.BatchConstants.calculateDeliveryFee(order, rank = 1, isBatch = true, paySettings = paySettings, specialRadiusExtra = 0.0)
        assertEquals(55.0, rank1Fee, 0.001)

        // Double batch rank 2: 35 + 1.5 * 10 = 50.0
        val rank2Fee = com.example.data.constants.BatchConstants.calculateDeliveryFee(order, rank = 2, isBatch = true, paySettings = paySettings, specialRadiusExtra = 0.0)
        assertEquals(50.0, rank2Fee, 0.001)

        // Pickup fee for rank 1: distance 2.5 km -> 15 + (2.5 - 1.0) * 8 = 15 + 12 = 27.0
        val pickupFeeRank1 = com.example.data.constants.BatchConstants.calculatePickupFee(pickupDistanceKm = 2.5, rank = 1, paySettings = paySettings)
        assertEquals(27.0, pickupFeeRank1, 0.001)

        // Pickup fee for rank 2+ is 0
        val pickupFeeRank2 = com.example.data.constants.BatchConstants.calculatePickupFee(pickupDistanceKm = 2.5, rank = 2, paySettings = paySettings)
        assertEquals(0.0, pickupFeeRank2, 0.001)
    }

    @Test
    fun `verify RiderEarning data class and order estimated earnings integration`() {
        val earning = com.example.data.models.RiderEarning(
            bookingFee = 55.0,
            pickupFee = 20.0,
            extra = 10.0,
            tip = 25.0,
            total = 110.0,
            rank = 1,
            batchId = "batch_123"
        )
        val orderWithEarning = Order(
            id = "o_delivered",
            riderEarning = earning,
            lateTipAmount = 15.0
        )
        // total (110.0) + lateTipAmount (15.0) = 125.0
        assertEquals(125.0, orderWithEarning.estimatedEarnings, 0.001)
    }

    @Test
    fun `verify Batch Stop Order visits all merchants first then all customers`() {
        val driverLat = 9.7390
        val driverLng = 118.7350

        // Order A: Merchant at (9.7400, 118.7350), Customer at (9.7500, 118.7350)
        val orderA = Order(
            id = "ord_A",
            orderNumber = "DLV-A",
            merchantName = "Merchant A",
            merchantLat = 9.7400,
            merchantLng = 118.7350,
            customerName = "Customer A",
            customerLat = 9.7500,
            customerLng = 118.7350,
            riderStep = OrderStepConstants.STEP_TO_MERCHANT
        )

        // Order B: Merchant at (9.7450, 118.7350), Customer at (9.7550, 118.7350)
        val orderB = Order(
            id = "ord_B",
            orderNumber = "DLV-B",
            merchantName = "Merchant B",
            merchantLat = 9.7450,
            merchantLng = 118.7350,
            customerName = "Customer B",
            customerLat = 9.7550,
            customerLng = 118.7350,
            riderStep = OrderStepConstants.STEP_TO_MERCHANT
        )

        val stops = com.example.data.models.RouteStopCalculator.computeBatchStops(
            activeOrders = listOf(orderA, orderB),
            driverLat = driverLat,
            driverLng = driverLng
        )

        assertEquals(4, stops.size)

        // First 2 stops MUST be merchants (Green pins)
        assertEquals(com.example.data.models.StopType.MERCHANT, stops[0].type)
        assertEquals(com.example.data.models.StopType.MERCHANT, stops[1].type)

        // Next 2 stops MUST be customers (Red pins)
        assertEquals(com.example.data.models.StopType.CUSTOMER, stops[2].type)
        assertEquals(com.example.data.models.StopType.CUSTOMER, stops[3].type)

        // Merchant A is closer to driver (9.7400 vs 9.7450)
        assertEquals("ord_A", stops[0].orderId)
        assertEquals(1, stops[0].stopNumber)
        assertEquals("ord_B", stops[1].orderId)
        assertEquals(2, stops[1].stopNumber)

        // Continuing numbering for customers: 3 and 4
        assertEquals(3, stops[2].stopNumber)
        assertEquals(4, stops[3].stopNumber)

        // Customer A (9.7500) is closer to last merchant B (9.7450) than Customer B (9.7550)
        assertEquals("ord_A", stops[2].orderId)
        assertEquals("ord_B", stops[3].orderId)
    }

    @Test
    fun `verify finished stops keep their numbers and pins disappear when remaining reordered`() {
        val driverLat = 9.7390
        val driverLng = 118.7350

        val orderA = Order(
            id = "ord_A",
            orderNumber = "DLV-A",
            merchantName = "Merchant A",
            merchantLat = 9.7400,
            merchantLng = 118.7350,
            customerName = "Customer A",
            customerLat = 9.7500,
            customerLng = 118.7350,
            riderStep = OrderStepConstants.STEP_PICKED_UP // Merchant A picked up (Finished)
        )

        val orderB = Order(
            id = "ord_B",
            orderNumber = "DLV-B",
            merchantName = "Merchant B",
            merchantLat = 9.7450,
            merchantLng = 118.7350,
            customerName = "Customer B",
            customerLat = 9.7550,
            customerLng = 118.7350,
            riderStep = OrderStepConstants.STEP_TO_MERCHANT
        )

        // Initial stops before order C joins
        val initialStops = com.example.data.models.RouteStopCalculator.computeBatchStops(
            activeOrders = listOf(orderA, orderB),
            driverLat = driverLat,
            driverLng = driverLng
        )

        // Stop 1 was Merchant A (Finished)
        val stop1 = initialStops.first { it.orderId == "ord_A" && it.isMerchant }
        assertTrue(stop1.isFinished)
        assertEquals(1, stop1.stopNumber)
        assertEquals(com.example.data.models.StopStatus.DONE, stop1.status)

        // Order C joins the batch!
        val orderC = Order(
            id = "ord_C",
            orderNumber = "DLV-C",
            merchantName = "Merchant C",
            merchantLat = 9.7420,
            merchantLng = 118.7350,
            customerName = "Customer C",
            customerLat = 9.7480,
            customerLng = 118.7350,
            riderStep = OrderStepConstants.STEP_TO_MERCHANT
        )

        val updatedStops = com.example.data.models.RouteStopCalculator.computeBatchStops(
            activeOrders = listOf(orderA, orderB, orderC),
            driverLat = driverLat,
            driverLng = driverLng,
            previousStops = initialStops
        )

        // Finished Stop 1 STILL retains stopNumber = 1 and status = DONE
        val updatedStop1 = updatedStops.first { it.orderId == "ord_A" && it.isMerchant }
        assertTrue(updatedStop1.isFinished)
        assertEquals(1, updatedStop1.stopNumber)

        // Remaining active stops: finished stop's pin disappears (!it.isFinished)
        val activeMapPins = updatedStops.filter { !it.isFinished }
        assertFalse(activeMapPins.any { it.orderId == "ord_A" && it.isMerchant })
        assertEquals(5, activeMapPins.size) // 2 remaining merchants + 3 customers

        // All remaining merchants come before customers
        val merchantIndices = activeMapPins.filter { it.isMerchant }.map { it.stopNumber }
        val customerIndices = activeMapPins.filter { it.isCustomer }.map { it.stopNumber }
        assertTrue(merchantIndices.maxOrNull()!! < customerIndices.minOrNull()!!)
    }

    @Test
    fun `verify OSRM off-route detection threshold 40 meters`() {
        val polyline = listOf(
            org.osmdroid.util.GeoPoint(9.739000, 118.735000),
            org.osmdroid.util.GeoPoint(9.740000, 118.735000)
        )

        // Very close point (~10m) -> NOT off route
        val closeLat = 9.739050
        val closeLng = 118.735050
        assertFalse(com.example.data.models.OsrmRouteService.isOffRoute(closeLat, closeLng, polyline, thresholdMeters = 40.0))

        // Far point (~150m away) -> IS off route (> 40m)
        val farLat = 9.739000
        val farLng = 118.736500
        assertTrue(com.example.data.models.OsrmRouteService.isOffRoute(farLat, farLng, polyline, thresholdMeters = 40.0))
    }

    @Test
    fun `verify HelpCenterReason keys and labels match requirements`() {
        val reasons = com.example.data.models.HelpCenterConstants.REASONS
        assertEquals(10, reasons.size)

        val keys = reasons.map { it.key }
        val expectedKeys = listOf(
            "cant_fit_bag",
            "too_big",
            "accident",
            "wrong_items",
            "merchant_wait",
            "merchant_closed",
            "customer_unreachable",
            "vehicle_breakdown",
            "safety",
            "other"
        )
        assertEquals(expectedKeys, keys)

        val splitCandidates = reasons.filter { it.requiresSplitChecklist }
        assertEquals(2, splitCandidates.size)
        assertTrue(splitCandidates.any { it.key == "cant_fit_bag" && it.label == "Can't fit on my bag" })
        assertTrue(splitCandidates.any { it.key == "too_big" && it.label == "Order is too big or heavy" })
    }

    @Test
    fun `verify SupportTicket status and unresolved check`() {
        val openTicket = com.example.data.models.SupportTicket(
            id = "t_1",
            title = "Box too large for motorcycle bag",
            reason = "cant_fit_bag",
            status = "open",
            splitItems = listOf("Whole Lechon Box", "Party Size Bilao")
        )
        assertTrue(openTicket.isOpen)
        assertTrue(openTicket.isUnresolved)
        assertFalse(openTicket.isResolved)
        assertEquals(2, openTicket.splitItems.size)

        val acceptedTicket = openTicket.copy(status = "accepted")
        assertFalse(acceptedTicket.isOpen)
        assertTrue(acceptedTicket.isAccepted)
        assertTrue(acceptedTicket.isUnresolved)
        assertFalse(acceptedTicket.isResolved)

        val resolvedTicket = openTicket.copy(status = "resolved", resolutionNote = "Second driver dispatched RD-4821")
        assertFalse(resolvedTicket.isOpen)
        assertFalse(resolvedTicket.isAccepted)
        assertFalse(resolvedTicket.isUnresolved)
        assertTrue(resolvedTicket.isResolved)
        assertEquals("Second driver dispatched RD-4821", resolvedTicket.resolutionNote)
    }

    @Test
    fun `verify split delivery for first rider preserves full COD cash collection`() {
        val originalOrder = Order(
            id = "ord_split_parent",
            orderNumber = "PAR100",
            serviceType = "food",
            paymentMethod = "cod",
            feesTotal = 850.0,
            cashToCollect = 850.0,
            splitOut = listOf("Party Platter Ribs"),
            isSplitChild = false
        )

        // First rider still collects full COD amount (fees.total)
        assertEquals(850.0, originalOrder.cashAmountToCollect, 0.001)
        assertTrue(originalOrder.splitOut.isNotEmpty())
        assertFalse(originalOrder.isSplitChild)
    }

    @Test
    fun `verify split delivery for second rider enforces zero COD and fixed rider fee`() {
        val splitChildOrder = Order(
            id = "ord_split_child",
            orderNumber = "CHI200",
            serviceType = "food",
            paymentMethod = "cod",
            feesTotal = 850.0,
            cashToCollect = 850.0,
            riderFeeFixed = 65.0,
            tip = 20.0,
            badge = "SPLIT",
            isSplitChild = true
        )

        // Split child collects ZERO cash
        assertEquals(0.0, splitChildOrder.cashAmountToCollect, 0.001)
        assertTrue(splitChildOrder.isSplitChild)

        // Split child earnings use riderFeeFixed (shown as one delivery fee) + tip
        assertEquals(85.0, splitChildOrder.estimatedEarnings, 0.001)

        val paySettings = com.example.data.constants.RiderPaySettings()
        val deliveryFee = com.example.data.constants.BatchConstants.calculateDeliveryFee(
            order = splitChildOrder,
            rank = 1,
            isBatch = false,
            paySettings = paySettings
        )
        assertEquals(65.0, deliveryFee, 0.001)
    }

    @Test
    fun `verify split child is never poolable`() {
        val dispatch = com.example.data.constants.DispatchSettings()
        val splitChildOrder = Order(
            id = "ord_child",
            serviceType = "food",
            isPoolable = true, // even if dispatch says poolable
            isSplitChild = true,
            badge = "SPLIT"
        )

        // Must never be pooled
        assertFalse(com.example.data.constants.BatchConstants.isPoolable(splitChildOrder, dispatch))
    }

    @Test
    fun `verify routing base url, waypoint cache and route unavailable fallback notice`() {
        // 1. ROUTING_BASE_URL constant check
        assertEquals("https://router.project-osrm.org", com.example.data.models.ROUTING_BASE_URL)

        // 2. Clear cache and verify cache retrieval
        com.example.data.models.OsrmRouteService.clearCache()
        assertNull(com.example.data.models.OsrmRouteService.getCachedResult("118.73500,9.73900"))

        // 3. Fallback direct route result specifies "Route unavailable"
        val fallbackResult = com.example.data.models.OsrmRouteResult(
            fullPoints = emptyList(),
            currentLegPoints = emptyList(),
            futureLegPoints = emptyList(),
            currentLegDistanceKm = 1.0,
            currentLegEtaMinutes = 3,
            totalRemainingDistanceKm = 1.0,
            totalRemainingEtaMinutes = 3,
            etaClockTime = "10:00 AM",
            nextManeuver = null,
            isFallbackDirect = true,
            fallbackNotice = "Route unavailable"
        )
        assertTrue(fallbackResult.isFallbackDirect)
        assertEquals("Route unavailable", fallbackResult.fallbackNotice)
    }

    @Test
    fun `verify order number display helper with fallback to last 6 characters uppercase`() {
        // 1. Order with orderNumber
        val order1 = Order(id = "doc12345678", orderNumber = "DLV-9912")
        assertEquals("#DLV-9912", order1.displayOrderNumber)

        val orderWithHash = Order(id = "doc12345678", orderNumber = "#DLV-9912")
        assertEquals("#DLV-9912", orderWithHash.displayOrderNumber)

        // 2. Order without orderNumber: fallback to # + last 6 chars uppercase
        val orderFallback = Order(id = "abcdef123456", orderNumber = "")
        assertEquals("#123456", orderFallback.displayOrderNumber)

        val orderFallbackLower = Order(id = "firestore_doc_ab89cd", orderNumber = "")
        assertEquals("#AB89CD", orderFallbackLower.displayOrderNumber)

        // 3. RouteStop displayOrderNumber
        val stop1 = RouteStop(orderId = "order_xyz789", orderNumber = "DLV-1234", name = "Shop", address = "Addr", lat = 0.0, lng = 0.0)
        assertEquals("#DLV-1234", stop1.displayOrderNumber)

        val stopFallback = RouteStop(orderId = "order_xyz789", orderNumber = "", name = "Shop", address = "Addr", lat = 0.0, lng = 0.0)
        assertEquals("#XYZ789", stopFallback.displayOrderNumber)

        // 4. OrderPayBreakdown displayOrderNumber
        val bk = OrderPayBreakdown(orderId = "order_mno456", orderNumber = "5521", rank = 1, deliveryFee = 50.0, pickupFee = 15.0, tip = 0.0, totalPay = 65.0)
        assertEquals("#5521", bk.displayOrderNumber)

        val bkFallback = OrderPayBreakdown(orderId = "doc_item_qwe123", orderNumber = "", rank = 1, deliveryFee = 50.0, pickupFee = 15.0, tip = 0.0, totalPay = 65.0)
        assertEquals("#QWE123", bkFallback.displayOrderNumber)

        // 5. SupportTicket displayOrderNumber
        val ticket = SupportTicket(id = "ticket1", orderId = "order_abc789", orderNumber = "8899")
        assertEquals("#8899", ticket.displayOrderNumber)

        val ticketFallback = SupportTicket(id = "ticket2", orderId = "order_abc789", orderNumber = "")
        assertEquals("#ABC789", ticketFallback.displayOrderNumber)
    }

    @Test
    fun `verify booking exclusivity and priority rider capacity and delay rules`() {
        val dispatch = com.example.data.constants.DispatchSettings(
            holdMinutes = 5.0,
            searchRadiusKm = 3.0,
            waveSeconds = 10,
            priorityHeadStartSeconds = 15,
            priorityMaxBatch = 4,
            maxBatch = 2
        )

        val foodOrder1 = Order(
            id = "f1",
            orderNumber = "F001",
            serviceType = "food",
            status = "Preparing",
            isPoolable = true,
            maxBatch = 2
        )
        val foodOrder2 = Order(
            id = "f2",
            orderNumber = "F002",
            serviceType = "food",
            status = "Preparing",
            isPoolable = true,
            maxBatch = 2
        )
        val parcelOrder = Order(
            id = "p1",
            orderNumber = "P001",
            serviceType = "parcel",
            status = "Placed Order",
            isPoolable = false
        )
        val taxiOrder = Order(
            id = "t1",
            orderNumber = "T001",
            serviceType = "taxi",
            vehicleType = "motorcycle",
            status = "Placed Order",
            isPoolable = false
        )

        // 1. Exclusivity: Non-food orders are single bookings only and cannot join active batches
        assertFalse(com.example.data.constants.BatchConstants.canJoin(listOf(foodOrder1), parcelOrder, dispatch))
        assertFalse(com.example.data.constants.BatchConstants.canJoin(listOf(foodOrder1), taxiOrder, dispatch))

        // 2. Normal rider capacity limit
        val fullBatch = listOf(foodOrder1, foodOrder2)
        val candidate3 = Order(
            id = "f3",
            orderNumber = "F003",
            serviceType = "food",
            status = "Preparing",
            isPoolable = true,
            maxBatch = 3
        )
        // For normal rider: maxBatch of orders is 2, so candidate cannot join full batch of 2
        assertFalse(com.example.data.constants.BatchConstants.canJoin(fullBatch, candidate3, dispatch, isPriorityRider = false))

        // For priority rider: priorityMaxBatch is 4, so candidate CAN join batch of 2
        assertTrue(com.example.data.constants.BatchConstants.canJoin(fullBatch, candidate3, dispatch, isPriorityRider = true))

        // 3. Priority wave delay formula
        // Priority rider: min(floor(km) * waveSeconds, priorityHeadStartSeconds)
        // Non-priority rider: priorityHeadStartSeconds + floor(km) * waveSeconds
        val km = 3 // 3 km
        val priorityDelay = kotlin.math.min(km * dispatch.waveSeconds, dispatch.priorityHeadStartSeconds)
        val normalDelay = dispatch.priorityHeadStartSeconds + (km * dispatch.waveSeconds)
        assertEquals(15, priorityDelay) // min(30, 15) = 15
        assertEquals(45, normalDelay)   // 15 + 30 = 45
    }

    @Test
    fun `verify quest applicability rules`() {
        val quest = Quest(
            id = "q1",
            title = "Weekend Rush",
            cityIds = listOf("puerto_princesa"),
            vehicleTypes = listOf("motorcycle"),
            riderIds = emptyList(),
            services = listOf("food", "convenience"),
            status = "active"
        )

        // 1. Matching rider
        assertTrue(quest.appliesTo("puerto_princesa", "motorcycle", "rider_1", listOf("food", "parcel")))

        // 2. Mismatched city
        assertFalse(quest.appliesTo("cebu", "motorcycle", "rider_1", listOf("food")))

        // 3. Mismatched vehicle
        assertFalse(quest.appliesTo("puerto_princesa", "car", "rider_1", listOf("food")))

        // 4. Mismatched services
        assertFalse(quest.appliesTo("puerto_princesa", "motorcycle", "rider_1", listOf("parcel", "taxi")))

        // 5. Specific rider ID restriction
        val privateQuest = quest.copy(riderIds = listOf("rider_special"))
        assertFalse(privateQuest.appliesTo("puerto_princesa", "motorcycle", "rider_1", listOf("food")))
        assertTrue(privateQuest.appliesTo("puerto_princesa", "motorcycle", "rider_special", listOf("food")))

        // 6. Universal quest with empty restrictions applies to anyone
        val universalQuest = Quest(id = "q_all", title = "All Riders")
        assertTrue(universalQuest.appliesTo("any_city", "any_vehicle", "any_rider", emptyList()))
    }

    @Test
    fun `verify quest goal description formatting`() {
        val deliveriesGoal = QuestGoal(
            level = 1,
            target = 18.0,
            reward = 75.0,
            minAcceptanceRate = 95.0,
            maxNoShows = 0
        )
        val deliveriesQuest = Quest(
            id = "q_del",
            title = "Peak Hours",
            metric = "deliveries",
            hourFrom = 11,
            hourTo = 14,
            services = listOf("food")
        )

        val desc = formatGoalDescription(deliveriesGoal, deliveriesQuest)
        // Expected format: "Complete 18 deliveries for ₱75 with a 95% acceptance rate and 0 no-shows between 11:00 - 14:00 on Food"
        assertTrue(desc.contains("Complete 18 deliveries for ₱75"))
        assertTrue(desc.contains("95% acceptance rate"))
        assertTrue(desc.contains("0 no-shows"))
        assertTrue(desc.contains("11:00 - 14:00"))
        assertTrue(desc.contains("Food"))

        val earningsGoal = QuestGoal(
            level = 2,
            target = 1000.0,
            reward = 150.0
        )
        val earningsQuest = Quest(
            id = "q_earn",
            title = "Big Earner",
            metric = "earnings"
        )
        val earnDesc = formatGoalDescription(earningsGoal, earningsQuest)
        assertTrue(earnDesc.contains("Earn ₱1000 for ₱150") || earnDesc.contains("for ₱150"))
        assertFalse(earnDesc.contains("acceptance rate"))
        assertFalse(earnDesc.contains("no-shows"))
    }

    @Test
    fun `verify quest progress calculation and goal qualification`() {
        val now = System.currentTimeMillis()
        val startAtMillis = now - 3600_000L
        val endAtMillis = now + 3600_000L

        val goals = listOf(
            QuestGoal(level = 1, target = 5.0, reward = 50.0, minAcceptanceRate = 80.0, maxNoShows = 0),
            QuestGoal(level = 2, target = 10.0, reward = 120.0, minAcceptanceRate = 90.0, maxNoShows = 0)
        )
        val quest = Quest(
            id = "q_prog",
            title = "Daily Grind",
            metric = "deliveries",
            startAt = com.google.firebase.Timestamp(java.util.Date(startAtMillis)),
            endAt = com.google.firebase.Timestamp(java.util.Date(endAtMillis)),
            goals = goals
        )

        // Case 1: Achieved 6 deliveries, 95% acceptance, 0 no shows -> Qualifies for Level 1 (index 0)
        val progress1 = QuestProgress(
            quest = quest,
            currentValue = 6.0,
            currentDeliveries = 6,
            currentEarnings = 300.0,
            acceptanceRate = 95.0,
            noShows = 0,
            levelReachedIndex = 0,
            currentReward = 50.0,
            nextGoal = goals[1]
        )
        assertEquals(0, progress1.levelReachedIndex)
        assertEquals(50.0, progress1.currentReward, 0.01)
        assertEquals(2, progress1.nextGoal?.level)
        assertFalse(progress1.isCompleted)

        // Case 2: Achieved 10 deliveries, 95% acceptance, 0 no shows -> Completed all goals (Level 2, index 1)
        val progressCompleted = QuestProgress(
            quest = quest,
            currentValue = 10.0,
            currentDeliveries = 10,
            currentEarnings = 500.0,
            acceptanceRate = 95.0,
            noShows = 0,
            levelReachedIndex = 1,
            currentReward = 120.0,
            nextGoal = null
        )
        assertTrue(progressCompleted.isCompleted)
        assertEquals(1.0f, progressCompleted.fraction, 0.001f)
        assertEquals(120.0, progressCompleted.currentReward, 0.01)
        assertNull(progressCompleted.nextGoal)
    }

    @Test
    fun `verify quest claim fixed ID format and status`() {
        val claim = QuestClaim(
            id = "q_weekend_rider_456",
            questId = "q_weekend",
            questTitle = "Weekend Challenge",
            riderId = "rider_456",
            riderName = "Pedro Cruz",
            metric = "deliveries",
            value = 20.0,
            deliveries = 20,
            earnings = 1500.0,
            acceptanceRate = 98.0,
            noShows = 0,
            levelReached = 2,
            rewardAmount = 250.0,
            status = "Pending"
        )

        // Verify document id follows strictly "${questId}_${uid}"
        assertEquals("${claim.questId}_${claim.riderId}", claim.id)
        assertEquals("Pending", claim.status)
        assertEquals(250.0, claim.rewardAmount, 0.01)
    }

    @Test
    fun `verify customer status in double booking`() {
        // In a batch of two orders, when orders are picked up:
        // Order 1 is the next customer stop -> "Rider is on the way"
        // Order 2 is the subsequent customer stop -> "On another delivery"
        val order1 = Order(
            id = "ord_batch_1",
            orderNumber = "1001",
            serviceType = "food",
            status = OrderStepConstants.STATUS_PREPARING,
            merchantLat = 14.550,
            merchantLng = 121.050,
            customerLat = 14.560,
            customerLng = 121.060
        )
        val order2 = Order(
            id = "ord_batch_2",
            orderNumber = "1002",
            serviceType = "food",
            status = OrderStepConstants.STATUS_PREPARING,
            merchantLat = 14.552,
            merchantLng = 121.052,
            customerLat = 14.570,
            customerLng = 121.070
        )
        val batch = listOf(order1, order2)

        // Compute stops from rider position at merchant 1
        val stops = com.example.data.models.RouteStopCalculator.computeBatchStops(batch, 14.550, 121.050)
        // Stops sequence: 2 merchant stops, then customer stops
        assertEquals(4, stops.size)
        assertTrue(stops[0].isMerchant)
        assertTrue(stops[1].isMerchant)
        assertTrue(stops[2].isCustomer)
        assertTrue(stops[3].isCustomer)

        val nextCustStop = stops.firstOrNull { it.isCustomer && !it.isFinished }
        assertEquals("ord_batch_1", nextCustStop?.orderId)

        // Status assignment for order 1 on pickup:
        val statusOrder1 = if (nextCustStop?.orderId == order1.id) {
            OrderStepConstants.STATUS_RIDER_ON_THE_WAY
        } else {
            OrderStepConstants.STATUS_ON_ANOTHER_DELIVERY
        }
        assertEquals(OrderStepConstants.STATUS_RIDER_ON_THE_WAY, statusOrder1)

        // Status assignment for order 2 on pickup:
        val statusOrder2 = if (nextCustStop?.orderId == order2.id) {
            OrderStepConstants.STATUS_RIDER_ON_THE_WAY
        } else {
            OrderStepConstants.STATUS_ON_ANOTHER_DELIVERY
        }
        assertEquals(OrderStepConstants.STATUS_ON_ANOTHER_DELIVERY, statusOrder2)
        assertEquals("On another delivery", OrderStepConstants.STATUS_ON_ANOTHER_DELIVERY)

        // When order 1 is delivered, remaining active is order 2:
        val remaining = listOf(order2)
        val nextStopsAfterDelivery = com.example.data.models.RouteStopCalculator.computeBatchStops(
            remaining,
            order1.customerLat,
            order1.customerLng
        )
        val newNextCustStop = nextStopsAfterDelivery.firstOrNull { it.isCustomer && !it.isFinished }
        assertEquals("ord_batch_2", newNextCustStop?.orderId)
    }

    @Test
    fun `verify split delivery with several riders parent and child properties`() {
        // Parent order with splitOut
        val splitChild1 = com.example.data.models.SplitChildItem(orderId = "child_ord_1", items = listOf("Box A"), seq = 1)
        val splitChild2 = com.example.data.models.SplitChildItem(orderId = "child_ord_2", items = listOf("Box B"), seq = 2)
        val splitOut = com.example.data.models.SplitOutInfo(
            count = 2,
            children = listOf(splitChild1, splitChild2),
            at = null,
            by = "rider_1"
        )
        val parentOrder = Order(
            id = "parent_ord_1",
            orderNumber = "5001",
            paymentMethod = "cash",
            splitOutInfo = splitOut,
            cashToCollect = 550.0,
            isSplitChild = false
        )

        assertTrue(parentOrder.hasSplitOut)
        assertTrue(parentOrder.isSplitParent)
        assertFalse(parentOrder.isSplitChild)
        assertTrue(parentOrder.isCod)
        assertEquals(550.0, parentOrder.cashToCollect, 0.01)

        // Split child order (riders 2 to N)
        val childOrder = Order(
            id = "child_ord_1",
            orderNumber = "5002",
            paymentMethod = "cash",
            isSplitChild = true,
            splitParentOrderId = "parent_ord_1",
            badge = "split",
            cashToCollect = 0.0,
            riderFeeFixed = 65.0
        )

        assertTrue(childOrder.isSplitChild)
        assertFalse(childOrder.isSplitParent)
        // Child order skips COD cash collection
        assertFalse(childOrder.isCod)
        assertEquals(0.0, childOrder.cashToCollect, 0.01)
        assertEquals(65.0, childOrder.riderFeeFixed ?: 0.0, 0.01)

        // Sibling exclusion rule:
        // A rider holding parent_ord_1 or child_ord_1 cannot receive another sibling from the same family
        val activeFamilyIds = setOf(childOrder.splitParentOrderId!!)
        val siblingCandidate = Order(
            id = "child_ord_2",
            orderNumber = "5003",
            isSplitChild = true,
            splitParentOrderId = "parent_ord_1"
        )
        val candidateFamilyId = siblingCandidate.splitParentOrderId
        assertTrue(activeFamilyIds.contains(candidateFamilyId))
    }

    @Test
    fun `verify exact 6 service names, mapping, order, and badges`() {
        // 1. Exact 6 service toggles in order
        val defs = OrderStepConstants.SERVICE_DEFINITIONS
        assertEquals(6, defs.size)

        // 1. Delivio Food [food] - "Restaurant and cooked food deliveries"
        assertEquals("food", defs[0].key)
        assertEquals("Delivio Food", defs[0].displayName)
        assertEquals("Restaurant and cooked food deliveries", defs[0].subtitle)
        assertEquals("FOOD", defs[0].badgeName)

        // 2. Delivio Mart [convenience] - "Convenience store, grocery, and pharmacy deliveries"
        assertEquals("convenience", defs[1].key)
        assertEquals("Delivio Mart", defs[1].displayName)
        assertEquals("Convenience store, grocery, and pharmacy deliveries", defs[1].subtitle)
        assertEquals("MART", defs[1].badgeName)

        // 3. Delivio Parcel [parcel] - "Boxed items, packages, and documents"
        assertEquals("parcel", defs[2].key)
        assertEquals("Delivio Parcel", defs[2].displayName)
        assertEquals("Boxed items, packages, and documents", defs[2].subtitle)
        assertEquals("PARCEL", defs[2].badgeName)

        // 4. Delivio Pabili [pabili] - "On-demand grocery and item purchasing"
        assertEquals("pabili", defs[3].key)
        assertEquals("Delivio Pabili", defs[3].displayName)
        assertEquals("On-demand grocery and item purchasing", defs[3].subtitle)
        assertEquals("PABILI", defs[3].badgeName)

        // 5. Delivio Instant [request_rider] - "Rider requests booked by merchants (the Request a Rider button in the Partner app)"
        assertEquals("request_rider", defs[4].key)
        assertEquals("Delivio Instant", defs[4].displayName)
        assertEquals("Rider requests booked by merchants (the Request a Rider button in the Partner app)", defs[4].subtitle)
        assertEquals("INSTANT", defs[4].badgeName)

        // 6. Delivio Taxi [taxi] - "Passenger rides"
        assertEquals("taxi", defs[5].key)
        assertEquals("Delivio Taxi", defs[5].displayName)
        assertEquals("Passenger rides", defs[5].subtitle)
        assertEquals("TAXI", defs[5].badgeName)

        // Verify helper functions
        assertEquals("Delivio Food", OrderStepConstants.getServiceDisplayName("food"))
        assertEquals("Delivio Mart", OrderStepConstants.getServiceDisplayName("convenience"))
        assertEquals("Delivio Parcel", OrderStepConstants.getServiceDisplayName("parcel"))
        assertEquals("Delivio Pabili", OrderStepConstants.getServiceDisplayName("pabili"))
        assertEquals("Delivio Instant", OrderStepConstants.getServiceDisplayName("request_rider"))
        assertEquals("Delivio Taxi", OrderStepConstants.getServiceDisplayName("taxi"))

        assertEquals("FOOD", OrderStepConstants.getServiceBadge("food"))
        assertEquals("MART", OrderStepConstants.getServiceBadge("convenience"))
        assertEquals("PARCEL", OrderStepConstants.getServiceBadge("parcel"))
        assertEquals("PABILI", OrderStepConstants.getServiceBadge("pabili"))
        assertEquals("INSTANT", OrderStepConstants.getServiceBadge("request_rider"))
        assertEquals("TAXI", OrderStepConstants.getServiceBadge("taxi"))

        // Vehicle rules: motorcycle sees all six; tricycle and cars see only Parcel and Taxi
        val motoServices = OrderStepConstants.getServicesForVehicle("motorcycle")
        assertEquals(6, motoServices.size)
        assertTrue(motoServices.containsAll(listOf("food", "convenience", "parcel", "pabili", "request_rider", "taxi")))

        val trikeServices = OrderStepConstants.getServicesForVehicle("tricycle")
        assertEquals(listOf("parcel", "taxi"), trikeServices)

        val car4Services = OrderStepConstants.getServicesForVehicle("car4")
        assertEquals(listOf("parcel", "taxi"), car4Services)

        val car8Services = OrderStepConstants.getServicesForVehicle("car8")
        assertEquals(listOf("parcel", "taxi"), car8Services)

        // Lock reasons: "Disabled platform-wide", "Disabled in <city>", "Disabled for your account by admin"
        assertEquals(
            "Disabled platform-wide",
            OrderStepConstants.getServiceLockReason(isPlatformOn = false, isCityOn = true, isAllowed = true, cityName = "Puerto Princesa")
        )
        assertEquals(
            "Disabled in Puerto Princesa",
            OrderStepConstants.getServiceLockReason(isPlatformOn = true, isCityOn = false, isAllowed = true, cityName = "Puerto Princesa")
        )
        assertEquals(
            "Disabled for your account by admin",
            OrderStepConstants.getServiceLockReason(isPlatformOn = true, isCityOn = true, isAllowed = false, cityName = "Puerto Princesa")
        )
        assertNull(
            OrderStepConstants.getServiceLockReason(isPlatformOn = true, isCityOn = true, isAllowed = true, cityName = "Puerto Princesa")
        )
    }

    @Test
    fun `verify scheduled order ignoring before release time and release after release time`() {
        val now = 1700000000000L
        val leadMinutes = 15.0 // release lead: 15 minutes = 900,000 ms
        val scheduledTime = now + 1800000L // 30 minutes in the future
        val releaseTime = scheduledTime - (leadMinutes * 60 * 1000).toLong() // 15 minutes in the future

        // 1. Order scheduled 30m in future, lead 15m -> releaseTime is 15m in future.
        // At 'now', now < releaseTime -> MUST be ignored
        val futureOrder = Order(
            id = "sched_1",
            isScheduled = true,
            scheduledFor = scheduledTime,
            scheduledDateText = "3:30 PM",
            status = OrderStepConstants.STATUS_PREPARING
        )
        assertEquals(scheduledTime, futureOrder.scheduledForMillis)
        assertEquals(releaseTime, futureOrder.getReleaseTimeMillis(leadMinutes))
        assertTrue(futureOrder.isFutureScheduled(leadMinutes, now))

        // 2. At releaseTime - 1ms, still ignored
        assertTrue(futureOrder.isFutureScheduled(leadMinutes, releaseTime - 1L))

        // 3. At releaseTime, no longer ignored (released for dispatch & offers)
        assertFalse(futureOrder.isFutureScheduled(leadMinutes, releaseTime))

        // 4. Past release time (e.g. 20m from now), no longer ignored
        assertFalse(futureOrder.isFutureScheduled(leadMinutes, releaseTime + 300000L))

        // 5. Default lead minutes (0.0): release time equals scheduled time
        val defaultLeadReleaseTime = futureOrder.getReleaseTimeMillis(0.0)
        assertEquals(scheduledTime, defaultLeadReleaseTime)
        assertTrue(futureOrder.isFutureScheduled(0.0, scheduledTime - 1L))
        assertFalse(futureOrder.isFutureScheduled(0.0, scheduledTime))

        // 6. Non-scheduled order is never future-ignored
        val regularOrder = Order(
            id = "reg_1",
            isScheduled = false,
            scheduledFor = scheduledTime
        )
        assertFalse(regularOrder.isFutureScheduled(leadMinutes, now))
    }

    @Test
    fun `verify scheduled order hold timers start only at release time`() {
        val dispatch = com.example.data.constants.DispatchSettings(holdMinutes = 5.0)
        val now = 1700000000000L
        val leadMinutes = 15.0
        val scheduledTime = now + 1800000L // 30 mins in future
        val releaseTime = scheduledTime - (leadMinutes * 60 * 1000).toLong() // 15 mins in future

        val scheduledSaver = Order(
            id = "sched_saver",
            serviceType = "food",
            isPoolable = true,
            isScheduled = true,
            scheduledFor = scheduledTime,
            badge = "Saver",
            status = OrderStepConstants.STATUS_READY,
            holdMinutes = 5.0,
            acceptedAtMillis = releaseTime,
            holdUntilMillis = releaseTime + 300000L
        )

        // Before release time, hold timer has not started
        assertFalse(com.example.data.constants.BatchConstants.isHolding(scheduledSaver, dispatch, now))

        // Exactly at release time, hold timer starts and is holding
        assertTrue(com.example.data.constants.BatchConstants.isHolding(scheduledSaver, dispatch, releaseTime))

        // 2 minutes after release time, still holding
        assertTrue(com.example.data.constants.BatchConstants.isHolding(scheduledSaver, dispatch, releaseTime + 120000L))

        // After hold ends (5 minutes after release time), hold ends
        assertFalse(com.example.data.constants.BatchConstants.isHolding(scheduledSaver, dispatch, releaseTime + 300000L))

        // Priority scheduled order never holds
        val scheduledPriority = scheduledSaver.copy(badge = "Priority", isPriority = true)
        assertFalse(com.example.data.constants.BatchConstants.isHolding(scheduledPriority, dispatch, releaseTime))
    }

    @Test
    fun `verify service types match firestore rules`() {
        val allowedInFirestoreRules = setOf("food", "convenience", "parcel", "pabili", "taxi", "request_rider")

        // 1. All service definition keys must be valid in firestore rules
        OrderStepConstants.SERVICE_DEFINITIONS.forEach { def ->
            assertTrue(
                "Service key ${def.key} must be valid in Firestore rules",
                allowedInFirestoreRules.contains(def.key)
            )
        }

        // 2. Rider allowedServices default must be valid in firestore rules
        val defaultRider = com.example.data.models.Rider()
        defaultRider.allowedServices.forEach { svc ->
            assertTrue(
                "Rider allowed service $svc must be valid in Firestore rules",
                allowedInFirestoreRules.contains(svc)
            )
        }
    }

    @Test
    fun `verify driver shift duration and days`() {
        val shift = com.example.data.models.DriverShift(
            dayOfWeek = 1,
            startTime = "08:00",
            endTime = "17:00",
            reminder15MinBefore = true,
            isEnabled = true
        )
        assertEquals("Monday", shift.dayName)
        assertEquals("Mon", shift.dayShortName)
        assertEquals(9.0, shift.durationHours, 0.01)
        assertTrue(shift.reminder15MinBefore)
    }

    @Test
    fun `verify demand heat map 500m grid cell calculation for orders within 60 mins`() {
        val now = 1700000000000L
        val fortyMinsAgo = now - 40 * 60 * 1000L
        val twoHoursAgo = now - 120 * 60 * 1000L

        // Order 1 & Order 2 in Puerto Princesa town center (~9.7392, 118.7353)
        val order1 = Order(
            id = "ord1",
            status = OrderStepConstants.STATUS_READY,
            merchantLat = 9.7392,
            merchantLng = 118.7353
        )
        val order2 = Order(
            id = "ord2",
            status = OrderStepConstants.STATUS_PREPARING,
            merchantLat = 9.7393,
            merchantLng = 118.7354
        )
        // Order 3 delivered 40 mins ago (within 60m window) at same grid area
        val order3 = Order(
            id = "ord3",
            status = OrderStepConstants.STATUS_DELIVERED,
            deliveredAt = com.google.firebase.Timestamp(java.util.Date(fortyMinsAgo)),
            merchantLat = 9.7391,
            merchantLng = 118.7352
        )
        // Order 4 delivered 2 hours ago (outside 60m window)
        val order4 = Order(
            id = "ord4",
            status = OrderStepConstants.STATUS_DELIVERED,
            deliveredAt = com.google.firebase.Timestamp(java.util.Date(twoHoursAgo)),
            merchantLat = 9.7392,
            merchantLng = 118.7353
        )

        val grid = com.example.data.models.BusyHeatMapCalculator.computeGridCells(
            listOf(order1, order2, order3, order4),
            nowMillis = now
        )

        assertEquals(1, grid.size)
        assertEquals(3, grid[0].orderCount) // 3 orders within 60 mins, order 4 excluded
        assertTrue(grid[0].centerLat in 9.73..9.75)
        assertTrue(grid[0].centerLng in 118.73..118.75)
    }

    @Test
    fun `verify document expiry reminders at 30, 14, 7 days and expired`() {
        val now = 1700000000000L
        val oneDayMs = 24 * 60 * 60 * 1000L

        // 1. Expired (2 days ago)
        val expiredDoc = com.example.data.models.RiderDocument(
            type = "license",
            expiresAt = now - 2 * oneDayMs
        )
        val expiredAlert = expiredDoc.getExpiryAlert(now)
        assertTrue(expiredAlert != null)
        assertEquals(com.example.data.models.DocumentAlertLevel.EXPIRED, expiredAlert!!.status)
        assertTrue(expiredAlert.title.contains("Expired"))

        // 2. 7 days before
        val sevenDaysDoc = com.example.data.models.RiderDocument(
            type = "registration",
            expiresAt = now + 5 * oneDayMs
        )
        val sevenDaysAlert = sevenDaysDoc.getExpiryAlert(now)
        assertTrue(sevenDaysAlert != null)
        assertEquals(com.example.data.models.DocumentAlertLevel.WARNING_7_DAYS, sevenDaysAlert!!.status)
        assertTrue(sevenDaysAlert.title.contains("Vehicle Registration"))

        // 3. 14 days before
        val fourteenDaysDoc = com.example.data.models.RiderDocument(
            type = "insurance",
            expiresAt = now + 12 * oneDayMs
        )
        val fourteenDaysAlert = fourteenDaysDoc.getExpiryAlert(now)
        assertTrue(fourteenDaysAlert != null)
        assertEquals(com.example.data.models.DocumentAlertLevel.NOTICE_14_DAYS, fourteenDaysAlert!!.status)
        assertTrue(fourteenDaysAlert.title.contains("Vehicle Insurance"))

        // 4. 30 days before
        val thirtyDaysDoc = com.example.data.models.RiderDocument(
            type = "license",
            expiresAt = now + 25 * oneDayMs
        )
        val thirtyDaysAlert = thirtyDaysDoc.getExpiryAlert(now)
        assertTrue(thirtyDaysAlert != null)
        assertEquals(com.example.data.models.DocumentAlertLevel.REMINDER_30_DAYS, thirtyDaysAlert!!.status)

        // 5. 60 days before (no alert)
        val sixtyDaysDoc = com.example.data.models.RiderDocument(
            type = "license",
            expiresAt = now + 60 * oneDayMs
        )
        assertNull(sixtyDaysDoc.getExpiryAlert(now))
    }

    @Test
    fun `verify inbox hides items whose sendAt is in the future`() {
        val now = 1700000000000L
        val pastMessage = com.example.data.models.MessageNotification(
            id = "msg1",
            title = "Welcome",
            body = "Hello",
            sendAt = now - 3600000L
        )
        val futureMessage = com.example.data.models.MessageNotification(
            id = "msg2",
            title = "Future Announcement",
            body = "Coming soon",
            sendAt = now + 7200000L // 2 hours in future
        )

        assertFalse(pastMessage.isFuture(now))
        assertTrue(futureMessage.isFuture(now))

        val list = listOf(pastMessage, futureMessage)
        val visibleList = list.filter { !it.isFuture(now) }
        assertEquals(1, visibleList.size)
        assertEquals("msg1", visibleList[0].id)
    }
}





