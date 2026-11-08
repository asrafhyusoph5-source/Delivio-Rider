package com.example.ui.screens

import android.Manifest
import android.content.Context
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onOpenHelpCenter: (String?) -> Unit = {},
    onOpenSupportChat: (String) -> Unit = {},
    onOpenEarnings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
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
    val hasOrderAtMerchant by viewModel.hasOrderAtMerchant.collectAsState()
    val adminMessageBanner by viewModel.adminMessageBanner.collectAsState()
    val isBusyAreasVisible by viewModel.isBusyAreasOverlayVisible.collectAsState()
    val busyGridCells by viewModel.busyGridCells.collectAsState()
    val documentExpiryAlerts by viewModel.documentExpiryAlerts.collectAsState()
    var showHeadsetMenuDialog by remember { mutableStateOf(false) }

    val riderLat = liveLoc?.first ?: 9.7392
    val riderLng = liveLoc?.second ?: 118.7353

    var recenterTrigger by remember { mutableIntStateOf(0) }
    var isSatelliteLayer by remember { mutableStateOf(false) }
    var sheetState by remember { mutableStateOf(OrderSheetState.COMPACT) }
    var routeResult by remember { mutableStateOf<OsrmRouteResult?>(null) }
    var routeDistanceKm by remember { mutableDoubleStateOf(0.0) }
    var routeEtaMinutes by remember { mutableIntStateOf(0) }

    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarMsg by viewModel.snackbarMessage.collectAsState()

    // Reset to Compact whenever current stop changes
    LaunchedEffect(currentStop?.orderId, currentStop?.stopNumber) {
        if (currentStop != null) {
            sheetState = OrderSheetState.COMPACT
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

    val targetSheetHeight = if (activeOrders.isEmpty() || currentStop == null) {
        185.dp
    } else {
        when (sheetState) {
            OrderSheetState.HIDDEN -> 64.dp
            OrderSheetState.COMPACT -> (screenHeight * 0.32f).coerceIn(225.dp, 280.dp)
            OrderSheetState.EXPANDED -> (screenHeight * 0.65f).coerceAtLeast(420.dp)
        }
    }

    val animatedSheetHeight by animateDpAsState(
        targetValue = targetSheetHeight,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "main_sheet_height"
    )

    val sheetHeightFraction = (targetSheetHeight.value / screenHeight.value).coerceIn(0.08f, 0.65f)

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
            // 1. Map fills the screen behind the sheet, pannable & zoomable in all sheet states
            OsmMapView(
                riderLat = riderLat,
                riderLng = riderLng,
                driverHeading = driverHeading,
                batchStops = batchStops,
                focusedStop = focusedStop,
                recenterTrigger = recenterTrigger,
                sheetHeightFraction = sheetHeightFraction,
                isSatelliteLayer = isSatelliteLayer,
                showBusyAreas = isBusyAreasVisible,
                busyGridCells = busyGridCells,
                onRouteCalculated = { result ->
                    routeResult = result
                    routeDistanceKm = result.currentLegDistanceKm
                    routeEtaMinutes = result.currentLegEtaMinutes
                },
                modifier = Modifier.fillMaxSize()
            )

            val activeStop = currentStop

            // 2. TOP OVERLAY:
            // When trip is active: Navigation Card (Next maneuver, distance, street name, speed chip, compass, recenter)
            // When idle: Hamburger, Online/offline pill, Support button
            if (activeOrders.isNotEmpty() && activeStop != null) {
                val maneuver = routeResult?.nextManeuver
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
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
                                color = Color(0xFF1E88E5), // Bright Blue
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
                            Column {
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

                        // Top Controls: Speed Chip, Compass, Recenter Button
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Speed Chip
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
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                )
                            }

                            // Compass
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable { recenterTrigger += 1 }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Explore,
                                        contentDescription = "Compass",
                                        tint = DelivioRed,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            // Recenter Button
                            Surface(
                                shape = CircleShape,
                                color = DelivioYellow,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable { recenterTrigger += 1 }
                                    .testTag("recenter_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.MyLocation,
                                        contentDescription = "Recenter",
                                        tint = TextOnYellow,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }

                            // Navigation Support Headset button with badge
                            Box(contentAlignment = Alignment.TopEnd) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clickable {
                                            if (unresolvedTickets.isNotEmpty()) {
                                                onOpenSupportChat(unresolvedTickets.first().id)
                                            } else if (hasOrderAtMerchant) {
                                                showHeadsetMenuDialog = true
                                            } else {
                                                if (!supportPhone.isNullOrBlank()) {
                                                    viewModel.contactSupport(context)
                                                } else {
                                                    onOpenSupport()
                                                }
                                            }
                                        }
                                        .testTag("nav_support_headset_button")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Headphones,
                                            contentDescription = "Support",
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                }
                                if (hasUnreadAdminMessage) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(DelivioRed)
                                            .border(1.5.dp, Color.White, CircleShape)
                                    )
                                } else if (hasUnresolvedTicket) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
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
                // IDLE TOP BAR: Menu, Online/Offline Pill, Support
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
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

                    OnlineOfflinePill(
                        isOnline = isOnline,
                        onClick = { viewModel.toggleOnline(context) }
                    )

                    Box(contentAlignment = Alignment.TopEnd) {
                        CircularFloatingButton(
                            icon = Icons.Default.Headphones,
                            contentDescription = "Support",
                            onClick = {
                                if (unresolvedTickets.isNotEmpty()) {
                                    onOpenSupportChat(unresolvedTickets.first().id)
                                } else if (hasOrderAtMerchant) {
                                    showHeadsetMenuDialog = true
                                } else {
                                    if (!supportPhone.isNullOrBlank()) {
                                        viewModel.contactSupport(context)
                                    } else {
                                        onOpenSupport()
                                    }
                                }
                            },
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

            // 3. Floating Map Controls: Busy areas toggle & satellite/streets layer toggle
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = animatedSheetHeight + (if (currentStop != null) 68.dp else 16.dp)),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.End
            ) {
                // Demand Heat Map Toggle ("Busy areas")
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isBusyAreasVisible) DelivioYellow else MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.5.dp, if (isBusyAreasVisible) DelivioYellowDark else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    modifier = Modifier
                        .clickable { viewModel.toggleBusyAreasOverlay() }
                        .testTag("busy_areas_toggle")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
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

                CircularFloatingButton(
                    icon = Icons.Default.Layers,
                    contentDescription = "Toggle Map Layers",
                    onClick = { isSatelliteLayer = !isSatelliteLayer },
                    testTag = "layers_button"
                )
            }

            // 4. BOTTOM BAR: ETA clock time, Next stop's name, Navigate button (geo: intent)
            if (activeOrders.isNotEmpty() && activeStop != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = animatedSheetHeight + 10.dp)
                        .testTag("bottom_navigation_bar")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = "ETA Clock",
                                tint = DelivioYellowDark,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = routeResult?.etaClockTime ?: "ETA",
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("•", color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Stop ${activeStop.stopNumber}: ${activeStop.name}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Button(
                            onClick = {
                                val geoUri = Uri.parse("geo:${activeStop.lat},${activeStop.lng}?q=${activeStop.lat},${activeStop.lng}(${Uri.encode(activeStop.name)})")
                                val navIntent = Intent(Intent.ACTION_VIEW, geoUri)
                                try {
                                    context.startActivity(Intent.createChooser(navIntent, "Navigate with"))
                                } catch (_: Exception) {
                                    viewModel.showSnackbar("No navigation app installed")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DelivioYellow,
                                contentColor = TextOnYellow
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Navigate", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            // 5. Draggable Order Bottom Sheet (Hidden, Compact, Expanded)
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
                    onFocusStop = { viewModel.focusStopOnMap(it) },
                    onOpenEarnings = onOpenEarnings,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 6. Incoming Offer Dialog (Single or Double Booking bundle)
            if (incomingOfferBundle != null) {
                IncomingOrderDialog(
                    bundle = incomingOfferBundle!!,
                    currencySymbol = currencySymbol,
                    soundEnabled = viewModel.soundEnabled.value,
                    vibrationEnabled = viewModel.vibrationEnabled.value,
                    onAccept = { viewModel.acceptIncomingOfferBundle(incomingOfferBundle!!) },
                    onDecline = { viewModel.declineIncomingOfferBundle(incomingOfferBundle!!) },
                    onMissed = { viewModel.missIncomingOfferBundle(incomingOfferBundle!!) }
                )
            } else if (incomingOffer != null) {
                val offer = incomingOffer!!
                IncomingOrderDialog(
                    order = offer,
                    currencySymbol = currencySymbol,
                    soundEnabled = viewModel.soundEnabled.value,
                    vibrationEnabled = viewModel.vibrationEnabled.value,
                    onAccept = { viewModel.acceptIncomingOffer(offer.id) },
                    onDecline = { viewModel.declineIncomingOffer(offer.id, offer.serviceType) },
                    onMissed = { viewModel.missIncomingOffer(offer.id, offer.serviceType) }
                )
            }

            // 7. Delivery Success Dialog
            completedOrder?.let { order ->
                DeliverySuccessDialog(
                    order = order,
                    currencySymbol = currencySymbol,
                    onDismiss = {
                        viewModel.dismissDeliverySuccess()
                        val sType = order.serviceType.lowercase().trim()
                        if (sType in listOf("food", "convenience", "instant")) {
                            viewModel.promptRateShop(order)
                        }
                    }
                )
            }

            // Rate The Shop Dialog (optional feedback for food/convenience deliveries)
            rateShopOrder?.let { order ->
                RateTheShopDialog(
                    order = order,
                    onSubmit = { rating, comment ->
                        viewModel.submitShopReview(order, rating, comment)
                    },
                    onDismiss = { viewModel.dismissRateShop() }
                )
            }

            // Late Tip Banner
            lateTipBanner?.let { banner ->
                LateTipBanner(
                    bannerInfo = banner,
                    currencySymbol = currencySymbol,
                    onDismiss = { viewModel.dismissLateTipBanner() },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(top = 70.dp, start = 16.dp, end = 16.dp)
                )
            }

            // Document Expiry Reminder Banner (30, 14, 7 days before and Expired after)
            documentExpiryAlerts.firstOrNull()?.let { alert ->
                DocumentExpiryBanner(
                    alert = alert,
                    onDismiss = { viewModel.dismissDocumentAlert(alert.document.type) },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(top = if (lateTipBanner != null) 140.dp else 70.dp, start = 16.dp, end = 16.dp)
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

            // Headset Assistance Dialog (Problem with booking vs Contact support)
            if (showHeadsetMenuDialog) {
                AlertDialog(
                    onDismissRequest = { showHeadsetMenuDialog = false },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SupportAgent, contentDescription = null, tint = DelivioGreen)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Driver Assistance", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "How can Delivio assist you with your current booking?",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Option 1: Problem with the booking
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = DelivioYellowLight.copy(alpha = 0.5f)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, DelivioYellowDark),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showHeadsetMenuDialog = false
                                        onOpenHelpCenter(currentStop?.orderId)
                                    }
                                    .testTag("menu_problem_with_booking")
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.ReportProblem, contentDescription = null, tint = DelivioRed, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Problem with the booking",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = TextOnYellow
                                        )
                                        Text(
                                            text = "Bag fit, too big, wait time, closed shop, split",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // Option 2: Contact Delivio Support
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showHeadsetMenuDialog = false
                                        if (!supportPhone.isNullOrBlank()) {
                                            viewModel.contactSupport(context)
                                        } else {
                                            onOpenSupport()
                                        }
                                    }
                                    .testTag("menu_contact_delivio_support")
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Phone, contentDescription = null, tint = DelivioGreen, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Contact Delivio Support",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "Call 24/7 hotline or view Driver FAQ",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { showHeadsetMenuDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // In-App Admin Message Notification Banner
            adminMessageBanner?.let { banner ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF1E293B),
                    shadowElevation = 10.dp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(0.92f)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(top = 10.dp)
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
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                        }
                    }
                }
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
