package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.Order
import com.example.data.models.PaymentType

@Composable
fun PaymentBadgeChip(
    order: Order,
    currencySymbol: String = "₱",
    modifier: Modifier = Modifier,
    isLarge: Boolean = true
) {
    PaymentBadgeContent(
        paymentType = order.paymentType,
        providerLabel = order.paymentProviderLabel,
        amountToCollect = order.cashAmountToCollect,
        isRequestRider = order.isRequestRider,
        currencySymbol = currencySymbol,
        isLarge = isLarge,
        modifier = modifier
    )
}

@Composable
fun PaymentBadgeContent(
    paymentType: PaymentType,
    providerLabel: String,
    amountToCollect: Double,
    isRequestRider: Boolean = false,
    currencySymbol: String = "₱",
    isLarge: Boolean = true,
    modifier: Modifier = Modifier
) {
    when (paymentType) {
        PaymentType.COD -> {
            // Large chip, orange "COD - collect ₱X"
            val text = "COD - collect $currencySymbol${String.format("%.2f", amountToCollect)}"
            val bg = Color(0xFFFFF3E0)
            val borderColor = Color(0xFFFF9800)
            val textColor = Color(0xFFE65100)

            Surface(
                shape = RoundedCornerShape(if (isLarge) 12.dp else 8.dp),
                color = bg,
                border = BorderStroke(1.dp, borderColor),
                modifier = modifier.testTag("payment_chip_cod")
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = if (isLarge) 12.dp else 8.dp,
                        vertical = if (isLarge) 6.dp else 4.dp
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Payments,
                        contentDescription = "COD",
                        tint = textColor,
                        modifier = Modifier.size(if (isLarge) 18.dp else 14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = text,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = if (isLarge) 13.sp else 11.sp,
                        color = textColor
                    )
                }
            }
        }
        PaymentType.PAID_ONLINE -> {
            // Large chip, green "Paid Online - no cash to collect" plus provider name
            val providerSuffix = if (providerLabel.isNotBlank()) " ($providerLabel)" else ""
            val text = "Paid Online - no cash to collect$providerSuffix"
            val bg = Color(0xFFE8F5E9)
            val borderColor = Color(0xFF4CAF50)
            val textColor = Color(0xFF2E7D32)

            Surface(
                shape = RoundedCornerShape(if (isLarge) 12.dp else 8.dp),
                color = bg,
                border = BorderStroke(1.dp, borderColor),
                modifier = modifier.testTag("payment_chip_paid_online")
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = if (isLarge) 12.dp else 8.dp,
                        vertical = if (isLarge) 6.dp else 4.dp
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Paid Online",
                        tint = textColor,
                        modifier = Modifier.size(if (isLarge) 18.dp else 14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = text,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (isLarge) 13.sp else 11.sp,
                        color = textColor
                    )
                }
            }
        }
        PaymentType.UNKNOWN -> {
            // Never default to "Paid Online". If missing or unknown, show "Payment unknown - contact support"
            val text = "Payment unknown - contact support"
            val bg = Color(0xFFFFEBEE)
            val borderColor = Color(0xFFE57373)
            val textColor = Color(0xFFC62828)

            Surface(
                shape = RoundedCornerShape(if (isLarge) 12.dp else 8.dp),
                color = bg,
                border = BorderStroke(1.dp, borderColor),
                modifier = modifier.testTag("payment_chip_unknown")
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = if (isLarge) 12.dp else 8.dp,
                        vertical = if (isLarge) 6.dp else 4.dp
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Payment unknown",
                        tint = textColor,
                        modifier = Modifier.size(if (isLarge) 18.dp else 14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = text,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (isLarge) 13.sp else 11.sp,
                        color = textColor
                    )
                }
            }
        }
    }
}

@Composable
fun TipBadgeChip(
    tipAmount: Double,
    currencySymbol: String = "₱",
    modifier: Modifier = Modifier,
    isLarge: Boolean = false
) {
    if (tipAmount <= 0.0) return

    val tipFormatted = if (tipAmount % 1.0 == 0.0) {
        String.format("%.0f", tipAmount)
    } else {
        String.format("%.2f", tipAmount)
    }
    val text = "Tip $currencySymbol$tipFormatted"

    Surface(
        shape = RoundedCornerShape(if (isLarge) 12.dp else 8.dp),
        color = Color(0xFFE8F5E9),
        border = BorderStroke(1.dp, Color(0xFF4CAF50)),
        modifier = modifier.testTag("tip_chip")
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (isLarge) 10.dp else 8.dp,
                vertical = if (isLarge) 5.dp else 3.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.VolunteerActivism,
                contentDescription = "Tip",
                tint = Color(0xFF2E7D32),
                modifier = Modifier.size(if (isLarge) 16.dp else 12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                fontWeight = FontWeight.ExtraBold,
                fontSize = if (isLarge) 13.sp else 11.sp,
                color = Color(0xFF2E7D32)
            )
        }
    }
}

@Composable
fun ScheduledOrderChip(
    order: Order,
    modifier: Modifier = Modifier,
    isLarge: Boolean = false
) {
    if (!order.isScheduled) return

    val text = order.scheduledChipText

    Surface(
        shape = RoundedCornerShape(if (isLarge) 12.dp else 8.dp),
        color = Color(0xFFE3F2FD),
        border = BorderStroke(1.dp, Color(0xFF2196F3)),
        modifier = modifier.testTag("scheduled_chip")
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (isLarge) 10.dp else 8.dp,
                vertical = if (isLarge) 5.dp else 3.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = "Scheduled",
                tint = Color(0xFF1565C0),
                modifier = Modifier.size(if (isLarge) 16.dp else 12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                fontWeight = FontWeight.Bold,
                fontSize = if (isLarge) 12.sp else 11.sp,
                color = Color(0xFF1565C0)
            )
        }
    }
}

