package com.kampusagi.android.feature.auth.register

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kampusagi.android.R
import com.kampusagi.android.core.file.PickedDocumentReader
import com.kampusagi.android.core.session.SessionRefresher
import com.kampusagi.android.core.ui.Loadable
import com.kampusagi.android.core.ui.UiText
import com.kampusagi.android.core.ui.toUiText
import com.kampusagi.android.core.ui.uiText
import com.kampusagi.android.domain.auth.AuthRepository
import com.kampusagi.android.domain.auth.SignUpResult
import com.kampusagi.android.domain.common.AppError
import com.kampusagi.android.domain.profile.ProfileRepository
import com.kampusagi.android.domain.university.University
import com.kampusagi.android.domain.university.UniversityRepository
import com.kampusagi.android.domain.verification.StudentVerificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Kayıt sihirbazı adımları. 1: hesap (e-posta/şifre) · 2: kişisel bilgiler · 3: üniversite ·
 * 4: bölüm · 5: özet + profili kaydet · 6: öğrenci belgesi. Adım 2-5, `profiles` tablosundaki gerçek
 * alanlara (full_name, username, university_id, department) karşılık gelir.
 */
enum class RegisterStep(val number: Int) {
    ACCOUNT_INFO(1),
    PERSONAL_INFO(2),
    UNIVERSITY(3),
    DEPARTMENT(4),
    SUMMARY(5),
    STUDENT_DOCUMENT(6),
    ;

    companion object {
        const val TOTAL_STEPS = 6
        fun fromNumber(number: Int): RegisterStep = entries.firstOrNull { it.number == number } ?: ACCOUNT_INFO
    }
}

/** Saf (Android'siz) doğrulama kuralları — birim testlenir. */
object RegisterValidators {
    private val emailRegex = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    private val usernameRegex = Regex("^[a-z0-9_]{3,30}$")

    const val MIN_PASSWORD_LENGTH = 6

    fun isValidEmail(value: String) = emailRegex.matches(value.trim())
    fun isValidPassword(value: String) = value.length >= MIN_PASSWORD_LENGTH
    fun isValidUsername(value: String) = usernameRegex.matches(value.trim())
    fun isValidFullName(value: String) = value.trim().length >= 2
    fun isValidDepartment(value: String) = value.trim().length >= 2
}

data class RegisterUiState(
    val step: RegisterStep = RegisterStep.ACCOUNT_INFO,
    val firstStep: RegisterStep = RegisterStep.ACCOUNT_INFO,
    val email: String = "",
    val password: String = "",
    val awaitingEmailConfirmation: Boolean = false,
    val fullName: String = "",
    val username: String = "",
    val universities: Loadable<List<University>> = Loadable.Loading,
    val universityQuery: String = "",
    val selectedUniversity: University? = null,
    val department: String = "",
    val documentName: String? = null,
    val isLoading: Boolean = false,
    val message: UiText? = null,
    val isHelpVisible: Boolean = false,
) {
    val canGoBack: Boolean get() = step.number > firstStep.number && !awaitingEmailConfirmation

    /** Hesap zaten açıksa (adım ≥ 2 ile devam) çıkış yapma seçeneği sunulur. */
    val isSignedIn: Boolean get() = firstStep.number > RegisterStep.ACCOUNT_INFO.number || step.number > RegisterStep.ACCOUNT_INFO.number

    val showUsernameError: Boolean get() = username.isNotEmpty() && !RegisterValidators.isValidUsername(username)

    val canContinue: Boolean
        get() = !isLoading && when (step) {
            RegisterStep.ACCOUNT_INFO ->
                RegisterValidators.isValidEmail(email) && RegisterValidators.isValidPassword(password)
            RegisterStep.PERSONAL_INFO ->
                RegisterValidators.isValidFullName(fullName) && RegisterValidators.isValidUsername(username)
            RegisterStep.UNIVERSITY -> selectedUniversity != null
            RegisterStep.DEPARTMENT -> RegisterValidators.isValidDepartment(department)
            RegisterStep.SUMMARY -> true
            RegisterStep.STUDENT_DOCUMENT -> documentName != null
        }

    val filteredUniversities: List<University>
        get() {
            val all = (universities as? Loadable.Success)?.value ?: return emptyList()
            val query = universityQuery.trim()
            if (query.isEmpty()) return all
            return all.filter {
                it.name.contains(query, ignoreCase = true) ||
                    it.shortName.contains(query, ignoreCase = true) ||
                    it.city.contains(query, ignoreCase = true)
            }
        }
}

