package com.kampusagi.android.domain.community

import java.time.Instant

/** Topluluk kapsamı: tüm üniversiteler ya da kullanıcının kendi üniversitesi. */
enum class CommunityScope { GENERAL, UNIVERSITY }

/** `posts.category` değerleri; ihtiyaç ayrıştırma (parse-need) sınıflandırmasıyla aynıdır. */
enum class PostCategory(val rawValue: String) {
    SPORTS("SPORTS"),
    ACADEMIC("ACADEMIC"),
    SOCIAL("SOCIAL"),
    HOUSING("HOUSING"),
    TRANSPORT("TRANSPORT"),
    OTHER("OTHER"),
    ;

    companion object {
        fun fromRawValue(value: String?): PostCategory = entries.firstOrNull { it.rawValue == value } ?: OTHER
    }
}

data class Post(
    val id: String,
    val authorId: String,
    val authorName: String,
    val authorDepartment: String?,
    val authorUniversityShortName: String?,
    val authorAvatarUrl: String?,
    val title: String,
    val body: String,
    val category: PostCategory,
    val createdAt: Instant,
    val likeCount: Int,
    val commentCount: Int,
    val likedByMe: Boolean,
) {
    /** İyimser güncelleme: beğeni durumunu değiştirir ve sayacı buna göre ayarlar (0'ın altına inmez). */
    fun withLiked(liked: Boolean): Post = when {
        liked == likedByMe -> this
        liked -> copy(likedByMe = true, likeCount = likeCount + 1)
        else -> copy(likedByMe = false, likeCount = (likeCount - 1).coerceAtLeast(0))
    }
}

data class Comment(
    val id: String,
    val postId: String,
    val authorId: String,
    val authorName: String,
    val authorAvatarUrl: String?,
    val body: String,
    val createdAt: Instant,
)

/** Tüm metotlar hata durumunda [com.kampusagi.android.domain.common.AppError] fırlatır. */
interface PostRepository {
    /** En yeniden eskiye; `before` verilirse yalnızca ondan ESKİ gönderiler (anahtar kümeli sayfalama). */
    suspend fun getFeed(scope: CommunityScope, limit: Int, before: Instant?): List<Post>

    suspend fun getPost(postId: String): Post

    suspend fun createPost(scope: CommunityScope, title: String, body: String, category: PostCategory): Post

    suspend fun setLiked(postId: String, liked: Boolean)

    suspend fun getComments(postId: String): List<Comment>

    suspend fun addComment(postId: String, body: String): Comment
}
