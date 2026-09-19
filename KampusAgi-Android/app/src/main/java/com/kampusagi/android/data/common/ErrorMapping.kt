package com.kampusagi.android.data.common

import android.util.Log
import com.kampusagi.android.domain.common.AppError
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.exception.AuthWeakPasswordException
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException

private const val TAG = "ErrorMapping"

/** Postgres `unique_violation` (ör. kullanıcı adı çakışması). */
internal const val PG_UNIQUE_VIOLATION = "23505"

/** Postgres `insufficient_privilege` (RLS reddi). */
private const val PG_INSUFFICIENT_PRIVILEGE = "42501"

/**
 * SDK/ağ/sunucu hatalarını tipli [AppError]'a çevirir.
 * `preferServerMessage=true` yalnızca Edge Function çağrılarında kullanılır: fonksiyonlar
 * kullanıcıya gösterilebilir Türkçe mesajı `{"error": "..."}` içinde döndürür.
 */
internal fun Throwable.toAppError(preferServerMessage: Boolean = false): AppError = when (this) {
    is AppError -> this
    is AuthWeakPasswordException -> AppError.WeakPassword(this)
    is AuthRestException -> mapAuthError(this)
    is PostgrestRestException -> when (code) {
        PG_INSUFFICIENT_PRIVILEGE -> AppError.Forbidden(this)
        else -> mapRestStatus(this, preferServerMessage)
    }
    is RestException -> mapRestStatus(this, preferServerMessage)
    is HttpRequestException, is IOException -> AppError.Network(this)
    else -> AppError.Unknown(this).also { Log.e(TAG, "Beklenmeyen hata", this) }
}

private fun mapAuthError(e: AuthRestException): AppError = when (e.errorCode) {
    AuthErrorCode.InvalidCredentials -> AppError.InvalidCredentials(e)
    AuthErrorCode.EmailNotConfirmed -> AppError.EmailNotConfirmed(e)
    AuthErrorCode.EmailExists, AuthErrorCode.UserAlreadyExists -> AppError.EmailAlreadyRegistered(e)
    AuthErrorCode.WeakPassword -> AppError.WeakPassword(e)
    AuthErrorCode.EmailAddressInvalid -> AppError.InvalidEmail(e)
    AuthErrorCode.OverRequestRateLimit, AuthErrorCode.OverEmailSendRateLimit -> AppError.RateLimited(e)
    AuthErrorCode.SessionNotFound, AuthErrorCode.SessionExpired, AuthErrorCode.BadJwt,
    AuthErrorCode.RefreshTokenNotFound, AuthErrorCode.NoAuthorization -> AppError.Unauthorized(e)
    else -> mapRestStatus(e, preferServerMessage = false)
}

private fun mapRestStatus(e: RestException, preferServerMessage: Boolean): AppError {
    if (preferServerMessage) {
        serverMessageOf(e)?.let { return AppError.Server(it, e) }
    }
    return when (e.statusCode) {
        401 -> AppError.Unauthorized(e)
        403 -> AppError.Forbidden(e)
        404 -> AppError.NotFound(e)
        429 -> AppError.RateLimited(e)
        else -> AppError.Unknown(e).also { Log.e(TAG, "Sunucu hatası ${e.statusCode}", e) }
    }
}

private val lenientJson = Json { ignoreUnknownKeys = true }

/** Edge Function yanıt gövdesindeki `{"error": "..."}` alanını (varsa) okur. */
internal fun serverMessageOf(e: RestException): String? = parseServerError(e.description) ?: parseServerError(e.error)

internal fun parseServerError(body: String?): String? {
    if (body.isNullOrBlank()) return null
    return try {
        lenientJson.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
    } catch (_: IllegalArgumentException) {
        null
    }
}

/** İş mantığını çalıştırıp her hatayı [AppError]'a çevirir; iptal (CancellationException) ASLA yutulmaz. */
internal suspend inline fun <T> mapErrors(preferServerMessage: Boolean = false, block: () -> T): T =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        throw e.toAppError(preferServerMessage)
    }
