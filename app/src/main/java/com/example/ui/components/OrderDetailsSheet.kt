package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.constants.BatchConstants
import com.example.data.constants.OrderStepConstants
import com.example.data.models.*
import com.example.ui.theme.*
import com.example.util.PhoneUtils
import java.util.Locale

enum class OrderSheetState {
    COLLAPSED,
    EXPANDED
}

@Composable
fun OrderDetailsSheet(
    batchStops: List<RouteStop>,
    currentStop: RouteStop?,
    activeOrders: List<Order>,
    isOnline: Boolean,
    walletBalance: Double,
    todayEarnings: Double,
    todayTrips: Int,
    currencySymbol: String,
    supportPhone: String?,
    routeDistanceKm: Double = 0.0,
    routeEtaMinutes: Int = 0,
    driverLocation: Pair<Double, Double>? = null,
    isDriverLocationFresh: Boolean = false,
    sheetState: OrderSheetState = OrderSheetState.EXPANDED,
    onSheetStateChange: (OrderSheetState) -> Unit = {},
    onGoOnlineClick: () -> Unit,
    onOnlineStatusClick: () -> Unit = {},
    onAdvanceStep: (Double?, String?) -> Unit,
    onAdvanceStepWithApproval: ((Double?, Boolean, String?) -> Unit)? = null,
    onReleaseOrder: (String) -> Unit = {},
    onReleaseOrderWithType: ((orderId: String, reportType: String, reason: String) -> Unit)? = null,
    onReportProblem: (String) -> Unit,
    onCallClick: (String, String) -> Unit,
    onChatClick: (Order) -> Unit = {},
    hasUnreadChat: (String) -> Boolean = { false },
    onFocusStop: (RouteStop) -> Unit = {},
    onOpenEarnings: () -> Unit = {},
    onHeightChangedPx: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    fun transitionState(target: OrderSheetState) {
        if (target == sheetState) return
        try {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        } catch (_: Throwable) {
        }
        onSheetStateChange(target)
    }

    fun triggerAdvance(receiptTotal: Double? = null, overBudgetApproved: Boolean = false, orderId: String? = null) {
        if (onAdvanceStepWithApproval != null) {
            onAdvanceStepWithApproval(receiptTotal, overBudgetApproved, orderId)
        } else {
            onAdvanceStep(receiptTotal, orderId)
        }
    }

    var showProblemDialog by remember { mutableStateOf(false) }
    var problemReason by remember { mutableStateOf("Merchant delay / waiting") }
    var showReleaseOrderDialog by remember(currentStop?.orderId) { mutableStateOf(false) }

    // Dialogs for Slide / Press confirmations
    var showItemChecklistDialog by remember { mutableStateOf(false) }
    var showCodConfirmDialog by remember { mutableStateOf(false) }
    var farArrivalDialogMessage by remember(currentStop?.orderId, currentStop?.order?.riderStep, currentStop?.order?.status) {
        mutableStateOf<String?>(null)
    }
    var parcelCashDialogStage by remember(currentStop?.orderId, currentStop?.order?.riderStep, currentStop?.order?.status) {
        mutableStateOf<ParcelStepStage?>(null)
    }
    var showTripDetails by remember(currentStop?.orderId) {
        mutableStateOf(false)
    }
    var showMoreMenu by remember(currentStop?.orderId) {
        mutableStateOf(false)
    }
    var pabiliReceiptInput by remember(currentStop?.orderId) {
        val initialPaid = currentStop?.order?.resolvedPaidOnMerchant ?: 0.0
        mutableStateOf(
            if (initialPaid > 0.0) formatPaymentAmount(initialPaid) else ""
        )
    }

    var selectedStopKey by remember(currentStop?.orderId, currentStop?.type) {
        mutableStateOf(currentStop?.let { "${it.orderId}_${it.type.name}" } ?: "")
    }
    val selectedStop = remember(batchStops, currentStop, selectedStopKey) {
        batchStops.firstOrNull { "${it.orderId}_${it.type.name}" == selectedStopKey } ?: currentStop
    }

    var showEarningsDetails by remember(currentStop?.orderId, currentStop?.stopNumber) {
        mutableStateOf(false)
    }

    // Swipe up/down gesture with velocity between COLLAPSED <-> EXPANDED
    val dragModifier = Modifier.pointerInput(sheetState) {
        val velocityTracker = VelocityTracker()
        var cumulativeDrag = 0f
        var transitioned = false
        detectVerticalDragGestures(
            onDragStart = {
                velocityTracker.resetTracking()
                cumulativeDrag = 0f
                transitioned = false
            },
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                velocityTracker.addPosition(change.uptimeMillis, change.position)
                if (transitioned) return@detectVerticalDragGestures
                cumulativeDrag += dragAmount
                if (sheetState == OrderSheetState.COLLAPSED && cumulativeDrag < -36f) {
                    transitioned = true
                    transitionState(OrderSheetState.EXPANDED)
                } else if (sheetState == OrderSheetState.EXPANDED && cumulativeDrag > 36f) {
                    transitioned = true
                    transitionState(OrderSheetState.COLLAPSED)
                }
            },
            onDragEnd = {
                if (!transitioned) {
                    val velocityY = velocityTracker.calculateVelocity().y
                    if (sheetState == OrderSheetState.COLLAPSED && (cumulativeDrag < -20f || velocityY < -350f)) {
                        transitionState(OrderSheetState.EXPANDED)
                    } else if (sheetState == OrderSheetState.EXPANDED && (cumulativeDrag > 20f || velocityY > 350f)) {
                        transitionState(OrderSheetState.COLLAPSED)
                    }
                }
            }
        )
    }

    val cardMaxHeight = when {
        activeOrders.isEmpty() || currentStop == null -> (screenHeight * 0.45f).coerceAtLeast(240.dp)
        sheetState == OrderSheetState.COLLAPSED -> (screenHeight * 0.25f).coerceAtLeast(160.dp)
        else -> (screenHeight * 0.85f).coerceAtLeast(360.dp)
    }

    Card(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .heightIn(max = cardMaxHeight)
            .animateContentSize(animationSpec = tween(durationMillis = 250))
            .onSizeChanged { onHeightChangedPx(it.height) }
            .testTag("order_details_sheet")
    ) {
        if (activeOrders.isEmpty() || currentStop == null) {
            // ================= IDLE STATE (WRAPPED HEIGHT, DOCKED FLUSH TO BOTTOM) =================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(44.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isOnline) "Ready for Bookings" else "You are Offline",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isOnline) "Waiting for incoming delivery offers..." else "Go online to start receiving offers",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (!isOnline) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = onGoOnlineClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DelivioGreen,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("go_online_button")
                        ) {
                            Text("Go Online", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(12.dp))
                        OutlinedButton(
                            onClick = onOnlineStatusClick,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, DelivioGreen.copy(alpha = 0.55f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = DelivioGreen.copy(alpha = 0.12f),
                                contentColor = DelivioGreenDark
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("idle_online_status_button")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(DelivioGreen)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Online", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onOpenEarnings() }
                        .padding(vertical = 4.dp)
                        .testTag("idle_earnings_row"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = DelivioYellowLight,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.AccountBalanceWallet,
                                    contentDescription = "Wallet",
                                    tint = DelivioYellowDark,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Today's Net Earnings",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$currencySymbol${String.format("%.2f", todayEarnings)} ($todayTrips ${if (todayTrips == 1) "trip" else "trips"})",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Text(
                        text = "View Earnings →",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = DelivioYellowDark
                    )
                }
            }
        } else {
            // ================= ACTIVE TRIP / BATCH (COLLAPSED OR EXPANDED) =================
            when (sheetState) {
                OrderSheetState.COLLAPSED -> {
                    CollapsedOrderSheetContent(
                        stopName = currentStop.name,
                        dragModifier = dragModifier,
                        onExpand = { transitionState(OrderSheetState.EXPANDED) }
                    )
                }

                OrderSheetState.EXPANDED -> {
                    ExpandedOrderSheetContent(
                        batchStops = batchStops,
                        currentStop = currentStop,
                        selectedStop = selectedStop ?: currentStop,
                        activeOrders = activeOrders,
                        currencySymbol = currencySymbol,
                        routeDistanceKm = routeDistanceKm,
                        routeEtaMinutes = routeEtaMinutes,
                        showEarningsDetails = showEarningsDetails,
                        dragModifier = dragModifier,
                        onToggleEarningsDetails = { showEarningsDetails = !showEarningsDetails },
                        onSelectStop = { stop ->
                            selectedStopKey = "${stop.orderId}_${stop.type.name}"
                            onFocusStop(stop)
                        },
                        onNavigateClick = { launchExternalNavigation(context, currentStop) },
                        onTripDetailsClick = {
                            showTripDetails = true
                        },
                        onCallClick = { callName, cleanedPhone ->
                            if (cleanedPhone.isNotBlank()) {
                                onCallClick(callName, cleanedPhone)
                            } else {
                                Toast.makeText(context, "No phone number", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onChatClick = { order ->
                            if (OrderChatConstants.isInstantOrder(order.serviceType)) {
                                Toast.makeText(context, "Chat is not available for instant requests", Toast.LENGTH_SHORT).show()
                            } else {
                                onChatClick(order)
                            }
                        },
                        hasUnreadChat = hasUnreadChat,
                        onMoreClick = { showMoreMenu = true },
                        onCollapse = { transitionState(OrderSheetState.COLLAPSED) },
                        onReportProblemClick = { showProblemDialog = true },
                        onReleaseOrderClick = { showReleaseOrderDialog = true },
                        onTaxiPressAction = {
                            val stage = currentStop.order.taxiStepStage
                            val needsFarCheck = stage == TaxiStepStage.TO_PICKUP ||
                                stage == TaxiStepStage.TO_DROPOFF ||
                                stage == TaxiStepStage.TO_EXTRA_STOP
                            val isFar = needsFarCheck &&
                                isDriverLocationFresh &&
                                isDriverFarFromTarget(driverLocation, currentStop.lat, currentStop.lng)
                            if (isFar) {
                                farArrivalDialogMessage = if (stage == TaxiStepStage.TO_PICKUP) {
                                    "You seem far from the pickup point. Mark as arrived anyway?"
                                } else {
                                    "You seem far from the drop-off point. Mark as arrived anyway?"
                                }
                            } else {
                                triggerAdvance(null, false, currentStop.orderId)
                            }
                        },
                        onPabiliPressAction = {
                            triggerAdvance(null, false, currentStop.orderId)
                        },
                        onParcelPressAction = {
                            triggerAdvance(null, false, currentStop.orderId)
                        },
                        onFoodPressAction = {
                            triggerAdvance(null, false, currentStop.orderId)
                        },
                        onSlideConfirmed = {
                            handleSlideAction(
                                currentStop = currentStop,
                                onOpenChecklist = { showItemChecklistDialog = true },
                                onOpenCodConfirm = { showCodConfirmDialog = true },
                                onOpenParcelCash = { stage -> parcelCashDialogStage = stage },
                                onAdvanceDirect = { triggerAdvance(null, false, currentStop.orderId) }
                            )
                        }
                    )
                }
            }
        }
    }

    // Far arrival confirmation dialog (Taxi only when > 300 m from target point)
    val pendingFarMessage = farArrivalDialogMessage
    if (pendingFarMessage != null && currentStop != null) {
        AlertDialog(
            onDismissRequest = { farArrivalDialogMessage = null },
            title = {
                Text("Confirm Arrival", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(pendingFarMessage, fontSize = 14.sp)
            },
            confirmButton = {
                Button(
                    onClick = {
                        farArrivalDialogMessage = null
                        triggerAdvance(null, false, currentStop.orderId)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DelivioYellow,
                        contentColor = TextOnYellow
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("far_arrival_yes_button")
                ) {
                    Text("Yes", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { farArrivalDialogMessage = null },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("far_arrival_cancel_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Item Checklist Dialog (for Slide to confirm Picked Up at Merchant/Vendor — never shown for Taxi or Parcel)
    if (showItemChecklistDialog && currentStop != null && !currentStop.order.isTaxi && !currentStop.order.isParcel) {
        ItemChecklistDialog(
            currentStop = currentStop,
            currencySymbol = currencySymbol,
            pabiliReceiptInput = pabiliReceiptInput,
            onPabiliReceiptChange = { pabiliReceiptInput = it },
            onConfirmPickedUp = { receiptTotal, overBudgetApproved ->
                showItemChecklistDialog = false
                triggerAdvance(receiptTotal, overBudgetApproved, currentStop.orderId)
            },
            onDismiss = { showItemChecklistDialog = false }
        )
    }

    // Parcel Cash / No-Cash Confirmation Dialog (Steps 2 & 4)
    val activeParcelCashStage = parcelCashDialogStage
    if (activeParcelCashStage != null && currentStop != null && currentStop.order.isParcel) {
        ParcelCashDialog(
            order = currentStop.order,
            stage = activeParcelCashStage,
            currencySymbol = currencySymbol,
            onConfirm = {
                parcelCashDialogStage = null
                triggerAdvance(null, false, currentStop.orderId)
            },
            onDismiss = { parcelCashDialogStage = null }
        )
    }

    // Full-screen Trip Details page (opened from clickable step chip for all services)
    if (showTripDetails && currentStop != null) {
        val tripOrder = (selectedStop ?: currentStop).order
        TripDetailsDialog(
            order = tripOrder,
            currencySymbol = currencySymbol,
            onCallClick = { name, phone ->
                val cleaned = PhoneUtils.cleanPhoneNumber(phone)
                if (cleaned.isNotEmpty()) {
                    onCallClick(name, cleaned)
                } else {
                    Toast.makeText(context, "No phone number", Toast.LENGTH_SHORT).show()
                }
            },
            onNavigateToLocation = { name, lat, lng ->
                launchExternalCoordinatesNavigation(context, name, lat, lng)
            },
            onDismiss = { showTripDetails = false }
        )
    }

    // More Actions Bottom Sheet
    if (showMoreMenu && currentStop != null) {
        SheetMoreActionsModal(
            canRelease = OrderReleaseRules.canReleaseBeforePickup(currentStop.order),
            onTripDetails = {
                showMoreMenu = false
                showTripDetails = true
            },
            onNavigate = {
                showMoreMenu = false
                launchExternalNavigation(context, currentStop)
            },
            onReportProblem = {
                showMoreMenu = false
                showProblemDialog = true
            },
            onReleaseOrder = {
                showMoreMenu = false
                showReleaseOrderDialog = true
            },
            onDismiss = { showMoreMenu = false }
        )
    }

    // COD Cash Confirmation Dialog (for Slide to Complete/Confirm Delivery at Customer)
    if (showCodConfirmDialog && currentStop != null) {
        CodCollectionDialog(
            currentStop = currentStop,
            currencySymbol = currencySymbol,
            onConfirmDelivery = {
                showCodConfirmDialog = false
                triggerAdvance(null, false, currentStop.orderId)
            },
            onDismiss = { showCodConfirmDialog = false }
        )
    }

    // Report Problem Dialog
    if (showProblemDialog) {
        AlertDialog(
            onDismissRequest = { showProblemDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ReportProblem, contentDescription = null, tint = DelivioRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Report an Issue", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Select problem type for current stop:", fontSize = 13.sp)
                    val reasons = listOf(
                        "Merchant delay / waiting",
                        "Customer unreachable",
                        "Wrong drop-off address",
                        "Vehicle trouble / Flat tire",
                        "Accident / Emergency",
                        "Customer refused delivery"
                    )
                    reasons.forEach { r ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .clickable { problemReason = r }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(selected = problemReason == r, onClick = { problemReason = r })
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(r, fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showProblemDialog = false
                        onReportProblem(problemReason)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DelivioRed, contentColor = Color.White),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Submit Report", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showProblemDialog = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Release Order Dialog (CR Cancellation / Close restaurant before pickup)
    if (showReleaseOrderDialog && currentStop != null && OrderReleaseRules.canReleaseBeforePickup(currentStop.order)) {
        ReleaseOrderDialog(
            order = currentStop.order,
            stopName = currentStop.name,
            onConfirmRelease = { reportType, reason ->
                showReleaseOrderDialog = false
                if (onReleaseOrderWithType != null) {
                    onReleaseOrderWithType(currentStop.order.id, reportType, reason)
                } else {
                    onReleaseOrder(reason)
                }
            },
            onDismiss = { showReleaseOrderDialog = false }
        )
    }
}

@Composable
fun ReleaseOrderDialog(
    order: Order,
    stopName: String,
    initialReportType: String = OrderReleaseRules.REPORT_TYPE_CR_CANCELLATION,
    onConfirmRelease: (reportType: String, reason: String) -> Unit,
    onDismiss: () -> Unit
) {
    val canCloseRestaurant = OrderReleaseRules.supportsCloseRestaurant(order)
    var selectedReportType by remember(order.id, initialReportType) {
        mutableStateOf(
            if (initialReportType == OrderReleaseRules.REPORT_TYPE_CLOSE_RESTAURANT && canCloseRestaurant) {
                OrderReleaseRules.REPORT_TYPE_CLOSE_RESTAURANT
            } else {
                OrderReleaseRules.REPORT_TYPE_CR_CANCELLATION
            }
        )
    }
    val crReasons = listOf(
        "Long wait at pickup",
        "Vehicle or safety issue",
        "Wrong pin / Unreachable",
        "Unable to fulfill order"
    )
    var selectedCrReason by remember(order.id) { mutableStateOf(crReasons.first()) }
    var noteInput by remember(order.id) { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .testTag("release_order_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Cancel,
                        contentDescription = "Release order",
                        tint = DelivioRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Cancel / Release Order",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${order.displayOrderNumber} • $stopName",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Text(
                    text = "Select action",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                val isCrSelected = selectedReportType == OrderReleaseRules.REPORT_TYPE_CR_CANCELLATION
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isCrSelected) DelivioYellow.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(
                        1.5.dp,
                        if (isCrSelected) DelivioYellowDark else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { selectedReportType = OrderReleaseRules.REPORT_TYPE_CR_CANCELLATION }
                        .testTag("release_type_cr_cancellation")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RadioButton(
                            selected = isCrSelected,
                            onClick = { selectedReportType = OrderReleaseRules.REPORT_TYPE_CR_CANCELLATION }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "CR Cancellation",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Release order immediately to another rider",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (canCloseRestaurant) {
                    val isCloseSelected = selectedReportType == OrderReleaseRules.REPORT_TYPE_CLOSE_RESTAURANT
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isCloseSelected) DelivioRed.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(
                            1.5.dp,
                            if (isCloseSelected) DelivioRed else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { selectedReportType = OrderReleaseRules.REPORT_TYPE_CLOSE_RESTAURANT }
                            .testTag("release_type_close_restaurant")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            RadioButton(
                                selected = isCloseSelected,
                                onClick = { selectedReportType = OrderReleaseRules.REPORT_TYPE_CLOSE_RESTAURANT }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Close restaurant",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Merchant is closed; hold order for admin review",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (selectedReportType == OrderReleaseRules.REPORT_TYPE_CR_CANCELLATION) {
                    Text(
                        text = "Reason",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        crReasons.forEach { r ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { selectedCrReason = r }
                                    .padding(horizontal = 4.dp)
                            ) {
                                RadioButton(
                                    selected = selectedCrReason == r,
                                    onClick = { selectedCrReason = r }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(r, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { if (it.length <= 200) noteInput = it },
                    label = { Text("Note (optional)") },
                    placeholder = {
                        Text(
                            if (selectedReportType == OrderReleaseRules.REPORT_TYPE_CLOSE_RESTAURANT) {
                                "e.g. Store lights off / posted hours"
                            } else {
                                "Optional details"
                            }
                        )
                    },
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("release_reason_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("cancel_release_order_button")
                    ) {
                        Text("Keep Order", maxLines = 1)
                    }

                    Button(
                        onClick = {
                            val finalReason = when {
                                noteInput.isNotBlank() && selectedReportType == OrderReleaseRules.REPORT_TYPE_CLOSE_RESTAURANT ->
                                    "Store closed: ${noteInput.trim()}"
                                selectedReportType == OrderReleaseRules.REPORT_TYPE_CLOSE_RESTAURANT ->
                                    "Restaurant / store is closed"
                                noteInput.isNotBlank() ->
                                    "$selectedCrReason (${noteInput.trim()})"
                                else -> selectedCrReason
                            }
                            onConfirmRelease(selectedReportType, finalReason)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DelivioRed,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1.2f)
                            .heightIn(min = 48.dp)
                            .testTag("confirm_release_order_button")
                    ) {
                        Text(
                            text = if (selectedReportType == OrderReleaseRules.REPORT_TYPE_CLOSE_RESTAURANT) {
                                "Report Closed"
                            } else {
                                "Release Order"
                            },
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

private fun launchExternalCoordinatesNavigation(context: Context, name: String, lat: Double, lng: Double) {
    val geoUri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(name)})")
    val navIntent = Intent(Intent.ACTION_VIEW, geoUri)
    try {
        context.startActivity(Intent.createChooser(navIntent, "Navigate with"))
    } catch (_: Exception) {
        Toast.makeText(context, "No navigation app installed", Toast.LENGTH_SHORT).show()
    }
}

private fun launchExternalNavigation(context: Context, stop: RouteStop) {
    launchExternalCoordinatesNavigation(context, stop.name, stop.lat, stop.lng)
}

private fun formatPaymentAmount(amount: Double): String {
    return if (amount % 1.0 == 0.0) {
        String.format(Locale.US, "%.0f", amount)
    } else {
        String.format(Locale.US, "%.2f", amount)
    }
}

private fun formatOnePaymentLine(order: Order, currencySymbol: String): String {
    if (order.isSplitChild || order.badge.equals("split", ignoreCase = true)) {
        return "No cash collected (Split order)"
    }
    val amount = order.cashAmountToCollect
    if (order.isParcel) {
        if (!order.parcelHasCashToCollect) {
            return "No cash to collect"
        }
        val stage = order.parcelStepStage
        val isPickupStep = stage == ParcelStepStage.TO_SENDER || stage == ParcelStepStage.AT_SENDER
        return when {
            isPickupStep && order.parcelCollectsAtPickup ->
                "Collect $currencySymbol${formatPaymentAmount(amount)} cash from the sender"
            !isPickupStep && order.parcelCollectsAtDropoff ->
                "Collect $currencySymbol${formatPaymentAmount(amount)} cash from the recipient"
            else -> "No cash to collect"
        }
    }
    if (order.isRequestRider) {
        return if (amount > 0.0) {
            "Collect $currencySymbol${formatPaymentAmount(amount)} cash"
        } else {
            "No cash to collect (paid)"
        }
    }
    return if (order.isCod && amount > 0.0) {
        "Collect $currencySymbol${formatPaymentAmount(amount)} cash"
    } else {
        "Paid online - no cash"
    }
}

fun isDriverFarFromTarget(
    driverLocation: Pair<Double, Double>?,
    targetLat: Double,
    targetLng: Double,
    thresholdMeters: Double = 300.0
): Boolean {
    if (driverLocation == null) return false
    val (dLat, dLng) = driverLocation
    if (dLat == 0.0 || dLng == 0.0 || targetLat == 0.0 || targetLng == 0.0) return false
    val distanceMeters = BatchConstants.distanceKm(dLat, dLng, targetLat, targetLng) * 1000.0
    return distanceMeters > thresholdMeters
}

private fun handleSlideAction(
    currentStop: RouteStop,
    onOpenChecklist: () -> Unit,
    onOpenCodConfirm: () -> Unit,
    onOpenParcelCash: (ParcelStepStage) -> Unit,
    onAdvanceDirect: () -> Unit
) {
    if (currentStop.order.isTaxi) {
        onAdvanceDirect()
        return
    }
    if (currentStop.order.isParcel) {
        when (currentStop.order.parcelStepStage) {
            ParcelStepStage.AT_SENDER -> onOpenParcelCash(ParcelStepStage.AT_SENDER)
            ParcelStepStage.AT_RECIPIENT -> onOpenParcelCash(ParcelStepStage.AT_RECIPIENT)
            else -> onAdvanceDirect()
        }
        return
    }
    if (currentStop.order.isPabili) {
        when (currentStop.order.pabiliStepStage) {
            PabiliStepStage.AT_VENDOR -> onOpenChecklist()
            PabiliStepStage.AT_RECIPIENT -> {
                val isSplitChild = currentStop.order.isSplitChild || currentStop.order.badge.equals("split", ignoreCase = true)
                if (currentStop.isCod && !isSplitChild) {
                    onOpenCodConfirm()
                } else {
                    onAdvanceDirect()
                }
            }
            else -> onAdvanceDirect()
        }
        return
    }
    when (currentStop.order.foodStepStage) {
        FoodStepStage.AT_MERCHANT -> onOpenChecklist()
        FoodStepStage.AT_CUSTOMER -> {
            val isSplitChild = currentStop.order.isSplitChild || currentStop.order.badge.equals("split", ignoreCase = true)
            if (currentStop.isCod && !isSplitChild) {
                onOpenCodConfirm()
            } else {
                onAdvanceDirect()
            }
        }
        else -> onAdvanceDirect()
    }
}

private fun getSlideButtonLabel(stop: RouteStop): String {
    if (stop.order.isTaxi) {
        return when (stop.order.taxiStepStage) {
            TaxiStepStage.AT_PICKUP -> "Slide to confirm Picked Up"
            TaxiStepStage.AT_EXTRA_STOP -> "Slide to continue to the next stop"
            TaxiStepStage.AT_DROPOFF -> "Slide to confirm Drop off"
            else -> "Slide to confirm Drop off"
        }
    }
    if (stop.order.isParcel) {
        return when (stop.order.parcelStepStage) {
            ParcelStepStage.AT_SENDER -> "Slide to confirm Picked Up"
            ParcelStepStage.AT_RECIPIENT, ParcelStepStage.COMPLETED -> "Slide to Confirm Delivery"
            else -> "Slide to Confirm Delivery"
        }
    }
    if (stop.order.isPabili) {
        return when (stop.order.pabiliStepStage) {
            PabiliStepStage.AT_VENDOR -> "Slide to confirm Picked Up"
            PabiliStepStage.AT_RECIPIENT, PabiliStepStage.COMPLETED -> "Slide to Confirm Delivery"
            else -> "Slide to Confirm Delivery"
        }
    }
    return when (stop.order.foodStepStage) {
        FoodStepStage.AT_MERCHANT -> "Slide to confirm Picked Up"
        FoodStepStage.AT_CUSTOMER, FoodStepStage.COMPLETED -> "Slide to Confirm Delivery"
        else -> "Slide to Confirm Delivery"
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepBadgeRow(
    currentStop: RouteStop,
    totalStops: Int,
    currencySymbol: String,
    onStepChipClick: (() -> Unit)? = null
) {
    val isTaxi = currentStop.order.isTaxi
    val isParcel = currentStop.order.isParcel
    val taxiStage = currentStop.order.taxiStepStage
    val badgeText = if (isTaxi) {
        when (taxiStage) {
            TaxiStepStage.TO_PICKUP -> "Step 1 of 4 - Go to pickup"
            TaxiStepStage.AT_PICKUP -> "Step 2 of 4 - Pick up"
            TaxiStepStage.TO_EXTRA_STOP -> "Step 3 of 4 - Go to Stop ${currentStop.order.currentTaxiStopNumber}"
            TaxiStepStage.AT_EXTRA_STOP -> "Step 3 of 4 - At Stop ${currentStop.order.currentTaxiStopNumber}"
            TaxiStepStage.TO_DROPOFF -> "Step 3 of 4 - Go to drop-off"
            TaxiStepStage.AT_DROPOFF, TaxiStepStage.COMPLETED -> "Step 4 of 4 - Drop off"
        }
    } else if (isParcel) {
        val isPickup = currentStop.order.parcelStepStage == ParcelStepStage.TO_SENDER ||
            currentStop.order.parcelStepStage == ParcelStepStage.AT_SENDER
        if (isPickup) "Stop 1 of 2 - Pick up" else "Stop 2 of 2 - Drop off"
    } else {
        val actionLabel = if (currentStop.isMerchant) "Pick up" else "Drop off"
        "Stop ${currentStop.stopNumber} of $totalStops - $actionLabel"
    }
    val isPickupTint = when {
        isTaxi -> taxiStage == TaxiStepStage.TO_PICKUP || taxiStage == TaxiStepStage.AT_PICKUP
        isParcel -> currentStop.order.parcelStepStage == ParcelStepStage.TO_SENDER ||
            currentStop.order.parcelStepStage == ParcelStepStage.AT_SENDER
        else -> currentStop.isMerchant
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val chipTint = if (isPickupTint) DelivioGreenDark else DelivioRed
        val chipModifier = if (onStepChipClick != null) {
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onStepChipClick)
                .testTag("trip_details_chip")
        } else {
            Modifier.testTag("trip_details_chip")
        }
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (isPickupTint) DelivioGreen.copy(alpha = 0.14f) else DelivioRed.copy(alpha = 0.14f),
            modifier = chipModifier
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = badgeText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = chipTint
                )
                if (onStepChipClick != null) {
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Trip details",
                        tint = chipTint,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
        if (currentStop.order.isPabili && currentStop.order.resolvedBudgetLimit > 0.0) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = DelivioYellow.copy(alpha = 0.22f),
                border = BorderStroke(0.5.dp, DelivioYellowDark.copy(alpha = 0.45f)),
                modifier = Modifier.testTag("pabili_budget_limit_chip")
            ) {
                Text(
                    text = "Budget limit $currencySymbol${formatPaymentAmount(currentStop.order.resolvedBudgetLimit)}",
                    color = TextOnYellow,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        if (currentStop.order.isSplitChild || currentStop.order.badge.equals("split", ignoreCase = true)) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFF7B1FA2)
            ) {
                Text(
                    text = "SPLIT",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }
        if (currentStop.order.isScheduled) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFF1565C0)
            ) {
                Text(
                    text = if (currentStop.order.scheduledDateText.isNotBlank()) "SCHEDULED • ${currentStop.order.scheduledDateText}" else "SCHEDULED",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun CollapsedOrderSheetContent(
    stopName: String,
    dragModifier: Modifier,
    onExpand: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .height(88.dp)
            .then(dragModifier)
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .testTag("collapsed_trip_bar"),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onExpand)
                .padding(bottom = 4.dp)
                .testTag("sheet_drag_handle"),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clickable(onClick = onExpand),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stopName,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .testTag("collapsed_stop_name")
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "Expand trip details",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(22.dp)
                    .testTag("collapsed_up_chevron")
            )
        }
    }
}

@Composable
private fun ExpandedOrderSheetContent(
    batchStops: List<RouteStop>,
    currentStop: RouteStop,
    selectedStop: RouteStop,
    activeOrders: List<Order>,
    currencySymbol: String,
    routeDistanceKm: Double,
    routeEtaMinutes: Int,
    showEarningsDetails: Boolean,
    dragModifier: Modifier,
    onToggleEarningsDetails: () -> Unit,
    onSelectStop: (RouteStop) -> Unit,
    onNavigateClick: () -> Unit,
    onTripDetailsClick: () -> Unit = {},
    onCallClick: (String, String) -> Unit,
    onChatClick: (Order) -> Unit,
    hasUnreadChat: (String) -> Boolean,
    onMoreClick: () -> Unit,
    onCollapse: () -> Unit,
    onReportProblemClick: () -> Unit,
    onReleaseOrderClick: () -> Unit,
    onTaxiPressAction: () -> Unit,
    onPabiliPressAction: () -> Unit,
    onParcelPressAction: () -> Unit,
    onFoodPressAction: () -> Unit,
    onSlideConfirmed: () -> Unit
) {
    val isTaxi = currentStop.order.isTaxi
    val taxiStage = currentStop.order.taxiStepStage
    val isPabili = currentStop.order.isPabili
    val pabiliStage = currentStop.order.pabiliStepStage
    val isParcel = currentStop.order.isParcel
    val parcelStage = currentStop.order.parcelStepStage
    val foodStage = currentStop.order.foodStepStage
    val scrollState = rememberScrollState()
    var cumulativePullDown by remember { mutableFloatStateOf(0f) }
    val pullDownCollapseConnection = remember(scrollState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && scrollState.value == 0 && available.y > 0f) {
                    cumulativePullDown += available.y
                    if (cumulativePullDown > 72f) {
                        cumulativePullDown = 0f
                        onCollapse()
                        return available
                    }
                } else if (available.y < 0f) {
                    cumulativePullDown = 0f
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: androidx.compose.ui.unit.Velocity, available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                cumulativePullDown = 0f
                return super.onPostFling(consumed, available)
            }
        }
    }

    val orderedStops = remember(batchStops) {
        val visible = batchStops.filterNot { stop ->
            stop.isMerchant &&
                stop.order.isPabili &&
                stop.order.pabiliStepStage !in setOf(PabiliStepStage.TO_VENDOR, PabiliStepStage.AT_VENDOR)
        }
        val current = visible.filter { it.status == StopStatus.CURRENT }
        val upcoming = visible.filter { it.status == StopStatus.NEXT }
        val done = visible.filter { it.status == StopStatus.DONE }
        current + upcoming + done
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .navigationBarsPadding()
    ) {
        // 1. Top drag handle with down chevron (tap or swipe down to collapse)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .then(dragModifier)
                .clickable(onClick = onCollapse)
                .padding(top = 10.dp, bottom = 4.dp)
                .testTag("sheet_drag_handle"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Collapse trip details",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(22.dp)
                    .testTag("collapse_sheet_button")
            )
        }

        // 2. Scrollable Middle Details Body
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .nestedScroll(pullDownCollapseConnection)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // (a) Step chip (+ Budget limit chip for Pabili at every step)
            StepBadgeRow(
                currentStop = currentStop,
                totalStops = batchStops.size.coerceAtLeast(1),
                currencySymbol = currencySymbol,
                onStepChipClick = onTripDetailsClick
            )

            // (b) Name
            Text(
                text = currentStop.name,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("expanded_stop_name")
            )

            // (c) ETA and distance
            val etaPart = if (routeEtaMinutes > 0) "$routeEtaMinutes min" else "Calculating ETA"
            val distPart = if (routeDistanceKm > 0.0) " • ${String.format(Locale.US, "%.1f", routeDistanceKm)} km" else ""
            Text(
                text = "$etaPart$distPart",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("expanded_eta_distance")
            )

            if (isTaxi) {
                // Taxi: Single current-step details card ONLY (NO stop timeline, NO items section)
                TaxiSingleStopDetailsCard(stop = currentStop)

                // Passenger count (+ pickup note during pickup steps only)
                TaxiPassengerMetaRow(
                    order = currentStop.order,
                    showPickupNote = taxiStage == TaxiStepStage.TO_PICKUP || taxiStage == TaxiStepStage.AT_PICKUP
                )
            } else if (isParcel) {
                // Parcel: Pickup-only details in Steps 1-2, Drop-off-only details in Steps 3-4, plus compact Parcel card
                ParcelSingleStopDetailsCard(stop = currentStop)
                ParcelCompactInfoCard(order = currentStop.order)
            } else if (!isPabili && activeOrders.size <= 1) {
                // Food / Convenience / Instant single order: isolated Merchant-only (Steps 1-2) or Customer-only (Steps 3-4)
                FoodSingleStopDetailsCard(
                    stop = currentStop,
                    stage = foodStage
                )
                SelectedStopItemsCard(
                    stop = currentStop,
                    currencySymbol = currencySymbol,
                    showCustomerNotes = false
                )
            } else {
                val isPabiliDropoffOnly = isPabili &&
                    pabiliStage !in setOf(PabiliStepStage.TO_VENDOR, PabiliStepStage.AT_VENDOR) &&
                    orderedStops.size <= 1
                if (isPabiliDropoffOnly) {
                    // Pabili Steps 3 & 4: Show ONLY drop-off details (customer name + revealed address), no merchant card
                    PabiliDropoffDetailsCard(stop = orderedStops.firstOrNull() ?: currentStop)
                } else {
                    // Standard timeline (batched stops or Pabili vendor steps)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.testTag("stop_timeline_list")
                    ) {
                        orderedStops.forEach { stop ->
                            CompactTimelineStopRow(
                                stop = stop,
                                isSelected = stop.orderId == selectedStop.orderId && stop.type == selectedStop.type,
                                onClick = { onSelectStop(stop) }
                            )
                        }
                    }
                }

                SelectedStopItemsCard(
                    stop = if (isPabiliDropoffOnly) currentStop else selectedStop,
                    currencySymbol = currencySymbol
                )
            }

            // Payment line
            OnePaymentLineBar(
                order = if (isTaxi || isParcel || (!isPabili && activeOrders.size <= 1)) currentStop.order else selectedStop.order,
                currencySymbol = currencySymbol
            )

            // Row of labeled buttons: Call, Chat, More, and yellow Navigate button (all services)
            val actionStop = if (isTaxi || isParcel || (!isPabili && activeOrders.size <= 1)) currentStop else selectedStop
            val (callTargetName, cleanedCallPhone) = remember(actionStop, activeOrders.size) {
                resolveStopCallTarget(actionStop, activeOrders.size)
            }
            SheetQuickActionIconsRow(
                currentStop = actionStop,
                hasPhone = cleanedCallPhone.isNotEmpty(),
                onCallClick = { onCallClick(callTargetName, cleanedCallPhone) },
                onChatClick = onChatClick,
                hasUnreadChat = hasUnreadChat,
                onMoreClick = onMoreClick,
                onNavigateClick = onNavigateClick
            )

            // Collapsed "Earnings details (...)" row
            val totalEarned = activeOrders.sumOf { it.estimatedEarnings }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onToggleEarningsDetails() }
                    .testTag("earnings_details_toggle")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Earnings details ($currencySymbol${String.format(Locale.US, "%.2f", totalEarned)})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = if (showEarningsDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Toggle earnings details",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AnimatedVisibility(visible = showEarningsDetails) {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            PaySummarySection(
                                activeOrders = activeOrders,
                                currencySymbol = currencySymbol
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))
        }

        // 3. Bottom pinned primary action (Press button for Taxi Steps 1/3 & Pabili Steps 1/3, Slide for Steps 2/4 and other services) + Report a Problem
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val isTaxiPressStep = isTaxi && (
                taxiStage == TaxiStepStage.TO_PICKUP ||
                    taxiStage == TaxiStepStage.TO_EXTRA_STOP ||
                    taxiStage == TaxiStepStage.TO_DROPOFF
                )
            val isPabiliPressStep = isPabili && (
                pabiliStage == PabiliStepStage.TO_VENDOR ||
                    pabiliStage == PabiliStepStage.TO_RECIPIENT
                )
            val isParcelPressStep = isParcel && (
                parcelStage == ParcelStepStage.TO_SENDER ||
                    parcelStage == ParcelStepStage.TO_RECIPIENT
                )
            val isFoodPressStep = !isTaxi && !isPabili && !isParcel && (
                foodStage == FoodStepStage.TO_MERCHANT ||
                    foodStage == FoodStepStage.TO_CUSTOMER
                )
            if (isTaxiPressStep) {
                val pressLabel = when (taxiStage) {
                    TaxiStepStage.TO_PICKUP -> "Arrived at ${currentStop.name}"
                    TaxiStepStage.TO_EXTRA_STOP -> "Arrived at Stop ${currentStop.order.currentTaxiStopNumber}"
                    else -> "Arrived at the Drop Off Area"
                }
                Button(
                    onClick = onTaxiPressAction,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DelivioYellow,
                        contentColor = TextOnYellow
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .testTag("taxi_press_action_button")
                ) {
                    Icon(Icons.Default.WhereToVote, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = pressLabel,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else if (isPabiliPressStep) {
                val pabiliPressLabel = if (pabiliStage == PabiliStepStage.TO_VENDOR) {
                    "Arrived at the Merchant"
                } else {
                    "Arrived at the Customer"
                }
                Button(
                    onClick = onPabiliPressAction,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DelivioYellow,
                        contentColor = TextOnYellow
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .testTag("pabili_press_action_button")
                ) {
                    Icon(Icons.Default.WhereToVote, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = pabiliPressLabel,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else if (isParcelPressStep) {
                val parcelPressLabel = if (parcelStage == ParcelStepStage.TO_SENDER) {
                    "Arrived at the Sender"
                } else {
                    "Arrived at the Recipient"
                }
                Button(
                    onClick = onParcelPressAction,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DelivioYellow,
                        contentColor = TextOnYellow
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .testTag("parcel_press_action_button")
                ) {
                    Icon(Icons.Default.WhereToVote, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = parcelPressLabel,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else if (isFoodPressStep) {
                val foodPressLabel = if (foodStage == FoodStepStage.TO_MERCHANT) {
                    "Arrived at the Merchant"
                } else {
                    "Arrived at the Customer"
                }
                Button(
                    onClick = onFoodPressAction,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DelivioYellow,
                        contentColor = TextOnYellow
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .testTag("food_press_action_button")
                ) {
                    Icon(Icons.Default.WhereToVote, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = foodPressLabel,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                SlideToConfirmButton(
                    text = getSlideButtonLabel(currentStop),
                    onConfirmed = onSlideConfirmed,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sheet_slide_button")
                )
            }

            val canRelease = OrderReleaseRules.canReleaseBeforePickup(currentStop.order)
            if (canRelease) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onReportProblemClick,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("report_problem_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DelivioRed)
                    ) {
                        Icon(Icons.Default.ReportProblem, contentDescription = null, modifier = Modifier.size(16.dp), tint = DelivioRed)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Report a Problem", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    OutlinedButton(
                        onClick = onReleaseOrderClick,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("release_order_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DelivioRed)
                    ) {
                        Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp), tint = DelivioRed)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Release Order", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            } else {
                OutlinedButton(
                    onClick = onReportProblemClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("report_problem_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DelivioRed)
                ) {
                    Icon(Icons.Default.ReportProblem, contentDescription = null, modifier = Modifier.size(16.dp), tint = DelivioRed)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Report a Problem", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun TaxiSingleStopDetailsCard(
    stop: RouteStop
) {
    val stage = stop.order.taxiStepStage
    val isPickupStep = stage == TaxiStepStage.TO_PICKUP || stage == TaxiStepStage.AT_PICKUP
    val badgeLabel = if (isPickupStep) "PICKUP" else "DROP-OFF"
    val badgeColor = if (isPickupStep) DelivioGreenDark else DelivioRed
    val addressText = stop.fullAddress.ifBlank { stop.address }.ifBlank { "Address unavailable" }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(
            width = 1.dp,
            color = if (isPickupStep) DelivioGreen.copy(alpha = 0.45f) else DelivioRed.copy(alpha = 0.45f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("taxi_stop_details_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeColor.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = badgeLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Text(
                    text = stop.displayOrderNumber,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = stop.name,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = badgeColor,
                    modifier = Modifier
                        .size(16.dp)
                        .padding(top = 2.dp)
                )
                Text(
                    text = addressText,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("taxi_stop_address")
                )
            }
        }
    }
}

@Composable
private fun PabiliDropoffDetailsCard(
    stop: RouteStop
) {
    val customerName = stop.order.customerName.ifBlank { stop.name }.ifBlank { "Customer" }
    val addressText = stop.fullAddress
        .ifBlank { deduplicateAddress(stop.order.customerAddress) }
        .ifBlank { stop.address }
        .ifBlank { "Address unavailable" }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, DelivioRed.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pabili_dropoff_details_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = DelivioRed.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = "DROP-OFF",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = DelivioRed,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Text(
                    text = stop.displayOrderNumber,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = customerName,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = DelivioRed,
                    modifier = Modifier
                        .size(16.dp)
                        .padding(top = 2.dp)
                )
                Text(
                    text = addressText,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("pabili_dropoff_address")
                )
            }
        }
    }
}

@Composable
private fun TaxiPassengerMetaRow(
    order: Order,
    showPickupNote: Boolean
) {
    val count = order.passengerCount.coerceAtLeast(1)
    val countText = if (count == 1) "1 passenger" else "$count passengers"
    val note = if (showPickupNote) order.resolvedPickupNote else ""

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("taxi_passenger_meta")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.People,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = countText,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (note.isNotBlank()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.StickyNote2,
                        contentDescription = null,
                        tint = DelivioYellow,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = note,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun FoodSingleStopDetailsCard(
    stop: RouteStop,
    stage: FoodStepStage
) {
    val context = LocalContext.current
    val isMerchantStep = stage == FoodStepStage.TO_MERCHANT || stage == FoodStepStage.AT_MERCHANT
    val badgeLabel = if (isMerchantStep) "PICKUP" else "DROP-OFF"
    val badgeColor = if (isMerchantStep) DelivioGreenDark else DelivioRed
    val personName = if (isMerchantStep) {
        resolveStopDisplayName(stop.order, StopType.MERCHANT)
    } else {
        resolveStopDisplayName(stop.order, StopType.CUSTOMER)
    }
    val addressText = if (isMerchantStep) {
        deduplicateAddress(stop.order.merchantAddress)
            .ifBlank { stop.fullAddress }
            .ifBlank { stop.address }
            .ifBlank { "Address unavailable" }
    } else {
        stop.fullAddress
            .ifBlank { deduplicateAddress(stop.order.customerAddress) }
            .ifBlank { deduplicateAddress(stop.order.customerArea) }
            .ifBlank { stop.address }
            .ifBlank { "Address unavailable" }
    }
    val noteText = if (!isMerchantStep) {
        stop.order.customerNotes.trim().ifBlank { stop.order.resolvedDropoffNote }
    } else {
        ""
    }
    val orderBadgeText = if (stop.displayOrderNumber.startsWith("Order ", ignoreCase = true)) {
        stop.displayOrderNumber
    } else {
        "Order ${stop.displayOrderNumber}"
    }
    val copyText = stop.order.displayOrderNumber.ifBlank { stop.displayOrderNumber }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(
            width = 1.dp,
            color = if (isMerchantStep) DelivioGreen.copy(alpha = 0.45f) else DelivioRed.copy(alpha = 0.45f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("food_stop_details_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeColor.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = badgeLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText("Order number", copyText))
                            Toast.makeText(context, "Copied $copyText", Toast.LENGTH_SHORT).show()
                        }
                        .testTag("sheet_order_number_chip")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = orderBadgeText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy order number",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            Text(
                text = personName,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = badgeColor,
                    modifier = Modifier
                        .size(16.dp)
                        .padding(top = 2.dp)
                )
                Text(
                    text = addressText,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("food_stop_address")
                )
            }

            if (noteText.isNotBlank()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.StickyNote2,
                        contentDescription = null,
                        tint = DelivioYellowDark,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(top = 2.dp)
                    )
                    Text(
                        text = noteText,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactTimelineStopRow(
    stop: RouteStop,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val isHighlighted = stop.isCurrent || isSelected
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = when {
            stop.isCurrent -> DelivioYellow.copy(alpha = 0.14f)
            isSelected -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            stop.status == StopStatus.DONE -> MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        },
        border = BorderStroke(
            width = if (isHighlighted) 1.5.dp else 0.5.dp,
            color = if (isHighlighted) DelivioYellow else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StopNumberBadge(stop = stop, size = 26)
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (stop.isMerchant) "PICKUP" else "DROP-OFF",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (stop.isMerchant) DelivioGreenDark else DelivioRed
                    )
                    if (stop.status == StopStatus.DONE) {
                        Text(
                            text = "• Done",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = DelivioGreenDark
                        )
                    }
                }
                Text(
                    text = stop.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (stop.address.isNotBlank()) {
                    Text(
                        text = stop.address,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (!stop.order.isTaxi && stop.items.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Text(
                        text = "${stop.items.size} ${if (stop.items.size == 1) "item" else "items"}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun OnePaymentLineBar(
    order: Order,
    currencySymbol: String
) {
    val lineText = formatOnePaymentLine(order, currencySymbol)
    val isCashCollect = when {
        order.isSplitChild || order.badge.equals("split", ignoreCase = true) -> false
        order.isParcel -> {
            val stage = order.parcelStepStage
            val isPickupStep = stage == ParcelStepStage.TO_SENDER || stage == ParcelStepStage.AT_SENDER
            (isPickupStep && order.parcelCollectsAtPickup) || (!isPickupStep && order.parcelCollectsAtDropoff)
        }
        order.isRequestRider -> order.cashAmountToCollect > 0.0
        else -> order.isCod && order.cashAmountToCollect > 0.0
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isCashCollect) Color(0xFFFFF3E0) else DelivioGreen.copy(alpha = 0.12f),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("payment_line_bar")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (isCashCollect) Icons.Default.LocalAtm else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = if (isCashCollect) Color(0xFFE65100) else DelivioGreenDark,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = lineText,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = if (isCashCollect) Color(0xFFBF360C) else DelivioGreenDark
            )
        }
    }
}

private fun resolveStopCallTarget(target: RouteStop, activeOrdersCount: Int): Pair<String, String> {
    val isPabiliOrder = target.order.isPabili
    val isParcelOrder = target.order.isParcel
    val isTaxiOrder = target.order.isTaxi
    val phoneRaw = when {
        isParcelOrder -> {
            val contacts = contactsOf(target.order)
            val stage = target.order.parcelStepStage
            if (stage == ParcelStepStage.TO_SENDER || stage == ParcelStepStage.AT_SENDER) {
                contacts.pickup.phone
            } else {
                contacts.dropoff.phone
            }
        }
        isPabiliOrder -> target.order.customerPhone
        isTaxiOrder -> if (PhoneUtils.isUsablePhone(target.order.customerPhone)) {
            target.order.customerPhone
        } else {
            target.order.merchantPhone
        }
        else -> {
            val isMerchantStop = if (activeOrdersCount <= 1) {
                val foodStage = target.order.foodStepStage
                foodStage == FoodStepStage.TO_MERCHANT || foodStage == FoodStepStage.AT_MERCHANT
            } else {
                target.isMerchant
            }
            if (isMerchantStop) target.order.merchantPhone else target.order.customerPhone
        }
    }
    val callName = when {
        isParcelOrder -> {
            val contacts = contactsOf(target.order)
            val stage = target.order.parcelStepStage
            if (stage == ParcelStepStage.TO_SENDER || stage == ParcelStepStage.AT_SENDER) {
                contacts.pickup.name
            } else {
                contacts.dropoff.name
            }
        }
        isPabiliOrder -> target.order.customerName.ifBlank { "Customer" }
        isTaxiOrder -> target.name
        else -> {
            val isMerchantStop = if (activeOrdersCount <= 1) {
                val foodStage = target.order.foodStepStage
                foodStage == FoodStepStage.TO_MERCHANT || foodStage == FoodStepStage.AT_MERCHANT
            } else {
                target.isMerchant
            }
            if (isMerchantStop) {
                resolveStopDisplayName(target.order, StopType.MERCHANT)
            } else {
                resolveStopDisplayName(target.order, StopType.CUSTOMER)
            }
        }
    }
    return callName to PhoneUtils.cleanPhoneNumber(phoneRaw)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetMoreActionsModal(
    canRelease: Boolean,
    onTripDetails: () -> Unit,
    onNavigate: () -> Unit,
    onReportProblem: () -> Unit,
    onReleaseOrder: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("sheet_more_menu")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Trip options",
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )

            MoreMenuOptionRow(
                icon = Icons.Default.Info,
                iconTint = MaterialTheme.colorScheme.primary,
                title = "Trip details",
                subtitle = "View addresses, items, and contact details",
                testTag = "more_option_trip_details",
                onClick = onTripDetails
            )

            MoreMenuOptionRow(
                icon = Icons.Default.Navigation,
                iconTint = DelivioYellowDark,
                title = "Navigate in maps app",
                subtitle = "Launch turn-by-turn navigation for this stop",
                testTag = "more_option_navigate",
                onClick = onNavigate
            )

            MoreMenuOptionRow(
                icon = Icons.Default.ReportProblem,
                iconTint = DelivioRed,
                title = "Report a Problem",
                subtitle = "Notify dispatch of an issue on this stop",
                testTag = "more_option_report_problem",
                onClick = onReportProblem
            )

            if (canRelease) {
                MoreMenuOptionRow(
                    icon = Icons.Default.Cancel,
                    iconTint = DelivioRed,
                    title = "Release Order",
                    subtitle = "Cancel or report store closed before pickup",
                    testTag = "more_option_release_order",
                    onClick = onReleaseOrder
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Composable
private fun MoreMenuOptionRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SheetQuickActionIconsRow(
    currentStop: RouteStop,
    hasPhone: Boolean,
    onCallClick: () -> Unit,
    onChatClick: (Order) -> Unit,
    hasUnreadChat: (String) -> Boolean,
    onMoreClick: () -> Unit,
    onNavigateClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("quick_actions_row"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoundLabeledActionButton(
            icon = Icons.Default.Call,
            label = if (hasPhone) "Call" else "No phone number",
            containerColor = if (hasPhone) {
                DelivioGreen.copy(alpha = 0.16f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            },
            iconTint = if (hasPhone) {
                DelivioGreen
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
            },
            labelColor = if (hasPhone) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            },
            enabled = hasPhone,
            showUnreadDot = false,
            onClick = onCallClick,
            testTag = "sheet_call_button"
        )

        RoundLabeledActionButton(
            icon = Icons.Default.ChatBubble,
            label = "Chat",
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            iconTint = MaterialTheme.colorScheme.onSurface,
            showUnreadDot = hasUnreadChat(currentStop.order.id),
            onClick = { onChatClick(currentStop.order) },
            testTag = "customer_chat_button"
        )

        RoundLabeledActionButton(
            icon = Icons.Default.MoreHoriz,
            label = "More",
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            iconTint = MaterialTheme.colorScheme.onSurface,
            showUnreadDot = false,
            onClick = onMoreClick,
            testTag = "sheet_more_button"
        )

        Button(
            onClick = onNavigateClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = DelivioYellow,
                contentColor = TextOnYellow
            ),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .testTag("inline_navigate_button")
        ) {
            Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Navigate", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun RoundLabeledActionButton(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    iconTint: Color,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    enabled: Boolean = true,
    showUnreadDot: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(modifier = Modifier.size(48.dp)) {
            Surface(
                shape = CircleShape,
                color = containerColor,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(enabled = enabled, onClick = onClick)
                    .testTag(testTag)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            if (showUnreadDot) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(DelivioRed)
                        .align(Alignment.TopEnd)
                        .testTag("chat_unread_dot")
                )
            }
        }
        Text(
            text = label,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = labelColor,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 68.dp)
        )
    }
}

@Composable
private fun SelectedStopItemsCard(
    stop: RouteStop,
    currencySymbol: String,
    showCustomerNotes: Boolean = true
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val itemsHeader = if (stop.order.isPabili) {
                    when (stop.order.pabiliStepStage) {
                        PabiliStepStage.TO_VENDOR, PabiliStepStage.AT_VENDOR -> "Items to Pick Up"
                        else -> "Items to Deliver"
                    }
                } else {
                    if (stop.isMerchant) "Items to Pick Up" else "Items to Deliver"
                }
                Text(
                    text = itemsHeader,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stop.displayOrderNumber,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (stop.order.splitOut.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF3E5F5),
                    border = BorderStroke(1.dp, Color(0xFFCE93D8)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.AltRoute,
                            contentDescription = null,
                            tint = Color(0xFF7B1FA2),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Another rider carries part of this order",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF7B1FA2)
                        )
                    }
                }
            }

            if (stop.items.isEmpty()) {
                Text(
                    text = "No item list provided",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                stop.items.forEach { item ->
                    val isTakenByOther = stop.order.splitOut.any { splitName ->
                        val sn = splitName.trim()
                        sn.equals(item.name.trim(), ignoreCase = true) ||
                            sn.equals(item.displayName.trim(), ignoreCase = true)
                    }
                    Column(modifier = Modifier.padding(vertical = 2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Text(
                                    text = "• ${item.displayName}",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = if (isTakenByOther) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                if (isTakenByOther) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFFEDE7F6)
                                    ) {
                                        Text(
                                            text = "Taken by another rider",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF7B1FA2),
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "x${item.quantity}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (item.price > 0.0) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "$currencySymbol${String.format("%.2f", item.price * item.quantity)}",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                        val validSelections = item.selections
                            .map { it.trim() }
                            .filter { it.isNotBlank() && it != "()" && it != "( )" }
                        validSelections.forEach { sel ->
                            Text(
                                text = "  └ $sel",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 10.dp)
                            )
                        }
                        if (item.specialInstructions.isNotBlank()) {
                            Text(
                                text = "  Note: ${item.specialInstructions.trim()}",
                                fontSize = 11.sp,
                                color = DelivioYellowDark,
                                modifier = Modifier.padding(start = 10.dp)
                            )
                        }
                    }
                }
            }

            if (stop.order.isPabili &&
                stop.order.pabiliStepStage !in setOf(PabiliStepStage.TO_VENDOR, PabiliStepStage.AT_VENDOR) &&
                stop.order.resolvedPaidOnMerchant > 0.0
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .testTag("pabili_paid_on_merchant_row"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Paid on Merchant $currencySymbol${formatPaymentAmount(stop.order.resolvedPaidOnMerchant)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (showCustomerNotes && stop.order.customerNotes.isNotBlank()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.StickyNote2,
                        contentDescription = null,
                        tint = DelivioYellow,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stop.order.customerNotes.trim(),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun PaySummarySection(
    activeOrders: List<Order>,
    currencySymbol: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        val totalTip = activeOrders.sumOf { it.tip + it.lateTipAmount }
        val totalEarned = activeOrders.sumOf { it.estimatedEarnings }
        val netDeliveryFee = (totalEarned - totalTip).coerceAtLeast(0.0)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Delivery fee", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("$currencySymbol${String.format(Locale.US, "%.2f", netDeliveryFee)}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Tip", fontSize = 12.sp, color = if (totalTip > 0.0) DelivioGreenDark else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "+$currencySymbol${String.format(Locale.US, "%.2f", totalTip)}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (totalTip > 0.0) DelivioGreenDark else MaterialTheme.colorScheme.onSurface
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Total", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
            Text("$currencySymbol${String.format(Locale.US, "%.2f", totalEarned)}", fontWeight = FontWeight.Black, fontSize = 14.sp, color = DelivioYellowDark)
        }
    }
}

@Composable
private fun StopNumberBadge(stop: RouteStop, size: Int = 30) {
    val bgColor = if (stop.isMerchant) {
        Color(0xFF2E7D32)
    } else {
        Color(0xFFE53935)
    }
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "${stop.stopNumber}",
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = (size * 0.45).sp
        )
    }
}

@Composable
private fun ItemChecklistDialog(
    currentStop: RouteStop,
    currencySymbol: String,
    pabiliReceiptInput: String,
    onPabiliReceiptChange: (String) -> Unit,
    onConfirmPickedUp: (receiptTotal: Double?, overBudgetApproved: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val isPabili = currentStop.order.isPabili
    val budgetLimit = currentStop.order.resolvedBudgetLimit
    var overBudgetApproved by remember(currentStop.orderId) { mutableStateOf(false) }

    val checkedItems = remember(currentStop.items) {
        mutableStateMapOf<Int, Boolean>().apply {
            currentStop.items.indices.forEach { put(it, true) }
        }
    }

    val parsedReceipt = remember(pabiliReceiptInput) {
        PabiliFlowRules.parseReceiptOrNull(pabiliReceiptInput)
    }
    val hasReceiptText = pabiliReceiptInput.trim().isNotEmpty()
    val isReceiptFormatInvalid = hasReceiptText && parsedReceipt == null
    val isOverBudget = isPabili && budgetLimit > 0.0 && parsedReceipt != null && parsedReceipt > budgetLimit

    LaunchedEffect(isOverBudget) {
        if (!isOverBudget) {
            overBudgetApproved = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Checklist, contentDescription = null, tint = DelivioGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Pick up Checklist",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Verify items at ${currentStop.name} (${currentStop.displayOrderNumber})",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isPabili && budgetLimit > 0.0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = DelivioYellow.copy(alpha = 0.22f),
                        border = BorderStroke(0.5.dp, DelivioYellowDark.copy(alpha = 0.45f)),
                        modifier = Modifier.testTag("checklist_budget_limit_chip")
                    ) {
                        Text(
                            text = "Budget limit $currencySymbol${formatPaymentAmount(budgetLimit)}",
                            color = TextOnYellow,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(10.dp))

                if (currentStop.order.splitOut.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF3E5F5),
                        border = BorderStroke(1.dp, Color(0xFFCE93D8)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.AltRoute,
                                contentDescription = null,
                                tint = Color(0xFF7B1FA2),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Another rider is picking up part of this order",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF7B1FA2)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    currentStop.items.forEachIndexed { idx, item ->
                        val isTakenByOther = currentStop.order.splitOut.any { splitName ->
                            val sn = splitName.trim()
                            sn.equals(item.name.trim(), ignoreCase = true) ||
                                sn.equals(item.displayName.trim(), ignoreCase = true)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .clickable(enabled = !isTakenByOther) {
                                    checkedItems[idx] = !(checkedItems[idx] ?: false)
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isTakenByOther) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Taken",
                                    tint = Color(0xFF7B1FA2),
                                    modifier = Modifier.size(24.dp)
                                )
                            } else {
                                Checkbox(
                                    checked = checkedItems[idx] ?: false,
                                    onCheckedChange = { checkedItems[idx] = it }
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = item.displayNameWithQty,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = if (isTakenByOther) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isTakenByOther) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFFEDE7F6)
                                        ) {
                                            Text(
                                                text = "Taken by another rider",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF7B1FA2),
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                                val validSelections = item.selections
                                    .map { it.trim() }
                                    .filter { it.isNotBlank() && it != "()" && it != "( )" }
                                validSelections.forEach { sel ->
                                    Text("  └ $sel", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                if (isPabili) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pabiliReceiptInput,
                        onValueChange = { input ->
                            // Allow only digits and at most one decimal point with up to 2 decimal places
                            if (input.isEmpty() || Regex("^\\d*(\\.\\d{0,2})?$").matches(input)) {
                                onPabiliReceiptChange(input)
                            }
                        },
                        label = { Text("Final Receipt Total ($currencySymbol)") },
                        placeholder = { Text("0.00") },
                        singleLine = true,
                        isError = isReceiptFormatInvalid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pabili_receipt_input")
                    )

                    if (isReceiptFormatInvalid) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Enter a valid amount greater than 0 (max 2 decimal places)",
                            fontSize = 11.sp,
                            color = DelivioRed,
                            modifier = Modifier.testTag("pabili_receipt_error")
                        )
                    }

                    if (isOverBudget) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = DelivioRed.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, DelivioRed.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pabili_over_budget_warning")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = DelivioRed,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Over the customer's budget limit ($currencySymbol${formatPaymentAmount(budgetLimit)}). Ask the customer first.",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DelivioRed
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .clickable { overBudgetApproved = !overBudgetApproved }
                                .testTag("pabili_over_budget_checkbox_row"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = overBudgetApproved,
                                onCheckedChange = { overBudgetApproved = it },
                                modifier = Modifier.testTag("pabili_over_budget_checkbox")
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "The customer approved the extra amount",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                val allRequiredChecked = currentStop.items.indices.all { idx ->
                    val itm = currentStop.items[idx]
                    val isTaken = currentStop.order.splitOut.any {
                        val sn = it.trim()
                        sn.equals(itm.name.trim(), ignoreCase = true) ||
                            sn.equals(itm.displayName.trim(), ignoreCase = true)
                    }
                    isTaken || (checkedItems[idx] == true)
                }
                val isPabiliValidToConfirm = !isPabili || (
                    parsedReceipt != null && (!isOverBudget || overBudgetApproved)
                    )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = { onConfirmPickedUp(parsedReceipt, isOverBudget && overBudgetApproved) },
                        enabled = allRequiredChecked && isPabiliValidToConfirm,
                        colors = ButtonDefaults.buttonColors(containerColor = DelivioGreen, contentColor = Color.White),
                        modifier = Modifier
                            .weight(1.3f)
                            .heightIn(min = 48.dp)
                            .testTag("checklist_confirm_button")
                    ) {
                        Text("Confirm Picked Up", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CodCollectionDialog(
    currentStop: RouteStop,
    currencySymbol: String,
    onConfirmDelivery: () -> Unit,
    onDismiss: () -> Unit
) {
    var isConfirmed by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocalAtm, contentDescription = null, tint = Color(0xFFE65100))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "COD Cash Collection",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Collect cash from ${currentStop.name} (${currentStop.displayOrderNumber})",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (currentStop.order.hasSplitOut && !currentStop.order.isSplitChild) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF3E5F5),
                        border = BorderStroke(1.dp, Color(0xFFCE93D8)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.AltRoute,
                                contentDescription = null,
                                tint = Color(0xFF7B1FA2),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Other riders carry part of this order. You collect the full cash.",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF7B1FA2)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFFF3E0),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("CASH TO COLLECT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                        Text(
                            text = "$currencySymbol${String.format(Locale.US, "%.2f", currentStop.cashToCollect)}",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFBF360C)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .clickable { isConfirmed = !isConfirmed }
                        .testTag("cod_confirm_checkbox_row"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isConfirmed,
                        onCheckedChange = { isConfirmed = it },
                        modifier = Modifier.testTag("cod_confirm_checkbox")
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "I have received the exact cash amount ($currencySymbol${String.format(Locale.US, "%.2f", currentStop.cashToCollect)})",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (currentStop.order.isPabili) {
                    val paidOnMerchantAmt = currentStop.order.resolvedPaidOnMerchant
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 6.dp)
                            .testTag("cod_paid_on_merchant_note"),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Paid on Merchant $currencySymbol${String.format(Locale.US, "%.2f", paidOnMerchantAmt)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Items you paid at the store. Not part of the cash to collect.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = onConfirmDelivery,
                        enabled = isConfirmed,
                        colors = ButtonDefaults.buttonColors(containerColor = DelivioGreen, contentColor = Color.White),
                        modifier = Modifier
                            .weight(1.3f)
                            .heightIn(min = 48.dp)
                            .testTag("cod_complete_delivery_button")
                    ) {
                        Text("Complete Delivery", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
