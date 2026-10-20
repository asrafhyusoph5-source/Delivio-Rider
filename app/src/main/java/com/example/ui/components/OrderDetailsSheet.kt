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

                // 2. Order Header (Badge, Order #, Status)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
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

                    // Report problem & Support
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showProblemDialog = true }) {
                            Icon(Icons.Default.ReportProblem, contentDescription = "Report Problem", tint = DelivioRed)
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

                // Scrollable details section based on stage & service
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (isExpanded) Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState()) else Modifier)
                ) {
                    when (order.serviceType) {
                        OrderStepConstants.SERVICE_PARCEL -> {
                            ParcelOrderContent(
                                order = order,
                                onCall = onCallClick,
                                onNavigate = { lat, lng, addr -> navigateExternal(context, lat, lng, addr) }
                            )
                        }
                        OrderStepConstants.SERVICE_PABILI -> {
                            PabiliOrderContent(
                                order = order,
                                pabiliReceiptInput = pabiliReceiptInput,
                                onReceiptChange = { pabiliReceiptInput = it },
                                onCall = onCallClick,
                                onNavigate = { lat, lng, addr -> navigateExternal(context, lat, lng, addr) }
                            )
                        }
                        OrderStepConstants.SERVICE_REQUEST -> {
                            RequestRiderOrderContent(
                                order = order,
                                onCall = onCallClick,
                                onNavigate = { lat, lng, addr -> navigateExternal(context, lat, lng, addr) }
                            )
                        }
                        else -> {
                            // FOOD & CONVENIENCE (Instant)
                            FoodOrderContent(
                                order = order,
                                onCall = onCallClick,
                                onNavigate = { lat, lng, addr -> navigateExternal(context, lat, lng, addr) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Release button (only allowed before pickup)
                val canRelease = order.riderStep == OrderStepConstants.STEP_HEADING_TO_MERCHANT ||
                        order.riderStep == OrderStepConstants.STEP_ARRIVED_AT_MERCHANT
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
                val isFinalStep = order.riderStep == OrderStepConstants.STEP_ARRIVED_AT_CUSTOMER
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
                val buttonText = getStepButtonText(order)
                SlideToConfirmButton(
                    text = buttonText,
                    enabled = if (isFinalStep && order.isCod) cashReceivedConfirmed else true,
                    onConfirmed = {
                        val receiptTotal = if (order.serviceType == OrderStepConstants.SERVICE_PABILI &&
                            order.riderStep == OrderStepConstants.STEP_ARRIVED_AT_MERCHANT) {
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

// ================= STAGE-SPECIFIC CONTENT COMPOSABLES =================

@Composable
private fun FoodOrderContent(
    order: Order,
    onCall: (String, String) -> Unit,
    onNavigate: (Double, Double, String) -> Unit
) {
    // Stage 2 (heading_to_merchant): Merchant name & address. Hide customer name & items. Merchant call icon.
    // Stage 3 (arrived_at_merchant): Merchant name & address, customer name, order number, items. Hide customer address.
    // Stage 4 (picked_up): Customer name, order number, items. Hide merchant name & address & customer address. Customer call icon.
    // Stage 5 & 6 (arrived_at_customer): Full customer details (name, phone, address, map pin), order number, items, merchant name only (hide merchant address).

    val step = order.riderStep
    val showMerchant = step == OrderStepConstants.STEP_HEADING_TO_MERCHANT ||
            step == OrderStepConstants.STEP_ARRIVED_AT_MERCHANT ||
            step == OrderStepConstants.STEP_ARRIVED_AT_CUSTOMER
    val showMerchantAddress = step == OrderStepConstants.STEP_HEADING_TO_MERCHANT ||
            step == OrderStepConstants.STEP_ARRIVED_AT_MERCHANT
    val showCustomerName = step != OrderStepConstants.STEP_HEADING_TO_MERCHANT
    val showCustomerAddress = step == OrderStepConstants.STEP_ARRIVED_AT_CUSTOMER
    val showItems = step != OrderStepConstants.STEP_HEADING_TO_MERCHANT

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showMerchant) {
            DestinationCard(
                title = "Merchant",
                name = order.merchantName,
                address = if (showMerchantAddress) order.merchantAddress else null,
                phone = if (step == OrderStepConstants.STEP_HEADING_TO_MERCHANT || step == OrderStepConstants.STEP_ARRIVED_AT_MERCHANT) order.merchantPhone else null,
                lat = order.merchantLat,
                lng = order.merchantLng,
                onCall = onCall,
                onNavigate = onNavigate
            )
        }

        if (showCustomerName) {
            DestinationCard(
                title = "Customer",
                name = order.customerName,
                address = if (showCustomerAddress) order.customerAddress else "Area: ${order.customerArea} (address reveals on arrival)",
                phone = if (showCustomerAddress || step == OrderStepConstants.STEP_PICKED_UP) order.customerPhone else null,
                lat = if (showCustomerAddress) order.customerLat else 0.0,
                lng = if (showCustomerAddress) order.customerLng else 0.0,
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
    onCall: (String, String) -> Unit,
    onNavigate: (Double, Double, String) -> Unit
) {
    // REQUEST A RIDER: Never show items!
    // Arrival at Merchant: merchant name & address, customer name.
    // Picked Up: merchant name & address, customer name, customer address.
    // Start heading to Customer: customer name only (hide merchant name & address).
    // Arrival at Customer: customer name, phone, address, and merchant name (no merchant address).
    val step = order.riderStep
    val showMerchantName = step != OrderStepConstants.STEP_PICKED_UP
    val showMerchantAddress = step == OrderStepConstants.STEP_HEADING_TO_MERCHANT || step == OrderStepConstants.STEP_ARRIVED_AT_MERCHANT
    val showCustomerAddress = step == OrderStepConstants.STEP_ARRIVED_AT_MERCHANT || step == OrderStepConstants.STEP_ARRIVED_AT_CUSTOMER

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showMerchantName) {
            DestinationCard(
                title = "Merchant (Requesting)",
                name = order.merchantName,
                address = if (showMerchantAddress) order.merchantAddress else null,
                phone = if (showMerchantAddress) order.merchantPhone else null,
                lat = order.merchantLat,
                lng = order.merchantLng,
                onCall = onCall,
                onNavigate = onNavigate
            )
        }

        DestinationCard(
            title = "Customer Destination",
            name = order.customerName,
            address = if (showCustomerAddress) order.customerAddress else "Area: ${order.customerArea} (reveals on arrival)",
            phone = if (step == OrderStepConstants.STEP_ARRIVED_AT_CUSTOMER) order.customerPhone else null,
            lat = if (showCustomerAddress) order.customerLat else 0.0,
            lng = if (showCustomerAddress) order.customerLng else 0.0,
            onCall = onCall,
            onNavigate = onNavigate
        )
    }
}

@Composable
private fun ParcelOrderContent(
    order: Order,
    onCall: (String, String) -> Unit,
    onNavigate: (Double, Double, String) -> Unit
) {
    val step = order.riderStep
    val showSpecs = step != OrderStepConstants.STEP_HEADING_TO_MERCHANT
    val showRecipientDetails = step == OrderStepConstants.STEP_PICKED_UP || step == OrderStepConstants.STEP_ARRIVED_AT_CUSTOMER
    val showRecipientAddress = step == OrderStepConstants.STEP_ARRIVED_AT_CUSTOMER

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DestinationCard(
            title = "Sender",
            name = order.merchantName,
            address = order.merchantAddress,
            phone = if (step != OrderStepConstants.STEP_ARRIVED_AT_CUSTOMER) order.merchantPhone else null,
            lat = order.merchantLat,
            lng = order.merchantLng,
            onCall = onCall,
            onNavigate = onNavigate
        )

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

        if (showRecipientDetails) {
            DestinationCard(
                title = "Recipient",
                name = order.customerName,
                address = if (showRecipientAddress) order.customerAddress else "Area: ${order.customerArea} (reveals on arrival)",
                phone = order.customerPhone,
                lat = if (showRecipientAddress) order.customerLat else 0.0,
                lng = if (showRecipientAddress) order.customerLng else 0.0,
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
    onCall: (String, String) -> Unit,
    onNavigate: (Double, Double, String) -> Unit
) {
    val step = order.riderStep
    val showVendor = step == OrderStepConstants.STEP_HEADING_TO_MERCHANT || step == OrderStepConstants.STEP_ARRIVED_AT_MERCHANT
    val showCustomerAddress = step == OrderStepConstants.STEP_ARRIVED_AT_CUSTOMER

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showVendor) {
            DestinationCard(
                title = "Vendor",
                name = order.merchantName,
                address = order.merchantAddress,
                phone = order.merchantPhone,
                lat = order.merchantLat,
                lng = order.merchantLng,
                onCall = onCall,
                onNavigate = onNavigate
            )
        }

        DestinationCard(
            title = "Customer",
            name = order.customerName,
            address = if (showCustomerAddress) order.customerAddress else "Area: ${order.customerArea} (reveals on arrival)",
            phone = order.customerPhone,
            lat = if (showCustomerAddress) order.customerLat else 0.0,
            lng = if (showCustomerAddress) order.customerLng else 0.0,
            onCall = onCall,
            onNavigate = onNavigate
        )

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
                if (step == OrderStepConstants.STEP_ARRIVED_AT_MERCHANT) {
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = pabiliReceiptInput,
                        onValueChange = onReceiptChange,
                        label = { Text("Receipt Total (Required)") },
                        prefix = { Text("₱ ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
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
    onCall: (String, String) -> Unit,
    onNavigate: (Double, Double, String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
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
                    IconButton(onClick = { onCall(name, phone) }) {
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

private fun getStepButtonText(order: Order): String {
    return when (order.serviceType) {
        OrderStepConstants.SERVICE_PARCEL -> {
            when (order.riderStep) {
                OrderStepConstants.STEP_HEADING_TO_MERCHANT -> "Slide to confirm Arrival at Sender"
                OrderStepConstants.STEP_ARRIVED_AT_MERCHANT -> "Slide to confirm Picked Up"
                OrderStepConstants.STEP_PICKED_UP -> "Slide to confirm Arrival at Recipient"
                else -> "Slide to Complete Delivery"
            }
        }
        OrderStepConstants.SERVICE_PABILI -> {
            when (order.riderStep) {
                OrderStepConstants.STEP_HEADING_TO_MERCHANT -> "Slide to confirm Arrival at Vendor"
                OrderStepConstants.STEP_ARRIVED_AT_MERCHANT -> "Slide to confirm Bought / Picked Up"
                OrderStepConstants.STEP_PICKED_UP -> "Slide to start heading to Customer"
                OrderStepConstants.STEP_HEADING_TO_CUSTOMER -> "Slide to confirm Arrival at Customer"
                else -> "Slide to Complete Delivery"
            }
        }
        else -> {
            when (order.riderStep) {
                OrderStepConstants.STEP_HEADING_TO_MERCHANT -> "Slide to confirm Arrival at Merchant"
                OrderStepConstants.STEP_ARRIVED_AT_MERCHANT -> "Slide to confirm Picked Up"
                OrderStepConstants.STEP_PICKED_UP -> "Slide to start heading to Customer"
                OrderStepConstants.STEP_HEADING_TO_CUSTOMER -> "Slide to confirm Arrival at Customer"
                else -> "Slide to Complete Delivery"
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
