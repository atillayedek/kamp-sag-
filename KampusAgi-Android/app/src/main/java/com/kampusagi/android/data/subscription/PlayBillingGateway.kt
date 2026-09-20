package com.kampusagi.android.data.subscription

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.kampusagi.android.domain.common.AppError
import com.kampusagi.android.domain.subscription.PlanPrice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google Play Billing (billing-ktx) uygulaması. Gerçek akış Play Store hesabı ve cihaz gerektirir; birim testlerde
 * [BillingGateway] sahtesi kullanılır, bu sınıf Play iç test kanalında elle doğrulanır (docs/RELEASE_NOTES.md).
 */
@Singleton
class PlayBillingGateway @Inject constructor(
    @ApplicationContext private val context: Context,
) : BillingGateway {

    /** Aktif satın alma akışının sonucu: Play sonucu `PurchasesUpdatedListener` ile ayrı bir çağrıda bildirir. */
    @Volatile
    private var pendingFlow: CompletableDeferred<StorePurchaseResult>? = null

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(PurchasesUpdatedListener { result, purchases -> onPurchasesUpdated(result, purchases) })
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    private val connectionLock = Mutex()

    /** `offerToken` ve `ProductDetails` satın alma penceresi için gerekir; fiyat sorgusunda önbelleğe alınır. */
    private val detailsByProduct = mutableMapOf<String, ProductDetails>()

    override suspend fun queryPrices(productIds: List<String>): Map<String, PlanPrice> {
        if (productIds.isEmpty()) return emptyMap()
        val details = queryDetails(productIds)
        return details.mapNotNull { detail -> detail.toPlanPrice()?.let { detail.productId to it } }.toMap()
    }

    override suspend fun launchPurchase(activity: Activity, productId: String, accountId: String): StorePurchaseResult {
        val details = detailsByProduct[productId] ?: queryDetails(listOf(productId)).firstOrNull()
            ?: throw AppError.Server("Bu plan şu an satın alınamıyor.")
        val offer = details.recurringOffer() ?: throw AppError.Server("Bu plan şu an satın alınamıyor.")

        ensureConnected()
        val flow = CompletableDeferred<StorePurchaseResult>()
        pendingFlow = flow
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offer.offerToken)
                        .build(),
                ),
            )
            .setObfuscatedAccountId(accountId)
            .build()

        val launch = client.launchBillingFlow(activity, params)
        if (launch.responseCode != BillingClient.BillingResponseCode.OK) {
            pendingFlow = null
            return launch.toResultOrThrow()
        }
        return try {
            flow.await()
        } finally {
            pendingFlow = null
        }
    }

    override suspend fun queryOwnedPurchases(): List<OwnedPurchase> {
        // Play Store'a ulaşılamıyorsa (Play yok / geçici kesinti) doğrulanacak bir satın alma da yoktur; abonelik Play'de
        // durur ve sonraki açılışta yeniden denenir. Bu yüzden burada hata değil boş liste döner.
        try {
            ensureConnected()
        } catch (_: AppError.Network) {
            return emptyList()
        }
        val result = client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
        )
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) throw result.billingResult.toError()
        return result.purchasesList
            .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            .flatMap { purchase -> purchase.products.map { OwnedPurchase(it, purchase.purchaseToken) } }
    }

    private suspend fun queryDetails(productIds: List<String>): List<ProductDetails> {
        ensureConnected()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                productIds.map {
                    QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(BillingClient.ProductType.SUBS).build()
                },
            )
            .build()
        val result = client.queryProductDetails(params)
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) throw result.billingResult.toError()
        val found = result.productDetailsList.orEmpty()
        found.forEach { detailsByProduct[it.productId] = it }
        return found
    }

    private fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        val flow = pendingFlow ?: return
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val purchase = purchases?.firstOrNull()
                when {
                    purchase == null -> flow.completeExceptionally(AppError.Unknown())
                    purchase.purchaseState == Purchase.PurchaseState.PENDING -> flow.complete(StorePurchaseResult.Pending)
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED ->
                        flow.complete(StorePurchaseResult.Purchased(purchase.products.first(), purchase.purchaseToken))
                    else -> flow.completeExceptionally(AppError.Unknown())
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> flow.complete(StorePurchaseResult.Canceled)
            else -> flow.completeExceptionally(result.toError())
        }
    }

    private suspend fun ensureConnected() = connectionLock.withLock {
        if (client.isReady) return@withLock
        val outcome = CompletableDeferred<BillingResult>()
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                outcome.complete(result)
            }

            override fun onBillingServiceDisconnected() = Unit
        })
        val result = outcome.await()
        if (result.responseCode != BillingClient.BillingResponseCode.OK) throw result.toError()
    }
}

/** Yenilenen (sonsuz tekrarlı) fazın fiyatı; deneme/ilk dönem indirimi fazları atlanır. */
private fun ProductDetails.recurringOffer(): ProductDetails.SubscriptionOfferDetails? =
    subscriptionOfferDetails?.let { offers -> offers.firstOrNull { it.offerId == null } ?: offers.firstOrNull() }

private fun ProductDetails.toPlanPrice(): PlanPrice? {
    val phase = recurringOffer()?.pricingPhases?.pricingPhaseList
        ?.lastOrNull { it.recurrenceMode == ProductDetails.RecurrenceMode.INFINITE_RECURRING }
        ?: recurringOffer()?.pricingPhases?.pricingPhaseList?.lastOrNull()
        ?: return null
    return PlanPrice(phase.formattedPrice, parseBillingPeriod(phase.billingPeriod))
}

private fun BillingResult.toResultOrThrow(): StorePurchaseResult = when (responseCode) {
    BillingClient.BillingResponseCode.USER_CANCELED -> StorePurchaseResult.Canceled
    else -> throw toError()
}

/** Play yanıt kodlarını tipli hataya çevirir; ağ/servis sorunları [AppError.Network], ürün sorunları kullanıcı mesajıdır. */
private fun BillingResult.toError(): AppError = when (responseCode) {
    BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
    BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
    BillingClient.BillingResponseCode.NETWORK_ERROR,
    BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
    -> AppError.Network()
    BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> AppError.Server("Bu plan şu an satın alınamıyor.")
    BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> AppError.Server("Bu plana zaten sahipsin. Planın birazdan etkinleşecek.")
    else -> AppError.Unknown()
}
