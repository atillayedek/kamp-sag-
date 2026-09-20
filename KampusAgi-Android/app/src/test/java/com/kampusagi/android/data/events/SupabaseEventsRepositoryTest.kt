package com.kampusagi.android.data.events

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kampusagi.android.domain.common.AppError
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
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * Ağı taklit eden Ktor MockEngine: RPC/tablo sözleşmesi ve katılım kuralları gerçek SDK üzerinden doğrulanır.
 * Auth eklentisi Android yaşam döngüsü sınıflarına dayandığı için Robolectric altında çalışır.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SupabaseEventsRepositoryTest {

    private val captured = mutableListOf<HttpRequestData>()

    private suspend fun repository(handler: (HttpRequestData) -> Pair<HttpStatusCode, String>): SupabaseEventsRepository {
        val engine = MockEngine { request ->
            captured += request
            val (status, body) = handler(request)
            respond(ByteReadChannel(body), status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val client = createSupabaseClient("https://test.supabase.co", "test-key") {
            httpEngine = engine
            install(Postgrest)
            install(Auth) {
                // Testte diske/SharedPreferences'a yazılmaz: oturum yalnızca bellekte tutulur.
                sessionManager = MemorySessionManager()
                codeVerifierCache = MemoryCodeVerifierCache()
                autoLoadFromStorage = false
                alwaysAutoRefresh = false
            }
        }
        client.auth.importSession(
            UserSession(
                accessToken = "access", refreshToken = "refresh", expiresIn = 3600, tokenType = "bearer",
                user = UserInfo(aud = "authenticated", id = "user-1"),
            ),
            autoRefresh = false,
        )
        return SupabaseEventsRepository(client)
    }

    private val rowsJson = """
        [{"id":"e1","title":"Python Atölyesi","description":"3 saatlik atölye","location":"Bilişim Amfi 2",
          "starts_at":"2026-09-26T15:00:00+00:00","ends_at":"2026-09-26T18:00:00+00:00","university_id":"uni-1",
          "attendee_count":5,"is_joined":true},
         {"id":"e2","title":"Kampüs Buluşması","description":"","location":"Yemekhane",
          "starts_at":"2026-09-27T10:30:00.123456+03:00","ends_at":null,"university_id":null,
          "attendee_count":0,"is_joined":false}]
    """.trimIndent()

    private fun bodyJson(request: HttpRequestData) = Json.parseToJsonElement((request.body as TextContent).text).jsonObject

    @Test
    fun `akis list_campus_events RPC sine limitle gider ve satirlar cozulur`() = runTest {
        val repo = repository { HttpStatusCode.OK to rowsJson }

        val events = repo.getUpcoming()

        val request = captured.single()
        assertEquals("https://test.supabase.co/rest/v1/rpc/list_campus_events", request.url.toString())
        assertEquals("50", bodyJson(request)["p_limit"]?.jsonPrimitive?.content)

        assertEquals(listOf("e1", "e2"), events.map { it.id })
        val first = events[0]
        assertEquals("Python Atölyesi", first.title)
        assertEquals(Instant.parse("2026-09-26T15:00:00Z"), first.startsAt)
        assertEquals(Instant.parse("2026-09-26T18:00:00Z"), first.endsAt)
        assertEquals(true, first.isUniversityEvent)
        assertEquals(5, first.attendeeCount)
        assertEquals(true, first.isJoined)
        val second = events[1]
        assertEquals(false, second.isUniversityEvent)
        assertNull(second.endsAt)
        // Saat dilimi farkı doğru çözülür (+03:00 -> UTC).
        assertEquals(Instant.parse("2026-09-27T07:30:00.123456Z"), second.startsAt)
    }

    @Test
    fun `sunucu hatasi AppError olur`() = runTest {
        val repo = repository { HttpStatusCode.InternalServerError to "<html>boom</html>" }
        try {
            repo.getUpcoming()
            fail("AppError bekleniyordu")
        } catch (e: AppError) {
            assertTrue(e is AppError.Unknown)
        }
    }

    @Test
    fun `katil kendi kullanici kimligiyle katilimci tablosuna yazar`() = runTest {
        val repo = repository { HttpStatusCode.Created to "" }

        repo.setAttending("e1", attending = true)

        val request = captured.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/rest/v1/campus_event_attendees", request.url.encodedPath)
        // PostgREST insert gövdesi satır dizisidir.
        val row = Json.parseToJsonElement((request.body as TextContent).text).jsonArray.single().jsonObject
        assertEquals("e1", row["event_id"]?.jsonPrimitive?.content)
        assertEquals("user-1", row["user_id"]?.jsonPrimitive?.content)
    }

    @Test
    fun `zaten katiliyorsa tekrar katil hata vermez`() = runTest {
        val repo = repository {
            HttpStatusCode.Conflict to """{"code":"23505","message":"duplicate key value violates unique constraint","details":null,"hint":null}"""
        }
        repo.setAttending("e1", attending = true)
        assertEquals(1, captured.size)
    }

    @Test
    fun `katilim reddi Forbidden olur`() = runTest {
        val repo = repository {
            HttpStatusCode.Forbidden to """{"code":"42501","message":"new row violates row-level security policy","details":null,"hint":null}"""
        }
        try {
            repo.setAttending("e1", attending = true)
            fail("AppError bekleniyordu")
        } catch (e: AppError) {
            assertTrue("Forbidden bekleniyordu ama ${e::class.simpleName}", e is AppError.Forbidden)
        }
    }

    @Test
    fun `ayril yalnizca kendi katilim satirini etkinlik ve kullaniciya gore siler`() = runTest {
        val repo = repository { HttpStatusCode.NoContent to "" }

        repo.setAttending("e1", attending = false)

        val request = captured.single()
        assertEquals(HttpMethod.Delete, request.method)
        assertEquals("/rest/v1/campus_event_attendees", request.url.encodedPath)
        assertEquals("eq.e1", request.url.parameters["event_id"])
        assertEquals("eq.user-1", request.url.parameters["user_id"])
    }
}
