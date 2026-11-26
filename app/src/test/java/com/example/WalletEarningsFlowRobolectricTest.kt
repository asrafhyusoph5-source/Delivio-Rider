package com.example

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.data.constants.OrderStepConstants
import com.example.data.models.*
import com.example.data.repository.UserPreferencesRepository
import com.example.ui.screens.EarningsContent
import com.example.ui.screens.IncomeHistoryContent
import com.example.ui.screens.WalletContent
import com.example.ui.theme.DelivioRiderTheme
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import org.junit.Assert.*
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
@Config(sdk = [34])
class WalletEarningsFlowRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun walletLedgerHelper_buildsDeterministicPayloads_obeysRuleCap_andGatesNegativeWalletCod() {
        val onlineOrder = Order(
            id = "order_online_1",
            orderNumber = "3LZKAP",
            paymentMethod = "online",
            tip = 15.0,
            riderQuote = RiderQuote(
                deliveryFee = 70.0,
                pickupFee = 10.0,
                commissionRate = 10.0,
                commissionAmount = 8.0,
                gross = 95.0,
                net = 87.0
            )
        )
        assertFalse(WalletLedgerHelper.isCodOrder(onlineOrder))
        // Net delivery fee after commission = 70 + 10 - 8 = 72.00 (excludes tip)
        val netDelivery = WalletLedgerHelper.computeNetDeliveryFeeForOrder(onlineOrder)
        assertEquals(72.0, netDelivery, 0.01)

        // Rule check: amount > 0 && amount <= feesTotal + 0.01
        assertEquals(72.0, WalletLedgerHelper.resolveRuleCompliantEarningAmount(netDelivery, 85.0)!!, 0.01)
        assertEquals(65.0, WalletLedgerHelper.resolveRuleCompliantEarningAmount(72.0, 65.0)!!, 0.01)
        assertNull(WalletLedgerHelper.resolveRuleCompliantEarningAmount(72.0, 0.0))

        assertEquals("rider_order_online_1_delivery_earning", WalletLedgerHelper.deliveryEarningDocId("order_online_1"))
        val earningPayload = WalletLedgerHelper.buildDeliveryEarningPayload(
            riderId = "rider_1",
            orderId = "order_online_1",
            orderNumber = "3LZKAP",
            amount = netDelivery
        )
        assertEquals("rider_1", earningPayload["riderId"])
        assertEquals("order_online_1", earningPayload["orderId"])
        assertEquals("delivery_earning", earningPayload["kind"])
        assertEquals(72.0, earningPayload["amount"] as Double, 0.01)
        assertEquals(72.0, earningPayload["signedAmount"] as Double, 0.01)
        assertEquals("delivery_credit", earningPayload["type"])
        assertEquals("Delivery earning - #3LZKAP", earningPayload["title"])
        assertTrue(earningPayload["createdAt"] is FieldValue)

        // COD order builds cod_collected deduction payload
        val codOrder = Order(
            id = "order_cod_1",
            orderNumber = "3LZKAP",
            paymentMethod = "cod",
            cashToCollect = 520.0
        )
        assertTrue(WalletLedgerHelper.isCodOrder(codOrder))
        assertEquals("rider_order_cod_1_cod_collected", WalletLedgerHelper.codCollectedDocId("order_cod_1"))
        val codPayload = WalletLedgerHelper.buildCodCollectedPayload(
            riderId = "rider_1",
            orderId = "order_cod_1",
            orderNumber = "3LZKAP",
            cashCollected = 520.0
        )
        assertEquals("cod_collected", codPayload["kind"])
        assertEquals(520.0, codPayload["amount"] as Double, 0.01)
        assertEquals(-520.0, codPayload["signedAmount"] as Double, 0.01)
        assertEquals("cod_deduction", codPayload["type"])
        assertEquals("COD collected - #3LZKAP", codPayload["title"])
        assertTrue(codPayload["createdAt"] is FieldValue)

