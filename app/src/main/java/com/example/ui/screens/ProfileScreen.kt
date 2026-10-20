package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.constants.OrderStepConstants
import com.example.ui.theme.*
import com.example.viewmodel.RiderViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: RiderViewModel,
    onBack: () -> Unit
) {
    val riderState by viewModel.rider.collectAsState()
    val rider = riderState

    var showEditPhoneDialog by remember { mutableStateOf(false) }
    var editPhone by remember(rider?.phone) { mutableStateOf(rider?.phone ?: "") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Profile", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showEditPhoneDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Phone", tint = DelivioYellowDark)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        modifier = Modifier.testTag("profile_screen")
    ) { padding ->
        if (rider == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DelivioYellowDark)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Profile Card Header
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Profile Picture (Coil AsyncImage, fallback circle with first letter, never emoji)
                        Box(
                            modifier = Modifier
                                .size(92.dp)
                                .clip(CircleShape)
                                .background(DelivioYellow),
                            contentAlignment = Alignment.Center
                        ) {
                            if (rider.photoUrl.isNotBlank()) {
                                AsyncImage(
                                    model = rider.photoUrl,
                                    contentDescription = "Profile Picture",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(92.dp).clip(CircleShape)
                                )
                            } else {
                                val initial = rider.name.firstOrNull()?.uppercase() ?: "R"
                                Text(
                                    text = initial,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 38.sp,
                                    color = TextOnYellow
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Name with lock icon
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = rider.name,
                                fontWeight = FontWeight.Black,
                                fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.Default.Lock, contentDescription = "Locked", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        }

                        // Rider ID with lock icon
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("ID: ${rider.riderCode}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.Lock, contentDescription = "Locked", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
                            }
                        }

                        // Email (Locked)
                        Text(
                            text = rider.email,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        // Editable Phone number
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .clickable { showEditPhoneDialog = true }
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = "Phone", tint = DelivioGreenDark, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (rider.phone.isNotBlank()) rider.phone else "Tap to add phone",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text(
                                text = "Only admin can change name, email, photo, or rider ID.",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(12.dp))

                        // Rating & Member stats
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("★ ${String.format("%.1f", rider.rating)}", fontWeight = FontWeight.Black, fontSize = 16.sp, color = DelivioYellowDark)
                                Text("Rating", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${rider.totalTrips}", fontWeight = FontWeight.Black, fontSize = 16.sp)
                                Text("Completed Trips", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(rider.cityId.replace("_", " ").replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Black, fontSize = 14.sp)
                                Text("Assigned City", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // Vehicle Information Card
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.TwoWheeler, contentDescription = "Vehicle", tint = DelivioYellowDark)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Registered Vehicle", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        ProfileRow("Vehicle Type", rider.vehicle.type.ifBlank { "Motorcycle" })
                        ProfileRow("Model", rider.vehicle.model.ifBlank { "Registered Model" })
                        ProfileRow("Plate Number", rider.vehicle.plateNumber.ifBlank { "On File" })
                        ProfileRow("Member Since", rider.memberSince.ifBlank { "Active Member" })
                    }
                }
            }

            // Active Services Summary
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Active Dispatch Services", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OrderStepConstants.ALL_SERVICES.forEach { svcKey ->
                                val isActive = rider.activeServices.contains(svcKey)
                                val isAllowed = rider.allowedServices.contains(svcKey)
                                val label = OrderStepConstants.getServiceBadge(svcKey)
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isActive && isAllowed) DelivioYellowLight else MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isActive && isAllowed) TextOnYellow else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Edit Phone Dialog
    if (showEditPhoneDialog) {
        AlertDialog(
            onDismissRequest = { showEditPhoneDialog = false },
            title = { Text("Update Phone Number", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter your current reachable mobile phone number:", fontSize = 13.sp)
                    OutlinedTextField(
                        value = editPhone,
                        onValueChange = { editPhone = it },
                        label = { Text("Phone Number") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editPhone.isNotBlank()) {
                            viewModel.updatePhone(editPhone.trim())
                            showEditPhoneDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DelivioYellow, contentColor = TextOnYellow)
                ) {
                    Text("Save Phone", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditPhoneDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ProfileRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
