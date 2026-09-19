package com.kampusagi.android.domain.verification

/**
 * Raw value'lar Postgres `student_verifications.status` / `profiles.verification_status`
 * check constraint'iyle BİREBİR eşleşmelidir: 'PENDING' | 'APPROVED' | 'REJECTED'
 * (bkz. supabase/migrations/202609160001, 202609160003).
 */
enum class VerificationState(val rawValue: String) {
    PENDING("PENDING"),
    APPROVED("APPROVED"),
    REJECTED("REJECTED"),
    ;

    companion object {
        /** Bilinmeyen değer kısıtlı tarafa (PENDING) düşer — asla yetkiyi genişletmez. */
        fun fromRawValue(value: String): VerificationState =
            entries.firstOrNull { it.rawValue == value } ?: PENDING
    }
}

/**
 * Doğrulama durumu ve red gerekçesi YALNIZCA backend/moderatör tarafından
 * belirlenir; istemci bu değerleri asla set edemez (bkz. AI_Guidelines.md §3,
 * supabase/migrations/202609160010 RLS politikaları).
 *
 * `hasSubmittedDocument=false` -> kullanıcı hesabı açmış ama henüz belge göndermemiş
 * (kayıt sihirbazı yarım kalmış); bu durumda "inceleniyor" ekranı gösterilmez.
 */
data class VerificationStatus(
    val state: VerificationState,
    val rejectionReason: String?,
    val hasSubmittedDocument: Boolean,
)
