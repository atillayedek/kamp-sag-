package com.kampusagi.android.domain.verification

import kotlinx.coroutines.flow.Flow

/**
 * Öğrenci belgesi (PDF) gönderimi ve doğrulama durumu takibi.
 *
 * `submitDocument` iki adımdan oluşur (implementasyona bakın):
 * 1) PDF, kullanıcının kendi Storage yoluna yüklenir (bkz. storage RLS,
 *    supabase/migrations/202609160020).
 * 2) Yükleme tamamlandığında `submit-student-document` Edge Function'ı
 *    çağrılarak Postgres'te inceleme kaydı oluşturulur ve accountStatus=PENDING set
 *    edilir. Bu adım istemciden DOĞRUDAN veritabanı yazımı ile YAPILAMAZ.
 *
 * PDF byte'ları çağıran tarafından (boyut/tür doğrulaması yapılmış) sağlanır; bu katman
 * Android'e özgü değildir. Tüm metotlar hata durumunda [com.kampusagi.android.domain.common.AppError] fırlatır.
 */
interface StudentVerificationRepository {
    suspend fun submitDocument(fileBytes: ByteArray)
    suspend fun fetchStatus(): VerificationStatus

    /**
     * Durumu canlı izler: hemen bir kez yayar, sonra sunucuda kullanıcının profili/başvurusu
     * değiştikçe yeniden yayar (moderatör onay/red verince ekran kendiliğinden güncellenir).
     * Akış iptal edilince kanal kapatılır.
     */
    fun observeStatus(): Flow<VerificationStatus>
}
