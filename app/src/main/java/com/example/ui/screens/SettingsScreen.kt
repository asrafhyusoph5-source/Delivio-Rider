package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.DelivioRed
import com.example.util.CrashReportEntry
import com.example.util.CrashReportStore
import com.example.viewmodel.RiderViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: RiderViewModel,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val soundEnabled by viewModel.soundEnabled.collectAsState()
    val vibrationEnabled by viewModel.vibrationEnabled.collectAsState()
    val navApp by viewModel.navigationApp.collectAsState()

    var crashReports by remember { mutableStateOf(CrashReportStore.getReports(context)) }
    var showCrashReportsDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings and privacy", fontWeight = FontWeight.Bold) },
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
        modifier = Modifier.testTag("settings_screen")
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Appearance & Theme Settings
            item {
                Text("Appearance & Display", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Dark Mode", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Applies dark map style and battery-efficient dark surfaces", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = isDarkMode,
                                onCheckedChange = { viewModel.toggleTheme(it) },
                                modifier = Modifier.testTag("dark_mode_switch")
                            )
                        }
                    }
                }
            }

            // Navigation Settings
            item {
                Text("Dispatch & Navigation", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Preferred Navigation App", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Selected: $navApp", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = {
                                val next = if (navApp == "Google Maps") "Waze" else "Google Maps"
                                viewModel.setNavigationApp(next)
                            }) {
                                Text("Switch")
                            }
                        }
                    }
                }
            }

            // Audio & Haptics
            item {
                Text("Alerts & Sound", fontWeight = FontWeight.Bold, fontSize = 15.sp)
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
                            Text("Dispatch Chime & Audio Alerts", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Switch(checked = soundEnabled, onCheckedChange = { viewModel.setSoundEnabled(it) })
                        }
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Haptic Vibration", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Switch(checked = vibrationEnabled, onCheckedChange = { viewModel.setVibrationEnabled(it) })
                        }
                    }
                }
            }

            // Help section (Crash reports row hidden when none exist)
            if (crashReports.isNotEmpty()) {
                item {
                    Text("Help", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 56.dp)
                                .clickable {
                                    crashReports = CrashReportStore.getReports(context)
                                    showCrashReportsDialog = true
                                }
                                .padding(16.dp)
                                .testTag("crash_reports_row"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BugReport,
                                    contentDescription = "Crash reports",
                                    tint = DelivioRed,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Crash reports", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(
                                        "${crashReports.size} recent ${if (crashReports.size == 1) "report" else "reports"} saved",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Open crash reports",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Privacy & Account
            item {
                Text("Account & System", fontWeight = FontWeight.Bold, fontSize = 15.sp)
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
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("App Version", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Delivio Driver v1.4.2 (Production)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Active Region", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Puerto Princesa, Palawan (PH)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }

                        HorizontalDivider()

                        // Log Out Button
                        OutlinedButton(
                            onClick = {
                                viewModel.performLogout {
                                    onLogout()
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = DelivioRed),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("logout_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, "Log Out", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Log Out of Account", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showCrashReportsDialog) {
        CrashReportsDialog(
            reports = crashReports,
            onCopy = { report ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(
                    ClipData.newPlainText("Crash Report", CrashReportStore.formatFullReport(report))
                )
                Toast.makeText(context, "Report copied", Toast.LENGTH_SHORT).show()
            },
            onShare = { report ->
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Delivio Driver Crash Report")
                    putExtra(Intent.EXTRA_TEXT, CrashReportStore.formatFullReport(report))
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share crash report"))
            },
            onClearAll = {
                CrashReportStore.clearReports(context)
                crashReports = emptyList()
                showCrashReportsDialog = false
            },
            onDismiss = { showCrashReportsDialog = false }
        )
    }
}

@Composable
private fun CrashReportsDialog(
    reports: List<CrashReportEntry>,
    onCopy: (CrashReportEntry) -> Unit,
    onShare: (CrashReportEntry) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy h:mm:ss a", Locale.US) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.85f)
                .testTag("crash_reports_dialog"),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Crash reports (${reports.size})",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = onClearAll, modifier = Modifier.testTag("clear_crash_reports_button")) {
                        Text("Clear all", color = DelivioRed)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(reports) { entry ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = if (entry.isNonFatal) MaterialTheme.colorScheme.tertiaryContainer else DelivioRed.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = if (entry.isNonFatal) "NON-FATAL" else "CRASH",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (entry.isNonFatal) MaterialTheme.colorScheme.onTertiaryContainer else DelivioRed,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(
                                        text = dateFormat.format(Date(entry.timestampMillis)),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Text(
                                    text = if (entry.exceptionMessage.isNotBlank()) "${entry.exceptionType}: ${entry.exceptionMessage}" else entry.exceptionType,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                if (!entry.documentPath.isNullOrBlank()) {
                                    Text(
                                        text = "Document: ${entry.documentPath}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Text(
                                    text = "v${entry.appVersion} | Thread: ${entry.threadName}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 160.dp)
                                        .background(MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp))
                                        .verticalScroll(rememberScrollState())
                                        .horizontalScroll(rememberScrollState())
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = CrashReportStore.firstLines(entry.stackTrace, 40),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        lineHeight = 14.sp
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { onCopy(entry) },
                                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                                    ) {
                                        Text("Copy report", fontSize = 12.sp)
                                    }
                                    OutlinedButton(
                                        onClick = { onShare(entry) },
                                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                                    ) {
                                        Text("Share report", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("close_crash_reports_button")
                ) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

