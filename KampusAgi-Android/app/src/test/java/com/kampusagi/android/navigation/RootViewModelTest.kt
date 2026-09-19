package com.kampusagi.android.navigation

import com.kampusagi.android.core.session.SessionRefresher
import com.kampusagi.android.domain.common.AppError
import com.kampusagi.android.domain.verification.VerificationState
import com.kampusagi.android.domain.verification.VerificationStatus
import com.kampusagi.android.testutil.FakeAuthRepository
import com.kampusagi.android.testutil.FakeProfileRepository
import com.kampusagi.android.testutil.FakeVerificationRepository
import com.kampusagi.android.testutil.MainDispatcherRule
import com.kampusagi.android.testutil.testUser
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RootViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val auth = FakeAuthRepository()
    private val profiles = FakeProfileRepository(FakeProfileRepository.completeProfile)
    private val verification = FakeVerificationRepository()
    private val refresher = SessionRefresher()

    private fun viewModel() = RootViewModel(auth, profiles, verification, refresher)

    private fun status(state: VerificationState, reason: String? = null, submitted: Boolean = true) =
        VerificationStatus(state, reason, submitted)

    @Test
    fun `oturum yokken Unauthenticated olur`() {
        val vm = viewModel()
        assertEquals(UserSessionState.Unauthenticated, vm.sessionState.value)
    }

    @Test
    fun `ilk durum cozulene kadar Loading kalir`() {
        auth.state.value = testUser
        // observeStatus henüz hiçbir şey yaymadı.
        val vm = viewModel()
        assertEquals(UserSessionState.Loading, vm.sessionState.value)
    }

    @Test
    fun `profil eksikse 2 numarali adimdan Onboarding baslar`() {
        auth.state.value = testUser
        profiles.getBlock = { FakeProfileRepository.incompleteProfile }
        val vm = viewModel()
        assertEquals(UserSessionState.Onboarding(ONBOARDING_PROFILE_STEP), vm.sessionState.value)
    }

    @Test
    fun `profil tamam ama belge gonderilmemisse 6 numarali adimdan Onboarding baslar`() {
        auth.state.value = testUser
        val vm = viewModel()
        verification.statuses.tryEmit(status(VerificationState.PENDING, submitted = false))
        assertEquals(UserSessionState.Onboarding(ONBOARDING_DOCUMENT_STEP), vm.sessionState.value)
    }

    @Test
    fun `bekleyen basvuru PendingReview olur`() {
        auth.state.value = testUser
        val vm = viewModel()
        verification.statuses.tryEmit(status(VerificationState.PENDING))
        assertEquals(UserSessionState.PendingReview, vm.sessionState.value)
    }

    @Test
    fun `reddedilen basvuru gerekceyle Rejected olur`() {
        auth.state.value = testUser
        val vm = viewModel()
        verification.statuses.tryEmit(status(VerificationState.REJECTED, reason = "Belge okunamıyor"))
        assertEquals(UserSessionState.Rejected("Belge okunamıyor"), vm.sessionState.value)
    }

    @Test
    fun `moderator onaylayinca canli olarak Approved a gecer`() {
        auth.state.value = testUser
        val vm = viewModel()
        verification.statuses.tryEmit(status(VerificationState.PENDING))
        assertEquals(UserSessionState.PendingReview, vm.sessionState.value)

        verification.statuses.tryEmit(status(VerificationState.APPROVED))
        assertEquals(UserSessionState.Approved, vm.sessionState.value)
    }

    @Test
    fun `cikis yapilinca Unauthenticated a doner`() {
        auth.state.value = testUser
        val vm = viewModel()
        verification.statuses.tryEmit(status(VerificationState.APPROVED))
        assertEquals(UserSessionState.Approved, vm.sessionState.value)

        auth.state.value = null
        assertEquals(UserSessionState.Unauthenticated, vm.sessionState.value)
    }

    @Test
    fun `durum okunamazsa guvenli tarafa dusulmez LoadFailed olur ve tekrar denenebilir`() {
        auth.state.value = testUser
        var attempts = 0
        verification.observeBlock = {
            flow {
                attempts++
                if (attempts == 1) throw AppError.Network()
                emit(status(VerificationState.APPROVED))
            }
        }
        val vm = viewModel()
        assertTrue(vm.sessionState.value is UserSessionState.LoadFailed)

        vm.retry()
        assertEquals(UserSessionState.Approved, vm.sessionState.value)
    }

    @Test
    fun `profil okunamazsa LoadFailed olur`() {
        auth.state.value = testUser
        profiles.getBlock = { throw AppError.Network() }
        val vm = viewModel()
        val state = vm.sessionState.value
        assertTrue(state is UserSessionState.LoadFailed)
        assertTrue((state as UserSessionState.LoadFailed).error is AppError.Network)
    }

    @Test
    fun `yonlendirme rotalari durumlarla eslesir`() {
        assertEquals(null, routeFor(UserSessionState.Loading))
        assertEquals(null, routeFor(UserSessionState.LoadFailed(AppError.Network())))
        assertEquals(KampusAgiRoute.Welcome, routeFor(UserSessionState.Unauthenticated))
        assertEquals(KampusAgiRoute.Register(2), routeFor(UserSessionState.Onboarding(2)))
        assertEquals(KampusAgiRoute.PendingReview, routeFor(UserSessionState.PendingReview))
        assertEquals(KampusAgiRoute.Rejected("x"), routeFor(UserSessionState.Rejected("x")))
        assertEquals(KampusAgiRoute.Approved, routeFor(UserSessionState.Approved))
    }
}
