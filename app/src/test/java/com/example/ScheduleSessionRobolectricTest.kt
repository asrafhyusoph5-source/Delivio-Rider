package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.models.BookedSessionItem
import com.example.data.models.Order
import com.example.data.models.RiderQuote
import com.example.data.models.ScheduleRules
import com.example.data.models.ScheduleSession
import com.example.data.models.ScheduleSessionParser
import com.example.data.models.SessionBooking
import com.example.data.models.SessionClaim
import com.example.data.models.SessionRewards
import com.example.data.repository.UserPreferencesRepository
import com.example.ui.screens.ScheduleContent
import com.example.ui.theme.DelivioRiderTheme
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class ScheduleSessionRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val manilaTz: TimeZone = TimeZone.getTimeZone("Asia/Manila")

    private fun manilaMillis(year: Int, monthZeroBased: Int, day: Int, hour: Int, minute: Int): Long {
        return Calendar.getInstance(manilaTz, Locale.US).apply {
            set(year, monthZeroBased, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    @Test
    fun `tolerant parser handles missing values, numeric strings, and skips malformed docs`() {
        val startMs = manilaMillis(2026, Calendar.NOVEMBER, 24, 3, 0)
        val endMs = manilaMillis(2026, Calendar.NOVEMBER, 24, 6, 0)

        val validMap = mapOf(
            "title" to "Dawn Boost",
            "cityId" to "Puerto_Princesa",
            "startAt" to Timestamp(Date(startMs)),
            "endAt" to Timestamp(Date(endMs)),
            "slots" to "10",
            "bookedCount" to 6L,
            "status" to "OPEN",
            "vehicleTypes" to listOf("Motorcycle", "TRICYCLE"),
            "rewards" to mapOf(
                "emoji" to "🔥",
                "perDelivery" to "3",
                "minEarnings" to 300,
                "multiplier" to 1.2,
                "coins" to 50,
                "completionTarget" to 10,
                "completionBonus" to 100
            )
        )
        val parsed = ScheduleSessionParser.fromMap("sess_1", validMap).getOrThrow()
        assertEquals("Dawn Boost", parsed.title)
        assertEquals(10, parsed.slots)
        assertEquals(6, parsed.bookedCount)
        assertEquals(4, parsed.remainingSlots)
        assertEquals("open", parsed.status)
        assertEquals(30, parsed.bookingCloseMin) // default
        assertEquals(120, parsed.cancelCutoffMin) // default
        assertTrue(parsed.rewards.hasRewards)
        assertEquals("🔥", parsed.rewards.emoji)

        val badMap = mapOf(
            "title" to "Broken Doc",
            "startAt" to "not-a-timestamp",
            "endAt" to Timestamp(Date(endMs))
        )
        assertTrue(ScheduleSessionParser.fromMap("bad_1", badMap).isFailure)
    }

    @Test
    fun `overlap and 15 minute slot indices catch partial overlaps and allow touching boundaries`() {
        val s0300 = manilaMillis(2026, Calendar.NOVEMBER, 24, 3, 0)
        val e0600 = manilaMillis(2026, Calendar.NOVEMBER, 24, 6, 0)
        val bookedSlots = ScheduleRules.computeSlotIndices(s0300, e0600).toSet()
        assertEquals(12, bookedSlots.size) // 3 hours * 4 slots/hour

        // Overlapping cases
        val c1Start = manilaMillis(2026, Calendar.NOVEMBER, 24, 2, 0)
        val c1End = manilaMillis(2026, Calendar.NOVEMBER, 24, 4, 0)
        assertTrue(ScheduleRules.sessionsOverlap(s0300, e0600, c1Start, c1End))
        assertTrue(ScheduleRules.computeSlotIndices(c1Start, c1End).any { it in bookedSlots })

        val c2Start = manilaMillis(2026, Calendar.NOVEMBER, 24, 5, 50)
        val c2End = manilaMillis(2026, Calendar.NOVEMBER, 24, 7, 0)
        assertTrue(ScheduleRules.sessionsOverlap(s0300, e0600, c2Start, c2End))
        assertTrue(ScheduleRules.computeSlotIndices(c2Start, c2End).any { it in bookedSlots })

        val c3Start = manilaMillis(2026, Calendar.NOVEMBER, 24, 2, 15)
        val c3End = manilaMillis(2026, Calendar.NOVEMBER, 24, 3, 15)
        assertTrue(ScheduleRules.sessionsOverlap(s0300, e0600, c3Start, c3End))
        assertTrue(ScheduleRules.computeSlotIndices(c3Start, c3End).any { it in bookedSlots })

        // Touching boundaries must NOT conflict or share slot indices
        val t1Start = manilaMillis(2026, Calendar.NOVEMBER, 24, 6, 0)
        val t1End = manilaMillis(2026, Calendar.NOVEMBER, 24, 9, 0)
        assertFalse(ScheduleRules.sessionsOverlap(s0300, e0600, t1Start, t1End))
        assertTrue(ScheduleRules.computeSlotIndices(t1Start, t1End).none { it in bookedSlots })

        val t2Start = manilaMillis(2026, Calendar.NOVEMBER, 24, 1, 0)
        val t2End = manilaMillis(2026, Calendar.NOVEMBER, 24, 3, 0)
        assertFalse(ScheduleRules.sessionsOverlap(s0300, e0600, t2Start, t2End))
        assertTrue(ScheduleRules.computeSlotIndices(t2Start, t2End).none { it in bookedSlots })
    }

    @Test
    fun `evaluateSessions filters open city vehicle and formats debug funnel plus rewards visibility`() {
        val now = manilaMillis(2026, Calendar.NOVEMBER, 24, 0, 0)
        val bookedStart = manilaMillis(2026, Calendar.NOVEMBER, 24, 3, 0)
        val bookedEnd = manilaMillis(2026, Calendar.NOVEMBER, 24, 6, 0)
        val bookedSession = ScheduleSession(
            id = "s_booked",
            title = "Early Shift",
            cityId = "puerto_princesa",
            startAtMillis = bookedStart,
            endAtMillis = bookedEnd,
            slots = 10,
            bookedCount = 4,
            status = "open",
            rewards = SessionRewards(emoji = "🔥", perDelivery = 3.0)
        )
        val openConflict = ScheduleSession(
            id = "s_conflict",
            title = "Overlap Shift",
            cityId = "",
            startAtMillis = manilaMillis(2026, Calendar.NOVEMBER, 24, 5, 50),
            endAtMillis = manilaMillis(2026, Calendar.NOVEMBER, 24, 7, 0),
            slots = 10,
            bookedCount = 2,
            status = "open",
            rewards = SessionRewards(emoji = "", perDelivery = 5.0) // no emoji = no rewards
        )
        val openGood = ScheduleSession(
            id = "s_good",
            title = "Morning Touching",
            cityId = "puerto_princesa",
            startAtMillis = manilaMillis(2026, Calendar.NOVEMBER, 24, 6, 0),
            endAtMillis = manilaMillis(2026, Calendar.NOVEMBER, 24, 9, 0),
            slots = 10,
            bookedCount = 6,
            status = "open",
            vehicleTypes = listOf("motorcycle"),
            rewards = SessionRewards(emoji = "⚡", coins = 50)
        )
        val wrongCity = ScheduleSession(
            id = "s_city",
            title = "Cebu Shift",
            cityId = "cebu",
            startAtMillis = manilaMillis(2026, Calendar.NOVEMBER, 24, 10, 0),
            endAtMillis = manilaMillis(2026, Calendar.NOVEMBER, 24, 12, 0),
            slots = 5,
            bookedCount = 0,
            status = "open"
        )
        val wrongVehicle = ScheduleSession(
            id = "s_veh",
            title = "Car Only",
            cityId = "puerto_princesa",
            startAtMillis = manilaMillis(2026, Calendar.NOVEMBER, 24, 13, 0),
            endAtMillis = manilaMillis(2026, Calendar.NOVEMBER, 24, 15, 0),
            slots = 5,
            bookedCount = 0,
            status = "open",
            vehicleTypes = listOf("car4")
        )
        val closedSess = ScheduleSession(
            id = "s_closed",
            title = "Closed",
            cityId = "puerto_princesa",
            startAtMillis = manilaMillis(2026, Calendar.NOVEMBER, 24, 16, 0),
            endAtMillis = manilaMillis(2026, Calendar.NOVEMBER, 24, 18, 0),
            slots = 5,
            bookedCount = 0,
            status = "closed"
        )

        val result = ScheduleRules.evaluateSessions(
            parsedSessions = listOf(bookedSession, openConflict, openGood, wrongCity, wrongVehicle, closedSess),
            skippedParseReasons = emptyList(),
            bookedBySessionId = mapOf("s_booked" to SessionBooking(sessionId = "s_booked", riderId = "r1")),
            claimsBySessionId = emptyMap(),
            riderCityId = "puerto_princesa",
            riderVehicleType = "Motorcycle",
            nowMillis = now
        )

        assertEquals(6, result.loadedCount)
        assertEquals(5, result.openCount)
        assertEquals(4, result.myCityCount)
        assertEquals(3, result.myVehicleCount)
        assertEquals(1, result.bookableCount)
        assertEquals(
            "Loaded 6 sessions - open 5 - my city 4 - my vehicle 3 - bookable 1",
            result.summaryLine
        )
        assertEquals(2, result.availableDisplayed.size)
        val conflictItem = result.availableDisplayed.first { it.session.id == "s_conflict" }
        assertEquals("Conflicts with your 03:00 - 06:00 session", conflictItem.disableReason)
        assertTrue(ScheduleRules.formatRewardChips(conflictItem.session.rewards).isEmpty())

        val goodItem = result.availableDisplayed.first { it.session.id == "s_good" }
        assertNull(goodItem.disableReason)
        assertEquals(listOf("+50 coins"), ScheduleRules.formatRewardChips(goodItem.session.rewards))
    }

    @Test
    fun `computeSessionClaimMetrics counts filtered deliveries earnings and onlineMinutes`() {
        val startMs = manilaMillis(2026, Calendar.NOVEMBER, 23, 8, 0)
        val endMs = manilaMillis(2026, Calendar.NOVEMBER, 23, 11, 0)
        val session = ScheduleSession(
            id = "sess_claim",
            title = "Morning Run",
            startAtMillis = startMs,
            endAtMillis = endMs,
            rewards = SessionRewards(emoji = "🎁", services = listOf("food"))
        )

        val oInsideFood = Order(
            id = "o1",
            serviceType = "food",
            status = "Delivered",
            deliveredAt = Timestamp(Date(manilaMillis(2026, Calendar.NOVEMBER, 23, 9, 15))),
            riderQuote = RiderQuote(net = 85.0)
        )
        val oInsideParcel = Order(
            id = "o2",
            serviceType = "parcel",
            status = "Delivered",
            deliveredAt = Timestamp(Date(manilaMillis(2026, Calendar.NOVEMBER, 23, 9, 45))),
            riderQuote = RiderQuote(net = 120.0)
        )
        val oOutside = Order(
            id = "o3",
            serviceType = "food",
            status = "Delivered",
            deliveredAt = Timestamp(Date(manilaMillis(2026, Calendar.NOVEMBER, 23, 12, 0))),
            riderQuote = RiderQuote(net = 70.0)
        )

        // Online from 07:30 to 09:30 (90 min overlap) and 10:00 to 11:30 (60 min overlap) -> 150 min total
        val intervals = listOf(
            UserPreferencesRepository.OnlineInterval(
                manilaMillis(2026, Calendar.NOVEMBER, 23, 7, 30),
                manilaMillis(2026, Calendar.NOVEMBER, 23, 9, 30)
            ),
            UserPreferencesRepository.OnlineInterval(
                manilaMillis(2026, Calendar.NOVEMBER, 23, 10, 0),
                manilaMillis(2026, Calendar.NOVEMBER, 23, 11, 30)
            )
        )

        val metrics = ScheduleRules.computeSessionClaimMetrics(
            session = session,
            deliveredOrders = listOf(oInsideFood, oInsideParcel, oOutside),
            onlineIntervals = intervals,
            activeOnlineSinceMillis = 0L,
            nowMillis = manilaMillis(2026, Calendar.NOVEMBER, 23, 12, 0)
        )

        assertEquals(1, metrics.deliveries)
        assertEquals(85.0, metrics.earned, 0.01)
        assertEquals(150, metrics.onlineMinutes)
    }

    @Test
    fun `scheduleScreen displays available session, booking confirmation, booked tab and Swap Keep sheet`() {
        val now = manilaMillis(2026, Calendar.NOVEMBER, 24, 0, 0)
        val bookedSess = ScheduleSession(
            id = "sess_booked",
            title = "Night Owl",
            cityId = "puerto_princesa",
            startAtMillis = manilaMillis(2026, Calendar.NOVEMBER, 24, 18, 0),
            endAtMillis = manilaMillis(2026, Calendar.NOVEMBER, 24, 21, 0),
            slots = 10,
            bookedCount = 3,
            status = "open",
            cancelCutoffMin = 120,
            rewards = SessionRewards(emoji = "🌙", perDelivery = 5.0)
        )
        val openSess = ScheduleSession(
            id = "sess_open",
            title = "Dawn Surge",
            cityId = "puerto_princesa",
            startAtMillis = manilaMillis(2026, Calendar.NOVEMBER, 24, 3, 0),
            endAtMillis = manilaMillis(2026, Calendar.NOVEMBER, 24, 6, 0),
            slots = 10,
            bookedCount = 6,
            status = "open",
            rewards = SessionRewards(emoji = "🔥", perDelivery = 3.0, minEarnings = 300.0)
        )
        val conflictSess = ScheduleSession(
            id = "sess_conflict",
            title = "Evening Conflict",
            cityId = "puerto_princesa",
            startAtMillis = manilaMillis(2026, Calendar.NOVEMBER, 24, 19, 0),
            endAtMillis = manilaMillis(2026, Calendar.NOVEMBER, 24, 22, 0),
            slots = 8,
            bookedCount = 2,
            status = "open"
        )

        val result = ScheduleRules.evaluateSessions(
            parsedSessions = listOf(bookedSess, openSess, conflictSess),
            skippedParseReasons = emptyList(),
            bookedBySessionId = mapOf("sess_booked" to SessionBooking(sessionId = "sess_booked", riderId = "r1")),
            claimsBySessionId = emptyMap(),
            riderCityId = "puerto_princesa",
            riderVehicleType = "motorcycle",
            nowMillis = now
        )

        var bookedCalledFor: String? = null
        var swapStartedFor: String? = null

        composeRule.setContent {
            DelivioRiderTheme {
                ScheduleContent(
                    filterResult = result,
                    nowMillis = now,
                    onBookSession = { sess, onDone ->
                        bookedCalledFor = sess.id
                        onDone()
                    },
                    onStartSwap = { sess ->
                        swapStartedFor = sess.id
                    }
                )
            }
        }

        // Top bar is "Schedule", old planner actions absent, only Available & Booked tabs
        composeRule.onNodeWithText("Schedule").assertIsDisplayed()
        composeRule.onNodeWithTag("add_shift_fab").assertDoesNotExist()
        composeRule.onNodeWithTag("add_shift_action").assertDoesNotExist()
        composeRule.onNodeWithTag("tab_available").assertIsDisplayed()
        composeRule.onNodeWithTag("tab_booked").assertIsDisplayed()

        // Available card shows time, emoji, rewards, slots left, debug line, and disabled conflict reason
        composeRule.onNodeWithText("03:00 - 06:00").assertIsDisplayed()
        composeRule.onNodeWithTag("session_reward_emoji_sess_open").assertIsDisplayed()
        composeRule.onNodeWithText("+₱3 per delivery").assertIsDisplayed()
        composeRule.onNodeWithText("4 of 10 left").assertIsDisplayed()
        composeRule.onNodeWithTag("book_session_button_sess_open").assertIsEnabled()

        composeRule.onNodeWithTag("book_session_button_sess_conflict").assertIsNotEnabled()
        composeRule.onNodeWithText("Conflicts with your 18:00 - 21:00 session").assertIsDisplayed()
        composeRule.onNodeWithTag("schedule_debug_line").assertIsDisplayed()

        // Tapping "+" opens confirmation dialog with "Confirmed" and "Cancel"
        composeRule.onNodeWithTag("book_session_button_sess_open").performClick()
        composeRule.onNodeWithText("Do you want to book this session?").assertIsDisplayed()
        composeRule.onNodeWithTag("cancel_book_button").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm_book_button").assertIsDisplayed().performClick()
        assertEquals("sess_open", bookedCalledFor)

        // Booked tab shows booked session; tapping opens Swap & Keep bottom sheet
        composeRule.onNodeWithTag("tab_booked").performClick()
        composeRule.onNodeWithTag("booked_session_card_sess_booked").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("sheet_swap_button").assertIsDisplayed()
        composeRule.onNodeWithTag("sheet_keep_button").assertIsDisplayed()
        composeRule.onNodeWithTag("sheet_swap_button").performClick()
        assertEquals("sess_booked", swapStartedFor)
    }

    @Test
    fun `scheduleScreen displays exact empty states on Available and Booked tabs`() {
        val emptyResult = ScheduleRules.evaluateSessions(
            parsedSessions = emptyList(),
            skippedParseReasons = emptyList(),
            bookedBySessionId = emptyMap(),
            claimsBySessionId = emptyMap(),
            riderCityId = "puerto_princesa",
            riderVehicleType = "motorcycle"
        )

        composeRule.setContent {
            DelivioRiderTheme {
                ScheduleContent(filterResult = emptyResult)
            }
        }

        composeRule.onNodeWithText("No sessions posted right now. The admin will post new sessions here.")
            .assertIsDisplayed()

        composeRule.onNodeWithTag("tab_booked").performClick()
        composeRule.onNodeWithText("You have not booked any sessions yet.")
            .assertIsDisplayed()
    }
}
