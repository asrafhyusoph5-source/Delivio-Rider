package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.constants.InstantDispatchEvaluator
import com.example.data.constants.OrderStepConstants
import com.example.data.models.DispatchListenerStatus
import com.example.data.models.ListenerConnectionState
import com.example.data.models.SkippedOrderRecord
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DispatchStatusFormatter {
    fun formatOnlineSince(onlineSinceMillis: Long, nowMillis: Long): String {
        if (onlineSinceMillis <= 0L) return "Just went online"
        val clock = try {
            SimpleDateFormat("h:mm a", Locale.US).format(Date(onlineSinceMillis))
        } catch (_: Throwable) {
            ""
        }
        val elapsedSec = ((nowMillis - onlineSinceMillis) / 1000L).coerceAtLeast(0L)
        val elapsed = formatElapsedShort(elapsedSec)
        return if (clock.isNotBlank()) "$clock ($elapsed ago)" else "$elapsed ago"
    }

    fun formatLocationAge(lastLocationMillis: Long, nowMillis: Long): Pair<String, Boolean> {
        if (lastLocationMillis <= 0L) {
            return "No GPS fix yet" to false
        }
        val ageMs = (nowMillis - lastLocationMillis).coerceAtLeast(0L)
        val isFresh = ageMs <= InstantDispatchEvaluator.DEFAULT_MAX_LOCATION_AGE_MS
        val ageLabel = formatElapsedShort(ageMs / 1000L)
        val prefix = if (isFresh) "Fresh" else "Stale"
        return "$prefix • $ageLabel ago" to isFresh
    }

    fun formatListenerState(status: DispatchListenerStatus, nowMillis: Long): Pair<String, String> {
        val label = when (status.state) {
            ListenerConnectionState.CONNECTED -> "Connected"
            ListenerConnectionState.RESTARTING -> "Restarting"
            ListenerConnectionState.DISCONNECTED -> "Disconnected"
        }
        val detail = when (status.state) {
            ListenerConnectionState.CONNECTED -> {
                if (status.lastUpdateMillis > 0L) {
                    val clock = try {
                        SimpleDateFormat("h:mm:ss a", Locale.US).format(Date(status.lastUpdateMillis))
                    } catch (_: Throwable) {
                        ""
                    }
                    val ago = formatElapsedShort(((nowMillis - status.lastUpdateMillis) / 1000L).coerceAtLeast(0L))
                    if (clock.isNotBlank()) "Last update: $clock ($ago ago)" else "Last update: $ago ago"
                } else {
                    "Listening for orders"
                }
            }
            ListenerConnectionState.RESTARTING -> {
                val retryPart = if (status.nextRetrySeconds > 0) " in ${status.nextRetrySeconds}s" else ""
                "Attempt #${status.restartAttempt.coerceAtLeast(1)} • retrying$retryPart"
            }
            ListenerConnectionState.DISCONNECTED -> "Listener paused"
        }
        return label to detail
    }

    fun formatElapsedShort(seconds: Long): String {
        val s = seconds.coerceAtLeast(0L)
        return when {
            s < 60L -> "${s}s"
            s < 3600L -> {
                val m = s / 60L
                val rem = s % 60L
                "${m}m ${rem}s"
            }
            else -> {
                val h = s / 3600L
                val m = (s % 3600L) / 60L
                "${h}h ${m}m"
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DispatchStatusSheet(
    onlineSinceMillis: Long,
    lastLocationMillis: Long,
    enabledServices: List<String>,
    vehicleType: String,
    listenerStatus: DispatchListenerStatus,
    ordersSeenCount: Int,
    badDataSkippedCount: Int,
    eligibleOrdersCount: Int,
    skippedOrders: List<SkippedOrderRecord>,
    hasActiveOrders: Boolean,
    onGoOffline: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("dispatch_status_sheet")
    ) {
        DispatchStatusSheetContent(
            onlineSinceMillis = onlineSinceMillis,
            lastLocationMillis = lastLocationMillis,
            enabledServices = enabledServices,
            vehicleType = vehicleType,
            listenerStatus = listenerStatus,
            ordersSeenCount = ordersSeenCount,
            badDataSkippedCount = badDataSkippedCount,
            eligibleOrdersCount = eligibleOrdersCount,
            skippedOrders = skippedOrders,
            hasActiveOrders = hasActiveOrders,
            onGoOffline = onGoOffline,
            onDismiss = onDismiss
        )
    }
}

@Composable
fun DispatchStatusSheetContent(
    onlineSinceMillis: Long,
    lastLocationMillis: Long,
    enabledServices: List<String>,
    vehicleType: String,
    listenerStatus: DispatchListenerStatus,
    ordersSeenCount: Int,
    badDataSkippedCount: Int,
    eligibleOrdersCount: Int,
    skippedOrders: List<SkippedOrderRecord>,
    hasActiveOrders: Boolean,
    onGoOffline: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val onlineSinceText = DispatchStatusFormatter.formatOnlineSince(onlineSinceMillis, nowMillis)
    val (locationAgeText, isLocationFresh) = DispatchStatusFormatter.formatLocationAge(lastLocationMillis, nowMillis)
    val (listenerLabel, listenerDetail) = DispatchStatusFormatter.formatListenerState(listenerStatus, nowMillis)
    val vehicleLabel = OrderStepConstants.getVehicleDisplayName(vehicleType)
    val servicesLabel = if (enabledServices.isEmpty()) "None active" else enabledServices.joinToString(", ")

    val listenerColor = when (listenerStatus.state) {
        ListenerConnectionState.CONNECTED -> DelivioGreen
        ListenerConnectionState.RESTARTING -> DelivioOrange
        ListenerConnectionState.DISCONNECTED -> DelivioRed
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = DelivioYellow.copy(alpha = 0.16f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Radar,
                            contentDescription = null,
                            tint = DelivioYellow,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = "Dispatch status",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(DelivioGreen)
                        )
                        Text(
                            text = "Online & monitoring orders",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = DelivioGreen
                        )
                    }
                }
            }
        }

        // Core Telemetry Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatusInfoRow(
                    icon = Icons.Default.Schedule,
                    label = "Online since",
                    value = onlineSinceText,
                    valueColor = MaterialTheme.colorScheme.onSurface
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                StatusInfoRow(
                    icon = Icons.Default.MyLocation,
                    label = "Location age",
                    value = locationAgeText,
                    valueColor = if (lastLocationMillis <= 0L) DelivioRed else if (isLocationFresh) DelivioGreen else DelivioOrange
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                StatusInfoRow(
                    icon = Icons.Default.TwoWheeler,
                    label = "Vehicle type",
                    value = vehicleLabel,
                    valueColor = MaterialTheme.colorScheme.onSurface
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                StatusInfoRow(
                    icon = Icons.Default.CheckCircle,
                    label = "Services that are on",
                    value = servicesLabel,
                    valueColor = if (enabledServices.isEmpty()) DelivioRed else MaterialTheme.colorScheme.onSurface
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                StatusInfoRow(
                    icon = Icons.Default.Sensors,
                    label = "Listener state",
                    value = "$listenerLabel — $listenerDetail",
                    valueColor = listenerColor
                )
            }
        }

        // Counts Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CountMetricCard(
                label = "Orders seen",
                count = ordersSeenCount,
                accent = DelivioYellow,
                modifier = Modifier
                    .weight(1f)
                    .testTag("dispatch_count_seen")
            )
            CountMetricCard(
                label = "Skipped (bad data)",
                count = badDataSkippedCount,
                accent = if (badDataSkippedCount > 0) DelivioRed else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .testTag("dispatch_count_bad_data")
            )
            CountMetricCard(
                label = "Eligible",
                count = eligibleOrdersCount,
                accent = DelivioGreen,
                modifier = Modifier
                    .weight(1f)
                    .testTag("dispatch_count_eligible")
            )
        }

        // Last 10 Skipped Orders
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Last skipped orders (${skippedOrders.size}/10)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (skippedOrders.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DoneAll,
                            contentDescription = null,
                            tint = DelivioGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "No skipped orders right now.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dispatch_skipped_orders_list")
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        skippedOrders.take(10).forEachIndexed { index, record ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(if (record.isBadData) DelivioRed else DelivioYellow)
                                )
                                Text(
                                    text = record.formattedLine,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (record.isBadData) DelivioRed else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (index < skippedOrders.take(10).lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                            }
                        }
                    }
                }
            }
        }

        // Actions Row: Go Offline + Close
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onGoOffline,
                enabled = !hasActiveOrders,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(
                    1.5.dp,
                    if (hasActiveOrders) MaterialTheme.colorScheme.outlineVariant else DelivioRed
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = DelivioRed
                ),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("dispatch_status_go_offline_button")
            ) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (hasActiveOrders) "Active Order" else "Go Offline",
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DelivioYellow,
                    contentColor = TextOnYellow
                ),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("dispatch_status_close_button")
            ) {
                Text(
                    text = "Done",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun StatusInfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = DelivioYellow,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(18.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = valueColor
            )
        }
    }
}

@Composable
private fun CountMetricCard(
    label: String,
    count: Int,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = count.toString(),
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = accent
            )
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
