package com.kampusagi.android.data.verification

import com.kampusagi.android.domain.verification.StudentVerificationRepository
import com.kampusagi.android.domain.verification.VerificationState
import com.kampusagi.android.domain.verification.VerificationStatus
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.storage.upload
import io.ktor.http.ContentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject

/**
 * NOT — doğrulanmamış API yüzeyi: supabase-kt Postgrest/Storage/Functions
 * modüllerinin API'sini iyi bilinen kalıplara göre varsayar; bu ortamda
 * derlenip test edilememiştir (bkz. memory-bank/Memory_Bank.md).
 *
 * Yol yapısı ({uid}/document.pdf) ve iş akışı iOS sürümüyle BİREBİR aynıdır
 * (bkz. storage RLS supabase/migrations/202609160020, Edge Function
 * supabase/functions/submit-student-document).
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
        @SerialName("rejection_reason") val rejectionReason: String?,
    )

    @Serializable
    private data class SubmitDocumentRequest(
        @SerialName("storagePath") val storagePath: String,
    )

    override suspend fun submitDocument(fileBytes: ByteArray) {
        val userId = requireSignedInUserId()
        val path = "$userId/document.pdf"

        client.storage.from("student-documents").upload(path, fileBytes) {
            upsert = true
            contentType = ContentType.Application.Pdf
        }

        client.functions.invoke("submit-student-document") {
            setBody(SubmitDocumentRequest(storagePath = path))
        }
    }

    override suspend fun fetchStatus(): VerificationStatus {
        val userId = requireSignedInUserId()

        val profile = client.postgrest.from("profiles")
            .select {
                filter { eq("id", userId) }
            }
            .decodeSingle<ProfileRow>()

        val state = VerificationState.fromRawValue(profile.verificationStatus)
        if (state != VerificationState.REJECTED) {
            return VerificationStatus(state = state, rejectionReason = null)
        }

        // Red gerekçesi student_verifications'ta yaşar (profiles'ta DEĞİL) —
        // en son başvurunun gerekçesi okunur.
        val rows = client.postgrest.from("student_verifications")
            .select {
                filter { eq("user_id", userId) }
                order("created_at", Order.DESCENDING)
                limit(1)
            }
            .decodeList<VerificationRow>()

        return VerificationStatus(state = state, rejectionReason = rows.firstOrNull()?.rejectionReason)
    }

    private fun requireSignedInUserId(): String =
        requireNotNull(client.auth.currentUserOrNull()?.id) { "Bu işlem için giriş yapmanız gerekiyor." }
}
