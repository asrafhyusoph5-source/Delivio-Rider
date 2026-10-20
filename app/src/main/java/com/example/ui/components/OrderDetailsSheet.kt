package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.constants.OrderStepConstants
import com.example.data.models.Order
import com.example.ui.theme.*

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
    onGoOnlineClick: () -> Unit,
    onAdvanceStep: (Double?) -> Unit,
    onReleaseOrder: (String) -> Unit,
    onReportProblem: (String) -> Unit,
    onCallClick: (String, String) -> Unit,
    onOpenEarnings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }
    var showProblemDialog by remember { mutableStateOf(false) }
    var problemReason by remember { mutableStateOf("Merchant delay / waiting") }
    var showReleaseDialog by remember { mutableStateOf(false) }
    var releaseReason by remember { mutableStateOf("Emergency / Flat tire") }
    var pabiliReceiptInput by remember { mutableStateOf("") }
    var cashReceivedConfirmed by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("order_details_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(44.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant)
                    .clickable { isExpanded = !isExpanded }
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (currentOrder == null) {
                // ================= IDLE / NO ACTIVE ORDERS =================
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

            } else {
                // ================= ACTIVE ORDER =================
                val order = currentOrder
                val badge = OrderStepConstants.getServiceBadge(order.serviceType)

                // 1. Order Switcher: "Order 1 | Order 2" (if holds 2 orders)
                if (activeOrders.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
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
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Order ${idx + 1} (${OrderStepConstants.getServiceBadge(o.serviceType)})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (isSelected) TextOnYellow else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Order Header (Badge, Order #, Current Stage Title, Report problem & Support)
                val stageTitle = OrderStepConstants.getStageTitle(order.serviceType, order.riderStep)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
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
                    }

                    // Report problem & Support
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showProblemDialog = true }) {
                            Icon(Icons.Default.ReportProblem, contentDescription = "Report a problem", tint = DelivioRed)
                        }
                        if (!supportPhone.isNullOrBlank()) {
                            IconButton(onClick = {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$supportPhone"))
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                context.startActivity(intent)
                            }) {
                                Icon(Icons.Default.Headphones, contentDescription = "Contact Support", tint = DelivioYellowDark)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                val onContactSupport = {
                    val phone = supportPhone ?: "09170000000"
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    try { context.startActivity(intent) } catch (_: Exception) {}
                }

                // Scrollable details section based on stage & service (visibility table rules)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (isExpanded) Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState()) else Modifier)
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
                        OrderStepConstants.SERVICE_REQUEST -> {
                            RequestRiderOrderContent(
                                order = order,
                                onContactSupport = onContactSupport,
                                onCall = onCallClick,
                                onNavigate = { lat, lng, addr -> navigateExternal(context, lat, lng, addr) }
                            )
                        }
                        else -> {
                            // FOOD & CONVENIENCE (Instant)
                            FoodOrderContent(
                                order = order,
                                onContactSupport = onContactSupport,
                                onCall = onCallClick,
                                onNavigate = { lat, lng, addr -> navigateExternal(context, lat, lng, addr) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Release button (only allowed before pickup)
                val canRelease = OrderStepConstants.isPickupStage(order.serviceType, order.riderStep)
                if (canRelease) {
                    TextButton(
                        onClick = { showReleaseDialog = true },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Release order before pickup", color = DelivioRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // COD Cash confirmation checkbox if final step
                val stepLower = order.riderStep.lowercase().trim()
                val isFinalStep = stepLower in listOf(
                    OrderStepConstants.STEP_AT_CUSTOMER,
                    OrderStepConstants.STEP_ARRIVED_AT_CUSTOMER,
                    OrderStepConstants.STEP_AT_RECIPIENT
                )
                if (isFinalStep && order.isCod) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = cashReceivedConfirmed,
                            onCheckedChange = { cashReceivedConfirmed = it }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Cash received ($currencySymbol${String.format("%.2f", order.codCollected)})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                // Slide To Confirm Action Button
                val buttonText = OrderStepConstants.getSlideButtonText(order.serviceType, order.riderStep)
                val isPabiliReceiptStep = order.serviceType == OrderStepConstants.SERVICE_PABILI &&
                        (stepLower in listOf(OrderStepConstants.STEP_AT_VENDOR, OrderStepConstants.STEP_ARRIVED_AT_MERCHANT))
                val pabiliReceiptValid = !isPabiliReceiptStep || (pabiliReceiptInput.toDoubleOrNull() != null && (pabiliReceiptInput.toDoubleOrNull() ?: 0.0) > 0.0)
                val isSlideEnabled = if (isFinalStep && order.isCod) cashReceivedConfirmed else pabiliReceiptValid

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
            ItemsCard(items = order.items)
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
private fun ItemsCard(items: List<com.example.data.models.OrderItem>) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Order Items (${items.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            items.forEach { item ->
                Text("• ${item.quantity}x ${item.name}", fontSize = 12.sp)
            }
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
