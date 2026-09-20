package com.kampusagi.android.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kampusagi.android.R
import com.kampusagi.android.core.ui.Loadable
import com.kampusagi.android.core.ui.UiText
import com.kampusagi.android.core.ui.toUiText
import com.kampusagi.android.core.ui.uiText
import com.kampusagi.android.domain.auth.AuthRepository
import com.kampusagi.android.domain.common.AppError
import com.kampusagi.android.domain.profile.Profile
import com.kampusagi.android.domain.profile.ProfileRepository
import com.kampusagi.android.feature.auth.register.RegisterValidators
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountInfoUiState(
    val profile: Loadable<Profile> = Loadable.Loading,
    val email: String = "",
    val fullName: String = "",
    val username: String = "",
    val department: String = "",
    val usernameTaken: Boolean = false,
    val isSaving: Boolean = false,
    val message: UiText? = null,
) {
    private val saved: Profile? get() = (profile as? Loadable.Success)?.value

    val isFullNameValid get() = RegisterValidators.isValidFullName(fullName)
    val isUsernameValid get() = RegisterValidators.isValidUsername(username)
    val isDepartmentValid get() = RegisterValidators.isValidDepartment(department)

    /** Kaydedilmiş değerlerden en az biri değişti mi (boşluklar önemsiz). */
    val isDirty: Boolean
        get() {
            val p = saved ?: return false
            return fullName.trim() != p.fullName || username.trim() != p.username || department.trim() != p.department.orEmpty()
        }

    val canSave: Boolean
        get() = saved != null && !isSaving && isDirty && isFullNameValid && isUsernameValid && isDepartmentValid
}

/** Ad soyad, kullanıcı adı ve bölüm düzenlenir; e-posta ve üniversite salt okunurdur (üniversite doğrulamadan sonra kilitli). */
@HiltViewModel
class AccountInfoViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountInfoUiState(email = authRepository.currentUser?.email.orEmpty()))
    val uiState: StateFlow<AccountInfoUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(profile = Loadable.Loading) }
        viewModelScope.launch {
            try {
                val profile = profileRepository.getMyProfile()
                _uiState.update { it.withSaved(profile) }
            } catch (e: AppError) {
                _uiState.update { it.copy(profile = Loadable.Failure(e)) }
            }
        }
    }

    fun onFullNameChange(value: String) = _uiState.update { it.copy(fullName = value) }

    fun onUsernameChange(value: String) = _uiState.update { it.copy(username = value.lowercase(), usernameTaken = false) }

    fun onDepartmentChange(value: String) = _uiState.update { it.copy(department = value) }

    fun save() {
        val state = _uiState.value
        val universityId = (state.profile as? Loadable.Success)?.value?.universityId
        if (!state.canSave || universityId == null) return
        _uiState.update { it.copy(isSaving = true, usernameTaken = false) }
        viewModelScope.launch {
            try {
                profileRepository.updateMyProfile(state.fullName, state.username, universityId, state.department)
                val refreshed = profileRepository.getMyProfile()
                _uiState.update { it.withSaved(refreshed).copy(message = uiText(R.string.account_saved)) }
            } catch (e: AppError.UsernameTaken) {
                _uiState.update { it.copy(usernameTaken = true) }
            } catch (e: AppError) {
                _uiState.update { it.copy(message = e.toUiText()) }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }
}

private fun AccountInfoUiState.withSaved(profile: Profile) = copy(
    profile = Loadable.Success(profile),
    fullName = profile.fullName,
    username = profile.username,
    department = profile.department.orEmpty(),
    usernameTaken = false,
)
