package com.kampusagi.android.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kampusagi.android.core.session.SessionRefresher
import com.kampusagi.android.domain.auth.AuthRepository
import com.kampusagi.android.domain.common.AppError
import com.kampusagi.android.domain.profile.ProfileRepository
import com.kampusagi.android.domain.verification.StudentVerificationRepository
import com.kampusagi.android.domain.verification.VerificationState
import com.kampusagi.android.domain.verification.VerificationStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Kök yönlendirmenin tek doğruluk kaynağı: oturum + profil + öğrenci doğrulama durumu. */
sealed interface UserSessionState {
    /** İlk durum çözülene kadar (splash). */
    data object Loading : UserSessionState
    data object Unauthenticated : UserSessionState

    /** Hesap açık ama kayıt sihirbazı bitmemiş; `startStep` 2 (profil) veya 6 (belge). */
    data class Onboarding(val startStep: Int) : UserSessionState

    data object PendingReview : UserSessionState
    data class Rejected(val reason: String?) : UserSessionState
    data object Approved : UserSessionState

    /** Durum okunamadı (ağ vb.); güvenli tarafa DÜŞÜLMEZ — kullanıcıya hata + "Tekrar Dene" gösterilir. */
    data class LoadFailed(val error: AppError) : UserSessionState
}

const val ONBOARDING_PROFILE_STEP = 2
const val ONBOARDING_DOCUMENT_STEP = 6

internal fun VerificationStatus.toSessionState(): UserSessionState = when {
    !hasSubmittedDocument -> UserSessionState.Onboarding(ONBOARDING_DOCUMENT_STEP)
    state == VerificationState.PENDING -> UserSessionState.PendingReview
    state == VerificationState.APPROVED -> UserSessionState.Approved
    else -> UserSessionState.Rejected(rejectionReason)
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RootViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val verificationRepository: StudentVerificationRepository,
    private val sessionRefresher: SessionRefresher,
) : ViewModel() {

    private val _sessionState = MutableStateFlow<UserSessionState>(UserSessionState.Loading)
    val sessionState: StateFlow<UserSessionState> = _sessionState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.observeAuthState()
                .flatMapLatest { user ->
                    if (user == null) {
                        flowOf(UserSessionState.Unauthenticated)
                    } else {
                        // Her yenileme isteğinde (ve ilk seferde) durum baştan çözülür.
                        sessionRefresher.requests.onStart { emit(Unit) }.flatMapLatest { signedInState() }
                    }
                }
                .collect { _sessionState.value = it }
        }
    }

    fun retry() = sessionRefresher.requestRefresh()

    fun signOut() {
        viewModelScope.launch {
            try {
                authRepository.signOut()
            } catch (_: AppError) {
                // Oturum sunucuda zaten kapanmış olabilir; yerel oturum yine de sonlandırılır.
            }
        }
    }

    private fun signedInState(): Flow<UserSessionState> = flow {
        val profile = profileRepository.getMyProfile()
        if (!profile.isComplete) {
            emit(UserSessionState.Onboarding(ONBOARDING_PROFILE_STEP))
        } else {
            emitAll(verificationRepository.observeStatus().map { it.toSessionState() })
        }
    }.catch { error ->
        if (error is CancellationException) throw error
        emit(UserSessionState.LoadFailed(error as? AppError ?: AppError.Unknown(error)))
    }
}
