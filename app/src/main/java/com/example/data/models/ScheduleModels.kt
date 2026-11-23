package com.example.data.models

import com.example.data.constants.OrderStepConstants
import com.example.data.repository.UserPreferencesRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToLong

enum class BookedSessionStatus(val label: String) {
    UPCOMING("Upcoming"),
    LIVE_NOW("Live now"),
    COMPLETED("Completed")
}

data class SessionRewards(
    val emoji: String = "",
    val perDelivery: Double = 0.0,
    val streakEvery: Int = 0,
    val streakBonus: Double = 0.0,
    val completionTarget: Int = 0,
    val completionBonus: Double = 0.0,
    val minEarnings: Double = 0.0,
    val multiplier: Double = 0.0,
    val coins: Int = 0,
    val onlineBonus: Double = 0.0,
    val onlineMinPercent: Int = 0,
    val services: List<String> = emptyList(),
    val note: String = ""
) {
    val hasRewards: Boolean
        get() = emoji.trim().isNotEmpty()
}

data class ScheduleSession(
    val id: String = "",
    val title: String = "",
    val cityId: String = "",
    val startAtMillis: Long = 0L,
    val endAtMillis: Long = 0L,
    val slots: Int = 0,
    val bookedCount: Int = 0,
    val status: String = "open", // "open" | "closed" | "cancelled"
    val vehicleTypes: List<String> = emptyList(),
    val bookingCloseMin: Int = 30,
    val cancelCutoffMin: Int = 120,
    val rewards: SessionRewards = SessionRewards()
) {
    val remainingSlots: Int
        get() = (slots - bookedCount).coerceAtLeast(0)

    val isFull: Boolean
        get() = slots <= 0 || bookedCount >= slots

    fun isBookingClosed(nowMillis: Long): Boolean {
        val closeMs = bookingCloseMin.coerceAtLeast(0) * 60_000L
        return nowMillis >= (startAtMillis - closeMs)
    }

    fun isTooLateToChange(nowMillis: Long): Boolean {
        if (nowMillis >= startAtMillis) return true
        val cutoffMs = cancelCutoffMin.coerceAtLeast(0) * 60_000L
        return nowMillis >= (startAtMillis - cutoffMs)
    }

    fun statusAt(nowMillis: Long): BookedSessionStatus {
        return when {
            nowMillis < startAtMillis -> BookedSessionStatus.UPCOMING
            nowMillis < endAtMillis -> BookedSessionStatus.LIVE_NOW
            else -> BookedSessionStatus.COMPLETED
        }
    }
}

data class SessionBooking(
    val sessionId: String = "",
    val riderId: String = "",
    val riderName: String = "",
    val startAtMillis: Long = 0L,
    val endAtMillis: Long = 0L,
    val bookedAtMillis: Long = 0L
)

data class AvailableSessionItem(
    val session: ScheduleSession,
    val disableReason: String? = null
) {
    val isEnabled: Boolean get() = disableReason == null
}

data class BookedSessionItem(
    val session: ScheduleSession,
    val booking: SessionBooking,
    val claim: SessionClaim? = null
)

data class HiddenSessionDebugEntry(
    val sessionId: String,
    val label: String,
    val reason: String
)

data class ScheduleFilterResult(
    val loadedCount: Int = 0,
    val openCount: Int = 0,
    val myCityCount: Int = 0,
    val myVehicleCount: Int = 0,
    val bookableCount: Int = 0,
    val availableDisplayed: List<AvailableSessionItem> = emptyList(),
    val bookedDisplayed: List<BookedSessionItem> = emptyList(),
    val hiddenReasons: List<HiddenSessionDebugEntry> = emptyList()
) {
    val summaryLine: String
        get() = "Loaded $loadedCount sessions - open $openCount - my city $myCityCount - my vehicle $myVehicleCount - bookable $bookableCount"
}

data class SessionClaimMetrics(
    val deliveries: Int,
    val earned: Double,
    val onlineMinutes: Int
)

object ScheduleSessionParser {

