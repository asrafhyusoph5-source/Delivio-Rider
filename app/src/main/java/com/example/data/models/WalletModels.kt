package com.example.data.models

import com.example.data.constants.OrderStepConstants
import com.google.firebase.firestore.FieldValue
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class PendingCodLedgerEntry(
    val orderId: String,
    val riderId: String,
    val orderNumber: String,
    val amount: Double,
    val createdAtMillis: Long = System.currentTimeMillis()
)

enum class WalletFilter(val label: String) {
    ALL("All"),
    CREDITS("Credits"),
    DEBITS("Debits")
}

data class WalletDayGroup(
    val dayKey: String,
    val dayLabel: String,
    val transactions: List<WalletTransaction>
)

object WalletLedgerHelper {
    const val COD_PAUSED_REASON = "COD orders are paused until your wallet is 0 or more"
    const val DEFAULT_TIMEZONE_ID = "Asia/Manila"

    fun resolveTimeZone(timezoneId: String?): TimeZone {
        val raw = timezoneId?.trim().orEmpty()
        if (raw.isEmpty()) return TimeZone.getTimeZone(DEFAULT_TIMEZONE_ID)
        val tz = TimeZone.getTimeZone(raw)
        // TimeZone.getTimeZone falls back to GMT if unknown unless raw is GMT/UTC
        return if (tz.id == "GMT" && !raw.equals("GMT", ignoreCase = true) && !raw.equals("UTC", ignoreCase = true)) {
            TimeZone.getTimeZone(DEFAULT_TIMEZONE_ID)
        } else {
            tz
        }
    }

    fun resolveZoneId(timezoneId: String?): ZoneId {
        return try {
            ZoneId.of(resolveTimeZone(timezoneId).id)
        } catch (_: Throwable) {
            ZoneId.of(DEFAULT_TIMEZONE_ID)
        }
    }

    fun deliveryEarningDocId(orderId: String): String = "rider_${orderId}_delivery_earning"

    fun codCollectedDocId(orderId: String): String = "rider_${orderId}_cod_collected"

    fun isCodOrder(order: Order): Boolean {
        val normMethod = order.paymentMethod.trim().lowercase(Locale.US)
        return order.isCod || normMethod == "cod" || normMethod == "cash"
    }

    fun isCodBlockedForWallet(walletBalance: Double): Boolean = walletBalance < 0.0

    fun formatOrderNumberTag(orderNumber: String, orderId: String): String {
        val raw = orderNumber.trim()
        return when {
            raw.isNotBlank() -> if (raw.startsWith("#")) raw else "#$raw"
            orderId.isNotBlank() -> "#" + orderId.takeLast(6).uppercase(Locale.US)
            else -> "#000000"
        }
    }

    /**
     * Computes the trip's net delivery fee after commission (deliveryFee + pickupFee - commissionAmount).
     * Never includes customer tips.
     */
    fun computeNetDeliveryFeeForOrder(
        order: Order,
        quote: RiderQuote? = order.riderQuote,
        earningTotalFallback: Double = 0.0
    ): Double {
        val raw = when {
            quote != null -> (quote.deliveryFee + quote.pickupFee - quote.commissionAmount).coerceAtLeast(0.0)
            earningTotalFallback > 0.0 -> (earningTotalFallback - order.tip).coerceAtLeast(0.0)
            else -> (order.estimatedEarnings - order.tip - order.lateTipAmount).coerceAtLeast(0.0)
        }
        return PricingEngine.round2(raw)
    }

    /**
     * Ensures the delivery earning ledger entry satisfies `/firestore.rules`:
     * `amount > 0 && amount <= order.fees.total + 0.01`.
     * Returns null if there is no positive amount or if `fees.total` on Firestore is <= 0.
     */
    fun resolveRuleCompliantEarningAmount(netDeliveryFee: Double, firestoreFeesTotal: Double): Double? {
        val rounded = PricingEngine.round2(netDeliveryFee)
        if (rounded <= 0.0) return null
        if (firestoreFeesTotal <= 0.0) return null
        val capped = if (rounded > firestoreFeesTotal + 0.01) {
            PricingEngine.round2(firestoreFeesTotal)
        } else {
            rounded
        }
        return capped.takeIf { it > 0.0 }
    }

