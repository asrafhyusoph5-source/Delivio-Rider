package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.constants.OrderStepConstants
import com.example.data.models.Rider
import com.example.ui.theme.*

@Composable
fun DrawerMenuPanel(
    isOpen: Boolean,
    rider: Rider?,
    unreadInboxCount: Int,
    isServiceOn: (String) -> Boolean = { true },
    cityName: String = "",
    onToggleService: (String, Boolean) -> Unit,
    onClose: () -> Unit,
    onNavigate: (String) -> Unit
) {
    var serviceTypeExpanded by remember { mutableStateOf(false) }
    var resourcesExpanded by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn(tween(250)),
        exit = fadeOut(tween(250))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("drawer_menu_scrim")
        ) {
            // Scrim (covers whole screen, 25% clickable on right)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { onClose() }
            )

            // Drawer content sliding from left, exactly 75% width
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.75f)
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable(enabled = false) {}
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .testTag("drawer_menu_content")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Header with live "Hi, <first name>"
                    val firstName = rider?.name?.trim()?.split(" ")?.firstOrNull()?.ifBlank { "Driver" } ?: "Driver"

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .background(DelivioYellow)
                            .padding(20.dp)
                    ) {
                        // Decorative rounded background circles
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .align(Alignment.TopEnd)
                                .offset(x = 30.dp, y = (-20).dp)
                                .clip(CircleShape)
                                .background(DelivioYellowDark.copy(alpha = 0.45f))
                        )
                        Box(
                            modifier = Modifier
                                .size(70.dp)
                                .align(Alignment.BottomEnd)
                                .offset(x = 10.dp, y = 20.dp)
                                .clip(CircleShape)
                                .background(DelivioYellowLight.copy(alpha = 0.35f))
                        )

                        // Greeting content
                        Column(
                            modifier = Modifier.align(Alignment.BottomStart)
                        ) {
                            Text(
                                text = "Hi, $firstName 👋",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = TextOnYellow
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val ratingStr = if (rider != null) String.format("%.1f", rider.rating) else "5.0"
                            val vehicleLabel = OrderStepConstants.getVehicleDisplayName(rider?.vehicle?.type)
                            val modelStr = rider?.vehicle?.model?.ifBlank { vehicleLabel } ?: vehicleLabel
                            Text(
                                text = "★ $ratingStr • $modelStr",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextOnYellow.copy(alpha = 0.85f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 2x2 Grid of rounded tiles (Inbox, Schedule, Wallet, Earnings)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            DrawerTile(
                                icon = Icons.Default.Mail,
                                label = "Inbox",
                                badgeText = if (unreadInboxCount > 0) "+$unreadInboxCount" else null,
                                onClick = {
                                    onClose()
                                    onNavigate("inbox")
                                },
                                modifier = Modifier.weight(1f)
                            )
                            DrawerTile(
                                icon = Icons.Default.Schedule,
                                label = "Schedule",
                                onClick = {
                                    onClose()
                                    onNavigate("schedule")
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            DrawerTile(
                                icon = Icons.Default.AccountBalanceWallet,
                                label = "Wallet",
                                onClick = {
                                    onClose()
                                    onNavigate("wallet")
                                },
                                modifier = Modifier.weight(1f)
                            )
                            DrawerTile(
                                icon = Icons.Default.BarChart,
                                label = "Earnings",
                                onClick = {
                                    onClose()
                                    onNavigate("earnings")
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(modifier = Modifier.height(8.dp))

                    // Menu List
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    ) {
                        // My profile
                        DrawerMenuItem(
                            icon = Icons.Default.Person,
                            title = "My profile",
                            onClick = {
                                onClose()
                                onNavigate("profile")
                            }
                        )

                        // Service Type: only services in SERVICES_BY_VEHICLE for riders.vehicleType appear
                        DrawerMenuItem(
                            icon = Icons.Default.Tune,
                            title = "Service Type",
                            hasChevron = true,
                            isExpanded = serviceTypeExpanded,
                            onClick = { serviceTypeExpanded = !serviceTypeExpanded }
                        )

                        AnimatedVisibility(visible = serviceTypeExpanded) {
                            val activeList = rider?.activeServices ?: emptyList()
                            val allowedList = rider?.allowedServices ?: OrderStepConstants.ALL_SERVICES
                            val supportedServices = OrderStepConstants.getServicesForVehicle(rider?.vehicle?.type)
                            val cityLabel = if (cityName.isNotBlank()) cityName else "City"

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)
                            ) {
                                // 1. Delivio Food (food)
                                if (supportedServices.contains(OrderStepConstants.SERVICE_FOOD)) {
                                    val isCityOn = isServiceOn(OrderStepConstants.SERVICE_FOOD)
                                    val isAllowed = allowedList.contains(OrderStepConstants.SERVICE_FOOD)
                                    val isDisabled = !isCityOn || !isAllowed
                                    val reason = if (!isCityOn) "Disabled by admin in $cityLabel" else "Disabled by admin"
                                    DrawerServiceTypeToggleItem(
                                        title = "Delivio Food",
                                        subtitle = "Restaurant & cooked food deliveries",
                                        icon = Icons.Default.Restaurant,
                                        isChecked = if (isDisabled) false else activeList.contains(OrderStepConstants.SERVICE_FOOD),
                                        isDisabledByAdmin = isDisabled,
                                        disabledReason = reason,
                                        onCheckedChange = { onToggleService(OrderStepConstants.SERVICE_FOOD, it) }
                                    )
                                }

                                // 2. Delivio Instant (convenience)
                                if (supportedServices.contains(OrderStepConstants.SERVICE_INSTANT)) {
                                    val isCityOn = isServiceOn(OrderStepConstants.SERVICE_INSTANT)
                                    val isAllowed = allowedList.contains(OrderStepConstants.SERVICE_INSTANT)
                                    val isDisabled = !isCityOn || !isAllowed
                                    val reason = if (!isCityOn) "Disabled by admin in $cityLabel" else "Disabled by admin"
                                    DrawerServiceTypeToggleItem(
                                        title = "Delivio Instant",
                                        subtitle = "Express point-to-point courier",
                                        icon = Icons.Default.Bolt,
                                        isChecked = if (isDisabled) false else activeList.contains(OrderStepConstants.SERVICE_INSTANT),
                                        isDisabledByAdmin = isDisabled,
                                        disabledReason = reason,
                                        onCheckedChange = { onToggleService(OrderStepConstants.SERVICE_INSTANT, it) }
                                    )
                                }

                                // 3. Delivio Parcel (parcel)
                                if (supportedServices.contains(OrderStepConstants.SERVICE_PARCEL)) {
                                    val isCityOn = isServiceOn(OrderStepConstants.SERVICE_PARCEL)
                                    val isAllowed = allowedList.contains(OrderStepConstants.SERVICE_PARCEL)
                                    val isDisabled = !isCityOn || !isAllowed
                                    val reason = if (!isCityOn) "Disabled by admin in $cityLabel" else "Disabled by admin"
                                    DrawerServiceTypeToggleItem(
                                        title = "Delivio Parcel",
                                        subtitle = "Boxed items, packages & documents",
                                        icon = Icons.Default.Inventory2,
                                        isChecked = if (isDisabled) false else activeList.contains(OrderStepConstants.SERVICE_PARCEL),
                                        isDisabledByAdmin = isDisabled,
                                        disabledReason = reason,
                                        onCheckedChange = { onToggleService(OrderStepConstants.SERVICE_PARCEL, it) }
                                    )
                                }

                                // 4. Delivio Pabili (pabili)
                                if (supportedServices.contains(OrderStepConstants.SERVICE_PABILI)) {
                                    val isCityOn = isServiceOn(OrderStepConstants.SERVICE_PABILI)
                                    val isAllowed = allowedList.contains(OrderStepConstants.SERVICE_PABILI)
                                    val isDisabled = !isCityOn || !isAllowed
                                    val reason = if (!isCityOn) "Disabled by admin in $cityLabel" else "Disabled by admin"
                                    DrawerServiceTypeToggleItem(
                                        title = "Delivio Pabili",
                                        subtitle = "On-demand grocery & item purchasing",
                                        icon = Icons.Default.ShoppingBag,
                                        isChecked = if (isDisabled) false else activeList.contains(OrderStepConstants.SERVICE_PABILI),
                                        isDisabledByAdmin = isDisabled,
                                        disabledReason = reason,
                                        onCheckedChange = { onToggleService(OrderStepConstants.SERVICE_PABILI, it) }
                                    )
                                }

                                // 5. Delivio Request (request_rider)
                                if (supportedServices.contains(OrderStepConstants.SERVICE_REQUEST)) {
                                    val isCityOn = isServiceOn(OrderStepConstants.SERVICE_REQUEST)
                                    val isAllowed = allowedList.contains(OrderStepConstants.SERVICE_REQUEST)
                                    val isDisabled = !isCityOn || !isAllowed
                                    val reason = if (!isCityOn) "Disabled by admin in $cityLabel" else "Disabled by admin"
                                    DrawerServiceTypeToggleItem(
                                        title = "Delivio Request",
                                        subtitle = "Merchant driver requests",
                                        icon = Icons.Default.DeliveryDining,
                                        isChecked = if (isDisabled) false else activeList.contains(OrderStepConstants.SERVICE_REQUEST),
                                        isDisabledByAdmin = isDisabled,
                                        disabledReason = reason,
                                        onCheckedChange = { onToggleService(OrderStepConstants.SERVICE_REQUEST, it) }
                                    )
                                }

                                // 6. Delivio Taxi (taxi)
                                if (supportedServices.contains(OrderStepConstants.SERVICE_TAXI)) {
                                    val isCityOn = isServiceOn(OrderStepConstants.SERVICE_TAXI)
                                    val isAllowed = allowedList.contains(OrderStepConstants.SERVICE_TAXI)
                                    val isDisabled = !isCityOn || !isAllowed
                                    val reason = if (!isCityOn) "Disabled by admin in $cityLabel" else "Disabled by admin"
                                    DrawerServiceTypeToggleItem(
                                        title = "Delivio Taxi",
                                        subtitle = "Passenger ride-hailing & city taxi",
                                        icon = Icons.Default.DirectionsCar,
                                        isChecked = if (isDisabled) false else activeList.contains(OrderStepConstants.SERVICE_TAXI),
                                        isDisabledByAdmin = isDisabled,
                                        disabledReason = reason,
                                        onCheckedChange = { onToggleService(OrderStepConstants.SERVICE_TAXI, it) }
                                    )
                                }
                            }
                        }

                        // Resources (Expandable)
                        DrawerMenuItem(
                            icon = Icons.Default.MenuBook,
                            title = "Resources",
                            hasChevron = true,
                            isExpanded = resourcesExpanded,
                            onClick = { resourcesExpanded = !resourcesExpanded }
                        )

                        AnimatedVisibility(visible = resourcesExpanded) {
                            Column(modifier = Modifier.padding(start = 24.dp)) {
                                DrawerSubMenuItem(
                                    title = "Tiers",
                                    trailingIcon = Icons.Default.WorkspacePremium
                                ) {
                                    onClose()
                                    onNavigate("resources/tiers")
                                }
                                DrawerSubMenuItem("Activity insights") {
                                    onClose()
                                    onNavigate("resources/insights")
                                }
                                DrawerSubMenuItem("Ratings & reviews") {
                                    onClose()
                                    onNavigate("resources/reviews")
                                }
                                DrawerSubMenuItem("Driver safety") {
                                    onClose()
                                    onNavigate("resources/safety")
                                }
                                DrawerSubMenuItem(
                                    title = "Tutorials",
                                    trailingIcon = Icons.AutoMirrored.Filled.OpenInNew
                                ) {
                                    onClose()
                                    onNavigate("resources/tutorials")
                                }
                            }
                        }

                        // Settings and privacy
                        DrawerMenuItem(
                            icon = Icons.Default.Settings,
                            title = "Settings and privacy",
                            onClick = {
                                onClose()
                                onNavigate("settings")
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun DrawerTile(
    icon: ImageVector,
    label: String,
    badgeText: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
            .height(76.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier.align(Alignment.BottomStart)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = DelivioYellowDark,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (badgeText != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DelivioRed,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Text(
                        text = badgeText,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerMenuItem(
    icon: ImageVector,
    title: String,
    hasChevron: Boolean = false,
    isExpanded: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = DelivioYellowDark,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (hasChevron) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun DrawerSubMenuItem(
    title: String,
    trailingIcon: ImageVector? = null,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (trailingIcon != null) {
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun DrawerServiceTypeToggleItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isChecked: Boolean,
    isDisabledByAdmin: Boolean = false,
    disabledReason: String? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (isDisabledByAdmin) Color.Gray else DelivioYellowDark,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDisabledByAdmin) Color.Gray else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isDisabledByAdmin) (disabledReason ?: "Disabled by admin") else subtitle,
                        fontSize = 10.sp,
                        fontWeight = if (isDisabledByAdmin) FontWeight.Bold else FontWeight.Normal,
                        color = if (isDisabledByAdmin) DelivioRed else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = if (isDisabledByAdmin) false else isChecked,
                onCheckedChange = if (isDisabledByAdmin) { {} } else onCheckedChange,
                enabled = !isDisabledByAdmin,
                modifier = Modifier.scale(0.8f)
            )
        }
    }
}
