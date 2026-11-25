package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.*
import com.example.ui.components.CustomerOrderChatDialog
import com.example.ui.theme.*
import com.example.viewmodel.RiderViewModel
import java.util.Locale

@Composable
fun IncomeHistoryScreen(
    viewModel: RiderViewModel,
    onBack: () -> Unit
) {
    val currencySymbol by viewModel.currencySymbol.collectAsState()
    val deliveredOrders by viewModel.deliveredOrders.collectAsState()
    val questClaims by viewModel.questClaims.collectAsState()
    val sessionClaims by viewModel.sessionClaims.collectAsState()
    val platformTimezone by viewModel.platformTimezone.collectAsState()
    val activeChatOrder by viewModel.activeChatOrder.collectAsState()
    val activeChatMessages by viewModel.activeChatMessages.collectAsState()

    IncomeHistoryContent(
        currencySymbol = currencySymbol,
        deliveredOrders = deliveredOrders,
        questClaims = questClaims.values.toList(),
        sessionClaims = sessionClaims,
        platformTimezone = platformTimezone,
        onBack = onBack,
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
fun IncomeHistoryContent(
    currencySymbol: String,
    deliveredOrders: List<Order>,
    questClaims: List<QuestClaim>,
    sessionClaims: List<SessionClaim>,
    platformTimezone: String = WalletLedgerHelper.DEFAULT_TIMEZONE_ID,
    onBack: () -> Unit,
    onOpenTripChat: (TripRecord) -> Unit = {},
    onReportIssue: (TripRecord) -> Unit = {},
    nowMillis: Long = System.currentTimeMillis()
) {
    var selectedTab by remember { mutableStateOf(IncomeHistoryTab.DAILY) }
    var selectedPastRow by remember { mutableStateOf<IncomeHistoryRow?>(null) }
    var selectedTripForDetail by remember { mutableStateOf<TripRecord?>(null) }

    BackHandler(enabled = selectedPastRow != null) {
        selectedPastRow = null
    }

    val historyRows = remember(selectedTab, deliveredOrders, questClaims, sessionClaims, platformTimezone, nowMillis) {
        EarningsHistoryCalculator.buildHistoryRows(
            tab = selectedTab,
            deliveredOrders = deliveredOrders,
            questClaims = questClaims,
            sessionClaims = sessionClaims,
            timezoneId = platformTimezone,
            nowMillis = nowMillis
        )
    }

    val rowsByYear = remember(historyRows) {
        historyRows.groupBy { it.year }.entries.sortedByDescending { it.key }
    }

    val activeDetailRow = selectedPastRow

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = activeDetailRow?.title ?: "Income history",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (selectedPastRow != null) {
                                selectedPastRow = null
                            } else {
                                onBack()
                            }
                        },
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
        modifier = Modifier.testTag("income_history_screen")
    ) { padding ->
        if (activeDetailRow != null) {
            val detailSummary = activeDetailRow.summary
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background)
                    .testTag("income_history_detail_list"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    TotalIncomeCard(
                        title = "Total Income",
                        subtitle = activeDetailRow.subtitle,
                        totalIncome = detailSummary.totalIncome,
                        jobsCount = detailSummary.jobsCount,
                        currencySymbol = currencySymbol,
                        showHistoryChevron = false,
                        modifier = Modifier.testTag("history_detail_total_card")
                    )
                }

                item {
                    EarningsBreakdownCard(
                        summary = detailSummary,
                        currencySymbol = currencySymbol,
                        modifier = Modifier.testTag("history_detail_breakdown_card")
                    )
                }

                item {
                    Text(
                        text = "Trip History (${detailSummary.jobsCount})",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                if (detailSummary.trips.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Inbox,
                                    contentDescription = "No trips",
                                    tint = MaterialTheme.colorScheme.outlineVariant,
                                    modifier = Modifier.size(34.dp)
                                )
                                Text(
                                    text = "No completed trips for this period.",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                } else {
                    items(detailSummary.trips, key = { it.id }) { trip ->
                        TripHistoryRowCard(
                            trip = trip,
                            currencySymbol = currencySymbol,
                            onClick = { selectedTripForDetail = trip }
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Black pill tabs: Daily / Weekly / Monthly
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IncomeHistoryTab.entries.forEach { tab ->
                        val isSelected = selectedTab == tab
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = if (isSelected) Color(0xFF111827) else MaterialTheme.colorScheme.surface,
                            border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .clickable { selectedTab = tab }
                                .testTag("income_history_tab_${tab.name.lowercase(Locale.US)}")
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tab.label,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("income_history_list"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowsByYear.forEach { (year, rowsInYear) ->
                        item(key = "year_header_$year") {
                            Text(
                                text = "$year",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(top = 6.dp, bottom = 2.dp)
                                    .testTag("income_history_year_$year")
                            )
                        }

                        items(rowsInYear, key = { it.id }) { row ->
                            IncomeHistoryPeriodRowCard(
                                row = row,
                                currencySymbol = currencySymbol,
                                onClick = {
                                    if (!row.isInProgress) {
                                        selectedPastRow = row
                                    }
                                }
                            )
                        }
                    }
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
private fun IncomeHistoryPeriodRowCard(
    row: IncomeHistoryRow,
    currencySymbol: String,
    onClick: () -> Unit
) {
    val cardModifier = Modifier
        .fillMaxWidth()
        .then(
            if (!row.isInProgress) {
                Modifier.clickable(onClick = onClick)
            } else {
                Modifier
            }
        )
        .testTag("income_history_row_${row.id}")

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = cardModifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 64.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = row.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (row.subtitle.isNotBlank()) {
                    Text(
                        text = row.subtitle,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (row.isInProgress) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DelivioYellowLight,
                    modifier = Modifier.testTag("in_progress_chip_${row.id}")
                ) {
                    Text(
                        text = "IN PROGRESS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = TextOnYellow,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "$currencySymbol${String.format(Locale.US, "%.2f", row.summary.totalIncome)}",
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        color = DelivioGreenDark
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Open ${row.title} details",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
