package com.example.navigation

import androidx.compose.animation.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
    const val RESOURCES_INSIGHTS = "resources/insights"
    const val RESOURCES_REVIEWS = "resources/reviews"
    const val RESOURCES_SAFETY = "resources/safety"
    const val RESOURCES_TUTORIALS = "resources/tutorials"
    const val TIERS = "resources/tiers"
    const val SETTINGS = "settings"
    const val SUPPORT = "support"
}

@Composable
fun AppNavigation(
    viewModel: RiderViewModel,
    navController: NavHostController = rememberNavController()
) {
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val rider by viewModel.rider.collectAsState()
    val unreadCount by viewModel.unreadNotificationCount.collectAsState()

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
                    onBack = { navController.popBackStack() }
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
        }

        // Overlay 75% width Drawer
        DrawerMenuPanel(
            isOpen = isDrawerOpen,
            rider = rider,
            unreadInboxCount = unreadCount,
            isServiceOn = { viewModel.isServiceOn(it) },
            cityName = viewModel.getCityDisplayName(),
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
