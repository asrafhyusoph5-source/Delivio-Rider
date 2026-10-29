package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.example.ui.theme.*
import com.example.data.models.PaymentType
import com.example.data.models.parsePaymentType
import com.example.data.models.formatPaymentProvider
import com.example.ui.components.PaymentBadgeContent
import com.example.viewmodel.RiderViewModel

data class EarningsJobRecord(
    val id: String,
    val orderNumber: String,
    val serviceType: String, // "Food", "Instant", "Parcel", "Pabili", "Request", "Merchant Request"
    val merchantName: String,
    val area: String,
    val timeText: String,
    val dayNumber: Int, // 5 to 11
    val dayOfWeek: String, // "Mon", "Tue", etc.
    val baseFare: Double,
    val distanceFare: Double,
    val tip: Double = 0.0,
    val surgeBoost: Double = 0.0,
    val waitingPay: Double = 0.0,
    val extraStopPay: Double = 0.0,
    val returnTripPay: Double = 0.0,
    val urgentBonus: Double = 0.0,
    val platformCommission: Double = 5.0,
    val paymentMethod: String = "cod",
    val paymentProvider: String = "",
    val cashToCollect: Double = 0.0,
    val codCollected: Double = 0.0
) {
    val paymentType: PaymentType
        get() = parsePaymentType(paymentMethod)

    val isCod: Boolean
        get() = paymentType == PaymentType.COD

    val isPaidOnline: Boolean
        get() = paymentType == PaymentType.PAID_ONLINE

    val isPaymentUnknown: Boolean
        get() = paymentType == PaymentType.UNKNOWN

    val paymentProviderLabel: String
        get() = formatPaymentProvider(paymentProvider)

    val netEarning: Double
        get() = baseFare + distanceFare + tip + surgeBoost + waitingPay + extraStopPay + returnTripPay + urgentBonus - platformCommission
}

