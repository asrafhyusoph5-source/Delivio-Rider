package com.example.ui.screens

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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.WalletTransaction
import com.example.ui.theme.*
import com.example.viewmodel.RiderViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(
    viewModel: RiderViewModel,
    onBack: () -> Unit,
    onOpenEarnings: () -> Unit = {}
) {
    val walletBal by viewModel.walletBalance.collectAsState()
    val cashOnHand by viewModel.cashOnHand.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val currencySymbol by viewModel.currencySymbol.collectAsState()

    var showCashOutDialog by remember { mutableStateOf(false) }
    var cashOutMethod by remember { mutableStateOf("GCash") }
    var cashOutAmountText by remember { mutableStateOf("500") }

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
                            text = "AVAILABLE WITHDRAWABLE BALANCE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$currencySymbol${String.format("%.2f", walletBal)}",
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Black,
                            color = DelivioYellowDark
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Cash on Hand vs Earnings Breakdown
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Cash Collected (On-Hand)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("$currencySymbol${String.format("%.2f", cashOnHand)}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Remittance Due", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("$currencySymbol${String.format("%.2f", (cashOnHand * 0.85).coerceAtLeast(0.0))}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DelivioRed)
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { showCashOutDialog = true },
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
                                onClick = { viewModel.showToast("Remittance deposited at 7-Eleven / GCash partner kiosk.") },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
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

            // Linked Payout Accounts
            item {
                Text("Linked Payout Accounts", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PhoneAndroid, "GCash", tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("GCash Account (Primary)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("0917 •••• 1234 • Test Rider", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Text("Default", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = DelivioYellow)
                        }

                        HorizontalDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AccountBalance, "Maya", tint = MaterialTheme.colorScheme.secondary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Maya Wallet", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("0918 •••• 5678 • Test Rider", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Text("Connected", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Payout Policy Note
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = "Info", tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Instant cash-outs to GCash and Maya are free for up to 3 transactions per week. Minimum payout is ₱100.00.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Transaction History Header
            item {
                Text(
                    text = "Transaction History",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            items(transactions) { tx ->
                TransactionRow(tx = tx, currencySymbol = currencySymbol)
            }
        }
    }

    if (showCashOutDialog) {
        AlertDialog(
            onDismissRequest = { showCashOutDialog = false },
            title = { Text("Instant Cash Out", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Select Destination:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("GCash", "Maya", "BDO", "BPI").forEach { method ->
                            FilterChip(
                                selected = cashOutMethod == method,
                                onClick = { cashOutMethod = method },
                                label = { Text(method) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = cashOutAmountText,
                        onValueChange = { cashOutAmountText = it },
                        label = { Text("Amount ($currencySymbol)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = cashOutAmountText.toDoubleOrNull() ?: 0.0
                        if (viewModel.cashOut(amount, cashOutMethod)) {
                            showCashOutDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DelivioYellow, contentColor = TextOnYellow)
                ) {
                    Text("Confirm Payout", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCashOutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun TransactionRow(tx: WalletTransaction, currencySymbol: String) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (tx.isCredit) DelivioGreen.copy(alpha = 0.15f) else DelivioRed.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (tx.isCredit) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                        contentDescription = tx.category,
                        tint = if (tx.isCredit) DelivioGreen else DelivioRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(tx.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("${tx.dateText} • ${tx.referenceNo}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Text(
                text = "${if (tx.isCredit) "+" else "-"}$currencySymbol${String.format("%.2f", tx.amount)}",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                color = if (tx.isCredit) DelivioGreenDark else DelivioRed
            )
        }
    }
}
