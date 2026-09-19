package com.kampusagi.android.data.auth

import com.kampusagi.android.data.common.mapErrors
import com.kampusagi.android.domain.auth.AuthRepository
import com.kampusagi.android.domain.auth.AuthUser
import com.kampusagi.android.domain.auth.SignUpResult
import com.kampusagi.android.domain.common.AppError
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform
import javax.inject.Inject

/**
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
        client.auth.sessionStatus.transform { status ->
            when (status) {
                is SessionStatus.Authenticated -> status.session.user?.let { emit(mapUser(it)) }
                is SessionStatus.NotAuthenticated -> emit(null)
                // Initializing: oturum diskten yükleniyor; RefreshFailure: geçici ağ hatası, oturum silinmedi.
                // İkisi de kesin bir durum değildir -> yayılmaz (bkz. AuthRepository.observeAuthState).
                is SessionStatus.Initializing, is SessionStatus.RefreshFailure -> Unit
            }
        }

    override suspend fun signIn(email: String, password: String): AuthUser = mapErrors {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        requireSignedInUser()
    }

    override suspend fun signUp(email: String, password: String): SignUpResult = mapErrors {
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
        // E-posta doğrulaması açıksa (Supabase varsayılanı) kayıt bir OTURUM oluşturmaz.
        val user = client.auth.currentUserOrNull()
        if (user != null && client.auth.currentSessionOrNull() != null) {
            SignUpResult.SignedIn(mapUser(user))
        } else {
            SignUpResult.ConfirmationRequired(email)
        }
    }

    override suspend fun resendSignUpConfirmation(email: String) = mapErrors {
        client.auth.resendEmail(OtpType.Email.SIGNUP, email)
    }

    override suspend fun signInWithGoogleIdToken(idToken: String): AuthUser = mapErrors {
        client.auth.signInWith(IDToken) {
            this.idToken = idToken
            this.provider = Google
        }
        requireSignedInUser()
    }

    override suspend fun sendPasswordReset(email: String) = mapErrors {
        client.auth.resetPasswordForEmail(email)
    }

    override suspend fun refreshSession() = mapErrors {
        client.auth.refreshCurrentSession()
    }

    override suspend fun signOut() = mapErrors {
        client.auth.signOut()
    }

    private fun requireSignedInUser(): AuthUser =
        currentUser ?: throw AppError.Unauthorized()

    private fun mapUser(user: UserInfo): AuthUser =
        AuthUser(uid = user.id, email = user.email, isEmailVerified = user.emailConfirmedAt != null)
}
