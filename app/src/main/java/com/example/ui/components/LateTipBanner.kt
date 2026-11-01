package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.LateTipBannerInfo
import com.example.ui.theme.DelivioGreen
import com.example.ui.theme.DelivioGreenDark
import kotlinx.coroutines.delay

@Composable
fun LateTipBanner(
    bannerInfo: LateTipBannerInfo,
    currencySymbol: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Auto-dismiss after 6 seconds
    LaunchedEffect(bannerInfo) {
        delay(6000)
        onDismiss()
    }

    AnimatedVisibility(
        visible = true,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 6.dp,
            border = BorderStroke(1.5.dp, DelivioGreen),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("late_tip_banner")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DelivioGreen.copy(alpha = 0.15f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Celebration,
                            contentDescription = "Late Tip",
                            tint = DelivioGreenDark,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    val amountInt = bannerInfo.amount.toInt()
                    val amountFormatted = if (bannerInfo.amount == amountInt.toDouble()) {
                        amountInt.toString()
                    } else {
                        String.format("%.2f", bannerInfo.amount)
                    }
                    Text(
                        text = "Late Tip Received! +$currencySymbol$amountFormatted",
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = DelivioGreenDark
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Customer added a tip for Order #${bannerInfo.orderNumber}. 100% added to your earnings!",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 15.sp
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
