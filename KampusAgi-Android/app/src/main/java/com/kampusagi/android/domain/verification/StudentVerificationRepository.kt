package com.kampusagi.android.domain.verification

/**
 * Öğrenci belgesi (PDF) gönderimi ve doğrulama durumu takibi.
 *
 * `submitDocument` iki adımdan oluşur (implementasyona bakın):
 * 1) PDF, kullanıcının kendi Storage yoluna yüklenir (bkz. storage RLS,
 *    supabase/migrations/202609160020).
 * 2) Yükleme tamamlandığında `submit-student-document` Edge Function'ı
 *    çağrılarak Postgres'te inceleme kaydı oluşturulur ve
 *    accountStatus=PENDING set edilir. Bu adım istemciden DOĞRUDAN veritabanı
 *    yazımı ile YAPILAMAZ (bkz. AI_Guidelines.md §3.2 karşılığı, RLS politikaları).
 *
 * PDF byte'ları çağıran (ViewModel) tarafından sağlanır — Android'in
 * content:// URI / Storage Access Framework modeli nedeniyle dosya okuma
 * (ContentResolver) Android context'i gerektirir; bu Repository katmanı
 * bilerek Android'e özgü değildir (yalnızca ByteArray alır).
 */
interface StudentVerificationRepository {
    suspend fun submitDocument(fileBytes: ByteArray)
    suspend fun fetchStatus(): VerificationStatus
}
