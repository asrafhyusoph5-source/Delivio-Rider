package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.constants.AuthConstants
import com.example.data.constants.OrderStepConstants
import com.example.data.models.Rider
import com.example.data.models.TierLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Delivio Rider", appName)
    }

    @Test
    fun `verify demo auth constants`() {
        assertEquals("testr@gmail.com", AuthConstants.DEMO_EMAIL)
        assertEquals("Lidesma@1234", AuthConstants.DEMO_PASSWORD)
        assertEquals("rider", AuthConstants.DEMO_ROLE)
    }

    @Test
    fun `verify monthly tier rides and coin thresholds`() {
        val tiers = TierLevel.values()
        assertEquals(4, tiers.size)

        val bronze = tiers.first { it.title == "Bronze" }
        assertEquals(500, bronze.monthlyRidesRequired)
        assertEquals(200, bronze.rewardCoins)

        val silver = tiers.first { it.title == "Silver" }
        assertEquals(650, silver.monthlyRidesRequired)
        assertEquals(500, silver.rewardCoins)

        val gold = tiers.first { it.title == "Gold" }
        assertEquals(850, gold.monthlyRidesRequired)
        assertEquals(700, gold.rewardCoins)

        val platinum = tiers.first { it.title.contains("Platinum") }
        assertEquals(1000, platinum.monthlyRidesRequired)
        assertEquals(1000, platinum.rewardCoins)
    }

    @Test
    fun `verify platform order step constants`() {
        assertEquals("food", OrderStepConstants.SERVICE_FOOD)
        assertEquals("convenience", OrderStepConstants.SERVICE_INSTANT)
        assertEquals("parcel", OrderStepConstants.SERVICE_PARCEL)
        assertEquals("pabili", OrderStepConstants.SERVICE_PABILI)
        assertEquals("request_rider", OrderStepConstants.SERVICE_REQUEST)

        assertEquals("heading_to_merchant", OrderStepConstants.STEP_HEADING_TO_MERCHANT)
        assertEquals("arrived_at_merchant", OrderStepConstants.STEP_ARRIVED_AT_MERCHANT)
        assertEquals("picked_up", OrderStepConstants.STEP_PICKED_UP)
        assertEquals("heading_to_customer", OrderStepConstants.STEP_HEADING_TO_CUSTOMER)
        assertEquals("arrived_at_customer", OrderStepConstants.STEP_ARRIVED_AT_CUSTOMER)
        assertEquals("delivered", OrderStepConstants.STEP_DELIVERED)
    }

    @Test
    fun `verify rider profile immutability and user mapping`() {
        val rider = Rider(
            uid = "rider_123",
            riderCode = "RD-9821",
            name = "John Doe",
            email = "john@delivio.ph",
            phone = "+63 912 345 6789"
        )
        val user = rider.user
        assertEquals("rider_123", user.id)
        assertEquals("John Doe", user.name)
        assertEquals("john@delivio.ph", user.email)
        assertEquals("rider", user.role)
    }
}
