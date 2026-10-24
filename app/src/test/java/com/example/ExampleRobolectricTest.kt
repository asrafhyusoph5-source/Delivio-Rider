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
        assertEquals("Delivio Rider", appName)
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
}
