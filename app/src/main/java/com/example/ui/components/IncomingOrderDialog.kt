package com.example.ui.components

import android.content.Context
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.constants.OrderStepConstants
import com.example.data.models.OfferBundle
import com.example.data.models.OfferType
import com.example.data.models.Order
import com.example.data.models.OrderPayBreakdown
import com.example.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun IncomingOrderDialog(
    bundle: OfferBundle,
    currencySymbol: String,
    soundEnabled: Boolean,
    vibrationEnabled: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onMissed: () -> Unit = onDecline
) {
    val context = LocalContext.current
    var secondsLeft by remember(bundle.id) { mutableIntStateOf(30) }

    // Sound & Vibration on launch
    LaunchedEffect(bundle.id) {
        if (soundEnabled) {
            try {
                val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val r = RingtoneManager.getRingtone(context, notification)
                r.play()
            } catch (_: Exception) {}
        }
        if (vibrationEnabled) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vm?.defaultVibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 200, 100, 200), -1))
                } else {
                    @Suppress("DEPRECATION")
                    val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                    @Suppress("DEPRECATION")
                    v?.vibrate(longArrayOf(0, 200, 100, 200), -1)
                }
            } catch (_: Exception) {}
        }
    }

    // 30-second countdown
    LaunchedEffect(bundle.id) {
        while (secondsLeft > 0) {
            delay(1000L)
            secondsLeft -= 1
        }
        onMissed()
    }

    val firstOrder = bundle.firstOrder
    val serviceBadge = OrderStepConstants.getServiceBadge(firstOrder.serviceType)

    Dialog(
        onDismissRequest = { /* Modal dialog */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
                .testTag("incoming_order_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Top Fixed Header: Badges & 30s Countdown Timer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = DelivioYellow
                        ) {
                            Text(
                                text = serviceBadge.uppercase(),
                                color = TextOnYellow,
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }

                        if (bundle.isDoubleBooking) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF1565C0)
                            ) {
                                Text(
                                    text = "DOUBLE BOOKING",
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                        } else if (bundle.isAddToBooking) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF2E7D32)
                            ) {
                                Text(
                                    text = "ADD TO BOOKING",
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                        }

                        // Order badges (Priority, Saver, SPLIT)
                        val badgesList = bundle.orders.map {
                            if (it.isSplitChild) "SPLIT" else it.badge
                        }.distinct()
                        badgesList.forEach { b ->
                            val bNorm = b.trim().lowercase()
                            if (bNorm in listOf("priority", "saver", "split")) {
                                val bColor = when (bNorm) {
                                    "priority" -> DelivioRed
                                    "saver" -> Color(0xFF00796B)
                                    "split" -> Color(0xFF7B1FA2)
                                    else -> MaterialTheme.colorScheme.secondary
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = bColor
                                ) {
                                    Text(
                                        text = b.uppercase(),
                                        color = Color.White,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }

                        if (bundle.orders.any { it.isSplitChild || it.badge.equals("split", ignoreCase = true) }) {
                            NoCashCollectedChip(isLarge = false)
                        }

                        if (firstOrder.isTaxi) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = OrderStepConstants.getVehicleDisplayName(firstOrder.vehicleType),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Circular Countdown Timer
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(if (secondsLeft <= 5) DelivioRed else DelivioYellowLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$secondsLeft",
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp,
                            color = if (secondsLeft <= 5) Color.White else TextOnYellow
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable Body Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Estimated Earnings Callout
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = DelivioYellowLight,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "TOTAL ESTIMATED EARNINGS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextOnYellow.copy(alpha = 0.85f)
                            )
                            Text(
                                text = "$currencySymbol${String.format("%.2f", bundle.totalDriverPay)}",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black,
                                color = TextOnYellow
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface
                                ) {
                                    Text(
                                        text = "${bundle.orders.size} ${if (bundle.orders.size > 1) "Orders" else "Order"} • ${bundle.totalStopsCount} Stops",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface
                                ) {
                                    Text(
                                        text = "~${String.format("%.1f", bundle.firstPickupDistanceKm)} km to pickup",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Itemized Pay Breakdown Section
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = "ITEMIZED DRIVER PAY",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )

                            if (bundle.pickupFee > 0.0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Pickup Fee (Stop 1)",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "$currencySymbol${String.format("%.2f", bundle.pickupFee)}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            bundle.breakdowns.forEachIndexed { idx, bk ->
                                val orderLabel = if (bundle.orders.size > 1) "Order ${idx + 1} (${bk.displayOrderNumber}) Delivery" else "Delivery Fee"
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = orderLabel,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "$currencySymbol${String.format("%.2f", bk.deliveryFee)}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                if (bk.tip > 0.0) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = if (bundle.orders.size > 1) "  ↳ Order ${idx + 1} Tip" else "Customer Tip",
                                            fontSize = 12.sp,
                                            color = Color(0xFF2E7D32)
                                        )
                                        Text(
                                            text = "+$currencySymbol${String.format("%.2f", bk.tip)}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = Color(0xFF2E7D32)
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 3.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Total Driver Pay",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "$currencySymbol${String.format("%.2f", bundle.totalDriverPay)}",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp,
                                    color = DelivioYellowDark
                                )
                            }
                        }
                    }

                    // Orders List & Payment Details (COD amounts / Paid Online)
                    bundle.orders.forEachIndexed { idx, ord ->
                        val bk = bundle.breakdowns.getOrNull(idx)
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
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
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = if (bundle.orders.size > 1) "Order ${idx + 1} • ${ord.displayOrderNumber}" else "Order ${ord.displayOrderNumber}",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (ord.badge.isNotBlank() && !ord.badge.equals("standard", ignoreCase = true)) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = DelivioYellowLight
                                            ) {
                                                Text(
                                                    text = ord.badge.uppercase(),
                                                    color = TextOnYellow,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Payment badge for this order (COD or Paid Online or Split child)
                                    if (ord.isSplitChild || ord.badge.equals("split", ignoreCase = true)) {
                                        NoCashCollectedChip(isLarge = false)
                                    } else if (ord.isCod) {
                                        val codAmt = if (bk != null && bk.codAmount > 0.0) bk.codAmount else ord.cashAmountToCollect
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = DelivioYellowDark
                                        ) {
                                            Text(
                                                text = "COD: Collect $currencySymbol${String.format("%.2f", codAmt)}",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                            )
                                        }
                                    } else if (ord.isPaidOnline) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFF2E7D32)
                                        ) {
                                            Text(
                                                text = "Paid Online",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                            )
                                        }
                                    } else {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = DelivioRed
                                        ) {
                                            Text(
                                                text = "Payment Unknown",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }

                                // Pickup location
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (ord.isTaxi) Icons.Default.DirectionsCar else Icons.Default.Storefront,
                                        contentDescription = "Pickup",
                                        tint = DelivioYellowDark,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (ord.merchantAddress.isNotBlank()) ord.merchantAddress else ord.merchantName,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                }

                                // Drop-off location
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.LocationOn,
                                        contentDescription = "Drop-off",
                                        tint = DelivioRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Drop-off Area: ${ord.customerArea}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                }

                                if (ord.prepStatusText.isNotBlank() || ord.tip > 0) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (ord.prepStatusText.isNotBlank()) {
                                            MerchantPrepStatusChip(order = ord)
                                        }
                                        if (ord.tip > 0) {
                                            TipBadgeChip(tipAmount = ord.tip, currencySymbol = currencySymbol)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Actions: Accept & Decline
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDecline,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text("Decline", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    }

                    val acceptLabel = when {
                        bundle.orders.size > 1 -> "Accept ${bundle.orders.size} Orders"
                        bundle.isAddToBooking -> "Add to Booking"
                        firstOrder.isTaxi -> "Accept Trip"
                        else -> "Accept Order"
                    }

                    Button(
                        onClick = onAccept,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DelivioYellow, contentColor = TextOnYellow),
                        modifier = Modifier
                            .weight(1.5f)
                            .height(48.dp)
                    ) {
                        Text(acceptLabel, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

// Single order backward-compatible wrapper
@Composable
fun IncomingOrderDialog(
    order: Order,
    currencySymbol: String,
    soundEnabled: Boolean,
    vibrationEnabled: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onMissed: () -> Unit = onDecline
) {
    val breakdown = OrderPayBreakdown(
        orderId = order.id,
        orderNumber = order.orderNumber,
        rank = 1,
        deliveryFee = order.deliveryFee,
        pickupFee = 0.0,
        tip = order.tip,
        totalPay = order.estimatedEarnings,
        isCod = order.isCod,
        codAmount = order.cashAmountToCollect,
        badge = order.badge,
        customerArea = order.customerArea
    )
    val bundle = OfferBundle(
        id = order.id,
        type = OfferType.SINGLE,
        orders = listOf(order),
        firstPickupDistanceKm = order.pickupDistanceKm,
        totalTripDistanceKm = order.deliveryDistanceKm,
        pickupFee = 0.0,
        breakdowns = listOf(breakdown),
        totalDriverPay = order.estimatedEarnings,
        eligibleSince = System.currentTimeMillis(),
        waveDelaySeconds = 0,
        badge = order.badge
    )
    IncomingOrderDialog(
        bundle = bundle,
        currencySymbol = currencySymbol,
        soundEnabled = soundEnabled,
        vibrationEnabled = vibrationEnabled,
        onAccept = onAccept,
        onDecline = onDecline,
        onMissed = onMissed
    )
}
