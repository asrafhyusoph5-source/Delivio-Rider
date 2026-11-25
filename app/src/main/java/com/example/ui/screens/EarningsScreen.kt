package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.*
import com.example.ui.components.CustomerOrderChatDialog
import com.example.ui.components.PaymentBadgeContent
import com.example.ui.theme.*
import com.example.viewmodel.RiderViewModel
import java.util.Locale

@Composable
fun EarningsScreen(
    viewModel: RiderViewModel,
    onBack: () -> Unit,
    onOpenIncomeHistory: () -> Unit = {}
) {
    val currencySymbol by viewModel.currencySymbol.collectAsState()
    val deliveredOrders by viewModel.deliveredOrders.collectAsState()
    val questClaims by viewModel.questClaims.collectAsState()
    val sessionClaims by viewModel.sessionClaims.collectAsState()
    val platformTimezone by viewModel.platformTimezone.collectAsState()
    val activeChatOrder by viewModel.activeChatOrder.collectAsState()
    val activeChatMessages by viewModel.activeChatMessages.collectAsState()

    EarningsContent(
        currencySymbol = currencySymbol,
        deliveredOrders = deliveredOrders,
        questClaims = questClaims.values.toList(),
        sessionClaims = sessionClaims,
        platformTimezone = platformTimezone,
        onBack = onBack,
        onOpenIncomeHistory = onOpenIncomeHistory,
        onOpenTripChat = { trip ->
            viewModel.openTripChat(
                orderId = trip.id,
                orderNumber = trip.displayOrderNumber,
                serviceType = trip.serviceType,
                customerName = trip.customerName.ifBlank { "Customer" },
                customerId = trip.customerId,
                merchantName = trip.merchantName,
                branchName = trip.branchName,
                branchCode = trip.branchCode,
                closedTimeMillis = trip.closedAtMillis
            )
        },
        onReportIssue = { trip ->
            viewModel.showToast("Incident report opened for ${trip.displayOrderNumber}")
        }
    )

    activeChatOrder?.let { chatOrder ->
        CustomerOrderChatDialog(
            order = chatOrder,
            messages = activeChatMessages,
            onSendMessage = { text, imageDataUri ->
                viewModel.sendChatMessage(chatOrder, text, imageDataUri)
            },
            onDismiss = { viewModel.closeChat() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EarningsContent(
    currencySymbol: String,
    deliveredOrders: List<Order>,
    questClaims: List<QuestClaim>,
    sessionClaims: List<SessionClaim>,
    platformTimezone: String = WalletLedgerHelper.DEFAULT_TIMEZONE_ID,
    onBack: () -> Unit,
    onOpenIncomeHistory: () -> Unit = {},
    onOpenTripChat: (TripRecord) -> Unit = {},
    onReportIssue: (TripRecord) -> Unit = {},
    nowMillis: Long = System.currentTimeMillis()
) {
    val todaySummary = remember(deliveredOrders, questClaims, sessionClaims, platformTimezone, nowMillis) {
        EarningsHistoryCalculator.computeTodaySummary(
            deliveredOrders = deliveredOrders,
            questClaims = questClaims,
            sessionClaims = sessionClaims,
            timezoneId = platformTimezone,
            nowMillis = nowMillis
        )
    }

    var selectedTripForDetail by remember { mutableStateOf<TripRecord?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Earnings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = Modifier.testTag("earnings_screen")
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .testTag("earnings_lazy_column"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Top "Total Income" Card (Today only, taps open Income History)
            item {
                TotalIncomeCard(
                    title = "Total Income",
                    subtitle = "Today",
                    totalIncome = todaySummary.totalIncome,
                    jobsCount = todaySummary.jobsCount,
                    currencySymbol = currencySymbol,
                    showHistoryChevron = true,
                    onClick = onOpenIncomeHistory,
                    modifier = Modifier.testTag("earnings_total_income_card")
                )
            }

            // 2. Earnings Breakdown (Delivery fees after commission, Tips with heart, Bonuses only if > 0)
            item {
                EarningsBreakdownCard(
                    summary = todaySummary,
                    currencySymbol = currencySymbol,
                    modifier = Modifier.testTag("earnings_breakdown_card")
                )
            }

            // 3. Today's Trip History Header
            item {
                Text(
                    text = "Trip History (${todaySummary.jobsCount})",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .testTag("earnings_trip_history_header")
                )
            }

            // 4. Today's Trips List or Friendly Empty State
            if (todaySummary.trips.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("earnings_today_empty")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Inbox,
                                contentDescription = "No trips today",
                                tint = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.size(38.dp)
                            )
                            Text(
                                text = "No trips completed today.",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Tap Total Income above to view past daily, weekly, and monthly earnings.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(todaySummary.trips, key = { it.id }) { trip ->
                    TripHistoryRowCard(
                        trip = trip,
                        currencySymbol = currencySymbol,
                        onClick = { selectedTripForDetail = trip }
                    )
                }
            }
        }
    }

    selectedTripForDetail?.let { trip ->
        TripDetailDialog(
            trip = trip,
            currencySymbol = currencySymbol,
            nowMillis = nowMillis,
            onOpenTripChat = {
                onOpenTripChat(trip)
                selectedTripForDetail = null
            },
            onReportIssue = {
                onReportIssue(trip)
                selectedTripForDetail = null
            },
            onDismiss = { selectedTripForDetail = null }
        )
    }
}

@Composable
internal fun TotalIncomeCard(
    title: String,
    subtitle: String,
    totalIncome: Double,
    jobsCount: Int,
    currencySymbol: String,
    showHistoryChevron: Boolean,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val cardModifier = modifier
        .fillMaxWidth()
        .then(
            if (onClick != null) {
                Modifier.clickable(onClick = onClick)
            } else {
                Modifier
            }
        )

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = BorderStroke(1.dp, DelivioYellow.copy(alpha = 0.45f)),
        modifier = cardModifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (subtitle.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = DelivioYellowLight
                        ) {
                            Text(
                                text = subtitle,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextOnYellow,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "$currencySymbol${String.format(Locale.US, "%.2f", totalIncome)}",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = DelivioYellowDark
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$jobsCount ${if (jobsCount == 1) "job" else "jobs"} completed",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (showHistoryChevron) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "History",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Open income history",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun EarningsBreakdownCard(
    summary: EarningsPeriodSummary,
    currencySymbol: String,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Earnings breakdown",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            EarningsBreakdownRow(
                label = "Delivery fees",
                amount = "$currencySymbol${String.format(Locale.US, "%.2f", summary.deliveryFees)}"
            )

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
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Tips",
                        tint = DelivioRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Tips",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "+$currencySymbol${String.format(Locale.US, "%.2f", summary.tips)}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (summary.tips > 0.0) DelivioGreenDark else MaterialTheme.colorScheme.onSurface
                )
            }

            if (summary.bonuses > 0.0) {
                EarningsBreakdownRow(
                    label = "Bonuses",
                    amount = "+$currencySymbol${String.format(Locale.US, "%.2f", summary.bonuses)}",
                    valueColor = DelivioGreenDark
                )
            }
        }
    }
}

@Composable
internal fun TripHistoryRowCard(
    trip: TripRecord,
    currencySymbol: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("earnings_job_row_${trip.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${trip.displayOrderNumber} • ${trip.timeText}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (trip.displayMerchantName.isNotBlank()) {
                        Text(
                            text = trip.displayMerchantName,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$currencySymbol${String.format(Locale.US, "%.2f", trip.deliveryIncomeAfterCommission)}",
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        color = DelivioYellowDark
                    )
                    if (trip.totalTips > 0.0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = "Tip",
                                tint = DelivioRed,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "+$currencySymbol${String.format(Locale.US, "%.2f", trip.totalTips)}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = DelivioGreenDark
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = trip.serviceType,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                PaymentBadgeContent(
                    paymentType = trip.paymentType,
                    providerLabel = trip.paymentProviderLabel,
                    amountToCollect = trip.resolvedCodCashCollected,
                    isRequestRider = trip.serviceType.lowercase(Locale.US).contains("request"),
                    currencySymbol = currencySymbol,
                    isLarge = false
                )
            }
        }
    }
}

@Composable
internal fun TripDetailDialog(
    trip: TripRecord,
    currencySymbol: String,
    nowMillis: Long,
    onOpenTripChat: () -> Unit,
    onReportIssue: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("earnings_trip_detail_dialog"),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Trip ${trip.displayOrderNumber}", fontWeight = FontWeight.Bold)
                Surface(shape = RoundedCornerShape(8.dp), color = DelivioYellowLight) {
                    Text(
                        text = trip.serviceType,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextOnYellow,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${trip.displayMerchantName} • ${trip.area}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "Completed ${trip.completedDateText} at ${trip.timeText}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider()

                EarningsBreakdownRow(
                    label = "Delivery fee",
                    amount = "$currencySymbol${String.format(Locale.US, "%.2f", trip.deliveryIncomeAfterCommission)}"
                )

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
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Tip",
                            tint = DelivioRed,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Tip",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "+$currencySymbol${String.format(Locale.US, "%.2f", trip.totalTips)}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (trip.totalTips > 0.0) DelivioGreenDark else MaterialTheme.colorScheme.onSurface
                    )
                }

                HorizontalDivider()
                EarningsBreakdownRow(
                    label = "Total",
                    amount = "$currencySymbol${String.format(Locale.US, "%.2f", trip.totalIncome)}",
                    isBold = true
                )

                Spacer(modifier = Modifier.height(4.dp))
                PaymentBadgeContent(
                    paymentType = trip.paymentType,
                    providerLabel = trip.paymentProviderLabel,
                    amountToCollect = trip.resolvedCodCashCollected,
                    isRequestRider = trip.serviceType.lowercase(Locale.US).contains("request"),
                    currencySymbol = currencySymbol,
                    isLarge = true,
                    modifier = Modifier.fillMaxWidth()
                )

                val canViewChat = OrderChatConstants.canViewTripChat(trip.serviceType, trip.closedAtMillis, nowMillis)
                if (canViewChat) {
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = onOpenTripChat,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("trip_history_view_chat_button")
                    ) {
                        Icon(Icons.Default.ChatBubble, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("View chat", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onReportIssue,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Text("Report issue", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "Delivio Driver Trip Receipt ${trip.displayOrderNumber}\n" +
                                        "Completed: ${trip.completedDateText} ${trip.timeText}\n" +
                                        "Delivery fee: $currencySymbol${String.format(Locale.US, "%.2f", trip.deliveryIncomeAfterCommission)}\n" +
                                        "Tip: +$currencySymbol${String.format(Locale.US, "%.2f", trip.totalTips)}\n" +
                                        "Total: $currencySymbol${String.format(Locale.US, "%.2f", trip.totalIncome)}"
                                )
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Trip Receipt"))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DelivioYellow, contentColor = TextOnYellow),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Text("Share receipt", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
internal fun EarningsBreakdownRow(
    label: String,
    amount: String,
    isBold: Boolean = false,
    valueColor: Color = Color.Unspecified
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = if (isBold) 14.sp else 13.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            color = if (isBold) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = amount,
            fontSize = if (isBold) 15.sp else 13.sp,
            fontWeight = if (isBold) FontWeight.Black else FontWeight.SemiBold,
            color = if (valueColor != Color.Unspecified) valueColor else MaterialTheme.colorScheme.onSurface
        )
    }
}
