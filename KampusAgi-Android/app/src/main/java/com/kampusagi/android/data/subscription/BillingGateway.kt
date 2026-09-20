package com.kampusagi.android.data.subscription

import android.app.Activity
import com.kampusagi.android.domain.subscription.PlanPrice

/** Google Play'de sahip olunan (doğrulanmamış) abonelik satın alması. */
data class OwnedPurchase(val productId: String, val purchaseToken: String)

sealed interface StorePurchaseResult {
    data class Purchased(val productId: String, val purchaseToken: String) : StorePurchaseResult
    data object Pending : StorePurchaseResult
    data object Canceled : StorePurchaseResult
}

/**
 * Google Play Billing üzerindeki dar arayüz: repository test edilebilsin diye SDK'dan ayrılmıştır.
 * Hatalar [com.kampusagi.android.domain.common.AppError] olarak fırlatılır.
 */
interface BillingGateway {
    /** Play'de bulunan ürünlerin yerelleştirilmiş fiyatı; bulunamayan ürün sonuçta yoktur. */
    suspend fun queryPrices(productIds: List<String>): Map<String, PlanPrice>

    /**
     * Play satın alma penceresini açar ve sonucu bekler. `accountId` Play'e "obfuscatedAccountId" olarak verilir;
     * sunucu, satın almanın bu hesaba ait olduğunu bununla doğrular (başkasının jetonu kendi hesabına tanımlanamaz).
     */
    suspend fun launchPurchase(activity: Activity, productId: String, accountId: String): StorePurchaseResult

    /** Play hesabında şu an geçerli (PURCHASED) abonelikler. */
    suspend fun queryOwnedPurchases(): List<OwnedPurchase>
}
