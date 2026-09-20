package com.kampusagi.android.domain.analytics

/**
 * Temel ürün olayları. YALNIZCA olay adı gönderilir (içerik, metin, konum, cihaz kimliği, serbest özellik YOK).
 * Adlar sunucudaki izin listesiyle (`track_event`, supabase/migrations/202609190016) birebir aynı olmalıdır.
 */
enum class AnalyticsEvent {
    /** Kayıt sihirbazında profil bilgileri kaydedildi. */
    SIGN_UP_COMPLETED,

    /** Öğrenci belgesi yüklendi. */
    DOCUMENT_UPLOADED,

    POST_CREATED,
    REQUIREMENT_PUBLISHED,

    /** Eşleşme kartından/profilden "Mesaj At" ile sohbet açıldı (var olan sohbet de sayılır). */
    CHAT_STARTED,

    MESSAGE_SENT,
    PURCHASE_COMPLETED,
}

/**
 * "Ateşle ve unut": çağıranı asla bekletmez ve asla hata fırlatmaz — analitik başarısızlığı kullanıcı akışını bozmamalı.
 * Kullanıcı istatistik paylaşımını kapattıysa hiçbir şey gönderilmez.
 */
interface AnalyticsTracker {
    fun track(event: AnalyticsEvent)
}
