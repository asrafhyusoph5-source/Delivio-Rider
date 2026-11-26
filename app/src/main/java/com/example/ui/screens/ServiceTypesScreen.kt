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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.constants.OrderStepConstants
import com.example.data.models.Rider
import com.example.ui.theme.*
import com.example.viewmodel.RiderViewModel
import java.util.Locale

@Composable
fun ServiceTypesScreen(
    viewModel: RiderViewModel,
    onBack: () -> Unit
) {
    val rider by viewModel.rider.collectAsState()
    val platformServices by viewModel.platformServices.collectAsState()
    val cityServices by viewModel.cityServices.collectAsState()
    val currentCityName by viewModel.currentCityName.collectAsState()
    val snackbarMessage by viewModel.snackbarMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearToast()
        }
    }

    val cityDisplayName = remember(currentCityName, rider?.cityId) {
        viewModel.getCityDisplayName()
    }

    ServiceTypesContent(
        rider = rider,
        isPlatformServiceOn = { svc ->
            val norm = OrderStepConstants.normalizeOrderServiceKey(svc)
            platformServices[norm] ?: platformServices[svc.trim().lowercase(Locale.US)] ?: true
        },
        isCityServiceOn = { svc ->
            val norm = OrderStepConstants.normalizeOrderServiceKey(svc)
            cityServices[norm] ?: cityServices[svc.trim().lowercase(Locale.US)] ?: true
        },
        cityDisplayName = cityDisplayName,
        snackbarHostState = snackbarHostState,
        onToggleService = { svcKey, enabled -> viewModel.toggleService(svcKey, enabled) },
        onBack = onBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceTypesContent(
    rider: Rider?,
    isPlatformServiceOn: (String) -> Boolean = { true },
    isCityServiceOn: (String) -> Boolean = { true },
    cityDisplayName: String = "",
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onToggleService: (String, Boolean) -> Unit,
    onBack: () -> Unit
) {
    val activeList = rider?.activeServices.orEmpty()
        .map { OrderStepConstants.normalizeOrderServiceKey(it) }
        .filter { it.isNotEmpty() }
        .toSet()
    val allowedList = rider?.allowedServices ?: OrderStepConstants.ALL_SERVICES
    val vehicleType = rider?.vehicle?.type
    val vehicleSupported = OrderStepConstants.getServicesForVehicle(vehicleType)
        .map { OrderStepConstants.normalizeOrderServiceKey(it) }
        .toSet()
    val vehicleDisplayName = OrderStepConstants.getVehicleDisplayName(vehicleType)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Service Types", fontWeight = FontWeight.Bold) },
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
        modifier = Modifier.testTag("service_types_screen")
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .testTag("service_types_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Choose which booking types you want to receive while online. Services locked by admin, city, platform, or vehicle type cannot be enabled.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }

            items(OrderStepConstants.SERVICE_DEFINITIONS, key = { it.key }) { def ->
                val svcKey = OrderStepConstants.normalizeOrderServiceKey(def.key)
                val isPlatformOn = isPlatformServiceOn(svcKey)
                val isCityOn = isCityServiceOn(svcKey)
                val isAllowed = OrderStepConstants.isServiceAllowedMatch(allowedList, svcKey)
                val isVehicleSupported = svcKey in vehicleSupported
                val lockReason = OrderStepConstants.getServiceLockReason(
                    isPlatformOn = isPlatformOn,
                    isCityOn = isCityOn,
                    isAllowed = isAllowed,
                    cityName = cityDisplayName,
                    isVehicleSupported = isVehicleSupported,
                    vehicleName = vehicleDisplayName
                )
                val isLocked = lockReason != null
                val isChecked = !isLocked && (svcKey in activeList)

                ServiceTypeCard(
                    serviceKey = def.key,
                    title = def.displayName,
                    description = def.subtitle,
                    icon = serviceTypeIcon(def.key),
                    isChecked = isChecked,
                    isLocked = isLocked,
                    lockReason = lockReason,
                    onCheckedChange = { enabled ->
                        if (!isLocked) {
                            onToggleService(def.key, enabled)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ServiceTypeCard(
    serviceKey: String,
    title: String,
    description: String,
    icon: ImageVector,
    isChecked: Boolean,
    isLocked: Boolean,
    lockReason: String?,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isLocked) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isLocked) 0.dp else 2.dp),
        border = if (!isLocked && isChecked) {
            BorderStroke(1.2.dp, DelivioYellow.copy(alpha = 0.7f))
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("service_type_card_$serviceKey")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (isLocked) MaterialTheme.colorScheme.surfaceVariant
                        else if (isChecked) DelivioYellowLight
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (isLocked) Color.Gray else DelivioYellowDark,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isLocked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
                if (isLocked && !lockReason.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Locked",
                            tint = DelivioRed,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = lockReason,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = DelivioRed
                        )
                    }
                }
            }

            Switch(
                checked = isChecked,
                onCheckedChange = onCheckedChange,
                enabled = !isLocked,
                modifier = Modifier.testTag("service_type_switch_$serviceKey")
            )
        }
    }
}

private fun serviceTypeIcon(serviceKey: String): ImageVector {
    return when (serviceKey.trim().lowercase(Locale.US)) {
        OrderStepConstants.SERVICE_FOOD -> Icons.Default.Restaurant
        OrderStepConstants.SERVICE_MART -> Icons.Default.Storefront
        OrderStepConstants.SERVICE_PARCEL -> Icons.Default.Inventory2
        OrderStepConstants.SERVICE_PABILI -> Icons.Default.ShoppingBag
        OrderStepConstants.SERVICE_REQUEST -> Icons.Default.DeliveryDining
        OrderStepConstants.SERVICE_TAXI -> Icons.Default.DirectionsCar
        else -> Icons.Default.DeliveryDining
    }
}
