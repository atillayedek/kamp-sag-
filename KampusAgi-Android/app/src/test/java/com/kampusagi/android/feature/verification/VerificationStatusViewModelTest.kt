package com.kampusagi.android.feature.verification

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kampusagi.android.R
import com.kampusagi.android.core.file.PickedDocumentReader
import com.kampusagi.android.core.session.SessionRefresher
import com.kampusagi.android.core.ui.uiText
import com.kampusagi.android.domain.common.AppError
import com.kampusagi.android.domain.verification.VerificationState
import com.kampusagi.android.domain.verification.VerificationStatus
import com.kampusagi.android.testutil.FakeAuthRepository
import com.kampusagi.android.testutil.FakeVerificationRepository
import com.kampusagi.android.testutil.MainDispatcherRule
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class VerificationStatusViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val auth = FakeAuthRepository()
    private val verification = FakeVerificationRepository()
    private val refresher = SessionRefresher()

    private fun viewModel() = VerificationStatusViewModel(
        authRepository = auth,
        verificationRepository = verification,
        documentReader = PickedDocumentReader(ApplicationProvider.getApplicationContext<Context>(), UnconfinedTestDispatcher()),
        sessionRefresher = refresher,
    )

    @Test
    fun `yenile once belirteci yeniler sonra durumu okur ve kok yenilemesini ister`() = runTest {
        verification.fetchBlock = { VerificationStatus(VerificationState.PENDING, null, true) }
        val requested = mutableListOf<Unit>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { refresher.requests.first().also { requested += it } }

        val vm = viewModel()
        vm.refresh()
        job.join()

        assertEquals(1, auth.refreshCalls)
        assertEquals(1, requested.size)
        assertFalse(vm.uiState.value.isRefreshing)
        assertEquals(null, vm.uiState.value.message)
    }

    @Test
    fun `yenileme ag hatasinda mesaj gosterir ve yukleniyor kapanir`() {
        auth.refreshBlock = { throw AppError.Network() }
        val vm = viewModel()
        vm.refresh()

        assertEquals(uiText(R.string.error_network), vm.uiState.value.message)
        assertFalse(vm.uiState.value.isRefreshing)
    }

    @Test
    fun `reddedilen basvuruda yeni PDF yuklenir`() = runTest {
        val file = tempFolder.newFile("yeni.pdf").apply { writeBytes("%PDF-1.5 yeni".toByteArray()) }
        val requested = mutableListOf<Unit>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { refresher.requests.first().also { requested += it } }

        val vm = viewModel()
        vm.resubmitDocument(Uri.fromFile(file))
        job.join()

        assertEquals("%PDF-1.5 yeni", String(verification.submitted.single()))
        assertEquals(1, requested.size)
        assertFalse(vm.uiState.value.isResubmitting)
    }

    @Test
    fun `sunucu hatasi mesaj olarak gosterilir`() {
        val file = tempFolder.newFile("yeni.pdf").apply { writeBytes("%PDF-1.5".toByteArray()) }
        verification.submitBlock = { throw AppError.Server("Zaten incelenmekte olan bir başvurunuz var.") }
        val vm = viewModel()
        vm.resubmitDocument(Uri.fromFile(file))

        assertEquals(com.kampusagi.android.core.ui.UiText.Plain("Zaten incelenmekte olan bir başvurunuz var."), vm.uiState.value.message)
    }

    @Test
    fun `cikis oturumu kapatir`() {
        val vm = viewModel()
        vm.signOut()
        assertEquals(1, auth.signOutCalls)
        assertTrue(auth.currentUser == null)
    }
}
