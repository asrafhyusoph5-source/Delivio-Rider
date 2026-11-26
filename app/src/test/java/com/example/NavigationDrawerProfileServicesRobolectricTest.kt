package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.example.data.constants.OrderStepConstants
import com.example.data.models.Rider
import com.example.data.models.Vehicle
import com.example.ui.components.OnlineOfflinePill
import com.example.ui.components.OrderDetailsSheet
import com.example.ui.screens.DrawerMenuPanel
import com.example.ui.screens.ProfileContent
import com.example.ui.screens.ServiceTypesContent
import com.example.ui.screens.formatAssignedCityName
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
class NavigationDrawerProfileServicesRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun serviceTypesContent_showsAllSixServicesAndLockReasonsAndTogglesUnlockedService() {
        val rider = Rider(
            uid = "r1",
            name = "Test Driver",
            cityId = "puerto_princesa",
            vehicle = Vehicle(type = "tricycle"),
            allowedServices = listOf(
                OrderStepConstants.SERVICE_FOOD,
                OrderStepConstants.SERVICE_MART,
                OrderStepConstants.SERVICE_PARCEL,
                OrderStepConstants.SERVICE_PABILI,
                OrderStepConstants.SERVICE_TAXI
            ), // SERVICE_REQUEST admin-disabled
            activeServices = listOf(OrderStepConstants.SERVICE_PARCEL)
        )
        val toggled = mutableListOf<Pair<String, Boolean>>()
        var backedOut = false