    fun fromMap(id: String, data: Map<String, Any?>?): Result<ScheduleSession> {
        if (data == null) return Result.failure(IllegalArgumentException("Empty document data"))
        return try {
            val startAtMillis = OrderDocumentParser.parseTolerantTimeMillis(
                raw = data["startAt"],
                fieldName = "startAt"
            ) ?: 0L
            val endAtMillis = OrderDocumentParser.parseTolerantTimeMillis(
                raw = data["endAt"],
                fieldName = "endAt"
            ) ?: 0L
            if (startAtMillis <= 0L || endAtMillis <= 0L || endAtMillis <= startAtMillis) {
                return Result.failure(
                    IllegalArgumentException("Invalid startAt/endAt range ($startAtMillis..$endAtMillis)")
                )
            }

            val title = OrderDocumentParser.safeString(data["title"])
            val cityId = OrderDocumentParser.safeString(data["cityId"]).trim()
            val slots = (OrderDocumentParser.safeInt(data["slots"]) ?: 0).coerceAtLeast(0)
            val bookedCount = (OrderDocumentParser.safeInt(data["bookedCount"]) ?: 0).coerceAtLeast(0)
            val status = OrderDocumentParser.safeString(data["status"], "open").trim().lowercase(Locale.US)
                .ifBlank { "open" }

            val vehicleTypes = parseStringList(data["vehicleTypes"])
            val bookingCloseMin = (OrderDocumentParser.safeInt(data["bookingCloseMin"]) ?: 30).coerceAtLeast(0)
            val cancelCutoffMin = (OrderDocumentParser.safeInt(data["cancelCutoffMin"]) ?: 120).coerceAtLeast(0)

            val rewardsMap = data["rewards"] as? Map<*, *>
            val rewards = parseRewards(rewardsMap)

            Result.success(
                ScheduleSession(
                    id = id,
                    title = title,
                    cityId = cityId,
                    startAtMillis = startAtMillis,
                    endAtMillis = endAtMillis,
                    slots = slots,
                    bookedCount = bookedCount,
                    status = status,
                    vehicleTypes = vehicleTypes,
                    bookingCloseMin = bookingCloseMin,
                    cancelCutoffMin = cancelCutoffMin,
                    rewards = rewards
                )
            )
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    fun bookingFromMap(sessionId: String, data: Map<String, Any?>?): SessionBooking? {
        if (data == null) return null
        return try {
            val riderId = OrderDocumentParser.safeString(data["riderId"])
            val riderName = OrderDocumentParser.safeString(data["riderName"])
            val startAtMillis = OrderDocumentParser.parseTolerantTimeMillisOrZero(data["startAt"])
            val endAtMillis = OrderDocumentParser.parseTolerantTimeMillisOrZero(data["endAt"])
            val bookedAtMillis = OrderDocumentParser.parseTolerantTimeMillisOrZero(data["bookedAt"])
            SessionBooking(
                sessionId = sessionId,
                riderId = riderId,
                riderName = riderName,
                startAtMillis = startAtMillis,
                endAtMillis = endAtMillis,
                bookedAtMillis = bookedAtMillis
            )
        } catch (_: Throwable) {
            null
        }
    }

    private fun parseRewards(map: Map<*, *>?): SessionRewards {
        if (map == null) return SessionRewards()
        val emoji = OrderDocumentParser.safeString(map["emoji"]).trim()
        return SessionRewards(
            emoji = emoji,
            perDelivery = OrderDocumentParser.safeDouble(map["perDelivery"]) ?: 0.0,
            streakEvery = (OrderDocumentParser.safeInt(map["streakEvery"]) ?: 0).coerceAtLeast(0),
            streakBonus = OrderDocumentParser.safeDouble(map["streakBonus"]) ?: 0.0,
            completionTarget = (OrderDocumentParser.safeInt(map["completionTarget"]) ?: 0).coerceAtLeast(0),
            completionBonus = OrderDocumentParser.safeDouble(map["completionBonus"]) ?: 0.0,
            minEarnings = OrderDocumentParser.safeDouble(map["minEarnings"]) ?: 0.0,
            multiplier = OrderDocumentParser.safeDouble(map["multiplier"]) ?: 0.0,
            coins = (OrderDocumentParser.safeInt(map["coins"]) ?: 0).coerceAtLeast(0),
            onlineBonus = OrderDocumentParser.safeDouble(map["onlineBonus"]) ?: 0.0,
            onlineMinPercent = (OrderDocumentParser.safeInt(map["onlineMinPercent"]) ?: 0).coerceAtLeast(0),
            services = parseStringList(map["services"]),
            note = OrderDocumentParser.safeString(map["note"]).trim()
        )
    }

    private fun parseStringList(raw: Any?): List<String> {
        return when (raw) {
            is List<*> -> raw.mapNotNull {
                it?.toString()?.trim()?.lowercase(Locale.US)?.takeIf { s -> s.isNotEmpty() }
            }
            is String -> raw.split(",")
                .map { it.trim().lowercase(Locale.US) }
                .filter { it.isNotEmpty() }
            else -> emptyList()
        }
    }
}

object ScheduleRules {
    const val SLOT_DURATION_MILLIS = 15L * 60L * 1000L // 900,000 ms (15 minutes)
    const val SEVEN_DAYS_MILLIS = 7L * 24L * 60L * 60L * 1000L
    const val REMINDER_LEAD_MILLIS = 15L * 60L * 1000L
    private val MANILA_TZ: TimeZone = TimeZone.getTimeZone("Asia/Manila")

    fun sessionsOverlap(startA: Long, endA: Long, startB: Long, endB: Long): Boolean {
        return startA < endB && startB < endA
    }

    /**
     * Generates deterministic 15-minute slot indices covering [startAtMillis, endAtMillis).
     * Touching endpoints (e.g. 03:00-06:00 and 06:00-09:00) share ZERO slots.
     */
    fun computeSlotIndices(startAtMillis: Long, endAtMillis: Long): List<Long> {
        if (startAtMillis <= 0L || endAtMillis <= startAtMillis) return emptyList()
        val firstSlot = Math.floorDiv(startAtMillis, SLOT_DURATION_MILLIS)
        val lastSlotInclusive = Math.floorDiv(endAtMillis - 1L, SLOT_DURATION_MILLIS)
        return (firstSlot..lastSlotInclusive).toList()
    }

    fun cityMatches(sessionCityId: String, riderCityId: String): Boolean {
        val sessCity = sessionCityId.trim().lowercase(Locale.US)
        if (sessCity.isEmpty()) return true
        val riderCity = riderCityId.trim().lowercase(Locale.US)
        if (riderCity.isEmpty()) return false
        return sessCity == riderCity
    }

    fun vehicleMatches(sessionVehicleTypes: List<String>, riderVehicleType: String): Boolean {
        if (sessionVehicleTypes.isEmpty()) return true
        val rawLower = riderVehicleType.trim().lowercase(Locale.US)
        if (rawLower.isEmpty()) return false
        val normalizedLower = OrderStepConstants.normalizeVehicleType(rawLower).lowercase(Locale.US)
        val normalizedSessionVehicles = sessionVehicleTypes.map { it.trim().lowercase(Locale.US) }
        return normalizedSessionVehicles.any { target ->
            target == rawLower ||
                target == normalizedLower ||
                OrderStepConstants.normalizeVehicleType(target).lowercase(Locale.US) == normalizedLower
        }
    }

    fun findConflictingSession(
        candidate: ScheduleSession,
        bookedSessions: List<ScheduleSession>,
        excludingSessionId: String? = null
    ): ScheduleSession? {
        return bookedSessions.firstOrNull { booked ->
            booked.id != candidate.id &&
                (excludingSessionId == null || booked.id != excludingSessionId) &&
                sessionsOverlap(candidate.startAtMillis, candidate.endAtMillis, booked.startAtMillis, booked.endAtMillis)
        }
    }

    fun computeDisableReason(
        session: ScheduleSession,
        bookedSessions: List<ScheduleSession>,
        nowMillis: Long,
        excludingSessionId: String? = null,
        timeZone: TimeZone = MANILA_TZ
    ): String? {
        if (session.isFull) return "Full"
        if (session.isBookingClosed(nowMillis)) return "Booking closed"
        val conflict = findConflictingSession(session, bookedSessions, excludingSessionId)
        if (conflict != null) {
            val range = formatTimeRange(conflict.startAtMillis, conflict.endAtMillis, timeZone)
            return "Conflicts with your $range session"
        }
        return null
    }

    fun evaluateSessions(
        parsedSessions: List<ScheduleSession>,
        skippedParseReasons: List<HiddenSessionDebugEntry>,
        bookedBySessionId: Map<String, SessionBooking>,
        claimsBySessionId: Map<String, SessionClaim>,
        riderCityId: String,
        riderVehicleType: String,
        nowMillis: Long = System.currentTimeMillis(),
        swapExcludingSessionId: String? = null,
        timeZone: TimeZone = MANILA_TZ
    ): ScheduleFilterResult {
        val hidden = skippedParseReasons.toMutableList()
        val loadedCount = parsedSessions.size

        // Booked sessions from last 7 days + future (regardless of open/closed status so rider always sees what they booked)
        val sevenDaysAgo = nowMillis - SEVEN_DAYS_MILLIS
        val bookedItems = parsedSessions
            .mapNotNull { session ->
                val booking = bookedBySessionId[session.id] ?: return@mapNotNull null
                if (session.endAtMillis >= sevenDaysAgo || session.startAtMillis >= sevenDaysAgo) {
                    BookedSessionItem(
                        session = session,
                        booking = booking,
                        claim = claimsBySessionId[session.id]
                    )
                } else null
            }
            .sortedWith(
                compareBy<BookedSessionItem>(
                    {
                        when (it.session.statusAt(nowMillis)) {
                            BookedSessionStatus.LIVE_NOW -> 0
                            BookedSessionStatus.UPCOMING -> 1
                            BookedSessionStatus.COMPLETED -> 2
                        }
                    },
                    {
                        if (it.session.statusAt(nowMillis) == BookedSessionStatus.COMPLETED) -it.session.startAtMillis
                        else it.session.startAtMillis
                    }
                )
            )
        val bookedSessionsList = bookedItems.map { it.session }

        var openCount = 0
        var myCityCount = 0
        var myVehicleCount = 0
        var bookableCount = 0
        val availableItems = mutableListOf<AvailableSessionItem>()

        for (session in parsedSessions.sortedBy { it.startAtMillis }) {
            val debugLabel = session.title.ifBlank {
                formatTimeRange(session.startAtMillis, session.endAtMillis, timeZone)
            }
            if (session.status != "open") {
                hidden.add(HiddenSessionDebugEntry(session.id, debugLabel, "Status is '${session.status}' (not open)"))
                continue
            }
            openCount++

            if (!cityMatches(session.cityId, riderCityId)) {
                hidden.add(
                    HiddenSessionDebugEntry(
                        session.id,
                        debugLabel,
                        "City '${session.cityId}' != '${riderCityId}'"
                    )
                )
                continue
            }
            myCityCount++

            if (!vehicleMatches(session.vehicleTypes, riderVehicleType)) {
                hidden.add(
                    HiddenSessionDebugEntry(
                        session.id,
                        debugLabel,
                        "Vehicle ${session.vehicleTypes} excludes '$riderVehicleType'"
                    )
                )
                continue
            }
            myVehicleCount++

            if (session.startAtMillis <= nowMillis) {
                hidden.add(HiddenSessionDebugEntry(session.id, debugLabel, "Already started or in the past"))
                continue
            }

            if (bookedBySessionId.containsKey(session.id)) {
                hidden.add(HiddenSessionDebugEntry(session.id, debugLabel, "Already booked by you"))
                continue
            }

            val disableReason = computeDisableReason(
                session = session,
                bookedSessions = bookedSessionsList,
                nowMillis = nowMillis,
                excludingSessionId = swapExcludingSessionId,
                timeZone = timeZone
            )
            if (disableReason == null) {
                bookableCount++
            }
            availableItems.add(AvailableSessionItem(session = session, disableReason = disableReason))
        }

        return ScheduleFilterResult(
            loadedCount = loadedCount,
            openCount = openCount,
            myCityCount = myCityCount,
            myVehicleCount = myVehicleCount,
            bookableCount = bookableCount,
            availableDisplayed = availableItems,
            bookedDisplayed = bookedItems,
            hiddenReasons = hidden
        )
    }

    fun formatTimeRange(
        startAtMillis: Long,
        endAtMillis: Long,
        timeZone: TimeZone = MANILA_TZ
    ): String {
        val fmt = SimpleDateFormat("HH:mm", Locale.US).apply { this.timeZone = timeZone }
        return "${fmt.format(Date(startAtMillis))} - ${fmt.format(Date(endAtMillis))}"
    }

    fun formatDuration(startAtMillis: Long, endAtMillis: Long): String {
        val totalMinutes = ((endAtMillis - startAtMillis) / 60_000L).coerceAtLeast(0L)
        val hours = totalMinutes / 60L
        val mins = totalMinutes % 60L
        return when {
            hours > 0L && mins == 0L -> "${hours}h"
            hours > 0L -> "${hours}h ${mins}m"
            else -> "${mins}m"
        }
    }

    fun dayGroupLabel(
        startAtMillis: Long,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = MANILA_TZ
    ): String {
        val targetCal = Calendar.getInstance(timeZone, Locale.US).apply { timeInMillis = startAtMillis }
        val todayCal = Calendar.getInstance(timeZone, Locale.US).apply { timeInMillis = nowMillis }
        val tomorrowCal = Calendar.getInstance(timeZone, Locale.US).apply {
            timeInMillis = nowMillis
            add(Calendar.DAY_OF_YEAR, 1)
        }
        return when {
            isSameDay(targetCal, todayCal) -> "Today"
            isSameDay(targetCal, tomorrowCal) -> "Tomorrow"
            else -> SimpleDateFormat("EEE, MMM d", Locale.US).apply { this.timeZone = timeZone }.format(Date(startAtMillis))
        }
    }

    fun formatSessionDate(
        startAtMillis: Long,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = MANILA_TZ
    ): String {
        val group = dayGroupLabel(startAtMillis, nowMillis, timeZone)
        val fullDate = SimpleDateFormat("MMM d, yyyy", Locale.US).apply { this.timeZone = timeZone }.format(Date(startAtMillis))
        return if (group == "Today" || group == "Tomorrow") "$group • $fullDate"
        else SimpleDateFormat("EEE, MMM d, yyyy", Locale.US).apply { this.timeZone = timeZone }.format(Date(startAtMillis))
    }

    fun formatCountdownUntilStart(startAtMillis: Long, nowMillis: Long): String {
        val diffMs = (startAtMillis - nowMillis).coerceAtLeast(0L)
        val totalMins = diffMs / 60_000L
        val days = totalMins / (60L * 24L)
        val hours = (totalMins % (60L * 24L)) / 60L
        val mins = totalMins % 60L
        return when {
            days > 0L -> "Starts in ${days}d ${hours}h"
            hours > 0L && mins > 0L -> "Starts in ${hours}h ${mins}m"
            hours > 0L -> "Starts in ${hours}h"
            totalMins > 0L -> "Starts in ${totalMins}m"
            else -> "Starts soon"
        }
    }

    fun formatTimeLeft(endAtMillis: Long, nowMillis: Long): String {
        val diffMs = (endAtMillis - nowMillis).coerceAtLeast(0L)
        val totalMins = (diffMs / 60_000L).coerceAtLeast(1L)
        val hours = totalMins / 60L
        val mins = totalMins % 60L
        return when {
            hours > 0L && mins > 0L -> "${hours}h ${mins}m left"
            hours > 0L -> "${hours}h left"
            else -> "${mins}m left"
        }
    }

    fun formatRewardChips(rewards: SessionRewards, currencySymbol: String = "₱"): List<String> {
        if (!rewards.hasRewards) return emptyList()
        val chips = mutableListOf<String>()
        if (rewards.perDelivery > 0.0) {
            chips.add("+${currencySymbol}${formatAmount(rewards.perDelivery)} per delivery")
        }
        if (rewards.minEarnings > 0.0) {
            chips.add("Min earn ${currencySymbol}${formatAmount(rewards.minEarnings)}")
        }
        if (rewards.multiplier > 1.0) {
            chips.add("x${formatAmount(rewards.multiplier)} rate")
        }
        if (rewards.coins > 0) {
            chips.add("+${rewards.coins} coins")
        }
        if (rewards.completionBonus > 0.0) {
            if (rewards.completionTarget > 0) {
                chips.add("Bonus ${currencySymbol}${formatAmount(rewards.completionBonus)} for ${rewards.completionTarget} deliveries")
            } else {
                chips.add("Bonus ${currencySymbol}${formatAmount(rewards.completionBonus)}")
            }
        }
        if (rewards.streakBonus > 0.0 && rewards.streakEvery > 0) {
            chips.add("+${currencySymbol}${formatAmount(rewards.streakBonus)} per ${rewards.streakEvery} streak")
        }
        if (rewards.onlineBonus > 0.0) {
            if (rewards.onlineMinPercent > 0) {
                chips.add("+${currencySymbol}${formatAmount(rewards.onlineBonus)} (${rewards.onlineMinPercent}% online)")
            } else {
                chips.add("+${currencySymbol}${formatAmount(rewards.onlineBonus)} online bonus")
            }
        }
        if (rewards.services.isNotEmpty()) {
            val svcNames = rewards.services.joinToString("/") { svc ->
                when (svc.lowercase(Locale.US)) {
                    "convenience", "mart" -> "Mart"
                    "request_rider", "instant" -> "Instant"
                    else -> svc.replaceFirstChar { it.uppercase() }
                }
            }
            chips.add(svcNames)
        }
        return chips
    }

    fun formatAmount(value: Double): String {
        val rounded = (value * 100.0).roundToLong() / 100.0
        return if (abs(rounded - rounded.toLong()) < 0.005) {
            rounded.toLong().toString()
        } else {
            String.format(Locale.US, "%.2f", rounded)
        }
    }

    private fun serviceMatchesFilter(orderServiceType: String, allowedServices: List<String>): Boolean {
        if (allowedServices.isEmpty()) return true
        val normOrder = when (orderServiceType.trim().lowercase(Locale.US)) {
            "mart", "convenience" -> "convenience"
            "instant", "request", "request_rider" -> "request_rider"
            else -> orderServiceType.trim().lowercase(Locale.US)
        }
        return allowedServices.any { raw ->
            val normAllowed = when (raw.trim().lowercase(Locale.US)) {
                "mart", "convenience" -> "convenience"
                "instant", "request", "request_rider" -> "request_rider"
                else -> raw.trim().lowercase(Locale.US)
            }
            normAllowed == normOrder
        }
    }

    fun computeSessionClaimMetrics(
        session: ScheduleSession,
        deliveredOrders: List<Order>,
        onlineIntervals: List<UserPreferencesRepository.OnlineInterval>,
        activeOnlineSinceMillis: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): SessionClaimMetrics {
        val matchingOrders = deliveredOrders.filter { order ->
            val ts = order.deliveredAtMillis.takeIf { it > 0L }
                ?: OrderDocumentParser.parseTolerantTimeMillisOrZero(order.completedAt).takeIf { it > 0L }
                ?: OrderDocumentParser.parseTolerantTimeMillisOrZero(order.updatedAt)
            ts in session.startAtMillis..session.endAtMillis &&
                serviceMatchesFilter(order.serviceType, session.rewards.services)
        }
        val deliveries = matchingOrders.size
        val earned = PricingEngine.round2(matchingOrders.sumOf { it.estimatedEarnings })

        val effectiveIntervals = buildList {
            addAll(onlineIntervals)
            if (activeOnlineSinceMillis > 0L && nowMillis > activeOnlineSinceMillis) {
                add(UserPreferencesRepository.OnlineInterval(activeOnlineSinceMillis, nowMillis))
            }
        }
        val overlapMillis = computeMergedOverlapMillis(
            intervals = effectiveIntervals,
            windowStart = session.startAtMillis,
            windowEnd = minOf(session.endAtMillis, nowMillis)
        )
        val onlineMinutes = (overlapMillis / 60_000L).toInt().coerceAtLeast(0)
        return SessionClaimMetrics(
            deliveries = deliveries,
            earned = earned,
            onlineMinutes = onlineMinutes
        )
    }

    fun computeMergedOverlapMillis(
        intervals: List<UserPreferencesRepository.OnlineInterval>,
        windowStart: Long,
        windowEnd: Long
    ): Long {
        if (windowEnd <= windowStart || intervals.isEmpty()) return 0L
        val clipped = intervals.mapNotNull { iv ->
            val s = maxOf(iv.startMs, windowStart)
            val e = minOf(iv.endMs, windowEnd)
            if (e > s) s to e else null
        }.sortedBy { it.first }
        if (clipped.isEmpty()) return 0L
        var total = 0L
        var curStart = clipped[0].first
        var curEnd = clipped[0].second
        for (i in 1 until clipped.size) {
            val (s, e) = clipped[i]
            if (s <= curEnd) {
                curEnd = maxOf(curEnd, e)
            } else {
                total += (curEnd - curStart)
                curStart = s
                curEnd = e
            }
        }
        total += (curEnd - curStart)
        return total
    }

    private fun isSameDay(a: Calendar, b: Calendar): Boolean {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }
}
