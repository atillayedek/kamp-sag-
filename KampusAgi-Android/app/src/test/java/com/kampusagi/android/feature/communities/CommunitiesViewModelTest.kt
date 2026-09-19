package com.kampusagi.android.feature.communities

import com.kampusagi.android.core.ui.UiText
import com.kampusagi.android.domain.common.AppError
import com.kampusagi.android.domain.community.CommunityScope
import com.kampusagi.android.domain.profile.Profile
import com.kampusagi.android.testutil.FakePostRepository
import com.kampusagi.android.testutil.FakeProfileRepository
import com.kampusagi.android.testutil.MainDispatcherRule
import com.kampusagi.android.testutil.testPost
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class CommunitiesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val posts = FakePostRepository()
    private val profiles = FakeProfileRepository(FakeProfileRepository.completeProfile.copy(universityName = "Test Üniversitesi"))
    private val events = PostEvents()

    private fun page(prefix: String, count: Int, start: Instant = Instant.parse("2026-09-19T12:00:00Z")) =
        (0 until count).map { testPost("$prefix$it", createdAt = start.minusSeconds(it * 60L)) }

    private fun viewModel() = CommunitiesViewModel(posts, profiles, events)

    @Test
    fun `acilista genel akisin ilk sayfasi cursorsiz istenir`() {
        posts.feedBlock = { _, _, _ -> page("g", 3) }
        val vm = viewModel()

        val feed = vm.uiState.value.feed
        assertFalse(feed.isLoading)
        assertEquals(3, feed.items.size)
        assertTrue("sayfa boyutundan az geldi -> son sayfa", feed.endReached)
        assertEquals(Triple(CommunityScope.GENERAL, CommunitiesViewModel.PAGE_SIZE, null), posts.feedCalls.first())
        assertEquals("Test Üniversitesi", vm.uiState.value.universityName)
    }

    @Test
    fun `universitem sekmesi yalnizca ilk secildiginde yuklenir`() {
        posts.feedBlock = { scope, _, _ -> page(scope.name.take(1), 2) }
        val vm = viewModel()

        vm.selectScope(CommunityScope.UNIVERSITY)
        vm.selectScope(CommunityScope.GENERAL)
        vm.selectScope(CommunityScope.UNIVERSITY)

        assertEquals(2, posts.feedCalls.size)
        assertEquals(CommunityScope.UNIVERSITY, vm.uiState.value.scope)
        assertEquals("U0", vm.uiState.value.feed.items.first().id)
    }

    @Test
    fun `sonraki sayfa son gonderinin zamaniyla istenir ve tekrarlar ayiklanir`() {
        val first = page("a", CommunitiesViewModel.PAGE_SIZE)
        posts.feedBlock = { _, _, before ->
            if (before == null) first else listOf(first.last(), testPost("z1", createdAt = first.last().createdAt.minusSeconds(60)))
        }
        val vm = viewModel()
        assertFalse(vm.uiState.value.feed.endReached)

        vm.loadMore()

        val feed = vm.uiState.value.feed
        assertEquals(first.last().createdAt, posts.feedCalls.last().third)
        assertEquals(CommunitiesViewModel.PAGE_SIZE + 1, feed.items.size)
        assertEquals(1, feed.items.count { it.id == first.last().id })
        assertTrue(feed.endReached)
    }

    @Test
    fun `akisin sonuna gelindiginde loadMore istek atmaz`() {
        posts.feedBlock = { _, _, _ -> page("a", 2) }
        val vm = viewModel()
        vm.loadMore()
        assertEquals(1, posts.feedCalls.size)
    }

    @Test
    fun `sonraki sayfa hatasi listeyi korur ve tekrar denenebilir`() {
        val first = page("a", CommunitiesViewModel.PAGE_SIZE)
        var failNext = true
        posts.feedBlock = { _, _, before ->
            when {
                before == null -> first
                failNext -> { failNext = false; throw AppError.Network() }
                else -> page("b", 1, first.last().createdAt.minusSeconds(60))
            }
        }
        val vm = viewModel()
        vm.loadMore()
        assertTrue(vm.uiState.value.feed.loadMoreError is AppError.Network)
        assertEquals(CommunitiesViewModel.PAGE_SIZE, vm.uiState.value.feed.items.size)

        vm.loadMore()
        assertNull(vm.uiState.value.feed.loadMoreError)
        assertEquals(CommunitiesViewModel.PAGE_SIZE + 1, vm.uiState.value.feed.items.size)
    }

    @Test
    fun `ilk yukleme hatasi tam ekran hata olur ve tekrar dene toparlar`() {
        var attempts = 0
        posts.feedBlock = { _, _, _ -> if (++attempts == 1) throw AppError.Network() else page("a", 2) }
        val vm = viewModel()
        assertTrue(vm.uiState.value.feed.error is AppError.Network)
        assertTrue(vm.uiState.value.feed.items.isEmpty())

        vm.retry()
        assertNull(vm.uiState.value.feed.error)
        assertEquals(2, vm.uiState.value.feed.items.size)
    }

    @Test
    fun `yenileme listeyi degistirir ve hata durumunda eski listeyi korur`() {
        var round = 0
        posts.feedBlock = { _, _, _ ->
            when (++round) {
                1 -> page("a", 2)
                2 -> page("n", 3)
                else -> throw AppError.Network()
            }
        }
        val vm = viewModel()
        vm.refresh()
        assertEquals(listOf("n0", "n1", "n2"), vm.uiState.value.feed.items.map { it.id })
        assertFalse(vm.uiState.value.feed.isRefreshing)

        vm.refresh()
        assertEquals(3, vm.uiState.value.feed.items.size)
        assertFalse(vm.uiState.value.feed.isRefreshing)
        assertNotNull(vm.uiState.value.message)
    }

    @Test
    fun `begeni iyimser guncellenir ve basariyla kalir`() {
        posts.feedBlock = { _, _, _ -> listOf(testPost("p1", likeCount = 4)) }
        val vm = viewModel()

        vm.toggleLike(vm.uiState.value.feed.items.single())

        val post = vm.uiState.value.feed.items.single()
        assertTrue(post.likedByMe)
        assertEquals(5, post.likeCount)
        assertEquals(listOf("p1" to true), posts.likeCalls)
    }

    @Test
    fun `begeni basarisiz olursa geri alinir ve mesaj gosterilir`() {
        posts.feedBlock = { _, _, _ -> listOf(testPost("p1", likeCount = 4)) }
        posts.likeBlock = { _, _ -> throw AppError.Network() }
        val vm = viewModel()

        vm.toggleLike(vm.uiState.value.feed.items.single())

        val post = vm.uiState.value.feed.items.single()
        assertFalse(post.likedByMe)
        assertEquals(4, post.likeCount)
        assertNotNull(vm.uiState.value.message)
    }

    @Test
    fun `beğeni istegi surerken ikinci dokunus yok sayilir`() {
        posts.feedBlock = { _, _, _ -> listOf(testPost("p1", likeCount = 0)) }
        val gate = CompletableDeferred<Unit>()
        posts.likeBlock = { _, _ -> gate.await() }
        val vm = viewModel()

        vm.toggleLike(vm.uiState.value.feed.items.single())
        vm.toggleLike(vm.uiState.value.feed.items.single())
        gate.complete(Unit)

        assertEquals(1, posts.likeCalls.size)
        assertEquals(1, vm.uiState.value.feed.items.single().likeCount)
    }

    @Test
    fun `baska ekrandan gelen degisiklik bildirimi akisi yeniler`() {
        var round = 0
        posts.feedBlock = { _, _, _ -> if (++round == 1) page("a", 1) else page("b", 2) }
        val vm = viewModel()
        assertEquals(1, vm.uiState.value.feed.items.size)

        events.notifyChanged()

        assertEquals(listOf("b0", "b1"), vm.uiState.value.feed.items.map { it.id })
    }

    @Test
    fun `mesaj tuketilince temizlenir`() {
        posts.feedBlock = { _, _, _ -> listOf(testPost("p1")) }
        posts.likeBlock = { _, _ -> throw AppError.Forbidden() }
        val vm = viewModel()
        vm.toggleLike(vm.uiState.value.feed.items.single())
        assertTrue(vm.uiState.value.message is UiText)
        vm.consumeMessage()
        assertNull(vm.uiState.value.message)
    }

    @Test
    fun `profil okunamazsa akis yine de calisir`() {
        profiles.getBlock = { throw AppError.Network() }
        posts.feedBlock = { _, _, _ -> page("a", 1) }
        val vm = viewModel()
        assertNull(vm.uiState.value.universityName)
        assertEquals(1, vm.uiState.value.feed.items.size)
    }
}
