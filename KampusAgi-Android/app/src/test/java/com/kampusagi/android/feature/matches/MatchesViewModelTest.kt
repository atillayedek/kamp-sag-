package com.kampusagi.android.feature.matches

import com.kampusagi.android.domain.analytics.AnalyticsEvent
import com.kampusagi.android.testutil.FakeAnalyticsTracker
import com.kampusagi.android.core.ui.Loadable
import com.kampusagi.android.domain.common.AppError
import com.kampusagi.android.domain.match.MatchesResult
import com.kampusagi.android.domain.match.MyRequirement
import com.kampusagi.android.testutil.FakeChatRepository
import com.kampusagi.android.testutil.FakeMatchRepository
import com.kampusagi.android.testutil.MainDispatcherRule
import com.kampusagi.android.testutil.testMatch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MatchesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private val matches = FakeMatchRepository()
    private val chat = FakeChatRepository()
    private val requirement = MyRequirement("req-1", "Basketbol")

    private val analytics = FakeAnalyticsTracker()

    private fun viewModel() = MatchesViewModel(matches, chat, analytics)

    @Test
    fun `eslesmeler yuklenir`() = runTest {
        matches.matchesBlock = { MatchesResult(requirement, listOf(testMatch("a"), testMatch("b"))) }
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(listOf("a", "b"), vm.uiState.value.visibleMatches.map { it.matchId })
    }

    @Test
    fun `ilan yoksa requirement null ve liste bos gelir`() = runTest {
        matches.matchesBlock = { MatchesResult(null, emptyList()) }
        val vm = viewModel()
        advanceUntilIdle()

        assertNull((vm.uiState.value.result as Loadable.Success).value.requirement)
        assertTrue(vm.uiState.value.visibleMatches.isEmpty())
    }

    @Test
    fun `yukleme hatasi Failure olur ve tekrar dene calisir`() = runTest {
        matches.matchesBlock = { throw AppError.Network() }
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.result is Loadable.Failure)

        matches.matchesBlock = { MatchesResult(requirement, listOf(testMatch("a"))) }
        vm.load()
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.visibleMatches.size)
    }

    @Test
    fun `gec denen aday listeden kalkar`() = runTest {
        matches.matchesBlock = { MatchesResult(requirement, listOf(testMatch("a"), testMatch("b"))) }
        val vm = viewModel()
        advanceUntilIdle()

        vm.dismiss(vm.uiState.value.visibleMatches.first())

        assertEquals(listOf("b"), vm.uiState.value.visibleMatches.map { it.matchId })
    }

    @Test
    fun `yenile sunucuda yeniden hesaplatir ve gecilenleri geri getirir`() = runTest {
        matches.matchesBlock = { MatchesResult(requirement, listOf(testMatch("a"))) }
        matches.refreshBlock = { MatchesResult(requirement, listOf(testMatch("a"), testMatch("c"))) }
        val vm = viewModel()
        advanceUntilIdle()
        vm.dismiss(vm.uiState.value.visibleMatches.first())

        vm.refresh()
        advanceUntilIdle()

        assertEquals(listOf("req-1"), matches.refreshed)
        assertEquals(listOf("a", "c"), vm.uiState.value.visibleMatches.map { it.matchId })
        assertFalse(vm.uiState.value.isRefreshing)
    }

    @Test
    fun `yenileme hatasi mevcut listeyi korur ve mesaj gosterir`() = runTest {
        matches.matchesBlock = { MatchesResult(requirement, listOf(testMatch("a"))) }
        matches.refreshBlock = { throw AppError.Network() }
        val vm = viewModel()
        advanceUntilIdle()

        vm.refresh()
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.visibleMatches.size)
        assertNotNull(vm.uiState.value.message)
        assertFalse(vm.uiState.value.isRefreshing)
        vm.consumeMessage()
        assertNull(vm.uiState.value.message)
    }

    @Test
    fun `ilan yokken yenile sadece yeniden yukler`() = runTest {
        matches.matchesBlock = { MatchesResult(null, emptyList()) }
        val vm = viewModel()
        advanceUntilIdle()
        val callsBefore = matches.getMatchesCalls

        vm.refresh()
        advanceUntilIdle()

        assertTrue(matches.refreshed.isEmpty())
        assertEquals(callsBefore + 1, matches.getMatchesCalls)
    }

    @Test
    fun `mesaj at sohbeti ilan kimligiyle baslatir ve sohbet kimligini yayar`() = runTest {
        matches.matchesBlock = { MatchesResult(requirement, listOf(testMatch("a", userId = "user-a"))) }
        chat.startBlock = { _, _ -> "conv-77" }
        val vm = viewModel()
        advanceUntilIdle()
        val opened = mutableListOf<String>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { vm.openChat.first().also { opened += it } }

        vm.message(vm.uiState.value.visibleMatches.first())
        advanceUntilIdle()

        assertEquals(listOf("user-a" to "req-1"), chat.started)
        assertEquals(listOf("conv-77"), opened)
        assertEquals(listOf(AnalyticsEvent.CHAT_STARTED), analytics.events)
        assertFalse(vm.uiState.value.isStartingChat)
        job.cancel()
    }

    @Test
    fun `sohbet baslatilirken ikinci dokunus yok sayilir`() = runTest {
        matches.matchesBlock = { MatchesResult(requirement, listOf(testMatch("a"))) }
        val gate = CompletableDeferred<String>()
        chat.startBlock = { _, _ -> gate.await() }
        val vm = viewModel()
        advanceUntilIdle()
        val candidate = vm.uiState.value.visibleMatches.first()

        vm.message(candidate)
        runCurrent()
        vm.message(candidate)
        runCurrent()

        assertEquals(1, chat.started.size)
        assertTrue(vm.uiState.value.isStartingChat)
        gate.complete("conv-1")
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isStartingChat)
    }

    @Test
    fun `sohbet baslatma hatasi mesaj gosterir ve yonlendirme olmaz`() = runTest {
        matches.matchesBlock = { MatchesResult(requirement, listOf(testMatch("a"))) }
        chat.startBlock = { _, _ -> throw AppError.Forbidden() }
        val vm = viewModel()
        advanceUntilIdle()
        val opened = mutableListOf<String>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { vm.openChat.first().also { opened += it } }

        vm.message(vm.uiState.value.visibleMatches.first())
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.message)
        assertTrue(opened.isEmpty())
        assertTrue("başarısız sohbet başlatma olay üretmez", analytics.events.isEmpty())
        job.cancel()
    }
}
