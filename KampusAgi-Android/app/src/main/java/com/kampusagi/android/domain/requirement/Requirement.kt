package com.kampusagi.android.domain.requirement

import com.kampusagi.android.domain.community.PostCategory
import java.time.Instant

/** `parse-need` / `requirements.help_type` değerleri. */
enum class HelpType(val rawValue: String) {
    LOOKING_FOR_PEOPLE("LOOKING_FOR_PEOPLE"),
    OFFERING_HELP("OFFERING_HELP"),
    LOOKING_FOR_ITEM("LOOKING_FOR_ITEM"),
    OFFERING_ITEM("OFFERING_ITEM"),
    LOOKING_FOR_INFO("LOOKING_FOR_INFO"),
    ;

    /** İhtiyaç mı (arıyorum) yoksa yardım/eşya teklifi mi (sunuyorum)? */
    val isOffer: Boolean get() = this == OFFERING_HELP || this == OFFERING_ITEM

    companion object {
        fun fromRawValue(value: String): HelpType = entries.firstOrNull { it.rawValue == value } ?: LOOKING_FOR_PEOPLE
    }
}

enum class NeedUrgency(val rawValue: String) {
    LOW("LOW"),
    NORMAL("NORMAL"),
    HIGH("HIGH"),
    URGENT("URGENT"),
    ;

    companion object {
        fun fromRawValue(value: String): NeedUrgency = entries.firstOrNull { it.rawValue == value } ?: NORMAL
    }
}

/**
 * Sunucunun (AI + sıkı şema doğrulaması) çıkardığı ilan taslağı. Güvenlik-kritik alan (üniversite,
 * yazar, durum, skor…) BİLEREK yoktur; onları yalnızca `publish-need` sunucuda belirler.
 */
data class ParsedNeed(
    val title: String,
    val category: PostCategory,
    val helpType: HelpType,
    val tags: List<String>,
    val participantCount: Int?,
    val urgency: NeedUrgency,
    val startsAt: Instant?,
    val skills: List<String>,
)

/** Analiz kaynağı: yapay zekâ ya da AI kullanılamadığında sunucunun anahtar kelime tabanlı yedeği. */
enum class AnalysisSource { AI, BASIC }

data class NeedAnalysis(val need: ParsedNeed, val source: AnalysisSource)

/** Tüm metotlar hata durumunda [com.kampusagi.android.domain.common.AppError] fırlatır. */
interface RequirementRepository {
    /** `parse-need` Edge Function'ı: metni yapılandırılmış taslağa çevirir (kayıt YAZMAZ). */
    suspend fun analyze(rawText: String): NeedAnalysis

    /** `publish-need` Edge Function'ı: taslağı sunucuda yeniden doğrulayıp yayınlar; ilan kimliğini döner. */
    suspend fun publish(rawText: String, need: ParsedNeed): String
}

/** Metin sınırı sunucuyla aynıdır (parse-need / publish-need `RAW_TEXT_MAX_LENGTH`). */
const val REQUIREMENT_TEXT_MAX_LENGTH = 500
