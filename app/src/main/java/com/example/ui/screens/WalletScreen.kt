package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.WalletFilter
import com.example.data.models.WalletLedgerHelper
import com.example.data.models.WalletTransaction
import com.example.ui.theme.*
import com.example.viewmodel.RiderViewModel
import java.util.Locale
import kotlin.math.abs

@Composable
fun WalletScreen(
    viewModel: RiderViewModel,
    onBack: () -> Unit
) {
    val walletBal by viewModel.walletBalance.collectAsState()
    val cashOnHand by viewModel.cashOnHand.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val isSharedWallet by viewModel.isSharedWallet.collectAsState()
    val platformTimezone by viewModel.platformTimezone.collectAsState()
    val isLoading by viewModel.isWalletLoading.collectAsState()
    val isRefreshing by viewModel.isWalletRefreshing.collectAsState()
    val isSyncingWallet by viewModel.isWalletSyncing.collectAsState()
    val currencySymbol by viewModel.currencySymbol.collectAsState()
    val snackbarMessage by viewModel.snackbarMessage.collectAsState()
    val walletLoadError by viewModel.walletLoadError.collectAsState()
    val listenerError by viewModel.listenerError.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearToast()
        }
    }

    LaunchedEffect(walletLoadError ?: listenerError) {
        val err = walletLoadError ?: listenerError
        if (!err.isNullOrBlank()) {
            snackbarHostState.showSnackbar(err)
        }
    }

    WalletContent(
        walletBalance = walletBal,
        cashOnHand = cashOnHand,
        transactions = transactions,
        currencySymbol = currencySymbol,
        platformTimezone = platformTimezone,
        isSharedWallet = isSharedWallet,
        isLoading = isLoading,
        isRefreshing = isRefreshing,
        isSyncingWallet = isSyncingWallet,
        snackbarHostState = snackbarHostState,
        onRefresh = { viewModel.refreshWallet() },
        onBack = onBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletContent(
    walletBalance: Double,
    cashOnHand: Double,
    transactions: List<WalletTransaction>,
    currencySymbol: String,
    platformTimezone: String = WalletLedgerHelper.DEFAULT_TIMEZONE_ID,
    isSharedWallet: Boolean = false,
    isLoading: Boolean = false,
    isRefreshing: Boolean = false,
    isSyncingWallet: Boolean = false,
    nowMillis: Long = System.currentTimeMillis(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onRefresh: () -> Unit = {},
    onBack: () -> Unit
) {
    val dayGroups = remember(transactions, platformTimezone, nowMillis) {
        WalletLedgerHelper.groupTransactionsByDay(
            transactions = transactions,
            filter = WalletFilter.ALL,
            timezoneId = platformTimezone,
            nowMillis = nowMillis
        )
    }

    val isNegativeBalance = WalletLedgerHelper.isCodBlockedForWallet(walletBalance)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Delivio Wallet", fontWeight = FontWeight.Bold) },
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.testTag("wallet_screen")
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("wallet_lazy_column"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Top Balance Card ("Delivio Wallet" + large balance + COD status chip)
                item {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        border = BorderStroke(1.dp, DelivioYellow.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("wallet_balance_card")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(DelivioYellowLight),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AccountBalanceWallet,
                                            contentDescription = "Delivio Wallet",
                                            tint = DelivioYellowDark,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "Delivio Wallet",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (isSharedWallet) {
                                            Text(
                                                text = "Shared wallet",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = DelivioGreenDark
                                            )
                                        }
                                    }
                                }

                                if (isSyncingWallet) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = DelivioYellowLight,
                                        modifier = Modifier.testTag("wallet_syncing_chip")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(11.dp),
                                                strokeWidth = 1.8.dp,
                                                color = TextOnYellow
                                            )
                                            Text(
                                                text = "Syncing wallet...",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextOnYellow
                                            )
                                        }
                                    }
                                }
                            }

                            val formattedBal = if (walletBalance < 0.0) {
                                "-$currencySymbol${String.format(Locale.US, "%.2f", abs(walletBalance))}"
                            } else {
                                "$currencySymbol${String.format(Locale.US, "%.2f", walletBalance)}"
                            }
                            Text(
                                text = formattedBal,
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Black,
                                color = if (isNegativeBalance) DelivioRed else DelivioYellowDark,
                                modifier = Modifier.testTag("wallet_balance_amount")
                            )

                            // COD eligibility status chip
                            val statusBg = if (isNegativeBalance) {
                                DelivioRed.copy(alpha = 0.12f)
                            } else {
                                DelivioGreen.copy(alpha = 0.14f)
                            }
                            val statusColor = if (isNegativeBalance) DelivioRed else DelivioGreenDark
                            val statusIcon = if (isNegativeBalance) Icons.Default.Warning else Icons.Default.CheckCircle
                            val statusText = if (isNegativeBalance) {
                                "Negative balance - COD orders are blocked. Add cash through the admin to continue."
                            } else {
                                "Can receive COD orders"
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = statusBg,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("wallet_cod_status_chip")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = statusIcon,
                                        contentDescription = null,
                                        tint = statusColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = statusText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = statusColor,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Wallet History Header
                item {
                    Text(
                        text = "Wallet history",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                // 3. "Cash collected (on hand)" card ABOVE filter chips & transaction list
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cash_on_hand_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(DelivioYellowLight),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Payments,
                                        contentDescription = "Cash collected",
                                        tint = DelivioYellowDark,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Text(
                                    text = "Cash collected (on hand)",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = "$currencySymbol${String.format(Locale.US, "%.2f", cashOnHand)}",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.testTag("cash_on_hand_amount")
                            )
                        }
                    }
                }

                // 4. Loading Skeletons / Empty State / Day-grouped Transactions (full history, newest first)
                if (isLoading && transactions.isEmpty()) {
                    items(4) { idx ->
                        WalletTransactionSkeleton(modifier = Modifier.testTag("wallet_skeleton_$idx"))
                    }
                } else if (dayGroups.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .testTag("wallet_history_empty")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(DelivioYellowLight),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ReceiptLong,
                                        contentDescription = null,
                                        tint = DelivioYellowDark,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "No wallet activity yet",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Delivery earnings, COD deductions, and admin cash deposits will appear here.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    dayGroups.forEach { group ->
                        item(key = "day_header_${group.dayKey}") {
                            Text(
                                text = group.dayLabel,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(top = 6.dp, bottom = 2.dp)
                                    .testTag("wallet_day_header_${group.dayKey}")
                            )
                        }
                        items(group.transactions, key = { it.id }) { tx ->
                            WalletTransactionCard(
                                tx = tx,
                                currencySymbol = currencySymbol,
                                platformTimezone = platformTimezone
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WalletTransactionCard(
    tx: WalletTransaction,
    currencySymbol: String,
    platformTimezone: String
) {
    val isPositive = tx.isPositive
    val iconAndTint = resolveWalletTxIcon(tx)
    val displayTitle = tx.title.ifBlank { tx.note.ifBlank { tx.formattedType } }
    val formattedTime = if (tx.dateRaw > 0L) {
        WalletLedgerHelper.formatTransactionTime(tx.dateRaw, platformTimezone)
    } else {
        tx.dateText.ifBlank { "Recent" }
    }
    val signedVal = tx.effectiveSignedAmount
    val signPrefix = if (isPositive) "+" else "-"
    val amountColor = if (isPositive) DelivioGreenDark else DelivioRed
    val formattedAmount = "$signPrefix$currencySymbol${String.format(Locale.US, "%.2f", abs(signedVal))}"

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("wallet_transaction_${tx.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(iconAndTint.second.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconAndTint.first,
                    contentDescription = displayTitle,
                    tint = iconAndTint.second,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = displayTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formattedTime,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = formattedAmount,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    color = amountColor
                )
                tx.balanceAfter?.let { after ->
                    val afterText = if (after < 0.0) {
                        "Balance after: -$currencySymbol${String.format(Locale.US, "%.2f", abs(after))}"
                    } else {
                        "Balance after: $currencySymbol${String.format(Locale.US, "%.2f", after)}"
                    }
                    Text(
                        text = afterText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun resolveWalletTxIcon(tx: WalletTransaction): Pair<ImageVector, Color> {
    val normKind = tx.kind.trim().lowercase(Locale.US)
    val normType = tx.type.trim().lowercase(Locale.US)
    val normTitle = tx.title.trim().lowercase(Locale.US)
    return when {
        normKind == "delivery_earning" || normType == "delivery_credit" || normTitle.contains("delivery earning") ->
            Icons.Default.DeliveryDining to DelivioGreenDark
        normKind == "cod_collected" || normType == "cod_deduction" || normTitle.contains("cod collected") ->
            Icons.Default.Payments to DelivioRed
        normTitle.contains("merged") || normType.contains("merge") ->
            Icons.Default.MergeType to DelivioYellowDark
        tx.isPositive ->
            Icons.Default.AddCircle to DelivioGreenDark
        else ->
            Icons.Default.RemoveCircle to DelivioRed
    }
}

@Composable
private fun WalletTransactionSkeleton(modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.62f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.32f)
                        .height(11.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                )
            }
            Box(
                modifier = Modifier
                    .width(68.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    }
}
