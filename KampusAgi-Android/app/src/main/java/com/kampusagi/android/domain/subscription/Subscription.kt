package com.kampusagi.android.domain.subscription

import android.app.Activity

enum class PlanTier {
    FREE,
    PREMIUM,
    COMMUNITY_PRO,
    ;

    companion object {
        /** Bilinmeyen değer (ör. sunucuya sonradan eklenen plan) güvenle FREE sayılır: hak verilmez. */
        fun fromRawValue(value: String?): PlanTier = entries.firstOrNull { it.name == value } ?: FREE
    }
}

enum class BillingUnit { DAY, WEEK, MONTH, YEAR }

/** ISO-8601 faturalama dönemi (`P1M`, `P3M`, `P1Y`…): Play Billing'den gelir, koda yazılmaz. */
data class BillingPeriod(val count: Int, val unit: BillingUnit)

/** Google Play'in bu kullanıcı için yerelleştirilmiş fiyatı (ör. "₺49,99") ve yenileme dönemi. */
data class PlanPrice(val formattedPrice: String, val period: BillingPeriod?)

/**
 * Sunucudaki (etkin) plan + Play Billing fiyatı. `price == null`: Play'de ürün bulunamadı (henüz tanımlı değil ya da
 * Play Store kullanılamıyor) -> satın alma düğmesi kapalı gösterilir. `productId == null`: ücretsiz plan.
 */
data class SubscriptionPlan(
    val tier: PlanTier,
    val title: String,
    val features: List<String>,
    val isFeatured: Boolean,
    val productId: String?,
    val price: PlanPrice?,
) {
    val isPurchasable: Boolean get() = productId != null && price != null
}

data class PlansOverview(val plans: List<SubscriptionPlan>, val currentTier: PlanTier)

sealed interface PurchaseResult {
    /** Play ödemeyi aldı, sunucu doğruladı ve hak yazıldı. */
    data class Success(val tier: PlanTier) : PurchaseResult

    /** Ödeme henüz onaylanmadı (ör. nakit/yavaş yöntem); onaylanınca hak, sonraki doğrulamada gelir. */
    data object Pending : PurchaseResult

    data object Canceled : PurchaseResult
}

/**
 * Tüm metotlar hata durumunda [com.kampusagi.android.domain.common.AppError] fırlatır.
 * `purchase` Play satın alma penceresini açmak için [Activity] ister (yalnızca çağrı boyunca kullanılır, saklanmaz).
 */
interface SubscriptionRepository {
    suspend fun getOverview(): PlansOverview

    suspend fun purchase(activity: Activity, plan: SubscriptionPlan): PurchaseResult

    /**
     * Play hesabında zaten sahip olunan abonelikleri sunucuya doğrulatır (yenileme, yeni cihaz, yarım kalan doğrulama).
     * Doğrulanan en yüksek plan döner; sahip olunan abonelik yoksa `null`.
     */
    suspend fun restorePurchases(): PlanTier?
}
