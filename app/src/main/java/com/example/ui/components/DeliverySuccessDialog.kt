package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.models.Order
import com.example.ui.theme.*

@Composable
fun DeliverySuccessDialog(
    order: Order,
    currencySymbol: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .testTag("delivery_success_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Success Badge
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(DelivioGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Success",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = if (order.isTaxi) "Trip Completed!" else "Delivery Completed!",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (order.isTaxi) "Trip #${order.orderNumber}" else "Order #${order.orderNumber}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Total Earnings Highlight
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = DelivioYellowLight,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "YOU EARNED",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF795548)
                        )
                        Text(
                            text = "$currencySymbol${String.format("%.2f", order.estimatedEarnings)}",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = TextOnYellow
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                PaymentBadgeChip(
                    order = order,
                    currencySymbol = currencySymbol,
                    isLarge = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (order.tip > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TipBadgeChip(
                        tipAmount = order.tip,
                        currencySymbol = currencySymbol,
                        isLarge = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Breakdown list
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (order.isTaxi) {
                        FareRow("Total Fare", "$currencySymbol${String.format("%.2f", order.feesTotal)}")
                        val commRate = if (order.platformCommission > 0.0) order.platformCommission else 15.0
                        val commAmount = order.feesTotal * (commRate / 100.0)
                        FareRow("Platform Commission (${commRate.toInt()}%)", "-$currencySymbol${String.format("%.2f", commAmount)}")
                        if (order.tip > 0) {
                            FareRow("Tip (100% to Driver)", "+$currencySymbol${String.format("%.2f", order.tip)}", isPositive = true)
                        }
                    } else {
                        FareRow("Base Pay", "$currencySymbol${String.format("%.2f", order.baseFare)}")
                        FareRow("Per-km Pay (${String.format("%.1f", order.deliveryDistanceKm)} km)", "$currencySymbol${String.format("%.2f", order.distanceFare)}")
                        if (order.waitingPay > 0) {
                            FareRow("Waiting-time Pay", "+$currencySymbol${String.format("%.2f", order.waitingPay)}", isPositive = true)
                        }
                        if (order.extraStopPay > 0) {
                            FareRow("Extra-stop Pay", "+$currencySymbol${String.format("%.2f", order.extraStopPay)}", isPositive = true)
                        }
                        if (order.returnTripPay > 0) {
                            FareRow("Return-trip Pay", "+$currencySymbol${String.format("%.2f", order.returnTripPay)}", isPositive = true)
                        }
                        if (order.urgentBonus > 0) {
                            FareRow("Urgent Request Bonus", "+$currencySymbol${String.format("%.2f", order.urgentBonus)}", isPositive = true)
                        }
                        if (order.surgeBonus > 0) {
                            FareRow("Peak Surge Bonus", "+$currencySymbol${String.format("%.2f", order.surgeBonus)}", isPositive = true)
                        }
                        if (order.tip > 0) {
                            FareRow("Tip", "+$currencySymbol${String.format("%.2f", order.tip)}", isPositive = true)
                        }
                        FareRow("Platform Commission", "-$currencySymbol${String.format("%.2f", order.platformCommission)}")
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    if (order.isCod) {
                        val collected = if (order.codCollected > 0) order.codCollected else order.cashAmountToCollect
                        FareRow("COD Cash Collected", "$currencySymbol${String.format("%.2f", collected)}", isBold = true)
                    } else if (order.isPaidOnline) {
                        val provider = if (order.paymentProviderLabel.isNotBlank()) " (${order.paymentProviderLabel})" else ""
                        FareRow("Payment", "Paid Online$provider", isBold = true)
                    } else {
                        FareRow("Payment", "Payment unknown - contact support", isBold = true)
                    }

                    if (order.isRequestRider) {
                        FareRow("Delivery Fee", "Paid by merchant")
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("dismiss_success_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DelivioYellow,
                        contentColor = TextOnYellow
                    )
                ) {
                    Text("Back to Orders", fontWeight = FontWeight.Black, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun FareRow(title: String, amount: String, isPositive: Boolean = false, isBold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = amount,
            fontSize = 13.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.SemiBold,
            color = if (isPositive) DelivioGreenDark else MaterialTheme.colorScheme.onSurface
        )
    }
}
