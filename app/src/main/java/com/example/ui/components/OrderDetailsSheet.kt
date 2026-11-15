package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.constants.OrderStepConstants
import com.example.data.models.*
import com.example.ui.theme.*
import java.util.Locale

enum class OrderSheetState {
    PEEK,
    HALF,
    FULL
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
    sheetState: OrderSheetState = OrderSheetState.PEEK,
    onSheetStateChange: (OrderSheetState) -> Unit = {},
    onGoOnlineClick: () -> Unit,
    onAdvanceStep: (Double?, String?) -> Unit,
    onReleaseOrder: (String) -> Unit,
    onReportProblem: (String) -> Unit,
    onCallClick: (String, String) -> Unit,
    onChatClick: (Order) -> Unit = {},
    hasUnreadChat: (String) -> Boolean = { false },
    onHeadsetClick: () -> Unit = {},
    onFocusStop: (RouteStop) -> Unit = {},
    onOpenEarnings: () -> Unit = {},
    onHeightChangedPx: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    var showProblemDialog by remember { mutableStateOf(false) }
    var problemReason by remember { mutableStateOf("Merchant delay / waiting") }

    // Dialogs for Slide confirmations
    var showItemChecklistDialog by remember { mutableStateOf(false) }
    var showCodConfirmDialog by remember { mutableStateOf(false) }
    var pabiliReceiptInput by remember(currentStop?.orderId) {
        mutableStateOf(
            if ((currentStop?.order?.finalReceiptTotal ?: 0.0) > 0.0) {
                currentStop?.order?.finalReceiptTotal.toString()
            } else ""
        )
    }

    var selectedStopKey by remember(currentStop?.orderId, currentStop?.type) {
        mutableStateOf(currentStop?.let { "${it.orderId}_${it.type.name}" } ?: "")
    }
    val selectedStop = remember(batchStops, currentStop, selectedStopKey) {
        batchStops.firstOrNull { "${it.orderId}_${it.type.name}" == selectedStopKey } ?: currentStop
    }

    var showEarningsDetails by remember(activeOrders.size) { mutableStateOf(false) }

