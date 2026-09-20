package com.kampusagi.android.core.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.kampusagi.android.R
import com.kampusagi.android.domain.common.AppError

/**
 * ViewModel'lerin `Context`/`R` bilmeden kullanıcıya göstereceği metni taşıması için.
 * Ekranda [asString], test/arka planda [resolve] ile çözülür.
 */
sealed interface UiText {
    data class Resource(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Plain(val value: String) : UiText

    @Composable
    fun asString(): String = when (this) {
        is Resource -> stringResource(id, *args.toTypedArray())
        is Plain -> value
    }

    fun resolve(context: Context): String = when (this) {
        is Resource -> context.getString(id, *args.toTypedArray())
        is Plain -> value
    }
}

fun uiText(@StringRes id: Int, vararg args: Any): UiText = UiText.Resource(id, args.toList())

/** Tipli hatayı Türkçe kullanıcı mesajına çevirir. */
fun AppError.toUiText(): UiText = when (this) {
    is AppError.Network -> uiText(R.string.error_network)
    is AppError.InvalidCredentials -> uiText(R.string.error_invalid_credentials)
    is AppError.EmailNotConfirmed -> uiText(R.string.error_email_not_confirmed)
    is AppError.EmailAlreadyRegistered -> uiText(R.string.error_email_already_registered)
    is AppError.WeakPassword -> uiText(R.string.error_weak_password)
    is AppError.InvalidEmail -> uiText(R.string.error_invalid_email)
    is AppError.RateLimited -> uiText(R.string.error_rate_limited)
    is AppError.Unauthorized -> uiText(R.string.error_unauthorized)
    is AppError.Forbidden -> uiText(R.string.error_forbidden)
    is AppError.NotFound -> uiText(R.string.error_not_found)
    is AppError.UsernameTaken -> uiText(R.string.error_username_taken)
    is AppError.FileUnreadable -> uiText(R.string.error_file_unreadable)
    is AppError.FileTooLarge -> uiText(R.string.error_file_too_large, maxMegabytes)
    is AppError.NotPdf -> uiText(R.string.error_file_not_pdf)
    is AppError.ProductUnavailable -> uiText(R.string.error_product_unavailable)
    is AppError.ProductAlreadyOwned -> uiText(R.string.error_product_already_owned)
    is AppError.Server -> UiText.Plain(userMessage)
    is AppError.Unknown -> uiText(R.string.error_unknown)
}

/** Bilinmeyen `Throwable` -> [AppError] değilse genel hata metni. */
fun Throwable.toUiText(): UiText = (this as? AppError)?.toUiText() ?: uiText(R.string.error_unknown)
