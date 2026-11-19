package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.models.*
import com.example.ui.theme.*
import java.util.Locale

private fun formatParcelAmount(amount: Double): String {
    return if (amount % 1.0 == 0.0) {
        String.format(Locale.US, "%.0f", amount)
    } else {
        String.format(Locale.US, "%.2f", amount)
    }
}

private fun formatParcelWeightKg(weightKg: Double): String {
    if (weightKg <= 0.0) return "Not specified"
    return if (weightKg % 1.0 == 0.0) {
        "${weightKg.toInt()} kg"
    } else {
        "${String.format(Locale.US, "%.1f", weightKg)} kg"
    }
}

@Composable
fun ParcelSingleStopDetailsCard(
    stop: RouteStop
) {
    val stage = stop.order.parcelStepStage
    val isPickupStep = stage == ParcelStepStage.TO_SENDER || stage == ParcelStepStage.AT_SENDER
    val badgeLabel = if (isPickupStep) "PICKUP" else "DROP-OFF"
    val badgeColor = if (isPickupStep) DelivioGreenDark else DelivioRed
    val personName = if (isPickupStep) {
        resolveStopDisplayName(stop.order, StopType.MERCHANT)
    } else {
        resolveStopDisplayName(stop.order, StopType.CUSTOMER)
    }
    val addressText = if (isPickupStep) {
        deduplicateAddress(stop.order.merchantAddress)
            .ifBlank { deduplicateAddress(stop.fullAddress) }
            .ifBlank { deduplicateAddress(stop.address) }
            .ifBlank { "Address unavailable" }
    } else {
        deduplicateAddress(stop.order.customerAddress)
            .ifBlank { deduplicateAddress(stop.order.customerArea) }
            .ifBlank { deduplicateAddress(stop.fullAddress) }
            .ifBlank { "Address unavailable" }
    }
    val noteText = if (isPickupStep) stop.order.resolvedPickupNote else stop.order.resolvedDropoffNote

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(
            width = 1.dp,
            color = if (isPickupStep) DelivioGreen.copy(alpha = 0.45f) else DelivioRed.copy(alpha = 0.45f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("parcel_stop_details_card")
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
                    modifier = Modifier.testTag("parcel_stop_address")
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
fun ParcelCompactInfoCard(
    order: Order
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("parcel_info_card")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Inventory2,
                        contentDescription = null,
                        tint = DelivioYellowDark,
                        modifier = Modifier.size(17.dp)
                    )
                    Text(
                        text = "Parcel Details",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (order.parcelFragile) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = DelivioRed.copy(alpha = 0.14f),
                        border = BorderStroke(0.5.dp, DelivioRed.copy(alpha = 0.4f)),
                        modifier = Modifier.testTag("parcel_fragile_badge")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Fragile",
                                tint = DelivioRed,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Fragile",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = DelivioRed
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                        Text(
                            text = "Size",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = order.parcelSize.trim().ifBlank { "Standard" },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                        Text(
                            text = "Weight",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatParcelWeightKg(order.parcelWeightKg),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (order.parcelItemDescription.isNotBlank()) {
                Text(
                    text = order.parcelItemDescription.trim(),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun ParcelCashDialog(
    order: Order,
    stage: ParcelStepStage,
    currencySymbol: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val isPickupStage = stage == ParcelStepStage.TO_SENDER || stage == ParcelStepStage.AT_SENDER
    val collectsCash = (isPickupStage && order.parcelCollectsAtPickup) ||
        (!isPickupStage && order.parcelCollectsAtDropoff)
    val cashAmount = order.cashAmountToCollect
    val formattedCash = String.format(Locale.US, "%.2f", cashAmount)
    var isConfirmed by remember(order.id, stage) { mutableStateOf(false) }

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
                .testTag("parcel_cash_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                if (collectsCash) {
                    val payerName = if (isPickupStage) {
                        resolveStopDisplayName(order, StopType.MERCHANT)
                    } else {
                        resolveStopDisplayName(order, StopType.CUSTOMER)
                    }
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
                        text = "Collect cash from $payerName (${order.displayOrderNumber})",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFFF3E0),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "CASH TO COLLECT",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE65100)
                            )
                            Text(
                                text = "$currencySymbol$formattedCash",
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
                            text = "I have received the exact cash amount ($currencySymbol$formattedCash)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = DelivioGreenDark)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "No Cash Collected",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = DelivioGreen.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isPickupStage) {
                                "No cash to collect from the sender"
                            } else {
                                "No cash to collect from the recipient"
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DelivioGreenDark,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                val confirmLabel = when {
                    isPickupStage -> "Confirm Picked Up"
                    collectsCash -> "Complete Delivery"
                    else -> "Confirm Delivery"
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
                        onClick = onConfirm,
                        enabled = !collectsCash || isConfirmed,
                        colors = ButtonDefaults.buttonColors(containerColor = DelivioGreen, contentColor = Color.White),
                        modifier = Modifier
                            .weight(1.3f)
                            .heightIn(min = 48.dp)
                            .testTag("parcel_cash_confirm_button")
                    ) {
                        Text(confirmLabel, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ParcelTripDetailsDialog(
    order: Order,
    currencySymbol: String,
    onCallClick: (String, String) -> Unit,
    onNavigateToLocation: (String, Double, Double) -> Unit,
    onDismiss: () -> Unit
) {
    val senderName = resolveStopDisplayName(order, StopType.MERCHANT)
    val senderAddress = deduplicateAddress(order.merchantAddress).ifBlank { "Address unavailable" }
    val recipientName = resolveStopDisplayName(order, StopType.CUSTOMER)
    val recipientAddress = deduplicateAddress(order.customerAddress)
        .ifBlank { deduplicateAddress(order.customerArea) }
        .ifBlank { "Address unavailable" }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxSize()
                .testTag("parcel_trip_details_page")
        ) {
            BackHandler(onBack = onDismiss)

            Column(modifier = Modifier.fillMaxSize()) {
                // Top App Bar
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("parcel_trip_details_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                            Text(
                                text = "Trip details",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = order.displayOrderNumber,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .testTag("trip_details_order_number")
                            )
                        }
                    }
                }

                // 4 Cards: PICKUP, DROP-OFF, PARCEL, PAYMENT
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. PICKUP Card
                    TripDetailsEndpointCard(
                        badgeText = "PICKUP",
                        badgeColor = DelivioGreenDark,
                        name = senderName,
                        address = senderAddress,
                        phone = order.merchantPhone,
                        note = order.resolvedPickupNote,
                        cardTag = "trip_details_pickup_card",
                        callTag = "trip_details_pickup_call_button",
                        navigateTag = "trip_details_pickup_navigate_button",
                        onCall = { onCallClick(senderName, order.merchantPhone) },
                        onNavigate = {
                            onNavigateToLocation(
                                senderName,
                                if (order.merchantLat != 0.0) order.merchantLat else 9.7392,
                                if (order.merchantLng != 0.0) order.merchantLng else 118.7353
                            )
                        }
                    )

                    // 2. DROP-OFF Card
                    TripDetailsEndpointCard(
                        badgeText = "DROP-OFF",
                        badgeColor = DelivioRed,
                        name = recipientName,
                        address = recipientAddress,
                        phone = order.customerPhone,
                        note = order.resolvedDropoffNote,
                        cardTag = "trip_details_dropoff_card",
                        callTag = "trip_details_dropoff_call_button",
                        navigateTag = "trip_details_dropoff_navigate_button",
                        onCall = { onCallClick(recipientName, order.customerPhone) },
                        onNavigate = {
                            onNavigateToLocation(
                                recipientName,
                                if (order.customerLat != 0.0) order.customerLat else 9.7400,
                                if (order.customerLng != 0.0) order.customerLng else 118.7360
                            )
                        }
                    )

                    // 3. PARCEL Card
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("trip_details_parcel_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = DelivioYellow.copy(alpha = 0.25f)
                                ) {
                                    Text(
                                        text = "PARCEL",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = TextOnYellow,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }

                                if (order.parcelFragile) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = DelivioRed.copy(alpha = 0.14f)
                                    ) {
                                        Text(
                                            text = "Fragile",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = DelivioRed,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }

                            TripDetailsInfoRow("Size", order.parcelSize.trim().ifBlank { "Standard" })
                            TripDetailsInfoRow("Weight", formatParcelWeightKg(order.parcelWeightKg))
                            TripDetailsInfoRow("Description", order.parcelItemDescription.trim().ifBlank { "No description" })
                            TripDetailsInfoRow("Fragile", if (order.parcelFragile) "Yes" else "No")
                            TripDetailsInfoRow("Vehicle", order.resolvedParcelVehicleLabel)
                        }
                    }

                    // 4. PAYMENT Card (never fee breakdowns)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("trip_details_payment_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (order.parcelHasCashToCollect) Color(0xFFFFF3E0) else DelivioGreen.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = "PAYMENT",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (order.parcelHasCashToCollect) Color(0xFFE65100) else DelivioGreenDark,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }

                            val whoPays = if (order.resolvedParcelFeePayer == "recipient") "Recipient" else "Sender"
                            val methodText = if (order.isCod) {
                                "Cash (COD)"
                            } else if (order.paymentProviderLabel.isNotBlank()) {
                                "Paid online (${order.paymentProviderLabel})"
                            } else {
                                "Paid online"
                            }

                            TripDetailsInfoRow("Delivery fee payer", whoPays)
                            TripDetailsInfoRow("Payment method", methodText)

                            if (order.parcelHasCashToCollect) {
                                TripDetailsInfoRow(
                                    "Cash to collect",
                                    "$currencySymbol${formatParcelAmount(order.cashAmountToCollect)}"
                                )
                                val collectStep = if (order.resolvedParcelFeePayer == "recipient") {
                                    "Drop-off (recipient)"
                                } else {
                                    "Pickup (sender)"
                                }
                                TripDetailsInfoRow("Collection step", collectStep)
                                if (order.cashCollected > 0.0 || order.cashCollectedStage.isNotBlank()) {
                                    val collectedAmt = if (order.cashCollected > 0.0) order.cashCollected else order.cashAmountToCollect
                                    val stageText = when (order.cashCollectedStage.lowercase(Locale.US)) {
                                        "pickup" -> "Collected at pickup"
                                        "dropoff" -> "Collected at drop-off"
                                        else -> "Collected"
                                    }
                                    TripDetailsInfoRow(
                                        "Collection status",
                                        "$stageText ($currencySymbol${formatParcelAmount(collectedAmt)})"
                                    )
                                } else {
                                    TripDetailsInfoRow("Collection status", "Not collected yet")
                                }
                            } else {
                                TripDetailsInfoRow("Cash to collect", "No cash to collect")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TripDetailsEndpointCard(
    badgeText: String,
    badgeColor: Color,
    name: String,
    address: String,
    phone: String,
    note: String,
    cardTag: String,
    callTag: String,
    navigateTag: String,
    onCall: () -> Unit,
    onNavigate: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(cardTag)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = badgeColor.copy(alpha = 0.14f)
            ) {
                Text(
                    text = badgeText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = badgeColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }

            Text(
                text = name,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
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
                    text = address,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = phone.ifBlank { "No phone number" },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                OutlinedButton(
                    onClick = onCall,
                    enabled = phone.isNotBlank(),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag(callTag)
                ) {
                    Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Call", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            if (note.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
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
                            text = note,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Button(
                onClick = onNavigate,
                colors = ButtonDefaults.buttonColors(
                    containerColor = DelivioYellow,
                    contentColor = TextOnYellow
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag(navigateTag)
            ) {
                Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Navigate", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun TripDetailsInfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 12.dp)
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
