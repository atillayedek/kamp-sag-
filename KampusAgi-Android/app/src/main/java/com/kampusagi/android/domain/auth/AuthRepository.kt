package com.kampusagi.android.domain.auth

import kotlinx.coroutines.flow.Flow

/**
 * Kimlik doğrulama soyutlaması. İmplementasyonu data katmanında yaşar
 * (SupabaseAuthRepository) — Swift/iOS sürümündeki Clean Architecture +
 * Repository pattern ilkesi burada da aynen korunur (bkz. AI_Guidelines.md §2.3/§45.6).
 *
 * ÖNEMLİ: Bu arayüzün hiçbir metodu isVerified/accountStatus/isAdmin gibi bir
 * değer DÖNDÜRMEZ veya SET ETMEZ. Öğrenci doğrulama durumu ayrı bir katman
 * olan StudentVerificationRepository'den okunur; yetki/rol bilgisi yalnızca
 * Supabase Auth JWT app_metadata'sından gelir (bkz. AI_Guidelines.md §3, §45.2).
 *
 * Not: Google ile giriş, Credential Manager'ın Activity context gerektirmesi
 * nedeniyle UI katmanında (LoginScreen) tetiklenir; bu arayüz yalnızca elde
 * edilen ID token'ı Supabase Auth ile değiştirir (`signInWithGoogleIdToken`).
 */
interface AuthRepository {
    val currentUser: AuthUser?

    /** Oturum durumundaki değişiklikleri canlı izler. `null` = oturum kapalı. */
    fun observeAuthState(): Flow<AuthUser?>

    suspend fun signIn(email: String, password: String): AuthUser
    suspend fun signUp(email: String, password: String): AuthUser
    suspend fun signInWithGoogleIdToken(idToken: String): AuthUser
    suspend fun sendPasswordReset(email: String)
    suspend fun signOut()
}
