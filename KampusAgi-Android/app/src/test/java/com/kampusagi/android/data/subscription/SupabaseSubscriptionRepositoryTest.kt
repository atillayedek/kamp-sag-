package com.kampusagi.android.data.subscription

import com.kampusagi.android.domain.common.AppError
import com.kampusagi.android.domain.subscription.BillingPeriod
import com.kampusagi.android.domain.subscription.BillingUnit
import com.kampusagi.android.domain.subscription.PlanTier
import com.kampusagi.android.testutil.FakeBillingGateway
import com.kampusagi.android.testutil.monthlyPrice
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Ağı taklit eden Ktor MockEngine: sunucu sözleşmesi (tablo/RPC/Edge Function) gerçek SDK üzerinden doğrulanır. */
class SupabaseSubscriptionRepositoryTest {

    private val captured = mutableListOf<HttpRequestData>()
    private val gateway = FakeBillingGateway()

    private val plansJson = """
        [{"id":"FREE","title":"Free","features":["A","B"],"play_product_id":null,"is_featured":false},
         {"id":"PREMIUM","title":"Premium","features":["C"],"play_product_id":"kampusagi_premium","is_featured":true},
         {"id":"COMMUNITY_PRO","title":"Community Pro","features":[],"play_product_id":"kampusagi_community_pro","is_featured":false}]
    """.trimIndent()

    private fun repository(handler: (String) -> Pair<HttpStatusCode, String>): SupabaseSubscriptionRepository {
        val engine = MockEngine { request ->
            captured += request
            val (status, body) = handler(request.url.encodedPath)
            respond(ByteReadChannel(body), status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val client = createSupabaseClient("https://test.supabase.co", "test-key") {
            httpEngine = engine
            install(Postgrest)
            install(Functions)
        }
        return SupabaseSubscriptionRepository(client, gateway)
    }

    private fun defaultHandler(path: String): Pair<HttpStatusCode, String> = when {
        path.endsWith("/subscription_plans") -> HttpStatusCode.OK to plansJson
        path.endsWith("/rpc/my_plan") -> HttpStatusCode.OK to "\"PREMIUM\""
        else -> HttpStatusCode.NotFound to "{}"
    }

    private fun bodyJson(request: HttpRequestData) = Json.parseToJsonElement((request.body as TextContent).text).jsonObject

    @Test
    fun `plan listesi sunucudan gelir fiyatlar Play den eklenir`() = runTest {
        gateway.prices = mapOf("kampusagi_premium" to monthlyPrice)
        val overview = repository(::defaultHandler).getOverview()

        assertEquals(PlanTier.PREMIUM, overview.currentTier)
        assertEquals(listOf(PlanTier.FREE, PlanTier.PREMIUM, PlanTier.COMMUNITY_PRO), overview.plans.map { it.tier })
        val free = overview.plans[0]
        assertEquals(listOf("A", "B"), free.features)
        assertNull(free.productId)
        assertNull(free.price)
        val premium = overview.plans[1]
        assertEquals("kampusagi_premium", premium.productId)
        assertEquals(true, premium.isFeatured)
        assertEquals(monthlyPrice, premium.price)
        assertEquals(BillingPeriod(1, BillingUnit.MONTH), premium.price?.period)
        assertTrue(premium.isPurchasable)
        // Play'de bulunmayan ürün: fiyat yok -> satın alınamaz (fiyat/dönem asla koda yazılı değil).
        assertNull(overview.plans[2].price)
        assertEquals(false, overview.plans[2].isPurchasable)
        // Yalnızca ücretli planların ürün kimlikleri Play'e sorulur.
        assertEquals(listOf(listOf("kampusagi_premium", "kampusagi_community_pro")), gateway.queriedProducts)
    }

    @Test
    fun `Play kullanilamiyorsa plan listesi yine gelir ama fiyat ve satin alma kapali olur`() = runTest {
        gateway.pricesError = AppError.Network()
        val overview = repository(::defaultHandler).getOverview()

        assertEquals(3, overview.plans.size)
        assertTrue(overview.plans.all { it.price == null })
        assertTrue(overview.plans.none { it.isPurchasable })
    }

    @Test
    fun `bilinmeyen plan kimligi ve gecerli plan FREE sayilir yani hak verilmez`() = runTest {
        val overview = repository { path ->
            when {
                path.endsWith("/subscription_plans") -> HttpStatusCode.OK to """[{"id":"GOLD","title":"Altın","features":[],"play_product_id":"x","is_featured":false}]"""
                path.endsWith("/rpc/my_plan") -> HttpStatusCode.OK to "\"GOLD\""
                else -> HttpStatusCode.NotFound to "{}"
            }
        }.getOverview()
        assertEquals(PlanTier.FREE, overview.currentTier)
        assertEquals(PlanTier.FREE, overview.plans.single().tier)
    }

    @Test
    fun `sunucu hatasi AppError olur`() = runTest {
        val repo = repository { HttpStatusCode.InternalServerError to "<html>boom</html>" }
        try {
            repo.getOverview()
            fail("AppError bekleniyordu")
        } catch (e: AppError) {
            assertTrue(e is AppError.Unknown)
        }
    }

    @Test
    fun `sahip olunan abonelik verify-purchase e urun ve jetonla gonderilir`() = runTest {
        gateway.owned = listOf(OwnedPurchase("kampusagi_premium", "jeton-abc"))
        val repo = repository { HttpStatusCode.OK to """{"plan":"PREMIUM","expiresAt":"2026-10-19T12:00:00.000Z"}""" }

        val tier = repo.restorePurchases()

        assertEquals(PlanTier.PREMIUM, tier)
        val request = captured.single()
        assertEquals("https://test.supabase.co/functions/v1/verify-purchase", request.url.toString())
        assertEquals("kampusagi_premium", bodyJson(request)["productId"]?.jsonPrimitive?.content)
        assertEquals("jeton-abc", bodyJson(request)["purchaseToken"]?.jsonPrimitive?.content)
    }

    @Test
    fun `sahip olunan abonelik yoksa sunucuya hic istek gitmez ve null doner`() = runTest {
        val repo = repository { HttpStatusCode.OK to "{}" }
        assertNull(repo.restorePurchases())
        assertTrue(captured.isEmpty())
    }

    @Test
    fun `birden cok abonelikte en yuksek plan doner`() = runTest {
        gateway.owned = listOf(OwnedPurchase("kampusagi_premium", "t1"), OwnedPurchase("kampusagi_community_pro", "t2"))
        var call = 0
        val repo = repository { _ ->
            call++
            HttpStatusCode.OK to (if (call == 1) """{"plan":"PREMIUM"}""" else """{"plan":"COMMUNITY_PRO"}""")
        }

        assertEquals(PlanTier.COMMUNITY_PRO, repo.restorePurchases())
        assertEquals(2, captured.size)
    }

    @Test
    fun `sunucunun Turkce reddi kullaniciya aynen iletilir`() = runTest {
        gateway.owned = listOf(OwnedPurchase("kampusagi_premium", "baskasinin"))
        val repo = repository { HttpStatusCode.Forbidden to """{"error":"Bu satın alma bu hesaba ait değil."}""" }
        try {
            repo.restorePurchases()
            fail("AppError bekleniyordu")
        } catch (e: AppError) {
            assertTrue("Server bekleniyordu ama ${e::class.simpleName}", e is AppError.Server)
            assertEquals("Bu satın alma bu hesaba ait değil.", (e as AppError.Server).userMessage)
        }
    }
}
