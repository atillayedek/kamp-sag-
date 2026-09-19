package com.kampusagi.android.feature.auth.login

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

internal sealed interface GoogleSignInResult {
    data class Token(val idToken: String) : GoogleSignInResult

    /** Kullanıcı hesap seçiciyi kapattı — hata değildir, mesaj gösterilmez. */
    data object Cancelled : GoogleSignInResult

    data object Failed : GoogleSignInResult
}

private const val TAG = "GoogleSignIn"

/**
 * Credential Manager ile Google ID token'ı alır. `webClientId` Supabase Dashboard > Authentication >
 * Providers > Google altında kayıtlı WEB OAuth Client ID'dir (Android Client ID DEĞİL).
 * `context` Activity bağlamı olmalıdır (hesap seçici arayüzü için).
 */
internal suspend fun requestGoogleIdToken(context: Context, webClientId: String): GoogleSignInResult {
    val option = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(webClientId)
        .build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

    return try {
        val result = CredentialManager.create(context).getCredential(context, request)
        val credential = result.credential
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            GoogleSignInResult.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken)
        } else {
            Log.e(TAG, "Beklenmeyen kimlik bilgisi türü: ${credential.type}")
            GoogleSignInResult.Failed
        }
    } catch (e: GetCredentialCancellationException) {
        GoogleSignInResult.Cancelled
    } catch (e: GetCredentialException) {
        Log.e(TAG, "Google girişi başarısız", e)
        GoogleSignInResult.Failed
    } catch (e: GoogleIdTokenParsingException) {
        Log.e(TAG, "Google ID token ayrıştırılamadı", e)
        GoogleSignInResult.Failed
    }
}
