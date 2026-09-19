package com.kampusagi.android.testutil

import com.kampusagi.android.domain.auth.AuthRepository
import com.kampusagi.android.domain.auth.AuthUser
import com.kampusagi.android.domain.auth.SignUpResult
import com.kampusagi.android.domain.profile.Profile
import com.kampusagi.android.domain.profile.ProfileRepository
import com.kampusagi.android.domain.university.University
import com.kampusagi.android.domain.university.UniversityRepository
import com.kampusagi.android.domain.verification.StudentVerificationRepository
import com.kampusagi.android.domain.verification.VerificationStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

val testUser = AuthUser(uid = "user-1", email = "ogrenci@kampus.test", isEmailVerified = true)

class FakeAuthRepository(initialUser: AuthUser? = null) : AuthRepository {
    val state = MutableStateFlow(initialUser)
    override val currentUser: AuthUser? get() = state.value
    override fun observeAuthState(): Flow<AuthUser?> = state

    var signInBlock: suspend (String, String) -> AuthUser = { _, _ -> testUser.also { state.value = it } }
    var signUpBlock: suspend (String, String) -> SignUpResult = { _, _ -> SignUpResult.SignedIn(testUser.also { state.value = it }) }
    var resendCalls = mutableListOf<String>()
    var refreshCalls = 0
    var signOutCalls = 0
    var refreshBlock: suspend () -> Unit = {}

    override suspend fun signIn(email: String, password: String): AuthUser = signInBlock(email, password)
    override suspend fun signUp(email: String, password: String): SignUpResult = signUpBlock(email, password)
    override suspend fun resendSignUpConfirmation(email: String) { resendCalls += email }
    override suspend fun signInWithGoogleIdToken(idToken: String): AuthUser = testUser.also { state.value = it }
    override suspend fun sendPasswordReset(email: String) = Unit
    override suspend fun refreshSession() { refreshCalls++; refreshBlock() }
    override suspend fun signOut() { signOutCalls++; state.value = null }
}

class FakeProfileRepository(var profile: Profile = incompleteProfile) : ProfileRepository {
    var getBlock: suspend () -> Profile = { profile }
    var updateBlock: suspend (String, String, String, String) -> Unit = { _, _, _, _ -> }
    val updates = mutableListOf<List<String>>()

    override suspend fun getMyProfile(): Profile = getBlock()
    override suspend fun updateMyProfile(fullName: String, username: String, universityId: String, department: String) {
        updateBlock(fullName, username, universityId, department)
        updates += listOf(fullName, username, universityId, department)
    }

    companion object {
        val incompleteProfile = Profile("user-1", "user_abc12345", "", null, null, null)
        val completeProfile = Profile("user-1", "ogrenci", "Test Öğrenci", null, "uni-1", "Bilgisayar Mühendisliği")
    }
}

class FakeUniversityRepository(var universities: List<University> = defaultUniversities) : UniversityRepository {
    var block: suspend () -> List<University> = { universities }
    var calls = 0
    override suspend fun getActiveUniversities(): List<University> { calls++; return block() }

    companion object {
        val defaultUniversities = listOf(
            University("uni-1", "Test Üniversitesi", "TÜ", "Ankara"),
            University("uni-2", "Deneme Teknik Üniversitesi", "DTÜ", "İstanbul"),
        )
    }
}

class FakeVerificationRepository : StudentVerificationRepository {
    val statuses = MutableSharedFlow<VerificationStatus>(replay = 1)
    var fetchBlock: suspend () -> VerificationStatus = { error("fetchBlock ayarlanmadı") }
    var observeBlock: (() -> Flow<VerificationStatus>)? = null
    val submitted = mutableListOf<ByteArray>()
    var submitBlock: suspend (ByteArray) -> Unit = {}
    var observeCalls = 0

    override suspend fun submitDocument(fileBytes: ByteArray) {
        submitBlock(fileBytes)
        submitted += fileBytes
    }

    override suspend fun fetchStatus(): VerificationStatus = fetchBlock()

    override fun observeStatus(): Flow<VerificationStatus> {
        observeCalls++
        return observeBlock?.invoke() ?: flow { statuses.collect { emit(it) } }
    }
}

fun testPost(
    id: String,
    createdAt: java.time.Instant = java.time.Instant.parse("2026-09-19T12:00:00Z"),
    likeCount: Int = 0,
    liked: Boolean = false,
    commentCount: Int = 0,
) = com.kampusagi.android.domain.community.Post(
    id = id, authorId = "author-$id", authorName = "Yazar $id", authorDepartment = "Bölüm",
    authorUniversityShortName = "TÜ", authorAvatarUrl = null, title = "Başlık $id", body = "Gövde $id",
    category = com.kampusagi.android.domain.community.PostCategory.OTHER, createdAt = createdAt,
    likeCount = likeCount, commentCount = commentCount, likedByMe = liked,
)

class FakePostRepository : com.kampusagi.android.domain.community.PostRepository {
    var feedBlock: suspend (com.kampusagi.android.domain.community.CommunityScope, Int, java.time.Instant?) -> List<com.kampusagi.android.domain.community.Post> =
        { _, _, _ -> emptyList() }
    var getPostBlock: suspend (String) -> com.kampusagi.android.domain.community.Post = { testPost(it) }
    var likeBlock: suspend (String, Boolean) -> Unit = { _, _ -> }
    var commentsBlock: suspend (String) -> List<com.kampusagi.android.domain.community.Comment> = { emptyList() }
    var addCommentBlock: suspend (String, String) -> com.kampusagi.android.domain.community.Comment =
        { postId, body -> com.kampusagi.android.domain.community.Comment("c-new", postId, "user-1", "Ben", null, body, java.time.Instant.parse("2026-09-19T12:30:00Z")) }
    var createBlock: suspend (com.kampusagi.android.domain.community.CommunityScope, String, String, com.kampusagi.android.domain.community.PostCategory) -> com.kampusagi.android.domain.community.Post =
        { _, _, _, _ -> testPost("created") }

    val feedCalls = mutableListOf<Triple<com.kampusagi.android.domain.community.CommunityScope, Int, java.time.Instant?>>()
    val likeCalls = mutableListOf<Pair<String, Boolean>>()
    val created = mutableListOf<List<Any>>()

    override suspend fun getFeed(scope: com.kampusagi.android.domain.community.CommunityScope, limit: Int, before: java.time.Instant?): List<com.kampusagi.android.domain.community.Post> {
        feedCalls += Triple(scope, limit, before)
        return feedBlock(scope, limit, before)
    }
    override suspend fun getPost(postId: String) = getPostBlock(postId)
    override suspend fun createPost(scope: com.kampusagi.android.domain.community.CommunityScope, title: String, body: String, category: com.kampusagi.android.domain.community.PostCategory): com.kampusagi.android.domain.community.Post {
        created += listOf(scope, title, body, category)
        return createBlock(scope, title, body, category)
    }
    override suspend fun setLiked(postId: String, liked: Boolean) { likeCalls += postId to liked; likeBlock(postId, liked) }
    override suspend fun getComments(postId: String) = commentsBlock(postId)
    override suspend fun addComment(postId: String, body: String) = addCommentBlock(postId, body)
}
