package com.kampusagi.android.data.account

import com.kampusagi.android.data.common.mapErrors
import com.kampusagi.android.domain.account.AccountRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.buildJsonObject
import javax.inject.Inject

class SupabaseAccountRepository @Inject constructor(
    private val client: SupabaseClient,
) : AccountRepository {

    override suspend fun deleteAccount() = mapErrors(preferServerMessage = true) {
        client.functions.invoke(function = "delete-account", body = buildJsonObject { })
        endLocalSession()
    }

    /**
     * Sunucu kullanıcıyı sildi; `/logout` artık geçersiz JWT nedeniyle reddedilebilir. Bu yüzden önce normal
     * çıkış denenir, olmazsa yerel oturum doğrudan temizlenir — her iki durumda da uygulama giriş ekranına döner.
     */
    private suspend fun endLocalSession() {
        try {
            client.auth.signOut()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            client.auth.clearSession()
        }
    }
}
