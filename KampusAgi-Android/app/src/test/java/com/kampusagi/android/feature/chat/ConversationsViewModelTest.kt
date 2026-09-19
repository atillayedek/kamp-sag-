package com.kampusagi.android.feature.chat

import com.kampusagi.android.core.ui.Loadable
import com.kampusagi.android.domain.common.AppError
import com.kampusagi.android.testutil.FakeChatRepository
import com.kampusagi.android.testutil.MainDispatcherRule
import com.kampusagi.android.testutil.testConversation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private val repository = FakeChatRepository()

    @Test
    fun `sohbetler yuklenir ve okunmamis toplam hesaplanir`() = runTest {
        repository.conversationsBlock = { listOf(testConversation("a", unread = 2), testConversation("b", unread = 0), testConversation("c", unread = 3)) }
        val vm = ConversationsViewModel(repository)
        advanceUntilIdle()

        assertEquals(3, (vm.uiState.value.conversations as Loadable.Success).value.size)
        assertEquals(5, vm.uiState.value.unreadTotal)
    }

    @Test
    fun `yuklenirken ve hatada okunmamis toplam sifirdir`() = runTest {
        repository.conversationsBlock = { throw AppError.Network() }
        val vm = ConversationsViewModel(repository)
        assertEquals(0, vm.uiState.value.unreadTotal)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.conversations is Loadable.Failure)
        assertEquals(0, vm.uiState.value.unreadTotal)
    }

    @Test
    fun `yeni mesaj sinyali listeyi sessizce tazeler`() = runTest {
        var unread = 1
        repository.conversationsBlock = { listOf(testConversation("a", unread = unread)) }
        val vm = ConversationsViewModel(repository)
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.unreadTotal)

        unread = 4
        repository.inbox.emit(Unit)
        advanceUntilIdle()

        assertEquals(4, vm.uiState.value.unreadTotal)
    }

    @Test
    fun `sessiz tazeleme basarisiz olursa mevcut liste ekranda kalir`() = runTest {
        repository.conversationsBlock = { listOf(testConversation("a", unread = 2)) }
        val vm = ConversationsViewModel(repository)
        advanceUntilIdle()

        repository.conversationsBlock = { throw AppError.Network() }
        vm.refresh()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.conversations is Loadable.Success)
        assertEquals(2, vm.uiState.value.unreadTotal)
    }

    @Test
    fun `ilk yukleme basarisizsa sessiz tazeleme de hata gosterir ve tekrar dene calisir`() = runTest {
        repository.conversationsBlock = { throw AppError.Network() }
        val vm = ConversationsViewModel(repository)
        advanceUntilIdle()

        vm.refresh()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.conversations is Loadable.Failure)

        repository.conversationsBlock = { listOf(testConversation("a")) }
        vm.load()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.conversations is Loadable.Success)
    }
}