@HiltViewModel
class RegisterViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val universityRepository: UniversityRepository,
    private val verificationRepository: StudentVerificationRepository,
    private val documentReader: PickedDocumentReader,
    private val sessionRefresher: SessionRefresher,
) : ViewModel() {

    private val startStep = RegisterStep.fromNumber(savedStateHandle.get<Int>("startStep") ?: 1)

    private val _uiState = MutableStateFlow(
        RegisterUiState(
            step = startStep,
            firstStep = startStep,
            email = authRepository.currentUser?.email.orEmpty(),
        ),
    )
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    private var documentUri: Uri? = null

    init {
        if (startStep.number >= RegisterStep.UNIVERSITY.number) loadUniversities()
    }

    // --- Alan değişiklikleri ---------------------------------------------------------------

    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value) }
    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value) }
    fun onFullNameChange(value: String) = _uiState.update { it.copy(fullName = value) }
    fun onUsernameChange(value: String) = _uiState.update { it.copy(username = value.lowercase()) }
    fun onUniversityQueryChange(value: String) = _uiState.update { it.copy(universityQuery = value) }
    fun onUniversitySelected(university: University) = _uiState.update { it.copy(selectedUniversity = university) }
    fun onDepartmentChange(value: String) = _uiState.update { it.copy(department = value) }
    fun setHelpVisible(visible: Boolean) = _uiState.update { it.copy(isHelpVisible = visible) }
    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    // --- Gezinme ---------------------------------------------------------------------------

    fun onContinue() {
        val state = _uiState.value
        if (!state.canContinue) return
        when (state.step) {
            RegisterStep.ACCOUNT_INFO -> createAccount()
            RegisterStep.SUMMARY -> saveProfile()
            RegisterStep.STUDENT_DOCUMENT -> submitDocument()
            else -> goTo(RegisterStep.fromNumber(state.step.number + 1))
        }
    }

    /** @return `true` bir önceki adıma dönüldüyse; `false` zaten ilk adımdaysa. */
    fun onBack(): Boolean {
        val state = _uiState.value
        if (!state.canGoBack) return false
        goTo(RegisterStep.fromNumber(state.step.number - 1))
        return true
    }

    private fun goTo(step: RegisterStep) {
        _uiState.update { it.copy(step = step) }
        if (step == RegisterStep.UNIVERSITY) loadUniversities()
    }

    // --- Adım 1: hesap ---------------------------------------------------------------------

    private fun createAccount() {
        val state = _uiState.value
        launchLoading {
            when (val result = authRepository.signUp(state.email.trim(), state.password)) {
                // Oturum açıldı: yönlendirmeyi RootViewModel (Onboarding) üstlenir.
                is SignUpResult.SignedIn -> Unit
                is SignUpResult.ConfirmationRequired ->
                    _uiState.update { it.copy(awaitingEmailConfirmation = true, email = result.email) }
            }
        }
    }

    fun confirmEmailAndContinue() {
        val state = _uiState.value
        launchLoading {
            // E-posta doğrulandıysa giriş başarılı olur ve yönlendirmeyi RootViewModel yapar.
            authRepository.signIn(state.email.trim(), state.password)
        }
    }

    fun resendConfirmation() {
        val email = _uiState.value.email.trim()
        launchLoading {
            authRepository.resendSignUpConfirmation(email)
            _uiState.update { it.copy(message = uiText(R.string.register_confirm_resent)) }
        }
    }

    fun editAccountInfo() = _uiState.update { it.copy(awaitingEmailConfirmation = false) }

    // --- Adım 3: üniversite ----------------------------------------------------------------

    fun loadUniversities() {
        if (_uiState.value.universities is Loadable.Success) return
        _uiState.update { it.copy(universities = Loadable.Loading) }
        viewModelScope.launch {
            val result = try {
                Loadable.Success(universityRepository.getActiveUniversities())
            } catch (e: AppError) {
                Loadable.Failure(e)
            }
            _uiState.update { it.copy(universities = result) }
        }
    }

    // --- Adım 5: profili kaydet ------------------------------------------------------------

    private fun saveProfile() {
        val state = _uiState.value
        val university = state.selectedUniversity ?: return
        launchLoading(onError = { error ->
            if (error is AppError.UsernameTaken) goTo(RegisterStep.PERSONAL_INFO)
        }) {
            profileRepository.updateMyProfile(
                fullName = state.fullName,
                username = state.username,
                universityId = university.id,
                department = state.department,
            )
            goTo(RegisterStep.STUDENT_DOCUMENT)
        }
    }

    // --- Adım 6: belge ---------------------------------------------------------------------

    fun onDocumentPicked(uri: Uri) {
        viewModelScope.launch {
            try {
                val info = documentReader.describe(uri)
                if (info.sizeBytes != null && info.sizeBytes > PickedDocumentReader.MAX_DOCUMENT_BYTES) {
                    throw AppError.FileTooLarge(PickedDocumentReader.MAX_DOCUMENT_MEGABYTES)
                }
                documentUri = uri
                _uiState.update { it.copy(documentName = info.displayName ?: uri.lastPathSegment.orEmpty().ifEmpty { "PDF" }) }
            } catch (e: AppError) {
                _uiState.update { it.copy(message = e.toUiText()) }
            }
        }
    }

    fun clearDocument() {
        documentUri = null
        _uiState.update { it.copy(documentName = null) }
    }

    private fun submitDocument() {
        val uri = documentUri ?: return
        launchLoading(fallbackMessage = uiText(R.string.register_document_submit_error)) {
            val document = documentReader.read(uri)
            verificationRepository.submitDocument(document.bytes)
            // Başvuru alındı: kök yönlendirme durumu yeniden çözer ve "Başvurunuz İnceleniyor" ekranına geçer.
            sessionRefresher.requestRefresh()
        }
    }

    fun signOut() {
        viewModelScope.launch {
            try {
                authRepository.signOut()
            } catch (_: AppError) {
                // Yerel oturum yine de kapanır; yönlendirmeyi RootViewModel yapar.
            }
        }
    }

    /** Yükleniyor durumunu yönetir; [AppError] kullanıcı mesajına çevrilir, iptal (CancellationException) yayılır. */
    private fun launchLoading(
        fallbackMessage: UiText? = null,
        onError: (AppError) -> Unit = {},
        block: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                block()
            } catch (e: AppError) {
                onError(e)
                val message = if (fallbackMessage != null && e is AppError.Unknown) fallbackMessage else e.toUiText()
                _uiState.update { it.copy(message = message) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}
