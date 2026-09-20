package com.kampusagi.android.snapshot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.kampusagi.android.core.designsystem.component.AppBottomBar
import com.kampusagi.android.core.designsystem.component.BottomBarItem
import com.kampusagi.android.core.ui.Loadable
import com.kampusagi.android.domain.common.AppError
import com.kampusagi.android.domain.community.Comment
import com.kampusagi.android.domain.community.CommunityScope
import com.kampusagi.android.domain.community.Post
import com.kampusagi.android.domain.community.PostCategory
import com.kampusagi.android.feature.communities.CommunitiesContent
import com.kampusagi.android.feature.communities.CommunitiesUiState
import com.kampusagi.android.feature.communities.CreatePostContent
import com.kampusagi.android.feature.communities.CreatePostUiState
import com.kampusagi.android.feature.communities.FeedState
import com.kampusagi.android.feature.communities.PostDetail
import com.kampusagi.android.feature.communities.PostDetailContent
import com.kampusagi.android.feature.communities.PostDetailUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = SNAPSHOT_QUALIFIERS)
class CommunitiesSnapshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val now = Instant.parse("2026-09-19T12:00:00Z")

    private val posts = listOf(
        Post(
            id = "1", authorId = "a", authorName = "Ayşe Nur", authorDepartment = "Bilgisayar Mühendisliği",
            authorUniversityShortName = "TÜ", authorAvatarUrl = null, title = "Veri yapıları notları",
            body = "Veri yapıları dersinin geçen yılki notlarına ihtiyacım var. Elinde olan varsa yazabilir mi?",
            category = PostCategory.ACADEMIC, createdAt = now.minusSeconds(2 * 3600), likeCount = 12, commentCount = 3, likedByMe = true,
        ),
        Post(
            id = "2", authorId = "b", authorName = "İlker Şahin", authorDepartment = "Makine Mühendisliği",
            authorUniversityShortName = "DTÜ", authorAvatarUrl = null, title = "Hafta sonu basketbol",
            body = "Cumartesi kampüs sahasında yarım saha maç yapacağız, 4 kişi daha arıyoruz.",
            category = PostCategory.SPORTS, createdAt = now.minusSeconds(3 * 86400), likeCount = 0, commentCount = 0, likedByMe = false,
        ),
    )

    private fun snap(name: String, content: @Composable () -> Unit) {
        composeRule.setContent { SideBySide(content = content) }
        composeRule.onRoot().captureRoboImage("$SNAPSHOT_DIR/$name.png")
    }

    @Composable
    private fun communities(state: CommunitiesUiState) = CommunitiesContent(
        state = state, snackbarHostState = remember { SnackbarHostState() },
        onScopeSelected = {}, onRefresh = {}, onRetry = {}, onLoadMore = {},
        onLikeClick = {}, onPostClick = {}, onCreatePost = {}, onOpenEvents = {}, now = now,
    )

    @Test
    fun feed() = snap("comm_feed") {
        communities(CommunitiesUiState(feeds = mapOf(CommunityScope.GENERAL to FeedState(items = posts, isLoading = false, endReached = true))))
    }

    @Test
    fun universityFeedWithName() = snap("comm_university") {
        communities(
            CommunitiesUiState(
                scope = CommunityScope.UNIVERSITY, universityName = "Test Üniversitesi",
                feeds = mapOf(CommunityScope.UNIVERSITY to FeedState(items = posts.take(1), isLoading = false, endReached = true)),
            ),
        )
    }

    @Test
    fun loading() = snap("comm_loading") { communities(CommunitiesUiState()) }

    @Test
    fun empty() = snap("comm_empty") {
        communities(CommunitiesUiState(feeds = mapOf(CommunityScope.GENERAL to FeedState(isLoading = false, endReached = true))))
    }

    @Test
    fun error() = snap("comm_error") {
        communities(CommunitiesUiState(feeds = mapOf(CommunityScope.GENERAL to FeedState(isLoading = false, error = AppError.Network()))))
    }

    @Test
    fun createPost() = snap("comm_create") {
        CreatePostContent(
            state = CreatePostUiState(title = "Ders notu", body = "Notlara ihtiyacım var.", category = PostCategory.ACADEMIC),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {}, onTitleChange = {}, onBodyChange = {}, onCategorySelected = {}, onSubmit = {},
        )
    }

    @Test
    fun postDetail() = snap("comm_detail") {
        PostDetailContent(
            state = PostDetailUiState(
                detail = Loadable.Success(
                    PostDetail(
                        post = posts[0],
                        comments = listOf(
                            Comment("c1", "1", "u", "Mehmet Kaya", null, "Bende var, yarın kütüphanede verebilirim.", now.minusSeconds(3600)),
                            Comment("c2", "1", "u2", "Zeynep Arslan", null, "Ben de ilgileniyorum.", now.minusSeconds(600)),
                        ),
                    ),
                ),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {}, onRetry = {}, onLikeClick = {}, onCommentDraftChange = {}, onSendComment = {}, now = now,
        )
    }

    @Test
    fun bottomBar() = snap("main_bottom_bar") {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.weight(1f))
            AppBottomBar(
                items = listOf("Topluluklar", "Eşleşmeler", "İhtiyaç", "Sohbet", "Profil").map {
                    BottomBarItem(it, Icons.Filled.Groups)
                },
                selectedIndex = 0,
                onSelect = {},
            )
        }
    }
}
