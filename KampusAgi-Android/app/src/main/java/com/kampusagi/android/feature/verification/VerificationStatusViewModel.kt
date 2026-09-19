package com.kampusagi.android.feature.verification

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kampusagi.android.R
import com.kampusagi.android.domain.auth.AuthRepository
import com.kampusagi.android.domain.verification.StudentVerificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VerificationStatusUiState(
    val isRefreshing: Boolean = false,
    val isResubmitting: Boolean = false,
    val rejectionReason: String? = null,
    @StringRes val errorMessageRes: Int? = null,
)

/** PendingReviewScreen ve RejectedScreen tarafından paylaşılan ViewModel. */
@HiltViewModel
class VerificationStatusViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: StudentVerificationRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VerificationStatusUiState())
    val uiState: StateFlow<VerificationStatusUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val status = runCatching { repository.fetchStatus() }.getOrNull()
            _uiState.update { it.copy(isRefreshing = false, rejectionReason = status?.rejectionReason) }
        }
    }

    /** RejectedScreen'de yeni bir PDF seçildiğinde çağrılır. */
    fun resubmitDocument(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isResubmitting = true, errorMessageRes = null) }
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Dosya okunamadı.")
                repository.submitDocument(bytes)
                refresh()
                // Başarılı gönderim sonrası RootViewModel PendingReview'e OTOMATİK
                // yönlendirir (observeAuthState + fetchStatus).
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessageRes = R.string.register_document_submit_error) }
            } finally {
                _uiState.update { it.copy(isResubmitting = false) }
            }
        }
    }

    fun consumeError() {
        _uiState.update { it.copy(errorMessageRes = null) }
    }

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
        // RootViewModel oturum kapanışını observeAuthState üzerinden OTOMATİK
        // yakalayıp Welcome'a yönlendirir — burada elle navigasyon yapılmaz.
    }
}
