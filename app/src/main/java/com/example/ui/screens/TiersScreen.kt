package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.CoinTransaction
import com.example.data.models.TierLevel
import com.example.ui.theme.*
import com.example.viewmodel.RiderViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TiersScreen(
    viewModel: RiderViewModel,
    onBack: () -> Unit
) {
    val tierStatus by viewModel.tierStatus.collectAsState()

    // Next tier calculation
    val currentRides = tierStatus.currentMonthRides
    val (nextTierName, nextTierTarget, ridesLeft, nextProgress) = when {
        currentRides < TierLevel.BRONZE.monthlyRidesRequired -> {
            val needed = TierLevel.BRONZE.monthlyRidesRequired - currentRides
            val prog = currentRides.toFloat() / TierLevel.BRONZE.monthlyRidesRequired.toFloat()
            listOf("Bronze", TierLevel.BRONZE.monthlyRidesRequired.toString(), "$needed rides left", prog)
        }
        currentRides < TierLevel.SILVER.monthlyRidesRequired -> {
            val needed = TierLevel.SILVER.monthlyRidesRequired - currentRides
            val prog = (currentRides - TierLevel.BRONZE.monthlyRidesRequired).toFloat() /
                    (TierLevel.SILVER.monthlyRidesRequired - TierLevel.BRONZE.monthlyRidesRequired).toFloat()
            listOf("Silver", TierLevel.SILVER.monthlyRidesRequired.toString(), "$needed rides left", prog)
        }
        currentRides < TierLevel.GOLD.monthlyRidesRequired -> {
            val needed = TierLevel.GOLD.monthlyRidesRequired - currentRides
            val prog = (currentRides - TierLevel.SILVER.monthlyRidesRequired).toFloat() /
                    (TierLevel.GOLD.monthlyRidesRequired - TierLevel.SILVER.monthlyRidesRequired).toFloat()
            listOf("Gold", TierLevel.GOLD.monthlyRidesRequired.toString(), "$needed rides left", prog)
        }
        currentRides < TierLevel.PLATINUM.monthlyRidesRequired -> {
            val needed = TierLevel.PLATINUM.monthlyRidesRequired - currentRides
            val prog = (currentRides - TierLevel.GOLD.monthlyRidesRequired).toFloat() /
                    (TierLevel.PLATINUM.monthlyRidesRequired - TierLevel.GOLD.monthlyRidesRequired).toFloat()
            listOf("Platinum", TierLevel.PLATINUM.monthlyRidesRequired.toString(), "$needed rides left", prog)
        }
        else -> {
            listOf("Platinum (Max Tier)", "1,000", "Goal achieved!", 1f)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rider Tier & Rewards", fontWeight = FontWeight.Bold) },
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
        modifier = Modifier.testTag("tiers_screen")
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Current Standing Card
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "CURRENT MONTH STATUS",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (tierStatus.currentTier != null) {
                                        "${tierStatus.currentTier?.title?.uppercase()} RIDER"
                                    } else {
                                        "NO TIER YET"
                                    },
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (tierStatus.currentTier != null) DelivioYellowDark else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Coin Balance Pill
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = DelivioYellowLight,
                                border = androidx.compose.foundation.BorderStroke(1.dp, DelivioYellow)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MonetizationOn,
                                        contentDescription = "Coins",
                                        tint = DelivioYellowDark,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${tierStatus.currentCoins} Coins",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 13.sp,
                                        color = TextOnYellow
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Monthly Rides Completed", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${tierStatus.currentMonthRides} rides", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Next Tier Goal ($nextTierName)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    "$nextTierTarget ($ridesLeft)",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (ridesLeft == "Goal achieved!") DelivioGreenDark else DelivioYellowDark
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Dynamic Progress indicator towards next tier
                        LinearProgressIndicator(
                            progress = { (nextProgress as Float).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = DelivioYellow,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${tierStatus.daysRemainingInMonth} days remaining in this month cycle",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Monthly Tier Levels & Coin Rewards",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // The 4 Specific Tiers: Bronze (500), Silver (650), Gold (850), Platinum (1000)
            items(TierLevel.values()) { tier ->
                TierCard(
                    tier = tier,
                    currentRides = tierStatus.currentMonthRides,
                    isClaimed = tierStatus.claimedTierRewards.contains(tier.name)
                )
            }

            // Coin History
            item {
                Text(
                    text = "Coin History",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            if (tierStatus.coinHistory.isEmpty()) {
                item {
                    Text(
                        text = "No coin transactions yet this month.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(tierStatus.coinHistory) { item ->
                    CoinTransactionRow(item = item)
                }
            }

            // Coin Redemption Info
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = "Coins Info", tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Coins are automatically awarded once per calendar month as soon as you hit each milestone (Bronze: 200, Silver: 500, Gold: 700, Platinum: 1,000). Coins can be cashed out to your Delivio Wallet or used for fuel vouchers.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // Celebration popup on reaching a tier
    tierStatus.newlyAchievedTier?.let { achievedTier ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissTierCelebration() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Stars, contentDescription = "Reward", tint = DelivioYellowDark)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tier Milestone Unlocked!", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "🎉 Congratulations on reaching the ${achievedTier.title} Tier this month!",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    Text(
                        "You completed ${achievedTier.monthlyRidesRequired}+ rides and have been rewarded with ${achievedTier.rewardCoins} Delivio Coins.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = DelivioYellowLight,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.MonetizationOn, contentDescription = "Coins", tint = DelivioYellowDark)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "+${achievedTier.rewardCoins} Coins Claimed",
                                fontWeight = FontWeight.Bold,
                                color = TextOnYellow
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.dismissTierCelebration() },
                    colors = ButtonDefaults.buttonColors(containerColor = DelivioYellow, contentColor = TextOnYellow)
                ) {
                    Text("Awesome!", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun TierCard(
    tier: TierLevel,
    currentRides: Int,
    isClaimed: Boolean
) {
    val isReached = currentRides >= tier.monthlyRidesRequired
    val progress = (currentRides.toFloat() / tier.monthlyRidesRequired.toFloat()).coerceIn(0f, 1f)
    val badgeColor = Color(tier.badgeColorHex)

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isReached) 3.dp else 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isReached) 2.dp else 1.dp,
                color = if (isReached) badgeColor else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(18.dp)
            )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(badgeColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.WorkspacePremium,
                            contentDescription = tier.title,
                            tint = badgeColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = tier.title,
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${tier.monthlyRidesRequired} rides a month",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Status chip: CLAIMED / REACHED / LOCKED
                when {
                    isClaimed -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = DelivioGreen.copy(alpha = 0.15f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = "Claimed", tint = DelivioGreenDark, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("CLAIMED (+${tier.rewardCoins})", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = DelivioGreenDark)
                            }
                        }
                    }
                    isReached -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = DelivioYellowLight
                        ) {
                            Text(
                                "REACHED (+${tier.rewardCoins})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = TextOnYellow,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    else -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = "Locked", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("LOCKED", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = tier.perkDescription,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (isReached) DelivioGreen else DelivioYellow,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${minOf(currentRides, tier.monthlyRidesRequired)} / ${tier.monthlyRidesRequired} rides",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isReached) {
                    Text(
                        text = "Milestone Met",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = DelivioGreenDark
                    )
                } else {
                    Text(
                        text = "${tier.monthlyRidesRequired - currentRides} rides needed",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = DelivioRed
                    )
                }
            }
        }
    }
}

@Composable
private fun CoinTransactionRow(item: CoinTransaction) {
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
                        .background(DelivioYellowLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MonetizationOn,
                        contentDescription = "Coin",
                        tint = DelivioYellowDark,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(item.title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(item.timestampText, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                text = "+${item.amount} coins",
                fontWeight = FontWeight.Black,
                fontSize = 13.sp,
                color = DelivioYellowDark
            )
        }
    }
}
