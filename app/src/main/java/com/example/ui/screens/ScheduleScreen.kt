package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.example.data.models.AvailableSessionItem
import com.example.data.models.BookedSessionItem
import com.example.data.models.BookedSessionStatus
import com.example.data.models.ScheduleFilterResult
import com.example.data.models.ScheduleRules
import com.example.data.models.ScheduleSession
import com.example.ui.theme.DelivioGreen
import com.example.ui.theme.DelivioOrange
import com.example.ui.theme.DelivioRed
import com.example.ui.theme.DelivioYellow
import com.example.ui.theme.DelivioYellowDark
import com.example.ui.theme.TextOnYellow
import com.example.viewmodel.RiderViewModel

@Composable
fun ScheduleScreen(
    viewModel: RiderViewModel,
    onBack: () -> Unit
) {
    val filterResult by viewModel.scheduleFilterResult.collectAsState()
    val isLoading by viewModel.isScheduleLoading.collectAsState()
    val isRefreshing by viewModel.isScheduleRefreshing.collectAsState()
    val isActionInProgress by viewModel.isBookingActionInProgress.collectAsState()
    val loadError by viewModel.scheduleLoadError.collectAsState()
    val actionError by viewModel.scheduleErrorMessage.collectAsState()
    val swapSourceSession by viewModel.swapSourceSession.collectAsState()
    val currencySymbol by viewModel.currencySymbol.collectAsState()
    val snackbarMsg by viewModel.snackbarMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.loadScheduleSessions(forceRefresh = false)
    }

    LaunchedEffect(snackbarMsg) {
        snackbarMsg?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    ScheduleContent(
        filterResult = filterResult,
        isLoading = isLoading,
        isRefreshing = isRefreshing,
        isActionInProgress = isActionInProgress,
        loadError = loadError,
        actionError = actionError,
        swapSourceSession = swapSourceSession,
        currencySymbol = currencySymbol,
        snackbarHostState = snackbarHostState,
        onRefresh = { viewModel.loadScheduleSessions(forceRefresh = true) },
        onBookSession = { session, onDone ->
            viewModel.bookScheduleSession(session, onBooked = onDone)
        },
        onStartSwap = { session ->
            viewModel.startSwapForSession(session)
        },
        onCancelSwap = {
            viewModel.cancelSwapMode()
        },
        onConfirmSwap = { newSession, onDone ->
            viewModel.confirmSwapScheduleSession(newSession, onSwapped = onDone)
        },
        onDismissActionError = {
            viewModel.dismissScheduleError()
        },
        onBack = onBack
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScheduleContent(
    filterResult: ScheduleFilterResult,
    isLoading: Boolean = false,
    isRefreshing: Boolean = false,
    isActionInProgress: Boolean = false,
    loadError: String? = null,
    actionError: String? = null,
    swapSourceSession: ScheduleSession? = null,
    currencySymbol: String = "₱",
    nowMillis: Long = System.currentTimeMillis(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onRefresh: () -> Unit = {},
    onBookSession: (ScheduleSession, () -> Unit) -> Unit = { _, _ -> },
    onStartSwap: (ScheduleSession) -> Unit = {},
    onCancelSwap: () -> Unit = {},
    onConfirmSwap: (ScheduleSession, () -> Unit) -> Unit = { _, _ -> },
    onDismissActionError: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Available (default), 1 = Booked
    var pendingBookSession by remember { mutableStateOf<ScheduleSession?>(null) }
    var pendingSwapTarget by remember { mutableStateOf<ScheduleSession?>(null) }
    var selectedBookedItemForSheet by remember { mutableStateOf<BookedSessionItem?>(null) }
    var debugExpanded by remember { mutableStateOf(false) }

    BackHandler {
        if (swapSourceSession != null) {
            onCancelSwap()
        } else {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Schedule",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (swapSourceSession != null) onCancelSwap() else onBack()
                        },
                        modifier = Modifier.testTag("schedule_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = Modifier
            .fillMaxSize()
            .testTag("schedule_screen")
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Two tabs: "Available" (default) and "Booked"
            SecondaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = "Available",
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    modifier = Modifier.testTag("tab_available")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        if (swapSourceSession != null) onCancelSwap()
                        selectedTab = 1
                    },
                    text = {
                        val bookedCount = filterResult.bookedDisplayed.size
                        Text(
                            text = if (bookedCount > 0) "Booked ($bookedCount)" else "Booked",
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    modifier = Modifier.testTag("tab_booked")
                )
            }

            if (swapSourceSession != null && selectedTab == 0) {
                Surface(
                    color = DelivioYellow.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, DelivioYellowDark.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("swap_mode_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Choose a new session to swap into",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Replacing ${ScheduleRules.formatTimeRange(swapSourceSession.startAtMillis, swapSourceSession.endAtMillis)}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = onCancelSwap,
                            modifier = Modifier.testTag("cancel_swap_mode_button")
                        ) {
                            Text("Cancel", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (!loadError.isNullOrBlank()) {
                Surface(
                    color = DelivioRed.copy(alpha = 0.12f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = loadError,
                            fontSize = 12.sp,
                            color = DelivioRed,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onRefresh) {
                            Text("Retry", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    isLoading && filterResult.loadedCount == 0 -> {
                        ScheduleLoadingSkeletons()
                    }
                    selectedTab == 0 -> {
                        val listToShow = if (swapSourceSession != null) {
                            filterResult.availableDisplayed.filter { it.isEnabled }
                        } else {
                            filterResult.availableDisplayed
                        }

                        if (listToShow.isEmpty()) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                item {
                                    EmptyScheduleState(
                                        message = if (swapSourceSession != null) {
                                            "No non-conflicting sessions available right now."
                                        } else {
                                            "No sessions posted right now. The admin will post new sessions here."
                                        },
                                        testTag = "available_empty_state"
                                    )
                                }
                                item {
                                    ScheduleDebugFooter(
                                        filterResult = filterResult,
                                        expanded = debugExpanded,
                                        onToggle = { debugExpanded = !debugExpanded }
                                    )
                                }
                            }
                        } else {
                            val grouped = remember(listToShow, nowMillis) {
                                listToShow.groupBy {
                                    ScheduleRules.dayGroupLabel(it.session.startAtMillis, nowMillis)
                                }
                            }
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("available_sessions_list"),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                grouped.forEach { (dayLabel, dayItems) ->
                                    item(key = "header_$dayLabel") {
                                        Text(
                                            text = dayLabel,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                                        )
                                    }
                                    items(
                                        items = dayItems,
                                        key = { it.session.id }
                                    ) { item ->
                                        AvailableSessionCard(
                                            item = item,
                                            currencySymbol = currencySymbol,
                                            nowMillis = nowMillis,
                                            isSwapPickMode = swapSourceSession != null,
                                            isActionBusy = isActionInProgress,
                                            onAddClick = {
                                                if (swapSourceSession != null) {
                                                    pendingSwapTarget = item.session
                                                } else {
                                                    pendingBookSession = item.session
                                                }
                                            }
                                        )
                                    }
                                }

                                item(key = "debug_footer") {
                                    ScheduleDebugFooter(
                                        filterResult = filterResult,
                                        expanded = debugExpanded,
                                        onToggle = { debugExpanded = !debugExpanded }
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        val bookedList = filterResult.bookedDisplayed
                        if (bookedList.isEmpty()) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp)
                            ) {
                                item {
                                    EmptyScheduleState(
                                        message = "You have not booked any sessions yet.",
                                        testTag = "booked_empty_state"
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("booked_sessions_list"),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(
                                    items = bookedList,
                                    key = { it.session.id }
                                ) { bookedItem ->
                                    BookedSessionCard(
                                        item = bookedItem,
                                        currencySymbol = currencySymbol,
                                        nowMillis = nowMillis,
                                        onClick = { selectedBookedItemForSheet = bookedItem }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Booking Confirmation Dialog
    pendingBookSession?.let { sessionToBook ->
        val dateText = ScheduleRules.formatSessionDate(sessionToBook.startAtMillis, nowMillis)
        val timeText = ScheduleRules.formatTimeRange(sessionToBook.startAtMillis, sessionToBook.endAtMillis)
        val durationText = ScheduleRules.formatDuration(sessionToBook.startAtMillis, sessionToBook.endAtMillis)
        val rewardChips = remember(sessionToBook.id, sessionToBook.rewards, currencySymbol) {
            ScheduleRules.formatRewardChips(sessionToBook.rewards, currencySymbol)
        }

        AlertDialog(
            onDismissRequest = { if (!isActionInProgress) pendingBookSession = null },
            title = {
                Text(
                    text = "Do you want to book this session?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (sessionToBook.title.isNotBlank()) {
                        Text(
                            text = sessionToBook.title,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                    Text(
                        text = "$dateText\n$timeText ($durationText)",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (sessionToBook.rewards.hasRewards) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(text = sessionToBook.rewards.emoji, fontSize = 18.sp)
                            Text(
                                text = rewardChips.joinToString(" • ").ifBlank { "Session rewards" },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = DelivioYellowDark
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onBookSession(sessionToBook) {
                            pendingBookSession = null
                            selectedTab = 1
                        }
                    },
                    enabled = !isActionInProgress,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DelivioYellow,
                        contentColor = TextOnYellow
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("confirm_book_button")
                ) {
                    if (isActionInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = TextOnYellow
                        )
                    } else {
                        Text("Confirmed", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingBookSession = null },
                    enabled = !isActionInProgress,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("cancel_book_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Swap Confirmation Dialog
    if (pendingSwapTarget != null && swapSourceSession != null) {
        val newSession = pendingSwapTarget!!
        val oldRange = ScheduleRules.formatTimeRange(swapSourceSession.startAtMillis, swapSourceSession.endAtMillis)
        val newRange = ScheduleRules.formatTimeRange(newSession.startAtMillis, newSession.endAtMillis)
        val newDate = ScheduleRules.formatSessionDate(newSession.startAtMillis, nowMillis)
        val rewardChips = remember(newSession.id, newSession.rewards, currencySymbol) {
            ScheduleRules.formatRewardChips(newSession.rewards, currencySymbol)
        }

        AlertDialog(
            onDismissRequest = { if (!isActionInProgress) pendingSwapTarget = null },
            title = {
                Text(
                    text = "Swap $oldRange for $newRange?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "New session: $newDate • $newRange",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (newSession.rewards.hasRewards) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(text = newSession.rewards.emoji, fontSize = 18.sp)
                            Text(
                                text = rewardChips.joinToString(" • ").ifBlank { "Session rewards" },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = DelivioYellowDark
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onConfirmSwap(newSession) {
                            pendingSwapTarget = null
                            selectedTab = 1
                        }
                    },
                    enabled = !isActionInProgress,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DelivioYellow,
                        contentColor = TextOnYellow
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("confirm_swap_button")
                ) {
                    if (isActionInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = TextOnYellow
                        )
                    } else {
                        Text("Confirm Swap", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingSwapTarget = null },
                    enabled = !isActionInProgress,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("cancel_swap_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Booked Session Bottom Sheet (Swap / Keep)
    selectedBookedItemForSheet?.let { item ->
        val session = item.session
        val tooLate = session.isTooLateToChange(nowMillis)
        val range = ScheduleRules.formatTimeRange(session.startAtMillis, session.endAtMillis)
        val dateText = ScheduleRules.formatSessionDate(session.startAtMillis, nowMillis)

        ModalBottomSheet(
            onDismissRequest = { selectedBookedItemForSheet = null },
            modifier = Modifier.testTag("booked_action_sheet")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (session.rewards.hasRewards) {
                        Text(text = session.rewards.emoji, fontSize = 22.sp)
                    }
                    Column {
                        Text(
                            text = session.title.ifBlank { range },
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = "$dateText • $range",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (tooLate) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Too late to change this session",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Button(
                    onClick = {
                        selectedBookedItemForSheet = null
                        onStartSwap(session)
                        selectedTab = 0
                    },
                    enabled = !tooLate && !isActionInProgress,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DelivioYellow,
                        contentColor = TextOnYellow
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("sheet_swap_button")
                ) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Swap", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { selectedBookedItemForSheet = null },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("sheet_keep_button")
                ) {
                    Text("Keep", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Error Alert Dialog for batch booking / swap failures
    actionError?.let { errMsg ->
        AlertDialog(
            onDismissRequest = onDismissActionError,
            title = {
                Text("Unable to Complete Action", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(errMsg, fontSize = 14.sp)
            },
            confirmButton = {
                Button(
                    onClick = onDismissActionError,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DelivioYellow,
                        contentColor = TextOnYellow
                    ),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AvailableSessionCard(
    item: AvailableSessionItem,
    currencySymbol: String,
    nowMillis: Long,
    isSwapPickMode: Boolean,
    isActionBusy: Boolean,
    onAddClick: () -> Unit
) {
    val session = item.session
    val enabled = item.isEnabled && !isActionBusy
    val timeRange = ScheduleRules.formatTimeRange(session.startAtMillis, session.endAtMillis)
    val duration = ScheduleRules.formatDuration(session.startAtMillis, session.endAtMillis)
    val dateLabel = ScheduleRules.formatSessionDate(session.startAtMillis, nowMillis)
    val rewardChips = remember(session.id, session.rewards, currencySymbol) {
        ScheduleRules.formatRewardChips(session.rewards, currencySymbol)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isEnabled) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
            }
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (item.isEnabled) {
                MaterialTheme.colorScheme.outlineVariant
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("available_session_card_${session.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (session.rewards.hasRewards) {
                            Surface(
                                shape = CircleShape,
                                color = DelivioYellow.copy(alpha = if (item.isEnabled) 0.25f else 0.1f),
                                modifier = Modifier.testTag("session_reward_emoji_${session.id}")
                            ) {
                                Text(
                                    text = session.rewards.emoji,
                                    fontSize = 18.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Text(
                            text = timeRange,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (item.isEnabled) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = duration,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    val subtitle = buildString {
                        append(dateLabel)
                        if (session.title.isNotBlank()) {
                            append(" • ")
                            append(session.title)
                        }
                    }
                    Text(
                        text = subtitle,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                FilledIconButton(
                    onClick = onAddClick,
                    enabled = enabled,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = DelivioYellow,
                        contentColor = TextOnYellow,
                        disabledContainerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("book_session_button_${session.id}")
                ) {
                    Icon(
                        imageVector = if (isSwapPickMode) Icons.Default.SwapHoriz else Icons.Default.Add,
                        contentDescription = if (isSwapPickMode) "Swap to session" else "Book session"
                    )
                }
            }

            if (session.rewards.hasRewards && rewardChips.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    rewardChips.forEach { chip ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (item.isEnabled) {
                                DelivioYellow.copy(alpha = 0.16f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            border = BorderStroke(
                                1.dp,
                                if (item.isEnabled) {
                                    DelivioYellowDark.copy(alpha = 0.35f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                }
                            )
                        ) {
                            Text(
                                text = chip,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (item.isEnabled) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            if (session.rewards.hasRewards && session.rewards.note.isNotBlank()) {
                Text(
                    text = session.rewards.note,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${session.remainingSlots} of ${session.slots} left",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (session.isFull) DelivioRed else MaterialTheme.colorScheme.onSurfaceVariant
                )

                item.disableReason?.let { reason ->
                    Text(
                        text = reason,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.testTag("disable_reason_${session.id}")
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookedSessionCard(
    item: BookedSessionItem,
    currencySymbol: String,
    nowMillis: Long,
    onClick: () -> Unit
) {
    val session = item.session
    val claim = item.claim
    val status = session.statusAt(nowMillis)
    val tooLate = session.isTooLateToChange(nowMillis)
    val timeRange = ScheduleRules.formatTimeRange(session.startAtMillis, session.endAtMillis)
    val duration = ScheduleRules.formatDuration(session.startAtMillis, session.endAtMillis)
    val dateLabel = ScheduleRules.formatSessionDate(session.startAtMillis, nowMillis)
    val rewardChips = remember(session.id, session.rewards, currencySymbol) {
        ScheduleRules.formatRewardChips(session.rewards, currencySymbol)
    }

    val (statusBg, statusFg) = when (status) {
        BookedSessionStatus.UPCOMING -> DelivioYellow.copy(alpha = 0.22f) to TextOnYellow
        BookedSessionStatus.LIVE_NOW -> DelivioGreen.copy(alpha = 0.16f) to DelivioGreen
        BookedSessionStatus.COMPLETED -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 1.dp,
            color = if (status == BookedSessionStatus.LIVE_NOW) DelivioGreen.copy(alpha = 0.55f)
            else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("booked_session_card_${session.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusBg
                ) {
                    Text(
                        text = status.label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusFg,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                when (status) {
                    BookedSessionStatus.UPCOMING -> {
                        Text(
                            text = ScheduleRules.formatCountdownUntilStart(session.startAtMillis, nowMillis),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DelivioYellowDark
                        )
                    }
                    BookedSessionStatus.LIVE_NOW -> {
                        Text(
                            text = ScheduleRules.formatTimeLeft(session.endAtMillis, nowMillis),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DelivioGreen
                        )
                    }
                    BookedSessionStatus.COMPLETED -> Unit
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (session.rewards.hasRewards) {
                    Text(
                        text = session.rewards.emoji,
                        fontSize = 20.sp
                    )
                }
                Column {
                    Text(
                        text = "$timeRange ($duration)",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = buildString {
                            append(dateLabel)
                            if (session.title.isNotBlank()) {
                                append(" • ")
                                append(session.title)
                            }
                        },
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (session.rewards.hasRewards && rewardChips.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    rewardChips.forEach { chip ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = DelivioYellow.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, DelivioYellowDark.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = chip,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Reward review / settled state after session
            if (claim != null && claim.isSettled) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = DelivioGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Rewards received $currencySymbol${ScheduleRules.formatAmount(claim.rewardAmount)}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = DelivioGreen
                    )
                }
            } else if (status == BookedSessionStatus.COMPLETED || claim != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.HourglassTop,
                        contentDescription = null,
                        tint = DelivioOrange,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Rewards pending review",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (status != BookedSessionStatus.COMPLETED && tooLate) {
                Text(
                    text = "Too late to change this session",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyScheduleState(
    message: String,
    testTag: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 56.dp, horizontal = 24.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(DelivioYellow.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CalendarMonth,
                contentDescription = null,
                tint = DelivioYellowDark,
                modifier = Modifier.size(32.dp)
            )
        }
        Text(
            text = message,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ScheduleLoadingSkeletons() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("schedule_loading_skeleton"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(3) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(150.dp)
                            .height(18.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    )
                    Box(
                        modifier = Modifier
                            .width(210.dp)
                            .height(14.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    )
                    Box(
                        modifier = Modifier
                            .width(100.dp)
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduleDebugFooter(
    filterResult: ScheduleFilterResult,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 20.dp)
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp)
            .testTag("schedule_debug_line")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = filterResult.summaryLine,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Hide debug details" else "Show debug details",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (filterResult.hiddenReasons.isEmpty()) {
                    Text(
                        text = "No hidden sessions.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    filterResult.hiddenReasons.forEach { entry ->
                        Text(
                            text = "• ${entry.label}: ${entry.reason}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
