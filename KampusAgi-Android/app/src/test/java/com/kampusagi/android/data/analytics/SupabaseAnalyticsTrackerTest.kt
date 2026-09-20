package com.kampusagi.android.data.analytics

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kampusagi.android.domain.analytics.AnalyticsEvent
import com.kampusagi.android.testutil.FakePrivacyPreferenceRepository
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Analitik izleyici: rıza, oturum ve hata yalıtımı. Tracker gerçek `Dispatchers.IO` üzerinde ateşle-unut çalıştığı için
 * olumlu durum beklemeyle, olumsuz durum "kısa süre içinde istek gelmedi" ile doğrulanır.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SupabaseAnalyticsTrackerTest {

    private val requests = CopyOnWriteArrayList<HttpRequestData>()

    private suspend fun tracker(
        privacy: FakePrivacyPreferenceRepository = FakePrivacyPreferenceRepository(),
        signedIn: Boolean = true,
        status: HttpStatusCode = HttpStatusCode.NoContent,
    ): SupabaseAnalyticsTracker {
        val engine = MockEngine { request ->
            requests += request
            respond(ByteReadChannel(""), status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val client = createSupabaseClient("https://test.supabase.co", "test-key") {
            httpEngine = engine
            install(Postgrest)
            install(Auth) {
                sessionManager = MemorySessionManager()
                codeVerifierCache = MemoryCodeVerifierCache()
                autoLoadFromStorage = false
                alwaysAutoRefresh = false
            }
        }
        if (signedIn) {
            client.auth.importSession(
                UserSession("access", "refresh", expiresIn = 3600, tokenType = "bearer", user = UserInfo(aud = "authenticated", id = "user-1")),
                autoRefresh = false,
            )
        }
        return SupabaseAnalyticsTracker(client, privacy, Dispatchers.IO)
    }

    private fun awaitRequests(count: Int, timeoutMs: Long = 5_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (requests.size < count && System.currentTimeMillis() < deadline) Thread.sleep(10)
    }

    private fun assertNoRequestSoon() {
        Thread.sleep(400)
        assertTrue("istek gitmemeliydi ama ${requests.size} istek gitti", requests.isEmpty())
    }

    @Test
    fun `rıza acik ve oturum varsa olay adi RPC ye gider ve baska hicbir sey gonderilmez`() = runBlocking {
        val tracker = tracker()

        tracker.track(AnalyticsEvent.POST_CREATED)
        awaitRequests(1)

        val request = requests.single()
        assertEquals("/rest/v1/rpc/track_event", request.url.encodedPath)
        val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
        // Yalnızca olay adı: içerik/özellik/cihaz bilgisi alanı yok.
        assertEquals(setOf("p_name"), body.keys)
        assertEquals("POST_CREATED", body["p_name"]?.jsonPrimitive?.content)
    }

    @Test
    fun `kullanici istatistik paylasimini kapattiysa hicbir sey gonderilmez`() = runBlocking {
        val tracker = tracker(privacy = FakePrivacyPreferenceRepository(initial = false))
        tracker.track(AnalyticsEvent.MESSAGE_SENT)
        assertNoRequestSoon()
    }

    @Test
    fun `oturum yoksa hicbir sey gonderilmez`() = runBlocking {
        val tracker = tracker(signedIn = false)
        tracker.track(AnalyticsEvent.SIGN_UP_COMPLETED)
        assertNoRequestSoon()
    }

    @Test
    fun `sunucu hatasi cagiriyi bozmaz ve olay yeniden denenmez`() = runBlocking {
        val tracker = tracker(status = HttpStatusCode.InternalServerError)

        tracker.track(AnalyticsEvent.PURCHASE_COMPLETED)
        awaitRequests(1)
        Thread.sleep(300)

        assertEquals("tek deneme, yeniden deneme yok", 1, requests.size)
    }

    @Test
    fun `olay adlari sunucudaki izin listesiyle birebir aynidir`() {
        // supabase/migrations/202609190016_analytics_events.sql -> check constraint ve track_event listesi.
        assertEquals(
            setOf("SIGN_UP_COMPLETED", "DOCUMENT_UPLOADED", "POST_CREATED", "REQUIREMENT_PUBLISHED", "CHAT_STARTED", "MESSAGE_SENT", "PURCHASE_COMPLETED"),
            AnalyticsEvent.entries.map { it.name }.toSet(),
        )
    }
}
