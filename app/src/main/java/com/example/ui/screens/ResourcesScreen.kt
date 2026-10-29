package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.viewmodel.RiderViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResourcesScreen(
    initialSubpage: String = "insights",
    viewModel: RiderViewModel,
    onBack: () -> Unit,
    onOpenEarnings: () -> Unit = {}
) {
    var selectedTab by remember {
        mutableIntStateOf(
            when (initialSubpage) {
                "tiers" -> 0
                "insights" -> 1
                "safety" -> 2
                "tutorials" -> 3
                else -> 0
            }
        )
    }

    val currencySymbol by viewModel.currencySymbol.collectAsState()
    val earningsSummary by viewModel.earningsSummary.collectAsState()
    val tierStatus by viewModel.tierStatus.collectAsState()
    val activeCall by viewModel.activeCall.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Driver Resources", fontWeight = FontWeight.Bold) },
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
        modifier = Modifier.testTag("resources_screen")
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Tiers", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Insights", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Safety & SOS", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text("Tutorials", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                )
            }

            when (selectedTab) {
                0 -> ResourcesTiersTab(tierStatus = tierStatus)
                1 -> ActivityInsightsTab(earningsSummary = earningsSummary, currencySymbol = currencySymbol, onOpenEarnings = onOpenEarnings)
                2 -> RiderSafetyTab(
                    onSosClick = {
                        viewModel.openDialer("911", "Emergency Services (911)")
                        viewModel.showToast("Emergency SOS triggered. Connecting to 911...")
                    },
                    onShareTrip = {
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, "I am currently delivering orders in Puerto Princesa. Track my live location here: https://delivio.ph/track/rider-test")
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share Live Trip"))
                    }
                )
                3 -> TutorialsTab(onOpenLink = { url ->
                    try {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(browserIntent)
                    } catch (e: Exception) {
                        viewModel.showToast("Opening tutorial guide...")
                    }
                })
            }
        }
    }

    // Safe in-app call overlay
    activeCall?.let { callInfo ->
        com.example.ui.components.InAppCallDialog(
            callInfo = callInfo,
            onEndCall = { viewModel.endActiveCall() },
            onOpenSystemDialer = { phone -> viewModel.launchExternalDialer(phone) }
        )
    }
}

@Composable
private fun ActivityInsightsTab(
    earningsSummary: com.example.data.models.EarningSummary,
    currencySymbol: String,
    onOpenEarnings: () -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Daily Performance Bar Chart Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Daily Earnings (Past 7 Days)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        TextButton(onClick = onOpenEarnings) {
                            Text("View All ›", fontWeight = FontWeight.Bold, color = DelivioYellowDark)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    // Visual Bar Chart
                    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                    val amounts = listOf(1150, 1420, 1200, 1680, 1950, 2400, 1480)
                    val maxAmount = amounts.maxOrNull() ?: 2400

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        days.zip(amounts).forEach { (day, amt) ->
                            val heightFraction = amt.toFloat() / maxAmount.toFloat()
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("$currencySymbol${amt / 1000}k", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .width(18.dp)
                                        .fillMaxHeight(heightFraction)
                                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                        .background(if (day == "Sun") DelivioYellow else MaterialTheme.colorScheme.surfaceVariant)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(day, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        // Best Operating Hours
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, "Hours", tint = DelivioYellowDark)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Best Earning Windows in Puerto Princesa", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• Lunch Peak (11:00 AM - 01:30 PM): ₱280 - ₱350/hr average", fontSize = 12.sp)
                    Text("• Dinner Rush (05:30 PM - 08:30 PM): ₱320 - ₱420/hr average", fontSize = 12.sp)
                    Text("• Late Night Baywalk (09:00 PM - 11:00 PM): High tips", fontSize = 12.sp)
                }
            }
        }

        // Recent Reviews
        item {
            Text("Customer Reviews & Feedback", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("★★★★★ • \"Food arrived steaming hot, very polite driver!\"", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("Maria S. • Order #DEL-9842", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun RiderSafetyTab(onSosClick: () -> Unit, onShareTrip: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Red SOS Emergency Button
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.WarningAmber, "SOS", tint = DelivioRed, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Emergency SOS Dispatch", fontWeight = FontWeight.Black, fontSize = 18.sp, color = DelivioRed)
                    Text("Instantly dials emergency response & broadcasts live coordinates to Delivio safety fleet.", fontSize = 12.sp, color = Color(0xFFB71C1C), modifier = Modifier.padding(vertical = 4.dp))

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onSosClick,
                        colors = ButtonDefaults.buttonColors(containerColor = DelivioRed, contentColor = Color.White),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("sos_emergency_button")
                    ) {
                        Text("TAP FOR EMERGENCY (SOS)", fontWeight = FontWeight.Black, fontSize = 15.sp)
                    }
                }
            }
        }

        // Share Trip
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Share Live Trip with Family", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Let your trusted contacts monitor your GPS path while delivering.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = onShareTrip,
                        colors = ButtonDefaults.buttonColors(containerColor = DelivioYellow, contentColor = TextOnYellow),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Share", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Safety Guidelines
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Palawan Driver Safety Rules", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("1. Always strap your full-face helmet securely or wear your seatbelt.", fontSize = 12.sp)
                    Text("2. Keep headlights on at all times on the National Highway.", fontSize = 12.sp)
                    Text("3. Never count large cash sums in open public streets.", fontSize = 12.sp)
                    Text("4. Keep customer items and food insulated and secured safely.", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun TutorialsTab(onOpenLink: (String) -> Unit) {
    val tutorials = listOf(
        Pair("Maximizing Earnings in Puerto Princesa", "Learn how to position near top restaurant clusters in San Miguel and Rizal Ave."),
        Pair("Managing Cash-on-Delivery Discrepancies", "Step-by-step guide on customer change and remittance kiosk deposits."),
        Pair("Handling Inclement Weather & Monsoons", "Equipment tips, waterproof phone mounts, and defensive driving habits."),
        Pair("Driver Tier Progression: Unlocking Gold Status", "Maintain 95%+ acceptance to unlock priority batching and fuel discounts.")
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(tutorials) { (title, desc) ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { onOpenLink("https://delivio.ph/academy") }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, "Open Tutorial", tint = DelivioYellowDark)
                    }
                }
            }
        }
    }
}