    // Swipe up/down gesture across PEEK <-> HALF <-> FULL (one step per gesture)
    val dragModifier = Modifier.pointerInput(sheetState) {
        var cumulativeDrag = 0f
        var transitioned = false
        detectVerticalDragGestures(
            onDragStart = {
                cumulativeDrag = 0f
                transitioned = false
            },
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                if (transitioned) return@detectVerticalDragGestures
                cumulativeDrag += dragAmount
                if (cumulativeDrag < -36f) {
                    transitioned = true
                    when (sheetState) {
                        OrderSheetState.PEEK -> onSheetStateChange(OrderSheetState.HALF)
                        OrderSheetState.HALF -> onSheetStateChange(OrderSheetState.FULL)
                        OrderSheetState.FULL -> {}
                    }
                } else if (cumulativeDrag > 36f) {
                    transitioned = true
                    when (sheetState) {
                        OrderSheetState.FULL -> onSheetStateChange(OrderSheetState.HALF)
                        OrderSheetState.HALF -> onSheetStateChange(OrderSheetState.PEEK)
                        OrderSheetState.PEEK -> {}
                    }
                }
            }
        )
    }

    val cardMaxHeight = when {
        activeOrders.isEmpty() || currentStop == null -> (screenHeight * 0.45f).coerceAtLeast(240.dp)
        sheetState == OrderSheetState.PEEK -> (screenHeight * 0.42f).coerceAtLeast(230.dp)
        sheetState == OrderSheetState.HALF -> (screenHeight * 0.56f).coerceAtLeast(360.dp)
        else -> (screenHeight * 0.80f).coerceAtLeast(460.dp)
    }

    Card(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .heightIn(max = cardMaxHeight)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
            .onSizeChanged { onHeightChangedPx(it.height) }
            .testTag("order_details_sheet")
    ) {
        if (activeOrders.isEmpty() || currentStop == null) {
            // ================= IDLE STATE (WRAPPED HEIGHT) =================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
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
            // ================= ACTIVE TRIP / BATCH =================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                // Drag Handle (supports swipe up/down AND tap to cycle PEEK -> HALF -> FULL -> PEEK)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(dragModifier)
                        .clickable {
                            val next = when (sheetState) {
                                OrderSheetState.PEEK -> OrderSheetState.HALF
                                OrderSheetState.HALF -> OrderSheetState.FULL
                                OrderSheetState.FULL -> OrderSheetState.PEEK
                            }
                            onSheetStateChange(next)
                        }
                        .padding(top = 10.dp, bottom = 6.dp)
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

                when (sheetState) {
                    OrderSheetState.PEEK -> {
                        PeekOrderSheetContent(
                            currentStop = currentStop,
                            totalStops = batchStops.size.coerceAtLeast(1),
                            routeDistanceKm = routeDistanceKm,
                            routeEtaMinutes = routeEtaMinutes,
                            dragModifier = dragModifier,
                            onExpandToHalf = { onSheetStateChange(OrderSheetState.HALF) },
                            onNavigateClick = { launchExternalNavigation(context, currentStop) },
                            onSlideConfirmed = {
                                handleSlideAction(
                                    currentStop = currentStop,
                                    onOpenChecklist = { showItemChecklistDialog = true },
                                    onOpenCodConfirm = { showCodConfirmDialog = true },
                                    onAdvanceDirect = { onAdvanceStep(null, currentStop.orderId) }
                                )
                            }
                        )
                    }

                    OrderSheetState.HALF -> {
                        HalfOrderSheetContent(
                            batchStops = batchStops,
                            currentStop = currentStop,
                            currencySymbol = currencySymbol,
                            routeDistanceKm = routeDistanceKm,
                            routeEtaMinutes = routeEtaMinutes,
                            onNavigateClick = { launchExternalNavigation(context, currentStop) },
                            onCallClick = {
                                if (currentStop.phone.isNotBlank()) {
                                    onCallClick(currentStop.name, currentStop.phone)
                                }
                            },
                            onChatClick = onChatClick,
                            hasUnreadChat = hasUnreadChat,
                            onHeadsetClick = onHeadsetClick,
                            onSelectStop = { stop ->
                                selectedStopKey = "${stop.orderId}_${stop.type.name}"
                                onFocusStop(stop)
                                onSheetStateChange(OrderSheetState.FULL)
                            },
                            onExpandToFull = { onSheetStateChange(OrderSheetState.FULL) },
                            onSlideConfirmed = {
                                handleSlideAction(
                                    currentStop = currentStop,
                                    onOpenChecklist = { showItemChecklistDialog = true },
                                    onOpenCodConfirm = { showCodConfirmDialog = true },
                                    onAdvanceDirect = { onAdvanceStep(null, currentStop.orderId) }
                                )
                            }
                        )
                    }

                    OrderSheetState.FULL -> {
                        FullOrderSheetContent(
                            batchStops = batchStops,
                            currentStop = currentStop,
                            selectedStop = selectedStop ?: currentStop,
                            activeOrders = activeOrders,
                            currencySymbol = currencySymbol,
                            routeDistanceKm = routeDistanceKm,
                            routeEtaMinutes = routeEtaMinutes,
                            showEarningsDetails = showEarningsDetails,
                            onToggleEarningsDetails = { showEarningsDetails = !showEarningsDetails },
                            onSelectStop = { stop ->
                                selectedStopKey = "${stop.orderId}_${stop.type.name}"
                                onFocusStop(stop)
                            },
                            onNavigateClick = { launchExternalNavigation(context, currentStop) },
                            onCallClick = {
                                val target = selectedStop ?: currentStop
                                if (target.phone.isNotBlank()) {
                                    onCallClick(target.name, target.phone)
                                }
                            },
                            onChatClick = onChatClick,
                            hasUnreadChat = hasUnreadChat,
                            onHeadsetClick = onHeadsetClick,
                            onCollapseToHalf = { onSheetStateChange(OrderSheetState.HALF) },
                            onReportProblemClick = { showProblemDialog = true },
                            onSlideConfirmed = {
                                handleSlideAction(
                                    currentStop = currentStop,
                                    onOpenChecklist = { showItemChecklistDialog = true },
                                    onOpenCodConfirm = { showCodConfirmDialog = true },
                                    onAdvanceDirect = { onAdvanceStep(null, currentStop.orderId) }
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    // Item Checklist Dialog (for Slide to confirm Picked Up at Merchant)
    if (showItemChecklistDialog && currentStop != null) {
        ItemChecklistDialog(
            currentStop = currentStop,
            pabiliReceiptInput = pabiliReceiptInput,
            onPabiliReceiptChange = { pabiliReceiptInput = it },
            onConfirmPickedUp = {
                val receipt = pabiliReceiptInput.toDoubleOrNull()
                showItemChecklistDialog = false
                onAdvanceStep(receipt, currentStop.orderId)
            },
            onDismiss = { showItemChecklistDialog = false }
        )
    }

    // COD Cash Confirmation Dialog (for Slide to Complete Delivery at Customer)
    if (showCodConfirmDialog && currentStop != null) {
        CodCollectionDialog(
            currentStop = currentStop,
            currencySymbol = currencySymbol,
            onConfirmDelivery = {
                showCodConfirmDialog = false
                onAdvanceStep(null, currentStop.orderId)
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
}

private fun launchExternalNavigation(context: Context, stop: RouteStop) {
    val geoUri = Uri.parse("geo:${stop.lat},${stop.lng}?q=${stop.lat},${stop.lng}(${Uri.encode(stop.name)})")
    val navIntent = Intent(Intent.ACTION_VIEW, geoUri)
    try {
        context.startActivity(Intent.createChooser(navIntent, "Navigate with"))
    } catch (_: Exception) {
        Toast.makeText(context, "No navigation app installed", Toast.LENGTH_SHORT).show()
    }
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

private fun handleSlideAction(
    currentStop: RouteStop,
    onOpenChecklist: () -> Unit,
    onOpenCodConfirm: () -> Unit,
    onAdvanceDirect: () -> Unit
) {
    val step = currentStop.order.riderStep.lowercase().trim()
    if (currentStop.isMerchant) {
        val atMerchantSteps = setOf(
            OrderStepConstants.STEP_AT_MERCHANT,
            OrderStepConstants.STEP_ARRIVED_AT_MERCHANT,
            OrderStepConstants.STEP_AT_SENDER,
            OrderStepConstants.STEP_AT_VENDOR,
            OrderStepConstants.STEP_AT_PICKUP
        )
        if (step in atMerchantSteps) {
            onOpenChecklist()
        } else {
            onAdvanceDirect()
        }
    } else {
        val atCustomerSteps = setOf(
            OrderStepConstants.STEP_AT_CUSTOMER,
            OrderStepConstants.STEP_ARRIVED_AT_CUSTOMER,
            OrderStepConstants.STEP_AT_RECIPIENT
        )
        if (step in atCustomerSteps) {
            val isSplitChild = currentStop.order.isSplitChild || currentStop.order.badge.equals("split", ignoreCase = true)
            if (currentStop.isCod && !isSplitChild) {
                onOpenCodConfirm()
            } else {
                onAdvanceDirect()
            }
        } else {
            onAdvanceDirect()
        }
    }
}

private fun getSlideButtonLabel(stop: RouteStop): String {
    val step = stop.order.riderStep.lowercase().trim()
    return if (stop.isMerchant) {
        val atMerchantSteps = setOf(
            OrderStepConstants.STEP_AT_MERCHANT,
            OrderStepConstants.STEP_ARRIVED_AT_MERCHANT,
            OrderStepConstants.STEP_AT_SENDER,
            OrderStepConstants.STEP_AT_VENDOR,
            OrderStepConstants.STEP_AT_PICKUP
        )
        if (step in atMerchantSteps) {
            "Slide to confirm Picked Up"
        } else {
            "Slide to confirm Arrival at ${stop.name}"
        }
    } else {
        val atCustomerSteps = setOf(
            OrderStepConstants.STEP_AT_CUSTOMER,
            OrderStepConstants.STEP_ARRIVED_AT_CUSTOMER,
            OrderStepConstants.STEP_AT_RECIPIENT
        )
        if (step in atCustomerSteps) {
            "Slide to Complete Delivery"
        } else {
            "Slide to confirm Arrival at Customer"
        }
    }
}

@Composable
private fun StepBadgeRow(
    currentStop: RouteStop,
    totalStops: Int
) {
    val actionLabel = if (currentStop.isMerchant) "Pick up" else "Drop off"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (currentStop.isMerchant) DelivioGreen.copy(alpha = 0.14f) else DelivioRed.copy(alpha = 0.14f)
        ) {
            Text(
                text = "Stop ${currentStop.stopNumber} of $totalStops - $actionLabel",
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = if (currentStop.isMerchant) DelivioGreenDark else DelivioRed,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
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
private fun PeekOrderSheetContent(
    currentStop: RouteStop,
    totalStops: Int,
    routeDistanceKm: Double,
    routeEtaMinutes: Int,
    dragModifier: Modifier,
    onExpandToHalf: () -> Unit,
    onNavigateClick: () -> Unit,
    onSlideConfirmed: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(dragModifier)
                .clickable { onExpandToHalf() }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StepBadgeRow(currentStop = currentStop, totalStops = totalStops)
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Expand trip details",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentStop.name,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val etaPart = if (routeEtaMinutes > 0) "$routeEtaMinutes min" else "Calculating ETA"
                    val distPart = if (routeDistanceKm > 0.0) " • ${String.format(Locale.US, "%.1f", routeDistanceKm)} km" else ""
                    Text(
                        text = "$etaPart$distPart",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Button(
                    onClick = onNavigateClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DelivioYellow,
                        contentColor = TextOnYellow
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("navigate_stop_button")
                ) {
                    Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Navigate", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        SlideToConfirmButton(
            text = getSlideButtonLabel(currentStop),
            onConfirmed = onSlideConfirmed,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("sheet_slide_button")
        )
    }
}

@Composable
private fun HalfOrderSheetContent(
    batchStops: List<RouteStop>,
    currentStop: RouteStop,
    currencySymbol: String,
    routeDistanceKm: Double,
    routeEtaMinutes: Int,
    onNavigateClick: () -> Unit,
    onCallClick: () -> Unit,
    onChatClick: (Order) -> Unit,
    hasUnreadChat: (String) -> Boolean,
    onHeadsetClick: () -> Unit,
    onSelectStop: (RouteStop) -> Unit,
    onExpandToFull: () -> Unit,
    onSlideConfirmed: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp)
    ) {
        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                StepBadgeRow(currentStop = currentStop, totalStops = batchStops.size.coerceAtLeast(1))
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = currentStop.name,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val etaText = buildString {
                    if (routeEtaMinutes > 0) append("$routeEtaMinutes min")
                    if (routeDistanceKm > 0.0) {
                        if (isNotEmpty()) append(" • ")
                        append("${String.format(Locale.US, "%.1f", routeDistanceKm)} km")
                    }
                }
                if (etaText.isNotBlank()) {
                    Text(
                        text = etaText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onNavigateClick,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("half_navigate_button")
                ) {
                    Icon(Icons.Default.Navigation, contentDescription = "Navigate", tint = DelivioYellow)
                }
                IconButton(
                    onClick = onExpandToFull,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("expand_sheet_button")
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Full details", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Scrollable compact stop timeline + one payment line
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            batchStops.forEach { stop ->
                CompactTimelineStopRow(
                    stop = stop,
                    isSelected = stop.orderId == currentStop.orderId && stop.type == currentStop.type,
                    onClick = { onSelectStop(stop) }
                )
            }

            // One payment line
            OnePaymentLineBar(
                order = currentStop.order,
                currencySymbol = currencySymbol
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Round icon action buttons row (Call, Chat, Headset) + Slide button
        SheetQuickActionIconsRow(
            currentStop = currentStop,
            onCallClick = onCallClick,
            onChatClick = onChatClick,
            hasUnreadChat = hasUnreadChat,
            onHeadsetClick = onHeadsetClick
        )

        Spacer(modifier = Modifier.height(10.dp))

        SlideToConfirmButton(
            text = getSlideButtonLabel(currentStop),
            onConfirmed = onSlideConfirmed,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("sheet_slide_button")
        )
    }
}

@Composable
private fun FullOrderSheetContent(
    batchStops: List<RouteStop>,
    currentStop: RouteStop,
    selectedStop: RouteStop,
    activeOrders: List<Order>,
    currencySymbol: String,
    routeDistanceKm: Double,
    routeEtaMinutes: Int,
    showEarningsDetails: Boolean,
    onToggleEarningsDetails: () -> Unit,
    onSelectStop: (RouteStop) -> Unit,
    onNavigateClick: () -> Unit,
    onCallClick: () -> Unit,
    onChatClick: (Order) -> Unit,
    hasUnreadChat: (String) -> Boolean,
    onHeadsetClick: () -> Unit,
    onCollapseToHalf: () -> Unit,
    onReportProblemClick: () -> Unit,
    onSlideConfirmed: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                StepBadgeRow(currentStop = currentStop, totalStops = batchStops.size.coerceAtLeast(1))
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = currentStop.name,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onNavigateClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Navigation, contentDescription = "Navigate", tint = DelivioYellow)
                }
                IconButton(
                    onClick = onCollapseToHalf,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("collapse_sheet_button")
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Collapse", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Scrollable Details Body
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Stop Timeline (tap any stop to view its items)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                batchStops.forEach { stop ->
                    CompactTimelineStopRow(
                        stop = stop,
                        isSelected = stop.orderId == selectedStop.orderId && stop.type == selectedStop.type,
                        onClick = { onSelectStop(stop) }
                    )
                }
            }

            // Items & Notes for selected stop
            SelectedStopItemsCard(
                stop = selectedStop,
                currencySymbol = currencySymbol
            )

            // One Payment Line
            OnePaymentLineBar(
                order = selectedStop.order,
                currencySymbol = currencySymbol
            )

            // Round Action Buttons (Call, Chat, Headset)
            SheetQuickActionIconsRow(
                currentStop = selectedStop,
                onCallClick = onCallClick,
                onChatClick = onChatClick,
                hasUnreadChat = hasUnreadChat,
                onHeadsetClick = onHeadsetClick
            )

            // Collapsed "Earnings details" row
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
                        val totalEarned = activeOrders.sumOf { it.estimatedEarnings }
                        Text(
                            text = "Earnings details ($currencySymbol${String.format("%.2f", totalEarned)})",
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
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Slide Button
        SlideToConfirmButton(
            text = getSlideButtonLabel(currentStop),
            onConfirmed = onSlideConfirmed,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("sheet_slide_button")
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Report a problem button
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

@Composable
private fun CompactTimelineStopRow(
    stop: RouteStop,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = when {
            isSelected -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            stop.status == StopStatus.DONE -> MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        },
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 0.5.dp,
            color = if (isSelected) DelivioYellow else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
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
            if (stop.items.isNotEmpty()) {
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
    val isCashCollect = !order.isSplitChild && !order.badge.equals("split", ignoreCase = true) &&
        ((order.isRequestRider && order.cashAmountToCollect > 0.0) || (!order.isRequestRider && order.isCod && order.cashAmountToCollect > 0.0))

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isCashCollect) Color(0xFFFFF3E0) else DelivioGreen.copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth()
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

@Composable
private fun SheetQuickActionIconsRow(
    currentStop: RouteStop,
    onCallClick: () -> Unit,
    onChatClick: (Order) -> Unit,
    hasUnreadChat: (String) -> Boolean,
    onHeadsetClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (currentStop.phone.isNotBlank()) {
            Surface(
                shape = CircleShape,
                color = DelivioGreen.copy(alpha = 0.16f),
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onCallClick)
                    .testTag("sheet_call_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Call",
                        tint = DelivioGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        if (currentStop.isCustomer && !OrderChatConstants.isInstantOrder(currentStop.order.serviceType)) {
            Box(modifier = Modifier.size(48.dp)) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable { onChatClick(currentStop.order) }
                        .testTag("customer_chat_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.ChatBubble,
                            contentDescription = "Chat",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                if (hasUnreadChat(currentStop.order.id)) {
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
        }

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(onClick = onHeadsetClick)
                .testTag("sheet_headset_button")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.HeadsetMic,
                    contentDescription = "Support options",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun SelectedStopItemsCard(
    stop: RouteStop,
    currencySymbol: String
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
                Text(
                    text = if (stop.isMerchant) "Items to Pick Up" else "Items to Deliver",
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

            if (stop.order.customerNotes.isNotBlank()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.StickyNote2,
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

        val pickupFee = activeOrders.firstOrNull()?.riderEarning?.pickupFee ?: 0.0
        if (pickupFee > 0.0) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Stop 1 Pickup Fee", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$currencySymbol${String.format("%.2f", pickupFee)}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        activeOrders.forEachIndexed { idx, order ->
            val delivFee = order.riderEarning?.bookingFee ?: order.riderFeeEstimate.coerceAtLeast(order.deliveryFee * 0.8)
            val label = if (activeOrders.size > 1) "Order ${idx + 1} Delivery Fee" else "Delivery Fee"
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$currencySymbol${String.format("%.2f", delivFee)}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            if (order.tip > 0) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("  + Customer Tip", fontSize = 11.sp, color = DelivioGreenDark)
                    Text("+$currencySymbol${String.format("%.2f", order.tip)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DelivioGreenDark)
                }
            }
        }

        val totalEarned = activeOrders.sumOf { it.estimatedEarnings }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Estimated Total Pay", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
            Text("$currencySymbol${String.format("%.2f", totalEarned)}", fontWeight = FontWeight.Black, fontSize = 14.sp, color = DelivioYellowDark)
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
    pabiliReceiptInput: String,
    onPabiliReceiptChange: (String) -> Unit,
    onConfirmPickedUp: () -> Unit,
    onDismiss: () -> Unit
) {
    val checkedItems = remember(currentStop.items) {
        mutableStateMapOf<Int, Boolean>().apply {
            currentStop.items.indices.forEach { put(it, true) }
        }
    }

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
                        .heightIn(max = 240.dp)
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

                if (currentStop.serviceType.lowercase().contains("pabili")) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pabiliReceiptInput,
                        onValueChange = onPabiliReceiptChange,
                        label = { Text("Final Receipt Total (₱)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
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
                        onClick = onConfirmPickedUp,
                        enabled = allRequiredChecked,
                        colors = ButtonDefaults.buttonColors(containerColor = DelivioGreen, contentColor = Color.White),
                        modifier = Modifier
                            .weight(1.3f)
                            .heightIn(min = 48.dp)
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
                            text = "$currencySymbol${String.format("%.2f", currentStop.cashToCollect)}",
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
                        .clickable { isConfirmed = !isConfirmed },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isConfirmed,
                        onCheckedChange = { isConfirmed = it }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "I have received the exact cash amount ($currencySymbol${String.format("%.2f", currentStop.cashToCollect)})",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
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
                    ) {
                        Text("Complete Delivery", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
