package com.kampusagi.android.domain.events

import java.time.Instant

/**
 * Yaklaşan bir kampüs etkinliği. `isUniversityEvent`: yalnızca kullanıcının üniversitesine açık (false: tüm üniversitelere açık).
 * `attendeeCount` yalnızca SAYIDIR — kimin katıldığı kimseye gösterilmez.
 */
data class CampusEvent(
    val id: String,
    val title: String,
    val description: String,
    val location: String,
    val startsAt: Instant,
    val endsAt: Instant?,
    val isUniversityEvent: Boolean,
    val attendeeCount: Int,
    val isJoined: Boolean,
) {
    /** Katılım durumunu iyimser olarak değiştirir; sayaç 0'ın altına inmez ve durum değişmediyse sayaç değişmez. */
    fun withAttending(attending: Boolean): CampusEvent = when {
        attending == isJoined -> this
        attending -> copy(isJoined = true, attendeeCount = attendeeCount + 1)
        else -> copy(isJoined = false, attendeeCount = (attendeeCount - 1).coerceAtLeast(0))
    }
}

/**
 * Tüm metotlar hata durumunda [com.kampusagi.android.domain.common.AppError] fırlatır.
 * Etkinlikler Supabase `campus_events` tablosundan gelir (docs/DECISIONS.md D34); moderatörler ekler.
 */
interface EventsRepository {
    /** Kullanıcının görebildiği yaklaşan/süren etkinlikler, başlangıç zamanına göre artan. */
    suspend fun getUpcoming(): List<CampusEvent>

    /** İdempotent: zaten katılıyorken "katıl" veya katılmıyorken "ayrıl" hata vermez. */
    suspend fun setAttending(eventId: String, attending: Boolean)
}
