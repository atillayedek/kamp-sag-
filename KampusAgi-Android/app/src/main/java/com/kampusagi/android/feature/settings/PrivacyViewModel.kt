package com.kampusagi.android.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kampusagi.android.R
import com.kampusagi.android.core.ui.UiText
import com.kampusagi.android.core.ui.uiText
import com.kampusagi.android.domain.settings.PrivacyPreferenceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

data class PrivacyUiState(
    val analyticsEnabled: Boolean = true,
    val message: UiText? = null,
)

@HiltViewModel
class PrivacyViewModel @Inject constructor(
    private val preferences: PrivacyPreferenceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PrivacyUiState())
    val uiState: StateFlow<PrivacyUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferences.analyticsEnabled.collect { enabled -> _uiState.update { it.copy(analyticsEnabled = enabled) } }
        }
    }

    fun onAnalyticsChange(enabled: Boolean) {
        viewModelScope.launch {
            try {
                preferences.setAnalyticsEnabled(enabled)
            } catch (_: IOException) {
                _uiState.update { it.copy(message = uiText(R.string.settings_preference_save_failed)) }
            }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }
}
