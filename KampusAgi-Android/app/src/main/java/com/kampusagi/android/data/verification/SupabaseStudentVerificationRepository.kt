package com.kampusagi.android.data.verification

import com.kampusagi.android.data.common.mapErrors
import com.kampusagi.android.domain.common.AppError
import com.kampusagi.android.domain.verification.StudentVerificationRepository
import com.kampusagi.android.domain.verification.VerificationState
import com.kampusagi.android.domain.verification.VerificationStatus
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject

/**
 * Yol yapısı ({uid}/document.pdf) ve iş akışı backend ile sabittir (bkz. storage RLS
 * supabase/migrations/202609160020, Edge Function supabase/functions/submit-student-document).
 */
class SupabaseStudentVerificationRepository @Inject constructor(
    private val client: SupabaseClient,
) : StudentVerificationRepository {

    @Serializable
    private data class ProfileRow(
        @SerialName("verification_status") val verificationStatus: String,
    )

    @Serializable
    private data class VerificationRow(
        @SerialName("rejection_reason") val rejectionReason: String? = null,
    )

    @Serializable
    private data class SubmitDocumentRequest(
        val storagePath: String,
    )

    override suspend fun submitDocument(fileBytes: ByteArray) = mapErrors(preferServerMessage = true) {
        val userId = requireUserId()
        val path = "$userId/document.pdf"

        client.storage.from("student-documents").upload(path, fileBytes) {
            upsert = true
            contentType = ContentType.Application.Pdf
        }

        client.functions.invoke(function = "submit-student-document", body = SubmitDocumentRequest(storagePath = path))
        Unit
    }

    override suspend fun fetchStatus(): VerificationStatus = mapErrors {
        val userId = requireUserId()

        val profile = client.from("profiles")
            .select(Columns.list("verification_status")) { filter { eq("id", userId) } }
            .decodeSingle<ProfileRow>()

        // Red gerekçesi ve "belge gönderildi mi" bilgisi student_verifications'ta yaşar.
        val latest = client.from("student_verifications")
            .select(Columns.list("rejection_reason")) {
                filter { eq("user_id", userId) }
                order("created_at", Order.DESCENDING)
                limit(1)
            }
            .decodeList<VerificationRow>()
            .firstOrNull()

        val state = VerificationState.fromRawValue(profile.verificationStatus)
        VerificationStatus(
            state = state,
            rejectionReason = if (state == VerificationState.REJECTED) latest?.rejectionReason else null,
            hasSubmittedDocument = latest != null,
        )
    }

    /**
     * Canlı izleme: profil satırı (verification_status, moderatör onayı/reddi trigger ile buraya
     * yansır) değiştikçe durum yeniden okunur. Kanal ÖNCE abone edilir, sonra ilk durum okunur —
     * böylece iki adım arasında gelen değişiklik kaçmaz. Yeniden bağlanma sırasında kaçan olaylar
     * için arayüz ayrıca elle yenileme sunar.
     */
    override fun observeStatus(): Flow<VerificationStatus> = channelFlow {
        val userId = requireUserId()
        val channel = client.channel("verification-status-$userId")
        val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "profiles"
            filter("id", FilterOperator.EQ, userId)
        }
        try {
            channel.subscribe(blockUntilSubscribed = true)
            send(fetchStatus())
            changes.collect {
                try {
                    send(fetchStatus())
                } catch (_: AppError) {
                    // Geçici hata: son bilinen durum korunur; kullanıcı elle yenileyebilir.
                }
            }
        } finally {
            withContext(NonCancellable) {
                channel.unsubscribe()
                client.realtime.removeChannel(channel)
            }
        }
    }

    private fun requireUserId(): String =
        client.auth.currentUserOrNull()?.id ?: throw AppError.Unauthorized()
}
