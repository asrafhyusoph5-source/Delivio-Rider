package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import com.example.data.constants.OrderStepConstants
import com.example.data.models.Order
import com.example.data.models.OrderItem
import com.example.data.models.RouteStopCalculator
import com.example.data.models.TaxiStop
import com.example.ui.components.OrderDetailsSheet
import com.example.ui.components.OrderSheetState
import com.example.ui.theme.DelivioRiderTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class OrderDetailsSheetUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleTaxiOrder(
        status: String = OrderStepConstants.STATUS_DRIVER_ON_WAY,
        riderStep: String = OrderStepConstants.STEP_TO_PICKUP,
        arrivedAtPickupAt: Any? = null,
        pickedUpAt: Any? = null,
        arrivedAtDropoffAt: Any? = null,
        stops: List<TaxiStop> = emptyList()
    ) = Order(
        id = "ord_taxi_1",
        orderNumber = "8821",
        serviceType = OrderStepConstants.SERVICE_TAXI,
        status = status,
        customerName = "Marco Villanueva",
        customerPhone = "09171234567",
        merchantAddress = "Robinsons Place Palawan, San Miguel, Puerto Princesa",
        customerAddress = "Puerto Princesa Airport, Brgy San Miguel, Puerto Princesa",
        customerArea = "San Miguel",
        merchantLat = 9.7410,
        merchantLng = 118.7360,
        customerLat = 9.7420,
        customerLng = 118.7580,
        passengerCount = 2,
        pickupNote = "At the main mall entrance",
        riderStep = riderStep,
        arrivedAtPickupAt = arrivedAtPickupAt,
        pickedUpAt = pickedUpAt,
        arrivedAtDropoffAt = arrivedAtDropoffAt,
        stops = stops,
        paymentMethod = "cod",
        cashToCollect = 150.0,
        riderFeeEstimate = 127.50
    )

    private fun samplePabiliOrder(
        status: String = OrderStepConstants.STATUS_RIDER_ON_WAY_VENDOR,
        riderStep: String = OrderStepConstants.STEP_TO_VENDOR,
        paidOnMerchant: Double = 0.0,
        overBudgetApproved: Boolean = false
    ) = Order(
        id = "ord_pabili_1",
        orderNumber = "7740",
        serviceType = OrderStepConstants.SERVICE_PABILI,
        status = status,
        riderStep = riderStep,
        merchantName = "Puregold Public Market",
        merchantPhone = "09998887777",
        merchantAddress = "Market Rd, Puerto Princesa",
        merchantLat = 9.7400,
        merchantLng = 118.7350,
        customerName = "Elena Cruz",
        customerPhone = "09170001111",
        customerAddress = "45 Bonifacio St, San Pedro, Puerto Princesa",
        customerArea = "San Pedro",
        customerLat = 9.7520,
        customerLng = 118.7420,
        items = listOf(
            OrderItem(name = "Rice 5kg", quantity = 1, price = 280.0),
            OrderItem(name = "Eggs 1 tray", quantity = 1, price = 210.0)
        ),
        budgetLimit = 500.0,
        paidOnMerchant = paidOnMerchant,
        finalReceiptTotal = paidOnMerchant,
        overBudgetApproved = overBudgetApproved,
        paymentMethod = "cod",
        feesDeliveryFee = 60.0,
        feesServiceFee = 25.0,
        cashToCollect = 85.0,
        riderFeeEstimate = 65.0
    )

    @Test
    fun collapsedState_showsOnlyHandleNameAndChevron() {
        val order = Order(
            id = "ord_food_1",
            orderNumber = "1042",
            serviceType = OrderStepConstants.SERVICE_FOOD,
            merchantName = "Jollibee",
            branchName = "Rizal",
            branchCode = "MAIN",
            merchantLat = 9.7400,
            merchantLng = 118.7350,
            customerName = "Maria Santos",
            customerLat = 9.7500,
            customerLng = 118.7360,
            riderStep = OrderStepConstants.STEP_TO_MERCHANT,
            paymentMethod = "cod",
            cashToCollect = 120.0,
            items = listOf(OrderItem(name = "Chickenjoy", quantity = 1, price = 120.0))
        )
        val stops = RouteStopCalculator.computeBatchStops(listOf(order), 9.7392, 118.7353)
        var currentState = OrderSheetState.COLLAPSED

        composeRule.setContent {
            DelivioRiderTheme {
                OrderDetailsSheet(
                    batchStops = stops,
                    currentStop = stops.first(),
                    activeOrders = listOf(order),
                    isOnline = true,
                    walletBalance = 0.0,
                    todayEarnings = 0.0,
                    todayTrips = 0,
                    currencySymbol = "₱",
                    supportPhone = null,
                    routeDistanceKm = 1.4,
                    routeEtaMinutes = 6,
                    sheetState = OrderSheetState.COLLAPSED,
                    onSheetStateChange = { currentState = it },
                    onGoOnlineClick = {},
                    onAdvanceStep = { _, _ -> },
                    onReleaseOrder = {},
                    onReportProblem = {},
                    onCallClick = { _, _ -> }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("collapsed_trip_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("sheet_drag_handle").assertIsDisplayed()
        composeRule.onNodeWithText("Jollibee - Rizal (MAIN)").assertIsDisplayed()
        composeRule.onNodeWithTag("collapsed_up_chevron", useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithTag("sheet_slide_button").assertDoesNotExist()
        composeRule.onNodeWithTag("taxi_press_action_button").assertDoesNotExist()
        composeRule.onNodeWithTag("navigate_stop_button").assertDoesNotExist()
        composeRule.onNodeWithTag("report_problem_button").assertDoesNotExist()
        composeRule.onNodeWithTag("earnings_details_toggle").assertDoesNotExist()
        composeRule.onNodeWithTag("payment_line_bar").assertDoesNotExist()

        composeRule.onNodeWithTag("collapsed_trip_bar").performClick()
        composeRule.waitForIdle()
        assertEquals(OrderSheetState.EXPANDED, currentState)
    }

    @Test
    fun taxiStep1_showsPickupOnlyPressButtonAndCollapsesCleanly() {
        val taxiOrder = sampleTaxiOrder()
        val stops = RouteStopCalculator.computeBatchStops(listOf(taxiOrder), 9.7392, 118.7353)
        var currentState = OrderSheetState.EXPANDED
        var advanceCount = 0

        composeRule.setContent {
            DelivioRiderTheme {
                OrderDetailsSheet(
                    batchStops = stops,
                    currentStop = stops.first(),
                    activeOrders = listOf(taxiOrder),
                    isOnline = true,
                    walletBalance = 0.0,
                    todayEarnings = 0.0,
                    todayTrips = 0,
                    currencySymbol = "₱",
                    supportPhone = null,
                    routeDistanceKm = 0.8,
                    routeEtaMinutes = 4,
                    driverLocation = Pair(9.7411, 118.7361),
                    isDriverLocationFresh = true,
                    sheetState = OrderSheetState.EXPANDED,
                    onSheetStateChange = { currentState = it },
                    onGoOnlineClick = {},
                    onAdvanceStep = { _, _ -> advanceCount += 1 },
                    onReleaseOrder = {},
                    onReportProblem = {},
                    onCallClick = { _, _ -> }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Step 1 of 4 - Go to pickup").assertIsDisplayed()
        composeRule.onNodeWithTag("expanded_stop_name").assertIsDisplayed()
        composeRule.onNodeWithText("4 min • 0.8 km").assertIsDisplayed()
        composeRule.onNodeWithTag("navigate_stop_button").assertIsDisplayed()

        // Single details card showing PICKUP address ONLY; NO timeline; NO items list; NO drop-off address
        composeRule.onNodeWithTag("stop_timeline_list").assertDoesNotExist()
        composeRule.onNodeWithText("Items to Pick Up").assertDoesNotExist()
        composeRule.onNodeWithText("No item list provided").assertDoesNotExist()
        composeRule.onNodeWithTag("taxi_stop_details_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("PICKUP").assertIsDisplayed()
        composeRule.onNodeWithText("Robinsons Place Palawan, San Miguel, Puerto Princesa").assertIsDisplayed()
        composeRule.onNodeWithText("Puerto Princesa Airport, Brgy San Miguel, Puerto Princesa").assertDoesNotExist()
        composeRule.onNodeWithText("DROP-OFF").assertDoesNotExist()

        // Passenger count and pickup note
        composeRule.onNodeWithTag("taxi_passenger_meta").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("2 passengers").assertIsDisplayed()
        composeRule.onNodeWithText("At the main mall entrance").assertIsDisplayed()

        // Call, Chat, Help
        composeRule.onNodeWithTag("quick_actions_row").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("sheet_call_button").assertIsDisplayed()
        composeRule.onNodeWithText("Call").assertIsDisplayed()
        composeRule.onNodeWithTag("customer_chat_button").assertIsDisplayed()
        composeRule.onNodeWithText("Chat").assertIsDisplayed()
        composeRule.onNodeWithTag("sheet_headset_button").assertIsDisplayed()
        composeRule.onNodeWithText("Help").assertIsDisplayed()

        composeRule.onNodeWithTag("earnings_details_toggle").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Earnings details (₱127.50)").assertIsDisplayed()

        // Step 1 uses press button "Arrived at Marco" (NOT a slide button)
        composeRule.onNodeWithTag("sheet_slide_button").assertDoesNotExist()
        composeRule.onNodeWithTag("taxi_press_action_button").assertIsDisplayed()
        composeRule.onNodeWithText("Arrived at Marco").assertIsDisplayed()
        composeRule.onNodeWithTag("report_problem_button").assertIsDisplayed()

        // Pressing within 300 m advances directly without dialog
        composeRule.onNodeWithTag("taxi_press_action_button").performClick()
        composeRule.waitForIdle()
        assertEquals(1, advanceCount)

        // Tapping top handle collapses back to COLLAPSED
        composeRule.onNodeWithTag("sheet_drag_handle").performClick()
        composeRule.waitForIdle()
        assertEquals(OrderSheetState.COLLAPSED, currentState)
    }

    @Test
    fun taxiStep1_farFromPickupShowsConfirmDialogBeforeAdvancing() {
        val taxiOrder = sampleTaxiOrder()
        val stops = RouteStopCalculator.computeBatchStops(listOf(taxiOrder), 9.7392, 118.7353)
        var advanceCount = 0

        composeRule.setContent {
            DelivioRiderTheme {
                OrderDetailsSheet(
                    batchStops = stops,
                    currentStop = stops.first(),
                    activeOrders = listOf(taxiOrder),
                    isOnline = true,
                    walletBalance = 0.0,
                    todayEarnings = 0.0,
                    todayTrips = 0,
                    currencySymbol = "₱",
                    supportPhone = null,
                    routeDistanceKm = 2.0,
                    routeEtaMinutes = 8,
                    driverLocation = Pair(9.7600, 118.7360), // > 2 km away (> 300m)
                    isDriverLocationFresh = true,
                    sheetState = OrderSheetState.EXPANDED,
                    onGoOnlineClick = {},
                    onAdvanceStep = { _, _ -> advanceCount += 1 },
                    onReleaseOrder = {},
                    onReportProblem = {},
                    onCallClick = { _, _ -> }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("taxi_press_action_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("You seem far from the pickup point. Mark as arrived anyway?").assertIsDisplayed()
        assertEquals(0, advanceCount)

        // Cancel dismisses without advancing
        composeRule.onNodeWithTag("far_arrival_cancel_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("You seem far from the pickup point. Mark as arrived anyway?").assertDoesNotExist()
        assertEquals(0, advanceCount)

        // Re-tap and confirm Yes
        composeRule.onNodeWithTag("taxi_press_action_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("far_arrival_yes_button").performClick()
        composeRule.waitForIdle()
        assertEquals(1, advanceCount)
    }

    @Test
    fun taxiStep2_slidesToConfirmPickedUpWithNoChecklistDialog() {
        val taxiOrder = sampleTaxiOrder(
            status = OrderStepConstants.STATUS_DRIVER_ARRIVED,
            riderStep = OrderStepConstants.STEP_AT_PICKUP,
            arrivedAtPickupAt = 1700000000000L
        )
        val stops = RouteStopCalculator.computeBatchStops(listOf(taxiOrder), 9.7410, 118.7360)
        var advanceCount = 0

        composeRule.setContent {
            DelivioRiderTheme {
                OrderDetailsSheet(
                    batchStops = stops,
                    currentStop = stops.first(),
                    activeOrders = listOf(taxiOrder),
                    isOnline = true,
                    walletBalance = 0.0,
                    todayEarnings = 0.0,
                    todayTrips = 0,
                    currencySymbol = "₱",
                    supportPhone = null,
                    sheetState = OrderSheetState.EXPANDED,
                    onGoOnlineClick = {},
                    onAdvanceStep = { _, _ -> advanceCount += 1 },
                    onReleaseOrder = {},
                    onReportProblem = {},
                    onCallClick = { _, _ -> }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Step 2 of 4 - Pick up").assertIsDisplayed()
        composeRule.onNodeWithText("PICKUP").assertIsDisplayed()
        composeRule.onNodeWithText("Robinsons Place Palawan, San Miguel, Puerto Princesa").assertIsDisplayed()
        composeRule.onNodeWithText("Puerto Princesa Airport, Brgy San Miguel, Puerto Princesa").assertDoesNotExist()

        composeRule.onNodeWithTag("taxi_press_action_button").assertDoesNotExist()
        composeRule.onNodeWithTag("sheet_slide_button").assertIsDisplayed()
        composeRule.onNodeWithText("Slide to confirm Picked Up").assertIsDisplayed()

        // Sliding confirms directly with NO "Pick up Checklist" dialog
        composeRule.onNodeWithTag("slide_confirm_thumb", useUnmergedTree = true).performTouchInput {
            swipeRight(startX = centerX, endX = centerX + 1500f, durationMillis = 300)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Pick up Checklist").assertDoesNotExist()
        assertEquals(1, advanceCount)
    }

    @Test
    fun taxiStep3AndStep4_showDropoffOnlyAndConfirmButtons() {
        // Step 3: On trip, arrivedAtDropoffAt empty -> Press button "Arrived at the Drop Off Area"
        val step3Order = sampleTaxiOrder(
            status = OrderStepConstants.STATUS_ON_TRIP,
            riderStep = "${OrderStepConstants.STEP_TO_STOP_PREFIX}2",
            pickedUpAt = 1700000100000L,
            arrivedAtDropoffAt = null
        )
        val step3Stops = RouteStopCalculator.computeBatchStops(listOf(step3Order), 9.7410, 118.7360)
        var step3Advances = 0

        composeRule.setContent {
            DelivioRiderTheme {
                OrderDetailsSheet(
                    batchStops = step3Stops,
                    currentStop = step3Stops.first { !it.isFinished },
                    activeOrders = listOf(step3Order),
                    isOnline = true,
                    walletBalance = 0.0,
                    todayEarnings = 0.0,
                    todayTrips = 0,
                    currencySymbol = "₱",
                    supportPhone = null,
                    driverLocation = Pair(9.7500, 118.7580), // > 300m away from drop-off
                    isDriverLocationFresh = true,
                    sheetState = OrderSheetState.EXPANDED,
                    onGoOnlineClick = {},
                    onAdvanceStep = { _, _ -> step3Advances += 1 },
                    onReleaseOrder = {},
                    onReportProblem = {},
                    onCallClick = { _, _ -> }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Step 3 of 4 - Go to drop-off").assertIsDisplayed()
        composeRule.onNodeWithTag("taxi_stop_details_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("DROP-OFF").assertIsDisplayed()
        composeRule.onNodeWithText("Puerto Princesa Airport, Brgy San Miguel, Puerto Princesa").assertIsDisplayed()
        composeRule.onNodeWithText("Robinsons Place Palawan, San Miguel, Puerto Princesa").assertDoesNotExist()
        composeRule.onNodeWithText("At the main mall entrance").assertDoesNotExist()
        composeRule.onNodeWithText("Arrived at the Drop Off Area").assertIsDisplayed()

        // Pressing > 300m triggers drop-off confirm dialog
        composeRule.onNodeWithTag("taxi_press_action_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("You seem far from the drop-off point. Mark as arrived anyway?").assertIsDisplayed()
        composeRule.onNodeWithTag("far_arrival_yes_button").performClick()
        composeRule.waitForIdle()
        assertEquals(1, step3Advances)
    }

    @Test
    fun taxiStep4_showsSlideToConfirmDropOff() {
        val step4Order = sampleTaxiOrder(
            status = OrderStepConstants.STATUS_ON_TRIP,
            riderStep = "${OrderStepConstants.STEP_TO_STOP_PREFIX}2",
            pickedUpAt = 1700000100000L,
            arrivedAtDropoffAt = 1700000500000L
        )
        val step4Stops = RouteStopCalculator.computeBatchStops(listOf(step4Order), 9.7420, 118.7580)
        var completed = false

        composeRule.setContent {
            DelivioRiderTheme {
                OrderDetailsSheet(
                    batchStops = step4Stops,
                    currentStop = step4Stops.first { !it.isFinished },
                    activeOrders = listOf(step4Order),
                    isOnline = true,
                    walletBalance = 0.0,
                    todayEarnings = 0.0,
                    todayTrips = 0,
                    currencySymbol = "₱",
                    supportPhone = null,
                    sheetState = OrderSheetState.EXPANDED,
                    onGoOnlineClick = {},
                    onAdvanceStep = { _, _ -> completed = true },
                    onReleaseOrder = {},
                    onReportProblem = {},
                    onCallClick = { _, _ -> }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Step 4 of 4 - Drop off").assertIsDisplayed()
        composeRule.onNodeWithText("DROP-OFF").assertIsDisplayed()
        composeRule.onNodeWithText("Puerto Princesa Airport, Brgy San Miguel, Puerto Princesa").assertIsDisplayed()
        composeRule.onNodeWithTag("taxi_press_action_button").assertDoesNotExist()
        composeRule.onNodeWithTag("sheet_slide_button").assertIsDisplayed()
        composeRule.onNodeWithText("Slide to confirm Drop off").assertIsDisplayed()

        composeRule.onNodeWithTag("slide_confirm_thumb", useUnmergedTree = true).performTouchInput {
            swipeRight(startX = centerX, endX = centerX + 1500f, durationMillis = 300)
        }
        composeRule.waitForIdle()
        assertEquals(true, completed)
    }

    @Test
    fun foodAtMerchant_preservesTimelineItemsAndPickupChecklistDialog() {
        val foodOrder = Order(
            id = "ord_food_check",
            orderNumber = "5501",
            serviceType = OrderStepConstants.SERVICE_FOOD,
            merchantName = "Jollibee",
            branchName = "Rizal",
            merchantLat = 9.7400,
            merchantLng = 118.7350,
            customerName = "Maria Santos",
            customerLat = 9.7500,
            customerLng = 118.7360,
            riderStep = OrderStepConstants.STEP_AT_MERCHANT,
            items = listOf(OrderItem(name = "Chickenjoy", quantity = 1, price = 120.0))
        )
        val stops = RouteStopCalculator.computeBatchStops(listOf(foodOrder), 9.7400, 118.7350)

        composeRule.setContent {
            DelivioRiderTheme {
                OrderDetailsSheet(
                    batchStops = stops,
                    currentStop = stops.first(),
                    activeOrders = listOf(foodOrder),
                    isOnline = true,
                    walletBalance = 0.0,
                    todayEarnings = 0.0,
                    todayTrips = 0,
                    currencySymbol = "₱",
                    supportPhone = null,
                    sheetState = OrderSheetState.EXPANDED,
                    onGoOnlineClick = {},
                    onAdvanceStep = { _, _ -> },
                    onReleaseOrder = {},
                    onReportProblem = {},
                    onCallClick = { _, _ -> }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Stop 1 of 2 - Pick up").assertIsDisplayed()
        composeRule.onNodeWithTag("stop_timeline_list").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Items to Pick Up").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("slide_confirm_thumb", useUnmergedTree = true).performTouchInput {
            swipeRight(startX = centerX, endX = centerX + 1500f, durationMillis = 300)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Pick up Checklist").assertIsDisplayed()
    }

    @Test
    fun taxiExtraStop_showsGoToStopPressThenAtStopSlide() {
        val extraStops = listOf(TaxiStop(stopNumber = 3, address = "Final Destination Rd"))
        val toExtraOrder = sampleTaxiOrder(
            status = OrderStepConstants.STATUS_ON_TRIP,
            riderStep = "${OrderStepConstants.STEP_TO_STOP_PREFIX}2",
            pickedUpAt = 1700000100000L,
            stops = extraStops
        )
        val stops = RouteStopCalculator.computeBatchStops(listOf(toExtraOrder), 9.7410, 118.7360)
        var advances = 0

        composeRule.setContent {
            DelivioRiderTheme {
                OrderDetailsSheet(
                    batchStops = stops,
                    currentStop = stops.first { !it.isFinished },
                    activeOrders = listOf(toExtraOrder),
                    isOnline = true,
                    walletBalance = 0.0,
                    todayEarnings = 0.0,
                    todayTrips = 0,
                    currencySymbol = "₱",
                    supportPhone = null,
                    sheetState = OrderSheetState.EXPANDED,
                    onGoOnlineClick = {},
                    onAdvanceStep = { _, _ -> advances += 1 },
                    onReleaseOrder = {},
                    onReportProblem = {},
                    onCallClick = { _, _ -> }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Step 3 of 4 - Go to Stop 2").assertIsDisplayed()
        composeRule.onNodeWithText("Arrived at Stop 2").assertIsDisplayed()
        composeRule.onNodeWithTag("taxi_press_action_button").performClick()
        composeRule.waitForIdle()
        assertEquals(1, advances)
    }

    @Test
    fun pabiliStep1_showsBudgetChipItemsToPickUpPressButtonAndCustomerOnlyCall() {
        val pabiliOrder = samplePabiliOrder()
        val stops = RouteStopCalculator.computeBatchStops(listOf(pabiliOrder), 9.7392, 118.7353)
        var advances = 0
        var calledName = ""
        var calledPhone = ""

        composeRule.setContent {
            DelivioRiderTheme {
                OrderDetailsSheet(
                    batchStops = stops,
                    currentStop = stops.first { !it.isFinished },
                    activeOrders = listOf(pabiliOrder),
                    isOnline = true,
                    walletBalance = 0.0,
                    todayEarnings = 0.0,
                    todayTrips = 0,
                    currencySymbol = "₱",
                    supportPhone = null,
                    sheetState = OrderSheetState.EXPANDED,
                    onGoOnlineClick = {},
                    onAdvanceStep = { _, _ -> },
                    onAdvanceStepWithApproval = { _, _, _ -> advances += 1 },
                    onReleaseOrder = {},
                    onReportProblem = {},
                    onCallClick = { name, phone ->
                        calledName = name
                        calledPhone = phone
                    }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Budget limit ₱500").assertIsDisplayed()
        composeRule.onNodeWithTag("stop_timeline_list").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Items to Pick Up").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Collect ₱85 cash").performScrollTo().assertIsDisplayed()

        // Call button MUST target customer only, NEVER merchantPhone
        composeRule.onNodeWithTag("sheet_call_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals("Elena Cruz", calledName)
        assertEquals("09170001111", calledPhone)

        // Press button "Arrived at the Merchant" (not a slide button)
        composeRule.onNodeWithTag("sheet_slide_button").assertDoesNotExist()
        composeRule.onNodeWithTag("pabili_press_action_button").assertIsDisplayed()
        composeRule.onNodeWithText("Arrived at the Merchant").assertIsDisplayed()
        composeRule.onNodeWithTag("pabili_press_action_button").performClick()
        composeRule.waitForIdle()
        assertEquals(1, advances)
    }

    @Test
    fun pabiliStep2_checklistRequiresReceiptAndOverBudgetCustomerApproval() {
        val pabiliOrder = samplePabiliOrder(
            status = OrderStepConstants.STATUS_ARRIVED_AT_VENDOR,
            riderStep = OrderStepConstants.STEP_AT_VENDOR
        )
        val stops = RouteStopCalculator.computeBatchStops(listOf(pabiliOrder), 9.7400, 118.7350)
        var capturedReceipt: Double? = null
        var capturedApproved: Boolean? = null

        composeRule.setContent {
            DelivioRiderTheme {
                OrderDetailsSheet(
                    batchStops = stops,
                    currentStop = stops.first { !it.isFinished },
                    activeOrders = listOf(pabiliOrder),
                    isOnline = true,
                    walletBalance = 0.0,
                    todayEarnings = 0.0,
                    todayTrips = 0,
                    currencySymbol = "₱",
                    supportPhone = null,
                    sheetState = OrderSheetState.EXPANDED,
                    onGoOnlineClick = {},
                    onAdvanceStep = { _, _ -> },
                    onAdvanceStepWithApproval = { receipt, approved, _ ->
                        capturedReceipt = receipt
                        capturedApproved = approved
                    },
                    onReleaseOrder = {},
                    onReportProblem = {},
                    onCallClick = { _, _ -> }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Slide to confirm Picked Up").assertIsDisplayed()
        composeRule.onNodeWithTag("slide_confirm_thumb", useUnmergedTree = true).performTouchInput {
            swipeRight(startX = centerX, endX = centerX + 1500f, durationMillis = 300)
        }
        composeRule.waitForIdle()

        // Checklist dialog opens with Budget limit chip & required receipt total
        composeRule.onNodeWithText("Pick up Checklist").assertIsDisplayed()
        composeRule.onNodeWithTag("checklist_budget_limit_chip").assertIsDisplayed()
        composeRule.onNodeWithTag("checklist_confirm_button").assertIsNotEnabled()

        // 0 is invalid
        composeRule.onNodeWithTag("pabili_receipt_input").performTextInput("0")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("checklist_confirm_button").assertIsNotEnabled()

        // Enter over-budget amount 620 -> red notice & approval checkbox required
        composeRule.onNodeWithTag("pabili_receipt_input").performTextReplacement("620")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Over the customer's budget limit (₱500). Ask the customer first.").assertIsDisplayed()
        composeRule.onNodeWithTag("checklist_confirm_button").assertIsNotEnabled()

        // Tick "The customer approved the extra amount" -> confirm enables
        composeRule.onNodeWithTag("pabili_over_budget_checkbox_row").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("checklist_confirm_button").assertIsEnabled()

        composeRule.onNodeWithTag("checklist_confirm_button").performClick()
        composeRule.waitForIdle()
        assertEquals(620.0, capturedReceipt ?: 0.0, 0.001)
        assertEquals(true, capturedApproved)
    }

    @Test
    fun pabiliStep3_removesMerchantCardShowsDropoffOnlyPaidOnMerchantAndPressButton() {
        val pabiliOrder = samplePabiliOrder(
            status = OrderStepConstants.STATUS_RIDER_ON_WAY_RECIPIENT,
            riderStep = OrderStepConstants.STEP_TO_RECIPIENT,
            paidOnMerchant = 620.0,
            overBudgetApproved = true
        )
        val stops = RouteStopCalculator.computeBatchStops(listOf(pabiliOrder), 9.7400, 118.7350)
        var advances = 0

        composeRule.setContent {
            DelivioRiderTheme {
                OrderDetailsSheet(
                    batchStops = stops,
                    currentStop = stops.first { !it.isFinished },
                    activeOrders = listOf(pabiliOrder),
                    isOnline = true,
                    walletBalance = 0.0,
                    todayEarnings = 0.0,
                    todayTrips = 0,
                    currencySymbol = "₱",
                    supportPhone = null,
                    sheetState = OrderSheetState.EXPANDED,
                    onGoOnlineClick = {},
                    onAdvanceStep = { _, _ -> },
                    onAdvanceStepWithApproval = { _, _, _ -> advances += 1 },
                    onReleaseOrder = {},
                    onReportProblem = {},
                    onCallClick = { _, _ -> }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Budget limit ₱500").assertIsDisplayed()
        // Merchant pickup card removed; only DROP-OFF card with full revealed customer address shown
        composeRule.onNodeWithTag("stop_timeline_list").assertDoesNotExist()
        composeRule.onNodeWithText("Puregold Public Market").assertDoesNotExist()
        composeRule.onNodeWithTag("pabili_dropoff_details_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("45 Bonifacio St, San Pedro, Puerto Princesa").assertIsDisplayed()

        // Items to Deliver + bold Paid on Merchant row + fees-only Collect ₱85 cash
        composeRule.onNodeWithText("Items to Deliver").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Paid on Merchant ₱620").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Collect ₱85 cash").performScrollTo().assertIsDisplayed()

        // Press button "Arrived at the Customer"
        composeRule.onNodeWithTag("sheet_slide_button").assertDoesNotExist()
        composeRule.onNodeWithTag("pabili_press_action_button").assertIsDisplayed()
        composeRule.onNodeWithText("Arrived at the Customer").assertIsDisplayed()
        composeRule.onNodeWithTag("pabili_press_action_button").performClick()
        composeRule.waitForIdle()
        assertEquals(1, advances)
    }

    @Test
    fun pabiliStep4_slidesToConfirmDeliveryAndCodDialogShowsPaidOnMerchantSeparately() {
        val pabiliOrder = samplePabiliOrder(
            status = OrderStepConstants.STATUS_ARRIVED_AT_RECIPIENT,
            riderStep = OrderStepConstants.STEP_AT_RECIPIENT,
            paidOnMerchant = 620.0,
            overBudgetApproved = true
        )
        val stops = RouteStopCalculator.computeBatchStops(listOf(pabiliOrder), 9.7520, 118.7420)
        var completed = false

        composeRule.setContent {
            DelivioRiderTheme {
                OrderDetailsSheet(
                    batchStops = stops,
                    currentStop = stops.first { !it.isFinished },
                    activeOrders = listOf(pabiliOrder),
                    isOnline = true,
                    walletBalance = 0.0,
                    todayEarnings = 0.0,
                    todayTrips = 0,
                    currencySymbol = "₱",
                    supportPhone = null,
                    sheetState = OrderSheetState.EXPANDED,
                    onGoOnlineClick = {},
                    onAdvanceStep = { _, _ -> },
                    onAdvanceStepWithApproval = { _, _, _ -> completed = true },
                    onReleaseOrder = {},
                    onReportProblem = {},
                    onCallClick = { _, _ -> }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Budget limit ₱500").assertIsDisplayed()
        composeRule.onNodeWithTag("pabili_press_action_button").assertDoesNotExist()
        composeRule.onNodeWithTag("sheet_slide_button").assertIsDisplayed()
        composeRule.onNodeWithText("Slide to Confirm Delivery").assertIsDisplayed()

        composeRule.onNodeWithTag("slide_confirm_thumb", useUnmergedTree = true).performTouchInput {
            swipeRight(startX = centerX, endX = centerX + 1500f, durationMillis = 300)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("COD Cash Collection").assertIsDisplayed()
        composeRule.onNodeWithText("₱85.00").assertIsDisplayed()
        composeRule.onNodeWithText("I have received the exact cash amount (₱85.00)").assertIsDisplayed()
        composeRule.onNodeWithText("Paid on Merchant ₱620.00").assertIsDisplayed()
        composeRule.onNodeWithText("Items you paid at the store. Not part of the cash to collect.").assertIsDisplayed()
        composeRule.onNodeWithTag("cod_complete_delivery_button").assertIsNotEnabled()

        composeRule.onNodeWithTag("cod_confirm_checkbox_row").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("cod_complete_delivery_button").assertIsEnabled()
        composeRule.onNodeWithTag("cod_complete_delivery_button").performClick()
        composeRule.waitForIdle()
        assertTrue(completed)
    }
}

