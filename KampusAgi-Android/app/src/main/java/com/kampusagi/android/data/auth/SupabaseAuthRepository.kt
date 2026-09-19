package com.kampusagi.android.data.auth

import com.kampusagi.android.domain.auth.AuthRepository
import com.kampusagi.android.domain.auth.AuthUser
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * NOT — doğrulanmamış API yüzeyi: supabase-kt (io.github.jan-tennert.supabase,
 * Kotlin Multiplatform) Auth modülünün API'sini iyi bilinen, stabil kalıplara
 * göre varsayar. Bu ortamda (Android SDK/Gradle yok) derlenip test
 * edilememiştir — Android Studio'da ilk derlemede küçük imza farkları
 * çıkabilir (bkz. memory-bank/Memory_Bank.md).
 *
 * Bu sınıf hiçbir yerde isVerified/accountStatus/is_admin set etmez —
 * doğrulama/yetki katmanı tamamen ayrıdır (bkz. StudentVerificationRepository,
 * AI_Guidelines.md §3, §45.2).
 */
class SupabaseAuthRepository @Inject constructor(
    private val client: SupabaseClient,
) : AuthRepository {

    override val currentUser: AuthUser?
        get() = client.auth.currentUserOrNull()?.let(::mapUser)

    override fun observeAuthState(): Flow<AuthUser?> =
        client.auth.sessionStatus.map { status ->
            (status as? SessionStatus.Authenticated)?.session?.user?.let(::mapUser)
        }

    override suspend fun signIn(email: String, password: String): AuthUser {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        return requireNotNull(currentUser) { "Giriş başarılı ama kullanıcı bilgisi okunamadı." }
    }

    override suspend fun signUp(email: String, password: String): AuthUser {
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
        return requireNotNull(currentUser) { "Kayıt başarılı ama kullanıcı bilgisi okunamadı." }
    }

    override suspend fun signInWithGoogleIdToken(idToken: String): AuthUser {
        client.auth.signInWith(IDToken) {
            this.idToken = idToken
            this.provider = Google
        }
        return requireNotNull(currentUser) { "Google girişi başarılı ama kullanıcı bilgisi okunamadı." }
    }

    override suspend fun sendPasswordReset(email: String) {
        client.auth.resetPasswordForEmail(email)
    }

    override suspend fun signOut() {
        client.auth.signOut()
    }

    private fun mapUser(user: UserInfo): AuthUser =
        AuthUser(uid = user.id, email = user.email, isEmailVerified = user.emailConfirmedAt != null)
}
