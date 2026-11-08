package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
import com.example.data.models.Order
import com.example.data.models.OrderItem
import com.example.data.models.RouteStop
import com.example.data.models.StopStatus
import com.example.data.models.StopType
import com.example.ui.theme.*

enum class OrderSheetState {
    HIDDEN,
    COMPACT,
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
    sheetState: OrderSheetState = OrderSheetState.COMPACT,
    onSheetStateChange: (OrderSheetState) -> Unit = {},
    onGoOnlineClick: () -> Unit,
    onAdvanceStep: (Double?, String?) -> Unit,
    onReleaseOrder: (String) -> Unit,
    onReportProblem: (String) -> Unit,
    onCallClick: (String, String) -> Unit,
    onFocusStop: (RouteStop) -> Unit = {},
    onOpenEarnings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp

    var showProblemDialog by remember { mutableStateOf(false) }
    var problemReason by remember { mutableStateOf("Merchant delay / waiting") }
    var showReleaseDialog by remember { mutableStateOf(false) }
    var releaseReason by remember { mutableStateOf("Emergency / Flat tire") }

    // Dialogs for Slide confirmations
    var showItemChecklistDialog by remember { mutableStateOf(false) }
    var showCodConfirmDialog by remember { mutableStateOf(false) }
    var pabiliReceiptInput by remember(currentStop?.orderId) {
        mutableStateOf(if ((currentStop?.order?.finalReceiptTotal ?: 0.0) > 0.0) currentStop?.order?.finalReceiptTotal.toString() else "")
    }

    // Spring animation for sheet snap height
    val targetHeight = if (activeOrders.isEmpty()) {
        185.dp
    } else {
        when (sheetState) {
            OrderSheetState.HIDDEN -> 64.dp
            OrderSheetState.COMPACT -> (screenHeight * 0.32f).coerceIn(225.dp, 280.dp)
            OrderSheetState.EXPANDED -> (screenHeight * 0.65f).coerceAtLeast(420.dp)
        }
    }

    val animatedHeight by animateDpAsState(
        targetValue = targetHeight,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "order_sheet_height"
    )

    // Vertical drag gesture for snap states
    val dragModifier = Modifier.pointerInput(sheetState) {
        detectVerticalDragGestures { change, dragAmount ->
            change.consume()
            if (dragAmount < -18f) {
                when (sheetState) {
                    OrderSheetState.HIDDEN -> onSheetStateChange(OrderSheetState.COMPACT)
                    OrderSheetState.COMPACT -> onSheetStateChange(OrderSheetState.EXPANDED)
                    OrderSheetState.EXPANDED -> {}
                }
            } else if (dragAmount > 18f) {
                when (sheetState) {
                    OrderSheetState.EXPANDED -> onSheetStateChange(OrderSheetState.COMPACT)
                    OrderSheetState.COMPACT -> onSheetStateChange(OrderSheetState.HIDDEN)
                    OrderSheetState.HIDDEN -> {}
                }
            }
        }
    }