        composeRule.setContent {
            DelivioRiderTheme {
                ServiceTypesContent(
                    rider = rider,
                    isPlatformServiceOn = { svc -> svc != OrderStepConstants.SERVICE_PARCEL }, // parcel platform-disabled
                    isCityServiceOn = { svc -> svc != OrderStepConstants.SERVICE_TAXI }, // taxi city-disabled
                    cityDisplayName = "Puerto Princesa",
                    onToggleService = { svc, enabled -> toggled.add(svc to enabled) },
                    onBack = { backedOut = true }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Service Types").assertIsDisplayed()

        // 1. food -> locked by vehicle (tricycle only supports parcel & taxi)
        composeRule.onNodeWithTag("service_types_list").performScrollToNode(hasTestTag("service_type_card_${OrderStepConstants.SERVICE_FOOD}"))
        composeRule.onNodeWithTag("service_type_card_${OrderStepConstants.SERVICE_FOOD}").assertIsDisplayed()
        composeRule.onNodeWithText("Delivio Food").assertIsDisplayed()
        composeRule.onNodeWithTag("service_type_switch_${OrderStepConstants.SERVICE_FOOD}").assertIsNotEnabled()

        // 2. convenience (Delivio Mart) -> locked by vehicle
        composeRule.onNodeWithTag("service_types_list").performScrollToNode(hasTestTag("service_type_card_${OrderStepConstants.SERVICE_MART}"))
        composeRule.onNodeWithTag("service_type_card_${OrderStepConstants.SERVICE_MART}").assertIsDisplayed()
        composeRule.onNodeWithText("Delivio Mart").assertIsDisplayed()
        composeRule.onNodeWithTag("service_type_switch_${OrderStepConstants.SERVICE_MART}").assertIsNotEnabled()

        // 3. parcel -> locked by platform
        composeRule.onNodeWithTag("service_types_list").performScrollToNode(hasTestTag("service_type_card_${OrderStepConstants.SERVICE_PARCEL}"))
        composeRule.onNodeWithTag("service_type_card_${OrderStepConstants.SERVICE_PARCEL}").assertIsDisplayed()
        composeRule.onNodeWithText("Delivio Parcel").assertIsDisplayed()
        composeRule.onNodeWithText("Disabled platform-wide").assertIsDisplayed()
        composeRule.onNodeWithTag("service_type_switch_${OrderStepConstants.SERVICE_PARCEL}").assertIsNotEnabled()

        // 4. pabili -> locked by vehicle
        composeRule.onNodeWithTag("service_types_list").performScrollToNode(hasTestTag("service_type_card_${OrderStepConstants.SERVICE_PABILI}"))
        composeRule.onNodeWithTag("service_type_card_${OrderStepConstants.SERVICE_PABILI}").assertIsDisplayed()
        composeRule.onNodeWithText("Delivio Pabili").assertIsDisplayed()
        composeRule.onNodeWithTag("service_type_switch_${OrderStepConstants.SERVICE_PABILI}").assertIsNotEnabled()

        // 5. request_rider (Delivio Instant) -> locked by admin (allowedServices lacks request_rider)
        composeRule.onNodeWithTag("service_types_list").performScrollToNode(hasTestTag("service_type_card_${OrderStepConstants.SERVICE_REQUEST}"))
        composeRule.onNodeWithTag("service_type_card_${OrderStepConstants.SERVICE_REQUEST}").assertIsDisplayed()
        composeRule.onNodeWithText("Delivio Instant").assertIsDisplayed()
        composeRule.onNodeWithText("Disabled for your account by admin").assertIsDisplayed()
        composeRule.onNodeWithTag("service_type_switch_${OrderStepConstants.SERVICE_REQUEST}").assertIsNotEnabled()

        // 6. taxi -> locked by city
        composeRule.onNodeWithTag("service_types_list").performScrollToNode(hasTestTag("service_type_card_${OrderStepConstants.SERVICE_TAXI}"))
        composeRule.onNodeWithTag("service_type_card_${OrderStepConstants.SERVICE_TAXI}").assertIsDisplayed()
        composeRule.onNodeWithText("Delivio Taxi").assertIsDisplayed()
        composeRule.onNodeWithText("Disabled in Puerto Princesa").assertIsDisplayed()
        composeRule.onNodeWithTag("service_type_switch_${OrderStepConstants.SERVICE_TAXI}").assertIsNotEnabled()

        composeRule.onNodeWithContentDescription("Back").performClick()
        assertTrue(backedOut)
        assertTrue(toggled.isEmpty())
    }

    @Test
    fun serviceTypesContent_allowsTogglingUnlockedServicesForMotorcycle() {
        val rider = Rider(
            uid = "r2",
            name = "Moto Driver",
            cityId = "puerto_princesa",
            vehicle = Vehicle(type = "motorcycle"),
            allowedServices = OrderStepConstants.ALL_SERVICES,
            activeServices = listOf(OrderStepConstants.SERVICE_FOOD)
        )
        val toggled = mutableListOf<Pair<String, Boolean>>()

        composeRule.setContent {
            DelivioRiderTheme {
                ServiceTypesContent(
                    rider = rider,
                    cityDisplayName = "Puerto Princesa",
                    onToggleService = { svc, enabled -> toggled.add(svc to enabled) },
                    onBack = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("service_types_list").performScrollToNode(hasTestTag("service_type_card_${OrderStepConstants.SERVICE_MART}"))
        composeRule.onNodeWithTag("service_type_switch_${OrderStepConstants.SERVICE_MART}").assertIsEnabled().performClick()
        assertEquals(listOf(OrderStepConstants.SERVICE_MART to true), toggled)
    }

    @Test
    fun onlineOfflinePill_updatesTextInstantlyBetweenOfflineAndOnline() {
        var onlineState by mutableStateOf(false)
        var clickCount = 0

        composeRule.setContent {
            DelivioRiderTheme {
                OnlineOfflinePill(
                    isOnline = onlineState,
                    onClick = { clickCount++ }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("online_offline_pill").assertIsDisplayed()
        composeRule.onNodeWithText("Offline").assertIsDisplayed()
        composeRule.onNodeWithText("Online").assertDoesNotExist()

        composeRule.runOnUiThread { onlineState = true }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Online").assertIsDisplayed()
        composeRule.onNodeWithText("Offline").assertDoesNotExist()

        composeRule.onNodeWithTag("online_offline_pill").performClick()
        assertEquals(1, clickCount)
    }

    @Test
    fun orderDetailsSheet_idleShowsMatchingStatusTitleAndButtonWithConfirmation() {
        var onlineState by mutableStateOf(false)
        var toggleCalls = 0

        composeRule.setContent {
            DelivioRiderTheme {
                OrderDetailsSheet(
                    batchStops = emptyList(),
                    currentStop = null,
                    activeOrders = emptyList(),
                    isOnline = onlineState,
                    walletBalance = 500.0,
                    todayEarnings = 250.0,
                    todayTrips = 3,
                    currencySymbol = "₱",
                    supportPhone = null,
                    onGoOnlineClick = { toggleCalls++ },
                    onAdvanceStep = { _, _ -> },
                    onReportProblem = {},
                    onCallClick = { _, _ -> }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("You are Offline").assertIsDisplayed()
        composeRule.onNodeWithTag("go_online_button").assertIsDisplayed()
        composeRule.onNodeWithText("Offline").assertIsDisplayed()
        composeRule.onNodeWithTag("go_offline_button").assertDoesNotExist()

        // Tap Offline button -> asks "Go Online?" first; Cancel does not toggle
        composeRule.onNodeWithTag("go_online_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Go Online?").assertIsDisplayed()
        composeRule.onNodeWithTag("cancel_online_toggle_button").performClick()
        composeRule.waitForIdle()
        assertEquals(0, toggleCalls)

        // Tap Offline button -> confirm "Go Online" toggles state
        composeRule.onNodeWithTag("go_online_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("confirm_online_toggle_button").performClick()
        composeRule.waitForIdle()
        assertEquals(1, toggleCalls)

        composeRule.runOnUiThread { onlineState = true }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("You are Online").assertIsDisplayed()
        composeRule.onNodeWithTag("go_offline_button").assertIsDisplayed()
        composeRule.onNodeWithText("Online").assertIsDisplayed()
        composeRule.onNodeWithTag("go_online_button").assertDoesNotExist()

        // Tap Online button -> asks "Go Offline?" first -> confirm
        composeRule.onNodeWithTag("go_offline_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Go Offline?").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm_online_toggle_button").performClick()
        composeRule.waitForIdle()
        assertEquals(2, toggleCalls)
    }

    @Test
    fun drawerMenuPanel_tappingGreetingOpensProfile_andServiceTypeDoesNotExpandInPlace() {
        val rider = Rider(uid = "r3", name = "Test User")
        val routes = mutableListOf<String>()
        var closeCount = 0

        composeRule.setContent {
            DelivioRiderTheme {
                DrawerMenuPanel(
                    isOpen = true,
                    rider = rider,
                    unreadInboxCount = 2,
                    averageRating = 4.9,
                    reviewCount = 12,
                    onClose = { closeCount++ },
                    onNavigate = { routes.add(it) }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Hi, Test 👋").assertIsDisplayed()
        composeRule.onNodeWithText("My profile").assertDoesNotExist()
        composeRule.onNodeWithTag("drawer_online_status_card").assertDoesNotExist()
        composeRule.onNodeWithTag("drawer_online_switch").assertDoesNotExist()

        composeRule.onNodeWithTag("drawer_profile_greeting_button").performClick()
        assertEquals(listOf("profile"), routes)
        assertEquals(1, closeCount)

        composeRule.onNodeWithTag("drawer_service_type_item").performScrollTo().performClick()
        // Service Type should navigate immediately rather than expanding inline cards
        composeRule.onNodeWithText("Delivio Food").assertDoesNotExist()
        assertEquals(listOf("profile", "service_types"), routes)
        assertEquals(2, closeCount)
    }

    @Test
    fun profileContent_omitsRatingsCardAndCompletedTrips_showsFormattedCityAndNavigatesOnRatingTap() {
        val rider = Rider(
            uid = "r4",
            name = "Test User",
            riderCode = "DRV-101",
            cityId = "puerto_princesa",
            rating = 4.85,
            totalTrips = 120
        )
        var reviewsOpened = false

        composeRule.setContent {
            DelivioRiderTheme {
                ProfileContent(
                    rider = rider,
                    cityDisplayName = "puerto_princesa",
                    onBack = {},
                    onNavigateToReviews = { reviewsOpened = true }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Completed Trips").assertDoesNotExist()
        composeRule.onNodeWithText("Ratings and reviews").assertDoesNotExist()
        composeRule.onNodeWithTag("profile_ratings_reviews_row").assertDoesNotExist()

        composeRule.onNodeWithTag("profile_assigned_city_stat").assertIsDisplayed()
        composeRule.onNodeWithText("Assigned City").assertIsDisplayed()
        composeRule.onNodeWithText("Puerto Princesa").assertIsDisplayed()

        composeRule.onNodeWithTag("profile_rating_stat").assertIsDisplayed().performClick()
        assertTrue(reviewsOpened)
        assertEquals("Puerto Princesa", formatAssignedCityName("", "puerto_princesa"))
        assertEquals("Puerto Princesa", formatAssignedCityName("puerto princesa", ""))
    }

    @Test
    fun acceptOrderRules_checksAllEightConditionsAndCanonicalizesServices() {
        // Canonical service mapping ("instant" -> "request_rider", "mart" -> "convenience")
        val normalized = OrderStepConstants.normalizeOrderServiceKeys(
            listOf("food", "mart", "parcel", "pabili", "instant", "taxi")
        )
        assertEquals(
            listOf("food", "convenience", "parcel", "pabili", "request_rider", "taxi"),
            normalized
        )
        assertTrue(OrderStepConstants.isServiceAllowedMatch(listOf("instant"), "request_rider"))
        assertTrue(OrderStepConstants.isServiceAllowedMatch(listOf("request_rider"), "instant"))

        // Evaluate 8 checks: failing rider status, vehicle match, city off, excluded, onHold, negative cash wallet, taken riderId
        val checks = com.example.data.models.AcceptOrderRules.evaluateAcceptChecks(
            myUid = "rider_1",
            riderStatus = "suspended",
            riderAllowedServices = listOf("food", "instant"),
            riderTopLevelVehicleType = "",
            riderFallbackVehicleType = "motorcycle",
            orderServiceType = "parcel",
            orderVehicleType = "sedan",
            orderCityId = "puerto_princesa",
            isCityServiceOn = false,
            excludeRiderIds = listOf("rider_1"),
            onHold = true,
            isCashOrder = true,
            userWalletBalance = -50.0,
            orderRiderId = "other_rider"
        )
        val byKey = checks.associateBy { it.key }
        assertEquals(8, checks.size)
        assertFalse(byKey.getValue("rider_status").passed)
        assertFalse(byKey.getValue("allowed_services").passed)
        assertFalse(byKey.getValue("vehicle_match").passed)
        assertFalse(byKey.getValue("city_service").passed)
        assertFalse(byKey.getValue("exclude_riders").passed)
        assertFalse(byKey.getValue("on_hold").passed)
        assertFalse(byKey.getValue("cod_wallet").passed)
        assertFalse(byKey.getValue("rider_id_empty").passed)

        // When riderId is already me -> ALREADY_OURS
        val alreadyOursEval = com.example.data.models.AcceptOrderRules.evaluateDocument(
            exists = true,
            riderIdRaw = "rider_1",
            statusRaw = "Preparing",
            riderRequestStatusRaw = null,
            serviceTypeRaw = "instant",
            excludeRiderIds = emptyList(),
            myUid = "rider_1",
            onHold = false
        )
        assertEquals(com.example.data.models.AcceptOrderDecision.ALREADY_OURS, alreadyOursEval.decision)
    }

    @Test
    fun acceptOrderErrorDialog_showsShortMessageAndDetailsButtonRevealingFailedChecks() {
        val checks = com.example.data.models.AcceptOrderRules.evaluateAcceptChecks(
            myUid = "rider_1",
            riderStatus = "active",
            riderAllowedServices = listOf("food"),
            riderTopLevelVehicleType = "motorcycle",
            orderServiceType = "taxi",
            orderVehicleType = "car4",
            orderCityId = "puerto_princesa",
            isCityServiceOn = true,
            excludeRiderIds = emptyList(),
            onHold = false,
            isCashOrder = false,
            userWalletBalance = 100.0,
            orderRiderId = ""
        )
        val details = com.example.data.models.AcceptOrderErrorDetails(
            exceptionMessage = "PERMISSION_DENIED",
            orderId = "ord_99",
            currentStatus = "Searching",
            authUid = "rider_1",
            shortMessage = com.example.data.models.AcceptOrderRules.buildShortFailureMessage(checks, "PERMISSION_DENIED"),
            checks = checks
        )

        composeRule.setContent {
            DelivioRiderTheme {
                com.example.ui.components.AcceptOrderErrorDialog(
                    errorDetails = details,
                    onDismiss = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("accept_order_error_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("accept_error_short_message").assertIsDisplayed()
        composeRule.onNodeWithTag("accept_error_details_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("accept_order_error_details_view").assertIsDisplayed()
        composeRule.onNodeWithTag("accept_check_vehicle_match").assertIsDisplayed()
        composeRule.onNodeWithTag("accept_check_allowed_services").assertIsDisplayed()
    }
}
