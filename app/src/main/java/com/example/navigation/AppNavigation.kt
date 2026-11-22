package com.example.navigation

import androidx.compose.animation.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.screens.*
import com.example.viewmodel.RiderViewModel

object NavRoutes {
    const val LOGIN = "login"
    const val MAIN_MAP = "main_map"
    const val INBOX = "inbox"
    const val SCHEDULE = "schedule"
    const val WALLET = "wallet"
    const val EARNINGS = "earnings"
    const val PAYMENTS = "earnings"
    const val PROFILE = "profile"
    const val QUESTS = "quests"
    const val RESOURCES_QUESTS = "resources/quests"
    const val RESOURCES_INSIGHTS = "resources/insights"
    const val RESOURCES_REVIEWS = "resources/reviews"
    const val RESOURCES_SAFETY = "resources/safety"
    const val RESOURCES_TUTORIALS = "resources/tutorials"
    const val TIERS = "resources/tiers"
    const val SETTINGS = "settings"
    const val SUPPORT = "support"
    const val REPORT_ISSUE = "report_issue"
    const val SUPPORT_CHAT_BASE = "support_chat"
    const val SUPPORT_CHAT = "support_chat/{ticketId}"
}

@Composable
fun AppNavigation(
    viewModel: RiderViewModel,
    navController: NavHostController = rememberNavController()
) {
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val rider by viewModel.rider.collectAsState()
    val activeOrders by viewModel.activeOrders.collectAsState()
    val unreadCount by viewModel.unreadNotificationCount.collectAsState()
    val avgRating by viewModel.averageCustomerRating.collectAsState()
    val reviewCount by viewModel.customerReviewCount.collectAsState()
    val context = LocalContext.current

    var isDrawerOpen by remember { mutableStateOf(false) }

    val startDestination = if (isLoggedIn) NavRoutes.MAIN_MAP else NavRoutes.LOGIN

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() }
        ) {
            composable(NavRoutes.LOGIN) {
                LoginScreen(
                    viewModel = viewModel,
                    onLoginSuccess = {
                        navController.navigate(NavRoutes.MAIN_MAP) {
                            popUpTo(NavRoutes.LOGIN) { inclusive = true }
                        }
                    }
                )
            }

            composable(NavRoutes.MAIN_MAP) {
                MainMapScreen(
                    viewModel = viewModel,
                    onOpenDrawer = { isDrawerOpen = true },
                    onOpenSupport = { navController.navigate(NavRoutes.SUPPORT) },
                    onOpenHelpCenter = { orderId, reasonKey ->
                        val params = mutableListOf<String>()
                        if (!orderId.isNullOrBlank()) params.add("orderId=$orderId")
                        if (!reasonKey.isNullOrBlank()) params.add("reasonKey=$reasonKey")
                        val route = if (params.isNotEmpty()) "${NavRoutes.REPORT_ISSUE}?${params.joinToString("&")}" else NavRoutes.REPORT_ISSUE
                        navController.navigate(route)
                    },
                    onOpenSupportChat = { ticketId ->
                        navController.navigate("${NavRoutes.SUPPORT_CHAT_BASE}/$ticketId")
                    },
                    onOpenEarnings = { navController.navigate(NavRoutes.EARNINGS) }
                )
            }

            composable(NavRoutes.INBOX) {
                InboxScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(NavRoutes.SCHEDULE) {
                ScheduleScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(NavRoutes.WALLET) {
                WalletScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onOpenEarnings = { navController.navigate(NavRoutes.EARNINGS) }
                )
            }

            composable(NavRoutes.EARNINGS) {
                EarningsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(NavRoutes.PROFILE) {
                ProfileScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onNavigateToReviews = { navController.navigate(NavRoutes.RESOURCES_REVIEWS) }
                )
            }

            composable(NavRoutes.QUESTS) {
                QuestsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(NavRoutes.RESOURCES_QUESTS) {
                ResourcesScreen(
                    initialSubpage = "quests",
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onOpenEarnings = { navController.navigate(NavRoutes.EARNINGS) }
                )
            }

            composable(NavRoutes.TIERS) {
                TiersScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(NavRoutes.RESOURCES_INSIGHTS) {
                ResourcesScreen(
                    initialSubpage = "insights",
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onOpenEarnings = { navController.navigate(NavRoutes.EARNINGS) }
                )
            }

            composable(NavRoutes.RESOURCES_SAFETY) {
                ResourcesScreen(
                    initialSubpage = "safety",
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(NavRoutes.RESOURCES_REVIEWS) {
                ResourcesScreen(
                    initialSubpage = "reviews",
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onOpenEarnings = { navController.navigate(NavRoutes.EARNINGS) }
                )
            }

            composable(NavRoutes.RESOURCES_TUTORIALS) {
                ResourcesScreen(
                    initialSubpage = "tutorials",
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(NavRoutes.SETTINGS) {
                SettingsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onLogout = {
                        navController.navigate(NavRoutes.LOGIN) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }

            composable(NavRoutes.SUPPORT) {
                SupportScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "${NavRoutes.REPORT_ISSUE}?orderId={orderId}&reasonKey={reasonKey}",
                arguments = listOf(
                    navArgument("orderId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("reasonKey") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val orderId = backStackEntry.arguments?.getString("orderId")
                val reasonKey = backStackEntry.arguments?.getString("reasonKey")
                HelpCenterFlowScreen(
                    viewModel = viewModel,
                    preselectedOrderId = orderId,
                    preselectedReasonKey = reasonKey,
                    onBack = { navController.popBackStack() },
                    onTicketCreated = { ticketId ->
                        navController.navigate("${NavRoutes.SUPPORT_CHAT_BASE}/$ticketId") {
                            popUpTo(NavRoutes.MAIN_MAP)
                        }
                    }
                )
            }

            composable(
                route = "${NavRoutes.SUPPORT_CHAT_BASE}/{ticketId}",
                arguments = listOf(navArgument("ticketId") { type = NavType.StringType })
            ) { backStackEntry ->
                val ticketId = backStackEntry.arguments?.getString("ticketId") ?: ""
                SupportChatScreen(
                    ticketId = ticketId,
                    viewModel = viewModel,
                    onSafeExit = {
                        navController.navigate(NavRoutes.MAIN_MAP) {
                            popUpTo(NavRoutes.MAIN_MAP) { inclusive = false }
                        }
                    }
                )
            }
        }

        // Overlay 75% width Drawer
        DrawerMenuPanel(
            isOpen = isDrawerOpen,
            rider = rider,
            unreadInboxCount = unreadCount,
            averageRating = avgRating,
            reviewCount = reviewCount,
            isServiceOn = { viewModel.isServiceOn(it) },
            getServiceLockReason = { viewModel.getServiceLockReason(it) },
            cityName = viewModel.getCityDisplayName(),
            hasActiveOrders = activeOrders.isNotEmpty(),
            onToggleOnline = { viewModel.toggleOnline(context) },
            onToggleService = { serviceKey, enabled ->
                viewModel.toggleService(serviceKey, enabled)
            },
            onClose = { isDrawerOpen = false },
            onNavigate = { route ->
                val fullRoute = when (route) {
                    "inbox" -> NavRoutes.INBOX
                    "schedule" -> NavRoutes.SCHEDULE
                    "wallet" -> NavRoutes.WALLET
                    "earnings" -> NavRoutes.EARNINGS
                    "payments" -> NavRoutes.EARNINGS
                    "profile" -> NavRoutes.PROFILE
                    "quests" -> NavRoutes.QUESTS
                    "resources/quests" -> NavRoutes.RESOURCES_QUESTS
                    "resources/tiers" -> NavRoutes.TIERS
                    "resources/insights" -> NavRoutes.RESOURCES_INSIGHTS
                    "resources/reviews" -> NavRoutes.RESOURCES_REVIEWS
                    "resources/safety" -> NavRoutes.RESOURCES_SAFETY
                    "resources/tutorials" -> NavRoutes.RESOURCES_TUTORIALS
                    "settings" -> NavRoutes.SETTINGS
                    else -> route
                }
                navController.navigate(fullRoute)
            }
        )
    }
}
