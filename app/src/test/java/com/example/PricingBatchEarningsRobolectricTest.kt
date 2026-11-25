package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.example.data.constants.BatchConstants
import com.example.data.constants.DispatchSettings
import com.example.data.constants.OrderStepConstants
import com.example.data.models.CommissionSettings
import com.example.data.models.DeliveryRatesV2
import com.example.data.models.Order
import com.example.data.models.OrderDocumentParser
import com.example.data.models.OrderPricingSnapshot
import com.example.data.models.OsrmRouteService
import com.example.data.models.PricingEngine
import com.example.data.models.QuestClaim
import com.example.data.models.RiderQuote
import com.example.data.models.RouteStopCalculator
import com.example.data.models.SessionClaim
import com.example.ui.components.DeliverySuccessDialog
import com.example.ui.components.IncomingOrderDialog
import com.example.ui.screens.EarningsContent
import com.example.ui.theme.DelivioRiderTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class PricingBatchEarningsRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleSchema2RatesMap(version: Long = 3L): Map<String, Any?> = mapOf(
        "schema" to 2,
        "version" to version,
        "updatedAt" to 1700000000000L,
        "services" to mapOf(
            "food" to mapOf(
                "delivery" to mapOf(
                    "baseFare" to 45.0,
                    "includedKm" to 2.0,
                    "perKm" to 10.0,
                    "minFee" to 45.0,
                    "maxFee" to 150.0
                ),
                "pickup" to mapOf(
                    "baseFee" to 15.0,
                    "freeKm" to 1.0,
                    "perKm" to 8.0,
                    "maxFee" to 40.0
                ),
                "serviceFee" to mapOf("mode" to "flat", "value" to 12.0),
                "peak" to mapOf("enabled" to false, "multiplier" to 1.2),
                "night" to mapOf("enabled" to false, "surcharge" to 15.0),
                "freeDelivery" to mapOf("enabled" to true, "minSubtotal" to 500.0)
            ),
            "convenience" to mapOf(
                "delivery" to mapOf(
                    "baseFare" to 50.0,
                    "includedKm" to 2.0,
                    "perKm" to 10.0,
                    "minFee" to 50.0,
                    "maxFee" to 160.0
                ),
                "pickup" to mapOf(
                    "baseFee" to 20.0,
                    "freeKm" to 1.0,
                    "perKm" to 10.0,
                    "maxFee" to 45.0
                ),
                "serviceFee" to mapOf("mode" to "percent", "value" to 5.0)
            ),
            "parcel" to mapOf(
                "motorcycle" to mapOf(
                    "delivery" to mapOf(
                        "baseFare" to 55.0,
                        "includedKm" to 2.0,
                        "perKm" to 12.0,
                        "minFee" to 55.0,
                        "maxFee" to 250.0
                    ),
                    "pickup" to mapOf(
                        "baseFee" to 12.0,
                        "freeKm" to 1.5,
                        "perKm" to 6.0,
                        "maxFee" to 30.0
                    ),
                    "maxWeightKg" to 20.0
                ),
                "sedan" to mapOf(
                    "delivery" to mapOf(
                        "baseFare" to 120.0,
                        "includedKm" to 3.0,
                        "perKm" to 20.0,
                        "minFee" to 120.0,
                        "maxFee" to 500.0
                    ),
                    "pickup" to mapOf(
                        "baseFee" to 25.0,
                        "freeKm" to 1.0,
                        "perKm" to 10.0,
                        "maxFee" to 60.0
                    ),
                    "maxWeightKg" to 150.0
                )
            ),
            "taxi" to mapOf(
                "car4" to mapOf(
                    "delivery" to mapOf(
                        "baseFare" to 80.0,
                        "includedKm" to 2.0,
                        "perKm" to 18.0,
                        "minFee" to 80.0,
                        "maxFee" to 400.0
                    ),
                    "pickup" to mapOf(
                        "baseFee" to 10.0,
                        "freeKm" to 1.0,
                        "perKm" to 5.0,
                        "maxFee" to 25.0
                    ),
                    "extraStopFee" to 25.0,
                    "active" to true
                )
            )
        )
    )

    @Test
    fun pricingContract_parsesSchema2Rates_resolvesVersion_andComputesPickupDeliveryAndCommission() {
        val ratesV2 = DeliveryRatesV2.fromMap(sampleSchema2RatesMap(version = 2L))!!
        val ratesV3 = DeliveryRatesV2.fromMap(sampleSchema2RatesMap(version = 3L))!!
        assertTrue(ratesV3.isSchema2)

        val commission = CommissionSettings(single = 10.0, second = 20.0, thirdPlus = 30.0, merchantDefault = 15.0)
        val foodOrder = Order(
            id = "o_food_1",
            serviceType = OrderStepConstants.SERVICE_FOOD,
            tip = 20.0,
            feesServiceFee = 12.0,
            pricing = OrderPricingSnapshot(
                schema = 2,
                ratesVersion = 2L,
                serviceKey = "food",
                vehicle = "motorcycle",
                deliveryFee = 65.0,
                serviceFee = 12.0,
                tip = 20.0,
                itemsSubtotal = 300.0,
                total = 397.0,
                payer = "customer"
            )
        )

        // Version resolution prefers order.pricing.ratesVersion when cached
        val resolved = PricingEngine.resolveRatesForOrder(
            order = foodOrder,
            liveRates = ratesV3,
            versionedRates = mapOf(2L to ratesV2)
        )
        assertEquals(2L, resolved?.version)

        // Index 1: pickupKm=2.5 -> pickupFee = 15 + (2.5 - 1.0)*8 = 27.0; deliv=65.0; commission = 10% of (65+27) = 9.20
        val quote1 = PricingEngine.computeRiderQuote(
            order = foodOrder,
            pickupKm = 2.5,
            batchIndex = 1,
            rates = resolved,
            commission = commission
        )
        assertEquals(2.5, quote1.pickupKm, 0.01)
        assertEquals(27.0, quote1.pickupFee, 0.01)
        assertEquals(65.0, quote1.deliveryFee, 0.01)
        assertEquals(10.0, quote1.commissionRate, 0.01)
        assertEquals(9.20, quote1.commissionAmount, 0.01)
        // gross = 65 + 27 + 20 = 112.0; net = (92 - 9.20) + 20 = 102.80
        assertEquals(112.0, quote1.gross, 0.01)
        assertEquals(102.80, quote1.net, 0.01)

        // Index 2: pickupFee MUST be 0.0, commissionRate = 20% of 65.0 = 13.0, net = 52 + 20 = 72.0
        val quote2 = PricingEngine.computeRiderQuote(
            order = foodOrder,
            pickupKm = 2.5,
            batchIndex = 2,
            rates = resolved,
            commission = commission
        )
        assertEquals(0.0, quote2.pickupFee, 0.01)
        assertEquals(20.0, quote2.commissionRate, 0.01)
        assertEquals(13.0, quote2.commissionAmount, 0.01)
        assertEquals(72.0, quote2.net, 0.01)

        // Index 3+: pickupFee MUST be 0.0, commissionRate = 30% of 65.0 = 19.50, net = 45.50 + 20 = 65.50
        val quote3 = PricingEngine.computeRiderQuote(
            order = foodOrder,
            pickupKm = 2.5,
            batchIndex = 3,
            rates = resolved,
            commission = commission
        )
        assertEquals(0.0, quote3.pickupFee, 0.01)
        assertEquals(30.0, quote3.commissionRate, 0.01)
        assertEquals(19.50, quote3.commissionAmount, 0.01)
        assertEquals(65.50, quote3.net, 0.01)
    }

    @Test
    fun pricingContract_recalculatesBatchQuotesWhenOrderReleased_andFallsBackToLegacyWithoutSchema2() {
        val ratesV2 = DeliveryRatesV2.fromMap(sampleSchema2RatesMap(version = 1L))!!
        val commission = CommissionSettings(single = 10.0, second = 20.0, thirdPlus = 30.0)

        val orderA = Order(
            id = "a",
            serviceType = OrderStepConstants.SERVICE_FOOD,
            batchIndex = 1,
            pickupDistanceKm = 1.0,
            pricing = OrderPricingSnapshot(schema = 2, ratesVersion = 1L, serviceKey = "food", deliveryFee = 50.0)
        )
        val orderB = Order(
            id = "b",
            serviceType = OrderStepConstants.SERVICE_FOOD,
            batchIndex = 2,
            pickupDistanceKm = 2.0,
            tip = 10.0,
            pricing = OrderPricingSnapshot(schema = 2, ratesVersion = 1L, serviceKey = "food", deliveryFee = 60.0)
        )
        val orderC = Order(
            id = "c",
            serviceType = OrderStepConstants.SERVICE_FOOD,
            batchIndex = 3,
            pickupDistanceKm = 1.5,
            pricing = OrderPricingSnapshot(schema = 2, ratesVersion = 1L, serviceKey = "food", deliveryFee = 70.0)
        )

        // Initially 3 orders -> 10%, 20%, 30%
        val initialRecalc = PricingEngine.recalculateQuotesForBatchPositionChange(
            orderedBatch = listOf(orderA, orderB, orderC),
            pickupKmByOrderId = mapOf("a" to 1.0, "b" to 2.0, "c" to 1.5),
            liveRates = ratesV2,
            commission = commission
        )
        assertEquals(10.0, initialRecalc[0].second.commissionRate, 0.01)
        assertEquals(20.0, initialRecalc[1].second.commissionRate, 0.01)
        assertEquals(30.0, initialRecalc[2].second.commissionRate, 0.01)
        assertEquals(15.0, initialRecalc[0].second.pickupFee, 0.01)
        assertEquals(0.0, initialRecalc[1].second.pickupFee, 0.01)

        // Release orderA -> orderB moves to position 1 (gets pickup fee + 10%), orderC moves to position 2 (20%)
        val afterRelease = PricingEngine.recalculateQuotesForBatchPositionChange(
            orderedBatch = listOf(orderB, orderC),
            pickupKmByOrderId = mapOf("b" to 2.0, "c" to 1.5),
            liveRates = ratesV2,
            commission = commission
        )
        assertEquals(1, afterRelease[0].first.batchIndex)
        assertEquals(10.0, afterRelease[0].second.commissionRate, 0.01)
        // pickupKm=2.0 -> 15 + (2.0-1.0)*8 = 23.0
        assertEquals(23.0, afterRelease[0].second.pickupFee, 0.01)
        assertEquals(2, afterRelease[1].first.batchIndex)
        assertEquals(20.0, afterRelease[1].second.commissionRate, 0.01)
        assertEquals(0.0, afterRelease[1].second.pickupFee, 0.01)

        // Legacy fallback when city has no schema 2 rates
        val legacyOrder = Order(
            id = "legacy_1",
            serviceType = OrderStepConstants.SERVICE_FOOD,
            deliveryDistanceKm = 3.0,
            tip = 15.0
        )
        val legacyQuote = PricingEngine.computeRiderQuote(
            order = legacyOrder,
            pickupKm = 2.0,
            batchIndex = 1,
            rates = null,
            commission = commission
        )
        assertEquals(0.0, legacyQuote.commissionRate, 0.01)
        assertEquals(0.0, legacyQuote.commissionAmount, 0.01)
        assertTrue(legacyQuote.deliveryFee > 0.0)
        assertTrue(legacyQuote.pickupFee > 0.0)
    }

    @Test
    fun osrmDistance_usesOsrmWhenAvailable_andFallsBackToStraightLineTimes1Point3() {
        val lat1 = 9.7392
        val lng1 = 118.7353
        val lat2 = 9.7592
        val lng2 = 118.7453
        val straight = BatchConstants.distanceKm(lat1, lng1, lat2, lng2)

        val roadKm = OsrmRouteService.fetchLegDistanceKm(lat1, lng1, lat2, lng2) { _, _, _, _ -> 3.45 }
        assertEquals(3.45, roadKm, 0.001)

        val fallbackKm = OsrmRouteService.fetchLegDistanceKm(lat1, lng1, lat2, lng2) { _, _, _, _ -> null }
        assertEquals(PricingEngine.round2(straight * 1.3), fallbackKm, 0.02)
    }

    @Test
    fun orderDocumentParser_parsesPricingAndRiderQuoteSnapshots() {
        val parsed = OrderDocumentParser.parseOrderFromMap(
            id = "ord_parse_1",
            data = mapOf(
                "serviceType" to "food",
                "status" to "Delivered",
                "paymentMethod" to "cod",
                "cashToCollect" to 420.0,
                "pricing" to mapOf(
                    "schema" to 2,
                    "ratesVersion" to 4L,
                    "serviceKey" to "food",
                    "vehicle" to "motorcycle",
                    "deliveryFee" to 60.0,
                    "serviceFee" to 15.0,
                    "tip" to 20.0,
                    "vat" to 0.0,
                    "itemsSubtotal" to 325.0,
                    "total" to 420.0,
                    "payer" to "customer",
                    "merchantCommissionRate" to 15.0
                ),
                "riderQuote" to mapOf(
                    "pickupKm" to 1.8,
                    "pickupFee" to 21.4,
                    "deliveryFee" to 60.0,
                    "batchIndex" to 1,
                    "commissionRate" to 10.0,
                    "commissionAmount" to 8.14,
                    "gross" to 101.4,
                    "net" to 93.26,
                    "ratesVersion" to 4L
                )
            )
        )
        assertEquals(2, parsed.pricing?.schema)
        assertEquals(4L, parsed.pricing?.ratesVersion)
        assertEquals(60.0, parsed.pricing?.deliveryFee ?: 0.0, 0.01)
        assertEquals(1.8, parsed.riderQuote?.pickupKm ?: 0.0, 0.01)
        assertEquals(21.4, parsed.riderQuote?.pickupFee ?: 0.0, 0.01)
        assertEquals(10.0, parsed.riderQuote?.commissionRate ?: 0.0, 0.01)
        assertEquals(93.26, parsed.riderQuote?.net ?: 0.0, 0.01)
        assertEquals(93.26, parsed.estimatedEarnings, 0.01)
    }

    @Test
    fun batchingRules_enforcesSameFamily_directionTolerance_aheadCheck_detourLimit_andNearestStops() {
        val dispatchOn = DispatchSettings(
            poolMaxPickupGapKm = 5.0,
            poolMaxDropoffGapKm = 5.0,
            batchDirectionToleranceDeg = 45.0,
            batchMaxDetourPercent = 40.0,
            martBatching = true
        )
        val dispatchMartOff = dispatchOn.copy(martBatching = false)

        // Base food order heading due North: (9.7400, 118.7400) -> (9.7700, 118.7400) (~3.33 km North)
        val foodA = Order(
            id = "food_a",
            serviceType = OrderStepConstants.SERVICE_FOOD,
            cityId = "ppc",
            isPoolable = true,
            status = OrderStepConstants.STATUS_PREPARING,
            riderStep = OrderStepConstants.STEP_TO_MERCHANT,
            merchantLat = 9.7400,
            merchantLng = 118.7400,
            customerLat = 9.7700,
            customerLng = 118.7400
        )
        // Aligned food order heading North along same corridor
        val foodAligned = foodA.copy(
            id = "food_aligned",
            merchantLat = 9.7440,
            merchantLng = 118.7410,
            customerLat = 9.7740,
            customerLng = 118.7410
        )
        // Convenience/Mart order along the exact same corridor
        val martAligned = foodAligned.copy(
            id = "mart_aligned",
            serviceType = OrderStepConstants.SERVICE_MART
        )
        val martBase = foodA.copy(
            id = "mart_base",
            serviceType = OrderStepConstants.SERVICE_MART
        )
        // Opposite direction Food order (heading South -> 180° off > 45°)
        val foodOpposite = foodA.copy(
            id = "food_opposite",
            merchantLat = 9.7440,
            merchantLng = 118.7400,
            customerLat = 9.7150,
            customerLng = 118.7400
        )
        // Large Eastward detour (> 40% extra distance)
        val foodBigDetour = foodA.copy(
            id = "food_detour",
            merchantLat = 9.7400,
            merchantLng = 118.7650,
            customerLat = 9.7700,
            customerLng = 118.7650
        )
        val instantOrder = foodAligned.copy(id = "inst_1", serviceType = OrderStepConstants.SERVICE_REQUEST)

        val riderLoc = 9.7350 to 118.7400

        // Food + Food aligned works
        assertTrue(BatchConstants.isPairable(foodA, foodAligned, dispatchOn, riderLoc))
        assertTrue(BatchConstants.canJoin(listOf(foodA), foodAligned, dispatchOn, isPriorityRider = false, riderLoc = riderLoc))

        // Food + Mart NEVER mix even when martBatching = true
        assertFalse(BatchConstants.isPairable(foodA, martAligned, dispatchOn, riderLoc))
        assertFalse(BatchConstants.canJoin(listOf(foodA), martAligned, dispatchOn, isPriorityRider = false, riderLoc = riderLoc))
        assertFalse(BatchConstants.isCandidateCompatibleWithActive(listOf(foodA), martAligned, dispatchOn))

        // Mart + Mart allowed ONLY when martBatching = true
        assertTrue(BatchConstants.isPairable(martBase, martAligned, dispatchOn, riderLoc))
        assertFalse(BatchConstants.isPairable(martBase, martAligned, dispatchMartOff, riderLoc))
        assertFalse(BatchConstants.canReceiveMoreOffers(listOf(martBase), dispatchMartOff, isPriorityRider = false))

        // Single-only service (Instant) never batchable
        assertFalse(BatchConstants.isPoolable(instantOrder, dispatchOn))
        assertFalse(BatchConstants.isPairable(foodA, instantOrder, dispatchOn, riderLoc))

        // Opposite direction (> 45°) and dropoff behind first pickup rejected
        assertFalse(BatchConstants.isPairable(foodA, foodOpposite, dispatchOn, riderLoc))
        assertFalse(BatchConstants.canJoin(listOf(foodA), foodOpposite, dispatchOn, isPriorityRider = false, riderLoc = riderLoc))

        // Excessive detour (> 40%) rejected
        assertFalse(BatchConstants.isPairable(foodA, foodBigDetour, dispatchOn, riderLoc))

        // Nearest-to-nearest stop ordering & sequential stop numbers (1, 2, 3, 4)
        val stops = RouteStopCalculator.computeBatchStops(
            activeOrders = listOf(foodAligned, foodA),
            driverLat = 9.7350,
            driverLng = 118.7400
        )
        assertEquals(4, stops.size)
        assertEquals(listOf(1, 2, 3, 4), stops.map { it.stopNumber })
        // Nearest merchant to 9.7350 is foodA (9.7400), then foodAligned (9.7440); then nearest dropoff to last pickup (9.7440) is foodA (9.7700), then foodAligned (9.7740)
        assertEquals("food_a", stops[0].orderId)
        assertTrue(stops[0].isMerchant)
        assertEquals("food_aligned", stops[1].orderId)
        assertTrue(stops[1].isMerchant)
        assertEquals("food_a", stops[2].orderId)
        assertTrue(stops[2].isCustomer)
        assertEquals("food_aligned", stops[3].orderId)
        assertTrue(stops[3].isCustomer)
    }

    @Test
    fun offerDialogAndSuccessDialog_displayRiderQuoteBreakdownWithoutCustomerServiceFee() {
        val quote = RiderQuote(
            pickupKm = 2.0,
            pickupFee = 23.0,
            deliveryFee = 65.0,
            batchIndex = 1,
            commissionRate = 10.0,
            commissionAmount = 8.80,
            gross = 103.0,
            net = 94.20
        )
        val order = Order(
            id = "ord_ui_1",
            orderNumber = "9042",
            serviceType = OrderStepConstants.SERVICE_FOOD,
            merchantName = "Kalui Restaurant",
            customerArea = "Bancao-Bancao",
            deliveryDistanceKm = 3.2,
            pickupDistanceKm = 2.0,
            deliveryFee = 65.0,
            feesDeliveryFee = 65.0,
            feesServiceFee = 19.0, // Customer service fee must NEVER be shown to driver
            tip = 15.0,
            paymentMethod = "online",
            paymentProvider = "gcash",
            pricing = OrderPricingSnapshot(schema = 2, serviceKey = "food", deliveryFee = 65.0, serviceFee = 19.0),
            riderQuote = quote
        )

        composeRule.setContent {
            DelivioRiderTheme {
                IncomingOrderDialog(
                    order = order,
                    currencySymbol = "₱",
                    soundEnabled = false,
                    vibrationEnabled = false,
                    onAccept = {},
                    onDecline = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Estimated earnings").assertIsDisplayed()
        composeRule.onNodeWithText("₱94.20").assertIsDisplayed()
        composeRule.onNodeWithText("Earnings details").performClick()
        composeRule.waitForIdle()

        // Net delivery fee after commission = 94.20 - 15.00 = 79.20
        composeRule.onNodeWithText("Delivery fee").assertIsDisplayed()
        composeRule.onNodeWithText("₱79.20").assertIsDisplayed()
        composeRule.onNodeWithText("Tip").assertIsDisplayed()
        composeRule.onNodeWithText("+₱15.00").assertIsDisplayed()
        composeRule.onNodeWithText("Total").assertIsDisplayed()
        // Commission, separate pickup fee, and customer service fee ₱19.00 must never appear
        composeRule.onNodeWithText("Pick up fee").assertDoesNotExist()
        composeRule.onNodeWithText("Commission fee (-10%)").assertDoesNotExist()
        composeRule.onNodeWithText("₱19.00").assertDoesNotExist()
    }

    @Test
    fun deliverySuccessDialog_showsSimplifiedDeliveryFeeTipAndWalletBalance() {
        val quote = RiderQuote(
            pickupKm = 1.5,
            pickupFee = 19.0,
            deliveryFee = 60.0,
            batchIndex = 2,
            commissionRate = 20.0,
            commissionAmount = 12.0,
            gross = 79.0,
            net = 67.0
        )
        val completedOrder = Order(
            id = "ord_done_1",
            orderNumber = "7788",
            serviceType = OrderStepConstants.SERVICE_FOOD,
            merchantName = "Chicken Hauz",
            tip = 10.0,
            lateTipAmount = 5.0,
            paymentMethod = "online",
            riderQuote = quote
        )

        composeRule.setContent {
            DelivioRiderTheme {
                DeliverySuccessDialog(
                    order = completedOrder,
                    currencySymbol = "₱",
                    walletBalance = 250.0,
                    onDismiss = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Delivery fee (after commission)").assertIsDisplayed()
        composeRule.onNodeWithText("₱67.00").assertIsDisplayed()
        composeRule.onNodeWithText("Tip").assertIsDisplayed()
        composeRule.onNodeWithText("+₱15.00").assertIsDisplayed()
        composeRule.onNodeWithText("New wallet balance").assertIsDisplayed()
        composeRule.onNodeWithText("₱250.00").assertIsDisplayed()

        // Removed commission, pickup, and legacy labels
        composeRule.onNodeWithText("YOU EARNED").assertDoesNotExist()
        composeRule.onNodeWithText("Pick up fee").assertDoesNotExist()
        composeRule.onNodeWithText("Commission fee (-20%)").assertDoesNotExist()
        composeRule.onNodeWithText("Base Pay").assertDoesNotExist()
        composeRule.onNodeWithText("Per-km Pay").assertDoesNotExist()
    }

    @Test
    fun earningsScreen_showsTodayTotalIncomeBreakdown_withoutCommissionOrCodSummary() {
        val tz = TimeZone.getTimeZone("Asia/Manila")
        val nowCal = Calendar.getInstance(tz, Locale.US).apply {
            set(2026, Calendar.APRIL, 15, 14, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val nowMs = nowCal.timeInMillis
        val twoDaysAgoMs = (nowCal.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, -2) }.timeInMillis

        val codOrderToday = Order(
            id = "trip_cod_today",
            orderNumber = "1001",
            serviceType = OrderStepConstants.SERVICE_FOOD,
            merchantName = "Jollibee",
            customerArea = "San Pedro",
            paymentMethod = "cod",
            cashToCollect = 500.0,
            codCollected = 500.0,
            tip = 10.0,
            deliveredAt = com.google.firebase.Timestamp(java.util.Date(nowMs - 3600_000L)),
            riderQuote = RiderQuote(
                pickupKm = 1.4,
                pickupFee = 18.0,
                deliveryFee = 62.0,
                batchIndex = 1,
                commissionRate = 10.0,
                commissionAmount = 8.0,
                gross = 90.0,
                net = 82.0
            )
        )
        val onlineOrderEarlierThisWeek = Order(
            id = "trip_online_week",
            orderNumber = "1002",
            serviceType = OrderStepConstants.SERVICE_MART,
            merchantName = "Express Mart",
            customerArea = "Bancao",
            paymentMethod = "online",
            paymentProvider = "gcash",
            tip = 20.0,
            deliveredAt = com.google.firebase.Timestamp(java.util.Date(twoDaysAgoMs)),
            riderQuote = RiderQuote(
                pickupKm = 2.0,
                pickupFee = 0.0,
                deliveryFee = 70.0,
                batchIndex = 2,
                commissionRate = 20.0,
                commissionAmount = 14.0,
                gross = 90.0,
                net = 76.0
            )
        )
        val creditedSession = SessionClaim(
            id = "sc_1",
            sessionId = "s_1",
            title = "Lunch Rush Session",
            rewardAmount = 50.0,
            status = "credited",
            createdAtMillis = nowMs - 1800_000L
        )
        val creditedQuest = QuestClaim(
            id = "qc_1",
            questId = "q_1",
            rewardAmount = 100.0,
            status = "Credited",
            createdAt = com.google.firebase.Timestamp(java.util.Date(nowMs - 1200_000L))
        )

        var historyClicked = false
        composeRule.setContent {
            DelivioRiderTheme {
                EarningsContent(
                    currencySymbol = "₱",
                    deliveredOrders = listOf(codOrderToday, onlineOrderEarlierThisWeek),
                    questClaims = listOf(creditedQuest),
                    sessionClaims = listOf(creditedSession),
                    onBack = {},
                    onOpenIncomeHistory = { historyClicked = true },
                    nowMillis = nowMs
                )
            }
        }
        composeRule.waitForIdle()

        // Summary card: Total Income = 82 (today cod trip) + 150 (bonuses) = 232.00; 1 job completed
        composeRule.onNodeWithText("Total Income").assertIsDisplayed()
        composeRule.onNodeWithText("₱232.00").assertIsDisplayed()
        composeRule.onNodeWithText("1 job completed").assertIsDisplayed()

        // Breakdown card has ONLY Delivery fees (72.00), Tips (+10.00), and Bonuses (+150.00)
        composeRule.onNodeWithTag("earnings_lazy_column")
            .performScrollToNode(hasTestTag("earnings_breakdown_card"))
        composeRule.onNodeWithTag("earnings_breakdown_card").assertIsDisplayed()
        composeRule.onNodeWithText("Delivery fees").assertIsDisplayed()
        composeRule.onNodeWithText("Tips").assertIsDisplayed()
        composeRule.onNodeWithText("Bonuses").assertIsDisplayed()
        composeRule.onNodeWithText("+₱150.00").assertIsDisplayed()

        // Removed commission, pickup fees, COD cash summary, filters, and period tabs from main EarningsScreen
        composeRule.onNodeWithText("Net earnings").assertDoesNotExist()
        composeRule.onNodeWithText("Pick up fees").assertDoesNotExist()
        composeRule.onNodeWithText("Commission fee").assertDoesNotExist()
        composeRule.onNodeWithText("COD cash summary").assertDoesNotExist()
        composeRule.onNodeWithText("Service Type").assertDoesNotExist()
        composeRule.onNodeWithText("Payment Method").assertDoesNotExist()
        composeRule.onNodeWithTag("earnings_period_weekly").assertDoesNotExist()

        // Today-only Trip History shows trip_cod_today (1) and NOT two-days-ago trip
        composeRule.onNodeWithText("Trip History (1)").assertIsDisplayed()
        composeRule.onNodeWithTag("earnings_lazy_column")
            .performScrollToNode(hasTestTag("earnings_job_row_trip_cod_today"))
        composeRule.onNodeWithTag("earnings_job_row_trip_cod_today").assertIsDisplayed()
        composeRule.onNodeWithTag("earnings_job_row_trip_online_week").assertDoesNotExist()

        // Tapping today's trip opens commission-free Trip Detail Dialog
        composeRule.onNodeWithTag("earnings_job_row_trip_cod_today").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("earnings_trip_detail_dialog").assertIsDisplayed()
        composeRule.onNode(
            hasText("Delivery fee") and hasAnyAncestor(hasTestTag("earnings_trip_detail_dialog"))
        ).assertIsDisplayed()
        composeRule.onNode(
            hasText("₱82.00") and hasAnyAncestor(hasTestTag("earnings_trip_detail_dialog"))
        ).assertIsDisplayed()
        composeRule.onNode(
            hasText("Commission fee (-10%)") and hasAnyAncestor(hasTestTag("earnings_trip_detail_dialog"))
        ).assertDoesNotExist()
        composeRule.onNodeWithText("Close").performClick()
        composeRule.waitForIdle()

        // Tapping Total Income card triggers onOpenIncomeHistory
        composeRule.onNodeWithTag("earnings_lazy_column")
            .performScrollToNode(hasTestTag("earnings_total_income_card"))
        composeRule.onNodeWithTag("earnings_total_income_card").performClick()
        composeRule.waitForIdle()
        assertTrue(historyClicked)
    }
}
