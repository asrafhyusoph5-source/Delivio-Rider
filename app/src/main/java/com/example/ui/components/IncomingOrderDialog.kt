package com.example.ui.components

import android.content.Context
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.constants.OrderStepConstants
import com.example.data.models.OfferBundle
import com.example.data.models.OfferType
import com.example.data.models.Order
import com.example.data.models.OrderPayBreakdown
import com.example.data.models.deduplicateAddress
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
    onMissed: () -> Unit = onDecline,
    onDisplayed: () -> Unit = {}
) {
    val context = LocalContext.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val totalTimerSeconds = bundle.timerSeconds.coerceAtLeast(10)
    var secondsLeft by remember(bundle.id) { mutableIntStateOf(totalTimerSeconds) }
    var showPayBreakdown by remember(bundle.id) { mutableStateOf(false) }

    // Log display, play sound & trigger vibration on launch
    LaunchedEffect(bundle.id) {
        onDisplayed()
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

    // Countdown timer (20s for Delivio Instant, 30s otherwise)
    LaunchedEffect(bundle.id) {
        while (secondsLeft > 0) {
            delay(1000L)
            secondsLeft -= 1
        }
        onMissed()
    }

    val firstOrder = bundle.firstOrder
    val serviceBadge = OrderStepConstants.getServiceBadge(firstOrder.serviceType)
    val timerProgress = (secondsLeft.toFloat() / totalTimerSeconds.toFloat()).coerceIn(0f, 1f)

    Dialog(
        onDismissRequest = { /* Modal offer dialog */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .wrapContentHeight()
                .heightIn(max = (screenHeight * 0.85f).coerceAtLeast(360.dp))
                .navigationBarsPadding()
                .testTag("incoming_order_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(18.dp)
            ) {
                // Top Header: Service Badges & Circular Countdown Ring
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = DelivioYellow
                        ) {
                            Text(
                                text = serviceBadge.uppercase(),
                                color = TextOnYellow,
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        } else if (bundle.isAddToBooking) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = DelivioGreenDark
                            ) {
                                Text(
                                    text = "ADD TO BOOKING",
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

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
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        val schedOrder = bundle.orders.firstOrNull { it.isScheduled }
                        if (schedOrder != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF1565C0)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = "Scheduled",
                                        tint = Color.White,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = if (schedOrder.scheduledDateText.isNotBlank()) "SCHEDULED • ${schedOrder.scheduledDateText}" else "SCHEDULED",
                                        color = Color.White,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }

                        if (firstOrder.isTaxi) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = OrderStepConstants.getVehicleDisplayName(firstOrder.vehicleType),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Circular Countdown Ring
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (secondsLeft <= 5) DelivioRed.copy(alpha = 0.14f)
                                else DelivioYellow.copy(alpha = 0.16f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            progress = { timerProgress },
                            modifier = Modifier.fillMaxSize(),
                            color = if (secondsLeft <= 5) DelivioRed else DelivioYellow,
                            trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                            strokeWidth = 4.dp
                        )
                        Text(
                            text = "$secondsLeft",
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            color = if (secondsLeft <= 5) DelivioRed else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Body (wraps content height, scrolls only if taller than screen max)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Large Total Earnings Callout
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = DelivioYellowLight,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "ESTIMATED EARNINGS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextOnYellow.copy(alpha = 0.8f)
                            )
                            Text(
                                text = "$currencySymbol${String.format("%.2f", bundle.totalDriverPay)}",
                                fontSize = 32.sp,
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
                                        text = "~${String.format("%.1f", bundle.firstPickupDistanceKm)} km away",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Route Summary Cards (Concise 2-line pickup -> drop-off + 1 payment chip)
                    bundle.orders.forEachIndexed { idx, ord ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Line 1: Pickup
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (ord.isTaxi) Icons.Default.DirectionsCar else Icons.Default.Storefront,
                                        contentDescription = "Pickup",
                                        tint = DelivioGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    val pickupAddressClean = deduplicateAddress(ord.merchantAddress)
                                    val pickupLabel = if (ord.isTaxi) {
                                        pickupAddressClean.ifBlank { ord.displayPickupName }
                                    } else {
                                        ord.displayPickupName
                                    }
                                    Text(
                                        text = if (bundle.orders.size > 1) "${idx + 1}. $pickupLabel" else pickupLabel,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                // Line 2: Drop-off area + distance
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = "Drop-off",
                                        tint = DelivioRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    val dropoffArea = deduplicateAddress(ord.customerArea)
                                        .ifBlank { deduplicateAddress(ord.customerAddress) }
                                        .ifBlank { "Drop-off location" }
                                    val distSuffix = if (ord.deliveryDistanceKm > 0.0) {
                                        " • ${String.format("%.1f", ord.deliveryDistanceKm)} km"
                                    } else {
                                        ""
                                    }
                                    Text(
                                        text = "$dropoffArea$distSuffix",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                // Single payment chip (+ tip/prep/scheduled if relevant)
                                @OptIn(ExperimentalLayoutApi::class)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    PaymentBadgeChip(
                                        order = ord,
                                        currencySymbol = currencySymbol,
                                        isLarge = false
                                    )
                                    if (ord.tip > 0.0) {
                                        TipBadgeChip(tipAmount = ord.tip, currencySymbol = currencySymbol)
                                    }
                                    if (ord.prepStatusText.isNotBlank()) {
                                        MerchantPrepStatusChip(order = ord)
                                    }
                                }
                            }
                        }
                    }

                    // Collapsed Pay Breakdown Toggle
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showPayBreakdown = !showPayBreakdown }
                            .testTag("offer_pay_breakdown_toggle")
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Pay breakdown",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Icon(
                                    imageVector = if (showPayBreakdown) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (showPayBreakdown) "Hide pay breakdown" else "Show pay breakdown",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            AnimatedVisibility(visible = showPayBreakdown) {
                                Column(
                                    modifier = Modifier.padding(top = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                    if (bundle.pickupFee > 0.0) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Pickup Fee (Stop 1)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("$currencySymbol${String.format("%.2f", bundle.pickupFee)}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                    bundle.breakdowns.forEachIndexed { idx, bk ->
                                        val orderLabel = if (bundle.orders.size > 1) "Order ${idx + 1} Delivery Fee" else "Delivery Fee"
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(orderLabel, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("$currencySymbol${String.format("%.2f", bk.deliveryFee)}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                        if (bk.tip > 0.0) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    if (bundle.orders.size > 1) "Order ${idx + 1} Tip" else "Customer Tip",
                                                    fontSize = 12.sp,
                                                    color = DelivioGreenDark
                                                )
                                                Text(
                                                    "+$currencySymbol${String.format("%.2f", bk.tip)}",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = DelivioGreenDark
                                                )
                                            }
                                        }
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Total Driver Pay", fontWeight = FontWeight.Black, fontSize = 13.sp)
                                        Text(
                                            "$currencySymbol${String.format("%.2f", bundle.totalDriverPay)}",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 13.sp,
                                            color = DelivioYellowDark
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Pinned Bottom Actions: Decline & Accept (>= 48.dp touch targets)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDecline,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("decline_offer_button")
                    ) {
                        Text("Decline", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    }

                    val acceptLabel = when {
                        bundle.orders.size > 1 -> "Accept ${bundle.orders.size} Orders"
                        bundle.isAddToBooking -> "Add to Booking"
                        firstOrder.isTaxi -> "Accept Trip"
                        else -> "Accept"
                    }

                    Button(
                        onClick = onAccept,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DelivioYellow, contentColor = TextOnYellow),
                        modifier = Modifier
                            .weight(1.5f)
                            .height(50.dp)
                            .testTag("accept_offer_button")
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
    onMissed: () -> Unit = onDecline,
    onDisplayed: () -> Unit = {}
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
    val timerSecs = if (order.serviceType.equals(OrderStepConstants.SERVICE_REQUEST, ignoreCase = true)) 20 else 30
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
        badge = order.badge,
        timerSeconds = timerSecs
    )
    IncomingOrderDialog(
        bundle = bundle,
        currencySymbol = currencySymbol,
        soundEnabled = soundEnabled,
        vibrationEnabled = vibrationEnabled,
        onAccept = onAccept,
        onDecline = onDecline,
        onMissed = onMissed,
        onDisplayed = onDisplayed
    )
}
