package com.kampusagi.android.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kampusagi.android.R
import com.kampusagi.android.core.ui.Loadable
import com.kampusagi.android.core.ui.UiText
import com.kampusagi.android.core.ui.toUiText
import com.kampusagi.android.core.ui.uiText
import com.kampusagi.android.domain.account.AccountRepository
import com.kampusagi.android.domain.common.AppError
import com.kampusagi.android.domain.profile.Profile
import com.kampusagi.android.domain.profile.ProfileRepository
import com.kampusagi.android.domain.settings.ThemeMode
import com.kampusagi.android.domain.settings.ThemePreferenceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

data class ProfileUiState(
    val profile: Loadable<Profile> = Loadable.Loading,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val showDeleteDialog: Boolean = false,
    val isDeleting: Boolean = false,
    val message: UiText? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val themeRepository: ThemePreferenceRepository,
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            themeRepository.themeMode.collect { mode -> _uiState.update { it.copy(themeMode = mode) } }
        }
    }

    fun load() {
        _uiState.update { it.copy(profile = Loadable.Loading) }
        viewModelScope.launch {
            val result = try {
                Loadable.Success(profileRepository.getMyProfile())
            } catch (e: AppError) {
                Loadable.Failure(e)
            }
            _uiState.update { it.copy(profile = result) }
        }
    }

    /** "Koyu Görünüm" anahtarı: açık/koyu sabitlenir. Sisteme dönmek için [useSystemTheme]. */
    fun onDarkModeChange(dark: Boolean) = saveTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT)

    fun useSystemTheme() = saveTheme(ThemeMode.SYSTEM)

    private fun saveTheme(mode: ThemeMode) {
        viewModelScope.launch {
            try {
                themeRepository.setThemeMode(mode)
            } catch (_: IOException) {
                _uiState.update { it.copy(message = uiText(R.string.settings_theme_save_failed)) }
            }
        }
    }

    fun requestDeleteAccount() = _uiState.update { it.copy(showDeleteDialog = true) }

    fun dismissDeleteDialog() {
        if (!_uiState.value.isDeleting) _uiState.update { it.copy(showDeleteDialog = false) }
    }

    /** Başarıda oturum kapanır ve kök yönlendirme otomatik olarak karşılama ekranına döner; burada ek işlem yok. */
    fun confirmDeleteAccount() {
        if (_uiState.value.isDeleting) return
        _uiState.update { it.copy(isDeleting = true) }
        viewModelScope.launch {
            try {
                accountRepository.deleteAccount()
            } catch (e: AppError) {
                _uiState.update { it.copy(showDeleteDialog = false, message = e.toUiText()) }
            } finally {
                _uiState.update { it.copy(isDeleting = false) }
            }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }
}
