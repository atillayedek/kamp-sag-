package com.kampusagi.android.feature.settings

import com.kampusagi.android.core.ui.Loadable
import com.kampusagi.android.domain.common.AppError
import com.kampusagi.android.testutil.FakeNotificationPreferencesRepository
import com.kampusagi.android.testutil.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationSettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private val repository = FakeNotificationPreferencesRepository()

    private fun enabled(vm: NotificationSettingsViewModel) = (vm.uiState.value.newMessage as Loadable.Success).value

    @Test
    fun `tercih sunucudan okunur`() = runTest {
        repository.getBlock = { false }
        val vm = NotificationSettingsViewModel(repository)
        advanceUntilIdle()
        assertEquals(false, enabled(vm))
    }

    @Test
    fun `okuma hatasi Failure olur ve tekrar dene calisir`() = runTest {
        repository.getBlock = { throw AppError.Network() }
        val vm = NotificationSettingsViewModel(repository)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.newMessage is Loadable.Failure)

        repository.getBlock = { true }
        vm.load()
        advanceUntilIdle()
        assertEquals(true, enabled(vm))
    }

    @Test
    fun `anahtar aninda doner ve sunucuya yazilir`() = runTest {
        val vm = NotificationSettingsViewModel(repository)
        advanceUntilIdle()

        vm.onNewMessageChange(false)
        assertEquals(false, enabled(vm))
        advanceUntilIdle()

        assertEquals(listOf(false), repository.saved)
    }

    @Test
    fun `ayni deger tekrar verilirse istek gonderilmez`() = runTest {
        val vm = NotificationSettingsViewModel(repository)
        advanceUntilIdle()
        vm.onNewMessageChange(true)
        advanceUntilIdle()
        assertTrue(repository.saved.isEmpty())
    }

    @Test
    fun `kayit basarisiz olursa eski degere donulur ve mesaj gosterilir`() = runTest {
        repository.setBlock = { throw AppError.Network() }
        val vm = NotificationSettingsViewModel(repository)
        advanceUntilIdle()

        vm.onNewMessageChange(false)
        advanceUntilIdle()

        assertEquals(true, enabled(vm))
        assertNotNull(vm.uiState.value.message)
        vm.consumeMessage()
        assertEquals(null, vm.uiState.value.message)
    }

    @Test
    fun `hizli ac kapa kayitlari gonderilme sirasiyla uygulanir`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var first = true
        repository.setBlock = {
            if (first) {
                first = false
                gate.await()
            }
        }
        val vm = NotificationSettingsViewModel(repository)
        advanceUntilIdle()

        vm.onNewMessageChange(false)
        runCurrent()
        vm.onNewMessageChange(true)
        runCurrent()
        // İlk kayıt henüz bitmedi; ikincisi onu beklemeli (sıra bozulup eski değer sunucuda kalmamalı).
        assertTrue(repository.saved.isEmpty())

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(false, true), repository.saved)
        assertEquals(true, enabled(vm))
    }
}
