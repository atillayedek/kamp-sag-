package com.kampusagi.android.domain.common

/**
 * Uygulama genelinde tipli hata. Veri katmanı, ağ/SDK/sunucu hatalarını bu tiplere
 * çevirir; ViewModel'ler kullanıcıya Türkçe mesaj göstermek için `toUiText()` kullanır
 * (bkz. core/ui/UiText.kt). Bilinmeyen hatalar `Unknown` içinde `cause` ile saklanır ve loglanır.
 */
sealed class AppError(message: String? = null, cause: Throwable? = null) : Exception(message, cause) {
    class Network(cause: Throwable? = null) : AppError(cause = cause)
    class InvalidCredentials(cause: Throwable? = null) : AppError(cause = cause)
    class EmailNotConfirmed(cause: Throwable? = null) : AppError(cause = cause)
    class EmailAlreadyRegistered(cause: Throwable? = null) : AppError(cause = cause)
    class WeakPassword(cause: Throwable? = null) : AppError(cause = cause)
    class InvalidEmail(cause: Throwable? = null) : AppError(cause = cause)
    class RateLimited(cause: Throwable? = null) : AppError(cause = cause)
    class Unauthorized(cause: Throwable? = null) : AppError(cause = cause)
    class Forbidden(cause: Throwable? = null) : AppError(cause = cause)
    class NotFound(cause: Throwable? = null) : AppError(cause = cause)
    class UsernameTaken(cause: Throwable? = null) : AppError(cause = cause)
    class FileUnreadable(cause: Throwable? = null) : AppError(cause = cause)
    class FileTooLarge(val maxMegabytes: Int) : AppError()
    class NotPdf : AppError()

    /** Abonelik ürünü Play'de bulunamıyor / satın alınamıyor. */
    class ProductUnavailable : AppError()

    /** Abonelik Play hesabında zaten var (doğrulama sonraki açılışta tamamlanır). */
    class ProductAlreadyOwned : AppError()

    /** Sunucunun döndürdüğü, kullanıcıya doğrudan gösterilebilen Türkçe mesaj (Edge Function `error` alanı). */
    class Server(val userMessage: String, cause: Throwable? = null) : AppError(userMessage, cause)

    class Unknown(cause: Throwable? = null) : AppError(cause = cause)
}
