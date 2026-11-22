package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.constants.OrderStepConstants
import com.example.data.models.*
import com.example.ui.components.CustomerOrderChatDialog
import com.example.ui.components.PaymentBadgeContent
import com.example.ui.theme.*
import com.example.viewmodel.RiderViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

private val MANILA_TZ: TimeZone = TimeZone.getTimeZone("Asia/Manila")

private fun manilaCalendar(millis: Long): Calendar {
    return Calendar.getInstance(MANILA_TZ, Locale.US).apply {
        timeInMillis = millis
    }
}

private fun startOfDayManilaMillis(millis: Long): Long {
    return manilaCalendar(millis).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun startOfWeekManilaMillis(millis: Long): Long {
    return manilaCalendar(millis).apply {
        firstDayOfWeek = Calendar.MONDAY
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun startOfMonthManilaMillis(millis: Long): Long {
    return manilaCalendar(millis).apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun resolveOrderTimestampMillis(order: Order): Long {
    val delivered = order.deliveredAtMillis
    if (delivered > 0L) return delivered
    val completed = OrderDocumentParser.parseTolerantTimeMillisOrZero(order.completedAt)
    if (completed > 0L) return completed
    val updated = OrderDocumentParser.parseTolerantTimeMillisOrZero(order.updatedAt)
    if (updated > 0L) return updated
    return order.acceptedAtMillis
}

private fun isClaimCredited(status: String): Boolean {
    val norm = status.trim().lowercase(Locale.US)
    return norm == "credited" || norm == "approved" || norm == "paid"
}

private fun formatRatePercent(rate: Double): String {
    return if (abs(rate - rate.toInt().toDouble()) < 0.01) {
        rate.toInt().toString()
    } else {
        String.format(Locale.US, "%.1f", rate)
    }
}

data class ManilaDaySlot(
    val dayStartMillis: Long,
    val dayOfWeek: String,
    val dayOfMonth: Int,
    val isToday: Boolean
)

data class EarningsJobRecord(
    val id: String,
    val orderNumber: String,
    val serviceType: String,
    val merchantName: String,
    val area: String,
    val timeText: String,
    val completedDateText: String,
    val completedAtMillis: Long,
    val deliveryFee: Double,
    val pickupFee: Double,
    val commissionRate: Double,
    val commissionAmount: Double,
    val tip: Double = 0.0,
    val lateTip: Double = 0.0,
    val netEarning: Double,
    val pickupKm: Double = 0.0,
    val batchIndex: Int = 1,
    val riderQuote: RiderQuote? = null,
    val paymentMethod: String = "",
    val paymentProvider: String = "",
    val cashToCollect: Double = 0.0,
    val codCollected: Double = 0.0,
    val customerName: String = "",
    val customerId: String = "",
    val closedAtMillis: Long? = null,
    val branchName: String = "",
    val branchCode: String = ""
) {
    val displayMerchantName: String
        get() = formatMerchantDisplayName(merchantName, branchName, branchCode).ifBlank { merchantName }

    val paymentType: PaymentType
        get() = parsePaymentType(paymentMethod, cashToCollect)

    val isCod: Boolean
        get() = paymentType == PaymentType.COD

    val isPaidOnline: Boolean
        get() = paymentType == PaymentType.PAID_ONLINE

    val paymentProviderLabel: String
        get() = formatPaymentProvider(paymentProvider)

    val totalTips: Double
        get() = tip + lateTip

    val resolvedCodCashCollected: Double
        get() = if (!isCod) 0.0 else when {
            codCollected > 0.0 -> codCollected
            cashToCollect > 0.0 -> cashToCollect
            else -> 0.0
        }

    val displayOrderNumber: String
        get() {
            val raw = orderNumber.trim()
            return when {
                raw.isNotBlank() -> if (raw.startsWith("#")) raw else "#$raw"
                id.isNotBlank() -> "#" + id.takeLast(6).uppercase(Locale.US)
                else -> "#000000"
            }
        }
}

fun buildEarningsJobRecord(order: Order): EarningsJobRecord {
    val completedMs = resolveOrderTimestampMillis(order)
    val safeMs = if (completedMs > 0L) completedMs else System.currentTimeMillis()
    val timeFmt = SimpleDateFormat("hh:mm a", Locale.US).apply { timeZone = MANILA_TZ }
    val dateFmt = SimpleDateFormat("MMM d, yyyy", Locale.US).apply { timeZone = MANILA_TZ }

    val quote = order.riderQuote
    val delivFee = quote?.deliveryFee
        ?: order.pricing?.deliveryFee?.takeIf { it > 0.0 }
        ?: order.riderEarning?.bookingFee?.takeIf { it > 0.0 }
        ?: order.feesDeliveryFee.takeIf { it > 0.0 }
        ?: order.deliveryFee.takeIf { it > 0.0 }
        ?: order.estimatedEarnings
    val pickupFee = quote?.pickupFee ?: order.riderEarning?.pickupFee ?: 0.0
    val commRate = quote?.commissionRate ?: 0.0
    val commAmount = quote?.commissionAmount ?: 0.0
    val net = order.estimatedEarnings
    val pickupKm = quote?.pickupKm ?: order.pickupDistanceKm
    val batchIndex = quote?.batchIndex ?: order.batchIndex.coerceAtLeast(1)

    return EarningsJobRecord(
        id = order.id,
        orderNumber = order.orderNumber,
        serviceType = OrderStepConstants.getServiceBadge(order.serviceType),
        merchantName = if (order.isTaxi) (order.stops.firstOrNull()?.address ?: "Pickup Point") else order.merchantName,
        area = if (order.isTaxi) {
            "${order.customerArea} (${order.stops.size + 1} stops)"
        } else {
            val from = order.merchantAddress.take(20).ifBlank { order.displayPickupName }
            val to = order.customerArea.ifBlank { order.customerAddress.take(20) }
            "$from to $to"
        },
        timeText = timeFmt.format(Date(safeMs)),
        completedDateText = dateFmt.format(Date(safeMs)),
        completedAtMillis = completedMs,
        deliveryFee = delivFee,
        pickupFee = pickupFee,
        commissionRate = commRate,
        commissionAmount = commAmount,
        tip = order.tip,
        lateTip = order.lateTipAmount,
        netEarning = net,
        pickupKm = pickupKm,
        batchIndex = batchIndex,
        riderQuote = quote,
        paymentMethod = order.paymentMethod,
        paymentProvider = order.paymentProvider,
        cashToCollect = order.cashAmountToCollect,
        codCollected = order.codCollected,
        customerName = order.customerName,
        customerId = order.customerId,
        closedAtMillis = OrderChatConstants.getOrderClosedTimeMillis(order) ?: completedMs.takeIf { it > 0L },
        branchName = if (order.isTaxi) "" else order.branchName,
        branchCode = if (order.isTaxi) "" else order.branchCode
    )
}

@Composable
fun EarningsScreen(
    viewModel: RiderViewModel,
    onBack: () -> Unit
) {
    val currencySymbol by viewModel.currencySymbol.collectAsState()
    val deliveredOrders by viewModel.deliveredOrders.collectAsState()
    val questClaims by viewModel.questClaims.collectAsState()
    val sessionClaims by viewModel.sessionClaims.collectAsState()
    val activeChatOrder by viewModel.activeChatOrder.collectAsState()
    val activeChatMessages by viewModel.activeChatMessages.collectAsState()

    EarningsContent(
        currencySymbol = currencySymbol,
        deliveredOrders = deliveredOrders,
        questClaims = questClaims.values.toList(),
        sessionClaims = sessionClaims,
        onBack = onBack,
        onOpenTripChat = { job ->
            viewModel.openTripChat(
                orderId = job.id,
                orderNumber = job.displayOrderNumber,
                serviceType = job.serviceType,
                customerName = job.customerName.ifBlank { "Customer" },
                customerId = job.customerId,
                merchantName = job.merchantName,
                branchName = job.branchName,
                branchCode = job.branchCode,
                closedTimeMillis = job.closedAtMillis
            )
        },
        onReportIssue = { job ->
            viewModel.showToast("Incident report opened for ${job.displayOrderNumber}")
        }
    )

    activeChatOrder?.let { chatOrder ->
        CustomerOrderChatDialog(
            order = chatOrder,
            messages = activeChatMessages,
            onSendMessage = { text, imageDataUri ->
                viewModel.sendChatMessage(chatOrder, text, imageDataUri)
            },
            onDismiss = { viewModel.closeChat() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EarningsContent(
    currencySymbol: String,
    deliveredOrders: List<Order>,
    questClaims: List<QuestClaim>,
    sessionClaims: List<SessionClaim>,
    onBack: () -> Unit,
    onOpenTripChat: (EarningsJobRecord) -> Unit = {},
    onReportIssue: (EarningsJobRecord) -> Unit = {},
    nowMillis: Long = System.currentTimeMillis()
) {
    val context = LocalContext.current
    val todayStartMs = remember(nowMillis) { startOfDayManilaMillis(nowMillis) }
    val weekStartMs = remember(nowMillis) { startOfWeekManilaMillis(nowMillis) }
    val monthStartMs = remember(nowMillis) { startOfMonthManilaMillis(nowMillis) }

    val daySlots = remember(todayStartMs) {
        val wkFmt = SimpleDateFormat("EEE", Locale.US).apply { timeZone = MANILA_TZ }
        (-6..0).map { offset ->
            val cal = manilaCalendar(todayStartMs).apply { add(Calendar.DAY_OF_MONTH, offset) }
            ManilaDaySlot(
                dayStartMillis = cal.timeInMillis,
                dayOfWeek = wkFmt.format(cal.time),
                dayOfMonth = cal.get(Calendar.DAY_OF_MONTH),
                isToday = offset == 0
            )
        }
    }

    var selectedPeriod by remember { mutableStateOf("Daily") } // "Daily", "Weekly", "Monthly"
    var selectedDayStartMs by remember(todayStartMs) { mutableLongStateOf(todayStartMs) }
    var selectedServiceFilter by remember { mutableStateOf("All") }
    var selectedPaymentFilter by remember { mutableStateOf("All") }

    var selectedJobForDetail by remember { mutableStateOf<EarningsJobRecord?>(null) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showStatementsDialog by remember { mutableStateOf(false) }

    val allJobs = remember(deliveredOrders) {
        deliveredOrders
            .map { buildEarningsJobRecord(it) }
            .distinctBy { it.id }
            .sortedByDescending { it.completedAtMillis }
    }

    val dayLengthMs = 24L * 60L * 60L * 1000L
    val isInSelectedPeriod: (Long) -> Boolean = remember(
        selectedPeriod,
        selectedDayStartMs,
        weekStartMs,
        monthStartMs,
        nowMillis
    ) {
        { ts: Long ->
            if (ts <= 0L) {
                false
            } else {
                when (selectedPeriod) {
                    "Daily" -> ts >= selectedDayStartMs && ts < selectedDayStartMs + dayLengthMs
                    "Weekly" -> ts >= weekStartMs && ts <= nowMillis + dayLengthMs
                    else -> ts >= monthStartMs && ts <= nowMillis + dayLengthMs
                }
            }
        }
    }

    val periodJobs = remember(allJobs, isInSelectedPeriod) {
        allJobs.filter { isInSelectedPeriod(it.completedAtMillis) }
    }

    val filteredJobs = remember(periodJobs, selectedServiceFilter, selectedPaymentFilter) {
        periodJobs.filter { job ->
            val jobSvc = job.serviceType.uppercase(Locale.US).trim()
            val matchesService = when (selectedServiceFilter) {
                "All" -> true
                "FOOD" -> jobSvc == "FOOD"
                "MART" -> jobSvc in listOf("MART", "CONVENIENCE")
                "PARCEL" -> jobSvc == "PARCEL"
                "PABILI" -> jobSvc == "PABILI"
                "INSTANT" -> jobSvc in listOf("INSTANT", "REQUEST", "MERCHANT REQUEST")
                "TAXI" -> jobSvc == "TAXI"
                else -> true
            }
            val matchesPayment = when (selectedPaymentFilter) {
                "All" -> true
                "Paid Online" -> job.isPaidOnline
                "COD" -> job.isCod
                else -> true
            }
            matchesService && matchesPayment
        }
    }

    // Period bonuses from credited sessionClaims + questClaims
    val periodBonuses = remember(questClaims, sessionClaims, isInSelectedPeriod) {
        val questBonus = questClaims
            .filter { isClaimCredited(it.status) && it.rewardAmount > 0.0 }
            .filter { claim ->
                val ts = claim.createdAt?.toDate()?.time ?: 0L
                isInSelectedPeriod(ts)
            }
            .sumOf { it.rewardAmount }
        val sessionBonus = sessionClaims
            .filter { isClaimCredited(it.status) && it.rewardAmount > 0.0 }
            .filter { claim -> isInSelectedPeriod(claim.createdAtMillis) }
            .sumOf { it.rewardAmount }
        questBonus + sessionBonus
    }

    val jobsNetEarnings = remember(filteredJobs) { filteredJobs.sumOf { it.netEarning } }
    val totalNetEarnings = remember(jobsNetEarnings, periodBonuses) { jobsNetEarnings + periodBonuses }
    val totalJobsCount = remember(filteredJobs) { filteredJobs.size }

    val totalDeliveryFees = remember(filteredJobs) { filteredJobs.sumOf { it.deliveryFee } }
    val totalPickupFees = remember(filteredJobs) { filteredJobs.sumOf { it.pickupFee } }
    val totalCommissionFee = remember(filteredJobs) { filteredJobs.sumOf { it.commissionAmount } }
    val totalTips = remember(filteredJobs) { filteredJobs.sumOf { it.totalTips } }

    // COD card calculations (independent of net earnings)
    val codJobs = remember(filteredJobs) { filteredJobs.filter { it.isCod } }
    val totalCodCashCollected = remember(codJobs) { codJobs.sumOf { it.resolvedCodCashCollected } }
    val codTripsNetEarnings = remember(codJobs) { codJobs.sumOf { it.netEarning } }
    val cashToRemit = remember(totalCodCashCollected, codTripsNetEarnings) {
        (totalCodCashCollected - codTripsNetEarnings).coerceAtLeast(0.0)
    }

    val periodHeaderLabel = remember(selectedPeriod, selectedDayStartMs, weekStartMs, monthStartMs) {
        val fullDateFmt = SimpleDateFormat("MMM d, yyyy", Locale.US).apply { timeZone = MANILA_TZ }
        val shortDateFmt = SimpleDateFormat("MMM d", Locale.US).apply { timeZone = MANILA_TZ }
        val monthFmt = SimpleDateFormat("MMMM yyyy", Locale.US).apply { timeZone = MANILA_TZ }
        when (selectedPeriod) {
            "Daily" -> fullDateFmt.format(Date(selectedDayStartMs))
            "Weekly" -> {
                val weekEndMs = weekStartMs + 6L * dayLengthMs
                "${shortDateFmt.format(Date(weekStartMs))} - ${fullDateFmt.format(Date(weekEndMs))}"
            }
            else -> monthFmt.format(Date(monthStartMs))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Earnings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showHelpDialog = true },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Default.HelpOutline, contentDescription = "Earnings Help")
                    }
                    TextButton(
                        onClick = { showStatementsDialog = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .padding(end = 6.dp)
                    ) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = "Statements", modifier = Modifier.size(18.dp), tint = DelivioYellowDark)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Statements", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = Modifier.testTag("earnings_screen")
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .testTag("earnings_lazy_column"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Daily / Weekly / Monthly Period Selector (Asia/Manila)
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(4.dp)) {
                        listOf("Daily", "Weekly", "Monthly").forEach { period ->
                            val isSelected = selectedPeriod == period
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) DelivioYellow else Color.Transparent)
                                    .clickable { selectedPeriod = period }
                                    .testTag("earnings_period_${period.lowercase(Locale.US)}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = period,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (isSelected) TextOnYellow else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // 2. Manila Period Label
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = periodHeaderLabel,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = "Asia/Manila time", modifier = Modifier.size(14.dp), tint = DelivioYellowDark)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Manila (PHT)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // 3. 7-Day Date Strip (when Daily is selected)
            if (selectedPeriod == "Daily") {
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(daySlots) { slot ->
                            val isSelected = selectedDayStartMs == slot.dayStartMillis
                            Surface(
                                onClick = { selectedDayStartMs = slot.dayStartMillis },
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) DelivioYellow else MaterialTheme.colorScheme.surface,
                                border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.widthIn(min = 56.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = if (slot.isToday) "Today" else slot.dayOfWeek,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) TextOnYellow else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${slot.dayOfMonth}",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (isSelected) TextOnYellow else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Summary Card: Net earnings & Jobs completed
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("earnings_summary_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Net earnings",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$currencySymbol${String.format(Locale.US, "%.2f", totalNetEarnings)}",
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Black,
                                color = DelivioYellowDark
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = DelivioYellowLight
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "$totalJobsCount",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 20.sp,
                                    color = TextOnYellow
                                )
                                Text(
                                    text = "Jobs completed",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = TextOnYellow
                                )
                            }
                        }
                    }
                }
            }

            // 5. Breakdown Card (ONLY: Delivery fees, Pick up fees, Commission fee, Tips, Bonuses)
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("earnings_breakdown_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Earnings breakdown",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        BreakdownRow(
                            label = "Delivery fees",
                            amount = "$currencySymbol${String.format(Locale.US, "%.2f", totalDeliveryFees)}"
                        )
                        BreakdownRow(
                            label = "Pick up fees",
                            amount = "$currencySymbol${String.format(Locale.US, "%.2f", totalPickupFees)}"
                        )
                        BreakdownRow(
                            label = "Commission fee",
                            amount = "-$currencySymbol${String.format(Locale.US, "%.2f", totalCommissionFee)}",
                            valueColor = if (totalCommissionFee > 0.0) DelivioRed else MaterialTheme.colorScheme.onSurface
                        )
                        BreakdownRow(
                            label = "Tips",
                            amount = "+$currencySymbol${String.format(Locale.US, "%.2f", totalTips)}",
                            valueColor = if (totalTips > 0.0) DelivioGreenDark else MaterialTheme.colorScheme.onSurface
                        )
                        BreakdownRow(
                            label = "Bonuses",
                            amount = "+$currencySymbol${String.format(Locale.US, "%.2f", periodBonuses)}",
                            valueColor = if (periodBonuses > 0.0) DelivioGreenDark else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // 6. Separate COD Card (COD cash collected + Cash to remit)
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("earnings_cod_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalAtm,
                                contentDescription = "COD Cash",
                                tint = DelivioYellowDark,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "COD cash summary",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "Cash collected from customers is not counted as earnings.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        BreakdownRow(
                            label = "COD cash collected",
                            amount = "$currencySymbol${String.format(Locale.US, "%.2f", totalCodCashCollected)}",
                            isBold = true
                        )
                        BreakdownRow(
                            label = "Cash to remit",
                            amount = "$currencySymbol${String.format(Locale.US, "%.2f", cashToRemit)}",
                            isBold = true,
                            valueColor = if (cashToRemit > 0.0) DelivioRed else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // 7. Service Filter Chips
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Service Type", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val services = listOf("All", "FOOD", "MART", "PARCEL", "PABILI", "INSTANT", "TAXI")
                        items(services) { svc ->
                            FilterChip(
                                selected = selectedServiceFilter == svc,
                                onClick = { selectedServiceFilter = svc },
                                label = { Text(svc, fontSize = 12.sp) }
                            )
                        }
                    }
                }
            }

            // 8. Payment Filter Chips
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Payment Method", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("All", "Paid Online", "COD").forEach { payType ->
                            FilterChip(
                                selected = selectedPaymentFilter == payType,
                                onClick = { selectedPaymentFilter = payType },
                                label = { Text(payType, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }

            // 9. Trip History List
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Trip History ($totalJobsCount)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Trips net: $currencySymbol${String.format(Locale.US, "%.2f", jobsNetEarnings)}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = DelivioYellowDark
                    )
                }
            }

            if (filteredJobs.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Inbox, contentDescription = "No trips", tint = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No completed trips for this period.", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Completed deliveries will appear here with their earnings breakdown.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                items(filteredJobs, key = { it.id }) { job ->
                    JobRowCard(
                        job = job,
                        currencySymbol = currencySymbol,
                        onClick = { selectedJobForDetail = job }
                    )
                }
            }
        }
    }

    // Trip Detail Dialog showing its riderQuote breakdown
    selectedJobForDetail?.let { job ->
        val commRateSuffix = if (job.commissionRate > 0.0) {
            " (-${formatRatePercent(job.commissionRate)}%)"
        } else {
            ""
        }
        AlertDialog(
            onDismissRequest = { selectedJobForDetail = null },
            modifier = Modifier.testTag("earnings_trip_detail_dialog"),
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Trip ${job.displayOrderNumber}", fontWeight = FontWeight.Bold)
                    Surface(shape = RoundedCornerShape(8.dp), color = DelivioYellowLight) {
                        Text(
                            text = job.serviceType,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextOnYellow,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${job.displayMerchantName} • ${job.area}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "Completed ${job.completedDateText} at ${job.timeText}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (job.pickupKm > 0.0 || job.batchIndex > 1) {
                        Text(
                            text = buildString {
                                if (job.pickupKm > 0.0) {
                                    append("Pickup distance: ${String.format(Locale.US, "%.1f", job.pickupKm)} km")
                                }
                                if (job.batchIndex > 1) {
                                    if (isNotEmpty()) append(" • ")
                                    append("Booking #${job.batchIndex} in batch")
                                }
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    HorizontalDivider()

                    BreakdownRow("Delivery fee", "$currencySymbol${String.format(Locale.US, "%.2f", job.deliveryFee)}")
                    BreakdownRow("Pick up fee", "$currencySymbol${String.format(Locale.US, "%.2f", job.pickupFee)}")
                    BreakdownRow(
                        label = "Commission fee$commRateSuffix",
                        amount = "-$currencySymbol${String.format(Locale.US, "%.2f", job.commissionAmount)}",
                        valueColor = if (job.commissionAmount > 0.0) DelivioRed else MaterialTheme.colorScheme.onSurface
                    )
                    BreakdownRow(
                        label = "Tip",
                        amount = "+$currencySymbol${String.format(Locale.US, "%.2f", job.totalTips)}",
                        valueColor = if (job.totalTips > 0.0) DelivioGreenDark else MaterialTheme.colorScheme.onSurface
                    )

                    HorizontalDivider()
                    BreakdownRow(
                        label = "Total",
                        amount = "$currencySymbol${String.format(Locale.US, "%.2f", job.netEarning)}",
                        isBold = true
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    PaymentBadgeContent(
                        paymentType = job.paymentType,
                        providerLabel = job.paymentProviderLabel,
                        amountToCollect = job.resolvedCodCashCollected,
                        isRequestRider = job.serviceType.lowercase(Locale.US).contains("request"),
                        currencySymbol = currencySymbol,
                        isLarge = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (job.lateTip > 0.0) {
                        val lateTipInt = job.lateTip.toInt()
                        val lateTipStr = if (job.lateTip == lateTipInt.toDouble()) {
                            "$lateTipInt"
                        } else {
                            String.format(Locale.US, "%.2f", job.lateTip)
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = DelivioGreen.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "Late tip +$currencySymbol$lateTipStr included",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = DelivioGreenDark,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (job.serviceType.lowercase(Locale.US).contains("request")) {
                        Text(
                            text = "Delivery fee paid by merchant",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = DelivioGreenDark
                        )
                    }

                    val canViewChat = OrderChatConstants.canViewTripChat(job.serviceType, job.closedAtMillis, nowMillis)
                    if (canViewChat) {
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = {
                                onOpenTripChat(job)
                                selectedJobForDetail = null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("trip_history_view_chat_button")
                        ) {
                            Icon(Icons.Default.ChatBubble, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("View chat", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onReportIssue(job)
                                selectedJobForDetail = null
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                        ) {
                            Text("Report issue", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "Delivio Driver Receipt\n" +
                                            "Trip: ${job.displayOrderNumber}\n" +
                                            "Service: ${job.serviceType}\n" +
                                            "Merchant: ${job.displayMerchantName}\n" +
                                            "Delivery fee: $currencySymbol${String.format(Locale.US, "%.2f", job.deliveryFee)}\n" +
                                            "Pick up fee: $currencySymbol${String.format(Locale.US, "%.2f", job.pickupFee)}\n" +
                                            "Commission fee$commRateSuffix: -$currencySymbol${String.format(Locale.US, "%.2f", job.commissionAmount)}\n" +
                                            "Tip: +$currencySymbol${String.format(Locale.US, "%.2f", job.totalTips)}\n" +
                                            "Total: $currencySymbol${String.format(Locale.US, "%.2f", job.netEarning)}\n" +
                                            "Date: ${job.completedDateText}"
                                    )
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share Receipt"))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DelivioYellow, contentColor = TextOnYellow),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                        ) {
                            Text("Share receipt", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { selectedJobForDetail = null }) {
                    Text("Close")
                }
            }
        )
    }

    // Help Dialog
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text("Earnings Guide", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("How net earnings are calculated:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("• Net earnings = Delivery fees + Pick up fees - Commission fee + Tips + Bonuses.", fontSize = 12.sp)
                    Text("• Pick up fees apply to the first booking of a batch.", fontSize = 12.sp)
                    Text("• Commission rate is based on booking position in a batch and applies to Delivery fee + Pick up fee.", fontSize = 12.sp)
                    Text("• 100% of customer tips go directly to you with zero commission.", fontSize = 12.sp)
                    Text("• COD cash collected is held for remittance and is never counted as earnings.", fontSize = 12.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = { showHelpDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = DelivioYellow, contentColor = TextOnYellow)
                ) {
                    Text("Got it", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Statements Dialog computed from real deliveredOrders + credited claims
    if (showStatementsDialog) {
        val todayJobsNet = remember(allJobs, todayStartMs) {
            allJobs.filter { it.completedAtMillis >= todayStartMs }.sumOf { it.netEarning }
        }
        val weekJobsNet = remember(allJobs, weekStartMs) {
            allJobs.filter { it.completedAtMillis >= weekStartMs }.sumOf { it.netEarning }
        }
        val monthJobsNet = remember(allJobs, monthStartMs) {
            allJobs.filter { it.completedAtMillis >= monthStartMs }.sumOf { it.netEarning }
        }
        val sumClaimsSince: (Long) -> Double = { sinceMs ->
            val q = questClaims
                .filter { isClaimCredited(it.status) && (it.createdAt?.toDate()?.time ?: 0L) >= sinceMs }
                .sumOf { it.rewardAmount }
            val s = sessionClaims
                .filter { isClaimCredited(it.status) && it.createdAtMillis >= sinceMs }
                .sumOf { it.rewardAmount }
            q + s
        }
        val statementRows = listOf(
            "Today" to "$currencySymbol${String.format(Locale.US, "%.2f", todayJobsNet + sumClaimsSince(todayStartMs))}",
            "This Week" to "$currencySymbol${String.format(Locale.US, "%.2f", weekJobsNet + sumClaimsSince(weekStartMs))}",
            "This Month" to "$currencySymbol${String.format(Locale.US, "%.2f", monthJobsNet + sumClaimsSince(monthStartMs))}"
        )

        AlertDialog(
            onDismissRequest = { showStatementsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ReceiptLong, contentDescription = "Statement", tint = DelivioYellowDark)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Earnings Statements", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Export your verified Manila-timezone earnings summary:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    statementRows.forEach { (periodLabel, amountText) ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(periodLabel, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Net Earnings: $amountText", fontSize = 12.sp, color = DelivioYellowDark, fontWeight = FontWeight.Bold)
                                }
                                IconButton(
                                    onClick = {
                                        val sendIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(
                                                Intent.EXTRA_TEXT,
                                                "Delivio Driver Statement ($periodLabel)\nNet Earnings: $amountText"
                                            )
                                            type = "text/plain"
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, "Share Statement"))
                                    },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = "Share $periodLabel statement", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showStatementsDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun JobRowCard(
    job: EarningsJobRecord,
    currencySymbol: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("earnings_job_row_${job.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${job.displayOrderNumber} • ${job.timeText} • ${job.displayMerchantName}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = job.area,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$currencySymbol${String.format(Locale.US, "%.2f", job.netEarning)}",
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        color = DelivioYellowDark
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Trip details",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PaymentBadgeContent(
                    paymentType = job.paymentType,
                    providerLabel = job.paymentProviderLabel,
                    amountToCollect = job.resolvedCodCashCollected,
                    isRequestRider = job.serviceType.lowercase(Locale.US).contains("request"),
                    currencySymbol = currencySymbol,
                    isLarge = false
                )

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = job.serviceType,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                if (job.tip > 0.0) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = DelivioGreen.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "Tip +$currencySymbol${String.format(Locale.US, "%.0f", job.tip)}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = DelivioGreenDark,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                if (job.lateTip > 0.0) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = DelivioGreen.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "Late tip +$currencySymbol${String.format(Locale.US, "%.0f", job.lateTip)}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = DelivioGreenDark,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BreakdownRow(
    label: String,
    amount: String,
    isBold: Boolean = false,
    valueColor: Color = Color.Unspecified
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = if (isBold) 13.sp else 12.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            color = if (isBold) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = amount,
            fontSize = if (isBold) 14.sp else 12.sp,
            fontWeight = if (isBold) FontWeight.Black else FontWeight.SemiBold,
            color = if (valueColor != Color.Unspecified) valueColor else MaterialTheme.colorScheme.onSurface
        )
    }
}
