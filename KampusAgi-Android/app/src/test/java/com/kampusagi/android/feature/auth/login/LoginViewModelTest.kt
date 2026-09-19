package com.kampusagi.android.feature.auth.login

import com.kampusagi.android.R
import com.kampusagi.android.core.ui.uiText
import com.kampusagi.android.domain.common.AppError
import com.kampusagi.android.testutil.FakeAuthRepository
import com.kampusagi.android.testutil.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LoginViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val auth = FakeAuthRepository()
    private fun viewModel() = LoginViewModel(auth)

    @Test
    fun `e-posta ve sifre dolu degilse giris yapilamaz`() {
        val vm = viewModel()
        assertFalse(vm.uiState.value.canSubmit)
        vm.onEmailChange("a@b.co")
        assertFalse(vm.uiState.value.canSubmit)
        vm.onPasswordChange("gizli")
        assertTrue(vm.uiState.value.canSubmit)
    }

    @Test
    fun `basarili giriste e-posta kirpilarak gonderilir`() {
        var received: Pair<String, String>? = null
        auth.signInBlock = { e, p -> received = e to p; com.kampusagi.android.testutil.testUser }
        val vm = viewModel()
        vm.onEmailChange("  a@b.co ")
        vm.onPasswordChange("gizli")
        vm.signIn()

        assertEquals("a@b.co" to "gizli", received)
        assertFalse(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.message)
    }

    @Test
    fun `hatali giris Turkce mesaj gosterir ve yukleniyor durumu kapanir`() {
        auth.signInBlock = { _, _ -> throw AppError.InvalidCredentials() }
        val vm = viewModel()
        vm.onEmailChange("a@b.co")
        vm.onPasswordChange("yanlis")
        vm.signIn()

        assertEquals(uiText(R.string.error_invalid_credentials), vm.uiState.value.message)
        assertFalse(vm.uiState.value.isLoading)

        vm.consumeMessage()
        assertNull(vm.uiState.value.message)
    }

    @Test
    fun `ag hatasi baglanti mesajina cevrilir`() {
        auth.signInBlock = { _, _ -> throw AppError.Network() }
        val vm = viewModel()
        vm.onEmailChange("a@b.co")
        vm.onPasswordChange("gizli")
        vm.signIn()

        assertEquals(uiText(R.string.error_network), vm.uiState.value.message)
    }

    @Test
    fun `sifre sifirlama e-posta olmadan uyari verir`() {
        val vm = viewModel()
        vm.sendPasswordReset()
        assertEquals(uiText(R.string.login_password_reset_missing_email), vm.uiState.value.message)
    }

    @Test
    fun `sifre sifirlama basarili olunca bilgi mesaji gosterir`() {
        val vm = viewModel()
        vm.onEmailChange("a@b.co")
        vm.sendPasswordReset()
        assertEquals(uiText(R.string.login_password_reset_sent), vm.uiState.value.message)
    }

    @Test
    fun `Google yapilandirilmamissa acik bir mesaj gosterilir`() {
        val vm = viewModel()
        vm.onGoogleNotConfigured()
        assertEquals(uiText(R.string.login_google_not_configured), vm.uiState.value.message)
    }
}