        // Negative wallet gate blocks COD only when balance < 0
        assertFalse(WalletLedgerHelper.isCodBlockedForWallet(0.0))
        assertFalse(WalletLedgerHelper.isCodBlockedForWallet(150.0))
        assertTrue(WalletLedgerHelper.isCodBlockedForWallet(-0.01))
        assertTrue(WalletLedgerHelper.isCodBlockedForWallet(-250.0))
    }

    @Test
    fun pendingCodLedgerQueue_persistsAndRemovesEntriesInPreferences() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = UserPreferencesRepository(ctx)
        val entry = PendingCodLedgerEntry(
            orderId = "ord_retry_1",
            riderId = "rider_1",
            orderNumber = "9988",
            amount = 345.50
        )
        prefs.enqueuePendingCodLedgerEntry(entry)
        assertEquals(1, prefs.pendingCodLedgerEntries.value.size)
        assertEquals("ord_retry_1", prefs.pendingCodLedgerEntries.value.first().orderId)

        // Enqueueing same orderId replaces instead of duplicating
        prefs.enqueuePendingCodLedgerEntry(entry.copy(amount = 350.0))
        assertEquals(1, prefs.pendingCodLedgerEntries.value.size)
        assertEquals(350.0, prefs.pendingCodLedgerEntries.value.first().amount, 0.01)

        prefs.removePendingCodLedgerEntry("ord_retry_1")
        assertTrue(prefs.pendingCodLedgerEntries.value.isEmpty())
    }

    @Test
    fun walletScreen_showsNewLayout_statusChips_cashOnHandAboveTransactions_andFilters() {
        val tz = TimeZone.getTimeZone("Asia/Manila")
        val nowCal = Calendar.getInstance(tz, Locale.US).apply {
            set(2026, Calendar.NOVEMBER, 25, 14, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val nowMs = nowCal.timeInMillis
        val yesterdayMs = (nowCal.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, -1) }.timeInMillis

        val txs = listOf(
            WalletTransaction(
                id = "rider_o1_delivery_earning",
                riderId = "r1",
                orderId = "o1",
                kind = "delivery_earning",
                type = "delivery_credit",
                title = "Delivery earning - #3LZKAP",
                amount = 65.0,
                signedAmount = 65.0,
                balanceAfter = 165.0,
                dateRaw = nowMs - 1_800_000L
            ),
            WalletTransaction(
                id = "rider_o1_cod_collected",
                riderId = "r1",
                orderId = "o1",
                kind = "cod_collected",
                type = "cod_deduction",
                title = "COD collected - #3LZKAP",
                amount = 420.0,
                signedAmount = -420.0,
                balanceAfter = -255.0,
                dateRaw = nowMs - 1_700_000L
            ),
            WalletTransaction(
                id = "admin_dep_1",
                riderId = "r1",
                type = "cash_in",
                title = "Admin cash deposit",
                amount = 300.0,
                signedAmount = 300.0,
                balanceAfter = 45.0,
                dateRaw = yesterdayMs
            )
        )

        composeRule.setContent {
            DelivioRiderTheme {
                WalletContent(
                    walletBalance = -255.0,
                    cashOnHand = 420.0,
                    transactions = txs,
                    currencySymbol = "₱",
                    platformTimezone = "Asia/Manila",
                    isSyncingWallet = true,
                    nowMillis = nowMs,
                    onBack = {}
                )
            }
        }
        composeRule.waitForIdle()

        // Top card & negative balance status chip & syncing indicator
        assertEquals(2, composeRule.onAllNodesWithText("Delivio Wallet").fetchSemanticsNodes().size)
        composeRule.onNodeWithText("-₱255.00").assertIsDisplayed()
        composeRule.onNodeWithText("Negative balance - COD orders are blocked. Add cash through the admin to continue.").assertIsDisplayed()
        composeRule.onNodeWithText("Syncing wallet...").assertIsDisplayed()

        // Old 4 buttons and lines are completely gone
        composeRule.onNodeWithText("Earnings available to withdraw").assertDoesNotExist()
        composeRule.onNodeWithText("Remittance due").assertDoesNotExist()
        composeRule.onNodeWithText("Cash Out").assertDoesNotExist()
        composeRule.onNodeWithText("Remit Cash").assertDoesNotExist()
        composeRule.onNodeWithText("View Earnings & Trip History").assertDoesNotExist()

        // Cash collected (on hand) card under Wallet history and above transactions
        composeRule.onNodeWithTag("wallet_lazy_column").performScrollToNode(hasTestTag("cash_on_hand_card"))
        composeRule.onNodeWithText("Wallet history").assertIsDisplayed()
        composeRule.onNodeWithText("Cash collected (on hand)").assertIsDisplayed()
        composeRule.onNodeWithText("₱420.00").assertIsDisplayed()

        // All, Credits, Debits filter chips are removed; full history displays newest first grouped by date
        composeRule.onNodeWithTag("wallet_filter_all").assertDoesNotExist()
        composeRule.onNodeWithTag("wallet_filter_credits").assertDoesNotExist()
        composeRule.onNodeWithTag("wallet_filter_debits").assertDoesNotExist()
        composeRule.onNodeWithText("All").assertDoesNotExist()
        composeRule.onNodeWithText("Credits").assertDoesNotExist()
        composeRule.onNodeWithText("Debits").assertDoesNotExist()

        // Day headers & transaction cards
        composeRule.onNodeWithTag("wallet_lazy_column").performScrollToNode(hasTestTag("wallet_transaction_rider_o1_cod_collected"))
        composeRule.onNodeWithText("Today").assertIsDisplayed()
        composeRule.onNodeWithText("COD collected - #3LZKAP").assertIsDisplayed()
        composeRule.onNodeWithText("-₱420.00").assertIsDisplayed()
        composeRule.onNodeWithText("Balance after: -₱255.00").assertIsDisplayed()
        composeRule.onNodeWithTag("wallet_lazy_column").performScrollToNode(hasTestTag("wallet_transaction_rider_o1_delivery_earning"))
        composeRule.onNodeWithText("Delivery earning - #3LZKAP").assertIsDisplayed()
    }

    @Test
    fun walletScreen_positiveBalanceShowsCanReceiveCodChip() {
        composeRule.setContent {
            DelivioRiderTheme {
                WalletContent(
                    walletBalance = 0.0,
                    cashOnHand = 0.0,
                    transactions = emptyList(),
                    currencySymbol = "₱",
                    onBack = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Can receive COD orders").assertIsDisplayed()
        composeRule.onNodeWithTag("wallet_lazy_column").performScrollToNode(hasTestTag("wallet_history_empty"))
        composeRule.onNodeWithText("No wallet activity yet").assertIsDisplayed()
    }

    @Test
    fun earningsScreen_hidesBonusesRowWhenZero() {
        val tz = TimeZone.getTimeZone("Asia/Manila")
        val nowMs = Calendar.getInstance(tz, Locale.US).apply {
            set(2026, Calendar.NOVEMBER, 25, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val todayOrder = Order(
            id = "t1",
            orderNumber = "8888",
            serviceType = OrderStepConstants.SERVICE_FOOD,
            merchantName = "Cafe",
            paymentMethod = "online",
            tip = 20.0,
            deliveredAt = Timestamp(Date(nowMs - 600_000L)),
            riderQuote = RiderQuote(
                deliveryFee = 60.0,
                commissionRate = 10.0,
                commissionAmount = 6.0,
                gross = 80.0,
                net = 74.0
            )
        )

        composeRule.setContent {
            DelivioRiderTheme {
                EarningsContent(
                    currencySymbol = "₱",
                    deliveredOrders = listOf(todayOrder),
                    questClaims = emptyList(),
                    sessionClaims = emptyList(),
                    onBack = {},
                    nowMillis = nowMs
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Total Income").assertIsDisplayed()
        composeRule.onNodeWithText("₱74.00").assertIsDisplayed()
        composeRule.onNodeWithText("Delivery fees").assertIsDisplayed()
        composeRule.onNodeWithText("Tips").assertIsDisplayed()
        composeRule.onNodeWithText("Bonuses").assertDoesNotExist()
    }

    @Test
    fun incomeHistoryScreen_displaysDailyWeeklyMonthlyTabs_inProgressCurrent_andPastPeriodDrillDown() {
        val tz = TimeZone.getTimeZone("Asia/Manila")
        // Wednesday Nov 25, 2026 -> Week 48 (Nov 23 - Nov 29)
        val nowCal = Calendar.getInstance(tz, Locale.US).apply {
            set(2026, Calendar.NOVEMBER, 25, 15, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val nowMs = nowCal.timeInMillis

        // Yesterday: Tue, Nov 24, 2026 (still in current Week 48 & current November, past Daily row)
        val yesterdayMs = (nowCal.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, -1) }.timeInMillis
        // Last week: Wed, Nov 18, 2026 -> Week 47 (past Weekly row)
        val lastWeekMs = (nowCal.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, -7) }.timeInMillis
        // September 2026 -> past Monthly row
        val septMs = Calendar.getInstance(tz, Locale.US).apply {
            set(2026, Calendar.SEPTEMBER, 15, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val orders = listOf(
            Order(
                id = "o_tue",
                orderNumber = "1101",
                serviceType = OrderStepConstants.SERVICE_FOOD,
                merchantName = "Bakery",
                paymentMethod = "online",
                tip = 15.0,
                deliveredAt = Timestamp(Date(yesterdayMs)),
                riderQuote = RiderQuote(deliveryFee = 60.0, commissionRate = 10.0, commissionAmount = 6.0, net = 69.0)
            ),
            Order(
                id = "o_w47",
                orderNumber = "1102",
                serviceType = OrderStepConstants.SERVICE_PARCEL,
                merchantName = "Parcel Hub",
                paymentMethod = "cod",
                cashToCollect = 200.0,
                deliveredAt = Timestamp(Date(lastWeekMs)),
                riderQuote = RiderQuote(deliveryFee = 80.0, commissionRate = 10.0, commissionAmount = 8.0, net = 72.0)
            ),
            Order(
                id = "o_sep",
                orderNumber = "1103",
                serviceType = OrderStepConstants.SERVICE_MART,
                merchantName = "Fresh Mart",
                paymentMethod = "online",
                deliveredAt = Timestamp(Date(septMs)),
                riderQuote = RiderQuote(deliveryFee = 100.0, commissionRate = 10.0, commissionAmount = 10.0, net = 90.0)
            )
        )

        composeRule.setContent {
            DelivioRiderTheme {
                IncomeHistoryContent(
                    currencySymbol = "₱",
                    deliveredOrders = orders,
                    questClaims = emptyList(),
                    sessionClaims = emptyList(),
                    platformTimezone = "Asia/Manila",
                    onBack = {},
                    nowMillis = nowMs
                )
            }
        }
        composeRule.waitForIdle()

        // Daily tab: year header + Today IN PROGRESS + Tue, Nov 24 ₱69.00
        composeRule.onNodeWithText("Income history").assertIsDisplayed()
        composeRule.onNodeWithText("2026").assertIsDisplayed()
        composeRule.onNodeWithText("Wed, Nov 25").assertIsDisplayed()
        composeRule.onNodeWithText("IN PROGRESS").assertIsDisplayed()
        composeRule.onNodeWithText("Tue, Nov 24").assertIsDisplayed()
        composeRule.onNodeWithText("₱69.00").assertIsDisplayed()

        // Tap Tue, Nov 24 opens full breakdown + trips for that day
        composeRule.onNodeWithText("Tue, Nov 24").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("history_detail_total_card").assertIsDisplayed()
        composeRule.onNodeWithText("Delivery fees").assertIsDisplayed()
        composeRule.onNodeWithText("₱54.00").assertIsDisplayed()
        composeRule.onNodeWithTag("income_history_detail_list")
            .performScrollToNode(hasTestTag("earnings_job_row_o_tue"))
        composeRule.onNodeWithText("#1101 • 3:00 PM").assertIsDisplayed()

        // Back returns to Income history list
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitForIdle()

        // Weekly tab: Week 48 IN PROGRESS, Week 47 ₱72.00
        composeRule.onNodeWithTag("income_history_tab_weekly").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Week 48").assertIsDisplayed()
        composeRule.onNodeWithText("IN PROGRESS").assertIsDisplayed()
        composeRule.onNodeWithText("Week 47").assertIsDisplayed()
        composeRule.onNodeWithText("₱72.00").assertIsDisplayed()

        // Monthly tab: November IN PROGRESS, September ₱90.00
        composeRule.onNodeWithTag("income_history_tab_monthly").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("November").assertIsDisplayed()
        composeRule.onNodeWithText("IN PROGRESS").assertIsDisplayed()
        composeRule.onNodeWithText("September").assertIsDisplayed()
        composeRule.onNodeWithText("₱90.00").assertIsDisplayed()
    }
}
