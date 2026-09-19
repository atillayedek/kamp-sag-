package com.kampusagi.android.navigation

import kotlinx.serialization.Serializable

/** Navigation Compose 2.8+ tip-güvenli (type-safe) rotalar. */
sealed interface KampusAgiRoute {
    @Serializable data object Welcome : KampusAgiRoute
    @Serializable data object Login : KampusAgiRoute
    @Serializable data object Register : KampusAgiRoute
    @Serializable data object PendingReview : KampusAgiRoute
    @Serializable data class Rejected(val reason: String?) : KampusAgiRoute
    @Serializable data object Approved : KampusAgiRoute
}
