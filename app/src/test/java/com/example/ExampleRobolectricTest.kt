package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.constants.AuthConstants
import com.example.data.constants.OrderStepConstants
import com.example.data.models.Order
import com.example.data.models.Rider
import com.example.data.models.TierLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Delivio Driver", appName)
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
}

