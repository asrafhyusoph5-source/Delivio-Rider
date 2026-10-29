package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DelivioRed
import com.example.viewmodel.RiderViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: RiderViewModel,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val soundEnabled by viewModel.soundEnabled.collectAsState()
    val vibrationEnabled by viewModel.vibrationEnabled.collectAsState()
    val navApp by viewModel.navigationApp.collectAsState()

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
}
