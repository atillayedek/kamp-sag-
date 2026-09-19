package com.kampusagi.android.domain.match

/** Kullanıcının en son yayınlanmış ihtiyacı; eşleşmeler bu ilana göre hesaplanır. */
data class MyRequirement(val id: String, val title: String)

/**
 * Bir eşleşme adayı. `score` 0-100 (yalnızca backend hesaplar; istemci okur).
 * `isSemantic`: skor gerçekten embedding benzerliği içeriyorsa true -> arayüz "Anlamsal Eşleşme" der;
 * aksi halde yalnızca "Eşleşme" (yanlış iddia yok, bkz. docs/BLOCKERS.md B3).
 */
data class MatchCandidate(
    val matchId: String,
    val userId: String,
    val fullName: String,
    val department: String?,
    val avatarUrl: String?,
    val score: Int,
    val isSemantic: Boolean,
    val tags: List<String>,
)

data class MatchesResult(val requirement: MyRequirement?, val matches: List<MatchCandidate>)

/** "Profili Gör" ekranı için başka bir öğrencinin herkese açık profili. */
data class PublicProfile(
    val id: String,
    val fullName: String,
    val department: String?,
    val universityName: String?,
    val avatarUrl: String?,
    val latestRequirementTitle: String?,
    val tags: List<String>,
)

/** Tüm metotlar hata durumunda [com.kampusagi.android.domain.common.AppError] fırlatır. */
interface MatchRepository {
    /** Kullanıcının son yayınlanmış ilanı için önerilen eşleşmeler (skora göre azalan). İlan yoksa `requirement = null`. */
    suspend fun getMatches(): MatchesResult

    /** `recompute-matches` Edge Function'ını çalıştırıp güncel eşleşmeleri döner. */
    suspend fun refreshMatches(requirementId: String): MatchesResult

    suspend fun getPublicProfile(userId: String): PublicProfile
}
