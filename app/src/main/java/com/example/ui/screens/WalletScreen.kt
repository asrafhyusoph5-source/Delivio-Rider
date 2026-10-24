package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.WalletTransaction
import com.example.ui.theme.*
import com.example.viewmodel.RiderViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(
    viewModel: RiderViewModel,
    onBack: () -> Unit,
    onOpenEarnings: () -> Unit = {}
) {
    // 1. Live wallet balance from riders/{uid}.walletBalance (snapshot listener)
    val walletBal by viewModel.walletBalance.collectAsState()

    // 2. Separate line for earnings available to withdraw (do not add together)
    val earningsAvailable by viewModel.earningsAvailableToWithdraw.collectAsState()

    // 3. Real COD cash collected and Remittance due
    val totalCodCollected by viewModel.totalCodCollected.collectAsState()
    val cashRemitted by viewModel.cashRemitted.collectAsState()
    val remittanceDue by viewModel.remittanceDue.collectAsState()
    val cashOnHand by viewModel.cashOnHand.collectAsState()

    // 4. Wallet history query walletTransactions where riderId == current uid
    val transactions by viewModel.transactions.collectAsState()

    val isSharedWallet by viewModel.isSharedWallet.collectAsState()
    val currencySymbol by viewModel.currencySymbol.collectAsState()
    val snackbarMessage by viewModel.snackbarMessage.collectAsState()
    val listenerError by viewModel.listenerError.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Handle snackbar messages & real errors from reading
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearToast()
        }
    }

    LaunchedEffect(listenerError) {
        listenerError?.let { err ->
            snackbarHostState.showSnackbar(err)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Delivio Wallet", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Main Balance Card
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = if (isSharedWallet) "Shared wallet (linked to your customer account)" else "WALLET BALANCE",
                            fontSize = if (isSharedWallet) 12.sp else 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSharedWallet) DelivioGreenDark else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$currencySymbol${String.format("%.2f", walletBal)}",
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Black,
                            color = DelivioYellowDark
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Below it: separate line "Earnings available to withdraw" (do not add the two together)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = "Earnings available to withdraw: ",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$currencySymbol${String.format("%.2f", earningsAvailable)}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = DelivioGreenDark
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(14.dp))

                        // Cash on Hand & Remittance Due using real values only
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Cash collected (on hand)",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "$currencySymbol${String.format("%.2f", cashOnHand)}",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (totalCodCollected > 0 && cashRemitted > 0) {
                                    Text(
                                        text = "Total COD: $currencySymbol${String.format("%.2f", totalCodCollected)}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Remittance due",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "$currencySymbol${String.format("%.2f", remittanceDue)}",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (remittanceDue > 0) DelivioRed else MaterialTheme.colorScheme.onSurface
                                )
                                if (cashRemitted > 0) {
                                    Text(
                                        text = "Remitted: $currencySymbol${String.format("%.2f", cashRemitted)}",
                                        fontSize = 10.sp,
                                        color = DelivioGreenDark
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Cash Out and Remit Cash buttons (show "Coming soon" message)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Coming soon")
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = DelivioYellow,
                                    contentColor = TextOnYellow
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("cash_out_button")
                            ) {
                                Icon(Icons.Default.AccountBalance, contentDescription = "Cash Out", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Cash Out", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Coming soon")
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("remit_cash_button")
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = "Remit", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Remit Cash", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            // View Detailed Earnings button
            item {
                OutlinedButton(
                    onClick = onOpenEarnings,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.BarChart, contentDescription = "Earnings", tint = DelivioYellowDark)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("View Earnings & Trip History", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            // Wallet History Header
            item {
                Text(
                    text = "Wallet history",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // Empty state or list of wallet transactions
            if (transactions.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .testTag("wallet_history_empty")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ReceiptLong,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No wallet activity yet",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Top-ups, adjustments, and cash activity will appear here in real time.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(transactions, key = { it.id }) { tx ->
                    TransactionRow(tx = tx, currencySymbol = currencySymbol)
                }
            }
        }
    }
}

@Composable
private fun TransactionRow(tx: WalletTransaction, currencySymbol: String) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("wallet_transaction_${tx.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Type chip / label (cash_in, adjustment, etc.)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (tx.isPositive) DelivioGreen.copy(alpha = 0.12f) else DelivioRed.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = tx.type, // e.g. "cash_in", "adjustment"
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (tx.isPositive) DelivioGreenDark else DelivioRed,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                // Signed amount: green + or red -
                val sign = if (tx.isPositive) "+" else "-"
                val amountColor = if (tx.isPositive) DelivioGreenDark else DelivioRed
                Text(
                    text = "$sign$currencySymbol${String.format("%.2f", kotlin.math.abs(tx.amount))}",
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    color = amountColor
                )
            }

            // Note (if present)
            if (tx.note.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = tx.note,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Date and balanceAfter (if present)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tx.dateText,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (tx.balanceAfter != null) {
                    Text(
                        text = "Balance after: $currencySymbol${String.format("%.2f", tx.balanceAfter)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
