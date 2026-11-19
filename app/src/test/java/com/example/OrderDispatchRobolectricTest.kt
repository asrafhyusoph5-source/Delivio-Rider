package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.constants.DispatchSettings
import com.example.data.constants.GeneralDispatchEvaluator
import com.example.data.constants.OrderStepConstants
import com.example.data.constants.TaxiDispatchEvaluator
import com.example.data.models.DispatchListenerStatus
import com.example.data.models.ListenerConnectionState
import com.example.data.models.Order
import com.example.data.models.OrderDocumentParser
import com.example.data.models.Rider
import com.example.data.models.SkippedOrderRecord
import com.example.data.models.Vehicle
import com.example.ui.components.DispatchStatusFormatter
import com.example.ui.components.DispatchStatusSheetContent
import com.example.ui.theme.DelivioRiderTheme
import com.example.util.CrashReportStore
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OrderDispatchRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `tolerant time reader accepts Timestamp Number ISO string Map and rejects wrong types`() {
        val now = 1_710_000_000_000L
        assertEquals(now, OrderDocumentParser.parseTolerantTimeMillis(Timestamp(Date(now))))
        assertEquals(now, OrderDocumentParser.parseTolerantTimeMillis(now))
        assertEquals(now, OrderDocumentParser.parseTolerantTimeMillis(now / 1000L))
        assertEquals(now, OrderDocumentParser.parseTolerantTimeMillis(mapOf("seconds" to now / 1000L, "nanoseconds" to 0)))
        assertNotNull(OrderDocumentParser.parseTolerantTimeMillis("2025-05-01T10:15:30Z"))
        assertNull(OrderDocumentParser.parseTolerantTimeMillis(null))
        assertNull(OrderDocumentParser.parseTolerantTimeMillis("   "))

        var threwOnBoolean = false
        try {
            OrderDocumentParser.parseTolerantTimeMillis(true, fieldName = "placedAt")
        } catch (_: IllegalArgumentException) {
            threwOnBoolean = true
        }
        assertTrue(threwOnBoolean)

        var threwOnInvalidString = false
        try {
            OrderDocumentParser.parseTolerantTimeMillis("not-a-date", fieldName = "deliveredAt")
        } catch (_: IllegalArgumentException) {
            threwOnInvalidString = true
        }
        assertTrue(threwOnInvalidString)
    }

    @Test
    fun `bad document with wrong date type is skipped while other orders arrive and logs crash report with order id`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        CrashReportStore.clearReports(context)

        val validFood = "ord_valid_1" to mapOf<String, Any?>(
            "orderNumber" to "F1001",
            "serviceType" to "food",
            "status" to "Preparing",
            "placedAt" to Timestamp(Date(1_710_000_000_000L)),
            "tip" to "25.5" // mismatched scalar string for number still parses cleanly
        )
        val badDateOrder = "ord_corrupt_99" to mapOf<String, Any?>(
            "orderNumber" to "R9999",
            "serviceType" to "taxi",
            "status" to "Searching",
            "placedAt" to listOf("invalid", "date", "array") // wrong date type
        )
        val validTaxi = "ord_valid_2" to mapOf<String, Any?>(
            "orderNumber" to "T2002",
            "serviceType" to "taxi",
            "status" to "Searching",
            "vehicleType" to "car4",
            "placedAt" to "2025-05-01T10:15:30Z",
            "pickup" to mapOf("lat" to 9.7401, "lng" to 118.7354, "address" to "Robinson Mall")
        )

        val skippedBadIds = mutableListOf<String>()
        val parsed = OrderDocumentParser.parseOrderMapsIndividually(
            docs = listOf(validFood, badDateOrder, validTaxi),
            sourceLabel = "test_offers",
            onBadDocument = { docId, _, _, error ->
                skippedBadIds.add(docId)
                CrashReportStore.logNonFatalParseError(context, "orders/$docId", error)
            }
        )

        assertEquals(2, parsed.size)
        assertEquals(listOf("ord_valid_1", "ord_valid_2"), parsed.map { it.id })
        assertEquals(25.5, parsed[0].tip, 0.01)
        assertEquals(9.7401, parsed[1].merchantLat, 0.0001)
        assertEquals(118.7354, parsed[1].merchantLng, 0.0001)
        assertEquals(listOf("ord_corrupt_99"), skippedBadIds)

        val reports = CrashReportStore.getReports(context)
        assertTrue(reports.isNotEmpty())
        assertTrue(reports.first().isNonFatal)
        assertTrue(reports.first().documentPath.contains("ord_corrupt_99"))
    }

    @Test
    fun `taxi, parcel, and pabili orders are never filtered out by merchant conditions`() {
        val motoRider = Rider(
            uid = "rider_1",
            isOnline = true,
            status = "active",
            cityId = "ppc",
            vehicle = Vehicle(type = OrderStepConstants.VEHICLE_MOTORCYCLE),
            allowedServices = OrderStepConstants.ALL_SERVICES,
            activeServices = OrderStepConstants.ALL_SERVICES
        )
        val carRider = motoRider.copy(
            uid = "rider_car",
            vehicle = Vehicle(type = OrderStepConstants.VEHICLE_CAR4)
        )
        val dispatch = DispatchSettings(searchRadiusKm = 3.0, taxiSearchRadiusKm = 5.0)
        val riderLoc = Pair(9.7392, 118.7353)

        // Parcel order with NO merchantId, NO merchantAccepted, Placed Order status, and > searchRadiusKm (should still pass since non-merchant!)
        val parcelOrder = Order(
            id = "parcel_1",
            orderNumber = "P100",
            serviceType = OrderStepConstants.SERVICE_PARCEL,
            status = OrderStepConstants.STATUS_PLACED_ORDER,
            cityId = "ppc",
            merchantId = "",
            merchantAccepted = false,
            merchantLat = 0.0,
            merchantLng = 0.0,
            pickupDistanceKm = 7.2
        )
        val parcelEval = GeneralDispatchEvaluator.evaluateSkipReason(
            order = parcelOrder,
            rider = motoRider,
            activeOrders = emptyList(),
            riderLoc = riderLoc,
            isPlatformServiceEnabled = true,
            isCityServiceEnabled = true,
            dispatch = dispatch
        )
        assertTrue(parcelOrder.isNonMerchantService)
        assertTrue("Parcel should not be blocked by merchant checks: ${parcelEval.skipReason}", parcelEval.isEligible)

        // Pabili order with NO merchantId, Placed Order status, merchantAccepted = false
        val pabiliOrder = Order(
            id = "pabili_1",
            orderNumber = "B101",
            serviceType = OrderStepConstants.SERVICE_PABILI,
            status = OrderStepConstants.STATUS_PLACED_ORDER,
            cityId = "ppc",
            merchantId = "",
            merchantAccepted = false,
            pickupDistanceKm = 6.5
        )
        val pabiliEval = GeneralDispatchEvaluator.evaluateSkipReason(
            order = pabiliOrder,
            rider = motoRider,
            activeOrders = emptyList(),
            riderLoc = riderLoc,
            isPlatformServiceEnabled = true,
            isCityServiceEnabled = true,
            dispatch = dispatch
        )
        assertTrue(pabiliOrder.isNonMerchantService)
        assertTrue("Pabili should not be blocked by merchant checks: ${pabiliEval.skipReason}", pabiliEval.isEligible)

        // Taxi order with NO merchantId, Searching status, within taxi radius, car4 match
        val taxiOrder = Order(
            id = "taxi_1",
            orderNumber = "TX50",
            serviceType = OrderStepConstants.SERVICE_TAXI,
            status = OrderStepConstants.REQ_SEARCHING,
            vehicleType = OrderStepConstants.VEHICLE_CAR4,
            cityId = "ppc",
            merchantId = "",
            merchantAccepted = false,
            merchantLat = 9.7410,
            merchantLng = 118.7360
        )
        val taxiEligible = TaxiDispatchEvaluator.evaluateSkipReason(
            order = taxiOrder,
            rider = carRider,
            activeOrders = emptyList(),
            riderLoc = riderLoc,
            isPlatformServiceEnabled = true,
            isCityServiceEnabled = true,
            dispatch = dispatch
        )
        assertTrue("Eligible taxi rider should receive offer: ${taxiEligible.skipReason}", taxiEligible.isEligible)
        assertEquals(20, TaxiDispatchEvaluator.TAXI_TIMER_SECONDS)
    }

    @Test
    fun `taxi eligibility enforces online, service toggles, vehicleType, no active order, radius, targetRiderId`() {
        val dispatch = DispatchSettings(taxiSearchRadiusKm = 5.0)
        val riderLoc = Pair(9.7392, 118.7353)
        val carRider = Rider(
            uid = "r_car",
            isOnline = true,
            status = "active",
            cityId = "ppc",
            vehicle = Vehicle(type = OrderStepConstants.VEHICLE_CAR4),
            allowedServices = listOf("taxi", "parcel"),
            activeServices = listOf("taxi", "parcel")
        )
        val taxiMotoOrder = Order(
            id = "tx_moto",
            orderNumber = "TX10",
            serviceType = OrderStepConstants.SERVICE_TAXI,
            status = OrderStepConstants.STATUS_PLACED_ORDER,
            vehicleType = OrderStepConstants.VEHICLE_MOTORCYCLE,
            cityId = "ppc",
            pickupDistanceKm = 1.5
        )

        // Vehicle mismatch
        val vehicleSkip = TaxiDispatchEvaluator.evaluateSkipReason(
            order = taxiMotoOrder,
            rider = carRider,
            activeOrders = emptyList(),
            riderLoc = riderLoc,
            isPlatformServiceEnabled = true,
            isCityServiceEnabled = true,
            dispatch = dispatch
        )
        assertFalse(vehicleSkip.isEligible)
        assertEquals("vehicle mismatch (order wants motorcycle)", vehicleSkip.skipReason)
        assertEquals("Taxi: vehicle mismatch (order wants motorcycle)", "${SkippedOrderRecord.labelForOrder(taxiMotoOrder)}: ${vehicleSkip.skipReason}")

        // City service off
        val citySkip = TaxiDispatchEvaluator.evaluateSkipReason(
            order = taxiMotoOrder.copy(vehicleType = OrderStepConstants.VEHICLE_CAR4),
            rider = carRider,
            activeOrders = emptyList(),
            riderLoc = riderLoc,
            isPlatformServiceEnabled = true,
            isCityServiceEnabled = false,
            dispatch = dispatch
        )
        assertFalse(citySkip.isEligible)
        assertEquals("service off in city", citySkip.skipReason)
        assertEquals("Taxi: service off in city", "${SkippedOrderRecord.labelForOrder(taxiMotoOrder)}: ${citySkip.skipReason}")

        // Outside radius
        val farOrder = Order(
            id = "tx_far",
            orderNumber = "R2504",
            serviceType = OrderStepConstants.SERVICE_FOOD,
            status = OrderStepConstants.STATUS_READY,
            cityId = "ppc",
            pickupDistanceKm = 6.1
        )
        val radiusSkip = GeneralDispatchEvaluator.evaluateSkipReason(
            order = farOrder,
            rider = carRider.copy(
                vehicle = Vehicle(type = OrderStepConstants.VEHICLE_MOTORCYCLE),
                allowedServices = OrderStepConstants.ALL_SERVICES,
                activeServices = OrderStepConstants.ALL_SERVICES
            ),
            activeOrders = emptyList(),
            riderLoc = null,
            isPlatformServiceEnabled = true,
            isCityServiceEnabled = true,
            dispatch = dispatch.copy(searchRadiusKm = 5.0)
        )
        assertFalse(radiusSkip.isEligible)
        assertEquals("outside radius (6.1 km, limit 5)", radiusSkip.skipReason)
        assertEquals("Order #R2504: outside radius (6.1 km, limit 5)", "${SkippedOrderRecord.labelForOrder(farOrder)}: ${radiusSkip.skipReason}")

        // Active order blocks taxi
        val busySkip = TaxiDispatchEvaluator.evaluateSkipReason(
            order = taxiMotoOrder.copy(vehicleType = OrderStepConstants.VEHICLE_CAR4),
            rider = carRider,
            activeOrders = listOf(Order(id = "act_1")),
            riderLoc = riderLoc,
            isPlatformServiceEnabled = true,
            isCityServiceEnabled = true,
            dispatch = dispatch
        )
        assertFalse(busySkip.isEligible)
        assertEquals("busy with active order", busySkip.skipReason)

        // targetRiderId exclusivity ensures one rider at a time
        val anotherRiderSkip = TaxiDispatchEvaluator.evaluateSkipReason(
            order = taxiMotoOrder.copy(vehicleType = OrderStepConstants.VEHICLE_CAR4, targetRiderId = "other_rider"),
            rider = carRider,
            activeOrders = emptyList(),
            riderLoc = riderLoc,
            isPlatformServiceEnabled = true,
            isCityServiceEnabled = true,
            dispatch = dispatch
        )
        assertFalse(anotherRiderSkip.isEligible)
        assertEquals("offered to another rider", anotherRiderSkip.skipReason)
    }

    @Test
    fun `taxi visibility trims and lowercases vehicleType and checks allowedServices contains taxi`() {
        val dispatch = DispatchSettings(taxiSearchRadiusKm = 5.0)
        val riderLoc = Pair(9.7392, 118.7353)
        // Rider has mixed-case/whitespace allowedServices and vehicleType, and activeServices does NOT include taxi
        val rider = Rider(
            uid = "r_taxi_1",
            isOnline = true,
            status = "active",
            cityId = "ppc",
            vehicle = Vehicle(type = "  Car4  "),
            allowedServices = listOf("food", " Taxi "),
            activeServices = listOf("food")
        )
        val matchingTaxi = Order(
            id = "tx_case",
            orderNumber = "TX77",
            serviceType = " Taxi ",
            status = OrderStepConstants.REQ_SEARCHING,
            vehicleType = "CAR4 ",
            cityId = "ppc",
            pickupDistanceKm = 2.0
        )

        val result = TaxiDispatchEvaluator.evaluateSkipReason(
            order = matchingTaxi,
            rider = rider,
            activeOrders = emptyList(),
            riderLoc = riderLoc,
            isPlatformServiceEnabled = true,
            isCityServiceEnabled = true,
            dispatch = dispatch
        )
        assertTrue("Expected trimmed/lowercased vehicleType and allowedServices taxi to match: ${result.skipReason}", result.isEligible)

        // Custom / non-canonical vehicle type like "sedan" vs "motorcycle" must NOT match
        val motoRider = rider.copy(vehicle = Vehicle(type = "motorcycle"))
        val sedanOrder = matchingTaxi.copy(vehicleType = "sedan")
        val sedanSkip = TaxiDispatchEvaluator.evaluateSkipReason(
            order = sedanOrder,
            rider = motoRider,
            activeOrders = emptyList(),
            riderLoc = riderLoc,
            isPlatformServiceEnabled = true,
            isCityServiceEnabled = true,
            dispatch = dispatch
        )
        assertFalse(sedanSkip.isEligible)
        assertEquals("vehicle mismatch (order wants sedan)", sedanSkip.skipReason)

        // Diagnostics checks all 7 required conditions with PASS / FAIL accurately
        val diag = TaxiDispatchEvaluator.diagnoseTaxiOrder(
            order = sedanOrder.copy(
                cityId = "ppc",
                pickupDistanceKm = 6.2,
                excludeRiderIds = listOf("r_taxi_1")
            ),
            rider = motoRider.copy(
                isOnline = false,
                allowedServices = listOf("food")
            ),
            activeOrders = listOf(Order(id = "active_1")),
            riderLoc = null,
            isPlatformServiceEnabled = true,
            isCityServiceEnabled = false,
            dispatch = dispatch
        )
        assertEquals(
            listOf("allowedServices", "vehicle match", "city service", "online", "busy", "distance", "excluded list"),
            diag.conditions.map { it.label }
        )
        assertTrue(diag.conditions.all { !it.passed })

        val passingDiag = TaxiDispatchEvaluator.diagnoseTaxiOrder(
            order = matchingTaxi,
            rider = rider,
            activeOrders = emptyList(),
            riderLoc = riderLoc,
            isPlatformServiceEnabled = true,
            isCityServiceEnabled = true,
            dispatch = dispatch
        )
        assertEquals(7, passingDiag.conditions.size)
        assertTrue(passingDiag.conditions.all { it.passed })
    }

    @Test
    fun `dispatch status sheet renders all telemetry counts, last taxi 7 conditions with PASS or FAIL, and skip reasons`() {
        var offlineClicked = false
        var closedClicked = false
        val now = 1_710_000_600_000L

        val skips = listOf(
            SkippedOrderRecord("ord_1", "Order #R2504", "outside radius (6.1 km, limit 5)"),
            SkippedOrderRecord("ord_2", "Taxi", "vehicle mismatch (order wants motorcycle)"),
            SkippedOrderRecord("ord_3", "Taxi", "service off in city"),
            SkippedOrderRecord("ord_bad", "Order #B99", "bad data (Wrong date type)", isBadData = true)
        )

        val taxiDiag = TaxiDispatchEvaluator.diagnoseTaxiOrder(
            order = Order(
                id = "tx_diag_1",
                orderNumber = "TX88",
                serviceType = OrderStepConstants.SERVICE_TAXI,
                vehicleType = "car4",
                cityId = "ppc",
                pickupDistanceKm = 6.4
            ),
            rider = Rider(
                uid = "r_car",
                isOnline = true,
                status = "active",
                cityId = "ppc",
                vehicle = Vehicle(type = "motorcycle"),
                allowedServices = listOf("food", "taxi")
            ),
            activeOrders = emptyList(),
            riderLoc = null,
            isPlatformServiceEnabled = true,
            isCityServiceEnabled = true,
            dispatch = DispatchSettings(taxiSearchRadiusKm = 5.0)
        )

        composeRule.setContent {
            DelivioRiderTheme(darkTheme = true) {
                DispatchStatusSheetContent(
                    onlineSinceMillis = now - 300_000L,
                    lastLocationMillis = now - 12_000L,
                    enabledServices = listOf("Delivio Food", "Delivio Taxi"),
                    vehicleType = OrderStepConstants.VEHICLE_CAR4,
                    listenerStatus = DispatchListenerStatus(
                        state = ListenerConnectionState.CONNECTED,
                        lastUpdateMillis = now - 4_000L
                    ),
                    ordersSeenCount = 6,
                    badDataSkippedCount = 1,
                    eligibleOrdersCount = 2,
                    skippedOrders = skips,
                    lastTaxiDiagnostics = taxiDiag,
                    hasActiveOrders = false,
                    onGoOffline = { offlineClicked = true },
                    onDismiss = { closedClicked = true }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Dispatch status").assertIsDisplayed()
        composeRule.onNodeWithText("4 Seater Car").assertIsDisplayed()
        composeRule.onNodeWithText("Delivio Food, Delivio Taxi").assertIsDisplayed()
        composeRule.onNodeWithTag("dispatch_count_seen").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("dispatch_count_bad_data").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("dispatch_count_eligible").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithText("Last taxi order seen").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("taxi_cond_allowedServices").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("taxi_cond_vehicle match").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("taxi_cond_city service").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("taxi_cond_online").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("taxi_cond_busy").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("taxi_cond_distance").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("taxi_cond_excluded list").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithText("Order #R2504: outside radius (6.1 km, limit 5)").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Taxi: vehicle mismatch (order wants motorcycle)").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Taxi: service off in city").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("dispatch_status_go_offline_button").performScrollTo().performClick()
        assertTrue(offlineClicked)

        composeRule.onNodeWithTag("dispatch_status_close_button").performScrollTo().performClick()
        assertTrue(closedClicked)

        val (restartingLabel, restartingDetail) = DispatchStatusFormatter.formatListenerState(
            DispatchListenerStatus(state = ListenerConnectionState.RESTARTING, restartAttempt = 2, nextRetrySeconds = 5),
            now
        )
        assertEquals("Restarting", restartingLabel)
        assertTrue(restartingDetail.contains("5s"))
    }

    @Test
    fun `parcel parser reads sender dropoff parcel metadata vehicleType feePayer and cashCollected`() {
        val raw = mapOf<String, Any?>(
            "orderNumber" to "P-408",
            "serviceType" to "parcel",
            "status" to "Placed Order",
            "vehicleType" to "  Sedan ",
            "feePayer" to "recipient",
            "paymentMethod" to "cod",
            "cashToCollect" to 185.0,
            "pickup" to mapOf(
                "name" to "Juan Dela Cruz",
                "phone" to "09171112222",
                "address" to "12 Rizal Ave, Brgy Tanglaw, Puerto Princesa",
                "lat" to 9.7405,
                "lng" to 118.7351,
                "note" to "Call upon arrival at gate"
            ),
            "dropoff" to mapOf(
                "name" to "Ana Reyes",
                "phone" to "09183334444",
                "address" to "88 Taft St, Brgy San Pedro, Puerto Princesa",
                "lat" to 9.7512,
                "lng" to 118.7420,
                "note" to "Leave with lobby guard"
            ),
            "parcel" to mapOf(
                "size" to "Large",
                "weightKg" to 8.5,
                "description" to "Glasswares and documents",
                "fragile" to true
            )
        )

        val order = OrderDocumentParser.parseOrderFromMap("ord_parcel_parse", raw)
        assertTrue(order.isParcel)
        assertEquals("Juan Dela Cruz", order.senderName)
        assertEquals("09171112222", order.merchantPhone)
        assertEquals("12 Rizal Ave, Brgy Tanglaw, Puerto Princesa", order.merchantAddress)
        assertEquals("Call upon arrival at gate", order.resolvedPickupNote)

        assertEquals("Ana Reyes", order.customerName)
        assertEquals("09183334444", order.customerPhone)
        assertEquals("88 Taft St, Brgy San Pedro, Puerto Princesa", order.customerAddress)
        assertEquals("Leave with lobby guard", order.resolvedDropoffNote)

        assertEquals("Large", order.parcelSize)
        assertEquals(8.5, order.parcelWeightKg, 0.001)
        assertEquals("Glasswares and documents", order.parcelItemDescription)
        assertTrue(order.parcelFragile)
        assertEquals("Sedan", order.resolvedParcelVehicleLabel)
        assertTrue(order.isSedanParcel)
        assertEquals("recipient", order.resolvedParcelFeePayer)
        assertTrue(order.parcelCollectsAtDropoff)
        assertFalse(order.parcelCollectsAtPickup)
        assertEquals(185.0, order.cashAmountToCollect, 0.001)
    }

    @Test
    fun `parcel flow rules enforce forward-only 4 transitions and rate formula uses parcelSedan vs single`() {
        val t1 = com.example.data.models.ParcelFlowRules.transitionFor(com.example.data.models.ParcelStepStage.TO_SENDER)!!
        assertEquals(OrderStepConstants.STATUS_RIDER_ON_WAY_SENDER, t1.expectedPrevStatus)
        assertEquals(OrderStepConstants.STATUS_ARRIVED_AT_SENDER, t1.nextStatus)
        assertEquals(OrderStepConstants.STEP_AT_SENDER, t1.nextStep)

        val t2 = com.example.data.models.ParcelFlowRules.transitionFor(com.example.data.models.ParcelStepStage.AT_SENDER)!!
        assertEquals(OrderStepConstants.STATUS_ARRIVED_AT_SENDER, t2.expectedPrevStatus)
        assertEquals(OrderStepConstants.STATUS_RIDER_ON_WAY_RECIPIENT, t2.nextStatus)
        assertEquals(OrderStepConstants.STEP_TO_RECIPIENT, t2.nextStep)

        val t3 = com.example.data.models.ParcelFlowRules.transitionFor(com.example.data.models.ParcelStepStage.TO_RECIPIENT)!!
        assertEquals(OrderStepConstants.STATUS_RIDER_ON_WAY_RECIPIENT, t3.expectedPrevStatus)
        assertEquals(OrderStepConstants.STATUS_ARRIVED_AT_RECIPIENT, t3.nextStatus)
        assertEquals(OrderStepConstants.STEP_AT_RECIPIENT, t3.nextStep)

        val t4 = com.example.data.models.ParcelFlowRules.transitionFor(com.example.data.models.ParcelStepStage.AT_RECIPIENT)!!
        assertEquals(OrderStepConstants.STATUS_ARRIVED_AT_RECIPIENT, t4.expectedPrevStatus)
        assertEquals(OrderStepConstants.STATUS_DELIVERED, t4.nextStatus)
        assertEquals(OrderStepConstants.STEP_COMPLETED, t4.nextStep)

        assertNull(com.example.data.models.ParcelFlowRules.transitionFor(com.example.data.models.ParcelStepStage.COMPLETED))
        assertTrue(com.example.data.models.ParcelFlowRules.isExpectedStatus(" Arrived at sender ", OrderStepConstants.STATUS_ARRIVED_AT_SENDER))
        assertFalse(com.example.data.models.ParcelFlowRules.isExpectedStatus(OrderStepConstants.STATUS_DELIVERED, OrderStepConstants.STATUS_ARRIVED_AT_SENDER))

        val paySettings = com.example.data.constants.RiderPaySettings(
            single = com.example.data.constants.RiderPayRate(minFare = 45.0, includedKm = 2.0, perKmExtra = 10.0),
            parcelSedan = com.example.data.constants.RiderPayRate(minFare = 110.0, includedKm = 3.0, perKmExtra = 20.0)
        )
        val motoParcel = Order(
            id = "p_moto",
            serviceType = OrderStepConstants.SERVICE_PARCEL,
            vehicleType = "motorcycle",
            deliveryDistanceKm = 5.0
        )
        val sedanParcel = Order(
            id = "p_sedan",
            serviceType = OrderStepConstants.SERVICE_PARCEL,
            vehicleType = "sedan",
            deliveryDistanceKm = 5.0
        )
        // Moto: 45 + (5 - 2)*10 = 75
        assertEquals(75.0, com.example.data.constants.BatchConstants.calculateDeliveryFee(motoParcel, 1, false, paySettings), 0.001)
        // Sedan: 110 + (5 - 3)*20 = 150
        assertEquals(150.0, com.example.data.constants.BatchConstants.calculateDeliveryFee(sedanParcel, 1, false, paySettings), 0.001)
    }

    @Test
    fun `parcel cash rule assigns collection to pickup or dropoff stop based on feePayer and paymentMethod`() {
        val baseOrder = Order(
            id = "p_cash",
            orderNumber = "P500",
            serviceType = OrderStepConstants.SERVICE_PARCEL,
            status = OrderStepConstants.STATUS_RIDER_ON_WAY_SENDER,
            riderStep = OrderStepConstants.STEP_TO_SENDER,
            paymentMethod = "cod",
            cashToCollect = 120.0,
            merchantLat = 9.7400,
            merchantLng = 118.7350,
            customerLat = 9.7500,
            customerLng = 118.7450
        )

        val senderPaysStops = com.example.data.models.RouteStopCalculator.computeBatchStops(
            listOf(baseOrder.copy(parcelFeePayer = "sender", cashCollectAt = "sender")), 9.739, 118.735
        )
        assertEquals(120.0, senderPaysStops[0].cashToCollect, 0.001)
        assertTrue(senderPaysStops[0].isCod)
        assertEquals(0.0, senderPaysStops[1].cashToCollect, 0.001)
        assertFalse(senderPaysStops[1].isCod)

        val recipientPaysStops = com.example.data.models.RouteStopCalculator.computeBatchStops(
            listOf(baseOrder.copy(parcelFeePayer = "recipient", cashCollectAt = "recipient")), 9.739, 118.735
        )
        assertEquals(0.0, recipientPaysStops[0].cashToCollect, 0.001)
        assertFalse(recipientPaysStops[0].isCod)
        assertEquals(120.0, recipientPaysStops[1].cashToCollect, 0.001)
        assertTrue(recipientPaysStops[1].isCod)

        val walletStops = com.example.data.models.RouteStopCalculator.computeBatchStops(
            listOf(baseOrder.copy(paymentMethod = "wallet", parcelFeePayer = "sender", cashCollectAt = "sender")), 9.739, 118.735
        )
        assertEquals(0.0, walletStops[0].cashToCollect, 0.001)
        assertEquals(0.0, walletStops[1].cashToCollect, 0.001)
        assertFalse(walletStops[0].isCod)
        assertFalse(walletStops[1].isCod)
    }

    @Test
    fun `parcel vehicle matching allows car4 and car8 for sedan and motorcycle only for motorcycle`() {
        val dispatch = DispatchSettings(searchRadiusKm = 8.0)
        val riderLoc = Pair(9.7392, 118.7353)
        val baseRider = Rider(
            uid = "r_p",
            isOnline = true,
            status = "active",
            cityId = "ppc",
            allowedServices = OrderStepConstants.ALL_SERVICES,
            activeServices = OrderStepConstants.ALL_SERVICES
        )
        val sedanOrder = Order(
            id = "p_sed",
            orderNumber = "P801",
            serviceType = OrderStepConstants.SERVICE_PARCEL,
            status = OrderStepConstants.STATUS_PLACED_ORDER,
            vehicleType = " Sedan ",
            cityId = "ppc",
            pickupDistanceKm = 2.0
        )
        val motoOrder = sedanOrder.copy(id = "p_mot", vehicleType = " Motorcycle ")

        // car4 & car8 match sedan parcel
        val car4Match = GeneralDispatchEvaluator.evaluateSkipReason(
            order = sedanOrder,
            rider = baseRider.copy(vehicle = Vehicle(type = "car4")),
            activeOrders = emptyList(),
            riderLoc = riderLoc,
            isPlatformServiceEnabled = true,
            isCityServiceEnabled = true,
            dispatch = dispatch
        )
        assertTrue("car4 should match sedan parcel: ${car4Match.skipReason}", car4Match.isEligible)

        val car8Match = GeneralDispatchEvaluator.evaluateSkipReason(
            order = sedanOrder,
            rider = baseRider.copy(vehicle = Vehicle(type = "CAR8 ")),
            activeOrders = emptyList(),
            riderLoc = riderLoc,
            isPlatformServiceEnabled = true,
            isCityServiceEnabled = true,
            dispatch = dispatch
        )
        assertTrue("car8 should match sedan parcel: ${car8Match.skipReason}", car8Match.isEligible)

        // motorcycle rejected from sedan parcel with clear reason
        val motoRejected = GeneralDispatchEvaluator.evaluateSkipReason(
            order = sedanOrder,
            rider = baseRider.copy(vehicle = Vehicle(type = "motorcycle")),
            activeOrders = emptyList(),
            riderLoc = riderLoc,
            isPlatformServiceEnabled = true,
            isCityServiceEnabled = true,
            dispatch = dispatch
        )
        assertFalse(motoRejected.isEligible)
        assertEquals("Parcel needs a Sedan (4 or 8 wheel car)", motoRejected.skipReason)

        // car4 rejected from motorcycle parcel with clear reason
        val carRejected = GeneralDispatchEvaluator.evaluateSkipReason(
            order = motoOrder,
            rider = baseRider.copy(vehicle = Vehicle(type = "car4")),
            activeOrders = emptyList(),
            riderLoc = riderLoc,
            isPlatformServiceEnabled = true,
            isCityServiceEnabled = true,
            dispatch = dispatch
        )
        assertFalse(carRejected.isEligible)
        assertEquals("Parcel needs a Motorcycle", carRejected.skipReason)
    }
}
