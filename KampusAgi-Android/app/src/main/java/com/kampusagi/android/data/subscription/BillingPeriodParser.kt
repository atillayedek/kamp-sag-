package com.kampusagi.android.data.subscription

import com.kampusagi.android.domain.subscription.BillingPeriod
import com.kampusagi.android.domain.subscription.BillingUnit

private val PERIOD_PATTERN = Regex("^P(\\d{1,3})([DWMY])$")

/**
 * Play Billing `pricingPhase.billingPeriod` değerini (ISO-8601, ör. `P1M`, `P3M`, `P1Y`, `P1W`) çözer.
 * Tanınmayan biçim (ör. `P1Y6M` gibi birleşik) `null` döner: dönem gösterilmez, yanlış dönem uydurulmaz.
 */
internal fun parseBillingPeriod(iso: String?): BillingPeriod? {
    val match = PERIOD_PATTERN.matchEntire(iso.orEmpty()) ?: return null
    val count = match.groupValues[1].toInt()
    if (count <= 0) return null
    val unit = when (match.groupValues[2]) {
        "D" -> BillingUnit.DAY
        "W" -> BillingUnit.WEEK
        "M" -> BillingUnit.MONTH
        else -> BillingUnit.YEAR
    }
    return BillingPeriod(count, unit)
}
