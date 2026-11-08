package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.DocumentAlertLevel
import com.example.data.models.DocumentExpiryAlert

@Composable
fun DocumentExpiryBanner(
    alert: DocumentExpiryAlert,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (bgColor, borderColor, contentColor, icon) = when (alert.status) {
        DocumentAlertLevel.EXPIRED -> Quadruple(
            Color(0xFFFFEBEE),
            Color(0xFFD32F2F),
            Color(0xFFB71C1C),
            Icons.Default.Error
        )
        DocumentAlertLevel.WARNING_7_DAYS -> Quadruple(
            Color(0xFFFFF3E0),
            Color(0xFFE65100),
            Color(0xFFBF360C),
            Icons.Default.Warning
        )
        DocumentAlertLevel.NOTICE_14_DAYS -> Quadruple(
            Color(0xFFFFF8E1),
            Color(0xFFFFA000),
            Color(0xFFE65100),
            Icons.Default.Schedule
        )
        DocumentAlertLevel.REMINDER_30_DAYS -> Quadruple(
            Color(0xFFE1F5FE),
            Color(0xFF0288D1),
            Color(0xFF01579B),
            Icons.Default.Info
        )
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = BorderStroke(1.5.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("document_expiry_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "Alert",
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alert.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = contentColor
                )
                Text(
                    text = alert.message,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    color = contentColor.copy(alpha = 0.9f)
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(28.dp)
                    .testTag("dismiss_document_alert")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = contentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