    fun buildDeliveryEarningPayload(
        riderId: String,
        orderId: String,
        orderNumber: String,
        amount: Double
    ): Map<String, Any> {
        val rounded = PricingEngine.round2(amount)
        val tag = formatOrderNumberTag(orderNumber, orderId)
        return mapOf(
            "riderId" to riderId,
            "orderId" to orderId,
            "kind" to "delivery_earning",
            "amount" to rounded,
            "signedAmount" to rounded,
            "type" to "delivery_credit",
            "title" to "Delivery earning - $tag",
            "createdAt" to FieldValue.serverTimestamp()
        )
    }

    fun buildCodCollectedPayload(
        riderId: String,
        orderId: String,
        orderNumber: String,
        cashCollected: Double
    ): Map<String, Any> {
        val rounded = PricingEngine.round2(cashCollected)
        val tag = formatOrderNumberTag(orderNumber, orderId)
        return mapOf(
            "riderId" to riderId,
            "orderId" to orderId,
            "kind" to "cod_collected",
            "amount" to rounded,
            "signedAmount" to -rounded,
            "type" to "cod_deduction",
            "title" to "COD collected - $tag",
            "createdAt" to FieldValue.serverTimestamp()
        )
    }

    fun buildUserWalletUpdate(signedDelta: Double, ledgerDocId: String): Map<String, Any> {
        return mapOf(
            "walletBalance" to FieldValue.increment(PricingEngine.round2(signedDelta)),
            "lastLedgerId" to ledgerDocId
        )
    }

    fun formatTransactionTime(dateRaw: Long, timezoneId: String? = DEFAULT_TIMEZONE_ID): String {
        if (dateRaw <= 0L) return "Recent"
        val sdf = SimpleDateFormat("h:mm a", Locale.US).apply {
            timeZone = resolveTimeZone(timezoneId)
        }
        return sdf.format(Date(dateRaw))
    }

    fun groupTransactionsByDay(
        transactions: List<WalletTransaction>,
        filter: WalletFilter = WalletFilter.ALL,
        timezoneId: String? = DEFAULT_TIMEZONE_ID,
        nowMillis: Long = System.currentTimeMillis()
    ): List<WalletDayGroup> {
        val tz = resolveTimeZone(timezoneId)
        val filtered = when (filter) {
            WalletFilter.ALL -> transactions
            WalletFilter.CREDITS -> transactions.filter { it.isPositive }
            WalletFilter.DEBITS -> transactions.filter { !it.isPositive }
        }.sortedByDescending { it.dateRaw }

        if (filtered.isEmpty()) return emptyList()

        val keyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = tz }
        val labelFmt = SimpleDateFormat("MMM d, yyyy", Locale.US).apply { timeZone = tz }
        val todayKey = keyFmt.format(Date(nowMillis))
        val yesterdayCal = Calendar.getInstance(tz, Locale.US).apply {
            timeInMillis = nowMillis
            add(Calendar.DAY_OF_MONTH, -1)
        }
        val yesterdayKey = keyFmt.format(yesterdayCal.time)

        val grouped = LinkedHashMap<String, MutableList<WalletTransaction>>()
        for (tx in filtered) {
            val dayKey = if (tx.dateRaw > 0L) keyFmt.format(Date(tx.dateRaw)) else "recent"
            grouped.getOrPut(dayKey) { mutableListOf() }.add(tx)
        }

        return grouped.entries.map { (dayKey, items) ->
            val label = when (dayKey) {
                todayKey -> "Today"
                yesterdayKey -> "Yesterday"
                "recent" -> "Recent"
                else -> {
                    val sampleMs = items.firstOrNull { it.dateRaw > 0L }?.dateRaw ?: 0L
                    if (sampleMs > 0L) labelFmt.format(Date(sampleMs)) else dayKey
                }
            }
            WalletDayGroup(dayKey = dayKey, dayLabel = label, transactions = items)
        }
    }
}

