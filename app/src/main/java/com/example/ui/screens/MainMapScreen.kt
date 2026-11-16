package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.constants.OrderStepConstants
import com.example.data.models.OfferBundle
import com.example.data.models.OfferType
import com.example.data.models.OrderPayBreakdown
import com.example.data.models.OsrmManeuver
import com.example.data.models.OsrmRouteResult
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.viewmodel.RiderViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainMapScreen(
    viewModel: RiderViewModel,
    onOpenDrawer: () -> Unit,
    onOpenSupport: () -> Unit,
    onOpenHelpCenter: (String?, String?) -> Unit = { _, _ -> },
    onOpenSupportChat: (String) -> Unit = {},
    onOpenEarnings: () -> Unit = {},
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
    val driverSpeedKmh by viewModel.speedKmh.collectAsState()

    val incomingOffer by viewModel.incomingOffer.collectAsState()
    val incomingOfferBundle by viewModel.incomingOfferBundle.collectAsState()
    val deliveredOrders by viewModel.deliveredOrders.collectAsState()
    val currencySymbol by viewModel.currencySymbol.collectAsState()
    val supportPhone by viewModel.supportPhone.collectAsState()
    val activeCall by viewModel.activeCall.collectAsState()
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
    val lastLocationMillis by viewModel.lastLocationTimeMillis.collectAsState()
    val enabledDispatchServices by viewModel.enabledDispatchServices.collectAsState()
    val dispatchListenerStatus by viewModel.dispatchListenerStatus.collectAsState()
    val ordersSeenCount by viewModel.ordersSeenCount.collectAsState()
    val badDataSkippedCount by viewModel.badDataSkippedCount.collectAsState()
    val eligibleOrdersCount by viewModel.eligibleOrdersCount.collectAsState()
    val skippedOrderRecords by viewModel.skippedOrderRecords.collectAsState()
    val lastTaxiDiagnostics by viewModel.lastTaxiDiagnostics.collectAsState()
    var showHeadsetMenuDialog by remember { mutableStateOf(false) }
    var showDispatchStatusSheet by remember { mutableStateOf(false) }

    LaunchedEffect(isOnline) {
        if (!isOnline) {
            showDispatchStatusSheet = false
        }
    }

    val riderLat = liveLoc?.first ?: 9.7392
    val riderLng = liveLoc?.second ?: 118.7353

    var recenterTrigger by remember { mutableIntStateOf(0) }
    var isSatelliteLayer by remember { mutableStateOf(false) }
    var sheetState by remember { mutableStateOf(OrderSheetState.PEEK) }
    var routeResult by remember { mutableStateOf<OsrmRouteResult?>(null) }
    var routeDistanceKm by remember { mutableDoubleStateOf(0.0) }
    var routeEtaMinutes by remember { mutableIntStateOf(0) }

    var sheetHeightPx by remember { mutableIntStateOf(0) }
    var topOverlayHeightPx by remember { mutableIntStateOf(0) }

    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarMsg by viewModel.snackbarMessage.collectAsState()

    // Reset sheet back to PEEK when active stop changes
    LaunchedEffect(currentStop?.orderId, currentStop?.stopNumber) {
        if (currentStop != null) {
            sheetState = OrderSheetState.PEEK
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
    val walletCashOnHand = deliveredOrders.filter { it.isCod }.sumOf { it.codCollected } - (rider?.cashRemitted ?: 0.0)

    val fallbackSheetDp = if (activeOrders.isEmpty() || currentStop == null) 172.dp else 210.dp
    val measuredSheetDp = if (sheetHeightPx > 0) with(density) { sheetHeightPx.toDp() } else fallbackSheetDp
    val animatedSheetHeight by animateDpAsState(
        targetValue = measuredSheetDp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "main_sheet_height"
    )

    val topBannerClearanceDp = if (topOverlayHeightPx > 0) {
        with(density) { topOverlayHeightPx.toDp() } + 8.dp
    } else if (activeOrders.isNotEmpty() && currentStop != null) {
        86.dp
    } else {
        76.dp
    }

    val sheetHeightFraction = (animatedSheetHeight.value / screenHeight.value).coerceIn(0.10f, 0.72f)
    val isTripActive = activeOrders.isNotEmpty() && currentStop != null

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
                isSatelliteLayer = isSatelliteLayer,
                showBusyAreas = isBusyAreasVisible && !isTripActive,
                busyGridCells = busyGridCells,
                topBannerPaddingDp = topBannerClearanceDp,
                bottomAttributionPaddingDp = animatedSheetHeight,
                onRouteCalculated = { result ->
                    routeResult = result
                    routeDistanceKm = result.currentLegDistanceKm
                    routeEtaMinutes = result.currentLegEtaMinutes
                },
                modifier = Modifier.fillMaxSize()
            )

            // 2. TOP OVERLAY COLUMN: Top Navigation Card / Idle Bar + Stacked Notification Banners (zero overlap)
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .onSizeChanged { topOverlayHeightPx = it.height },
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val activeStop = currentStop
                if (isTripActive && activeStop != null) {
                    val maneuver = routeResult?.nextManeuver
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                            .testTag("top_navigation_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Maneuver Info: Arrow, Distance, Street Name
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFF1E88E5),
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = getManeuverIcon(maneuver),
                                            contentDescription = maneuver?.instruction ?: "Maneuver",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = maneuver?.distanceText ?: "${String.format(Locale.US, "%.1f", routeDistanceKm)} km",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = maneuver?.instruction ?: "Head to Stop ${activeStop.stopNumber}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Speed Chip + Support Headset (>= 48.dp touch target)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Text(
                                        text = "$driverSpeedKmh km/h",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                    )
                                }

                                Box(contentAlignment = Alignment.TopEnd) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .clickable { showHeadsetMenuDialog = true }
                                            .testTag("nav_support_headset_button")
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Headphones,
                                                contentDescription = "Support",
                                                tint = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    if (hasUnreadAdminMessage) {
                                        Box(
                                            modifier = Modifier
                                                .size(11.dp)
                                                .clip(CircleShape)
                                                .background(DelivioRed)
                                                .border(1.5.dp, Color.White, CircleShape)
                                        )
                                    } else if (hasUnresolvedTicket) {
                                        Box(
                                            modifier = Modifier
                                                .size(11.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFFFA000))
                                                .border(1.5.dp, Color.White, CircleShape)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // IDLE TOP BAR: Hamburger Menu, Online/Offline Pill, Support Headset
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
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
                            onClick = {
                                if (isOnline) {
                                    showDispatchStatusSheet = true
                                } else {
                                    viewModel.toggleOnline(context)
                                }
                            }
                        )

                        Box(contentAlignment = Alignment.TopEnd) {
                            CircularFloatingButton(
                                icon = Icons.Default.Headphones,
                                contentDescription = "Support",
                                onClick = { showHeadsetMenuDialog = true },
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

            // 3. Floating Map Controls: Busy areas (hidden during active trip) + Locate + Layers, offset cleanly above docked sheet
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(end = 16.dp, bottom = animatedSheetHeight + 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.End
            ) {
                if (!isTripActive) {
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

                CircularFloatingButton(
                    icon = Icons.Default.Layers,
                    contentDescription = "Toggle Map Layers",
                    onClick = { isSatelliteLayer = !isSatelliteLayer },
                    testTag = "layers_button"
                )
            }

            // 4. Docked Order Bottom Sheet (PEEK, HALF, FULL) above navigation bar
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                OrderDetailsSheet(
                    batchStops = batchStops,
                    currentStop = currentStop,
                    activeOrders = activeOrders,
                    isOnline = isOnline,
                    walletBalance = walletCashOnHand.coerceAtLeast(0.0),
                    todayEarnings = todayEarnings,
                    todayTrips = todayTrips,
                    currencySymbol = currencySymbol,
                    supportPhone = supportPhone,
                    routeDistanceKm = routeDistanceKm,
                    routeEtaMinutes = routeEtaMinutes,
                    sheetState = sheetState,
                    onSheetStateChange = { sheetState = it },
                    onGoOnlineClick = { viewModel.toggleOnline(context) },
                    onAdvanceStep = { receiptTotal, orderId ->
                        viewModel.advanceCurrentOrderStep(receiptTotal, orderId)
                    },
                    onReleaseOrder = { reason -> viewModel.releaseOrder(reason) },
                    onReportProblem = { reason ->
                        currentStop?.orderId?.let { viewModel.reportProblem(it, reason) }
                    },
                    onCallClick = { name, phone -> viewModel.startCall(name, phone) },
                    onChatClick = { order -> viewModel.openChatForOrder(order) },
                    hasUnreadChat = { orderId -> viewModel.hasUnreadChat(orderId) },
                    onHeadsetClick = { showHeadsetMenuDialog = true },
                    onFocusStop = { viewModel.focusStopOnMap(it) },
                    onOpenEarnings = onOpenEarnings,
                    onHeightChangedPx = { sheetHeightPx = it },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 5. Incoming Offer Dialog (Single or Double Booking bundle)
            if (incomingOfferBundle != null) {
                val bundle = incomingOfferBundle!!
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
            } else if (incomingOffer != null) {
                val offer = incomingOffer!!
                val fallbackBundle = remember(offer.id) {
                    val timerSecs = if (
                        offer.serviceType.equals(OrderStepConstants.SERVICE_REQUEST, ignoreCase = true) ||
                        offer.isTaxi
                    ) 20 else 30
                    OfferBundle(
                        id = offer.id,
                        type = OfferType.SINGLE,
                        orders = listOf(offer),
                        firstPickupDistanceKm = offer.pickupDistanceKm,
                        totalTripDistanceKm = offer.deliveryDistanceKm,
                        pickupFee = 0.0,
                        breakdowns = listOf(
                            OrderPayBreakdown(
                                orderId = offer.id,
                                orderNumber = offer.orderNumber,
                                rank = 1,
                                deliveryFee = offer.deliveryFee,
                                pickupFee = 0.0,
                                tip = offer.tip,
                                totalPay = offer.estimatedEarnings,
                                isCod = offer.isCod,
                                codAmount = offer.cashAmountToCollect,
                                badge = offer.badge,
                                customerArea = offer.customerArea
                            )
                        ),
                        totalDriverPay = offer.estimatedEarnings,
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

            // In-App Call Dialog
            activeCall?.let { call ->
                InAppCallDialog(
                    callInfo = call,
                    onEndCall = { viewModel.endCall() },
                    onOpenSystemDialer = { phone -> viewModel.launchExternalDialer(phone) }
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
                    onDismiss = { showHeadsetMenuDialog = false }
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

private fun getManeuverIcon(maneuver: OsrmManeuver?): ImageVector {
    if (maneuver == null) return Icons.Default.Navigation
    val modifier = maneuver.modifier.lowercase()
    val type = maneuver.type.lowercase()
    return when {
        modifier.contains("u-turn") || modifier.contains("uturn") -> Icons.AutoMirrored.Filled.Undo
        modifier.contains("sharp right") || modifier.contains("right") -> Icons.Default.TurnRight
        modifier.contains("sharp left") || modifier.contains("left") -> Icons.Default.TurnLeft
        modifier.contains("slight right") -> Icons.Default.TurnSlightRight
        modifier.contains("slight left") -> Icons.Default.TurnSlightLeft
        type.contains("roundabout") || type.contains("rotary") -> Icons.Default.RoundaboutRight
        type.contains("arrive") -> Icons.Default.Place
        else -> Icons.Default.Straight
    }
}
