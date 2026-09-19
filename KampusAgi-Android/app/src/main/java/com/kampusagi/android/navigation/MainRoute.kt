package com.kampusagi.android.navigation

import kotlinx.serialization.Serializable

/** Onaylı kullanıcının ana uygulamasındaki rotalar (sekmeler + ayrıntı ekranları). */
sealed interface MainRoute {
    @Serializable data object Communities : MainRoute
    @Serializable data object Matches : MainRoute
    @Serializable data object CreateRequirement : MainRoute
    @Serializable data object Conversations : MainRoute
    @Serializable data object Profile : MainRoute

    /** `scope`: [com.kampusagi.android.domain.community.CommunityScope] adı. */
    @Serializable data class CreatePost(val scope: String) : MainRoute

    @Serializable data class PostDetail(val postId: String) : MainRoute
}