    Card(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(animatedHeight)
            .testTag("order_details_sheet")
    ) {
        if (activeOrders.isEmpty() || currentStop == null) {
            // ================= IDLE STATE =================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(44.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
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
                        Button(
                            onClick = onGoOnlineClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DelivioGreen,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text("Go Online", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenEarnings() },
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
                    .then(dragModifier)
            ) {
                // Drag Handle
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
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
                    OrderSheetState.HIDDEN -> {
                        // Minimal bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .clickable { onSheetStateChange(OrderSheetState.COMPACT) },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StopNumberBadge(stop = currentStop, size = 28)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = currentStop.displayActionTitle,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (routeEtaMinutes > 0) {
                                Text(
                                    text = "$routeEtaMinutes min • ${String.format("%.1f", routeDistanceKm)} km",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DelivioYellowDark
                                )
                            }
                        }
                    }

                    OrderSheetState.COMPACT -> {
                        // ================= COMPACT STATE =================
                        CompactOrderSheetContent(
                            currentStop = currentStop,
                            routeDistanceKm = routeDistanceKm,
                            routeEtaMinutes = routeEtaMinutes,
                            onCallClick = {
                                if (currentStop.phone.isNotBlank()) {
                                    onCallClick(currentStop.name, currentStop.phone)
                                }
                            },
                            onExpandClick = { onSheetStateChange(OrderSheetState.EXPANDED) },
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

                    OrderSheetState.EXPANDED -> {
                        // ================= EXPANDED STATE =================
                        ExpandedOrderSheetContent(
                            batchStops = batchStops,
                            currentStop = currentStop,
                            activeOrders = activeOrders,
                            currencySymbol = currencySymbol,
                            routeDistanceKm = routeDistanceKm,
                            routeEtaMinutes = routeEtaMinutes,
                            onFocusStop = onFocusStop,
                            onCollapseClick = { onSheetStateChange(OrderSheetState.COMPACT) },
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
                    colors = ButtonDefaults.buttonColors(containerColor = DelivioRed, contentColor = Color.White)
                ) {
                    Text("Submit Report", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showProblemDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Handles slide button action based on current stop type and order riderStep.
 */
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

/**
 * Returns the exact slide button text required:
 * Merchant: "Slide to confirm Arrival at <merchant>" then "Slide to confirm Picked Up"
 * Customer: "Slide to confirm Arrival at Customer" then "Slide to Complete Delivery"
 */
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
private fun CompactOrderSheetContent(
    currentStop: RouteStop,
    routeDistanceKm: Double,
    routeEtaMinutes: Int,
    onCallClick: () -> Unit,
    onExpandClick: () -> Unit,
    onSlideConfirmed: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onExpandClick() },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StopNumberBadge(stop = currentStop, size = 32)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = currentStop.displayActionTitle,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (currentStop.order.isSplitChild || currentStop.order.badge.equals("split", ignoreCase = true)) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF7B1FA2)
                            ) {
                                Text(
                                    text = "SPLIT",
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 9.sp,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (currentStop.order.isScheduled) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF1565C0)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = "Scheduled",
                                        tint = Color.White,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = if (currentStop.order.scheduledDateText.isNotBlank()) "SCHEDULED • ${currentStop.order.scheduledDateText}" else "SCHEDULED",
                                        color = Color.White,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        text = currentStop.address,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Call icon
                if (currentStop.phone.isNotBlank()) {
                    IconButton(
                        onClick = onCallClick,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(DelivioGreen.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Call",
                            tint = DelivioGreen,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                // ETA badge
                if (routeEtaMinutes > 0) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = DelivioYellowLight,
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Text(
                            text = "$routeEtaMinutes min",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = TextOnYellow,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        if (currentStop.isCustomer && (currentStop.order.isSplitChild || currentStop.order.badge.equals("split", ignoreCase = true))) {
            Spacer(modifier = Modifier.height(8.dp))
            NoCashCollectedChip(isLarge = false)
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ONE slide button for the action
        SlideToConfirmButton(
            text = getSlideButtonLabel(currentStop),
            onConfirmed = onSlideConfirmed,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ExpandedOrderSheetContent(
    batchStops: List<RouteStop>,
    currentStop: RouteStop,
    activeOrders: List<Order>,
    currencySymbol: String,
    routeDistanceKm: Double,
    routeEtaMinutes: Int,
    onFocusStop: (RouteStop) -> Unit,
    onCollapseClick: () -> Unit,
    onReportProblemClick: () -> Unit,
    onSlideConfirmed: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .verticalScroll(scrollState)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Trip Stops (${batchStops.size} Stops)",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp
                )
                Text(
                    text = "Tap a stop to focus it on the map",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onCollapseClick) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Collapse", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Vertical Timeline 1..N
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            batchStops.forEachIndexed { index, stop ->
                TimelineStopCard(
                    stop = stop,
                    currencySymbol = currencySymbol,
                    isLast = index == batchStops.size - 1,
                    onTap = { onFocusStop(stop) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(12.dp))

        // Pay Summary
        PaySummarySection(
            activeOrders = activeOrders,
            currencySymbol = currencySymbol
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Slide Button
        SlideToConfirmButton(
            text = getSlideButtonLabel(currentStop),
            onConfirmed = onSlideConfirmed,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Report a problem button
        OutlinedButton(
            onClick = onReportProblemClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = DelivioRed)
        ) {
            Icon(Icons.Default.ReportProblem, contentDescription = null, modifier = Modifier.size(16.dp), tint = DelivioRed)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Report a Problem", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun TimelineStopCard(
    stop: RouteStop,
    currencySymbol: String,
    isLast: Boolean,
    onTap: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (stop.status) {
                StopStatus.CURRENT -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                StopStatus.DONE -> MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                StopStatus.NEXT -> MaterialTheme.colorScheme.surface
            }
        ),
        border = if (stop.status == StopStatus.CURRENT) {
            androidx.compose.foundation.BorderStroke(1.5.dp, DelivioYellow)
        } else {
            androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTap() }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header row: Badge, Type, Status, OrderNumber
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StopNumberBadge(stop = stop, size = 26)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (stop.isMerchant) "PICKUP" else "DROP-OFF",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (stop.isMerchant) DelivioGreenDark else Color(0xFFC62828)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stop.displayOrderNumber,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (stop.order.isSplitChild || stop.order.badge.equals("split", ignoreCase = true)) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF7B1FA2)
                        ) {
                            Text(
                                text = "SPLIT",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                        if (stop.isCustomer) {
                            Spacer(modifier = Modifier.width(6.dp))
                            NoCashCollectedChip(isLarge = false)
                        }
                    }
                    if (stop.order.isScheduled) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF1565C0)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = "Scheduled",
                                    tint = Color.White,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = if (stop.order.scheduledDateText.isNotBlank()) "SCHEDULED • ${stop.order.scheduledDateText}" else "SCHEDULED",
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }
                }

                // Status chip: Done, Current, Next
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when (stop.status) {
                        StopStatus.DONE -> DelivioGreen.copy(alpha = 0.15f)
                        StopStatus.CURRENT -> DelivioYellow.copy(alpha = 0.25f)
                        StopStatus.NEXT -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Text(
                        text = when (stop.status) {
                            StopStatus.DONE -> "✓ Done"
                            StopStatus.CURRENT -> "● Current"
                            StopStatus.NEXT -> "Next"
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (stop.status) {
                            StopStatus.DONE -> DelivioGreenDark
                            StopStatus.CURRENT -> TextOnYellow
                            StopStatus.NEXT -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Name & Address
            Text(
                text = stop.name,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Text(
                text = stop.address,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (stop.order.isScheduled) {
                Spacer(modifier = Modifier.height(4.dp))
                ScheduledOrderChip(order = stop.order, isLarge = false)
            }

            // Items & selections list
            if (stop.items.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(6.dp))

                // Split delivery banner for first rider
                if (stop.order.splitOut.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF3E5F5),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCE93D8)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
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

                Text(
                    text = if (stop.isMerchant) "Items to Pick Up:" else "Items to Deliver:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                stop.items.forEach { item ->
                    val isTakenByOther = stop.order.splitOut.any { splitName ->
                        splitName.trim().equals(item.name.trim(), ignoreCase = true)
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
                                    text = "• ${item.name}",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = if (isTakenByOther) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
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
                                    color = if (isTakenByOther) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (item.price > 0.0) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "$currencySymbol${String.format("%.2f", item.price * item.quantity)}",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        color = if (isTakenByOther) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        // Selections as small indented lines under item name (never show empty "()")
                        val validSelections = item.selections
                            .map { it.trim() }
                            .filter { it.isNotBlank() && it != "()" && it != "( )" }

                        validSelections.forEach { sel ->
                            Text(
                                text = "  └ $sel",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                    }
                }
            }

            // Money lines in order details (Requirement 2):
            // - Cash on delivery: show the product lines with prices, the Tip (if any), and "Total to collect ₱X" (cashToCollect or fees.total). Never show subtotal, service fee, VAT, delivery fee, or discount.
            // - Paid online or wallet: show the product lines, then "Paid online - no cash to collect". Do NOT show a total, tip, or any fee line.
            if (stop.isCustomer) {
                Spacer(modifier = Modifier.height(8.dp))
                if (stop.order.isSplitChild || stop.order.badge.equals("split", ignoreCase = true)) {
                    NoCashCollectedChip(modifier = Modifier.fillMaxWidth(), isLarge = true)
                } else if (stop.isCod) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFFFF3E0))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (stop.order.hasSplitOut && !stop.order.isSplitChild) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFF3E5F5),
                                border = BorderStroke(1.dp, Color(0xFFCE93D8)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.AltRoute,
                                        contentDescription = null,
                                        tint = Color(0xFF7B1FA2),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Other riders carry part of this order. You collect the full cash.",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF7B1FA2)
                                    )
                                }
                            }
                        }
                        if (stop.order.tip > 0.0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Tip",
                                    fontSize = 12.sp,
                                    color = Color(0xFFE65100),
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "$currencySymbol${String.format("%.2f", stop.order.tip)}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE65100)
                                )
                            }
                        }

                        val totalToCollect = if (stop.cashToCollect > 0.0) stop.cashToCollect else stop.order.feesTotal
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocalAtm, contentDescription = "COD", tint = Color(0xFFE65100), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Total to collect",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp,
                                    color = Color(0xFFBF360C)
                                )
                            }
                            Text(
                                text = "$currencySymbol${String.format("%.2f", totalToCollect)}",
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp,
                                color = Color(0xFFBF360C)
                            )
                        }
                    }
                } else {
                    // Paid online or wallet
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = DelivioGreen.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Paid Online", tint = DelivioGreenDark, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Paid online - no cash to collect",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = DelivioGreenDark
                            )
                        }
                    }
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
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Batch Driver Pay Summary", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            HorizontalDivider()

            val pickupFee = activeOrders.firstOrNull()?.riderEarning?.pickupFee ?: 15.0
            val isBatch = activeOrders.size > 1

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Stop 1 Pickup Fee", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$currencySymbol${String.format("%.2f", pickupFee)}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            activeOrders.forEachIndexed { idx, order ->
                val delivFee = order.riderEarning?.bookingFee ?: order.riderFeeEstimate.coerceAtLeast(order.deliveryFee * 0.8)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Order ${order.displayOrderNumber} Delivery Fee (Rank ${idx + 1})", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            HorizontalDivider()
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Estimated Total Pay", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                Text("$currencySymbol${String.format("%.2f", totalEarned)}", fontWeight = FontWeight.Black, fontSize = 15.sp, color = DelivioYellowDark)
            }
        }
    }
}

