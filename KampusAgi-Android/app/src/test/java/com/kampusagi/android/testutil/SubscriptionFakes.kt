package com.kampusagi.android.testutil

import android.app.Activity
import com.kampusagi.android.data.subscription.BillingGateway
import com.kampusagi.android.data.subscription.OwnedPurchase
import com.kampusagi.android.data.subscription.StorePurchaseResult
import com.kampusagi.android.domain.common.AppError
import com.kampusagi.android.domain.subscription.BillingPeriod
import com.kampusagi.android.domain.subscription.BillingUnit
import com.kampusagi.android.domain.subscription.PlanPrice
import com.kampusagi.android.domain.subscription.PlanTier
import com.kampusagi.android.domain.subscription.PlansOverview
import com.kampusagi.android.domain.subscription.PurchaseResult
import com.kampusagi.android.domain.subscription.SubscriptionPlan
import com.kampusagi.android.domain.subscription.SubscriptionRepository

val monthlyPrice = PlanPrice("₺49,99", BillingPeriod(1, BillingUnit.MONTH))

fun freePlan() = SubscriptionPlan(PlanTier.FREE, "Free", listOf("Özellik A"), false, null, null)

fun premiumPlan(price: PlanPrice? = monthlyPrice) =
    SubscriptionPlan(PlanTier.PREMIUM, "Premium", listOf("Özellik B"), true, "kampusagi_premium", price)

fun proPlan(price: PlanPrice? = null) =
    SubscriptionPlan(PlanTier.COMMUNITY_PRO, "Community Pro", listOf("Özellik C"), false, "kampusagi_community_pro", price)

class FakeSubscriptionRepository : SubscriptionRepository {
    var overviewBlock: suspend () -> PlansOverview = { PlansOverview(listOf(freePlan(), premiumPlan()), PlanTier.FREE) }
    var purchaseBlock: suspend (SubscriptionPlan) -> PurchaseResult = { PurchaseResult.Success(it.tier) }
    var restoreBlock: suspend () -> PlanTier? = { null }
    val purchases = mutableListOf<SubscriptionPlan>()
    var restoreCalls = 0

    override suspend fun getOverview() = overviewBlock()

    override suspend fun purchase(activity: Activity, plan: SubscriptionPlan): PurchaseResult {
        purchases += plan
        return purchaseBlock(plan)
    }

    override suspend fun restorePurchases(): PlanTier? {
        restoreCalls++
        return restoreBlock()
    }
}

class FakeBillingGateway : BillingGateway {
    var prices: Map<String, PlanPrice> = emptyMap()
    var pricesError: AppError? = null
    var owned: List<OwnedPurchase> = emptyList()
    var purchaseResult: StorePurchaseResult = StorePurchaseResult.Canceled
    val launched = mutableListOf<Pair<String, String>>()
    val queriedProducts = mutableListOf<List<String>>()

    override suspend fun queryPrices(productIds: List<String>): Map<String, PlanPrice> {
        queriedProducts += productIds
        pricesError?.let { throw it }
        return prices
    }

    override suspend fun launchPurchase(activity: Activity, productId: String, accountId: String): StorePurchaseResult {
        launched += productId to accountId
        return purchaseResult
    }

    override suspend fun queryOwnedPurchases() = owned
}
