package com.kampusagi.android.feature.verification

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kampusagi.android.R
import com.kampusagi.android.core.file.PickedDocumentReader
import com.kampusagi.android.core.session.SessionRefresher
import com.kampusagi.android.core.ui.UiText
import com.kampusagi.android.core.ui.toUiText
import com.kampusagi.android.core.ui.uiText
import com.kampusagi.android.domain.auth.AuthRepository
import com.kampusagi.android.domain.common.AppError
import com.kampusagi.android.domain.verification.StudentVerificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VerificationUiState(
    val isRefreshing: Boolean = false,
    val isResubmitting: Boolean = false,
    val message: UiText? = null,
)

/**
 * Bekleyen/Reddedilen ekranlarının ortak eylemleri. Durumun kendisi RootViewModel'de canlı
 * izlenir; burada yalnızca kullanıcı eylemleri (yenile, yeniden yükle, çıkış) yönetilir.
 */
@HiltViewModel
class VerificationStatusViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val verificationRepository: StudentVerificationRepository,
    private val documentReader: PickedDocumentReader,
    private val sessionRefresher: SessionRefresher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VerificationUiState())
    val uiState: StateFlow<VerificationUiState> = _uiState.asStateFlow()

    /** Oturum belirtecini zorla yeniler (JWT'deki rol/durum güncellenir), sonra durumu baştan çözer. */
    fun refresh() {
        if (_uiState.value.isRefreshing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                authRepository.refreshSession()
                verificationRepository.fetchStatus()
                sessionRefresher.requestRefresh()
            } catch (e: AppError) {
                _uiState.update { it.copy(message = e.toUiText()) }
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    /** Reddedilen başvuru için seçilen yeni PDF'i doğrulayıp gönderir. */
    fun resubmitDocument(uri: Uri) {
        if (_uiState.value.isResubmitting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isResubmitting = true) }
            try {
                val document = documentReader.read(uri)
                verificationRepository.submitDocument(document.bytes)
                // Başvuru alındı: kök yönlendirme "Başvurunuz İnceleniyor" ekranına geçer.
                sessionRefresher.requestRefresh()
            } catch (e: AppError) {
                val message = if (e is AppError.Unknown) uiText(R.string.register_document_submit_error) else e.toUiText()
                _uiState.update { it.copy(message = message) }
            } finally {
                _uiState.update { it.copy(isResubmitting = false) }
            }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    fun signOut() {
        viewModelScope.launch {
            try {
                authRepository.signOut()
            } catch (_: AppError) {
                // Yerel oturum yine de kapanır; yönlendirmeyi RootViewModel yapar.
            }
        }
    }
}
