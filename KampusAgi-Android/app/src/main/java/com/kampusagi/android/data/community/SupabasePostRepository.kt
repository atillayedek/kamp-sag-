package com.kampusagi.android.data.community

import com.kampusagi.android.data.common.mapErrors
import com.kampusagi.android.domain.common.AppError
import com.kampusagi.android.domain.community.Comment
import com.kampusagi.android.domain.community.CommunityScope
import com.kampusagi.android.domain.community.Post
import com.kampusagi.android.domain.community.PostCategory
import com.kampusagi.android.domain.community.PostRepository
import com.kampusagi.android.domain.profile.ProfileRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabasePostRepository @Inject constructor(
    private val client: SupabaseClient,
    private val profileRepository: ProfileRepository,
) : PostRepository {

    @Serializable
    private data class FeedRow(
        val id: String,
        @SerialName("author_id") val authorId: String,
        val title: String,
        val body: String,
        val category: String,
        @SerialName("created_at") val createdAt: String,
        @SerialName("author_full_name") val authorFullName: String,
        @SerialName("author_avatar_url") val authorAvatarUrl: String? = null,
        @SerialName("author_department") val authorDepartment: String? = null,
        @SerialName("author_university_short_name") val authorUniversityShortName: String? = null,
        @SerialName("like_count") val likeCount: Long = 0,
        @SerialName("comment_count") val commentCount: Long = 0,
    )

    @Serializable
    private data class IdRow(val id: String)

    @Serializable
    private data class LikedRow(@SerialName("post_id") val postId: String)

    @Serializable
    private data class PostInsert(
        @SerialName("author_id") val authorId: String,
        @SerialName("community_id") val communityId: String,
        val title: String,
        val body: String,
        val category: String,
    )

    @Serializable
    private data class LikeRow(
        @SerialName("post_id") val postId: String,
        @SerialName("user_id") val userId: String,
    )

    @Serializable
    private data class AuthorRef(
        @SerialName("full_name") val fullName: String,
        @SerialName("avatar_url") val avatarUrl: String? = null,
    )

    @Serializable
    private data class CommentRow(
        val id: String,
        @SerialName("post_id") val postId: String,
        @SerialName("author_id") val authorId: String,
        val body: String,
        @SerialName("created_at") val createdAt: String,
        val profiles: AuthorRef? = null,
    )

    @Serializable
    private data class CommentInsert(
        @SerialName("post_id") val postId: String,
        @SerialName("author_id") val authorId: String,
        val body: String,
    )

    // Anahtar kullanıcıyı da içerir: aynı süreçte farklı hesapla girişte başkasının üniversite topluluğu kullanılmasın.
    private val communityIds = mutableMapOf<Pair<String, CommunityScope>, String>()
    private val communityLock = Mutex()

    override suspend fun getFeed(scope: CommunityScope, limit: Int, before: Instant?): List<Post> = mapErrors {
        val communityId = communityId(scope)
        val rows = client.from("post_feed_view")
            .select {
                filter {
                    eq("community_id", communityId)
                    if (before != null) lt("created_at", before.toString())
                }
                order("created_at", Order.DESCENDING)
                limit(limit.toLong())
            }
            .decodeList<FeedRow>()
        withLikedFlags(rows)
    }

    override suspend fun getPost(postId: String): Post = mapErrors {
        val row = client.from("post_feed_view")
            .select { filter { eq("id", postId) } }
            .decodeList<FeedRow>()
            .firstOrNull() ?: throw AppError.NotFound()
        withLikedFlags(listOf(row)).single()
    }

    override suspend fun createPost(scope: CommunityScope, title: String, body: String, category: PostCategory): Post =
        mapErrors {
            val inserted = client.from("posts")
                .insert(
                    PostInsert(
                        authorId = requireUserId(),
                        communityId = communityId(scope),
                        title = title.trim(),
                        body = body.trim(),
                        category = category.rawValue,
                    ),
                ) { select(Columns.list("id")) }
                .decodeSingle<IdRow>()
            getPost(inserted.id)
        }

    override suspend fun setLiked(postId: String, liked: Boolean) = mapErrors {
        val userId = requireUserId()
        if (liked) {
            client.from("post_likes").insert(LikeRow(postId = postId, userId = userId))
        } else {
            client.from("post_likes").delete {
                filter {
                    eq("post_id", postId)
                    eq("user_id", userId)
                }
            }
        }
        Unit
    }

    override suspend fun getComments(postId: String): List<Comment> = mapErrors {
        client.from("comments")
            .select(Columns.raw(COMMENT_COLUMNS)) {
                filter { eq("post_id", postId) }
                order("created_at", Order.ASCENDING)
            }
            .decodeList<CommentRow>()
            .map { it.toDomain() }
    }

    override suspend fun addComment(postId: String, body: String): Comment = mapErrors {
        client.from("comments")
            .insert(CommentInsert(postId = postId, authorId = requireUserId(), body = body.trim())) {
                select(Columns.raw(COMMENT_COLUMNS))
            }
            .decodeSingle<CommentRow>()
            .toDomain()
    }

    private suspend fun withLikedFlags(rows: List<FeedRow>): List<Post> {
        if (rows.isEmpty()) return emptyList()
        // post_likes RLS yalnızca kullanıcının KENDİ beğenilerini döndürür.
        val liked = client.from("post_likes")
            .select(Columns.list("post_id")) { filter { isIn("post_id", rows.map { it.id }) } }
            .decodeList<LikedRow>()
            .mapTo(HashSet()) { it.postId }
        return rows.map { it.toDomain(likedByMe = it.id in liked) }
    }

    /** Topluluk kimlikleri sabittir; oturum boyunca bir kez çözülüp önbelleğe alınır. */
    private suspend fun communityId(scope: CommunityScope): String = communityLock.withLock {
        val key = requireUserId() to scope
        communityIds[key] ?: resolveCommunityId(scope).also { communityIds[key] = it }
    }

    private suspend fun resolveCommunityId(scope: CommunityScope): String {
        val rows = when (scope) {
            CommunityScope.GENERAL -> client.from("communities")
                .select(Columns.list("id")) { filter { eq("type", "GENERAL") } }
                .decodeList<IdRow>()
            CommunityScope.UNIVERSITY -> {
                val universityId = profileRepository.getMyProfile().universityId ?: throw AppError.NotFound()
                client.from("communities")
                    .select(Columns.list("id")) {
                        filter {
                            eq("type", "UNIVERSITY")
                            eq("university_id", universityId)
                        }
                    }
                    .decodeList<IdRow>()
            }
        }
        return rows.firstOrNull()?.id ?: throw AppError.NotFound()
    }

    private fun requireUserId(): String =
        client.auth.currentUserOrNull()?.id ?: throw AppError.Unauthorized()

    private fun FeedRow.toDomain(likedByMe: Boolean) = Post(
        id = id,
        authorId = authorId,
        authorName = authorFullName,
        authorDepartment = authorDepartment,
        authorUniversityShortName = authorUniversityShortName,
        authorAvatarUrl = authorAvatarUrl,
        title = title,
        body = body,
        category = PostCategory.fromRawValue(category),
        createdAt = parseInstant(createdAt),
        likeCount = likeCount.toInt(),
        commentCount = commentCount.toInt(),
        likedByMe = likedByMe,
    )

    private fun CommentRow.toDomain() = Comment(
        id = id,
        postId = postId,
        authorId = authorId,
        authorName = profiles?.fullName.orEmpty(),
        authorAvatarUrl = profiles?.avatarUrl,
        body = body,
        createdAt = parseInstant(createdAt),
    )

    private companion object {
        const val COMMENT_COLUMNS = "id, post_id, author_id, body, created_at, profiles(full_name, avatar_url)"
    }
}

/** PostgREST `timestamptz` çıktısı ("...+00:00") — `OffsetDateTime` ofsetli biçimi güvenle çözer. */
internal fun parseInstant(value: String): Instant = OffsetDateTime.parse(value).toInstant()
