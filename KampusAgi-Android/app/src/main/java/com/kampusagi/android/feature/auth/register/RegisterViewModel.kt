package com.kampusagi.android.feature.auth.register

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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

/**
 * Mockup'ta yalnızca "6/6 — Öğrenci Belgesi" somut verildi. ACCOUNT_INFO,
 * ilerleyen adımlar için bir Supabase Auth kullanıcısı (uid) gerektiği için
 * ZORUNLU bir çıkarımdır. PERSONAL_INFO/UNIVERSITY_SELECTION/DEPARTMENT_INFO/
 * ADDITIONAL_INFO içerikleri HİÇBİR kaynakta verilmedi — uydurulmadı, açıkça
 * "netleşmedi" placeholder'ı gösterilir (bkz. RegisterScreen.kt).
 */
enum class RegisterStep(val stepNumber: Int) {
    ACCOUNT_INFO(1),
    PERSONAL_INFO(2),
    UNIVERSITY_SELECTION(3),
    DEPARTMENT_INFO(4),
    ADDITIONAL_INFO(5),
    STUDENT_DOCUMENT(6),
    ;

    companion object {
        const val TOTAL_STEPS = 6
    }
}

data class RegisterUiState(
    val currentStep: RegisterStep = RegisterStep.ACCOUNT_INFO,
    val email: String = "",
    val password: String = "",
    val selectedDocumentUri: Uri? = null,
    val selectedDocumentName: String? = null,
    val isLoading: Boolean = false,
    @StringRes val errorMessageRes: Int? = null,
    val isHelpSheetVisible: Boolean = false,
) {
    val canSubmitAccountInfo: Boolean get() = email.isNotBlank() && password.length >= 6 && !isLoading
    val canSubmitDocument: Boolean get() = selectedDocumentUri != null && !isLoading
}

@HiltViewModel
class RegisterViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val verificationRepository: StudentVerificationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value, errorMessageRes = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, errorMessageRes = null) }
    }

    fun createAccountAndAdvance() {
        val state = _uiState.value
        if (!state.canSubmitAccountInfo) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessageRes = null) }
            try {
                authRepository.signUp(state.email, state.password)
                advance()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessageRes = R.string.register_account_error) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun advance() {
        val next = RegisterStep.entries.getOrNull(_uiState.value.currentStep.ordinal + 1) ?: return
        _uiState.update { it.copy(currentStep = next) }
    }

    /** @return `true` eğer bir önceki adıma dönüldüyse; `false` zaten ilk adımdaysa. */
    fun goBack(): Boolean {
        val previous = RegisterStep.entries.getOrNull(_uiState.value.currentStep.ordinal - 1) ?: return false
        _uiState.update { it.copy(currentStep = previous) }
        return true
    }

    fun onDocumentSelected(uri: Uri) {
        _uiState.update {
            it.copy(selectedDocumentUri = uri, selectedDocumentName = queryFileName(uri), errorMessageRes = null)
        }
    }

    fun clearSelectedDocument() {
        _uiState.update { it.copy(selectedDocumentUri = null, selectedDocumentName = null) }
    }

    fun toggleHelpSheet(visible: Boolean) {
        _uiState.update { it.copy(isHelpSheetVisible = visible) }
    }

    fun submitDocument() {
        val uri = _uiState.value.selectedDocumentUri ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessageRes = null) }
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Dosya okunamadı.")
                verificationRepository.submitDocument(bytes)
                // Başarılı gönderim sonrası RootViewModel PendingReview'e OTOMATİK
                // yönlendirir (observeAuthState + fetchStatus) — burada elle yapılmaz.
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessageRes = R.string.register_document_submit_error) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun consumeError() {
        _uiState.update { it.copy(errorMessageRes = null) }
    }

    private fun queryFileName(uri: Uri): String? =
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
        }
}
