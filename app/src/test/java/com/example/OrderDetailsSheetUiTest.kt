package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import com.example.data.constants.OrderStepConstants
import com.example.data.models.AcceptOrderDecision
import com.example.data.models.AcceptOrderRules
import com.example.data.models.FoodFlowRules
import com.example.data.models.FoodStepStage
import com.example.data.models.Order
import com.example.data.models.OrderDocumentParser
import com.example.data.models.OrderItem
import com.example.data.models.RouteStopCalculator
import com.example.data.models.TaxiStop
import com.example.data.models.contactsOf
import com.example.data.models.deduplicateAddress
import com.example.ui.components.IncomingOrderDialog
import com.example.ui.components.OrderDetailsSheet
import com.example.ui.components.OrderSheetState
import com.example.ui.theme.DelivioRiderTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
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
        composeRule.onNodeWithTag("navigate_stop_button").assertDoesNotExist()
        composeRule.onNodeWithTag("inline_navigate_button").performScrollTo().assertIsDisplayed()

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

        // Call, Chat, More (Help removed from quick action row)
        composeRule.onNodeWithTag("quick_actions_row").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("sheet_call_button").assertIsDisplayed()
        composeRule.onNodeWithText("Call").assertIsDisplayed()
        composeRule.onNodeWithTag("customer_chat_button").assertIsDisplayed()
        composeRule.onNodeWithText("Chat").assertIsDisplayed()
        composeRule.onNodeWithTag("sheet_more_button").assertIsDisplayed()
        composeRule.onNodeWithText("More").assertIsDisplayed()
        composeRule.onNodeWithTag("sheet_headset_button").assertDoesNotExist()

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
        composeRule.onNodeWithTag("trip_details_chip").assertIsDisplayed()
        composeRule.onNodeWithTag("food_stop_details_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("sheet_order_number_chip").assertIsDisplayed()
        composeRule.onNodeWithText("Maria Santos").assertDoesNotExist()
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

    private fun sampleParcelOrder(
        status: String = OrderStepConstants.STATUS_RIDER_ON_WAY_SENDER,
        riderStep: String = OrderStepConstants.STEP_TO_SENDER,
        vehicleType: String = OrderStepConstants.VEHICLE_SEDAN,
        feePayer: String = "sender",
        paymentMethod: String = "cod",
        cashToCollect: Double = 120.0
    ) = Order(
        id = "ord_parcel_1",
        orderNumber = "P902",
        serviceType = OrderStepConstants.SERVICE_PARCEL,
        status = status,
        riderStep = riderStep,
        vehicleType = vehicleType,
        senderName = "Carlos Mendoza",
        merchantName = "Carlos Mendoza",
        merchantPhone = "09171110001",
        merchantAddress = "14 Rizal Ave, Brgy Tanglaw, Brgy Tanglaw, Puerto Princesa",
        merchantLat = 9.7405,
        merchantLng = 118.7351,
        pickupNote = "Call when at the gate",
        customerName = "Lucia Santos",
        customerPhone = "09182220002",
        customerAddress = "99 Valencia St, Brgy San Pedro, Puerto Princesa",
        customerArea = "San Pedro",
        customerLat = 9.7510,
        customerLng = 118.7425,
        customerNotes = "Hand over to front desk guard",
        parcelSize = "Medium",
        parcelWeightKg = 4.5,
        parcelItemDescription = "Framed painting and books",
        parcelFragile = true,
        parcelFeePayer = feePayer,
        cashCollectAt = feePayer,
        paymentMethod = paymentMethod,
        cashToCollect = cashToCollect,
        riderFeeEstimate = 110.0
    )

    @Test
    fun parcelStep1_showsPickupOnlyCompactCardSenderPaymentInlineNavigateAndPressButton() {
        val order = sampleParcelOrder(feePayer = "sender", paymentMethod = "cod", cashToCollect = 120.0)
        val stops = RouteStopCalculator.computeBatchStops(listOf(order), 9.7392, 118.7353)
        var advances = 0
        var calledName = ""
        var calledPhone = ""

        composeRule.setContent {
            DelivioRiderTheme {
                OrderDetailsSheet(
                    batchStops = stops,
                    currentStop = stops.first { !it.isFinished },
                    activeOrders = listOf(order),
                    isOnline = true,
                    walletBalance = 0.0,
                    todayEarnings = 0.0,
                    todayTrips = 0,
                    currencySymbol = "₱",
                    supportPhone = null,
                    routeDistanceKm = 1.6,
                    routeEtaMinutes = 7,
                    sheetState = OrderSheetState.EXPANDED,
                    onGoOnlineClick = {},
                    onAdvanceStep = { _, _ -> advances += 1 },
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

        // Step chip is clickable
        composeRule.onNodeWithTag("trip_details_chip").assertIsDisplayed()
        composeRule.onNodeWithText("Stop 1 of 2 - Pick up").assertIsDisplayed()

        // Standalone top Navigate button is hidden; inline Navigate sits beside Help
        composeRule.onNodeWithTag("navigate_stop_button").assertDoesNotExist()
        composeRule.onNodeWithTag("inline_navigate_button").performScrollTo().assertIsDisplayed()

        // Pickup-only card with deduplicated full address & pickup note (no dropoff address or items list)
        composeRule.onNodeWithText("No item list provided").assertDoesNotExist()
        composeRule.onNodeWithTag("parcel_stop_details_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("PICKUP").assertIsDisplayed()
        composeRule.onNodeWithText("14 Rizal Ave, Brgy Tanglaw, Puerto Princesa").assertIsDisplayed()
        composeRule.onNodeWithText("Call when at the gate").assertIsDisplayed()
        composeRule.onNodeWithText("99 Valencia St, Brgy San Pedro, Puerto Princesa").assertDoesNotExist()

        // Compact Parcel Info Card
        composeRule.onNodeWithTag("parcel_info_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Medium").assertIsDisplayed()
        composeRule.onNodeWithText("4.5 kg").assertIsDisplayed()
        composeRule.onNodeWithTag("parcel_fragile_badge").assertIsDisplayed()
        composeRule.onNodeWithText("Framed painting and books").assertIsDisplayed()

        // Payment line for sender COD at pickup
        composeRule.onNodeWithText("Collect ₱120 cash from the sender").performScrollTo().assertIsDisplayed()

        // Call dials pickup.phone
        composeRule.onNodeWithTag("sheet_call_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals("Carlos Mendoza", calledName)
        assertEquals("09171110001", calledPhone)

        // Full-width yellow press button "Arrived at the Sender"
        composeRule.onNodeWithTag("sheet_slide_button").assertDoesNotExist()
        composeRule.onNodeWithTag("parcel_press_action_button").assertIsDisplayed()
        composeRule.onNodeWithText("Arrived at the Sender").assertIsDisplayed()
        composeRule.onNodeWithTag("parcel_press_action_button").performClick()
        composeRule.waitForIdle()
        assertEquals(1, advances)
    }

    @Test
    fun parcelStep2_slideOpensSenderCashDialogWithoutPickupChecklist() {
        val senderPaysOrder = sampleParcelOrder(
            status = OrderStepConstants.STATUS_ARRIVED_AT_SENDER,
            riderStep = OrderStepConstants.STEP_AT_SENDER,
            feePayer = "sender",
            paymentMethod = "cod",
            cashToCollect = 120.0
        )
        val stops = RouteStopCalculator.computeBatchStops(listOf(senderPaysOrder), 9.7405, 118.7351)
        var pickedUpConfirmed = false

        composeRule.setContent {
            DelivioRiderTheme {
                OrderDetailsSheet(
                    batchStops = stops,
                    currentStop = stops.first { !it.isFinished },
                    activeOrders = listOf(senderPaysOrder),
                    isOnline = true,
                    walletBalance = 0.0,
                    todayEarnings = 0.0,
                    todayTrips = 0,
                    currencySymbol = "₱",
                    supportPhone = null,
                    sheetState = OrderSheetState.EXPANDED,
                    onGoOnlineClick = {},
                    onAdvanceStep = { _, _ -> pickedUpConfirmed = true },
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

        // No Pick up Checklist dialog; ParcelCashDialog opens requiring checkbox
        composeRule.onNodeWithText("Pick up Checklist").assertDoesNotExist()
        composeRule.onNodeWithTag("parcel_cash_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("COD Cash Collection").assertIsDisplayed()
        composeRule.onNodeWithText("₱120.00").assertIsDisplayed()
        composeRule.onNodeWithTag("parcel_cash_confirm_button").assertIsNotEnabled()

        composeRule.onNodeWithTag("cod_confirm_checkbox_row").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("parcel_cash_confirm_button").assertIsEnabled()
        composeRule.onNodeWithTag("parcel_cash_confirm_button").performClick()
        composeRule.waitForIdle()
        assertTrue(pickedUpConfirmed)
    }

    @Test
    fun parcelStep2_recipientPaysShowsNoCashCollectedFromSender() {
        val recipientPaysOrder = sampleParcelOrder(
            status = OrderStepConstants.STATUS_ARRIVED_AT_SENDER,
            riderStep = OrderStepConstants.STEP_AT_SENDER,
            feePayer = "recipient",
            paymentMethod = "cod",
            cashToCollect = 120.0
        )
        val stops = RouteStopCalculator.computeBatchStops(listOf(recipientPaysOrder), 9.7405, 118.7351)
        var pickedUpConfirmed = false

        composeRule.setContent {
            DelivioRiderTheme {
                OrderDetailsSheet(
                    batchStops = stops,
                    currentStop = stops.first { !it.isFinished },
                    activeOrders = listOf(recipientPaysOrder),
                    isOnline = true,
                    walletBalance = 0.0,
                    todayEarnings = 0.0,
                    todayTrips = 0,
                    currencySymbol = "₱",
                    supportPhone = null,
                    sheetState = OrderSheetState.EXPANDED,
                    onGoOnlineClick = {},
                    onAdvanceStep = { _, _ -> pickedUpConfirmed = true },
                    onReleaseOrder = {},
                    onReportProblem = {},
                    onCallClick = { _, _ -> }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("No cash to collect").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("slide_confirm_thumb", useUnmergedTree = true).performTouchInput {
            swipeRight(startX = centerX, endX = centerX + 1500f, durationMillis = 300)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("No Cash Collected").assertIsDisplayed()
        composeRule.onNodeWithText("No cash to collect from the sender").assertIsDisplayed()
        composeRule.onNodeWithTag("parcel_cash_confirm_button").assertIsEnabled()
        composeRule.onNodeWithTag("parcel_cash_confirm_button").performClick()
        composeRule.waitForIdle()
        assertTrue(pickedUpConfirmed)
    }

    @Test
    fun parcelStep3AndStep4_showDropoffOnlyRecipientCallPressAndCashDialogs() {
        // Step 3: recipient pays COD -> shows recipient drop-off details, dropoff Call, "Arrived at the Recipient"
        val step3Order = sampleParcelOrder(
            status = OrderStepConstants.STATUS_RIDER_ON_WAY_RECIPIENT,
            riderStep = OrderStepConstants.STEP_TO_RECIPIENT,
            feePayer = "recipient",
            paymentMethod = "cod",
            cashToCollect = 120.0
        )
        val step3Stops = RouteStopCalculator.computeBatchStops(listOf(step3Order), 9.7405, 118.7351)
        var step3Advanced = false
        var calledName = ""
        var calledPhone = ""

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
                    sheetState = OrderSheetState.EXPANDED,
                    onGoOnlineClick = {},
                    onAdvanceStep = { _, _ -> step3Advanced = true },
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

        composeRule.onNodeWithText("Stop 2 of 2 - Drop off").assertIsDisplayed()
        composeRule.onNodeWithText("DROP-OFF").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("99 Valencia St, Brgy San Pedro, Puerto Princesa").assertIsDisplayed()
        composeRule.onNodeWithText("Hand over to front desk guard").assertIsDisplayed()
        composeRule.onNodeWithText("14 Rizal Ave, Brgy Tanglaw, Puerto Princesa").assertDoesNotExist()
        composeRule.onNodeWithText("Collect ₱120 cash from the recipient").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("sheet_call_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals("Lucia Santos", calledName)
        assertEquals("09182220002", calledPhone)

        composeRule.onNodeWithText("Arrived at the Recipient").assertIsDisplayed()
        composeRule.onNodeWithTag("parcel_press_action_button").performClick()
        composeRule.waitForIdle()
        assertTrue(step3Advanced)
    }

    @Test
    fun parcelStep4_walletOrSenderPaysShowsNoCashCollectedFromRecipient() {
        val step4Order = sampleParcelOrder(
            status = OrderStepConstants.STATUS_ARRIVED_AT_RECIPIENT,
            riderStep = OrderStepConstants.STEP_AT_RECIPIENT,
            feePayer = "sender",
            paymentMethod = "cod",
            cashToCollect = 120.0
        )
        val stops = RouteStopCalculator.computeBatchStops(listOf(step4Order), 9.7510, 118.7425)
        var delivered = false

        composeRule.setContent {
            DelivioRiderTheme {
                OrderDetailsSheet(
                    batchStops = stops,
                    currentStop = stops.first { !it.isFinished },
                    activeOrders = listOf(step4Order),
                    isOnline = true,
                    walletBalance = 0.0,
                    todayEarnings = 0.0,
                    todayTrips = 0,
                    currencySymbol = "₱",
                    supportPhone = null,
                    sheetState = OrderSheetState.EXPANDED,
                    onGoOnlineClick = {},
                    onAdvanceStep = { _, _ -> delivered = true },
                    onReleaseOrder = {},
                    onReportProblem = {},
                    onCallClick = { _, _ -> }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("No cash to collect").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Slide to Confirm Delivery").assertIsDisplayed()
        composeRule.onNodeWithTag("slide_confirm_thumb", useUnmergedTree = true).performTouchInput {
            swipeRight(startX = centerX, endX = centerX + 1500f, durationMillis = 300)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("No Cash Collected").assertIsDisplayed()
        composeRule.onNodeWithText("No cash to collect from the recipient").assertIsDisplayed()
        composeRule.onNodeWithText("Confirm Delivery").assertIsDisplayed()
        composeRule.onNodeWithTag("parcel_cash_confirm_button").performClick()
        composeRule.waitForIdle()
        assertTrue(delivered)
    }

    @Test
    fun parcelTripDetailsPage_opensFromStepChipShowsFourCardsAndDismissesOnBack() {
        val order = sampleParcelOrder(
            vehicleType = OrderStepConstants.VEHICLE_SEDAN,
            feePayer = "recipient",
            paymentMethod = "cod",
            cashToCollect = 180.0
        )
        val stops = RouteStopCalculator.computeBatchStops(listOf(order), 9.7392, 118.7353)
        var calledPhone = ""

        composeRule.setContent {
            DelivioRiderTheme {
                OrderDetailsSheet(
                    batchStops = stops,
                    currentStop = stops.first { !it.isFinished },
                    activeOrders = listOf(order),
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
                    onCallClick = { _, phone -> calledPhone = phone }
                )
            }
        }
        composeRule.waitForIdle()

        // Tap clickable step chip
        composeRule.onNodeWithTag("trip_details_chip").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("trip_details_page").assertIsDisplayed()
        composeRule.onNodeWithText("Trip details").assertIsDisplayed()
        composeRule.onNodeWithTag("trip_details_order_number").assertIsDisplayed()

        // 4 Cards: PICKUP, DROP-OFF, PARCEL, PAYMENT
        composeRule.onNodeWithTag("trip_details_pickup_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("trip_details_dropoff_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("trip_details_parcel_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Sedan").assertIsDisplayed()
        composeRule.onNodeWithTag("trip_details_payment_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Recipient").assertIsDisplayed()
        composeRule.onNodeWithText("₱180").assertIsDisplayed()

        // Call drop-off button dials recipient phone
        composeRule.onNodeWithTag("trip_details_dropoff_call_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals("09182220002", calledPhone)

        // Back button closes Trip details page
        composeRule.onNodeWithTag("trip_details_back_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("trip_details_page").assertDoesNotExist()
    }

    @Test
    fun parcelCollapsedState_showsOnlyStopPersonName() {
        val order = sampleParcelOrder()
        val stops = RouteStopCalculator.computeBatchStops(listOf(order), 9.7392, 118.7353)
        var currentState = OrderSheetState.COLLAPSED

        composeRule.setContent {
            DelivioRiderTheme {
                OrderDetailsSheet(
                    batchStops = stops,
                    currentStop = stops.first { !it.isFinished },
                    activeOrders = listOf(order),
                    isOnline = true,
                    walletBalance = 0.0,
                    todayEarnings = 0.0,
                    todayTrips = 0,
                    currencySymbol = "₱",
                    supportPhone = null,
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
        composeRule.onNodeWithText("Carlos Mendoza").assertIsDisplayed()
        composeRule.onNodeWithTag("parcel_press_action_button").assertDoesNotExist()
        composeRule.onNodeWithTag("inline_navigate_button").assertDoesNotExist()
        composeRule.onNodeWithTag("trip_details_chip").assertDoesNotExist()

        composeRule.onNodeWithTag("collapsed_trip_bar").performClick()
        composeRule.waitForIdle()
        assertEquals(OrderSheetState.EXPANDED, currentState)
    }

    private fun sampleFoodOrder(
        serviceType: String = OrderStepConstants.SERVICE_FOOD,
        status: String = OrderStepConstants.STATUS_PREPARING,
        riderStep: String = OrderStepConstants.STEP_TO_MERCHANT,
        paymentMethod: String = "cod",
        cashToCollect: Double = 245.0
    ) = Order(
        id = "ord_food_1",
        orderNumber = "F4410",
        serviceType = serviceType,
        status = status,
        riderStep = riderStep,
        merchantName = "Mang Inasal",
        branchName = "Robinsons",
        merchantPhone = "09171001111",
        merchantAddress = "Robinsons Place, Robinsons Place, San Miguel, Puerto Princesa, Puerto Princesa City",
        merchantLat = 9.7400,
        merchantLng = 118.7350,
        customerName = "Maria Santos",
        customerPhone = "09189990000",
        customerAddress = "88 Malvar St, San Miguel, Puerto Princesa",
        customerArea = "San Miguel",
        customerLat = 9.7510,
        customerLng = 118.7410,
        customerNotes = "Leave at lobby reception",
        paymentMethod = paymentMethod,
        cashToCollect = cashToCollect,
        riderFeeEstimate = 65.0,
        items = listOf(OrderItem(name = "PM1 Pecho", quantity = 1, price = 180.0))
    )

    @Test
    fun foodStep1_showsMerchantOnlyAddressOrderChipCallAndPressButton() {
        val foodOrder = sampleFoodOrder(riderStep = OrderStepConstants.STEP_TO_MERCHANT)
        val stops = RouteStopCalculator.computeBatchStops(listOf(foodOrder), 9.7392, 118.7353)
        var advances = 0
        var calledName = ""
        var calledPhone = ""

        composeRule.setContent {
            DelivioRiderTheme {
                OrderDetailsSheet(
                    batchStops = stops,
                    currentStop = stops.first { !it.isFinished },
                    activeOrders = listOf(foodOrder),
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
                    onCallClick = { name, phone ->
                        calledName = name
                        calledPhone = phone
                    }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("trip_details_chip").assertIsDisplayed()
        composeRule.onNodeWithText("Stop 1 of 2 - Pick up").assertIsDisplayed()
        composeRule.onNodeWithTag("navigate_stop_button").assertDoesNotExist()
        composeRule.onNodeWithTag("inline_navigate_button").performScrollTo().assertIsDisplayed()

        // Merchant-only details with deduplicated full address & clickable order number chip
        composeRule.onNodeWithTag("food_stop_details_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("sheet_order_number_chip").assertIsDisplayed()
        composeRule.onNodeWithText("Robinsons Place, San Miguel, Puerto Princesa").assertIsDisplayed()
        composeRule.onNodeWithText("Maria Santos").assertDoesNotExist()
        composeRule.onNodeWithText("88 Malvar St, San Miguel, Puerto Princesa").assertDoesNotExist()
        composeRule.onNodeWithText("Leave at lobby reception").assertDoesNotExist()

        // Call dials merchant phone only
        composeRule.onNodeWithTag("sheet_call_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals("Mang Inasal - Robinsons", calledName)
        assertEquals("09171001111", calledPhone)

        // Press button "Arrived at the Merchant"
        composeRule.onNodeWithTag("sheet_slide_button").assertDoesNotExist()
        composeRule.onNodeWithTag("food_press_action_button").assertIsDisplayed()
        composeRule.onNodeWithText("Arrived at the Merchant").assertIsDisplayed()
        composeRule.onNodeWithTag("food_press_action_button").performClick()
        composeRule.waitForIdle()
        assertEquals(1, advances)
    }

    @Test
    fun foodStep3AndStep4_showCustomerOnlyFullAddressPressAndCodDeliverySlide() {
        val step3Order = sampleFoodOrder(
            serviceType = OrderStepConstants.SERVICE_REQUEST,
            status = OrderStepConstants.REQ_ON_THE_WAY,
            riderStep = OrderStepConstants.STEP_TO_CUSTOMER
        )
        val step3Stops = RouteStopCalculator.computeBatchStops(listOf(step3Order), 9.7400, 118.7350)
        var step3Advances = 0
        var calledPhone = ""

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
                    sheetState = OrderSheetState.EXPANDED,
                    onGoOnlineClick = {},
                    onAdvanceStep = { _, _ -> step3Advances += 1 },
                    onReleaseOrder = {},
                    onReportProblem = {},
                    onCallClick = { _, phone -> calledPhone = phone }
                )
            }
        }
        composeRule.waitForIdle()

        // Customer-only card with revealed full address + customer note; NO merchant card
        composeRule.onNodeWithText("Stop 2 of 2 - Drop off").assertIsDisplayed()
        composeRule.onNodeWithText("Mang Inasal - Robinsons").assertDoesNotExist()
        composeRule.onNodeWithText("88 Malvar St, San Miguel, Puerto Princesa").assertIsDisplayed()
        composeRule.onNodeWithText("Leave at lobby reception").assertIsDisplayed()

        composeRule.onNodeWithTag("sheet_call_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals("09189990000", calledPhone)

        // Step 3 uses press button "Arrived at the Customer"
        composeRule.onNodeWithTag("food_press_action_button").assertIsDisplayed()
        composeRule.onNodeWithText("Arrived at the Customer").assertIsDisplayed()
        composeRule.onNodeWithTag("food_press_action_button").performClick()
        composeRule.waitForIdle()
        assertEquals(1, step3Advances)
    }

    @Test
    fun foodStep4_usesSlideToConfirmDeliveryAndOpensCodDialog() {
        val step4Order = sampleFoodOrder(
            serviceType = OrderStepConstants.SERVICE_MART,
            status = OrderStepConstants.REQ_ON_THE_WAY,
            riderStep = OrderStepConstants.STEP_AT_CUSTOMER,
            paymentMethod = "cod",
            cashToCollect = 245.0
        )
        val stops = RouteStopCalculator.computeBatchStops(listOf(step4Order), 9.7510, 118.7410)
        var delivered = false

        composeRule.setContent {
            DelivioRiderTheme {
                OrderDetailsSheet(
                    batchStops = stops,
                    currentStop = stops.first { !it.isFinished },
                    activeOrders = listOf(step4Order),
                    isOnline = true,
                    walletBalance = 0.0,
                    todayEarnings = 0.0,
                    todayTrips = 0,
                    currencySymbol = "₱",
                    supportPhone = null,
                    sheetState = OrderSheetState.EXPANDED,
                    onGoOnlineClick = {},
                    onAdvanceStep = { _, _ -> delivered = true },
                    onReleaseOrder = {},
                    onReportProblem = {},
                    onCallClick = { _, _ -> }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("food_press_action_button").assertDoesNotExist()
        composeRule.onNodeWithText("Slide to Confirm Delivery").assertIsDisplayed()
        composeRule.onNodeWithTag("slide_confirm_thumb", useUnmergedTree = true).performTouchInput {
            swipeRight(startX = centerX, endX = centerX + 1500f, durationMillis = 300)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("COD Cash Collection").assertIsDisplayed()
        composeRule.onNodeWithTag("cod_confirm_checkbox_row").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("cod_complete_delivery_button").performClick()
        composeRule.waitForIdle()
        assertTrue(delivered)
    }

    @Test
    fun universalTripDetailsDialog_opensForFoodPabiliTaxiAndTogglesRawDataOnLongPress() {
        // 1. Pabili Trip details: VENDOR has NO call button; CUSTOMER has call button & masked address before pickup; long-press reveals Raw data
        val pabili = samplePabiliOrder().copy(
            rawDebugFields = mapOf(
                "status" to OrderStepConstants.STATUS_RIDER_ON_WAY_VENDOR,
                "riderStep" to OrderStepConstants.STEP_TO_VENDOR,
                "serviceType" to "pabili"
            )
        )
        val pabiliStops = RouteStopCalculator.computeBatchStops(listOf(pabili), 9.7392, 118.7353)

        composeRule.setContent {
            DelivioRiderTheme {
                OrderDetailsSheet(
                    batchStops = pabiliStops,
                    currentStop = pabiliStops.first { !it.isFinished },
                    activeOrders = listOf(pabili),
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

        composeRule.onNodeWithTag("trip_details_chip").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("trip_details_page").assertIsDisplayed()
        composeRule.onNodeWithTag("trip_details_vendor_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("trip_details_vendor_call_button").assertDoesNotExist()
        composeRule.onNodeWithTag("trip_details_customer_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("trip_details_customer_call_button").assertIsDisplayed()
        composeRule.onNode(
            hasText("San Pedro (Full address revealed after pickup)") and hasAnyAncestor(hasTestTag("trip_details_customer_card"))
        ).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("trip_details_order_card").performScrollTo().assertIsDisplayed()

        // Raw data hidden until long-press on order number
        composeRule.onNodeWithTag("trip_details_raw_data_card").assertDoesNotExist()
        composeRule.onNodeWithTag("trip_details_order_number").performTouchInput {
            longClick()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("trip_details_raw_data_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Raw data").assertIsDisplayed()

        // Long-press again hides Raw data
        composeRule.onNodeWithTag("trip_details_order_number").performTouchInput {
            longClick()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("trip_details_raw_data_card").assertDoesNotExist()
    }

    @Test
    fun taxiTripDetailsDialog_opensFromStepChipAndShowsPickupDropoffTripCards() {
        val taxi = sampleTaxiOrder()
        val stops = RouteStopCalculator.computeBatchStops(listOf(taxi), 9.7392, 118.7353)
        var calledPhone = ""

        composeRule.setContent {
            DelivioRiderTheme {
                OrderDetailsSheet(
                    batchStops = stops,
                    currentStop = stops.first(),
                    activeOrders = listOf(taxi),
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
                    onCallClick = { _, phone -> calledPhone = phone }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("trip_details_chip").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("trip_details_page").assertIsDisplayed()
        composeRule.onNodeWithTag("trip_details_pickup_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("trip_details_dropoff_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("trip_details_trip_card").performScrollTo().assertIsDisplayed()
        composeRule.onNode(
            hasText("2 passengers") and hasAnyAncestor(hasTestTag("trip_details_trip_card"))
        ).performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("trip_details_pickup_call_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals("09171234567", calledPhone)
    }

    @Test
    fun incomingOrderDialog_disablesAcceptButtonOnFirstTapAndPreventsDuplicateAccept() {
        val order = sampleFoodOrder()
        var acceptCount = 0

        composeRule.setContent {
            DelivioRiderTheme {
                IncomingOrderDialog(
                    order = order,
                    currencySymbol = "₱",
                    soundEnabled = false,
                    vibrationEnabled = false,
                    onAccept = { acceptCount += 1 },
                    onDecline = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("accept_offer_button").assertIsEnabled()
        composeRule.onNodeWithTag("accept_offer_button").performClick()
        composeRule.waitForIdle()

        assertEquals(1, acceptCount)
        composeRule.onNodeWithTag("accept_offer_button").assertIsNotEnabled()
        composeRule.onNodeWithText("Accepting...").assertIsDisplayed()
    }

    @Test
    fun acceptOrderRules_foodFlowRules_deduplicateAddress_and_parcelContactsIsolation() {
        // 1. AcceptOrderRules handles ALREADY_OURS, ALLOW_ACCEPT, TAKEN_BY_OTHER, missing status != "unknown"
        val ours = AcceptOrderRules.evaluateDocument(
            exists = true,
            riderIdRaw = "rider_me",
            statusRaw = "Rider is on the way",
            riderRequestStatusRaw = "accepted",
            serviceTypeRaw = "food",
            excludeRiderIds = emptyList(),
            myUid = "rider_me"
        )
        assertEquals(AcceptOrderDecision.ALREADY_OURS, ours.decision)

        val other = AcceptOrderRules.evaluateDocument(
            exists = true,
            riderIdRaw = "rider_other",
            statusRaw = "Preparing",
            riderRequestStatusRaw = null,
            serviceTypeRaw = "food",
            excludeRiderIds = emptyList(),
            myUid = "rider_me"
        )
        assertEquals(AcceptOrderDecision.TAKEN_BY_OTHER, other.decision)

        val missing = AcceptOrderRules.evaluateDocument(
            exists = true,
            riderIdRaw = null,
            statusRaw = null,
            riderRequestStatusRaw = null,
            serviceTypeRaw = "parcel",
            excludeRiderIds = emptyList(),
            myUid = "rider_me"
        )
        assertEquals(AcceptOrderDecision.UNAVAILABLE, missing.decision)
        assertEquals("(missing)", missing.rawStatus)

        val validReady = AcceptOrderRules.evaluateDocument(
            exists = true,
            riderIdRaw = "",
            statusRaw = "Ready",
            riderRequestStatusRaw = "pending",
            serviceTypeRaw = "food",
            excludeRiderIds = emptyList(),
            myUid = "rider_me"
        )
        assertEquals(AcceptOrderDecision.ALLOW_ACCEPT, validReady.decision)

        // 2. FoodFlowRules forward-only & skip checks
        assertTrue(FoodFlowRules.isExpectedState(OrderStepConstants.STEP_TO_MERCHANT, "Preparing", FoodStepStage.TO_MERCHANT))
        assertFalse(FoodFlowRules.isExpectedState(OrderStepConstants.STEP_AT_MERCHANT, "Preparing", FoodStepStage.TO_MERCHANT))
        assertFalse(FoodFlowRules.isExpectedState(OrderStepConstants.STEP_AT_CUSTOMER, "Delivered", FoodStepStage.AT_CUSTOMER))

        // 3. deduplicateAddress removes consecutive multi-word repeats and Puerto Princesa / City duplicate
        assertEquals(
            "San Pedro, Puerto Princesa",
            deduplicateAddress("San Pedro, San Pedro, Puerto Princesa, Puerto Princesa City")
        )
        assertEquals(
            "Robinsons Place Mall, San Miguel, Puerto Princesa",
            deduplicateAddress("Robinsons Place Mall Robinsons Place Mall, San Miguel, Puerto Princesa")
        )

        // 4. Parcel contactsOf isolation: never leaks booking customer ("Test C") into recipient drop-off
        val parcelDocs = OrderDocumentParser.parseOrderMapsIndividually(
            docs = listOf(
                "parcel_iso_1" to mapOf(
                    "orderNumber" to "P-555",
                    "serviceType" to "parcel",
                    "status" to "Placed Order",
                    "customerName" to "Test C",
                    "customerPhone" to "09170009999",
                    "customerAddress" to "Wrong Booking Address",
                    "pickup" to mapOf(
                        "name" to "Alice Sender",
                        "phone" to "09171112222",
                        "address" to "10 Main St, Puerto Princesa, Puerto Princesa City"
                    ),
                    "dropoff" to mapOf(
                        "address" to "20 Second St, San Pedro"
                    )
                )
            ),
            sourceLabel = "test"
        )
        val parsedParcel = parcelDocs.first()
        val contacts = contactsOf(parsedParcel)
        assertEquals("Alice Sender", contacts.pickup.name)
        assertEquals("09171112222", contacts.pickup.phone)
        assertEquals("10 Main St, Puerto Princesa", contacts.pickup.address)
        assertEquals("Not provided", contacts.dropoff.name)
        assertEquals("Not provided", contacts.dropoff.phone)
        assertEquals("20 Second St, San Pedro", contacts.dropoff.address)
    }

    @Test
    fun releaseOrderDialog_supportsCrCancellationAndCloseRestaurantForFoodBeforePickup() {
        val foodOrder = sampleFoodOrder(riderStep = OrderStepConstants.STEP_TO_MERCHANT)
        val stops = RouteStopCalculator.computeBatchStops(listOf(foodOrder), 9.7392, 118.7353)
        var capturedOrderId = ""
        var capturedType = ""
        var capturedReason = ""

        composeRule.setContent {
            DelivioRiderTheme {
                OrderDetailsSheet(
                    batchStops = stops,
                    currentStop = stops.first { !it.isFinished },
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
                    onReleaseOrderWithType = { id, type, reason ->
                        capturedOrderId = id
                        capturedType = type
                        capturedReason = reason
                    },
                    onReportProblem = {},
                    onCallClick = { _, _ -> }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("release_order_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("release_order_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("release_type_cr_cancellation").assertIsDisplayed()
        composeRule.onNodeWithTag("release_type_close_restaurant").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("release_reason_input").performTextInput("Doors locked")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("confirm_release_order_button").performClick()
        composeRule.waitForIdle()

        assertEquals("ord_food_1", capturedOrderId)
        assertEquals("close_restaurant", capturedType)
        assertTrue(capturedReason.contains("Doors locked"))
    }

    @Test
    fun releaseOrderButton_hiddenAfterPickup() {
        val pickedUpFood = sampleFoodOrder(riderStep = OrderStepConstants.STEP_TO_CUSTOMER)
        val foodStops = RouteStopCalculator.computeBatchStops(listOf(pickedUpFood), 9.7392, 118.7353)

        composeRule.setContent {
            DelivioRiderTheme {
                OrderDetailsSheet(
                    batchStops = foodStops,
                    currentStop = foodStops.first { !it.isFinished },
                    activeOrders = listOf(pickedUpFood),
                    isOnline = true,
                    walletBalance = 0.0,
                    todayEarnings = 0.0,
                    todayTrips = 0,
                    currencySymbol = "₱",
                    supportPhone = null,
                    sheetState = OrderSheetState.EXPANDED,
                    onGoOnlineClick = {},
                    onAdvanceStep = { _, _ -> },
                    onReportProblem = {},
                    onCallClick = { _, _ -> }
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("release_order_button").assertDoesNotExist()
        composeRule.onNodeWithTag("report_problem_button").assertIsDisplayed()
    }

    @Test
    fun parcelReleaseDialog_showsOnlyCrCancellation() {
        val parcelOrder = sampleParcelOrder(
            status = OrderStepConstants.STATUS_RIDER_ON_WAY_SENDER,
            riderStep = OrderStepConstants.STEP_TO_SENDER
        )
        val stops = RouteStopCalculator.computeBatchStops(listOf(parcelOrder), 9.7392, 118.7353)
        var capturedOrderId = ""
        var capturedType = ""

        composeRule.setContent {
            DelivioRiderTheme {
                OrderDetailsSheet(
                    batchStops = stops,
                    currentStop = stops.first { !it.isFinished },
                    activeOrders = listOf(parcelOrder),
                    isOnline = true,
                    walletBalance = 0.0,
                    todayEarnings = 0.0,
                    todayTrips = 0,
                    currencySymbol = "₱",
                    supportPhone = null,
                    sheetState = OrderSheetState.EXPANDED,
                    onGoOnlineClick = {},
                    onAdvanceStep = { _, _ -> },
                    onReleaseOrderWithType = { id, type, _ ->
                        capturedOrderId = id
                        capturedType = type
                    },
                    onReportProblem = {},
                    onCallClick = { _, _ -> }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("release_order_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("release_order_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("release_type_cr_cancellation").assertIsDisplayed()
        composeRule.onNodeWithTag("release_type_close_restaurant").assertDoesNotExist()

        composeRule.onNodeWithTag("confirm_release_order_button").performClick()
        composeRule.waitForIdle()

        assertEquals(parcelOrder.id, capturedOrderId)
        assertEquals("cr_cancellation", capturedType)
    }

    @Test
    fun phoneUtils_cleansSpacesDashesBracketsAndKeepsLeadingPlus() {
        assertEquals("+639171234567", com.example.util.PhoneUtils.cleanPhoneNumber(" +63 917-123 (4567) "))
        assertEquals("0281234567", com.example.util.PhoneUtils.cleanPhoneNumber("(02) 8123-4567"))
        assertEquals("09182220002", com.example.util.PhoneUtils.cleanPhoneNumber("[0918] 222-{0002}"))
        assertEquals("", com.example.util.PhoneUtils.cleanPhoneNumber(""))
        assertEquals("", com.example.util.PhoneUtils.cleanPhoneNumber("   "))
        assertEquals("", com.example.util.PhoneUtils.cleanPhoneNumber("Not provided"))
        assertEquals("", com.example.util.PhoneUtils.cleanPhoneNumber("() - "))
    }

    @Test
    fun sheetCallButton_missingPhoneIsDisabledWithNoPhoneNumberLabel() {
        val foodOrder = sampleFoodOrder().copy(merchantPhone = "Not provided")
        val stops = RouteStopCalculator.computeBatchStops(listOf(foodOrder), 9.7392, 118.7353)

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

        composeRule.onNodeWithTag("quick_actions_row").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("sheet_call_button").assertIsDisplayed().assertIsNotEnabled()
        composeRule.onNodeWithText("No phone number").assertIsDisplayed()
    }

    @Test
    fun sheetCallButton_cleansFormattedNumberBeforeForwardingCall() {
        val foodOrder = sampleFoodOrder().copy(merchantPhone = "+63 (917) 555-12 34")
        val stops = RouteStopCalculator.computeBatchStops(listOf(foodOrder), 9.7392, 118.7353)
        var calledPhone = ""

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
                    onCallClick = { _, phone -> calledPhone = phone }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("sheet_call_button").performScrollTo().assertIsEnabled().performClick()
        composeRule.waitForIdle()
        assertEquals("+639175551234", calledPhone)
    }

    @Test
    fun tripDetailsDialog_missingPhoneDisablesButtonWithNoPhoneNumberAndCleansFormattedNumber() {
        val foodOrder = sampleFoodOrder().copy(
            merchantPhone = "+63 (48) 433-9900",
            customerPhone = ""
        )
        val stops = RouteStopCalculator.computeBatchStops(listOf(foodOrder), 9.7392, 118.7353)
        var calledPhone = ""

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
                    onCallClick = { _, phone -> calledPhone = phone }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("trip_details_chip").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("trip_details_merchant_call_button")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        composeRule.waitForIdle()
        assertEquals("+63484339900", calledPhone)

        composeRule.onNodeWithTag("trip_details_customer_call_button")
            .performScrollTo()
            .assertIsNotEnabled()
        composeRule.onNode(
            hasTestTag("trip_details_customer_call_button") and hasText("No phone number")
        ).assertIsDisplayed()
    }

    @Test
    fun sheetMoreButton_opensMoreMenuAndNavigatesToTripDetails() {
        val foodOrder = sampleFoodOrder()
        val stops = RouteStopCalculator.computeBatchStops(listOf(foodOrder), 9.7392, 118.7353)

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

        composeRule.onNodeWithTag("sheet_more_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("sheet_more_menu").assertIsDisplayed()
        composeRule.onNodeWithTag("more_option_trip_details").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("trip_details_page").assertIsDisplayed()
    }

    @Test
    fun drawerMenuPanel_locksOfflineSwitchDuringActiveOrder() {
        val onlineRider = com.example.data.models.Rider(
            uid = "r1",
            name = "Juan Dela Cruz",
            isOnline = true
        )

        composeRule.setContent {
            DelivioRiderTheme {
                com.example.ui.screens.DrawerMenuPanel(
                    isOpen = true,
                    rider = onlineRider,
                    unreadInboxCount = 0,
                    hasActiveOrders = true,
                    onToggleOnline = {},
                    onToggleService = { _, _ -> },
                    onClose = {},
                    onNavigate = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("drawer_online_status_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("drawer_online_switch").assertIsNotEnabled()
        composeRule.onNodeWithText("Going offline locked during active order").assertIsDisplayed()
    }

    @Test
    fun mainMapTopBar_showsOnlyHamburgerOnLeftAndHeadsetOnRight() {
        var drawerOpened = false
        var headsetOpened = false

        composeRule.setContent {
            DelivioRiderTheme {
                com.example.ui.screens.MainMapTopBar(
                    hasUnreadAdminMessage = false,
                    hasUnresolvedTicket = false,
                    onOpenDrawer = { drawerOpened = true },
                    onHeadsetClick = { headsetOpened = true }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("main_top_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("hamburger_menu_button").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("support_headset_button").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("top_navigation_card").assertDoesNotExist()
        composeRule.onNodeWithTag("nav_support_headset_button").assertDoesNotExist()

        assertTrue(drawerOpened)
        assertTrue(headsetOpened)
    }

    @Test
    fun phoneUtils_openSystemDialerStartsActionDialWithCleanedTelUri() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val started = com.example.util.PhoneUtils.openSystemDialer(context, "+63 (917) 123-4567")
        assertTrue(started)

        val shadowApp = org.robolectric.Shadows.shadowOf(context as android.app.Application)
        val launchedIntent = shadowApp.nextStartedActivity
        assertEquals(android.content.Intent.ACTION_DIAL, launchedIntent.action)
        assertEquals("tel:+639171234567", launchedIntent.dataString)
    }
}