@Composable
private fun StopNumberBadge(stop: RouteStop, size: Int = 30) {
    val bgColor = if (stop.isMerchant) {
        Color(0xFF2E7D32) // Green
    } else {
        Color(0xFFE53935) // Red
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

/**
 * Item checklist dialog for "Slide to confirm Picked Up"
 */
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

                // Split delivery banner for first rider
                if (currentStop.order.splitOut.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF3E5F5),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCE93D8)),
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

                // Items list
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    currentStop.items.forEachIndexed { idx, item ->
                        val isTakenByOther = currentStop.order.splitOut.any { splitName ->
                            splitName.trim().equals(item.name.trim(), ignoreCase = true)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
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
                                        text = "${item.name} x${item.quantity}",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = if (isTakenByOther) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
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

                // If Pabili, prompt for final receipt total
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
                    val isTaken = currentStop.order.splitOut.any { it.trim().equals(itm.name.trim(), ignoreCase = true) }
                    isTaken || (checkedItems[idx] == true)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = onConfirmPickedUp,
                        enabled = allRequiredChecked,
                        colors = ButtonDefaults.buttonColors(containerColor = DelivioGreen, contentColor = Color.White),
                        modifier = Modifier.weight(1.3f)
                    ) {
                        Text("Confirm Picked Up", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * COD Cash received confirmation dialog for "Slide to Complete Delivery"
 */
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
                            .padding(bottom = 12.dp)
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
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = onConfirmDelivery,
                        enabled = isConfirmed,
                        colors = ButtonDefaults.buttonColors(containerColor = DelivioGreen, contentColor = Color.White),
                        modifier = Modifier.weight(1.3f)
                    ) {
                        Text("Complete Delivery", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
