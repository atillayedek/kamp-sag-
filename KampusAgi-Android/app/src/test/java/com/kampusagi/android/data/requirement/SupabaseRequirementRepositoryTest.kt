package com.kampusagi.android.data.requirement

import com.kampusagi.android.domain.common.AppError
import com.kampusagi.android.domain.community.PostCategory
import com.kampusagi.android.domain.requirement.AnalysisSource
import com.kampusagi.android.domain.requirement.HelpType
import com.kampusagi.android.domain.requirement.NeedUrgency
import com.kampusagi.android.domain.requirement.ParsedNeed
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant

/** Ağı taklit eden Ktor MockEngine ile: URL, gövde biçimi ve hata eşlemesi gerçek SDK üzerinden doğrulanır. */
class SupabaseRequirementRepositoryTest {

    private val captured = mutableListOf<HttpRequestData>()

    private fun repository(status: HttpStatusCode, body: String): SupabaseRequirementRepository =
        repository { _ -> status to body }

    /** İstek başına yanıt: `handler(url)` -> (durum, gövde). */
    private fun repository(handler: (String) -> Pair<HttpStatusCode, String>): SupabaseRequirementRepository {
        val engine = MockEngine { request ->
            captured += request
            val (status, body) = handler(request.url.toString())
            respond(
                content = ByteReadChannel(body),
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = createSupabaseClient("https://test.supabase.co", "test-key") {
            httpEngine = engine
            install(Functions)
        }
        return SupabaseRequirementRepository(client)
    }

    private fun bodyJson(request: HttpRequestData) =
        Json.parseToJsonElement((request.body as TextContent).text).jsonObject

    private val analyzeOk = """
        {"need":{"title":"Basketbol maçı","category":"SPORTS","helpType":"LOOKING_FOR_PEOPLE","tags":["basketbol"],
        "participantCount":4,"urgency":"HIGH","startsAt":"2026-09-26T15:00:00Z","skills":[]},"source":"claude"}
    """.trimIndent()

    @Test
    fun `analiz istegi parse-need a rawText ile gider ve yanit cozulur`() = runTest {
        val analysis = repository(HttpStatusCode.OK, analyzeOk).analyze("Basketbol için 4 kişi arıyorum")

        val request = captured.single()
        assertEquals("https://test.supabase.co/functions/v1/parse-need", request.url.toString())
        assertEquals("Basketbol için 4 kişi arıyorum", bodyJson(request)["rawText"]?.jsonPrimitive?.content)

        assertEquals(AnalysisSource.AI, analysis.source)
        with(analysis.need) {
            assertEquals("Basketbol maçı", title)
            assertEquals(PostCategory.SPORTS, category)
            assertEquals(HelpType.LOOKING_FOR_PEOPLE, helpType)
            assertEquals(4, participantCount)
            assertEquals(NeedUrgency.HIGH, urgency)
            assertEquals(Instant.parse("2026-09-26T15:00:00Z"), startsAt)
            assertEquals(listOf("basketbol"), tags)
        }
    }

    @Test
    fun `yedek analiz kaynagi BASIC olarak isaretlenir ve null alanlar kabul edilir`() = runTest {
        val body = """{"need":{"title":"x","category":"OTHER","helpType":"LOOKING_FOR_PEOPLE","tags":[],
            "participantCount":null,"urgency":"NORMAL","startsAt":null,"skills":[]},"source":"fallback"}"""
        val analysis = repository(HttpStatusCode.OK, body).analyze("yeterince uzun bir metin")
        assertEquals(AnalysisSource.BASIC, analysis.source)
        assertEquals(null, analysis.need.participantCount)
        assertEquals(null, analysis.need.startsAt)
    }

    @Test
    fun `yayin taslagi sunucunun bekledigi sekilde gonderilir null alanlar acikca yazilir`() = runTest {
        val need = ParsedNeed("Ders notu", PostCategory.ACADEMIC, HelpType.OFFERING_HELP, listOf("python"), null, NeedUrgency.NORMAL, null, listOf("python"))

        val id = repository(HttpStatusCode.OK, """{"requirementId":"req-123"}""").publish("Python dersi veriyorum", need)

        assertEquals("req-123", id)
        val request = captured.first()
        assertEquals("https://test.supabase.co/functions/v1/publish-need", request.url.toString())
        val body = bodyJson(request)
        assertEquals("Python dersi veriyorum", body["rawText"]?.jsonPrimitive?.content)
        val draft = body["draft"]!!.jsonObject
        assertEquals(
            setOf("title", "category", "helpType", "tags", "participantCount", "urgency", "startsAt", "skills"),
            draft.keys,
        )
        // Sunucu şeması .strict() ve bu alanları null OLARAK bekler; eksik anahtar 400 döndürür.
        assertEquals(JsonNull, draft["participantCount"])
        assertEquals(JsonNull, draft["startsAt"])
        assertEquals("OFFERING_HELP", draft["helpType"]?.jsonPrimitive?.content)
    }

    @Test
    fun `yayindan sonra eslesmeler yeni ilan icin hesaplatilir`() = runTest {
        val repo = repository { _ -> HttpStatusCode.OK to """{"requirementId":"req-123"}""" }
        repo.publish("metin metin metin", ParsedNeed("t", PostCategory.OTHER, HelpType.LOOKING_FOR_PEOPLE, emptyList(), null, NeedUrgency.NORMAL, null, emptyList()))

        assertEquals(
            listOf(
                "https://test.supabase.co/functions/v1/publish-need",
                "https://test.supabase.co/functions/v1/recompute-matches",
            ),
            captured.map { it.url.toString() },
        )
        assertEquals("req-123", bodyJson(captured[1])["requirementId"]?.jsonPrimitive?.content)
    }

    @Test
    fun `eslesme hesaplamasi basarisiz olsa da yayin basarili sayilir`() = runTest {
        val repo = repository { url ->
            if (url.endsWith("recompute-matches")) HttpStatusCode.InternalServerError to "boom"
            else HttpStatusCode.OK to """{"requirementId":"req-9"}"""
        }
        val id = repo.publish("metin metin metin", ParsedNeed("t", PostCategory.OTHER, HelpType.LOOKING_FOR_PEOPLE, emptyList(), null, NeedUrgency.NORMAL, null, emptyList()))
        assertEquals("req-9", id)
    }

    @Test
    fun `yayin basarisizsa eslesme hesaplamasi hic tetiklenmez`() = runTest {
        val repo = repository(HttpStatusCode.Forbidden, """{"error":"Bu işlem için öğrenci doğrulamanızın onaylanmış olması gerekiyor."}""")
        try {
            repo.publish("metin metin metin", ParsedNeed("t", PostCategory.OTHER, HelpType.LOOKING_FOR_PEOPLE, emptyList(), null, NeedUrgency.NORMAL, null, emptyList()))
            fail("AppError bekleniyordu")
        } catch (_: AppError) {
        }
        assertEquals(1, captured.size)
    }

    @Test
    fun `sunucunun Turkce hata mesaji kullaniciya aynen iletilir`() = runTest {
        val repo = repository(HttpStatusCode.TooManyRequests, """{"error":"Çok fazla istek gönderdiniz. Lütfen bir süre sonra tekrar deneyin."}""")
        try {
            repo.analyze("yeterince uzun bir metin")
            fail("AppError bekleniyordu")
        } catch (e: AppError) {
            assertTrue("Server bekleniyordu ama ${e::class.simpleName}", e is AppError.Server)
            assertEquals("Çok fazla istek gönderdiniz. Lütfen bir süre sonra tekrar deneyin.", (e as AppError.Server).userMessage)
        }
    }

    @Test
    fun `onaysiz hesap 403 mesajini gorur`() = runTest {
        val repo = repository(HttpStatusCode.Forbidden, """{"error":"Bu işlem için öğrenci doğrulamanızın onaylanmış olması gerekiyor."}""")
        try {
            repo.publish("metin metin metin", ParsedNeed("t", PostCategory.OTHER, HelpType.LOOKING_FOR_PEOPLE, emptyList(), null, NeedUrgency.NORMAL, null, emptyList()))
            fail("AppError bekleniyordu")
        } catch (e: AppError) {
            assertEquals("Bu işlem için öğrenci doğrulamanızın onaylanmış olması gerekiyor.", (e as AppError.Server).userMessage)
        }
    }

    @Test
    fun `govdesi JSON olmayan hata yanitinda durum koduna dusulur`() = runTest {
        val repo = repository(HttpStatusCode.Unauthorized, "Unauthorized")
        try {
            repo.analyze("yeterince uzun bir metin")
            fail("AppError bekleniyordu")
        } catch (e: AppError) {
            assertTrue(e is AppError.Unauthorized)
        }
    }

    @Test
    fun `sunucu hatasi bilinmeyen hata olur`() = runTest {
        val repo = repository(HttpStatusCode.InternalServerError, "<html>gateway</html>")
        try {
            repo.analyze("yeterince uzun bir metin")
            fail("AppError bekleniyordu")
        } catch (e: AppError) {
            assertTrue(e is AppError.Unknown)
        }
    }
}
