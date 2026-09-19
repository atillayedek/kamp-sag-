package com.kampusagi.android.data.match

import com.kampusagi.android.data.common.mapErrors
import com.kampusagi.android.domain.common.AppError
import com.kampusagi.android.domain.match.MatchCandidate
import com.kampusagi.android.domain.match.MatchRepository
import com.kampusagi.android.domain.match.MatchesResult
import com.kampusagi.android.domain.match.MyRequirement
import com.kampusagi.android.domain.match.PublicProfile
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import javax.inject.Inject

class SupabaseMatchRepository @Inject constructor(
    private val client: SupabaseClient,
) : MatchRepository {

    @Serializable
    private data class RequirementRow(val id: String, val title: String)

    @Serializable
    private data class ProfileRef(
        @SerialName("full_name") val fullName: String,
        val department: String? = null,
        @SerialName("avatar_url") val avatarUrl: String? = null,
    )

    @Serializable
    private data class MatchRow(
        val id: String,
        @SerialName("matched_user_id") val matchedUserId: String,
        val score: Double,
        @SerialName("score_breakdown") val scoreBreakdown: JsonObject? = null,
        val profiles: ProfileRef? = null,
    )

    @Serializable
    private data class TagRow(
        @SerialName("author_id") val authorId: String,
        val tags: List<String> = emptyList(),
    )

    @Serializable
    private data class UniversityRef(val name: String)

    @Serializable
    private data class PublicProfileRow(
        val id: String,
        @SerialName("full_name") val fullName: String,
        val department: String? = null,
        @SerialName("avatar_url") val avatarUrl: String? = null,
        val universities: UniversityRef? = null,
    )

    @Serializable
    private data class LatestRequirementRow(
        val title: String,
        val tags: List<String> = emptyList(),
    )

    override suspend fun getMatches(): MatchesResult = mapErrors {
        val requirement = client.from("requirements")
            .select(Columns.list("id", "title")) {
                filter {
                    eq("author_id", requireUserId())
                    eq("status", "PUBLISHED")
                }
                order("created_at", Order.DESCENDING)
                limit(1)
            }
            .decodeList<RequirementRow>()
            .firstOrNull()
            ?: return@mapErrors MatchesResult(requirement = null, matches = emptyList())

        MatchesResult(MyRequirement(requirement.id, requirement.title), loadMatches(requirement.id))
    }

    override suspend fun refreshMatches(requirementId: String): MatchesResult = mapErrors {
        client.functions.invoke(function = "recompute-matches", body = buildJsonObject { put("requirementId", requirementId) })
        getMatches()
    }

    override suspend fun getPublicProfile(userId: String): PublicProfile = mapErrors {
        val profile = client.from("profiles")
            .select(Columns.raw("id, full_name, department, avatar_url, universities(name)")) { filter { eq("id", userId) } }
            .decodeList<PublicProfileRow>()
            .firstOrNull() ?: throw AppError.NotFound()

        val latest = client.from("requirements")
            .select(Columns.list("title", "tags")) {
                filter {
                    eq("author_id", userId)
                    eq("status", "PUBLISHED")
                }
                order("created_at", Order.DESCENDING)
                limit(1)
            }
            .decodeList<LatestRequirementRow>()
            .firstOrNull()

        PublicProfile(
            id = profile.id,
            fullName = profile.fullName,
            department = profile.department,
            universityName = profile.universities?.name,
            avatarUrl = profile.avatarUrl,
            latestRequirementTitle = latest?.title,
            tags = latest?.tags.orEmpty(),
        )
    }

    private suspend fun loadMatches(requirementId: String): List<MatchCandidate> {
        val rows = client.from("matches")
            .select(Columns.raw("id, matched_user_id, score, score_breakdown, profiles(full_name, department, avatar_url)")) {
                filter {
                    eq("requirement_id", requirementId)
                    eq("status", "SUGGESTED")
                }
                order("score", Order.DESCENDING)
            }
            .decodeList<MatchRow>()
        if (rows.isEmpty()) return emptyList()

        // Adayın en son yayınladığı ilanın etiketleri (kart çipleri); kullanıcı başına ilk (en yeni) satır.
        val tagsByUser = client.from("requirements")
            .select(Columns.list("author_id", "tags")) {
                filter {
                    isIn("author_id", rows.map { it.matchedUserId })
                    eq("status", "PUBLISHED")
                }
                order("created_at", Order.DESCENDING)
            }
            .decodeList<TagRow>()
            .groupBy { it.authorId }
            .mapValues { (_, list) -> list.first().tags }

        return rows.map { row ->
            MatchCandidate(
                matchId = row.id,
                userId = row.matchedUserId,
                fullName = row.profiles?.fullName.orEmpty(),
                department = row.profiles?.department,
                avatarUrl = row.profiles?.avatarUrl,
                score = row.score.toInt().coerceIn(0, 100),
                isSemantic = isSemanticScore(row.scoreBreakdown),
                tags = tagsByUser[row.matchedUserId].orEmpty(),
            )
        }
    }

    private fun requireUserId(): String =
        client.auth.currentUserOrNull()?.id ?: throw AppError.Unauthorized()
}

/** `semantic_similarity` sayısal ise skor embedding benzerliği içeriyordur; "NOT_IMPLEMENTED" (metin) ise içermez. */
internal fun isSemanticScore(breakdown: JsonObject?): Boolean =
    (breakdown?.get("semantic_similarity") as? JsonPrimitive)?.let { !it.isString && it.doubleOrNull != null } ?: false
