package com.kampusagi.android.data.settings

import com.kampusagi.android.data.common.mapErrors
import com.kampusagi.android.domain.common.AppError
import com.kampusagi.android.domain.settings.NotificationPreferencesRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject

class SupabaseNotificationPreferencesRepository @Inject constructor(
    private val client: SupabaseClient,
) : NotificationPreferencesRepository {

    @Serializable
    private data class PreferenceRow(@SerialName("new_message") val newMessage: Boolean)

    @Serializable
    private data class PreferenceUpsert(
        @SerialName("user_id") val userId: String,
        @SerialName("new_message") val newMessage: Boolean,
    )

    override suspend fun isNewMessageEnabled(): Boolean = mapErrors {
        client.from("notification_preferences")
            .select(Columns.list("new_message")) { filter { eq("user_id", requireUserId()) } }
            .decodeList<PreferenceRow>()
            .firstOrNull()
            ?.newMessage
            ?: true
    }

    override suspend fun setNewMessageEnabled(enabled: Boolean) = mapErrors {
        client.from("notification_preferences").upsert(PreferenceUpsert(requireUserId(), enabled)) { onConflict = "user_id" }
        Unit
    }

    private fun requireUserId(): String =
        client.auth.currentUserOrNull()?.id ?: throw AppError.Unauthorized()
}
