package com.kampusagi.android.feature.auth.register

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kampusagi.android.R
import com.kampusagi.android.core.file.PickedDocumentReader
import com.kampusagi.android.core.session.SessionRefresher
import com.kampusagi.android.core.ui.Loadable
import com.kampusagi.android.core.ui.uiText
import com.kampusagi.android.domain.auth.SignUpResult
import com.kampusagi.android.domain.common.AppError
import com.kampusagi.android.testutil.FakeAuthRepository
import com.kampusagi.android.testutil.FakeProfileRepository
import com.kampusagi.android.testutil.FakeUniversityRepository
import com.kampusagi.android.testutil.FakeVerificationRepository
import com.kampusagi.android.testutil.MainDispatcherRule
import com.kampusagi.android.testutil.testUser
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class RegisterViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val auth = FakeAuthRepository()
    private val profiles = FakeProfileRepository()
    private val universities = FakeUniversityRepository()
    private val verification = FakeVerificationRepository()
    private val refresher = SessionRefresher()

    private fun viewModel(startStep: Int = 1) = RegisterViewModel(
        savedStateHandle = SavedStateHandle(mapOf("startStep" to startStep)),
        authRepository = auth,
        profileRepository = profiles,
        universityRepository = universities,
        verificationRepository = verification,
        documentReader = PickedDocumentReader(context, UnconfinedTestDispatcher()),
        sessionRefresher = refresher,
    )

    private fun RegisterViewModel.fillPersonalStep() {
        onFullNameChange("  Test Öğrenci ")
        onUsernameChange("Test_Ogrenci")
    }

    @Test
    fun `ilk adimda gecerli e-posta ve sifre olmadan devam edilemez`() {
        val vm = viewModel()
        assertFalse(vm.uiState.value.canContinue)

        vm.onEmailChange("gecersiz")
        vm.onPasswordChange("123456")
        assertFalse(vm.uiState.value.canContinue)

        vm.onEmailChange("ogrenci@kampus.test")
        vm.onPasswordChange("12345")
        assertFalse(vm.uiState.value.canContinue)

        vm.onPasswordChange("123456")
        assertTrue(vm.uiState.value.canContinue)
    }

    @Test
    fun `kayit oturum actiginda yerel adim ilerlemez yonlendirmeyi kok ustlenir`() {
        val vm = viewModel()
        vm.onEmailChange("ogrenci@kampus.test")
        vm.onPasswordChange("123456")
        vm.onContinue()

        val state = vm.uiState.value
        assertEquals(RegisterStep.ACCOUNT_INFO, state.step)
        assertFalse(state.awaitingEmailConfirmation)
        assertFalse(state.isLoading)
        assertEquals(testUser, auth.currentUser)
    }

    @Test
    fun `e-posta dogrulamasi gerekiyorsa bekleme durumuna gecer ve oturum acilmaz`() {
        auth.signUpBlock = { email, _ -> SignUpResult.ConfirmationRequired(email) }
        val vm = viewModel()
        vm.onEmailChange("ogrenci@kampus.test")
        vm.onPasswordChange("123456")
        vm.onContinue()

        assertTrue(vm.uiState.value.awaitingEmailConfirmation)
        assertNull(auth.currentUser)
        assertFalse(vm.uiState.value.canGoBack)
    }

    @Test
    fun `dogruladim dendiginde ayni bilgilerle giris yapilir`() {
        var signedInWith: Pair<String, String>? = null
        auth.signUpBlock = { email, _ -> SignUpResult.ConfirmationRequired(email) }
        auth.signInBlock = { email, password -> signedInWith = email to password; testUser }
        val vm = viewModel()
        vm.onEmailChange(" ogrenci@kampus.test ")
        vm.onPasswordChange("123456")
        vm.onContinue()
        vm.confirmEmailAndContinue()

        assertEquals("ogrenci@kampus.test" to "123456", signedInWith)
    }

    @Test
    fun `dogrulama e-postasi tekrar gonderilir ve bilgi mesaji gosterilir`() {
        auth.signUpBlock = { email, _ -> SignUpResult.ConfirmationRequired(email) }
        val vm = viewModel()
        vm.onEmailChange("ogrenci@kampus.test")
        vm.onPasswordChange("123456")
        vm.onContinue()
        vm.resendConfirmation()

        assertEquals(listOf("ogrenci@kampus.test"), auth.resendCalls)
        assertEquals(uiText(R.string.register_confirm_resent), vm.uiState.value.message)
    }

    @Test
    fun `kayit hatasi Turkce mesaja cevrilir`() {
        auth.signUpBlock = { _, _ -> throw AppError.EmailAlreadyRegistered() }
        val vm = viewModel()
        vm.onEmailChange("ogrenci@kampus.test")
        vm.onPasswordChange("123456")
        vm.onContinue()

        assertEquals(uiText(R.string.error_email_already_registered), vm.uiState.value.message)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `hesap zaten aciksa 2 numarali adimdan baslanir ve geri gidilemez`() {
        auth.state.value = testUser
        val vm = viewModel(startStep = 2)

        assertEquals(RegisterStep.PERSONAL_INFO, vm.uiState.value.step)
        assertFalse(vm.uiState.value.canGoBack)
        assertFalse(vm.onBack())
        assertTrue(vm.uiState.value.isSignedIn)
        assertEquals(testUser.email, vm.uiState.value.email)
    }

    @Test
    fun `profil adimlari sirayla ilerler ve ozette gercek profil kaydedilir`() {
        auth.state.value = testUser
        val vm = viewModel(startStep = 2)

        vm.fillPersonalStep()
        assertEquals("test_ogrenci", vm.uiState.value.username)
        vm.onContinue()
        assertEquals(RegisterStep.UNIVERSITY, vm.uiState.value.step)
        assertTrue(vm.uiState.value.universities is Loadable.Success)

        assertFalse(vm.uiState.value.canContinue)
        vm.onUniversitySelected(FakeUniversityRepository.defaultUniversities.first())
        vm.onContinue()
        assertEquals(RegisterStep.DEPARTMENT, vm.uiState.value.step)

        vm.onDepartmentChange(" Bilgisayar Mühendisliği ")
        vm.onContinue()
        assertEquals(RegisterStep.SUMMARY, vm.uiState.value.step)

        vm.onContinue()
        assertEquals(RegisterStep.STUDENT_DOCUMENT, vm.uiState.value.step)
        assertEquals(listOf(listOf("  Test Öğrenci ", "test_ogrenci", "uni-1", " Bilgisayar Mühendisliği ")), profiles.updates)
    }

    @Test
    fun `geri tusu bir onceki adima doner`() {
        auth.state.value = testUser
        val vm = viewModel(startStep = 2)
        vm.fillPersonalStep()
        vm.onContinue()
        assertEquals(RegisterStep.UNIVERSITY, vm.uiState.value.step)

        assertTrue(vm.onBack())
        assertEquals(RegisterStep.PERSONAL_INFO, vm.uiState.value.step)
        assertFalse(vm.onBack())
    }

    @Test
    fun `kullanici adi alinmissa kisisel bilgiler adimina donulur`() {
        auth.state.value = testUser
        profiles.updateBlock = { _, _, _, _ -> throw AppError.UsernameTaken() }
        val vm = viewModel(startStep = 2)
        vm.fillPersonalStep()
        vm.onContinue()
        vm.onUniversitySelected(FakeUniversityRepository.defaultUniversities.first())
        vm.onContinue()
        vm.onDepartmentChange("Bilgisayar Mühendisliği")
        vm.onContinue()
        vm.onContinue() // özet -> kaydet

        assertEquals(RegisterStep.PERSONAL_INFO, vm.uiState.value.step)
        assertEquals(uiText(R.string.error_username_taken), vm.uiState.value.message)
    }

    @Test
    fun `gecersiz kullanici adi hata gosterir`() {
        val vm = viewModel(startStep = 2)
        vm.onUsernameChange("Ab")
        assertTrue(vm.uiState.value.showUsernameError)
        vm.onUsernameChange("ab çd")
        assertTrue(vm.uiState.value.showUsernameError)
        vm.onUsernameChange("ali_veli1")
        assertFalse(vm.uiState.value.showUsernameError)
    }

    @Test
    fun `universite listesi hata verirse tekrar denenebilir`() {
        var attempts = 0
        universities.block = {
            attempts++
            if (attempts == 1) throw AppError.Network()
            FakeUniversityRepository.defaultUniversities
        }
        auth.state.value = testUser
        val vm = viewModel(startStep = 2)
        vm.fillPersonalStep()
        vm.onContinue()
        assertTrue(vm.uiState.value.universities is Loadable.Failure)

        vm.loadUniversities()
        assertTrue(vm.uiState.value.universities is Loadable.Success)
    }

    @Test
    fun `universite aramasi ada sehire ve kisaltmaya gore filtreler`() {
        auth.state.value = testUser
        val vm = viewModel(startStep = 2)
        vm.fillPersonalStep()
        vm.onContinue()

        assertEquals(2, vm.uiState.value.filteredUniversities.size)
        vm.onUniversityQueryChange("istanbul")
        assertEquals(listOf("uni-2"), vm.uiState.value.filteredUniversities.map { it.id })
        vm.onUniversityQueryChange("tü")
        assertEquals(2, vm.uiState.value.filteredUniversities.size)
        vm.onUniversityQueryChange("yok böyle bir yer")
        assertTrue(vm.uiState.value.filteredUniversities.isEmpty())
    }

    @Test
    fun `belge adiminda PDF secilip gonderilince basvuru yuklenir ve kok yenilenir`() = runTest {
        auth.state.value = testUser
        val vm = viewModel(startStep = 6)
        val file = tempFolder.newFile("ogrenci_belgesi.pdf").apply { writeBytes("%PDF-1.4 test".toByteArray()) }

        val refreshRequested = mutableListOf<Unit>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { refresher.requests.first().also { refreshRequested += it } }

        vm.onDocumentPicked(Uri.fromFile(file))
        assertTrue(vm.uiState.value.documentName.orEmpty().endsWith("ogrenci_belgesi.pdf"))
        assertTrue(vm.uiState.value.canContinue)

        vm.onContinue()
        job.join()

        assertEquals(1, verification.submitted.size)
        assertEquals("%PDF-1.4 test", String(verification.submitted.single()))
        assertEquals(1, refreshRequested.size)
    }

    @Test
    fun `PDF olmayan dosya reddedilir ve gonderilmez`() {
        auth.state.value = testUser
        val vm = viewModel(startStep = 6)
        val file = tempFolder.newFile("foto.pdf").apply { writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6)) }

        vm.onDocumentPicked(Uri.fromFile(file))
        vm.onContinue()

        assertTrue(verification.submitted.isEmpty())
        assertEquals(uiText(R.string.error_file_not_pdf), vm.uiState.value.message)
        assertNotNull(vm.uiState.value.message)
    }

    @Test
    fun `belge kaldirilinca gonderim kapanir`() {
        val vm = viewModel(startStep = 6)
        val file: File = tempFolder.newFile("a.pdf").apply { writeBytes("%PDF-1.7".toByteArray()) }
        vm.onDocumentPicked(Uri.fromFile(file))
        assertTrue(vm.uiState.value.canContinue)

        vm.clearDocument()
        assertFalse(vm.uiState.value.canContinue)
        assertNull(vm.uiState.value.documentName)
    }
}