private val SAMPLE_EARNINGS_JOBS = listOf(
    // Sunday Oct 11 (Today)
    EarningsJobRecord(
        id = "job_101",
        orderNumber = "DLV-8821",
        serviceType = "Request",
        merchantName = "City Health Logistics",
        area = "Rizal Ave to Ospital ng Palawan",
        timeText = "03:45 PM",
        dayNumber = 11,
        dayOfWeek = "Sun",
        baseFare = 70.0,
        distanceFare = 45.0,
        tip = 30.0,
        waitingPay = 25.0,
        extraStopPay = 35.0,
        returnTripPay = 40.0,
        urgentBonus = 50.0,
        platformCommission = 10.0,
        paymentMethod = "cod",
        cashToCollect = 320.0,
        codCollected = 320.0
    ),
    EarningsJobRecord(
        id = "job_102",
        orderNumber = "DLV-7740",
        serviceType = "Food",
        merchantName = "Kalui Restaurant",
        area = "Rizal Ave to Bancao-Bancao",
        timeText = "01:15 PM",
        dayNumber = 11,
        dayOfWeek = "Sun",
        baseFare = 55.0,
        distanceFare = 35.0,
        tip = 20.0,
        surgeBoost = 30.0,
        platformCommission = 5.0,
        paymentMethod = "online",
        paymentProvider = "gcash"
    ),
    EarningsJobRecord(
        id = "job_103",
        orderNumber = "DLV-6612",
        serviceType = "Instant",
        merchantName = "Express Courier Hub",
        area = "San Pedro to Tiniguiban",
        timeText = "11:30 AM",
        dayNumber = 11,
        dayOfWeek = "Sun",
        baseFare = 50.0,
        distanceFare = 30.0,
        tip = 0.0,
        platformCommission = 5.0,
        paymentMethod = "wallet",
        paymentProvider = "wallet"
    ),
    EarningsJobRecord(
        id = "job_104",
        orderNumber = "DLV-9011",
        serviceType = "Taxi",
        merchantName = "Puerto Princesa City Hall",
        area = "City Hall to Airport",
        timeText = "05:10 PM",
        dayNumber = 11,
        dayOfWeek = "Sun",
        baseFare = 150.0,
        distanceFare = 50.0,
        tip = 30.0,
        platformCommission = 30.0, // 15% of 200 fare
        paymentMethod = "cod",
        cashToCollect = 230.0,
        codCollected = 230.0
    ),
    // Saturday Oct 10
    EarningsJobRecord(
        id = "job_201",
        orderNumber = "DLV-5519",
        serviceType = "Food",
        merchantName = "Kinabuchs Grill & Bar",
        area = "Rizal Ave to San Manuel",
        timeText = "08:10 PM",
        dayNumber = 10,
        dayOfWeek = "Sat",
        baseFare = 60.0,
        distanceFare = 40.0,
        tip = 25.0,
        surgeBoost = 40.0,
        platformCommission = 6.0,
        paymentMethod = "cash",
        cashToCollect = 560.0,
        codCollected = 560.0
    ),
    EarningsJobRecord(
        id = "job_202",
        orderNumber = "DLV-5502",
        serviceType = "Pabili",
        merchantName = "Puregold Supermarket",
        area = "Malvar St to Sta. Monica",
        timeText = "04:20 PM",
        dayNumber = 10,
        dayOfWeek = "Sat",
        baseFare = 70.0,
        distanceFare = 45.0,
        tip = 35.0,
        platformCommission = 7.0,
        paymentMethod = "online",
        paymentProvider = "maya"
    ),
    // Friday Oct 9
    EarningsJobRecord(
        id = "job_301",
        orderNumber = "DLV-4411",
        serviceType = "Merchant Request",
        merchantName = "Palawan Bakery Partner",
        area = "Valencia to San Jose",
        timeText = "02:00 PM",
        dayNumber = 9,
        dayOfWeek = "Fri",
        baseFare = 65.0,
        distanceFare = 35.0,
        tip = 15.0,
        extraStopPay = 30.0,
        platformCommission = 5.0,
        paymentMethod = "cod",
        cashToCollect = 450.0,
        codCollected = 450.0
    ),
    EarningsJobRecord(
        id = "job_302",
        orderNumber = "DLV-4390",
        serviceType = "Parcel",
        merchantName = "Rizal Print Center",
        area = "Rizal Ave to Puerto Airport",
        timeText = "10:15 AM",
        dayNumber = 9,
        dayOfWeek = "Fri",
        baseFare = 50.0,
        distanceFare = 25.0,
        tip = 10.0,
        platformCommission = 5.0,
        paymentMethod = "online",
        paymentProvider = "bank"
    ),
    // Thursday Oct 8
    EarningsJobRecord(
        id = "job_401",
        orderNumber = "DLV-3310",
        serviceType = "Food",
        merchantName = "Badjao Seafront",
        area = "Abueg Rd to City Center",
        timeText = "07:30 PM",
        dayNumber = 8,
        dayOfWeek = "Thu",
        baseFare = 60.0,
        distanceFare = 45.0,
        tip = 20.0,
        surgeBoost = 25.0,
        platformCommission = 6.0,
        paymentMethod = "online",
        paymentProvider = "card"
    ),
    // Wednesday Oct 7
    EarningsJobRecord(
        id = "job_501",
        orderNumber = "DLV-2218",
        serviceType = "Request",
        merchantName = "Provincial Capitol Admin",
        area = "Fernandez St to Bgy. Irawan",
        timeText = "01:45 PM",
        dayNumber = 7,
        dayOfWeek = "Wed",
        baseFare = 70.0,
        distanceFare = 55.0,
        tip = 40.0,
        urgentBonus = 50.0,
        platformCommission = 8.0,
        paymentMethod = "unknown",
        paymentProvider = ""
    ),
    // Tuesday Oct 6
    EarningsJobRecord(
        id = "job_601",
        orderNumber = "DLV-1180",
        serviceType = "Instant",
        merchantName = "Palawan Tech Supplies",
        area = "Lacao St to San Pedro",
        timeText = "11:10 AM",
        dayNumber = 6,
        dayOfWeek = "Tue",
        baseFare = 50.0,
        distanceFare = 30.0,
        tip = 15.0,
        platformCommission = 5.0,
        paymentMethod = "cash on delivery",
        cashToCollect = 250.0,
        codCollected = 250.0
    ),
    // Monday Oct 5
    EarningsJobRecord(
        id = "job_701",
        orderNumber = "DLV-0912",
        serviceType = "Food",
        merchantName = "La Terrasse Restaurant",
        area = "Rizal Ave to Bancao-Bancao",
        timeText = "06:50 PM",
        dayNumber = 5,
        dayOfWeek = "Mon",
        baseFare = 55.0,
        distanceFare = 30.0,
        tip = 25.0,
        surgeBoost = 20.0,
        platformCommission = 5.0,
        paymentMethod = "wallet",
        paymentProvider = "wallet"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EarningsScreen(
    viewModel: RiderViewModel,
    onBack: () -> Unit
) {
    val currencySymbol by viewModel.currencySymbol.collectAsState()
    val context = LocalContext.current

    var selectedPeriod by remember { mutableStateOf("Daily") } // "Daily", "Weekly", "Monthly"
    var selectedDayNumber by remember { mutableStateOf(11) } // Default to today (Oct 11)
    var selectedServiceFilter by remember { mutableStateOf("All") }
    var selectedPaymentFilter by remember { mutableStateOf("All") }
    var isBreakdownExpanded by remember { mutableStateOf(false) }

    var selectedJobForDetail by remember { mutableStateOf<EarningsJobRecord?>(null) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showStatementsDialog by remember { mutableStateOf(false) }

    // Filter jobs based on period
    val periodJobs = remember(selectedPeriod, selectedDayNumber) {
        when (selectedPeriod) {
            "Daily" -> SAMPLE_EARNINGS_JOBS.filter { it.dayNumber == selectedDayNumber }
            "Weekly" -> SAMPLE_EARNINGS_JOBS.filter { it.dayNumber in 5..11 }
            else -> SAMPLE_EARNINGS_JOBS // Monthly (all October jobs)
        }
    }

    // Apply service and payment filter
    val filteredJobs = remember(periodJobs, selectedServiceFilter, selectedPaymentFilter) {
        periodJobs.filter { job ->
            val matchesService = when (selectedServiceFilter) {
                "All" -> true
                "Food" -> job.serviceType == "Food"
                "Instant" -> job.serviceType == "Instant"
                "Parcel" -> job.serviceType == "Parcel"
                "Pabili" -> job.serviceType == "Pabili"
                "Request" -> job.serviceType == "Request"
                "Merchant Request" -> job.serviceType == "Merchant Request"
                "Taxi" -> job.serviceType == "Taxi"
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

    // Totals strictly equal the sum of the filtered job list for the period
    val totalNetEarnings = remember(filteredJobs) { filteredJobs.sumOf { it.netEarning } }
    val totalJobsCount = remember(filteredJobs) { filteredJobs.size }
    val totalBaseFare = remember(filteredJobs) { filteredJobs.sumOf { it.baseFare } }
    val totalDistanceFare = remember(filteredJobs) { filteredJobs.sumOf { it.distanceFare } }
    val totalTips = remember(filteredJobs) { filteredJobs.sumOf { it.tip } }
    val totalSurges = remember(filteredJobs) { filteredJobs.sumOf { it.surgeBoost } }
    val totalWaiting = remember(filteredJobs) { filteredJobs.sumOf { it.waitingPay } }
    val totalExtraStops = remember(filteredJobs) { filteredJobs.sumOf { it.extraStopPay } }
    val totalReturnTrip = remember(filteredJobs) { filteredJobs.sumOf { it.returnTripPay } }
    val totalUrgent = remember(filteredJobs) { filteredJobs.sumOf { it.urgentBonus } }
    val totalCommission = remember(filteredJobs) { filteredJobs.sumOf { it.platformCommission } }
    val totalCodCash = remember(filteredJobs) { filteredJobs.filter { it.isCod }.sumOf { it.codCollected } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Earnings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Help Icon Button
                    IconButton(onClick = { showHelpDialog = true }) {
                        Icon(Icons.Default.HelpOutline, contentDescription = "Earnings Help")
                    }
                    // Statements Button
                    TextButton(
                        onClick = { showStatementsDialog = true },
                        modifier = Modifier.padding(end = 6.dp)
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
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Daily / Weekly / Monthly Tab Selector
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
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) DelivioYellow else Color.Transparent)
                                    .clickable { selectedPeriod = period }
                                    .padding(vertical = 10.dp),
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

            // 2. Month label with picker
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedPeriod == "Monthly") "October 2026 (Full Month)" else if (selectedPeriod == "Weekly") "Oct 5 - Oct 11, 2026 (Week 41)" else "October 2026",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = "Calendar", modifier = Modifier.size(14.dp), tint = DelivioYellowDark)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Palawan Fleet", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // 3. Date strip (Only visible when Daily selector is active)
            if (selectedPeriod == "Daily") {
                item {
                    val daysList = listOf(
                        Pair("Mon", 5),
                        Pair("Tue", 6),
                        Pair("Wed", 7),
                        Pair("Thu", 8),
                        Pair("Fri", 9),
                        Pair("Sat", 10),
                        Pair("Sun", 11)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(daysList) { (dayName, dayNum) ->
                            val isSelected = selectedDayNumber == dayNum
                            Surface(
                                onClick = { selectedDayNumber = dayNum },
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) Color(0xFFFFC107) else MaterialTheme.colorScheme.surface,
                                border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.width(56.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = dayName,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) TextOnYellow else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "$dayNum",
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

            // 4. Total Net Earnings Card with Jobs Completed & Expandable breakdown
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column {
                                Text(
                                    text = "TOTAL NET EARNINGS",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "$currencySymbol${String.format("%.2f", totalNetEarnings)}",
                                    fontSize = 30.sp,
                                    fontWeight = FontWeight.Black,
                                    color = DelivioYellowDark
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = DelivioYellowLight
                            ) {
                                Text(
                                    text = "$totalJobsCount Jobs Completed",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp,
                                    color = TextOnYellow,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }

                        // Separate COD cash collected note (not counted in net earnings)
                        if (totalCodCash > 0) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.LocalAtm, contentDescription = "COD", tint = DelivioYellowDark, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "COD Cash Collected: $currencySymbol${String.format("%.2f", totalCodCash)} (Handled separately in Wallet, not driver earnings)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))

                        // Expandable breakdown toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isBreakdownExpanded = !isBreakdownExpanded }
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isBreakdownExpanded) "Hide Earnings Breakdown" else "View Detailed Earnings Breakdown",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = DelivioYellowDark
                            )
                            Icon(
                                imageVector = if (isBreakdownExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "Toggle Breakdown",
                                tint = DelivioYellowDark
                            )
                        }

                        AnimatedVisibility(visible = isBreakdownExpanded) {
                            Column(
                                modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                BreakdownRow("Base Fare", "$currencySymbol${String.format("%.2f", totalBaseFare)}")
                                BreakdownRow("Distance Fare", "$currencySymbol${String.format("%.2f", totalDistanceFare)}")
                                if (totalTips > 0) BreakdownRow("Tips (100% to Driver)", "$currencySymbol${String.format("%.2f", totalTips)}", valueColor = DelivioGreenDark)
                                if (totalSurges > 0) BreakdownRow("Surges & Demand Boosts", "$currencySymbol${String.format("%.2f", totalSurges)}", valueColor = DelivioYellowDark)
                                if (totalWaiting > 0) BreakdownRow("Waiting Time Pay", "$currencySymbol${String.format("%.2f", totalWaiting)}")
                                if (totalExtraStops > 0) BreakdownRow("Multi-Drop Extra Stop Pay", "$currencySymbol${String.format("%.2f", totalExtraStops)}")
                                if (totalReturnTrip > 0) BreakdownRow("Return Trip Pay", "$currencySymbol${String.format("%.2f", totalReturnTrip)}")
                                if (totalUrgent > 0) BreakdownRow("Urgent Dispatch Bonus", "$currencySymbol${String.format("%.2f", totalUrgent)}", valueColor = DelivioRed)
                                BreakdownRow("Platform Commission", "-$currencySymbol${String.format("%.2f", totalCommission)}", valueColor = DelivioRed)
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                BreakdownRow("Total Net Driver Pay", "$currencySymbol${String.format("%.2f", totalNetEarnings)}", isBold = true)
                            }
                        }
                    }
                }
            }

            // 5. Service Filter Chips
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Service Type Filter:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val services = listOf("All", "Food", "Instant", "Parcel", "Pabili", "Request", "Merchant Request", "Taxi")
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

            // 6. Payment Filter Chips
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Payment Method:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

            // 7. Completed Jobs Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Trip History ($totalJobsCount jobs)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Sum: $currencySymbol${String.format("%.2f", totalNetEarnings)}",
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
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Inbox, contentDescription = "Empty", tint = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No trips found for this filter.", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Try switching date or clearing service filters.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                items(filteredJobs) { job ->
                    JobRowCard(
                        job = job,
                        currencySymbol = currencySymbol,
                        onClick = { selectedJobForDetail = job }
                    )
                }
            }
        }
    }

    // Job Detail Sheet / Dialog
    selectedJobForDetail?.let { job ->
        AlertDialog(
            onDismissRequest = { selectedJobForDetail = null },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Job #${job.orderNumber}", fontWeight = FontWeight.Bold)
                    Surface(shape = RoundedCornerShape(8.dp), color = DelivioYellowLight) {
                        Text(job.serviceType, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextOnYellow, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${job.merchantName} • ${job.area}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("Completed at ${job.timeText}, Oct ${job.dayNumber}, 2026", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider()

                    BreakdownRow("Base Fare", "$currencySymbol${String.format("%.2f", job.baseFare)}")
                    BreakdownRow("Distance Fare", "$currencySymbol${String.format("%.2f", job.distanceFare)}")
                    if (job.tip > 0) BreakdownRow("Tip", "$currencySymbol${String.format("%.2f", job.tip)}", valueColor = DelivioGreenDark)
                    if (job.surgeBoost > 0) BreakdownRow("Surge Boost", "$currencySymbol${String.format("%.2f", job.surgeBoost)}", valueColor = DelivioYellowDark)
                    if (job.waitingPay > 0) BreakdownRow("Waiting Pay", "$currencySymbol${String.format("%.2f", job.waitingPay)}")
                    if (job.extraStopPay > 0) BreakdownRow("Extra Stop Pay", "$currencySymbol${String.format("%.2f", job.extraStopPay)}")
                    if (job.returnTripPay > 0) BreakdownRow("Return Trip Pay", "$currencySymbol${String.format("%.2f", job.returnTripPay)}")
                    if (job.urgentBonus > 0) BreakdownRow("Urgent Bonus", "$currencySymbol${String.format("%.2f", job.urgentBonus)}", valueColor = DelivioRed)
                    BreakdownRow("Platform Commission", "-$currencySymbol${String.format("%.2f", job.platformCommission)}", valueColor = DelivioRed)
                    HorizontalDivider()
                    BreakdownRow("Net Driver Pay", "$currencySymbol${String.format("%.2f", job.netEarning)}", isBold = true)

                    Spacer(modifier = Modifier.height(6.dp))
                    PaymentBadgeContent(
                        paymentType = job.paymentType,
                        providerLabel = job.paymentProviderLabel,
                        amountToCollect = if (job.isCod) (if (job.codCollected > 0) job.codCollected else job.cashToCollect) else 0.0,
                        isRequestRider = job.serviceType.lowercase().contains("request"),
                        currencySymbol = currencySymbol,
                        isLarge = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (job.serviceType.lowercase().contains("request")) {
                        Text(
                            text = "Delivery Fee: Paid by merchant",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = DelivioGreenDark
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                viewModel.showToast("Incident report opened for #${job.orderNumber}")
                                selectedJobForDetail = null
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Report an issue", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, "Delivio Driver Receipt\nJob: #${job.orderNumber}\nService: ${job.serviceType}\nMerchant: ${job.merchantName}\nNet Earning: ₱${job.netEarning}\nDelivered on Oct ${job.dayNumber}, 2026")
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Download/Share Receipt"))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DelivioYellow, contentColor = TextOnYellow),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Download receipt", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
            title = { Text("Earnings & Pay Guide", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("How driver pay is computed:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("• Net Earnings = Base Fare + Distance Fare + Tips + Surge/Boosts + Waiting Pay + Extra Stop Pay + Return Trip Pay + Urgent Bonus - Platform Commission.", fontSize = 12.sp)
                    Text("• COD Collected: Cash received from customers is recorded as Cash-on-Hand in your Wallet and must be remitted. It is not counted as net trip earnings.", fontSize = 12.sp)
                    Text("• 100% of customer tips are paid directly to the driver with zero deductions.", fontSize = 12.sp)
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

    // Statements Dialog (CSV / PDF download)
    if (showStatementsDialog) {
        AlertDialog(
            onDismissRequest = { showStatementsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ReceiptLong, contentDescription = "Statement", tint = DelivioYellowDark)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Weekly Statements", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Download or share verified statements for tax, bank financing, or fleet records:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    listOf(
                        Pair("Oct 5 - Oct 11, 2026", "$currencySymbol${String.format("%.2f", totalNetEarnings)}"),
                        Pair("Sep 28 - Oct 04, 2026", "$currencySymbol 4,890.00"),
                        Pair("Sep 21 - Sep 27, 2026", "$currencySymbol 5,120.00")
                    ).forEach { (periodText, amount) ->
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
                                    Text(periodText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Net Payout: $amount", fontSize = 12.sp, color = DelivioYellowDark, fontWeight = FontWeight.Bold)
                                }
                                Row {
                                    IconButton(onClick = {
                                        val sendIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_TEXT, "Delivio Statement ($periodText)\nNet Driver Earnings: $amount\nPalawan Fleet • Verified")
                                            type = "text/plain"
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, "Download Statement (CSV)"))
                                    }) {
                                        Icon(Icons.Default.Download, contentDescription = "CSV", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    }
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
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${job.timeText} • ${job.merchantName}",
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
                        text = "$currencySymbol${String.format("%.2f", job.netEarning)}",
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        color = DelivioYellowDark
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Details",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Chips: "Paid Online" or "COD", service badge, "Tip included" (only when tip > 0), "Boost" when applicable
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Payment chip
                PaymentBadgeContent(
                    paymentType = job.paymentType,
                    providerLabel = job.paymentProviderLabel,
                    amountToCollect = if (job.isCod) (if (job.codCollected > 0) job.codCollected else job.cashToCollect) else 0.0,
                    isRequestRider = job.serviceType.lowercase().contains("request"),
                    currencySymbol = currencySymbol,
                    isLarge = false
                )

                // Service badge
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

                // Tip included (ONLY when a tip exists)
                if (job.tip > 0) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = DelivioGreen.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "Tip included (+$currencySymbol${String.format("%.0f", job.tip)})",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = DelivioGreenDark,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Boost when applicable
                if (job.surgeBoost > 0 || job.urgentBonus > 0 || job.returnTripPay > 0) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = DelivioYellow.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "Boost",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = DelivioYellowDark,
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
