package com.kampusagi.android.feature.settings

import com.kampusagi.android.core.ui.Loadable
import com.kampusagi.android.domain.common.AppError
import com.kampusagi.android.domain.settings.ThemeMode
import com.kampusagi.android.testutil.FakeAccountRepository
import com.kampusagi.android.testutil.FakeProfileRepository
import com.kampusagi.android.testutil.FakeThemePreferenceRepository
import com.kampusagi.android.testutil.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private val profiles = FakeProfileRepository(FakeProfileRepository.completeProfile)
    private val theme = FakeThemePreferenceRepository()
    private val account = FakeAccountRepository()

    private fun viewModel() = ProfileViewModel(profiles, theme, account)

    @Test
    fun `profil ve kayitli tema tercihi yuklenir`() = runTest {
        val vm = ProfileViewModel(profiles, FakeThemePreferenceRepository(ThemeMode.DARK), account)
        advanceUntilIdle()

        assertEquals("Test Öğrenci", (vm.uiState.value.profile as Loadable.Success).value.fullName)
        assertEquals(ThemeMode.DARK, vm.uiState.value.themeMode)
    }

    @Test
    fun `profil yuklenemezse hata gosterilir ve tekrar dene calisir`() = runTest {
        profiles.getBlock = { throw AppError.Network() }
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.profile is Loadable.Failure)

        profiles.getBlock = { FakeProfileRepository.completeProfile }
        vm.load()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.profile is Loadable.Success)
    }

    @Test
    fun `koyu gorunum anahtari acik veya koyu sabitler sisteme donus SYSTEM yazar`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onDarkModeChange(true)
        advanceUntilIdle()
        assertEquals(ThemeMode.DARK, vm.uiState.value.themeMode)

        vm.onDarkModeChange(false)
        advanceUntilIdle()
        assertEquals(ThemeMode.LIGHT, vm.uiState.value.themeMode)

        vm.useSystemTheme()
        advanceUntilIdle()
        assertEquals(ThemeMode.SYSTEM, vm.uiState.value.themeMode)
        assertEquals(listOf(ThemeMode.DARK, ThemeMode.LIGHT, ThemeMode.SYSTEM), theme.saved)
    }

    @Test
    fun `tercih yazilamazsa mesaj gosterilir ve uygulama cokmez`() = runTest {
        theme.setBlock = { throw IOException("disk dolu") }
        val vm = viewModel()
        advanceUntilIdle()

        vm.onDarkModeChange(true)
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.message)
        assertEquals(ThemeMode.SYSTEM, vm.uiState.value.themeMode)
        vm.consumeMessage()
        assertNull(vm.uiState.value.message)
    }

    @Test
    fun `hesap silme once onay penceresi acar ve vazgecilebilir`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.requestDeleteAccount()
        assertTrue(vm.uiState.value.showDeleteDialog)
        assertEquals(0, account.deleteCalls)

        vm.dismissDeleteDialog()
        assertFalse(vm.uiState.value.showDeleteDialog)
        assertEquals(0, account.deleteCalls)
    }

    @Test
    fun `onaylaninca hesap silinir ve cift dokunus tek istek uretir`() = runTest {
        val gate = CompletableDeferred<Unit>()
        account.deleteBlock = { gate.await() }
        val vm = viewModel()
        advanceUntilIdle()
        vm.requestDeleteAccount()

        vm.confirmDeleteAccount()
        vm.confirmDeleteAccount()
        runCurrent()

        assertEquals(1, account.deleteCalls)
        assertTrue(vm.uiState.value.isDeleting)

        // Silinirken pencere kapatılamaz (yarım kalan işlem ekranda görünür kalır).
        vm.dismissDeleteDialog()
        assertTrue(vm.uiState.value.showDeleteDialog)

        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isDeleting)
    }

    @Test
    fun `silme hatasi mesaj gosterir pencereyi kapatir ve oturum acik kalir`() = runTest {
        account.deleteBlock = { throw AppError.Server("Hesabınız silinemedi. Lütfen tekrar deneyin.") }
        val vm = viewModel()
        advanceUntilIdle()
        vm.requestDeleteAccount()

        vm.confirmDeleteAccount()
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.message)
        assertFalse(vm.uiState.value.showDeleteDialog)
        assertFalse(vm.uiState.value.isDeleting)
    }
}