@Composable
private fun ResourcesTiersTab(tierStatus: com.example.data.models.RiderTierStatus) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    val tierTitle = if (tierStatus.currentTier != null) "${tierStatus.currentTier?.title?.uppercase()} DRIVER" else "NO TIER YET"
                    val ridesSubtext = when {
                        tierStatus.currentMonthRides < 500 -> "${tierStatus.currentMonthRides} rides completed this month • ${500 - tierStatus.currentMonthRides} rides to Bronze"
                        tierStatus.currentMonthRides < 650 -> "${tierStatus.currentMonthRides} rides completed this month • ${650 - tierStatus.currentMonthRides} rides to Silver"
                        tierStatus.currentMonthRides < 850 -> "${tierStatus.currentMonthRides} rides completed this month • ${850 - tierStatus.currentMonthRides} rides to Gold"
                        tierStatus.currentMonthRides < 1000 -> "${tierStatus.currentMonthRides} rides completed this month • ${1000 - tierStatus.currentMonthRides} rides to Platinum"
                        else -> "${tierStatus.currentMonthRides} rides completed this month • Platinum Achieved!"
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Current Standing", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Surface(shape = RoundedCornerShape(8.dp), color = if (tierStatus.currentTier != null) DelivioYellow else MaterialTheme.colorScheme.surfaceVariant) {
                            Text(tierTitle, fontWeight = FontWeight.Black, fontSize = 11.sp, color = if (tierStatus.currentTier != null) TextOnYellow else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("${tierStatus.currentCoins} Coins Earned", fontSize = 26.sp, fontWeight = FontWeight.Black, color = DelivioYellowDark)
                    Text(ridesSubtext, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            Text("Monthly Tiers & Rewards", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        items(com.example.data.models.TierLevel.values()) { tier ->
            val isAchieved = tierStatus.currentMonthRides >= tier.monthlyRidesRequired
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isAchieved) 2.dp else 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(tier.badgeColorHex).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.WorkspacePremium, contentDescription = tier.title, tint = Color(tier.badgeColorHex), modifier = Modifier.size(22.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(tier.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("${tier.monthlyRidesRequired} rides a month", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = DelivioYellowLight
                        ) {
                            Text("+${tier.rewardCoins} coins", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = TextOnYellow, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(tier.perkDescription, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

