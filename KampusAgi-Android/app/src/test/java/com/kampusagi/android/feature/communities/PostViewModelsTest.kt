package com.kampusagi.android.feature.communities

import com.kampusagi.android.domain.analytics.AnalyticsEvent
import com.kampusagi.android.testutil.FakeAnalyticsTracker
import androidx.lifecycle.SavedStateHandle
import com.kampusagi.android.R
import com.kampusagi.android.core.time.RelativeTime
import com.kampusagi.android.core.time.relativeTimeOf
import com.kampusagi.android.core.ui.Loadable
import com.kampusagi.android.core.ui.uiText
import com.kampusagi.android.domain.common.AppError
import com.kampusagi.android.domain.community.CommunityScope
import com.kampusagi.android.domain.community.PostCategory
import com.kampusagi.android.testutil.FakePostRepository
import com.kampusagi.android.testutil.MainDispatcherRule
import com.kampusagi.android.testutil.testPost
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PostViewModelsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val posts = FakePostRepository()
    private val events = PostEvents()
    private val analytics = FakeAnalyticsTracker()

    // --- CreatePostViewModel -------------------------------------------------------------------

    private fun createVm(scope: CommunityScope = CommunityScope.UNIVERSITY) =
        CreatePostViewModel(SavedStateHandle(mapOf("scope" to scope.name)), posts, events, analytics)

    @Test
    fun `baslik govde ve kategori olmadan paylasilamaz`() {
        val vm = createVm()
        assertFalse(vm.uiState.value.canSubmit)
        vm.onTitleChange("Başlık")
        vm.onBodyChange("Gövde")
        assertFalse("kategori seçilmedi", vm.uiState.value.canSubmit)
        vm.onCategorySelected(PostCategory.SPORTS)
        assertTrue(vm.uiState.value.canSubmit)
        vm.onTitleChange("   ")
        assertFalse("boşluktan ibaret başlık", vm.uiState.value.canSubmit)
    }

    @Test
    fun `uzunluk sinirlari alan girisinde uygulanir`() {
        val vm = createVm()
        vm.onTitleChange("a".repeat(500))
        vm.onBodyChange("b".repeat(9000))
        assertEquals(CreatePostUiState.MAX_TITLE, vm.uiState.value.title.length)
        assertEquals(CreatePostUiState.MAX_BODY, vm.uiState.value.body.length)
    }

    @Test
    fun `paylasim dogru kapsamla gonderilir bildirim yayilir ve ekran kapanir`() = runTest {
        val vm = createVm(CommunityScope.UNIVERSITY)
        vm.onTitleChange("Başlık")
        vm.onBodyChange("Gövde")
        vm.onCategorySelected(PostCategory.HOUSING)

        var changed = 0
        val changeJob = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { events.changes.first().also { changed++ } }
        val createdJob = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { vm.created.first() }

        vm.submit()
        changeJob.join()
        createdJob.join()

        assertEquals(listOf<Any>(CommunityScope.UNIVERSITY, "Başlık", "Gövde", PostCategory.HOUSING), posts.created.single())
        assertEquals(1, changed)
        assertFalse(vm.uiState.value.isSubmitting)
        assertEquals(listOf(AnalyticsEvent.POST_CREATED), analytics.events)
    }

    @Test
    fun `paylasim hatasi mesaj gosterir ve ekrani kapatmaz`() {
        posts.createBlock = { _, _, _, _ -> throw AppError.Forbidden() }
        val vm = createVm()
        vm.onTitleChange("Başlık")
        vm.onBodyChange("Gövde")
        vm.onCategorySelected(PostCategory.OTHER)
        vm.submit()

        assertEquals(uiText(R.string.error_forbidden), vm.uiState.value.message)
        assertFalse(vm.uiState.value.isSubmitting)
        assertTrue("başarısız paylaşım olay üretmez", analytics.events.isEmpty())
    }

    // --- PostDetailViewModel -------------------------------------------------------------------

    private fun detailVm() = PostDetailViewModel(SavedStateHandle(mapOf("postId" to "p1")), posts, events)

    @Test
    fun `detay gonderi ve yorumlari birlikte yukler`() {
        posts.getPostBlock = { testPost(it, commentCount = 1) }
        posts.commentsBlock = { listOf(com.kampusagi.android.domain.community.Comment("c1", it, "u", "Ali", null, "Merhaba", Instant.EPOCH)) }
        val vm = detailVm()

        val detail = (vm.uiState.value.detail as Loadable.Success).value
        assertEquals("p1", detail.post.id)
        assertEquals(listOf("c1"), detail.comments.map { it.id })
    }

    @Test
    fun `detay yuklenemezse hata durumu olur ve tekrar denenebilir`() {
        var attempts = 0
        posts.getPostBlock = { if (++attempts == 1) throw AppError.NotFound() else testPost(it) }
        val vm = detailVm()
        assertTrue(vm.uiState.value.detail is Loadable.Failure)
        vm.load()
        assertTrue(vm.uiState.value.detail is Loadable.Success)
    }

    @Test
    fun `yorum eklenince liste ve sayac guncellenir taslak temizlenir`() {
        posts.getPostBlock = { testPost(it, commentCount = 0) }
        val vm = detailVm()
        vm.onCommentDraftChange("  Yeni yorum ")
        assertTrue(vm.uiState.value.canSend)

        vm.sendComment()

        val detail = (vm.uiState.value.detail as Loadable.Success).value
        assertEquals(1, detail.post.commentCount)
        assertEquals(listOf("c-new"), detail.comments.map { it.id })
        assertEquals("", vm.uiState.value.commentDraft)
    }

    @Test
    fun `bos yorum gonderilemez`() {
        val vm = detailVm()
        vm.onCommentDraftChange("   ")
        assertFalse(vm.uiState.value.canSend)
    }

    @Test
    fun `detayda begeni basarisiz olursa geri alinir`() {
        posts.getPostBlock = { testPost(it, likeCount = 2) }
        posts.likeBlock = { _, _ -> throw AppError.Network() }
        val vm = detailVm()

        vm.toggleLike()

        val post = (vm.uiState.value.detail as Loadable.Success).value.post
        assertFalse(post.likedByMe)
        assertEquals(2, post.likeCount)
        assertEquals(uiText(R.string.error_network), vm.uiState.value.message)
    }

    // --- Domain / zaman ------------------------------------------------------------------------

    @Test
    fun `withLiked sayaci sifirin altina indirmez ve gereksiz degismez`() {
        val post = testPost("x", likeCount = 0, liked = false)
        assertEquals(post, post.withLiked(false))
        assertEquals(1, post.withLiked(true).likeCount)
        assertEquals(0, testPost("y", likeCount = 0, liked = true).withLiked(false).likeCount)
    }

    @Test
    fun `goreli zaman esikleri`() {
        val now = Instant.parse("2026-09-19T12:00:00Z")
        assertEquals(RelativeTime.JustNow, relativeTimeOf(now.minusSeconds(30), now))
        assertEquals(RelativeTime.JustNow, relativeTimeOf(now.plusSeconds(120), now))
        assertEquals(RelativeTime.MinutesAgo(5), relativeTimeOf(now.minusSeconds(5 * 60), now))
        assertEquals(RelativeTime.HoursAgo(2), relativeTimeOf(now.minusSeconds(2 * 3600 + 59), now))
        assertEquals(RelativeTime.DaysAgo(3), relativeTimeOf(now.minusSeconds(3 * 86400), now))
        assertEquals(RelativeTime.WeeksAgo(2), relativeTimeOf(now.minusSeconds(15 * 86400), now))
        assertTrue(relativeTimeOf(now.minusSeconds(40 * 86400), now) is RelativeTime.Absolute)
    }
}
