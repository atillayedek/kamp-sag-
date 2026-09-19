package com.kampusagi.android.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kampusagi.android.domain.auth.AuthRepository
import com.kampusagi.android.domain.verification.StudentVerificationRepository
import com.kampusagi.android.domain.verification.VerificationState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Kullanıcı durumu — kullanıcının tasarım mesajındaki sealed interface birebir. */
sealed interface UserSessionState {
    data object Loading : UserSessionState
    data object Unauthenticated : UserSessionState
    data object PendingReview : UserSessionState
    data object Approved : UserSessionState
    data class Rejected(val reason: String?) : UserSessionState
}

/**
 * Kök yönlendirme mantığı: oturum durumu + öğrenci doğrulama durumuna göre
 * doğru başlangıç ekranını belirler (bkz. KampusAgiNavHost — popUpTo(0) ile
 * geri yığını temizlenir).
 */
@HiltViewModel
class RootViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val verificationRepository: StudentVerificationRepository,
) : ViewModel() {

    private val _sessionState = MutableStateFlow<UserSessionState>(UserSessionState.Loading)
    val sessionState: StateFlow<UserSessionState> = _sessionState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.observeAuthState().collect { user ->
                if (user == null) {
                    _sessionState.value = UserSessionState.Unauthenticated
                    return@collect
                }
                _sessionState.value = try {
                    val status = verificationRepository.fetchStatus()
                    when (status.state) {
                        VerificationState.PENDING -> UserSessionState.PendingReview
                        VerificationState.APPROVED -> UserSessionState.Approved
                        VerificationState.REJECTED -> UserSessionState.Rejected(status.rejectionReason)
                    }
                } catch (e: Exception) {
                    // Durum okunamazsa güvenli/kısıtlı taraf seçilir: Pending.
                    UserSessionState.PendingReview
                }
            }
        }
    }
}
