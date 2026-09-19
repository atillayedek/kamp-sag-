package com.kampusagi.android.data.requirement

import com.kampusagi.android.data.common.mapErrors
import com.kampusagi.android.data.community.parseInstant
import com.kampusagi.android.domain.common.AppError
import com.kampusagi.android.domain.community.PostCategory
import com.kampusagi.android.domain.requirement.AnalysisSource
import com.kampusagi.android.domain.requirement.HelpType
import com.kampusagi.android.domain.requirement.NeedAnalysis
import com.kampusagi.android.domain.requirement.NeedUrgency
import com.kampusagi.android.domain.requirement.ParsedNeed
import com.kampusagi.android.domain.requirement.RequirementRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

class SupabaseRequirementRepository @Inject constructor(
    private val client: SupabaseClient,
) : RequirementRepository {

    @Serializable
    private data class NeedDto(
        val title: String,
        val category: String,
        val helpType: String,
        val tags: List<String> = emptyList(),
        val participantCount: Int? = null,
        val urgency: String,
        val startsAt: String? = null,
        val skills: List<String> = emptyList(),
    )

    @Serializable
    private data class AnalyzeResponse(val need: NeedDto, val source: String)

    @Serializable
    private data class PublishResponse(@SerialName("requirementId") val requirementId: String)

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun analyze(rawText: String): NeedAnalysis = mapErrors(preferServerMessage = true) {
        val response = client.functions.invoke(function = "parse-need", body = buildJsonObject { put("rawText", rawText) })
        val parsed = decode<AnalyzeResponse>(response.bodyAsText())
        NeedAnalysis(
            need = parsed.need.toDomain(),
            source = if (parsed.source == "claude") AnalysisSource.AI else AnalysisSource.BASIC,
        )
    }

    override suspend fun publish(rawText: String, need: ParsedNeed): String = mapErrors(preferServerMessage = true) {
        val response = client.functions.invoke(
            function = "publish-need",
            body = buildJsonObject {
                put("rawText", rawText)
                put("draft", need.toDraftJson())
            },
        )
        val requirementId = decode<PublishResponse>(response.bodyAsText()).requirementId
        computeMatches(requirementId)
        requirementId
    }

    /**
     * İlan yayınlanır yayınlanmaz eşleşmeler hesaplatılır. İlan zaten kaydedildiği için burada bir hata
     * yayını BAŞARISIZ saymaz; kullanıcı Eşleşmeler ekranında "Yenile" ile hesaplamayı yeniden tetikleyebilir.
     */
    private suspend fun computeMatches(requirementId: String) {
        try {
            client.functions.invoke(function = "recompute-matches", body = buildJsonObject { put("requirementId", requirementId) })
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    private inline fun <reified T> decode(body: String): T =
        try {
            json.decodeFromString<T>(body)
        } catch (e: kotlinx.serialization.SerializationException) {
            throw AppError.Unknown(e)
        }

    private fun NeedDto.toDomain() = ParsedNeed(
        title = title,
        category = PostCategory.fromRawValue(category),
        helpType = HelpType.fromRawValue(helpType),
        tags = tags,
        participantCount = participantCount,
        urgency = NeedUrgency.fromRawValue(urgency),
        startsAt = startsAt?.let { runCatching { parseInstant(it) }.getOrNull() },
        skills = skills,
    )

    /**
     * Sunucu şeması `.strict()` ve `participantCount`/`startsAt` alanlarını `null` olarak BEKLER (anahtar eksik olamaz);
     * bu yüzden null'lar açıkça `JsonNull` yazılır.
     */
    private fun ParsedNeed.toDraftJson(): JsonObject = buildJsonObject {
        put("title", title)
        put("category", category.rawValue)
        put("helpType", helpType.rawValue)
        put("tags", buildJsonArray { tags.forEach { add(JsonPrimitive(it)) } })
        put("participantCount", participantCount?.let { JsonPrimitive(it) } ?: JsonNull)
        put("urgency", urgency.rawValue)
        put("startsAt", startsAt?.let { JsonPrimitive(it.toString()) } ?: JsonNull)
        put("skills", buildJsonArray { skills.forEach { add(JsonPrimitive(it)) } })
    }
}
