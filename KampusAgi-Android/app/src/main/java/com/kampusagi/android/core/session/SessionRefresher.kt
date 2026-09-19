package com.kampusagi.android.core.session

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Oturum/durum çözümlemesini yeniden başlatma sinyali. Kök yönlendirme (RootViewModel) kullanıcının
 * profil ve doğrulama durumunu kendiliğinden izlemez; kayıt sihirbazı belgeyi gönderince, moderatör
 * kararı elle yenilenince veya hata sonrası "Tekrar Dene" denince bu sinyal ile yeniden çözümlenir.
 */
@Singleton
class SessionRefresher @Inject constructor() {
    private val _requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val requests: Flow<Unit> = _requests.asSharedFlow()

    fun requestRefresh() {
        _requests.tryEmit(Unit)
    }
}
