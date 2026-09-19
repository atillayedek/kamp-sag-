package com.kampusagi.android.navigation

import kotlinx.serialization.Serializable

/** Navigation Compose tip-güvenli (type-safe) rotalar. */
sealed interface KampusAgiRoute {
    @Serializable data object Welcome : KampusAgiRoute
    @Serializable data object Login : KampusAgiRoute

    /** `startStep`: 1 = yeni kayıt; 2 = hesap açık, profil eksik; 6 = profil tamam, belge gönderilmemiş. */
    @Serializable data class Register(val startStep: Int = 1) : KampusAgiRoute

    @Serializable data object PendingReview : KampusAgiRoute
    @Serializable data class Rejected(val reason: String?) : KampusAgiRoute
    @Serializable data object Approved : KampusAgiRoute
}
