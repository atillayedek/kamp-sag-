package com.kampusagi.android.feature.auth.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kampusagi.android.R
import com.kampusagi.android.core.ui.UiText
import com.kampusagi.android.core.ui.toUiText
import com.kampusagi.android.core.ui.uiText
import com.kampusagi.android.domain.auth.AuthRepository
import com.kampusagi.android.domain.common.AppError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    /** Tek seferlik kullanıcı mesajı (hata veya bilgi); ekran gösterince `consumeMessage` çağrılır. */
    val message: UiText? = null,
) {
    val canSubmit: Boolean get() = email.isNotBlank() && password.isNotBlank() && !isLoading
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value) }

    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value) }

    fun signIn() {
        val state = _uiState.value
        if (!state.canSubmit) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                authRepository.signIn(state.email.trim(), state.password)
                // Başarılı girişten sonra yönlendirme RootViewModel'in oturum akışından OTOMATİK yapılır.
            } catch (e: AppError) {
                _uiState.update { it.copy(message = e.toUiText()) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onGoogleIdToken(idToken: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                authRepository.signInWithGoogleIdToken(idToken)
            } catch (e: AppError) {
                _uiState.update { it.copy(message = e.toUiText()) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onGoogleFailed() = _uiState.update { it.copy(message = uiText(R.string.login_google_error)) }

    fun onGoogleNotConfigured() = _uiState.update { it.copy(message = uiText(R.string.login_google_not_configured)) }

    fun sendPasswordReset() {
        val email = _uiState.value.email.trim()
        if (email.isBlank()) {
            _uiState.update { it.copy(message = uiText(R.string.login_password_reset_missing_email)) }
            return
        }
        viewModelScope.launch {
            try {
                authRepository.sendPasswordReset(email)
                _uiState.update { it.copy(message = uiText(R.string.login_password_reset_sent)) }
            } catch (e: AppError) {
                _uiState.update { it.copy(message = e.toUiText()) }
            }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }
}
