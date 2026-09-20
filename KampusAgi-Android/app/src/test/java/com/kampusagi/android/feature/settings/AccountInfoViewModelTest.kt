package com.kampusagi.android.feature.settings

import com.kampusagi.android.core.ui.Loadable
import com.kampusagi.android.domain.common.AppError
import com.kampusagi.android.domain.profile.Profile
import com.kampusagi.android.testutil.FakeAuthRepository
import com.kampusagi.android.testutil.FakeProfileRepository
import com.kampusagi.android.testutil.MainDispatcherRule
import com.kampusagi.android.testutil.testUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class AccountInfoViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private val profiles = FakeProfileRepository(FakeProfileRepository.completeProfile)
    private val auth = FakeAuthRepository(testUser)

    private fun viewModel() = AccountInfoViewModel(profiles, auth)

    @Test
    fun `alanlar profilden ve e posta oturumdan gelir degisiklik yokken kaydet kapali`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(testUser.email, state.email)
        assertEquals("Test Öğrenci", state.fullName)
        assertEquals("ogrenci", state.username)
        assertEquals("Bilgisayar Mühendisliği", state.department)
        assertFalse(state.isDirty)
        assertFalse(state.canSave)
    }

    @Test
    fun `yukleme hatasi Failure olur`() = runTest {
        profiles.getBlock = { throw AppError.Network() }
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.profile is Loadable.Failure)
        assertFalse(vm.uiState.value.canSave)
    }

    @Test
    fun `degisiklik yapilinca kaydet acilir bosluklar degisiklik sayilmaz`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onFullNameChange("  Test Öğrenci  ")
        assertFalse(vm.uiState.value.isDirty)

        vm.onDepartmentChange("Makine Mühendisliği")
        assertTrue(vm.uiState.value.canSave)
    }

    @Test
    fun `gecersiz kullanici adi veya kisa ad kaydi engeller`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onUsernameChange("Geçersiz Ad!")
        assertFalse(vm.uiState.value.isUsernameValid)
        assertFalse(vm.uiState.value.canSave)

        vm.onUsernameChange("gecerli_ad")
        assertTrue(vm.uiState.value.canSave)

        vm.onFullNameChange("A")
        assertFalse(vm.uiState.value.canSave)
    }

    @Test
    fun `kullanici adi kucuk harfe cevrilir`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onUsernameChange("YeniAd")
        assertEquals("yeniad", vm.uiState.value.username)
    }

    @Test
    fun `kaydet mevcut universite kimligiyle gunceller ve basari mesaji gosterir`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onDepartmentChange("Makine Mühendisliği")
        vm.onUsernameChange("yeni_ad")
        profiles.getBlock = { profiles.profile.copy(username = "yeni_ad", department = "Makine Mühendisliği") }

        vm.save()
        advanceUntilIdle()

        // Üniversite istemciden değiştirilemez: her zaman mevcut kimlik gönderilir.
        assertEquals(listOf(listOf("Test Öğrenci", "yeni_ad", "uni-1", "Makine Mühendisliği")), profiles.updates)
        assertNotNull(vm.uiState.value.message)
        assertFalse(vm.uiState.value.isDirty)
        assertFalse(vm.uiState.value.isSaving)
    }

    @Test
    fun `kullanici adi alinmissa alan hatasi gosterilir mesaj degil`() = runTest {
        profiles.updateBlock = { _, _, _, _ -> throw AppError.UsernameTaken() }
        val vm = viewModel()
        advanceUntilIdle()
        vm.onUsernameChange("alinmis_ad")

        vm.save()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.usernameTaken)
        assertFalse(vm.uiState.value.isSaving)
        // Kullanıcı adını değiştirince hata kalkar.
        vm.onUsernameChange("baska_ad")
        assertFalse(vm.uiState.value.usernameTaken)
    }

    @Test
    fun `diger kayit hatalari mesaj olarak gosterilir ve girilenler korunur`() = runTest {
        profiles.updateBlock = { _, _, _, _ -> throw AppError.Network() }
        val vm = viewModel()
        advanceUntilIdle()
        vm.onDepartmentChange("Yeni Bölüm")

        vm.save()
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.message)
        assertEquals("Yeni Bölüm", vm.uiState.value.department)
        assertTrue(vm.uiState.value.canSave)
    }

    @Test
    fun `degisiklik yokken save hicbir istek gondermez`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()
        assertTrue(profiles.updates.isEmpty())
    }

    @Test
    fun `universite kimligi olmayan profil kaydedilemez`() = runTest {
        profiles.getBlock = { Profile("user-1", "ogrenci", "Test Öğrenci", null, null, "Bölüm") }
        val vm = viewModel()
        advanceUntilIdle()
        vm.onDepartmentChange("Başka")

        vm.save()
        advanceUntilIdle()

        assertTrue(profiles.updates.isEmpty())
    }
}
