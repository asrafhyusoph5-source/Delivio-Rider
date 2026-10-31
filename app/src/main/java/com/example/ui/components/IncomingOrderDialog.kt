package com.example.ui.components

import android.content.Context
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.data.models.Order
import com.example.ui.theme.*
import kotlinx.coroutines.delay

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
    val context = LocalContext.current
    var secondsLeft by remember(order.id) { mutableIntStateOf(30) }

    // Sound & Vibration on launch
    LaunchedEffect(order.id) {
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
    LaunchedEffect(order.id) {
        while (secondsLeft > 0) {
            delay(1000L)
            secondsLeft -= 1
        }
        onMissed()
    }

    val badge = OrderStepConstants.getServiceBadge(order.serviceType)

    Dialog(
        onDismissRequest = { /* Modal dialog */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .testTag("incoming_order_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with Badge & Countdown
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = DelivioYellow
                        ) {
                            Text(
                                text = badge.uppercase(),
                                color = TextOnYellow,
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }

                        if (order.isTaxi) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = OrderStepConstants.getVehicleDisplayName(order.vehicleType),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    // Circular Countdown Timer
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (secondsLeft <= 5) DelivioRed else DelivioYellowLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$secondsLeft",
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            color = if (secondsLeft <= 5) Color.White else TextOnYellow
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Estimated Earnings Callout
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = DelivioYellowLight,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "ESTIMATED EARNINGS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextOnYellow.copy(alpha = 0.8f)
                        )
                        Text(
                            text = "$currencySymbol${String.format("%.2f", order.estimatedEarnings)}",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            color = TextOnYellow
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        if (order.isTaxi) {
                            val paymentText = if (order.isCod) {
                                "Collect $currencySymbol${String.format("%.2f", order.cashAmountToCollect)} from passenger"
                            } else {
                                "Paid"
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (order.isCod) DelivioYellowDark else Color(0xFF2E7D32)
                            ) {
                                Text(
                                    text = paymentText,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        } else {
                            PaymentBadgeChip(
                                order = order,
                                currencySymbol = currencySymbol,
                                isLarge = true
                            )
                        }
                        if (order.prepStatusText.isNotBlank() || order.isEditedByMerchant) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
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
                        if (order.isRequestRider) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Delivery fee: Paid by merchant",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextOnYellow
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                val pickupTitle = when {
                    order.isTaxi -> "Pickup Location"
                    order.serviceType == OrderStepConstants.SERVICE_PARCEL -> "Pickup / Sender"
                    order.serviceType == OrderStepConstants.SERVICE_PABILI -> "Purchase at Vendor"
                    else -> "Pickup Merchant"
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (order.isTaxi) Icons.Default.DirectionsCar else Icons.Default.Storefront,
                                contentDescription = "Pickup",
                                tint = DelivioYellowDark,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(pickupTitle.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                val pickupAddr = if (order.merchantAddress.isNotBlank()) order.merchantAddress else order.merchantName
                                Text(pickupAddr, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }

                        HorizontalDivider()

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, contentDescription = "Drop-off Area", tint = DelivioRed, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    if (order.isTaxi) "DROP-OFF DESTINATION (${order.totalStopsCount} STOPS)" else "DROP-OFF DESTINATION",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text("Area: ${order.customerArea}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Passenger identity revealed after accepting", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        if (order.pickupDistanceKm > 0 || order.deliveryDistanceKm > 0) {
                            HorizontalDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Distance to pickup: ~${order.pickupDistanceKm} km", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Trip distance: ~${order.deliveryDistanceKm} km", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Actions: Accept & Decline
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDecline,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    ) {
                        Text("Decline", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                    }

                    Button(
                        onClick = onAccept,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DelivioYellow, contentColor = TextOnYellow),
                        modifier = Modifier
                            .weight(1.5f)
                            .height(50.dp)
                    ) {
                        Text(if (order.isTaxi) "Accept Trip" else "Accept Order", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}
