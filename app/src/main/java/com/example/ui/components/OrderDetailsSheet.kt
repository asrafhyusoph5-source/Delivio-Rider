package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
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
import com.example.data.constants.OrderStepConstants
import com.example.data.models.Order
import com.example.ui.theme.*

enum class OrderSheetState {
    HIDDEN,
    COMPACT,
    EXPANDED
}

@Composable
fun OrderDetailsSheet(
    activeOrders: List<Order>,
    currentOrder: Order?,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
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
    onAdvanceStep: (Double?) -> Unit,
    onReleaseOrder: (String) -> Unit,
    onReportProblem: (String) -> Unit,
    onCallClick: (String, String) -> Unit,
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
    var pabiliReceiptInput by remember(currentOrder?.id) {
        mutableStateOf(if ((currentOrder?.finalReceiptTotal ?: 0.0) > 0.0) currentOrder?.finalReceiptTotal.toString() else "")
    }
    var cashReceivedConfirmed by remember { mutableStateOf(false) }

    LaunchedEffect(currentOrder?.id, currentOrder?.riderStep) {
        cashReceivedConfirmed = false
    }

    // Spring animation for sheet snap height
    val targetHeight = if (currentOrder == null) {
        185.dp
    } else {
        when (sheetState) {
            OrderSheetState.HIDDEN -> 74.dp
            OrderSheetState.COMPACT -> (screenHeight * 0.28f).coerceIn(215.dp, 265.dp)
            OrderSheetState.EXPANDED -> screenHeight * 0.65f
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

    // Vertical drag gesture for expanding/collapsing
    val dragModifier = Modifier.pointerInput(sheetState) {
        detectVerticalDragGestures { change, dragAmount ->
            change.consume()
            if (dragAmount < -18f) {
                // Drag up -> expand
                when (sheetState) {
                    OrderSheetState.HIDDEN -> onSheetStateChange(OrderSheetState.COMPACT)
                    OrderSheetState.COMPACT -> onSheetStateChange(OrderSheetState.EXPANDED)
                    OrderSheetState.EXPANDED -> {}
                }
            } else if (dragAmount > 18f) {
                // Drag down -> collapse
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
        if (currentOrder == null) {
            // ================= IDLE / NO ACTIVE ORDERS =================
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

                Spacer(modifier = Modifier.height(10.dp))

                if (!isOnline) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Wallet Cash on Hand", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$currencySymbol${String.format("%.2f", walletBalance)}", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Button(
                            onClick = onGoOnlineClick,
                            colors = ButtonDefaults.buttonColors(containerColor = DelivioYellow, contentColor = TextOnYellow),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Go Online", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = DelivioYellowDark,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text("Looking for nearby orders...", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("Stay within high demand areas in your city", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(10.dp))

                // Real Daily stats
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Today: ", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$currencySymbol${String.format("%.2f", todayEarnings)}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DelivioYellowDark)
                        Text(" • $todayTrips trips", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onOpenEarnings) {
                        Text("Earnings", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                    }
                }
            }

        } else {
            // ================= ACTIVE ORDER =================
            val order = currentOrder
            val badge = OrderStepConstants.getServiceBadge(order.serviceType)
            val stageTitle = OrderStepConstants.getStageTitle(order.serviceType, order.riderStep, order.currentTaxiStopNumber)
            val isCustomerRevealed = OrderStepConstants.isCustomerAddressRevealed(order.serviceType, order.riderStep)

            val allowedCallParty: Pair<String, String>? = if (order.isTaxi) {
                if (order.customerPhone.isNotBlank()) {
                    "Passenger" to order.customerPhone
                } else null
            } else if (isCustomerRevealed) {
                if (order.customerPhone.isNotBlank()) {
                    val label = if (order.serviceType.lowercase() == OrderStepConstants.SERVICE_PARCEL) "Recipient" else "Customer"
                    label to order.customerPhone
                } else null
            } else {
                if (order.merchantPhone.isNotBlank()) {
                    val label = when (order.serviceType.lowercase()) {
                        OrderStepConstants.SERVICE_PARCEL -> "Sender"
                        OrderStepConstants.SERVICE_PABILI -> "Vendor"
                        else -> "Merchant"
                    }
                    label to order.merchantPhone
                } else null
            }

            val navLat = if (order.isTaxi) {
                if (order.riderStep == OrderStepConstants.STEP_TO_PICKUP) order.merchantLat
                else order.currentTargetStop?.lat ?: order.customerLat
            } else if (isCustomerRevealed) order.customerLat else order.merchantLat

            val navLng = if (order.isTaxi) {
                if (order.riderStep == OrderStepConstants.STEP_TO_PICKUP) order.merchantLng
                else order.currentTargetStop?.lng ?: order.customerLng
            } else if (isCustomerRevealed) order.customerLng else order.merchantLng

            val navAddr = if (order.isTaxi) {
                if (order.riderStep == OrderStepConstants.STEP_TO_PICKUP) order.merchantAddress
                else order.currentTargetStop?.address ?: order.customerAddress
            } else if (isCustomerRevealed) order.customerAddress else order.merchantAddress

            val onNavigateClick = {
                navigateExternal(context, navLat, navLng, navAddr)
            }

            val buttonText = OrderStepConstants.getSlideButtonText(
                order.serviceType,
                order.riderStep,
                order.currentTaxiStopNumber,
                order.isAtLastTaxiStop
            )
            val stepLower = order.riderStep.lowercase().trim()
            val isFinalStep = if (order.isTaxi) {
                order.isAtLastTaxiStop && stepLower.startsWith(OrderStepConstants.STEP_TO_STOP_PREFIX)
            } else {
                stepLower in listOf(
                    OrderStepConstants.STEP_AT_CUSTOMER,
                    OrderStepConstants.STEP_ARRIVED_AT_CUSTOMER,
                    OrderStepConstants.STEP_AT_RECIPIENT
                )
            }
            val isParcelPickupCashStep = order.isCod && order.isParcel && order.parcelCollectAtSender &&
                    (stepLower in listOf(OrderStepConstants.STEP_AT_SENDER, OrderStepConstants.STEP_ARRIVED_AT_MERCHANT))
            val isDeliveryCashStep = order.isCod && (
                (order.isTaxi && isFinalStep) ||
                (!order.isParcel && !order.isTaxi && isFinalStep) ||
                (order.isParcel && !order.parcelCollectAtSender && isFinalStep)
            )
            val isCashCollectionStep = isParcelPickupCashStep || isDeliveryCashStep

            val isPabiliReceiptStep = order.serviceType == OrderStepConstants.SERVICE_PABILI &&
                    (stepLower in listOf(OrderStepConstants.STEP_AT_VENDOR, OrderStepConstants.STEP_ARRIVED_AT_MERCHANT))
            val pabiliReceiptValid = !isPabiliReceiptStep || (pabiliReceiptInput.toDoubleOrNull() != null && (pabiliReceiptInput.toDoubleOrNull() ?: 0.0) > 0.0)
            val isSlideEnabled = if (isCashCollectionStep) cashReceivedConfirmed else pabiliReceiptValid

            when (sheetState) {
                OrderSheetState.HIDDEN -> {
                    // SNAP STATE 1: HIDDEN (Small pill at bottom, tap or drag up to open)
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { onSheetStateChange(OrderSheetState.COMPACT) }
                            .then(dragModifier)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = DelivioYellowLight
                            ) {
                                Text(
                                    text = badge,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = TextOnYellow,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "#${order.orderNumber}",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stageTitle,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { onSheetStateChange(OrderSheetState.COMPACT) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Expand sheet",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                OrderSheetState.COMPACT -> {
                    // SNAP STATE 2: COMPACT (~28% height, handle, badge, stage title, distance/ETA, navigate, call icons, slide button, NO detail sections)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(dragModifier)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            // Drag handle: tap cycles to Expanded
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .width(44.dp)
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(MaterialTheme.colorScheme.outlineVariant)
                                    .clickable { onSheetStateChange(OrderSheetState.EXPANDED) }
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Order Switcher if multiple active orders
                            if (activeOrders.size > 1) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    activeOrders.forEachIndexed { idx, o ->
                                        val isSelected = idx == selectedIndex
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isSelected) DelivioYellow else MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { onSelectIndex(idx) }
                                        ) {
                                            Text(
                                                text = "Order ${idx + 1} (${OrderStepConstants.getServiceBadge(o.serviceType)})",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = if (isSelected) TextOnYellow else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(vertical = 4.dp, horizontal = 6.dp),
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }

                            // Header row: Badge, Order #, Stage title, Distance/ETA, Navigate & Call
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = DelivioYellowLight
                                        ) {
                                            Text(
                                                text = badge,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = TextOnYellow,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "#${order.orderNumber}",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 14.sp
                                        )
                                    }
                                    Text(
                                        text = stageTitle,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (routeDistanceKm > 0.0) {
                                        Text(
                                            text = "${String.format("%.1f", routeDistanceKm)} km • $routeEtaMinutes min${if (routeEtaMinutes != 1) "s" else ""}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    FilledTonalIconButton(
                                        onClick = onNavigateClick,
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = DelivioYellowLight,
                                            contentColor = TextOnYellow
                                        ),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Navigation,
                                            contentDescription = "Navigate",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    if (allowedCallParty != null && allowedCallParty.second.isNotBlank()) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        FilledTonalIconButton(
                                            onClick = { onCallClick(allowedCallParty.first, allowedCallParty.second) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Phone,
                                                contentDescription = "Call ${allowedCallParty.first}",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(6.dp))
                                    IconButton(
                                        onClick = { showProblemDialog = true },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ReportProblem,
                                            contentDescription = "Report a problem",
                                            tint = DelivioRed,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PaymentBadgeChip(
                                    order = order,
                                    currencySymbol = currencySymbol,
                                    isLarge = false
                                )
                                if (order.prepStatusText.isNotBlank()) {
                                    MerchantPrepStatusChip(
                                        order = order,
                                        isLarge = false
                                    )
                                }
                                if (order.isEditedByMerchant) {
                                    OrderUpdatedByMerchantBadge(
                                        isLarge = false
                                    )
                                }
                                if (order.tip > 0) {
                                    TipBadgeChip(
                                        tipAmount = order.tip,
                                        currencySymbol = currencySymbol,
                                        isLarge = false
                                    )
                                }
                                if (order.isScheduled) {
                                    ScheduledOrderChip(
                                        order = order,
                                        isLarge = false
                                    )
                                }
                            }
                        }

                        // Bottom Actions: Pabili input / COD Checkbox & Slide Button
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (isPabiliReceiptStep) {
                                OutlinedTextField(
                                    value = pabiliReceiptInput,
                                    onValueChange = { pabiliReceiptInput = it },
                                    label = { Text("Receipt Total ($currencySymbol)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 4.dp)
                                )
                            }

                            if (isCashCollectionStep) {
                                val collectParty = if (order.isParcel && order.parcelCollectAtSender) "sender" else "customer"
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = cashReceivedConfirmed,
                                        onCheckedChange = { cashReceivedConfirmed = it }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Collect $currencySymbol${String.format("%.2f", order.cashAmountToCollect)} (Cash received from $collectParty)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color(0xFFE65100)
                                    )
                                }
                            } else if (isFinalStep) {
                                if (order.isPaidOnline) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFFE8F5E9),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = Color(0xFF2E7D32),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "No cash to collect",
                                                color = Color(0xFF2E7D32),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                } else if (order.isPaymentUnknown) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFFFFEBEE),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Warning,
                                                contentDescription = null,
                                                tint = Color(0xFFC62828),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Payment unknown - contact support",
                                                color = Color(0xFFC62828),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            SlideToConfirmButton(
                                text = buttonText,
                                enabled = isSlideEnabled,
                                onConfirmed = {
                                    val receiptTotal = if (isPabiliReceiptStep) {
                                        pabiliReceiptInput.toDoubleOrNull()
                                    } else null
                                    onAdvanceStep(receiptTotal)
                                }
                            )
                        }
                    }
                }

                OrderSheetState.EXPANDED -> {
                    // SNAP STATE 3: EXPANDED (At most 65% height, scrollable details, slide button pinned)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        // Drag Handle: tap cycles to Compact
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .width(44.dp)
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.outlineVariant)
                                .clickable { onSheetStateChange(OrderSheetState.COMPACT) }
                                .then(dragModifier)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Switcher if multiple
                        if (activeOrders.size > 1) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                activeOrders.forEachIndexed { idx, o ->
                                    val isSelected = idx == selectedIndex
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) DelivioYellow else MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { onSelectIndex(idx) }
                                    ) {
                                        Text(
                                            text = "Order ${idx + 1} (${OrderStepConstants.getServiceBadge(o.serviceType)})",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (isSelected) TextOnYellow else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(vertical = 6.dp, horizontal = 10.dp),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        // Header row with Badge, Order #, Stage, Distance & ETA, Navigate, Call, Report, Support
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(dragModifier),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = DelivioYellowLight
                                    ) {
                                        Text(
                                            text = badge,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = TextOnYellow,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "#${order.orderNumber}",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 16.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stageTitle,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (routeDistanceKm > 0.0) {
                                    Text(
                                        text = "${String.format("%.1f", routeDistanceKm)} km • $routeEtaMinutes min${if (routeEtaMinutes != 1) "s" else ""}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FilledTonalIconButton(
                                    onClick = onNavigateClick,
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = DelivioYellowLight,
                                        contentColor = TextOnYellow
                                    ),
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Navigation,
                                        contentDescription = "Navigate",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                if (allowedCallParty != null && allowedCallParty.second.isNotBlank()) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    FilledTonalIconButton(
                                        onClick = { onCallClick(allowedCallParty.first, allowedCallParty.second) },
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Phone,
                                            contentDescription = "Call ${allowedCallParty.first}",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                IconButton(
                                    onClick = { showProblemDialog = true },
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(Icons.Default.ReportProblem, contentDescription = "Report a problem", tint = DelivioRed)
                                }
                                if (!supportPhone.isNullOrBlank()) {
                                    IconButton(
                                        onClick = {
                                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$supportPhone"))
                                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            try { context.startActivity(intent) } catch (_: Exception) {}
                                        },
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Icon(Icons.Default.Headphones, contentDescription = "Contact Support", tint = DelivioYellowDark)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        PaymentBadgeChip(
                            order = order,
                            currencySymbol = currencySymbol,
                            isLarge = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (order.prepStatusText.isNotBlank() || order.isEditedByMerchant) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (order.prepStatusText.isNotBlank()) {
                                    MerchantPrepStatusChip(
                                        order = order,
                                        isLarge = true
                                    )
                                }
                                if (order.isEditedByMerchant) {
                                    OrderUpdatedByMerchantBadge(
                                        isLarge = true
                                    )
                                }
                            }
                        }
                        if (order.tip > 0 || order.isScheduled) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (order.tip > 0) {
                                    TipBadgeChip(
                                        tipAmount = order.tip,
                                        currencySymbol = currencySymbol,
                                        isLarge = true
                                    )
                                }
                                if (order.isScheduled) {
                                    ScheduledOrderChip(
                                        order = order,
                                        isLarge = true
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        val onContactSupport = {
                            val phone = supportPhone ?: "09170000000"
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            try { context.startActivity(intent) } catch (_: Exception) {}
                        }

                        // Scrollable details section
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            when (order.serviceType) {
                                OrderStepConstants.SERVICE_PARCEL -> {
                                    ParcelOrderContent(
                                        order = order,
                                        onContactSupport = onContactSupport,
                                        onCall = onCallClick,
                                        onNavigate = { lat, lng, addr -> navigateExternal(context, lat, lng, addr) }
                                    )
                                }
                                OrderStepConstants.SERVICE_PABILI -> {
                                    PabiliOrderContent(
                                        order = order,
                                        pabiliReceiptInput = pabiliReceiptInput,
                                        onReceiptChange = { pabiliReceiptInput = it },
                                        onContactSupport = onContactSupport,
                                        onCall = onCallClick,
                                        onNavigate = { lat, lng, addr -> navigateExternal(context, lat, lng, addr) }
                                    )
                                }
                                OrderStepConstants.SERVICE_TAXI -> {
                                    TaxiOrderContent(
                                        order = order,
                                        currencySymbol = currencySymbol,
                                        onContactSupport = onContactSupport,
                                        onCall = onCallClick,
                                        onNavigate = { lat, lng, addr -> navigateExternal(context, lat, lng, addr) },
                                        onReportProblem = { showProblemDialog = true }
                                    )
                                }
                                OrderStepConstants.SERVICE_REQUEST -> {
                                    RequestRiderOrderContent(
                                        order = order,
                                        onContactSupport = onContactSupport,
                                        onCall = onCallClick,
                                        onNavigate = { lat, lng, addr -> navigateExternal(context, lat, lng, addr) }
                                    )
                                }
                                else -> {
                                    FoodOrderContent(
                                        order = order,
                                        currencySymbol = currencySymbol,
                                        onContactSupport = onContactSupport,
                                        onCall = onCallClick,
                                        onNavigate = { lat, lng, addr -> navigateExternal(context, lat, lng, addr) }
                                    )
                                }
                            }

                            // Release button (only allowed before pickup / trip start)
                            val canRelease = OrderStepConstants.isPickupStage(order.serviceType, order.riderStep)
                            if (canRelease) {
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(
                                    onClick = { showReleaseDialog = true },
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                ) {
                                    Text(if (order.isTaxi) "Release trip" else "Release order before pickup", color = DelivioRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Bottom pinned slide action in Expanded mode
                        if (isCashCollectionStep) {
                            val collectParty = if (order.isParcel && order.parcelCollectAtSender) "sender" else "customer"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = cashReceivedConfirmed,
                                    onCheckedChange = { cashReceivedConfirmed = it }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Collect $currencySymbol${String.format("%.2f", order.cashAmountToCollect)} (Cash received from $collectParty)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFFE65100)
                                )
                            }
                        } else if (isFinalStep) {
                            if (order.isPaidOnline) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFE8F5E9),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF2E7D32),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "No cash to collect",
                                            color = Color(0xFF2E7D32),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            } else if (order.isPaymentUnknown) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFFFEBEE),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = Color(0xFFC62828),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Payment unknown - contact support",
                                            color = Color(0xFFC62828),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        SlideToConfirmButton(
                            text = buttonText,
                            enabled = isSlideEnabled,
                            onConfirmed = {
                                val receiptTotal = if (isPabiliReceiptStep) {
                                    pabiliReceiptInput.toDoubleOrNull()
                                } else null
                                onAdvanceStep(receiptTotal)
                            }
                        )
                    }
                }
            }
        }
    }

    // Problem Dialog
    if (showProblemDialog) {
        val problems = listOf(
            "Merchant delay / long waiting",
            "Customer unreachable / no answer",
            "Wrong address provided",
            "Vehicle breakdown / flat tire",
            "Severe weather / flooding",
            "Other incident"
        )
        AlertDialog(
            onDismissRequest = { showProblemDialog = false },
            title = { Text("Report a Problem", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    problems.forEach { prob ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (problemReason == prob) DelivioYellowLight else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { problemReason = prob }
                        ) {
                            Text(
                                text = prob,
                                fontSize = 13.sp,
                                fontWeight = if (problemReason == prob) FontWeight.Bold else FontWeight.Normal,
                                color = if (problemReason == prob) TextOnYellow else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onReportProblem(problemReason)
                        showProblemDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DelivioRed, contentColor = Color.White)
                ) {
                    Text("Submit Report", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showProblemDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Release Dialog
    if (showReleaseDialog) {
        val releaseReasons = listOf(
            "Vehicle emergency / mechanical issue",
            "Merchant order cancelled / unavailable",
            "Distance too far",
            "Personal emergency"
        )
        AlertDialog(
            onDismissRequest = { showReleaseDialog = false },
            title = { Text("Release Order", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Select reason for releasing this order back to dispatch pool:", fontSize = 13.sp)
                    releaseReasons.forEach { r ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (releaseReason == r) DelivioYellowLight else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { releaseReason = r }
                        ) {
                            Text(
                                text = r,
                                fontSize = 13.sp,
                                fontWeight = if (releaseReason == r) FontWeight.Bold else FontWeight.Normal,
                                color = if (releaseReason == r) TextOnYellow else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onReleaseOrder(releaseReason)
                        showReleaseDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DelivioRed, contentColor = Color.White)
                ) {
                    Text("Confirm Release", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showReleaseDialog = false }) { Text("Cancel") }
            }
        )
    }
}

// ================= STAGE-SPECIFIC CONTENT COMPOSABLES (Visibility Table) =================

@Composable
private fun FoodOrderContent(
    order: Order,
    currencySymbol: String = "₱",
    onContactSupport: () -> Unit,
    onCall: (String, String) -> Unit,
    onNavigate: (Double, Double, String) -> Unit
) {
    // Visibility rules for Food & Convenience:
    // 1. to_merchant: Merchant details (name, address, call, navigate). Customer completely hidden. Items hidden.
    // 2. at_merchant: Merchant details. Customer name only (address hidden). Items shown.
    // 3. picked_up: Merchant name only (hide merchant address). Customer name & call. Customer address reveals next stage. Items shown.
    // 4. to_customer: Customer address revealed! Full customer card (name, phone, address, call, navigate). Merchant name only. Items shown.
    // 5. at_customer: Full customer card. Merchant name only. Items shown.
    val step = order.riderStep.lowercase().trim()
    val isToMerchant = step in listOf(OrderStepConstants.STEP_TO_MERCHANT, OrderStepConstants.STEP_HEADING_TO_MERCHANT)
    val isAtMerchant = step in listOf(OrderStepConstants.STEP_AT_MERCHANT, OrderStepConstants.STEP_ARRIVED_AT_MERCHANT)
    val isPickedUp = step == OrderStepConstants.STEP_PICKED_UP
    val isCustomerRevealed = OrderStepConstants.isCustomerAddressRevealed(order.serviceType, step)

    val showMerchant = true
    val showMerchantAddress = isToMerchant || isAtMerchant
    val showMerchantPhone = isToMerchant || isAtMerchant
    val showCustomer = !isToMerchant
    val showCustomerAddress = isCustomerRevealed
    val showCustomerPhone = isPickedUp || isCustomerRevealed
    val showItems = !isToMerchant

    val isCustomerLocationMissing = isCustomerRevealed && (order.customerLat == 0.0 || order.customerLng == 0.0)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (order.isEditedByMerchant) {
            OrderUpdatedByMerchantBadge(
                isLarge = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (showMerchant) {
            DestinationCard(
                title = "Merchant",
                name = order.merchantName,
                address = if (showMerchantAddress) order.merchantAddress else null,
                phone = if (showMerchantPhone) order.merchantPhone else null,
                lat = if (showMerchantAddress) order.merchantLat else 0.0,
                lng = if (showMerchantAddress) order.merchantLng else 0.0,
                onCall = onCall,
                onNavigate = onNavigate
            )
        }

        if (showCustomer) {
            DestinationCard(
                title = "Customer",
                name = order.customerName,
                address = if (showCustomerAddress) order.customerAddress else "Area: ${order.customerArea} (address reveals on way to customer)",
                phone = if (showCustomerPhone) order.customerPhone else null,
                lat = if (showCustomerAddress) order.customerLat else 0.0,
                lng = if (showCustomerAddress) order.customerLng else 0.0,
                isLocationMissing = isCustomerLocationMissing,
                onContactSupport = onContactSupport,
                onCall = onCall,
                onNavigate = onNavigate
            )
        }

        if (showItems && order.items.isNotEmpty()) {
            ItemsCard(order = order, currencySymbol = currencySymbol)
        }
    }
}

@Composable
private fun RequestRiderOrderContent(
    order: Order,
    onContactSupport: () -> Unit,
    onCall: (String, String) -> Unit,
    onNavigate: (Double, Double, String) -> Unit
) {
    // REQUEST A RIDER: Never show items!
    // 1. to_merchant: Merchant details (name, address, call, navigate). Customer completely hidden.
    // 2. at_merchant: Merchant details. Customer name only.
    // 3. picked_up: Merchant name only. Customer name & call.
    // 4. to_customer & at_customer: Customer address revealed! Merchant name only.
    val step = order.riderStep.lowercase().trim()
    val isToMerchant = step in listOf(OrderStepConstants.STEP_TO_MERCHANT, OrderStepConstants.STEP_HEADING_TO_MERCHANT)
    val isAtMerchant = step in listOf(OrderStepConstants.STEP_AT_MERCHANT, OrderStepConstants.STEP_ARRIVED_AT_MERCHANT)
    val isPickedUp = step == OrderStepConstants.STEP_PICKED_UP
    val isCustomerRevealed = OrderStepConstants.isCustomerAddressRevealed(order.serviceType, step)

    val showMerchant = true
    val showMerchantAddress = isToMerchant || isAtMerchant
    val showMerchantPhone = isToMerchant || isAtMerchant
    val showCustomer = !isToMerchant
    val showCustomerAddress = isCustomerRevealed
    val showCustomerPhone = isPickedUp || isCustomerRevealed

    val isCustomerLocationMissing = isCustomerRevealed && (order.customerLat == 0.0 || order.customerLng == 0.0)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showMerchant) {
            DestinationCard(
                title = "Merchant (Requesting)",
                name = order.merchantName,
                address = if (showMerchantAddress) order.merchantAddress else null,
                phone = if (showMerchantPhone) order.merchantPhone else null,
                lat = if (showMerchantAddress) order.merchantLat else 0.0,
                lng = if (showMerchantAddress) order.merchantLng else 0.0,
                onCall = onCall,
                onNavigate = onNavigate
            )
        }

        if (showCustomer) {
            DestinationCard(
                title = "Customer Destination",
                name = order.customerName,
                address = if (showCustomerAddress) order.customerAddress else "Area: ${order.customerArea} (reveals on way to customer)",
                phone = if (showCustomerPhone) order.customerPhone else null,
                lat = if (showCustomerAddress) order.customerLat else 0.0,
                lng = if (showCustomerAddress) order.customerLng else 0.0,
                isLocationMissing = isCustomerLocationMissing,
                onContactSupport = onContactSupport,
                onCall = onCall,
                onNavigate = onNavigate
            )
        }

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Delivery Fee & Payment", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Delivery Fee", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Paid by merchant", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DelivioGreenDark)
                }
                if (order.isCod) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("COD Amount to Collect", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("₱${String.format("%.2f", order.codAmount)} (from recipient)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                    }
                }
            }
        }
    }
}

@Composable
private fun ParcelOrderContent(
    order: Order,
    onContactSupport: () -> Unit,
    onCall: (String, String) -> Unit,
    onNavigate: (Double, Double, String) -> Unit
) {
    // PARCEL:
    // 1. to_sender: Sender details (name, address, phone, navigate). Recipient hidden. Specs hidden.
    // 2. at_sender: Sender details. Recipient name only (address hidden). Specs shown.
    // 3. to_recipient & at_recipient: Recipient address revealed! Full recipient card. Sender name only. Specs shown.
    val step = order.riderStep.lowercase().trim()
    val isToSender = step in listOf(OrderStepConstants.STEP_TO_SENDER, OrderStepConstants.STEP_HEADING_TO_MERCHANT)
    val isAtSender = step in listOf(OrderStepConstants.STEP_AT_SENDER, OrderStepConstants.STEP_ARRIVED_AT_MERCHANT)
    val isRecipientRevealed = OrderStepConstants.isCustomerAddressRevealed(order.serviceType, step)

    val showSender = true
    val showSenderAddress = isToSender || isAtSender
    val showSenderPhone = isToSender || isAtSender
    val showSpecs = !isToSender
    val showRecipient = !isToSender
    val showRecipientAddress = isRecipientRevealed
    val showRecipientPhone = isRecipientRevealed

    val isRecipientLocationMissing = isRecipientRevealed && (order.customerLat == 0.0 || order.customerLng == 0.0)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showSender) {
            DestinationCard(
                title = "Sender",
                name = order.merchantName,
                address = if (showSenderAddress) order.merchantAddress else null,
                phone = if (showSenderPhone) order.merchantPhone else null,
                lat = if (showSenderAddress) order.merchantLat else 0.0,
                lng = if (showSenderAddress) order.merchantLng else 0.0,
                onCall = onCall,
                onNavigate = onNavigate
            )
        }

        if (showSpecs) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Package Specifications", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Size: ${order.parcelSize} • Weight: ${order.parcelWeightKg} kg", fontSize = 12.sp)
                    if (order.parcelItemDescription.isNotBlank()) {
                        Text("Item: ${order.parcelItemDescription}", fontSize = 12.sp)
                    }
                    if (order.parcelFragile) {
                        Surface(shape = RoundedCornerShape(6.dp), color = DelivioRed.copy(alpha = 0.15f)) {
                            Text("⚠ Fragile Item", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DelivioRed, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                    Text("Fee Payer: ${order.parcelFeePayer}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (showRecipient) {
            DestinationCard(
                title = "Recipient",
                name = order.customerName,
                address = if (showRecipientAddress) order.customerAddress else "Area: ${order.customerArea} (reveals on way to recipient)",
                phone = if (showRecipientPhone) order.customerPhone else null,
                lat = if (showRecipientAddress) order.customerLat else 0.0,
                lng = if (showRecipientAddress) order.customerLng else 0.0,
                isLocationMissing = isRecipientLocationMissing,
                onContactSupport = onContactSupport,
                onCall = onCall,
                onNavigate = onNavigate
            )
        }
    }
}

@Composable
private fun PabiliOrderContent(
    order: Order,
    pabiliReceiptInput: String,
    onReceiptChange: (String) -> Unit,
    onContactSupport: () -> Unit,
    onCall: (String, String) -> Unit,
    onNavigate: (Double, Double, String) -> Unit
) {
    // PABILI:
    // 1. to_vendor: Vendor details (name, address, phone, navigate). Shopping list shown. Customer name only (address hidden).
    // 2. at_vendor: Vendor details. Shopping list + OutlinedTextField for Receipt Total. Customer name only.
    // 3. picked_up: Vendor name only. Customer name, call. Shopping list with receipt total.
    // 4. to_customer & at_customer: Customer address revealed! Full customer card. Vendor name only. Shopping list with receipt total.
    val step = order.riderStep.lowercase().trim()
    val isToVendor = step in listOf(OrderStepConstants.STEP_TO_VENDOR, OrderStepConstants.STEP_HEADING_TO_MERCHANT)
    val isAtVendor = step in listOf(OrderStepConstants.STEP_AT_VENDOR, OrderStepConstants.STEP_ARRIVED_AT_MERCHANT)
    val isPickedUp = step == OrderStepConstants.STEP_PICKED_UP
    val isCustomerRevealed = OrderStepConstants.isCustomerAddressRevealed(order.serviceType, step)

    val showVendor = true
    val showVendorAddress = isToVendor || isAtVendor
    val showVendorPhone = isToVendor || isAtVendor
    val showCustomer = true
    val showCustomerAddress = isCustomerRevealed
    val showCustomerPhone = isPickedUp || isCustomerRevealed

    val isCustomerLocationMissing = isCustomerRevealed && (order.customerLat == 0.0 || order.customerLng == 0.0)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showVendor) {
            DestinationCard(
                title = "Vendor",
                name = order.merchantName,
                address = if (showVendorAddress) order.merchantAddress else null,
                phone = if (showVendorPhone) order.merchantPhone else null,
                lat = if (showVendorAddress) order.merchantLat else 0.0,
                lng = if (showVendorAddress) order.merchantLng else 0.0,
                onCall = onCall,
                onNavigate = onNavigate
            )
        }

        if (showCustomer) {
            DestinationCard(
                title = "Customer",
                name = order.customerName,
                address = if (showCustomerAddress) order.customerAddress else "Area: ${order.customerArea} (reveals on way to customer)",
                phone = if (showCustomerPhone) order.customerPhone else null,
                lat = if (showCustomerAddress) order.customerLat else 0.0,
                lng = if (showCustomerAddress) order.customerLng else 0.0,
                isLocationMissing = isCustomerLocationMissing,
                onContactSupport = onContactSupport,
                onCall = onCall,
                onNavigate = onNavigate
            )
        }

        // Pabili Item list & totals
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Pabili Shopping List", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                order.pabiliItems.forEach { item ->
                    Text("• ${item.quantity}x ${item.product} (est. ₱${String.format("%.2f", item.estimatedPrice)})", fontSize = 12.sp)
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Estimated: ₱${String.format("%.2f", order.pabiliEstimatedTotal)}", fontSize = 12.sp)
                    Text("Budget: ₱${String.format("%.2f", order.pabiliBudget)}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // If at vendor bought step: require receipt total
                if (isAtVendor) {
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = pabiliReceiptInput,
                        onValueChange = onReceiptChange,
                        label = { Text("Receipt Total (Required to Pick Up)") },
                        prefix = { Text("₱ ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (order.finalReceiptTotal > 0.0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Actual Receipt: ₱${String.format("%.2f", order.finalReceiptTotal)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = DelivioGreenDark
                    )
                }
            }
        }
    }
}

// Helpers
@Composable
private fun DestinationCard(
    title: String,
    name: String,
    address: String?,
    phone: String?,
    lat: Double,
    lng: Double,
    isLocationMissing: Boolean = false,
    onContactSupport: (() -> Unit)? = null,
    onCall: (String, String) -> Unit,
    onNavigate: (Double, Double, String) -> Unit
) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    if (!address.isNullOrBlank()) {
                        Text(address, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!phone.isNullOrBlank()) {
                        IconButton(onClick = {
                            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                            dialIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            try {
                                context.startActivity(dialIntent)
                            } catch (_: Exception) {
                                onCall(name, phone)
                            }
                        }) {
                            Icon(Icons.Default.Phone, contentDescription = "Call $title", tint = DelivioGreenDark)
                        }
                    }
                    if (lat != 0.0 && lng != 0.0) {
                        IconButton(onClick = { onNavigate(lat, lng, address ?: name) }) {
                            Icon(Icons.Default.Navigation, contentDescription = "Navigate to $title", tint = DelivioYellowDark)
                        }
                    }
                }
            }

            // Location missing warning with Contact Support button (does not block flow)
            if (isLocationMissing) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = DelivioRed.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = DelivioRed, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Customer location missing", color = DelivioRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        if (onContactSupport != null) {
                            TextButton(
                                onClick = onContactSupport,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Contact Support", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DelivioYellowDark)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemsCard(
    order: Order,
    currencySymbol: String = "₱"
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("order_items_card")
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Order Items (${order.items.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                if (order.isEditedByMerchant) {
                    OrderUpdatedByMerchantBadge(isLarge = false)
                }
            }

            order.items.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("• ${item.quantity}x ${item.name}", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        if (item.specialInstructions.isNotBlank()) {
                            Text("Note: ${item.specialInstructions}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (item.price > 0.0) {
                        Text(
                            text = "$currencySymbol${String.format("%.2f", item.price * item.quantity)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Totals breakdown (updates live from order snapshot)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Order Total", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$currencySymbol${String.format("%.2f", order.feesTotal)}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            if (order.feesDeliveryFee > 0.0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Delivery Fee", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$currencySymbol${String.format("%.2f", order.feesDeliveryFee)}", fontSize = 12.sp)
                }
            }

            if (order.tip > 0.0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Tip", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$currencySymbol${String.format("%.2f", order.tip)}", fontSize = 12.sp, color = DelivioGreenDark, fontWeight = FontWeight.Bold)
                }
            }

            if (order.isCod) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Cash to Collect (COD)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                    Text("$currencySymbol${String.format("%.2f", order.cashAmountToCollect)}", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFE65100))
                }
            } else if (order.isPaidOnline) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Payment", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Paid Online (No Cash)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DelivioGreenDark)
                }
            }
        }
    }
}