data class TripRecord(
    val id: String,
    val orderNumber: String,
    val serviceType: String,
    val merchantName: String,
    val area: String,
    val timeText: String,
    val completedDateText: String,
    val completedAtMillis: Long,
    val deliveryIncomeAfterCommission: Double,
    val tip: Double = 0.0,
    val lateTip: Double = 0.0,
    val totalIncome: Double,
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
        get() = PricingEngine.round2(tip + lateTip)

    val resolvedCodCashCollected: Double
        get() = if (!isCod) 0.0 else when {
            codCollected > 0.0 -> codCollected
            cashToCollect > 0.0 -> cashToCollect
            else -> 0.0
        }

    val displayOrderNumber: String
        get() = WalletLedgerHelper.formatOrderNumberTag(orderNumber, id)
}

data class EarningsPeriodSummary(
    val deliveryFees: Double = 0.0,
    val tips: Double = 0.0,
    val bonuses: Double = 0.0,
    val totalIncome: Double = 0.0,
    val jobsCount: Int = 0,
    val trips: List<TripRecord> = emptyList()
)

enum class IncomeHistoryTab(val label: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly")
}

data class IncomeHistoryRow(
    val id: String,
    val year: Int,
    val title: String,
    val subtitle: String,
    val sortKeyMillis: Long,
    val isInProgress: Boolean,
    val summary: EarningsPeriodSummary
)

object EarningsHistoryCalculator {

    private val CREDITED_CLAIM_STATUSES = setOf("credited", "approved", "paid", "settled")

    fun resolveOrderTimestampMillis(order: Order): Long {
        val delivered = order.deliveredAtMillis
        if (delivered > 0L) return delivered
        val completed = OrderDocumentParser.parseTolerantTimeMillisOrZero(order.completedAt)
        if (completed > 0L) return completed
        val updated = OrderDocumentParser.parseTolerantTimeMillisOrZero(order.updatedAt)
        if (updated > 0L) return updated
        val accepted = order.acceptedAtMillis
        if (accepted > 0L) return accepted
        return OrderChatConstants.getOrderClosedTimeMillis(order) ?: 0L
    }

    fun isClaimCredited(status: String): Boolean {
        return status.trim().lowercase(Locale.US) in CREDITED_CLAIM_STATUSES
    }

