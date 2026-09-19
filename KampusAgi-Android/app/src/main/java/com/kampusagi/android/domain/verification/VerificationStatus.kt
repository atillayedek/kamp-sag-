package com.kampusagi.android.domain.verification

/**
 * Raw value'lar Postgres `student_verifications.status` / `profiles.verification_status`
 * check constraint'iyle BİREBİR eşleşmelidir: 'PENDING' | 'APPROVED' | 'REJECTED'
 * (bkz. supabase/migrations/202609160001, 202609160003).
 *
 * Not: iOS sürümünde bu enum başlangıçta yanlışlıkla "VERIFIED" raw value'su
 * taşıyordu (backend'deki gerçek "APPROVED" değeriyle uyuşmuyordu) — kod
 * incelemesinde bulunup düzeltilmişti. Android tarafında aynı hatanın
 * TEKRARLANMAMASI için raw value'lar burada baştan doğru yazılmıştır.
 */
enum class VerificationState(val rawValue: String) {
    PENDING("PENDING"),
    APPROVED("APPROVED"),
    REJECTED("REJECTED");

    companion object {
        fun fromRawValue(value: String): VerificationState =
            entries.firstOrNull { it.rawValue == value } ?: PENDING
    }
}

/**
 * Doğrulama durumu ve red gerekçesi YALNIZCA backend/moderatör tarafından
 * belirlenir; istemci bu değerleri asla set edemez (bkz. AI_Guidelines.md §3,
 * supabase/migrations/202609160010 RLS politikaları).
 */
data class VerificationStatus(
    val state: VerificationState,
    val rejectionReason: String?,
)
