package com.kampusagi.android.core.ui

import com.kampusagi.android.domain.common.AppError

/** Yüklenen verinin durumu: ekranlar skeleton / içerik / hata+tekrar dene arasında bununla seçer. */
sealed interface Loadable<out T> {
    data object Loading : Loadable<Nothing>
    data class Success<T>(val value: T) : Loadable<T>
    data class Failure(val error: AppError) : Loadable<Nothing>
}
