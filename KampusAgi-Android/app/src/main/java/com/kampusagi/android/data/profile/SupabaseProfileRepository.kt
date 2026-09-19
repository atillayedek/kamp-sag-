package com.kampusagi.android.data.profile

import com.kampusagi.android.data.common.PG_UNIQUE_VIOLATION
import com.kampusagi.android.data.common.mapErrors
import com.kampusagi.android.domain.common.AppError
import com.kampusagi.android.domain.profile.Profile
import com.kampusagi.android.domain.profile.ProfileRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject

class SupabaseProfileRepository @Inject constructor(
    private val client: SupabaseClient,
) : ProfileRepository {

    @Serializable
    private data class ProfileRow(
        val id: String,
        val username: String,
        @SerialName("full_name") val fullName: String,
        @SerialName("avatar_url") val avatarUrl: String? = null,
        @SerialName("university_id") val universityId: String? = null,
        val department: String? = null,
        val universities: UniversityRef? = null,
    )

    @Serializable
    private data class UniversityRef(
        val name: String,
        @SerialName("short_name") val shortName: String,
    )

    override suspend fun getMyProfile(): Profile = mapErrors {
        val row = client.from("profiles")
            .select(Columns.raw("id, username, full_name, avatar_url, university_id, department, universities(name, short_name)")) {
                filter { eq("id", requireUserId()) }
            }
            .decodeSingle<ProfileRow>()
        Profile(
            id = row.id,
            username = row.username,
            fullName = row.fullName,
            avatarUrl = row.avatarUrl,
            universityId = row.universityId,
            department = row.department,
            universityName = row.universities?.name,
            universityShortName = row.universities?.shortName,
        )
    }

    override suspend fun updateMyProfile(
        fullName: String,
        username: String,
        universityId: String,
        department: String,
    ) = mapErrors {
        try {
            client.from("profiles").update({
                set("full_name", fullName.trim())
                set("username", username.trim())
                set("university_id", universityId)
                set("department", department.trim())
            }) {
                filter { eq("id", requireUserId()) }
            }
            Unit
        } catch (e: PostgrestRestException) {
            if (e.code == PG_UNIQUE_VIOLATION) throw AppError.UsernameTaken(e) else throw e
        }
    }

    private fun requireUserId(): String =
        client.auth.currentUserOrNull()?.id ?: throw AppError.Unauthorized()
}
