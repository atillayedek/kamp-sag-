package com.kampusagi.android.domain.profile

/**
 * `account_status` / `verification_status` / `karma_score` bilerek YOKTUR — bunlar istemciden
 * yazılamaz ve profil düzenleme ekranlarının işi değildir (bkz. StudentVerificationRepository).
 */
data class Profile(
    val id: String,
    val username: String,
    val fullName: String,
    val avatarUrl: String?,
    val universityId: String?,
    val department: String?,
    val universityName: String? = null,
    val universityShortName: String? = null,
) {
    /** Kayıt sihirbazının profil adımları (2-5) tamamlandı mı? */
    val isComplete: Boolean
        get() = fullName.isNotBlank() && universityId != null && !department.isNullOrBlank()
}

interface ProfileRepository {
    suspend fun getMyProfile(): Profile

    /** Kullanıcı adı zaten alınmışsa [com.kampusagi.android.domain.common.AppError.UsernameTaken] fırlatır. */
    suspend fun updateMyProfile(fullName: String, username: String, universityId: String, department: String)
}
