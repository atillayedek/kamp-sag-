package com.kampusagi.android.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kampusagi.android.domain.settings.ThemeMode
import com.kampusagi.android.domain.settings.ThemePreferenceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Uygulama genelindeki görünüm tercihi. Tercih okunana kadar `null` (yanlış temayla bir kare çizilmesin). */
@HiltViewModel
class AppThemeViewModel @Inject constructor(
    themeRepository: ThemePreferenceRepository,
) : ViewModel() {
    val themeMode: StateFlow<ThemeMode?> = themeRepository.themeMode
        .map<ThemeMode, ThemeMode?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
