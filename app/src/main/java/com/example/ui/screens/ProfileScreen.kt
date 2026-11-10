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
    onBack: () -> Unit,
    onNavigateToReviews: () -> Unit = {}
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
                                text = "Only admin can change name, email, photo, or driver ID.",
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
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable { onNavigateToReviews() }
                            ) {
                                Text("★ ${String.format(java.util.Locale.US, "%.1f", rider.rating)}", fontWeight = FontWeight.Black, fontSize = 16.sp, color = DelivioYellowDark)
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

            // Ratings and reviews Row
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToReviews() }
                        .testTag("profile_ratings_reviews_row")
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
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = DelivioYellowLight,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Ratings and reviews",
                                        tint = DelivioYellowDark,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Ratings and reviews",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                val reviewCount by viewModel.customerReviewCount.collectAsState()
                                Text(
                                    text = "★ ${String.format(java.util.Locale.US, "%.1f", rider.rating)} ($reviewCount reviews)",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Open Ratings and reviews",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
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
                            Icon(Icons.Default.DirectionsCar, contentDescription = "Vehicle", tint = DelivioYellowDark)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Registered Vehicle", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        ProfileRow("Vehicle Type", com.example.data.constants.OrderStepConstants.getVehicleDisplayName(rider.vehicle.type))
                        ProfileRow("Model", rider.vehicle.model.ifBlank { "Registered Model" })
                        if (rider.vehicle.color.isNotBlank()) {
                            ProfileRow("Color", rider.vehicle.color)
                        }
                        if (rider.vehicle.seats > 0) {
                            ProfileRow("Seats", "${rider.vehicle.seats} Seats")
                        }
                        ProfileRow("Plate Number", rider.vehicle.plateNumber.ifBlank { "On File" })
                        ProfileRow("Member Since", rider.memberSince.ifBlank { "Active Member" })
                    }
                }
            }

            // Driver Documents & Expiry Card
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth().testTag("profile_documents_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Badge, contentDescription = "Documents", tint = DelivioYellowDark)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Documents & Expiry", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }

                        val docs = rider.documents.ifEmpty {
                            listOf(
                                com.example.data.models.RiderDocument(type = "license"),
                                com.example.data.models.RiderDocument(type = "registration"),
                                com.example.data.models.RiderDocument(type = "insurance")
                            )
                        }

                        docs.forEach { doc ->
                            val alert = doc.getExpiryAlert()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = doc.typeDisplayName,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "Expires: ${doc.formattedExpiryDate}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (alert != null) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = when (alert.status) {
                                            com.example.data.models.DocumentAlertLevel.EXPIRED -> Color(0xFFFFCDD2)
                                            com.example.data.models.DocumentAlertLevel.WARNING_7_DAYS -> Color(0xFFFFE0B2)
                                            com.example.data.models.DocumentAlertLevel.NOTICE_14_DAYS -> Color(0xFFFFF9C4)
                                            com.example.data.models.DocumentAlertLevel.REMINDER_30_DAYS -> Color(0xFFB3E5FC)
                                        }
                                    ) {
                                        Text(
                                            text = when (alert.status) {
                                                com.example.data.models.DocumentAlertLevel.EXPIRED -> "Expired"
                                                com.example.data.models.DocumentAlertLevel.WARNING_7_DAYS -> "${alert.daysDiff}d left"
                                                com.example.data.models.DocumentAlertLevel.NOTICE_14_DAYS -> "${alert.daysDiff}d left"
                                                com.example.data.models.DocumentAlertLevel.REMINDER_30_DAYS -> "${alert.daysDiff}d left"
                                            },
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when (alert.status) {
                                                com.example.data.models.DocumentAlertLevel.EXPIRED -> Color(0xFFB71C1C)
                                                com.example.data.models.DocumentAlertLevel.WARNING_7_DAYS -> Color(0xFFBF360C)
                                                com.example.data.models.DocumentAlertLevel.NOTICE_14_DAYS -> Color(0xFFE65100)
                                                com.example.data.models.DocumentAlertLevel.REMINDER_30_DAYS -> Color(0xFF01579B)
                                            },
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFFE8F5E9)
                                    ) {
                                        Text(
                                            text = if (doc.expiresAtMillis > 0L) "Valid" else "On File",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF2E7D32),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
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
