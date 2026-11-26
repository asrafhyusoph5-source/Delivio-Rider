package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DelivioGreen
import com.example.ui.theme.DelivioGreenDark
import com.example.ui.theme.DelivioRed

@Composable
fun OnlineOfflinePill(
    isOnline: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val containerColor = if (isOnline) {
        DelivioGreen.copy(alpha = 0.16f).compositeOver(MaterialTheme.colorScheme.surface)
    } else {
        Color(0xFF9E9E9E).copy(alpha = 0.16f).compositeOver(MaterialTheme.colorScheme.surface)
    }
    val borderColor = if (isOnline) {
        DelivioGreen.copy(alpha = 0.65f)
    } else {
        DelivioRed.copy(alpha = 0.45f)
    }
    val textColor = if (isOnline) DelivioGreenDark else DelivioRed

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 6.dp,
        color = containerColor,
        border = BorderStroke(1.2.dp, borderColor),
        modifier = modifier
            .heightIn(min = 48.dp)
            .testTag("online_offline_pill")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isOnline) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(DelivioGreen.copy(alpha = 0.35f))
                    )
                }
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(if (isOnline) DelivioGreen else DelivioRed)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = if (isOnline) "Online" else "Offline",
                color = textColor,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}
