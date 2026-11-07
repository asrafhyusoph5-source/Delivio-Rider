package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.models.Quest
import com.example.data.models.QuestGoal
import com.example.data.models.QuestProgress
import com.example.data.models.formatGoalDescription
import com.example.ui.theme.*
import com.example.viewmodel.RiderViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestsScreen(
    viewModel: RiderViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quests", fontWeight = FontWeight.Bold) },
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
        modifier = modifier.testTag("quests_screen")
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            QuestsTabContent(viewModel = viewModel)
        }
    }
}

/**
 * Tabbed Quests Content that can be embedded in ResourcesScreen (above Tiers)
 * or in standalone QuestsScreen.
 */
@Composable
fun QuestsTabContent(
    viewModel: RiderViewModel,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: In progress, 1: Upcoming, 2: History
    val questProgressList by viewModel.questProgressList.collectAsState()
    val currencySymbol by viewModel.currencySymbol.collectAsState()

    var selectedQuestDetail by remember { mutableStateOf<QuestProgress?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refreshQuests()
    }

    val now = System.currentTimeMillis()

    val inProgressQuests = remember(questProgressList, now) {
        questProgressList.filter { p ->
            val q = p.quest
            q.startAtMillis <= now && now <= q.endAtMillis
        }
    }

    val upcomingQuests = remember(questProgressList, now) {
        questProgressList.filter { p ->
            p.quest.startAtMillis > now
        }
    }

    val historyQuests = remember(questProgressList, now) {
        questProgressList.filter { p ->
            p.quest.endAtMillis in 1L until now
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Segmented Tabs: In progress, Upcoming, History
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = DelivioYellowDark,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("In progress", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        if (inProgressQuests.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = CircleShape,
                                color = DelivioYellowLight,
                                modifier = Modifier.size(20.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        "${inProgressQuests.size}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        color = TextOnYellow
                                    )
                                }
                            }
                        }
                    }
                }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Upcoming", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        if (upcomingQuests.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(20.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        "${upcomingQuests.size}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = {
                    Text("History", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            )
        }

        val activeList = when (selectedTab) {
            0 -> inProgressQuests
            1 -> upcomingQuests
            else -> historyQuests
        }

        if (activeList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(54.dp)
                    )
                    Text(
                        text = when (selectedTab) {
                            0 -> "No active quests right now"
                            1 -> "No upcoming quests scheduled"
                            else -> "No quest history yet"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = when (selectedTab) {
                            0 -> "Check back during peak hours for new delivery challenges."
                            1 -> "New weekend and holiday quests will appear here."
                            else -> "Completed and expired quests will be archived here."
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(activeList, key = { it.quest.id }) { item ->
                    QuestCard(
                        progress = item,
                        currencySymbol = currencySymbol,
                        tabType = selectedTab,
                        onClick = { selectedQuestDetail = item }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    // Quest Detail Dialog/Modal
    selectedQuestDetail?.let { detailProgress ->
        QuestDetailDialog(
            progress = detailProgress,
            currencySymbol = currencySymbol,
            onDismiss = { selectedQuestDetail = null }
        )
    }
}

/**
 * Card representing a Quest in the List
 * Designed in the style of reference photos using Delivio colors.
 */
@Composable
fun QuestCard(
    progress: QuestProgress,
    currencySymbol: String,
    tabType: Int, // 0: In progress, 1: Upcoming, 2: History
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val quest = progress.quest

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(
            0.8.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Title and Chevron
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = quest.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = quest.formattedPeriod(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Details",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Subtitle / Next goal row
            when (tabType) {
                0 -> {
                    // In progress
                    val nextGoal = progress.nextGoal
                    val nextGoalText = if (nextGoal != null) {
                        "Next goal ($currencySymbol${String.format(Locale.US, "%.2f", nextGoal.reward)})"
                    } else if (progress.isCompleted) {
                        "All goals completed! ($currencySymbol${String.format(Locale.US, "%.2f", progress.currentReward)})"
                    } else {
                        "Top reward ($currencySymbol${String.format(Locale.US, "%.2f", quest.topReward)})"
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = nextGoalText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (progress.isCompleted) DelivioGreenDark else DelivioYellowDark
                        )

                        val progressStr = if (quest.metric.lowercase().contains("earning")) {
                            "$currencySymbol${String.format(Locale.US, "%.0f", progress.currentValue)} / $currencySymbol${String.format(Locale.US, "%.0f", quest.topTarget)}"
                        } else {
                            "${progress.currentValue.toInt()} / ${quest.topTarget.toInt()} deliveries"
                        }
                        Text(
                            text = progressStr,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Progress bar with milestone dots and trophy
                    QuestProgressBar(
                        progress = progress,
                        currencySymbol = currencySymbol
                    )
                }

                1 -> {
                    // Upcoming
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Top reward: $currencySymbol${String.format(Locale.US, "%.2f", quest.topReward)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = DelivioYellowDark
                        )
                        Text(
                            text = quest.timeLeftText(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    QuestProgressBar(
                        progress = progress.copy(currentValue = 0.0),
                        currencySymbol = currencySymbol
                    )
                }

                2 -> {
                    // History: Show payout claim status
                    QuestProgressBar(
                        progress = progress,
                        currencySymbol = currencySymbol
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Payout claim status banner
                    HistoryStatusBadge(
                        progress = progress,
                        currencySymbol = currencySymbol
                    )
                }
            }
        }
    }
}

/**
 * Status banner in History tab
 * Handles:
 * - "Reward pending - credited after verification"
 * - "Credited ₱X"
 * - "Not achieved"
 * - "Rejected" with note
 */
@Composable
fun HistoryStatusBadge(
    progress: QuestProgress,
    currencySymbol: String,
    modifier: Modifier = Modifier
) {
    val claim = progress.claim
    val levelReached = claim?.levelReached ?: progress.levelReachedIndex
    val rewardAmount = claim?.rewardAmount ?: progress.currentReward
    val status = claim?.status ?: "Pending"

    val isNotAchieved = levelReached == -1 || rewardAmount <= 0.0

    val (bgColor, textColor, icon, label) = when {
        isNotAchieved -> {
            Tuple4(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.onSurfaceVariant,
                Icons.Default.Cancel,
                "Not achieved"
            )
        }
        status.equals("Credited", ignoreCase = true) -> {
            Tuple4(
                DelivioGreen.copy(alpha = 0.15f),
                DelivioGreenDark,
                Icons.Default.CheckCircle,
                "Credited $currencySymbol${String.format(Locale.US, "%.2f", rewardAmount)}"
            )
        }
        status.equals("Rejected", ignoreCase = true) -> {
            val noteText = if (claim?.note?.isNotBlank() == true) " - ${claim.note}" else ""
            Tuple4(
                DelivioRed.copy(alpha = 0.15f),
                DelivioRed,
                Icons.Default.Error,
                "Rejected$noteText"
            )
        }
        else -> {
            // Pending
            Tuple4(
                DelivioYellow.copy(alpha = 0.2f),
                TextOnYellow,
                Icons.Default.Schedule,
                "Reward pending - credited after verification"
            )
        }
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
    }
}

private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

/**
 * Progress bar with milestone dots for every goal and a trophy at the end with the top reward below.
 */
@Composable
fun QuestProgressBar(
    progress: QuestProgress,
    currencySymbol: String,
    modifier: Modifier = Modifier
) {
    val quest = progress.quest
    val sortedGoals = quest.sortedGoals
    val topTarget = quest.topTarget
    val currentValue = progress.currentValue

    val animatedFraction by animateFloatAsState(
        targetValue = if (topTarget > 0.0) (currentValue / topTarget).coerceIn(0.0, 1.0).toFloat() else 0f,
        label = "quest_progress"
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track Box containing bar and dots
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .height(28.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            val totalWidthPx = constraints.maxWidth.toFloat()

            // Background Track
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            )

            // Active Progress Fill
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedFraction)
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(DelivioGreen)
            )

            // Milestone Dots for every goal
            sortedGoals.forEachIndexed { idx, goal ->
                val fraction = if (topTarget > 0.0) (goal.target / topTarget).coerceIn(0.0, 1.0).toFloat() else 0f
                val isReached = idx <= progress.levelReachedIndex && progress.levelReachedIndex >= 0

                val dotOffsetDp = (totalWidthPx * fraction) / (LocalDensity ?: 1f)

                Box(
                    modifier = Modifier
                        .offset(x = (totalWidthPx * fraction / 2.7f).coerceIn(0f, totalWidthPx).dp - 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(if (isReached) DelivioGreenDark else MaterialTheme.colorScheme.surface)
                            .border(
                                width = 1.5.dp,
                                color = if (isReached) DelivioGreenDark else MaterialTheme.colorScheme.outlineVariant,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isReached) {
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Trophy at the end with top reward below
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.EmojiEvents,
                contentDescription = "Top Reward Trophy",
                tint = Color(0xFFFFB300),
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "$currencySymbol${String.format(Locale.US, "%.0f", quest.topReward)}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = DelivioYellowDark
            )
        }
    }
}

private val LocalDensity: Float?
    @Composable get() = androidx.compose.ui.platform.LocalDensity.current.density

/**
 * Quest Detail Screen / Modal Dialog
 * Covers:
 * - Title, period, time left, description
 * - One line per goal ("Complete 18 deliveries for ₱75 with a 95% acceptance rate and 0 no-shows", adapted to conditions)
 * - "Show more / Show less" toggle
 * - Total deliveries (or earnings) and Total rewards
 * - Progress bar with milestone dots and trophy
 * - "Next goal ₱X: Complete N more deliveries (or earn ₱N more) to achieve it"
 * - "View all goals" breakdown
 */
@Composable
fun QuestDetailDialog(
    progress: QuestProgress,
    currencySymbol: String,
    onDismiss: () -> Unit
) {
    val quest = progress.quest
    val sortedGoals = quest.sortedGoals
    var showAllGoals by remember { mutableStateOf(false) }
    var showMoreGoalsText by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f)
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header: Title & Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = quest.title,
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = quest.formattedPeriod(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Time left chip
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = DelivioYellowLight,
                    modifier = Modifier.wrapContentWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = TextOnYellow,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = quest.timeLeftText(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextOnYellow
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Description
                if (quest.description.isNotBlank()) {
                    Text(
                        text = quest.description,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(14.dp))

                // One line per goal ("Complete 18 deliveries for ₱75 with a 95% acceptance rate and 0 no-shows")
                Text(
                    text = "Goal Requirements",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))

                val displayedGoals = if (showMoreGoalsText) sortedGoals else sortedGoals.take(2)
                displayedGoals.forEachIndexed { idx, goal ->
                    val isMet = idx <= progress.levelReachedIndex && progress.levelReachedIndex >= 0
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = if (isMet) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (isMet) DelivioGreenDark else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .size(16.dp)
                                .padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formatGoalDescription(goal, quest, currencySymbol),
                            fontSize = 12.sp,
                            fontWeight = if (isMet) FontWeight.Bold else FontWeight.Normal,
                            color = if (isMet) DelivioGreenDark else MaterialTheme.colorScheme.onSurface,
                            lineHeight = 17.sp
                        )
                    }
                }

                if (sortedGoals.size > 2) {
                    Text(
                        text = if (showMoreGoalsText) "Show less" else "Show more",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = DelivioYellowDark,
                        modifier = Modifier
                            .clickable { showMoreGoalsText = !showMoreGoalsText }
                            .padding(vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Stats: Total deliveries (or earnings) & Total rewards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Deliveries or Earnings
                    val metricTitle = if (quest.metric.lowercase().contains("earning")) "Total earnings" else "Total deliveries"
                    val metricValue = if (quest.metric.lowercase().contains("earning")) {
                        "$currencySymbol${String.format(Locale.US, "%.2f", progress.currentValue)}"
                    } else {
                        "${progress.currentValue.toInt()}"
                    }

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(metricTitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(metricValue, fontSize = 18.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    // Total rewards
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DelivioYellowLight),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Total rewards", fontSize = 11.sp, color = TextOnYellow.copy(alpha = 0.7f))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "$currencySymbol${String.format(Locale.US, "%.2f", progress.currentReward)}",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = TextOnYellow
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Progress Bar with milestone dots and trophy
                QuestProgressBar(
                    progress = progress,
                    currencySymbol = currencySymbol
                )

                Spacer(modifier = Modifier.height(14.dp))

                // "Next goal ₱X: Complete N more deliveries (or earn ₱N more) to achieve it"
                val nextGoal = progress.nextGoal
                if (nextGoal != null) {
                    val needed = (nextGoal.target - progress.currentValue).coerceAtLeast(0.0)
                    val actionLabel = if (quest.metric.lowercase().contains("earning")) {
                        "earn $currencySymbol${String.format(Locale.US, "%.2f", needed)} more"
                    } else {
                        "Complete ${needed.toInt()} more ${if (needed.toInt() == 1) "delivery" else "deliveries"}"
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Flag,
                                contentDescription = null,
                                tint = DelivioYellowDark,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Next goal $currencySymbol${String.format(Locale.US, "%.2f", nextGoal.reward)}: $actionLabel to achieve it",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                } else if (progress.isCompleted) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = DelivioGreen.copy(alpha = 0.15f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = DelivioGreenDark,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "All goals achieved! Top reward of $currencySymbol${String.format(Locale.US, "%.2f", progress.currentReward)} unlocked.",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = DelivioGreenDark
                            )
                        }
                    }
                }

                // If finished, show History Status Card
                if (quest.endAtMillis in 1L until System.currentTimeMillis()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    HistoryStatusBadge(
                        progress = progress,
                        currencySymbol = currencySymbol
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // "View all goals" expandable section
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAllGoals = !showAllGoals }
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "View all goals (${sortedGoals.size} levels)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = DelivioYellowDark
                    )
                    Icon(
                        imageVector = if (showAllGoals) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = DelivioYellowDark
                    )
                }

                AnimatedVisibility(visible = showAllGoals) {
                    Column(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        sortedGoals.forEachIndexed { idx, goal ->
                            val isReached = idx <= progress.levelReachedIndex && progress.levelReachedIndex >= 0
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isReached) DelivioGreen.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                ),
                                border = if (isReached) androidx.compose.foundation.BorderStroke(1.dp, DelivioGreenDark) else null,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Level ${goal.level}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            if (isReached) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("✓ Achieved", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = DelivioGreenDark)
                                            }
                                        }
                                        Text(
                                            formatGoalDescription(goal, quest, currencySymbol),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        "$currencySymbol${String.format(Locale.US, "%.2f", goal.reward)}",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 13.sp,
                                        color = if (isReached) DelivioGreenDark else DelivioYellowDark
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Dismiss Button
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = DelivioYellow, contentColor = TextOnYellow),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
