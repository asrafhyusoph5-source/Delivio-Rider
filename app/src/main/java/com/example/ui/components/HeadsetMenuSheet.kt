package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.constants.OrderStepConstants
import com.example.data.models.Order
import com.example.ui.theme.DelivioGreen
import com.example.ui.theme.DelivioRed
import com.example.ui.theme.DelivioYellow
import com.example.ui.theme.TextOnYellow

fun findSplitEligibleOrder(orders: List<Order>, focusedOrderId: String? = null): Order? {
    val focused = focusedOrderId?.let { id -> orders.firstOrNull { it.id == id } }
    val candidates = if (focused != null) listOf(focused) + orders.filter { it.id != focused.id } else orders
    return candidates.firstOrNull { order ->
        val st = order.serviceType.lowercase().trim()
        val isFoodOrConvenience = st == OrderStepConstants.SERVICE_FOOD ||
            st == OrderStepConstants.SERVICE_MART ||
            st == "mart"
        isFoodOrConvenience && OrderStepConstants.isPickupStage(st, order.riderStep)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeadsetMenuSheet(
    activeOrders: List<Order>,
    focusedOrderId: String? = null,
    onCreateSplitTicket: (order: Order, shortReason: String, onComplete: () -> Unit, onError: (String) -> Unit) -> Unit,
    onSelectStandardIssue: (orderId: String?, reasonKey: String) -> Unit,
    onDismiss: () -> Unit
) {
    val splitEligibleOrder = remember(activeOrders, focusedOrderId) {
        findSplitEligibleOrder(activeOrders, focusedOrderId)
    }
    val defaultOrderId = remember(activeOrders, focusedOrderId) {
        focusedOrderId ?: activeOrders.firstOrNull()?.id
    }

    var showSplitReasonPrompt by remember { mutableStateOf(false) }
    val quickReasons = listOf("Too many items", "Too heavy or bulky", "Other")
    var selectedQuickReason by remember { mutableStateOf(quickReasons.first()) }
    var customReasonNote by remember { mutableStateOf("") }
    var isSubmittingSplit by remember { mutableStateOf(false) }
    var splitError by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("headset_menu_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!showSplitReasonPrompt) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.HeadsetMic,
                        contentDescription = null,
                        tint = DelivioYellow,
                        modifier = Modifier.size(26.dp)
                    )
                    Text(
                        text = "How can we help?",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 1. Request split delivery — shown ONLY for food or convenience orders before pickup
                if (splitEligibleOrder != null) {
                    HeadsetOptionRow(
                        icon = Icons.Default.AltRoute,
                        iconTint = DelivioYellow,
                        title = "Request split delivery",
                        subtitle = "Order is too big or heavy for me",
                        highlight = true,
                        testTag = "headset_option_split_delivery",
                        onClick = { showSplitReasonPrompt = true }
                    )
                }

                // 2. Problem with the booking at the merchant
                HeadsetOptionRow(
                    icon = Icons.Default.Storefront,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = "Problem with the booking at the merchant",
                    testTag = "headset_option_merchant",
                    onClick = {
                        onDismiss()
                        onSelectStandardIssue(defaultOrderId, "merchant_wait")
                    }
                )

                // 3. Customer not responding
                HeadsetOptionRow(
                    icon = Icons.Default.PhoneDisabled,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = "Customer not responding",
                    testTag = "headset_option_customer",
                    onClick = {
                        onDismiss()
                        onSelectStandardIssue(defaultOrderId, "customer_unreachable")
                    }
                )

                // 4. Wrong address or pin
                HeadsetOptionRow(
                    icon = Icons.Default.WrongLocation,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = "Wrong address or pin",
                    testTag = "headset_option_wrong_address",
                    onClick = {
                        onDismiss()
                        onSelectStandardIssue(defaultOrderId, "wrong_address")
                    }
                )

                // 5. Vehicle or safety problem
                HeadsetOptionRow(
                    icon = Icons.Default.CarCrash,
                    iconTint = DelivioRed,
                    title = "Vehicle or safety problem",
                    testTag = "headset_option_vehicle_safety",
                    onClick = {
                        onDismiss()
                        onSelectStandardIssue(defaultOrderId, "vehicle_breakdown")
                    }
                )

                // 6. Other
                HeadsetOptionRow(
                    icon = Icons.Default.HelpOutline,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    title = "Other",
                    testTag = "headset_option_other",
                    onClick = {
                        onDismiss()
                        onSelectStandardIssue(defaultOrderId, "other")
                    }
                )
            } else if (splitEligibleOrder != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = { showSplitReasonPrompt = false },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(
                            text = "Request split delivery",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "${splitEligibleOrder.displayOrderNumber} • ${splitEligibleOrder.displayPickupName}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = "Select a short reason:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    quickReasons.forEach { reason ->
                        val selected = selectedQuickReason == reason
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) DelivioYellow.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(
                                1.5.dp,
                                if (selected) DelivioYellow else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 52.dp)
                                .clickable { selectedQuickReason = reason }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = reason,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                RadioButton(
                                    selected = selected,
                                    onClick = { selectedQuickReason = reason }
                                )
                            }
                        }
                    }
                }

                if (selectedQuickReason == "Other") {
                    OutlinedTextField(
                        value = customReasonNote,
                        onValueChange = { if (it.length <= 120) customReasonNote = it },
                        label = { Text("Short reason") },
                        placeholder = { Text("Describe why order needs to be split") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("split_custom_reason_input")
                    )
                }

                splitError?.let { err ->
                    Text(
                        text = err,
                        color = DelivioRed,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Button(
                    onClick = {
                        val finalReason = if (selectedQuickReason == "Other" && customReasonNote.isNotBlank()) {
                            customReasonNote.trim()
                        } else {
                            selectedQuickReason
                        }
                        isSubmittingSplit = true
                        splitError = null
                        onCreateSplitTicket(
                            splitEligibleOrder,
                            finalReason,
                            {
                                isSubmittingSplit = false
                            },
                            { err ->
                                isSubmittingSplit = false
                                splitError = err
                            }
                        )
                    },
                    enabled = !isSubmittingSplit && (selectedQuickReason != "Other" || customReasonNote.trim().length >= 3),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DelivioYellow,
                        contentColor = TextOnYellow
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("confirm_split_request_button")
                ) {
                    if (isSubmittingSplit) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = TextOnYellow, strokeWidth = 2.dp)
                    } else {
                        Text("Submit Split Request", fontWeight = FontWeight.Black, fontSize = 15.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Composable
private fun HeadsetOptionRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String? = null,
    highlight: Boolean = false,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (highlight) DelivioYellow.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = if (highlight) BorderStroke(1.dp, DelivioYellow.copy(alpha = 0.65f)) else null,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 60.dp)
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = if (highlight) DelivioGreen else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
