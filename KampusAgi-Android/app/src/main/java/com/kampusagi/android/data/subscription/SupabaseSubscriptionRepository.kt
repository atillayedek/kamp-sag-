package com.kampusagi.android.data.subscription

import android.app.Activity
import com.kampusagi.android.data.common.mapErrors
import com.kampusagi.android.domain.common.AppError
import com.kampusagi.android.domain.subscription.PlanPrice
import com.kampusagi.android.domain.subscription.PlanTier
import com.kampusagi.android.domain.subscription.PlansOverview
import com.kampusagi.android.domain.subscription.PurchaseResult
import com.kampusagi.android.domain.subscription.SubscriptionPlan
import com.kampusagi.android.domain.subscription.SubscriptionRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

class SupabaseSubscriptionRepository @Inject constructor(
    private val client: SupabaseClient,
    private val billing: BillingGateway,
) : SubscriptionRepository {

    @Serializable
    private data class PlanRow(
        val id: String,
        val title: String,
        val features: List<String> = emptyList(),
        @SerialName("play_product_id") val playProductId: String? = null,
        @SerialName("is_featured") val isFeatured: Boolean = false,
    )

    @Serializable
    private data class VerifyResponse(val plan: String)

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getOverview(): PlansOverview = mapErrors {
        val rows = client.from("subscription_plans")
            .select(Columns.list("id", "title", "features", "play_product_id", "is_featured")) {
                order("sort_order", Order.ASCENDING)
            }
            .decodeList<PlanRow>()
        val currentTier = PlanTier.fromRawValue(client.postgrest.rpc("my_plan").decodeAs<String>())

        // Play kullanılamıyorsa (Play Store yok/ağ yok) plan listesi yine gösterilir; yalnızca fiyat ve satın alma kapanır.
        val prices = pricesFor(rows.mapNotNull { it.playProductId })
        val plans = rows.map { row ->
            SubscriptionPlan(
                tier = PlanTier.fromRawValue(row.id),
                title = row.title,
                features = row.features,
                isFeatured = row.isFeatured,
                productId = row.playProductId,
                price = row.playProductId?.let { prices[it] },
            )
        }
        PlansOverview(plans, currentTier)
    }

    override suspend fun purchase(activity: Activity, plan: SubscriptionPlan): PurchaseResult = mapErrors(preferServerMessage = true) {
        val productId = plan.productId ?: throw AppError.ProductUnavailable()
        val accountId = client.auth.currentUserOrNull()?.id ?: throw AppError.Unauthorized()
        when (val result = billing.launchPurchase(activity, productId, accountId)) {
            is StorePurchaseResult.Purchased -> PurchaseResult.Success(verify(result.productId, result.purchaseToken))
            StorePurchaseResult.Pending -> PurchaseResult.Pending
            StorePurchaseResult.Canceled -> PurchaseResult.Canceled
        }
    }

    override suspend fun restorePurchases(): PlanTier? = mapErrors(preferServerMessage = true) {
        // Aynı anda birden çok abonelik varsa en yüksek plan sayılır (enum sırası: FREE < PREMIUM < COMMUNITY_PRO).
        billing.queryOwnedPurchases()
            .map { verify(it.productId, it.purchaseToken) }
            .maxOrNull()
    }

    private suspend fun pricesFor(productIds: List<String>): Map<String, PlanPrice> =
        try {
            billing.queryPrices(productIds)
        } catch (e: CancellationException) {
            throw e
        } catch (_: AppError) {
            emptyMap()
        }

    /** Sunucu (Play Developer API ile) doğrular ve hakkı yazar; dönen plan geçerli hak planıdır. */
    private suspend fun verify(productId: String, purchaseToken: String): PlanTier {
        val response = client.functions.invoke(
            function = "verify-purchase",
            body = buildJsonObject {
                put("productId", productId)
                put("purchaseToken", purchaseToken)
            },
        )
        return PlanTier.fromRawValue(json.decodeFromString<VerifyResponse>(response.bodyAsText()).plan)
    }
}
