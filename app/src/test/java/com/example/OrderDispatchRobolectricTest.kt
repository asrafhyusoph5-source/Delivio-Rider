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
}
