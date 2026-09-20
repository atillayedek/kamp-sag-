package com.kampusagi.android.feature.matches

import com.kampusagi.android.domain.analytics.AnalyticsEvent
import com.kampusagi.android.testutil.FakeAnalyticsTracker
import androidx.lifecycle.SavedStateHandle
import com.kampusagi.android.core.ui.Loadable
import com.kampusagi.android.domain.common.AppError
import com.kampusagi.android.domain.match.PublicProfile
import com.kampusagi.android.testutil.FakeChatRepository
import com.kampusagi.android.testutil.FakeMatchRepository
import com.kampusagi.android.testutil.MainDispatcherRule
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UserProfileViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private val matches = FakeMatchRepository()
    private val chat = FakeChatRepository()

    private val analytics = FakeAnalyticsTracker()

    private fun viewModel() = UserProfileViewModel(SavedStateHandle(mapOf("userId" to "user-9")), matches, chat, analytics)

    @Test
    fun `profil rota argumanindaki kullanici icin yuklenir`() = runTest {
        val requested = mutableListOf<String>()
        matches.profileBlock = { id ->
            requested += id
            PublicProfile(id, "Ayşe", "Bölüm", "Üni", null, "İlan", listOf("etiket"))
        }
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(listOf("user-9"), requested)
        assertEquals("Ayşe", (vm.uiState.value.profile as Loadable.Success).value.fullName)
    }

    @Test
    fun `profil yuklenemezse hata gosterilir ve tekrar dene calisir`() = runTest {
        matches.profileBlock = { throw AppError.NotFound() }
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.profile is Loadable.Failure)

        matches.profileBlock = { id -> PublicProfile(id, "Ayşe", null, null, null, null, emptyList()) }
        vm.load()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.profile is Loadable.Success)
    }

    @Test
    fun `mesaj at ilan olmadan sohbet baslatir`() = runTest {
        chat.startBlock = { _, _ -> "conv-5" }
        val vm = viewModel()
        advanceUntilIdle()
        val opened = mutableListOf<String>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { vm.openChat.first().also { opened += it } }

        vm.message()
        advanceUntilIdle()

        assertEquals(listOf("user-9" to null), chat.started)
        assertEquals(listOf("conv-5"), opened)
        assertEquals(listOf(AnalyticsEvent.CHAT_STARTED), analytics.events)
        job.cancel()
    }

    @Test
    fun `sohbet baslatma hatasi mesaj olarak gosterilir`() = runTest {
        chat.startBlock = { _, _ -> throw AppError.Network() }
        val vm = viewModel()
        advanceUntilIdle()

        vm.message()
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.message)
        assertFalse(vm.uiState.value.isStartingChat)
    }
}
