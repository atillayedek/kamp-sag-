package com.kampusagi.android.feature.auth.login

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kampusagi.android.R
import com.kampusagi.android.domain.auth.AuthRepository
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
    val isPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    @StringRes val errorMessageRes: Int? = null,
) {
    val canSubmit: Boolean get() = email.isNotBlank() && password.isNotBlank() && !isLoading
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value, errorMessageRes = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, errorMessageRes = null) }
    }

    fun onTogglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun signIn() {
        val state = _uiState.value
        if (!state.canSubmit) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessageRes = null) }
            try {
                authRepository.signIn(state.email, state.password)
                // Başarılı girişten sonra navigasyon RootViewModel'in observeAuthState
                // akışı üzerinden OTOMATİK tetiklenir — burada elle yapılmaz.
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessageRes = R.string.login_error_generic) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun signInWithGoogleIdToken(idToken: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessageRes = null) }
            try {
                authRepository.signInWithGoogleIdToken(idToken)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessageRes = R.string.login_google_error) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onGoogleSignInFailed() {
        _uiState.update { it.copy(errorMessageRes = R.string.login_google_error, isLoading = false) }
    }

    fun sendPasswordReset() {
        val email = _uiState.value.email
        if (email.isBlank()) {
            _uiState.update { it.copy(errorMessageRes = R.string.login_password_reset_missing_email) }
            return
        }
        viewModelScope.launch {
            try {
                authRepository.sendPasswordReset(email)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessageRes = R.string.login_password_reset_failed) }
            }
        }
    }

    fun consumeError() {
        _uiState.update { it.copy(errorMessageRes = null) }
    }
}
