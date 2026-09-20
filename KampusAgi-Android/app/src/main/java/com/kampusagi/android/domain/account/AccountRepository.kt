package com.kampusagi.android.domain.account

/** Hesap yaşam döngüsü işlemleri (oturum açma/kapama [com.kampusagi.android.domain.auth.AuthRepository]'de). */
interface AccountRepository {
    /**
     * Hesabı ve tüm kullanıcı verisini kalıcı olarak siler (sunucuda `delete-account`), ardından yerel oturumu kapatır.
     * Başarısızlıkta [com.kampusagi.android.domain.common.AppError] fırlatır; oturum açık kalır.
     */
    suspend fun deleteAccount()
}