    fun buildTripRecord(order: Order, timezoneId: String? = WalletLedgerHelper.DEFAULT_TIMEZONE_ID): TripRecord {
        val tz = WalletLedgerHelper.resolveTimeZone(timezoneId)
        val completedMs = resolveOrderTimestampMillis(order)
        val safeMs = if (completedMs > 0L) completedMs else System.currentTimeMillis()
        val timeFmt = SimpleDateFormat("h:mm a", Locale.US).apply { timeZone = tz }
        val dateFmt = SimpleDateFormat("MMM d, yyyy", Locale.US).apply { timeZone = tz }

        val totalTips = PricingEngine.round2(order.tip + order.lateTipAmount)
        val totalIncome = PricingEngine.round2(order.estimatedEarnings)
        val deliveryAfterComm = PricingEngine.round2((totalIncome - totalTips).coerceAtLeast(0.0))

        return TripRecord(
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
            deliveryIncomeAfterCommission = deliveryAfterComm,
            tip = order.tip,
            lateTip = order.lateTipAmount,
            totalIncome = totalIncome,
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

    private fun extractCreditedBonuses(
        questClaims: List<QuestClaim>,
        sessionClaims: List<SessionClaim>
    ): List<Pair<Long, Double>> {
        val q = questClaims
            .filter { isClaimCredited(it.status) && it.rewardAmount > 0.0 }
            .map { (it.createdAt?.toDate()?.time ?: 0L) to it.rewardAmount }
        val s = sessionClaims
            .filter { isClaimCredited(it.status) && it.rewardAmount > 0.0 }
            .map { it.createdAtMillis to it.rewardAmount }
        return (q + s).filter { it.first > 0L }
    }

    private fun buildSummary(
        trips: List<TripRecord>,
        bonuses: Double
    ): EarningsPeriodSummary {
        val deliveryFees = PricingEngine.round2(trips.sumOf { it.deliveryIncomeAfterCommission })
        val tips = PricingEngine.round2(trips.sumOf { it.totalTips })
        val roundedBonuses = PricingEngine.round2(bonuses.coerceAtLeast(0.0))
        val totalIncome = PricingEngine.round2(deliveryFees + tips + roundedBonuses)
        return EarningsPeriodSummary(
            deliveryFees = deliveryFees,
            tips = tips,
            bonuses = roundedBonuses,
            totalIncome = totalIncome,
            jobsCount = trips.size,
            trips = trips.sortedByDescending { it.completedAtMillis }
        )
    }

    fun computeTodaySummary(
        deliveredOrders: List<Order>,
        questClaims: List<QuestClaim>,
        sessionClaims: List<SessionClaim>,
        timezoneId: String? = WalletLedgerHelper.DEFAULT_TIMEZONE_ID,
        nowMillis: Long = System.currentTimeMillis()
    ): EarningsPeriodSummary {
        val zoneId = WalletLedgerHelper.resolveZoneId(timezoneId)
        val todayDate = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        val allTrips = deliveredOrders
            .distinctBy { it.id }
            .map { buildTripRecord(it, timezoneId) }
        val todayTrips = allTrips.filter { trip ->
            trip.completedAtMillis > 0L &&
                Instant.ofEpochMilli(trip.completedAtMillis).atZone(zoneId).toLocalDate() == todayDate
        }
        val todayBonuses = extractCreditedBonuses(questClaims, sessionClaims)
            .filter { (ts, _) ->
                Instant.ofEpochMilli(ts).atZone(zoneId).toLocalDate() == todayDate
            }
            .sumOf { it.second }
        return buildSummary(todayTrips, todayBonuses)
    }

    fun buildHistoryRows(
        tab: IncomeHistoryTab,
        deliveredOrders: List<Order>,
        questClaims: List<QuestClaim>,
        sessionClaims: List<SessionClaim>,
        timezoneId: String? = WalletLedgerHelper.DEFAULT_TIMEZONE_ID,
        nowMillis: Long = System.currentTimeMillis()
    ): List<IncomeHistoryRow> {
        val zoneId = WalletLedgerHelper.resolveZoneId(timezoneId)
        val todayDate = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        val allTrips = deliveredOrders
            .distinctBy { it.id }
            .map { buildTripRecord(it, timezoneId) }
            .filter { it.completedAtMillis > 0L }
        val allBonuses = extractCreditedBonuses(questClaims, sessionClaims)

        return when (tab) {
            IncomeHistoryTab.DAILY -> {
                val tripsByDay = allTrips.groupBy {
                    Instant.ofEpochMilli(it.completedAtMillis).atZone(zoneId).toLocalDate()
                }
                val bonusesByDay = allBonuses.groupBy(
                    keySelector = { Instant.ofEpochMilli(it.first).atZone(zoneId).toLocalDate() },
                    valueTransform = { it.second }
                )
                val allDays = (tripsByDay.keys + bonusesByDay.keys + todayDate).toSortedSet(compareByDescending { it })
                allDays.map { date ->
                    val dayTrips = tripsByDay[date].orEmpty()
                    val dayBonuses = bonusesByDay[date].orEmpty().sum()
                    val isToday = date == todayDate
                    val dow = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US)
                    val mon = date.month.getDisplayName(TextStyle.SHORT, Locale.US)
                    val startMs = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
                    IncomeHistoryRow(
                        id = "day_$date",
                        year = date.year,
                        title = "$dow, $mon ${date.dayOfMonth}",
                        subtitle = if (dayTrips.isEmpty()) "No completed trips" else "${dayTrips.size} ${if (dayTrips.size == 1) "trip" else "trips"}",
                        sortKeyMillis = startMs,
                        isInProgress = isToday,
                        summary = buildSummary(dayTrips, dayBonuses)
                    )
                }
            }

            IncomeHistoryTab.WEEKLY -> {
                val wf = WeekFields.ISO
                data class WeekKey(val weekYear: Int, val weekNum: Int) : Comparable<WeekKey> {
                    override fun compareTo(other: WeekKey): Int {
                        val yCmp = other.weekYear.compareTo(weekYear)
                        return if (yCmp != 0) yCmp else other.weekNum.compareTo(weekNum)
                    }
                }

                fun weekKeyFor(ms: Long): WeekKey {
                    val d = Instant.ofEpochMilli(ms).atZone(zoneId).toLocalDate()
                    return WeekKey(d.get(wf.weekBasedYear()), d.get(wf.weekOfWeekBasedYear()))
                }

                val currentWeek = weekKeyFor(nowMillis)
                val tripsByWeek = allTrips.groupBy { weekKeyFor(it.completedAtMillis) }
                val bonusesByWeek = allBonuses.groupBy(
                    keySelector = { weekKeyFor(it.first) },
                    valueTransform = { it.second }
                )
                val allWeeks = (tripsByWeek.keys + bonusesByWeek.keys + currentWeek).toSortedSet()
                allWeeks.map { wk ->
                    val weekTrips = tripsByWeek[wk].orEmpty()
                    val weekBonuses = bonusesByWeek[wk].orEmpty().sum()
                    val monday = todayDate
                        .with(wf.weekBasedYear(), wk.weekYear.toLong())
                        .with(wf.weekOfWeekBasedYear(), wk.weekNum.toLong())
                        .with(wf.dayOfWeek(), 1L)
                    val sunday = monday.plusDays(6)
                    val startMonStr = "${monday.month.getDisplayName(TextStyle.SHORT, Locale.US)} ${monday.dayOfMonth}"
                    val endSunStr = "${sunday.month.getDisplayName(TextStyle.SHORT, Locale.US)} ${sunday.dayOfMonth}"
                    val startMs = monday.atStartOfDay(zoneId).toInstant().toEpochMilli()
                    IncomeHistoryRow(
                        id = "week_${wk.weekYear}_${wk.weekNum}",
                        year = wk.weekYear,
                        title = "Week ${wk.weekNum}",
                        subtitle = "$startMonStr - $endSunStr",
                        sortKeyMillis = startMs,
                        isInProgress = wk == currentWeek,
                        summary = buildSummary(weekTrips, weekBonuses)
                    )
                }
            }

            IncomeHistoryTab.MONTHLY -> {
                data class MonthKey(val year: Int, val monthValue: Int) : Comparable<MonthKey> {
                    override fun compareTo(other: MonthKey): Int {
                        val yCmp = other.year.compareTo(year)
                        return if (yCmp != 0) yCmp else other.monthValue.compareTo(monthValue)
                    }
                }

                fun monthKeyFor(ms: Long): MonthKey {
                    val d = Instant.ofEpochMilli(ms).atZone(zoneId).toLocalDate()
                    return MonthKey(d.year, d.monthValue)
                }

                val currentMonth = MonthKey(todayDate.year, todayDate.monthValue)
                val tripsByMonth = allTrips.groupBy { monthKeyFor(it.completedAtMillis) }
                val bonusesByMonth = allBonuses.groupBy(
                    keySelector = { monthKeyFor(it.first) },
                    valueTransform = { it.second }
                )
                val allMonths = (tripsByMonth.keys + bonusesByMonth.keys + currentMonth).toSortedSet()
                allMonths.map { mk ->
                    val monthTrips = tripsByMonth[mk].orEmpty()
                    val monthBonuses = bonusesByMonth[mk].orEmpty().sum()
                    val firstDay = java.time.LocalDate.of(mk.year, mk.monthValue, 1)
                    val monthName = firstDay.month.getDisplayName(TextStyle.FULL, Locale.US)
                    val startMs = firstDay.atStartOfDay(zoneId).toInstant().toEpochMilli()
                    IncomeHistoryRow(
                        id = "month_${mk.year}_${mk.monthValue}",
                        year = mk.year,
                        title = monthName,
                        subtitle = if (monthTrips.isEmpty()) "No completed trips" else "${monthTrips.size} ${if (monthTrips.size == 1) "trip" else "trips"}",
                        sortKeyMillis = startMs,
                        isInProgress = mk == currentMonth,
                        summary = buildSummary(monthTrips, monthBonuses)
                    )
                }
            }
        }
    }
}