@Composable
private fun TaxiOrderContent(
    order: Order,
    currencySymbol: String,
    onContactSupport: () -> Unit,
    onCall: (String, String) -> Unit,
    onNavigate: (Double, Double, String) -> Unit,
    onReportProblem: () -> Unit
) {
    val step = order.riderStep.lowercase().trim()
    val isToPickup = step == OrderStepConstants.STEP_TO_PICKUP
    val isAtPickup = step == OrderStepConstants.STEP_AT_PICKUP

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // 1. Passenger Details Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(DelivioYellow),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, contentDescription = "Passenger", tint = TextOnYellow)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("PASSENGER", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(order.customerName.ifBlank { "Passenger" }, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                            if (order.customerPhone.isNotBlank()) {
                                Text(order.customerPhone, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    if (order.customerPhone.isNotBlank()) {
                        IconButton(
                            onClick = { onCall("Passenger", order.customerPhone) },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(DelivioGreenDark)
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = "Call Passenger", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                if (order.customerNotes.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Note: ${order.customerNotes}",
                            fontSize = 11.sp,
                            modifier = Modifier.padding(8.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 2. Stops / Trip Route Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Trip Route & Stops (${order.totalStopsCount} Stops)", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                // Stop 1: Pickup
                val isPickupActive = isToPickup || isAtPickup
                val isPickupDone = !isPickupActive
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (isPickupDone) Color(0xFF2E7D32) else DelivioYellowDark),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isPickupDone) {
                            Icon(Icons.Default.Check, contentDescription = "Completed", tint = Color.White, modifier = Modifier.size(16.dp))
                        } else {
                            Text("1", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Stop 1: Pickup Point", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        val pickupAddr = if (order.merchantAddress.isNotBlank()) order.merchantAddress else order.merchantName
                        Text(pickupAddr, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (isToPickup) {
                        IconButton(onClick = { onNavigate(order.merchantLat, order.merchantLng, order.merchantAddress) }) {
                            Icon(Icons.Default.Navigation, contentDescription = "Navigate to Pickup", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                HorizontalDivider()

                // Stops 2, 3, 4...
                order.allTaxiStops.forEach { stop ->
                    val isDone = order.completedStops.contains(stop.stopNumber)
                    val isCurrent = order.currentTaxiStopNumber == stop.stopNumber && !isPickupActive && !isDone
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(if (isDone) Color(0xFF2E7D32) else if (isCurrent) DelivioRed else Color.Gray),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isDone) {
                                Icon(Icons.Default.Check, contentDescription = "Done", tint = Color.White, modifier = Modifier.size(16.dp))
                            } else {
                                Text("${stop.stopNumber}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            val stopTitle = if (stop.stopNumber == 2 && order.allTaxiStops.size == 1) "Stop 2: Drop-off (${stop.area})"
                            else "Stop ${stop.stopNumber}: ${stop.area}"
                            Text(stopTitle, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(stop.address.ifBlank { "Destination stop" }, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (isCurrent) {
                            IconButton(onClick = { onNavigate(stop.lat, stop.lng, stop.address) }) {
                                Icon(Icons.Default.Navigation, contentDescription = "Navigate to Stop", tint = DelivioRed)
                            }
                        }
                    }
                }
            }
        }

        // 3. Fare and Payment Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Fare & Earnings", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Trip Fare", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$currencySymbol${String.format("%.2f", order.feesTotal)}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Payment Method", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val pText = if (order.isCod) "Cash (COD): Collect $currencySymbol${String.format("%.2f", order.cashAmountToCollect)}" else "Delivio Wallet / Paid"
                    Text(pText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (order.isCod) Color(0xFFE65100) else Color(0xFF2E7D32))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Estimated Driver Net", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("$currencySymbol${String.format("%.2f", order.estimatedEarnings)}", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // 4. Report Problem button
        OutlinedButton(
            onClick = onReportProblem,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.ReportProblem, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Report a problem", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

private fun navigateExternal(context: android.content.Context, lat: Double, lng: Double, address: String) {
    val geoUri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(address)})")
    val intent = Intent(Intent.ACTION_VIEW, geoUri).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        // Fallback open OSM in browser
        val browserUri = Uri.parse("https://www.openstreetmap.org/?mlat=$lat&mlon=$lng#map=17/$lat/$lng")
        val browserIntent = Intent(Intent.ACTION_VIEW, browserUri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try { context.startActivity(browserIntent) } catch (_: Exception) {}
    }
}
