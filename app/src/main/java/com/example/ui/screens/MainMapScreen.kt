package com.example.ui.screens

import android.Manifest
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.constants.OrderStepConstants
import com.example.data.models.OfferBundle
import com.example.data.models.OfferType
import com.example.data.models.Order
import com.example.data.models.OrderPayBreakdown
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.viewmodel.RiderViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainMapScreen(
    viewModel: RiderViewModel,
    onOpenDrawer: () -> Unit,
    onOpenSupport: () -> Unit,
    onOpenHelpCenter: (String?, String?) -> Unit = { _, _ -> },
    onOpenSupportChat: (String) -> Unit = {},
    onOpenEarnings: () -> Unit = {},
    onOpenSchedule: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp

    val rider by viewModel.rider.collectAsState()
    val isOnline = rider?.isOnline ?: false
    val activeOrders by viewModel.activeOrders.collectAsState()
    val currentOrder by viewModel.currentActiveOrder.collectAsState()
    val batchStops by viewModel.batchStops.collectAsState()
    val currentStop by viewModel.currentRouteStop.collectAsState()
    val focusedStop by viewModel.focusedStopOnMap.collectAsState()
    val driverHeading by viewModel.heading.collectAsState()

    val incomingOffer by viewModel.incomingOffer.collectAsState()
    val incomingOfferBundle by viewModel.incomingOfferBundle.collectAsState()
    val deliveredOrders by viewModel.deliveredOrders.collectAsState()
    val currencySymbol by viewModel.currencySymbol.collectAsState()
    val supportPhone by viewModel.supportPhone.collectAsState()
    val newlyReachedTier by viewModel.newlyReachedTier.collectAsState()
    val completedOrder by viewModel.completedDeliveryOrder.collectAsState()
    val rateShopOrder by viewModel.rateShopOrder.collectAsState()
    val lateTipBanner by viewModel.lateTipBanner.collectAsState()
    val acceptOrderError by viewModel.acceptOrderError.collectAsState()
    val liveLoc by viewModel.currentLocation.collectAsState()

    val unresolvedTickets by viewModel.unresolvedTickets.collectAsState()
    val hasUnresolvedTicket by viewModel.hasUnresolvedTicket.collectAsState()
    val hasUnreadAdminMessage by viewModel.hasUnreadAdminMessage.collectAsState()
    val adminMessageBanner by viewModel.adminMessageBanner.collectAsState()
    val isBusyAreasVisible by viewModel.isBusyAreasOverlayVisible.collectAsState()
    val busyGridCells by viewModel.busyGridCells.collectAsState()
    val documentExpiryAlerts by viewModel.documentExpiryAlerts.collectAsState()
    val activeChatOrder by viewModel.activeChatOrder.collectAsState()
    val activeChatMessages by viewModel.activeChatMessages.collectAsState()
    val onlineSinceMillis by viewModel.onlineSinceMillis.collectAsState()
    val activeLiveBookedSession by viewModel.activeLiveBookedSession.collectAsState()
    val lastLocationMillis by viewModel.lastLocationTimeMillis.collectAsState()
    val enabledDispatchServices by viewModel.enabledDispatchServices.collectAsState()
    val dispatchListenerStatus by viewModel.dispatchListenerStatus.collectAsState()
    val ordersSeenCount by viewModel.ordersSeenCount.collectAsState()
    val badDataSkippedCount by viewModel.badDataSkippedCount.collectAsState()
    val eligibleOrdersCount by viewModel.eligibleOrdersCount.collectAsState()
    val skippedOrderRecords by viewModel.skippedOrderRecords.collectAsState()
    val lastTaxiDiagnostics by viewModel.lastTaxiDiagnostics.collectAsState()
    val walletBalance by viewModel.walletBalance.collectAsState()
    val isCodPaused by viewModel.isCodPausedByNegativeWallet.collectAsState()
    var showHeadsetMenuDialog by remember { mutableStateOf(false) }
    var showDispatchStatusSheet by remember { mutableStateOf(false) }
    var pendingReleaseTarget by remember { mutableStateOf<Pair<Order, String>?>(null) }

    LaunchedEffect(isOnline) {
        if (!isOnline) {
            showDispatchStatusSheet = false
        }
    }

    val riderLat = liveLoc?.first ?: 9.7392
    val riderLng = liveLoc?.second ?: 118.7353

    val view = LocalView.current
    var recenterTrigger by remember { mutableIntStateOf(0) }
    var sheetState by remember { mutableStateOf(OrderSheetState.EXPANDED) }
    var routeDistanceKm by remember { mutableDoubleStateOf(0.0) }
    var routeEtaMinutes by remember { mutableIntStateOf(0) }

    var sheetHeightPx by remember { mutableIntStateOf(0) }
    var topOverlayHeightPx by remember { mutableIntStateOf(0) }

    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarMsg by viewModel.snackbarMessage.collectAsState()

    // Open EXPANDED when a trip is accepted or a new step starts
    LaunchedEffect(
        currentStop?.orderId,
        currentStop?.stopNumber,
        currentStop?.order?.riderStep,
        currentStop?.order?.status,
        currentStop?.order?.hasArrivedAtDropoff
    ) {
        if (currentStop != null) {
            sheetState = OrderSheetState.EXPANDED
        }
    }

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
            androidx.core.content.ContextCompat.checkSelfPermission(context, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    // Today earnings & trips
    val todayTrips = deliveredOrders.size
    val todayEarnings = deliveredOrders.sumOf { it.estimatedEarnings }

    val navBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val fallbackSheetDp = (when {
        activeOrders.isEmpty() || currentStop == null -> 172.dp
        sheetState == OrderSheetState.COLLAPSED -> 88.dp
        else -> 360.dp
    }) + navBarBottomPadding
    val measuredSheetDp = if (sheetHeightPx > 0) with(density) { sheetHeightPx.toDp() } else fallbackSheetDp
    val animatedSheetHeight by animateDpAsState(
        targetValue = measuredSheetDp,
        animationSpec = tween(durationMillis = 250),
        label = "main_sheet_height"
    )

    val topBannerClearanceDp = if (topOverlayHeightPx > 0) {
        with(density) { topOverlayHeightPx.toDp() } + 8.dp
    } else {
        76.dp
    }

    val sheetHeightFraction = (animatedSheetHeight.value / screenHeight.value).coerceIn(0.08f, 0.85f)
    val isTripActive = activeOrders.isNotEmpty() && currentStop != null

    // System Back button collapses expanded trip sheet first instead of leaving screen
    BackHandler(enabled = isTripActive && sheetState == OrderSheetState.EXPANDED) {
        try {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        } catch (_: Throwable) {
        }
        sheetState = OrderSheetState.COLLAPSED
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier
            .fillMaxSize()
            .testTag("main_map_screen")
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 1. Map fills full screen behind overlays, dynamically centered above docked sheet
            OsmMapView(
                riderLat = riderLat,
                riderLng = riderLng,
                driverHeading = driverHeading,
                batchStops = batchStops,
                focusedStop = focusedStop,
                recenterTrigger = recenterTrigger,
                sheetHeightFraction = sheetHeightFraction,
                isSatelliteLayer = false,
                showBusyAreas = isBusyAreasVisible && !isTripActive,
                busyGridCells = busyGridCells,
                topBannerPaddingDp = topBannerClearanceDp,
                bottomAttributionPaddingDp = animatedSheetHeight,
                onRouteCalculated = { result ->
                    routeDistanceKm = result.currentLegDistanceKm
                    routeEtaMinutes = result.currentLegEtaMinutes
                },
                modifier = Modifier.fillMaxSize()
            )

            // 2. TOP OVERLAY COLUMN: Hamburger on top left, Headset on top right (both main screen and active trip) + Stacked Notification Banners
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .onSizeChanged { topOverlayHeightPx = it.height },
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MainMapTopBar(
                    isOnline = isOnline,
                    hasUnreadAdminMessage = hasUnreadAdminMessage,
                    hasUnresolvedTicket = hasUnresolvedTicket,
                    onOpenDrawer = onOpenDrawer,
                    onStatusPillClick = {
                        if (isOnline) {
                            showDispatchStatusSheet = true
                        } else {
                            viewModel.toggleOnline(context)
                        }
                    },
                    onHeadsetClick = { showHeadsetMenuDialog = true }
                )

                // Negative-wallet COD pause banner
                if (isCodPaused) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = DelivioRed,
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .testTag("cod_paused_banner")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "COD orders paused",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = com.example.data.models.WalletLedgerHelper.COD_PAUSED_REASON,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                // Slim Live Booked Session Bar (shows rewards.emoji if present + time left)
                activeLiveBookedSession?.let { liveItem ->
                    val liveSession = liveItem.session
                    val timeLeft = com.example.data.models.ScheduleRules.formatTimeLeft(
                        endAtMillis = liveSession.endAtMillis,
                        nowMillis = System.currentTimeMillis()
                    )
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = DelivioGreen,
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .clickable { onOpenSchedule() }
                            .testTag("live_session_bar")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (liveSession.rewards.hasRewards) {
                                    Text(
                                        text = liveSession.rewards.emoji,
                                        fontSize = 14.sp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = "Live session",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Text(
                                    text = liveSession.title.ifBlank { "Live session" },
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = timeLeft,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // In-App Admin Message Notification Banner (cleanly stacked below top bar)
                adminMessageBanner?.let { banner ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF1E293B),
                        shadowElevation = 10.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp)
                            .clickable {
                                viewModel.dismissAdminMessageBanner()
                                onOpenSupportChat(banner.ticketId)
                            }
                            .testTag("admin_message_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(DelivioGreen),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.SupportAgent, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Delivio Support",
                                    color = DelivioYellow,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = banner.messageText,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "View",
                                color = DelivioYellow,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            IconButton(
                                onClick = { viewModel.dismissAdminMessageBanner() },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                // Late Tip Banner
                lateTipBanner?.let { banner ->
                    LateTipBanner(
                        bannerInfo = banner,
                        currencySymbol = currencySymbol,
                        onDismiss = { viewModel.dismissLateTipBanner() },
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )
                }

                // Document Expiry Reminder Banner
                documentExpiryAlerts.firstOrNull()?.let { alert ->
                    DocumentExpiryBanner(
                        alert = alert,
                        onDismiss = { viewModel.dismissDocumentAlert(alert.document.type) },
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )
                }
            }

            // 3. Floating Map Controls: strictly constrained between top banner clearance and top of docked sheet
            val availableMiddleHeightDp = (screenHeight - topBannerClearanceDp - animatedSheetHeight - 14.dp).coerceAtLeast(0.dp)
            if (availableMiddleHeightDp >= 52.dp) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = topBannerClearanceDp,
                            bottom = animatedSheetHeight + 14.dp,
                            end = 16.dp
                        ),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    if (availableMiddleHeightDp >= 112.dp) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier.testTag("floating_map_controls")
                        ) {
                            if (!isTripActive && availableMiddleHeightDp >= 172.dp) {
                                Card(
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isBusyAreasVisible) DelivioYellow else MaterialTheme.colorScheme.surface
                                    ),
                                    border = BorderStroke(
                                        1.5.dp,
                                        if (isBusyAreasVisible) DelivioYellowDark else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                                    modifier = Modifier
                                        .defaultMinSize(minHeight = 48.dp)
                                        .clickable { viewModel.toggleBusyAreasOverlay() }
                                        .testTag("busy_areas_toggle")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Whatshot,
                                            contentDescription = "Busy areas",
                                            tint = if (isBusyAreasVisible) Color(0xFFC62828) else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = if (isBusyAreasVisible) "Busy areas (${busyGridCells.size})" else "Busy areas",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = if (isBusyAreasVisible) TextOnYellow else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }

                            CircularFloatingButton(
                                icon = Icons.Default.MyLocation,
                                contentDescription = "Locate",
                                onClick = { recenterTrigger += 1 },
                                testTag = "recenter_button"
                            )
                        }
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.testTag("floating_map_controls")
                        ) {
                            CircularFloatingButton(
                                icon = Icons.Default.MyLocation,
                                contentDescription = "Locate",
                                onClick = { recenterTrigger += 1 },
                                testTag = "recenter_button"
                            )
                        }
                    }
                }
            }

            // 4. Docked Order Bottom Sheet (COLLAPSED, EXPANDED) flush to bottom edge
            val isDriverLocFresh = remember(liveLoc, lastLocationMillis) {
                com.example.data.service.RiderLocationTracker.isLocationFresh()
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                OrderDetailsSheet(
                    batchStops = batchStops,
                    currentStop = currentStop,
                    activeOrders = activeOrders,
                    isOnline = isOnline,
                    walletBalance = walletBalance,
                    todayEarnings = todayEarnings,
                    todayTrips = todayTrips,
                    currencySymbol = currencySymbol,
                    supportPhone = supportPhone,
                    routeDistanceKm = routeDistanceKm,
                    routeEtaMinutes = routeEtaMinutes,
                    driverLocation = liveLoc,
                    isDriverLocationFresh = isDriverLocFresh,
                    sheetState = sheetState,
                    onSheetStateChange = { sheetState = it },
                    onGoOnlineClick = { viewModel.toggleOnline(context) },
                    onOnlineStatusClick = { showDispatchStatusSheet = true },
                    onAdvanceStep = { receiptTotal, orderId ->
                        viewModel.advanceCurrentOrderStep(receiptTotal, orderId)
                    },
                    onAdvanceStepWithApproval = { receiptTotal, overBudgetApproved, orderId ->
                        viewModel.advanceCurrentOrderStep(receiptTotal, orderId, overBudgetApproved)
                    },
                    onReleaseOrder = { reason ->
                        viewModel.releaseOrder(reason = reason, orderId = currentStop?.orderId)
                    },
                    onReleaseOrderWithType = { orderId, reportType, reason ->
                        viewModel.releaseOrder(reason = reason, reportType = reportType, orderId = orderId)
                    },
                    onReportProblem = { reason ->
                        currentStop?.orderId?.let { viewModel.reportProblem(it, reason) }
                    },
                    onCallClick = { _, phone -> viewModel.launchExternalDialer(phone) },
                    onChatClick = { order -> viewModel.openChatForOrder(order) },
                    hasUnreadChat = { orderId -> viewModel.hasUnreadChat(orderId) },
                    onFocusStop = { viewModel.focusStopOnMap(it) },
                    onOpenEarnings = onOpenEarnings,
                    onHeightChangedPx = { sheetHeightPx = it },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 5. Incoming Offer Dialog (Single or Double Booking bundle)
            val allowedIncomingBundle = incomingOfferBundle?.takeIf { bundle ->
                val currentRider = rider
                currentRider == null || bundle.orders.all { currentRider.isServiceAllowedByAdmin(it.serviceType) }
            }
            val allowedIncomingOffer = incomingOffer?.takeIf { offer ->
                val currentRider = rider
                currentRider == null || currentRider.isServiceAllowedByAdmin(offer.serviceType)
            }
            if (allowedIncomingBundle != null) {
                val bundle = allowedIncomingBundle
                IncomingOrderDialog(
                    bundle = bundle,
                    currencySymbol = currencySymbol,
                    soundEnabled = viewModel.soundEnabled.value,
                    vibrationEnabled = viewModel.vibrationEnabled.value,
                    onAccept = { viewModel.acceptIncomingOfferBundle(bundle) },
                    onDecline = { viewModel.declineIncomingOfferBundle(bundle) },
                    onMissed = { viewModel.missIncomingOfferBundle(bundle) },
                    onDisplayed = { viewModel.logOfferDisplayed(bundle) }
                )
            } else if (allowedIncomingOffer != null) {
                val offer = allowedIncomingOffer
                val fallbackBundle = remember(offer.id, offer.riderQuote) {
                    val timerSecs = if (
                        offer.serviceType.equals(OrderStepConstants.SERVICE_REQUEST, ignoreCase = true) ||
                        offer.isTaxi
                    ) 20 else 30
                    val quote = offer.riderQuote
                    val delivFee = quote?.deliveryFee ?: offer.pricing?.deliveryFee?.takeIf { it > 0.0 } ?: offer.deliveryFee
                    val pickupFee = quote?.pickupFee ?: 0.0
                    val commRate = quote?.commissionRate ?: 0.0
                    val commAmount = quote?.commissionAmount ?: 0.0
                    val netTotal = quote?.net ?: offer.estimatedEarnings
                    OfferBundle(
                        id = offer.id,
                        type = OfferType.SINGLE,
                        orders = listOf(offer),
                        firstPickupDistanceKm = quote?.pickupKm ?: offer.pickupDistanceKm,
                        totalTripDistanceKm = offer.deliveryDistanceKm,
                        pickupFee = pickupFee,
                        breakdowns = listOf(
                            OrderPayBreakdown(
                                orderId = offer.id,
                                orderNumber = offer.orderNumber,
                                rank = quote?.batchIndex ?: offer.batchIndex.coerceAtLeast(1),
                                deliveryFee = delivFee,
                                pickupFee = pickupFee,
                                commissionRate = commRate,
                                commissionAmount = commAmount,
                                tip = offer.tip,
                                gross = quote?.gross ?: (delivFee + pickupFee + offer.tip),
                                totalPay = netTotal,
                                isCod = offer.isCod,
                                codAmount = offer.cashAmountToCollect,
                                badge = offer.badge,
                                customerArea = offer.customerArea,
                                pickupKm = quote?.pickupKm ?: offer.pickupDistanceKm,
                                riderQuote = quote
                            )
                        ),
                        totalDriverPay = netTotal,
                        eligibleSince = System.currentTimeMillis(),
                        waveDelaySeconds = 0,
                        badge = offer.badge,
                        timerSeconds = timerSecs
                    )
                }
                IncomingOrderDialog(
                    bundle = fallbackBundle,
                    currencySymbol = currencySymbol,
                    soundEnabled = viewModel.soundEnabled.value,
                    vibrationEnabled = viewModel.vibrationEnabled.value,
                    onAccept = { viewModel.acceptIncomingOffer(offer.id) },
                    onDecline = { viewModel.declineIncomingOffer(offer.id, offer.serviceType) },
                    onMissed = { viewModel.missIncomingOffer(offer.id, offer.serviceType) },
                    onDisplayed = { viewModel.logOfferDisplayed(fallbackBundle) }
                )
            }

            // 6. Delivery Success Dialog
            completedOrder?.let { order ->
                DeliverySuccessDialog(
                    order = order,
                    currencySymbol = currencySymbol,
                    walletBalance = walletBalance,
                    onDismiss = {
                        viewModel.dismissDeliverySuccess()
                        val sType = order.serviceType.lowercase().trim()
                        if (sType in listOf(OrderStepConstants.SERVICE_FOOD, OrderStepConstants.SERVICE_MART, "mart")) {
                            viewModel.promptRateShop(order)
                        }
                    }
                )
            }

            // Rate The Shop Dialog
            rateShopOrder?.let { order ->
                RateTheShopDialog(
                    order = order,
                    onSubmit = { rating, comment ->
                        viewModel.submitShopReview(order, rating, comment)
                    },
                    onDismiss = { viewModel.dismissRateShop() }
                )
            }

            // Customer Order Chat Dialog
            activeChatOrder?.let { chatOrder ->
                CustomerOrderChatDialog(
                    order = chatOrder,
                    messages = activeChatMessages,
                    onSendMessage = { text, imageDataUri ->
                        viewModel.sendChatMessage(chatOrder, text, imageDataUri)
                    },
                    onDismiss = { viewModel.closeChat() }
                )
            }

            // Tier Celebration Dialog
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
                            Text("Congratulations! You unlocked the ${tier.name} tier this month!", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text("Completed ${tier.ridesRequired}+ rides this month. Reward of ${tier.coins} coins will be credited upon admin confirmation.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.dismissCelebration() },
                            colors = ButtonDefaults.buttonColors(containerColor = DelivioYellow, contentColor = TextOnYellow),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Awesome!", fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

            // Accept Order Error Dialog
            acceptOrderError?.let { err ->
                AcceptOrderErrorDialog(
                    errorDetails = err,
                    onDismiss = { viewModel.dismissAcceptOrderError() }
                )
            }

            // Headset Support Bottom Sheet
            if (showHeadsetMenuDialog) {
                HeadsetMenuSheet(
                    activeOrders = activeOrders,
                    focusedOrderId = currentStop?.orderId ?: currentOrder?.id,
                    onCreateSplitTicket = { order, shortReason, onComplete, onError ->
                        viewModel.createSupportTicket(
                            order = order,
                            reasonKey = "split_delivery",
                            reasonLabel = "Request split delivery",
                            splitItems = emptyList(),
                            title = "Request split delivery - ${order.displayOrderNumber}",
                            details = shortReason,
                            photosBase64 = emptyList(),
                            onSuccess = { ticketId ->
                                onComplete()
                                showHeadsetMenuDialog = false
                                onOpenSupportChat(ticketId)
                            },
                            onError = onError
                        )
                    },
                    onSelectStandardIssue = { orderId, reasonKey ->
                        if (!orderId.isNullOrBlank()) {
                            onOpenHelpCenter(orderId, reasonKey)
                        } else if (unresolvedTickets.isNotEmpty()) {
                            onOpenSupportChat(unresolvedTickets.first().id)
                        } else {
                            onOpenSupport()
                        }
                    },
                    onOpenReleaseDialog = { order, initialReportType ->
                        showHeadsetMenuDialog = false
                        pendingReleaseTarget = order to initialReportType
                    },
                    onDismiss = { showHeadsetMenuDialog = false }
                )
            }

            // Release Order Dialog triggered from Headset menu
            pendingReleaseTarget?.let { (targetOrder, initialReportType) ->
                ReleaseOrderDialog(
                    order = targetOrder,
                    stopName = currentStop?.name ?: targetOrder.displayPickupName,
                    initialReportType = initialReportType,
                    onConfirmRelease = { reportType, reason ->
                        pendingReleaseTarget = null
                        viewModel.releaseOrder(
                            reason = reason,
                            reportType = reportType,
                            orderId = targetOrder.id
                        )
                    },
                    onDismiss = { pendingReleaseTarget = null }
                )
            }

            // Dispatch Status Bottom Sheet (when tapping Online pill)
            if (showDispatchStatusSheet && isOnline) {
                DispatchStatusSheet(
                    onlineSinceMillis = onlineSinceMillis,
                    lastLocationMillis = lastLocationMillis,
                    enabledServices = enabledDispatchServices,
                    vehicleType = rider?.vehicle?.type ?: OrderStepConstants.VEHICLE_MOTORCYCLE,
                    listenerStatus = dispatchListenerStatus,
                    ordersSeenCount = ordersSeenCount,
                    badDataSkippedCount = badDataSkippedCount,
                    eligibleOrdersCount = eligibleOrdersCount,
                    skippedOrders = skippedOrderRecords,
                    lastTaxiDiagnostics = lastTaxiDiagnostics,
                    hasActiveOrders = activeOrders.isNotEmpty(),
                    onGoOffline = {
                        showDispatchStatusSheet = false
                        viewModel.toggleOnline(context)
                    },
                    onDismiss = { showDispatchStatusSheet = false }
                )
            }
        }
    }
}

@Composable
fun MainMapTopBar(
    isOnline: Boolean = false,
    hasUnreadAdminMessage: Boolean = false,
    hasUnresolvedTicket: Boolean = false,
    onOpenDrawer: () -> Unit,
    onStatusPillClick: () -> Unit = {},
    onHeadsetClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("main_top_bar"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularFloatingButton(
            icon = Icons.Default.Menu,
            contentDescription = "Menu",
            onClick = onOpenDrawer,
            testTag = "hamburger_menu_button"
        )

        OnlineOfflinePill(
            isOnline = isOnline,
            onClick = onStatusPillClick
        )

        Box(contentAlignment = Alignment.TopEnd) {
            CircularFloatingButton(
                icon = Icons.Default.Headphones,
                contentDescription = "Support",
                onClick = onHeadsetClick,
                testTag = "support_headset_button"
            )
            if (hasUnreadAdminMessage) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(DelivioRed)
                        .border(1.5.dp, Color.White, CircleShape)
                )
            } else if (hasUnresolvedTicket) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFA000))
                        .border(1.5.dp, Color.White, CircleShape)
                )
            }
        }
    }
}
