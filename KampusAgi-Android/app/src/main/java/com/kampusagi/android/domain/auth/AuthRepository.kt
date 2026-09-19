package com.kampusagi.android.domain.auth

import kotlinx.coroutines.flow.Flow

/** Kayıt sonucu: e-posta doğrulaması kapalıysa oturum hemen açılır; açıksa kullanıcı önce e-postasını doğrulamalıdır. */
sealed interface SignUpResult {
    data class SignedIn(val user: AuthUser) : SignUpResult
    data class ConfirmationRequired(val email: String) : SignUpResult
}

/**
 * Kimlik doğrulama soyutlaması. İmplementasyonu data katmanında yaşar
 * (SupabaseAuthRepository).
 *
 * ÖNEMLİ: Bu arayüzün hiçbir metodu isVerified/accountStatus/isAdmin gibi bir
 * değer DÖNDÜRMEZ veya SET ETMEZ. Öğrenci doğrulama durumu ayrı bir katman
 * olan StudentVerificationRepository'den okunur; yetki/rol bilgisi yalnızca
 * Supabase Auth JWT app_metadata'sından gelir (bkz. AI_Guidelines.md §3, §45.2).
 *
 * Tüm metotlar hata durumunda [com.kampusagi.android.domain.common.AppError] fırlatır.
 */
interface AuthRepository {
    val currentUser: AuthUser?

    /**
     * Oturum durumundaki DEĞİŞİKLİKLERİ canlı izler. Yalnızca kesinleşmiş durumlar yayılır:
     * `null` = oturum kapalı. SDK'nın "oturum diskten yükleniyor" ara durumu yayılmaz —
     * böylece açık oturumlu kullanıcı soğuk açılışta yanlışlıkla "oturumsuz" görünmez.
     */
    fun observeAuthState(): Flow<AuthUser?>

    suspend fun signIn(email: String, password: String): AuthUser
    suspend fun signUp(email: String, password: String): SignUpResult
    suspend fun resendSignUpConfirmation(email: String)
    suspend fun signInWithGoogleIdToken(idToken: String): AuthUser
    suspend fun sendPasswordReset(email: String)

    /** Oturum belirtecini (JWT) sunucudan zorla yeniler. */
    suspend fun refreshSession()
    suspend fun signOut()
}
