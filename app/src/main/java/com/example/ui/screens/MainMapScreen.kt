package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.models.Order
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.viewmodel.RiderViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainMapScreen(
    viewModel: RiderViewModel,
    onOpenDrawer: () -> Unit,
    onOpenSupport: () -> Unit,
    onOpenEarnings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val rider by viewModel.rider.collectAsState()
    val isOnline = rider?.isOnline ?: false
    val activeOrders by viewModel.activeOrders.collectAsState()
    val currentOrder by viewModel.currentActiveOrder.collectAsState()
    val selectedIndex by viewModel.selectedOrderIndex.collectAsState()
    val incomingOffer by viewModel.incomingOffer.collectAsState()
    val deliveredOrders by viewModel.deliveredOrders.collectAsState()
    val currencySymbol by viewModel.currencySymbol.collectAsState()
    val supportPhone by viewModel.supportPhone.collectAsState()
    val activeCall by viewModel.activeCall.collectAsState()
    val newlyReachedTier by viewModel.newlyReachedTier.collectAsState()
    val completedOrder by viewModel.completedDeliveryOrder.collectAsState()
    val acceptOrderError by viewModel.acceptOrderError.collectAsState()
    val liveLoc by viewModel.currentLocation.collectAsState()

    val riderLat = liveLoc?.first ?: 9.7392
    val riderLng = liveLoc?.second ?: 118.7353

    var recenterTrigger by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarMsg by viewModel.snackbarMessage.collectAsState()

    LaunchedEffect(snackbarMsg) {
        snackbarMsg?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    // Permission launcher for Location and Notifications
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val needed = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    // Today earnings & trips
    val todayTrips = deliveredOrders.size
    val todayEarnings = deliveredOrders.sumOf { it.estimatedEarnings }
    val walletCashOnHand = deliveredOrders.filter { it.isCod }.sumOf { it.codCollected } - (rider?.cashRemitted ?: 0.0)

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier
            .fillMaxSize()
            .testTag("main_map_screen")
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 1. Real OpenStreetMap with osmdroid (No Google Maps, No API keys)
            OsmMapView(
                riderLat = riderLat,
                riderLng = riderLng,
                activeOrders = activeOrders,
                currentActiveOrder = currentOrder,
                recenterTrigger = recenterTrigger,
                modifier = Modifier.fillMaxSize()
            )

            // 2. Floating Controls Overlay
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Row Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    // Top-Left: Hamburger & Refresh
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularFloatingButton(
                            icon = Icons.Default.Menu,
                            contentDescription = "Menu",
                            onClick = onOpenDrawer,
                            testTag = "hamburger_menu_button"
                        )

                        CircularFloatingButton(
                            icon = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            onClick = { recenterTrigger += 1 },
                            testTag = "refresh_demand_button"
                        )
                    }

                    // Top-Center: Online / Offline Large Rounded Pill
                    OnlineOfflinePill(
                        isOnline = isOnline,
                        onClick = { viewModel.toggleOnline(context) }
                    )

                    // Top-Right: Circular Headset Button for Support
                    CircularFloatingButton(
                        icon = Icons.Default.Headphones,
                        contentDescription = "Support",
                        onClick = {
                            if (!supportPhone.isNullOrBlank()) {
                                viewModel.contactSupport(context)
                            } else {
                                onOpenSupport()
                            }
                        },
                        testTag = "support_headset_button"
                    )
                }

                // Bottom Controls: Recenter and OrderDetailsSheet
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.End
                ) {
                    CircularFloatingButton(
                        icon = Icons.Default.MyLocation,
                        contentDescription = "Recenter",
                        onClick = { recenterTrigger += 1 },
                        modifier = Modifier.padding(bottom = 12.dp),
                        testTag = "recenter_button"
                    )

                    OrderDetailsSheet(
                        activeOrders = activeOrders,
                        currentOrder = currentOrder,
                        selectedIndex = selectedIndex,
                        onSelectIndex = { viewModel.selectOrderIndex(it) },
                        isOnline = isOnline,
                        walletBalance = walletCashOnHand.coerceAtLeast(0.0),
                        todayEarnings = todayEarnings,
                        todayTrips = todayTrips,
                        currencySymbol = currencySymbol,
                        supportPhone = supportPhone,
                        onGoOnlineClick = { viewModel.toggleOnline(context) },
                        onAdvanceStep = { receiptTotal -> viewModel.advanceCurrentOrderStep(receiptTotal) },
                        onReleaseOrder = { reason -> viewModel.releaseOrder(reason) },
                        onReportProblem = { reason ->
                            currentOrder?.let { viewModel.reportProblem(it.id, reason) }
                        },
                        onCallClick = { name, phone -> viewModel.startCall(name, phone) },
                        onOpenEarnings = onOpenEarnings,
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                    )
                }
            }

            // 3. Incoming Offer Dialog (30s countdown, sound, vibration, exact section 8 fields)
            incomingOffer?.let { offer ->
                IncomingOrderDialog(
                    order = offer,
                    currencySymbol = currencySymbol,
                    soundEnabled = viewModel.soundEnabled.value,
                    vibrationEnabled = viewModel.vibrationEnabled.value,
                    onAccept = { viewModel.acceptIncomingOffer(offer.id) },
                    onDecline = { viewModel.declineIncomingOffer(offer.id) }
                )
            }

            // 4. Delivery Success Dialog
            completedOrder?.let { order ->
                DeliverySuccessDialog(
                    order = order,
                    currencySymbol = currencySymbol,
                    onDismiss = { viewModel.dismissDeliverySuccess() }
                )
            }

            // 5. Tier Celebration Dialog
            newlyReachedTier?.let { tier ->
                AlertDialog(
                    onDismissRequest = { viewModel.dismissCelebration() },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.WorkspacePremium, contentDescription = "Tiers", tint = DelivioYellowDark)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Tier Milestone Unlocked!", fontWeight = FontWeight.Bold)
                        }
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("🎉 Congratulations! You unlocked the ${tier.name} tier this month!", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text("Completed ${tier.ridesRequired}+ rides this month. Reward of ${tier.coins} coins will be credited upon admin confirmation.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.dismissCelebration() },
                            colors = ButtonDefaults.buttonColors(containerColor = DelivioYellow, contentColor = TextOnYellow)
                        ) {
                            Text("Awesome!", fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

            // 6. In-App Call Dialog
            activeCall?.let { call ->
                InAppCallDialog(
                    callInfo = call,
                    onEndCall = { viewModel.endCall() },
                    onOpenSystemDialer = { phone -> viewModel.launchExternalDialer(phone) }
                )
            }

            // 7. Accept Order Error Dialog
            acceptOrderError?.let { err ->
                AcceptOrderErrorDialog(
                    errorDetails = err,
                    onDismiss = { viewModel.dismissAcceptOrderError() }
                )
            }
        }
    }
}
