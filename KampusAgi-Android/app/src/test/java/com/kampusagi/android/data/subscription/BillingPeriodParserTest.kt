package com.kampusagi.android.data.subscription

import com.kampusagi.android.domain.subscription.BillingPeriod
import com.kampusagi.android.domain.subscription.BillingUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BillingPeriodParserTest {

    @Test
    fun `tanidik ISO donemleri cozulur`() {
        assertEquals(BillingPeriod(1, BillingUnit.MONTH), parseBillingPeriod("P1M"))
        assertEquals(BillingPeriod(3, BillingUnit.MONTH), parseBillingPeriod("P3M"))
        assertEquals(BillingPeriod(6, BillingUnit.MONTH), parseBillingPeriod("P6M"))
        assertEquals(BillingPeriod(1, BillingUnit.YEAR), parseBillingPeriod("P1Y"))
        assertEquals(BillingPeriod(1, BillingUnit.WEEK), parseBillingPeriod("P1W"))
        assertEquals(BillingPeriod(7, BillingUnit.DAY), parseBillingPeriod("P7D"))
    }

    @Test
    fun `birlesik bozuk veya bos donem null doner yani donem uydurulmaz`() {
        assertNull(parseBillingPeriod("P1Y6M"))
        assertNull(parseBillingPeriod("P0M"))
        assertNull(parseBillingPeriod("1M"))
        assertNull(parseBillingPeriod("PM"))
        assertNull(parseBillingPeriod("P1H"))
        assertNull(parseBillingPeriod(""))
        assertNull(parseBillingPeriod(null))
    }
}
